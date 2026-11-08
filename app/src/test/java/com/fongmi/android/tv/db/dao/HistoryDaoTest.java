package com.fongmi.android.tv.db.dao;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HistoryDaoTest {

    @Test
    public void findReturnsHistoryWithoutTimeOrCountLimits() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "db", "dao", "HistoryDao.java")));
        String compact = source.replaceAll("\\s+", " ");
        String lower = source.toLowerCase(Locale.ROOT);

        assertTrue(compact.contains("SELECT * FROM History WHERE cid = :cid ORDER BY createTime DESC"));
        assertTrue(compact.contains("public abstract List<History> find(int cid);"));
        assertFalse(lower.contains("createtime >= :createtime"));

        // 本条断言必须**只针对 find(int cid) 自己的查询**。
        // 原先是对整个文件做 `lower.contains("limit")`，而 `findBySiteKey` 合法地带
        // `LIMIT :limit`（按站点读历史需要条数上限），于是整文件检查被别的方法击穿，
        // 守卫报出的是假失败，而不是它真正要保护的「find(int) 没有时间/条数上限」。
        // 按方法取查询文本后，断言与测试名重新一致，且新增带 LIMIT 的方法不会再误伤。
        String findQuery = queryOf(source, "public abstract List<History> find(int cid);");
        assertFalse("find(int cid) must not cap the row count",
                findQuery.toLowerCase(Locale.ROOT).contains("limit"));

        // 反向对照：证明上面的按方法取查询确实生效（否则 scoping 写错也会静默全绿）。
        String siteQuery = queryOf(source, "public abstract List<History> findBySiteKey(");
        assertTrue("findBySiteKey legitimately caps its row count",
                siteQuery.toLowerCase(Locale.ROOT).contains("limit"));

        // findBySiteKey 必须用 substr 做精确前缀匹配，**不能**用 LIKE。
        // siteKey 形如 `csp_xxx`，其中 `_` 是 LIKE 的单字符通配符、`%` 是多字符通配符；
        // 一旦改成 LIKE，`csp_a` 会命中 `csp_ab`，且含 `%` 的站点配置会读出别的站点历史。
        // 这是本端点的核心安全决策，因此单独锁定，而不是只断言"查询里有 limit"——
        // 后者在把 substr 变异成 LIKE 之后仍然全绿。
        assertTrue("findBySiteKey must match the site key by exact prefix (substr)",
                siteQuery.contains("substr(`key`, 1, length(:keyPrefix)) = :keyPrefix"));
        assertFalse("findBySiteKey must not use LIKE: `_` / `%` in a site key would wildcard-match",
                siteQuery.toUpperCase(Locale.ROOT).contains("LIKE"));
    }

    /** 取某个方法声明**紧邻其前**的 {@code @Query} 注解文本。 */
    private static String queryOf(String source, String signature) {
        int method = source.indexOf(signature);
        assertTrue("missing method: " + signature, method >= 0);
        int query = source.lastIndexOf("@Query(", method);
        assertTrue("missing @Query for: " + signature, query >= 0);
        int end = source.indexOf("\")", query);
        assertTrue("unterminated @Query for: " + signature, end > query);
        return source.substring(query, end);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path mainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
