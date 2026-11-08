package com.fongmi.android.tv.ui.player;

import android.view.MotionEvent;
import android.view.View;

import com.fongmi.android.tv.player.PlayerManager;

public interface VodPlayerUiHost {

    PlayerManager player();

    String osdTitle();

    default boolean suppressPersistentOsd() {
        return false;
    }

    /**
     * 当前是否全屏播放。媒体信息徽标只在全屏画面出现，因为半屏 / 小窗里它会把
     * 本来就拥挤的控件再挤一层；默认 false 让未显式覆盖的宿主（例如详情页内嵌小窗）
     * 天然不显示。
     */
    default boolean isFullscreen() {
        return false;
    }

    default boolean restoreDiagnosticsOnStart() {
        return true;
    }

    default void showDanmakuDialog() {
    }

    default void showPlayerInfoDialog() {
    }

    default void onDanmakuStateChanged(boolean show) {
    }

    default void playPrevious() {
    }

    default void playNext() {
    }

    default void showQuality() {
    }

    default void cycleParse() {
    }

    default void changeSpeed() {
    }

    default boolean resetSpeed() {
        return false;
    }

    default void cycleScale() {
    }

    default void showLut() {
    }

    default void refreshPlayback() {
    }

    default void changeSource() {
    }

    default boolean openSourceSearch() {
        return false;
    }

    default void toggleRepeat() {
    }

    default void showDisplay() {
    }

    default void toggleDecode() {
    }

    default void togglePlayParams() {
    }

    default void showCodecCapability() {
    }

    default void showTrack(View view) {
    }

    default boolean showSubtitle() {
        return false;
    }

    default void setOpeningFromPosition() {
    }

    default boolean resetOpening() {
        return false;
    }

    default void setEndingFromPosition() {
    }

    default boolean resetEnding() {
        return false;
    }

    default void toggleDanmaku() {
    }

    default boolean onDanmakuLongClick() {
        return false;
    }

    default void showChapter() {
    }

    default boolean showPlayerChoice() {
        return false;
    }

    default void showEpisodes() {
    }

    default void toggleFullscreen() {
    }

    default void cast() {
    }

    default void showMediaInfo() {
    }

    default boolean onControlsTouch(View view, MotionEvent event) {
        return false;
    }
}
