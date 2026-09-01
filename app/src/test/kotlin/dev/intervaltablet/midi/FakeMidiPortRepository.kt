package dev.intervaltablet.midi

import dev.intervaltablet.domain.MidiMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Deterministic fake shared by coordinator tests; it never depends on Android classes. */
internal class FakeMidiPortRepository : MidiPortRepository {
    data class SentMessage(
        val destinationSessionId: String,
        val message: MidiMessage,
    )

    private val mutableState = MutableStateFlow(MidiRepositoryState())
    override val state: StateFlow<MidiRepositoryState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<MidiRepositoryEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<MidiRepositoryEvent> = mutableEvents.asSharedFlow()

    val publishedEvents = mutableListOf<MidiRepositoryEvent>()
    val sentMessages = mutableListOf<SentMessage>()
    var closeCount: Int = 0
        private set

    private var sourceGeneration: Long = 0
    private var destinationGeneration: Long = 0
    private var closed = false

    fun setPorts(ports: List<MidiPortDescriptor>) {
        check(!closed)
        val current = mutableState.value
        val delta = reconcileMidiPortCatalog(current.sources, current.destinations, ports)
        mutableState.value = current.copy(
            sources = delta.sources,
            destinations = delta.destinations,
        )
        if (delta.added.isNotEmpty()) publish(MidiRepositoryEvent.PortsAdded(delta.added))
        if (delta.removed.isNotEmpty()) publish(MidiRepositoryEvent.PortsRemoved(delta.removed))

        current.selectedSource
            ?.takeIf { selected ->
                delta.sources.none { it.stableSessionId == selected.stableSessionId }
            }
            ?.let {
                simulateConnectionLoss(
                    MidiPortDirection.SOURCE,
                    MidiConnectionLossReason.PORT_DISAPPEARED,
                )
            }
        mutableState.value.selectedDestination
            ?.takeIf { selected ->
                delta.destinations.none { it.stableSessionId == selected.stableSessionId }
            }
            ?.let {
                simulateConnectionLoss(
                    MidiPortDirection.DESTINATION,
                    MidiConnectionLossReason.PORT_DISAPPEARED,
                )
            }
    }

    override fun refreshDevices() = Unit

    override fun selectSource(descriptor: MidiPortDescriptor?) {
        check(!closed)
        require(descriptor == null || descriptor.direction == MidiPortDirection.SOURCE)
        sourceGeneration += 1
        mutableState.value = mutableState.value.copy(
            selectedSource = descriptor,
            sourceConnection = descriptor?.let {
                MidiConnectionState(MidiConnectionPhase.OPEN, it, sourceGeneration)
            } ?: MidiConnectionState(generation = sourceGeneration),
            lastError = null,
        )
    }

    override fun selectDestination(descriptor: MidiPortDescriptor?) {
        check(!closed)
        require(descriptor == null || descriptor.direction == MidiPortDirection.DESTINATION)
        destinationGeneration += 1
        mutableState.value = mutableState.value.copy(
            selectedDestination = descriptor,
            destinationConnection = descriptor?.let {
                MidiConnectionState(MidiConnectionPhase.OPEN, it, destinationGeneration)
            } ?: MidiConnectionState(generation = destinationGeneration),
            lastError = null,
        )
    }

    override fun send(message: MidiMessage) {
        val destination = mutableState.value.selectedDestination ?: return
        sentMessages += SentMessage(destination.stableSessionId, message)
        mutableState.value = mutableState.value.copy(
            sentMessageCount = mutableState.value.sentMessageCount + 1,
        )
    }

    override fun sendTo(destinationSessionId: String, message: MidiMessage) {
        if (mutableState.value.selectedDestination?.stableSessionId != destinationSessionId) return
        send(message)
    }

    fun simulateConnectionLoss(
        direction: MidiPortDirection,
        reason: MidiConnectionLossReason,
        detail: String = "simulated loss",
    ) {
        val current = mutableState.value
        val descriptor: MidiPortDescriptor
        val generation: Long
        when (direction) {
            MidiPortDirection.SOURCE -> {
                descriptor = requireNotNull(current.selectedSource)
                generation = current.sourceConnection.generation
                sourceGeneration += 1
                mutableState.value = current.copy(
                    selectedSource = null,
                    sourceConnection = MidiConnectionState(
                        MidiConnectionPhase.LOST,
                        descriptor,
                        generation,
                        detail,
                    ),
                    lastError = detail,
                )
            }
            MidiPortDirection.DESTINATION -> {
                descriptor = requireNotNull(current.selectedDestination)
                generation = current.destinationConnection.generation
                destinationGeneration += 1
                mutableState.value = current.copy(
                    selectedDestination = null,
                    destinationConnection = MidiConnectionState(
                        MidiConnectionPhase.LOST,
                        descriptor,
                        generation,
                        detail,
                    ),
                    lastError = detail,
                )
            }
        }
        publish(MidiRepositoryEvent.ConnectionLost(direction, descriptor, generation, reason, detail))
    }

    fun rejectSend(message: MidiMessage, detail: String = "simulated send failure") {
        val current = mutableState.value
        val descriptor = requireNotNull(current.selectedDestination)
        val generation = current.destinationConnection.generation
        publish(MidiRepositoryEvent.SendFailed(descriptor, generation, message, detail))
        simulateConnectionLoss(MidiPortDirection.DESTINATION, MidiConnectionLossReason.SEND_FAILED, detail)
    }

    fun simulateOutputOverflow(droppedMessages: Long = 1L) {
        require(droppedMessages > 0L)
        val current = mutableState.value
        mutableState.value = current.copy(
            droppedOutputMessageCount = current.droppedOutputMessageCount + droppedMessages,
        )
    }

    private fun publish(event: MidiRepositoryEvent) {
        publishedEvents += event
        mutableEvents.tryEmit(event)
    }

    override fun close() {
        if (closed) return
        closed = true
        closeCount += 1
        sourceGeneration += 1
        destinationGeneration += 1
        mutableState.value = mutableState.value.copy(
            sources = emptyList(),
            destinations = emptyList(),
            selectedSource = null,
            selectedDestination = null,
            sourceConnection = MidiConnectionState(generation = sourceGeneration),
            destinationConnection = MidiConnectionState(generation = destinationGeneration),
        )
    }
}
