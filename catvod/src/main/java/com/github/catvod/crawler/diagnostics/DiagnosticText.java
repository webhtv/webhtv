package com.github.catvod.crawler.diagnostics;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The common boundary for untrusted legacy/native text. Structured fields also use it. */
public final class DiagnosticText {
    public static final int MAX_CHARS = 12_000;
    private static final Pattern URI_TEXT = Pattern.compile("(?i)(?:[a-z][a-z0-9+.-]{1,24}:)?//[^\\s<>\"'\\\\]+|(?:file|content|magnet):[^\\s<>\"']+");

    private DiagnosticText() {}

    public record Clean(String text, boolean truncated) {}

    public static Clean clean(String input) {
        if (input == null) return new Clean("", false);
        boolean truncated = input.length() > MAX_CHARS;
        String value = input.substring(0, Math.min(MAX_CHARS, input.length())).replace("\\/", "/");
        // 与 fish 一致：调试日志只给自己看，不打码，保留完整路径、链接与参数，否则无法排查问题。
        String escaped = escape(value);
        truncated |= escaped.length() >= MAX_CHARS;
        return new Clean(truncated ? escaped + " [truncated]" : escaped, truncated);
    }

    /**
     * Short label text: escaped, and capped at {@link #MAX_CHARS} like {@link #clean}.
     * Plain labels without special characters skip the escape scan fast path.
     */
    public static String sanitizeTag(String tag) {
        if (tag == null) return "";
        String value = tag.length() > MAX_CHARS ? tag.substring(0, MAX_CHARS) : tag;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/' || c == ':' || c == '=' || c == '@' || c == '.' || c <= ' ') return clean(value).text();
        }
        return escape(value);
    }

    private static String escape(String value) {
        int limit = Math.min(MAX_CHARS, value.length());
        boolean needed = false;
        for (int i = 0; i < limit; i++) {
            if (isEscapedControl(value.charAt(i))) { needed = true; break; }
        }
        if (!needed) return value.length() <= MAX_CHARS ? value : value.substring(0, MAX_CHARS);
        StringBuilder result = new StringBuilder(limit);
        for (int i = 0; i < limit && result.length() < MAX_CHARS; i++) {
            char c = value.charAt(i);
            if (c == '\n') result.append("\\n");
            else if (c == '\r') result.append("\\r");
            else if (c == '\t') result.append("\\t");
            else if (isEscapedControl(c)) result.append('?');
            else result.append(c);
        }
        return result.toString();
    }

    private static boolean isEscapedControl(char c) {
        return Character.isISOControl(c) || c == '\u2028' || c == '\u2029' || (c >= '\u202a' && c <= '\u202e');
    }

    /** Domain suggestions are local memory only, never part of an exported event. */
    public static List<String> origins(String input) {
        if (input == null) return List.of();
        String value = input.substring(0, Math.min(MAX_CHARS, input.length())).replace("\\/", "/");
        if (value.indexOf('/') < 0 && value.indexOf(':') < 0) return List.of();
        Matcher matcher = URI_TEXT.matcher(value);
        List<String> result = new ArrayList<>();
        while (matcher.find() && result.size() < 16) {
            try {
                String candidate = matcher.group();
                URI uri = URI.create(candidate.startsWith("//") ? "https:" + candidate : candidate);
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (host != null && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                    result.add(scheme.toLowerCase(java.util.Locale.ROOT) + "://" + host + (uri.getPort() < 0 ? "" : ":" + uri.getPort()));
                }
            } catch (IllegalArgumentException ignored) {
                // Malformed input is neither a diagnostic failure nor a proxy rule.
            }
        }
        return result;
    }

    public static String throwable(Throwable error) {
        StringBuilder result = new StringBuilder();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        appendThrowable(error, "", 0, visited, result);
        return clean(result.toString()).text();
    }

    private static void appendThrowable(Throwable error, String relation, int depth, Set<Throwable> visited, StringBuilder out) {
        if (error == null) return;
        if (depth >= 8 || visited.size() >= 16 || out.length() >= MAX_CHARS || !visited.add(error)) {
            out.append("\n[truncated exception chain/cycle]");
            return;
        }
        out.append(relation).append(error.getClass().getName()).append(": ").append(clean(error.getMessage()).text());
        StackTraceElement[] stack = error.getStackTrace();
        for (int i = 0; i < Math.min(stack.length, 48) && out.length() < MAX_CHARS; i++) out.append("\n at ").append(stack[i]);
        if (stack.length > 48) out.append("\n[truncated stack]");
        Throwable[] suppressed = error.getSuppressed();
        for (int i = 0; i < Math.min(suppressed.length, 4); i++) appendThrowable(suppressed[i], "\nSuppressed: ", depth + 1, visited, out);
        if (suppressed.length > 4) out.append("\n[truncated suppressed]");
        appendThrowable(error.getCause(), "\nCaused by: ", depth + 1, visited, out);
    }
}
