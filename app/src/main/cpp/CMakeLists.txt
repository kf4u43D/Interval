cmake_minimum_required(VERSION 3.22.1)
project(interval_audio LANGUAGES CXX)

find_package(oboe REQUIRED CONFIG)

add_library(interval_audio SHARED
    AudioEngine.cpp
    native_bridge.cpp
)

target_compile_features(interval_audio PRIVATE cxx_std_20)
target_compile_options(interval_audio PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -Wconversion
    -Wshadow
)
target_include_directories(interval_audio PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(interval_audio PRIVATE oboe::oboe android log)
