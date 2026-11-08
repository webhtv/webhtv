package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 交给运行时的配置信封：{@code {"id":..,"version":"1.0.0","params":{..},"permissions":[..],"timeout":15}}。
 *
 * <p><b>为什么需要信封而不是直接把参数 JSON 传下去。</b>{@link PluginRuntime#init} 只有一个
 * {@code config} 字符串入口，而运行时需要三样互不相干的东西：插件自己定义的参数、宿主授予的权限、
 * 清单里的超时预算。把它们塞进同一个信封，比给接口加三个参数更省——加参数会让
 * {@link PluginRuntime} 从"四个方法的脚本生命周期"退化成"宿主内部结构的镜像"。
 *
 * <p><b>为什么信封里还要带 {@code version}。</b>D2 放弃域名白名单之后，
 * {@link PluginAuditLog} 是插件外发请求的唯一观测面，而它存在的意义是"定位到具体 id 与版本"。
 * 装载器（{@code PluginHost}）手上有清单，运行时的网络桥只有插件 id；把版本放进信封，
 * 网络桥才能在不反向依赖装载器的前提下把审计记完整。
 *
 * <p><b>脚本侧只看到 {@code params}。</b>运行时把信封拆开后，只把 {@code params} 注入成
 * {@code globalThis.webhtv.config}；{@code permissions} 由宿主侧消费（比如决定是否放行
 * {@code webhtv.request}），不暴露给脚本——脚本能读到自己的权限声明毫无用处，
 * 只会诱导插件作者在 JS 里做权限判断。
 *
 * <p>纯 JVM，可直接单测。
 */
public final class PluginConfig {

    private PluginConfig() {
    }

    /**
     * 为一个插件构造配置信封。
     *
     * @param manifest  清单（提供 id、权限、超时与参数模板）
     * @param params    已做占位符替换的参数；{@code null} 视为空对象
     */
    public static String create(PluginManifest manifest, @Nullable JsonObject params) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("id", manifest == null ? "" : manifest.getId());
        // 空串而不是省略键：信封形状恒定，读取方只判断值（见 versionOf）。
        envelope.addProperty("version", manifest == null ? "" : manifest.getVersion());
        envelope.add("params", params == null ? new JsonObject() : params);
        envelope.addProperty("timeout", manifest == null ? 15 : manifest.getTimeoutSeconds());
        JsonArray permissions = new JsonArray();
        if (manifest != null) {
            for (String permission : manifest.getPermissions()) permissions.add(permission);
        }
        envelope.add("permissions", permissions);
        return envelope.toString();
    }

    /** 取出参数对象；信封非法时返回空对象。 */
    public static JsonObject paramsOf(@Nullable String config) {
        JsonObject envelope = parse(config);
        JsonElement params = envelope.get("params");
        return params != null && params.isJsonObject() ? params.getAsJsonObject() : new JsonObject();
    }

    /** 信封里声明的权限集合；信封非法时返回空集。 */
    public static Set<String> permissionsOf(@Nullable String config) {
        JsonObject envelope = parse(config);
        Set<String> result = new LinkedHashSet<>();
        JsonElement element = envelope.get("permissions");
        if (element == null || !element.isJsonArray()) return result;
        for (JsonElement permission : element.getAsJsonArray()) {
            if (permission == null || !permission.isJsonPrimitive()) continue;
            String value = permission.getAsString();
            if (value != null && !value.isEmpty()) result.add(value);
        }
        return result;
    }

    /**
     * 信封里的插件版本；缺失、空白或非法（非原始值）时返回 {@code null}。
     *
     * <p><b>为什么用 {@code null} 而不是空串表示"没有版本"。</b>审计里的版本字段本身可空，
     * 而"旧信封压根没写版本"与"清单里版本就是空"对追责的含义不同；这里保留这个区别，
     * 由调用方（网络桥）原样交给审计，不替它编一个看起来像真的版本号。
     */
    @Nullable
    public static String versionOf(@Nullable String config) {
        JsonObject envelope = parse(config);
        JsonElement element = envelope.get("version");
        if (element == null || !element.isJsonPrimitive()) return null;
        try {
            String value = element.getAsString();
            return value == null || value.isBlank() ? null : value;
        } catch (RuntimeException error) {
            return null;
        }
    }

    /**
     * 信封里的超时秒数；缺失、非法或越界（不在 1..{@link PluginNetBridge#MAX_TIMEOUT_SECONDS} 内）
     * 一律回落到 {@code fallback}。
     *
     * <p><b>为什么钳制收敛到这一个读点。</b>信封是宿主写、运行时读的唯一超时来源，越界值只可能
     * 从这里被读出来；把范围判定钉在读点上，"多少秒算合法"就只有一份定义。网络桥仍留着同范围的
     * 兜底检查——它是公共入口，测试与其它调用方会绕过信封直接传秒数，那条路必须自己守住；
     * 两边共用 {@link PluginNetBridge#MAX_TIMEOUT_SECONDS}，范围不会被抄成两个数字。
     */
    public static int timeoutOf(@Nullable String config, int fallback) {
        JsonObject envelope = parse(config);
        JsonElement element = envelope.get("timeout");
        if (element == null || !element.isJsonPrimitive()) return fallback;
        int value;
        try {
            value = element.getAsInt();
        } catch (RuntimeException error) {
            return fallback;
        }
        return value < 1 || value > PluginNetBridge.MAX_TIMEOUT_SECONDS ? fallback : value;
    }

    private static JsonObject parse(@Nullable String config) {
        if (config == null || config.isBlank()) return new JsonObject();
        try {
            JsonElement element = JsonParser.parseString(config);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException error) {
            return new JsonObject();
        }
    }
}
