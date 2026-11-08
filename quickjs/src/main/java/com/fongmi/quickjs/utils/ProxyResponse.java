package com.fongmi.quickjs.utils;

import com.fongmi.quickjs.bean.Res;
import com.github.catvod.utils.Json;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProxyResponse {

    private ProxyResponse() {
    }

    public static Object[] fromJson(String json) {
        Gson gson = new Gson();
        JsonElement element = gson.fromJson(json, JsonElement.class);
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException("Proxy response requires a JSON object");
        JsonObject root = element.getAsJsonObject();
        Res res = gson.fromJson(root, Res.class);
        if (res == null) throw new IllegalArgumentException("Proxy response requires status, MIME and body");
        JsonObject rawHeaders = Json.safeObject(root.get("headers"));
        for (Map.Entry<String, JsonElement> entry : rawHeaders.entrySet()) {
            requireSafe(entry.getKey(), "Proxy response header name");
            if (entry.getValue().isJsonPrimitive()) requireSafe(entry.getValue().getAsString(), "Proxy response header value");
        }
        Map<String, String> source = res.getHeader();
        String mime = contentType(source);
        requireSafe(mime, "Proxy response MIME");
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String name = entry.getKey();
            if (name.equalsIgnoreCase("Content-Type") || name.equalsIgnoreCase("Content-Length")
                    || name.equalsIgnoreCase("Transfer-Encoding") || name.equalsIgnoreCase("Content-Encoding")) continue;
            headers.put(name, entry.getValue());
        }
        return new Object[]{res.getCode(), mime, res.getStream(), headers};
    }

    private static String contentType(Map<String, String> headers) {
        if (headers.containsKey("Content-Type")) return headers.get("Content-Type");
        if (headers.containsKey("content-type")) return headers.get("content-type");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("Content-Type")) return entry.getValue();
        }
        return "application/octet-stream";
    }

    private static void requireSafe(String value, String message) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c == '\0') throw new IllegalArgumentException(message + " contains a control character");
        }
    }
}
