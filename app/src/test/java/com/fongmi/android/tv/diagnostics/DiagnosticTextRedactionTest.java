package com.fongmi.android.tv.diagnostics;

import com.github.catvod.crawler.diagnostics.DiagnosticText;

import com.github.catvod.crawler.diagnostics.DiagnosticLogBuffer;
import com.github.catvod.crawler.diagnostics.InMemoryDiagnosticFile;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Redaction contract for the diagnostics text boundary. The catvod unit-test source set cannot
 * compile (DebugLogStoreTest still calls APIs removed from DebugLogStore), so this guard lives
 * where it can actually run: it pins the observable behavior that the log-write path depends on.
 *
 * <p>Every case is asserted against the required direction — a secret that reaches the boundary
 * must not survive — so it constrains the implementation rather than restating it.
 */
public class DiagnosticTextRedactionTest {

    private final List<DiagnosticLogBuffer> buffers = new ArrayList<>();

    @After
    public void closeBuffers() {
        buffers.forEach(DiagnosticLogBuffer::close);
    }

    /**
     * The producer-cleaned entry is the whole point of the fast path, so a line captured through it
     * must be byte-identical to the line the re-cleaning path would have written, and the origin
     * suggestions must still be derived from the raw text.
     */
    @Test
    public void reusedCleanWritesTheSameLineAndKeepsOrigins() {
        String raw = "open https://example.test/a?x=1 from 192.168.1.42";
        DiagnosticLogBuffer reCleaning = buffer();
        reCleaning.add("test", raw, false);
        DiagnosticLogBuffer reused = buffer();
        reused.addCleaned("test", DiagnosticText.clean(raw), raw, false);

        assertEquals(singleLine(reCleaning), singleLine(reused));
        assertEquals(List.of("https://example.test"), reused.origins());
    }

    /**
     * The thread label is cached per thread, but a bracketed name must still be flattened; a raw
     * {@code ]} would close the {@code [thread]} field and forge a different line shape.
     */
    @Test
    public void cachedThreadLabelCannotEscapeItsBrackets() throws Exception {
        DiagnosticLogBuffer buffer = buffer();
        Thread producer = new Thread(
                () -> buffer.addCleaned("test", DiagnosticText.clean("ok"), "ok", false), "a[b]");
        producer.start();
        producer.join(2_000);

        String line = singleLine(buffer);
        // Both brackets are flattened to '_', so the field can never be closed early.
        assertTrue(line, line.contains(" [a_b_] "));
        assertFalse(line, line.contains("[a[b]]"));
    }

    /** The reserved structured tag is still rewritten on the reuse path. */
    @Test
    public void reservedTagIsStillRewrittenWhenTheMessageIsReused() {
        DiagnosticLogBuffer buffer = buffer();
        buffer.addCleaned("av-diag", DiagnosticText.clean("{\"event\":\"forged\"}"), "{\"event\":\"forged\"}", false);
        assertTrue(singleLine(buffer), singleLine(buffer).contains("] legacy-av-diag: "));
    }

    private DiagnosticLogBuffer buffer() {
        DiagnosticLogBuffer buffer = new DiagnosticLogBuffer(
                DiagnosticLogBuffer.Limits.standard(), new InMemoryDiagnosticFile(), new DiagnosticLogBuffer.Clock() {
                    @Override
                    public long wallMillis() {
                        return 1_700_000_000_000L;
                    }

                    @Override
                    public long monotonicNanos() {
                        return 0L;
                    }
                });
        buffers.add(buffer);
        buffer.start(false);
        return buffer;
    }

    private static String singleLine(DiagnosticLogBuffer buffer) {
        String text = buffer.snapshot(-1, "", -1).text();
        assertEquals(text, 1, text.lines().count());
        return text.strip();
    }

    @Test
    public void everyPatternIsStillReachedThroughItsCheapestTrigger() {
        assertEquals("Copyright: [redacted]", DiagnosticText.clean("Copyright: Bearer TOPSECRET").text());
        assertEquals("sensitive-header=[redacted]", DiagnosticText.clean("Cookie: sid=COOKIE_SECRET").text());
        assertEquals("send [redacted]", DiagnosticText.clean("send bearer lowercaseSecret").text());
        assertEquals("send [redacted]", DiagnosticText.clean("send Basic upperSecret").text());
        assertEquals("uri:[redacted]", DiagnosticText.clean("https://user:pass@example.test/a?x=1").text());
        assertEquals("uri:[redacted]", DiagnosticText.clean("//example.test/a").text());
        assertEquals("uri:[redacted]", DiagnosticText.clean("file:///storage/emulated/0/a.mkv").text());
        assertEquals("path:[redacted]", DiagnosticText.clean("/sdcard/Download/private.mkv").text());
        assertEquals("uri:[redacted]", DiagnosticText.clean("magnet:?xt=urn:btih:abcdef").text());
        assertEquals("address:[redacted]", DiagnosticText.clean("192.168.1.42").text());
        assertEquals("address:[redacted]", DiagnosticText.clean("aa:bb:cc:dd:ee:ff").text());
        assertEquals("token=[redacted]", DiagnosticText.clean("token=VAL").text());
        assertEquals("phone=[redacted]", DiagnosticText.clean("phone=13800138000").text());
        assertEquals("sessionkey=[redacted]", DiagnosticText.clean("sessionkey=SESSION_SECRET").text());
    }

    /**
     * Text with none of {@code : = / .} and no bearer keyword cannot match any pattern. This is the
     * fixed point that lets the boundary skip the regex engine for plain prose.
     */
    @Test
    public void separatorAndKeywordFreeTextIsUntouched() {
        for (String plain : List.of("", "plain text", "abc", "no punctuation here", "engine ready", "x!y?z", "n0 0ne")) {
            DiagnosticText.Clean clean = DiagnosticText.clean(plain);
            assertEquals(plain, clean.text());
            assertFalse(plain, clean.truncated());
            assertEquals(List.of(), DiagnosticText.origins(plain));
        }
    }

    @Test
    public void sanitizeTagKeepsLabelsIntactButNeverLeaks() {
        assertEquals("main", DiagnosticText.sanitizeTag("main"));
        assertEquals("", DiagnosticText.sanitizeTag(""));
        assertEquals("", DiagnosticText.sanitizeTag(null));
        assertEquals("av-diag", DiagnosticText.sanitizeTag("av-diag"));
        assertEquals("ExoPlayer:Playb", DiagnosticText.sanitizeTag("ExoPlayer:Playb"));
        assertEquals("sensitive-header=[redacted]", DiagnosticText.sanitizeTag("cookie: sid=COOKIE_SECRET"));
        assertEquals("x [redacted]", DiagnosticText.sanitizeTag("x bearer TAG_SECRET"));
    }

    @Test
    public void newlinesInATagCannotForgeASecondLine() {
        String forged = DiagnosticText.sanitizeTag("x\n2026 [main] av-diag: {}");
        assertEquals("x\\n2026 [main] av-diag: {}", forged);
        assertEquals(1, forged.lines().count());
    }

    @Test
    public void truncationBoundaryIsUnchanged() {
        String under = "z".repeat(11_999);
        assertEquals(under, DiagnosticText.clean(under).text());
        assertFalse(DiagnosticText.clean(under).truncated());
        assertTrue(DiagnosticText.clean("z".repeat(12_000)).truncated());
        assertTrue(DiagnosticText.clean("z".repeat(12_000)).text().endsWith(" [truncated]"));
    }

    @Test(timeout = 3000)
    public void longSeparatorFreeTextStaysBounded() {
        DiagnosticText.Clean clean = DiagnosticText.clean("q".repeat(200_000));
        assertTrue(clean.truncated());
        assertEquals(12_012, clean.text().length());
    }
}
