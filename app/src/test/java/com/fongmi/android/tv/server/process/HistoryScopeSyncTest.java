package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fongmi.android.tv.api.config.VodSiteCatalog;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 桥接推送"按源落库"的协议契约。
 *
 * <p>真正的落库路径（History.syncTo / VodSiteCatalog.findConfig 命中分支）要 Room 与运行中的
 * 上下文，纯 JVM 单测无法构造；这里锁定的是不依赖 Android 的部分：新协议开关的判定、
 * 未知配置标识的报错、以及按 cid 落库的入口签名存在。
 */
public class HistoryScopeSyncTest {

    private static Map<String, String> params(String configKey) {
        Map<String, String> params = new HashMap<>();
        params.put("type", "history");
        params.put("mode", "1");
        if (configKey != null) params.put("configKey", configKey);
        return params;
    }

    @Test
    public void scopedPushRequiresNonBlankConfigKey() {
        assertTrue(Action.isScopedHistoryPush(params("bridge-1")));
        assertFalse(Action.isScopedHistoryPush(params(null)));
        assertFalse(Action.isScopedHistoryPush(params("")));
        assertFalse(Action.isScopedHistoryPush(params("   ")));
        assertFalse(Action.isScopedHistoryPush(null));
    }

    @Test
    public void scopedPushDoesNotDependOnLegacyConfigField() {
        Map<String, String> params = params("bridge-1");
        assertFalse(params.containsKey("config"));
        assertTrue(Action.isScopedHistoryPush(params));
    }

    @Test
    public void blankConfigKeyIsRejectedBeforeTouchingDatabase() {
        for (String key : new String[]{null, "", "  "}) {
            try {
                VodSiteCatalog.findConfig(key);
                fail("expected IllegalArgumentException for key=" + key);
            } catch (IllegalArgumentException e) {
                assertEquals("缺少配置标识", e.getMessage());
            }
        }
    }

    /**
     * M4：落库必须写到「那个配置」的 cid。
     *
     * <p>桥接推送的整个价值就在这一行：请求写 B，落库就必须是 B 的 cid。抽出的接缝让本用例
     * 能在不碰 Room 的前提下看到 cid；一旦实现改回 {@code VodConfig.getCid()}，这里立刻变红。
     */
    @Test
    public void scopedPushWritesToTargetCid() {
        Recorder recorder = new Recorder();
        Config target = config(77, "https://target.example/config");

        Action.pushScopedHistory(target, List.of(new History()), false, recorder);

        assertEquals(List.of(77), recorder.written);
        assertEquals("不 force 时不得清空目标源历史", List.of(), recorder.cleared);
        assertEquals(1, recorder.refreshed);
    }

    /** 同上，force 先清目标 cid 再写；换一个配置就必须换一个 cid，证明 cid 不是常量。 */
    @Test
    public void scopedPushClearsTargetCidAndFollowsConfig() {
        Recorder first = new Recorder();
        Action.pushScopedHistory(config(77, "https://target.example/config"), List.of(new History()), true, first);
        assertEquals("force 必须先清空同一个 cid", List.of(77), first.cleared);
        assertEquals(List.of(77), first.written);

        Recorder second = new Recorder();
        Action.pushScopedHistory(config(91, "https://other.example/config"), List.of(new History()), false, second);
        assertEquals("cid 只能来自解析出的配置", List.of(91), second.written);
    }

    private static Config config(int id, String url) {
        Config config = new Config();
        config.setType(0);
        config.setUrl(url);
        config.setId(id);
        return config;
    }

    private static final class Recorder implements Action.ScopedHistorySink {

        private final List<Integer> cleared = new ArrayList<>();
        private final List<Integer> written = new ArrayList<>();
        private int refreshed;

        @Override public void clear(int cid) { cleared.add(cid); }

        @Override public void write(int cid, List<History> targets) { written.add(cid); }

        @Override public void refresh() { refreshed++; }
    }

    @Test
    public void cidScopedHistoryEntryPointsExist() throws Exception {
        Method syncTo = History.class.getMethod("syncTo", int.class, List.class);
        assertNotNull(syncTo);
        assertTrue(java.lang.reflect.Modifier.isStatic(syncTo.getModifiers()));

        Method findByName = History.class.getMethod("findByName", int.class, String.class);
        assertNotNull(findByName);
        assertTrue(java.lang.reflect.Modifier.isStatic(findByName.getModifiers()));
        assertEquals(List.class, findByName.getReturnType());
    }

    @Test
    public void legacySyncStillTargetsActiveSource() throws Exception {
        Method legacy = History.class.getMethod("sync", List.class);
        assertTrue(java.lang.reflect.Modifier.isStatic(legacy.getModifiers()));
    }
}
