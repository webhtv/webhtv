package com.fongmi.android.tv.playback.origin;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OriginSessionContractTest {

    @Test
    public void schemaVersionIsOneAndCannotBeChangedByCallers() throws Exception {
        Field version = contract().getField("VERSION");
        assertEquals(int.class, version.getType());
        assertTrue(Modifier.isStatic(version.getModifiers()));
        assertTrue(Modifier.isFinal(version.getModifiers()));
        assertEquals(1, version.getInt(null));
    }

    @Test
    public void portPreservesOwnerLeaseGenerationAndFdAtTheBinderBoundary() throws Exception {
        Class<?> port = nested("OriginSessionPort");
        Class<?> reply = nested("OriginReply");
        Class<?> bundle = Class.forName("android.os.Bundle");
        Class<?> fd = Class.forName("android.os.ParcelFileDescriptor");
        Class<?> binder = Class.forName("android.os.IBinder");
        assertTrue(Class.forName("android.os.IInterface").isAssignableFrom(port));
        assertVoid(port, "open", bundle, fd, binder, reply);
        assertVoid(port, "play", bundle, reply);
        assertVoid(port, "cancel", long.class);
        assertVoid(port, "close", String.class, long.class);
        Set<String> operations = new HashSet<>();
        for (Method method : port.getDeclaredMethods()) {
            if (!method.isSynthetic() && !Modifier.isStatic(method.getModifiers())) operations.add(method.getName());
        }
        assertEquals(new HashSet<>(Arrays.asList("open", "play", "cancel", "close")), operations);
    }

    @Test
    public void replyCarriesOnlySmallEnvelopeAndFdNotLiveRuntimeObjects() throws Exception {
        Class<?> reply = nested("OriginReply");
        assertTrue(Class.forName("android.os.IInterface").isAssignableFrom(reply));
        assertVoid(reply, "complete", Class.forName("android.os.Bundle"),
                Class.forName("android.os.ParcelFileDescriptor"));
        int operations = 0;
        for (Method method : reply.getDeclaredMethods()) {
            if (!method.isSynthetic() && !Modifier.isStatic(method.getModifiers())) {
                assertEquals("complete", method.getName());
                operations++;
            }
        }
        assertEquals(1, operations);
    }

    @Test
    public void eachPublicBinderInterfaceProvidesItsOwnStub() throws Exception {
        for (String name : new String[]{"OriginSessionPort", "OriginReply"}) {
            Class<?> api = nested(name);
            Class<?> stub = null;
            for (Class<?> candidate : api.getDeclaredClasses()) {
                if (candidate.getSimpleName().equals("Stub")) stub = candidate;
            }
            assertTrue(name + " must provide a hand-written Binder Stub", stub != null);
            assertTrue(Class.forName("android.os.Binder").isAssignableFrom(stub));
            assertTrue(api.isAssignableFrom(stub));
            Method factory = stub.getMethod("asInterface", Class.forName("android.os.IBinder"));
            assertTrue(Modifier.isStatic(factory.getModifiers()));
            assertEquals(api, factory.getReturnType());
        }
    }

    private static void assertVoid(Class<?> type, String name, Class<?>... parameters) throws Exception {
        assertEquals("Binder work must be asynchronous: " + name, void.class,
                type.getMethod(name, parameters).getReturnType());
    }

    private static Class<?> nested(String simpleName) throws Exception {
        for (Class<?> candidate : contract().getDeclaredClasses()) {
            if (candidate.getSimpleName().equals(simpleName)) return candidate;
        }
        fail("Stage 1 Binder implementation missing: OriginSessionContract." + simpleName);
        return null;
    }

    private static Class<?> contract() throws Exception {
        try {
            return Class.forName("com.fongmi.android.tv.playback.origin.OriginSessionContract", false,
                    OriginSessionContractTest.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            fail("Stage 1 implementation missing: OriginSessionContract; runnable red baseline");
            return null;
        }
    }
}
