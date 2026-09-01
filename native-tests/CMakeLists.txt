cmake_minimum_required(VERSION 3.22)
project(interval_tablet_native_tests LANGUAGES CXX)

enable_testing()
find_package(Threads REQUIRED)

set(INTERVAL_STRICT_WARNINGS
    -Wall
    -Wextra
    -Wpedantic
    -Werror
    -Wconversion
    -Wshadow
)

add_executable(dsp_smoke_tests dsp_smoke_test.cpp)
target_compile_features(dsp_smoke_tests PRIVATE cxx_std_20)
target_compile_options(dsp_smoke_tests PRIVATE ${INTERVAL_STRICT_WARNINGS})
target_compile_definitions(dsp_smoke_tests PRIVATE INTERVAL_NATIVE_TESTING=1)
target_include_directories(dsp_smoke_tests PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/cpp
)
add_test(NAME dsp_smoke_tests COMMAND dsp_smoke_tests)

add_executable(audio_engine_tests
    audio_engine_test.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/cpp/AudioEngine.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/cpp/native_bridge.cpp
)
target_compile_features(audio_engine_tests PRIVATE cxx_std_20)
target_compile_options(audio_engine_tests PRIVATE ${INTERVAL_STRICT_WARNINGS})
target_compile_definitions(audio_engine_tests PRIVATE INTERVAL_NATIVE_TESTING=1)
target_include_directories(audio_engine_tests PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/stubs
    ${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/cpp
)
target_link_libraries(audio_engine_tests PRIVATE Threads::Threads)
add_test(NAME audio_engine_tests COMMAND audio_engine_tests)

# Compile the Android-facing native bridge against narrow host stubs. This does not
# replace an Android/Oboe build; it catches C++ syntax, ownership and conversion issues
# in AudioEngine.cpp/native_bridge.cpp without requiring the Android SDK.
add_library(native_bridge_syntax OBJECT
    ${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/cpp/AudioEngine.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/cpp/native_bridge.cpp
)
target_compile_features(native_bridge_syntax PRIVATE cxx_std_20)
target_compile_options(native_bridge_syntax PRIVATE ${INTERVAL_STRICT_WARNINGS})
target_include_directories(native_bridge_syntax PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/stubs
    ${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/cpp
)
