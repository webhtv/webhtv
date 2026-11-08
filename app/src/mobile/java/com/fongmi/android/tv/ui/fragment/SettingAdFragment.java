package com.fongmi.android.tv.ui.fragment;

import android.view.LayoutInflater;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.ImportedAdRuleCandidateStore;
import com.fongmi.android.tv.api.config.RuleConfig;
import com.fongmi.android.tv.api.config.UserAdRuleStore;
import com.fongmi.android.tv.databinding.FragmentSettingAdBinding;
import com.fongmi.android.tv.player.IntroSkipKinds;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.AdBlockStatsDialog;
import com.fongmi.android.tv.ui.dialog.AdRuleManageDialog;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.crawler.SpiderDebug;

public class SettingAdFragment extends BaseFragment {

    private FragmentSettingAdBinding mBinding;
    private String[] introSkipMode;

    public static SettingAdFragment newInstance() {
        return new SettingAdFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingAdBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        introSkipMode = ResUtil.getStringArray(R.array.select_auto_skip_intro_outro);
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.adblock.setOnClickListener(this::setAdblock);
        mBinding.aiAdDetection.setOnClickListener(this::setAiAdDetection);
        mBinding.adRuleManage.setOnClickListener(view -> AdRuleManageDialog.create().show(requireActivity(), this::setText));
        mBinding.adBlockStats.setOnClickListener(view -> AdBlockStatsDialog.create(requireActivity()).show());
        mBinding.autoSkipIntroOutro.setOnClickListener(this::setAutoSkipIntroOutro);
        mBinding.introSkipKinds.setOnClickListener(view -> IntroSkipKinds.show(requireActivity(), this::setText));
    }

    private void setText() {
        if (mBinding == null) return;
        if (!canSetText()) return;
        safeSet("adblock", mBinding.adblockText, () -> getSwitch(Setting.isAdblock()));
        safeRun("aiAdDetection", () -> {
            mBinding.aiAdDetection.setVisibility(Setting.isAiConfigReady() ? View.VISIBLE : View.GONE);
            setSummary(mBinding.aiAdDetectionText, getSwitch(Setting.isAiAdDetection()));
        }, () -> setError(mBinding.aiAdDetectionText));
        safeSet("adRuleManage", mBinding.adRuleManageText, () -> getString(R.string.ad_rule_count_with_pending,
                UserAdRuleStore.load().size() + RuleConfig.get().getDefaultRules().size(),
                ImportedAdRuleCandidateStore.pending().size()));
        safeSet("autoSkipIntroOutro", mBinding.autoSkipIntroOutroText, () -> introSkipMode[Setting.getIntroSkipMode()]);
        safeSet("introSkipKinds", mBinding.introSkipKindsText, IntroSkipKinds::summary);
    }

    private boolean canSetText() {
        return mBinding != null && isAdded() && getContext() != null;
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
            SpiderDebug.log("ad", "summary failed item=%s error=%s", name, e.toString());
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

    private void setAdblock(View view) {
        Setting.putAdblock(!Setting.isAdblock());
        setText();
    }

    private void setAiAdDetection(View view) {
        Setting.putAiAdDetection(!Setting.isAiAdDetection());
        setText();
    }

    private void setAutoSkipIntroOutro(View view) {
        Setting.putIntroSkipMode((Setting.getIntroSkipMode() + 1) % introSkipMode.length);
        setText();
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
