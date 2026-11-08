package com.fongmi.android.tv.player;

/**
 * 直播回退策略：区分「线路播放失败」与「直播接口节目列表拉取失败」两种失败。
 * <p>
 * 用户契约（2026-10 用户反馈）：
 * <ul>
 *     <li>线路播放失败 → 只在当前频道的线路间轮换（含最后一条绕回），试完一圈即停，
 *     绝不因播放失败直接跳到配置里的下一个直播接口；</li>
 *     <li>只有当直播接口拉取不到节目列表时，才直接跳转到下一个直播接口。</li>
 * </ul>
 */
public final class LiveSourceFallbackPolicy {

    public enum Action {
        NONE,
        NEXT_LINE,
        NEXT_SOURCE
    }

    private LiveSourceFallbackPolicy() {
    }

    /**
     * 线路播放失败的处理：优先（且只）换当前频道的下一条线路。
     * <p>
     * 换线不受 {@code sourceFallbackEnabled}（直播源失效自动切换）控制，而由
     * {@code changeLineEnabled}（换台自动换线路）决定；线路轮换的"绕一圈即停"防循环
     * 由调用方（LiveActivity#advanceLineForFallback）保证，此处只决定是否换线。
     * 播放失败永远不会返回 {@link Action#NEXT_SOURCE}。
     */
    public static Action decideLineFailure(boolean changeLineEnabled, boolean channelPresent, boolean multiLine) {
        if (channelPresent && changeLineEnabled && multiLine) return Action.NEXT_LINE;
        return Action.NONE;
    }

    /**
     * 直播接口节目列表拉取失败的处理：开启「直播源失效自动切换」且有下一个接口时，
     * 直接跳转到下一个直播接口。
     */
    public static Action decideSourceFailure(boolean sourceFallbackEnabled, boolean nextSourceAvailable) {
        if (!sourceFallbackEnabled) return Action.NONE;
        return nextSourceAvailable ? Action.NEXT_SOURCE : Action.NONE;
    }
}
