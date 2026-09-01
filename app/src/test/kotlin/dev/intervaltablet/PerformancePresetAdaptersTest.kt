package dev.intervaltablet

import dev.intervaltablet.data.toPersistenceSnapshot
import dev.intervaltablet.data.toStoppedDomainState
import dev.intervaltablet.domain.ClockSource
import dev.intervaltablet.domain.ToneRowEntry
import dev.intervaltablet.domain.ToneRowMode
import dev.intervaltablet.domain.ToneRowPlayMode
import dev.intervaltablet.domain.ToneRowState
import dev.intervaltablet.domain.TransportMode
import dev.intervaltablet.domain.TransportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PerformancePresetAdaptersTest {
    @Test
    fun toneRowRestorePreservesDefinitionButDropsLiveTransportState() {
        val live = ToneRowState(
            mode = ToneRowMode.AUTO_PLAYING,
            entries = listOf(
                ToneRowEntry(-2, 60, 50),
                ToneRowEntry(3, 67, 100),
            ),
            rowIndex = 1,
            intervalSequence = listOf(-1, 4),
            sequenceIndex = 1,
            playMode = ToneRowPlayMode.RETRO,
            inverted = true,
            transpositionSemitones = 7,
            translation = -5,
            octaveOffset = 9,
            randomState = 123456L,
            referenceRootPitchClass = 4,
            referenceScaleId = "dorian",
            playOnce = true,
            notesRemainingInPass = 1,
        )

        val restored = live.toPersistenceSnapshot().toStoppedDomainState()

        assertEquals(ToneRowMode.IDLE, restored.mode)
        assertEquals(live.entries, restored.entries)
        assertEquals(live.intervalSequence, restored.intervalSequence)
        assertEquals(live.playMode, restored.playMode)
        assertEquals(live.transpositionSemitones, restored.transpositionSemitones)
        assertEquals(live.referenceScaleId, restored.referenceScaleId)
        assertEquals(0, restored.rowIndex)
        assertEquals(0, restored.sequenceIndex)
        assertFalse(restored.playOnce)
    }

    @Test
    fun transportRestoreKeepsOptionsButNeverResumesOrRetainsDeadlines() {
        val live = TransportState(
            mode = TransportMode.PLAYING,
            clockSource = ClockSource.INTERNAL,
            midiClockPulse = 22,
            stepCounter = 9,
            clocksPerStep = 13,
            tempoBpm = 231,
            noteDurationPercent = 34,
            nextInternalTickNanos = 4_000L,
            lastInternalInputTimestampNanos = 3_000L,
            lastTickTimestampNanos = 3_000L,
        )

        val restored = live.toPersistenceSnapshot().toStoppedDomainState()

        assertEquals(TransportMode.STOPPED, restored.mode)
        assertEquals(ClockSource.INTERNAL, restored.clockSource)
        assertEquals(13, restored.clocksPerStep)
        assertEquals(231, restored.tempoBpm)
        assertEquals(34, restored.noteDurationPercent)
        assertEquals(0L, restored.midiClockPulse)
        assertEquals(0L, restored.stepCounter)
        assertNull(restored.nextInternalTickNanos)
        assertNull(restored.lastTickTimestampNanos)
    }
}
