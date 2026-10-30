package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.github.catvod.crawler.SpiderDebug;
import com.whl.quickjs.android.QuickJSLoader;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * QuickJS 插件运行时：每个插件一个独立 JS 上下文，只暴露 `globalThis.webhtv` 对象。
 *
 * <p><b>为什么新写而不复用 {@code quickjs.crawler.Spider}。</b>那个类继承
 * {@code com.github.catvod.crawler.Spider}，方法全是爬虫语义（`homeContent`/`categoryContent`/
 * `detailContent`/`playerContent`），并强制加载 `js/lib/spider.js` 与 `__JS_SPIDER__` 全局。
 * 插件需要的是"事件 + 动作"模型，与爬虫契约不匹配；硬套会让插件作者必须实现一堆用不到的
 * 爬虫方法，也会让宿主的权限门禁找不到对应的调用点。
 *
 * <p><b>线程模型。</b>QuickJS 上下文不是线程安全的，跨线程求值会直接崩溃。因此每个插件
 * 绑一个单线程执行器，上下文只在该线程创建与使用，调用经 {@link Session#withLock} 串行进入
 * 并带超时——死循环脚本会被取消并作废整个会话，不能拖住宿主。
 *
 * <p><b>脚本契约（同步优先）。</b>插件通过 `webhtv.on(event, fn)` 与
 * `webhtv.action(name, fn)` 注册处理器，返回值直接回传。QuickJS wrapper 没有内建
 * Promise 事件循环，因此**处理器必须是同步函数**；返回 Promise 会被当作不支持而不是
 * 静默等待。这条约束写在 `assets/plugin/README.md` 里，属于诚实的能力边界，不是疏漏。
 */
public final class QuickJsPluginRuntime implements PluginRuntime {

    private static final String TAG = "plugin-js";
    /** 单次脚本调用超时。超时即作废会话，避免死循环持续占用执行器。 */
    private static final long CALL_TIMEOUT_MS = 5_000L;

    /**
     * 插件引导脚本：建立 `webhtv` 宿主对象与订阅表。
     *
     * <p>刻意保持极小：不含任何网络、文件、反射能力。插件想要能力必须由宿主在后续切片
     * 显式注入并受 {@link PluginCapabilityRegistry} 门禁，不能靠 JS 侧自行获得。
     */
    private static final String BOOTSTRAP = """
            (function(){
              if(globalThis.webhtv) return;
              const handlers={};
              globalThis.webhtv={
                version:1,
                on:function(event,fn){ if(typeof event==='string'&&typeof fn==='function') handlers[event]=fn; },
                action:function(name,fn){ if(typeof name==='string'&&typeof fn==='function') handlers['action:'+name]=fn; },
                log:function(){ try{ console.log('[plugin] '+Array.prototype.slice.call(arguments).join(' ')); }catch(e){} },
                __dispatch:function(event,payload){
                  const fn=handlers[event];
                  if(typeof fn!=='function') return null;
                  const value=fn(payload);
                  return (value&&typeof value.then==='function')?'__PROMISE_UNSUPPORTED__':value;
                },
                __act:function(name,payload){
                  const fn=handlers['action:'+name];
                  if(typeof fn!=='function') return null;
                  const value=fn(payload);
                  return (value&&typeof value.then==='function')?'__PROMISE_UNSUPPORTED__':value;
                }
              };
            })();
            """;

    private final ConcurrentHashMap<String, Session> sessions;

    /**
     * 原生库是否已加载。
     *
     * <p>`QuickJSLoader.init()` 是**必须显式调用**的：{@code QuickJSContext.create()} 之前
     * 没加载 so 会直接 UnsatisfiedLinkError。既有 {@code quickjs.crawler.Loader} 把这一步
     * 放在构造函数里（`Loader.java:9-11`），这里改为懒加载 + 幂等，
     * 避免"装了插件功能却从不使用"的用户白付一次 .so 加载成本。
     */
    private static volatile boolean nativeLoaded;

    public QuickJsPluginRuntime() {
        this.sessions = new ConcurrentHashMap<>();
    }

    private static synchronized void ensureNativeLoaded() {
        if (nativeLoaded) return;
        QuickJSLoader.init();
        nativeLoaded = true;
    }

    @Override
    public String init(String pluginId, String entry, String config) throws Exception {
        if (isBlank(pluginId)) throw new IllegalArgumentException("pluginId is empty");
        if (isBlank(entry)) throw new IllegalArgumentException("entry is empty");
        ensureNativeLoaded();
        destroy(pluginId);
        Session session = new Session(pluginId);
        sessions.put(pluginId, session);
        try {
            return session.withLock(ignored -> {
                session.context().evaluate(BOOTSTRAP);
                String source = PluginScriptSource.read(entry);
                if (source == null || source.isBlank()) {
                    throw new IllegalStateException("plugin script is empty: " + entry);
                }
                session.context().evaluate(source);
                return "ok";
            });
        } catch (Throwable error) {
            // 初始化失败必须整个丢掉上下文，否则半初始化状态会被后续事件继续踩。
            sessions.remove(pluginId, session);
            session.close();
            throw error;
        }
    }

    @Override
    public String onEvent(String pluginId, String event, String payload) throws Exception {
        return dispatch(pluginId, "__dispatch", event, payload);
    }

    @Override
    public String action(String pluginId, String action, String payload) throws Exception {
        return dispatch(pluginId, "__act", action, payload);
    }

    @Nullable
    private String dispatch(String pluginId, String method, String key, String payload) throws Exception {
        Session session = sessions.get(pluginId);
        if (session == null) throw new IllegalStateException("plugin is not initialized: " + pluginId);
        return session.withLock(ignored -> {
            JSObject api = session.context().getGlobalObject().getJSObject("webhtv");
            if (api == null) return null;
            JSFunction fn = api.getJSFunction(method);
            if (fn == null) return null;
            try {
                Object result = fn.call(key == null ? "" : key, payload == null ? "{}" : payload);
                if (result == null) return null;
                if (result instanceof JSObject object) {
                    // 非 Promise 的普通对象：序列化后回传；Promise 已被引导脚本拦成标记串。
                    return object.stringify();
                }
                return String.valueOf(result);
            } finally {
                fn.release();
            }
        });
    }

    @Override
    public void destroy(String pluginId) {
        if (pluginId == null) return;
        Session session = sessions.remove(pluginId);
        if (session != null) session.close();
    }

    /** 当前活跃的插件上下文数，供诊断。 */
    public int activeSessions() {
        return sessions.size();
    }

    /** 关闭全部上下文。 */
    public void destroyAll() {
        for (String id : sessions.keySet()) destroy(id);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    /**
     * 一个插件的 JS 会话。
     *
     * <p>上下文与执行器必须同生共死：在 A 线程创建却在 B 线程求值会崩，所以两者绑在
     * 同一个对象里，只在 {@link #close()} 拆开。
     */
    private static final class Session {

        private final ExecutorService executor;
        private final String pluginId;
        private volatile QuickJSContext context;
        private volatile boolean closed;

        Session(String pluginId) {
            this.pluginId = pluginId;
            this.executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "plugin-js-" + pluginId);
                thread.setDaemon(true);
                return thread;
            });
        }

        QuickJSContext context() {
            return context;
        }

        /** 在会话线程上执行，带超时。上下文创建也发生在这条线程上。 */
        <T> T withLock(SessionFn<T> fn) throws Exception {
            if (closed) throw new IllegalStateException("plugin session is closed: " + pluginId);
            Future<T> future = executor.submit(() -> {
                if (context == null) {
                    ensureNativeLoaded();
                    QuickJSContext created = QuickJSContext.create();
                    created.setConsole(new DiagnosticConsole());
                    created.setEnableStackTrace(true);
                    context = created;
                }
                return fn.apply(this);
            });
            try {
                return future.get(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                future.cancel(true);
                closed = true;
                SpiderDebug.log(TAG, "call timeout plugin=%s", pluginId);
                throw new IllegalStateException("plugin script timed out after " + CALL_TIMEOUT_MS + "ms");
            }
        }

        void close() {
            closed = true;
            try {
                executor.shutdownNow();
            } catch (Throwable ignored) {
            }
            QuickJSContext current = context;
            context = null;
            if (current != null) {
                try {
                    current.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @FunctionalInterface
    private interface SessionFn<T> {
        T apply(Session session) throws Exception;
    }

    /** 插件 console 输出汇入既有诊断通道，便于与站源脚本日志一起排查。 */
    private static final class DiagnosticConsole implements QuickJSContext.Console {

        @Override
        public void log(String info) {
            SpiderDebug.log(TAG, "%s", info);
        }

        @Override
        public void info(String info) {
            SpiderDebug.log(TAG, "%s", info);
        }

        @Override
        public void warn(String info) {
            SpiderDebug.log(TAG, "warn %s", info);
        }

        @Override
        public void error(String info) {
            SpiderDebug.log(TAG, "error %s", info);
        }
    }
}
