package dev.intervaltablet.ui

import dev.intervaltablet.domain.SynthParameter
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceSynthControlsTest {
    @Test
    fun cutoffUiMatchesTheCanonicalAudibleRange() {
        val minimum = SynthParameter.CUTOFF.minimum

        assertEquals(SynthParameter.CUTOFF.maximum, SynthCutoffUiMaximumHz, 0f)

        assertEquals(
            0f,
            logarithmicSliderPosition(minimum, minimum, SynthCutoffUiMaximumHz),
            0.000_001f,
        )
        assertEquals(
            1f,
            logarithmicSliderPosition(
                SynthParameter.CUTOFF.maximum,
                minimum,
                SynthCutoffUiMaximumHz,
            ),
            0.000_001f,
        )
        assertEquals(
            SynthCutoffUiMaximumHz,
            logarithmicSliderValue(1f, minimum, SynthCutoffUiMaximumHz),
            0.01f,
        )
    }

    @Test
    fun logarithmicSliderRoundTripsEnvelopeAndFilterValues() {
        listOf(
            Triple(0.005f, SynthParameter.ATTACK.minimum, SynthParameter.ATTACK.maximum),
            Triple(0.35f, SynthParameter.RELEASE.minimum, SynthParameter.RELEASE.maximum),
            Triple(3_500f, SynthParameter.CUTOFF.minimum, SynthCutoffUiMaximumHz),
        ).forEach { (value, minimum, maximum) ->
            val position = logarithmicSliderPosition(value, minimum, maximum)
            val roundTrip = logarithmicSliderValue(position, minimum, maximum)

            assertTrue(position in 0f..1f)
            assertTrue(abs(roundTrip - value) <= value * 0.000_01f)
        }
    }
}
