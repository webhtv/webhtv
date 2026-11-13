package com.fongmi.android.tv.service;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.AppBranding;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Util;

import java.util.Locale;

public class ManageService extends Service {

    public static final String PATH = "/m";

    private static final String ACTION_START = BuildConfig.APPLICATION_ID + ".manage.START";
    private static final String ACTION_STOP = BuildConfig.APPLICATION_ID + ".manage.STOP";
    private static final String ACTION_REFRESH = BuildConfig.APPLICATION_ID + ".manage.REFRESH";
    private static final long IDLE_TIMEOUT = 600_000L;
    private static final long CHECK_INTERVAL = 15_000L;
    private static volatile ManageService instance;
    private static volatile boolean running;
    private static volatile boolean backgroundPowerGuided;
    private static volatile long lastAccess;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    private final Runnable checker = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (System.currentTimeMillis() - lastAccess > IDLE_TIMEOUT) {
                SpiderDebug.log("server", "manage page idle timeout");
                stopSelf();
                return;
            }
            Server.get().startManage();
            App.post(this, CHECK_INTERVAL);
        }
    };

    public static void start(Context context) {
        Server.get().startManage();
        touch();
        Intent intent = new Intent(context, ManageService.class).setAction(ACTION_START);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, ManageService.class));
    }

    /** APP 图标切换后重建常驻通知，否则旧通知会一直挂着旧的品牌图标。 */
    public static void refreshNotification(Context context) {
        if (!running) return;
        ContextCompat.startForegroundService(context, new Intent(context, ManageService.class).setAction(ACTION_REFRESH));
    }

    public static boolean isRunning() {
        return running;
    }

    public static void touch() {
        if (running && !Server.get().isRunning()) Server.get().startManage();
        if (!running && !Server.get().isRunning()) return;
        lastAccess = System.currentTimeMillis();
    }

    public static void closeSoon() {
        SpiderDebug.log("server", "manage page close signal received");
    }

    public static String getLocalUrl() {
        return Server.get().getAddress(true) + PATH;
    }

    public static String getLanUrl() {
        return Server.get().getAddress(false) + PATH;
    }

    public static long getLastAccess() {
        return lastAccess;
    }

    public static long getIdleTimeout() {
        return IDLE_TIMEOUT;
    }

    public static boolean isWakeLockHeld() {
        try {
            ManageService service = instance;
            return service != null && service.wakeLock != null && service.wakeLock.isHeld();
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isWifiLockHeld() {
        try {
            ManageService service = instance;
            return service != null && service.wifiLock != null && service.wifiLock.isHeld();
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return powerManager == null || powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Throwable e) {
            SpiderDebug.log("server", e);
            return false;
        }
    }

    public static boolean shouldRequestBatteryOptimizations(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations(context);
    }

    public static boolean shouldOpenBackgroundPowerSettings(Context context) {
        if (backgroundPowerGuided && isStrictBackgroundBrand()) return false;
        return shouldRequestBatteryOptimizations(context) || isStrictBackgroundBrand();
    }

    public static boolean isBackgroundPowerGuided() {
        return backgroundPowerGuided;
    }

    public static boolean requestIgnoreBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.getPackageName()));
        return startActivity(context, intent);
    }

    public static boolean openBackgroundPowerSettings(Context context) {
        if (openVendorBackgroundSettings(context)) {
            confirmBackgroundPowerHandled();
            return true;
        }
        if (!isVivoLike() && requestIgnoreBatteryOptimizations(context)) {
            confirmBackgroundPowerHandled();
            return true;
        }
        if (openBatterySettings(context) || openAppDetailsSettings(context) || openSystemSettings(context)) {
            confirmBackgroundPowerHandled();
            return true;
        }
        return false;
    }

    public static String getBackgroundPowerGuide(Context context) {
        if (isVivoLike()) return context.getString(R.string.manage_page_battery_guide_vivo);
        if (isXiaomiLike()) return context.getString(R.string.manage_page_battery_guide_xiaomi);
        if (isOppoLike()) return context.getString(R.string.manage_page_battery_guide_oppo);
        if (isHuaweiLike()) return context.getString(R.string.manage_page_battery_guide_huawei);
        if (isSamsungLike()) return context.getString(R.string.manage_page_battery_guide_samsung);
        if (isMeizuLike()) return context.getString(R.string.manage_page_battery_guide_meizu);
        return context.getString(R.string.manage_page_battery_guide_default);
    }

    public static void confirmBackgroundPowerHandled() {
        backgroundPowerGuided = true;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        running = true;
        Server.get().startManage();
        touch();
        startForegroundCompat(notification());
        acquireLocks();
        App.removeCallbacks(checker);
        App.post(checker, CHECK_INTERVAL);
        SpiderDebug.log("server", "manage service started url=%s lan=%s ip=%s batteryOptimized=%s wake=%s wifi=%s", getLocalUrl(), getLanUrl(), Util.getIp(), !isIgnoringBatteryOptimizations(this), isWakeLockHeld(), isWifiLockHeld());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            touch();
            startForegroundCompat(notification());
            return START_STICKY;
        }
        touch();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        backgroundPowerGuided = false;
        App.removeCallbacks(checker);
        releaseLocks();
        Server.get().stopManage();
        instance = null;
        SpiderDebug.log("server", "manage service stopped");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notify.ID + 2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC | ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(Notify.ID + 2, notification);
        }
    }

    private void acquireLocks() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, BuildConfig.APPLICATION_ID + ":manage");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Throwable e) {
            SpiderDebug.log("server", e);
        }
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, BuildConfig.APPLICATION_ID + ":manage");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            }
        } catch (Throwable e) {
            SpiderDebug.log("server", e);
        }
    }

    private static boolean openVendorBackgroundSettings(Context context) {
        if (isVivoLike()) return openVivoPowerSettings(context);
        if (isXiaomiLike()) return openKnownActivity(context,
                new Intent().setComponent(new ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")).putExtra("package_name", context.getPackageName()).putExtra("package_label", context.getString(R.string.app_name)),
                new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")));
        if (isOnePlusLike()) return openKnownActivity(context,
                new Intent().setComponent(new ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
                new Intent("com.android.settings.action.BACKGROUND_OPTIMIZE"));
        if (isOppoLike()) return openKnownActivity(context,
                new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
                new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
                new Intent().setComponent(new ComponentName("com.heytap.safecenter", "com.heytap.safecenter.startup.StartupAppListActivity")));
        if (isHuaweiLike()) return openKnownActivity(context,
                new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")));
        if (isSamsungLike()) return openKnownActivity(context,
                new Intent().setComponent(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
                new Intent().setComponent(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity")),
                new Intent().setComponent(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")));
        if (isMeizuLike()) return openKnownActivity(context,
                new Intent().setComponent(new ComponentName("com.meizu.safe", "com.meizu.safe.powerui.PowerAppPermissionActivity")),
                new Intent().setComponent(new ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity")));
        return false;
    }

    private static boolean openVivoPowerSettings(Context context) {
        String packageName = context.getPackageName();
        return openKnownActivity(context,
                new Intent("com.vivo.abe.permission.action.openhpactivity").setPackage("com.vivo.abe").putExtra("packageName", packageName).putExtra("pkgName", packageName).putExtra("packagename", packageName),
                new Intent().setComponent(new ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity")).putExtra("packageName", packageName).putExtra("pkgName", packageName).putExtra("packagename", packageName),
                new Intent("com.vivo.abe.action.POWER_CONSUMPTION_MANAGER").setPackage("com.vivo.abe").putExtra("packageName", packageName).putExtra("pkgName", packageName),
                new Intent("com.vivo.abe.action.POWER_MANAGER").setPackage("com.vivo.abe").putExtra("packageName", packageName).putExtra("pkgName", packageName));
    }

    private static boolean isVivoLike() {
        String text = brandText();
        return text.contains("vivo") || text.contains("iqoo");
    }

    private static boolean isXiaomiLike() {
        String text = brandText();
        return text.contains("xiaomi") || text.contains("redmi") || text.contains("poco");
    }

    private static boolean isOppoLike() {
        String text = brandText();
        return text.contains("oppo") || text.contains("realme");
    }

    private static boolean isHuaweiLike() {
        String text = brandText();
        return text.contains("huawei") || text.contains("honor");
    }

    private static boolean isSamsungLike() {
        return brandText().contains("samsung");
    }

    private static boolean isOnePlusLike() {
        return brandText().contains("oneplus");
    }

    private static boolean isMeizuLike() {
        return brandText().contains("meizu");
    }

    private static boolean isStrictBackgroundBrand() {
        return isVivoLike() || isXiaomiLike() || isOppoLike() || isHuaweiLike() || isSamsungLike() || isOnePlusLike() || isMeizuLike();
    }

    private static String brandText() {
        return (Build.MANUFACTURER + " " + Build.BRAND + " " + Build.PRODUCT).toLowerCase(Locale.ROOT);
    }

    private static boolean openKnownActivity(Context context, Intent... intents) {
        for (Intent intent : intents) {
            if (startActivity(context, intent)) return true;
        }
        return false;
    }

    private static boolean openBatterySettings(Context context) {
        return openKnownActivity(context,
                new Intent("android.settings.BATTERY_SETTINGS"),
                new Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
                new Intent("android.settings.POWER_USAGE_SUMMARY"),
                new Intent("android.settings.BATTERY_SAVER_SETTINGS"));
    }

    private static boolean openAppDetailsSettings(Context context) {
        return startActivity(context, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName())));
    }

    private static boolean openSystemSettings(Context context) {
        return startActivity(context, new Intent(Settings.ACTION_SETTINGS));
    }

    private static boolean startActivity(Context context, Intent intent) {
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        } catch (Throwable e) {
            SpiderDebug.log("server", e);
            return false;
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

    private Notification notification() {
        return new NotificationCompat.Builder(this, Notify.DEFAULT)
                .setSmallIcon(AppBranding.notificationIcon())
                .setContentTitle(getString(R.string.manage_page_notification_title))
                .setContentText(getLanUrl())
                .setContentIntent(openIntent())
                .setOngoing(true)
                .setSilent(true)
                .addAction(0, getString(R.string.manage_page_stop), stopIntent())
                .build();
    }

    private PendingIntent openIntent() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getLanUrl())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent stopIntent() {
        Intent intent = new Intent(this, ManageService.class).setAction(ACTION_STOP);
        return PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
