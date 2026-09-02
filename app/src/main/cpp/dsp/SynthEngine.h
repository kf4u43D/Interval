#pragma once

#include "DspPrimitives.h"
#include "Effects.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>

namespace intervaltablet::dsp {

enum class ParameterId : int {
    SawMix = 0,
    PulseMix = 1,
    TriangleMix = 2,
    PulseWidth = 3,
    Attack = 4,
    Decay = 5,
    Sustain = 6,
    Release = 7,
    Cutoff = 8,
    Resonance = 9,
    ChorusMix = 10,
    DelayTime = 11,
    DelayFeedback = 12,
    DelayMix = 13,
    ReverbMix = 14,
    Master = 15,
    FilterAttack = 16,
    FilterDecay = 17,
    FilterSustain = 18,
    FilterRelease = 19,
    FilterEnvelopeAmount = 20,
    Drive = 21,
    LfoRate = 22,
    LfoDepth = 23,
    LfoDestination = 24,
    LfoDelay = 25,
    DelaySyncBeats = 26,
    TempoBpm = 27,
};

class SynthVoice {
public:
    void prepare(
        const float sampleRate,
        const Adsr::Coefficients& envelopeCoefficients,
        const StateVariableLowPass::Coefficients& filterCoefficients) noexcept {
        prepare(
            sampleRate,
            envelopeCoefficients,
            Adsr::makeCoefficients(sampleRate, 0.005F, 0.18F, 0.0F, 0.35F),
            filterCoefficients);
    }

    void prepare(
        const float sampleRate,
        const Adsr::Coefficients& envelopeCoefficients,
        const Adsr::Coefficients& filterEnvelopeCoefficients,
        const StateVariableLowPass::Coefficients& filterCoefficients) noexcept {
        oscillator_.prepare(sampleRate);
        envelope_.prepare(sampleRate, envelopeCoefficients);
        filterEnvelope_.prepare(sampleRate, filterEnvelopeCoefficients);
        filter_.prepare(sampleRate, filterCoefficients);
        active_ = false;
        age_ = 0;
        filterUpdateCountdown_ = 0U;
    }

    void start(
        const int note,
        const int velocity,
        const std::uint64_t age,
        const float frequency) noexcept {
        note_ = std::clamp(note, 0, 127);
        velocityGain_ = static_cast<float>(std::clamp(velocity, 1, 127)) / 127.0F;
        age_ = age;
        oscillator_.setFrequency(frequency);
        envelope_.noteOn();
        filterEnvelope_.noteOn();
        filterUpdateCountdown_ = 0U;
        active_ = true;
    }

    void release() noexcept {
        envelope_.noteOff();
        filterEnvelope_.noteOff();
    }
    void panic() noexcept {
        oscillator_.reset();
        envelope_.reset();
        filterEnvelope_.reset();
        filter_.reset();
        active_ = false;
    }

    void setEnvelopeCoefficients(const Adsr::Coefficients& coefficients) noexcept {
        envelope_.setCoefficients(coefficients);
    }

    void setFilterEnvelopeCoefficients(const Adsr::Coefficients& coefficients) noexcept {
        filterEnvelope_.setCoefficients(coefficients);
    }

    void setFilterCoefficients(const StateVariableLowPass::Coefficients& coefficients) noexcept {
        filter_.setCoefficients(coefficients);
    }

    float process(
        const float sawMix,
        const float pulseMix,
        const float triangleMix,
        const float pulseWidth,
        const float baseCutoff = 3500.0F,
        const float filterEnvelopeAmount = 0.0F,
        const float lfoFilterModulation = 0.0F) noexcept {
        if (!active_) return 0.0F;
        const float envelope = envelope_.process();
        const float filterEnvelope = filterEnvelope_.process();
        if (!envelope_.isActive()) {
            active_ = false;
            return 0.0F;
        }
        if (filterUpdateCountdown_ == 0U) {
            const float octaveOffset =
                filterEnvelope * filterEnvelopeAmount + lfoFilterModulation * 3.0F;
            filter_.setCutoff(baseCutoff * std::exp2(octaveOffset));
            filterUpdateCountdown_ = 15U;
        } else {
            --filterUpdateCountdown_;
        }
        const float sample = oscillator_.process(sawMix, pulseMix, triangleMix, pulseWidth);
        return filter_.process(sample) * envelope * velocityGain_;
    }

    bool isActive() const noexcept { return active_; }
    bool isReleasing() const noexcept { return active_ && envelope_.isReleasing(); }
    int note() const noexcept { return note_; }
    std::uint64_t age() const noexcept { return age_; }

private:
    Oscillator oscillator_{};
    Adsr envelope_{};
    Adsr filterEnvelope_{};
    StateVariableLowPass filter_{};
    int note_{-1};
    float velocityGain_{0.0F};
    std::uint64_t age_{0};
    bool active_{false};
    std::uint8_t filterUpdateCountdown_{0U};
};

class SynthEngine {
public:
    static constexpr std::size_t kVoiceCount = 8;

#if defined(INTERVAL_NATIVE_TESTING)
    struct ParameterWorkCounters {
        std::size_t envelopeCoefficientComputations{0};
        std::size_t filterCoefficientComputations{0};
        std::size_t envelopeVoiceUpdates{0};
        std::size_t cutoffVoiceUpdates{0};
        std::size_t resonanceVoiceUpdates{0};
    };

    struct RuntimeState {
        float sampleRate{0.0F};
        float chorusMix{0.0F};
        float delayTimeSeconds{0.0F};
        float delayFeedback{0.0F};
        float delayMix{0.0F};
        float reverbMix{0.0F};
        float master{0.0F};
        Adsr::Coefficients envelopeCoefficients{};
        StateVariableLowPass::Coefficients filterCoefficients{};
    };

    struct OutputStats {
        float maximumPreLimiterMagnitude{0.0F};
        std::size_t samplesAboveLimiterKnee{0U};
        std::size_t processedSamples{0U};
    };
#endif

    void prepare(const float sampleRate) {
        sampleRate_ = sanitizeSampleRate(sampleRate);
        for (std::size_t note = 0; note < noteFrequencies_.size(); ++note) {
            noteFrequencies_[note] = midiToHz(static_cast<int>(note));
        }
        recomputeEnvelopeCoefficients();
        recomputeFilterEnvelopeCoefficients();
        recomputeFilterCoefficients();
        recomputeNormalizedOscillatorMixes();
        smoothedSawMix_.prepare(sampleRate_);
        smoothedPulseMix_.prepare(sampleRate_);
        smoothedTriangleMix_.prepare(sampleRate_);
        smoothedPulseWidth_.prepare(sampleRate_);
        smoothedSawMix_.reset(normalizedSawMix_);
        smoothedPulseMix_.reset(normalizedPulseMix_);
        smoothedTriangleMix_.reset(normalizedTriangleMix_);
        smoothedPulseWidth_.reset(pulseWidth_);
        for (auto& voice : voices_) {
            voice.prepare(
                sampleRate_,
                envelopeCoefficients_,
                filterEnvelopeCoefficients_,
                filterCoefficients_);
        }
        chorus_.prepare(sampleRate_, chorusMix_);
        delay_.prepare(sampleRate_, delayTimeSeconds_, delayFeedback_, delayMix_);
        reverb_.prepare(sampleRate_, reverbMix_);
        master_.prepare(sampleRate_);
        master_.reset(masterGain_);
        drive_.prepare(sampleRate_);
        drive_.reset(driveAmount_);
        lfoPhase_ = 0.0F;
        lfoDelaySamplesElapsed_ = 0U;
        ageCounter_ = 0;
#if defined(INTERVAL_NATIVE_TESTING)
        resetOutputStatsForTesting();
#endif
    }

    void noteOn(const int note, const int velocity) noexcept {
        const int boundedNote = std::clamp(note, 0, 127);
        auto* voice = findVoiceForStart();
        const float preparedFrequency = noteFrequencies_[static_cast<std::size_t>(boundedNote)];
        // AudioEngine always prepares before accepting events. Keep the fallback
        // for direct host use without putting exp2 in the normal callback path.
        const float frequency = preparedFrequency > 0.0F
            ? preparedFrequency
            : midiToHz(boundedNote);
        voice->start(boundedNote, velocity, ++ageCounter_, frequency);
        lfoDelaySamplesElapsed_ = 0U;
    }

    void noteOff(const int note) noexcept {
        SynthVoice* selected = nullptr;
        for (auto& voice : voices_) {
            if (voice.isActive() && !voice.isReleasing() && voice.note() == note) {
                if (selected == nullptr || voice.age() < selected->age()) selected = &voice;
            }
        }
        if (selected != nullptr) selected->release();
    }

    void panic() noexcept {
        for (auto& voice : voices_) voice.panic();
        chorus_.clear();
        delay_.clear();
        reverb_.clear();
    }

    void setParameter(const ParameterId id, const float value) noexcept {
        if (!std::isfinite(value)) return;
        switch (id) {
            case ParameterId::SawMix:
                if (const float bounded = clampUnit(value); bounded != sawMix_) {
                    sawMix_ = bounded;
                    recomputeNormalizedOscillatorMixes();
                }
                break;
            case ParameterId::PulseMix:
                if (const float bounded = clampUnit(value); bounded != pulseMix_) {
                    pulseMix_ = bounded;
                    recomputeNormalizedOscillatorMixes();
                }
                break;
            case ParameterId::TriangleMix:
                if (const float bounded = clampUnit(value); bounded != triangleMix_) {
                    triangleMix_ = bounded;
                    recomputeNormalizedOscillatorMixes();
                }
                break;
            case ParameterId::PulseWidth: {
                const float bounded = std::clamp(value, 0.05F, 0.95F);
                if (bounded == pulseWidth_) break;
                pulseWidth_ = bounded;
                smoothedPulseWidth_.setTarget(bounded);
                break;
            }
            case ParameterId::Attack: {
                const float bounded = std::clamp(value, 0.0005F, 10.0F);
                if (bounded == attack_) break;
                attack_ = bounded;
                recomputeAndApplyEnvelopeCoefficients();
                break;
            }
            case ParameterId::Decay: {
                const float bounded = std::clamp(value, 0.001F, 20.0F);
                if (bounded == decay_) break;
                decay_ = bounded;
                recomputeAndApplyEnvelopeCoefficients();
                break;
            }
            case ParameterId::Sustain: {
                const float bounded = clampUnit(value);
                if (bounded == sustain_) break;
                sustain_ = bounded;
                recomputeAndApplyEnvelopeCoefficients();
                break;
            }
            case ParameterId::Release: {
                const float bounded = std::clamp(value, 0.001F, 30.0F);
                if (bounded == release_) break;
                release_ = bounded;
                recomputeAndApplyEnvelopeCoefficients();
                break;
            }
            case ParameterId::Cutoff: {
                const float bounded = std::clamp(
                    value,
                    20.0F,
                    std::max(20.0F, sampleRate_ * 0.45F));
                if (bounded == cutoff_) break;
                cutoff_ = bounded;
                recomputeAndApplyFilterCoefficients(true);
                break;
            }
            case ParameterId::Resonance: {
                const float bounded = clampUnit(value);
                if (bounded == resonance_) break;
                resonance_ = bounded;
                recomputeAndApplyFilterCoefficients(false);
                break;
            }
            case ParameterId::ChorusMix: {
                const float bounded = clampUnit(value);
                if (bounded == chorusMix_) break;
                chorusMix_ = bounded;
                chorus_.setMix(bounded);
                break;
            }
            case ParameterId::DelayTime: {
                const float bounded = std::clamp(value, 0.01F, 2.0F);
                if (bounded == delayTimeSeconds_) break;
                delayTimeSeconds_ = bounded;
                delay_.setTimeSeconds(bounded);
                break;
            }
            case ParameterId::DelayFeedback: {
                const float bounded = std::clamp(value, 0.0F, 0.94F);
                if (bounded == delayFeedback_) break;
                delayFeedback_ = bounded;
                delay_.setFeedback(bounded);
                break;
            }
            case ParameterId::DelayMix: {
                const float bounded = clampUnit(value);
                if (bounded == delayMix_) break;
                delayMix_ = bounded;
                delay_.setMix(bounded);
                break;
            }
            case ParameterId::ReverbMix: {
                const float bounded = clampUnit(value);
                if (bounded == reverbMix_) break;
                reverbMix_ = bounded;
                reverb_.setMix(bounded);
                break;
            }
            case ParameterId::Master: {
                const float bounded = std::clamp(value, 0.0F, 1.5F);
                if (bounded == masterGain_) break;
                masterGain_ = bounded;
                master_.setTarget(bounded);
                break;
            }
            case ParameterId::FilterAttack: {
                const float bounded = std::clamp(value, 0.0005F, 10.0F);
                if (bounded == filterAttack_) break;
                filterAttack_ = bounded;
                recomputeAndApplyFilterEnvelopeCoefficients();
                break;
            }
            case ParameterId::FilterDecay: {
                const float bounded = std::clamp(value, 0.001F, 20.0F);
                if (bounded == filterDecay_) break;
                filterDecay_ = bounded;
                recomputeAndApplyFilterEnvelopeCoefficients();
                break;
            }
            case ParameterId::FilterSustain: {
                const float bounded = clampUnit(value);
                if (bounded == filterSustain_) break;
                filterSustain_ = bounded;
                recomputeAndApplyFilterEnvelopeCoefficients();
                break;
            }
            case ParameterId::FilterRelease: {
                const float bounded = std::clamp(value, 0.001F, 30.0F);
                if (bounded == filterRelease_) break;
                filterRelease_ = bounded;
                recomputeAndApplyFilterEnvelopeCoefficients();
                break;
            }
            case ParameterId::FilterEnvelopeAmount:
                filterEnvelopeAmount_ = std::clamp(value, -4.0F, 4.0F);
                break;
            case ParameterId::Drive: {
                const float bounded = clampUnit(value);
                if (bounded == driveAmount_) break;
                driveAmount_ = bounded;
                drive_.setTarget(bounded);
                break;
            }
            case ParameterId::LfoRate:
                lfoRateHz_ = std::clamp(value, 0.05F, 20.0F);
                break;
            case ParameterId::LfoDepth:
                lfoDepth_ = clampUnit(value);
                break;
            case ParameterId::LfoDestination:
                lfoDestination_ = std::clamp(static_cast<int>(std::lround(value)), 0, 2);
                break;
            case ParameterId::LfoDelay:
                lfoDelaySeconds_ = std::clamp(value, 0.0F, 10.0F);
                break;
            case ParameterId::DelaySyncBeats:
                delaySyncBeats_ = std::clamp(value, 0.0F, 4.0F);
                delay_.setSyncBeats(delaySyncBeats_);
                break;
            case ParameterId::TempoBpm:
                tempoBpm_ = std::clamp(value, 20.0F, 300.0F);
                delay_.setTempoBpm(tempoBpm_);
                break;
        }
    }

    void process(float* interleavedStereo, const int frameCount) noexcept {
        for (int frame = 0; frame < frameCount; ++frame) {
            const float sawMix = smoothedSawMix_.next();
            const float pulseMix = smoothedPulseMix_.next();
            const float triangleMix = smoothedTriangleMix_.next();
            const std::uint64_t lfoDelaySamples = static_cast<std::uint64_t>(
                lfoDelaySeconds_ * sampleRate_);
            const float lfo = lfoDelaySamplesElapsed_ >= lfoDelaySamples
                ? std::sin(2.0F * kPi * lfoPhase_) * lfoDepth_
                : 0.0F;
            lfoPhase_ += lfoRateHz_ / sampleRate_;
            if (lfoPhase_ >= 1.0F) lfoPhase_ -= 1.0F;
            if (lfoDelaySamplesElapsed_ < UINT64_MAX) ++lfoDelaySamplesElapsed_;
            const float lfoFilter = lfoDestination_ == 0 ? lfo : 0.0F;
            const float lfoPulseWidth = lfoDestination_ == 1 ? lfo * 0.40F : 0.0F;
            const float lfoDelay = lfoDestination_ == 2 ? lfo : 0.0F;
            const float pulseWidth = std::clamp(
                smoothedPulseWidth_.next() + lfoPulseWidth,
                0.05F,
                0.95F);
            float mono = 0.0F;
            for (auto& voice : voices_) {
                mono += voice.process(
                    sawMix,
                    pulseMix,
                    triangleMix,
                    pulseWidth,
                    cutoff_,
                    filterEnvelopeAmount_,
                    lfoFilter);
            }
            mono *= 0.18F;
            float left = 0.0F;
            float right = 0.0F;
            chorus_.process(mono, left, right);
            delay_.process(left, right, lfoDelay);
            reverb_.process(left, right);
            const float drive = drive_.next();
            if (drive > 0.0001F) {
                const float driveGain = 1.0F + drive * 7.0F;
                const float normalization = std::tanh(driveGain);
                left = std::tanh(left * driveGain) / normalization;
                right = std::tanh(right * driveGain) / normalization;
            }
            const float master = master_.next();
            const float preLimitedLeft = left * master;
            const float preLimitedRight = right * master;
#if defined(INTERVAL_NATIVE_TESTING)
            maximumPreLimiterMagnitudeForTesting_ = std::max({
                maximumPreLimiterMagnitudeForTesting_,
                std::abs(preLimitedLeft),
                std::abs(preLimitedRight),
            });
            samplesAboveLimiterKneeForTesting_ +=
                static_cast<std::size_t>(std::abs(preLimitedLeft) > kSoftLimitKnee) +
                static_cast<std::size_t>(std::abs(preLimitedRight) > kSoftLimitKnee);
            processedSamplesForTesting_ += 2U;
#endif
            interleavedStereo[frame * 2] = softLimit(preLimitedLeft);
            interleavedStereo[frame * 2 + 1] = softLimit(preLimitedRight);
        }
    }

    std::size_t activeVoices() const noexcept {
        return static_cast<std::size_t>(std::count_if(voices_.begin(), voices_.end(), [](const auto& voice) {
            return voice.isActive();
        }));
    }

#if defined(INTERVAL_NATIVE_TESTING)
    ParameterWorkCounters parameterWorkCountersForTesting() const noexcept {
        return parameterWorkCounters_;
    }

    void resetParameterWorkCountersForTesting() noexcept {
        parameterWorkCounters_ = {};
    }

    std::array<float, 3> normalizedOscillatorMixesForTesting() const noexcept {
        return {normalizedSawMix_, normalizedPulseMix_, normalizedTriangleMix_};
    }

    std::array<float, 3> smoothedOscillatorMixesForTesting() const noexcept {
        return {smoothedSawMix_.current(), smoothedPulseMix_.current(), smoothedTriangleMix_.current()};
    }

    float smoothedPulseWidthForTesting() const noexcept {
        return smoothedPulseWidth_.current();
    }

    OutputStats outputStatsForTesting() const noexcept {
        return {
            maximumPreLimiterMagnitudeForTesting_,
            samplesAboveLimiterKneeForTesting_,
            processedSamplesForTesting_,
        };
    }

    void resetOutputStatsForTesting() noexcept {
        maximumPreLimiterMagnitudeForTesting_ = 0.0F;
        samplesAboveLimiterKneeForTesting_ = 0U;
        processedSamplesForTesting_ = 0U;
    }

    RuntimeState runtimeStateForTesting() const noexcept {
        return {
            sampleRate_,
            chorus_.currentMixForTesting(),
            delay_.currentTimeSecondsForTesting(),
            delay_.currentFeedbackForTesting(),
            delay_.currentMixForTesting(),
            reverb_.currentMixForTesting(),
            master_.current(),
            envelopeCoefficients_,
            filterCoefficients_,
        };
    }
#endif

private:
    SynthVoice* findVoiceForStart() noexcept {
        for (auto& voice : voices_) if (!voice.isActive()) return &voice;
        SynthVoice* candidate = &voices_.front();
        for (auto& voice : voices_) {
            if (voice.isReleasing() && !candidate->isReleasing()) candidate = &voice;
            if (voice.isReleasing() == candidate->isReleasing() && voice.age() < candidate->age()) candidate = &voice;
        }
        return candidate;
    }

    void recomputeEnvelopeCoefficients() noexcept {
        envelopeCoefficients_ = Adsr::makeCoefficients(
            sampleRate_,
            attack_,
            decay_,
            sustain_,
            release_);
#if defined(INTERVAL_NATIVE_TESTING)
        ++parameterWorkCounters_.envelopeCoefficientComputations;
#endif
    }

    void recomputeFilterEnvelopeCoefficients() noexcept {
        filterEnvelopeCoefficients_ = Adsr::makeCoefficients(
            sampleRate_,
            filterAttack_,
            filterDecay_,
            filterSustain_,
            filterRelease_);
    }

    void recomputeAndApplyFilterEnvelopeCoefficients() noexcept {
        recomputeFilterEnvelopeCoefficients();
        for (auto& voice : voices_) {
            voice.setFilterEnvelopeCoefficients(filterEnvelopeCoefficients_);
        }
    }

    void recomputeFilterCoefficients() noexcept {
        filterCoefficients_ = StateVariableLowPass::makeCoefficients(
            sampleRate_,
            cutoff_,
            resonance_);
#if defined(INTERVAL_NATIVE_TESTING)
        ++parameterWorkCounters_.filterCoefficientComputations;
#endif
    }

    void recomputeAndApplyEnvelopeCoefficients() noexcept {
        recomputeEnvelopeCoefficients();
        for (auto& voice : voices_) {
            voice.setEnvelopeCoefficients(envelopeCoefficients_);
#if defined(INTERVAL_NATIVE_TESTING)
            ++parameterWorkCounters_.envelopeVoiceUpdates;
#endif
        }
    }

    void recomputeAndApplyFilterCoefficients([[maybe_unused]] const bool cutoffChange) noexcept {
        recomputeFilterCoefficients();
        for (auto& voice : voices_) {
            voice.setFilterCoefficients(filterCoefficients_);
#if defined(INTERVAL_NATIVE_TESTING)
            if (cutoffChange) {
                ++parameterWorkCounters_.cutoffVoiceUpdates;
            } else {
                ++parameterWorkCounters_.resonanceVoiceUpdates;
            }
#endif
        }
    }

    void recomputeNormalizedOscillatorMixes() noexcept {
        const float scale = 1.0F / std::max(1.0F, sawMix_ + pulseMix_ + triangleMix_);
        normalizedSawMix_ = sawMix_ * scale;
        normalizedPulseMix_ = pulseMix_ * scale;
        normalizedTriangleMix_ = triangleMix_ * scale;
        smoothedSawMix_.setTarget(normalizedSawMix_);
        smoothedPulseMix_.setTarget(normalizedPulseMix_);
        smoothedTriangleMix_.setTarget(normalizedTriangleMix_);
    }

    float sampleRate_{48000.0F};
    std::array<SynthVoice, kVoiceCount> voices_{};
    std::array<float, 128> noteFrequencies_{};
    Adsr::Coefficients envelopeCoefficients_{};
    Adsr::Coefficients filterEnvelopeCoefficients_{};
    StateVariableLowPass::Coefficients filterCoefficients_{};
    StereoChorus chorus_{};
    StereoDelay delay_{};
    StereoReverb reverb_{};
    SmoothedValue master_{};
    SmoothedValue drive_{};
    std::uint64_t ageCounter_{0};
    float sawMix_{0.65F};
    float pulseMix_{0.20F};
    float triangleMix_{0.15F};
    float normalizedSawMix_{0.65F};
    float normalizedPulseMix_{0.20F};
    float normalizedTriangleMix_{0.15F};
    SmoothedValue smoothedSawMix_{};
    SmoothedValue smoothedPulseMix_{};
    SmoothedValue smoothedTriangleMix_{};
    SmoothedValue smoothedPulseWidth_{};
    float pulseWidth_{0.50F};
    float attack_{0.005F};
    float decay_{0.18F};
    float sustain_{0.70F};
    float release_{0.35F};
    float cutoff_{3500.0F};
    float resonance_{0.15F};
    float chorusMix_{StereoChorus::kDefaultMix};
    float delayTimeSeconds_{StereoDelay::kDefaultTimeSeconds};
    float delayFeedback_{StereoDelay::kDefaultFeedback};
    float delayMix_{StereoDelay::kDefaultMix};
    float reverbMix_{StereoReverb::kDefaultMix};
    float masterGain_{0.35F};
    float filterAttack_{0.005F};
    float filterDecay_{0.18F};
    float filterSustain_{0.0F};
    float filterRelease_{0.35F};
    float filterEnvelopeAmount_{0.0F};
    float driveAmount_{0.0F};
    float lfoRateHz_{2.0F};
    float lfoDepth_{0.0F};
    int lfoDestination_{0};
    float lfoDelaySeconds_{0.0F};
    float delaySyncBeats_{0.0F};
    float tempoBpm_{120.0F};
    float lfoPhase_{0.0F};
    std::uint64_t lfoDelaySamplesElapsed_{0U};
#if defined(INTERVAL_NATIVE_TESTING)
    ParameterWorkCounters parameterWorkCounters_{};
    float maximumPreLimiterMagnitudeForTesting_{0.0F};
    std::size_t samplesAboveLimiterKneeForTesting_{0U};
    std::size_t processedSamplesForTesting_{0U};
#endif
};

}  // namespace intervaltablet::dsp
