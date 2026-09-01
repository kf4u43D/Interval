package dev.intervaltablet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForceToScaleTest {
    private val reducer = IntervalReducer()

    @Test
    fun standardScaleLibraryContainsAllDiatonicModesAndCommonMinorScales() {
        val ids = ScaleLibrary.all.map(ScaleDefinition::id)

        assertTrue(ids.containsAll(listOf(
            "major",
            "natural_minor",
            "harmonic_minor",
            "melodic_minor",
            "dorian",
            "phrygian",
            "lydian",
            "mixolydian",
            "locrian",
            "major_pentatonic",
            "minor_pentatonic",
            "blues",
            "chromatic",
        )))
        assertEquals(ScaleLibrary.harmonicMinor, ScaleLibrary.byId("harmonic_minor"))
        assertEquals(ScaleLibrary.major, ScaleLibrary.byId("unknown"))
    }

    @Test
    fun chromaticPressQuantizesToNearestScaleNoteAndBreaksTieDownward() {
        val state = reducer.initialState(
            InstrumentConfig(
                scale = ScaleDefinition("whole_tone", "Whole Tone", listOf(0, 2, 4, 6, 8, 10)),
                forceToScale = true,
            ),
        ).copy(currentNote = 60)

        val transition = reducer.reduce(
            state,
            InstrumentAction.PressChromatic(TriggerSource.Touch(1), semitones = 1, velocity = 90),
        )

        assertEquals(60, transition.state.currentNote)
        assertEquals(listOf(60), transition.noteOns())
    }

    @Test
    fun disabledForceToScalePreservesChromaticPitch() {
        val state = reducer.initialState(InstrumentConfig(forceToScale = false)).copy(currentNote = 60)

        val transition = reducer.reduce(
            state,
            InstrumentAction.PressChromatic(TriggerSource.Touch(1), semitones = 1, velocity = 90),
        )

        assertEquals(61, transition.state.currentNote)
        assertEquals(listOf(61), transition.noteOns())
    }

    @Test
    fun forceToScaleQuantizesChromaticShiftedChordAndKeepsReleaseOwnership() {
        val shiftSource = TriggerSource.Midi(1, 0, 0, 40)
        val noteSource = TriggerSource.Touch(7)
        var state = reducer.initialState(
            InstrumentConfig(
                chord = ChordLibrary.triad,
                padArticulation = PadArticulation.STACKED,
                forceToScale = true,
            ),
        ).copy(currentNote = 60)
        state = reducer.reduce(
            state,
            InstrumentAction.HoldChromaticShift(shiftSource, semitones = 1),
        ).state

        val pressed = reducer.reduce(
            state,
            InstrumentAction.PressInterval(noteSource, steps = 0, velocity = 100),
        )
        val released = reducer.reduce(pressed.state, InstrumentAction.Release(noteSource))

        assertEquals(listOf(60, 57, 53), pressed.noteOns())
        assertEquals(listOf(60, 57, 53), released.noteOffs())
        assertTrue(released.state.activeBySource.isEmpty())
        assertFalse(released.state.chromaticShiftBySource.isEmpty())
    }

    @Test
    fun togglingForceToScaleDoesNotCutAlreadyHeldNotes() {
        val source = TriggerSource.Touch(3)
        val state = reducer.reduce(
            reducer.initialState(),
            InstrumentAction.PressChromatic(source, semitones = 1, velocity = 80),
        ).state

        val toggled = reducer.reduce(state, InstrumentAction.SetForceToScale(true))
        val released = reducer.reduce(toggled.state, InstrumentAction.Release(source))

        assertTrue(toggled.events.isEmpty())
        assertTrue(toggled.state.config.forceToScale)
        assertEquals(listOf(61), released.noteOffs())
    }

    private fun InstrumentTransition.noteOns(): List<Int> = events.mapNotNull { event ->
        ((event as? OutputEvent.MidiOut)?.message as? MidiMessage.NoteOn)?.note
    }

    private fun InstrumentTransition.noteOffs(): List<Int> = events.mapNotNull { event ->
        ((event as? OutputEvent.MidiOut)?.message as? MidiMessage.NoteOff)?.note
    }
}
