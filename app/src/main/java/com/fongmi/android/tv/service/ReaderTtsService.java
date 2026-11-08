package com.fongmi.android.tv.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.tts.ReaderTtsController;
import com.fongmi.android.tv.tts.TtsEngineConfig;
import com.fongmi.android.tv.tts.TtsOptions;
import com.fongmi.android.tv.tts.TtsVoice;
import com.github.catvod.crawler.SpiderDebug;

import java.util.List;

/**
 * 小说朗读前台服务。
 *
 * 对齐 legado {@code BaseReadAloudService} 的能力：朗读在后台/锁屏后继续、通知栏提供
 * 上一段/播放暂停/下一段/停止/定时、遵守音频焦点、拔耳机（ACTION_AUDIO_BECOMING_NOISY）自动暂停。
 *
 * 与服务外的交互采用本仓库既有模式（{@link PlaybackService} 的静态访问 + {@code App.get()}），
 * 不额外引入 Binder/绑定抽象：阅读页启动服务后通过 {@link #get()} 拿到实例下发朗读任务，
 * 服务再通过 {@link WebCallback} 把状态回传给阅读页 WebView。
 */
public class ReaderTtsService extends Service implements ReaderTtsController.Listener {

    private static final String TAG = "TV-tts";
    private static final String CHANNEL_ID = "reader_tts";
    private static final int NOTIFY_ID = 0x7EAD;

    public static final String ACTION_START = "com.fongmi.android.tv.tts.START";
    public static final String ACTION_PAUSE = "com.fongmi.android.tv.tts.PAUSE";
    public static final String ACTION_RESUME = "com.fongmi.android.tv.tts.RESUME";
    public static final String ACTION_TOGGLE = "com.fongmi.android.tv.tts.TOGGLE";
    public static final String ACTION_PREV = "com.fongmi.android.tv.tts.PREV";
    public static final String ACTION_NEXT = "com.fongmi.android.tv.tts.NEXT";
    public static final String ACTION_STOP = "com.fongmi.android.tv.tts.STOP";
    public static final String ACTION_TIMER = "com.fongmi.android.tv.tts.TIMER";
    public static final String ACTION_TIMER_OFF = "com.fongmi.android.tv.tts.TIMER_OFF";

    /** 阅读页（WebView）回调：由 WebReaderActivity 注册，阅读页销毁时必须清空。 */
    public interface WebCallback {
        void onTtsState(String state, int paragraph, int total, String message, String title);

        void onTtsParagraph(int paragraph, int total, String text);

        void onTtsChapterEnd();

        void onTtsVoices(List<TtsVoice> voices);
    }

    /**
     * 阅读页下发的朗读任务。
     *
     * 朗读正文可能很长（整章段落），不走 Intent extra（Binder 事务上限），
     * 而是静态传参：阅读页先赋值再 startForegroundService，服务在 onStartCommand
     * 的 ACTION_START 分支取走。同进程同主线程，顺序确定。
     */
    public static final class StartRequest {
        public final String source;
        public final TtsOptions options;
        public final TtsEngineConfig rule;
        public final List<String> paragraphs;
        public final int index;
        public final int timerMinutes;
        public final String title;

        public StartRequest(String source, TtsOptions options, TtsEngineConfig rule,
                            List<String> paragraphs, int index, int timerMinutes, String title) {
            this.source = source;
            this.options = options;
            this.rule = rule;
            this.paragraphs = paragraphs;
            this.index = index;
            this.timerMinutes = timerMinutes;
            this.title = title;
        }
    }

    private static volatile StartRequest pendingStart;

    /** 阅读页在 startForegroundService 之前调用。 */
    public static void setPendingStart(StartRequest request) {
        pendingStart = request;
    }

    private static volatile WebCallback pendingWebCallback;

    /**
     * 阅读页在服务尚未创建时先登记回调（服务 onCreate 后接管）。
     * 阅读器首次点朗读时服务还不存在，而状态回调（如系统音色列表）在服务创建后就会发出，
     * 没有这个登记就会丢掉第一次回调。
     */
    public static void setPendingWebCallback(WebCallback callback) {
        pendingWebCallback = callback;
    }

    /**
     * 章末等待阅读页续读的时间：读完一章后阅读页会立刻用新章的段落重新 start；
     * 若它决定不续读（最后一章/用户已退出），服务在此时限后自行结束，避免常驻。
     */
    private static final long IDLE_STOP_DELAY_MS = 6000L;

    private static volatile ReaderTtsService instance;

    private ReaderTtsController controller;
    private WebCallback webCallback;
    private PowerManager.WakeLock wakeLock;
    private Object focusRequest;
    private boolean focusHeld;
    private boolean foreground;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable idleStop = this::stopSelf;
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                onAudioBecomingNoisy();
            }
        }
    };

    @Nullable
    public static ReaderTtsService get() {
        return instance;
    }

    public static boolean isRunning() {
        ReaderTtsService service = instance;
        return service != null && service.controller != null && service.controller.isActive();
    }

    /** 停止朗读并结束服务（阅读页退出、或用户点停止时调用）。 */
    public static void stopAndQuit() {
        ReaderTtsService service = instance;
        if (service != null) service.shutdown();
        try {
            App.get().stopService(new Intent(App.get(), ReaderTtsService.class));
        } catch (Throwable ignore) {
        }
    }

    /** 通知栏动作：不依赖阅读页是否在前台。 */
    public static void dispatch(Context context, String action, int timerMinutes) {
        Intent intent = new Intent(context, ReaderTtsService.class).setAction(action);
        if (timerMinutes > 0) intent.putExtra("minutes", timerMinutes);
        try {
            context.startService(intent);
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "dispatch failed action=%s %s", action, e.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        controller = new ReaderTtsController(this);
        controller.setListener(this);
        WebCallback pending = pendingWebCallback;
        if (pending != null) {
            webCallback = pending;
            pendingWebCallback = null;
        }
        createChannel();
        registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            // 服务被系统重建：没有朗读任务就退出，避免留下不可控的后台常驻
            if (controller == null || !controller.isActive()) stopSelf();
            return START_NOT_STICKY;
        }
        switch (action) {
            case ACTION_PAUSE:
                controller.pause();
                break;
            case ACTION_RESUME:
                controller.resume();
                break;
            case ACTION_TOGGLE:
                controller.toggle();
                break;
            case ACTION_PREV:
                controller.previous();
                break;
            case ACTION_NEXT:
                controller.next();
                break;
            case ACTION_TIMER:
                controller.addTimer(intent.getIntExtra("minutes", 30));
                break;
            case ACTION_TIMER_OFF:
                controller.setTimer(0);
                break;
            case ACTION_STOP:
                controller.stop(null);
                stopSelf();
                break;
            case ACTION_START:
            default: {
                StartRequest request = pendingStart;
                pendingStart = null;
                if (request != null) startReadAloudNow(request);
                break;
            }
        }
        if (controller.isActive() || ReaderTtsController.STATE_ERROR.equals(controller.state())) {
            startForegroundCompat();
        }
        return START_NOT_STICKY;
    }

    /**
     * 音频输出路由丢失（拔耳机/蓝牙断开）→ 自动暂停。
     *
     * 保持 public 是为了让真机测试能驱动广播接收器里的同一条路径：
     * {@code ACTION_AUDIO_BECOMING_NOISY} 是系统保护广播，应用和 shell 都发不出去。
     */
    public void onAudioBecomingNoisy() {
        SpiderDebug.log(TAG, "audio becoming noisy, pause read aloud");
        if (controller != null) controller.pause();
    }

    /* ---------------- 阅读页（WebView）调用入口 ---------------- */

    public void setWebCallback(WebCallback callback) {
        this.webCallback = callback;
    }

    public void clearWebCallback(WebCallback callback) {
        if (webCallback == callback) webCallback = null;
        if (pendingWebCallback == callback) pendingWebCallback = null;
    }

    public void startReadAloud(String source, TtsOptions options, TtsEngineConfig rule,
                               List<String> paragraphs, int startIndex, int timerMinutes, String title) {
        startReadAloudNow(new StartRequest(source, options, rule, paragraphs, startIndex, timerMinutes, title));
    }

    private void startReadAloudNow(StartRequest request) {
        handler.removeCallbacks(idleStop);
        startForegroundCompat();
        controller.start(request.source, request.options, request.rule, request.paragraphs,
                request.index, request.timerMinutes, request.title);
    }

    public void pauseReadAloud() {
        controller.pause();
    }

    public void resumeReadAloud() {
        controller.resume();
    }

    public void toggleReadAloud() {
        controller.toggle();
    }

    public void prevParagraph() {
        controller.previous();
    }

    public void nextParagraph() {
        controller.next();
    }

    public void seekParagraph(int paragraph) {
        controller.seekParagraph(paragraph);
    }

    public void setRate(float rate) {
        controller.setRate(rate);
    }

    public void setPitch(float pitch) {
        controller.setPitch(pitch);
    }

    public void setVoice(String voice) {
        controller.setVoice(voice);
    }

    public void setTimer(int minutes) {
        controller.setTimer(minutes);
    }

    public String state() {
        return controller.state();
    }

    public int currentParagraph() {
        return controller.currentParagraph();
    }

    public int paragraphCount() {
        return controller.paragraphCount();
    }

    public int timerMinutes() {
        return controller.timerMinutes();
    }

    public String sourceId() {
        return controller.sourceId();
    }

    /** 停播并结束服务（阅读页退出时调用）。 */
    public void shutdown() {
        handler.removeCallbacks(idleStop);
        if (controller != null) controller.stop(null);
        stopForegroundCompat();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        instance = null;
        handler.removeCallbacks(idleStop);
        try {
            unregisterReceiver(noisyReceiver);
        } catch (Throwable ignore) {
        }
        if (controller != null) {
            controller.release();
            controller = null;
        }
        abandonFocus();
        releaseWakeLock();
        // 兜住退出路径上的残留通知：任何原因导致服务销毁时都清掉朗读通知条
        try {
            android.app.NotificationManager manager = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.cancel(NOTIFY_ID);
        } catch (Throwable ignore) {
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /* ---------------- ReaderTtsController.Listener ---------------- */

    @Override
    public void onTtsState(String state, int paragraph, int total, String message, String title) {
        boolean stopped = ReaderTtsController.STATE_STOPPED.equals(state);
        if (stopped) {
            // 章节读完（或用户停播）：先给阅读页留出「续读下一章」的窗口，再自行结束
            abandonFocus();
            stopForegroundCompat();
            handler.removeCallbacks(idleStop);
            handler.postDelayed(idleStop, IDLE_STOP_DELAY_MS);
        } else if (ReaderTtsController.STATE_ERROR.equals(state)) {
            abandonFocus();
            handler.removeCallbacks(idleStop);
            startForegroundCompat();
            handler.postDelayed(idleStop, IDLE_STOP_DELAY_MS);
        } else if (ReaderTtsController.STATE_PAUSED.equals(state)) {
            // 暂停即让出音频焦点，否则会挡住用户暂停朗读后去放别的音频
            abandonFocus();
            handler.removeCallbacks(idleStop);
            startForegroundCompat();
        } else {
            handler.removeCallbacks(idleStop);
            startForegroundCompat();
            acquireWakeLock();
        }
        // 停播时通知已被 stopForegroundCompat 撤掉，不能再 notify：
        // 否则通知栏会残留一条标题写着「正在朗读」的失效控制条，
        // 而且服务随后退出也不会把它清掉（用户只能手动划掉）。
        if (!stopped) updateNotification(state, paragraph, total, message, title);
        WebCallback callback = webCallback;
        if (callback != null) callback.onTtsState(state, paragraph, total, message, title);
    }

    @Override
    public void onTtsParagraph(int paragraph, int total, String text) {
        WebCallback callback = webCallback;
        if (callback != null) callback.onTtsParagraph(paragraph, total, text);
    }

    @Override
    public void onTtsChapterEnd() {
        WebCallback callback = webCallback;
        if (callback != null) callback.onTtsChapterEnd();
    }

    @Override
    public void onTtsVoices(List<TtsVoice> voices) {
        WebCallback callback = webCallback;
        if (callback != null) callback.onTtsVoices(voices);
    }

    /* ---------------- 前台服务 / 通知 ---------------- */

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.reader_tts_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }

    private void startForegroundCompat() {
        if (foreground) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(NOTIFY_ID, buildNotification(controller == null ? null : controller.state(),
                        controller == null ? -1 : controller.currentParagraph(),
                        controller == null ? 0 : controller.paragraphCount(), null, ""));
            } else {
                Notification notification = buildNotification(controller == null ? null : controller.state(),
                        controller == null ? -1 : controller.currentParagraph(),
                        controller == null ? 0 : controller.paragraphCount(), null, "");
                android.app.NotificationManager manager = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager != null) manager.notify(NOTIFY_ID, notification);
            }
            foreground = true;
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "startForeground failed %s", e.getMessage());
        }
    }

    private void stopForegroundCompat() {
        if (!foreground) return;
        foreground = false;
        try {
            stopForeground(true);
        } catch (Throwable ignore) {
        }
        try {
            android.app.NotificationManager manager = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.cancel(NOTIFY_ID);
        } catch (Throwable ignore) {
        }
        releaseWakeLock();
    }

    private void updateNotification(String state, int paragraph, int total, String message, String title) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        try {
            manager.notify(NOTIFY_ID, buildNotification(state, paragraph, total, message, title));
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "notify failed %s", e.getMessage());
        }
    }

    private Notification buildNotification(String state, int paragraph, int total, String message, String title) {
        boolean playing = ReaderTtsController.STATE_PLAYING.equals(state);
        String heading;
        if (ReaderTtsController.STATE_PAUSED.equals(state)) heading = getString(R.string.reader_tts_paused);
        else if (ReaderTtsController.STATE_ERROR.equals(state)) heading = getString(R.string.reader_tts_error);
        else heading = getString(R.string.reader_tts_playing);
        if (controller != null && controller.timerMinutes() > 0) {
            heading += " · " + getString(R.string.reader_tts_timer, controller.timerMinutes());
        }
        String track = TextUtils.isEmpty(title) ? getString(R.string.reader_tts_default_title) : title;
        String detail = TextUtils.isEmpty(message)
                ? (paragraph >= 0 ? getString(R.string.reader_tts_progress, paragraph + 1, Math.max(total, paragraph + 1)) : track)
                : message;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle(heading + " · " + track)
                .setContentText(detail)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVibrate(null)
                .setSound(null)
                .addAction(0, getString(R.string.reader_tts_prev), servicePending(ACTION_PREV, 1))
                .addAction(0, playing ? getString(R.string.reader_tts_pause) : getString(R.string.reader_tts_resume),
                        servicePending(playing ? ACTION_PAUSE : ACTION_RESUME, 2))
                .addAction(0, getString(R.string.reader_tts_next), servicePending(ACTION_NEXT, 3))
                .addAction(0, getString(R.string.reader_tts_timer_action), servicePending(ACTION_TIMER, 4))
                .addAction(0, getString(R.string.reader_tts_stop), servicePending(ACTION_STOP, 5));
        return builder.build();
    }

    private PendingIntent servicePending(String action, int requestCode) {
        Intent intent = new Intent(this, ReaderTtsService.class).setAction(action);
        if (ACTION_TIMER.equals(action)) intent.putExtra("minutes", 30);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, requestCode, intent, flags);
    }

    /* ---------------- 音频焦点 / 电源 ---------------- */

    /** 请求音频焦点；被其他应用抢占时暂停朗读（对齐 legado 的焦点策略）。 */
    private boolean requestFocus() {
        AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attributes)
                        .setWillPauseWhenDucked(true)
                        .setOnAudioFocusChangeListener(this::onAudioFocusChange)
                        .build();
            }
            return manager.requestAudioFocus((AudioFocusRequest) focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        return manager.requestAudioFocus(this::onAudioFocusChange, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonFocus() {
        if (!focusHeld) return;
        focusHeld = false;
        AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest instanceof AudioFocusRequest) {
            manager.abandonAudioFocusRequest((AudioFocusRequest) focusRequest);
        } else {
            manager.abandonAudioFocus(null);
        }
        focusRequest = null;
    }

    private void onAudioFocusChange(int change) {
        if (controller == null || !controller.isActive()) return;
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                controller.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                controller.pause();
                break;
            default:
                break;
        }
    }

    private void acquireWakeLock() {
        // 焦点只请求一次：每次状态回调都去 requestAudioFocus 会刷爆系统焦点栈，
        // 也让「暂停后让出焦点」永远无法生效。
        if (!focusHeld) {
            focusHeld = requestFocus();
            if (!focusHeld) {
                SpiderDebug.log(TAG, "audio focus denied, pause");
                if (controller != null && controller.isPlaying()) controller.pause();
                return;
            }
        }
        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) return;
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebHTV:ReaderTts");
            wakeLock.setReferenceCounted(false);
        }
        if (!wakeLock.isHeld()) wakeLock.acquire(4 * 60 * 60 * 1000L);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Throwable ignore) {
            }
        }
    }
}
