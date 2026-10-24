# Changelog

## 1.4.1-legacy18 — 2026-10-24

- Use one generic app-card pipeline: prefer a valid TV banner, otherwise keep the original app icon with consistent sizing and a sampled-color background. No third-party package-specific recipes.
- Read banner resources from the manifest on API18; reject square banners and render artwork at focused resolution.
- Use the midpoint of each original Aerial system-card gradient as a solid background, with the original vector symbols and no added shadows.
- Add cached daily Bing preview in the TV wallpaper picker and phone setup page, with asynchronous refresh.
- Add physical API18 Canvas tests for system glyphs, original icon-color preservation, blank-icon fallback and Bing preview.

## 1.4.0-legacy18 — 2026-10-24

- Add Bing daily wallpaper with API18-compatible HTTPS, verified CA roots, persistent cache and offline fallback.
- Add a token-protected LAN phone setup page, real QR code, builtin previews, photo upload and Home preferences.
- Add three app-name modes and restore selected labels on the first-row glass shelf; migrate the existing preference.
- Display Ethernet, Wi-Fi and offline network glyphs.
- Initialize missing EUI desktop lists; localize the two vendor desktop names and retain package-scoped HOME routing in the optional bridge.
- Add physical API18 TV tests for real image download, crop, invalid upload protection, local HTTP security and name rendering.
- Known EUI limitation: master-volume changes require UID1000; adding the standard audio permission alone does not fix that vendor restriction.

## 1.3.1-legacy18 — 2026-10-24

- Align the control-center date and network heading with the left edge of the controls below.
- Refresh the control-center screenshot.

## 1.3-legacy18 — 2026-10-24

- Extract the Android 4.3 launcher into its own standalone repository.
- Remove added text, tile and icon drop shadows; keep light borders and glass separation.
- Align control-center date/network headings with the lower row labels and shift the top controls.
- Bundle Google Sans Regular/Medium Android static fonts with system CJK fallback.
- Preserve animated focus, press/edge feedback, anchored app menus, folders, hiding and wallpapers.
- Open old LeTV inputs via the explicit MAIN+MONKEY Activity rather than LAUNCHER lookup.
- Optional LeTV bridge retains ordinary package launch lookup and provides the Messenger endpoint
  required by older input-manager activities. This does not emulate TV hardware or title-bar UI.
- Add host model tests, API18 instrumentation, build scripts, screenshots and CI.

Validated on an Android 4.3 x86 emulator; physical TV and vendor hardware remain unverified.
