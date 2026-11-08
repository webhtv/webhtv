package com.fongmi.android.tv.subtitle.provider;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 字幕提供方注册表的首次初始化会同步启动 CPython 并导入 webhtv_logging / app 模块
 * （设备实测约 0.9s，debuggerd 抓栈显示主线程正停在 libpython3.10.so）。
 * 这份成本过去由 SubtitleMatchService 的默认构造器 eager 触发，而该构造器处在
 * VideoActivity / TmdbDetailActivity -> SubtitlePlaybackSession -> DefaultController
 * 的字段初始化链上，于是每次冷启动播放页都要在首帧窗口里付掉它。
 * 这些测试锁住「注册表延迟到后台线程解析」这条约束，同时保证真正的工作没被删掉。
 */
public class SubtitleProviderRegistryLazyInitTest {

    @Test
    public void theDefaultConstructorNoLongerResolvesTheRegistryEagerly() throws Exception {
        String body = methodBody(readSource(), "public SubtitleMatchService()");
        assertFalse("the default constructor must not touch the registry singleton",
                body.contains("SubtitleProviderRegistry.get()"));
        assertTrue("the registry must start unresolved and be injected later",
                body.contains("null,"));
    }

    @Test
    public void everyProviderUseGoesThroughTheLazyAccessor() throws Exception {
        String source = readSource();
        String accessor = methodBody(source, "private SubtitleProviderRegistry registry()");
        assertTrue("the lazy accessor must resolve the singleton", accessor.contains("SubtitleProviderRegistry.get()"));
        assertTrue("the lazy accessor must double check under a lock", accessor.contains("synchronized (this)"));
        assertTrue("the cached field must be safe to publish across threads",
                source.contains("private volatile SubtitleProviderRegistry registry;"));
        String withoutAccessorCalls = source.replace("registry()", "");
        assertFalse("no call site may read the raw field any more",
                withoutAccessorCalls.contains("registry."));
    }

    @Test
    public void providerAvailabilityChecksMovedIntoTheBackgroundTasks() throws Exception {
        String source = readSource();
        String autoMatch = methodBody(source, "public void autoMatch(SubtitleRequest request, Listener listener)");
        assertFalse("autoMatch runs on the scheduler thread, so it must not probe the registry",
                autoMatch.contains("enabledProviders()"));
        assertTrue("the probe must move into the submitted background task",
                methodBody(source, "private void runAutoMatch(").contains("registry().enabledProviders()"));
        String manualSearch = methodBody(source, "public void manualSearch(SubtitleRequest request, String keyword, Listener listener)");
        int submit = manualSearch.indexOf("Task.searchExecutor().submit(");
        int probe = manualSearch.indexOf("registry().enabledProviders()");
        assertTrue("manualSearch must submit its task before probing providers", submit >= 0 && probe > submit);
    }

    @Test
    public void theInjectedConstructorStillAcceptsAnExplicitRegistry() throws Exception {
        String body = methodBody(readSource(), "SubtitleMatchService(SubtitleContextBuilder contextBuilder");
        assertTrue("tests and callers must still be able to inject a registry", body.contains("this.registry = registry;"));
    }

    private static String readSource() throws Exception {
        Path moduleRelative = Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "subtitle", "SubtitleMatchService.java");
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
