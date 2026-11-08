package com.fongmi.android.tv.ui.fragment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.FragmentSettingBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.setting.ConfigSyncPolicy;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.AboutDialog;
import com.fongmi.android.tv.ui.dialog.AppearanceDialog;
import com.fongmi.android.tv.ui.dialog.ChoiceDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.RestoreDialog;
import com.fongmi.android.tv.ui.dialog.BackupProgressDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.AppVersion;
import com.fongmi.android.tv.cache.CacheCenter;
import com.fongmi.android.tv.cache.CachePolicyStore;
import com.fongmi.android.tv.ui.dialog.CacheManagementDialog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class SettingFragment extends BaseFragment implements ConfigListener, SiteListener, LiveListener {

    private FragmentSettingBinding mBinding;

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    private HomeActivity getRoot() {
        return (HomeActivity) requireActivity();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        setWallText();
        mBinding.versionText.setText(AppVersion.fullName());
        setOtherText();
        setCacheText();
    }

    private void setOtherText() {
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.incognitoText.setText(getSwitch(Setting.isIncognito()));
    }

    private void setCacheText() {
        if (!CachePolicyStore.isManagementEnabled()) {
            FileUtil.getCacheSize(new Callback() {
                @Override
                public void success(String result) {
                    if (mBinding != null && isAdded()) mBinding.cacheText.setText(result);
                }
            });
            return;
        }
        mBinding.cacheText.setText(R.string.cache_management_scanning);
        CacheCenter.get().requestSnapshot(false, snapshot -> {
            if (mBinding != null && isAdded()) {
                String total = FileUtil.byteCountToDisplaySize(snapshot.totalBytes());
                long quota = snapshot.systemQuotaBytes();
                mBinding.cacheText.setText(quota > 0
                        ? getString(R.string.cache_management_inline, total, FileUtil.byteCountToDisplaySize(quota))
                        : total);
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.wall.setOnClickListener(this::onWall);
        mBinding.appearance.setOnClickListener(this::onAppearance);
        mBinding.cache.setOnClickListener(this::onCacheClear);
        // The plain click restores the pre-split one-key clear; the panel moved to the long press.
        // The row stays single-line by user request, so §15.2's "not long-press only" intent is a
        // recorded deviation rather than a printed hint (docs/CACHE-MGMT-01-cache-management-design.md).
        mBinding.cache.setOnLongClickListener(this::onCacheManage);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.enhance.setOnClickListener(this::onEnhance);
        mBinding.tmdb.setOnClickListener(this::onTmdb);
        mBinding.ai.setOnClickListener(this::onAi);
        mBinding.personal.setOnClickListener(this::onPersonal);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.ad.setOnClickListener(this::onAd);
        mBinding.danmaku.setOnClickListener(this::onDanmaku);
        mBinding.subtitle.setOnClickListener(this::onSubtitle);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.incognito.setOnClickListener(this::setIncognito);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.wallDefault.setOnClickListener(this::setWallDefault);
        mBinding.wallRefresh.setOnClickListener(this::setWallRefresh);
        mBinding.wallRefresh.setOnLongClickListener(this::onWallHistory);
    }

    @Override
    public void setConfig(Config config) {
        if (config == null) return;
        String url = config.getUrl();
        if (!TextUtils.isEmpty(url) && url.startsWith("file")) {
            requireView().post(() -> PermissionUtil.requestFile(this, allGranted -> load(config)));
        } else {
            load(config);
        }
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                String previousVodUrl = VodConfig.getUrl();
                VodConfig.load(config, getCallback());
                if (ConfigSyncPolicy.shouldSyncLive(previousVodUrl, LiveConfig.getUrl())) {
                    Config liveConfig = AppDatabase.get().getConfigDao().find(config.getUrl(), 1);
                    if (liveConfig != null) LiveConfig.load(liveConfig, new Callback());
                }
                break;
            case 1:
                LiveConfig.load(config, getCallback());
                break;
            case 2:
                Setting.putWall(0);
                WallConfig.load(config, getCallback());
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    private void onVod(View view) {
        HistoryDialog.create().vod().manage().show(this);
    }

    private void onLive(View view) {
        HistoryDialog.create().live().manage().show(this);
    }

    private void onWall(View view) {
        HistoryDialog.create().wall().manage().show(this);
    }

    private void onVodHome(View view) {
        SiteDialog.create().search().change().show(this);
    }

    private void onLiveHome(View view) {
        LiveDialog.show(this);
    }

    private void onVodHistory(View view) {
        HistoryDialog.create().vod().show(this);
    }

    private void onLiveHistory(View view) {
        HistoryDialog.create().live().show(this);
    }

    private void onPlayer(View view) {
        getRoot().change(2);
    }

    private void onAd(View view) {
        getRoot().change(9);
    }

    private void onDanmaku(View view) {
        getRoot().change(4);
    }

    private void onSubtitle(View view) {
        getRoot().change(6);
    }

    private void onEnhance(View view) {
        getRoot().change(3);
    }

    private void onTmdb(View view) {
        getRoot().change(7);
    }

    private void onAi(View view) {
        getRoot().change(8);
    }

    private void onPersonal(View view) {
        getRoot().change(5);
    }

    private void onAppearance(View view) {
        AppearanceDialog.show(this);
    }


    private void onVersion(View view) {
        AboutDialog.show(requireActivity(), () -> Updater.create().force().start(requireActivity()));
    }

    private void setWallDefault(View view) {
        Setting.putWall(Setting.nextDefaultWall());
        Setting.putWallType(0);
        setWallText();
        ConfigEvent.wall();
    }

    private void setWallRefresh(View view) {
        Setting.putWall(0);
        WallConfig.get().load(getCallback());
    }

    private boolean onWallHistory(View view) {
        HistoryDialog.create().wall().show(this);
        return true;
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.incognitoText.setText(getSwitch(Setting.isIncognito()));
    }

    private void setDoh(View view) {
        ChoiceDialog.showSingle(this, R.string.setting_doh, getDohList(), getDohIndex(), which -> {
            setDoh(VodConfig.get().getDoh().get(which));
        });
    }

    private void setDoh(Doh doh) {
        OkHttp.dns().setDoh(doh);
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
    }

    /**
     * The settings row's plain click: clear every cache at once, the way this row behaved before it
     * opened the cache management panel.
     *
     * <p>The full clean runs in the background and reports itself through a notification without
     * opening the panel; the row re-reads itself when the cleanup publishes its change. With cache
     * management switched off the legacy {@link FileUtil#clearCache} path is kept, because the panel
     * and its cleanup engine belong to the feature that switch turns off.</p>
     */
    private void onCacheClear(View view) {
        if (!CachePolicyStore.isManagementEnabled()) {
            FileUtil.clearCache(new Callback() {
                @Override
                public void success() {
                    setCacheText();
                }
            });
            return;
        }
        CacheManagementDialog.cleanEverything();
    }

    /**
     * The settings row's long press: open the cache management panel (graded cleanup, module
     * limits, auto cleanup and the running state).
     *
     * <p>With the plain click now clearing everything this is the panel's only entry. The row stays
     * single-line by user request, so the gesture is not printed under the title and the panel is
     * reached by the long press alone (deviation recorded in
     * docs/CACHE-MGMT-01-cache-management-design.md). With cache management switched off there is
     * no panel to open, so the long press keeps the legacy behaviour and repeats the plain clear.</p>
     */
    private boolean onCacheManage(View view) {
        if (!CachePolicyStore.isManagementEnabled()) {
            onCacheClear(view);
            return true;
        }
        CacheManagementDialog.show(this);
        return true;
    }

    private void onBackup(View view) {
        PermissionUtil.requestFile(this, allGranted -> {
            if (!allGranted) {
                Notify.show(R.string.backup_permission_denied);
                return;
            }
            BackupProgressDialog progress = BackupProgressDialog.open(getParentFragmentManager(), "备份应用数据");
            AppDatabase.backup(new Callback() {
                @Override
                public void success() {
                    progress.finish();
                    Notify.show(R.string.backup_success);
                }

                @Override
                public void error() {
                    progress.finish();
                    Notify.show(R.string.backup_fail);
                }
            }, progress::update);
        });
    }

    private void onRestore(View view) {
        PermissionUtil.requestFile(this, allGranted -> {
            if (!allGranted) {
                Notify.show(R.string.backup_permission_denied);
                return;
            }
            RestoreDialog.create().show(requireActivity(), new Callback() {
                @Override
                public void success() {
                    Notify.show(R.string.restore_success);
                    setOtherText();
                }

                @Override
                public void error() {
                    Notify.show(R.string.restore_fail);
                }
            });
        });
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() == ConfigEvent.Type.WALL) {
            setWallText();
            return;
        }
        if (event.type() != ConfigEvent.Type.COMMON) return;
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        setWallText();
    }

    /**
     * Re-reads the cache inventory the moment a cleanup (or any other cache mutation) finishes.
     *
     * <p>The settings row used to keep showing the pre-cleanup value until the user switched to
     * another page and back, because nothing told it the cache had changed.</p>
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() != RefreshEvent.Type.CACHE) return;
        if (mBinding == null || !isAdded()) return;
        setCacheText();
    }

    private void setWallText() {
        mBinding.wallUrl.setText(Setting.getWallDesc(WallConfig.getDesc()));
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) return;
        setCacheText();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
        mBinding = null;
    }
}
