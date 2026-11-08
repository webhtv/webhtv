package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;

public class CustomRecyclerView extends RecyclerView {

    private int minWidth;
    private int minHeight;
    private int maxWidth;
    private int maxHeight;
    private int touchSlop;
    private float x1;
    private float y1;

    public CustomRecyclerView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public CustomRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CustomRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        if (attrs != null) setAttrs(context, attrs);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    private void setAttrs(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CustomRecyclerView);
        minWidth = a.getDimensionPixelSize(R.styleable.CustomRecyclerView_android_minWidth, 0);
        minHeight = a.getDimensionPixelSize(R.styleable.CustomRecyclerView_android_minHeight, 0);
        maxWidth = a.getDimensionPixelSize(R.styleable.CustomRecyclerView_maxWidth, 0);
        maxHeight = a.getDimensionPixelSize(R.styleable.CustomRecyclerView_maxHeight, 0);
        a.recycle();
    }

    public void setMinWidth(int minWidth) {
        this.minWidth = minWidth;
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
    }

    public void setMinHeight(int minHeight) {
        this.minHeight = minHeight;
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    private int getConstrainedSize(int measuredSize, int minSize, int maxSize) {
        int finalSize = measuredSize;
        if (maxSize > 0) finalSize = Math.min(finalSize, maxSize);
        if (minSize > 0) finalSize = Math.max(finalSize, minSize);
        return finalSize;
    }

    private int getConstrainedMeasureSpec(int measureSpec, int maxSize) {
        if (maxSize <= 0) return measureSpec;
        int mode = View.MeasureSpec.getMode(measureSpec);
        int size = View.MeasureSpec.getSize(measureSpec);
        if (mode == View.MeasureSpec.UNSPECIFIED) return View.MeasureSpec.makeMeasureSpec(maxSize, View.MeasureSpec.AT_MOST);
        if (mode == View.MeasureSpec.AT_MOST && size > maxSize) return View.MeasureSpec.makeMeasureSpec(maxSize, View.MeasureSpec.AT_MOST);
        return measureSpec;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        widthMeasureSpec = getConstrainedMeasureSpec(widthMeasureSpec, maxWidth);
        heightMeasureSpec = getConstrainedMeasureSpec(heightMeasureSpec, maxHeight);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int finalWidth = getConstrainedSize(getMeasuredWidth(), minWidth, maxWidth);
        int finalHeight = getConstrainedSize(getMeasuredHeight(), minHeight, maxHeight);
        setMeasuredDimension(finalWidth, finalHeight);
    }

    private static final int FOCUS_ATTEMPTS = 5;
    private static final long FOCUS_RETRY_DELAY_MS = 50;

    private void focus(int position) {
        focus(position, FOCUS_ATTEMPTS);
    }

    /**
     * 把焦点落到指定行，但在布局完成前不放弃。
     *
     * <p>弹层刚 show 时 RecyclerView 往往还没走到 onLayout，{@code findViewHolderForLayoutPosition}
     * 会返回 null，原先的实现就直接丢掉这次请求——结果是整张列表没有任何行处于 focus，
     * 遥控器上下键既看不到焦点环，也看不出光标停在哪（用户报告「TV 版本视轨/音轨没有焦点显示」）。
     * 电视盒子冷启动时更容易命中这个时序。
     *
     * <p>失败时按 50ms 重试，总时长上限 {@code FOCUS_ATTEMPTS * FOCUS_RETRY_DELAY_MS} = 250ms。
     * 只要列表中已经有子 View 拿到焦点（用户已经开始按键）就立刻停止，不去抢焦点。
     */
    private void focus(int position, int attempts) {
        ViewHolder holder = findViewHolderForLayoutPosition(position);
        if (holder != null) {
            holder.itemView.requestFocus();
            return;
        }
        if (getFocusedChild() != null || attempts <= 1) return;
        postDelayed(() -> focus(position, attempts - 1), FOCUS_RETRY_DELAY_MS);
    }

    @Override
    public void scrollToPosition(int position) {
        super.scrollToPosition(position);
        postDelayed(() -> focus(position), FOCUS_RETRY_DELAY_MS);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getPointerCount() != 1) return false;
        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                x1 = y1 = 0;
                break;
            case MotionEvent.ACTION_DOWN:
                x1 = event.getX();
                y1 = event.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                float x2 = event.getX();
                float y2 = event.getY();
                float offsetX = Math.abs(x2 - x1);
                float offsetY = Math.abs(y2 - y1);
                if (offsetX > offsetY && offsetX > touchSlop) getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        return super.dispatchTouchEvent(event);
    }
}
