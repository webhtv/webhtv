package com.fongmi.android.tv.compat;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Android 7 (API 24/25) contracts for API 26/34 platform calls that have an API 1 compatible form.
 *
 * <p>Two defects were found on a real Android 7 device and fixed; these guards keep the fixes from
 * being reverted silently, because neither defect can be reproduced by a JVM unit test: the JVM
 * still exposes {@code Process#isAlive()} and the {@code StringBuilder} matcher overloads.
 */
public class Android7ApiCompatContractTest {

    private static final Pattern MATCHER_APPEND = Pattern.compile(
            "\\.(appendReplacement|appendTail)\\s*\\(\\s*([A-Za-z_$][\\w$]*)");
    private static final Pattern DECLARATION = Pattern.compile("\\b(StringBuilder|StringBuffer)\\s+([A-Za-z_$][\\w$]*)\\b");

    @Test
    public void autofillCallsGoThroughTheApiIsolatedCompatLayer() throws Exception {
        for (String file : new String[]{"GitCloudDialog.java", "RemoteTrustDialog.java"}) {
            String source = stripComments(read("app/src/main/java/com/fongmi/android/tv/ui/dialog/" + file));
            assertTrue(file + " must import androidx.core.view.ViewCompat",
                    source.contains("import androidx.core.view.ViewCompat;"));
            assertEquals(file + " must route the API 26 call through the compat layer exactly once",
                    1, occurrences(source, ".setImportantForAutofill("));
            assertTrue(file + " must call ViewCompat.setImportantForAutofill (no-op below API 26)",
                    source.contains("ViewCompat.setImportantForAutofill("));
        }
    }

    @Test
    public void noProductionSourceAppendsMatcherOutputIntoAStringBuilder() throws Exception {
        List<String> violations = new ArrayList<>();
        int inspected = 0;
        try (Stream<Path> files = Files.walk(mainJavaRoot())) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                String source = read(file);
                Matcher call = MATCHER_APPEND.matcher(source);
                while (call.find()) {
                    inspected++;
                    String variable = call.group(2);
                    Set<String> types = new HashSet<>();
                    Matcher declaration = DECLARATION.matcher(source);
                    while (declaration.find()) {
                        if (declaration.group(2).equals(variable)) types.add(declaration.group(1));
                    }
                    if (types.contains("StringBuilder") && !types.contains("StringBuffer")) {
                        violations.add(file + " -> " + variable);
                    }
                }
            }
        }
        assertTrue("the scan must actually inspect the repo matcher appends (found " + inspected + ")",
                inspected >= 30);
        assertEquals("Matcher#appendReplacement/appendTail(StringBuilder, ...) needs API 34: " + violations,
                0, violations.size());
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\n]*", " ");
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

    private static Path mainJavaRoot() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static String read(String name) throws Exception {
        return read(Path.of(name));
    }

    private static String read(Path path) throws Exception {
        if (!Files.exists(path)) path = Path.of("..").resolve(path).normalize();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
