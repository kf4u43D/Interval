#pragma once

#include <cstdint>
#include <memory>
#include <utility>

namespace oboe {

enum class Result { OK, ErrorInternal, ErrorDisconnected };
enum class SharingMode { Exclusive, Shared };
enum class Direction { Output };
enum class PerformanceMode { LowLatency };
enum class AudioFormat { Float };
enum class DataCallbackResult { Continue, Stop };

template <typename T>
class ResultWithValue {
public:
    ResultWithValue(const Result result, const T value) noexcept
        : result_(result), value_(value) {}

    explicit operator bool() const noexcept { return result_ == Result::OK; }
    T value() const noexcept { return value_; }

private:
    Result result_{Result::ErrorInternal};
    T value_{};
};

class AudioStream;

class AudioStreamDataCallback {
public:
    virtual ~AudioStreamDataCallback() = default;
    virtual DataCallbackResult onAudioReady(
        AudioStream* stream,
        void* audioData,
        std::int32_t numFrames) = 0;
};

class AudioStreamErrorCallback {
public:
    virtual ~AudioStreamErrorCallback() = default;
    virtual void onErrorBeforeClose(AudioStream* stream, Result error) = 0;
    virtual void onErrorAfterClose(AudioStream* stream, Result error) = 0;
};

class AudioStream {
public:
    Result requestStart();
    Result requestStop();
    Result close();
    ResultWithValue<std::int32_t> getXRunCount() const;
    std::int32_t getSampleRate() const;
    std::int32_t getFramesPerBurst() const;
    std::int32_t getBufferSizeInFrames() const;
    ResultWithValue<std::int32_t> setBufferSizeInFrames(std::int32_t frames);

    void retainCallbacks(
        std::shared_ptr<AudioStreamDataCallback> dataCallback,
        std::shared_ptr<AudioStreamErrorCallback> errorCallback) {
        dataCallbackOwner_ = std::move(dataCallback);
        errorCallbackOwner_ = std::move(errorCallback);
    }

private:
    std::shared_ptr<AudioStreamDataCallback> dataCallbackOwner_{};
    std::shared_ptr<AudioStreamErrorCallback> errorCallbackOwner_{};
};

class AudioStreamBuilder {
public:
    AudioStreamBuilder& setDirection(Direction direction);
    AudioStreamBuilder& setPerformanceMode(PerformanceMode mode);
    AudioStreamBuilder& setSharingMode(SharingMode mode);
    AudioStreamBuilder& setFormat(AudioFormat format);
    AudioStreamBuilder& setChannelCount(std::int32_t channelCount);
    AudioStreamBuilder& setSampleRate(std::int32_t sampleRate);
    AudioStreamBuilder& setDataCallback(std::shared_ptr<AudioStreamDataCallback> callback);
    AudioStreamBuilder& setDataCallback(AudioStreamDataCallback* callback);
    AudioStreamBuilder& setErrorCallback(std::shared_ptr<AudioStreamErrorCallback> callback);
    AudioStreamBuilder& setErrorCallback(AudioStreamErrorCallback* callback);
    Result openStream(std::shared_ptr<AudioStream>& stream);
};

}  // namespace oboe
