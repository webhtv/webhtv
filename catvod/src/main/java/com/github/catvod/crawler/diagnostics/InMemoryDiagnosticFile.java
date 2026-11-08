package com.github.catvod.crawler.diagnostics;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;

/**
 * Bounded, memory-only persistence used by a crash session.
 *
 * <p>The diagnostics files in cacheDir/filesDir belong to the process that writes them. A crash
 * screen runs in {@code :error_activity} while the main process may still be alive, so a temporary
 * session must not become a second disk writer; the report and the logcat tail only need to live
 * as long as the crash screen does.
 */
public final class InMemoryDiagnosticFile implements DiagnosticLogBuffer.Persistence {

    private static final int BUDGET_BYTES = 4 << 20;
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private List<String> pinned = List.of();
    private int bytes;

    @Override
    public synchronized void clear() throws IOException {
        lines.clear();
        pinned = List.of();
        bytes = 0;
    }

    @Override
    public synchronized List<String> restore(int maxBytes) throws IOException {
        return List.of();
    }

    @Override
    public synchronized void append(List<String> added, List<String> pinnedLines) throws IOException {
        if (pinnedLines != null) pinned = List.copyOf(pinnedLines);
        if (added == null) return;
        for (String line : added) {
            if (line == null || line.isEmpty()) continue;
            int cost = cost(line);
            lines.addLast(line);
            bytes += cost;
        }
        while (bytes > BUDGET_BYTES && !lines.isEmpty()) bytes -= cost(lines.removeFirst());
    }

    @Override
    public synchronized DiagnosticLogBuffer.Export export(String header, List<String> pinnedLines) throws IOException {
        StringBuilder text = new StringBuilder(header == null ? "" : header);
        text.append("# Pinned snapshots and cached capture window (may also occur in timeline)\n");
        for (String line : pinnedLines == null ? List.<String>of() : pinnedLines) text.append(line).append('\n');
        text.append("# Retained timeline\n");
        for (String line : lines) text.append(line).append('\n');
        byte[] data = text.toString().getBytes(StandardCharsets.UTF_8);
        return new DiagnosticLogBuffer.Export(new ByteArrayInputStream(data), data.length, true,
                () -> new ByteArrayInputStream(data));
    }

    @Override
    public synchronized long bytes() {
        return bytes;
    }

    private static int cost(String line) {
        return line.getBytes(StandardCharsets.UTF_8).length + 1;
    }
}
