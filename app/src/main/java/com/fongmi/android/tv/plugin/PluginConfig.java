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
 * 交给运行时的配置信封：{@code {"id":..,"params":{..},"permissions":[..],"timeout":15}}。
 *
 * <p><b>为什么需要信封而不是直接把参数 JSON 传下去。</b>{@link PluginRuntime#init} 只有一个
 * {@code config} 字符串入口，而运行时需要三样互不相干的东西：插件自己定义的参数、宿主授予的权限、
 * 清单里的超时预算。把它们塞进同一个信封，比给接口加三个参数更省——加参数会让
 * {@link PluginRuntime} 从"四个方法的脚本生命周期"退化成"宿主内部结构的镜像"。
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

    /** 信封里的超时秒数；缺失或越界回落到 {@code fallback}。 */
    public static int timeoutOf(@Nullable String config, int fallback) {
        JsonObject envelope = parse(config);
        JsonElement element = envelope.get("timeout");
        if (element == null || !element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsInt();
        } catch (RuntimeException error) {
            return fallback;
        }
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
