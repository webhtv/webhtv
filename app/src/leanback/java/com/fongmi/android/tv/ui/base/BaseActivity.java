package com.fongmi.android.tv.ui.base;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.server.process.ApkUrlPush;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.ui.custom.CustomWallView;
import com.fongmi.android.tv.ui.helper.TouchOptimizationHelper;
import com.fongmi.android.tv.utils.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.AutoSizeCompat;

public abstract class BaseActivity extends AppCompatActivity {

    private static final int DESIGN_WIDTH_IN_DP = 960;
    private static final int DESIGN_HEIGHT_IN_DP = 540;

    protected abstract ViewBinding getBinding();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(Setting.wrapLanguage(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeController.applyFromPreferences(this);
        registerFragmentLifecycleCallbacks();
        // getBinding() inflates on every call, so resolve it once: the root that is set as
        // content must be the same instance that initView()/initEvent() configure.
        View content = getBinding().getRoot();
        setContentView(content);
        bindFullWindowContent();
        ThemeController.bindTheme(content);
        EventBus.getDefault().register(this);
        initView(savedInstanceState);
        ThemeController.bindTheme(content);
        Util.hideSystemUI(this);
        setBackCallback();
        initEvent();
    }

    /**
     * 让电视端内容区等于整块窗口，避免底部出现整条黑带。
     *
     * <p>背景（实测）：模拟器 5554 的屏幕带底部 overscan 90px
     * （{@code dumpsys window displays} 里 {@code overscan (0,0,0,90)}），系统会把这条内边距
     * 下发给窗口；AppCompat 的 decor 里有两层都会各自应用一次 fitsSystemWindows 内边距
     * （外层 window content 与内层 {@code android:id/content}），于是内容区被扣掉 2×90=180px。
     * 同一块屏上 launcher 的内容区是 990（只扣一次），本应用实测是 900，卡片标题被齐腰切断、
     * {@code 900..1080} 整条是窗口背景黑。
     *
     * <p>电视端本来就全屏沉浸（{@link Util#hideSystemUI}），且各弹层自己留了 overscan 安全边距
     * （见 AboutDialog / UpdateDialog 的注释），所以这里在 DecorView 上装一个"只消费、不加内边距"
     * 的监听器，inset 不再下发给任何一层，内容铺满窗口。没有 inset 的电视/盒子上这条路径
     * 与改动前完全一致（insets 为 0 时各层本来就不会加内边距）。
     *
     * <p>弹层是独立窗口，不受影响；软键盘仍是窗口 resize（adjustResize），不依赖这条 inset。
     */
    private void bindFullWindowContent() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (view, insets) -> WindowInsetsCompat.CONSUMED);
        View decorContent = findViewById(android.R.id.content);
        if (decorContent != null) decorContent.setFitsSystemWindows(false);
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        if (customWall()) addCustomWall();
        TouchOptimizationHelper.sync(getWindow().getDecorView());
    }

    private void addCustomWall() {
        ((ViewGroup) findViewById(android.R.id.content)).addView(new CustomWallView(this, null).setMotionEnabled(customWallMotion()), 0, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }

    protected FragmentActivity getActivity() {
        return this;
    }

    protected boolean customWall() {
        return true;
    }

    /**
     * 动态壁纸（视频/GIF）是否允许在本页面播放。返回 false 时壁纸降级为首帧静态图，
     * 避免与页面自身的播放器争抢 MediaCodec 硬解实例并按壁纸帧率重绘整个界面。
     */
    protected boolean customWallMotion() {
        return true;
    }

    protected void initView(Bundle savedInstanceState) {
    }

    protected void initEvent() {
    }

    protected boolean isVisible(View view) {
        return view.getVisibility() == View.VISIBLE;
    }

    protected boolean isGone(View view) {
        return view.getVisibility() == View.GONE;
    }

    protected void notifyItemChanged(RecyclerView view, RecyclerView.Adapter<?> adapter) {
        postRecyclerUpdate(view, adapter::notifyDataSetChanged);
    }

    protected void notifyItemsChanged(RecyclerView view, RecyclerView.Adapter<?> adapter, int... positions) {
        postRecyclerUpdate(view, () -> {
            for (int i = 0; i < positions.length; i++) {
                int position = positions[i];
                if (position < 0 || position >= adapter.getItemCount()) continue;
                boolean duplicate = false;
                for (int j = 0; j < i; j++) if (positions[j] == position) duplicate = true;
                if (!duplicate) adapter.notifyItemChanged(position);
            }
        });
    }

    private void postRecyclerUpdate(RecyclerView view, Runnable update) {
        view.post(new Runnable() {
            @Override
            public void run() {
                if (view.isComputingLayout()) {
                    view.postOnAnimation(this);
                    return;
                }
                update.run();
            }
        });
    }

    private void setBackCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBackInvoked();
            }
        });
    }

    private Resources hackResources(Resources resources) {
        try {
            // Keep the TV AutoSize pipeline and express the user scale through its design size.
            applyUiScale();
            AutoSizeCompat.autoConvertDensityOfGlobal(resources);
            return resources;
        } catch (Exception ignored) {
            return resources;
        }
    }

    private void applyUiScale() {
        AutoSizeConfig config = AutoSizeConfig.getInstance();
        float factor = Setting.getUiScaleFactor(Setting.getUiScale());
        config.setDesignWidthInDp(Math.round(DESIGN_WIDTH_IN_DP / factor));
        config.setDesignHeightInDp(Math.round(DESIGN_HEIGHT_IN_DP / factor));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSubscribe(Object o) {
        if (o instanceof RefreshEvent event && (event.getType() == RefreshEvent.Type.LANGUAGE || event.getType() == RefreshEvent.Type.UI_SCALE || event.getType() == RefreshEvent.Type.THEME)) recreate();
    }

    @Override
    public Resources getResources() {
        return hackResources(super.getResources());
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Util.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Util.hideSystemUI(this);
    }

    protected void onBackInvoked() {
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        TouchOptimizationHelper.sync(getWindow().getDecorView());
        Updater.create().resume(this);
        ApkUrlPush.get().resume(this);
    }

    private void registerFragmentLifecycleCallbacks() {
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment, @NonNull View view, Bundle savedInstanceState) {
                TouchOptimizationHelper.sync(view);
            }

            @Override
            public void onFragmentStarted(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
                if (!(fragment instanceof DialogFragment dialog) || dialog.getDialog() == null) return;
                Window window = dialog.getDialog().getWindow();
                if (window == null) return;
                window.getDecorView().post(() -> TouchOptimizationHelper.sync(window.getDecorView()));
            }
        }, true);
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }
}
