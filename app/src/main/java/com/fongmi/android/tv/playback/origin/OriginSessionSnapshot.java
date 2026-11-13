package com.fongmi.android.tv.playback.origin;

import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.db.AppDatabase;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class OriginSessionSnapshot {
    private static final int MAX_JSON_BYTES = 32 * 1024 * 1024;
    private static final Set<String> SETTINGS = new HashSet<>(Arrays.asList("shell_proxy", "shell_proxy_url", "shell_proxy_rules", "shell_proxy_hosts", "doh", "language"));
    public final Config config;
    public final History history;
    private final JsonObject json;

    private OriginSessionSnapshot(JsonObject json) {
        this.json = json;
        config = json.has("config") && !json.get("config").isJsonNull() ? App.gson().fromJson(json.get("config"), Config.class) : null;
        history = json.has("history") ? App.gson().fromJson(json.get("history"), History.class) : null;
    }

    public static OriginSessionSnapshot capture(History history) {
        if (OriginSessionBootstrap.isWorker()) throw new IllegalStateException(OriginSessionContract.ISOLATION_ERROR);
        JsonObject json = new JsonObject();
        Config origin = Config.find(history.getCid());
        if (origin != null) {
            origin = App.gson().fromJson(App.gson().toJson(origin), Config.class);
            String url = origin.getUrl();
            if (url.startsWith("file:")) {
                // 已经合法的绝对 file URI 直接保留：Path.local 不解码，二次编码会把 %20 变成 %2520。
                try { origin.setUrl(new java.io.File(java.net.URI.create(url)).toURI().toString()); }
                catch (IllegalArgumentException legacy) { origin.setUrl(Path.local(url).toURI().toString()); }
            } else if (!url.contains("://") && Path.local(url).exists()) origin.setUrl(Path.local(url).toURI().toString());
        }
        json.add("config", App.gson().toJsonTree(origin));
        json.add("history", App.gson().toJsonTree(history.copy()));
        json.add("siteSettings", App.gson().toJsonTree(Site.findAll().stream().filter(site -> site.getKey().equals(history.getSiteKey())).toList()));
        JsonObject settings = new JsonObject();
        for (String key : SETTINGS) if (Prefers.getPrefers().contains(key)) settings.add(key, App.gson().toJsonTree(Prefers.getPrefers().getAll().get(key)));
        json.add("appSettings", settings);
        json.add("originCredentials", new JsonObject());
        return new OriginSessionSnapshot(json);
    }

    public static OriginSessionSnapshot read(ParcelFileDescriptor fd) throws Exception {
        JsonObject value = App.gson().fromJson(readJson(fd), JsonObject.class);
        if (value == null || !value.has("config") || !value.has("history") || !value.has("appSettings") || !value.has("siteSettings")) throw new IllegalArgumentException(OriginSessionContract.SNAPSHOT_INVALID);
        return new OriginSessionSnapshot(value);
    }

    public void seed(OriginStorageContext context, int originCid, String key) {
        if (history == null || history.getCid() != originCid || key == null || !key.equals(history.getKey()) || (config != null && config.getId() != originCid)) throw new IllegalArgumentException(OriginSessionContract.SNAPSHOT_INVALID);
        AppDatabase.installOrigin(context);
        AppDatabase database = AppDatabase.get();
        database.runInTransaction(() -> {
            if (config != null) database.getConfigDao().insertOrUpdate(config);
            for (JsonElement element : json.getAsJsonArray("siteSettings")) database.getSiteDao().insertOrUpdate(App.gson().fromJson(element, Site.class));
        });
        SharedPreferences.Editor editor = Prefers.getPrefers().edit();
        for (java.util.Map.Entry<String, JsonElement> entry : json.getAsJsonObject("appSettings").entrySet()) {
            if (!SETTINGS.contains(entry.getKey())) throw new IllegalArgumentException(OriginSessionContract.SNAPSHOT_INVALID);
            JsonElement value = entry.getValue();
            if (value.getAsJsonPrimitive().isBoolean()) editor.putBoolean(entry.getKey(), value.getAsBoolean());
            else if (value.getAsJsonPrimitive().isNumber()) editor.putInt(entry.getKey(), value.getAsInt());
            else editor.putString(entry.getKey(), value.getAsString());
        }
        if (config != null) editor.putString("config_0", config.getUrl());
        if (!editor.commit()) throw new IllegalStateException(OriginSessionContract.ISOLATION_ERROR);
    }

    public ParcelFileDescriptor descriptor(File directory) throws Exception { return writeJson(directory, json); }
    public static ParcelFileDescriptor writeJson(File directory, Object value) throws Exception {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException(OriginSessionContract.ISOLATION_ERROR);
        File file = File.createTempFile("origin-json-", ".json", directory);
        try {
            byte[] bytes = App.gson().toJson(value).getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_JSON_BYTES) throw new IllegalArgumentException(OriginSessionContract.SNAPSHOT_INVALID);
            try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
            ParcelFileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            if (!file.delete()) { fd.close(); throw new IllegalStateException(OriginSessionContract.ISOLATION_ERROR); }
            return fd;
        } catch (Exception e) { file.delete(); throw e; }
    }
    public static String readJson(ParcelFileDescriptor fd) throws Exception {
        if (fd == null) throw new IllegalArgumentException(OriginSessionContract.SNAPSHOT_INVALID);
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(fd); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) >= 0) { if (output.size() + count > MAX_JSON_BYTES) throw new IllegalArgumentException(OriginSessionContract.SNAPSHOT_INVALID); output.write(buffer, 0, count); }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
    public static String revision(Config config) {
        if (config == null) return "missing";
        return digest(App.gson().toJson(config));
    }
    public static String resumeVersion(History history) {
        return history == null ? "missing" : digest(history.getCid() + "\n" + history.getKey() + "\n" + history.getCreateTime() + "\n" + history.getPosition() + "\n" + history.getDuration() + "\n" + history.getEpisodeUrl() + "\n" + history.getSourceBindingKey() + "\n" + history.getTmdbId() + "\n" + history.getTmdbSeasonNumber() + "\n" + history.getTmdbEpisodeNumber());
    }
    private static String digest(String value) {
        try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); for (byte item : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", item & 255)); return out.toString(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
