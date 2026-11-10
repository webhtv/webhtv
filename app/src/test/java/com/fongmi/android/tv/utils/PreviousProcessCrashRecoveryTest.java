package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class PreviousProcessCrashRecoveryTest {
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
}
