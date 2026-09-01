package dev.intervaltablet.ui

import dev.intervaltablet.AppUiState
import dev.intervaltablet.domain.MIDI_MAPPING_DEFAULT_CC_THRESHOLD
import dev.intervaltablet.domain.MidiAction
import dev.intervaltablet.domain.MidiBindingKey
import dev.intervaltablet.domain.MidiMapping
import dev.intervaltablet.domain.MidiMappingEditorState
import dev.intervaltablet.midi.MidiConnectionPhase
import dev.intervaltablet.midi.MidiConnectionState
import dev.intervaltablet.midi.MidiPortDescriptor
import dev.intervaltablet.midi.MidiPortDirection
import dev.intervaltablet.midi.MidiRepositoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiMappingEditorUiModelTest {
    @Test
    fun primitiveSavedValuesRestoreSelectionWithoutCustomParcelableState() {
        val restored = midiLearnActionSelectionFromSavedValues(
            kindName = MidiLearnActionKind.CHROMATIC_SHIFT.name,
            amount = -7,
            homeSound = false,
        )
        assertEquals(
            MidiLearnActionSelection(
                kind = MidiLearnActionKind.CHROMATIC_SHIFT,
                amount = -7,
                homeSound = false,
            ),
            restored,
        )
        assertEquals(
            MidiLearnActionKind.MOVE,
            midiLearnActionSelectionFromSavedValues("removed-kind", 4, true).kind,
        )
    }

    @Test
    fun actionCatalogueBuildsEveryCurrentMidiActionFamily() {
        val actions = MidiLearnActionKind.entries.associateWith { kind ->
            MidiLearnActionSelection(kind = kind, amount = 1, homeSound = true).toMidiAction()
        }

        assertEquals(MidiLearnActionKind.entries.toSet(), actions.keys)
        assertEquals(MidiAction.Move(1), actions[MidiLearnActionKind.MOVE])
        assertEquals(MidiAction.Chromatic(1), actions[MidiLearnActionKind.CHROMATIC])
        assertEquals(
            MidiAction.ChromaticShift(1),
            actions[MidiLearnActionKind.CHROMATIC_SHIFT],
        )
        assertEquals(
            MidiAction.UndoThenMove(1),
            actions[MidiLearnActionKind.UNDO_THEN_MOVE],
        )
        assertEquals(MidiAction.Octave(1), actions[MidiLearnActionKind.OCTAVE])
        assertEquals(MidiAction.Home(sound = true), actions[MidiLearnActionKind.HOME])
        assertEquals(MidiAction.Same, actions[MidiLearnActionKind.SAME])
        assertEquals(MidiAction.SamePitch, actions[MidiLearnActionKind.SAME_PITCH])
        assertEquals(MidiAction.Random, actions[MidiLearnActionKind.RANDOM])
        assertEquals(MidiAction.TogglePassThrough, actions[MidiLearnActionKind.TOGGLE_PASS_THROUGH])
        assertEquals(MidiAction.Record, actions[MidiLearnActionKind.RECORD])
    }

    @Test
    fun parameterizedActionSelectionClampsToDomainBoundaries() {
        assertEquals(
            MidiAction.Move(14),
            MidiLearnActionSelection(MidiLearnActionKind.MOVE, amount = 999).toMidiAction(),
        )
        assertEquals(
            MidiAction.Chromatic(-127),
            MidiLearnActionSelection(MidiLearnActionKind.CHROMATIC, amount = -999).toMidiAction(),
        )
        assertEquals(
            MidiAction.ChromaticShift(12),
            MidiLearnActionSelection(
                MidiLearnActionKind.CHROMATIC_SHIFT,
                amount = 99,
            ).toMidiAction(),
        )
        assertEquals(
            MidiAction.Octave(-10),
            MidiLearnActionSelection(MidiLearnActionKind.OCTAVE, amount = -99).toMidiAction(),
        )
    }

    @Test
    fun projectionSortsBindingsAndExposesEffectiveThresholdAndConnectionContext() {
        val noteOmni = MidiBindingKey(MidiBindingKey.Kind.NOTE, 60, channel = null)
        val noteExact = MidiBindingKey(MidiBindingKey.Kind.NOTE, 60, channel = 3)
        val ccDefault = MidiBindingKey(MidiBindingKey.Kind.CC, 4, channel = 2)
        val ccCustom = MidiBindingKey(MidiBindingKey.Kind.CC, 9, channel = null)
        val mapping = MidiMapping(
            bindings = linkedMapOf(
                ccCustom to MidiAction.Play,
                noteExact to MidiAction.SamePitch,
                ccDefault to MidiAction.Stop,
                noteOmni to MidiAction.Move(1),
            ),
            ccThresholds = mapOf(ccCustom to 23),
        )
        val source = MidiPortDescriptor(
            deviceId = 4,
            portNumber = 2,
            direction = MidiPortDirection.SOURCE,
            deviceName = "Keyboard",
            portName = "MIDI Out",
        )
        val projected = AppUiState(
            midiMappingEditor = MidiMappingEditorState.Editing(mapping, mapping),
            midi = MidiRepositoryState(
                selectedSource = source,
                sourceConnection = MidiConnectionState(
                    phase = MidiConnectionPhase.OPEN,
                    descriptor = source,
                    generation = 7,
                ),
            ),
            hostStarted = true,
            selectedPresetSlot = 5,
        ).toMidiMappingEditorUiState()

        assertEquals(listOf(noteOmni, noteExact, ccDefault, ccCustom), projected.bindings.map { it.key })
        assertEquals(
            MIDI_MAPPING_DEFAULT_CC_THRESHOLD,
            projected.bindings.first { it.key == ccDefault }.ccThreshold,
        )
        assertEquals(23, projected.bindings.first { it.key == ccCustom }.ccThreshold)
        assertTrue(projected.sourceConnected)
        assertEquals(source.displayName, projected.sourceName)
        assertEquals(6, projected.selectedPresetNumber)
    }
}
