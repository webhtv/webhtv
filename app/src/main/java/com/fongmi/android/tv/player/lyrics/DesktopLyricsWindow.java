package com.fongmi.android.tv.player.lyrics;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.custom.LyricsOverlayView;
import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DesktopLyricsWindow {

    private static final long UPDATE_INTERVAL_MS = 500L;

    private final WindowManager windowManager;
    private final Context context;
    private final Runnable tick = this::onTick;

    private LyricsController controller;
    private LyricsResult lyricsResult;
    private List<LyricsLine> lyricsLines = Collections.emptyList();
    private LyricsOverlayView view;
    private WindowManager.LayoutParams params;
    private PlayerManager player;
    private float downRawX;
    private float downRawY;
    private int downX;
    private int downY;
    private int touchSlop;
    private boolean attached;
    private boolean audioContent;
    private boolean dragging;
    private boolean foreground = true;
    private String displayState;
    private int lyricsVersion;
    private int appliedLyricsVersion = -1;

    public DesktopLyricsWindow(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void setForeground(boolean foreground) {
        this.foreground = foreground;
        update(player, true);
    }

    public void setAudioContent(boolean audioContent) {
        if (this.audioContent == audioContent) return;
        this.audioContent = audioContent;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("desktop-lyrics", "content eligible=%s", audioContent);
        update(player, true);
    }

    public void setLyricsSnapshot(LyricsResult result, List<LyricsLine> lines) {
        lyricsResult = result;
        lyricsLines = result == null || lines == null || lines.isEmpty() ? Collections.emptyList() : new ArrayList<>(lines);
        lyricsVersion++;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("desktop-lyrics", "snapshot source=%s lines=%d version=%d", result == null ? "none" : result.getSource(), lyricsLines.size(), lyricsVersion);
        update(player, true);
    }

    public void refresh(PlayerManager player) {
        update(player, true);
    }

    public void update(PlayerManager player) {
        update(player, false);
    }

    public void release() {
        hide();
        if (controller != null) controller.release();
        controller = null;
        view = null;
        player = null;
        audioContent = false;
        displayState = null;
        lyricsResult = null;
        lyricsLines = Collections.emptyList();
        lyricsVersion = 0;
        appliedLyricsVersion = -1;
    }

    private void update(PlayerManager player, boolean refresh) {
        this.player = player;
        if (!canShow(player)) {
            hide();
            return;
        }
        ensureAttached();
        if (!attached || view == null) return;
        boolean firstAttach = controller == null;
        if (firstAttach) controller = new LyricsController(view);
        if (lyricsResult != null && !lyricsLines.isEmpty()) {
            if (firstAttach || appliedLyricsVersion != lyricsVersion) {
                controller.applySnapshot(lyricsResult, lyricsLines, player);
                appliedLyricsVersion = lyricsVersion;
                if (SpiderDebug.isEnabled()) SpiderDebug.log("desktop-lyrics", "snapshot applied source=%s lines=%d version=%d", lyricsResult.getSource(), lyricsLines.size(), lyricsVersion);
            }
        } else if (refresh || firstAttach) {
            controller.refresh(player, true);
        }
        controller.update(player);
        schedule();
    }

    private boolean canShow(PlayerManager player) {
        boolean activityForeground = App.activity() != null;
        boolean enabled = PlayerSetting.isDesktopLyrics();
        boolean ready = player != null && !player.isEmpty();
        boolean playing = ready && player.isPlaying();
        boolean inferredAudio = ready && LyricsController.isAudioContent(player);
        boolean eligible = !foreground && !activityForeground && enabled && playing && (audioContent || inferredAudio);
        boolean canShow = eligible && canDrawOverlays();
        String state = "show=" + canShow
                + " foreground=" + foreground
                + " activity=" + activityForeground
                + " enabled=" + enabled
                + " eligible=" + eligible
                + " ready=" + ready
                + " playing=" + playing
                + " explicitAudio=" + audioContent
                + " inferredAudio=" + inferredAudio;
        if (!Objects.equals(displayState, state)) {
            displayState = state;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("desktop-lyrics", state);
        }
        return canShow;
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
    }

    private void ensureAttached() {
        if (windowManager == null) return;
        if (view == null) {
            view = new LyricsOverlayView(context);
            view.setDesktopMode(true);
            view.setOnTouchListener(this::onTouch);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }
        if (attached) return;
        try {
            params = buildParams();
            windowManager.addView(view, params);
            attached = true;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("desktop-lyrics", "window attached x=%d y=%d width=%d", params.x, params.y, params.width);
        } catch (Throwable error) {
            attached = false;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("desktop-lyrics", error);
        }
    }

    private WindowManager.LayoutParams buildParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.width = windowWidth();
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = PlayerSetting.getDesktopLyricsX(defaultX(params.width));
        params.y = PlayerSetting.getDesktopLyricsY(defaultY());
        clamp(params);
        params.format = PixelFormat.TRANSLUCENT;
        params.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        return params;
    }

    private boolean onTouch(View view, MotionEvent event) {
        if (params == null || windowManager == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downX = params.x;
                downY = params.y;
                dragging = false;
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                int dx = Math.round(event.getRawX() - downRawX);
                int dy = Math.round(event.getRawY() - downRawY);
                if (!dragging && Math.abs(dx) < touchSlop && Math.abs(dy) < touchSlop) return true;
                dragging = true;
                params.x = downX + dx;
                params.y = downY + dy;
                clamp(params);
                updateLayout();
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) PlayerSetting.putDesktopLyricsPosition(params.x, params.y);
                view.performClick();
                dragging = false;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void updateLayout() {
        try {
            if (attached && view != null) windowManager.updateViewLayout(view, params);
        } catch (Throwable ignored) {
        }
    }

    private void clamp(WindowManager.LayoutParams params) {
        params.x = Math.min(Math.max(params.x, 0), Math.max(0, screenWidth() - params.width));
        params.y = Math.min(Math.max(params.y, dp(24)), Math.max(dp(24), screenHeight() - dp(120)));
    }

    private int windowWidth() {
        int width = screenWidth() - dp(32);
        return Math.min(Math.max(width, dp(280)), dp(760));
    }

    private int defaultX(int width) {
        return Math.max(0, (screenWidth() - width) / 2);
    }

    private int defaultY() {
        return Math.max(dp(24), Math.round(screenHeight() * 0.68f));
    }

    private int screenWidth() {
        return displayMetrics().widthPixels;
    }

    private int screenHeight() {
        return displayMetrics().heightPixels;
    }

    private DisplayMetrics displayMetrics() {
        return context.getResources().getDisplayMetrics();
    }

    private void onTick() {
        update(player, false);
    }

    private void schedule() {
        App.post(tick, UPDATE_INTERVAL_MS);
    }

    private void hide() {
        App.removeCallbacks(tick);
        if (controller != null) {
            controller.release();
            controller = null;
        }
        appliedLyricsVersion = -1;
        if (!attached || windowManager == null || view == null) return;
        try {
            windowManager.removeView(view);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("desktop-lyrics", "window detached");
        } catch (Throwable error) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("desktop-lyrics", error);
        }
        attached = false;
    }

    private int dp(float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
