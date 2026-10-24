#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
elif [ -x '/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/javac' ]; then
  export PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH"
fi
TEMP_BUILD="$(mktemp -d)"
trap 'rm -rf "$TEMP_BUILD"' EXIT
javac -d "$TEMP_BUILD" "$HERE/../app/src/app/aerial/legacy/DesktopLayout.java" "$HERE/DesktopLayoutTest.java"
java -ea -cp "$TEMP_BUILD" app.aerial.legacy.DesktopLayoutTest

ROOT="$(cd "$HERE/.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
CP="$SDK/platforms/android-18/android.jar:$HERE/lib/json-20240303.jar:$ROOT/third_party/conscrypt/classes.jar"
javac -cp "$CP" -d "$TEMP_BUILD" "$HERE"/stubs/android/content/Context.java \
  "$HERE"/stubs/android/os/Handler.java "$HERE"/stubs/android/util/Log.java \
  "$HERE"/stubs/app/aerial/legacy/R.java "$ROOT"/app/src/app/aerial/legacy/{WeatherData,CityCatalog,WeatherApi,WeatherStore,LegacyHttp}.java "$HERE/WeatherTest.java"
java -ea -cp "$TEMP_BUILD:$HERE/lib/json-20240303.jar:$SDK/platforms/android-18/android.jar:$ROOT/third_party/conscrypt/classes.jar" app.aerial.legacy.WeatherTest "$ROOT/app/assets" "${1:-}"
