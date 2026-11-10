package com.fongmi.android.tv.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterDownloadBinding;
import com.fongmi.android.tv.download.DownloadTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 下载任务列表适配器：显示名称/进度/状态，并按任务状态提供 暂停/继续/重试/取消/删除/播放 按钮。 */
public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.ViewHolder> {

    public interface OnClickListener {
        void onPause(DownloadTask task);    // 暂停进行中的任务，保留 .part 便于续传
        void onResume(DownloadTask task);   // 继续已暂停的任务
        void onRetry(DownloadTask task);    // 失败重试（优先续传）
        void onCancel(DownloadTask task);   // 取消进行中的任务，清理分片
        void onRemove(DownloadTask task);   // 删除任务记录及其成品文件
        void onPlay(DownloadTask task);     // 播放已完成的成品
    }

    private final OnClickListener listener;
    private final List<DownloadTask> items = new ArrayList<>();

    public DownloadAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<DownloadTask> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDownloadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DownloadTask t = items.get(position);
        holder.binding.name.setText(t.name);
        holder.binding.status.setText(describe(holder.binding.getRoot().getContext(), t));
        if (t.totalSize > 0) holder.binding.progress.setProgress((int) Math.min(100L, t.downloadedBytes * 100 / t.totalSize));
        else if (t.totalSegments > 0) holder.binding.progress.setProgress(Math.min(100, t.completedSegments * 100 / t.totalSegments));
        else holder.binding.progress.setProgress(0);
        bindActions(holder, t);
    }

    /** 按任务状态决定按钮：运行中[暂停/取消]、已暂停[继续/取消]、失败[重试/删除]、已完成[播放/删除]。 */
    private void bindActions(ViewHolder holder, DownloadTask t) {
        switch (t.status) {
            case RUNNING:
            case PENDING:
                setPrimary(holder, R.string.download_action_pause, v -> listener.onPause(t));
                setSecondary(holder, R.string.download_manager_cancel, v -> listener.onCancel(t));
                break;
            case PAUSED:
                setPrimary(holder, R.string.download_action_resume, v -> listener.onResume(t));
                setSecondary(holder, R.string.download_manager_cancel, v -> listener.onCancel(t));
                break;
            case FAILED:
                setPrimary(holder, R.string.download_action_retry, v -> listener.onRetry(t));
                setSecondary(holder, R.string.download_manager_delete, v -> listener.onRemove(t));
                break;
            default:  // COMPLETED / CANCELLED
                // 成品还在就给播放入口；文件被外部删掉时仍只留删除，避免点了没反应
                if (t.status == DownloadTask.Status.COMPLETED && outputFile(t) != null) {
                    setPrimary(holder, R.string.play, v -> listener.onPlay(t));
                } else {
                    holder.binding.action.setVisibility(View.GONE);
                }
                setSecondary(holder, R.string.download_manager_delete, v -> listener.onRemove(t));
                break;
        }
    }

    /** 成品文件；不存在或长度为 0 都视为不可播放。 */
    public static File outputFile(DownloadTask t) {
        try {
            File f = new File(new File(t.outputDir), t.name + "." + t.ext);
            return f.exists() && f.length() > 0 ? f : null;
        } catch (Exception e) {
            return null;
        }
    }
 
    private void setPrimary(ViewHolder holder, int text, View.OnClickListener click) {
        holder.binding.action.setVisibility(View.VISIBLE);
        holder.binding.action.setText(text);
        holder.binding.action.setOnClickListener(click);
    }
 
    private void setSecondary(ViewHolder holder, int text, View.OnClickListener click) {
        holder.binding.action2.setVisibility(View.VISIBLE);
        holder.binding.action2.setText(text);
        holder.binding.action2.setOnClickListener(click);
    }
 
    private String describe(Context ctx, DownloadTask t) {
        String size = t.totalSize > 0
                ? formatSize(t.downloadedBytes) + " / " + formatSize(t.totalSize)
                : formatSize(t.downloadedBytes);
        String status;
        switch (t.status) {
            case COMPLETED: status = ctx.getString(R.string.download_done_notify_title); break;
            case FAILED: status = ctx.getString(R.string.download_failed_notify_title); break;
            case CANCELLED: status = ctx.getString(R.string.download_manager_cancelled); break;
            case RUNNING: status = ctx.getString(R.string.download_notify_title); break;
            case PAUSED: status = ctx.getString(R.string.download_status_paused); break;
            default: status = ctx.getString(R.string.download_manager_pending); break;
        }
        return status + " · " + size;
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int i = 0;
        double v = bytes;
        while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
        return String.format("%.1f %s", v, units[i]);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final AdapterDownloadBinding binding;

        ViewHolder(AdapterDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
