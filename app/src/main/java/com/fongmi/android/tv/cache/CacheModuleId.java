package com.fongmi.android.tv.cache;

public enum CacheModuleId {
    EXO("playback.exo"),
    MPV_HLS("playback.mpv_hls"),
    MPV_DEMUXER("playback.mpv_demuxer"),
    MPV_RUNTIME("playback.mpv_runtime"),
    LYRICS("media.lyrics"),
    KARAOKE("media.karaoke"),
    WEBHOME_EXT("network.webhome_ext"),
    WEBHOME_RAW("network.webhome_raw"),
    EPG("network.epg"),
    GLIDE("image.glide"),
    PLUGIN_SCRIPTS("plugin.scripts"),
    TEMP_FILES("temp.file"),
    DIAGNOSTIC_LOGS("diagnostic.logs"),
    LEGACY_FILES("legacy.file"),
    UNCLASSIFIED("unclassified.cache");

    private final String id;

    CacheModuleId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
