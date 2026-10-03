package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.databinding.AdapterChannelBinding;

import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Channel> mItems;

    public ChannelAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    public void addAll(List<Channel> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void remove(Channel item) {
        int index = mItems.indexOf(item);
        if (index < 0) return;
        mItems.remove(index);
        notifyItemRemoved(index);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public Channel get(int position) {
        return mItems.get(position);
    }

    public void setSelected(Channel selected) {
        for (Channel item : mItems) item.setSelected(selected);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterChannelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel item = mItems.get(position);
        String epg = item.getData().getCurrent().getTitle();
        item.loadLogo(holder.binding.logo);
        holder.binding.name.setText(item.getShow());
        holder.binding.number.setText(item.getNumber());
        holder.binding.epg.setText(epg);
        holder.binding.epg.setVisibility(epg.isEmpty() ? View.GONE : View.VISIBLE);
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(v -> mListener.onLongClick(item));

        // ========== 新增焦点监听，跑马灯逻辑 ==========
        holder.binding.getRoot().setOnFocusChangeListener((v, hasFocus) -> {
            TextView tvName = holder.binding.name;
            TextView tvEpg = holder.binding.epg;
            if (hasFocus) {
                // 用post等待布局绘制完成，避免getEllipsisCount测量不准
                tvName.post(() -> {
                    if (tvName.getLayout() != null && tvName.getLayout().getEllipsisCount(0) > 0) {
                        tvName.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                        tvName.setSelected(true);
                    } else {
                        tvName.setEllipsize(TextUtils.TruncateAt.END);
                        tvName.setSelected(false);
                    }
                });
                tvEpg.post(() -> {
                    if (tvEpg.getLayout() != null && tvEpg.getLayout().getEllipsisCount(0) > 0) {
                        tvEpg.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                        tvEpg.setSelected(true);
                    } else {
                        tvEpg.setEllipsize(TextUtils.TruncateAt.END);
                        tvEpg.setSelected(false);
                    }
                });
            } else {
                // 失去焦点，关闭跑马灯，恢复末尾省略号
                tvName.setEllipsize(TextUtils.TruncateAt.END);
                tvName.setSelected(false);
                tvEpg.setEllipsize(TextUtils.TruncateAt.END);
                tvEpg.setSelected(false);
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        // 回收时强制关闭跑马灯，防止复用错乱
        holder.binding.name.setEllipsize(TextUtils.TruncateAt.END);
        holder.binding.name.setSelected(false);
        holder.binding.epg.setEllipsize(TextUtils.TruncateAt.END);
        holder.binding.epg.setSelected(false);
        Glide.with(holder.binding.logo).clear(holder.binding.logo);
    }

    public interface OnClickListener {
        void onItemClick(Channel item);
        boolean onLongClick(Channel item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterChannelBinding binding;
        ViewHolder(@NonNull AdapterChannelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
