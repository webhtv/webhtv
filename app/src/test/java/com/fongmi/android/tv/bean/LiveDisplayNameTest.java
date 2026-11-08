package com.fongmi.android.tv.bean;

import com.github.catvod.utils.Trans;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * 直播源的“持久化身份”与“界面显示名”必须分离。
 * <p>
 * 场景来源：用户把界面语言切到繁体中文后，已保存的收藏/屏蔽/上次播放频道静默失效。
 * 根因是 {@link Live#trans()} 旧实现直接改写 {@link Live#name}，而 name 同时是 Room 主键、
 * {@code Config.home}、spider 缓存键与 {@link Live#equals} 的依据——繁体态写入的是繁体名，
 * 简体态写入的是简体名，同一频道在库里分裂成两行，且 {@code LiveConfig.finishLive()} 用
 * 当前语言的 name 去匹配库里的另一语言 name 永远落空。
 * <p>
 * 因此这里锁死：简繁转换只允许写 displayName，绝不允许改 name。
 */
public class LiveDisplayNameTest {

    @After
    public void tearDown() {
        Trans.setTraditional(null);
    }

    private static Live live(String name) {
        return new Live(name, "http://example.com/live.m3u");
    }

    @Test
    public void traditionalConversionKeepsIdentityName() {
        Live live = live("频道");
        Trans.setTraditional(true);
        live.trans();

        // 身份名保持简体原文，否则 Room 主键/Config.home/equals 会随语言漂移。
        assertEquals("频道", live.getName());
        // 展示名走繁体，UI 仍然看到繁体。
        assertEquals("頻道", live.getDisplayName());
    }

    @Test
    public void displayNameFallsBackToIdentityNameWhenNotTranslated() {
        Live live = live("频道");
        assertEquals("频道", live.getDisplayName());

        // 跟随系统/简体时 trans() 直接短路，displayName 保持为空。
        Trans.setTraditional(false);
        live.trans();
        assertEquals("频道", live.getDisplayName());
    }

    @Test
    public void identityEqualitySurvivesLanguageSwitch() {
        Live simplified = live("频道");
        Live traditional = live("频道");

        Trans.setTraditional(false);
        simplified.trans();
        Trans.setTraditional(true);
        traditional.trans();

        // 同一个直播源在两种语言下必须是同一个对象身份，否则 distinct()/toMap() 会把它拆开。
        assertEquals(simplified, traditional);
        assertEquals("频道", traditional.getName());
        assertNotEquals(simplified.getDisplayName(), traditional.getDisplayName());
    }

    @Test
    public void differentSourcesStayDifferentAfterTranslation() {
        Live first = live("频道一");
        Live second = live("频道二");
        Trans.setTraditional(true);
        first.trans();
        second.trans();

        assertNotEquals(first, second);
        assertEquals("頻道一", first.getDisplayName());
        assertEquals("頻道二", second.getDisplayName());
    }

    @Test
    public void displayNameIsNotPartOfPersistenceOrSerialization() throws Exception {
        // transient 同时意味着：gson 不会把译文名写进配置导出/备份 JSON，Room 也不会为它建列，
        // 所以本次修复不需要新增数据库迁移。
        Field field = Live.class.getDeclaredField("displayName");
        assertTrue("displayName 必须是 transient", Modifier.isTransient(field.getModifiers()));
        assertFalse("displayName 不能是持久化字段", Modifier.isStatic(field.getModifiers()));
    }
}
