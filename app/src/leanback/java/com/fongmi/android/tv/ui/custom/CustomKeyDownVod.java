package com.fongmi.android.tv.ui.custom;

import android.app.Activity;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.player.PlaybackTrace;
import com.fongmi.android.tv.utils.KeyUtil;

public class CustomKeyDownVod extends GestureDetector.SimpleOnGestureListener {

    private final GestureDetector detector;
    private final Listener listener;
    private final SeekSession seek;
    private boolean changeSpeed;
    private boolean full;

    public static CustomKeyDownVod create(Activity activity) {
        return new CustomKeyDownVod(activity);
    }

    private CustomKeyDownVod(Activity activity) {
        this.detector = new GestureDetector(activity, this);
        this.listener = (Listener) activity;
        this.seek = new SeekSession(listener);
    }

    public boolean onTouchEvent(MotionEvent e) {
        if (!full) return false;
        return detector.onTouchEvent(e);
    }

    public boolean isChangingSpeed() {
        return changeSpeed;
    }

    public void releaseSpeed() {
        if (!changeSpeed) return;
        changeSpeed = false;
        listener.onSpeedEnd();
    }

    public void setFull(boolean full) {
        if (!full) cancelSeek();
        this.full = full;
    }

    public boolean hasEvent(KeyEvent event) {
        return KeyUtil.isEnterKey(event) || KeyUtil.isUpKey(event) || KeyUtil.isDownKey(event) || KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event);
    }

    public boolean onKeyDown(KeyEvent event) {
        check(event);
        return true;
    }

    private void check(KeyEvent event) {
        if (seek.onKey(event)) return;
        if (KeyUtil.isActionUp(event) && KeyUtil.isUpKey(event)) {
            if (changeSpeed) listener.onSpeedEnd();
            else listener.onKeyUp();
            changeSpeed = false;
        } else if (KeyUtil.isActionUp(event) && KeyUtil.isDownKey(event)) {
            listener.onKeyDown();
        } else if (KeyUtil.isActionUp(event) && KeyUtil.isEnterKey(event)) {
            listener.onKeyCenter();
        } else if (!changeSpeed && event.isLongPress() && KeyUtil.isUpKey(event)) {
            listener.onSpeedUp();
            changeSpeed = true;
        }
    }

    @Override
    public boolean onDoubleTap(@NonNull MotionEvent e) {
        listener.onDoubleTap();
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
        listener.onSingleTap();
        return true;
    }

    public void reset() {
        cancelSeek();
    }

    public void cancelSeek() {
        seek.cancelSeek();
    }

    static final class SeekSession {
        private final SeekListener listener;
        private final Runnable commit = this::commitSeek;
        private int keyCode = KeyEvent.KEYCODE_UNKNOWN;
        private int deviceId;
        private long downTime;
        private long offset;
        private String session;
        private boolean pending;

        SeekSession(SeekListener listener) {
            this.listener = listener;
        }

        void cancelSeek() {
            keyCode = KeyEvent.KEYCODE_UNKNOWN;
            cancelPending();
        }

        private void cancelPending() {
            boolean wasPending = pending;
            App.removeCallbacks(commit);
            offset = 0;
            session = null;
            pending = false;
            if (wasPending) listener.onSeekCancel();
        }

        boolean onKey(KeyEvent event) {
            if (!KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event)) {
                if (KeyUtil.isActionDown(event)) cancelSeek();
                return false;
            }
            if (KeyUtil.isActionDown(event)) onDown(event);
            else if (KeyUtil.isActionUp(event)) onUp(event);
            return true;
        }

        private void onDown(KeyEvent event) {
            String current = listener.getSeekSession();
            if (event.getRepeatCount() == 0) {
                if (pending && !session.equals(current)) cancelSeek();
                keyCode = event.getKeyCode();
                deviceId = event.getDeviceId();
                downTime = event.getDownTime();
            }
            if (!owns(event)) return;
            if (event.isCanceled()) {
                cancelSeek();
            } else if (!listener.canSeek() || current == null || PlaybackTrace.NONE.equals(current)) {
                cancelPending();
            } else if (pending && !session.equals(current)) {
                cancelSeek();
            } else {
                App.removeCallbacks(commit);
                session = current;
                pending = true;
                offset += KeyUtil.isLeftKey(event) ? -Constant.INTERVAL_SEEK : Constant.INTERVAL_SEEK;
                listener.onSeeking(offset);
            }
        }

        private void onUp(KeyEvent event) {
            if (!owns(event)) {
                // 无配对 DOWN 的按键仍交给监听者判定（VOD/Cast 为 no-op）
                if (!pending) listener.onSeekUnavailable(event);
                return;
            }
            keyCode = KeyEvent.KEYCODE_UNKNOWN;
            if (event.isCanceled()) {
                cancelSeek();
            } else if (!listener.canSeek()) {
                cancelPending();
                listener.onSeekUnavailable(event);
            } else if (pending && session.equals(listener.getSeekSession())) {
                App.post(commit, 250);
            } else {
                cancelSeek();
            }
        }

        private boolean owns(KeyEvent event) {
            return keyCode == event.getKeyCode() && deviceId == event.getDeviceId() && downTime == event.getDownTime();
        }

        private void commitSeek() {
            if (!pending || !listener.canSeek() || !session.equals(listener.getSeekSession()) || offset == 0) {
                cancelSeek();
                return;
            }
            long time = offset;
            offset = 0;
            pending = false;
            session = null;
            listener.onSeekEnd(time);
        }
    }

    public interface SeekListener {
        boolean canSeek();
        String getSeekSession();
        void onSeeking(long time);
        void onSeekEnd(long time);
        void onSeekCancel();
        default void onSeekUnavailable(KeyEvent event) { }
    }

    public interface Listener extends SeekListener {
        void onSpeedUp();
        void onSpeedEnd();
        void onKeyUp();
        void onKeyDown();
        void onKeyCenter();
        void onSingleTap();
        void onDoubleTap();
    }
}
