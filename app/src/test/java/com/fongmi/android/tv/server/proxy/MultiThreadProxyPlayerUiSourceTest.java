package com.fongmi.android.tv.server.proxy;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MultiThreadProxyPlayerUiSourceTest {

    @Test
    public void mobileAndLeanbackPlayersExposeConfigurableMultiThreadButton() throws Exception {
        String mobileLayout = read("mobile", "res", "layout", "view_control_vod_action.xml");
        String leanbackLayout = read("leanback", "res", "layout", "view_control_vod_action.xml");
        String mobile = read("mobile", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        String leanback = read("leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        String fusionLayout = read("main", "res", "layout", "activity_tmdb_detail.xml");
        String mobileFusionLayout = read("main", "res", "layout", "view_control_vod_action_tmdb.xml");
        String fusion = read("main", "java", "com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java");
        String catalog = read("main", "java", "com", "fongmi", "android", "tv", "setting", "PlayerButtonSetting.java");

        assertTrue(mobileLayout.contains("android:id=\"@+id/multiThreadProxy\""));
        assertTrue(leanbackLayout.contains("android:id=\"@+id/multiThreadProxy\""));
        assertTrue(fusionLayout.contains("android:id=\"@+id/playerMultiThreadProxy\""));
        assertTrue(mobileFusionLayout.contains("android:id=\"@+id/multiThreadProxy\""));
        assertTrue(catalog.contains("MULTI_THREAD_PROXY"));
        assertTrue(mobile.contains("MultiThreadProxyDialog.show(this"));
        assertTrue(leanback.contains("MultiThreadProxyDialog.show(this"));
        assertTrue(mobile.contains("addActionButton(PlayerButtonSetting.MULTI_THREAD_PROXY, mBinding.control.action.multiThreadProxy);"));
        assertTrue(leanback.contains("addActionButton(PlayerButtonSetting.MULTI_THREAD_PROXY, mBinding.control.action.multiThreadProxy);"));
        assertTrue(mobile.contains("player().reloadCurrentMediaItem()"));
        assertTrue(leanback.contains("player().reloadCurrentMediaItem()"));
        assertTrue(fusion.contains("binding.playerMultiThreadProxy.setOnClickListener"));
        assertTrue(fusion.contains("detailActionView(R.id.multiThreadProxy, View.class).setOnClickListener"));
        assertTrue(fusion.contains("PlayerButtonSetting.MULTI_THREAD_PROXY, binding.playerMultiThreadProxy"));
        assertTrue(fusion.contains("player().reloadCurrentMediaItem()"));
        // 回归：只登记 addActionButton 不足以让按钮可点，底部控制栏必须真正绑定点击监听器。
        assertTrue(mobile.contains("mBinding.control.action.multiThreadProxy.setOnClickListener(guarded(this::onMultiThreadProxy));"));
        assertTrue(leanback.contains("mBinding.control.action.multiThreadProxy.setOnClickListener(guarded(this::onMultiThreadProxy));"));
        assertTrue(fusion.contains("playerMultiThreadProxy.setOnClickListener"));
    }

    @Test
    public void multiThreadButtonIsHiddenByDefaultButStillUserConfigurable() throws Exception {
        String catalog = read("main", "java", "com", "fongmi", "android", "tv", "setting", "PlayerButtonSetting.java");

        assertTrue("多线程按钮必须在默认清单中标记为不显示",
                catalog.contains("new Item(MULTI_THREAD_PROXY, R.string.multi_thread_proxy_button, false)"));
        assertTrue("首次运行必须把默认不显示项写入隐藏偏好",
                catalog.contains("Prefers.put(HIDDEN, join(hidden));"));
        assertTrue("默认隐藏只能注入一次，之后以用户选择为准",
                catalog.contains("if (Prefers.getBoolean(HIDDEN_SEEDED)) return hidden;")
                        && catalog.contains("Prefers.put(HIDDEN_SEEDED, true);"));
        assertTrue("重置播放器按钮必须同时清除默认隐藏注入标记", catalog.contains("Prefers.remove(HIDDEN_SEEDED);"));
        assertTrue("用户仍可通过 putVisible 打开该按钮", catalog.contains("public static void putVisible(String id, boolean visible)"));
    }

    @Test
    public void dialogExposesExplicitEnableGlobalFallbackAndMultiDomainRules() throws Exception {
        String dialog = read("main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "MultiThreadProxyDialog.java");
        String layout = read("main", "res", "layout", "dialog_multi_thread_proxy.xml");

        assertTrue(layout.contains("android:id=\"@+id/enabled\""));
        assertTrue(layout.contains("android:id=\"@+id/rangeConcurrency\""));
        assertTrue(layout.contains("android:id=\"@+id/shardCount\""));
        assertTrue(layout.contains("android:id=\"@+id/domainRules\""));
        assertTrue(layout.contains("android:id=\"@+id/extractCurrentDomain\""));
        assertTrue(dialog.contains("ProxyDomainRuleSet.parse"));
        assertTrue(dialog.contains("MultiThreadProxy.apply"));
        assertTrue(dialog.contains("MultiThreadProxySetting.putDomainRules"));
        assertTrue(dialog.contains("ProxyDomainRuleSet.extractHost(currentUrl)"));
        assertTrue(dialog.contains("showApplyChoice(next, rules)"));
        assertTrue(dialog.contains("callback.onSaved(true)"));
    }

    @Test
    public void playerManagerRoutesEligiblePlaybackThroughOpaqueRegistrationOnlyWhenEnabled() throws Exception {
        String player = read("main", "java", "com", "fongmi", "android", "tv", "player", "PlayerManager.java");
        String server = read("main", "java", "com", "fongmi", "android", "tv", "server", "proxy", "MultiThreadProxyServer.java");

        assertTrue(player.contains("ProxyPlaybackPolicy.shouldProxy"));
        assertTrue(player.contains("MultiThreadProxy.register"));
        assertTrue(player.contains("ProxyStreamRegistration"));
        assertTrue(player.contains("closeMultiThreadProxyRegistration"));
        assertTrue(player.contains("public void reloadCurrentMediaItem()"));
        assertTrue(server.contains("domainRules.resolve(target, config)"));
        assertTrue(server.contains("sessionConfig"));
    }

    @Test
    public void internalPlayerRestartsReuseProxyPreparation() throws Exception {
        String player = read("main", "java", "com", "fongmi", "android", "tv", "player", "PlayerManager.java");

        assertTrue(player.contains("private void startWithProxy("));
        assertTrue(player.contains("private void restartWithProxy("));
        assertTrue(player.contains("prepareProxyPlayback(PlaySpec source)"));
        assertFalse(player.contains("engine.start(target.checkUa()"));
        assertFalse(player.contains("engine.start(spec.checkUa()"));
        assertFalse(player.contains("engine.restart(spec.checkUa()"));
        assertFalse(player.contains("ijk.restart(spec.checkUa()"));
        assertFalse(player.contains("recovery.target().checkUa()"));
    }

    private static String read(String... parts) throws Exception {
        Path path = Path.of("src");
        for (String part : parts) path = path.resolve(part);
        if (!Files.exists(path)) {
            path = Path.of("app", "src");
            for (String part : parts) path = path.resolve(part);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}