package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 播放器错误层重试的契约。
 *
 * <p>设计文档 §9.9 要求错误态有「重试动作」。改动前播放器只有错误图标 + 文案，
 * 用户唯一的办法是退出重进。
 *
 * <p>本契约钉住三件容易改坏的事：
 * <ol>
 *   <li><b>焦点环的族</b>：错误面板是 {@code shape_widget}（{@code black_40} 半透明深色，
 *       两张表都一样），属**固定深玻璃族**，环必须取 {@code ?attr/tvPlayerRing}。
 *       用空态那套 {@code ?attr/tvFocusRing} 在深玻璃上（浅色表）只有约 1.8:1，会看不见
 *       ——这正是 {@code selector_control_sheet_button} 注释里记录过的同类缺陷。</li>
 *   <li><b>焦点归属</b>：播放页平时把焦点放在视频面上，浮层里的按钮靠方向键不一定走得到，
 *       所以显示错误时要主动把焦点交给重试；隐藏时必须**只在重试确实持有焦点时**才交还视频面，
 *       否则会抢走剧集网格等其它控件的焦点。</li>
 *   <li><b>不新写播放路径</b>：重试必须复用既有的 {@code onReset()}，
 *       而不是另起一套恢复逻辑与自动换源（{@code startFlow}）打架。</li>
 * </ol>
 */
public class TvPlayerErrorRetryContractTest {

    private static final String WIDGET = "app/src/leanback/res/layout/view_widget_vod.xml";
    private static final String SHEET_BUTTON = "app/src/leanback/res/drawable/selector_control_sheet_button.xml";
    private static final String VIDEO = "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java";

    @Test
    public void errorOverlayOffersAFocusableRetry() throws Exception {
        String layout = read(WIDGET);
        assertTrue("错误层必须有 @+id/retry", layout.contains("@+id/retry"));
        String block = block(layout, "retry");
        assertTrue("重试必须可聚焦（电视上焦点不可见的按钮等于没有按钮）",
                block.contains("android:focusable=\"true\""));
        assertTrue("重试必须可点击", block.contains("android:clickable=\"true\""));
        assertTrue("重试文案复用 error_retry", block.contains("@string/error_retry"));
    }

    /**
     * 环族必须是播放器面板族，不能借空态那套应用表面族的 selector。
     */
    @Test
    public void retryUsesThePlayerPanelRingFamilyNotTheAppSurfaceOne() throws Exception {
        String block = block(read(WIDGET), "retry");
        assertTrue("必须用播放器面板族的 selector_control_sheet_button",
                block.contains("@drawable/selector_control_sheet_button"));
        assertFalse("不得用空态那套 selector_empty_action（它取 ?attr/tvFocusRing，深玻璃上看不见）",
                block.contains("selector_empty_action"));

        String sheet = read(SHEET_BUTTON);
        assertTrue("播放器面板按钮的焦点环必须取 ?attr/tvPlayerRing", sheet.contains("?attr/tvPlayerRing"));
        assertTrue("环宽必须取唯一来源 @dimen/webhtv_focus_ring_width",
                sheet.contains("@dimen/webhtv_focus_ring_width"));
        // 注释里会解释"为什么不能用 tvFocusRing"，所以先剥掉注释再查，
        // 否则负向断言会命中说明文字（本仓库已踩过三次同类坑）。
        assertFalse("播放器面板按钮不得取应用表面族的 ?attr/tvFocusRing",
                withoutComments(sheet).contains("?attr/tvFocusRing"));
    }

    @Test
    public void showingTheErrorTakesFocusAndHidingItGivesFocusBackSafely() throws Exception {
        String source = read(VIDEO);
        String show = methodBody(source, "private void showError(String text)");
        assertTrue("显示错误时必须把焦点交给重试按钮", show.contains("mBinding.widget.retry.requestFocus()"));

        String hide = methodBody(source, "private void hideError()");
        assertTrue("隐藏错误时必须在重试持有焦点的情况下交还视频面",
                hide.contains("if (mBinding.widget.retry.hasFocus()) mBinding.video.requestFocus()"));
    }

    @Test
    public void retryReusesTheExistingResetActionInsteadOfANewPlaybackPath() throws Exception {
        String source = read(VIDEO);
        assertTrue("重试必须复用既有的 onReset",
                source.contains("mBinding.widget.retry.setOnClickListener(guarded(this::onReset))"));
        // onReset 必须仍然是"按用户设置决定从头重播还是重新拉流"的那一条
        String reset = methodBody(source, "private void onReset()");
        assertTrue("onReset 必须仍按 isReplay() 分流", reset.contains("isReplay()"));
        assertTrue("onReset 必须仍走 onReplay", reset.contains("onReplay()"));
        assertTrue("onReset 必须仍走 onRefresh", reset.contains("onRefresh()"));
    }

    // ------------------------------------------------------------------ helpers

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("必须存在方法 " + signature, start > 0);
        int end = source.indexOf("\n    }", start);
        return source.substring(start, end > 0 ? end : source.length());
    }

    private static String block(String layout, String id) {        int at = layout.indexOf("@+id/" + id + "\"");
        assertTrue("布局里必须能找到 @+id/" + id, at > 0);
        int tagStart = layout.lastIndexOf("<", at);
        int end = layout.length();
        int selfClosing = layout.indexOf("/>", at);
        if (selfClosing >= 0) end = Math.min(end, selfClosing);
        int closing = layout.indexOf(">", at);
        if (closing >= 0) end = Math.min(end, closing);
        return layout.substring(tagStart, end);
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }

    /** 剥掉 XML 注释，避免负向断言命中说明文字。 */
    private static String withoutComments(String source) {
        return source.replaceAll("(?s)<!--.*?-->", " ");
    }
}
