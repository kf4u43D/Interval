package dev.intervaltablet

import dev.intervaltablet.data.MusicalContextSnapshot
import dev.intervaltablet.data.PerformancePresetSerializer
import dev.intervaltablet.data.PerformancePresetSnapshot
import dev.intervaltablet.data.PresetBank
import dev.intervaltablet.data.RoutingSnapshot
import dev.intervaltablet.data.StoredClockSource
import dev.intervaltablet.data.ToneRowEntrySnapshot
import dev.intervaltablet.data.ToneRowPlaybackSnapshotMode
import dev.intervaltablet.data.ToneRowSnapshot
import dev.intervaltablet.data.TransportOptionsSnapshot
import dev.intervaltablet.domain.MidiAction
import dev.intervaltablet.domain.MidiBindingKey
import dev.intervaltablet.domain.MidiMapping
import dev.intervaltablet.domain.PadArticulation
import dev.intervaltablet.domain.PassThroughMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformancePresetSerializerTest {
    @Test
    fun completeSnapshotRoundTripsEveryGateTwoBoundary() {
        val key = MidiBindingKey(MidiBindingKey.Kind.CC, number = 22, channel = 7)
        val preset = PerformancePresetSnapshot(
            name = "Night sequence",
            musicalContext = MusicalContextSnapshot(
                rootPitchClass = 11,
                scaleId = "minor_pentatonic",
                chordId = "wide",
                padArticulation = PadArticulation.MUTED,
                rangeMin = 12,
                rangeMax = 120,
                solfegeWrap = false,
            ),
            routing = RoutingSnapshot(
                passThroughMode = PassThroughMode.ACTIVE_LAST_NOTE,
                inputChannel = 7,
                outputChannel = 14,
                preferredSourceIdentity = "source-descriptor",
                preferredDestinationIdentity = "destination-descriptor",
            ),
            toneRow = ToneRowSnapshot(
                entries = listOf(
                    ToneRowEntrySnapshot(-3, 60, 32),
                    ToneRowEntrySnapshot(2, 62, 96),
                    ToneRowEntrySnapshot(8, 67, 127),
                ),
                intervalSequence = listOf(1, -2, 4, 0),
                playMode = ToneRowPlaybackSnapshotMode.PENDULUM,
                inverted = true,
                transpositionSemitones = 11,
                translation = -8,
                octaveOffset = 8,
                randomState = -42L,
                referenceRootPitchClass = 6,
                referenceScaleId = "dorian",
            ),
            transport = TransportOptionsSnapshot(
                tempoBpm = 173,
                noteDurationPercent = 41,
                clocksPerStep = 8,
                clockSource = StoredClockSource.MIDI,
            ),
            midiMapping = MidiMapping(
                bindings = mapOf(key to MidiAction.Record),
                ccThresholds = mapOf(key to 101),
            ),
        )

        assertEquals(preset, PerformancePresetSerializer.decode(PerformancePresetSerializer.encode(preset)))
    }

    @Test
    fun automaticTransformationModesPersistByNameWithoutChangingTheSchema() {
        val modes = listOf(
            ToneRowPlaybackSnapshotMode.AUTO_TRANSPOSE_UP,
            ToneRowPlaybackSnapshotMode.AUTO_TRANSPOSE_DOWN,
            ToneRowPlaybackSnapshotMode.AUTO_TRANSLATE_UP,
            ToneRowPlaybackSnapshotMode.AUTO_TRANSLATE_DOWN,
        )

        modes.forEach { mode ->
            val preset = PerformancePresetSnapshot(toneRow = ToneRowSnapshot(playMode = mode))
            val encoded = PerformancePresetSerializer.encode(preset)

            assertTrue("mode=$mode", encoded.contains("\"schemaVersion\":3"))
            assertTrue("mode=$mode", encoded.contains("\"playMode\":\"${mode.name}\""))
            assertEquals("mode=$mode", preset, PerformancePresetSerializer.decode(encoded))
        }
    }

    @Test
    fun bankOperationsAndRoundTripCoverBoundarySlots() {
        val first = PerformancePresetSnapshot(name = "First")
        val last = PerformancePresetSnapshot(name = "Last")
        val bank = PresetBank()
            .save(127, last)
            .save(0, first)

        assertTrue(bank.contains(0))
        assertTrue(bank.contains(127))
        assertEquals(last, bank.recall(127))
        assertEquals(bank, PerformancePresetSerializer.decodeBank(PerformancePresetSerializer.encodeBank(bank)))

        val withoutFirst = bank.delete(0)
        assertFalse(withoutFirst.contains(0))
        assertNull(withoutFirst.recall(0))
        assertTrue(bank.contains(0))
    }

    @Test
    fun flatSchemaOneMigratesToSafeStoppedSnapshotDefaults() {
        val fixture = """{
            "schemaVersion":1,
            "name":"Legacy row",
            "rootPitchClass":9,
            "scaleId":"dorian",
            "chordId":"triad",
            "rangeMin":24,
            "rangeMax":100,
            "solfegeWrap":false,
            "passThroughMode":"OFF",
            "inputChannel":3,
            "outputChannel":8,
            "toneRow":[
                {"relativeDegree":0,"recordedMidiNote":57,"velocity":70},
                {"relativeDegree":2,"recordedMidiNote":60,"velocity":71}
            ],
            "intervalSequence":[1,-1,3],
            "tempoBpm":90,
            "noteDurationPercent":60,
            "clocksPerStep":12
        }""".trimIndent()

        val migrated = requireNotNull(PerformancePresetSerializer.decode(fixture))

        assertEquals("Legacy row", migrated.name)
        assertEquals(9, migrated.musicalContext.rootPitchClass)
        assertEquals(PassThroughMode.OFF, migrated.routing.passThroughMode)
        assertEquals(listOf(1, -1, 3), migrated.toneRow.intervalSequence)
        assertEquals(ToneRowPlaybackSnapshotMode.PRIME, migrated.toneRow.playMode)
        assertEquals(9, migrated.toneRow.referenceRootPitchClass)
        assertEquals("dorian", migrated.toneRow.referenceScaleId)
        assertEquals(PadArticulation.STACKED, migrated.musicalContext.padArticulation)
        assertEquals(90, migrated.transport.tempoBpm)
        assertEquals(StoredClockSource.INTERNAL, migrated.transport.clockSource)
    }

    @Test
    fun flatSchemaOneWithChordOffMigratesToArpeggiatedPads() {
        val fixture = """{
            "schemaVersion":1,
            "name":"Legacy lead",
            "chordId":"off"
        }""".trimIndent()

        val migrated = requireNotNull(PerformancePresetSerializer.decode(fixture))

        assertEquals(PadArticulation.ARPEGGIATED, migrated.musicalContext.padArticulation)
    }

    @Test
    fun corruptFutureDuplicateAndOversizedPayloadsAreRejected() {
        val valid = PerformancePresetSerializer.encode(PerformancePresetSnapshot())
        val future = valid.replaceFirst("\"schemaVersion\":3", "\"schemaVersion\":4")
        val duplicatePitchClass = """{
            "schemaVersion":1,
            "toneRow":[
                {"relativeDegree":0,"recordedMidiNote":60,"velocity":64},
                {"relativeDegree":1,"recordedMidiNote":72,"velocity":64}
            ]
        }""".trimIndent()
        val duplicateBankSlot = """{
            "schemaVersion":1,
            "slots":[
                {"slot":4,"preset":${legacyV2Preset("triad")}},
                {"slot":4,"preset":${legacyV2Preset("triad")}}
            ]
        }""".trimIndent()

        assertNull(PerformancePresetSerializer.decode(future))
        assertNull(PerformancePresetSerializer.decode(duplicatePitchClass))
        assertNull(PerformancePresetSerializer.decode("x".repeat(4 * 1_048_576 + 1)))
        assertNull(PerformancePresetSerializer.decodeBank(duplicateBankSlot))
    }

    @Test
    fun nestedSchemaTwoInfersBothLegacyArticulationsWithoutChangingSound() {
        val stacked = requireNotNull(PerformancePresetSerializer.decode(legacyV2Preset("triad")))
        val arpeggiated = requireNotNull(PerformancePresetSerializer.decode(legacyV2Preset("off")))

        assertEquals(PadArticulation.STACKED, stacked.musicalContext.padArticulation)
        assertEquals(PadArticulation.ARPEGGIATED, arpeggiated.musicalContext.padArticulation)
    }

    @Test
    fun bankEncodingRejectsAValidButExcessiveDataStorePayload() {
        val allBindings = buildMap {
            for (kind in MidiBindingKey.Kind.entries) {
                for (number in 0..127) {
                    put(MidiBindingKey(kind, number, null), MidiAction.Undo)
                    for (channel in 0..15) {
                        put(MidiBindingKey(kind, number, channel), MidiAction.Undo)
                    }
                }
            }
        }
        val largePreset = PerformancePresetSnapshot(
            name = "Dense mapping",
            midiMapping = MidiMapping(allBindings),
        )
        val onePresetChars = PerformancePresetSerializer.encode(largePreset).length
        val slotsNeeded = (4 * 1_048_576 / onePresetChars) + 1
        assertTrue(slotsNeeded in 2..128)
        val oversizedBank = PresetBank(
            (0 until slotsNeeded).associateWith { largePreset.copy(name = "Dense $it") },
        )

        assertThrows(IllegalArgumentException::class.java) {
            PerformancePresetSerializer.encodeBank(oversizedBank)
        }
    }

    @Test
    fun schemaOneBankMigratesItsNestedSchemaTwoPresets() {
        val fixture = """{
            "schemaVersion":1,
            "slots":[{"slot":127,"preset":${legacyV2Preset("triad")}}]
        }""".trimIndent()

        val bank = requireNotNull(PerformancePresetSerializer.decodeBank(fixture))

        assertEquals(PadArticulation.STACKED, bank.recall(127)?.musicalContext?.padArticulation)
        assertEquals(bank, PerformancePresetSerializer.decodeBank(PerformancePresetSerializer.encodeBank(bank)))
    }

    private fun legacyV2Preset(chordId: String): String = """{
        "schemaVersion":2,
        "name":"Legacy bank preset",
        "musicalContext":{"rootPitchClass":0,"scaleId":"major","chordId":"$chordId"},
        "routing":{},
        "toneRow":{},
        "transport":{},
        "serializedMidiMapping":"{\"schemaVersion\":1,\"bindings\":[]}"
    }""".trimIndent()
}
