package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DanmakuBundleEndpointSourceTest {

    @Test
    public void searchApiFallsBackToBundleDanmakuService() throws Exception {
        // 本地包自带弹幕服务，但 index.config.js 没有 danmaku 字段，订阅也没有。回退必须排在
        // 用户手填和订阅配置之后：用户显式配置永远优先，本地包只是「没配时也能用」。
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "DanmakuSetting.java"));
        String configured = methodBody(source, "public static String getConfiguredApiUrl()", "public static String getEffectiveApiUrl()");
        String effective = methodBody(source, "public static String getEffectiveApiUrl()", "public static String getValidApiUrl()");

        int userUrl = configured.indexOf("String userUrl = getApiUrl();");
        int configUrl = configured.indexOf("VodConfig.get().getConfig().getDanmaku()");
        assertTrue(userUrl >= 0);
        assertTrue(configUrl > userUrl);
        assertContainsAll(effective, "String configured = getConfiguredApiUrl();", "bundleApiUrl()");
    }

    @Test
    public void bundleFallbackStopsWhenTheActiveSourceIsNoLongerTheCatBundle() throws Exception {
        // 切到普通订阅时 VodConfig 不停 node，:node 进程与端口都还在；只判存活会把本地包地址
        // 继续用在普通线路上——切源后仍「有接口」且能搜到弹幕，要重启 APP 才恢复。
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "DanmakuSetting.java"));
        String bundleSource = methodBody(source, "public static boolean isBundleSource()", "private static String bundleApiUrl()");
        String fallback = methodBody(source, "private static String bundleApiUrl()", "public static String getValidApiUrl()");
        assertContainsAll(bundleSource, "CatSource.isBundle(VodConfig.get().getConfig().getUrl())");
        assertContainsAll(fallback, "isBundleSource() ? NodeRuntime.danmakuApiUrl() : \"\";");
    }

    @Test
    public void apiDialogPrefillsOnlyConfiguredValueSoTheBundleFallbackIsNeverPersisted() throws Exception {
        // 输入框回填若用 getEffectiveApiUrl()，用户不改直接确定，putApiUrl 会把运行时的本机
        // 回环地址固化进 prefs——换包后就是一个死地址，且再也不回退。
        for (String flavor : new String[]{"leanback", "mobile"}) {
            String source = read(sourcePath(flavor, "java", "com", "fongmi", "android", "tv", "ui", "dialog", "DanmakuApiDialog.java"));
            String initView = methodBody(source, "protected void initView()", "protected void initEvent()");
            // 只看真正回填的那一行；方法体里的注释会提到被排除的方法名。
            String prefill = initView.lines().filter(line -> line.contains("binding.text.setText(")).findFirst().orElse("");
            assertContainsAll(prefill, "DanmakuSetting.getConfiguredApiUrl()");
            assertFalse(prefill.contains("getEffectiveApiUrl()"));
        }
    }

    @Test
    public void runtimeProbesTheBundleDanmakuPortOutsideTheCatSourcePort() throws Exception {
        // 猫源本体端口与弹幕服务端口一起写在 files/node/bundle/port 里（实测 9988,9321）。
        // 只能靠 GET /danmaku 区分：本体回 404，弹幕服务回 200 且 body 是 JSON。
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "node", "NodeRuntime.java"));
        String probe = methodBody(source, "private static void resolveDanmakuPort(int catPort, long generation)", "private static boolean isDanmakuService(okhttp3.OkHttpClient client, int candidate)");
        int serviceAt = source.indexOf("private static boolean isDanmakuService(okhttp3.OkHttpClient client, int candidate)");
        assertTrue(serviceAt > 0);
        String isService = source.substring(serviceAt);

        assertTrue(probe.contains("readPorts(new File(NodeBundle.dir(App.get()), \"port\"))"));
        assertTrue(probe.contains("candidate == catPort"));
        assertTrue(probe.contains("isDanmakuService(client, candidate)"));
        assertTrue(probe.contains("Task.execute("));
        // 换源会开出新世代，旧探测线程必须靠这条判据自己收手，否则会把上一代 bundle 的端口写回去。
        assertTrue(probe.contains("generation == START_GENERATION.get()"));
        // 去重必须按世代：上一代线程还在跑时，按「有无线程」去重会把新世代唯一的触发点挡回去，
        // 新 bundle 的弹幕端口就永久探不到。
        assertTrue(source.contains("private static final AtomicLong DANMAKU_PROBING_GENERATION = new AtomicLong(-1);"));
        String claim = methodBody(source, "private static boolean claimDanmakuProbe(long generation)", "private static void releaseDanmakuProbe(");
        String release = methodBody(source, "private static void releaseDanmakuProbe(long generation)", "private static void resolveDanmakuPort(");
        assertContainsAll(probe, "if (!claimDanmakuProbe(generation)) return;", "releaseDanmakuProbe(generation);");
        // 探到的端口要另记一份给「曾经开过的端口」用，换源清零后才有据可查。
        assertContainsAll(probe, "danmakuPort = candidate;", "lastDanmakuPort = candidate;");
        assertContainsAll(claim,
                "long current = DANMAKU_PROBING_GENERATION.get();",
                "if (current == generation) return false;",
                "DANMAKU_PROBING_GENERATION.compareAndSet(current, generation)",
                "return true;");
        assertContainsAll(release, "DANMAKU_PROBING_GENERATION.compareAndSet(generation, -1);");
        assertFalse(probe.contains("DANMAKU_PROBING."));
        assertContainsAll(isService,
                "\"/danmaku\"",
                "response.code() != 200",
                "body.startsWith(\"[\")",
                "body.startsWith(\"{\")");
    }

    @Test
    public void runtimeExposesTheProbedEndpointAndClearsItWithTheCatSourcePort() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "node", "NodeRuntime.java"));
        String accessor = methodBody(source, "public static String danmakuApiUrl()", "public static boolean ownsLoopbackPort(String url)");
        String clear = methodBody(source, "private static void clearPort()", "public static synchronized void start(");
        String owns = methodBody(source, "public static boolean ownsLoopbackPort(String url)", "private static void clearPort()");
        String start = methodBody(source, "public static synchronized void start(", "private static synchronized void timeout(");
        String ready = methodBody(source, "case NodeService.MSG_READY:", "case NodeService.MSG_PROGRESS:");
        String error = methodBody(source, "case NodeService.MSG_ERROR:", "default:");

        assertContainsAll(accessor, "loopbackUrl(value, \"/danmaku\")");
        // 子进程被系统杀掉时 running/端口都不会被清，只认端口号会把死地址交给弹幕搜索。
        assertContainsAll(accessor, "value <= 0 || !running || !serviceAlive()", "return \"\";");
        assertContainsAll(clear, "port = 0;", "danmakuPort = 0;");
        // 换源后正是靠 lastDanmakuPort 认出上一场本地包残留的弹幕地址，clearPort 必须留着它。
        assertContainsAll(source, "private static volatile int lastDanmakuPort;");
        assertContainsAll(owns, "value == port || value == danmakuPort || value == lastDanmakuPort");
        assertFalse(clear.contains("lastDanmakuPort = 0"));
        // 换源/启动失败/就绪超时都必须把旧的弹幕端口一起清掉，否则新源会连到旧进程的地址。
        assertContainsAll(start, "clearPort();");
        assertContainsAll(error, "clearPort();");
        assertContainsAll(ready, "danmakuPort = 0;", "resolveDanmakuPort(port, generation);");
        // bundle 的弹幕服务可能晚于首次探测预算才落盘端口，复用捷径不补探就整场没有弹幕。
        assertContainsAll(start, "resolveDanmakuPort(port, START_GENERATION.get())");
    }

    @Test
    public void playerDropsAStaleBundleDanmakuSourceAfterTheSourceSwitch() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "player", "PlayerManager.java"));
        String guard = methodBody(source, "private static boolean isStaleBundleDanmaku(", "private void clearDanmaku(");
        String setter = methodBody(source, "String url = DanmakuUrlPolicy.normalize(", "DanmakuUrlPolicy.SourceType sourceType");
        String restore = methodBody(source, "private void restoreDanmakuDataSource()", "public void setDanmakuConfig(");

        // 只有「属于本地包、且本地包已经不是当前源」的回环地址才算残留：先问订阅是否仍是本地包
        // （探测窗口期内也必须为 true），再只认运行时真的开过的端口。
        assertContainsAll(guard,
                "TextUtils.equals(url, DanmakuSetting.getConfiguredApiUrl())",
                "if (!DanmakuUrlPolicy.isLoopback(url)) return false;",
                "if (DanmakuSetting.isBundleSource()) return false;",
                "return NodeRuntime.ownsLoopbackPort(url);");
        // 形态判据（回环 + path 恰好 /danmaku）不是归属证明：用户自建的回环弹幕服务地址与配置值
        // 不逐字相等时（localhost vs 127.0.0.1，或配置了内网地址）会被误当残留清掉，必须删除。
        assertFalse(source.contains("isBundleDanmakuEndpoint"));
        assertContainsAll(setter, "isStaleBundleDanmaku(url)", "clearDanmaku(\"stale_bundle_source\")");
        assertContainsAll(restore, "isStaleBundleDanmaku(currentDanmakuUrl)", "clearDanmaku(\"stale_bundle_source\")");
    }

    @Test
    public void sourceSwitchClearsTheDanmakuLeftByTheBundle() throws Exception {
        String player = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "player", "PlayerManager.java"));
        String drop = methodBody(player, "public void dropStaleBundleDanmaku()", "private static boolean isStaleBundleDanmaku(");
        assertContainsAll(drop, "isStaleBundleDanmaku(currentDanmakuUrl)", "clearDanmaku(\"stale_bundle_source\")");
        for (String flavor : new String[]{"leanback", "mobile"}) {
            String activity = read(sourcePath(flavor, "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java"));
            String event = methodBody(activity, "public void onConfigEvent(ConfigEvent event) {", "@Subscribe");
            assertContainsAll(event, "player().dropStaleBundleDanmaku();");
        }
    }

    private static void assertContainsAll(String source, String... values) {
        for (String value : values) assertTrue("Missing: " + value, source.contains(value));
    }

    private static String methodBody(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue("Missing method: " + start, from >= 0);
        assertTrue("Missing method boundary: " + end, to > from);
        return source.substring(from, to);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path sourcePath(String... parts) {
        Path path = Path.of("src", parts[0], parts[1]);
        for (int i = 2; i < parts.length; i++) path = path.resolve(parts[i]);
        if (Files.exists(path)) return path;
        path = Path.of("app", "src", parts[0], parts[1]);
        for (int i = 2; i < parts.length; i++) path = path.resolve(parts[i]);
        return path;
    }
}
