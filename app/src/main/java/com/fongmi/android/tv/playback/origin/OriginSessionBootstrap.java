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
    /**
     * 独立的 WebView 数据目录从 API 28 才有。缺它时两个进程会争用同一份 WebView 数据，
     * 所以只有真正要创建 WebView 的解析必须被拒绝；纯直链原源在低版本仍可用。
     */
    private static volatile boolean webViewDirectory;

    private OriginSessionBootstrap() { }

    /** 当前进程名：API 28 起用官方 API，更早版本读 /proc/self/cmdline（minSdk 24 也要能识别 worker）。 */
    public static String processName() {
        if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName();
        try (FileInputStream input = new FileInputStream("/proc/self/cmdline")) {
            byte[] buffer = new byte[256];
            int count = input.read(buffer);
            if (count > 0) return new String(buffer, 0, count, StandardCharsets.UTF_8).split("\u0000", 2)[0];
        } catch (Exception ignored) { }
        return "";
    }

    public static boolean identify(Context context) {
        String name = processName();
        worker = name.equals(context.getPackageName() + ":origin") || name.equals(context.getPackageName() + ":origin_node");
        return worker;
    }

    public static boolean isWorker() { return worker; }
    public static OriginStorageContext storage() { return storage; }
    /** worker 是否拥有可独立设置的 WebView 数据目录（API 28 起才有）。 */
    public static boolean canIsolateWebView() { return webViewDirectory; }

    public static synchronized OriginStorageContext install(Context base, String leaseId) {
        if (!worker || storage != null) throw new IllegalStateException(OriginSessionContract.ISOLATION_ERROR);
        OriginStorageContext context = new OriginStorageContext(base, leaseId);
        if (Build.VERSION.SDK_INT >= 28) {
            String suffix = processName().endsWith(":origin_node") ? "origin_node_" : "origin_";
            WebView.setDataDirectorySuffix(suffix + leaseId.replace("-", ""));
            webViewDirectory = true;
        } else {
            // 低版本没有 setDataDirectorySuffix：私有文件/偏好/数据库照常隔离，
            // 但任何要创建 WebView 的解析都在创建前被拒绝（见 OriginSessionService）。
            webViewDirectory = false;
        }
        storage = context;
        App.get().installOriginContext(context);
        Init.set(context);
        Path.setRootOverride(context.root());
        return context;
    }
}
