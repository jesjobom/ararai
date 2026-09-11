cmake_minimum_required(VERSION 3.22.1)

project(ararai_quickjs_host_tests LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

option(ARARAI_ENABLE_SANITIZERS "Build host tests with AddressSanitizer and UBSan" ON)

set(MAIN_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp")
set(QUICKJS_DIR "${MAIN_CPP_DIR}/quickjs")
set(
    QUICKJS_SOURCES
    "${QUICKJS_DIR}/quickjs.c"
    "${QUICKJS_DIR}/dtoa.c"
    "${QUICKJS_DIR}/libregexp.c"
    "${QUICKJS_DIR}/libunicode.c"
    "${QUICKJS_DIR}/cutils.c"
)

add_library(
    ararai_quickjs_host_core
    STATIC
    "${MAIN_CPP_DIR}/sandbox_core.cpp"
    ${QUICKJS_SOURCES}
)

target_compile_definitions(
    ararai_quickjs_host_core
    PRIVATE
        CONFIG_VERSION="2026-06-04"
        _GNU_SOURCE
)
target_include_directories(
    ararai_quickjs_host_core
    PUBLIC "${MAIN_CPP_DIR}"
    PRIVATE "${QUICKJS_DIR}"
)

find_package(Threads REQUIRED)

add_executable(quickjs_sandbox_host_tests sandbox_core_test.cpp)
target_link_libraries(
    quickjs_sandbox_host_tests
    PRIVATE ararai_quickjs_host_core Threads::Threads m
)

if(ARARAI_ENABLE_SANITIZERS)
    foreach(target ararai_quickjs_host_core quickjs_sandbox_host_tests)
        target_compile_options(
            ${target}
            PRIVATE -fsanitize=address,undefined -fno-omit-frame-pointer
        )
        target_link_options(
            ${target}
            PRIVATE -fsanitize=address,undefined -fno-omit-frame-pointer
        )
    endforeach()
    if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
        # Debian enables PIE by default. Combined with container ASLR, that can
        # intermittently collide with ASan's shadow mapping before main().
        # Non-PIE applies only to this host-test executable, never Android code.
        foreach(target ararai_quickjs_host_core quickjs_sandbox_host_tests)
            target_compile_options(${target} PRIVATE -fno-pie)
        endforeach()
        target_link_options(quickjs_sandbox_host_tests PRIVATE -no-pie)
    endif()
    # QuickJS intentionally uses low-level signed bit operations that UBSan
    # reports even though they are part of the vendored engine implementation.
    # Keep ASan over the engine and UBSan over the project-owned C++ boundary.
    set_source_files_properties(
        ${QUICKJS_SOURCES}
        PROPERTIES COMPILE_OPTIONS -fno-sanitize=undefined
    )
endif()

enable_testing()
add_test(NAME quickjs_sandbox_host_tests COMMAND quickjs_sandbox_host_tests)
set_tests_properties(quickjs_sandbox_host_tests PROPERTIES TIMEOUT 30)
