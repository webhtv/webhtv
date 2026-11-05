package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.ui.activity.AppBrandingActivity;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Prefers;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 两件只有真机才能证的事：页面过滤在 {@code attach} 里真的生效、悬浮 chip 真的能用手指拖动并记住落点。
 *
 * <p><b>为什么单开一个测试类。</b>{@code PluginUiPresenterTest}（纯 JVM）只能锁住
 * {@link PluginSurface#matches} 这条纯函数判定，拿不到真实 Activity，也就证不了
 * "{@code attach} 在碰视图之前就把它筛掉了"；拖动更彻底——它依赖 {@code MotionEvent.getRawX}、
 * 真实 layout 与 {@code SharedPreferences}，JVM 里连事件都造不出来。
 *
 * <p><b>为什么宿主仍然复用 {@link AppBrandingActivity}。</b>与 {@code PluginUiPresenterDeviceTest}
 * 同理：新增 Manifest 条目会扩大测试对生产配置的侵入面，而渲染与拖动对宿主类型没有要求。
 * 它不属于 {@code detail}（{@code AppBrandingActivity} 直接继承 {@code BaseActivity}），
 * 所以正好能当"不该显示 detail 槽位的页面"；本类第 2 条用例会先把这一点断言死。
 */
@RunWith(AndroidJUnit4.class)
public class PluginUiPresenterDragAndSurfaceDeviceTest {

    /** 自己的插件 id：{@code PluginController} 会把用户自己导入的插件的入口也挂到同一个 decorView 上，
     *  用独立 id 才能保证 {@link PluginUiPresenter#tagOf} 只命中本类自己挂的控件。 */
    private static final String PROBE_PLUGIN_ID = "probe.drag";
    private static final String LABEL = "拖动探测";

    private static final String ACTION_EVERY_PAGE = "probe.drag.every";
    private static final String ACTION_DETAIL_ONLY = "probe.drag.detail";
    private static final String ACTION_OTHER_ONLY = "probe.drag.other";
    private static final String ACTION_DRAG = "probe.drag.action";

    /** 与 {@code PluginUiPresenter.POSITION_PREFIX}（私有常量）同值，测试只能照字面量写。 */
    private static final String POSITION_KEY_PREFIX = "plugin_chip_pos:";
    /** 键的实际形状由 {@code PluginUiPresenter.positionKey} 决定：前缀 + pluginId + ":" + action。 */
    private static final String DRAG_KEY = POSITION_KEY_PREFIX + PROBE_PLUGIN_ID + ":" + ACTION_DRAG;
    /** 与 {@code PluginUiPresenter.EDGE_INSET_DP}（私有常量）同值：吸附后贴边的水平留白。 */
    private static final int EDGE_INSET_DP = 16;

    private static final int DRAG_STEPS = 6;
    private static final int DRAG_UP_PX = 220;
    private static final int RESTORE_TOLERANCE_PX = 2;
    /** {@link #currentSurface()} 探测失败时的哨兵，绝不能与 {@code PluginSurface} 的任何真值相同。 */
    private static final String SURFACE_PROBE_FAILED = "probe-failed";
    private static final long LAYOUT_WAIT_MS = 5_000L;
    private static final long FOCUS_WAIT_MS = 5_000L;
    private static final long SETTLE_WAIT_MS = 3_000L;
    private static final long PRESENCE_WAIT_MS = 1_000L;

    private ActivityScenario<AppBrandingActivity> scenario;
    private AppBrandingActivity activity;
    private PluginUiPresenter presenter;

    @Before
    public void setUp() {
        // 上一次运行若在任何一步崩掉，残留的落点会让本次 attach 就把 chip 挪走，
        // 于是"拖动前后位置必须不同"会莫名其妙地红——清掉它比事后解释便宜。
        removeStoredPosition();
        scenario = ActivityScenario.launch(AppBrandingActivity.class);
        scenario.onActivity(host -> activity = host);
        assertNotNull("host activity must be launched", activity);
        presenter = new PluginUiPresenter();
    }

    /**
     * 收尾：摘掉自己挂的控件、删掉落点记忆。
     *
     * <p>{@code detach} 走的是 {@code ViewGroup.removeView}，必须在建树线程上做，
     * 否则 {@code ViewRootImpl.checkThread} 会抛 {@code CalledFromWrongThreadException}。
     */
    @After
    public void tearDown() {
        if (presenter != null) {
            try {
                detachOnMain();
            } catch (Throwable ignored) {
            }
        }
        removeStoredPosition();
        if (scenario != null) scenario.close();
    }

    /**
     * 未声明 {@code surfaces} 的槽位必须在任何页面上都挂出来。
     *
     * <p>钉住的是向后兼容命门：历史清单没有 {@code surfaces} 字段，若把"没声明"当成
     * "哪都不显示"，升级一次宿主就会让所有既有插件集体消失。
     */
    @Test
    public void undeclaredSurfaceSlotIsAttachedOnEveryPage() {
        PluginHost.PluginSlot slot = slot("floating", ACTION_EVERY_PAGE);
        assertTrue("夹具前提：5 参构造出来的槽位 surfaces 必须是空集", slot.slot().surfaces().isEmpty());

        attachOnMain(List.of(slot), false);

        assertNotNull("未声明 surfaces 的槽位必须出现在任意页面上（缺省＝全页显示）", findTagged(slot));
        assertEquals("一个可渲染槽位对应一个控件", 1, attachedCountOnMain());
    }

    /**
     * 只在 {@code detail} 生效的槽位不得出现在当前页面。
     *
     * <p>钉住的是"过滤真的发生在 {@code attach} 里"：控件数量必须是 0，也就是根本没建过控件，
     * 而不是建完再摘掉（后者会让用户看到一次不该出现的入口，也会让排查变成猜）。
     */
    @Test
    public void declaredSurfaceSlotIsSkippedOnTheCurrentPage() {
        assertEquals("夹具前提：AppBrandingActivity 必须被判成 other 页，否则本条断言没有意义",
                PluginSurface.OTHER, currentSurface());

        PluginHost.PluginSlot slot = slot("floating", ACTION_DETAIL_ONLY, Set.of(PluginSurface.DETAIL));
        attachOnMain(List.of(slot), false);

        assertNull("只在 detail 生效的槽位不得出现在 other 页面上", findTagged(slot));
        assertEquals("被过滤的槽位连控件都不该建", 0, attachedCountOnMain());
    }

    /**
     * 声明了当前页面的槽位必须照常挂出来。
     *
     * <p>与上一条合起来才证明过滤是"按声明判"：如果实现写成一律不挂，上一条仍然绿，
     * 而这一条会红——只测"不该出现的没出现"是测不出过滤器坏掉的。
     */
    @Test
    public void declaredSurfaceSlotIsAttachedWhenThePageMatches() {
        assertEquals("夹具前提：AppBrandingActivity 必须被判成 other 页",
                PluginSurface.OTHER, currentSurface());

        PluginHost.PluginSlot slot = slot("floating", ACTION_OTHER_ONLY, Set.of(PluginSurface.OTHER));
        attachOnMain(List.of(slot), false);

        assertNotNull("声明了 other 的槽位必须出现在 other 页面上", findTagged(slot));
        assertEquals("一个可渲染槽位对应一个控件", 1, attachedCountOnMain());
    }

    /**
     * 手指拖动必须真的改落点、松手贴边、并把落点记住供下次 {@code attach} 复原。
     *
     * <p><b>为什么必须真注入触摸。</b>拖动状态机在 {@code PluginUiPresenter.onChipTouch} 里，
     * 判据是 {@code event.getRawX()/getRawY()} 的位移是否超过 touch slop；{@code performClick()}
     * 只能造出点击，造不出"拖完再抬手"这条路径。
     *
     * <p><b>为什么坐标必须用 {@code getLocationOnScreen}。</b>注入的事件坐标是屏幕坐标，
     * 而 {@code getLeft()/getTop()} 是父容器坐标；拿后者当事件坐标喂进去，手指会落在 chip 之外，
     * 表现为"怎么拖都不动"。
     */
    @Test
    public void dragMovesTheChipSnapsToASideAndIsRestoredOnNextAttach() {
        Assume.assumeFalse("拖动只在手机端生效：电视端刻意不装触摸监听", Util.isLeanback());

        PluginHost.PluginSlot slot = slot("floating", ACTION_DRAG);
        attachOnMain(List.of(slot), false);
        View chip = awaitLaidOutChip(slot, LAYOUT_WAIT_MS);
        assertNotNull("拖动用例前提：chip 必须先完成首次布局，否则 getLeft/getTop 恒为 0", chip);
        assertTrue("注入触摸前宿主窗口必须先拿到焦点，否则系统会直接丢弃这些事件",
                awaitWindowFocus(FOCUS_WAIT_MS));

        int[] before = locationOnMain(chip);
        int[] size = sizeOnMain(chip);
        assertNotNull("拖动前必须能读到 chip 的屏幕坐标", before);
        assertNotNull("拖动前必须能读到 chip 的尺寸", size);

        // 把 chip 的中心拖到"距屏幕左边 EDGE_INSET_DP"处：落点中心必然落在屏幕左半边，
        // 松手时 snap 只能选 start 侧，不会"拖了但还贴回原来那一边"。纵向同时上移，
        // 保证位移在两个轴上都远超 touch slop，不会被判成一次点击。
        // 落点按屏幕坐标算：手指出到屏幕外之后，注入的 MOVE 有可能被输入系统直接丢弃，
        // 于是整段手势只剩一个 DOWN，什么都不会发生。
        int densityDpi = Math.round(activity.getResources().getDisplayMetrics().density);
        int targetCenterX = EDGE_INSET_DP * densityDpi + size[0] / 2;
        int dragDx = targetCenterX - (before[0] + size[0] / 2);
        int dragDy = -DRAG_UP_PX;
        dragChip(chip, dragDx, dragDy);

        int[] after = awaitMoved(chip, before, SETTLE_WAIT_MS);
        assertNotNull("松手吸附后 chip 必须换到新落点；before=[" + before[0] + "," + before[1]
                + "]，落点没变说明 chip 没收到这串触摸事件", after);

        String stored = Prefers.getString(DRAG_KEY, "");
        assertTrue("落点必须被写进 Prefers 的 " + DRAG_KEY + "，actual=\"" + stored + "\"",
                stored.matches("(start|end):\\d\\.\\d{4}"));
        assertTrue("拖到屏幕左半边后必须贴左侧，actual=\"" + stored + "\"", stored.startsWith("start:"));

        // 贴边留白只能拿"相对父容器的左偏移"来验：{@code getLeft()} 就是 decor 坐标系里的位置，
        // 等于我们写进 {@code marginStart} 的那份留白；而屏幕坐标还混着窗口 inset，
        // 拿它去比 dp(16) 会在有状态栏/刘海/分屏的设备上假红。
        int expectedLeft = Math.round(EDGE_INSET_DP * activity.getResources().getDisplayMetrics().density);
        int leftInParent = leftOnMain(chip);
        assertTrue("吸附必须贴到左侧留白处：expectedLeft=" + expectedLeft + " actualLeft=" + leftInParent,
                Math.abs(leftInParent - expectedLeft) <= 1);

        // 复原：换一个全新的控件再看落点，才能证明位置来自记忆而不是上一次的 LayoutParams。
        detachOnMain();
        assertNull("detach 之后 chip 必须离开视图树", findTagged(slot));
        attachOnMain(List.of(slot), false);
        View restored = awaitLaidOutChip(slot, LAYOUT_WAIT_MS);
        assertNotNull("再次 attach 必须重新挂出 chip", restored);
        int[] restoredLoc = locationOnMain(restored);
        assertNotNull("复原后必须能读到屏幕坐标", restoredLoc);
        assertTrue("再次 attach 必须复原到拖动后的落点（±" + RESTORE_TOLERANCE_PX + "px 舍入），dragged=["
                        + after[0] + "," + after[1] + "] restored=[" + restoredLoc[0] + "," + restoredLoc[1] + "]",
                Math.abs(restoredLoc[0] - after[0]) <= RESTORE_TOLERANCE_PX
                        && Math.abs(restoredLoc[1] - after[1]) <= RESTORE_TOLERANCE_PX);
    }

    private static PluginHost.PluginSlot slot(String type, String action) {
        return new PluginHost.PluginSlot(PROBE_PLUGIN_ID,
                new PluginManifest.UiSlot(type, LABEL, action, null, null));
    }

    private static PluginHost.PluginSlot slot(String type, String action, Set<String> surfaces) {
        return new PluginHost.PluginSlot(PROBE_PLUGIN_ID,
                new PluginManifest.UiSlot(type, LABEL, action, null, null, surfaces));
    }

    /**
     * 当前页面归属。规则与实现都在 {@code PluginSurface.of(Activity)}。
     *
     * <p>探测失败时返回哨兵而不是 {@code OTHER}：{@code OTHER} 正是我们要断言的值，
     * 拿它当兜底会让"主线程探测没跑起来"伪装成"页面判定正确"。
     */
    private String currentSurface() {
        return onMain(() -> PluginSurface.of(activity), SURFACE_PROBE_FAILED);
    }

    private void attachOnMain(List<PluginHost.PluginSlot> slots, boolean leanback) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> presenter.attach(activity, slots, leanback));
    }

    private void detachOnMain() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(presenter::detach);
    }

    private int attachedCountOnMain() {
        return onMain(presenter::attachedCount, -1);
    }

    /** 按 tag 精确找自己挂的控件；{@code decorView} 上还有用户自己导入的插件的入口，不能按前缀扫。 */
    private View findTagged(PluginHost.PluginSlot slot) {
        return poll(() -> activity.getWindow().getDecorView().findViewWithTag(PluginUiPresenter.tagOf(slot)),
                PRESENCE_WAIT_MS);
    }

    /** 等到 chip 真有尺寸再动手：未布局时 {@code getLeft()/getTop()} 都是 0，拖动会从错误的起点开始。 */
    private View awaitLaidOutChip(PluginHost.PluginSlot slot, long timeoutMs) {
        return poll(() -> {
            View view = activity.getWindow().getDecorView().findViewWithTag(PluginUiPresenter.tagOf(slot));
            if (view == null || !view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
            return view;
        }, timeoutMs);
    }

    private boolean awaitWindowFocus(long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain(() -> activity != null && activity.hasWindowFocus(), false)) return true;
            sleep();
        }
        return false;
    }

    /** 等吸附后的位置真的落到屏幕上（{@code setLayoutParams} 只排一次布局，读太早还是旧值）。 */
    private int[] awaitMoved(View chip, int[] before, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            int[] now = locationOnMain(chip);
            if (now != null && (now[0] != before[0] || now[1] != before[1])) return now;
            sleep();
        }
        return null;
    }

    private int[] locationOnMain(View view) {
        return onMain(() -> {
            int[] point = new int[2];
            view.getLocationOnScreen(point);
            return point;
        }, null);
    }

    private int[] sizeOnMain(View view) {
        return onMain(() -> new int[]{view.getWidth(), view.getHeight()}, null);
    }

    private int leftOnMain(View view) {
        return onMain(view::getLeft, 0);
    }

    /**
     * 注入一串指针事件：DOWN → 若干 MOVE → UP。
     *
     * <p>起点与终点都按 {@code getLocationOnScreen} 换算成屏幕坐标；所有事件共用同一个
     * {@code downTime}，系统才会把它们识别为同一次手势。
     */
    private void dragChip(View chip, int dx, int dy) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        int[] point = locationOnMain(chip);
        int[] size = sizeOnMain(chip);
        assertNotNull("注入前必须能读到 chip 的屏幕坐标", point);
        assertNotNull("注入前必须能读到 chip 的尺寸", size);

        float fromX = point[0] + size[0] / 2f;
        float fromY = point[1] + size[1] / 2f;
        long downTime = SystemClock.uptimeMillis();
        sendPointer(instrumentation, downTime, downTime, MotionEvent.ACTION_DOWN, fromX, fromY);
        for (int step = 1; step <= DRAG_STEPS; step++) {
            // 事件之间留一点时间差：同一毫秒内的一串事件在部分设备上会被合并成一次移动。
            sleep();
            sendPointer(instrumentation, downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE,
                    fromX + dx * step / (float) DRAG_STEPS, fromY + dy * step / (float) DRAG_STEPS);
        }
        sleep();
        sendPointer(instrumentation, downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                fromX + dx, fromY + dy);
    }

    private static void sendPointer(Instrumentation instrumentation, long downTime, long eventTime,
            int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        try {
            instrumentation.sendPointerSync(event);
        } finally {
            event.recycle();
        }
    }

    private static void removeStoredPosition() {
        try {
            Prefers.remove(DRAG_KEY);
        } catch (Throwable ignored) {
        }
    }

    private static <T> T onMain(Callable<T> probe, T fallback) {
        AtomicReference<T> box = new AtomicReference<>();
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                try {
                    box.set(probe.call());
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
            return fallback;
        }
        T value = box.get();
        return value == null ? fallback : value;
    }

    private static <T> T poll(Callable<T> probe, long timeoutMs) {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final AtomicReference<T> box = new AtomicReference<>();
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            box.set(null);
            instrumentation.runOnMainSync(() -> {
                try {
                    box.set(probe.call());
                } catch (Throwable ignored) {
                }
            });
            T value = box.get();
            if (value != null) return value;
            sleep();
        }
        return null;
    }

    private static void sleep() {
        try {
            Thread.sleep(50L);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }
}
