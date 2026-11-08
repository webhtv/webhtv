package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterPlayerOsdBinding;
import com.fongmi.android.tv.databinding.DialogPlayerOsdBinding;
import com.fongmi.android.tv.theme.PlayerSettingsRowTheme;
import com.fongmi.android.tv.theme.ThemeController;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.Arrays;

public final class PlayerOsdDialog extends DialogFragment {

    private DialogPlayerOsdBinding binding;
    private OptionAdapter adapter;
    private String[] items;
    private boolean[] checked;
    private Callback callback;

    public interface Callback {

        void onApply(boolean[] checked);
    }

    public static void show(Fragment fragment, String[] items, boolean[] checked, Callback callback) {
        for (Fragment child : fragment.getChildFragmentManager().getFragments()) {
            if (child instanceof PlayerOsdDialog) return;
        }
        PlayerOsdDialog dialog = new PlayerOsdDialog();
        dialog.items = items == null ? new String[0] : Arrays.copyOf(items, items.length);
        dialog.checked = checked == null ? new boolean[0] : Arrays.copyOf(checked, checked.length);
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), PlayerOsdDialog.class.getSimpleName());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        binding = DialogPlayerOsdBinding.inflate(LayoutInflater.from(requireContext()));
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(binding.getRoot());
        dialog.setCanceledOnTouchOutside(true);
        if (items == null) items = new String[0];
        if (checked == null) checked = new boolean[0];
        initView();
        binding.close.setOnClickListener(view -> dismiss());
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        int screenWidth = ResUtil.getScreenWidth(requireContext());
        int screenHeight = ResUtil.getScreenHeight(requireContext());
        boolean land = ResUtil.isLand(requireContext());
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (screenWidth * (land ? 0.56f : 0.92f));
        params.height = (int) (screenHeight * (land ? 0.9f : 0.66f));
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
    }

    private void initView() {
        adapter = new OptionAdapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setItemAnimator(null);
        binding.recycler.setAdapter(adapter);
    }

    private void notifyChanged() {
        Callback apply = callback;
        if (apply != null) apply.onApply(Arrays.copyOf(checked, checked.length));
    }

    private class OptionAdapter extends RecyclerView.Adapter<OptionAdapter.ViewHolder> {

        OptionAdapter() {
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(AdapterPlayerOsdBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            boolean visible = checked[position];
            holder.binding.name.setText(items[position]);
            holder.binding.visible.setImageResource(visible ? R.drawable.ic_player_button_visible : R.drawable.ic_player_button_hidden);
            holder.binding.visible.setContentDescription(getString(visible ? R.string.setting_show : R.string.setting_hide));
            holder.binding.visible.setSelected(visible);
            holder.binding.visible.setActivated(visible);
            PlayerSettingsRowTheme.apply(holder.binding.root, holder.binding.name, ThemeController.current(), visible);
            PlayerSettingsRowTheme.applySwitch(holder.binding.visible, ThemeController.current());
            holder.binding.root.setOnClickListener(view -> toggle(holder.getBindingAdapterPosition()));
            holder.binding.visible.setOnClickListener(view -> toggle(holder.getBindingAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return Math.min(items.length, checked.length);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        private void toggle(int position) {
            if (position == RecyclerView.NO_POSITION || position < 0 || position >= getItemCount()) return;
            checked[position] = !checked[position];
            notifyItemChanged(position);
            notifyChanged();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {

            private final AdapterPlayerOsdBinding binding;

            ViewHolder(AdapterPlayerOsdBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
