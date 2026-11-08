package com.fongmi.android.tv.player.exo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Bounded, versioned and expiring persistent EXO video decoder runtime profiles. */
final class ExoDecoderRuntimeProfileStore {

    static final int STORE_VERSION = 1;
    static final int ENTRY_VERSION = 1;
    static final int BLACKLIST_THRESHOLD = 2;
    static final long ENTRY_TTL_MS = TimeUnit.DAYS.toMillis(30);
    static final long MAX_FUTURE_SKEW_MS = TimeUnit.MINUTES.toMillis(5);
    static final int MAX_ENTRIES = 64;

    private static final String HEADER = "store-version=" + STORE_VERSION;

    private final Backend backend;
    private final Map<ExoDecoderRuntimeKey.Key, Entry> entries;
    private boolean loaded;

    ExoDecoderRuntimeProfileStore(Backend backend) {
        this.backend = backend;
        this.entries = new LinkedHashMap<>();
    }

    synchronized Lookup lookup(ExoDecoderRuntimeKey.Key key, long nowEpochMs) {
        if (key == null || !key.valid() || nowEpochMs <= 0) return Lookup.miss();
        ensureLoaded(nowEpochMs);
        if (prune(nowEpochMs)) save();
        Entry entry = entries.get(key);
        return entry == null ? Lookup.miss() : new Lookup(true, entry);
    }

    synchronized RecordResult recordFailure(
            ExoDecoderRuntimeKey.Key key,
            FailureKind failureKind,
            Metrics metrics,
            long nowEpochMs) {
        if (!validInput(key, nowEpochMs) || failureKind == null || failureKind == FailureKind.NONE) {
            return RecordResult.skipped();
        }
        metrics = metrics == null ? Metrics.EMPTY : metrics;
        ensureLoaded(nowEpochMs);
        prune(nowEpochMs);
        Entry previous = entries.get(key);
        Entry updated = new Entry(
                ENTRY_VERSION,
                key,
                previous == null ? 0 : previous.firstFrameCount(),
                previous == null ? 0 : previous.successCount(),
                saturatingIncrement(previous == null ? 0 : previous.failureCount()),
                saturatingIncrement(previous == null ? 0 : previous.consecutiveFailures()),
                saturatingAdd(previous == null ? 0 : previous.droppedFrames(), metrics.droppedFrames()),
                saturatingAdd(previous == null ? 0 : previous.observedDurationMs(), metrics.observedDurationMs()),
                saturatingAdd(previous == null ? 0 : previous.recoverableCodecErrors(), metrics.recoverableCodecErrors()),
                previous == null ? 0 : previous.fallbackSuccessCount(),
                previous == null ? 0 : previous.fallbackFailureCount(),
                failureKind,
                nowEpochMs);
        entries.put(key, updated);
        trimToCapacity();
        save();
        return new RecordResult(true, updated);
    }

    synchronized RecordResult recordStableSuccess(
            ExoDecoderRuntimeKey.Key key,
            Metrics metrics,
            long nowEpochMs) {
        if (!validInput(key, nowEpochMs)) return RecordResult.skipped();
        metrics = metrics == null ? Metrics.EMPTY : metrics;
        ensureLoaded(nowEpochMs);
        prune(nowEpochMs);
        Entry previous = entries.get(key);
        Entry updated = new Entry(
                ENTRY_VERSION,
                key,
                previous == null ? 0 : previous.firstFrameCount(),
                saturatingIncrement(previous == null ? 0 : previous.successCount()),
                previous == null ? 0 : previous.failureCount(),
                0,
                saturatingAdd(previous == null ? 0 : previous.droppedFrames(), metrics.droppedFrames()),
                saturatingAdd(previous == null ? 0 : previous.observedDurationMs(), metrics.observedDurationMs()),
                saturatingAdd(previous == null ? 0 : previous.recoverableCodecErrors(), metrics.recoverableCodecErrors()),
                previous == null ? 0 : previous.fallbackSuccessCount(),
                previous == null ? 0 : previous.fallbackFailureCount(),
                previous == null ? FailureKind.NONE : previous.lastFailureKind(),
                nowEpochMs);
        entries.put(key, updated);
        trimToCapacity();
        save();
        return new RecordResult(true, updated);
    }

    synchronized RecordResult recordFirstFrame(
            ExoDecoderRuntimeKey.Key key,
            long nowEpochMs) {
        if (!validInput(key, nowEpochMs)) return RecordResult.skipped();
        ensureLoaded(nowEpochMs);
        prune(nowEpochMs);
        Entry previous = entries.get(key);
        Entry updated = new Entry(
                ENTRY_VERSION,
                key,
                saturatingIncrement(previous == null ? 0 : previous.firstFrameCount()),
                previous == null ? 0 : previous.successCount(),
                previous == null ? 0 : previous.failureCount(),
                previous == null ? 0 : previous.consecutiveFailures(),
                previous == null ? 0 : previous.droppedFrames(),
                previous == null ? 0 : previous.observedDurationMs(),
                previous == null ? 0 : previous.recoverableCodecErrors(),
                previous == null ? 0 : previous.fallbackSuccessCount(),
                previous == null ? 0 : previous.fallbackFailureCount(),
                previous == null ? FailureKind.NONE : previous.lastFailureKind(),
                nowEpochMs);
        entries.put(key, updated);
        trimToCapacity();
        save();
        return new RecordResult(true, updated);
    }

    synchronized RecordResult recordObservation(
            ExoDecoderRuntimeKey.Key key,
            Metrics metrics,
            long nowEpochMs) {
        metrics = metrics == null ? Metrics.EMPTY : metrics;
        if (!validInput(key, nowEpochMs) || metrics.empty()) return RecordResult.skipped();
        ensureLoaded(nowEpochMs);
        prune(nowEpochMs);
        Entry previous = entries.get(key);
        Entry updated = new Entry(
                ENTRY_VERSION,
                key,
                previous == null ? 0 : previous.firstFrameCount(),
                previous == null ? 0 : previous.successCount(),
                previous == null ? 0 : previous.failureCount(),
                previous == null ? 0 : previous.consecutiveFailures(),
                saturatingAdd(previous == null ? 0 : previous.droppedFrames(), metrics.droppedFrames()),
                saturatingAdd(previous == null ? 0 : previous.observedDurationMs(), metrics.observedDurationMs()),
                saturatingAdd(previous == null ? 0 : previous.recoverableCodecErrors(), metrics.recoverableCodecErrors()),
                previous == null ? 0 : previous.fallbackSuccessCount(),
                previous == null ? 0 : previous.fallbackFailureCount(),
                previous == null ? FailureKind.NONE : previous.lastFailureKind(),
                nowEpochMs);
        entries.put(key, updated);
        trimToCapacity();
        save();
        return new RecordResult(true, updated);
    }

    synchronized RecordResult recordFallback(
            ExoDecoderRuntimeKey.Key key,
            FallbackResult result,
            long nowEpochMs) {
        if (!validInput(key, nowEpochMs) || result == null || result == FallbackResult.NONE) {
            return RecordResult.skipped();
        }
        ensureLoaded(nowEpochMs);
        prune(nowEpochMs);
        Entry previous = entries.get(key);
        Entry updated = new Entry(
                ENTRY_VERSION,
                key,
                previous == null ? 0 : previous.firstFrameCount(),
                previous == null ? 0 : previous.successCount(),
                previous == null ? 0 : previous.failureCount(),
                previous == null ? 0 : previous.consecutiveFailures(),
                previous == null ? 0 : previous.droppedFrames(),
                previous == null ? 0 : previous.observedDurationMs(),
                previous == null ? 0 : previous.recoverableCodecErrors(),
                result == FallbackResult.SUCCESS
                        ? saturatingIncrement(previous == null ? 0 : previous.fallbackSuccessCount())
                        : previous == null ? 0 : previous.fallbackSuccessCount(),
                result == FallbackResult.FAILURE
                        ? saturatingIncrement(previous == null ? 0 : previous.fallbackFailureCount())
                        : previous == null ? 0 : previous.fallbackFailureCount(),
                previous == null ? FailureKind.NONE : previous.lastFailureKind(),
                nowEpochMs);
        entries.put(key, updated);
        trimToCapacity();
        save();
        return new RecordResult(true, updated);
    }

    synchronized int entryCount(long nowEpochMs) {
        ensureLoaded(nowEpochMs);
        if (prune(nowEpochMs)) save();
        return entries.size();
    }

    /**
     * Current persistent decoder exclusions, newest first. Used to make the silent
     * hardware-decoder blacklist visible to the user and to diagnostics. It does not change the
     * exclusion state, but it may persist expiry pruning of stale entries.
     */
    synchronized List<Entry> activeBlacklist(long nowEpochMs) {
        ensureLoaded(nowEpochMs);
        if (prune(nowEpochMs)) save();
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.blacklisted()) result.add(entry);
        }
        result.sort(Comparator.comparingLong(Entry::updatedAtEpochMs).reversed());
        return result;
    }

    synchronized void clear() {
        loaded = true;
        entries.clear();
        try {
            backend.clear();
        } catch (Throwable ignored) {
        }
    }

    private boolean validInput(ExoDecoderRuntimeKey.Key key, long nowEpochMs) {
        return key != null && key.valid() && nowEpochMs > 0;
    }

    private void ensureLoaded(long nowEpochMs) {
        if (loaded) return;
        loaded = true;
        String raw;
        try {
            raw = backend.read();
        } catch (Throwable ignored) {
            raw = "";
        }
        DecodeResult decoded = decode(raw);
        if (!decoded.validStore()) {
            entries.clear();
            try {
                backend.clear();
            } catch (Throwable ignored) {
            }
            return;
        }
        entries.putAll(decoded.entries());
        if (decoded.rewriteRequired() || prune(nowEpochMs)) save();
    }

    private boolean prune(long nowEpochMs) {
        if (nowEpochMs <= 0) return false;
        boolean changed = false;
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (!entry.valid()
                    || entry.updatedAtEpochMs() > saturatingAdd(nowEpochMs, MAX_FUTURE_SKEW_MS)
                    || nowEpochMs >= entry.updatedAtEpochMs()
                    && nowEpochMs - entry.updatedAtEpochMs() >= ENTRY_TTL_MS) {
                iterator.remove();
                changed = true;
            }
        }
        return trimToCapacity() || changed;
    }

    private boolean trimToCapacity() {
        boolean changed = false;
        while (entries.size() > MAX_ENTRIES) {
            ExoDecoderRuntimeKey.Key victim = null;
            Entry victimEntry = null;
            for (Entry entry : entries.values()) {
                if (victimEntry == null
                        || victimEntry.blacklisted() && !entry.blacklisted()
                        || victimEntry.blacklisted() == entry.blacklisted()
                        && entry.updatedAtEpochMs() < victimEntry.updatedAtEpochMs()) {
                    victim = entry.key();
                    victimEntry = entry;
                }
            }
            if (victim == null) break;
            entries.remove(victim);
            changed = true;
        }
        return changed;
    }

    private void save() {
        try {
            if (entries.isEmpty()) backend.clear();
            else backend.write(encode(entries.values()));
        } catch (Throwable ignored) {
        }
    }

    private static DecodeResult decode(String raw) {
        if (raw == null || raw.isBlank()) return new DecodeResult(true, false, Map.of());
        String[] lines = raw.split("\\r?\\n");
        if (lines.length == 0 || !HEADER.equals(lines[0].trim())) {
            return new DecodeResult(false, false, Map.of());
        }
        Map<ExoDecoderRuntimeKey.Key, Entry> decoded = new LinkedHashMap<>();
        boolean rewriteRequired = false;
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) continue;
            Entry entry = decodeEntry(line);
            if (entry == null) {
                rewriteRequired = true;
                continue;
            }
            Entry previous = decoded.get(entry.key());
            if (previous == null || entry.updatedAtEpochMs() > previous.updatedAtEpochMs()) {
                decoded.put(entry.key(), entry);
            }
            if (previous != null) rewriteRequired = true;
        }
        return new DecodeResult(true, rewriteRequired, decoded);
    }

    private static Entry decodeEntry(String line) {
        try {
            String[] values = line.split("\\|", -1);
            if (values.length != 28) return null;
            ExoDecoderRuntimeKey.Key key = new ExoDecoderRuntimeKey.Key(
                    values[1],
                    values[2],
                    values[3],
                    values[4],
                    Integer.parseInt(values[5]),
                    Integer.parseInt(values[6]),
                    Integer.parseInt(values[7]),
                    Integer.parseInt(values[8]),
                    Integer.parseInt(values[9]),
                    Integer.parseInt(values[10]),
                    Integer.parseInt(values[11]),
                    Integer.parseInt(values[12]),
                    parseBoolean(values[13]),
                    parseBoolean(values[14]),
                    ExoDecoderRuntimeSession.OutputTarget.valueOf(values[15]),
                    parseBoolean(values[16]));
            Entry entry = new Entry(
                    Integer.parseInt(values[0]),
                    key,
                    Integer.parseInt(values[17]),
                    Integer.parseInt(values[18]),
                    Integer.parseInt(values[19]),
                    Integer.parseInt(values[20]),
                    Long.parseLong(values[21]),
                    Long.parseLong(values[22]),
                    Integer.parseInt(values[23]),
                    Integer.parseInt(values[24]),
                    Integer.parseInt(values[25]),
                    FailureKind.valueOf(values[26]),
                    Long.parseLong(values[27]));
            return entry.valid() ? entry : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean parseBoolean(String value) {
        if ("1".equals(value)) return true;
        if ("0".equals(value)) return false;
        throw new IllegalArgumentException("invalid boolean");
    }

    private static String encode(Iterable<Entry> values) {
        List<Entry> sorted = new ArrayList<>();
        values.forEach(sorted::add);
        sorted.sort(Comparator
                .comparing(Entry::blacklisted).reversed()
                .thenComparing(Entry::updatedAtEpochMs, Comparator.reverseOrder())
                .thenComparing(entry -> ExoDecoderRuntimeKey.shortId(entry.key())));
        StringBuilder result = new StringBuilder(HEADER);
        for (Entry entry : sorted) {
            ExoDecoderRuntimeKey.Key key = entry.key();
            result.append('\n')
                    .append(entry.version()).append('|')
                    .append(key.environmentDigest()).append('|')
                    .append(key.decoderDigest()).append('|')
                    .append(key.mimeType()).append('|')
                    .append(key.codecDigest()).append('|')
                    .append(key.profile()).append('|')
                    .append(key.level()).append('|')
                    .append(key.width()).append('|')
                    .append(key.height()).append('|')
                    .append(key.frameRateMilli()).append('|')
                    .append(key.colorSpace()).append('|')
                    .append(key.colorRange()).append('|')
                    .append(key.colorTransfer()).append('|')
                    .append(key.hdrStaticInfo() ? 1 : 0).append('|')
                    .append(key.secure() ? 1 : 0).append('|')
                    .append(key.outputTarget().name()).append('|')
                    .append(key.tunneling() ? 1 : 0).append('|')
                    .append(entry.firstFrameCount()).append('|')
                    .append(entry.successCount()).append('|')
                    .append(entry.failureCount()).append('|')
                    .append(entry.consecutiveFailures()).append('|')
                    .append(entry.droppedFrames()).append('|')
                    .append(entry.observedDurationMs()).append('|')
                    .append(entry.recoverableCodecErrors()).append('|')
                    .append(entry.fallbackSuccessCount()).append('|')
                    .append(entry.fallbackFailureCount()).append('|')
                    .append(entry.lastFailureKind().name()).append('|')
                    .append(entry.updatedAtEpochMs());
        }
        return result.toString();
    }

    private static int saturatingIncrement(int value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, value) + 1;
    }

    private static int saturatingAdd(int first, int second) {
        if (second <= 0) return Math.max(0, first);
        return first > Integer.MAX_VALUE - second ? Integer.MAX_VALUE : Math.max(0, first) + second;
    }

    private static long saturatingAdd(long first, long second) {
        if (second <= 0) return Math.max(0, first);
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : Math.max(0, first) + second;
    }

    interface Backend {

        String read();

        void write(String value);

        void clear();
    }

    enum FailureKind {
        NONE,
        INIT,
        QUERY,
        DECODE
    }

    enum FallbackResult {
        NONE,
        SUCCESS,
        FAILURE
    }

    record Metrics(long droppedFrames, long observedDurationMs, int recoverableCodecErrors) {

        static final Metrics EMPTY = new Metrics(0, 0, 0);

        Metrics {
            droppedFrames = Math.max(0, droppedFrames);
            observedDurationMs = Math.max(0, observedDurationMs);
            recoverableCodecErrors = Math.max(0, recoverableCodecErrors);
        }

        boolean empty() {
            return droppedFrames == 0 && recoverableCodecErrors == 0;
        }
    }

    record Lookup(boolean hit, Entry entry) {

        static Lookup miss() {
            return new Lookup(false, null);
        }

        boolean blacklisted() {
            return hit && entry != null && entry.blacklisted();
        }
    }

    record RecordResult(boolean recorded, Entry entry) {

        static RecordResult skipped() {
            return new RecordResult(false, null);
        }
    }

    record Entry(
            int version,
            ExoDecoderRuntimeKey.Key key,
            int firstFrameCount,
            int successCount,
            int failureCount,
            int consecutiveFailures,
            long droppedFrames,
            long observedDurationMs,
            int recoverableCodecErrors,
            int fallbackSuccessCount,
            int fallbackFailureCount,
            FailureKind lastFailureKind,
            long updatedAtEpochMs) {

        Entry {
            firstFrameCount = Math.max(0, firstFrameCount);
            successCount = Math.max(0, successCount);
            failureCount = Math.max(0, failureCount);
            consecutiveFailures = Math.max(0, consecutiveFailures);
            droppedFrames = Math.max(0, droppedFrames);
            observedDurationMs = Math.max(0, observedDurationMs);
            recoverableCodecErrors = Math.max(0, recoverableCodecErrors);
            fallbackSuccessCount = Math.max(0, fallbackSuccessCount);
            fallbackFailureCount = Math.max(0, fallbackFailureCount);
            lastFailureKind = lastFailureKind == null ? FailureKind.NONE : lastFailureKind;
        }

        boolean valid() {
            return version == ENTRY_VERSION
                    && key != null
                    && key.valid()
                    && updatedAtEpochMs > 0;
        }

        boolean blacklisted() {
            return consecutiveFailures >= BLACKLIST_THRESHOLD;
        }
    }

    private record DecodeResult(
            boolean validStore,
            boolean rewriteRequired,
            Map<ExoDecoderRuntimeKey.Key, Entry> entries) {
    }
}
