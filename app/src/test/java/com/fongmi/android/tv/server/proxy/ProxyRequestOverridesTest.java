package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProxyRequestOverridesTest {

    @Test
    public void missingParametersReturnNoneAndPreserveBaseIdentity() {
        ProxyRequestOverrides overrides = ProxyRequestOverrides.parse(Map.of());
        ProxyRuntimeConfig base = base(ProxyRuntimeConfig.ShardMode.AUTO);

        assertSame(ProxyRequestOverrides.NONE, overrides);
        assertTrue(overrides.isEmpty());
        assertSame(base, overrides.applyTo(base));
        assertSame(base, new ProxyRequestOverrides(null, null, null).applyTo(base));
    }

    @Test
    public void explicitBlankAndNullValuesAreRejected() {
        for (String parameter : new String[]{"thread", "shards", "chunkSize"}) {
            for (String value : new String[]{"", " \t ", "\r\n"}) {
                assertInvalid(Map.of(parameter, value), parameter + " must not be blank");
            }
            Map<String, String> parameters = new HashMap<>();
            parameters.put(parameter, null);
            assertInvalid(parameters, parameter + " must not be blank");
        }
    }

    @Test
    public void unrelatedParametersAreIgnored() {
        assertSame(ProxyRequestOverrides.NONE,
                ProxyRequestOverrides.parse(Map.of("url", "https://example.com/video")));
    }

    @Test
    public void threadIsTrimmedAndOnlyChangesConcurrency() {
        ProxyRequestOverrides overrides = ProxyRequestOverrides.parse(Map.of("thread", " 7 "));
        ProxyRuntimeConfig base = base(ProxyRuntimeConfig.ShardMode.SIZE);
        ProxyRuntimeConfig result = overrides.applyTo(base);

        assertEquals(new ProxyRequestOverrides(7, null, null), overrides);
        assertFalse(overrides.isEmpty());
        assertNotSame(base, result);
        assertEquals(expected(base, 7, ProxyRuntimeConfig.ShardMode.SIZE, 73, 4096L), result);
    }

    @Test
    public void shardsSelectCountModeAndPreserveOtherFields() {
        ProxyRequestOverrides overrides = ProxyRequestOverrides.parse(Map.of("shards", " 12 "));
        ProxyRuntimeConfig base = base(ProxyRuntimeConfig.ShardMode.SIZE);

        assertEquals(new ProxyRequestOverrides(null, 12, null), overrides);
        assertFalse(overrides.isEmpty());
        assertEquals(expected(base, 5, ProxyRuntimeConfig.ShardMode.COUNT, 12, 4096L),
                overrides.applyTo(base));
        assertEquals(ProxyRuntimeConfig.ShardMode.SIZE, base.shardMode());
    }

    @Test
    public void chunkSizeUsesKibibytesAndSelectsSizeMode() {
        ProxyRequestOverrides overrides = ProxyRequestOverrides.parse(Map.of("chunkSize", " 3 "));
        ProxyRuntimeConfig base = base(ProxyRuntimeConfig.ShardMode.COUNT);

        assertEquals(new ProxyRequestOverrides(null, null, 3072L), overrides);
        assertFalse(overrides.isEmpty());
        assertEquals(expected(base, 5, ProxyRuntimeConfig.ShardMode.SIZE, 73, 3072L),
                overrides.applyTo(base));
        assertEquals(ProxyRuntimeConfig.ShardMode.COUNT, base.shardMode());
    }

    @Test
    public void threadCanCombineWithEitherShardOverride() {
        ProxyRuntimeConfig base = base(ProxyRuntimeConfig.ShardMode.AUTO);

        assertEquals(expected(base, 9, ProxyRuntimeConfig.ShardMode.COUNT, 24, 4096L),
                ProxyRequestOverrides.parse(Map.of("thread", "9", "shards", "24")).applyTo(base));
        assertEquals(expected(base, 9, ProxyRuntimeConfig.ShardMode.SIZE, 73, 8192L),
                ProxyRequestOverrides.parse(Map.of("thread", "9", "chunkSize", "8")).applyTo(base));
    }

    @Test
    public void shardsAndChunkSizeAreMutuallyExclusive() {
        assertInvalid(Map.of("shards", "12", "chunkSize", "3"),
                "shards and chunkSize are mutually exclusive");
    }

    @Test
    public void blankShardParameterIsRejectedEvenWithAnotherOverride() {
        assertInvalid(Map.of("shards", "12", "chunkSize", " "),
                "chunkSize must not be blank");
        assertInvalid(Map.of("shards", " ", "chunkSize", "3"),
                "shards must not be blank");
    }

    @Test
    public void positiveBoundariesAreAccepted() {
        assertEquals(new ProxyRequestOverrides(1, 1, null),
                ProxyRequestOverrides.parse(Map.of("thread", "1", "shards", "1")));
        assertEquals(new ProxyRequestOverrides(128, 65536, null),
                ProxyRequestOverrides.parse(Map.of("thread", "128", "shards", "65536")));
        assertEquals(Long.valueOf(1024L),
                ProxyRequestOverrides.parse(Map.of("chunkSize", "1")).chunkSizeBytes());
        assertEquals(Long.valueOf(67108864L),
                ProxyRequestOverrides.parse(Map.of("chunkSize", "65536")).chunkSizeBytes());
    }

    @Test
    public void threadRejectsZeroNegativeAndExcessiveValues() {
        assertInvalid(Map.of("thread", "0"), "thread must be positive");
        assertInvalid(Map.of("thread", "-1"), "thread must be positive");
        assertInvalid(Map.of("thread", "129"), "thread exceeds 128");
    }

    @Test
    public void shardsRejectZeroNegativeAndExcessiveValues() {
        assertInvalid(Map.of("shards", "0"), "shards must be positive");
        assertInvalid(Map.of("shards", "-1"), "shards must be positive");
        assertInvalid(Map.of("shards", "65537"), "shards exceeds 65536");
    }

    @Test
    public void chunkSizeRejectsZeroNegativeAndExcessiveValues() {
        assertInvalid(Map.of("chunkSize", "0"), "chunkSize must be positive");
        assertInvalid(Map.of("chunkSize", "-1"), "chunkSize must be positive");
        assertInvalid(Map.of("chunkSize", "65537"), "chunkSize exceeds 64 MiB");
        assertInvalid(Map.of("chunkSize", "99999999999999"), "chunkSize exceeds 64 MiB");
    }

    @Test
    public void chunkSizeRejectsLongParsingAndMultiplicationOverflow() {
        assertInvalid(Map.of("chunkSize", "9223372036854775808"), "chunkSize is out of range");
        assertInvalid(Map.of("chunkSize", "9223372036854775807"), "chunkSize overflows bytes");
        assertInvalid(Map.of("chunkSize", "9007199254740992"), "chunkSize overflows bytes");
    }

    @Test
    public void countsRejectLongOverflow() {
        assertInvalid(Map.of("thread", "9223372036854775808"), "thread is out of range");
        assertInvalid(Map.of("shards", "9223372036854775808"), "shards is out of range");
    }

    @Test
    public void nonIntegersAndCombinedValuesAreRejected() {
        for (String parameter : new String[]{"thread", "shards", "chunkSize"}) {
            for (String value : new String[]{"abc", "1.5", "1e2", "1 2", "1,2", "1&2"}) {
                assertInvalid(Map.of(parameter, value), parameter + " must be an integer");
            }
        }
    }

    private static void assertInvalid(Map<String, String> parameters, String message) {
        try {
            ProxyRequestOverrides.parse(parameters);
            fail("Expected invalid parameters: " + parameters);
        } catch (IllegalArgumentException e) {
            assertEquals(message, e.getMessage());
        }
    }

    private static ProxyRuntimeConfig base(ProxyRuntimeConfig.ShardMode shardMode) {
        return new ProxyRuntimeConfig(1, true, ProxyRuntimeConfig.PortMode.AUTO, 0,
                11, 29, 17, 5, shardMode, 73, 4096L, 3, 13, 8388608L,
                4, 1234L, 5678L, 987654L);
    }

    private static ProxyRuntimeConfig expected(ProxyRuntimeConfig base, int concurrency,
                                              ProxyRuntimeConfig.ShardMode shardMode,
                                              int shardCount, long chunkSizeBytes) {
        return new ProxyRuntimeConfig(base.schemaVersion(), base.enabled(), base.portMode(),
                base.configuredPort(), base.serverWorkers(), base.connectionQueueCapacity(),
                base.rangeWorkers(), concurrency, shardMode, shardCount, chunkSizeBytes,
                base.maxSessions(), base.reorderWindowBlocks(), base.bufferBudgetBytes(),
                base.retryCount(), base.connectTimeoutMillis(), base.readTimeoutMillis(),
                base.fileThresholdBytes());
    }
}
