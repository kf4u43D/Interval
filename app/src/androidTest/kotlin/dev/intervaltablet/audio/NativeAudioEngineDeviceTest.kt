package dev.intervaltablet.audio

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.intervaltablet.domain.AudioCommand
import dev.intervaltablet.domain.SynthParameter
import dev.intervaltablet.domain.SynthPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeAudioEngineDeviceTest {
    @Test(timeout = 90_000L)
    fun audio01StartsAndStopsTenTimesWithNegotiatedDiagnostics() {
        val engine = NativeAudioEngine()
        val runningSamples = mutableListOf<AudioDiagnostics>()

        try {
            assertTrue("The packaged native audio library must be available", engine.isAvailable)

            var previousRestartCount = 0
            repeat(CycleCount) { zeroBasedCycle ->
                val cycle = zeroBasedCycle + 1
                assertTrue("AUDIO-01 cycle $cycle failed to start", engine.start())

                awaitDiagnostics(engine, "AUDIO-01 cycle $cycle did not expose a running stream") {
                    it.streamRunning &&
                        it.sampleRate > 0 &&
                        it.framesPerBurst > 0 &&
                        !it.recoveryPending
                }
                assertTrue("AUDIO-01 cycle $cycle did not retain its native handle", engine.isCreated)
                assertTrue("AUDIO-01 cycle $cycle disagreed with the native running state", engine.isRunning)

                SystemClock.sleep(StreamDwellMillis)
                var probeRestartCount = engine.diagnostics().restartCount
                enqueueFullParameterProbe(engine, cycle)
                var running = awaitDiagnostics(
                    engine,
                    "AUDIO-01 cycle $cycle did not drain its callback probe",
                ) {
                    it.streamRunning &&
                        it.sampleRate > 0 &&
                        it.framesPerBurst > 0 &&
                        it.currentQueueDepth == 0 &&
                        !it.recoveryPending
                }
                if (running.restartCount != probeRestartCount) {
                    probeRestartCount = running.restartCount
                    enqueueFullParameterProbe(engine, cycle)
                    running = awaitDiagnostics(
                        engine,
                        "AUDIO-01 cycle $cycle did not drain its post-recovery probe",
                    ) {
                        it.streamRunning &&
                            it.currentQueueDepth == 0 &&
                            it.restartCount == probeRestartCount &&
                            !it.recoveryPending
                    }
                }

                assertEquals("AUDIO-01 cycle $cycle dropped an audio event", 0, running.droppedEvents)
                assertTrue("AUDIO-01 cycle $cycle reported a negative xrun count", running.xRunCount >= 0)
                assertTrue(
                    "AUDIO-01 cycle $cycle made the restart counter decrease",
                    running.restartCount >= previousRestartCount,
                )
                assertTrue(
                    "AUDIO-01 cycle $cycle reported an invalid queue maximum",
                    running.maximumQueueDepth >= running.currentQueueDepth,
                )
                previousRestartCount = running.restartCount
                runningSamples += running
                Log.i(AudioLogTag, "AUDIO-01 cycle=$cycle running=$running")

                engine.stop()
                val stopped = awaitDiagnostics(
                    engine,
                    "AUDIO-01 cycle $cycle did not release its stream",
                ) {
                    !it.streamRunning &&
                        it.sampleRate == 0 &&
                        it.framesPerBurst == 0 &&
                        it.bufferSizeFrames == 0 &&
                        it.currentQueueDepth == 0 &&
                        !it.recoveryPending
                }
                assertFalse("AUDIO-01 cycle $cycle remained marked as running", engine.isRunning)
                assertEquals("AUDIO-01 cycle $cycle dropped an event while stopping", 0, stopped.droppedEvents)
                Log.i(AudioLogTag, "AUDIO-01 cycle=$cycle stopped=$stopped")
            }

            assertEquals("AUDIO-01 did not complete every requested cycle", CycleCount, runningSamples.size)
            Log.i(
                AudioLogTag,
                "AUDIO-01 summary cycles=${runningSamples.size} " +
                    "sampleRates=${runningSamples.map { it.sampleRate }.distinct().sorted()} " +
                    "framesPerBurst=${runningSamples.map { it.framesPerBurst }.distinct().sorted()} " +
                    "maxXruns=${runningSamples.maxOf { it.xRunCount }} " +
                    "restartCount=${runningSamples.last().restartCount} " +
                    "lastErrorCodes=${runningSamples.map { it.lastErrorCode }.distinct().sorted()}",
            )

            engine.close()
            assertFalse("Closing AUDIO-01 must release the native handle", engine.isCreated)
            assertFalse("Closing AUDIO-01 must clear the running state", engine.isRunning)
            assertFalse("A closed AUDIO-01 engine must not restart", engine.start())
        } finally {
            engine.close()
        }
    }

    private fun awaitDiagnostics(
        engine: NativeAudioEngine,
        failureMessage: String,
        predicate: (AudioDiagnostics) -> Boolean,
    ): AudioDiagnostics {
        val deadlineMillis = SystemClock.elapsedRealtime() + DiagnosticTimeoutMillis
        var latest = engine.diagnostics()
        while (!predicate(latest) && SystemClock.elapsedRealtime() < deadlineMillis) {
            SystemClock.sleep(DiagnosticPollMillis)
            latest = engine.diagnostics()
        }
        assertTrue("$failureMessage; latest=$latest", predicate(latest))
        return latest
    }

    private fun enqueueFullParameterProbe(engine: NativeAudioEngine, cycle: Int) {
        val patch = SynthPatch()
            .withTimbre(0.10F + cycle.toFloat() * 0.07F)
            .withParameter(SynthParameter.CUTOFF, 2_000.0F + cycle.toFloat() * 1_000.0F)
            .withParameter(SynthParameter.RESONANCE, cycle.toFloat() * 0.05F)
        patch.toAudioCommands().forEach { command ->
            assertTrue(
                "AUDIO-01 cycle $cycle rejected parameter ${command.parameter.name}",
                engine.send(command),
            )
        }
        assertTrue(
            "AUDIO-01 cycle $cycle rejected a silent callback probe",
            engine.send(AudioCommand.Panic),
        )
    }

    private companion object {
        const val AudioLogTag = "IntervalAudioTest"
        const val CycleCount = 10
        const val StreamDwellMillis = 200L
        const val DiagnosticPollMillis = 25L
        const val DiagnosticTimeoutMillis = 2_500L
    }
}
