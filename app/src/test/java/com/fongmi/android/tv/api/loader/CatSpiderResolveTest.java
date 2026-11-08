package com.fongmi.android.tv.api.loader;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 猫源请求地址重建的契约：只有<b>本机回环 api</b> 才跟随 Node 运行时当前端口重建，
 * 远端（非本机）T4 猫源 api 必须原样请求。
 *
 * <p>背景：站点 api 在配置加载时被 rebase 成 {@code http://127.0.0.1:<port>/spider/...}
 * 并驻留内存。:node 子进程崩溃自动重启后端口可能变化，固化的旧端口会一直白等超时。
 * 但远端猫源（如 {@code https://…/spider/…}）与本地 bundle 的 api 形状相同——若重建逻辑
 * 只看“是否含 /spider/ + 本地端口非 0”，本地 node 在跑时远端站点会被错误改写到 127.0.0.1，
 * 表现为“切换订阅后远端源全部失效”。
 *
 * <p>resolve 依赖 NodeRuntime/OkHttp 等运行时，单测不易直接构造；这里钉死它的地址判定内核
 * {@link CatSpider#isLoopbackBase(String)}（包内可见），覆盖远端/回环/userinfo/无 host 各形态。
 */
public class CatSpiderResolveTest {

    private static final String[] REMOTE_BASES = {
            "https://catpaw.example.me",
            "https://user:pass@catpaw.example.me",
            "http://192.168.50.10:9988",
            "https://example.me:8443",
    };

    private static final String[] LOOPBACK_BASES = {
            "http://127.0.0.1:9988",
            "http://localhost:9988",
            "http://127.0.0.1",
    };

    @Test
    public void remoteBasesAreNotLoopback() {
        for (String base : REMOTE_BASES) {
            assertFalse("远端 base 不得被判定为本机：" + base, CatSpider.isLoopbackBase(base));
        }
    }

    @Test
    public void loopbackBasesAreLoopback() {
        for (String base : LOOPBACK_BASES) {
            assertTrue("回环 base 必须判定为本机：" + base, CatSpider.isLoopbackBase(base));
        }
    }

    @Test
    public void userinfoDoesNotHideHost() {
        // userinfo 里带 @ 和 : 时不能把凭据段误当 host——那会把远端站点误判成本机。
        assertFalse(CatSpider.isLoopbackBase("https://user:pass@127.0.0.1@evil.example.me"));
        assertTrue("userinfo 含 127.0.0.1 文本但 host 是远端域名时仍应判远端（lastIndexOf@ 取真 host）",
                CatSpider.isLoopbackBase("https://pass@127.0.0.1"));
    }

    @Test
    public void patternOnlyAcceptsSpiderRoutes() {
        // BUNDLE_BASE 的形状契约：api 形如 [scheme://host]/spider/<site>/<n>[/...]，方法名接在后面。
        assertTrue(CatSpider.BUNDLE_BASE.matcher("http://127.0.0.1:9988/spider/douban/3").matches());
        assertTrue(CatSpider.BUNDLE_BASE.matcher("https://user:pass@catpaw.example.me/spider/muou/3").matches());
        assertTrue(CatSpider.BUNDLE_BASE.matcher("/spider/muou/3").matches());
        assertFalse("非 /spider/ 路由不参与重建", CatSpider.BUNDLE_BASE.matcher("http://127.0.0.1:9988/config").matches());
        assertFalse("缺站点段不参与重建", CatSpider.BUNDLE_BASE.matcher("http://127.0.0.1:9988/spider/").matches());
    }

    @Test
    public void matchesRequiresAbsoluteHttp() {
        assertTrue(CatSpider.matches("https://user:pass@catpaw.example.me/spider/muou/3"));
        assertTrue(CatSpider.matches("http://127.0.0.1:9988/spider/douban/3"));
        assertFalse("相对路径不匹配", CatSpider.matches("/spider/muou/3"));
        assertFalse("空串不匹配", CatSpider.matches(""));
    }

    @Test
    public void apiTrailingSlashStripped() {
        assertEquals("构造时去掉尾部斜杠，避免拼接出双斜杠路由",
                "http://127.0.0.1:9988/spider/douban/3", new CatSpider("http://127.0.0.1:9988/spider/douban/3/").apiForTest());
        assertEquals("http://127.0.0.1:9988/spider/douban/3", new CatSpider("http://127.0.0.1:9988/spider/douban/3").apiForTest());
    }
}
