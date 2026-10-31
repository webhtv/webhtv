package com.fongmi.android.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.server.proxy.MultiThreadProxy;
import com.fongmi.android.tv.cache.CacheScheduler;
import com.fongmi.android.tv.playback.PlaybackRemoteSyncer;
import com.fongmi.android.tv.player.MediaBadgeCatalog;
import com.fongmi.android.tv.player.PlaybackMemoryMonitor;
import com.fongmi.android.tv.player.PlaybackSystemConditionMonitor;
import com.fongmi.android.tv.player.mpv.PlaybackRecoveryMonitor;
import com.fongmi.android.tv.plugin.PluginBootstrap;
import com.fongmi.android.tv.plugin.PluginController;
import com.fongmi.android.tv.plugin.PluginHostEvents;
import com.fongmi.android.tv.remote.RemoteAgent;
import com.fongmi.android.tv.setting.AppBranding;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.subtitle.provider.SubtitleProviderRegistry;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.utils.DanmakuSearchListFocusFixer;
import com.fongmi.android.tv.utils.NsdDeviceDiscovery;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PreviousProcessExitLogger;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.WebViewDataDirectoryGuard;
import com.fongmi.hook.Hook;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.Init;
import com.google.gson.Gson;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private static volatile App instance;

    private final Handler handler;
    private final Gson gson;
    private final long time;

    private final Runnable backgroundServicesStarter = this::startBackgroundServicesNow;

    private volatile Activity activity;
    private volatile int foregroundActivities;
    private Hook hook;

    /** 同一语言上语言更新连续失败的次数上限，超过后放弃该语言，避免热路径无限重试。 */
    private static final int MAX_RESOURCES_APPLY_ATTEMPTS = 3;

    private final Object resourcesLock = new Object();
    private volatile Resources resources;
    private volatile int resourcesLanguage = Integer.MIN_VALUE;
    private int resourcesFailedLanguage = Integer.MIN_VALUE;
    private int resourcesFailures;

    public App() {
        instance = this;
        gson = new Gson();
        time = System.currentTimeMillis();
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    public static App get() {
        return instance;
    }

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity;
    }

    public static boolean isForeground() {
        App app = get();
        return app != null && app.foregroundActivities > 0;
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }

    public void setHook(Hook hook) {
        this.hook = hook;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (PlaybackRecoveryMonitor.isRecoveryProcess(base)) return;
        WebViewDataDirectoryGuard.clearStaleLock(base);
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (PlaybackRecoveryMonitor.isRecoveryProcess(this)) return;
        PlaybackMemoryMonitor.process().initialize(this);
        PlaybackSystemConditionMonitor.process().initialize(this);
        Setting.applyLanguage();
        ThemeController.applyNightModeToApp();
        AppBranding.applyLauncherIcon(this);
        DebugLogStore.restoreEnabled();
        if (DebugLogStore.isEnabled()) {
            PlaybackRecoveryMonitor.logPreviousResult(this);
            Setting.logDebugEnvironment("restore");
            PreviousProcessExitLogger.log(this);
        }
        Notify.createChannel();
        ProxySetting.apply();
        registerActivityLifecycleCallbacks(this);
        registerContentHandlers();
        resumeBackgroundServices();
        warmDeviceIdentity();
        warmSubtitleProviders();
        warmMediaBadgePatterns();
        // 插件装载会读盘并初始化脚本引擎（阻塞），必须离开主线程；失败也已在本层吞掉。
        Task.execute(PluginBootstrap::installAndReloadQuietly);
        post(() -> CacheScheduler.get().start(), 30_000L);
    }

    /**
     * 字幕提供方注册表的首次初始化会同步启动 CPython 并导入 webhtv_logging / app 模块
     * （设备实测约 0.9s，debuggerd 抓栈显示主线程停在 libpython3.10.so）。
     * 它过去发生在播放页 Activity 的构造期
     * （VideoActivity / TmdbDetailActivity -> SubtitlePlaybackSession -> DefaultController
     * -> SubtitleMatchService -> SubtitleProviderRegistry.get()），正好压在首帧窗口里。
     * 现在提前到 App 启动的后台线程，用户从首页点进播放页时这份成本已经付完。
     */
    private void warmSubtitleProviders() {
        Task.execute(SubtitleProviderRegistry::get);
    }

    /**
     * 设备身份的首次探测要 spawn 一个 shell 子进程取 ro.serialno，再枚举两次网卡读 MAC，
     * 全是阻塞调用；过去第一次发生在播放页进场窗口里（设备实测在主线程 tid 上抓到该 shell 命令）。
     * 现在同样提前到 App 启动的后台线程，等主线程真正需要身份时缓存已就绪。
     */
    private void warmDeviceIdentity() {
        Task.execute(Device::warmIdentity);
    }

    /**
     * 徽标清单里 127 条正则的首次编译是同步的（源码注释估约 55ms），过去发生在
     * 播放页进场窗口的主线程上（debuggerd 栈：MediaBadgeCatalog.patterns
     * <- MediaBadgeResolver.addSourceBadges <- resolve <- PlayerOsdController.setMediaBadges）。
     * 现在同样提前到 App 启动的后台线程，首次渲染 OSD 时 patterns() 直接命中缓存。
     */
    private void warmMediaBadgePatterns() {
        // 总开关关上时 setMediaBadges() 会在解析前就返回，127 条正则永远不会编译，
        // 这时预热只是白白在后台跑一遍编译。
        if (!PlayerSetting.isDisplayMediaBadges()) return;
        Task.execute(MediaBadgeCatalog::patterns);
    }

    private void registerContentHandlers() {
        // 猫源动作项排最前：它的判定最便宜（只比字符串），且命中就该直接开网页，
        // 不该让音频/阅读器 handler 先按站点规则把它认走
        com.fongmi.android.tv.content.ContentDispatcher.registerHandler(new com.fongmi.android.tv.content.CatActionContentHandler());
        com.fongmi.android.tv.content.ContentDispatcher.registerHandler(new com.fongmi.android.tv.content.GameContentHandler());
        com.fongmi.android.tv.content.ContentDispatcher.registerHandler(new com.fongmi.android.tv.content.AudioContentHandler());
        com.fongmi.android.tv.content.ContentDispatcher.registerHandler(new com.fongmi.android.tv.content.ReaderContentHandler());
        registerReaderFallback();
    }

    private void registerReaderFallback() {
        Product.registerReaderFallback();
    }

    @Override
    public void onTrimMemory(int level) {
        if (!PlaybackRecoveryMonitor.isRecoveryProcess(this)) PlaybackMemoryMonitor.process().onTrimMemory(level);
        super.onTrimMemory(level);
    }

    @Override
    public void onLowMemory() {
        if (!PlaybackRecoveryMonitor.isRecoveryProcess(this)) PlaybackMemoryMonitor.process().onLowMemory();
        super.onLowMemory();
    }

    private void startBackgroundServicesNow() {
        SpiderDebug.log("startup", "background services start cost=%sms", System.currentTimeMillis() - time);
        Server.get().start();
        startMultiThreadProxy();
        PlaybackRemoteSyncer.start();
        RemoteAgent.get().start();
        NsdDeviceDiscovery.register();
        com.fongmi.android.tv.lab.LabAutoStart.start(this);
        SpiderDebug.log("startup", "background services ready cost=%sms", System.currentTimeMillis() - time);
    }

    private void startMultiThreadProxy() {
        try {
            var snapshot = MultiThreadProxy.applyStored();
            SpiderDebug.log("proxy",
                    "multi-thread proxy enabled=%s ready=%s port=%s revision=%s",
                    snapshot.config().enabled(),
                    snapshot.ready(),
                    snapshot.actualPort(),
                    snapshot.configRevision());
        } catch (Exception e) {
            SpiderDebug.log("proxy", "multi-thread proxy start failed error=%s", e.getMessage());
        }
    }

    public static void resumeBackgroundServices() {
        removeCallbacks(get().backgroundServicesStarter);
        DanmakuSearchListFocusFixer.start();
        post(get().backgroundServicesStarter, 1200);
    }

    public static void stopBackgroundServices() {
        removeCallbacks(get().backgroundServicesStarter);
        DanmakuSearchListFocusFixer.stop();
        MultiThreadProxy.stop();
        PlaybackRemoteSyncer.stop();
        RemoteAgent.get().stop();
        NsdDeviceDiscovery.unregister();
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }

    /**
     * 全应用的语言资源入口：{@code ResUtil} 与几乎所有界面都经由这里取 Resources，
     * 并且调用方可能来自任意线程（包括 Task 线程池），所以这里必须是非阻塞安全的。
     * <p>
     * 语言切换会把新 Configuration 写进框架自己持有的 Resources，而
     * {@code updateConfiguration} 不是线程安全的：缓存未命中时若无锁，两个线程会同时改写同一个
     * Resources，WebView 在 Android 9 挂到这个 AssetManager 上的资源包就可能被写坏。
     * 另外这条路径一旦抛异常，异常会穿透每一个取资源的调用点（含系统回调），所以更新失败只记日志并
     * 继续返回框架 Resources，让界面以系统语言降级显示，而不是把异常散播到全应用。
     * <p>
     * 更新失败时<b>不</b>把语言记成已应用：否则同一语言的后续调用会命中快路径，
     * 失败将被永久固化到下次切语言或重启。这里只缓存 Resources（避免反复 super.getResources()），
     * 语言标记留在未命中态，下次取资源时自动重试。
     * <p>
     * 但重试必须有限：{@code getResources()} 是热路径（每个 TextView 都可能调用），
     * 若该语言的更新恒定失败，无限重试等于每次取资源都重算 Configuration 并刷一条日志。
     * 因此同一语言连续失败 {@link #MAX_RESOURCES_APPLY_ATTEMPTS} 次后放弃该项语言：
     * 认领该语言、固定返回框架 Resources，让热路径恢复成一次 volatile 读。放弃状态在语言变化
     * （{@link #resourcesFailedLanguage} 随之失配）或被 {@link #invalidateResources()} 显式重置后才解除。
     */
    @Override
    @SuppressWarnings("deprecation")
    public Resources getResources() {
        int language = Setting.getLanguage();
        Resources cached = resources;
        if (cached != null && resourcesLanguage == language) return cached;
        synchronized (resourcesLock) {
            if (resources != null && resourcesLanguage == language) return resources;
            if (shouldRetryResourcesApply(language)) {
                Resources resources = super.getResources();
                try {
                    Configuration configuration = Setting.wrapLanguage(getBaseContext()).getResources().getConfiguration();
                    // WebView adds its resource package to the framework-owned AssetManager on Android 9.
                    resources.updateConfiguration(configuration, resources.getDisplayMetrics());
                    resourcesFailedLanguage = Integer.MIN_VALUE;
                    resourcesFailures = 0;
                    this.resources = resources;
                    resourcesLanguage = language;
                } catch (RuntimeException e) {
                    recordResourcesApplyFailure(language, e);
                    // 失败只缓存 Resources 本身、不认领语言：下一轮同语言调用会再试一次。
                    this.resources = resources;
                    resourcesLanguage = Integer.MIN_VALUE;
                    if (resourcesFailures >= MAX_RESOURCES_APPLY_ATTEMPTS) {
                        // 放弃该语言：认领语言让热路径恢复为一次 volatile 读，避免每次取资源都重算并刷日志。
                        SpiderDebug.log("resources", "apply language gave up language=%s attempts=%d", language, resourcesFailures);
                        resourcesLanguage = language;
                    }
                }
                return resources;
            }
            // 已放弃该语言：不再重算 Configuration，直接返回框架 Resources。
            Resources fallback = this.resources != null ? this.resources : super.getResources();
            this.resources = fallback;
            resourcesLanguage = language;
            return fallback;
        }
    }

    /** 该语言是否还值得再尝试一次；同一语言连续失败到上限后放弃，直到语言变化或显式失效。 */
    private boolean shouldRetryResourcesApply(int language) {
        return resourcesFailedLanguage != language || resourcesFailures < MAX_RESOURCES_APPLY_ATTEMPTS;
    }

    private void recordResourcesApplyFailure(int language, RuntimeException e) {
        if (resourcesFailedLanguage == language) {
            resourcesFailures++;
        } else {
            resourcesFailedLanguage = language;
            resourcesFailures = 1;
        }
        SpiderDebug.log("resources", "apply language failed language=%s attempt=%d error=%s", language, resourcesFailures, e.getMessage());
    }

    public void invalidateResources() {
        synchronized (resourcesLock) {
            resources = null;
            resourcesLanguage = Integer.MIN_VALUE;
            resourcesFailedLanguage = Integer.MIN_VALUE;
            resourcesFailures = 0;
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity != activity()) this.activity = activity;
        PluginHostEvents.appForeground();
        PluginController.safeOnResumed(activity);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity == activity()) this.activity = null;
        PluginHostEvents.appBackground();
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        PluginHostEvents.activityCreated(activity);
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        PluginHostEvents.activityDestroyed(activity);
        PluginController.safeOnDestroyed(activity);
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        foregroundActivities++;
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        foregroundActivities = Math.max(0, foregroundActivities - 1);
    }
}
