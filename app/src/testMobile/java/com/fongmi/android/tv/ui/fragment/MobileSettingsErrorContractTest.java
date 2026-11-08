package com.fongmi.android.tv.ui.fragment;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 手机端设置页内联错误的契约（电视端 TV-6 的同构项）。
 *
 * <p>设计文档 §9.9 要求错误态有「错误色」。改动前 {@code setError} 只改文案不改颜色，
 * 于是错误与正常取值**看起来完全一样**——用户根本不知道这一行是失败的。
 *
 * <p>本契约钉住三件事：
 * <ol>
 *   <li><b>可区分</b>：{@code setError} 必须同时换错误色与实心深底；</li>
 *   <li><b>可复位</b>：{@code setSummary} 必须把颜色与底材都还原——这些摘要复用同一个
 *       TextView，不复位的话某行失败一次后会一直是红的；</li>
 *   <li><b>前提成立</b>：{@code setSummary} 用 {@code setBackground(null)} 撤底材，
 *       前提是这些 TextView 在布局里**原本没有背景**。一旦有人给它们加了背景，
 *       复位就会把它一并抹掉——所以这里把前提也钉住。</li>
 * </ol>
 */
public class MobileSettingsErrorContractTest {

    private static final String ENHANCE =
            "app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingEnhanceFragment.java";
    private static final String AD =
            "app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingAdFragment.java";
    private static final String ENHANCE_LAYOUT = "app/src/mobile/res/layout/fragment_setting_enhance.xml";
    private static final String AD_LAYOUT = "app/src/mobile/res/layout/fragment_setting_ad.xml";
    private static final String CHIP = "app/src/main/res/drawable/shape_error_value.xml";

    @Test
    public void inlineErrorsUseTheErrorRoleAndTheSolidSubstrate() throws Exception {
        for (String path : new String[]{ENHANCE, AD}) {
            String source = read(path);
            assertTrue(path + " 的 setError 必须使用错误角色",
                    source.contains("R.attr.webhtvColorOnWallpaperError"));
            assertTrue(path + " 的 setError 必须换上错误底材",
                    source.contains("setBackgroundResource(R.drawable.shape_error_value)"));
            // 不能用跟随调色板的 colorError：浅色表的深红压在深色壁纸行色带上不可读。
            assertFalse(path + " 不得复用 ?attr/colorError",
                    source.contains("R.attr.colorError"));
        }
    }

    @Test
    public void successPathResetsBothColourAndSubstrate() throws Exception {
        for (String path : new String[]{ENHANCE, AD}) {
            String source = read(path);
            assertTrue(path + " 的成功路径必须走 setSummary",
                    source.contains("setSummary(view, supplier.get())"));
            String impl = methodBody(source, "private void setSummary(");
            assertTrue(path + " 的 setSummary 必须复位颜色",
                    impl.contains("setTextColor(themeColor(R.attr.webhtvColorOnWallpaper))"));
            assertTrue(path + " 的 setSummary 必须撤掉错误底材",
                    impl.contains("setBackground(null)"));
        }
    }

    /**
     * 取值摘要必须都走 setSummary：任何残留的直接 {@code mBinding.xText.setText(...)}
     * 都会在失败后保持红字加深底。
     */
    @Test
    public void noSummaryIsWrittenDirectlyBypassingTheReset() throws Exception {
        Pattern direct = Pattern.compile("mBinding\\.\\w*[Tt]ext\\.setText\\(");
        for (String path : new String[]{ENHANCE, AD}) {
            Matcher matcher = direct.matcher(withoutJavaComments(read(path)));
            if (matcher.find()) {
                assertTrue(path + " 仍有绕过复位的直接 setText：" + matcher.group(), false);
            }
        }
    }

    /**
     * {@code setBackground(null)} 的前提：这些 TextView 在布局里原本没有背景。
     */
    @Test
    public void summaryTextViewsHaveNoBackgroundSoClearingTheErrorSubstrateIsSafe() throws Exception {
        for (String layout : new String[]{ENHANCE_LAYOUT, AD_LAYOUT}) {
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

    /**
     * 底材必须与 flavor 无关：它是**实心**色，所以电视端夹取的 8.84:1 对手机端同样成立。
     * 若有人把它改成半透明，这个比值就不再可保证。
     */
    @Test
    public void errorSubstrateStaysSolidAndSharedBetweenFlavours() throws Exception {
        String chip = read(CHIP);
        assertTrue("错误底材必须用 solid 而不是半透明", chip.contains("<solid"));
        assertFalse("错误底材不得使用带 alpha 的 #AARRGGBB", chip.contains("android:alpha="));
        // 放在 main 源集，两个 flavor 共用同一份，避免出现内容相同的副本。
        assertFalse("leanback 不应再有第二份副本",
                Files.exists(Path.of("app/src/leanback/res/drawable/shape_error_value.xml")));
        assertTrue("底材色必须是 palette-independent 的固定语义色",
                read("app/src/main/res/values/webhtv_tokens.xml").contains("webhtv_color_error_value_bg"));
    }

    // ------------------------------------------------------------------ helpers

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

    /** 取某个 id 匹配位置之后、当前标签结束（{@code />} 或 {@code >}）之前的属性文本。 */
    private static String tagBlockAfter(String source, int from) {
        int end = source.length();
        int selfClosing = source.indexOf("/>", from);
        if (selfClosing >= 0) end = Math.min(end, selfClosing);
        int closing = source.indexOf(">", from);
        if (closing >= 0) end = Math.min(end, closing);
        return source.substring(from, end);
    }

    private static String withoutJavaComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
