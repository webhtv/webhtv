package com.fongmi.android.tv.plugin;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Prefers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    /** 拖动落点的记忆键前缀，完整键为 {@code plugin_chip_pos:<pluginId>:<action>}。 */
    private static final String POSITION_PREFIX = "plugin_chip_pos:";

    /** 吸附后与屏幕左右边的间距（dp），与旧版默认落点一致。 */
    private static final int EDGE_INSET_DP = 16;

    /** 纵向钳制时留出的最小上下边距（dp），避免 chip 贴死在状态栏或导航栏上。 */
    private static final int VERTICAL_INSET_DP = 24;

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
     * <p><b>页面过滤在碰视图之前完成。</b>槽位声明的生效页面不含当前页面时直接跳过，而不是
     * 先建出控件再摘掉：先建后摘会闪一次不该出现的入口，还会让"这一页到底挂了什么"变得难查。
     * 缺省（空集）表示全页显示，见 {@link PluginSurface#matches(java.util.Set, String)}。
     *
     * @param slots 待渲染槽位（来自 {@link PluginHost#uiSlots()}，带归属插件 id）
     */
    public void attach(@Nullable Activity activity, @Nullable List<PluginHost.PluginSlot> slots, boolean leanback) {
        detach();
        if (activity == null || slots == null || slots.isEmpty()) return;
        String surface = PluginSurface.of(activity);
        ViewGroup decor = decorOf(activity);
        if (decor == null) return;
        LinearLayout toolbar = toolbarContainer(activity);
        for (PluginHost.PluginSlot entry : slots) {
            if (entry == null || entry.slot() == null) continue;
            // 先过滤再碰视图：这一页不该出现的入口，连控件都不建。
            if (!PluginSurface.matches(entry.slot().surfaces(), surface)) continue;
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

    /**
     * 挂一个悬浮按钮。电视端不进焦点链（见类注释）；手机端可用手指拖动并按插件记忆落点。
     *
     * <p><b>为什么只有这一条路径装触摸监听。</b>控制条里的 chip 由播放页自己管手势与焦点，
     * 电视端的可聚焦浮标靠方向键而非手指——给它们装拖动等于在两套既有交互上叠一层冲突。
     * 悬浮按钮是手机端唯一的自由落点，它是"能拖"这件事的最小且唯一适用面。
     */
    private void addFloating(ViewGroup decor, Activity activity, PluginHost.PluginSlot entry, boolean leanback) {
        TextView button = createChip(activity, entry, false);
        button.setFocusable(!leanback);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.END | Gravity.BOTTOM;
        params.setMarginEnd(dp(activity, 16));
        params.bottomMargin = dp(activity, 96);
        if (!leanback) {
            // 复原必须赶在 addView 之前：落点只能靠 LayoutParams 表达，挂上去再改会先闪一次默认位置。
            restorePosition(activity, decor, entry, button, params);
            DragState drag = new DragState();
            button.setOnTouchListener((view, event) -> onChipTouch(drag, decor, activity, entry, view, params, event));
            // 拖过的手指不该再算一次点击。这里重设监听而不是 setOnClickListener(null)：
            // 置 null 会让 chip 变成不可点击，拖一次之后就再也打不开面板。
            button.setOnClickListener(view -> {
                if (drag.consumeClick()) return;
                dispatch(entry, view);
            });
        }
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
            params.setMarginEnd(dp(context, EDGE_INSET_DP));
            params.topMargin = 0;
            params.bottomMargin = Math.max(0, parentHeight - top - chipHeight);
        } else {
            params.gravity = Gravity.START | Gravity.TOP;
            params.setMarginStart(dp(context, EDGE_INSET_DP));
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
        int inset = dp(activity, VERTICAL_INSET_DP);
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
            return dp(context, 8);
        }
    }

    /** 父容器高度：优先 decorView 实测值，尚未布局时退回屏幕尺寸。 */
    private static int heightOf(@Nullable ViewGroup decor, Context context) {
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

    private static String positionKey(PluginHost.PluginSlot entry) {
        return POSITION_PREFIX + entry.pluginId() + ":" + entry.slot().action();
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
