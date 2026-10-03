package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.databinding.AdapterEpgDataBinding;

import java.util.ArrayList;
import java.util.List;

public class EpgDataAdapter extends RecyclerView.Adapter<EpgDataAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<EpgData> mItems;

    public EpgDataAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    public void addAll(List<EpgData> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public EpgData get(int position) {
        return mItems.get(position);
    }

    public void setSelected(EpgData selected) {
        for (EpgData item : mItems) item.setSelected(selected);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterEpgDataBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EpgData item = mItems.get(position);
        holder.binding.time.setText(item.getTime());
        holder.binding.title.setText(item.getTitle());
        holder.binding.getRoot().setSelected(item.isSelected());

        // 新增：判断当前节目是否直播中
        if (item.isInRange()) {
            holder.binding.tvLiveTag.setVisibility(View.VISIBLE);
            holder.binding.tvLiveTag.setText(com.fongmi.android.tv.R.string.live_program_current);
        } else {
            holder.binding.tvLiveTag.setVisibility(View.GONE);
        }

        holder.binding.getRoot().setOnClickListener(v -> {
            if (!item.isFuture()) mListener.onItemClick(item);
        });
    }

    public interface OnClickListener {
        void hideEpg();
        void onItemClick(EpgData item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterEpgDataBinding binding;
        ViewHolder(@NonNull AdapterEpgDataBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
