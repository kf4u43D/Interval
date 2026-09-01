#include "AudioEngine.h"

#include <jni.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <cstdlib>
#include <deque>
#include <functional>
#include <iostream>
#include <memory>
#include <mutex>
#include <limits>
#include <thread>
#include <unordered_map>
#include <utility>
#include <vector>

extern "C" jlong JNICALL
Java_dev_intervaltablet_audio_NativeAudioEngine_nativeCreate(JNIEnv*, jobject);
extern "C" void JNICALL
Java_dev_intervaltablet_audio_NativeAudioEngine_nativeDestroy(JNIEnv*, jobject, jlong);
extern "C" jboolean JNICALL
Java_dev_intervaltablet_audio_NativeAudioEngine_nativeStart(JNIEnv*, jobject, jlong);
extern "C" jboolean JNICALL
Java_dev_intervaltablet_audio_NativeAudioEngine_nativeNoteOn(JNIEnv*, jobject, jlong, jint, jint);
extern "C" jboolean JNICALL
Java_dev_intervaltablet_audio_NativeAudioEngine_nativeSetParameter(JNIEnv*, jobject, jlong, jint, jfloat);

jintArray JNIEnv::NewIntArray(jsize) { return nullptr; }
void JNIEnv::SetIntArrayRegion(jintArray, jsize, jsize, const jint*) {}

namespace intervaltablet {

class AudioEngineTestAccess {
public:
    using EnqueueHook = void (*)(AudioEngine&) noexcept;

    static void requestStopIntent(AudioEngine& engine) noexcept {
        engine.requestStopIntent();
    }

    static void setEnqueueInterleaveHook(AudioEngine& engine, EnqueueHook hook) noexcept {
        engine.enqueueInterleaveHook_ = hook;
    }

    static std::uint64_t activeGeneration(const AudioEngine& engine) noexcept {
        return engine.activeGeneration_.load(std::memory_order_acquire);
    }

    static bool injectTaggedEvent(
        AudioEngine& engine,
        AudioEvent event,
        const std::uint64_t generation) noexcept {
        event.streamGeneration = generation;
        const bool pushed = engine.queue_.push(event);
        if (pushed) engine.updateMaximumQueueDepth(engine.queue_.sizeApprox());
        return pushed;
    }
};

}  // namespace intervaltablet

namespace fake_oboe {

struct Plan {
    oboe::Result openResult{oboe::Result::OK};
    oboe::Result startResult{oboe::Result::OK};
    std::int32_t sampleRate{48000};
    std::int32_t framesPerBurst{96};
    std::int32_t callbackFrames{32};
    bool callbackOnStart{false};
};

struct BuilderState {
    oboe::SharingMode sharingMode{oboe::SharingMode::Shared};
    std::int32_t requestedSampleRate{0};
    oboe::AudioStreamDataCallback* dataCallback{nullptr};
    oboe::AudioStreamErrorCallback* errorCallback{nullptr};
    std::shared_ptr<oboe::AudioStreamDataCallback> sharedDataCallback{};
    std::shared_ptr<oboe::AudioStreamErrorCallback> sharedErrorCallback{};
};

struct StreamState {
    Plan plan{};
    oboe::AudioStreamDataCallback* dataCallback{nullptr};
    oboe::AudioStreamErrorCallback* errorCallback{nullptr};
    std::int32_t bufferSizeFrames{0};
    std::int32_t xRunCount{0};
    oboe::DataCallbackResult lastCallbackResult{oboe::DataCallbackResult::Stop};
    std::vector<float> lastBuffer{};
    bool started{false};
    bool closed{false};
};

std::mutex mutex{};
std::condition_variable changed{};
std::deque<Plan> plans{};
BuilderState builder{};
std::unordered_map<const oboe::AudioStream*, StreamState> streamStates{};
std::vector<std::weak_ptr<oboe::AudioStream>> streams{};
std::vector<oboe::SharingMode> openModes{};
std::vector<std::int32_t> requestedSampleRates{};
int closeCalls{0};
int startCalls{0};
std::function<void()> afterOpenHook{};
std::function<void()> beforeRequestStartHook{};

struct ErrorCallbackGate {
    std::mutex mutex{};
    std::condition_variable condition{};
    bool callbackCaptured{false};
    bool callbackMayContinue{false};
};

void reset() {
    std::scoped_lock lock(mutex);
    plans.clear();
    builder = BuilderState{};
    streamStates.clear();
    streams.clear();
    openModes.clear();
    requestedSampleRates.clear();
    closeCalls = 0;
    startCalls = 0;
    afterOpenHook = {};
    beforeRequestStartHook = {};
}

void setAfterOpenHook(std::function<void()> hook) {
    std::scoped_lock lock(mutex);
    afterOpenHook = std::move(hook);
}

void setBeforeRequestStartHook(std::function<void()> hook) {
    std::scoped_lock lock(mutex);
    beforeRequestStartHook = std::move(hook);
}

void addPlan(const Plan& plan) {
    std::scoped_lock lock(mutex);
    plans.push_back(plan);
}

std::size_t openCount() {
    std::scoped_lock lock(mutex);
    return openModes.size();
}

oboe::SharingMode openMode(const std::size_t index) {
    std::scoped_lock lock(mutex);
    return openModes.at(index);
}

std::int32_t requestedSampleRate(const std::size_t index) {
    std::scoped_lock lock(mutex);
    return requestedSampleRates.at(index);
}

std::shared_ptr<oboe::AudioStream> stream(const std::size_t index) {
    std::scoped_lock lock(mutex);
    return streams.at(index).lock();
}

bool waitForOpenCount(const std::size_t expected) {
    std::unique_lock lock(mutex);
    return changed.wait_for(lock, std::chrono::seconds{2}, [expected] {
        return openModes.size() >= expected;
    });
}

oboe::DataCallbackResult render(oboe::AudioStream* stream, const std::int32_t frames) {
    oboe::AudioStreamDataCallback* callback = nullptr;
    {
        std::scoped_lock lock(mutex);
        callback = streamStates.at(stream).dataCallback;
    }
    std::vector<float> output(static_cast<std::size_t>(frames) * 2U, 0.75F);
    const auto result = callback->onAudioReady(stream, output.data(), frames);
    {
        std::scoped_lock lock(mutex);
        auto& state = streamStates.at(stream);
        state.lastCallbackResult = result;
        state.lastBuffer = std::move(output);
    }
    changed.notify_all();
    return result;
}

std::vector<float> lastBuffer(oboe::AudioStream* stream) {
    std::scoped_lock lock(mutex);
    return streamStates.at(stream).lastBuffer;
}

oboe::DataCallbackResult lastCallbackResult(oboe::AudioStream* stream) {
    std::scoped_lock lock(mutex);
    return streamStates.at(stream).lastCallbackResult;
}

void fireError(oboe::AudioStream* stream, const oboe::Result error) {
    oboe::AudioStreamErrorCallback* callback = nullptr;
    {
        std::scoped_lock lock(mutex);
        callback = streamStates.at(stream).errorCallback;
    }
    callback->onErrorBeforeClose(stream, error);
    {
        std::scoped_lock lock(mutex);
        streamStates.at(stream).closed = true;
    }
    callback->onErrorAfterClose(stream, error);
    changed.notify_all();
}

void repeatOldErrorAfterClose(oboe::AudioStream* stream, const oboe::Result error) {
    oboe::AudioStreamErrorCallback* callback = nullptr;
    {
        std::scoped_lock lock(mutex);
        callback = streamStates.at(stream).errorCallback;
    }
    callback->onErrorAfterClose(stream, error);
}

void fireErrorAfterCapture(
    const std::shared_ptr<oboe::AudioStream>& stream,
    const oboe::Result error,
    ErrorCallbackGate& gate) {
    oboe::AudioStreamErrorCallback* callback = nullptr;
    {
        std::scoped_lock lock(mutex);
        callback = streamStates.at(stream.get()).errorCallback;
    }
    {
        std::unique_lock gateLock(gate.mutex);
        gate.callbackCaptured = true;
        gate.condition.notify_all();
        gate.condition.wait(gateLock, [&gate] { return gate.callbackMayContinue; });
    }
    (void)stream->requestStop();
    callback->onErrorBeforeClose(stream.get(), error);
    (void)stream->close();
    callback->onErrorAfterClose(stream.get(), error);
}

bool waitForErrorCallbackCapture(ErrorCallbackGate& gate) {
    std::unique_lock lock(gate.mutex);
    return gate.condition.wait_for(lock, std::chrono::seconds{2}, [&gate] {
        return gate.callbackCaptured;
    });
}

void releaseErrorCallback(ErrorCallbackGate& gate) {
    {
        std::scoped_lock lock(gate.mutex);
        gate.callbackMayContinue = true;
    }
    gate.condition.notify_all();
}

int closeCallCount() {
    std::scoped_lock lock(mutex);
    return closeCalls;
}

int startCallCount() {
    std::scoped_lock lock(mutex);
    return startCalls;
}

}  // namespace fake_oboe

namespace oboe {

Result AudioStream::requestStart() {
    fake_oboe::Plan plan{};
    {
        std::scoped_lock lock(fake_oboe::mutex);
        auto& state = fake_oboe::streamStates.at(this);
        plan = state.plan;
        state.started = plan.startResult == Result::OK;
        ++fake_oboe::startCalls;
    }
    if (plan.startResult == Result::OK && plan.callbackOnStart) {
        (void)fake_oboe::render(this, plan.callbackFrames);
    }
    return plan.startResult;
}

Result AudioStream::requestStop() {
    std::scoped_lock lock(fake_oboe::mutex);
    fake_oboe::streamStates.at(this).started = false;
    return Result::OK;
}

Result AudioStream::close() {
    std::scoped_lock lock(fake_oboe::mutex);
    fake_oboe::streamStates.at(this).closed = true;
    ++fake_oboe::closeCalls;
    return Result::OK;
}

ResultWithValue<std::int32_t> AudioStream::getXRunCount() const {
    std::scoped_lock lock(fake_oboe::mutex);
    return {Result::OK, fake_oboe::streamStates.at(this).xRunCount};
}

std::int32_t AudioStream::getSampleRate() const {
    std::scoped_lock lock(fake_oboe::mutex);
    return fake_oboe::streamStates.at(this).plan.sampleRate;
}

std::int32_t AudioStream::getFramesPerBurst() const {
    std::scoped_lock lock(fake_oboe::mutex);
    return fake_oboe::streamStates.at(this).plan.framesPerBurst;
}

std::int32_t AudioStream::getBufferSizeInFrames() const {
    std::scoped_lock lock(fake_oboe::mutex);
    return fake_oboe::streamStates.at(this).bufferSizeFrames;
}

ResultWithValue<std::int32_t> AudioStream::setBufferSizeInFrames(const std::int32_t frames) {
    std::function<void()> hook{};
    {
        std::scoped_lock lock(fake_oboe::mutex);
        fake_oboe::streamStates.at(this).bufferSizeFrames = frames;
        hook = std::move(fake_oboe::beforeRequestStartHook);
    }
    if (hook) hook();
    return {Result::OK, frames};
}

AudioStreamBuilder& AudioStreamBuilder::setDirection(Direction) {
    std::scoped_lock lock(fake_oboe::mutex);
    fake_oboe::builder = fake_oboe::BuilderState{};
    return *this;
}

AudioStreamBuilder& AudioStreamBuilder::setPerformanceMode(PerformanceMode) { return *this; }

AudioStreamBuilder& AudioStreamBuilder::setSharingMode(const SharingMode mode) {
    std::scoped_lock lock(fake_oboe::mutex);
    fake_oboe::builder.sharingMode = mode;
    return *this;
}

AudioStreamBuilder& AudioStreamBuilder::setFormat(AudioFormat) { return *this; }

AudioStreamBuilder& AudioStreamBuilder::setChannelCount(std::int32_t) { return *this; }

AudioStreamBuilder& AudioStreamBuilder::setSampleRate(const std::int32_t sampleRate) {
    std::scoped_lock lock(fake_oboe::mutex);
    fake_oboe::builder.requestedSampleRate = sampleRate;
    return *this;
}

AudioStreamBuilder& AudioStreamBuilder::setDataCallback(
    std::shared_ptr<AudioStreamDataCallback> callback) {
    std::scoped_lock lock(fake_oboe::mutex);
    fake_oboe::builder.dataCallback = callback.get();
    fake_oboe::builder.sharedDataCallback = std::move(callback);
    return *this;
}

AudioStreamBuilder& AudioStreamBuilder::setDataCallback(AudioStreamDataCallback* callback) {
    std::scoped_lock lock(fake_oboe::mutex);
    fake_oboe::builder.dataCallback = callback;
    fake_oboe::builder.sharedDataCallback.reset();
    return *this;
}

AudioStreamBuilder& AudioStreamBuilder::setErrorCallback(
    std::shared_ptr<AudioStreamErrorCallback> callback) {
    std::scoped_lock lock(fake_oboe::mutex);
    fake_oboe::builder.errorCallback = callback.get();
    fake_oboe::builder.sharedErrorCallback = std::move(callback);
    return *this;
}

AudioStreamBuilder& AudioStreamBuilder::setErrorCallback(AudioStreamErrorCallback* callback) {
    std::scoped_lock lock(fake_oboe::mutex);
    fake_oboe::builder.errorCallback = callback;
    fake_oboe::builder.sharedErrorCallback.reset();
    return *this;
}

Result AudioStreamBuilder::openStream(std::shared_ptr<AudioStream>& stream) {
    fake_oboe::Plan plan{};
    fake_oboe::BuilderState currentBuilder{};
    {
        std::scoped_lock lock(fake_oboe::mutex);
        if (!fake_oboe::plans.empty()) {
            plan = fake_oboe::plans.front();
            fake_oboe::plans.pop_front();
        }
        currentBuilder = fake_oboe::builder;
        fake_oboe::builder.sharedDataCallback.reset();
        fake_oboe::builder.sharedErrorCallback.reset();
        fake_oboe::openModes.push_back(currentBuilder.sharingMode);
        fake_oboe::requestedSampleRates.push_back(currentBuilder.requestedSampleRate);
    }
    fake_oboe::changed.notify_all();
    if (plan.openResult != Result::OK) {
        stream.reset();
        return plan.openResult;
    }

    auto created = std::make_shared<AudioStream>();
    created->retainCallbacks(
        currentBuilder.sharedDataCallback,
        currentBuilder.sharedErrorCallback);
    {
        std::scoped_lock lock(fake_oboe::mutex);
        fake_oboe::streamStates[created.get()] = fake_oboe::StreamState{
            plan,
            currentBuilder.dataCallback,
            currentBuilder.errorCallback,
        };
        fake_oboe::streams.push_back(created);
    }
    stream = std::move(created);
    std::function<void()> hook{};
    {
        std::scoped_lock lock(fake_oboe::mutex);
        hook = std::move(fake_oboe::afterOpenHook);
    }
    if (hook) hook();
    return Result::OK;
}

}  // namespace oboe

namespace {

enum DiagnosticIndex : std::size_t {
    SampleRate = 0,
    FramesPerBurst = 1,
    XRunCount = 2,
    DroppedEvents = 3,
    CurrentQueueDepth = 4,
    MaximumQueueDepth = 5,
    Running = 6,
    RestartCount = 7,
    LastError = 8,
    BufferSizeFrames = 9,
    RecoveryPending = 10,
};

void require(const bool condition, const char* message) {
    if (!condition) {
        std::cerr << "FAIL: " << message << '\n';
        std::exit(EXIT_FAILURE);
    }
}

class EngineFixture {
public:
    EngineFixture()
        : engine_(intervaltablet::AudioEngine::create()) {}

    ~EngineFixture() { engine_->shutdown(); }

    bool start() { return engine_->start(); }
    void stop() { engine_->stop(); }
    bool enqueue(const intervaltablet::AudioEvent& event) { return engine_->enqueue(event); }
    intervaltablet::AudioEngine::Diagnostics diagnostics() const { return engine_->diagnostics(); }

private:
    std::shared_ptr<intervaltablet::AudioEngine> engine_{};
};

bool isSilent(const std::vector<float>& buffer) {
    return std::all_of(buffer.begin(), buffer.end(), [](const float sample) {
        return sample == 0.0F;
    });
}

void testExclusiveOpenFailureFallsBackToShared() {
    fake_oboe::reset();
    fake_oboe::addPlan({oboe::Result::ErrorInternal});
    fake_oboe::addPlan({
        oboe::Result::OK,
        oboe::Result::OK,
        44100,
        128,
        32,
        true,
    });

    EngineFixture engine;
    require(engine.start(), "Shared stream must start after Exclusive open failure");
    require(fake_oboe::openCount() == 2U, "fallback must make exactly two open attempts");
    require(fake_oboe::openMode(0) == oboe::SharingMode::Exclusive, "Exclusive must be attempted first");
    require(fake_oboe::openMode(1) == oboe::SharingMode::Shared, "Shared must be attempted second");
    require(fake_oboe::requestedSampleRate(0) == 0, "engine must not force a sample rate");
    require(fake_oboe::requestedSampleRate(1) == 0, "fallback must keep native sample-rate negotiation");

    const auto diagnostics = engine.diagnostics();
    require(diagnostics[SampleRate] == 44100, "diagnostics must expose negotiated sample rate");
    require(diagnostics[FramesPerBurst] == 128, "diagnostics must expose negotiated burst size");
    require(diagnostics[BufferSizeFrames] == 256, "engine must request a two-burst buffer");
    require(diagnostics[Running] == 1, "stream must report running");
    const auto startedStream = fake_oboe::stream(0);
    require(startedStream != nullptr, "successful fake stream must remain owned");
    require(
        fake_oboe::lastCallbackResult(startedStream.get()) == oboe::DataCallbackResult::Continue,
        "first callback must see running armed before requestStart returns");
}

void testExclusiveStartFailureFallsBackToShared() {
    fake_oboe::reset();
    fake_oboe::addPlan({oboe::Result::OK, oboe::Result::ErrorInternal});
    fake_oboe::addPlan({oboe::Result::OK, oboe::Result::OK});

    EngineFixture engine;
    require(engine.start(), "Shared stream must start after Exclusive requestStart failure");
    require(fake_oboe::openCount() == 2U, "start failure must trigger a second open attempt");
    require(fake_oboe::openMode(0) == oboe::SharingMode::Exclusive, "first start must be Exclusive");
    require(fake_oboe::openMode(1) == oboe::SharingMode::Shared, "second start must be Shared");
    require(fake_oboe::closeCallCount() >= 1, "failed Exclusive stream must be closed");
}

void testOverflowForcesEmergencySilence() {
    fake_oboe::reset();
    fake_oboe::addPlan({});

    EngineFixture engine;
    require(engine.start(), "engine must start for overflow test");
    const intervaltablet::AudioEvent noteOn{
        intervaltablet::AudioEventType::NoteOn,
        60,
        127.0F,
    };
    for (std::size_t index = 0; index < 1023U; ++index) {
        require(engine.enqueue(noteOn), "queue must accept every usable slot");
    }
    require(!engine.enqueue(noteOn), "queue must report overflow");
    auto diagnostics = engine.diagnostics();
    require(diagnostics[DroppedEvents] == 1, "overflow must increment dropped-event count");
    require(diagnostics[CurrentQueueDepth] == 1023, "current queue depth must be diagnosed");
    require(diagnostics[MaximumQueueDepth] == 1023, "maximum queue depth must be diagnosed");

    const auto activeStream = fake_oboe::stream(0);
    require(activeStream != nullptr, "overflow test needs an active stream");
    require(
        fake_oboe::render(activeStream.get(), 64) == oboe::DataCallbackResult::Continue,
        "engine should continue after conservative overflow recovery");
    require(isSilent(fake_oboe::lastBuffer(activeStream.get())), "overflow recovery must render silence");
    diagnostics = engine.diagnostics();
    require(diagnostics[CurrentQueueDepth] == 0, "overflow recovery must clear queued events");
}

void testStopRestartDiscardsPendingEvents() {
    fake_oboe::reset();
    fake_oboe::addPlan({});
    fake_oboe::addPlan({
        oboe::Result::OK,
        oboe::Result::OK,
        48000,
        96,
        32,
        true,
    });

    EngineFixture engine;
    require(engine.start(), "first stream must start");
    require(engine.enqueue({intervaltablet::AudioEventType::NoteOn, 64, 127.0F}), "note must enqueue");
    engine.stop();
    require(engine.diagnostics()[CurrentQueueDepth] == 0, "stop must clear pending events");
    require(engine.start(), "second stream must start");
    const auto restartedStream = fake_oboe::stream(1);
    require(restartedStream != nullptr, "second fake stream must remain owned");
    require(isSilent(fake_oboe::lastBuffer(restartedStream.get())), "restart must not replay a stale NoteOn");
}

void testErrorRecoversOffCallbackAndRejectsOldCallbacks() {
    fake_oboe::reset();
    fake_oboe::addPlan({});
    fake_oboe::addPlan({
        oboe::Result::OK,
        oboe::Result::OK,
        48000,
        96,
        32,
        true,
    });

    EngineFixture engine;
    require(engine.start(), "initial stream must start");
    require(engine.enqueue({intervaltablet::AudioEventType::NoteOn, 67, 127.0F}), "note must queue before error");
    const auto failedStream = fake_oboe::stream(0);
    require(failedStream != nullptr, "failed stream must remain available to the fake callback");
    fake_oboe::fireError(failedStream.get(), oboe::Result::ErrorDisconnected);
    require(fake_oboe::waitForOpenCount(2U), "recovery worker must reopen outside the error callback");

    const auto diagnostics = engine.diagnostics();
    require(diagnostics[Running] == 1, "recovered stream must be running");
    require(diagnostics[RestartCount] == 1, "successful automatic recovery must be counted");
    require(
        diagnostics[LastError] == static_cast<int>(oboe::Result::ErrorDisconnected),
        "last stream error must remain diagnosable after recovery");
    require(diagnostics[RecoveryPending] == 0, "successful recovery must clear pending state");
    require(diagnostics[CurrentQueueDepth] == 0, "recovery must discard events from the failed stream");

    const auto recoveredStream = fake_oboe::stream(1);
    require(recoveredStream != nullptr, "recovery stream must remain owned");
    require(isSilent(fake_oboe::lastBuffer(recoveredStream.get())), "recovery must restart from silence");
    require(
        fake_oboe::render(failedStream.get(), 16) == oboe::DataCallbackResult::Stop,
        "data callback from an old stream generation must stop");
    require(isSilent(fake_oboe::lastBuffer(failedStream.get())), "old generation callback must only write silence");

    fake_oboe::repeatOldErrorAfterClose(failedStream.get(), oboe::Result::ErrorDisconnected);
    std::this_thread::sleep_for(std::chrono::milliseconds{20});
    require(fake_oboe::openCount() == 2U, "old error callback must not replace the recovered stream");
}

void testStopCancelsRecoveryBackoff() {
    fake_oboe::reset();
    fake_oboe::addPlan({});
    fake_oboe::addPlan({oboe::Result::ErrorInternal});
    fake_oboe::addPlan({oboe::Result::ErrorInternal});

    EngineFixture engine;
    require(engine.start(), "initial stream must start before failed recovery");
    const auto failedStream = fake_oboe::stream(0);
    require(failedStream != nullptr, "failed recovery test needs its initial stream");
    fake_oboe::fireError(failedStream.get(), oboe::Result::ErrorDisconnected);
    require(fake_oboe::waitForOpenCount(3U), "recovery must attempt Exclusive and Shared");
    engine.stop();
    const auto opensAfterStop = fake_oboe::openCount();
    std::this_thread::sleep_for(std::chrono::milliseconds{150});
    require(fake_oboe::openCount() == opensAfterStop, "stop must cancel recovery retries");
    require(engine.diagnostics()[RecoveryPending] == 0, "stop must clear recovery state");
}

void testSharedCallbackOwnershipOutlivesShutdown() {
    fake_oboe::reset();
    fake_oboe::addPlan({});

    auto engine = intervaltablet::AudioEngine::create();
    const std::weak_ptr<intervaltablet::AudioEngine> weakEngine = engine;
    require(engine->start(), "engine must start before callback lifetime test");
    auto failedStream = fake_oboe::stream(0);
    require(failedStream != nullptr, "lifetime test needs the opened stream wrapper");

    fake_oboe::ErrorCallbackGate gate{};
    std::thread errorThread([failedStream, &gate] {
        fake_oboe::fireErrorAfterCapture(failedStream, oboe::Result::ErrorDisconnected, gate);
    });
    require(
        fake_oboe::waitForErrorCallbackCapture(gate),
        "error thread must capture its callback before shutdown");

    engine->shutdown();
    engine.reset();
    require(
        !weakEngine.expired(),
        "stream-owned callback must keep the engine alive while the error thread is paused");

    fake_oboe::releaseErrorCallback(gate);
    errorThread.join();
    failedStream.reset();
    require(
        weakEngine.expired(),
        "engine must be released after shutdown and the captured callback both finish");
}

void testStopIntentAfterOpenPreventsRequestStart() {
    fake_oboe::reset();
    fake_oboe::addPlan({});

    auto engine = intervaltablet::AudioEngine::create();
    fake_oboe::setAfterOpenHook([engine] {
        intervaltablet::AudioEngineTestAccess::requestStopIntent(*engine);
    });

    require(!engine->start(), "Stop intent after open must cancel the start attempt");
    require(fake_oboe::openCount() == 1U, "cancelled open must not attempt Shared fallback");
    require(fake_oboe::startCallCount() == 0, "cancelled open must never call requestStart");
    require(engine->diagnostics()[Running] == 0, "cancelled open must remain stopped");
    engine->shutdown();
}

void testStopIntentImmediatelyBeforeRequestStartPreventsStart() {
    fake_oboe::reset();
    fake_oboe::addPlan({});

    auto engine = intervaltablet::AudioEngine::create();
    fake_oboe::setBeforeRequestStartHook([engine] {
        intervaltablet::AudioEngineTestAccess::requestStopIntent(*engine);
    });

    require(!engine->start(), "final Stop intent must cancel requestStart");
    require(fake_oboe::openCount() == 1U, "final cancellation must not attempt Shared fallback");
    require(fake_oboe::startCallCount() == 0, "final intent check must precede requestStart");
    require(engine->diagnostics()[Running] == 0, "final cancellation must leave the engine stopped");
    engine->shutdown();
}

void testEnqueueGenerationRaceTriggersConservativeRecovery() {
    fake_oboe::reset();
    fake_oboe::addPlan({});
    fake_oboe::addPlan({});

    auto engine = intervaltablet::AudioEngine::create();
    require(engine->start(), "initial stream must start before enqueue race");
    intervaltablet::AudioEngineTestAccess::setEnqueueInterleaveHook(
        *engine,
        +[](intervaltablet::AudioEngine& target) noexcept {
            target.stop();
            (void)target.start();
        });

    require(
        !engine->enqueue({intervaltablet::AudioEventType::NoteOn, 72, 127.0F}),
        "enqueue crossing a stream generation must report rejection");
    auto diagnostics = engine->diagnostics();
    require(diagnostics[Running] == 1, "interleaved replacement stream must be running");
    require(diagnostics[DroppedEvents] == 1, "generation rejection must increment dropped events");
    require(diagnostics[CurrentQueueDepth] == 1, "stale tagged event must remain visible until callback");

    const auto replacementStream = fake_oboe::stream(1);
    require(replacementStream != nullptr, "enqueue race must create a replacement stream");
    require(
        fake_oboe::render(replacementStream.get(), 64) == oboe::DataCallbackResult::Continue,
        "replacement stream must continue after generation recovery");
    require(
        isSilent(fake_oboe::lastBuffer(replacementStream.get())),
        "generation recovery must not render the stale NoteOn");
    diagnostics = engine->diagnostics();
    require(diagnostics[CurrentQueueDepth] == 0, "generation recovery must clear the stale queue");
    engine->shutdown();
}

void testCallbackNeutralizesStaleGenerationWithoutOverflowFlag() {
    fake_oboe::reset();
    fake_oboe::addPlan({});
    fake_oboe::addPlan({});

    auto engine = intervaltablet::AudioEngine::create();
    require(engine->start(), "initial stream must start before stale-tag test");
    const auto oldGeneration = intervaltablet::AudioEngineTestAccess::activeGeneration(*engine);
    engine->stop();
    require(engine->start(), "replacement stream must start before stale-tag injection");
    require(
        oldGeneration != intervaltablet::AudioEngineTestAccess::activeGeneration(*engine),
        "replacement stream must have a distinct generation");
    require(
        intervaltablet::AudioEngineTestAccess::injectTaggedEvent(
            *engine,
            {intervaltablet::AudioEventType::NoteOn, 74, 127.0F},
            oldGeneration),
        "test must inject one stale tagged event");

    const auto replacementStream = fake_oboe::stream(1);
    require(replacementStream != nullptr, "stale-tag test needs its replacement stream");
    (void)fake_oboe::render(replacementStream.get(), 64);
    require(
        isSilent(fake_oboe::lastBuffer(replacementStream.get())),
        "callback must neutralize a stale NoteOn even without emergency overflow state");
    require(
        engine->diagnostics()[CurrentQueueDepth] == 0,
        "callback must consume and discard the stale tagged event");
    engine->shutdown();
}

void testCallbackDrainIsBoundedAndFifo() {
    fake_oboe::reset();
    fake_oboe::addPlan({});

    auto engine = intervaltablet::AudioEngine::create();
    require(engine->start(), "engine must start before bounded-drain test");
    for (std::size_t index = 0; index < intervaltablet::AudioEngine::kMaxEventsPerCallback; ++index) {
        require(
            engine->enqueue({intervaltablet::AudioEventType::NoteOff, 60, 0.0F}),
            "bounded-drain prefix must enqueue");
    }
    require(
        engine->enqueue({intervaltablet::AudioEventType::NoteOn, 60, 127.0F}),
        "event immediately beyond callback budget must enqueue");

    const auto activeStream = fake_oboe::stream(0);
    require(activeStream != nullptr, "bounded-drain test needs its active stream");
    (void)fake_oboe::render(activeStream.get(), 64);
    require(
        engine->diagnostics()[CurrentQueueDepth] == 1,
        "first callback must consume exactly its bounded event budget");
    require(
        isSilent(fake_oboe::lastBuffer(activeStream.get())),
        "FIFO must keep the trailing NoteOn out of the first callback");

    (void)fake_oboe::render(activeStream.get(), 64);
    require(
        engine->diagnostics()[CurrentQueueDepth] == 0,
        "second callback must consume the remaining FIFO event");
    require(
        !isSilent(fake_oboe::lastBuffer(activeStream.get())),
        "trailing NoteOn must render only after earlier FIFO events");
    engine->shutdown();
}

void testNonFiniteEventsCannotPoisonCallback() {
    fake_oboe::reset();
    fake_oboe::addPlan({});

    auto engine = intervaltablet::AudioEngine::create();
    require(engine->start(), "engine must start before non-finite event test");
    const float nan = std::numeric_limits<float>::quiet_NaN();
    const float infinity = std::numeric_limits<float>::infinity();
    require(
        engine->enqueue({intervaltablet::AudioEventType::NoteOn, 60, nan}),
        "non-finite NoteOn payload must remain queue-safe");
    require(
        engine->enqueue({
            intervaltablet::AudioEventType::Parameter,
            static_cast<std::int32_t>(intervaltablet::dsp::ParameterId::DelayTime),
            infinity,
        }),
        "non-finite parameter payload must remain queue-safe");

    const auto activeStream = fake_oboe::stream(0);
    require(activeStream != nullptr, "non-finite event test needs an active stream");
    (void)fake_oboe::render(activeStream.get(), 64);
    auto output = fake_oboe::lastBuffer(activeStream.get());
    require(isSilent(output), "non-finite NoteOn must be ignored instead of producing a voice");
    for (const float sample : output) require(std::isfinite(sample), "non-finite events must keep output finite");

    require(
        engine->enqueue({intervaltablet::AudioEventType::NoteOn, 60, 127000.0F}),
        "oversized finite velocity must remain queue-safe");
    require(
        engine->enqueue({
            intervaltablet::AudioEventType::Parameter,
            static_cast<std::int32_t>(intervaltablet::dsp::ParameterId::Cutoff),
            nan,
        }),
        "NaN filter payload must remain queue-safe");
    (void)fake_oboe::render(activeStream.get(), 64);
    output = fake_oboe::lastBuffer(activeStream.get());
    require(!isSilent(output), "finite oversized velocity must clamp to an audible MIDI velocity");
    for (const float sample : output) require(std::isfinite(sample), "sanitized callback output must stay finite");
    engine->shutdown();
}

std::vector<float> renderBridgeVelocity(const jint velocity) {
    fake_oboe::reset();
    fake_oboe::addPlan({});
    const jlong handle = Java_dev_intervaltablet_audio_NativeAudioEngine_nativeCreate(nullptr, nullptr);
    require(handle != 0, "JNI bridge must create an engine handle");
    require(
        Java_dev_intervaltablet_audio_NativeAudioEngine_nativeStart(nullptr, nullptr, handle) == JNI_TRUE,
        "JNI bridge engine must start");
    require(
        Java_dev_intervaltablet_audio_NativeAudioEngine_nativeNoteOn(
            nullptr,
            nullptr,
            handle,
            static_cast<jint>(60),
            velocity) == JNI_TRUE,
        "JNI bridge NoteOn must enqueue");
    const auto activeStream = fake_oboe::stream(0);
    require(activeStream != nullptr, "JNI bridge velocity test needs an active stream");
    (void)fake_oboe::render(activeStream.get(), 64);
    const auto output = fake_oboe::lastBuffer(activeStream.get());
    Java_dev_intervaltablet_audio_NativeAudioEngine_nativeDestroy(nullptr, nullptr, handle);
    return output;
}

void testNativeBridgeClampsMidiAndRejectsInvalidParameters() {
    const auto maximumVelocity = renderBridgeVelocity(std::numeric_limits<jint>::max());
    const auto midiMaximum = renderBridgeVelocity(static_cast<jint>(127));
    require(maximumVelocity == midiMaximum, "JNI velocity must clamp to 127 before float conversion");

    fake_oboe::reset();
    fake_oboe::addPlan({});
    const jlong handle = Java_dev_intervaltablet_audio_NativeAudioEngine_nativeCreate(nullptr, nullptr);
    require(handle != 0, "JNI parameter test must create an engine handle");
    require(
        Java_dev_intervaltablet_audio_NativeAudioEngine_nativeStart(nullptr, nullptr, handle) == JNI_TRUE,
        "JNI parameter test engine must start");
    require(
        Java_dev_intervaltablet_audio_NativeAudioEngine_nativeSetParameter(
            nullptr,
            nullptr,
            handle,
            static_cast<jint>(intervaltablet::dsp::ParameterId::DelayTime),
            std::numeric_limits<jfloat>::quiet_NaN()) == JNI_FALSE,
        "JNI bridge must reject non-finite parameter values before enqueue");
    require(
        Java_dev_intervaltablet_audio_NativeAudioEngine_nativeSetParameter(
            nullptr,
            nullptr,
            handle,
            static_cast<jint>(999),
            0.5F) == JNI_FALSE,
        "JNI bridge must reject unknown parameter identifiers");
    Java_dev_intervaltablet_audio_NativeAudioEngine_nativeDestroy(nullptr, nullptr, handle);
}

}  // namespace

int main() {
    testExclusiveOpenFailureFallsBackToShared();
    testExclusiveStartFailureFallsBackToShared();
    testOverflowForcesEmergencySilence();
    testStopRestartDiscardsPendingEvents();
    testErrorRecoversOffCallbackAndRejectsOldCallbacks();
    testStopCancelsRecoveryBackoff();
    testSharedCallbackOwnershipOutlivesShutdown();
    testStopIntentAfterOpenPreventsRequestStart();
    testStopIntentImmediatelyBeforeRequestStartPreventsStart();
    testEnqueueGenerationRaceTriggersConservativeRecovery();
    testCallbackNeutralizesStaleGenerationWithoutOverflowFlag();
    testCallbackDrainIsBoundedAndFifo();
    testNonFiniteEventsCannotPoisonCallback();
    testNativeBridgeClampsMidiAndRejectsInvalidParameters();
    std::cout << "Native audio engine tests: OK\n";
    return EXIT_SUCCESS;
}
