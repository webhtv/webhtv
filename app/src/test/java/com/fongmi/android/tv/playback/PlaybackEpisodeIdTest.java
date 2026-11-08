package com.fongmi.android.tv.playback;

import com.google.gson.JsonObject;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 集消歧字段（{@code episodeId}）的契约。
 *
 * <p>背景：只读 API（{@code apiSafe()}）不输出 {@code episodeUrl}——那是带时效
 * 签名的播放地址。但对端要区分同一剧的不同集必须有稳定的集身份，否则同名集
 * （如两条线路都有"第01集"）会塌缩成同一条记录、互相覆盖。
 * {@code episodeId} 就是 {@code episodeUrl} 的 SHA-256，只给指纹不给地址。
 */
public class PlaybackEpisodeIdTest {

    @Test
    public void emptyUrlProducesEmptyIdInsteadOfHashOfEmptyString() {
        // 空串必须映射到空串：对端据此就能区分"没有单集地址"与"有一个地址"，
        // 不必再猜。若返回 sha256("") 的常量，对端会把它当成一个真实集身份。
        assertEquals("", PlaybackRecord.episodeId(""));
        assertEquals("", PlaybackRecord.episodeId(null));
    }

    @Test
    public void nonEmptyUrlProducesStableSixtyFourHexChars() {
        String url = "https://cdn.example.com/live/abc.m3u8?sign=deadbeef&t=123";
        String id = PlaybackRecord.episodeId(url);

        assertEquals(64, id.length());
        assertTrue(id.matches("[0-9a-f]{64}"));
        // 确定性：同一输入必须得到同一指纹（对端与本地各算一次也要一致）。
        assertEquals(id, PlaybackRecord.episodeId(url));
    }

    @Test
    public void differentUrlsProduceDifferentIds() {
        // 这是本字段存在的理由：不同集必须可区分。
        String first = PlaybackRecord.episodeId("https://cdn.example.com/ep01.m3u8");
        String second = PlaybackRecord.episodeId("https://cdn.example.com/ep02.m3u8");

        assertFalse(first.equals(second));
    }

    @Test
    public void apiSafeExposesEpisodeIdButNotEpisodeUrl() {
        PlaybackFieldPolicy policy = PlaybackFieldPolicy.apiSafe();

        // 集身份必须可得，否则对端无法消歧。
        assertTrue("apiSafe 必须暴露 episodeId", policy.includes("episodeId"));
        // 播放地址必须不可得：它常带 CDN 签名/token，对端只需要"是不是同一集"。
        assertFalse("apiSafe 不得暴露 episodeUrl", policy.includes("episodeUrl"));
    }

    @Test
    public void recordSerializesEpisodeIdUnderApiSafe() {
        PlaybackRecord record = new PlaybackRecord();
        record.siteKey = "site";
        record.vodId = "vod";
        record.episodeUrl = "https://cdn.example.com/ep01.m3u8";
        record.episodeId = PlaybackRecord.episodeId(record.episodeUrl);

        JsonObject json = record.toJson(PlaybackFieldPolicy.apiSafe());

        assertTrue(json.has("episodeId"));
        assertEquals(record.episodeId, json.get("episodeId").getAsString());
        assertFalse("apiSafe 序列化不得写出 episodeUrl", json.has("episodeUrl"));
    }

    @Test
    public void episodeIdIsDerivedFromEpisodeUrlAtConstructionSites() {
        // 生产路径：PlaybackRecord.from() 会调 episodeId(episodeUrl) 自动补指纹。
        // 但 from() 依赖 Config.find()/VodConfig（Android 运行时），JVM 单测里不可用，
        // 因此这里只锁"赋值契约"：给定 url 时字段必须等于 episodeId(url)。
        // from() 的接线由源码断言锁（见下一条用例）。
        PlaybackRecord record = new PlaybackRecord();
        record.episodeUrl = "https://cdn.example.com/ep01.m3u8";
        record.episodeId = PlaybackRecord.episodeId(record.episodeUrl);

        assertEquals(PlaybackRecord.episodeId("https://cdn.example.com/ep01.m3u8"), record.episodeId);
        assertFalse(record.episodeId.isEmpty());
    }

    @Test
    public void fromWiresEpisodeIdFromEpisodeUrl() throws Exception {
        // 源码级接线锁：from() 必须真的调用 episodeId(...)。
        //
        // 为什么用源码断言而不是行为断言：from() 依赖 Config.find() 与 VodConfig，
        // 两者都需要 Android 运行时；本仓库已有同类先例（如
        // GlobalHistoryResumeSourceTest 用源码断言锁接线）。
        // 断言前剥掉注释行，避免"把语句注释掉"也能通过。
        String source = readSource("app/src/main/java/com/fongmi/android/tv/playback/PlaybackRecord.java");
        assertTrue("from() 必须从 episodeUrl 派生 episodeId",
                source.contains("record.episodeId = episodeId(record.episodeUrl);"));
        assertTrue("toJson 必须在 apiSafe 下写出 episodeId",
                source.contains("if (policy.includes(\"episodeId\") && !TextUtils.isEmpty(episodeId)) object.addProperty(\"episodeId\", episodeId);"));
    }

    private static String readSource(String relative) throws Exception {
        // 从 user.dir 逐级向上找项目根：Gradle 的 JVM 单测工作目录可能是仓库根，
        // 也可能是模块目录（app/）；写死单层回退会在换一种布局后静默失效。
        // 仓库既有先例：ExoVideoHardwareDecodeTest.readSource 用同样的向上查找。
        java.nio.file.Path root = java.nio.file.Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !java.nio.file.Files.isRegularFile(root.resolve(relative))) root = root.getParent();
        if (root == null) throw new IllegalStateException("找不到项目根，无法读取 " + relative);
        StringBuilder builder = new StringBuilder();
        for (String line : java.nio.file.Files.readAllLines(root.resolve(relative), java.nio.charset.StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue;
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    @Test
    public void inputParsesEpisodeIdAndDerivesItFromUrlWhenAbsent() {
        // 对端可能只给 url（旧形态）或只给 id（apiSafe 形态），两种都必须能收。
        PlaybackProgressInput fromId = PlaybackProgressInput.fromJson(
                "{\"episodeId\":\"abc123\",\"siteKey\":\"s\",\"vodId\":\"v\"}");
        assertEquals("abc123", fromId.episodeId);

        PlaybackProgressInput fromUrl = PlaybackProgressInput.fromJson(
                "{\"episodeUrl\":\"https://cdn.example.com/ep01.m3u8\",\"siteKey\":\"s\",\"vodId\":\"v\"}");
        assertEquals(PlaybackRecord.episodeId("https://cdn.example.com/ep01.m3u8"), fromUrl.episodeId);
    }
}
