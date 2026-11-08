package com.fongmi.android.tv.ui.presenter;

import android.content.res.ColorStateList;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.databinding.AdapterTmdbCastBinding;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.card.MaterialCardView;

public class TmdbCastPresenter extends Presenter {

    // 统一焦点规范（与 selector_video_item.xml / selector_tmdb_cast_focus.xml 一致）：
    // 常态 1dp @color/tv_item_normal_stroke，焦点环 3dp @color/tv_item_focus_ring 由 foreground 绘制。
    private static final int STROKE_NORMAL = 0x33FFFFFF;
    private static final int STROKE_WIDTH_NORMAL_DP = 1;

    private final OnClickListener mListener;

    public TmdbCastPresenter(OnClickListener listener) {
        this.mListener = listener;
    }

    public interface OnClickListener {
        void onItemClick(TmdbPerson item);
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        return new ViewHolder(AdapterTmdbCastBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        TmdbPerson person = (TmdbPerson) item;
        ViewHolder holder = (ViewHolder) viewHolder;
        bindFocusStyle(holder.binding.getRoot());
        holder.binding.name.setText(person.getName());
        holder.binding.role.setText(person.getSubtitle());
        ImgUtil.load(person.getName(), person.getProfileUrl(), holder.binding.profile);
        setOnClickListener(holder, view -> {
            if (mListener != null) mListener.onItemClick(person);
        });
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
    }

    private void bindFocusStyle(MaterialCardView card) {
        card.setRippleColor(ColorStateList.valueOf(0x00000000));
        card.setForeground(card.getContext().getDrawable(R.drawable.selector_tmdb_cast_focus));
        card.setStateListAnimator(null);
        applyFocusStyle(card, card.hasFocus());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) card.setDefaultFocusHighlightEnabled(false);
        card.setOnFocusChangeListener((view, focused) -> applyFocusStyle(card, focused));
    }

    private void applyFocusStyle(MaterialCardView card, boolean focused) {
        var tokens = ThemeController.current();
        card.setActivated(focused);
        // 卡面走统一主题 token，自定义主题可覆写；
        // 焦点环已由 foreground 的 selector 统一绘制，这里只维护常态描边，
        // 避免"焦点用 3dp 卡片描边 + foreground 再画一圈"的双重描边。
        card.setCardBackgroundColor(tokens.colorSurfaceContainerHigh());
        card.setStrokeColor(STROKE_NORMAL);
        card.setStrokeWidth(ResUtil.dp2px(STROKE_WIDTH_NORMAL_DP));
        card.setCardElevation(0);
        card.setTranslationZ(0);
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterTmdbCastBinding binding;

        public ViewHolder(@NonNull AdapterTmdbCastBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
