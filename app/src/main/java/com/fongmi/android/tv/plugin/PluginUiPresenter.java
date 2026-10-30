package com.fongmi.android.tv.plugin;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.theme.ThemeController;

import java.util.ArrayList;
import java.util.List;

/**
 * 插件 UI 槽位渲染器：把清单声明的槽位变成真实控件，并把点击路由回插件。
 *
 * <p><b>两种槽位的落位方式（对应设计文档 §8.2 的裁定）。</b>
 * <ul>
 *   <li>{@code floating}：挂在 decorView 右下角，手机主入口。</li>
 *   <li>{@code toolbar}：追加进播放页控制条的 chip 容器，电视主入口，<b>可聚焦</b>。</li>
 * </ul>
 *
 * <p><b>为什么 toolbar 靠 findViewById + 主题包装而不是改两份布局。</b>
 * {@code R.id.control} 与 {@code R.id.container} 在 mobile 与 leanback 的
 * activity_video.xml 里都存在；{@code @style/Control} 也在 main(:117)/leanback(:80)/
 * mobile(:32) 三处有定义，且三份内容不同（leanback 带 {@code selector_chip} 与
 * {@code nextFocusDown=@id/timeBar}，mobile 用
 * {@code ?attr/selectableItemBackgroundBorderless}）。因此 chip 必须用
 * {@code ContextThemeWrapper(context, R.style.Control)} 套主题，让各 flavor 取自己那份，
 * 而不是在代码里点名某个 drawable——后者在另一侧根本不存在。
 *
 * <p><b>电视端方案的选择依据。</b>屏幕悬浮球不在任何焦点链里，遥控够不到；强行 focusable
 * 又会在原有导航路径上凭空多一个停靠点，破坏 {@code PlayerControlFocusHelper} 的方向推断。
 * 而控制条本身已经是一列 chip（{@code @style/Control} 自带 selector_chip 背景、
 * focusable 与 {@code nextFocusDown=@id/timeBar}），所以"当成又一个 chip"是唯一
 * 零布局改动且不破坏焦点语义的落法。
 *
 * <p><b>toolbar 缺容器时降级为 floating，而不是静默丢弃。</b>作者声明了入口就该看到入口；
 * 当前页面没有控制条（如首页）时，给一个悬浮按钮比什么都不给更有用。降级是显式的，
 * 可通过 {@link #lastFallbacks()} 查证。
 */
public final class PluginUiPresenter {

    /** 挂载点标记前缀，便于卸载时精确移除、不误删他人视图。 */
    static final String TAG_PREFIX = "webhtv-plugin-host:";

    /** 槽位被点击时的回调，由装配层接上 {@link PluginHost#action(String, String, String)}。 */
    public interface OnSlotAction {
        void onSlotAction(String pluginId, String action);
    }

    private final List<View> attached;
    private final List<PluginHost.PluginSlot> fallbacks;
    private OnSlotAction onSlotAction;

    public PluginUiPresenter() {
        this.attached = new ArrayList<>();
        this.fallbacks = new ArrayList<>();
    }

    /** 设置点击回调。必须在 {@link #attach} 之前设置才能对当次挂载生效。 */
    public void setOnSlotAction(@Nullable OnSlotAction listener) {
        this.onSlotAction = listener;
    }

    /**
     * 把插件槽位渲染到当前 Activity。
     *
     * <p>无槽位、无 Activity 时直接返回——渲染是可选增强，任何情况下都不该影响宿主页面。
     *
     * @param slots 待渲染槽位（来自 {@link PluginHost#uiSlots()}，带归属插件 id）
     */
    public void attach(@Nullable Activity activity, @Nullable List<PluginHost.PluginSlot> slots, boolean leanback) {
        detach();
        if (activity == null || slots == null || slots.isEmpty()) return;
        ViewGroup decor = decorOf(activity);
        if (decor == null) return;
        LinearLayout toolbar = toolbarContainer(activity);
        for (PluginHost.PluginSlot entry : slots) {
            if (entry == null || entry.slot() == null) continue;
            String type = entry.slot().slot();
            boolean isToolbar = "toolbar".equalsIgnoreCase(type);
            if (isToolbar && toolbar != null) {
                addToolbarChip(toolbar, entry);
            } else if (isToolbar || "floating".equalsIgnoreCase(type)) {
                // toolbar 在无控制条的页面上降级为 floating；floating 本身照常挂 decorView。
                addFloating(decor, activity, entry, leanback);
                if (isToolbar) fallbacks.add(entry);
            }
        }
    }

    /** 移除本渲染器添加的全部视图并清空降级记录。重复调用安全。 */
    public void detach() {
        for (View view : attached) {
            ViewGroup parent = view.getParent() instanceof ViewGroup group ? group : null;
            if (parent != null) parent.removeView(view);
        }
        attached.clear();
        fallbacks.clear();
    }

    /** 当前已挂载的控件数（floating + toolbar），供诊断与测试。 */
    public int attachedCount() {
        return attached.size();
    }

    /** 上次 attach 中因缺少控制条而降级为 floating 的 toolbar 槽位。 */
    public List<PluginHost.PluginSlot> lastFallbacks() {
        return List.copyOf(fallbacks);
    }

    /**
     * 找到播放页控制条的 chip 容器。
     *
     * <p>路径：{@code R.id.control}（include 的 view_control_vod）→ {@code R.id.action}
     * （include 的 view_control_vod_action 外层）→ {@code R.id.container}（HorizontalScrollView
     * 内的 LinearLayout）。找不到任一层返回 {@code null}，由调用方降级。
     */
    @Nullable
    private static LinearLayout toolbarContainer(Activity activity) {
        View control = activity.findViewById(R.id.control);
        if (!(control instanceof ViewGroup controlGroup)) return null;
        View action = controlGroup.findViewById(R.id.action);
        ViewGroup scope = action instanceof ViewGroup group ? group : controlGroup;
        View container = scope.findViewById(R.id.container);
        return container instanceof LinearLayout linear ? linear : null;
    }

    /** 向控制条追加 chip：沿用 {@code @style/Control} 的焦点语义，排在末尾不改肌肉记忆。 */
    private void addToolbarChip(LinearLayout container, PluginHost.PluginSlot entry) {
        TextView chip = createChip(container.getContext(), entry, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(container.getContext(), 8));
        container.addView(chip, params);
        attached.add(chip);
    }

    /** 挂一个悬浮按钮。电视端不进焦点链（见类注释）。 */
    private void addFloating(ViewGroup decor, Activity activity, PluginHost.PluginSlot entry, boolean leanback) {
        TextView button = createChip(activity, entry, false);
        button.setFocusable(!leanback);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.END | Gravity.BOTTOM;
        params.setMarginEnd(dp(activity, 16));
        params.bottomMargin = dp(activity, 96);
        decor.addView(button, params);
        attached.add(button);
    }

    /**
     * 创建控件。
     *
     * <p>toolbar 与 floating 的背景来源刻意不同：toolbar 用静态资源
     * {@code R.drawable.selector_chip} 以继承既有焦点环（两个 flavor 都有）；floating 走代码
     * {@link #ringBackground}，因为它的描边色来自运行时主题，静态资源拿不到——
     * 这与 {@code docs/THEME-CUSTOMIZATION-LAYERS} 的结论一致。
     */
    private TextView createChip(Context context, PluginHost.PluginSlot entry, boolean toolbar) {
        String label = entry.slot().label();
        if (label == null || label.isEmpty()) label = "插件";
        // toolbar 走 @style/Control 主题包装：三种 flavor 各自持有该样式（main:117 基线、
        // leanback:80 带 selector_chip + nextFocusDown、mobile:32 用
        // ?attr/selectableItemBackgroundBorderless），套主题即可自动取到正确的那份。
        // 早先这里直接写 R.drawable.selector_chip，在 mobile 上编译失败——那个资源只存在于
        // leanback 源集。硬编码资源名等于把「两端同构」这个前提写死成谎言。
        TextView chip = toolbar
                ? new TextView(new ContextThemeWrapper(context, R.style.Control))
                : new TextView(context);
        chip.setText(label);
        chip.setTextSize(12f);
        chip.setTextColor(0xFFFFFFFF);
        chip.setGravity(Gravity.CENTER);
        chip.setTag(tagOf(entry));
        chip.setClickable(true);
        if (toolbar) {
            // Control 已声明 focusable/textColor/background，此处只补间距。
            chip.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        } else {
            chip.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
            chip.setBackground(ringBackground(context));
        }
        chip.setOnClickListener(view -> dispatch(entry));
        return chip;
    }

    /** 点击 → 回调。一次点击失败不该影响宿主页面，因此异常在此吞掉。 */
    private void dispatch(PluginHost.PluginSlot entry) {
        OnSlotAction listener = onSlotAction;
        if (listener == null) return;
        try {
            listener.onSlotAction(entry.pluginId(), entry.slot().action());
        } catch (Throwable ignored) {
        }
    }

    /** 半透明底 + 主题焦点环描边，自动跟随深浅色主题。 */
    private static GradientDrawable ringBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xCC000000);
        drawable.setCornerRadius(dp(context, 20));
        drawable.setStroke(dp(context, 1), ThemeController.focusRingColor(context));
        return drawable;
    }

    /** 挂载根：decorView 本身是 ViewGroup；否则退回内容根。绝不强转父节点。 */
    @Nullable
    private static ViewGroup decorOf(Activity activity) {
        if (activity == null || activity.getWindow() == null) return null;
        View decor = activity.getWindow().getDecorView();
        if (decor instanceof ViewGroup group) return group;
        View content = activity.findViewById(android.R.id.content);
        return content instanceof ViewGroup group ? group : null;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** 给定槽位列表里有多少个需要挂载（floating + toolbar），供调用方预判。 */
    public static int countRenderable(@Nullable List<PluginHost.PluginSlot> slots) {
        if (slots == null) return 0;
        int count = 0;
        for (PluginHost.PluginSlot entry : slots) {
            if (entry == null || entry.slot() == null) continue;
            String type = entry.slot().slot();
            if ("floating".equalsIgnoreCase(type) || "toolbar".equalsIgnoreCase(type)) count++;
        }
        return count;
    }

    /** 控件 tag 形如 {@code webhtv-plugin-host:<pluginId>:<action>}，供测试与诊断定位。 */
    static String tagOf(PluginHost.PluginSlot entry) {
        return TAG_PREFIX + entry.pluginId() + ":" + entry.slot().action();
    }
}
