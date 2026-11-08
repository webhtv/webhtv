package com.fongmi.android.tv.lab;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;

import java.util.ArrayList;
import java.util.List;

public final class LabCommandAdapter extends RecyclerView.Adapter<LabCommandAdapter.Holder> {

    public interface Listener {
        void onOpen(LabModels.Item item, LabModels.Command command);

        void onAction(LabModels.Item item, LabModels.Command command);

        void onLongPress(LabModels.Item item, LabModels.Command command);
    }

    private final List<LabModels.Command> rows = new ArrayList<>();
    private final List<String> rowStates = new ArrayList<>();
    private final Listener listener;
    private final Context context;
    private LabModels.Item item;

    public LabCommandAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItem(LabModels.Item item) {
        this.item = item;
        rows.clear();
        if (item != null) {
            if (item.commands != null) rows.addAll(item.commands);
            List<LabCustomCommands.CustomCommand> customs = LabCustomCommands.list(item.name);
            for (int i = 0; i < customs.size(); i++) {
                LabCustomCommands.CustomCommand custom = customs.get(i);
                LabModels.Command command = new LabModels.Command();
                command.id = custom.id != null ? custom.id : ("custom_" + i);
                command.name = custom.name;
                command.description = custom.description;
                command.command = custom.command;
                command.auto_execute = custom.autoExecute;
                rows.add(command);
            }
        }
        rowStates.clear();
        for (LabModels.Command command : rows) rowStates.add(stateOf(command));
        notifyDataSetChanged();
    }

    /**
     * 只刷新状态真正变化的行。
     *
     * <p>详情页每 2 秒调用一次 {@code notifyDataSetChanged()} 时，RecyclerView 会把全部子 View
     * 回收重挂：焦点所在的「运行」按钮被移除后，框架把焦点丢回工具栏的返回键，
     * 遥控器再按不到命令列表（实测表现就是「遥控器没法操作焦点，只能在实验室配置文件里控制启动」）。
     *
     * <p>状态没变时这里完全不触发重排，焦点保持不动；真变化时只做局部 {@code notifyItemChanged}，
     * 复用同一个 ViewHolder，不会像整表刷新那样回收焦点所在的视图。
     */
    public void refreshStates() {
        for (int i = 0; i < rows.size(); i++) {
            String state = stateOf(rows.get(i));
            if (i < rowStates.size() && state.equals(rowStates.get(i))) continue;
            if (i < rowStates.size()) rowStates.set(i, state);
            else rowStates.add(state);
            notifyItemChanged(i);
        }
    }

    /** 行内全部可视差异（圆点、运行标签、按钮文字与底色）都由这两个字段决定。 */
    private String stateOf(LabModels.Command command) {
        boolean running = item != null && LabRunner.isRunning(item.name + "/" + command.id);
        return (running ? "running:" : "idle:") + (command.name == null ? command.description : command.name);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.adapter_lab_command_compact, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LabModels.Command command = rows.get(position);
        boolean running = item != null && LabRunner.isRunning(item.name + "/" + command.id);
        holder.name.setText(command.name == null ? command.description : command.name);
        holder.statusDot.setBackgroundResource(running ? R.drawable.shape_lab_status_dot_running : R.drawable.shape_lab_status_dot_idle);
        holder.runningTag.setVisibility(running ? View.VISIBLE : View.GONE);
        holder.btnAction.setText(running ? R.string.lab_stop : R.string.lab_run);
        holder.btnAction.setBackgroundResource(running ? R.drawable.shape_lab_stop_btn : R.drawable.shape_lab_run_btn);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onOpen(item, command);
        });
        holder.btnAction.setOnClickListener(v -> {
            if (listener != null) listener.onAction(item, command);
        });
        // 遥控器上焦点落在右侧「运行」按钮时按键事件只发给它，itemView 的长按收不到，
        // 所以两个 View 共用同一个长按监听器：按住 OK 到长按阈值进编辑，短按仍是运行/停止。
        View.OnLongClickListener longPress = v -> {
            if (listener != null) listener.onLongPress(item, command);
            return true;
        };
        holder.itemView.setOnLongClickListener(longPress);
        holder.btnAction.setOnLongClickListener(longPress);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View statusDot;
        final TextView name;
        final TextView runningTag;
        final TextView btnAction;

        Holder(View itemView) {
            super(itemView);
            statusDot = itemView.findViewById(R.id.statusDot);
            name = itemView.findViewById(R.id.name);
            runningTag = itemView.findViewById(R.id.runningTag);
            btnAction = itemView.findViewById(R.id.btnAction);
        }
    }
}
