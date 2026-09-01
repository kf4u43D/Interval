package dev.intervaltablet

import dev.intervaltablet.domain.MidiMessage
import dev.intervaltablet.midi.MidiConnectionParser
import dev.intervaltablet.midi.MidiInputPacket
import dev.intervaltablet.midi.MidiMessageParser
import dev.intervaltablet.midi.MidiPortDescriptor
import dev.intervaltablet.midi.MidiPortDirection
import dev.intervaltablet.midi.toByteArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiMessageParserTest {
    @Test
    fun parsesRunningStatusAndVelocityZero() {
        val parser = MidiMessageParser()
        val messages = parser.consume(
            byteArrayOf(0x90.toByte(), 60, 100, 62, 0),
            0,
            5,
            123L,
        )
        assertEquals(
            listOf(
                MidiMessage.NoteOn(0, 60, 100, 123L),
                MidiMessage.NoteOff(0, 62, 0, 123L),
            ),
            messages,
        )
    }

    @Test
    fun realtimeDoesNotBreakPendingOrRunningStatus() {
        val parser = MidiMessageParser()
        val messages = parser.consume(
            byteArrayOf(
                0x90.toByte(),
                60,
                0xF8.toByte(),
                100,
                0xFE.toByte(),
                62,
                110,
            ),
            0,
            7,
            7L,
        )
        assertEquals(
            listOf(
                MidiMessage.RealTime(0xF8, 7L),
                MidiMessage.NoteOn(0, 60, 100, 7L),
                MidiMessage.RealTime(0xFE, 7L),
                MidiMessage.NoteOn(0, 62, 110, 7L),
            ),
            messages,
        )
    }

    @Test
    fun parsesClockTransportProgramChangeAndTypedSongSelectFromOneByteStream() {
        val parser = MidiMessageParser()
        val messages = parser.consume(
            byteArrayOf(
                0xF8.toByte(),
                0xFA.toByte(),
                0xFB.toByte(),
                0xFC.toByte(),
                0xC2.toByte(), 12,
                0xF3.toByte(), 127,
            ),
            0,
            8,
            456L,
        )

        assertEquals(
            listOf(
                MidiMessage.RealTime(0xF8, 456L),
                MidiMessage.RealTime(0xFA, 456L),
                MidiMessage.RealTime(0xFB, 456L),
                MidiMessage.RealTime(0xFC, 456L),
                MidiMessage.ProgramChange(2, 12, 456L),
                MidiMessage.SongSelect(127, 456L),
            ),
            messages,
        )
    }

    @Test
    fun clockInsideSongSelectDoesNotInterruptItsDataByte() {
        val parser = MidiMessageParser()

        assertEquals(
            listOf(
                MidiMessage.RealTime(0xF8, 999L),
                MidiMessage.SongSelect(64, 999L),
            ),
            parser.consume(
                byteArrayOf(0xF3.toByte(), 0xF8.toByte(), 64),
                0,
                3,
                999L,
            ),
        )
    }

    @Test
    fun parsesEveryChannelVoiceFamilyAcrossSingleByteFragments() {
        val parser = MidiMessageParser()
        val bytes = byteArrayOf(
            0x82.toByte(), 60, 4,
            0x93.toByte(), 61, 100,
            0xA4.toByte(), 62, 70,
            0xB5.toByte(), 7, 99,
            0xC6.toByte(), 12,
            0xD7.toByte(), 45,
            0xE8.toByte(), 1, 64,
        )

        val messages = bytes.flatMap { byte ->
            parser.consume(byteArrayOf(byte), 0, 1, 99L)
        }

        assertEquals(
            listOf(
                MidiMessage.NoteOff(2, 60, 4, 99L),
                MidiMessage.NoteOn(3, 61, 100, 99L),
                MidiMessage.PolyPressure(4, 62, 70, 99L),
                MidiMessage.ControlChange(5, 7, 99, 99L),
                MidiMessage.ProgramChange(6, 12, 99L),
                MidiMessage.ChannelPressure(7, 45, 99L),
                MidiMessage.PitchBend(8, 8193, 99L),
            ),
            messages,
        )
    }

    @Test
    fun systemCommonMessagesCancelRunningStatus() {
        val systemMessages = listOf(
            byteArrayOf(0xF1.toByte(), 1),
            byteArrayOf(0xF2.toByte(), 1, 2),
            byteArrayOf(0xF3.toByte(), 3),
            byteArrayOf(0xF6.toByte()),
            byteArrayOf(0xF7.toByte()),
        )

        for (systemMessage in systemMessages) {
            val parser = MidiMessageParser()
            parser.consume(byteArrayOf(0x90.toByte(), 60, 100), 0, 3, 1L)
            val result = parser.consume(systemMessage + byteArrayOf(62, 100), 0, systemMessage.size + 2, 2L)
            assertFalse(
                "System status ${systemMessage.first().toInt() and 0xFF} retained running status",
                result.any { it is MidiMessage.NoteOn },
            )
        }
    }

    @Test
    fun sysexCancelsRunningStatusAndKeepsRealtimeOutOfPacket() {
        val parser = MidiMessageParser()
        val messages = parser.consume(
            byteArrayOf(
                0x90.toByte(), 60, 100,
                0xF0.toByte(), 0x7D, 1, 0xF8.toByte(), 0xF7.toByte(),
                62, 100,
            ),
            0,
            10,
            42L,
        )

        assertEquals(
            listOf(
                MidiMessage.NoteOn(0, 60, 100, 42L),
                MidiMessage.RealTime(0xF8, 42L),
                MidiMessage.Raw(listOf(0xF0, 0x7D, 1, 0xF7), 42L),
            ),
            messages,
        )
        assertEquals(2L, parser.diagnostics.ignoredDataBytes)
    }

    @Test
    fun completeSysexCanSpanArbitraryFragments() {
        val parser = MidiMessageParser()
        assertTrue(parser.consume(byteArrayOf(0xF0.toByte(), 0x7D), 0, 2, 1L).isEmpty())
        assertTrue(parser.consume(byteArrayOf(1, 2), 0, 2, 2L).isEmpty())
        assertEquals(
            listOf(MidiMessage.Raw(listOf(0xF0, 0x7D, 1, 2, 0xF7), 3L)),
            parser.consume(byteArrayOf(0xF7.toByte()), 0, 1, 3L),
        )
    }

    @Test
    fun oversizedSysexIsDiscardedUntilEoxThenParserRecovers() {
        val parser = MidiMessageParser(maxSysExBytes = 4)
        val messages = parser.consume(
            byteArrayOf(
                0xF0.toByte(), 1, 2, 3, 0xF8.toByte(), 0xF7.toByte(),
                0x90.toByte(), 64, 100,
            ),
            0,
            9,
            55L,
        )

        assertEquals(
            listOf(
                MidiMessage.RealTime(0xF8, 55L),
                MidiMessage.NoteOn(0, 64, 100, 55L),
            ),
            messages,
        )
        assertEquals(1L, parser.diagnostics.oversizedSysExPackets)
    }

    @Test
    fun channelStatusAbortsMalformedSysexAndIsReprocessed() {
        val parser = MidiMessageParser()
        val messages = parser.consume(
            byteArrayOf(0xF0.toByte(), 0x7D, 0x90.toByte(), 60, 100),
            0,
            5,
            8L,
        )

        assertEquals(listOf(MidiMessage.NoteOn(0, 60, 100, 8L)), messages)
        assertEquals(1L, parser.diagnostics.abortedSysExPackets)
    }

    @Test
    fun resetPreventsPartialMessageFromCrossingSourceGeneration() {
        val parser = MidiMessageParser()
        assertTrue(parser.consume(byteArrayOf(0x90.toByte(), 60), 0, 2, 1L).isEmpty())

        parser.reset()

        assertTrue(parser.consume(byteArrayOf(100), 0, 1, 2L).isEmpty())
        assertEquals(
            listOf(MidiMessage.NoteOn(1, 65, 90, 3L)),
            parser.consume(byteArrayOf(0x91.toByte(), 65, 90), 0, 3, 3L),
        )
        assertEquals(1L, parser.diagnostics.ignoredDataBytes)
        parser.clearDiagnostics()
        assertEquals(0L, parser.diagnostics.ignoredDataBytes)
    }

    @Test
    fun connectionParserResetsAutomaticallyWhenGenerationChanges() {
        val source = MidiPortDescriptor(1, 0, MidiPortDirection.SOURCE, "Keys", "Out")
        val parser = MidiConnectionParser()
        assertTrue(
            parser.consume(MidiInputPacket(source, 4, byteArrayOf(0x90.toByte(), 60), 1L)).isEmpty(),
        )

        assertTrue(
            parser.consume(MidiInputPacket(source, 5, byteArrayOf(100), 2L)).isEmpty(),
        )
        assertEquals(
            listOf(MidiMessage.NoteOn(0, 62, 90, 3L)),
            parser.consume(
                MidiInputPacket(source, 5, byteArrayOf(0x90.toByte(), 62, 90), 3L),
            ),
        )
        assertEquals(5L, parser.activeConnection?.generation)
    }

    @Test
    fun incompleteSysexCannotCrossConnectionGeneration() {
        val source = MidiPortDescriptor(1, 0, MidiPortDirection.SOURCE, "Keys", "Out")
        val parser = MidiConnectionParser()
        assertTrue(
            parser.consume(
                MidiInputPacket(source, 7, byteArrayOf(0xF0.toByte(), 0x7D, 1), 1L),
            ).isEmpty(),
        )

        assertEquals(
            listOf(MidiMessage.Raw(listOf(0xF7), 2L)),
            parser.consume(MidiInputPacket(source, 8, byteArrayOf(0xF7.toByte()), 2L)),
        )
        assertEquals(0L, parser.diagnostics.abortedSysExPackets)
    }

    @Test
    fun minimalBoundAcceptsEmptySysexExactly() {
        val parser = MidiMessageParser(maxSysExBytes = 2)

        assertEquals(
            listOf(MidiMessage.Raw(listOf(0xF0, 0xF7), 6L)),
            parser.consume(byteArrayOf(0xF0.toByte(), 0xF7.toByte()), 0, 2, 6L),
        )
        assertEquals(0L, parser.diagnostics.oversizedSysExPackets)
    }

    @Test
    fun offsetAndCountAreValidatedWithoutIntegerOverflow() {
        val parser = MidiMessageParser()
        val framed = byteArrayOf(1, 0x90.toByte(), 60, 100, 2)
        assertEquals(
            listOf(MidiMessage.NoteOn(0, 60, 100, 4L)),
            parser.consume(framed, 1, 3, 4L),
        )
        assertThrows(IllegalArgumentException::class.java) {
            parser.consume(framed, Int.MAX_VALUE, 1, 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parser.consume(framed, 4, 2, 0L)
        }
    }

    @Test
    fun encoderCoversEveryMessageFamily() {
        val fixtures = listOf(
            MidiMessage.NoteOn(1, 60, 100) to byteArrayOf(0x91.toByte(), 60, 100),
            MidiMessage.NoteOff(2, 61, 3) to byteArrayOf(0x82.toByte(), 61, 3),
            MidiMessage.PolyPressure(3, 62, 4) to byteArrayOf(0xA3.toByte(), 62, 4),
            MidiMessage.ControlChange(4, 7, 99) to byteArrayOf(0xB4.toByte(), 7, 99),
            MidiMessage.ProgramChange(5, 10) to byteArrayOf(0xC5.toByte(), 10),
            MidiMessage.SongSelect(127) to byteArrayOf(0xF3.toByte(), 127),
            MidiMessage.ChannelPressure(6, 11) to byteArrayOf(0xD6.toByte(), 11),
            MidiMessage.PitchBend(7, 8193) to byteArrayOf(0xE7.toByte(), 1, 64),
            MidiMessage.RealTime(0xFA) to byteArrayOf(0xFA.toByte()),
            MidiMessage.Raw(listOf(0xF6)) to byteArrayOf(0xF6.toByte()),
        )

        for ((message, expected) in fixtures) {
            assertArrayEquals(expected, message.toByteArray())
        }
    }
}
