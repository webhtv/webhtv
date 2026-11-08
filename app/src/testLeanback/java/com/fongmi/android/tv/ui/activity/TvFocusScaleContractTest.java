package com.fongmi.android.tv.ui.activity;

import com.fongmi.android.tv.theme.ThemeTokens;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 电视端「聚焦放大」的单一来源契约。
 *
 * <p>改动前，同一个焦点缩放在 TV 端有四种写法：最近观看/收藏/历史用 {@code 1.1f} +
 * {@code 150ms}，TMDB 相关视频与详情页视频卡用 {@code 1.04f} + {@code 120ms}，
 * 网络诊断面板用 {@code 1.035f} + {@code 100ms}。没有任何注释说明为什么不同。
 *
 * <p>本测试与 {@code TvFocusRingContractTest} 的 R2 是同一思路的延伸：那里把**环宽**与
 * **环色**收敛成唯一来源，这里把**实现**与**时长**收敛成唯一来源。
 *
 * <p><b>为什么保留两个档位。</b>收敛时查到一条反证：{@code TmdbDetailActivityLayoutTest}
 * 记录 {@code TmdbEpisodeAdapter} 刻意完全不缩放，理由是「detail rows clip enlarged cards」。
 * 说明密集宿主上的小倍率是为了避免裁切，不是随意漂移；把 {@code 1.04} 一律提到 {@code 1.1}
 * 会引入裁切回归。所以倍率保留两个档位，但由本测试钉住"哪个站点属于哪一档"，
 * 将来不能悄悄把密集宿主挪到常规档。
 *
 * <p>明确不在收敛范围内的（它们不是焦点缩放）：{@code AudioMiniPlayer} 的
 * {@code 1.03f/90ms} 是拖拽反馈、{@code 0.94f→1f/180ms} 是出现动画；
 * {@code PlayerGesture} 的缩放是画面手势；{@code TmdbCardFocusHelper} /
 * {@code TmdbEpisodeAdapter} 的 {@code setScaleX(1f)} 是刻意不缩放。
 */
public class TvFocusScaleContractTest {

    private static final String HELPER = "app/src/main/java/com/fongmi/android/tv/ui/helper/TvFocusScale.java";
    private static final String TOKENS = "app/src/main/res/values/webhtv_tokens.xml";
    private static final String TOKENS_NIGHT = "app/src/main/res/values-night/webhtv_tokens.xml";

    /** 常规宿主（卡片/列表项，行内空间足够）：走主题 token 的倍率。 */
    private static final String[] REGULAR_SITES = {
            "app/src/leanback/java/com/fongmi/android/tv/ui/adapter/HistoryAdapter.java",
            "app/src/leanback/java/com/fongmi/android/tv/ui/adapter/KeepAdapter.java",
    };

    /** 密集宿主（放大后会被所在行裁切）：走紧凑倍率。 */
    private static final String[] COMPACT_SITES = {
            "app/src/leanback/java/com/fongmi/android/tv/ui/presenter/TmdbVideoPresenter.java",
            "app/src/main/java/com/fongmi/android/tv/ui/adapter/TmdbVideoAdapter.java",
            "app/src/main/java/com/fongmi/android/tv/ui/dialog/PanNetworkDiagnosticDialog.java",
    };

    /** 非焦点缩放站点：必须**不**引用本助手，否则会把拖拽/画面手势也改成焦点手感。 */
    private static final String[] NON_FOCUS_SCALE_SITES = {
            "app/src/main/java/com/fongmi/android/tv/ui/audio/AudioMiniPlayer.java",
            "app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerGesture.java",
    };

    private static final Pattern DIRECT_SCALE = Pattern.compile("\\.scale[XY]\\(");
    private static final Pattern FOCUS_SCALE_LITERAL = Pattern.compile("focused\\s*\\?\\s*1\\.\\d+f");

    // ------------------------------------------------------------------ 单一实现

    @Test
    public void theHelperOwnsTheImplementationAndTheOnlyScaleLiterals() throws Exception {
        String helper = read(HELPER);
        assertTrue("常规档必须取自主题 token（ThemeTokens.focusScale）",
                helper.contains("ThemeController.current().focusScale()"));
        assertTrue("时长必须取自 @integer/webhtv_focus_scale_duration",
                helper.contains("R.integer.webhtv_focus_scale_duration"));

        // 紧凑倍率是**唯一**允许写死缩放字面量的位置，且必须显式命名、写明理由。
        assertTrue("紧凑档必须是显式命名常量", helper.contains("private static final float COMPACT_SCALE = 1.04f;"));
        assertFalse("助手不得再出现别的聚焦倍率字面量",
                FOCUS_SCALE_LITERAL.matcher(helper).find());
        assertFalse("助手不得写死时长字面量",
                Pattern.compile("setDuration\\(\\s*\\d+\\s*\\)").matcher(helper).find());
    }

    @Test
    public void everyFocusScaleSiteDelegatesToTheHelper() throws Exception {
        for (String source : allSites()) {
            String body = read(source);
            assertTrue(source + " 必须委托给 TvFocusScale", body.contains("TvFocusScale."));
            assertFalse(source + " 不得再直接改缩放（应走 TvFocusScale）",
                    DIRECT_SCALE.matcher(body).find());
            assertFalse(source + " 不得再写死聚焦倍率字面量",
                    FOCUS_SCALE_LITERAL.matcher(body).find());
        }
    }

    /**
     * 档位归属必须稳定：常规宿主用 {@code apply}，密集宿主用 {@code applyCompact}。
     *
     * <p>这条把"为什么 TMDB 行是 1.04 而不是 1.1"从注释变成可执行的约束——
     * 将来有人把密集宿主改成常规档时，这里会先失败并指向裁切风险。
     */
    @Test
    public void eachSiteStaysInItsDocumentedEmphasisTier() throws Exception {
        for (String source : REGULAR_SITES) {
            String body = read(source);
            assertTrue(source + " 属于常规档，必须用 TvFocusScale.apply(", body.contains("TvFocusScale.apply("));
            assertFalse(source + " 不得改用紧凑档", body.contains("applyCompact("));
        }
        for (String source : COMPACT_SITES) {
            String body = read(source);
            assertTrue(source + " 属于密集档，必须用 TvFocusScale.applyCompact(",
                    body.contains("TvFocusScale.applyCompact("));
            assertFalse(source + " 不得改用常规档（行内会把放大的卡片裁掉）",
                    body.contains("TvFocusScale.apply("));
        }
    }

    /** 边界守卫：拖拽与画面手势的缩放**不是**焦点缩放，不能被顺手收敛进来。 */
    @Test
    public void dragAndVideoGesturesStayOutOfTheFocusScaleHelper() throws Exception {
        for (String source : NON_FOCUS_SCALE_SITES) {
            assertFalse(source + " 的缩放不是焦点缩放，不得引用 TvFocusScale",
                    read(source).contains("TvFocusScale"));
        }
    }

    // ------------------------------------------------------------------ 单一取值

    @Test
    public void focusScaleIsPaletteIndependentAndMatchesTheDocumentedValue() {
        ThemeTokens light = ThemeTokens.light();
        ThemeTokens dark = ThemeTokens.dark();
        assertEquals("焦点缩放是动效属性，不得随明暗模式变化",
                dark.focusScale(), light.focusScale(), 0.0001f);
        assertEquals("常规档取值必须是设计文档 §5.2 与 Android TV 按钮指南记录的 1.1",
                1.1f, light.focusScale(), 0.0001f);
    }

    @Test
    public void theDurationTokenExistsOnceAndTheDeadDimenIsGone() throws Exception {
        String light = read(TOKENS);
        Matcher value = Pattern
                .compile("<integer name=\"webhtv_focus_scale_duration\">(\\d+)</integer>").matcher(light);
        assertTrue("必须声明 @integer/webhtv_focus_scale_duration 且为可解析的正整数",
                value.find() && Integer.parseInt(value.group(1)) > 0);

        // 此前另有一个恒不被任何代码读取的 <dimen name="webhtv_focus_scale">，
        // 与 ThemeTokens.focusScale() 是同一语义的第二份声明。统一后必须消失，
        // 否则"单一来源"又会退化成两份需要手工同步的值。
        // 注释里会解释这件事，所以先剥掉注释再查，否则负向断言会命中说明文字。
        for (String table : new String[]{TOKENS, TOKENS_NIGHT}) {
            assertFalse(table + " 不得再有 <dimen name=\"webhtv_focus_scale\">（与 token 重复）",
                    withoutComments(read(table)).contains("<dimen name=\"webhtv_focus_scale\">"));
        }
    }

    /** 防止将来有人把站点逐个改回去：列出仍未委托的站点，失败信息可直接定位。 */
    @Test
    public void noFocusScaleSiteWasLeftBehind() throws Exception {
        List<String> missing = new ArrayList<>();
        for (String source : allSites()) {
            if (!read(source).contains("TvFocusScale.")) missing.add(source);
        }
        assertTrue("以下焦点缩放站点没有走唯一实现：" + missing, missing.isEmpty());
    }

    private static List<String> allSites() {
        List<String> sites = new ArrayList<>();
        sites.addAll(List.of(REGULAR_SITES));
        sites.addAll(List.of(COMPACT_SITES));
        return sites;
    }

    private static String withoutComments(String source) {
        return source.replaceAll("(?s)<!--.*?-->", " ");
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
