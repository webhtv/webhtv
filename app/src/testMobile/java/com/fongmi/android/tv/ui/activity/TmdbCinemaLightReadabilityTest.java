package com.fongmi.android.tv.ui.activity;

import com.fongmi.android.tv.ui.helper.TmdbCinemaTheme;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 「光影剧幕」浅色模式的可读性数值契约。
 *
 * <p>用户报告：浅色光影剧幕下内容直接浮在剧照原图上，看不清；要求改回"早之前有白色中间层的版本，
 * 和深色模式的差不多"。深色剧幕一直是「暗幕 + 浅字」，浅色剧幕此前该层被改成全透明，于是文字与
 * chip 都没有稳定底板。
 *
 * <p>本测试不依赖设备：它<strong>从生产源码里读出白色中间层真正发布的 alpha</strong>，
 * 再按 sRGB 相对亮度与 WCAG 对比度公式验证正文/小标题达到可读阈值。因此它不是一份会漂移的
 * 副本——若有人把中间层调淡（或重新改成全透明），对比度会随之下降并让断言失败。
 */
public class TmdbCinemaLightReadabilityTest {

    private static final String ACTIVITY =
            "app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java";

    /** 中间层色调：浅色剧幕的白色幕布。 */
    private static final int LIGHT_LAYER_RGB = 0xF4F7FA;

    /** 文字区域下面最不利的剧照底色：饱和亮红，是这类剧集最常见的画面。 */
    private static final int[] BRIGHT_ARTWORK = {0xC8, 0x00, 0x00};

    private static String readActivity() throws Exception {
        Path path = Paths.get(ACTIVITY);
        if (!Files.exists(path)) path = Paths.get("../" + ACTIVITY);
        assertTrue("production activity source must be readable at " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("production must still declare " + signature, start > 0);
        return source.substring(start, source.indexOf("\n    }", start));
    }

    /**
     * 从 {@code cinemaLightBackdropShade()} 里取出文字区域实际叠加的两层 alpha：
     * 水平渐变最左端（正文所在的左侧）与垂直渐变最上端（文字块起始处，白度最低）。
     *
     * <p>取非紧凑分支（数组里的第二个字面量组），与电视横屏一致。
     */
    private static int[] shippedTextRegionAlphas() throws Exception {
        String body = methodBody(readActivity(), "private Drawable cinemaLightBackdropShade()");

        int horizontal = firstAlphaOfSecondArray(body, "Orientation.LEFT_RIGHT");
        int vertical = firstAlphaOfSecondArray(body, "Orientation.TOP_BOTTOM");
        return new int[]{horizontal, vertical};
    }

    /** 找到 orientation 标记后的 int[] 字面量组，返回第二组（非紧凑布局）的首个 alpha。 */
    private static int firstAlphaOfSecondArray(String body, String orientation) {
        int at = body.indexOf(orientation);
        assertTrue("middle layer must keep a " + orientation + " gradient", at > 0);
        String tail = body.substring(at);
        int open = tail.indexOf("new int[]{");
        assertTrue("gradient must be built from an int[] literal", open > 0);
        String literal = tail.substring(open);
        Matcher groups = Pattern.compile("new int\\[]\\{(.*?)\\}", Pattern.DOTALL).matcher(literal);

        List<String> arrays = new ArrayList<>();
        while (groups.find()) arrays.add(groups.group(1));
        assertTrue("gradient must have a compact and a non-compact array", arrays.size() >= 2);

        Matcher colors = Pattern.compile("0x([0-9A-Fa-f]{2})[0-9A-Fa-f]{6}").matcher(arrays.get(1));
        assertTrue("non-compact gradient array must contain ARGB colours", colors.find());
        return Integer.parseInt(colors.group(1), 16);
    }

    private static int composite(int base, int layerRgb, int alpha) {
        int channel = (base * (255 - alpha) + layerRgb * alpha) / 255;
        return Math.max(0, Math.min(255, channel));
    }

    /** 依次叠加水平层与垂直层，得到文字实际压着的底色。 */
    private static int[] shadedArtwork(int[] alphas) {
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            int rgb = (LIGHT_LAYER_RGB >> (16 - i * 8)) & 0xFF;
            int once = composite(BRIGHT_ARTWORK[i], rgb, alphas[0]);
            out[i] = composite(once, rgb, alphas[1]);
        }
        return out;
    }

    private static double relativeLuminance(int color) {
        double[] linear = new double[3];
        for (int i = 0; i < 3; i++) {
            double channel = ((color >> (16 - i * 8)) & 0xFF) / 255.0;
            linear[i] = channel <= 0.03928
                    ? channel / 12.92
                    : Math.pow((channel + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
    }

    private static double contrastRatio(int foreground, int background) {
        double a = relativeLuminance(foreground);
        double b = relativeLuminance(background);
        double lighter = Math.max(a, b);
        double darker = Math.min(a, b);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static int rgb(int[] channels) {
        return (channels[0] << 16) | (channels[1] << 8) | channels[2];
    }

    @Test
    public void lightCinemaKeepsAWhiteMiddleLayerInsteadOfAFullyTransparentOne() throws Exception {
        String source = readActivity();
        String shade = methodBody(source, "private Drawable cinemaBackdropShade()");

        assertTrue("浅色剧幕必须继续走白色中间层，不能退回全透明",
                shade.contains("if (lightTheme) return cinemaLightBackdropShade();"));
        assertTrue("白色中间层不得退化成透明 drawable",
                !shade.contains("return TmdbDetailLayoutUtils.colorDrawable(Color.TRANSPARENT);"));

        int[] alphas = shippedTextRegionAlphas();
        assertTrue("正文所在左侧必须有足够白的覆盖（实测 alpha=0x" + Integer.toHexString(alphas[0]) + "）",
                alphas[0] >= 0x66);
    }

    @Test
    public void restoredWhiteMiddleLayerMakesLightCinemaTextReadable() throws Exception {
        int background = rgb(shadedArtwork(shippedTextRegionAlphas()));
        int sectionTitle = TmdbCinemaTheme.palette(true).primary();
        int body = TmdbCinemaTheme.palette(true).body();

        double titleContrast = contrastRatio(sectionTitle, background);
        double bodyContrast = contrastRatio(body, background);

        assertTrue("浅色剧幕的白色中间层必须把亮剧照提亮到可读区间（实测标题 "
                        + String.format("%.2f", titleContrast) + ":1）",
                titleContrast >= 4.5);
        assertTrue("浅色剧幕正文也必须可读（实测 " + String.format("%.2f", bodyContrast) + ":1）",
                bodyContrast >= 4.5);
    }

    @Test
    public void forcingWhiteTextOverTheWhiteMiddleLayerWouldBeUnreadable() throws Exception {
        // 恢复白色中间层之后，文字底下是浅色底板。这正是必须停止在浅色剧幕强制白字的原因：
        // 白字压浅色底板达不到 WCAG AA 正文阈值，会看不见。
        int background = rgb(shadedArtwork(shippedTextRegionAlphas()));
        double whiteOnWhiteLayer = contrastRatio(0xFFFFFFFF, background);

        assertTrue("对照：白字压白色中间层达不到 AA 正文阈值（实测 "
                        + String.format("%.2f", whiteOnWhiteLayer) + ":1）",
                whiteOnWhiteLayer < 4.5);
    }

    @Test
    public void lightCinemaTextColourIsThePalettePrimaryNotForcedWhite() {
        // 浅色剧幕的正文色必须是调色板的深色；强制白字只在深色剧幕（暗幕）上成立。
        int lightPrimary = TmdbCinemaTheme.palette(true).primary();
        int darkPrimary = TmdbCinemaTheme.palette(false).primary();

        assertEquals("浅色剧幕正文色应为调色板 primary 的深色", 0xFF12202D, lightPrimary);
        assertEquals("深色剧幕正文色应为白色", 0xFFFFFFFF, darkPrimary);
        assertTrue("浅色剧幕不得沿用深色剧幕的强制白字", lightPrimary != 0xFFFFFFFF);
    }
}
