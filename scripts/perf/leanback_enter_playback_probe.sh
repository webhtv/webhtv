#!/usr/bin/env bash
# C111 电视版(leanback)「首页卡片 -> 播放页」进场窗口帧级探针。
# 与 docs/C111-leanback-playback-loading-jank.md 的基线口径一致：
#   窗口 = tap_wall -> playback_ready(logcat onTimeChanged ... canSave=true)
#   帧源 = dumpsys gfxinfo framestats 逐帧（starve = vsync-intendedVsync, trav = drawStart-performTraversalsStart）
# 与 scripts/perf/loading_jank_probe.sh 的区别：那个只解析 gfxinfo 汇总段，
# 本脚本逐帧解析并能把 video-flow 分段打点对齐到帧，用于改动前后对照。
set -uo pipefail

SERIAL=${SERIAL:-emulator-5554}
PKG=${PKG:-com.silent.android.webhtv}
OUT=${OUT:-}
RUNS=${RUNS:-3}
XY=${XY:-217,694}
X=${XY%,*}
Y=${XY#*,}

[ -n "$OUT" ] || { echo "usage: OUT=<dir> [RUNS=n] [XY=x,y] $0" >&2; exit 2; }
mkdir -p "$OUT"

adb_() { command adb -s "$SERIAL" "$@"; }
wall() { adb_ shell date +%s%3N | tr -d '\r'; }
focus() { adb_ shell dumpsys window windows 2>/dev/null | tr -d '\r' | grep -m1 -E 'mCurrentFocus' || true; }

wait_focus() {
  local pat=$1 limit=$2 i=1
  while [ $i -le "$limit" ]; do
    case "$(focus)" in *$pat*) return 0;; esac
    sleep 0.5; i=$(( i + 1 ))
  done
  return 1
}

cap() {
  local dir=$1 stage=$2
  adb_ shell "dumpsys gfxinfo $PKG" > "$dir/gfxinfo-$stage.txt" 2>/dev/null
  adb_ shell "dumpsys gfxinfo $PKG framestats" > "$dir/framestats-$stage.txt" 2>/dev/null
  adb_ logcat -d > "$dir/logcat-$stage.txt" 2>/dev/null
  adb_ exec-out screencap -p > "$dir/screen-$stage.png" 2>/dev/null
}

run_once() {
  local n=$1 dir="$OUT/run$n"
  rm -rf "$dir"; mkdir -p "$dir"; : > "$dir/summary.txt"; : > "$dir/markers.txt"
  adb_ shell "am force-stop $PKG" >/dev/null 2>&1
  adb_ shell "monkey -p $PKG -c android.intent.category.LAUNCHER 1" >/dev/null 2>&1
  local ok=no
  wait_focus HomeActivityCurrent 25 && ok=yes
  echo "home_reached=$ok" >> "$dir/summary.txt"
  # 等首页异步内容(最近观看卡片)就绪，否则 tap 会落空
  sleep 12
  adb_ logcat -c >/dev/null 2>&1
  adb_ shell "dumpsys gfxinfo $PKG reset" >/dev/null 2>&1
  local tap; tap=$(wall)
  echo "tap_wall_ms=$tap xy=$XY" >> "$dir/markers.txt"
  # 先聚焦再激活：单次 tap 只让卡片获焦，不进播放页
  adb_ shell input keyevent KEYCODE_DPAD_DOWN
  sleep 0.4
  adb_ shell input tap $X $Y
  local act=no
  wait_focus VideoActivity 32 && act=yes
  echo "activity_start=$act wall_ms=$(wall)" >> "$dir/markers.txt"
  sleep 3
  cap "$dir" load-early
  local ready=no i=1
  while [ $i -le 30 ]; do
    if adb_ logcat -d -t 300 2>/dev/null | grep -q 'onTimeChanged: position=.*duration=.*canSave=true'; then ready=yes; break; fi
    sleep 1; i=$(( i + 1 ))
  done
  echo "ready_flag=$ready wall_ms=$(wall)" >> "$dir/markers.txt"
  echo "playback_ready=$ready" >> "$dir/summary.txt"
  sleep 1
  cap "$dir" load-full
  adb_ shell "am force-stop $PKG" >/dev/null 2>&1
  echo "[run$n] home=$ok activity=$act ready=$ready"
}

n=1
while [ $n -le "$RUNS" ]; do run_once "$n"; n=$(( n + 1 )); done
echo "artifacts: $OUT"
