package dev.intervaltablet.audio

import dev.intervaltablet.domain.AudioCommand
import java.io.Closeable
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write

/**
 * Coordinates calls that cross the JNI ownership boundary.
 *
 * Lock order is always producer lock, then lifecycle read lock. Lifecycle writers never acquire
 * the producer lock, so start/stop/close can safely wait for the sole in-flight send without a
 * lock cycle. Diagnostics acquire only the lifecycle read lock and therefore remain independent
 * from producer serialization.
 */
internal class NativeAudioCallGate(
    private val lifecycleLock: ReentrantReadWriteLock = ReentrantReadWriteLock(),
    private val producerLock: ReentrantLock = ReentrantLock(),
) {
    fun <T> withLifecycleRead(block: () -> T): T = lifecycleLock.read(block)

    fun <T> withLifecycleWrite(block: () -> T): T = lifecycleLock.write(block)

    fun <T> withSerializedSend(block: () -> T): T = producerLock.withLock {
        lifecycleLock.read(block)
    }
}

interface AudioMonitor : Closeable {
    val isAvailable: Boolean
    val isRunning: Boolean

    fun start(): Boolean
    fun stop()
    fun send(command: AudioCommand): Boolean
    fun diagnostics(): AudioDiagnostics
}

class NativeAudioEngine : AudioMonitor {
    private val callGate = NativeAudioCallGate()
    private var handle: Long = 0L
    @Volatile
    private var running: Boolean = false
    private var closed: Boolean = false

    override val isAvailable: Boolean get() = nativeLibraryLoaded
    val isCreated: Boolean get() = callGate.withLifecycleRead { handle != 0L }
    override val isRunning: Boolean get() = running

    override fun start(): Boolean = callGate.withLifecycleWrite {
        if (!nativeLibraryLoaded || closed) return@withLifecycleWrite false
        if (handle == 0L) handle = nativeCreate()
        running = handle != 0L && nativeStart(handle)
        running
    }

    override fun stop(): Unit = callGate.withLifecycleWrite {
        if (handle != 0L) nativeStop(handle)
        running = false
    }

    override fun send(command: AudioCommand): Boolean = callGate.withSerializedSend {
        val currentHandle = handle
        if (currentHandle == 0L || closed) return@withSerializedSend false
        when (command) {
            is AudioCommand.NoteOn -> nativeNoteOn(currentHandle, command.note, command.velocity)
            is AudioCommand.NoteOff -> nativeNoteOff(currentHandle, command.note)
            AudioCommand.Panic -> nativePanic(currentHandle)
            is AudioCommand.Parameter -> nativeSetParameter(
                currentHandle,
                command.parameter.wireId,
                command.value,
            )
        }
    }

    override fun diagnostics(): AudioDiagnostics = callGate.withLifecycleRead {
        val currentHandle = handle
        if (currentHandle == 0L || closed) return@withLifecycleRead AudioDiagnostics()
        val values = nativeDiagnostics(currentHandle)
        val diagnostics = AudioDiagnostics(
            sampleRate = values.getOrElse(0) { 0 },
            framesPerBurst = values.getOrElse(1) { 0 },
            xRunCount = values.getOrElse(2) { 0 },
            droppedEvents = values.getOrElse(3) { 0 },
            currentQueueDepth = values.getOrElse(4) { 0 },
            maximumQueueDepth = values.getOrElse(5) { 0 },
            streamRunning = values.getOrElse(6) { 0 } != 0,
            restartCount = values.getOrElse(7) { 0 },
            lastErrorCode = values.getOrElse(8) { 0 },
            bufferSizeFrames = values.getOrElse(9) { 0 },
            recoveryPending = values.getOrElse(10) { 0 } != 0,
        )
        running = diagnostics.streamRunning
        diagnostics
    }

    override fun close(): Unit = callGate.withLifecycleWrite {
        if (closed) return@withLifecycleWrite
        closed = true
        val currentHandle = handle
        handle = 0L
        running = false
        if (currentHandle != 0L) nativeDestroy(currentHandle)
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeStart(handle: Long): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeNoteOn(handle: Long, note: Int, velocity: Int): Boolean
    private external fun nativeNoteOff(handle: Long, note: Int): Boolean
    private external fun nativePanic(handle: Long): Boolean
    private external fun nativeSetParameter(handle: Long, parameterId: Int, value: Float): Boolean
    private external fun nativeDiagnostics(handle: Long): IntArray

    private companion object {
        val nativeLibraryLoaded: Boolean = runCatching {
            System.loadLibrary("interval_audio")
            true
        }.getOrDefault(false)
    }
}

data class AudioDiagnostics(
    val sampleRate: Int = 0,
    val framesPerBurst: Int = 0,
    val xRunCount: Int = 0,
    val droppedEvents: Int = 0,
    val currentQueueDepth: Int = 0,
    val maximumQueueDepth: Int = 0,
    val streamRunning: Boolean = false,
    val restartCount: Int = 0,
    val lastErrorCode: Int = 0,
    val bufferSizeFrames: Int = 0,
    val recoveryPending: Boolean = false,
)
