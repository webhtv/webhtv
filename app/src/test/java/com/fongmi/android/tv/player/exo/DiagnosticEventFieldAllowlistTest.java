package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.github.catvod.crawler.diagnostics.DiagnosticEvent;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locks the join between a diagnostic producer and the schema allowlist.
 *
 * <p>{@link DiagnosticEvent#observed(String, Object)} throws {@code IllegalArgumentException} for
 * any name missing from {@code DiagnosticEvent.FIELDS}, and the collector's {@code emit} catches
 * that as a collector failure. A missing name therefore does not fail loudly: the whole event
 * (including the fields that were valid) is dropped and the export silently loses the line. The
 * E-SP11 input-side drop counters were added without the matching allowlist entries, which is
 * exactly this failure — the very {@code video.output.summary} line the change was meant to enrich
 * disappeared from every diagnostics-on session.
 *
 * <p>This test is a source+behaviour hybrid on purpose: the source scan is what notices a new
 * {@code observed} literal, and constructing the real event is what notices the allowlist entry
 * being absent. Neither half alone catches the regression.
 */
public class DiagnosticEventFieldAllowlistTest {

    private static final Pattern OBSERVED_CALL = Pattern.compile("\\.observed\\(\"([^\"]+)\"");

    @Test
    public void everyFieldTheExoCollectorRecordsIsAcceptedByTheSchema() throws Exception {
        String source = read(sourcePath(
                "main", "java", "com", "fongmi", "android", "tv", "player", "exo",
                "ExoDiagnosticCollector.java"));
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = OBSERVED_CALL.matcher(source);
        while (matcher.find()) names.add(matcher.group(1));
        assertTrue("No observed() literals found in ExoDiagnosticCollector", names.size() > 5);
        // Guard against a vacuous pass if the counters are removed from the collector later.
        assertTrue(names.contains("droppedInputBuffers"));
        assertTrue(names.contains("skippedInputBuffers"));
        assertTrue(names.contains("droppedToKeyframe"));
        for (String name : names) {
            try {
                new DiagnosticEvent("video.output.summary", "trace", "instance", 1, 1)
                        .observed(name, 0);
            } catch (IllegalArgumentException error) {
                fail("ExoDiagnosticCollector records \"" + name
                        + "\" but DiagnosticEvent rejects it, so the whole event is dropped: "
                        + error.getMessage());
            }
        }
    }

    @Test
    public void theAllowlistCheckIsNotVacuous() {
        // Self-proof that the fixture really reaches the validator.
        assertThrows(IllegalArgumentException.class,
                () -> new DiagnosticEvent("video.output.summary", "trace", "instance", 1, 1)
                        .observed("notARegisteredDiagnosticField", 0));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path sourcePath(String... parts) {
        Path path = Path.of("src", parts[0], parts[1]);
        for (int i = 2; i < parts.length; i++) path = path.resolve(parts[i]);
        if (Files.exists(path)) return path;
        path = Path.of("app", "src", parts[0], parts[1]);
        for (int i = 2; i < parts.length; i++) path = path.resolve(parts[i]);
        return path;
    }
}
