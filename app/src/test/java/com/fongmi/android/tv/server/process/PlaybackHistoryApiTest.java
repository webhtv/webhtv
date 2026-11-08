package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * 按站点读历史端点的参数归一化契约。
 *
 * <p>只测纯逻辑部分（参数解析与夹取）。HTTP 层（状态码、CORS、响应体形态、
 * 观影记录同步开关的 403 门控）**目前没有自动化覆盖**：实例化端点需要
 * {@code IHTTPSession} 与运行中的 {@code NanoHTTPD}，纯 JVM 单测无法构造。
 * 那部分靠真机手工验证（开关关闭 → 403；开启 → 200 且 items 只含 apiSafe 字段）。
 * 本类不声称覆盖它。
 */
public class PlaybackHistoryApiTest {

    @Test
    public void siteKeyPrefersSiteKeyOverSiteAlias() {
        Map<String, String> params = new HashMap<>();
        params.put("siteKey", "csp_main");
        params.put("site", "csp_alias");

        assertEquals("csp_main", PlaybackHistoryApi.siteKey(params));
    }

    @Test
    public void siteKeyFallsBackToSiteAliasAndTrims() {
        Map<String, String> params = new HashMap<>();
        params.put("site", "  csp_alias  ");

        assertEquals("csp_alias", PlaybackHistoryApi.siteKey(params));
    }

    @Test
    public void siteKeyIsEmptyWhenAbsent() {
        // 空 siteKey 由调用方判 400；这里只保证不会返回 null 或抛异常。
        assertEquals("", PlaybackHistoryApi.siteKey(new HashMap<>()));
        assertEquals("", PlaybackHistoryApi.siteKey(Map.of("siteKey", "   ")));
    }

    @Test
    public void blankSiteKeyStillFallsBackToSiteAlias() {
        // 回归锁：`?siteKey=%20&site=csp_x` 里调用方明确给了别名，
        // 若只看 isEmpty 就返回空串，端点会报 400 而调用方拿不到解释。
        // 只有主参数与别名**都**空才返回空串。
        Map<String, String> params = new HashMap<>();
        params.put("siteKey", "   ");
        params.put("site", "csp_alias");

        assertEquals("csp_alias", PlaybackHistoryApi.siteKey(params));
    }

    @Test
    public void limitFallsBackToDefaultOnMissingOrMalformed() {
        // 坏参数一律回落默认（0 表示"用默认"），而不是报 400——
        // 对端传了一个坏 limit 不该让整次拉取失败。
        assertEquals(0, PlaybackHistoryApi.limit(new HashMap<>()));
        assertEquals(0, PlaybackHistoryApi.limit(Map.of("limit", "abc")));
        assertEquals(0, PlaybackHistoryApi.limit(Map.of("limit", "")));
        assertEquals(0, PlaybackHistoryApi.limit(Map.of("limit", "1.5")));
    }

    @Test
    public void limitParsesValidNumber() {
        assertEquals(7, PlaybackHistoryApi.limit(Map.of("limit", "7")));
        assertEquals(7, PlaybackHistoryApi.limit(Map.of("limit", "  7  ")));
        // 负值原样返回，由 effectiveHistoryLimit 负责回落默认。
        assertEquals(-3, PlaybackHistoryApi.limit(Map.of("limit", "-3")));
    }
}
