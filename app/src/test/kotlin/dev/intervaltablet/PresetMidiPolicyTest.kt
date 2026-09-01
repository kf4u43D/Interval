package dev.intervaltablet

import dev.intervaltablet.data.PerformancePresetSnapshot
import dev.intervaltablet.data.PresetBank
import dev.intervaltablet.data.PresetMidiPolicy
import dev.intervaltablet.data.PresetRecallDecision
import dev.intervaltablet.data.PresetRecallOrigin
import dev.intervaltablet.domain.MidiMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PresetMidiPolicyTest {
    private val preset = PerformancePresetSnapshot(name = "Slot 12")
    private val bank = PresetBank().save(12, preset)

    @Test
    fun matchingProgramChangeConsumesExistingZeroBasedSlot() {
        assertEquals(
            PresetRecallDecision.Consumed(12, preset, PresetRecallOrigin.PROGRAM_CHANGE),
            PresetMidiPolicy.resolve(
                message = MidiMessage.ProgramChange(channel = 3, program = 12),
                bank = bank,
                inputChannel = 3,
                allowRecall = true,
            ),
        )
    }

    @Test
    fun programChangeHonorsInputChannelAndOmni() {
        assertSame(
            PresetRecallDecision.NotApplicable,
            PresetMidiPolicy.resolve(MidiMessage.ProgramChange(2, 12), bank, inputChannel = 3, allowRecall = true),
        )
        assertEquals(
            PresetRecallDecision.Consumed(12, preset, PresetRecallOrigin.PROGRAM_CHANGE),
            PresetMidiPolicy.resolve(MidiMessage.ProgramChange(2, 12), bank, inputChannel = null, allowRecall = true),
        )
    }

    @Test
    fun songSelectIsGlobalButPassThruCanDisableRecall() {
        assertEquals(
            PresetRecallDecision.Consumed(12, preset, PresetRecallOrigin.SONG_SELECT),
            PresetMidiPolicy.resolve(MidiMessage.SongSelect(12), bank, inputChannel = 9, allowRecall = true),
        )
        assertSame(
            PresetRecallDecision.NotApplicable,
            PresetMidiPolicy.resolve(MidiMessage.SongSelect(12), bank, inputChannel = null, allowRecall = false),
        )
    }

    @Test
    fun emptySlotIsExplicitlyNotFoundAndMustNotBeConsumed() {
        assertEquals(
            PresetRecallDecision.NotFound(99, PresetRecallOrigin.PROGRAM_CHANGE),
            PresetMidiPolicy.resolve(MidiMessage.ProgramChange(0, 99), bank, null, allowRecall = true),
        )
        assertEquals(
            PresetRecallDecision.NotFound(0, PresetRecallOrigin.SONG_SELECT),
            PresetMidiPolicy.resolve(MidiMessage.SongSelect(0), bank, null, allowRecall = true),
        )
    }
}
