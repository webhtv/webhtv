package com.fongmi.android.tv.playback.origin;

/**
 * 跨源历史的入口分流：与当前源历史同一套详情页模式判断，只是身份来自原历史。
 *
 * <p>纯函数、不触碰 Android API，便于直接用 JVM 单测锁住"该进 TMDB 详情还是直接播放"。
 */
public final class OriginHistoryRoute {

    public static final String TMDB_DETAIL = "tmdb_detail";
    public static final String DIRECT_MODE = "direct:detail_mode_not_tmdb";
    public static final String DIRECT_IDENTITY = "direct:history_without_tmdb_identity";

    private OriginHistoryRoute() {
    }

    /**
     * @param tmdbDetailModeConfigured {@code Setting.isTmdbDetailModeConfigured()}
     * @param standaloneTmdbMode       {@code Setting.isStandaloneTmdbDetailMode(Setting.getDetailOpenMode())}
     * @param tmdbId                   原历史的 TMDB 数字 ID
     * @param mediaType                原历史的 TMDB 媒体类型
     */
    public static String describe(boolean tmdbDetailModeConfigured, boolean standaloneTmdbMode, int tmdbId, String mediaType) {
        if (tmdbId <= 0 || mediaType == null || mediaType.trim().isEmpty()) return DIRECT_IDENTITY;
        if (!tmdbDetailModeConfigured || !standaloneTmdbMode) return DIRECT_MODE;
        return TMDB_DETAIL;
    }

    public static boolean isTmdbDetail(String route) {
        return TMDB_DETAIL.equals(route);
    }
}
