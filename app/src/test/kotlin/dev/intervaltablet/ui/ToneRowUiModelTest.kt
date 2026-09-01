package dev.intervaltablet.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneRowUiModelTest {
    @Test
    fun invalidCursorsAndEmptyMovementSequenceFallBackWithoutCrashing() {
        val state = ToneRowUiState(
            available = true,
            row = listOf(ToneRowStepUi("C4", "d1", 64)),
            rowCursorIndex = 9,
            movementSequence = emptyList(),
            sequenceCursorIndex = -1,
        )

        assertNull(state.boundedRowCursorIndex)
        assertEquals(listOf(1), state.effectiveMovementSequence)
        assertNull(state.boundedSequenceCursorIndex)
        assertFalse(state.timelineItems().single().selected)
    }

    @Test
    fun timelineClampsDisplayVelocityAndMarksOnlyTheCurrentStep() {
        val state = ToneRowUiState(
            row = listOf(
                ToneRowStepUi("C4", "d1", -20),
                ToneRowStepUi("G4", "d5", 240),
            ),
            rowCursorIndex = 1,
        )

        val items = state.timelineItems()

        assertEquals(listOf(1, 127), items.map { it.velocity })
        assertEquals(listOf(false, true), items.map { it.selected })
        assertEquals(listOf(1, 2), items.map { it.ordinal })
    }

    @Test
    fun playbackAndRecordAvailabilityReflectPhaseAndContent() {
        val empty = ToneRowUiState(available = true)
        val playing = ToneRowUiState(
            available = true,
            phase = ToneRowUiPhase.AUTO_PLAYING,
            row = listOf(ToneRowStepUi("D4", "d2", 80)),
        )

        assertFalse(empty.canStartPlayback)
        assertTrue(empty.canRecord)
        assertTrue(playing.canStartPlayback)
        assertFalse(playing.canRecord)
        assertTrue(playing.isRunning)
    }

    @Test
    fun presetSelectionIsBoundedAndOccupancyUsesTheBoundedSlot() {
        val belowRange = ToneRowUiState(
            selectedPresetSlot = -8,
            presetSlotCount = 0,
            occupiedPresetSlots = setOf(1),
        )
        val aboveRange = ToneRowUiState(
            selectedPresetSlot = 90,
            presetSlotCount = 4,
            occupiedPresetSlots = setOf(4),
        )

        assertEquals(1, belowRange.safePresetSlotCount)
        assertEquals(1, belowRange.safeSelectedPresetSlot)
        assertTrue(belowRange.selectedPresetIsOccupied)
        assertEquals(4, aboveRange.safeSelectedPresetSlot)
        assertTrue(aboveRange.selectedPresetIsOccupied)
    }

    @Test
    fun signedValuesExposePositiveDirectionWithoutChangingZero() {
        assertEquals("+3", signedUiValue(3))
        assertEquals("0", signedUiValue(0))
        assertEquals("-4", signedUiValue(-4))
    }
}
