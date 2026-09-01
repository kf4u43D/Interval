package dev.intervaltablet.domain

sealed interface MidiMessage {
    val timestampNanos: Long

    data class NoteOn(
        val channel: Int,
        val note: Int,
        val velocity: Int,
        override val timestampNanos: Long = 0L,
    ) : MidiMessage {
        init {
            require(channel in 0..15)
            require(note in 0..127)
            require(velocity in 1..127)
        }
    }

    data class NoteOff(
        val channel: Int,
        val note: Int,
        val velocity: Int = 0,
        override val timestampNanos: Long = 0L,
    ) : MidiMessage {
        init {
            require(channel in 0..15)
            require(note in 0..127)
            require(velocity in 0..127)
        }
    }

    data class ControlChange(
        val channel: Int,
        val controller: Int,
        val value: Int,
        override val timestampNanos: Long = 0L,
    ) : MidiMessage {
        init {
            require(channel in 0..15)
            require(controller in 0..127)
            require(value in 0..127)
        }
    }

    data class ProgramChange(
        val channel: Int,
        val program: Int,
        override val timestampNanos: Long = 0L,
    ) : MidiMessage {
        init {
            require(channel in 0..15)
            require(program in 0..127)
        }
    }

    /** MIDI System Common Song Select (F3), used by the documented preset policy. */
    data class SongSelect(
        val song: Int,
        override val timestampNanos: Long = 0L,
    ) : MidiMessage {
        init {
            require(song in 0..127)
        }
    }

    data class PitchBend(
        val channel: Int,
        val value14Bit: Int,
        override val timestampNanos: Long = 0L,
    ) : MidiMessage {
        init {
            require(channel in 0..15)
            require(value14Bit in 0..16_383)
        }
    }

    data class ChannelPressure(
        val channel: Int,
        val pressure: Int,
        override val timestampNanos: Long = 0L,
    ) : MidiMessage {
        init {
            require(channel in 0..15)
            require(pressure in 0..127)
        }
    }

    data class PolyPressure(
        val channel: Int,
        val note: Int,
        val pressure: Int,
        override val timestampNanos: Long = 0L,
    ) : MidiMessage {
        init {
            require(channel in 0..15)
            require(note in 0..127)
            require(pressure in 0..127)
        }
    }

    data class RealTime(
        val status: Int,
        override val timestampNanos: Long = 0L,
    ) : MidiMessage {
        init {
            require(status in 0xF8..0xFF)
        }
    }

    data class Raw(
        val bytes: List<Int>,
        override val timestampNanos: Long = 0L,
    ) : MidiMessage {
        init {
            require(bytes.isNotEmpty())
            require(bytes.all { it in 0..255 })
        }
    }
}

sealed interface TriggerSource {
    data class Touch(val pointerId: Long) : TriggerSource
    data class Midi(
        val deviceId: Int,
        val portNumber: Int,
        val channel: Int,
        val note: Int,
    ) : TriggerSource
    data class System(val id: String) : TriggerSource
}

sealed interface AudioCommand {
    data class NoteOn(val note: Int, val velocity: Int) : AudioCommand
    data class NoteOff(val note: Int) : AudioCommand
    data object Panic : AudioCommand
    data class Parameter(val parameter: SynthParameter, val value: Float) : AudioCommand {
        init {
            require(parameter.accepts(value)) {
                "Invalid ${parameter.name} value: $value"
            }
        }
    }
}

sealed interface OutputEvent {
    data class MidiOut(val message: MidiMessage) : OutputEvent
    data class Audio(val command: AudioCommand) : OutputEvent
}
