package com.fongmi.android.tv.chaquo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 恢复备份曾让进程直接崩溃：
 *
 * <pre>
 * NoClassDefFoundError: com.fongmi.android.tv.api.loader.BaseLoader$Loader
 *   &lt;- ExceptionInInitializerError
 *   &lt;- IllegalStateException: Python already started
 *      at com.fongmi.chaquo.Loader.&lt;init&gt;
 * </pre>
 *
 * 原因是 {@code Loader} 的构造用 {@code if (!Python.isStarted()) Python.start(...)} 做
 * check-then-act：S9 把字幕 ProviderRegistry 放到 App 启动的后台线程预热，于是字幕侧
 * （PythonSubtitleScriptRuntime）与点播配置侧（PyLoader / JsLoader）会并发构造 Loader。
 * 谁先进入判断、谁后调用 {@code Python.start()}，后者就抛 IllegalStateException；
 * 而 {@code BaseLoader$Loader} 的静态初始化抛错后会被永久标记为失败，之后每次
 * {@code BaseLoader.get()} 都是 NoClassDefFoundError。
 *
 * 这个测试锁住「启动 CPython 只走一个类级同步入口且容忍并发败者」的形状。
 */
public class PythonStartIdempotenceSourceTest {

    @Test
    public void constructorStartsPythonThroughTheGuardedEntryPoint() throws Exception {
        String source = readSource();
        String constructor = methodBody(source, "public Loader()");
        assertTrue("the constructor must delegate to the guarded starter",
                constructor.contains("startPythonOnce();"));
        assertFalse("the constructor must not call Python.start() directly",
                constructor.contains("Python.start("));
        assertFalse("the unguarded check-then-act must not come back",
                constructor.contains("if (!Python.isStarted()) Python.start("));
    }

    @Test
    public void theStarterIsSynchronizedAndToleratesALostRace() throws Exception {
        String source = readSource();
        String starter = methodBody(source, "private static synchronized void startPythonOnce()");
        assertTrue("the starter must be a class-level synchronized gate",
                source.contains("private static synchronized void startPythonOnce()"));
        assertTrue("the starter must check whether python is already up",
                starter.contains("Python.isStarted()"));
        assertTrue("losing the race must be caught instead of propagating",
                starter.contains("catch (IllegalStateException"));
        assertTrue("the catcher must verify the runtime is really up before swallowing",
                starter.contains("if (!Python.isStarted()) throw alreadyStarted;"));
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("missing method: " + signature, start >= 0);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(start, i + 1);
            }
        }
        throw new AssertionError("unbalanced braces for: " + signature);
    }

    /**
     * The unit test's working directory is the {@code app} module, so the chaquo module is a
     * sibling reached through {@code ..}; the root-relative form is kept as a fallback for
     * runners that start from the repository root.
     */
    private static String readSource() throws Exception {
        Path moduleRelative = Path.of("src", "main", "java", "com", "fongmi", "chaquo", "Loader.java");
        Path[] candidates = {
                Path.of("..", "chaquo").resolve(moduleRelative),
                Path.of("chaquo").resolve(moduleRelative),
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) return Files.readString(candidate, StandardCharsets.UTF_8);
        }
        throw new java.nio.file.NoSuchFileException("chaquo Loader.java not found from " + Path.of("").toAbsolutePath());
    }
}
