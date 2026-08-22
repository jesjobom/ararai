#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
variant="${1:-releaseCandidate}"
variant_dir="${variant:0:1}"
variant_dir="${variant_dir,,}${variant:1}"
mapping_dir="$repo_root/app/build/outputs/mapping/$variant_dir"

for artifact in mapping.txt seeds.txt usage.txt configuration.txt; do
    path="$mapping_dir/$artifact"
    if [[ ! -s "$path" ]]; then
        echo "Missing or empty R8 diagnostic artifact: $path" >&2
        exit 1
    fi
done

echo "R8 diagnostic artifacts verified for $variant_dir:"
wc -c "$mapping_dir"/{mapping,seeds,usage,configuration}.txt
