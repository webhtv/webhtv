package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source-contract regression checks for the TV detail shell. */
public class VideoActivityDetailShellSourceTest {

    @Test
    public void emptyDetailFromSearchKeepsPlaybackPageForRetry() throws Exception {
        Path path = Path.of("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        if (!Files.isRegularFile(path)) path = Path.of("app").resolve(path);
        String source = Files.readString(path, StandardCharsets.UTF_8);
        int start = source.indexOf("private void setEmpty(boolean finish) {");
        int end = source.indexOf("\n    private void showEmpty()", start);

        assertTrue("VideoActivity must keep a dedicated empty-detail path", start >= 0 && end > start);
        String empty = source.substring(start, end);
        assertTrue("explicit detail errors must still close the failed playback page",
                empty.contains("if (finish) {"));
        assertFalse("a failed search-result detail must not close the playback page and reveal search results",
                empty.contains("isFromCollect() || finish"));
        assertTrue("the failed detail must retain the title and enter the existing retry path",
                empty.contains("mBinding.name.setText(getName());")
                        && empty.contains("checkSearch(false);"));
    }

    @Test
    public void directPlaybackKeepsSynopsisButtonVisibleByDefault() throws Exception {
        Path path = Path.of("src", "leanback", "res", "layout", "activity_video.xml");
        if (!Files.isRegularFile(path)) path = Path.of("app").resolve(path);
        String xml = Files.readString(path, StandardCharsets.UTF_8);
        int id = xml.indexOf("@+id/content\"");
        int end = xml.indexOf(">", id);

        assertTrue("the leanback playback page must keep its synopsis (简介) button", id >= 0 && end > id);
        String block = xml.substring(id, end);
        assertFalse("影视原生 (DIRECT) never runs the TMDB reveal that re-shows the button, so the layout "
                        + "must not default it to gone (regression from 934e831de)",
                block.contains("android:visibility=\"gone\""));

        Path source = Path.of("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        if (!Files.isRegularFile(source)) source = Path.of("app").resolve(source);
        String activity = Files.readString(source, StandardCharsets.UTF_8);
        int tag = activity.indexOf("mBinding.content.setTag(item.getContent());");
        assertTrue("setDetail must still feed the synopsis tag so the dialog can open", tag >= 0);
    }

    /**
     * 影视原生 (DIRECT) 没有 TMDB 富集，简介区此前没有任何代码写入，用户报告
     * 「简介不显示，必须点击简介才能弹窗显示，以前简介这里会显示一部分」。
     * 这里钉住：站源详情文本必须真的铺到 @id/tmdbOverview 上，且 DIRECT 下简介按钮常驻。
     */
    @Test
    public void directPlaybackRendersInlineSynopsisPreview() throws Exception {
        Path source = Path.of("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        if (!Files.isRegularFile(source)) source = Path.of("app").resolve(source);
        String activity = Files.readString(source, StandardCharsets.UTF_8);

        int helper = activity.indexOf("private void updateOverviewPreview()");
        assertTrue("VideoActivity must keep one shared synopsis-preview writer", helper >= 0);
        int helperEnd = activity.indexOf("private void updateTmdbOverviewButton()", helper);
        String body = activity.substring(helper, helperEnd);
        assertTrue("the preview must write the synopsis node itself, not only its tag",
                body.contains("mBinding.tmdbOverview.setText(overviewText)")
                        && body.contains("mBinding.tmdbOverview.setVisibility("));

        int setText = activity.indexOf("private void setText(Vod item) {");
        int setTextEnd = activity.indexOf("private void setText(TextView view, int resId, String text)", setText);
        assertTrue("setText(Vod) must feed the preview in non-TMDB mode", setText >= 0 && setTextEnd > setText);
        assertTrue("direct playback must render the synopsis preview without waiting for TMDB enrichment",
                activity.substring(setText, setTextEnd).contains("if (!isTmdbSourceEnabled()) updateOverviewPreview();"));

        int button = activity.indexOf("private void updateTmdbOverviewButton()");
        int buttonEnd = activity.indexOf("private int getTmdbOverviewMaxLines(", button);
        assertTrue("updateTmdbOverviewButton must stay the single owner of the synopsis button visibility",
                button >= 0 && buttonEnd > button);
        assertTrue("DIRECT must keep the synopsis button reachable even when the preview is not truncated",
                activity.substring(button, buttonEnd).contains("boolean directMode = !isTmdbSourceEnabled();")
                        && activity.substring(button, buttonEnd).contains("truncated || directMode"));
    }

    /**
     * 影视原生选集走文本芯片 (adapter_episode + selector_video_item)，而该 selector 只给
     * state_activated 画「当前播放」环；EpisodeAdapter 此前只调 setSelected（跑马灯），
     * 用户报告播到第 12 集时看不出在播哪一集。
     */
    @Test
    public void directEpisodeChipsMarkThePlayingEpisode() throws Exception {
        Path source = Path.of("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "adapter", "EpisodeAdapter.java");
        if (!Files.isRegularFile(source)) source = Path.of("app").resolve(source);
        String adapter = Files.readString(source, StandardCharsets.UTF_8);

        int bind = adapter.indexOf("private void bindTextView(@NonNull ViewHolder holder, Episode item, int position)");
        int bindEnd = adapter.indexOf("private void applyTextActive(", bind);
        assertTrue("bindTextView must route its active state through the shared helper", bind >= 0 && bindEnd > bind);
        assertTrue("the playing episode chip must be bound through applyTextActive",
                adapter.substring(bind, bindEnd).contains("applyTextActive(holder, item,"));

        int helper = adapter.indexOf("private void applyTextActive(@NonNull ViewHolder holder, Episode item, boolean hasFocus)");
        int helperEnd = adapter.indexOf("private void bindCardView(", helper);
        assertTrue("EpisodeAdapter must keep the text-chip active helper", helper >= 0 && helperEnd > helper);
        String body = adapter.substring(helper, helperEnd);
        assertTrue("selected stays the marquee switch", body.contains("setSelected(active || hasFocus)"));
        assertTrue("the playing episode must be activated so selector_video_item draws the current ring",
                body.contains("setActivated(active)"));
    }

    @Test
    public void synopsisPreviewIsAnchoredBelowTheActorRow() throws Exception {
        Path path = Path.of("src", "leanback", "res", "layout", "activity_video.xml");
        if (!Files.isRegularFile(path)) path = Path.of("app").resolve(path);
        String xml = Files.readString(path, StandardCharsets.UTF_8);
        int id = xml.indexOf("@+id/tmdbOverview\"");
        int end = xml.indexOf("/>", id);

        assertTrue("the playback page must keep @+id/tmdbOverview", id >= 0 && end > id);
        String block = xml.substring(id, end);
        assertTrue("the synopsis must sit below the actor row so it never overlaps it in DIRECT mode",
                block.contains("android:layout_below=\"@+id/actor\""));
        assertTrue("the synopsis must stay above the action row",
                block.contains("android:layout_above=\"@+id/row2\""));
    }

    /**
     * 预览写入不得再把已被沉浸音频收起的详情列重新点亮。
     *
     * <p>{@code setDetail(Vod)} 里 {@code checkFlag()} 排在 {@code setText()} <b>之前</b>：
     * 沉浸音频续播时 {@code checkFlag()->onItemClick()->onRefresh()->getPlayer()} 会先调
     * {@code setAudioStageVisible(true)}，而它经 {@code syncAudioStageSurface} 调
     * {@code setVideoDetailsVisible(false)} 把整列（含简介节点）收掉；随后 {@code setText(Vod)}
     * 跑到 {@code updateOverviewPreview()}，若无条件下 {@code View.VISIBLE} 就会把刚收起的简介
     * 重新亮在音频面板旁边——正是「简介区必须跟着一起收」这条修复要消灭的现象。
     *
     * <p>因此详情列的显隐只能由 {@code setVideoDetailsVisible()} 拥有，写入点必须受
     * {@code mAudioStageVisible} 约束。用单行字面量断言，避免工作区 CRLF 让跨行字面量恒假。
     */
    @Test
    public void synopsisPreviewDoesNotUnhideTheDetailColumnWhileImmersiveAudioIsShown() throws Exception {
        Path source = Path.of("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        if (!Files.isRegularFile(source)) source = Path.of("app").resolve(source);
        String activity = Files.readString(source, StandardCharsets.UTF_8);

        int helper = activity.indexOf("private void updateOverviewPreview()");
        int helperEnd = activity.indexOf("private void updateTmdbOverviewButton()", helper);
        assertTrue("updateOverviewPreview must exist", helper >= 0 && helperEnd > helper);
        String body = activity.substring(helper, helperEnd);

        assertTrue("the preview must not re-show the detail column while immersive audio is up",
                body.contains("mBinding.tmdbOverview.setVisibility(mAudioStageVisible ? View.GONE : View.VISIBLE);"));
        assertFalse("no unconditional show of the synopsis node may remain in the preview writer",
                body.contains("mBinding.tmdbOverview.setVisibility(View.VISIBLE);"));

        // setVideoDetailsVisible() 必须仍然是详情列（含简介）显隐的唯一所有者。
        int details = activity.indexOf("private void setVideoDetailsVisible(boolean visible)");
        int detailsEnd = activity.indexOf("private boolean shouldShowVideoDetailRemark", details);
        assertTrue("setVideoDetailsVisible must own the synopsis visibility", details >= 0 && detailsEnd > details);
        assertTrue("the detail column must still collapse the synopsis together with its siblings",
                activity.substring(details, detailsEnd).contains("mBinding.tmdbOverview.setVisibility("));
    }
}
