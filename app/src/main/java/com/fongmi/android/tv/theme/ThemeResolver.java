package com.fongmi.android.tv.theme;

import com.google.android.material.color.utilities.DynamicScheme;
import com.google.android.material.color.utilities.Hct;

/** Resolves semantic tokens without exposing seed colors directly to UI surfaces. */
public final class ThemeResolver {

    private static final ThreadLocal<String> LAST_DIAGNOSTIC = ThreadLocal.withInitial(() -> "default");

    /** Material's own baseline scheme keeps primary at or above this on every surface role. */
    private static final double MIN_ACCENT_CONTRAST = 4.5;

    /**
     * 播放页（视频层）环色必须可读的背景。
     *
     * <p>视频层环色的宿主是**固定深色玻璃**：控制面板
     * ({@code shape_dialog_control_glass_panel})、快捷搜索/选集面板
     * ({@code shape_quick_search_dialog})、播放器底部抽屉，三档渐变都是
     * {@code #E62F315E → #D6282955 → #CC303463}，叠在任何画面上都保持深色。
     * 浅色表的 FOCUS 深蓝 #0B57D0 在它们上面只有 1.83:1（dev1 实机实测），
     * 所以浅色表必须夹取到 ≥3:1（派生结果 #447BF5）——这正是本方法存在的理由。
     *
     * <p><b>纯白视频（雪/白墙）刻意不在列表里。</b>用户报告（2026-10-11）
     * 「播放页存在多个不同颜色的高亮效果」：把纯白视频也作为约束，会把深色表的
     * 近白环压成灰蓝 #7695C5，于是同一页出现「应用表面近白 + 视频层灰蓝」两种高亮。
     * 深色表两类宿主的实际底色都是深色，近白环对它们全部可读，所以深色表统一取近白
     * （与应用表面焦点环同值），只有浅色表保留夹取。
     * 已知取舍：近白环在纯白视频上对比度不足，仅在「亮场景视频 + 焦点落在画面框/播控条」
     * 时出现，而绝大多数播放页宿主的局部底色是深玻璃。
     */
    private static final int[] PLAYER_RING_BACKDROPS = {
            0xFF2F315E, 0xFF282955, 0xFF303463,   // 控制面板固定玻璃渐变的三档
    };

    /**
     * 播放页环色的对比度门槛。取 WCAG 2.2 SC 1.4.11「非文本对比度」的 3:1（与
     * {@code outline}/{@code focus} 的 {@code ensureContrast(..., 3.0)} 同一口径）；
     * 4.5:1 是正文文本的档位，用在环上会把浅色表的蓝压得过暗。
     */
    private static final double MIN_PLAYER_RING_CONTRAST = 3.0;

    private ThemeResolver() {
    }

    public static ThemeTokens resolve(ThemeMode mode, ThemeSeed seed, int seedColor,
                                      int wallpaperColor, boolean systemDark) {
        return resolve(mode, seed, seedColor, wallpaperColor, null, systemDark);
    }

    /**
     * Resolves the frozen palette, an optional seed, and the B-safe profile in one
     * deterministic order. A profile may only narrow the 16 user slots; it cannot
     * reach player colors or the derived on* pairs.
     */
    public static ThemeTokens resolve(ThemeMode mode, ThemeSeed seed, int seedColor,
                                      int wallpaperColor, ThemeProfile profile, boolean systemDark) {
        ThemeProfile lastGood = null;
        try {
            lastGood = ThemeProfileStore.loadLastGood();
        } catch (RuntimeException ignored) {
            // Android preference access is unavailable (for example in a JVM test); defaults remain valid.
        }
        return resolve(mode, seed, seedColor, wallpaperColor, profile, lastGood, systemDark);
    }

    /** Testable overload with an explicit last-good fallback. */
    public static ThemeTokens resolve(ThemeMode mode, ThemeSeed seed, int seedColor, int wallpaperColor,
                                      ThemeProfile profile, ThemeProfile lastGood, boolean systemDark) {
        boolean dark = mode == ThemeMode.DARK || (mode != ThemeMode.LIGHT && systemDark);
        ThemeTokens fallback = dark ? ThemeTokens.dark() : ThemeTokens.light();
        ThemeTokens base = fallback;
        String path = "default";
        ThemePaletteStyle paletteStyle = profile == null
                ? ThemePaletteStyle.TONAL_SPOT
                : ThemePaletteStyle.from(profile.paletteStyle);
        try {
            if (seed != null && seed != ThemeSeed.NONE) {
                int source = seed == ThemeSeed.WALLPAPER ? wallpaperColor : seedColor;
                if (!isOpaque(source)) {
                    LAST_DIAGNOSTIC.set("fallback:invalid-seed:" + seed.name().toLowerCase(java.util.Locale.US));
                    return fallback;
                }
                base = derive(source, dark, fallback, paletteStyle);
                path = "seed:" + seed.name().toLowerCase(java.util.Locale.US);
            } else if (profile != null && paletteStyle != ThemePaletteStyle.TONAL_SPOT) {
                base = derive(paletteStyle.defaultSeed(), dark, fallback, paletteStyle);
                path = "palette:" + paletteStyle.id();
            }
            ThemeTokens candidate = profile == null ? base : applyProfile(base, profile, dark);
            candidate.requireContrast();
            LAST_DIAGNOSTIC.set(profile == null ? path : path + "+profile:" + profile.displayName());
            return candidate;
        } catch (RuntimeException error) {
            String cause = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            if (lastGood != null && lastGood != profile) {
                try {
                    ThemeTokens recovered = applyProfile(base, lastGood, dark);
                    recovered.requireContrast();
                    LAST_DIAGNOSTIC.set("last-good:" + cause);
                    return recovered;
                } catch (RuntimeException ignored) {
                    // Fall through to the frozen default palette.
                }
            }
            LAST_DIAGNOSTIC.set("fallback:profile-resolve:" + cause);
            return fallback;
        }
    }

    /**
     * Applies only the slots the user actually overrode. Empty/null slots keep the
     * resolved base value, and on* pairs are recomputed from black/white contrast so
     * the profile cannot create unreadable foreground/background pairs.
     */
    static ThemeTokens applyProfile(ThemeTokens base, ThemeProfile profile, boolean dark) {
        ThemeProfileValidator.Result validated = ThemeProfileValidator.validate(profile);
        if (!validated.valid()) throw new IllegalArgumentException(validated.message());
        ThemeProfile.SlotSet slots = dark ? validated.profile().dark : validated.profile().light;

        boolean hasPrimary = slots.primary != null;
        boolean hasPrimaryContainer = slots.primaryContainer != null;
        boolean hasSecondaryContainer = slots.secondaryContainer != null;
        boolean hasFocus = slots.focus != null;
        boolean hasSurface = slots.surface != null;
        boolean hasContainer = slots.surfaceContainer != null;
        boolean hasContainerHigh = slots.surfaceContainerHigh != null;
        boolean hasOnSurface = slots.onSurface != null;
        boolean hasOnSurfaceVariant = slots.onSurfaceVariant != null;
        boolean hasOutline = slots.outline != null;
        boolean hasError = slots.error != null;
        boolean hasSuccess = slots.success != null;
        boolean hasWarning = slots.warning != null;

        int primary = color(slots.primary, base.colorPrimary());
        int primaryContainer = color(slots.primaryContainer, base.colorPrimaryContainer());
        int secondaryContainer = color(slots.secondaryContainer, base.colorSecondaryContainer());
        int surface = color(slots.surface, base.colorSurface());
        int surfaceContainer = color(slots.surfaceContainer, base.colorSurfaceContainer());
        int surfaceContainerHigh = color(slots.surfaceContainerHigh, base.colorSurfaceContainerHigh());
        if (hasContainer && surfaceContainer == surface) {
            surfaceContainer = mix(surface, dark ? 0xFFFFFFFF : 0xFF000000, 0.06);
        }
        if (hasContainerHigh && surfaceContainerHigh == surfaceContainer) {
            surfaceContainerHigh = mix(surfaceContainer, dark ? 0xFFFFFFFF : 0xFF000000, 0.06);
        }
        int surfaceContainerHighest = hasContainer || hasContainerHigh
                ? mix(surfaceContainerHigh, surfaceContainer, 0.35)
                : base.colorSurfaceContainerHighest();
        // Material 3 derives primary and the surface roles from one tonal palette, so
        // primary is legible on every surface role by construction (the shipped baseline
        // scheme measures 4.97-10.91:1). The editor exposes both slots independently,
        // which can break that guarantee - primary is also the dialog action-button text
        // colour, so a broken pair renders actions invisible. Restoring the guarantee is
        // what the documented B-safe contract promises: a profile must never be able to
        // create an unreadable pair. The move keeps hue and chroma and only shifts tone.
        int readablePrimary = readableAccent(
                primary, surface, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest);
        boolean primaryAdjusted = readablePrimary != primary;
        primary = readablePrimary;
        int error = color(slots.error, base.colorError());
        int success = color(slots.success, base.colorSuccess());
        int warning = color(slots.warning, base.colorWarning());

        // Derived pairs are only recomputed when the user actually touched a slot that
        // feeds them; an empty profile must stay byte-identical to the frozen palette.
        int onPrimary = (hasPrimary || primaryAdjusted)
                ? readableOn(primary, base.colorOnPrimary()) : base.colorOnPrimary();
        int onPrimaryContainer = hasPrimaryContainer
                ? readableOn(primaryContainer, base.colorOnPrimaryContainer()) : base.colorOnPrimaryContainer();
        int onSecondaryContainer = hasSecondaryContainer
                ? readableOn(secondaryContainer, base.colorOnSecondaryContainer()) : base.colorOnSecondaryContainer();
        int onError = hasError ? readableOn(error, base.colorOnError()) : base.colorOnError();
        int onSuccess = hasSuccess ? readableOn(success, base.colorOnSuccess()) : base.colorOnSuccess();
        int onWarning = hasWarning ? readableOn(warning, base.colorOnWarning()) : base.colorOnWarning();

        boolean surfaceChanged = hasSurface || hasContainer || hasContainerHigh;
        int onSurface = surfaceChanged || hasOnSurface
                ? enforceBase(color(slots.onSurface, base.colorOnSurface()), surface,
                        hasContainer ? surfaceContainer : -1,
                        hasContainerHigh ? surfaceContainerHigh : -1)
                : base.colorOnSurface();
        int onSurfaceVariant = surfaceChanged || hasOnSurfaceVariant
                ? enforceBase(color(slots.onSurfaceVariant, base.colorOnSurfaceVariant()), surface,
                        hasContainer ? surfaceContainer : -1, -1)
                : base.colorOnSurfaceVariant();
        int outline = hasOutline || hasSurface
                ? ensureContrast(color(slots.outline, base.colorOutline()), surface, 3.0)
                : base.colorOutline();
        // The frozen palette deliberately ships focus == primary. Keeping that relationship
        // matters beyond aesthetics: the binder resolves a view colour by its semantic role,
        // and a baseline colour shared by two roles is only rewritten when both agree. If
        // focus drifted away from primary, primary-as-text views (dialog actions) would keep
        // their compiled colour and stay unreadable. `primary` already clears 4.5:1 on the
        // surface, so it also clears the 3.0:1 focus requirement.
        // A user-chosen focus colour is clamped exactly like primary: hue and chroma are kept
        // and only tone moves. Snapping to black/white instead turned a legible deep red such
        // as #8C0000 (1.86:1 on the dark surface) into pure #FFFFFF, which is why the editor
        // showed "输入 #8C0000 / 显示 #FFFFFF" and every dark-surface ring came out white.
        int focus = hasFocus || hasSurface
                ? readableAccent(color(slots.focus, primary), 3.0, surface)
                : base.colorFocus();

        int surfaceDim = hasSurface ? mix(surface, 0xFF000000, dark ? 0.06 : 0.10) : base.colorSurfaceDim();
        int surfaceBright = hasSurface ? mix(surface, 0xFFFFFFFF, dark ? 0.14 : 0.08) : base.colorSurfaceBright();

        // ---------------------------------------------------------------- 播放页（视频层）环色
        // 视频层环色的宿主是固定深色玻璃与深色视频画面，与应用表面无关，所以不能直接用
        // focus：浅色表的 focus #0B57D0 在固定深靛玻璃上只有 1.83:1（实机 dev1 复核），
        // 焦点不可见。因此用仓库已有的 readableAccent：保持用户 FOCUS 槽的色相与彩度、
        // 只走明度，直到在固定玻璃上达到 MIN_PLAYER_RING_CONTRAST(3.0)。
        // 深色表的 focus 本身就是近白 #F5F7FF，对深玻璃与深色 video 都远远达标，夹取是
        // 恒等变换——这正是用户要的「播放页只有一种高亮色」：深色表下视频层环 = 应用表面环。
        int playerFocusRing = hasFocus
                ? readableAccent(focus, MIN_PLAYER_RING_CONTRAST, PLAYER_RING_BACKDROPS)
                : base.colorPlayerFocusRing();
        // 「当前播放」有独立的用户槽位（默认绿）；同样做固定玻璃上的可读性夹取。
        boolean hasPlayerCurrent = slots.playerCurrent != null;
        int playerCurrentRing = hasPlayerCurrent
                ? readableAccent(color(slots.playerCurrent, base.colorPlayerCurrentRing()),
                        MIN_PLAYER_RING_CONTRAST, PLAYER_RING_BACKDROPS)
                : base.colorPlayerCurrentRing();

        float scrimOpacity = slots.scrimOpacity == null ? (base.colorScrim() >>> 24) / 255f : slots.scrimOpacity;
        float dialogOpacity = slots.dialogOpacity == null ? base.dialogOpacity() : slots.dialogOpacity;
        float overlayOpacity = slots.overlayOpacity == null ? (base.colorOverlayLight() >>> 24) / 255f : slots.overlayOpacity;

        // ---------------------------------------------------------------- 条目遮罩
        // 遮罩底色默认取编译期 @color/black_40（#66000000）：强度槽为空时**完全不动 alpha**，
        // 空 profile 解析出的 token 才与改动前的静态资源字节一致。用户改过颜色槽之后才用强度槽
        // 重写 alpha（空则视作 1.0），因为颜色槽只接受不透明 #RRGGBB，本身没有 alpha 可继承。
        boolean hasItemOverlay = slots.itemOverlay != null;
        boolean hasItemOverlayOpacity = slots.itemOverlayOpacity != null;
        int itemOverlayColor = hasItemOverlay
                ? ThemeProfileValidator.parseColor(slots.itemOverlay, base.colorItemOverlay())
                : base.colorItemOverlay();
        int itemOverlay = hasItemOverlayOpacity
                ? withAlpha(itemOverlayColor, slots.itemOverlayOpacity) : itemOverlayColor;
        // 遮罩上的文字：用户显式改过才夹取可读性（保持色相、只走明度，与 FOCUS 槽同一口径）。
        // 夹取的宿主是「遮罩压在 surface 各层上」的复合底色——遮罩自带 alpha，直接拿 surface 比
        // 会高估对比度。用户没改文字色但遮罩变了（底色或强度）时，仍用既有的 readableOn 在这几个
        // 复合底色上重挑黑/白：否则默认的选中文字压在用户选的不透明浅色遮罩上会跌到 4.5:1 以下。
        int onItemOverlay;
        if (slots.onItemOverlay != null) {
            onItemOverlay = readableAccent(color(slots.onItemOverlay, base.colorOnItemOverlay()),
                    MIN_ACCENT_CONTRAST,
                    overlayBackdrops(itemOverlay, surface, surfaceContainer, surfaceContainerHigh));
        } else if (hasItemOverlay || hasItemOverlayOpacity) {
            onItemOverlay = readableOverlayText(base.colorOnItemOverlay(),
                    overlayBackdrops(itemOverlay, surface, surfaceContainer, surfaceContainerHigh));
        } else {
            onItemOverlay = base.colorOnItemOverlay();
        }

        return new ThemeTokens(
                primary, onPrimary, primaryContainer, onPrimaryContainer,
                base.colorSecondary(), base.colorOnSecondary(),
                secondaryContainer, onSecondaryContainer,
                base.colorTertiary(), base.colorOnTertiary(),
                error, onError, base.colorErrorContainer(), base.colorOnErrorContainer(),
                success, onSuccess, base.colorSuccessContainer(), base.colorOnSuccessContainer(),
                warning, onWarning, base.colorWarningContainer(), base.colorOnWarningContainer(),
                surface, surfaceDim, surfaceBright, base.colorSurfaceContainerLowest(),
                base.colorSurfaceContainerLow(), surfaceContainer, surfaceContainerHigh, surfaceContainerHighest,
                onSurface, onSurfaceVariant, outline, base.colorOutlineVariant(),
                base.colorInverseSurface(), base.colorInverseOnSurface(), base.colorInversePrimary(),
                withAlpha(base.colorScrim(), scrimOpacity), base.colorShadow(), focus, base.focusScale(),
                playerFocusRing, playerCurrentRing, dialogOpacity,
                base.colorPlayerControl(), base.colorPlayerControlMuted(), base.colorPlayerControlActive(), base.colorPlayerScrim(),
                base.colorHealthGood(), base.colorHealthWarn(), base.colorHealthBad(),
                withAlpha(base.colorOverlayLight(), overlayOpacity), base.colorOverlayDark(),
                itemOverlay, onItemOverlay
        );
    }

    /**
     * 条目遮罩真正压在什么底色上：遮罩自身的 alpha 与 surface 各层复合后的结果。
     *
     * <p>遮罩是半透明的，用户又可能只改强度，因此对比度必须按复合后的实色算；
     * 直接拿 surface 当宿主会把默认白字算成 4.5:1 以上，实机却是 3.2:1。
     */
    private static int[] overlayBackdrops(int itemOverlay, int... surfaces) {
        int[] backdrops = new int[surfaces.length];
        for (int index = 0; index < surfaces.length; index++) {
            backdrops[index] = ThemeContrast.composite(itemOverlay, surfaces[index]);
        }
        return backdrops;
    }

    /**
     * 用户只改了遮罩（底色或强度）时，让既有的 onPrimary 一族在这几个复合底色上重新可读。
     *
     * <p>为什么不逐个底色跑 {@link #readableOn}：那个方法只对**一个**底色保证 4.5:1，
     * 逐个套用会在不同底色间来回改判（遮罩压在 surface 与 surfaceContainerHigh 上亮度不同），
     * 最终可能连第一个底色都不再达标。这里一次性比较黑/白在**全部**底色上的最差比值，
     * 既有的 default 值只要本来全部达标就原样保留——空 profile 之外的常见路径也不会漂移。
     */
    private static int readableOverlayText(int preferred, int... backdrops) {
        if (clearsContrast(preferred, MIN_ACCENT_CONTRAST, backdrops)) return preferred;
        return worstRatio(0xFF000000, backdrops) >= worstRatio(0xFFFFFFFF, backdrops) ? 0xFF000000 : 0xFFFFFFFF;
    }

    /** 前景色在全部底色上最差的那个对比度。 */
    private static double worstRatio(int foreground, int... backdrops) {
        double worst = Double.MAX_VALUE;
        for (int backdrop : backdrops) worst = Math.min(worst, ThemeContrast.ratio(foreground, backdrop));
        return worst;
    }
    private static int enforceBase(int foreground, int surface, int container, int containerHigh) {
        int result = ensureContrast(foreground, surface, 4.5);
        if (container >= 0) result = ensureContrast(result, container, 4.5);
        if (containerHigh >= 0) result = ensureContrast(result, containerHigh, 4.5);
        return result;
    }

    private static int color(String value, int fallback) {
        return value == null ? fallback : ThemeProfileValidator.parseColor(value, fallback);
    }

    private static int readableOn(int background, int preferred) {
        double black = ThemeContrast.ratio(0xFF000000, background);
        double white = ThemeContrast.ratio(0xFFFFFFFF, background);
        boolean useBlack = black >= white;
        if ((preferred == 0xFF000000) == useBlack && ThemeContrast.ratio(preferred, background) >= 4.5) return preferred;
        return useBlack ? 0xFF000000 : 0xFFFFFFFF;
    }

    /**
     * Keeps {@code color} when it clears {@link #MIN_ACCENT_CONTRAST} on every backdrop,
     * otherwise returns the nearest tone on the same hue/chroma that does.
     *
     * <p>Walking tone rather than snapping to black/white preserves the user's brand
     * hue - the same thing Material's tonal palette does when it assigns primary.
     */
    private static int readableAccent(int color, int... backdrops) {
        return readableAccent(color, MIN_ACCENT_CONTRAST, backdrops);
    }

    /**
     * Same walk, with an explicit threshold. The player ring uses
     * {@link #MIN_PLAYER_RING_CONTRAST} (3:1, the WCAG non-text state bar) because
     * 4.5:1 would drag the light-table blue far too dark for a ring that sits on
     * fixed dark glass.
     */
    private static int readableAccent(int color, double minimum, int... backdrops) {
        if (clearsContrast(color, minimum, backdrops)) return color;
        Hct source = Hct.fromInt(color);
        double hue = source.getHue();
        double chroma = source.getChroma();
        double original = source.getTone();
        int best = color;
        double bestDistance = Double.MAX_VALUE;
        for (int tone = 0; tone <= 100; tone++) {
            int candidate = Hct.from(hue, chroma, tone).toInt();
            if (!clearsContrast(candidate, minimum, backdrops)) continue;
            double distance = Math.abs(tone - original);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (bestDistance != Double.MAX_VALUE) return best;
        // Unreachable for real surfaces (tone 0 / 100 always bracket), but never return
        // an unreadable colour if a future call site passes something degenerate.
        int fallback = ensureContrast(color, backdrops[0], minimum);
        for (int backdrop : backdrops) fallback = ensureContrast(fallback, backdrop, minimum);
        return fallback;
    }

    private static boolean clearsContrast(int color, int... backdrops) {
        return clearsContrast(color, MIN_ACCENT_CONTRAST, backdrops);
    }

    private static boolean clearsContrast(int color, double minimum, int... backdrops) {
        for (int backdrop : backdrops) {
            if (ThemeContrast.ratio(color, backdrop) + 0.0001 < minimum) return false;
        }
        return true;
    }

    private static int ensureContrast(int foreground, int background, double minimum) {
        if (ThemeContrast.ratio(foreground, background) + 0.0001 >= minimum) return foreground;
        double black = ThemeContrast.ratio(0xFF000000, background);
        double white = ThemeContrast.ratio(0xFFFFFFFF, background);
        return black >= white ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static int mix(int first, int second, double amount) {
        double inverse = 1.0 - amount;
        int red = (int) Math.round(((first >>> 16) & 0xFF) * inverse + ((second >>> 16) & 0xFF) * amount);
        int green = (int) Math.round(((first >>> 8) & 0xFF) * inverse + ((second >>> 8) & 0xFF) * amount);
        int blue = (int) Math.round((first & 0xFF) * inverse + (second & 0xFF) * amount);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int withAlpha(int color, float opacity) {
        int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255f)));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static String lastDiagnostic() {
        return LAST_DIAGNOSTIC.get();
    }

    static ThemeTokens requireOrFallback(ThemeTokens candidate, ThemeTokens fallback) {
        try {
            return candidate.requireContrast();
        } catch (IllegalArgumentException error) {
            LAST_DIAGNOSTIC.set("fallback:seed-contrast:" + error.getMessage());
            return fallback;
        }
    }

    private static ThemeTokens derive(int seedColor, boolean dark, ThemeTokens fallback,
                                      ThemePaletteStyle paletteStyle) {
        DynamicScheme scheme = paletteStyle.create(seedColor, dark);
        return new ThemeTokens(
                scheme.getPrimary(), scheme.getOnPrimary(), scheme.getPrimaryContainer(), scheme.getOnPrimaryContainer(),
                scheme.getSecondary(), scheme.getOnSecondary(), scheme.getSecondaryContainer(), scheme.getOnSecondaryContainer(),
                scheme.getTertiary(), scheme.getOnTertiary(), scheme.getError(), scheme.getOnError(),
                scheme.getErrorContainer(), scheme.getOnErrorContainer(),
                fallback.colorSuccess(), fallback.colorOnSuccess(), fallback.colorSuccessContainer(), fallback.colorOnSuccessContainer(),
                fallback.colorWarning(), fallback.colorOnWarning(), fallback.colorWarningContainer(), fallback.colorOnWarningContainer(),
                scheme.getSurface(), scheme.getSurfaceDim(), scheme.getSurfaceBright(), scheme.getSurfaceContainerLowest(),
                scheme.getSurfaceContainerLow(), scheme.getSurfaceContainer(), scheme.getSurfaceContainerHigh(), scheme.getSurfaceContainerHighest(),
                scheme.getOnSurface(), scheme.getOnSurfaceVariant(), scheme.getOutline(), scheme.getOutlineVariant(),
                scheme.getInverseSurface(), scheme.getInverseOnSurface(), scheme.getInversePrimary(), scheme.getScrim(),
                scheme.getShadow(), scheme.getPrimary(), fallback.focusScale(),
                fallback.colorPlayerFocusRing(), fallback.colorPlayerCurrentRing(), fallback.dialogOpacity(), fallback.colorPlayerControl(),
                fallback.colorPlayerControlMuted(), fallback.colorPlayerControlActive(), fallback.colorPlayerScrim(),
                fallback.colorHealthGood(), fallback.colorHealthWarn(), fallback.colorHealthBad(), fallback.colorOverlayLight(), fallback.colorOverlayDark(),
                fallback.colorItemOverlay(), fallback.colorOnItemOverlay()
        );
    }

    private static boolean isOpaque(int color) {
        return ((color >>> 24) & 0xFF) == 0xFF;
    }
}
