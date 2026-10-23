# Aerial TV Legacy

This is the independent Android 4.3 / API18 port, not the Compose upstream application.

- Keep builds against the API18 android.jar; preserve DEX 035 and v1 signing.
- Use Java / Canvas and bundled static Google Sans fonts. Missing CJK glyphs use system fallback.
- UI uses a 960x540 design canvas. Keep text and cards mostly shadow-free.
- Keep expensive wallpaper/glass work off the UI thread, preserve tiled drawing for old GPUs,
  and stop animations and idle callbacks when the desktop is hidden.
- Remote menu actions activate only after a matching down/up pair; do not let long-press release
  activate the first item in a newly opened menu.
- Resources live in app/res/values*; keep translations synchronized.
- Preserve upstream attribution and font/icon licenses.
- Do not commit local/, build/, dist/, signing keys, device addresses, proprietary firmware or logs.
- Run ./tests/run.sh and ./build.sh. API18 instrumentation is documented in README.md.
- Distinguish emulator/render evidence from real TV and hardware verification.
