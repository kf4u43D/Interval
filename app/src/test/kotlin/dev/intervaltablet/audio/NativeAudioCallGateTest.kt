package dev.intervaltablet.audio

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAudioCallGateTest {
    @Test
    fun concurrentSendsAreStrictlySerialized() {
        val producerLock = ReentrantLock()
        val gate = NativeAudioCallGate(producerLock = producerLock)
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val order = CopyOnWriteArrayList<String>()

        val first = startTask("audio-send-first") {
            gate.withSerializedSend {
                order += "first-enter"
                firstEntered.countDown()
                require(releaseFirst.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                order += "first-exit"
            }
        }
        assertTrue(firstEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        val second = startTask("audio-send-second") {
            gate.withSerializedSend {
                order += "second-enter"
                secondEntered.countDown()
            }
        }
        awaitQueued(
            message = "second send must queue behind the active producer",
            isQueued = { producerLock.hasQueuedThread(second.thread) },
        )
        assertEquals(1L, secondEntered.count)

        releaseFirst.countDown()
        first.await()
        second.await()
        assertEquals(listOf("first-enter", "first-exit", "second-enter"), order)
    }

    @Test
    fun diagnosticsCanRunWhileASendOwnsTheProducerLock() {
        val gate = NativeAudioCallGate()
        val sendEntered = CountDownLatch(1)
        val releaseSend = CountDownLatch(1)
        val diagnosticsEntered = CountDownLatch(1)

        val send = startTask("audio-send") {
            gate.withSerializedSend {
                sendEntered.countDown()
                require(releaseSend.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            }
        }
        assertTrue(sendEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        val diagnostics = startTask("audio-diagnostics") {
            gate.withLifecycleRead { diagnosticsEntered.countDown() }
        }
        assertTrue(
            "diagnostics must not wait for the producer lock",
            diagnosticsEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )

        releaseSend.countDown()
        diagnostics.await()
        send.await()
    }

    @Test
    fun lifecycleWriteWaitsForCurrentSendAndExcludesTheNextSend() {
        val lifecycleLock = ReentrantReadWriteLock()
        val gate = NativeAudioCallGate(lifecycleLock = lifecycleLock)
        val firstSendEntered = CountDownLatch(1)
        val releaseFirstSend = CountDownLatch(1)
        val writerEntered = CountDownLatch(1)
        val releaseWriter = CountDownLatch(1)
        val secondSendEntered = CountDownLatch(1)
        val order = CopyOnWriteArrayList<String>()

        val firstSend = startTask("audio-send-before-close") {
            gate.withSerializedSend {
                order += "send"
                firstSendEntered.countDown()
                require(releaseFirstSend.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            }
        }
        assertTrue(firstSendEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        val lifecycleWriter = startTask("audio-lifecycle-writer") {
            gate.withLifecycleWrite {
                order += "lifecycle"
                writerEntered.countDown()
                require(releaseWriter.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            }
        }
        awaitQueued(
            message = "lifecycle write must wait for the in-flight send",
            isQueued = { lifecycleLock.hasQueuedThread(lifecycleWriter.thread) },
        )
        assertEquals(1L, writerEntered.count)

        releaseFirstSend.countDown()
        assertTrue(writerEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        val secondSend = startTask("audio-send-after-close") {
            gate.withSerializedSend {
                order += "next-send"
                secondSendEntered.countDown()
            }
        }
        awaitQueued(
            message = "new send must wait while lifecycle write is active",
            isQueued = { lifecycleLock.hasQueuedThread(secondSend.thread) },
        )
        assertEquals(1L, secondSendEntered.count)

        releaseWriter.countDown()
        firstSend.await()
        lifecycleWriter.await()
        secondSend.await()
        assertEquals(listOf("send", "lifecycle", "next-send"), order)
    }

    private fun startTask(name: String, block: () -> Unit): RunningTask {
        val future = FutureTask<Unit> {
            block()
            Unit
        }
        val thread = Thread(future, name).apply {
            isDaemon = true
            start()
        }
        return RunningTask(thread, future)
    }

    private fun awaitQueued(message: String, isQueued: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (!isQueued()) {
            if (System.nanoTime() >= deadline) throw AssertionError(message)
            Thread.yield()
        }
    }

    private data class RunningTask(
        val thread: Thread,
        val future: FutureTask<Unit>,
    ) {
        fun await() {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS: Long = 2L
    }
}
