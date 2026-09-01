package dev.intervaltablet.data

import dev.intervaltablet.domain.MidiAction
import dev.intervaltablet.domain.MidiBindingKey
import dev.intervaltablet.domain.MidiMapping
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Versioned app-side representation; the pure domain never depends on serialization or I/O. */
object MidiMappingSerializer {
    private const val SCHEMA_VERSION: Int = 1
    private const val MAX_SERIALIZED_CHARS: Int = 1_048_576
    private const val MAX_BINDINGS: Int = 4_352
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encode(mapping: MidiMapping): String {
        val bindings = mapping.bindings.entries
            .sortedWith(
                compareBy<Map.Entry<MidiBindingKey, MidiAction>>(
                    { it.key.kind.ordinal },
                    { it.key.number },
                    { it.key.channel ?: -1 },
                ),
            )
            .map { (key, action) ->
                StoredMidiBinding(
                    kind = key.kind.name,
                    number = key.number,
                    channel = key.channel,
                    threshold = mapping.ccThresholds[key],
                    action = action.toStoredAction(),
                )
            }
        return json.encodeToString(StoredMidiMapping(schemaVersion = SCHEMA_VERSION, bindings = bindings))
    }

    fun decode(serialized: String): MidiMapping? = runCatching {
        require(serialized.length <= MAX_SERIALIZED_CHARS)
        val stored = json.decodeFromString<StoredMidiMapping>(serialized)
        require(stored.schemaVersion == SCHEMA_VERSION)
        require(stored.bindings.size <= MAX_BINDINGS)
        val bindings = linkedMapOf<MidiBindingKey, MidiAction>()
        val thresholds = linkedMapOf<MidiBindingKey, Int>()
        stored.bindings.forEach { binding ->
            val key = MidiBindingKey(
                kind = MidiBindingKey.Kind.valueOf(binding.kind),
                number = binding.number,
                channel = binding.channel,
            )
            require(bindings.put(key, binding.action.toDomainAction()) == null) {
                "Duplicate MIDI binding: $key"
            }
            binding.threshold?.let { thresholds[key] = it }
        }
        MidiMapping(bindings = bindings, ccThresholds = thresholds)
    }.getOrNull()
}

@Serializable
private data class StoredMidiMapping(
    val schemaVersion: Int,
    val bindings: List<StoredMidiBinding>,
)

@Serializable
private data class StoredMidiBinding(
    val kind: String,
    val number: Int,
    val channel: Int? = null,
    val threshold: Int? = null,
    val action: StoredMidiAction,
)

@Serializable
private data class StoredMidiAction(
    val type: String,
    val value: Int? = null,
    val enabled: Boolean? = null,
)

private fun MidiAction.toStoredAction(): StoredMidiAction {
    return when (this) {
        is MidiAction.Move -> StoredMidiAction("move", value = steps)
        is MidiAction.Chromatic -> StoredMidiAction("chromatic", value = semitones)
        is MidiAction.ChromaticShift -> StoredMidiAction("chromatic_shift", value = semitones)
        is MidiAction.UndoThenMove -> StoredMidiAction("undo_then_move", value = steps)
        MidiAction.Undo -> StoredMidiAction("undo")
        MidiAction.Same -> StoredMidiAction("same")
        MidiAction.SamePitch -> StoredMidiAction("same_pitch")
        MidiAction.Random -> StoredMidiAction("random")
        is MidiAction.Home -> StoredMidiAction("home", enabled = sound)
        is MidiAction.Octave -> StoredMidiAction("octave", value = octaves)
        MidiAction.Panic -> StoredMidiAction("panic")
        MidiAction.TogglePassThrough -> StoredMidiAction("toggle_pass_through")
        MidiAction.Play -> StoredMidiAction("play")
        MidiAction.Stop -> StoredMidiAction("stop")
        MidiAction.Record -> StoredMidiAction("record")
    }
}

private fun StoredMidiAction.toDomainAction(): MidiAction {
    return when (type) {
        "move" -> MidiAction.Move(requireNotNull(value))
        "chromatic" -> MidiAction.Chromatic(requireNotNull(value))
        "chromatic_shift" -> MidiAction.ChromaticShift(requireNotNull(value))
        "undo_then_move" -> MidiAction.UndoThenMove(requireNotNull(value))
        "undo" -> MidiAction.Undo
        "same" -> MidiAction.Same
        "same_pitch" -> MidiAction.SamePitch
        "random" -> MidiAction.Random
        "home" -> MidiAction.Home(requireNotNull(enabled))
        "octave" -> MidiAction.Octave(requireNotNull(value))
        "panic" -> MidiAction.Panic
        "toggle_pass_through" -> MidiAction.TogglePassThrough
        "play" -> MidiAction.Play
        "stop" -> MidiAction.Stop
        "record" -> MidiAction.Record
        else -> error("Unknown MIDI action type: $type")
    }
}
