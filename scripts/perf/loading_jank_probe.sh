#!/usr/bin/env bash
# C101 loading-spinner jank probe.
#
# Measures the frame health of the "entering a card" window on one device:
# from the tap on a home card until playback actually renders, which is exactly
# the interval where the loading spinner is on screen.
#
# Usage:
#   bash scripts/perf/loading_jank_probe.sh --tag baseline [--serial SERIAL] [--runs N]
#        [--debug-log on|off|keep] [--launch-wait SEC] [--early-wait SEC]
#        [--ready-timeout SEC] [--item-xy X,Y]
#
# Per run, .wh_diag/perf/<tag>/run<N>/ gets:
#   gfxinfo-load-early.txt   frames from tap to --early-wait seconds
#   gfxinfo-load-full.txt    frames from tap until playback is ready (acceptance window)
#   logcat-load-*.txt        Skipped/Davey lines in that window
#   screen-*.png             what was on screen at capture time
#   markers.txt              wall-clock offsets of activity start / player init / decoder / first frame
#   summary.txt              parsed metrics
set -euo pipefail

PKG="com.silent.android.webhtv"
HOME_ACT="HomeActivityCurrent"
VIDEO_ACT="VideoActivity"
SERIAL="emulator-5556"
TAG="baseline"
OUT_ROOT=""
RUNS=3
DEBUG_LOG="keep"
LAUNCH_WAIT=14
EARLY_WAIT=3
READY_TIMEOUT=30
ITEM_XY="220,390"

usage() {
  sed -n '3,14p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
}

while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --tag) TAG="$2"; shift 2 ;;
    --runs) RUNS="$2"; shift 2 ;;
    --debug-log) DEBUG_LOG="$2"; shift 2 ;;
    --launch-wait) LAUNCH_WAIT="$2"; shift 2 ;;
    --early-wait) EARLY_WAIT="$2"; shift 2 ;;
    --ready-timeout) READY_TIMEOUT="$2"; shift 2 ;;
    --item-xy) ITEM_XY="$2"; shift 2 ;;
    --out) OUT_ROOT="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "unknown option: $1" >&2; usage ;;
  esac
done

adb() { command adb -s "$SERIAL" "$@"; }
if [ -z "$OUT_ROOT" ]; then OUT_ROOT=".wh_diag/perf"; fi
root="$OUT_ROOT/$TAG"
mkdir -p "$root"
prefs="/data/data/$PKG/shared_prefs/$PKG""_preferences.xml"

now_ms() { date +%s%3N; }

focus_now() {
  adb shell "dumpsys window windows" 2>/dev/null | tr -d '\r' | grep -m1 -E 'mCurrentFocus' || true
}

tap_item() {
  local x y
  x="$(echo "$1" | cut -d, -f1)"
  y="$(echo "$1" | cut -d, -f2)"
  adb shell "input tap $x $y" >/dev/null 2>&1 || true
}

dismiss_anr() {
  local focus
  focus="$(focus_now)"
  case "$focus" in
    *"Not Responding"*|*"isn't responding"*|*"没有响应"*)
      echo "  dismissing ANR dialog"
      adb shell "input keyevent KEYCODE_BACK" >/dev/null 2>&1 || true
      sleep 1
      return 0
      ;;
  esac
  return 1
}

# Note: never pipe focus_now into grep under "set -o pipefail"; dumpsys gets
# SIGPIPE when grep exits on the first match, which turns a hit into exit 141.
focus_has() {
  local f
  f="$(focus_now)"
  case "$f" in
    *"$1"*) return 0 ;;
    *) return 1 ;;
  esac
}

wait_home() {
  local i=1
  while [ "$i" -le 20 ]; do
    if focus_has "$HOME_ACT"; then return 0; fi
    dismiss_anr >/dev/null 2>&1 || true
    sleep 2
    i=$(( i + 1 ))
  done
  return 1
}

wait_video() {
  local i=1
  while [ "$i" -le 12 ]; do
    if focus_has "$VIDEO_ACT"; then return 0; fi
    dismiss_anr >/dev/null 2>&1 || true
    sleep 1
    i=$(( i + 1 ))
  done
  return 1
}

# Playback is "ready" once the player has a duration and can persist progress,
# which is the first moment the spinner is expected to be gone.
playback_ready() {
  local out
  out="$(adb logcat -d -t 300 2>/dev/null || true)"
  case "$out" in
    *"VideoActivity: onTimeChanged: position="*"duration="*"canSave=true"*) return 0 ;;
    *) return 1 ;;
  esac
}

wait_ready() {
  local i=1
  while [ "$i" -le "$READY_TIMEOUT" ]; do
    if playback_ready; then return 0; fi
    sleep 1
    i=$(( i + 1 ))
  done
  return 1
}

set_debug_log() {
  local want="$1" have
  have="$(adb shell "grep -o 'name=\"debug_log\" value=\"[a-z]*\"' $prefs 2>/dev/null || true" | tr -d '\r')"
  if [ "$have" = "name=\"debug_log\" value=\"$want\"" ]; then
    echo "debug_log already $want"
    return 0
  fi
  adb shell "am force-stop $PKG" >/dev/null 2>&1 || true
  if [ "$want" = "true" ]; then
    adb shell "sed -i 's/name=\"debug_log\" value=\"false\"/name=\"debug_log\" value=\"true\"/' $prefs" >/dev/null 2>&1 || true
  else
    adb shell "sed -i 's/name=\"debug_log\" value=\"true\"/name=\"debug_log\" value=\"false\"/' $prefs" >/dev/null 2>&1 || true
  fi
  have="$(adb shell "grep -o 'name=\"debug_log\" value=\"[a-z]*\"' $prefs" | tr -d '\r')"
  echo "debug_log now: $have"
}

capture() {
  local dir="$1" stage="$2"
  adb shell "dumpsys gfxinfo $PKG" > "$dir/gfxinfo-$stage.txt" 2>/dev/null || true
  adb shell "dumpsys gfxinfo $PKG framestats" > "$dir/framestats-$stage.txt" 2>/dev/null || true
  adb logcat -d > "$dir/logcat-$stage.txt" 2>/dev/null || true
  grep -E 'Skipped [0-9]+ frames|Davey!' "$dir/logcat-$stage.txt" > "$dir/logcat-frames-$stage.txt" 2>/dev/null || true
  adb exec-out screencap -p > "$dir/screen-$stage.png" 2>/dev/null || true
}

metric() {
  awk -v key="$2" '
    $0 ~ "^" key ": " { sub("^" key ": ", ""); print; found = 1 }
    END { if (!found) print "unknown" }
  ' "$1"
}

summarize_stage() {
  local dir="$1" stage="$2" gfx="$1/gfxinfo-$2.txt" frames="$1/logcat-frames-$2.txt"
  local total janky pct p99 missed slowui skipped maxdrop davey
  : "${skipped:=0}"
  total="$(metric "$gfx" 'Total frames rendered')"
  janky="$(metric "$gfx" 'Janky frames' | grep -oE '^[0-9]+' || true)"
  pct="$(metric "$gfx" 'Janky frames' | grep -oE '\([0-9.]+%' | tr -d '(' || true)"
  p99="$(metric "$gfx" '99th percentile')"
  missed="$(metric "$gfx" 'Number Missed Vsync')"
  slowui="$(metric "$gfx" 'Number Slow UI thread')"
  skipped="$(grep -cE 'Skipped [0-9]+ frames' "$frames" 2>/dev/null || true)"
  maxdrop="$(grep -oE 'Skipped [0-9]+ frames' "$frames" 2>/dev/null | grep -oE '[0-9]+' | sort -n | tail -1 || true)"
  davey="$(grep -cE 'Davey!' "$frames" 2>/dev/null || true)"
  {
    echo "stage=$stage"
    echo "total_frames=$total"
    echo "janky_frames=$janky"
    echo "janky_pct=$pct"
    echo "p99=$p99"
    echo "missed_vsync=$missed"
    echo "slow_ui_thread=$slowui"
    echo "skipped_events=$skipped"
    echo "max_skipped_frames=$maxdrop"
    echo "davey_events=$davey"
  } >> "$dir/summary.txt"
  echo "  [$stage] frames=$total janky=$janky (pct=$pct) p99=$p99 skipped=$skipped maxSkipped=$maxdrop davey=$davey"
}

record_marker() {
  local dir="$1" name="$2" base="$3"
  local t
  t="$(now_ms)"
  echo "$name=+$(( t - base ))ms" >> "$dir/markers.txt"
}

run_once() {
  local n="$1"
  local dir="$root/run$n"
  rm -rf "$dir"
  mkdir -p "$dir"
  : > "$dir/summary.txt"
  : > "$dir/markers.txt"

  adb shell "am force-stop $PKG" >/dev/null 2>&1 || true
  adb shell "monkey -p $PKG -c android.intent.category.LAUNCHER 1" >/dev/null 2>&1 || true
  echo "[run$n] launched, waiting for $HOME_ACT"
  if wait_home; then
    echo "home_reached=yes" >> "$dir/summary.txt"
  else
    echo "home_reached=no" >> "$dir/summary.txt"
  fi
  sleep "$LAUNCH_WAIT"

  adb logcat -c >/dev/null 2>&1 || true
  adb shell "dumpsys gfxinfo $PKG reset" >/dev/null 2>&1 || true
  local base
  base="$(now_ms)"
  echo "[run$n] tap card $ITEM_XY"
  tap_item "$ITEM_XY"

  local i=1
  while [ "$i" -le 12 ]; do
    if focus_has "$VIDEO_ACT"; then record_marker "$dir" activity_start "$base"; break; fi
    sleep 0.5
    i=$(( i + 1 ))
  done

  sleep "$EARLY_WAIT"
  capture "$dir" load-early
  echo "[run$n] early window captured"
  summarize_stage "$dir" load-early

  if wait_ready; then
    echo "playback_ready=yes" >> "$dir/summary.txt"
  else
    echo "playback_ready=no" >> "$dir/summary.txt"
  fi
  record_marker "$dir" ready "$base"
  sleep 1
  capture "$dir" load-full
  summarize_stage "$dir" load-full

  adb shell "am force-stop $PKG" >/dev/null 2>&1 || true
}

if [ "$DEBUG_LOG" = "on" ]; then set_debug_log true; fi
if [ "$DEBUG_LOG" = "off" ]; then set_debug_log false; fi

i=1
while [ "$i" -le "$RUNS" ]; do
  run_once "$i"
  i=$(( i + 1 ))
done

{
  echo "tag=$TAG serial=$SERIAL runs=$RUNS debug_log=$DEBUG_LOG"
  i=1
  while [ "$i" -le "$RUNS" ]; do
    echo "run$i:"
    cat "$root/run$i/summary.txt" 2>/dev/null || true
    echo "markers:"
    cat "$root/run$i/markers.txt" 2>/dev/null || true
    echo "---"
    i=$(( i + 1 ))
  done
} > "$root/summary.txt"

echo "artifacts: $root"
