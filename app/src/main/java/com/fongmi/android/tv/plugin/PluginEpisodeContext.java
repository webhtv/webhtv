package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 宿主向插件提供"当前剧集上下文"的唯一通道。
 *
 * <p><b>为什么单独一个类，而不是塞进事件 payload。</b>事件总线
 * {@link PluginEventBus} 的 payload 只能承载 {@code String -> String}，塞一份结构化上下文
 * 就得在每次页面切换时做一次序列化；而槽位点击时宿主需要的是"此刻这一页是哪部剧"，
 * 是状态而不是消息。把状态放在这里，点击时按需取一次，代价只落在真正发生的点击上。
 *
 * <p><b>为什么要绑定 Activity 简名。</b>同一份上下文只对发布它的那一页有效。插件在
 * 播放页被点击时，若拿到的还是上一页详情页的剧集，评论就会串台——这种错误在用户看来
 * 是"评论内容不对"，在日志里却毫无痕迹。因此 {@link #current(String)} 做名称比对，
 * 不匹配一律返回 {@code null}，宁可让插件少一份数据，也不给它一份别人的数据。
 *
 * <p><b>线程约束。</b>{@link #publish} 在主线程（页面拿到剧集时），{@link #current}
 * 在 {@code plugin-action} 线程（槽位点击后）。发布与读取通过单一不可变快照交接，
 * 既不会读到"新名字 + 旧内容"的半更新状态，也不需要加锁。
 */
public final class PluginEpisodeContext {

    /**
     * 名字与内容一次性交接。
     *
     * <p>拆成两个 volatile 字段会出现撕裂：读者可能看到新的 activityName 配旧的 json，
     * 而那正好是"评论串台"的形态。用一个不可变对象做整体替换，读到的永远是同一版。
     */
    private record Snapshot(String activityName, String json) {
    }

    private static volatile Snapshot snapshot;

    private PluginEpisodeContext() {
    }

    /**
     * 发布当前页面的剧集上下文。
     *
     * @param activityName 宿主 Activity 简名；为空时本方法**什么都不做**——不要指望它顺手清掉
     *                     别人发布的内容
     * @param context      只读剧集字段，由 {@link PluginEpisodePayload} 产出；为 {@code null}
     *                     时清除该名字下已发布的上下文，名字不匹配则不清
     */
    public static void publish(@Nullable String activityName, @Nullable JsonObject context) {
        if (activityName == null || activityName.isEmpty() || context == null) {
            clear(activityName);
            return;
        }
        snapshot = new Snapshot(activityName, context.toString());
    }

    /** 清除上下文，但只在名字匹配时清——别的页面发布的内容不该被这一页的销毁带走。 */
    public static void clear(@Nullable String activityName) {
        Snapshot current = snapshot;
        if (current == null || activityName == null || activityName.isEmpty()) return;
        if (!current.activityName().equals(activityName)) return;
        snapshot = null;
    }

    /**
     * 取当前页面的上下文。
     *
     * @return 名称匹配时返回**深拷贝**（调用方可随意修改），不匹配或解析失败返回 {@code null}
     */
    @Nullable
    public static JsonObject current(@Nullable String activityName) {
        Snapshot current = snapshot;
        if (current == null || activityName == null || activityName.isEmpty()) return null;
        if (!current.activityName().equals(activityName)) return null;
        try {
            JsonElement parsed = JsonParser.parseString(current.json());
            if (parsed == null || !parsed.isJsonObject()) return null;
            return parsed.getAsJsonObject().deepCopy();
        } catch (Throwable error) {
            // 内容由宿主自己写入，理论上不会坏；坏掉时给 null 而不是抛给点击路径。
            return null;
        }
    }
}
