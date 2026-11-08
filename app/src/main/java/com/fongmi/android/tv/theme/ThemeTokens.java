package com.fongmi.android.tv.theme;

/** Immutable Material 3 semantic color and player-control contract. */
public record ThemeTokens(
        int colorPrimary,
        int colorOnPrimary,
        int colorPrimaryContainer,
        int colorOnPrimaryContainer,
        int colorSecondary,
        int colorOnSecondary,
        int colorSecondaryContainer,
        int colorOnSecondaryContainer,
        int colorTertiary,
        int colorOnTertiary,
        int colorError,
        int colorOnError,
        int colorErrorContainer,
        int colorOnErrorContainer,
        int colorSuccess,
        int colorOnSuccess,
        int colorSuccessContainer,
        int colorOnSuccessContainer,
        int colorWarning,
        int colorOnWarning,
        int colorWarningContainer,
        int colorOnWarningContainer,
        int colorSurface,
        int colorSurfaceDim,
        int colorSurfaceBright,
        int colorSurfaceContainerLowest,
        int colorSurfaceContainerLow,
        int colorSurfaceContainer,
        int colorSurfaceContainerHigh,
        int colorSurfaceContainerHighest,
        int colorOnSurface,
        int colorOnSurfaceVariant,
        int colorOutline,
        int colorOutlineVariant,
        int colorInverseSurface,
        int colorInverseOnSurface,
        int colorInversePrimary,
        int colorScrim,
        int colorShadow,
        int colorFocus,
        float focusScale,
        /**
         * 播放页（视频层）焦点环色。由 FOCUS 槽派生并在固定玻璃面板上做了 ≥4.5:1 的可读性夹取，
         * 因为播放页控制条是叠在视频上的固定深靛玻璃：浅色表的 FOCUS 深蓝在它上面只有 1.83:1。
         */
        int colorPlayerFocusRing,
        /** 播放页「当前播放/当前生效」环色。有独立的用户可编辑槽位（profile 的 playerCurrent）。 */
        int colorPlayerCurrentRing,
        float dialogOpacity,
        int colorPlayerControl,
        int colorPlayerControlMuted,
        int colorPlayerControlActive,
        int colorPlayerScrim,
        int colorHealthGood,
        int colorHealthWarn,
        int colorHealthBad,
        int colorOverlayLight,
        int colorOverlayDark,
        /**
         * 条目遮罩底色（已含 alpha）：默认 {@code #66000000}，即编译期 {@code @color/black_40}
         * 的取值，透明度由 profile 的 itemOverlayOpacity 槽缩放。
         *
         * <p>前缀 color 不是命名洁癖：{@code ThemeContractTest} 用组件名推断同族资源名
         * （{@code "webhtv_color_" + snakeCase(name.substring(5))}），组件名必须带 color 前缀
         * 才能推断出 {@code webhtv_color_item_overlay}。
         */
        int colorItemOverlay,
        /** 遮罩上的选中/聚焦文字色。默认等于本表的 {@link #colorOnPrimary}。 */
        int colorOnItemOverlay
) {

    public static ThemeTokens light() {
        return new ThemeTokens(
            0xFF0B57D0,
            0xFFFFFFFF,
            0xFFD3E3FD,
            0xFF041E49,
            0xFF5B5F63,
            0xFFFFFFFF,
            0xFFDFE2E6,
            0xFF191C1E,
            0xFF00639B,
            0xFFFFFFFF,
            0xFFB3261E,
            0xFFFFFFFF,
            0xFFF9DEDC,
            0xFF410E0B,
            0xFF146C2E,
            0xFFFFFFFF,
            0xFFC4EED0,
            0xFF00210B,
            0xFF7A5900,
            0xFFFFFFFF,
            0xFFFFDF9A,
            0xFF261A00,
            0xFFF8FAFD,
            0xFFD8DAE0,
            0xFFF8FAFD,
            0xFFFFFFFF,
            0xFFF2F3F9,
            0xFFECEEF4,
            0xFFE7E8EF,
            0xFFE1E2E9,
            0xFF1A1C1E,
            0xFF44474F,
            0xFF74777F,
            0xFFC4C6CF,
            0xFF2F3033,
            0xFFF1F0F4,
            0xFFA8C7FA,
            0x52000000,
            0xFF000000,
            0xFF0B57D0,
            // 焦点缩放倍率与明暗模式无关：它是**动效**属性，不是配色属性。此前浅色表为
            // 1.0、深色表为 1.1，而各适配器一律写死 1.1，于是浅色主题的电视一旦改走 token
            // 就会丢掉缩放反馈。这里统一为 1.1（= 既有实机行为，也是 Android TV 按钮
            // 指南与设计文档 §5.2 记录的取值）。
            1.1f,
            // 浅色表视频层环：FOCUS 深蓝在固定深靛玻璃上仅 1.83:1，
            // 取 ThemeResolver 夹取到 ≥3:1 的派生值（与 values/webhtv_tokens.xml 同步）。
            0xFF447BF5,
            0xFF00A95A,
            1.0f,
            0xFFFFFFFF,
            0xCCFFFFFF,
            0xFFFFD35C,
            0x7A000000,
            0xFF146C2E,
            0xFF7A5900,
            0xFFB3261E,
            0x14FFFFFF,
            0x52000000,
            // 同上：遮罩 = @color/black_40，遮罩文字 = 本表 colorOnPrimary（浅色表为白）。
            0x66000000,
            0xFFFFFFFF
    );
    }

    public static ThemeTokens dark() {
        return new ThemeTokens(
            0xFFA8C7FA,
            0xFF062E6F,
            0xFF0842A0,
            0xFFD3E3FD,
            0xFFC6C6C9,
            0xFF2F3133,
            0xFF44474A,
            0xFFE2E2E6,
            0xFF8ECDFF,
            0xFF003353,
            0xFFFFB4AB,
            0xFF690005,
            0xFF93000A,
            0xFFFFDAD6,
            0xFF8EDB9F,
            0xFF003918,
            0xFF00522A,
            0xFFC4EED0,
            0xFFF2C66D,
            0xFF402D00,
            0xFF5C4200,
            0xFFFFDF9A,
            0xFF101418,
            0xFF0B0E11,
            0xFF363A3F,
            0xFF0B0E11,
            0xFF181C20,
            0xFF1F2428,
            0xFF2A2F34,
            0xFF34393E,
            0xFFE2E2E9,
            0xFFC4C6D0,
            0xFF8E9099,
            0xFF44474F,
            0xFFE2E2E9,
            0xFF2F3033,
            0xFF0B57D0,
            0x8A000000,
            0xFF000000,
            0xFFF5F7FF,
            1.1f,
            // 深色表视频层环 = 应用表面焦点环（近白）：深玻璃/深色画面都可读，
            // 因此播放页只有一种高亮色。浅色表仍为固定玻璃上的派生夹取值。
            0xFFF5F7FF,
            0xFF00A95A,
            1.0f,
            0xFFFFFFFF,
            0xCCFFFFFF,
            0xFFFFD35C,
            0x8F000000,
            0xFF8EDB9F,
            0xFFF2C66D,
            0xFFFFB4AB,
            0x14FFFFFF,
            // 条目遮罩沿用编译期 @color/black_40（#66000000）；遮罩上的文字沿用本表的
            // colorOnPrimary，因此空 profile 的解析结果与改动前字节一致。
            0x66000000,
            0x66000000,
            0xFF062E6F
    );
    }

    public ThemeTokens requireContrast() {
        ThemeContrast.require(this);
        return this;
    }
}
