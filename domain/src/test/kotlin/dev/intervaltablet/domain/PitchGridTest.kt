package dev.intervaltablet.domain

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchGridTest {
    @Test
    fun previewReportsNormalClampedAndWrappedMoves() {
        val wrapped = PitchGrid(0, ScaleLibrary.major, MidiNoteRange(60, 71), wrap = true)
        assertEquals(PitchMoveResult(62, PitchMoveBoundary.NONE), wrapped.previewMove(60, 1))
        assertEquals(PitchMoveResult(71, PitchMoveBoundary.WRAPPED), wrapped.previewMove(60, -1))
        assertEquals(PitchMoveResult(60, PitchMoveBoundary.WRAPPED), wrapped.previewMove(71, 1))

        val clamped = PitchGrid(0, ScaleLibrary.major, MidiNoteRange(60, 71), wrap = false)
        assertEquals(PitchMoveResult(60, PitchMoveBoundary.CLAMPED), clamped.previewMove(60, -1))
        assertEquals(PitchMoveResult(71, PitchMoveBoundary.CLAMPED), clamped.previewMove(71, 1))
        assertEquals(PitchMoveResult(60, PitchMoveBoundary.NONE), clamped.previewMove(60, 0))
        assertEquals(PitchMoveResult(60, PitchMoveBoundary.CLAMPED), clamped.previewMove(12, 0))
    }

    @Test
    fun externalAnchorConsumesFirstDirectionalStep() {
        val grid = PitchGrid(0, ScaleLibrary.major, MidiNoteRange(36, 95), wrap = true)
        assertEquals(PitchMoveResult(62, PitchMoveBoundary.NONE), grid.previewMove(61, 1))
        assertEquals(PitchMoveResult(60, PitchMoveBoundary.NONE), grid.previewMove(61, -1))
        assertEquals(61, grid.move(61, 0))
    }

    @Test
    fun movementMatchesOracleForEveryScaleRootPolicyAnchorAndPublicStep() {
        val range = MidiNoteRange(0, 127)
        for (scale in ScaleLibrary.all) {
            for (root in 0..11) {
                val notes = (range.min..range.max)
                    .filter { floorMod(it - root, 12) in scale.offsets }
                for (wrap in listOf(false, true)) {
                    val grid = PitchGrid(root, scale, range, wrap)
                    for (anchor in range.min..range.max) {
                        for (steps in MIN_INTERVAL_STEPS..MAX_INTERVAL_STEPS) {
                            val expected = oracleMove(notes, anchor, steps, wrap)
                            val actual = grid.previewMove(anchor, steps)
                            if (expected != actual) {
                                throw AssertionError(
                                    "${scale.id}, root=$root, wrap=$wrap, anchor=$anchor, " +
                                        "steps=$steps: expected $expected but was $actual",
                                )
                            }
                            assertTrue(actual.note in range.min..range.max)
                            if (steps != 0 || grid.contains(anchor)) {
                                assertTrue(grid.contains(actual.note))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun degreeIndexIsZeroBasedWithinScaleAndNullForNonMembers() {
        for (scale in ScaleLibrary.all) {
            for (root in 0..11) {
                val grid = PitchGrid(root, scale, MidiNoteRange(0, 127), wrap = false)
                for (note in 0..127) {
                    val expected = scale.offsets.indexOf(floorMod(note - root, 12))
                    if (expected < 0) {
                        assertNull(grid.degreeIndexOf(note))
                    } else {
                        assertEquals(expected, grid.degreeIndexOf(note))
                    }
                }
                assertNull(grid.degreeIndexOf(-1))
                assertNull(grid.degreeIndexOf(128))
            }
        }
    }

    @Test
    fun homeIsRootNearestRangeCenterForEveryScaleAndRoot() {
        val range = MidiNoteRange(36, 95)
        for (scale in ScaleLibrary.all) {
            for (root in 0..11) {
                val grid = PitchGrid(root, scale, range, wrap = true)
                val expected = (range.min..range.max)
                    .filter { floorMod(it, 12) == root }
                    .minBy { abs(it - range.center) }
                assertEquals(expected, grid.home())
            }
        }
    }

    private fun oracleMove(
        notes: List<Int>,
        anchor: Int,
        steps: Int,
        wrap: Boolean,
    ): PitchMoveResult {
        if (steps == 0) return PitchMoveResult(anchor, PitchMoveBoundary.NONE)

        val exact = notes.binarySearch(anchor)
        val rawIndex = if (exact >= 0) {
            exact + steps
        } else if (steps > 0) {
            val firstAbove = notes.indexOfFirst { it > anchor }.let { if (it < 0) notes.size else it }
            firstAbove + steps - 1
        } else {
            val firstBelow = notes.indexOfLast { it < anchor }
            firstBelow + steps + 1
        }
        if (rawIndex in notes.indices) return PitchMoveResult(notes[rawIndex], PitchMoveBoundary.NONE)

        val boundary = if (wrap) PitchMoveBoundary.WRAPPED else PitchMoveBoundary.CLAMPED
        val normalized = if (wrap) floorMod(rawIndex, notes.size) else rawIndex.coerceIn(notes.indices)
        return PitchMoveResult(notes[normalized], boundary)
    }
}
