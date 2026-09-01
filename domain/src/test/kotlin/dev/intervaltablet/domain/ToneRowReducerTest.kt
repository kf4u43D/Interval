package dev.intervaltablet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneRowReducerTest {
    private val grid = PitchGrid(0, ScaleLibrary.major, MidiNoteRange(36, 95), wrap = true)
    private val reducer = ToneRowReducer(grid)

    @Test
    fun recordingCompletesFiveSevenAndTwelveNoteFixturesWithoutPitchClassDuplicates() {
        val fixtures = listOf(
            ScaleLibrary.majorPentatonic to 5,
            ScaleLibrary.major to 7,
            ScaleLibrary.chromatic to 12,
        )
        for ((scale, expectedSize) in fixtures) {
            val fixtureGrid = PitchGrid(0, scale, MidiNoteRange(36, 95), wrap = true)
            val fixtureReducer = ToneRowReducer(fixtureGrid)
            var state = fixtureReducer.reduce(
                ToneRowState(),
                ToneRowAction.StartRecording(fixtureGrid.home()),
            ).state
            repeat(expectedSize) { index ->
                state = fixtureReducer.reduce(state, ToneRowAction.RecordMove(1, 50 + index)).state
            }

            assertEquals(expectedSize, state.entries.size)
            assertEquals(expectedSize, state.entries.map { it.recordedPitchClass }.distinct().size)
            assertEquals(ToneRowMode.MANUAL_PLAYBACK, state.mode)
            assertEquals(expectedSize, state.recordingCapacity)
            assertEquals(scale.id, state.referenceScaleId)
            assertEquals(0, state.referenceRootPitchClass)
            assertEquals((50 until 50 + expectedSize).toList(), state.entries.map { it.velocity })
        }
    }

    @Test
    fun duplicateTargetSearchesForwardOrBackwardAndZeroDuplicateIsIgnored() {
        var state = reducer.reduce(ToneRowState(), ToneRowAction.StartRecording(60)).state
        state = reducer.reduce(state, ToneRowAction.RecordMove(1, 80)).state
        assertEquals(62, state.entries.last().recordedMidiNote)
        state = reducer.reduce(state, ToneRowAction.RecordMove(-1, 81)).state
        assertEquals(60, state.entries.last().recordedMidiNote)

        val forwardSkip = reducer.reduce(state, ToneRowAction.RecordMove(1, 82))
        assertEquals(64, forwardSkip.state.entries.last().recordedMidiNote)
        assertEquals(3, forwardSkip.state.entries.size)

        val zeroDuplicate = reducer.reduce(forwardSkip.state, ToneRowAction.RecordMove(0, 83))
        assertEquals(forwardSkip.state, zeroDuplicate.state)
        assertTrue(zeroDuplicate.events.isEmpty())

        val backwardSkip = reducer.reduce(forwardSkip.state, ToneRowAction.RecordMove(-1, 84))
        assertEquals(59, backwardSkip.state.entries.last().recordedMidiNote)
        assertEquals(4, backwardSkip.state.entries.size)
    }

    @Test
    fun recordClearsPreviousRowAndCapturesTheNewReferenceGrid() {
        val previous = rowState(5).copy(
            mode = ToneRowMode.MANUAL_PLAYBACK,
            playMode = ToneRowPlayMode.RETRO,
            intervalSequence = listOf(2, -1),
            translation = 3,
        )
        val changedGrid = PitchGrid(5, ScaleLibrary.minorPentatonic, MidiNoteRange(48, 84), wrap = true)
        val transition = ToneRowReducer(changedGrid).reduce(previous, ToneRowAction.StartRecording(70))

        assertEquals(ToneRowMode.RECORDING, transition.state.mode)
        assertTrue(transition.state.entries.isEmpty())
        assertEquals(5, transition.state.recordingCapacity)
        assertEquals(5, transition.state.referenceRootPitchClass)
        assertEquals(ScaleLibrary.minorPentatonic.id, transition.state.referenceScaleId)
        assertEquals(ToneRowPlayMode.RETRO, transition.state.playMode)
        assertEquals(listOf(2, -1), transition.state.intervalSequence)
        assertEquals(3, transition.state.translation)
    }

    @Test
    fun playFinishesRecordingEarlyAndEmptyRecordingReturnsIdle() {
        var state = reducer.reduce(ToneRowState(), ToneRowAction.StartRecording(60)).state
        val empty = reducer.reduce(state, ToneRowAction.Play)
        assertEquals(ToneRowMode.IDLE, empty.state.mode)
        assertTrue(empty.state.entries.isEmpty())
        assertTrue(empty.events.isEmpty())

        state = reducer.reduce(ToneRowState(), ToneRowAction.StartRecording(60)).state
        state = reducer.reduce(state, ToneRowAction.RecordMove(2, 93)).state
        val early = reducer.reduce(state, ToneRowAction.Play)
        assertEquals(ToneRowMode.MANUAL_PLAYBACK, early.state.mode)
        assertEquals(1, early.state.entries.size)
        assertEquals(0, early.state.rowIndex)
        assertTrue(early.events.isEmpty())
    }

    @Test
    fun stopPreservesNonEmptyRecordingAndCurrentPlaybackPosition() {
        val emptyRecording = reducer.reduce(ToneRowState(), ToneRowAction.StartRecording(60)).state
        val cancelled = reducer.reduce(emptyRecording, ToneRowAction.Stop)
        assertEquals(ToneRowMode.IDLE, cancelled.state.mode)
        assertTrue(cancelled.state.entries.isEmpty())
        assertEquals(0, cancelled.state.recordingCapacity)

        var recording = reducer.reduce(ToneRowState(), ToneRowAction.StartRecording(60)).state
        recording = reducer.reduce(recording, ToneRowAction.RecordMove(1, 70)).state
        val stoppedRecording = reducer.reduce(recording, ToneRowAction.Stop)
        assertEquals(ToneRowMode.IDLE, stoppedRecording.state.mode)
        assertEquals(recording.entries, stoppedRecording.state.entries)

        val positioned = rowState(5).copy(mode = ToneRowMode.MANUAL_PLAYBACK, rowIndex = 3)
        val stoppedPlayback = reducer.reduce(positioned, ToneRowAction.Stop)
        assertEquals(ToneRowMode.IDLE, stoppedPlayback.state.mode)
        assertEquals(3, stoppedPlayback.state.rowIndex)
    }

    @Test
    fun manualPlaybackWrapsIndexZeroReplaysAndRestartPlaysFirstImmediately() {
        val initial = rowState(5)
        val play = reducer.reduce(initial, ToneRowAction.Play)
        assertEquals(ToneRowMode.MANUAL_PLAYBACK, play.state.mode)
        assertEquals(0, play.state.rowIndex)
        assertEquals(noteAt(initial, 0), play.playedNote())

        val plusOne = reducer.reduce(play.state, ToneRowAction.ManualMove(1))
        assertEquals(1, plusOne.state.rowIndex)
        assertEquals(noteAt(initial, 1), plusOne.playedNote())

        val wrapped = reducer.reduce(plusOne.state, ToneRowAction.ManualMove(-2))
        assertEquals(4, wrapped.state.rowIndex)
        val replay = reducer.reduce(wrapped.state, ToneRowAction.ManualMove(0))
        assertEquals(4, replay.state.rowIndex)
        assertEquals(wrapped.playedNote(), replay.playedNote())

        val restart = reducer.reduce(replay.state, ToneRowAction.Restart)
        assertEquals(0, restart.state.rowIndex)
        assertEquals(noteAt(initial, 0), restart.playedNote())
    }

    @Test
    fun manualMovesAreIgnoredOutsideManualPlayback() {
        val idle = rowState(5)
        val auto = idle.copy(mode = ToneRowMode.AUTO_PLAYING)
        assertEquals(idle, reducer.reduce(idle, ToneRowAction.ManualMove(1)).state)
        assertEquals(auto, reducer.reduce(auto, ToneRowAction.ManualMove(1)).state)
    }

    @Test
    fun sequenceIsEditableWhilePlayingAndAlwaysKeepsOneBoundedStep() {
        var state = rowState(5).copy(mode = ToneRowMode.AUTO_PLAYING)
        state = reducer.reduce(state, ToneRowAction.AddSequenceStep(-2)).state
        assertEquals(ToneRowMode.AUTO_PLAYING, state.mode)
        assertEquals(listOf(1, -2), state.intervalSequence)

        state = reducer.reduce(state, ToneRowAction.DeleteSequenceStep(0)).state
        assertEquals(listOf(-2), state.intervalSequence)
        state = reducer.reduce(state, ToneRowAction.DeleteSequenceStep(0)).state
        assertEquals(listOf(1), state.intervalSequence)

        repeat(63) { state = reducer.reduce(state, ToneRowAction.AddSequenceStep(1)).state }
        assertEquals(64, state.intervalSequence.size)
        val atLimit = reducer.reduce(state, ToneRowAction.AddSequenceStep(1))
        assertEquals(state, atLimit.state)
        assertEquals(ToneRowMode.AUTO_PLAYING, atLimit.state.mode)
    }

    @Test
    fun sequenceCursorCanBeSelectedWhilePlaying() {
        val playing = rowState(5).copy(
            mode = ToneRowMode.AUTO_PLAYING,
            intervalSequence = listOf(1, -2, 3),
        )
        val selected = reducer.reduce(playing, ToneRowAction.SelectSequenceStep(2))
        assertEquals(2, selected.state.sequenceIndex)
        assertEquals(ToneRowMode.AUTO_PLAYING, selected.state.mode)
        assertTrue(selected.events.isEmpty())
    }

    @Test
    fun invalidSequenceCursorSelectionIsANoOp() {
        val selected = rowState(5).copy(
            mode = ToneRowMode.AUTO_PLAYING,
            intervalSequence = listOf(1, -2, 3),
            sequenceIndex = 2,
        )
        val invalid = reducer.reduce(selected, ToneRowAction.SelectSequenceStep(3))
        assertEquals(selected, invalid.state)
        assertTrue(invalid.events.isEmpty())
    }

    @Test
    fun primeAndRetroTraverseInOppositeOrders() {
        val initial = rowState(5)
        val prime = emittedPass(initial.copy(playMode = ToneRowPlayMode.PRIME), ToneRowAction.StartAuto())
        val retro = emittedPass(initial.copy(playMode = ToneRowPlayMode.RETRO), ToneRowAction.StartAuto())
        assertEquals((0..4).map { noteAt(initial, it) }, prime)
        assertEquals((0..4).reversed().map { noteAt(initial, it) }, retro)
    }

    @Test
    fun randomModeIsSeedDeterministicAndConsumesItsExplicitState() {
        val initial = rowState(7).copy(playMode = ToneRowPlayMode.RANDOM, randomState = 1234L)
        val first = emittedTransitions(initial, 20)
        val second = emittedTransitions(initial, 20)
        assertEquals(first, second)
        assertNotEquals(initial.randomState, first.last().state.randomState)
        assertTrue(first.all { it.playedNote() in grid.range.min..grid.range.max })

        val differentSeed = emittedTransitions(initial.copy(randomState = 1235L), 20)
        assertNotEquals(first.map { it.state.rowIndex }, differentSeed.map { it.state.rowIndex })
    }

    @Test
    fun pendulumDoesNotRepeatEitherEndpoint() {
        val initial = rowState(5).copy(playMode = ToneRowPlayMode.PENDULUM)
        val transitions = emittedTransitions(initial, 10)
        assertEquals(listOf(0, 1, 2, 3, 4, 3, 2, 1, 0, 1), transitions.map { it.state.rowIndex })
        val indices = transitions.map { it.state.rowIndex }
        assertFalse(indices.zipWithNext().any { (left, right) -> left == right && left in listOf(0, 4) })
    }

    @Test
    fun inversionReflectsContourAroundFirstEntryWithoutChangingTraversalDirection() {
        val entries = listOf(2, 4, 7).map { degree ->
            ToneRowEntry(degree, grid.noteFromRelativeDegree(degree), 80)
        }
        val initial = ToneRowState(entries = entries, mode = ToneRowMode.MANUAL_PLAYBACK)
        val inverted = reducer.reduce(initial, ToneRowAction.SetInverted(true)).state
        val second = reducer.reduce(inverted, ToneRowAction.ManualMove(1))

        assertEquals(1, second.state.rowIndex)
        assertEquals(grid.noteFromRelativeDegree(0), second.playedNote())
        assertEquals(listOf(1), second.state.intervalSequence)
    }

    @Test
    fun translationTranspositionAndOctaveComposeInDocumentedOrderAndClampToRange() {
        val initial = rowState(5).copy(mode = ToneRowMode.MANUAL_PLAYBACK)
        var state = reducer.reduce(initial, ToneRowAction.SetTranslation(2)).state
        state = reducer.reduce(state, ToneRowAction.SetTransposition(1)).state
        state = reducer.reduce(state, ToneRowAction.SetOctaveOffset(1)).state
        val played = reducer.reduce(state, ToneRowAction.ManualMove(0))
        val expected = (
            grid.noteFromRelativeDegree(initial.entries.first().relativeDegree + 2) + 13
            ).coerceIn(grid.range.min, grid.range.max)
        assertEquals(expected, played.playedNote())

        state = reducer.reduce(state, ToneRowAction.SetOctaveOffset(99)).state
        assertEquals(10, state.octaveOffset)
        assertEquals(grid.range.max, reducer.reduce(state, ToneRowAction.ManualMove(0)).playedNote())
    }

    @Test
    fun resetRestoresTransformationDefaultsWithoutErasingTheRow() {
        val entries = rowState(5).entries
        val transformed = ToneRowState(
            entries = entries,
            playMode = ToneRowPlayMode.RANDOM,
            inverted = true,
            transpositionSemitones = 7,
            translation = -3,
            octaveOffset = 2,
            intervalSequence = listOf(2, -1),
            sequenceIndex = 1,
            pendulumDirection = -1,
        )
        val reset = reducer.reduce(transformed, ToneRowAction.ResetTransformations).state
        assertEquals(entries, reset.entries)
        assertEquals(ToneRowPlayMode.PRIME, reset.playMode)
        assertFalse(reset.inverted)
        assertEquals(0, reset.transpositionSemitones)
        assertEquals(0, reset.translation)
        assertEquals(0, reset.octaveOffset)
        assertEquals(listOf(1), reset.intervalSequence)
        assertEquals(0, reset.sequenceIndex)
        assertEquals(1, reset.pendulumDirection)
    }

    @Test
    fun relativeDegreesPreserveContourOnAChangedScaleAndKey() {
        val recorded = rowState(5).copy(
            referenceRootPitchClass = 0,
            referenceScaleId = ScaleLibrary.major.id,
            mode = ToneRowMode.MANUAL_PLAYBACK,
        )
        val playbackGrid = PitchGrid(2, ScaleLibrary.naturalMinor, MidiNoteRange(36, 95), wrap = true)
        val playbackReducer = ToneRowReducer(playbackGrid)
        val output = buildList {
            var state = playbackReducer.reduce(recorded, ToneRowAction.Play).also {
                add(it.playedNote())
            }.state
            repeat(recorded.entries.size - 1) {
                val transition = playbackReducer.reduce(state, ToneRowAction.ManualMove(1))
                add(transition.playedNote())
                state = transition.state
            }
        }
        assertEquals(
            recorded.entries.map { playbackGrid.noteFromRelativeDegree(it.relativeDegree) },
            output,
        )
        assertEquals(0, recorded.referenceRootPitchClass)
        assertEquals(ScaleLibrary.major.id, recorded.referenceScaleId)
    }

    @Test
    fun playOnceEmitsExactlyOneRowLengthForEveryModeThenFinishesInManualPlayback() {
        for (mode in ToneRowPlayMode.entries) {
            var transition = reducer.reduce(rowState(7).copy(playMode = mode), ToneRowAction.PlayOnce)
            var notes = transition.events.filterIsInstance<ToneRowEvent.PlayNote>().size
            var finished = transition.events.count { it == ToneRowEvent.FinishedPass }
            while (transition.state.mode == ToneRowMode.AUTO_PLAYING) {
                transition = reducer.reduce(transition.state, ToneRowAction.Tick)
                notes += transition.events.filterIsInstance<ToneRowEvent.PlayNote>().size
                finished += transition.events.count { it == ToneRowEvent.FinishedPass }
            }
            assertEquals("mode=$mode", 7, notes)
            assertEquals("mode=$mode", 1, finished)
            assertEquals(ToneRowMode.MANUAL_PLAYBACK, transition.state.mode)
            assertFalse(transition.state.playOnce)
            assertEquals(0, transition.state.notesRemainingInPass)
        }
    }

    @Test
    fun oneElementPlayOnceFinishesInItsInitialTransition() {
        val transition = reducer.reduce(rowState(1), ToneRowAction.PlayOnce)
        assertEquals(1, transition.events.filterIsInstance<ToneRowEvent.PlayNote>().size)
        assertEquals(1, transition.events.count { it == ToneRowEvent.FinishedPass })
        assertEquals(ToneRowMode.MANUAL_PLAYBACK, transition.state.mode)
    }

    @Test
    fun pauseSuppressesTicksAndResumeKeepsThePosition() {
        val started = reducer.reduce(rowState(5), ToneRowAction.StartAuto())
        val advanced = reducer.reduce(started.state, ToneRowAction.Tick)
        val paused = reducer.reduce(advanced.state, ToneRowAction.PauseToggle)
        assertEquals(ToneRowMode.PAUSED, paused.state.mode)
        assertTrue(reducer.reduce(paused.state, ToneRowAction.Tick).events.isEmpty())

        val resumed = reducer.reduce(paused.state, ToneRowAction.PauseToggle)
        assertEquals(ToneRowMode.AUTO_PLAYING, resumed.state.mode)
        assertEquals(advanced.state.rowIndex, resumed.state.rowIndex)
        assertEquals(advanced.state.rowIndex + 1, reducer.reduce(resumed.state, ToneRowAction.Tick).state.rowIndex)
    }

    @Test
    fun continuousAutoCanRestartOrContinueFromRetainedPosition() {
        val stopped = rowState(5).copy(mode = ToneRowMode.IDLE, rowIndex = 3, sequenceIndex = 0)
        val continued = reducer.reduce(stopped, ToneRowAction.StartAuto(restart = false))
        assertEquals(3, continued.state.rowIndex)
        assertEquals(noteAt(stopped, 3), continued.playedNote())

        val restarted = reducer.reduce(continued.state, ToneRowAction.StartAuto(restart = true))
        assertEquals(0, restarted.state.rowIndex)
        assertEquals(noteAt(stopped, 0), restarted.playedNote())
    }

    @Test
    fun invalidRowsAndSequencesAreRejectedAtTheBoundary() {
        val duplicate = ToneRowEntry(1, 62, 80)
        assertThrows(IllegalArgumentException::class.java) {
            ToneRowState(entries = listOf(duplicate, duplicate.copy(relativeDegree = 8)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToneRowState(intervalSequence = emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToneRowState(intervalSequence = List(65) { 1 })
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToneRowAction.SetIntervalSequence(listOf(15))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToneRowAction.SetIntervalSequence(List(65) { 1 })
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToneRowState(
                entries = rowState(5).entries,
                mode = ToneRowMode.PAUSED,
                playOnce = true,
                notesRemainingInPass = 6,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToneRowState(mode = ToneRowMode.AUTO_PLAYING)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToneRowEntry(0, 128, 64)
        }
    }

    @Test
    fun pausedPlayOnceRestartPreservesOnePassAndRestartsItsBudget() {
        var transition = reducer.reduce(rowState(5), ToneRowAction.PlayOnce)
        transition = reducer.reduce(transition.state, ToneRowAction.Tick)
        transition = reducer.reduce(transition.state, ToneRowAction.PauseToggle)

        transition = reducer.reduce(transition.state, ToneRowAction.Restart)
        assertEquals(ToneRowMode.AUTO_PLAYING, transition.state.mode)
        assertTrue(transition.state.playOnce)
        assertEquals(4, transition.state.notesRemainingInPass)
        var emitted = transition.events.filterIsInstance<ToneRowEvent.PlayNote>().size
        var finished = transition.events.count { it == ToneRowEvent.FinishedPass }
        while (transition.state.mode == ToneRowMode.AUTO_PLAYING) {
            transition = reducer.reduce(transition.state, ToneRowAction.Tick)
            emitted += transition.events.filterIsInstance<ToneRowEvent.PlayNote>().size
            finished += transition.events.count { it == ToneRowEvent.FinishedPass }
        }
        assertEquals(5, emitted)
        assertEquals(1, finished)
    }

    @Test
    fun recordingCapacityUsesOnlyPitchClassesReachableInsideTheRange() {
        val narrowGrid = PitchGrid(
            rootPitchClass = 0,
            scale = ScaleLibrary.major,
            range = MidiNoteRange(60, 63),
            wrap = true,
        )
        val narrowReducer = ToneRowReducer(narrowGrid)
        var transition = narrowReducer.reduce(ToneRowState(), ToneRowAction.StartRecording(60))
        assertEquals(2, transition.state.recordingCapacity)
        transition = narrowReducer.reduce(transition.state, ToneRowAction.RecordMove(0, 80))
        assertEquals(ToneRowMode.RECORDING, transition.state.mode)
        transition = narrowReducer.reduce(transition.state, ToneRowAction.RecordMove(1, 80))
        assertEquals(ToneRowMode.MANUAL_PLAYBACK, transition.state.mode)
        assertEquals(listOf(0, 2), transition.state.entries.map { it.recordedPitchClass })
    }

    private fun rowState(size: Int): ToneRowState {
        require(size in 1..grid.scale.offsets.size)
        val entries = (0 until size).map { degree ->
            ToneRowEntry(
                relativeDegree = degree,
                recordedMidiNote = grid.noteFromRelativeDegree(degree),
                velocity = 70 + degree,
            )
        }
        return ToneRowState(entries = entries)
    }

    private fun emittedPass(initial: ToneRowState, action: ToneRowAction): List<Int> {
        return emittedTransitions(initial, initial.entries.size, action).map { it.playedNote() }
    }

    private fun emittedTransitions(
        initial: ToneRowState,
        count: Int,
        action: ToneRowAction = ToneRowAction.StartAuto(),
    ): List<ToneRowTransition> {
        require(count > 0)
        val transitions = mutableListOf<ToneRowTransition>()
        var transition = reducer.reduce(initial, action)
        transitions += transition
        repeat(count - 1) {
            transition = reducer.reduce(transition.state, ToneRowAction.Tick)
            transitions += transition
        }
        return transitions
    }

    private fun noteAt(state: ToneRowState, index: Int): Int =
        grid.noteFromRelativeDegree(state.entries[index].relativeDegree)

    private fun ToneRowTransition.playedNote(): Int =
        events.filterIsInstance<ToneRowEvent.PlayNote>().single().midiNote
}
