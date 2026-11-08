package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code ExoUtil.buildAudioSink} 里的音频能力日志原本每次都会调
 * {@code AudioCapabilities.getCapabilities(...)}，那条路径会在主线程上同步创建并释放
 * AudioTrack。设备实测这一行日志独占 354-757ms，正是进入播放页时加载圈停住的窗口。
 * 这些测试锁住「日志不再触发硬件探测」以及「offload 策略仍按原顺序装配」。
 */
public class ExoUtilAudioCapabilityLogTest {

    @Test
    public void theDiagnosticLogNoLongerProbesAudioHardware() throws Exception {
        String body = stripComments(methodBody(readSource(), "private static AudioSink buildAudioSink("));
        assertFalse("buildAudioSink must not call AudioCapabilities.getCapabilities on the main thread",
                body.contains("AudioCapabilities.getCapabilities("));
        assertTrue("the audio diagnostic log itself must survive", body.contains("SpiderDebug.log(\"exo-audio\""));
        assertTrue("the log must stay behind the enabled check", body.contains("SpiderDebug.isEnabled()"));
    }

    @Test
    public void theOffloadPolicyIsStillWiredBeforeTheSinkIsBuilt() throws Exception {
        String body = methodBody(readSource(), "private static AudioSink buildAudioSink(");
        assertTrue("the passthrough policy must still be configured from the setting",
                body.contains("directPolicy.setAudioPassthroughEnabled(passthrough);"));
        assertTrue("the audio sink must still be built with the offload support provider",
                body.contains("AudioTrackAudioOutputProvider.Builder"));
        int policy = body.indexOf("directPolicy.setAudioPassthroughEnabled(passthrough);");
        int builder = body.indexOf("AudioTrackAudioOutputProvider.Builder");
        assertTrue("the policy must be configured before the sink is built", policy >= 0 && builder > policy);
    }

    @Test
    public void theUnusedCapabilityImportIsGone() throws Exception {
        String source = readSource();
        assertFalse("an unused AudioCapabilities import must not come back",
                source.contains("import androidx.media3.exoplayer.audio.AudioCapabilities;"));
    }

    private static String stripComments(String body) {
        // 断言针对真实代码，不能被解释性注释命中（注释里会提到被删除的调用）。
        StringBuilder out = new StringBuilder(body.length());
        for (String line : body.split("\\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) continue;
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String readSource() throws Exception {
        Path moduleRelative = Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "player", "exo", "ExoUtil.java");
        Path path = Files.exists(moduleRelative) ? moduleRelative : Path.of("app").resolve(moduleRelative);
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("source is missing " + signature, start >= 0);
        int open = source.indexOf('{', start + signature.length());
        assertTrue("source is missing the body of " + signature, open > start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("unclosed body for " + signature);
    }
}
