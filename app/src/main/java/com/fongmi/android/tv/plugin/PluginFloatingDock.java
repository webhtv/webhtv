package com.fongmi.android.tv.plugin;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Prefers;

import java.util.Locale;

/**
 * 悬浮入口的挂载与拖动：手机端的悬浮球、电视端"没有控制条时"的可聚焦浮标，以及它们共享的
 * 落点记忆与吸附逻辑。
 *
 * <p><b>为什么整体搬出 {@link PluginUiPresenter}。</b>拖动状态机要求的坐标换算是"父容器实测
 * 尺寸 + 手指位移 + 边距"三者的几何关系，和"哪个槽位挂到哪里"是两条毫不相干的推理链；混在
 * 一个类里，六百多行中有一多半在讲边距，找槽位判定得先翻过整段几何。拆开后渲染器只回答
 * "挂什么、挂到哪"，本类只回答"挂上去之后怎么动"。
 *
 * <p><b>为什么直接收 {@link PluginUiPresenter} 而不是两个回调。</b>挂载要登记控件、点击要派发
 * 事件，这两件事在每条路径上都同时发生；传渲染器本身，签名只多一个参数，也不会出现"登记了
 * 却忘了派发"的半套接线。
 */
final class PluginFloatingDock {

    /** 拖动落点的记忆键前缀，完整键为 {@code plugin_chip_pos:<pluginId>:<action>}。 */
    private static final String POSITION_PREFIX = "plugin_chip_pos:";

    /** 吸附后与屏幕左右边的间距（dp），与旧版默认落点一致。 */
    private static final int EDGE_INSET_DP = 16;

    /** 纵向钳制时留出的最小上下边距（dp），避免 chip 贴死在状态栏或导航栏上。 */
    private static final int VERTICAL_INSET_DP = 24;

    private PluginFloatingDock() {
    }

    /**
     * 挂一个悬浮按钮。电视端不进焦点链（见 {@link PluginUiPresenter} 类注释）；手机端可用手指
     * 拖动并按插件记忆落点。
     *
     * <p><b>为什么只有这一条路径装触摸监听。</b>控制条里的 chip 由播放页自己管手势与焦点，
     * 电视端的可聚焦浮标靠方向键而非手指——给它们装拖动等于在两套既有交互上叠一层冲突。
     * 悬浮按钮是手机端唯一的自由落点，它是"能拖"这件事的最小且唯一适用面。
     */
    static void addFloating(PluginUiPresenter owner, ViewGroup decor, Activity activity,
            PluginHost.PluginSlot entry, boolean leanback) {
        TextView button = PluginChipFactory.create(owner, activity, entry, false);
        button.setFocusable(!leanback);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.END | Gravity.BOTTOM;
        params.setMarginEnd(PluginChipFactory.dp(activity, 16));
        params.bottomMargin = PluginChipFactory.dp(activity, 96);
        if (!leanback) {
            // 复原必须赶在 addView 之前：落点只能靠 LayoutParams 表达，挂上去再改会先闪一次默认位置。
            restorePosition(activity, decor, entry, button, params);
            DragState drag = new DragState();
            button.setOnTouchListener((view, event) -> onChipTouch(drag, decor, activity, entry, view, params, event));
            // 拖过的手指不该再算一次点击。这里重设监听而不是 setOnClickListener(null)：
            // 置 null 会让 chip 变成不可点击，拖一次之后就再也打不开面板。
            button.setOnClickListener(view -> {
                if (drag.consumeClick()) return;
                owner.dispatch(entry, view);
            });
        }
        decor.addView(button, params);
        owner.register(button);
    }

    /**
     * 挂一颗**可聚焦**的浮标，给电视端"没有控制条"的页面用。
     *
     * <p>与 {@link #addFloating} 的唯一差别是焦点：这里 {@code setFocusable(true)} 并自己切换
     * 焦点环。电视上焦点环是"我在哪"的唯一表达，普通浮标的半透明底在选中与未选中时一模一样，
     * 用户按方向键选中它之后看不出变化，反而会怀疑遥控器坏了。
     */
    static void addFocusableFloating(PluginUiPresenter owner, ViewGroup decor, Activity activity,
            PluginHost.PluginSlot entry) {
        TextView button = PluginChipFactory.create(owner, activity, entry, false);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setBackground(PluginChipFactory.ringBackground(activity, false));
        button.setOnFocusChangeListener((view, focused) -> view.setBackground(
                PluginChipFactory.ringBackground(activity, focused)));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.END | Gravity.BOTTOM;
        params.setMarginEnd(PluginChipFactory.dp(activity, 16));
        params.bottomMargin = PluginChipFactory.dp(activity, 96);
        decor.addView(button, params);
        owner.register(button);
    }

    /**
     * 复原上次拖动后的落点。
     *
     * <p>必须在 {@code decor.addView} 之前做完：位置只能通过 LayoutParams 的边距表达，挂上去之后
     * 再改会先闪一次默认落点。也正因为此刻控件还没布局，高度只能自己量一次，再按"中心 Y"反推
     * 上/下边距。
     *
     * <p>没有记录、格式坏了、{@code Prefers} 不可用（纯 JVM 单测里没有 Init.context()）一律静默
     * 保留默认位置：记忆是便利，不该因为它坏了就把入口挪到屏幕外或让它消失。
     */
    private static void restorePosition(Context context, ViewGroup decor, PluginHost.PluginSlot entry,
            TextView chip, FrameLayout.LayoutParams params) {
        String raw = readPosition(entry);
        if (raw == null) return;
        int separator = raw.indexOf(':');
        if (separator <= 0 || separator >= raw.length() - 1) return;
        String side = raw.substring(0, separator).trim();
        boolean atEnd = "end".equalsIgnoreCase(side);
        if (!atEnd && !"start".equalsIgnoreCase(side)) return;
        float ratio;
        try {
            ratio = Float.parseFloat(raw.substring(separator + 1).trim());
        } catch (NumberFormatException e) {
            return;
        }
        // NaN 会被这个比较挡掉（NaN 与任何数比较都为假），不必单独判。
        if (!(ratio >= 0f && ratio <= 1f)) return;
        chip.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int height = chip.getMeasuredHeight();
        if (height <= 0) return;
        int parentHeight = heightOf(decor, context);
        if (parentHeight <= 0) return;
        int top = clampTop(Math.round(ratio * parentHeight) - height / 2, height, parentHeight);
        applySide(params, atEnd, context, height, top, parentHeight);
    }

    /** 把"贴左还是贴右 + 纵向位置"写进 LayoutParams。两处调用（复原与吸附）必须同源，否则会漂。 */
    private static void applySide(FrameLayout.LayoutParams params, boolean atEnd, Context context,
            int chipHeight, int top, int parentHeight) {
        if (atEnd) {
            params.gravity = Gravity.END | Gravity.BOTTOM;
            params.setMarginStart(0);
            params.setMarginEnd(PluginChipFactory.dp(context, EDGE_INSET_DP));
            params.topMargin = 0;
            params.bottomMargin = Math.max(0, parentHeight - top - chipHeight);
        } else {
            params.gravity = Gravity.START | Gravity.TOP;
            params.setMarginStart(PluginChipFactory.dp(context, EDGE_INSET_DP));
            params.setMarginEnd(0);
            params.topMargin = Math.max(0, top);
            params.bottomMargin = 0;
        }
    }

    /**
     * 拖动中实时改边距。
     *
     * <p>统一改用 {@code START | TOP}：拖动时按绝对坐标最直观，不必为左右两侧各写一套换算；
     * 松手吸附时再切回贴边的 gravity。
     */
    private static void applyDrag(DragState drag, View view, FrameLayout.LayoutParams params, int left, int top) {
        int maxLeft = Math.max(0, drag.parentWidth - drag.chipWidth);
        int maxTop = Math.max(0, drag.parentHeight - drag.chipHeight);
        params.gravity = Gravity.START | Gravity.TOP;
        params.setMarginStart(Math.max(0, Math.min(maxLeft, left)));
        params.setMarginEnd(0);
        params.topMargin = Math.max(0, Math.min(maxTop, top));
        params.bottomMargin = 0;
        // 记下最后一次真的写进去的坐标。setLayoutParams 只是排一次布局，抬手那一刻
        // view.getLeft()/getTop() 很可能还停在上一次绘制的位置，直接用它会吸附回拖动前。
        drag.lastLeft = params.leftMargin;
        drag.lastTop = params.topMargin;
        view.setLayoutParams(params);
    }

    /**
     * 悬浮按钮的触摸处理。
     *
     * <p><b>为什么 DOWN 与"未超过 slop 的 MOVE"都不消费事件。</b>只有让事件继续走到 view 自己的
     * {@code onTouchEvent}，系统才会计出一次点击、{@code setOnClickListener} 才会触发——这就是
     * "没拖动就是点击"的来由。位移一旦超过 slop 就改为消费事件，手势变成拖动，抬手不再产生点击。
     */
    private static boolean onChipTouch(DragState drag, ViewGroup decor, Activity activity,
            PluginHost.PluginSlot entry, View view, FrameLayout.LayoutParams params, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                drag.tracking = true;
                drag.moved = false;
                drag.suppressClick = false;
                drag.downRawX = event.getRawX();
                drag.downRawY = event.getRawY();
                drag.downLeft = view.getLeft();
                drag.downTop = view.getTop();
                drag.chipWidth = view.getWidth();
                drag.chipHeight = view.getHeight();
                drag.parentWidth = widthOf(decor, activity);
                drag.parentHeight = heightOf(decor, activity);
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (!drag.tracking) return false;
                float dx = event.getRawX() - drag.downRawX;
                float dy = event.getRawY() - drag.downRawY;
                if (!drag.moved) {
                    int slop = slopOf(activity);
                    if (Math.abs(dx) <= slop && Math.abs(dy) <= slop) return false;
                    drag.moved = true;
                    drag.suppressClick = true;
                    // 拖动期间不让父容器抢事件，否则滑到边缘时手势会被别的控件接走。
                    disallowIntercept(view, true);
                    view.setPressed(false);
                }
                applyDrag(drag, view, params, drag.downLeft + Math.round(dx), drag.downTop + Math.round(dy));
                return true;
            }
            case MotionEvent.ACTION_UP: {
                boolean dragged = drag.moved;
                if (dragged) snap(drag, decor, activity, entry, view, params);
                drag.tracking = false;
                drag.moved = false;
                if (!dragged) drag.suppressClick = false;
                disallowIntercept(view, false);
                return dragged;
            }
            case MotionEvent.ACTION_CANCEL:
                drag.tracking = false;
                drag.moved = false;
                drag.suppressClick = false;
                disallowIntercept(view, false);
                return false;
            default:
                return false;
        }
    }

    private static void disallowIntercept(View view, boolean disallow) {
        ViewParent parent = view.getParent();
        if (parent == null) return;
        try {
            parent.requestDisallowInterceptTouchEvent(disallow);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 松手吸附：水平贴到更近的一侧，纵向把 chip 中心钳制在屏内并留出
     * {@link #VERTICAL_INSET_DP} 的上下留白。
     *
     * <p><b>为什么纵向存"中心 Y 的比例"而不是像素。</b>同一个落点在竖屏手机、横屏平板与不同 DPI
     * 上应当是"屏幕靠下的同一个相对位置"；存像素换个设备就跑到屏幕外去了。
     */
    private static void snap(DragState drag, ViewGroup decor, Activity activity, PluginHost.PluginSlot entry,
            View view, FrameLayout.LayoutParams params) {
        int parentWidth = drag.parentWidth > 0 ? drag.parentWidth : widthOf(decor, activity);
        int parentHeight = drag.parentHeight > 0 ? drag.parentHeight : heightOf(decor, activity);
        int chipWidth = view.getWidth() > 0 ? view.getWidth() : drag.chipWidth;
        int chipHeight = view.getHeight() > 0 ? view.getHeight() : drag.chipHeight;
        if (parentWidth <= 0 || parentHeight <= 0 || chipHeight <= 0) return;
        boolean atEnd = (drag.lastLeft + chipWidth / 2) * 2 >= parentWidth;
        int inset = PluginChipFactory.dp(activity, VERTICAL_INSET_DP);
        int minCenter = Math.max(inset, chipHeight / 2);
        int maxCenter = Math.min(parentHeight - inset, parentHeight - chipHeight / 2);
        if (maxCenter < minCenter) maxCenter = minCenter;
        int center = Math.max(minCenter, Math.min(maxCenter, drag.lastTop + chipHeight / 2));
        int top = clampTop(center - chipHeight / 2, chipHeight, parentHeight);
        applySide(params, atEnd, activity, chipHeight, top, parentHeight);
        view.setLayoutParams(params);
        writePosition(entry, atEnd, center, parentHeight);
    }

    /** 纵向钳制：既满足"中心不越界"，也保证 chip 整体在屏内（矮屏上后者更严格）。 */
    private static int clampTop(int top, int chipHeight, int parentHeight) {
        if (parentHeight <= 0 || chipHeight <= 0) return Math.max(0, top);
        int max = Math.max(0, parentHeight - chipHeight);
        return Math.max(0, Math.min(max, top));
    }

    private static int slopOf(Context context) {
        try {
            return ViewConfiguration.get(context).getScaledTouchSlop();
        } catch (Throwable ignored) {
            return PluginChipFactory.dp(context, 8);
        }
    }

    /** 父容器高度：优先 decorView 实测值，尚未布局时退回屏幕尺寸。 */
    static int heightOf(@Nullable ViewGroup decor, Context context) {
        int measured = decor == null ? 0 : decor.getHeight();
        if (measured > 0) return measured;
        try {
            return ResUtil.getScreenHeight(context);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** 父容器宽度，取法与 {@link #heightOf} 一致。 */
    private static int widthOf(@Nullable ViewGroup decor, Context context) {
        int measured = decor == null ? 0 : decor.getWidth();
        if (measured > 0) return measured;
        try {
            return ResUtil.getScreenWidth(context);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * 落点记忆的键。
     *
     * <p>与控件 tag 从前各拼一份"前缀 + pluginId + action"，差别只有前缀常量；两处一旦漂移，
     * tag 能找到控件、落点却记到另一个键上，而这种错只在"拖过并且换页面回来"时才显形。
     * 所以标识部分只留 {@link PluginUiPresenter#identityOf} 一处定义。
     */
    private static String positionKey(PluginHost.PluginSlot entry) {
        return POSITION_PREFIX + PluginUiPresenter.identityOf(entry);
    }

    /**
     * 读记忆。
     *
     * <p>{@code Prefers.getString} 内部只 catch Exception，而纯 JVM 单测里没有
     * {@code Init.context()}，静态初始化失败抛的是 Error——所以这里必须连 Throwable 一起挡。
     */
    @Nullable
    private static String readPosition(PluginHost.PluginSlot entry) {
        try {
            String value = Prefers.getString(positionKey(entry), "");
            return value == null || value.isEmpty() ? null : value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 记住落点，值形如 {@code side:yRatio}。写失败只是丢一次记忆，不该影响本次拖动的手感。 */
    private static void writePosition(PluginHost.PluginSlot entry, boolean atEnd, int center, int parentHeight) {
        if (parentHeight <= 0) return;
        float ratio = Math.max(0f, Math.min(1f, center / (float) parentHeight));
        String value = (atEnd ? "end" : "start") + ":" + String.format(Locale.US, "%.4f", ratio);
        try {
            Prefers.put(positionKey(entry), value);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 单个悬浮按钮的拖动状态。
     *
     * <p>每个 chip 一份而不是做成渲染器字段：同屏可以有多家插件的入口，共用一份状态会让拖动
     * 其中一颗时另一颗跟着跳。
     */
    private static final class DragState {
        boolean tracking;
        boolean moved;
        boolean suppressClick;
        float downRawX;
        float downRawY;
        int downLeft;
        int downTop;
        int chipWidth;
        int chipHeight;
        int parentWidth;
        int parentHeight;
        /** 最后一次写进 LayoutParams 的左上角坐标，父容器坐标系。 */
        int lastLeft;
        int lastTop;

        /** 读一次"这次抬手是否属于刚结束的拖动"，读完即复位，避免吞掉下一次真实点击。 */
        boolean consumeClick() {
            boolean suppressed = suppressClick;
            suppressClick = false;
            return suppressed;
        }
    }

    /** 挂载根：decorView 本身是 ViewGroup；否则退回内容根。绝不强转父节点。 */
    @Nullable
    static ViewGroup decorOf(Activity activity) {
        if (activity == null || activity.getWindow() == null) return null;
        View decor = activity.getWindow().getDecorView();
        if (decor instanceof ViewGroup group) return group;
        View content = activity.findViewById(android.R.id.content);
        return content instanceof ViewGroup group ? group : null;
    }
}
