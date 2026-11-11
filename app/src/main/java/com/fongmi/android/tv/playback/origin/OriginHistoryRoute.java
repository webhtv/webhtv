package com.fongmi.android.tv.playback.origin;

/**
 * 跨源历史的入口分流：判据与当前源历史逐项同构（详情页模式 + 站点 TMDB 策略），
 * 不含任何"历史必须已存 TMDB 身份"的前置条件——TMDB 匹配由详情页自己完成。
 *
 * <p>纯函数、不触碰 Android API，便于直接用 JVM 单测锁住"该进 TMDB 详情还是直接播放"。
 */
public final class OriginHistoryRoute {

    public static final String TMDB_DETAIL = "tmdb_detail";
    public static final String DIRECT_MODE = "direct:detail_mode_not_tmdb";

    private OriginHistoryRoute() {
    }

    /**
     * @param tmdbDetailModeConfigured {@code Setting.isTmdbDetailModeConfigured()}
     * @param standaloneTmdbMode       {@code Setting.isStandaloneTmdbDetailMode(Setting.getDetailOpenMode())}
     * @param siteEnabled              {@code TmdbSitePolicy.isEnabled(history.getSiteKey(), history.getVodId())}
     */
    public static String describe(boolean tmdbDetailModeConfigured, boolean standaloneTmdbMode, boolean siteEnabled) {
        if (!tmdbDetailModeConfigured || !standaloneTmdbMode || !siteEnabled) return DIRECT_MODE;
        return TMDB_DETAIL;
    }

    public static boolean isTmdbDetail(String route) {
        return TMDB_DETAIL.equals(route);
    }
}
