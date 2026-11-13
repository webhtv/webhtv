package com.fongmi.android.tv.utils;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CrashActivityIsolationTest {

    @Test
    public void errorProcessAvoidsBusinessInitializationAndPreferenceResources() throws Exception {
        String app = read("app/src/main/java/com/fongmi/android/tv/App.java");
        String attach = section(app, "protected void attachBaseContext", "public void onCreate");
        assertTrue("error process must be recognized before business initialization", attach.contains("CrashReport.isErrorProcess(base)"));
        assertTrue("lightweight processes must exit before WebView initialization", attach.indexOf("if (lightweightProcess)") < attach.indexOf("WebViewDataDirectoryGuard.clearStaleLock"));
        String resources = section(app, "public Resources getResources()", "private boolean shouldRetryResourcesApply");
        assertTrue("error resources must not read broken preferences", resources.indexOf("if (crashPageProcess) return super.getResources()") >= 0);
        assertTrue(resources.indexOf("if (crashPageProcess)") < resources.indexOf("Setting.getLanguage()"));
        assertTrue("only the crash page process may skip the language override",
                attach.contains("crashPageProcess = errorProcess"));
        String create = section(app, "public void onCreate()", "private void warmSubtitleProviders");
        assertTrue(create.indexOf("if (lightweightProcess) return") < create.indexOf("PlaybackMemoryMonitor.process().initialize"));
    }

    @Test
    public void handlerIsInstalledBeforeProvidersAndCannotBeOverwrittenByCaoc() throws Exception {
        String app = read("app/src/main/java/com/fongmi/android/tv/App.java");
        String attach = section(app, "protected void attachBaseContext", "public void onCreate");
        int install = attach.indexOf("CrashReport.install(base)");
        assertTrue("attachBaseContext must install the handler", install >= 0);
        assertTrue(install < attach.indexOf("PreviousProcessCrashRecovery.initialize(base)"));
        assertTrue(install < attach.indexOf("WebViewDataDirectoryGuard.clearStaleLock"));
        String startup = read("app/src/main/java/com/fongmi/android/tv/Startup.java");
        assertFalse("Startup must not replace the early handler", startup.contains("CrashReport.install(context)"));
        NodeList providers = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(root().resolve("app/src/main/AndroidManifest.xml").toFile()).getElementsByTagName("provider");
        boolean removed = false;
        for (int i = 0; i < providers.getLength(); i++) {
            Element provider = (Element) providers.item(i);
            if ("cat.ereza.customactivityoncrash.CaocInitProvider".equals(provider.getAttribute("android:name"))) {
                removed = "remove".equals(provider.getAttribute("tools:node"));
            }
        }
        assertTrue("automatic CAOC provider must not overwrite the early handler", removed);
    }

    @Test
    public void independentPageRetainsActionsWithoutBusinessBaseActivity() throws Exception {
        String activity = read("app/src/main/java/com/fongmi/android/tv/ui/activity/CrashActivity.java");
        assertTrue("crash page must bypass both business BaseActivity implementations", activity.contains("extends AppCompatActivity"));
        assertFalse(activity.contains("import com.fongmi.android.tv.ui.base.BaseActivity"));
        assertFalse(activity.contains("Setting."));
        assertFalse(activity.contains("ThemeController"));
        assertFalse(activity.contains("Notify."));
        assertTrue(activity.contains("setContentView(mBinding.getRoot())"));
        assertTrue(activity.contains("mBinding.restart.requestFocus()"));
        assertTrue(activity.contains("getOnBackPressedDispatcher().addCallback"));
        for (String action : new String[]{"restart", "silentRestart", "details", "copyLogAddress", "openLog"}) {
            assertTrue(action, activity.contains("mBinding." + action + ".setOnClickListener"));
        }
        assertTrue(activity.contains("PreviousProcessCrashRecovery.acknowledgeShown(this, getIntent())"));
    }

    @Test
    public void missingOrMalformedConfigCannotDisableRestartOrDetails() throws Exception {
        String activity = read("app/src/main/java/com/fongmi/android/tv/ui/activity/CrashActivity.java");
        assertFalse("a plain Intent must not crash when restarting", activity.contains("Objects.requireNonNull"));
        assertTrue(activity.contains("new CaocConfig()"));
        assertTrue(activity.contains("getLaunchIntentForPackage(getPackageName())"));
        assertTrue(activity.contains("new Intent(this, HomeActivity.class)"));
        assertTrue(activity.contains("CustomActivityOnCrash.restartApplicationWithIntent"));
        assertTrue(activity.contains("PreviousProcessCrashRecovery.getRecoveryDetails(getIntent())"));
        assertTrue(activity.contains("CustomActivityOnCrash.getStackTraceFromIntent(getIntent())"));
    }

    @Test
    public void mainProcessIdentityUsesTheManifestApplicationProcess() throws Exception {
        String report = read("app/src/main/java/com/fongmi/android/tv/utils/CrashReport.java");
        String identity = section(report, "public static boolean isMainProcess", "private static void launchRecovery");
        assertTrue("custom application process names must take precedence over package name", identity.contains("context.getApplicationInfo()"));
        assertTrue(identity.contains(".processName"));
        assertTrue("missing process metadata must fall back to the package", identity.contains("context.getPackageName()"));
        assertTrue("unreadable metadata must fail safely", identity.contains("catch (Throwable ignored)") && identity.contains("return false"));
    }

    private static String section(String source, String start, String end) {
        int begin = source.indexOf(start);
        return source.substring(begin, source.indexOf(end, begin));
    }

    private static Path root() {
        return Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
    }

    private static String read(String file) throws Exception {
        return Files.readString(root().resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
