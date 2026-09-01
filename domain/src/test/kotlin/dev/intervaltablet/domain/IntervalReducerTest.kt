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
            { InstrumentAction.SetScale(ScaleLibrary.naturalMinor, it) },
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
    fun chordChangeKeepsExistingInstancesReleasable() {
        var state = reducer.initialState(
            InstrumentConfig(chord = ChordLibrary.third, padArticulation = PadArticulation.STACKED),
        )
        state = reducer.reduce(state, InstrumentAction.PressInterval(source, 0, 100)).state
        val original = state.activeBySource.getValue(source)

        val changed = reducer.reduce(
            state,
            InstrumentAction.SetChord(ChordLibrary.off, timestampNanos = 70L),
        )
        assertEquals(original, changed.state.activeBySource.getValue(source))
        assertTrue(changed.events.isEmpty())

        val released = reducer.reduce(changed.state, InstrumentAction.Release(source, timestampNanos = 71L))
        assertEquals(original.map { it.note }, noteOffs(released).map { it.note })
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
