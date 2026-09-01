#pragma once

#include "dsp/SpscQueue.h"
#include "dsp/SynthEngine.h"

#include <oboe/Oboe.h>

#include <array>
#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <memory>
#include <mutex>
#include <thread>

namespace intervaltablet {

#if defined(INTERVAL_NATIVE_TESTING)
class AudioEngineTestAccess;
#endif

enum class AudioEventType : std::uint8_t { NoteOn, NoteOff, Panic, Parameter };

struct AudioEvent {
    AudioEventType type{AudioEventType::Panic};
    std::int32_t integerValue{0};
    float floatValue{0.0F};
    std::uint64_t streamGeneration{0};
};

class AudioEngine final :
    public oboe::AudioStreamDataCallback,
    public oboe::AudioStreamErrorCallback,
    public std::enable_shared_from_this<AudioEngine> {
public:
    static constexpr std::size_t kDiagnosticCount = 11;
    static constexpr std::size_t kMaxEventsPerCallback = 128U;
    using Diagnostics = std::array<int, kDiagnosticCount>;

    static std::shared_ptr<AudioEngine> create();

    ~AudioEngine() override;

    bool start();
    void stop();
    void shutdown() noexcept;
    bool enqueue(const AudioEvent& event) noexcept;
    Diagnostics diagnostics() const;

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        std::int32_t numFrames) override;
    void onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    AudioEngine();

    bool startLocked();
    bool openAndStartLocked(oboe::SharingMode sharingMode);
    void requestStopIntent() noexcept;
    void discardStreamLocked(bool alreadyClosed) noexcept;
    void discardPendingEventsLocked() noexcept;
    void scheduleRecovery(std::uint64_t failedGeneration) noexcept;
    void recoveryLoop() noexcept;
    void updateMaximumQueueDepth(std::size_t depth) noexcept;
    void handleEvent(const AudioEvent& event) noexcept;
    static void writeSilence(float* output, std::int32_t numFrames) noexcept;

    mutable std::mutex streamMutex_{};
    std::shared_ptr<oboe::AudioStream> stream_{};
    // Keep the most recently error-closed wrapper alive until a later control
    // operation. The recovery worker can wake before onErrorAfterClose returns.
    std::shared_ptr<oboe::AudioStream> retiredStream_{};
    dsp::SpscQueue<AudioEvent, 1024> queue_{};
    dsp::SynthEngine synth_{};
    std::atomic<int> sampleRate_{0};
    std::atomic<int> framesPerBurst_{0};
    std::atomic<int> bufferSizeFrames_{0};
    std::atomic<int> droppedEvents_{0};
    std::atomic<int> maximumQueueDepth_{0};
    std::atomic<int> restartCount_{0};
    std::atomic<int> lastError_{0};
    std::atomic<bool> emergencyPanic_{false};
    std::atomic<bool> desiredRunning_{false};
    std::atomic<bool> running_{false};
    std::atomic<bool> shuttingDown_{false};
    std::atomic<bool> recoveryPending_{false};
    std::atomic<bool> errorCloseInProgress_{false};
    std::atomic<bool> streamClosedByError_{false};
    std::atomic<oboe::AudioStream*> activeStream_{nullptr};
    std::atomic<oboe::AudioStream*> failedStream_{nullptr};
    std::atomic<std::uint64_t> generationCounter_{0};
    std::atomic<std::uint64_t> activeGeneration_{0};
    std::atomic<std::uint64_t> failedGeneration_{0};
    std::mutex lifecycleIntentMutex_{};

    std::mutex recoveryMutex_{};
    std::condition_variable recoveryCondition_{};
    bool recoveryRequested_{false};
    bool recoveryShutdown_{false};
    std::uint64_t requestedRecoveryGeneration_{0};
    std::thread recoveryThread_{};

    std::mutex shutdownMutex_{};
    std::condition_variable shutdownCondition_{};
    bool shutdownStarted_{false};
    bool shutdownComplete_{false};

#if defined(INTERVAL_NATIVE_TESTING)
    friend class AudioEngineTestAccess;
    void (*enqueueInterleaveHook_)(AudioEngine&) noexcept{nullptr};
#endif
};

}  // namespace intervaltablet
