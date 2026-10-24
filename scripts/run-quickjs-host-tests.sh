#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_dir="$repo_root/quickjs-runtime/src/hostTest/cpp"
host_cmake="${ARARAI_HOST_CMAKE:-cmake}"
host_ctest="${ARARAI_HOST_CTEST:-ctest}"
host_cc="${ARARAI_HOST_CC:-cc}"
host_cxx="${ARARAI_HOST_CXX:-c++}"

run_host_tests() {
    local build_dir="$1"
    local build_type="$2"
    local sanitizers="$3"

    "$host_cmake" \
        -S "$source_dir" \
        -B "$build_dir" \
        -DCMAKE_BUILD_TYPE="$build_type" \
        -DCMAKE_C_COMPILER="$host_cc" \
        -DCMAKE_CXX_COMPILER="$host_cxx" \
        -DARARAI_ENABLE_SANITIZERS="$sanitizers"
    "$host_cmake" --build "$build_dir" --parallel 2
    "$host_ctest" --test-dir "$build_dir" --output-on-failure
}

ASAN_OPTIONS="detect_leaks=1:halt_on_error=1:strict_string_checks=1" \
UBSAN_OPTIONS="halt_on_error=1:print_stacktrace=1" \
    run_host_tests "$repo_root/build/quickjs-host-tests" Debug ON

run_host_tests "$repo_root/build/quickjs-host-tests-release" Release OFF
