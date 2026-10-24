# Changelog

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
