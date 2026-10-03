package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.databinding.AdapterChannelBinding;
import com.fongmi.android.tv.setting.LiveSetting;

import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    private static final Object PAYLOAD_SELECTED = new Object();
    private static final int WINDOW_SIZE = 64;
    private static final int WINDOW_STEP = 16;
    private static final int WINDOW_EDGE = 8;
    private static final int WINDOW_MAX = WINDOW_SIZE + WINDOW_STEP;

    private final OnClickListener listener;
    private final List<Channel> mItems;
    private int selectedPosition;
    private int visibleStart;
    private int visibleEnd;
    private boolean windowUpdatePending;

    public ChannelAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
        this.selectedPosition = -1;
    }

    public interface OnClickListener {

        void onItemClick(Channel item);

        boolean onLongClick(Channel item);
    }

    public void clear() {
        mItems.clear();
        selectedPosition = -1;
        visibleStart = visibleEnd = 0;
        notifyDataSetChanged();
    }

    public void addAll(List<Channel> items) {
        addAll(items, -1);
    }

    public void addAll(List<Channel> items, int position) {
        mItems.clear();
        mItems.addAll(items);
        selectedPosition = position >= 0 && position < mItems.size() ? position : -1;
        for (int i = 0; i < mItems.size(); i++) mItems.get(i).setSelected(i == selectedPosition);
        setWindowAround(selectedPosition == -1 ? 0 : selectedPosition);
        notifyDataSetChanged();
    }

    public void remove(Channel item) {
        int position = indexOf(item);
        if (position == -1) return;
        mItems.remove(position);
        if (selectedPosition == position) selectedPosition = -1;
        else if (selectedPosition > position) selectedPosition--;
        trimWindow();
        notifyDataSetChanged();
    }

    public void setSelected(int position) {
        if (position < 0 || position >= mItems.size()) return;
        int old = selectedPosition;
        selectedPosition = position;
        if (old >= 0 && old < mItems.size()) mItems.get(old).setSelected(false);
        mItems.get(position).setSelected(true);
        boolean outsideWindow = position < visibleStart || position >= visibleEnd;
        ensurePosition(position);
        if (outsideWindow) return;
        notifySelectedChanged(old);
        notifySelectedChanged(position);
    }

    public int setSelected(Channel channel) {
        int position = indexOf(channel);
        setSelected(position);
        return position;
    }

    public int ensurePosition(int position) {
        if (position < 0 || position >= mItems.size()) return -1;
        if (position >= visibleStart && position < visibleEnd) return position - visibleStart;
        setWindowAround(position);
        notifyDataSetChanged();
        return position - visibleStart;
    }

    public void scheduleWindowUpdate(RecyclerView recyclerView) {
        if (windowUpdatePending) return;
        windowUpdatePending = true;
        recyclerView.post(() -> {
            windowUpdatePending = false;
            expandWindow(recyclerView);
        });
    }

    private void expandWindow(RecyclerView recyclerView) {
        if (recyclerView.isComputingLayout()) {
            scheduleWindowUpdate(recyclerView);
            return;
        }
        if (!(recyclerView.getLayoutManager() instanceof LinearLayoutManager manager)) return;
        int first = manager.findFirstVisibleItemPosition();
        int last = manager.findLastVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return;
        int oldStart = visibleStart;
        int oldEnd = visibleEnd;
        int top = getTop(manager, first);
        int newStart = visibleStart;
        int newEnd = visibleEnd;
        boolean prepend = first <= WINDOW_EDGE;
        boolean append = last >= getItemCount() - WINDOW_EDGE - 1;
        if (prepend) newStart = Math.max(0, visibleStart - WINDOW_STEP);
        if (append) newEnd = Math.min(mItems.size(), visibleEnd + WINDOW_STEP);
        if (newEnd - newStart > WINDOW_MAX) {
            if (prepend) newEnd = Math.min(newEnd, newStart + WINDOW_MAX);
            else if (append) newStart = Math.max(0, newEnd - WINDOW_MAX);
        }
        applyWindowChange(recyclerView, manager, first, top, oldStart, oldEnd, newStart, newEnd);
    }

    public int getTotalCount() {
        return mItems.size();
    }

    private int indexOf(Channel channel) {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i) == channel) return i;
        return mItems.indexOf(channel);
    }

    private void notifySelectedChanged(int position) {
        if (position < visibleStart || position >= visibleEnd) return;
        notifyItemChanged(position - visibleStart, PAYLOAD_SELECTED);
    }

    private int getTop(LinearLayoutManager manager, int position) {
        View view = manager.findViewByPosition(position);
        return view == null ? 0 : view.getTop();
    }

    private void applyWindowChange(RecyclerView recyclerView, LinearLayoutManager manager, int first, int top, int oldStart, int oldEnd, int newStart, int newEnd) {
        if (oldStart == newStart && oldEnd == newEnd) return;
        if (newStart < oldStart) {
            int inserted = oldStart - newStart;
            visibleStart = newStart;
            notifyItemRangeInserted(0, inserted);
            recyclerView.post(() -> manager.scrollToPositionWithOffset(first + inserted, top));
        }
        if (newEnd > oldEnd) {
            int inserted = newEnd - oldEnd;
            int position = oldEnd - visibleStart;
            visibleEnd = newEnd;
            notifyItemRangeInserted(position, inserted);
        }
        if (newStart > oldStart) {
            int removed = newStart - oldStart;
            visibleStart = newStart;
            notifyItemRangeRemoved(0, removed);
            recyclerView.post(() -> manager.scrollToPositionWithOffset(Math.max(0, first - removed), top));
        }
        if (newEnd < oldEnd) {
            int removed = oldEnd - newEnd;
            visibleEnd = newEnd;
            notifyItemRangeRemoved(newEnd - visibleStart, removed);
        }
    }

    private void setWindowAround(int position) {
        if (mItems.isEmpty()) {
            visibleStart = visibleEnd = 0;
            return;
        }
        int half = WINDOW_SIZE / 2;
        visibleStart = Math.max(0, position - half);
        visibleEnd = Math.min(mItems.size(), visibleStart + WINDOW_SIZE);
        visibleStart = Math.max(0, visibleEnd - WINDOW_SIZE);
    }

    private void trimWindow() {
        visibleStart = Math.min(visibleStart, mItems.size());
        visibleEnd = Math.min(visibleEnd, mItems.size());
        if (visibleStart > visibleEnd) visibleStart = visibleEnd;
    }

    @Override
    public int getItemCount() {
        return visibleEnd - visibleStart;
    }

    @NonNull
    @Override
    public ChannelAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterChannelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelAdapter.ViewHolder holder, int position) {
        Channel item = mItems.get(visibleStart + position);
        String epg = item.getData().getCurrent().getTitle();
        item.loadLogo(holder.binding.logo);
        holder.binding.name.setText(item.getShow());
        holder.binding.number.setText(item.getNumber());
        holder.binding.epg.setText(epg);
        holder.binding.epg.setVisibility(epg.isEmpty() ? View.GONE : View.VISIBLE);
        setListStyle(holder);
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(view -> listener.onLongClick(item));
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelAdapter.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            holder.binding.getRoot().setSelected(mItems.get(visibleStart + position).isSelected());
        }
    }

    private void setListStyle(ViewHolder holder) {
        boolean classic = LiveSetting.isListStyleClassic();
        holder.binding.getRoot().setBackgroundResource(classic ? R.drawable.shape_live_classic : R.drawable.shape_live);
        holder.binding.name.setTextColor(ContextCompat.getColorStateList(holder.binding.name.getContext(), classic ? R.color.selector_live_text_classic : R.color.selector_live_text));
        holder.binding.number.setTextColor(ContextCompat.getColorStateList(holder.binding.number.getContext(), classic ? R.color.selector_live_text_classic : R.color.selector_live_text));
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        Glide.with(holder.binding.logo.getContext().getApplicationContext()).clear(holder.binding.logo);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterChannelBinding binding;

        ViewHolder(@NonNull AdapterChannelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
