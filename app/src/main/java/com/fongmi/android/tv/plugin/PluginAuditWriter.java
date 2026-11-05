package com.fongmi.android.tv.plugin;

import android.os.Looper;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 审计日志的落盘调度层：决定"这次写盘是同步做还是交给单线程写入器"，并保证旧快照永不覆盖新快照。
 *
 * <p><b>为什么必须区分线程。</b>这条路径**不**只跑在网络线程上：{@code PluginHost.onEvent}
 * 由 {@code PluginEventBus} 在主线程驱动，插件脚本一抛错就会走到
 * {@code quarantine -> recordFailure -> append}。若在这里同步重写整份文件，那就是
 * StrictMode 磁盘写违规加掉帧，而审计只是观测面，不该拖慢 UI。所以：主线程只把最终内容
 * 排进写入器并立刻返回；网络线程 / JS 线程 / JVM 单测线程保持同步写，
 * "调用返回即已落盘"的既有语义一个字都不变。</p>
 *
 * <p><b>为什么单独成文件。</b>这段代码原先整段长在 {@link PluginAuditLog} 里，与缓冲、序列化
 * 混在一起已经撞上 docs §7:310 的 500 行门禁。调度逻辑本来就自成一体——它只依赖"待写的最终
 * 文本"和落盘后端，不依赖环形缓冲——整段搬走后行为一字不改。</p>
 */
final class PluginAuditWriter {

    /**
     * 落盘后端；{@code null} 表示还没解析过，首次使用时才取 {@link PluginAuditStore#get()}。
     *
     * <p>惰性而不是在静态初始化里直取：JVM 单测没有 {@code Init.context()}，
     * static 初始化抛异常会变成 {@code ExceptionInInitializerError}，把整个测试类打死。</p>
     */
    private static volatile PluginAuditStore store;

    /** 仅测试使用：强制指定"是否走异步写入器"，绕过真实主线程判定。 */
    private static volatile Boolean deferForTest;

    /**
     * 落盘顺序的唯一依据：每次要写盘就 +1，取到编号最大的内容才有资格覆盖文件。
     *
     * <p><b>为什么必须有它。</b>主线程把快照交给写入器就返回，网络线程却仍同步写盘；两者
     * 并发时，"谁先发起"和"谁先写完"不再同序。没有版本号，一个更旧的快照完全可能在新快照
     * 落盘之后才写下去，磁盘上就会永久留下少了最后几条的日志——而那几条往往正是刚发生的
     * 失败。有了版本号，任何较早的写入都发现自己不是最新就整条放弃，旧快照永不覆盖新快照。</p>
     *
     * <p>由调用方（{@link PluginAuditLog#persist()}，仍持有环形缓冲的锁）递增——版本号与它
     * 对应的内容必须取自同一个临界区，否则两个线程可能拿到同一号。</p>
     */
    private long nextVersion;

    /** 版本号与排队状态的短暂临界区；与缓冲锁分开，避免写文件时挡住记账线程。 */
    private final Object writeLock = new Object();

    /** 真正落盘的互斥；只包住 IO 与版本比对，绝不在主线程上被持有。 */
    private final Object ioLock = new Object();

    /** 是否已经给写入器排过一个 drain 任务；避免主线程刷日志时堆出一串空转任务。 */
    private boolean drainScheduled;

    /** 已真正落盘的版本号；写入前比对它，小于等于就弃写。 */
    private long writtenVersion;

    /** 已排队但还没写的最新快照版本与内容；{@code null} 表示没有待写内容。 */
    private long queuedVersion;
    private String queuedText;

    /** 单线程写入器；惰性创建，守护线程，进程退出不被它拖住。 */
    private ExecutorService executor;

    /**
     * 把一份已序列化的快照交给落盘路径；调用方必须仍持有环形缓冲的锁。
     *
     * <p>整段 try/catch：写盘失败只丢持久化，绝不让异常冒到网络请求或事件派发的返回路径上。</p>
     */
    void persist(String text) {
        try {
            long version = ++nextVersion;
            if (isMainThread()) {
                enqueue(version, text);
            } else {
                writeIfNewer(version, text);
            }
        } catch (Throwable ignored) {
            // 见方法注释：落盘是观测面，不能让它的失败影响记账。
        }
    }

    /**
     * 主线程分支：只把"稍后要写的最终内容"放进来就返回，绝不碰文件。
     *
     * <p>同一个快照槽位被后到的更新内容直接覆盖，而不是排队——审计要的是"最终状态收敛到
     * 内存最新快照"，中间态没有保存价值，排队反而会在频繁事件时积压成写放大。</p>
     */
    private void enqueue(long version, String text) {
        synchronized (writeLock) {
            // 更旧的快照已经在磁盘上了，排队也没有意义。
            if (version <= writtenVersion) return;
            if (queuedText != null && queuedVersion > version) return;
            queuedVersion = version;
            queuedText = text;
        }
        scheduleDrain();
    }

    /** 已经在跑就不要再排一个任务，否则主线程刷日志时会堆出一串空转任务。 */
    private void scheduleDrain() {
        synchronized (writeLock) {
            if (drainScheduled) return;
            drainScheduled = true;
        }
        try {
            executor().execute(this::drain);
        } catch (Throwable error) {
            synchronized (writeLock) {
                drainScheduled = false;
                writeLock.notifyAll();
            }
        }
    }

    /** 写入器线程主体：一直写到手头没有待写内容为止，然后放开 {@link #drainScheduled}。 */
    private void drain() {
        try {
            while (true) {
                long version;
                String text;
                synchronized (writeLock) {
                    if (queuedText == null) {
                        drainScheduled = false;
                        writeLock.notifyAll();
                        return;
                    }
                    version = queuedVersion;
                    text = queuedText;
                    queuedText = null;
                }
                writeIfNewer(version, text);
            }
        } catch (Throwable error) {
            synchronized (writeLock) {
                drainScheduled = false;
                writeLock.notifyAll();
            }
        }
    }

    /**
     * 真正落盘的唯一入口：只有比已落盘版本更新的内容才有资格覆盖文件。
     *
     * <p>IO 由 {@link #ioLock} 串行化，版本比对与写入在同一个 ioLock 临界区内完成，
     * 所以"比对通过"与"写完"之间不会有别的线程插进来写一个更旧的版本。</p>
     */
    private void writeIfNewer(long version, String text) {
        synchronized (ioLock) {
            synchronized (writeLock) {
                if (version <= writtenVersion) return;
            }
            try {
                store().write(text);
            } catch (Throwable ignored) {
                // PluginAuditStore 自己已吞异常；这里再兜一层，防测试用的 Sink 直接抛。
            }
            synchronized (writeLock) {
                if (version > writtenVersion) writtenVersion = version;
                writeLock.notifyAll();
            }
        }
    }

    /**
     * 主线程判定，写法与 {@code PluginController.isMainThread} 一致。
     *
     * <p>整体 try/catch 是必需的：JVM 单测里 {@code android.os.Looper} 是桩，调用会抛
     * {@code RuntimeException("Stub!")} 或 {@code NoClassDefFoundError}。那种情况必须当作
     * "非主线程"（同步写），而不是被误判成主线程而丢掉落盘。</p>
     */
    private static boolean isMainThread() {
        Boolean forced = deferForTest;
        if (forced != null) return forced;
        try {
            return Looper.myLooper() == Looper.getMainLooper();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 单线程写入器；守护线程，进程退出时不会被一条待写审计记录拖住。 */
    private ExecutorService executor() {
        synchronized (writeLock) {
            if (executor == null) {
                executor = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "plugin-audit-writer");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            return executor;
        }
    }

    /**
     * 仅测试使用：阻塞到写入器把当前待写内容真正落盘为止。
     *
     * <p>生产代码不需要它——主线程自己在返回前不会去读文件；需要等的是测试里的断言。</p>
     */
    void flush() {
        long deadline = System.currentTimeMillis() + 5000L;
        synchronized (writeLock) {
            while (queuedText != null || drainScheduled) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) return;
                try {
                    writeLock.wait(left);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    static PluginAuditStore store() {
        PluginAuditStore current = store;
        if (current != null) return current;
        synchronized (PluginAuditWriter.class) {
            if (store == null) store = PluginAuditStore.get();
            return store;
        }
    }

    /**
     * 仅测试使用：强制"这次是否走异步写入器"，绕过真实主线程判定。
     *
     * <p>JVM 单测里 {@link #isMainThread()} 恒为假，没有这个入口就永远测不到异步分支。
     * 传 {@code null} 恢复正常判定。</p>
     */
    static void forceAsyncForTest(@Nullable Boolean value) {
        deferForTest = value;
    }

    /** 仅测试使用：替换落盘后端。传 {@code null} 恢复默认实现。 */
    static void storeForTest(@Nullable PluginAuditStore value) {
        synchronized (PluginAuditWriter.class) {
            store = value;
        }
    }
}
