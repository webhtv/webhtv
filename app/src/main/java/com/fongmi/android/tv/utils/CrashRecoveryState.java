package com.fongmi.android.tv.utils;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
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

    /**
     * One claim on a confirmed death. {@code eventId} is durable before the claim is returned, so a
     * process that dies before the crash screen opened it hands the same claim over on the next
     * launch. Only {@link #acknowledge(String)} proves the screen displayed it.
     */
    record Recovery(String eventId, int pid, String details) { }

    /**
     * One platform exit record. {@code runId} is the process state summary published by the run that
     * died and is the only cross-channel identity this platform offers: a reused pid, a wall-clock
     * window or a reason code cannot attribute a record to another death. An empty runId still proves
     * a death, so it is reported as its own event rather than guessed into an existing one.
     */
    record Exit(String process, int pid, long timestamp, int reason, int status, String description, String runId) { }

    private final File file;
    private File selectedGeneration;
    CrashRecoveryState(File file) { this.file = file; }

    static Exit latestMain(List<Exit> exits, String main) {
        Exit latest = null;
        if (exits == null || main == null) return null;
        for (Exit exit : exits) {
            if (exit != null && main.equals(exit.process()) && (latest == null || exit.timestamp() > latest.timestamp())) latest = exit;
        }
        return latest;
    }

    private static final int REASON_CRASH = 4;
    private static final int REASON_CRASH_NATIVE = 5;
    private static final int REASON_ANR = 6;

    static boolean confirmed(int reason) { return reason == REASON_CRASH || reason == REASON_CRASH_NATIVE || reason == REASON_ANR; }

    /** True only when the run that published this exit is provably the persisted Java run. */
    static boolean sameProcessRun(Exit exit, String javaRun) {
        String runId = exit == null ? null : exit.runId();
        return runId != null && !runId.isEmpty() && runId.equals(javaRun);
    }

    boolean recordJava(Run run, String event, long timestamp, String details) {
        try {
            return transaction(state -> {
                String text = "# Java fatal recovery\nevent=" + safe(event,128) + "\nrun=" + run.id()
                        + "\nprocess=" + run.main() + "\npid=" + run.pid() + "\ntimestamp=" + timestamp
                        + "\nreason=JAVA_FATAL\n" + safe(details,8192);
                try (Events events = new Events()) {
                    migrate(state, events);
                    events.add(new Recovery(event,run.pid(),text),false);
                }
                state.setProperty("java.event",event);
                state.setProperty("java.run",run.id());
                state.setProperty("java.main",run.main());
                state.setProperty("java.pid",Integer.toString(run.pid()));
                state.setProperty("java.consumed","true");
                state.setProperty("java.details",text);
                return true;
            });
        } catch (Exception ignored) { return false; }
    }

    boolean acknowledge(String event) {
        if(event == null || event.isEmpty()) return false;
        try {
            return transaction(state -> {
                try(Events events = new Events()) {
                    migrate(state,events);
                    return events.acknowledge(event);
                }
            });
        } catch(Exception ignored) { return false; }
    }

    Recovery begin(Run run,int api,List<Exit> exits) {
        try {
            return transaction(state -> {
                if(run.id().equals(state.getProperty("run"))) return null;
                try(Events events = new Events()) {
                    migrate(state,events);
                    Exit latest = api >= 30 ? latestMain(exits,run.main()) : null;
                    if(latest != null && confirmed(latest.reason())) {
                        boolean javaRun = sameProcessRun(latest,state.getProperty("java.run"))
                                || (latest.runId() != null && !latest.runId().isEmpty()
                                && events.find("java:"+latest.runId()) != null);
                        String identity=exitIdentity(latest);
                        if(!javaRun && !seen(state).contains(identity))
                            events.add(new Recovery(identity,latest.pid(),exitDetails(latest)),false);
                    }
                    // Observe and persist the latest confirmed fact BEFORE choosing an older delivery.
                    // The journal itself deduplicates observations, including consumed records.
                    state.setProperty("run",run.id());
                    return events.firstPending();
                }
            });
        } catch(Exception ignored) { return null; }
    }

    private void migrate(Properties state,Events events) throws IOException {
        String held=state.getProperty("handoff.event");
        if(held != null && !held.isEmpty()) {
            events.add(new Recovery(held,(int)number(state,"handoff.pid",0),state.getProperty("handoff.details","")),false);
            state.remove("handoff.event"); state.remove("handoff.pid"); state.remove("handoff.details");
        }
        String java=state.getProperty("java.event");
        if(java != null && !java.isEmpty()) {
            events.add(new Recovery(java,(int)number(state,"java.pid",0),state.getProperty("java.details","")),
                    "true".equals(state.getProperty("java.consumed")));
            state.setProperty("java.consumed","true");
        }
    }

    /** Append-only event ledger, under the state's file lock. Memory is bounded to one record;
     * retained identities have no count cap, so neither queue overflow nor a later SIGNALLED exit
     * can discard an already observed death. A partial final append is never a committed record. */
    private final class Events implements AutoCloseable {
        private static final int COMMIT=0x43524153;
        private static final int MAX_RECORD=32768;
        private final RandomAccessFile data;
        private long validEnd;
        private long foundFlag;
        private boolean foundConsumed;

        Events() throws IOException { data=new RandomAccessFile(file.getPath()+".events","rw"); }

        Recovery find(String identity) throws IOException { return scan(identity,false); }
        Recovery firstPending() throws IOException { return scan(null,true); }

        private Recovery scan(String identity,boolean pending) throws IOException {
            data.seek(0); validEnd=0;
            Recovery selected=null;
            while(data.getFilePointer()<data.length()) {
                long start=data.getFilePointer();
                if(data.length()-start<4) break;
                int length=data.readInt();
                if(length<1 || length>MAX_RECORD) throw new IOException("invalid event size");
                if(data.length()-data.getFilePointer()<length+5L) break;
                long flag=data.getFilePointer();
                int consumed=data.readUnsignedByte();
                byte[] payload=new byte[length]; data.readFully(payload);
                if(data.readInt()!=COMMIT || consumed>1) throw new IOException("invalid event record");
                validEnd=data.getFilePointer();
                Recovery recovery;
                try(DataInputStream input=new DataInputStream(new ByteArrayInputStream(payload))) {
                    recovery=new Recovery(input.readUTF(),input.readInt(),input.readUTF());
                    if(input.available()!=0) throw new IOException("trailing event data");
                }
                if(selected==null && (pending ? consumed==0 : recovery.eventId().equals(identity))) {
                    selected=recovery; foundFlag=flag; foundConsumed=consumed==1;
                }
            }
            return selected;
        }

        void add(Recovery recovery,boolean consumed) throws IOException {
            if(find(recovery.eventId())!=null) return;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(DataOutputStream output=new DataOutputStream(bytes)) {
                output.writeUTF(recovery.eventId()); output.writeInt(recovery.pid());
                output.writeUTF(safe(recovery.details(),8192));
            }
            byte[] payload=bytes.toByteArray();
            if(payload.length>MAX_RECORD) throw new IOException("oversized event");
            // Only an incomplete tail can be removed; all committed records stay untouched.
            data.setLength(validEnd); data.seek(validEnd);
            data.writeInt(payload.length); data.writeByte(consumed ? 1 : 0);
            data.write(payload); data.writeInt(COMMIT); data.getFD().sync();
        }

        boolean acknowledge(String event) throws IOException {
            if(find(event)==null) return false;
            if(!foundConsumed) {
                data.seek(foundFlag); data.writeByte(1); data.getFD().sync();
            }
            return true;
        }

        @Override public void close() throws IOException { data.close(); }
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
        String reason = switch (exit.reason()) {
            case REASON_CRASH -> "CRASH";
            case REASON_CRASH_NATIVE -> "CRASH_NATIVE";
            case REASON_ANR -> "ANR";
            default -> "UNKNOWN";
        };
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

    /**
     * The newest readable generation wins.
     *
     * <p>Every save stages a complete numbered generation before the published names change, so an
     * interruption can leave the published file, the staged {@code .new} generation and the previous
     * {@code .bak} generation behind. Picking the newest instead of the first readable one is what
     * stops a death inside {@code save()} from resurrecting an older generation that still claimed
     * the event was unconsumed. Damaged generations are skipped; when none is readable nothing is
     * recovered and nothing is overwritten, so a corrupt state fails open instead of replaying.
     */
    private Properties read() throws IOException {
        Properties newest = null;
        selectedGeneration = null;
        long newestGen = Long.MIN_VALUE;
        IOException failure = null;
        for (File candidate : new File[]{file, new File(file.getPath() + ".new"), new File(file.getPath() + ".bak")}) {
            if (!candidate.exists()) continue;
            Properties state;
            try {
                state = load(candidate);
            } catch (IOException unreadable) {
                if (failure == null) failure = unreadable;
                continue;
            }
            long gen = number(state, "gen", 0);
            if (newest == null || gen > newestGen) {
                newest = state;
                selectedGeneration = candidate;
                newestGen = gen;
            }
        }
        if (newest != null) return newest;
        if (failure != null) throw failure;
        return new Properties();
    }

    private static Properties load(File source) throws IOException {
        if (source.length() > 128 * 1024) throw new IOException("oversized state");
        Properties state = new Properties();
        try (FileInputStream input = new FileInputStream(source)) {
            state.load(input);
        } catch (IllegalArgumentException malformed) {
            // A damaged code point in the file makes Properties.load throw IllegalArgumentException,
            // which is not an IOException; normalize it so the generation fallback above still applies.
            throw new IOException("malformed state", malformed);
        }
        if (!"1".equals(state.getProperty("version"))) throw new IOException("invalid state");
        try {
            long gen=number(state,"gen",0);
            if(gen<0 || gen==Long.MAX_VALUE) throw new NumberFormatException("invalid generation");
        } catch(NumberFormatException invalid) { throw new IOException("invalid generation",invalid); }
        return state;
    }

    private void save(Properties state) throws IOException {
        long generation = number(state, "gen", 0);
        if (generation < 0 || generation >= Long.MAX_VALUE - 1) throw new IOException("state generation exhausted");
        state.setProperty("version", "1");
        state.setProperty("gen", Long.toString(generation + 1));
        File temporary = new File(file.getPath() + ".tmp");
        File staged = new File(file.getPath() + ".new");
        File backup = new File(file.getPath() + ".bak");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            state.store(output, null);
            output.getFD().sync();
        }
        publish(temporary, staged, file, backup, selectedGeneration);
    }

    static void publish(File temporary,File staged,File file,File backup,File selected) throws IOException {
        // Preserve the actual readable source, never rotate a merely existing corrupt generation.
        if(staged.exists() && !staged.equals(selected) && !staged.delete()) throw new IOException("cannot clear corrupt or older staged state");
        if(selected != null && !selected.equals(backup)) {
            if(backup.exists() && !backup.delete()) throw new IOException("cannot rotate backup");
            if(!selected.renameTo(backup)) throw new IOException("cannot preserve selected state");
        }
        if(!temporary.renameTo(staged)) throw new IOException("cannot stage state");
        if(file.exists() && !file.delete()) throw new IOException("cannot replace published state");
        if(!staged.renameTo(file)) throw new IOException("cannot publish state");
        if(backup.exists()) backup.delete();
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
