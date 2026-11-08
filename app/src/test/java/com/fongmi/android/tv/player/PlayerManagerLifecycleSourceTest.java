package com.fongmi.android.tv.player;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 本类断言的是 {@code PlayerManager} 的**实现文本**（仓库既有的 source-text 约定），用于锁定
 * 那些无法从行为上便宜地观测、却容易被顺手改掉的不变量。
 *
 * <p>若日后搬走、改名或重排这些方法，断言会变红 —— 那说明约定被破坏需要同步更新断言，
 * **不是**测试坏了。确认行为未变后请重新对齐断言，而不要删除用例。
 */
public class PlayerManagerLifecycleSourceTest {

    @Test
    public void releasedEngineClassificationIsNullSafeForLateCallbacks() throws Exception {
        String source = readPlayerManager();

        assertTrue("isLive must tolerate a late callback after engine release",
                source.contains("public boolean isLive() {\n        return engine != null && engine.isLive();"));
        assertTrue("isVod must tolerate a late callback after engine release",
                source.contains("public boolean isVod() {\n        return engine != null && engine.isVod();"));
    }

    /**
     * Slices one method body. Relies on the body containing no closing brace at four-space
     * indentation; every method asserted here satisfies that today. If a nested block ever
     * breaks it, the slice truncates early and the assertion fails spuriously — re-align the
     * helper rather than deleting the assertion.
     */
    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("method must exist: " + signature, start >= 0);
        int end = source.indexOf("\n    }", start);
        assertTrue("method body must be delimited: " + signature, end > start);
        return source.substring(start, end);
    }

    @Test
    public void seekInducedBufferingIsExcludedFromTheRebufferCount() throws Exception {
        String source = readPlayerManager();
        String body = methodBody(source, "private void recordBufferingState(");
        // MpvPlayer now publishes BUFFERING across a seek, which is what lets the UI keep its
        // progress indicator up instead of freezing on the old frame. That state reaches
        // PlaybackBufferingTracker, which counts every post-startup BUFFERING as a rebuffer,
        // and the rebuffer count feeds the network guard and the HLS variant policy. Without
        // this exclusion, scrubbing alone would look like collapsing throughput.
        int exclusion = body.indexOf("isMpvSeekBuffering()");
        int tracker = body.indexOf("playbackBufferingTracker.update(");
        assertTrue("recordBufferingState must recognise seek-induced buffering", exclusion >= 0);
        assertTrue("the seek exclusion must return before the rebuffer tracker is updated",
                tracker > exclusion);
        assertTrue("the exclusion must be traceable", body.contains("result=excluded-from-rebuffer"));
        // Only the entering edge may be dropped. Swallowing the leaving edge as well would
        // leave the tracker latched as buffering for the rest of the session.
        assertTrue("the exclusion must only apply to the entering edge",
                body.contains("!playbackBufferingTracker.isBuffering()"));
    }

    @Test
    public void bufferingStateArmsTheStallWatchdogForEveryEngine() throws Exception {
        String source = readPlayerManager();
        int buffering = source.indexOf("} else if (state == Player.STATE_BUFFERING) {");
        int nextBranch = source.indexOf("\n            } else {", buffering);
        assertTrue("state listener must contain the buffering branch", buffering >= 0);
        assertTrue("state listener buffering branch must have a closing boundary", nextBranch > buffering);
        int arm = source.indexOf("armBufferingStallWatchdog();", buffering);
        assertTrue("entering BUFFERING must start the stall watchdog", arm > buffering && arm < nextBranch);
        // The watchdog is engine-independent: MPV reaches the same frozen-BUFFERING state and
        // MpvPlayer.timeOutSeekBuffering() already defers to it. Restricting it to Exo left MPV
        // with no bound at all, because the 15 s startup timer is cancelled by BUFFERING itself.
        assertFalse("the stall watchdog must not be gated on a single engine",
                source.contains("if (isExo()) armBufferingStallWatchdog();"));
        assertTrue("watchdog arming must only require a live player and spec",
                source.contains("private void armBufferingStallWatchdog() {\n        if (player == null || spec == null) return;"));
        assertTrue("watchdog polling must only require a live player and spec",
                source.contains("private void checkBufferingStall() {\n        if (player == null || spec == null) {"));
        assertTrue("seeking must re-arm the stall watchdog for every engine",
                source.contains("player.seekTo(time);\n        cancelBufferingStallWatchdog();\n        armBufferingStallWatchdog();"));
        int polling = source.indexOf("private void checkBufferingStall()");
        int observe = source.indexOf("bufferingStallWatchdog.observe(", polling);
        int timeout = source.indexOf("bufferingStallWatchdog.shouldTimeout(", polling);
        assertTrue("buffering polling method must exist", polling >= 0);
        assertTrue("a discontinuity must be observed before timeout is evaluated",
                observe > polling && timeout > observe);
    }

    /**
     * 超时日志里的实测停滞时长必须在停掉看门狗之前读出。
     *
     * <p>{@code cancelBufferingStallWatchdog()} 会把看门狗置为未布防，而
     * {@code stalledDurationMs()} 在未布防时返回 {@code -1}。先取消再读，日志就永远写
     * {@code stalledMs=-1}，实测时长这个字段等于不存在——排查时无法分辨「刚过 20s 就超时」
     * 与「卡了 90s 才超时」。
     */
    @Test
    public void stallDurationIsMeasuredBeforeTheWatchdogIsDisarmed() throws Exception {
        String source = stripComments(readPlayerManager());
        String body = methodBody(source, "private void onBufferingStall(");
        int measured = body.indexOf("stalledDurationMs(");
        int disarmed = body.indexOf("cancelBufferingStallWatchdog();");
        assertTrue("onBufferingStall must report the measured stall", measured >= 0);
        assertTrue("onBufferingStall must disarm the watchdog", disarmed >= 0);
        assertTrue("reading stalledDurationMs() after reset() always yields -1", measured < disarmed);
    }

    /**
     * Drops whole-line comments and trailing {@code //} comments.
     *
     * <p>Without this, an assertion that looks for a call by name can be satisfied by the same
     * name inside the explanatory comment that documents it — the guard then passes no matter
     * what the code does. Verified by mutation: restoring the buggy order while the comment
     * still names {@code stalledDurationMs()} kept the assertion green until comments were
     * stripped.
     */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue;
            int marker = line.indexOf("//");
            out.append(marker >= 0 ? line.substring(0, marker) : line).append('\n');
        }
        return out.toString();
    }

    private static String readPlayerManager() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        Path source = root.resolve(Path.of(
                "app", "src", "main", "java", "com", "fongmi", "android", "tv", "player", "PlayerManager.java"));
        if (!Files.exists(source)) {
            source = root.resolve(Path.of(
                    "src", "main", "java", "com", "fongmi", "android", "tv", "player", "PlayerManager.java"));
        }
        return Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
