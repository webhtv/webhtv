package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 手机端播放器错误层重试的契约。
 *
 * <p>与电视端的 `TvPlayerErrorRetryContractTest` 同构。设计文档 §9.9 要求错误态有
 * 「重试动作」；改动前手机端的错误层是一个**纯文本** TextView，只有失败原因，
 * 没有任何可操作的东西。
 *
 * <p>本契约钉住两件容易改坏的事：
 * <ol>
 *   <li><b>不新写播放路径</b>：重试必须复用既有的 {@code onReset()}，而不是另起一套恢复逻辑
 *       与自动换源打架；</li>
 *   <li><b>文本与按钮分离后不能留旧引用</b>：{@code @id/error} 从 TextView 变成了容器，
 *       原来的 {@code widget.error.setText(...)} 会编译不过——但若有人把 id 挪回去而忘了
 *       改 Java，这里会先失败。</li>
 * </ol>
 */
public class MobilePlayerErrorRetryContractTest {

    private static final String WIDGET = "app/src/mobile/res/layout/view_widget_vod.xml";
    private static final String VIDEO = "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java";

    @Test
    public void errorOverlayIsAContainerWithReasonTextAndARetry() throws Exception {
        String layout = read(WIDGET);
        for (String id : new String[]{"error", "errorText", "errorRetry"}) {
            assertTrue("错误层必须声明 @+id/" + id, layout.contains("@+id/" + id));
        }
        // @id/error 必须是容器而不是文本：它是重试按钮的宿主。
        String container = block(layout, "error");
        assertTrue("@id/error 必须是容器（LinearLayoutCompat）", container.contains("LinearLayoutCompat"));
        assertTrue("容器必须竖排", container.contains("android:orientation=\"vertical\""));

        String retry = block(layout, "errorRetry");
        assertTrue("重试必须是按钮而不是裸文本", retry.contains("MaterialButton"));
        assertTrue("重试必须用应用自己的语义按钮样式",
                retry.contains("@style/Widget.WebHTV.Button.Tonal"));
        assertTrue("重试文案复用 error_retry", retry.contains("@string/error_retry"));
    }

    @Test
    public void showingAndHidingTheErrorDrivesTheReasonTextNotTheContainer() throws Exception {
        String source = read(VIDEO);
        String show = methodBody(source, "private void showError(String text)");
        assertTrue("显示错误必须设置原因文本", show.contains("mBinding.widget.errorText.setText(text)"));
        String hide = methodBody(source, "private void hideError()");
        assertTrue("隐藏错误必须清空原因文本", hide.contains("mBinding.widget.errorText.setText(\"\")"));
        // 容器不再是 TextView，任何残留的 widget.error.setText(...) 都会编译不过；
        // 这里显式钉住，避免有人把 id 挪回去而只改一半。
        assertFalse("不得再对容器调用 setText（它已不是 TextView）",
                source.contains("mBinding.widget.error.setText("));
    }

    @Test
    public void retryReusesTheExistingResetActionInsteadOfANewPlaybackPath() throws Exception {
        String source = read(VIDEO);
        assertTrue("重试必须复用既有的 onReset",
                source.contains("mBinding.widget.errorRetry.setOnClickListener(guarded(this::onReset))"));
        String reset = methodBody(source, "private void onReset()");
        assertTrue("onReset 必须仍按 isReplay() 分流", reset.contains("isReplay()"));
        assertTrue("onReset 必须仍走 onReplay", reset.contains("onReplay()"));
        assertTrue("onReset 必须仍走 onRefresh", reset.contains("onRefresh()"));
    }

    // ------------------------------------------------------------------ helpers

    /** 取方法体：先剥注释再按花括号配对，匿名内部类也能正确收尾。 */
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

    /** 取某个 {@code @+id/xxx} 控件标签自身的属性文本。 */
    private static String block(String layout, String id) {
        int at = layout.indexOf("@+id/" + id + "\"");
        assertTrue("布局里必须能找到 @+id/" + id, at > 0);
        int tagStart = layout.lastIndexOf("<", at);
        int end = layout.length();
        int selfClosing = layout.indexOf("/>", at);
        if (selfClosing >= 0) end = Math.min(end, selfClosing);
        int closing = layout.indexOf(">", at);
        if (closing >= 0) end = Math.min(end, closing);
        return layout.substring(tagStart, end);
    }

    /** 剥掉 XML 注释，避免负向断言命中说明文字（本仓库已踩过多次同类坑）。 */
    private static String withoutComments(String source) {
        return source.replaceAll("(?s)<!--.*?-->", " ");
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
