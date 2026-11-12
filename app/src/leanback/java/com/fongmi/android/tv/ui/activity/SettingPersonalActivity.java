package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySettingPersonalBinding;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.following.FollowingScheduler;
import com.fongmi.android.tv.following.FollowingSettings;
import com.fongmi.android.tv.setting.AppBranding;
import com.fongmi.android.tv.setting.AutoBackupPolicy;
import com.fongmi.android.tv.setting.GroupRuleConfig;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.GroupRuleDialog;
import com.fongmi.android.tv.ui.dialog.HomeMenuKeyDialog;
import com.fongmi.android.tv.ui.dialog.SpeedSettingDialog;
import com.fongmi.android.tv.ui.dialog.SliderNumberDialog;
import com.fongmi.android.tv.ui.helper.TouchOptimizationHelper;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.Locale;

public class SettingPersonalActivity extends BaseActivity {

    /** 本页可聚焦的条目，顺序必须与布局从上到下一致；只用于上下环形跳转。 */
    private static final int[] FOCUS_ROWS = {
            R.id.following,
            R.id.autoBackup,
            R.id.fullscreenMenuKey,
            R.id.homeMenuKey,
            R.id.playBackToDetail,
            R.id.playbackOverlay,
            R.id.episodeHistory,
            R.id.globalHistory,
            R.id.interfaceFailover,
            R.id.playSpeed,
            R.id.groupRule,
            R.id.searchThread,
            R.id.searchUi,
            R.id.searchResultSort,
            R.id.resetApp,
            R.id.appBranding,
            R.id.touchOptimization
    };

    private ActivitySettingPersonalBinding mBinding;
    private String[] fullscreenMenuKey;
    private String[] homeMenuKey;
    private String[] searchUi;
    private String[] searchColumn;
    private String[] searchResultSort;
    private String[] globalHistoryMode;
    private String[] interfaceFailoverMode;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingPersonalActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingPersonalBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.following.requestFocus();
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.following.setOnClickListener(this::setFollowing);
        mBinding.autoBackup.setOnClickListener(this::setAutoBackup);
        mBinding.fullscreenMenuKey.setOnClickListener(this::setFullscreenMenuKey);
        mBinding.homeMenuKey.setOnClickListener(this::setHomeMenuKey);
        mBinding.playBackToDetail.setOnClickListener(this::setPlayBackToDetail);
        mBinding.playbackOverlay.setOnClickListener(this::setPlaybackOverlay);
        mBinding.episodeHistory.setOnClickListener(this::setEpisodeHistory);
        mBinding.globalHistory.setOnClickListener(this::setGlobalHistory);
        mBinding.interfaceFailover.setOnClickListener(this::setInterfaceFailover);
        mBinding.playSpeed.setOnClickListener(this::setPlaySpeed);
        mBinding.groupRule.setOnClickListener(this::setGroupRule);
        mBinding.searchThread.setOnClickListener(this::setSearchThread);
        mBinding.searchUi.setOnClickListener(this::setSearchUi);
        mBinding.searchResultSort.setOnClickListener(this::setSearchResultSort);
        // mBinding.searchColumn.setOnClickListener(this::setSearchColumn); // 在搜索页面切换更方便
        mBinding.appBranding.setOnClickListener(this::startAppBranding);
        mBinding.resetApp.setOnClickListener(this::showResetAppDialog);
        mBinding.touchOptimization.setOnClickListener(this::setTouchOptimization);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setText();
    }

    /** 上下到边界时在首尾之间环绕，避免焦点跑到页面外或被别的控件抢走。 */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && wrapVerticalFocus(event.getKeyCode())) return true;
        return super.dispatchKeyEvent(event);
    }

    private boolean wrapVerticalFocus(int keyCode) {
        View focus = getCurrentFocus();
        int index = focus == null ? -1 : indexOf(focus.getId());
        if (index < 0) return false;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && index == 0) return focusRow(FOCUS_ROWS.length - 1);
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && index == FOCUS_ROWS.length - 1) return focusRow(0);
        return false;
    }

    private int indexOf(int id) {
        for (int i = 0; i < FOCUS_ROWS.length; i++) if (FOCUS_ROWS[i] == id) return i;
        return -1;
    }

    private boolean focusRow(int index) {
        View view = findViewById(FOCUS_ROWS[index]);
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        return view.requestFocus();
    }

    private void setText() {
        mBinding.followingText.setText(getSwitch(FollowingSettings.isEnabled()));
        mBinding.autoBackupText.setText(getSwitch(isAutoBackupEnabled()));
        mBinding.fullscreenMenuKeyText.setText((fullscreenMenuKey = getResources().getStringArray(R.array.select_fullscreen_menu_key))[Setting.getFullscreenMenuKey()]);
        mBinding.homeMenuKeyText.setText((homeMenuKey = getResources().getStringArray(R.array.select_home_menu_key))[Setting.getHomeMenuKey()]);
        mBinding.playBackToDetailText.setText(getSwitch(Setting.isPlayBackToDetail()));
        mBinding.playbackOverlayText.setText(getSwitch(Setting.isPlaybackOverlayEnabled()));
        mBinding.episodeHistoryText.setText(getSwitch(Setting.isEpisodeHistory()));
        mBinding.globalHistoryText.setText((globalHistoryMode = getResources().getStringArray(R.array.select_global_history_mode))[Setting.getGlobalHistoryMode()]);
        mBinding.interfaceFailoverText.setText((interfaceFailoverMode = getResources().getStringArray(R.array.select_interface_failover_mode))[Setting.getInterfaceFailoverMode()]);
        mBinding.playSpeedText.setText(getSpeedText(PlayerSetting.getDefaultSpeed()));
        mBinding.groupRuleText.setText(getString(R.string.setting_group_rule_summary, GroupRuleConfig.enabledCount(), GroupRuleConfig.totalCount()));
        mBinding.searchThreadText.setText(String.valueOf(Setting.getSearchThread()));
        mBinding.searchUiText.setText((searchUi = getResources().getStringArray(R.array.select_search_ui))[Setting.getSearchUi()]);
        mBinding.searchResultSortText.setText((searchResultSort = getResources().getStringArray(R.array.select_search_result_sort))[Setting.getSearchResultSort()]);
        // mBinding.searchColumnText.setText(getSearchColumnText()); // 在搜索页面切换更方便
        mBinding.appBrandingText.setText(AppBranding.getSummary(this));
        mBinding.touchOptimizationText.setText(getSwitch(Setting.isTouchOptimized()));
    }

    private String getSearchColumnText() {
        searchColumn = getResources().getStringArray(R.array.select_search_column);
        int column = Setting.getSearchColumn();
        if (column >= 0 && column < searchColumn.length) {
            return searchColumn[column];
        }
        return searchColumn[0]; // 默认返回第一项
    }

    private String getSpeedText(float speed) {
        return String.format(Locale.US, "%.2f", speed);
    }

    private void setFollowing(View view) {
        boolean enabled = !FollowingSettings.isEnabled();
        FollowingSettings.setEnabled(enabled);
        if (enabled) {
            FollowingScheduler.ensurePeriodic(this);
            FollowingScheduler.enqueueDueNow(this);
        } else {
            FollowingScheduler.cancelAll(this);
        }
        ConfigEvent.common();
        RefreshEvent.home();
        setText();
    }

    private void setAutoBackup(View view) {
        if (isAutoBackupEnabled()) {
            Setting.putAutoBackup(false);
            setText();
            return;
        }
        PermissionUtil.requestFile(this, allGranted -> {
            if (!allGranted) {
                Notify.show(R.string.backup_permission_denied);
                return;
            }
            Setting.putAutoBackup(true);
            setText();
        });
    }

    private boolean isAutoBackupEnabled() {
        return AutoBackupPolicy.isEffective(Setting.isAutoBackup(), Setting.hasFileAccess());
    }

    private void setFullscreenMenuKey(View view) {
        Setting.putFullscreenMenuKey((Setting.getFullscreenMenuKey() + 1) % fullscreenMenuKey.length);
        setText();
    }

    private void setHomeMenuKey(View view) {
        HomeMenuKeyDialog.show(this, this::setText);
    }

    private void setPlaybackOverlay(View view) {
        Setting.putPlaybackOverlayEnabled(!Setting.isPlaybackOverlayEnabled());
        setText();
    }

    private void setPlayBackToDetail(View view) {
        Setting.putPlayBackToDetail(!Setting.isPlayBackToDetail());
        setText();
    }

    private void setEpisodeHistory(View view) {
        Setting.putEpisodeHistory(!Setting.isEpisodeHistory());
        setText();
    }

    private void setGlobalHistory(View view) {
        Setting.putGlobalHistoryMode((Setting.getGlobalHistoryMode() + 1) % globalHistoryMode.length);
        RefreshEvent.history();
        setText();
    }

    private void setInterfaceFailover(View view) {
        Setting.putInterfaceFailoverMode((Setting.getInterfaceFailoverMode() + 1) % interfaceFailoverMode.length);
        setText();
    }

    private void setPlaySpeed(View view) {
        SpeedSettingDialog.show(this, R.string.setting_play_speed, PlayerSetting.getDefaultSpeed(), 0.5f, 5f, 0.25f, value -> {
            PlayerSetting.putDefaultSpeed(value);
            setText();
        });
    }

    private void setGroupRule(View view) {
        GroupRuleDialog.create(this).onChanged(this::setText).show();
    }

    private void setSearchThread(View view) {
        SliderNumberDialog.show(this, R.string.setting_search_thread, Setting.getSearchThread(), 1, 100, value -> {
            Setting.putSearchThread(value);
            setText();
        });
    }

    private void setSearchUi(View view) {
        Setting.putSearchUi((Setting.getSearchUi() + 1) % searchUi.length);
        setText();
    }

    private void setSearchResultSort(View view) {
        Setting.putSearchResultSort((Setting.getSearchResultSort() + 1) % searchResultSort.length);
        setText();
    }

    private void showResetAppDialog(View view) {
        new WebHtvAlertDialogBuilder(this)
                .setTitle(R.string.dialog_reset_app)
                .setMessage(R.string.dialog_reset_app_data)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> resetApp())
                .show();
    }

    private void startAppBranding(View view) {
        AppBrandingActivity.start(this);
    }

    private void setTouchOptimization(View view) {
        boolean enabled = !Setting.isTouchOptimized();
        Setting.putTouchOptimized(enabled);
        mBinding.touchOptimizationText.setText(getSwitch(enabled));
        TouchOptimizationHelper.sync(getWindow().getDecorView());
    }

    private void resetApp() {
        if (!Util.resetApp()) Notify.show(R.string.reset_app_failed);
    }

    // 在搜索页面切换更方便，此处不再提供设置入口
    /*
    private void setSearchColumn(View view) {
        int current = Setting.getSearchColumn();
        int next = (current + 1) % 3; // 0: 自适应, 1: 1列, 2: 默认5列
        Setting.putSearchColumn(next);
        setText();
    }
    */

}
