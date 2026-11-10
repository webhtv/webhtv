package com.fongmi.android.tv.bean;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;

public class MergeMeta {
    @SerializedName("lastMergeRun")
    public long lastMergeRun;
    @SerializedName("sources")
    public Map<String, SourceItem> sources = new HashMap<>();

    public static class SourceItem {
        @SerializedName("etag")
        public String etag;
    }
}
