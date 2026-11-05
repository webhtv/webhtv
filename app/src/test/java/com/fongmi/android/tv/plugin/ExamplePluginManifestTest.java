package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 示例清单与 SDK README 的一致性测试。
 *
 * <p>示例插件的价值在于"照着它能写出能跑的东西"。如果清单与 README 描述的字段漂移，
 * 示例就会静默失效——这类坏掉很难靠人工发现，所以用测试钉住。
 */
public class ExamplePluginManifestTest {

    private static final String EXAMPLE_MANIFEST = "apps/plugin/example/manifest.json";
    private static final String README = "assets/plugin/README.md";

    @Test
    public void exampleManifestParsesWithCurrentSchema() throws IOException {
        String json = readAsset("plugin/example/manifest.json");
        assertNotNull("example manifest must stay parseable", PluginManifest.parse(json));
    }

    @Test
    public void exampleManifestDeclaresFloatingSlotAndEvents() throws IOException {
        PluginManifest manifest = PluginManifest.parse(readAsset("plugin/example/manifest.json"));
        assertNotNull(manifest);
        assertTrue("example should keep at least one event", !manifest.getEvents().isEmpty());
        assertTrue("example should keep at least one ui slot", !manifest.getUiSlots().isEmpty());
        assertTrue("rationale: floating is the phone entry point",
                manifest.getPermissions().contains(PluginCapabilityRegistry.PERMISSION_UI_FLOATING));
    }

    @Test
    public void readmeDocumentsEveryKnownPermission() throws IOException {
        String readme = readAsset("plugin/README.md");
        for (String permission : PluginCapabilityRegistry.knownPermissions()) {
            assertTrue("README must document permission: " + permission, readme.contains(permission));
        }
    }

    @Test
    public void readmeDocumentsEveryBusEvent() throws IOException {
        String readme = readAsset("plugin/README.md");
        for (String event : new String[]{
                PluginEventBus.EVENT_APP_FOREGROUND, PluginEventBus.EVENT_APP_BACKGROUND,
                PluginEventBus.EVENT_ACTIVITY_CREATED, PluginEventBus.EVENT_ACTIVITY_DESTROYED,
                PluginEventBus.EVENT_UI_CONTENT_SHOWN}) {
            assertTrue("README must document event: " + event, readme.contains(event));
        }
    }

    /** Gradle 单测工作目录是模块根（app/），但也兼容仓库根，故两处都试。 */
    private static String readAsset(String path) throws IOException {
        File file = new File("src/main/assets/" + path);
        if (!file.exists()) file = new File("app/src/main/assets/" + path);
        if (!file.exists()) throw new IOException("asset not found: " + path + " (cwd=" + new File(".").getAbsolutePath() + ")");
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }
}
