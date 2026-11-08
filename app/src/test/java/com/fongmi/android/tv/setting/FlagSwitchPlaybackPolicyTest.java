package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlagSwitchPlaybackPolicyTest {

    @Test
    public void disabledToggleNeverDefersPlayback() {
        assertFalse(FlagSwitchPlaybackPolicy.shouldDeferPlayback(false, false));
        assertFalse(FlagSwitchPlaybackPolicy.shouldDeferPlayback(false, true));
    }

    @Test
    public void enabledToggleDefersManualLineSwitchOnly() {
        assertTrue(FlagSwitchPlaybackPolicy.shouldDeferPlayback(true, false));
        assertFalse(FlagSwitchPlaybackPolicy.shouldDeferPlayback(true, true));
    }
}
