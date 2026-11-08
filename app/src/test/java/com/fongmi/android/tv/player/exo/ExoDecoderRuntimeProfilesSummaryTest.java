package com.fongmi.android.tv.player.exo;

import org.junit.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards the user-visible hint for silent hardware-decoder exclusions: playback falls back to
 * software decoding after two consecutive failures for one decoder+format combination, which looks
 * like unexplained frame drops on 4K content unless the exclusion is surfaced.
 */
public class ExoDecoderRuntimeProfilesSummaryTest {

    @Test
    public void summaryIsEmptyWithoutExclusions() {
        ExoDecoderRuntimeProfiles profiles = profiles(new MemoryBackend());

        assertEquals("", profiles.blacklistSummary());
        assertEquals(0, profiles.blacklistedCount());
    }

    @Test
    public void summaryDescribesExclusionAfterTwoConsecutiveFailures() {
        ExoDecoderRuntimeProfiles profiles = profiles(new MemoryBackend());
        long now = System.currentTimeMillis();
        failTwice(profiles, key(1, "video/hevc", 3840, 2160), now);

        assertEquals(1, profiles.blacklistedCount());
        String summary = profiles.blacklistSummary();
        assertTrue(summary, summary.startsWith("硬解已排除 1 条"));
        assertTrue(summary, summary.contains("HEVC"));
        assertTrue(summary, summary.contains("3840×2160"));
    }

    @Test
    public void singleFailureDoesNotProduceASummary() {
        ExoDecoderRuntimeProfiles profiles = profiles(new MemoryBackend());
        long now = System.currentTimeMillis();
        profiles.recordFailure(
                key(1, "video/hevc", 3840, 2160),
                ExoDecoderRuntimeProfileStore.FailureKind.INIT,
                ExoDecoderRuntimeProfileStore.Metrics.EMPTY,
                now);

        assertEquals(0, profiles.blacklistedCount());
        assertEquals("", profiles.blacklistSummary());
    }

    @Test
    public void summaryIgnoresExclusionsRecordedUnderAnotherEnvironment() {
        ExoDecoderRuntimeProfiles profiles = profiles(new MemoryBackend());
        long now = System.currentTimeMillis();
        // The environment digest covers Build.FINGERPRINT plus the app/media3 versions, so an
        // upgraded install can no longer match these keys. They stay in the store for 30 days and
        // must not be reported as an active hardware-decode fallback.
        failTwice(profiles, key(9, 1, "video/hevc", 3840, 2160), now);
        failTwice(profiles, key(1, 2, "video/avc", 1920, 1080), now);

        assertEquals(1, profiles.blacklistedCount());
        String summary = profiles.blacklistSummary();
        assertTrue(summary, summary.startsWith("硬解已排除 1 条"));
        assertTrue(summary, summary.contains("H.264"));
        assertFalse(summary, summary.contains("HEVC"));
    }

    @Test
    public void activeBlacklistOrdersNewestFirstAndSkipsHealthyEntries() {
        ExoDecoderRuntimeProfileStore store = new ExoDecoderRuntimeProfileStore(new MemoryBackend());
        long now = System.currentTimeMillis();
        failTwice(store, key(1, "video/hevc", 3840, 2160), now - 10_000);
        failTwice(store, key(2, "video/avc", 1920, 1080), now);
        store.recordFailure(
                key(3, "video/hevc", 1280, 720),
                ExoDecoderRuntimeProfileStore.FailureKind.INIT,
                ExoDecoderRuntimeProfileStore.Metrics.EMPTY,
                now);

        List<ExoDecoderRuntimeProfileStore.Entry> active = store.activeBlacklist(now);

        assertEquals(2, active.size());
        assertEquals("video/avc", active.get(0).key().mimeType());
        assertEquals("video/hevc", active.get(1).key().mimeType());
        assertFalse(store.activeBlacklist(now).contains(store.lookup(key(3, "video/hevc", 1280, 720), now).entry()));
    }

    private static ExoDecoderRuntimeProfiles profiles(MemoryBackend backend) {
        return new ExoDecoderRuntimeProfiles(
                backend, new ExoDecoderRuntimeKey.Environment(digest(1)));
    }

    private static void failTwice(
            ExoDecoderRuntimeProfiles profiles,
            ExoDecoderRuntimeKey.Key key,
            long now) {
        profiles.recordFailure(key, ExoDecoderRuntimeProfileStore.FailureKind.INIT,
                ExoDecoderRuntimeProfileStore.Metrics.EMPTY, now);
        profiles.recordFailure(key, ExoDecoderRuntimeProfileStore.FailureKind.DECODE,
                ExoDecoderRuntimeProfileStore.Metrics.EMPTY, now + 1);
    }

    private static void failTwice(
            ExoDecoderRuntimeProfileStore store,
            ExoDecoderRuntimeKey.Key key,
            long now) {
        store.recordFailure(key, ExoDecoderRuntimeProfileStore.FailureKind.INIT,
                ExoDecoderRuntimeProfileStore.Metrics.EMPTY, now);
        store.recordFailure(key, ExoDecoderRuntimeProfileStore.FailureKind.DECODE,
                ExoDecoderRuntimeProfileStore.Metrics.EMPTY, now + 1);
    }

    private static ExoDecoderRuntimeKey.Key key(
            int decoder,
            String mimeType,
            int width,
            int height) {
        return key(1, decoder, mimeType, width, height);
    }

    private static ExoDecoderRuntimeKey.Key key(
            int environment,
            int decoder,
            String mimeType,
            int width,
            int height) {
        return new ExoDecoderRuntimeKey.Key(
                digest(environment),
                digest(decoder + 1000),
                mimeType,
                digest(decoder + 2000),
                2,
                153,
                width,
                height,
                25_000,
                6,
                2,
                6,
                true,
                false,
                ExoDecoderRuntimeSession.OutputTarget.SURFACE,
                false);
    }

    private static String digest(int value) {
        return String.format(Locale.US, "%024x", value);
    }

    private static final class MemoryBackend
            implements ExoDecoderRuntimeProfileStore.Backend {

        private String value = "";

        @Override
        public String read() {
            return value;
        }

        @Override
        public void write(String value) {
            this.value = value == null ? "" : value;
        }

        @Override
        public void clear() {
            value = "";
        }
    }
}
