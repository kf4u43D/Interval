package dev.intervaltablet.midi

import dev.intervaltablet.domain.MidiMessage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable
import java.util.ArrayDeque
import java.util.Locale

enum class MidiPortDirection { SOURCE, DESTINATION }

/** Identity that can be persisted and matched after Android assigns a new device id. */
data class MidiPortPersistentIdentity(
    val direction: MidiPortDirection,
    val portNumber: Int,
    val manufacturer: String = "",
    val product: String = "",
    val deviceName: String = "",
    val portName: String = "",
) {
    init {
        require(portNumber >= 0)
    }

    fun matches(descriptor: MidiPortDescriptor): Boolean {
        return reconnectKey == descriptor.persistentIdentity.reconnectKey
    }

    /** Canonical, delimiter-safe key suitable for deterministic persistence fixtures. */
    val reconnectKey: String
        get() = listOf(
            direction.name,
            portNumber.toString(),
            manufacturer.normalizedIdentityPart(),
            product.normalizedIdentityPart(),
            deviceName.normalizedIdentityPart(),
            portName.normalizedIdentityPart(),
        ).joinToString(separator = "") { part -> "${part.length}:$part" }
}

data class MidiPortDescriptor(
    val deviceId: Int,
    val portNumber: Int,
    val direction: MidiPortDirection,
    val deviceName: String,
    val portName: String,
    val manufacturer: String = "",
    val product: String = "",
) {
    init {
        require(deviceId >= 0)
        require(portNumber >= 0)
    }

    /** Stable only for the lifetime of the Android MIDI device session. */
    val stableSessionId: String = "$deviceId:$portNumber:$direction"
    val displayName: String = listOf(deviceName, portName)
        .filter { it.isNotBlank() }
        .joinToString(" — ")
        .ifBlank { "MIDI $deviceId · port $portNumber" }
    val persistentIdentity: MidiPortPersistentIdentity = MidiPortPersistentIdentity(
        direction = direction,
        portNumber = portNumber,
        manufacturer = manufacturer,
        product = product,
        deviceName = deviceName,
        portName = portName,
    )
}

enum class MidiConnectionPhase {
    CLOSED,
    OPENING,
    OPEN,
    LOST,
    ERROR,
}

data class MidiConnectionState(
    val phase: MidiConnectionPhase = MidiConnectionPhase.CLOSED,
    val descriptor: MidiPortDescriptor? = null,
    val generation: Long = 0,
    val error: String? = null,
)

data class MidiRepositoryState(
    val sources: List<MidiPortDescriptor> = emptyList(),
    val destinations: List<MidiPortDescriptor> = emptyList(),
    /** Successfully opened source, retained for compatibility with the initial UI. */
    val selectedSource: MidiPortDescriptor? = null,
    /** Successfully opened destination, retained for compatibility with the initial UI. */
    val selectedDestination: MidiPortDescriptor? = null,
    val sourceConnection: MidiConnectionState = MidiConnectionState(),
    val destinationConnection: MidiConnectionState = MidiConnectionState(),
    val receivedPacketCount: Long = 0,
    val sentMessageCount: Long = 0,
    val droppedInputPacketCount: Long = 0,
    val droppedOutputMessageCount: Long = 0,
    val lastError: String? = null,
)

data class MidiInputPacket(
    val source: MidiPortDescriptor,
    val generation: Long,
    val bytes: ByteArray,
    val timestampNanos: Long,
)

/** Non-blocking hand-off used directly from Android's MidiReceiver callback. */
fun interface MidiPacketSink {
    /** Returns false when the coordinator's bounded ingress cannot accept the packet. */
    fun offer(packet: MidiInputPacket): Boolean
}

enum class MidiConnectionLossReason {
    DEVICE_REMOVED,
    PORT_DISAPPEARED,
    SEND_FAILED,
}

sealed interface MidiRepositoryEvent {
    data class PortsAdded(val ports: List<MidiPortDescriptor>) : MidiRepositoryEvent
    data class PortsRemoved(val ports: List<MidiPortDescriptor>) : MidiRepositoryEvent

    data class ConnectionLost(
        val direction: MidiPortDirection,
        val descriptor: MidiPortDescriptor,
        val generation: Long,
        val reason: MidiConnectionLossReason,
        val detail: String? = null,
    ) : MidiRepositoryEvent

    data class OpenFailed(
        val direction: MidiPortDirection,
        val descriptor: MidiPortDescriptor,
        val generation: Long,
        val detail: String,
    ) : MidiRepositoryEvent

    data class SendFailed(
        val descriptor: MidiPortDescriptor,
        val generation: Long,
        val message: MidiMessage,
        val detail: String,
    ) : MidiRepositoryEvent

    data class InputOverflow(
        val descriptor: MidiPortDescriptor,
        val generation: Long,
        val droppedPacketCount: Long,
    ) : MidiRepositoryEvent
}

/** Injectable contract consumed by the application coordinator and JVM fakes. */
interface MidiPortRepository : Closeable {
    val state: StateFlow<MidiRepositoryState>
    val events: SharedFlow<MidiRepositoryEvent>

    fun refreshDevices()
    fun selectSource(descriptor: MidiPortDescriptor?)
    fun selectDestination(descriptor: MidiPortDescriptor?)
    fun send(message: MidiMessage)

    /** Sends only if the same destination session is still open when I/O is performed. */
    fun sendTo(destinationSessionId: String, message: MidiMessage)
}

/**
 * Destination operations share one bounded FIFO so Send/Select order does not depend on
 * Handler scheduling. Once saturated, further sends are rejected until the consumer has
 * emitted a conservative reset. Selection requests remain lossless and consecutive or
 * post-overflow requests collapse to the latest one.
 */
internal sealed interface MidiDestinationOperation {
    data class Send(
        val expectedDestinationSessionId: String?,
        val expectedGeneration: Long,
        val message: MidiMessage,
    ) : MidiDestinationOperation {
        init {
            require(expectedGeneration >= 0L)
        }
    }

    data class Select(val descriptor: MidiPortDescriptor?) : MidiDestinationOperation

    data class ResetCurrent(
        val timestampNanos: Long,
        val droppedMessageCount: Long,
    ) : MidiDestinationOperation {
        init {
            require(droppedMessageCount > 0L)
        }
    }
}

internal enum class MidiDestinationOfferResult {
    ACCEPTED,
    DROPPED_OVERFLOW,
    REJECTED_CLOSED,
}

internal class BoundedMidiDestinationOperationBuffer(
    private val capacity: Int,
) {
    private val lock = Any()
    private val operations = ArrayDeque<MidiDestinationOperation>(capacity)
    private var accepting = true
    private var overflowActive = false
    private var overflowResetRequired = false
    private var overflowTimestampNanos = 0L
    private var overflowDroppedMessageCount = 0L
    private var pendingLatestSelection: MidiDestinationOperation.Select? = null

    init {
        require(capacity > 0)
    }

    fun offerSend(operation: MidiDestinationOperation.Send): MidiDestinationOfferResult {
        return synchronized(lock) {
            if (!accepting) return@synchronized MidiDestinationOfferResult.REJECTED_CLOSED
            if (overflowActive || operations.size >= capacity) {
                overflowActive = true
                overflowResetRequired = true
                overflowTimestampNanos = maxOf(overflowTimestampNanos, operation.message.timestampNanos, 0L)
                overflowDroppedMessageCount += 1L
                MidiDestinationOfferResult.DROPPED_OVERFLOW
            } else {
                operations.addLast(operation)
                MidiDestinationOfferResult.ACCEPTED
            }
        }
    }

    fun offerSelection(operation: MidiDestinationOperation.Select): MidiDestinationOfferResult {
        return synchronized(lock) {
            if (!accepting) return@synchronized MidiDestinationOfferResult.REJECTED_CLOSED
            if (overflowActive) {
                pendingLatestSelection = operation
            } else if (operations.peekLast() is MidiDestinationOperation.Select) {
                operations.removeLast()
                operations.addLast(operation)
            } else if (operations.size < capacity) {
                operations.addLast(operation)
            } else {
                // No send is lost here. Drain the full prefix, then apply only the latest
                // selection received while the buffer is recovering.
                overflowActive = true
                pendingLatestSelection = operation
            }
            MidiDestinationOfferResult.ACCEPTED
        }
    }

    fun poll(): MidiDestinationOperation? {
        return synchronized(lock) {
            operations.pollFirst()?.let { return@synchronized it }
            if (!overflowActive) return@synchronized null
            if (overflowResetRequired) {
                overflowResetRequired = false
                val timestamp = overflowTimestampNanos
                val droppedCount = overflowDroppedMessageCount
                overflowTimestampNanos = 0L
                overflowDroppedMessageCount = 0L
                return@synchronized MidiDestinationOperation.ResetCurrent(timestamp, droppedCount)
            }
            pendingLatestSelection?.let { selection ->
                pendingLatestSelection = null
                overflowActive = false
                return@synchronized selection
            }
            overflowActive = false
            null
        }
    }

    fun hasPendingOperations(): Boolean {
        return synchronized(lock) {
            operations.isNotEmpty() || overflowActive
        }
    }

    /** Rejects new producers while retaining every operation already accepted for final drain. */
    fun closeForOffers() {
        synchronized(lock) {
            accepting = false
        }
    }
}

/** Pure catalog reconciliation result, also used by JVM repository fakes. */
data class MidiPortCatalogDelta(
    val sources: List<MidiPortDescriptor>,
    val destinations: List<MidiPortDescriptor>,
    val added: List<MidiPortDescriptor>,
    val removed: List<MidiPortDescriptor>,
)

fun reconcileMidiPortCatalog(
    previousSources: List<MidiPortDescriptor>,
    previousDestinations: List<MidiPortDescriptor>,
    discovered: List<MidiPortDescriptor>,
): MidiPortCatalogDelta {
    val previous = (previousSources + previousDestinations).associateBy { it.stableSessionId }
    val current = discovered
        .distinctBy { it.stableSessionId }
        .sortedWith(
            compareBy<MidiPortDescriptor>(
                { it.direction.ordinal },
                { it.displayName.lowercase(Locale.ROOT) },
                { it.deviceId },
                { it.portNumber },
            ),
        )
        .associateBy { it.stableSessionId }

    return MidiPortCatalogDelta(
        sources = current.values.filter { it.direction == MidiPortDirection.SOURCE },
        destinations = current.values.filter { it.direction == MidiPortDirection.DESTINATION },
        added = current.filterKeys { it !in previous }.values.toList(),
        removed = previous.filterKeys { it !in current }.values.toList(),
    )
}

private fun String.normalizedIdentityPart(): String {
    return trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
