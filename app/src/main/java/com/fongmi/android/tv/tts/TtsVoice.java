package com.fongmi.android.tv.tts;

/** 朗读音色。 */
public final class TtsVoice {

    public final String id;
    public final String name;
    public final String lang;

    public TtsVoice(String id, String name, String lang) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.lang = lang == null ? "" : lang;
    }

    @Override
    public String toString() {
        return id;
    }
}
