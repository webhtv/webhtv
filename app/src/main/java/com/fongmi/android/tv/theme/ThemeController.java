package com.fongmi.android.tv.theme;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.app.Dialog;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.Util;

/** Read-only application entry point for future theme adoption. */
public final class ThemeController {

    private static volatile ThemeTokens current = ThemeTokens.light();
    private static volatile ThemeTokens baseline = ThemeTokens.light();
    private static volatile ThemeProfile profile = ThemeProfile.defaultProfile();

    private ThemeController() {
    }

    public static void apply(AppCompatActivity activity, ThemeTokens tokens) {
        ThemeTokens safe = tokens == null ? ThemeTokens.light() : tokens;
        current = safe;
        int color = safe.colorSurface();
        activity.getWindow().setStatusBarColor(color);
        activity.getWindow().setNavigationBarColor(color);
    }

    /**
     * Keeps AppCompat's DayNight configuration aligned with the persisted
     * appearance mode so the whole activity tree (mobile and leanback) shares
     * one resolution order: explicit mode > product default > system.
     *
     * <p>TV 的产品默认是**深色**（与上游一致，上游 TV 主题直接继承
     * {@code Theme.Material3.Dark}）。理由与上游相同：电视多在暗环境观看，
     * 浅色大面积亮底在夜间刺眼；而 TV 常见的深色底上，上游那套白色焦点环
     * （18.5:1）才能成立（白环对浅色表 surface 只有 1.05:1，等于看不见）。
     *
     * <p>“跟随系统”对 TV 不再等于“跟随一个通常恒为浅色的系统默认”，否则电视上
     * 会默认落到浅色表。用户仍可在外观设置里显式选择浅色/深色。
     * mobile 保持既有的“跟随系统”语义不变。
     */
    public static void applyNightModeToApp() {
        int mode = com.fongmi.android.tv.setting.Setting.getThemeMode();
        int delegate = switch (mode) {
            case 0 -> AppCompatDelegate.MODE_NIGHT_NO;
            case 1 -> AppCompatDelegate.MODE_NIGHT_YES;
            default -> Util.isLeanback()
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        };
        if (AppCompatDelegate.getDefaultNightMode() != delegate) {
            AppCompatDelegate.setDefaultNightMode(delegate);
        }
    }

    public static ThemeTokens current() {
        return current;
    }

    /** The frozen Layer 1 palette the binder compares against. */
    public static ThemeTokens baseline() {
        return baseline;
    }

    /**
     * Single source of truth for "the current app appearance is dark".
     *
     * <p>The Activity configuration already reflects a forced {@code theme_mode}, so
     * ordinary UI must ask this method instead of reading the raw system night bit,
     * which would contradict an explicit light/dark choice.
     */
    public static boolean isNight(Context context) {
        Configuration configuration = context == null ? Resources.getSystem().getConfiguration()
                : context.getResources().getConfiguration();
        return (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * The user's explicit scrim opacity, or {@code null} when they never set one.
     *
     * <p>Callers must keep this distinguished from "the set opacity happens to equal
     * the shipped default". Modal scrims in this app do not use the token colour:
     * the episode-detail and TMDB-person dialogs paint their own scrim (a translucent
     * white in light mode, translucent black in dark) and that is the shipped design.
     * Replacing those colours with {@code colorScrim()} was measured to flip light-mode
     * scrims from lightening to darkening, so the wiring instead lets the user drive
     * only the alpha of each dialog's own scrim colour, and stays a strict no-op while
     * the slot is unset so the default rendering is byte-identical.
     *
     * @param light which slot set the caller resolved for its own colours; the scrim
     *              owner decides light/dark, so the same answer is reused here instead
     *              of re-deriving it from the uiMode bit
     * @return the configured alpha in {@code 0..1}, or {@code null} when unset
     */
    public static Float configuredScrimOpacity(boolean light) {
        ThemeProfile active = profile;
        if (active == null) return null;
        ThemeProfile.SlotSet slots = light ? active.light : active.dark;
        if (slots == null) return null;
        Float value = slots.scrimOpacity;
        if (value == null || !Float.isFinite(value)) return null;
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * The user's explicit playback-menu scrim strength for one mode, or {@code null}.
     *
     * <p>播放菜单遮盖层是"每个宿主自己决定明暗"的黑幕：它没有 ThemeTokens 分量，
     * 由播放页在创建时通过本方法读取，未设置时由 {@link PlaybackMenuScrim} 回落到
     * 它自己的 DEFAULT_OPACITY，因此老 profile 的默认观感是确定的。
     */
    public static Float configuredPlayerScrimOpacity(boolean light) {
        ThemeProfile active = profile;
        if (active == null) return null;
        ThemeProfile.SlotSet slots = light ? active.light : active.dark;
        if (slots == null) return null;
        Float value = slots.playerScrimOpacity;
        if (value == null || !Float.isFinite(value)) return null;
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * The user's explicit live-list (channel drawer) scrim strength for one mode, or {@code null}.
     *
     * <p>直播页频道抽屉的遮盖层与播放菜单遮盖层同构：没有 ThemeTokens 分量，由宿主在创建时
     * 通过本方法读取；未设置时由 {@link LiveListScrim} 回落到它自己的 DEFAULT_OPACITY，
     * 因此老 profile 的默认观感与 shape_live_list.xml 逐值一致。
     */
    public static Float configuredLiveListScrimOpacity(boolean light) {
        ThemeProfile active = profile;
        if (active == null) return null;
        ThemeProfile.SlotSet slots = light ? active.light : active.dark;
        if (slots == null) return null;
        Float value = slots.liveListScrimOpacity;
        if (value == null || !Float.isFinite(value)) return null;
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * Applies a scrim opacity to a surface's own scrim colour, replacing only the alpha.
     *
     * <p>{@code null} (the user never set the slot) returns {@code base} untouched, which
     * is what keeps the shipped look byte-identical. Package-visible and taking the
     * opacity explicitly so both branches can be asserted without an Android device.
     */
    public static int applyScrimOpacity(int base, Float opacity) {
        if (opacity == null) return base;
        int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255f)));
        return (base & 0x00FFFFFF) | (alpha << 24);
    }

    public static void refresh() {
        boolean systemDark = (Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        current = ThemeResolver.resolve(currentThemeMode(), ThemeSeed.NONE, 0, 0, systemDark);
    }

    /**
     * Resolves the active semantic snapshot from the persisted appearance
     * preferences. {@code themeColor} follows the existing WebHTV contract:
     * {@code -1} disables tinting, {@code 0} uses the wallpaper seed and any
     * other value is an explicit ARGB seed.
     */
    public static ThemeTokens resolveFromPreferences() {
        ThemeProfile stored = null;
        try {
            stored = ThemeProfileStore.load();
        } catch (RuntimeException ignored) {
            // Preference access is unavailable; Layer 1 defaults remain valid.
        }
        return resolveWith(stored, null);
    }

    /**
     * Resolves the active snapshot against the table the given context inflates with.
     *
     * <p>{@code context} must be the activity whose views are about to be inflated, or
     * {@code null} where no activity exists yet (the read-only probe device tests use).
     * It is what decides light/dark: see {@link #resolvedDark(Context)}.
     */
    private static ThemeTokens resolveWith(ThemeProfile profile, Context context) {
        // Both the base palette and the profile's light/dark slot selection follow the
        // table that context resolves, so the active tokens stay comparable with inflated
        // colours on every flavour (see resolvedDark(Context)).
        boolean systemDark = resolvedDark(context);
        ThemeMode themeMode = currentThemeMode();
        int themeColor = com.fongmi.android.tv.setting.Setting.getThemeColor();
        if (themeColor == -1) {
            return ThemeResolver.resolve(themeMode, ThemeSeed.NONE, 0, 0, profile, null, systemDark);
        }
        ThemeSeed seed = themeColor == 0 ? ThemeSeed.WALLPAPER : ThemeSeed.EXPLICIT;
        int explicit = themeColor == 0 ? 0 : themeColor;
        int wallpaper = com.fongmi.android.tv.setting.Setting.getWallColor();
        return ThemeResolver.resolve(themeMode, seed, explicit, wallpaper, profile, null, systemDark);
    }

    /**
     * The frozen compiled palette the binder compares view colours against.
     *
     * <p>Every {@code ?attr/color*} attribute was resolved from the static
     * {@code webhtv_color_*} resources when the view was inflated, so this baseline
     * must ignore {@code theme_color}/{@code wall_color}: those preferences only
     * reach the activity through {@link #resolveWith(ThemeProfile, Context)}. Deriving
     * the baseline from the active seed made it describe colours no inflated view ever
     * held, and {@link ThemeBinder} then had nothing left to match - the feature
     * silently degraded to the legacy site dialog.
     */
    private static ThemeTokens frozenPalette(Context context) {
        return ThemeResolver.resolve(ThemeMode.SYSTEM, ThemeSeed.NONE, 0, 0, null, null, resolvedDark(context));
    }

    private static ThemeMode currentThemeMode() {
        int mode = com.fongmi.android.tv.setting.Setting.getThemeMode();
        return mode < 0 ? ThemeMode.SYSTEM : (mode == 0 ? ThemeMode.LIGHT : ThemeMode.DARK);
    }

    /**
     * Whether the palette the running build actually ships is the dark table.
     *
     * <p>{@link #frozenPalette(Context)} must describe the colours inflation produced,
     * and those come from the compiled {@code webhtv_color_*} resources. Resource
     * selection is not purely a function of uiMode: a flavour may override
     * {@code values/} with a fixed table and ship no table of its own for the other
     * mode. When that happens a uiMode-derived baseline describes colours no inflated
     * view ever held. Every binder rewrite compares against that baseline exactly, so
     * the mismatch silently turned the whole TV theme channel into a no-op (measured:
     * probe vs default = 0 changed pixels on TV while the identical probe changed
     * ~49k pixels on mobile).
     *
     * <p>The compiled table is therefore read from resources whenever it can be
     * identified, and only an unidentifiable palette falls back to the uiMode rule.
     *
     * <p>WebHTV no longer ships such an override: the TV flavour used to carry a
     * permanent dark {@code values/webhtv_tokens.xml} and that file is gone, so both
     * flavours now resolve the same day/night tables and this method normally agrees
     * with the uiMode rule. The resource probe is kept because it is what keeps the
     * baseline honest if a flavour ever pins a palette again.
     *
     * <p><b>The probe must run against the context that inflates the UI, never against
     * the application context.</b> A forced appearance mode is applied by
     * {@code AppCompatDelegate.setDefaultNightMode}, which rewrites the resources of the
     * activities it delegates for; on API &lt; 31 it does not rewrite the application's
     * own resources. The TV flavour forces {@code MODE_NIGHT_YES} by product default
     * (see {@link #applyNightModeToApp()}), so on a day-mode TV box the application
     * context still resolved the light table while every inflated {@code ?attr/color*}
     * resolved the dark one. The snapshot therefore came out light under a dark UI, and
     * the dialog shell painted from it ({@code LightDialog.shell}) ended up under dark
     * foregrounds - measured 1.06-1.41:1 on the 新增点播 dialog. The activity's own
     * configuration ({@link #isNight(Context)}) is the only source that already reflects
     * the forced mode, so it is what {@code context} must be.
     */
    static boolean resolvedDark(Context context) {
        boolean systemDark = context == null
                ? (Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                : isNight(context);
        return darkPaletteFor(compiledDarkPalette(context), currentThemeMode(), systemDark);
    }

    /**
     * The dark/light decision shared by the baseline and the active palette.
     *
     * <p>When the compiled table is identifiable it is authoritative: it is literally
     * what inflation resolved, so it must win even over an explicit appearance mode.
     * A flavour that pins {@code values/} to one table needs that - a light-mode device
     * (or a user who picked light) would otherwise be described by the wrong baseline.
     * Only an unidentifiable palette falls back to the historical mode/uiMode rule.
     */
    static boolean darkPaletteFor(Boolean compiledDark, ThemeMode mode, boolean systemDark) {
        if (compiledDark != null) return compiledDark;
        return mode == ThemeMode.DARK || (mode != ThemeMode.LIGHT && systemDark);
    }

    /**
     * Identifies which canonical table the given context resolves by comparing every role
     * the binder may rewrite against its {@code webhtv_color_*} resource. Returns
     * {@code null} when no context is available or neither table matches exactly, so
     * an unmodelled future palette keeps the previous uiMode behaviour instead of
     * silently binding against the wrong baseline.
     *
     * <p>The context must be the one that inflated the views (see
     * {@link #resolvedDark(Context)}); it falls back to the application context only for
     * callers that have no activity, such as the read-only device-test probe.
     */
    static Boolean compiledDarkPalette(Context context) {
        Context probe = context == null ? App.get() : context;
        if (probe == null) return null;
        ThemeTokens light = ThemeTokens.light();
        ThemeTokens dark = ThemeTokens.dark();
        boolean matchesLight = true;
        boolean matchesDark = true;
        for (ThemeRole role : ThemeRole.values()) {
            int resource = colorResourceOf(role);
            if (resource == 0) continue;
            int compiled = ContextCompat.getColor(probe, resource);
            matchesLight &= compiled == role.colorOf(light);
            matchesDark &= compiled == role.colorOf(dark);
        }
        if (matchesLight == matchesDark) return null;
        return matchesDark;
    }

    /** The compiled default of a binder role, or 0 when the role has no resource. */
    private static int colorResourceOf(ThemeRole role) {
        return switch (role) {
            case PRIMARY -> R.color.webhtv_color_primary;
            case PRIMARY_CONTAINER -> R.color.webhtv_color_primary_container;
            case SECONDARY_CONTAINER -> R.color.webhtv_color_secondary_container;
            case FOCUS -> R.color.webhtv_color_focus;
            case SURFACE -> R.color.webhtv_color_surface;
            case SURFACE_CONTAINER -> R.color.webhtv_color_surface_container;
            case SURFACE_CONTAINER_HIGH -> R.color.webhtv_color_surface_container_high;
            case ON_SURFACE -> R.color.webhtv_color_on_surface;
            case ON_SURFACE_VARIANT -> R.color.webhtv_color_on_surface_variant;
            case OUTLINE -> R.color.webhtv_color_outline;
            case ERROR -> R.color.webhtv_color_error;
            case SUCCESS -> R.color.webhtv_color_success;
            case WARNING -> R.color.webhtv_color_warning;
            case ON_PRIMARY -> R.color.webhtv_color_on_primary;
            case ON_PRIMARY_CONTAINER -> R.color.webhtv_color_on_primary_container;
            case ON_SECONDARY_CONTAINER -> R.color.webhtv_color_on_secondary_container;
            case ON_ERROR -> R.color.webhtv_color_on_error;
            case ON_SUCCESS -> R.color.webhtv_color_on_success;
            case ON_WARNING -> R.color.webhtv_color_on_warning;
            // 条目遮罩族没有编译期颜色资源（遮罩取 @color/black_40，文字取 onPrimary），
            // 因此这里如实返回 0，与「角色没有资源」的既有语义一致。
            case ITEM_OVERLAY, ON_ITEM_OVERLAY -> 0;
        };
    }

    /**
     * Applies the persisted appearance snapshot to one activity.
     *
     * <p>Only the read-only token snapshot is refreshed here. System-bar colors
     * are updated solely when the window still owns an opaque bar; edge-to-edge
     * windows keep their transparent bars so this hook cannot regress the
     * existing immersive layouts.
     */
    public static void applyFromPreferences(AppCompatActivity activity) {
        ThemeProfile stored = null;
        try {
            stored = ThemeProfileStore.load();
        } catch (RuntimeException ignored) {
            // Preference access is unavailable; Layer 1 defaults remain valid.
        }
        profile = stored == null ? ThemeProfile.defaultProfile() : stored;
        // `activity` is the context whose resources inflate every view of this screen, so
        // it is also what decides which compiled table the snapshot must describe.
        baseline = frozenPalette(activity);
        current = resolveWith(profile, activity);
        if (activity == null || activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        if (activity.getWindow().getStatusBarColor() != android.graphics.Color.TRANSPARENT) {
            activity.getWindow().setStatusBarColor(current.colorSurface());
        }
        if (activity.getWindow().getNavigationBarColor() != android.graphics.Color.TRANSPARENT) {
            activity.getWindow().setNavigationBarColor(current.colorSurface());
        }
    }

    /**
     * Applies the active profile to an already-created view tree.
     *
     * <p>When no profile override is active this returns immediately, so the
     * default Layer 1 path is byte-identical and costs nothing.
     */
    public static void bindTheme(View root) {
        ThemeBinder.bind(root, baseline, current);
    }

    /** Same controlled channel for a dialog or bottom-sheet window. */
    public static void bindDialog(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        bindTheme(dialog.getWindow().getDecorView());
    }

    /**
     * Applies the active profile to a window-level background drawable.
     *
     * <p>Alert dialog panels live on the window rather than on a view, so they need
     * this second entry point next to {@link #bindTheme(View)}. It is a no-op while the
     * active tokens equal the frozen baseline.
     */
    public static void bindWindowBackground(Drawable background) {
        if (background == null) return;
        ThemeBinder.bindWindowBackground(background, baseline, current);
    }

    /** True when the active palette differs from the frozen compiled baseline. */
    public static boolean hasProfileOverrides() {
        return !baseline.equals(current);
    }

    // ---------------------------------------------------------------- TV focus ring

    /**
     * The one TV focus-ring colour source, for the code paths that must set a stroke
     * imperatively (dynamic rows, adapters, drawables built at runtime).
     *
     * <p>TV 的焦点环只允许有一个取值源：{@code tv_item_focus_ring}（由
     * {@code ?attr/tvFocusRing} 提供给 XML 选择器，由本方法提供给代码路径）。此前详情页把
     * {@code #FFD166} 写死在 Java 里，于是 {@code tv_item_focus_ring} 接线到主题 FOCUS 槽
     * 之后，同一个 TV 应用里就出现了两种焦点环色：播放页/追更页跟随主题，详情页永远黄色。
     *
     * <p><b>取值规则：活动快照的 FOCUS 槽一旦偏离冻结基线就跟随它，否则保持编译期静态值。</b>
     * {@code ?attr/tvFocusRing} 是编译期静态资源，Android 没有公开 API 能在运行时改写已编译的
     * {@code ?attr/color*}（见 {@link ThemeBinder} 类注释）。只有代码路径能动态取值，因此：
     * <ul>
     *   <li>FOCUS 槽与冻结基线同值时返回 {@code ?attr/tvFocusRing}，与 XML 选择器字节一致——
     *       默认档两条路径永远同色，不会出现「代码路径跟随、选择器不跟随」的两种环色；</li>
     *   <li>FOCUS 槽被改过（编辑器 FOCUS 槽覆写，或 seed/壁纸重新生成色板导致 FOCUS 变化）时
     *       返回 {@code current().colorFocus()}。这与 {@link ThemeBinder} 的判据一致：binder 也是
     *       在 {@code baseline != current} 时把白名单 selector 的环改写为 FOCUS 角色，因此代码路径
     *       与白名单 selector 始终同色。这也是用户对「焦点色」这一项设置唯一能看到的反馈：卡片
     *       描边（{@code TmdbCardFocusHelper}、{@code TmdbEpisodeAdapter}、详情页/追更页）由代码
     *       路径绘制，若这里仍返回静态值，编辑器的焦点色对这些视图永远无效。</li>
     * </ul>
     * 已知并发面：XML 选择器与 {@code ?attr/tvPlayerRing} 玻璃族仍是静态环色，覆写后同一个
     * 屏幕里可能出现「卡片环跟随、选择器环不跟随」，属于 {@link ThemeBinder} 白名单边界，
     * 需要另外扩展白名单才能统一。
     *
     * <p>同时这也修正了浅色详情页的可读性缺陷：{@code #FFD166} 画在浅色底板
     * （cinema {@code #EBE3DA}、chip {@code #F5F8FB}）上只有 1.14–1.35:1，环几乎看不见，
     * 而主题 FOCUS 槽的浅色取值是 5.03–5.99:1（Resolver 另有 ≥3:1 的对比度门）。
     */
    public static int focusRingColor(Context context) {
        int compiled = compiledFocusRingColor(context);
        // Comparing the resolved FOCUS role against the frozen baseline avoids a second
        // light/dark resolution here (this runs on every card focus change) and states the
        // rule exactly: follow the profile only when it actually moved the focus colour.
        int active = current.colorFocus();
        return active == baseline.colorFocus() ? compiled : active;
    }

    /** The compiled {@code ?attr/tvFocusRing} value, shared with the XML selector path. */
    private static int compiledFocusRingColor(Context context) {
        if (context != null) {
            TypedValue value = new TypedValue();
            if (context.getTheme().resolveAttribute(R.attr.tvFocusRing, value, true)) {
                if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    return value.data;
                }
                if (value.resourceId != 0) return ContextCompat.getColor(context, value.resourceId);
            }
        }
        return ContextCompat.getColor(App.get(), R.color.tv_item_focus_ring);
    }

    /**
     * The focus-ring colour with an explicit alpha, matching the historical
     * {@code 0x55FFD166} / {@code 0x1AFFD166} fills now that the hue is theme-driven.
     */
    public static int focusRingColor(Context context, float alpha) {
        int color = focusRingColor(context);
        int scaled = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, scaled)) << 24);
    }

    public static ThemeProfile activeProfile() {
        return profile;
    }
}
