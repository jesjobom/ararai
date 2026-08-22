#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

scripts/check-java-runtime-alignment.sh

gradle_java_major="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.version = \([0-9][0-9]*\).*/\1/p' | head -1)"
if [[ "$gradle_java_major" != "17" ]]; then
    echo "The canonical Android Gradle quality gate requires JDK 17; found Java ${gradle_java_major:-unknown}." >&2
    exit 1
fi

npm ci --include=dev --ignore-scripts
scripts/run-firestore-rules-tests.sh
./gradlew spotlessCheck detekt
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleDebugAndroidTest
./gradlew assembleReleaseCandidate
scripts/verify-release-artifacts.sh releaseCandidate
openspec validate --all --strict
