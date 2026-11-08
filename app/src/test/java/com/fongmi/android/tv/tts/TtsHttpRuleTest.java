package com.fongmi.android.tv.tts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TtsHttpRuleTest {

    @Test
    public void encodesSpeakTextWithEncodeUriSemantics() {
        String rendered = TtsHttpRule.render("https://a/b?text={{java.encodeURI(speakText)}}", "你好 世界", 5);
        assertEquals("https://a/b?text=%E4%BD%A0%E5%A5%BD%20%E4%B8%96%E7%95%8C", rendered);
    }

    @Test
    public void encodeUriKeepsReservedCharacters() {
        // JS encodeURI 不转义这些保留字符；换成 URLEncoder 语义会让部分接口取不到参数
        assertEquals("a-b_c.d!e~f'g(h)i/j:k?l,m;n", TtsHttpRule.encodeUri("a-b_c.d!e~f'g(h)i/j:k?l,m;n"));
    }

    @Test
    public void arithmeticSpeakSpeedIsEvaluated() {
        // legado 百度规则：spd = (speakSpeed + 5) / 10 + 4（JS 语义，浮点除法）
        assertEquals("5", TtsHttpRule.render("{{(speakSpeed + 5) / 10 + 4}}", "x", 5));
        assertEquals("4.6", TtsHttpRule.render("{{(speakSpeed + 5) / 10 + 4}}", "x", 1));
        assertEquals("6", TtsHttpRule.render("{{String((speakSpeed + 5) / 10 + 4)}}", "x", 15));
        assertEquals("500", TtsHttpRule.render("{{String((speakSpeed) * 20 - 400)}}", "x", 45));
    }

    @Test
    public void nestedEncodeUriAndExpression() {
        String rendered = TtsHttpRule.render(
                "tex={{java.encodeURI(java.encodeURI(speakText))}}&spd={{speakSpeed}}", "中 文", 4);
        // 双重编码后空格变成 %2520
        assertTrue(rendered, rendered.startsWith("tex=%25E4%25B8%25AD%2520%25E6%2596%2587&spd=4"));
    }

    @Test
    public void plainUrlWithoutOptionsParsesAsGet() {
        TtsHttpRule rule = TtsHttpRule.parse(
                "https://fanyi.baidu.com/gettts?lan=zh&text={{java.encodeURI(speakText)}}&spd={{speakSpeed}}&source=web",
                "audio/mpeg");
        assertTrue(rule.isUsable());
        assertEquals("GET", rule.method());
        assertNull(rule.body("你好", 5));
        assertTrue(rule.url("你好", 5).contains("spd=5"));
    }

    @Test
    public void baiduStylePostRuleParsesBodyAndMethod() {
        String raw = "http://tts.baidu.com/text2audio,{"
                + "\"method\": \"POST\","
                + "\"body\": \"tex={{java.encodeURI(java.encodeURI(speakText))}}&spd={{(speakSpeed + 5) / 10 + 4}}&per=3\""
                + "}";
        TtsHttpRule rule = TtsHttpRule.parse(raw, "audio/wav");
        assertTrue(rule.unsupportedReason(), rule.isUsable());
        assertEquals("POST", rule.method());
        assertEquals("http://tts.baidu.com/text2audio", rule.url("文本", 5));
        String body = rule.body("文本", 5);
        assertNotNull(body);
        assertTrue(body, body.endsWith("&spd=5&per=3"));
    }

    @Test
    public void optionsWithHeadersAreParsed() {
        String raw = "https://host/tts,{"
                + "\"method\":\"POST\",\"body\":\"t={{speakText}}\","
                + "\"headers\":{\"User-Agent\":\"webhtv\",\"X-A\":\"1\"}}";
        TtsHttpRule rule = TtsHttpRule.parse(raw, "audio/mpeg");
        assertTrue(rule.isUsable());
        assertEquals("webhtv", rule.headers().get("User-Agent"));
        assertEquals("1", rule.headers().get("X-A"));
    }

    @Test
    public void jsRuleIsRejectedWithReason() {
        TtsHttpRule rule = TtsHttpRule.parse("@js:apiurl(speakText,speakSpeed);", "audio/mpeg");
        assertFalse(rule.isUsable());
        assertTrue(rule.unsupportedReason().contains("@js"));
    }

    @Test
    public void postWithoutBodyIsRejected() {
        TtsHttpRule rule = TtsHttpRule.parse("https://host/tts,{\"method\":\"POST\"}", "audio/mpeg");
        assertFalse(rule.isUsable());
    }

    @Test
    public void emptyRuleIsRejected() {
        assertFalse(TtsHttpRule.parse("", "audio/mpeg").isUsable());
        assertFalse(TtsHttpRule.parse(null, "audio/mpeg").isUsable());
    }

    @Test
    public void unknownExpressionIsLeftVerbatimSoItIsVisibleInLogs() {
        assertEquals("{{foo}}", TtsHttpRule.render("{{foo}}", "x", 5));
    }

    @Test
    public void engineConfigRoundTripsThroughJson() {
        TtsEngineConfig config = new TtsEngineConfig();
        config.name = "我的引擎";
        config.url = "https://host/tts?t={{java.encodeURI(speakText)}}";
        config.contentType = "audio/mpeg";
        config.header = "{\"User-Agent\":\"webhtv\"}";
        TtsEngineConfig parsed = TtsEngineConfig.fromJson(config.toJson());
        assertNotNull(parsed);
        assertEquals(config.name, parsed.name);
        assertEquals(config.url, parsed.url);
        assertEquals(config.contentType, parsed.contentType);
        assertEquals("我的引擎", parsed.displayName());
    }

    @Test
    public void engineConfigWithoutUrlIsRejected() {
        assertNull(TtsEngineConfig.fromJson("{\"name\":\"x\"}"));
        assertNull(TtsEngineConfig.fromJson("not json"));
    }

    @Test
    public void builtinBaiduRuleIsUsableAndMapsRateIntoEngineRange() {
        // 内置百度音源规则必须可用，且语速映射落在接口有效区间 1..7
        TtsHttpRule rule = TtsHttpRule.parse(
                "https://fanyi.baidu.com/gettts?lan=zh&text={{java.encodeURI(speakText)}}&spd={{speakSpeed}}&source=web",
                "audio/mpeg");
        assertTrue(rule.isUsable());
        for (float rate : new float[]{0.5f, 1.0f, 1.5f, 2.0f}) {
            int speed = Math.max(1, Math.min(7, Math.round(rate * 5f)));
            assertTrue("rate=" + rate + " speed=" + speed, speed >= 1 && speed <= 7);
            String url = rule.url("测试", speed);
            assertTrue(url, url.contains("spd=" + speed));
        }
    }

    @Test
    public void optionsAreOrderedDescendingWhenMultipleCommasExist() {
        // 真实场景：URL 模板里带逗号，选项在最后
        String raw = "https://host/tts?t={{java.encodeURI(speakText)}},{\"method\":\"GET\"}";
        TtsHttpRule rule = TtsHttpRule.parse(raw, "audio/mpeg");
        assertTrue(rule.unsupportedReason(), rule.isUsable());
        assertEquals("https://host/tts?t=a", rule.url("a", 5));
    }
}
