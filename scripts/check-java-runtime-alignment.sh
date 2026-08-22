#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

require_text() {
    local file="$1"
    local expected="$2"
    if ! grep -Fq -- "$expected" "$file"; then
        echo "Java runtime alignment check failed: '$file' must contain '$expected'." >&2
        exit 1
    fi
}

require_text .github/workflows/android-quality-gate.yml "name: Set up Firebase JDK 21"
require_text .github/workflows/android-quality-gate.yml "name: Preserve Firebase JDK 21"
require_text .github/workflows/android-quality-gate.yml 'echo "FIREBASE_JAVA_HOME=$JAVA_HOME" >> "$GITHUB_ENV"'
require_text .github/workflows/android-quality-gate.yml "name: Set up Android JDK 17"

firebase_line="$(grep -nF "name: Set up Firebase JDK 21" .github/workflows/android-quality-gate.yml | cut -d: -f1)"
android_line="$(grep -nF "name: Set up Android JDK 17" .github/workflows/android-quality-gate.yml | cut -d: -f1)"
firebase_version="$(sed -n "$((firebase_line + 4))p" .github/workflows/android-quality-gate.yml)"
android_version="$(sed -n "$((android_line + 4))p" .github/workflows/android-quality-gate.yml)"

[[ "$firebase_version" == *'java-version: "21"'* ]] || {
    echo "Java runtime alignment check failed: Firebase setup must install Java 21." >&2
    exit 1
}
[[ "$android_version" == *'java-version: "17"'* ]] || {
    echo "Java runtime alignment check failed: Android setup must install Java 17." >&2
    exit 1
}
(( firebase_line < android_line )) || {
    echo "Java runtime alignment check failed: Android JDK 17 must be the final active CI runtime." >&2
    exit 1
}

require_text README.md "Gradle/AGP builds require JDK 17; Firebase Emulator tests require a full JDK 21."
require_text docs/quality-gates.md "The canonical Android Gradle runtime is Temurin JDK 17."
require_text openspec/project.md "Canonical Android Gradle runtime: JDK 17; Firebase Emulator runtime: JDK 21"

echo "Java runtime declarations are aligned: Android JDK 17, Firebase JDK 21."
