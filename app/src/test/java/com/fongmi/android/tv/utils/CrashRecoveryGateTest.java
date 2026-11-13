package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Drives the production gate kernel. The crash screen, the deadline Handler and the platform query
 * cannot run on a plain JVM, so the executable part lives in the kernel the app itself uses.
 */
public class CrashRecoveryGateTest {

    /** The unit-test working directory is the module, not the repository root. */
    private static Path root() {
        return Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
    }

    private final List<String> ran = new ArrayList<>();

    private PreviousProcessCrashRecovery.Gate gate() {
        return new PreviousProcessCrashRecovery.Gate();
    }

    @Test
    public void everyCallerWaitsForTheSingleDecision() {
        PreviousProcessCrashRecovery.Gate gate = gate();
        assertFalse(gate.decided());
        assertTrue(gate.parkOrDecided("home", () -> ran.add("loadConfig")));
        assertTrue(gate.parkOrDecided("settings", () -> ran.add("handleAction")));
        assertTrue("no configuration may load before the decision", ran.isEmpty());
        assertFalse(gate.decided());
    }

    @Test
    public void theDecisionReleasesEveryParkedStepExactlyOnce() {
        PreviousProcessCrashRecovery.Gate gate = gate();
        gate.parkOrDecided("home", () -> ran.add("loadConfig"));
        gate.parkOrDecided("settings", () -> ran.add("handleAction"));
        List<PreviousProcessCrashRecovery.Gate.Step> released = gate.finish();
        assertNotNull(released);
        assertEquals(2, released.size());
        assertNull("a late deadline must not release the same steps twice", gate.finish());
        PreviousProcessCrashRecovery.Gate.runAll(released, owner -> true);
        assertEquals(List.of("loadConfig", "handleAction"), ran);
        assertFalse("a settled gate parks nothing else", gate.parkOrDecided("late", () -> ran.add("late")));
        assertTrue(gate.decided());
    }

    @Test
    public void aDecisionThatArrivesAfterTheDeadlineIsStillDeliveredToOneEntryPoint() {
        PreviousProcessCrashRecovery.Gate gate = gate();
        gate.parkOrDecided("home", () -> ran.add("loadConfig"));
        PreviousProcessCrashRecovery.Gate.runAll(gate.finish(), owner -> true);
        assertEquals(List.of("loadConfig"), ran);
        assertTrue("a decision after the deadline is held, not dropped", gate.hold("recovery"));
        assertFalse("a second decision cannot replace the pending one", gate.hold("other recovery"));
        assertEquals("recovery", gate.claim());
        assertNull("a delivered decision is never handed over twice", gate.claim());
    }

    @Test
    public void aDestroyedHostIsDroppedWithItsParkedSteps() {
        PreviousProcessCrashRecovery.Gate gate = gate();
        gate.parkOrDecided("home", () -> ran.add("loadConfig"));
        gate.parkOrDecided("settings", () -> ran.add("handleAction"));
        gate.release("home");
        PreviousProcessCrashRecovery.Gate.runAll(gate.finish(), owner -> true);
        assertEquals(List.of("handleAction"), ran);
    }

    @Test
    public void aStepThatFinishesItsHostStopsTheFollowingStepsOfThatHost() {
        PreviousProcessCrashRecovery.Gate gate = gate();
        Set<Object> alive = new HashSet<>(List.of("home"));
        gate.parkOrDecided("home", () -> {
            ran.add("loadConfig");
            alive.remove("home");
        });
        gate.parkOrDecided("home", () -> ran.add("handleAction"));
        PreviousProcessCrashRecovery.Gate.runAll(gate.finish(), alive::contains);
        assertEquals("nothing may run on a host the previous step finished", List.of("loadConfig"), ran);
    }

    @Test
    public void onePlatformQueryPerProcessRun() {
        PreviousProcessCrashRecovery.Gate gate = gate();
        assertTrue(gate.queryOnce());
        assertFalse("a second entry point must not start a second query", gate.queryOnce());
        PreviousProcessCrashRecovery.Gate settled = gate();
        settled.finish();
        assertFalse("a settled gate never queries the platform again", settled.queryOnce());
    }

    @Test
    public void bothHomesAwaitRecoveryBeforeConfigAndExternalActions() throws Exception {
        for (String flavor : new String[]{"mobile", "leanback"}) {
            String source = Files.readString(root().resolve("app/src/" + flavor + "/java/com/fongmi/android/tv/ui/activity/HomeActivity.java"), StandardCharsets.UTF_8);
            int config = source.indexOf("private void initConfig()");
            int consume = source.indexOf("CrashRestartMode.consume()", config);
            int gate = source.indexOf("PreviousProcessCrashRecovery.checkBeforeConfig(this, this::loadConfig)", config);
            assertTrue(flavor + " must await recovery before consuming silent restart or loading configuration", gate > config && gate < consume);
            int action = source.indexOf("private void checkAction(Intent intent)");
            int actionGate = source.indexOf("PreviousProcessCrashRecovery.checkBeforeConfig(this, () -> handleAction(intent))", action);
            int send = source.indexOf("Intent.ACTION_SEND", action);
            assertTrue(flavor + " external entry must not bypass recovery", actionGate > action && actionGate < send);
        }
    }

    @Test
    public void bothHomesUnregisterTheirDestroyedHost() throws Exception {
        for (String flavor : new String[]{"mobile", "leanback"}) {
            String source = Files.readString(root().resolve("app/src/" + flavor + "/java/com/fongmi/android/tv/ui/activity/HomeActivity.java"), StandardCharsets.UTF_8);
            int destroy = source.indexOf("protected void onDestroy()");
            int release = source.indexOf("PreviousProcessCrashRecovery.release(this)", destroy);
            assertTrue(flavor + " must drop its parked steps when it is destroyed", release > destroy);
            assertTrue(flavor + " must release before the super call", release < source.indexOf("super.onDestroy()", destroy));
        }
    }

    @Test
    public void recoveryLogCarriesTheCurrentIntentAndGuardsAgainstAnotherReport() throws Exception {
        String session = read("app/src/main/java/com/fongmi/android/tv/utils/CrashLogSession.java");
        int intent = session.indexOf("intentOf(context)");
        int pid = session.indexOf("historyPid(history)", intent);
        int drop = session.indexOf("report = null;", pid);
        assertTrue("the session must read this entry's history, not an older report", intent > 0 && pid > intent);
        assertTrue("a report from a different death must be dropped, not relabelled", drop > pid);
        assertTrue("the recovery facts stay on the file-locked state, not preferences",
                !session.contains("Prefers.") && session.contains("PreviousProcessCrashRecovery.getRecoveryDetails"));
    }

    private static String read(String file) throws Exception {
        return Files.readString(root().resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
    @Test public void rejectedQueryReleasesParkedStepsOnlyOnce() {
        PreviousProcessCrashRecovery.Gate gate = gate();
        gate.parkOrDecided("home", () -> ran.add("config"));
        gate.submit(() -> { throw new java.util.concurrent.RejectedExecutionException("rejected"); }, owner -> true);
        assertEquals(List.of("config"), ran);
        assertNull("deadline after rejection must not release again", gate.finish());
    }
}