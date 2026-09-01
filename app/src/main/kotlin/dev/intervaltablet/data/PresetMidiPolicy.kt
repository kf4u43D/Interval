package dev.intervaltablet.data

import dev.intervaltablet.domain.MidiMessage

enum class PresetRecallOrigin { PROGRAM_CHANGE, SONG_SELECT }

sealed interface PresetRecallDecision {
    /** The caller must release active notes before atomically installing [preset]. */
    data class Consumed(
        val slot: Int,
        val preset: PerformancePresetSnapshot,
        val origin: PresetRecallOrigin,
    ) : PresetRecallDecision

    /** A valid recall command targeted an empty slot and must continue through normal routing. */
    data class NotFound(
        val slot: Int,
        val origin: PresetRecallOrigin,
    ) : PresetRecallDecision

    /** Recall is disabled (notably in PassThru) or the message is unrelated/wrong-channel. */
    data object NotApplicable : PresetRecallDecision
}

/**
 * Gate-2 preset recall policy:
 *
 * - Program Change uses the zero-based program as slot and follows the configured input channel.
 * - Song Select is system-common/global and uses the zero-based song as slot.
 * - only an existing slot is consumed; an empty slot remains available to normal MIDI routing.
 * - [allowRecall] lets the coordinator preserve byte-exact PassThru semantics.
 */
object PresetMidiPolicy {
    fun resolve(
        message: MidiMessage,
        bank: PresetBank,
        inputChannel: Int?,
        allowRecall: Boolean,
    ): PresetRecallDecision {
        if (!allowRecall) return PresetRecallDecision.NotApplicable
        val candidate = when (message) {
            is MidiMessage.ProgramChange -> {
                if (inputChannel != null && message.channel != inputChannel) return PresetRecallDecision.NotApplicable
                message.program to PresetRecallOrigin.PROGRAM_CHANGE
            }
            is MidiMessage.SongSelect -> message.song to PresetRecallOrigin.SONG_SELECT
            else -> return PresetRecallDecision.NotApplicable
        }
        val (slot, origin) = candidate
        val preset = bank[slot] ?: return PresetRecallDecision.NotFound(slot, origin)
        return PresetRecallDecision.Consumed(slot, preset, origin)
    }
}
