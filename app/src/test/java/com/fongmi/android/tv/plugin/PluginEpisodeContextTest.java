package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Before;
import org.junit.Test;

/**
 * 剧集上下文单槽交接的边界测试。
 *
 * <p>核心断言只有一条：<b>名字不匹配一律读不到</b>。这条守的是用户眼里的
 * "播放页显示了详情页那部剧的评论"——在日志里完全没有痕迹的错。
 */
public class PluginEpisodeContextTest {

    private static final String SENTINEL = "reset.sentinel";

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /**
     * 该类刻意没有测试钩子（生产类不为测试开口子），所以用"发布哨兵名再按名清除"
     * 把唯一的那个快照槽恢复成空；单槽因此每个用例都从已知状态起步。
     */
    @Before
    public void resetSnapshot() {
        PluginEpisodeContext.publish(SENTINEL, json("{}"));
        PluginEpisodeContext.clear(SENTINEL);
        assertNull("前置条件：快照必须是空的", PluginEpisodeContext.current(SENTINEL));
    }

    @Test
    public void sameActivityNameReadsBackEveryField() {
        PluginEpisodeContext.publish("VideoActivity", json("{\"title\":\"让子弹飞\",\"year\":\"2010\",\"episodeNumber\":3}"));

        JsonObject current = PluginEpisodeContext.current("VideoActivity");

        assertNotNull(current);
        assertEquals("让子弹飞", current.get("title").getAsString());
        assertEquals("2010", current.get("year").getAsString());
        assertEquals(3, current.get("episodeNumber").getAsInt());
    }

    @Test
    public void differentActivityNameReadsNothing() {
        PluginEpisodeContext.publish("TmdbDetailActivity", json("{\"title\":\"详情页的剧\"}"));

        assertNull(PluginEpisodeContext.current("VideoActivity"));
    }

    @Test
    public void currentReturnsADeepCopySoCallersCannotCorruptTheSnapshot() {
        PluginEpisodeContext.publish("VideoActivity", json("{\"title\":\"原始标题\"}"));

        JsonObject first = PluginEpisodeContext.current("VideoActivity");
        first.addProperty("title", "被调用方改坏");

        assertEquals("原始标题", PluginEpisodeContext.current("VideoActivity").get("title").getAsString());
    }

    @Test
    public void callerMutationAfterPublishDoesNotLeakIntoTheSnapshot() {
        JsonObject context = json("{\"title\":\"发布时的值\"}");
        PluginEpisodeContext.publish("VideoActivity", context);

        context.addProperty("title", "发布之后才改的");

        assertEquals("发布时的值", PluginEpisodeContext.current("VideoActivity").get("title").getAsString());
    }

    @Test
    public void publishWithNullNameCreatesNoSnapshot() {
        PluginEpisodeContext.publish(null, json("{\"title\":\"x\"}"));

        assertNull(PluginEpisodeContext.current("VideoActivity"));
        assertNull(PluginEpisodeContext.current(SENTINEL));
    }

    @Test
    public void publishWithEmptyNameCreatesNoSnapshot() {
        PluginEpisodeContext.publish("", json("{\"title\":\"x\"}"));

        assertNull(PluginEpisodeContext.current("VideoActivity"));
    }

    @Test
    public void publishWithNullContextClearsThatName() {
        PluginEpisodeContext.publish("VideoActivity", json("{\"title\":\"x\"}"));

        PluginEpisodeContext.publish("VideoActivity", null);

        assertNull(PluginEpisodeContext.current("VideoActivity"));
    }

    @Test
    public void clearOfAnotherNameLeavesTheSnapshotAlone() {
        PluginEpisodeContext.publish("VideoActivity", json("{\"title\":\"播放页\"}"));

        PluginEpisodeContext.clear("TmdbDetailActivity");

        assertNotNull(PluginEpisodeContext.current("VideoActivity"));
    }

    @Test
    public void clearOfTheMatchingNameDropsTheSnapshot() {
        PluginEpisodeContext.publish("VideoActivity", json("{\"title\":\"播放页\"}"));

        PluginEpisodeContext.clear("VideoActivity");

        assertNull(PluginEpisodeContext.current("VideoActivity"));
    }

    @Test
    public void clearWithNullNameIsANoOp() {
        PluginEpisodeContext.publish("VideoActivity", json("{\"title\":\"播放页\"}"));

        PluginEpisodeContext.clear(null);

        assertNotNull(PluginEpisodeContext.current("VideoActivity"));
    }

    @Test
    public void clearWithEmptyNameIsANoOp() {
        PluginEpisodeContext.publish("VideoActivity", json("{\"title\":\"播放页\"}"));

        PluginEpisodeContext.clear("");

        assertNotNull(PluginEpisodeContext.current("VideoActivity"));
    }

    /**
     * 记录一处 javadoc 与实现不一致：publish 的注释说名字为空时"等同清除"，
     * 但空名字走的是 clear(null/"")，而 clear 对空名字是 no-op，所以旧快照会留下。
     * 断言按实现的真实行为写，好让将来有人改这里时看见这个问题。
     */
    @Test
    public void publishWithNullNameDoesNotClearAnExistingSnapshot() {
        PluginEpisodeContext.publish("VideoActivity", json("{\"title\":\"留存下来的旧值\"}"));

        PluginEpisodeContext.publish(null, json("{\"title\":\"新值\"}"));

        assertEquals("留存下来的旧值", PluginEpisodeContext.current("VideoActivity").get("title").getAsString());
    }

    @Test
    public void secondPublishReplacesTheFirstOneEntirely() {
        PluginEpisodeContext.publish("VideoActivity", json("{\"title\":\"第一次\"}"));
        PluginEpisodeContext.publish("VideoActivity", json("{\"title\":\"第二次\"}"));

        assertEquals("第二次", PluginEpisodeContext.current("VideoActivity").get("title").getAsString());
    }

    @Test
    public void publishingANewNameEvictsTheOldOne() {
        PluginEpisodeContext.publish("VideoActivity", json("{\"title\":\"播放页\"}"));

        PluginEpisodeContext.publish("TmdbDetailActivity", json("{\"title\":\"详情页\"}"));

        assertNull("换页之后旧页必须读不到，否则评论会串台", PluginEpisodeContext.current("VideoActivity"));
        assertEquals("详情页", PluginEpisodeContext.current("TmdbDetailActivity").get("title").getAsString());
    }

    @Test
    public void currentWithNullOrEmptyNameReadsNothing() {
        PluginEpisodeContext.publish("VideoActivity", json("{\"title\":\"播放页\"}"));

        assertNull(PluginEpisodeContext.current(null));
        assertNull(PluginEpisodeContext.current(""));
        assertTrue("名字为空的读法不该影响已有快照", PluginEpisodeContext.current("VideoActivity").size() > 0);
    }
}