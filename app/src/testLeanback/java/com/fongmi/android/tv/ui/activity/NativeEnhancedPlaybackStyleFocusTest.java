package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 原生增强播放页的视觉统一与焦点链路契约。
 *
 * 用户报告的三类问题：
 * 1. 同一页存在多套按钮风格（4dp/8dp/14dp/28dp 圆角、白/黄两套焦点环、部分卡片无描边）；
 * 2. 选中后的高亮颜色不统一（白色、黄色、以及完全没有描边）；
 * 3. 焦点不完善：聚焦在分段（分组）按钮上按向下到不了季度按钮，评分与数据卡片无法选中。
 *
 * 本测试把「唯一规范」固化成契约：焦点 3dp tv_item_focus_ring，当前态 2dp tv_item_current_ring，
 * 常态 1dp tv_item_normal_stroke，圆角 8dp；并锁定纵向焦点链的顺序。
 */
public class NativeEnhancedPlaybackStyleFocusTest {

    private static final String CHIP_SELECTOR = "app/src/leanback/res/drawable/selector_video_item.xml";
    private static final String EPISODE_CARD_SELECTOR = "app/src/main/res/drawable/selector_episode_card.xml";
    private static final String CAST_FOCUS_SELECTOR = "app/src/main/res/drawable/selector_tmdb_cast_focus.xml";
    private static final String COLORS = "app/src/main/res/values/colors.xml";
    private static final String ATTRS = "app/src/main/res/values/attrs.xml";
    private static final String LEANBACK_STYLES = "app/src/leanback/res/values/styles.xml";
    private static final String MOBILE_STYLES = "app/src/mobile/res/values/styles.xml";
    private static final String VIDEO_ACTIVITY = "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java";
    private static final String VIDEO_LAYOUT = "app/src/leanback/res/layout/activity_video.xml";

    // ---------------------------------------------------------------- 统一规范本身

    @Test
    public void unifiedFocusTokensAreDeclaredOnceAndThemeable() throws Exception {
        String colors = read(COLORS);
        assertTrue("焦点环色必须只有一个来源", colors.contains("<color name=\"tv_item_focus_ring\">#FFD166</color>"));
        assertTrue("当前播放环色必须只有一个来源", colors.contains("<color name=\"tv_item_current_ring\">#2CC56F</color>"));
        assertTrue("常态描边色必须只有一个来源", colors.contains("<color name=\"tv_item_normal_stroke\">#33FFFFFF</color>"));

        // 焦点语义必须可被主题覆写，而不是散落的字面量：属性定义 + 两个 flavor 的绑定都要在。
        String attrs = read(ATTRS);
        assertTrue("必须声明 tvFocusRing 主题属性", attrs.contains("<attr name=\"tvFocusRing\" format=\"color\" />"));
        assertTrue("必须声明 tvCurrentRing 主题属性", attrs.contains("<attr name=\"tvCurrentRing\" format=\"color\" />"));
        assertTrue("必须声明 tvNormalStroke 主题属性", attrs.contains("<attr name=\"tvNormalStroke\" format=\"color\" />"));
        for (String styles : new String[]{LEANBACK_STYLES, MOBILE_STYLES}) {
            String body = read(styles);
            assertTrue(styles + " 必须把 tvFocusRing 绑到统一取值", body.contains("<item name=\"tvFocusRing\">@color/tv_item_focus_ring</item>"));
            assertTrue(styles + " 必须把 tvCurrentRing 绑到统一取值", body.contains("<item name=\"tvCurrentRing\">@color/tv_item_current_ring</item>"));
            assertTrue(styles + " 必须把 tvNormalStroke 绑到统一取值", body.contains("<item name=\"tvNormalStroke\">@color/tv_item_normal_stroke</item>"));
        }
    }

    @Test
    public void everySelectableSurfaceSharesOneFocusSpec() throws Exception {
        // 方形芯片与选集卡统一 8dp 圆角。
        for (String selector : new String[]{CHIP_SELECTOR, EPISODE_CARD_SELECTOR}) {
            String body = values(read(selector));
            assertTrue(selector + " 的焦点态必须是 3dp 焦点语义属性",
                    body.contains("android:width=\"3dp\" android:color=\"?attr/tvFocusRing\""));
            assertTrue(selector + " 的圆角必须统一为 8dp", body.contains("<corners android:radius=\"8dp\" />"));
            assertFalse(selector + " 不允许再出现白色焦点环", body.contains("android:color=\"@color/white\"")
                    || body.contains("android:color=\"#FFFFFF\""));
            assertFalse(selector + " 的值不允许再出现硬编码焦点色，必须走主题属性",
                    body.contains("#FFD166") || body.contains("#FFE16A") || body.contains("#0077FF"));
        }

        // 演员卡是 14dp 圆角卡片，焦点环必须同半径，否则描边会内缩或外溢。
        String cast = values(read(CAST_FOCUS_SELECTOR));
        assertTrue("演员卡焦点态必须是 3dp 焦点语义属性",
                cast.contains("android:width=\"3dp\" android:color=\"?attr/tvFocusRing\""));
        assertTrue("演员卡当前态必须是 2dp 当前语义属性",
                cast.contains("android:width=\"2dp\" android:color=\"?attr/tvCurrentRing\""));
        assertTrue("演员卡焦点环半径必须与其卡片圆角一致（14dp）",
                cast.contains("<corners android:radius=\"14dp\" />")
                        && read("app/src/main/res/layout/adapter_tmdb_cast.xml").contains("app:cardCornerRadius=\"14dp\""));
        assertFalse("演员卡不允许再出现白色焦点环", cast.contains("android:color=\"@color/white\""));
    }

    @Test
    public void chipSelectorKeepsCurrentStateAtTwoDpAndNormalStateAtOneDp() throws Exception {
        String body = squeeze(read(CHIP_SELECTOR));
        assertTrue("当前播放环必须是 2dp 当前态语义属性",
                body.contains("android:width=\"2dp\" android:color=\"?attr/tvCurrentRing\""));
        assertTrue("常态必须补上 1dp 常态描边，消除「没有边框颜色」的芯片",
                body.contains("android:width=\"1dp\" android:color=\"?attr/tvNormalStroke\""));
        assertTrue("state_selected 仍然只承担跑马灯开关，不能加描边",
                body.contains("跑马灯开关"));
    }

    @Test
    public void playbackCardsOwnTheUnifiedRingInLeanbackPresentersWithoutTouchingSharedLayouts() throws Exception {
        // adapter_tmdb_*.xml 与独立详情页共用，详情页有自己的焦点契约
        // （TmdbDetailActivityLayoutTest 会断言这些共享布局的原样取值），
        // 所以播放页的统一焦点环必须落在 leanback 专属的 presenter 上，不能改共享布局。
        String[] shared = {
                "app/src/main/res/layout/adapter_tmdb_photo.xml",
                "app/src/main/res/layout/adapter_tmdb_video.xml",
                "app/src/main/res/layout/adapter_tmdb_cast.xml",
                "app/src/main/res/layout/adapter_tmdb_recommendation.xml",
                "app/src/main/res/layout/adapter_tmdb_recommendation_landscape.xml",
        };
        for (String card : shared) {
            assertFalse(card + " 是详情页共用布局，不允许把播放页的统一描边写进去",
                    read(card).contains("tv_item_normal_stroke"));
        }

        String cast = read("app/src/leanback/java/com/fongmi/android/tv/ui/presenter/TmdbCastPresenter.java");
        assertTrue("演员卡必须由 presenter 在绑定时挂上统一焦点环 selector",
                cast.contains("card.setForeground(card.getContext().getDrawable(R.drawable.selector_tmdb_cast_focus))"));
        assertTrue("演员卡常态描边必须是 1dp 统一取值",
                cast.contains("STROKE_NORMAL = 0x33FFFFFF") && cast.contains("STROKE_WIDTH_NORMAL_DP = 1"));
        assertFalse("演员卡不允许再自己画白色焦点描边（会与 foreground 双重描边）",
                cast.contains("STROKE_FOCUSED"));

        String recommendation = read("app/src/leanback/java/com/fongmi/android/tv/ui/presenter/TmdbRecommendationPresenter.java");
        assertTrue("推荐卡此前没有任何焦点描边，必须补上统一焦点环",
                recommendation.contains("FOCUS_WIDTH_DP = 3")
                        && recommendation.contains("applyUnifiedFocus")
                        && recommendation.contains("R.color.tv_item_focus_ring")
                        && recommendation.contains("R.color.tv_item_normal_stroke"));
    }

    @Test
    public void episodeHeaderToolsUseTheSameChipSelectorAsRouteAndSegmentRows() throws Exception {
        String body = read(VIDEO_LAYOUT);
        int header = body.indexOf("android:id=\"@+id/episodeHeader\"");
        int headerEnd = body.indexOf("<!-- 选集列表容器", header);
        assertTrue("集数表头必须存在", header >= 0 && headerEnd > header);
        String tools = body.substring(header, headerEnd);
        // 表头此前用 selector_button（28dp 圆角、白色 1.5dp 焦点环），是同一页最显眼的另一套风格。
        assertFalse("集数表头不允许再用 selector_button 这套独立风格", tools.contains("@drawable/selector_button"));
        assertTrue("集数表头必须复用统一芯片 selector", countOf(tools, "@drawable/selector_video_item") == 4);
    }

    @Test
    public void presentersUseTheUnifiedFocusRingAndWidth() throws Exception {
        String cast = read("app/src/leanback/java/com/fongmi/android/tv/ui/presenter/TmdbCastPresenter.java");
        assertTrue("演员卡常态描边必须是 1dp tv_item_normal_stroke 取值",
                cast.contains("STROKE_NORMAL = 0x33FFFFFF") && cast.contains("STROKE_WIDTH_NORMAL_DP = 1"));
        assertFalse("演员卡不允许再用白色焦点描边", cast.contains("STROKE_FOCUSED = 0xFFFFFFFF"));

        String video = read("app/src/leanback/java/com/fongmi/android/tv/ui/presenter/TmdbVideoPresenter.java");
        // 封面 ImageView 是全出血的，卡片描边画在背景层会被封面盖住：焦点环必须走前景 selector。
        assertTrue("相关视频必须用前景 selector 画焦点环（卡片描边会被全出血封面盖住）",
                video.contains("R.drawable.selector_tmdb_media_focus") && video.contains("card.setForeground("));
        // RecyclerView 复用后卡片可能已持有焦点，只挂监听拿不到回调，必须按当前状态补一次。
        assertTrue("相关视频必须在绑定时同步当前焦点态，否则停下的卡片看不到焦点环",
                video.contains("applyFocusChrome(holder, card.hasFocus())"));
    }

    @Test
    public void photoCardsHaveAFocusAppearanceAtAll() throws Exception {
        // 剧照/海报卡片此前只有卡面图片，布局关闭了系统焦点高亮且 presenter 不改描边，
        // 遥控停下时完全没有视觉反馈，用户报告“看不出焦点到哪里”。
        String photo = read("app/src/leanback/java/com/fongmi/android/tv/ui/presenter/TmdbPhotoPresenter.java");
        assertTrue("剧照/海报卡片必须安装焦点前景 selector",
                photo.contains("bindFocusStyle(") && photo.contains("R.drawable.selector_tmdb_media_focus"));
        assertTrue("剧照/海报卡片必须在创建时按当前焦点态落地外观",
                photo.contains("applyFocusStyle(card, card.hasFocus())"));
        String selector = read("app/src/main/res/drawable/selector_tmdb_media_focus.xml");
        String body = values(selector);
        assertTrue("剧照/海报焦点环必须使用统一主题属性与 3dp",
                body.contains("android:width=\"3dp\"") && body.contains("android:color=\"?attr/tvFocusRing\""));
    }

    @Test
    public void everyTmdbRowCardCarriesItsOwnVerticalFocusTargets() throws Exception {
        // 只给容器设 nextFocusDown 不够：焦点在卡片上时 Android 优先读卡片自身的声明，
        // 否则退回几何搜索，出现“第一张剧照下不去、第二张可以”这类位置相关行为。
        String source = read(VIDEO_ACTIVITY);
        assertTrue("必须存在按可见行重排纵向焦点链的方法",
                source.contains("private void applyTmdbRowFocusChain()"));
        assertTrue("绑定完成后必须立即应用纵向焦点链",
                source.contains("applyTmdbRowFocusChain();\n        updateFocus();"));
        assertTrue("行内卡片必须写入自己的上下焦点目标",
                source.contains("card.setNextFocusUpId(grid.getNextFocusUpId())")
                        && source.contains("card.setNextFocusDownId(grid.getNextFocusDownId())"));
        assertTrue("新接入的卡片必须补写焦点目标（RecyclerView 复用）",
                source.contains("installRowCardFocusLinks(")
                        && source.contains("onChildViewAttachedToWindow"));
        assertTrue("焦点链必须跳过已隐藏的行（否则下键指向不可见 View 会退回几何搜索）",
                source.contains("row.getVisibility() == View.VISIBLE && row.getAdapter() != null")
                        && source.contains("row.getAdapter().getItemCount() > 0"));
    }

    @Test
    public void everyTmdbRowHasRowHeightConfigured() throws Exception {
        // Leanback 的 HorizontalGridView 不配置 rowHeight 时行高会塌陷为 0：
        // 标签正常显示但卡片一张都看不到（海报行曾漏配，用户报告“海报卡片一张都没显示”）。
        String source = read(VIDEO_ACTIVITY);
        int start = source.indexOf("private void setupTmdbGridViews()");
        int end = source.indexOf("private List<HorizontalGridView> tmdbMediaRows()", start);
        assertTrue("TMDB 行初始化方法必须存在", start >= 0 && end > start);
        String body = source.substring(start, end);
        for (String row : new String[]{"tmdbCast", "tmdbPhotos", "tmdbPosters", "tmdbCrew", "tmdbRelatedVideos",
                "tmdbRecommendations", "tmdbPersonalTmdbRecommendations", "tmdbPersonalDoubanRecommendations",
                "tmdbPersonalAiRecommendations"}) {
            assertTrue(row + " 必须配置 rowHeight，否则行高塌陷为 0、卡片不可见",
                    body.contains("mBinding." + row + ".setRowHeight("));
        }
    }

    @Test
    public void posterLabelVisibilityFollowsTheSameSourceAsItsCards() throws Exception {
        // 标签与卡片必须同源：海报列表为空时标签与行一起隐藏，不能只隐行或只隐标签。
        String source = read(VIDEO_ACTIVITY);
        int start = source.indexOf("// 海报", source.indexOf("private void bindTmdbData()"));
        int end = source.indexOf("// TMDB related videos", start);
        assertTrue("海报绑定分支必须存在", start >= 0 && end > start);
        String body = source.substring(start, end);
        assertTrue("海报列表非空时必须同时显示行与标签",
                body.contains("mBinding.tmdbPosters.setVisibility(View.VISIBLE)")
                        && body.contains("postersLabel.setVisibility(View.VISIBLE)"));
        assertTrue("海报列表为空时必须同时隐藏行与标签",
                body.contains("mBinding.tmdbPosters.setVisibility(View.GONE)")
                        && body.contains("postersLabel.setVisibility(View.GONE)"));
    }

    // ---------------------------------------------------------------- 焦点链路

    @Test
    public void segmentRowDownReachesTheSeasonButtonBeforeTheEpisodeList() throws Exception {
        String source = read(VIDEO_ACTIVITY);
        int start = source.indexOf("private boolean onArrayKey(KeyEvent event)");
        int end = source.indexOf("private boolean focusEpisodeHeaderTool(int direction)", start);
        assertTrue("分段行的向下策略必须存在", start >= 0 && end > start);
        String body = source.substring(start, end);
        assertFalse("分段行向下不允许直接把焦点推进选集列表（会跳过季度/选集表头）",
                body.contains("selectEpisodeSegment(position, true)"));
        assertTrue("分段行向下必须先装载分段但不抢焦点", body.contains("selectEpisodeSegment(position, false)"));
        assertTrue("分段行向下必须先落到集数表头（“选集 · 第 N 季”）",
                body.indexOf("focusEpisodeHeaderTool(View.FOCUS_DOWN)")
                        < body.indexOf("scrollToEpisode(getSelectedEpisodePosition(mEpisodeAdapter.getItems()), true)"));

        int helper = source.indexOf("private boolean focusEpisodeHeaderTool(int direction)");
        int helperEnd = source.indexOf("\n    }", helper);
        String helperBody = source.substring(helper, helperEnd);
        assertTrue("表头焦点助手必须复用统一的可见/可聚焦判定",
                helperBody.contains("isEpisodeFocusTarget(mBinding.episodeTitle)"));
        assertTrue("表头焦点助手必须把焦点放到季度按钮上",
                helperBody.contains("mBinding.episodeTitle.requestFocus(direction)"));
    }

    @Test
    public void ratingCardsSitBetweenTheEpisodeListAndTheCastRowInTheFocusChain() throws Exception {
        String source = read(VIDEO_ACTIVITY);
        int start = source.indexOf("private List<Integer> getEpisodeFocusOrders()");
        int end = source.indexOf("private void updateFocus()", start);
        assertTrue("焦点顺序表必须存在", start >= 0 && end > start);
        String orders = source.substring(start, end);
        int episodeGrid = orders.indexOf("R.id.episodeGrid");
        int ratings = orders.indexOf("R.id.tmdbOmdbRatings");
        int cast = orders.indexOf("R.id.tmdbCast");
        assertTrue("评分与数据行必须进入焦点顺序表", episodeGrid >= 0 && ratings > episodeGrid);
        assertTrue("评分与数据行必须排在演员行之前", cast > ratings);

        assertTrue("updateFocus 必须把评分卡片接上纵向焦点链",
                source.contains("private void updateRatingChipFocus()")
                        && source.contains("updateRatingChipFocus();")
                        && source.contains("child.setNextFocusUpId(up == 0 ? View.NO_ID : up);")
                        && source.contains("child.setNextFocusDownId(down == 0 ? View.NO_ID : down);"));

        // 集数卡片由 adapter 动态构建，不会把 nextFocusDown 写回卡片项；
        // 末行向下必须由集数列表按键处理显式接管，否则焦点链在选集底部断掉。
        assertTrue("选集网格末行向下必须显式落到“评分与数据”",
                source.contains("private boolean focusRatingRow()")
                        && source.contains("return focusRatingRow();"));

        int gridDown = source.indexOf("int target = TmdbEpisodeGridPolicy.verticalFocusTarget(position, spanCount, mEpisodeGridAdapter.getItemCount(), true);");
        int fallback = source.indexOf("return focusRatingRow();", gridDown);
        assertTrue("末行向下回退必须在网格内没有下一行时才生效",
                gridDown >= 0 && fallback > gridDown
                        && source.indexOf("if (target != TmdbEpisodeGridPolicy.NO_FOCUS_TARGET) return focusEpisodeGridPosition(target);", gridDown) > gridDown);
    }

    @Test
    public void ratingCardsAreFocusableAndCarryTheUnifiedRing() throws Exception {
        String source = read(VIDEO_ACTIVITY);
        int start = source.indexOf("private View createOmdbRatingChip(String platform, String value, String color)");
        int end = source.indexOf("private void setupBackdropSlideshow", start);
        assertTrue("评分卡片构造方法必须存在", start >= 0 && end > start);
        String body = source.substring(start, end);
        assertTrue("评分卡片必须可聚焦，否则遥控会整行跳过", body.contains("chip.setFocusable(true)"));
        assertTrue("评分卡片必须安装焦点监听", body.contains("chip.setOnFocusChangeListener"));
        assertTrue("评分卡片焦点态必须使用统一焦点环与统一宽度",
                source.contains("private void styleRatingChipBackground(View view, boolean focused)")
                        && source.contains("ResUtil.dp2px(TV_ITEM_FOCUS_WIDTH_DP), getColor(R.color.tv_item_focus_ring)")
                        && source.contains("TV_ITEM_FOCUS_WIDTH_DP = 3"));
        assertTrue("评分卡片常态必须保留可读的 1dp 半透明描边",
                source.contains("background.setStroke(ResUtil.dp2px(1), 0x33FFFFFF)"));
        assertTrue("评分卡片必须在异步到达后重算焦点链",
                source.contains("private void renderTmdbRatingChips(View label, ViewGroup container, java.util.List<String[]> chips)")
                        && source.contains("// 卡片是异步到达的（OMDB 回包），到达后必须重算焦点链，"));
    }

    @Test
    public void ratingRowVisibilityChangesRecomputeTheRowChain() throws Exception {
        // 评分行是 TMDB 区块第一行的 up 邻居，且是异步到达的。
        // 若它的显隐变化不重算行链，第一行向上会指向一个已隐藏（或尚未出现）的 View，
        // 后退回几何搜索，出现“向上/向下跑到意料之外的行”。
        String source = read(VIDEO_ACTIVITY);
        int hide = source.indexOf("private void hideTmdbRatingChips");
        int render = source.indexOf("private void renderTmdbRatingChips");
        int build = source.indexOf("private java.util.List<String[]> buildTmdbRatingChips");
        assertTrue("两个评分渲染方法必须存在", hide >= 0 && render > hide && build > render);
        assertTrue("隐藏评分行后必须重算行链", source.substring(hide, render).contains("applyTmdbRowFocusChain()"));
        String renderBody = source.substring(render, build);
        assertTrue("评分卡片为空时必须重算行链", renderBody.contains("applyTmdbRowFocusChain()"));
        assertTrue("评分卡片到达后必须重算行链",
                renderBody.indexOf("applyTmdbRowFocusChain()", renderBody.indexOf("container.setVisibility(View.VISIBLE)"))
                        > renderBody.indexOf("container.setVisibility(View.VISIBLE)"));
    }

    private static int countOf(String source, String needle) {
        int count = 0;
        for (int i = source.indexOf(needle); i >= 0; i = source.indexOf(needle, i + needle.length())) count++;
        return count;
    }

    /** XML 属性换行不属于语义差异，断言前折叠空白以免脆弱匹配。 */
    private static String squeeze(String source) {
        return source.replaceAll("\\s+", " ");
    }

    /**
     * 注释里会写明「获得焦点 3dp #FFD166（= @color/tv_item_focus_ring）」这类取值映射，
     * 那是文档而不是实现，语义断言必须只看真实属性值。
     */
    private static String values(String source) {
        return squeeze(source.replaceAll("(?s)<!--.*?-->", " "));
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
