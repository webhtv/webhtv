package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 空态/错误态浮层的 scrim 契约（设计文档 §5.3 / §5.4）。
 *
 * <p>§5.3 要求「图片上的文字必须有 scrim/gradient」，§5.4 要求「所有内容表面必须有不透明或
 * 可控的 surface 层」。空态/错误态的文字此前**直接压在壁纸（一张图）上、没有任何 scrim**。
 *
 * <p>靠调色无解：实测壁纸合成亮度 L=0.1217/0.1476/0.2019，正文（16sp）门槛 4.5:1，
 * 80% 白文字只有 4.61/4.08/3.30:1；**即使换成纯白，最亮壁纸上限也只有 4.17:1**。
 * 加 scrim 后（overlay_dark ≈32% 黑）为 6.02/5.43/4.53:1，全部达标。
 */
public class StateSurfaceScrimContractTest {

    private static final String PROGRESS_LAYOUT =
            "app/src/main/java/com/fongmi/android/tv/ui/custom/ProgressLayout.java";
    private static final String STYLES = "app/src/main/res/values/webhtv_styles.xml";
    private static final String TOKENS = "app/src/main/res/values/webhtv_tokens.xml";
    private static final String NIGHT_TOKENS = "app/src/main/res/values-night/webhtv_tokens.xml";

    /** 空态/错误态必须带 scrim，否则文字直接压在壁纸上（违反 §5.3/§5.4）。 */
    @Test
    public void emptyAndErrorStatesCarryAScrim() throws Exception {
        String source = read(PROGRESS_LAYOUT);
        String scrim = methodBody(source, "private void applyScrim(State state)");
        assertTrue("空态/错误态必须设 scrim", scrim.contains("setBackgroundColor(themeColor(R.attr.webhtvColorOverlayDark))"));
        assertTrue("必须同时覆盖 EMPTY 与 ERROR", scrim.contains("State.EMPTY") && scrim.contains("State.ERROR"));
        assertTrue("其余状态必须清掉背景", scrim.contains("setBackground(null)"));
        assertTrue("必须在状态切换处调用", source.contains("applyScrim(state)"));
    }

    /**
     * PROGRESS 态刻意不加 scrim：它是短暂的加载瞬间，加 scrim 会让页面闪一下暗。
     * 这条是**边界守卫**——防止有人"顺手"把它也加进去。
     */
    @Test
    public void progressStateDeliberatelyHasNoScrim() throws Exception {
        String scrim = methodBody(read(PROGRESS_LAYOUT), "private void applyScrim(State state)");
        assertFalse("PROGRESS 不得进入加 scrim 的分支", scrim.contains("State.PROGRESS"));
        assertFalse("CONTENT 不得进入加 scrim 的分支", scrim.contains("State.CONTENT"));
    }

    /**
     * scrim 角色必须真的接上线。此前 {@code webhtvColorScrim} / {@code webhtvColorOverlayDark} /
     * {@code webhtvColorOverlayLight} 三个属性都只声明、从未赋值——任何想用它们的布局都取不到值。
     */
    @Test
    public void theScrimRoleIsActuallyWiredToATheme() throws Exception {
        assertTrue("必须声明 webhtvColorOverlayDark 属性",
                read("app/src/main/res/values/webhtv_attrs.xml").contains("name=\"webhtvColorOverlayDark\""));
        assertTrue("Theme.WebHTV 必须真的给它赋值（只声明不接线等于没有）",
                read(STYLES).contains("<item name=\"webhtvColorOverlayDark\">@color/webhtv_color_overlay_dark</item>"));
        String light = read(TOKENS);
        assertTrue("浅色表必须有该 token", light.contains("name=\"webhtv_color_overlay_dark\""));
        assertTrue("深色表必须有该 token", read(NIGHT_TOKENS).contains("name=\"webhtv_color_overlay_dark\""));
    }

    /**
     * 空态/错误态的描述文字必须仍是正文档（16sp），因为 scrim 是按正文门槛算的。
     * 若有人把字号降到 14sp 以下而仍按 4.5:1 验收，这条会提醒重新核算。
     */
    @Test
    public void descriptionStaysBodySizedSoTheScrimMathHolds() throws Exception {
        String layout = read("app/src/leanback/res/layout/view_empty.xml");
        int at = layout.indexOf("@+id/description");
        assertTrue("空态必须有 description 槽位", at > 0);
        String block = layout.substring(at, Math.min(layout.length(), at + 600));
        assertTrue("描述文字必须是 16sp（scrim 的对比度按正文门槛核算）", block.contains("android:textSize=\"16sp\""));
        assertTrue("描述文字必须用壁纸次级前景角色", block.contains("?attr/webhtvColorOnWallpaperVariant"));
    }

    // ------------------------------------------------------------------ helpers

    private static String methodBody(String source, String signature) {
        String cleaned = source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
        int start = cleaned.indexOf(signature);
        assertTrue("必须存在方法 " + signature, start > 0);
        int open = cleaned.indexOf('{', start);
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
