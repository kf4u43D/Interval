package dev.intervaltablet.domain

import kotlin.math.max

const val MIN_INTERVAL_STEPS: Int = -14
const val MAX_INTERVAL_STEPS: Int = 14

private const val DEFAULT_INSTRUMENT_RANDOM_SEED: Long = 0x49544C524E44
private const val RANDOM_MULTIPLIER: Long = 6364136223846793005L
private const val RANDOM_INCREMENT: Long = 1442695040888963407L

enum class PadArticulation {
    ARPEGGIATED,
    STACKED,
    MUTED,
}

enum class ArpeggioOrder {
    AS_PLAYED,
    UP,
    DOWN,
    UP_DOWN,
}

/** Standalone pad-arpeggiator options. Rate and gate remain shared with [TransportState]. */
data class ArpeggiatorConfig(
    val order: ArpeggioOrder = ArpeggioOrder.AS_PLAYED,
    val octaveSpan: Int = 1,
    val stepEnabled: List<Boolean> = List(8) { true },
) {
    init {
        require(octaveSpan in 1..3)
        require(stepEnabled.size == 8)
        require(stepEnabled.any { it }) { "Arpeggio pattern must contain at least one sounding step" }
    }
}

data class InstrumentConfig(
    val rootPitchClass: Int = 0,
    val scale: ScaleDefinition = ScaleLibrary.major,
    val range: MidiNoteRange = MidiNoteRange(),
    val solfegeWrap: Boolean = true,
    val outputChannel: Int = 0,
    val defaultVelocity: Int = 64,
    val chord: ChordDefinition = ChordLibrary.off,
    val padArticulation: PadArticulation = PadArticulation.ARPEGGIATED,
    val arpeggiator: ArpeggiatorConfig = ArpeggiatorConfig(),
    val forceToScale: Boolean = false,
) {
    init {
        require(rootPitchClass in 0..11)
        require(outputChannel in 0..15)
        require(defaultVelocity in 1..127)
        require(
            (range.min..range.max).any { note ->
                floorMod(note - rootPitchClass, 12) in scale.offsets
            },
        ) { "Scale, root, and range must expose at least one note" }
    }

    fun grid(): PitchGrid = PitchGrid(rootPitchClass, scale, range, solfegeWrap)
}

data class ActiveNoteInstance(
    val note: Int,
    val velocity: Int,
    val channel: Int,
) {
    init {
        require(note in 0..127)
        require(velocity in 1..127)
        require(channel in 0..15)
    }
}

/** Immutable musical context retained while one performance pad is physically held. */
data class HeldPadGesture(
    val leadNote: Int,
    val velocity: Int,
    val articulation: PadArticulation,
    val nextArpeggioVoiceIndex: Int = 0,
    val nextArpeggioStepIndex: Int = 0,
) {
    init {
        require(leadNote in 0..127)
        require(velocity in 1..127)
        require(nextArpeggioVoiceIndex >= 0)
        require(nextArpeggioStepIndex >= 0)
    }
}

data class InstrumentState(
    val config: InstrumentConfig,
    val currentNote: Int,
    val previousDistinctNotes: List<Int> = emptyList(),
    val activeBySource: Map<TriggerSource, List<ActiveNoteInstance>> = emptyMap(),
    val lastExternalNote: Int? = null,
    val lastIntervalSteps: Int = 0,
    val lastSoundedLeadNote: Int? = null,
    val lastPitchDeltaSemitones: Int = 0,
    val randomState: Long = DEFAULT_INSTRUMENT_RANDOM_SEED,
    val chromaticShiftBySource: Map<TriggerSource, Int> = emptyMap(),
    val heldPadBySource: Map<TriggerSource, HeldPadGesture> = emptyMap(),
) {
    init {
        require(config.range.contains(currentNote)) { "currentNote must belong to the configured range" }
        require(lastExternalNote == null || lastExternalNote in 0..127)
        require(lastIntervalSteps in MIN_INTERVAL_STEPS..MAX_INTERVAL_STEPS)
        require(lastSoundedLeadNote == null || lastSoundedLeadNote in 0..127)
        require(lastPitchDeltaSemitones in -127..127)
        require(chromaticShiftBySource.values.all { it in -12..12 })
        require(heldPadBySource.values.all { config.range.contains(it.leadNote) })
    }

    val activeInstanceCount: Int get() = activeBySource.values.sumOf { it.size }

    val activeChromaticShiftSemitones: Int
        get() = chromaticShiftBySource.values
            .fold(0L) { total, shift -> total + shift.toLong() }
            .coerceIn(-127L, 127L)
            .toInt()
}

sealed interface InstrumentAction {
    data class PressInterval(
        val source: TriggerSource,
        val steps: Int,
        val velocity: Int,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction {
        init {
            require(steps in MIN_INTERVAL_STEPS..MAX_INTERVAL_STEPS)
            require(velocity in 1..127)
        }
    }

    data class PressChromatic(
        val source: TriggerSource,
        val semitones: Int,
        val velocity: Int,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction {
        init {
            require(semitones in -127..127)
            require(velocity in 1..127)
        }
    }

    data class PressSameInterval(
        val source: TriggerSource,
        val velocity: Int,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction {
        init {
            require(velocity in 1..127)
        }
    }

    data class PressSamePitch(
        val source: TriggerSource,
        val velocity: Int,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction {
        init {
            require(velocity in 1..127)
        }
    }

    data class PressRandomInterval(
        val source: TriggerSource,
        val velocity: Int,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction {
        init {
            require(velocity in 1..127)
        }
    }

    /** A silent, source-owned modifier applied only to notes started while it is held. */
    data class HoldChromaticShift(
        val source: TriggerSource,
        val semitones: Int,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction {
        init {
            require(semitones in -12..12)
        }
    }

    /**
     * Plays a resolved absolute pitch through the full historical chord and ownership path.
     * Automatic Tone Row playback uses this action so it never bypasses Note Off accounting.
     */
    data class PressAbsolute(
        val source: TriggerSource,
        val note: Int,
        val velocity: Int,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction {
        init {
            require(note in 0..127)
            require(velocity in 1..127)
        }
    }

    /**
     * Plays a resolved Tone Row pitch using the selected pad articulation.
     * Manual playback uses this action; automatic playback keeps using [PressAbsolute].
     */
    data class PressPadAbsolute(
        val source: TriggerSource,
        val note: Int,
        val velocity: Int,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction {
        init {
            require(note in 0..127)
            require(velocity in 1..127)
        }
    }

    /** Plays one indexed tone of the current full voicing without moving the instrument. */
    data class StrumTone(
        val source: TriggerSource,
        val voiceIndex: Int,
        val velocity: Int,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction {
        init {
            require(velocity in 1..127)
        }
    }

    data class UndoThenMove(
        val source: TriggerSource,
        val steps: Int,
        val velocity: Int,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction {
        init {
            require(steps in MIN_INTERVAL_STEPS..MAX_INTERVAL_STEPS)
            require(velocity in 1..127)
        }
    }

    data class Release(
        val source: TriggerSource,
        val releaseVelocity: Int = 0,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction {
        init {
            require(releaseVelocity in 0..127)
        }
    }

    /** Advances exactly one voice of a held standalone arpeggio. */
    data class AdvanceArpeggio(
        val source: TriggerSource,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction

    /** Releases only the sounding arpeggio voice while retaining its physical pad session. */
    data class ReleaseArpeggioVoice(
        val source: TriggerSource,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction

    data class Undo(
        val source: TriggerSource,
        val velocity: Int,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction {
        init {
            require(velocity in 1..127)
        }
    }

    data class Home(
        val source: TriggerSource,
        val sound: Boolean,
        val velocity: Int,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction {
        init {
            require(velocity in 1..127)
        }
    }

    data class AnchorExternal(val note: Int) : InstrumentAction {
        init {
            require(note in 0..127)
        }
    }

    data class SetScale(val scale: ScaleDefinition, val timestampNanos: Long = 0L) : InstrumentAction

    data class SetRoot(val rootPitchClass: Int, val timestampNanos: Long = 0L) : InstrumentAction {
        init {
            require(rootPitchClass in 0..11)
        }
    }

    data class SetRange(val range: MidiNoteRange, val timestampNanos: Long = 0L) : InstrumentAction
    data class SetWrap(val enabled: Boolean, val timestampNanos: Long = 0L) : InstrumentAction
    data class SetChord(val chord: ChordDefinition, val timestampNanos: Long = 0L) : InstrumentAction
    data class SetArpeggiatorConfig(
        val config: ArpeggiatorConfig,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction
    data class SetPadArticulation(
        val mode: PadArticulation,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction
    data class SetForceToScale(
        val enabled: Boolean,
        val timestampNanos: Long = 0L,
    ) : InstrumentAction

    data class SetOutputChannel(val channel: Int, val timestampNanos: Long = 0L) : InstrumentAction {
        init {
            require(channel in 0..15)
        }
    }

    data class Panic(val timestampNanos: Long = 0L) : InstrumentAction
}

data class InstrumentTransition(
    val state: InstrumentState,
    val events: List<OutputEvent>,
)

class IntervalReducer {
    fun initialState(config: InstrumentConfig = InstrumentConfig()): InstrumentState {
        val grid = config.grid()
        return InstrumentState(config = config, currentNote = grid.home())
    }

    fun reduce(state: InstrumentState, action: InstrumentAction): InstrumentTransition {
        return when (action) {
            is InstrumentAction.PressInterval -> pressInterval(state, action)
            is InstrumentAction.PressChromatic -> press(
                state = state,
                source = action.source,
                target = state.config.grid().moveChromatic(state.currentNote, action.semitones),
                velocity = action.velocity,
                timestampNanos = action.timestampNanos,
                pushHistory = action.semitones != 0,
            )
            is InstrumentAction.PressSameInterval -> pressInterval(
                state,
                InstrumentAction.PressInterval(
                    source = action.source,
                    steps = state.lastIntervalSteps,
                    velocity = action.velocity,
                    timestampNanos = action.timestampNanos,
                ),
            )
            is InstrumentAction.PressSamePitch -> {
                val consumesExternalAnchor = state.lastExternalNote != null
                val grid = state.config.grid()
                val unshiftedAnchor = state.lastExternalNote ?: state.currentNote
                val transition = press(
                    state = state,
                    source = action.source,
                    target = grid.moveChromatic(unshiftedAnchor, state.lastPitchDeltaSemitones),
                    velocity = action.velocity,
                    timestampNanos = action.timestampNanos,
                    pushHistory = state.lastPitchDeltaSemitones != 0,
                    articulation = state.config.padArticulation,
                    trackHeldPad = true,
                )
                if (consumesExternalAnchor) {
                    transition.copy(state = transition.state.copy(lastExternalNote = null))
                } else {
                    transition
                }
            }
            is InstrumentAction.PressRandomInterval -> pressRandomInterval(state, action)
            is InstrumentAction.HoldChromaticShift -> InstrumentTransition(
                state = state.copy(
                    chromaticShiftBySource = state.chromaticShiftBySource +
                        (action.source to action.semitones),
                ),
                events = emptyList(),
            )
            is InstrumentAction.PressAbsolute -> press(
                state = state,
                source = action.source,
                target = action.note.coerceIn(state.config.range.min, state.config.range.max),
                velocity = action.velocity,
                timestampNanos = action.timestampNanos,
                // Absolute playback is the Tone Row output path. Its own row cursor is the
                // history; accumulating interval Undo history on every auto tick is unbounded.
                pushHistory = false,
            )
            is InstrumentAction.PressPadAbsolute -> press(
                state = state,
                source = action.source,
                target = action.note.coerceIn(state.config.range.min, state.config.range.max),
                velocity = action.velocity,
                timestampNanos = action.timestampNanos,
                pushHistory = false,
                articulation = state.config.padArticulation,
                trackHeldPad = true,
            )
            is InstrumentAction.StrumTone -> strumTone(state, action)
            is InstrumentAction.UndoThenMove -> undoThenMove(state, action)
            is InstrumentAction.Release -> releaseSource(state, action.source, action.releaseVelocity, action.timestampNanos)
            is InstrumentAction.AdvanceArpeggio -> advanceArpeggio(state, action)
            is InstrumentAction.ReleaseArpeggioVoice -> releaseOwnedInstances(
                state,
                action.source,
                action.timestampNanos,
            )
            is InstrumentAction.Undo -> undo(state, action)
            is InstrumentAction.Home -> home(state, action)
            is InstrumentAction.AnchorExternal -> InstrumentTransition(
                state.copy(
                    currentNote = action.note.coerceIn(state.config.range.min, state.config.range.max),
                    lastExternalNote = action.note,
                    lastSoundedLeadNote = action.note,
                ),
                emptyList(),
            )
            is InstrumentAction.SetScale -> setScale(state, action)
            is InstrumentAction.SetRoot -> reconfigure(
                state,
                state.config.copy(rootPitchClass = action.rootPitchClass),
                action.timestampNanos,
            )
            is InstrumentAction.SetRange -> reconfigure(
                state,
                state.config.copy(range = action.range),
                action.timestampNanos,
            )
            is InstrumentAction.SetWrap -> reconfigure(
                state,
                state.config.copy(solfegeWrap = action.enabled),
                action.timestampNanos,
            )
            is InstrumentAction.SetChord -> setChord(state, action)
            is InstrumentAction.SetArpeggiatorConfig -> setArpeggiatorConfig(state, action)
            is InstrumentAction.SetPadArticulation -> InstrumentTransition(
                state.copy(config = state.config.copy(padArticulation = action.mode)),
                emptyList(),
            )
            is InstrumentAction.SetForceToScale -> InstrumentTransition(
                state.copy(config = state.config.copy(forceToScale = action.enabled)),
                emptyList(),
            )
            is InstrumentAction.SetOutputChannel -> {
                val (cleared, events) = releaseAll(state, actionTimestamp = action.timestampNanos)
                InstrumentTransition(cleared.copy(config = cleared.config.copy(outputChannel = action.channel)), events)
            }
            is InstrumentAction.Panic -> {
                val channels = (state.activeBySource.values.flatten().map { it.channel } + state.config.outputChannel)
                    .distinct()
                    .sorted()
                val (cleared, events) = releaseAll(state, action.timestampNanos)
                InstrumentTransition(
                    cleared,
                    events + channels.flatMap { channel ->
                        listOf(
                            OutputEvent.MidiOut(MidiMessage.ControlChange(channel, 123, 0, action.timestampNanos)),
                            OutputEvent.MidiOut(MidiMessage.ControlChange(channel, 120, 0, action.timestampNanos)),
                        )
                    } + OutputEvent.Audio(AudioCommand.Panic),
                )
            }
        }
    }

    private fun pressInterval(
        state: InstrumentState,
        action: InstrumentAction.PressInterval,
    ): InstrumentTransition {
        val consumesExternalAnchor = action.steps != 0 && state.lastExternalNote != null
        val anchor = state.lastExternalNote?.takeIf { action.steps != 0 } ?: state.currentNote
        val transition = press(
            state = state,
            source = action.source,
            target = state.config.grid().move(anchor, action.steps),
            velocity = action.velocity,
            timestampNanos = action.timestampNanos,
            pushHistory = action.steps != 0,
            articulation = state.config.padArticulation,
            trackHeldPad = true,
        )
        return transition.copy(
            state = transition.state.copy(
                lastExternalNote = if (consumesExternalAnchor) null else transition.state.lastExternalNote,
                lastIntervalSteps = action.steps,
            ),
        )
    }

    private fun pressRandomInterval(
        state: InstrumentState,
        action: InstrumentAction.PressRandomInterval,
    ): InstrumentTransition {
        val nextRandomState = nextRandom(state.randomState)
        val span = MAX_INTERVAL_STEPS - MIN_INTERVAL_STEPS + 1
        val mixed = nextRandomState xor (nextRandomState ushr 33)
        val steps = MIN_INTERVAL_STEPS + floorMod((mixed ushr 1).toInt(), span)
        return pressInterval(
            state.copy(randomState = nextRandomState),
            InstrumentAction.PressInterval(
                source = action.source,
                steps = steps,
                velocity = action.velocity,
                timestampNanos = action.timestampNanos,
            ),
        )
    }

    private fun press(
        state: InstrumentState,
        source: TriggerSource,
        target: Int,
        velocity: Int,
        timestampNanos: Long,
        pushHistory: Boolean,
        articulation: PadArticulation = PadArticulation.STACKED,
        trackHeldPad: Boolean = false,
    ): InstrumentTransition {
        require(velocity in 1..127)
        val release = releaseSource(state, source, 0, timestampNanos)
        val releasedState = release.state
        val resolvedTarget = releasedState.config.forceToScale(target)
        val history = if (pushHistory && resolvedTarget != releasedState.currentNote) {
            releasedState.previousDistinctNotes + releasedState.currentNote
        } else {
            releasedState.previousDistinctNotes
        }
        val fullVoicing = buildChord(releasedState.config, resolvedTarget, velocity)
        val arpeggioVoicing = buildArpeggio(releasedState.config, resolvedTarget, velocity)
        val firstArpeggioStepEnabled = releasedState.config.arpeggiator.stepEnabled.first()
        val unshiftedInstances = when (articulation) {
            PadArticulation.ARPEGGIATED -> if (firstArpeggioStepEnabled) {
                arpeggioVoicing.take(1)
            } else {
                emptyList()
            }
            PadArticulation.STACKED -> fullVoicing
            PadArticulation.MUTED -> emptyList()
        }
        val instances = shiftInstances(
            config = releasedState.config,
            instances = unshiftedInstances,
            semitones = releasedState.activeChromaticShiftSemitones,
        )
        val nextActive = if (instances.isEmpty()) {
            releasedState.activeBySource
        } else {
            releasedState.activeBySource + (source to instances)
        }
        val nextHeldPads = if (trackHeldPad) {
            releasedState.heldPadBySource + (
                source to HeldPadGesture(
                    leadNote = resolvedTarget,
                    velocity = velocity,
                    articulation = articulation,
                    nextArpeggioVoiceIndex = if (
                        articulation == PadArticulation.ARPEGGIATED &&
                        firstArpeggioStepEnabled &&
                        arpeggioVoicing.isNotEmpty()
                    ) {
                        1 % arpeggioVoicing.size
                    } else {
                        0
                    },
                    nextArpeggioStepIndex = if (articulation == PadArticulation.ARPEGGIATED) 1 else 0,
                )
            )
        } else {
            releasedState.heldPadBySource
        }
        val noteOns = instances.flatMap { instance ->
            listOf(
                OutputEvent.MidiOut(
                    MidiMessage.NoteOn(instance.channel, instance.note, instance.velocity, timestampNanos),
                ),
                OutputEvent.Audio(AudioCommand.NoteOn(instance.note, instance.velocity)),
            )
        }
        val soundedLead = instances.firstOrNull()?.note
        val nextPitchDelta = if (soundedLead != null && releasedState.lastSoundedLeadNote != null) {
            soundedLead - releasedState.lastSoundedLeadNote
        } else {
            releasedState.lastPitchDeltaSemitones
        }
        return InstrumentTransition(
            state = releasedState.copy(
                currentNote = resolvedTarget,
                previousDistinctNotes = history,
                activeBySource = nextActive,
                heldPadBySource = nextHeldPads,
                lastSoundedLeadNote = soundedLead ?: releasedState.lastSoundedLeadNote,
                lastPitchDeltaSemitones = nextPitchDelta,
            ),
            events = release.events + noteOns,
        )
    }

    private fun advanceArpeggio(
        state: InstrumentState,
        action: InstrumentAction.AdvanceArpeggio,
    ): InstrumentTransition {
        val gesture = state.heldPadBySource[action.source]
            ?: return InstrumentTransition(state, emptyList())
        if (gesture.articulation != PadArticulation.ARPEGGIATED) {
            return InstrumentTransition(state, emptyList())
        }
        return revoiceHeldPad(state, action.source, action.timestampNanos)
    }

    private fun setChord(
        state: InstrumentState,
        action: InstrumentAction.SetChord,
    ): InstrumentTransition {
        if (action.chord == state.config.chord) return InstrumentTransition(state, emptyList())
        var nextState = state.copy(config = state.config.copy(chord = action.chord))
        val events = mutableListOf<OutputEvent>()
        state.heldPadBySource.keys.forEach { source ->
            val held = checkNotNull(nextState.heldPadBySource[source])
            nextState = nextState.copy(
                heldPadBySource = nextState.heldPadBySource + (
                    source to held.copy(nextArpeggioVoiceIndex = 0, nextArpeggioStepIndex = 0)
                ),
            )
            val revoiced = revoiceHeldPad(nextState, source, action.timestampNanos)
            nextState = revoiced.state
            events += revoiced.events
        }
        return InstrumentTransition(nextState, events)
    }

    private fun setScale(
        state: InstrumentState,
        action: InstrumentAction.SetScale,
    ): InstrumentTransition {
        if (action.scale == state.config.scale) return InstrumentTransition(state, emptyList())
        val nextConfig = state.config.copy(scale = action.scale)
        val nextGrid = nextConfig.grid()
        val remappedGestures = state.heldPadBySource.mapValues { (_, gesture) ->
            gesture.copy(
                leadNote = nextGrid.nearest(gesture.leadNote),
                nextArpeggioVoiceIndex = 0,
                nextArpeggioStepIndex = 0,
            )
        }
        var nextState = state.copy(
            config = nextConfig,
            currentNote = nextGrid.nearest(state.currentNote),
            previousDistinctNotes = emptyList(),
            heldPadBySource = remappedGestures,
        )
        val events = mutableListOf<OutputEvent>()
        remappedGestures.keys.forEach { source ->
            val revoiced = revoiceHeldPad(nextState, source, action.timestampNanos)
            nextState = revoiced.state
            events += revoiced.events
        }
        return InstrumentTransition(nextState, events)
    }

    private fun setArpeggiatorConfig(
        state: InstrumentState,
        action: InstrumentAction.SetArpeggiatorConfig,
    ): InstrumentTransition {
        if (action.config == state.config.arpeggiator) return InstrumentTransition(state, emptyList())
        var nextState = state.copy(config = state.config.copy(arpeggiator = action.config))
        val events = mutableListOf<OutputEvent>()
        state.heldPadBySource
            .filterValues { gesture -> gesture.articulation == PadArticulation.ARPEGGIATED }
            .keys
            .forEach { source ->
            val held = checkNotNull(nextState.heldPadBySource[source])
            nextState = nextState.copy(
                heldPadBySource = nextState.heldPadBySource + (
                    source to held.copy(nextArpeggioVoiceIndex = 0, nextArpeggioStepIndex = 0)
                ),
            )
            val revoiced = revoiceHeldPad(nextState, source, action.timestampNanos)
            nextState = revoiced.state
            events += revoiced.events
        }
        return InstrumentTransition(nextState, events)
    }

    private fun revoiceHeldPad(
        state: InstrumentState,
        source: TriggerSource,
        timestampNanos: Long,
    ): InstrumentTransition {
        val gesture = state.heldPadBySource[source]
            ?: return InstrumentTransition(state, emptyList())
        val release = releaseOwnedInstances(state, source, timestampNanos)
        val fullVoicing = when (gesture.articulation) {
            PadArticulation.ARPEGGIATED -> buildArpeggio(
                release.state.config,
                gesture.leadNote,
                gesture.velocity,
            )
            PadArticulation.STACKED,
            PadArticulation.MUTED,
            -> buildChord(release.state.config, gesture.leadNote, gesture.velocity)
        }
        val voiceIndex = if (fullVoicing.isEmpty()) {
            0
        } else {
            gesture.nextArpeggioVoiceIndex % fullVoicing.size
        }
        val stepIndex = gesture.nextArpeggioStepIndex % release.state.config.arpeggiator.stepEnabled.size
        val stepEnabled = release.state.config.arpeggiator.stepEnabled[stepIndex]
        val unshiftedInstances = when (gesture.articulation) {
            PadArticulation.ARPEGGIATED -> if (stepEnabled) {
                fullVoicing.getOrNull(voiceIndex)?.let(::listOf).orEmpty()
            } else {
                emptyList()
            }
            PadArticulation.STACKED -> fullVoicing
            PadArticulation.MUTED -> emptyList()
        }
        val instances = shiftInstances(
            config = release.state.config,
            instances = unshiftedInstances,
            semitones = release.state.activeChromaticShiftSemitones,
        )
        val nextVoiceIndex = if (
            gesture.articulation == PadArticulation.ARPEGGIATED && fullVoicing.isNotEmpty()
        ) {
            if (stepEnabled) (voiceIndex + 1) % fullVoicing.size else voiceIndex
        } else {
            0
        }
        val nextStepIndex = if (gesture.articulation == PadArticulation.ARPEGGIATED) {
            (stepIndex + 1) % release.state.config.arpeggiator.stepEnabled.size
        } else {
            0
        }
        val nextActive = if (instances.isEmpty()) {
            release.state.activeBySource
        } else {
            release.state.activeBySource + (source to instances)
        }
        val noteOns = instances.flatMap { instance ->
            listOf(
                OutputEvent.MidiOut(
                    MidiMessage.NoteOn(instance.channel, instance.note, instance.velocity, timestampNanos),
                ),
                OutputEvent.Audio(AudioCommand.NoteOn(instance.note, instance.velocity)),
            )
        }
        val soundedNote = instances.firstOrNull()?.note
        val nextPitchDelta = if (soundedNote != null && release.state.lastSoundedLeadNote != null) {
            soundedNote - release.state.lastSoundedLeadNote
        } else {
            release.state.lastPitchDeltaSemitones
        }
        return InstrumentTransition(
            state = release.state.copy(
                activeBySource = nextActive,
                heldPadBySource = release.state.heldPadBySource + (
                    source to gesture.copy(
                        nextArpeggioVoiceIndex = nextVoiceIndex,
                        nextArpeggioStepIndex = nextStepIndex,
                    )
                ),
                lastSoundedLeadNote = soundedNote ?: release.state.lastSoundedLeadNote,
                lastPitchDeltaSemitones = nextPitchDelta,
            ),
            events = release.events + noteOns,
        )
    }

    private fun strumTone(
        state: InstrumentState,
        action: InstrumentAction.StrumTone,
    ): InstrumentTransition {
        val note = state.strumNotes().getOrNull(action.voiceIndex)
            ?: return InstrumentTransition(state, emptyList())
        val released = releaseSource(state, action.source, 0, action.timestampNanos)
        val instance = shiftInstances(
            config = released.state.config,
            instances = listOf(
                ActiveNoteInstance(
                    note = note,
                    velocity = action.velocity,
                    channel = released.state.config.outputChannel,
                ),
            ),
            semitones = released.state.activeChromaticShiftSemitones,
        ).single()
        return InstrumentTransition(
            state = released.state.copy(
                activeBySource = released.state.activeBySource + (action.source to listOf(instance)),
            ),
            events = released.events + listOf(
                OutputEvent.MidiOut(
                    MidiMessage.NoteOn(instance.channel, instance.note, instance.velocity, action.timestampNanos),
                ),
                OutputEvent.Audio(AudioCommand.NoteOn(instance.note, instance.velocity)),
            ),
        )
    }

    private fun undo(state: InstrumentState, action: InstrumentAction.Undo): InstrumentTransition {
        val previous = state.previousDistinctNotes.lastOrNull() ?: state.currentNote
        val trimmed = if (state.previousDistinctNotes.isEmpty()) {
            state.previousDistinctNotes
        } else {
            state.previousDistinctNotes.dropLast(1)
        }
        val base = state.copy(previousDistinctNotes = trimmed)
        return press(
            state = base,
            source = action.source,
            target = previous,
            velocity = action.velocity,
            timestampNanos = action.timestampNanos,
            pushHistory = false,
        )
    }

    private fun undoThenMove(
        state: InstrumentState,
        action: InstrumentAction.UndoThenMove,
    ): InstrumentTransition {
        val previous = state.previousDistinctNotes.lastOrNull() ?: state.currentNote
        val trimmed = if (state.previousDistinctNotes.isEmpty()) {
            state.previousDistinctNotes
        } else {
            state.previousDistinctNotes.dropLast(1)
        }
        val base = state.copy(currentNote = previous, previousDistinctNotes = trimmed)
        val transition = press(
            state = base,
            source = action.source,
            target = base.config.grid().move(previous, action.steps),
            velocity = action.velocity,
            timestampNanos = action.timestampNanos,
            pushHistory = action.steps != 0,
        )
        return transition.copy(
            state = transition.state.copy(lastIntervalSteps = action.steps),
        )
    }

    private fun home(state: InstrumentState, action: InstrumentAction.Home): InstrumentTransition {
        val target = state.config.grid().home()
        if (!action.sound) {
            val release = releaseSource(state, action.source, 0, action.timestampNanos)
            val history = if (target != release.state.currentNote) {
                release.state.previousDistinctNotes + release.state.currentNote
            } else {
                release.state.previousDistinctNotes
            }
            return InstrumentTransition(
                release.state.copy(currentNote = target, previousDistinctNotes = history),
                release.events,
            )
        }
        return press(
            state = state,
            source = action.source,
            target = target,
            velocity = action.velocity,
            timestampNanos = action.timestampNanos,
            pushHistory = true,
        )
    }

    private fun releaseSource(
        state: InstrumentState,
        source: TriggerSource,
        releaseVelocity: Int,
        timestampNanos: Long,
    ): InstrumentTransition {
        val instances = state.activeBySource[source].orEmpty()
        val events = instances.flatMap { instance ->
            listOf(
                OutputEvent.MidiOut(
                    MidiMessage.NoteOff(instance.channel, instance.note, releaseVelocity.coerceIn(0, 127), timestampNanos),
                ),
                OutputEvent.Audio(AudioCommand.NoteOff(instance.note)),
            )
        }
        return InstrumentTransition(
            state.copy(
                activeBySource = state.activeBySource - source,
                chromaticShiftBySource = state.chromaticShiftBySource - source,
                heldPadBySource = state.heldPadBySource - source,
            ),
            events,
        )
    }

    private fun releaseOwnedInstances(
        state: InstrumentState,
        source: TriggerSource,
        timestampNanos: Long,
    ): InstrumentTransition {
        val instances = state.activeBySource[source].orEmpty()
        val events = instances.flatMap { instance ->
            listOf(
                OutputEvent.MidiOut(MidiMessage.NoteOff(instance.channel, instance.note, 0, timestampNanos)),
                OutputEvent.Audio(AudioCommand.NoteOff(instance.note)),
            )
        }
        return InstrumentTransition(state.copy(activeBySource = state.activeBySource - source), events)
    }

    private fun releaseAll(state: InstrumentState, actionTimestamp: Long): Pair<InstrumentState, List<OutputEvent>> {
        val events = state.activeBySource.values.flatten().flatMap { instance ->
            listOf(
                OutputEvent.MidiOut(MidiMessage.NoteOff(instance.channel, instance.note, 0, actionTimestamp)),
                OutputEvent.Audio(AudioCommand.NoteOff(instance.note)),
            )
        }
        return state.copy(
            activeBySource = emptyMap(),
            chromaticShiftBySource = emptyMap(),
            heldPadBySource = emptyMap(),
        ) to events
    }

    private fun reconfigure(
        state: InstrumentState,
        config: InstrumentConfig,
        timestampNanos: Long,
    ): InstrumentTransition {
        val (cleared, releaseEvents) = releaseAll(state, actionTimestamp = timestampNanos)
        val grid = config.grid()
        return InstrumentTransition(
            state = cleared.copy(
                config = config,
                currentNote = grid.nearest(cleared.currentNote),
                previousDistinctNotes = emptyList(),
            ),
            events = releaseEvents,
        )
    }

    private fun buildChord(config: InstrumentConfig, lead: Int, velocity: Int): List<ActiveNoteInstance> {
        return resolveVoicingNotes(config, lead).mapIndexed { index, note ->
            ActiveNoteInstance(
                note = note,
                velocity = if (index == 0) velocity else max(1, velocity / 2),
                channel = config.outputChannel,
            )
        }
    }

    private fun buildArpeggio(
        config: InstrumentConfig,
        lead: Int,
        velocity: Int,
    ): List<ActiveNoteInstance> {
        val expanded = buildChord(config, lead, velocity).flatMap { instance ->
            (0 until config.arpeggiator.octaveSpan).mapNotNull { octave ->
                val note = instance.note + octave * 12
                instance.copy(note = note).takeIf { config.range.contains(note) }
            }
        }
        val ascending = expanded.sortedBy(ActiveNoteInstance::note)
        return when (config.arpeggiator.order) {
            ArpeggioOrder.AS_PLAYED -> expanded
            ArpeggioOrder.UP -> ascending
            ArpeggioOrder.DOWN -> ascending.asReversed()
            ArpeggioOrder.UP_DOWN -> if (ascending.size <= 2) {
                ascending
            } else {
                ascending + ascending.subList(1, ascending.lastIndex).asReversed()
            }
        }
    }

    private fun shiftInstances(
        config: InstrumentConfig,
        instances: List<ActiveNoteInstance>,
        semitones: Int,
    ): List<ActiveNoteInstance> {
        if (semitones == 0 && !config.forceToScale) return instances
        val grid = config.grid()
        return instances.map { instance ->
            val shifted = if (semitones == 0) {
                instance.note
            } else {
                grid.moveChromatic(instance.note, semitones)
            }
            instance.copy(note = if (config.forceToScale) grid.nearest(shifted) else shifted)
        }
    }

    private fun nextRandom(value: Long): Long = value * RANDOM_MULTIPLIER + RANDOM_INCREMENT

}

/** Quantizes only notes generated by the instrument; routed MIDI remains untouched. */
private fun InstrumentConfig.forceToScale(note: Int): Int {
    return if (forceToScale) grid().nearest(note) else note
}

/** Three-octave current voicing, preserving duplicate strings and omitting out-of-range tones. */
fun InstrumentState.strumNotes(): List<Int> = resolveVoicingNotes(config, currentNote)
    .flatMap { note -> listOf(note - 12, note, note + 12) }
    .filter(config.range::contains)
    .sorted()

/** True when a retained pad owns a multi-voice standalone arpeggio. */
fun InstrumentState.hasStandaloneArpeggio(source: TriggerSource): Boolean {
    val gesture = heldPadBySource[source] ?: return false
    if (gesture.articulation != PadArticulation.ARPEGGIATED) return false
    val soundingVoices = resolveVoicingNotes(config, gesture.leadNote).sumOf { note ->
        (0 until config.arpeggiator.octaveSpan).count { octave ->
            config.range.contains(note + octave * 12)
        }
    }
    return soundingVoices > 1 || config.arpeggiator.stepEnabled.any { !it }
}

private fun resolveVoicingNotes(config: InstrumentConfig, lead: Int): List<Int> {
    val grid = config.grid()
    return config.chord.tones.mapNotNull { tone ->
        val note = when (tone) {
            is ChordTone.Degree -> grid.moveUnbounded(lead, tone.steps)
            is ChordTone.Octave -> lead + tone.octaves * 12
        }
        note.takeIf(config.range::contains)
    }
}
