package dev.intervaltablet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MidiMessagesTest {
    @Test
    fun songSelectIsTypedTimestampedAndBoundedToSevenBits() {
        val message = MidiMessage.SongSelect(song = 42, timestampNanos = 99L)
        assertEquals(42, message.song)
        assertEquals(99L, message.timestampNanos)
        assertThrows(IllegalArgumentException::class.java) { MidiMessage.SongSelect(-1) }
        assertThrows(IllegalArgumentException::class.java) { MidiMessage.SongSelect(128) }
    }
}
