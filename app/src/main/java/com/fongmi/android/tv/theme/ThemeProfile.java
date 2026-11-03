package com.fongmi.android.tv.theme;

import java.util.Locale;

/** Persisted B-safe theme profile limited to the 22 user-editable semantic slots. */
public final class ThemeProfile {

    public static final int SCHEMA_VERSION = 2;
    public static final String FORMAT = "webhtv-theme";
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String SEED_NONE = "none";
    public static final String SEED_WALLPAPER = "wallpaper";
    public static final String SEED_CUSTOM = "custom";

    public int schemaVersion = SCHEMA_VERSION;
    public String format = FORMAT;
    public String id = "webhtv.local";
    public String name = "Default";
    public String mode = MODE_SYSTEM;
    public String paletteStyle = ThemePaletteStyle.TONAL_SPOT.id();
    public String seedSource = SEED_NONE;
    public String seedColor;
    public SlotSet light = new SlotSet();
    public SlotSet dark = new SlotSet();

    public ThemeProfile() {
    }

    public static ThemeProfile defaultProfile() {
        return new ThemeProfile();
    }

    public ThemeProfile copy() {
        ThemeProfile copy = new ThemeProfile();
        copy.schemaVersion = schemaVersion;
        copy.format = format;
        copy.id = id;
        copy.name = name;
        copy.mode = mode;
        copy.paletteStyle = paletteStyle;
        copy.seedSource = seedSource;
        copy.seedColor = seedColor;
        copy.light = light == null ? null : light.copy();
        copy.dark = dark == null ? null : dark.copy();
        return copy;
    }

    public SlotSet slots(boolean dark) {
        if (dark) {
            if (this.dark == null) this.dark = new SlotSet();
            return this.dark;
        }
        if (light == null) light = new SlotSet();
        return light;
    }

    public String displayName() {
        return name == null || name.isBlank() ? "Default" : name;
    }

    public static String normalizeMode(String value) {
        if (MODE_LIGHT.equals(value)) return MODE_LIGHT;
        if (MODE_DARK.equals(value)) return MODE_DARK;
        return MODE_SYSTEM;
    }

    public static String normalizePaletteStyle(String value) {
        return ThemePaletteStyle.from(value).id();
    }

    public static String normalizeSeedSource(String value) {
        if (SEED_CUSTOM.equals(value)) return SEED_CUSTOM;
        if (SEED_WALLPAPER.equals(value)) return SEED_WALLPAPER;
        return SEED_NONE;
    }

    /** The 16 color slots plus 6 opacity slots a user may edit. */
    public static final class SlotSet {
        public String primary;
        public String primaryContainer;
        public String secondaryContainer;
        public String focus;
        /**
         * 播放页「当前播放/当前生效」环色。与 focus 分开，因为这两个状态在播放页同时存在、
         * 必须可区分；取值会在播放页固定玻璃面板上做可读性夹取（ThemeResolver）。
         */
        public String playerCurrent;
        public String surface;
        public String surfaceContainer;
        public String surfaceContainerHigh;
        public String onSurface;
        public String onSurfaceVariant;
        public String outline;
        public String error;
        public String success;
        public String warning;
        /**
         * 条目遮罩底色。列表/按钮聚焦时压在封面与标题上的那层半透明深色就是它。
         * null 表示沿用编译期 {@code @color/black_40}（{@code #66000000}）的取值，因此空
         * profile 的解析结果与改动前字节一致；强度单独由 {@link #itemOverlayOpacity} 控制，
         * 颜色槽本身仍只接受不透明 #RRGGBB（与其它颜色槽同一套 B-safe 校验）。
         */
        public String itemOverlay;
        /**
         * 遮罩上的选中/聚焦文字色。null 表示沿用 {@code colorOnPrimary}：遮罩上的文字本来就是
         * onPrimary 一族、跟随明暗表；只有用户显式改过，才在「遮罩复合底色」上夹取 ≥4.5:1。
         */
        public String onItemOverlay;
        public Float scrimOpacity;
        public Float dialogOpacity;
        public Float overlayOpacity;
        /**
         * 条目遮罩强度：只缩放 {@link #itemOverlay} 的 alpha，不改色相。null 表示不缩放，
         * 于是默认遮罩仍是编译期的 40% 黑（{@code #66000000}）。
         */
        public Float itemOverlayOpacity;
        /**
         * 播放菜单遮盖层（播放页底部控制条黑幕）的强度，只缩放 alpha，色相固定取
         * colorPlayerScrim；null 表示回落到 {@code PlaybackMenuScrim.DEFAULT_OPACITY}。
         */
        public Float playerScrimOpacity;
        /**
         * 直播页频道抽屉（列表）遮盖层的强度，只缩放 alpha，色相固定为纯黑；null 表示回落到
         * {@code LiveListScrim.DEFAULT_OPACITY}，此时渲染结果与 shape_live_list.xml 逐值一致。
         */
        public Float liveListScrimOpacity;

        public SlotSet copy() {
            SlotSet copy = new SlotSet();
            copy.primary = primary;
            copy.primaryContainer = primaryContainer;
            copy.secondaryContainer = secondaryContainer;
            copy.focus = focus;
            copy.playerCurrent = playerCurrent;
            copy.surface = surface;
            copy.surfaceContainer = surfaceContainer;
            copy.surfaceContainerHigh = surfaceContainerHigh;
            copy.onSurface = onSurface;
            copy.onSurfaceVariant = onSurfaceVariant;
            copy.outline = outline;
            copy.error = error;
            copy.success = success;
            copy.warning = warning;
            copy.itemOverlay = itemOverlay;
            copy.onItemOverlay = onItemOverlay;
            copy.scrimOpacity = scrimOpacity;
            copy.dialogOpacity = dialogOpacity;
            copy.overlayOpacity = overlayOpacity;
            copy.itemOverlayOpacity = itemOverlayOpacity;
            copy.playerScrimOpacity = playerScrimOpacity;
            copy.liveListScrimOpacity = liveListScrimOpacity;
            return copy;
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s (%s)", displayName(), mode);
    }
}
