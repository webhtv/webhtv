package com.fongmi.android.tv.ui.presenter;

import android.content.res.ColorStateList;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbVideo;
import com.fongmi.android.tv.databinding.AdapterTmdbVideoBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.google.android.material.card.MaterialCardView;

public class TmdbVideoPresenter extends Presenter {

    private static final float FOCUS_SCALE = 1.04f;
    private static final int FOCUS_SCALE_DURATION_MS = 120;

    public interface OnClickListener {
        void onItemClick(TmdbVideo item);
    }

    private final OnClickListener listener;

    public TmdbVideoPresenter(OnClickListener listener) {
        this.listener = listener;
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        return new ViewHolder(AdapterTmdbVideoBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        TmdbVideo video = (TmdbVideo) item;
        ViewHolder holder = (ViewHolder) viewHolder;
        String name = video.getName().isEmpty() ? video.getDisplayType() : video.getName();
        holder.binding.title.setText(name);
        holder.binding.subtitle.setText(video.getDisplayType() + " ? " + video.getScopeLabel());
        ImgUtil.load(name, video.getThumbnailUrl(), holder.binding.poster, true, 300, 169);
        bindFocusStyle(holder);
        setOnClickListener(holder, view -> {
            if (listener != null) listener.onItemClick(video);
        });
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        View root = viewHolder.view;
        root.animate().cancel();
        root.setScaleX(1.0f);
        root.setScaleY(1.0f);
        root.setOnFocusChangeListener(null);
        root.setForeground(null);
        root.setActivated(false);
    }

    /**
     * 相关视频卡片的封面 ImageView 是 match_parent 全出血的，而 MaterialCardView 的描边画在
     * 背景层，会被封面完全盖住——这正是“剧照修好之后，相关视频仍然看不出焦点在哪”的原因。
     * 与剧照/海报、演员卡一致，改用前景 selector 把 3dp 焦点环画在封面之上。
     */
    private void bindFocusStyle(ViewHolder holder) {
        MaterialCardView card = holder.binding.getRoot();
        card.setRippleColor(ColorStateList.valueOf(0x00000000));
        card.setForeground(card.getContext().getDrawable(R.drawable.selector_tmdb_media_focus));
        card.setStateListAnimator(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) card.setDefaultFocusHighlightEnabled(false);
        applyFocusChrome(holder, card.hasFocus());
        card.setOnFocusChangeListener((view, focused) -> applyFocusChrome(holder, focused));
    }

    /**
     * RecyclerView 复用时卡片可能已经持有焦点，只挂监听不会触发回调，
     * 必须按当前真实焦点状态补一次，否则“停下”的那张卡看不到焦点环。
     */
    private void applyFocusChrome(ViewHolder holder, boolean focused) {
        holder.binding.getRoot().setActivated(focused);
        holder.view.animate().cancel();
        float scale = focused ? FOCUS_SCALE : 1.0f;
        holder.view.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_SCALE_DURATION_MS).start();
    }

    static final class ViewHolder extends Presenter.ViewHolder {
        private final AdapterTmdbVideoBinding binding;

        ViewHolder(@NonNull AdapterTmdbVideoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
