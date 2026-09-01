package dev.intervaltablet

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.intervaltablet.data.CURRENT_SETTINGS_SCHEMA
import dev.intervaltablet.data.MusicalContextSnapshot
import dev.intervaltablet.data.MidiMappingSerializer
import dev.intervaltablet.data.PerformancePresetSnapshot
import dev.intervaltablet.data.PresetBank
import dev.intervaltablet.data.RoutingSnapshot
import dev.intervaltablet.data.StoredSettings
import dev.intervaltablet.data.ToneRowEntrySnapshot
import dev.intervaltablet.data.ToneRowSnapshot
import dev.intervaltablet.data.TransportOptionsSnapshot
import dev.intervaltablet.data.decodeStoredSettings
import dev.intervaltablet.data.writeStoredSettings
import dev.intervaltablet.domain.DefaultMidiMap
import dev.intervaltablet.domain.MidiAction
import dev.intervaltablet.domain.MidiBindingKey
import dev.intervaltablet.domain.MidiMapping
import dev.intervaltablet.domain.PadArticulation
import dev.intervaltablet.domain.PassThroughMode
import dev.intervaltablet.domain.SynthPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryMigrationTest {
    @Test
    fun emptyLegacyPreferencesMigrateToCurrentDefaults() {
        assertEquals(StoredSettings(), decodeStoredSettings(emptyPreferences()))
    }

    @Test
    fun legacyValuesAreMigratedAndDefensivelyBounded() {
        val preferences = mutablePreferencesOf(
            intPreferencesKey("schema_version") to 0,
            intPreferencesKey("root_pitch_class") to -1,
            intPreferencesKey("range_min") to 200,
            intPreferencesKey("range_max") to -50,
            intPreferencesKey("input_channel") to 99,
            intPreferencesKey("output_channel") to -4,
            stringPreferencesKey("pass_through_mode") to "not-a-mode",
            stringPreferencesKey("scale_id") to "",
            stringPreferencesKey("chord_id") to "x".repeat(97),
            stringPreferencesKey("preferred_source_identity") to "",
            stringPreferencesKey("preferred_destination_identity") to "x".repeat(513),
            booleanPreferencesKey("audio_enabled") to false,
        )

        val decoded = decodeStoredSettings(preferences)

        assertEquals(CURRENT_SETTINGS_SCHEMA, decoded.schemaVersion)
        assertEquals(11, decoded.rootPitchClass)
        assertEquals(127, decoded.rangeMin)
        assertEquals(127, decoded.rangeMax)
        assertNull(decoded.inputChannel)
        assertEquals(0, decoded.outputChannel)
        assertEquals(PassThroughMode.ACTIVE, decoded.passThroughMode)
        assertFalse(decoded.audioMonitorEnabled)
        assertEquals("major", decoded.scaleId)
        assertEquals("off", decoded.chordId)
        assertEquals(PadArticulation.ARPEGGIATED, decoded.padArticulation)
        assertEquals(SynthPatch(), decoded.synthPatch)
        assertNull(decoded.preferredSourceIdentity)
        assertNull(decoded.preferredDestinationIdentity)
    }

    @Test
    fun schemasZeroThroughThreeUseDefaultPatchAndSchemaThreeKeepsArticulation() {
        for (schema in 0..3) {
            val preferences = mutablePreferencesOf(
                intPreferencesKey("schema_version") to schema,
                stringPreferencesKey("pad_articulation") to "muted",
                floatPreferencesKey("synth_saw_mix") to 0.0f,
                floatPreferencesKey("synth_master_gain") to 1.5f,
            )

            val decoded = decodeStoredSettings(preferences)

            assertEquals("schema $schema", SynthPatch(), decoded.synthPatch)
            assertEquals(
                "schema $schema articulation",
                if (schema == 3) PadArticulation.MUTED else PadArticulation.ARPEGGIATED,
                decoded.padArticulation,
            )
        }
    }

    @Test
    fun futureSchemaIsRejectedAsAWhole() {
        val preferences = mutablePreferencesOf(
            intPreferencesKey("schema_version") to (CURRENT_SETTINGS_SCHEMA + 1),
            intPreferencesKey("root_pitch_class") to 7,
            stringPreferencesKey("scale_id") to "chromatic",
            booleanPreferencesKey("performance_lock") to true,
        )

        assertEquals(StoredSettings(), decodeStoredSettings(preferences))
    }

    @Test
    fun corruptGateTwoPayloadsFallBackIndependentlyToLegacySnapshot() {
        val preferences = mutablePreferencesOf(
            intPreferencesKey("schema_version") to CURRENT_SETTINGS_SCHEMA,
            intPreferencesKey("root_pitch_class") to 5,
            stringPreferencesKey("scale_id") to "blues",
            stringPreferencesKey("working_preset_json") to "not-json",
            stringPreferencesKey("preset_bank_json") to "also-not-json",
            intPreferencesKey("selected_preset_slot") to 500,
        )

        val decoded = decodeStoredSettings(preferences)

        assertEquals(5, decoded.workingPreset?.musicalContext?.rootPitchClass)
        assertEquals("blues", decoded.workingPreset?.musicalContext?.scaleId)
        assertTrue(decoded.presetBank.presets.isEmpty())
        assertNull(decoded.selectedPresetSlot)
    }

    @Test
    fun currentSchemaRetainsEveryPersistedBoundary() {
        val preferences = mutablePreferencesOf(
            intPreferencesKey("schema_version") to CURRENT_SETTINGS_SCHEMA,
            booleanPreferencesKey("audio_enabled") to false,
            intPreferencesKey("root_pitch_class") to 11,
            stringPreferencesKey("scale_id") to "minor",
            stringPreferencesKey("chord_id") to "fifth",
            stringPreferencesKey("pad_articulation") to "muted",
            stringPreferencesKey("pass_through_mode") to PassThroughMode.PASS_THRU.name,
            intPreferencesKey("range_min") to 0,
            intPreferencesKey("range_max") to 127,
            booleanPreferencesKey("solfege_wrap") to false,
            intPreferencesKey("input_channel") to 15,
            intPreferencesKey("output_channel") to 15,
            stringPreferencesKey("preferred_source_identity") to "source-key",
            stringPreferencesKey("preferred_destination_identity") to "destination-key",
            stringPreferencesKey("serialized_midi_mapping") to "mapping-json",
            booleanPreferencesKey("performance_lock") to true,
            floatPreferencesKey("synth_saw_mix") to 0.0f,
            floatPreferencesKey("synth_pulse_mix") to 1.0f,
            floatPreferencesKey("synth_triangle_mix") to 0.0f,
            floatPreferencesKey("synth_pulse_width") to 0.05f,
            floatPreferencesKey("synth_attack_seconds") to 10.0f,
            floatPreferencesKey("synth_decay_seconds") to 0.001f,
            floatPreferencesKey("synth_sustain") to 1.0f,
            floatPreferencesKey("synth_release_seconds") to 30.0f,
            floatPreferencesKey("synth_cutoff_hz") to 20_000.0f,
            floatPreferencesKey("synth_resonance") to 1.0f,
            floatPreferencesKey("synth_chorus_mix") to 0.0f,
            floatPreferencesKey("synth_delay_time_seconds") to 2.0f,
            floatPreferencesKey("synth_delay_feedback") to 0.94f,
            floatPreferencesKey("synth_delay_mix") to 1.0f,
            floatPreferencesKey("synth_reverb_mix") to 0.0f,
            floatPreferencesKey("synth_master_gain") to 1.5f,
        )

        val decoded = decodeStoredSettings(preferences)
        assertFalse(decoded.audioMonitorEnabled)
        assertEquals(11, decoded.rootPitchClass)
        assertEquals("minor", decoded.scaleId)
        assertEquals("fifth", decoded.chordId)
        assertEquals(PadArticulation.MUTED, decoded.padArticulation)
        assertEquals(PassThroughMode.PASS_THRU, decoded.passThroughMode)
        assertEquals(0, decoded.rangeMin)
        assertEquals(127, decoded.rangeMax)
        assertFalse(decoded.solfegeWrap)
        assertEquals(15, decoded.inputChannel)
        assertEquals(15, decoded.outputChannel)
        assertEquals("source-key", decoded.preferredSourceIdentity)
        assertEquals("destination-key", decoded.preferredDestinationIdentity)
        assertEquals("mapping-json", decoded.serializedMidiMapping)
        assertEquals(
            SynthPatch(
                sawMix = 0.0f,
                pulseMix = 1.0f,
                triangleMix = 0.0f,
                pulseWidth = 0.05f,
                attackSeconds = 10.0f,
                decaySeconds = 0.001f,
                sustain = 1.0f,
                releaseSeconds = 30.0f,
                cutoffHz = 20_000.0f,
                resonance = 1.0f,
                chorusMix = 0.0f,
                delayTimeSeconds = 2.0f,
                delayFeedback = 0.94f,
                delayMix = 1.0f,
                reverbMix = 0.0f,
                masterGain = 1.5f,
            ),
            decoded.synthPatch,
        )
        assertEquals("Working Session", decoded.workingPreset?.name)
        assertEquals(DefaultMidiMap.mapping, decoded.workingPreset?.midiMapping)
        assertEquals(PresetBank(), decoded.presetBank)
    }

    @Test
    fun currentSchemaSanitizesCorruptPatchValuesIndependently() {
        val preferences = mutablePreferencesOf(
            intPreferencesKey("schema_version") to CURRENT_SETTINGS_SCHEMA,
            floatPreferencesKey("synth_saw_mix") to Float.NaN,
            floatPreferencesKey("synth_pulse_mix") to Float.POSITIVE_INFINITY,
            floatPreferencesKey("synth_triangle_mix") to Float.NEGATIVE_INFINITY,
            floatPreferencesKey("synth_pulse_width") to -2.0f,
            floatPreferencesKey("synth_attack_seconds") to Float.MAX_VALUE,
            floatPreferencesKey("synth_decay_seconds") to -Float.MAX_VALUE,
            floatPreferencesKey("synth_sustain") to Float.MAX_VALUE,
            floatPreferencesKey("synth_release_seconds") to -Float.MAX_VALUE,
            floatPreferencesKey("synth_cutoff_hz") to Float.MAX_VALUE,
            floatPreferencesKey("synth_resonance") to -Float.MAX_VALUE,
            floatPreferencesKey("synth_chorus_mix") to Float.MAX_VALUE,
            floatPreferencesKey("synth_delay_time_seconds") to -Float.MAX_VALUE,
            floatPreferencesKey("synth_delay_feedback") to Float.MAX_VALUE,
            floatPreferencesKey("synth_delay_mix") to -Float.MAX_VALUE,
            floatPreferencesKey("synth_reverb_mix") to Float.MAX_VALUE,
            floatPreferencesKey("synth_master_gain") to Float.MAX_VALUE,
        )

        assertEquals(
            SynthPatch(
                sawMix = 0.65f,
                pulseMix = 0.20f,
                triangleMix = 0.15f,
                pulseWidth = 0.05f,
                attackSeconds = 10.0f,
                decaySeconds = 0.001f,
                sustain = 1.0f,
                releaseSeconds = 0.001f,
                cutoffHz = 20_000.0f,
                resonance = 0.0f,
                chorusMix = 1.0f,
                delayTimeSeconds = 0.01f,
                delayFeedback = 0.94f,
                delayMix = 0.0f,
                reverbMix = 1.0f,
                masterGain = 1.5f,
            ),
            decodeStoredSettings(preferences).synthPatch,
        )
    }

    @Test
    fun schemaOneMigratesLegacyContextIntoWorkingPresetAndEmptyBank() {
        val customMapping = MidiMapping(
            mapOf(MidiBindingKey(MidiBindingKey.Kind.NOTE, 42, 3) to MidiAction.Play),
        )
        val preferences = mutablePreferencesOf(
            intPreferencesKey("schema_version") to 1,
            intPreferencesKey("root_pitch_class") to 7,
            stringPreferencesKey("scale_id") to "dorian",
            stringPreferencesKey("chord_id") to "triad",
            stringPreferencesKey("pass_through_mode") to PassThroughMode.OFF.name,
            intPreferencesKey("range_min") to 24,
            intPreferencesKey("range_max") to 108,
            intPreferencesKey("output_channel") to 4,
            stringPreferencesKey("serialized_midi_mapping") to MidiMappingSerializer.encode(customMapping),
        )

        val decoded = decodeStoredSettings(preferences)

        assertEquals(CURRENT_SETTINGS_SCHEMA, decoded.schemaVersion)
        assertEquals(
            MusicalContextSnapshot(
                rootPitchClass = 7,
                scaleId = "dorian",
                chordId = "triad",
                padArticulation = PadArticulation.STACKED,
                rangeMin = 24,
                rangeMax = 108,
                solfegeWrap = true,
            ),
            decoded.workingPreset?.musicalContext,
        )
        assertEquals(PassThroughMode.OFF, decoded.workingPreset?.routing?.passThroughMode)
        assertEquals(4, decoded.workingPreset?.routing?.outputChannel)
        assertEquals(customMapping, decoded.workingPreset?.midiMapping)
        assertTrue(decoded.presetBank.presets.isEmpty())
        assertNull(decoded.selectedPresetSlot)
    }

    @Test
    fun schemaTwoWorkingPresetAndBankMigrateThenReencodeCurrentSchemas() {
        val legacyPreset = """{
            "schemaVersion":2,
            "name":"Legacy two",
            "musicalContext":{"rootPitchClass":2,"scaleId":"major","chordId":"triad"},
            "routing":{},
            "toneRow":{},
            "transport":{},
            "serializedMidiMapping":"{\"schemaVersion\":1,\"bindings\":[]}"
        }""".trimIndent()
        val legacyBank = """{
            "schemaVersion":1,
            "slots":[{"slot":9,"preset":$legacyPreset}]
        }""".trimIndent()
        val preferences = mutablePreferencesOf(
            intPreferencesKey("schema_version") to 2,
            stringPreferencesKey("chord_id") to "off",
            stringPreferencesKey("working_preset_json") to legacyPreset,
            stringPreferencesKey("preset_bank_json") to legacyBank,
            intPreferencesKey("selected_preset_slot") to 9,
        )

        val migrated = decodeStoredSettings(preferences)

        assertEquals(PadArticulation.ARPEGGIATED, migrated.padArticulation)
        assertEquals(PadArticulation.STACKED, migrated.workingPreset?.musicalContext?.padArticulation)
        assertEquals(PadArticulation.STACKED, migrated.presetBank.recall(9)?.musicalContext?.padArticulation)

        val rewritten = mutablePreferencesOf()
        writeStoredSettings(rewritten, migrated)
        assertEquals(CURRENT_SETTINGS_SCHEMA, rewritten[intPreferencesKey("schema_version")])
        assertTrue(requireNotNull(rewritten[stringPreferencesKey("working_preset_json")]).contains("\"schemaVersion\":3"))
        assertTrue(requireNotNull(rewritten[stringPreferencesKey("preset_bank_json")]).contains("\"schemaVersion\":2"))
        assertEquals(migrated, decodeStoredSettings(rewritten))
    }

    @Test
    fun pureWriterRestoresWorkingRowBankAndSelectionAcrossRecreation() {
        val working = PerformancePresetSnapshot(
            name = "Autosave",
            musicalContext = MusicalContextSnapshot(
                rootPitchClass = 5,
                scaleId = "blues",
                padArticulation = PadArticulation.MUTED,
            ),
            routing = RoutingSnapshot(outputChannel = 6),
            toneRow = ToneRowSnapshot(
                entries = listOf(
                    ToneRowEntrySnapshot(0, 65, 70),
                    ToneRowEntrySnapshot(2, 68, 90),
                ),
                intervalSequence = listOf(1, -2, 3),
                referenceRootPitchClass = 5,
                referenceScaleId = "blues",
            ),
            transport = TransportOptionsSnapshot(tempoBpm = 141, noteDurationPercent = 52, clocksPerStep = 12),
        )
        val original = StoredSettings(
            rootPitchClass = 5,
            scaleId = "blues",
            outputChannel = 6,
            padArticulation = PadArticulation.MUTED,
            synthPatch = SynthPatch(
                sawMix = 0.10f,
                pulseMix = 0.80f,
                triangleMix = 0.05f,
                pulseWidth = 0.70f,
                attackSeconds = 0.02f,
                decaySeconds = 0.40f,
                sustain = 0.60f,
                releaseSeconds = 0.80f,
                cutoffHz = 8_400.0f,
                resonance = 0.40f,
                chorusMix = 0.25f,
                delayTimeSeconds = 0.45f,
                delayFeedback = 0.50f,
                delayMix = 0.30f,
                reverbMix = 0.35f,
                masterGain = 0.90f,
            ),
            workingPreset = working,
            presetBank = PresetBank().save(0, working).save(127, working.copy(name = "Last")),
            selectedPresetSlot = 127,
        )
        val firstProcessPreferences = mutablePreferencesOf()
        writeStoredSettings(firstProcessPreferences, original)
        val restored = decodeStoredSettings(firstProcessPreferences)

        val recreatedProcessPreferences = mutablePreferencesOf()
        writeStoredSettings(recreatedProcessPreferences, restored)
        val recreated = decodeStoredSettings(recreatedProcessPreferences)

        assertEquals(original, restored)
        assertEquals(restored, recreated)
    }
}
