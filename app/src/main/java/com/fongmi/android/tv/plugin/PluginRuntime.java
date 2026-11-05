package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

/**
 * 插件运行时接口。形状照 {@code subtitle/source/SubtitleScriptRuntime}——那个接口已证明
 * 在本项目可以用四个方法覆盖一个非 Java 脚本宿主的完整生命周期（init / 查询 / 动作 / destroy）。
 *
 * <p><b>为什么本接口不带 Android 依赖。</b>具体实现（QuickJS、Chaquopy、WebView）都要加载
 * 原生库，无法在普通 JVM 单测里跑。把接口留在纯 JVM，就能用假实现完整验证
 * {@link PluginHost} 的路由、门禁与故障隔离逻辑，而不必依赖仪器化测试。
 *
 * <p><b>实现方必须保证线程安全。</b>{@code PluginHost} 会在主线程调用 {@link #onEvent}，
 * 而脚本执行可能阻塞；实现方自行决定是否切线程，但不能让宿主主线程等待。
 *
 * <p><b>实现方必须容忍重复 {@link #destroy}。</b>禁用、卸载、进程退出都会调用它。
 */
public interface PluginRuntime {

    /**
     * 初始化插件。
     *
     * @param pluginId  插件 id（用于日志与缓存分键）
     * @param entry     入口脚本路径或 URL
     * @param config    插件配置（清单序列化后的 JSON）
     * @return 初始化结果，可为 {@code null}
     * @throws Exception 初始化失败；{@code PluginHost} 会记录并禁用该插件
     */
    String init(String pluginId, String entry, String config) throws Exception;

    /**
     * 派发一个事件给插件。
     *
     * @param payload 事件负载（已由宿主构造，不含敏感字段）
     * @return 脚本返回值，可为 {@code null}
     */
    String onEvent(String pluginId, String event, String payload) throws Exception;

    /**
     * 执行一个插件动作（UI 槽位被点击时触发）。
     *
     * @param action  动作标识，来自清单 {@code ui[].action}
     * @return 脚本返回值，可为 {@code null}
     */
    String action(String pluginId, String action, String payload) throws Exception;

    /** 销毁插件，释放脚本上下文与线程。重复调用必须安全。 */
    void destroy(String pluginId);
}
