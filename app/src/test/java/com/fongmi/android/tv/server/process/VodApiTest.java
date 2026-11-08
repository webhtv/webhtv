package com.fongmi.android.tv.server.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fongmi.android.tv.api.CatSource;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.VodSiteCatalog;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.bean.Rule;
import com.github.catvod.bean.Doh;
import com.github.catvod.crawler.Spider;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 本机 T4 网关的转换契约。
 *
 * <p>参考对象是影视+ 实验室的 {@code T4Proxy.js} 与 {@code vod.php}：两者都把
 * Spider API 的返回重新包装成苹果CMS V10。这里断言同一份信封形状与字段名，
 * 保证 T4 客户端在两套实现之间可以互换订阅。
 */
public class VodApiTest {

    @Test
    public void configListsEverySiteAsType4PointingBackAtGateway() {
        List<Site> sites = new ArrayList<>();
        sites.add(Site.get("csp_Demo", "演示站"));
        Site noSearch = Site.get("js_site", "JS站");
        noSearch.setSearchable(false);
        sites.add(noSearch);

        JsonObject config = VodApi.configJson(sites, "http://192.168.1.9:9978/vod/api", null, null);

        assertEquals("", config.get("spider").getAsString());
        assertEquals("资源来自网络，仅供学习使用", config.get("warningText").getAsString());
        JsonArray out = config.getAsJsonArray("sites");
        assertEquals(2, out.size());

        JsonObject first = out.get(0).getAsJsonObject();
        assertEquals("csp_Demo", first.get("key").getAsString());
        assertEquals("演示站", first.get("name").getAsString());
        assertEquals("http://192.168.1.9:9978/vod/api?key=csp_Demo", first.get("api").getAsString());
        assertEquals("type 必须是字符串 4，与 T4Proxy.js 一致", "4", first.get("type").getAsString());
        assertEquals(1, first.get("searchable").getAsInt());
        assertEquals(1, first.get("filterable").getAsInt());

        assertEquals("不可搜索的站点要如实上报", 0, out.get(1).getAsJsonObject().get("searchable").getAsInt());
        assertTrue(config.has("doh"));
        assertTrue(config.has("rules"));
        assertTrue(config.has("lives"));
    }

    @Test
    public void macCmsEnvelopeMatchesT4ProxyShape() {
        Result result = new Result();
        result.setList(List.of(vod("vod-1", "测试片")));
        result.setTypes(List.of(type("movie", "电影")));

        JsonObject out = VodApi.macCms(result, 3);

        assertEquals(3, out.get("page").getAsInt());
        assertEquals(1, out.get("limit").getAsInt());
        assertEquals("站源没给总页数时按 9999 处理", 9999, out.get("pagecount").getAsInt());
        assertEquals(9999, out.get("total").getAsInt());
        assertEquals(1, out.getAsJsonArray("list").size());
        assertEquals(1, out.getAsJsonArray("class").size());
        assertEquals("movie", out.getAsJsonArray("class").get(0).getAsJsonObject().get("type_id").getAsString());
    }

    @Test
    public void macCmsUsesSourcePageCountWhenPresent() {
        Result result = new Result();
        setField(result, "pagecount", 7);
        result.setList(List.of(vod("vod-1", "测试片")));

        JsonObject out = VodApi.macCms(result, 2);

        assertEquals(7, out.get("pagecount").getAsInt());
        assertEquals(7, out.get("total").getAsInt());
    }

    @Test
    public void macCmsNeverLeaksInternalSiteState() {
        Vod item = vod("vod-1", "测试片");
        item.setSite(Site.get("csp_Secret", "内部站点"));

        JsonObject out = VodApi.macCms(result(item), 1).getAsJsonArray("list").get(0).getAsJsonObject();

        assertFalse("T4 响应不能带出内部 Site 对象", out.has("site"));
        assertFalse("T4 响应不能带出内部 flag 对象", out.has("vodFlags"));
        assertFalse("T4 响应不能带出内部 TMDB 载荷", out.has("tmdb"));
    }

    @Test
    public void vodJsonMapsStandardAppleCmsFields() {
        Vod item = vod("vod-9", "片名");
        item.setTypeName("动作片");
        item.setPic("https://img.example/9.jpg");
        item.setRemarks("更新至08集");
        item.setYear("2026");
        item.setArea("大陆");
        item.setDirector("导演");
        item.setActor("演员");
        item.setContent("简介");

        JsonObject out = VodApi.vodJson(item);

        assertEquals("vod-9", out.get("vod_id").getAsString());
        assertEquals("片名", out.get("vod_name").getAsString());
        assertEquals("动作片", out.get("type_name").getAsString());
        assertEquals("https://img.example/9.jpg", out.get("vod_pic").getAsString());
        assertEquals("更新至08集", out.get("vod_remarks").getAsString());
        assertEquals("2026", out.get("vod_year").getAsString());
        assertEquals("大陆", out.get("vod_area").getAsString());
        assertEquals("导演", out.get("vod_director").getAsString());
        assertEquals("演员", out.get("vod_actor").getAsString());
        assertEquals("简介", out.get("vod_content").getAsString());
    }

    @Test
    public void vodJsonFallsBackToFlagsWhenSpiderOnlyReturnsFlagArray() {
        // T3 爬虫常见形态：只有 flags，没有 vod_play_from / vod_play_url
        Vod item = new Vod();
        item.setId("vod-1");
        item.setName("测试片");
        Flag hd = Flag.create("HD");
        hd.setEpisodes("第01集$https://v.example/1.m3u8#第02集$https://v.example/2.m3u8");
        Flag sd = Flag.create("SD");
        sd.setEpisodes("第01集$https://v.example/sd1.m3u8");
        item.setFlags(new ArrayList<>(List.of(hd, sd)));

        JsonObject out = VodApi.vodJson(item);

        assertEquals("HD$$$SD", out.get("vod_play_from").getAsString());
        assertEquals("第01集$https://v.example/1.m3u8#第02集$https://v.example/2.m3u8$$$第01集$https://v.example/sd1.m3u8",
                out.get("vod_play_url").getAsString());
    }

    @Test
    public void vodJsonKeepsSourceProvidedPlayFields() {
        Vod item = new Vod();
        item.setId("vod-1");
        item.setName("测试片");
        item.setPlayFrom("线路A");
        item.setPlayUrl("第01集$https://v.example/a.m3u8");

        JsonObject out = VodApi.vodJson(item);

        assertEquals("线路A", out.get("vod_play_from").getAsString());
        assertEquals("第01集$https://v.example/a.m3u8", out.get("vod_play_url").getAsString());
    }

    @Test
    public void playEnvelopeCarriesResolvedUrlAndClientHints() {
        Result result = new Result();
        result.setUrl("https://v.example/final.m3u8");
        result.setFlag("HD");
        result.setParse(1);
        result.setFormat("application/x-mpegURL");
        HashMap<String, String> header = new HashMap<>();
        header.put("Referer", "https://v.example/");
        result.setHeader(header);

        JsonObject out = VodApi.play(result, "HD");

        assertEquals("https://v.example/final.m3u8", out.get("url").getAsString());
        assertEquals("HD", out.get("flag").getAsString());
        assertEquals(1, out.get("parse").getAsInt());
        assertEquals("application/x-mpegURL", out.get("format").getAsString());
        assertEquals("https://v.example/", out.getAsJsonObject("header").get("Referer").getAsString());
    }

    @Test
    public void playEnvelopeFallsBackToRequestedFlag() {
        Result result = new Result();
        result.setUrl("https://v.example/final.m3u8");

        JsonObject out = VodApi.play(result, "线路A");

        assertEquals("线路A", out.get("flag").getAsString());
    }

    @Test
    public void playEnvelopeListsMultipleResolvedUrls() {
        Result result = new Result();
        // setUrl 会真正写入字段；之后 getUrl() 才返回同一个对象，add 才能生效。
        result.setUrl("https://v.example/a.m3u8");
        result.getUrl().add("https://v.example/b.m3u8");

        JsonObject out = VodApi.play(result, "HD");

        assertTrue(out.has("urls"));
        assertEquals(2, out.getAsJsonArray("urls").size());
        assertEquals("首选仍是 position 指向的那条", "https://v.example/a.m3u8", out.get("url").getAsString());
        assertEquals("https://v.example/b.m3u8", out.getAsJsonArray("urls").get(1).getAsString());
    }

    @Test
    public void playEnvelopeToleratesResultWithoutUrl() {
        JsonObject out = VodApi.play(new Result(), "HD");

        assertEquals("", out.get("url").getAsString());
        assertEquals("HD", out.get("flag").getAsString());
    }

    @Test
    public void routePrefersConfigThenSearchThenExplicitAc() {
        assertEquals("config", VodApi.route(parse("{\"ac\":\"config\"}")));
        assertEquals("config", VodApi.route(parse("{\"ac\":\"site\"}")));
        assertEquals("search", VodApi.route(parse("{\"wd\":\"关键词\"}")));
        assertEquals("search", VodApi.route(parse("{\"ac\":\"detail\",\"wd\":\"关键词\"}")));
        assertEquals("list", VodApi.route(parse("{\"ac\":\"detail\"}")));
        assertEquals("play", VodApi.route(parse("{\"ac\":\"play\"}")));
        assertEquals("list", VodApi.route(parse("{\"ac\":\"list\"}")));
        assertEquals("list", VodApi.route(parse("{}")));
    }

    @Test
    public void routeInfersFromParametersWhenAcIsAbsent() {
        assertEquals("detail", VodApi.route(parse("{\"ids\":\"1,2\"}")));
        assertEquals("play", VodApi.route(parse("{\"flag\":\"HD\",\"id\":\"1\"}")));
        assertEquals("play", VodApi.route(parse("{\"flag\":\"HD\",\"play\":\"1\"}")));
        assertEquals("list", VodApi.route(parse("{\"t\":\"1\",\"pg\":\"2\"}")));
        assertEquals("list", VodApi.route(parse("{\"flag\":\"HD\"}")));
    }

    @Test
    public void siteKeyComesFromPathOrQuery() {
        assertEquals("csp_Demo", VodApi.siteKeyFromPath("/vod/api/csp_Demo"));
        // IHTTPSession.getUri() 已去掉查询部分并解码路径。
        assertEquals("中文/key?+", VodApi.siteKeyFromPath("/vod/api/中文/key?+"));
        assertEquals("csp_Demo/detail", VodApi.siteKeyFromPath("/vod/api/csp_Demo/detail"));
        assertEquals("", VodApi.siteKeyFromPath("/vod/api"));
        assertEquals("", VodApi.siteKeyFromPath("/vod/api?ac=config"));
        assertEquals("", VodApi.siteKeyFromPath("/spider"));
    }

    @Test
    public void extendMergesExplicitFilterFields() {
        HashMap<String, String> extend = VodApi.extendFrom(parse("{\"area\":\"大陆\",\"year\":\"2026\"}"));

        assertEquals("大陆", extend.get("area"));
        assertEquals("2026", extend.get("year"));
        assertFalse(extend.containsKey("type"));
    }

    @Test
    public void extendReadsJsonExtendParameter() {
        HashMap<String, String> extend = VodApi.extendFrom(parse("{\"extend\":\"{\\\"lang\\\":\\\"国语\\\"}\"}"));

        assertEquals("国语", extend.get("lang"));
    }

    @Test
    public void explicitFilterFieldsOverrideExtendJson() {
        HashMap<String, String> extend = VodApi.extendFrom(
                parse("{\"extend\":\"{\\\"year\\\":\\\"2020\\\"}\",\"year\":\"2026\"}"));

        assertEquals("2026", extend.get("year"));
    }

    @Test
    public void blankExtendIsEmptyWithoutDecoding() {
        assertTrue(VodApi.extendFrom(parse("{}")).isEmpty());
        assertEquals(null, VodApi.decodeExtend(""));
    }

    @Test
    public void idsAcceptsCommaListAndJsonArray() {
        assertEquals("1,2", VodApi.csv(parse("{\"ids\":\"1,2\"}"), "ids"));
        assertEquals("1,2", VodApi.csv(parse("{\"ids\":[\"1\",\"2\"]}"), "ids"));
        assertEquals("", VodApi.csv(parse("{}"), "ids"));
    }

    @Test
    public void errorEnvelopeMatchesSpiderApiShape() {
        JsonObject out = VodApi.error("site not found: demo");

        assertEquals(-1, out.get("code").getAsInt());
        assertEquals("site not found: demo", out.get("msg").getAsString());
    }

    @Test
    public void realT4CategoryRequestMustNotBeTreatedAsDetail() {
        assertEquals("list", VodApi.route(parse("{\"ac\":\"detail\",\"t\":\"movie\",\"pg\":\"2\"}")));
        assertEquals("detail", VodApi.route(parse("{\"ac\":\"detail\",\"ids\":\"a,b\"}")));
        assertEquals("list", VodApi.route(parse("{\"ac\":\"detail\"}")));
    }

    @Test
    public void routeDoesNotClaimAdjacentEndpoints() {
        assertTrue(new VodApi().isRequest(null, "/vod/api"));
        assertTrue(new VodApi().isRequest(null, "/vod/api/demo"));
        assertFalse(new VodApi().isRequest(null, "/vod/apix"));
    }

    @Test
    public void urlSafeBase64FiltersMustReachCategoryUnchanged() {
        String ext = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"year\":\"2026\",\"area\":\"大陆\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        JsonObject request = new JsonObject();
        request.addProperty("ext", ext);
        assertEquals("2026", VodApi.extendFrom(request).get("year"));
        assertEquals("大陆", VodApi.extendFrom(request).get("area"));
    }

    @Test
    public void httpRoundTripSupportsActualT4ClientRequests() throws Exception {
        try (HttpFixture fixture = new HttpFixture()) {
            JsonObject config = fixture.request("GET", "?ac=config", null).body;
            String api = config.getAsJsonArray("sites").get(0).getAsJsonObject().get("api").getAsString();
            assertTrue(api, api.endsWith("?key=" + encode(HttpFixture.KEY)));
            String route = "?key=" + encode(HttpFixture.KEY);
            JsonObject home = fixture.request("GET", route, null).body;
            assertEquals("7", home.getAsJsonArray("class").get(0).getAsJsonObject().get("type_id").getAsString());
            assertTrue(home.has("filters"));
            assertEquals("推荐", home.getAsJsonArray("list").get(0).getAsJsonObject().get("vod_name").getAsString());

            String ext = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"year\":\"2026\"}".getBytes(StandardCharsets.UTF_8));
            JsonObject category = fixture.request("GET", route + "&ac=detail&t=7&pg=2&ext=" + ext, null).body;
            assertEquals("category:7:2:true", fixture.spider.lastCall);
            assertEquals("2026", fixture.spider.extend.get("year"));
            assertEquals(4, category.get("pagecount").getAsInt());
            assertEquals(10, category.get("limit").getAsInt());
            assertEquals(33, category.get("total").getAsInt());
            assertEquals("7", category.getAsJsonArray("list").get(0).getAsJsonObject().get("type_id").getAsString());

            JsonObject detail = fixture.request("POST", route, "{\"ac\":\"detail\",\"ids\":[\"first\",\"second,opaque\"]}").body;
            assertEquals(List.of("first", "second,opaque"), fixture.spider.ids);
            assertEquals(2, detail.getAsJsonArray("list").size());
            assertFalse(detail.getAsJsonArray("list").get(0).getAsJsonObject().has("site"));
            fixture.request("GET", route + "&wd=test&quick=true&pg=2", null);
            assertEquals("search:test:true:2", fixture.spider.lastCall);
            fixture.request("GET", route + "&wd=test", null);
            assertEquals("search:test:false:1", fixture.spider.lastCall);

            JsonObject play = fixture.request("GET", route + "&play=opaque%2Bplay&flag=HD", null).body;
            assertEquals("play:HD:opaque+play", fixture.spider.lastCall);
            assertEquals(List.of("vip"), fixture.spider.flags);
            assertEquals(1, play.get("parse").getAsInt());
            assertEquals(1, play.get("jx").getAsInt());
            assertEquals(fixture.origin + "/proxy?do=fixture&token=a%2Bb&siteKey=" + encode(HttpFixture.KEY), play.get("url").getAsString());
            assertEquals("https://example.test/", play.getAsJsonObject("header").get("Referer").getAsString());
        }
    }

    @Test
    public void httpPreflightAndErrorsDoNotExecuteSpiders() throws Exception {
        try (HttpFixture fixture = new HttpFixture()) {
            assertEquals(204, fixture.request("OPTIONS", "?key=" + encode(HttpFixture.KEY) + "&play=x&flag=HD", null).status);
            assertEquals("", fixture.spider.lastCall);
            assertEquals(-1, fixture.request("GET", "?key=unknown", null).body.get("code").getAsInt());
            assertEquals(-1, fixture.request("POST", "", "{malformed").body.get("code").getAsInt());
            assertEquals(-1, fixture.request("GET", "?key=" + encode(HttpFixture.KEY) + "&ac=play", null).body.get("code").getAsInt());
            assertEquals("", fixture.spider.lastCall);
        }
    }

    @Test
    public void localUrlsKeepOpaqueQueryAndPinProxySource() {
        String origin = "http://192.168.50.3:9978";
        assertEquals(origin + "/proxy?url=https%3A%2F%2Fa.test%2Fv&sig=a%2Bb&siteKey=source%2Bkey",
                VodApi.remoteUrl("http://localhost:9978/proxy?url=https%3A%2F%2Fa.test%2Fv&sig=a%2Bb", origin, "source+key", 9978));
        String external = "https://cdn.test/v?next=http://127.0.0.1:9978/proxy";
        assertEquals(external, VodApi.remoteUrl(external, origin, "source", 9978));
        assertEquals("http://127.0.0.1:1234/v", VodApi.remoteUrl("http://127.0.0.1:1234/v", origin, "source", 9978));
        assertEquals(origin + "/proxy?siteKey=original", VodApi.remoteUrl("http://[::1]:9978/proxy?siteKey=original", origin, "other", 9978));
    }

    @Test
    public void rawPlaybackKeepsAlternativesSubtitlesAndDrm() {
        JsonObject raw = parse("{\"url\":[\"HD\",\"http://127.0.0.1:9978/proxy?do=x\",\"SD\",\"https://cdn.test/sd\"],\"parse\":1,\"playUrl\":\"https://parse.test/?url=\",\"header\":{\"Referer\":\"http://localhost:9978/unchanged\"},\"subs\":[{\"url\":\"http://localhost:9978/sub.vtt\"}],\"drm\":{\"server\":\"http://localhost:9978/license\"}}");
        JsonObject out = VodApi.playJson(raw, "HD", Site.get("s", "source"));
        VodApi.externalize(out, "http://192.168.1.1:9978", "s", 9978);
        assertEquals("HD", out.getAsJsonArray("url").get(0).getAsString());
        assertEquals("http://192.168.1.1:9978/proxy?do=x&siteKey=s", out.getAsJsonArray("url").get(1).getAsString());
        assertEquals("https://cdn.test/sd", out.getAsJsonArray("url").get(3).getAsString());
        assertEquals("https://parse.test/?url=", out.get("playUrl").getAsString());
        assertEquals("http://localhost:9978/unchanged", out.getAsJsonObject("header").get("Referer").getAsString());
        assertEquals("http://192.168.1.1:9978/sub.vtt", out.getAsJsonArray("subs").get(0).getAsJsonObject().get("url").getAsString());
        assertEquals("http://192.168.1.1:9978/license", out.getAsJsonObject("drm").get("server").getAsString());
    }

    @Test
    public void emptyPagesStopAndLargeTotalsDoNotOverflow() {
        assertEquals(3, VodApi.formatContent(parse("{\"list\":[]}"), 3).get("pagecount").getAsInt());
        JsonObject full = VodApi.formatContent(parse("{\"pagecount\":2147483647,\"limit\":20,\"list\":[]}"), 1);
        assertEquals(42949672940L, full.get("total").getAsLong());
    }

    @Test
    public void jsonObjectFiltersAndEmptyFlagSlotsArePreserved() {
        assertEquals("2026", VodApi.extendFrom(parse("{\"extend\":{\"year\":\"2026\"}}")).get("year"));
        JsonObject out = VodApi.formatContent(parse("{\"list\":[{\"vod_id\":\"a\",\"flags\":[{\"flag\":\"empty\",\"episodes\":[]},{\"flag\":\"HD\",\"episodes\":[{\"name\":\"01\",\"url\":\"id\"}]}]}]}"), 1);
        JsonObject vod = out.getAsJsonArray("list").get(0).getAsJsonObject();
        assertEquals("empty$$$HD", vod.get("vod_play_from").getAsString());
        assertEquals("$$$01$id", vod.get("vod_play_url").getAsString());
    }

    @Test
    public void selfSubscriptionIsRejectedWithoutBlockingOtherDevices() {
        assertTrue(VodApi.isSelfGateway("http://127.0.0.1:9978/vod/api?key=x", "http://192.168.1.1:9978", 9978));
        assertTrue(VodApi.isSelfGateway("http://192.168.1.1:9978/vod/api/x", "http://192.168.1.1:9978", 9978));
        assertFalse(VodApi.isSelfGateway("http://192.168.1.2:9978/vod/api/x", "http://192.168.1.1:9978", 9978));
    }

    @Test
    public void directProxyPinningDoesNotDisableLegacyJarProxy() {
        assertFalse(VodApi.supportsDirectProxy(new Spider() { }));
        assertTrue(VodApi.supportsDirectProxy(new FixtureSpider()));
    }

    /** 桥接进来的配置：站点清单来自 catalog，当前激活源必须完全不参与。 */
    @Test
    public void configKeyServesAnotherConfigWithoutTouchingActiveSource() throws Exception {
        FixtureSpider other = new FixtureSpider();
        Site site = new Site() {
            @Override public Spider spider() { return other; }
            @Override public Site recent() { throw new AssertionError("Catalog must not change the active loader"); }
        };
        site.setKey("catalog_site");
        site.setName("订阅站");
        site.setApi("csp_Catalog");
        site.setType(3);
        VodSiteCatalog.Source catalogs = interfaceKey -> {
            assertEquals("bridge-1", interfaceKey);
            return new VodSiteCatalog.Catalog(List.of(site), List.of(), List.of(), List.of("svip"));
        };

        try (HttpFixture fixture = new HttpFixture(catalogs)) {
            JsonObject config = fixture.request("GET", "?ac=config&configKey=bridge-1", null).body;
            JsonObject item = config.getAsJsonArray("sites").get(0).getAsJsonObject();
            assertEquals("catalog_site", item.get("key").getAsString());
            assertTrue(item.get("api").getAsString(), item.get("api").getAsString().endsWith("?key=catalog_site&configKey=bridge-1"));

            String route = "?key=catalog_site&configKey=bridge-1";
            JsonObject home = fixture.request("GET", route, null).body;
            assertEquals("7", home.getAsJsonArray("class").get(0).getAsJsonObject().get("type_id").getAsString());
            assertEquals("home:true", other.lastCall);

            JsonObject play = fixture.request("GET", route + "&play=opaque&flag=HD", null).body;
            assertEquals("play:HD:opaque", other.lastCall);
            assertEquals(List.of("svip"), other.flags);
            assertEquals(fixture.origin + "/proxy?do=fixture&token=a%2Bb&siteKey=catalog_site&configKey=bridge-1",
                    play.get("url").getAsString());
            assertEquals("桥接配置不能触发当前激活源", "", fixture.spider.lastCall);
        }
    }

    @Test
    public void unknownConfigKeyFailsWithoutFallingBackToActiveSource() throws Exception {
        VodSiteCatalog.Source catalogs = interfaceKey -> {
            throw new IllegalArgumentException("未知的配置标识: " + interfaceKey);
        };

        try (HttpFixture fixture = new HttpFixture(catalogs)) {
            JsonObject body = fixture.request("GET", "?ac=config&configKey=missing", null).body;
            assertEquals(-1, body.get("code").getAsInt());
            assertEquals("未知的配置标识: missing", body.get("msg").getAsString());
            assertEquals("", fixture.spider.lastCall);
        }
    }

    @Test
    public void proxyUrlsCarrySiteAndConfigKeyForNonActiveSources() {
        String origin = "http://192.168.50.3:9978";
        assertEquals(origin + "/proxy?do=x&siteKey=site&configKey=cfg%2B1",
                VodApi.remoteUrl("http://127.0.0.1:9978/proxy?do=x", origin, "site", "cfg+1", 9978));
        assertEquals(origin + "/proxy?do=x&siteKey=site",
                VodApi.remoteUrl("http://127.0.0.1:9978/proxy?do=x", origin, "site", "", 9978));
        assertEquals(origin + "/proxy?siteKey=orig&configKey=cfg",
                VodApi.remoteUrl("http://127.0.0.1:9978/proxy?siteKey=orig&configKey=cfg", origin, "s", "cfg", 9978));
        assertEquals("http://127.0.0.1:1234/proxy?do=x",
                VodApi.remoteUrl("http://127.0.0.1:1234/proxy?do=x", origin, "site", "cfg", 9978));
    }

    /**
     * B1：桥接解析遇到非激活配置的猫源时必须直接失败。
     *
     * <p>{@link CatSource#reuse} 是唯一的放行口子；实现里一旦退回 {@code serve}，这里就会
     * 因为「当前没有 bundle 在服务」而拿到 IllegalStateException，而不是去动全局 node。
     */
    @Test
    public void nonActiveBundleIsRejectedInsteadOfRestartingNode() {
        try {
            CatSource.reuse("http://127.0.0.1:9988/cat/index.js");
            fail("非激活配置的 bundle 必须被拒绝");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("已拒绝解析"));
        }

        String catalog = read("com/fongmi/android/tv/api/config/VodSiteCatalog.java");
        assertTrue("桥接解析只允许复用已在服务的 bundle", catalog.contains("CatSource.reuse("));
        assertFalse("桥接解析不得调用会换源杀进程的 CatSource.serve", catalog.contains("CatSource.serve("));

        String runtime = read("com/fongmi/android/tv/node/NodeRuntime.java");
        assertTrue("NodeRuntime 需要只读判定", runtime.contains("public static boolean isServing(String url)"));
        assertTrue("isServing 必须复用 same() 的比对语义", runtime.indexOf("isServing") < runtime.indexOf("private static boolean same(") + 4000);
    }

    /** B2：缓存槽的命名空间前缀，空命名空间必须与改动前逐字节等价。 */
    @Test
    public void spiderSlotKeepsActiveCacheKeysByteIdentical() {
        assertEquals("active_site", BaseLoader.slot("", "active_site"));
        assertEquals("active_site", BaseLoader.slot(null, "active_site"));
        assertEquals("cfg@@@active_site", BaseLoader.slot("cfg", "active_site"));
        assertEquals(java.util.Set.of("active_site"), BaseLoader.baseKeys(List.of("active_site")));
        assertEquals(java.util.Set.of("active_site"), BaseLoader.baseKeys(List.of("cfg@@@active_site")));
        assertEquals(java.util.Set.of("a", "b"), BaseLoader.baseKeys(List.of("a", "cfg@@@b")));

        String loader = read("com/fongmi/android/tv/api/loader/BaseLoader.java");
        assertTrue("猫源站点的缓存键必须带命名空间", loader.contains("catSpiders.computeIfAbsent(slot, k -> new CatSpider(api))"));
        assertTrue("四种 spider 都要拿到命名空间",
                loader.contains("pyLoader.getSpider(namespace, key, api, ext)")
                        && loader.contains("jsLoader.getSpider(namespace, key, api, ext, jar)")
                        && loader.contains("jarLoader.getSpider(namespace, key, api, ext, jar)"));
        assertTrue("桥接 spider 必须落在 configKey 命名空间里",
                loader.contains("getSpider(VodSiteCatalog.namespace(configKey), site.getKey()"));

        for (String name : new String[]{"JsLoader", "PyLoader", "JarLoader"}) {
            String source = read("com/fongmi/android/tv/api/loader/" + name + ".java");
            assertTrue(name + " 必须用 slot 建缓存键", source.contains("BaseLoader.slot(namespace, key)"));
        }
        String jar = read("com/fongmi/android/tv/api/loader/JarLoader.java");
        assertTrue("jar 的 dex 缓存仍只按 jar md5 建键", jar.contains("String jaKey = Util.md5(jar)"));
        assertTrue("插件脚本保护要看到不带命名空间的 key",
                read("com/fongmi/android/tv/api/loader/JsLoader.java").contains("BaseLoader.baseKeys(spiders.keySet())"));
    }

    /** B3：显式带 configKey 时解析失败不许回落到激活配置。 */
    @Test
    public void catalogProxyNeverFallsBackToActiveLoader() {
        String loader = read("com/fongmi/android/tv/api/loader/BaseLoader.java");
        assertFalse("不得保留 spider 为空就回落的写法", loader.contains("spider != null ? spider :"));
        assertTrue("带 configKey 时只走 catalogSpider",
                loader.contains("if (!TextUtils.isEmpty(params.get(\"configKey\"))) return catalogSpider("));
        int method = loader.indexOf("private Spider catalogSpider(");
        assertTrue("catalogSpider 必须向调用方抛异常", method > 0 && loader.indexOf("throws Exception", method) > method);
        assertFalse("catalogSpider 不得把异常吞成 null", loader.substring(method, loader.indexOf("public void parseJar")).contains("catch"));

        String proxy = read("com/fongmi/android/tv/server/process/Proxy.java");
        assertTrue("异常必须转成可见的错误响应", proxy.contains("return Nano.error(Objects.toString(e.getMessage(), e.toString()));"));
    }

    /** B4：配置标识的解析与既有播放身份解析器收敛成一套。 */
    @Test
    public void configKeyResolutionAcceptsExactAndLegacyIdentity() {
        Config exact = config(11, "https://exact.example/config");
        Config legacy = config(22, "https://legacy.example/config");

        assertSame("精确命中优先", exact, VodSiteCatalog.resolve("k", exact, legacy));
        assertSame("未命中精确键时沿用既有解析器", legacy, VodSiteCatalog.resolve("k", null, legacy));

        for (Config[] pair : new Config[][]{{null, null}, {exact, null}}) {
            String id = pair[0] == null ? "missing" : "blank-url";
            if (pair[0] != null) pair[0].setUrl("  ");
            try {
                VodSiteCatalog.resolve(id, pair[0], pair[1]);
                fail("未知的配置标识必须报错: " + id);
            } catch (IllegalArgumentException e) {
                assertEquals("未知的配置标识: " + id, e.getMessage());
            }
        }

        String catalog = code("com/fongmi/android/tv/api/config/VodSiteCatalog.java");
        assertTrue("必须接入既有身份解析器", catalog.contains("PlaybackConfigIdentity.cidForKey(id)"));
        assertTrue("兜底按 cid 只读取行", catalog.contains("Config.find(cid)"));
        assertFalse("桥接解析绝不能走会插行的 Config.find(url, type)", catalog.contains("Config.find(url"));
    }

    /** M1/M2/M3：缓存失效入口、时间戳取在解析之后、清单补齐原生加载的两步。 */
    @Test
    public void catalogCacheAndSiteListMatchNativeLoading() {
        String catalog = read("com/fongmi/android/tv/api/config/VodSiteCatalog.java");
        assertTrue("需要公开的失效入口", catalog.contains("public static void invalidate()"));
        assertTrue("解析完成后再取时间戳",
                catalog.indexOf("catalogOf(findConfig(id), id)") > 0
                        && catalog.indexOf("new Entry(catalog, System.currentTimeMillis())") > catalog.indexOf("catalogOf(findConfig(id), id)"));
        assertTrue("不得在解析前采样时间", !catalog.contains("long now = System.currentTimeMillis()"));
        assertTrue("补齐 Site.findAll() 回填", catalog.contains("for (Site site : Site.findAll()) known.put(site.getKey(), site);"));
        assertTrue("补齐 CustomCspSetting 合并", catalog.contains("CustomCspSetting.inject(sites)"));

        String config = read("com/fongmi/android/tv/bean/Config.java");
        int hooks = 0;
        for (int at = config.indexOf("invalidateCatalog();"); at >= 0; at = config.indexOf("invalidateCatalog();", at + 1)) hooks++;
        assertEquals("insert/save/delete 三处都要失效缓存", 3, hooks);
    }

    private static Config config(int id, String url) {
        Config config = new Config();
        config.setType(0);
        config.setUrl(url);
        config.setId(id);
        return config;
    }

    private static String code(String relative) {
        StringBuilder builder = new StringBuilder();
        for (String line : read(relative).split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue;
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    private static String read(String relative) {
        java.nio.file.Path path = mainJava().resolve(java.nio.file.Paths.get(relative.replace('/', java.io.File.separatorChar)));
        try {
            return new String(java.nio.file.Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError("无法读取 " + path, e);
        }
    }

    private static java.nio.file.Path mainJava() {
        java.nio.file.Path dir = java.nio.file.Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            java.nio.file.Path candidate = dir.resolve(java.nio.file.Paths.get("app", "src", "main", "java"));
            if (java.nio.file.Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("app/src/main/java not found from " + java.nio.file.Paths.get("").toAbsolutePath());
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
    }

    private static final class FixtureSpider extends Spider {
        volatile String lastCall = "";
        volatile Map<String, String> extend = Map.of();
        volatile List<String> ids = List.of();
        volatile List<String> flags = List.of();

        @Override public String homeContent(boolean filter) {
            lastCall = "home:" + filter;
            return "{\"class\":[{\"type_id\":\"7\",\"type_name\":\"电影\"}],\"filters\":{\"7\":[{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"2026\",\"v\":\"2026\"}]}]}}";
        }
        @Override public String homeVideoContent() { return "{\"list\":[{\"vod_id\":\"home\",\"vod_name\":\"推荐\"}]}"; }
        @Override public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
            lastCall = "category:" + tid + ":" + pg + ":" + filter;
            this.extend = new HashMap<>(extend);
            return "{\"page\":2,\"pagecount\":4,\"limit\":10,\"total\":33,\"list\":[{\"vod_id\":\"category\",\"type_id\":\"7\",\"vod_name\":\"分类\"}]}";
        }
        @Override public String detailContent(List<String> ids) {
            lastCall = "detail";
            this.ids = new ArrayList<>(ids);
            JsonArray list = new JsonArray();
            for (String id : ids) {
                JsonObject item = new JsonObject();
                item.addProperty("vod_id", id);
                item.addProperty("vod_name", id);
                item.addProperty("vod_play_from", "HD");
                item.addProperty("vod_play_url", "01$opaque");
                item.add("site", parse("{\"key\":\"private\"}"));
                list.add(item);
            }
            JsonObject result = new JsonObject();
            result.add("list", list);
            return result.toString();
        }
        @Override public String searchContent(String key, boolean quick) { return searchContent(key, quick, "1"); }
        @Override public String searchContent(String key, boolean quick, String pg) {
            lastCall = "search:" + key + ":" + quick + ":" + pg;
            return "{\"list\":[]}";
        }
        @Override public Object[] proxy(Map<String, String> params) {
            return new Object[]{200, "text/plain", new java.io.ByteArrayInputStream("fixture".getBytes(StandardCharsets.UTF_8))};
        }
        @Override public String playerContent(String flag, String id, List<String> flags) {
            lastCall = "play:" + flag + ":" + id;
            this.flags = new ArrayList<>(flags);
            return "{\"url\":\"http://127.0.0.1:9978/proxy?do=fixture&token=a%2Bb\",\"parse\":1,\"jx\":1,\"header\":{\"Referer\":\"https://example.test/\"}}";
        }
    }

    private static final class HttpFixture implements AutoCloseable {
        static final String KEY = "中文/+?&key";
        final FixtureSpider spider = new FixtureSpider();
        final NanoHTTPD server;
        final String origin;
        final int previousPort;

        HttpFixture() throws Exception {
            this(null);
        }

        HttpFixture(VodSiteCatalog.Source catalogs) throws Exception {
            Site site = new Site() {
                @Override public Spider spider() { return spider; }
                @Override public Site recent() { throw new AssertionError("Gateway must not change the active loader"); }
            };
            site.setKey(KEY);
            site.setName("fixture");
            site.setApi("csp_Fixture");
            site.setType(3);
            VodConfig sources = new VodConfig() {
                @Override public List<Site> getSites() { return List.of(site); }
                @Override public List<Doh> getDoh() { return List.of(); }
                @Override public List<Rule> getRules() { return List.of(); }
                @Override public List<String> getFlags() { return List.of("vip"); }
            };
            VodApi gateway = new VodApi(sources, catalogs != null ? catalogs : VodSiteCatalog.database());
            server = new NanoHTTPD("127.0.0.1", 0) {
                @Override public Response serve(IHTTPSession session) {
                    if (!gateway.isRequest(session, session.getUri())) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
                    Map<String, String> files = new HashMap<>();
                    try {
                        if (session.getMethod() == Method.POST) session.parseBody(files);
                        return gateway.doResponse(session, session.getUri(), files);
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                }
            };
            previousPort = com.github.catvod.Proxy.getPort();
            com.github.catvod.Proxy.set(9978);
            try { server.start(5000, true); } catch (Exception e) { com.github.catvod.Proxy.set(previousPort); throw e; }
            origin = "http://127.0.0.1:" + server.getListeningPort();
        }

        HttpResult request(String method, String suffix, String body) throws Exception {
            HttpURLConnection connection = (HttpURLConnection) new URL(origin + "/vod/api" + suffix).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod(method);
            try {
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    try (java.io.OutputStream output = connection.getOutputStream()) { output.write(body.getBytes(StandardCharsets.UTF_8)); }
                }
                int status = connection.getResponseCode();
                String data;
                try (InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                    data = input == null ? "" : new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                return new HttpResult(status, data.isEmpty() ? new JsonObject() : JsonParser.parseString(data).getAsJsonObject());
            } finally {
                connection.disconnect();
            }
        }
        @Override public void close() {
            server.stop();
            com.github.catvod.Proxy.set(previousPort);
        }
    }

    private record HttpResult(int status, JsonObject body) { }

    private static Result result(Vod vod) {
        Result result = new Result();
        result.setList(List.of(vod));
        return result;
    }

    private static Vod vod(String id, String name) {
        Vod vod = new Vod();
        vod.setId(id);
        vod.setName(name);
        return vod;
    }

    private static Class type(String id, String name) {
        Class type = new Class();
        type.setTypeId(id);
        type.setTypeName(name);
        return type;
    }

    private static JsonObject parse(String json) {
        JsonObject out = VodApi.jsonObject(json);
        assertNotNull("应能解析为 JSON 对象: " + json, out);
        return out;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new AssertionError("无法设置字段 " + name, e);
        }
    }
}
