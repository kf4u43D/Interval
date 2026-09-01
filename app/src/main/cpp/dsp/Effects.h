#pragma once

#include "DspPrimitives.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>
#include <vector>

namespace intervaltablet::dsp {

class FractionalDelayLine {
public:
    void prepare(const std::size_t samples) {
        buffer_.assign(std::max<std::size_t>(samples, 4U), 0.0F);
        writeIndex_ = 0;
        validSamples_ = 0;
    }

    void clear() noexcept {
        // Do not clear the storage here: panic() runs on the real-time thread and
        // the stereo delay alone owns roughly 200k samples at 48 kHz. Resetting
        // the validity window makes every pre-reset sample read as zero in O(1);
        // normal writes replace the stale storage incrementally.
        writeIndex_ = 0;
        validSamples_ = 0;
    }

    void write(const float value) noexcept {
        buffer_[writeIndex_] = value;
        ++writeIndex_;
        if (writeIndex_ == buffer_.size()) writeIndex_ = 0U;
        if (validSamples_ < buffer_.size()) ++validSamples_;
    }

    float read(const float delaySamples) const noexcept {
        const float bounded = std::clamp(
            finiteOr(delaySamples, 1.0F),
            1.0F,
            static_cast<float>(buffer_.size() - 2U));
        float readPosition = static_cast<float>(writeIndex_) - bounded;
        if (readPosition < 0.0F) readPosition += static_cast<float>(buffer_.size());
        const auto index0 = static_cast<std::size_t>(readPosition);
        const auto nextIndex = index0 + 1U;
        const auto index1 = nextIndex == buffer_.size() ? 0U : nextIndex;
        const float fraction = readPosition - static_cast<float>(static_cast<std::size_t>(readPosition));
        const float sample0 = validSample(index0);
        const float sample1 = validSample(index1);
        return sample0 + fraction * (sample1 - sample0);
    }

private:
    float validSample(const std::size_t index) const noexcept {
        const std::size_t distance = writeIndex_ >= index
            ? writeIndex_ - index
            : buffer_.size() - (index - writeIndex_);
        return distance != 0U && distance <= validSamples_ ? buffer_[index] : 0.0F;
    }

    std::vector<float> buffer_{};
    std::size_t writeIndex_{0};
    std::size_t validSamples_{0};
};

class StereoChorus {
public:
    static constexpr float kDefaultMix = 0.18F;

    void prepare(const float sampleRate, const float initialMix = kDefaultMix) {
        sampleRate_ = sanitizeSampleRate(sampleRate);
        line_.prepare(static_cast<std::size_t>(sampleRate_ * 0.06F) + 4U);
        const float radiansPerSample = kTwoPi * 0.23F / sampleRate_;
        rotationSin_ = std::sin(radiansPerSample);
        rotationCos_ = std::cos(radiansPerSample);
        baseDelaySamples_ = sampleRate_ * 0.018F;
        depthSamples_ = sampleRate_ * 0.006F;
        lfoSin_ = 0.0F;
        lfoCos_ = 1.0F;
        renormalizeCountdown_ = kRenormalizePeriod;
        mix_.prepare(sampleRate_);
        mix_.reset(std::clamp(finiteOr(initialMix, kDefaultMix), 0.0F, 1.0F));
    }

    void setMix(const float value) noexcept {
        if (std::isfinite(value)) mix_.setTarget(std::clamp(value, 0.0F, 1.0F));
    }
    void clear() noexcept { line_.clear(); }

    void process(const float input, float& left, float& right) noexcept {
        const float lfoLeft = lfoSin_;
        const float lfoRight = lfoCos_;
        const float wetLeft = line_.read(baseDelaySamples_ + depthSamples_ * lfoLeft);
        const float wetRight = line_.read(baseDelaySamples_ + depthSamples_ * lfoRight);
        line_.write(input);
        const float nextSin = lfoSin_ * rotationCos_ + lfoCos_ * rotationSin_;
        const float nextCos = lfoCos_ * rotationCos_ - lfoSin_ * rotationSin_;
        lfoSin_ = nextSin;
        lfoCos_ = nextCos;
        if (--renormalizeCountdown_ == 0U) {
            const float magnitude = std::sqrt(lfoSin_ * lfoSin_ + lfoCos_ * lfoCos_);
            if (magnitude > 0.0F) {
                lfoSin_ /= magnitude;
                lfoCos_ /= magnitude;
            }
            renormalizeCountdown_ = kRenormalizePeriod;
        }
        const float mix = mix_.next();
        left = input + mix * (wetLeft - input);
        right = input + mix * (wetRight - input);
    }

#if defined(INTERVAL_NATIVE_TESTING)
    float currentMixForTesting() const noexcept { return mix_.current(); }
#endif

private:
    static constexpr std::size_t kRenormalizePeriod = 1024U;
    float sampleRate_{48000.0F};
    float rotationSin_{0.0F};
    float rotationCos_{1.0F};
    float baseDelaySamples_{864.0F};
    float depthSamples_{288.0F};
    float lfoSin_{0.0F};
    float lfoCos_{1.0F};
    std::size_t renormalizeCountdown_{kRenormalizePeriod};
    FractionalDelayLine line_{};
    SmoothedValue mix_{};
};

class StereoDelay {
public:
    static constexpr float kDefaultTimeSeconds = 0.32F;
    static constexpr float kDefaultFeedback = 0.28F;
    static constexpr float kDefaultMix = 0.16F;

    void prepare(
        const float sampleRate,
        const float initialTimeSeconds = kDefaultTimeSeconds,
        const float initialFeedback = kDefaultFeedback,
        const float initialMix = kDefaultMix) {
        sampleRate_ = sanitizeSampleRate(sampleRate);
        const auto size = static_cast<std::size_t>(sampleRate_ * 2.1F) + 4U;
        left_.prepare(size);
        right_.prepare(size);
        delaySamples_.prepare(sampleRate_, 0.04F);
        const float boundedTime = std::clamp(
            finiteOr(initialTimeSeconds, kDefaultTimeSeconds),
            0.01F,
            2.0F);
        delaySamples_.reset(sampleRate_ * boundedTime);
        feedback_.prepare(sampleRate_);
        feedback_.reset(std::clamp(finiteOr(initialFeedback, kDefaultFeedback), 0.0F, 0.94F));
        mix_.prepare(sampleRate_);
        mix_.reset(std::clamp(finiteOr(initialMix, kDefaultMix), 0.0F, 1.0F));
    }

    void setTimeSeconds(const float value) noexcept {
        if (std::isfinite(value)) {
            delaySamples_.setTarget(std::clamp(value, 0.01F, 2.0F) * sampleRate_);
        }
    }
    void setFeedback(const float value) noexcept {
        if (std::isfinite(value)) feedback_.setTarget(std::clamp(value, 0.0F, 0.94F));
    }
    void setMix(const float value) noexcept {
        if (std::isfinite(value)) mix_.setTarget(std::clamp(value, 0.0F, 1.0F));
    }
    void clear() noexcept { left_.clear(); right_.clear(); }

    void process(float& left, float& right) noexcept {
        const float delay = delaySamples_.next();
        const float delayedLeft = left_.read(delay);
        const float delayedRight = right_.read(delay * 1.011F);
        const float feedback = feedback_.next();
        left_.write(left + delayedRight * feedback);
        right_.write(right + delayedLeft * feedback);
        const float mix = mix_.next();
        left += mix * (delayedLeft - left);
        right += mix * (delayedRight - right);
    }

#if defined(INTERVAL_NATIVE_TESTING)
    float currentTimeSecondsForTesting() const noexcept {
        return delaySamples_.current() / sampleRate_;
    }
    float currentFeedbackForTesting() const noexcept { return feedback_.current(); }
    float currentMixForTesting() const noexcept { return mix_.current(); }
#endif

private:
    float sampleRate_{48000.0F};
    FractionalDelayLine left_{};
    FractionalDelayLine right_{};
    SmoothedValue delaySamples_{};
    SmoothedValue feedback_{};
    SmoothedValue mix_{};
};

class CombFilter {
public:
    void prepare(const std::size_t size) {
        buffer_.assign(std::max<std::size_t>(size, 2U), 0.0F);
        index_ = 0;
        validSamples_ = 0;
        filterStore_ = 0.0F;
    }

    float process(const float input, const float feedback, const float damping) noexcept {
        const float output = validSamples_ == buffer_.size() ? buffer_[index_] : 0.0F;
        filterStore_ = output * (1.0F - damping) + filterStore_ * damping;
        buffer_[index_] = input + filterStore_ * feedback;
        ++index_;
        if (index_ == buffer_.size()) index_ = 0U;
        if (validSamples_ < buffer_.size()) ++validSamples_;
        return output;
    }

    void clear() noexcept {
        filterStore_ = 0.0F;
        index_ = 0;
        validSamples_ = 0;
    }

private:
    std::vector<float> buffer_{};
    std::size_t index_{0};
    std::size_t validSamples_{0};
    float filterStore_{0.0F};
};

class AllPassFilter {
public:
    void prepare(const std::size_t size) {
        buffer_.assign(std::max<std::size_t>(size, 2U), 0.0F);
        index_ = 0;
        validSamples_ = 0;
    }

    float process(const float input) noexcept {
        const float buffered = validSamples_ == buffer_.size() ? buffer_[index_] : 0.0F;
        constexpr float feedback = 0.5F;
        const float output = buffered - feedback * input;
        // Canonical Schroeder all-pass form. Feeding back the output (rather
        // than the delayed sample) makes the numerator the time-reverse of
        // the denominator and therefore preserves magnitude at every
        // frequency while changing phase only.
        buffer_[index_] = input + feedback * output;
        ++index_;
        if (index_ == buffer_.size()) index_ = 0U;
        if (validSamples_ < buffer_.size()) ++validSamples_;
        return output;
    }

    void clear() noexcept {
        index_ = 0;
        validSamples_ = 0;
    }

private:
    std::vector<float> buffer_{};
    std::size_t index_{0};
    std::size_t validSamples_{0};
};

class StereoReverb {
public:
    static constexpr float kDefaultMix = 0.20F;
    static constexpr float kCombFeedback = 0.79F;
    static constexpr float kCombDamping = 0.24F;

    void prepare(const float sampleRate, const float initialMix = kDefaultMix) {
        sampleRate_ = sanitizeSampleRate(sampleRate);
        constexpr std::array<int, 4> combTuning{1116, 1188, 1277, 1356};
        constexpr std::array<int, 2> allPassTuning{556, 441};
        const float ratio = sampleRate_ / 44100.0F;
        for (std::size_t i = 0; i < combTuning.size(); ++i) {
            leftCombs_[i].prepare(static_cast<std::size_t>(static_cast<float>(combTuning[i]) * ratio));
            rightCombs_[i].prepare(static_cast<std::size_t>(static_cast<float>(combTuning[i] + 23) * ratio));
        }
        for (std::size_t i = 0; i < allPassTuning.size(); ++i) {
            leftAllPass_[i].prepare(static_cast<std::size_t>(static_cast<float>(allPassTuning[i]) * ratio));
            rightAllPass_[i].prepare(static_cast<std::size_t>(static_cast<float>(allPassTuning[i] + 23) * ratio));
        }
        mix_.prepare(sampleRate_);
        mix_.reset(std::clamp(finiteOr(initialMix, kDefaultMix), 0.0F, 1.0F));
    }

    void setMix(const float value) noexcept {
        if (std::isfinite(value)) mix_.setTarget(std::clamp(value, 0.0F, 1.0F));
    }

    void clear() noexcept {
        for (auto& comb : leftCombs_) comb.clear();
        for (auto& comb : rightCombs_) comb.clear();
        for (auto& allPass : leftAllPass_) allPass.clear();
        for (auto& allPass : rightAllPass_) allPass.clear();
    }

    void process(float& left, float& right) noexcept {
        constexpr float combAverageGain = 1.0F / static_cast<float>(kCombCount);
        const float mono = (left + right) * 0.5F;
        // A parallel feedback bank otherwise multiplies coherent low-frequency
        // content by combCount / (1 - feedback). Compensating the send by the
        // loop loss and averaging the combs keeps the settled wet path near
        // unity without touching the decay time.
        const float input = mono * (1.0F - kCombFeedback);
        float wetLeft = 0.0F;
        float wetRight = 0.0F;
        for (auto& comb : leftCombs_) wetLeft += comb.process(input, kCombFeedback, kCombDamping);
        for (auto& comb : rightCombs_) wetRight += comb.process(input, kCombFeedback, kCombDamping);
        wetLeft *= combAverageGain;
        wetRight *= combAverageGain;
        for (auto& allPass : leftAllPass_) wetLeft = allPass.process(wetLeft);
        for (auto& allPass : rightAllPass_) wetRight = allPass.process(wetRight);
        const float mix = mix_.next();
        left += mix * (wetLeft - left);
        right += mix * (wetRight - right);
    }

#if defined(INTERVAL_NATIVE_TESTING)
    float currentMixForTesting() const noexcept { return mix_.current(); }
#endif

private:
    static constexpr std::size_t kCombCount = 4U;
    float sampleRate_{48000.0F};
    std::array<CombFilter, kCombCount> leftCombs_{};
    std::array<CombFilter, kCombCount> rightCombs_{};
    std::array<AllPassFilter, 2> leftAllPass_{};
    std::array<AllPassFilter, 2> rightAllPass_{};
    SmoothedValue mix_{};
};

}  // namespace intervaltablet::dsp
