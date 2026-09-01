#include "AudioEngine.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <limits>

namespace intervaltablet {
namespace {

constexpr std::array<std::chrono::milliseconds, 4> kRecoveryBackoff{
    std::chrono::milliseconds{100},
    std::chrono::milliseconds{250},
    std::chrono::milliseconds{500},
    std::chrono::milliseconds{1000},
};

int resultCode(const oboe::Result result) noexcept {
    return static_cast<int>(result);
}

}  // namespace

AudioEngine::AudioEngine()
    : recoveryThread_(&AudioEngine::recoveryLoop, this) {}

std::shared_ptr<AudioEngine> AudioEngine::create() {
    return std::shared_ptr<AudioEngine>(new AudioEngine());
}

AudioEngine::~AudioEngine() {
    shutdown();
}

void AudioEngine::shutdown() noexcept {
    {
        std::unique_lock shutdownLock(shutdownMutex_);
        if (shutdownComplete_) return;
        if (shutdownStarted_) {
            shutdownCondition_.wait(shutdownLock, [this] { return shutdownComplete_; });
            return;
        }
        shutdownStarted_ = true;
    }

    shuttingDown_.store(true, std::memory_order_release);
    requestStopIntent();
    recoveryPending_.store(false, std::memory_order_release);
    {
        std::scoped_lock recoveryLock(recoveryMutex_);
        recoveryShutdown_ = true;
        recoveryRequested_ = false;
    }
    recoveryCondition_.notify_all();

    stop();
    if (recoveryThread_.joinable()) recoveryThread_.join();

    {
        std::scoped_lock streamLock(streamMutex_);
        stream_.reset();
        retiredStream_.reset();
        failedStream_.store(nullptr, std::memory_order_release);
        failedGeneration_.store(0, std::memory_order_release);
        streamClosedByError_.store(false, std::memory_order_release);
        errorCloseInProgress_.store(false, std::memory_order_release);
    }

    {
        std::scoped_lock shutdownLock(shutdownMutex_);
        shutdownComplete_ = true;
    }
    shutdownCondition_.notify_all();
}

bool AudioEngine::start() {
    {
        std::scoped_lock intentLock(lifecycleIntentMutex_);
        if (shuttingDown_.load(std::memory_order_acquire)) return false;
        desiredRunning_.store(true, std::memory_order_release);
    }
    std::scoped_lock lock(streamMutex_);
    if (shuttingDown_.load(std::memory_order_acquire) ||
        !desiredRunning_.load(std::memory_order_acquire)) {
        desiredRunning_.store(false, std::memory_order_release);
        return false;
    }
    if (running_.load(std::memory_order_acquire)) return true;

    // Oboe owns the close operation between its two error callbacks. The recovery
    // worker will reopen as soon as onErrorAfterClose reports that it is safe.
    if (errorCloseInProgress_.load(std::memory_order_acquire)) return false;

    if (stream_) {
        const bool alreadyClosed = streamClosedByError_.exchange(false, std::memory_order_acq_rel);
        discardStreamLocked(alreadyClosed);
    }
    discardPendingEventsLocked();
    return startLocked();
}

void AudioEngine::stop() {
    requestStopIntent();
    recoveryPending_.store(false, std::memory_order_release);
    recoveryCondition_.notify_all();

    std::unique_lock streamLock(streamMutex_);
    if (errorCloseInProgress_.load(std::memory_order_acquire)) {
        streamLock.unlock();
        std::unique_lock recoveryLock(recoveryMutex_);
        recoveryCondition_.wait(recoveryLock, [this] {
            return !errorCloseInProgress_.load(std::memory_order_acquire);
        });
        recoveryLock.unlock();
        streamLock.lock();
    }

    if (stream_) {
        const bool alreadyClosed = streamClosedByError_.exchange(false, std::memory_order_acq_rel);
        discardStreamLocked(alreadyClosed);
    }
    discardPendingEventsLocked();
}

bool AudioEngine::enqueue(const AudioEvent& event) noexcept {
    if (shuttingDown_.load(std::memory_order_acquire) ||
        !running_.load(std::memory_order_acquire)) {
        droppedEvents_.fetch_add(1, std::memory_order_relaxed);
        emergencyPanic_.store(true, std::memory_order_release);
        return false;
    }
    AudioEvent taggedEvent = event;
    taggedEvent.streamGeneration = activeGeneration_.load(std::memory_order_acquire);
#if defined(INTERVAL_NATIVE_TESTING)
    if (enqueueInterleaveHook_ != nullptr) {
        auto* hook = enqueueInterleaveHook_;
        enqueueInterleaveHook_ = nullptr;
        hook(*this);
    }
#endif
    if (!queue_.push(taggedEvent)) {
        droppedEvents_.fetch_add(1, std::memory_order_relaxed);
        emergencyPanic_.store(true, std::memory_order_release);
        return false;
    }
    updateMaximumQueueDepth(queue_.sizeApprox());
    if (shuttingDown_.load(std::memory_order_acquire) ||
        !running_.load(std::memory_order_acquire) ||
        taggedEvent.streamGeneration != activeGeneration_.load(std::memory_order_acquire)) {
        droppedEvents_.fetch_add(1, std::memory_order_relaxed);
        emergencyPanic_.store(true, std::memory_order_release);
        return false;
    }
    return true;
}

AudioEngine::Diagnostics AudioEngine::diagnostics() const {
    std::scoped_lock lock(streamMutex_);
    int xruns = 0;
    const auto localStream = stream_;
    if (localStream && running_.load(std::memory_order_acquire) &&
        localStream.get() == activeStream_.load(std::memory_order_acquire)) {
        const auto result = localStream->getXRunCount();
        if (result) xruns = result.value();
    }
    const auto depth = std::min(
        queue_.sizeApprox(),
        static_cast<std::size_t>(std::numeric_limits<int>::max()));
    return {
        sampleRate_.load(std::memory_order_relaxed),
        framesPerBurst_.load(std::memory_order_relaxed),
        xruns,
        droppedEvents_.load(std::memory_order_relaxed),
        static_cast<int>(depth),
        maximumQueueDepth_.load(std::memory_order_relaxed),
        running_.load(std::memory_order_relaxed) ? 1 : 0,
        restartCount_.load(std::memory_order_relaxed),
        lastError_.load(std::memory_order_relaxed),
        bufferSizeFrames_.load(std::memory_order_relaxed),
        recoveryPending_.load(std::memory_order_relaxed) ? 1 : 0,
    };
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream* stream,
    void* audioData,
    const std::int32_t numFrames) {
    auto* output = static_cast<float*>(audioData);
    if (output == nullptr || numFrames <= 0) return oboe::DataCallbackResult::Stop;

    if (!running_.load(std::memory_order_acquire) ||
        stream != activeStream_.load(std::memory_order_acquire)) {
        writeSilence(output, numFrames);
        return oboe::DataCallbackResult::Stop;
    }

    if (emergencyPanic_.exchange(false, std::memory_order_acq_rel)) {
        queue_.clearFromConsumer();
        synth_.panic();
    }

    const auto callbackGeneration = activeGeneration_.load(std::memory_order_acquire);
    AudioEvent event{};
    std::size_t handledEvents = 0;
    bool staleGenerationNeutralized = false;
    while (handledEvents < kMaxEventsPerCallback && queue_.pop(event)) {
        ++handledEvents;
        if (event.streamGeneration != callbackGeneration) {
            if (!staleGenerationNeutralized) {
                synth_.panic();
                staleGenerationNeutralized = true;
            }
            continue;
        }
        handleEvent(event);
    }
    synth_.process(output, numFrames);
    return running_.load(std::memory_order_acquire) &&
            stream == activeStream_.load(std::memory_order_acquire)
        ? oboe::DataCallbackResult::Continue
        : oboe::DataCallbackResult::Stop;
}

void AudioEngine::onErrorBeforeClose(oboe::AudioStream* stream, const oboe::Result error) {
    std::scoped_lock lock(streamMutex_);
    if (!stream_ || stream_.get() != stream ||
        stream != activeStream_.load(std::memory_order_acquire)) {
        return;
    }
    const auto generation = activeGeneration_.load(std::memory_order_acquire);
    failedStream_.store(stream, std::memory_order_release);
    failedGeneration_.store(generation, std::memory_order_release);
    errorCloseInProgress_.store(true, std::memory_order_release);
    streamClosedByError_.store(false, std::memory_order_release);
    running_.store(false, std::memory_order_release);
    recoveryPending_.store(desiredRunning_.load(std::memory_order_acquire), std::memory_order_release);
    lastError_.store(resultCode(error), std::memory_order_relaxed);
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* stream, const oboe::Result error) {
    std::uint64_t generation = 0;
    {
        std::scoped_lock lock(streamMutex_);
        if (stream != failedStream_.load(std::memory_order_acquire)) return;
        generation = failedGeneration_.load(std::memory_order_acquire);
        streamClosedByError_.store(true, std::memory_order_release);
        errorCloseInProgress_.store(false, std::memory_order_release);
        lastError_.store(resultCode(error), std::memory_order_relaxed);
    }
    recoveryCondition_.notify_all();
    if (!shuttingDown_.load(std::memory_order_acquire) &&
        desiredRunning_.load(std::memory_order_acquire)) {
        scheduleRecovery(generation);
    } else {
        recoveryPending_.store(false, std::memory_order_release);
    }
}

bool AudioEngine::startLocked() {
    if (shuttingDown_.load(std::memory_order_acquire) ||
        !desiredRunning_.load(std::memory_order_acquire)) return false;
    if (openAndStartLocked(oboe::SharingMode::Exclusive)) return true;
    if (!desiredRunning_.load(std::memory_order_acquire)) return false;
    return openAndStartLocked(oboe::SharingMode::Shared);
}

bool AudioEngine::openAndStartLocked(const oboe::SharingMode sharingMode) {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(sharingMode);
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setChannelCount(2);
    const auto self = shared_from_this();
    builder.setDataCallback(std::static_pointer_cast<oboe::AudioStreamDataCallback>(self));
    builder.setErrorCallback(std::static_pointer_cast<oboe::AudioStreamErrorCallback>(self));

    std::shared_ptr<oboe::AudioStream> candidate;
    const auto openResult = builder.openStream(candidate);
    if (openResult != oboe::Result::OK || !candidate) {
        lastError_.store(resultCode(openResult), std::memory_order_relaxed);
        if (candidate) candidate->close();
        return false;
    }

    if (shuttingDown_.load(std::memory_order_acquire) ||
        !desiredRunning_.load(std::memory_order_acquire)) {
        (void)candidate->close();
        return false;
    }

    const int negotiatedSampleRate = candidate->getSampleRate();
    const int negotiatedFramesPerBurst = candidate->getFramesPerBurst();
    if (negotiatedSampleRate <= 0 || negotiatedFramesPerBurst <= 0) {
        lastError_.store(resultCode(oboe::Result::ErrorInternal), std::memory_order_relaxed);
        candidate->close();
        return false;
    }

    stream_ = std::move(candidate);
    const auto generation = generationCounter_.fetch_add(1, std::memory_order_acq_rel) + 1U;
    activeGeneration_.store(generation, std::memory_order_release);
    activeStream_.store(stream_.get(), std::memory_order_release);
    streamClosedByError_.store(false, std::memory_order_release);
    errorCloseInProgress_.store(false, std::memory_order_release);

    try {
        synth_.prepare(static_cast<float>(negotiatedSampleRate));
    } catch (...) {
        lastError_.store(resultCode(oboe::Result::ErrorInternal), std::memory_order_relaxed);
        discardStreamLocked(false);
        discardPendingEventsLocked();
        return false;
    }

    sampleRate_.store(negotiatedSampleRate, std::memory_order_relaxed);
    framesPerBurst_.store(negotiatedFramesPerBurst, std::memory_order_relaxed);
    const int targetBuffer = negotiatedFramesPerBurst <= std::numeric_limits<int>::max() / 2
        ? negotiatedFramesPerBurst * 2
        : negotiatedFramesPerBurst;
    const auto bufferResult = stream_->setBufferSizeInFrames(targetBuffer);
    bufferSizeFrames_.store(
        bufferResult ? bufferResult.value() : stream_->getBufferSizeInFrames(),
        std::memory_order_relaxed);

    // requestStart may make the first callback runnable before it returns. Serialize
    // the final intent check with Stop, then arm the callback before starting it.
    oboe::Result startResult = oboe::Result::ErrorInternal;
    bool startRequested = false;
    {
        std::scoped_lock intentLock(lifecycleIntentMutex_);
        if (!shuttingDown_.load(std::memory_order_acquire) &&
            desiredRunning_.load(std::memory_order_acquire)) {
            running_.store(true, std::memory_order_release);
            startRequested = true;
            startResult = stream_->requestStart();
        }
    }
    if (!startRequested) {
        discardStreamLocked(false);
        discardPendingEventsLocked();
        return false;
    }
    if (startResult == oboe::Result::OK) return true;

    running_.store(false, std::memory_order_release);
    lastError_.store(resultCode(startResult), std::memory_order_relaxed);
    discardStreamLocked(false);
    discardPendingEventsLocked();
    return false;
}

void AudioEngine::requestStopIntent() noexcept {
    std::scoped_lock intentLock(lifecycleIntentMutex_);
    desiredRunning_.store(false, std::memory_order_release);
    running_.store(false, std::memory_order_release);
}

void AudioEngine::discardStreamLocked(const bool alreadyClosed) noexcept {
    running_.store(false, std::memory_order_release);
    activeStream_.store(nullptr, std::memory_order_release);
    if (stream_) {
        if (alreadyClosed) {
            retiredStream_ = std::move(stream_);
        } else {
            (void)stream_->requestStop();
            (void)stream_->close();
            stream_.reset();
        }
        const auto generation = generationCounter_.fetch_add(1, std::memory_order_acq_rel) + 1U;
        activeGeneration_.store(generation, std::memory_order_release);
    }
    sampleRate_.store(0, std::memory_order_relaxed);
    framesPerBurst_.store(0, std::memory_order_relaxed);
    bufferSizeFrames_.store(0, std::memory_order_relaxed);
}

void AudioEngine::discardPendingEventsLocked() noexcept {
    queue_.clearFromConsumer();
    emergencyPanic_.store(false, std::memory_order_release);
    synth_.panic();
}

void AudioEngine::scheduleRecovery(const std::uint64_t failedGeneration) noexcept {
    if (failedGeneration == 0U) return;
    {
        std::scoped_lock lock(recoveryMutex_);
        if (recoveryShutdown_ || shuttingDown_.load(std::memory_order_acquire) ||
            !desiredRunning_.load(std::memory_order_acquire)) {
            recoveryPending_.store(false, std::memory_order_release);
            return;
        }
        if (!recoveryRequested_ || failedGeneration >= requestedRecoveryGeneration_) {
            requestedRecoveryGeneration_ = failedGeneration;
        }
        recoveryRequested_ = true;
        recoveryPending_.store(true, std::memory_order_release);
    }
    recoveryCondition_.notify_all();
}

void AudioEngine::recoveryLoop() noexcept {
    for (;;) {
        std::uint64_t failedGeneration = 0;
        {
            std::unique_lock lock(recoveryMutex_);
            recoveryCondition_.wait(lock, [this] {
                return recoveryShutdown_ || recoveryRequested_;
            });
            if (recoveryShutdown_) return;
            failedGeneration = requestedRecoveryGeneration_;
            recoveryRequested_ = false;
        }

        bool validateFailedStream = true;
        std::size_t backoffIndex = 0;
        while (desiredRunning_.load(std::memory_order_acquire)) {
            bool staleRequest = false;
            bool restarted = false;
            {
                std::scoped_lock streamLock(streamMutex_);
                if (!desiredRunning_.load(std::memory_order_acquire)) break;
                if (validateFailedStream) {
                    staleRequest =
                        failedGeneration != activeGeneration_.load(std::memory_order_acquire) ||
                        failedGeneration != failedGeneration_.load(std::memory_order_acquire) ||
                        stream_.get() != failedStream_.load(std::memory_order_acquire) ||
                        !streamClosedByError_.load(std::memory_order_acquire);
                    if (!staleRequest) {
                        discardStreamLocked(true);
                        failedStream_.store(nullptr, std::memory_order_release);
                        failedGeneration_.store(0, std::memory_order_release);
                        streamClosedByError_.store(false, std::memory_order_release);
                        discardPendingEventsLocked();
                    }
                }
                if (!staleRequest) {
                    try {
                        restarted = startLocked();
                    } catch (...) {
                        lastError_.store(resultCode(oboe::Result::ErrorInternal), std::memory_order_relaxed);
                        if (stream_) discardStreamLocked(false);
                        discardPendingEventsLocked();
                    }
                }
            }

            if (staleRequest) {
                recoveryPending_.store(false, std::memory_order_release);
                break;
            }
            if (restarted) {
                restartCount_.fetch_add(1, std::memory_order_relaxed);
                recoveryPending_.store(false, std::memory_order_release);
                break;
            }

            validateFailedStream = false;
            std::unique_lock recoveryLock(recoveryMutex_);
            const auto delay = kRecoveryBackoff[backoffIndex];
            const bool interrupted = recoveryCondition_.wait_for(recoveryLock, delay, [this] {
                return recoveryShutdown_ || recoveryRequested_ ||
                    !desiredRunning_.load(std::memory_order_acquire);
            });
            if (recoveryShutdown_) return;
            if (!desiredRunning_.load(std::memory_order_acquire)) break;
            if (interrupted && recoveryRequested_) {
                failedGeneration = requestedRecoveryGeneration_;
                recoveryRequested_ = false;
                validateFailedStream = true;
                backoffIndex = 0;
            } else if (backoffIndex + 1U < kRecoveryBackoff.size()) {
                ++backoffIndex;
            }
        }
        if (!desiredRunning_.load(std::memory_order_acquire)) {
            recoveryPending_.store(false, std::memory_order_release);
        }
    }
}

void AudioEngine::updateMaximumQueueDepth(const std::size_t depth) noexcept {
    const int boundedDepth = static_cast<int>(std::min(
        depth,
        static_cast<std::size_t>(std::numeric_limits<int>::max())));
    int currentMaximum = maximumQueueDepth_.load(std::memory_order_relaxed);
    while (boundedDepth > currentMaximum &&
           !maximumQueueDepth_.compare_exchange_weak(
               currentMaximum,
               boundedDepth,
               std::memory_order_relaxed,
               std::memory_order_relaxed)) {
    }
}

void AudioEngine::handleEvent(const AudioEvent& event) noexcept {
    switch (event.type) {
        case AudioEventType::NoteOn:
            if (std::isfinite(event.floatValue)) {
                const float boundedVelocity = std::clamp(event.floatValue, 0.0F, 127.0F);
                synth_.noteOn(event.integerValue, static_cast<int>(boundedVelocity));
            }
            break;
        case AudioEventType::NoteOff:
            synth_.noteOff(event.integerValue);
            break;
        case AudioEventType::Panic:
            synth_.panic();
            break;
        case AudioEventType::Parameter:
            synth_.setParameter(static_cast<dsp::ParameterId>(event.integerValue), event.floatValue);
            break;
    }
}

void AudioEngine::writeSilence(float* output, const std::int32_t numFrames) noexcept {
    std::fill_n(output, static_cast<std::size_t>(numFrames) * 2U, 0.0F);
}

}  // namespace intervaltablet
