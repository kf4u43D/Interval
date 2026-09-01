package dev.intervaltablet.domain

import kotlin.math.abs

private const val DEFAULT_TONE_ROW_RANDOM_SEED: Long = 0x49544C54424C
private const val MAX_TONE_ROW_SIZE: Int = 12
private const val MAX_TONE_ROW_SEQUENCE_SIZE: Int = 64
private const val MIN_TONE_ROW_TRANSFORMATION: Int = -127
private const val MAX_TONE_ROW_TRANSFORMATION: Int = 127
private const val MIN_TONE_ROW_OCTAVE: Int = -10
private const val MAX_TONE_ROW_OCTAVE: Int = 10

enum class ToneRowMode {
    IDLE,
    RECORDING,
    MANUAL_PLAYBACK,
    AUTO_PLAYING,
    PAUSED,
}

enum class ToneRowPlayMode { PRIME, RETRO, RANDOM, PENDULUM }

/**
 * One immutable Tone Row element.
 *
 * [relativeDegree] is recorded against the scale/key captured by [ToneRowState]. It is
 * deliberately kept instead of only storing an absolute MIDI note so that a different
 * playback grid can preserve the contour. [recordedMidiNote] and [recordedPitchClass]
 * retain enough information to validate uniqueness and render the original take.
 */
data class ToneRowEntry(
    val relativeDegree: Int,
    val recordedMidiNote: Int,
    val velocity: Int,
    val recordedPitchClass: Int = floorMod(recordedMidiNote, 12),
) {
    init {
        require(relativeDegree in MIN_TONE_ROW_TRANSFORMATION..MAX_TONE_ROW_TRANSFORMATION)
        require(recordedMidiNote in 0..127)
        require(velocity in 1..127)
        require(recordedPitchClass in 0..11)
        require(recordedPitchClass == floorMod(recordedMidiNote, 12))
    }
}

/**
 * Complete deterministic Tone Row state. Lists exposed here are treated as immutable.
 * Restore adapters must construct a valid state; invalid/corrupt snapshots are rejected
 * by these invariants rather than being allowed to fail later in playback.
 */
data class ToneRowState(
    val mode: ToneRowMode = ToneRowMode.IDLE,
    val entries: List<ToneRowEntry> = emptyList(),
    val currentRecordNote: Int? = null,
    val rowIndex: Int = 0,
    val intervalSequence: List<Int> = listOf(1),
    val sequenceIndex: Int = 0,
    val playMode: ToneRowPlayMode = ToneRowPlayMode.PRIME,
    val inverted: Boolean = false,
    val transpositionSemitones: Int = 0,
    val translation: Int = 0,
    val octaveOffset: Int = 0,
    val pendulumDirection: Int = 1,
    val randomState: Long = DEFAULT_TONE_ROW_RANDOM_SEED,
    val recordingCapacity: Int = 0,
    val referenceRootPitchClass: Int = 0,
    val referenceScaleId: String = ScaleLibrary.major.id,
    val playOnce: Boolean = false,
    val notesRemainingInPass: Int = 0,
) {
    init {
        require(entries.size <= MAX_TONE_ROW_SIZE)
        require(entries.map { it.recordedPitchClass }.distinct().size == entries.size) {
            "Tone Row entries must use distinct pitch classes"
        }
        require(rowIndex == 0 || rowIndex in entries.indices)
        require(intervalSequence.isNotEmpty())
        require(intervalSequence.size <= MAX_TONE_ROW_SEQUENCE_SIZE)
        require(intervalSequence.all { it in MIN_INTERVAL_STEPS..MAX_INTERVAL_STEPS })
        require(sequenceIndex in intervalSequence.indices)
        require(transpositionSemitones in MIN_TONE_ROW_TRANSFORMATION..MAX_TONE_ROW_TRANSFORMATION)
        require(translation in MIN_TONE_ROW_TRANSFORMATION..MAX_TONE_ROW_TRANSFORMATION)
        require(octaveOffset in MIN_TONE_ROW_OCTAVE..MAX_TONE_ROW_OCTAVE)
        require(pendulumDirection == -1 || pendulumDirection == 1)
        require(recordingCapacity in 0..MAX_TONE_ROW_SIZE)
        require(recordingCapacity == 0 || entries.size <= recordingCapacity)
        require(referenceRootPitchClass in 0..11)
        require(referenceScaleId.isNotBlank())
        require(notesRemainingInPass in 0..MAX_TONE_ROW_SIZE)
        require(notesRemainingInPass <= entries.size)
        require(playOnce || notesRemainingInPass == 0)
        require(!playOnce || mode == ToneRowMode.AUTO_PLAYING || mode == ToneRowMode.PAUSED)
        require(!playOnce || notesRemainingInPass > 0)
        require(mode !in setOf(ToneRowMode.AUTO_PLAYING, ToneRowMode.PAUSED) || entries.isNotEmpty())
        require(currentRecordNote == null || currentRecordNote in 0..127)
        require(mode != ToneRowMode.RECORDING || recordingCapacity > 0)
        require(mode != ToneRowMode.RECORDING || currentRecordNote != null)
    }
}

sealed interface ToneRowAction {
    /** Clears the previous row and captures the current scale/key as recording reference. */
    data class StartRecording(val anchorNote: Int) : ToneRowAction {
        init {
            require(anchorNote in 0..127)
        }
    }

    data class RecordMove(val steps: Int, val velocity: Int) : ToneRowAction {
        init {
            require(steps in MIN_INTERVAL_STEPS..MAX_INTERVAL_STEPS)
            require(velocity in 1..127)
        }
    }

    /** Early recording completion; equivalent to Play while recording. */
    data object FinishRecording : ToneRowAction

    /** Stops while retaining both row contents and the current row position. */
    data object Stop : ToneRowAction

    /** Enters manual playback at the first element and plays it immediately. */
    data object Play : ToneRowAction

    /** Starts continuous Auto Play. [restart] selects Start versus Continue semantics. */
    data class StartAuto(val restart: Boolean = true) : ToneRowAction

    /** Emits exactly one row-length pass, including the first immediate emission. */
    data object PlayOnce : ToneRowAction

    data object PauseToggle : ToneRowAction

    data class ManualMove(val steps: Int) : ToneRowAction {
        init {
            require(steps in MIN_INTERVAL_STEPS..MAX_INTERVAL_STEPS)
        }
    }

    data object Restart : ToneRowAction
    data object Tick : ToneRowAction
    data class SetPlayMode(val mode: ToneRowPlayMode) : ToneRowAction
    data class SetInverted(val enabled: Boolean) : ToneRowAction

    data class SetIntervalSequence(val steps: List<Int>) : ToneRowAction {
        init {
            require(steps.isNotEmpty())
            require(steps.size <= MAX_TONE_ROW_SEQUENCE_SIZE)
            require(steps.all { it in MIN_INTERVAL_STEPS..MAX_INTERVAL_STEPS })
        }
    }

    data class AddSequenceStep(val steps: Int) : ToneRowAction {
        init {
            require(steps in MIN_INTERVAL_STEPS..MAX_INTERVAL_STEPS)
        }
    }

    data class SelectSequenceStep(val index: Int) : ToneRowAction {
        init {
            require(index >= 0)
        }
    }

    /** Deleting the final remaining step resets it to the mandatory default `{+1}`. */
    data class DeleteSequenceStep(val index: Int) : ToneRowAction {
        init {
            require(index >= 0)
        }
    }

    data class SetRandomSeed(val seed: Long) : ToneRowAction

    data class SetTransposition(val semitones: Int) : ToneRowAction {
        init {
            require(semitones in MIN_TONE_ROW_TRANSFORMATION..MAX_TONE_ROW_TRANSFORMATION)
        }
    }

    data class SetTranslation(val steps: Int) : ToneRowAction {
        init {
            require(steps in MIN_TONE_ROW_TRANSFORMATION..MAX_TONE_ROW_TRANSFORMATION)
        }
    }

    data class SetOctaveOffset(val octaves: Int) : ToneRowAction
    data object ResetTransformations : ToneRowAction
}

sealed interface ToneRowEvent {
    data class PlayNote(val midiNote: Int, val velocity: Int) : ToneRowEvent {
        init {
            require(midiNote in 0..127)
            require(velocity in 1..127)
        }
    }

    /** Emitted after, and in the same transition as, the final Play Once note. */
    data object FinishedPass : ToneRowEvent
}

data class ToneRowTransition(
    val state: ToneRowState,
    val events: List<ToneRowEvent> = emptyList(),
)

/** Pure `state + action -> state + events` Tone Row reducer. */
class ToneRowReducer(private val grid: PitchGrid) {
    fun reduce(state: ToneRowState, action: ToneRowAction): ToneRowTransition {
        return when (action) {
            is ToneRowAction.StartRecording -> startRecording(state, action.anchorNote)
            is ToneRowAction.RecordMove -> recordMove(state, action)
            ToneRowAction.FinishRecording -> finishRecording(state)
            ToneRowAction.Stop -> stop(state)
            ToneRowAction.Play -> playManual(state)
            is ToneRowAction.StartAuto -> startAuto(state, restart = action.restart, playOnce = false)
            ToneRowAction.PlayOnce -> startAuto(state, restart = true, playOnce = true)
            ToneRowAction.PauseToggle -> pauseToggle(state)
            is ToneRowAction.ManualMove -> manualMove(state, action.steps)
            ToneRowAction.Restart -> restart(state)
            ToneRowAction.Tick -> tick(state)
            is ToneRowAction.SetPlayMode -> setPlayMode(state, action.mode)
            is ToneRowAction.SetInverted -> ToneRowTransition(state.copy(inverted = action.enabled))
            is ToneRowAction.SetIntervalSequence -> setIntervalSequence(state, action.steps)
            is ToneRowAction.AddSequenceStep -> addSequenceStep(state, action.steps)
            is ToneRowAction.SelectSequenceStep -> selectSequenceStep(state, action.index)
            is ToneRowAction.DeleteSequenceStep -> deleteSequenceStep(state, action.index)
            is ToneRowAction.SetRandomSeed -> ToneRowTransition(state.copy(randomState = action.seed))
            is ToneRowAction.SetTransposition -> ToneRowTransition(
                state.copy(transpositionSemitones = action.semitones),
            )
            is ToneRowAction.SetTranslation -> ToneRowTransition(state.copy(translation = action.steps))
            is ToneRowAction.SetOctaveOffset -> ToneRowTransition(
                state.copy(octaveOffset = action.octaves.coerceIn(MIN_TONE_ROW_OCTAVE, MAX_TONE_ROW_OCTAVE)),
            )
            ToneRowAction.ResetTransformations -> resetTransformations(state)
        }
    }

    private fun startRecording(state: ToneRowState, anchorNote: Int): ToneRowTransition {
        val anchor = if (grid.range.contains(anchorNote)) anchorNote else grid.nearest(anchorNote)
        val reachablePitchClassCount = grid.allNotes().map { floorMod(it, 12) }.distinct().size
        return ToneRowTransition(
            state.copy(
                mode = ToneRowMode.RECORDING,
                entries = emptyList(),
                currentRecordNote = anchor,
                rowIndex = 0,
                sequenceIndex = 0,
                pendulumDirection = 1,
                recordingCapacity = minOf(grid.scale.offsets.size, reachablePitchClassCount),
                referenceRootPitchClass = grid.rootPitchClass,
                referenceScaleId = grid.scale.id,
                playOnce = false,
                notesRemainingInPass = 0,
            ),
        )
    }

    private fun recordMove(state: ToneRowState, action: ToneRowAction.RecordMove): ToneRowTransition {
        if (state.mode != ToneRowMode.RECORDING) return ToneRowTransition(state)
        val capacity = state.recordingCapacity.takeIf { it > 0 } ?: grid.scale.offsets.size
        if (state.entries.size >= capacity) return finishRecording(state)

        val anchor = state.currentRecordNote ?: grid.home()
        val usedPitchClasses = state.entries.map { it.recordedPitchClass }.toSet()
        val initialCandidate = grid.move(anchor, action.steps)
        val candidate = findAvailableCandidate(
            initialCandidate = initialCandidate,
            requestedSteps = action.steps,
            usedPitchClasses = usedPitchClasses,
        ) ?: return ToneRowTransition(state)

        val entry = ToneRowEntry(
            relativeDegree = grid.relativeDegree(candidate),
            recordedMidiNote = candidate,
            velocity = action.velocity,
        )
        val nextEntries = state.entries + entry
        val completed = nextEntries.size >= capacity
        return ToneRowTransition(
            state.copy(
                mode = if (completed) ToneRowMode.MANUAL_PLAYBACK else ToneRowMode.RECORDING,
                entries = nextEntries,
                currentRecordNote = if (completed) null else candidate,
                rowIndex = 0,
                playOnce = false,
                notesRemainingInPass = 0,
            ),
            events = listOf(ToneRowEvent.PlayNote(candidate, entry.velocity)),
        )
    }

    private fun findAvailableCandidate(
        initialCandidate: Int,
        requestedSteps: Int,
        usedPitchClasses: Set<Int>,
    ): Int? {
        if (floorMod(initialCandidate, 12) !in usedPitchClasses) return initialCandidate
        val direction = requestedSteps.compareTo(0)
        if (direction == 0) return null

        var candidate = initialCandidate
        repeat(grid.size) {
            val next = grid.move(candidate, direction)
            if (next == candidate) return null
            candidate = next
            if (floorMod(candidate, 12) !in usedPitchClasses) return candidate
        }
        return null
    }

    private fun finishRecording(state: ToneRowState): ToneRowTransition {
        if (state.mode != ToneRowMode.RECORDING) return ToneRowTransition(state)
        return if (state.entries.isEmpty()) {
            ToneRowTransition(
                state.copy(
                    mode = ToneRowMode.IDLE,
                    currentRecordNote = null,
                    recordingCapacity = 0,
                ),
            )
        } else {
            ToneRowTransition(
                state.copy(
                    mode = ToneRowMode.MANUAL_PLAYBACK,
                    currentRecordNote = null,
                    rowIndex = 0,
                    playOnce = false,
                    notesRemainingInPass = 0,
                ),
            )
        }
    }

    private fun stop(state: ToneRowState): ToneRowTransition {
        val cancelledEmptyRecording = state.mode == ToneRowMode.RECORDING && state.entries.isEmpty()
        return ToneRowTransition(
            state.copy(
                mode = ToneRowMode.IDLE,
                currentRecordNote = null,
                recordingCapacity = if (cancelledEmptyRecording) 0 else state.recordingCapacity,
                playOnce = false,
                notesRemainingInPass = 0,
            ),
        )
    }

    private fun playManual(state: ToneRowState): ToneRowTransition {
        if (state.mode == ToneRowMode.RECORDING) return finishRecording(state)
        if (state.entries.isEmpty()) return ToneRowTransition(state)
        val next = state.copy(
            mode = ToneRowMode.MANUAL_PLAYBACK,
            rowIndex = 0,
            sequenceIndex = 0,
            pendulumDirection = 1,
            playOnce = false,
            notesRemainingInPass = 0,
        )
        return emitCurrent(next)
    }

    private fun startAuto(state: ToneRowState, restart: Boolean, playOnce: Boolean): ToneRowTransition {
        if (state.mode == ToneRowMode.RECORDING || state.entries.isEmpty()) return ToneRowTransition(state)
        val initial = if (restart) startPosition(state) else state
        val playing = initial.copy(
            mode = ToneRowMode.AUTO_PLAYING,
            sequenceIndex = if (restart) 0 else initial.sequenceIndex,
            pendulumDirection = if (restart) 1 else initial.pendulumDirection,
            playOnce = playOnce,
            notesRemainingInPass = if (playOnce) initial.entries.size else 0,
        )
        val emitted = emitCurrent(playing)
        return consumePlayOnceEmission(emitted)
    }

    private fun startPosition(state: ToneRowState): ToneRowState {
        return when (state.playMode) {
            ToneRowPlayMode.PRIME,
            ToneRowPlayMode.PENDULUM,
            -> state.copy(rowIndex = 0, sequenceIndex = 0, pendulumDirection = 1)
            ToneRowPlayMode.RETRO -> state.copy(
                rowIndex = state.entries.lastIndex,
                sequenceIndex = 0,
                pendulumDirection = -1,
            )
            ToneRowPlayMode.RANDOM -> {
                val random = nextRandom(state.randomState)
                state.copy(
                    rowIndex = randomIndex(random, state.entries.size),
                    sequenceIndex = 0,
                    pendulumDirection = 1,
                    randomState = random,
                )
            }
        }
    }

    private fun pauseToggle(state: ToneRowState): ToneRowTransition {
        return when (state.mode) {
            ToneRowMode.AUTO_PLAYING -> ToneRowTransition(state.copy(mode = ToneRowMode.PAUSED))
            ToneRowMode.PAUSED -> ToneRowTransition(state.copy(mode = ToneRowMode.AUTO_PLAYING))
            ToneRowMode.IDLE,
            ToneRowMode.RECORDING,
            ToneRowMode.MANUAL_PLAYBACK,
            -> ToneRowTransition(state)
        }
    }

    private fun manualMove(state: ToneRowState, steps: Int): ToneRowTransition {
        if (state.mode != ToneRowMode.MANUAL_PLAYBACK || state.entries.isEmpty()) {
            return ToneRowTransition(state)
        }
        val nextIndex = floorMod(state.rowIndex + steps, state.entries.size)
        return emitCurrent(state.copy(rowIndex = nextIndex))
    }

    private fun restart(state: ToneRowState): ToneRowTransition {
        if (state.entries.isEmpty() || state.mode == ToneRowMode.RECORDING) return ToneRowTransition(state)
        val positioned = startPosition(state)
        val next = positioned.copy(
            mode = when (state.mode) {
                ToneRowMode.IDLE -> ToneRowMode.MANUAL_PLAYBACK
                ToneRowMode.PAUSED -> ToneRowMode.AUTO_PLAYING
                ToneRowMode.RECORDING,
                ToneRowMode.MANUAL_PLAYBACK,
                ToneRowMode.AUTO_PLAYING,
                -> state.mode
            },
            notesRemainingInPass = if (state.playOnce) state.entries.size else 0,
        )
        val emitted = emitCurrent(next)
        return consumePlayOnceEmission(emitted)
    }

    private fun tick(state: ToneRowState): ToneRowTransition {
        if (state.mode != ToneRowMode.AUTO_PLAYING || state.entries.isEmpty()) return ToneRowTransition(state)
        val sequenceStep = state.intervalSequence[state.sequenceIndex]
        val nextSequenceIndex = floorMod(state.sequenceIndex + 1, state.intervalSequence.size)
        val moved = when (state.playMode) {
            ToneRowPlayMode.PRIME -> state.copy(
                rowIndex = floorMod(state.rowIndex + sequenceStep, state.entries.size),
                sequenceIndex = nextSequenceIndex,
            )
            ToneRowPlayMode.RETRO -> state.copy(
                rowIndex = floorMod(state.rowIndex - sequenceStep, state.entries.size),
                sequenceIndex = nextSequenceIndex,
            )
            ToneRowPlayMode.RANDOM -> {
                val random = nextRandom(state.randomState)
                state.copy(
                    rowIndex = randomIndex(random, state.entries.size),
                    sequenceIndex = nextSequenceIndex,
                    randomState = random,
                )
            }
            ToneRowPlayMode.PENDULUM -> pendulumMove(state, sequenceStep, nextSequenceIndex)
        }
        return consumePlayOnceEmission(emitCurrent(moved))
    }

    private fun pendulumMove(state: ToneRowState, step: Int, nextSequenceIndex: Int): ToneRowState {
        if (state.entries.size == 1 || step == 0) {
            return state.copy(rowIndex = 0, sequenceIndex = nextSequenceIndex)
        }
        var direction = state.pendulumDirection
        var index = state.rowIndex
        repeat(abs(step)) {
            val movementSign = step.compareTo(0)
            val candidate = index + direction * movementSign
            if (candidate !in state.entries.indices) {
                direction *= -1
                index += direction * movementSign
            } else {
                index = candidate
            }
        }
        return state.copy(
            rowIndex = index,
            sequenceIndex = nextSequenceIndex,
            pendulumDirection = direction,
        )
    }

    private fun setPlayMode(state: ToneRowState, mode: ToneRowPlayMode): ToneRowTransition {
        val direction = when (mode) {
            ToneRowPlayMode.RETRO -> -1
            ToneRowPlayMode.PRIME,
            ToneRowPlayMode.RANDOM,
            ToneRowPlayMode.PENDULUM,
            -> 1
        }
        return ToneRowTransition(state.copy(playMode = mode, pendulumDirection = direction))
    }

    private fun setIntervalSequence(state: ToneRowState, steps: List<Int>): ToneRowTransition {
        return ToneRowTransition(state.copy(intervalSequence = steps.toList(), sequenceIndex = 0))
    }

    private fun addSequenceStep(state: ToneRowState, steps: Int): ToneRowTransition {
        if (state.intervalSequence.size >= MAX_TONE_ROW_SEQUENCE_SIZE) return ToneRowTransition(state)
        return ToneRowTransition(state.copy(intervalSequence = state.intervalSequence + steps))
    }

    private fun selectSequenceStep(state: ToneRowState, index: Int): ToneRowTransition {
        if (index !in state.intervalSequence.indices) return ToneRowTransition(state)
        return ToneRowTransition(state.copy(sequenceIndex = index))
    }

    private fun deleteSequenceStep(state: ToneRowState, index: Int): ToneRowTransition {
        if (index !in state.intervalSequence.indices) return ToneRowTransition(state)
        val nextSequence = if (state.intervalSequence.size == 1) {
            listOf(1)
        } else {
            state.intervalSequence.filterIndexed { itemIndex, _ -> itemIndex != index }
        }
        return ToneRowTransition(
            state.copy(
                intervalSequence = nextSequence,
                sequenceIndex = state.sequenceIndex.coerceAtMost(nextSequence.lastIndex),
            ),
        )
    }

    private fun resetTransformations(state: ToneRowState): ToneRowTransition {
        return ToneRowTransition(
            state.copy(
                playMode = ToneRowPlayMode.PRIME,
                inverted = false,
                transpositionSemitones = 0,
                intervalSequence = listOf(1),
                sequenceIndex = 0,
                translation = 0,
                octaveOffset = 0,
                pendulumDirection = 1,
            ),
        )
    }

    private fun emitCurrent(state: ToneRowState): ToneRowTransition {
        val entry = state.entries[state.rowIndex]
        val pivot = state.entries.first().relativeDegree
        val contourDegree = if (state.inverted) {
            pivot - (entry.relativeDegree - pivot)
        } else {
            entry.relativeDegree
        }
        val translatedDegree = contourDegree + state.translation
        val base = grid.noteFromRelativeDegree(translatedDegree)
        val transformed = base.toLong() +
            state.transpositionSemitones.toLong() +
            state.octaveOffset.toLong() * 12L
        val note = transformed.coerceIn(grid.range.min.toLong(), grid.range.max.toLong()).toInt()
        return ToneRowTransition(state, listOf(ToneRowEvent.PlayNote(note, entry.velocity)))
    }

    private fun consumePlayOnceEmission(transition: ToneRowTransition): ToneRowTransition {
        val state = transition.state
        if (!state.playOnce || transition.events.none { it is ToneRowEvent.PlayNote }) return transition
        val remaining = state.notesRemainingInPass - 1
        return if (remaining > 0) {
            transition.copy(state = state.copy(notesRemainingInPass = remaining))
        } else {
            transition.copy(
                state = state.copy(
                    mode = ToneRowMode.MANUAL_PLAYBACK,
                    playOnce = false,
                    notesRemainingInPass = 0,
                ),
                events = transition.events + ToneRowEvent.FinishedPass,
            )
        }
    }

    private fun randomIndex(random: Long, size: Int): Int {
        val mixed = random xor (random ushr 33)
        return floorMod((mixed ushr 1).toInt(), size)
    }

    private fun nextRandom(value: Long): Long = value * 6364136223846793005L + 1442695040888963407L
}
