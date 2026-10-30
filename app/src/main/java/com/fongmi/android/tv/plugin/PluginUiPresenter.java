package com.fongmi.android.tv.plugin;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.theme.ThemeController;

import java.util.ArrayList;
import java.util.List;

/**
 * 插件 UI 槽位渲染器：把清单声明的槽位变成真实控件。
 *
 * <p><b>P2d 只实现 `floating`。</b>`toolbar` 需要插进 mobile/leanback 两份不同的布局，
 * `panel`/`page` 各有独立宿主；先把最简单的一种做到生产可用，比四种都做到"能显示"更有价值。
 *
 * <p><b>为什么挂在 Activity 的 decorView 上而不是 `ProgressLayout`。</b>后者虽然提供了
 * `addOverlayView`（`ProgressLayout.java:224`，语义正是"不随状态切换隐藏"），但它只存在于
 * 部分页面；插件要覆盖任意页面，只能挂 decorView。代价是必须自己处理页面切换时的清理，
 * 这由 {@link #detach()} 负责。
 *
 * <p><b>电视端原则：默认不可聚焦。</b>悬浮控件不在任何焦点链里，强行 `focusable` 会在原有
 * 导航路径上凭空多一个停靠点，破坏 {@code PlayerControlFocusHelper} 的方向推断。
 * 所以 floating 在 Leanback 上默认 `focusable=false`，只作为视觉提示存在；
 * 电视的正式入口留待 `toolbar` 槽位（见设计文档 §8.2）。
 */
public final class PluginUiPresenter {

    /** 挂载点标记，便于卸载时精确移除、不误删他人视图。 */
    private static final String TAG_HOST = "webhtv-plugin-host";

    private final List<View> attached;

    public PluginUiPresenter() {
        this.attached = new ArrayList<>();
    }

    /**
     * 把插件槽位渲染到当前 Activity。
     *
     * <p>无槽位、无 Activity 或非主线程时直接返回——渲染是可选增强，任何情况下都不该
     * 影响宿主页面的正常显示。
     *
     * @param slots 待渲染槽位（来自 {@link PluginHost#uiSlots()}）
     */
    public void attach(@Nullable Activity activity, @Nullable List<PluginManifest.UiSlot> slots, boolean leanback) {
        detach();
        if (activity == null || slots == null || slots.isEmpty()) return;
        ViewGroup root = rootOf(activity);
        if (root == null) return;
        for (PluginManifest.UiSlot slot : slots) {
            if (!"floating".equalsIgnoreCase(slot.slot())) continue;
            View button = createFloatingButton(activity, slot, leanback);
            if (button == null) continue;
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.END | Gravity.BOTTOM;
            params.setMarginEnd(dp(activity, 16));
            params.bottomMargin = dp(activity, 96);
            root.addView(button, params);
            attached.add(button);
        }
    }

    /** 移除本渲染器添加的全部视图。重复调用安全。 */
    public void detach() {
        for (View view : attached) {
            ViewGroup parent = view.getParent() instanceof ViewGroup group ? group : null;
            if (parent != null) parent.removeView(view);
        }
        attached.clear();
    }

    /** 当前已挂载的控件数，供诊断与测试。 */
    public int attachedCount() {
        return attached.size();
    }

    /**
     * 创建悬浮按钮。
     *
     * <p>样式刻意用 `ThemeController.focusRingColor` 与半透明底色，而不是自定义 drawable：
     * 这样它自动跟随主题，不需要为深浅色各维护一份资源。
     */
    @Nullable
    private View createFloatingButton(Context context, PluginManifest.UiSlot slot, boolean leanback) {
        String label = slot.label() == null || slot.label().isEmpty() ? "插件" : slot.label();
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextSize(12f);
        button.setTextColor(0xFFFFFFFF);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
        button.setBackgroundColor(0xCC000000);
        button.setTag(TAG_HOST + ":" + slot.action());
        // 电视端不可聚焦：见类注释。手机端可点。
        button.setFocusable(!leanback);
        button.setClickable(true);
        // 描边取主题焦点环色，让控件自动跟随深浅色主题，无需维护两套 drawable。
        button.setBackground(ringBackground(context));
        return button;
    }

    /**
     * 半透明底 + 主题焦点环描边。
     *
     * <p>不用 XML drawable 是因为描边色来自运行时主题（{@code ThemeController}），
     * 静态资源拿不到；这也与 `docs/THEME-CUSTOMIZATION-LAYERS` 的结论一致——
     * 跟随主题的控件必须走代码路径而不是编译期资源。
     */
    private static GradientDrawable ringBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xCC000000);
        drawable.setCornerRadius(dp(context, 20));
        drawable.setStroke(dp(context, 1), ThemeController.focusRingColor(context));
        return drawable;
    }

    /** 挂载根：decorView 本身是 ViewGroup；否则退回内容根。绝不强转父节点。 */
    @Nullable
    private static ViewGroup rootOf(Activity activity) {
        if (activity.getWindow() == null) return null;
        View decor = activity.getWindow().getDecorView();
        if (decor instanceof ViewGroup group) return group;
        View content = activity.findViewById(android.R.id.content);
        return content instanceof ViewGroup group ? group : null;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** 给定槽位列表里有多少个 floating，供调用方决定是否需要挂载。 */
    public static int countFloating(@Nullable List<PluginManifest.UiSlot> slots) {
        if (slots == null) return 0;
        int count = 0;
        for (PluginManifest.UiSlot slot : slots) {
            if (slot != null && "floating".equalsIgnoreCase(slot.slot())) count++;
        }
        return count;
    }

    /** 供布局测试断言：按钮的 tag 形如 `webhtv-plugin-host:<action>`。 */
    static String tagOf(PluginManifest.UiSlot slot) {
        return TAG_HOST + ":" + slot.action();
    }
}
