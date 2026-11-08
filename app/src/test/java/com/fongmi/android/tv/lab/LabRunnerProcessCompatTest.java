package com.fongmi.android.tv.lab;

import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Android 7 (API 24/25) contract for the Lab runtime.
 *
 * <p>{@code Process#isAlive()} is API 26, so the original call sites threw {@code NoSuchMethodError}
 * as soon as a Lab entry with commands was installed. Because that failure is an {@code Error},
 * {@code LabAutoStart}'s {@code catch (Exception)} could not stop it and the process died. The
 * replacement probes {@code exitValue()} (API 1) and must keep doing so.
 */
public class LabRunnerProcessCompatTest {

    @Test
    public void probeTreatsOnlyATerminatedProcessAsDead() {
        assertTrue("a still running process must be reported alive", LabRunner.isAlive(fake(false)));
        assertFalse("a terminated process must be reported dead", LabRunner.isAlive(fake(true)));
        assertFalse("a missing process must be reported dead", LabRunner.isAlive(null));
    }

    @Test
    public void labSourcesProbeThroughExitValueInsteadOfTheApi26Call() throws Exception {
        String runner = stripComments(read("app/src/main/java/com/fongmi/android/tv/lab/LabRunner.java"));
        String terminal = stripComments(read("app/src/main/java/com/fongmi/android/tv/lab/LabTerminalActivity.java"));

        assertTrue("the helper must probe the API 1 exitValue() contract",
                runner.contains("process.exitValue()"));
        assertFalse("java.lang.Process#isAlive() is API 26 and must not be called in LabRunner",
                runner.contains(".isAlive()"));
        assertFalse("java.lang.Process#isAlive() is API 26 and must not be called in LabTerminalActivity",
                terminal.contains(".isAlive()"));
        assertTrue("LabTerminalActivity must route every liveness check through the compatible helper",
                terminal.contains("LabRunner.isAlive("));
    }

    private static Process fake(boolean terminated) {
        return new Process() {
            @Override
            public OutputStream getOutputStream() {
                return OutputStream.nullOutputStream();
            }

            @Override
            public InputStream getInputStream() {
                return InputStream.nullInputStream();
            }

            @Override
            public InputStream getErrorStream() {
                return InputStream.nullInputStream();
            }

            @Override
            public int waitFor() {
                return 0;
            }

            @Override
            public int exitValue() {
                if (terminated) return 0;
                throw new IllegalThreadStateException("still running");
            }

            @Override
            public void destroy() {
            }
        };
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\n]*", " ");
    }

    private static String read(String name) throws Exception {
        Path path = Path.of(name);
        if (!Files.exists(path)) path = Path.of("..").resolve(name).normalize();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
