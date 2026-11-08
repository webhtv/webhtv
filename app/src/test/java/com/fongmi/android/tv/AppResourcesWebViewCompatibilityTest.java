package com.fongmi.android.tv;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppResourcesWebViewCompatibilityTest {

    @Test
    public void applicationResourcesKeepTheAssetManagerUsedByWebView() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "App.java")));

        assertTrue("Application resources should start from the framework-owned Resources instance",
                source.contains("Resources resources = super.getResources();"));
        assertTrue("Application language changes should update that Resources instance in place",
                source.contains("resources.updateConfiguration("));
        assertTrue("Application language should still derive its locale from the existing language wrapper",
                source.contains("Setting.wrapLanguage(getBaseContext()).getResources().getConfiguration()"));
        assertFalse("Application must not replace Resources with a configuration context",
                source.contains("resources = Setting.wrapLanguage(getBaseContext()).getResources();"));
    }

    @Test
    public void applicationResourcesUpdateIsSerializedAndFailSafe() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "App.java")));

        assertTrue("Application resources lookups are reachable from background threads, so the language update must run under a lock",
                source.contains("synchronized (resourcesLock) {"));
        assertTrue("A failed language update must not propagate out of getResources into every resource consumer",
                source.contains("} catch (RuntimeException e) {"));
        assertTrue("Application must still return the framework-owned Resources when the language update fails",
                source.contains("return resources;"));
        assertTrue("A failed language update must not be cached as applied, otherwise the degradation becomes permanent until the next language switch",
                source.contains("resourcesLanguage = Integer.MIN_VALUE;"));
        assertTrue("A failing language update must be retried, not cached forever as a silent degrade",
                source.contains("private boolean shouldRetryResourcesApply(int language) {"));
        assertTrue("Retries must be bounded so the getResources hot path cannot recompute a failing configuration on every call",
                source.contains("MAX_RESOURCES_APPLY_ATTEMPTS"));
        int invalidate = source.indexOf("public void invalidateResources() {");
        assertTrue("Resource cache invalidation must remain the single place that drops the cache", invalidate > 0);
        assertTrue("Resource cache invalidation must take the same lock as the update path",
                source.indexOf("synchronized (resourcesLock) {", invalidate) > invalidate);
        assertTrue("Explicit invalidation must also clear the failure budget, otherwise a language that once failed can never recover",
                source.indexOf("resourcesFailures = 0;", invalidate) > invalidate);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
