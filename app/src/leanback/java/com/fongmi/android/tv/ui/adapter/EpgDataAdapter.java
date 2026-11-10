package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.databinding.AdapterEpgDataBinding;

import java.util.ArrayList;
import java.util.List;

public class EpgDataAdapter extends RecyclerView.Adapter<EpgDataAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Item> mItems;

    public EpgDataAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    /**
     * [NEW] 一天节目单 + 这一天要显示的日期标签（今天传空串即不显示日期）。
     */
    public static class Day {

        public final Epg epg;
        public final String label;

        public Day(Epg epg, String label) {
            this.epg = epg;
            this.label = label;
        }
    }

    private static class Item {

        final EpgData data;
        final String label;      // 所属那天的日期标签，同一天每条都一样
        final boolean firstOfDay; // 是不是这天的第一条，用于吸顶切换

        Item(EpgData data, String label, boolean firstOfDay) {
            this.data = data;
            this.label = label;
            this.firstOfDay = firstOfDay;
        }
    }

    /**
     * [NEW] 整体替换为若干天，按传入顺序（由早到晚）展平。
     */
    public void setDays(List<Day> days) {
        mItems.clear();
        append(mItems, days);
        notifyDataSetChanged();
    }

    /**
     * [NEW] 在顶部插入若干天（往日节目单），返回插入的条目数，0 表示没有数据。
     */
    public int insertDaysTop(List<Day> days) {
        List<Item> add = new ArrayList<>();
        append(add, days);
        if (add.isEmpty()) return 0;
        mItems.addAll(0, add);
        notifyItemRangeInserted(0, add.size());
        return add.size();
    }

    /**
     * [NEW] 在底部追加若干天（明日节目预告），返回插入的起始位置，-1 表示没有数据。
     */
    public int appendDaysBottom(List<Day> days) {
        List<Item> add = new ArrayList<>();
        append(add, days);
        if (add.isEmpty()) return -1;
        int start = mItems.size();
        mItems.addAll(add);
        notifyItemRangeInserted(start, add.size());
        return start;
    }

    private void append(List<Item> target, List<Day> days) {
        for (Day day : days) {
            if (day == null || day.epg == null) continue;
            List<EpgData> list = day.epg.getList();
            if (list.isEmpty()) continue;
            for (int i = 0; i < list.size(); i++) target.add(new Item(list.get(i), day.label, i == 0));
        }
    }

    public void addAll(List<EpgData> items) {
        mItems.clear();
        for (EpgData item : items) mItems.add(new Item(item, "", false));
        notifyDataSetChanged();
    }

    /** [NEW] 某一条属于哪一天，给吸顶用 */
    public String getLabel(int position) {
        if (position < 0 || position >= mItems.size()) return "";
        return mItems.get(position).label;
    }

    /** [NEW] 这条是不是新一天的第一条 */
    public boolean isFirstOfDay(int position) {
        if (position < 0 || position >= mItems.size()) return false;
        return mItems.get(position).firstOfDay;
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public EpgData get(int position) {
        return mItems.get(position).data;
    }

    public void setSelected(EpgData selected) {
        for (Item item : mItems) item.data.setSelected(selected);
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
        Item item = mItems.get(position);
        EpgData data = item.data;
        holder.binding.time.setText(data.getTime());
        holder.binding.title.setText(data.getTitle());
        holder.binding.getRoot().setSelected(data.isSelected());

        if (data.isInRange()) {
            holder.binding.tvLiveTag.setVisibility(View.VISIBLE);
            holder.binding.tvLiveTag.setText(com.fongmi.android.tv.R.string.live_program_current);
        } else {
            holder.binding.tvLiveTag.setVisibility(View.GONE);
        }

        holder.binding.getRoot().setOnClickListener(v -> {
            if (!data.isFuture()) mListener.onItemClick(data);
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
