package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.utils.AppVersion;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Reproduces the update loop seen on an Android 9 box: the installed beta build and the stable
 * manifest carried the same {@code versionCode} and the stable one was older, so the app offered a
 * downgrade, installed it, read the beta manifest again, and repeated forever. Both packages also
 * disagreed about the database schema, so each pass destroyed the user's data.
 */
public class UpdateOrderingTest {

    private static final String OLDER_STABLE = "v5.6.0-202609221328";
    private static final String NEWER_BETA = "v5.6.0-beta-202610091755";

    @Test
    public void olderManifestWithTheSameVersionCodeIsNotAnUpdate() {
        Update update = manifest("stable", OLDER_STABLE, BuildConfig.VERSION_CODE);
        assertTrue(update.hasManifest());
        assertFalse("An older package of the same version code must not be offered as an update",
                update.hasUpdate());
    }

    @Test
    public void identicalManifestIsNotAnUpdate() {
        Update update = manifest("stable", AppVersion.fullName(), BuildConfig.VERSION_CODE);
        assertFalse(update.hasUpdate());
    }

    @Test
    public void higherVersionCodeIsAlwaysAnUpdate() {
        Update update = manifest("stable", "v0.0.1-202001010000", BuildConfig.VERSION_CODE + 1);
        assertTrue(update.hasUpdate());
    }

    @Test
    public void lowerVersionCodeIsNeverAnUpdate() {
        Update update = manifest("stable", "v9.9.9-209901010000", BuildConfig.VERSION_CODE - 1);
        assertFalse(update.hasUpdate());
    }

    @Test
    public void manifestWithoutDownloadTargetIsNotAnUpdate() {
        Update update = manifest("stable", "v9.9.9-209901010000", BuildConfig.VERSION_CODE + 1);
        update.githubUrl = "";
        update.oci = null;
        assertFalse(update.hasUpdate());
    }

    private static Update manifest(String channel, String name, int code) {
        Update update = new Update();
        update.channel = channel;
        update.name = name;
        update.versionName = "5.6.0";
        update.code = code;
        update.githubUrl = "https://example.invalid/" + name + ".apk";
        return update;
    }
}
