package dev.intervaltablet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intervaltablet.R

private val ToneRowDeckShape = RoundedCornerShape(20.dp)
private val ToneRowControlShape = RoundedCornerShape(14.dp)

/**
 * Performance-facing Tone Row surface. Transport, both cursors and live transformations remain
 * visible while secondary arrangement/clock editing is delegated to [onOpenArrangement].
 */
@Composable
fun ToneRowDeck(
    state: ToneRowUiState,
    performanceLock: Boolean,
    compact: Boolean,
    onIntent: (ToneRowUiIntent) -> Unit,
    onOpenArrangement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cursorState = rememberUpdatedState(state.toToneRowCursorUiState())
    ToneRowDeckContent(
        state = state.toToneRowContentUiState(),
        cursorState = cursorState,
        performanceLock = performanceLock,
        compact = compact,
        onIntent = onIntent,
        onOpenArrangement = onOpenArrangement,
        modifier = modifier,
    )
}

/** Hot-path entry point: clock cursor updates remain State reads inside cursor leaves. */
@Composable
internal fun ToneRowDeck(
    state: State<ToneRowContentUiState>,
    cursorState: State<ToneRowCursorUiState>,
    performanceLock: Boolean,
    compact: Boolean,
    onIntent: (ToneRowUiIntent) -> Unit,
    onOpenArrangement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToneRowDeckContent(
        state = state.value,
        cursorState = cursorState,
        performanceLock = performanceLock,
        compact = compact,
        onIntent = onIntent,
        onOpenArrangement = onOpenArrangement,
        modifier = modifier,
    )
}

@Composable
private fun ToneRowDeckContent(
    state: ToneRowContentUiState,
    cursorState: State<ToneRowCursorUiState>,
    performanceLock: Boolean,
    compact: Boolean,
    onIntent: (ToneRowUiIntent) -> Unit,
    onOpenArrangement: () -> Unit,
    modifier: Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = if (compact) 142.dp else 154.dp),
        shape = ToneRowDeckShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (compact) 7.dp else 9.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
            ) {
                ToneRowIdentity(
                    phase = state.phase,
                    rowSize = state.row.size,
                    compact = compact,
                )
                ToneRowTransport(
                    available = state.available,
                    phase = state.phase,
                    hasRow = state.row.isNotEmpty(),
                    playOnce = state.playOnce,
                    compact = compact,
                    onIntent = onIntent,
                )
                ToneRowTimeline(
                    available = state.available,
                    row = state.row,
                    cursorState = cursorState,
                    compact = compact,
                    modifier = Modifier.weight(1f),
                )
                SequenceCursorReadout(
                    movementSequence = state.movementSequence,
                    cursorState = cursorState,
                    compact = compact,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            ToneRowTransformControls(
                available = state.available,
                playbackMode = state.playbackMode,
                inverted = state.inverted,
                transpositionSemitones = state.transpositionSemitones,
                translationDegrees = state.translationDegrees,
                octaveOffset = state.octaveOffset,
                performanceLock = performanceLock,
                onIntent = onIntent,
                onOpenArrangement = onOpenArrangement,
            )
        }
    }
}

@Composable
private fun ToneRowTransformControls(
    available: Boolean,
    playbackMode: ToneRowUiPlaybackMode,
    inverted: Boolean,
    transpositionSemitones: Int,
    translationDegrees: Int,
    octaveOffset: Int,
    performanceLock: Boolean,
    onIntent: (ToneRowUiIntent) -> Unit,
    onOpenArrangement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ToneRowUiPlaybackMode.entries.forEach { mode ->
            FilterChip(
                selected = playbackMode == mode,
                onClick = { onIntent(ToneRowUiIntent.SetPlaybackMode(mode)) },
                enabled = available,
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .semantics {
                        role = Role.RadioButton
                        selected = playbackMode == mode
                    },
                label = { Text(playbackModeLabel(mode), maxLines = 1) },
            )
        }
        FilterChip(
            selected = inverted,
            onClick = { onIntent(ToneRowUiIntent.ToggleInversion) },
            enabled = available,
            modifier = Modifier.heightIn(min = 56.dp),
            label = { Text(stringResource(R.string.tone_row_invert), maxLines = 1) },
        )
        TransformStepper(
            label = stringResource(R.string.tone_row_transposition_semitones),
            value = signedUiValue(transpositionSemitones),
            enabled = available,
            onDecrease = { onIntent(ToneRowUiIntent.ChangeTranspositionSemitones(-1)) },
            onIncrease = { onIntent(ToneRowUiIntent.ChangeTranspositionSemitones(1)) },
        )
        TransformStepper(
            label = stringResource(R.string.tone_row_translation_degrees),
            value = signedUiValue(translationDegrees),
            enabled = available,
            onDecrease = { onIntent(ToneRowUiIntent.ChangeTranslationDegrees(-1)) },
            onIncrease = { onIntent(ToneRowUiIntent.ChangeTranslationDegrees(1)) },
        )
        TransformStepper(
            label = stringResource(R.string.tone_row_octave),
            value = signedUiValue(octaveOffset),
            enabled = available,
            onDecrease = { onIntent(ToneRowUiIntent.ChangeOctave(-1)) },
            onIncrease = { onIntent(ToneRowUiIntent.ChangeOctave(1)) },
        )
        OutlinedButton(
            onClick = { onIntent(ToneRowUiIntent.ResetTransformations) },
            enabled = available,
            modifier = Modifier.heightIn(min = 56.dp),
            shape = ToneRowControlShape,
        ) {
            Text(stringResource(R.string.tone_row_reset), maxLines = 1)
        }
        if (!performanceLock) {
            Button(
                onClick = onOpenArrangement,
                enabled = available,
                modifier = Modifier.heightIn(min = 56.dp),
                shape = ToneRowControlShape,
            ) {
                Text(stringResource(R.string.tone_row_arrangement), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ToneRowIdentity(
    phase: ToneRowUiPhase,
    rowSize: Int,
    compact: Boolean,
) {
    Column(modifier = Modifier.width(if (compact) 96.dp else 118.dp)) {
        Text(
            stringResource(R.string.tone_row_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
        )
        Text(
            phaseLabel(phase),
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            pluralStringResource(R.plurals.tone_row_count, rowSize, rowSize),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToneRowTransport(
    available: Boolean,
    phase: ToneRowUiPhase,
    hasRow: Boolean,
    playOnce: Boolean,
    compact: Boolean,
    onIntent: (ToneRowUiIntent) -> Unit,
) {
    val isRunning = phase == ToneRowUiPhase.AUTO_PLAYING
    val canRecord = available && phase != ToneRowUiPhase.AUTO_PLAYING
    val canStartPlayback = available && hasRow
    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
        TransportButton(
            text = "●",
            description = stringResource(R.string.tone_row_record_description),
            active = phase == ToneRowUiPhase.RECORDING,
            enabled = canRecord,
            onClick = { onIntent(ToneRowUiIntent.Record) },
        )
        TransportButton(
            text = if (isRunning) "Ⅱ" else "▶",
            description = if (isRunning) {
                stringResource(R.string.tone_row_pause_description)
            } else {
                stringResource(R.string.tone_row_play_description)
            },
            active = isRunning,
            enabled = canStartPlayback,
            onClick = { onIntent(ToneRowUiIntent.PlayPause) },
        )
        TransportButton(
            text = "■",
            description = stringResource(R.string.tone_row_stop_description),
            enabled = available,
            onClick = { onIntent(ToneRowUiIntent.Stop) },
        )
        TransportButton(
            text = "↺",
            description = stringResource(R.string.tone_row_restart_description),
            enabled = canStartPlayback,
            onClick = { onIntent(ToneRowUiIntent.Restart) },
        )
        TransportButton(
            text = "1×",
            description = stringResource(R.string.tone_row_play_once_description),
            active = playOnce,
            enabled = canStartPlayback,
            onClick = { onIntent(ToneRowUiIntent.PlayOnce) },
        )
    }
}

@Composable
private fun TransportButton(
    text: String,
    description: String,
    enabled: Boolean,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val modifier = Modifier
        .sizeIn(minWidth = 56.dp, minHeight = 56.dp)
        .semantics {
            contentDescription = description
            stateDescription = if (active) description else ""
        }
    if (active) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = ToneRowControlShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(text, fontWeight = FontWeight.Black)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = ToneRowControlShape,
        ) {
            Text(text, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ToneRowTimeline(
    available: Boolean,
    row: List<ToneRowStepUi>,
    cursorState: State<ToneRowCursorUiState>,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 60.dp),
        shape = ToneRowControlShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (row.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    if (available) {
                        stringResource(R.string.tone_row_empty)
                    } else {
                        stringResource(R.string.tone_row_unavailable)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEachIndexed { index, step ->
                    val selectedState = remember(cursorState, index) {
                        derivedStateOf(structuralEqualityPolicy()) {
                            cursorState.value.rowCursorIndex == index
                        }
                    }
                    TimelineStep(
                        ordinal = index + 1,
                        noteLabel = step.noteLabel,
                        degreeLabel = step.degreeLabel,
                        velocity = step.safeVelocity,
                        selectedState = selectedState,
                        compact = compact,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineStep(
    ordinal: Int,
    noteLabel: String,
    degreeLabel: String,
    velocity: Int,
    selectedState: State<Boolean>,
    compact: Boolean,
) {
    val selected = selectedState.value
    val cursorDescription = if (selected) stringResource(R.string.tone_row_current_step) else ""
    val description = stringResource(
        R.string.tone_row_step_description,
        ordinal,
        noteLabel,
        degreeLabel,
        velocity,
    )
    Surface(
        modifier = Modifier
            .width(if (compact) 64.dp else 72.dp)
            // fillMaxHeight() would claim the performance column's full available height
            // when the first row entry appears, collapsing the interval-pad area.
            .heightIn(min = if (compact) 52.dp else 56.dp)
            .semantics {
                contentDescription = description
                this.selected = selected
                stateDescription = cursorDescription
            },
        shape = RoundedCornerShape(11.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "$ordinal · $degreeLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                noteLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "v$velocity",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SequenceCursorReadout(
    movementSequence: List<Int>,
    cursorState: State<ToneRowCursorUiState>,
    compact: Boolean,
) {
    val sequenceCursorIndex = cursorState.value.sequenceCursorIndex
    val sequence = movementSequence.ifEmpty { listOf(1) }
    val cursor = sequenceCursorIndex?.takeIf { it in sequence.indices }
    val currentMovement = cursor?.let(sequence::get)
    val cursorText = cursor?.let { "${it + 1}/${sequence.size}" } ?: "—/${sequence.size}"
    val sequenceDescription = if (currentMovement == null) {
        stringResource(R.string.tone_row_sequence_empty_description, sequence.size)
    } else {
        stringResource(
            R.string.tone_row_sequence_cursor_description,
            cursor + 1,
            sequence.size,
            signedUiValue(currentMovement),
        )
    }
    Surface(
        modifier = Modifier
            .width(if (compact) 80.dp else 96.dp)
            .heightIn(min = 60.dp)
            .semantics {
                contentDescription = sequenceDescription
            },
        shape = ToneRowControlShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.tone_row_sequence).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(cursorText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(
                currentMovement?.let(::signedUiValue) ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

/** Secondary editor, intended for the existing non-modal stage overlay. */
@Composable
fun ToneRowArrangementPanel(
    state: ToneRowUiState,
    onClose: () -> Unit,
    onIntent: (ToneRowUiIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentState = rememberUpdatedState(state.toToneRowContentUiState())
    val cursorState = rememberUpdatedState(state.toToneRowCursorUiState())
    ToneRowArrangementPanel(
        state = contentState,
        cursorState = cursorState,
        onClose = onClose,
        onIntent = onIntent,
        modifier = modifier,
    )
}

/** Hot-path entry point: the running sequence cursor invalidates only its editor. */
@Composable
internal fun ToneRowArrangementPanel(
    state: State<ToneRowContentUiState>,
    cursorState: State<ToneRowCursorUiState>,
    onClose: () -> Unit,
    onIntent: (ToneRowUiIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToneRowArrangementPanelContent(
        state = state.value,
        cursorState = cursorState,
        onClose = onClose,
        onIntent = onIntent,
        modifier = modifier,
    )
}

@Composable
private fun ToneRowArrangementPanelContent(
    state: ToneRowContentUiState,
    cursorState: State<ToneRowCursorUiState>,
    onClose: () -> Unit,
    onIntent: (ToneRowUiIntent) -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = ToneRowDeckShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.tone_row_arrangement).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        stringResource(R.string.tone_row_arrangement_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.tone_row_close_arrangement))
                }
            }

            ArrangementSectionTitle(stringResource(R.string.tone_row_movement_sequence))
            MovementSequenceEditor(
                available = state.available,
                movementSequence = state.movementSequence,
                cursorState = cursorState,
                onIntent = onIntent,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ArrangementSectionTitle(stringResource(R.string.tone_row_add_step))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (-4..4).forEach { movement ->
                    OutlinedButton(
                        onClick = { onIntent(ToneRowUiIntent.AddSequenceStep(movement)) },
                        enabled = state.available,
                        modifier = Modifier.sizeIn(minWidth = 56.dp, minHeight = 56.dp),
                        shape = ToneRowControlShape,
                    ) {
                        Text(signedUiValue(movement), fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ArrangementSectionTitle(stringResource(R.string.tone_row_presets))
            PresetEditor(
                available = state.available,
                selectedPresetSlot = state.selectedPresetSlot,
                presetSlotCount = state.presetSlotCount,
                occupiedPresetSlots = state.occupiedPresetSlots,
                onIntent = onIntent,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ArrangementSectionTitle(stringResource(R.string.tone_row_clock))
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToneRowUiClockSource.entries.forEach { source ->
                    FilterChip(
                        selected = state.clockSource == source,
                        onClick = { onIntent(ToneRowUiIntent.SetClockSource(source)) },
                        enabled = state.available,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        label = { Text(clockSourceLabel(source), maxLines = 1) },
                    )
                }
            }
            EditorStepper(
                label = stringResource(R.string.tone_row_tempo),
                value = stringResource(R.string.tone_row_tempo_value, state.tempoBpm.coerceIn(1, 999)),
                enabled = state.available && state.clockSource == ToneRowUiClockSource.INTERNAL,
                decreaseDescription = stringResource(R.string.tone_row_tempo_decrease),
                increaseDescription = stringResource(R.string.tone_row_tempo_increase),
                onDecrease = { onIntent(ToneRowUiIntent.ChangeTempo(-1)) },
                onIncrease = { onIntent(ToneRowUiIntent.ChangeTempo(1)) },
            )
            EditorStepper(
                label = stringResource(R.string.tone_row_clock_division),
                value = state.clockDivisionLabel.ifBlank { "—" },
                enabled = state.available,
                decreaseDescription = stringResource(R.string.tone_row_division_decrease),
                increaseDescription = stringResource(R.string.tone_row_division_increase),
                onDecrease = { onIntent(ToneRowUiIntent.ChangeClockDivision(-1)) },
                onIncrease = { onIntent(ToneRowUiIntent.ChangeClockDivision(1)) },
            )
            EditorStepper(
                label = stringResource(R.string.tone_row_note_duration),
                value = "${state.noteDurationPercent.coerceIn(1, 100)} %",
                enabled = state.available,
                decreaseDescription = stringResource(R.string.tone_row_duration_decrease),
                increaseDescription = stringResource(R.string.tone_row_duration_increase),
                onDecrease = { onIntent(ToneRowUiIntent.ChangeNoteDuration(-5)) },
                onIncrease = { onIntent(ToneRowUiIntent.ChangeNoteDuration(5)) },
            )
        }
    }
}

@Composable
private fun PresetEditor(
    available: Boolean,
    selectedPresetSlot: Int,
    presetSlotCount: Int,
    occupiedPresetSlots: Set<Int>,
    onIntent: (ToneRowUiIntent) -> Unit,
) {
    val safeSlotCount = presetSlotCount.coerceIn(1, 128)
    val selectedSlot = selectedPresetSlot.coerceIn(1, safeSlotCount)
    val selectedPresetIsOccupied = selectedSlot in occupiedPresetSlots
    var deleteArmedSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(selectedSlot, selectedPresetIsOccupied) {
        if (deleteArmedSlot != selectedSlot || !selectedPresetIsOccupied) deleteArmedSlot = null
    }

    EditorStepper(
        label = stringResource(R.string.tone_row_preset_slot),
        value = if (selectedPresetIsOccupied) {
            stringResource(R.string.tone_row_preset_occupied, selectedSlot)
        } else {
            stringResource(R.string.tone_row_preset_empty, selectedSlot)
        },
        enabled = available,
        decreaseEnabled = available && selectedSlot > 1,
        increaseEnabled = available && selectedSlot < safeSlotCount,
        decreaseDescription = stringResource(R.string.tone_row_preset_previous),
        increaseDescription = stringResource(R.string.tone_row_preset_next),
        onDecrease = { onIntent(ToneRowUiIntent.SelectPreset(selectedSlot - 1)) },
        onIncrease = { onIntent(ToneRowUiIntent.SelectPreset(selectedSlot + 1)) },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { onIntent(ToneRowUiIntent.SavePreset) },
            enabled = available,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            shape = ToneRowControlShape,
        ) {
            Text(stringResource(R.string.tone_row_preset_save), maxLines = 1)
        }
        Button(
            onClick = { onIntent(ToneRowUiIntent.RecallPreset) },
            enabled = available && selectedPresetIsOccupied,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            shape = ToneRowControlShape,
        ) {
            Text(stringResource(R.string.tone_row_preset_recall), maxLines = 1)
        }
    }
    OutlinedButton(
        onClick = {
            if (deleteArmedSlot == selectedSlot) {
                onIntent(ToneRowUiIntent.DeletePreset)
                deleteArmedSlot = null
            } else {
                deleteArmedSlot = selectedSlot
            }
        },
        enabled = available && selectedPresetIsOccupied,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = ToneRowControlShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(
            if (deleteArmedSlot == selectedSlot) {
                stringResource(R.string.tone_row_preset_delete_confirm, selectedSlot)
            } else {
                stringResource(R.string.tone_row_preset_delete)
            },
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MovementSequenceEditor(
    available: Boolean,
    movementSequence: List<Int>,
    cursorState: State<ToneRowCursorUiState>,
    onIntent: (ToneRowUiIntent) -> Unit,
) {
    val sequence = movementSequence.ifEmpty { listOf(1) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        sequence.forEachIndexed { index, movement ->
            MovementSequenceChip(
                available = available,
                index = index,
                sequenceSize = sequence.size,
                movement = movement,
                cursorState = cursorState,
                onIntent = onIntent,
            )
        }
    }
    DeleteSequenceStepButton(
        available = available,
        sequenceSize = sequence.size,
        cursorState = cursorState,
        onIntent = onIntent,
    )
}

@Composable
private fun MovementSequenceChip(
    available: Boolean,
    index: Int,
    sequenceSize: Int,
    movement: Int,
    cursorState: State<ToneRowCursorUiState>,
    onIntent: (ToneRowUiIntent) -> Unit,
) {
    val selected by remember(cursorState, index, sequenceSize) {
        derivedStateOf {
            cursorState.value.sequenceCursorIndex?.takeIf { it in 0 until sequenceSize } == index
        }
    }
    FilterChip(
        selected = selected,
        onClick = { onIntent(ToneRowUiIntent.SelectSequenceStep(index)) },
        enabled = available,
        modifier = Modifier
            .heightIn(min = 56.dp)
            .widthIn(min = 56.dp)
            .semantics {
                contentDescription = "${index + 1}/$sequenceSize, ${signedUiValue(movement)}"
                this.selected = selected
            },
        label = {
            Text(
                signedUiValue(movement),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
        },
    )
}

@Composable
private fun DeleteSequenceStepButton(
    available: Boolean,
    sequenceSize: Int,
    cursorState: State<ToneRowCursorUiState>,
    onIntent: (ToneRowUiIntent) -> Unit,
) {
    val cursor = cursorState.value.sequenceCursorIndex?.takeIf { it in 0 until sequenceSize }
    OutlinedButton(
        onClick = { onIntent(ToneRowUiIntent.DeleteSequenceStep(cursor ?: sequenceSize - 1)) },
        enabled = available && sequenceSize > 1,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = ToneRowControlShape,
    ) {
        Text(stringResource(R.string.tone_row_delete_step))
    }
}

@Composable
private fun TransformStepper(
    label: String,
    value: String,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Surface(
        shape = ToneRowControlShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onDecrease,
                enabled = enabled,
                modifier = Modifier.sizeIn(minWidth = 56.dp, minHeight = 56.dp),
            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Column(
                modifier = Modifier.widthIn(min = 92.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            }
            TextButton(
                onClick = onIncrease,
                enabled = enabled,
                modifier = Modifier.sizeIn(minWidth = 56.dp, minHeight = 56.dp),
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Composable
private fun EditorStepper(
    label: String,
    value: String,
    enabled: Boolean,
    decreaseEnabled: Boolean = enabled,
    increaseEnabled: Boolean = enabled,
    decreaseDescription: String,
    increaseDescription: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onDecrease,
            enabled = decreaseEnabled,
            modifier = Modifier
                .sizeIn(minWidth = 56.dp, minHeight = 56.dp)
                .semantics { contentDescription = decreaseDescription },
            shape = ToneRowControlShape,
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        OutlinedButton(
            onClick = onIncrease,
            enabled = increaseEnabled,
            modifier = Modifier
                .sizeIn(minWidth = 56.dp, minHeight = 56.dp)
                .semantics { contentDescription = increaseDescription },
            shape = ToneRowControlShape,
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun ArrangementSectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Black,
    )
}

@Composable
private fun phaseLabel(phase: ToneRowUiPhase): String {
    return when (phase) {
        ToneRowUiPhase.IDLE -> stringResource(R.string.tone_row_phase_idle)
        ToneRowUiPhase.RECORDING -> stringResource(R.string.tone_row_phase_recording)
        ToneRowUiPhase.MANUAL_PLAYBACK -> stringResource(R.string.tone_row_phase_manual)
        ToneRowUiPhase.AUTO_PLAYING -> stringResource(R.string.tone_row_phase_playing)
        ToneRowUiPhase.PAUSED -> stringResource(R.string.tone_row_phase_paused)
    }
}

@Composable
private fun playbackModeLabel(mode: ToneRowUiPlaybackMode): String {
    return when (mode) {
        ToneRowUiPlaybackMode.PRIME -> stringResource(R.string.tone_row_mode_prime)
        ToneRowUiPlaybackMode.RETRO -> stringResource(R.string.tone_row_mode_retro)
        ToneRowUiPlaybackMode.RANDOM -> stringResource(R.string.tone_row_mode_random)
        ToneRowUiPlaybackMode.PENDULUM -> stringResource(R.string.tone_row_mode_pendulum)
    }
}

@Composable
private fun clockSourceLabel(source: ToneRowUiClockSource): String {
    return when (source) {
        ToneRowUiClockSource.INTERNAL -> stringResource(R.string.tone_row_clock_internal)
        ToneRowUiClockSource.MIDI -> stringResource(R.string.tone_row_clock_midi)
    }
}
