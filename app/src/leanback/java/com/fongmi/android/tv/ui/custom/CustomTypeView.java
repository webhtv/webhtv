package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.KeyUtil;
import com.google.android.material.textview.MaterialTextView;

public class CustomTypeView extends MaterialTextView {

    public static final long LONG_PRESS_MS = 550;

    private Listener listener;
    private LongPressListener longPressListener;
    private boolean coolDown;
    private boolean longPressFired;

    private final Runnable longPressRunnable = () -> {
        longPressFired = true;
        if (longPressListener != null) longPressListener.onLongPress();
    };

    public CustomTypeView(@NonNull Context context) {
        super(context);
    }

    public CustomTypeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setLongPressListener(LongPressListener listener) {
        this.longPressListener = listener;
    }

    private boolean hasEvent(KeyEvent event) {
        return !coolDown && KeyUtil.isActionDown(event) && KeyUtil.isUpKey(event);
    }

    private boolean hasLongPressEvent(KeyEvent event) {
        return longPressListener != null && KeyUtil.isEnterKey(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (listener != null && hasEvent(event)) return onKeyDown();
        if (hasLongPressEvent(event)) return onLongPressKey(event);
        return super.dispatchKeyEvent(event);
    }

    private boolean onLongPressKey(KeyEvent event) {
        if (KeyUtil.isActionDown(event)) {
            // Key repeats must not restart the timer; only the first press arms it. A fresh press
            // also clears any latch left behind by a release this view never received (pause,
            // sleep, detach), otherwise that stale latch would swallow the next click.
            if (event.getRepeatCount() == 0) {
                longPressFired = false;
                App.post(longPressRunnable, LONG_PRESS_MS);
            }
            return super.dispatchKeyEvent(event);
        }
        if (KeyUtil.isActionUp(event)) {
            cancelLongPressTimer();
            boolean consumed = longPressFired;
            // The long press already ran, so this release must not deliver a click as well.
            if (consumed) longPressFired = false;
            else super.dispatchKeyEvent(event);
            return consumed;
        }
        return super.dispatchKeyEvent(event);
    }

    private void cancelLongPressTimer() {
        App.removeCallbacks(longPressRunnable);
    }

    private boolean onKeyDown() {
        App.post(() -> coolDown = false, 3000);
        listener.onRefresh();
        coolDown = true;
        return true;
    }

    @Override
    public boolean performClick() {
        if (longPressFired) {
            longPressFired = false;
            return true;
        }
        return super.performClick();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        App.post(() -> coolDown = false, 500);
        if (focused) coolDown = true;
        else {
            cancelLongPressTimer();
            longPressFired = false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelLongPressTimer();
        longPressFired = false;
        super.onDetachedFromWindow();
    }

    public interface Listener {

        void onRefresh();
    }

    public interface LongPressListener {

        void onLongPress();
    }
}
