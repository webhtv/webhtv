package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 播放会话的归属令牌必须在会话内保持稳定。
 *
 * <p>getPlaybackKey() 由子类从 intent 现算，而 intent 的 id 会在起播之后被详情结果改写
 * （TMDB 富集回来的 vodId，见 VideoActivity#updateVod）。播放器里的 key 是起播那一刻固化进
 * PlaySpec 的，之后无从更改。两者一旦不等，isOwner() 便永久为 false，同时打断三条链：
 * STATE_READY 不再下发（转圈不收）、每秒进度采样直接返回（进度不落库、刷新回起点）、
 * 切集因 PlaybackService#isNavigationOwner 失配而不派发。
 */
public class PlaybackOwnershipSourceTest {

    private static final String PLAYBACK = "app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java";

    @Test
    public void ownershipReadsThePinnedSessionKeyNotTheLiveIntent() throws Exception {
        String source = read(PLAYBACK);

        int owner = source.indexOf("protected boolean isOwner()");
        int ownerEnd = source.indexOf("\n    }", owner);
        String body = owner < 0 ? "" : source.substring(owner, ownerEnd);

        assertTrue("isOwner must exist", owner > 0);
        assertTrue("ownership must read the pinned session key", body.contains("activePlaybackKey()"));
        assertTrue("ownership must compare sessions, not raw strings",
                body.contains("PlaybackOwnershipKeys.sameSession("));
        assertFalse("ownership must not recompute the key from the live intent",
                body.contains("getPlaybackKey()"));
    }

    @Test
    public void theSessionKeyIsPinnedWhenPlaybackActuallyStarts() throws Exception {
        String source = read(PLAYBACK);

        assertTrue("the pinned key must fall back to the intent before the first start",
                source.contains("return pinnedPlaybackKey != null ? pinnedPlaybackKey : getPlaybackKey();"));
        // 两条起播分支（需解析 / 直连）都要钉住，漏一条那条链路照旧丢归属；
        // 重建恢复也写同一字段，但不是 startPlayer 内的赋值，所以要限定方法体统计
        int startPlayer = source.indexOf("protected void startPlayer(String key, Result result, boolean useParse, long timeout,");
        int startPlayerEnd = source.indexOf("private String lifecycleState()", startPlayer);
        assertTrue("startPlayer must exist", startPlayer > 0 && startPlayerEnd > startPlayer);
        assertEquals("both start branches must pin the session key from startPlayer",
                2, countOccurrences(source.substring(startPlayer, startPlayerEnd), "pinnedPlaybackKey = key;"));

        int route = source.indexOf("NovelRouter.isReaderUrl(result)");
        int firstPin = source.indexOf("pinnedPlaybackKey = key;", route);
        int parse = source.indexOf("player().parse(", route);
        int start = source.indexOf("player().start(", route);

        assertTrue("the reader route must return before anything is pinned", firstPin > route);
        assertTrue("pinning must happen before the player is handed the spec",
                firstPin < parse && firstPin < start);
    }

    @Test
    public void routingAndReadyReplayFollowTheSameSessionKey() throws Exception {
        String source = read(PLAYBACK);

        // 导航 key 与归属 key 必须同源：PlaybackService#isNavigationOwner 拿它跟
        // player.getKey() 比，用现算值会让切集在 id 漂移后静默失效
        assertFalse("navigation must not be registered with a recomputed key",
                source.contains("setNavigationCallback(getNavigationCallback(), getPlaybackKey())"));
        assertEquals("every navigation registration must use the session key",
                3, countOccurrences(source, "setNavigationCallback(getNavigationCallback(), activePlaybackKey())"));
        assertTrue("the READY replay reconciliation must compare the session key",
                source.contains("PlaybackStateReconciliation.shouldReplayReady(activePlaybackKey()"));
    }

    @Test
    public void switchingEntryPointsKeepTheSessionKeyInsteadOfTheHistoryKey() throws Exception {
        // 切内核/切解码会重建 PlaySpec；传 getHistoryKey() 会把播放器的 key 换成
        // 当前 intent 算出来的新值，等于在会话中途把归属令牌搬走
        for (String path : new String[] {
                "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java",
                "app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java"
        }) {
            String source = read(path);
            assertFalse(path + " must not re-key the player with a freshly computed history key",
                    source.contains("switchPlayer(type, result, getHistoryKey()")
                            || source.contains("switchPlayer(playerType, result, getHistoryKey()")
                            || source.contains("switchDecode(result, getHistoryKey()"));
        }
        assertTrue("mobile decode switching must reuse the session key",
                read("app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java")
                        .contains("switchDecode(result, activePlaybackKey()"));
    }

    @Test
    public void itemSwitchesInvalidateInFlightPlayerRequests() throws Exception {
        String viewModel = read("app/src/main/java/com/fongmi/android/tv/model/SiteViewModel.java");

        assertTrue("SiteViewModel must expose a player-content cancellation hook",
                viewModel.contains("public void cancelPlayerContent()"));
        assertTrue("cancellation must invalidate the old task id",
                viewModel.indexOf("taskIds.get(TaskType.PLAYER).incrementAndGet();") >= 0);
        assertTrue("cancellation must clear stale LiveData",
                viewModel.indexOf("player.setValue(null);") >= 0);
        for (String path : new String[] {
                "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java"
        }) {
            String source = read(path);
            int newIntent = source.indexOf("protected void onNewIntent(Intent intent)");
            int end = source.indexOf("protected void initView", newIntent);
            int cancel = source.indexOf("cancelPlayerContent();", newIntent);
            assertTrue(path + " must cancel player content on item switch",
                    newIntent >= 0 && cancel > newIntent && (end < 0 || cancel < end));
        }
    }

    @Test
    public void tmdbDetailRefreshesNavigationAfterResettingOldItemOwnership() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java");

        int newIntent = source.indexOf("protected void onNewIntent(Intent intent)");
        int reset = source.indexOf("resetPlaybackOwnership();", newIntent);
        int navigation = source.indexOf("updateNavigationKey();", newIntent);

        assertTrue("TMDB detail must reset stale ownership on item switch", newIntent >= 0 && reset > newIntent);
        assertTrue("TMDB detail must refresh the service navigation key after that reset", navigation > reset);
    }

    @Test
    public void kernelRebuildBindsTheNewEngineToThePlayerViewByInstanceIdentity() throws Exception {
        // 自动下一集先 clear() 把 Manager.spec 置空，再 preparePlayer() 重建引擎；此时
        // isOwner() 为 false，onPlayerRebuild() 里的 setRender() 整段被跳过，render 值不变。
        // 只按“PlayerView.player 是否为空”判断，就会把已释放的旧引擎留在 View 上，
        // 新引擎从未拿到 Surface —— 画面全黑、声音正常，手动切内核/软硬解才恢复。
        // AUTONEXT-02 起判定收敛到 VideoSurfaceBinding，attachSurface 只执行它的结论。
        String source = read(PLAYBACK);
        int attach = source.indexOf("private void attachSurface(boolean restoreExoShutter)");
        int end = source.indexOf("\n    }", attach);
        assertTrue("attachSurface must exist", attach > 0 && end > attach);
        String body = source.substring(attach, end);

        assertTrue("the surface rebind must compare the live engine instance, not emptiness",
                body.contains("Player currentPlayer = getExoView().getPlayer();")
                        && body.contains("Player nextPlayer = player().getPlayer();")
                        && body.contains("VideoSurfaceBinding.resolve(currentPlayer, nextPlayer);"));
        assertTrue("the identity branch must hand the view the new engine",
                body.contains("getExoView().setPlayer(binding.player());"));
        int detach = body.indexOf("if (binding.detachedFirst()) getExoView().setPlayer(null);");
        int rebind = body.indexOf("getExoView().setPlayer(binding.player());");
        assertTrue("the released engine must be detached before the replacement is bound",
                detach > 0 && rebind > detach);
        assertFalse("attachment must no longer depend on the view merely being empty",
                body.contains("if (getExoView().getPlayer() == null)"));
    }

    @Test
    public void staleSpinnerFallbackCannotExposeAnotherItemsPlayback() throws Exception {
        for (String path : new String[] {
                "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java"
        }) {
            String source = read(path);
            int fallback = source.indexOf("private void hidePlaybackProgressIfStale()");
            int close = source.indexOf("showPlaybackContent();", fallback);
            String guard = source.substring(fallback, close);
            assertTrue(path + " must require a nonempty current owner before hiding the spinner",
                    guard.contains("player().isReleased()")
                            && guard.contains("player().isEmpty()")
                            && guard.contains("player().getPlaybackState() != Player.STATE_READY")
                            && guard.contains("!isOwner()"));

            int seekFallback = source.indexOf("private void hideSeekProgressIfReady()");
            int seekClose = source.indexOf("showPlaybackContent();", seekFallback);
            String seekGuard = source.substring(seekFallback, seekClose);
            assertTrue(path + " must keep the seek fallback owner-scoped",
                    seekGuard.contains("!isOwner()"));
        }
    }

    @Test
    public void staleSwitchCallbacksRecheckPageAndPlaybackContextWithApplyGuard() throws Exception {
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");

        assertTrue("mobile switch callbacks must use the captured request context",
                mobile.contains("isCurrentPlayerContentRequest(requestId, generation, key, flag, episode)"));
        assertTrue("mobile decode failures must not clear a newer request",
                mobile.contains("if (!isCurrentPlayerContentRequest(requestId, generation, key, flag, episode)) return;"));
        assertTrue("leanback switch callbacks must use the captured request context",
                leanback.contains("isCurrentPlayerContentRequest(requestId, generation, key, flag, episode)"));
        assertTrue("leanback failures must not clear a newer request",
                leanback.contains("if (!isCurrentPlayerContentRequest(requestId, generation, key, flag, episode)) return;"));
    }

    @Test
    public void switchingEpisodesClearsPendingSwitchStateAfterRefresh() throws Exception {
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");

        assertTrue("mobile episode playback must invalidate older switch requests",
                mobile.contains("playerContentGeneration++;")
                        && mobile.contains("decodeSwitchRefreshing = false;"));
        assertTrue("leanback episode playback must invalidate older switch requests",
                leanback.contains("playerContentGeneration++;")
                        && leanback.contains("playerKernelSwitchRefreshing = false;"));
        assertTrue("mobile item switch must discard a result waiting for the service",
                mobile.contains("mPendingPlayerResult = null;"));
        int mobileRequest = mobile.indexOf("private void beginPlayerContentRequest");
        int leanbackRequest = leanback.indexOf("private void beginPlayerContentRequest");
        assertTrue("mobile episode request must discard a result waiting for the service",
                mobile.substring(mobileRequest, mobile.indexOf("\n    }", mobileRequest))
                        .contains("mPendingPlayerResult = null;"));
        assertTrue("leanback episode request must discard a result waiting for the service",
                leanback.substring(leanbackRequest, leanback.indexOf("\n    }", leanbackRequest))
                        .contains("mPendingPlayer = null;"));
    }

    @Test
    public void kernelSwitchReleasesRefreshingStateBeforeApplyGuard() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        int method = source.indexOf("private void switchPlayerKernelWithResult");
        int clear = source.indexOf("playerKernelSwitchRefreshing = false;", method);
        int apply = source.indexOf("if (!canApplyPlayerContentRequest", method);
        assertTrue("leanback must clear a current switch before checking temporary player availability",
                method >= 0 && clear > method && apply > method && clear < apply);
    }

    @Test
    public void switchingToAnotherItemReleasesThePreviousSessionKey() throws Exception {
        // onNewIntent 换的是条目，旧会话已作废；不清令牌会让新条目一直拿旧 key 比对
        assertTrue("the reset hook must exist", read(PLAYBACK).contains("protected final void resetPlaybackOwnership()"));
        for (String path : new String[] {
                "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java",
                "app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java"
        }) {
            String source = read(path);
            int newIntent = source.indexOf("protected void onNewIntent(Intent intent)");
            int reset = source.indexOf("resetPlaybackOwnership();", newIntent);
            int end = source.indexOf("protected void initView", newIntent);
            assertTrue(path + " must exist", newIntent > 0);
            assertTrue(path + " must release the stale session key when the item changes",
                    reset > newIntent && (end < 0 || reset < end));
        }
    }

    @Test
    public void theSessionKeySurvivesActivityRecreation() throws Exception {
        String source = read(PLAYBACK);

        int save = source.indexOf("protected void onSaveInstanceState(@NonNull Bundle outState)");
        int restore = source.indexOf("private void restorePlaybackKey(Bundle savedInstanceState)");
        int initViewBody = source.indexOf("restorePlaybackKey(savedInstanceState);");
        int bindService = source.indexOf("if (!shouldBindPlaybackService()) return;", initViewBody);

        assertTrue("playback activity must implement onSaveInstanceState", save > 0);
        assertTrue("playback activity must restore the pinned key on recreation", restore > 0);
        assertTrue("the pinned key must be restored before binding the service",
                initViewBody > 0 && initViewBody < restore && bindService > initViewBody);
        assertTrue("the state key must match between save and restore",
                countOccurrences(source, "STATE_PLAYBACK_KEY") >= 3);
        assertTrue("the session key must not be saved after playback exits",
                source.substring(save, source.indexOf("\n    }", save))
                        .contains("!playbackExiting && pinnedPlaybackKey != null"));
    }

    /**
     * 加载圈的兜底必须与当前播放会话保持一致。
     */
    @Test
    public void theSpinnerFallbackStaysOwnerScoped() throws Exception {
        for (String path : new String[] {
                "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java"
        }) {
            String source = read(path);
            int fallback = source.indexOf("private void hidePlaybackProgressIfStale()");
            assertTrue(path + " must provide a spinner fallback", fallback > 0);

            String body = source.substring(fallback, source.indexOf("\n    }", fallback));
            assertTrue(path + " the fallback must reject a stale playback owner", body.contains("!isOwner()"));
            assertTrue(path + " the fallback must read the player state directly",
                    body.contains("player().getPlaybackState() != Player.STATE_READY"));
            // 详情还没加载完时播放器是空的，那时的圈属于详情页自己，不能收
            assertTrue(path + " the fallback must not steal the detail page's own loading state",
                    body.contains("player().isEmpty()"));
            assertTrue(path + " the fallback must only act while the spinner is actually up",
                    body.contains("getVisibility() != View.VISIBLE"));

            // 挂在网速自循环上：圈可见时每秒一跳，圈收了循环本就停，无额外开销
            int traffic = source.indexOf("private void setTraffic()");
            assertTrue(path + " must drive the fallback from the traffic ticker",
                    source.indexOf("hidePlaybackProgressIfStale();", traffic) > traffic);
        }
    }

    @Test
    public void staleSwitchCallbacksRecheckPageAndPlaybackContext() throws Exception {
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");

        assertTrue("mobile switch callbacks must use the captured request context",
                mobile.contains("isCurrentPlayerContentRequest(requestId, generation, key, flag, episode)"));
        assertTrue("mobile switch failures must not clear a newer request",
                mobile.contains("if (!isCurrentPlayerContentRequest(requestId, generation, key, flag, episode)) return;"));
        assertTrue("leanback switch callbacks must use the captured request context",
                leanback.contains("isCurrentPlayerContentRequest(requestId, generation, key, flag, episode)"));
        assertTrue("leanback switch failures must not clear a newer request",
                leanback.contains("if (!isCurrentPlayerContentRequest(requestId, generation, key, flag, episode)) return;"));
        assertTrue("mobile switch callbacks must have an apply guard",
                mobile.contains("private boolean canApplyPlayerContentRequest(int requestId, int generation,"));
        assertTrue("leanback switch callbacks must have an apply guard",
                leanback.contains("private boolean canApplyPlayerContentRequest(int requestId, int generation,"));
        assertTrue("mobile switch callbacks must have a request-state helper",
                mobile.contains("private int beginPlayerContentSwitch(int requestId, String key, String flag, String episode)"));
        assertTrue("leanback switch callbacks must have a request-state helper",
                leanback.contains("private int beginPlayerContentSwitch(int requestId, String key, String flag, String episode)"));
    }

    @Test
    public void switchingEpisodesClearsPendingSwitchState() throws Exception {
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");
        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java");

        assertTrue("mobile item refresh must invalidate the player request generation",
                mobile.contains("playerContentGeneration++;")
                        && mobile.contains("decodeSwitchRefreshing = false;"));
        assertTrue("leanback item refresh must invalidate the player request generation",
                leanback.contains("playerContentGeneration++;")
                        && leanback.contains("playerKernelSwitchRefreshing = false;"));
        assertTrue("mobile item switch must discard a result waiting for the service",
                mobile.contains("mPendingPlayerResult = null;"));
    }

    /**
     * 退出播放页必须确定性停播。
     *
     * <p>旧实现只在「服务在且本页是归属方」时 shutdown，其余情况交给控制器。控制器是异步连上的，
     * 播放又由服务侧直接起播，于是控制器未就绪时整次停播是空操作；播放器处于 IDLE（取流中退出）时
     * 会被会话里的 stop 直接忽略；这条路径也从不撤销前台通知。用户看到的就是
     * 「退回详情页后还有声音、通知栏还显示播放中」。
     */
    @Test
    public void exitingThePlayerPageAlwaysStopsThroughTheService() throws Exception {
        String source = read(PLAYBACK);

        int stop = source.indexOf("protected void stopPlayback()");
        assertTrue("stopPlayback must exist", stop > 0);
        int end = source.indexOf("\n    }", stop);
        assertTrue("stopPlayback must be readable", end > stop);
        String body = source.substring(stop, end);

        assertTrue("the owner must still shut the service down", body.contains("mService.shutdown();"));
        assertTrue("a non-owner exit must stop through the service as well",
                body.contains("mService.suspend();"));
        assertTrue("the controller may only remain as the no-service fallback",
                body.indexOf("mController.stop();") > body.indexOf("if (mService == null)"));
        assertFalse("the non-owner stop must not stay gated on an asynchronous controller",
                body.contains("else if (mController != null)"));
    }

    /**
     * 已经决定退出的播放页不能再起播。
     *
     * <p>自动换源、换线路、取流结果这些链路最终都回到 startPlayer；迟到的一次起播会在服务里正常
     * 播放并挂上前台通知，而页面已经退出，没有人再停它。
     */
    @Test
    public void anExitingPlayerPageNeverStartsPlaybackAgain() throws Exception {
        String source = read(PLAYBACK);

        int startPlayer = source.indexOf("protected void startPlayer(String key, Result result, boolean useParse, long timeout,");
        assertTrue("the six-argument startPlayer must exist", startPlayer > 0);
        int body = source.indexOf("MediaMetadata metadata, long startPositionMs) {", startPlayer);
        assertTrue("the six-argument startPlayer body must be readable", body > startPlayer);
        int route = source.indexOf("NovelRouter.isReaderUrl(result)", body);
        assertTrue("the reader route must still be the first dispatch", route > body);

        String guard = source.substring(body, route);
        assertTrue("startPlayer must refuse to start once the page is exiting",
                guard.contains("isPlaybackExiting()"));
        assertTrue("startPlayer must refuse to start once the page is finishing or destroyed",
                guard.contains("isFinishing()") && guard.contains("isDestroyed()"));
    }

    /**
     * 归属判定两侧必须同源：页面与服务各用一份，任一侧退回字符串相等就会重现 AUTONEXT-02。
     */
    @Test
    public void serviceNavigationOwnershipComparesTheSameSessions() throws Exception {
        String service = read("app/src/main/java/com/fongmi/android/tv/service/PlaybackService.java");

        int owner = service.indexOf("private boolean isNavigationOwner()");
        int ownerEnd = service.indexOf("\n    }", owner);
        assertTrue("isNavigationOwner must exist", owner > 0 && ownerEnd > owner);
        assertTrue("service-side navigation ownership must compare sessions, not raw strings",
                service.substring(owner, ownerEnd).contains("PlaybackOwnershipKeys.sameSession("));

        // 选集键前缀在 browse 与 player 两个包里各写一份，必须同值，否则归属判定会静默失效。
        assertTrue("VodBrowse must keep the episode prefix",
                read("app/src/main/java/com/fongmi/android/tv/browse/VodBrowse.java")
                        .contains("static final String VOD_EP = \"VE:\";"));
        assertTrue("PlaybackOwnershipKeys must use the same episode prefix",
                read("app/src/main/java/com/fongmi/android/tv/player/PlaybackOwnershipKeys.java")
                        .contains("static final String EPISODE_PREFIX = \"VE:\";"));
    }

    /**
     * Surface 补绑只能在用户实际在看的那一页执行，否则后台旧页面会把引擎抢走。
     */
    @Test
    public void surfaceTopUpRunsOnlyWhileThePageIsResumedAndNeverUnbinds() throws Exception {
        String source = read(PLAYBACK);

        int topUp = source.indexOf("private void ensureVideoSurfaceBound(String reason)");
        int end = source.indexOf("\n    }", topUp);
        assertTrue("ensureVideoSurfaceBound must exist", topUp > 0 && end > topUp);
        String body = source.substring(topUp, end);

        assertTrue("only a RESUMED page may re-bind the shared engine",
                body.contains("isAtLeast(Lifecycle.State.RESUMED)"));
        assertTrue("an engine that is not ready must not unbind the view",
                body.contains("if (nextPlayer == null) return;"));
        assertTrue("the top-up must not drop the ASS/subtitle sessions",
                !body.contains("detachSurface()"));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of(path.substring("app/".length())), StandardCharsets.UTF_8);
    }
}
