package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class CrashRecoveryGateTest {

    /** The unit-test working directory is the module, not the repository root. */
    private static Path root() {
        return Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
    }

    @Test
    public void bothHomesWaitBeforeConfigAndExternalActions() throws Exception {
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
    public void recoveryLogCarriesTheCurrentIntentAndGuardsAgainstAnotherReport() throws Exception {
        String session = Files.readString(root().resolve("app/src/main/java/com/fongmi/android/tv/utils/CrashLogSession.java"), StandardCharsets.UTF_8);
        int intent = session.indexOf("intentOf(context)");
        int pid = session.indexOf("historyPid(history)", intent);
        int drop = session.indexOf("report = null;", pid);
        assertTrue("the session must read this entry's history, not an older report", intent > 0 && pid > intent);
        assertTrue("a report from a different death must be dropped, not relabelled", drop > pid);
        assertTrue("the recovery facts stay on the file-locked state, not preferences",
                !session.contains("Prefers.") && session.contains("PreviousProcessCrashRecovery.getRecoveryDetails"));
    }
}
