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
 * <p><b>三种槽位的落位方式（对应设计文档 §8.2 的裁定）。</b>
 * <ul>
 *   <li>{@code floating}：挂在 decorView 右下角，手机主入口。</li>
 *   <li>{@code toolbar}：追加进播放页控制条的 chip 容器，电视主入口，<b>可聚焦</b>。</li>
 *   <li>{@code panel}：入口形态随端走——电视端优先进控制条（与 toolbar 同形），
 *       没有控制条时才挂一颗<b>可聚焦</b>浮标；手机端与 floating 完全一致。</li>
 * </ul>
 *
 * <p><b>为什么 panel 在电视端不能沿用 floating 的"不可聚焦"。</b>floating 在 leanback 上
 * 刻意不进焦点链（见下条），因为悬浮球强行 focusable 会破坏方向键推断；但 panel 的整条
 * 交互（点击 → 开面板 → 关闭 → 焦点归还）都建立在"用户能选中它"之上。电视没有触摸屏，
 * 靠焦点环表达"我现在在哪"，不可聚焦等于遥控器根本够不到——那不是一个弱化的入口，
 * 而是一个不存在的入口。所以 panel 在电视端必须先争取进控制条；控制条缺失时才退到一颗
 * 带焦点环切换的浮标，且这是唯一一处我们主动往焦点链里加停靠点的地方。
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
    /** 最近一次被点击的入口控件，供面板关闭后归还焦点。 */
    @Nullable
    private View lastClicked;

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
            boolean isPanel = "panel".equalsIgnoreCase(type);
            if (isToolbar && toolbar != null) {
                addToolbarChip(toolbar, entry);
            } else if (isPanel) {
                // panel 单独一路：它的入口必须"够得到"，而 floating 在电视端刻意不可聚焦。
                addPanelEntry(decor, activity, entry, leanback, toolbar);
            } else if (isToolbar || "floating".equalsIgnoreCase(type)) {
                // toolbar 在无控制条的页面上降级为 floating，floating 本身照常挂 decorView。
                addFloating(decor, activity, entry, leanback);
                // panel 不记进 fallbacks：降级记录专门表示"toolbar 想进控制条却没容器"，
                // panel 进不了控制条是正常的端差异，不是降级。
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
        // 控件已经被摘下来了，再留着引用只会在归还焦点时 requestFocus 到一个孤儿视图上。
        lastClicked = null;
    }

    /**
     * 把焦点还给最近被点击的入口控件。
     *
     * <p>面板是独立窗口，关闭后系统会把焦点交给宿主自己挑的落点；电视端如果不管，
     * 用户的焦点会留在页面顶部或直接消失，得重新按一遍方向键才能回到控制条。
     *
     * <p>返回 {@code false} 而不是抛异常：控件可能已经被 {@link #detach} 摘掉、所在的页面
     * 可能已经不可见、也可能压根没人点过。这些都是正常情况，收尾动作不该制造崩溃。
     */
    public boolean focusLastClicked() {
        View view = lastClicked;
        if (view == null) return false;
        if (view.getParent() == null) return false;
        if (!view.isShown()) return false;
        try {
            return view.requestFocus();
        } catch (Throwable ignored) {
            return false;
        }
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
     * 挂 panel 的入口。三种落位，按"能不能被选中"排序。
     *
     * <p>电视端先争取进控制条：控制条既有的 {@code @style/Control} 已经带焦点环与方向键通路，
     * 放进去即可被遥控到，且不会在原有导航路径上凭空多一个停靠点。只有当前页面没有控制条
     * （首页、设置页、插件管理页等）时，才退到一颗自己管焦点环的浮标——此时页面本来也没有
     * 播放控制，加一个停靠点不会和方向键推断冲突。
     *
     * <p>手机端与 {@link #addFloating} 完全一致：那里本来就靠触摸，不需要焦点语义。
     */
    private void addPanelEntry(ViewGroup decor, Activity activity, PluginHost.PluginSlot entry, boolean leanback,
            @Nullable LinearLayout toolbar) {
        if (leanback && toolbar != null) {
            addToolbarChip(toolbar, entry);
            return;
        }
        if (!leanback) {
            addFloating(decor, activity, entry, false);
            return;
        }
        addFocusableFloating(decor, activity, entry);
    }

    /**
     * 挂一颗**可聚焦**的浮标，给电视端"没有控制条"的页面用。
     *
     * <p>与 {@link #addFloating} 的唯一差别是焦点：这里 {@code setFocusable(true)} 并自己切换
     * 焦点环。电视上焦点环是"我在哪"的唯一表达，普通浮标的半透明底在选中与未选中时一模一样，
     * 用户按方向键选中它之后看不出变化，反而会怀疑遥控器坏了。
     */
    private void addFocusableFloating(ViewGroup decor, Activity activity, PluginHost.PluginSlot entry) {
        TextView button = createChip(activity, entry, false);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setBackground(ringBackground(activity, false));
        button.setOnFocusChangeListener((view, focused) -> view.setBackground(ringBackground(activity, focused)));
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
        chip.setOnClickListener(view -> dispatch(entry, view));
        return chip;
    }

    /**
     * 点击 → 回调。一次点击失败不该影响宿主页面，因此异常在此吞掉。
     *
     * <p>顺手记下被点的控件：面板关闭后要把焦点还给它，否则电视用户关掉面板就"丢了"自己
     * 刚才站的位置，只能从头再按一遍方向键。
     */
    private void dispatch(PluginHost.PluginSlot entry, @Nullable View view) {
        lastClicked = view;
        OnSlotAction listener = onSlotAction;
        if (listener == null) return;
        try {
            listener.onSlotAction(entry.pluginId(), entry.slot().action());
        } catch (Throwable ignored) {
        }
    }

    /** 半透明底 + 主题焦点环描边，自动跟随深浅色主题。 */
    private static GradientDrawable ringBackground(Context context) {
        return ringBackground(context, false);
    }

    /**
     * 同上，但可指定焦点态。
     *
     * <p>未聚焦时与旧实现逐像素相同（{@code 0xCC000000} 底 + 1dp 描边），保证手机端观感不变；
     * 聚焦时加深底色并加粗到 2dp，让电视端"选中了"这件事一眼可见。
     */
    private static GradientDrawable ringBackground(Context context, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(focused ? 0xF0000000 : 0xCC000000);
        drawable.setCornerRadius(dp(context, 20));
        drawable.setStroke(dp(context, focused ? 2 : 1), ThemeController.focusRingColor(context));
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

    /** 给定槽位列表里有多少个需要挂载（floating + toolbar + panel），供调用方预判。 */
    public static int countRenderable(@Nullable List<PluginHost.PluginSlot> slots) {
        if (slots == null) return 0;
        int count = 0;
        for (PluginHost.PluginSlot entry : slots) {
            if (entry == null || entry.slot() == null) continue;
            String type = entry.slot().slot();
            if ("floating".equalsIgnoreCase(type) || "toolbar".equalsIgnoreCase(type) || "panel".equalsIgnoreCase(type)) count++;
        }
        return count;
    }

    /** 控件 tag 形如 {@code webhtv-plugin-host:<pluginId>:<action>}，供测试与诊断定位。 */
    static String tagOf(PluginHost.PluginSlot entry) {
        return TAG_PREFIX + entry.pluginId() + ":" + entry.slot().action();
    }
}
