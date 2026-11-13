package com.fongmi.android.tv.remote;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.AppBranding;
import com.fongmi.android.tv.utils.Notify;

public class RemoteAgentService extends Service {

    private static final String ACTION_START = BuildConfig.APPLICATION_ID + ".remote.START";
    private static final String ACTION_STOP = BuildConfig.APPLICATION_ID + ".remote.STOP";
    private static final int NOTIFICATION_ID = Notify.ID + 5;
    private static volatile boolean running;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    public static void start(Context context) {
        if (running || !RemoteStore.hasEnabledProfile()) return;
        Intent intent = new Intent(context, RemoteAgentService.class).setAction(ACTION_START);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, RemoteAgentService.class));
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!RemoteStore.hasEnabledProfile()) {
            stopSelf();
            return;
        }
        running = true;
        startForegroundCompat(notification());
        acquireLocks();
        RemoteLog.log("agent service started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!RemoteStore.hasEnabledProfile()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        RemoteAgent.get().startFromService();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseLocks();
        stopForegroundCompat();
        running = false;
        RemoteLog.log("agent service stopped");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC | ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
    }

    private Notification notification() {
        return new NotificationCompat.Builder(this, Notify.DEFAULT)
                .setSmallIcon(AppBranding.notificationIcon())
                .setContentTitle(getString(R.string.remote_trust_notification_title))
                .setContentText(RemoteStore.summary(this))
                .setOngoing(true)
                .setSilent(true)
                .addAction(0, getString(R.string.manage_page_stop), stopIntent())
                .build();
    }

    private PendingIntent stopIntent() {
        Intent intent = new Intent(this, RemoteAgentService.class).setAction(ACTION_STOP);
        return PendingIntent.getService(this, 2, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void acquireLocks() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, BuildConfig.APPLICATION_ID + ":remote");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Throwable e) {
            RemoteLog.log("wake lock failed error=%s", e.getMessage());
        }
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, BuildConfig.APPLICATION_ID + ":remote");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            }
        } catch (Throwable e) {
            RemoteLog.log("wifi lock failed error=%s", e.getMessage());
        }
    }

    private void releaseLocks() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {
        }
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Throwable ignored) {
        }
        wakeLock = null;
        wifiLock = null;
    }
}
