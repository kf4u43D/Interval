package dev.intervaltablet.midi

import dev.intervaltablet.domain.MidiMessage

data class MidiParserDiagnostics(
    val oversizedSysExPackets: Long = 0,
    val abortedSysExPackets: Long = 0,
    val ignoredDataBytes: Long = 0,
)

/**
 * Stateful MIDI 1.0 byte-stream parser with running-status and real-time support.
 *
 * A parser instance belongs to exactly one input connection generation. Call [reset]
 * before reusing it for another source. SysEx packets are emitted only when complete and
 * are discarded after [maxSysExBytes] bytes so a malformed sender cannot grow memory
 * without bound.
 */
class MidiMessageParser(
    private val maxSysExBytes: Int = DEFAULT_MAX_SYSEX_BYTES,
) {
    init {
        require(maxSysExBytes >= MIN_SYSEX_BYTES) {
            "maxSysExBytes must accommodate F0 and F7"
        }
    }

    private var runningStatus: Int? = null
    private var pendingStatus: Int? = null
    private val dataBytes = ArrayList<Int>(2)
    private var inSysEx = false
    private var discardingSysEx = false
    private val sysEx = ArrayList<Int>(minOf(maxSysExBytes, INITIAL_SYSEX_CAPACITY))
    private var oversizedSysExPackets: Long = 0
    private var abortedSysExPackets: Long = 0
    private var ignoredDataBytes: Long = 0

    val diagnostics: MidiParserDiagnostics
        get() = MidiParserDiagnostics(
            oversizedSysExPackets = oversizedSysExPackets,
            abortedSysExPackets = abortedSysExPackets,
            ignoredDataBytes = ignoredDataBytes,
        )

    fun consume(bytes: ByteArray, offset: Int, count: Int, timestampNanos: Long): List<MidiMessage> {
        require(offset >= 0 && offset <= bytes.size)
        require(count >= 0 && count <= bytes.size - offset)
        val output = ArrayList<MidiMessage>()
        for (index in offset until offset + count) {
            val value = bytes[index].toInt() and 0xFF
            if (value >= 0xF8) {
                output += MidiMessage.RealTime(value, timestampNanos)
                continue
            }
            if (inSysEx) {
                when {
                    value == 0xF7 -> finishSysEx(output, timestampNanos)
                    value and STATUS_MASK != 0 -> {
                        abortSysEx()
                        processStatus(value, output, timestampNanos)
                    }
                    discardingSysEx -> Unit
                    sysEx.size < maxSysExBytes -> sysEx += value
                    else -> {
                        oversizedSysExPackets += 1
                        discardingSysEx = true
                        sysEx.clear()
                    }
                }
                continue
            }
            if (value and 0x80 != 0) {
                processStatus(value, output, timestampNanos)
                continue
            }

            val status = pendingStatus ?: runningStatus
            if (status == null) {
                ignoredDataBytes += 1
                continue
            }
            dataBytes += value
            if (dataBytes.size == dataLength(status)) {
                output += decode(status, dataBytes, timestampNanos)
                dataBytes.clear()
                pendingStatus = null
            }
        }
        return output
    }

    fun reset() {
        runningStatus = null
        pendingStatus = null
        dataBytes.clear()
        inSysEx = false
        discardingSysEx = false
        sysEx.clear()
    }

    fun clearDiagnostics() {
        oversizedSysExPackets = 0
        abortedSysExPackets = 0
        ignoredDataBytes = 0
    }

    private fun processStatus(
        status: Int,
        output: MutableList<MidiMessage>,
        timestampNanos: Long,
    ) {
        dataBytes.clear()
        when {
            status == 0xF0 -> {
                // Every System Common status, including SysEx start, cancels running status.
                runningStatus = null
                pendingStatus = null
                inSysEx = true
                discardingSysEx = false
                sysEx.clear()
                sysEx += status
            }
            status >= 0xF0 -> {
                runningStatus = null
                pendingStatus = status
                if (dataLength(status) == 0) {
                    output += MidiMessage.Raw(listOf(status), timestampNanos)
                    pendingStatus = null
                }
            }
            else -> {
                runningStatus = status
                pendingStatus = status
            }
        }
    }

    private fun finishSysEx(output: MutableList<MidiMessage>, timestampNanos: Long) {
        if (!discardingSysEx && sysEx.size < maxSysExBytes) {
            sysEx += 0xF7
            output += MidiMessage.Raw(sysEx.toList(), timestampNanos)
        } else if (!discardingSysEx) {
            oversizedSysExPackets += 1
        }
        inSysEx = false
        discardingSysEx = false
        sysEx.clear()
    }

    private fun abortSysEx() {
        abortedSysExPackets += 1
        inSysEx = false
        discardingSysEx = false
        sysEx.clear()
    }

    private fun decode(status: Int, data: List<Int>, timestampNanos: Long): MidiMessage {
        if (status == 0xF3) return MidiMessage.SongSelect(data[0], timestampNanos)
        val channel = status and 0x0F
        return when (status and 0xF0) {
            0x80 -> MidiMessage.NoteOff(channel, data[0], data[1], timestampNanos)
            0x90 -> if (data[1] == 0) {
                MidiMessage.NoteOff(channel, data[0], 0, timestampNanos)
            } else {
                MidiMessage.NoteOn(channel, data[0], data[1], timestampNanos)
            }
            0xA0 -> MidiMessage.PolyPressure(channel, data[0], data[1], timestampNanos)
            0xB0 -> MidiMessage.ControlChange(channel, data[0], data[1], timestampNanos)
            0xC0 -> MidiMessage.ProgramChange(channel, data[0], timestampNanos)
            0xD0 -> MidiMessage.ChannelPressure(channel, data[0], timestampNanos)
            0xE0 -> MidiMessage.PitchBend(channel, data[0] or (data[1] shl 7), timestampNanos)
            else -> MidiMessage.Raw(listOf(status) + data, timestampNanos)
        }
    }

    private fun dataLength(status: Int): Int {
        return when (status and 0xF0) {
            0xC0, 0xD0 -> 1
            in 0x80..0xE0 -> 2
            else -> when (status) {
                0xF1, 0xF3 -> 1
                0xF2 -> 2
                else -> 0
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_SYSEX_BYTES = 65_536
        private const val STATUS_MASK = 0x80
        private const val MIN_SYSEX_BYTES = 2
        private const val INITIAL_SYSEX_CAPACITY = 256
    }
}

fun MidiMessage.toByteArray(): ByteArray? {
    return when (this) {
        is MidiMessage.NoteOn -> byteArrayOf((0x90 or channel).toByte(), note.toByte(), velocity.toByte())
        is MidiMessage.NoteOff -> byteArrayOf((0x80 or channel).toByte(), note.toByte(), velocity.toByte())
        is MidiMessage.ControlChange -> byteArrayOf((0xB0 or channel).toByte(), controller.toByte(), value.toByte())
        is MidiMessage.ProgramChange -> byteArrayOf((0xC0 or channel).toByte(), program.toByte())
        is MidiMessage.SongSelect -> byteArrayOf(0xF3.toByte(), song.toByte())
        is MidiMessage.PitchBend -> byteArrayOf(
            (0xE0 or channel).toByte(),
            (value14Bit and 0x7F).toByte(),
            ((value14Bit shr 7) and 0x7F).toByte(),
        )
        is MidiMessage.ChannelPressure -> byteArrayOf((0xD0 or channel).toByte(), pressure.toByte())
        is MidiMessage.PolyPressure -> byteArrayOf(
            (0xA0 or channel).toByte(),
            note.toByte(),
            pressure.toByte(),
        )
        is MidiMessage.RealTime -> byteArrayOf(status.toByte())
        is MidiMessage.Raw -> bytes.map { it.toByte() }.toByteArray()
    }
}
