#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>

namespace intervaltablet::dsp {

inline constexpr float kPi = 3.14159265358979323846F;
inline constexpr float kTwoPi = 2.0F * kPi;
inline constexpr float kDefaultSampleRate = 48000.0F;
inline constexpr float kMaximumSampleRate = 768000.0F;
inline constexpr float kSoftLimitKnee = 0.75F;

inline float finiteOr(const float value, const float fallback) noexcept {
    return std::isfinite(value) ? value : fallback;
}

inline float sanitizeSampleRate(const float sampleRate) noexcept {
    return std::clamp(finiteOr(sampleRate, kDefaultSampleRate), 1.0F, kMaximumSampleRate);
}

inline float midiToHz(const int note) noexcept {
    return 440.0F * std::exp2((static_cast<float>(note) - 69.0F) / 12.0F);
}

inline float clampUnit(const float value) noexcept {
    return std::clamp(finiteOr(value, 0.0F), 0.0F, 1.0F);
}

inline float softLimit(const float value) noexcept {
    const float finiteValue = finiteOr(value, 0.0F);
    const float magnitude = std::abs(finiteValue);
    if (magnitude <= kSoftLimitKnee) return finiteValue;

    // Preserve an exactly linear nominal range, then enter a continuous
    // rational knee whose slope starts at one and tends asymptotically to
    // full scale. Unlike softsign, ordinary signals receive no waveshaping.
    constexpr float headroom = 1.0F - kSoftLimitKnee;
    const float excess = magnitude - kSoftLimitKnee;
    const float limited = kSoftLimitKnee + headroom * excess / (excess + headroom);
    return std::copysign(std::min(limited, 1.0F), finiteValue);
}

class SmoothedValue {
public:
    void prepare(const float sampleRate, const float timeSeconds = 0.02F) noexcept {
        sampleRate_ = sanitizeSampleRate(sampleRate);
        setTime(timeSeconds);
        current_ = target_;
    }

    void setTime(const float seconds) noexcept {
        const float finiteSeconds = finiteOr(seconds, 0.02F);
        coefficient_ = std::exp(-1.0F / (std::max(finiteSeconds, 0.0001F) * sampleRate_));
    }

    void reset(const float value) noexcept {
        current_ = target_ = finiteOr(value, 0.0F);
    }

    void setTarget(const float value) noexcept {
        if (std::isfinite(value)) target_ = value;
    }

    float next() noexcept {
        current_ = target_ + coefficient_ * (current_ - target_);
        if (std::abs(current_ - target_) <= 1.0e-4F) current_ = target_;
        return current_;
    }

    float current() const noexcept { return current_; }

private:
    float sampleRate_{48000.0F};
    float coefficient_{0.99F};
    float current_{0.0F};
    float target_{0.0F};
};

class Adsr {
public:
    enum class Stage : std::uint8_t { Idle, Attack, Decay, Sustain, Release };

    static constexpr float kAttackTargetError = 0.0005F;
    static constexpr float kDecayTargetDistance = 0.0005F;
    static constexpr float kReleaseEndLevel = 0.00005F;

    struct Coefficients {
        float attackMultiplier{0.99F};
        float decayMultiplier{0.99F};
        float sustainLevel{0.70F};
        float releaseMultiplier{0.99F};
        std::uint32_t attackSamples{240U};
        std::uint32_t decaySamples{8640U};
        std::uint32_t releaseSamples{16800U};
    };

    static Coefficients makeCoefficients(
        const float sampleRate,
        const float attackSeconds,
        const float decaySeconds,
        const float sustain,
        const float releaseSeconds) noexcept {
        const float boundedSampleRate = sanitizeSampleRate(sampleRate);
        const float boundedAttack = std::clamp(finiteOr(attackSeconds, 0.005F), 0.0005F, 10.0F);
        const float boundedDecay = std::clamp(finiteOr(decaySeconds, 0.18F), 0.001F, 20.0F);
        const float boundedSustain = std::clamp(finiteOr(sustain, 0.70F), 0.0F, 1.0F);
        const float boundedRelease = std::clamp(finiteOr(releaseSeconds, 0.35F), 0.001F, 30.0F);
        const float decayDistance = 1.0F - boundedSustain;
        const std::uint32_t attackSamples = durationSamples(boundedAttack, boundedSampleRate);
        const std::uint32_t decaySamples = decayDistance <= kDecayTargetDistance
            ? 1U
            : durationSamples(boundedDecay, boundedSampleRate);
        const std::uint32_t releaseSamples = durationSamples(boundedRelease, boundedSampleRate);
        return {
            coefficientToRatio(attackSamples, kAttackTargetError),
            coefficientToRatio(
                decaySamples,
                decayDistance <= kDecayTargetDistance
                    ? 1.0F
                    : kDecayTargetDistance / decayDistance),
            boundedSustain,
            coefficientToRatio(releaseSamples, kReleaseEndLevel),
            attackSamples,
            decaySamples,
            releaseSamples,
        };
    }

    void prepare(const float sampleRate) noexcept {
        sampleRate_ = sanitizeSampleRate(sampleRate);
        sustainSmoother_.prepare(sampleRate_);
        updateCoefficients();
        reset();
    }

    void prepare(const float sampleRate, const Coefficients& coefficients) noexcept {
        sampleRate_ = sanitizeSampleRate(sampleRate);
        sustainSmoother_.prepare(sampleRate_);
        setCoefficients(coefficients);
        reset();
    }

    void setParameters(
        const float attackSeconds,
        const float decaySeconds,
        const float sustain,
        const float releaseSeconds) noexcept {
        if (std::isfinite(attackSeconds)) attackSeconds_ = std::clamp(attackSeconds, 0.0005F, 10.0F);
        if (std::isfinite(decaySeconds)) decaySeconds_ = std::clamp(decaySeconds, 0.001F, 20.0F);
        if (std::isfinite(sustain)) sustain_ = std::clamp(sustain, 0.0F, 1.0F);
        if (std::isfinite(releaseSeconds)) releaseSeconds_ = std::clamp(releaseSeconds, 0.001F, 30.0F);
        updateCoefficients();
    }

    void setCoefficients(const Coefficients& coefficients) noexcept {
        attackCoefficient_ = clampUnit(coefficients.attackMultiplier);
        decayCoefficient_ = clampUnit(coefficients.decayMultiplier);
        sustain_ = clampUnit(coefficients.sustainLevel);
        if (stage_ == Stage::Idle) {
            sustainSmoother_.reset(sustain_);
        } else {
            sustainSmoother_.setTarget(sustain_);
        }
        releaseCoefficient_ = clampUnit(coefficients.releaseMultiplier);
        attackSamples_ = std::max(coefficients.attackSamples, std::uint32_t{1U});
        decaySamples_ = std::max(coefficients.decaySamples, std::uint32_t{1U});
        releaseSamples_ = std::max(coefficients.releaseSamples, std::uint32_t{1U});
    }

    void noteOn() noexcept {
        stageSample_ = 0U;
        stage_ = Stage::Attack;
    }
    void noteOff() noexcept {
        if (stage_ != Stage::Idle) {
            stageSample_ = 0U;
            stage_ = Stage::Release;
        }
    }

    void reset() noexcept {
        value_ = 0.0F;
        sustainSmoother_.reset(sustain_);
        stageSample_ = 0U;
        stage_ = Stage::Idle;
    }

    float process() noexcept {
        const float smoothedSustain = sustainSmoother_.next();
        switch (stage_) {
            case Stage::Idle:
                value_ = 0.0F;
                break;
            case Stage::Attack:
                value_ = 1.0F + attackCoefficient_ * (value_ - 1.0F);
                ++stageSample_;
                if (stageSample_ >= attackSamples_) {
                    value_ = 1.0F;
                    stageSample_ = 0U;
                    stage_ = Stage::Decay;
                }
                break;
            case Stage::Decay:
                value_ = smoothedSustain + decayCoefficient_ * (value_ - smoothedSustain);
                ++stageSample_;
                if (stageSample_ >= decaySamples_) {
                    value_ = smoothedSustain;
                    stageSample_ = 0U;
                    stage_ = Stage::Sustain;
                }
                break;
            case Stage::Sustain:
                value_ = smoothedSustain;
                break;
            case Stage::Release:
                value_ = releaseCoefficient_ * value_;
                ++stageSample_;
                if (stageSample_ >= releaseSamples_) reset();
                break;
        }
        return value_;
    }

    bool isActive() const noexcept { return stage_ != Stage::Idle; }
    bool isReleasing() const noexcept { return stage_ == Stage::Release; }
    Stage stage() const noexcept { return stage_; }

private:
    static std::uint32_t durationSamples(const float seconds, const float sampleRate) noexcept {
        return static_cast<std::uint32_t>(std::max(
            std::ceil(static_cast<double>(seconds) * static_cast<double>(sampleRate)),
            1.0));
    }

    static float coefficientToRatio(const std::uint32_t frameCount, const float endRatio) noexcept {
        const float boundedRatio = std::clamp(endRatio, kReleaseEndLevel, 1.0F);
        return std::exp(std::log(boundedRatio) / static_cast<float>(frameCount));
    }

    void updateCoefficients() noexcept {
        setCoefficients(makeCoefficients(
            sampleRate_,
            attackSeconds_,
            decaySeconds_,
            sustain_,
            releaseSeconds_));
    }

    float sampleRate_{48000.0F};
    float attackSeconds_{0.005F};
    float decaySeconds_{0.18F};
    float sustain_{0.70F};
    float releaseSeconds_{0.35F};
    SmoothedValue sustainSmoother_{};
    float attackCoefficient_{0.99F};
    float decayCoefficient_{0.99F};
    float releaseCoefficient_{0.99F};
    float value_{0.0F};
    std::uint32_t attackSamples_{240U};
    std::uint32_t decaySamples_{8640U};
    std::uint32_t releaseSamples_{16800U};
    std::uint32_t stageSample_{0U};
    Stage stage_{Stage::Idle};
};

class Oscillator {
public:
    void prepare(const float sampleRate) noexcept {
        sampleRate_ = sanitizeSampleRate(sampleRate);
        phase_ = 0.0F;
    }

    void setFrequency(const float frequency) noexcept {
        if (std::isfinite(frequency)) {
            phaseIncrement_ = std::clamp(frequency / sampleRate_, 0.0F, 0.45F);
        }
    }

    void reset() noexcept { phase_ = 0.0F; }

    float process(
        const float sawMix,
        const float pulseMix,
        const float triangleMix,
        const float pulseWidth) noexcept {
        const float saw = 2.0F * phase_ - 1.0F - polyBlep(phase_, phaseIncrement_);
        float pulse = phase_ < pulseWidth ? 1.0F : -1.0F;
        pulse += polyBlep(phase_, phaseIncrement_);
        float shifted = phase_ - pulseWidth;
        if (shifted < 0.0F) shifted += 1.0F;
        pulse -= polyBlep(shifted, phaseIncrement_);
        const float triangle = 1.0F - 4.0F * std::abs(phase_ - 0.5F);

        phase_ += phaseIncrement_;
        if (phase_ >= 1.0F) phase_ -= 1.0F;
        return saw * sawMix + pulse * pulseMix + triangle * triangleMix;
    }

private:
    static float polyBlep(const float phase, const float dt) noexcept {
        if (dt <= 0.0F) return 0.0F;
        if (phase < dt) {
            const float t = phase / dt;
            return t + t - t * t - 1.0F;
        }
        if (phase > 1.0F - dt) {
            const float t = (phase - 1.0F) / dt;
            return t * t + t + t + 1.0F;
        }
        return 0.0F;
    }

    float sampleRate_{48000.0F};
    float phase_{0.0F};
    float phaseIncrement_{440.0F / 48000.0F};
};

class StateVariableLowPass {
public:
    struct Coefficients {
        float a1{1.0F};
        float a2{0.0F};
        float a3{0.0F};
    };

    static Coefficients makeCoefficients(
        const float sampleRate,
        const float cutoffHz,
        const float resonance) noexcept {
        const float boundedSampleRate = sanitizeSampleRate(sampleRate);
        const float maximumCutoff = std::max(20.0F, boundedSampleRate * 0.45F);
        const float boundedCutoff = std::clamp(finiteOr(cutoffHz, 3500.0F), 20.0F, maximumCutoff);
        const float boundedResonance = std::clamp(finiteOr(resonance, 0.15F), 0.0F, 1.0F);
        const float g = std::tan(kPi * boundedCutoff / boundedSampleRate);
        const float k = 2.0F - 1.9F * boundedResonance;
        const float a1 = 1.0F / (1.0F + g * (g + k));
        const float a2 = g * a1;
        return {a1, a2, g * a2};
    }

    void prepare(const float sampleRate) noexcept {
        sampleRate_ = sanitizeSampleRate(sampleRate);
        reset();
        prepareCoefficientSmoothing(makeCoefficients(sampleRate_, cutoffHz_, resonance_));
    }

    void prepare(const float sampleRate, const Coefficients& coefficients) noexcept {
        sampleRate_ = sanitizeSampleRate(sampleRate);
        reset();
        prepareCoefficientSmoothing(coefficients);
    }

    void setCutoff(const float cutoffHz) noexcept {
        if (std::isfinite(cutoffHz)) {
            cutoffHz_ = std::clamp(cutoffHz, 20.0F, std::max(20.0F, sampleRate_ * 0.45F));
            update();
        }
    }

    void setResonance(const float resonance) noexcept {
        if (std::isfinite(resonance)) {
            resonance_ = std::clamp(resonance, 0.0F, 1.0F);
            update();
        }
    }

    void setCoefficients(const Coefficients& coefficients) noexcept {
        if (std::isfinite(coefficients.a1) &&
            std::isfinite(coefficients.a2) &&
            std::isfinite(coefficients.a3)) {
            a1_.setTarget(coefficients.a1);
            a2_.setTarget(coefficients.a2);
            a3_.setTarget(coefficients.a3);
        } else {
            a1_.setTarget(1.0F);
            a2_.setTarget(0.0F);
            a3_.setTarget(0.0F);
        }
    }

    void reset() noexcept { ic1eq_ = ic2eq_ = 0.0F; }

    float process(const float input) noexcept {
        const float a1 = a1_.next();
        const float a2 = a2_.next();
        const float a3 = a3_.next();
        const float v3 = input - ic2eq_;
        const float v1 = a1 * ic1eq_ + a2 * v3;
        const float v2 = ic2eq_ + a2 * ic1eq_ + a3 * v3;
        ic1eq_ = 2.0F * v1 - ic1eq_;
        ic2eq_ = 2.0F * v2 - ic2eq_;
        return v2;
    }

#if defined(INTERVAL_NATIVE_TESTING)
    Coefficients smoothedCoefficientsForTesting() const noexcept {
        return {a1_.current(), a2_.current(), a3_.current()};
    }
#endif

private:
    void prepareCoefficientSmoothing(const Coefficients& coefficients) noexcept {
        const bool valid = std::isfinite(coefficients.a1) &&
            std::isfinite(coefficients.a2) &&
            std::isfinite(coefficients.a3);
        const Coefficients safe = valid ? coefficients : Coefficients{};
        a1_.prepare(sampleRate_);
        a2_.prepare(sampleRate_);
        a3_.prepare(sampleRate_);
        a1_.reset(safe.a1);
        a2_.reset(safe.a2);
        a3_.reset(safe.a3);
    }

    void update() noexcept {
        setCoefficients(makeCoefficients(sampleRate_, cutoffHz_, resonance_));
    }

    float sampleRate_{48000.0F};
    float cutoffHz_{3500.0F};
    float resonance_{0.15F};
    SmoothedValue a1_{};
    SmoothedValue a2_{};
    SmoothedValue a3_{};
    float ic1eq_{0.0F};
    float ic2eq_{0.0F};
};

}  // namespace intervaltablet::dsp
