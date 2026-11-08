#!/usr/bin/env bash
# C111 rate probe v2: sample "Total frames rendered" every ~200ms across the entry window.
# 免疫 framestats 120 帧环形缓冲截断：gfxinfo 汇总是累计量，采样间隔即时间分辨率。
set -uo pipefail
SERIAL=${SERIAL:-emulator-5554}
PKG=${PKG:-com.silent.android.webhtv}
OUT=${OUT:-}
XY=${XY:-217,694}
SAMPLES=${SAMPLES:-70}
X=${XY%,*}; Y=${XY#*,}
[ -n "$OUT" ] || { echo "usage: OUT=<dir> $0" >&2; exit 2; }
mkdir -p "$OUT"
adb_() { command adb -s "$SERIAL" "$@"; }
wall() { adb_ shell date +%s%3N | tr -d '\r'; }
focus() { adb_ shell dumpsys window windows 2>/dev/null | tr -d '\r' | grep -m1 mCurrentFocus || true; }
wait_focus() { local p=$1 l=$2 i=1; while [ $i -le "$l" ]; do case "$(focus)" in *$p*) return 0;; esac; sleep 0.5; i=$((i+1)); done; return 1; }

adb_ shell "am force-stop $PKG" >/dev/null 2>&1
adb_ shell "monkey -p $PKG -c android.intent.category.LAUNCHER 1" >/dev/null 2>&1
if ! wait_focus HomeActivityCurrent 20; then
  adb_ shell "am start -n $PKG/com.fongmi.android.tv.ui.activity.HomeActivityCurrent" >/dev/null 2>&1
  wait_focus HomeActivityCurrent 20 || { echo "home not reached" >&2; exit 3; }
fi
sleep 12
adb_ logcat -c >/dev/null 2>&1
adb_ shell "dumpsys gfxinfo $PKG reset" >/dev/null 2>&1

# device-side sampler (background), ~200ms apart
adb_ shell "i=0; while [ \$i -lt $SAMPLES ]; do echo \"\$(date +%s%3N) \$(dumpsys gfxinfo $PKG | grep -m1 Total)\"; i=\$((i+1)); sleep 0.2; done" > "$OUT/rate.txt" 2>/dev/null &

TAP=$(wall); echo "tap_wall_ms=$TAP xy=$XY" > "$OUT/markers.txt"
adb_ shell input keyevent KEYCODE_DPAD_DOWN
sleep 0.4
adb_ shell input tap $X $Y
sleep 1
echo "tap2_wall_ms=$(wall)" >> "$OUT/markers.txt"
# 轮询 playback_ready
ready=no
for i in $(seq 1 20); do
  if adb_ logcat -d -t 400 2>/dev/null | grep -q 'onTimeChanged: position=.*duration=.*canSave=true'; then ready=yes; break; fi
  sleep 0.5
done
echo "ready_flag=$ready wall_ms=$(wall)" >> "$OUT/markers.txt"
sleep 3
adb_ logcat -d > "$OUT/logcat.txt" 2>/dev/null
wait
adb_ shell "am force-stop $PKG" >/dev/null 2>&1
echo "[rate] ready=$ready tap=$TAP"
echo "artifacts: $OUT"
