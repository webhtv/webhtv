package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Map;

/**
 * 插件参数模板的环境变量替换。
 *
 * <p>形状照 {@code subtitle/source/SubtitleSourceEnvironment}——那套 "${KEY} 占位符 + 用户填写
 * 环境 JSON" 的机制在字幕源上已经跑了一年多，插件沿用同一套心智，用户不必学第二种配置方式。
 *
 * <p><b>与字幕源的两处刻意差异。</b>第一，字幕源只替换顶层字符串值，这里递归进对象与数组：
 * 插件的参数（比如某接口的 header 组）天然嵌套，只认顶层会逼插件作者把结构拍平。
 * 第二，字幕源把缺失变量替换成空串，这里同样替换成空串——但**只对整值即占位符的情况**替换，
 * 不做字符串内插（"Bearer ${TOKEN}" 这种半截占位符保持原样），否则用户看不到自己漏填了。
 *
 * <p>纯 JVM：不碰 Android API，可以直接单测。
 */
public final class PluginEnvironment {

    private PluginEnvironment() {
    }

    /**
     * 用用户填写的环境替换参数模板里的占位符。
     *
     * @param template    清单 {@code params}（不会被修改）
     * @param environment 用户填写的键值对；{@code null} 视为空
     * @return 替换后的新树；模板为 {@code null} 时返回空对象
     */
    public static JsonObject resolve(@Nullable JsonElement template, @Nullable JsonObject environment) {
        JsonObject source = environment == null ? new JsonObject() : environment;
        JsonElement resolved = resolveValue(template, source);
        return resolved != null && resolved.isJsonObject() ? resolved.getAsJsonObject() : new JsonObject();
    }

    @Nullable
    private static JsonElement resolveValue(@Nullable JsonElement element, JsonObject environment) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                JsonElement value = resolveValue(entry.getValue(), environment);
                if (value != null) result.add(entry.getKey(), value);
            }
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement value : element.getAsJsonArray()) {
                JsonElement resolved = resolveValue(value, environment);
                if (resolved != null) result.add(resolved);
            }
            return result;
        }
        if (!element.getAsJsonPrimitive().isString()) return element.deepCopy();
        String raw = element.getAsString();
        String name = placeholderName(raw);
        if (name == null) return new JsonPrimitive(raw);
        JsonElement value = environment.get(name);
        // 用户没填就落成空串而不是保留占位符：插件那边看到的永远是"空值"，
        // 不会把 ${ASSRT_TOKEN} 这种字面量当真实 token 发出去。
        return value == null || value.isJsonNull() ? new JsonPrimitive("") : value.deepCopy();
    }

    /** 整值占位符才认：形如 {@code ${NAME}}，首字母大写，其余大写/数字/下划线。 */
    @Nullable
    static String placeholderName(@Nullable String value) {
        if (value == null || value.length() < 4 || !value.startsWith("${") || !value.endsWith("}")) return null;
        String name = value.substring(2, value.length() - 1);
        if (name.isEmpty() || name.charAt(0) < 'A' || name.charAt(0) > 'Z') return null;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c < 'A' || c > 'Z') && (c < '0' || c > '9') && c != '_') return null;
        }
        return name;
    }
}
