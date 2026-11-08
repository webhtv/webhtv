package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 钉住「切换线路不自动播放」开关的接线不变式：存储键、抑制点、程序化例外、设置页与布局。
 *
 * <p>只钉不变式，不钉具体实现形态：抑制必须发生在 {@code onRefresh()} 的拆流与取流之前
 * （旧流继续播，直到用户点具体集数；同时仍在途的旧取流结果必须就地作废），
 * 且程序化切线路（历史恢复、自动换源、TMDB 匹配）必须继续自动起播。
 */
public class FlagSwitchPlaybackWiringTest {

    private static final String MOBILE = "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java";
    private static final String LEANBACK = "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java";

    @Test
    public void settingStoresTheToggleUnderItsOwnKey() throws Exception {
        String source = read(sourcePath("main", "java").resolve(Path.of("com", "fongmi", "android", "tv", "setting", "PlayerSetting.java")));

        assertTrue("开关必须独立存储，默认关闭以保持升级后的既有行为",
                source.contains("Prefers.getBoolean(\"player_defer_flag_switch\", false)"));
        assertTrue("开关必须可写", source.contains("Prefers.put(\"player_defer_flag_switch\", defer)"));
        assertTrue("必须暴露读取访问器", source.contains("public static boolean isDeferFlagSwitchPlayback()"));
        assertTrue("必须暴露写入访问器", source.contains("public static void putDeferFlagSwitchPlayback(boolean defer)"));
    }

    @Test
    public void refreshKeepsTheCurrentStreamOnlyWhileTheFlagSwitchIsArmed() throws Exception {
        for (String path : new String[]{MOBILE, LEANBACK}) {
            String source = read(path);
            String body = methodBody(source, "private void onRefresh()");
            int guard = body.indexOf("if (mDeferFlagSwitchPlayback)");
            int fetch = body.indexOf("getPlayer(getFlag(), getEpisode());");
            int release = body.indexOf("mDeferFlagSwitchPlayback = false;", guard);
            int branchEnd = body.indexOf("return;", guard);
            String guardBranch = branchEnd > guard ? body.substring(guard, branchEnd) : "";

            assertTrue(path + " 的 onRefresh 必须在拆流前检查抑制标记", guard >= 0);
            assertTrue(path + " 的取流调用必须仍然存在", fetch >= 0);
            assertTrue(path + " 的抑制检查必须早于取流调用", guard < fetch);
            assertTrue(path + " 的抑制必须早于 stop()，否则当前正在播的视频会被停掉",
                    guard < body.indexOf("player().stop();"));
            assertTrue(path + " 的抑制必须早于 clear()，否则当前正在播的视频会被清掉",
                    guard < body.indexOf("player().clear();"));
            assertTrue(path + " 的抑制分支必须在早退前作废仍在途的旧取流请求，否则旧线路稍后会自动起播",
                    guardBranch.contains("cancelPlayerContent()"));
            assertTrue(path + " 的抑制分支必须在早退前复位取流上下文，否则旧结果会被当成当前请求",
                    guardBranch.contains("invalidatePlayerContent()"));
            assertTrue(path + " 的抑制标记必须在 onRefresh 内消费即复位，否则后续播放键恢复会被永久吞掉",
                    release > guard && release < fetch);
        }
    }

    @Test
    public void manualLineSwitchArmsTheDeferralAndKeepsThePinnedRefreshCall() throws Exception {
        for (String path : new String[]{MOBILE, LEANBACK}) {
            String source = read(path);
            String body = methodBody(source, "public void onItemClick(Flag item)");

            assertTrue(path + " 的切线路必须咨询共享策略",
                    body.contains("FlagSwitchPlaybackPolicy.shouldDeferPlayback(PlayerSetting.isDeferFlagSwitchPlayback(), mProgrammaticFlagSwitch)"));
            assertTrue(path + " 的切线路必须在刷新前武装抑制标记",
                    body.indexOf("mDeferFlagSwitchPlayback =") < body.indexOf("boolean episodeChanged = seamless(resolved);"));
            assertTrue(path + " 必须在方法末尾复位抑制标记，避免泄漏到后续播放操作",
                    body.indexOf("mDeferFlagSwitchPlayback = false;") > body.indexOf("loadTmdbRelatedVideosForCurrentEpisode();"));
            assertTrue(path + " 必须保留既有的初始绑定刷新调用",
                    body.contains("if (initialBinding && !episodeChanged) onRefresh();"));
        }
    }

    @Test
    public void programmaticLineSwitchStillAutoplays() throws Exception {
        for (String path : new String[]{MOBILE, LEANBACK}) {
            String source = read(path);

            assertProgrammatic(source, path, "private void checkFlag(Vod item)", "onItemClick(resolveHistoryPlaybackFlag(item.getFlags()));");
            assertProgrammatic(source, path, "private void resumeHistoryAfterTmdbMatch()", "onItemClick(targetFlag);");
            assertProgrammatic(source, path, "private void nextFlag(int position)", "onItemClick(flag);");
        }
    }

    @Test
    public void settingPagesAndLayoutsExposeTheToggle() throws Exception {
        String mobilePage = read(sourcePath("mobile", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "fragment", "SettingPlayerFragment.java")));
        String leanbackPage = read(sourcePath("leanback", "java").resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "SettingPlayerActivity.java")));
        for (String source : new String[]{mobilePage, leanbackPage}) {
            assertTrue("设置页必须显示开关当前值", source.contains("mBinding.deferFlagSwitchText.setText(getSwitch(PlayerSetting.isDeferFlagSwitchPlayback()));"));
            assertTrue("设置页必须绑定开关点击", source.contains("mBinding.deferFlagSwitch.setOnClickListener(this::setDeferFlagSwitch);"));
            assertTrue("设置页必须持久化开关", source.contains("PlayerSetting.putDeferFlagSwitchPlayback(!PlayerSetting.isDeferFlagSwitchPlayback());"));
        }

        String mobileLayout = read(sourcePath("mobile", "res").resolve(Path.of("layout", "fragment_setting_player.xml")));
        String leanbackLayout = read(sourcePath("leanback", "res").resolve(Path.of("layout", "activity_setting_player.xml")));
        for (String layout : new String[]{mobileLayout, leanbackLayout}) {
            assertTrue("布局必须提供开关行", layout.contains("@+id/deferFlagSwitch"));
            assertTrue("布局必须提供开关取值文本", layout.contains("@+id/deferFlagSwitchText"));
            assertTrue("布局必须引用开关文案", layout.contains("@string/player_defer_flag_switch"));
        }
    }

    @Test
    public void everyLocaleCarriesTheToggleLabel() throws Exception {
        for (String locale : new String[]{"values", "values-zh-rCN", "values-zh-rTW"}) {
            String strings = read(sourcePath("main", "res").resolve(Path.of(locale, "strings.xml")));
            assertTrue(locale + " 缺少开关文案", strings.contains("<string name=\"player_defer_flag_switch\">"));
        }
    }

    private static void assertProgrammatic(String source, String path, String method, String click) {
        String body = methodBody(source, method);
        int call = body.indexOf(click);
        assertTrue(path + " 的 " + method + " 必须仍调用 " + click, call >= 0);
        int arm = body.lastIndexOf("mProgrammaticFlagSwitch = true;", call);
        int reset = body.indexOf("mProgrammaticFlagSwitch = false;", call);
        assertTrue(path + " 的 " + method + " 必须在调用前置位程序化标记", arm >= 0);
        assertTrue(path + " 的 " + method + " 必须在调用后复位程序化标记", reset > call);
    }


    @Test
    public void bothPlayersIdentifyTheCurrentStreamByTheAppliedFlagKey() throws Exception {
        for (String path : new String[]{MOBILE, LEANBACK}) {
            String source = read(path);
            assertTrue(path + " 必须记住实际已应用的线路稳定键",
                    source.contains("private String mPlaybackFlagKey = \"\";")
                            && source.contains("private String playbackFlagKey(Flag flag) {")
                            && source.contains("private boolean isPlaybackFlag(Flag flag) {")
                            && source.contains("private boolean isPlayingContent(Episode episode) {"));
            String invalidate = methodBody(source, "private void invalidatePlayerContent()");
            assertTrue(path + " 作废在途取流时必须同时作废已应用线路键",
                    invalidate.contains("mPlaybackFlagKey = \"\";"));
            String getPlayer = methodBody(source, "private void getPlayer(Flag flag, Episode episode)");
            int begin = getPlayer.indexOf("beginPlayerContentRequest(getKey(), playFlag, episode.getUrl());");
            int remember = getPlayer.indexOf("mPlaybackFlagKey = playbackFlagKey(flag);");
            assertTrue(path + " 起播时必须记录线路键", begin >= 0 && remember > begin);
            assertTrue(path + " 只按标记不能认定该集正在播放",
                    methodBody(source, "private boolean shouldEnterFullscreen(Episode item)")
                            .contains("isPlayingContent(item)"));
            assertTrue(path + " 平滑换线路必须确认当前流确实属于该线路",
                    methodBody(source, "private boolean seamless(Flag flag)")
                            .contains("(isPlaybackFlag(flag) && episode.isSelected())"));
            assertTrue(path + " 线路点击早退必须以当前线路身份为前提",
                    methodBody(source, "public void onItemClick(Flag item)").contains("isPlaybackFlag(resolved)"));
        }
    }

    /**
     * 线路切换自身会重设选中位并刷新整行，Leanback 因此会把「同一条线路」再派发一次；
     * 若这次回声回到 onItemClick，就会形成 200ms 级自激切换循环（实测日志 934 次
     * line-switch 中 918 次 from==to，且循环窗口内没有任何用户输入事件）。
     *
     * <p>行为层（{@code FlagSelectionListenerTest}）测得到「回声被拦」与「换线路/切回仍派发」，
     * 但测不到「窗口在刷新途中不被重开」——该差别只在 {@code run()} 自身派发路径上才可观测。
     * 因此这里补最小结构不变式，每条断言都先要求 token 存在（{@code indexOf} 的 −1 不得
     * 被当成「顺序成立」），再要求相对顺序，避免「漏写关键字反而变绿」。
     */
    @Test
    public void leanbackFlagRowSuppressesItsOwnSelectionEcho() throws Exception {
        String source = read(LEANBACK_LISTENER);
        String run = methodBody(source, "public void run()");
        String selected = methodBody(source, "public void onChildViewHolderSelected(");
        assertTrue("必须保留选中回调", !selected.isEmpty());

        int stale = run.indexOf("adapter.get(position) == item)");
        int guard = run.indexOf("if (item == dispatchedItem) return;");
        int remember = run.indexOf("dispatchedItem = item;");
        int dispatch = run.indexOf("listener.onItemClick(item);");
        assertTrue("回声拦截必须发生在 stale 校验之后、派发之前",
                stale >= 0 && guard >= 0 && dispatch >= 0 && stale < guard && guard < dispatch);
        assertTrue("拦截后必须记住本次派发，否则回声仍会重复进入",
                remember >= 0 && remember > guard);

        // 窗口条件：只有「真的选中了另一条线路」才结束窗口；NO_POSITION（item == null）属于
        // 刷新中间态，不得清窗口，否则回声会在下一次同项选中时重新接上。
        assertTrue("改选另一条线路必须重新打开回声窗口，否则用户再也切不回原线路",
                selected.contains("if (item != null && item != dispatchedItem) dispatchedItem = null;"));

        // 取消旧回调若被顺手删掉，功能测试不会红（pendingItem 仍会被覆盖），故在此单独钉住。
        int cancel = selected.indexOf("view.removeCallbacks(this);");
        int pending = selected.indexOf("pendingItem = item;");
        int post = selected.indexOf("view.post(this)");
        assertTrue("必须保留取消旧回调，且发生在记录新 pendingItem 之前",
                cancel >= 0 && pending >= 0 && cancel < pending);
        assertTrue("必须仍然申请派发那一帧", post >= 0 && post > pending);

        // 点击线路走 FlagAdapter 的 OnClickListener，不经过本监听器；回声去重一旦下沉进
        // onItemClick，点击也会被吞掉。此处钉住「守卫只活在选中监听器里」这条结构边界。
        String flagClick = methodBody(read(LEANBACK), "public void onItemClick(Flag item)");
        assertTrue("回声去重不得下沉进 onItemClick，否则点击线路会被静默吞掉",
                !flagClick.contains("dispatchedItem"));
    }

    @Test
    public void detailInlinePlaybackAlsoComparesTheAppliedFlagKey() throws Exception {
        String source = read(DETAIL);
        assertTrue("详情页必须记住内联播放的线路稳定键",
                source.contains("private String inlinePlaybackFlagKey = \"\";"));
        String start = methodBody(source, "private void startInlinePlayer(Result result, long resumePosition)");
        assertTrue("内联起播必须记录线路稳定键",
                start.contains("inlinePlaybackFlagKey = selectedFlag == null ? \"\" : selectedSeasonFlagKey();"));
        String stop = methodBody(source, "private void stopInlinePlayerForReload()");
        assertTrue("内联停止必须清空线路稳定键", stop.contains("inlinePlaybackFlagKey = \"\";"));
        assertTrue("同名线路必须靠稳定键区分是否仍是当前播放",
                source.contains("&& TextUtils.equals(selectedSeasonFlagKey(), inlinePlaybackFlagKey)"));
        assertTrue("所有内联停止点都必须清空线路稳定键", occurrences(source, "inlinePlaybackFlagKey = \"\";") >= 2);
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static final String DETAIL = "app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java";
    private static final String LEANBACK_LISTENER = "app/src/leanback/java/com/fongmi/android/tv/ui/custom/FlagSelectionListener.java";

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("missing method " + signature, start >= 0);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("unbalanced method " + signature);
    }

    private static Path sourcePath(String sourceSet, String kind) {
        return root().resolve(Path.of("app", "src", sourceSet, kind));
    }

    /** Gradle 单测的工作目录可能是仓库根或 app/，两种都要能解析。 */
    private static Path root() {
        return Files.exists(Path.of("app", "src", "main")) ? Path.of(".") : Path.of("..");
    }

    private static String read(String path) throws Exception {
        return read(root().resolve(path));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
