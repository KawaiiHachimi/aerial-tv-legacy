# API18 system-app integration

For a dedicated TV firmware with Aerial preinstalled in `/system/app/AerialLegacy.apk`:

1. Copy the **matching target-ABI** `libconscrypt_jni.so` from `third_party/conscrypt/jni/` into `/system/lib/`. Old Android does not reliably extract JNI libraries from preinstalled system APKs. The APK's embedded ARMv7/x86 libraries remain necessary for ordinary APK installation.
2. Set the APK/library to root:root, mode 0644 and the existing system-file SELinux label. Keep certificate and hostname verification enabled.
3. If the original setup application has been removed, invoke `finish-setup.sh` once in the background from the root vendor init script, after saving it as `/system/etc/aerial-finish-setup.sh` with mode 0755. An unfinished `device_provisioned=0` state can prevent HOME from returning even when the default launcher is correct. This is for the dedicated TV image, not for bypassing setup on general Android devices.

Firmware verification must extract the final image and compare the native library and setup script byte-for-byte with the staged files. Test both a system-preinstalled launch and an APK update; APK installation alone does not validate the preinstalled JNI path.

This repository contains no vendor firmware or device-specific binaries. Keep the original boot, panel configuration and hardware services under the firmware integrator's control.
