package com.fongmi.android.tv.api.loader;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeBridgeGuardTest {

    @Test
    public void nativeBridgePropertyMarksTranslatedRuntime() {
        assertTrue(NativeBridgeGuard.isTranslatedRuntime("libnb.so", List.of("x86_64", "x86"), "x86_64"));
        assertFalse(NativeBridgeGuard.isTranslatedRuntime("", List.of("x86_64", "x86"), "x86_64"));
        assertFalse(NativeBridgeGuard.isTranslatedRuntime(null, List.of("x86_64", "x86"), "x86_64"));
    }

    @Test
    public void foreignAbiFamilyWithoutBridgePropertyAlsoCounts() {
        // Emulators sometimes expose the translated ABI list before the system
        // property becomes readable through reflection.
        assertTrue(NativeBridgeGuard.isTranslatedRuntime("", List.of("x86_64", "x86", "arm64-v8a", "armeabi-v7a"), "x86_64"));
        assertFalse(NativeBridgeGuard.isTranslatedRuntime("", List.of("x86_64", "x86"), "x86_64"));
        assertFalse(NativeBridgeGuard.isTranslatedRuntime("", List.of("arm64-v8a", "armeabi-v7a"), "arm64-v8a"));
    }

    @Test
    public void armPrimaryWithX86SupportIsAlsoTreatedAsTranslated() {
        assertTrue(NativeBridgeGuard.isTranslatedRuntime("", List.of("arm64-v8a", "x86_64"), "arm64-v8a"));
    }
}
