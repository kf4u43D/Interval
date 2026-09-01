package dev.intervaltablet.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrummerGestureTrackerTest {
    private val geometry = StrummerGeometry(
        toneCount = 4,
        primaryExtent = 400f,
        velocityExtent = 100f,
    )

    @Test
    fun downEmitsInitialBandWithCrossAxisVelocity() {
        val tracker = StrummerGestureTracker()

        assertEquals(listOf(StrumHit(toneIndex = 1, velocity = 64)), tracker.down(7L, 150f, 50f, geometry))
        assertEquals(1, tracker.activePointerCount)
        assertEquals(emptyList<StrumHit>(), tracker.down(7L, 350f, 100f, geometry))
    }

    @Test
    fun movementInsideOneBandProducesNoTraffic() {
        val tracker = StrummerGestureTracker()
        tracker.down(1L, 10f, 20f, geometry)

        assertEquals(emptyList<StrumHit>(), tracker.move(1L, 99f, 80f, geometry))
        assertEquals(listOf(StrumHit(1, 102)), tracker.move(1L, 101f, 80f, geometry))
    }

    @Test
    fun fastMoveEnumeratesEverySkippedBand() {
        val tracker = StrummerGestureTracker()
        tracker.down(1L, 1f, 100f, geometry)

        assertEquals(
            listOf(StrumHit(1, 127), StrumHit(2, 127), StrumHit(3, 127)),
            tracker.move(1L, 399f, 100f, geometry),
        )
    }

    @Test
    fun reversalRetriggersCrossedBandsInReverseOrder() {
        val tracker = StrummerGestureTracker()
        tracker.down(1L, 1f, 0f, geometry)
        tracker.move(1L, 399f, 0f, geometry)

        assertEquals(
            listOf(StrumHit(2, 1), StrumHit(1, 1), StrumHit(0, 1)),
            tracker.move(1L, 1f, 0f, geometry),
        )
    }

    @Test
    fun positionsAndVelocityAreClampedAtBothEdges() {
        val tracker = StrummerGestureTracker()

        assertEquals(listOf(StrumHit(0, 1)), tracker.down(1L, -500f, -10f, geometry))
        assertEquals(listOf(StrumHit(1, 127), StrumHit(2, 127), StrumHit(3, 127)), tracker.move(1L, 900f, 500f, geometry))
    }

    @Test
    fun pointersMoveAndReleaseIndependently() {
        val tracker = StrummerGestureTracker()
        assertEquals(listOf(StrumHit(0, 1)), tracker.down(10L, 1f, 0f, geometry))
        assertEquals(listOf(StrumHit(3, 127)), tracker.down(11L, 399f, 100f, geometry))

        assertEquals(listOf(StrumHit(1, 64)), tracker.move(10L, 101f, 50f, geometry))
        assertEquals(emptyList<StrumHit>(), tracker.move(11L, 350f, 50f, geometry))
        assertTrue(tracker.up(10L))
        assertFalse(tracker.up(10L))
        assertEquals(1, tracker.activePointerCount)
    }

    @Test
    fun cancellationDropsPointersAndLaterMovesUntilANewDown() {
        val tracker = StrummerGestureTracker()
        tracker.down(3L, 1f, 0f, geometry)
        tracker.cancelAll()

        assertEquals(0, tracker.activePointerCount)
        assertEquals(emptyList<StrumHit>(), tracker.move(3L, 399f, 100f, geometry))
        assertEquals(listOf(StrumHit(3, 127)), tracker.down(3L, 399f, 100f, geometry))
    }

    @Test
    fun toneCountChangeReanchorsGestureWithoutInvalidIntermediateIndices() {
        val tracker = StrummerGestureTracker()
        tracker.down(9L, 399f, 50f, geometry)
        val singleTone = geometry.copy(toneCount = 1)

        assertEquals(listOf(StrumHit(0, 64)), tracker.move(9L, 200f, 50f, singleTone))
        assertEquals(emptyList<StrumHit>(), tracker.move(9L, 300f, 50f, singleTone))
    }

    @Test
    fun hysteresisPreventsBoundaryJitterButStillAllowsReversal() {
        val tracker = StrummerGestureTracker()
        val stableGeometry = geometry.copy(hysteresis = 8f)
        tracker.down(12L, 50f, 50f, stableGeometry)

        assertEquals(emptyList<StrumHit>(), tracker.move(12L, 105f, 50f, stableGeometry))
        assertEquals(listOf(StrumHit(1, 64)), tracker.move(12L, 109f, 50f, stableGeometry))
        assertEquals(emptyList<StrumHit>(), tracker.move(12L, 95f, 50f, stableGeometry))
        assertEquals(listOf(StrumHit(0, 64)), tracker.move(12L, 90f, 50f, stableGeometry))
    }
}
