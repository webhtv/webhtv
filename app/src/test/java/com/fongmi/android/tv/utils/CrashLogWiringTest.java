package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source contracts for the crash-time LAN log session; the flow spans processes and XML. */
public class CrashLogWiringTest {

    @Test
    public void crashWriterIsInstalledBeforeStartupProviders() throws Exception {
        String app = read("app/src/main/java/com/fongmi/android/tv/App.java");
        String startup = read("app/src/main/java/com/fongmi/android/tv/Startup.java");
        int attach = app.indexOf("protected void attachBaseContext");
        int install = app.indexOf("CrashReport.install(base)", attach);
        int initialize = app.indexOf("PreviousProcessCrashRecovery.initialize(base)", attach);
        int mainOnly = app.indexOf("CrashReport.isMainProcess(base)", install);
        assertTrue("fatal routing must cover provider/Application startup", install > attach && initialize > install);
        assertTrue("recovery state must be published only by the default process", mainOnly > install && mainOnly < initialize);
        assertFalse("Startup must not install over the early handler", startup.contains("CrashReport.install(context)"));
    }

    @Test
    public void crashScreenStartsTheSessionBeforeServingTheAddress() throws Exception {
        String activity = read("app/src/main/java/com/fongmi/android/tv/ui/activity/CrashActivity.java");
        int prepare = activity.indexOf("CrashLogSession.prepare(this)");
        int start = activity.indexOf("Server.get().start()", prepare);

        assertTrue(activity.contains("mBinding.copyLogAddress.setOnClickListener"));
        assertTrue(activity.contains("mBinding.openLog.setOnClickListener"));
        assertTrue(activity.contains("Server.get().getAddress(false)"));
        assertTrue("the crash screen must keep the log service available", activity.contains("Server.get().shutdown()"));
        assertTrue("back must not leave the LAN service running unseen", activity.contains("protected void onBackInvoked()"));
        assertTrue("the crash session must be prepared before the address is offered", prepare >= 0 && start > prepare);
    }

    @Test
    public void bothCrashLayoutsExposeTheSameLogAddressViews() throws Exception {
        for (String file : new String[]{
                "app/src/leanback/res/layout/activity_crash.xml",
                "app/src/mobile/res/layout/activity_crash.xml"}) {
            String layout = read(file);
            assertTrue(file + " must scroll, so a small or landscape screen still reaches every entry",
                    layout.contains("<ScrollView") && layout.contains("android:fillViewport=\"true\""));
            for (String id : new String[]{"logTitle", "logAddress", "logHint", "copyLogAddress", "openLog"}) {
                assertTrue(file + " must declare " + id, layout.contains("android:id=\"@+id/" + id + "\""));
            }
        }
    }

    @Test
    public void crashSessionNeverPersistsTheDebugLogPreference() throws Exception {
        String store = read("catvod/src/main/java/com/github/catvod/crawler/DebugLogStore.java");
        int begin = store.indexOf("public static synchronized boolean beginCrashSession()");
        int append = store.indexOf("public static void appendCrashContext", begin);

        assertTrue("crash sessions must use memory-only persistence", store.contains("InMemoryDiagnosticFile"));
        assertTrue(begin >= 0 && append > begin);
        assertFalse("a crash session must not change the user's debug-log preference",
                store.substring(begin, append).contains("Prefers.put"));
    }

    @Test
    public void debugLogPageLabelsTheCrashSessionAndHidesItsControls() throws Exception {
        String page = read("app/src/main/java/com/fongmi/android/tv/server/process/DebugLogs.java");

        assertTrue(page.contains("DebugLogStore.isCrashSessionTemporary()"));
        assertTrue(page.contains("崩溃会话"));
        assertTrue(page.contains("crash-banner"));
        assertTrue("the poll refresh must not overwrite the crash-session label with 记录中",
                page.contains("crashSession") && page.contains("'崩溃会话 · 自动开启'"));
        assertTrue(page.contains("崩溃会话临时日志：本页自动开启，重启后恢复原设置。"));
    }

    @Test
    public void crashSessionCapturesTheSystemLogcatTail() throws Exception {
        String session = read("app/src/main/java/com/fongmi/android/tv/utils/CrashLogSession.java");

        assertTrue(session.contains("CrashReport.readRecent(context)"));
        assertTrue(session.contains("\"logcat\""));
        assertTrue("coverage must not claim a logcat tail when the tail is empty",
                session.contains("!logcat.isEmpty()"));
        assertTrue(session.contains("DebugLogStore.appendCrashContext"));
        assertTrue(session.contains("CrashLogText.filterLogcat"));
    }

    @Test
    public void newCrashStringsExistInEveryLocale() throws Exception {
        String[] keys = {"crash_log_title", "crash_log_hint", "crash_log_preparing", "crash_log_unavailable",
                "crash_log_no_lan", "crash_log_service_failed", "crash_log_copy", "crash_log_copied", "crash_log_open"};
        for (String file : new String[]{
                "app/src/main/res/values/strings.xml",
                "app/src/main/res/values-zh-rCN/strings.xml",
                "app/src/main/res/values-zh-rTW/strings.xml"}) {
            String strings = read(file);
            for (String key : keys) assertTrue(file + " must define " + key, strings.contains("name=\"" + key + "\""));
        }
    }

    @Test
    public void javaRecoveryKeepsTheReportOfTheSameDeath() {
        assertFalse("a Java fatal handoff writes the report of this very death",
                CrashLogSession.discardReport(javaHistory(42), report(42)));
        assertTrue("a report of another death must not be presented as this entry's evidence",
                CrashLogSession.discardReport(javaHistory(42), report(7).replace("event=java:test","event=java:other")));
        assertTrue("an unprovable Java identity must not mix another report in",
                CrashLogSession.discardReport(javaHistory(0).replace("event=java:test\n",""), report(7)));
        assertFalse("without a history the recent report is the only evidence, as before",
                CrashLogSession.discardReport(null, report(7)));
    }

    @Test
    public void platformRecoveryNeverBorrowsAnOlderJavaReport() {
        for (String reason : new String[]{"CRASH", "CRASH_NATIVE", "ANR"}) {
            assertTrue("a " + reason + " entry has no report of its own, even when the pid was reused",
                    CrashLogSession.discardReport(exitHistory(reason, 42), report(42)));
        }
        assertTrue("an unknown platform identity must not be relabelled as this entry's report",
                CrashLogSession.discardReport(exitHistory("CRASH_NATIVE", -1), report(7)));
    }

    private static String javaHistory(int pid) {
        return "# Java fatal recovery\nevent=java:test\nprocess=actual.main\npid=" + pid + "\ntimestamp=1\nreason=JAVA_FATAL\nstack";
    }

    private static String exitHistory(String reason, int pid) {
        return "# previous-process-recovery v1\nprocess=actual.main\npid=" + pid
                + "\ntimestamp=1\nreason=" + reason + "\nstatus=0\n";
    }

    private static String report(int pid) {
        return "# webhtv crash report\nevent=java:test\nprocess=actual.main\npid=" + pid + "\n";
    }

    private static String read(String file) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
    @Test public void reusedPidCannotAssociateDifferentJavaEvents() {
        String a = javaHistory(42).replace("event=java:test", "event=java:A");
        String b = report(42).replace("event=java:test","event=java:B");
        assertTrue("B report must not enter A page just because pid matches", CrashLogSession.discardReport(a,b));
        assertTrue("legacy report without identity is not attributed", CrashLogSession.discardReport(a,report(42).replace("event=java:test\n","")));
        assertTrue("legacy history without identity is not attributed", CrashLogSession.discardReport(javaHistory(42).replace("event=java:test\n",""),b));
        assertFalse(CrashLogSession.discardReport(a,report(42).replace("event=java:test","event=java:A")));
    }
    @Test public void writerAndHandoffShareOneRunTokenWithMetadataBeforeLargeBody() throws Exception {
        java.lang.reflect.Field run = PreviousProcessCrashRecovery.class.getDeclaredField("run");
        run.setAccessible(true);
        Object previous = run.get(null);
        try {
            run.set(null,new CrashRecoveryState.Run("shared-token",42,100,"actual.main"));
            String writer = PreviousProcessCrashRecovery.javaEvent(null);
            String handoff = PreviousProcessCrashRecovery.javaEvent(null);
            assertEquals("java:shared-token",writer);
            assertEquals(writer,handoff);
            String report = CrashReport.withIdentity("# report\nprocess=actual.main\n--- stack ---\n"+"x".repeat(200000),writer);
            assertTrue(report.startsWith("# report\nevent=java:shared-token\n"));
            String source = read("app/src/main/java/com/fongmi/android/tv/utils/CrashReport.java");
            assertTrue(source.contains("withIdentity(CrashLogText.format(facts(thread, error)), PreviousProcessCrashRecovery.javaEvent(context))"));
            source = read("app/src/main/java/com/fongmi/android/tv/utils/PreviousProcessCrashRecovery.java");
            assertTrue(source.contains("String event = javaEvent(app)"));
        } finally { run.set(null,previous); }
    }
}
