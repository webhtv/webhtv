package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.AudioConfig;
import com.fongmi.android.tv.bean.ShortDramaConfig;
import com.fongmi.android.tv.gitcloud.GitCloudAccountStore;
import com.fongmi.android.tv.lab.LabActivity;
import com.fongmi.android.tv.playback.ViewingRecordSyncStore;
import com.fongmi.android.tv.plugin.PluginSourceConfig;
import com.fongmi.android.tv.remote.RemoteStore;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.databinding.ActivitySettingEnhanceBinding;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.setting.SiteNameStore;
import com.fongmi.android.tv.ui.activity.PluginManagerActivity;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.AudioSourceDialog;
import com.fongmi.android.tv.ui.dialog.ShortDramaSourceDialog;
import com.fongmi.android.tv.ui.dialog.CspWarmupDialog;
import com.fongmi.android.tv.ui.dialog.CustomCspDialog;
import com.fongmi.android.tv.ui.dialog.DebugLogDialog;
import com.fongmi.android.tv.ui.dialog.GitCloudDialog;
import com.fongmi.android.tv.ui.dialog.LoginStateLearnDialog;
import com.fongmi.android.tv.ui.dialog.ManagePageDialog;
import com.fongmi.android.tv.ui.dialog.OneKeySyncDialog;
import com.fongmi.android.tv.ui.dialog.RemoteTrustDialog;
import com.fongmi.android.tv.ui.dialog.ShellProxyDialog;
import com.fongmi.android.tv.ui.dialog.SiteHealthDialog;
import com.fongmi.android.tv.ui.dialog.SiteNameDialog;
import com.fongmi.android.tv.ui.dialog.ViewingRecordSyncDialog;
import com.fongmi.android.tv.ui.dialog.WebHomeExtensionDialog;
import com.fongmi.android.tv.ui.dialog.WebHomeThemeDialog;
import com.fongmi.android.tv.utils.LoginStateSync;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.fongmi.android.tv.web.ext.WebHomeExtensionRegistry;

public class SettingEnhanceActivity extends BaseActivity {

    private static final String URL_GITHUB = "https://github.com/YaChengMu/webhtv";
    private static final String URL_CNB = "https://cnb.cool/fish2018/ext";

    private ActivitySettingEnhanceBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingEnhanceActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_enable : R.string.setting_disable);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingEnhanceBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        reorderItems();
        mBinding.customCsp.requestFocus();
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.githubRepo.setOnClickListener(view -> openRepo(URL_GITHUB));
        mBinding.cnbRepo.setOnClickListener(view -> openRepo(URL_CNB));
        mBinding.lab.setOnClickListener(view -> LabActivity.start(this));
        mBinding.driveCheck.setOnClickListener(this::setDriveCheck);
        mBinding.siteName.setOnClickListener(this::setSiteName);
        mBinding.audioSource.setOnClickListener(this::setAudioSource);
        mBinding.shortDramaSource.setOnClickListener(this::setShortDramaSource);
        mBinding.debugLog.setOnClickListener(this::setDebugLog);
        mBinding.siteHealthSort.setOnClickListener(view -> SiteHealthDialog.show(this, this::setText));
        mBinding.siteHealthSort.setOnLongClickListener(this::clearSiteHealth);
        mBinding.webHomeExtension.setOnClickListener(view -> WebHomeExtensionDialog.show(this, this::setText));
        mBinding.webHomeExtension.setOnLongClickListener(this::clearWebHomeExtension);
        mBinding.webHomeTheme.setOnClickListener(view -> WebHomeThemeDialog.show(this, this::setText));
        mBinding.webHomeFullscreen.setOnClickListener(this::setWebHomeFullscreen);
        mBinding.cspWarmup.setOnClickListener(this::setCspWarmup);
        mBinding.playbackArtworkWall.setOnClickListener(this::setPlaybackArtworkWall);
        mBinding.playbackWebhook.setOnClickListener(view -> ViewingRecordSyncDialog.show(this, this::setText));
        mBinding.managePage.setOnClickListener(view -> ManagePageDialog.show(this));
        mBinding.remoteTrust.setOnClickListener(view -> RemoteTrustDialog.show(this, this::setText));
        mBinding.gitCloud.setOnClickListener(view -> GitCloudDialog.show(this, this::setText));
        mBinding.shellProxy.setOnClickListener(view -> ShellProxyDialog.show(this, this::setText));
        mBinding.shellProxy.setOnLongClickListener(v -> false);
        mBinding.shellProxyConfig.setVisibility(View.GONE);
        mBinding.customCsp.setOnClickListener(view -> PermissionUtil.requestFile(this, granted -> {
            if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) return;
            if (granted) CustomCspDialog.show(this, this::setText);
            else Notify.show(R.string.setting_custom_csp_permission_required);
        }));
        mBinding.loginState.setOnClickListener(view -> LoginStateLearnDialog.show(this, this::setText));
        mBinding.oneKeySync.setOnClickListener(v -> OneKeySyncDialog.create().show(this));
        mBinding.pluginManager.setOnClickListener(view -> PluginManagerActivity.start(this));
    }

    private void reorderItems() {
        ViewGroup parent = (ViewGroup) mBinding.customCsp.getParent();
        View[] order = {
                mBinding.lab,
                mBinding.customCsp,
                mBinding.webHomeExtension,
                mBinding.gitCloud,
                mBinding.remoteTrust,
                mBinding.oneKeySync,
                mBinding.loginState,
                mBinding.shellProxy,
                mBinding.shellProxyConfig,
                mBinding.managePage,
                mBinding.webHomeTheme,
                mBinding.webHomeFullscreen,
                mBinding.cspWarmup,
                mBinding.playbackArtworkWall,
                mBinding.driveCheck,
                mBinding.siteName,
                mBinding.audioSource,
                mBinding.shortDramaSource,
                mBinding.siteHealthSort,
                mBinding.debugLog,
                mBinding.playbackWebhook,
                mBinding.pluginManager
        };
        for (View view : order) parent.removeView(view);
        for (View view : order) parent.addView(view);
    }

    private void setText() {
        if (!canSetText()) return;
        safeSet("driveCheck", mBinding.driveCheckText, () -> getSwitch(Setting.isDriveCheck()));
        safeSet("siteName", mBinding.siteNameText, () -> getString(R.string.setting_site_name_summary, SiteNameStore.count()));
        safeSet("audioSource", mBinding.audioSourceText, () -> getSwitch(!AudioConfig.objectFrom(Setting.getAudioConfig()).getDisplayRules().isEmpty()));
        safeSet("shortDramaSource", mBinding.shortDramaSourceText, () -> getSwitch(!ShortDramaConfig.objectFrom(Setting.getShortDramaConfig()).getDisplayRules().isEmpty()));
        safeSet("debugLog", mBinding.debugLogText, () -> getSwitch(Setting.isDebugLog()));
        safeSet("siteHealthSort", mBinding.siteHealthSortText, this::getSiteHealthText);
        safeSet("webHomeExtension", mBinding.webHomeExtensionText, () -> {
            WebHomeExtensionRegistry.Snapshot webHomeExtension = WebHomeExtensionRegistry.get().snapshot();
            return getSwitch(Setting.isWebHomeExtension()) + " · " + webHomeExtension.readyCount + "/" + webHomeExtension.installedCount;
        });
        safeSet("webHomeTheme", mBinding.webHomeThemeText, () -> WebHomeThemeDialog.summary(this));
        safeSet("webHomeFullscreen", mBinding.webHomeFullscreenText, () -> getSwitch(Setting.isWebHomeFullscreen()));
        safeSet("cspWarmup", mBinding.cspWarmupText, this::getCspWarmupText);
        safeSet("playbackArtworkWall", mBinding.playbackArtworkWallText, () -> getSwitch(Setting.isPlaybackArtworkWall()));
        safeSet("playbackWebhook", mBinding.playbackWebhookText, () -> ViewingRecordSyncStore.summary(this));
        safeSet("managePage", mBinding.managePageText, () -> getString(R.string.manage_page_web));
        safeSet("remoteTrust", mBinding.remoteTrustText, () -> RemoteStore.summary(this));
        safeSet("gitCloud", mBinding.gitCloudText, () -> getString(R.string.git_cloud_account_count, GitCloudAccountStore.list().size()));
        safeSet("pluginManager", mBinding.pluginManagerText, () -> getString(R.string.plugin_manager_entry_count, PluginSourceConfig.get().entries().size()));
        setShellProxyText();
        setCustomCspText();
        safeSet("loginState", mBinding.loginStateText, () -> {
            int learned = LoginStateSync.learnedCount();
            int pending = LoginStateSync.pendingPaths().size();
            return getString(LoginStateSync.hasLearningSnapshot() ? R.string.login_state_learning_count : R.string.login_state_count, learned, pending);
        });
    }

    private boolean canSetText() {
        return mBinding != null && !isFinishing() && !isDestroyed();
    }

    private void setShellProxyText() {
        safeRun("shellProxy", () -> {
            int count = ProxySetting.count();
            setSummary(mBinding.shellProxyText, getSwitch(Setting.isShellProxy()) + " · " + getString(R.string.setting_proxy_rule_count, count));
            setSummary(mBinding.shellProxyConfigText, getString(R.string.setting_proxy_rule_count, count));
        }, () -> {
            setError(mBinding.shellProxyText);
            setError(mBinding.shellProxyConfigText);
        });
    }

    private void setCustomCspText() {
        safeRun("customCsp", () -> {
            CustomCspSetting.Status status = CustomCspSetting.status();
            if (!status.available()) {
                setError(mBinding.customCspText);
                return;
            }
            CustomCspSetting.Count count = status.count();
            setSummary(mBinding.customCspText, getSwitch(status.enabled()) + " · " + getString(R.string.setting_custom_csp_count, count.active(), count.enabled()));
        }, () -> setError(mBinding.customCspText));
    }

    private void safeSet(String name, TextView view, TextSupplier supplier) {
        safeRun(name, () -> setSummary(view, supplier.get()), () -> setError(view));
    }

    /**
     * 写入取值摘要，并把颜色复位到常规壁纸前景色。
     *
     * <p>复位这一步是必需的：{@link #setError} 会把同一个 TextView 染成错误色，而这些摘要
     * 全部**复用同一个控件**——不复位的话，某一行失败一次之后，即使后续刷新成功，
     * 它也会一直显示为红色。
     */
    private void setSummary(TextView view, CharSequence text) {
        if (view == null) return;
        view.setText(text);
        view.setTextColor(themeColor(R.attr.webhtvColorOnWallpaper));
        // 撤掉错误底材：错误态是"临时"的，刷新成功必须完全回到原样（这些 TextView 原本没有背景）。
        view.setBackground(null);
    }

    /**
     * 内联错误：设计文档 §9.9 要求错误态有「错误色」。
     *
     * <p>不能直接用 {@code ?attr/colorError}：那是跟随调色板的角色，浅色表的深红
     * （{@code #B3261E}）压在深色壁纸行色带上实测只有 1.06:1，完全不可读。这里用与壁纸
     * 同族的 {@code ?attr/webhtvColorOnWallpaperError}（同一底材实测 4.05–4.50:1）。
     *
     * <p>底材：错误色直接画在行色带上只有 4.05–4.50:1，略低于正文门槛 4.5:1，而继续调色无解
     * （要达标就得接近纯白，错误色随之失去语义）。因此这里同时换上一块**实心深底**
     * （{@code @drawable/shape_error_value}），核算为 8.84:1，稳过门槛。
     *
     * <p>如实记录：该取值在行色带最亮处是 4.05:1，**略低于正文门槛 4.5:1**。这是底材
     * （中亮度壁纸）决定的取舍，理由与替代方案见 {@code values/webhtv_tokens.xml} 的同名条目
     * 与 docs/TV-ERROR-STATE-20261012.md §7。
     */
    private void setError(TextView view) {
        if (view == null) return;
        view.setText(R.string.error_config_get);
        view.setTextColor(themeColor(R.attr.webhtvColorOnWallpaperError));
        view.setBackgroundResource(R.drawable.shape_error_value);
    }

    /** 解析主题属性里的颜色值；未赋值时返回 0（透明）。 */
    private int themeColor(int attr) {
        TypedValue value = new TypedValue();
        return getTheme().resolveAttribute(attr, value, true) ? value.data : 0;
    }

    private void safeRun(String name, Runnable action, Runnable fallback) {
        try {
            action.run();
        } catch (Throwable e) {
            SpiderDebug.log("enhance", "summary failed item=%s error=%s", name, e.toString());
            if (fallback == null) return;
            try {
                fallback.run();
            } catch (Throwable ignored) {
            }
        }
    }

    private interface TextSupplier {
        CharSequence get();
    }

    private void setDriveCheck(View view) {
        Setting.putDriveCheck(!Setting.isDriveCheck());
        setSummary(mBinding.driveCheckText, getSwitch(Setting.isDriveCheck()));
    }

    private void setDebugLog(View view) {
        Setting.putDebugLog(!Setting.isDebugLog());
        setSummary(mBinding.debugLogText, getSwitch(Setting.isDebugLog()));
        if (!Setting.isDebugLog()) return;
        DebugLogDialog.show(this);
    }

    private void setSiteName(View view) {
        SiteNameDialog.create(this).onChanged(this::setText).show();
    }

    private void setAudioSource(View view) {
        AudioSourceDialog.create(this).onDismiss(this::setText).show();
    }

    private void setShortDramaSource(View view) {
        ShortDramaSourceDialog.create(this).onDismiss(this::setText).show();
    }

    private String getSiteHealthText() {
        SiteHealthStore.Summary summary = SiteHealthStore.summary();
        String state = getSwitch(Setting.isSiteHealthSort());
        if (summary.siteCount <= 0) return state;
        return state + " · " + getString(R.string.site_health_report_setting_summary, summary.siteCount, summary.sampleCount);
    }

    private void setPlaybackArtworkWall(View view) {
        Setting.putPlaybackArtworkWall(!Setting.isPlaybackArtworkWall());
        setSummary(mBinding.playbackArtworkWallText, getSwitch(Setting.isPlaybackArtworkWall()));
    }

    private void setWebHomeFullscreen(View view) {
        Setting.putWebHomeFullscreen(!Setting.isWebHomeFullscreen());
        setSummary(mBinding.webHomeFullscreenText, getSwitch(Setting.isWebHomeFullscreen()));
    }

    private void setCspWarmup(View view) {
        CspWarmupDialog.show(this, this::setText);
    }

    private String getCspWarmupText() {
        int mode = Setting.getCspWarmupMode();
        if (mode == Setting.CSP_WARMUP_CUSTOM) return getString(R.string.setting_csp_warmup_custom_count, Setting.getCspWarmupSites().size());
        return getString(mode == Setting.CSP_WARMUP_DEFAULT ? R.string.setting_csp_warmup_default : R.string.setting_disable);
    }

    private boolean clearSiteHealth(View view) {
        SiteHealthStore.clear();
        Notify.show(R.string.site_health_clear_done);
        return true;
    }

    private boolean clearWebHomeExtension(View view) {
        WebHomeExtensionRegistry.get().clear();
        Notify.show(R.string.web_home_extension_clear_done);
        return true;
    }

    private void openRepo(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Notify.show(R.string.manage_page_no_browser);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setText();
    }
}
