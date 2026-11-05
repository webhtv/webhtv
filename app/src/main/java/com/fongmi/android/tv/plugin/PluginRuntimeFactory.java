package com.fongmi.android.tv.plugin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 按 {@code runtime.api} 分发到具体运行时。
 *
 * <p>形状照 {@code subtitle/source/SubtitleScriptRuntimeFactory}：那个类的价值不在实现，
 * 而在于**把"清单声明什么引擎"与"用哪个引擎跑"解耦**，且对未知引擎明确抛错而不是静默降级。
 *
 * <p><b>为什么这里是可注册的而不是固定 switch。</b>P2b 阶段还没有任何真实运行时
 * （QuickJS 适配器在 P2c），若写死 {@code switch(api)} 就只能靠 {@code null} 表达
 * "未实现"，而这会让 {@link PluginHost} 无法区分"引擎不存在"与"引擎加载失败"两种故障。
 * 改为注册表后，未注册的引擎一律走 {@link UnsupportedRuntime}，语义明确且可审计。
 *
 * <p>本类纯 JVM，可被单测完整覆盖。
 */
public final class PluginRuntimeFactory {

    /** 已知但尚未在本版本实现的引擎，用于给出准确诊断。 */
    private static final Set<String> KNOWN_APIS = Set.of("js", "py", "html");

    private final Map<String, PluginRuntime> runtimes;

    public PluginRuntimeFactory() {
        this.runtimes = new LinkedHashMap<>();
    }

    /** 供测试或后续切片注入真实运行时。{@code api} 大小写不敏感。 */
    public PluginRuntimeFactory register(@Nullable String api, @Nullable PluginRuntime runtime) {
        if (api == null || api.isBlank() || runtime == null) return this;
        runtimes.put(normalize(api), runtime);
        return this;
    }

    /**
     * 取引擎实例。
     *
     * @return 已注册的运行时；未注册时返回 {@link UnsupportedRuntime}（**不返回 null**）
     */
    @NonNull
    public PluginRuntime create(@Nullable String api) {
        String key = normalize(api);
        PluginRuntime runtime = runtimes.get(key);
        if (runtime != null) return runtime;
        return new UnsupportedRuntime(key);
    }

    /** 该引擎是否已在本进程实现。 */
    public boolean isSupported(@Nullable String api) {
        return runtimes.containsKey(normalize(api));
    }

    /** 清单声明了已知引擎但本版本没实现——诊断用，可区分"拼错了"与"还没做"。 */
    public static boolean isKnownApi(@Nullable String api) {
        return KNOWN_APIS.contains(normalize(api));
    }

    /** 已注册的引擎名快照。 */
    @NonNull
    public List<String> registeredApis() {
        return List.copyOf(runtimes.keySet());
    }

    /** 清空注册（测试用）。 */
    public void clear() {
        runtimes.clear();
    }

    private static String normalize(@Nullable String api) {
        return api == null ? "" : api.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 未实现引擎的占位运行时。
     *
     * <p>它**不会**静默吞掉调用：{@link #init} 抛错让 {@link PluginHost} 走故障隔离路径，
     * 从而在审计日志里留下"引擎未实现"这一条明确记录，而不是让插件表现为"什么都不做"。
     * 后者会让人误以为插件本身有问题。
     */
    static final class UnsupportedRuntime implements PluginRuntime {

        private final String api;

        UnsupportedRuntime(@Nullable String api) {
            this.api = api == null ? "" : api;
        }

        String api() {
            return api;
        }

        @Override
        public String init(String pluginId, String entry, String config) {
            throw new UnsupportedOperationException(describe());
        }

        @Override
        public String onEvent(String pluginId, String event, String payload) {
            throw new UnsupportedOperationException(describe());
        }

        @Override
        public String action(String pluginId, String action, String payload) {
            throw new UnsupportedOperationException(describe());
        }

        @Override
        public void destroy(String pluginId) {
            // 无资源可释放。
        }

        private String describe() {
            String reason = isKnownApi(api) ? "not implemented in this build" : "unknown runtime";
            return "Unsupported plugin runtime: " + (api.isEmpty() ? "<empty>" : api) + " (" + reason + ")";
        }
    }

    /** 供诊断输出：已注册引擎 + 已知但未实现的引擎。 */
    @NonNull
    public Map<String, String> describe() {
        Map<String, String> items = new LinkedHashMap<>();
        for (String api : KNOWN_APIS) {
            items.put(api, runtimes.containsKey(api) ? "ready" : "not implemented");
        }
        for (String api : Collections.unmodifiableSet(runtimes.keySet())) {
            if (!KNOWN_APIS.contains(api)) items.put(api, "ready");
        }
        return Collections.unmodifiableMap(items);
    }
}
