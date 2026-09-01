package dev.intervaltablet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intervaltablet.AppUiState
import dev.intervaltablet.R
import dev.intervaltablet.domain.MIDI_MAPPING_DEFAULT_CC_THRESHOLD
import dev.intervaltablet.domain.MidiAction
import dev.intervaltablet.domain.MidiBindingAssignment
import dev.intervaltablet.domain.MidiBindingKey
import dev.intervaltablet.domain.MidiLearnChannelMode
import dev.intervaltablet.domain.MidiMappingCapture
import dev.intervaltablet.domain.MidiMappingEditorAction
import dev.intervaltablet.domain.MidiMappingEditorState
import dev.intervaltablet.midi.MidiConnectionPhase
import kotlin.math.roundToInt

internal const val MidiMappingEditorTestTag: String = "midi-mapping-editor"
internal const val MidiMappingLearnTestTag: String = "midi-mapping-learn"
internal const val MidiMappingSaveTestTag: String = "midi-mapping-save"
internal const val MidiMappingActionSelectorTestTag: String = "midi-mapping-action-selector"
internal const val MidiMappingThresholdTestTag: String = "midi-mapping-threshold"
internal const val MidiMappingConflictTestTag: String = "midi-mapping-conflict"
internal const val MidiMappingReplaceTestTag: String = "midi-mapping-replace"
internal const val MidiMappingCancelTestTag: String = "midi-mapping-cancel"

private val MidiMappingPanelShape = RoundedCornerShape(20.dp)
private val MidiMappingControlShape = RoundedCornerShape(14.dp)

@Immutable
internal data class MidiMappingEditorUiState(
    val editor: MidiMappingEditorState = MidiMappingEditorState.Closed,
    val bindings: List<MidiBindingAssignment> = emptyList(),
    val sourceConnected: Boolean = false,
    val sourceName: String? = null,
    val inputChannel: Int? = null,
    val selectedPresetNumber: Int = 1,
)

internal fun AppUiState.toMidiMappingEditorUiState(): MidiMappingEditorUiState {
    val editing = midiMappingEditor as? MidiMappingEditorState.Editing
    val assignments = editing?.draft?.bindings
        ?.map { (key, action) ->
            MidiBindingAssignment(
                key = key,
                action = action,
                ccThreshold = if (key.kind == MidiBindingKey.Kind.CC) {
                    editing.draft.ccThresholds[key] ?: MIDI_MAPPING_DEFAULT_CC_THRESHOLD
                } else {
                    null
                },
            )
        }
        ?.sortedWith(
            compareBy<MidiBindingAssignment>(
                { it.key.kind.ordinal },
                { it.key.number },
                { it.key.channel ?: -1 },
            ),
        )
        .orEmpty()
    return MidiMappingEditorUiState(
        editor = midiMappingEditor,
        bindings = assignments,
        sourceConnected = hostStarted &&
            midi.sourceConnection.phase == MidiConnectionPhase.OPEN,
        sourceName = midi.sourceConnection.descriptor?.displayName,
        inputChannel = inputChannel,
        selectedPresetNumber = selectedPresetSlot + 1,
    )
}

internal enum class MidiLearnActionKind {
    MOVE,
    CHROMATIC,
    CHROMATIC_SHIFT,
    UNDO_THEN_MOVE,
    OCTAVE,
    HOME,
    UNDO,
    SAME,
    SAME_PITCH,
    RANDOM,
    PANIC,
    TOGGLE_PASS_THROUGH,
    PLAY,
    STOP,
    RECORD,
}

@Immutable
internal data class MidiLearnActionSelection(
    val kind: MidiLearnActionKind = MidiLearnActionKind.MOVE,
    val amount: Int = 1,
    val homeSound: Boolean = true,
) {
    fun toMidiAction(): MidiAction {
        return when (kind) {
            MidiLearnActionKind.MOVE -> MidiAction.Move(amount.coerceIn(-14, 14))
            MidiLearnActionKind.CHROMATIC -> MidiAction.Chromatic(amount.coerceIn(-127, 127))
            MidiLearnActionKind.CHROMATIC_SHIFT ->
                MidiAction.ChromaticShift(amount.coerceIn(-12, 12))
            MidiLearnActionKind.UNDO_THEN_MOVE ->
                MidiAction.UndoThenMove(amount.coerceIn(-14, 14))
            MidiLearnActionKind.OCTAVE -> MidiAction.Octave(amount.coerceIn(-10, 10))
            MidiLearnActionKind.HOME -> MidiAction.Home(homeSound)
            MidiLearnActionKind.UNDO -> MidiAction.Undo
            MidiLearnActionKind.SAME -> MidiAction.Same
            MidiLearnActionKind.SAME_PITCH -> MidiAction.SamePitch
            MidiLearnActionKind.RANDOM -> MidiAction.Random
            MidiLearnActionKind.PANIC -> MidiAction.Panic
            MidiLearnActionKind.TOGGLE_PASS_THROUGH -> MidiAction.TogglePassThrough
            MidiLearnActionKind.PLAY -> MidiAction.Play
            MidiLearnActionKind.STOP -> MidiAction.Stop
            MidiLearnActionKind.RECORD -> MidiAction.Record
        }
    }

    fun select(nextKind: MidiLearnActionKind): MidiLearnActionSelection {
        val defaultAmount = when (nextKind) {
            MidiLearnActionKind.MOVE,
            MidiLearnActionKind.CHROMATIC,
            MidiLearnActionKind.CHROMATIC_SHIFT,
            MidiLearnActionKind.UNDO_THEN_MOVE,
            MidiLearnActionKind.OCTAVE,
            -> 1
            else -> amount
        }
        return copy(kind = nextKind, amount = defaultAmount)
    }
}

internal fun midiLearnActionSelectionFromSavedValues(
    kindName: String,
    amount: Int,
    homeSound: Boolean,
): MidiLearnActionSelection {
    return MidiLearnActionSelection(
        kind = MidiLearnActionKind.entries.firstOrNull { it.name == kindName }
            ?: MidiLearnActionKind.MOVE,
        amount = amount,
        homeSound = homeSound,
    )
}

@Composable
internal fun MidiMappingEditorPanel(
    state: MidiMappingEditorUiState,
    onAction: (MidiMappingEditorAction) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editing = state.editor as? MidiMappingEditorState.Editing ?: return
    var selectedKindName by rememberSaveable(editing.baseline) {
        mutableStateOf(MidiLearnActionKind.MOVE.name)
    }
    var selectedAmount by rememberSaveable(editing.baseline) { mutableIntStateOf(1) }
    var selectedHomeSound by rememberSaveable(editing.baseline) { mutableStateOf(true) }
    val selection = midiLearnActionSelectionFromSavedValues(
        kindName = selectedKindName,
        amount = selectedAmount,
        homeSound = selectedHomeSound,
    )
    val capture = editing.capture
    val updateSelection: (MidiLearnActionSelection) -> Unit = { next ->
        selectedKindName = next.kind.name
        selectedAmount = next.amount
        selectedHomeSound = next.homeSound
        if (capture is MidiMappingCapture.Captured) {
            onAction(MidiMappingEditorAction.SetCandidateAction(next.toMidiAction()))
        }
    }

    Surface(
        modifier = modifier.testTag(MidiMappingEditorTestTag),
        shape = MidiMappingPanelShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
        tonalElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "editor-header") {
                    MidiMappingEditorHeader(
                        selectedPresetNumber = state.selectedPresetNumber,
                        hasChanges = editing.hasChanges,
                        onCancel = { onAction(MidiMappingEditorAction.Cancel) },
                    )
                }
                item(key = "source-status") {
                    if (!state.sourceConnected) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MidiMappingControlShape,
                        ) {
                            Text(
                                stringResource(R.string.midi_learn_source_required),
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        Text(
                            stringResource(
                                R.string.midi_learn_source_connected,
                                state.sourceName ?: stringResource(R.string.midi_input),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "input-filter") {
                    Text(
                        if (state.inputChannel == null) {
                            stringResource(R.string.midi_learn_global_filter_omni)
                        } else {
                            stringResource(
                                R.string.midi_learn_global_filter_channel,
                                state.inputChannel + 1,
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "action-heading") {
                    Text(
                        stringResource(R.string.midi_learn_action_target),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item(key = "action-editor") {
                    MidiLearnActionEditor(
                        selection = selection,
                        enabled = capture !is MidiMappingCapture.Armed,
                        onSelectionChange = updateSelection,
                    )
                }
                item(key = "capture-editor") {
                    MidiCaptureEditor(
                        capture = capture,
                        sourceConnected = state.sourceConnected,
                        onAction = onAction,
                        selectedAction = selection.toMidiAction(),
                    )
                }
                item(key = "bindings-heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                pluralStringResource(
                                    R.plurals.midi_mapping_binding_count,
                                    state.bindings.size,
                                    state.bindings.size,
                                ),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            TextButton(
                                onClick = { onAction(MidiMappingEditorAction.ResetDraft) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.midi_mapping_reset_draft))
                            }
                        }
                    }
                }
                if (state.bindings.isEmpty()) {
                    item(key = "bindings-empty") {
                        Text(
                            stringResource(R.string.midi_mapping_empty),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(
                        items = state.bindings,
                        key = { assignment ->
                            val key = assignment.key
                            "${key.kind}:${key.number}:${key.channel}"
                        },
                    ) { assignment ->
                        MidiBindingRow(
                            assignment = assignment,
                            onDelete = {
                                onAction(MidiMappingEditorAction.DeleteBinding(assignment.key))
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onAction(MidiMappingEditorAction.Cancel) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .testTag(MidiMappingCancelTestTag),
                    shape = MidiMappingControlShape,
                ) {
                    Text(stringResource(R.string.midi_mapping_cancel))
                }
                Button(
                    onClick = onSave,
                    enabled = capture == MidiMappingCapture.Idle,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .testTag(MidiMappingSaveTestTag),
                    shape = MidiMappingControlShape,
                ) {
                    Text(stringResource(R.string.midi_mapping_save))
                }
            }
        }
    }
}

@Composable
private fun MidiMappingEditorHeader(
    selectedPresetNumber: Int,
    hasChanges: Boolean,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.midi_mapping_editor_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                stringResource(R.string.midi_mapping_editor_session, selectedPresetNumber),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.midi_mapping_preset_resave_warning,
                    selectedPresetNumber,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasChanges) {
                Text(
                    stringResource(R.string.midi_mapping_unsaved),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.performance_close_console))
        }
    }
}

@Composable
private fun MidiLearnActionEditor(
    selection: MidiLearnActionSelection,
    enabled: Boolean,
    onSelectionChange: (MidiLearnActionSelection) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(MidiMappingActionSelectorTestTag),
            shape = MidiMappingControlShape,
        ) {
            Text(
                midiLearnActionKindLabel(selection.kind),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(R.string.midi_mapping_choose_action))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MidiLearnActionKind.entries.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(midiLearnActionKindLabel(kind)) },
                    onClick = {
                        expanded = false
                        onSelectionChange(selection.select(kind))
                    },
                )
            }
        }
    }
    MidiLearnActionParameter(
        selection = selection,
        enabled = enabled,
        onSelectionChange = onSelectionChange,
    )
}

@Composable
private fun MidiLearnActionParameter(
    selection: MidiLearnActionSelection,
    enabled: Boolean,
    onSelectionChange: (MidiLearnActionSelection) -> Unit,
) {
    val range = when (selection.kind) {
        MidiLearnActionKind.MOVE,
        MidiLearnActionKind.UNDO_THEN_MOVE,
        -> -14..14
        MidiLearnActionKind.CHROMATIC -> -127..127
        MidiLearnActionKind.CHROMATIC_SHIFT -> -12..12
        MidiLearnActionKind.OCTAVE -> -10..10
        else -> null
    }
    if (range != null) {
        val amount = selection.amount.coerceIn(range)
        val decrease = stringResource(R.string.configuration_decrease)
        val increase = stringResource(R.string.configuration_increase)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.midi_mapping_action_amount, amount),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = { onSelectionChange(selection.copy(amount = amount - 1)) },
                enabled = enabled && amount > range.first,
                modifier = Modifier
                    .sizeIn(minWidth = 56.dp, minHeight = 56.dp)
                    .semantics { contentDescription = decrease },
                shape = MidiMappingControlShape,
            ) {
                Text("-")
            }
            OutlinedButton(
                onClick = { onSelectionChange(selection.copy(amount = amount + 1)) },
                enabled = enabled && amount < range.last,
                modifier = Modifier
                    .sizeIn(minWidth = 56.dp, minHeight = 56.dp)
                    .semantics { contentDescription = increase },
                shape = MidiMappingControlShape,
            ) {
                Text("+")
            }
        }
    } else if (selection.kind == MidiLearnActionKind.HOME) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.midi_mapping_home_sound),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.midi_mapping_home_sound_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = selection.homeSound,
                enabled = enabled,
                onCheckedChange = { onSelectionChange(selection.copy(homeSound = it)) },
            )
        }
    }
}

@Composable
private fun MidiCaptureEditor(
    capture: MidiMappingCapture,
    sourceConnected: Boolean,
    selectedAction: MidiAction,
    onAction: (MidiMappingEditorAction) -> Unit,
) {
    when (capture) {
        MidiMappingCapture.Idle -> Button(
            onClick = { onAction(MidiMappingEditorAction.Arm(selectedAction)) },
            enabled = sourceConnected,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(MidiMappingLearnTestTag),
            shape = MidiMappingControlShape,
        ) {
            Text(stringResource(R.string.midi_learn_start))
        }
        is MidiMappingCapture.Armed -> Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MidiMappingControlShape,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.midi_learn_listening),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.midi_learn_listening_detail),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(
                    onClick = { onAction(MidiMappingEditorAction.CancelCapture) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.midi_learn_cancel_capture))
                }
            }
        }
        is MidiMappingCapture.Captured -> MidiCandidateEditor(
            candidate = capture.candidate,
            onAction = onAction,
        )
    }
}

@Composable
private fun MidiCandidateEditor(
    candidate: dev.intervaltablet.domain.MidiMappingCandidate,
    onAction: (MidiMappingEditorAction) -> Unit,
) {
    val binding = candidate.binding
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MidiMappingControlShape,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(
                    R.string.midi_learn_candidate,
                    midiBindingInputLabel(binding.key),
                    midiActionLabel(binding.action),
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(
                    R.string.midi_learn_received_channel,
                    candidate.receivedChannel + 1,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = candidate.channelMode == MidiLearnChannelMode.RECEIVED,
                    onClick = {
                        onAction(
                            MidiMappingEditorAction.SetCandidateChannelMode(
                                MidiLearnChannelMode.RECEIVED,
                            ),
                        )
                    },
                    label = {
                        Text(
                            stringResource(
                                R.string.midi_learn_channel_exact,
                                candidate.receivedChannel + 1,
                            ),
                        )
                    },
                )
                FilterChip(
                    selected = candidate.channelMode == MidiLearnChannelMode.OMNI,
                    onClick = {
                        onAction(
                            MidiMappingEditorAction.SetCandidateChannelMode(
                                MidiLearnChannelMode.OMNI,
                            ),
                        )
                    },
                    label = { Text(stringResource(R.string.midi_channel_omni)) },
                )
            }
            if (binding.key.kind == MidiBindingKey.Kind.CC) {
                MidiCandidateThreshold(
                    threshold = binding.ccThreshold ?: MIDI_MAPPING_DEFAULT_CC_THRESHOLD,
                    onThresholdChange = {
                        onAction(MidiMappingEditorAction.SetCandidateThreshold(it))
                    },
                )
            }
            MidiCandidateWarnings(candidate)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { onAction(MidiMappingEditorAction.CancelCapture) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.midi_learn_cancel_capture))
                }
                Button(
                    onClick = {
                        onAction(
                            if (candidate.conflict == null) {
                                MidiMappingEditorAction.AddCandidate
                            } else {
                                MidiMappingEditorAction.ReplaceCandidate
                            },
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .then(
                            if (candidate.conflict != null) {
                                Modifier.testTag(MidiMappingReplaceTestTag)
                            } else {
                                Modifier
                            },
                        ),
                    shape = MidiMappingControlShape,
                ) {
                    Text(
                        stringResource(
                            if (candidate.conflict == null) {
                                R.string.midi_mapping_add
                            } else {
                                R.string.midi_mapping_replace
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MidiCandidateThreshold(
    threshold: Int,
    onThresholdChange: (Int) -> Unit,
) {
    val description = stringResource(R.string.midi_mapping_cc_threshold)
    Column {
        Text(
            stringResource(R.string.midi_mapping_cc_threshold_value, threshold),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Slider(
            value = threshold.toFloat(),
            onValueChange = { onThresholdChange(it.roundToInt().coerceIn(1, 127)) },
            valueRange = 1f..127f,
            steps = 125,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MidiMappingThresholdTestTag)
                .semantics {
                    contentDescription = description
                },
        )
    }
}

@Composable
private fun MidiCandidateWarnings(
    candidate: dev.intervaltablet.domain.MidiMappingCandidate,
) {
    candidate.conflict?.let { conflict ->
        Text(
            stringResource(
                R.string.midi_mapping_conflict,
                midiActionLabel(conflict.existing.action),
            ),
            modifier = Modifier.testTag(MidiMappingConflictTestTag),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
    }
    candidate.overlap.omniFallback?.let {
        Text(
            stringResource(R.string.midi_mapping_exact_over_omni),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    val exactCount = candidate.overlap.exactOverrides.size
    if (exactCount > 0) {
        Text(
            pluralStringResource(
                R.plurals.midi_mapping_omni_under_exact,
                exactCount,
                exactCount,
            ),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MidiBindingRow(
    assignment: MidiBindingAssignment,
    onDelete: () -> Unit,
) {
    val channelLabel = midiBindingChannelLabel(assignment.key)
    val actionLabel = midiActionLabel(assignment.action)
    val thresholdLabel = assignment.ccThreshold?.let { threshold ->
        stringResource(R.string.midi_mapping_cc_threshold_short, threshold)
    }
    val detail = listOfNotNull(channelLabel, actionLabel, thresholdLabel).joinToString(" - ")
    val deleteDescription = stringResource(
        R.string.midi_mapping_delete_description,
        midiBindingInputLabel(assignment.key),
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MidiMappingControlShape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    midiBindingInputLabel(assignment.key),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = deleteDescription },
            ) {
                Text(stringResource(R.string.midi_mapping_delete))
            }
        }
    }
}

@Composable
private fun midiBindingInputLabel(key: MidiBindingKey): String {
    return when (key.kind) {
        MidiBindingKey.Kind.NOTE -> stringResource(R.string.midi_mapping_note, key.number)
        MidiBindingKey.Kind.CC -> stringResource(R.string.midi_mapping_cc, key.number)
    }
}

@Composable
private fun midiBindingChannelLabel(key: MidiBindingKey): String {
    return key.channel?.let { channel ->
        stringResource(R.string.midi_channel_number, channel + 1)
    } ?: stringResource(R.string.midi_channel_omni)
}

@Composable
private fun midiLearnActionKindLabel(kind: MidiLearnActionKind): String {
    return when (kind) {
        MidiLearnActionKind.MOVE -> stringResource(R.string.midi_action_move)
        MidiLearnActionKind.CHROMATIC -> stringResource(R.string.midi_action_chromatic)
        MidiLearnActionKind.CHROMATIC_SHIFT -> stringResource(R.string.midi_action_chromatic_shift)
        MidiLearnActionKind.UNDO_THEN_MOVE -> stringResource(R.string.midi_action_undo_then_move)
        MidiLearnActionKind.OCTAVE -> stringResource(R.string.midi_action_octave)
        MidiLearnActionKind.HOME -> stringResource(R.string.midi_action_home)
        MidiLearnActionKind.UNDO -> stringResource(R.string.midi_action_undo)
        MidiLearnActionKind.SAME -> stringResource(R.string.midi_action_same)
        MidiLearnActionKind.SAME_PITCH -> stringResource(R.string.midi_action_same_pitch)
        MidiLearnActionKind.RANDOM -> stringResource(R.string.midi_action_random)
        MidiLearnActionKind.PANIC -> stringResource(R.string.midi_action_panic)
        MidiLearnActionKind.TOGGLE_PASS_THROUGH ->
            stringResource(R.string.midi_action_toggle_pass_through)
        MidiLearnActionKind.PLAY -> stringResource(R.string.midi_action_play)
        MidiLearnActionKind.STOP -> stringResource(R.string.midi_action_stop)
        MidiLearnActionKind.RECORD -> stringResource(R.string.midi_action_record)
    }
}

@Composable
internal fun midiActionLabel(action: MidiAction): String {
    return when (action) {
        is MidiAction.Move -> stringResource(R.string.midi_action_move_value, action.steps)
        is MidiAction.Chromatic ->
            stringResource(R.string.midi_action_chromatic_value, action.semitones)
        is MidiAction.ChromaticShift ->
            stringResource(R.string.midi_action_chromatic_shift_value, action.semitones)
        is MidiAction.UndoThenMove ->
            stringResource(R.string.midi_action_undo_then_move_value, action.steps)
        MidiAction.Undo -> stringResource(R.string.midi_action_undo)
        MidiAction.Same -> stringResource(R.string.midi_action_same)
        MidiAction.SamePitch -> stringResource(R.string.midi_action_same_pitch)
        MidiAction.Random -> stringResource(R.string.midi_action_random)
        is MidiAction.Home -> stringResource(
            if (action.sound) R.string.midi_action_home_sound else R.string.midi_action_home_silent,
        )
        is MidiAction.Octave -> stringResource(R.string.midi_action_octave_value, action.octaves)
        MidiAction.Panic -> stringResource(R.string.midi_action_panic)
        MidiAction.TogglePassThrough -> stringResource(R.string.midi_action_toggle_pass_through)
        MidiAction.Play -> stringResource(R.string.midi_action_play)
        MidiAction.Stop -> stringResource(R.string.midi_action_stop)
        MidiAction.Record -> stringResource(R.string.midi_action_record)
    }
}
