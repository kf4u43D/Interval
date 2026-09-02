package dev.intervaltablet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalReducerTest {
    private val reducer = IntervalReducer()
    private val source = TriggerSource.Touch(7)

    @Test
    fun publicActionsAndConfigurationRejectOutOfRangeValues() {
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentAction.PressInterval(source, MAX_INTERVAL_STEPS + 1, 64)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentAction.PressInterval(source, 0, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentAction.PressAbsolute(source, 128, 64)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentAction.PressAbsolute(source, 60, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentAction.PressPadAbsolute(source, 128, 64)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentAction.PressPadAbsolute(source, 60, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentAction.PressSameInterval(source, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentAction.PressSamePitch(source, 128)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentAction.PressRandomInterval(source, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentAction.HoldChromaticShift(source, 13)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentAction.StrumTone(source, voiceIndex = 0, velocity = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentAction.Release(source, releaseVelocity = 128)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentAction.AnchorExternal(128)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentAction.SetRoot(12)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentAction.SetOutputChannel(16)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentConfig(rootPitchClass = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentConfig(
                rootPitchClass = 0,
                scale = ScaleLibrary.major,
                range = MidiNoteRange(61, 61),
            )
        }
    }

    @Test
    fun zeroRetriggersWithoutGrowingHistory() {
        var state = reducer.initialState()
        state = reducer.reduce(state, InstrumentAction.PressInterval(source, 1, 100)).state
        state = reducer.reduce(state, InstrumentAction.Release(source)).state
        val before = state.previousDistinctNotes
        val transition = reducer.reduce(
            state,
            InstrumentAction.PressInterval(source, 0, 100, timestampNanos = 12L),
        )
        assertEquals(before, transition.state.previousDistinctNotes)
        assertEquals(62, transition.state.currentNote)
        assertEquals(listOf(62), noteOns(transition).map { it.note })
        assertEquals(listOf(12L), noteOns(transition).map { it.timestampNanos })
    }

    @Test
    fun sameIntervalRepeatsScaleStepsWhileSamePitchRepeatsTheAudibleRatio() {
        var intervalState = reducer.initialState()
        intervalState = reducer.reduce(
            intervalState,
            InstrumentAction.PressInterval(source, 3, 100, 1L),
        ).state
        val sameInterval = reducer.reduce(
            intervalState,
            InstrumentAction.PressSameInterval(source, 101, 2L),
        )

        assertEquals(71, sameInterval.state.currentNote)
        assertEquals(3, sameInterval.state.lastIntervalSteps)
        assertEquals(listOf(71), noteOns(sameInterval).map { it.note })

        var pitchState = reducer.initialState()
        pitchState = reducer.reduce(
            pitchState,
            InstrumentAction.PressInterval(source, 1, 90, 3L),
        ).state
        pitchState = reducer.reduce(
            pitchState,
            InstrumentAction.PressInterval(source, 1, 91, 4L),
        ).state
        assertEquals(2, pitchState.lastPitchDeltaSemitones)

        val samePitch = reducer.reduce(
            pitchState,
            InstrumentAction.PressSamePitch(source, 92, 5L),
        )
        assertEquals(66, samePitch.state.currentNote)
        assertEquals(listOf(66), noteOns(samePitch).map { it.note })
        assertEquals(2, samePitch.state.lastPitchDeltaSemitones)

        val released = reducer.reduce(
            samePitch.state,
            InstrumentAction.Release(source, timestampNanos = 6L),
        )
        assertEquals(listOf(66), noteOffs(released).map { it.note })
    }

    @Test
    fun samePitchWithAStableHeldChromaticShiftPreservesTheAudibleRatio() {
        val shiftSource = TriggerSource.Touch(8)
        val noteSource = TriggerSource.Touch(9)
        var state = reducer.initialState()
        state = reducer.reduce(
            state,
            InstrumentAction.HoldChromaticShift(shiftSource, 1, 1L),
        ).state

        val first = reducer.reduce(
            state,
            InstrumentAction.PressInterval(noteSource, 1, 90, 2L),
        )
        assertEquals(listOf(63), noteOns(first).map { it.note })
        state = reducer.reduce(
            first.state,
            InstrumentAction.Release(noteSource, timestampNanos = 3L),
        ).state

        val second = reducer.reduce(
            state,
            InstrumentAction.PressInterval(noteSource, 1, 91, 4L),
        )
        assertEquals(listOf(65), noteOns(second).map { it.note })
        assertEquals(2, second.state.lastPitchDeltaSemitones)
        state = reducer.reduce(
            second.state,
            InstrumentAction.Release(noteSource, timestampNanos = 5L),
        ).state

        val samePitch = reducer.reduce(
            state,
            InstrumentAction.PressSamePitch(noteSource, 92, 6L),
        )
        assertEquals(66, samePitch.state.currentNote)
        assertEquals(listOf(67), noteOns(samePitch).map { it.note })
        assertEquals(67, samePitch.state.lastSoundedLeadNote)
        assertEquals(2, samePitch.state.lastPitchDeltaSemitones)

        val released = reducer.reduce(
            samePitch.state,
            InstrumentAction.Release(noteSource, timestampNanos = 7L),
        )
        assertEquals(listOf(67), noteOffs(released).map { it.note })
        assertEquals(1, released.state.activeChromaticShiftSemitones)
    }

    @Test
    fun samePitchComposesAChromaticShiftAddedAfterTheRecordedRatio() {
        val noteSource = TriggerSource.Touch(10)
        val shiftSource = TriggerSource.Touch(11)
        var state = reducer.initialState()

        val first = reducer.reduce(
            state,
            InstrumentAction.PressInterval(noteSource, 1, 90, 8L),
        )
        assertEquals(listOf(62), noteOns(first).map { it.note })
        state = reducer.reduce(
            first.state,
            InstrumentAction.Release(noteSource, timestampNanos = 9L),
        ).state
        val second = reducer.reduce(
            state,
            InstrumentAction.PressInterval(noteSource, 1, 91, 10L),
        )
        assertEquals(listOf(64), noteOns(second).map { it.note })
        assertEquals(2, second.state.lastPitchDeltaSemitones)
        state = reducer.reduce(
            second.state,
            InstrumentAction.Release(noteSource, timestampNanos = 11L),
        ).state
        state = reducer.reduce(
            state,
            InstrumentAction.HoldChromaticShift(shiftSource, 1, 12L),
        ).state

        val samePitch = reducer.reduce(
            state,
            InstrumentAction.PressSamePitch(noteSource, 92, 13L),
        )
        assertEquals(66, samePitch.state.currentNote)
        assertEquals(listOf(67), noteOns(samePitch).map { it.note })
    }

    @Test
    fun samePitchUsesTheBaseTargetAfterTheRecordedShiftIsReleased() {
        val shiftSource = TriggerSource.Touch(12)
        val noteSource = TriggerSource.Touch(13)
        var state = reducer.initialState()
        state = reducer.reduce(
            state,
            InstrumentAction.HoldChromaticShift(shiftSource, 1, 14L),
        ).state

        val first = reducer.reduce(
            state,
            InstrumentAction.PressInterval(noteSource, 1, 90, 15L),
        )
        assertEquals(listOf(63), noteOns(first).map { it.note })
        state = reducer.reduce(
            first.state,
            InstrumentAction.Release(noteSource, timestampNanos = 16L),
        ).state
        val second = reducer.reduce(
            state,
            InstrumentAction.PressInterval(noteSource, 1, 91, 17L),
        )
        assertEquals(listOf(65), noteOns(second).map { it.note })
        assertEquals(2, second.state.lastPitchDeltaSemitones)
        state = reducer.reduce(
            second.state,
            InstrumentAction.Release(noteSource, timestampNanos = 18L),
        ).state
        state = reducer.reduce(
            state,
            InstrumentAction.Release(shiftSource, timestampNanos = 19L),
        ).state
        assertEquals(0, state.activeChromaticShiftSemitones)

        val samePitch = reducer.reduce(
            state,
            InstrumentAction.PressSamePitch(noteSource, 92, 20L),
        )
        assertEquals(66, samePitch.state.currentNote)
        assertEquals(listOf(66), noteOns(samePitch).map { it.note })
    }

    @Test
    fun randomIntervalIsImmediateDeterministicBoundedAndBecomesTheSameInterval() {
        val config = InstrumentConfig(
            scale = ScaleLibrary.chromatic,
            range = MidiNoteRange(0, 127),
            solfegeWrap = false,
        )
        val initial = reducer.initialState(config).copy(randomState = 1234L)
        val action = InstrumentAction.PressRandomInterval(source, 88, 10L)
        val first = reducer.reduce(initial, action)
        val replay = reducer.reduce(initial, action)

        assertEquals(first.state, replay.state)
        assertEquals(first.events, replay.events)
        assertTrue(first.state.randomState != initial.randomState)
        assertTrue(first.state.lastIntervalSteps in MIN_INTERVAL_STEPS..MAX_INTERVAL_STEPS)
        assertEquals(1, noteOns(first).size)

        fun randomSeries(seed: Long): List<Int> {
            var state = reducer.initialState(config).copy(randomState = seed)
            return List(12) { index ->
                val transition = reducer.reduce(
                    state,
                    InstrumentAction.PressRandomInterval(
                        source,
                        velocity = 88,
                        timestampNanos = 20L + index,
                    ),
                )
                state = transition.state
                transition.state.lastIntervalSteps
            }
        }

        val seededSeries = randomSeries(1234L)
        assertEquals(seededSeries, randomSeries(1234L))
        assertTrue(seededSeries != randomSeries(1235L))
        assertTrue(seededSeries.all { it in MIN_INTERVAL_STEPS..MAX_INTERVAL_STEPS })

        val expected = first.state.config.grid().move(
            first.state.currentNote,
            first.state.lastIntervalSteps,
        )
        val same = reducer.reduce(
            first.state,
            InstrumentAction.PressSameInterval(source, 89, 11L),
        )
        assertEquals(expected, same.state.currentNote)
        assertEquals(1, noteOns(same).size)
    }

    @Test
    fun chromaticShiftsAreSilentStackPerSourceAndOnlyAffectNewNotes() {
        val firstShift = TriggerSource.Touch(101)
        val secondShift = TriggerSource.Touch(102)
        val firstNote = TriggerSource.Touch(103)
        val secondNote = TriggerSource.Touch(104)
        var state = reducer.initialState()

        val heldFirst = reducer.reduce(
            state,
            InstrumentAction.HoldChromaticShift(firstShift, 1, 1L),
        )
        assertTrue(heldFirst.events.isEmpty())
        state = reducer.reduce(
            heldFirst.state,
            InstrumentAction.HoldChromaticShift(secondShift, 2, 2L),
        ).state
        assertEquals(3, state.activeChromaticShiftSemitones)

        val shifted = reducer.reduce(
            state,
            InstrumentAction.PressInterval(firstNote, 0, 100, 3L),
        )
        assertEquals(60, shifted.state.currentNote)
        assertEquals(listOf(63), noteOns(shifted).map { it.note })

        val releasedFirstShift = reducer.reduce(
            shifted.state,
            InstrumentAction.Release(firstShift, timestampNanos = 4L),
        )
        assertTrue(releasedFirstShift.events.isEmpty())
        assertEquals(2, releasedFirstShift.state.activeChromaticShiftSemitones)
        assertEquals(listOf(63), releasedFirstShift.state.activeBySource.getValue(firstNote).map { it.note })

        val shiftedAgain = reducer.reduce(
            releasedFirstShift.state,
            InstrumentAction.PressInterval(secondNote, 0, 90, 5L),
        )
        assertEquals(listOf(62), noteOns(shiftedAgain).map { it.note })
        val releasedOriginal = reducer.reduce(
            shiftedAgain.state,
            InstrumentAction.Release(firstNote, timestampNanos = 6L),
        )
        assertEquals(listOf(63), noteOffs(releasedOriginal).map { it.note })

        state = reducer.reduce(
            releasedOriginal.state,
            InstrumentAction.Release(secondShift, timestampNanos = 7L),
        ).state
        val unshifted = reducer.reduce(
            state,
            InstrumentAction.PressInterval(TriggerSource.Touch(105), 0, 80, 8L),
        )
        assertEquals(listOf(60), noteOns(unshifted).map { it.note })
    }

    @Test
    fun panicClearsMomentaryShiftsAndReleasesShiftedVoices() {
        val shift = TriggerSource.Touch(111)
        val note = TriggerSource.Touch(112)
        var state = reducer.initialState()
        state = reducer.reduce(state, InstrumentAction.HoldChromaticShift(shift, 1)).state
        state = reducer.reduce(state, InstrumentAction.PressInterval(note, 0, 80)).state

        val panic = reducer.reduce(state, InstrumentAction.Panic(99L))

        assertTrue(panic.state.chromaticShiftBySource.isEmpty())
        assertEquals(0, panic.state.activeInstanceCount)
        assertEquals(listOf(61), noteOffs(panic).map { it.note })
    }

    @Test
    fun undoWalksEveryDistinctPositionAndIgnoresZero() {
        var state = reducer.initialState()
        val positions = mutableListOf(state.currentNote)
        for (steps in listOf(1, 2, -1, 0, 3)) {
            state = reducer.reduce(state, InstrumentAction.PressInterval(source, steps, 90)).state
            if (state.currentNote != positions.last()) positions += state.currentNote
        }
        assertEquals(listOf(60, 62, 65, 64, 69), positions)

        for (expected in positions.dropLast(1).asReversed()) {
            state = reducer.reduce(state, InstrumentAction.Undo(source, 90)).state
            assertEquals(expected, state.currentNote)
        }
        assertTrue(state.previousDistinctNotes.isEmpty())

        val retrigger = reducer.reduce(state, InstrumentAction.Undo(source, 90))
        assertEquals(positions.first(), retrigger.state.currentNote)
        assertEquals(listOf(positions.first()), noteOns(retrigger).map { it.note })
    }

    @Test
    fun historyIsNotSilentlyTruncated() {
        var state = reducer.initialState(
            InstrumentConfig(
                scale = ScaleLibrary.chromatic,
                range = MidiNoteRange(0, 127),
                solfegeWrap = true,
            ),
        )
        repeat(140) {
            state = reducer.reduce(state, InstrumentAction.PressInterval(source, 1, 64)).state
        }
        assertEquals(140, state.previousDistinctNotes.size)
    }

    @Test
    fun undoThenMoveProducesOnlyTheFinalPlayableTransition() {
        var state = reducer.initialState()
        state = reducer.reduce(state, InstrumentAction.PressInterval(source, 1, 80)).state
        state = reducer.reduce(state, InstrumentAction.PressInterval(source, 1, 80)).state
        assertEquals(64, state.currentNote)
        assertEquals(listOf(60, 62), state.previousDistinctNotes)

        val transition = reducer.reduce(
            state,
            InstrumentAction.UndoThenMove(source, 1, 99, timestampNanos = 55L),
        )
        assertEquals(64, transition.state.currentNote)
        assertEquals(listOf(60, 62), transition.state.previousDistinctNotes)
        assertEquals(listOf(64), noteOffs(transition).map { it.note })
        assertEquals(listOf(64), noteOns(transition).map { it.note })
        assertFalse(noteOns(transition).any { it.note == 62 })
        assertEquals(listOf(55L, 55L), midiMessages(transition).map { it.timestampNanos })
    }

    @Test
    fun sameIntervalRepeatsTheLastUndoThenMoveDisplacement() {
        val moved = reducer.reduce(
            reducer.initialState(),
            InstrumentAction.UndoThenMove(source, -3, 90, timestampNanos = 56L),
        )
        assertEquals(55, moved.state.currentNote)
        assertEquals(-3, moved.state.lastIntervalSteps)
        val released = reducer.reduce(
            moved.state,
            InstrumentAction.Release(source, timestampNanos = 57L),
        )

        val same = reducer.reduce(
            released.state,
            InstrumentAction.PressSameInterval(source, 91, timestampNanos = 58L),
        )
        assertEquals(50, same.state.currentNote)
        assertEquals(listOf(50), noteOns(same).map { it.note })
        assertEquals(-3, same.state.lastIntervalSteps)
    }

    @Test
    fun silentHomeReleasesItsSourceWithoutPlayingAndSoundingHomePlays() {
        var state = reducer.initialState()
        state = reducer.reduce(state, InstrumentAction.PressInterval(source, 2, 80)).state

        val silent = reducer.reduce(
            state,
            InstrumentAction.Home(source, sound = false, velocity = 80, timestampNanos = 20L),
        )
        assertEquals(60, silent.state.currentNote)
        assertEquals(listOf(64), noteOffs(silent).map { it.note })
        assertTrue(noteOns(silent).isEmpty())

        val sounding = reducer.reduce(
            silent.state,
            InstrumentAction.Home(source, sound = true, velocity = 81, timestampNanos = 21L),
        )
        assertEquals(listOf(60), noteOns(sounding).map { it.note })
        assertEquals(81, noteOns(sounding).single().velocity)
    }

    @Test
    fun repressingOneSourceReleasesAllItsInstancesBeforeNewNoteOns() {
        var state = reducer.initialState(
            InstrumentConfig(chord = ChordLibrary.triad, padArticulation = PadArticulation.STACKED),
        )
        state = reducer.reduce(state, InstrumentAction.PressInterval(source, 0, 100, 10L)).state

        val transition = reducer.reduce(
            state,
            InstrumentAction.PressInterval(source, 1, 90, timestampNanos = 20L),
        )
        val messages = midiMessages(transition)
        assertEquals(6, messages.size)
        assertTrue(messages.take(3).all { it is MidiMessage.NoteOff })
        assertTrue(messages.drop(3).all { it is MidiMessage.NoteOn })
        assertTrue(messages.all { it.timestampNanos == 20L })
        assertEquals(3, transition.state.activeInstanceCount)
    }

    @Test
    fun absoluteToneRowPitchUsesChordVoicingAndReplacesTheSameOwnedSource() {
        val toneRowSource = TriggerSource.System("tone-row")
        var state = reducer.initialState(
            InstrumentConfig(
                range = MidiNoteRange(48, 72),
                chord = ChordLibrary.triad,
            ),
        )
        val first = reducer.reduce(
            state,
            InstrumentAction.PressAbsolute(toneRowSource, 67, 100, timestampNanos = 40L),
        )
        assertEquals(listOf(67, 64, 60), noteOns(first).map { it.note })
        assertEquals(listOf(100, 50, 50), noteOns(first).map { it.velocity })
        assertEquals(3, first.state.activeInstanceCount)

        state = first.state
        val replacement = reducer.reduce(
            state,
            InstrumentAction.PressAbsolute(toneRowSource, 127, 90, timestampNanos = 41L),
        )
        assertEquals(listOf(67, 64, 60), noteOffs(replacement).map { it.note })
        assertEquals(listOf(72, 69, 65), noteOns(replacement).map { it.note })
        assertTrue(midiMessages(replacement).take(3).all { it is MidiMessage.NoteOff })
        assertTrue(midiMessages(replacement).drop(3).all { it is MidiMessage.NoteOn })
        assertTrue(midiMessages(replacement).all { it.timestampNanos == 41L })
        assertEquals(3, replacement.state.activeInstanceCount)
    }

    @Test
    fun separateSourcesRemainPolyphonicAndReleaseIndependently() {
        val first = TriggerSource.Touch(1)
        val second = TriggerSource.Touch(2)
        var state = reducer.initialState(
            InstrumentConfig(chord = ChordLibrary.third, padArticulation = PadArticulation.STACKED),
        )
        state = reducer.reduce(state, InstrumentAction.PressInterval(first, 0, 100)).state
        state = reducer.reduce(state, InstrumentAction.PressInterval(second, 1, 80)).state
        assertEquals(6, state.activeInstanceCount)

        val releaseFirst = reducer.reduce(state, InstrumentAction.Release(first, timestampNanos = 30L))
        assertEquals(3, noteOffs(releaseFirst).size)
        assertEquals(3, releaseFirst.state.activeInstanceCount)
        assertTrue(second in releaseFirst.state.activeBySource)
        assertFalse(first in releaseFirst.state.activeBySource)
    }

    @Test
    fun releasingReconfigurationsUseTheirActionTimestampAndClearVoices() {
        val timestamp = 9_876L
        val actions = listOf<(Long) -> InstrumentAction>(
            { InstrumentAction.SetRoot(1, it) },
            { InstrumentAction.SetRange(MidiNoteRange(48, 72), it) },
            { InstrumentAction.SetWrap(false, it) },
            { InstrumentAction.SetOutputChannel(4, it) },
        )

        for (createAction in actions) {
            var state = reducer.initialState(
                InstrumentConfig(chord = ChordLibrary.triad, padArticulation = PadArticulation.STACKED),
            )
            state = reducer.reduce(state, InstrumentAction.PressInterval(source, 0, 80, 1L)).state
            val transition = reducer.reduce(state, createAction(timestamp))
            assertEquals(0, transition.state.activeInstanceCount)
            assertEquals(3, noteOffs(transition).size)
            assertTrue(noteOffs(transition).all { it.timestampNanos == timestamp })
            assertTrue(transition.state.config.range.contains(transition.state.currentNote))
        }
    }

    @Test
    fun chordChangeImmediatelyRevoicesHeldPadAndKeepsReplacementReleasable() {
        var state = reducer.initialState(
            InstrumentConfig(chord = ChordLibrary.third, padArticulation = PadArticulation.STACKED),
        )
        state = reducer.reduce(state, InstrumentAction.PressInterval(source, 0, 100)).state
        val original = state.activeBySource.getValue(source)

        val changed = reducer.reduce(
            state,
            InstrumentAction.SetChord(ChordLibrary.off, timestampNanos = 70L),
        )
        assertEquals(original.map { it.note }, noteOffs(changed).map { it.note })
        assertEquals(listOf(60), noteOns(changed).map { it.note })
        assertTrue(midiMessages(changed).all { it.timestampNanos == 70L })
        assertEquals(listOf(60), changed.state.activeBySource.getValue(source).map { it.note })

        val released = reducer.reduce(changed.state, InstrumentAction.Release(source, timestampNanos = 71L))
        assertEquals(listOf(60), noteOffs(released).map { it.note })
        assertTrue(released.state.heldPadBySource.isEmpty())
    }

    @Test
    fun externalAnchorIsClampedForDisplayAndConsumedByFirstDirectionalMove() {
        val config = InstrumentConfig(range = MidiNoteRange(60, 71))
        var state = reducer.initialState(config)
        state = reducer.reduce(state, InstrumentAction.AnchorExternal(100)).state
        assertEquals(71, state.currentNote)
        assertEquals(100, state.lastExternalNote)

        state = reducer.reduce(state, InstrumentAction.PressInterval(source, -1, 64)).state
        assertEquals(71, state.currentNote)
        assertEquals(null, state.lastExternalNote)

        state = reducer.reduce(state, InstrumentAction.AnchorExternal(61)).state
        assertEquals(61, state.currentNote)
        state = reducer.reduce(state, InstrumentAction.PressInterval(source, 1, 64)).state
        assertEquals(62, state.currentNote)
        assertEquals(null, state.lastExternalNote)

        state = reducer.reduce(state, InstrumentAction.AnchorExternal(61)).state
        state = reducer.reduce(state, InstrumentAction.PressInterval(source, -1, 64)).state
        assertEquals(60, state.currentNote)
    }

    @Test
    fun externalAnchorBecomesTheAudibleReferenceForTheNextSamePitchDelta() {
        var state = reducer.initialState()
        state = reducer.reduce(
            state,
            InstrumentAction.PressInterval(source, 2, 90, 1L),
        ).state
        state = reducer.reduce(
            state,
            InstrumentAction.Release(source, timestampNanos = 2L),
        ).state
        assertEquals(64, state.lastSoundedLeadNote)

        state = reducer.reduce(state, InstrumentAction.AnchorExternal(67)).state
        assertEquals(67, state.lastExternalNote)
        assertEquals(67, state.lastSoundedLeadNote)

        val moved = reducer.reduce(
            state,
            InstrumentAction.PressInterval(source, 1, 91, 3L),
        )
        assertEquals(69, moved.state.currentNote)
        assertEquals(2, moved.state.lastPitchDeltaSemitones)
        state = reducer.reduce(
            moved.state,
            InstrumentAction.Release(source, timestampNanos = 4L),
        ).state

        val samePitch = reducer.reduce(
            state,
            InstrumentAction.PressSamePitch(source, 92, 5L),
        )
        assertEquals(71, samePitch.state.currentNote)
        assertEquals(listOf(71), noteOns(samePitch).map { it.note })
        assertEquals(2, samePitch.state.lastPitchDeltaSemitones)
    }

    @Test
    fun zeroReplaysClampedCurrentNoteWithoutConsumingRawExternalAnchor() {
        val config = InstrumentConfig(range = MidiNoteRange(60, 71))
        var state = reducer.initialState(config)
        state = reducer.reduce(state, InstrumentAction.AnchorExternal(100)).state

        val zero = reducer.reduce(state, InstrumentAction.PressInterval(source, 0, 64))
        assertEquals(71, zero.state.currentNote)
        assertEquals(100, zero.state.lastExternalNote)
        assertEquals(listOf(71), noteOns(zero).map { it.note })

        val directional = reducer.reduce(
            zero.state,
            InstrumentAction.PressInterval(source, -1, 64),
        )
        assertEquals(71, directional.state.currentNote)
        assertEquals(null, directional.state.lastExternalNote)
    }

    @Test
    fun panicClearsAllSourcesThenSendsControllersOnEveryConcernedChannel() {
        val config = InstrumentConfig(outputChannel = 0)
        val initial = reducer.initialState(config)
        val state = initial.copy(
            activeBySource = linkedMapOf(
                TriggerSource.Touch(1) to listOf(ActiveNoteInstance(60, 80, 5)),
                TriggerSource.Touch(2) to listOf(ActiveNoteInstance(64, 70, 2)),
            ),
        )

        val transition = reducer.reduce(state, InstrumentAction.Panic(timestampNanos = 123L))
        assertEquals(0, transition.state.activeInstanceCount)
        val messages = midiMessages(transition)
        assertTrue(messages.take(2).all { it is MidiMessage.NoteOff })
        assertEquals(listOf(60, 64), messages.take(2).map { (it as MidiMessage.NoteOff).note })
        val controllers = messages.drop(2).map { it as MidiMessage.ControlChange }
        assertEquals(
            listOf(0 to 123, 0 to 120, 2 to 123, 2 to 120, 5 to 123, 5 to 120),
            controllers.map { it.channel to it.controller },
        )
        assertTrue(messages.all { it.timestampNanos == 123L })
        assertEquals(OutputEvent.Audio(AudioCommand.Panic), transition.events.last())
    }

    @Test
    fun longAbsolutePlaybackKeepsUndoHistoryAndActiveInstancesBounded() {
        val automaticSource = TriggerSource.System("tone-row:test")
        var state = reducer.initialState()
        val history = state.previousDistinctNotes

        repeat(10_000) { index ->
            state = reducer.reduce(
                state,
                InstrumentAction.PressAbsolute(
                    source = automaticSource,
                    note = 48 + index % 24,
                    velocity = 90,
                    timestampNanos = index.toLong(),
                ),
            ).state
        }

        assertEquals(history, state.previousDistinctNotes)
        assertEquals(1, state.activeInstanceCount)
        assertEquals(1, state.activeBySource.size)
    }

    private fun midiMessages(transition: InstrumentTransition): List<MidiMessage> =
        transition.events.filterIsInstance<OutputEvent.MidiOut>().map { it.message }

    private fun noteOns(transition: InstrumentTransition): List<MidiMessage.NoteOn> =
        midiMessages(transition).filterIsInstance<MidiMessage.NoteOn>()

    private fun noteOffs(transition: InstrumentTransition): List<MidiMessage.NoteOff> =
        midiMessages(transition).filterIsInstance<MidiMessage.NoteOff>()
}
