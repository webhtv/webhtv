package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.update.OciArtifact;
import com.fongmi.android.tv.utils.AppVersion;

public class Update {

    public static final String CHANNEL_STABLE = "stable";
    public static final String CHANNEL_BETA = "beta";

    public String channel;
    public String name;
    public String versionName;
    public String desc;
    public String notes;
    public String apk;
    public String apkUrl;
    public String githubUrl;
    public String error;
    public String sha256;
    public OciArtifact oci;
    public int code;
    public long size;

    public static Update empty(String channel) {
        Update update = new Update();
        update.channel = channel;
        return update;
    }

    public boolean isBeta() {
        return CHANNEL_BETA.equals(channel);
    }

    public boolean hasManifest() {
        return !TextUtils.isEmpty(name)
                && !TextUtils.isEmpty(versionName)
                && code > 0
                && (!TextUtils.isEmpty(githubUrl) || oci != null && oci.isValid());
    }

    /**
     * Only a strictly newer build counts as an update.
     *
     * <p>The previous "anything different" test made an older channel manifest look installable over
     * a newer install. Because the stable and beta manifests of one release line share a
     * {@code versionCode}, a beta install was offered the older stable package and vice versa, so
     * the two channels kept overwriting each other. Those packages also disagree about the database
     * schema, so the loop destroyed the user's data and could leave a database the app refused to
     * open.
     */
    public boolean hasUpdate() {
        if (!hasManifest()) return false;
        if (code > BuildConfig.VERSION_CODE) return true;
        if (code < BuildConfig.VERSION_CODE) return false;
        return AppVersion.isNewer(name);
    }

    public String getText() {
        if (!TextUtils.isEmpty(notes)) return notes;
        if (!TextUtils.isEmpty(desc)) return desc;
        return "";
    }
}
