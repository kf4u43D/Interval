package dev.intervaltablet.domain

sealed interface MidiAction {
    data class Move(val steps: Int) : MidiAction {
        init {
            require(steps in -14..14) { "Move steps must be in -14..14" }
        }
    }

    data class Chromatic(val semitones: Int) : MidiAction {
        init {
            require(semitones in -127..127) { "Chromatic movement must fit the MIDI note space" }
        }
    }

    data class ChromaticShift(val semitones: Int) : MidiAction {
        init {
            require(semitones in -12..12) { "Chromatic shift must be in -12..12" }
        }
    }

    data class UndoThenMove(val steps: Int) : MidiAction {
        init {
            require(steps in -14..14) { "UndoThenMove steps must be in -14..14" }
        }
    }
    data object Undo : MidiAction
    data object Same : MidiAction
    data object SamePitch : MidiAction
    data object Random : MidiAction
    data class Home(val sound: Boolean) : MidiAction
    data class Octave(val octaves: Int) : MidiAction {
        init {
            require(octaves in -10..10) { "Octave movement must be in -10..10" }
        }
    }
    data object Panic : MidiAction
    data object TogglePassThrough : MidiAction
    data object Play : MidiAction
    data object Stop : MidiAction
    data object Record : MidiAction
}

data class MidiBindingKey(
    val kind: Kind,
    val number: Int,
    val channel: Int? = null,
) {
    enum class Kind { NOTE, CC }

    init {
        require(number in 0..127)
        require(channel == null || channel in 0..15)
    }

    fun matches(messageChannel: Int, messageNumber: Int): Boolean {
        return number == messageNumber && (channel == null || channel == messageChannel)
    }
}

data class MidiMapping(
    val bindings: Map<MidiBindingKey, MidiAction>,
    val ccThresholds: Map<MidiBindingKey, Int> = emptyMap(),
) {
    init {
        require(ccThresholds.keys.all { it.kind == MidiBindingKey.Kind.CC }) {
            "Only CC bindings may define a threshold"
        }
        require(ccThresholds.keys.all { it in bindings }) {
            "Every CC threshold must refer to an existing binding"
        }
        require(ccThresholds.values.all { it in 1..127 }) {
            "CC thresholds must be in 1..127"
        }
    }

    fun noteAction(channel: Int, note: Int): MidiAction? = lookup(MidiBindingKey.Kind.NOTE, channel, note)
    fun ccAction(channel: Int, controller: Int): MidiAction? = lookup(MidiBindingKey.Kind.CC, channel, controller)

    fun ccThreshold(channel: Int, controller: Int): Int {
        val exact = MidiBindingKey(MidiBindingKey.Kind.CC, controller, channel)
        val omni = MidiBindingKey(MidiBindingKey.Kind.CC, controller, null)
        val selected = if (bindings.containsKey(exact)) exact else omni
        return ccThresholds[selected] ?: DEFAULT_CC_THRESHOLD
    }

    fun reset(): MidiMapping = DefaultMidiMap.mapping

    private fun lookup(kind: MidiBindingKey.Kind, channel: Int, number: Int): MidiAction? {
        val exact = MidiBindingKey(kind = kind, number = number, channel = channel)
        val omni = MidiBindingKey(kind = kind, number = number, channel = null)
        return bindings[exact] ?: bindings[omni]
    }

    private companion object {
        const val DEFAULT_CC_THRESHOLD: Int = 64
    }
}

fun MidiAction.toInstrumentActions(
    source: TriggerSource,
    velocity: Int,
    timestampNanos: Long,
): List<InstrumentAction> {
    return when (this) {
        is MidiAction.Move -> listOf(InstrumentAction.PressInterval(source, steps, velocity, timestampNanos))
        is MidiAction.Chromatic -> listOf(InstrumentAction.PressChromatic(source, semitones, velocity, timestampNanos))
        is MidiAction.UndoThenMove -> listOf(InstrumentAction.UndoThenMove(source, steps, velocity, timestampNanos))
        MidiAction.Undo -> listOf(InstrumentAction.Undo(source, velocity, timestampNanos))
        MidiAction.Same -> listOf(InstrumentAction.PressSameInterval(source, velocity, timestampNanos))
        MidiAction.SamePitch -> listOf(InstrumentAction.PressSamePitch(source, velocity, timestampNanos))
        MidiAction.Random -> listOf(InstrumentAction.PressRandomInterval(source, velocity, timestampNanos))
        is MidiAction.ChromaticShift -> listOf(
            InstrumentAction.HoldChromaticShift(source, semitones, timestampNanos),
        )
        is MidiAction.Home -> listOf(InstrumentAction.Home(source, sound, velocity, timestampNanos))
        is MidiAction.Octave -> listOf(InstrumentAction.PressChromatic(source, octaves * 12, velocity, timestampNanos))
        MidiAction.Panic -> listOf(InstrumentAction.Panic(timestampNanos))
        MidiAction.TogglePassThrough,
        MidiAction.Play,
        MidiAction.Stop,
        MidiAction.Record,
        -> emptyList()
    }
}

internal fun MidiAction.requiresRelease(): Boolean {
    return when (this) {
        is MidiAction.Move,
        is MidiAction.Chromatic,
        is MidiAction.UndoThenMove,
        MidiAction.Undo,
        MidiAction.Same,
        MidiAction.SamePitch,
        MidiAction.Random,
        is MidiAction.ChromaticShift,
        is MidiAction.Octave,
        -> true
        is MidiAction.Home -> sound
        MidiAction.Panic,
        MidiAction.TogglePassThrough,
        MidiAction.Play,
        MidiAction.Stop,
        MidiAction.Record,
        -> false
    }
}
