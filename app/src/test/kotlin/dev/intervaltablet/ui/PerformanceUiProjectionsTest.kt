package dev.intervaltablet.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import dev.intervaltablet.AppUiState
import dev.intervaltablet.audio.AudioDiagnostics
import dev.intervaltablet.domain.ActiveNoteInstance
import dev.intervaltablet.domain.ChordLibrary
import dev.intervaltablet.domain.PadArticulation
import dev.intervaltablet.domain.SynthParameter
import dev.intervaltablet.domain.SynthPatch
import dev.intervaltablet.domain.ToneRowEntry
import dev.intervaltablet.domain.ToneRowMode
import dev.intervaltablet.domain.ToneRowState
import dev.intervaltablet.domain.TriggerSource
import dev.intervaltablet.domain.strumNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PerformanceUiProjectionsTest {
    @Test
    fun diagnosticsOnlyUpdateChangesOnlyTheSynthProjection() {
        val initial = AppUiState()
        val diagnosticsOnly = initial.copy(
            audioDiagnostics = AudioDiagnostics(
                sampleRate = 48_000,
                framesPerBurst = 96,
                xRunCount = 3,
                droppedEvents = 2,
            ),
        )

        assertEquals(initial.toPerformancePitchUiState(), diagnosticsOnly.toPerformancePitchUiState())
        assertEquals(initial.toPerformanceHeaderUiState(), diagnosticsOnly.toPerformanceHeaderUiState())
        assertEquals(initial.toToneRowUiState(), diagnosticsOnly.toToneRowUiState())
        assertEquals(initial.toToneRowContentInputs(), diagnosticsOnly.toToneRowContentInputs())
        assertEquals(
            initial.toToneRowContentInputs().toToneRowContentUiState(),
            diagnosticsOnly.toToneRowContentInputs().toToneRowContentUiState(),
        )
        assertEquals(initial.toPerformanceGridUiState(), diagnosticsOnly.toPerformanceGridUiState())
        assertEquals(initial.toPerformanceUtilityUiState(), diagnosticsOnly.toPerformanceUtilityUiState())
        assertEquals(initial.toPerformanceControlsUiState(), diagnosticsOnly.toPerformanceControlsUiState())
        assertEquals(initial.toPerformanceRibbonUiState(), diagnosticsOnly.toPerformanceRibbonUiState())
        assertEquals(initial.toPerformanceActiveNotesUiState(), diagnosticsOnly.toPerformanceActiveNotesUiState())
        assertEquals(initial.toPerformanceArticulationUiState(), diagnosticsOnly.toPerformanceArticulationUiState())
        assertEquals(initial.toPerformanceConsoleUiState(), diagnosticsOnly.toPerformanceConsoleUiState())
        assertNotEquals(initial.toPerformanceSynthUiState(), diagnosticsOnly.toPerformanceSynthUiState())
    }

    @Test
    fun synthProjectionCarriesPatchAndDiagnosticsWithoutInvalidatingTheStage() {
        val initial = AppUiState()
        val patch = SynthPatch()
            .withTimbre(0.72F)
            .withParameter(SynthParameter.CUTOFF, 8_400F)
            .withParameter(SynthParameter.REVERB_MIX, 0.44F)
        val diagnostics = AudioDiagnostics(
            sampleRate = 48_000,
            framesPerBurst = 192,
            currentQueueDepth = 3,
            maximumQueueDepth = 11,
            streamRunning = true,
        )
        val changed = initial.copy(synthPatch = patch, audioDiagnostics = diagnostics)

        assertEquals(
            PerformanceSynthUiState(patch = patch, diagnostics = diagnostics),
            changed.toPerformanceSynthUiState(),
        )
        assertNotEquals(initial.toPerformanceSynthUiState(), changed.toPerformanceSynthUiState())
        assertEquals(initial.toPerformancePitchUiState(), changed.toPerformancePitchUiState())
        assertEquals(initial.toPerformanceHeaderUiState(), changed.toPerformanceHeaderUiState())
        assertEquals(initial.toToneRowContentInputs(), changed.toToneRowContentInputs())
        assertEquals(initial.toToneRowCursorUiState(), changed.toToneRowCursorUiState())
        assertEquals(initial.toPerformanceGridUiState(), changed.toPerformanceGridUiState())
        assertEquals(initial.toPerformanceArticulationUiState(), changed.toPerformanceArticulationUiState())
        assertEquals(initial.toPerformanceConsoleUiState(), changed.toPerformanceConsoleUiState())
    }

    @Test
    fun settingsLoadStateEnablesOnlyControlSurfacesThatCanOpenTheSynth() {
        val loading = AppUiState(settingsLoaded = false)
        val loaded = loading.copy(settingsLoaded = true)

        assertEquals(false, loading.toPerformanceControlsUiState().settingsLoaded)
        assertEquals(true, loaded.toPerformanceControlsUiState().settingsLoaded)
        assertEquals(false, loading.toPerformanceRibbonUiState().settingsLoaded)
        assertEquals(true, loaded.toPerformanceRibbonUiState().settingsLoaded)
        assertEquals(loading.toPerformancePitchUiState(), loaded.toPerformancePitchUiState())
        assertEquals(loading.toPerformanceSynthUiState(), loaded.toPerformanceSynthUiState())
    }

    @Test
    fun automaticCursorTickKeepsFormattedToneRowContentEqual() {
        val entries = listOf(
            ToneRowEntry(relativeDegree = 0, recordedMidiNote = 60, velocity = 64),
            ToneRowEntry(relativeDegree = 1, recordedMidiNote = 62, velocity = 65),
        )
        val initial = AppUiState(
            performance = dev.intervaltablet.PerformanceCoordinatorState.initial().copy(
                toneRow = ToneRowState(
                    mode = ToneRowMode.AUTO_PLAYING,
                    entries = entries,
                    rowIndex = 0,
                    intervalSequence = listOf(1, -1),
                    sequenceIndex = 0,
                ),
            ),
            settingsLoaded = true,
        )
        val ticked = initial.copy(
            performance = initial.performance.copy(
                toneRow = initial.performance.toneRow.copy(rowIndex = 1, sequenceIndex = 1),
            ),
        )

        assertEquals(initial.toToneRowContentInputs(), ticked.toToneRowContentInputs())
        assertEquals(
            initial.toToneRowContentInputs().toToneRowContentUiState(),
            ticked.toToneRowContentInputs().toToneRowContentUiState(),
        )
        assertNotEquals(initial.toToneRowCursorUiState(), ticked.toToneRowCursorUiState())
    }

    @Test
    fun toneRowInputGateDoesNotReformatContentForCursorOnlyUpdate() {
        val entries = listOf(
            ToneRowEntry(relativeDegree = 0, recordedMidiNote = 60, velocity = 64),
            ToneRowEntry(relativeDegree = 1, recordedMidiNote = 62, velocity = 65),
        )
        val initial = AppUiState(
            performance = dev.intervaltablet.PerformanceCoordinatorState.initial().copy(
                toneRow = ToneRowState(
                    mode = ToneRowMode.AUTO_PLAYING,
                    entries = entries,
                    rowIndex = 0,
                    intervalSequence = listOf(1, -1),
                    sequenceIndex = 0,
                ),
            ),
            settingsLoaded = true,
        )
        val source = mutableStateOf(initial)
        val inputs = derivedStateOf(structuralEqualityPolicy()) {
            source.value.toToneRowContentInputs()
        }
        var formatCount = 0
        val content = derivedStateOf(structuralEqualityPolicy()) {
            formatCount += 1
            inputs.value.toToneRowContentUiState()
        }

        val before = content.value
        source.value = initial.copy(
            performance = initial.performance.copy(
                toneRow = initial.performance.toneRow.copy(rowIndex = 1, sequenceIndex = 1),
            ),
        )
        val after = content.value

        assertEquals(before, after)
        assertEquals(1, formatCount)
    }

    @Test
    fun automaticGateReleaseChangesOnlyActiveNotesProjection() {
        val initial = AppUiState()
        val source = TriggerSource.System(id = "projection-auto-release")
        val active = initial.copy(
            performance = initial.performance.copy(
                instrument = initial.instrument.copy(
                    activeBySource = mapOf(
                        source to listOf(
                            ActiveNoteInstance(
                                note = initial.instrument.currentNote,
                                velocity = 64,
                                channel = initial.instrument.config.outputChannel,
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            PerformanceIntervalSteps.map(initial::toPerformancePadUiState),
            PerformanceIntervalSteps.map(active::toPerformancePadUiState),
        )
        assertEquals(initial.toToneRowContentInputs(), active.toToneRowContentInputs())
        assertEquals(initial.toToneRowCursorUiState(), active.toToneRowCursorUiState())
        assertNotEquals(
            initial.toPerformanceActiveNotesUiState(),
            active.toPerformanceActiveNotesUiState(),
        )
    }

    @Test
    fun scheduledReleaseOnlyChangesGridActivityAndRibbonCount() {
        val initial = AppUiState()
        val source = TriggerSource.System(id = "projection-release-17")
        val activeInstrument = initial.instrument.copy(
            activeBySource = mapOf(
                source to listOf(
                    ActiveNoteInstance(
                        note = initial.instrument.currentNote,
                        velocity = 64,
                        channel = initial.instrument.config.outputChannel,
                    ),
                ),
            ),
        )
        val active = initial.copy(
            performance = initial.performance.copy(
                instrument = activeInstrument,
                activeStepsBySource = mapOf(source to 2),
            ),
        )

        assertEquals(initial.toPerformancePitchUiState(), active.toPerformancePitchUiState())
        assertEquals(initial.toPerformanceHeaderUiState(), active.toPerformanceHeaderUiState())
        assertEquals(initial.toToneRowUiState(), active.toToneRowUiState())
        assertEquals(initial.toPerformanceUtilityUiState(), active.toPerformanceUtilityUiState())
        assertEquals(initial.toPerformanceControlsUiState(), active.toPerformanceControlsUiState())
        assertNotEquals(initial.toPerformanceGridUiState(), active.toPerformanceGridUiState())
        assertEquals(initial.toPerformanceRibbonUiState(), active.toPerformanceRibbonUiState())
        assertNotEquals(initial.toPerformanceActiveNotesUiState(), active.toPerformanceActiveNotesUiState())
    }

    @Test
    fun pitchProjectionContainsAllPadsFromOneSharedGridSnapshot() {
        val pitch = AppUiState().toPerformancePitchUiState()

        assertEquals(PerformanceIntervalSteps.toSet(), pitch.intervalPreviews.keys)
        assertEquals(9, pitch.intervalPreviews.size)
    }

    @Test
    fun articulationProjectionUsesDomainVoicingAndKeepsDuplicateTones() {
        val initial = AppUiState()
        val state = initial.copy(
            performance = initial.performance.copy(
                instrument = initial.instrument.copy(
                    config = initial.instrument.config.copy(
                        chord = ChordLibrary.third,
                        padArticulation = PadArticulation.MUTED,
                        defaultVelocity = 91,
                    ),
                ),
            ),
        )

        val projected = state.toPerformanceArticulationUiState()

        assertEquals(PadArticulation.MUTED, projected.articulation)
        assertEquals(91, projected.defaultVelocity)
        assertEquals(state.instrument.strumNotes(), projected.tones.map(StrumToneUi::note))
        assertEquals(projected.tones[1].note, projected.tones[2].note)
    }

    @Test
    fun manualActivityChangesOnlyTheMatchingPadProjection() {
        val initial = AppUiState()
        val source = TriggerSource.Touch(pointerId = 42L)
        val active = initial.copy(
            performance = initial.performance.copy(
                activeStepsBySource = mapOf(source to -3),
            ),
        )

        val changedSteps = PerformanceIntervalSteps.filter { steps ->
            initial.toPerformancePadUiState(steps) != active.toPerformancePadUiState(steps)
        }

        assertEquals(listOf(-3), changedSteps)
        assertEquals(1, active.toPerformancePadUiState(-3).activeCount)
    }
}
