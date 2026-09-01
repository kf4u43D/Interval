package dev.intervaltablet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MidiMappingTest {
    @Test
    fun exactChannelBindingAndThresholdOverrideOmniBinding() {
        val omni = MidiBindingKey(MidiBindingKey.Kind.CC, 7)
        val exact = MidiBindingKey(MidiBindingKey.Kind.CC, 7, 2)
        val mapping = MidiMapping(
            bindings = mapOf(
                omni to MidiAction.Move(1),
                exact to MidiAction.Move(3),
            ),
            ccThresholds = mapOf(omni to 64, exact to 96),
        )

        assertEquals(MidiAction.Move(3), mapping.ccAction(2, 7))
        assertEquals(96, mapping.ccThreshold(2, 7))
        assertEquals(MidiAction.Move(1), mapping.ccAction(1, 7))
        assertEquals(64, mapping.ccThreshold(1, 7))
    }

    @Test
    fun defaultMapContainsTheNineDirectIntervalActions() {
        val expected = mapOf(
            65 to -4,
            67 to -3,
            69 to -2,
            71 to -1,
            72 to 0,
            77 to 1,
            79 to 2,
            81 to 3,
            83 to 4,
        )

        expected.forEach { (note, steps) ->
            assertEquals("note $note", MidiAction.Move(steps), DefaultMidiMap.mapping.noteAction(0, note))
        }
    }

    @Test
    fun resetReturnsAFreshDefaultMapping() {
        val custom = MidiMapping(mapOf(MidiBindingKey(MidiBindingKey.Kind.NOTE, 1) to MidiAction.Move(1)))
        val first = custom.reset()
        val second = custom.reset()

        assertEquals(DefaultMidiMap.mapping, first)
        assertEquals(first, second)
        assertNotSame(first, second)
    }

    @Test
    fun undoThenMoveConvertsToOneAtomicInstrumentAction() {
        val source = TriggerSource.Midi(1, 0, 0, 60)
        val actions = MidiAction.UndoThenMove(-3).toInstrumentActions(source, 90, 42)

        assertEquals(1, actions.size)
        assertEquals(InstrumentAction.UndoThenMove(source, -3, 90, 42), actions.single())
    }

    @Test
    fun semanticActionsConvertToDistinctImmediateInstrumentActions() {
        val source = TriggerSource.Midi(1, 0, 2, 75)

        assertEquals(
            InstrumentAction.PressSameInterval(source, 90, 42),
            MidiAction.Same.toInstrumentActions(source, 90, 42).single(),
        )
        assertEquals(
            InstrumentAction.PressSamePitch(source, 91, 43),
            MidiAction.SamePitch.toInstrumentActions(source, 91, 43).single(),
        )
        assertEquals(
            InstrumentAction.PressRandomInterval(source, 92, 44),
            MidiAction.Random.toInstrumentActions(source, 92, 44).single(),
        )
        assertEquals(
            InstrumentAction.HoldChromaticShift(source, -1, 45),
            MidiAction.ChromaticShift(-1).toInstrumentActions(source, 127, 45).single(),
        )
    }

    @Test
    fun requiresReleaseIncludesSoundingActionsAndSilentMomentaryModifiersOnly() {
        assertTrue(MidiAction.Same.requiresRelease())
        assertTrue(MidiAction.SamePitch.requiresRelease())
        assertTrue(MidiAction.Random.requiresRelease())
        assertTrue(MidiAction.ChromaticShift(1).requiresRelease())
        assertTrue(MidiAction.Home(sound = true).requiresRelease())

        assertFalse(MidiAction.Home(sound = false).requiresRelease())
        assertFalse(MidiAction.Play.requiresRelease())
        assertFalse(MidiAction.Record.requiresRelease())
        assertFalse(MidiAction.TogglePassThrough.requiresRelease())
    }

    @Test
    fun publicActionAndThresholdBoundsAreRejected() {
        expectIllegalArgument { MidiAction.Move(-15) }
        expectIllegalArgument { MidiAction.Move(15) }
        expectIllegalArgument { MidiAction.UndoThenMove(15) }
        expectIllegalArgument { MidiAction.Chromatic(128) }
        expectIllegalArgument { MidiAction.ChromaticShift(13) }
        expectIllegalArgument { MidiAction.Octave(11) }

        val cc = MidiBindingKey(MidiBindingKey.Kind.CC, 7)
        expectIllegalArgument {
            MidiMapping(bindings = mapOf(cc to MidiAction.Move(1)), ccThresholds = mapOf(cc to 0))
        }
        assertTrue(MidiAction.Move(-14).steps == -14)
        assertTrue(MidiAction.Move(14).steps == 14)
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
