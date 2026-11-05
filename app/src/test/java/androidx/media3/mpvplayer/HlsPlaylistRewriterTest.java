package androidx.media3.mpvplayer;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HlsPlaylistRewriterTest {

    @Test
    public void masterPlaylistPreservesEveryVariantAndSeparateBitrateSemantics() {
        String playlist = """
                #EXTM3U
                #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="中文",URI="audio/zh.m3u8"
                #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="English",URI="https://sub.example/en.m3u8"
                #EXT-X-STREAM-INF:BANDWIDTH=4500000,AVERAGE-BANDWIDTH=4000000,RESOLUTION=1280x720,AUDIO="audio",SUBTITLES="subs"
                video/720.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=9000000,AVERAGE-BANDWIDTH=7800000,RESOLUTION=1920x1080,AUDIO="audio",SUBTITLES="subs"
                https://video.example/1080.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=28000000,AVERAGE-BANDWIDTH=24000000,RESOLUTION=3840x2160,AUDIO="audio",SUBTITLES="subs"
                video/4k.m3u8
                """;
        List<Mapping> mappings = new ArrayList<>();

        HlsPlaylistRewriter.Result result = rewrite(playlist, null, mappings);

        assertEquals(3, count(result.text(), "#EXT-X-STREAM-INF:"));
        assertTrue(result.text().contains("proxy:video/720.m3u8"));
        assertTrue(result.text().contains("proxy:https://video.example/1080.m3u8"));
        assertTrue(result.text().contains("proxy:video/4k.m3u8"));
        assertTrue(result.text().contains("URI=\"proxy:audio/zh.m3u8\""));
        assertTrue(result.text().contains("URI=\"proxy:https://sub.example/en.m3u8\""));
        assertEquals(3, result.variants().size());
        assertVariant(result.variants().get(0), "source:video/720.m3u8", 4_500_000, 4_000_000, 1280, 720);
        assertVariant(result.variants().get(1), "source:https://video.example/1080.m3u8", 9_000_000, 7_800_000, 1920, 1080);
        assertVariant(result.variants().get(2), "source:video/4k.m3u8", 28_000_000, 24_000_000, 3840, 2160);
        assertEquals(HlsPlaylistRewriter.VariantKind.STREAM,
                result.variants().get(0).variant().kind());
        assertEquals(5, mappings.size());
        assertEquals(HlsPlaylistRewriter.UriRole.OTHER, mappings.get(0).context().role());
        assertEquals(HlsPlaylistRewriter.UriRole.OTHER, mappings.get(1).context().role());
        assertEquals(HlsPlaylistRewriter.UriRole.VARIANT_PLAYLIST, mappings.get(2).context().role());
    }

    @Test
    public void iframeAndImageVariantsRemainAvailable() {
        String playlist = """
                #EXTM3U
                #EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=950000,AVERAGE-BANDWIDTH=800000,RESOLUTION=640x360,URI="iframe.m3u8"
                #EXT-X-IMAGE-STREAM-INF:BANDWIDTH=120000,AVERAGE-BANDWIDTH=100000,RESOLUTION=320x180,URI="thumbs.m3u8"
                #EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1280x720
                main.m3u8
                """;

        HlsPlaylistRewriter.Result result = rewrite(playlist, null, new ArrayList<>());

        assertEquals(3, result.variants().size());
        assertTrue(result.text().contains("URI=\"proxy:iframe.m3u8\""));
        assertTrue(result.text().contains("URI=\"proxy:thumbs.m3u8\""));
        assertTrue(result.text().contains("proxy:main.m3u8"));
        assertVariant(result.variants().get(0), "source:iframe.m3u8", 950_000, 800_000, 640, 360);
        assertVariant(result.variants().get(1), "source:thumbs.m3u8", 120_000, 100_000, 320, 180);
        assertEquals(HlsPlaylistRewriter.VariantKind.I_FRAME,
                result.variants().get(0).variant().kind());
        assertEquals(HlsPlaylistRewriter.VariantKind.IMAGE,
                result.variants().get(1).variant().kind());
        assertEquals(HlsPlaylistRewriter.VariantKind.STREAM,
                result.variants().get(2).variant().kind());
    }

    @Test
    public void malformedAttributesAndCrLfDoNotRemoveSingleVariant() {
        String playlist = "#EXTM3U\r\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=invalid,AVERAGE-BANDWIDTH=3500000,RESOLUTION=broken,CODECS=\"avc1.4d401f,mp4a.40.2\"\r\n"
                + "only.m3u8\r\n";

        HlsPlaylistRewriter.Result result = rewrite(playlist, null, new ArrayList<>());

        assertEquals(1, count(result.text(), "#EXT-X-STREAM-INF:"));
        assertTrue(result.text().contains("proxy:only.m3u8"));
        assertFalse(result.text().contains("\r"));
        assertEquals(1, result.variants().size());
        assertVariant(result.variants().get(0), "source:only.m3u8", 0, 3_500_000, 0, 0);
    }

    @Test
    public void mediaPlaylistKeepsSegmentsByteRangesAndDataUris() {
        String playlist = """
                #EXTM3U
                #EXT-X-MAP:URI="init.mp4"
                #EXT-X-KEY:METHOD=AES-128,URI="data:text/plain;base64,c2VjcmV0"
                #EXTINF:4.5,
                first.ts
                #EXTINF:6,
                #EXT-X-BYTERANGE:1024@0
                second.ts
                #EXT-X-ENDLIST
                """;
        List<Mapping> mappings = new ArrayList<>();

        HlsPlaylistRewriter.Result result = rewrite(playlist, null, mappings);

        assertTrue(result.variants().isEmpty());
        assertEquals(2, result.segments().size());
        assertSegment(result.segments().get(0), "source:first.ts", 4.5, 0, false);
        assertSegment(result.segments().get(1), "source:second.ts", 6, 4.5, true);
        assertEquals(2, result.mediaUnits().size());
        assertMediaUnit(result.mediaUnits().get(0),
                "source:first.ts", 4.5, 0, false, false);
        assertMediaUnit(result.mediaUnits().get(1),
                "source:second.ts", 6, 4.5, true, false);
        assertTrue(result.text().contains("URI=\"proxy:init.mp4\""));
        assertTrue(result.text().contains("URI=\"data:text/plain;base64,c2VjcmV0\""));
        assertTrue(result.text().contains("proxy:first.ts"));
        assertTrue(result.text().contains("proxy:second.ts"));
        assertEquals(3, mappings.size());
        assertTrue(mappings.get(0).cacheable());
        assertTrue(mappings.get(1).cacheable());
        assertFalse(mappings.get(2).cacheable());
    }

    @Test
    public void lowLatencyPartsCarrySelectedVariantWithoutBecomingVariants() {
        HlsPlaylistRewriter.Variant selected = new HlsPlaylistRewriter.Variant(9_000_000, 7_800_000, 1920, 1080);
        String playlist = """
                #EXTM3U
                #EXT-X-PART:DURATION=0.333,URI="part-1.m4s"
                #EXT-X-PRELOAD-HINT:TYPE=PART,URI="part-2.m4s"
                """;
        List<Mapping> mappings = new ArrayList<>();

        HlsPlaylistRewriter.Result result = rewrite(playlist, selected, mappings);

        assertTrue(result.variants().isEmpty());
        assertEquals(2, mappings.size());
        assertEquals(1, result.mediaUnits().size());
        assertMediaUnit(result.mediaUnits().get(0),
                "source:part-1.m4s", 0.333, 0, false, true);
        assertEquals(HlsPlaylistRewriter.UriRole.MEDIA_SEGMENT, mappings.get(0).context().role());
        assertEquals(selected, mappings.get(0).context().variant());
        assertEquals(0.333, mappings.get(0).context().durationSeconds(),
                0.0001);
        assertEquals(0, mappings.get(0).context().startSeconds(), 0.0001);
        assertEquals(selected, mappings.get(1).context().variant());
        assertEquals(0, mappings.get(1).context().durationSeconds(), 0.0001);
        assertEquals(0.333, mappings.get(1).context().startSeconds(),
                0.0001);
    }

    @Test
    public void completedLowLatencyPartsDoNotDoubleCountParentSegment() {
        String playlist = """
                #EXTM3U
                #EXT-X-PART:DURATION=0.5,URI="p1.m4s"
                #EXT-X-PART:DURATION=0.5,URI="p2.m4s"
                #EXTINF:1.0,
                full.m4s
                #EXT-X-PART:DURATION=0.25,URI="p3.m4s"
                """;

        HlsPlaylistRewriter.Result result = rewrite(
                playlist, null, new ArrayList<>());

        assertEquals(1, result.segments().size());
        assertSegment(result.segments().get(0),
                "source:full.m4s", 1, 0, false);
        assertEquals(4, result.mediaUnits().size());
        assertMediaUnit(result.mediaUnits().get(0),
                "source:p1.m4s", 0.5, 0, false, true);
        assertMediaUnit(result.mediaUnits().get(1),
                "source:p2.m4s", 0.5, 0.5, false, true);
        assertMediaUnit(result.mediaUnits().get(2),
                "source:full.m4s", 1, 0, false, false);
        assertMediaUnit(result.mediaUnits().get(3),
                "source:p3.m4s", 0.25, 1, false, true);
    }

    @Test
    public void parentSegmentRoundingCannotMovePartTimelineBackward() {
        String playlist = """
                #EXTM3U
                #EXT-X-PART:DURATION=0.6,URI="p1.m4s"
                #EXT-X-PART:DURATION=0.6,URI="p2.m4s"
                #EXTINF:1.0,
                full.m4s
                #EXT-X-PART:DURATION=0.25,URI="p3.m4s"
                """;

        HlsPlaylistRewriter.Result result = rewrite(
                playlist, null, new ArrayList<>());

        assertMediaUnit(result.mediaUnits().get(3),
                "source:p3.m4s", 0.25, 1.2, false, true);
    }

    @Test
    public void nonUriLinesAndDataUrisRemainUnchanged() {
        String playlist = """
                #EXTM3U
                #EXT-X-SESSION-DATA:DATA-ID="com.example.title",VALUE="Example"
                #EXT-X-KEY:METHOD=SAMPLE-AES,URI="data:application/octet-stream;base64,AAAA"
                """;

        HlsPlaylistRewriter.Result result = rewrite(playlist, null, new ArrayList<>());

        assertEquals(playlist, result.text());
        assertTrue(result.variants().isEmpty());
        assertTrue(result.segments().isEmpty());
        assertTrue(result.mediaUnits().isEmpty());
    }

    @Test
    public void stripsEncryptionKeyAndNumbersSegmentsFromMediaSequence() {
        String playlist = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-TARGETDURATION:6
                #EXT-X-MEDIA-SEQUENCE:5
                #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
                #EXTINF:4.5,
                first.ts
                #EXTINF:6,
                second.ts
                #EXT-X-ENDLIST
                """;
        List<Mapping> mappings = new ArrayList<>();

        HlsPlaylistRewriter.Result result = HlsPlaylistRewriter.rewrite(playlist, null, (uri, cacheable, context) -> {
            mappings.add(new Mapping(uri, cacheable, context));
            return new HlsPlaylistRewriter.MappedUri("source:" + uri, "proxy:" + uri);
        }, true);

        assertFalse(result.text().contains("#EXT-X-KEY"));
        assertTrue(result.text().contains("#EXT-X-MEDIA-SEQUENCE:5"));
        assertEquals(2, result.segments().size());
        HlsPlaylistRewriter.UriContext first = mappings.stream()
                .filter(m -> m.context().role() == HlsPlaylistRewriter.UriRole.MEDIA_SEGMENT)
                .findFirst().orElseThrow().context();
        assertEquals(5, first.mediaSequence());
    }

    @Test
    public void keepsEncryptionKeyRewrittenWhenNotStripping() {
        String playlist = """
                #EXTM3U
                #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
                #EXTINF:4.5,
                first.ts
                #EXT-X-ENDLIST
                """;
        List<Mapping> mappings = new ArrayList<>();

        HlsPlaylistRewriter.Result result = rewrite(playlist, null, mappings);

        assertTrue(result.text().contains("#EXT-X-KEY"));
        assertTrue(result.text().contains("URI=\"proxy:key.bin\""));
    }

    @Test
    public void findsAes128KeyAndKeepsCommaInsideQuotedUri() {
        String playlist = """
                #EXTM3U
                #EXT-X-KEY:METHOD=AES-128,URI="data:text/plain;base64,c2VjcmV0",IV=0x000102030405060708090a0b0c0d0e0f
                #EXTINF:4.5,
                first.ts
                """;

        HlsPlaylistRewriter.EncryptionKey key = HlsPlaylistRewriter.findAes128Key(playlist);

        assertNotNull(key);
        assertEquals("AES-128", key.method());
        assertEquals("data:text/plain;base64,c2VjcmV0", key.uri());
        assertNotNull(key.explicitIv());
        assertEquals(16, key.explicitIv().length);
        assertEquals(0, key.explicitIv()[0]);
        assertEquals(0x0f, key.explicitIv()[15]);
    }

    @Test
    public void ignoresSampleAesAndKeylessPlaylists() {
        assertNull(HlsPlaylistRewriter.findAes128Key("""
                #EXTM3U
                #EXT-X-KEY:METHOD=SAMPLE-AES,URI="key.bin"
                #EXTINF:4.5,
                first.ts
                """));
        assertNull(HlsPlaylistRewriter.findAes128Key("""
                #EXTM3U
                #EXTINF:4.5,
                first.ts
                """));
    }

    private static HlsPlaylistRewriter.Result rewrite(String playlist, HlsPlaylistRewriter.Variant inherited, List<Mapping> mappings) {
        return HlsPlaylistRewriter.rewrite(playlist, inherited, (uri, cacheable, context) -> {
            mappings.add(new Mapping(uri, cacheable, context));
            return new HlsPlaylistRewriter.MappedUri("source:" + uri, "proxy:" + uri);
        });
    }

    private static void assertVariant(HlsPlaylistRewriter.VariantEntry entry, String uri, long bandwidth, long average, int width, int height) {
        assertEquals(uri, entry.uri());
        assertEquals(bandwidth, entry.variant().bandwidth());
        assertEquals(average, entry.variant().averageBandwidth());
        assertEquals(width, entry.variant().width());
        assertEquals(height, entry.variant().height());
    }

    private static void assertSegment(HlsPlaylistRewriter.Segment segment, String uri, double duration, double start, boolean byteRange) {
        assertEquals(uri, segment.uri());
        assertEquals(duration, segment.durationSeconds(), 0.0001);
        assertEquals(start, segment.startSeconds(), 0.0001);
        assertEquals(byteRange, segment.byteRange());
    }

    private static void assertMediaUnit(
            HlsPlaylistRewriter.MediaUnit unit,
            String uri,
            double duration,
            double start,
            boolean byteRange,
            boolean partial) {
        assertEquals(uri, unit.uri());
        assertEquals(duration, unit.durationSeconds(), 0.0001);
        assertEquals(start, unit.startSeconds(), 0.0001);
        assertEquals(byteRange, unit.byteRange());
        assertEquals(partial, unit.partial());
    }

    private static int count(String value, String needle) {
        int count = 0;
        for (int index = 0; (index = value.indexOf(needle, index)) >= 0; index += needle.length()) count++;
        return count;
    }

    private record Mapping(String uri, boolean cacheable, HlsPlaylistRewriter.UriContext context) {
    }
}
