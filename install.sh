#!/usr/bin/env bash
# Build Standby Light (or take a release APK), install it on the TV and open it.
#   TV_HOST=<tv ip> ./install.sh
#   TV_HOST=<tv ip> APK=StandbyLight-0.1.0.apk ./install.sh   # a release APK instead of building
# Then, on the TV: Switch off and keep off, and allow the TV's "Allow debugging?" prompt once (Always allow).
# Uses adb. Where macOS blocks the stock adb, set CLEANYOURTV to a clone of github.com/Gathin23/cleanyourtv with
# its tools set up (tools/venv), and its tools/tv.py is used instead.
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
if [ -n "${APK:-}" ]; then
  [ -f "$APK" ] || { echo "no such file: $APK" >&2; exit 1; }
  APK="$(cd "$(dirname "$APK")" && pwd)/$(basename "$APK")"
fi
cd "$(dirname "$0")"
PKG=io.github.maxhayim.standbylight

if [ -n "${CLEANYOURTV:-}" ]; then
  TOOLS="$(cd "$CLEANYOURTV/tools" && pwd)"
  PY=$TOOLS/venv/bin/python
  [ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
  export MSYS_NO_PATHCONV=1
  tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }
  push() { "$PY" -I "$TOOLS/b64push.py" "$1" "$2" >/dev/null; }
else
  ADB="${ADB:-adb}"
  command -v "$ADB" >/dev/null || { echo "adb not found: install Android platform-tools, or set CLEANYOURTV" >&2; exit 1; }
  "$ADB" connect "$TV_HOST:5555" >/dev/null
  tv() { "$ADB" -s "$TV_HOST:5555" shell "$@"; }
  push() { "$ADB" -s "$TV_HOST:5555" push "$1" "$2" >/dev/null; }
fi

# The light it switches: Realtek's LED HAL. Without it this app can do nothing on this TV.
case "$(tv 'service check com.realtek.power.ILedManager/default')" in
  *": found"*) ;;
  *) echo "This TV has no com.realtek.power.ILedManager service, so Standby Light can't switch its light." >&2; exit 1 ;;
esac

if [ -z "${APK:-}" ]; then
  [ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=$ANDROID_HOME" > local.properties; } || true
  if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
  ./gradlew assembleRelease -q --console=plain
  APK=app/build/outputs/apk/release/app-release.apk
fi

push "$APK" /data/local/tmp/standby-light.apk
OUT=$(tv 'pm install -r /data/local/tmp/standby-light.apk; rm -f /data/local/tmp/standby-light.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac
tv "am start -n $PKG/.MainActivity >/dev/null; echo opened"
echo "Installed. On the TV: Switch off and keep off, then allow \"Allow debugging?\" with Always allow ticked."
