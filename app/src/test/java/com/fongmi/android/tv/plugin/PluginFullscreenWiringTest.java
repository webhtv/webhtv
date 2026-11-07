package com.fongmi.android.tv.plugin;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 全屏/沉浸态接线的守卫（纯 JVM，读源码文本 + 真实能力门禁调用）。
 *
 * <p><b>为什么钉文本而不是跑行为。</b>三条接线都发生在真实 Activity 的生命周期回调里：
 * PiP 回调要系统 PiP 栈、{@code setFullscreen} 要真实窗口、ROW chip 的焦点态要 Android
 * {@code GradientDrawable} 与资源（{@code app/src/test} 没有 Robolectric）。真机用例只能证明
 * "点了会变"，证明不了"变了会重算"——而这里要守的恰恰是"判据翻转后有没有人重算"。
 *
 * <p>三个缺陷都是静默的：PiP 下入口按旧形态留着、事件报"全屏"而入口可见、电视上选中了看不出来。
 * 用户不会看到崩溃，只会觉得插件坏了。
 */
public class PluginFullscreenWiringTest {

    private static final String VIDEO_ACTIVITY = "VideoActivity.java";
    private static final String DETAIL_ACTIVITY = "TmdbDetailActivity.java";

    private static final String PIP_CALLBACK_HEAD =
            "void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig)";
    private static final String PIP_ASSIGN = "inPip = isInPictureInPictureMode;";
    /** 创建期那处必须问平台，因此带调用的括号，与回调里的形参赋值是两条不同的串。 */
    private static final String PIP_INIT_ASSIGN = "inPip = isInPictureInPictureMode();";

    @Test
    public void videoActivityRecomputesPluginEntrancesOnPipChange() throws Exception {
        String activity = videoActivity();
        String pip = methodBody(activity, PIP_CALLBACK_HEAD);

        assertTrue("VideoActivity 的 PiP 回调必须重算插件入口与事件", pip.contains("notifyPluginFullscreen();"));
        assertTrue("重算必须同时刷新渲染", methodBody(activity, "private void notifyPluginFullscreen()")
                .contains("refreshRender("));
    }

    /**
     * 判据必须读回调告知的状态快照，而不是在读判据时现场问平台。
     *
     * <p><b>这不是时序问题。</b>平台在派发 {@code onPictureInPictureModeChanged} 之前就已写好
     * 画中画状态，回调里问平台也是新值。收益在于"这一次形态变更只有一个快照"：重算入口与广播
     * 事件必须看到同一份形态，现场各问一次就可能落在不同时点（本页 PiP 回调还要先翻转内嵌
     * 布局）。断的是写法而非行为，作 OEM 防御，不要当行为回归锁看。
     */
    @Test
    public void immersiveJudgementReadsTheCallbackStateInsteadOfThePlatformApi() throws Exception {
        String video = videoActivity();
        String videoImmersive = methodBody(video, "public boolean isImmersive()");
        assertTrue("播放页判据必须读 inPip 字段", videoImmersive.contains("!inPip"));
        assertFalse("判据不许在读的时候问平台", videoImmersive.contains("isInPictureInPictureMode()"));
        assertTrue("回调第一句必须用形参刷新该字段",
                afterSuper(methodBody(video, PIP_CALLBACK_HEAD)).startsWith(PIP_ASSIGN));

        String detail = detailActivity();
        String detailImmersive = methodBody(detail, "public boolean isImmersive()");
        assertTrue("详情页判据必须读 inPip 字段", detailImmersive.contains("!inPip"));
        assertFalse("判据不许在读的时候问平台", detailImmersive.contains("isInPictureInPictureMode()"));
        assertTrue("回调第一句必须用形参刷新该字段",
                afterSuper(methodBody(detail, PIP_CALLBACK_HEAD)).startsWith(PIP_ASSIGN));
        // 布局判据是另一件事，不许被顺手删掉。
        assertTrue("内嵌全屏布局判据必须保留", detailImmersive.contains("!inlinePiPLayout"));
    }

    /**
     * 判据的初始值必须取自平台真实状态，而不是字段默认值。
     *
     * <p>进程在画中画里被回收后重建时，系统不会再走一次 {@code onPictureInPictureModeChanged}；
     * 字段停在默认 false，`isImmersive()` 就会把仍在 PiP 的这一页报成沉浸态，入口在错误的形态
     * 下整批隐藏。因此初始化必须落在页面创建期，且与回调里的赋值是两处、不是同一行。
     */
    @Test
    public void pipStateIsInitialisedAtPageCreationNotOnlyInTheCallback() throws Exception {
        for (String source : new String[]{videoActivity(), detailActivity()}) {
            String initView = methodBody(source, "protected void initView(Bundle savedInstanceState)");
            int initAssign = initView.indexOf(PIP_INIT_ASSIGN);
            assertTrue("创建期必须用平台真实状态初始化 inPip", initAssign >= 0);

            // 顺序断言：创建期那处必须排在回调定义之前，光靠"回调里有赋"过不了这一关。
            int initInSource = source.indexOf(PIP_INIT_ASSIGN);
            int callbackDef = source.indexOf(PIP_CALLBACK_HEAD);
            assertTrue("回调定义必须存在", callbackDef >= 0);
            assertTrue("初始化必须排在回调定义之前，说明它属于创建期而不是写在回调里",
                    initInSource >= 0 && initInSource < callbackDef);
            // 两处各一次：创建期取平台状态，回调取形参。只剩一处说明又退化成单点写入。
            assertEquals("创建期初始化只能一处", 1, countOf(source, PIP_INIT_ASSIGN));
            assertEquals("回调首句赋值只能一处", 1, countOf(source, PIP_ASSIGN));
        }
    }

    @Test
    public void detailActivityRecomputesOncePerPipPathAfterTheLayoutFlip() throws Exception {
        String pip = methodBody(detailActivity(), PIP_CALLBACK_HEAD);

        // 三条路径各自带一次重算即可；不锁总数量——将来在回调里加一次合法重算不该让这条误红。
        assertTrue("回调里必须有重算", pip.contains("refreshPluginEntrances();"));

        // 非内嵌形态：没有布局翻转，重算就在自己的 return 之前。
        int nonInline = pip.indexOf("if (!isInlinePlayerMode()) {");
        assertTrue("非内嵌早退分支必须还在", nonInline >= 0);
        int nonInlineReturn = pip.indexOf("return;", nonInline);
        int nonInlineRefresh = pip.indexOf("refreshPluginEntrances();", nonInline);
        assertTrue("非内嵌路径必须重算", nonInlineRefresh > nonInline && nonInlineRefresh < nonInlineReturn);

        // 进 PiP：isImmersive() 读 inlinePiPLayout，必须等它翻转之后再算。
        int enterLayout = pip.indexOf("enterInlinePiPLayout();");
        int enterRefresh = pip.indexOf("refreshPluginEntrances();", enterLayout);
        int enterReturn = pip.indexOf("return;", enterLayout);
        assertTrue("进入 PiP 分支必须重算", enterRefresh > enterLayout);
        assertTrue("进入 PiP 的重算必须在 enterInlinePiPLayout() 之后、分支 return 之前",
                enterRefresh > enterLayout && enterRefresh < enterReturn);

        // 出 PiP：同理，exitInlinePiPLayout() 之后。
        int exitLayout = pip.indexOf("exitInlinePiPLayout();");
        int exitRefresh = pip.indexOf("refreshPluginEntrances();", exitLayout);
        assertTrue("退出 PiP 的重算必须在 exitInlinePiPLayout() 之后", exitRefresh > exitLayout);
    }

    @Test
    public void fullscreenEventReportsImmersiveInsteadOfTheRawField() throws Exception {
        String videoEvent = methodBody(videoActivity(), "private void notifyPluginFullscreen()");
        assertTrue("播放页事件值必须取 isImmersive()", videoEvent.contains("playbackFullscreen(this, isImmersive())"));
        assertFalse("不许再发裸 fullscreen 字段",
                videoEvent.contains("playbackFullscreen(this, fullscreen)")
                        || videoEvent.contains("playbackFullscreen(this, this.fullscreen)"));

        String detailEvent = methodBody(detailActivity(), "private void refreshPluginEntrances()");
        assertTrue("详情页事件值必须取 isImmersive()", detailEvent.contains("playbackFullscreen(this, isImmersive())"));
        assertFalse("不许再发裸 inlineFullscreen 字段",
                detailEvent.contains("playbackFullscreen(this, inlineFullscreen)"));
        // 电视端拦截是既有契约，不许在改口径时顺手删掉。
        assertTrue("详情页事件仍必须在电视端被拦截", detailEvent.contains("if (Util.isLeanback()) return;"));
    }

    @Test
    public void playbackFullscreenStaysUnderTheEventsPermission() {
        // 真实 API 调用而不是文本匹配：这条口径决定"只申请了剧集数据的插件收不到全屏事件"。
        assertTrue(PluginCapabilityRegistry.allowsEvent(
                Set.of(PluginCapabilityRegistry.PERMISSION_EVENTS),
                PluginEventBus.EVENT_PLAYBACK_FULLSCREEN));
        assertFalse(PluginCapabilityRegistry.allowsEvent(
                Set.of(PluginCapabilityRegistry.PERMISSION_EPISODE_DATA),
                PluginEventBus.EVENT_PLAYBACK_FULLSCREEN));
    }

    @Test
    public void rowChipBackgroundFollowsFocus() throws Exception {
        String factory = read("src", "main", "java", "com", "fongmi", "android", "tv", "plugin", "PluginChipFactory.java");

        assertTrue("ROW chip 必须挂焦点态背景切换",
                factory.contains("chip.setOnFocusChangeListener((view, focused) -> view.setBackground(rowBackground(context, focused)))"));
        assertTrue("初始背景必须是未聚焦态", factory.contains("chip.setBackground(rowBackground(context, false))"));
        assertFalse("不许留着不区分焦点态的无参调用", factory.contains("rowBackground(context)"));

        // 这里不调 rowBackground(...) 取实例比 drawable：android.jar 在纯 JVM 单测里是 stub，
        // GradientDrawable 一碰就抛 "Stub!"，因此两条分支的差异只能钉在源码字面上。
        String rowBackground = methodBody(factory,
                "static GradientDrawable rowBackground(Context context, boolean focused)");
        assertTrue("未聚焦底色必须仍是旧实现的 0xB3101820", rowBackground.contains(": 0xB3101820"));
        assertTrue("聚焦态必须换底色，否则电视上依然看不出选中", rowBackground.contains("focused ? 0xE0"));
        assertTrue("未聚焦描边必须仍是 1dp，聚焦才加粗", rowBackground.contains("focused ? 2 : 1"));
        assertFalse("圆角不许跟着焦点变，未聚焦形态必须逐像素等同",
                rowBackground.contains("focused ? 8") || rowBackground.contains("focused ? dp"));
    }

    private static String videoActivity() throws Exception {
        return read("src", "mobile", "java", "com", "fongmi", "android", "tv", "ui", "activity", VIDEO_ACTIVITY);
    }

    private static String detailActivity() throws Exception {
        return read("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "activity", DETAIL_ACTIVITY);
    }

    /** 取 {@code super.xxx(...);} 之后的正文：用来钉"回调第一句就刷新判据"。 */
    private static String afterSuper(String body) {
        int superCall = body.indexOf("super.");
        if (superCall < 0) return body.trim();
        int end = body.indexOf(';', superCall);
        return body.substring(end + 1).trim();
    }

    private static int countOf(String source, String needle) {
        int count = 0;
        for (int i = source.indexOf(needle); i >= 0; i = source.indexOf(needle, i + needle.length())) count++;
        return count;
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing method: " + signature, start >= 0);
        int open = source.indexOf('{', start);
        assertTrue("Missing method body: " + signature, open >= 0);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value == '{') depth++;
            if (value == '}' && --depth == 0) return source.substring(open + 1, i);
        }
        throw new AssertionError("Unclosed method: " + signature);
    }

    private static String read(String... parts) throws Exception {
        Path root = Files.exists(Path.of("src", "main")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(Path.of("", parts)), StandardCharsets.UTF_8);
    }
}
