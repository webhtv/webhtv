package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ViewEmptyBinding;
import com.fongmi.android.tv.databinding.ViewProgressBinding;
import com.fongmi.android.tv.plugin.PluginHostEvents;

import java.util.ArrayList;
import java.util.List;

public class ProgressLayout extends RelativeLayout {

    private static final String TAG_PROGRESS = "ProgressLayout.TAG_PROGRESS";

    public enum State {
        CONTENT, PROGRESS, EMPTY, ERROR
    }

    private List<View> mContentViews;
    private View mProgressView;
    private View mEmptyView;
    private int mEmptyTitleRes;
    private int mEmptyDescriptionRes;
    private int mEmptyActionRes;
    private OnClickListener mEmptyActionListener;
    private int mErrorTitleRes;
    private CharSequence mErrorReason;
    private int mErrorRetryRes;
    private OnClickListener mErrorRetryListener;
    private State mState;

    public ProgressLayout(Context context) {
        super(context);
    }

    public ProgressLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ProgressLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mState = State.CONTENT;
        mContentViews = new ArrayList<>();
    }

    private void ensureEmptyView() {
        if (mEmptyView != null) return;
        mEmptyView = ViewEmptyBinding.inflate(LayoutInflater.from(getContext())).getRoot();
        mEmptyView.setTag(TAG_PROGRESS);
        mEmptyView.setVisibility(GONE);
        addView(mEmptyView, centerParams());
        applyEmptyContent();
    }
    /**
     * 配置空态内容。
     *
     * <p>设计文档 §9.9 要求空态是「标题 + 解释 + 一个主动作」，并明确「不能只显示一句
     * '暂无数据'」。本方法让宿主按需接入：<b>不调用时渲染结果与改动前完全一致</b>
     * （只有标题、解释与动作保持 GONE），因此既有调用点无需改动。
     *
     * <p>可在任意时刻调用（早于首次显示也可以）：空态视图尚未创建时会先记住参数，
     * 在创建时应用。
     *
     * @param titleRes       标题；传 0 表示沿用布局里的默认文案
     * @param descriptionRes 解释；传 0 表示不显示
     * @param actionRes      主按钮文案；传 0 或不传监听器表示不显示按钮
     * @param listener       主按钮动作；为空则不显示按钮（避免出现点了没反应的死按钮）
     */
    public void setEmptyContent(int titleRes, int descriptionRes, int actionRes, OnClickListener listener) {
        mEmptyTitleRes = titleRes;
        mEmptyDescriptionRes = descriptionRes;
        mEmptyActionRes = actionRes;
        mEmptyActionListener = listener;
        applyEmptyContent();
    }

    private void applyEmptyContent() {
        if (mEmptyView == null) return;
        // 用 findViewById 而不是 ViewEmptyBinding 的字段：view_empty.xml 是**分 flavor** 的，
        // mobile 版没有 description/action 两个控件，而本类在 main 源集。直接引用 binding
        // 字段会让 mobile 编译失败；这里按 id 查找并对缺失做空值守卫，于是 TV 端拿到完整空态、
        // 手机端保持原样（传进来的内容会被静默忽略）。
        View illustration = mEmptyView.findViewById(R.id.illustration);
        TextView title = mEmptyView.findViewById(R.id.text);
        TextView description = mEmptyView.findViewById(R.id.description);
        TextView action = mEmptyView.findViewById(R.id.action);
        // 从错误态回到空态时要把外观复原：插画显示、标题回到常规前景色。
        if (illustration != null) illustration.setVisibility(VISIBLE);
        if (title != null) {
            title.setTextColor(themeColor(R.attr.webhtvColorOnWallpaper));
            if (mEmptyTitleRes != 0) title.setText(mEmptyTitleRes);
        }
        if (description != null) {
            if (mEmptyDescriptionRes != 0) {
                description.setText(mEmptyDescriptionRes);
                description.setVisibility(VISIBLE);
            } else {
                description.setVisibility(GONE);
            }
        }
        if (action != null) {
            // 没有动作就不显示按钮：避免出现点了没反应的死按钮。
            if (mEmptyActionRes != 0 && mEmptyActionListener != null) {
                action.setText(mEmptyActionRes);
                action.setOnClickListener(mEmptyActionListener);
                action.setVisibility(VISIBLE);
            } else {
                action.setOnClickListener(null);
                action.setVisibility(GONE);
            }
        }
    }

    /**
     * 进入错误态：设计文档 §9.9「错误态：错误色 + 原因 + 重试动作」。
     *
     * <p>复用空态的三个槽位（标题 / 解释 / 主动作），只做两处区分：标题改用
     * {@code ?attr/webhtvColorOnWallpaperError}，并隐藏插画——空态那张"空箱子"表达的是
     * "这里没有内容"，用在错误上会误导。
     *
     * <p>原因正文刻意**不**用错误色：错误前景色在壁纸上是 4.11:1（只够 Display 档大字号），
     * 而原因是 16sp 正文，需要 4.5:1；它沿用空态的次级壁纸前景色。
     *
     * @param titleRes 错误标题；传 0 沿用当前标题
     * @param reasonRes 失败原因；传 0 不显示
     * @param retryRes 重试按钮文案；传 0 或不传监听器则不显示按钮
     * @param retry 重试动作
     */
    public void showError(int titleRes, int reasonRes, int retryRes, OnClickListener retry) {
        showError(titleRes, reasonRes == 0 ? null : getContext().getString(reasonRes), retryRes, retry);
    }

    /**
     * 同上，但原因来自运行时字符串（例如服务端返回的 message）。
     */
    public void showError(int titleRes, CharSequence reason, int retryRes, OnClickListener retry) {
        mErrorTitleRes = titleRes;
        mErrorReason = reason;
        mErrorRetryRes = retryRes;
        mErrorRetryListener = retry;
        // 已经在错误态时 switchState 会提前返回，但重试再次失败需要刷新原因文案，
        // 所以这里显式重放一次。
        if (mState == State.ERROR) applyErrorContent();
        else switchState(State.ERROR);
    }

    public boolean isError() {
        return mState == State.ERROR;
    }

    private void applyErrorContent() {
        if (mEmptyView == null) return;
        View illustration = mEmptyView.findViewById(R.id.illustration);
        TextView title = mEmptyView.findViewById(R.id.text);
        TextView description = mEmptyView.findViewById(R.id.description);
        TextView action = mEmptyView.findViewById(R.id.action);
        if (illustration != null) illustration.setVisibility(GONE);
        if (title != null) {
            title.setTextColor(themeColor(R.attr.webhtvColorOnWallpaperError));
            if (mErrorTitleRes != 0) title.setText(mErrorTitleRes);
        }
        if (description != null) {
            if (mErrorReason != null && mErrorReason.length() > 0) {
                description.setText(mErrorReason);
                description.setVisibility(VISIBLE);
            } else {
                description.setVisibility(GONE);
            }
        }
        if (action != null) {
            if (mErrorRetryRes != 0 && mErrorRetryListener != null) {
                action.setText(mErrorRetryRes);
                action.setOnClickListener(mErrorRetryListener);
                action.setVisibility(VISIBLE);
            } else {
                action.setOnClickListener(null);
                action.setVisibility(GONE);
            }
        }
    }

    /** 解析主题属性里的颜色值；未赋值时返回 {@code 0}（透明），调用方自行决定是否使用。 */
    private int themeColor(int attr) {
        android.util.TypedValue value = new android.util.TypedValue();
        return getContext().getTheme().resolveAttribute(attr, value, true) ? value.data : 0;
    }

    private void ensureProgressView() {
        if (mProgressView != null) return;
        mProgressView = ViewProgressBinding.inflate(LayoutInflater.from(getContext())).getRoot();
        mProgressView.setTag(TAG_PROGRESS);
        mProgressView.setVisibility(GONE);
        addView(mProgressView, centerParams());
    }

    private LayoutParams centerParams() {
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(CENTER_IN_PARENT);
        return params;
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        if (child.getTag() == null || !child.getTag().equals(TAG_PROGRESS)) {
            mContentViews.add(child);
        }
    }

    /** Adds a child that must not follow CONTENT/PROGRESS/EMPTY visibility changes. */
    public void addOverlayView(View child, ViewGroup.LayoutParams params) {
        addView(child, params);
        mContentViews.remove(child);
    }

    public void showProgress() {
        switchState(State.PROGRESS);
    }

    public void showEmpty() {
        switchState(State.EMPTY);
    }

    public void showContent() {
        switchState(State.CONTENT);
    }

    // 强制把内容视图重新隐藏（仅在 PROGRESS/EMPTY 态有意义）：
    // 用于内容填充（如 setText）在 loading 期间把部分子视图改回 VISIBLE 后，重新压回隐藏，避免泄漏
    public void hideContent() {
        if (mState == State.CONTENT) return;
        setContentVisibility(false);
    }

    public void showContent(boolean flag, int size) {
        if (flag && size == 0) showEmpty();
        else showContent();
    }

    public boolean isProgress() {
        return mState == State.PROGRESS;
    }

    public boolean isContent() {
        return mState == State.CONTENT;
    }

    public boolean isEmpty() {
        return mState == State.EMPTY;
    }

    public void switchState(State state) {
        if (mState == state) return;
        mState = state;
        applyScrim(state);
        switch (state) {
            case CONTENT:
                if (mEmptyView != null) mEmptyView.setVisibility(GONE);
                if (mProgressView != null) mProgressView.setVisibility(GONE);
                setContentVisibility(true);
                // 宿主内容可见的埋点。本类被 Home/File/History/Keep/Video 等多页共用，
                // 因此事件只带 Activity 身份，由订阅者按类型过滤（详见 PluginEventBus 类注释）。
                PluginHostEvents.uiContentShown(this);
                break;
            case PROGRESS:
                ensureProgressView();
                if (mEmptyView != null) mEmptyView.setVisibility(GONE);
                mProgressView.setVisibility(VISIBLE);
                setContentVisibility(false);
                break;
            case EMPTY:
                ensureEmptyView();
                // 从错误态过来时要把外观复原（插画显示、标题回到常规前景色），
                // 所以进 EMPTY 时重新应用一次空态内容。
                applyEmptyContent();
                mEmptyView.setVisibility(VISIBLE);
                if (mProgressView != null) mProgressView.setVisibility(GONE);
                setContentVisibility(false);
                break;
            case ERROR:
                ensureEmptyView();
                applyErrorContent();
                mEmptyView.setVisibility(VISIBLE);
                if (mProgressView != null) mProgressView.setVisibility(GONE);
                setContentVisibility(false);
                break;
        }
    }

    /**
     * 空态/错误态给浮层加 scrim，其余状态清掉。
     *
     * <p><b>为什么必须加。</b>设计文档 §5.3 要求「图片上的文字必须有 scrim/gradient」、
     * §5.4 要求「所有内容表面必须有不透明或可控的 surface 层」。而空态/错误态的文字此前
     * **直接压在壁纸（一张图）上、没有任何 scrim**——这违反那两条，不只是对比度差一点。
     *
     * <p>而且靠调色无解：实测壁纸合成亮度 L=0.1217/0.1476/0.2019，正文（16sp）门槛 4.5:1，
     * 80% 白文字只有 4.61/4.08/3.30:1；**即使换成纯白，最亮壁纸上限也只有 4.17:1**，
     * 即 4.5:1 物理上不可达。加 scrim 后（overlay_dark ≈32% 黑）为 6.02/5.43/4.53:1，全部达标。
     *
     * <p>加在 {@code ProgressLayout} 自身而不是 {@code view_empty.xml} 的内容块上：
     * 空态视图是**居中摆放的 wrap_content 内容块**（{@code centerParams()}），
     * 给它加背景会变成"文字后面一块小方框"；本类是 match_parent 的宿主层，盖的是整屏。
     *
     * <p>PROGRESS 态刻意**不加**：它是短暂的加载瞬间，加 scrim 会让页面闪一下暗。
     */
    private void applyScrim(State state) {
        if (state == State.EMPTY || state == State.ERROR) {
            setBackgroundColor(themeColor(R.attr.webhtvColorOverlayDark));
        } else {
            setBackground(null);
        }
    }

    private void setContentVisibility(boolean visible) {
        for (View view : mContentViews) {
            if (visible) showView(view);
            else hideView(view);
        }
    }

    private void showView(View view) {
        // 幂等：已经完整可见时不再重放淡入。
        //
        // 为什么必须幂等。switchState(CONTENT) 会在 EMPTY/ERROR ↔ CONTENT 之间被反复调用，
        // 而每次切换都会走一遍 setContentVisibility(true)。旧实现每次都 setAlpha(0f) 再
        // new 一个 ViewPropertyAnimator，两步都会请求一次绘制；加载圈期间主线程本来就紧绷
        // （详情页/播放页要 inflate、解析、建 ExoPlayer），这些无效的 alpha 动画会把转圈
        // 的帧挤掉——用户看到的就是「转圈卡住不动再恢复」。
        if (view.getVisibility() == VISIBLE && view.getAlpha() == 1f) return;
        // 取消上一轮可能还在跑的淡入，避免多个 animator 同时写同一个 alpha。
        view.animate().cancel();
        view.setAlpha(0f);
        view.setVisibility(VISIBLE);
        view.animate().alpha(1f).setDuration(100);
    }

    private void hideView(View view) {
        if (view.getVisibility() == INVISIBLE) return;
        // 取消未完成的淡入：否则它仍会把 alpha 推到 1，下一次 show 又从 0 重来，
        // 同样是一帧白干的请求。
        view.animate().cancel();
        view.setVisibility(INVISIBLE);
    }
}
