package dev.intervaltablet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PadArticulationTest {
    private val reducer = IntervalReducer()

    @Test
    fun defaultArticulationPlaysOnlyTheLeadAndExposesTheFullVoicing() {
        val source = TriggerSource.Touch(1)
        val initial = reducer.initialState(InstrumentConfig(chord = ChordLibrary.triad))

        assertEquals(PadArticulation.ARPEGGIATED, initial.config.padArticulation)
        assertEquals(listOf(60, 57, 53), initial.strumNotes())

        val transition = reducer.reduce(
            initial,
            InstrumentAction.PressInterval(source, steps = 0, velocity = 101, timestampNanos = 12L),
        )

        assertEquals(listOf(60), transition.noteOns().map { it.note })
        assertEquals(listOf(101), transition.noteOns().map { it.velocity })
        assertEquals(listOf(60), transition.state.activeBySource.getValue(source).map { it.note })
        assertEquals(listOf(60, 57, 53), transition.state.strumNotes())
        assertTrue(transition.state.hasStandaloneArpeggio(source))
    }

    @Test
    fun heldArpeggioCyclesEveryChordVoiceAndReleaseClearsItsSession() {
        val source = TriggerSource.Touch(101)
        var state = reducer.initialState(InstrumentConfig(chord = ChordLibrary.triad))

        val pressed = reducer.reduce(
            state,
            InstrumentAction.PressInterval(source, 0, 101, timestampNanos = 100L),
        )
        assertEquals(listOf(60), pressed.noteOns().map { it.note })
        assertEquals(1, pressed.state.heldPadBySource.getValue(source).nextArpeggioVoiceIndex)

        val second = reducer.reduce(
            pressed.state,
            InstrumentAction.AdvanceArpeggio(source, timestampNanos = 200L),
        )
        assertEquals(listOf(60), second.noteOffs().map { it.note })
        assertEquals(listOf(57), second.noteOns().map { it.note })
        assertEquals(listOf(50), second.noteOns().map { it.velocity })

        val third = reducer.reduce(
            second.state,
            InstrumentAction.AdvanceArpeggio(source, timestampNanos = 300L),
        )
        assertEquals(listOf(57), third.noteOffs().map { it.note })
        assertEquals(listOf(53), third.noteOns().map { it.note })

        val wrapped = reducer.reduce(
            third.state,
            InstrumentAction.AdvanceArpeggio(source, timestampNanos = 400L),
        )
        assertEquals(listOf(53), wrapped.noteOffs().map { it.note })
        assertEquals(listOf(60), wrapped.noteOns().map { it.note })
        assertEquals(1, wrapped.state.activeInstanceCount)

        val released = reducer.reduce(
            wrapped.state,
            InstrumentAction.Release(source, timestampNanos = 450L),
        )
        assertEquals(listOf(60), released.noteOffs().map { it.note })
        assertTrue(released.state.activeBySource.isEmpty())
        assertTrue(released.state.heldPadBySource.isEmpty())

        val staleTick = reducer.reduce(
            released.state,
            InstrumentAction.AdvanceArpeggio(source, timestampNanos = 500L),
        )
        assertSame(released.state, staleTick.state)
        assertTrue(staleTick.events.isEmpty())
    }

    @Test
    fun chordChangeImmediatelyRevoicesHeldArpeggioAndRestartsItsVoiceOrder() {
        val source = TriggerSource.Touch(102)
        var state = reducer.initialState(InstrumentConfig(chord = ChordLibrary.triad))
        state = reducer.reduce(
            state,
            InstrumentAction.PressInterval(source, 0, 100, timestampNanos = 10L),
        ).state
        state = reducer.reduce(
            state,
            InstrumentAction.AdvanceArpeggio(source, timestampNanos = 20L),
        ).state
        assertEquals(listOf(57), state.activeBySource.getValue(source).map { it.note })

        val changed = reducer.reduce(
            state,
            InstrumentAction.SetChord(ChordLibrary.sixth, timestampNanos = 30L),
        )
        assertEquals(listOf(57), changed.noteOffs().map { it.note })
        assertEquals(listOf(60), changed.noteOns().map { it.note })
        assertEquals(1, changed.state.heldPadBySource.getValue(source).nextArpeggioVoiceIndex)
        assertEquals(ChordLibrary.sixth, changed.state.config.chord)

        val advanced = reducer.reduce(
            changed.state,
            InstrumentAction.AdvanceArpeggio(source, timestampNanos = 40L),
        )
        assertEquals(changed.state.strumNotes()[1], advanced.noteOns().single().note)
        assertTrue(advanced.midiMessages().all { it.timestampNanos == 40L })
    }

    @Test
    fun everyChordAndPadArticulationPreservesRangeOrderAndReleaseOwnership() {
        for (chord in ChordLibrary.all) {
            for (articulation in PadArticulation.entries) {
                for (steps in -4..4) {
                    val source = TriggerSource.Touch(steps.toLong())
                    val initial = reducer.initialState(
                        InstrumentConfig(
                            range = MidiNoteRange(48, 72),
                            chord = chord,
                            padArticulation = articulation,
                        ),
                    )
                    val pressed = reducer.reduce(
                        initial,
                        InstrumentAction.PressInterval(source, steps, velocity = 127, timestampNanos = 20L),
                    )
                    val fullVoicing = pressed.state.strumNotes()
                    val expectedNotes = when (articulation) {
                        PadArticulation.ARPEGGIATED -> fullVoicing.take(1)
                        PadArticulation.STACKED -> fullVoicing
                        PadArticulation.MUTED -> emptyList()
                    }

                    assertTrue("${chord.id}/$articulation/$steps", fullVoicing.all { it in 48..72 })
                    assertTrue("${chord.id}/$articulation/$steps", fullVoicing.size in 1..3)
                    assertEquals("${chord.id}/$articulation/$steps", expectedNotes, pressed.noteOns().map { it.note })
                    assertEquals(expectedNotes.size, pressed.state.activeInstanceCount)
                    assertEquals(expectedNotes.isNotEmpty(), source in pressed.state.activeBySource)

                    val released = reducer.reduce(
                        pressed.state,
                        InstrumentAction.Release(source, timestampNanos = 21L),
                    )
                    assertEquals(expectedNotes, released.noteOffs().map { it.note })
                    assertEquals(0, released.state.activeInstanceCount)
                }
            }
        }
    }

    @Test
    fun mutedPadConsumesAnchorAndUpdatesNavigationWithoutCreatingAnEmptyOwner() {
        val source = TriggerSource.Touch(2)
        var state = reducer.initialState(
            InstrumentConfig(chord = ChordLibrary.third, padArticulation = PadArticulation.MUTED),
        )
        state = reducer.reduce(state, InstrumentAction.AnchorExternal(61)).state

        val transition = reducer.reduce(
            state,
            InstrumentAction.PressInterval(source, steps = 1, velocity = 90, timestampNanos = 30L),
        )

        assertEquals(62, transition.state.currentNote)
        assertEquals(listOf(61), transition.state.previousDistinctNotes)
        assertEquals(null, transition.state.lastExternalNote)
        assertEquals(listOf(62, 59, 62), transition.state.strumNotes())
        assertTrue(transition.noteOns().isEmpty())
        assertTrue(transition.audioNoteOns().isEmpty())
        assertTrue(transition.state.activeBySource.isEmpty())
        assertFalse(source in transition.state.activeBySource)
    }

    @Test
    fun articulationChangeAffectsOnlyFuturePadGesturesAndOldVoicesRemainReleasable() {
        val source = TriggerSource.Touch(3)
        var state = reducer.initialState(
            InstrumentConfig(chord = ChordLibrary.triad, padArticulation = PadArticulation.STACKED),
        )
        state = reducer.reduce(
            state,
            InstrumentAction.PressInterval(source, 0, velocity = 100, timestampNanos = 40L),
        ).state
        val held = state.activeBySource.getValue(source)

        val changed = reducer.reduce(
            state,
            InstrumentAction.SetPadArticulation(PadArticulation.MUTED, timestampNanos = 41L),
        )
        assertTrue(changed.events.isEmpty())
        assertEquals(held, changed.state.activeBySource.getValue(source))

        val mutedPress = reducer.reduce(
            changed.state,
            InstrumentAction.PressInterval(source, 1, velocity = 80, timestampNanos = 42L),
        )
        assertEquals(held.map { it.note }, mutedPress.noteOffs().map { it.note })
        assertTrue(mutedPress.noteOns().isEmpty())
        assertTrue(mutedPress.state.activeBySource.isEmpty())
        assertTrue(mutedPress.midiMessages().all { it.timestampNanos == 42L })
    }

    @Test
    fun padAbsoluteUsesArticulationWhileAutomaticAbsoluteAlwaysRemainsStacked() {
        val manualSource = TriggerSource.Touch(4)
        val autoSource = TriggerSource.System("auto")
        for (articulation in PadArticulation.entries) {
            val initial = reducer.initialState(
                InstrumentConfig(
                    range = MidiNoteRange(48, 72),
                    chord = ChordLibrary.triad,
                    padArticulation = articulation,
                ),
            )
            val manual = reducer.reduce(
                initial,
                InstrumentAction.PressPadAbsolute(manualSource, 67, 110, timestampNanos = 50L),
            )
            val expectedManual = when (articulation) {
                PadArticulation.ARPEGGIATED -> listOf(67)
                PadArticulation.STACKED -> listOf(67, 64, 60)
                PadArticulation.MUTED -> emptyList()
            }
            assertEquals(expectedManual, manual.noteOns().map { it.note })
            assertEquals(67, manual.state.currentNote)
            assertTrue(manual.state.previousDistinctNotes.isEmpty())

            val automatic = reducer.reduce(
                initial,
                InstrumentAction.PressAbsolute(autoSource, 67, 110, timestampNanos = 51L),
            )
            assertEquals(listOf(67, 64, 60), automatic.noteOns().map { it.note })
            assertEquals(listOf(110, 55, 55), automatic.noteOns().map { it.velocity })
        }
    }

    @Test
    fun mutedArticulationDoesNotChangeChromaticUndoOrSoundingHome() {
        val config = InstrumentConfig(chord = ChordLibrary.triad, padArticulation = PadArticulation.MUTED)

        val chromatic = reducer.reduce(
            reducer.initialState(config),
            InstrumentAction.PressChromatic(TriggerSource.Touch(40), 1, 100, timestampNanos = 52L),
        )
        assertEquals(listOf(61, 59, 55), chromatic.noteOns().map { it.note })

        var undoState = reducer.initialState(config)
        undoState = reducer.reduce(
            undoState,
            InstrumentAction.PressInterval(TriggerSource.Touch(41), 1, 90, timestampNanos = 53L),
        ).state
        val undo = reducer.reduce(
            undoState,
            InstrumentAction.Undo(TriggerSource.Touch(42), 100, timestampNanos = 54L),
        )
        assertEquals(listOf(60, 57, 53), undo.noteOns().map { it.note })

        val home = reducer.reduce(
            reducer.initialState(config),
            InstrumentAction.Home(TriggerSource.Touch(43), sound = true, velocity = 100, timestampNanos = 55L),
        )
        assertEquals(listOf(60, 57, 53), home.noteOns().map { it.note })
    }

    @Test
    fun strumToneUsesFullVelocityWithoutMovingAndInvalidIndicesAreExactNoOps() {
        val source = TriggerSource.System("strum:1")
        var state = reducer.initialState(
            InstrumentConfig(chord = ChordLibrary.third, padArticulation = PadArticulation.MUTED),
        )
        state = reducer.reduce(
            state,
            InstrumentAction.PressInterval(TriggerSource.Touch(5), 1, 73, timestampNanos = 60L),
        ).state
        val noteBefore = state.currentNote
        val historyBefore = state.previousDistinctNotes
        assertEquals(listOf(62, 59, 62), state.strumNotes())

        val strummed = reducer.reduce(
            state,
            InstrumentAction.StrumTone(source, voiceIndex = 1, velocity = 123, timestampNanos = 61L),
        )
        assertEquals(listOf(59), strummed.noteOns().map { it.note })
        assertEquals(listOf(123), strummed.noteOns().map { it.velocity })
        assertEquals(noteBefore, strummed.state.currentNote)
        assertEquals(historyBefore, strummed.state.previousDistinctNotes)
        assertEquals(listOf(59), strummed.state.activeBySource.getValue(source).map { it.note })

        for (invalidIndex in listOf(-1, 3, Int.MAX_VALUE)) {
            val invalid = reducer.reduce(
                strummed.state,
                InstrumentAction.StrumTone(
                    TriggerSource.System("invalid:$invalidIndex"),
                    invalidIndex,
                    velocity = 127,
                    timestampNanos = 62L,
                ),
            )
            assertSame(strummed.state, invalid.state)
            assertTrue(invalid.events.isEmpty())
        }
    }

    @Test
    fun strumDuplicatesHaveIndependentOwnersAndPanicReleasesEveryInstanceInOrder() {
        val first = TriggerSource.System("strum:first")
        val duplicate = TriggerSource.System("strum:duplicate")
        var state = reducer.initialState(InstrumentConfig(chord = ChordLibrary.third))
        assertEquals(listOf(60, 57, 60), state.strumNotes())

        state = reducer.reduce(
            state,
            InstrumentAction.StrumTone(first, voiceIndex = 0, velocity = 100, timestampNanos = 70L),
        ).state
        state = reducer.reduce(
            state,
            InstrumentAction.StrumTone(duplicate, voiceIndex = 2, velocity = 90, timestampNanos = 71L),
        ).state
        assertEquals(2, state.activeInstanceCount)
        assertEquals(listOf(60), state.activeBySource.getValue(first).map { it.note })
        assertEquals(listOf(60), state.activeBySource.getValue(duplicate).map { it.note })

        val panic = reducer.reduce(state, InstrumentAction.Panic(timestampNanos = 72L))
        assertEquals(listOf(60, 60), panic.noteOffs().map { it.note })
        assertTrue(panic.state.activeBySource.isEmpty())
        assertTrue(panic.midiMessages().all { it.timestampNanos == 72L })
        assertEquals(OutputEvent.Audio(AudioCommand.Panic), panic.events.last())
    }

    @Test
    fun reusingAStrumSourceReleasesItsOldToneBeforeTheReplacement() {
        val source = TriggerSource.System("strum:reused")
        var state = reducer.initialState(InstrumentConfig(chord = ChordLibrary.triad))
        state = reducer.reduce(
            state,
            InstrumentAction.StrumTone(source, 0, velocity = 100, timestampNanos = 80L),
        ).state

        val replacement = reducer.reduce(
            state,
            InstrumentAction.StrumTone(source, 1, velocity = 99, timestampNanos = 81L),
        )
        assertEquals(listOf("off", "on"), replacement.midiMessages().map {
            when (it) {
                is MidiMessage.NoteOff -> "off"
                is MidiMessage.NoteOn -> "on"
                else -> "other"
            }
        })
        assertEquals(listOf(57), replacement.state.activeBySource.getValue(source).map { it.note })
        assertTrue(replacement.midiMessages().all { it.timestampNanos == 81L })
    }

    private fun InstrumentTransition.midiMessages(): List<MidiMessage> =
        events.filterIsInstance<OutputEvent.MidiOut>().map { it.message }

    private fun InstrumentTransition.noteOns(): List<MidiMessage.NoteOn> =
        midiMessages().filterIsInstance<MidiMessage.NoteOn>()

    private fun InstrumentTransition.noteOffs(): List<MidiMessage.NoteOff> =
        midiMessages().filterIsInstance<MidiMessage.NoteOff>()

    private fun InstrumentTransition.audioNoteOns(): List<AudioCommand.NoteOn> =
        events.filterIsInstance<OutputEvent.Audio>().map { it.command }.filterIsInstance<AudioCommand.NoteOn>()
}
