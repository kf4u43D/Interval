package dev.intervaltablet.ui

import dev.intervaltablet.AppUiState
import dev.intervaltablet.PerformanceCoordinatorState
import dev.intervaltablet.domain.InstrumentAction
import dev.intervaltablet.domain.IntervalReducer
import dev.intervaltablet.domain.midiNoteName
import org.junit.Assert.assertEquals
import org.junit.Test

class IntervalPadPreviewTest {
    @Test
    fun previewProjectionMatchesTheSharedNavigationRuleForAllNinePads() {
        val initial = PerformanceCoordinatorState.initial()
        val anchored = IntervalReducer().reduce(
            initial.instrument,
            InstrumentAction.AnchorExternal(note = 0),
        ).state
        val state = AppUiState(performance = initial.copy(instrument = anchored))
        val steps = listOf(2, 3, 4, -1, 0, 1, -4, -3, -2)

        val previews = buildIntervalPadPreviews(
            config = state.instrument.config,
            currentNote = state.instrument.currentNote,
            lastExternalNote = state.instrument.lastExternalNote,
            steps = steps,
        )

        assertEquals(steps.toSet(), previews.keys)
        steps.forEach { interval ->
            val expected = state.targetPreview(interval)
            val actual = previews.getValue(interval)
            assertEquals(midiNoteName(expected.note), actual.target)
            assertEquals(expected.boundary, actual.boundary)
        }
    }
}
