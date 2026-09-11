cmake_minimum_required(VERSION 3.22.1)

project(ararai_quickjs)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

add_library(
    ararai_quickjs
    SHARED
    quickjs_jni.cpp
    sandbox_core.cpp
    quickjs/quickjs.c
    quickjs/dtoa.c
    quickjs/libregexp.c
    quickjs/libunicode.c
    quickjs/cutils.c
)

target_compile_definitions(
    ararai_quickjs
    PRIVATE
        CONFIG_VERSION="2026-06-04"
        _GNU_SOURCE
)

target_compile_options(
    ararai_quickjs
    PRIVATE
        -O2
        -fstack-protector-strong
        -Wformat
        -Werror=format-security
)

target_include_directories(ararai_quickjs PRIVATE quickjs)
target_link_options(
    ararai_quickjs
    PRIVATE
        -Wl,-z,relro,-z,now
        -Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/exports.map
)
target_link_libraries(ararai_quickjs PRIVATE android log m)
