package com.fongmi.android.tv.server.proxy;

import java.util.Map;

public record ProxyRequestOverrides(Integer rangeConcurrency, Integer shardCount, Long chunkSizeBytes) {

    public static final ProxyRequestOverrides NONE = new ProxyRequestOverrides(null, null, null);

    public static ProxyRequestOverrides parse(Map<String, String> parameters) {
        Integer concurrency = parseCount(parameters, "thread", ProxyRuntimeConfigValidator.MAX_RANGE_CONCURRENCY);
        Integer count = parseCount(parameters, "shards", ProxyRuntimeConfigValidator.MAX_SHARD_COUNT);
        Long chunkBytes = parseChunkSize(parameters);
        if (count != null && chunkBytes != null) {
            throw new IllegalArgumentException("shards and chunkSize are mutually exclusive");
        }
        if (concurrency == null && count == null && chunkBytes == null) return NONE;
        return new ProxyRequestOverrides(concurrency, count, chunkBytes);
    }

    public boolean isEmpty() {
        return rangeConcurrency == null && shardCount == null && chunkSizeBytes == null;
    }

    public ProxyRuntimeConfig applyTo(ProxyRuntimeConfig base) {
        if (isEmpty()) return base;
        ProxyRuntimeConfig.ShardMode mode = shardCount != null ? ProxyRuntimeConfig.ShardMode.COUNT
                : chunkSizeBytes != null ? ProxyRuntimeConfig.ShardMode.SIZE : base.shardMode();
        return new ProxyRuntimeConfig(base.schemaVersion(), base.enabled(), base.portMode(),
                base.configuredPort(), base.serverWorkers(), base.connectionQueueCapacity(),
                base.rangeWorkers(), rangeConcurrency != null ? rangeConcurrency : base.rangeConcurrency(),
                mode, shardCount != null ? shardCount : base.shardCount(),
                chunkSizeBytes != null ? chunkSizeBytes : base.chunkSizeBytes(),
                base.maxSessions(), base.reorderWindowBlocks(), base.bufferBudgetBytes(),
                base.retryCount(), base.connectTimeoutMillis(), base.readTimeoutMillis(),
                base.fileThresholdBytes());
    }

    private static Integer parseCount(Map<String, String> parameters, String key, int maximum) {
        if (!parameters.containsKey(key)) return null;
        long value = parsePositive(parameters.get(key), key);
        if (value > maximum) throw new IllegalArgumentException(key + " exceeds " + maximum);
        return (int) value;
    }

    private static Long parseChunkSize(Map<String, String> parameters) {
        if (!parameters.containsKey("chunkSize")) return null;
        long kibibytes = parsePositive(parameters.get("chunkSize"), "chunkSize");
        long bytes;
        try {
            bytes = Math.multiplyExact(kibibytes, 1024L);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("chunkSize overflows bytes", e);
        }
        if (bytes > ProxyRuntimeConfigValidator.MAX_CHUNK_SIZE_BYTES) {
            throw new IllegalArgumentException("chunkSize exceeds 64 MiB");
        }
        return bytes;
    }

    private static long parsePositive(String input, String key) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException(key + " must not be blank");
        String value = input.trim();
        if (!value.matches("[+-]?[0-9]+")) throw new IllegalArgumentException(key + " must be an integer");
        long number;
        try {
            number = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " is out of range", e);
        }
        if (number <= 0) throw new IllegalArgumentException(key + " must be positive");
        return number;
    }
}
