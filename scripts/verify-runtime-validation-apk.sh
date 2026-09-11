#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
apk_path="${1:-$repo_root/app/build/outputs/apk/releaseCandidate/app-releaseCandidate.apk}"

if [[ ! -f "$apk_path" ]]; then
    echo "Runtime validation APK not found: $apk_path" >&2
    exit 1
fi

apkanalyzer_bin="$(command -v apkanalyzer || true)"
if [[ -z "$apkanalyzer_bin" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
    candidate="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/apkanalyzer"
    [[ -x "$candidate" ]] && apkanalyzer_bin="$candidate"
fi
if [[ -z "$apkanalyzer_bin" ]]; then
    echo "apkanalyzer is required to verify the runtime validation APK." >&2
    exit 1
fi

manifest="$($apkanalyzer_bin manifest print "$apk_path")"
if ! grep -q 'com.jesjobom.ararai.validation.RuntimeValidationActivity' <<<"$manifest"; then
    echo "Runtime validation launcher activity is missing from $apk_path" >&2
    exit 1
fi

if grep -R -q 'RuntimeValidationActivity' "$repo_root/app/src/main" "$repo_root/app/src/release"; then
    echo "Runtime validation launcher leaked into a production source set." >&2
    exit 1
fi

mapfile -t quickjs_entries < <(zipinfo -1 "$apk_path" | grep -E '^lib/[^/]+/libararai_quickjs\.so$' || true)
if [[ "${#quickjs_entries[@]}" -ne 1 || "${quickjs_entries[0]:-}" != "lib/arm64-v8a/libararai_quickjs.so" ]]; then
    echo "Expected exactly one arm64 QuickJS library in $apk_path; found: ${quickjs_entries[*]:-none}" >&2
    exit 1
fi

echo "Runtime validation APK verified:"
echo "path=$apk_path"
echo "bytes=$(stat -c '%s' "$apk_path")"
echo "sha256=$(sha256sum "$apk_path" | awk '{print $1}')"
echo "quickjs_entry=${quickjs_entries[0]}"
