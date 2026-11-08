package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 加载圈期间的卡顿有一部分来自 {@link ProgressLayout} 自己：每进一次 CONTENT 都会重放一段
 * 内容淡入（setAlpha(0) + 新建 ViewPropertyAnimator），而 EMPTY/ERROR 与 CONTENT 之间
 * 的切换在详情页/播放页非常频繁。这些测试锁住「切换幂等且不再白建动画」这条约束，
 * 同时锁住淡入本身没被删掉——去掉淡入是另一种行为回退。
 */
public class ProgressLayoutContractTest {

    @Test
    public void showingAnAlreadyVisibleContentDoesNotReplayTheFade() throws Exception {
        String body = methodBody(readMainSource("ui/custom/ProgressLayout.java"), "private void showView(View view)");
        assertTrue("showView must skip the fade when the view is already fully visible, or every"
                        + " state flip re-requests a frame during the loading spinner",
                body.contains("if (view.getVisibility() == VISIBLE && view.getAlpha() == 1f) return;"));
    }

    @Test
    public void showingCancelsThePreviousAnimatorBeforeRestarting() throws Exception {
        String body = methodBody(readMainSource("ui/custom/ProgressLayout.java"), "private void showView(View view)");
        int cancel = body.indexOf("view.animate().cancel();");
        int fadeIn = body.indexOf("view.animate().alpha(1f).setDuration(100);");
        assertTrue("showView must cancel the previous animator", cancel >= 0);
        assertTrue("showView must still fade the content in", fadeIn >= 0);
        assertTrue("showView must cancel before it starts a new fade", cancel < fadeIn);
    }

    @Test
    public void hidingAnAlreadyHiddenContentIsFree() throws Exception {
        String body = methodBody(readMainSource("ui/custom/ProgressLayout.java"), "private void hideView(View view)");
        assertTrue("hideView must skip work when the view is already INVISIBLE",
                body.contains("if (view.getVisibility() == INVISIBLE) return;"));
        int cancel = body.indexOf("view.animate().cancel();");
        int invisible = body.indexOf("view.setVisibility(INVISIBLE);");
        assertTrue("hideView must cancel a fade that is still running", cancel >= 0);
        assertTrue("hideView must still hide the view", invisible >= 0);
        assertTrue("hideView must cancel before it hides", cancel < invisible);
        assertFalse("hideView must not animate the content away, it is a state switch not a transition",
                body.contains("setDuration("));
    }

    @Test
    public void contentVisibilityStillDrivesEveryContentChild() throws Exception {
        String source = readMainSource("ui/custom/ProgressLayout.java");
        String body = methodBody(source, "private void setContentVisibility(boolean visible)");
        assertTrue("setContentVisibility must keep routing VISIBLE through showView", body.contains("if (visible) showView(view);"));
        assertTrue("setContentVisibility must keep routing hidden through hideView", body.contains("else hideView(view);"));
        String stateBody = methodBody(source, "public void switchState(State state)");
        assertTrue("CONTENT must still reveal the content views", stateBody.contains("setContentVisibility(true);"));
    }

    private static String methodBody(String source, String signature) {
        // 工作区被 core.autocrlf 转成 CRLF，而断言按 LF 写：先归一化再切片，
        // 否则同样会撞上本仓库既有那批「行尾冤案」。
        String text = source.replace("\r\n", "\n");
        int start = text.indexOf(signature);
        assertTrue("source is missing " + signature, start >= 0);
        int end = text.indexOf("\n    }", start);
        assertTrue("source is missing the end of " + signature, end > start);
        return text.substring(start, end);
    }

    private static String readMainSource(String file) throws Exception {
        Path app = Files.exists(Path.of("src", "main")) ? Path.of(".") : Path.of("app");
        Path path = app.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", file));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
