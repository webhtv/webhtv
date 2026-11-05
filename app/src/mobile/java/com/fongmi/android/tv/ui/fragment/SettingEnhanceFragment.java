package com.fongmi.android.tv.ui.fragment;

import android.content.Intent;
import android.net.Uri;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.AudioConfig;
import com.fongmi.android.tv.bean.ComicSourceConfig;
import com.fongmi.android.tv.bean.NovelSourceConfig;
import com.fongmi.android.tv.bean.ShortDramaConfig;
import com.fongmi.android.tv.gitcloud.GitCloudAccountStore;
import com.fongmi.android.tv.lab.LabActivity;
import com.fongmi.android.tv.playback.ViewingRecordSyncStore;
import com.fongmi.android.tv.plugin.PluginSourceConfig;
import com.fongmi.android.tv.remote.RemoteStore;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.databinding.FragmentSettingEnhanceBinding;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.setting.SiteNameStore;
import com.fongmi.android.tv.ui.activity.FileActivity;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.activity.PluginManagerActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.AudioSourceDialog;
import com.fongmi.android.tv.ui.dialog.ComicSourceDialog;
import com.fongmi.android.tv.ui.dialog.NovelSourceDialog;
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
import com.google.gson.JsonObject;

public class SettingEnhanceFragment extends BaseFragment {

    private static final String URL_GITHUB = "https://github.com/YaChengMu/webhtv";
    private static final String URL_CNB = "https://cnb.cool/fish2018/ext";

    private FragmentSettingEnhanceBinding mBinding;

    public static SettingEnhanceFragment newInstance() {
        return new SettingEnhanceFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_enable : R.string.setting_disable);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingEnhanceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        reorderItems();
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.githubRepo) {
                openRepo(URL_GITHUB);
                return true;
            }
            if (item.getItemId() == R.id.cnbRepo) {
                openRepo(URL_CNB);
                return true;
            }
            return false;
        });
        mBinding.lab.setOnClickListener(view -> LabActivity.start(requireContext()));
        mBinding.driveCheck.setOnClickListener(this::setDriveCheck);
        mBinding.siteName.setOnClickListener(this::setSiteName);
        mBinding.localReader.setOnClickListener(this::setLocalReader);
        mBinding.novelSource.setOnClickListener(this::setNovelSource);
        mBinding.comicSource.setOnClickListener(this::setComicSource);
        mBinding.audioSource.setOnClickListener(this::setAudioSource);
        mBinding.shortDramaSource.setOnClickListener(this::setShortDramaSource);
        mBinding.debugLog.setOnClickListener(this::setDebugLog);
        mBinding.siteHealthSort.setOnClickListener(view -> SiteHealthDialog.show(this, this::setText));
        mBinding.siteHealthSort.setOnLongClickListener(this::clearSiteHealth);
        mBinding.webHomeExtension.setOnClickListener(view -> WebHomeExtensionDialog.show(this, this::setText));
        mBinding.webHomeExtension.setOnLongClickListener(this::clearWebHomeExtension);
        mBinding.webHomeTheme.setOnClickListener(view -> WebHomeThemeDialog.show(requireActivity(), this::setText));
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
            if (!isAdded() || isStateSaved() || getActivity() == null) return;
            if (granted) CustomCspDialog.show(this, this::setText);
            else Notify.show(R.string.setting_custom_csp_permission_required);
        }));
        mBinding.loginState.setOnClickListener(view -> LoginStateLearnDialog.show(this, this::setText));
        mBinding.oneKeySync.setOnClickListener(v -> OneKeySyncDialog.create().show(requireActivity()));
        mBinding.pluginManager.setOnClickListener(view -> PluginManagerActivity.start(requireActivity()));
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
                mBinding.localReader,
                mBinding.novelSource,
                mBinding.comicSource,
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
        safeSet("novelSource", mBinding.novelSourceText, () -> getSwitch(!NovelSourceConfig.get().getDisplayRules().isEmpty()));
        safeSet("comicSource", mBinding.comicSourceText, () -> getSwitch(!ComicSourceConfig.get().getDisplayRules().isEmpty()));
        safeSet("audioSource", mBinding.audioSourceText, () -> getSwitch(!AudioConfig.objectFrom(Setting.getAudioConfig()).getDisplayRules().isEmpty()));
        safeSet("shortDramaSource", mBinding.shortDramaSourceText, () -> getSwitch(!ShortDramaConfig.objectFrom(Setting.getShortDramaConfig()).getDisplayRules().isEmpty()));
        safeSet("debugLog", mBinding.debugLogText, () -> getSwitch(Setting.isDebugLog()));
        safeSet("siteHealthSort", mBinding.siteHealthSortText, this::getSiteHealthText);
        safeSet("webHomeExtension", mBinding.webHomeExtensionText, () -> {
            WebHomeExtensionRegistry.Snapshot webHomeExtension = WebHomeExtensionRegistry.get().snapshot();
            return getSwitch(Setting.isWebHomeExtension()) + " · " + webHomeExtension.readyCount + "/" + webHomeExtension.installedCount;
        });
        safeSet("webHomeTheme", mBinding.webHomeThemeText, () -> WebHomeThemeDialog.summary(requireContext()));
        safeSet("webHomeFullscreen", mBinding.webHomeFullscreenText, () -> getSwitch(Setting.isWebHomeFullscreen()));
        safeSet("cspWarmup", mBinding.cspWarmupText, this::getCspWarmupText);
        safeSet("playbackArtworkWall", mBinding.playbackArtworkWallText, () -> getSwitch(Setting.isPlaybackArtworkWall()));
        safeSet("playbackWebhook", mBinding.playbackWebhookText, () -> ViewingRecordSyncStore.summary(requireContext()));
        safeSet("managePage", mBinding.managePageText, () -> getString(R.string.manage_page_web));
        safeSet("remoteTrust", mBinding.remoteTrustText, () -> RemoteStore.summary(requireContext()));
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
        return mBinding != null && isAdded() && getContext() != null;
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
     * 写入取值摘要，并把颜色与底材复位到常规状态。
     *
     * <p>复位这一步是必需的：{@link #setError} 会把同一个 TextView 染成错误色并换上一块深底，
     * 而这些摘要全部**复用同一个控件**——不复位的话，某一行失败一次之后，即使后续刷新成功，
     * 它也会一直显示为红字加深底。
     */
    private void setSummary(TextView view, CharSequence text) {
        if (view == null) return;
        view.setText(text);
        view.setTextColor(themeColor(R.attr.webhtvColorOnWallpaper));
        // 这些 TextView 在布局里原本都没有背景，所以置空就是"恢复原样"。
        view.setBackground(null);
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

    /**
     * 内联错误：设计文档 §9.9 要求错误态有「错误色」。
     *
     * <p>不能直接用 {@code ?attr/colorError}：那是跟随调色板的角色，浅色表的深红
     * （{@code #B3261E}）压在深色壁纸行色带上实测只有约 1.06:1，完全不可读。这里用与壁纸
     * 同族的 {@code ?attr/webhtvColorOnWallpaperError}，并同时换上一块**实心深底**
     * （{@code @drawable/shape_error_value}）——错误色直接画在行色带上只有 4.05–4.50:1，
     * 略低于正文门槛 4.5:1；实心底上是 8.84:1，稳过门槛。底材是实心色，因此这个比值与
     * flavor 无关，与电视端共用同一份 drawable 与同一个角色。
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
        return requireContext().getTheme().resolveAttribute(attr, value, true) ? value.data : 0;
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
        SiteNameDialog.create(requireActivity()).onChanged(this::setText).show();
    }

    private void setLocalReader(View view) {
        Intent intent = new Intent(requireContext(), FileActivity.class);
        intent.putExtra("read_mode", true);
        startActivity(intent);
    }

    private void setAudioSource(View view) {
        AudioSourceDialog.create(requireActivity()).onDismiss(this::setText).show();
    }

    private void setNovelSource(View view) {
        NovelSourceDialog.create(requireActivity()).onDismiss(this::setText).show();
    }

    private void setComicSource(View view) {
        ComicSourceDialog.create(requireActivity()).onDismiss(this::setText).show();
    }

    private void setShortDramaSource(View view) {
        ShortDramaSourceDialog.create(requireActivity()).onDismiss(this::setText).show();
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
        if (requireActivity() instanceof HomeActivity activity) {
            if (!Setting.isWebHomeFullscreen()) {
                JsonObject object = new JsonObject();
                object.addProperty("mode", "normal");
                activity.setWebHomeChrome(object);
            }
            activity.refreshWebHomeChromeState();
        }
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
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) setText();
    }

    @Override
    public void onResume() {
        super.onResume();
        setText();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
