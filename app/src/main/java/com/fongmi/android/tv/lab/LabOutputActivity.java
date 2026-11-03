package com.fongmi.android.tv.lab;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityLabOutputBinding;

import java.util.HashMap;

public class LabOutputActivity extends AppCompatActivity {

    private static LabOutputActivity sInstance;

    private ActivityLabOutputBinding mBinding;
    private LabModels.Item item;
    private LabModels.Command command;
    private String itemName;
    private String commandId;
    private HashMap<String, String> vars;

    public static void start(Context context, String itemName, String commandId, HashMap<String, String> vars) {
        Intent intent = new Intent(context, LabOutputActivity.class);
        intent.putExtra("item", itemName);
        intent.putExtra("command", commandId);
        intent.putExtra("vars", vars);
        context.startActivity(intent);
    }

    public static void appendGlobal(String text) {
        if (sInstance != null) sInstance.append(text);
    }

    public static void onExitGlobal(int code) {
        if (sInstance != null) sInstance.onExit(code);
    }

    private String key() {
        return itemName + "/" + commandId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityLabOutputBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        sInstance = this;

        itemName = getIntent().getStringExtra("item");
        commandId = getIntent().getStringExtra("command");
        //noinspection unchecked
        vars = (HashMap<String, String>) getIntent().getSerializableExtra("vars");
        if (vars == null) vars = new HashMap<>();

        item = findItem(itemName);
        command = findCommand(item, commandId);

        mBinding.btnClose.setOnClickListener(v -> finish());
        mBinding.btnClear.setOnClickListener(v -> clearLog());
        mBinding.btnStop.setOnClickListener(v -> stop());
        mBinding.btnSend.setOnClickListener(v -> sendInput());
        mBinding.inputEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_GO) {
                sendInput();
                return true;
            }
            return false;
        });

        mBinding.outputTitle.setText(command == null ? (commandId == null ? "输出" : commandId)
                : (command.name == null ? command.description : command.name));

        String log = LabRunner.getLog(key());
        if (!TextUtils.isEmpty(log)) mBinding.outputText.setText(log);

        boolean running = LabRunner.isRunning(key());
        showRunning(running);
    }

    private LabModels.Item findItem(String name) {
        if (name == null) return null;
        LabModels.LabRoot root = LabConfig.get().getLabRoot();
        if (root != null && root.lists != null) {
            for (LabModels.Item candidate : root.lists) {
                if (name.equals(candidate.name)) return candidate;
            }
        }
        return null;
    }

    private LabModels.Command findCommand(LabModels.Item item, String id) {
        if (item == null || id == null || item.commands == null) return null;
        for (LabModels.Command c : item.commands) {
            if (id.equals(c.id)) return c;
        }
        return null;
    }

    private void showRunning(boolean running) {
        mBinding.btnStop.setVisibility(running ? View.VISIBLE : View.GONE);
        boolean interactive = running && command != null && command.isShowOutput() && !command.isBackground();
        mBinding.inputContainer.setVisibility(interactive ? View.VISIBLE : View.GONE);
    }

    private void append(String text) {
        App.post(() -> {
            mBinding.outputText.append(text);
            NestedScrollView scroll = mBinding.outputScroll;
            int range = Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight());
            boolean atBottom = scroll.getScrollY() >= range - 4;
            if (atBottom) scroll.fullScroll(View.FOCUS_DOWN);
        });
    }

    private void onExit(int code) {
        App.post(() -> {
            append("\n[进程结束，退出码 " + code + "]\n");
            showRunning(false);
        });
    }

    private void stop() {
        LabRunner.stop(key());
        showRunning(false);
        append("\n=== 进程已停止 ===\n");
    }

    /**
     * 清 UI 先由点击线程 {@link App#post} 入队，之后才让后台线程清缓冲与截文件：
     *
     * <p>「清缓冲 + 截文件」必须离开点击线程——{@link LabRunner#clearLog} 要与 pump 每行的 flush
     * 争同一把锁，慢存储上会卡住输入事件。
     *
     * <p>UI 清空必须先用点击线程入队，不能等 {@code clearLog} 返回后再入队：{@link #append} 只投递到
     * TextView、不写缓冲与文件，停止横幅、退出码、输入回显、转后台提示这类行只存在于界面与主线程
     * 队列里；若延后入队，这段时间产生的行会排在 {@code setText("")} 之前，被它永久抹掉且无处找回。
     *
     * <p>回调里写空串而不回填 {@link LabRunner#getLog}：回填时缓冲与文件可能已被后台清掉、也可能还没
     * 清，回填结果与仍在途的 {@code append} 无法判重，界面会重复或短暂回退到旧内容。
     *
     * <p>截断跑在自建线程上而不是 {@code Task.execute}：{@code Task} 是全 app 共享的固定 5 线程池
     * （Web 请求、清单解析都在用），池被占满时截断会无限期排队；这里是一次性的用户点按，自建线程不受
     * 其影响，也与 pump 自建读者线程的做法一致。界面清空走的是主线程队列、不经过该池，所以文本在点按后
     * 下一帧就清空；落盘截断要等后台线程取到 pump 的缓冲锁，若正在写一行则顺延。不再额外同步
     * {@code setText("")} 一次，否则已入队的行会先冒出来再被抹掉，出现闪烁。
     *
     * <p>已知代价：点击到后台拿到锁之间新产生的 pump 行（可能不止一条，取决于线程调度），其
     * {@code append} 排在 {@code setText("")} 之后因而留在界面上，但缓冲与文件里的副本被这次清除一并
     * 截掉——重进输出页时这些行不再出现。反之，重进输出页若正落在「UI 已清、后台未截」的窗口里，
     * onCreate 的回填会短暂显示旧日志。
     */
    private void clearLog() {
        String logKey = key();
        App.post(() -> {
            mBinding.outputText.setText("");
            mBinding.outputScroll.scrollTo(0, 0);
        });
        new Thread(() -> LabRunner.clearLog(logKey), "lab-clear-log").start();
    }

    private void sendInput() {
        String text = mBinding.inputEdit.getText() == null ? "" : mBinding.inputEdit.getText().toString();
        if (text.isEmpty()) return;
        mBinding.inputEdit.setText("");
        append("$ " + text + "\n");
        if (!LabRunner.writeInput(key(), text)) {
            append("[发送失败：进程可能不支持交互输入]\n");
        }
    }

    @Override
    protected void onDestroy() {
        if (sInstance == this) sInstance = null;
        super.onDestroy();
    }
}
