package com.fongmi.android.tv.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 插件参数存储测试。
 *
 * <p>这里断言的重点是**坏输入不落盘、也不炸调用方**：用户在管理页手打 JSON，写错是常态。
 * 与字幕源那套 {@code Setting.putSubtitleSourceEnvironment} 的差别就在这里——那边直接
 * {@code getAsJsonObject()}，非法 JSON 会一路抛到 UI 线程。
 */
public class PluginEnvironmentStoreTest {

    private MemoryStore store;
    private PluginEnvironmentStore environments;

    @Before
    public void setUp() {
        store = new MemoryStore();
        environments = PluginEnvironmentStore.useStoreForTest(store);
    }

    @After
    public void tearDown() {
        PluginEnvironmentStore.useStoreForTest(null);
    }

    @Test
    public void missingPluginYieldsEmptyObject() {
        assertEquals(0, environments.getJson("nope").size());
        assertEquals("", environments.get("nope"));
    }

    @Test
    public void roundTripsPerPlugin() {
        assertTrue(environments.put("a", "{\"TOKEN\":\"x\"}"));
        assertTrue(environments.put("b", "{\"TOKEN\":\"y\"}"));
        assertEquals("x", environments.getJson("a").get("TOKEN").getAsString());
        assertEquals("y", environments.getJson("b").get("TOKEN").getAsString());
    }

    @Test
    public void rejectsInvalidJsonAndKeepsPreviousValue() {
        environments.put("a", "{\"TOKEN\":\"x\"}");
        assertFalse(environments.put("a", "{not json"));
        assertEquals("x", environments.getJson("a").get("TOKEN").getAsString());
    }

    @Test
    public void rejectsNonObjectJson() {
        assertFalse(environments.put("a", "[1,2,3]"));
        assertFalse(environments.put("a", "\"text\""));
        assertEquals(0, environments.getJson("a").size());
    }

    @Test
    public void rejectsNullAndEmptyPluginId() {
        assertFalse(environments.put(null, "{}"));
        assertFalse(environments.put("", "{}"));
    }

    @Test
    public void emptyStringClearsTheEntry() {
        environments.put("a", "{\"TOKEN\":\"x\"}");
        assertTrue(environments.put("a", ""));
        assertEquals(0, environments.getJson("a").size());
        assertFalse(store.json.contains("\"a\""));
    }

    @Test
    public void oversizedEnvironmentIsRejected() {
        String huge = "{\"k\":\"" + "x".repeat(PluginEnvironmentStore.MAX_ENVIRONMENT_BYTES) + "\"}";
        assertFalse(environments.put("a", huge));
        assertEquals(0, environments.getJson("a").size());
    }

    @Test
    public void removeClearsOnlyThatPlugin() {
        environments.put("a", "{\"T\":\"1\"}");
        environments.put("b", "{\"T\":\"2\"}");
        environments.remove("a");
        assertEquals(0, environments.getJson("a").size());
        assertEquals("2", environments.getJson("b").get("T").getAsString());
    }

    @Test
    public void returnedObjectIsACopy() {
        environments.put("a", "{\"T\":\"1\"}");
        JsonObject first = environments.getJson("a");
        first.addProperty("T", "mutated");
        assertEquals("1", environments.getJson("a").get("T").getAsString());
    }

    @Test
    public void corruptBackingJsonDegradesToEmptyInsteadOfThrowing() {
        store.json = "{broken";
        assertEquals(0, environments.getJson("a").size());
        assertTrue(environments.put("a", "{\"T\":\"1\"}"));
        assertEquals("1", environments.getJson("a").get("T").getAsString());
    }

    private static final class MemoryStore implements PluginEnvironmentStore.Store {
        private String json = "";

        @Override
        public String read() {
            return json;
        }

        @Override
        public void write(String json) {
            this.json = json == null ? "" : json;
        }
    }
}
