package com.fongmi.android.tv.plugin;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.R;

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
 * <p><b>为什么 toolbar 靠 findViewById + defStyleRes 而不是改两份布局。</b>
 * {@code R.id.control} 与 {@code R.id.container} 在 mobile 与 leanback 的
 * activity_video.xml 里都存在；{@code @style/Control} 也在 main(:117)/leanback(:80)/
 * mobile(:32) 三处有定义，且三份内容不同（leanback 带 {@code selector_chip} 与
 * {@code nextFocusDown=@id/timeBar}，mobile 用
 * {@code ?attr/selectableItemBackgroundBorderless}）。因此 chip 用四参构造
 * {@code new TextView(context, null, 0, R.style.Control)} 把该样式当 defStyleRes 套上去，
 * 让各 flavor 取自己那份，而不是在代码里点名某个 drawable——后者在另一侧根本不存在。
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
 *
 * <p><b>为什么悬浮球那一整套（拖动状态机、落点记忆、吸附几何）不在这里。</b>本类回答的是
 * "哪个槽位挂到哪"，那是一张类型到落位方式的表；拖动回答的是"挂上去之后手指怎么移动它"，
 * 那是一套父容器尺寸与边距的几何换算。两条推理链没有共享前提，混在一个类里只会让想找槽位
 * 判定的人先翻过几百行边距代码——所以后者整体在 {@link PluginFloatingDock}，chip 的构造与
 * 配色在 {@link PluginChipFactory}。
 */
public final class PluginUiPresenter {

    /** 挂载点标记前缀，便于卸载时精确移除、不误删他人视图。 */
    static final String TAG_PREFIX = "webhtv-plugin-host:";

    /** 槽位的落位方式。 */
    private enum Landing {
        /** 不挂控件：{@code page} 是整页接管，未知类型、空槽位同理。 */
        NONE,
        TOOLBAR,
        PANEL,
        FLOATING
    }

    /** 槽位被点击时的回调，由装配层接上 {@link PluginHost#action(String, String, String)}。 */
    public interface OnSlotAction {
        void onSlotAction(String pluginId, String action);
    }

    private final List<View> attached;
    private final List<PluginHost.PluginSlot> fallbacks;
    /** 被我们改过 {@code nextFocusDown} 的既有控件及其原值，{@link #detach} 时原样还回去。 */
    private final List<FocusLink> focusLinks;
    private OnSlotAction onSlotAction;
    /** 最近一次被点击的入口控件，供面板关闭后归还焦点。 */
    @Nullable
    private View lastClicked;

    public PluginUiPresenter() {
        this.attached = new ArrayList<>();
        this.fallbacks = new ArrayList<>();
        this.focusLinks = new ArrayList<>();
    }

    /** 设置点击回调。必须在 {@link #attach} 之前设置才能对当次挂载生效。 */
    public void setOnSlotAction(@Nullable OnSlotAction listener) {
        this.onSlotAction = listener;
    }

    /**
     * 槽位类型 → 落位方式。
     *
     * <p>判定只此一处：{@link #attach} 按它决定挂到哪里，{@link #countRenderable} 按它决定
     * 算不算一个入口。这两处从前各写一份字符串比较，多一种槽位类型就要记得改两遍，而漏改的
     * 症状是"数得出 4 个、屏幕上只有 3 个"这种只在特定清单下才显形的偏差。
     */
    private static Landing landingOf(@Nullable PluginHost.PluginSlot entry) {
        if (entry == null || entry.slot() == null) return Landing.NONE;
        String type = entry.slot().slot();
        if ("toolbar".equalsIgnoreCase(type)) return Landing.TOOLBAR;
        if ("panel".equalsIgnoreCase(type)) return Landing.PANEL;
        if ("floating".equalsIgnoreCase(type)) return Landing.FLOATING;
        return Landing.NONE;
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
        ViewGroup decor = PluginFloatingDock.decorOf(activity);
        if (decor == null) return;
        LinearLayout toolbar = toolbarContainer(activity);
        for (PluginHost.PluginSlot entry : slots) {
            Landing landing = landingOf(entry);
            if (landing == Landing.NONE) continue;
            // 先过滤再碰视图：这一页不该出现的入口，连控件都不建。
            if (!PluginSurface.matches(entry.slot().surfaces(), surface)) continue;
            switch (landing) {
                case TOOLBAR -> {
                    if (toolbar != null) {
                        addToolbarChip(toolbar, entry);
                    } else {
                        // toolbar 在无控制条的页面上降级为 floating，并把这件事记下来。
                        PluginFloatingDock.addFloating(this, decor, activity, entry, leanback);
                        fallbacks.add(entry);
                    }
                }
                // panel 单独一路：它的入口必须"够得到"，而 floating 在电视端刻意不可聚焦。
                // 它也**不**记进 fallbacks——降级记录专门表示"toolbar 想进控制条却没容器"，
                // panel 进不了控制条是正常的端差异，不是降级，混进去会让诊断数据失真。
                case PANEL -> addPanelEntry(decor, activity, entry, leanback, toolbar);
                case FLOATING -> PluginFloatingDock.addFloating(this, decor, activity, entry, leanback);
                default -> {
                    // NONE 已被上面跳过，这里只是让 switch 穷尽。
                }
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
        // 撤掉焦点链：控制条里那些控件是宿主自己的，改过的 nextFocusDown 必须还原。
        // 不还原就会留下一条指向已移除 chip 的断头链——那之后从它往下走会退回几何推断，
        // 而它原本明确指向时间条，等于插件退出后把宿主的焦点路径悄悄改掉了。
        // 必须倒序还原：同一个控件被登记多次时，后一份快照记的是「指向某个 chip 的 id」，
        // 只有从最后一份开始倒着还原，最早那份真实原值才会最后写回、成为最终值。
        for (int i = focusLinks.size() - 1; i >= 0; i--) focusLinks.get(i).restore();
        focusLinks.clear();
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

    /**
     * 向控制条追加 chip：沿用 {@code @style/Control} 的焦点语义，排在末尾不改肌肉记忆。
     *
     * <p><b>为什么必须补 nextFocus 链。</b>{@code @style/Control} 只声明了
     * {@code nextFocusDown=@id/timeBar}，没有任何一条通往"追加在末尾的那个 chip"的通路：不补的话
     * 遥控器在控制条上走不到它——视觉上它在末尾，实际上够不到，等于挂了个不存在的入口
     * （docs §7:307 与 §8.3:365 把工具栏 chip 的 nextFocusUp/Down 链列为验收项）。
     *
     * <p><b>为什么只动"旧末尾 ⇄ 新 chip"这一环。</b>其余跳转全由布局文件自己声明；从中间插入
     * 新跳转会让既有方向键路径凭空改道。只在末尾续一环，原有路径到旧末尾为止完全不变。
     *
     * <p><b>为什么两端都现发 id。</b>nextFocus 是 id 引用，而动态 view 默认没有 id。
     * {@code View.generateViewId()} 给的是一次性的正整数 id，只参与这几条跳转，不注册进
     * {@code R}，也就不会和任何布局里的 {@code @+id} 撞名。
     *
     * <p><b>为什么容器里没有可聚焦子视图时一个 nextFocus 都不设。</b>没有"上一个 chip"可指，
     * 设了 nextFocusUp 只会让方向键从新 chip 跳到屏幕任意位置；此时保持 {@code Control} 自带
     * 的默认行为，退化成与改动前一致，比造一条断头链更安全。
     */
    private void addToolbarChip(LinearLayout container, PluginHost.PluginSlot entry) {
        TextView chip = PluginChipFactory.create(this, container.getContext(), entry, true);
        // 先取旧末尾再 addView：挂上去之后"最后一个子视图"就变成新 chip 自己了。
        View previous = lastFocusableChild(container);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(PluginChipFactory.dp(container.getContext(), 8));
        container.addView(chip, params);
        if (previous != null) {
            // 没有"上一个 chip"时连 id 都不发：这颗 chip 不参与任何跳转，不该凭空多出一个
            // 可被布局保存/恢复机制认领的 id。
            chip.setId(View.generateViewId());
            if (previous.getId() == View.NO_ID) previous.setId(View.generateViewId());
            chip.setNextFocusUpId(previous.getId());
            // 旧末尾原本靠 Control 的 nextFocusDown 直接跳去时间条；不覆盖它，从新 chip 再往后
            // 就断了，"末尾续了一环"只做了一半。原值记下来，detach 时还原。
            focusLinks.add(new FocusLink(previous, previous.getNextFocusDownId()));
            previous.setNextFocusDownId(chip.getId());
        }
        attached.add(chip);
    }

    /**
     * 容器里最后一个可聚焦、且未被隐藏的子视图——也就是追加之前的"最后一个 chip"。
     *
     * <p>跳过 {@code GONE} 是必要的：控制条里大量 chip（episodes/discMenu/actionQuality…）
     * 默认不显示，把 nextFocus 指向它们，系统会再按方向猜一次落点，结果不可预期。
     *
     * <p>找不到（容器空、或全是隐藏/不可聚焦）时返回 {@code null}，调用方据此完全不碰 nextFocus。
     */
    @Nullable
    private static View lastFocusableChild(LinearLayout container) {
        for (int i = container.getChildCount() - 1; i >= 0; i--) {
            View child = container.getChildAt(i);
            if (child == null) continue;
            if (child.getVisibility() == View.GONE) continue;
            if (child.isFocusable()) return child;
        }
        return null;
    }

    /**
     * 挂 panel 的入口。三种落位，按"能不能被选中"排序。
     *
     * <p>电视端先争取进控制条：控制条既有的 {@code @style/Control} 已经带焦点环与方向键通路，
     * 放进去即可被遥控到，且不会在原有导航路径上凭空多一个停靠点。只有当前页面没有控制条
     * （首页、设置页、插件管理页等）时，才退到一颗自己管焦点环的浮标——此时页面本来也没有
     * 播放控制，加一个停靠点不会和方向键推断冲突。
     *
     * <p>手机端与 {@link PluginFloatingDock#addFloating} 完全一致：那里本来就靠触摸，不需要焦点语义。
     */
    private void addPanelEntry(ViewGroup decor, Activity activity, PluginHost.PluginSlot entry, boolean leanback,
            @Nullable LinearLayout toolbar) {
        if (leanback && toolbar != null) {
            addToolbarChip(toolbar, entry);
            return;
        }
        if (!leanback) {
            PluginFloatingDock.addFloating(this, decor, activity, entry, false);
            return;
        }
        PluginFloatingDock.addFocusableFloating(this, decor, activity, entry);
    }

    /**
     * 点击 → 回调。一次点击失败不该影响宿主页面，因此异常在此吞掉。
     *
     * <p>顺手记下被点的控件：面板关闭后要把焦点还给它，否则电视用户关掉面板就"丢了"自己
     * 刚才站的位置，只能从头再按一遍方向键。
     *
     * <p>包可见而非私有：{@link PluginFloatingDock} 挂出来的悬浮球与浮标也要走同一条
     * "记录 + 派发"路径，不能让它们各写一份。
     */
    void dispatch(PluginHost.PluginSlot entry, @Nullable View view) {
        lastClicked = view;
        OnSlotAction listener = onSlotAction;
        if (listener == null) return;
        try {
            listener.onSlotAction(entry.pluginId(), entry.slot().action());
        } catch (Throwable ignored) {
        }
    }

    /** 登记一个由本渲染器挂上去的控件，供 {@link #detach} 精确移除。 */
    void register(View view) {
        attached.add(view);
    }

    /**
     * 一条被我们改过、需要还原的 nextFocus 边。
     *
     * <p>只存"谁的哪条边、原值是什么"：还原时把原值写回去就够了，不涉及控件本身。
     * 每个新 chip 至多产生一条，控件被 {@link #detach} 摘掉时一并撤销。
     */
    private static final class FocusLink {
        private final View target;
        private final int originalDown;

        FocusLink(View target, int originalDown) {
            this.target = target;
            this.originalDown = originalDown;
        }

        /** 原值可能是 {@code View.NO_ID}（布局里没声明），写回去即恢复"交还给系统推断"。 */
        void restore() {
            try {
                target.setNextFocusDownId(originalDown);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 给定槽位列表里有多少个需要挂载（floating + toolbar + panel），供调用方预判。 */
    public static int countRenderable(@Nullable List<PluginHost.PluginSlot> slots) {
        if (slots == null) return 0;
        int count = 0;
        for (PluginHost.PluginSlot entry : slots) {
            if (landingOf(entry) != Landing.NONE) count++;
        }
        return count;
    }

    /** 控件 tag 形如 {@code webhtv-plugin-host:<pluginId>:<action>}，供测试与诊断定位。 */
    static String tagOf(PluginHost.PluginSlot entry) {
        return TAG_PREFIX + identityOf(entry);
    }

    /**
     * 槽位的稳定标识 {@code <pluginId>:<action>}。
     *
     * <p>控件 tag 与 {@link PluginFloatingDock} 的落点记忆键都长成"前缀 + 这一串"，从前各拼
     * 一份，差别只在前缀常量。两处一旦漂移，tag 找得到控件、落点却记到另一个键上——这种错只在
     * "拖过并且换页面回来"时才显形，最难查。标识部分因此只留这一处定义。
     */
    static String identityOf(PluginHost.PluginSlot entry) {
        return entry.pluginId() + ":" + entry.slot().action();
    }
}
