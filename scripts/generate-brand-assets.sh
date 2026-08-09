#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <directory-containing-reviewed-ararai-pngs>" >&2
  exit 2
fi

brand_source_dir=$1
project_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
compact_symbol="$brand_source_dir/logo_small_transparency.png"
transparent_wordmark="$brand_source_dir/logo_title_transparency.png"
opaque_banner="$brand_source_dir/logo_title.png"

for source_asset in "$compact_symbol" "$transparent_wordmark" "$opaque_banner"; do
  if [[ ! -f "$source_asset" ]]; then
    echo "Missing reviewed source asset: $source_asset" >&2
    exit 1
  fi
done

command -v ffmpeg >/dev/null || {
  echo "ffmpeg is required to generate brand assets" >&2
  exit 1
}

drawable_dir="$project_dir/app/src/main/res/drawable-nodpi"
docs_image_dir="$project_dir/docs/images"
mkdir -p "$drawable_dir" "$docs_image_dir"

ffmpeg -loglevel error -y \
  -f lavfi -i "color=c=black@0.0:s=432x432,format=rgba" \
  -i "$compact_symbol" \
  -filter_complex \
  "[1:v]scale=360:360:flags=lanczos[logo];[0:v][logo]overlay=(W-w)/2:(H-h)/2:format=auto" \
  -frames:v 1 "$drawable_dir/ararai_launcher_foreground.png"

ffmpeg -loglevel error -y \
  -f lavfi -i "color=c=black@0.0:s=512x512,format=rgba" \
  -i "$compact_symbol" \
  -filter_complex \
  "[1:v]scale=400:400:flags=lanczos[logo];[0:v][logo]overlay=(W-w)/2:(H-h)/2:format=auto" \
  -frames:v 1 "$drawable_dir/ararai_splash_symbol.png"

ffmpeg -loglevel error -y -i "$transparent_wordmark" \
  -vf "crop=1536:512:0:256,scale=960:320:flags=lanczos" -frames:v 1 \
  "$drawable_dir/ararai_wordmark.png"

for density_and_size in "mdpi 48" "hdpi 72" "xhdpi 96" "xxhdpi 144" "xxxhdpi 192"; do
  read -r density size <<<"$density_and_size"
  logo_size=$((size * 84 / 100))
  mipmap_dir="$project_dir/app/src/main/res/mipmap-$density"
  mkdir -p "$mipmap_dir"
  ffmpeg -loglevel error -y \
    -f lavfi -i "color=c=#010923:s=${size}x${size},format=rgba" \
    -i "$compact_symbol" \
    -filter_complex \
    "[1:v]scale=${logo_size}:${logo_size}:flags=lanczos[logo];[0:v][logo]overlay=(W-w)/2:(H-h)/2:format=auto" \
    -frames:v 1 "$mipmap_dir/ic_launcher.png"
  cp "$mipmap_dir/ic_launcher.png" "$mipmap_dir/ic_launcher_round.png"
done

ffmpeg -loglevel error -y -i "$opaque_banner" \
  -vf "scale=1200:-2:flags=lanczos" -c:v libwebp -quality 82 -frames:v 1 \
  "$docs_image_dir/ararai-banner.webp"
