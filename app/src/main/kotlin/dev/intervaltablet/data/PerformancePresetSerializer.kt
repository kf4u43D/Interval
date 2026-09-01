package dev.intervaltablet.data

import dev.intervaltablet.domain.DefaultMidiMap
import dev.intervaltablet.domain.MAX_INTERVAL_STEPS
import dev.intervaltablet.domain.MIN_INTERVAL_STEPS
import dev.intervaltablet.domain.MidiMapping
import dev.intervaltablet.domain.PadArticulation
import dev.intervaltablet.domain.PassThroughMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val PRESET_SLOT_COUNT: Int = 128
const val CURRENT_PRESET_SCHEMA: Int = 3

data class MusicalContextSnapshot(
    val rootPitchClass: Int = 0,
    val scaleId: String = "major",
    val chordId: String = "off",
    val padArticulation: PadArticulation = PadArticulation.ARPEGGIATED,
    val rangeMin: Int = 36,
    val rangeMax: Int = 95,
    val solfegeWrap: Boolean = true,
) {
    init {
        require(rootPitchClass in 0..11)
        require(scaleId.isNotBlank() && scaleId.length <= MAX_IDENTIFIER_CHARS)
        require(chordId.isNotBlank() && chordId.length <= MAX_IDENTIFIER_CHARS)
        require(rangeMin in 0..127)
        require(rangeMax in rangeMin..127)
    }
}

data class RoutingSnapshot(
    val passThroughMode: PassThroughMode = PassThroughMode.ACTIVE,
    val inputChannel: Int? = null,
    val outputChannel: Int = 0,
    val preferredSourceIdentity: String? = null,
    val preferredDestinationIdentity: String? = null,
) {
    init {
        require(inputChannel == null || inputChannel in 0..15)
        require(outputChannel in 0..15)
        require(
            preferredSourceIdentity == null ||
                preferredSourceIdentity.isNotBlank() && preferredSourceIdentity.length <= MAX_PORT_IDENTITY_CHARS,
        )
        require(
            preferredDestinationIdentity == null ||
                preferredDestinationIdentity.isNotBlank() &&
                preferredDestinationIdentity.length <= MAX_PORT_IDENTITY_CHARS,
        )
    }
}

data class ToneRowEntrySnapshot(
    val relativeDegree: Int,
    val recordedMidiNote: Int,
    val velocity: Int,
) {
    init {
        require(relativeDegree in -127..127)
        require(recordedMidiNote in 0..127)
        require(velocity in 1..127)
    }
}

enum class ToneRowPlaybackSnapshotMode { PRIME, RETRO, RANDOM, PENDULUM }

data class ToneRowSnapshot(
    val entries: List<ToneRowEntrySnapshot> = emptyList(),
    val intervalSequence: List<Int> = listOf(1),
    val playMode: ToneRowPlaybackSnapshotMode = ToneRowPlaybackSnapshotMode.PRIME,
    val inverted: Boolean = false,
    val transpositionSemitones: Int = 0,
    val translation: Int = 0,
    val octaveOffset: Int = 0,
    val randomState: Long = DEFAULT_RANDOM_SEED,
    val referenceRootPitchClass: Int = 0,
    val referenceScaleId: String = "major",
) {
    init {
        require(entries.size <= MAX_TONE_ROW_ENTRIES)
        require(entries.map { Math.floorMod(it.recordedMidiNote, 12) }.distinct().size == entries.size)
        require(intervalSequence.isNotEmpty() && intervalSequence.size <= MAX_SEQUENCE_STEPS)
        require(intervalSequence.all { it in MIN_INTERVAL_STEPS..MAX_INTERVAL_STEPS })
        require(transpositionSemitones in -127..127)
        require(translation in -127..127)
        require(octaveOffset in -10..10)
        require(referenceRootPitchClass in 0..11)
        require(referenceScaleId.isNotBlank() && referenceScaleId.length <= MAX_IDENTIFIER_CHARS)
    }
}

enum class StoredClockSource { INTERNAL, MIDI }

data class TransportOptionsSnapshot(
    val tempoBpm: Int = 120,
    val noteDurationPercent: Int = 75,
    val clocksPerStep: Int = 6,
    val clockSource: StoredClockSource = StoredClockSource.INTERNAL,
) {
    init {
        require(tempoBpm in MIN_TEMPO_BPM..MAX_TEMPO_BPM)
        require(noteDurationPercent in 1..100)
        require(clocksPerStep in 1..MAX_CLOCKS_PER_STEP)
    }
}

/**
 * Complete, transport-safe snapshot. Running/pressed-note state is deliberately absent:
 * restoring a preset never emits MIDI or resumes playback by itself.
 */
data class PerformancePresetSnapshot(
    val name: String = "Init",
    val musicalContext: MusicalContextSnapshot = MusicalContextSnapshot(),
    val routing: RoutingSnapshot = RoutingSnapshot(),
    val toneRow: ToneRowSnapshot = ToneRowSnapshot(),
    val transport: TransportOptionsSnapshot = TransportOptionsSnapshot(),
    val midiMapping: MidiMapping = DefaultMidiMap.mapping,
) {
    init {
        require(name.isNotBlank() && name.length <= MAX_PRESET_NAME_CHARS)
    }
}

data class PresetBank(
    val presets: Map<Int, PerformancePresetSnapshot> = emptyMap(),
) {
    init {
        require(presets.size <= PRESET_SLOT_COUNT)
        require(presets.keys.all { it in 0 until PRESET_SLOT_COUNT })
    }

    operator fun get(slot: Int): PerformancePresetSnapshot? = presets[slot]

    fun contains(slot: Int): Boolean {
        require(slot in 0 until PRESET_SLOT_COUNT)
        return slot in presets
    }

    fun save(slot: Int, preset: PerformancePresetSnapshot): PresetBank {
        require(slot in 0 until PRESET_SLOT_COUNT)
        return copy(presets = presets + (slot to preset))
    }

    fun recall(slot: Int): PerformancePresetSnapshot? {
        require(slot in 0 until PRESET_SLOT_COUNT)
        return presets[slot]
    }

    fun delete(slot: Int): PresetBank {
        require(slot in 0 until PRESET_SLOT_COUNT)
        return copy(presets = presets - slot)
    }
}

/** Bounded, versioned JSON codec shared by DataStore autosave and user preset slots. */
object PerformancePresetSerializer {
    private const val BANK_SCHEMA: Int = 2
    private const val MAX_SERIALIZED_CHARS: Int = 4 * 1_048_576
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encode(preset: PerformancePresetSnapshot): String {
        val serialized = json.encodeToString(preset.toStoredV3())
        require(serialized.length <= MAX_SERIALIZED_CHARS) { "Preset exceeds DataStore payload bound" }
        return serialized
    }

    /** Accepts every released schema and migrates it to the current immutable model. */
    fun decode(serialized: String): PerformancePresetSnapshot? = runCatching {
        require(serialized.length <= MAX_SERIALIZED_CHARS)
        val element = json.parseToJsonElement(serialized)
        when (element.jsonObject["schemaVersion"]?.jsonPrimitive?.intOrNull) {
            1 -> json.decodeFromString<StoredPerformancePresetV1>(serialized).toSnapshot()
            2 -> json.decodeFromString<StoredPerformancePresetV2>(serialized).toSnapshot()
            CURRENT_PRESET_SCHEMA -> json.decodeFromString<StoredPerformancePresetV3>(serialized).toSnapshot()
            else -> error("Unsupported preset schema")
        }
    }.getOrNull()

    fun encodeBank(bank: PresetBank): String {
        val stored = StoredPresetBankV2(
            schemaVersion = BANK_SCHEMA,
            slots = bank.presets.entries.sortedBy { it.key }.map { (slot, preset) ->
                StoredPresetSlotV2(slot = slot, preset = preset.toStoredV3())
            },
        )
        val serialized = json.encodeToString(stored)
        require(serialized.length <= MAX_SERIALIZED_CHARS) { "Preset bank exceeds DataStore payload bound" }
        return serialized
    }

    fun decodeBank(serialized: String): PresetBank? = runCatching {
        require(serialized.length <= MAX_SERIALIZED_CHARS)
        val element = json.parseToJsonElement(serialized)
        val schema = element.jsonObject["schemaVersion"]?.jsonPrimitive?.intOrNull
        val presets = linkedMapOf<Int, PerformancePresetSnapshot>()
        when (schema) {
            1 -> {
                val stored = json.decodeFromString<StoredPresetBankV1>(serialized)
                require(stored.slots.size <= PRESET_SLOT_COUNT)
                stored.slots.forEach { slot ->
                    putPresetSlot(presets, slot.slot, slot.preset.toSnapshot())
                }
            }
            BANK_SCHEMA -> {
                val stored = json.decodeFromString<StoredPresetBankV2>(serialized)
                require(stored.slots.size <= PRESET_SLOT_COUNT)
                stored.slots.forEach { slot ->
                    putPresetSlot(presets, slot.slot, slot.preset.toSnapshot())
                }
            }
            else -> error("Unsupported preset bank schema")
        }
        PresetBank(presets)
    }.getOrNull()
}

@Serializable
private data class StoredMusicalContext(
    val rootPitchClass: Int = 0,
    val scaleId: String = "major",
    val chordId: String = "off",
    val padArticulation: String? = null,
    val rangeMin: Int = 36,
    val rangeMax: Int = 95,
    val solfegeWrap: Boolean = true,
)

@Serializable
private data class StoredRouting(
    val passThroughMode: String = PassThroughMode.ACTIVE.name,
    val inputChannel: Int? = null,
    val outputChannel: Int = 0,
    val preferredSourceIdentity: String? = null,
    val preferredDestinationIdentity: String? = null,
)

@Serializable
private data class StoredToneRowEntry(
    val relativeDegree: Int,
    val recordedMidiNote: Int,
    val velocity: Int,
)

@Serializable
private data class StoredToneRow(
    val entries: List<StoredToneRowEntry> = emptyList(),
    val intervalSequence: List<Int> = listOf(1),
    val playMode: String = ToneRowPlaybackSnapshotMode.PRIME.name,
    val inverted: Boolean = false,
    val transpositionSemitones: Int = 0,
    val translation: Int = 0,
    val octaveOffset: Int = 0,
    val randomState: Long = DEFAULT_RANDOM_SEED,
    val referenceRootPitchClass: Int = 0,
    val referenceScaleId: String = "major",
)

@Serializable
private data class StoredTransport(
    val tempoBpm: Int = 120,
    val noteDurationPercent: Int = 75,
    val clocksPerStep: Int = 6,
    val clockSource: String = StoredClockSource.INTERNAL.name,
)

@Serializable
private data class StoredPerformancePresetV2(
    val schemaVersion: Int = 2,
    val name: String = "Init",
    val musicalContext: StoredMusicalContext = StoredMusicalContext(),
    val routing: StoredRouting = StoredRouting(),
    val toneRow: StoredToneRow = StoredToneRow(),
    val transport: StoredTransport = StoredTransport(),
    val serializedMidiMapping: String,
)

@Serializable
private data class StoredPerformancePresetV3(
    val schemaVersion: Int = CURRENT_PRESET_SCHEMA,
    val name: String = "Init",
    val musicalContext: StoredMusicalContext = StoredMusicalContext(),
    val routing: StoredRouting = StoredRouting(),
    val toneRow: StoredToneRow = StoredToneRow(),
    val transport: StoredTransport = StoredTransport(),
    val serializedMidiMapping: String,
)

/** Historical flat shape retained solely as an explicit migration fixture. */
@Serializable
private data class StoredPerformancePresetV1(
    val schemaVersion: Int,
    val name: String = "Migrated",
    val rootPitchClass: Int = 0,
    val scaleId: String = "major",
    val chordId: String = "off",
    val rangeMin: Int = 36,
    val rangeMax: Int = 95,
    val solfegeWrap: Boolean = true,
    val passThroughMode: String = PassThroughMode.ACTIVE.name,
    val inputChannel: Int? = null,
    val outputChannel: Int = 0,
    val preferredSourceIdentity: String? = null,
    val preferredDestinationIdentity: String? = null,
    val serializedMidiMapping: String? = null,
    val toneRow: List<StoredToneRowEntry> = emptyList(),
    val intervalSequence: List<Int> = listOf(1),
    val tempoBpm: Int = 120,
    val noteDurationPercent: Int = 75,
    val clocksPerStep: Int = 6,
)

@Serializable
private data class StoredPresetBankV1(
    val schemaVersion: Int,
    val slots: List<StoredPresetSlotV1>,
)

@Serializable
private data class StoredPresetSlotV1(
    val slot: Int,
    val preset: StoredPerformancePresetV2,
)

@Serializable
private data class StoredPresetBankV2(
    val schemaVersion: Int,
    val slots: List<StoredPresetSlotV2>,
)

@Serializable
private data class StoredPresetSlotV2(
    val slot: Int,
    val preset: StoredPerformancePresetV3,
)

private fun PerformancePresetSnapshot.toStoredV3(): StoredPerformancePresetV3 {
    return StoredPerformancePresetV3(
        name = name,
        musicalContext = StoredMusicalContext(
            rootPitchClass = musicalContext.rootPitchClass,
            scaleId = musicalContext.scaleId,
            chordId = musicalContext.chordId,
            padArticulation = musicalContext.padArticulation.toStoredId(),
            rangeMin = musicalContext.rangeMin,
            rangeMax = musicalContext.rangeMax,
            solfegeWrap = musicalContext.solfegeWrap,
        ),
        routing = StoredRouting(
            passThroughMode = routing.passThroughMode.name,
            inputChannel = routing.inputChannel,
            outputChannel = routing.outputChannel,
            preferredSourceIdentity = routing.preferredSourceIdentity,
            preferredDestinationIdentity = routing.preferredDestinationIdentity,
        ),
        toneRow = StoredToneRow(
            entries = toneRow.entries.map(ToneRowEntrySnapshot::toStored),
            intervalSequence = toneRow.intervalSequence,
            playMode = toneRow.playMode.name,
            inverted = toneRow.inverted,
            transpositionSemitones = toneRow.transpositionSemitones,
            translation = toneRow.translation,
            octaveOffset = toneRow.octaveOffset,
            randomState = toneRow.randomState,
            referenceRootPitchClass = toneRow.referenceRootPitchClass,
            referenceScaleId = toneRow.referenceScaleId,
        ),
        transport = StoredTransport(
            tempoBpm = transport.tempoBpm,
            noteDurationPercent = transport.noteDurationPercent,
            clocksPerStep = transport.clocksPerStep,
            clockSource = transport.clockSource.name,
        ),
        serializedMidiMapping = MidiMappingSerializer.encode(midiMapping),
    )
}

private fun StoredPerformancePresetV2.toSnapshot(): PerformancePresetSnapshot {
    require(schemaVersion == 2)
    return PerformancePresetSnapshot(
        name = name,
        musicalContext = musicalContext.toLegacySnapshot(),
        routing = routing.toSnapshot(),
        toneRow = toneRow.toSnapshot(),
        transport = transport.toSnapshot(),
        midiMapping = requireNotNull(MidiMappingSerializer.decode(serializedMidiMapping)),
    )
}

private fun StoredPerformancePresetV3.toSnapshot(): PerformancePresetSnapshot {
    require(schemaVersion == CURRENT_PRESET_SCHEMA)
    return PerformancePresetSnapshot(
        name = name,
        musicalContext = musicalContext.toSnapshot(),
        routing = routing.toSnapshot(),
        toneRow = toneRow.toSnapshot(),
        transport = transport.toSnapshot(),
        midiMapping = requireNotNull(MidiMappingSerializer.decode(serializedMidiMapping)),
    )
}

private fun StoredPerformancePresetV1.toSnapshot(): PerformancePresetSnapshot {
    require(schemaVersion == 1)
    return PerformancePresetSnapshot(
        name = name,
        musicalContext = MusicalContextSnapshot(
            rootPitchClass = rootPitchClass,
            scaleId = scaleId,
            chordId = chordId,
            padArticulation = inferLegacyPadArticulation(chordId),
            rangeMin = rangeMin,
            rangeMax = rangeMax,
            solfegeWrap = solfegeWrap,
        ),
        routing = RoutingSnapshot(
            passThroughMode = PassThroughMode.valueOf(passThroughMode),
            inputChannel = inputChannel,
            outputChannel = outputChannel,
            preferredSourceIdentity = preferredSourceIdentity,
            preferredDestinationIdentity = preferredDestinationIdentity,
        ),
        toneRow = ToneRowSnapshot(
            entries = toneRow.map(StoredToneRowEntry::toSnapshot),
            intervalSequence = intervalSequence,
            referenceRootPitchClass = rootPitchClass,
            referenceScaleId = scaleId,
        ),
        transport = TransportOptionsSnapshot(
            tempoBpm = tempoBpm,
            noteDurationPercent = noteDurationPercent,
            clocksPerStep = clocksPerStep,
        ),
        midiMapping = if (serializedMidiMapping == null) {
            DefaultMidiMap.mapping
        } else {
            requireNotNull(MidiMappingSerializer.decode(serializedMidiMapping))
        },
    )
}

private fun StoredMusicalContext.toSnapshot(): MusicalContextSnapshot = MusicalContextSnapshot(
    rootPitchClass = rootPitchClass,
    scaleId = scaleId,
    chordId = chordId,
    padArticulation = decodePadArticulation(padArticulation, chordId),
    rangeMin = rangeMin,
    rangeMax = rangeMax,
    solfegeWrap = solfegeWrap,
)

private fun StoredMusicalContext.toLegacySnapshot(): MusicalContextSnapshot = MusicalContextSnapshot(
    rootPitchClass = rootPitchClass,
    scaleId = scaleId,
    chordId = chordId,
    padArticulation = inferLegacyPadArticulation(chordId),
    rangeMin = rangeMin,
    rangeMax = rangeMax,
    solfegeWrap = solfegeWrap,
)

private fun StoredRouting.toSnapshot(): RoutingSnapshot = RoutingSnapshot(
    passThroughMode = PassThroughMode.valueOf(passThroughMode),
    inputChannel = inputChannel,
    outputChannel = outputChannel,
    preferredSourceIdentity = preferredSourceIdentity,
    preferredDestinationIdentity = preferredDestinationIdentity,
)

private fun StoredToneRow.toSnapshot(): ToneRowSnapshot = ToneRowSnapshot(
    entries = entries.map(StoredToneRowEntry::toSnapshot),
    intervalSequence = intervalSequence,
    playMode = ToneRowPlaybackSnapshotMode.valueOf(playMode),
    inverted = inverted,
    transpositionSemitones = transpositionSemitones,
    translation = translation,
    octaveOffset = octaveOffset,
    randomState = randomState,
    referenceRootPitchClass = referenceRootPitchClass,
    referenceScaleId = referenceScaleId,
)

private fun StoredTransport.toSnapshot(): TransportOptionsSnapshot = TransportOptionsSnapshot(
    tempoBpm = tempoBpm,
    noteDurationPercent = noteDurationPercent,
    clocksPerStep = clocksPerStep,
    clockSource = StoredClockSource.valueOf(clockSource),
)

private fun ToneRowEntrySnapshot.toStored(): StoredToneRowEntry = StoredToneRowEntry(
    relativeDegree = relativeDegree,
    recordedMidiNote = recordedMidiNote,
    velocity = velocity,
)

private fun StoredToneRowEntry.toSnapshot(): ToneRowEntrySnapshot = ToneRowEntrySnapshot(
    relativeDegree = relativeDegree,
    recordedMidiNote = recordedMidiNote,
    velocity = velocity,
)

private fun putPresetSlot(
    presets: MutableMap<Int, PerformancePresetSnapshot>,
    slot: Int,
    preset: PerformancePresetSnapshot,
) {
    require(slot in 0 until PRESET_SLOT_COUNT)
    require(presets.put(slot, preset) == null) { "Duplicate preset slot $slot" }
}

internal fun PadArticulation.toStoredId(): String = when (this) {
    PadArticulation.ARPEGGIATED -> "arpeggiated"
    PadArticulation.STACKED -> "stacked"
    PadArticulation.MUTED -> "muted"
}

internal fun decodePadArticulation(value: String?, chordId: String): PadArticulation {
    return when (value) {
        "arpeggiated" -> PadArticulation.ARPEGGIATED
        "stacked" -> PadArticulation.STACKED
        "muted" -> PadArticulation.MUTED
        else -> inferLegacyPadArticulation(chordId)
    }
}

internal fun inferLegacyPadArticulation(chordId: String): PadArticulation {
    return if (chordId == "off") PadArticulation.ARPEGGIATED else PadArticulation.STACKED
}

private const val MAX_IDENTIFIER_CHARS: Int = 96
private const val MAX_PORT_IDENTITY_CHARS: Int = 512
private const val MAX_PRESET_NAME_CHARS: Int = 64
private const val MAX_TONE_ROW_ENTRIES: Int = 12
private const val MAX_SEQUENCE_STEPS: Int = 64
private const val MAX_CLOCKS_PER_STEP: Int = 96
private const val DEFAULT_RANDOM_SEED: Long = 0x49544C54424C
private const val MIN_TEMPO_BPM: Int = 20
private const val MAX_TEMPO_BPM: Int = 300
