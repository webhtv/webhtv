package com.fongmi.android.tv.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

public class CrashRecoveryStateTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private CrashRecoveryState.Run run(String id, int pid, long started) {
        return new CrashRecoveryState.Run(id, pid, started, "actual.main");
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
        assertNotNull(state.begin(run("first", 20, 300_000), 30,
                List.of(new CrashRecoveryState.Exit("actual.main", 10, 200_000, 4, 0, "first", "one"))));
        assertNotNull("event identities, not a monotonic wall-clock cursor, control consumption",
                state.begin(run("second", 30, 150_000), 30,
                        List.of(new CrashRecoveryState.Exit("actual.main", 20, 100_000, 5, 0, "clock rollback", "two"))));
    }

    @Test
    public void acknowledgedFutureJavaStillRecovers() throws Exception {
        File file = new File(temporary.newFolder(), "state");
        CrashRecoveryState store = new CrashRecoveryState(file);
        assertTrue(store.recordJava(run("fourth", 40, 400_000), "java3", 450_000, "future fatal"));
        assertNotNull(new CrashRecoveryState(file).begin(run("fifth", 50, 500_000), 29, List.of()));
    }
}
