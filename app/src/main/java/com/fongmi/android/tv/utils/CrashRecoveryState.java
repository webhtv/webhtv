package com.fongmi.android.tv.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/** Recovery decisions and locked durable consumption, independent of Android and debug settings. */
final class CrashRecoveryState {
    record Run(String id, int pid, long started, String main) { }
    record Recovery(String eventId, int pid, String details) { }
    record Exit(String process, int pid, long timestamp, int reason, int status, String description, String runId) { }

    private final File file;
    CrashRecoveryState(File file) { this.file = file; }

    static Exit latestMain(List<Exit> exits, String main) {
        Exit latest = null;
        if (exits == null || main == null) return null;
        for (Exit exit : exits) {
            if (exit != null && main.equals(exit.process()) && (latest == null || exit.timestamp() > latest.timestamp())) latest = exit;
        }
        return latest;
    }

    static boolean confirmed(int reason) { return reason == 4 || reason == 5 || reason == 6; }

    boolean recordJava(Run run, String event, long timestamp, String details) {
        try {
            return transaction(state -> {
                state.setProperty("java.event", safe(event, 128));
                state.setProperty("java.run", run.id());
                state.setProperty("java.main", run.main());
                state.setProperty("java.pid", Integer.toString(run.pid()));
                state.setProperty("java.start", Long.toString(run.started()));
                state.setProperty("java.time", Long.toString(timestamp));
                state.setProperty("java.end", "0");
                state.setProperty("java.consumed", "false");
                state.setProperty("java.details", "# Java fatal recovery\nprocess=" + run.main() + "\npid=" + run.pid()
                        + "\ntimestamp=" + timestamp + "\nreason=JAVA_FATAL\n" + safe(details, 8192));
                return true;
            });
        } catch (Exception ignored) {
            return false;
        }
    }

    boolean acknowledge(String event) {
        if (event == null || event.isEmpty()) return false;
        try {
            return transaction(state -> {
                if (!event.equals(state.getProperty("java.event"))) return false;
                state.setProperty("java.consumed", "true");
                return true;
            });
        } catch (Exception ignored) {
            return false;
        }
    }

    Recovery begin(Run run, int api, List<Exit> exits) {
        try {
            return transaction(state -> {
                if (run.id().equals(state.getProperty("run"))) return null;
                state.setProperty("run", run.id());
                long javaEnd = number(state, "java.end", 0);
                if (javaEnd == 0 && state.getProperty("java.event") != null) {
                    javaEnd = run.started();
                    state.setProperty("java.end", Long.toString(javaEnd));
                }
                Exit latest = api >= 30 ? latestMain(exits, run.main()) : null;
                String identity = latest == null ? "" : exitIdentity(latest);
                List<String> seen = seen(state);
                boolean fresh = latest != null && latest.timestamp() < run.started() && !seen.contains(identity);
                if (latest != null) {
                    if (!seen.contains(identity)) seen.add(identity);
                    while (seen.size() > 64) seen.remove(0);
                    state.setProperty("seen.events", String.join("\n", seen));
                }
                String javaEvent = pendingJava(state, run.main());
                if (javaEvent != null) {
                    state.setProperty("java.consumed", "true");
                    return new Recovery(javaEvent, (int) number(state, "java.pid", 0), state.getProperty("java.details", ""));
                }
                if (!fresh || !confirmed(latest.reason())) return null;
                boolean sameJavaRun;
                if (latest.runId() != null && !latest.runId().isEmpty()) {
                    sameJavaRun = latest.runId().equals(state.getProperty("java.run"));
                } else {
                    sameJavaRun = latest.pid() == number(state, "java.pid", -1)
                            && latest.timestamp() >= number(state, "java.start", Long.MAX_VALUE)
                            && latest.timestamp() < javaEnd;
                }
                if (sameJavaRun) return null;
                return new Recovery(identity, latest.pid(), exitDetails(latest));
            });
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Unconsumed Java fact written by decorateJavaCrashIntent; consumed once the screen reports it. */
    private static String pendingJava(Properties state, String main) {
        if ("true".equals(state.getProperty("java.consumed"))) return null;
        if (!main.equals(state.getProperty("java.main"))) return null;
        String event = state.getProperty("java.event");
        String pid = state.getProperty("java.pid");
        if (event == null || event.isEmpty() || pid == null) return null;
        return event;
    }

    private static List<String> seen(Properties state) {
        String events = state.getProperty("seen.events", "");
        return events.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(events.split("\n")));
    }

    private static String exitIdentity(Exit exit) {
        return "exit:" + safe(exit.process(), 256) + ":" + exit.pid() + ":" + exit.timestamp()
                + ":" + exit.reason() + ":" + safe(exit.runId(), 128);
    }

    private static String exitDetails(Exit exit) {
        String reason = switch (exit.reason()) { case 4 -> "CRASH"; case 5 -> "CRASH_NATIVE"; case 6 -> "ANR"; default -> "UNKNOWN"; };
        return "# previous-process-recovery v1\nprocess=" + exit.process() + "\npid=" + exit.pid()
                + "\ntimestamp=" + exit.timestamp() + "\nreason=" + reason + "\nstatus=" + exit.status()
                + "\ndescription=" + safe(exit.description(), 2048)
                + "\n# Confirmed platform exit; not a report from the current process.\n"
                + "# Native/ANR trace unavailable or binary; no native trace parsing performed.\n";
    }

    private interface Change<T> { T apply(Properties state) throws IOException; }

    private <T> T transaction(Change<T> change) throws IOException {
        synchronized (CrashRecoveryState.class) {
            File directory = file.getParentFile();
            if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) throw new IOException("state directory unavailable");
            try (RandomAccessFile lockFile = new RandomAccessFile(file.getPath() + ".lock", "rw");
                 FileLock lock = lockFile.getChannel().tryLock()) {
                if (lock == null) throw new IOException("state busy");
                Properties state = read();
                T result = change.apply(state);
                save(state);
                return result;
            }
        }
    }

    private Properties read() throws IOException {
        File backup = new File(file.getPath() + ".bak");
        File source = backup.exists() ? backup : file;
        Properties state = new Properties();
        if (!source.exists()) return state;
        if (source.length() > 128 * 1024) throw new IOException("oversized state");
        try (FileInputStream input = new FileInputStream(source)) { state.load(input); }
        if (!"1".equals(state.getProperty("version"))) throw new IOException("invalid state");
        return state;
    }

    private void save(Properties state) throws IOException {
        state.setProperty("version", "1");
        File temporary = new File(file.getPath() + ".new");
        File backup = new File(file.getPath() + ".bak");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            state.store(output, null);
            output.getFD().sync();
        }
        if (backup.exists()) {
            if (file.exists() && !file.delete()) throw new IOException("cannot restore state");
        } else if (file.exists() && !file.renameTo(backup)) throw new IOException("cannot preserve state");
        if (!temporary.renameTo(file)) throw new IOException("cannot publish state");
        if (backup.exists() && !backup.delete()) throw new IOException("cannot finalize state");
    }

    private static long number(Properties state, String key, long fallback) {
        String value = state.getProperty(key);
        return value == null ? fallback : Long.parseLong(value);
    }

    private static String safe(String value, int limit) {
        if (value == null) return "";
        return value.substring(0, Math.min(value.length(), limit)).replace('\r', ' ');
    }
}
