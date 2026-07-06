#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_apk="$repo_root/app/build/outputs/apk/debug/app-debug.apk"
target_apk="/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk"

if [[ ! -f "$source_apk" ]]; then
  echo "Debug APK not found. Run ./gradlew assembleDebug first." >&2
  exit 1
fi

mkdir -p "$(dirname "$target_apk")"
cp "$source_apk" "$target_apk"
echo "$target_apk"
