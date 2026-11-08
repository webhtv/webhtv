package com.fongmi.android.tv.player;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 本类断言 {@code PlayerManager} 的**实现文本**（仓库既有的 source-text 约定），锁定
 * “每次内核/软硬解切换都必须能回答为什么换”的不变量。
 *
 * <p>这些断言无法从行为上便宜地观测：切换发生在多个异步路径里，而这里要保证的恰恰是
 * “所有路径都经过同一个归因入口”。若日后重构掉这些方法，断言会变红 —— 那说明约定被破坏
 * 需要同步更新断言，**不是**测试坏了。确认行为未变后请重新对齐断言。
 */
public class PlayerManagerSwitchCauseSourceTest {

    @Test
    public void everyKernelSwitchFlowsThroughTheSingleCauseLogger() throws Exception {
        String source = readPlayerManager();
        String engineSwitch = methodBody(source,
                "private void switchEngine(int type, boolean chosen, boolean notifyPrepare, int decode, long position, float speed, boolean repeat, boolean wasPlayWhenReady, String cause)",
                "private void rebuildPlayer()");

        assertTrue("the kernel switch must log its cause", engineSwitch.contains("logSwitchCause(cause,"));
        // Capture the previous identity before mutating it, or the log would report from==to.
        int captureType = engineSwitch.indexOf("int previousType = playerType;");
        int assign = engineSwitch.indexOf("playerType = type;");
        assertTrue("the previous kernel must be captured before it is overwritten",
                captureType >= 0 && assign > captureType);
        int captureDecode = engineSwitch.indexOf("int previousDecode = engine == null ? -1 : engine.getDecode();");
        assertTrue("the previous decode must be captured before the engine is released",
                captureDecode >= 0 && captureDecode < engineSwitch.indexOf("engine.release();"));
    }

    @Test
    public void switchKindIsDerivedFromWhatActuallyChanged() throws Exception {
        String source = readPlayerManager();
        String derived = methodBody(source,
                "private void logSwitchCause(String cause, int fromType, int toType,",
                "private static String decodeText(int decode)");

        // A decode-only switch (same kernel, hard->soft) must not be reported as a kernel switch.
        assertTrue("kind must be derived from the actual change",
                derived.contains("fromType != toType ? \"kernel\" : fromDecode != toDecode ? \"decode\" : \"none\""));
    }

    @Test
    public void fallbackCausesCarryTheTriggeringErrorCode() throws Exception {
        String source = readPlayerManager();
        String decodeFallback = methodBody(source,
                "private boolean fallbackDecode(PlaybackException e)",
                "private boolean fallbackPlayer(PlaybackException e)");
        String playerFallback = methodBody(source,
                "private boolean fallbackPlayer(PlaybackException e)",
                "private static String fallbackTrigger(PlaybackException e)");
        String trigger = methodBody(source,
                "private static String fallbackTrigger(PlaybackException e)",
                "private boolean ensurePlayerAvailableForPlayback()");

        assertTrue("a decode fallback must state why it happened",
                decodeFallback.contains("\"fallback-decode:\" + fallbackTrigger(e)"));
        assertTrue("a kernel fallback must state why it happened",
                playerFallback.contains("\"fallback-kernel:\" + fallbackTrigger(e)"));
        // The mapping must not collapse every failure into one label, or the log cannot
        // distinguish a timeout from a decoder failure.
        assertTrue(trigger.contains("ERROR_CODE_TIMEOUT -> \"timeout\""));
        assertTrue(trigger.contains("ERROR_CODE_DECODER_INIT_FAILED -> \"decoder-init\""));
        assertTrue(trigger.contains("default -> \"code-\" + e.errorCode"));
    }

    @Test
    public void kernelUnavailableIsDistinguishedFromFailureFallback() throws Exception {
        String source = readPlayerManager();
        String ensure = methodBody(source,
                "private boolean ensurePlayerAvailableForPlayback()",
                "private int nextFallbackPlayer()");

        assertTrue("an unavailable kernel is not a playback failure and must say so",
                ensure.contains("\"kernel-unavailable\""));
    }

    @Test
    public void historyKernelAndPendingKernelAreDistinguishable() throws Exception {
        String source = readPlayerManager();
        // force=true is only ever passed by the onError retry, so it doubles as the cause signal
        // and no public signature had to change to record it.
        assertTrue(source.contains("force ? \"history-kernel-retry-after-error\" : \"history-kernel-before-play\""));
        // preparePlayer is the only entry point that restores a remembered kernel before the
        // play url is resolved, so its cause is unambiguous without a parameter.
        String prepare = methodBody(source, "public void preparePlayer(int type, boolean force)", "public void switchPlayer(int type, PlaySpec freshSpec");
        assertTrue("restoring a remembered kernel must be logged", prepare.contains("logSwitchCause("));
        assertFalse("preparePlayer must not take a cause parameter; the call sites are all the same reason",
                prepare.contains("String cause"));
    }

    @Test
    public void userDrivenSwitchesAreLabelledAsUserChoice() throws Exception {
        String source = readPlayerManager();
        // Every switchPlayer/switchDecode caller is a UI entry point, so the label is derived
        // inside PlayerManager instead of being threaded through five Activity files.
        assertTrue(source.contains("switchEngine(type, true, true, true, engine.getDecode(), \"user-choice\")"));
        assertTrue(source.contains("logSwitchCause(\"user-choice\", previousType, type, decode, decode, true, position)"));
        assertTrue(source.contains("logSwitchCause(\"decode\", \"user-choice\", playerType, playerType, engine.getDecode(), next, false, position)"));
    }

    @Test
    public void chosenFlagSeparatesUserChoiceFromAutomaticFallback() throws Exception {
        String source = readPlayerManager();
        String engineSwitch = methodBody(source,
                "private void switchEngine(int type, boolean chosen, boolean notifyPrepare, int decode, long position, float speed, boolean repeat, boolean wasPlayWhenReady, String cause)",
                "private void rebuildPlayer()");

        assertTrue("the log must expose whether the user chose this kernel",
                engineSwitch.contains("chosen,"));
        // Automatic fallbacks pass chosen=false so they never look like a user selection.
        assertTrue(source.contains("switchEngine(playerType, false, true, true, PlayerEngine.SOFT, \"fallback-decode:\""));
        assertTrue(source.contains("switchEngine(next, false, true, true, fallbackDecode("));
    }

    @Test
    public void manualDecodeButtonLogsItsCauseToo() throws Exception {
        String source = readPlayerManager();
        // toggleDecode() is the control bar's decode button - the most common manual
        // soft/hard switch on both flavors. switchDecode(...) only covers the source-refresh
        // path, so without a log here the everyday manual switch had no switch-cause row.
        String toggle = methodBody(source, "public void toggleDecode()", "private void maybeFallbackToSoftDecode(");
        assertTrue("the manual decode button must log its cause",
                toggle.contains("logSwitchCause(\"decode\", \"user-choice\","));
        // The previous decode has to be read before engine.setDecode(next) overwrites it.
        int capture = toggle.indexOf("int previousDecode = engine.getDecode();");
        int mutate = toggle.indexOf("engine.setDecode(next);");
        assertTrue("the previous decode must be captured before it is overwritten",
                capture >= 0 && mutate > capture);
    }

    @Test
    public void switchCauseLogIsNotGatedOnSpiderDebug() throws Exception {
        String source = readPlayerManager();
        String logger = methodBody(source,
                "private void logSwitchCause(String kind, String cause, int fromType, int toType,",
                "private void logSwitchCause(String cause, int fromType, int toType,");

        // PlaybackTrace.log already gates on SpiderDebug.isEnabled(); a second gate here would
        // make the cause invisible in exactly the exports users send for triage.
        assertTrue(logger.contains("PlaybackTrace.log(\"switch-cause\""));
        assertFalse(logger.contains("SpiderDebug.isEnabled()"));
    }

    /**
     * 日志里的位置必须在引擎释放之前读出。
     *
     * <p>{@code preparePlayer()} 的切换原因日志位于 {@code engine.release()} 之后；若那里
     * 直接写 {@code getPosition()}，记录的就是已释放播放器的状态，而不是内核被换掉时播放
     * 真正所在的位置——排查「按剧集记住的内核」时这个字段正是关键。
     */
    @Test
    public void switchCausePositionIsReadBeforeTheEngineIsReleased() throws Exception {
        String source = stripComments(readPlayerManager());
        String prepare = methodBody(source,
                "public void preparePlayer(int type, boolean force)",
                "public void switchPlayer(int type, PlaySpec freshSpec");

        int capture = prepare.indexOf("long positionBeforeRelease = Math.max(0, getPosition());");
        int release = prepare.indexOf("engine.release();");
        int log = prepare.indexOf("logSwitchCause(");
        assertTrue("preparePlayer must read the position while the old engine is alive", capture >= 0);
        assertTrue("preparePlayer must release the old engine", release >= 0);
        assertTrue("preparePlayer must log the switch cause", log >= 0);
        assertTrue("the read must precede the release", capture < release);
        assertTrue("the log must use the captured value, not a post-release read",
                prepare.indexOf("true, positionBeforeRelease)") > release && log > release);
    }

    /**
     * Drops whole-line comments and trailing {@code //} comments, so an assertion looking for a
     * call by name cannot be satisfied by that name appearing in the comment documenting it.
     * Verified by mutation: leaving the comment in place kept a broken ordering assertion green.
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
        return read("app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java",
                "src/main/java/com/fongmi/android/tv/player/PlayerManager.java");
    }

    private static String read(String moduleRelative, String rootRelative) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        Path source = root.resolve(moduleRelative);
        if (!Files.exists(source)) source = root.resolve(rootRelative);
        assertTrue("source must exist: " + source, Files.exists(source));
        return Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String methodBody(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        assertTrue("Missing source token: " + startToken, start >= 0);
        int end = source.indexOf(endToken, start);
        assertTrue("Missing source token after " + startToken + ": " + endToken, end > start);
        return source.substring(start, end);
    }
}
