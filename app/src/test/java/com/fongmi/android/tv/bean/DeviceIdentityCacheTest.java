package com.fongmi.android.tv.bean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code Device.get()} 每次都会重新探测 MAC、Android ID、型号，其中 {@code Util.getMac}
 * 还会遍历网络接口。它被 {@code PlaybackRecord.clientKey()} 调用，而后者在播放中每秒
 * 至少跑一次（进度 tick），相当于每秒在主线程做一遍设备探测。
 * 这些测试锁住「身份信息进程内只探测一次」这条约束，同时锁住每次调用仍需返回新的可变实体。
 */
public class DeviceIdentityCacheTest {

    @Test
    public void identityIsProbedOncePerProcess() throws Exception {
        String source = readSource("bean/Device.java");
        String body = methodBody(source, "public static Device get()");
        assertTrue("Device.get() must go through the cached identity probe", body.contains("ensureIdentity();"));
        assertFalse("Device.get() must not re-read the MAC addresses on every call", body.contains("Util.getMac("));
        assertFalse("Device.get() must not re-read the Android ID on every call", body.contains("Util.getAndroidId("));
        assertFalse("Device.get() must not re-read the build model on every call", body.contains("Util.getDeviceName("));
    }

    @Test
    public void theProbeIsGuardedByADoubleCheckedLock() throws Exception {
        String body = methodBody(readSource("bean/Device.java"), "private static void ensureIdentity()");
        assertTrue("the probe must check the ready flag before locking", body.contains("if (identityReady) return;"));
        assertTrue("the probe must re-check inside the lock", body.contains("synchronized (IDENTITY_LOCK)"));
        assertTrue("the probe must publish the ready flag last", body.indexOf("identityReady = true;") > body.indexOf("synchronized (IDENTITY_LOCK)"));
    }

    @Test
    public void theHotCallerUsesTheCheapAccessor() throws Exception {
        String source = readSource("playback/PlaybackRecord.java");
        String body = stripComments(methodBody(source, "private static String clientKey()"));
        assertTrue("clientKey must use the cached identity accessor", body.contains("Device.identityUuid()"));
        assertFalse("clientKey must not build a whole Device on the progress tick", body.contains("Device.get()"));
        String deviceSource = readSource("bean/Device.java");
        assertTrue("the cached accessor must exist", deviceSource.contains("public static String identityUuid()"));
        assertTrue("the accessor must never return null", deviceSource.contains("identityUuid == null ? \"\" : identityUuid"));
    }

    @Test
    public void deviceStillReturnsAFreshInstanceWithLiveTimeAndAddress() throws Exception {
        String body = methodBody(readSource("bean/Device.java"), "public static Device get()");
        assertTrue("callers mutate and persist the entity, so a new instance is still required",
                body.contains("Device device = new Device();"));
        assertTrue("the address can change while the app runs", body.contains("setIp("));
        assertTrue("the store timestamp must stay live", body.contains("setTime("));
    }

    private static String stripComments(String body) {
        // 断言针对真实代码，不能被解释性注释命中（注释里会提到被删除的调用）。
        StringBuilder out = new StringBuilder(body.length());
        for (String line : body.split("\\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) continue;
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String readSource(String file) throws Exception {
        Path moduleRelative = Path.of("src", "main", "java", "com", "fongmi", "android", "tv", file);
        Path path = Files.exists(moduleRelative) ? moduleRelative : Path.of("app").resolve(moduleRelative);
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("source is missing " + signature, start >= 0);
        int open = source.indexOf('{', start + signature.length());
        assertTrue("source is missing the body of " + signature, open > start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("unclosed body for " + signature);
    }
}
