package com.fongmi.android.tv.player.extractor;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Android 7-13 (API &lt; 34) contract for MPD sanitizing.
 *
 * <p>{@code Matcher#appendReplacement/appendTail(StringBuilder, String)} only exists since API 34,
 * and {@code desugar_jdk_libs_nio} does not back-port that overload, so those calls threw
 * {@code NoSuchMethodError} on API 24-33 and {@code Source.fetch()} silently fell back to the raw
 * URL. The whole pass must therefore keep using the API 1 {@code StringBuffer} overloads.
 */
public class MpdSanitizerMatcherMethodContractTest {

    private static final Pattern APPEND_REPLACEMENT = Pattern.compile("\\.appendReplacement\\s*\\(\\s*([A-Za-z_$][\\w$]*)\\s*,");
    private static final Pattern APPEND_TAIL = Pattern.compile("\\.appendTail\\s*\\(\\s*([A-Za-z_$][\\w$]*)\\s*\\)");

    @Test
    public void everyMatcherAppendUsesTheApi1StringBufferOverload() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/player/extractor/MpdSanitizer.java");
        List<String> targets = appendTargets(source);
        assertEquals("the sanitizer must keep all five replacement calls", 5, occurrences(source, ".appendReplacement("));
        assertEquals("the sanitizer must keep all five tail calls", 5, occurrences(source, ".appendTail("));
        assertTrue("the sanitizer must keep its matcher appends", targets.size() >= 2);

        StringBuilder stringBuilderTyped = new StringBuilder();
        for (String variable : targets) {
            Matcher declaration = Pattern
                    .compile("\\b(StringBuilder|StringBuffer)\\s+" + Pattern.quote(variable) + "\\b")
                    .matcher(source);
            assertTrue("every matcher target must be declared:" + variable, declaration.find());
            if ("StringBuilder".equals(declaration.group(1))) stringBuilderTyped.append(variable).append(' ');
        }
        assertFalse("no matcher append may target a StringBuilder (API 34 overload): " + stringBuilderTyped,
                stringBuilderTyped.length() > 0);
        assertTrue("the source must document why the StringBuffer overload is required",
                source.contains("StringBuilder 重载需要 API 34"));
    }

    @Test
    public void theApi1StringBufferOverloadStillPerformsTheReplacement() {
        StringBuffer buffer = new StringBuffer();
        Matcher matcher = Pattern.compile("([0-9]+)").matcher("a1b2c");
        while (matcher.find()) matcher.appendReplacement(buffer, "#");
        matcher.appendTail(buffer);
        assertEquals("the API 1 StringBuffer overload must still rewrite the matches", "a#b#c", buffer.toString());
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int from = source.indexOf(needle);
        while (from >= 0) {
            count++;
            from = source.indexOf(needle, from + needle.length());
        }
        return count;
    }

    private static List<String> appendTargets(String source) {
        List<String> targets = new ArrayList<>();
        for (Pattern pattern : new Pattern[]{APPEND_REPLACEMENT, APPEND_TAIL}) {
            Matcher matcher = pattern.matcher(source);
            while (matcher.find()) {
                if (!targets.contains(matcher.group(1))) targets.add(matcher.group(1));
            }
        }
        return targets;
    }

    private static String read(String name) throws Exception {
        Path path = Path.of(name);
        if (!Files.exists(path)) path = Path.of("..").resolve(name).normalize();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
