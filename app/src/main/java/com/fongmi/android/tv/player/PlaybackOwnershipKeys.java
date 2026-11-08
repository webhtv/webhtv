package com.fongmi.android.tv.player;

import android.text.TextUtils;

import androidx.annotation.Nullable;

/**
 * 播放归属令牌的等价判定。
 *
 * <p>同一个 {@link PlayerManager} 会话里并存两种形态的 key：
 *
 * <ul>
 *   <li>Activity 侧写入的记录键：{@code <siteKey>@@@<vodId>@@@<cid>}（带 {@code @@} 前缀、
 *       不带线路名与集序号），由 {@code VideoActivity#getHistoryKey()} 现算并经
 *       {@code startPlayer(key, ...)} 固化进 {@code PlaySpec}。</li>
 *   <li>服务侧「浏览下一集」路径写入的选集键：{@code VE:<historyKey>|<线路名>|<集序号>}，
 *       由 {@code PlaybackService#navigateItem} → {@code VodBrowse#navigateEpisode} 构造，
 *       {@code PlaybackService#startBrowse} 把它原样作为 {@code PlaySpec} 的 key。</li>
 * </ul>
 *
 * <p>两者指向**同一条播放会话**，但字符串不相等。旧判定只做精确 {@code equals}，于是：
 * {@code PlaybackActivity#isOwner()} 变假 → 重建回调 {@code onPlayerRebuild()} 整体被跳过；
 * {@code PlaybackService#isNavigationOwner()} 同样变假。用户可观察后果是「自动下一集后在
 * 播放页里再也回不到自己的会话」，并且服务把下一次 ENDED 当成无主事件直接
 * {@code navigateItem(1)} 再跳一集。
 *
 * <p>本类只回答「两个 key 是否同属一次播放会话」这一个纯字符串问题，不持有状态、不接触
 * 播放器实例，因此可以被单元测试直接证伪。
 */
public final class PlaybackOwnershipKeys {

    /** 选集键前缀，与 {@code VodBrowse#VOD_EP} 同值；{@code browse} 包持有真正的定义。 */
    static final String EPISODE_PREFIX = "VE:";

    private static final char EPISODE_SEPARATOR = '|';

    private PlaybackOwnershipKeys() {
    }

    /**
     * 一次播放会话的归属令牌。
     *
     * <p>{@code null}/{@code ""} 表示「不限制归属」（沿用既有语义）；选集键会被规范化为它
     * 承载的记录键，其余 key 原样返回。两个非空结果相等，就说明是同一次播放会话。
     */
    @Nullable
    public static String sessionKeyOf(@Nullable String key) {
        if (TextUtils.isEmpty(key)) return null;
        if (!isEpisodeKey(key)) return key;
        String historyKey = historyKeyOfEpisode(key);
        return TextUtils.isEmpty(historyKey) ? key : historyKey;
    }

    /**
     * 两个 key 是否同属一次播放会话。
     *
     * <p>任一侧为空视为「不限制」，与既有 {@code key == null || key.equals(other)} 语义一致。
     */
    public static boolean sameSession(@Nullable String expected, @Nullable String actual) {
        String left = sessionKeyOf(expected);
        if (left == null) return true;
        String right = sessionKeyOf(actual);
        return right != null && left.equals(right);
    }

    /** 选集键 {@code VE:<historyKey>|<线路名>|<集序号>} 承载的记录键；非选集键或结构不完整返回 {@code null}。 */
    @Nullable
    static String historyKeyOfEpisode(@Nullable String key) {
        if (!isEpisodeKey(key)) return null;
        String body = key.substring(EPISODE_PREFIX.length());
        // 记录键本身可能含 '|'（线路名同理），所以从右往左裁掉「集序号」「线路名」两段。
        int last = body.lastIndexOf(EPISODE_SEPARATOR);
        if (last <= 0) return null;
        int previous = body.lastIndexOf(EPISODE_SEPARATOR, last - 1);
        if (previous <= 0) return null;
        return body.substring(0, previous);
    }

    private static boolean isEpisodeKey(@Nullable String key) {
        return !TextUtils.isEmpty(key) && key.startsWith(EPISODE_PREFIX);
    }
}
