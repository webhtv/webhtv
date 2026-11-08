package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source contracts for the crash-time LAN log session; the flow spans processes and XML. */
public class CrashLogWiringTest {

    @Test
    public void crashWriterIsInstalledRightAfterTheCrashHandlerConfiguration() throws Exception {
        String startup = read("app/src/main/java/com/fongmi/android/tv/Startup.java");
        int config = startup.indexOf("CaocConfig.Builder.create()");
        int install = startup.indexOf("CrashReport.install(context)");

        assertTrue("Startup must install the crash report writer", install >= 0);
        assertTrue("the writer must wrap the handler installed by CustomActivityOnCrash", config >= 0 && install > config);
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

    private static String read(String file) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
