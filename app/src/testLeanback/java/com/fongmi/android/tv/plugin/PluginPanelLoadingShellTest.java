package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.dialog.PluginPanelDialog;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowWebView;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 插件面板「加载壳」的真实行为守卫。
 *
 * <p><b>为什么需要它。</b>这条链路的既有守卫全是源码文本断言（见 {@code PluginPanelPlaybackWiringTest}），
 * 它们能证明某个方法里写了某一行，证明不了面板真的能开、真的能回填、空结果真的能撤壳。而这轮
 * 返工的两个缺陷恰恰都在"多步交互之后才暴露"的路径上：复用已有面板后再来一个空结果、以及
 * Fragment 被系统重建之后。
 *
 * <p><b>两条用例直接对应两个必修缺陷。</b>{@code reusingAPanelWithContentCanStillBeDismissedByAnEmptyResult}
 * 锁 B1（复用后字段还留着旧内容，{@code dismissLoading} 的守卫误判成"用户正在阅读"，加载壳永不撤）；
 * {@code contentAndTitleSurviveFragmentRecreation} 锁 B2（{@code html}/{@code title} 没进 Bundle，
 * 重建后内容面板永久转圈）。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = App.class)
public class PluginPanelLoadingShellTest {

    /** 与生产代码的 {@code PluginPanelDialog.TAG} 一致：测试也按标签取面板，取法要和产品路径相同。 */
    private static final String TAG = "plugin-panel";
    /** 与生产代码的 {@code ARG_HTML} / {@code ARG_HTML_OVERSIZED} 一致（均为私有常量，只能照抄键名）。 */
    private static final String ARG_HTML = "html";
    private static final String ARG_HTML_OVERSIZED = "htmlOversized";
    /** 与生产代码的 {@code MAX_PERSISTED_HTML_CHARS} 一致：两条边界用例要精确压在上限两侧。 */
    private static final int PERSIST_LIMIT = 32_768;
    private static final String HTML = "<html><body><p>豆瓣短评正文</p></body></html>";

    /** 面板宿主。必须走真实主题：面板是 Material 的 SideSheetDialog，主题缺属性会直接崩在构造里。 */
    public static class Host extends AppCompatActivity {
        @Override
        protected void onCreate(Bundle state) {
            setTheme(R.style.Theme_App);
            super.onCreate(state);
        }
    }

    @Test
    public void showLoadingOpensTheShellInLoadingState() {
        FragmentActivity activity = launch();
        String token = PluginPanelDialog.showLoading(activity, "豆瓣", null);
        assertNotNull("加载壳必须能打开", token);
        drain(activity);

        assertNotNull("加载壳必须以 plugin-panel 标签挂上", panel(activity));
        assertEquals("打开加载壳后必须可见地转圈",
                View.VISIBLE, part(activity, R.id.pluginPanelLoading).getVisibility());
        assertTrue("加载中正文必须让位，否则会和转圈一起闪出来",
                part(activity, R.id.pluginPanelWeb).getVisibility() != View.VISIBLE);
    }

    @Test
    public void fillReplacesTheSpinnerWithTheContent() {
        FragmentActivity activity = launch();
        String token = PluginPanelDialog.showLoading(activity, "豆瓣", null);
        drain(activity);

        PluginPanelDialog.fill(activity, token, HTML);

        assertEquals("结果回来必须收起转圈",
                View.GONE, part(activity, R.id.pluginPanelLoading).getVisibility());
        View web = part(activity, R.id.pluginPanelWeb);
        assertEquals("结果回来必须露出正文", View.VISIBLE, web.getVisibility());
        ShadowWebView shadow = Shadow.extract(web);
        assertNotNull("正文必须真的交给 WebView，而不是只存在字段里", shadow.getLastLoadDataWithBaseURL());
        assertEquals("正文必须原样交给 WebView（宿主不做任何加工）",
                HTML, shadow.getLastLoadDataWithBaseURL().data);
    }

    /**
     * B1 的回归锁：复用已有内容的面板后，空结果必须还能把壳撤掉。
     *
     * <p>把复用分支改回"只 fill(title, null)、不清 {@code this.html}"，这条会红：{@code dismissLoading}
     * 会认为面板里是用户正在阅读的内容而放行，面板留在屏幕上永久转圈。
     */
    @Test
    public void reusingAPanelWithContentCanStillBeDismissedByAnEmptyResult() {
        FragmentActivity activity = launch();
        String first = PluginPanelDialog.showLoading(activity, "豆瓣", null);
        drain(activity);
        PluginPanelDialog.fill(activity, first, HTML);

        String second = PluginPanelDialog.showLoading(activity, "豆瓣", null);
        assertNotNull("已有面板时应当复用并返回新 token", second);
        drain(activity);
        assertFalse("复用必须换发新 token，旧结果才会失效", second.equals(first));
        assertEquals("复用必须把面板真的切回加载态",
                View.VISIBLE, part(activity, R.id.pluginPanelLoading).getVisibility());

        PluginPanelDialog.dismissLoading(activity, second);
        drain(activity);

        assertNull("复用后插件返回空必须撤壳：留下转圈的面板等于让用户等一个不会来的结果",
                activity.getSupportFragmentManager().findFragmentByTag(TAG));
    }

    /** 反向守卫：用户正在阅读内容时（没有再发起动作），空结果不得把面板抢走。 */
    @Test
    public void emptyResultNeverClosesAPanelTheUserIsReading() {
        FragmentActivity activity = launch();
        String token = PluginPanelDialog.showLoading(activity, "豆瓣", null);
        drain(activity);
        PluginPanelDialog.fill(activity, token, HTML);

        PluginPanelDialog.dismissLoading(activity, token);
        drain(activity);

        assertNotNull("已有内容的面板不是加载壳，空结果不许撤它",
                activity.getSupportFragmentManager().findFragmentByTag(TAG));
        assertEquals("面板必须保持内容态", View.GONE,
                part(activity, R.id.pluginPanelLoading).getVisibility());
    }

    /**
     * B2 的回归锁：Fragment 被系统重建后，内容与标题都不能丢。
     *
     * <p>用「保存实例状态 → 销毁 → 用同一份 Bundle 起一个新 Activity」模拟进程回收恢复：
     * 这是唯一会真正走无参构造器重建 Fragment 的路径（旋屏不会——宿主页的 {@code configChanges}
     * 已经声明 orientation|screenSize）。
     *
     * <p>把 {@code persistState()} 或 {@code initView} 里回读 {@code ARG_HTML}/{@code ARG_TITLE} 的
     * 那几行改回去，这条会红：重建后的实例以为从未填过正文，面板退回永久转圈。
     */
    @Test
    public void contentAndTitleSurviveFragmentRecreation() {
        ActivityController<Host> before = Robolectric.buildActivity(Host.class).setup();
        FragmentActivity activity = before.get();
        String token = PluginPanelDialog.showLoading(activity, "豆瓣", null);
        drain(activity);
        PluginPanelDialog.fill(activity, token, HTML);

        Bundle state = new Bundle();
        before.saveInstanceState(state).pause().stop().destroy();

        ActivityController<Host> after = Robolectric.buildActivity(Host.class).setup(state);
        try {
            FragmentActivity restored = after.get();
            drain(restored);

            assertNotNull("重建后面板必须还在", panel(restored));
            assertEquals("重建后标题必须仍是原来的标题，而不是退回占位文案",
                    "豆瓣", ((TextView) part(restored, R.id.pluginPanelTitle)).getText().toString());
            assertEquals("重建后不许退回加载态——这正是缺陷 B2 的症状",
                    View.GONE, part(restored, R.id.pluginPanelLoading).getVisibility());
            View web = part(restored, R.id.pluginPanelWeb);
            assertEquals("重建后正文必须仍然可见", View.VISIBLE, web.getVisibility());
            ShadowWebView shadow = Shadow.extract(web);
            assertNotNull("重建后必须把正文重新交给新的 WebView", shadow.getLastLoadDataWithBaseURL());
            assertEquals("重建后正文不许丢", HTML, shadow.getLastLoadDataWithBaseURL().data);
        } finally {
            after.pause().stop().destroy();
        }
    }

    /**
     * B1 的源码级备份锁：切回加载态必须无条件覆盖字段。
     *
     * <p>行为用例已经覆盖同一条，但面板宿主 {@code SideSheetDialog} 在 JVM 下没有真正的窗口
     * （见 {@code PluginPanelAndContextDeviceTest} 的说明），若哪天环境连面板都建不起来，
     * B1 的回归锁不该跟着无声消失。这条只钉修复本身：{@code fill} 里不许再有
     * {@code if (html != null) this.html = html;} 那种"清不掉旧内容"的写法。
     */
    @Test
    public void fillAlwaysOverwritesTheContentField() throws Exception {
        String fill = body(dialogSource(), "private void fill(@Nullable String newTitle, @Nullable String html)");

        assertTrue("切回加载态必须清掉旧 html，否则复用后空结果撤不掉壳", fill.contains("this.html = html;"));
        assertFalse("不许再用条件赋值留住旧内容", fill.contains("if (html != null) this.html = html;"));
        assertTrue("字段变更必须同步进 Bundle，rebuild 才恢复得回来", fill.contains("persistState();"));
    }

    /** B2 的源码级备份锁：内容与标题必须真的写进 arguments，并在 initView 里回读。 */
    @Test
    public void contentAndTitleArePersistedAndReadBack() throws Exception {
        String source = dialogSource();
        String persist = body(source, "private void persistState()");
        String initView = body(source, "protected void initView()");

        assertTrue("超限判定必须按字符数与常量比较", persist.contains("html.length() > MAX_PERSISTED_HTML_CHARS"));
        assertTrue("正文与超限标记必须互斥：写正文时不写标记",
                persist.contains("args.putString(ARG_HTML, oversized ? null : html);"));
        assertTrue("写标记时一定不写正文", persist.contains("args.putBoolean(ARG_HTML_OVERSIZED, oversized);"));
        assertTrue(persist.contains("args.putString(ARG_TITLE, title);"));
        assertTrue("重建后回读内容", initView.contains("args.getString(ARG_HTML)"));
        assertTrue("重建后回读标题", initView.contains("title = args.getString(ARG_TITLE);"));
        assertTrue("超限实例必须主动关掉面板", initView.contains("beginOversizedShellClose(binding.getRoot())"));
    }

    /**
     * 正文超限的行为锁：内容照常显示，但不许把整段正文塞进 arguments。
     *
     * <p>回退 `persistState` 的 `oversized ? null : html` 那一行（改回无条件 `putString(ARG_HTML, html)`）
     * 这条会红：`getArguments().getString("html")` 变回整段超长正文，即宿主退到后台时
     * {@code TransactionTooLargeException} 的来源。
     */
    @Test
    public void oversizedContentDisplaysButIsNotPersisted() {
        FragmentActivity activity = launch();
        String oversized = htmlOf(PERSIST_LIMIT + 1);
        String token = PluginPanelDialog.showLoading(activity, "豆瓣", null);
        drain(activity);

        PluginPanelDialog.fill(activity, token, oversized);

        assertEquals("超限正文照样要显示：上限只约束持久化，不约束渲染",
                View.GONE, part(activity, R.id.pluginPanelLoading).getVisibility());
        View web = part(activity, R.id.pluginPanelWeb);
        assertEquals("超限正文必须原样交给 WebView", View.VISIBLE, web.getVisibility());
        ShadowWebView shadow = Shadow.extract(web);
        assertEquals("超限正文必须原样交给 WebView", oversized, shadow.getLastLoadDataWithBaseURL().data);

        Bundle args = panel(activity).getArguments();
        assertNotNull("面板必须有 arguments", args);
        assertNull("超限正文不许进 arguments：它要随 onSaveInstanceState 走 Binder 事务",
                args.getString(ARG_HTML));
        assertTrue("必须留下超限标记，重建实例才知道内容不可恢复", args.getBoolean(ARG_HTML_OVERSIZED));
    }

    /**
     * 正文超限的重建锁：重建后面板必须已被关闭，而不是留一个永远转圈的壳。
     *
     * <p>回退 `initView` 末尾的 `beginOversizedShellClose(binding.getRoot())`（或去掉幂等标记让投递
     * 不再落地）会红：面板还在，且因为 Bundle 里没有正文而永久转圈。
     */
    @Test
    public void oversizedContentClosesThePanelAfterRecreation() {
        ActivityController<Host> before = Robolectric.buildActivity(Host.class).setup();
        FragmentActivity activity = before.get();
        String token = PluginPanelDialog.showLoading(activity, "豆瓣", null);
        drain(activity);
        PluginPanelDialog.fill(activity, token, htmlOf(PERSIST_LIMIT + 1));

        Bundle state = new Bundle();
        before.saveInstanceState(state).pause().stop().destroy();

        ActivityController<Host> after = Robolectric.buildActivity(Host.class).setup(state);
        try {
            FragmentActivity restored = after.get();
            settle(restored);

            assertNull("内容过大无法忠实恢复时必须关掉面板；留着就是一个永远转圈的空壳",
                    restored.getSupportFragmentManager().findFragmentByTag(TAG));
        } finally {
            after.pause().stop().destroy();
        }
    }

    /**
     * 上限的边界：**恰好等于**上限的正文仍然要被持久化并恢复。
     *
     * <p>这条防的是把上限实现成"一律不持久化"（例如误写成 `>=` 或干脆不写）。回退
     * `persistState` 的比较方向（`>` 改 `>=`）或去掉写正文的分支，这条会红：重建后面板消失、
     * `getArguments().getString("html")` 为 null。
     */
    @Test
    public void contentExactlyAtTheLimitSurvivesRecreation() {
        String atLimit = htmlOf(PERSIST_LIMIT);
        ActivityController<Host> before = Robolectric.buildActivity(Host.class).setup();
        FragmentActivity activity = before.get();
        String token = PluginPanelDialog.showLoading(activity, "豆瓣", null);
        drain(activity);
        PluginPanelDialog.fill(activity, token, atLimit);

        Bundle args = panel(activity).getArguments();
        assertNotNull("面板必须有 arguments", args);
        assertEquals("恰好等于上限的正文必须照旧持久化", atLimit, args.getString(ARG_HTML));
        assertFalse("等于上限不是超限", args.getBoolean(ARG_HTML_OVERSIZED));

        Bundle state = new Bundle();
        before.saveInstanceState(state).pause().stop().destroy();

        ActivityController<Host> after = Robolectric.buildActivity(Host.class).setup(state);
        try {
            FragmentActivity restored = after.get();
            settle(restored);

            assertNotNull("等于上限的正文必须能恢复，面板不许被关掉", panel(restored));
            assertEquals("恢复后不许退回加载态",
                    View.GONE, part(restored, R.id.pluginPanelLoading).getVisibility());
            View web = part(restored, R.id.pluginPanelWeb);
            ShadowWebView shadow = Shadow.extract(web);
            assertEquals("恢复后正文必须原样交回 WebView", atLimit, shadow.getLastLoadDataWithBaseURL().data);
        } finally {
            after.pause().stop().destroy();
        }
    }

    /** 造一段长度恰为 {@code length} 个字符的正文，用来精确压在上限的两侧。 */
    private static String htmlOf(int length) {
        StringBuilder builder = new StringBuilder(length);
        while (builder.length() < length) builder.append('评');
        return builder.toString();
    }

    private static String dialogSource() throws Exception {
        Path root = Files.exists(Path.of("src", "main")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(Path.of("", "src", "main", "java", "com", "fongmi", "android",
                "tv", "ui", "dialog", "PluginPanelDialog.java")), StandardCharsets.UTF_8);
    }

    private static String body(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("缺少方法：" + signature, start >= 0);
        int open = source.indexOf('{', start);
        assertTrue("缺少方法体：" + signature, open >= 0);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value == '{') depth++;
            if (value == '}' && --depth == 0) return source.substring(open + 1, i);
        }
        throw new AssertionError("方法体未闭合：" + signature);
    }

    private Host launch() {
        return Robolectric.buildActivity(Host.class).setup().get();
    }

    private static void drain(FragmentActivity activity) {
        activity.getSupportFragmentManager().executePendingTransactions();
    }

    /**
     * 让投递到主线程的关闭动作真正跑起来。
     *
     * <p>超限面板的关闭是 {@code binding.getRoot().post(...)} 投递的，停下 looper 或只排空
     * FragmentManager 事务队列都不够：得先让 looper 执行那个 Runnable，再把它新提交的事务排空。
     * 多来几轮是因为 {@code dismissAllowingStateLoss} 自身也可能再投递一次。
     */
    private static void settle(FragmentActivity activity) {
        for (int i = 0; i < 3; i++) {
            ShadowLooper.idleMainLooper();
            drain(activity);
        }
    }

    private static Fragment panel(FragmentActivity activity) {
        return activity.getSupportFragmentManager().findFragmentByTag(TAG);
    }

    private static View part(FragmentActivity activity, int id) {
        Fragment fragment = panel(activity);
        assertNotNull("面板必须存在", fragment);
        View root = fragment.getView();
        assertNotNull("面板视图必须已经建立", root);
        return root.findViewById(id);
    }
}
