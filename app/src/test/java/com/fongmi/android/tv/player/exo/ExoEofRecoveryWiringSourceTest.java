package com.fongmi.android.tv.player.exo;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the wiring that decides which HTTP EOF recoveries are published as foreground.
 *
 * <p>This is a source guard rather than a behavioral test because building the real data source
 * chain needs {@code App.get()}, which is unavailable in a plain JVM test. The contract is small and
 * structural, so reading it is both sufficient and stable.
 *
 * <p>Both directions are load-bearing. If the preload path were marked foreground, a preload-side
 * EOF would publish the signal that makes preload yield — preload would stall itself and never
 * restart. If the foreground path were not marked, the yield would never fire at all and the change
 * would be inert.
 */
public class ExoEofRecoveryWiringSourceTest {

    @Test
    public void preloadPathIsMarkedNonForeground() throws Exception {
        String factory = read("MediaSourceFactory.java");
        String preloadFactory = methodBody(
                factory,
                "static DataSource.Factory createUpstreamDataSourceFactory(",
                "static synchronized Cache getCache()");

        assertTrue("preload must build its recovery source explicitly",
                preloadFactory.contains("new HttpEofRecoveryDataSource.Factory(upstream, false)"));
    }

    @Test
    public void foregroundPathIsMarkedForeground() throws Exception {
        String factory = read("MediaSourceFactory.java");
        String cacheDataSource = methodBody(
                factory,
                "private CacheDataSource.Factory getCacheDataSource(\n            DataSource.Factory upstreamFactory, Map<String, String> headers)",
                "private OkHttpDataSource.Factory getHttpDataSourceFactory()");

        assertTrue("the cache-backed foreground path must publish recoveries",
                cacheDataSource.contains("new HttpEofRecoveryDataSource.Factory(upstreamFactory, true)"));
    }

    @Test
    public void preloadNeverReachesTheForegroundDataSourceFactory() throws Exception {
        // PreCache must build its chain only through the preload factory. If it ever reached
        // getDataSourceFactory(headers) it would silently publish preload recoveries as foreground.
        String preCache = read("PreCache.java");

        assertTrue("PreCache is expected to use the preload upstream factory",
                preCache.contains("MediaSourceFactory.createUpstreamDataSourceFactory("));
        assertFalse("PreCache must not build a foreground media source",
                preCache.contains("MediaSourceFactory.createMediaSource("));
    }

    private static String methodBody(String source, String signature, String nextSignature) {
        int start = source.indexOf(signature);
        assertTrue("missing signature: " + signature, start >= 0);
        int end = source.indexOf(nextSignature, start);
        assertTrue("missing following signature: " + nextSignature, end > start);
        return source.substring(start, end);
    }

    private static String read(String fileName) throws Exception {
        // Gradle may run from the repository root or the module directory; try both, and normalize
        // line endings so the guards below are not defeated by CRLF.
        Path root = Path.of("").toAbsolutePath();
        Path relative = Path.of("app", "src", "main", "java", "com", "fongmi", "android", "tv",
                "player", "exo", fileName);
        Path source = root.resolve(relative);
        if (!Files.exists(source)) source = root.resolve(Path.of("src", "main", "java", "com",
                "fongmi", "android", "tv", "player", "exo", fileName));
        assertTrue("missing source file: " + source, Files.exists(source));
        return Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
