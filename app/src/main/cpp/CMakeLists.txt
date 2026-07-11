cmake_minimum_required(VERSION 3.22.1)

project(ararai_llama)

include(FetchContent)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN ON CACHE BOOL "" FORCE)
set(SPIRV-Headers_DIR "${CMAKE_CURRENT_LIST_DIR}/cmake/SPIRV-Headers" CACHE PATH "" FORCE)
set(
    Vulkan_GLSLC_EXECUTABLE
    "${CMAKE_ANDROID_NDK}/shader-tools/linux-x86_64/glslc"
    CACHE FILEPATH "" FORCE
)
if (DEFINED ENV{ARARAI_HOST_CC} AND DEFINED ENV{ARARAI_HOST_CXX})
    set(
        GGML_VULKAN_SHADERS_GEN_TOOLCHAIN
        "${CMAKE_CURRENT_LIST_DIR}/cmake/host-toolchain-env.cmake"
        CACHE FILEPATH "" FORCE
    )
endif()

FetchContent_Declare(
    vulkan_headers
    GIT_REPOSITORY https://github.com/KhronosGroup/Vulkan-Headers.git
    GIT_TAG vulkan-sdk-1.3.275.0
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(vulkan_headers)
include_directories(SYSTEM "${vulkan_headers_SOURCE_DIR}/include")
include_directories(
    SYSTEM
    "${CMAKE_ANDROID_NDK}/sources/third_party/shaderc/third_party/spirv-tools/external/spirv-headers/include"
)

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG 1d1d9a9ed7a4f09c4225ea4cc8fd3bd1cf2c940f
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(llama_cpp)

add_library(ararai_llama SHARED ararai_llama.cpp)

target_compile_features(ararai_llama PRIVATE cxx_std_17)

target_link_libraries(
    ararai_llama
    PRIVATE
        llama
        android
        log
)
