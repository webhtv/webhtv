package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class PreviousProcessCrashRecoveryTest {

    private static CrashRecoveryState.Exit exit(String process, int pid, long timestamp, int reason, String runId) {
        return new CrashRecoveryState.Exit(process, pid, timestamp, reason, 0, "record", runId);
    }

    @Test
    public void selectsLatestActualMainThenClassifiesOnlyConfirmedDeaths() {
        CrashRecoveryState.Exit crash = new CrashRecoveryState.Exit("actual.main", 42, 100, 4, 0, "fatal", "run1");
        CrashRecoveryState.Exit errorProcess = new CrashRecoveryState.Exit("actual.main:error_activity", 43, 200, 4, 0, "error page", "");
        assertSame(crash, CrashRecoveryState.latestMain(List.of(errorProcess, crash), "actual.main"));
        for (int reason : new int[]{4, 5, 6}) assertTrue("CRASH/CRASH_NATIVE/ANR", CrashRecoveryState.confirmed(reason));
        for (int reason : new int[]{0, 1, 2, 3, 7, 8, 9, 10, 11, 12, 13, 14}) assertFalse("not confirmed: " + reason, CrashRecoveryState.confirmed(reason));
        CrashRecoveryState.Exit normal = new CrashRecoveryState.Exit("actual.main", 44, 300, 1, 0, "normal", "run2");
        assertSame(normal, CrashRecoveryState.latestMain(List.of(crash, normal, errorProcess), "actual.main"));
        assertFalse(CrashRecoveryState.confirmed(CrashRecoveryState.latestMain(List.of(crash, normal), "actual.main").reason()));
        assertNull(CrashRecoveryState.latestMain(List.of(errorProcess), "actual.main"));
    }

    @Test
    public void onlyAPublishedRunIdCanProveThePlatformRecordIsTheJavaDeath() {
        assertTrue(CrashRecoveryState.sameProcessRun(exit("actual.main", 10, 100, 4, "run-1"), "run-1"));
        assertFalse("no summary: the death has no provable identity",
                CrashRecoveryState.sameProcessRun(exit("actual.main", 10, 100, 4, ""), "run-1"));
        assertFalse(CrashRecoveryState.sameProcessRun(exit("actual.main", 10, 100, 4, null), "run-1"));
        assertFalse("a different run is a different death",
                CrashRecoveryState.sameProcessRun(exit("actual.main", 10, 100, 4, "run-2"), "run-1"));
        assertFalse("a missing java run cannot be identified either",
                CrashRecoveryState.sameProcessRun(exit("actual.main", 10, 100, 4, "run-1"), null));
    }
}
