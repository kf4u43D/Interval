#include "dsp/DspPrimitives.h"
#include "dsp/Effects.h"
#include "dsp/SpscQueue.h"
#include "dsp/SynthEngine.h"

#include <array>
#include <chrono>
#include <cmath>
#include <cstdlib>
#include <iostream>
#include <limits>
#include <vector>

namespace {

void require(const bool condition, const char* message) {
    if (!condition) {
        std::cerr << "FAIL: " << message << '\n';
        std::exit(EXIT_FAILURE);
    }
}

bool nearlyEqual(const float left, const float right, const float tolerance = 0.00001F) {
    const float scale = std::max(1.0F, std::max(std::abs(left), std::abs(right)));
    return std::abs(left - right) <= tolerance * scale;
}

void testQueue() {
    intervaltablet::dsp::SpscQueue<int, 8> queue;
    require(queue.sizeApprox() == 0U, "new queue must be empty");
    require(queue.usableCapacity() == 7U, "queue exposes its usable capacity");
    for (int value = 0; value < 7; ++value) require(queue.push(value), "queue should accept capacity-1 items");
    require(queue.sizeApprox() == 7U, "full queue depth must be observable");
    require(!queue.push(8), "queue must report full");
    for (int expected = 0; expected < 7; ++expected) {
        int actual = -1;
        require(queue.pop(actual), "queue should pop");
        require(actual == expected, "queue order must be FIFO");
    }
    require(queue.sizeApprox() == 0U, "drained queue must be empty");

    for (int cycle = 0; cycle < 64; ++cycle) {
        require(queue.push(cycle), "queue should wrap its write index");
        int actual = -1;
        require(queue.pop(actual), "queue should wrap its read index");
        require(actual == cycle, "wrapped queue must remain FIFO");
    }

    require(queue.push(10), "queue should accept an item before clear");
    require(queue.push(11), "queue should accept a second item before clear");
    queue.clearFromConsumer();
    require(queue.sizeApprox() == 0U, "consumer clear must discard pending items");
    int discarded = -1;
    require(!queue.pop(discarded), "consumer clear must leave no readable item");
}

void testEnvelope() {
    intervaltablet::dsp::Adsr envelope;
    envelope.prepare(48000.0F);
    envelope.setParameters(0.001F, 0.002F, 0.5F, 0.003F);
    envelope.noteOn();
    float peak = 0.0F;
    for (int i = 0; i < 10000; ++i) peak = std::max(peak, envelope.process());
    require(peak > 0.9F, "ADSR must reach attack peak");
    envelope.noteOff();
    for (int i = 0; i < 100000 && envelope.isActive(); ++i) (void)envelope.process();
    require(!envelope.isActive(), "ADSR must terminate release");
}

void testEnvelopeDurationsAreMusicalTimes() {
    using intervaltablet::dsp::Adsr;
    constexpr std::array<float, 3> sampleRates{44100.0F, 48000.0F, 96000.0F};
    constexpr float attackSeconds = 0.005F;
    constexpr float decaySeconds = 0.18F;
    constexpr float sustain = 0.70F;
    constexpr float releaseSeconds = 0.35F;

    for (const float sampleRate : sampleRates) {
        const auto coefficients = Adsr::makeCoefficients(
            sampleRate,
            attackSeconds,
            decaySeconds,
            sustain,
            releaseSeconds);

        Adsr attackAndDecay;
        attackAndDecay.prepare(sampleRate, coefficients);
        attackAndDecay.noteOn();
        int attackSamples = 0;
        const int attackLimit = static_cast<int>(std::ceil(sampleRate * attackSeconds)) + 8;
        while (attackAndDecay.stage() == Adsr::Stage::Attack && attackSamples < attackLimit) {
            (void)attackAndDecay.process();
            ++attackSamples;
        }
        const int expectedAttackSamples = static_cast<int>(std::ceil(sampleRate * attackSeconds));
        require(
            std::abs(attackSamples - expectedAttackSamples) <= 2,
            "attack must reach its target at the configured duration");
        require(attackAndDecay.stage() == Adsr::Stage::Decay, "attack must enter decay on time");

        int decaySamples = 0;
        const int decayLimit = static_cast<int>(std::ceil(sampleRate * decaySeconds)) + 8;
        while (attackAndDecay.stage() == Adsr::Stage::Decay && decaySamples < decayLimit) {
            (void)attackAndDecay.process();
            ++decaySamples;
        }
        const int expectedDecaySamples = static_cast<int>(std::ceil(sampleRate * decaySeconds));
        require(
            std::abs(decaySamples - expectedDecaySamples) <= 2,
            "decay must reach sustain at the configured duration");
        require(attackAndDecay.stage() == Adsr::Stage::Sustain, "decay must enter sustain on time");

        Adsr fullScaleRelease;
        fullScaleRelease.prepare(sampleRate, coefficients);
        fullScaleRelease.noteOn();
        while (fullScaleRelease.stage() == Adsr::Stage::Attack) {
            (void)fullScaleRelease.process();
        }
        fullScaleRelease.noteOff();
        int releaseSamples = 0;
        const int releaseLimit = static_cast<int>(std::ceil(sampleRate * releaseSeconds)) + 8;
        while (fullScaleRelease.isActive() && releaseSamples < releaseLimit) {
            (void)fullScaleRelease.process();
            ++releaseSamples;
        }
        const int expectedReleaseSamples = static_cast<int>(std::ceil(sampleRate * releaseSeconds));
        require(
            std::abs(releaseSamples - expectedReleaseSamples) <= 2,
            "full-scale release must reach silence at the configured duration");
        require(!fullScaleRelease.isActive(), "release must become idle on time");
    }
}

void testSoftLimiterPreservesLinearHeadroom() {
    using intervaltablet::dsp::kSoftLimitKnee;
    using intervaltablet::dsp::softLimit;
    constexpr std::array<float, 9> nominalValues{
        -0.75F,
        -0.5F,
        -0.1F,
        -0.0F,
        0.0F,
        0.1F,
        0.5F,
        0.749F,
        0.75F,
    };
    for (const float value : nominalValues) {
        require(softLimit(value) == value, "soft limiter must be exactly linear through the nominal range");
    }

    float previous = softLimit(kSoftLimitKnee);
    for (int step = 1; step <= 10000; ++step) {
        const float input = kSoftLimitKnee + static_cast<float>(step) * 0.01F;
        const float positive = softLimit(input);
        const float negative = softLimit(-input);
        require(positive >= previous, "soft limiter knee must be monotone");
        require(positive >= kSoftLimitKnee && positive <= 1.0F, "soft limiter knee must remain bounded");
        require(negative == -positive, "soft limiter must remain odd-symmetric");
        previous = positive;
    }
    require(
        std::abs(softLimit(kSoftLimitKnee + 0.00001F) - (kSoftLimitKnee + 0.00001F)) < 0.000001F,
        "soft limiter must enter its knee continuously with unit slope");
    require(
        softLimit(std::numeric_limits<float>::max()) <= 1.0F,
        "soft limiter must bound the largest finite input");
    require(
        softLimit(std::numeric_limits<float>::quiet_NaN()) == 0.0F,
        "soft limiter must neutralize NaN");
    require(
        softLimit(std::numeric_limits<float>::infinity()) == 0.0F,
        "soft limiter must neutralize infinity");
}

void testSynthAndEffectsStayFinite() {
    intervaltablet::dsp::SynthEngine synth;
    synth.prepare(48000.0F);
    synth.setParameter(intervaltablet::dsp::ParameterId::DelayFeedback, 0.94F);
    synth.setParameter(intervaltablet::dsp::ParameterId::ReverbMix, 1.0F);
    for (int note = 48; note < 60; ++note) synth.noteOn(note, 127);

    std::vector<float> buffer(512 * 2);
    for (int block = 0; block < 4000; ++block) {
        synth.process(buffer.data(), 512);
        for (const float sample : buffer) {
            require(std::isfinite(sample), "DSP output must stay finite");
            require(std::abs(sample) <= 1.0F, "soft limiter must bound output");
        }
        if (block == 50) {
            for (int note = 48; note < 60; ++note) synth.noteOff(note);
        }
    }
    synth.panic();
    synth.process(buffer.data(), 512);
    for (const float sample : buffer) {
        require(std::isfinite(sample), "panic output must stay finite");
        require(sample == 0.0F, "panic must invalidate every effect tail immediately");
    }
}

void testLogicalDelayClearInvalidatesOldStorage() {
    intervaltablet::dsp::FractionalDelayLine line;
    line.prepare(32U);
    for (int sample = 1; sample <= 19; ++sample) line.write(static_cast<float>(sample));
    require(line.read(1.0F) == 19.0F, "prepared delay must expose its newest stored sample");

    // Clear deliberately occurs away from the ring origin.
    line.clear();
    for (int delay = 1; delay <= 30; ++delay) {
        require(line.read(static_cast<float>(delay)) == 0.0F, "clear must hide all pre-reset delay data");
    }

    line.write(0.75F);
    require(line.read(1.0F) == 0.75F, "delay must expose data written after logical clear");
    require(line.read(2.0F) == 0.0F, "delay must not expose adjacent stale storage during refill");
    require(
        std::abs(line.read(1.5F) - 0.375F) < 0.000001F,
        "fractional interpolation must treat an invalid endpoint as zero");

    for (int sample = 0; sample < 64; ++sample) line.write(0.0F);
    for (int delay = 1; delay <= 30; ++delay) {
        require(
            line.read(static_cast<float>(delay)) == 0.0F,
            "pre-reset delay data must not reappear after a complete refill and wrap");
    }
}

void testReverbFiltersInvalidateOldStorage() {
    intervaltablet::dsp::CombFilter comb;
    comb.prepare(5U);
    (void)comb.process(1.0F, 0.79F, 0.24F);
    for (int sample = 0; sample < 12; ++sample) (void)comb.process(0.0F, 0.79F, 0.24F);
    comb.clear();
    for (int sample = 0; sample < 12; ++sample) {
        require(
            comb.process(0.0F, 0.79F, 0.24F) == 0.0F,
            "comb clear must prevent stale feedback from reappearing after wrap");
    }

    intervaltablet::dsp::AllPassFilter allPass;
    allPass.prepare(5U);
    (void)allPass.process(1.0F);
    for (int sample = 0; sample < 12; ++sample) (void)allPass.process(0.0F);
    allPass.clear();
    for (int sample = 0; sample < 12; ++sample) {
        require(
            allPass.process(0.0F) == 0.0F,
            "all-pass clear must prevent stale state from reappearing after wrap");
    }
}

void testAllPassPreservesEnergyAndMagnitude() {
    using intervaltablet::dsp::AllPassFilter;

    AllPassFilter impulseFilter;
    constexpr std::size_t impulseDelay = 37U;
    impulseFilter.prepare(impulseDelay);
    double outputEnergy = 0.0;
    for (std::size_t sample = 0; sample < impulseDelay * 40U; ++sample) {
        const float input = sample == 0U ? 1.0F : 0.0F;
        const float output = impulseFilter.process(input);
        outputEnergy += static_cast<double>(output) * static_cast<double>(output);
    }
    require(
        std::abs(outputEnergy - 1.0) < 0.00001,
        "all-pass impulse response must preserve total energy");

    constexpr std::array<float, 3> sampleRates{44100.0F, 48000.0F, 96000.0F};
    constexpr std::array<float, 3> frequencies{110.0F, 997.0F, 7000.0F};
    for (const float sampleRate : sampleRates) {
        for (const float frequency : frequencies) {
            AllPassFilter sineFilter;
            sineFilter.prepare(static_cast<std::size_t>(sampleRate * 0.011F));
            const int warmupFrames = static_cast<int>(sampleRate);
            const int measuredFrames = static_cast<int>(sampleRate);
            double inputEnergy = 0.0;
            double filteredEnergy = 0.0;
            for (int frame = 0; frame < warmupFrames + measuredFrames; ++frame) {
                const float phase = intervaltablet::dsp::kTwoPi * frequency *
                    static_cast<float>(frame) / sampleRate;
                const float input = 0.25F * std::sin(phase);
                const float output = sineFilter.process(input);
                require(std::isfinite(output), "all-pass sine response must remain finite");
                if (frame >= warmupFrames) {
                    inputEnergy += static_cast<double>(input) * static_cast<double>(input);
                    filteredEnergy += static_cast<double>(output) * static_cast<double>(output);
                }
            }
            const double gain = std::sqrt(filteredEnergy / inputEnergy);
            require(
                std::abs(gain - 1.0) < 0.002,
                "all-pass magnitude must remain unity across sample rates and frequencies");
        }
    }
}

void testReverbGainIsNormalizedAcrossSampleRates() {
    using intervaltablet::dsp::StereoReverb;
    constexpr std::array<float, 3> sampleRates{44100.0F, 48000.0F, 96000.0F};
    constexpr float inputLevel = 0.10F;

    for (const float sampleRate : sampleRates) {
        StereoReverb wetOnly;
        wetOnly.prepare(sampleRate, 1.0F);
        float settledLeft = 0.0F;
        float settledRight = 0.0F;
        const int frames = static_cast<int>(sampleRate * 3.0F);
        for (int frame = 0; frame < frames; ++frame) {
            float left = inputLevel;
            float right = inputLevel;
            wetOnly.process(left, right);
            require(std::isfinite(left) && std::isfinite(right), "normalized reverb must remain finite");
            settledLeft = left;
            settledRight = right;
        }
        require(
            std::abs(settledLeft - inputLevel) < 0.002F,
            "settled left reverb gain must remain near unity");
        require(
            std::abs(settledRight - inputLevel) < 0.002F,
            "settled right reverb gain must remain near unity");

        StereoReverb invalidParameters;
        const float nan = std::numeric_limits<float>::quiet_NaN();
        invalidParameters.prepare(nan, nan);
        invalidParameters.setMix(nan);
        float left = inputLevel;
        float right = inputLevel;
        for (int frame = 0; frame < 2048; ++frame) {
            left = inputLevel;
            right = inputLevel;
            invalidParameters.process(left, right);
        }
        require(
            std::isfinite(left) && std::isfinite(right),
            "non-finite reverb parameters must not poison its state");
    }
}

void testOscillatorMixNormalization() {
    using intervaltablet::dsp::ParameterId;
    using intervaltablet::dsp::SynthEngine;

    SynthEngine synth;
    synth.prepare(48000.0F);
    auto mixes = synth.normalizedOscillatorMixesForTesting();
    require(nearlyEqual(mixes[0] + mixes[1] + mixes[2], 1.0F), "default oscillator mix must retain unity sum");
    require(nearlyEqual(mixes[0], 0.65F), "default saw mix must remain unchanged");
    require(nearlyEqual(mixes[1], 0.20F), "default pulse mix must remain unchanged");
    require(nearlyEqual(mixes[2], 0.15F), "default triangle mix must remain unchanged");

    synth.setParameter(ParameterId::SawMix, 1.0F);
    synth.setParameter(ParameterId::PulseMix, 1.0F);
    synth.setParameter(ParameterId::TriangleMix, 1.0F);
    mixes = synth.normalizedOscillatorMixesForTesting();
    require(nearlyEqual(mixes[0], 1.0F / 3.0F), "over-unity saw mix must normalize proportionally");
    require(nearlyEqual(mixes[1], 1.0F / 3.0F), "over-unity pulse mix must normalize proportionally");
    require(nearlyEqual(mixes[2], 1.0F / 3.0F), "over-unity triangle mix must normalize proportionally");
    require(nearlyEqual(mixes[0] + mixes[1] + mixes[2], 1.0F), "normalized oscillator sum must not exceed unity");

    synth.setParameter(ParameterId::SawMix, std::numeric_limits<float>::quiet_NaN());
    require(
        synth.normalizedOscillatorMixesForTesting() == mixes,
        "NaN oscillator parameters must leave the normalized mix unchanged");
}

void testOscillatorTimbreChangesAreSmoothed() {
    using intervaltablet::dsp::ParameterId;
    using intervaltablet::dsp::SynthEngine;

    constexpr float sampleRate = 48000.0F;
    SynthEngine synth;
    synth.prepare(sampleRate);
    const auto initial = synth.smoothedOscillatorMixesForTesting();
    const float initialPulseWidth = synth.smoothedPulseWidthForTesting();

    synth.setParameter(ParameterId::SawMix, 0.10F);
    synth.setParameter(ParameterId::PulseMix, 0.80F);
    synth.setParameter(ParameterId::TriangleMix, 0.10F);
    synth.setParameter(ParameterId::PulseWidth, 0.80F);
    const auto target = synth.normalizedOscillatorMixesForTesting();
    require(
        synth.smoothedOscillatorMixesForTesting() == initial,
        "oscillator mix targets must not jump on the control thread");
    require(
        synth.smoothedPulseWidthForTesting() == initialPulseWidth,
        "pulse-width target must not jump on the control thread");

    std::array<float, 2> firstFrame{};
    synth.process(firstFrame.data(), 1);
    const auto first = synth.smoothedOscillatorMixesForTesting();
    for (std::size_t index = 0; index < first.size(); ++index) {
        const float lower = std::min(initial[index], target[index]);
        const float upper = std::max(initial[index], target[index]);
        require(first[index] > lower && first[index] < upper, "first smoothed mix step must stay inside its endpoints");
        require(std::abs(first[index] - initial[index]) < 0.002F, "one audio frame must not create an oscillator mix zipper step");
    }
    const float firstPulseWidth = synth.smoothedPulseWidthForTesting();
    require(
        firstPulseWidth > initialPulseWidth && firstPulseWidth < 0.80F,
        "first smoothed pulse-width step must stay inside its endpoints");
    require(
        firstPulseWidth - initialPulseWidth < 0.002F,
        "one audio frame must not create a pulse-width zipper step");

    std::vector<float> settleBuffer(static_cast<std::size_t>(4800) * 2U, 0.0F);
    synth.process(settleBuffer.data(), 4800);
    const auto settled = synth.smoothedOscillatorMixesForTesting();
    for (std::size_t index = 0; index < settled.size(); ++index) {
        require(std::abs(settled[index] - target[index]) < 0.005F, "oscillator mix smoothing must converge within 100 ms");
    }
    require(
        nearlyEqual(settled[0] + settled[1] + settled[2], 1.0F, 0.0001F),
        "smoothed oscillator mix must retain the normalized sum");
    require(
        std::abs(synth.smoothedPulseWidthForTesting() - 0.80F) < 0.005F,
        "pulse-width smoothing must converge within 100 ms");
}

void testSmoothedValueSnapsExactlyToZero() {
    using intervaltablet::dsp::SmoothedValue;

    SmoothedValue value;
    value.prepare(48000.0F);
    value.reset(1.0F);
    value.setTarget(0.0F);
    for (int frame = 0; frame < 20000; ++frame) (void)value.next();

    require(value.current() == 0.0F, "smoothing must not leave a subnormal tail at a zero target");
}

void testFilterCoefficientChangesAreSmoothed() {
    using intervaltablet::dsp::StateVariableLowPass;

    constexpr float sampleRate = 48000.0F;
    StateVariableLowPass filter;
    filter.prepare(sampleRate);
    const auto initial = filter.smoothedCoefficientsForTesting();
    filter.setCutoff(12000.0F);
    filter.setResonance(0.75F);
    const auto target = StateVariableLowPass::makeCoefficients(sampleRate, 12000.0F, 0.75F);
    const auto beforeProcess = filter.smoothedCoefficientsForTesting();
    require(nearlyEqual(beforeProcess.a1, initial.a1), "filter a1 must not jump when its target changes");
    require(nearlyEqual(beforeProcess.a2, initial.a2), "filter a2 must not jump when its target changes");
    require(nearlyEqual(beforeProcess.a3, initial.a3), "filter a3 must not jump when its target changes");

    (void)filter.process(0.0F);
    const auto first = filter.smoothedCoefficientsForTesting();
    require(!nearlyEqual(first.a1, initial.a1), "filter smoothing must advance in process");
    require(std::abs(first.a1 - initial.a1) < 0.002F, "one frame must not create a filter zipper step");

    for (int frame = 0; frame < 20000; ++frame) (void)filter.process(0.0F);
    const auto settled = filter.smoothedCoefficientsForTesting();
    require(nearlyEqual(settled.a1, target.a1, 0.000002F), "smoothed filter a1 must reach its target");
    require(nearlyEqual(settled.a2, target.a2, 0.000002F), "smoothed filter a2 must reach its target");
    require(nearlyEqual(settled.a3, target.a3, 0.000002F), "smoothed filter a3 must reach its target");
}

void testActiveSustainChangesAreSmoothed() {
    using intervaltablet::dsp::Adsr;

    constexpr float sampleRate = 48000.0F;
    Adsr envelope;
    envelope.prepare(sampleRate);
    envelope.setParameters(0.0005F, 0.001F, 0.80F, 0.35F);
    envelope.noteOn();
    float before = 0.0F;
    for (int frame = 0; frame < 500; ++frame) before = envelope.process();
    require(nearlyEqual(before, 0.80F, 0.001F), "test envelope must reach its initial sustain level");

    envelope.setCoefficients(Adsr::makeCoefficients(sampleRate, 0.0005F, 0.001F, 0.10F, 0.35F));
    const float first = envelope.process();
    require(first < before && first > 0.10F, "active sustain must move toward its target without jumping");
    require(before - first < 0.002F, "one frame must not create a sustain zipper step");
    for (int frame = 0; frame < 20000; ++frame) (void)envelope.process();
    require(nearlyEqual(envelope.process(), 0.10F, 0.000002F), "smoothed sustain must reach its target");
}

void testArpeggioReleaseDoesNotFillVoicePool() {
    using intervaltablet::dsp::SynthEngine;
    constexpr int sampleRate = 48000;
    constexpr int stepFrames = sampleRate / 8;  // 120 BPM, six MIDI clocks per step.
    constexpr int gateFrames = stepFrames * 3 / 4;
    constexpr std::array<int, 5> notes{60, 62, 64, 65, 67};

    SynthEngine synth;
    synth.prepare(static_cast<float>(sampleRate));
    std::vector<float> output(static_cast<std::size_t>(stepFrames) * 2U, 0.0F);
    std::size_t maximumVoices = 0U;
    for (int step = 0; step < 24; ++step) {
        const int note = notes[static_cast<std::size_t>(step) % notes.size()];
        synth.noteOn(note, 64);
        synth.process(output.data(), gateFrames);
        maximumVoices = std::max(maximumVoices, synth.activeVoices());
        synth.noteOff(note);
        synth.process(output.data(), stepFrames - gateFrames);
        maximumVoices = std::max(maximumVoices, synth.activeVoices());
    }
    require(maximumVoices <= 4U, "a default one-note arpeggio must not fill all eight voices with release tails");

    synth.process(output.data(), stepFrames);
    synth.process(output.data(), stepFrames);
    synth.process(output.data(), stepFrames);
    require(synth.activeVoices() == 0U, "all arpeggio tails must finish within the configured release window");
}

void testNominalStackedPolyphonyStaysBelowLimiterKnee() {
    using intervaltablet::dsp::SynthEngine;
    SynthEngine synth;
    synth.prepare(48000.0F);

    // Two simultaneous documented three-tone voicings at the production
    // velocity rule: lead at 64, harmonies at half velocity.
    constexpr std::array<int, 6> notes{60, 57, 53, 64, 61, 57};
    constexpr std::array<int, 6> velocities{64, 32, 32, 64, 32, 32};
    for (std::size_t voice = 0; voice < notes.size(); ++voice) {
        synth.noteOn(notes[voice], velocities[voice]);
    }
    synth.resetOutputStatsForTesting();
    std::vector<float> output(512U * 2U, 0.0F);
    for (int block = 0; block < 480; ++block) {
        synth.process(output.data(), 512);
        for (const float sample : output) {
            require(std::isfinite(sample), "nominal stacked polyphony must remain finite");
            require(std::abs(sample) <= 1.0F, "nominal stacked polyphony must remain bounded");
        }
    }
    const auto stats = synth.outputStatsForTesting();
    require(stats.processedSamples == 480U * 512U * 2U, "output meter must cover every rendered channel sample");
    require(
        stats.maximumPreLimiterMagnitude < intervaltablet::dsp::kSoftLimitKnee,
        "two nominal stacked voicings must retain linear headroom before the limiter");
    require(
        stats.samplesAboveLimiterKnee == 0U,
        "nominal stacked polyphony must not rely on saturation for level control");
}

void testParameterUpdatesStayTargeted() {
    using intervaltablet::dsp::ParameterId;
    using intervaltablet::dsp::SynthEngine;

    SynthEngine synth;
    synth.prepare(48000.0F);
    synth.resetParameterWorkCountersForTesting();

    synth.noteOn(60, 100);
    synth.setParameter(ParameterId::SawMix, 0.2F);
    synth.setParameter(ParameterId::PulseMix, 0.6F);
    synth.setParameter(ParameterId::TriangleMix, 0.2F);
    synth.setParameter(ParameterId::DelayMix, 0.5F);
    auto work = synth.parameterWorkCountersForTesting();
    require(work.envelopeCoefficientComputations == 0U, "NoteOn and mix changes must do no ADSR math");
    require(work.filterCoefficientComputations == 0U, "NoteOn and mix changes must do no filter math");
    require(work.envelopeVoiceUpdates == 0U, "NoteOn and mix changes must not recalculate ADSR coefficients");
    require(work.cutoffVoiceUpdates == 0U, "NoteOn and mix changes must not recalculate cutoff coefficients");
    require(work.resonanceVoiceUpdates == 0U, "NoteOn and mix changes must not recalculate resonance coefficients");

    synth.setParameter(ParameterId::PulseWidth, 0.7F);
    work = synth.parameterWorkCountersForTesting();
    require(work.envelopeVoiceUpdates == 0U, "pulse width must not update envelopes on the control thread");
    require(work.cutoffVoiceUpdates == 0U, "pulse width must not update filters on the control thread");
    synth.setParameter(ParameterId::PulseWidth, 0.7F);

    synth.resetParameterWorkCountersForTesting();
    synth.setParameter(ParameterId::Attack, 0.02F);
    work = synth.parameterWorkCountersForTesting();
    require(work.envelopeCoefficientComputations == 1U, "attack coefficients must be calculated once");
    require(work.envelopeVoiceUpdates == SynthEngine::kVoiceCount, "attack must update each envelope once");
    require(work.cutoffVoiceUpdates == 0U, "attack must not update filters");
    require(work.resonanceVoiceUpdates == 0U, "attack must not update filter resonance");

    synth.resetParameterWorkCountersForTesting();
    synth.setParameter(ParameterId::Cutoff, 4200.0F);
    work = synth.parameterWorkCountersForTesting();
    require(work.filterCoefficientComputations == 1U, "cutoff coefficients must be calculated once");
    require(work.cutoffVoiceUpdates == SynthEngine::kVoiceCount, "cutoff must update each filter once");
    require(work.resonanceVoiceUpdates == 0U, "cutoff must not issue a second filter update");
    require(work.envelopeVoiceUpdates == 0U, "cutoff must not update envelopes");

    synth.resetParameterWorkCountersForTesting();
    synth.setParameter(ParameterId::Resonance, 0.4F);
    work = synth.parameterWorkCountersForTesting();
    require(work.filterCoefficientComputations == 1U, "resonance coefficients must be calculated once");
    require(work.resonanceVoiceUpdates == SynthEngine::kVoiceCount, "resonance must update each filter once");
    require(work.cutoffVoiceUpdates == 0U, "resonance must not recalculate cutoff separately");
    require(work.envelopeVoiceUpdates == 0U, "resonance must not update envelopes");
}

void testPreparedCoefficientsMatchLegacyPrimitives() {
    using intervaltablet::dsp::Adsr;
    using intervaltablet::dsp::StateVariableLowPass;
    constexpr float sampleRate = 48000.0F;

    const auto envelopeCoefficients = Adsr::makeCoefficients(sampleRate, 0.013F, 0.27F, 0.63F, 0.42F);
    Adsr legacyEnvelope;
    legacyEnvelope.prepare(sampleRate);
    legacyEnvelope.setParameters(0.013F, 0.27F, 0.63F, 0.42F);
    Adsr preparedEnvelope;
    preparedEnvelope.prepare(sampleRate, envelopeCoefficients);
    legacyEnvelope.noteOn();
    preparedEnvelope.noteOn();
    for (int sample = 0; sample < 20000; ++sample) {
        require(
            nearlyEqual(legacyEnvelope.process(), preparedEnvelope.process(), 0.000001F),
            "prepared ADSR coefficients must preserve the legacy envelope curve");
    }
    legacyEnvelope.noteOff();
    preparedEnvelope.noteOff();
    for (int sample = 0; sample < 40000; ++sample) {
        require(
            nearlyEqual(legacyEnvelope.process(), preparedEnvelope.process(), 0.000001F),
            "prepared ADSR coefficients must preserve the release curve");
    }

    const auto filterCoefficients = StateVariableLowPass::makeCoefficients(sampleRate, 4200.0F, 0.37F);
    StateVariableLowPass legacyFilter;
    legacyFilter.prepare(sampleRate);
    legacyFilter.setCutoff(4200.0F);
    legacyFilter.setResonance(0.37F);
    for (int sample = 0; sample < 20000; ++sample) (void)legacyFilter.process(0.0F);
    legacyFilter.reset();
    StateVariableLowPass preparedFilter;
    preparedFilter.prepare(sampleRate, filterCoefficients);
    for (int sample = 0; sample < 4096; ++sample) {
        const float input = std::sin(static_cast<float>(sample) * 0.017F) * 0.7F;
        require(
            nearlyEqual(legacyFilter.process(input), preparedFilter.process(input), 0.000001F),
            "prepared SVF coefficients must preserve the legacy filter response");
    }

    const float nan = std::numeric_limits<float>::quiet_NaN();
    const float infinity = std::numeric_limits<float>::infinity();
    const auto safeEnvelope = Adsr::makeCoefficients(nan, nan, infinity, -infinity, nan);
    require(std::isfinite(safeEnvelope.attackMultiplier), "invalid attack must produce a finite coefficient");
    require(std::isfinite(safeEnvelope.decayMultiplier), "invalid decay must produce a finite coefficient");
    require(std::isfinite(safeEnvelope.sustainLevel), "invalid sustain must produce a finite coefficient");
    require(std::isfinite(safeEnvelope.releaseMultiplier), "invalid release must produce a finite coefficient");
    require(safeEnvelope.attackMultiplier >= 0.0F && safeEnvelope.attackMultiplier <= 1.0F,
        "attack coefficient must remain bounded");
    require(safeEnvelope.sustainLevel >= 0.0F && safeEnvelope.sustainLevel <= 1.0F,
        "sustain coefficient must remain bounded");

    const auto safeFilter = StateVariableLowPass::makeCoefficients(nan, infinity, nan);
    require(std::isfinite(safeFilter.a1), "invalid cutoff must produce finite SVF a1");
    require(std::isfinite(safeFilter.a2), "invalid cutoff must produce finite SVF a2");
    require(std::isfinite(safeFilter.a3), "invalid cutoff must produce finite SVF a3");
}

void testRuntimeParametersSurvivePrepare() {
    using intervaltablet::dsp::Adsr;
    using intervaltablet::dsp::ParameterId;
    using intervaltablet::dsp::StateVariableLowPass;
    using intervaltablet::dsp::SynthEngine;

    SynthEngine synth;
    synth.prepare(48000.0F);
    synth.setParameter(ParameterId::Attack, 0.031F);
    synth.setParameter(ParameterId::Decay, 0.41F);
    synth.setParameter(ParameterId::Sustain, 0.52F);
    synth.setParameter(ParameterId::Release, 0.77F);
    synth.setParameter(ParameterId::Cutoff, 9200.0F);
    synth.setParameter(ParameterId::Resonance, 0.46F);
    synth.setParameter(ParameterId::ChorusMix, 0.73F);
    synth.setParameter(ParameterId::DelayTime, 1.25F);
    synth.setParameter(ParameterId::DelayFeedback, 0.81F);
    synth.setParameter(ParameterId::DelayMix, 0.64F);
    synth.setParameter(ParameterId::ReverbMix, 0.58F);
    synth.setParameter(ParameterId::Master, 1.10F);

    synth.prepare(44100.0F);
    auto state = synth.runtimeStateForTesting();
    require(state.sampleRate == 44100.0F, "prepare must accept the newly negotiated sample rate");
    require(nearlyEqual(state.chorusMix, 0.73F), "prepare must restore runtime chorus mix exactly");
    require(nearlyEqual(state.delayTimeSeconds, 1.25F), "prepare must restore runtime delay time in seconds");
    require(nearlyEqual(state.delayFeedback, 0.81F), "prepare must restore runtime delay feedback");
    require(nearlyEqual(state.delayMix, 0.64F), "prepare must restore runtime delay mix");
    require(nearlyEqual(state.reverbMix, 0.58F), "prepare must restore runtime reverb mix");
    require(nearlyEqual(state.master, 1.10F), "prepare must restore runtime master gain exactly");

    const auto expectedEnvelope = Adsr::makeCoefficients(44100.0F, 0.031F, 0.41F, 0.52F, 0.77F);
    const auto expectedFilter = StateVariableLowPass::makeCoefficients(44100.0F, 9200.0F, 0.46F);
    require(nearlyEqual(state.envelopeCoefficients.attackMultiplier, expectedEnvelope.attackMultiplier),
        "prepare must recalculate ADSR for the negotiated sample rate");
    require(nearlyEqual(state.filterCoefficients.a1, expectedFilter.a1),
        "prepare must recalculate SVF for the negotiated sample rate");

    synth.prepare(96000.0F);
    state = synth.runtimeStateForTesting();
    const auto expectedFilter96k = StateVariableLowPass::makeCoefficients(96000.0F, 9200.0F, 0.46F);
    require(nearlyEqual(state.delayTimeSeconds, 1.25F), "sample-rate change must preserve delay seconds");
    require(nearlyEqual(state.chorusMix, 0.73F), "sample-rate change must preserve effect targets");
    require(nearlyEqual(state.master, 1.10F), "sample-rate change must preserve master target");
    require(nearlyEqual(state.filterCoefficients.a1, expectedFilter96k.a1),
        "sample-rate change must refresh shared filter coefficients");

    intervaltablet::dsp::StereoDelay delay;
    delay.prepare(48000.0F, 0.4F, 0.3F, 0.2F);
    delay.setMix(0.8F);
    float left = 0.0F;
    float right = 0.0F;
    delay.process(left, right);
    require(delay.currentMixForTesting() > 0.2F && delay.currentMixForTesting() < 0.8F,
        "normal parameter changes must retain smoothing after exact prepare restoration");
}

void testInvalidParametersCannotPoisonDsp() {
    using intervaltablet::dsp::ParameterId;
    using intervaltablet::dsp::SynthEngine;
    const float nan = std::numeric_limits<float>::quiet_NaN();
    const float infinity = std::numeric_limits<float>::infinity();

    SynthEngine synth;
    synth.prepare(nan);
    const auto initial = synth.runtimeStateForTesting();
    require(initial.sampleRate == intervaltablet::dsp::kDefaultSampleRate,
        "non-finite sample rate must use a safe default");

    for (int rawId = static_cast<int>(ParameterId::SawMix);
         rawId <= static_cast<int>(ParameterId::TempoBpm);
         ++rawId) {
        const auto id = static_cast<ParameterId>(rawId);
        synth.setParameter(id, nan);
        synth.setParameter(id, infinity);
        synth.setParameter(id, -infinity);
    }
    synth.prepare(infinity);
    const auto afterInvalid = synth.runtimeStateForTesting();
    require(afterInvalid.sampleRate == intervaltablet::dsp::kDefaultSampleRate,
        "infinite sample rate must use a safe default");
    require(afterInvalid.chorusMix == initial.chorusMix, "invalid chorus target must be ignored");
    require(afterInvalid.delayTimeSeconds == initial.delayTimeSeconds, "invalid delay time must be ignored");
    require(afterInvalid.delayFeedback == initial.delayFeedback, "invalid feedback must be ignored");
    require(afterInvalid.delayMix == initial.delayMix, "invalid delay mix must be ignored");
    require(afterInvalid.reverbMix == initial.reverbMix, "invalid reverb mix must be ignored");
    require(afterInvalid.master == initial.master, "invalid master target must be ignored");

    const float maximumFinite = std::numeric_limits<float>::max();
    synth.setParameter(ParameterId::Attack, maximumFinite);
    synth.setParameter(ParameterId::Decay, -maximumFinite);
    synth.setParameter(ParameterId::Sustain, maximumFinite);
    synth.setParameter(ParameterId::Release, -maximumFinite);
    synth.setParameter(ParameterId::Cutoff, maximumFinite);
    synth.setParameter(ParameterId::Resonance, -maximumFinite);
    synth.setParameter(ParameterId::ChorusMix, maximumFinite);
    synth.setParameter(ParameterId::DelayTime, maximumFinite);
    synth.setParameter(ParameterId::DelayFeedback, -maximumFinite);
    synth.setParameter(ParameterId::DelayMix, -maximumFinite);
    synth.setParameter(ParameterId::ReverbMix, maximumFinite);
    synth.setParameter(ParameterId::Master, maximumFinite);
    synth.prepare(48000.0F);
    const auto clamped = synth.runtimeStateForTesting();
    require(clamped.chorusMix == 1.0F, "finite chorus value must clamp to one");
    require(clamped.delayTimeSeconds == 2.0F, "finite delay time must clamp to two seconds");
    require(clamped.delayFeedback == 0.0F, "negative feedback must clamp to zero");
    require(clamped.delayMix == 0.0F, "negative delay mix must clamp to zero");
    require(clamped.reverbMix == 1.0F, "finite reverb mix must clamp to one");
    require(clamped.master == 1.5F, "finite master gain must clamp to its safe maximum");
    require(intervaltablet::dsp::sanitizeSampleRate(maximumFinite) == intervaltablet::dsp::kMaximumSampleRate,
        "finite sample rate must clamp before any allocation size conversion");

    synth.noteOn(60, 127);
    std::vector<float> output(2048U, 0.0F);
    synth.process(output.data(), static_cast<int>(output.size() / 2U));
    for (const float sample : output) require(std::isfinite(sample), "invalid parameters must not poison output");
    require(intervaltablet::dsp::softLimit(nan) == 0.0F, "soft limiter must neutralize non-finite input");

    intervaltablet::dsp::FractionalDelayLine line;
    line.prepare(16U);
    require(line.read(nan) == 0.0F, "non-finite delay read must remain in bounds and silent");

    intervaltablet::dsp::StereoDelay delay;
    delay.prepare(nan, nan, infinity, -infinity);
    float left = 0.0F;
    float right = 0.0F;
    for (int sample = 0; sample < 128; ++sample) delay.process(left, right);
    require(std::isfinite(left) && std::isfinite(right), "invalid delay preparation must stay finite");
}

void testPanicResetsVoicePhaseAndFilterState() {
    using intervaltablet::dsp::Adsr;
    using intervaltablet::dsp::StateVariableLowPass;
    using intervaltablet::dsp::SynthVoice;
    constexpr float sampleRate = 48000.0F;
    const auto envelope = Adsr::makeCoefficients(sampleRate, 0.005F, 0.18F, 0.7F, 0.35F);
    const auto filter = StateVariableLowPass::makeCoefficients(sampleRate, 3500.0F, 0.15F);

    SynthVoice reused;
    SynthVoice fresh;
    reused.prepare(sampleRate, envelope, filter);
    fresh.prepare(sampleRate, envelope, filter);
    reused.start(64, 127, 1U, intervaltablet::dsp::midiToHz(64));
    for (int sample = 0; sample < 317; ++sample) (void)reused.process(0.65F, 0.2F, 0.15F, 0.5F);
    reused.panic();

    reused.start(64, 127, 2U, intervaltablet::dsp::midiToHz(64));
    fresh.start(64, 127, 1U, intervaltablet::dsp::midiToHz(64));
    for (int sample = 0; sample < 1024; ++sample) {
        require(
            nearlyEqual(
                reused.process(0.65F, 0.2F, 0.15F, 0.5F),
                fresh.process(0.65F, 0.2F, 0.15F, 0.5F),
                0.000001F),
            "first note after Panic must start with clean oscillator and filter state");
    }
}

void testPanicResetBudget() {
    intervaltablet::dsp::SynthEngine synth;
    synth.prepare(48000.0F);
    constexpr int kResetCount = 250000;
    const auto started = std::chrono::steady_clock::now();
    for (int reset = 0; reset < kResetCount; ++reset) synth.panic();
    const auto elapsed = std::chrono::steady_clock::now() - started;
    require(
        elapsed < std::chrono::seconds{2},
        "panic reset budget must remain independent of allocated delay storage");
    require(synth.activeVoices() == 0U, "repeated panic must leave every voice inactive");
}

void testMidiFrequency() {
    const float frequency = intervaltablet::dsp::midiToHz(69);
    require(std::abs(frequency - 440.0F) < 0.001F, "A4 must be 440 Hz");
}

}  // namespace

int main() {
    testQueue();
    testEnvelope();
    testEnvelopeDurationsAreMusicalTimes();
    testSoftLimiterPreservesLinearHeadroom();
    testSynthAndEffectsStayFinite();
    testLogicalDelayClearInvalidatesOldStorage();
    testReverbFiltersInvalidateOldStorage();
    testAllPassPreservesEnergyAndMagnitude();
    testReverbGainIsNormalizedAcrossSampleRates();
    testOscillatorMixNormalization();
    testOscillatorTimbreChangesAreSmoothed();
    testSmoothedValueSnapsExactlyToZero();
    testFilterCoefficientChangesAreSmoothed();
    testActiveSustainChangesAreSmoothed();
    testArpeggioReleaseDoesNotFillVoicePool();
    testNominalStackedPolyphonyStaysBelowLimiterKnee();
    testParameterUpdatesStayTargeted();
    testPreparedCoefficientsMatchLegacyPrimitives();
    testRuntimeParametersSurvivePrepare();
    testInvalidParametersCannotPoisonDsp();
    testPanicResetsVoicePhaseAndFilterState();
    testPanicResetBudget();
    testMidiFrequency();
    std::cout << "Native DSP smoke tests: OK\n";
    return EXIT_SUCCESS;
}
