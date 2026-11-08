package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class ExoHlsAdblockDataSourceTest {

    @Test
    public void identifiesOnlyHlsManifestRequests() {
        assertTrue(ExoHlsAdblockDataSource.isManifestUrl("https://example.test/live/index.m3u8"));
        assertTrue(ExoHlsAdblockDataSource.isManifestUrl("https://example.test/live/playlist.m3u8?token=1"));
        assertFalse(ExoHlsAdblockDataSource.isManifestUrl("https://example.test/live/segment.ts"));
        assertFalse(ExoHlsAdblockDataSource.isManifestUrl("https://example.test/live/key.bin"));
    }

    @Test
    public void identifiesExtensionlessHlsManifestFromResponseContentType() {
        assertTrue(ExoHlsAdblockDataSource.isManifestRequest(
                "https://example.test/playback/token",
                Map.of("Content-Type", List.of("application/vnd.apple.mpegurl; charset=utf-8"))));
        assertTrue(ExoHlsAdblockDataSource.isManifestRequest(
                "https://example.test/playback/token",
                Map.of("content-type", List.of("application/x-mpegURL"))));
        assertFalse(ExoHlsAdblockDataSource.isManifestRequest(
                "https://example.test/live/segment.ts",
                Map.of("Content-Type", List.of("video/mp2t"))));
    }

    @Test
    public void octetStreamDoesNotVetoARealPlaylist() {
        // 不少静态服务器没配 .m3u8 的 MIME，真播放列表也返回 application/octet-stream；
        // 若把它当分片证据，广告过滤会在这些源上整体失效。
        assertTrue(ExoHlsAdblockDataSource.isManifestRequest(
                "https://example.test/live/index.m3u8",
                Map.of("Content-Type", List.of("application/octet-stream"))));
    }

    @Test
    public void segmentBytesUnderAnM3u8SuffixAreNotPlaylists() {
        // 实测本地包直播：分片地址顶着 .m3u8 后缀返回 TS 字节，必须按原样透传。
        assertFalse(ExoHlsAdblockDataSource.looksLikePlaylist(new byte[]{0x47, 0x40, 0x11, 0x10, 0x00}));
        assertFalse(ExoHlsAdblockDataSource.looksLikePlaylist(new byte[0]));
        assertFalse(ExoHlsAdblockDataSource.looksLikePlaylist(null));
        assertTrue(ExoHlsAdblockDataSource.looksLikePlaylist("#EXTM3U\n#EXTINF:1,\na.ts".getBytes(StandardCharsets.UTF_8)));
        byte[] bom = "#EXTM3U\n#EXT-X-VERSION:3\n".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bom.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(bom, 0, withBom, 3, bom.length);
        assertTrue(ExoHlsAdblockDataSource.looksLikePlaylist(withBom));
    }

    @Test
    public void changedOutcomeProducesPlaybackNoticeText() {
        String manifest = "#EXTM3U\n#EXTINF:5.0,\nad/001.ts\n#EXTINF:8.0,\nvideo/002.ts\n#EXTINF:8.0,\nvideo/003.ts\n#EXTINF:8.0,\nvideo/004.ts\n#EXT-X-ENDLIST\n";
        ExoHlsAdblockDataSource.Result result = ExoHlsAdblockDataSource.cleanForTest(
                "https://example.test/live/index.m3u8", manifest);

        assertTrue(result.changed());
        assertEquals("已跳过 1 个广告片段，总广告时长 5.0 秒", result.notice());
    }
}
