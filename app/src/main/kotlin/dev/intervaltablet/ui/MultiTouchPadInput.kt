package dev.intervaltablet.ui

import androidx.compose.ui.input.pointer.PointerInputScope

/**
 * Small pointer registry kept independent from Compose state so its release guarantees can
 * be covered by a local JVM test. Duplicate downs/ups are deliberately idempotent.
 */
internal class ActivePointerRegistry(
    private val steps: Int,
    private val onDown: (pointerId: Long, steps: Int) -> Unit,
    private val onUp: (pointerId: Long) -> Unit,
) {
    private val activePointerIds = linkedSetOf<Long>()

    val activeCount: Int
        get() = activePointerIds.size

    fun press(pointerId: Long) {
        if (activePointerIds.add(pointerId)) onDown(pointerId, steps)
    }

    fun release(pointerId: Long) {
        if (activePointerIds.remove(pointerId)) onUp(pointerId)
    }

    fun releaseAll() {
        val pending = activePointerIds.toList()
        activePointerIds.clear()
        pending.forEach(onUp)
    }
}

/**
 * Reports every physical pointer independently, including multiple fingers on one pad.
 * Cancellation of the gesture coroutine releases all remaining sources exactly once.
 */
internal suspend fun PointerInputScope.detectIntervalPointers(
    steps: Int,
    onDown: (pointerId: Long, steps: Int) -> Unit,
    onUp: (pointerId: Long) -> Unit,
) {
    awaitPointerEventScope {
        val registry = ActivePointerRegistry(steps, onDown, onUp)
        try {
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { change ->
                    when {
                        change.pressed && !change.previousPressed -> {
                            registry.press(change.id.value)
                            change.consume()
                        }
                        !change.pressed && change.previousPressed -> {
                            registry.release(change.id.value)
                            change.consume()
                        }
                    }
                }
            }
        } finally {
            registry.releaseAll()
        }
    }
}
