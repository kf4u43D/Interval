package dev.intervaltablet.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.intervaltablet.domain.PadArticulation
import dev.intervaltablet.domain.PassThroughMode
import dev.intervaltablet.domain.SynthParameter
import dev.intervaltablet.domain.SynthLfoDestination
import dev.intervaltablet.domain.SynthPatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.intervalTabletDataStore by preferencesDataStore(name = "interval_tablet_settings")

private val SCHEMA_VERSION = intPreferencesKey("schema_version")
private val AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
private val ROOT = intPreferencesKey("root_pitch_class")
private val SCALE = stringPreferencesKey("scale_id")
private val CHORD = stringPreferencesKey("chord_id")
private val PAD_ARTICULATION = stringPreferencesKey("pad_articulation")
private val FORCE_TO_SCALE = booleanPreferencesKey("force_to_scale")
private val PASS_MODE = stringPreferencesKey("pass_through_mode")
private val RANGE_MIN = intPreferencesKey("range_min")
private val RANGE_MAX = intPreferencesKey("range_max")
private val SOLFEGE_WRAP = booleanPreferencesKey("solfege_wrap")
private val INPUT_CHANNEL = intPreferencesKey("input_channel")
private val OUTPUT_CHANNEL = intPreferencesKey("output_channel")
private val PREFERRED_SOURCE = stringPreferencesKey("preferred_source_identity")
private val PREFERRED_DESTINATION = stringPreferencesKey("preferred_destination_identity")
private val SERIALIZED_MAPPING = stringPreferencesKey("serialized_midi_mapping")
private val PERFORMANCE_LOCK = booleanPreferencesKey("performance_lock")
private val SYNTH_SAW_MIX = floatPreferencesKey("synth_saw_mix")
private val SYNTH_PULSE_MIX = floatPreferencesKey("synth_pulse_mix")
private val SYNTH_TRIANGLE_MIX = floatPreferencesKey("synth_triangle_mix")
private val SYNTH_PULSE_WIDTH = floatPreferencesKey("synth_pulse_width")
private val SYNTH_ATTACK_SECONDS = floatPreferencesKey("synth_attack_seconds")
private val SYNTH_DECAY_SECONDS = floatPreferencesKey("synth_decay_seconds")
private val SYNTH_SUSTAIN = floatPreferencesKey("synth_sustain")
private val SYNTH_RELEASE_SECONDS = floatPreferencesKey("synth_release_seconds")
private val SYNTH_CUTOFF_HZ = floatPreferencesKey("synth_cutoff_hz")
private val SYNTH_RESONANCE = floatPreferencesKey("synth_resonance")
private val SYNTH_CHORUS_MIX = floatPreferencesKey("synth_chorus_mix")
private val SYNTH_DELAY_TIME_SECONDS = floatPreferencesKey("synth_delay_time_seconds")
private val SYNTH_DELAY_FEEDBACK = floatPreferencesKey("synth_delay_feedback")
private val SYNTH_DELAY_MIX = floatPreferencesKey("synth_delay_mix")
private val SYNTH_REVERB_MIX = floatPreferencesKey("synth_reverb_mix")
private val SYNTH_MASTER_GAIN = floatPreferencesKey("synth_master_gain")
private val SYNTH_FILTER_ATTACK_SECONDS = floatPreferencesKey("synth_filter_attack_seconds")
private val SYNTH_FILTER_DECAY_SECONDS = floatPreferencesKey("synth_filter_decay_seconds")
private val SYNTH_FILTER_SUSTAIN = floatPreferencesKey("synth_filter_sustain")
private val SYNTH_FILTER_RELEASE_SECONDS = floatPreferencesKey("synth_filter_release_seconds")
private val SYNTH_FILTER_ENV_AMOUNT = floatPreferencesKey("synth_filter_env_amount")
private val SYNTH_DRIVE = floatPreferencesKey("synth_drive")
private val SYNTH_LFO_RATE = floatPreferencesKey("synth_lfo_rate")
private val SYNTH_LFO_DEPTH = floatPreferencesKey("synth_lfo_depth")
private val SYNTH_LFO_DESTINATION = floatPreferencesKey("synth_lfo_destination")
private val SYNTH_LFO_DELAY = floatPreferencesKey("synth_lfo_delay")
private val SYNTH_DELAY_SYNC_BEATS = floatPreferencesKey("synth_delay_sync_beats")
private val SYNTH_TEMPO_BPM = floatPreferencesKey("synth_tempo_bpm")
private val WORKING_PRESET = stringPreferencesKey("working_preset_json")
private val PRESET_BANK = stringPreferencesKey("preset_bank_json")
private val SELECTED_PRESET_SLOT = intPreferencesKey("selected_preset_slot")

data class StoredSettings(
    val schemaVersion: Int = CURRENT_SETTINGS_SCHEMA,
    val audioMonitorEnabled: Boolean = true,
    val rootPitchClass: Int = 0,
    val scaleId: String = "major",
    val chordId: String = "off",
    val padArticulation: PadArticulation = PadArticulation.ARPEGGIATED,
    val forceToScale: Boolean = false,
    val passThroughMode: PassThroughMode = PassThroughMode.ACTIVE,
    val rangeMin: Int = 36,
    val rangeMax: Int = 95,
    val solfegeWrap: Boolean = true,
    val inputChannel: Int? = null,
    val outputChannel: Int = 0,
    val preferredSourceIdentity: String? = null,
    val preferredDestinationIdentity: String? = null,
    val serializedMidiMapping: String? = null,
    val performanceLock: Boolean = false,
    val synthPatch: SynthPatch = SynthPatch(),
    val workingPreset: PerformancePresetSnapshot? = null,
    val presetBank: PresetBank = PresetBank(),
    val selectedPresetSlot: Int? = null,
) {
    init {
        require(schemaVersion in 0..CURRENT_SETTINGS_SCHEMA)
        require(rootPitchClass in 0..11)
        require(scaleId.isNotBlank() && scaleId.length <= MAX_STORED_IDENTIFIER_CHARS)
        require(chordId.isNotBlank() && chordId.length <= MAX_STORED_IDENTIFIER_CHARS)
        require(rangeMin in 0..127)
        require(rangeMax in rangeMin..127)
        require(inputChannel == null || inputChannel in 0..15)
        require(outputChannel in 0..15)
        require(
            preferredSourceIdentity == null ||
                preferredSourceIdentity.isNotBlank() && preferredSourceIdentity.length <= MAX_STORED_PORT_IDENTITY_CHARS,
        )
        require(
            preferredDestinationIdentity == null ||
                preferredDestinationIdentity.isNotBlank() &&
                preferredDestinationIdentity.length <= MAX_STORED_PORT_IDENTITY_CHARS,
        )
        require(selectedPresetSlot == null || selectedPresetSlot in 0 until PRESET_SLOT_COUNT)
    }
}

const val CURRENT_SETTINGS_SCHEMA: Int = 6
private const val PAD_ARTICULATION_SETTINGS_SCHEMA: Int = 3
private const val SYNTH_PATCH_SETTINGS_SCHEMA: Int = 4
private const val FORCE_TO_SCALE_SETTINGS_SCHEMA: Int = 5
private const val EXTENDED_SYNTH_SETTINGS_SCHEMA: Int = 6

/** Pure decoder kept separate from DataStore I/O so migrations and corrupt values are testable. */
internal fun decodeStoredSettings(preferences: Preferences): StoredSettings {
    val storedSchema = preferences[SCHEMA_VERSION] ?: 0
    if (storedSchema !in 0..CURRENT_SETTINGS_SCHEMA) return StoredSettings()

    val storedRangeMin = preferences[RANGE_MIN]?.coerceIn(0, 127) ?: 36
    val storedRangeMax = preferences[RANGE_MAX]?.coerceIn(storedRangeMin, 127) ?: 95
    val storedChordId = preferences[CHORD]
        ?.takeIf { it.isNotBlank() && it.length <= MAX_STORED_IDENTIFIER_CHARS }
        ?: "off"
    val base = StoredSettings(
        schemaVersion = CURRENT_SETTINGS_SCHEMA,
        audioMonitorEnabled = preferences[AUDIO_ENABLED] ?: true,
        rootPitchClass = Math.floorMod(preferences[ROOT] ?: 0, 12),
        scaleId = preferences[SCALE]
            ?.takeIf { it.isNotBlank() && it.length <= MAX_STORED_IDENTIFIER_CHARS }
            ?: "major",
        chordId = storedChordId,
        padArticulation = if (storedSchema >= PAD_ARTICULATION_SETTINGS_SCHEMA) {
            decodePadArticulation(preferences[PAD_ARTICULATION], storedChordId)
        } else {
            inferLegacyPadArticulation(storedChordId)
        },
        forceToScale = if (storedSchema >= FORCE_TO_SCALE_SETTINGS_SCHEMA) {
            preferences[FORCE_TO_SCALE] ?: false
        } else {
            false
        },
        passThroughMode = runCatching {
            PassThroughMode.valueOf(preferences[PASS_MODE] ?: PassThroughMode.ACTIVE.name)
        }.getOrDefault(PassThroughMode.ACTIVE),
        rangeMin = storedRangeMin,
        rangeMax = storedRangeMax,
        solfegeWrap = preferences[SOLFEGE_WRAP] ?: true,
        inputChannel = preferences[INPUT_CHANNEL]?.takeIf { it in 0..15 },
        outputChannel = preferences[OUTPUT_CHANNEL]?.coerceIn(0, 15) ?: 0,
        preferredSourceIdentity = preferences[PREFERRED_SOURCE]
            ?.takeIf { it.isNotBlank() && it.length <= MAX_STORED_PORT_IDENTITY_CHARS },
        preferredDestinationIdentity = preferences[PREFERRED_DESTINATION]
            ?.takeIf { it.isNotBlank() && it.length <= MAX_STORED_PORT_IDENTITY_CHARS },
        serializedMidiMapping = preferences[SERIALIZED_MAPPING],
        performanceLock = preferences[PERFORMANCE_LOCK] ?: false,
        synthPatch = decodeSynthPatch(preferences, storedSchema),
        presetBank = preferences[PRESET_BANK]
            ?.let(PerformancePresetSerializer::decodeBank)
            ?: PresetBank(),
        selectedPresetSlot = preferences[SELECTED_PRESET_SLOT]
            ?.takeIf { it in 0 until PRESET_SLOT_COUNT },
    )
    val persistedWorkingPreset = preferences[WORKING_PRESET]
        ?.let(PerformancePresetSerializer::decode)
    val migratedWorkingPreset = if (storedSchema >= 1 || preferences.asMap().isNotEmpty()) {
        runCatching(base::toWorkingPresetSnapshot).getOrNull()
    } else {
        null
    }
    return base.copy(workingPreset = persistedWorkingPreset ?: migratedWorkingPreset)
}

private fun decodeSynthPatch(preferences: Preferences, storedSchema: Int): SynthPatch {
    if (storedSchema < SYNTH_PATCH_SETTINGS_SCHEMA) return SynthPatch()
    return SynthPatch(
        sawMix = preferences.synthValue(SYNTH_SAW_MIX, SynthParameter.SAW_MIX),
        pulseMix = preferences.synthValue(SYNTH_PULSE_MIX, SynthParameter.PULSE_MIX),
        triangleMix = preferences.synthValue(SYNTH_TRIANGLE_MIX, SynthParameter.TRIANGLE_MIX),
        pulseWidth = preferences.synthValue(SYNTH_PULSE_WIDTH, SynthParameter.PULSE_WIDTH),
        attackSeconds = preferences.synthValue(SYNTH_ATTACK_SECONDS, SynthParameter.ATTACK),
        decaySeconds = preferences.synthValue(SYNTH_DECAY_SECONDS, SynthParameter.DECAY),
        sustain = preferences.synthValue(SYNTH_SUSTAIN, SynthParameter.SUSTAIN),
        releaseSeconds = preferences.synthValue(SYNTH_RELEASE_SECONDS, SynthParameter.RELEASE),
        cutoffHz = preferences.synthValue(SYNTH_CUTOFF_HZ, SynthParameter.CUTOFF),
        resonance = preferences.synthValue(SYNTH_RESONANCE, SynthParameter.RESONANCE),
        chorusMix = preferences.synthValue(SYNTH_CHORUS_MIX, SynthParameter.CHORUS_MIX),
        delayTimeSeconds = preferences.synthValue(SYNTH_DELAY_TIME_SECONDS, SynthParameter.DELAY_TIME),
        delayFeedback = preferences.synthValue(SYNTH_DELAY_FEEDBACK, SynthParameter.DELAY_FEEDBACK),
        delayMix = preferences.synthValue(SYNTH_DELAY_MIX, SynthParameter.DELAY_MIX),
        reverbMix = preferences.synthValue(SYNTH_REVERB_MIX, SynthParameter.REVERB_MIX),
        masterGain = preferences.synthValue(SYNTH_MASTER_GAIN, SynthParameter.MASTER),
        filterAttackSeconds = preferences.extendedSynthValue(
            SYNTH_FILTER_ATTACK_SECONDS,
            SynthParameter.FILTER_ATTACK,
            storedSchema,
        ),
        filterDecaySeconds = preferences.extendedSynthValue(
            SYNTH_FILTER_DECAY_SECONDS,
            SynthParameter.FILTER_DECAY,
            storedSchema,
        ),
        filterSustain = preferences.extendedSynthValue(
            SYNTH_FILTER_SUSTAIN,
            SynthParameter.FILTER_SUSTAIN,
            storedSchema,
        ),
        filterReleaseSeconds = preferences.extendedSynthValue(
            SYNTH_FILTER_RELEASE_SECONDS,
            SynthParameter.FILTER_RELEASE,
            storedSchema,
        ),
        filterEnvelopeAmount = preferences.extendedSynthValue(
            SYNTH_FILTER_ENV_AMOUNT,
            SynthParameter.FILTER_ENV_AMOUNT,
            storedSchema,
        ),
        drive = preferences.extendedSynthValue(SYNTH_DRIVE, SynthParameter.DRIVE, storedSchema),
        lfoRateHz = preferences.extendedSynthValue(SYNTH_LFO_RATE, SynthParameter.LFO_RATE, storedSchema),
        lfoDepth = preferences.extendedSynthValue(SYNTH_LFO_DEPTH, SynthParameter.LFO_DEPTH, storedSchema),
        lfoDestination = SynthLfoDestination.fromWire(
            preferences.extendedSynthValue(
                SYNTH_LFO_DESTINATION,
                SynthParameter.LFO_DESTINATION,
                storedSchema,
            ),
        ),
        lfoDelaySeconds = preferences.extendedSynthValue(
            SYNTH_LFO_DELAY,
            SynthParameter.LFO_DELAY,
            storedSchema,
        ),
        delaySyncBeats = preferences.extendedSynthValue(
            SYNTH_DELAY_SYNC_BEATS,
            SynthParameter.DELAY_SYNC_BEATS,
            storedSchema,
        ),
        tempoBpm = preferences.extendedSynthValue(
            SYNTH_TEMPO_BPM,
            SynthParameter.TEMPO_BPM,
            storedSchema,
        ),
    )
}

private fun Preferences.synthValue(
    key: Preferences.Key<Float>,
    parameter: SynthParameter,
): Float = parameter.sanitize(this[key] ?: parameter.defaultValue)

private fun Preferences.extendedSynthValue(
    key: Preferences.Key<Float>,
    parameter: SynthParameter,
    storedSchema: Int,
): Float = if (storedSchema >= EXTENDED_SYNTH_SETTINGS_SCHEMA) {
    synthValue(key, parameter)
} else {
    parameter.defaultValue
}

private const val MAX_STORED_IDENTIFIER_CHARS: Int = 96
private const val MAX_STORED_PORT_IDENTITY_CHARS: Int = 512

interface SettingsStore {
    val settings: Flow<StoredSettings>

    suspend fun update(settings: StoredSettings)
}

class SettingsRepository(private val context: Context) : SettingsStore {
    override val settings: Flow<StoredSettings> = context.intervalTabletDataStore.data.map(::decodeStoredSettings)

    override suspend fun update(settings: StoredSettings) {
        context.intervalTabletDataStore.edit { preferences ->
            writeStoredSettings(preferences, settings)
        }
    }

}

/** Pure writer paired with [decodeStoredSettings], allowing process-recreation fixtures without Android I/O. */
internal fun writeStoredSettings(preferences: MutablePreferences, settings: StoredSettings) {
    preferences[SCHEMA_VERSION] = CURRENT_SETTINGS_SCHEMA
    preferences[AUDIO_ENABLED] = settings.audioMonitorEnabled
    preferences[ROOT] = settings.rootPitchClass
    preferences[SCALE] = settings.scaleId
    preferences[CHORD] = settings.chordId
    preferences[PAD_ARTICULATION] = settings.padArticulation.toStoredId()
    preferences[FORCE_TO_SCALE] = settings.forceToScale
    preferences[PASS_MODE] = settings.passThroughMode.name
    preferences[RANGE_MIN] = settings.rangeMin
    preferences[RANGE_MAX] = settings.rangeMax
    preferences[SOLFEGE_WRAP] = settings.solfegeWrap
    settings.inputChannel?.let { preferences[INPUT_CHANNEL] = it } ?: preferences.remove(INPUT_CHANNEL)
    preferences[OUTPUT_CHANNEL] = settings.outputChannel
    settings.preferredSourceIdentity?.let { preferences[PREFERRED_SOURCE] = it }
        ?: preferences.remove(PREFERRED_SOURCE)
    settings.preferredDestinationIdentity?.let { preferences[PREFERRED_DESTINATION] = it }
        ?: preferences.remove(PREFERRED_DESTINATION)
    settings.serializedMidiMapping?.let { preferences[SERIALIZED_MAPPING] = it }
        ?: preferences.remove(SERIALIZED_MAPPING)
    preferences[PERFORMANCE_LOCK] = settings.performanceLock
    preferences[SYNTH_SAW_MIX] = settings.synthPatch.sawMix
    preferences[SYNTH_PULSE_MIX] = settings.synthPatch.pulseMix
    preferences[SYNTH_TRIANGLE_MIX] = settings.synthPatch.triangleMix
    preferences[SYNTH_PULSE_WIDTH] = settings.synthPatch.pulseWidth
    preferences[SYNTH_ATTACK_SECONDS] = settings.synthPatch.attackSeconds
    preferences[SYNTH_DECAY_SECONDS] = settings.synthPatch.decaySeconds
    preferences[SYNTH_SUSTAIN] = settings.synthPatch.sustain
    preferences[SYNTH_RELEASE_SECONDS] = settings.synthPatch.releaseSeconds
    preferences[SYNTH_CUTOFF_HZ] = settings.synthPatch.cutoffHz
    preferences[SYNTH_RESONANCE] = settings.synthPatch.resonance
    preferences[SYNTH_CHORUS_MIX] = settings.synthPatch.chorusMix
    preferences[SYNTH_DELAY_TIME_SECONDS] = settings.synthPatch.delayTimeSeconds
    preferences[SYNTH_DELAY_FEEDBACK] = settings.synthPatch.delayFeedback
    preferences[SYNTH_DELAY_MIX] = settings.synthPatch.delayMix
    preferences[SYNTH_REVERB_MIX] = settings.synthPatch.reverbMix
    preferences[SYNTH_MASTER_GAIN] = settings.synthPatch.masterGain
    preferences[SYNTH_FILTER_ATTACK_SECONDS] = settings.synthPatch.filterAttackSeconds
    preferences[SYNTH_FILTER_DECAY_SECONDS] = settings.synthPatch.filterDecaySeconds
    preferences[SYNTH_FILTER_SUSTAIN] = settings.synthPatch.filterSustain
    preferences[SYNTH_FILTER_RELEASE_SECONDS] = settings.synthPatch.filterReleaseSeconds
    preferences[SYNTH_FILTER_ENV_AMOUNT] = settings.synthPatch.filterEnvelopeAmount
    preferences[SYNTH_DRIVE] = settings.synthPatch.drive
    preferences[SYNTH_LFO_RATE] = settings.synthPatch.lfoRateHz
    preferences[SYNTH_LFO_DEPTH] = settings.synthPatch.lfoDepth
    preferences[SYNTH_LFO_DESTINATION] = settings.synthPatch.lfoDestination.wireValue
    preferences[SYNTH_LFO_DELAY] = settings.synthPatch.lfoDelaySeconds
    preferences[SYNTH_DELAY_SYNC_BEATS] = settings.synthPatch.delaySyncBeats
    preferences[SYNTH_TEMPO_BPM] = settings.synthPatch.tempoBpm

    val workingPreset = settings.workingPreset ?: settings.toWorkingPresetSnapshot()
    preferences[WORKING_PRESET] = PerformancePresetSerializer.encode(workingPreset)
    if (settings.presetBank.presets.isEmpty()) {
        preferences.remove(PRESET_BANK)
    } else {
        preferences[PRESET_BANK] = PerformancePresetSerializer.encodeBank(settings.presetBank)
    }
    settings.selectedPresetSlot?.let { preferences[SELECTED_PRESET_SLOT] = it }
        ?: preferences.remove(SELECTED_PRESET_SLOT)
}

private fun StoredSettings.toWorkingPresetSnapshot(): PerformancePresetSnapshot {
    val mapping = serializedMidiMapping
        ?.let(MidiMappingSerializer::decode)
        ?: dev.intervaltablet.domain.DefaultMidiMap.mapping
    return PerformancePresetSnapshot(
        name = "Working Session",
        musicalContext = MusicalContextSnapshot(
            rootPitchClass = rootPitchClass,
            scaleId = scaleId,
            chordId = chordId,
            padArticulation = padArticulation,
            forceToScale = forceToScale,
            rangeMin = rangeMin,
            rangeMax = rangeMax,
            solfegeWrap = solfegeWrap,
        ),
        routing = RoutingSnapshot(
            passThroughMode = passThroughMode,
            inputChannel = inputChannel,
            outputChannel = outputChannel,
            preferredSourceIdentity = preferredSourceIdentity,
            preferredDestinationIdentity = preferredDestinationIdentity,
        ),
        toneRow = ToneRowSnapshot(
            referenceRootPitchClass = rootPitchClass,
            referenceScaleId = scaleId,
        ),
        midiMapping = mapping,
    )
}
