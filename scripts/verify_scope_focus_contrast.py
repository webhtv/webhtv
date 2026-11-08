"""TV 搜索范围下拉 / 键盘按键的对比度像素采样（数字截图，非翻拍）。

用法：
    python scripts/verify_scope_focus_contrast.py <截图目录>

截图由设备直接产生：
    adb -s <serial> exec-out screencap -p > <截图目录>/<name>.png

坐标口径：设备 1920x1080 原生截图；下面的区域参数按**预览图 1708x961** 标定，
脚本内统一换算回原生像素（见 PREVIEW_TO_NATIVE），改设备分辨率时需要同步调整。

判定：正文 4.5:1（WCAG 2.2 SC 1.4.3），焦点环 3:1（SC 1.4.11）。
退出码：0 全部达标；1 有用例不达标；2 截图缺失/为空跑（不构成验证通过）。
"""
import sys
from collections import Counter
from pathlib import Path

from PIL import Image

PREVIEW_TO_NATIVE = 1920 / 1708


def native(box):
    return tuple(int(round(value * PREVIEW_TO_NATIVE)) for value in box)


def channel(value):
    value /= 255.0
    return value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4


def luminance(rgb):
    r, g, b = rgb[:3]
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)


def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def hexs(rgb):
    return "#%02X%02X%02X" % rgb[:3]


def pixels(crop):
    """Pillow 12 起 getdata 被弃用；优先用展平数据。"""
    getter = getattr(crop, "get_flattened_data", None)
    return list(getter()) if getter is not None else list(crop.getdata())


def sample(path, label, fill_box, text_box, threshold=4.5):
    image = Image.open(path).convert("RGB")
    fill_crop = image.crop(native(fill_box))
    text_crop = image.crop(native(text_box))
    fill = Counter(pixels(fill_crop)).most_common(1)[0]
    text = max(pixels(text_crop), key=luminance)
    value = contrast(text, fill[0])
    verdict = "PASS" if value >= threshold else "FAIL"
    print(f"{label:<28} fill={hexs(fill[0])} text={hexs(text)} "
          f"ratio={value:5.2f}:1  {verdict} (>= {threshold:.1f}:1)")
    return value, verdict


CASES = [
    # name, 截图, 说明, 填充区域（文字右侧空白）, 文字区域, 门槛
    ("popup.focused", "final-popup-focused.png", "焦点行「当前站」", (470, 285, 600, 330), (330, 288, 460, 335), 4.5),
    ("popup.selected", "final-popup-focused.png", "选中未聚焦「全部」", (470, 190, 600, 235), (330, 190, 440, 240), 4.5),
    ("popup.focusedSelected", "final-popup-merged.png", "焦点+选中「全部」", (470, 190, 600, 235), (330, 190, 440, 240), 4.5),
    ("popup.default", "final-popup-merged.png", "未聚焦默认行「当前站」", (470, 285, 600, 330), (330, 288, 460, 335), 4.5),
    ("keyboard.normal", "search-after2.png", "键盘按键（black_40 后）", (50, 272, 68, 300), (60, 280, 118, 335), 4.5),
    ("trigger.normal", "search-after2.png", "范围触发器「全部」", (540, 60, 660, 130), (528, 60, 700, 140), 4.5),
]


def main(directory):
    root = Path(directory)
    rows = []
    missing = []
    for _, file, label, fill_box, text_box, threshold in CASES:
        path = root / file
        if not path.is_file():
            missing.append(str(path))
            continue
        rows.append(sample(path, label, fill_box, text_box, threshold))
    if missing:
        print("SKIP: 缺少截图\n  " + "\n  ".join(missing))
    # 空跑不能算通过：没有任何一例跑过说明证据缺失（目录写错、没截图），必须显式失败，
    # 否则这条「验证」在任何环境下都返回 0，等于没有验证。
    if not rows:
        print("FAIL: 没有可判定的截图，未完成任何验证")
        return 2
    failed = [row for row in rows if row[1] == "FAIL"]
    if missing:
        print(f"FAIL: {len(missing)} 张截图缺失，覆盖不完整")
        return 2
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "build/device-verify"))
