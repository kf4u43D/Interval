package dev.intervaltablet.midi

import dev.intervaltablet.domain.MidiMessage

data class MidiInputConnectionKey(
    val sourceSessionId: String,
    val generation: Long,
)

/**
 * Owns parser state for the current Android input connection and prevents partial bytes
 * or running status from crossing a source/generation boundary.
 */
class MidiConnectionParser(
    maxSysExBytes: Int = MidiMessageParser.DEFAULT_MAX_SYSEX_BYTES,
) {
    private val parser = MidiMessageParser(maxSysExBytes)

    var activeConnection: MidiInputConnectionKey? = null
        private set

    val diagnostics: MidiParserDiagnostics get() = parser.diagnostics

    fun consume(packet: MidiInputPacket): List<MidiMessage> {
        val connection = MidiInputConnectionKey(
            sourceSessionId = packet.source.stableSessionId,
            generation = packet.generation,
        )
        if (connection != activeConnection) {
            parser.reset()
            activeConnection = connection
        }
        return parser.consume(
            bytes = packet.bytes,
            offset = 0,
            count = packet.bytes.size,
            timestampNanos = packet.timestampNanos,
        )
    }

    fun resetConnection() {
        parser.reset()
        activeConnection = null
    }

    fun clearDiagnostics() {
        parser.clearDiagnostics()
    }
}
