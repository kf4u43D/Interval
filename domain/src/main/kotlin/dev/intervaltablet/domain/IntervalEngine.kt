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

data class InstrumentConfig(
    val rootPitchClass: Int = 0,
    val scale: ScaleDefinition = ScaleLibrary.major,
    val range: MidiNoteRange = MidiNoteRange(),
    val solfegeWrap: Boolean = true,
    val outputChannel: Int = 0,
    val defaultVelocity: Int = 64,
    val chord: ChordDefinition = ChordLibrary.off,
    val padArticulation: PadArticulation = PadArticulation.ARPEGGIATED,
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
) {
    init {
        require(config.range.contains(currentNote)) { "currentNote must belong to the configured range" }
        require(lastExternalNote == null || lastExternalNote in 0..127)
        require(lastIntervalSteps in MIN_INTERVAL_STEPS..MAX_INTERVAL_STEPS)
        require(lastSoundedLeadNote == null || lastSoundedLeadNote in 0..127)
        require(lastPitchDeltaSemitones in -127..127)
        require(chromaticShiftBySource.values.all { it in -12..12 })
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
    data class SetPadArticulation(
        val mode: PadArticulation,
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
            )
            is InstrumentAction.StrumTone -> strumTone(state, action)
            is InstrumentAction.UndoThenMove -> undoThenMove(state, action)
            is InstrumentAction.Release -> releaseSource(state, action.source, action.releaseVelocity, action.timestampNanos)
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
            is InstrumentAction.SetScale -> reconfigure(
                state,
                state.config.copy(scale = action.scale),
                action.timestampNanos,
            )
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
            is InstrumentAction.SetChord -> InstrumentTransition(state.copy(config = state.config.copy(chord = action.chord)), emptyList())
            is InstrumentAction.SetPadArticulation -> InstrumentTransition(
                state.copy(config = state.config.copy(padArticulation = action.mode)),
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
    ): InstrumentTransition {
        require(velocity in 1..127)
        val release = releaseSource(state, source, 0, timestampNanos)
        val releasedState = release.state
        val history = if (pushHistory && target != releasedState.currentNote) {
            releasedState.previousDistinctNotes + releasedState.currentNote
        } else {
            releasedState.previousDistinctNotes
        }
        val fullVoicing = buildChord(releasedState.config, target, velocity)
        val unshiftedInstances = when (articulation) {
            PadArticulation.ARPEGGIATED -> fullVoicing.take(1)
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
                currentNote = target,
                previousDistinctNotes = history,
                activeBySource = nextActive,
                lastSoundedLeadNote = soundedLead ?: releasedState.lastSoundedLeadNote,
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
            ),
            events,
        )
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

    private fun shiftInstances(
        config: InstrumentConfig,
        instances: List<ActiveNoteInstance>,
        semitones: Int,
    ): List<ActiveNoteInstance> {
        if (semitones == 0) return instances
        val grid = config.grid()
        return instances.map { instance ->
            instance.copy(note = grid.moveChromatic(instance.note, semitones))
        }
    }

    private fun nextRandom(value: Long): Long = value * RANDOM_MULTIPLIER + RANDOM_INCREMENT

}

/** Full current voicing in chord-definition order, preserving duplicates and omitting out-of-range tones. */
fun InstrumentState.strumNotes(): List<Int> = resolveVoicingNotes(config, currentNote)

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
