package dev.intervaltablet.ui

import dev.intervaltablet.AppUiState
import dev.intervaltablet.PerformanceCoordinatorState
import dev.intervaltablet.data.PerformancePresetSnapshot
import dev.intervaltablet.data.PresetBank
import dev.intervaltablet.domain.ClockSource
import dev.intervaltablet.domain.ToneRowEntry
import dev.intervaltablet.domain.ToneRowMode
import dev.intervaltablet.domain.ToneRowPlayMode
import dev.intervaltablet.domain.ToneRowState
import dev.intervaltablet.domain.TransportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneRowUiAdapterTest {
    @Test
    fun adapterMapsDomainCursorsTransformsClockAndOneBasedPresetSlots() {
        val performance = PerformanceCoordinatorState.initial().copy(
            toneRow = ToneRowState(
                mode = ToneRowMode.PAUSED,
                entries = listOf(
                    ToneRowEntry(relativeDegree = 0, recordedMidiNote = 60, velocity = 70),
                    ToneRowEntry(relativeDegree = 4, recordedMidiNote = 67, velocity = 100),
                ),
                rowIndex = 1,
                intervalSequence = listOf(2, -1),
                sequenceIndex = 1,
                playMode = ToneRowPlayMode.PENDULUM,
                inverted = true,
                transpositionSemitones = 3,
                translation = -2,
                octaveOffset = 1,
                playOnce = true,
                notesRemainingInPass = 1,
            ),
            transport = TransportState(
                clockSource = ClockSource.MIDI,
                clocksPerStep = 6,
                tempoBpm = 90,
                noteDurationPercent = 63,
            ),
        )
        val appState = AppUiState(
            performance = performance,
            settingsLoaded = true,
            presetBank = PresetBank(
                mapOf(0 to PerformancePresetSnapshot(), 127 to PerformancePresetSnapshot(name = "Fin")),
            ),
            selectedPresetSlot = 127,
        )
        val content = appState.toToneRowContentInputs().toToneRowContentUiState()
        val cursor = appState.toToneRowCursorUiState()
        val state = content.withCursor(cursor)

        assertEquals(appState.toToneRowUiState(), state)
        assertTrue(state.available)
        assertEquals(ToneRowUiPhase.PAUSED, state.phase)
        assertEquals(listOf("C4", "G4"), state.row.map { it.noteLabel })
        assertEquals(listOf("0", "+4"), state.row.map { it.degreeLabel })
        assertEquals(1, state.rowCursorIndex)
        assertEquals(listOf(2, -1), state.movementSequence)
        assertEquals(ToneRowUiPlaybackMode.PENDULUM, state.playbackMode)
        assertEquals(ToneRowUiClockSource.MIDI, state.clockSource)
        assertEquals("1/16", state.clockDivisionLabel)
        assertEquals(128, state.selectedPresetSlot)
        assertEquals(setOf(1, 128), state.occupiedPresetSlots)
        assertTrue(state.playOnce)
    }

    @Test
    fun clockDivisionLabelsReduceTheNinetySixPulseWholeNote() {
        assertEquals("1/96", clockDivisionLabel(1))
        assertEquals("1/16", clockDivisionLabel(6))
        assertEquals("1/4", clockDivisionLabel(24))
        assertEquals("3/8", clockDivisionLabel(36))
        assertEquals("1/1", clockDivisionLabel(96))
    }

    @Test
    fun adapterMapsEveryAutomaticTransformationModeWithoutCollapsingDirection() {
        val fixtures = mapOf(
            ToneRowPlayMode.AUTO_TRANSPOSE_UP to ToneRowUiPlaybackMode.AUTO_TRANSPOSE_UP,
            ToneRowPlayMode.AUTO_TRANSPOSE_DOWN to ToneRowUiPlaybackMode.AUTO_TRANSPOSE_DOWN,
            ToneRowPlayMode.AUTO_TRANSLATE_UP to ToneRowUiPlaybackMode.AUTO_TRANSLATE_UP,
            ToneRowPlayMode.AUTO_TRANSLATE_DOWN to ToneRowUiPlaybackMode.AUTO_TRANSLATE_DOWN,
        )

        fixtures.forEach { (domainMode, uiMode) ->
            val inputs = AppUiState(
                performance = PerformanceCoordinatorState.initial().copy(
                    toneRow = ToneRowState(playMode = domainMode),
                ),
            ).toToneRowContentInputs()

            assertEquals(domainMode.name, uiMode, inputs.toToneRowContentUiState().playbackMode)
        }
    }
}
