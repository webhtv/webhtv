package com.fongmi.android.tv.playback.origin;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.webkit.WebView;

import com.fongmi.android.tv.App;
import com.github.catvod.Init;
import com.github.catvod.utils.Path;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public final class OriginSessionBootstrap {
    private static volatile boolean worker;
    private static volatile OriginStorageContext storage;

    private OriginSessionBootstrap() { }

    public static boolean identify(Context context) {
        String name = "";
        if (Build.VERSION.SDK_INT >= 28) name = Application.getProcessName();
        else try (FileInputStream input = new FileInputStream("/proc/self/cmdline")) {
            byte[] buffer = new byte[256];
            int count = input.read(buffer);
            if (count > 0) name = new String(buffer, 0, count, StandardCharsets.UTF_8).split("\u0000", 2)[0];
        } catch (Exception ignored) { }
        worker = name.equals(context.getPackageName() + ":origin") || name.equals(context.getPackageName() + ":origin_node");
        return worker;
    }

    public static boolean isWorker() { return worker; }
    public static OriginStorageContext storage() { return storage; }

    public static synchronized OriginStorageContext install(Context base, String leaseId) {
        if (!worker || storage != null) throw new IllegalStateException(OriginSessionContract.ISOLATION_ERROR);
        if (Build.VERSION.SDK_INT < 28) throw new IllegalStateException("ISOLATION_ERROR: WebView requires API 28 private data directory");
        OriginStorageContext context = new OriginStorageContext(base, leaseId);
        String suffix = Application.getProcessName().endsWith(":origin_node") ? "origin_node_" : "origin_";
        WebView.setDataDirectorySuffix(suffix + leaseId.replace("-", ""));
        storage = context;
        App.get().installOriginContext(context);
        Init.set(context);
        Path.setRootOverride(context.root());
        return context;
    }
}
