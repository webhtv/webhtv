package com.fongmi.android.tv.theme;

/**
 * Editing session for one B-safe theme profile.
 *
 * <p>The draft is a deep copy. Every mutation is validated before it reaches the
 * draft, so an invalid hex value or an out-of-range opacity can never be applied.
 * {@link #apply()} is the only method that writes to disk; it is atomic and
 * refreshes the legacy {@code theme_color} mirror through {@link ThemeProfileStore}.
 * Cancelling simply drops the draft.
 */
public final class ThemeEditor {

    /** A user-editable slot of one mode. */
    public enum Slot {
        PRIMARY, PRIMARY_CONTAINER, SECONDARY_CONTAINER, FOCUS,
        /**
         * 播放页「当前播放/当前生效」环色。独立于 FOCUS：播放页里两个状态会同时出现，
         * 必须能区分；解析时会在播放页玻璃面板上做可读性夹取。
         */
        PLAYER_CURRENT,
        SURFACE, SURFACE_CONTAINER, SURFACE_CONTAINER_HIGH,
        ON_SURFACE, ON_SURFACE_VARIANT, OUTLINE,
        ERROR, SUCCESS, WARNING,
        /**
         * 条目遮罩底色。遮罩就是列表/按钮聚焦时压在同一块表面上的那层半透明深色，底色、文字色、
         * 强度三者必须一起调才不会互相打架，所以三个槽相邻成组（见 ThemePreviewView 的分组）。
         */
        ITEM_OVERLAY,
        /** 遮罩上的选中/聚焦文字色。 */
        ON_ITEM_OVERLAY,
        SCRIM_OPACITY, DIALOG_OPACITY, OVERLAY_OPACITY,
        /**
         * 播放菜单遮盖层强度。只缩放黑幕 alpha、不改色相，所以仍走 opacity 分支。
         */
        PLAYER_SCRIM_OPACITY,
        /** 条目遮罩强度：只缩放遮罩色的 alpha，不改色相。 */
        ITEM_OVERLAY_OPACITY,
        /**
         * 直播页频道抽屉遮盖层强度。只缩放黑幕 alpha、不改色相，所以仍走 opacity 分支。
         *
         * <p>位置固定在末尾：beta 的 {@code ThemeEditorContractTest#theLiveListScrimSlotIsTheLastSlotAndUsesItsOwnRange}
         * 以「这个槽是最后一个」作为「新槽不会被读成颜色槽」的守卫。合并时 {@code isColor()} 已改成
         * 显式列出不透明度槽（不再依赖 ordinal），但保留该位置约束可以让两边的既有断言都无需改写。
         */
        LIVE_LIST_SCRIM_OPACITY;

        /**
         * 颜色槽 = 除不透明度槽以外的全部槽。这里刻意列出不透明度槽而不是用
         * {@code ordinal() <= WARNING.ordinal()} 的位置判断：条目遮罩组就是插在 WARNING 之后
         * 新增的颜色槽，位置一挪就会被误判成不透明度槽。
         */
        public boolean isColor() {
            return switch (this) {
                case SCRIM_OPACITY, DIALOG_OPACITY, OVERLAY_OPACITY, PLAYER_SCRIM_OPACITY, LIVE_LIST_SCRIM_OPACITY,
                        ITEM_OVERLAY_OPACITY -> false;
                default -> true;
            };
        }
    }

    private final ThemeProfile original;
    private ThemeProfile draft;

    public ThemeEditor(ThemeProfile source) {
        this.original = source == null ? ThemeProfile.defaultProfile() : source.copy();
        this.draft = this.original.copy();
    }

    public static ThemeEditor load() {
        return new ThemeEditor(ThemeProfileStore.load());
    }

    public ThemeProfile draft() {
        return draft.copy();
    }

    public boolean isDirty() {
        return !ThemeProfileCodec.encode(original).equals(ThemeProfileCodec.encode(draft));
    }

    /** Applies a color or opacity value to one slot of one mode. */
    public Result set(Slot slot, boolean dark, String color, float opacity) {
        if (slot == null) return Result.failure("unknown slot");
        ThemeProfile.SlotSet slots = draft.slots(dark);
        if (slot.isColor()) {
            String normalized = ThemeProfileValidator.normalizeColor(color);
            if (normalized == null) return Result.failure("invalid color");
            setColor(slots, slot, normalized);
        } else {
            Double range = clamp(slot, opacity);
            if (range == null) return Result.failure("opacity out of range");
            setOpacity(slots, slot, opacity);
        }
        ThemeProfileValidator.Result validated = ThemeProfileValidator.validate(draft);
        if (!validated.valid()) return Result.failure(validated.message());
        return Result.success(draft.copy());
    }

    /** Clears one slot so it inherits the built-in token or seed result again. */
    public Result clear(Slot slot, boolean dark) {
        if (slot == null) return Result.failure("unknown slot");
        ThemeProfile.SlotSet slots = draft.slots(dark);
        if (slot.isColor()) setColor(slots, slot, null);
        else setOpacity(slots, slot, null);
        return Result.success(draft.copy());
    }

    public Result setMode(String mode) {
        draft.mode = ThemeProfile.normalizeMode(mode);
        return Result.success(draft.copy());
    }

    public Result setPaletteStyle(String style) {
        draft.paletteStyle = ThemeProfile.normalizePaletteStyle(style);
        return Result.success(draft.copy());
    }

    public Result setSeed(String seedSource, String seedColor) {
        String source = ThemeProfile.normalizeSeedSource(seedSource);
        if (ThemeProfile.SEED_CUSTOM.equals(source)) {
            String normalized = ThemeProfileValidator.normalizeColor(seedColor);
            if (normalized == null) return Result.failure("invalid seed color");
            draft.seedSource = source;
            draft.seedColor = normalized;
        } else {
            draft.seedSource = source;
            if (ThemeProfile.SEED_NONE.equals(source)) draft.seedColor = null;
        }
        return Result.success(draft.copy());
    }

    /** Resolves the draft for previewing without touching persisted state. */
    public ThemeTokens preview(ThemeMode mode, ThemeSeed seed, int seedColor, int wallpaperColor, boolean systemDark) {
        return ThemeResolver.resolve(mode, seed, seedColor, wallpaperColor, draft, null, systemDark);
    }

    /** Atomically persists the draft. */
    public ThemeProfileStore.ApplyResult apply() {
        ThemeProfileValidator.Result validated = ThemeProfileValidator.validate(draft);
        if (!validated.valid()) return ThemeProfileStore.ApplyResult.failure(validated.message());
        return ThemeProfileStore.apply(validated.profile());
    }

    /** Resolves the actual editing mode, including the live wallpaper seed, without global state. */
    public ThemeTokens preview(boolean dark, int wallpaperColor) {
        ThemeSeed seed = ThemeProfile.SEED_WALLPAPER.equals(draft.seedSource) ? ThemeSeed.WALLPAPER
                : ThemeProfile.SEED_CUSTOM.equals(draft.seedSource) ? ThemeSeed.EXPLICIT : ThemeSeed.NONE;
        return preview(dark ? ThemeMode.DARK : ThemeMode.LIGHT, seed,
                ThemeProfileValidator.parseColor(draft.seedColor, 0), wallpaperColor, dark);
    }

    /** Replaces only the draft; imports and presets retain the original dirty-state baseline. */
    public Result replace(ThemeProfile profile) {
        ThemeProfileValidator.Result validated = ThemeProfileValidator.validate(profile == null ? null : profile.copy());
        if (!validated.valid()) return Result.failure(validated.message());
        draft = validated.profile();
        return Result.success(draft.copy());
    }

    /** Restores the frozen default in memory. Like every edit, this still needs Apply. */
    public Result reset() {
        return replace(ThemeProfile.defaultProfile());
    }

    public String valueOf(Slot slot, boolean dark) {
        if (slot == null) return null;
        ThemeProfile.SlotSet slots = draft.slots(dark);
        return switch (slot) {
            case PRIMARY -> slots.primary;
            case PRIMARY_CONTAINER -> slots.primaryContainer;
            case SECONDARY_CONTAINER -> slots.secondaryContainer;
            case FOCUS -> slots.focus;
            case PLAYER_CURRENT -> slots.playerCurrent;
            case SURFACE -> slots.surface;
            case SURFACE_CONTAINER -> slots.surfaceContainer;
            case SURFACE_CONTAINER_HIGH -> slots.surfaceContainerHigh;
            case ON_SURFACE -> slots.onSurface;
            case ON_SURFACE_VARIANT -> slots.onSurfaceVariant;
            case OUTLINE -> slots.outline;
            case ERROR -> slots.error;
            case SUCCESS -> slots.success;
            case WARNING -> slots.warning;
            case ITEM_OVERLAY -> slots.itemOverlay;
            case ON_ITEM_OVERLAY -> slots.onItemOverlay;
            case SCRIM_OPACITY -> slots.scrimOpacity == null ? null : String.valueOf(slots.scrimOpacity);
            case DIALOG_OPACITY -> slots.dialogOpacity == null ? null : String.valueOf(slots.dialogOpacity);
            case OVERLAY_OPACITY -> slots.overlayOpacity == null ? null : String.valueOf(slots.overlayOpacity);
            case PLAYER_SCRIM_OPACITY -> slots.playerScrimOpacity == null ? null : String.valueOf(slots.playerScrimOpacity);
            case LIVE_LIST_SCRIM_OPACITY -> slots.liveListScrimOpacity == null ? null : String.valueOf(slots.liveListScrimOpacity);
            case ITEM_OVERLAY_OPACITY -> slots.itemOverlayOpacity == null ? null : String.valueOf(slots.itemOverlayOpacity);
        };
    }

    private static void setColor(ThemeProfile.SlotSet slots, Slot slot, String value) {
        switch (slot) {
            case PRIMARY -> slots.primary = value;
            case PRIMARY_CONTAINER -> slots.primaryContainer = value;
            case SECONDARY_CONTAINER -> slots.secondaryContainer = value;
            case FOCUS -> slots.focus = value;
            case PLAYER_CURRENT -> slots.playerCurrent = value;
            case SURFACE -> slots.surface = value;
            case SURFACE_CONTAINER -> slots.surfaceContainer = value;
            case SURFACE_CONTAINER_HIGH -> slots.surfaceContainerHigh = value;
            case ON_SURFACE -> slots.onSurface = value;
            case ON_SURFACE_VARIANT -> slots.onSurfaceVariant = value;
            case OUTLINE -> slots.outline = value;
            case ERROR -> slots.error = value;
            case SUCCESS -> slots.success = value;
            case WARNING -> slots.warning = value;
            case ITEM_OVERLAY -> slots.itemOverlay = value;
            case ON_ITEM_OVERLAY -> slots.onItemOverlay = value;
            default -> throw new IllegalArgumentException("not a color slot");
        }
    }

    private static void setOpacity(ThemeProfile.SlotSet slots, Slot slot, Float value) {
        switch (slot) {
            case SCRIM_OPACITY -> slots.scrimOpacity = value;
            case DIALOG_OPACITY -> slots.dialogOpacity = value;
            case OVERLAY_OPACITY -> slots.overlayOpacity = value;
            case PLAYER_SCRIM_OPACITY -> slots.playerScrimOpacity = value;
            case LIVE_LIST_SCRIM_OPACITY -> slots.liveListScrimOpacity = value;
            case ITEM_OVERLAY_OPACITY -> slots.itemOverlayOpacity = value;
            default -> throw new IllegalArgumentException("not an opacity slot");
        }
    }

    /** Returns the allowed range when the value is valid, otherwise null. */
    public static Double clamp(Slot slot, float value) {
        if (!Float.isFinite(value)) return null;
        return switch (slot) {
            case SCRIM_OPACITY -> inRange(value, ThemeProfileValidator.MIN_SCRIM_OPACITY, ThemeProfileValidator.MAX_SCRIM_OPACITY);
            case DIALOG_OPACITY -> inRange(value, ThemeProfileValidator.MIN_DIALOG_OPACITY, ThemeProfileValidator.MAX_DIALOG_OPACITY);
            case OVERLAY_OPACITY -> inRange(value, ThemeProfileValidator.MIN_OVERLAY_OPACITY, ThemeProfileValidator.MAX_OVERLAY_OPACITY);
            case PLAYER_SCRIM_OPACITY -> inRange(value, ThemeProfileValidator.MIN_PLAYER_SCRIM_OPACITY, ThemeProfileValidator.MAX_PLAYER_SCRIM_OPACITY);
            case LIVE_LIST_SCRIM_OPACITY -> inRange(value, ThemeProfileValidator.MIN_LIVE_LIST_SCRIM_OPACITY, ThemeProfileValidator.MAX_LIVE_LIST_SCRIM_OPACITY);
            case ITEM_OVERLAY_OPACITY -> inRange(value, ThemeProfileValidator.MIN_ITEM_OVERLAY_OPACITY, ThemeProfileValidator.MAX_ITEM_OVERLAY_OPACITY);
            default -> null;
        };
    }

    private static Double inRange(float value, float minimum, float maximum) {
        return value < minimum || value > maximum ? null : (double) value;
    }

    public record Result(boolean success, ThemeProfile profile, String error) {

        public static Result success(ThemeProfile profile) {
            return new Result(true, profile, "");
        }

        public static Result failure(String error) {
            return new Result(false, null, error == null ? "unknown error" : error);
        }
    }
}
