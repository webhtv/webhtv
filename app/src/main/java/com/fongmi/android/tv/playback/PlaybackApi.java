package com.fongmi.android.tv.playback;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlaybackApi {

    /** 单次读取的默认条数（对齐 `PlaybackRemoteSyncer` 的 `maxItems` 默认 100 的量级）。 */
    public static final int DEFAULT_HISTORY_LIMIT = 20;

    /** 单次读取的条数上限（防止一次拉爆内存与响应体）。 */
    public static final int MAX_HISTORY_LIMIT = 100;

    private PlaybackApi() {
    }

    @Nullable
    public static PlaybackRecord current(String callerSiteKey) {
        if (TextUtils.isEmpty(callerSiteKey) || Setting.isIncognito()) return null;
        History history = PlaybackRuntime.currentHistory();
        if (!PlaybackRecord.canCreate(history)) return null;
        if (!TextUtils.equals(callerSiteKey, history.getSiteKey())) return null;
        PlayerManager player = PlaybackRuntime.playerFor(history.getKey());
        String sessionId = PlaybackRuntime.ensureSession(history);
        return PlaybackRecord.from(history, player, null, sessionId).copyFor(PlaybackFieldPolicy.apiSafe());
    }

    /**
     * 按站点读取历史（从**数据库**读，不依赖播放内存态）。
     *
     * <p>为什么需要它：{@link #current} 的数据源是
     * {@link PlaybackRuntime#currentHistory()}——一个只在**播放中**有值的
     * static 内存字段，未播放时恒为 {@code null}。因此它在息屏或未播放时
     * 必然 404，无法支撑"定时把历史同步给对端"这类需求。本方法从
     * {@code HistoryDao} 读，只要记录在库里就能返回。
     *
     * <p>字段裁剪与 {@link #current} 一致（{@link PlaybackFieldPolicy#apiSafe()}），
     * 因此不会因为新增端点而多暴露字段。
     *
     * <p>隐身模式返回空列表（与 {@link #current} 返回 {@code null} 同源）：
     * 隐身是用户显式表达的"不要记录/不要外泄"意图。
     *
     * @param siteKey 站点 key；为空时返回空列表（不报错，由调用方判 400）
     * @param limit   条数上限；{@code <= 0} 用 {@link #DEFAULT_HISTORY_LIMIT}，
     *                超过 {@link #MAX_HISTORY_LIMIT} 则夹取到上限
     */
    public static List<PlaybackRecord> history(String siteKey, int limit) {
        if (TextUtils.isEmpty(siteKey) || Setting.isIncognito()) return Collections.emptyList();
        int effective = effectiveHistoryLimit(limit);
        List<History> rows;
        try {
            rows = AppDatabase.get().getHistoryDao()
                    .findBySiteKey(VodConfig.getCid(), siteKey + AppDatabase.SYMBOL, effective);
        } catch (Throwable e) {
            // 读库失败必须留下痕迹：静默返回空数组会让调用方把「库挂了」误读成
            // 「这个站点没有历史」，排查时完全失去线索。
            // 注意：只打 SpiderDebug 不够——它的每条重载先过 DebugLogStore.acceptsTag()，
            // 而 DebugLogStore.enabled 默认 false（要用户手动开「调试日志」），
            // 也就是默认发行版里这条日志根本不会落盘。printStackTrace 走 Logcat 无条件可见，
            // 两者并存才能既进诊断面板、又不依赖用户设置。
            SpiderDebug.log("playback-api", e);
            e.printStackTrace();
            return Collections.emptyList();
        }
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        List<PlaybackRecord> records = new ArrayList<>(rows.size());
        for (History history : rows) {
            try {
                if (!PlaybackRecord.canCreate(history)) continue;
                // player 传 null：这些记录不在播放中，没有实时位置可读，
                // 位置/时长直接取 History 自身落库的值（`PlaybackRecord.from` 已处理 null）。
                records.add(PlaybackRecord.from(history, null, null, "")
                        .copyFor(PlaybackFieldPolicy.apiSafe()));
            } catch (Throwable e) {
                // 单条坏记录不该让整批读取失败（与对端「部分失败不推进游标」的语义一致）。
                // 同样补 printStackTrace：坏记录是「历史里少了一条」的唯一解释点。
                SpiderDebug.log("playback-api", e);
                e.printStackTrace();
            }
        }
        return records;
    }

    /**
     * 把请求的条数夹到合法区间。
     *
     * <p>公开而不是内联：这是纯函数且是对端能直接影响的输入，
     * 必须有单测锁定边界（否则一次 {@code limit=999999} 就能拉爆内存）。
     *
     * @param limit 请求值；{@code <= 0} 用默认，超过上限则夹到上限
     */
    public static int effectiveHistoryLimit(int limit) {
        return limit <= 0 ? DEFAULT_HISTORY_LIMIT : Math.min(limit, MAX_HISTORY_LIMIT);
    }
}
