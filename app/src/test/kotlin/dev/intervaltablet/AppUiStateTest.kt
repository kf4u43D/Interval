package dev.intervaltablet

import dev.intervaltablet.domain.InstrumentAction
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUiStateTest {
    @Test
    fun previewUsesThePendingRawExternalAnchorForTheFirstNonZeroMove() {
        val coordinatorState = PerformanceCoordinatorState.initial()
        val anchored = dev.intervaltablet.domain.IntervalReducer().reduce(
            coordinatorState.instrument,
            InstrumentAction.AnchorExternal(0),
        ).state
        val state = AppUiState(performance = coordinatorState.copy(instrument = anchored))

        assertEquals(36, anchored.currentNote)
        assertEquals(36, state.targetPreview(1).note)
        assertEquals(36, state.targetPreview(0).note)
    }
}
