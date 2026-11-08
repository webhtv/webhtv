package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.databinding.AdapterTmdbRecommendationBinding;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.ui.helper.TmdbRatingFormatter;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.TmdbImageSelector;
import com.google.android.material.card.MaterialCardView;

public class TmdbRecommendationPresenter extends Presenter {

    // 焦点环宽度与环色都不得在本类里写死：
    //   宽度 → @dimen/webhtv_focus_ring_width（全 TV 唯一宽度来源）；
    //   环色 → ThemeController.focusRingColor()（= ?attr/tvFocusRing 的代码路径同一来源）。
    // 此前这里写死 FOCUS_WIDTH_DP = 3 与 R.color.tv_item_focus_ring，于是前几轮统一
    // drawable 宽度时漏掉了本卡（用户报告「个性推荐等卡片的边框粗细没有改小没有统一」）。
    private static final int NORMAL_WIDTH_DP = 1;

    private final OnClickListener mListener;
    private final OnLongClickListener mLongClickListener;
    private final OnFocusListener mFocusListener;

    public TmdbRecommendationPresenter(OnClickListener listener) {
        this(listener, null, null);
    }

    public TmdbRecommendationPresenter(OnClickListener listener, OnLongClickListener longClickListener, OnFocusListener focusListener) {
        this.mListener = listener;
        this.mLongClickListener = longClickListener;
        this.mFocusListener = focusListener;
    }

    public interface OnClickListener {
        void onItemClick(TmdbItem item);
    }

    public interface OnLongClickListener {
        boolean onItemLongClick(TmdbItem item);
    }

    public interface OnFocusListener {
        void onItemFocus(TmdbItem item, boolean focused);
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        return new ViewHolder(AdapterTmdbRecommendationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        TmdbItem tmdbItem = (TmdbItem) item;
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.item = tmdbItem;
        holder.binding.title.setText(tmdbItem.getTitle());
        TmdbRatingFormatter.Ratings ratings = TmdbRatingFormatter.completeRatings(tmdbItem);
        holder.binding.tmdbRating.setText(ratings.getTmdb());
        holder.binding.tmdbRating.setVisibility(View.VISIBLE);
        holder.binding.tmdbRating.setAlpha(ratings.hasTmdbRating() ? 1.0f : 0.55f);
        holder.binding.doubanRating.setText(ratings.getDouban());
        holder.binding.doubanRating.setVisibility(View.VISIBLE);
        holder.binding.doubanRating.setAlpha(ratings.hasDoubanRating() ? 1.0f : 0.55f);
        holder.binding.ratingGroup.setVisibility(View.VISIBLE);
        String image = TmdbImageSelector.cardImage(tmdbItem, false);
        String fallbackImage = TmdbImageSelector.cardImage(tmdbItem, true);
        ImgUtil.load(tmdbItem.getTitle(), image, fallbackImage, holder.binding.poster, true, 300, 450);
        setOnClickListener(holder, view -> {
            if (mListener != null) mListener.onItemClick(tmdbItem);
        });
        holder.view.setOnLongClickListener(view -> mLongClickListener != null && mLongClickListener.onItemLongClick(tmdbItem));
        holder.view.setOnFocusChangeListener((view, focused) -> {
            applyUnifiedFocus((MaterialCardView) view, focused);
            if (mFocusListener != null) mFocusListener.onItemFocus(tmdbItem, focused);
        });
        applyUnifiedFocus((MaterialCardView) holder.view, holder.view.hasFocus());
        if (holder.view.hasFocus() && mFocusListener != null) mFocusListener.onItemFocus(tmdbItem, true);
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ViewHolder holder = (ViewHolder) viewHolder;
        if (viewHolder.view.hasFocus() && holder.item != null && mFocusListener != null) {
            mFocusListener.onItemFocus(holder.item, false);
        }
        holder.item = null;
        viewHolder.view.setOnLongClickListener(null);
        viewHolder.view.setOnFocusChangeListener(null);
    }

    /**
     * 统一焦点外观：焦点用 @dimen/webhtv_focus_ring_width 的主题环，失焦恢复常态描边。
     * 只作用于 leanback 播放页的推荐卡，不改变共享布局和独立详情页的焦点契约。
     */
    private static void applyUnifiedFocus(MaterialCardView card, boolean focused) {
        if (focused) {
            card.setStrokeWidth(card.getContext().getResources()
                    .getDimensionPixelSize(R.dimen.webhtv_focus_ring_width));
            card.setStrokeColor(ThemeController.focusRingColor(card.getContext()));
        } else {
            card.setStrokeWidth(ResUtil.dp2px(NORMAL_WIDTH_DP));
            card.setStrokeColor(card.getContext().getColor(R.color.tv_item_normal_stroke));
        }
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterTmdbRecommendationBinding binding;
        private TmdbItem item;

        public ViewHolder(@NonNull AdapterTmdbRecommendationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
