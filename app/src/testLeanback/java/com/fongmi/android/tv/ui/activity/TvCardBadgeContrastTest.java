package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TV 卡片角标（进度 / 片源 / 年份）的语义与可读性契约。
 *
 * <p>改动前，这三个角标是 leanback 独有的裸十六进制：{@code #177535}、{@code #F44336}、
 * {@code #2196F3}，且都带 {@code android:alpha="0.7"}。手机端同一组角标早就是语义色
 * （{@code ?attr/colorSecondaryContainer} / {@code ?attr/colorErrorContainer} /
 * {@code ?attr/colorSurfaceVariant}），只有 TV 端落后。
 *
 * <p>透明度带来的问题比"没走 token"更严重：角标上的文字恒为
 * {@code ?attr/webhtvColorOnWallpaper}（纯白），而 0.7 的底色叠在**浅色海报**上会被提亮，
 * 白字对比度跌到约 2.0–3.7:1。海报内容不受控，所以这个组合在真实内容上是不可读的。
 *
 * <p>{@code LeanbackForegroundContrastTest} 抓不到它：那里的 {@code luminance()} 会丢弃
 * alpha 通道，把半透明底色当成不透明来算，于是 {@code #177535} 被判定为"深色表面，通过"。
 * 本测试补上这一步——**把角标底色按 alpha 合成到最坏底（纯白海报）上再算对比度**，
 * 因此将来有人重新引入透明度，这里会先失败。
 *
 * <p>角标刻意用直接的 {@code @color} 引用而不是 {@code ?attr}：可读性依赖"底色足够暗"，
 * 做成可被主题改写的角色就等于放弃这个保证；同时 {@code ?attr} 填充会被
 * {@code LeanbackForegroundContrastTest} 判为"调色板跟随背景（日间为浅色）"而误报。
 */
public class TvCardBadgeContrastTest {

    private static final String TOKENS = "app/src/main/res/values/webhtv_tokens.xml";
    private static final String TOKENS_NIGHT = "app/src/main/res/values-night/webhtv_tokens.xml";
    private static final String ADAPTER = "app/src/leanback/res/layout/adapter_vod.xml";
    private static final String LEANBACK_COLOR = "app/src/leanback/res/color/";

    /** 角标上的文字恒为纯白（{@code ?attr/webhtvColorOnWallpaper} 两张表同值）。 */
    private static final String BADGE_FOREGROUND = "#FFFFFF";

    /** 正文门槛；角标文字是 14sp，不属于大字号豁免。 */
    private static final double MIN_CONTRAST = 4.5;

    /** 角标文件名（不含 .xml） → { 应引用的 token, 对应布局里的 id }。 */
    private static final Map<String, String[]> BADGES = new LinkedHashMap<>();

    static {
        BADGES.put("bg_remark", new String[]{"webhtv_color_badge_progress", "playback"});
        BADGES.put("bg_site", new String[]{"webhtv_color_badge_site", "site"});
        BADGES.put("bg_year", new String[]{"webhtv_color_badge_year", "year"});
    }

    private static final Pattern COLOR_ENTRY = Pattern.compile("<color name=\"([^\"]+)\">([^<]+)</color>");
    private static final Pattern SELECTOR_ITEM = Pattern.compile("<item\\b([^>]*)/?>");
    private static final Pattern ATTRIBUTE = Pattern.compile("android:(alpha|color)=\"([^\"]+)\"");
    private static final Pattern RAW_HEX = Pattern.compile("#[0-9A-Fa-f]{3,8}");

    // ------------------------------------------------------------------ 语义

    @Test
    public void cardBadgesUseTokensInsteadOfRawHex() throws Exception {
        for (Map.Entry<String, String[]> badge : BADGES.entrySet()) {
            String name = badge.getKey();
            String token = badge.getValue()[0];
            String source = read(LEANBACK_COLOR + name + ".xml");
            assertFalse(name + " 不允许再出现裸十六进制",
                    RAW_HEX.matcher(withoutComments(source)).find());
            assertTrue(name + " 必须引用 @" + token, source.contains("@color/" + token));
            assertFalse(name + " 不应再带透明度：半透明底色叠在浅色海报上会让白字跌破 4.5:1",
                    withoutComments(source).contains("android:alpha"));
            assertFalse(name + " 不得改成 ?attr 角色：那会让可读性依赖主题取值",
                    withoutComments(source).contains("?attr/"));
        }
    }

    @Test
    public void badgeTokensAreDeclaredInBothPalettesWithTheSameValue() throws Exception {
        String light = read(TOKENS);
        String night = read(TOKENS_NIGHT);
        for (String[] badge : BADGES.values()) {
            String token = badge[0];
            String lightValue = tokenValue(light, token);
            assertTrue(token + " 必须声明在日间表", lightValue != null);
            assertEquals(token + " 两张表必须同值：角标画在海报上，与调色板无关",
                    lightValue, tokenValue(night, token));
        }
    }

    // ------------------------------------------------------------------ 可读性

    /**
     * 角标白字必须在**任意海报**上都达标。
     *
     * <p>做法是按角标色表里声明的 alpha，把底色合成到最坏底（纯白海报）上再算对比度；
     * 角标一旦不透明，合成结果就是它自身，对比度与海报无关。
     */
    @Test
    public void badgeTextStaysReadableOverAnyPoster() throws Exception {
        String tokens = read(TOKENS);
        for (Map.Entry<String, String[]> badge : BADGES.entrySet()) {
            String name = badge.getKey();
            String token = badge.getValue()[0];
            String literal = tokenValue(tokens, token);
            assertTrue(token + " 必须是可直接解析的十六进制取值", literal != null && literal.startsWith("#"));

            double alpha = declaredAlpha(read(LEANBACK_COLOR + name + ".xml"));
            double ratio = contrast(BADGE_FOREGROUND, hex(compositeOver(literal, alpha, 0xFFFFFF)));

            assertTrue(name + " 的白字在纯白海报上只有 "
                            + String.format(Locale.ROOT, "%.2f:1", ratio)
                            + "（alpha=" + alpha + "），低于 " + MIN_CONTRAST + ":1",
                    ratio >= MIN_CONTRAST);
        }
    }

    /**
     * 角标文字必须走壁纸前景角色。
     *
     * <p>如果哪天把角标文字换成调色板跟随的角色（例如 {@code ?attr/colorOnSurface}），
     * 上面那条"白字对深色角标"的保证就不再成立，这里先把配对钉住。
     */
    @Test
    public void badgeLabelsUseTheWallpaperForegroundRole() throws Exception {
        String layout = read(ADAPTER);
        for (String[] badge : BADGES.values()) {
            assertEquals(badge[1] + " 角标文字必须走 ?attr/webhtvColorOnWallpaper",
                    "?attr/webhtvColorOnWallpaper", textColourOf(layout, badge[1]));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String tokenValue(String tokens, String token) {
        Matcher matcher = COLOR_ENTRY.matcher(withoutComments(tokens));
        while (matcher.find()) {
            if (matcher.group(1).equals(token)) return matcher.group(2).trim();
        }
        return null;
    }

    /** 角标色表声明的 alpha；未声明即不透明。 */
    private static double declaredAlpha(String source) {
        Matcher item = SELECTOR_ITEM.matcher(withoutComments(source));
        assertTrue("角标色表必须至少有一段 <item>", item.find());
        Matcher attribute = ATTRIBUTE.matcher(item.group(1));
        while (attribute.find()) {
            if ("alpha".equals(attribute.group(1))) return Double.parseDouble(attribute.group(2));
        }
        return 1.0;
    }

    /** 把 {@code foreground} 按 {@code alpha} 合成到 {@code backdrop} 上（逐通道 sRGB）。 */
    private static int compositeOver(String foreground, double alpha, int backdrop) {
        int value = Integer.parseInt(foreground.substring(1), 16);
        return (mix((value >> 16) & 0xFF, (backdrop >> 16) & 0xFF, alpha) << 16)
                | (mix((value >> 8) & 0xFF, (backdrop >> 8) & 0xFF, alpha) << 8)
                | mix(value & 0xFF, backdrop & 0xFF, alpha);
    }

    private static int mix(int channel, int backdrop, double alpha) {
        return (int) Math.round(alpha * channel + (1 - alpha) * backdrop);
    }

    private static String hex(int value) {
        return String.format("#%06X", value & 0xFFFFFF);
    }

    private static double contrast(String first, String second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(String hex) {
        String digits = hex.startsWith("#") ? hex.substring(1) : hex;
        if (digits.length() == 8) digits = digits.substring(2);
        double[] channels = new double[3];
        for (int i = 0; i < 3; i++) {
            double value = Integer.parseInt(digits.substring(i * 2, i * 2 + 2), 16) / 255.0;
            channels[i] = value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
    }

    private static String textColourOf(String layout, String id) {
        Matcher block = Pattern.compile("@\\+id/" + Pattern.quote(id) + "\"(.*?)(?:/>|</)",
                Pattern.DOTALL).matcher(layout);
        assertTrue("布局里必须能找到 @+id/" + id, block.find());
        Matcher colour = Pattern.compile("android:textColor=\"([^\"]+)\"").matcher(block.group(1));
        assertTrue("@+id/" + id + " 必须显式声明 textColor", colour.find());
        return colour.group(1);
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
