package com.fongmi.android.tv.service;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.ui.helper.TmdbMatchPolicy;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 证明消歧 lite 详情与完整详情在**打分输入**上完全等价。
 *
 * <p>消歧只用 {@code credits}/{@code aggregate_credits}（人物）与根对象基础字段（标题/国家/季数）。
 * 本测试对同一夹具构造两份 JSON：完整版与去掉
 * images/translations/external_ids/content_ratings/recommendations/similar 的 lite 版，
 * 断言 {@link TmdbService#cast}、{@link TmdbService#creators} 与
 * {@link TmdbMatchPolicy#splitSeasonDetailScore} 的输出逐条相同。
 * 只要该等价成立，消歧的排序与阈值判定就不可能因瘦身而改变。
 */
public class TmdbDisambiguationDetailParityTest {

    private static final String FULL = """
            {
              "id": 1399,
              "name": "测试剧",
              "original_name": "测试剧",
              "first_air_date": "2011-04-17",
              "origin_country": ["US"],
              "number_of_seasons": 2,
              "seasons": [{"season_number": 1, "episode_count": 10}, {"season_number": 2, "episode_count": 10}],
              "credits": {
                "cast": [
                  {"id": 1, "name": "演员甲", "character": "角色甲", "profile_path": "/a.jpg", "known_for_department": "Acting"},
                  {"id": 3, "name": "演员丙", "character": "角色丙", "profile_path": "/c.jpg", "known_for_department": "Acting"}
                ],
                "crew": [
                  {"id": 2, "name": "导演乙", "job": "Director", "department": "Directing", "profile_path": null}
                ]
              },
              "aggregate_credits": {
                "cast": [
                  {"id": 1, "name": "演员甲", "roles": [{"character": "角色甲"}], "profile_path": "/a.jpg", "known_for_department": "Acting"},
                  {"id": 3, "name": "演员丙", "roles": [{"character": "角色丙"}], "profile_path": "/c.jpg", "known_for_department": "Acting"}
                ],
                "crew": [
                  {"id": 2, "name": "导演乙", "jobs": [{"job": "Director"}], "department": "Directing"}
                ]
              },
              "images": {"posters": [{"file_path": "/p.jpg", "width": 2000, "height": 3000}], "backdrops": []},
              "translations": {"translations": [{"iso_639_1": "zh", "data": {"name": "测试剧"}}]},
              "external_ids": {"imdb_id": "tt0000001"},
              "content_ratings": {"results": [{"iso_3166_1": "US", "rating": "TV-MA"}]},
              "recommendations": {"page": 1, "results": [{"id": 9, "name": "推荐剧"}]},
              "similar": {"page": 1, "results": [{"id": 8, "name": "相似剧"}]}
            }
            """;

    @Test
    public void liteDetailKeepsEveryScoringInputIdentical() throws Exception {
        TmdbService service = new TmdbService();
        TmdbConfig config = config();

        JsonObject full = JsonParser.parseString(FULL).getAsJsonObject();
        JsonObject lite = JsonParser.parseString(FULL).getAsJsonObject();
        for (String key : List.of("images", "translations", "external_ids", "content_ratings", "recommendations", "similar")) {
            lite.remove(key);
        }

        // cast 是 scoreTmdbPeople 的第一路输入；creators 走 TextUtils.join（JVM 单测里是 Android 桩，
        // 会抛 NoSuchMethodError），因此它的输入由 disambiguationAppendKeepsOnlyScoringBlocks 从
        // 请求参数层面锁定：credits.crew 与 aggregate_credits.crew 必须同时保留。
        assertEquals(describe(service.cast(full, config)), describe(service.cast(lite, config)));

        for (String text : List.of("测试剧 第一季", "测试剧 分季版", "测试剧", "别名的剧 第2季")) {
            assertEquals("split-season score must not depend on stripped blocks: " + text,
                    TmdbMatchPolicy.splitSeasonDetailScore(text, full),
                    TmdbMatchPolicy.splitSeasonDetailScore(text, lite));
        }
    }

    /**
     * lite 请求只能保留打分需要的块：TV 必须同时带 {@code credits}（creators 的 crew 来源）与
     * {@code aggregate_credits}（cast 的优先来源），电影带 {@code credits}；
     * 并且绝不能带 images/translations/recommendations/similar。
     */
    @Test
    public void disambiguationAppendKeepsOnlyScoringBlocks() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/fongmi/android/tv/service/TmdbService.java"), StandardCharsets.UTF_8);
        int start = source.indexOf("private String disambiguationAppend(");
        assertTrue("disambiguationAppend must exist", start >= 0);
        int end = source.indexOf('}', source.indexOf("return", start));
        String body = source.substring(start, end + 1);

        assertTrue(body.contains("\"credits,aggregate_credits\""));
        assertTrue(body.contains("\"credits\""));
        for (String stripped : List.of("images", "translations", "external_ids", "content_ratings", "recommendations", "similar")) {
            assertFalse("lite detail must not request " + stripped, body.contains(stripped));
        }
        assertTrue("the lite append must be wired into the request",
                source.contains("disambiguation ? disambiguationAppend(item) : detailAppend(item, includeRelated)"));
    }

    private static List<String> describe(List<TmdbPerson> people) {
        List<String> out = new ArrayList<>(people.size());
        for (TmdbPerson person : people) {
            out.add(person.getPersonId() + "|" + person.getName() + "|" + person.getSubtitle() + "|" + person.getKnownForDepartment());
        }
        return out;
    }

    private static TmdbConfig config() throws Exception {
        TmdbConfig config = new TmdbConfig();
        for (String[] pair : new String[][]{
                {"apiBase", "https://api.tmdb.org/3"},
                {"apiKey", "test-key"},
                {"language", "zh-CN"}}) {
            Field field = TmdbConfig.class.getDeclaredField(pair[0]);
            field.setAccessible(true);
            field.set(config, pair[1]);
        }
        return config.sanitize();
    }
}
