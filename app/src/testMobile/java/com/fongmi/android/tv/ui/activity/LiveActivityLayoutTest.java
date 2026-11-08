package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveActivityLayoutTest {

    @Test
    public void leanbackLiveControlsUseSharedOsdWithoutLegacyTopBar() throws Exception {
        Path sourcePath = findLeanbackJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String showControlBody = section(source, "private void showControl(View view)", "private void hideControl()");

        assertFalse(sourcePath + " is missing showControl", showControlBody.isEmpty());
        assertTrue("leanback live controls must hide the retired top bar before showing shared OSD",
                showControlBody.contains("mBinding.widget.top.setVisibility(View.GONE);")
                        && showControlBody.contains("mOsd.setControlsVisible(true);"));
        assertFalse("leanback live controls must not show the retired top bar when all OSD options are off",
                showControlBody.contains("mBinding.widget.top.setVisibility(View.VISIBLE);"));
    }

    @Test
    public void liveControlSourceButtonShowsTheCurrentLiveSourceName() throws Exception {
        assertLiveSourceLabelFollowsHome(findMobileJavaPath());
        assertLiveSourceLabelFollowsHome(findLeanbackJavaPath());
    }

    private static void assertLiveSourceLabelFollowsHome(Path javaRoot) throws Exception {
        Path sourcePath = javaRoot.resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String getLiveBody = section(source, "private void getLive()", "private void setSource(Live live)");
        String setSourceBody = section(source, "private void setSource(Live live)", "private void renderLive(Live live)");

        assertFalse(sourcePath + " is missing getLive", getLiveBody.isEmpty());
        assertTrue("every live load (including a source switch and the automatic fallback) must refresh the control bar label",
                getLiveBody.contains("setSource(live);"));
        assertTrue("the control bar source button must show the current live source name and fall back to its own label when the name is empty",
                setSourceBody.contains("boolean named = !name.isEmpty();")
                        && setSourceBody.contains("String label = named ? name : getString(R.string.live_control_source);")
                        && setSourceBody.contains("mBinding.control.action.source.setText(label);"));
        assertTrue("replacing the label with a bare source name must not drop the switch-source meaning for screen readers",
                setSourceBody.contains("mBinding.control.action.source.setContentDescription(named ? getString(R.string.live_control_source_named, name) : null);"));
    }

    @Test
    public void liveControlSourceChipClampsLongSourceNames() throws Exception {
        assertSourceChipIsClamped(findMobileJavaPath(), "160dp");
        assertSourceChipIsClamped(findLeanbackJavaPath(), "240dp");
    }

    private static void assertSourceChipIsClamped(Path javaRoot, String maxWidth) throws Exception {
        Path layoutPath = javaRoot.getParent().resolve(Path.of("res", "layout", "view_control_live_action.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        int id = layout.indexOf("android:id=\"@+id/source\"");
        int tagStart = id < 0 ? -1 : layout.lastIndexOf("<com.google.android.material.textview.MaterialTextView", id);
        int tagEnd = id < 0 ? -1 : layout.indexOf("/>", id);
        String chip = tagStart >= 0 && tagEnd > tagStart ? layout.substring(tagStart, tagEnd) : "";

        assertFalse(layoutPath + " is missing the live source chip", chip.isEmpty());
        assertTrue("a user-defined live source name must stay on one line and ellipsize instead of expanding the action row",
                chip.contains("android:maxLines=\"1\"") && chip.contains("android:ellipsize=\"end\""));
        assertTrue("the live source chip must cap its width so a long source name cannot crowd the primary live actions",
                chip.contains("android:maxWidth=\"" + maxWidth + "\""));
    }

    @Test
    public void liveControlSheetMirrorsTheCurrentLiveSourceName() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "dialog", "LiveControlDialog.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String initViewBody = section(source, "protected void initView()", "protected void initEvent()");
        String setPlayerBody = section(source, "public void setPlayer()", "private void setScale(int mode)");

        assertFalse(sourcePath + " is missing initView", initViewBody.isEmpty());
        assertTrue("the control sheet must open with the current live source name",
                initViewBody.contains("binding.source.setText(parent.control.action.source.getText());"));
        assertTrue("the control sheet must open with the current live source accessibility label",
                initViewBody.contains("binding.source.setContentDescription(parent.control.action.source.getContentDescription());"));
        assertTrue("an open control sheet must follow a live source switch",
                setPlayerBody.contains("binding.source.setText(parent.control.action.source.getText());"));
        assertTrue("an open control sheet must follow a live source switch for screen readers too",
                setPlayerBody.contains("binding.source.setContentDescription(parent.control.action.source.getContentDescription());"));
    }

    @Test
    public void explicitLivePiPPreparesVideoBeforeEnteringSystemPiP() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        int onPip = source.indexOf("private void onPiP()");
        int onPipEnd = source.indexOf("private void prepareLivePiPView()", onPip);
        String onPipBody = onPip >= 0 && onPipEnd > onPip ? source.substring(onPip, onPipEnd) : "";
        assertTrue(sourcePath + " is missing onPiP", onPip >= 0);
        assertTrue("explicit live PiP must prepare the entry path before scheduling system PiP",
                onPipBody.contains("preparePiP(\"panel\")")
                        && onPipBody.contains("prepareLivePiPView();")
                        && onPipBody.contains("scheduleLivePiPEntry();"));
        assertTrue("explicit live PiP preparation must run before the deferred entry is scheduled",
                onPipBody.indexOf("preparePiP(\"panel\")")
                        < onPipBody.indexOf("prepareLivePiPView();")
                        && onPipBody.indexOf("prepareLivePiPView();")
                        < onPipBody.indexOf("pipEntryPending = true;")
                        && onPipBody.indexOf("pipEntryPending = true;")
                        < onPipBody.indexOf("scheduleLivePiPEntry();"));
        assertFalse("explicit live PiP must not enter before the video layout is prepared",
                onPipBody.contains("mPiP.enter("));

        int prepare = source.indexOf("private void prepareLivePiPView()");
        int prepareEnd = source.indexOf("private void scheduleLivePiPEntry()", prepare);
        String prepareBody = prepare >= 0 && prepareEnd > prepare ? source.substring(prepare, prepareEnd) : "";
        assertTrue("live PiP preparation must expand the video container before entry",
                prepareBody.contains("setVideoView(true);")
                        && prepareBody.contains("mBinding.video.requestLayout();"));
        assertTrue("live PiP preparation must hide the embedded live chrome before entry",
                prepareBody.contains("mBinding.recycler.setVisibility(View.GONE);")
                        && prepareBody.contains("mBinding.navigation.setVisibility(View.GONE);"));

        int schedule = source.indexOf("private void scheduleLivePiPEntry()");
        int enter = source.indexOf("private void enterPreparedLivePiP()", schedule);
        String scheduleBody = schedule >= 0 && enter > schedule ? source.substring(schedule, enter) : "";
        assertTrue("live PiP entry must wait for a completed layout pass",
                scheduleBody.contains("OneShotPreDrawListener.add"));

        int enterEnd = source.indexOf("private void restoreAfterFailedLivePiP()", enter);
        String enterBody = enter >= 0 && enterEnd > enter ? source.substring(enter, enterEnd) : "";
        assertTrue("executing the deferred entry must release its completed listener handle",
                enterBody.contains("pipEntryListener = null;")
                        && enterBody.indexOf("pipEntryListener = null;")
                        < enterBody.indexOf("pipEntryPending = false;"));
        assertTrue("live PiP must update the source view before entering",
                enterBody.contains("mPiP.update(this, mBinding.video)")
                        && enterBody.indexOf("mPiP.update(this, mBinding.video)")
                        < enterBody.indexOf("mPiP.enter("));
        assertTrue("failed live PiP entry must restore the pre-entry layout",
                enterBody.contains("restoreAfterFailedLivePiP();"));
    }

    @Test
    public void livePipAudioActionKeepsBackgroundPlayback() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        String panelBody = section(source, "public void onLiveBackgroundPanel()", "public void onLiveListStylePanel(boolean classic)");
        String navigationBody = section(source, "public void onAudio()", "};");
        String switchBody = section(source, "private void switchToAudioBackground()", "private void setVideoView(");
        String prepareBody = section(source, "private boolean preparePiP(String reason)", "private boolean requestPiP(String reason)");
        String pipModeBody = section(source, "public void onPictureInPictureModeChanged(", "private void setVideoView(");
        String pipExitBody = section(source, "private void finishIfPipClosed()", "private void dismissLiveControlDialog()");
        String startBody = section(source, "protected void onStart()", "protected void onStop()");

        assertTrue("both live background entry points must share the guarded audio transition",
                panelBody.contains("switchToAudioBackground();")
                        && navigationBody.contains("switchToAudioBackground();"));
        int captureAudioMode = switchBody.indexOf("boolean audioOnly = isAudioOnly();");
        int markIntentionalExit = switchBody.indexOf("mKeepPlaybackAfterPipExit = isInPictureInPictureMode();");
        int setAudioMode = switchBody.indexOf("setAudioOnly(true);");
        int syncPipMode = switchBody.indexOf("syncPiPForPlaybackMode();");
        int moveToBackground = switchBody.indexOf("moveTaskToBack(true)");
        assertTrue("live audio background must preserve, mark, and synchronize state before moving the task",
                captureAudioMode >= 0
                        && markIntentionalExit > captureAudioMode
                        && setAudioMode > markIntentionalExit
                        && syncPipMode > setAudioMode
                        && moveToBackground > syncPipMode);
        assertTrue("a failed live background transition must restore audio and PiP state",
                switchBody.contains("mKeepPlaybackAfterPipExit = false;")
                        && switchBody.contains("setAudioOnly(audioOnly);")
                        && switchBody.lastIndexOf("syncPiPForPlaybackMode();") > moveToBackground);

        int pipEntered = pipModeBody.indexOf("if (isInPictureInPictureMode)");
        int resetStaleIntent = pipModeBody.indexOf("mKeepPlaybackAfterPipExit = false;", pipEntered);
        assertTrue("a new live PiP session must clear stale keep-playback state",
                pipEntered >= 0 && resetStaleIntent > pipEntered);
        assertTrue("live PiP exit detection must consume the intentional background marker",
                pipExitBody.contains("boolean keepPlayback = mKeepPlaybackAfterPipExit;")
                        && pipExitBody.contains("mKeepPlaybackAfterPipExit = false;")
                        && pipExitBody.contains("PipExitDecision.shouldFinishAfterPipExit(atLeastStarted, isFinishing(), isDestroyed(), keepPlayback)"));
        int resetAudioState = startBody.indexOf("setAudioOnly(false);");
        int resetPipState = startBody.indexOf("mPiP.resetAudioMode();");
        assertTrue("returning to live video must reset PiP mode without applying premature system parameters",
                resetAudioState >= 0
                        && resetPipState > resetAudioState
                        && !startBody.contains("syncPiPForPlaybackMode();"));
        int validateVideoTrack = prepareBody.indexOf("service() == null || !player().haveTrack(C.TRACK_TYPE_VIDEO)");
        int applyPipMode = prepareBody.indexOf("syncPiPForPlaybackMode()");
        assertTrue("live PiP parameters must only be applied after video eligibility is confirmed",
                validateVideoTrack >= 0 && applyPipMode > validateVideoTrack);
    }

    @Test
    public void pendingLivePiPEntryIsCancelledWhenTheActivityStops() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("the pending pre-draw listener must be retained so it can be cancelled",
                source.contains("private OneShotPreDrawListener pipEntryListener;"));

        int schedule = source.indexOf("private void scheduleLivePiPEntry()");
        int enter = source.indexOf("private void enterPreparedLivePiP()", schedule);
        String scheduleBody = schedule >= 0 && enter > schedule ? source.substring(schedule, enter) : "";
        assertTrue("live PiP scheduling must retain the one-shot listener",
                scheduleBody.contains("pipEntryListener = OneShotPreDrawListener.add"));

        int cancel = source.indexOf("private void cancelPendingLivePiP(boolean restoreUi)");
        int cancelEnd = source.indexOf("private void enterFullscreenLive()", cancel);
        String cancelBody = cancel >= 0 && cancelEnd > cancel ? source.substring(cancel, cancelEnd) : "";
        assertTrue("live PiP cancellation must remove the listener and clear pending state",
                cancelBody.contains("pipEntryListener.removeListener();")
                        && cancelBody.contains("pipEntryListener = null;")
                        && cancelBody.contains("pipEntryPending = false;"));
        assertTrue("cancelled live PiP must restore the prepared layout when requested",
                cancelBody.contains("if (restoreUi && !isInPictureInPictureMode()) restoreAfterFailedLivePiP();"));

        int onStop = source.indexOf("protected void onStop()");
        int onStopEnd = source.indexOf("protected void onBackInvoked()", onStop);
        String onStopBody = onStop >= 0 && onStopEnd > onStop ? source.substring(onStop, onStopEnd) : "";
        assertTrue("stopping the activity must cancel and restore a pending live PiP entry",
                onStopBody.contains("cancelPendingLivePiP(true);"));

        int onDestroy = source.indexOf("protected void onDestroy()");
        String onDestroyBody = onDestroy >= 0 ? source.substring(onDestroy) : "";
        assertTrue("destroying the activity must remove any pending live PiP listener without restoring UI",
                onDestroyBody.contains("cancelPendingLivePiP(false);"));

        int onPipChanged = source.indexOf("public void onPictureInPictureModeChanged(");
        int onPipChangedEnd = source.indexOf("private void setVideoView(", onPipChanged);
        String onPipChangedBody = onPipChanged >= 0 && onPipChangedEnd > onPipChanged
                ? source.substring(onPipChanged, onPipChangedEnd) : "";
        int enteredPiP = onPipChangedBody.indexOf("if (isInPictureInPictureMode)");
        int cancelPending = onPipChangedBody.indexOf("cancelPendingLivePiP(false);", enteredPiP);
        assertTrue("a successful system PiP transition must cancel any still-pending manual entry",
                enteredPiP >= 0 && cancelPending > enteredPiP);
    }

    @Test
    public void liveConfigInitializationUsesRuntimeStateForMobileAndLeanback() throws Exception {
        assertLiveConfigInitializationUsesRuntimeState(findMobileJavaPath());
        assertLiveConfigInitializationUsesRuntimeState(findLeanbackJavaPath());
    }

    private static void assertLiveConfigInitializationUsesRuntimeState(Path javaRoot) throws Exception {
        Path sourcePath = javaRoot.resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String checkLiveBody = section(source, "private void checkLive()", "private Callback getCallback()");

        assertFalse(sourcePath + " is missing checkLive", checkLiveBody.isEmpty());
        assertTrue("checkLive must inspect the current LiveConfig state before loading",
                checkLiveBody.contains("if (LiveConfig.isEmpty())"));
        assertFalse("checkLive must not trust the potentially stale launch Intent empty flag",
                checkLiveBody.contains("if (isEmpty())"));
        assertTrue("an uninitialized LiveConfig must be initialized and loaded",
                checkLiveBody.contains("LiveConfig.get().init().load(getCallback());"));
        assertTrue("an already-loaded LiveConfig must continue directly to playback",
                checkLiveBody.contains("getLive();"));
    }

    @Test
    public void liveParseFailureUsesSourceFallbackForMobileAndLeanback() throws Exception {
        assertLiveParseFailureUsesSourceFallback(findMobileJavaPath());
        assertLiveParseFailureUsesSourceFallback(findLeanbackJavaPath());
    }

    private static void assertLiveParseFailureUsesSourceFallback(Path javaRoot) throws Exception {
        Path sourcePath = javaRoot.resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String renderLiveBody = section(source, "private void renderLive(Live live)", "private void setGroup(Live live)");

        assertFalse(sourcePath + " is missing renderLive", renderLiveBody.isEmpty());
        assertTrue("an empty parsed live source must enter the source-level fallback when source fallback is enabled",
                renderLiveBody.contains("if (live == null || live.getGroups().isEmpty())")
                        && renderLiveBody.contains("if (LiveSetting.isSourceFallback()) startSourceFallback();"));
        assertTrue("an empty parsed live source must stop before rendering groups",
                renderLiveBody.indexOf("startSourceFallback();") < renderLiveBody.indexOf("return;"));
        assertTrue("a valid parsed live source must still render its groups",
                renderLiveBody.contains("mViewModel.parseXml(live);")
                        && renderLiveBody.contains("setGroup(live);")
                        && renderLiveBody.contains("setWidth(live);"));
    }

    @Test
    public void sourceHttpErrorBypassesPlayerRetriesWhenFallbackIsEnabledForMobileAndLeanback() throws Exception {
        assertSourceHttpErrorBypassesPlayerRetries(findMobileJavaPath());
        assertSourceHttpErrorBypassesPlayerRetries(findLeanbackJavaPath());
    }

    private static void assertSourceHttpErrorBypassesPlayerRetries(Path javaRoot) throws Exception {
        Path sourcePath = javaRoot.resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String method = section(source, "protected boolean onSourceHttpError(int statusCode, String msg)", "protected void onError(String msg)");

        assertFalse(sourcePath + " is missing onSourceHttpError", method.isEmpty());
        assertTrue("disabled line change and source fallback must preserve the normal player retry chain",
                method.contains("if (!LiveSetting.isChange() && !LiveSetting.isSourceFallback()) return false;"));
        assertTrue("enabled line change or source fallback must route the HTTP failure into the live fallback flow",
                method.contains("onError(msg);") && method.contains("return true;"));
        assertTrue("the failure must be handled before returning true",
                method.indexOf("onError(msg);") < method.indexOf("return true;"));
    }

    @Test
    public void playbackErrorCancelsStaleBufferingFallbackForMobileAndLeanback() throws Exception {
        assertPlaybackErrorCancelsStaleBufferingFallback(findMobileJavaPath());
        assertPlaybackErrorCancelsStaleBufferingFallback(findLeanbackJavaPath());
    }

    private static void assertPlaybackErrorCancelsStaleBufferingFallback(Path javaRoot) throws Exception {
        Path sourcePath = javaRoot.resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String onErrorBody = section(source, "protected void onError(String msg)", "protected void onReload(String msg)");

        assertFalse(sourcePath + " is missing onError", onErrorBody.isEmpty());
        assertTrue("a playback error must cancel the stale buffering timeout before fallback",
                onErrorBody.contains("App.removeCallbacks(mBufferingTimeout);")
                        && onErrorBody.indexOf("App.removeCallbacks(mBufferingTimeout);")
                        < onErrorBody.indexOf("startFlow();"));
    }

    @Test
    public void lineFallbackExhaustionKeepsErrorFeedbackForMobileAndLeanback() throws Exception {
        assertLineFallbackExhaustionKeepsErrorFeedback(findMobileJavaPath());
        assertLineFallbackExhaustionKeepsErrorFeedback(findLeanbackJavaPath());
    }

    private static void assertLineFallbackExhaustionKeepsErrorFeedback(Path javaRoot) throws Exception {
        Path sourcePath = javaRoot.resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        String advanceBody = section(source, "private boolean advanceLineForFallback()", "private boolean isLineFallbackExhausted()");
        assertFalse(sourcePath + " is missing advanceLineForFallback", advanceBody.isEmpty());
        assertTrue("the only line-switching site must itself refuse to rotate once the round is exhausted",
                advanceBody.contains("if (isLineFallbackExhausted()) return false;"));
        assertTrue("an exhausted rotation must report that no line was switched",
                advanceBody.contains("mLineFallbackExhausted = true;")
                        && advanceBody.contains("return false;"));
        assertTrue("a real line switch must report success after reloading the stream",
                advanceBody.indexOf("fetch();") < advanceBody.lastIndexOf("return true;"));

        String startFlowBody = section(source, "private boolean startFlow()", "private void startSourceFallback()");
        assertFalse(sourcePath + " is missing startFlow", startFlowBody.isEmpty());
        assertTrue("startFlow must report whether it actually switched lines",
                startFlowBody.contains("return action == LiveSourceFallbackPolicy.Action.NEXT_LINE && advanceLineForFallback();"));

        String onErrorBody = section(source, "protected void onError(String msg)", "protected void onReload(String msg)");
        assertFalse(sourcePath + " is missing onError", onErrorBody.isEmpty());
        assertTrue("a playback failure must surface its reason before any fallback decision",
                onErrorBody.indexOf("showError(msg);") < onErrorBody.indexOf("startFlow();"));

        String timeoutBody = section(source, "private void onBufferingTimeout()", "protected void onReload(String msg)");
        assertFalse(sourcePath + " is missing onBufferingTimeout", timeoutBody.isEmpty());
        assertTrue("the buffering timeout must not be muted by the previous attempt's failure flag",
                source.contains("mBufferingTimeout = this::onBufferingTimeout;")
                        && !timeoutBody.contains("mFailedThisSession"));
        assertTrue("a stall that cannot switch lines must still show a visible failure",
                timeoutBody.contains("if (!startFlow()) showError("));

        String reloadBody = section(source, "private void handleSameReloadUrl(String msg)", "private void resetAdapter()");
        assertFalse(sourcePath + " is missing handleSameReloadUrl", reloadBody.isEmpty());
        assertTrue("a reload that cannot switch lines must still show a visible failure",
                reloadBody.contains("if (advanceLineForFallback()) return;")
                        && reloadBody.contains("showError(msg);"));
        assertFalse("the reload path must not re-implement the switch preconditions and bypass the exhaustion guard",
                reloadBody.contains("mChannel != null && !mChannel.isOnly() && advanceLineForFallback()"));
    }

    @Test
    public void playbackEndStaysOnCurrentChannelForMobileAndLeanback() throws Exception {
        assertPlaybackEndStaysOnCurrentChannel(findMobileJavaPath());
        assertPlaybackEndStaysOnCurrentChannel(findLeanbackJavaPath());
    }
    private static void assertPlaybackEndStaysOnCurrentChannel(Path javaRoot) throws Exception {
        Path sourcePath = javaRoot.resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String checkEndedBody = section(source, "private void checkEnded()", "private void setTrackVisible()");

        assertFalse(sourcePath + " is missing checkEnded", checkEndedBody.isEmpty());
        assertTrue("live playback end must continue within the current channel", source.contains("mEndRetry = this::checkNext;"));
        assertFalse("live playback end must not advance to the next channel", checkEndedBody.contains("nextChannel();"));
        assertFalse("channel navigation must not depend on player live classification", checkEndedBody.contains("player().isLive()"));
        assertTrue("natural playback end must use a bounded retry delay",
                source.contains("private static final long PLAYBACK_END_RETRY_DELAY = 500;")
                        && source.contains("private Runnable mEndRetry;"));
        assertTrue("natural playback end must debounce before reloading the current channel",
                checkEndedBody.contains("App.removeCallbacks(mEndRetry);")
                        && checkEndedBody.contains("App.post(mEndRetry, PLAYBACK_END_RETRY_DELAY);"));

        String checkNextBody = section(source, "private void checkNext()", "\n    private void");
        assertTrue("delayed end retry must tolerate an activity without a selected channel",
                checkNextBody.contains("if (mChannel == null) return;"));

        String fetchBody = section(source, "private void fetch()", "private void start(Result result)");
        assertTrue("a new fetch must cancel a stale delayed end retry",
                fetchBody.contains("App.removeCallbacks(mEndRetry);"));

        int onDestroy = source.indexOf("protected void onDestroy()");
        assertTrue("destroying the activity must cancel a delayed end retry",
                onDestroy >= 0 && source.indexOf("App.removeCallbacks(mEndRetry);", onDestroy) >= onDestroy);
    }

    @Test
    public void liveActionBarFollowsPlayerButtonSettings() throws Exception {
        assertLiveActionBarFollowsPlayerButtonSettings(findMobileJavaPath());
        assertLiveActionBarFollowsPlayerButtonSettings(findLeanbackJavaPath());
    }

    private static void assertLiveActionBarFollowsPlayerButtonSettings(Path javaRoot) throws Exception {
        Path layoutPath = javaRoot.getParent().resolve(Path.of("res", "layout", "view_control_live_action.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        assertTrue("the live action row must expose @id/container so player button order can be applied",
                layout.contains("android:id=\"@+id/container\""));

        Path sourcePath = javaRoot.resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String setupBody = section(source, "private void setupActionButtons()", "private void addActionButton(");
        String visibilityBody = methodBody(source, "private void applyActionButtonVisibility()");
        String trackBody = section(source, "private void setTrackVisible()", "private MediaMetadata buildMetadata()");

        assertFalse(sourcePath + " is missing setupActionButtons", setupBody.isEmpty());
        assertFalse(sourcePath + " is missing applyActionButtonVisibility", visibilityBody.isEmpty());
        assertTrue("the live action row must register every player button that shares a name with the VOD row",
                setupBody.contains("addActionButton(PlayerButtonSetting.PLAYER, mBinding.control.action.player);")
                        && setupBody.contains("addActionButton(PlayerButtonSetting.DECODE, mBinding.control.action.decode);")
                        && setupBody.contains("addActionButton(PlayerButtonSetting.PLAY_PARAMS, mBinding.control.action.playParams);")
                        && setupBody.contains("addActionButton(PlayerButtonSetting.SPEED, mBinding.control.action.speed);")
                        && setupBody.contains("addActionButton(PlayerButtonSetting.SCALE, mBinding.control.action.scale);")
                        && setupBody.contains("addActionButton(PlayerButtonSetting.CHANGE, mBinding.control.action.change);")
                        && setupBody.contains("addActionButton(PlayerButtonSetting.TEXT, mBinding.control.action.text);")
                        && setupBody.contains("addActionButton(PlayerButtonSetting.AUDIO, mBinding.control.action.audio);")
                        && setupBody.contains("addActionButton(PlayerButtonSetting.VIDEO, mBinding.control.action.video);"));
        assertTrue("the live action row must register itself before applying the configured order",
                setupBody.indexOf("PlayerButtonSetting.applyOrder(mBinding.control.action.container, mActionButtons);")
                        > setupBody.indexOf("addActionButton(PlayerButtonSetting.PLAYER"));
        assertTrue("live must hide the buttons the user disabled in the shared player button settings",
                setupBody.contains("PlayerButtonSetting.applyOrder(mBinding.control.action.container, mActionButtons);")
                        && visibilityBody.contains("PlayerButtonSetting.applyVisibility(mActionButtons);"));
        assertTrue("live must keep unconditioned buttons restorable when the user re-enables them",
                visibilityBody.contains("mBinding.control.action.playParams.setVisibility(View.VISIBLE);")
                        && visibilityBody.contains("mBinding.control.action.player.setVisibility(View.VISIBLE);"));
        assertTrue("live must register the action row during view setup",
                source.contains("setupActionButtons();"));
        assertFalse(sourcePath + " is missing setTrackVisible", trackBody.isEmpty());
        assertTrue("dynamic track visibility must be decided before the user-hidden overlay is applied",
                trackBody.indexOf("applyActionButtonVisibility();") > trackBody.indexOf("mBinding.control.action.speed.setVisibility("));
    }

    @Test
    public void liveControlRevealRefreshesTrackButtons() throws Exception {
        assertShowControlRefreshesTracks(findMobileJavaPath(), "private void showControl()");
        assertShowControlRefreshesTracks(findLeanbackJavaPath(), "private void showControl(View view)");
    }

    private static void assertShowControlRefreshesTracks(Path javaRoot, String signature) throws Exception {
        Path sourcePath = javaRoot.resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String showBody = section(source, signature, "private void hideControl()");

        assertFalse(sourcePath + " is missing " + signature, showBody.isEmpty());
        assertTrue("revealing live controls must recompute the track buttons so re-enabled text/audio/video return",
                showBody.contains("setTrackVisible();"));
        assertTrue("dynamic track buttons must be recomputed before the configured-hidden overlay is applied",
                showBody.indexOf("setTrackVisible();") < showBody.indexOf("setPlayParamsState();"));
    }

    @Test
    public void livePlayParamsEntryFollowsTheSharedSetting() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String stateBody = section(source, "private void setPlayParamsState()", "private void showInfo()");

        assertFalse(sourcePath + " is missing setPlayParamsState", stateBody.isEmpty());
        assertTrue("hiding the playback params button must also hide the live top-bar shortcut",
                stateBody.contains("PlayerButtonSetting.isVisible(PlayerButtonSetting.PLAY_PARAMS)")
                        && stateBody.contains("mBinding.control.osdDiagnostics.setVisibility(entry ? View.VISIBLE : View.GONE);"));
        assertTrue("the playback params button itself must follow the shared settings overlay too",
                stateBody.contains("applyActionButtonVisibility();"));
    }

    @Test
    public void liveControlSheetMirrorsConfiguredButtons() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "dialog", "LiveControlDialog.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String initViewBody = section(source, "protected void initView()", "protected void initEvent()");
        String setPlayerBody = section(source, "public void setPlayer()", "private void setScale(int mode)");
        String syncBody = methodBody(source, "private void syncConfiguredButtonVisibility()");

        assertTrue("the live control sheet must mirror the configured player buttons on open",
                initViewBody.contains("syncConfiguredButtonVisibility();"));
        assertTrue("an open live control sheet must follow a player button settings change",
                setPlayerBody.contains("syncConfiguredButtonVisibility();"));
        assertFalse(sourcePath + " is missing syncConfiguredButtonVisibility", syncBody.isEmpty());
        assertTrue("the sheet must mirror player, decode and playback params visibility from the action row",
                syncBody.contains("binding.player.setVisibility(parent.control.action.player.getVisibility());")
                        && syncBody.contains("binding.decode.setVisibility(parent.control.action.decode.getVisibility());")
                        && syncBody.contains("binding.playParams.setVisibility(parent.control.action.playParams.getVisibility());"));
    }

    @Test
    public void padLiveFullscreenRotatesWithoutLeavingFullscreen() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String showControlBody = section(source, "private void showControl()", "private void hideControl()");
        String onRotateBody = section(source, "private void onRotate()", "private void toggleFullscreenOrientation()");
        String toggleBody = section(source, "private void toggleFullscreenOrientation()", "private void onPiP()");
        String orientBody = section(source, "private int getFullscreenOrient()", "private int getEmbeddedOrient()");
        String exitBody = section(source, "private void exitFullscreenLive()", "private int getLaunchOrient()");

        assertTrue("the rotate action must stay reachable on a tablet running pad fullscreen live",
                showControlBody.contains("mBinding.control.right.rotate.setVisibility(isLock() ? View.GONE : View.VISIBLE);"));
        assertFalse("pad fullscreen live must not keep hiding the rotate action",
                showControlBody.contains("mBinding.control.right.rotate.setVisibility(isLock() || isPadLiveFullscreenMode()"));
        assertTrue("the rotate action must branch into the tablet fullscreen orientation toggle",
                onRotateBody.contains("ResUtil.isPad()")
                        && onRotateBody.contains("toggleFullscreenOrientation();"));
        assertTrue("the tablet toggle must flip the portrait override and re-request orientation",
                toggleBody.contains("livePortraitFullscreen = !livePortraitFullscreen;")
                        && toggleBody.contains("requestOrientation(\"rotate-fullscreen-pad\", getFullscreenOrient());"));
        assertFalse("rotating on a tablet must not leave fullscreen",
                toggleBody.contains("exitFullscreenLive()"));
        assertTrue("pad fullscreen orientation must support the portrait direction",
                orientBody.contains("ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT"));
        assertTrue("leaving fullscreen must reset the tablet portrait override",
                exitBody.contains("livePortraitFullscreen = false;"));
    }

    @Test
    public void mobilePortraitLivePlayerReservesTheStatusBarArea() throws Exception {
        Path resPath = findMobileJavaPath().getParent().resolve("res");
        Path layoutPath = resPath.resolve(Path.of("layout", "activity_live.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        int spacer = layout.indexOf("android:id=\"@+id/statusBar\"");
        int video = layout.indexOf("android:id=\"@+id/video\"");

        assertTrue(layoutPath + " is missing the @+id/statusBar spacer", spacer >= 0);
        assertTrue("the status bar spacer must sit above the embedded live player", video > spacer);

        Path landPath = resPath.resolve(Path.of("layout-land", "activity_live.xml"));
        String land = new String(Files.readAllBytes(landPath), StandardCharsets.UTF_8);
        assertFalse("fullscreen landscape live must not reserve a status bar band",
                land.contains("android:id=\"@+id/statusBar\""));

        Path sourcePath = findMobileJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String setupBody = section(source, "private void setupWindowInsets()", "private void setVideoView()");
        String insetBody = methodBody(source, "private void updateStatusBarInset(@Nullable WindowInsetsCompat insets)");
        String applyBody = methodBody(source, "private void applyStatusBarInset()");
        String embeddedBody = section(source, "private void updateEmbeddedUiMode()", "private void setLiveMenuOverlay(boolean overlay)");
        String pipBody = section(source, "public void onPictureInPictureModeChanged(", "private void switchToAudioBackground()");

        assertTrue("the live window must hand the status bar inset to the spacer",
                setupBody.contains("updateStatusBarInset(insets);"));
        assertTrue("the status bar height must come from the status bar insets", insetBody.contains("Type.statusBars()).top"));
        assertTrue("the embedded live player must reserve the status bar height and release it in fullscreen",
                applyBody.contains("isEmbeddedLiveUi() ? statusBarInset : 0"));
        assertTrue("switching between embedded and fullscreen live must reapply the reserved status bar area",
                embeddedBody.contains("applyStatusBarInset();"));
        assertTrue("the PiP window must release the reserved status bar area as well",
                pipBody.contains("applyStatusBarInset();"));
    }

    private static String methodBody(String source, String startMarker) {
        int start = source.indexOf(startMarker);
        if (start < 0) return "";
        int end = source.indexOf("\n    private ", start + startMarker.length());
        return end > start ? source.substring(start, end) : source.substring(start);
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = start < 0 ? -1 : source.indexOf(endMarker, start + startMarker.length());
        return start >= 0 && end > start ? source.substring(start, end) : "";
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
    }
}
