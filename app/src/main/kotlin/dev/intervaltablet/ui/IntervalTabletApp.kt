package dev.intervaltablet.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.intervaltablet.IntervalTabletViewModel

@Composable
fun IntervalTabletApp(viewModel: IntervalTabletViewModel = viewModel()) {
    // Keep the collected State as a stable handle. Reading its value here would invalidate this
    // root and the complete stage shell on every scheduled Note Off.
    val appState = viewModel.uiState.collectAsStateWithLifecycle()
    val projections = rememberPerformanceUiProjections(appState)
    ProjectedPerformanceScreen(
        projections = projections,
        onToneRowIntent = viewModel::onToneRowIntent,
        onSetPadArticulation = viewModel::setPadArticulation,
        onStrumTone = viewModel::strumTone,
        onIntervalDown = viewModel::pressInterval,
        onIntervalUp = viewModel::releaseInterval,
        onIntervalOneShot = viewModel::triggerInterval,
        onUndo = viewModel::undo,
        onHome = viewModel::home,
        onPanic = viewModel::panic,
        onSetScale = viewModel::setScale,
        onSetRoot = viewModel::setRoot,
        onSetChord = viewModel::setChord,
        onSetForceToScale = viewModel::setForceToScale,
        onSetRange = viewModel::setRange,
        onSetWrap = viewModel::setWrap,
        onSetInputChannel = viewModel::setInputChannel,
        onSetOutputChannel = viewModel::setOutputChannel,
        onSetMode = viewModel::setPassThroughMode,
        onSelectSource = viewModel::selectSource,
        onSelectDestination = viewModel::selectDestination,
        onResetMidiMapping = viewModel::resetMidiMapping,
        onOpenMidiMappingEditor = viewModel::openMidiMappingEditor,
        onMidiMappingEditorAction = viewModel::onMidiMappingEditorAction,
        onSaveMidiMappingEditor = viewModel::saveMidiMappingEditor,
        onToggleAudio = viewModel::toggleAudioMonitor,
        onSynthPatchPreview = viewModel::previewSynthPatch,
        onSynthPatchChangeFinished = viewModel::setSynthPatch,
        onTogglePerformanceLock = viewModel::togglePerformanceLock,
        onDismissStatus = viewModel::dismissStatus,
    )
}
