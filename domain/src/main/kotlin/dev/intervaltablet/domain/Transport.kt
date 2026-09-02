package dev.intervaltablet.domain

private const val MIDI_CLOCKS_PER_QUARTER_NOTE: Long = 24L
private const val NANOS_PER_MINUTE: Long = 60_000_000_000L
private const val MIN_TEMPO_BPM: Int = 20
private const val MAX_TEMPO_BPM: Int = 300
private const val MIN_CLOCKS_PER_STEP: Int = 1
private const val MAX_CLOCKS_PER_STEP: Int = 96

enum class TransportMode { STOPPED, PLAYING, PAUSED }

/** Exactly one clock source can own musical ticks at a time. */
enum class ClockSource { INTERNAL, MIDI }

data class TimeSignature(
    val beatsPerBar: Int = 4,
    val beatUnit: Int = 4,
) {
    init {
        require(beatsPerBar in 1..12)
        require(beatUnit in setOf(2, 4, 8, 16))
    }
}

data class TransportState(
    val mode: TransportMode = TransportMode.STOPPED,
    val clockSource: ClockSource = ClockSource.INTERNAL,
    val midiClockPulse: Long = 0,
    val stepCounter: Long = 0,
    val clocksPerStep: Int = 6,
    val tempoBpm: Int = 120,
    val noteDurationPercent: Int = 75,
    val timeSignature: TimeSignature = TimeSignature(),
    val nextInternalTickNanos: Long? = null,
    val lastInternalInputTimestampNanos: Long? = null,
    val lastMidiInputTimestampNanos: Long? = null,
    val lastMidiClockTimestampNanos: Long? = null,
    val observedMidiClockPeriodNanos: Long? = null,
    val lastTickTimestampNanos: Long? = null,
) {
    init {
        require(midiClockPulse >= 0)
        require(stepCounter >= 0)
        require(clocksPerStep in MIN_CLOCKS_PER_STEP..MAX_CLOCKS_PER_STEP)
        require(tempoBpm in MIN_TEMPO_BPM..MAX_TEMPO_BPM)
        require(noteDurationPercent in 1..100)
        require(nextInternalTickNanos == null || nextInternalTickNanos >= 0)
        require(lastInternalInputTimestampNanos == null || lastInternalInputTimestampNanos >= 0)
        require(lastMidiInputTimestampNanos == null || lastMidiInputTimestampNanos >= 0)
        require(lastMidiClockTimestampNanos == null || lastMidiClockTimestampNanos >= 0)
        require(observedMidiClockPeriodNanos == null || observedMidiClockPeriodNanos > 0)
        require(lastTickTimestampNanos == null || lastTickTimestampNanos >= 0)
        require(clockSource == ClockSource.INTERNAL || nextInternalTickNanos == null)
    }

    /** Duration represented by one musical step with the active tempo/division. */
    fun stepDurationNanos(): Long {
        if (clockSource == ClockSource.MIDI && observedMidiClockPeriodNanos != null) {
            return safeMultiply(observedMidiClockPeriodNanos, clocksPerStep.toLong())
        }
        val numerator = NANOS_PER_MINUTE * clocksPerStep.toLong()
        val denominator = tempoBpm.toLong() * MIDI_CLOCKS_PER_QUARTER_NOTE
        return (numerator / denominator).coerceAtLeast(1L)
    }

    fun noteDurationNanos(): Long {
        val step = stepDurationNanos()
        val percent = noteDurationPercent.toLong()
        return (step / 100L * percent + step % 100L * percent / 100L).coerceAtLeast(1L)
    }

    private fun safeMultiply(value: Long, multiplier: Long): Long {
        return if (value > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else value * multiplier
    }
}

sealed interface TransportAction {
    data class SetClockSource(val source: ClockSource, val timestampNanos: Long = 0L) : TransportAction {
        init {
            require(timestampNanos >= 0)
        }
    }

    data class SetTempo(val bpm: Int) : TransportAction {
        init {
            require(bpm in MIN_TEMPO_BPM..MAX_TEMPO_BPM)
        }
    }

    data class SetClocksPerStep(val clocks: Int) : TransportAction {
        init {
            require(clocks in MIN_CLOCKS_PER_STEP..MAX_CLOCKS_PER_STEP)
        }
    }

    data class SetNoteDuration(val percent: Int) : TransportAction {
        init {
            require(percent in 1..100)
        }
    }

    data class SetTimeSignature(val signature: TimeSignature) : TransportAction

    /** Start resets the musical position before playback. */
    data class Start(val timestampNanos: Long) : TransportAction {
        init {
            require(timestampNanos >= 0)
        }
    }

    /** Continue preserves counters and resumes from the retained position. */
    data class Continue(val timestampNanos: Long) : TransportAction {
        init {
            require(timestampNanos >= 0)
        }
    }

    data class Pause(val timestampNanos: Long) : TransportAction {
        init {
            require(timestampNanos >= 0)
        }
    }

    data class Stop(val timestampNanos: Long) : TransportAction {
        init {
            require(timestampNanos >= 0)
        }
    }

    /**
     * Scheduler callback for the internal source. A timestamp is accepted at most once;
     * a late callback emits no more than one tick and therefore cannot block the UI.
     */
    data class InternalClock(val timestampNanos: Long) : TransportAction {
        init {
            require(timestampNanos >= 0)
        }
    }

    /** One MIDI real-time byte with its adapter-provided monotonic timestamp. */
    data class MidiRealtime(val status: Int, val timestampNanos: Long) : TransportAction {
        init {
            require(status in 0..255)
            require(timestampNanos >= 0)
        }
    }
}

sealed interface TransportEvent {
    val timestampNanos: Long

    data class Restart(override val timestampNanos: Long) : TransportEvent

    data class Tick(
        override val timestampNanos: Long,
        val step: Long,
    ) : TransportEvent {
        init {
            require(step > 0)
        }
    }

    data class Stopped(override val timestampNanos: Long) : TransportEvent
}

data class TransportTransition(
    val state: TransportState,
    val events: List<TransportEvent> = emptyList(),
)

/** Pure reducer shared by the internal scheduler and incoming MIDI clock. */
class TransportReducer {
    fun reduce(state: TransportState, action: TransportAction): TransportTransition {
        return when (action) {
            is TransportAction.SetClockSource -> setClockSource(state, action)
            is TransportAction.SetTempo -> TransportTransition(state.copy(tempoBpm = action.bpm))
            is TransportAction.SetClocksPerStep -> TransportTransition(
                state.copy(clocksPerStep = action.clocks),
            )
            is TransportAction.SetNoteDuration -> TransportTransition(
                state.copy(noteDurationPercent = action.percent),
            )
            is TransportAction.SetTimeSignature -> TransportTransition(
                state.copy(timeSignature = action.signature),
            )
            is TransportAction.Start -> start(state, action.timestampNanos)
            is TransportAction.Continue -> continuePlayback(state, action.timestampNanos)
            is TransportAction.Pause -> pause(state, action.timestampNanos)
            is TransportAction.Stop -> stop(state, action.timestampNanos)
            is TransportAction.InternalClock -> internalClock(state, action.timestampNanos)
            is TransportAction.MidiRealtime -> midiRealtime(state, action)
        }
    }

    private fun setClockSource(
        state: TransportState,
        action: TransportAction.SetClockSource,
    ): TransportTransition {
        if (state.clockSource == action.source) return TransportTransition(state)
        val wasRunning = state.mode != TransportMode.STOPPED
        val next = state.copy(
            mode = TransportMode.STOPPED,
            clockSource = action.source,
            midiClockPulse = 0,
            nextInternalTickNanos = null,
            lastInternalInputTimestampNanos = null,
            lastMidiInputTimestampNanos = null,
            lastMidiClockTimestampNanos = null,
            observedMidiClockPeriodNanos = null,
            lastTickTimestampNanos = null,
        )
        return TransportTransition(
            next,
            if (wasRunning) listOf(TransportEvent.Stopped(action.timestampNanos)) else emptyList(),
        )
    }

    private fun start(state: TransportState, timestampNanos: Long): TransportTransition {
        return TransportTransition(
            state.copy(
                mode = TransportMode.PLAYING,
                midiClockPulse = 0,
                stepCounter = 0,
                nextInternalTickNanos = if (state.clockSource == ClockSource.INTERNAL) {
                    safeAdd(timestampNanos, state.stepDurationNanos())
                } else {
                    null
                },
                lastInternalInputTimestampNanos = null,
                lastMidiInputTimestampNanos = null,
                lastMidiClockTimestampNanos = null,
                observedMidiClockPeriodNanos = null,
                lastTickTimestampNanos = null,
            ),
            listOf(TransportEvent.Restart(timestampNanos)),
        )
    }

    private fun continuePlayback(state: TransportState, timestampNanos: Long): TransportTransition {
        if (state.mode == TransportMode.PLAYING) return TransportTransition(state)
        val nextDue = if (state.clockSource == ClockSource.INTERNAL) {
            safeAdd(timestampNanos, state.stepDurationNanos())
        } else {
            null
        }
        return TransportTransition(
            state.copy(
                mode = TransportMode.PLAYING,
                nextInternalTickNanos = nextDue,
                lastInternalInputTimestampNanos = null,
                lastMidiClockTimestampNanos = if (state.clockSource == ClockSource.MIDI) {
                    null
                } else {
                    state.lastMidiClockTimestampNanos
                },
            ),
        )
    }

    private fun pause(state: TransportState, timestampNanos: Long): TransportTransition {
        if (state.mode != TransportMode.PLAYING) return TransportTransition(state)
        return TransportTransition(
            state.copy(
                mode = TransportMode.PAUSED,
                nextInternalTickNanos = null,
                lastInternalInputTimestampNanos = timestampNanos,
                lastMidiClockTimestampNanos = if (state.clockSource == ClockSource.MIDI) {
                    null
                } else {
                    state.lastMidiClockTimestampNanos
                },
            ),
        )
    }

    private fun stop(state: TransportState, timestampNanos: Long): TransportTransition {
        if (state.mode == TransportMode.STOPPED) return TransportTransition(state)
        return TransportTransition(
            state.copy(
                mode = TransportMode.STOPPED,
                nextInternalTickNanos = null,
                lastInternalInputTimestampNanos = if (state.clockSource == ClockSource.INTERNAL) {
                    timestampNanos
                } else {
                    state.lastInternalInputTimestampNanos
                },
                lastMidiInputTimestampNanos = if (state.clockSource == ClockSource.MIDI) {
                    timestampNanos
                } else {
                    state.lastMidiInputTimestampNanos
                },
                lastMidiClockTimestampNanos = if (state.clockSource == ClockSource.MIDI) {
                    null
                } else {
                    state.lastMidiClockTimestampNanos
                },
            ),
            listOf(TransportEvent.Stopped(timestampNanos)),
        )
    }

    private fun internalClock(state: TransportState, timestampNanos: Long): TransportTransition {
        if (state.clockSource != ClockSource.INTERNAL || state.mode != TransportMode.PLAYING) {
            return TransportTransition(state)
        }
        val lastInput = state.lastInternalInputTimestampNanos
        if (lastInput != null && timestampNanos <= lastInput) return TransportTransition(state)

        val due = state.nextInternalTickNanos ?: timestampNanos
        if (timestampNanos < due) {
            return TransportTransition(state.copy(lastInternalInputTimestampNanos = timestampNanos))
        }
        val tickTimestamp = maxOf(timestampNanos, safeIncrement(state.lastTickTimestampNanos ?: -1L))
        val nextStep = safeIncrement(state.stepCounter)
        val interval = state.stepDurationNanos()
        return TransportTransition(
            state.copy(
                stepCounter = nextStep,
                // A late callback advances once and rebases the following deadline. Catching
                // up every missed step would enqueue an immediate burst on the app actor.
                nextInternalTickNanos = safeAdd(maxOf(due, timestampNanos), interval),
                lastInternalInputTimestampNanos = timestampNanos,
                lastTickTimestampNanos = tickTimestamp,
            ),
            listOf(TransportEvent.Tick(tickTimestamp, nextStep)),
        )
    }

    private fun midiRealtime(
        state: TransportState,
        action: TransportAction.MidiRealtime,
    ): TransportTransition {
        if (state.clockSource != ClockSource.MIDI) return TransportTransition(state)
        return when (action.status and 0xFF) {
            0xFA -> start(state, action.timestampNanos).withMidiInputTimestamp(action.timestampNanos)
            0xFB -> continuePlayback(state, action.timestampNanos).withMidiInputTimestamp(action.timestampNanos)
            0xFC -> stop(state, action.timestampNanos).withMidiInputTimestamp(action.timestampNanos)
            0xF8 -> midiClock(state, action.timestampNanos)
            else -> TransportTransition(state)
        }
    }

    private fun midiClock(state: TransportState, timestampNanos: Long): TransportTransition {
        if (state.mode != TransportMode.PLAYING) return TransportTransition(state)
        val previousClockTimestamp = state.lastMidiClockTimestampNanos
        val observedPeriod = if (previousClockTimestamp != null && timestampNanos > previousClockTimestamp) {
            timestampNanos - previousClockTimestamp
        } else {
            state.observedMidiClockPeriodNanos
        }
        val clockTimestamp = if (previousClockTimestamp == null || timestampNanos > previousClockTimestamp) {
            timestampNanos
        } else {
            previousClockTimestamp
        }
        val pulse = safeIncrement(state.midiClockPulse)
        val tickDue = pulse % state.clocksPerStep.toLong() == 0L
        if (!tickDue) {
            return TransportTransition(
                state.copy(
                    midiClockPulse = pulse,
                    lastMidiInputTimestampNanos = timestampNanos,
                    lastMidiClockTimestampNanos = clockTimestamp,
                    observedMidiClockPeriodNanos = observedPeriod,
                ),
            )
        }
        if (state.lastTickTimestampNanos == Long.MAX_VALUE) {
            return TransportTransition(
                state.copy(
                    midiClockPulse = pulse,
                    lastMidiInputTimestampNanos = timestampNanos,
                    lastMidiClockTimestampNanos = clockTimestamp,
                    observedMidiClockPeriodNanos = observedPeriod,
                ),
            )
        }
        val tickTimestamp = maxOf(timestampNanos, safeIncrement(state.lastTickTimestampNanos ?: -1L))
        val nextStep = safeIncrement(state.stepCounter)
        return TransportTransition(
            state.copy(
                midiClockPulse = pulse,
                stepCounter = nextStep,
                lastMidiInputTimestampNanos = timestampNanos,
                lastMidiClockTimestampNanos = clockTimestamp,
                observedMidiClockPeriodNanos = observedPeriod,
                lastTickTimestampNanos = tickTimestamp,
            ),
            listOf(TransportEvent.Tick(tickTimestamp, nextStep)),
        )
    }

    private fun TransportTransition.withMidiInputTimestamp(timestampNanos: Long): TransportTransition {
        return copy(state = state.copy(lastMidiInputTimestampNanos = timestampNanos))
    }

    private fun safeAdd(value: Long, increment: Long): Long {
        return if (Long.MAX_VALUE - value < increment) Long.MAX_VALUE else value + increment
    }

    private fun safeIncrement(value: Long): Long = if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1L
}

/**
 * Compatibility façade for the pre-stage-2 API. It deterministically synthesizes an
 * increasing timestamp when the adapter does not provide one; new code should use
 * [TransportReducer] and [TransportAction.MidiRealtime] directly.
 */
class MidiClockReducer {
    private val reducer = TransportReducer()

    fun onRealtime(state: TransportState, status: Int): TransportTransition {
        val previousTimestamp = maxOf(
            state.lastMidiInputTimestampNanos ?: -1L,
            state.lastTickTimestampNanos ?: -1L,
        )
        val timestamp = if (previousTimestamp == Long.MAX_VALUE) Long.MAX_VALUE else previousTimestamp + 1L
        return onRealtime(state, status, timestamp)
    }

    fun onRealtime(state: TransportState, status: Int, timestampNanos: Long): TransportTransition {
        val midiState = if (state.clockSource == ClockSource.MIDI) {
            state
        } else {
            state.copy(clockSource = ClockSource.MIDI, nextInternalTickNanos = null)
        }
        return reducer.reduce(midiState, TransportAction.MidiRealtime(status and 0xFF, timestampNanos))
    }
}
