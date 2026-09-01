package dev.intervaltablet.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiDeviceStatus
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import dev.intervaltablet.domain.MidiMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Android MIDI adapter. Musical decisions stay in the coordinator/domain; this class owns
 * only discovery, connection generations, byte hand-off and deterministic resources.
 */
@Suppress("DEPRECATION") // API 29 baseline requires the pre-transport MidiManager discovery API.
class AndroidMidiRepository(
    context: Context,
    private val packetSink: MidiPacketSink,
) : MidiPortRepository {
    /** Compatibility bridge for the original ViewModel callback. */
    constructor(
        context: Context,
        onBytes: (MidiPortDescriptor, ByteArray, Long) -> Unit,
    ) : this(
        context = context,
        packetSink = MidiPacketSink { packet ->
            onBytes(packet.source, packet.bytes, packet.timestampNanos)
            true
        },
    )

    private data class SourceHandle(
        val descriptor: MidiPortDescriptor,
        val generation: Long,
        val device: MidiDevice,
        val port: MidiOutputPort,
        val receiver: MidiReceiver,
    )

    private data class DestinationHandle(
        val descriptor: MidiPortDescriptor,
        val generation: Long,
        val device: MidiDevice,
        val port: MidiInputPort,
    )

    private data class OverflowIdentity(
        val descriptor: MidiPortDescriptor,
        val generation: Long,
    )

    private val applicationContext = context.applicationContext
    private val midiManager: MidiManager? = applicationContext.getSystemService(MidiManager::class.java)
    private val ioThread = HandlerThread(IO_THREAD_NAME).apply { start() }
    private val ioHandler = Handler(ioThread.looper)
    private val closing = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val sourceGeneration = AtomicLong(0)
    private val destinationGeneration = AtomicLong(0)
    private val pendingReceivedPacketCount = AtomicLong(0)
    private val pendingDroppedPacketCount = AtomicLong(0)
    private val pendingOverflowIdentity = AtomicReference<OverflowIdentity?>(null)
    private val packetMetricsFlushScheduled = AtomicBoolean(false)
    private val packetMetricsFlushRunnable = Runnable { flushPacketMetricsOnIo(reschedule = true) }
    private val destinationOperations = BoundedMidiDestinationOperationBuffer(DESTINATION_OPERATION_CAPACITY)
    private val destinationDrainScheduled = AtomicBoolean(false)
    private val destinationDrainRunnable = Runnable { drainDestinationOperationsOnIo(reschedule = true) }

    private val mutableState = MutableStateFlow(
        MidiRepositoryState(
            lastError = if (midiManager == null) MIDI_UNAVAILABLE_MESSAGE else null,
        ),
    )
    override val state: StateFlow<MidiRepositoryState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<MidiRepositoryEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<MidiRepositoryEvent> = mutableEvents.asSharedFlow()

    private var sourceHandle: SourceHandle? = null
    private var destinationHandle: DestinationHandle? = null

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            if (!isStopping()) refreshDevicesOnIo()
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            if (isStopping()) return
            sourceHandle
                ?.takeIf { it.descriptor.deviceId == device.id }
                ?.let { loseSourceOnIo(it, MidiConnectionLossReason.DEVICE_REMOVED, "Source MIDI déconnectée") }
            destinationHandle
                ?.takeIf { it.descriptor.deviceId == device.id }
                ?.let {
                    loseDestinationOnIo(
                        it,
                        MidiConnectionLossReason.DEVICE_REMOVED,
                        "Destination MIDI déconnectée",
                    )
                }
            refreshDevicesOnIo()
        }

        override fun onDeviceStatusChanged(status: MidiDeviceStatus) {
            if (!isStopping()) refreshDevicesOnIo()
        }
    }

    init {
        val manager = midiManager
        if (manager != null) {
            manager.registerDeviceCallback(deviceCallback, ioHandler)
            postToIo(::refreshDevicesOnIo)
        }
    }

    override fun refreshDevices() {
        postToIo(::refreshDevicesOnIo)
    }

    override fun selectSource(descriptor: MidiPortDescriptor?) {
        require(descriptor == null || descriptor.direction == MidiPortDirection.SOURCE)
        if (isStopping()) return
        // Invalidate the receiver before the handler processes the switch. This closes the
        // window where a callback from the old lease could otherwise enter the app mailbox.
        val generation = sourceGeneration.incrementAndGet()
        postToIo { selectSourceOnIo(descriptor, generation) }
    }

    override fun selectDestination(descriptor: MidiPortDescriptor?) {
        require(descriptor == null || descriptor.direction == MidiPortDirection.DESTINATION)
        when (destinationOperations.offerSelection(MidiDestinationOperation.Select(descriptor))) {
            MidiDestinationOfferResult.ACCEPTED -> scheduleDestinationDrain()
            MidiDestinationOfferResult.DROPPED_OVERFLOW -> error("Selection offers are never dropped")
            MidiDestinationOfferResult.REJECTED_CLOSED -> Unit
        }
    }

    override fun send(message: MidiMessage) {
        enqueueSend(expectedDestinationSessionId = null, message = message)
    }

    override fun sendTo(destinationSessionId: String, message: MidiMessage) {
        enqueueSend(expectedDestinationSessionId = destinationSessionId, message = message)
    }

    private fun enqueueSend(expectedDestinationSessionId: String?, message: MidiMessage) {
        val operation = MidiDestinationOperation.Send(
            expectedDestinationSessionId = expectedDestinationSessionId,
            expectedGeneration = destinationGeneration.get(),
            message = message,
        )
        when (destinationOperations.offerSend(operation)) {
            MidiDestinationOfferResult.ACCEPTED -> scheduleDestinationDrain()
            MidiDestinationOfferResult.DROPPED_OVERFLOW -> {
                scheduleDestinationDrain()
            }
            MidiDestinationOfferResult.REJECTED_CLOSED -> Unit
        }
    }

    private fun scheduleDestinationDrain() {
        if (closed.get() || !destinationDrainScheduled.compareAndSet(false, true)) return
        if (!ioHandler.post(destinationDrainRunnable)) destinationDrainScheduled.set(false)
    }

    private fun drainDestinationOperationsOnIo(reschedule: Boolean) {
        assertIoThread()
        val operationLimit = if (reschedule) MAX_DESTINATION_OPERATIONS_PER_DRAIN else Int.MAX_VALUE
        var processed = 0
        while (processed < operationLimit) {
            when (val operation = destinationOperations.poll() ?: break) {
                is MidiDestinationOperation.Send -> {
                    operation.message.toByteArray()?.let { bytes ->
                        sendOnIo(
                            expectedDestinationSessionId = operation.expectedDestinationSessionId,
                            expectedGeneration = operation.expectedGeneration,
                            message = operation.message,
                            bytes = bytes,
                        )
                    }
                }
                is MidiDestinationOperation.Select -> {
                    val generation = destinationGeneration.incrementAndGet()
                    selectDestinationOnIo(operation.descriptor, generation)
                }
                is MidiDestinationOperation.ResetCurrent -> {
                    resetCurrentDestinationOnIo(operation.timestampNanos)
                    recordDroppedOutputCountOnIo(operation.droppedMessageCount)
                }
            }
            processed += 1
        }

        if (reschedule && !isStopping() && destinationOperations.hasPendingOperations()) {
            if (ioHandler.post(destinationDrainRunnable)) return
        }
        destinationDrainScheduled.set(false)
        if (reschedule && !isStopping() && destinationOperations.hasPendingOperations()) {
            scheduleDestinationDrain()
        }
    }

    private fun resetCurrentDestinationOnIo(timestampNanos: Long) {
        assertIoThread()
        val handle = destinationHandle ?: return
        for (channel in 0..15) {
            for (controller in RESET_CONTROLLERS) {
                if (destinationHandle !== handle) return
                val message = MidiMessage.ControlChange(
                    channel = channel,
                    controller = controller,
                    value = 0,
                    timestampNanos = maxOf(timestampNanos, 0L),
                )
                sendOnIo(
                    expectedDestinationSessionId = handle.descriptor.stableSessionId,
                    expectedGeneration = handle.generation,
                    message = message,
                    bytes = requireNotNull(message.toByteArray()),
                )
            }
        }
    }

    private fun recordDroppedOutputCountOnIo(droppedDelta: Long) {
        assertIoThread()
        require(droppedDelta > 0L)
        val current = mutableState.value
        mutableState.value = current.copy(
            droppedOutputMessageCount = current.droppedOutputMessageCount + droppedDelta,
        )
    }

    private fun refreshDevicesOnIo() {
        assertIoThread()
        val manager = midiManager ?: return
        val current = mutableState.value
        val discovered = manager.devices.flatMap(::descriptors)
        val delta = reconcileMidiPortCatalog(
            previousSources = current.sources,
            previousDestinations = current.destinations,
            discovered = discovered,
        )
        mutableState.value = current.copy(
            sources = delta.sources,
            destinations = delta.destinations,
        )
        if (delta.added.isNotEmpty()) emit(MidiRepositoryEvent.PortsAdded(delta.added))
        if (delta.removed.isNotEmpty()) emit(MidiRepositoryEvent.PortsRemoved(delta.removed))

        sourceHandle
            ?.takeIf { handle -> delta.sources.none { it.stableSessionId == handle.descriptor.stableSessionId } }
            ?.let {
                loseSourceOnIo(
                    it,
                    MidiConnectionLossReason.PORT_DISAPPEARED,
                    "Port MIDI source indisponible",
                )
            }
        destinationHandle
            ?.takeIf { handle -> delta.destinations.none { it.stableSessionId == handle.descriptor.stableSessionId } }
            ?.let {
                loseDestinationOnIo(
                    it,
                    MidiConnectionLossReason.PORT_DISAPPEARED,
                    "Port MIDI destination indisponible",
                )
            }
        loseOpeningConnectionIfMissingOnIo(MidiPortDirection.SOURCE, delta.sources)
        loseOpeningConnectionIfMissingOnIo(MidiPortDirection.DESTINATION, delta.destinations)
    }

    private fun selectSourceOnIo(descriptor: MidiPortDescriptor?, generation: Long) {
        assertIoThread()
        // Concurrent callers may post in a different order from their atomic generations.
        // Only the newest request may mutate or close the current connection.
        if (generation != sourceGeneration.get()) return
        closeSourceHandleOnIo()
        if (descriptor == null) {
            mutableState.value = mutableState.value.copy(
                selectedSource = null,
                sourceConnection = MidiConnectionState(generation = generation),
                lastError = null,
            )
            return
        }

        mutableState.value = mutableState.value.copy(
            selectedSource = null,
            sourceConnection = MidiConnectionState(
                phase = MidiConnectionPhase.OPENING,
                descriptor = descriptor,
                generation = generation,
            ),
            lastError = null,
        )
        val manager = midiManager
        val info = manager?.devices?.firstOrNull { it.id == descriptor.deviceId }
        if (manager == null || info == null) {
            failOpenOnIo(
                direction = MidiPortDirection.SOURCE,
                descriptor = descriptor,
                generation = generation,
                detail = "Périphérique MIDI source indisponible",
            )
            return
        }

        try {
            manager.openDevice(
                info,
                { device -> completeSourceOpenOnIo(descriptor, generation, device) },
                ioHandler,
            )
        } catch (error: RuntimeException) {
            failOpenOnIo(
                MidiPortDirection.SOURCE,
                descriptor,
                generation,
                "Impossible de demander l’ouverture MIDI source: ${safeDetail(error)}",
            )
        }
    }

    private fun completeSourceOpenOnIo(
        descriptor: MidiPortDescriptor,
        generation: Long,
        device: MidiDevice?,
    ) {
        assertIoThread()
        if (isStopping() || generation != sourceGeneration.get()) {
            runCatching { device?.close() }
            return
        }
        if (device == null) {
            failOpenOnIo(
                MidiPortDirection.SOURCE,
                descriptor,
                generation,
                "Impossible d’ouvrir la source MIDI",
            )
            return
        }
        val port = try {
            device.openOutputPort(descriptor.portNumber)
        } catch (error: RuntimeException) {
            runCatching { device.close() }
            failOpenOnIo(
                MidiPortDirection.SOURCE,
                descriptor,
                generation,
                "Impossible d’ouvrir le port MIDI source: ${safeDetail(error)}",
            )
            return
        }
        if (port == null) {
            runCatching { device.close() }
            failOpenOnIo(
                MidiPortDirection.SOURCE,
                descriptor,
                generation,
                "Impossible d’ouvrir le port MIDI source",
            )
            return
        }

        val receiver = receiverFor(descriptor, generation)
        try {
            sourceHandle = SourceHandle(descriptor, generation, device, port, receiver)
            port.connect(receiver)
        } catch (error: RuntimeException) {
            sourceHandle = null
            runCatching { port.disconnect(receiver) }
            runCatching { port.close() }
            runCatching { device.close() }
            failOpenOnIo(
                MidiPortDirection.SOURCE,
                descriptor,
                generation,
                "Impossible de connecter le port MIDI source: ${safeDetail(error)}",
            )
            return
        }
        mutableState.value = mutableState.value.copy(
            selectedSource = descriptor,
            sourceConnection = MidiConnectionState(
                phase = MidiConnectionPhase.OPEN,
                descriptor = descriptor,
                generation = generation,
            ),
            lastError = null,
        )
    }

    private fun selectDestinationOnIo(descriptor: MidiPortDescriptor?, generation: Long) {
        assertIoThread()
        if (generation != destinationGeneration.get()) return
        closeDestinationHandleOnIo()
        if (descriptor == null) {
            mutableState.value = mutableState.value.copy(
                selectedDestination = null,
                destinationConnection = MidiConnectionState(generation = generation),
                lastError = null,
            )
            return
        }

        mutableState.value = mutableState.value.copy(
            selectedDestination = null,
            destinationConnection = MidiConnectionState(
                phase = MidiConnectionPhase.OPENING,
                descriptor = descriptor,
                generation = generation,
            ),
            lastError = null,
        )
        val manager = midiManager
        val info = manager?.devices?.firstOrNull { it.id == descriptor.deviceId }
        if (manager == null || info == null) {
            failOpenOnIo(
                direction = MidiPortDirection.DESTINATION,
                descriptor = descriptor,
                generation = generation,
                detail = "Périphérique MIDI destination indisponible",
            )
            return
        }

        try {
            manager.openDevice(
                info,
                { device -> completeDestinationOpenOnIo(descriptor, generation, device) },
                ioHandler,
            )
        } catch (error: RuntimeException) {
            failOpenOnIo(
                MidiPortDirection.DESTINATION,
                descriptor,
                generation,
                "Impossible de demander l’ouverture MIDI destination: ${safeDetail(error)}",
            )
        }
    }

    private fun completeDestinationOpenOnIo(
        descriptor: MidiPortDescriptor,
        generation: Long,
        device: MidiDevice?,
    ) {
        assertIoThread()
        if (isStopping() || generation != destinationGeneration.get()) {
            runCatching { device?.close() }
            return
        }
        if (device == null) {
            failOpenOnIo(
                MidiPortDirection.DESTINATION,
                descriptor,
                generation,
                "Impossible d’ouvrir la destination MIDI",
            )
            return
        }
        val port = try {
            device.openInputPort(descriptor.portNumber)
        } catch (error: RuntimeException) {
            runCatching { device.close() }
            failOpenOnIo(
                MidiPortDirection.DESTINATION,
                descriptor,
                generation,
                "Impossible d’ouvrir le port MIDI destination: ${safeDetail(error)}",
            )
            return
        }
        if (port == null) {
            runCatching { device.close() }
            failOpenOnIo(
                MidiPortDirection.DESTINATION,
                descriptor,
                generation,
                "Impossible d’ouvrir le port MIDI destination",
            )
            return
        }

        destinationHandle = DestinationHandle(descriptor, generation, device, port)
        mutableState.value = mutableState.value.copy(
            selectedDestination = descriptor,
            destinationConnection = MidiConnectionState(
                phase = MidiConnectionPhase.OPEN,
                descriptor = descriptor,
                generation = generation,
            ),
            lastError = null,
        )
    }

    private fun receiverFor(descriptor: MidiPortDescriptor, generation: Long): MidiReceiver {
        return object : MidiReceiver() {
            override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
                if (isStopping() || generation != sourceGeneration.get()) return
                if (offset < 0 || count < 0 || offset > data.size || count > data.size - offset) return
                val packet = MidiInputPacket(
                    source = descriptor,
                    generation = generation,
                    bytes = data.copyOfRange(offset, offset + count),
                    timestampNanos = timestamp,
                )
                val accepted = try {
                    packetSink.offer(packet)
                } catch (_: RuntimeException) {
                    false
                }
                recordPacketMetrics(packet, accepted)
            }
        }
    }

    private fun recordPacketMetrics(packet: MidiInputPacket, accepted: Boolean) {
        if (!accepted) {
            // Publish identity before the volatile count. A flush that observes the
            // increment can then always attribute (or deliberately reject) the event.
            pendingOverflowIdentity.set(OverflowIdentity(packet.source, packet.generation))
            pendingDroppedPacketCount.incrementAndGet()
        }
        pendingReceivedPacketCount.incrementAndGet()
        schedulePacketMetricsFlush()
    }

    private fun schedulePacketMetricsFlush() {
        if (isStopping() || !packetMetricsFlushScheduled.compareAndSet(false, true)) return
        if (!ioHandler.postDelayed(packetMetricsFlushRunnable, PACKET_METRICS_FLUSH_MILLIS)) {
            packetMetricsFlushScheduled.set(false)
        }
    }

    private fun flushPacketMetricsOnIo(reschedule: Boolean) {
        assertIoThread()
        val receivedDelta = pendingReceivedPacketCount.getAndSet(0)
        val droppedDelta = pendingDroppedPacketCount.getAndSet(0)
        // Keep the last identity until another rejected packet replaces it. Clearing it here
        // would race with a rejection that lands between the counter and identity reads.
        val overflowIdentity = pendingOverflowIdentity.get()
        if (receivedDelta != 0L || droppedDelta != 0L) {
            val current = mutableState.value
            val totalDropped = current.droppedInputPacketCount + droppedDelta
            mutableState.value = current.copy(
                receivedPacketCount = current.receivedPacketCount + receivedDelta,
                droppedInputPacketCount = totalDropped,
            )
            val sourceConnection = current.sourceConnection
            if (droppedDelta != 0L &&
                overflowIdentity != null &&
                sourceConnection.phase == MidiConnectionPhase.OPEN &&
                sourceConnection.generation == overflowIdentity.generation &&
                sourceConnection.descriptor?.stableSessionId ==
                overflowIdentity.descriptor.stableSessionId
            ) {
                emit(
                    MidiRepositoryEvent.InputOverflow(
                        descriptor = overflowIdentity.descriptor,
                        generation = overflowIdentity.generation,
                        droppedPacketCount = totalDropped,
                    ),
                )
            }
        }

        packetMetricsFlushScheduled.set(false)
        if (reschedule &&
            (pendingReceivedPacketCount.get() != 0L || pendingDroppedPacketCount.get() != 0L)
        ) {
            schedulePacketMetricsFlush()
        }
    }

    private fun sendOnIo(
        expectedDestinationSessionId: String?,
        expectedGeneration: Long,
        message: MidiMessage,
        bytes: ByteArray,
    ) {
        assertIoThread()
        if (expectedGeneration != destinationGeneration.get()) return
        val handle = destinationHandle ?: return
        if (handle.generation != expectedGeneration) return
        if (expectedDestinationSessionId != null &&
            handle.descriptor.stableSessionId != expectedDestinationSessionId
        ) {
            return
        }
        try {
            handle.port.send(bytes, 0, bytes.size, message.timestampNanos)
            val current = mutableState.value
            mutableState.value = current.copy(sentMessageCount = current.sentMessageCount + 1)
        } catch (error: Exception) {
            val detail = "Échec MIDI Out: ${safeDetail(error)}"
            emit(
                MidiRepositoryEvent.SendFailed(
                    descriptor = handle.descriptor,
                    generation = handle.generation,
                    message = message,
                    detail = detail,
                ),
            )
            loseDestinationOnIo(
                handle = handle,
                reason = MidiConnectionLossReason.SEND_FAILED,
                detail = detail,
            )
        }
    }

    private fun loseOpeningConnectionIfMissingOnIo(
        direction: MidiPortDirection,
        available: List<MidiPortDescriptor>,
    ) {
        assertIoThread()
        val current = mutableState.value
        val connection = when (direction) {
            MidiPortDirection.SOURCE -> current.sourceConnection
            MidiPortDirection.DESTINATION -> current.destinationConnection
        }
        if (connection.phase != MidiConnectionPhase.OPENING) return
        val descriptor = connection.descriptor ?: return
        if (available.any { it.stableSessionId == descriptor.stableSessionId }) return

        val detail = when (direction) {
            MidiPortDirection.SOURCE -> "Source MIDI retirée pendant l’ouverture"
            MidiPortDirection.DESTINATION -> "Destination MIDI retirée pendant l’ouverture"
        }
        when (direction) {
            MidiPortDirection.SOURCE -> sourceGeneration.incrementAndGet()
            MidiPortDirection.DESTINATION -> destinationGeneration.incrementAndGet()
        }
        val lost = MidiConnectionState(
            phase = MidiConnectionPhase.LOST,
            descriptor = descriptor,
            generation = connection.generation,
            error = detail,
        )
        mutableState.value = when (direction) {
            MidiPortDirection.SOURCE -> current.copy(
                selectedSource = null,
                sourceConnection = lost,
                lastError = detail,
            )
            MidiPortDirection.DESTINATION -> current.copy(
                selectedDestination = null,
                destinationConnection = lost,
                lastError = detail,
            )
        }
        emit(
            MidiRepositoryEvent.ConnectionLost(
                direction = direction,
                descriptor = descriptor,
                generation = connection.generation,
                reason = MidiConnectionLossReason.PORT_DISAPPEARED,
                detail = detail,
            ),
        )
    }

    private fun failOpenOnIo(
        direction: MidiPortDirection,
        descriptor: MidiPortDescriptor,
        generation: Long,
        detail: String,
    ) {
        assertIoThread()
        val failed = MidiConnectionState(
            phase = MidiConnectionPhase.ERROR,
            descriptor = descriptor,
            generation = generation,
            error = detail,
        )
        mutableState.value = when (direction) {
            MidiPortDirection.SOURCE -> mutableState.value.copy(
                selectedSource = null,
                sourceConnection = failed,
                lastError = detail,
            )
            MidiPortDirection.DESTINATION -> mutableState.value.copy(
                selectedDestination = null,
                destinationConnection = failed,
                lastError = detail,
            )
        }
        emit(MidiRepositoryEvent.OpenFailed(direction, descriptor, generation, detail))
    }

    private fun loseSourceOnIo(
        handle: SourceHandle,
        reason: MidiConnectionLossReason,
        detail: String,
    ) {
        assertIoThread()
        if (sourceHandle !== handle) return
        sourceGeneration.incrementAndGet()
        closeSourceHandleOnIo()
        mutableState.value = mutableState.value.copy(
            selectedSource = null,
            sourceConnection = MidiConnectionState(
                phase = MidiConnectionPhase.LOST,
                descriptor = handle.descriptor,
                generation = handle.generation,
                error = detail,
            ),
            lastError = detail,
        )
        emit(
            MidiRepositoryEvent.ConnectionLost(
                direction = MidiPortDirection.SOURCE,
                descriptor = handle.descriptor,
                generation = handle.generation,
                reason = reason,
                detail = detail,
            ),
        )
    }

    private fun loseDestinationOnIo(
        handle: DestinationHandle,
        reason: MidiConnectionLossReason,
        detail: String,
    ) {
        assertIoThread()
        if (destinationHandle !== handle) return
        destinationGeneration.incrementAndGet()
        closeDestinationHandleOnIo()
        mutableState.value = mutableState.value.copy(
            selectedDestination = null,
            destinationConnection = MidiConnectionState(
                phase = MidiConnectionPhase.LOST,
                descriptor = handle.descriptor,
                generation = handle.generation,
                error = detail,
            ),
            lastError = detail,
        )
        emit(
            MidiRepositoryEvent.ConnectionLost(
                direction = MidiPortDirection.DESTINATION,
                descriptor = handle.descriptor,
                generation = handle.generation,
                reason = reason,
                detail = detail,
            ),
        )
    }

    private fun closeSourceHandleOnIo() {
        assertIoThread()
        val handle = sourceHandle ?: return
        sourceHandle = null
        runCatching { handle.port.disconnect(handle.receiver) }
        runCatching { handle.port.close() }
        runCatching { handle.device.close() }
    }

    private fun closeDestinationHandleOnIo() {
        assertIoThread()
        val handle = destinationHandle ?: return
        destinationHandle = null
        runCatching { handle.port.close() }
        runCatching { handle.device.close() }
    }

    private fun descriptors(info: MidiDeviceInfo): List<MidiPortDescriptor> {
        val properties = info.properties
        val manufacturer = properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER).orEmpty()
        val product = properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT).orEmpty()
        val declaredName = properties.getString(MidiDeviceInfo.PROPERTY_NAME).orEmpty()
        val deviceName = listOf(declaredName, product, manufacturer)
            .firstOrNull { it.isNotBlank() }
            ?: "MIDI ${info.id}"
        return info.ports.map { port ->
            MidiPortDescriptor(
                deviceId = info.id,
                portNumber = port.portNumber,
                direction = if (port.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT) {
                    MidiPortDirection.SOURCE
                } else {
                    MidiPortDirection.DESTINATION
                },
                deviceName = deviceName,
                portName = port.name.orEmpty(),
                manufacturer = manufacturer,
                product = product,
            )
        }
    }

    private fun postToIo(block: () -> Unit) {
        if (isStopping()) return
        if (Looper.myLooper() == ioThread.looper) {
            block()
        } else {
            ioHandler.post {
                if (!closed.get()) block()
            }
        }
    }

    private fun emit(event: MidiRepositoryEvent) {
        mutableEvents.tryEmit(event)
    }

    private fun assertIoThread() {
        check(Looper.myLooper() == ioThread.looper) { "Android MIDI I/O must stay on its handler thread" }
    }

    private fun isStopping(): Boolean = closing.get() || closed.get()

    override fun close() {
        if (!closing.compareAndSet(false, true)) return
        destinationOperations.closeForOffers()
        // Reject old receivers immediately. Destination generation stays valid until cleanup,
        // allowing already accepted Note Off/Panic sends to drain in Handler FIFO order.
        sourceGeneration.incrementAndGet()
        val cleanup = {
            ioHandler.removeCallbacks(packetMetricsFlushRunnable)
            flushPacketMetricsOnIo(reschedule = false)
            pendingOverflowIdentity.set(null)
            ioHandler.removeCallbacks(destinationDrainRunnable)
            drainDestinationOperationsOnIo(reschedule = false)
            closed.set(true)
            runCatching { midiManager?.unregisterDeviceCallback(deviceCallback) }
            sourceGeneration.incrementAndGet()
            destinationGeneration.incrementAndGet()
            closeSourceHandleOnIo()
            closeDestinationHandleOnIo()
            mutableState.value = mutableState.value.copy(
                sources = emptyList(),
                destinations = emptyList(),
                selectedSource = null,
                selectedDestination = null,
                sourceConnection = MidiConnectionState(generation = sourceGeneration.get()),
                destinationConnection = MidiConnectionState(generation = destinationGeneration.get()),
            )
        }

        if (Looper.myLooper() == ioThread.looper) {
            if (!ioHandler.post(cleanup)) cleanup()
            ioThread.quitSafely()
            return
        }

        val completed = CountDownLatch(1)
        val posted = ioHandler.post {
            try {
                cleanup()
            } finally {
                completed.countDown()
            }
        }
        if (posted) completed.await(CLOSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        ioThread.quitSafely()
        runCatching { ioThread.join(CLOSE_TIMEOUT_MILLIS) }
    }

    private companion object {
        const val IO_THREAD_NAME = "IntervalTablet-MIDI"
        const val EVENT_BUFFER_CAPACITY = 64
        const val CLOSE_TIMEOUT_MILLIS = 2_000L
        const val PACKET_METRICS_FLUSH_MILLIS = 50L
        const val DESTINATION_OPERATION_CAPACITY = 512
        const val MAX_DESTINATION_OPERATIONS_PER_DRAIN = 128
        const val ALL_NOTES_OFF = 123
        const val ALL_SOUND_OFF = 120
        val RESET_CONTROLLERS: IntArray = intArrayOf(ALL_NOTES_OFF, ALL_SOUND_OFF)
        const val MIDI_UNAVAILABLE_MESSAGE = "Service MIDI Android indisponible"

        fun safeDetail(error: Throwable): String {
            return error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        }
    }
}
