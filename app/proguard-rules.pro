# JNI entry points are reached from Kotlin and must keep their native declarations.
-keepclasseswithmembernames class dev.intervaltablet.audio.NativeAudioEngine {
    native <methods>;
}
