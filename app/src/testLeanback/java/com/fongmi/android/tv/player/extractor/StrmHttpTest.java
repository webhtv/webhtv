package com.fongmi.android.tv.player.extractor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.net.Uri;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.setting.PlayerSetting;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class StrmHttpTest {

    @Rule
    public TemporaryFolder files = new TemporaryFolder();

    @Test
    public void successfulPointerWithoutFilenameHeaderReturnsFirstTarget() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("https://media.example/movie.mp4\n"));
            server.start();
            String input = server.url("/movie.strm?token=a%2Bb").toString();
            assertEquals("https://media.example/movie.mp4", new Strm().fetch(input));
            assertEquals(1, server.getRequestCount());
            RecordedRequest request = server.takeRequest();
            assertEquals("GET", request.getMethod());
            assertEquals("/movie.strm?token=a%2Bb", request.getPath());
        }
    }

    @Test
    public void bomWhitespaceAndLineEndingsPreserveFirstTarget() throws Exception {
        for (String ending : new String[]{"\n", "\r", "\r\n"}) {
            assertTarget("https://media.example/a.mp4?sig=a%2Bb&x=1",
                    "\uFEFF\t https://media.example/a.mp4?sig=a%2Bb&x=1 \t"
                            + ending + "https://other.example/b.mp4");
        }
    }

    @Test
    public void invalidOrRelativeFirstLinesReturnOriginalUrl() throws Exception {
        for (String body : new String[]{"", " \t\r\nhttps://valid.example/a", "<html>error</html>",
                "{\"error\":\"failed\"}", "/media/a", "//host/a", "https:", "http:///a",
                "https://host/%GG", "https://[bad]/a", "https://host/a b", "https://host/a\u001b",
                "https://host/a\u0000", "https://host/a\tb", "\u0000https://host/a"}) {
            assertFallback(new MockResponse().setBody(body));
        }
    }

    @Test
    public void errorResponsesNeverTreatTheirBodyAsAPointer() throws Exception {
        for (int code : new int[]{401, 403, 404, 500}) {
            assertFallback(new MockResponse().setResponseCode(code).setBody("https://media.example/error.mp4"));
        }
    }

    @Test
    public void partialAndEmptySuccessResponsesReturnOriginalUrl() throws Exception {
        assertFallback(new MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-32/1024")
                .setBody("https://media.example/part.mp4"));
        assertFallback(new MockResponse().setResponseCode(204));
        assertFallback(new MockResponse().setResponseCode(205));
    }

    @Test
    public void responsesAtOrAboveThePeekCapReturnOriginalUrl() throws Exception {
        String target = "https://media.example/boundary.mp4";
        assertTarget(target, body(target, 65535));
        assertFallback(new MockResponse().setBody(body(target, 65536)));
        assertFallback(new MockResponse().setBody(body(target, 65537)));
        assertFallback(new MockResponse().setBody("https://media.example/huge.mp4\n"
                + "x".repeat(200_000)));
    }

    @Test
    public void interruptedBodiesAndFailedRequestsReturnOriginalUrl() throws Exception {
        assertFallback(new MockResponse().setBody("https://media.example/truncated.mp4\n" + "x".repeat(2048))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
        assertFallback(new MockResponse().setBody("https://media.example/short.mp4")
                .setHeader("Content-Length", "2000").setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));
        String unavailable;
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            unavailable = server.url("/unavailable.strm").toString();
        }
        assertEquals(unavailable, new Strm().fetch(unavailable));
        assertEquals("http:///missing.strm", new Strm().fetch("http:///missing.strm"));
    }

    @Test
    public void filenameHeadersDoNotControlSuccessfulPointerParsing() throws Exception {
        for (String name : new String[]{"movie.strm", "movie.txt", "movie.mp4", "MOVIE.STRM"}) {
            assertTarget("https://media.example/header.mp4", new MockResponse()
                    .setHeader("Content-Disposition", "attachment; filename=" + name)
                    .setBody("https://media.example/header.mp4\n"));
        }
    }

    @Test
    public void absoluteTargetsKeepTheirSchemeFragmentAndEscapes() throws Exception {
        for (String target : new String[]{"https://host/a#track", "rtsp://host/stream",
                "file:///sdcard/a.mp4", "custom+media:resource", "HTTPS://host/a?sig=a%2Fb%2Bc"}) {
            assertTarget(target, target + "\n");
        }
    }

    @Test
    public void redirectsAreReturnedToThePlayerWithoutFollowingLocation() throws Exception {
        try (MockWebServer redirected = new MockWebServer()) {
            redirected.start();
            for (int code : new int[]{301, 302, 307, 308}) {
                assertFallback(new MockResponse().setResponseCode(code)
                        .setHeader("Location", redirected.url("/media.mp4").toString())
                        .setBody("https://media.example/redirect.mp4"));
            }
            assertEquals(0, redirected.getRequestCount());
        }
    }

    @Test
    public void unknownLengthBodiesUseTheActualPeekBoundary() throws Exception {
        String target = "https://media.example/chunked.mp4";
        assertTarget(target, new MockResponse().setChunkedBody(target + "\n", 7));
        assertTarget(target, new MockResponse().setChunkedBody(body(target, 65535), 4096));
        assertFallback(new MockResponse().setChunkedBody(body(target, 65536), 4096));
        assertFallback(new MockResponse().setChunkedBody(body(target, 65537), 4096));
    }

    @Test
    public void completeBinaryAndHtmlBodiesDoNotBecomeMediaTargets() throws Exception {
        byte[] binary = new byte[512];
        for (int i = 0; i < binary.length; i++) binary[i] = (byte) i;
        assertFallback(new MockResponse().setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Disposition", "attachment; filename=direct.strm")
                .setBody(new Buffer().write(binary)));
        assertFallback(new MockResponse().setHeader("Content-Type", "text/html")
                .setBody("<!DOCTYPE html><html><body>login required</body></html>"));
    }

    @Test
    public void localFilesKeepTheirUntrimmedFirstLineAndMissingFileResult() throws Exception {
        File local = files.newFile("local.strm");
        String first = "\uFEFF\t https://media.example/file.mp4 \t";
        Files.writeString(local.toPath(), first + "\r\nhttps://other.example/file.mp4", StandardCharsets.UTF_8);
        assertEquals(first, new Strm().fetch(local.getAbsolutePath()));
        assertEquals(first, new Strm().fetch("file://" + local.getAbsolutePath()));
        assertEquals("", new Strm().fetch(new File(files.getRoot(), "missing.strm").getAbsolutePath()));
    }

    @Test
    public void matchingKeepsExistingExtensionAndQueryBehavior() {
        Strm strm = new Strm();
        assertTrue(strm.match(Uri.parse("https://host/movie.strm?token=1")));
        assertTrue(strm.match(Uri.parse("file:///movie.strm")));
        assertFalse(strm.match(Uri.parse("https://host/movie.STRM")));
        assertFalse(strm.match(Uri.parse("https://host/movie.txt")));
        assertFalse(strm.match(Uri.parse("https://host/movie.mp4")));
    }

    @Test
    public void sourceConsumersKeepParseAndHeadersForEachPlayer() throws Exception {
        for (int player : new int[]{PlayerSetting.EXO, PlayerSetting.IJK, PlayerSetting.MPV}) {
            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(new MockResponse().setBody("https://media.example/source.mp4\n"));
                server.enqueue(new MockResponse().setResponseCode(500).setBody("https://media.example/error.mp4"));
                server.start();
                for (boolean success : new boolean[]{true, false}) {
                    String input = server.url(success ? "/source.strm" : "/fallback.strm").toString();
                    Result result = new Result();
                    result.setUrl(input);
                    result.setParse(1);
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Authorization", "Bearer retained");
                    result.setHeader(headers);
                    assertEquals(success ? "https://media.example/source.mp4" : input,
                            new Source().fetch(result, player));
                    assertEquals(0, result.getParse().intValue());
                    assertEquals(Map.of("Authorization", "Bearer retained"), result.getHeader());
                    assertNull(server.takeRequest().getHeader("Authorization"));
                }
                assertEquals(2, server.getRequestCount());
            }
        }
    }

    @Test
    public void slowSmallBodyAndRequestsAfterFallbackStillResolve() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setChunkedBody("x".repeat(65537), 4096));
            server.enqueue(new MockResponse().setBody("https://media.example/after.mp4\n")
                    .setBodyDelay(100, TimeUnit.MILLISECONDS));
            server.start();
            String input = server.url("/large.strm").toString();
            assertEquals(input, new Strm().fetch(input));
            assertEquals("https://media.example/after.mp4", new Strm().fetch(server.url("/after.strm").toString()));
            assertEquals(2, server.getRequestCount());
        }
    }

    @Test
    public void headerSuffixedPointersKeepTheirWholeLine() throws Exception {
        String target = "http://host/x.m3u8|User-Agent=A&Referer=B";
        assertTarget(target, target + "\n");
    }

    @Test
    public void nonAsciiPointersKeepTheirWholeLine() throws Exception {
        String target = "https://media.example/电影/第01集.mp4";
        assertTarget(target, target + "\n");
    }

    @Test
    public void mediaResponsesAreNeverTreatedAsPointers() throws Exception {
        assertFallback(new MockResponse().setHeader("Content-Type", "video/mp4")
                .setChunkedBody("https://media.example/direct.mp4\n", 1));
        assertFallback(new MockResponse().setHeader("Content-Type", "audio/mpeg")
                .setChunkedBody("https://media.example/direct.mp3\n", 1));
        assertFallback(new MockResponse().setHeader("Content-Type", "application/vnd.apple.mpegurl")
                .setChunkedBody("https://media.example/direct.m3u8\n", 1));
    }

    @Test
    public void knownLengthMediaResponsesStillResolveTheirPointer() throws Exception {
        assertTarget("https://media.example/direct.mp4", new MockResponse()
                .setHeader("Content-Type", "application/vnd.apple.mpegurl")
                .setBody("https://media.example/direct.mp4\n"));
        assertFallback(new MockResponse().setHeader("Content-Type", "application/vnd.apple.mpegurl")
                .setBody("#EXTM3U\nhttps://media.example/direct.mp4\n"));
    }

    private static String body(String target, int total) {
        return target + "\n" + " ".repeat(total - target.length() - 1);
    }

    private void assertTarget(String expected, String responseBody) throws Exception {
        assertTarget(expected, new MockResponse().setBody(responseBody));
    }

    private void assertTarget(String expected, MockResponse response) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(response);
            server.start();
            assertEquals(expected, new Strm().fetch(server.url("/target.strm").toString()));
            assertEquals(1, server.getRequestCount());
        }
    }

    private void assertFallback(MockResponse response) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(response);
            server.start();
            String input = server.url("/fallback.strm?token=a%2Bb").toString();
            assertEquals(input, new Strm().fetch(input));
            assertEquals(1, server.getRequestCount());
        }
    }
}
