cmake_minimum_required(VERSION 3.22.1)

project(ararai_whisper)

include(FetchContent)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)

# Android debug variants normally compile native code without optimization.
# Candidate benchmarks must measure the runtime rather than an -O0 build while
# retaining Java/Kotlin debug tooling in the test APK.
add_compile_options(-O3 -DNDEBUG)
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

FetchContent_Declare(
    whisper_cpp
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG f049fff95a089aa9969deb009cdd4892b3e74916
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(whisper_cpp)

add_library(ararai_whisper SHARED ararai_whisper.cpp)

target_compile_features(ararai_whisper PRIVATE cxx_std_17)

target_link_libraries(
    ararai_whisper
    PRIVATE
        whisper
        android
        log
)
