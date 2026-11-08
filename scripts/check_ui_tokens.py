#!/usr/bin/env python3
"""UI token gate — Python port of scripts/check_ui_tokens.sh.

Why this file exists
--------------------
Design doc section 13.1 lists ``scripts/check_ui_tokens.sh`` as a **per-stage
mandatory** static check, and section 19 defines its contract. The shell script
runs its whole check body inside an embedded ``python3`` heredoc, so the only
thing that actually needs bash is the argument parsing and the ``git rev-parse``
call. On a host without a working bash (Windows + unavailable WSL service) the
gate therefore could not run at all, and it silently went unexecuted for a long
stretch of UI work.

This port keeps the shell script as the canonical definition and mirrors its CLI
and its five checks exactly:

1. token completeness  — the 49 required ``webhtv_color_*`` names exist in both
   the light and the night table;
2. style completeness  — the 12 required ``Widget/Theme.WebHTV*`` styles exist;
3. contrast            — 18 role pairs against both palettes, plus
   ``player_control_active`` on black;
4. hard-coded colour    — raw ``#RRGGBB``/``#AARRGGBB`` in layout / drawable /
   color / asset files, minus the allowlist;
5. legacy theme names   — ``Theme.WebHTV.LightDialog`` / ``Widget.WebHTV.LightDialog``.

The output lines (``UI_TOKEN_BASELINE`` / ``UI_TOKEN_SCOPE`` /
``UI_TOKEN_CONTRAST`` / ``UI_TOKEN_VIOLATION`` / ``UI_TOKEN_LEGACY`` /
``UI_TOKEN_STATUS``) are byte-compatible with the shell version, so existing
records that quote them stay comparable.

Usage:
  python scripts/check_ui_tokens.py
  python scripts/check_ui_tokens.py --baseline
  python scripts/check_ui_tokens.py --strict
  python scripts/check_ui_tokens.py --stage B|C|D|E|F
  python scripts/check_ui_tokens.py --tokens-file path
  python scripts/check_ui_tokens.py --allowlist path

Exit code is 0 on PASS and 1 on FAIL, matching the shell script.
"""

from __future__ import annotations

import pathlib
import subprocess
import sys


def repo_root() -> pathlib.Path:
    """Same resolution as the shell script: git top-level, else cwd."""
    try:
        out = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            capture_output=True, text=True, check=True,
        )
        return pathlib.Path(out.stdout.strip())
    except Exception:
        return pathlib.Path.cwd()


def run(root_arg, tokens_file, night_tokens_file, allowlist, strict_flag, stage_value):
    import fnmatch
    import pathlib
    import re
    import sys


    root = pathlib.Path(root_arg)
    tokens_path = pathlib.Path(tokens_file)
    night_tokens_path = pathlib.Path(night_tokens_file)
    allowlist_path = pathlib.Path(allowlist)
    strict = strict_flag
    stage = stage_value

    required = [
        "primary", "on_primary", "primary_container", "on_primary_container",
        "secondary", "on_secondary", "secondary_container", "on_secondary_container",
        "tertiary", "on_tertiary", "error", "on_error", "error_container", "on_error_container",
        "success", "on_success", "success_container", "on_success_container",
        "warning", "on_warning", "warning_container", "on_warning_container",
        "surface", "surface_dim", "surface_bright", "surface_container_lowest",
        "surface_container_low", "surface_container", "surface_container_high",
        "surface_container_highest", "on_surface", "on_surface_variant", "outline",
        "outline_variant", "inverse_surface", "inverse_on_surface", "inverse_primary",
        "scrim", "shadow", "focus", "player_control", "player_control_muted",
        "player_control_active", "player_scrim", "health_good", "health_warn", "health_bad",
        "overlay_light", "overlay_dark",
    ]

    color_pattern = re.compile(r'<color name="webhtv_color_([a-z0-9_]+)">#([0-9A-Fa-f]{6,8})</color>')
    hex_pattern = re.compile(r'#[0-9A-Fa-f]{6,8}\b')
    xml_comment_pattern = re.compile(r'<!--.*?-->', re.DOTALL)

    def strip_xml_comments(text):
        """XML comments are documentation, not rendered values.

        An audit comment quoting the palette a file replaced (and the contrast it scored) is not
        a hard-coded colour, so scanning raw text flags any file whose only hex sits in a comment
        - a false positive whose cheapest fix is deleting the evidence. Stripping comments first
        keeps check 4 aimed at what actually renders.
        """
        return xml_comment_pattern.sub(" ", text)

    def parse_colors(path):
        text = path.read_text(encoding="utf-8")
        colors = {}
        for name, value in color_pattern.findall(text):
            colors[name] = int(value, 16)
        return colors

    if not tokens_path.is_file() or not night_tokens_path.is_file():
        print("UI_TOKEN_STATUS FAIL missing token resource file", file=sys.stderr)
        raise SystemExit(1)

    light = parse_colors(tokens_path)
    dark = parse_colors(night_tokens_path)
    missing = [name for name in required if name not in light or name not in dark]
    if missing:
        print("UI_TOKEN_STATUS FAIL missing tokens=" + ",".join(missing), file=sys.stderr)
        raise SystemExit(1)

    styles_path = root / "app/src/main/res/values/webhtv_styles.xml"
    styles = styles_path.read_text(encoding="utf-8") if styles_path.is_file() else ""
    required_styles = [
        "Theme.WebHTV", "ThemeOverlay.WebHTV.Dialog", "Widget.WebHTV.Button.Filled",
        "Widget.WebHTV.Button.Tonal", "Widget.WebHTV.Button.Outlined", "Widget.WebHTV.Button.Text",
        "Widget.WebHTV.Input", "Widget.WebHTV.ListItem", "Widget.WebHTV.Card",
        "Widget.WebHTV.Dialog", "Widget.WebHTV.BottomSheet", "Widget.WebHTV.PlayerControl",
    ]
    missing_styles = [name for name in required_styles if f'name="{name}"' not in styles]
    if missing_styles:
        print("UI_TOKEN_STATUS FAIL missing styles=" + ",".join(missing_styles), file=sys.stderr)
        raise SystemExit(1)

    def rgb(value):
        return value & 0xFFFFFF

    def luminance(value):
        value = rgb(value)
        channels = [(value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF]
        linear = [v / 255 / 12.92 if v / 255 <= 0.04045 else ((v / 255 + 0.055) / 1.055) ** 2.4 for v in channels]
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]

    def contrast(foreground, background):
        a, b = luminance(foreground), luminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)

    pairs = [
        ("on_primary", "primary", 4.5), ("on_primary_container", "primary_container", 4.5),
        ("on_secondary", "secondary", 4.5), ("on_secondary_container", "secondary_container", 4.5),
        ("on_tertiary", "tertiary", 4.5), ("on_error", "error", 4.5),
        ("on_error_container", "error_container", 4.5), ("on_success", "success", 4.5),
        ("on_success_container", "success_container", 4.5), ("on_warning", "warning", 4.5),
        ("on_warning_container", "warning_container", 4.5), ("on_surface", "surface", 4.5),
        ("on_surface_variant", "surface", 4.5), ("on_surface", "surface_container_high", 4.5),
        ("on_surface", "surface_container_highest", 4.5), ("inverse_on_surface", "inverse_surface", 4.5),
        ("outline", "surface", 3.0), ("focus", "surface", 3.0),
    ]

    failures = []
    minimum = 100.0
    for foreground, background, threshold in pairs:
        for palette in (light, dark):
            ratio = contrast(palette[foreground], palette[background])
            minimum = min(minimum, ratio)
            if ratio + 0.0001 < threshold:
                failures.append(f"{foreground}/{background}={ratio:.2f}<{threshold}")
    player_ratio = min(contrast(palette["player_control_active"], 0x000000) for palette in (light, dark))
    minimum = min(minimum, player_ratio)
    if player_ratio + 0.0001 < 4.5:
        failures.append(f"player_control_active/player_scrim={player_ratio:.2f}<4.5")

    allow_patterns = []
    if allowlist_path.is_file():
        for raw in allowlist_path.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            allow_patterns.append(line.split("|", 1)[0].strip())

    def relative(path):
        return path.relative_to(root).as_posix()

    def allowed(path):
        value = relative(path)
        return any(fnmatch.fnmatchcase(value, pattern) or value.startswith(pattern.rstrip("/") + "/") for pattern in allow_patterns)

    resource_roots = [
        root / "app/src/main/res/layout", root / "app/src/mobile/res/layout", root / "app/src/leanback/res/layout",
        root / "app/src/main/res/drawable", root / "app/src/mobile/res/drawable", root / "app/src/leanback/res/drawable",
        root / "app/src/main/res/color",
        root / "app/src/main/assets",
    ]
    resource_files = []
    for directory in resource_roots:
        if directory.is_dir():
            if directory == root / "app/src/main/assets":
                resource_files.extend(directory.rglob("*.css"))
                resource_files.extend(directory.rglob("*.html"))
            else:
                resource_files.extend(directory.rglob("*.xml"))
    resource_files = sorted(set(resource_files))

    def in_stage(path):
        value = relative(path)
        name = path.name
        if stage == "B":
            return name.startswith(("dialog_", "fragment_setting", "activity_setting")) or "/color/dialog_" in value
        if stage == "C":
            if name.startswith(("view_empty", "view_progress", "view_wall")):
                return True
            if name.startswith(("item_", "adapter_")):
                # Detail and player OSD layouts are owned by stage D.
                return "player_osd" not in value and "tmdb" not in value
            return False
        if stage == "D":
            return "tmdb" in value or "detail" in value or name.startswith(("view_control", "view_player_osd", "adapter_player_osd"))
        if stage == "E":
            return value.startswith("app/src/main/assets/") and path.suffix.lower() in {".css", ".html"}
        return True

    layout_files = [p for p in resource_files if "/res/layout/" in relative(p)]
    drawable_files = [p for p in resource_files if "/res/drawable" in relative(p)]
    color_files = [p for p in resource_files if "/res/color/" in relative(p)]

    def asset_has_hex(path):
        """Raw hex is only allowed on controlled token declarations and theme-color metadata."""
        for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
            if not hex_pattern.search(line):
                continue
            stripped = line.strip()
            if stripped.startswith("--") and ":" in stripped:
                continue
            if '<meta name="theme-color"' in stripped:
                continue
            return True
        return False


    def has_hex(path):
        if path.suffix.lower() in {".css", ".html"} and "app/src/main/assets/" in relative(path):
            return asset_has_hex(path)
        return bool(hex_pattern.search(strip_xml_comments(path.read_text(encoding="utf-8", errors="ignore"))))


    def scan(files):
        hits = []
        for path in files:
            if path in {tokens_path, night_tokens_path} or allowed(path):
                continue
            if has_hex(path):
                hits.append(relative(path))
        return hits

    violations = scan([p for p in resource_files if in_stage(p)])
    legacy_hits = []
    for path in resource_files:
        if path in {tokens_path, night_tokens_path} or allowed(path):
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        if "Theme.WebHTV.LightDialog" in text or "Widget.WebHTV.LightDialog" in text:
            legacy_hits.append(relative(path))

    hex_layouts = len(scan(layout_files))
    hex_drawables = len(scan(drawable_files))
    hex_colors = len(scan(color_files))
    allow_hits = sum(1 for path in resource_files if allowed(path))

    scope = f"stage={stage or 'A'}"
    print(f"UI_TOKEN_BASELINE layouts={len(layout_files)} hex_layouts={hex_layouts} drawables={len(drawable_files)} hex_drawables={hex_drawables} colors={len(color_files)} hex_colors={hex_colors} allowlisted={allow_hits}")
    print(f"UI_TOKEN_SCOPE {scope} violations={len(violations)} legacy={len(legacy_hits)}")
    print(f"UI_TOKEN_CONTRAST pairs={(len(pairs) * 2) + 2} failures={len(failures)} min={minimum:.2f}")

    failed = bool(failures)
    if strict and (violations or legacy_hits):
        failed = True
        for path in violations[:40]:
            print(f"UI_TOKEN_VIOLATION {path}")
        if len(violations) > 40:
            print(f"UI_TOKEN_VIOLATION ... {len(violations) - 40} more")
        for path in legacy_hits[:20]:
            print(f"UI_TOKEN_LEGACY {path}")
    if failures:
        for failure in failures:
            print(f"UI_TOKEN_CONTRAST_FAILURE {failure}")

    print("UI_TOKEN_STATUS " + ("FAIL" if failed else "PASS"))
    return 1 if failed else 0


USAGE = """Usage:
  scripts/check_ui_tokens.py
  scripts/check_ui_tokens.py --baseline
  scripts/check_ui_tokens.py --strict
  scripts/check_ui_tokens.py --stage B|C|D|E|F
  scripts/check_ui_tokens.py --tokens-file path
  scripts/check_ui_tokens.py --allowlist path"""


def main(argv=None) -> int:
    """Argument parsing mirrors the shell script's ``case`` block exactly.

    Hand-rolled rather than ``argparse`` on purpose: the shell version accepts a
    bare ``help`` alias next to ``-h``/``--help``, and it treats an unknown
    option as a usage error with exit code 2. Reproducing that keeps the two
    entry points interchangeable.
    """
    argv = list(sys.argv[1:] if argv is None else argv)
    baseline = False
    strict = False
    stage = ""
    tokens_file = ""
    allowlist = ""

    while argv:
        arg = argv.pop(0)
        if arg == "--baseline":
            baseline = True
        elif arg == "--strict":
            strict = True
        elif arg == "--stage":
            if not argv:
                print(USAGE, file=sys.stderr)
                return 2
            stage = argv.pop(0)
        elif arg == "--tokens-file":
            if not argv:
                print(USAGE, file=sys.stderr)
                return 2
            tokens_file = argv.pop(0)
        elif arg == "--allowlist":
            if not argv:
                print(USAGE, file=sys.stderr)
                return 2
            allowlist = argv.pop(0)
        elif arg in ("-h", "--help", "help"):
            print(USAGE)
            return 0
        else:
            print(f"unknown option: {arg}", file=sys.stderr)
            print(USAGE, file=sys.stderr)
            return 2

    if stage not in ("", "A", "B", "C", "D", "E", "F"):
        print(f"invalid stage: {stage}", file=sys.stderr)
        return 2

    # Mirrors the shell script: --baseline forces non-strict; a stage other than
    # A (or empty) turns strict on.
    if baseline:
        strict = False
    elif stage and stage != "A":
        strict = True

    root = repo_root()
    tokens_file = tokens_file or str(root / "app/src/main/res/values/webhtv_tokens.xml")
    night_tokens_file = str(root / "app/src/main/res/values-night/webhtv_tokens.xml")
    allowlist = allowlist or str(root / "docs/ui-token-allowlist.txt")

    return run(root, tokens_file, night_tokens_file, allowlist, strict, stage)


if __name__ == "__main__":
    raise SystemExit(main())
