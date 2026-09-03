#include "AudioEngine.h"

#include <jni.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <memory>
#include <new>
#include <utility>

namespace {

using EngineHandle = std::shared_ptr<intervaltablet::AudioEngine>;

EngineHandle* fromHandle(const jlong handle) noexcept {
    return reinterpret_cast<EngineHandle*>(static_cast<std::intptr_t>(handle));
}

jlong toHandle(EngineHandle* handle) noexcept {
    return static_cast<jlong>(reinterpret_cast<std::intptr_t>(handle));
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_dev_intervaltablet_audio_NativeAudioEngine_nativeCreate(JNIEnv*, jobject) {
    try {
        auto engine = intervaltablet::AudioEngine::create();
        auto* handle = new (std::nothrow) EngineHandle(std::move(engine));
        return handle == nullptr ? 0 : toHandle(handle);
    } catch (...) {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_intervaltablet_audio_NativeAudioEngine_nativeDestroy(JNIEnv*, jobject, const jlong handle) {
    std::unique_ptr<EngineHandle> ownedHandle(fromHandle(handle));
    if (ownedHandle && *ownedHandle) (*ownedHandle)->shutdown();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_intervaltablet_audio_NativeAudioEngine_nativeStart(JNIEnv*, jobject, const jlong handle) {
    const auto* ownedHandle = fromHandle(handle);
    if (ownedHandle == nullptr || !*ownedHandle) return JNI_FALSE;
    const auto engine = *ownedHandle;
    try {
        return engine->start() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_intervaltablet_audio_NativeAudioEngine_nativeStop(JNIEnv*, jobject, const jlong handle) {
    const auto* ownedHandle = fromHandle(handle);
    if (ownedHandle == nullptr || !*ownedHandle) return;
    try {
        (*ownedHandle)->stop();
    } catch (...) {
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_intervaltablet_audio_NativeAudioEngine_nativeNoteOn(
    JNIEnv*, jobject, const jlong handle, const jint note, const jint velocity) {
    const auto* ownedHandle = fromHandle(handle);
    if (ownedHandle == nullptr || !*ownedHandle) return JNI_FALSE;
    const auto engine = *ownedHandle;
    const jint boundedNote = std::clamp(note, static_cast<jint>(0), static_cast<jint>(127));
    const jint boundedVelocity = std::clamp(velocity, static_cast<jint>(0), static_cast<jint>(127));
    const intervaltablet::AudioEvent event{
        intervaltablet::AudioEventType::NoteOn,
        static_cast<std::int32_t>(boundedNote),
        static_cast<float>(boundedVelocity),
    };
    return engine->enqueue(event) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_intervaltablet_audio_NativeAudioEngine_nativeNoteOff(
    JNIEnv*, jobject, const jlong handle, const jint note) {
    const auto* ownedHandle = fromHandle(handle);
    if (ownedHandle == nullptr || !*ownedHandle) return JNI_FALSE;
    const auto engine = *ownedHandle;
    const jint boundedNote = std::clamp(note, static_cast<jint>(0), static_cast<jint>(127));
    const intervaltablet::AudioEvent event{
        intervaltablet::AudioEventType::NoteOff,
        static_cast<std::int32_t>(boundedNote),
        0.0F,
    };
    return engine->enqueue(event) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_intervaltablet_audio_NativeAudioEngine_nativePanic(JNIEnv*, jobject, const jlong handle) {
    const auto* ownedHandle = fromHandle(handle);
    if (ownedHandle == nullptr || !*ownedHandle) return JNI_FALSE;
    const auto engine = *ownedHandle;
    return engine->enqueue({intervaltablet::AudioEventType::Panic, 0, 0.0F}) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_intervaltablet_audio_NativeAudioEngine_nativeSetParameter(
    JNIEnv*, jobject, const jlong handle, const jint parameterId, const jfloat value) {
    const auto* ownedHandle = fromHandle(handle);
    if (ownedHandle == nullptr || !*ownedHandle) return JNI_FALSE;
    if (parameterId < static_cast<jint>(intervaltablet::dsp::ParameterId::SawMix) ||
        parameterId > static_cast<jint>(intervaltablet::dsp::ParameterId::TempoBpm) ||
        !std::isfinite(static_cast<float>(value))) {
        return JNI_FALSE;
    }
    const auto engine = *ownedHandle;
    return engine->enqueue({
        intervaltablet::AudioEventType::Parameter,
        static_cast<std::int32_t>(parameterId),
        static_cast<float>(value),
    }) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_dev_intervaltablet_audio_NativeAudioEngine_nativeDiagnostics(
    JNIEnv* env, jobject, const jlong handle) {
    const auto* ownedHandle = fromHandle(handle);
    intervaltablet::AudioEngine::Diagnostics rawValues{};
    if (ownedHandle != nullptr && *ownedHandle) {
        const auto engine = *ownedHandle;
        try {
            rawValues = engine->diagnostics();
        } catch (...) {
            rawValues.fill(0);
        }
    }
    std::array<jint, intervaltablet::AudioEngine::kDiagnosticCount> values{};
    std::transform(rawValues.begin(), rawValues.end(), values.begin(), [](const int value) {
        return static_cast<jint>(value);
    });
    jintArray output = env->NewIntArray(static_cast<jsize>(values.size()));
    if (output != nullptr) {
        env->SetIntArrayRegion(output, 0, static_cast<jsize>(values.size()), values.data());
    }
    return output;
}
