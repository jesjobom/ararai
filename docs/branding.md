# ArarAI visual identity

The checked-in Android and README images are optimized derivatives of artwork
reviewed in `/home/node/.openclaw/jarvis/artifacts/ararai` on 2026-08-08. That
external directory is not a build input.

## Baseline

- Brand background: `#010923`
- Compact color symbol: `logo_small_transparency.png`
- Transparent wordmark: `logo_title_transparency.png`
- Opaque README banner: `logo_title.png`

The launcher foreground is padded for Android adaptive-icon masks. The splash
symbol uses its own larger transparent safe area because Android 12+ applies a
smaller circular mask than the launcher. Legacy icons are precomposed on the
brand background. The Home wordmark is cropped to its horizontal content and
displayed on the brand background. The README uses an opaque WebP banner so its
contrast does not depend on the GitHub theme.

Regenerate the checked-in derivatives with:

```bash
scripts/generate-brand-assets.sh /path/to/reviewed/ararai-artwork
```

The script validates the three expected source filenames and uses `ffmpeg` for
deterministic sizing, padding, composition, and WebP conversion.

The current light/dark artwork is not used as an Android monochrome or
notification icon. Those platform assets use alpha as a mask; future artwork
must express internal detail as transparent cutouts rather than opaque dark
lines, and must omit glow, shadows, and gradients.

When replacing artwork, regenerate all density variants, visually check common
adaptive masks and small launcher sizes, and validate the splash on both sides
of the Android 12 platform boundary.
