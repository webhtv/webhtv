package com.fongmi.android.tv.theme;

/** Produces a bounded, read-only token snapshot for trusted WebTheme pages. */
public final class ThemeWebBridge {

    private ThemeWebBridge() {
    }

    public static String snapshotJson(ThemeTokens tokens) {
        ThemeTokens safe = tokens == null ? ThemeTokens.light() : tokens;
        return "{\"primary\":\"" + hex(safe.colorPrimary()) + "\""
                + ",\"onPrimary\":\"" + hex(safe.colorOnPrimary()) + "\""
                + ",\"surface\":\"" + hex(safe.colorSurface()) + "\""
                + ",\"surfaceContainer\":\"" + hex(safe.colorSurfaceContainer()) + "\""
                + ",\"surfaceContainerHigh\":\"" + hex(safe.colorSurfaceContainerHigh()) + "\""
                + ",\"onSurface\":\"" + hex(safe.colorOnSurface()) + "\""
                + ",\"onSurfaceVariant\":\"" + hex(safe.colorOnSurfaceVariant()) + "\""
                + ",\"outline\":\"" + hex(safe.colorOutline()) + "\""
                + ",\"outlineVariant\":\"" + hex(safe.colorOutlineVariant()) + "\""
                + ",\"error\":\"" + hex(safe.colorError()) + "\""
                + ",\"success\":\"" + hex(safe.colorSuccess()) + "\""
                + ",\"warning\":\"" + hex(safe.colorWarning()) + "\""
                + ",\"focus\":\"" + hex(safe.colorFocus()) + "\""
                + ",\"focusScale\":" + decimal(safe.focusScale())
                + ",\"scrimOpacity\":" + decimal(alpha(safe.colorScrim()))
                + ",\"dialogOpacity\":" + decimal(safe.dialogOpacity())
                + ",\"overlayOpacity\":" + decimal(alpha(safe.colorOverlayLight()))
                // 条目遮罩是本轮新增的用户可编辑机制，页面只拿 overlayOpacity 画不出原生列表的
                // 聚焦遮罩，所以补三个只读派生字段（一共 20 个字段，字段集合仍由本类固定，页面只读）。
                + ",\"itemOverlay\":\"" + hex(safe.colorItemOverlay()) + "\""
                + ",\"onItemOverlay\":\"" + hex(safe.colorOnItemOverlay()) + "\""
                + ",\"itemOverlayOpacity\":" + decimal(alpha(safe.colorItemOverlay())) + "}";
    }

    private static String hex(int color) {
        return String.format(java.util.Locale.US, "#%08X", color);
    }

    /** Alpha channel of a colour as a stable 2-decimal fraction. */
    private static float alpha(int color) {
        return ((color >>> 24) & 0xFF) / 255f;
    }

    private static String decimal(float value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }
}
