# Verification

For version 1.3-legacy18 (2026-10-24):

- Compiled against API18 android.jar, min/target SDK18, DEX035, v1 APK signature.
- Layout model: 2,000 randomized operations, 70,658 invariant assertions passed.
- API18 x86 emulator: four instrumentation test cases passed, covering typography/CJK rendering,
  animation completion, organization and persistence, remote-key dispatch, and the explicit input
  route plus compatibility Messenger bind/send/unbind contract.
- The presentation test additionally waits for wallpaper/glass preparation before rendering.
- README images are native 1920x1080 emulator screen captures.

No hardware HDMI/source-switch, physical remote, real MStar GPU or physical-TV ADB test is implied.
The APK does not enable network ADB. That is a separate firmware-level integration.

## 1.3.1 visual adjustment

The control-center header now uses the same visible left edge as the controls below.
The APK was rebuilt against API18, host layout tests passed, and the updated native emulator
screenshot was visually checked. This layout-only revision does not imply new hardware testing.
