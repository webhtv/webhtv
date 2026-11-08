package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 一键同步的「陈旧回调」守卫：每次同步开始换一个代际，异步回调执行前比对代际。
 *
 * <p>为什么需要这个守卫：取消/失败/成功都是 OkHttp 线程 → {@code App.post} 的异步投递。
 * 用户在上一条 post 尚未执行时就重新点「开始同步」，旧回调会把**新一次同步**的界面复位：
 * 进度清零、按钮文案回「开始同步」、方向选择器被重新启用（{@code setSyncing(false)} 里三件事）。
 * 请求方向本身已在 {@code startSync()} 入口快照，所以这不是数据错误，而是界面与真实请求
 * 不一致——正是这类「只错显示」的竞态最容易被后续重构悄悄删掉守卫，因此单独锁死。
 */
public class OneKeySyncGenerationGuardTest {

    private static final Path DIALOG = Path.of("app/src/main/java/com/fongmi/android/tv/ui/dialog/OneKeySyncDialog.java");

    @Test
    public void startSyncMintsGeneration() throws Exception {
        String body = methodBody(code(DIALOG), "private void startSync()");
        assertTrue("每次开始同步都必须换一个代际（++syncGeneration）", body.contains("++syncGeneration"));
    }

    @Test
    public void everyPostedResetComparesGeneration() throws Exception {
        String source = code(DIALOG);
        List<String> resets = new ArrayList<>();
        List<String> guarded = new ArrayList<>();
        for (String block : postedBlocks(source)) {
            if (!block.contains("setSyncing(false)")) continue;
            resets.add(block);
            if (block.contains("generation != syncGeneration")) guarded.add(block);
        }
        // 固定为4处：startSync 的异常兜底、retry 的失败收尾、callback 的取消与成功。
        // 增删任一处都必须回来复核代际守卫，因此这里锁数量而不是只锁「有守卫」。
        assertEquals("异步复位点数量变化时必须显式复核代际守卫", 4, resets.size());
        assertEquals("每一个异步复位点都必须先比对代际", resets.size(), guarded.size());
        // postedBlocks 只收花括号形式的块体，所以再加一道「App.post 总数」锁：
        // 若有人用单表达式形式（App.post(() -> setSyncing(false))）新增复位点，上面的 4 不变，
        // 这条计数会变红，迫使作者回来补守卫。
        assertEquals("App.post 出现次数变化时必须复核是否新增了未守卫的异步复位点",
                8, countOf(source, "App.post("));
    }

    @Test
    public void cancelledPreparationStopsArchiving() throws Exception {
        String body = methodBody(code(DIALOG), "private void startSync()");
        assertTrue("打包的取消信号必须同时判代际：只判 syncing 时，取消后立刻重开的陈旧线程会继续打包",
                body.contains("() -> syncing && generation == syncGeneration"));
    }

    @Test
    public void retryChainCarriesGeneration() throws Exception {
        String source = code(DIALOG);
        String retry = methodBody(source, "private void retry(");
        assertTrue("重试分支必须先比对代际再决定是否收尾", retry.contains("generation != syncGeneration"));
        assertTrue("重试必须沿用同一个代际", retry.contains("request(url, body, archive, mpvArchive, loginArchive, retry + 1, generation)"));
        assertTrue("回调必须持有代际", methodBody(source, "private Callback callback(").contains("generation"));
    }

    @Test
    public void temporaryArchivesAreDeletedRegardlessOfGeneration() throws Exception {
        String response = methodBody(code(DIALOG), "public void onResponse(");
        int guard = response.indexOf("generation != syncGeneration");
        assertTrue("成功路径必须比对代际", guard >= 0);
        // 三个临时归档都要在代际判断之前删：陈旧回调同样得清理它这一轮产生的文件。
        // 只锚定最后一个（loginArchive）会漏掉主归档：删掉 archive.delete() 仍然全绿。
        for (String delete : new String[]{"archive.delete()", "mpvArchive.delete()", "loginArchive.delete()"}) {
            int at = response.indexOf(delete);
            assertTrue("成功路径必须清理临时归档：" + delete, at >= 0);
            assertTrue("归档清理必须发生在代际判断之前：" + delete, at < guard);
        }
    }

    @Test
    public void staleGenerationNeverIssuesARealRequest() throws Exception {
        String source = code(DIALOG);
        String startSync = methodBody(source, "private void startSync()");
        int guard = startSync.indexOf("!syncing || generation != syncGeneration");
        int request = startSync.indexOf("request(url, body, archive, mpvArchive, loginArchive, 0, generation)");
        assertTrue("发请求前必须同时判 syncing 与代际：只判 syncing 会让取消后立刻重开的陈旧准备线程真发一次同步", guard >= 0);
        assertTrue("请求调用必须存在", request >= 0);
        assertTrue("代际判断必须发生在发请求之前", guard < request);

        String retry = methodBody(source, "private void retry(");
        int schedule = retry.indexOf("Task.schedule(");
        assertTrue("延迟重试必须用 Task.schedule", schedule >= 0);
        assertTrue("延迟重试的 lambda 内部也要复核代际，否则会与新同步并发并劫持 syncCall",
                retry.indexOf("generation != syncGeneration", schedule) > schedule);
    }

    @Test
    public void progressCallbacksCarryGeneration() throws Exception {
        String source = code(DIALOG);
        for (String signature : new String[]{"private void updatePrepare(", "private void updatePrepareProgress(", "private void updateUpload("}) {
            assertTrue(signature + " 必须按代际过滤，否则旧同步的进度会写进新同步的进度条",
                    methodBody(source, signature).contains("generation != syncGeneration"));
        }
    }

    @Test
    public void destroyInvalidatesInFlightCallbacks() throws Exception {
        String body = methodBody(code(DIALOG), "public void onDestroyView()");
        assertTrue("关闭对话框必须作废代际，否则在途回调会碰已销毁的 binding", body.contains("++syncGeneration"));
    }

    /**
     * 去掉注释行后再断言：否则把守卫注释掉也能满足「语句存在」的断言，守卫退化成恒真检查。
     *
     * <p>路径从 {@code user.dir} 逐级向上找项目根，避免依赖单测工作目录是仓库根还是模块目录。
     */
    private static String code(Path relative) throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve(relative))) root = root.getParent();
        if (root == null) throw new IllegalStateException("找不到项目根，无法读取 " + relative);
        StringBuilder result = new StringBuilder();
        for (String line : Files.readString(root.resolve(relative)).replace("\r\n", "\n").split("\n", -1)) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("//") && !trimmed.startsWith("*") && !trimmed.startsWith("/*")) {
                result.append(line).append('\n');
            }
        }
        return result.toString();
    }

    /** 用花括号配平取方法/块的正文，避免字面量缩进匹配在换行风格或缩进变化后静默失配。 */
    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new IllegalStateException("找不到方法：" + signature);
        int open = source.indexOf('{', start);
        return block(source, open);
    }

    /**
     * 收集 {@code App.post(…)} 的**花括号**块体：{@code App.post(() -> { … })}。
     *
     * <p>单表达式形式（{@code App.post(() -> call())}）没有块体，按「先遇到 {@code ;}
     * 而不是 {@code &#123;}」跳过——它不可能是复位点，除非写成 {@code App.post(() -> x());}
     * 之外的形式。为防「单表达式复位点逃过收集」，{@link #everyPostedResetComparesGeneration()}
     * 另外锁住 {@code App.post(} 的总出现次数：总数一变就必须回来复核。
     */
    private static List<String> postedBlocks(String source) {
        List<String> blocks = new ArrayList<>();
        int from = 0;
        while (true) {
            int at = source.indexOf("App.post(", from);
            if (at < 0) return blocks;
            int open = source.indexOf('{', at);
            int end = source.indexOf(';', at);
            if (open >= 0 && (end < 0 || open < end)) blocks.add(block(source, open));
            from = at + 1;
        }
    }

    /** 统计子串出现次数（不重叠），用于「总数变化即失效」的数量锁。 */
    private static int countOf(String source, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = source.indexOf(needle, from);
            if (at < 0) return count;
            count++;
            from = at + needle.length();
        }
    }

    /**
     * 花括号配平取块体，跳过字符串与字符字面量里的花括号。
     *
     * <p>不跳过字面量的话，块内出现含 {@code &#123;} 的提示文案就会配平错位：可能把块体截断
     * 成「不含守卫」而误红，也可能撑大到下一个块而误绿。
     */
    private static String block(String source, int open) {
        if (open < 0) throw new IllegalStateException("找不到块起始花括号");
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(source, i);
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        throw new IllegalStateException("块缺少配对的结束花括号");
    }

    /** 返回字面量结束引号的下标（跳过转义）；未闭合时返回最后一个下标，让配平继续往前走。 */
    private static int skipLiteral(String source, int quote) {
        char delimiter = source.charAt(quote);
        for (int i = quote + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\') i++;
            else if (c == delimiter) return i;
        }
        return source.length() - 1;
    }
}
