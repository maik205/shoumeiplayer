# Shoumei Player publishing assets

This directory contains source artwork and publication-ready exports. Keep
editable master files under `brand/source` and place flattened exports in the
corresponding platform directory.

## Layout

- `brand/source/` — editable SVG, Figma exports, fonts, palette, and brand guide
- `android-tv/launcher-icon/` — adaptive icon layers and density exports
- `android-tv/launcher-banner/` — packaged 16:9 launcher banner exports
- `google-play/app-icon/` — 512 x 512 Play Store icon
- `google-play/feature-graphic/` — 1024 x 500 feature graphic
- `google-play/tv-banner/` — 1280 x 720 Play Console TV banner
- `google-play/screenshots/tv/` — 1920 x 1080 TV screenshots
- `google-play/preview-video/` — video source, captions, and thumbnail
- `in-app/splash/` — splash-screen artwork
- `in-app/placeholders/` — poster, backdrop, avatar, and episode placeholders
- `press/` — social preview, repository banner, transparent logos, and press kit

Do not place editable design sources directly in `app/src/main/res`. Approved
Android exports can be copied there once their filenames and resource mappings
are finalized.

`brand/source/app_icon.png` and `brand/source/banner.png` are the authoritative
launcher sources. Run `scripts/export-android-assets.ps1` from PowerShell after
either source changes; it regenerates the Android density resources and the
Google Play icon and TV banner without recomposing the supplied artwork.
