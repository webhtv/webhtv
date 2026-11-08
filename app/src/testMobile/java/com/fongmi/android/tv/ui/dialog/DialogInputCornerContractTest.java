package com.fongmi.android.tv.ui.dialog;

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
 * 输入框圆角与 §7.1 尺度表的契约。
 *
 * <p>设计文档 §7.1 把 8dp（`shapeSmall`）定义为"列表项、输入框、普通按钮"。改动前
 * `selector_dialog_input.xml` 用的是 **4dp**，即属于"标签、微小 chip"的 `shapeExtraSmall`
 * 档——输入框被放到了小一档的位置上。
 *
 * <p>这与内容卡圆角是两回事：网格内容卡用 8dp 是**有依据的取舍**（卡片半径与焦点环外边界
 * 几何耦合，见 §7.1 的补充说明），而输入框没有这个耦合，属**纯粹偏离**，因此按规范修正。
 */
public class DialogInputCornerContractTest {

    private static final String INPUT = "app/src/main/res/drawable/selector_dialog_input.xml";

    @Test
    public void inputUsesShapeSmallNotExtraSmall() throws Exception {
        String source = read(INPUT);
        // 两处（focused / 常态）都必须同步，否则聚焦时圆角会跳一下。
        assertEquals("输入框的两个状态必须都用 8dp（§7.1 shapeSmall）", 2, count(source, "android:radius=\"8dp\""));
        assertFalse("输入框不得再使用 4dp（那是 shapeExtraSmall，属标签/微小 chip）",
                source.contains("android:radius=\"4dp\""));
    }

    /**
     * 底材不得写死颜色（§9.2）：输入背景必须走语义 token。
     */
    @Test
    public void inputKeepsSemanticSurfaceAndOutline() throws Exception {
        String source = read(INPUT);
        assertTrue("常态描边必须走语义 outline", source.contains("?attr/colorOutline"));
        assertTrue("聚焦描边必须走语义 primary", source.contains("?attr/colorPrimary"));
        assertTrue("底材必须走 surfaceContainerHighest（§7.2 层级 4）",
                source.contains("?attr/colorSurfaceContainerHighest"));
        assertFalse("不得写死十六进制颜色", Pattern.compile("\"#[0-9A-Fa-f]{6,8}\"").matcher(source).find());
    }

    /**
     * §7.1 的补充说明必须留在规范里：网格内容卡 8dp 是有依据的取舍，不是漂移。
     * 若有人删掉这段理由，下一个人就会"顺手"把它改成 12dp 并撞上月牙问题。
     */
    @Test
    public void specKeepsTheReasonForGridCardRadius() throws Exception {
        String spec = read("docs/webhtv-unified-visual-design-system-20260920.md");
        assertTrue("§7.1 必须记录网格内容卡 8dp 的理由", spec.contains("密集海报网格"));
        assertTrue("必须说明环外边界半径 = 卡片半径 + 环宽/2", spec.contains("环宽/2"));
        assertTrue("必须说明月牙现象", spec.contains("月牙"));
    }

    /**
     * §7.2 的玻璃判据必须是"底材是否可控"，而不是"宿主是不是播放器"——
     * 否则手机端分类/筛选面板会被误判为越界。
     */
    @Test
    public void specStatesTheGlassCriterionNotTheHostName() throws Exception {
        String spec = read("docs/webhtv-unified-visual-design-system-20260920.md");
        assertTrue("§7.2 必须给出玻璃判据", spec.contains("底材是否可控"));
        assertTrue("必须点明透明度不得成为可读性的唯一保障", spec.contains("唯一保障"));
    }

    /**
     * §9.9 必须把播放器错误层记为明文例外，否则下一个人会去"补"错误色并降低对比度。
     */
    @Test
    public void specRecordsThePlayerErrorLayerAsAnExplicitException() throws Exception {
        String spec = read("docs/webhtv-unified-visual-design-system-20260920.md");
        assertTrue("§9.9 必须记录播放器错误层的例外", spec.contains("播放器错误层不使用"));
        assertTrue("必须说明理由是底材不可控", spec.contains("任意视频画面"));
    }

    // ------------------------------------------------------------------ helpers

    private static int count(String source, String needle) {
        Matcher matcher = Pattern.compile(Pattern.quote(needle)).matcher(source);
        int total = 0;
        while (matcher.find()) total++;
        return total;
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
