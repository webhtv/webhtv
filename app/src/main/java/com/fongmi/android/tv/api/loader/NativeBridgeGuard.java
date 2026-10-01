package com.fongmi.android.tv.api.loader;

import android.os.Build;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Detects whether this process runs translated native code (for example an
 * x86_64 emulator executing an arm64-only APK through libhoudini).
 *
 * <p>On such devices ART's {@code UnloadNativeLibraries()} calls back into the
 * binary translator. Dropping a {@code DexClassLoader} whose classes loaded
 * translated native libraries therefore crashes inside the translator from
 * {@code HeapTaskDaemon} (SIGSEGV in {@code libhoudini.so}). Reloading the VOD
 * interface replaces the config jar and used to drop exactly such a loader.
 *
 * <p>The guard is intentionally read-only and side-effect free so it can be
 * unit tested on a plain JVM.
 */
final class NativeBridgeGuard {

    private NativeBridgeGuard() {
    }

    /** True when the runtime translates native code instead of running it natively. */
    static boolean isTranslatedRuntime() {
        return isTranslatedRuntime(readNativeBridge(), safeSupportedAbis(), Build.CPU_ABI);
    }

    /**
     * Pure decision helper: a native bridge is configured, or the device claims
     * support for a foreign ABI family next to its own primary ABI.
     */
    static boolean isTranslatedRuntime(String nativeBridge, List<String> supportedAbis, String primaryAbi) {
        if (nativeBridge != null && !nativeBridge.isEmpty() && !"0".equals(nativeBridge)) return true;
        if (supportedAbis == null || supportedAbis.isEmpty()) return false;
        String primary = primaryAbi == null ? supportedAbis.get(0) : primaryAbi;
        boolean x86Primary = isX86(primary);
        boolean armPrimary = isArm(primary);
        if (!x86Primary && !armPrimary) return false;
        for (String abi : supportedAbis) {
            if (abi == null) continue;
            if (x86Primary && isArm(abi)) return true;
            if (armPrimary && isX86(abi)) return true;
        }
        return false;
    }

    private static boolean isX86(String abi) {
        return abi.startsWith("x86");
    }

    private static boolean isArm(String abi) {
        return abi.startsWith("arm");
    }

    private static List<String> safeSupportedAbis() {
        String[] abis = Build.SUPPORTED_ABIS;
        return abis == null ? List.of() : Arrays.asList(abis);
    }

    private static String readNativeBridge() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            Method get = properties.getMethod("get", String.class, String.class);
            Object value = get.invoke(null, "ro.dalvik.vm.native.bridge", "");
            return value instanceof String string ? string : "";
        } catch (Throwable error) {
            return "";
        }
    }
}
