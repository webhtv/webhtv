package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 电视端「错误态」的规格契约。
 *
 * <p>依据是本仓库自己的设计文档 §9.9：「错误态：<b>错误色 + 原因 + 重试动作</b>」。
 *
 * <p>改动前 TV 上没有任何错误态：配置加载失败只弹一个会消失的吐司（`Notify.show`），
 * 然后 `showContent()` 留一个没有任何内容源的空首页——用户既不知道原因，也没有重试入口；
 * 设置页则把 `error_config_get` 写进普通的取值 TextView，与正常值**颜色完全相同**。
 *
 * <p>本测试同时把该角色在真机上实测的对比度钉住（见 {@link #MEASURED_*}）。数字来自
 * 192.168.50.3:5561（1920×1080 / 280dpi）的壁纸与行色带取样，取的是**最不利**样本，
 * 而不是平均值——平均值会掩盖最亮处的问题。
 */
public class TvErrorStateContractTest {

    private static final String TOKENS = "app/src/main/res/values/webhtv_tokens.xml";
    private static final String TOKENS_NIGHT = "app/src/main/res/values-night/webhtv_tokens.xml";
    private static final String ATTRS = "app/src/main/res/values/webhtv_attrs.xml";
    private static final String STYLES = "app/src/main/res/values/webhtv_styles.xml";
    private static final String PROGRESS_LAYOUT =
            "app/src/main/java/com/fongmi/android/tv/ui/custom/ProgressLayout.java";
    private static final String HOME = "app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java";
    private static final String ENHANCE =
            "app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingEnhanceActivity.java";
    private static final String AD =
            "app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingAdActivity.java";
    private static final String KEEP =
            "app/src/leanback/java/com/fongmi/android/tv/ui/activity/KeepActivity.java";

    /** 实机实测的最不利底材亮度（sRGB 相对亮度）。 */
    private static final double MEASURED_WALLPAPER_WORST = 0.2019;
    private static final double MEASURED_BAND_WORST = 0.1506;

    private static final String ERROR_ROLE = "webhtv_color_on_wallpaper_error";
    private static final int ERROR_VALUE = 0xFFDAD6;
    /** 错误取值的实心深色底（TV-6 底材改造）。 */
    private static final String ERROR_BG_ROLE = "webhtv_color_error_value_bg";
    private static final int ERROR_BG_VALUE = 0x4A3438;

    // ------------------------------------------------------------------ 角色本身

    @Test
    public void errorRoleIsPaletteIndependentAndSharedByBothTables() throws Exception {
        assertEquals("错误前景色在两个调色板里必须同值（壁纸在两种模式下都是深色）",
                tokenValue(TOKENS), tokenValue(TOKENS_NIGHT));
        assertEquals("取值必须与注释记录的 #FFDAD6 一致", ERROR_VALUE, tokenValue(TOKENS));
    }

    @Test
    public void errorRoleIsWiredAsItsOwnAttributeNotBorrowingColorError() throws Exception {
        assertTrue("必须声明 webhtvColorOnWallpaperError",
                read(ATTRS).contains("webhtvColorOnWallpaperError"));
        assertTrue("必须在 Theme.WebHTV 里接线",
                read(STYLES).contains("webhtvColorOnWallpaperError\">@color/" + ERROR_ROLE));
        // 关键点：不能用跟随调色板的 colorError——浅色表的深红压在深色壁纸上不可读。
        for (String source : new String[]{read(ATTRS), read(STYLES)}) {
            assertFalse("错误前景色不得复用 colorError（它是跟随调色板的角色）",
                    source.contains("webhtvColorOnWallpaperError\">?attr/colorError"));
        }
    }

    /**
     * 稳定满足的是**大字号**门槛：错误态标题按 §6.2 属 Display 档，门槛 3:1。
     */
    @Test
    public void errorRoleMeetsTheLargeTextFloorOnEveryMeasuredSubstrate() {
        double luminance = luminance(ERROR_VALUE);
        double onBand = contrast(luminance, MEASURED_BAND_WORST);
        double onWallpaper = contrast(luminance, MEASURED_WALLPAPER_WORST);
        assertTrue("对行色带最亮处应满足大字号 3:1，实测 " + round(onBand), onBand >= 3.0);
        assertTrue("对壁纸最亮处应满足大字号 3:1，实测 " + round(onWallpaper), onWallpaper >= 3.0);
    }

    /**
     * 如实钉住**正文**对比度，包括那个达不到 AA 的缺口。
     *
     * <p>壁纸/色带是中亮度（L≈0.13–0.15），任何带明显红相的前景色要达到正文门槛 4.5:1
     * 就必须接近纯白（L≥0.86），那时"错误色"在观感上已与普通白字无异、失去语义。
     * 所以当前取值停在 4.05–4.50:1。这里断言的是"不许再低"，而不是"已经达标"——
     * 后者是假的，写进去只会掩盖问题。
     */
    @Test
    public void errorRoleKeepsItsDocumentedBodyContrastFloor() {
        double luminance = luminance(ERROR_VALUE);
        double onBand = contrast(luminance, MEASURED_BAND_WORST);
        assertTrue("正文对比度不得低于已记录的 4.0:1（当前实测 " + round(onBand)
                + "，正文门槛 4.5:1，缺口与替代方案见 tokens 注释与任务文档 §7）", onBand >= 4.0);
    }

    // ------------------------------------------------------------------ 错误态本身

    @Test
    public void progressLayoutExposesAnErrorStateWithReasonAndRetry() throws Exception {
        String source = read(PROGRESS_LAYOUT);
        assertTrue("状态机必须有 ERROR", source.contains("ERROR"));
        assertTrue("必须提供 showError", source.contains("public void showError("));
        assertTrue("错误标题必须用错误角色", source.contains("R.attr.webhtvColorOnWallpaperError"));
        assertTrue("错误态必须隐藏空态插画（空箱子表达的是'没有内容'，用在错误上会误导）",
                source.contains("R.id.illustration"));
        assertTrue("重试再次失败要能刷新原因文案",
                source.contains("if (mState == State.ERROR) applyErrorContent();"));
    }

    @Test
    public void emptyStateLayoutsExposeTheIllustrationSlotForTheErrorState() throws Exception {
        for (String layout : new String[]{
                "app/src/leanback/res/layout/view_empty.xml",
                "app/src/mobile/res/layout/view_empty.xml"}) {
            assertTrue(layout + " 必须给插画加 @+id/illustration（错误态要隐藏它）",
                    read(layout).contains("@+id/illustration"));
        }
    }

    /**
     * 首页配置加载失败必须进入错误态，而不是只弹吐司后留一个空页面。
     */
    @Test
    public void homeConfigFailureEntersTheErrorStateWithAWorkingRetry() throws Exception {
        String source = read(HOME);
        assertTrue("失败回调必须进入错误态", source.contains("showConfigError(msg)"));
        assertTrue("错误态标题必须是 error_config_load",
                source.contains("R.string.error_config_load"));
        assertTrue("必须有重试按钮文案", source.contains("R.string.error_retry"));
        assertTrue("重试必须真的重新加载配置", source.contains("initConfig();"));
        // 吐司是既有行为，不删。
        assertTrue("应保留原有的吐司提示", source.contains("Notify.show(msg);"));
        // 失败后不能再走 showContent()，否则又回到"空页面"。
        int handler = source.indexOf("public void error(String msg)");
        assertTrue("必须存在配置失败回调", handler > 0);
        // 注释里会解释"改动前是 showContent()"，所以先剥掉注释再查，
        // 否则负向断言会命中说明文字（本仓库已踩过两次同类坑）。
        String body = withoutJavaComments(source.substring(handler, source.indexOf("};", handler)));
        assertFalse("失败回调不得再调用 showContent()", body.contains("showContent()"));
    }

    /**
     * 「还没配接口」必须与「加载失败」分开：前者不是失败。
     *
     * <p>重置应用、重装或恢复出厂后的首次启动就是这个状态。改动前 {@code BaseConfig} 把它
     * 当成 {@code error("")} 抛出来，首页显示「内容源加载失败 / 重试」——一个不存在的故障
     * 加一个点了永远没用的按钮（用户实机截图确认：只有标题与重试，没有原因行）。
     */
    @Test
    public void missingConfigIsNotTreatedAsAFailure() throws Exception {
        String base = read("app/src/main/java/com/fongmi/android/tv/api/config/BaseConfig.java");
        assertFalse("BaseConfig 不得再把'没配地址'当成 error(\"\")", base.contains("callback.error(\"\")"));
        assertTrue("没有配置地址必须走 callback.noConfig()", base.contains("callback::noConfig"));
        assertTrue("Callback 必须提供 noConfig 钩子",
                read("app/src/main/java/com/fongmi/android/tv/impl/Callback.java").contains("public void noConfig()"));

        String home = read(HOME);
        assertTrue("HomeActivity 必须实现 noConfig 回调", home.contains("public void noConfig()"));
        assertTrue("未配置必须走 noConfig 分支", home.contains("showNoConfig()"));
        String showNoConfig = methodBody(home, "private void showNoConfig()");
        // 未配置 = 正常首页：点播/直播/设置这一行是首页列表的第一行，任何铺满首页的状态浮层
        // 都会把它整块隐藏（ProgressLayout 把内容视图置为 INVISIBLE），新装用户就只剩一个按钮。
        assertTrue("未配置必须回到内容态（导航可用）", showNoConfig.contains("showContent()"));
        assertFalse("未配置不得再用全屏空态接管首页", showNoConfig.contains("showEmpty()"));
        assertFalse("未配置不得调用 showError", showNoConfig.contains("showError("));
        // 导航行只由既有按钮构成：未配置时不再额外插入任何入口。
        String setFunc = methodBody(home, "private void setFunc()");
        assertFalse("未配置不得在导航行里插入额外按钮", setFunc.contains("home_no_config"));
        assertFalse("未配置态的主动作不得是重试", showNoConfig.contains("error_retry"));
    }

    /**
     * 配置在**别的路径**上变得可用后，首页不能继续顶着状态卡。
     *
     * <p>用户反馈："卸载后重新安装再通过备份恢复也会出现类似情况，必现；杀进程后重进才行"。
     * 恢复备份走的是 {@code RefreshEvent.home()} 而不是重新走首页启动（{@code showContent()}），
     * 而状态浮层只被启动成功与点重试收掉——于是配置明明已经可用，首页仍停在状态卡上。
     */
    @Test
    public void aConfiguredSourceDismissesAStaleStateOverlay() throws Exception {
        String home = read(HOME);
        String handler = methodBody(home, "public void onRefreshEvent(RefreshEvent event)");
        assertTrue("HOME 刷新时必须检查并收起过期的状态卡", handler.contains("dismissStateIfConfigured()"));
        String dismiss = methodBody(home, "private void dismissStateIfConfigured()");
        assertTrue("只有配置真的可用（有站点）时才收起", dismiss.contains("getSites().isEmpty()"));
        assertTrue("收起动作必须回到内容态", dismiss.contains("showContent()"));
        // 只能收「空态 / 错误态」：加载中（PROGRESS）与内容态都不得被这条顺手收掉，
        // 否则 HOME 刷新会把还在转圈的首页直接变成空白。
        assertTrue("必须能识别空态", dismiss.contains("isEmpty()"));
        assertTrue("必须能识别错误态", dismiss.contains("isError()"));
    }

    /**
     * 未配置时宿主不得停在加载中：{@code Callback.noConfig()} 的默认实现必须保留改动前的收尾行为。
     *
     * <p>{@code noConfig()} 是本次新增的**终态**分支，而既有宿主是拿 {@code start()} 与
     * {@code error(String)} 当「加载开始 / 加载结束」的一对在用。默认留空实现，所有没来得及
     * 覆写它的宿主在无配置时就会永远停在加载中。实测可达路径：TV 标题长按走
     * {@code reloadConfig()}，它先 {@code progressLayout.showProgress()}，加载失败后只有
     * {@code error("")} 能收掉转圈（空消息在 {@code Notify.show} 里直接返回，不会弹空吐司）。
     */
    @Test
    public void defaultNoConfigStillTerminatesTheLoadingState() throws Exception {
        String callback = read("app/src/main/java/com/fongmi/android/tv/impl/Callback.java");
        String body = methodBody(callback, "public void noConfig()");
        assertTrue("默认实现必须结束加载态（转调 error(\"\")），否则既有宿主会永久停在加载中",
                body.contains("error(\"\")"));
        // 可达性证据：reloadConfig() 先进加载态，且它的匿名回调只覆写了 error 作为收尾。
        String reload = methodBody(read(HOME), "public void reloadConfig()");
        assertTrue("reloadConfig 必须先进加载态", reload.contains("progressLayout.showProgress()"));
        assertTrue("该回调以 error(String) 作为收尾", reload.contains("public void error(String msg)"));
    }

    /**
     * 设置页的内联错误必须与正常取值**可区分**，且失败过的行在后续刷新成功时能复位。
     */
    /**
     * 底材改造（TV-6）：错误取值换上一块**实心深底**后，正文必须稳过 4.5:1。
     *
     * <p>这是 TV-5 记录的那个缺口的收口方式——缺口在壁纸/色带这种中亮度底材上无法靠调色
     * 解决（要达标就得接近纯白，错误色随之失去语义），只能改底材。
     */
    @Test
    public void errorValueSubstrateGivesTheBodyTextARealMargin() throws Exception {
        for (String table : new String[]{TOKENS, TOKENS_NIGHT}) {
            Matcher matcher = Pattern.compile("<color name=\"" + ERROR_BG_ROLE + "\">#([0-9A-Fa-f]{6})</color>")
                    .matcher(read(table));
            assertTrue(table + " 必须声明 " + ERROR_BG_ROLE, matcher.find());
            assertEquals("错误底材必须与明暗无关（它是实心色，不跟随调色板）",
                    ERROR_BG_VALUE, Integer.parseInt(matcher.group(1), 16));
        }
        double onChip = contrast(luminance(ERROR_VALUE), luminance(ERROR_BG_VALUE));
        assertTrue("错误正文在实心底上必须稳过正文门槛 4.5:1，实测 " + round(onChip), onChip >= 4.5);
    }

    @Test
    public void settingsInlineErrorsUseTheErrorRoleAndRecoverOnSuccess() throws Exception {
        for (String path : new String[]{ENHANCE, AD}) {
            String source = read(path);
            assertTrue(path + " 的 setError 必须使用错误角色",
                    source.contains("R.attr.webhtvColorOnWallpaperError"));
            assertTrue(path + " 的 setError 必须换上错误底材",
                    source.contains("setBackgroundResource(R.drawable.shape_error_value)"));
            assertTrue(path + " 必须把成功路径复位到常规壁纸前景色",
                    source.contains("R.attr.webhtvColorOnWallpaper)"));
            // 复位必须发生在写入摘要的同一条路径上，否则某行失败一次后会一直是红的。
            assertTrue(path + " 的成功路径必须走 setSummary", source.contains("setSummary(view, supplier.get())"));
            int reset = source.indexOf("private void setSummary(");
            assertTrue(path + " 必须有 setSummary 实现", reset > 0);
            String impl = source.substring(reset, source.indexOf("}", reset));
            assertTrue(path + " 的 setSummary 必须复位颜色",
                    impl.contains("setTextColor(themeColor(R.attr.webhtvColorOnWallpaper))"));
            assertTrue(path + " 的 setSummary 必须撤掉错误底材（错误态是临时的）",
                    impl.contains("setBackground(null)"));
        }
    }

    /**
     * 收藏页点击条目时的配置加载失败必须进入错误态并可重试。
     *
     * <p>TV-5 的文档 §7.3 原本把"其余宿主"泛泛列成历史/收藏/文件三处；本批实际侦察后发现
     * **历史与文件的加载没有失败分支**（本地读取不会抛错），搜索/采集也只有提示性吐司——
     * 唯一真实的页面级远端失败是收藏页的 `loadConfig`。所以这里只钉这一处，
     * 不为了凑数量去臆造错误态。
     */
    @Test
    public void favoritesConfigLoadFailureEntersTheErrorStateWithARetry() throws Exception {
        String source = read(KEEP);
        int handler = source.indexOf("public void error(String msg)");
        assertTrue("收藏页必须有配置加载失败回调", handler > 0);
        String body = withoutJavaComments(source.substring(handler, source.indexOf("});", handler)));
        assertTrue("失败必须进入错误态", body.contains("showError("));
        assertTrue("错误态标题必须是 error_config_load", body.contains("R.string.error_config_load"));
        assertTrue("必须有重试按钮文案", body.contains("R.string.error_retry"));
        assertTrue("重试必须复用同一条加载路径", body.contains("loadConfig(config, item);"));
        assertTrue("应保留原有的吐司提示", body.contains("Notify.show(msg);"));
    }

    /**
     * `setSummary` 会用 `setBackground(null)` 撤掉错误底材——前提是这些取值 TextView
     * **原本就没有背景**。一旦有人给其中任何一个加上背景，复位就会把它一并抹掉。
     * 这里把前提钉住，让那种改动在这里先失败，而不是变成"设置页某个取值突然没底了"。
     */
    @Test
    public void summaryTextViewsHaveNoBackgroundSoClearingTheErrorSubstrateIsSafe() throws Exception {
        for (String layout : new String[]{
                "app/src/leanback/res/layout/activity_setting_enhance.xml",
                "app/src/leanback/res/layout/activity_setting_ad.xml"}) {
            String source = read(layout);
            Matcher ids = Pattern.compile("@\\+id/(\\w*[Tt]ext\\w*)\"").matcher(source);
            int checked = 0;
            while (ids.find()) {
                String id = ids.group(1);
                String block = tagBlockAfter(source, ids.end());
                assertFalse(layout + " 的 " + id
                                + " 在布局里带了背景：setSummary 的 setBackground(null) 会把它抹掉",
                        block.contains("android:background="));
                checked++;
            }
            assertTrue(layout + " 应至少含一个取值 TextView", checked > 0);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** 取某个 id 匹配位置之后、当前标签结束（{@code />} 或 {@code >}）之前的属性文本。 */
    private static String tagBlockAfter(String source, int from) {
        int end = source.length();
        int selfClosing = source.indexOf("/>", from);
        if (selfClosing >= 0) end = Math.min(end, selfClosing);
        int closing = source.indexOf(">", from);
        if (closing >= 0) end = Math.min(end, closing);
        return source.substring(from, end);
    }

    private static int tokenValue(String path) throws Exception {
        Matcher matcher = Pattern.compile("<color name=\"" + ERROR_ROLE + "\">#([0-9A-Fa-f]{6})</color>")
                .matcher(read(path));
        assertTrue(path + " 必须声明 " + ERROR_ROLE, matcher.find());
        return Integer.parseInt(matcher.group(1), 16);
    }

    /** sRGB 相对亮度，与 WCAG 2.2 定义一致。 */
    private static double luminance(int rgb) {
        double r = channel((rgb >> 16) & 0xFF);
        double g = channel((rgb >> 8) & 0xFF);
        double b = channel(rgb & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double v = value / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double contrast(double first, double second) {
        double hi = Math.max(first, second);
        double lo = Math.min(first, second);
        return (hi + 0.05) / (lo + 0.05);
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f:1", value);
    }

    /** 剥掉 Java 的行注释与块注释，避免负向断言命中说明文字。 */
    private static String withoutJavaComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    /** 取一个方法的完整方法体（含签名），用花括号配平切分。 */
    private static String methodBody(String source, String signature) {
        String cleaned = withoutJavaComments(source);
        int start = cleaned.indexOf(signature);
        assertTrue("必须存在方法 " + signature, start > 0);
        int open = cleaned.indexOf('{', start);
        assertTrue("方法签名后必须有方法体", open > 0);
        int depth = 0;
        for (int i = open; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return cleaned.substring(start, i + 1);
        }
        return cleaned.substring(start);
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
