#!/usr/bin/env bash
# Build a standalone API18 launcher. No Gradle or network dependencies during the build.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WITH_BRIDGE=0
case "${1:-}" in
  '') ;;
  --with-bridge) WITH_BRIDGE=1 ;;
  *) echo 'Usage: ./build.sh [--with-bridge]' >&2; exit 2 ;;
esac
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
BT="${BUILD_TOOLS:-$SDK/build-tools/36.0.0}"
PLATFORM="${PLATFORM_JAR:-$SDK/platforms/android-18/android.jar}"
if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
elif [ -x '/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/javac' ]; then
  export PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH"
fi
for command in java javac keytool zip; do
  command -v "$command" >/dev/null || { echo "Missing $command (JDK 17+ and zip required)" >&2; exit 1; }
done
for command in aapt d8 zipalign apksigner; do
  [ -x "$BT/$command" ] || { echo "Missing $BT/$command" >&2; exit 1; }
done
[ -f "$PLATFORM" ] || { echo "Missing API18 platform: $PLATFORM" >&2; exit 1; }
BUILD="$ROOT/build"
OUT="${OUT_DIR:-$ROOT/dist}"
KEY="${KEYSTORE_PATH:-$ROOT/local/development.p12}"
ALIAS="${KEY_ALIAS:-aerial}"
PASSWORD="${KEYSTORE_PASSWORD:-android}"
export AERIAL_SIGNING_PASSWORD="$PASSWORD"
mkdir -p "$BUILD" "$OUT"
if [ ! -f "$KEY" ]; then
  if [ -n "${KEYSTORE_PATH:-}" ]; then
    echo "Configured signing key does not exist: $KEY" >&2; exit 1
  fi
  mkdir -p "$ROOT/local"
  keytool -genkeypair -keystore "$KEY" -storetype PKCS12 -alias "$ALIAS" -keyalg RSA \
    -keysize 2048 -validity 10000 -storepass:env AERIAL_SIGNING_PASSWORD \
    -keypass:env AERIAL_SIGNING_PASSWORD -dname 'CN=Aerial TV Legacy development' >/dev/null
fi
build_app() {
  local name="$1" source="$2" output="$3" work="$BUILD/$1"
  rm -rf "$work"
  mkdir -p "$work/classes" "$work/dex" "$work/gen" "$work/empty-assets"
  local assets="$source/assets"
  if [ ! -d "$assets" ]; then assets="$work/empty-assets"; fi
  "$BT/aapt" package -f -A "$assets" -M "$source/AndroidManifest.xml" -S "$source/res" \
    -I "$PLATFORM" -F "$work/base.apk" -J "$work/gen" --min-sdk-version 18 --target-sdk-version 18
  find "$source/src" "$work/gen" -name '*.java' -print > "$work/sources.txt"
  javac -source 8 -target 8 -encoding UTF-8 -bootclasspath "$PLATFORM" \
    -d "$work/classes" @"$work/sources.txt"
  find "$work/classes" -name '*.class' -print > "$work/classes.txt"
  "$BT/d8" --release --min-api 18 --lib "$PLATFORM" --output "$work/dex" @"$work/classes.txt"
  (cd "$work/dex" && zip -q -X "$work/base.apk" classes.dex)
  "$BT/zipalign" -f -p 4 "$work/base.apk" "$work/aligned.apk"
  "$BT/apksigner" sign --ks "$KEY" --ks-key-alias "$ALIAS" \
    --ks-pass env:AERIAL_SIGNING_PASSWORD --key-pass env:AERIAL_SIGNING_PASSWORD \
    --min-sdk-version 18 --v1-signing-enabled true --v2-signing-enabled false \
    --v3-signing-enabled false --out "$OUT/$output" "$work/aligned.apk"
  "$BT/apksigner" verify --verbose "$OUT/$output"
  "$BT/aapt" dump badging "$OUT/$output" | sed -n '1,3p'
  echo "Built $OUT/$output"
}
build_app aerial "$ROOT/app" AerialLegacy.apk
if [ "$WITH_BRIDGE" = 1 ]; then build_app bridge "$ROOT/extras/t2launcher" T2launcher.apk; fi
python3 - "$OUT" <<'PY'
from pathlib import Path
import hashlib,sys
root=Path(sys.argv[1]);files=sorted(root.glob('*.apk'))
(root/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in files))
PY
