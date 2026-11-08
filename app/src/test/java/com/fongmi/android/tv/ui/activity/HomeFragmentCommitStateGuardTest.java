package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 首页（TV/leanback）在宿主已保存状态的窗口里不能再提交 Fragment 事务。
 *
 * <p>真机崩溃证据（TCL tcl_m7642 / Android 9，dev3 基线）：
 *
 * <pre>
 * 08:19:15.710 [main] startup: home showContent start cost=231455ms
 * 08:19:15.718 [main] av-diag: process.recovery … message="Can not perform this action after onSaveInstanceState"
 * 08:19:15.737 [main] process-crash: java.lang.IllegalStateException: Can not perform this action
 *   after onSaveInstanceState
 *     at androidx.fragment.app.a.g            (FragmentTransaction.commit)
 *     at HomeActivity.clearCategoryContent
 *     at HomeActivity.syncCategorySite
 *     at HomeActivity.getVideo
 *     at HomeActivity.showContent
 *     at HomeActivity$4.success             (VodConfig 加载回调)
 *     at gd.run → Handler.handleCallback → Looper.loop
 * </pre>
 *
 * <p>配置加载回调来自进程级单例 {@code VodConfig}，经 {@code App.post} 投递，与 Activity
 * 生命周期解耦；而外观页改主题色/主题模式/UI 缩放都会 {@code recreate()}。两者叠加时，
 * 这条延迟回调落在旧实例的已保存状态上，无条件 {@code commit()} 便抛异常。
 *
 * <p>守卫拒绝的是「提交」，不是「工作」：实例仍存活（只是被保存了状态，例如用户在配置
 * 加载期间切到别的应用）时，分类页清理与首页内容刷新都要推迟到回到前台补做，否则会留下
 * 旧站点分类页与一个永远不再加载的空首页。
 */
public class HomeFragmentCommitStateGuardTest {

    private static final String HOME =
            "app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java";

    @Test
    public void guardHelperRejectsFinishingDestroyedAndSavedState() throws Exception {
        String home = read(HOME);
        String helper = between(home,
                "private boolean canCommitFragmentTransaction() {",
                "private void clearCategoryContent()");

        assertTrue("a saved-state guard must exist for delayed Fragment work", helper.contains("isFinishing()"));
        assertTrue("a destroyed host must not commit Fragment work", helper.contains("isDestroyed()"));
        assertTrue("a saved FragmentManager must reject the transaction",
                helper.contains("getSupportFragmentManager().isStateSaved()"));
        // 推迟只对「仍存活」的宿主成立；正在结束/已销毁的实例必须丢弃，不能再挂起工作。
        assertTrue("deferral must exist as a separate predicate", helper.contains("private boolean canDeferFragmentWork()"));
        String defer = between(home,
                "private boolean canDeferFragmentWork() {",
                "private void clearCategoryContent()");
        assertTrue("deferral must reject a finishing host", defer.contains("!isFinishing()"));
        assertTrue("deferral must reject a destroyed host", defer.contains("!isDestroyed()"));
    }

    @Test
    public void categoryStateIsClearedEvenWhenCommitIsSkipped() throws Exception {
        String home = read(HOME);
        String clear = between(home,
                "private void clearCategoryContent() {",
                "private void removeCategoryFragments() {");

        int guard = clear.indexOf("if (!canCommitFragmentTransaction()) {");
        int remove = clear.indexOf("removeCategoryFragments();");
        assertTrue("clearing categories must consult the saved-state guard", guard >= 0);
        assertTrue("the guard must be the statement that leads into the transaction", remove > guard);
        assertTrue("a live host must defer the skipped removal instead of dropping it",
                clear.contains("mPendingCategoryCleanup = true;"));
        assertFalse("the guard must not fall back to state-loss commits",
                clear.contains("commitAllowingStateLoss"));

        // 纯内存状态必须在守卫“之前”就已经丢弃：否则 isCurrentCategory() 会把一个即将
        // 消失的 Fragment 当成当前分类，重建窗口里到处误判。
        for (String reset : new String[]{
                "mCurrentType = null;",
                "mFolder = null;",
                "mBinding.categoryContainer.setVisibility(View.GONE);"}) {
            int at = clear.indexOf(reset);
            assertTrue("category reset must not be skipped with the transaction: " + reset, at >= 0);
            assertTrue("category reset must happen before the commit guard: " + reset, at < guard);
        }
    }

    @Test
    public void savedCategoryPageNeverCommitsWithoutTheGuard() throws Exception {
        String home = read(HOME);
        String clear = between(home,
                "private void clearCategoryContent() {",
                "private void removeCategoryFragments() {");
        String remove = between(home,
                "private void removeCategoryFragments() {",
                "private void clearStaleSiteTypes()");

        assertEquals("clearCategoryContent must delegate the removal instead of committing itself",
                0, countOccurrences(clear, "transaction.commit();"));
        assertEquals("the removal must hold exactly one commit", 1, countOccurrences(remove, "transaction.commit();"));
        assertTrue("removing cached category pages must stay generation-safe",
                remove.contains("folder.clearContentFocusRequest();"));
        assertTrue("the removal must sweep every added page, not only hidden ones",
                remove.contains("getSupportFragmentManager().getFragments()"));
    }

    @Test
    public void homeContentRefreshStopsBeforeReshapingASavedStateHost() throws Exception {
        String home = read(HOME);
        String getVideo = between(home,
                "private void getVideo(boolean forceNative) {",
                "private void showWebOverlay()");

        int sync = getVideo.indexOf("syncCategorySite();");
        int guard = getVideo.indexOf("if (!canCommitFragmentTransaction()) {");
        int webLoad = getVideo.indexOf("WebHomeTarget.canLoad(getHome())");

        assertTrue("the site key must still be committed before the guard", sync >= 0);
        assertTrue("a delayed refresh must stop when the host state is already saved", guard > sync);
        assertTrue("the guarded refresh must not start web or native content work", webLoad > guard);
        assertTrue("a live host must not lose the refresh", getVideo.contains("mPendingContentRefresh = true;"));
        assertTrue("a web-triggered refresh must keep its forceNative intent",
                getVideo.contains("mPendingContentRefreshForceNative |= forceNative;"));
    }

    @Test
    public void deferredWorkIsResumedWhenTheHostReturnsToForeground() throws Exception {
        String home = read(HOME);
        String onResume = between(home, "protected void onResume() {", "protected void onPause() {");
        String resume = between(home,
                "private void resumeDeferredFragmentWork() {",
                "protected void onPause() {");

        assertTrue("deferred work must run when the host returns to the foreground",
                onResume.contains("resumeDeferredFragmentWork();"));
        assertTrue("deferred work must still respect the commit guard",
                resume.contains("if (!canCommitFragmentTransaction()) return;"));
        for (String deferred : new String[]{
                "mPendingCategoryCleanup = false;",
                "removeCategoryFragments();",
                "mPendingContentRefresh = false;",
                "getVideo(forceNative);"}) {
            assertTrue("deferred work must be replayed once: " + deferred, resume.contains(deferred));
        }
    }

    @Test
    public void hidingTheHomeChromeNeverCommitsWithoutTheGuard() throws Exception {
        String home = read(HOME);
        String showHome = between(home,
                "private void showHomeContent() {",
                "private void showCategoryContent(Class item) {");

        int commit = showHome.indexOf("beginTransaction().hide(mFolder).commit()");
        int guard = showHome.indexOf("if (canCommitFragmentTransaction())");

        assertTrue("hiding the home chrome commit must remain present", commit >= 0);
        assertTrue("hiding the home chrome must be guarded by the saved-state check", guard >= 0);
        assertTrue("the guard must wrap the hide transaction", guard < commit);
        assertTrue("local visibility state must still be cleared outside the transaction",
                showHome.contains("mFolder.setUserVisibleHint(false);"));
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + needle.length())) count++;
        return count;
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertTrue("Missing method: " + start, from >= 0);
        int to = source.indexOf(end, from + start.length());
        assertTrue("Missing method boundary: " + end, to > from);
        return source.substring(from, to);
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path) && relative.startsWith("app/")) path = Path.of(relative.substring(4));
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
