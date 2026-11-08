package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.google.gson.Gson;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class PlayerManagerTest {

    @Test
    public void speedToggle_restoresOriginalShowSpeedAfterFastToggle() {
        PlayerManager.SpeedToggleState state = new PlayerManager.SpeedToggleState();

        float fastSpeed = state.next(1.5f, 1.5f, 3.0f, 1.25f);
        float restoredSpeed = state.next(fastSpeed, fastSpeed, 3.0f, 1.25f);

        assertEquals(3.0f, fastSpeed, 0.001f);
        assertEquals(1.5f, restoredSpeed, 0.001f);
    }

    @Test
    public void speedToggle_withoutSessionBaselineFallsBackToPersonalDefault() {
        PlayerManager.SpeedToggleState state = new PlayerManager.SpeedToggleState();

        float restoredSpeed = state.next(3.0f, 3.0f, 3.0f, 1.25f);

        assertEquals(1.25f, restoredSpeed, 0.001f);
    }

    @Test
    public void exoSpeedRestore_defersSpeedUntilMatchingPrepareIsReady() {
        PlayerManager.ExoSpeedRestoreState state = new PlayerManager.ExoSpeedRestoreState();

        assertEquals(1.0f, state.beginPrepare(7, 1.5f), 0.001f);
        assertEquals(1.5f, state.effectiveSpeed(1.0f), 0.001f);
        assertTrue(state.deferSpeed(1.25f));
        assertEquals(1.25f, state.effectiveSpeed(1.0f), 0.001f);
        assertTrue(Float.isNaN(state.takeReadySpeed(6)));
        assertEquals(1.25f, state.takeReadySpeed(7), 0.001f);
        assertEquals(1.0f, state.effectiveSpeed(1.0f), 0.001f);
    }

    @Test
    public void exoSpeedRestore_carriesPendingTargetIntoReplacementPrepare() {
        PlayerManager.ExoSpeedRestoreState state = new PlayerManager.ExoSpeedRestoreState();

        state.beginPrepare(7, 1.5f);
        float desiredSpeed = state.effectiveSpeed(1.0f);
        state.beginPrepare(8, desiredSpeed);

        assertTrue(Float.isNaN(state.takeReadySpeed(7)));
        assertEquals(1.5f, state.takeReadySpeed(8), 0.001f);
    }

    @Test
    public void exoSpeedRestore_cancelledPrepareKeepsTargetButRejectsLateReady() {
        PlayerManager.ExoSpeedRestoreState state = new PlayerManager.ExoSpeedRestoreState();

        state.beginPrepare(7, 1.5f);
        state.cancelPrepare(7);

        assertTrue(Float.isNaN(state.takeReadySpeed(7)));
        assertEquals(1.5f, state.effectiveSpeed(1.0f), 0.001f);
        assertTrue(state.deferSpeed(1.25f));
        assertEquals(1.0f, state.beginPrepare(8, state.effectiveSpeed(1.0f)), 0.001f);
        assertTrue(Float.isNaN(state.takeReadySpeed(7)));
        assertEquals(1.25f, state.takeReadySpeed(8), 0.001f);
    }

    @Test
    public void exoSpeedRestore_staleCancellationDoesNotCancelReplacementPrepare() {
        PlayerManager.ExoSpeedRestoreState state = new PlayerManager.ExoSpeedRestoreState();

        state.beginPrepare(7, 1.5f);
        state.beginPrepare(8, state.effectiveSpeed(1.0f));
        state.cancelPrepare(7);

        assertEquals(1.5f, state.takeReadySpeed(8), 0.001f);
    }

    @Test
    public void nextFallbackAction_obeysConfiguredMode() {
        assertEquals(PlayerManager.FALLBACK_DECODE, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_FULL, PlayerEngine.HARD));
        assertEquals(PlayerManager.FALLBACK_PLAYER, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_FULL, PlayerEngine.SOFT));
        assertEquals(PlayerManager.FALLBACK_DECODE, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_DECODE_ONLY, PlayerEngine.HARD));
        assertEquals(PlayerManager.FALLBACK_NONE, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_DECODE_ONLY, PlayerEngine.SOFT));
        assertEquals(PlayerManager.FALLBACK_PLAYER, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_PLAYER_ONLY, PlayerEngine.HARD));
        assertEquals(PlayerManager.FALLBACK_PLAYER, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_PLAYER_ONLY, PlayerEngine.SOFT));
        assertEquals(PlayerManager.FALLBACK_NONE, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_DISABLED, PlayerEngine.HARD));
        assertEquals(PlayerManager.FALLBACK_NONE, PlayerManager.nextFallbackAction(PlayerSetting.FALLBACK_DISABLED, PlayerEngine.SOFT));
    }

    @Test
    public void fallbackDecode_obeysConfiguredMode() {
        assertEquals(PlayerEngine.HARD, PlayerManager.fallbackDecode(PlayerSetting.FALLBACK_FULL, PlayerSetting.EXO, PlayerSetting.IJK, PlayerEngine.SOFT));
        assertEquals(PlayerEngine.SOFT, PlayerManager.fallbackDecode(PlayerSetting.FALLBACK_PLAYER_ONLY, PlayerSetting.EXO, PlayerSetting.IJK, PlayerEngine.SOFT));
        assertEquals(PlayerEngine.SOFT, PlayerManager.fallbackDecode(PlayerSetting.FALLBACK_FULL, PlayerSetting.EXO, PlayerSetting.EXO, PlayerEngine.SOFT));
        assertEquals(PlayerEngine.HARD, PlayerManager.fallbackDecode(PlayerSetting.FALLBACK_PLAYER_ONLY, PlayerSetting.EXO, PlayerSetting.IJK, 99));
    }

    @Test
    public void shouldRetryMpvCopy_retriesAutomaticGpuHardFailures() {
        assertTrue(PlayerManager.shouldRetryMpvCopy(
                true, true, false, false, false,
                PlaybackException.ERROR_CODE_TIMEOUT, null));
        assertTrue(PlayerManager.shouldRetryMpvCopy(
                true, true, false, false, false,
                PlaybackException.ERROR_CODE_DECODING_FAILED, "MPV_DECODE_FAILED: codec init"));
    }

    @Test
    public void shouldRetryMpvCopy_preservesManualModesAndRejectsUnrelatedFailures() {
        assertFalse(PlayerManager.shouldRetryMpvCopy(
                false, true, false, false, false,
                PlaybackException.ERROR_CODE_DECODING_FAILED, null));
        assertFalse(PlayerManager.shouldRetryMpvCopy(
                true, true, true, false, false,
                PlaybackException.ERROR_CODE_DECODING_FAILED, null));
        assertFalse(PlayerManager.shouldRetryMpvCopy(
                true, true, false, true, false,
                PlaybackException.ERROR_CODE_DECODING_FAILED, null));
        assertFalse(PlayerManager.shouldRetryMpvCopy(
                true, true, false, false, true,
                PlaybackException.ERROR_CODE_DECODING_FAILED, null));
        assertFalse(PlayerManager.shouldRetryMpvCopy(
                true, true, false, false, false,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, "Socket timeout"));
    }

    @Test
    public void shouldStopOnManualSwitchFailure_blocksFallbackWhileManualSwitchIsPending() {
        assertEquals(true, PlayerManager.shouldStopOnManualSwitchFailure(true, PlayerEngine.ErrorAction.FATAL));
    }

    @Test
    public void shouldStopOnManualSwitchFailure_allowsRecoveredManualErrors() {
        assertEquals(false, PlayerManager.shouldStopOnManualSwitchFailure(true, PlayerEngine.ErrorAction.RECOVERED));
    }

    @Test
    public void shouldStopOnManualSwitchFailure_allowsAutomaticFallbacks() {
        assertEquals(false, PlayerManager.shouldStopOnManualSwitchFailure(false, PlayerEngine.ErrorAction.FATAL));
    }

    @Test
    public void snapshotCurrentResult_usesResolvedSpecStateWhilePreservingMetadata() {
        Result source = new Gson().fromJson(
                "{\"url\":\"source.m3u8\",\"playUrl\":\"https://proxy.example/?url=\",\"parse\":1,\"jx\":1,\"artwork\":\"https://img.example/art.jpg\"}",
                Result.class);
        source.setHeader(new HashMap<>(Map.of("Old", "1")));
        PlaySpec spec = PlaySpec.fromParse(source, "VE:site@@vod|线路一|1", null);
        spec.setUrl("https://cdn.example/current.m3u8");
        spec.setHeaders(new HashMap<>(Map.of("Referer", "https://example.com/")));
        spec.setFormat(MimeTypes.APPLICATION_M3U8);

        Result snapshot = PlayerManager.snapshotCurrentResult(spec);

        assertEquals("https://cdn.example/current.m3u8", snapshot.getRealUrl());
        assertEquals("", snapshot.getPlayUrl());
        assertEquals(0, snapshot.getParse().intValue());
        assertFalse(snapshot.needParse());
        assertEquals("https://example.com/", snapshot.getHeader().get("Referer"));
        assertFalse(snapshot.getHeader().containsKey("Old"));
        assertEquals(MimeTypes.APPLICATION_M3U8, snapshot.getFormat());
        assertEquals("https://img.example/art.jpg", snapshot.getArtwork());
        assertEquals("https://proxy.example/?url=source.m3u8", source.getRealUrl());
        assertTrue(source.needParse());
        assertEquals("1", source.getHeader().get("Old"));
    }

    @Test
    public void isCurrentDirectSwitchRefresh_acceptsOnlyMatchingPendingRequest() {
        PlaySpec requested = PlaySpec.from("key", "https://example.com/video.m3u8", null, null);
        PlaySpec replacement = PlaySpec.from("other", "https://example.com/other.m3u8", null, null);

        assertTrue(PlayerManager.isCurrentDirectSwitchRefresh(true, 7, 7, PlayerSetting.EXO, PlayerSetting.EXO, requested, requested));
        assertFalse(PlayerManager.isCurrentDirectSwitchRefresh(false, 7, 7, PlayerSetting.EXO, PlayerSetting.EXO, requested, requested));
        assertFalse(PlayerManager.isCurrentDirectSwitchRefresh(true, 7, 8, PlayerSetting.EXO, PlayerSetting.EXO, requested, requested));
        assertFalse(PlayerManager.isCurrentDirectSwitchRefresh(true, 7, 7, PlayerSetting.EXO, PlayerSetting.IJK, requested, requested));
        assertFalse(PlayerManager.isCurrentDirectSwitchRefresh(true, 7, 7, PlayerSetting.EXO, PlayerSetting.EXO, requested, replacement));
    }

    @Test
    public void findSubtitleSub_matchesSelectedExternalSubtitleByLabelAndMime() {
        Sub english = Sub.create("English", "/tmp/english.srt", "en", MimeTypes.APPLICATION_SUBRIP);
        Sub chinese = Sub.create("Chinese", "/tmp/chinese.srt", "zh-Hans", MimeTypes.APPLICATION_SUBRIP);
        Format selected = new Format.Builder()
                .setLabel("English")
                .setLanguage("eng")
                .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
                .build();

        Sub matched = PlayerManager.findSubtitleSub(List.of(chinese, english), selected);

        assertSame(english, matched);
    }

    @Test
    public void findSubtitleSub_ignoresInternalSubtitleWithoutMatchingSub() {
        Sub english = Sub.create("English", "/tmp/english.srt", "en", MimeTypes.APPLICATION_SUBRIP);
        Format selected = new Format.Builder()
                .setLabel("Embedded English")
                .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
                .build();

        assertNull(PlayerManager.findSubtitleSub(List.of(english), selected));
    }

    @Test
    public void findSelectedSubtitleSub_usesFirstExternalSubtitleBeforeTracksLoad() {
        Sub manual = Sub.create("Manual", "/tmp/manual.srt", "zh-Hans", MimeTypes.APPLICATION_SUBRIP);

        assertSame(manual, PlayerManager.findSelectedSubtitleSub(List.of(manual), Tracks.EMPTY));
    }

    @Test
    public void selectionTrack_isMarkedSelectedSoTheOverrideSelectsIt() {
        // 设备实测：自动匹配命中后字幕出现在列表里，但「关闭字幕」仍高亮。
        // 原因是这里构造的 Track 没置 selected，Exo 的 setTrackSelection 只对
        // isSelected() 为真的轨道写选中索引，空索引 override 等于清空字幕组。
        Sub sub = Sub.create("战狼2.2017.4K.HDR.H265.简体 雅黑 灰.ass", "/tmp/zhanlang2.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        Format format = new Format.Builder()
                .setLabel("战狼2.2017.4K.HDR.H265.简体 雅黑 灰.ass")
                .setLanguage("zh-Hans")
                .setSampleMimeType(MimeTypes.TEXT_SSA)
                .build();

        Track track = PlayerManager.selectionTrack(format, sub);

        assertTrue("选轨用的 Track 必须是 selected，否则 override 会清空字幕组", track.isSelected());
        assertFalse(track.isDisabled());
        assertEquals(C.TRACK_TYPE_TEXT, track.getType());
    }

    @Test
    public void selectionTrack_fallsBackToSubtitleNameWhenFormatHasNoLabel() {
        Sub sub = Sub.create("匹配字幕.ass", "/tmp/sub.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        Format format = new Format.Builder()
                .setLanguage("zh-Hans")
                .setSampleMimeType(MimeTypes.TEXT_SSA)
                .build();

        Track track = PlayerManager.selectionTrack(format, sub);

        assertEquals("匹配字幕.ass", track.getName());
        assertTrue(track.isSelected());
    }

    @Test
    public void httpStatus_findsNestedResponseCode() {
        IOException error = new IOException("source", new IOException("Response code: 403"));

        assertEquals(403, PlayerManager.httpStatus(error));
    }

    @Test
    public void httpStatus_returnsZeroWithoutResponseCode() {
        assertEquals(0, PlayerManager.httpStatus(new IOException("Socket closed")));
    }

    @Test
    public void resolvePendingSubtitleApply_waitsWhileTheTrackListHasNoSubtitleTrack() throws Exception {
        PlayerManager manager = bareManager(Tracks.EMPTY, new ArrayList<>());
        Sub target = Sub.create("匹配字幕.ass", "/tmp/sub.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        List<Boolean> results = new ArrayList<>();
        Consumer<Boolean> listener = results::add;
        setField(manager, "pendingSubtitleApply", target);
        setField(manager, "subtitleApplyListener", listener);

        manager.resolvePendingSubtitleApply();
        assertTrue("空轨道表不能回报失败", results.isEmpty());
        assertSame("空轨道表必须保留待确认字幕", target, getField(manager, "pendingSubtitleApply"));
        assertSame("空轨道表必须保留确认回调", listener, getField(manager, "subtitleApplyListener"));

        setField(manager, "pendingSubtitleSelectionIssued", true);
        manager.resolvePendingSubtitleApply();
        assertTrue("已下发选轨但轨道表仍为空时，同样只能继续等", results.isEmpty());
        assertSame(target, getField(manager, "pendingSubtitleApply"));
    }

    @Test
    public void resolvePendingSubtitleApply_doesNotFailOnANonEmptyTrackListWithoutTheSubtitle() throws Exception {
        Format video = new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build();
        Tracks tracks = new Tracks(List.of(new Tracks.Group(new TrackGroup(video), true,
                new int[]{C.FORMAT_HANDLED}, new boolean[]{true})));
        PlayerManager manager = bareManager(tracks, new ArrayList<>());
        Sub target = Sub.create("匹配字幕.ass", "/tmp/sub.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        List<Boolean> results = new ArrayList<>();
        setField(manager, "pendingSubtitleApply", target);
        setField(manager, "subtitleApplyListener", (Consumer<Boolean>) results::add);
        setField(manager, "pendingSubtitleSelectionIssued", true);

        manager.resolvePendingSubtitleApply();

        assertTrue("只有视频轨时不能判失败", results.isEmpty());
        assertSame(target, getField(manager, "pendingSubtitleApply"));
    }

    @Test
    public void subtitleApplyDeadline_reportsTheRealSelectionStateAndClearsTheRegistration() throws Exception {
        PlayerManager manager = bareManager(Tracks.EMPTY, new ArrayList<>());
        Sub target = Sub.create("匹配字幕.ass", "/tmp/sub.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        List<Boolean> results = new ArrayList<>();
        setField(manager, "pendingSubtitleApply", target);
        setField(manager, "subtitleApplyListener", (Consumer<Boolean>) results::add);
        setField(manager, "subtitleApplyTimer", new RecordingTimer(new ArrayList<>(), new ArrayList<>()));

        manager.onSubtitleApplyTimeout();

        assertEquals("到期仍未选中才算未生效", List.of(false), results);
        assertNull(getField(manager, "pendingSubtitleApply"));
        assertNull(getField(manager, "subtitleApplyListener"));
    }

    @Test
    public void resolvePendingSubtitleApply_selectsTheTrackAndConfirmsSynchronously() throws Exception {
        Sub target = Sub.create("匹配字幕.ass", "/tmp/sub.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        Format text = new Format.Builder()
                .setLabel(target.getName())
                .setLanguage("zh-Hans")
                .setSampleMimeType(MimeTypes.TEXT_SSA)
                .build();
        Tracks tracks = new Tracks(List.of(new Tracks.Group(new TrackGroup(text), true,
                new int[]{C.FORMAT_HANDLED}, new boolean[]{true})));
        List<Boolean> timerArmedAtSelection = new ArrayList<>();
        List<List<Track>> selections = new ArrayList<>();
        PlayerManager manager = bareManager(tracks, selections);
        List<Boolean> results = new ArrayList<>();
        setField(manager, "pendingSubtitleApply", target);
        setField(manager, "subtitleApplyListener", (Consumer<Boolean>) results::add);
        RecordingTimer timer = new RecordingTimer(timerArmedAtSelection, selections);
        setField(manager, "subtitleApplyTimer", timer);

        manager.resolvePendingSubtitleApply();

        assertEquals("超时必须先于选轨挂好", List.of(true), timerArmedAtSelection);
        assertEquals("必须先显式选中目标轨道", 1, selections.size());
        assertTrue("命中的就是当前选中轨时必须立即确认", results.contains(Boolean.TRUE));
        assertNull(getField(manager, "pendingSubtitleApply"));
    }

    @Test
    public void clearPendingSubtitleApply_cancelsTheDeadlineAndDropsTheListener() throws Exception {
        PlayerManager manager = bareManager(Tracks.EMPTY, new ArrayList<>());
        List<Boolean> results = new ArrayList<>();
        RecordingTimer timer = new RecordingTimer(new ArrayList<>(), new ArrayList<>());
        setField(manager, "subtitleApplyTimer", timer);
        setField(manager, "pendingSubtitleApply", Sub.create("匹配字幕.ass", "/tmp/sub.ass", "zh-Hans", MimeTypes.TEXT_SSA));
        setField(manager, "subtitleApplyListener", (Consumer<Boolean>) results::add);

        manager.clearPendingSubtitleApply();

        assertNull(getField(manager, "pendingSubtitleApply"));
        assertNull(getField(manager, "subtitleApplyListener"));
        assertTrue("必须撤销已挂的超时", timer.cancelled > 0);
        assertTrue("静默清理不能给界面报任何结果", results.isEmpty());
    }

    @Test
    public void findSubtitleFormat_rejectsTheOnlyCandidateWhenItsLabelBelongsToAnotherTrack() {
        Sub external = Sub.create("匹配字幕.ass", "/tmp/sub.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        Format embedded = new Format.Builder()
                .setLabel("内嵌中字")
                .setLanguage("zh-Hans")
                .setSampleMimeType(MimeTypes.TEXT_SSA)
                .build();

        assertNull("label 存在且不是这条字幕，就不能当成目标轨",
                PlayerManager.findSubtitleFormat(textTracks(embedded, false), external, embedded));
    }

    @Test
    public void findSubtitleFormat_acceptsTheOnlyCandidateWhenItIsIdentifiedAsTheSubtitle() {
        Sub external = Sub.create("匹配字幕.ass", "/tmp/sub.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        Format candidate = new Format.Builder()
                .setLabel(external.getName())
                .setSampleMimeType(MimeTypes.TEXT_SSA)
                .setLanguage("zh-Hans")
                .build();

        assertSame(candidate, PlayerManager.findSubtitleFormat(textTracks(candidate, false), external, null));
    }

    @Test
    public void findSubtitleFormat_rejectsAMimeMismatchEvenWhenItIsSelected() {
        Sub external = Sub.create("匹配字幕.ass", "/tmp/sub.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        Format embedded = new Format.Builder()
                .setLanguage("zh-Hans")
                .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
                .build();

        assertNull("selectedFallback 不能绕过身份校验",
                PlayerManager.findSubtitleFormat(textTracks(embedded, true), external, embedded));
    }

    @Test
    public void findSubtitleFormat_rejectsUnlabeledCandidateEvenWhenMimeLanguageAndSelectionMatch() {
        Sub external = Sub.create("匹配字幕.ass", "/tmp/sub.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        Format unidentified = new Format.Builder()
                .setSampleMimeType(MimeTypes.TEXT_SSA)
                .setLanguage("zh-Hans")
                .build();
        Tracks tracks = textTracks(unidentified, true);

        assertNull("唯一无 label 候选不是外挂身份凭据",
                PlayerManager.findSubtitleFormat(tracks, external, null));
        assertNull("selectedFallback 不能把无身份凭据的轨当成目标",
                PlayerManager.findSubtitleFormat(tracks, external, unidentified));
    }

    @Test
    public void setSub_manualReplacementCancelsPreviousApplyAndDoesNotOverrideManualChoice() throws Exception {
        Sub automatic = Sub.create("自动字幕.ass", "/tmp/auto.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        Sub manual = Sub.create("手动字幕.ass", "/tmp/manual.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        Format oldTrack = new Format.Builder()
                .setLabel(automatic.getName())
                .setLanguage("zh-Hans")
                .setSampleMimeType(MimeTypes.TEXT_SSA)
                .build();
        List<List<Track>> selections = new ArrayList<>();
        PlayerManager manager = bareManager(textTracks(oldTrack, true), selections);
        PlaySpec spec = PlaySpec.from("", "https://example.com/video.mp4", null, null);
        spec.setSub(automatic);
        setField(manager, "spec", spec);
        setField(manager, "pendingSubtitleApply", automatic);
        List<Boolean> results = new ArrayList<>();
        setField(manager, "subtitleApplyListener", (Consumer<Boolean>) results::add);
        RecordingTimer timer = (RecordingTimer) getField(manager, "subtitleApplyTimer");
        timer.schedule(null, 15000L);

        manager.setSub(manual, false);

        assertSame("真实 setSub 已把手动字幕挂到 spec 首位", manual, spec.getSubs().get(0));
        assertTrue("手动替换必须丢掉旧自动目标", getField(manager, "pendingSubtitleApply") == null);
        assertNull("手动替换必须丢掉旧自动回调", getField(manager, "subtitleApplyListener"));
        assertFalse("手动替换必须撤销旧超时", timer.armed);
        assertTrue("手动替换不回报过期结果", results.isEmpty());

        manager.resolvePendingSubtitleApply();
        manager.onSubtitleApplyTimeout();

        assertTrue("晚到轨道或超时不能覆盖手动选择", selections.isEmpty());
        assertTrue("晚到轨道或超时不能通知旧回调", results.isEmpty());
    }

    @Test
    public void setSub_automaticReplacementPreservesItsListenerAndArmsTheDeadline() throws Exception {
        Sub target = Sub.create("自动字幕.ass", "/tmp/auto.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        PlayerManager manager = bareManager(Tracks.EMPTY, new ArrayList<>());
        setField(manager, "spec", PlaySpec.from("", "https://example.com/video.mp4", null, null));
        List<Boolean> results = new ArrayList<>();
        Consumer<Boolean> listener = results::add;
        manager.setSubtitleApplyListener(listener);
        RecordingTimer timer = (RecordingTimer) getField(manager, "subtitleApplyTimer");

        manager.setSub(target, true);

        assertSame(target, getField(manager, "pendingSubtitleApply"));
        assertSame("自动 setSub 不能丢自己的回调", listener, getField(manager, "subtitleApplyListener"));
        assertTrue("自动匹配继续等原有 deadline", timer.armed);
        assertTrue(results.isEmpty());
    }

    @Test
    public void setTrack_manualSubtitleChoiceCancelsThePendingAutomaticTarget() throws Exception {
        Sub automatic = Sub.create("自动字幕.ass", "/tmp/auto.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        Format autoTrack = new Format.Builder()
                .setLabel(automatic.getName())
                .setLanguage("zh-Hans")
                .setSampleMimeType(MimeTypes.TEXT_SSA)
                .build();
        List<List<Track>> selections = new ArrayList<>();
        PlayerManager manager = bareManager(textTracks(autoTrack, false), selections);
        List<Boolean> results = new ArrayList<>();
        RecordingTimer timer = (RecordingTimer) getField(manager, "subtitleApplyTimer");
        setField(manager, "pendingSubtitleApply", automatic);
        setField(manager, "subtitleApplyListener", (Consumer<Boolean>) results::add);
        timer.schedule(null, 15000L);

        Track manualOther = new Track(C.TRACK_TYPE_TEXT, "其它字幕", "ass").playerId(autoTrack.id);
        manualOther.setSelected(true);
        manager.setTrack(List.of(manualOther));

        assertTrue("用户手选别的字幕必须取消待确认自动目标", getField(manager, "pendingSubtitleApply") == null);
        assertTrue("用户手选后不能回报自动匹配结果", results.isEmpty());
        assertFalse("旧超时必须被撤销", timer.armed);
        assertEquals("用户选择本身仍要下发", 1, selections.size());

        manager.resolvePendingSubtitleApply();
        manager.onSubtitleApplyTimeout();
        assertEquals("晚到的自动目标不能覆盖手选", 1, selections.size());
        assertTrue(results.isEmpty());
    }

    @Test
    public void setTrack_disablingSubtitlesCancelsThePendingAutomaticTarget() throws Exception {
        Sub automatic = Sub.create("自动字幕.ass", "/tmp/auto.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        PlayerManager manager = bareManager(Tracks.EMPTY, new ArrayList<>());
        RecordingTimer timer = (RecordingTimer) getField(manager, "subtitleApplyTimer");
        setField(manager, "pendingSubtitleApply", automatic);
        setField(manager, "subtitleApplyListener", (Consumer<Boolean>) results -> { });
        timer.schedule(null, 15000L);

        manager.setTrack(List.of(Track.disabled(C.TRACK_TYPE_TEXT, "")));

        assertTrue("关闭字幕同样取消待确认自动目标", getField(manager, "pendingSubtitleApply") == null);
        assertFalse(timer.armed);
    }

    @Test
    public void setTrack_audioOnlySelectionKeepsThePendingSubtitleApply() throws Exception {
        Sub automatic = Sub.create("自动字幕.ass", "/tmp/auto.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        PlayerManager manager = bareManager(Tracks.EMPTY, new ArrayList<>());
        setField(manager, "pendingSubtitleApply", automatic);

        Track audio = new Track(C.TRACK_TYPE_AUDIO, "粤语", "aac").playerId("a1");
        audio.setSelected(true);
        manager.setTrack(List.of(audio));

        assertSame("选音轨不得中断字幕匹配", automatic, getField(manager, "pendingSubtitleApply"));
    }

    @Test
    public void automaticApplyStillConfirmsThroughTheInternalSelectionPath() throws Exception {
        Sub target = Sub.create("匹配字幕.ass", "/tmp/sub.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        Format text = new Format.Builder()
                .setLabel(target.getName())
                .setLanguage("zh-Hans")
                .setSampleMimeType(MimeTypes.TEXT_SSA)
                .build();
        List<List<Track>> selections = new ArrayList<>();
        PlayerManager manager = bareManager(textTracks(text, false), selections);
        List<Boolean> results = new ArrayList<>();
        setField(manager, "pendingSubtitleApply", target);
        setField(manager, "subtitleApplyListener", (Consumer<Boolean>) results::add);
        RecordingTimer timer = (RecordingTimer) getField(manager, "subtitleApplyTimer");

        manager.resolvePendingSubtitleApply();

        assertEquals("自动路径必须真的下发选轨", 1, selections.size());
        assertTrue("自动路径不能被自己的清理逻辑打断", timer.armed || !results.isEmpty());
        assertSame("第一阶段后仍需保留登记等待复核", target, getField(manager, "pendingSubtitleApply"));
    }

    @Test
    public void timeoutFixtureArmsTheRealRunnableAndCancelPreventsAnyLateReport() throws Exception {
        Sub target = Sub.create("匹配字幕.ass", "/tmp/sub.ass", "zh-Hans", MimeTypes.TEXT_SSA);
        Format text = new Format.Builder()
                .setLabel(target.getName())
                .setLanguage("zh-Hans")
                .setSampleMimeType(MimeTypes.TEXT_SSA)
                .build();
        PlayerManager manager = bareManager(textTracks(text, false), new ArrayList<>());
        List<Boolean> results = new ArrayList<>();
        setField(manager, "pendingSubtitleApply", target);
        setField(manager, "subtitleApplyListener", (Consumer<Boolean>) results::add);
        RecordingTimer timer = (RecordingTimer) getField(manager, "subtitleApplyTimer");

        manager.resolvePendingSubtitleApply();
        Runnable armed = timer.armedRunnable();
        assertTrue("第一阶段必须真实挂上到期任务", armed != null);

        manager.clearPendingSubtitleApply();
        assertTrue("取消必须走真实 cancel", timer.cancelled > 0);
        armed.run();

        assertTrue("取消之后晚到的到期任务不得回报任何结果", results.isEmpty());
    }

    @Test
    public void resetKnownDuration_dropsThePreviousMovieBeforeAnUnknownOrLiveItem() throws Exception {
        PlayerManager manager = bareManager(Tracks.EMPTY, new ArrayList<>());
        setField(manager, "lastKnownDurationMs", 123L * 60000L);
        assertEquals(123L * 60000L, manager.getLastKnownDurationMs());
        manager.resetKnownDuration();
        assertEquals("新媒体未知时长不得沿用上一片", 0L, manager.getLastKnownDurationMs());
    }

    private static Tracks textTracks(Format format, boolean selected) {
        return new Tracks(List.of(new Tracks.Group(new TrackGroup(format), true,
                new int[]{C.FORMAT_HANDLED}, new boolean[]{selected})));
    }

    // 绕过依赖 App/播放器服务的构造器，只为字幕回调注入一个可控的 engine 快照。
    private static PlayerManager bareManager(Tracks tracks, List<List<Track>> selections) throws Exception {
        Class<?> type = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field singleton = type.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        PlayerManager manager = (PlayerManager) type.getMethod("allocateInstance", Class.class).invoke(unsafe, PlayerManager.class);
        PlayerEngine engine = (PlayerEngine) java.lang.reflect.Proxy.newProxyInstance(
                PlayerEngine.class.getClassLoader(), new Class<?>[]{PlayerEngine.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getCurrentTracks")) return tracks;
                    if (method.getName().equals("setTrack")) {
                        selections.add((List<Track>) args[0]);
                        return null;
                    }
                    if (method.getName().equals("resetTrack")) return null;
                    throw new AssertionError("Unexpected engine call: " + method.getName());
                });
        setField(manager, "engine", engine);
        setField(manager, "subtitleApplyTimer", new RecordingTimer(new ArrayList<>(), selections));
        PlayerManager.Callback callback = (PlayerManager.Callback) java.lang.reflect.Proxy.newProxyInstance(
                PlayerManager.Callback.class.getClassLoader(), new Class<?>[]{PlayerManager.Callback.class},
                (proxy, method, args) -> null);
        setField(manager, "callback", callback);
        return manager;
    }

    /** 记录「选轨时超时是否已经挂好」的假调度器，避免依赖 App 主线程队列。 */
    private static final class RecordingTimer implements PlayerManager.SubtitleApplyTimer {

        private final List<Boolean> armedAtSelection;
        private final List<List<Track>> selections;
        private int cancelled;
        private boolean armed;
        private Runnable lastArmed;

        private Runnable armedRunnable() {
            return lastArmed;
        }

        private RecordingTimer(List<Boolean> armedAtSelection, List<List<Track>> selections) {
            this.armedAtSelection = armedAtSelection;
            this.selections = selections;
        }

        @Override
        public void schedule(Runnable runnable, long delayMs) {
            armedAtSelection.add(selections.isEmpty());
            armed = true;
            lastArmed = runnable;
        }

        @Override
        public void cancel(Runnable runnable) {
            cancelled++;
            armed = false;
        }
    }

    private static void setField(PlayerManager manager, String name, Object value) throws Exception {
        java.lang.reflect.Field field = PlayerManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    private static Object getField(PlayerManager manager, String name) throws Exception {
        java.lang.reflect.Field field = PlayerManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(manager);
    }

}
