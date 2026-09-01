#pragma once

#include <cstdint>

#define JNIEXPORT
#define JNICALL
#define JNI_FALSE 0
#define JNI_TRUE 1

using jboolean = unsigned char;
using jint = std::int32_t;
using jlong = std::int64_t;
using jfloat = float;
using jsize = jint;
using jobject = void*;

struct _jintArray;
using jintArray = _jintArray*;

class JNIEnv {
public:
    jintArray NewIntArray(jsize length);
    void SetIntArrayRegion(jintArray array, jsize start, jsize length, const jint* values);
};
