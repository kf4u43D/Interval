package dev.intervaltablet.ui

import androidx.compose.runtime.Immutable
import dev.intervaltablet.AppUiState
import dev.intervaltablet.data.PRESET_SLOT_COUNT
import dev.intervaltablet.data.PresetBank
import dev.intervaltablet.domain.ClockSource
import dev.intervaltablet.domain.ToneRowEntry
import dev.intervaltablet.domain.ToneRowMode
import dev.intervaltablet.domain.ToneRowPlayMode
import dev.intervaltablet.domain.midiNoteName

/**
 * Tone Row values whose formatted presentation is stable while only the live cursors move.
 *
 * Keeping this separate from [ToneRowCursorUiState] lets Compose retain the row labels, preset
 * occupancy and transport controls across every clock tick.
 */
@Immutable
internal data class ToneRowContentUiState(
    val available: Boolean,
    val phase: ToneRowUiPhase,
    val row: List<ToneRowStepUi>,
    val movementSequence: List<Int>,
    val playbackMode: ToneRowUiPlaybackMode,
    val inverted: Boolean,
    val transpositionSemitones: Int,
    val translationDegrees: Int,
    val octaveOffset: Int,
    val clockSource: ToneRowUiClockSource,
    val tempoBpm: Int,
    val clockDivisionLabel: String,
    val noteDurationPercent: Int,
    val playOnce: Boolean,
    val selectedPresetSlot: Int,
    val presetSlotCount: Int,
    val occupiedPresetSlots: Set<Int>,
)

@Immutable
internal data class ToneRowCursorUiState(
    val rowCursorIndex: Int?,
    val sequenceCursorIndex: Int?,
)

/** Raw immutable inputs used to gate the more expensive row/preset formatting. */
internal data class ToneRowContentInputs(
    val available: Boolean,
    val mode: ToneRowMode,
    val entries: List<ToneRowEntry>,
    val movementSequence: List<Int>,
    val playbackMode: ToneRowPlayMode,
    val inverted: Boolean,
    val transpositionSemitones: Int,
    val translationDegrees: Int,
    val octaveOffset: Int,
    val clockSource: ClockSource,
    val tempoBpm: Int,
    val clocksPerStep: Int,
    val noteDurationPercent: Int,
    val playOnce: Boolean,
    val selectedPresetSlot: Int,
    val presetBank: PresetBank,
)

/** Pure presentation adapter; no musical rule is recalculated by Compose. */
fun AppUiState.toToneRowUiState(): ToneRowUiState {
    return toToneRowContentInputs()
        .toToneRowContentUiState()
        .withCursor(toToneRowCursorUiState())
}

internal fun AppUiState.toToneRowContentInputs(): ToneRowContentInputs {
    val rowState = performance.toneRow
    val transportState = performance.transport
    return ToneRowContentInputs(
        available = settingsLoaded,
        mode = rowState.mode,
        entries = rowState.entries,
        movementSequence = rowState.intervalSequence,
        playbackMode = rowState.playMode,
        inverted = rowState.inverted,
        transpositionSemitones = rowState.transpositionSemitones,
        translationDegrees = rowState.translation,
        octaveOffset = rowState.octaveOffset,
        clockSource = transportState.clockSource,
        tempoBpm = transportState.tempoBpm,
        clocksPerStep = transportState.clocksPerStep,
        noteDurationPercent = transportState.noteDurationPercent,
        playOnce = rowState.playOnce,
        selectedPresetSlot = selectedPresetSlot,
        presetBank = presetBank,
    )
}

internal fun ToneRowContentInputs.toToneRowContentUiState(): ToneRowContentUiState {
    return ToneRowContentUiState(
        available = available,
        phase = when (mode) {
            ToneRowMode.IDLE -> ToneRowUiPhase.IDLE
            ToneRowMode.RECORDING -> ToneRowUiPhase.RECORDING
            ToneRowMode.MANUAL_PLAYBACK -> ToneRowUiPhase.MANUAL_PLAYBACK
            ToneRowMode.AUTO_PLAYING -> ToneRowUiPhase.AUTO_PLAYING
            ToneRowMode.PAUSED -> ToneRowUiPhase.PAUSED
        },
        row = entries.map { entry ->
            ToneRowStepUi(
                noteLabel = midiNoteName(entry.recordedMidiNote),
                degreeLabel = signedDegree(entry.relativeDegree),
                velocity = entry.velocity,
            )
        },
        movementSequence = movementSequence,
        playbackMode = when (playbackMode) {
            ToneRowPlayMode.PRIME -> ToneRowUiPlaybackMode.PRIME
            ToneRowPlayMode.RETRO -> ToneRowUiPlaybackMode.RETRO
            ToneRowPlayMode.RANDOM -> ToneRowUiPlaybackMode.RANDOM
            ToneRowPlayMode.PENDULUM -> ToneRowUiPlaybackMode.PENDULUM
            ToneRowPlayMode.AUTO_TRANSPOSE_UP -> ToneRowUiPlaybackMode.AUTO_TRANSPOSE_UP
            ToneRowPlayMode.AUTO_TRANSPOSE_DOWN -> ToneRowUiPlaybackMode.AUTO_TRANSPOSE_DOWN
            ToneRowPlayMode.AUTO_TRANSLATE_UP -> ToneRowUiPlaybackMode.AUTO_TRANSLATE_UP
            ToneRowPlayMode.AUTO_TRANSLATE_DOWN -> ToneRowUiPlaybackMode.AUTO_TRANSLATE_DOWN
        },
        inverted = inverted,
        transpositionSemitones = transpositionSemitones,
        translationDegrees = translationDegrees,
        octaveOffset = octaveOffset,
        clockSource = when (clockSource) {
            ClockSource.INTERNAL -> ToneRowUiClockSource.INTERNAL
            ClockSource.MIDI -> ToneRowUiClockSource.MIDI
        },
        tempoBpm = tempoBpm,
        clockDivisionLabel = clockDivisionLabel(clocksPerStep),
        noteDurationPercent = noteDurationPercent,
        playOnce = playOnce,
        selectedPresetSlot = selectedPresetSlot + 1,
        presetSlotCount = PRESET_SLOT_COUNT,
        occupiedPresetSlots = presetBank.presets.keys.mapTo(linkedSetOf()) { it + 1 },
    )
}

internal fun AppUiState.toToneRowCursorUiState(): ToneRowCursorUiState {
    val rowState = performance.toneRow
    return ToneRowCursorUiState(
        rowCursorIndex = rowState.rowIndex.takeIf { rowState.entries.isNotEmpty() },
        sequenceCursorIndex = rowState.sequenceIndex,
    )
}

internal fun ToneRowUiState.toToneRowContentUiState(): ToneRowContentUiState {
    return ToneRowContentUiState(
        available = available,
        phase = phase,
        row = row,
        movementSequence = movementSequence,
        playbackMode = playbackMode,
        inverted = inverted,
        transpositionSemitones = transpositionSemitones,
        translationDegrees = translationDegrees,
        octaveOffset = octaveOffset,
        clockSource = clockSource,
        tempoBpm = tempoBpm,
        clockDivisionLabel = clockDivisionLabel,
        noteDurationPercent = noteDurationPercent,
        playOnce = playOnce,
        selectedPresetSlot = selectedPresetSlot,
        presetSlotCount = presetSlotCount,
        occupiedPresetSlots = occupiedPresetSlots,
    )
}

internal fun ToneRowUiState.toToneRowCursorUiState(): ToneRowCursorUiState {
    return ToneRowCursorUiState(
        rowCursorIndex = boundedRowCursorIndex,
        sequenceCursorIndex = boundedSequenceCursorIndex,
    )
}

internal fun ToneRowContentUiState.withCursor(cursor: ToneRowCursorUiState): ToneRowUiState {
    return ToneRowUiState(
        available = available,
        phase = phase,
        row = row,
        rowCursorIndex = cursor.rowCursorIndex,
        movementSequence = movementSequence,
        sequenceCursorIndex = cursor.sequenceCursorIndex,
        playbackMode = playbackMode,
        inverted = inverted,
        transpositionSemitones = transpositionSemitones,
        translationDegrees = translationDegrees,
        octaveOffset = octaveOffset,
        clockSource = clockSource,
        tempoBpm = tempoBpm,
        clockDivisionLabel = clockDivisionLabel,
        noteDurationPercent = noteDurationPercent,
        playOnce = playOnce,
        selectedPresetSlot = selectedPresetSlot,
        presetSlotCount = presetSlotCount,
        occupiedPresetSlots = occupiedPresetSlots,
    )
}

internal fun clockDivisionLabel(clocksPerStep: Int): String {
    require(clocksPerStep > 0)
    val divisor = greatestCommonDivisor(clocksPerStep, CLOCKS_PER_WHOLE_NOTE)
    return "${clocksPerStep / divisor}/${CLOCKS_PER_WHOLE_NOTE / divisor}"
}

private fun signedDegree(value: Int): String = if (value > 0) "+$value" else value.toString()

private tailrec fun greatestCommonDivisor(left: Int, right: Int): Int {
    return if (right == 0) kotlin.math.abs(left) else greatestCommonDivisor(right, left % right)
}

private const val CLOCKS_PER_WHOLE_NOTE: Int = 96
