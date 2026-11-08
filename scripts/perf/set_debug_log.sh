#!/usr/bin/env bash
# 切换设备上的 debug_log 偏好（DebugLogStore.PREF_ENABLED = "debug_log"）。
#
# 背景：SpiderDebug.log(tag, ...) 首行就是 `if (!DebugLogStore.acceptsTag(tag)) return;`，
# 关闭时 C111 新增的 video-flow 分段打点一条都不会进 logcat。
#   - 想看 stage 时间线（诊断跑）  -> on
#   - 想采可比的验收帧数据（复测跑）-> off（基线就是在 off 状态采的，开着会引入日志开销）
#
# 用法:
#   bash scripts/perf/set_debug_log.sh show [serial]
#   bash scripts/perf/set_debug_log.sh on   [serial]
#   bash scripts/perf/set_debug_log.sh off  [serial]
#
# 注意：写 prefs 前必须 force-stop，否则进程下次提交偏好时会把文件覆盖回去。
set -uo pipefail
# Git bash(MSYS) 会把 /data/local/tmp/... 这类参数改写成 G:/Git/data/...；
# adb 需要的是设备端原样路径，所以关掉参数路径转换。
export MSYS_NO_PATHCONV=1

ACTION=${1:-show}
SERIAL=${2:-emulator-5554}
PKG=${PKG:-com.silent.android.webhtv}
PREFS="shared_prefs/${PKG}_preferences.xml"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

adb_() { command adb -s "$SERIAL" "$@"; }

read_prefs() {
  adb_ exec-out run-as "$PKG" cat "$PREFS" > "$TMP/prefs.xml" || return 1
  [ -s "$TMP/prefs.xml" ] || { echo "empty prefs (run-as denied?)" >&2; return 1; }
}

state() {
  if grep -q 'name="debug_log" value="true"' "$TMP/prefs.xml"; then echo ON; else echo OFF; fi
}

if ! read_prefs; then exit 1; fi

if [ "$ACTION" = show ]; then
  echo "debug_log = $(state)  (serial=$SERIAL)"
  exit 0
fi

case "$ACTION" in
  on|off) ;;
  *) echo "usage: $0 show|on|off [serial]" >&2; exit 2;;
esac

WANT=$([ "$ACTION" = on ] && echo true || echo false)
if grep -q 'name="debug_log"' "$TMP/prefs.xml"; then
  # 已有键：只改值，保留位置
  perl -0pi -e 's/(name="debug_log" value=")[a-z]+(")/${1}'"$WANT"'${2}/' "$TMP/prefs.xml"
else
  # 没有键：插到 </map> 前（Android 的 prefs 是 <map>...</map>）
  perl -0pi -e 's#</map>#    <boolean name="debug_log" value="'"$WANT"'" />\n</map>#' "$TMP/prefs.xml"
fi

# adb 是 Windows 程序，Git bash 的 /tmp/... 它读不到；必须转成 Windows 路径再 push。
SRC=$(cygpath -w "$TMP/prefs.xml" 2>/dev/null || echo "$TMP/prefs.xml")
adb_ shell "am force-stop $PKG" >/dev/null 2>&1
adb_ push "$SRC" /data/local/tmp/c111_prefs.xml >/dev/null || { echo "push failed" >&2; exit 1; }
adb_ shell "run-as $PKG cp /data/local/tmp/c111_prefs.xml $PREFS" >/dev/null 2>&1 || { echo "cp failed" >&2; exit 1; }
adb_ shell "rm -f /data/local/tmp/c111_prefs.xml" >/dev/null 2>&1

read_prefs || exit 1
echo "debug_log = $(state)  (serial=$SERIAL, action=$ACTION)"
