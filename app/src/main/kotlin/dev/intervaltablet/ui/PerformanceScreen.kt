package dev.intervaltablet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.intervaltablet.AppUiState
import dev.intervaltablet.R
import dev.intervaltablet.audio.AudioDiagnostics
import dev.intervaltablet.navigationAnchor
import dev.intervaltablet.domain.ArpeggiatorConfig
import dev.intervaltablet.domain.ArpeggioOrder
import dev.intervaltablet.domain.ChordDefinition
import dev.intervaltablet.domain.ChordLibrary
import dev.intervaltablet.domain.InstrumentConfig
import dev.intervaltablet.domain.MidiMappingEditorAction
import dev.intervaltablet.domain.MidiNoteRange
import dev.intervaltablet.domain.PadArticulation
import dev.intervaltablet.domain.PassThroughMode
import dev.intervaltablet.domain.PitchGrid
import dev.intervaltablet.domain.PitchMoveBoundary
import dev.intervaltablet.domain.ScaleDefinition
import dev.intervaltablet.domain.ScaleLibrary
import dev.intervaltablet.domain.SynthParameter
import dev.intervaltablet.domain.SynthPatch
import dev.intervaltablet.domain.SynthLfoDestination
import dev.intervaltablet.domain.SynthPresetLibrary
import dev.intervaltablet.domain.midiNoteName
import dev.intervaltablet.midi.MidiConnectionPhase
import dev.intervaltablet.midi.MidiConnectionState
import dev.intervaltablet.midi.MidiPortDescriptor
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val StageShape = RoundedCornerShape(20.dp)
private val ControlShape = RoundedCornerShape(14.dp)
internal val PerformanceIntervalSteps: List<Int> = listOf(2, 3, 4, -1, 0, 1, -4, -3, -2)
private val IntervalRows: List<List<Int>> = PerformanceIntervalSteps.chunked(3)
internal const val SynthPanelOpenTestTag: String = "synth-panel:open"
internal const val SynthPanelTestTag: String = "synth-panel"
internal const val ForceToScaleTestTag: String = "force-to-scale"
internal const val HarmonyHandPaneTestTag: String = "two-hand:harmony-left"
internal const val IntervalHandPaneTestTag: String = "two-hand:intervals-right"
internal const val SynthCutoffUiMaximumHz: Float = 20_000f
internal fun synthSliderTestTag(key: String): String = "synth-slider:$key"
internal fun scaleChipTestTag(scaleId: String): String = "scale-chip:$scaleId"
internal fun chordChipTestTag(chordId: String): String = "chord-chip:$chordId"

private enum class PerformancePage {
    INTERVAL,
    MIDI,
    SYNTH,
    ARPEGGIATOR,
}

@androidx.compose.runtime.Immutable
internal data class IntervalPadPreview(
    val target: String,
    val boundary: PitchMoveBoundary,
)

internal fun buildIntervalPadPreviews(
    config: InstrumentConfig,
    currentNote: Int,
    lastExternalNote: Int?,
    steps: Iterable<Int>,
): Map<Int, IntervalPadPreview> {
    return buildIntervalPadPreviews(
        grid = config.grid(),
        currentNote = currentNote,
        lastExternalNote = lastExternalNote,
        steps = steps,
    )
}

internal fun buildIntervalPadPreviews(
    grid: PitchGrid,
    currentNote: Int,
    lastExternalNote: Int?,
    steps: Iterable<Int>,
): Map<Int, IntervalPadPreview> {
    return steps.associateWith { interval ->
        val preview = grid.previewMove(
            navigationAnchor(currentNote, lastExternalNote, interval),
            interval,
        )
        IntervalPadPreview(
            target = midiNoteName(preview.note),
            boundary = preview.boundary,
        )
    }
}

@Composable
fun PerformanceScreen(
    state: AppUiState,
    toneRowState: ToneRowUiState = ToneRowUiState(),
    onToneRowIntent: (ToneRowUiIntent) -> Unit = {},
    onSetPadArticulation: (PadArticulation) -> Unit = {},
    onStrumTone: (toneIndex: Int, velocity: Int) -> Unit = { _, _ -> },
    onIntervalDown: (pointerId: Long, steps: Int) -> Unit,
    onIntervalUp: (pointerId: Long) -> Unit,
    onIntervalOneShot: (steps: Int) -> Unit,
    onUndo: () -> Unit,
    onHome: () -> Unit,
    onPanic: () -> Unit,
    onSetScale: (ScaleDefinition) -> Unit,
    onSetRoot: (Int) -> Unit,
    onSetChord: (ChordDefinition) -> Unit,
    onSetForceToScale: (Boolean) -> Unit = {},
    onSetArpeggiatorConfig: (ArpeggiatorConfig) -> Unit = {},
    onSetTempo: (Int) -> Unit = {},
    onSetClockDivision: (Int) -> Unit = {},
    onSetArpeggioGate: (Int) -> Unit = {},
    onSetTimeSignature: (Int, Int) -> Unit = { _, _ -> },
    onSetRange: (MidiNoteRange) -> Unit,
    onSetWrap: (Boolean) -> Unit,
    onSetInputChannel: (Int?) -> Unit,
    onSetOutputChannel: (Int) -> Unit,
    onSetMode: (PassThroughMode) -> Unit,
    onSelectSource: (MidiPortDescriptor?) -> Unit,
    onSelectDestination: (MidiPortDescriptor?) -> Unit,
    onResetMidiMapping: () -> Unit,
    onToggleAudio: () -> Unit,
    onTogglePerformanceLock: () -> Unit,
    onDismissStatus: () -> Unit,
    onSynthPatchPreview: (SynthPatch) -> Unit = {},
    onSynthPatchChangeFinished: (SynthPatch) -> Unit = {},
    onOpenMidiMappingEditor: () -> Unit = {},
    onMidiMappingEditorAction: (MidiMappingEditorAction) -> Unit = {},
    onSaveMidiMappingEditor: () -> Unit = {},
) {
    val appState = rememberUpdatedState(state)
    val providedToneRowState = rememberUpdatedState(toneRowState)
    val providedToneRowContentState = remember(providedToneRowState) {
        derivedStateOf(structuralEqualityPolicy()) {
            providedToneRowState.value.toToneRowContentUiState()
        }
    }
    val providedToneRowCursorState = remember(providedToneRowState) {
        derivedStateOf(structuralEqualityPolicy()) {
            providedToneRowState.value.toToneRowCursorUiState()
        }
    }
    val projections = rememberPerformanceUiProjections(appState)
    ProjectedPerformanceScreen(
        projections = projections,
        toneRowContentState = providedToneRowContentState,
        toneRowCursorState = providedToneRowCursorState,
        onToneRowIntent = onToneRowIntent,
        onSetPadArticulation = onSetPadArticulation,
        onStrumTone = onStrumTone,
        onIntervalDown = onIntervalDown,
        onIntervalUp = onIntervalUp,
        onIntervalOneShot = onIntervalOneShot,
        onUndo = onUndo,
        onHome = onHome,
        onPanic = onPanic,
        onSetScale = onSetScale,
        onSetRoot = onSetRoot,
        onSetChord = onSetChord,
        onSetForceToScale = onSetForceToScale,
        onSetArpeggiatorConfig = onSetArpeggiatorConfig,
        onSetTempo = onSetTempo,
        onSetClockDivision = onSetClockDivision,
        onSetArpeggioGate = onSetArpeggioGate,
        onSetTimeSignature = onSetTimeSignature,
        onSetRange = onSetRange,
        onSetWrap = onSetWrap,
        onSetInputChannel = onSetInputChannel,
        onSetOutputChannel = onSetOutputChannel,
        onSetMode = onSetMode,
        onSelectSource = onSelectSource,
        onSelectDestination = onSelectDestination,
        onResetMidiMapping = onResetMidiMapping,
        onToggleAudio = onToggleAudio,
        onTogglePerformanceLock = onTogglePerformanceLock,
        onDismissStatus = onDismissStatus,
        onSynthPatchPreview = onSynthPatchPreview,
        onSynthPatchChangeFinished = onSynthPatchChangeFinished,
        onOpenMidiMappingEditor = onOpenMidiMappingEditor,
        onMidiMappingEditorAction = onMidiMappingEditorAction,
        onSaveMidiMappingEditor = onSaveMidiMappingEditor,
    )
}

@Composable
internal fun ProjectedPerformanceScreen(
    projections: PerformanceUiProjections,
    toneRowContentState: State<ToneRowContentUiState> = projections.toneRowContent,
    toneRowCursorState: State<ToneRowCursorUiState> = projections.toneRowCursor,
    onToneRowIntent: (ToneRowUiIntent) -> Unit,
    onSetPadArticulation: (PadArticulation) -> Unit,
    onStrumTone: (toneIndex: Int, velocity: Int) -> Unit,
    onIntervalDown: (pointerId: Long, steps: Int) -> Unit,
    onIntervalUp: (pointerId: Long) -> Unit,
    onIntervalOneShot: (steps: Int) -> Unit,
    onUndo: () -> Unit,
    onHome: () -> Unit,
    onPanic: () -> Unit,
    onSetScale: (ScaleDefinition) -> Unit,
    onSetRoot: (Int) -> Unit,
    onSetChord: (ChordDefinition) -> Unit,
    onSetForceToScale: (Boolean) -> Unit = {},
    onSetArpeggiatorConfig: (ArpeggiatorConfig) -> Unit = {},
    onSetTempo: (Int) -> Unit = {},
    onSetClockDivision: (Int) -> Unit = {},
    onSetArpeggioGate: (Int) -> Unit = {},
    onSetTimeSignature: (Int, Int) -> Unit = { _, _ -> },
    onSetRange: (MidiNoteRange) -> Unit,
    onSetWrap: (Boolean) -> Unit,
    onSetInputChannel: (Int?) -> Unit,
    onSetOutputChannel: (Int) -> Unit,
    onSetMode: (PassThroughMode) -> Unit,
    onSelectSource: (MidiPortDescriptor?) -> Unit,
    onSelectDestination: (MidiPortDescriptor?) -> Unit,
    onResetMidiMapping: () -> Unit,
    onToggleAudio: () -> Unit,
    onTogglePerformanceLock: () -> Unit,
    onDismissStatus: () -> Unit,
    onSynthPatchPreview: (SynthPatch) -> Unit = {},
    onSynthPatchChangeFinished: (SynthPatch) -> Unit = {},
    onOpenMidiMappingEditor: () -> Unit = {},
    onMidiMappingEditorAction: (MidiMappingEditorAction) -> Unit = {},
    onSaveMidiMappingEditor: () -> Unit = {},
) {
    var selectedPage by rememberSaveable { mutableStateOf(PerformancePage.INTERVAL) }
    var toneRowArrangementOpen by rememberSaveable { mutableStateOf(false) }
    PerformanceLockObserver(projections.lock) {
        selectedPage = PerformancePage.INTERVAL
        toneRowArrangementOpen = false
    }
    val backgroundColor = MaterialTheme.colorScheme.background
    val backgroundEndColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val stageBackground = remember(backgroundColor, backgroundEndColor) {
        Brush.verticalGradient(listOf(backgroundColor, backgroundEndColor))
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
    ) { scaffoldPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .background(stageBackground),
        ) {
            val compact = maxWidth < 1_040.dp || maxHeight < 640.dp
            val portrait = maxHeight > maxWidth
            val availableWidth = maxWidth
            val outerPadding = if (compact) 6.dp else 10.dp
            val sectionGap = if (compact) 6.dp else 9.dp
            Column(
                modifier = Modifier.fillMaxSize().padding(outerPadding),
                verticalArrangement = Arrangement.spacedBy(sectionGap),
            ) {
                UnifiedTopBar(
                    pitch = projections.pitch.value,
                    header = projections.header.value,
                    utility = projections.utility.value,
                    controls = projections.controls.value,
                    selectedPage = selectedPage,
                    compact = compact,
                    onSelectPage = { page ->
                        if (
                            page == PerformancePage.INTERVAL ||
                            !projections.lock.value.locked
                        ) {
                            selectedPage = page
                            toneRowArrangementOpen = false
                        }
                    },
                    onHome = onHome,
                    onUndo = onUndo,
                    onPanic = onPanic,
                    onToggleAudio = onToggleAudio,
                    onSetTempo = onSetTempo,
                    onSetTimeSignature = onSetTimeSignature,
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedPage) {
                        PerformancePage.INTERVAL -> Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(sectionGap),
                        ) {
                            V24PerformanceStrip(
                                toneRow = toneRowContentState.value,
                                articulation = projections.articulation.value.articulation,
                                performanceLock = projections.lock.value.locked,
                                onIntent = onToneRowIntent,
                                onSetArticulation = onSetPadArticulation,
                                onOpenArrangement = { toneRowArrangementOpen = true },
                            )
                            V24IntervalStage(
                                projections = projections,
                                portrait = portrait,
                                compact = compact,
                                sectionGap = sectionGap,
                                onSetScale = onSetScale,
                                onSetChord = onSetChord,
                                onSetForceToScale = onSetForceToScale,
                                onSetPadArticulation = onSetPadArticulation,
                                onStrumTone = onStrumTone,
                                onIntervalDown = onIntervalDown,
                                onIntervalUp = onIntervalUp,
                                onIntervalOneShot = onIntervalOneShot,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        }
                        PerformancePage.MIDI -> MidiConsole(
                            state = projections.console.value,
                            onClose = { selectedPage = PerformancePage.INTERVAL },
                            onSetScale = onSetScale,
                            onSetRoot = onSetRoot,
                            onSetChord = onSetChord,
                            onSetRange = onSetRange,
                            onSetWrap = onSetWrap,
                            onSetInputChannel = onSetInputChannel,
                            onSetOutputChannel = onSetOutputChannel,
                            onSetMode = onSetMode,
                            onSelectSource = onSelectSource,
                            onSelectDestination = onSelectDestination,
                            onResetMidiMapping = onResetMidiMapping,
                            onOpenMidiMappingEditor = onOpenMidiMappingEditor,
                            onTogglePerformanceLock = onTogglePerformanceLock,
                            modifier = Modifier.fillMaxSize(),
                        )
                        PerformancePage.SYNTH -> SynthPanel(
                            state = projections.synth.value,
                            compact = compact,
                            onClose = { selectedPage = PerformancePage.INTERVAL },
                            onPatchPreview = onSynthPatchPreview,
                            onPatchChangeFinished = onSynthPatchChangeFinished,
                            modifier = Modifier.fillMaxSize(),
                        )
                        PerformancePage.ARPEGGIATOR -> ArpeggiatorPanel(
                            state = projections.arpeggiator.value,
                            onClose = { selectedPage = PerformancePage.INTERVAL },
                            onSetConfig = onSetArpeggiatorConfig,
                            onSetTempo = onSetTempo,
                            onSetDivision = onSetClockDivision,
                            onSetGate = onSetArpeggioGate,
                            onSetTimeSignature = onSetTimeSignature,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    PerformanceOverlay(
                        consoleState = projections.console,
                        midiMappingEditorState = projections.midiMappingEditor,
                        synthState = projections.synth,
                        toneRowContentState = toneRowContentState,
                        toneRowCursorState = toneRowCursorState,
                        lockState = projections.lock,
                        arrangementOpen = toneRowArrangementOpen,
                        consoleOpen = false,
                        synthOpen = false,
                        compact = compact,
                        availableWidth = availableWidth,
                        onCloseArrangement = { toneRowArrangementOpen = false },
                        onCloseConsole = { selectedPage = PerformancePage.INTERVAL },
                        onCloseSynth = { selectedPage = PerformancePage.INTERVAL },
                        onToneRowIntent = onToneRowIntent,
                        onSetScale = onSetScale,
                        onSetRoot = onSetRoot,
                        onSetChord = onSetChord,
                        onSetRange = onSetRange,
                        onSetWrap = onSetWrap,
                        onSetInputChannel = onSetInputChannel,
                        onSetOutputChannel = onSetOutputChannel,
                        onSetMode = onSetMode,
                        onSelectSource = onSelectSource,
                        onSelectDestination = onSelectDestination,
                        onResetMidiMapping = onResetMidiMapping,
                        onOpenMidiMappingEditor = onOpenMidiMappingEditor,
                        onMidiMappingEditorAction = onMidiMappingEditorAction,
                        onSaveMidiMappingEditor = onSaveMidiMappingEditor,
                        onTogglePerformanceLock = onTogglePerformanceLock,
                        onSynthPatchPreview = onSynthPatchPreview,
                        onSynthPatchChangeFinished = onSynthPatchChangeFinished,
                    )
                }
                ProjectedStatusBanner(state = projections.status, onDismiss = onDismissStatus)
            }
        }
    }
}

@Composable
private fun UnifiedTopBar(
    pitch: PerformancePitchUiState,
    header: PerformanceHeaderUiState,
    utility: PerformanceUtilityUiState,
    controls: PerformanceControlsUiState,
    selectedPage: PerformancePage,
    compact: Boolean,
    onSelectPage: (PerformancePage) -> Unit,
    onHome: () -> Unit,
    onUndo: () -> Unit,
    onPanic: () -> Unit,
    onToggleAudio: () -> Unit,
    onSetTempo: (Int) -> Unit,
    onSetTimeSignature: (Int, Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = StageShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${pitch.currentNoteName} · ${header.rootName} ${header.scaleName}",
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        header.chordName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                TextButton(
                    onClick = { onSetTempo((header.tempoBpm - 1).coerceAtLeast(20)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("−") }
                Text(
                    "${header.tempoBpm} BPM",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = { onSetTempo((header.tempoBpm + 1).coerceAtMost(300)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("+") }
                OutlinedButton(
                    onClick = {
                        val next = if (header.beatsPerBar == 12) 1 else header.beatsPerBar + 1
                        onSetTimeSignature(next, header.beatUnit)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = ControlShape,
                ) {
                    Text("${header.beatsPerBar}/${header.beatUnit}")
                }
                Button(
                    onClick = onToggleAudio,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = ControlShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (header.audioMonitorEnabled) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                    ),
                ) {
                    Text(if (header.audioMonitorEnabled) "Mute" else "Son")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(
                    onClick = onHome,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = ControlShape,
                    contentPadding = PaddingValues(horizontal = 3.dp),
                ) { Text("Home", maxLines = 1) }
                OutlinedButton(
                    onClick = onUndo,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = ControlShape,
                    contentPadding = PaddingValues(horizontal = 3.dp),
                ) { Text(if (utility.restartMode) "Restart" else "Undo", maxLines = 1) }
                Button(
                    onClick = onPanic,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = ControlShape,
                    contentPadding = PaddingValues(horizontal = 3.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Panic", maxLines = 1) }
                PerformancePage.entries.forEach { page ->
                    val label = when (page) {
                        PerformancePage.INTERVAL -> "Interval"
                        PerformancePage.MIDI -> "MIDI"
                        PerformancePage.SYNTH -> "Synthé"
                        PerformancePage.ARPEGGIATOR -> "Arp"
                    }
                    FilterChip(
                        selected = selectedPage == page,
                        onClick = { onSelectPage(page) },
                        enabled = page == PerformancePage.INTERVAL ||
                            controls.settingsLoaded && !controls.performanceLock,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .then(
                                if (page == PerformancePage.SYNTH) {
                                    Modifier.testTag(SynthPanelOpenTestTag)
                                } else {
                                    Modifier
                                },
                            ),
                        label = {
                            Text(
                                label,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun V24PerformanceStrip(
    toneRow: ToneRowContentUiState,
    articulation: PadArticulation,
    performanceLock: Boolean,
    onIntent: (ToneRowUiIntent) -> Unit,
    onSetArticulation: (PadArticulation) -> Unit,
    onOpenArrangement: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = ControlShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val isRunning = toneRow.phase == ToneRowUiPhase.AUTO_PLAYING
            CompactStripChip(
                label = "●",
                description = stringResource(R.string.tone_row_record_description),
                selected = toneRow.phase == ToneRowUiPhase.RECORDING,
                enabled = toneRow.available && !isRunning,
                onClick = { onIntent(ToneRowUiIntent.Record) },
                modifier = Modifier.weight(1f),
            )
            CompactStripChip(
                label = if (isRunning) "Ⅱ" else "▶",
                description = stringResource(
                    if (isRunning) R.string.tone_row_pause_description else R.string.tone_row_play_description,
                ),
                selected = isRunning,
                enabled = toneRow.available && toneRow.row.isNotEmpty(),
                onClick = { onIntent(ToneRowUiIntent.PlayPause) },
                modifier = Modifier.weight(1f),
            )
            CompactStripChip(
                label = "■",
                description = stringResource(R.string.tone_row_stop_description),
                selected = false,
                enabled = toneRow.available,
                onClick = { onIntent(ToneRowUiIntent.Stop) },
                modifier = Modifier.weight(1f),
            )
            PadArticulation.entries.forEach { option ->
                CompactStripChip(
                    label = when (option) {
                        PadArticulation.ARPEGGIATED -> "Arp"
                        PadArticulation.STACKED -> "Acc"
                        PadArticulation.MUTED -> "Muet"
                    },
                    description = articulationDescription(option),
                    selected = articulation == option,
                    enabled = toneRow.available,
                    onClick = { onSetArticulation(option) },
                    modifier = Modifier.weight(1f).testTag(articulationModeTestTag(option)),
                )
            }
            CompactStripChip(
                label = "Régl.",
                description = stringResource(R.string.tone_row_arrangement_description),
                selected = false,
                enabled = toneRow.available && !performanceLock,
                onClick = onOpenArrangement,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CompactStripChip(
    label: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = description },
        label = {
            Text(
                label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun V24IntervalStage(
    projections: PerformanceUiProjections,
    portrait: Boolean,
    compact: Boolean,
    sectionGap: Dp,
    onSetScale: (ScaleDefinition) -> Unit,
    onSetChord: (ChordDefinition) -> Unit,
    onSetForceToScale: (Boolean) -> Unit,
    onSetPadArticulation: (PadArticulation) -> Unit,
    onStrumTone: (Int, Int) -> Unit,
    onIntervalDown: (Long, Int) -> Unit,
    onIntervalUp: (Long) -> Unit,
    onIntervalOneShot: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        StageBackdrop(Modifier.fillMaxSize())
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(sectionGap),
        ) {
            ProjectedHarmonySurface(
                state = projections.controls,
                onSetScale = onSetScale,
                onSetChord = onSetChord,
                onSetForceToScale = onSetForceToScale,
                portraitTwoHanded = true,
                chordColumns = if (portrait) 2 else 5,
                scaleColumns = if (portrait) 3 else 5,
                modifier = Modifier
                    .weight(if (portrait) 0.37f else 0.34f)
                    .fillMaxHeight()
                    .testTag(HarmonyHandPaneTestTag),
            )
            ProjectedStrummerLane(
                state = projections.articulation,
                orientation = StrummerOrientation.VERTICAL,
                showArticulationSelector = false,
                onSetArticulation = onSetPadArticulation,
                onStrumTone = onStrumTone,
                modifier = Modifier
                    .weight(if (portrait) 0.15f else 0.12f)
                    .fillMaxHeight(),
            )
            Surface(
                modifier = Modifier
                    .weight(if (portrait) 0.48f else 0.54f)
                    .fillMaxHeight()
                    .testTag(IntervalHandPaneTestTag),
                shape = StageShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.90f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.performance_right_hand_intervals).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                    )
                    ProjectedIntervalGrid(
                        pads = projections.pads,
                        compact = compact,
                        onDown = onIntervalDown,
                        onUp = onIntervalUp,
                        onOneShot = onIntervalOneShot,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArpeggiatorPanel(
    state: PerformanceArpeggiatorUiState,
    onClose: () -> Unit,
    onSetConfig: (ArpeggiatorConfig) -> Unit,
    onSetTempo: (Int) -> Unit,
    onSetDivision: (Int) -> Unit,
    onSetGate: (Int) -> Unit,
    onSetTimeSignature: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = StageShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Arpégiateur", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(
                        "Ordre, registre et motif rythmique du pad Arpégé — indépendant de Tone Row.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Interval") }
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionTitle("Tempo et mesure")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { onSetTempo((state.tempoBpm - 1).coerceAtLeast(20)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("−") }
                    Text("${state.tempoBpm} BPM", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = { onSetTempo((state.tempoBpm + 1).coerceAtMost(300)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("+") }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            onSetTimeSignature(
                                if (state.beatsPerBar == 12) 1 else state.beatsPerBar + 1,
                                state.beatUnit,
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("${state.beatsPerBar}/${state.beatUnit}") }
                    TextButton(
                        onClick = {
                            val units = listOf(2, 4, 8, 16)
                            val next = units[(units.indexOf(state.beatUnit) + 1) % units.size]
                            onSetTimeSignature(state.beatsPerBar, next)
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Unité suivante") }
                }
                SectionTitle("Division")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 4, 6, 8, 12, 16, 24).forEach { clocks ->
                        FilterChip(
                            selected = state.clocksPerStep == clocks,
                            onClick = { onSetDivision(clocks) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            label = {
                                Text(
                                    clockDivisionLabel(clocks),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                )
                            },
                        )
                    }
                }
                SectionTitle("Gate · ${state.gatePercent} %")
                Slider(
                    value = state.gatePercent.toFloat(),
                    onValueChange = { onSetGate(it.roundToInt()) },
                    valueRange = 1f..100f,
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionTitle("Ordre")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArpeggioOrder.entries.forEach { order ->
                        val label = when (order) {
                            ArpeggioOrder.AS_PLAYED -> "Accord"
                            ArpeggioOrder.UP -> "Montant"
                            ArpeggioOrder.DOWN -> "Descendant"
                            ArpeggioOrder.UP_DOWN -> "Aller-retour"
                        }
                        FilterChip(
                            selected = state.config.order == order,
                            onClick = { onSetConfig(state.config.copy(order = order)) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
                SectionTitle("Octaves")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { octaves ->
                        FilterChip(
                            selected = state.config.octaveSpan == octaves,
                            onClick = { onSetConfig(state.config.copy(octaveSpan = octaves)) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            label = { Text("$octaves") },
                        )
                    }
                }
                SectionTitle("Motif rythmique · 8 pas")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.config.stepEnabled.forEachIndexed { index, enabled ->
                        FilterChip(
                            selected = enabled,
                            enabled = enabled || state.config.stepEnabled.count { it } > 1,
                            onClick = {
                                val pattern = state.config.stepEnabled.toMutableList()
                                pattern[index] = !enabled
                                onSetConfig(state.config.copy(stepEnabled = pattern))
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            label = {
                                Text(
                                    if (enabled) "${index + 1} · ON" else "${index + 1} · —",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyProjectedPerformanceScreen(
    projections: PerformanceUiProjections,
    toneRowContentState: State<ToneRowContentUiState> = projections.toneRowContent,
    toneRowCursorState: State<ToneRowCursorUiState> = projections.toneRowCursor,
    onToneRowIntent: (ToneRowUiIntent) -> Unit,
    onSetPadArticulation: (PadArticulation) -> Unit,
    onStrumTone: (toneIndex: Int, velocity: Int) -> Unit,
    onIntervalDown: (pointerId: Long, steps: Int) -> Unit,
    onIntervalUp: (pointerId: Long) -> Unit,
    onIntervalOneShot: (steps: Int) -> Unit,
    onUndo: () -> Unit,
    onHome: () -> Unit,
    onPanic: () -> Unit,
    onSetScale: (ScaleDefinition) -> Unit,
    onSetRoot: (Int) -> Unit,
    onSetChord: (ChordDefinition) -> Unit,
    onSetForceToScale: (Boolean) -> Unit = {},
    onSetArpeggiatorConfig: (ArpeggiatorConfig) -> Unit = {},
    onSetTempo: (Int) -> Unit = {},
    onSetClockDivision: (Int) -> Unit = {},
    onSetArpeggioGate: (Int) -> Unit = {},
    onSetTimeSignature: (Int, Int) -> Unit = { _, _ -> },
    onSetRange: (MidiNoteRange) -> Unit,
    onSetWrap: (Boolean) -> Unit,
    onSetInputChannel: (Int?) -> Unit,
    onSetOutputChannel: (Int) -> Unit,
    onSetMode: (PassThroughMode) -> Unit,
    onSelectSource: (MidiPortDescriptor?) -> Unit,
    onSelectDestination: (MidiPortDescriptor?) -> Unit,
    onResetMidiMapping: () -> Unit,
    onToggleAudio: () -> Unit,
    onTogglePerformanceLock: () -> Unit,
    onDismissStatus: () -> Unit,
    onSynthPatchPreview: (SynthPatch) -> Unit = {},
    onSynthPatchChangeFinished: (SynthPatch) -> Unit = {},
    onOpenMidiMappingEditor: () -> Unit = {},
    onMidiMappingEditorAction: (MidiMappingEditorAction) -> Unit = {},
    onSaveMidiMappingEditor: () -> Unit = {},
) {
    var consoleOpen by rememberSaveable { mutableStateOf(false) }
    var toneRowArrangementOpen by rememberSaveable { mutableStateOf(false) }
    var synthOpen by rememberSaveable { mutableStateOf(false) }
    PerformanceLockObserver(projections.lock) {
        if (consoleOpen || toneRowArrangementOpen || synthOpen) {
            consoleOpen = false
            toneRowArrangementOpen = false
            synthOpen = false
        }
    }
    val currentTogglePerformanceLock = rememberUpdatedState(onTogglePerformanceLock)
    val openConsole = remember(projections.lock) {
        {
            if (projections.lock.value.locked) {
                currentTogglePerformanceLock.value()
            } else {
                toneRowArrangementOpen = false
                synthOpen = false
                consoleOpen = !consoleOpen
            }
        }
    }
    val openArrangement = remember {
        {
            consoleOpen = false
            synthOpen = false
            toneRowArrangementOpen = true
        }
    }
    val openSynth = remember(projections.lock, projections.controls) {
        {
            if (!projections.lock.value.locked && projections.controls.value.settingsLoaded) {
                consoleOpen = false
                toneRowArrangementOpen = false
                synthOpen = !synthOpen
            }
        }
    }
    val backgroundColor = MaterialTheme.colorScheme.background
    val backgroundEndColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val stageBackground = remember(backgroundColor, backgroundEndColor) {
        Brush.verticalGradient(listOf(backgroundColor, backgroundEndColor))
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
    ) { scaffoldPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .background(stageBackground),
        ) {
            val compact = maxWidth < 1_040.dp || maxHeight < 640.dp
            val portraitTwoHanded = maxHeight > maxWidth
            val availableWidth = maxWidth
            val outerPadding = if (compact) 8.dp else 12.dp
            val sectionGap = if (compact) 7.dp else 10.dp

            Column(
                modifier = Modifier.fillMaxSize().padding(outerPadding),
                verticalArrangement = Arrangement.spacedBy(sectionGap),
            ) {
                ProjectedStageHeader(
                    pitchState = projections.pitch,
                    headerState = projections.header,
                    compact = compact,
                    onOpenConsole = openConsole,
                )

                ProjectedToneRowDeck(
                    state = toneRowContentState,
                    cursorState = toneRowCursorState,
                    lockState = projections.lock,
                    compact = compact,
                    onIntent = onToneRowIntent,
                    onOpenArrangement = openArrangement,
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    StageBackdrop(Modifier.fillMaxSize())
                    if (portraitTwoHanded) {
                        TwoHandedPortraitStage(
                            projections = projections,
                            compact = compact,
                            sectionGap = sectionGap,
                            onSetScale = onSetScale,
                            onSetChord = onSetChord,
                            onSetForceToScale = onSetForceToScale,
                            onSetPadArticulation = onSetPadArticulation,
                            onStrumTone = onStrumTone,
                            onHome = onHome,
                            onUndo = onUndo,
                            onPanic = onPanic,
                            onIntervalDown = onIntervalDown,
                            onIntervalUp = onIntervalUp,
                            onIntervalOneShot = onIntervalOneShot,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(sectionGap),
                        ) {
                            ProjectedHarmonySurface(
                                state = projections.controls,
                                onSetScale = onSetScale,
                                onSetChord = onSetChord,
                                onSetForceToScale = onSetForceToScale,
                                portraitTwoHanded = true,
                                chordColumns = 5,
                                scaleColumns = 5,
                                modifier = Modifier
                                    .width(if (compact) 320.dp else 420.dp)
                                    .fillMaxHeight()
                                    .testTag(HarmonyHandPaneTestTag),
                            )
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag(IntervalHandPaneTestTag),
                                horizontalArrangement = Arrangement.spacedBy(sectionGap),
                            ) {
                            ProjectedUtilityRail(
                                state = projections.utility,
                                compact = compact,
                                onHome = onHome,
                                onUndo = onUndo,
                                onPanic = onPanic,
                                modifier = Modifier
                                    .width(if (compact) 104.dp else 124.dp)
                                    .fillMaxHeight(),
                            )
                            ProjectedIntervalGrid(
                                pads = projections.pads,
                                compact = compact,
                                onDown = onIntervalDown,
                                onUp = onIntervalUp,
                                onOneShot = onIntervalOneShot,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            ProjectedStrummerLane(
                                state = projections.articulation,
                                orientation = StrummerOrientation.VERTICAL,
                                onSetArticulation = onSetPadArticulation,
                                onStrumTone = onStrumTone,
                                modifier = Modifier
                                    .width(if (compact) 88.dp else 112.dp)
                                    .fillMaxHeight(),
                            )
                            }
                        }
                    }

                    PerformanceOverlay(
                        consoleState = projections.console,
                        midiMappingEditorState = projections.midiMappingEditor,
                        synthState = projections.synth,
                        toneRowContentState = toneRowContentState,
                        toneRowCursorState = toneRowCursorState,
                        lockState = projections.lock,
                        arrangementOpen = toneRowArrangementOpen,
                        consoleOpen = consoleOpen,
                        synthOpen = synthOpen,
                        compact = compact,
                        availableWidth = availableWidth,
                        onCloseArrangement = { toneRowArrangementOpen = false },
                        onCloseConsole = { consoleOpen = false },
                        onCloseSynth = { synthOpen = false },
                        onToneRowIntent = onToneRowIntent,
                        onSetScale = onSetScale,
                        onSetRoot = onSetRoot,
                        onSetChord = onSetChord,
                        onSetRange = onSetRange,
                        onSetWrap = onSetWrap,
                        onSetInputChannel = onSetInputChannel,
                        onSetOutputChannel = onSetOutputChannel,
                        onSetMode = onSetMode,
                        onSelectSource = onSelectSource,
                        onSelectDestination = onSelectDestination,
                        onResetMidiMapping = onResetMidiMapping,
                        onOpenMidiMappingEditor = onOpenMidiMappingEditor,
                        onMidiMappingEditorAction = onMidiMappingEditorAction,
                        onSaveMidiMappingEditor = onSaveMidiMappingEditor,
                        onTogglePerformanceLock = onTogglePerformanceLock,
                        onSynthPatchPreview = onSynthPatchPreview,
                        onSynthPatchChangeFinished = onSynthPatchChangeFinished,
                    )
                }

                ProjectedStatusBanner(state = projections.status, onDismiss = onDismissStatus)

                ProjectedSystemRibbon(
                    state = projections.ribbon,
                    activeNotesState = projections.activeNotes,
                    compact = compact,
                    onSetChord = onSetChord,
                    onSetMode = onSetMode,
                    onToggleAudio = onToggleAudio,
                    onOpenSynth = openSynth,
                    onOpenConsole = openConsole,
                )
            }
        }
    }
}

@Composable
private fun PerformanceLockObserver(
    state: State<PerformanceLockUiState>,
    onLocked: () -> Unit,
) {
    val locked = state.value.locked
    LaunchedEffect(locked) {
        if (locked) onLocked()
    }
}

@Composable
private fun ProjectedStageHeader(
    pitchState: State<PerformancePitchUiState>,
    headerState: State<PerformanceHeaderUiState>,
    compact: Boolean,
    onOpenConsole: () -> Unit,
) {
    val pitch = pitchState.value
    val header = headerState.value
    StageHeader(
        currentNoteName = pitch.currentNoteName,
        currentDegree = pitch.currentDegree,
        rootName = header.rootName,
        scaleName = header.scaleName,
        chordName = header.chordName,
        sourceConnection = header.sourceConnection,
        destinationConnection = header.destinationConnection,
        audioAvailable = header.audioAvailable,
        audioRunning = header.audioRunning,
        performanceLock = header.performanceLock,
        compact = compact,
        onOpenConsole = onOpenConsole,
    )
}

@Composable
private fun ProjectedToneRowDeck(
    state: State<ToneRowContentUiState>,
    cursorState: State<ToneRowCursorUiState>,
    lockState: State<PerformanceLockUiState>,
    compact: Boolean,
    onIntent: (ToneRowUiIntent) -> Unit,
    onOpenArrangement: () -> Unit,
) {
    ToneRowDeck(
        state = state,
        cursorState = cursorState,
        performanceLock = lockState.value.locked,
        compact = compact,
        onIntent = onIntent,
        onOpenArrangement = onOpenArrangement,
    )
}

@Composable
private fun ProjectedUtilityRail(
    state: State<PerformanceUtilityUiState>,
    compact: Boolean,
    onHome: () -> Unit,
    onUndo: () -> Unit,
    onPanic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UtilityRail(
        compact = compact,
        restartMode = state.value.restartMode,
        onHome = onHome,
        onUndo = onUndo,
        onPanic = onPanic,
        modifier = modifier,
    )
}

@Composable
private fun ProjectedIntervalGrid(
    pads: List<PerformancePadUiProjection>,
    compact: Boolean,
    onDown: (Long, Int) -> Unit,
    onUp: (Long) -> Unit,
    onOneShot: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    IntervalGrid(
        pads = pads,
        compact = compact,
        onDown = onDown,
        onUp = onUp,
        onOneShot = onOneShot,
        modifier = modifier,
    )
}

@Composable
private fun ProjectedStrummerLane(
    state: State<PerformanceArticulationUiState>,
    orientation: StrummerOrientation,
    showArticulationSelector: Boolean = true,
    onSetArticulation: (PadArticulation) -> Unit,
    onStrumTone: (toneIndex: Int, velocity: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    StrummerLane(
        state = state.value,
        orientation = orientation,
        showArticulationSelector = showArticulationSelector,
        onSetArticulation = onSetArticulation,
        onStrumTone = onStrumTone,
        modifier = modifier,
    )
}

@Composable
private fun ProjectedHarmonySurface(
    state: State<PerformanceControlsUiState>,
    onSetScale: (ScaleDefinition) -> Unit,
    onSetChord: (ChordDefinition) -> Unit,
    onSetForceToScale: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    portraitTwoHanded: Boolean = false,
    chordColumns: Int = 2,
    scaleColumns: Int = 3,
) {
    val controls = state.value
    HarmonySurface(
        scale = controls.scale,
        chord = controls.chord,
        forceToScale = controls.forceToScale,
        enabled = controls.settingsLoaded && !controls.performanceLock,
        onSetScale = onSetScale,
        onSetChord = onSetChord,
        onSetForceToScale = onSetForceToScale,
        portraitTwoHanded = portraitTwoHanded,
        chordColumns = chordColumns,
        scaleColumns = scaleColumns,
        modifier = modifier,
    )
}

@Composable
private fun TwoHandedPortraitStage(
    projections: PerformanceUiProjections,
    compact: Boolean,
    sectionGap: Dp,
    onSetScale: (ScaleDefinition) -> Unit,
    onSetChord: (ChordDefinition) -> Unit,
    onSetForceToScale: (Boolean) -> Unit,
    onSetPadArticulation: (PadArticulation) -> Unit,
    onStrumTone: (toneIndex: Int, velocity: Int) -> Unit,
    onHome: () -> Unit,
    onUndo: () -> Unit,
    onPanic: () -> Unit,
    onIntervalDown: (pointerId: Long, steps: Int) -> Unit,
    onIntervalUp: (pointerId: Long) -> Unit,
    onIntervalOneShot: (steps: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(sectionGap),
    ) {
        Column(
            modifier = Modifier
                .weight(0.43f)
                .fillMaxHeight()
                .testTag(HarmonyHandPaneTestTag),
            verticalArrangement = Arrangement.spacedBy(sectionGap),
        ) {
            ProjectedHarmonySurface(
                state = projections.controls,
                onSetScale = onSetScale,
                onSetChord = onSetChord,
                onSetForceToScale = onSetForceToScale,
                portraitTwoHanded = true,
                chordColumns = 2,
                scaleColumns = 3,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            ProjectedStrummerLane(
                state = projections.articulation,
                orientation = StrummerOrientation.HORIZONTAL,
                onSetArticulation = onSetPadArticulation,
                onStrumTone = onStrumTone,
                modifier = Modifier.fillMaxWidth().height(172.dp),
            )
        }

        Surface(
            modifier = Modifier
                .weight(0.57f)
                .fillMaxHeight()
                .testTag(IntervalHandPaneTestTag),
            shape = StageShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.90f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.performance_right_hand_intervals).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                )
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp),
                ) {
                    ProjectedUtilityRail(
                        state = projections.utility,
                        compact = true,
                        onHome = onHome,
                        onUndo = onUndo,
                        onPanic = onPanic,
                        modifier = Modifier.width(76.dp).fillMaxHeight(),
                    )
                    ProjectedIntervalGrid(
                        pads = projections.pads,
                        compact = compact,
                        onDown = onIntervalDown,
                        onUp = onIntervalUp,
                        onOneShot = onIntervalOneShot,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectedStatusBanner(
    state: State<PerformanceStatusUiState>,
    onDismiss: () -> Unit,
) {
    state.value.message?.let { message ->
        StatusBanner(message = message, onDismiss = onDismiss)
    }
}

@Composable
private fun ProjectedSystemRibbon(
    state: State<PerformanceRibbonUiState>,
    activeNotesState: State<PerformanceActiveNotesUiState>,
    compact: Boolean,
    onSetChord: (ChordDefinition) -> Unit,
    onSetMode: (PassThroughMode) -> Unit,
    onToggleAudio: () -> Unit,
    onOpenSynth: () -> Unit,
    onOpenConsole: () -> Unit,
) {
    val ribbon = state.value
    SystemRibbon(
        range = ribbon.range,
        solfegeWrap = ribbon.solfegeWrap,
        activeNotesState = activeNotesState,
        chord = ribbon.chord,
        mode = ribbon.mode,
        audioMonitorEnabled = ribbon.audioMonitorEnabled,
        audioAvailable = ribbon.audioAvailable,
        audioRunning = ribbon.audioRunning,
        performanceLock = ribbon.performanceLock,
        synthEnabled = ribbon.settingsLoaded,
        compact = compact,
        onSetChord = onSetChord,
        onSetMode = onSetMode,
        onToggleAudio = onToggleAudio,
        onOpenSynth = onOpenSynth,
        onOpenConsole = onOpenConsole,
    )
}

@Composable
private fun BoxScope.PerformanceOverlay(
    consoleState: State<PerformanceConsoleUiState>,
    midiMappingEditorState: State<MidiMappingEditorUiState>,
    synthState: State<PerformanceSynthUiState>,
    toneRowContentState: State<ToneRowContentUiState>,
    toneRowCursorState: State<ToneRowCursorUiState>,
    lockState: State<PerformanceLockUiState>,
    arrangementOpen: Boolean,
    consoleOpen: Boolean,
    synthOpen: Boolean,
    compact: Boolean,
    availableWidth: androidx.compose.ui.unit.Dp,
    onCloseArrangement: () -> Unit,
    onCloseConsole: () -> Unit,
    onCloseSynth: () -> Unit,
    onToneRowIntent: (ToneRowUiIntent) -> Unit,
    onSetScale: (ScaleDefinition) -> Unit,
    onSetRoot: (Int) -> Unit,
    onSetChord: (ChordDefinition) -> Unit,
    onSetRange: (MidiNoteRange) -> Unit,
    onSetWrap: (Boolean) -> Unit,
    onSetInputChannel: (Int?) -> Unit,
    onSetOutputChannel: (Int) -> Unit,
    onSetMode: (PassThroughMode) -> Unit,
    onSelectSource: (MidiPortDescriptor?) -> Unit,
    onSelectDestination: (MidiPortDescriptor?) -> Unit,
    onResetMidiMapping: () -> Unit,
    onOpenMidiMappingEditor: () -> Unit,
    onMidiMappingEditorAction: (MidiMappingEditorAction) -> Unit,
    onSaveMidiMappingEditor: () -> Unit,
    onTogglePerformanceLock: () -> Unit,
    onSynthPatchPreview: (SynthPatch) -> Unit,
    onSynthPatchChangeFinished: (SynthPatch) -> Unit,
) {
    if (lockState.value.locked) return
    if (midiMappingEditorState.value.editor is dev.intervaltablet.domain.MidiMappingEditorState.Editing) {
        val panelMargin = if (compact) 16.dp else 120.dp
        val maximumPanelWidth = (availableWidth - panelMargin).coerceAtLeast(320.dp)
        MidiMappingEditorPanel(
            state = midiMappingEditorState.value,
            onAction = onMidiMappingEditorAction,
            onSave = onSaveMidiMappingEditor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width((if (compact) 520.dp else 720.dp).coerceAtMost(maximumPanelWidth))
                .fillMaxHeight()
                .shadow(22.dp, StageShape),
        )
    } else if (arrangementOpen) {
        ToneRowArrangementPanel(
            state = toneRowContentState,
            cursorState = toneRowCursorState,
            onClose = onCloseArrangement,
            onIntent = onToneRowIntent,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width((if (compact) 380.dp else 424.dp).coerceAtMost(availableWidth - 120.dp))
                .fillMaxHeight()
                .shadow(22.dp, StageShape),
        )
    } else if (consoleOpen) {
        MidiConsole(
            state = consoleState.value,
            onClose = onCloseConsole,
            onSetScale = onSetScale,
            onSetRoot = onSetRoot,
            onSetChord = onSetChord,
            onSetRange = onSetRange,
            onSetWrap = onSetWrap,
            onSetInputChannel = onSetInputChannel,
            onSetOutputChannel = onSetOutputChannel,
            onSetMode = onSetMode,
            onSelectSource = onSelectSource,
            onSelectDestination = onSelectDestination,
            onResetMidiMapping = onResetMidiMapping,
            onOpenMidiMappingEditor = onOpenMidiMappingEditor,
            onTogglePerformanceLock = onTogglePerformanceLock,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width((if (compact) 360.dp else 392.dp).coerceAtMost(availableWidth - 120.dp))
                .fillMaxHeight()
                .shadow(22.dp, StageShape),
        )
    } else if (synthOpen) {
        val panelMargin = if (compact) 16.dp else 120.dp
        val maximumPanelWidth = (availableWidth - panelMargin).coerceAtLeast(280.dp)
        SynthPanel(
            state = synthState.value,
            compact = compact,
            onClose = onCloseSynth,
            onPatchPreview = onSynthPatchPreview,
            onPatchChangeFinished = onSynthPatchChangeFinished,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width((if (compact) 440.dp else 640.dp).coerceAtMost(maximumPanelWidth))
                .fillMaxHeight()
                .shadow(22.dp, StageShape),
        )
    }
}

@Composable
private fun StageHeader(
    currentNoteName: String,
    currentDegree: Int?,
    rootName: String,
    scaleName: String,
    chordName: String,
    sourceConnection: MidiConnectionState,
    destinationConnection: MidiConnectionState,
    audioAvailable: Boolean,
    audioRunning: Boolean,
    performanceLock: Boolean,
    compact: Boolean,
    onOpenConsole: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(if (compact) 70.dp else 82.dp),
        shape = StageShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 10.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
        ) {
            Column(modifier = Modifier.weight(if (compact) 0.8f else 0.65f)) {
                Text(
                    stringResource(R.string.performance_note).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        currentNoteName,
                        style = if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        currentDegree?.let { "${stringResource(R.string.performance_degree)} $it" }
                            ?: stringResource(R.string.performance_chromatic_anchor),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            HeaderContext(
                label = stringResource(R.string.performance_key),
                value = rootName,
                compact = compact,
                modifier = Modifier.weight(0.45f),
            )
            HeaderContext(
                label = stringResource(R.string.performance_scale),
                value = scaleName,
                compact = compact,
                modifier = Modifier.weight(if (compact) 0.8f else 0.65f),
            )
            if (!compact) {
                HeaderContext(
                    label = stringResource(R.string.performance_chord),
                    value = chordName,
                    compact = false,
                    modifier = Modifier.weight(0.55f),
                )
            }

            MidiSummary(
                sourceConnection = sourceConnection,
                destinationConnection = destinationConnection,
                audioAvailable = audioAvailable,
                audioRunning = audioRunning,
                compact = compact,
                modifier = Modifier.weight(if (compact) 1.25f else 1f),
            )

            OutlinedButton(
                onClick = onOpenConsole,
                modifier = Modifier.heightIn(min = 56.dp).widthIn(min = if (compact) 98.dp else 116.dp),
                shape = ControlShape,
            ) {
                Text(
                    if (performanceLock) {
                        stringResource(R.string.performance_unlock)
                    } else {
                        stringResource(R.string.performance_open_console)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HeaderContext(
    label: String,
    value: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MidiSummary(
    sourceConnection: MidiConnectionState,
    destinationConnection: MidiConnectionState,
    audioAvailable: Boolean,
    audioRunning: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MiniConnectionLine(
            label = stringResource(R.string.midi_input),
            connection = sourceConnection,
            compact = compact,
        )
        MiniConnectionLine(
            label = stringResource(R.string.midi_output),
            connection = destinationConnection,
            compact = compact,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 8.dp, minHeight = 8.dp)
                    .background(
                        if (audioRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(50),
                    ),
            )
            Text(
                when {
                    !audioAvailable -> stringResource(R.string.performance_audio_short_unavailable)
                    audioRunning -> stringResource(R.string.performance_audio_short_on)
                    else -> stringResource(R.string.performance_audio_short_off)
                },
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MiniConnectionLine(
    label: String,
    connection: MidiConnectionState,
    compact: Boolean,
) {
    val connected = connection.phase == MidiConnectionPhase.OPEN
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 8.dp, minHeight = 8.dp)
                .background(
                    if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(50),
                ),
        )
        Text(
            "$label · ${connectionPhaseLabel(connection.phase)}",
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UtilityRail(
    compact: Boolean,
    restartMode: Boolean,
    onHome: () -> Unit,
    onUndo: () -> Unit,
    onPanic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeDescription = stringResource(R.string.performance_home_description)
    val undoDescription = stringResource(
        if (restartMode) R.string.performance_restart_description else R.string.performance_undo_description,
    )
    val panicDescription = stringResource(R.string.performance_panic_description)
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        shape = StageShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(if (compact) 7.dp else 9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = homeDescription },
                shape = ControlShape,
            ) {
                Text(stringResource(R.string.performance_home), maxLines = 1)
            }
            OutlinedButton(
                onClick = onUndo,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = undoDescription },
                shape = ControlShape,
            ) {
                Text(
                    stringResource(if (restartMode) R.string.performance_restart else R.string.performance_undo),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onPanic,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (compact) 64.dp else 72.dp)
                    .semantics { contentDescription = panicDescription },
                shape = ControlShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(
                    stringResource(R.string.performance_panic).uppercase(),
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StrummerLane(
    state: PerformanceArticulationUiState,
    orientation: StrummerOrientation,
    showArticulationSelector: Boolean = true,
    onSetArticulation: (PadArticulation) -> Unit,
    onStrumTone: (toneIndex: Int, velocity: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        shape = StageShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        when (orientation) {
            StrummerOrientation.VERTICAL -> Column(
                modifier = Modifier.fillMaxSize().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.strummer_title).uppercase(),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                if (showArticulationSelector) {
                    ArticulationSelector(
                        selected = state.articulation,
                        onSelect = onSetArticulation,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                StrummerToneSurface(
                    state = state,
                    orientation = orientation,
                    onStrumTone = onStrumTone,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            StrummerOrientation.HORIZONTAL -> Row(
                modifier = Modifier.fillMaxSize().padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ArticulationSelector(
                    selected = state.articulation,
                    onSelect = onSetArticulation,
                    modifier = Modifier.width(112.dp).fillMaxHeight(),
                )
                StrummerToneSurface(
                    state = state,
                    orientation = orientation,
                    onStrumTone = onStrumTone,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun ArticulationSelector(
    selected: PadArticulation,
    onSelect: (PadArticulation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PadArticulation.entries.toList().chunked(2).forEach { articulations ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                articulations.forEach { articulation ->
                    val isSelected = articulation == selected
                    val label = articulationLabel(articulation)
                    val description = articulationDescription(articulation)
                    ImmediateChoiceButton(
                        selected = isSelected,
                        enabled = true,
                        label = label.uppercase(),
                        description = description,
                        onSelect = { onSelect(articulation) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(articulationModeTestTag(articulation)),
                    )
                }
                repeat(2 - articulations.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ImmediateChoiceButton(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    description: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSelect = rememberUpdatedState(onSelect)
    var focused by remember { mutableStateOf(false) }
    val background = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        focused -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = description
                if (!enabled) disabled()
                onClick(label = description) {
                    if (enabled) currentOnSelect.value()
                    enabled
                }
            }
            .onKeyEvent { event ->
                val activationKey = event.key == Key.Enter || event.key == Key.Spacebar
                if (!enabled || !activationKey) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyDown) currentOnSelect.value()
                    true
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .pointerInput(enabled) {
                if (enabled) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        currentOnSelect.value()
                        down.consume()
                        waitForUpOrCancellation()
                    }
                }
            },
        shape = ControlShape,
        color = background,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = foreground,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StrummerToneSurface(
    state: PerformanceArticulationUiState,
    orientation: StrummerOrientation,
    onStrumTone: (toneIndex: Int, velocity: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentToneCount = rememberUpdatedState(state.tones.size)
    val currentOnStrumTone = rememberUpdatedState(onStrumTone)
    val velocityHint = when (orientation) {
        StrummerOrientation.VERTICAL -> stringResource(R.string.strummer_velocity_horizontal)
        StrummerOrientation.HORIZONTAL -> stringResource(R.string.strummer_velocity_vertical)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = velocityHint,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(orientation) {
                    detectStrummerPointers(
                        orientation = orientation,
                        toneCount = { currentToneCount.value },
                        onHit = { toneIndex, velocity ->
                            currentOnStrumTone.value(toneIndex, velocity)
                        },
                    )
                },
        ) {
            if (state.tones.isEmpty()) {
                Text(
                    text = stringResource(R.string.strummer_empty),
                    modifier = Modifier.align(Alignment.Center).padding(6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                StrummerToneBands(
                    tones = state.tones,
                    defaultVelocity = state.defaultVelocity,
                    orientation = orientation,
                    onStrumTone = { toneIndex ->
                        currentOnStrumTone.value(toneIndex, state.defaultVelocity)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun StrummerToneBands(
    tones: List<StrumToneUi>,
    defaultVelocity: Int,
    orientation: StrummerOrientation,
    onStrumTone: (toneIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (orientation) {
        StrummerOrientation.VERTICAL -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            tones.forEachIndexed { index, tone ->
                StrummerToneBand(
                    tone = tone,
                    index = index,
                    toneCount = tones.size,
                    defaultVelocity = defaultVelocity,
                    onStrumTone = onStrumTone,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
        StrummerOrientation.HORIZONTAL -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            tones.forEachIndexed { index, tone ->
                StrummerToneBand(
                    tone = tone,
                    index = index,
                    toneCount = tones.size,
                    defaultVelocity = defaultVelocity,
                    onStrumTone = onStrumTone,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun StrummerToneBand(
    tone: StrumToneUi,
    index: Int,
    toneCount: Int,
    defaultVelocity: Int,
    onStrumTone: (toneIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnStrumTone = rememberUpdatedState(onStrumTone)
    var focused by remember { mutableStateOf(false) }
    val description = stringResource(
        R.string.strummer_tone_description,
        index + 1,
        toneCount,
        tone.label,
        defaultVelocity,
    )
    val container = if (index % 2 == 0) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val content = if (index % 2 == 0) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .background(
                color = if (focused) MaterialTheme.colorScheme.primaryContainer else container,
                shape = ControlShape,
            )
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = description) {
                    currentOnStrumTone.value(index)
                    true
                }
            }
            .testTag(strummerToneTestTag(index))
            .onKeyEvent { event ->
                val activationKey = event.key == Key.Enter || event.key == Key.Spacebar
                if (!activationKey) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyUp) currentOnStrumTone.value(index)
                    true
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 4.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.72f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = tone.label,
                style = MaterialTheme.typography.titleSmall,
                color = content,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun articulationLabel(articulation: PadArticulation): String = when (articulation) {
    PadArticulation.ARPEGGIATED -> stringResource(R.string.performance_articulation_arpeggiated)
    PadArticulation.STACKED -> stringResource(R.string.performance_articulation_stacked)
    PadArticulation.MUTED -> stringResource(R.string.performance_articulation_muted)
}

@Composable
private fun articulationDescription(articulation: PadArticulation): String = when (articulation) {
    PadArticulation.ARPEGGIATED -> stringResource(R.string.performance_articulation_arpeggiated_description)
    PadArticulation.STACKED -> stringResource(R.string.performance_articulation_stacked_description)
    PadArticulation.MUTED -> stringResource(R.string.performance_articulation_muted_description)
}

private fun performanceScaleLabel(scale: ScaleDefinition): String = when (scale.id) {
    "natural_minor" -> "Nat. minor"
    "harmonic_minor" -> "Harm. minor"
    "melodic_minor" -> "Mel. minor"
    "mixolydian" -> "Mixolyd."
    "major_pentatonic" -> "Maj. penta"
    "minor_pentatonic" -> "Min. penta"
    else -> scale.displayName
}

internal fun articulationModeTestTag(articulation: PadArticulation): String =
    "pad-articulation:${articulation.name.lowercase()}"

internal fun strummerToneTestTag(index: Int): String = "strummer-tone:$index"

@Composable
private fun IntervalGrid(
    pads: List<PerformancePadUiProjection>,
    compact: Boolean,
    onDown: (Long, Int) -> Unit,
    onUp: (Long) -> Unit,
    onOneShot: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap = if (compact) 6.dp else 9.dp
    val padsByStep = remember(pads) { pads.associateBy(PerformancePadUiProjection::steps) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        IntervalRows.forEach { row ->
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { steps ->
                    IntervalPad(
                        steps = steps,
                        state = padsByStep.getValue(steps).state,
                        compact = compact,
                        onDown = onDown,
                        onUp = onUp,
                        onOneShot = onOneShot,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun IntervalPad(
    steps: Int,
    state: State<PerformancePadUiState>,
    compact: Boolean,
    onDown: (Long, Int) -> Unit,
    onUp: (Long) -> Unit,
    onOneShot: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentDown = rememberUpdatedState(onDown)
    val currentUp = rememberUpdatedState(onUp)
    val currentOneShot = rememberUpdatedState(onOneShot)
    val resources = LocalResources.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 48)
    var focused by remember { mutableStateOf(false) }
    val stepLabel = if (steps > 0) "+$steps" else steps.toString()
    val direction = when {
        steps > 0 -> stringResource(R.string.interval_direction_up)
        steps < 0 -> stringResource(R.string.interval_direction_down)
        else -> stringResource(R.string.interval_direction_repeat)
    }.uppercase()
    val clampedLabel = stringResource(R.string.interval_clamped).uppercase()
    val wrappedLabel = stringResource(R.string.interval_wrapped).uppercase()
    val accent = when {
        steps > 0 -> MaterialTheme.colorScheme.primary
        steps < 0 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val activeContainer = when {
        steps > 0 -> MaterialTheme.colorScheme.primaryContainer
        steps < 0 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val inactiveContainer = MaterialTheme.colorScheme.surfaceContainerHigh
    val onActiveContainer = when {
        steps > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
        steps < 0 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val directionStyle = MaterialTheme.typography.labelSmall.copy(
        color = accent,
        fontWeight = FontWeight.Bold,
    )
    val stepStyle = (if (compact) {
        MaterialTheme.typography.headlineLarge
    } else {
        MaterialTheme.typography.displaySmall
    }).copy(
        color = onSurface,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
    )
    val targetStyle = (if (compact) {
        MaterialTheme.typography.titleSmall
    } else {
        MaterialTheme.typography.titleMedium
    }).copy(
        color = onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
    val badgeStyle = MaterialTheme.typography.labelSmall.copy(
        color = accent,
        fontWeight = FontWeight.Bold,
    )
    val activeStyle = MaterialTheme.typography.labelMedium.copy(
        color = accent,
        fontWeight = FontWeight.Black,
    )

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .drawWithCache {
                val pad = state.value
                val active = pad.activeCount > 0
                val innerPadding = (if (compact) 7.dp else 10.dp).toPx()
                val shapeRadius = 20.dp.toPx()
                val borderWidth = (if (active) 3.dp else 1.dp).toPx()
                val maxTextWidth = (size.width - innerPadding * 2f)
                    .roundToInt()
                    .coerceAtLeast(0)
                val textConstraints = Constraints(maxWidth = maxTextWidth)
                val directionLayout = textMeasurer.measure(
                    text = direction,
                    style = directionStyle.copy(color = if (active) onActiveContainer else accent),
                    maxLines = 1,
                    constraints = textConstraints,
                )
                val stepLayout = textMeasurer.measure(
                    text = stepLabel,
                    style = stepStyle.copy(color = if (active) onActiveContainer else onSurface),
                    maxLines = 1,
                    constraints = textConstraints,
                )
                val targetLayout = textMeasurer.measure(
                    text = resources.getString(R.string.interval_target, pad.target),
                    style = targetStyle.copy(
                        color = if (active) onActiveContainer else onSurfaceVariant,
                    ),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = textConstraints,
                )
                val boundaryLayout = when (pad.boundary) {
                    PitchMoveBoundary.NONE -> null
                    PitchMoveBoundary.CLAMPED -> textMeasurer.measure(
                        text = clampedLabel,
                        style = badgeStyle.copy(color = if (active) onActiveContainer else accent),
                        maxLines = 1,
                    )
                    PitchMoveBoundary.WRAPPED -> textMeasurer.measure(
                        text = wrappedLabel,
                        style = badgeStyle.copy(color = if (active) onActiveContainer else accent),
                        maxLines = 1,
                    )
                }
                val activeLayout = if (active) {
                    textMeasurer.measure(
                        text = "● ${pad.activeCount}",
                        style = activeStyle.copy(color = onActiveContainer),
                        maxLines = 1,
                    )
                } else {
                    null
                }

                onDrawBehind {
                    drawRoundRect(
                        color = if (active) activeContainer else inactiveContainer,
                        cornerRadius = CornerRadius(shapeRadius),
                    )
                    val borderInset = borderWidth / 2f
                    drawRoundRect(
                        color = accent.copy(alpha = if (active) 1f else 0.58f),
                        topLeft = Offset(borderInset, borderInset),
                        size = Size(
                            width = (size.width - borderWidth).coerceAtLeast(0f),
                            height = (size.height - borderWidth).coerceAtLeast(0f),
                        ),
                        cornerRadius = CornerRadius((shapeRadius - borderInset).coerceAtLeast(0f)),
                        style = Stroke(width = borderWidth),
                    )
                    if (focused) {
                        val focusWidth = 3.dp.toPx()
                        val focusInset = focusWidth * 1.75f
                        drawRoundRect(
                            color = onSurface,
                            topLeft = Offset(focusInset, focusInset),
                            size = Size(
                                width = (size.width - focusInset * 2f).coerceAtLeast(0f),
                                height = (size.height - focusInset * 2f).coerceAtLeast(0f),
                            ),
                            cornerRadius = CornerRadius((shapeRadius - focusInset).coerceAtLeast(0f)),
                            style = Stroke(width = focusWidth),
                        )
                    }

                    val textHeight = directionLayout.size.height +
                        stepLayout.size.height + targetLayout.size.height
                    var textTop = (size.height - textHeight) / 2f
                    drawText(
                        textLayoutResult = directionLayout,
                        topLeft = Offset((size.width - directionLayout.size.width) / 2f, textTop),
                    )
                    textTop += directionLayout.size.height
                    drawText(
                        textLayoutResult = stepLayout,
                        topLeft = Offset((size.width - stepLayout.size.width) / 2f, textTop),
                    )
                    textTop += stepLayout.size.height
                    drawText(
                        textLayoutResult = targetLayout,
                        topLeft = Offset((size.width - targetLayout.size.width) / 2f, textTop),
                    )

                    boundaryLayout?.let { layout ->
                        val horizontalPadding = 7.dp.toPx()
                        val verticalPadding = 3.dp.toPx()
                        val badgeWidth = layout.size.width + horizontalPadding * 2f
                        val badgeHeight = layout.size.height + verticalPadding * 2f
                        drawRoundRect(
                            color = accent.copy(alpha = 0.16f),
                            topLeft = Offset(innerPadding, innerPadding),
                            size = Size(badgeWidth, badgeHeight),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                        )
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                x = innerPadding + horizontalPadding,
                                y = innerPadding + verticalPadding,
                            ),
                        )
                    }
                    activeLayout?.let { layout ->
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                x = size.width - innerPadding - layout.size.width,
                                y = innerPadding,
                            ),
                        )
                    }
                }
            }
            .semantics {
                val pad = state.value
                role = Role.Button
                val actionDescription = resources.intervalActionDescription(
                    steps = steps,
                    target = pad.target,
                    boundary = pad.boundary,
                )
                contentDescription = actionDescription
                stateDescription = resources.intervalPressedDescription(pad.activeCount)
                onClick(label = actionDescription) {
                    currentOneShot.value(steps)
                    true
                }
            }
            .testTag(intervalPadTestTag(steps))
            .onKeyEvent { event ->
                val activationKey = event.key == Key.Enter || event.key == Key.Spacebar
                if (!activationKey) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyUp) currentOneShot.value(steps)
                    true
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .pointerInput(steps) {
                detectIntervalPointers(
                    steps = steps,
                    onDown = { pointerId, interval -> currentDown.value(pointerId, interval) },
                    onUp = { pointerId -> currentUp.value(pointerId) },
                )
            },
    ) {}
}

@Composable
private fun HarmonySurface(
    scale: ScaleDefinition,
    chord: ChordDefinition,
    forceToScale: Boolean,
    enabled: Boolean,
    onSetScale: (ScaleDefinition) -> Unit,
    onSetChord: (ChordDefinition) -> Unit,
    onSetForceToScale: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    portraitTwoHanded: Boolean = false,
    chordColumns: Int = 2,
    scaleColumns: Int = 3,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = (if (portraitTwoHanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (portraitTwoHanded) {
                Text(
                    stringResource(R.string.performance_left_hand_harmony).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    stringResource(R.string.performance_scales).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                ScaleLibrary.all.chunked(scaleColumns).forEach { options ->
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        options.forEach { option ->
                            ImmediateChoiceButton(
                                selected = option == scale,
                                enabled = enabled,
                                label = performanceScaleLabel(option),
                                description = option.displayName,
                                onSelect = { onSetScale(option) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag(scaleChipTestTag(option.id)),
                            )
                        }
                        repeat(scaleColumns - options.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                FilterChip(
                    selected = forceToScale,
                    onClick = { onSetForceToScale(!forceToScale) },
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(ForceToScaleTestTag),
                    label = { Text(stringResource(R.string.performance_force_to_scale)) },
                )
                Text(
                    stringResource(R.string.performance_chord_variants).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                ChordLibrary.all.chunked(chordColumns).forEach { options ->
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        options.forEach { option ->
                            ImmediateChoiceButton(
                                selected = option == chord,
                                enabled = enabled,
                                label = option.displayName,
                                description = option.displayName,
                                onSelect = { onSetChord(option) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag(chordChipTestTag(option.id)),
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = forceToScale,
                        onClick = { onSetForceToScale(!forceToScale) },
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(ForceToScaleTestTag),
                        label = { Text(stringResource(R.string.performance_force_to_scale)) },
                    )
                    Text(
                        stringResource(R.string.performance_scales).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(ScaleLibrary.all, key = ScaleDefinition::id) { option ->
                            FilterChip(
                                selected = option == scale,
                                onClick = { onSetScale(option) },
                                enabled = enabled,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag(scaleChipTestTag(option.id)),
                                label = { Text(option.displayName, maxLines = 1) },
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.performance_chord_variants).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                ChordLibrary.all.chunked(5).forEach { options ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        options.forEach { option ->
                            FilterChip(
                                selected = option == chord,
                                onClick = { onSetChord(option) },
                                enabled = enabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag(chordChipTestTag(option.id)),
                                label = {
                                    Text(
                                        option.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemRibbon(
    range: MidiNoteRange,
    solfegeWrap: Boolean,
    activeNotesState: State<PerformanceActiveNotesUiState>,
    chord: ChordDefinition,
    mode: PassThroughMode,
    audioMonitorEnabled: Boolean,
    audioAvailable: Boolean,
    audioRunning: Boolean,
    performanceLock: Boolean,
    synthEnabled: Boolean,
    compact: Boolean,
    onSetChord: (ChordDefinition) -> Unit,
    onSetMode: (PassThroughMode) -> Unit,
    onToggleAudio: () -> Unit,
    onOpenSynth: () -> Unit,
    onOpenConsole: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RibbonValue(
                label = stringResource(R.string.performance_range),
                value = "${midiNoteName(range.min)}–${midiNoteName(range.max)}",
            )
            ActiveNotesRibbonValue(
                label = if (solfegeWrap) {
                    stringResource(R.string.performance_wrap)
                } else {
                    stringResource(R.string.performance_clamp)
                },
                state = activeNotesState,
                modifier = Modifier.weight(1f),
            )
            if (compact) {
                ChoiceSelector(
                    label = stringResource(R.string.performance_chord),
                    selected = chord,
                    options = ChordLibrary.all,
                    optionLabel = { it.displayName },
                    onSelect = onSetChord,
                    modifier = Modifier.width(160.dp),
                    compact = true,
                )
                ChoiceSelector(
                    label = stringResource(R.string.performance_routing),
                    selected = mode,
                    options = PassThroughMode.entries,
                    optionLabel = { modeLabel(it) },
                    onSelect = onSetMode,
                    modifier = Modifier.width(180.dp),
                    compact = true,
                )
            } else {
                Text(
                    audioLabel(audioAvailable = audioAvailable, audioRunning = audioRunning),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (audioRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onToggleAudio, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(if (audioMonitorEnabled) "Audio −" else "Audio +")
            }
            if (!performanceLock) {
                OutlinedButton(
                    onClick = onOpenSynth,
                    enabled = synthEnabled,
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .testTag(SynthPanelOpenTestTag),
                    shape = ControlShape,
                ) {
                    Text(stringResource(R.string.performance_open_synth))
                }
            }
            OutlinedButton(
                onClick = onOpenConsole,
                modifier = Modifier.heightIn(min = 56.dp),
                shape = ControlShape,
            ) {
                Text(
                    if (performanceLock) {
                        stringResource(R.string.performance_unlock)
                    } else {
                        stringResource(R.string.performance_open_console)
                    },
                )
            }
        }
    }
}

@Composable
private fun ActiveNotesRibbonValue(
    label: String,
    state: State<PerformanceActiveNotesUiState>,
    modifier: Modifier = Modifier,
) {
    val count = state.value.count
    RibbonValue(
        label = label,
        value = pluralStringResource(R.plurals.performance_active_notes, count, count),
        modifier = modifier,
    )
}

@Composable
private fun RibbonValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun StatusBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.performance_dismiss_status))
            }
        }
    }
}

private data class SynthControlSpec(
    val key: String,
    val label: String,
    val valueText: String,
    val sliderValue: Float,
    val sliderRange: ClosedFloatingPointRange<Float>,
    val applyValue: (SynthPatch, Float) -> SynthPatch,
)

private data class SynthDiagnosticSpec(
    val label: String,
    val value: String,
)

private class SynthPreviewFrameScheduler {
    var pendingPatch: SynthPatch? = null
    var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
        pendingPatch = null
    }
}

@Composable
private fun SynthPanel(
    state: PerformanceSynthUiState,
    compact: Boolean,
    onClose: () -> Unit,
    onPatchPreview: (SynthPatch) -> Unit,
    onPatchChangeFinished: (SynthPatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draftPatchState = remember { mutableStateOf(state.patch) }
    val gestureActiveState = remember { mutableStateOf(false) }
    var draftPatch by draftPatchState
    var gestureActive by gestureActiveState
    val previewScope = rememberCoroutineScope()
    val previewScheduler = remember { SynthPreviewFrameScheduler() }
    val currentOnPatchPreview = rememberUpdatedState(onPatchPreview)
    val currentOnPatchChangeFinished = rememberUpdatedState(onPatchChangeFinished)
    DisposableEffect(Unit) {
        onDispose {
            previewScheduler.cancel()
            if (gestureActiveState.value) {
                currentOnPatchChangeFinished.value(draftPatchState.value)
            }
        }
    }
    LaunchedEffect(state.patch) {
        if (!gestureActive) draftPatch = state.patch
    }

    val filterControls = listOf(
        SynthControlSpec(
            key = "timbre",
            label = stringResource(R.string.synth_timbre),
            valueText = stringResource(
                R.string.synth_value_percent,
                (draftPatch.pulseMix * 100f).roundToInt(),
            ),
            sliderValue = draftPatch.pulseMix,
            sliderRange = SynthParameter.PULSE_MIX.minimum..SynthParameter.PULSE_MIX.maximum,
            applyValue = SynthPatch::withTimbre,
        ),
        logarithmicSynthControl(
            key = SynthParameter.CUTOFF.name,
            label = stringResource(R.string.synth_cutoff),
            valueText = stringResource(
                R.string.synth_value_hz,
                draftPatch.cutoffHz.coerceAtMost(SynthCutoffUiMaximumHz).roundToInt(),
            ),
            patch = draftPatch,
            parameter = SynthParameter.CUTOFF,
            maximum = SynthCutoffUiMaximumHz,
        ),
        directSynthControl(
            key = SynthParameter.RESONANCE.name,
            label = stringResource(R.string.synth_resonance),
            valueText = stringResource(
                R.string.synth_value_percent,
                (draftPatch.resonance * 100f).roundToInt(),
            ),
            patch = draftPatch,
            parameter = SynthParameter.RESONANCE,
        ),
    )
    val envelopeControls = listOf(
        logarithmicSynthControl(
            key = SynthParameter.ATTACK.name,
            label = stringResource(R.string.synth_attack),
            valueText = stringResource(R.string.synth_value_seconds, draftPatch.attackSeconds),
            patch = draftPatch,
            parameter = SynthParameter.ATTACK,
        ),
        logarithmicSynthControl(
            key = SynthParameter.DECAY.name,
            label = stringResource(R.string.synth_decay),
            valueText = stringResource(R.string.synth_value_seconds, draftPatch.decaySeconds),
            patch = draftPatch,
            parameter = SynthParameter.DECAY,
        ),
        directSynthControl(
            key = SynthParameter.SUSTAIN.name,
            label = stringResource(R.string.synth_sustain),
            valueText = stringResource(
                R.string.synth_value_percent,
                (draftPatch.sustain * 100f).roundToInt(),
            ),
            patch = draftPatch,
            parameter = SynthParameter.SUSTAIN,
        ),
        logarithmicSynthControl(
            key = SynthParameter.RELEASE.name,
            label = stringResource(R.string.synth_release),
            valueText = stringResource(R.string.synth_value_seconds, draftPatch.releaseSeconds),
            patch = draftPatch,
            parameter = SynthParameter.RELEASE,
        ),
    )
    val filterEnvelopeControls = listOf(
        logarithmicSynthControl(
            key = SynthParameter.FILTER_ATTACK.name,
            label = "Filtre · attaque",
            valueText = stringResource(R.string.synth_value_seconds, draftPatch.filterAttackSeconds),
            patch = draftPatch,
            parameter = SynthParameter.FILTER_ATTACK,
        ),
        logarithmicSynthControl(
            key = SynthParameter.FILTER_DECAY.name,
            label = "Filtre · déclin",
            valueText = stringResource(R.string.synth_value_seconds, draftPatch.filterDecaySeconds),
            patch = draftPatch,
            parameter = SynthParameter.FILTER_DECAY,
        ),
        directSynthControl(
            key = SynthParameter.FILTER_SUSTAIN.name,
            label = "Filtre · maintien",
            valueText = stringResource(
                R.string.synth_value_percent,
                (draftPatch.filterSustain * 100f).roundToInt(),
            ),
            patch = draftPatch,
            parameter = SynthParameter.FILTER_SUSTAIN,
        ),
        logarithmicSynthControl(
            key = SynthParameter.FILTER_RELEASE.name,
            label = "Filtre · relâchement",
            valueText = stringResource(R.string.synth_value_seconds, draftPatch.filterReleaseSeconds),
            patch = draftPatch,
            parameter = SynthParameter.FILTER_RELEASE,
        ),
        directSynthControl(
            key = SynthParameter.FILTER_ENV_AMOUNT.name,
            label = "Montant enveloppe",
            valueText = draftPatch.filterEnvelopeAmount.toString() + " oct",
            patch = draftPatch,
            parameter = SynthParameter.FILTER_ENV_AMOUNT,
        ),
    )
    val lfoControls = listOf(
        logarithmicSynthControl(
            key = SynthParameter.LFO_RATE.name,
            label = "LFO · fréquence",
            valueText = draftPatch.lfoRateHz.toString() + " Hz",
            patch = draftPatch,
            parameter = SynthParameter.LFO_RATE,
        ),
        directSynthControl(
            key = SynthParameter.LFO_DEPTH.name,
            label = "LFO · profondeur",
            valueText = stringResource(
                R.string.synth_value_percent,
                (draftPatch.lfoDepth * 100f).roundToInt(),
            ),
            patch = draftPatch,
            parameter = SynthParameter.LFO_DEPTH,
        ),
        directSynthControl(
            key = SynthParameter.LFO_DELAY.name,
            label = "LFO · délai",
            valueText = stringResource(R.string.synth_value_seconds, draftPatch.lfoDelaySeconds),
            patch = draftPatch,
            parameter = SynthParameter.LFO_DELAY,
        ),
    )
    val effectControls = listOf(
        directSynthControl(
            key = SynthParameter.CHORUS_MIX.name,
            label = stringResource(R.string.synth_chorus),
            valueText = stringResource(
                R.string.synth_value_percent,
                (draftPatch.chorusMix * 100f).roundToInt(),
            ),
            patch = draftPatch,
            parameter = SynthParameter.CHORUS_MIX,
        ),
        logarithmicSynthControl(
            key = SynthParameter.DELAY_TIME.name,
            label = stringResource(R.string.synth_delay_time),
            valueText = stringResource(
                R.string.synth_value_seconds,
                draftPatch.delayTimeSeconds,
            ),
            patch = draftPatch,
            parameter = SynthParameter.DELAY_TIME,
        ),
        directSynthControl(
            key = SynthParameter.DELAY_FEEDBACK.name,
            label = stringResource(R.string.synth_delay_feedback),
            valueText = stringResource(
                R.string.synth_value_percent,
                (draftPatch.delayFeedback * 100f).roundToInt(),
            ),
            patch = draftPatch,
            parameter = SynthParameter.DELAY_FEEDBACK,
        ),
        directSynthControl(
            key = SynthParameter.DELAY_MIX.name,
            label = stringResource(R.string.synth_delay_mix),
            valueText = stringResource(
                R.string.synth_value_percent,
                (draftPatch.delayMix * 100f).roundToInt(),
            ),
            patch = draftPatch,
            parameter = SynthParameter.DELAY_MIX,
        ),
        directSynthControl(
            key = SynthParameter.DELAY_SYNC_BEATS.name,
            label = "Delay · rythme",
            valueText = if (draftPatch.delaySyncBeats == 0f) {
                "Libre"
            } else {
                draftPatch.delaySyncBeats.toString() + " temps"
            },
            patch = draftPatch,
            parameter = SynthParameter.DELAY_SYNC_BEATS,
        ),
        directSynthControl(
            key = SynthParameter.REVERB_MIX.name,
            label = stringResource(R.string.synth_reverb),
            valueText = stringResource(
                R.string.synth_value_percent,
                (draftPatch.reverbMix * 100f).roundToInt(),
            ),
            patch = draftPatch,
            parameter = SynthParameter.REVERB_MIX,
        ),
        directSynthControl(
            key = SynthParameter.MASTER.name,
            label = stringResource(R.string.synth_master),
            valueText = stringResource(
                R.string.synth_value_percent,
                (draftPatch.masterGain * 100f).roundToInt(),
            ),
            patch = draftPatch,
            parameter = SynthParameter.MASTER,
        ),
        directSynthControl(
            key = SynthParameter.DRIVE.name,
            label = "Drive de sortie",
            valueText = stringResource(
                R.string.synth_value_percent,
                (draftPatch.drive * 100f).roundToInt(),
            ),
            patch = draftPatch,
            parameter = SynthParameter.DRIVE,
        ),
    )

    val updateDraft: (SynthControlSpec, Float) -> Unit = { control, value ->
        gestureActive = true
        val updatedPatch = control.applyValue(draftPatch, value)
        draftPatch = updatedPatch
        previewScheduler.pendingPatch = updatedPatch
        if (previewScheduler.job == null) {
            previewScheduler.job = previewScope.launch {
                try {
                    while (previewScheduler.pendingPatch != null) {
                        withFrameNanos { }
                        val previewPatch = previewScheduler.pendingPatch ?: continue
                        previewScheduler.pendingPatch = null
                        currentOnPatchPreview.value(previewPatch)
                    }
                } finally {
                    previewScheduler.job = null
                }
            }
        }
    }
    val finishGesture: () -> Unit = {
        val finalPreview = previewScheduler.pendingPatch
        previewScheduler.cancel()
        if (finalPreview != null) currentOnPatchPreview.value(finalPreview)
        gestureActive = false
        currentOnPatchChangeFinished.value(draftPatch)
    }
    val applyImmediatePatch: (SynthPatch) -> Unit = { patch ->
        previewScheduler.cancel()
        gestureActive = false
        draftPatch = patch
        currentOnPatchPreview.value(patch)
        currentOnPatchChangeFinished.value(patch)
    }

    Surface(
        modifier = modifier.testTag(SynthPanelTestTag),
        shape = StageShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
        tonalElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.synth_panel_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        stringResource(R.string.synth_panel_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.synth_panel_close))
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(SynthPresetLibrary.all, key = { it.id }) { preset ->
                    FilterChip(
                        selected = preset.patch == draftPatch,
                        onClick = {
                            applyImmediatePatch(
                                preset.patch.withParameter(
                                    SynthParameter.TEMPO_BPM,
                                    draftPatch.tempoBpm,
                                ),
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text(preset.displayName) },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle(stringResource(R.string.synth_section_timbre_filter))
                SynthControlGrid(
                    controls = filterControls,
                    compact = compact,
                    onValueChange = updateDraft,
                    onValueChangeFinished = finishGesture,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SectionTitle("Enveloppe du filtre")
                SynthControlGrid(
                    controls = filterEnvelopeControls,
                    compact = compact,
                    onValueChange = updateDraft,
                    onValueChangeFinished = finishGesture,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SectionTitle("Enveloppe d’amplitude")
                SynthControlGrid(
                    controls = envelopeControls,
                    compact = compact,
                    onValueChange = updateDraft,
                    onValueChangeFinished = finishGesture,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SectionTitle("LFO assignable")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SynthLfoDestination.entries.forEach { destination ->
                        val label = when (destination) {
                            SynthLfoDestination.FILTER -> "Filtre"
                            SynthLfoDestination.PULSE_WIDTH -> "Largeur pulse"
                            SynthLfoDestination.DELAY_TIME -> "Temps delay"
                        }
                        FilterChip(
                            selected = draftPatch.lfoDestination == destination,
                            onClick = { applyImmediatePatch(draftPatch.copy(lfoDestination = destination)) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            label = { Text(label) },
                        )
                    }
                }
                SynthControlGrid(
                    controls = lfoControls,
                    compact = compact,
                    onValueChange = updateDraft,
                    onValueChangeFinished = finishGesture,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SectionTitle(stringResource(R.string.synth_section_effects))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { beats ->
                        FilterChip(
                            selected = draftPatch.delaySyncBeats == beats,
                            onClick = {
                                applyImmediatePatch(
                                    draftPatch.withParameter(SynthParameter.DELAY_SYNC_BEATS, beats),
                                )
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            label = {
                                Text(
                                    when (beats) {
                                        0f -> "Libre"
                                        0.25f -> "1/16"
                                        0.5f -> "1/8"
                                        0.75f -> "1/8."
                                        else -> "1/4"
                                    },
                                )
                            },
                        )
                    }
                }
                SynthControlGrid(
                    controls = effectControls,
                    compact = compact,
                    onValueChange = updateDraft,
                    onValueChangeFinished = finishGesture,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SectionTitle(stringResource(R.string.synth_section_diagnostics))
                SynthDiagnostics(
                    diagnostics = state.diagnostics,
                    compact = compact,
                )
            }
        }
    }
}

private fun directSynthControl(
    key: String,
    label: String,
    valueText: String,
    patch: SynthPatch,
    parameter: SynthParameter,
): SynthControlSpec = SynthControlSpec(
    key = key,
    label = label,
    valueText = valueText,
    sliderValue = patch[parameter],
    sliderRange = parameter.minimum..parameter.maximum,
    applyValue = { currentPatch, value -> currentPatch.withParameter(parameter, value) },
)

private fun logarithmicSynthControl(
    key: String,
    label: String,
    valueText: String,
    patch: SynthPatch,
    parameter: SynthParameter,
    maximum: Float = parameter.maximum,
): SynthControlSpec = SynthControlSpec(
    key = key,
    label = label,
    valueText = valueText,
    sliderValue = logarithmicSliderPosition(
        value = patch[parameter],
        minimum = parameter.minimum,
        maximum = maximum,
    ),
    sliderRange = 0f..1f,
    applyValue = { currentPatch, position ->
        currentPatch.withParameter(
            parameter,
            logarithmicSliderValue(
                position = position,
                minimum = parameter.minimum,
                maximum = maximum,
            ),
        )
    },
)

internal fun logarithmicSliderPosition(value: Float, minimum: Float, maximum: Float): Float {
    require(minimum > 0f && maximum > minimum)
    val clamped = value.coerceIn(minimum, maximum).toDouble()
    val minimumLog = ln(minimum.toDouble())
    val span = ln(maximum.toDouble()) - minimumLog
    return ((ln(clamped) - minimumLog) / span).toFloat().coerceIn(0f, 1f)
}

internal fun logarithmicSliderValue(position: Float, minimum: Float, maximum: Float): Float {
    require(minimum > 0f && maximum > minimum)
    val minimumLog = ln(minimum.toDouble())
    val span = ln(maximum.toDouble()) - minimumLog
    return exp(minimumLog + position.coerceIn(0f, 1f) * span).toFloat()
}

@Composable
private fun SynthControlGrid(
    controls: List<SynthControlSpec>,
    compact: Boolean,
    onValueChange: (SynthControlSpec, Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val columnCount = if (compact) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        controls.chunked(columnCount).forEach { rowControls ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowControls.forEach { control ->
                    SynthSliderControl(
                        control = control,
                        onValueChange = { value -> onValueChange(control, value) },
                        onValueChangeFinished = onValueChangeFinished,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!compact && rowControls.size < columnCount) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SynthSliderControl(
    control: SynthControlSpec,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                control.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                control.valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        Slider(
            value = control.sliderValue.coerceIn(control.sliderRange),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = control.sliderRange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(synthSliderTestTag(control.key))
                .semantics {
                    contentDescription = control.label
                    stateDescription = control.valueText
                },
        )
    }
}

@Composable
private fun SynthDiagnostics(
    diagnostics: AudioDiagnostics,
    compact: Boolean,
) {
    val unavailable = stringResource(R.string.synth_diagnostics_unavailable)
    val streamState = when {
        diagnostics.recoveryPending -> stringResource(R.string.synth_stream_recovering)
        diagnostics.streamRunning -> stringResource(R.string.synth_stream_running)
        else -> stringResource(R.string.synth_stream_stopped)
    }
    val metrics = listOf(
        SynthDiagnosticSpec(stringResource(R.string.synth_stream), streamState),
        SynthDiagnosticSpec(
            stringResource(R.string.synth_sample_rate),
            diagnostics.sampleRate.takeIf { it > 0 }
                ?.let { stringResource(R.string.synth_value_hz, it) }
                ?: unavailable,
        ),
        SynthDiagnosticSpec(
            stringResource(R.string.synth_frames_per_burst),
            diagnostics.framesPerBurst.takeIf { it > 0 }?.toString() ?: unavailable,
        ),
        SynthDiagnosticSpec(
            stringResource(R.string.synth_buffer_frames),
            diagnostics.bufferSizeFrames.takeIf { it > 0 }?.toString() ?: unavailable,
        ),
        SynthDiagnosticSpec(
            stringResource(R.string.synth_queue),
            stringResource(
                R.string.synth_queue_value,
                diagnostics.currentQueueDepth,
                diagnostics.maximumQueueDepth,
            ),
        ),
        SynthDiagnosticSpec(stringResource(R.string.synth_xruns), diagnostics.xRunCount.toString()),
        SynthDiagnosticSpec(
            stringResource(R.string.synth_dropped_events),
            diagnostics.droppedEvents.toString(),
        ),
        SynthDiagnosticSpec(
            stringResource(R.string.synth_restarts),
            diagnostics.restartCount.toString(),
        ),
        SynthDiagnosticSpec(
            stringResource(R.string.synth_last_error),
            diagnostics.lastErrorCode.takeIf { it != 0 }?.toString()
                ?: stringResource(R.string.synth_no_error),
        ),
    )
    val columnCount = if (compact) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(columnCount).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowMetrics.forEach { metric ->
                    SynthDiagnosticMetric(metric = metric, modifier = Modifier.weight(1f))
                }
                repeat(columnCount - rowMetrics.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SynthDiagnosticMetric(
    metric: SynthDiagnosticSpec,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 60.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${metric.label}, ${metric.value}"
            },
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                metric.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                metric.value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MidiConsole(
    state: PerformanceConsoleUiState,
    onClose: () -> Unit,
    onSetScale: (ScaleDefinition) -> Unit,
    onSetRoot: (Int) -> Unit,
    onSetChord: (ChordDefinition) -> Unit,
    onSetRange: (MidiNoteRange) -> Unit,
    onSetWrap: (Boolean) -> Unit,
    onSetInputChannel: (Int?) -> Unit,
    onSetOutputChannel: (Int) -> Unit,
    onSetMode: (PassThroughMode) -> Unit,
    onSelectSource: (MidiPortDescriptor?) -> Unit,
    onSelectDestination: (MidiPortDescriptor?) -> Unit,
    onResetMidiMapping: () -> Unit,
    onOpenMidiMappingEditor: () -> Unit,
    onTogglePerformanceLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = StageShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
        tonalElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.midi_console_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        stringResource(R.string.midi_console_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.performance_close_console))
                }
            }

            SectionTitle(stringResource(R.string.configuration_instrument))
            ChoiceSelector(
                label = stringResource(R.string.performance_key),
                selected = state.config.rootPitchClass,
                options = (0..11).toList(),
                optionLabel = { rootName(it) },
                onSelect = onSetRoot,
            )
            ChoiceSelector(
                label = stringResource(R.string.performance_scale),
                selected = state.config.scale,
                options = ScaleLibrary.all,
                optionLabel = { it.displayName },
                onSelect = onSetScale,
            )
            ChoiceSelector(
                label = stringResource(R.string.performance_chord),
                selected = state.config.chord,
                options = ChordLibrary.all,
                optionLabel = { it.displayName },
                onSelect = onSetChord,
            )
            RangeEditor(range = state.config.range, onSetRange = onSetRange)
            SettingSwitch(
                label = stringResource(R.string.performance_wrap),
                detail = if (state.config.solfegeWrap) {
                    stringResource(R.string.interval_wrapped)
                } else {
                    stringResource(R.string.interval_clamped)
                },
                checked = state.config.solfegeWrap,
                onCheckedChange = onSetWrap,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionTitle(stringResource(R.string.midi_console_title))
            PortPicker(
                label = stringResource(R.string.midi_input),
                ports = state.sources,
                connection = state.sourceConnection,
                selectedPort = state.selectedSource,
                onSelect = onSelectSource,
            )
            PortPicker(
                label = stringResource(R.string.midi_output),
                ports = state.destinations,
                connection = state.destinationConnection,
                selectedPort = state.selectedDestination,
                onSelect = onSelectDestination,
            )
            ChoiceSelector(
                label = stringResource(R.string.midi_input_channel),
                selected = state.inputChannel,
                options = listOf<Int?>(null) + (0..15).toList(),
                optionLabel = { channel ->
                    channel?.let { stringResource(R.string.midi_channel_number, it + 1) }
                        ?: stringResource(R.string.midi_channel_omni)
                },
                onSelect = onSetInputChannel,
            )
            ChoiceSelector(
                label = stringResource(R.string.midi_output_channel),
                selected = state.outputChannel,
                options = (0..15).toList(),
                optionLabel = { stringResource(R.string.midi_channel_number, it + 1) },
                onSelect = onSetOutputChannel,
            )
            ChoiceSelector(
                label = stringResource(R.string.performance_routing),
                selected = state.passThroughMode,
                options = PassThroughMode.entries,
                optionLabel = { modeLabel(it) },
                onSelect = onSetMode,
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.midi_mapping),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (state.mappingCustomized) {
                        stringResource(R.string.midi_mapping_custom)
                    } else {
                        stringResource(R.string.midi_mapping_default)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    pluralStringResource(
                        R.plurals.midi_mapping_binding_count,
                        state.mappingCount,
                        state.mappingCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onOpenMidiMappingEditor,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = ControlShape,
                ) {
                    Text(stringResource(R.string.midi_mapping_edit))
                }
                OutlinedButton(
                    onClick = onResetMidiMapping,
                    enabled = state.mappingCustomized,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = ControlShape,
                ) {
                    Text(stringResource(R.string.midi_reset_mapping))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingSwitch(
                label = stringResource(R.string.performance_lock),
                detail = stringResource(R.string.performance_locked),
                checked = state.performanceLock,
                onCheckedChange = { onTogglePerformanceLock() },
            )
        }
    }
}

@Composable
private fun PortPicker(
    label: String,
    ports: List<MidiPortDescriptor>,
    connection: MidiConnectionState,
    selectedPort: MidiPortDescriptor?,
    onSelect: (MidiPortDescriptor?) -> Unit,
) {
    val selectedSession = (selectedPort ?: connection.descriptor)?.stableSessionId
    Column(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                connectionPhaseLabel(connection.phase),
                style = MaterialTheme.typography.labelMedium,
                color = connectionColor(connection.phase),
            )
        }
        PortChoice(
            label = stringResource(R.string.midi_no_port),
            selected = selectedSession == null,
            onClick = { onSelect(null) },
        )
        ports.forEach { port ->
            PortChoice(
                label = port.displayName,
                selected = selectedSession == port.stableSessionId,
                onClick = { onSelect(port) },
            )
        }
        if (ports.isEmpty()) {
            Text(
                stringResource(R.string.midi_ports_available),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        connection.error?.let { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PortChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RangeEditor(range: MidiNoteRange, onSetRange: (MidiNoteRange) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.performance_range),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RangeStepper(
            label = stringResource(R.string.configuration_minimum),
            note = range.min,
            canDecrease = range.min > 0,
            canIncrease = range.min < range.max,
            onDecrease = { onSetRange(MidiNoteRange(range.min - 1, range.max)) },
            onIncrease = { onSetRange(MidiNoteRange(range.min + 1, range.max)) },
        )
        RangeStepper(
            label = stringResource(R.string.configuration_maximum),
            note = range.max,
            canDecrease = range.max > range.min,
            canIncrease = range.max < 127,
            onDecrease = { onSetRange(MidiNoteRange(range.min, range.max - 1)) },
            onIncrease = { onSetRange(MidiNoteRange(range.min, range.max + 1)) },
        )
    }
}

@Composable
private fun RangeStepper(
    label: String,
    note: Int,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val decreaseDescription = stringResource(R.string.configuration_decrease)
    val increaseDescription = stringResource(R.string.configuration_increase)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${midiNoteName(note)} · $note", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onDecrease,
            enabled = canDecrease,
            modifier = Modifier
                .sizeIn(minWidth = 56.dp, minHeight = 56.dp)
                .semantics { contentDescription = decreaseDescription },
            shape = ControlShape,
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        OutlinedButton(
            onClick = onIncrease,
            enabled = canIncrease,
            modifier = Modifier
                .sizeIn(minWidth = 56.dp, minHeight = 56.dp)
                .semantics { contentDescription = increaseDescription },
            shape = ControlShape,
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Black,
    )
}

@Composable
private fun <T> ChoiceSelector(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 48.dp else 56.dp)
                .then(if (testTag == null) Modifier else Modifier.testTag(testTag)),
            shape = ControlShape,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    optionLabel(selected),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    modifier = Modifier.heightIn(min = if (compact) 48.dp else 56.dp),
                    enabled = enabled,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun StageBackdrop(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary.copy(alpha = 0.055f)
    val secondary = MaterialTheme.colorScheme.secondary.copy(alpha = 0.04f)
    Canvas(modifier) {
        drawCircle(primary, radius = size.minDimension * 0.66f, center = Offset(size.width * 0.58f, size.height * 0.48f))
        drawCircle(secondary, radius = size.minDimension * 0.42f, center = Offset(size.width * 0.24f, size.height * 0.72f))
        repeat(5) { index ->
            val y = size.height * (index + 1) / 6f
            drawLine(
                color = Color.White.copy(alpha = 0.018f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
        }
    }
}

private fun android.content.res.Resources.intervalActionDescription(
    steps: Int,
    target: String,
    boundary: PitchMoveBoundary,
): String {
    val magnitude = kotlin.math.abs(steps)
    val movement = when {
        steps == 0 -> getString(R.string.interval_repeat, target)
        steps > 0 -> getQuantityString(R.plurals.interval_up, magnitude, magnitude, target)
        else -> getQuantityString(R.plurals.interval_down, magnitude, magnitude, target)
    }
    val boundaryDescription = when (boundary) {
        PitchMoveBoundary.NONE -> null
        PitchMoveBoundary.CLAMPED -> getString(R.string.interval_clamped)
        PitchMoveBoundary.WRAPPED -> getString(R.string.interval_wrapped)
    }
    return listOfNotNull(movement, boundaryDescription).joinToString(separator = ". ")
}

private fun android.content.res.Resources.intervalPressedDescription(activeCount: Int): String {
    return if (activeCount > 0) {
        getQuantityString(R.plurals.interval_pressed, activeCount, activeCount)
    } else {
        getString(R.string.interval_released)
    }
}

internal fun intervalPadTestTag(steps: Int): String = "interval-pad:$steps"

@Composable
private fun modeLabel(mode: PassThroughMode): String {
    return when (mode) {
        PassThroughMode.OFF -> stringResource(R.string.midi_mode_off)
        PassThroughMode.ACTIVE -> stringResource(R.string.midi_mode_active)
        PassThroughMode.ACTIVE_LAST_NOTE -> stringResource(R.string.midi_mode_active_last_note)
        PassThroughMode.PASS_THRU -> stringResource(R.string.midi_mode_pass_thru)
    }
}

@Composable
private fun connectionPhaseLabel(phase: MidiConnectionPhase): String {
    return when (phase) {
        MidiConnectionPhase.CLOSED -> stringResource(R.string.midi_connection_closed)
        MidiConnectionPhase.OPENING -> stringResource(R.string.midi_connection_opening)
        MidiConnectionPhase.OPEN -> stringResource(R.string.midi_connection_open)
        MidiConnectionPhase.LOST -> stringResource(R.string.midi_connection_lost)
        MidiConnectionPhase.ERROR -> stringResource(R.string.midi_connection_error)
    }
}

@Composable
private fun connectionColor(phase: MidiConnectionPhase): Color {
    return when (phase) {
        MidiConnectionPhase.OPEN -> MaterialTheme.colorScheme.primary
        MidiConnectionPhase.OPENING -> MaterialTheme.colorScheme.secondary
        MidiConnectionPhase.ERROR,
        MidiConnectionPhase.LOST,
        -> MaterialTheme.colorScheme.error
        MidiConnectionPhase.CLOSED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun audioLabel(audioAvailable: Boolean, audioRunning: Boolean): String {
    return when {
        !audioAvailable -> stringResource(R.string.performance_audio_unavailable)
        audioRunning -> stringResource(R.string.performance_audio_on)
        else -> stringResource(R.string.performance_audio_off)
    }
}

private fun rootName(rootPitchClass: Int): String {
    return midiNoteName(rootPitchClass + 60).dropLast(1)
}
