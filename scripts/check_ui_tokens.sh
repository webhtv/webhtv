#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/check_ui_tokens.sh
  scripts/check_ui_tokens.sh --baseline
  scripts/check_ui_tokens.sh --strict
  scripts/check_ui_tokens.sh --stage B|C|D|E|F
  scripts/check_ui_tokens.sh --tokens-file path
  scripts/check_ui_tokens.sh --allowlist path
EOF
}

root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
tokens_file="$root/app/src/main/res/values/webhtv_tokens.xml"
night_tokens_file="$root/app/src/main/res/values-night/webhtv_tokens.xml"
allowlist="$root/docs/ui-token-allowlist.txt"
strict=0
baseline=0
stage=""

while (($# > 0)); do
  case "$1" in
    --baseline) baseline=1; shift ;;
    --strict) strict=1; shift ;;
    --stage) (($# >= 2)) || { usage >&2; exit 2; }; stage="$2"; shift 2 ;;
    --tokens-file) (($# >= 2)) || { usage >&2; exit 2; }; tokens_file="$2"; shift 2 ;;
    --allowlist) (($# >= 2)) || { usage >&2; exit 2; }; allowlist="$2"; shift 2 ;;
    -h|--help|help) usage; exit 0 ;;
    *) printf 'unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

case "$stage" in
  ""|A|B|C|D|E|F) ;;
  *) printf 'invalid stage: %s\n' "$stage" >&2; exit 2 ;;
esac

if ((baseline == 1)); then
  strict=0
elif [[ -n "$stage" && "$stage" != "A" ]]; then
  strict=1
fi

python3 - "$root" "$tokens_file" "$night_tokens_file" "$allowlist" "$strict" "$stage" <<'PY'
import fnmatch
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
tokens_path = pathlib.Path(sys.argv[2])
night_tokens_path = pathlib.Path(sys.argv[3])
allowlist_path = pathlib.Path(sys.argv[4])
strict = sys.argv[5] == "1"
stage = sys.argv[6]

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

    An audit comment quoting the palette a file replaced (and the contrast it scored) is not a
    hard-coded colour, so scanning raw text flags any file whose only hex sits in a comment - a
    false positive whose cheapest fix is deleting the evidence. Stripping comments first keeps
    check 4 aimed at what actually renders.
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
        return name.startswith("dialog_") or name.startswith("fragment_setting") or name.startswith("activity_setting") or "/color/dialog_" in value
    if stage == "C":
        if name.startswith(("view_empty", "view_progress", "view_wall")):
            return True
        if name.startswith(("item_", "adapter_")):
            # Detail and player OSD layouts are owned by stage D.
            return "player_osd" not in value and "tmdb" not in value
        return False
    if stage == "D":
        return "tmdb" in value or "detail" in value or name.startswith("view_control") or name.startswith("view_player_osd") or name.startswith("adapter_player_osd")
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
raise SystemExit(1 if failed else 0)
PY
