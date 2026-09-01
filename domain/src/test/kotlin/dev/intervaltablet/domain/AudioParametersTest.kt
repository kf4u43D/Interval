package dev.intervaltablet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioParametersTest {
    @Test
    fun wireContractMatchesNativeIdsBoundsAndDefaults() {
        val expected = listOf(
            Contract(0, 0.0F, 1.0F, 0.65F),
            Contract(1, 0.0F, 1.0F, 0.20F),
            Contract(2, 0.0F, 1.0F, 0.15F),
            Contract(3, 0.05F, 0.95F, 0.50F),
            Contract(4, 0.0005F, 10.0F, 0.005F),
            Contract(5, 0.001F, 20.0F, 0.18F),
            Contract(6, 0.0F, 1.0F, 0.70F),
            Contract(7, 0.001F, 30.0F, 0.35F),
            Contract(8, 20.0F, 20_000.0F, 3_500.0F),
            Contract(9, 0.0F, 1.0F, 0.15F),
            Contract(10, 0.0F, 1.0F, 0.18F),
            Contract(11, 0.01F, 2.0F, 0.32F),
            Contract(12, 0.0F, 0.94F, 0.28F),
            Contract(13, 0.0F, 1.0F, 0.16F),
            Contract(14, 0.0F, 1.0F, 0.20F),
            Contract(15, 0.0F, 1.5F, 0.35F),
        )

        assertEquals(
            expected,
            SynthParameter.entries.map { parameter ->
                Contract(
                    wireId = parameter.wireId,
                    minimum = parameter.minimum,
                    maximum = parameter.maximum,
                    defaultValue = parameter.defaultValue,
                )
            },
        )
        assertEquals(16, SynthParameter.entries.map(SynthParameter::wireId).distinct().size)
    }

    @Test
    fun sanitizationClampsFiniteValuesAndDefaultsEveryNonFiniteValue() {
        SynthParameter.entries.forEach { parameter ->
            assertFloatBits(parameter.minimum, parameter.sanitize(-Float.MAX_VALUE))
            assertFloatBits(parameter.maximum, parameter.sanitize(Float.MAX_VALUE))
            assertFloatBits(parameter.defaultValue, parameter.sanitize(Float.NaN))
            assertFloatBits(parameter.defaultValue, parameter.sanitize(Float.POSITIVE_INFINITY))
            assertFloatBits(parameter.defaultValue, parameter.sanitize(Float.NEGATIVE_INFINITY))
        }
    }

    @Test
    fun defaultTimbreReconstructsNativeOscillatorDefaultsExactly() {
        val original = SynthPatch(cutoffHz = 8_800.0F, masterGain = 0.9F)

        val patch = original.withTimbre(0.20F)

        assertFloatBits(0.65F, patch.sawMix)
        assertFloatBits(0.20F, patch.pulseMix)
        assertFloatBits(0.15F, patch.triangleMix)
        assertFloatBits(0.50F, patch.pulseWidth)
        assertFloatBits(original.cutoffHz, patch.cutoffHz)
        assertFloatBits(original.masterGain, patch.masterGain)
        assertEquals(SynthPatch(), SynthPatch().withTimbre(Float.NaN))
    }

    @Test
    fun patchUpdatesSanitizeValuesAndValidatedCommandsRejectInvalidPayloads() {
        val bounded = SynthPatch()
            .withParameter(SynthParameter.MASTER, Float.MAX_VALUE)
            .withParameter(SynthParameter.DELAY_FEEDBACK, -1.0F)
            .withParameter(SynthParameter.CUTOFF, Float.NaN)

        assertFloatBits(1.5F, bounded.masterGain)
        assertFloatBits(0.0F, bounded.delayFeedback)
        assertFloatBits(3_500.0F, bounded.cutoffHz)
        assertThrows(IllegalArgumentException::class.java) {
            SynthPatch(sawMix = Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioCommand.Parameter(SynthParameter.MASTER, 1.5001F)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioCommand.Parameter(SynthParameter.ATTACK, Float.POSITIVE_INFINITY)
        }
    }

    @Test
    fun patchCommandsContainEveryParameterInAscendingWireOrder() {
        val patch = SynthPatch()
            .withTimbre(0.75F)
            .withParameter(SynthParameter.ATTACK, 0.031F)
            .withParameter(SynthParameter.DELAY_MIX, 0.61F)
            .withParameter(SynthParameter.MASTER, 1.1F)

        val commands = patch.toAudioCommands()

        assertEquals((0..15).toList(), commands.map { it.parameter.wireId })
        assertTrue(commands.all { command -> command.value == patch[command.parameter] })
    }

    @Test
    fun changedPatchCommandsContainOnlyModifiedParametersInWireOrder() {
        val original = SynthPatch()
        val changed = original
            .withParameter(SynthParameter.REVERB_MIX, 0.61F)
            .withParameter(SynthParameter.ATTACK, 0.031F)

        val commands = changed.changedAudioCommandsSince(original)

        assertEquals(
            listOf(SynthParameter.ATTACK, SynthParameter.REVERB_MIX),
            commands.map(AudioCommand.Parameter::parameter),
        )
        assertEquals(emptyList<AudioCommand.Parameter>(), changed.changedAudioCommandsSince(changed))
    }

    private fun assertFloatBits(expected: Float, actual: Float) {
        assertEquals(expected.toRawBits(), actual.toRawBits())
    }

    private data class Contract(
        val wireId: Int,
        val minimum: Float,
        val maximum: Float,
        val defaultValue: Float,
    )
}
