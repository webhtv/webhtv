package com.fongmi.android.tv.tts;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 自定义在线朗读引擎配置（与 legado 在线朗读引擎字段兼容的最小子集）。
 *
 * 只保留真正被使用到的字段：名称、URL 规则、Content-Type、Header。
 * legado 的 {@code loginUrl} / {@code loginUi} / {@code loginCheckJs} / {@code jsLib}
 * 需要规则引擎与 JS 运行时支撑，本实现不引入（见任务文档 §3 的取舍决策）。
 */
public final class TtsEngineConfig {

    public String name = "";
    public String url = "";
    public String contentType = "audio/mpeg";
    public String header = "";

    public static TtsEngineConfig fromJson(String json) {
        if (json == null) return null;
        try {
            JsonObject o = JsonParser.parseString(json.trim()).getAsJsonObject();
            TtsEngineConfig config = new TtsEngineConfig();
            config.name = optString(o, "name");
            config.url = optString(o, "url");
            config.contentType = optString(o, "contentType");
            config.header = optString(o, "header");
            if (config.contentType.isEmpty()) config.contentType = "audio/mpeg";
            return config.url.isEmpty() ? null : config;
        } catch (Throwable e) {
            return null;
        }
    }

    private static String optString(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return "";
        return o.get(key).getAsString();
    }

    public String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name == null ? "" : name);
        o.addProperty("url", url == null ? "" : url);
        o.addProperty("contentType", contentType == null ? "" : contentType);
        o.addProperty("header", header == null ? "" : header);
        return new Gson().toJson(o);
    }

    public String displayName() {
        if (name != null && !name.trim().isEmpty()) return name.trim();
        String u = url == null ? "" : url.trim();
        int cut = u.indexOf(',');
        if (cut > 0) u = u.substring(0, cut);
        return u.length() > 48 ? u.substring(0, 48) + "…" : u;
    }
}
