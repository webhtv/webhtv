package com.fongmi.android.tv.ui.adapter;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 「列表数量变化 → 状态机」的收敛契约。
 *
 * <p><b>为什么需要它。</b>本仓库反复出现过同一类缺陷（共 5 处，跨电视端与手机端）：
 * 删除/清空之后宿主忘了驱动状态机，于是列表空了但页面**留白**——连空态都不出现。
 * 逐页发现是不可靠的，所以把这件事从"每个调用点都要记得写"改成"适配器统一通知"：
 * {@code BaseDiffAdapter} 在 diff 应用完成后回调 {@link BaseDiffAdapter.OnCountChanged}，
 * 宿主只注册一次。
 *
 * <p>本契约钉住三件事：
 * <ol>
 *   <li>钩子必须从**两个** {@code setItems} 重载都会触发（少一个就仍有遗漏路径）；</li>
 *   <li>触发时机必须在 diff **应用之后**（{@code AsyncListDiffer} 是异步的，
 *       提前读 {@code getItemCount()} 会拿到旧值）；</li>
 *   <li>宿主必须注册钩子，且**不得再手写** {@code showContent(true, ...)}——
 *       手写正是此前漏掉"删除全部"那条路径的原因。</li>
 * </ol>
 */
public class ListStateConvergenceContractTest {

    private static final String BASE = "app/src/main/java/com/fongmi/android/tv/ui/adapter/BaseDiffAdapter.java";

    /** 已接入该收敛的宿主：源文件（相对 app/src）。 */
    private static final String[] HOSTS = {
            "app/src/leanback/java/com/fongmi/android/tv/ui/activity/KeepActivity.java",
            "app/src/leanback/java/com/fongmi/android/tv/ui/activity/HistoryActivity.java",
            "app/src/mobile/java/com/fongmi/android/tv/ui/activity/KeepActivity.java",
            "app/src/mobile/java/com/fongmi/android/tv/ui/activity/HistoryActivity.java",
    };

    @Test
    public void baseAdapterExposesACountChangedHook() throws Exception {
        String source = read(BASE);
        assertTrue("必须声明 OnCountChanged", source.contains("interface OnCountChanged"));
        assertTrue("必须提供注册入口", source.contains("public void setOnCountChanged(OnCountChanged listener)"));
        assertTrue("必须有一个统一的收尾方法", source.contains("private void afterUpdate(Runnable runnable)"));
    }

    /**
     * 两个 setItems 重载都必须经过 afterUpdate：漏掉任何一个就仍有"改了列表但状态没跟上"的路径。
     */
    @Test
    public void bothSetItemsOverloadsFireTheHook() throws Exception {
        String source = read(BASE);
        int calls = count(source, "afterUpdate(");
        // 一处定义 + 两处调用（Runnable 重载、Callback 重载的两个分支）
        assertTrue("两个 setItems 重载都必须触发钩子，实测 afterUpdate 出现 " + calls + " 次", calls >= 4);
        assertTrue("Runnable 重载必须在 diff 回调里触发",
                source.contains("differ.submitList(Objects.requireNonNullElseGet(items, ArrayList::new), () -> afterUpdate(runnable))"));
        assertTrue("Callback 重载的变化分支必须在 diff 回调里触发",
                source.contains("differ.submitList(newItems, () -> {"));
        assertTrue("Callback 重载的无变化分支也要触发", source.contains("afterUpdate(null);"));
        // 钩子必须在回调里读数量，不能提前读。
        assertFalse("不得在 submitList 之前读取数量", source.contains("int count = getItemCount();"));
    }

    @Test
    public void hostsRegisterTheHookOnceAndNoLongerHandRollStateDriving() throws Exception {
        for (String host : HOSTS) {
            String source = withoutJavaComments(read(host));
            assertTrue(host + " 必须注册 OnCountChanged 钩子",
                    source.contains("mAdapter.setOnCountChanged(count -> mBinding.progressLayout.showContent(true, count))"));
            // 唯一允许出现 showContent(true, ...) 的地方就是那行注册。
            assertEquals(host + " 不得再手写状态驱动（只允许注册钩子那一处）",
                    1, count(source, "showContent(true,"));
        }
    }

    /**
     * "删除全部"这条路径尤其容易漏：它此前是直接 {@code mAdapter.clear()}。
     * 现在它靠钩子自动驱动，宿主侧不应再有额外调用。
     */
    @Test
    public void deleteAllReliesOnTheHookNotOnHandWrittenCalls() throws Exception {
        for (String host : HOSTS) {
            String source = withoutJavaComments(read(host));
            int at = source.indexOf("mAdapter.clear()");
            if (at < 0) continue;
            // clear() 之后紧跟的应当是 lambda 收尾，而不是一串状态驱动语句。
            String after = source.substring(at, Math.min(source.length(), at + 120));
            assertFalse(host + " 的 mAdapter.clear() 之后不得再手写状态驱动", after.contains("showContent(true,"));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static int count(String source, String needle) {
        Matcher matcher = Pattern.compile(Pattern.quote(needle)).matcher(source);
        int total = 0;
        while (matcher.find()) total++;
        return total;
    }

    private static String withoutJavaComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
