# Apply Visual Identity

## Why

ArarAI still uses the platform default launcher presentation, a plain launch
window, a text-only app name on Home, and a text-only README heading. The
current artifact set now provides a recognizable macaw-and-AI symbol, a
wordmark, and the brand background color `#010923`. The project should import
optimized, repository-owned derivatives of those sources so the installed app
and project documentation present one coherent identity.

The available monochrome and notification candidates still rely on opaque
light/dark detail rather than transparent cutouts. They are useful design
references, but are not reliable Android alpha masks. This change therefore
uses the current artwork where it is technically suitable and keeps mask
refinement explicit follow-up work.

## What Changes

- Add repository-owned, optimized derivatives of the approved ArarAI artwork
  instead of depending at runtime or build time on the external artifact
  directory.
- Add an Android adaptive launcher icon with the compact color symbol as its
  foreground and `#010923` as its background, plus compatible legacy launcher
  resources for supported pre-adaptive launchers.
- Add a branded splash experience with background `#010923` and the compact
  color symbol, using the platform splash API on Android 12+ and a compatible
  launch theme on older supported Android versions.
- Replace the text-only Home app heading with the transparent ArarAI wordmark
  on a stable `#010923` brand surface while retaining an accessible text name
  and the existing version information.
- Add an optimized opaque ArarAI banner near the top of the README so it is
  legible in both light and dark GitHub themes.
- Add focused resource/configuration tests and document the manual visual
  checks required across launcher masks, Android versions, themes, and display
  densities.

## Out Of Scope

- Redesigning or regenerating the supplied logo or wordmark.
- Treating the current black/white candidates as production monochrome or
  notification masks.
- A custom animated splash screen.
- Rebranding individual feature screens, changing the Material color system,
  or replacing dynamic colors throughout the app.
- Store-listing artwork, release signing, screenshots, feature graphics, or
  other distribution assets.

## Source Artwork Baseline

- Compact color symbol: `artifacts/ararai/logo_small_transparency.png`
- Transparent Home wordmark: `artifacts/ararai/logo_title_transparency.png`
- Opaque README banner: `artifacts/ararai/logo_title.png`
- Brand background: `#010923`

The artifact paths identify the reviewed inputs only. Implementation SHALL
check optimized derivatives into the ArarAI repository and SHALL NOT introduce
a build dependency on `/home/node/.openclaw/jarvis/artifacts`.
