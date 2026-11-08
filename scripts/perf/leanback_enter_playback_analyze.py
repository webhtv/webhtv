#!/usr/bin/env python3
"""C111 电视版(leanback)播放页进场窗口帧级分析。

口径与 scripts/perf/leanback_enter_playback_probe.sh 产物配套：
  window   = markers.txt 的 tap_wall_ms -> ready_flag
  starve   = Vsync - IntendedVsync        (主线程多久没申请下一帧)
  trav     = DrawStart - PerformTraversalsStart (建树/布局/绘制耗时)
  boot_wall= wall_ms - uptime_ms，用于把 framestats 单调时钟换算回 logcat 墙钟

用法:
  python leanback_enter_playback_analyze.py <out_dir> [--boot-wall MS] [--runs 1,2,3]

不指定 --boot-wall 时自动取 adb shell 的 uptime 反推（需要 adb）。
"""
import argparse
import json
import os
import re
import subprocess
import sys


def pct(vals, p):
    if not vals:
        return 0.0
    s = sorted(vals)
    k = (len(s) - 1) * p / 100.0
    lo = int(k)
    hi = min(lo + 1, len(s) - 1)
    return s[lo] + (s[hi] - s[lo]) * (k - lo)


def parse_frames(path):
    """返回 [{flags, iv, vs, pt, ds, fc}]，按 FrameCompleted 去重。"""
    rows, seen = [], set()
    if not os.path.exists(path):
        return rows
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.strip()
            if not line or not re.match(r"^-?\d+,", line):
                continue
            parts = line.split(",")
            if len(parts) < 14:
                continue
            try:
                v = [int(x) for x in parts[:14]]
            except ValueError:
                continue
            if v[13] <= 0 or v[13] in seen:
                continue
            seen.add(v[13])
            rows.append({"flags": v[0], "iv": v[1], "vs": v[2], "pt": v[7], "ds": v[8], "fc": v[13]})
    return rows


def read_markers(path):
    out = {}
    if not os.path.exists(path):
        return out
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            if "=" not in line:
                continue
            key, rest = line.strip().split("=", 1)
            m = re.search(r"(\d{13})", rest)
            if m:
                out[key] = int(m.group(1))
    return out


def detect_boot_wall(serial):
    """boot_wall = wall_ms - uptime_ms，一次采样即可（偏差 <1s，仅用于对齐）。"""
    try:
        wall = subprocess.check_output(["adb", "-s", serial, "shell", "date +%s%3N"],
                                       text=True).strip()
        up = subprocess.check_output(["adb", "-s", serial, "shell", "cat /proc/uptime"],
                                     text=True).strip()
        return int(wall) - int(float(up.split()[0]) * 1000)
    except Exception as exc:  # noqa: BLE001 - adb 缺失时退回 0 并由调用方显式告警
        print("WARN: boot_wall detection failed: %s" % exc, file=sys.stderr)
        return 0


VIDEO_FLOW = re.compile(r"video-flow\s*[:(]\s*(.*)$")


def parse_video_flow(path, boot_wall):
    """把 logcat 里的 video-flow 打点换算成相对 tap 的毫秒偏移。"""
    events = []
    if not os.path.exists(path):
        return events
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            if "video-flow" not in line:
                continue
            m = re.match(r"^(\d\d-\d\d) (\d\d):(\d\d):(\d\d)\.(\d{3})", line)
            mf = VIDEO_FLOW.search(line)
            if not m or not mf:
                continue
            events.append({"hms": "%s:%s:%s.%s" % (m.group(2), m.group(3), m.group(4), m.group(5)),
                           "msg": mf.group(1).strip()})
    return events


def analyze_run(base, run, boot_wall):
    d = os.path.join(base, "run%d" % run)
    mk = read_markers(os.path.join(d, "markers.txt"))
    tap, ready = mk.get("tap_wall_ms"), mk.get("ready_flag")
    res = {"run": run, "tap_wall": tap, "ready_wall": ready}
    if not tap or not ready:
        res["error"] = "markers incomplete"
        return res, []
    frames = parse_frames(os.path.join(d, "framestats-load-full.txt"))
    win = [f for f in frames if tap <= (f["fc"] / 1e6 + boot_wall) <= ready]
    res["window_ms"] = ready - tap
    res["frames_all"] = len(frames)
    res["frames_in_window"] = len(win)
    if not win:
        res["error"] = "no frames in window"
        return res, []
    st = [(f["vs"] - f["iv"]) / 1e6 for f in win]
    tv = [(f["ds"] - f["pt"]) / 1e6 for f in win]
    res.update({
        "starve_gt100": len([x for x in st if x > 100]),
        "max_starve": round(max(st), 1),
        "trav_gt40": len([x for x in tv if x > 40]),
        "max_trav": round(max(tv), 1),
        "starve_p50": round(pct(st, 50), 1),
        "starve_p90": round(pct(st, 90), 1),
        "starve_p99": round(pct(st, 99), 1),
        "trav_p50": round(pct(tv, 50), 1),
        "trav_p90": round(pct(tv, 90), 1),
        "trav_p99": round(pct(tv, 99), 1),
    })
    long_frames = []
    for f in win:
        t = (f["fc"] / 1e6 + boot_wall) - tap
        ntv = (f["ds"] - f["pt"]) / 1e6
        nst = (f["vs"] - f["iv"]) / 1e6
        if ntv > 40 or nst > 100:
            long_frames.append({"run": run, "t_rel_ms": round(t, 1),
                                "trav": round(ntv, 1), "starve": round(nst, 1)})
    long_frames.sort(key=lambda x: -max(x["trav"], x["starve"]))
    return res, long_frames


def gfxinfo_summary(path):
    if not os.path.exists(path):
        return {}
    with open(path, encoding="utf-8", errors="replace") as fh:
        txt = fh.read()

    def gv(key):
        m = re.search(r"^" + re.escape(key) + r":\s*(\S+)", txt, re.M)
        return m.group(1) if m else "?"

    j = re.search(r"^Janky frames:\s*(\d+) \(([\d.]+)%\)", txt, re.M)
    return {"total": gv("Total frames rendered"),
            "janky": j.group(1) if j else "?",
            "janky_pct": j.group(2) if j else "?",
            "p99": gv("99th percentile"),
            "missed_vsync": gv("Number Missed Vsync"),
            "slow_ui": gv("Number Slow UI Thread"),
            "slow_draw": gv("Number Slow issue draw commands")}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out_dir")
    ap.add_argument("--boot-wall", type=int, default=None)
    ap.add_argument("--runs", default="1,2,3")
    ap.add_argument("--serial", default="emulator-5554")
    args = ap.parse_args()

    base = os.path.abspath(args.out_dir)
    boot_wall = args.boot_wall if args.boot_wall is not None else detect_boot_wall(args.serial)
    print("boot_wall=%d" % boot_wall)
    runs = [int(x) for x in args.runs.split(",") if x.strip()]
    agg = []
    for run in runs:
        res, longs = analyze_run(base, run, boot_wall)
        res["gfxinfo"] = gfxinfo_summary(os.path.join(base, "run%d" % run, "gfxinfo-load-full.txt"))
        agg.append(res)
        print(json.dumps(res, ensure_ascii=False))
        for item in longs[:10]:
            print("  long t=%8.1fms trav=%7.1f starve=%7.1f" % (item["t_rel_ms"], item["trav"], item["starve"]))
        flows = parse_video_flow(os.path.join(base, "run%d" % run, "logcat-load-full.txt"), boot_wall)
        for ev in flows:
            print("  video-flow %s  %s" % (ev["hms"], ev["msg"][:140]))

    ok = [r for r in agg if "error" not in r]
    if ok:
        print("=== aggregate over %d run(s) ===" % len(ok))
        for key in ("window_ms", "frames_in_window", "starve_gt100", "max_starve",
                    "trav_gt40", "max_trav", "trav_p90", "trav_p99"):
            vals = [r[key] for r in ok]
            print("%-20s avg=%7.1f  min=%7.1f  max=%7.1f" %
                  (key, sum(vals) / len(vals), min(vals), max(vals)))
        print("AGG_JSON=" + json.dumps(ok, ensure_ascii=False))


if __name__ == "__main__":
    main()
