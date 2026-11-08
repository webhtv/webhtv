package com.fongmi.android.tv.model;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 播放页销毁会清掉 ViewModel，而宿主在销毁后仍可能发起请求：
 * 播放页的 onRefresh()/自动选集、短剧队列的下一条预取、阅读器章节切换都会走到
 * {@link SiteViewModel} 的提交入口。此前这些提交会命中 shutdownNow() 后的单线程池并抛
 * {@code RejectedExecutionException: Task rejected from ThreadPoolExecutor [Terminated, ...]}，
 * 异常顺着宿主的 UI 线程冒出去直接打崩 App。
 *
 * <p>因此 onCleared() 之后的提交必须统一降级为「本次请求作废」，而不是把异常留给宿主。</p>
 */
public class SiteViewModelSubmitAfterClearTest {

    @Test
    public void foregroundEntriesAfterClearDoNotThrow() {
        SiteViewModel model = new SiteViewModel();
        model.onCleared();

        model.playerContent("key", "flag", "episode");
        model.playerContent("key", "flag", "episode", 0);
        model.homeContent();
        model.categoryContent("key", "tid", "1", false, null);
        model.detailContent("key", "id");
        model.action("key", "act");
        model.searchContent(List.<com.fongmi.android.tv.bean.Site>of(), "keyword", true);
    }

    @Test
    public void isolatedResolverAfterClearReturnsCancelledFuture() {
        SiteViewModel model = new SiteViewModel();
        model.onCleared();

        ListenableFuture<?> future = model.playerContentIsolated("key", "flag", "episode", 0, null, null);

        assertNotNull(future);
        assertTrue("清理后的隔离取流必须直接作废", future.isCancelled());

        model.playerContentIsolated("key", "flag", "episode", 0, null, null);
        model.cancelPlayerContentIsolated();
        model.stopSearch();
    }

    @Test
    public void lateForegroundRequestAfterClearIsDroppedInsteadOfDelivered() {
        SiteViewModel model = new SiteViewModel();
        model.onCleared();

        model.playerContent("key", "flag", "episode");

        assertNull("已清理的 ViewModel 不应再向播放 LiveData 投递结果", model.getPlayer().getValue());
        assertNull(model.getResult().getValue());
        assertNull(model.getAction().getValue());
    }

    /** 覆盖「检查通过后池才被关闭」的竞态：try/catch 兜底必须生效，异常不得逃逸。 */
    @Test
    public void concurrentSubmitDuringClearNeverPropagatesRejection() throws Exception {
        AtomicReference<Throwable> escaped = new AtomicReference<>();
        for (int round = 0; round < 200 && escaped.get() == null; round++) {
            SiteViewModel model = new SiteViewModel();
            CountDownLatch start = new CountDownLatch(1);
            Thread submitter = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 50; i++) {
                        model.playerContent("key", "flag", "episode");
                        model.playerContentIsolated("key", "flag", "episode", 0, null, null);
                    }
                } catch (Throwable e) {
                    escaped.compareAndSet(null, e);
                }
            }, "submit-during-clear");
            submitter.setDaemon(true);
            submitter.start();
            start.countDown();
            model.onCleared();
            submitter.join(TimeUnit.SECONDS.toMillis(10));
        }
        Throwable error = escaped.get();
        assertNull(error == null ? null : String.valueOf(error) + "\n" + firstFrame(error), error);
    }

    private static String firstFrame(Throwable error) {
        StackTraceElement[] frames = error.getStackTrace();
        return frames.length == 0 ? "" : frames[0].toString();
    }
}
