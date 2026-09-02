package dev.intervaltablet.domain

/**
 * Stable wire contract shared with the native `ParameterId` enum.
 *
 * [maximum] is the canonical UI and persistence ceiling. Cutoff remains sample-rate-dependent
 * at render time, so C++ additionally applies the negotiated stream's Nyquist-safe ceiling.
 */
enum class SynthParameter(
    val wireId: Int,
    val minimum: Float,
    val maximum: Float,
    val defaultValue: Float,
) {
    SAW_MIX(0, 0.0F, 1.0F, 0.65F),
    PULSE_MIX(1, 0.0F, 1.0F, 0.20F),
    TRIANGLE_MIX(2, 0.0F, 1.0F, 0.15F),
    PULSE_WIDTH(3, 0.05F, 0.95F, 0.50F),
    ATTACK(4, 0.0005F, 10.0F, 0.005F),
    DECAY(5, 0.001F, 20.0F, 0.18F),
    SUSTAIN(6, 0.0F, 1.0F, 0.70F),
    RELEASE(7, 0.001F, 30.0F, 0.35F),
    CUTOFF(8, 20.0F, 20_000.0F, 3_500.0F),
    RESONANCE(9, 0.0F, 1.0F, 0.15F),
    CHORUS_MIX(10, 0.0F, 1.0F, 0.18F),
    DELAY_TIME(11, 0.01F, 2.0F, 0.32F),
    DELAY_FEEDBACK(12, 0.0F, 0.94F, 0.28F),
    DELAY_MIX(13, 0.0F, 1.0F, 0.16F),
    REVERB_MIX(14, 0.0F, 1.0F, 0.20F),
    MASTER(15, 0.0F, 1.5F, 0.35F),
    FILTER_ATTACK(16, 0.0005F, 10.0F, 0.005F),
    FILTER_DECAY(17, 0.001F, 20.0F, 0.18F),
    FILTER_SUSTAIN(18, 0.0F, 1.0F, 0.0F),
    FILTER_RELEASE(19, 0.001F, 30.0F, 0.35F),
    FILTER_ENV_AMOUNT(20, -4.0F, 4.0F, 0.0F),
    DRIVE(21, 0.0F, 1.0F, 0.0F),
    LFO_RATE(22, 0.05F, 20.0F, 2.0F),
    LFO_DEPTH(23, 0.0F, 1.0F, 0.0F),
    LFO_DESTINATION(24, 0.0F, 2.0F, 0.0F),
    LFO_DELAY(25, 0.0F, 10.0F, 0.0F),
    DELAY_SYNC_BEATS(26, 0.0F, 4.0F, 0.0F),
    TEMPO_BPM(27, 20.0F, 300.0F, 120.0F),
    ;

    init {
        require(wireId >= 0)
        require(minimum.isFinite())
        require(maximum.isFinite() && maximum >= minimum)
        require(defaultValue.isFinite() && defaultValue in minimum..maximum)
    }

    /** Non-finite input cannot enter durable state or cross the JNI boundary. */
    fun sanitize(value: Float): Float = if (value.isFinite()) {
        value.coerceIn(minimum, maximum)
    } else {
        defaultValue
    }

    fun accepts(value: Float): Boolean = value.isFinite() && value in minimum..maximum
}

enum class SynthLfoDestination(val wireValue: Float) {
    FILTER(0.0F),
    PULSE_WIDTH(1.0F),
    DELAY_TIME(2.0F),
    ;

    companion object {
        fun fromWire(value: Float): SynthLfoDestination = entries[
            value.toInt().coerceIn(0, entries.lastIndex)
        ]
    }
}

/** Canonical, device-independent patch. Values use the native wire units. */
data class SynthPatch(
    val sawMix: Float = SynthParameter.SAW_MIX.defaultValue,
    val pulseMix: Float = SynthParameter.PULSE_MIX.defaultValue,
    val triangleMix: Float = SynthParameter.TRIANGLE_MIX.defaultValue,
    val pulseWidth: Float = SynthParameter.PULSE_WIDTH.defaultValue,
    val attackSeconds: Float = SynthParameter.ATTACK.defaultValue,
    val decaySeconds: Float = SynthParameter.DECAY.defaultValue,
    val sustain: Float = SynthParameter.SUSTAIN.defaultValue,
    val releaseSeconds: Float = SynthParameter.RELEASE.defaultValue,
    val cutoffHz: Float = SynthParameter.CUTOFF.defaultValue,
    val resonance: Float = SynthParameter.RESONANCE.defaultValue,
    val chorusMix: Float = SynthParameter.CHORUS_MIX.defaultValue,
    val delayTimeSeconds: Float = SynthParameter.DELAY_TIME.defaultValue,
    val delayFeedback: Float = SynthParameter.DELAY_FEEDBACK.defaultValue,
    val delayMix: Float = SynthParameter.DELAY_MIX.defaultValue,
    val reverbMix: Float = SynthParameter.REVERB_MIX.defaultValue,
    val masterGain: Float = SynthParameter.MASTER.defaultValue,
    val filterAttackSeconds: Float = SynthParameter.FILTER_ATTACK.defaultValue,
    val filterDecaySeconds: Float = SynthParameter.FILTER_DECAY.defaultValue,
    val filterSustain: Float = SynthParameter.FILTER_SUSTAIN.defaultValue,
    val filterReleaseSeconds: Float = SynthParameter.FILTER_RELEASE.defaultValue,
    val filterEnvelopeAmount: Float = SynthParameter.FILTER_ENV_AMOUNT.defaultValue,
    val drive: Float = SynthParameter.DRIVE.defaultValue,
    val lfoRateHz: Float = SynthParameter.LFO_RATE.defaultValue,
    val lfoDepth: Float = SynthParameter.LFO_DEPTH.defaultValue,
    val lfoDestination: SynthLfoDestination = SynthLfoDestination.FILTER,
    val lfoDelaySeconds: Float = SynthParameter.LFO_DELAY.defaultValue,
    val delaySyncBeats: Float = SynthParameter.DELAY_SYNC_BEATS.defaultValue,
    val tempoBpm: Float = SynthParameter.TEMPO_BPM.defaultValue,
) {
    init {
        SynthParameter.entries.forEach { parameter ->
            require(parameter.accepts(this[parameter])) {
                "Invalid ${parameter.name} value: ${this[parameter]}"
            }
        }
    }

    operator fun get(parameter: SynthParameter): Float = when (parameter) {
        SynthParameter.SAW_MIX -> sawMix
        SynthParameter.PULSE_MIX -> pulseMix
        SynthParameter.TRIANGLE_MIX -> triangleMix
        SynthParameter.PULSE_WIDTH -> pulseWidth
        SynthParameter.ATTACK -> attackSeconds
        SynthParameter.DECAY -> decaySeconds
        SynthParameter.SUSTAIN -> sustain
        SynthParameter.RELEASE -> releaseSeconds
        SynthParameter.CUTOFF -> cutoffHz
        SynthParameter.RESONANCE -> resonance
        SynthParameter.CHORUS_MIX -> chorusMix
        SynthParameter.DELAY_TIME -> delayTimeSeconds
        SynthParameter.DELAY_FEEDBACK -> delayFeedback
        SynthParameter.DELAY_MIX -> delayMix
        SynthParameter.REVERB_MIX -> reverbMix
        SynthParameter.MASTER -> masterGain
        SynthParameter.FILTER_ATTACK -> filterAttackSeconds
        SynthParameter.FILTER_DECAY -> filterDecaySeconds
        SynthParameter.FILTER_SUSTAIN -> filterSustain
        SynthParameter.FILTER_RELEASE -> filterReleaseSeconds
        SynthParameter.FILTER_ENV_AMOUNT -> filterEnvelopeAmount
        SynthParameter.DRIVE -> drive
        SynthParameter.LFO_RATE -> lfoRateHz
        SynthParameter.LFO_DEPTH -> lfoDepth
        SynthParameter.LFO_DESTINATION -> lfoDestination.wireValue
        SynthParameter.LFO_DELAY -> lfoDelaySeconds
        SynthParameter.DELAY_SYNC_BEATS -> delaySyncBeats
        SynthParameter.TEMPO_BPM -> tempoBpm
    }

    fun withParameter(parameter: SynthParameter, value: Float): SynthPatch {
        val sanitized = parameter.sanitize(value)
        return when (parameter) {
            SynthParameter.SAW_MIX -> copy(sawMix = sanitized)
            SynthParameter.PULSE_MIX -> copy(pulseMix = sanitized)
            SynthParameter.TRIANGLE_MIX -> copy(triangleMix = sanitized)
            SynthParameter.PULSE_WIDTH -> copy(pulseWidth = sanitized)
            SynthParameter.ATTACK -> copy(attackSeconds = sanitized)
            SynthParameter.DECAY -> copy(decaySeconds = sanitized)
            SynthParameter.SUSTAIN -> copy(sustain = sanitized)
            SynthParameter.RELEASE -> copy(releaseSeconds = sanitized)
            SynthParameter.CUTOFF -> copy(cutoffHz = sanitized)
            SynthParameter.RESONANCE -> copy(resonance = sanitized)
            SynthParameter.CHORUS_MIX -> copy(chorusMix = sanitized)
            SynthParameter.DELAY_TIME -> copy(delayTimeSeconds = sanitized)
            SynthParameter.DELAY_FEEDBACK -> copy(delayFeedback = sanitized)
            SynthParameter.DELAY_MIX -> copy(delayMix = sanitized)
            SynthParameter.REVERB_MIX -> copy(reverbMix = sanitized)
            SynthParameter.MASTER -> copy(masterGain = sanitized)
            SynthParameter.FILTER_ATTACK -> copy(filterAttackSeconds = sanitized)
            SynthParameter.FILTER_DECAY -> copy(filterDecaySeconds = sanitized)
            SynthParameter.FILTER_SUSTAIN -> copy(filterSustain = sanitized)
            SynthParameter.FILTER_RELEASE -> copy(filterReleaseSeconds = sanitized)
            SynthParameter.FILTER_ENV_AMOUNT -> copy(filterEnvelopeAmount = sanitized)
            SynthParameter.DRIVE -> copy(drive = sanitized)
            SynthParameter.LFO_RATE -> copy(lfoRateHz = sanitized)
            SynthParameter.LFO_DEPTH -> copy(lfoDepth = sanitized)
            SynthParameter.LFO_DESTINATION -> copy(
                lfoDestination = SynthLfoDestination.fromWire(sanitized),
            )
            SynthParameter.LFO_DELAY -> copy(lfoDelaySeconds = sanitized)
            SynthParameter.DELAY_SYNC_BEATS -> copy(delaySyncBeats = sanitized)
            SynthParameter.TEMPO_BPM -> copy(tempoBpm = sanitized)
        }
    }

    /**
     * Simple oscillator macro whose canonical value is the pulse mix.
     *
     * The non-pulse share retains the native default 13:3 saw/triangle ratio. Pulse width
     * follows a conservative 0.40...0.90 range. Therefore the native default macro value
     * `0.20` reconstructs the four native defaults bit-for-bit.
     */
    fun withTimbre(timbre: Float): SynthPatch {
        val pulse = SynthParameter.PULSE_MIX.sanitize(timbre)
        val nonPulse = 1.0F - pulse
        val triangle = nonPulse * NON_PULSE_TRIANGLE_SHARE
        val saw = nonPulse - triangle
        val width = TIMBRE_MINIMUM_PULSE_WIDTH + TIMBRE_PULSE_WIDTH_SPAN * pulse
        return copy(
            sawMix = SynthParameter.SAW_MIX.sanitize(saw),
            pulseMix = pulse,
            triangleMix = SynthParameter.TRIANGLE_MIX.sanitize(triangle),
            pulseWidth = SynthParameter.PULSE_WIDTH.sanitize(width),
        )
    }

    /** Stable ascending wire order, independent of call-site collection ordering. */
    fun toAudioCommands(): List<AudioCommand.Parameter> = SynthParameter.entries
        .sortedBy(SynthParameter::wireId)
        .map { parameter -> AudioCommand.Parameter(parameter, this[parameter]) }

    /** Only parameters whose wire values differ, still emitted in stable wire order. */
    fun changedAudioCommandsSince(previous: SynthPatch): List<AudioCommand.Parameter> =
        SynthParameter.entries
            .sortedBy(SynthParameter::wireId)
            .filter { parameter -> this[parameter] != previous[parameter] }
            .map { parameter -> AudioCommand.Parameter(parameter, this[parameter]) }

    private companion object {
        const val NON_PULSE_TRIANGLE_SHARE: Float = 0.1875F
        const val TIMBRE_MINIMUM_PULSE_WIDTH: Float = 0.40F
        const val TIMBRE_PULSE_WIDTH_SPAN: Float = 0.50F
    }
}

data class SynthPreset(
    val id: String,
    val displayName: String,
    val patch: SynthPatch,
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
    }
}

/** Original built-in examples; recalling one simply installs its patch as the global sound. */
object SynthPresetLibrary {
    val all: List<SynthPreset> = listOf(
        SynthPreset("initial", "Initial", SynthPatch()),
        SynthPreset(
            "velours",
            "Velours",
            SynthPatch(
                sawMix = 0.35F,
                pulseMix = 0.15F,
                triangleMix = 0.50F,
                attackSeconds = 0.65F,
                releaseSeconds = 1.8F,
                cutoffHz = 2_200F,
                chorusMix = 0.32F,
                reverbMix = 0.34F,
            ),
        ),
        SynthPreset(
            "pique",
            "Piqué",
            SynthPatch(
                sawMix = 0.72F,
                pulseMix = 0.22F,
                triangleMix = 0.06F,
                decaySeconds = 0.11F,
                sustain = 0.18F,
                releaseSeconds = 0.12F,
                cutoffHz = 6_800F,
                filterDecaySeconds = 0.16F,
                filterEnvelopeAmount = 2.4F,
                drive = 0.12F,
            ),
        ),
        SynthPreset(
            "basse_ronde",
            "Basse ronde",
            SynthPatch(
                sawMix = 0.20F,
                pulseMix = 0.18F,
                triangleMix = 0.62F,
                cutoffHz = 780F,
                resonance = 0.24F,
                sustain = 0.82F,
                drive = 0.22F,
                masterGain = 0.42F,
            ),
        ),
        SynthPreset(
            "nebuleuse",
            "Nébuleuse",
            SynthPatch(
                sawMix = 0.44F,
                pulseMix = 0.18F,
                triangleMix = 0.38F,
                attackSeconds = 1.4F,
                releaseSeconds = 3.2F,
                cutoffHz = 1_650F,
                chorusMix = 0.48F,
                delayFeedback = 0.52F,
                delayMix = 0.28F,
                delaySyncBeats = 0.75F,
                reverbMix = 0.46F,
                lfoRateHz = 0.18F,
                lfoDepth = 0.34F,
                lfoDestination = SynthLfoDestination.FILTER,
                lfoDelaySeconds = 0.8F,
            ),
        ),
        SynthPreset(
            "echo_dub",
            "Écho dub",
            SynthPatch(
                sawMix = 0.54F,
                pulseMix = 0.31F,
                triangleMix = 0.15F,
                cutoffHz = 1_900F,
                resonance = 0.42F,
                filterEnvelopeAmount = 1.2F,
                delayFeedback = 0.72F,
                delayMix = 0.46F,
                delaySyncBeats = 0.75F,
                drive = 0.18F,
                reverbMix = 0.22F,
            ),
        ),
    )
}
