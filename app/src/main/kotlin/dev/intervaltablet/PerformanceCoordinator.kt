package dev.intervaltablet

import dev.intervaltablet.domain.AudioCommand
import dev.intervaltablet.domain.DefaultMidiMap
import dev.intervaltablet.domain.InstrumentAction
import dev.intervaltablet.domain.InstrumentConfig
import dev.intervaltablet.domain.InstrumentState
import dev.intervaltablet.domain.IntervalReducer
import dev.intervaltablet.domain.MidiDestinationId
import dev.intervaltablet.domain.MidiAction
import dev.intervaltablet.domain.MidiMapping
import dev.intervaltablet.domain.MidiMessage
import dev.intervaltablet.domain.MidiRouter
import dev.intervaltablet.domain.MidiRouterState
import dev.intervaltablet.domain.OutputEvent
import dev.intervaltablet.domain.PassThroughMode
import dev.intervaltablet.domain.RouterEffect
import dev.intervaltablet.domain.RouterTransition
import dev.intervaltablet.domain.ToneRowAction
import dev.intervaltablet.domain.ToneRowEvent
import dev.intervaltablet.domain.ToneRowMode
import dev.intervaltablet.domain.ToneRowReducer
import dev.intervaltablet.domain.ToneRowState
import dev.intervaltablet.domain.TransportAction
import dev.intervaltablet.domain.TransportEvent
import dev.intervaltablet.domain.TransportMode
import dev.intervaltablet.domain.TransportReducer
import dev.intervaltablet.domain.TransportState
import dev.intervaltablet.domain.TriggerSource

/**
 * App-layer deterministic coordinator. Android callbacks and Compose only enqueue commands;
 * this reducer preserves one total order for mapped, forwarded, local MIDI and audio events.
 */
data class PerformanceCoordinatorState(
    val instrument: InstrumentState,
    val router: MidiRouterState = MidiRouterState(),
    val mapping: MidiMapping = DefaultMidiMap.mapping,
    val currentDestination: MidiDestinationId = MidiDestinationId.Default,
    val activeStepsBySource: Map<TriggerSource, Int> = emptyMap(),
    val toneRow: ToneRowState = ToneRowState(),
    val transport: TransportState = TransportState(),
    val toneRowAutoSource: TriggerSource.System? = null,
    val toneRowAutoDestination: MidiDestinationId? = null,
    val toneRowVoiceCounter: Long = 0L,
) {
    init {
        require((toneRowAutoSource == null) == (toneRowAutoDestination == null))
    }

    val activeStepCounts: Map<Int, Int>
        get() = activeStepsBySource.values.groupingBy { it }.eachCount()

    companion object {
        fun initial(config: InstrumentConfig = InstrumentConfig()): PerformanceCoordinatorState {
            return PerformanceCoordinatorState(instrument = IntervalReducer().initialState(config))
        }
    }
}

sealed interface PerformanceCommand {
    data class Instrument(
        val action: InstrumentAction,
        val destination: MidiDestinationId? = null,
    ) : PerformanceCommand

    data class MidiMessages(
        val deviceId: Int,
        val portNumber: Int,
        val messages: List<MidiMessage>,
    ) : PerformanceCommand

    data class SetPassThroughMode(val mode: PassThroughMode) : PerformanceCommand
    data class SetInputChannel(val channel: Int?) : PerformanceCommand
    data class SetMapping(val mapping: MidiMapping) : PerformanceCommand

    data class ToneRow(
        val action: ToneRowAction,
        val timestampNanos: Long,
        val source: TriggerSource? = null,
    ) : PerformanceCommand {
        init {
            require(timestampNanos >= 0L)
        }
    }

    data class Transport(val action: TransportAction) : PerformanceCommand

    data class SwitchDestination(
        val destination: MidiDestinationId,
        val timestampNanos: Long,
    ) : PerformanceCommand

    data class PurgeSource(
        val deviceId: Int,
        val portNumber: Int,
        val timestampNanos: Long,
    ) : PerformanceCommand

    data class Panic(val timestampNanos: Long) : PerformanceCommand
}

sealed interface PerformanceEffect {
    data class Midi(
        val destination: MidiDestinationId,
        val message: MidiMessage,
    ) : PerformanceEffect

    data class Audio(val command: AudioCommand) : PerformanceEffect

    /** App adapter request. The unique source makes a late release harmless. */
    data class ReleaseAt(
        val source: TriggerSource.System,
        val destination: MidiDestinationId,
        val timestampNanos: Long,
    ) : PerformanceEffect {
        init {
            require(timestampNanos >= 0L)
        }
    }
}

data class PerformanceCoordinatorTransition(
    val state: PerformanceCoordinatorState,
    val effects: List<PerformanceEffect>,
)

class PerformanceCoordinator(
    private val intervalReducer: IntervalReducer = IntervalReducer(),
    private val transportReducer: TransportReducer = TransportReducer(),
) {
    fun reduce(
        state: PerformanceCoordinatorState,
        command: PerformanceCommand,
    ): PerformanceCoordinatorTransition {
        return when (command) {
            is PerformanceCommand.Instrument -> {
                if (command.action is InstrumentAction.Panic) {
                    panic(state, command.action.timestampNanos)
                } else {
                    applyInstrumentWithToneRowRouting(
                        state,
                        command.action,
                        command.destination ?: state.currentDestination,
                    )
                }
            }
            is PerformanceCommand.MidiMessages -> routeMessages(state, command)
            is PerformanceCommand.SetPassThroughMode -> PerformanceCoordinatorTransition(
                state.copy(router = MidiRouter(state.mapping).setMode(state.router, command.mode)),
                emptyList(),
            )
            is PerformanceCommand.SetInputChannel -> PerformanceCoordinatorTransition(
                state.copy(router = MidiRouter(state.mapping).setInputChannel(state.router, command.channel)),
                emptyList(),
            )
            is PerformanceCommand.SetMapping -> PerformanceCoordinatorTransition(
                state.copy(mapping = command.mapping),
                emptyList(),
            )
            is PerformanceCommand.ToneRow -> applyToneRowAction(
                state = state,
                action = command.action,
                timestampNanos = command.timestampNanos,
                source = command.source,
                synchronizeTransport = true,
            )
            is PerformanceCommand.Transport -> applyTransportAction(state, command.action)
            is PerformanceCommand.SwitchDestination -> switchDestination(state, command)
            is PerformanceCommand.PurgeSource -> {
                val routerTransition = MidiRouter(state.mapping).purgeSource(
                    state.router,
                    command.deviceId,
                    command.portNumber,
                    command.timestampNanos,
                )
                applyRouterTransition(state, routerTransition)
            }
            is PerformanceCommand.Panic -> panic(state, command.timestampNanos)
        }
    }

    private fun routeMessages(
        initial: PerformanceCoordinatorState,
        command: PerformanceCommand.MidiMessages,
    ): PerformanceCoordinatorTransition {
        var state = initial
        val effects = mutableListOf<PerformanceEffect>()
        command.messages.forEach { message ->
            val routed = MidiRouter(state.mapping).route(
                state = state.router,
                deviceId = command.deviceId,
                portNumber = command.portNumber,
                message = message,
                destination = state.currentDestination,
                outputChannel = state.instrument.config.outputChannel,
            )
            val applied = applyRouterTransition(state, routed)
            state = applied.state
            effects += applied.effects

            routed.triggeredMappedAction?.let { action ->
                val mapped = applyMappedToneRowAction(state, action, message.timestampNanos)
                state = mapped.state
                effects += mapped.effects
            }

            if (
                message is MidiMessage.RealTime &&
                state.router.mode != PassThroughMode.PASS_THRU &&
                message.status in MIDI_TRANSPORT_STATUSES
            ) {
                val transported = applyTransportAction(
                    state,
                    TransportAction.MidiRealtime(message.status, message.timestampNanos),
                )
                state = transported.state
                effects += transported.effects
            }
        }
        return PerformanceCoordinatorTransition(state, effects)
    }

    private fun switchDestination(
        state: PerformanceCoordinatorState,
        command: PerformanceCommand.SwitchDestination,
    ): PerformanceCoordinatorTransition {
        if (state.currentDestination == command.destination) {
            return PerformanceCoordinatorTransition(state, emptyList())
        }
        val released = panic(state, command.timestampNanos)
        if (command.destination == MidiDestinationId.Default) {
            return released.copy(
                state = released.state.copy(currentDestination = MidiDestinationId.Default),
            )
        }
        val reset = MidiRouter(released.state.mapping).resetDestination(
            state = released.state.router,
            destination = command.destination,
            timestampNanos = command.timestampNanos,
        )
        val appliedReset = applyRouterTransition(released.state, reset)
        return PerformanceCoordinatorTransition(
            state = appliedReset.state.copy(currentDestination = command.destination),
            effects = released.effects + appliedReset.effects,
        )
    }

    private fun panic(
        state: PerformanceCoordinatorState,
        timestampNanos: Long,
    ): PerformanceCoordinatorTransition {
        val routed = MidiRouter(state.mapping).panic(
            state = state.router,
            timestampNanos = timestampNanos,
            fallbackDestination = state.currentDestination,
            fallbackOutputChannel = state.instrument.config.outputChannel,
        )
        return applyRouterTransition(state, routed)
    }

    private fun applyRouterTransition(
        initial: PerformanceCoordinatorState,
        transition: RouterTransition,
    ): PerformanceCoordinatorTransition {
        var state = initial.copy(router = transition.state)
        val effects = mutableListOf<PerformanceEffect>()
        transition.effects.forEach { effect ->
            when (effect) {
                is RouterEffect.Instrument -> {
                    val applied = applyInstrumentWithToneRowRouting(state, effect.action, effect.destination)
                    state = applied.state
                    effects += applied.effects
                }
                is RouterEffect.Midi -> effects += PerformanceEffect.Midi(effect.destination, effect.message)
            }
        }
        return PerformanceCoordinatorTransition(state, effects)
    }

    private fun applyInstrument(
        state: PerformanceCoordinatorState,
        action: InstrumentAction,
        destination: MidiDestinationId,
    ): PerformanceCoordinatorTransition {
        val transition = intervalReducer.reduce(state.instrument, action)
        val activeSteps = when (action) {
            is InstrumentAction.PressInterval -> state.activeStepsBySource + (action.source to action.steps)
            is InstrumentAction.PressSameInterval -> state.activeStepsBySource +
                (action.source to transition.state.lastIntervalSteps)
            is InstrumentAction.PressRandomInterval -> state.activeStepsBySource +
                (action.source to transition.state.lastIntervalSteps)
            is InstrumentAction.UndoThenMove -> state.activeStepsBySource + (action.source to action.steps)
            is InstrumentAction.Release -> state.activeStepsBySource - action.source
            is InstrumentAction.SetScale,
            is InstrumentAction.SetRoot,
            is InstrumentAction.SetRange,
            is InstrumentAction.SetWrap,
            is InstrumentAction.SetOutputChannel,
            is InstrumentAction.Panic,
            -> emptyMap()
            else -> state.activeStepsBySource
        }
        val effects = transition.events.map { output ->
            when (output) {
                is OutputEvent.MidiOut -> PerformanceEffect.Midi(destination, output.message)
                is OutputEvent.Audio -> PerformanceEffect.Audio(output.command)
            }
        }
        val stoppedToneRow = if (action is InstrumentAction.Panic) {
            state.toneRow.copy(
                mode = ToneRowMode.IDLE,
                currentRecordNote = null,
                playOnce = false,
                notesRemainingInPass = 0,
            )
        } else {
            state.toneRow
        }
        val stoppedTransport = if (action is InstrumentAction.Panic) {
            state.transport.copy(mode = TransportMode.STOPPED, nextInternalTickNanos = null)
        } else {
            state.transport
        }
        val clearsAutoVoice = action is InstrumentAction.Panic ||
            (action is InstrumentAction.Release && action.source == state.toneRowAutoSource)
        val retainedAutoSource = state.toneRowAutoSource.takeUnless { clearsAutoVoice }
        val retainedAutoDestination = state.toneRowAutoDestination.takeUnless { clearsAutoVoice }
        return PerformanceCoordinatorTransition(
            state = state.copy(
                instrument = transition.state,
                activeStepsBySource = activeSteps,
                toneRow = stoppedToneRow,
                transport = stoppedTransport,
                toneRowAutoSource = retainedAutoSource,
                toneRowAutoDestination = retainedAutoDestination,
            ),
            effects = effects,
        )
    }

    private fun applyInstrumentWithToneRowRouting(
        state: PerformanceCoordinatorState,
        action: InstrumentAction,
        destination: MidiDestinationId,
    ): PerformanceCoordinatorTransition {
        return when {
            state.toneRow.mode == ToneRowMode.RECORDING && action.changesPitchGrid() -> {
                val finished = applyToneRowAction(
                    state = state,
                    action = ToneRowAction.FinishRecording,
                    timestampNanos = action.timestampNanos(),
                    source = null,
                    synchronizeTransport = false,
                    destination = destination,
                )
                val configured = applyInstrument(finished.state, action, destination)
                PerformanceCoordinatorTransition(
                    state = configured.state,
                    effects = finished.effects + configured.effects,
                )
            }
            action is InstrumentAction.PressInterval && state.toneRow.mode == ToneRowMode.RECORDING -> {
                val applied = applyToneRowAction(
                    state = state,
                    action = ToneRowAction.RecordMove(action.steps, action.velocity),
                    timestampNanos = action.timestampNanos,
                    source = action.source,
                    synchronizeTransport = false,
                    destination = destination,
                )
                applied.copy(
                    state = applied.state.copy(
                        activeStepsBySource = applied.state.activeStepsBySource + (action.source to action.steps),
                    ),
                )
            }
            action is InstrumentAction.PressInterval && state.toneRow.mode in setOf(
                ToneRowMode.MANUAL_PLAYBACK,
                ToneRowMode.PAUSED,
            ) -> {
                val applied = applyToneRowAction(
                    state = state,
                    action = ToneRowAction.ManualMove(
                        steps = action.steps,
                        velocityOverride = action.source.midiVelocityOverride(action.velocity),
                    ),
                    timestampNanos = action.timestampNanos,
                    source = action.source,
                    synchronizeTransport = false,
                    destination = destination,
                )
                applied.copy(
                    state = applied.state.copy(
                        activeStepsBySource = applied.state.activeStepsBySource + (action.source to action.steps),
                    ),
                )
            }
            action is InstrumentAction.PressSameInterval && state.toneRow.mode in setOf(
                ToneRowMode.MANUAL_PLAYBACK,
                ToneRowMode.PAUSED,
            ) -> {
                val applied = applyToneRowAction(
                    state = state,
                    action = ToneRowAction.RepeatLastManualMove(
                        velocityOverride = action.source.midiVelocityOverride(action.velocity),
                    ),
                    timestampNanos = action.timestampNanos,
                    source = action.source,
                    synchronizeTransport = false,
                    destination = destination,
                )
                applied.copy(
                    state = applied.state.copy(
                        activeStepsBySource = applied.state.activeStepsBySource +
                            (action.source to applied.state.toneRow.lastManualSteps),
                    ),
                )
            }
            action is InstrumentAction.Undo && state.toneRow.mode in setOf(
                ToneRowMode.MANUAL_PLAYBACK,
                ToneRowMode.AUTO_PLAYING,
                ToneRowMode.PAUSED,
            ) -> {
                applyToneRowAction(
                    state = state,
                    action = ToneRowAction.Restart,
                    timestampNanos = action.timestampNanos,
                    source = action.source.takeIf { state.toneRow.mode == ToneRowMode.MANUAL_PLAYBACK },
                    synchronizeTransport = state.toneRow.mode != ToneRowMode.MANUAL_PLAYBACK,
                    destination = destination,
                )
            }
            else -> applyInstrument(state, action, destination)
        }
    }

    private fun applyToneRowAction(
        state: PerformanceCoordinatorState,
        action: ToneRowAction,
        timestampNanos: Long,
        source: TriggerSource?,
        synchronizeTransport: Boolean,
        destination: MidiDestinationId = state.currentDestination,
    ): PerformanceCoordinatorTransition {
        val effectiveAction = if (
            action is ToneRowAction.StartRecording &&
            state.toneRow.mode == ToneRowMode.RECORDING
        ) {
            ToneRowAction.CancelRecording
        } else {
            action
        }
        val reduced = ToneRowReducer(state.instrument.config.grid()).reduce(state.toneRow, effectiveAction)
        var applied = applyToneRowEvents(
            state.copy(toneRow = reduced.state),
            reduced.events,
            timestampNanos,
            source,
            destination,
        )

        if (synchronizeTransport) {
            val transportAction = when (effectiveAction) {
                is ToneRowAction.StartRecording,
                ToneRowAction.FinishRecording,
                ToneRowAction.CancelRecording,
                ToneRowAction.Play,
                ToneRowAction.Stop,
                -> TransportAction.Stop(timestampNanos)
                is ToneRowAction.StartAuto -> if (applied.state.toneRow.mode == ToneRowMode.AUTO_PLAYING) {
                    if (effectiveAction.restart) {
                        TransportAction.Start(timestampNanos)
                    } else {
                        TransportAction.Continue(timestampNanos)
                    }
                } else {
                    null
                }
                ToneRowAction.PlayOnce -> if (applied.state.toneRow.mode == ToneRowMode.AUTO_PLAYING) {
                    TransportAction.Start(timestampNanos)
                } else {
                    null
                }
                ToneRowAction.PauseToggle -> when (applied.state.toneRow.mode) {
                    ToneRowMode.PAUSED -> TransportAction.Pause(timestampNanos)
                    ToneRowMode.AUTO_PLAYING -> TransportAction.Continue(timestampNanos)
                    else -> null
                }
                ToneRowAction.Restart -> if (
                    state.toneRow.mode == ToneRowMode.AUTO_PLAYING ||
                    state.toneRow.mode == ToneRowMode.PAUSED
                ) {
                    TransportAction.Start(timestampNanos)
                } else {
                    null
                }
                else -> null
            }
            if (transportAction != null) {
                applied = applied.copy(
                    state = applied.state.copy(
                        transport = transportReducer.reduce(applied.state.transport, transportAction).state,
                    ),
                )
            }
        }

        val finishedPass = reduced.events.any { it is ToneRowEvent.FinishedPass }
        if (finishedPass) {
            applied = applied.copy(
                state = applied.state.copy(
                    transport = transportReducer.reduce(
                        applied.state.transport,
                        TransportAction.Stop(timestampNanos),
                    ).state,
                ),
            )
        }

        val shouldReleaseAutoVoice = reduced.events.none { it is ToneRowEvent.PlayNote } && when (effectiveAction) {
            is ToneRowAction.StartRecording,
            ToneRowAction.FinishRecording,
            ToneRowAction.CancelRecording,
            ToneRowAction.Play,
            ToneRowAction.Stop,
            ToneRowAction.PauseToggle,
            -> true
            else -> false
        }
        if (shouldReleaseAutoVoice) {
            val released = releaseToneRowAutoVoice(applied.state, timestampNanos)
            applied = PerformanceCoordinatorTransition(
                state = released.state,
                effects = applied.effects + released.effects,
            )
        }
        return applied
    }

    private fun applyToneRowEvents(
        initial: PerformanceCoordinatorState,
        events: List<ToneRowEvent>,
        timestampNanos: Long,
        source: TriggerSource?,
        destination: MidiDestinationId,
    ): PerformanceCoordinatorTransition {
        var state = initial
        val effects = mutableListOf<PerformanceEffect>()
        events.forEach { event ->
            when (event) {
                is ToneRowEvent.PlayNote -> {
                    val voiceSource = source ?: TriggerSource.System(
                        "tone-row:auto:${state.toneRowVoiceCounter + 1L}",
                    )
                    if (source == null) {
                        val released = releaseToneRowAutoVoice(state, timestampNanos)
                        state = released.state.copy(toneRowVoiceCounter = state.toneRowVoiceCounter + 1L)
                        effects += released.effects
                    }
                    val pressAction = if (source == null) {
                        InstrumentAction.PressAbsolute(
                            source = voiceSource,
                            note = event.midiNote,
                            velocity = event.velocity,
                            timestampNanos = timestampNanos,
                        )
                    } else {
                        InstrumentAction.PressPadAbsolute(
                            source = voiceSource,
                            note = event.midiNote,
                            velocity = event.velocity,
                            timestampNanos = timestampNanos,
                        )
                    }
                    val pressed = applyInstrument(
                        state,
                        pressAction,
                        destination,
                    )
                    state = pressed.state
                    effects += pressed.effects
                    if (source == null) {
                        state = state.copy(
                            toneRowAutoSource = voiceSource as TriggerSource.System,
                            toneRowAutoDestination = destination,
                        )
                        effects += PerformanceEffect.ReleaseAt(
                            source = voiceSource,
                            destination = destination,
                            timestampNanos = safeAdd(timestampNanos, state.transport.noteDurationNanos()),
                        )
                    }
                }
                ToneRowEvent.FinishedPass -> Unit
            }
        }
        return PerformanceCoordinatorTransition(state, effects)
    }

    private fun applyTransportAction(
        state: PerformanceCoordinatorState,
        action: TransportAction,
    ): PerformanceCoordinatorTransition {
        val reduced = transportReducer.reduce(state.transport, action)
        var current = PerformanceCoordinatorTransition(state.copy(transport = reduced.state), emptyList())

        val resumed = state.transport.mode != TransportMode.PLAYING &&
            reduced.state.mode == TransportMode.PLAYING &&
            reduced.events.none { it is TransportEvent.Restart }
        if (resumed) {
            val resumeAction = when {
                current.state.toneRow.mode == ToneRowMode.PAUSED -> ToneRowAction.PauseToggle
                current.state.toneRow.mode == ToneRowMode.IDLE && current.state.toneRow.entries.isNotEmpty() -> {
                    ToneRowAction.StartAuto(restart = false)
                }
                else -> null
            }
            if (resumeAction != null) {
                current = append(
                    current,
                    applyToneRowAction(
                        current.state,
                        resumeAction,
                        action.timestampNanos(),
                        source = null,
                        synchronizeTransport = false,
                    ),
                )
            }
        }

        val pausedWithoutEvent = state.transport.mode == TransportMode.PLAYING &&
            reduced.state.mode == TransportMode.PAUSED
        if (pausedWithoutEvent && current.state.toneRow.mode == ToneRowMode.AUTO_PLAYING) {
            current = append(
                current,
                applyToneRowAction(
                    current.state,
                    ToneRowAction.PauseToggle,
                    action.timestampNanos(),
                    source = null,
                    synchronizeTransport = false,
                ),
            )
            current = append(current, releaseToneRowAutoVoice(current.state, action.timestampNanos()))
        }

        reduced.events.forEach { event ->
            val next = when (event) {
                is TransportEvent.Restart -> restartToneRowFromTransport(current.state, event.timestampNanos)
                is TransportEvent.Tick -> applyToneRowAction(
                    current.state,
                    ToneRowAction.Tick,
                    event.timestampNanos,
                    source = null,
                    synchronizeTransport = false,
                )
                is TransportEvent.Stopped -> {
                    var stopped = PerformanceCoordinatorTransition(current.state, emptyList())
                    if (stopped.state.toneRow.mode == ToneRowMode.AUTO_PLAYING) {
                        stopped = append(
                            stopped,
                            applyToneRowAction(
                                stopped.state,
                                ToneRowAction.PauseToggle,
                                event.timestampNanos,
                                source = null,
                                synchronizeTransport = false,
                            ),
                        )
                    }
                    append(stopped, releaseToneRowAutoVoice(stopped.state, event.timestampNanos))
                }
            }
            current = append(current, next)
        }
        return current
    }

    private fun applyMappedToneRowAction(
        state: PerformanceCoordinatorState,
        action: MidiAction,
        timestampNanos: Long,
    ): PerformanceCoordinatorTransition {
        val toneAction = when (action) {
            MidiAction.Play -> when (state.toneRow.mode) {
                ToneRowMode.RECORDING -> ToneRowAction.FinishRecording
                ToneRowMode.AUTO_PLAYING,
                ToneRowMode.PAUSED,
                -> ToneRowAction.PauseToggle
                ToneRowMode.IDLE,
                ToneRowMode.MANUAL_PLAYBACK,
                -> ToneRowAction.StartAuto(restart = true)
            }
            MidiAction.Stop -> ToneRowAction.Stop
            MidiAction.Record -> if (state.toneRow.mode == ToneRowMode.RECORDING) {
                ToneRowAction.CancelRecording
            } else {
                ToneRowAction.StartRecording(state.instrument.currentNote)
            }
            else -> return PerformanceCoordinatorTransition(state, emptyList())
        }
        return applyToneRowAction(
            state = state,
            action = toneAction,
            timestampNanos = timestampNanos,
            source = null,
            synchronizeTransport = true,
        )
    }

    private fun releaseToneRowAutoVoice(
        state: PerformanceCoordinatorState,
        timestampNanos: Long,
    ): PerformanceCoordinatorTransition {
        val source = state.toneRowAutoSource ?: return PerformanceCoordinatorTransition(state, emptyList())
        val destination = state.toneRowAutoDestination ?: state.currentDestination
        val released = applyInstrument(
            state,
            InstrumentAction.Release(source, timestampNanos = timestampNanos),
            destination,
        )
        return released.copy(
            state = released.state.copy(
                toneRowAutoSource = null,
                toneRowAutoDestination = null,
            ),
        )
    }

    private fun restartToneRowFromTransport(
        state: PerformanceCoordinatorState,
        timestampNanos: Long,
    ): PerformanceCoordinatorTransition {
        var current = PerformanceCoordinatorTransition(state, emptyList())
        if (current.state.toneRow.mode == ToneRowMode.RECORDING) {
            current = append(
                current,
                applyToneRowAction(
                    current.state,
                    ToneRowAction.FinishRecording,
                    timestampNanos,
                    source = null,
                    synchronizeTransport = false,
                ),
            )
        }
        if (current.state.toneRow.entries.isEmpty()) {
            val stopped = transportReducer.reduce(
                current.state.transport,
                TransportAction.Stop(timestampNanos),
            ).state
            return current.copy(state = current.state.copy(transport = stopped))
        }
        return append(
            current,
            applyToneRowAction(
                current.state,
                ToneRowAction.StartAuto(restart = true),
                timestampNanos,
                source = null,
                synchronizeTransport = false,
            ),
        )
    }

    private fun append(
        first: PerformanceCoordinatorTransition,
        second: PerformanceCoordinatorTransition,
    ): PerformanceCoordinatorTransition {
        return PerformanceCoordinatorTransition(second.state, first.effects + second.effects)
    }

    private fun TransportAction.timestampNanos(): Long {
        return when (this) {
            is TransportAction.SetClockSource -> timestampNanos
            is TransportAction.Start -> timestampNanos
            is TransportAction.Continue -> timestampNanos
            is TransportAction.Pause -> timestampNanos
            is TransportAction.Stop -> timestampNanos
            is TransportAction.InternalClock -> timestampNanos
            is TransportAction.MidiRealtime -> timestampNanos
            is TransportAction.SetTempo,
            is TransportAction.SetClocksPerStep,
            is TransportAction.SetNoteDuration,
            -> 0L
        }
    }

    private fun InstrumentAction.changesPitchGrid(): Boolean {
        return this is InstrumentAction.SetScale ||
            this is InstrumentAction.SetRoot ||
            this is InstrumentAction.SetRange ||
            this is InstrumentAction.SetWrap
    }

    private fun InstrumentAction.timestampNanos(): Long {
        return when (this) {
            is InstrumentAction.PressInterval -> timestampNanos
            is InstrumentAction.PressChromatic -> timestampNanos
            is InstrumentAction.PressSameInterval -> timestampNanos
            is InstrumentAction.PressSamePitch -> timestampNanos
            is InstrumentAction.PressRandomInterval -> timestampNanos
            is InstrumentAction.HoldChromaticShift -> timestampNanos
            is InstrumentAction.PressAbsolute -> timestampNanos
            is InstrumentAction.PressPadAbsolute -> timestampNanos
            is InstrumentAction.StrumTone -> timestampNanos
            is InstrumentAction.UndoThenMove -> timestampNanos
            is InstrumentAction.Release -> timestampNanos
            is InstrumentAction.Undo -> timestampNanos
            is InstrumentAction.Home -> timestampNanos
            is InstrumentAction.AnchorExternal -> 0L
            is InstrumentAction.SetScale -> timestampNanos
            is InstrumentAction.SetRoot -> timestampNanos
            is InstrumentAction.SetRange -> timestampNanos
            is InstrumentAction.SetWrap -> timestampNanos
            is InstrumentAction.SetChord -> timestampNanos
            is InstrumentAction.SetPadArticulation -> timestampNanos
            is InstrumentAction.SetForceToScale -> timestampNanos
            is InstrumentAction.SetOutputChannel -> timestampNanos
            is InstrumentAction.Panic -> timestampNanos
        }
    }

    private fun TriggerSource.midiVelocityOverride(velocity: Int): Int? {
        return velocity.takeIf { this is TriggerSource.Midi }
    }

    private fun safeAdd(value: Long, increment: Long): Long {
        return if (Long.MAX_VALUE - value < increment) Long.MAX_VALUE else value + increment
    }

    private companion object {
        val MIDI_TRANSPORT_STATUSES: Set<Int> = setOf(0xF8, 0xFA, 0xFB, 0xFC)
    }
}
