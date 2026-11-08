package com.fongmi.android.tv.node;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 锁定 {@link NodeRuntime#isServing(String)} 的存活语义：只比对 servingUrl 是不够的——
 * timeout() 会把 running 置 false、port 置 0，:node 子进程被杀时 running 还停在 true。
 * 旧实现两种情况下都返回 true，于是 CatSource.reuse 会给出 http://127.0.0.1:0/config 这种无效地址。
 *
 * <p>进程存活那一半在本 JVM 里无法制造（serviceAlive() 拿不到 App 就 fail-open 返回 true），
 * 因此运行时用例覆盖 running/port/servingUrl 三个真实字段，serviceAlive() 的参与用源码契约固定。
 */
public class NodeRuntimeServingTest {

    private static final String URL = "http://127.0.0.1:9988/cat/index.js";

    @After
    public void resetRuntimeState() throws Exception {
        set("servingUrl", "");
        set("running", false);
        set("port", 0);
    }

    @Test
    public void servingRequiresMatchingUrlAndLiveRuntime() throws Exception {
        set("servingUrl", URL);
        set("running", true);
        set("port", 9988);
        assertTrue("同一 bundle 且运行时存活时必须返回 true", NodeRuntime.isServing(URL));

        set("port", 0);
        assertFalse("timeout() 之后 port 被清 0，必须判定为不可复用", NodeRuntime.isServing(URL));

        set("port", 9988);
        set("running", false);
        assertFalse("运行时已被标记停止时必须判定为不可复用", NodeRuntime.isServing(URL));

        set("running", true);
        set("servingUrl", "");
        assertFalse("没有服务中的 bundle 时必须判定为不可复用", NodeRuntime.isServing(URL));

        set("servingUrl", "http://127.0.0.1:9988/cat/other.js");
        assertFalse("换了 bundle 必须判定为不可复用", NodeRuntime.isServing(URL));
    }

    @Test
    public void isServingProbesProcessLivenessWithoutBlocking() {
        String source = read("com/fongmi/android/tv/node/NodeRuntime.java");
        int start = source.indexOf("public static boolean isServing(String url)");
        int end = source.indexOf("private static boolean same(", start);
        assertTrue("必须能定位 isServing", start > 0 && end > start);
        String body = source.substring(start, end);
        assertTrue("isServing 必须复用 serviceAlive() 探进程", body.contains("serviceAlive()"));
        assertTrue("isServing 必须校验 port > 0", body.contains("port <= 0"));
        assertTrue("isServing 必须校验 running", body.contains("!running"));
        assertTrue("isServing 不得引入阻塞等待", !body.contains("sleep") && !body.contains("await") && !body.contains("wait("));
    }

    private static void set(String name, Object value) throws Exception {
        Field field = NodeRuntime.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static String read(String relative) {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(Paths.get("app", "src", "main", "java")).resolve(relative.replace('/', java.io.File.separatorChar));
            if (Files.isRegularFile(candidate)) {
                try {
                    return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
                } catch (java.io.IOException e) {
                    throw new AssertionError("无法读取 " + candidate, e);
                }
            }
        }
        throw new IllegalStateException("app/src/main/java 下找不到 " + relative);
    }
}
