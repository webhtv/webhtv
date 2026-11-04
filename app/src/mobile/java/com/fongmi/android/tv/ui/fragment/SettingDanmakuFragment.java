package com.fongmi.android.tv.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.FragmentSettingDanmakuBinding;
import com.fongmi.android.tv.impl.DanmakuListener;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.DanmakuApiDialog;

public class SettingDanmakuFragment extends BaseFragment implements DanmakuListener {

    private FragmentSettingDanmakuBinding mBinding;

    public static SettingDanmakuFragment newInstance() {
        return new SettingDanmakuFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    private String getApiStatus() {
        return getString(DanmakuSetting.hasValidApiUrl() ? R.string.yes : R.string.none);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingDanmakuBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mBinding.danmakuApiText.setText(getApiStatus());
        mBinding.danmakuAutoText.setText(getSwitch(DanmakuSetting.isAuto()));
        mBinding.danmakuLoadText.setText(getSwitch(DanmakuSetting.isLoad()));
        mBinding.danmakuSpiderText.setText(getSwitch(DanmakuSetting.isSpiderFirst()));
        updateApiVisibility();
    }

    @Override
    protected void initEvent() {
        mBinding.danmakuApi.setOnClickListener(this::onDanmakuApi);
        mBinding.danmakuAuto.setOnClickListener(this::setDanmakuAuto);
        mBinding.danmakuLoad.setOnClickListener(this::setDanmakuLoad);
        mBinding.danmakuSpider.setOnClickListener(this::setDanmakuSpider);
    }

    private void setDanmakuLoad(View view) {
        DanmakuSetting.putLoad(!DanmakuSetting.isLoad());
        mBinding.danmakuLoadText.setText(getSwitch(DanmakuSetting.isLoad()));
        updateApiVisibility();
    }

    private void updateApiVisibility() {
        boolean load = DanmakuSetting.isLoad();
        mBinding.danmakuApi.setVisibility(load ? View.VISIBLE : View.GONE);
        updateAutoVisibility();
    }

    private void updateAutoVisibility() {
        boolean show = DanmakuSetting.isLoad() && (DanmakuSetting.hasValidApiUrl() || DanmakuSetting.isBundleSource());
        mBinding.danmakuAuto.setVisibility(show ? View.VISIBLE : View.GONE);
        updateSpiderVisibility();
    }

    private void updateSpiderVisibility() {
        boolean show = DanmakuSetting.isLoad() && DanmakuSetting.hasValidApiUrl() && DanmakuSetting.isAuto();
        mBinding.danmakuSpider.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void onDanmakuApi(View view) {
        DanmakuApiDialog.show(this);
    }

    @Override
    public void setDanmakuApi(String url) {
        DanmakuSetting.putApiUrl(url);
        mBinding.danmakuApiText.setText(getApiStatus());
        updateAutoVisibility();
    }

    private void setDanmakuAuto(View view) {
        DanmakuSetting.putAuto(!DanmakuSetting.isAuto());
        mBinding.danmakuAutoText.setText(getSwitch(DanmakuSetting.isAuto()));
        updateSpiderVisibility();
    }

    private void setDanmakuSpider(View view) {
        DanmakuSetting.putSpiderFirst(!DanmakuSetting.isSpiderFirst());
        mBinding.danmakuSpiderText.setText(getSwitch(DanmakuSetting.isSpiderFirst()));
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) initView();
    }
}
