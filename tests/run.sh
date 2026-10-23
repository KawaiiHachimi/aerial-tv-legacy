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
