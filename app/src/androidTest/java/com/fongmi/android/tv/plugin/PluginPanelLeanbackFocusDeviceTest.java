package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.activity.AppBrandingActivity;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Path;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 插件 {@code panel} 槽位在**电视端**的三件事：入口能被遥控器选中、入口进得了宿主控制条、
 * 面板关闭后焦点回到入口。
 *
 * <p><b>为什么单开一个测试类。</b>{@link PluginPanelAndContextDeviceTest} 用
 * {@code PluginController.dispatchSlotAction} 直接注入点击，绕过了 chip 本身——于是「chip 是否可聚焦」
 * 从来没有被任何测试覆盖过。而电视端没有触摸，焦点环就是用户唯一的"我在哪"的表达：
 * 一颗不可聚焦的 chip 等于插件根本不存在，可这条链路在手机端永远是对的，靠手机回归发现不了。
 *
 * <p><b>为什么整个类都 {@code Assume} 掉非电视端。</b>可聚焦性与"进控制条"都是电视专属语义，
 * 手机端刻意保持 floating 的触摸行为（{@code PluginUiPresenter.addPanelEntry} 里
 * {@code if (!leanback) addFloating(..., false)}）。同一份 androidTest 源集会被 mobile 变体编译并执行，
 * 所以这里用 {@code Assume.assumeTrue(Util.isLeanback())} 跳过而不是断言"手机端也一样"。
 */
@RunWith(AndroidJUnit4.class)
public class PluginPanelLeanbackFocusDeviceTest {

    private static final String ID = "panel.focus";
    private static final String PLUGIN_VERSION = "focus-1";
    private static final String ACTION = "probe.focus";
    private static final String FLOAT_ACTION = "probe.float";
    private static final String SLOT_LABEL = "FocusPanel";
    private static final String PANEL_TAG = "plugin-panel";
    private static final long WAIT_MS = 3_000L;

    private static final String MANIFEST = """
            {
              "protocol": "webhtv-plugin",
              "version": 1,
              "id": "%s",
              "name": "Focus Probe",
              "pluginVersion": "%s",
              "sdk": 1,
              "runtime": { "api": "js", "entry": "main.js", "timeout": 15 },
              "events": ["ui.contentShown"],
              "permissions": ["plugin.events", "ui.panel"],
              "ui": [ { "slot": "panel", "label": "%s", "action": "%s" } ]
            }
            """;

    private static final String SCRIPT = """
            webhtv.action('probe.focus', function (payload) {
              return '<html><body><div id="probe-body">FOCUSMARK</div></body></html>';
            });
            """;

    private ActivityScenario<AppBrandingActivity> scenario;
    private AppBrandingActivity activity;
    private PluginUiPresenter presenter;
    private File sourceDir;
    /** 用例 2 临时搭出来的控制条根节点；必须在用例结束前摘掉，否则会污染后续用例的方向键路径。 */
    private View tempControlBar;
    private LinearLayout tempChipContainer;

    @Before
    public void setUp() {
        PluginController.reset();
        PluginSourceConfig.get().remove(ID);
        PluginSourceConfig.get().save();
        deleteQuietly(new File(PluginPackageImporter.pluginsRoot(), ID));
        sourceDir = new File(Path.cache(), "panel-focus-src");
        deleteQuietly(sourceDir);
        assertTrue("cannot create fixture dir", sourceDir.mkdirs());

        scenario = ActivityScenario.launch(AppBrandingActivity.class);
        scenario.onActivity(host -> activity = host);
        assertNotNull("host activity must be launched", activity);
        presenter = new PluginUiPresenter();
    }

    @After
    public void tearDown() {
        dismissPanelQuietly();
        removeTempControlBar();
        // detach() 里是 parent.removeView(...)，必须在建树线程上做。
        if (presenter != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(presenter::detach);
        }
        PluginSourceConfig.get().remove(ID);
        PluginSourceConfig.get().save();
        deleteQuietly(new File(PluginPackageImporter.pluginsRoot(), ID));
        // 卸载同样要回主线程：它在 host.size()==0 时会 presenter.detach()，走的是 removeView。
        reloadOnMain();
        PluginController.reset();
        if (scenario != null) scenario.close();
        deleteQuietly(sourceDir);
    }

    /**
     * 电视端入口必须可聚焦：这是遥控器"能停在它上面"的唯一前提。
     *
     * <p>同时留一条对照断言——{@code floating} 槽位在电视端**仍然**不可聚焦（既有行为，
     * 见 {@code PluginUiPresenterDeviceTest}）。没有这条对照，日后把 {@code addFloating} 的
     * {@code setFocusable(!leanback)} 一并改掉也不会有人发现。
     */
    @Test
    public void panelEntryIsFocusableOnLeanback() {
        Assume.assumeTrue("面板入口的焦点语义只在电视端成立", Util.isLeanback());

        PluginHost.PluginSlot panel = slot("panel", ACTION);
        attachOnMain(List.of(panel), true);
        View chip = findChip(panel);
        assertNotNull("panel entry must be attached to the decor view on leanback", chip);
        assertTrue("panel entry must be focusable on leanback, actual focusable=" + isFocusableOnMain(chip),
                isFocusableOnMain(chip));
        assertTrue("requestFocus must succeed on the panel entry", requestFocusOnMain(chip));
        assertTrue("panel entry must hold focus after requestFocus", isFocusedOnMain(chip));

        PluginHost.PluginSlot floating = slot("floating", FLOAT_ACTION);
        attachOnMain(List.of(floating), true);
        View floatChip = findChip(floating);
        assertNotNull("floating entry must still be attached on leanback", floatChip);
        assertFalse("floating slots must stay non-focusable on leanback (existing behaviour)",
                isFocusableOnMain(floatChip));
    }

    /**
     * 有控制条的页面里，panel 入口要进控制条而不是当浮标。
     *
     * <p>控制条里已有的 {@code @style/Control} 自带焦点环与方向键通路，挂进去即可被遥控到，
     * 且不会在原有导航路径上凭空多一个停靠点——这正是"进控制条"与"自己管焦点环的浮标"的差别，
     * 所以断言落在**父节点身份**上，而不是"它可聚焦"（两者都不可取）。
     *
     * <p>{@link AppBrandingActivity} 本身没有控制条，因此这里按宿主真实的三层 id 结构
     * （{@code R.id.control} → {@code R.id.action} → {@code R.id.container}）临时搭一份。
     */
    @Test
    public void panelSlotGoesIntoControlBarOnLeanback() {
        Assume.assumeTrue("控制条归属只在电视端成立", Util.isLeanback());

        buildTempControlBar();
        assertNotNull("temp control bar must be built", tempChipContainer);

        PluginHost.PluginSlot panel = slot("panel", ACTION);
        attachOnMain(List.of(panel), true);
        View chip = findChip(panel);
        assertNotNull("panel entry must be attached when a control bar exists", chip);
        ViewGroup parent = (ViewGroup) chip.getParent();
        assertTrue("panel entry must live inside R.id.container, actual parentId="
                        + (parent == null ? "null" : String.valueOf(parent.getId())),
                parent == tempChipContainer);
        assertTrue("chip inside the control bar must be focusable", isFocusableOnMain(chip));
    }

    /**
     * 面板关闭后焦点必须回到入口。
     *
     * <p>电视端最关键的一条：面板是独立窗口，关闭时系统把焦点交还给宿主自己挑的落点。
     * 若不还，用户关掉评论面板就"丢了自己刚才站的位置"，只能从头按方向键找回控制条。
     *
     * <p>本用例刻意走 {@code chip.performClick()} 而不是 {@code dispatchSlotAction}：
     * 只有真实点击才会让 {@code PluginUiPresenter.dispatch} 记下 {@code lastClicked}，
     * 而那正是关闭后归还焦点的落点。
     */
    @Test
    public void closingPanelReturnsFocusToEntryOnLeanback() {
        Assume.assumeTrue("焦点归还只在电视端成立", Util.isLeanback());

        install();
        resumeHost();

        PluginHost.PluginSlot panel = slot("panel", ACTION);
        View chip = awaitChip(panel);
        assertNotNull("panel entry must appear after the plugin is loaded", chip);

        performClickOnMain(chip);
        Fragment sheet = awaitPanel();
        assertNotNull("clicking the panel entry must open the panel", sheet);
        // 面板是独立窗口，宿主窗口失去焦点就说明按键已经进了面板。
        // 不断言 chip.isFocused()==false：窗口失去焦点时焦点视图内部的 focused 标志并不被清除，
        // 那条断言会去证明一件不成立的事，只会制造随机红灯。
        // 用轮询而非立即断言：提交对话框事务与窗口真正拿到焦点是两步，中间有极短的空窗。
        assertTrue("host window must lose focus while the panel is up", awaitWindowFocusLost(WAIT_MS));

        dismissPanelOnMain();
        boolean focusedAgain = awaitFocused(chip, WAIT_MS);
        assertTrue("closing the panel must return focus to the entry, actual focused="
                        + isFocusedOnMain(chip) + " windowFocus=" + hasWindowFocusOnMain(), focusedAgain);
    }

    private PluginHost.PluginSlot slot(String type, String action) {
        return new PluginHost.PluginSlot(ID, new PluginManifest.UiSlot(type, SLOT_LABEL, action, null, null));
    }

    private void install() {
        write(new File(sourceDir, "manifest.json"),
                String.format(Locale.US, MANIFEST, ID, PLUGIN_VERSION, SLOT_LABEL, ACTION));
        write(new File(sourceDir, "main.js"), SCRIPT);
        PluginPackageImporter.Result result = PluginPackageImporter.importFrom(sourceDir);
        assertTrue("import failed: " + result.error(), result.isSuccess());
        PluginBootstrap.install();
        assertTrue("reload should load the probe plugin", reloadOnMain());
        PluginHost host = PluginController.get().host();
        assertTrue("plugin must be loaded", host.isLoaded(ID));
        assertNotNull("panel slot must survive manifest parsing", host.slotOfAction(ID, ACTION));
    }

    /**
     * 装载必须回主线程：{@code PluginController.reload → renderNow → presenter.attach} 会往
     * decorView 上 addView，而 View 只能在建树线程上改。测试线程直接调会被 reload 的
     * {@code catch (Throwable)} 吞成"插件已 ready 但 reload 返回空"。
     */
    private static boolean reloadOnMain() {
        AtomicBoolean loaded = new AtomicBoolean(false);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> loaded.set(PluginBootstrap.reloadAll()));
        return loaded.get();
    }

    /** 宿主页面要是控制器眼里的"当前页"，否则 showPanel 会按防串台规则直接放弃。 */
    private void resumeHost() {
        scenario.onActivity(host -> PluginController.safeOnResumed(host));
    }

    private void attachOnMain(List<PluginHost.PluginSlot> slots, boolean leanback) {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> presenter.attach(activity, slots, leanback));
    }

    private View findChip(PluginHost.PluginSlot slot) {
        return poll(() -> activity.getWindow().getDecorView().findViewWithTag(PluginUiPresenter.tagOf(slot)), 1_000L);
    }

    private View awaitChip(PluginHost.PluginSlot slot) {
        return poll(() -> activity.getWindow().getDecorView().findViewWithTag(PluginUiPresenter.tagOf(slot)), WAIT_MS);
    }

    private boolean isFocusableOnMain(View view) {
        return onMain(view::isFocusable, false);
    }

    private boolean isFocusedOnMain(View view) {
        return onMain(view::isFocused, false);
    }

    private boolean requestFocusOnMain(View view) {
        return onMain(view::requestFocus, false);
    }

    private boolean hasWindowFocusOnMain() {
        return onMain(() -> activity == null || activity.hasWindowFocus(), false);
    }

    private void performClickOnMain(View view) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(view::performClick);
    }

    /** 搭一份与宿主同构的控制条：control → action → container，找不到 R.id.container 就不进控制条。 */
    private void buildTempControlBar() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            LinearLayout control = new LinearLayout(activity);
            control.setId(R.id.control);
            control.setOrientation(LinearLayout.VERTICAL);
            FrameLayout actionGroup = new FrameLayout(activity);
            actionGroup.setId(R.id.action);
            LinearLayout container = new LinearLayout(activity);
            container.setId(R.id.container);
            container.setOrientation(LinearLayout.HORIZONTAL);
            actionGroup.addView(container);
            control.addView(actionGroup);
            // getDecorView() 的静态类型是 View，挂子视图前要换成 ViewGroup。
            View decor = activity.getWindow().getDecorView();
            if (decor instanceof ViewGroup group) group.addView(control);
            tempControlBar = control;
            tempChipContainer = container;
        });
    }

    private void removeTempControlBar() {
        View bar = tempControlBar;
        tempControlBar = null;
        tempChipContainer = null;
        if (bar == null) return;
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                if (bar.getParent() instanceof ViewGroup parent) parent.removeView(bar);
            });
        } catch (Throwable ignored) {
        }
    }

    private Fragment awaitPanel() {
        return poll(this::panelNow, WAIT_MS);
    }

    private Fragment panelNow() {
        AppBrandingActivity host = activity;
        if (host == null || host.isFinishing()) return null;
        return host.getSupportFragmentManager().findFragmentByTag(PANEL_TAG);
    }

    private void dismissPanelOnMain() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fragment panel = panelNow();
            if (panel instanceof DialogFragment dialog) dialog.dismissAllowingStateLoss();
        });
    }

    private void dismissPanelQuietly() {
        try {
            dismissPanelOnMain();
        } catch (Throwable ignored) {
        }
    }

    private boolean awaitWindowFocusLost(long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            if (!hasWindowFocusOnMain()) return true;
            sleep();
        }
        return false;
    }

    private boolean awaitFocused(View view, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            if (isFocusedOnMain(view)) return true;
            sleep();
        }
        return false;
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

    private static void write(File file, String text) {
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(file);
            try {
                out.write(text.getBytes(java.nio.charset.Charset.forName("UTF-8")));
            } finally {
                out.close();
            }
        } catch (Exception error) {
            throw new AssertionError("cannot write fixture " + file, error);
        }
    }

    private static void deleteQuietly(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteQuietly(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
