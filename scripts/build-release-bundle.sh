#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
jarvis_root="$(cd "$repo_root/../.." && pwd)"
signing_environment="$jarvis_root/secrets/ararai/release-signing.env"
source_aab="$repo_root/app/build/outputs/bundle/release/app-release.aab"
target_dir="$jarvis_root/artifacts/ararai"

version_code="${ARARAI_VERSION_CODE:-$(( $(date +%s) / 60 ))}"
if [[ ! "$version_code" =~ ^[0-9]+$ ]] || (( version_code < 1 || version_code > 2100000000 )); then
  echo "ARARAI_VERSION_CODE must be an integer from 1 to 2100000000." >&2
  exit 1
fi
export ARARAI_VERSION_CODE="$version_code"

target_aab="$target_dir/ararai-release-vc${version_code}.aab"
target_diagnostics="$target_dir/release-diagnostics-vc${version_code}"

java_major="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.version = \([0-9][0-9]*\).*/\1/p' | head -1)"
if [[ "$java_major" != "17" ]]; then
  echo "Release assembly requires the canonical JDK 17 runtime; found Java ${java_major:-unknown}." >&2
  exit 1
fi

if [[ ! -f "$signing_environment" ]]; then
  echo "Release-signing environment not found: ../../secrets/ararai/release-signing.env" >&2
  exit 1
fi

signing_environment_mode="$(stat -c '%a' "$signing_environment")"
if (( (8#$signing_environment_mode & 8#077) != 0 )); then
  echo "Release-signing environment must not be accessible by group or others." >&2
  exit 1
fi

# The external file contains only exports. It is deliberately kept outside Git.
# shellcheck disable=SC1090
source "$signing_environment"

required_variables=(
  ARARAI_UPLOAD_STORE_FILE
  ARARAI_UPLOAD_STORE_PASSWORD_FILE
  ARARAI_UPLOAD_KEY_ALIAS
  ARARAI_UPLOAD_KEY_PASSWORD_FILE
)

for variable_name in "${required_variables[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "Missing release-signing variable: $variable_name" >&2
    exit 1
  fi
done

required_secret_files=(
  "$ARARAI_UPLOAD_STORE_FILE"
  "$ARARAI_UPLOAD_STORE_PASSWORD_FILE"
  "$ARARAI_UPLOAD_KEY_PASSWORD_FILE"
)

for secret_file in "${required_secret_files[@]}"; do
  if [[ ! -f "$secret_file" ]]; then
    echo "Release-signing file does not exist: $secret_file" >&2
    exit 1
  fi

  secret_mode="$(stat -c '%a' "$secret_file")"
  if (( (8#$secret_mode & 8#077) != 0 )); then
    echo "Release-signing file must not be accessible by group or others: $secret_file" >&2
    exit 1
  fi
done

cd "$repo_root"
./gradlew bundleRelease
scripts/verify-release-artifacts.sh release

if [[ ! -f "$source_aab" ]]; then
  echo "Release AAB was not produced: $source_aab" >&2
  exit 1
fi

signature_verification="$(jarsigner -verify "$source_aab" 2>&1)"
if ! grep -q '^jar verified\.$' <<<"$signature_verification"; then
  echo "$signature_verification" >&2
  echo "Release AAB signature verification failed." >&2
  exit 1
fi
echo "Release AAB signature verified."

mkdir -p "$target_dir"
cp "$source_aab" "$target_aab"
mkdir -p "$target_diagnostics"
cp app/build/outputs/mapping/release/{mapping,seeds,usage,configuration}.txt "$target_diagnostics/"

echo "Release AAB: $target_aab"
sha256sum "$target_aab"
sha256sum "$target_diagnostics"/*.txt
keytool \
  -list \
  -v \
  -alias "$ARARAI_UPLOAD_KEY_ALIAS" \
  -keystore "$ARARAI_UPLOAD_STORE_FILE" \
  -storepass:file "$ARARAI_UPLOAD_STORE_PASSWORD_FILE" \
  | grep -E 'SHA256:|Valid from:'
