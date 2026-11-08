package com.fongmi.android.tv.ui.presenter;

import android.content.res.ColorStateList;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterTmdbPhotoBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.card.MaterialCardView;

public class TmdbPhotoPresenter extends Presenter {

    private final OnClickListener mListener;
    private final boolean poster;

    public TmdbPhotoPresenter(OnClickListener listener) {
        this(listener, false);
    }

    public TmdbPhotoPresenter(OnClickListener listener, boolean poster) {
        this.mListener = listener;
        this.poster = poster;
    }

    public interface OnClickListener {
        void onItemClick(String url, int position);
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        AdapterTmdbPhotoBinding binding = AdapterTmdbPhotoBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        if (poster) {
            ViewGroup.LayoutParams params = binding.getRoot().getLayoutParams();
            params.width = ResUtil.dp2px(148);
            params.height = ResUtil.dp2px(222);
            binding.getRoot().setLayoutParams(params);
        }
        bindFocusStyle(binding.getRoot());
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        String url = (String) item;
        ViewHolder holder = (ViewHolder) viewHolder;
        int label = poster ? R.string.tmdb_posters_label : R.string.tmdb_photos_label;
        ImgUtil.load(holder.binding.photo.getContext().getString(label), url, holder.binding.photo);
        setOnClickListener(holder, view -> {
            if (mListener != null) mListener.onItemClick(url, 0);
        });
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
    }

    /**
     * 剧照/海报卡片此前完全没有焦点外观：布局里关闭了系统默认焦点高亮，presenter 也不改描边，
     * 所以遥控停在卡片上时看不出焦点在哪里。这里用前景 selector 画 3dp 焦点环，
     * 与演员卡（selector_tmdb_cast_focus.xml）同一套做法：前景绘制在图片之上，
     * 不需要在每次绑定时改动卡片描边。
     */
    private static void bindFocusStyle(MaterialCardView card) {
        card.setRippleColor(ColorStateList.valueOf(0x00000000));
        card.setForeground(card.getContext().getDrawable(R.drawable.selector_tmdb_media_focus));
        card.setStateListAnimator(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) card.setDefaultFocusHighlightEnabled(false);
        applyFocusStyle(card, card.hasFocus());
        card.setOnFocusChangeListener((view, focused) -> applyFocusStyle(card, focused));
    }

    private static void applyFocusStyle(MaterialCardView card, boolean focused) {
        card.setActivated(focused);
    }

    static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterTmdbPhotoBinding binding;

        public ViewHolder(@NonNull AdapterTmdbPhotoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
