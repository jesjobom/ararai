#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${FIREBASE_JAVA_HOME:-}" ]]; then
    echo "FIREBASE_JAVA_HOME must point to a full JDK 21 installation." >&2
    exit 1
fi

firebase_java="$FIREBASE_JAVA_HOME/bin/java"
if [[ ! -x "$firebase_java" ]]; then
    echo "FIREBASE_JAVA_HOME does not contain an executable bin/java: $FIREBASE_JAVA_HOME" >&2
    exit 1
fi

firebase_java_major="$("$firebase_java" -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.version = \([0-9][0-9]*\).*/\1/p' | head -1)"
if [[ "$firebase_java_major" != "21" ]]; then
    echo "Firebase Emulator requires JDK 21; found Java ${firebase_java_major:-unknown}." >&2
    exit 1
fi

PATH="$FIREBASE_JAVA_HOME/bin:$PATH" JAVA_HOME="$FIREBASE_JAVA_HOME" npm run test:firestore-rules:emulator
