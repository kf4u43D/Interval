package dev.intervaltablet.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivePointerRegistryTest {
    @Test
    fun twoPointersOnSamePadRemainIndependentAndReleaseExactlyOnce() {
        val events = mutableListOf<String>()
        val registry = ActivePointerRegistry(
            steps = 3,
            onDown = { pointerId, steps -> events += "down:$pointerId:$steps" },
            onUp = { pointerId -> events += "up:$pointerId" },
        )

        registry.press(41L)
        registry.press(42L)
        registry.press(41L)

        assertEquals(2, registry.activeCount)
        assertEquals(listOf("down:41:3", "down:42:3"), events)

        registry.release(41L)
        registry.release(41L)
        registry.releaseAll()
        registry.releaseAll()

        assertEquals(0, registry.activeCount)
        assertEquals(
            listOf("down:41:3", "down:42:3", "up:41", "up:42"),
            events,
        )
    }
}
