package com.fongmi.android.tv.bean;

import com.github.catvod.utils.Trans;
import com.google.gson.Gson;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * 直播分组的“持久化身份”与“界面显示名”必须分离。
 * <p>
 * 场景来源：用户把界面语言切到繁体中文后，“上次播放频道”静默回落到第一组第一个频道。
 * 根因是 {@link Group#trans()} 旧实现直接改写 {@link Group#name}，而分组名同时参与
 * keep 复合键（{@code 组名@@@频道名@@@线路}）、浏览树 id（{@code LG:组名}）与
 * {@link Group#equals}——切一次语言，这些键就全部漂移一次。
 * <p>
 * 因此这里锁死：简繁转换只允许写 displayName，绝不允许改 name；旧版本已经写进 keep 键与
 * 浏览树 id 的译文组名，由 {@link Group#matchesName(String)} 的归一回退兼容。
 */
public class GroupDisplayNameTest {

    @After
    public void tearDown() {
        Trans.setTraditional(null);
    }

    @Test
    public void traditionalConversionKeepsIdentityName() {
        Group group = new Group("线路");
        Trans.setTraditional(true);
        group.trans();

        // 身份名保持简体原文，否则 keep 键/浏览树 id/equals 会随语言漂移。
        assertEquals("线路", group.getName());
        // 展示名走繁体，界面照旧显示繁体分组名。
        assertEquals("線路", group.getDisplayName());
    }

    @Test
    public void displayNameFallsBackToIdentityNameWhenNotTranslated() {
        Group group = new Group("线路");
        assertEquals("线路", group.getDisplayName());

        // 跟随系统/简体时 trans() 直接短路，displayName 保持为空。
        Trans.setTraditional(false);
        group.trans();
        assertEquals("线路", group.getDisplayName());
    }

    @Test
    public void keepKeyIsIdenticalInBothLanguages() {
        assertEquals(keepKey(false), keepKey(true));
        // 同一套身份名拼出的键在两种语言下必须逐字相同。
        assertEquals("线路@@@频道@@@http://example.com/1.m3u8", keepKey(true));
    }

    @Test
    public void legacyTranslatedGroupNameStillMatches() {
        Group group = new Group("线路");
        Trans.setTraditional(true);
        group.trans();

        // 新格式：持久化身份名直接命中。
        assertTrue(group.matchesName("线路"));
        // 旧格式：keep 键/浏览树 id 里存的是旧版本写进去的译文组名，靠归一回退命中。
        assertTrue(group.matchesName("線路"));
        assertFalse(group.matchesName("别的组"));
        assertFalse(group.matchesName(""));
        assertFalse(group.matchesName(null));
    }

    @Test
    public void normalizingComparisonDoesNotWidenIdentity() {
        // 不同的分组即使做简繁归一也不能互相匹配，否则频道会被塞进错误的组。
        assertFalse(new Group("线路").matchesName("頻道"));
        assertFalse(new Group("频道").matchesName("線路"));
    }

    @Test
    public void equalityIgnoresDisplayName() {
        Group simplified = new Group("线路");
        Group traditional = new Group("线路");

        Trans.setTraditional(false);
        simplified.trans();
        Trans.setTraditional(true);
        traditional.trans();

        assertEquals(simplified, traditional);
        assertEquals("线路", traditional.getName());
        assertNotEquals(simplified.getDisplayName(), traditional.getDisplayName());
    }

    @Test
    public void displayNameIsNotPartOfSerialization() throws Exception {
        // transient 同时意味着：gson 不会把译文组名写进导出的直播源 JSON，
        // 本次修复因此不需要任何数据迁移或格式升级。
        Field field = Group.class.getDeclaredField("displayName");
        assertTrue("displayName 必须是 transient", Modifier.isTransient(field.getModifiers()));
        assertFalse("displayName 不能是静态字段", Modifier.isStatic(field.getModifiers()));

        Group group = new Group("线路");
        Trans.setTraditional(true);
        group.trans();
        String json = new Gson().toJson(group);
        assertTrue(json.contains("线路"));
        assertFalse("译文不应进入序列化结果", json.contains("線路"));
        assertEquals("线路", new Gson().fromJson(json, Group.class).getName());
    }

    @Test
    public void browseTreeUsesDisplayNameForTitleButIdentityNameForId() throws Exception {
        // 浏览树的 id 必须用身份名（要能长期持久化、并和 Live.keep 写的那一套对齐），
        // 标题必须用展示名，否则 Group.trans() 改为只写 displayName 后，繁体界面下的
        // 直播分组名会回退成未翻译的原文（可见回退）。
        java.nio.file.Path moduleRelative = java.nio.file.Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "browse", "LiveBrowse.java");
        java.nio.file.Path path = java.nio.file.Files.exists(moduleRelative) ? moduleRelative : java.nio.file.Path.of("app", "src", "main", "java", "com", "fongmi", "android", "tv", "browse", "LiveBrowse.java");
        String source = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue("浏览树 id 必须继续用身份名 group.getName()",
                source.contains("BrowseTree.folder(LIVE_GROUP + group.getName(), group.getDisplayName())"));
    }

    /**
     * 用与真实调用链一致的顺序复现“记住上次频道”：解析期 {@code Group.trans()} 先跑，
     * 播放期再 {@code Live.keep()} 拼键。
     */
    private static String keepKey(boolean traditional) {
        Trans.setTraditional(traditional);
        Group group = new Group("线路");
        group.trans();
        Channel channel = Channel.create("频道");
        channel.getUrls().add("http://example.com/1.m3u8");
        channel.group(group);
        return new Live("央视", "http://example.com/live.m3u").keep(channel).getKeep();
    }
}
