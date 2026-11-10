package com.fongmi.android.tv.download;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.DownloadManagerActivity;
import com.fongmi.android.tv.utils.Notify;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** 后台下载服务：前台常驻，状态栏显示进度条；点击通知进入下载管理界面。 */
public class DownloadService extends Service {
    private static final int NOTIFY_ID = 8001;  // 全服务共用一个通知 id，避免出现两条下载提示
    private static final String CHANNEL = Notify.DEFAULT;  // 复用 Notify.createChannel() 已创建的 "default" 频道

    private long lastNotifyTime;  // 进度通知节流时间戳

    public static void start(Context ctx, String name, String url, Map<String, String> headers) {
        Intent i = new Intent(ctx, DownloadService.class);
        i.putExtra("name", name);
        i.putExtra("url", url);
        i.putExtra("headers", new HashMap<>(headers != null ? headers : new HashMap<>()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    /** 仅把服务拉起来做通知宿主（用于进程重启后继续历史任务），不携带 name/url，不会新建任务。 */
    public static void attach(Context ctx) {
        Intent i = new Intent(ctx, DownloadService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Throwable ignored) {
            // 后台启动受限（Android 12+）时忽略，下载本身不依赖通知
        }
    }

    public static void cancel(Context ctx, String taskId) {
        DownloadManager.get().cancelTask(taskId);
    }

    @Override public void onCreate() {
        super.onCreate();
        Notify.createChannel();
        // 确保下载目录存在
        new File(Setting.getDownloadDir()).mkdirs();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String name = intent.getStringExtra("name");
        String url = intent.getStringExtra("url");
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) intent.getSerializableExtra("headers");

        startForeground(NOTIFY_ID, buildProgressNotification(
                getString(R.string.download_notify_title),
                name != null ? name : "", 0, -1, 0, 0).build());

        bindListener();

        // attach 模式：只做通知宿主，不创建任务（进程重启后继续历史下载时走这里）
        if (url == null || url.isEmpty()) {
            updateNotification();
            // 全是暂停态时 updateNotification 会直接 return，这里补一条"已暂停"，
            // 避免用户看到一条空白的进度通知
            if (DownloadManager.get().getActiveCount() == 0) {
                for (DownloadTask t : DownloadManager.get().getAllTasks()) {
                    if (t.status == DownloadTask.Status.PAUSED) { showPausedNotification(t); break; }
                }
            }
            return START_STICKY;
        }

        File outputDir = new File(Setting.getDownloadDir());
        outputDir.mkdirs();
        DownloadManager.get().addTask(name, url, headers, outputDir);
        return START_STICKY;
    }

    /** 绑定进度监听（每次 start 都刷新一次，保证拿到的是最新实例）。 */
    private void bindListener() {
        DownloadManager.get().setListener(new DownloadManager.Listener() {
            @Override public void onTaskStarted(DownloadTask task) {
                updateNotification();
            }
            @Override public void onTaskProgress(DownloadTask task, long bytes, long total) {
                // 分片每读 64KB 回调一次，这里节流，避免高频 notify 拖垮系统
                long now = SystemClock.elapsedRealtime();
                if (now - lastNotifyTime < 500) return;
                lastNotifyTime = now;
                updateNotification();
            }
            @Override public void onTaskCompleted(DownloadTask task, File output) {
                task.status = DownloadTask.Status.COMPLETED;
                showNotification(task, getString(R.string.download_done_notify_title), android.R.drawable.stat_sys_download_done);
                stopIfIdle();
            }
            @Override public void onTaskFailed(DownloadTask task, String msg) {
                showNotification(task, getString(R.string.download_failed_notify_title), android.R.drawable.stat_notify_error);
                stopIfIdle();
            }
            @Override public void onTaskCancelled(DownloadTask task) {
                updateNotification();
                stopIfIdle();
            }
            @Override public void onTaskPaused(DownloadTask task) {
                // 仍有其它任务在跑时刷新聚合进度；否则显示"已暂停"常驻通知
                if (DownloadManager.get().getActiveCount() > 0) updateNotification();
                else showPausedNotification(task);
            }
            @Override public void onTaskResumed(DownloadTask task) {
                updateNotification();
            }
        });
    }

    /** 仅当没有未结束的任务（运行中/已暂停/待开始）时才停服务，避免多任务并行或暂停态被误停。 */
    private void stopIfIdle() {
        if (!DownloadManager.get().hasTaskWork()) stopSelf();
    }

    /** 聚合所有进行中任务的总进度刷新同一条通知。 */
    private void updateNotification() {
        long bytes = 0;
        long total = 0;
        boolean totalKnown = true;
        int running = 0;
        int doneSeg = 0;
        int allSeg = 0;
        String firstName = null;
        for (DownloadTask t : DownloadManager.get().getAllTasks()) {
            if (t.status != DownloadTask.Status.RUNNING) continue;
            running++;
            if (firstName == null) firstName = t.name;
            bytes += t.downloadedBytes;
            if (t.totalSize > 0) total += t.totalSize;
            else totalKnown = false;
            doneSeg += t.completedSegments;
            allSeg += t.totalSegments;
        }
        if (running == 0) return;
        String display = running == 1 ? firstName : running + " 个任务";
        NotificationCompat.Builder nb = buildProgressNotification(getString(R.string.download_notify_title), display, bytes, totalKnown ? total : -1, doneSeg, allSeg);
        getSystemService(NotificationManager.class).notify(NOTIFY_ID, nb.build());
    }

    /** 暂停态常驻通知：点击仍可进入下载管理界面。 */
    private void showPausedNotification(DownloadTask task) {
        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_pause)
                .setContentTitle(getString(R.string.download_status_paused))
                .setContentText(task.name)
                .setContentIntent(contentIntent())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);
        getSystemService(NotificationManager.class).notify(NOTIFY_ID, nb.build());
    }
 
    private void showNotification(DownloadTask task, String title, int icon) {
        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(task.name)
                .setContentIntent(contentIntent())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(false);
        getSystemService(NotificationManager.class).notify(NOTIFY_ID, nb.build());
    }

    private NotificationCompat.Builder buildProgressNotification(String title, String name, long bytes, long total, int doneSeg, int allSeg) {
        int progress;
        boolean determinate;
        String text;
        if (total > 0) {
            // 直链：按字节百分比
            progress = (int) (bytes * 100 / total);
            determinate = true;
            text = name + "  " + progress + "%  " + formatSize(bytes) + " / " + formatSize(total);
        } else if (allSeg > 0) {
            // HLS：总大小未知，按分片完成数百分比
            progress = doneSeg * 100 / allSeg;
            determinate = true;
            text = name + "  " + progress + "%  " + doneSeg + "/" + allSeg + " 段  " + formatSize(bytes);
        } else {
            progress = 0;
            determinate = false;
            text = name + "  " + formatSize(bytes);
        }
        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent())
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if (determinate) nb.setProgress(100, progress, false);
        else nb.setProgress(0, 0, true);
        return nb;
    }

    private PendingIntent contentIntent() {
        Intent intent = new Intent(this, DownloadManagerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int i = 0;
        double v = bytes;
        while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
        return String.format("%.1f %s", v, units[i]);
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        DownloadManager.get().setListener(null);
        super.onDestroy();
    }
}
