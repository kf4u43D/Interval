package dev.intervaltablet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportReducerTest {
    private val reducer = TransportReducer()

    @Test
    fun defaultTimingRepresentsSixMidiClocksAndConfiguredNoteGate() {
        val state = TransportState()
        assertEquals(125_000_000L, state.stepDurationNanos())
        assertEquals(93_750_000L, state.noteDurationNanos())

        val slow = state.copy(tempoBpm = 60, clocksPerStep = 24, noteDurationPercent = 50)
        assertEquals(1_000_000_000L, slow.stepDurationNanos())
        assertEquals(500_000_000L, slow.noteDurationNanos())
    }

    @Test
    fun internalStartRestartsAndSchedulesFirstMovementAfterOneFullStep() {
        val startTime = 1_000_000_000L
        val started = reducer.reduce(TransportState(), TransportAction.Start(startTime))
        assertEquals(TransportMode.PLAYING, started.state.mode)
        assertEquals(listOf(TransportEvent.Restart(startTime)), started.events)
        assertEquals(startTime + 125_000_000L, started.state.nextInternalTickNanos)
        assertEquals(0L, started.state.stepCounter)

        val early = reducer.reduce(started.state, TransportAction.InternalClock(startTime + 100_000_000L))
        assertTrue(early.events.isEmpty())
        val due = reducer.reduce(early.state, TransportAction.InternalClock(startTime + 125_000_000L))
        assertEquals(listOf(TransportEvent.Tick(startTime + 125_000_000L, 1)), due.events)
        assertEquals(1L, due.state.stepCounter)
    }

    @Test
    fun internalClockDeduplicatesInputTimestampsAndEmitsAtMostOneCatchupTick() {
        val started = reducer.reduce(TransportState(), TransportAction.Start(0L)).state
        val late = reducer.reduce(started, TransportAction.InternalClock(1_000_000_000L))
        assertEquals(1, late.events.size)
        assertEquals(1L, late.state.stepCounter)
        assertEquals(1_125_000_000L, late.state.nextInternalTickNanos)

        val duplicateInput = reducer.reduce(late.state, TransportAction.InternalClock(1_000_000_000L))
        assertTrue(duplicateInput.events.isEmpty())
        assertEquals(late.state, duplicateInput.state)

        val nextInput = reducer.reduce(late.state, TransportAction.InternalClock(1_000_000_001L))
        assertTrue(nextInput.events.isEmpty())
        val nextDue = reducer.reduce(nextInput.state, TransportAction.InternalClock(1_125_000_000L))
        assertEquals(listOf(TransportEvent.Tick(1_125_000_000L, 2)), nextDue.events)
    }

    @Test
    fun tempoChangeKeepsAlreadyScheduledTickAndAppliesAfterIt() {
        val started = reducer.reduce(TransportState(), TransportAction.Start(10L)).state
        val originalDue = started.nextInternalTickNanos
        val changed = reducer.reduce(started, TransportAction.SetTempo(60)).state
        assertEquals(originalDue, changed.nextInternalTickNanos)

        val tick = reducer.reduce(changed, TransportAction.InternalClock(originalDue!!))
        assertEquals(originalDue, tick.events.single().timestampNanos)
        assertEquals(originalDue + 250_000_000L, tick.state.nextInternalTickNanos)
    }

    @Test
    fun stopAndContinuePreservePositionAndWaitAFullStepBeforeResuming() {
        var state = reducer.reduce(TransportState(), TransportAction.Start(0L)).state
        state = reducer.reduce(state, TransportAction.InternalClock(125_000_000L)).state
        val stopped = reducer.reduce(state, TransportAction.Stop(200_000_000L))
        assertEquals(TransportMode.STOPPED, stopped.state.mode)
        assertEquals(1L, stopped.state.stepCounter)
        assertNull(stopped.state.nextInternalTickNanos)
        assertEquals(listOf(TransportEvent.Stopped(200_000_000L)), stopped.events)

        val continued = reducer.reduce(stopped.state, TransportAction.Continue(1_000_000_000L))
        assertEquals(TransportMode.PLAYING, continued.state.mode)
        assertEquals(1L, continued.state.stepCounter)
        assertEquals(1_125_000_000L, continued.state.nextInternalTickNanos)
        assertTrue(continued.events.isEmpty())
    }

    @Test
    fun pauseSuppressesInternalTicksAndResumePreservesStepCounter() {
        var state = reducer.reduce(TransportState(), TransportAction.Start(0L)).state
        state = reducer.reduce(state, TransportAction.InternalClock(125_000_000L)).state
        val paused = reducer.reduce(state, TransportAction.Pause(126_000_000L))
        assertEquals(TransportMode.PAUSED, paused.state.mode)
        assertNull(paused.state.nextInternalTickNanos)
        assertTrue(reducer.reduce(paused.state, TransportAction.InternalClock(500_000_000L)).events.isEmpty())

        val resumed = reducer.reduce(paused.state, TransportAction.Continue(600_000_000L))
        assertEquals(1L, resumed.state.stepCounter)
        assertEquals(725_000_000L, resumed.state.nextInternalTickNanos)
    }

    @Test
    fun midiClockEmitsOneStepEveryConfiguredSixOfTwentyFourPulses() {
        var state = TransportState(clockSource = ClockSource.MIDI)
        var transition = reducer.reduce(state, TransportAction.MidiRealtime(0xFA, 10L))
        assertEquals(listOf(TransportEvent.Restart(10L)), transition.events)
        state = transition.state

        val ticks = mutableListOf<TransportEvent.Tick>()
        for (pulse in 1..24) {
            transition = reducer.reduce(state, TransportAction.MidiRealtime(0xF8, 10L + pulse))
            ticks += transition.events.filterIsInstance<TransportEvent.Tick>()
            state = transition.state
        }
        assertEquals(24L, state.midiClockPulse)
        assertEquals(4L, state.stepCounter)
        assertEquals(listOf(16L, 22L, 28L, 34L), ticks.map { it.timestampNanos })
        assertEquals(listOf(1L, 2L, 3L, 4L), ticks.map { it.step })
    }

    @Test
    fun midiClockGateDurationFollowsTheObservedPulsePeriod() {
        var state = TransportState(clockSource = ClockSource.MIDI, clocksPerStep = 6)
        state = reducer.reduce(state, TransportAction.MidiRealtime(0xFA, 0L)).state
        repeat(6) { pulse ->
            state = reducer.reduce(
                state,
                TransportAction.MidiRealtime(0xF8, (pulse + 1L) * 40_000_000L),
            ).state
        }

        assertEquals(40_000_000L, state.observedMidiClockPeriodNanos)
        assertEquals(240_000_000L, state.stepDurationNanos())
        assertEquals(180_000_000L, state.noteDurationNanos())
    }

    @Test
    fun midiStartResetsContinueResumesAndStopRetainsPulsePhase() {
        var state = TransportState(clockSource = ClockSource.MIDI)
        state = reducer.reduce(state, TransportAction.MidiRealtime(0xFA, 0L)).state
        repeat(7) { pulse ->
            state = reducer.reduce(state, TransportAction.MidiRealtime(0xF8, pulse + 1L)).state
        }
        assertEquals(7L, state.midiClockPulse)
        assertEquals(1L, state.stepCounter)

        val stopped = reducer.reduce(state, TransportAction.MidiRealtime(0xFC, 20L))
        assertEquals(TransportMode.STOPPED, stopped.state.mode)
        assertEquals(7L, stopped.state.midiClockPulse)
        val ignoredClock = reducer.reduce(stopped.state, TransportAction.MidiRealtime(0xF8, 21L))
        assertEquals(stopped.state, ignoredClock.state)

        state = reducer.reduce(stopped.state, TransportAction.MidiRealtime(0xFB, 22L)).state
        repeat(5) { pulse ->
            state = reducer.reduce(state, TransportAction.MidiRealtime(0xF8, 23L + pulse)).state
        }
        assertEquals(12L, state.midiClockPulse)
        assertEquals(2L, state.stepCounter)

        state = reducer.reduce(state, TransportAction.MidiRealtime(0xFA, 100L)).state
        assertEquals(0L, state.midiClockPulse)
        assertEquals(0L, state.stepCounter)
    }

    @Test
    fun clockSourcesAreStrictlyExclusiveAndSwitchingAPlayingSourceStopsIt() {
        val internal = reducer.reduce(TransportState(), TransportAction.Start(0L)).state
        assertTrue(
            reducer.reduce(internal, TransportAction.MidiRealtime(0xF8, 1L)).events.isEmpty(),
        )

        val switched = reducer.reduce(
            internal,
            TransportAction.SetClockSource(ClockSource.MIDI, timestampNanos = 2L),
        )
        assertEquals(ClockSource.MIDI, switched.state.clockSource)
        assertEquals(TransportMode.STOPPED, switched.state.mode)
        assertEquals(listOf(TransportEvent.Stopped(2L)), switched.events)
        assertNull(switched.state.nextInternalTickNanos)

        val midiStarted = reducer.reduce(
            switched.state,
            TransportAction.MidiRealtime(0xFA, 3L),
        ).state
        assertTrue(
            reducer.reduce(midiStarted, TransportAction.InternalClock(1_000_000_000L)).events.isEmpty(),
        )
    }

    @Test
    fun midiTicksWithEqualOrDecreasingAdapterTimestampsRemainStrictlyOrdered() {
        var state = TransportState(clockSource = ClockSource.MIDI, clocksPerStep = 1)
        state = reducer.reduce(state, TransportAction.MidiRealtime(0xFA, 0L)).state
        val outputTimestamps = mutableListOf<Long>()
        for (timestamp in listOf(100L, 100L, 99L, 101L)) {
            val transition = reducer.reduce(state, TransportAction.MidiRealtime(0xF8, timestamp))
            outputTimestamps += transition.events.map { it.timestampNanos }
            state = transition.state
        }
        assertEquals(listOf(100L, 101L, 102L, 103L), outputTimestamps)
        assertEquals(outputTimestamps.distinct(), outputTimestamps)
    }

    @Test
    fun legacyMidiFacadeUsesAReproducibleSyntheticTimestamp() {
        val legacy = MidiClockReducer()
        var transition = legacy.onRealtime(TransportState(), 0xFA)
        assertEquals(ClockSource.MIDI, transition.state.clockSource)
        assertEquals(listOf(TransportEvent.Restart(0L)), transition.events)

        repeat(6) {
            transition = legacy.onRealtime(transition.state, 0xF8)
        }
        assertEquals(listOf(TransportEvent.Tick(6L, 1L)), transition.events)
    }

    @Test
    fun timingConfigurationRejectsInvalidValues() {
        assertThrows(IllegalArgumentException::class.java) { TransportState(tempoBpm = 19) }
        assertThrows(IllegalArgumentException::class.java) { TransportState(clocksPerStep = 0) }
        assertThrows(IllegalArgumentException::class.java) { TransportState(noteDurationPercent = 101) }
        assertThrows(IllegalArgumentException::class.java) { TransportAction.SetTempo(301) }
        assertThrows(IllegalArgumentException::class.java) { TransportAction.SetClocksPerStep(97) }
        assertThrows(IllegalArgumentException::class.java) { TransportAction.InternalClock(-1L) }
        assertThrows(IllegalArgumentException::class.java) {
            TransportState(clockSource = ClockSource.MIDI, nextInternalTickNanos = 1L)
        }
    }
}
