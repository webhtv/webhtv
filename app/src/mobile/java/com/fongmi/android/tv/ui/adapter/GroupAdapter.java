package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.databinding.AdapterGroupBinding;
import com.fongmi.android.tv.setting.LiveSetting;

import java.util.ArrayList;
import java.util.List;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.ViewHolder> {

    private static final Object PAYLOAD_SELECTED = new Object();

    private final OnClickListener listener;
    private final List<Group> mItems;

    public GroupAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void setWidth(Group item);

        void onItemClick(Group item);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void addAll(List<Group> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void add(Group item) {
        mItems.add(item);
        notifyItemInserted(getItemCount() - 1);
    }

    public Group get(int position) {
        return mItems.get(position);
    }

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    public int indexOf(Group group) {
        return mItems.indexOf(group);
    }

    public void setSelected(Group group) {
        setSelected(indexOf(group));
    }

    public void setSelected(int position) {
        if (position < 0 || position >= mItems.size()) return;
        for (int i = 0; i < mItems.size(); i++) {
            Group item = mItems.get(i);
            boolean selected = i == position;
            if (item.isSelected() == selected) continue;
            item.setSelected(selected);
            notifyItemChanged(i, PAYLOAD_SELECTED);
        }
        listener.setWidth(mItems.get(position));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterGroupBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Group item = mItems.get(position);
        holder.binding.name.setText(item.getDisplayName());
        setListStyle(holder);
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            holder.binding.getRoot().setSelected(mItems.get(position).isSelected());
        }
    }

    private void setListStyle(ViewHolder holder) {
        boolean classic = LiveSetting.isListStyleClassic();
        holder.binding.name.setBackgroundResource(classic ? R.drawable.shape_live_classic : R.drawable.shape_live);
        holder.binding.name.setTextColor(ContextCompat.getColorStateList(holder.binding.name.getContext(), classic ? R.color.selector_live_text_classic : R.color.selector_live_text));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterGroupBinding binding;

        ViewHolder(@NonNull AdapterGroupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
