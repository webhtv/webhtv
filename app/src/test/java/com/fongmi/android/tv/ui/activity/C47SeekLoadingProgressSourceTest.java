package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 拖拽进度时「大概率看不到加载中/网速」的回归契约。
 *
 * <p>现象：拖动进度条后，加载圈（转圈 + 网速）基本不出现，或者一闪就没。
 *
 * <p>根因：{@code onSeekStarted()} 会在 seek 命令发出前先亮圈，随后 {@code showProgress()}
 * 立刻投递每秒一跳的网速 ticker（{@code App.post(mR2/mR3, 0)}）。那个 ticker 的兜底收口
 * {@code hidePlaybackProgressIfStale()} 读到引擎尚未开始 seek 的旧 READY，就在同一帧把圈
 * 收掉——圈因此连最小可见时长都活不到，第二跳网速采样（需要 1000ms 间隔）更没机会发生。
 *
 * <p>修法：seek 期间打开一个「最小可见 + 必须确实可收」的窗口。窗口未关时任何收圈路径
 * （网速 ticker 兜底、READY 回调、控制器晚绑定补发）都不得收圈；窗口由显式收圈关闭。
 */
public class C47SeekLoadingProgressSourceTest {

    private static final String MOBILE =
            "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java";
    private static final String LEANBACK =
            "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java";
    private static final String[] SOURCES = {MOBILE, LEANBACK};

    @Test
    public void seekOpensTheMinimumVisibleWindowBeforeShowingProgress() throws Exception {
        for (String path : SOURCES) {
            String body = bodyToBrace(read(path), "protected void onSeekStarted()");

            int pending = body.indexOf("mSeekProgressPending = true;");
            int startedAt = body.indexOf("mSeekProgressStartedAtMs = SystemClock.elapsedRealtime();");
            int show = body.indexOf("showProgress();");
            int arm = body.indexOf("App.post(mSeekProgressFallback, SEEK_PROGRESS_MIN_VISIBLE_MS);");

            assertTrue(path + " must open the seek window", pending >= 0);
            assertTrue(path + " must stamp the window start", startedAt > pending);
            // showProgress() 会立刻投递网速 ticker，窗口必须先于它打开，否则第一跳就把圈收掉。
            assertTrue(path + " must open the window before showProgress() posts the traffic ticker",
                    show > startedAt);
            assertTrue(path + " must arm the minimum-visible timer after showing progress", arm > show);
        }
    }

    @Test
    public void theMinimumVisibleGateIsTheOnlyWayToCloseTheSeekWindow() throws Exception {
        for (String path : SOURCES) {
            String source = read(path);
            String gate = bodyToBrace(source, "private boolean canHideSeekProgress()");
            String fallback = bodyToBrace(source, "private void hideSeekProgressIfReady()");
            String stale = bodyToBrace(source, "private void hidePlaybackProgressIfStale()");

            // 最小可见时长：第二跳网速采样需要 1000ms 间隔，窗口不能被瞬间收掉。
            assertTrue(path + " must enforce a minimum visible duration",
                    gate.contains("SystemClock.elapsedRealtime() - mSeekProgressStartedAtMs < SEEK_PROGRESS_MIN_VISIBLE_MS"));
            // 网速读数由相邻两次采样的字节差算出，而采样每秒一跳：窗口必须盖过一跳，
            // 否则加载圈虽然出现了，用户仍然看不到网速（本次诉求的另一半）。
            assertTrue(path + " must keep the overlay visible long enough to render one speed sample",
                    seekMinVisibleMs(source) >= 1000L);
            assertTrue(path + " must keep the readout ticker period at one second",
                    bodyToBrace(source, "private void setTraffic()").contains("App.post(mR2, 1000);")
                            || bodyToBrace(source, "private void setTraffic()").contains("App.post(mR3, 1000);"));
            assertTrue(path + " must not treat an unopened window as blocking",
                    gate.contains("if (!mSeekProgressPending) return true;"));
            // 已 READY 但仍在真实加载（且没在播）时不能收圈，那正是「卡画面」的那一帧。
            assertTrue(path + " must keep the spinner while the player is still loading",
                    gate.contains("player().getPlaybackState() == Player.STATE_READY")
                            && gate.contains("!player().isLoading() || player().isPlaying()"));

            assertTrue(path + " must close the window when the gate opens",
                    fallback.contains("if (!canHideSeekProgress()) return;")
                            && fallback.contains("mSeekProgressPending = false;"));

            // 网速 ticker 的兜底收口是本次症状的直接执行者，必须受同一道闸门约束。
            assertTrue(path + " must not let the traffic ticker hide a pending seek",
                    stale.contains("if (!canHideSeekProgress()) return;"));
            assertTrue(path + " must keep the stale-spinner fallback owner-scoped",
                    stale.contains("if (!isOwner()) return;"));
            assertTrue(path + " must only act while the spinner is up",
                    stale.contains("getVisibility() != View.VISIBLE"));

            // 控制器晚绑定补发收口同样不能越过闸门。
            String reconcile = bodyToBrace(source, "protected void onControllerReadyReconciled()");
            assertTrue(path + " must gate the late-controller reconciliation",
                    reconcile.contains("if (canHideSeekProgress()) showPlaybackContent();"));
        }
    }

    @Test
    public void aStaleReadyReadingCannotCloseTheSeekWindow() throws Exception {
        for (String path : SOURCES) {
            String source = read(path);
            int state = source.indexOf("protected void onStateChanged(int state)");
            int ready = source.indexOf("case Player.STATE_READY:", state);
            int ended = source.indexOf("case Player.STATE_ENDED:", ready);
            assertTrue(path + " must find the READY branch", state >= 0 && ready > state && ended > ready);

            String readyBody = source.substring(ready, ended);
            assertTrue(path + " must route the READY reveal through the seek gate",
                    readyBody.contains("if (canHideSeekProgress()) showPlaybackContent();"));
            // 未通过闸门时绝不能无条件收圈：那是把圈收在冻结画面上的原始缺陷。
            assertFalse(path + " must not reveal playback content unconditionally on READY",
                    readyBody.contains("\n                showPlaybackContent();"));
        }
    }

    @Test
    public void anyExplicitHideAlsoClosesTheWindow() throws Exception {
        for (String path : SOURCES) {
            String hide = bodyToBrace(read(path), "private void hideProgress()");
            assertTrue(path + " must clear the pending seek window whenever progress is hidden",
                    hide.contains("mSeekProgressPending = false;")
                            && hide.contains("mSeekProgressStartedAtMs = 0;"));
            // 顺序：先关窗口再摘回调，避免关窗后仍有缓存计时器把状态又推回去。
            assertTrue(path + " must close the window before dropping its callback",
                    hide.indexOf("mSeekProgressPending = false;") < hide.indexOf("App.removeCallbacks(mSeekProgressFallback);"));
        }
    }

    @Test
    public void showingProgressKeepsAnOpenSeekWindowArmed() throws Exception {
        for (String path : SOURCES) {
            String show = bodyToBrace(read(path), "private void showProgress()");
            // seek 窗口开着时那次 showProgress 属于 seek 自身（BUFFERING 分支），
            // 不能顺手把窗口唯一的最小可见计时器摘掉。
            assertTrue(path + " must not cancel an open seek window's timer while showing progress",
                    show.contains("if (!mSeekProgressPending && mSeekProgressFallback != null) App.removeCallbacks(mSeekProgressFallback);"));
        }
    }

    /** 取源文件里 seek 加载圈最小可见时长常量的数值。 */
    private static long seekMinVisibleMs(String source) {
        String marker = "private static final long SEEK_PROGRESS_MIN_VISIBLE_MS = ";
        int start = source.indexOf(marker);
        assertTrue("cannot locate SEEK_PROGRESS_MIN_VISIBLE_MS", start >= 0);
        int end = source.indexOf(';', start + marker.length());
        assertTrue("cannot locate the end of SEEK_PROGRESS_MIN_VISIBLE_MS", end > start);
        return Long.parseLong(source.substring(start + marker.length(), end).trim().replace("L", "").replace("l", ""));
    }

    /** 取一个方法体：从签名到紧随其后的第一个方法级闭合花括号。 */
    private static String bodyToBrace(String source, String marker) {
        int start = source.indexOf(marker);
        assertTrue("cannot locate " + marker, start >= 0);
        int end = source.indexOf("\n    }", start + marker.length());
        assertTrue("cannot locate the end of " + marker, end > start);
        return source.substring(start, end);
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of(path.substring("app/".length())), StandardCharsets.UTF_8);
    }
}
