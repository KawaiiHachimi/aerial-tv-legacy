#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
BT="${BUILD_TOOLS:-$SDK/build-tools/36.0.0}"
PLATFORM="${PLATFORM_JAR:-$SDK/platforms/android-18/android.jar}"
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"
elif [ -x '/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/javac' ]; then
  export PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH"
fi
TEMP_BUILD="$ROOT/build/instrumentation"
KEY="${KEYSTORE_PATH:-$ROOT/local/development.p12}"
export AERIAL_SIGNING_PASSWORD="${KEYSTORE_PASSWORD:-android}"
mkdir -p "$TEMP_BUILD/classes" "$TEMP_BUILD/dex"
"$BT/aapt" package -f -M "$HERE/android/AndroidManifest.xml" -I "$PLATFORM" -F "$TEMP_BUILD/base.apk"
find "$HERE/android/src" -name '*.java' > "$TEMP_BUILD/sources.txt"
javac -source 8 -target 8 -bootclasspath "$PLATFORM" -cp "$ROOT/build/aerial/classes" -d "$TEMP_BUILD/classes" @"$TEMP_BUILD/sources.txt"
find "$TEMP_BUILD/classes" -name '*.class' -print > "$TEMP_BUILD/classes.txt"
"$BT/d8" --release --min-api 18 --lib "$PLATFORM" --classpath "$ROOT/build/aerial/classes" --output "$TEMP_BUILD/dex" @"$TEMP_BUILD/classes.txt"
(cd "$TEMP_BUILD/dex" && zip -q -X "$TEMP_BUILD/base.apk" classes.dex)
"$BT/zipalign" -f 4 "$TEMP_BUILD/base.apk" "$TEMP_BUILD/aligned.apk"
"$BT/apksigner" sign --ks "$KEY" --ks-key-alias "${KEY_ALIAS:-aerial}" --ks-pass env:AERIAL_SIGNING_PASSWORD \
 --min-sdk-version 18 --v1-signing-enabled true --v2-signing-enabled false --v3-signing-enabled false \
 --out "$ROOT/build/AerialUiTests.apk" "$TEMP_BUILD/aligned.apk"
echo "Built $ROOT/build/AerialUiTests.apk"
