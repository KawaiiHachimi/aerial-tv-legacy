# Changelog

## 1.5.2-legacy18 — 2026-10-30

- Remove Picture from Control Center and compact the remaining rows.
- Make Sound a non-focusable volume indicator that refreshes on system volume changes; remove its settings action and left/right volume adjustment.
- Remove unused picture translations, settings fallbacks and the obsolete device-test volume adjustment.

## 1.5.1-legacy18 — 2026-10-25

- Enable weather and IP-based city lookup by default on a fresh configuration; cache location for a day, preserve manual-city/off preferences and allow returning to automatic location from phone setup.
- Add host-scoped Google Trust Services roots for ipwho.is; keep normal HTTPS verification and avoid requesting/logging the raw IP address.
- Convert missing/failed Conscrypt native initialization into a recoverable network error instead of crashing the wallpaper worker.
- Document API18 preinstalled JNI placement and add a bounded root setup-completion script for dedicated TV firmware that removed its original setup application; unfinished provisioning can suppress HOME.
- Keep EUI's main desktop on Aerial and report hidden state when another app opens. Route network and inputs to their EUI panels; picture/sound fall back to the same eight-item settings panel because their dialogs are private.
- Add automatic-location/manual-override/cache tests and a headless API18 TLS probe that validates /system/lib lookup without an APK update.

## 1.5.0-legacy18 — 2026-10-24

- Port Open-Meteo weather: manual city selection, current temperature, day/night glyphs and daily high/low in both Home clock layouts.
- Reuse Aerial's GeoNames Chinese city catalog, with online geocoding for other queries; add weather controls to the token-protected phone page.
- Refresh hourly while Home is visible, back off after failures and retain clearly marked cached readings for up to three hours. Discard late responses after a city change.
- Share the API18 HTTPS client with Bing, retaining host-scoped CA roots and certificate/hostname verification; add ISRG X1/X2 roots for weather.
- Keep original Aerial solid system-card colors. Add host tests for parsing/cache/lifecycle races and a native API18 HTTPS/phone/Canvas integration test.

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
