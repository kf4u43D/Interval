package dev.intervaltablet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import dev.intervaltablet.AppUiState
import dev.intervaltablet.audio.AudioDiagnostics
import dev.intervaltablet.domain.ChordDefinition
import dev.intervaltablet.domain.InstrumentConfig
import dev.intervaltablet.domain.InstrumentState
import dev.intervaltablet.domain.MidiNoteRange
import dev.intervaltablet.domain.PadArticulation
import dev.intervaltablet.domain.PassThroughMode
import dev.intervaltablet.domain.PitchMoveBoundary
import dev.intervaltablet.domain.SynthPatch
import dev.intervaltablet.domain.ToneRowMode
import dev.intervaltablet.domain.midiNoteName
import dev.intervaltablet.domain.strumNotes
import dev.intervaltablet.midi.MidiConnectionState
import dev.intervaltablet.midi.MidiPortDescriptor

/** Values which change only when the musical anchor or its pitch configuration changes. */
@Immutable
internal data class PerformancePitchUiState(
    val currentNoteName: String,
    val currentDegree: Int?,
    val intervalPreviews: Map<Int, IntervalPadPreview>,
)

@Immutable
internal data class PerformanceHeaderUiState(
    val rootName: String,
    val scaleName: String,
    val chordName: String,
    val sourceConnection: MidiConnectionState,
    val destinationConnection: MidiConnectionState,
    val audioAvailable: Boolean,
    val audioRunning: Boolean,
    val performanceLock: Boolean,
)

@Immutable
internal data class PerformanceGridUiState(
    val activeStepCounts: Map<Int, Int>,
)

@Immutable
internal data class PerformancePadUiState(
    val target: String,
    val boundary: PitchMoveBoundary,
    val activeCount: Int,
)

@Stable
internal class PerformancePadUiProjection internal constructor(
    val steps: Int,
    val state: State<PerformancePadUiState>,
)

@Immutable
internal data class PerformanceUtilityUiState(
    val restartMode: Boolean,
)

@Immutable
internal data class PerformanceControlsUiState(
    val chord: ChordDefinition,
    val mode: PassThroughMode,
    val audioMonitorEnabled: Boolean,
    val audioAvailable: Boolean,
    val audioRunning: Boolean,
    val performanceLock: Boolean,
    val settingsLoaded: Boolean,
)

@Immutable
internal data class PerformanceRibbonUiState(
    val range: MidiNoteRange,
    val solfegeWrap: Boolean,
    val chord: ChordDefinition,
    val mode: PassThroughMode,
    val audioMonitorEnabled: Boolean,
    val audioAvailable: Boolean,
    val audioRunning: Boolean,
    val performanceLock: Boolean,
    val settingsLoaded: Boolean,
)

@Immutable
internal data class PerformanceActiveNotesUiState(val count: Int)

@Immutable
internal data class StrumToneUi(
    val note: Int,
    val label: String,
)

@Immutable
internal data class PerformanceArticulationUiState(
    val articulation: PadArticulation,
    val tones: List<StrumToneUi>,
    val defaultVelocity: Int,
)

@Immutable
internal data class PerformanceStatusUiState(val message: String?)

@Immutable
internal data class PerformanceLockUiState(val locked: Boolean)

/** Audio-only projection: native polling never invalidates the musical stage leaves. */
@Immutable
internal data class PerformanceSynthUiState(
    val patch: SynthPatch,
    val diagnostics: AudioDiagnostics,
)

@Immutable
internal data class PerformanceConsoleUiState(
    val config: InstrumentConfig,
    val sources: List<MidiPortDescriptor>,
    val destinations: List<MidiPortDescriptor>,
    val sourceConnection: MidiConnectionState,
    val destinationConnection: MidiConnectionState,
    val selectedSource: MidiPortDescriptor?,
    val selectedDestination: MidiPortDescriptor?,
    val inputChannel: Int?,
    val outputChannel: Int,
    val passThroughMode: PassThroughMode,
    val mappingCustomized: Boolean,
    val mappingCount: Int,
    val performanceLock: Boolean,
)

@Immutable
private data class PerformancePitchInputs(
    val config: InstrumentConfig,
    val currentNote: Int,
    val lastExternalNote: Int?,
)

@Immutable
private data class PerformanceArticulationInputs(
    val config: InstrumentConfig,
    val currentNote: Int,
)

/**
 * Stable handles passed through the static performance-stage shell.
 *
 * Each leaf observes only its own [State]. Explicit structural policies are important here:
 * the coordinator publishes a fresh [AppUiState] for Note On and Note Off, while most rendered
 * sections are unchanged for one of those transitions.
 */
@Stable
internal class PerformanceUiProjections internal constructor(
    val pitch: State<PerformancePitchUiState>,
    val header: State<PerformanceHeaderUiState>,
    val toneRowContent: State<ToneRowContentUiState>,
    val toneRowCursor: State<ToneRowCursorUiState>,
    val grid: State<PerformanceGridUiState>,
    val pads: List<PerformancePadUiProjection>,
    val utility: State<PerformanceUtilityUiState>,
    val controls: State<PerformanceControlsUiState>,
    val ribbon: State<PerformanceRibbonUiState>,
    val activeNotes: State<PerformanceActiveNotesUiState>,
    val articulation: State<PerformanceArticulationUiState>,
    val status: State<PerformanceStatusUiState>,
    val lock: State<PerformanceLockUiState>,
    val synth: State<PerformanceSynthUiState>,
    val console: State<PerformanceConsoleUiState>,
    val midiMappingEditor: State<MidiMappingEditorUiState>,
)

@Composable
internal fun rememberPerformanceUiProjections(
    appState: State<AppUiState>,
): PerformanceUiProjections {
    return remember(appState) {
        val pitchInputs = derivedStateOf(structuralEqualityPolicy()) {
            val state = appState.value
            PerformancePitchInputs(
                config = state.instrument.config,
                currentNote = state.instrument.currentNote,
                lastExternalNote = state.instrument.lastExternalNote,
            )
        }
        val activeSteps = derivedStateOf(structuralEqualityPolicy()) {
            appState.value.performance.activeStepsBySource
        }
        val articulationInputs = derivedStateOf(structuralEqualityPolicy()) {
            val instrument = appState.value.instrument
            PerformanceArticulationInputs(
                config = instrument.config,
                currentNote = instrument.currentNote,
            )
        }
        val toneRowContentInputs = derivedStateOf(structuralEqualityPolicy()) {
            appState.value.toToneRowContentInputs()
        }
        val pitch = derivedStateOf(structuralEqualityPolicy()) {
            pitchInputs.value.toPerformancePitchUiState()
        }
        val grid = derivedStateOf(structuralEqualityPolicy()) {
            activeSteps.value.toPerformanceGridUiState()
        }
        val toneRowContent = derivedStateOf(structuralEqualityPolicy()) {
            toneRowContentInputs.value.toToneRowContentUiState()
        }
        val toneRowCursor = derivedStateOf(structuralEqualityPolicy()) {
            appState.value.toToneRowCursorUiState()
        }
        val pads = PerformanceIntervalSteps.map { steps ->
            PerformancePadUiProjection(
                steps = steps,
                state = derivedStateOf(structuralEqualityPolicy()) {
                    val preview = pitch.value.intervalPreviews.getValue(steps)
                    PerformancePadUiState(
                        target = preview.target,
                        boundary = preview.boundary,
                        activeCount = grid.value.activeStepCounts[steps] ?: 0,
                    )
                },
            )
        }

        PerformanceUiProjections(
            pitch = pitch,
            header = derivedStateOf(structuralEqualityPolicy()) {
                appState.value.toPerformanceHeaderUiState()
            },
            toneRowContent = toneRowContent,
            toneRowCursor = toneRowCursor,
            grid = grid,
            pads = pads,
            utility = derivedStateOf(structuralEqualityPolicy()) {
                appState.value.toPerformanceUtilityUiState()
            },
            controls = derivedStateOf(structuralEqualityPolicy()) {
                appState.value.toPerformanceControlsUiState()
            },
            ribbon = derivedStateOf(structuralEqualityPolicy()) {
                appState.value.toPerformanceRibbonUiState()
            },
            activeNotes = derivedStateOf(structuralEqualityPolicy()) {
                appState.value.toPerformanceActiveNotesUiState()
            },
            articulation = derivedStateOf(structuralEqualityPolicy()) {
                articulationInputs.value.toPerformanceArticulationUiState()
            },
            status = derivedStateOf(structuralEqualityPolicy()) {
                PerformanceStatusUiState(appState.value.statusMessage)
            },
            lock = derivedStateOf(structuralEqualityPolicy()) {
                PerformanceLockUiState(appState.value.performanceLock)
            },
            synth = derivedStateOf(structuralEqualityPolicy()) {
                appState.value.toPerformanceSynthUiState()
            },
            console = derivedStateOf(structuralEqualityPolicy()) {
                appState.value.toPerformanceConsoleUiState()
            },
            midiMappingEditor = derivedStateOf(structuralEqualityPolicy()) {
                appState.value.toMidiMappingEditorUiState()
            },
        )
    }
}

private fun Map<dev.intervaltablet.domain.TriggerSource, Int>.toPerformanceGridUiState(): PerformanceGridUiState {
    return PerformanceGridUiState(activeStepCounts = values.groupingBy { it }.eachCount())
}

internal fun AppUiState.toPerformanceGridUiState(): PerformanceGridUiState {
    return performance.activeStepsBySource.toPerformanceGridUiState()
}

internal fun AppUiState.toPerformancePadUiState(steps: Int): PerformancePadUiState {
    require(steps in PerformanceIntervalSteps)
    val preview = toPerformancePitchUiState().intervalPreviews.getValue(steps)
    return PerformancePadUiState(
        target = preview.target,
        boundary = preview.boundary,
        activeCount = performance.activeStepCounts[steps] ?: 0,
    )
}

internal fun AppUiState.toPerformancePitchUiState(): PerformancePitchUiState {
    return PerformancePitchInputs(
        config = instrument.config,
        currentNote = instrument.currentNote,
        lastExternalNote = instrument.lastExternalNote,
    ).toPerformancePitchUiState()
}

private fun PerformancePitchInputs.toPerformancePitchUiState(): PerformancePitchUiState {
    val grid = config.grid()
    return PerformancePitchUiState(
        currentNoteName = dev.intervaltablet.domain.midiNoteName(currentNote),
        currentDegree = grid.degreeIndexOf(currentNote)?.plus(1),
        intervalPreviews = buildIntervalPadPreviews(
            grid = grid,
            currentNote = currentNote,
            lastExternalNote = lastExternalNote,
            steps = PerformanceIntervalSteps,
        ),
    )
}

internal fun AppUiState.toPerformanceHeaderUiState(): PerformanceHeaderUiState {
    return PerformanceHeaderUiState(
        rootName = rootName,
        scaleName = scaleName,
        chordName = chordName,
        sourceConnection = midi.sourceConnection,
        destinationConnection = midi.destinationConnection,
        audioAvailable = audioAvailable,
        audioRunning = audioRunning,
        performanceLock = performanceLock,
    )
}

internal fun AppUiState.toPerformanceUtilityUiState(): PerformanceUtilityUiState {
    val mode = performance.toneRow.mode
    return PerformanceUtilityUiState(
        restartMode = mode == ToneRowMode.MANUAL_PLAYBACK ||
            mode == ToneRowMode.AUTO_PLAYING ||
            mode == ToneRowMode.PAUSED,
    )
}

internal fun AppUiState.toPerformanceControlsUiState(): PerformanceControlsUiState {
    return PerformanceControlsUiState(
        chord = instrument.config.chord,
        mode = passThroughMode,
        audioMonitorEnabled = audioMonitorEnabled,
        audioAvailable = audioAvailable,
        audioRunning = audioRunning,
        performanceLock = performanceLock,
        settingsLoaded = settingsLoaded,
    )
}

internal fun AppUiState.toPerformanceRibbonUiState(): PerformanceRibbonUiState {
    return PerformanceRibbonUiState(
        range = instrument.config.range,
        solfegeWrap = instrument.config.solfegeWrap,
        chord = instrument.config.chord,
        mode = passThroughMode,
        audioMonitorEnabled = audioMonitorEnabled,
        audioAvailable = audioAvailable,
        audioRunning = audioRunning,
        performanceLock = performanceLock,
        settingsLoaded = settingsLoaded,
    )
}

internal fun AppUiState.toPerformanceActiveNotesUiState(): PerformanceActiveNotesUiState {
    return PerformanceActiveNotesUiState(count = instrument.activeInstanceCount)
}

internal fun AppUiState.toPerformanceArticulationUiState(): PerformanceArticulationUiState {
    return PerformanceArticulationInputs(
        config = instrument.config,
        currentNote = instrument.currentNote,
    ).toPerformanceArticulationUiState()
}

internal fun AppUiState.toPerformanceSynthUiState(): PerformanceSynthUiState {
    return PerformanceSynthUiState(
        patch = synthPatch,
        diagnostics = audioDiagnostics,
    )
}

private fun PerformanceArticulationInputs.toPerformanceArticulationUiState(): PerformanceArticulationUiState {
    val instrument = InstrumentState(config = config, currentNote = currentNote)
    return PerformanceArticulationUiState(
        articulation = config.padArticulation,
        tones = instrument.strumNotes().map { note ->
            StrumToneUi(note = note, label = midiNoteName(note))
        },
        defaultVelocity = config.defaultVelocity,
    )
}

internal fun AppUiState.toPerformanceConsoleUiState(): PerformanceConsoleUiState {
    return PerformanceConsoleUiState(
        config = instrument.config,
        sources = midi.sources,
        destinations = midi.destinations,
        sourceConnection = midi.sourceConnection,
        destinationConnection = midi.destinationConnection,
        selectedSource = midi.selectedSource,
        selectedDestination = midi.selectedDestination,
        inputChannel = inputChannel,
        outputChannel = outputChannel,
        passThroughMode = passThroughMode,
        mappingCustomized = mappingCustomized,
        mappingCount = performance.mapping.bindings.size,
        performanceLock = performanceLock,
    )
}
