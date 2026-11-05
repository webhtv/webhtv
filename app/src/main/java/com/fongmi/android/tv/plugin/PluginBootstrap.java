package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * 插件系统的启动接线：注册运行时、按持久化配置装载，并保证这两步永不影响 App 启动。
 *
 * <p><b>为什么需要这一层。</b>{@link PluginController} 只负责装配与路由，它不知道
 * "插件从哪来、用哪个引擎跑"。让 {@code App} 直接写这两件事会把"插件来源"的知识
 * 漏进 30KB+ 的 Application 类；而让 {@code PluginController} 自己找来源又会让它在
 * 单测里被迫依赖 SharedPreferences 与文件系统。这里作为唯一的接线点，App 只需要一行。
 *
 * <p><b>为什么全部方法都不抛异常。</b>调用点在 {@code App.onCreate} 投递的后台任务里。
 * 后台任务抛出的异常会被线程池吞掉并留不下任何可读现场；更糟的是若将来有人在主线程
 * 直接调用，插件系统的任何故障都会变成启动崩溃。插件是可选功能，它的失败不该拖垮宿主。
 *
 * <p><b>为什么 {@link #install()} 幂等。</b>运行时注册表挂在
 * {@link PluginController#get()} 的单例上，而 {@code PluginController.reset()} 会丢弃它。
 * 注册表一旦重建，旧注册就没了；因此每次装载前都重放一次注册，用"幂等 + 便宜"换取
 * "不会因为别处 reset 过而静默失去引擎"。
 */
public final class PluginBootstrap {

    /** 本宿主当前唯一落地的运行时引擎。py/html 见 {@link PluginRuntimeFactory#KNOWN_APIS}，尚未实现。 */
    private static final String RUNTIME_JS = "js";

    private PluginBootstrap() {
    }

    /**
     * 注册本版本实现的脚本运行时。
     *
     * <p>可重复调用：{@link PluginRuntimeFactory#register} 按 api 覆盖写入，重复注册
     * 只是把一个等价实例换成另一个，不会产生重复项。
     */
    public static void install() {
        try {
            PluginController.get().registerRuntime(RUNTIME_JS, new QuickJsPluginRuntime());
        } catch (Throwable ignored) {
            // 构造 QuickJsPluginRuntime 不加载 so（原生库在首次 init 时懒加载），
            // 理论上不会失败；真失败时让后续 reload 走 UnsupportedRuntime 的明确诊断路径。
        }
    }

    /**
     * 按持久化配置装载全部启用插件。
     *
     * <p>阻塞执行（脚本初始化是同步的），因此只能在后台线程调用。
     *
     * @return 是否装载了至少一个插件
     */
    public static boolean reloadAll() {
        try {
            install();
            List<PluginHost.LoadedPlugin> loaded = PluginController.get()
                    .reload(PluginSourceConfig.get().toManifests(), null, PluginBootstrap::configOf);
            return loaded != null && !loaded.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * {@link #reloadAll()} 的安全封装：供 {@code App.onCreate} 与后台线程直接引用为 Runnable。
     *
     * <p>只做一件事：调用 {@link #reloadAll()}（注册运行时 + 按持久化配置重新装载）。插件的唯一
     * 来源是用户自己导入，所以装载前不需要先往磁盘上放任何宿主自带的插件。
     * 调用点在 {@code App.onCreate} 投递的后台任务里，也可能在用户导入插件包之后：两处都是
     * "配置刚变化，重新装载一次"的时机。
     *
     * <p>任何失败都被吞掉，绝不抛给调用方。
     */
    public static void installAndReloadQuietly() {
        reloadAll();
    }

    /**
     * 构造单个插件的配置信封：参数模板经用户填写的环境变量替换后交给运行时。
     *
     * <p>与字幕源 {@code T3SubtitleProvider} 同一形状——模板来自插件包（清单 {@code params}），
     * 值来自用户（设置页 {@code plugin_environment}），两者在装载时才合并。合并放这里而不是
     * {@code PluginHost}：宿主不该知道"参数模板"和"用户环境"是两份数据。
     */
    @Nullable
    private static String configOf(PluginManifest manifest) {
        try {
            JsonObject environment = PluginEnvironmentStore.get().getJson(manifest.getId());
            JsonObject params = PluginEnvironment.resolve(manifest.getParams(), environment);
            return PluginConfig.create(manifest, params);
        } catch (Throwable error) {
            // 单个插件的配置拼装失败不能拖垮整批装载：回落成空配置，让它在 init 阶段明确报错。
            return null;
        }
    }
}
