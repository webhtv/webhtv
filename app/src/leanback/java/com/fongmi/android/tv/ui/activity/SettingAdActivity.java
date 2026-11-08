package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.ImportedAdRuleCandidateStore;
import com.fongmi.android.tv.api.config.RuleConfig;
import com.fongmi.android.tv.api.config.UserAdRuleStore;
import com.fongmi.android.tv.databinding.ActivitySettingAdBinding;
import com.fongmi.android.tv.player.IntroSkipKinds;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.AdBlockStatsDialog;
import com.fongmi.android.tv.ui.dialog.AdRuleManageDialog;
import com.github.catvod.crawler.SpiderDebug;

public class SettingAdActivity extends BaseActivity {

    private ActivitySettingAdBinding mBinding;
    private String[] introSkipMode;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingAdActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingAdBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.adblock.requestFocus();
        introSkipMode = getResources().getStringArray(R.array.select_auto_skip_intro_outro);
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.adblock.setOnClickListener(this::setAdblock);
        mBinding.aiAdDetection.setOnClickListener(this::setAiAdDetection);
        mBinding.adRuleManage.setOnClickListener(view -> AdRuleManageDialog.create().show(this, this::setText));
        mBinding.adBlockStats.setOnClickListener(view -> AdBlockStatsDialog.create(this).show());
        mBinding.autoSkipIntroOutro.setOnClickListener(this::setAutoSkipIntroOutro);
        mBinding.introSkipKinds.setOnClickListener(view -> IntroSkipKinds.show(this, this::setText));
    }

    private void setText() {
        if (!canSetText()) return;
        safeSet("adblock", mBinding.adblockText, () -> getSwitch(Setting.isAdblock()));
        mBinding.aiAdDetection.setVisibility(Setting.isAiConfigReady() ? View.VISIBLE : View.GONE);
        safeSet("aiAdDetection", mBinding.aiAdDetectionText, () -> getSwitch(Setting.isAiAdDetection()));
        safeSet("adRuleManage", mBinding.adRuleManageText, () -> getString(R.string.ad_rule_count_with_pending,
                UserAdRuleStore.load().size() + RuleConfig.get().getDefaultRules().size(),
                ImportedAdRuleCandidateStore.pending().size()));
        safeSet("autoSkipIntroOutro", mBinding.autoSkipIntroOutroText, () -> introSkipMode[Setting.getIntroSkipMode()]);
        safeSet("introSkipKinds", mBinding.introSkipKindsText, IntroSkipKinds::summary);
    }

    private boolean canSetText() {
        return mBinding != null && !isFinishing() && !isDestroyed();
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
    protected void onResume() {
        super.onResume();
        setText();
    }
}
