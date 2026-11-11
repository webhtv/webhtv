package com.fongmi.android.tv.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

public class CrashRecoveryStateTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private CrashRecoveryState.Run run(String id, int pid, long started) {
        return new CrashRecoveryState.Run(id, pid, started, "actual.main");
    }

    private static CrashRecoveryState.Exit exit(int pid, long timestamp, int reason, String runId) {
        return new CrashRecoveryState.Exit("actual.main", pid, timestamp, reason, 0, "fatal", runId);
    }

    /** One persisted generation, written exactly the way save() shapes it. */
    private static void generation(File file, long gen, long start, long time, String event, boolean consumed) throws Exception {
        Files.writeString(file.toPath(), String.join("\n",
                "version=1",
                "gen=" + gen,
                "run=seen",
                "java.event=" + event,
                "java.main=actual.main",
                "java.pid=10",
                "java.start=" + start,
                "java.time=" + time,
                "java.consumed=" + consumed,
                "java.run=old",
                "java.details=# Java fatal recovery",
                ""), StandardCharsets.UTF_8);
    }

    @Test
    public void consumesConfirmedDeathBeforeHandoffAcrossStoreInstances() throws Exception {
        for (int reason : new int[]{4, 5, 6}) {
            File file = new File(temporary.newFolder(), "state");
            List<CrashRecoveryState.Exit> exits = List.of(new CrashRecoveryState.Exit("actual.main", 10, 100_000, reason, 0, "fatal", "old"));
            CrashRecoveryState.Recovery recovery = new CrashRecoveryState(file).begin(run("next", 20, 200_000), 30, exits);
            assertNotNull("confirmed reason " + reason, recovery);
            assertEquals(10, recovery.pid());
            assertTrue(recovery.details().contains("pid=10"));
            assertTrue("the screen confirmed this event", new CrashRecoveryState(file).acknowledge(recovery.eventId()));
            assertNull(new CrashRecoveryState(file).begin(run("restart", 30, 300_000), 30, exits));
        }
    }

    @Test
    public void oldAndroidRecoversOnlyExplicitJavaAndNeverReplaysShownEvent() throws Exception {
        for (int api : new int[]{24, 29}) {
            File file = new File(temporary.newFolder(), "state");
            CrashRecoveryState store = new CrashRecoveryState(file);
            List<CrashRecoveryState.Exit> older = List.of(new CrashRecoveryState.Exit("actual.main", 5, 90_000, 5, 0, "native", "older"));
            assertNull(store.begin(run("first", 10, 100_000), api, older));
            assertTrue(store.recordJava(run("first", 10, 100_000), "java1", 150_000, "current Java stack"));
            CrashRecoveryState.Recovery recovery = new CrashRecoveryState(file).begin(run("second", 20, 200_000), api, List.of());
            assertNotNull(recovery);
            assertEquals(10, recovery.pid());
            assertTrue(recovery.details().contains("current Java stack"));
            assertTrue(new CrashRecoveryState(file).acknowledge("java1"));
            assertNull(new CrashRecoveryState(file).begin(run("third", 30, 300_000), api, List.of()));
            assertTrue(store.recordJava(run("third", 30, 300_000), "java2", 350_000, "next fatal"));
            assertFalse(new CrashRecoveryState(file).acknowledge("unrelated"));
            assertTrue(new CrashRecoveryState(file).acknowledge("java2"));
            assertNull(new CrashRecoveryState(file).begin(run("fourth", 40, 400_000), api, List.of()));
        }
    }

    @Test
    public void javaAndDelayedPlatformHistoryAreOneEventIncludingPidReuse() throws Exception {
        File file = new File(temporary.newFolder(), "state");
        CrashRecoveryState store = new CrashRecoveryState(file);
        assertNull(store.begin(run("old", 10, 1_000_000), 30, List.of()));
        assertTrue(store.recordJava(run("old", 10, 1_000_000), "java", 1_000_150, "fatal stack"));
        assertTrue(new CrashRecoveryState(file).acknowledge("java"));
        assertNull(new CrashRecoveryState(file).begin(run("next", 20, 1_000_200), 30, List.of()));
        // The published run id proves this platform record is the Java death already shown.
        List<CrashRecoveryState.Exit> delayed = List.of(new CrashRecoveryState.Exit("actual.main", 10, 1_000_160, 4, 0, "fatal", "old"));
        assertNull(new CrashRecoveryState(file).begin(run("next2", 30, 1_000_300), 30, delayed));
        List<CrashRecoveryState.Exit> reusedPid = List.of(new CrashRecoveryState.Exit("actual.main", 10, 1_350_000, 5, 0, "new native", "different"));
        assertNotNull(new CrashRecoveryState(file).begin(run("future", 40, 1_400_000), 30, reusedPid));
    }

    @Test
    public void newerNormalDoesNotSearchOlderCrashAndFailuresReleaseTheGate() throws Exception {
        File file = new File(temporary.newFolder(), "state");
        assertNull(new CrashRecoveryState(file).begin(run("next", 30, 300_000), 30, List.of(
                new CrashRecoveryState.Exit("actual.main", 10, 100_000, 5, 0, "old crash", "old"),
                new CrashRecoveryState.Exit("actual.main", 20, 200_000, 10, 0, "force stop", "normal"))));
        File corrupt = new File(temporary.newFolder(), "state");
        java.nio.file.Files.writeString(corrupt.toPath(), "invalid-state");
        assertNull(new CrashRecoveryState(corrupt).begin(run("next", 30, 300_000), 30, List.of(new CrashRecoveryState.Exit("actual.main", 20, 200_000, 4, 0, "fatal", "old"))));
        File blocked = temporary.newFile();
        assertNull(new CrashRecoveryState(new File(blocked, "state")).begin(run("next", 30, 300_000), 30, List.of()));
    }

    @Test
    public void distinctNativeCrashInsideOldJavaGraceWindowIsNotSuppressed() throws Exception {
        File file = new File(temporary.newFolder(), "state");
        CrashRecoveryState state = new CrashRecoveryState(file);
        assertTrue(state.recordJava(run("java-run", 10, 100_000), "java-event", 110_000, "java fatal"));
        assertTrue(state.acknowledge("java-event"));
        assertNull(state.begin(run("next", 20, 120_000), 30, List.of()));
        CrashRecoveryState.Exit nativeCrash = new CrashRecoveryState.Exit("actual.main", 20, 130_000, 5, 0, "new native", "next");
        assertNotNull("a different run must not disappear inside a 60-second window",
                state.begin(run("third", 30, 140_000), 30, List.of(nativeCrash)));
    }

    @Test
    public void clockRollbackDoesNotHideAnotherConfirmedDeath() throws Exception {
        File file = new File(temporary.newFolder(), "state");
        CrashRecoveryState state = new CrashRecoveryState(file);
        CrashRecoveryState.Recovery first = state.begin(run("first", 20, 300_000), 30,
                List.of(new CrashRecoveryState.Exit("actual.main", 10, 200_000, 4, 0, "first", "one")));
        assertEquals("exit:actual.main:10:200000:4:one", first.eventId());
        assertTrue(state.acknowledge(first.eventId()));
        CrashRecoveryState.Recovery second = state.begin(run("second",30,150_000),30,
                List.of(new CrashRecoveryState.Exit("actual.main",20,100_000,5,0,"clock rollback","two")));
        assertEquals("exit:actual.main:20:100000:5:two",second.eventId());
        assertTrue(second.details().contains("reason=CRASH_NATIVE"));
    }

    @Test
    public void acknowledgedFutureJavaStillRecovers() throws Exception {
        File file = new File(temporary.newFolder(), "state");
        CrashRecoveryState store = new CrashRecoveryState(file);
        assertTrue(store.recordJava(run("fourth", 40, 400_000), "java3", 450_000, "future fatal"));
        assertNotNull(new CrashRecoveryState(file).begin(run("fifth", 50, 500_000), 29, List.of()));
    }

    @Test
    public void wallClockRollBackStillRecoversTheConfirmedDeath() throws Exception {
        File file = new File(temporary.newFolder(), "state");
        // The platform timestamp is later than this launch only because the wall clock moved back.
        List<CrashRecoveryState.Exit> exits = List.of(new CrashRecoveryState.Exit("actual.main", 10, 200_000, 4, 0, "clock ahead", "one"));
        CrashRecoveryState.Recovery recovery = new CrashRecoveryState(file).begin(run("next", 20, 150_000), 30, exits);
        assertNotNull("a confirmed death must not disappear when the clock moved back", recovery);
        assertTrue(new CrashRecoveryState(file).acknowledge(recovery.eventId()));
        assertNull("the same event must still be claimed only once",
                new CrashRecoveryState(file).begin(run("again", 30, 160_000), 30, exits));
    }

    @Test
    public void publishedGenerationWinsOverLeftoverBackup() throws Exception {
        File file = new File(temporary.newFolder(), "state");
        assertTrue(new CrashRecoveryState(file).recordJava(run("first", 10, 100_000), "java-old", 110_000, "old fatal"));
        assertTrue(new CrashRecoveryState(file).acknowledge("java-old"));
        // A death between the renames of save() leaves exactly this shape: the published file holds
        // the consumed generation while the untouched backup still claims the event is unconsumed.
        Files.writeString(new File(file.getPath() + ".bak").toPath(), String.join("\n",
                "version=1",
                "java.event=java-old",
                "java.main=actual.main",
                "java.pid=10",
                "java.consumed=false"), StandardCharsets.UTF_8);
        assertNull("a leftover backup must not replay a consumed event",
                new CrashRecoveryState(file).begin(run("second", 20, 120_000), 29, List.of()));
    }

    @Test
    public void summarylessNativeDeathOfAnotherRunIsNotSwallowedByAStaleJavaFact() throws Exception {
        File file = new File(temporary.newFolder(), "state");
        CrashRecoveryState store = new CrashRecoveryState(file);
        assertNull(store.begin(run("old", 10, 100_000), 30, List.of()));
        assertTrue(store.recordJava(run("old", 10, 100_000), "java:old", 110_000, "old java fatal"));
        assertTrue(new CrashRecoveryState(file).acknowledge("java:old"));
        assertNull(new CrashRecoveryState(file).begin(run("next", 20, 200_000), 30, List.of()));
        // A later run reuses pid 10 and dies natively before it can publish a process state summary.
        CrashRecoveryState.Exit reused = exit(10, 160_000, 5, "");
        CrashRecoveryState.Recovery recovery = new CrashRecoveryState(file).begin(run("third", 30, 170_000), 30, List.of(reused));
        assertNotNull("a confirmed death must not disappear because an old java fact once used the pid", recovery);
        assertEquals(10, recovery.pid());
        assertTrue(recovery.details().contains("reason=CRASH_NATIVE"));
    }

    @Test
    public void claimedRecoverySurvivesProcessDeathUntilTheScreenConfirmsIt() throws Exception {
        File file = new File(temporary.newFolder(), "state");
        List<CrashRecoveryState.Exit> exits = List.of(exit(10, 100_000, 5, "old"));
        CrashRecoveryState.Recovery first = new CrashRecoveryState(file).begin(run("next", 20, 200_000), 30, exits);
        assertNotNull(first);
        // The page never started and this process died: the claim must outlive it.
        CrashRecoveryState.Recovery again = new CrashRecoveryState(file).begin(run("next2", 30, 300_000), 30, exits);
        assertNotNull("a claimed but unconfirmed delivery must be handed over again", again);
        assertEquals(first.eventId(), again.eventId());
        assertTrue(new CrashRecoveryState(file).acknowledge(again.eventId()));
        assertNull(new CrashRecoveryState(file).begin(run("next3", 40, 400_000), 30, exits));
    }

    @Test
    public void javaFactClaimedButNeverShownIsRedeliveredAfterRestart() throws Exception {
        File file = new File(temporary.newFolder(), "state");
        assertNull(new CrashRecoveryState(file).begin(run("old", 10, 100_000), 29, List.of()));
        assertTrue(new CrashRecoveryState(file).recordJava(run("old", 10, 100_000), "java:old", 110_000, "fatal stack"));
        assertNotNull(new CrashRecoveryState(file).begin(run("next", 20, 200_000), 29, List.of()));
        CrashRecoveryState.Recovery again = new CrashRecoveryState(file).begin(run("next2", 30, 300_000), 29, List.of());
        assertNotNull("a claimed but unconfirmed Java death must not be lost with the process", again);
        assertEquals("java:old", again.eventId());
        assertTrue(again.details().contains("fatal stack"));
        assertTrue(new CrashRecoveryState(file).acknowledge("java:old"));
        assertNull(new CrashRecoveryState(file).begin(run("next3", 40, 400_000), 29, List.of()));
    }

    @Test
    public void malformedPublishedGenerationStillUsesTheReadableBackup() throws Exception {
        File file = new File(temporary.newFolder(), "state");
        generation(file, 4, 100_000, 110_000, "java:old", false);
        // A damaged unicode escape makes Properties.load throw IllegalArgumentException, which is not
        // an IOException; the previous generation still holds the death, so it must be recoverable.
        Files.writeString(file.toPath(), String.join("\n",
                "version=1",
                "gen=5",
                "note=\\uZZZZ",
                ""), StandardCharsets.UTF_8);
        generation(new File(file.getPath() + ".bak"), 4, 100_000, 110_000, "java:old", false);
        CrashRecoveryState.Recovery recovery = new CrashRecoveryState(file).begin(run("next", 20, 120_000), 29, List.of());
        assertNotNull("an unreadable generation must fall back to the readable one", recovery);
        assertEquals("java:old", recovery.eventId());
    }

    @Test
    public void interruptedPublishNeverRollsBackToAnOlderConsumption() throws Exception {
        File file = new File(temporary.newFolder(), "state");
        // Death inside save() after the acknowledging generation was staged and the previous one was
        // rotated aside: the published name is briefly absent and only .new/.bak remain.
        generation(new File(file.getPath() + ".new"), 2, 100_000, 110_000, "java:old", true);
        generation(new File(file.getPath() + ".bak"), 1, 100_000, 110_000, "java:old", false);
        assertNull("an interrupted publish must not resurrect a consumed event",
                new CrashRecoveryState(file).begin(run("next", 20, 120_000), 29, List.of()));
    }

    @Test
    public void twoGenerationRemainderKeepsTheNewestGeneration() throws Exception {
        File file = new File(temporary.newFolder(), "state");
        generation(file, 6, 100_000, 110_000, "java:old", true);
        generation(new File(file.getPath() + ".bak"), 5, 100_000, 110_000, "java:old", false);
        assertNull("the newest generation, not the file name, decides consumption",
                new CrashRecoveryState(file).begin(run("next", 20, 120_000), 29, List.of()));
    }

    @Test
    public void unreadableGenerationsFailOpenWithoutRecoveringAnything() throws Exception {
        File file = new File(temporary.newFolder(), "state");
        Files.writeString(file.toPath(), "invalid-state", StandardCharsets.UTF_8);
        Files.writeString(new File(file.getPath() + ".bak").toPath(), "note=\\uZZZZ", StandardCharsets.UTF_8);
        assertNull("damaged state must fail open, not replay a death",
                new CrashRecoveryState(file).begin(run("next", 20, 120_000), 30, List.of(exit(10, 100_000, 4, "old"))));
    }
    @Test public void observedDeathSurvivesOldPendingAndLaterSignalledHistory() throws Exception {
        File f = new File(temporary.newFolder(), "state");
        CrashRecoveryState a = new CrashRecoveryState(f);
        CrashRecoveryState.Recovery first = a.begin(run("r1",20,200),30,List.of(exit(10,100,5,"A")));
        assertEquals(first.eventId(), new CrashRecoveryState(f).begin(run("r2",30,300),30,List.of(exit(20,200,6,"B"))).eventId());
        assertTrue(a.acknowledge(first.eventId()));
        CrashRecoveryState.Recovery b = new CrashRecoveryState(f).begin(run("r3",40,400),30,List.of(exit(30,300,2,"r2")));
        assertNotNull("observed B must survive a later SIGNALLED", b);
        assertEquals("exit:actual.main:20:200:6:B", b.eventId());
        assertTrue(a.acknowledge(b.eventId()));
        assertNull(a.begin(run("r4",50,500),30,List.of(exit(40,400,2,"r3"))));
    }
    @Test public void newJavaFactsNeverOverwriteOlderUndeliveredJava() throws Exception {
        File f = new File(temporary.newFolder(), "state");
        CrashRecoveryState a = new CrashRecoveryState(f);
        assertTrue(a.recordJava(run("A",10,100),"java:A",110,"A stack"));
        assertEquals("java:A", a.begin(run("r1",20,200),29,List.of()).eventId());
        assertTrue(a.recordJava(run("B",10,300),"java:B",310,"B stack"));
        assertTrue(a.recordJava(run("C",10,400),"java:C",410,"C stack"));
        assertTrue(a.acknowledge("java:A"));
        assertEquals("java:B", a.begin(run("r2",30,500),29,List.of()).eventId());
        assertTrue(a.acknowledge("java:B"));
        assertEquals("java:C", a.begin(run("r3",40,600),29,List.of()).eventId());
        assertTrue(a.acknowledge("java:C"));
        assertNull(a.begin(run("r4",50,700),29,List.of()));
    }
    @Test public void invalidGenerationFallsBackToValidBackupOrStaged() throws Exception {
        for (String suffix : new String[]{".bak", ".new"}) {
            File f = new File(temporary.newFolder(), "state");
            Files.writeString(f.toPath(), "version=1\ngen=broken\n");
            generation(new File(f.getPath()+suffix),4,100,110,"java:valid",false);
            CrashRecoveryState.Recovery r = new CrashRecoveryState(f).begin(run("next",20,200),29,List.of());
            assertNotNull("invalid gen must not bypass valid " + suffix,r);
            assertEquals("java:valid",r.eventId());
        }
    }
    @Test public void allInvalidGenerationsFailOpenWithoutOverwriting() throws Exception {
        File f = new File(temporary.newFolder(), "state");
        for (String suffix : new String[]{"", ".bak", ".new"}) Files.writeString(new File(f.getPath()+suffix).toPath(),"version=1\ngen=broken\n");
        assertNull(new CrashRecoveryState(f).begin(run("next",20,200),30,List.of(exit(10,100,4,"A"))));
        assertEquals("version=1\ngen=broken\n",Files.readString(f.toPath()));
    }
    @Test public void failedStageWithCorruptNewPreservesSelectedBackup() throws Exception {
        for (boolean oldCurrent : new boolean[]{false,true}) {
            File f = new File(temporary.newFolder(),"state");
            if(oldCurrent) generation(f,1,100,110,"java:old",true);
            File backup = new File(f.getPath()+".bak");
            generation(backup,4,100,110,"java:valid",false);
            File staged = new File(f.getPath()+".new");
            Files.writeString(staged.toPath(),"damaged");
            File failedTemporary = new File(f.getPath()+".tmp") {
                @Override public boolean renameTo(File target) { return false; }
            };
            generation(failedTemporary,5,100,110,"java:valid",false);
            try { CrashRecoveryState.publish(failedTemporary,staged,f,backup,backup); fail("stage must fail"); }
            catch(java.io.IOException expected) { }
            CrashRecoveryState.Recovery r = new CrashRecoveryState(f).begin(run("next",20,200),29,List.of());
            assertNotNull("sole valid backup must survive failed stage",r);
            assertEquals("java:valid",r.eventId());
        }
    }
    @Test public void incompleteJournalTailDoesNotDestroyCommittedEvents() throws Exception {
        File f=new File(temporary.newFolder(),"state");
        CrashRecoveryState state=new CrashRecoveryState(f);
        assertTrue(state.recordJava(run("A",10,100),"java:A",110,"A"));
        try(java.io.RandomAccessFile data=new java.io.RandomAccessFile(f.getPath()+".events","rw")) {
            data.seek(data.length()); data.writeInt(100); data.writeByte(0); data.write(new byte[]{1,2,3});
        }
        assertEquals("java:A",state.begin(run("next",20,200),29,List.of()).eventId());
        assertTrue(state.recordJava(run("B",10,300),"java:B",310,"B"));
        assertTrue(state.acknowledge("java:A"));
        assertEquals("java:B",state.begin(run("next2",30,400),29,List.of()).eventId());
        assertTrue(state.acknowledge("java:B"));
        assertNull(state.begin(run("next3",40,500),29,List.of()));
    }
    @Test public void manyObservedDeathsAreRetainedAndAcknowledgedWithoutQueueOverflow() throws Exception {
        File f=new File(temporary.newFolder(),"state");
        CrashRecoveryState state=new CrashRecoveryState(f);
        for(int i=0;i<80;i++) assertTrue(state.recordJava(run("run"+i,10,i),"java:run"+i,i,"x".repeat(3000)));
        for(int i=0;i<80;i++) {
            assertEquals("java:run"+i,state.begin(run("host"+i,20,1000+i),29,List.of()).eventId());
            assertTrue(state.acknowledge("java:run"+i));
        }
        assertNull(state.begin(run("finished",30,2000),29,List.of()));
    }
    @Test public void exhaustedGenerationRejectsSaveWithoutDestroyingReadableSourceOrLedger() throws Exception {
        for(String suffix:new String[]{"", ".bak", ".new"}) {
            File f=new File(temporary.newFolder(),"state");
            CrashRecoveryState state=new CrashRecoveryState(f);
            assertTrue(state.recordJava(run("A",10,100),"java:A",110,"acknowledged A"));
            assertTrue(state.acknowledge("java:A"));
            assertTrue(state.recordJava(run("B",20,200),"java:B",210,"pending B"));
            java.util.Properties properties=new java.util.Properties();
            try(java.io.FileInputStream input=new java.io.FileInputStream(f)) { properties.load(input); }
            properties.setProperty("gen",Long.toString(Long.MAX_VALUE-1));
            File selected=new File(f.getPath()+suffix);
            try(java.io.FileOutputStream output=new java.io.FileOutputStream(selected)) { properties.store(output,null); output.getFD().sync(); }
            if(!suffix.isEmpty()) assertTrue(f.delete());
            byte[] source=Files.readAllBytes(selected.toPath());
            File ledger=new File(f.getPath()+".events");
            byte[] events=Files.readAllBytes(ledger.toPath());
            assertFalse("an unreadable next generation must be rejected before publication",new CrashRecoveryState(f).acknowledge("java:A"));
            assertTrue("selected readable source must not be deleted",selected.isFile());
            assertArrayEquals("rejected save must preserve selected source",source,Files.readAllBytes(selected.toPath()));
            assertArrayEquals("repeated acknowledgement must preserve event ledger",events,Files.readAllBytes(ledger.toPath()));
            assertFalse("rejected save must not stage an unreadable generation",new File(f.getPath()+".tmp").exists());
            assertNull("exhaustion fails open, not a promise of continued writes",new CrashRecoveryState(f).begin(run("next",30,300),29,List.of()));
            assertArrayEquals(source,Files.readAllBytes(selected.toPath()));
        }
    }
    @Test public void lastReadableGenerationCanBePublishedOnceAndIsNeverWrapped() throws Exception {
        File f=new File(temporary.newFolder(),"state");
        generation(f,Long.MAX_VALUE-2,100,110,"java:A",true);
        assertTrue(new CrashRecoveryState(f).acknowledge("java:A"));
        java.util.Properties properties=new java.util.Properties();
        try(java.io.FileInputStream input=new java.io.FileInputStream(f)) { properties.load(input); }
        assertEquals(Long.toString(Long.MAX_VALUE-1),properties.getProperty("gen"));
        assertFalse("the next save cannot publish MAX or wrap to negative",new CrashRecoveryState(f).acknowledge("java:A"));
        try(java.io.FileInputStream input=new java.io.FileInputStream(f)) { properties.load(input); }
        assertEquals(Long.toString(Long.MAX_VALUE-1),properties.getProperty("gen"));
    }
    private static void restoreWritableGeneration(File f) throws Exception {
        java.util.Properties properties=new java.util.Properties();
        try(java.io.FileInputStream input=new java.io.FileInputStream(f)) { properties.load(input); }
        properties.setProperty("gen","0");
        try(java.io.FileOutputStream output=new java.io.FileOutputStream(f)) { properties.store(output,null); output.getFD().sync(); }
    }
    @Test public void eventSyncedBeforeRejectedSaveSurvivesWritableGenerationRecovery() throws Exception {
        File f=new File(temporary.newFolder(),"state");
        generation(f,Long.MAX_VALUE-1,100,110,"java:A",true);
        byte[] selected=Files.readAllBytes(f.toPath());
        assertFalse(new CrashRecoveryState(f).recordJava(run("B",20,200),"java:B",210,"B survived"));
        assertArrayEquals("failed save preserves original complete generation",selected,Files.readAllBytes(f.toPath()));
        // Exhaustion rejects writes: the following recovery is test-fixture-only, not auto-reset.
        restoreWritableGeneration(f);
        CrashRecoveryState state=new CrashRecoveryState(f);
        CrashRecoveryState.Recovery b=state.begin(run("next",30,300),29,List.of());
        assertEquals("java:B",b.eventId());
        assertTrue(b.details().contains("B survived"));
        assertTrue(state.acknowledge("java:B"));
        assertNull(state.begin(run("after",40,400),29,List.of()));
    }
    @Test public void acknowledgementSyncedBeforeRejectedSaveIsNeverRevivedByMigration() throws Exception {
        File f=new File(temporary.newFolder(),"state");
        generation(f,Long.MAX_VALUE-1,100,110,"java:A",false);
        byte[] selected=Files.readAllBytes(f.toPath());
        assertFalse(new CrashRecoveryState(f).acknowledge("java:A"));
        assertArrayEquals("selected source stays unconsumed but journal ack is synced",selected,Files.readAllBytes(f.toPath()));
        restoreWritableGeneration(f);
        CrashRecoveryState state=new CrashRecoveryState(f);
        assertNull("migration must not revive a journal acknowledgement",state.begin(run("next",20,200),29,List.of()));
        assertTrue("repeat acknowledgement is idempotent",state.acknowledge("java:A"));
        assertNull(state.begin(run("after",30,300),29,List.of()));
    }
}
