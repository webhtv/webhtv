#!/usr/bin/env bash
# C101-S10 baseline profile generator for WebHTV.
#
# Produces app/src/main/baseline-prof.txt from a profile the device already
# collected, using the manual path documented by Android:
#   profman --dump-classes-and-methods --profile-file=<primary.prof> --apk=<base.apk>
# That is the same rule set Macrobenchmark's generateBaselineProfile writes,
# without adding a benchmark module or any dependency.
#
# Usage:
#   bash scripts/perf/baseline_profile_build.sh --serial emulator-5556 [--out DIR]
#        [--reset]      wipe the device profile and re-record the entry journeys first
#        [--pkg PKG]    package name (default com.silent.android.webhtv)
#
# Recording implies driving the app: launch, open a detail card and start
# playback. --reset only clears state; commit the resulting text file.
set -euo pipefail

PKG="com.silent.android.webhtv"
SERIAL="emulator-5556"
OUT_DIR=""
RESET=0
HOME_ACT="com.fongmi.android.tv.ui.activity.HomeActivity"

while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --pkg) PKG="$2"; shift 2 ;;
    --out) OUT_DIR="$2"; shift 2 ;;
    --reset) RESET=1; shift ;;
    -h|--help) sed -n '3,16p' "$0" | sed 's/^# {0,1}//'; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

[ -n "$OUT_DIR" ] || OUT_DIR="$(mktemp -d)"
mkdir -p "$OUT_DIR"

adb() { command adb -s "$SERIAL" "$@"; }

if ! adb get-state >/dev/null 2>&1; then
  echo "device $SERIAL is not available" >&2
  exit 2
fi

APK_PATH="$(adb shell pm path "$PKG" | sed 's/^package://' | tr -d '' | head -1)"
[ -n "$APK_PATH" ] || { echo "package $PKG is not installed" >&2; exit 2; }
PROFILE="/data/misc/profiles/cur/0/$PKG/primary.prof"

if [ "$RESET" -eq 1 ]; then
  echo "== reset: clearing device profile and dexopt state =="
  adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
  adb shell "rm -f '$PROFILE'" >/dev/null 2>&1 || true
  adb shell "rm -rf /data/misc/profiles/ref/$PKG" >/dev/null 2>&1 || true
  adb shell cmd package compile -m verify -f "$PKG" >/dev/null 2>&1 || true
  echo "== record: launch, then open a card and start playback by hand =="
  adb shell am start -W -n "$PKG/$HOME_ACT" >/dev/null 2>&1 || true
  echo "   (waiting 60s for you to drive the journey; the probe window is not scripted)"
  sleep 60
  adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
fi

if ! adb shell "test -f '$PROFILE'" >/dev/null 2>&1; then
  echo "no device profile at $PROFILE; run once with --reset and exercise the app" >&2
  exit 3
fi

RAW="$OUT_DIR/raw-dump.txt"
adb shell profman --dump-classes-and-methods   --profile-file="$PROFILE" --apk="$APK_PATH" > "$RAW"

# Keep every rule profman emitted, verbatim; only normalise line endings,
# drop blank lines and duplicates, then sort for a stable diff.
OUT="$OUT_DIR/baseline-prof.txt"
tr -d '' < "$RAW" | sed '/^$/d' | sort -u > "$OUT"

echo "== dump: $(wc -l < "$OUT") rules ($(grep -c 'com/fongmi' "$OUT" || true) in this app) =="
echo "== written: $OUT"
echo "   commit it as app/src/main/baseline-prof.txt; AGP merges it with the"
echo "   dependency-provided rules and packages assets/dexopt/baseline.prof."
