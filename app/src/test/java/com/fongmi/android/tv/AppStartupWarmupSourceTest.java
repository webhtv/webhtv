package com.fongmi.android.tv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 冷启动播放页时，主线程曾花约 0.9s 同步启动 CPython
 * （SubtitleProviderRegistry -> SubtitleSourceManager -> PythonSubtitleScriptRuntime -> Loader），
 * 因为这条链挂在 VideoActivity / TmdbDetailActivity 的构造期。
 * 现在注册表改在 App 启动时由后台线程预热，用户点进播放页时成本已经付完。
 * 这个测试锁住预热的存在、它的后台线程下放、以及 App.onCreate 的主线程顺序没有被破坏。
 */
public class AppStartupWarmupSourceTest {

    @Test
    public void theApplicationWarmsUpSubtitleProvidersInTheBackground() throws Exception {
        String source = readSource();
        String warmup = methodBody(source, "private void warmSubtitleProviders()");
        assertTrue("the registry must still be resolved by the warmup",
                warmup.contains("SubtitleProviderRegistry::get"));
        assertFalse("the warmup must not run the registry bootstrap on the caller thread",
                warmup.contains("SubtitleProviderRegistry.get();"));
        assertTrue("the warmup must be handed to the shared background executor",
                warmup.contains("Task.execute("));
    }

    @Test
    public void onCreateStillCallsTheWarmupBeforeTheDeferredStarters() throws Exception {
        String body = methodBody(readSource(), "public void onCreate()");
        int warmup = body.indexOf("warmSubtitleProviders();");
        int deferred = body.indexOf("post(() -> CacheScheduler.get().start()");
        assertTrue("onCreate must warm the subtitle providers up", warmup >= 0);
        assertTrue("the warmup must not displace the existing deferred cache starter", deferred > warmup);
        assertTrue("the existing startup contract must stay intact",
                body.contains("ThemeController.applyNightModeToApp();"));
        assertTrue("the existing startup contract must stay intact",
                body.contains("resumeBackgroundServices();"));
    }

    private static String readSource() throws Exception {
        Path moduleRelative = Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "App.java");
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
