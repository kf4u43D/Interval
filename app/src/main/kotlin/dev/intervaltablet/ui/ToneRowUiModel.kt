package dev.intervaltablet.ui

import androidx.compose.runtime.Immutable

/**
 * Small presentation contract between the musical coordinator and Compose.
 *
 * These types deliberately contain no domain reducers or Android resources. The adapter remains
 * responsible for formatting notes/degrees while the UI only renders an immutable snapshot and
 * emits semantic user intents.
 */
@Immutable
data class ToneRowUiState(
    val available: Boolean = false,
    val phase: ToneRowUiPhase = ToneRowUiPhase.IDLE,
    val row: List<ToneRowStepUi> = emptyList(),
    val rowCursorIndex: Int? = null,
    val movementSequence: List<Int> = listOf(1),
    val sequenceCursorIndex: Int? = null,
    val playbackMode: ToneRowUiPlaybackMode = ToneRowUiPlaybackMode.PRIME,
    val inverted: Boolean = false,
    val transpositionSemitones: Int = 0,
    val translationDegrees: Int = 0,
    val octaveOffset: Int = 0,
    val clockSource: ToneRowUiClockSource = ToneRowUiClockSource.INTERNAL,
    val tempoBpm: Int = 120,
    val clockDivisionLabel: String = "1/4",
    val noteDurationPercent: Int = 75,
    val playOnce: Boolean = false,
    val selectedPresetSlot: Int = 1,
    val presetSlotCount: Int = 8,
    val occupiedPresetSlots: Set<Int> = emptySet(),
) {
    val boundedRowCursorIndex: Int?
        get() = rowCursorIndex?.takeIf { it in row.indices }

    val effectiveMovementSequence: List<Int>
        get() = movementSequence.ifEmpty { listOf(1) }

    val boundedSequenceCursorIndex: Int?
        get() = sequenceCursorIndex?.takeIf { it in effectiveMovementSequence.indices }

    val isRunning: Boolean
        get() = phase == ToneRowUiPhase.AUTO_PLAYING

    val canStartPlayback: Boolean
        get() = available && row.isNotEmpty()

    val canRecord: Boolean
        get() = available && phase != ToneRowUiPhase.AUTO_PLAYING

    val safePresetSlotCount: Int
        get() = presetSlotCount.coerceIn(1, 128)

    val safeSelectedPresetSlot: Int
        get() = selectedPresetSlot.coerceIn(1, safePresetSlotCount)

    val selectedPresetIsOccupied: Boolean
        get() = safeSelectedPresetSlot in occupiedPresetSlots
}

@Immutable
data class ToneRowStepUi(
    val noteLabel: String,
    val degreeLabel: String,
    val velocity: Int,
) {
    val safeVelocity: Int
        get() = velocity.coerceIn(1, 127)
}

enum class ToneRowUiPhase {
    IDLE,
    RECORDING,
    MANUAL_PLAYBACK,
    AUTO_PLAYING,
    PAUSED,
}

enum class ToneRowUiPlaybackMode {
    PRIME,
    RETRO,
    RANDOM,
    PENDULUM,
    AUTO_TRANSPOSE_UP,
    AUTO_TRANSPOSE_DOWN,
    AUTO_TRANSLATE_UP,
    AUTO_TRANSLATE_DOWN,
}

enum class ToneRowUiClockSource {
    INTERNAL,
    MIDI,
}

sealed interface ToneRowUiIntent {
    data object Record : ToneRowUiIntent

    data object PlayPause : ToneRowUiIntent

    data object Stop : ToneRowUiIntent

    data object Restart : ToneRowUiIntent

    data object PlayOnce : ToneRowUiIntent

    data class SetPlaybackMode(val mode: ToneRowUiPlaybackMode) : ToneRowUiIntent

    data object ToggleInversion : ToneRowUiIntent

    data class ChangeTranspositionSemitones(val delta: Int) : ToneRowUiIntent

    data class ChangeTranslationDegrees(val delta: Int) : ToneRowUiIntent

    data class ChangeOctave(val delta: Int) : ToneRowUiIntent

    data object ResetTransformations : ToneRowUiIntent

    data class AddSequenceStep(val movement: Int) : ToneRowUiIntent

    data class DeleteSequenceStep(val index: Int) : ToneRowUiIntent

    data class SelectSequenceStep(val index: Int) : ToneRowUiIntent

    data class ChangeTempo(val deltaBpm: Int) : ToneRowUiIntent

    data class ChangeClockDivision(val delta: Int) : ToneRowUiIntent

    data class ChangeNoteDuration(val deltaPercent: Int) : ToneRowUiIntent

    data class SetClockSource(val source: ToneRowUiClockSource) : ToneRowUiIntent

    data class SelectPreset(val slot: Int) : ToneRowUiIntent

    data object SavePreset : ToneRowUiIntent

    data object RecallPreset : ToneRowUiIntent

    data object DeletePreset : ToneRowUiIntent
}

@Immutable
internal data class ToneRowTimelineItem(
    val index: Int,
    val ordinal: Int,
    val noteLabel: String,
    val degreeLabel: String,
    val velocity: Int,
    val selected: Boolean,
)

internal fun ToneRowUiState.timelineItems(): List<ToneRowTimelineItem> {
    val selectedIndex = boundedRowCursorIndex
    return row.mapIndexed { index, step ->
        ToneRowTimelineItem(
            index = index,
            ordinal = index + 1,
            noteLabel = step.noteLabel,
            degreeLabel = step.degreeLabel,
            velocity = step.safeVelocity,
            selected = index == selectedIndex,
        )
    }
}

internal fun signedUiValue(value: Int): String = if (value > 0) "+$value" else value.toString()
