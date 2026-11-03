package com.fongmi.android.tv.plugin;

import androidx.annotation.Nullable;

import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.whl.quickjs.android.QuickJSLoader;
import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
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
 * 绑一个单线程执行器，上下文的 {@code create}/{@code evaluate}/{@code destroy} 一律只在该线程
 * 发生，调用经 {@link Session#withLock} 串行进入并带超时——死循环脚本不能让宿主一起陷进去，
 * 只能作废整个会话（原生 eval 不响应中断，所以不是"取消"而是"作废"）。
 *
 * <p><b>销毁线程不变量。</b>销毁与求值受同一条约束：{@link Session#close()} 不得在调用线程
 * 上销毁上下文，只能把销毁动作投递到会话线程。超时发生时脚本仍在原生求值、仍持有同一个
 * 上下文，跨线程 destroy 就是 use-after-free。</p>
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
    /** 装载期（脚本顶层）发起的请求在审计里的触发者标记，与 {@code PluginHost} 的失败归因同名。 */
    private static final String TRIGGER_INIT = "runtime.init";

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
                request:function(method,url,headers,body,ttlSeconds){
                  const native=globalThis.webhtv.__nativeRequest;
                  if(typeof native!=='function') return {status:0,error:'net.request is not available'};
                  const h=(headers===undefined||headers===null)?null:(typeof headers==='string'?headers:JSON.stringify(headers));
                  const b=(body===undefined||body===null)?null:(typeof body==='string'?body:JSON.stringify(body));
                  const t=(typeof ttlSeconds==='number'||typeof ttlSeconds==='string')?ttlSeconds:null;
                  let raw;
                  try{ raw=native(method===undefined?null:method, url===undefined?null:url, h, b, t); }
                  catch(e){ return {status:0,error:String(e)}; }
                  if(typeof raw!=='string') return (raw===undefined||raw===null)?{status:0,error:'empty response'}:raw;
                  try{ return JSON.parse(raw); }catch(e){ return {status:0,error:String(raw)}; }
                },
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
        Session session = new Session(pluginId, sessions);
        sessions.put(pluginId, session);
        try {
            return session.withLock(ignored -> {
                // 插件脚本顶层就可能调 webhtv.request：这一段触发者是「装载」，审计据此区分启动期请求。
                session.enterTrigger(TRIGGER_INIT);
                try {
                    session.context().evaluate(BOOTSTRAP);
                    inject(pluginId, session, config);
                    String source = PluginScriptSource.read(entry);
                    if (source == null || source.isBlank()) {
                        throw new IllegalStateException("plugin script is empty: " + entry);
                    }
                    session.context().evaluate(source);
                    return "ok";
                } finally {
                    session.exitTrigger();
                }
            });
        } catch (Throwable error) {
            // 初始化失败必须整个丢掉上下文，否则半初始化状态会被后续事件继续踩。
            sessions.remove(pluginId, session);
            session.close();
            throw error;
        }
    }

    /**
     * 把能力注入刚建好的 JS 会话：{@code webhtv.config} 与 {@code webhtv.request}。
     *
     * <p><b>为什么注入发生在引导脚本之后、插件脚本之前。</b>插件脚本顶层就可能读
     * {@code webhtv.config}（比如启动时校验 token 是否填了），晚一步注入就恒读到 undefined。
     *
     * <p><b>{@code config} 用 {@code JSON.parse} 走一遍而不是直接赋字符串。</b>插件作者写
     * {@code webhtv.config.DOUBAN_TOKEN} 比 {@code JSON.parse(webhtv.config).DOUBAN_TOKEN} 自然；
     * 转义交给 Gson 生成的字面量，避免手拼时被引号咬。
     *
     * <p><b>{@code request} 的权限在注入时就判定完。</b>没给 {@code net.request} 的插件连
     * {@code __nativeRequest} 都拿到的是"恒拒绝"的实现——门禁在宿主侧，脚本改不了。
     */
    private static void inject(String pluginId, Session session, @Nullable String config) {
        QuickJSContext context = session.context();
        JSObject webhtv = context.getGlobalObject().getJSObject("webhtv");
        if (webhtv == null) throw new IllegalStateException("bootstrap did not create webhtv");
        try {
            JsonObject params = PluginConfig.paramsOf(config);
            // 用 JsonPrimitive 序列化字符串字面量：引号/反斜杠/换行都被正确转义，不会破坏 evaluate。
            context.evaluate("globalThis.webhtv.config = JSON.parse(" + new JsonPrimitive(params.toString()) + ");");
            Set<String> permissions = PluginConfig.permissionsOf(config);
            int timeout = PluginConfig.timeoutOf(config, PluginNetBridge.DEFAULT_TIMEOUT_SECONDS);
            // 版本只用于审计归因：注入时读一次并闭包捕获，避免每次请求都重新解析信封。
            String version = PluginConfig.versionOf(config);
            boolean granted = PluginCapabilityRegistry.allows(permissions,
                    PluginCapabilityRegistry.PERMISSION_NET_REQUEST);
            // session 也传下去：请求条目要把「触发这次请求的事件/动作」记进审计（docs §8.1:330）。
            JSCallFunction bridge = args -> netRequest(session, pluginId, version, granted, args, timeout);
            webhtv.setProperty("__nativeRequest", bridge);
        } finally {
            webhtv.release();
        }
    }

    /**
     * {@code webhtv.request} 的 Java 侧实现：参数全部容忍 {@code null}，绝不上抛。
     *
     * @param pluginVersion 信封里的插件版本，透传给审计；{@code null} 表示信封没写
     * @param session 承载「触发事件」的会话；审计用它回答"插件在响应什么的时候发出的请求"
     */
    private static String netRequest(Session session, String pluginId, @Nullable String pluginVersion,
            boolean granted, @Nullable Object[] args, int timeout) {
        String method = arg(args, 0);
        String url = arg(args, 1);
        // 触发者由 dispatch/init 在会话线程写入，这里在同一条线程同步读——脚本调 request 是同步的。
        String event = session.trigger();
        if (!granted) {
            // 缺权限记 blocked 审计后返回错误对象：脚本能看见失败，但插件不会被判为故障。
            return PluginNetBridge.blocked(pluginId, pluginVersion, event, method, url,
                    "missing permission " + PluginCapabilityRegistry.PERMISSION_NET_REQUEST);
        }
        int ttlSeconds = PluginHttpCache.ttlSeconds(arg(args, 4));
        return PluginNetBridge.request(pluginId, pluginVersion, event, method, url, arg(args, 2), arg(args, 3),
                timeout, ttlSeconds);
    }

    @Nullable
    private static String arg(@Nullable Object[] args, int index) {
        if (args == null || index < 0 || index >= args.length) return null;
        Object value = args[index];
        return value == null ? null : String.valueOf(value);
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
            // 处理器执行期间脚本可能调 webhtv.request：把触发者记进会话，外发请求因此可归因。
            session.enterTrigger(key);
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
                session.exitTrigger();
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
     *
     * <p>会话还持有自己所在的 {@code sessions} 表：超时是可以从任何调用点（{@code init}、
     * {@code onEvent}、{@code action}）发生的，若只有调用方负责摘除，每个调用点都得重写一遍
     * 同样的清理，漏一处就留下一条"表里还在、上下文已死"的僵尸会话。把摘除交给 {@link #close()}，
     * 所有路径（超时、{@code destroy}、初始化失败）自动一致。
     */
    private static final class Session {

        private final ConcurrentHashMap<String, Session> owner;
        private final ExecutorService executor;
        private final String pluginId;
        private volatile QuickJSContext context;
        private volatile boolean closed;
        /**
         * 当前正在执行的触发者（事件名或动作名），仅供审计给外发请求归因。
         *
         * <p>只在会话线程读写：求值经 {@link #withLock} 串行，{@code dispatch}/初始化在会话线程写，
         * 网络桥也是脚本同步调用、在同一条线程读。故意不加 {@code volatile}——跨线程可见性反而
         * 会让一条请求读到别的调用留下的触发者。
         */
        private String trigger;

        Session(String pluginId, ConcurrentHashMap<String, Session> owner) {
            this.pluginId = pluginId;
            this.owner = owner;
            this.executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "plugin-js-" + pluginId);
                thread.setDaemon(true);
                return thread;
            });
        }

        QuickJSContext context() {
            return context;
        }

        @Nullable
        String trigger() {
            return trigger;
        }

        void enterTrigger(@Nullable String value) {
            trigger = value;
        }

        void exitTrigger() {
            trigger = null;
        }

        /** 在会话线程上执行，带超时。上下文创建与销毁都发生在这条线程上。 */
        <T> T withLock(SessionFn<T> fn) throws Exception {
            if (closed) throw new IllegalStateException("plugin session is closed: " + pluginId);
            Future<T> future;
            try {
                future = executor.submit(() -> {
                    try {
                        if (context == null) {
                            ensureNativeLoaded();
                            QuickJSContext created = QuickJSContext.create();
                            created.setConsole(new DiagnosticConsole());
                            created.setEnableStackTrace(true);
                            context = created;
                            // 竞态窗口：超时发生在上下文创建途中时，close() 看到的 context 还是 null，
                            // 它投递的销毁任务也要等这条线程空出来才轮得到执行。这里复查 closed 后
                            // 直接抛出即可——销毁统一交给下面的 finally，不再自己 destroy 一次，
                            // 否则同一条销毁路径写两遍就有二次销毁的风险。
                            if (closed) throw new IllegalStateException("plugin session is closed: " + pluginId);
                        }
                        return fn.apply(this);
                    } finally {
                        // 会话线程侧的统一收尾：close() 一旦置位，本任务体无论正常返回还是抛异常，
                        // 上下文都在这里（会话线程上）销毁。超时后脚本自己跑完、以及 close() 时线程
                        // 正忙，两种情况都由这句收住。
                        if (closed) destroyOnSessionThread();
                    }
                });
            } catch (RejectedExecutionException rejected) {
                // 执行器已关闭 = 会话已作废：销毁责任在会话线程侧（任务体 finally 或 close() 投递的
                // 销毁任务），这里没有可回收的资源，只需把契约异常原样转出去。
                throw new IllegalStateException("plugin session is closed: " + pluginId);
            }
            try {
                return future.get(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                // 只 future.cancel(true) 是不够的：它给这条线程发中断，而原生求值（QuickJS 的
                // eval）全程不检查中断标志，脚本线程可以永远卡在里面并继续持有上下文与执行器。
                // 唯一能真正作废这对资源的是 close()（从 sessions 摘除 + 把销毁投递到会话线程 +
                // shutdown 执行器），因此超时直接走它，让故障会话整体作废而不是半死不活。
                // 但 close() 绝不能在**本线程**上销毁上下文：这条线程不是会话线程，而会话线程此刻
                // 仍在原生求值并持有同一个 QuickJSContext，在这里 destroy() 就是跨线程
                // use-after-free（SIGABRT 级崩溃）。销毁一律由会话线程自己执行。
                close();
                SpiderDebug.log(TAG, "call timeout plugin=%s", pluginId);
                throw new IllegalStateException("plugin script timed out after " + CALL_TIMEOUT_MS + "ms");
            }
        }

        /**
         * 作废会话：摘表 + 把销毁投递到会话线程 + 关执行器，**绝不在调用线程上销毁上下文**。
         *
         * <p>可重入且并发安全：{@code synchronized} 判定保证只有第一个调用者做摘表与投递，
         * 后续调用直接返回。
         *
         * <p><b>为什么销毁必须投递到会话线程。</b>超时的语义是"脚本仍在那条单线程上跑原生
         * eval"，该线程此刻仍持有同一个 {@link QuickJSContext}。若在调用线程 destroy()，销毁
         * 与原生求值会争用同一份上下文内存，即 use-after-free——QuickJS 没有跨线程保护，进程
         * 直接 SIGABRT。销毁只能发生在会话线程：任务体的 finally（脚本自己跑完时），或这里
         * 投递的销毁任务（线程随后空闲时被调度）；两者被同一个单线程串行化，不会重叠。
         */
        void close() {
            synchronized (this) {
                if (closed) return;
                closed = true;
            }
            // 两参 remove：只摘掉"表里确实是我"这一条，避免并发情况下误删同 id 的新会话。
            owner.remove(pluginId, this);
            try {
                // 这里只是投递，不在本线程执行；与任务体 finally 的第二次调用靠 destroyOnSessionThread()
                // 的幂等兜住。
                executor.execute(this::destroyOnSessionThread);
            } catch (RejectedExecutionException rejected) {
                // 执行器已关闭说明会话线程侧早已收尾并销毁过上下文，销毁责任不在本线程，静默即可。
            }
            // shutdown() 而不是 shutdownNow()：上面投递的销毁任务必须还能跑，不能在队列里被丢掉。
            try {
                executor.shutdown();
            } catch (Throwable ignored) {
            }
        }

        /**
         * 销毁上下文。**只允许在会话线程调用**：{@link QuickJSContext#destroy()} 与原生求值
         * 使用同一份上下文状态，跨线程调用会与仍在执行的 eval 冲突并崩溃。
         *
         * <p>幂等：只有第一次调用真正销毁，后续因 {@code context} 已置空而空转。读字段与置空
         * 都在会话线程上，单线程执行即可，不需要额外同步。
         */
        private void destroyOnSessionThread() {
            QuickJSContext current = context;
            context = null;
            if (current == null) return;
            try {
                current.destroy();
            } catch (Throwable ignored) {
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
