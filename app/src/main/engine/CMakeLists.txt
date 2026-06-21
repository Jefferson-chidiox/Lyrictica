cmake_minimum_required(VERSION 3.22)
project(euphoriae_audio)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Create the audio engine library
add_library(
    audio_engine
    SHARED
    audio_engine.cpp
    jni_bridge.cpp
)

# Include directories
target_include_directories(
    audio_engine
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Link libraries
target_link_libraries(
    audio_engine
    log
    android
)
