package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 只看源码文本，不引入任何新框架：本仓库既有的守卫测试都是这个风格。
 *
 * <p>断言口径刻意避开局部变量名、注释内容和硬编码的调用次数，只钉住真正的契约：
 *
 * <ol>
 *   <li>守卫闭包自己复查布局并重新挂帧（崩溃根因）；</li>
 *   <li>守卫是「无返回值 + 自决执行或入槽」的单入口：任何调用点都不可能重复执行目标
 *       （历史缺陷：boolean 返回值让非延后路径「方法内 run 一次 + 调用方再 run 一次」）；</li>
 *   <li>排队语义是「单槽合并 + 必执行」——后写者覆盖、同一时刻只有一个 runner、
 *       最新写入最终一定会被执行，绝不能只丢弃；</li>
 *   <li>结构性写入只有守卫一条入口，没有任何绕过守卫的直接同步 addAll；</li>
 *   <li>可见性的隐藏留在同步段，只有 adapter 换数据被延后（否则旧分类按钮至少多活一帧）。</li>
 * </ol>
 *
 * <p>「单槽」「posted 标志」等字段名不写死：先按结构（把 update 参数赋给谁、在
 * postOnAnimation 之前把谁置 true）从守卫体里推出来，再拿推出来的名字做后续断言，
 * 所以合法重命名不会误红，而语义被改掉一定红。
 */
public class HomeTypeRecyclerLayoutGuardTest {

    private static final String HOME = "app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java";
    /** 守卫的固定名字：它是本次修复的契约本体，不随重构变动。 */
    private static final String GUARD = "runTypeMutation";

    @Test
    public void deferredTypeUpdatesRecheckLayoutInsideTheCallback() throws Exception {
        String home = read(HOME);
        String guard = guardBody(home);

        String busy = busyHelperName(guard);
        String busyBody = methodBody(home, "private boolean " + busy + "(");
        assertNotNull("布局忙碌判定辅助方法 " + busy + " 必须存在：同一条件在守卫里重复两遍就会改一处漏一处", busyBody);
        assertTrue("崩溃的根因是布局过程中做结构性修改：忙碌判定必须复查 isComputingLayout()",
                busyBody.contains("isComputingLayout()"));
        assertTrue("忙碌判定必须把内容行 recycler 的布局状态算进来，否则内容行布局时仍会崩",
                busyBody.contains("mBinding.recycler.isComputingLayout()"));
        assertTrue("忙碌判定必须把类型栏自己的布局状态算进来，否则类型栏布局时仍会崩",
                busyBody.contains("mBinding.typeRecycler.isComputingLayout()"));
        assertEquals("两个 recycler 的布局状态只允许在忙碌判定里各出现一次（消除重复条件），"
                + "守卫体里不得再内联同一条件", 0, occurrences(guard, "isComputingLayout()"));

        int recheck = guard.indexOf("if (" + busy + "())");
        int repost = guard.indexOf("postOnAnimation(this)");
        assertTrue("只复查一次不够：下一帧动画里布局可能还没结束，未就绪必须重新 postOnAnimation 同一个 Runnable",
                recheck >= 0 && repost > recheck);
        assertTrue("复查必须排在重新挂帧之前，否则等于没守",
                guard.indexOf("canDeferFragmentWork()") < recheck && recheck < repost);
        assertTrue("延后任务在宿主正在结束或已销毁时必须丢弃，沿用本文件既有语义",
                guard.contains("canDeferFragmentWork()"));
    }

    /**
     * 本轮缺陷的回归钉：守卫曾经返回 boolean，于是「同步分支自己 run 一次」+
     * 「调用方按返回值再 run 一次」＝非延后路径双执行（addAll/notifyDataSetChanged 两遍、
     * setSelectedPosition(0) 两遍、showHomeContent() 两遍）。
     */
    @Test
    public void theGuardIsVoidSoCallSitesCannotReExecuteTheTarget() throws Exception {
        String home = read(HOME);
        String guard = guardBody(home);
        assertFalse("守卫一旦返回布尔值，调用方就会据此再执行一次目标（双执行缺陷本体）",
                guard.contains("return true;") || guard.contains("return false;"));
        assertFalse("守卫不得用「返回值告诉调用方该不该执行」的旧协议",
                guard.contains("boolean " + GUARD + "("));

        List<String> callSites = guardCallSites(home);
        assertFalse("守卫必须被真实调用，否则它只是死代码", callSites.isEmpty());
        for (String line : callSites) {
            assertTrue("调用点必须是裸语句 " + GUARD + "(...);，不得写成 if (" + GUARD + "(...)) ...、"
                    + "不得赋值给变量、不得参与三元或布尔运算：任何取值形态都会让目标再被执行一次。实际：" + line,
                    isBareStatement(line));
        }

        assertFalse("调用点不得出现在 if/while/for 的条件里：条件成立时调用方会再执行一次目标",
                Pattern.compile("(?:if|while|for)\\s*\\([^;{}]*" + GUARD + "\\(").matcher(home).find());
        assertFalse("调用点不得被赋值（等于重新引入返回值协议）",
                Pattern.compile("=\\s*" + GUARD + "\\(").matcher(home).find());
        assertFalse("调用点不得被取反或参与布尔运算（等于重新引入返回值协议）",
                Pattern.compile("[!&|]\\s*" + GUARD + "\\(").matcher(home).find());

        Set<String> targets = guardedTargets(home);
        assertFalse("守卫必须真实接管结构性写入目标", targets.isEmpty());
        for (String target : targets) {
            boolean covered = false;
            for (String line : callSites) {
                if (isBareStatement(line) && line.contains(target)) covered = true;
            }
            assertTrue("延后目标 " + target + " 必须由一个裸语句调用点接管，不得只出现在条件/赋值里", covered);
        }
    }

    /**
     * 非延后路径的「只执行一次」结构钉：守卫体里只允许两个执行点——同步分支的
     * update.run() 与 runner 从槽位取出后的 pending.run()。多出第三处就是重复执行。
     */
    @Test
    public void theSynchronousBranchRunsItsTargetExactlyOnce() throws Exception {
        String guard = guardBody(read(HOME));
        assertEquals("守卫体里 update.run() 只能出现一次：出现两次意味着同步分支自己就对目标执行了两遍",
                1, occurrences(guard, "update.run();"));
        String sync = guard.substring(0, guard.indexOf("postOnAnimation"));
        assertEquals("同步分支只允许一个执行点：多出来的执行点就是重复执行",
                1, occurrences(sync, ".run();"));
        String runner = guard.substring(guard.indexOf("postOnAnimation"));
        assertEquals("runner 只允许一个执行点（从槽位取出的那个待写入），多出来的执行点就是重复执行",
                1, occurrences(runner, ".run();"));
        assertTrue("守卫体总共只允许两个执行点，实测 " + occurrences(guard, ".run();"),
                occurrences(guard, ".run();") == 2);

        int post = guard.indexOf("postOnAnimation");
        int syncRun = guard.indexOf("update.run();");
        assertTrue("同步执行必须排在首次 post 之前，语义才是「不忙就立刻落地」", syncRun >= 0 && syncRun < post);
    }

    /**
     * 缺陷 A 的回归钉：旧实现用「代际号丢弃」，一次布局中的写入会被随后的焦点失效静默作废，
     * 类型栏就永久空白。新语义下最新一次写入必然被执行。
     */
    @Test
    public void theNewestDeferredWriteIsAlwaysExecuted() throws Exception {
        String guard = guardBody(read(HOME));
        String slot = deferralSlot(guard);

        String sync = guard.substring(0, guard.indexOf("postOnAnimation"));
        assertTrue("布局没在跑时必须立刻执行更新", sync.contains("update.run();"));
        assertTrue("立刻执行的分支必须清空待写入槽位，否则这条已落地的旧写入会在下一帧盖掉更新的写入",
                sync.contains(slot + " = null;"));
        assertTrue("必须先清空槽位再执行更新：更新自身可能再次排队，先 run 后清会把新排队的写入抹掉",
                sync.indexOf(slot + " = null;") < sync.indexOf("update.run();"));

        String runner = guard.substring(guard.indexOf("postOnAnimation"));
        assertTrue("销毁期清理必须把槽位一起清空，避免持有已失效的闭包", runner.contains(slot + " = null;"));
        assertTrue("重启后的 runner 必须继续复查，布局没结束时重新挂帧而不是放弃",
                runner.contains("postOnAnimation(this)"));

        String pending = captureFromSlot(runner, slot);
        assertTrue("最新写入必被执行：runner 必须在最后取出槽位里的写入并调用它，只丢弃会让类型栏永久空白",
                runner.contains(pending + ".run();"));
        assertTrue("执行必须发生在重新挂帧判定之后，否则又变成布局中做结构性修改",
                runner.indexOf(pending + ".run();") > runner.indexOf("postOnAnimation(this)"));
    }

    /** 缺陷 A 的另一半：旧写入不得覆盖新写入，且同一时刻只允许一个 runner 在排。 */
    @Test
    public void deferralMergesIntoOneSlotSoStaleWorkCannotOverwriteNewData() throws Exception {
        String home = read(HOME);
        String guard = guardBody(home);
        String slot = deferralSlot(guard);

        assertTrue("待写入必须是可被覆盖的单个槽位：把本次 update 存进槽位，后写者自然覆盖前者",
                guard.contains(slot + " = update;"));

        String posted = postedFlag(guard);
        int dedupe = guard.indexOf("if (" + posted + ") return;");
        int setPosted = guard.indexOf(posted + " = true;");
        int post = guard.indexOf("postOnAnimation");
        assertTrue("同一时刻只允许一个 runner 在排队：已在排队时必须直接返回，不得重复 post",
                dedupe >= 0 && dedupe < setPosted);
        assertTrue("标记必须在真正 post 之前置位，否则重入会排出第二个 runner",
                setPosted > dedupe && setPosted < post);
        assertTrue("runner 开始时必须先清标记，否则一次执行之后就再也不会有新的 runner",
                guard.substring(guard.indexOf("postOnAnimation")).contains(posted + " = false;"));

        assertFalse("不得再用代际号丢弃在途写入：丢弃会让布局中的写入变成无人执行的孤儿",
                home.contains("mTypeUpdateGeneration"));
        assertFalse("守卫体里不允许出现弃写式代际字段（只看字段名，注释措辞不算）",
                guard.contains("mTypeUpdateGeneration"));
        assertFalse("换源作废靠「后写者覆盖」自然实现，不需要额外代际号",
                methodBody(home, "private void clearCategoryContent(").contains("mTypeUpdateGeneration"));
    }

    /** 结构性写入只有一条入口：守卫自己执行，或由 runner 执行。 */
    @Test
    public void everyStructuralTypeMutationGoesThroughTheLayoutGuard() throws Exception {
        String home = read(HOME);
        String guard = guardBody(home);
        Set<String> targets = guardedTargets(home);
        assertTrue("守卫必须被真实使用，否则它只是死代码", !targets.isEmpty());
        assertTrue("站点切换清空旧分类是目标崩溃场景，必须经守卫",
                methodBody(home, "private void clearStaleSiteTypes(").contains(GUARD + "("));

        for (String target : targets) {
            String body = methodBody(home, "private void " + target + "(");
            assertNotNull("被守卫调用的 " + target + " 必须存在，否则守卫目标缺失", body);
            assertTrue(target + " 必须承载结构性写入，否则它不该出现在守卫后面",
                    body.contains("mTypeAdapter.addAll("));
            assertEquals("延后目标 " + target + " 内部只允许一次 mTypeAdapter.addAll(："
                    + "出现两次意味着一次调用就触发两遍 notifyDataSetChanged", 1, occurrences(body, "mTypeAdapter.addAll("));
        }

        // 语义化判定（不数次数）：每一处 adapter 结构性写入所在的宿主方法，必须是某个被
        // 守卫接管的延后目标。多出来的一处同步直写就是绕过布局守卫，这条断言必红。
        int index = home.indexOf("mTypeAdapter.addAll(");
        int seen = 0;
        while (index >= 0) {
            seen++;
            String owner = enclosingMethod(home, index);
            assertTrue("mTypeAdapter.addAll( 只能出现在被守卫接管的延后目标里，实际出现在 " + owner + "："
                    + "这就是绕过布局守卫的直接同步写入", targets.contains(owner));
            index = home.indexOf("mTypeAdapter.addAll(", index + 1);
        }
        assertTrue("adapter 结构性写入不能被整体删掉", seen > 0);

        for (String target : targets) {
            for (String raw : home.split("\n")) {
                String line = raw.trim();
                if (!line.contains(target + "(")) continue;
                if (line.contains(GUARD + "(")) continue;
                if (line.startsWith("*") || line.startsWith("/*") || line.startsWith("//")) continue;
                assertTrue("延后目标 " + target + " 不允许在守卫之外被单独调用：" + line,
                        line.contains("private void " + target + "("));
            }
        }
        assertFalse("守卫不得退化成 notifyDataSetChanged 式的全量刷新绕开结构性守卫",
                guard.contains("notifyDataSetChanged("));
    }

    /**
     * 缺陷 B 的回归钉：换源后旧分类按钮必须立刻不可见，否则 switchType() 会按旧 adapter 判定
     * 并打开旧 Class。延后整个 clearStaleTypeItems 会把 setVisibility(GONE) 一起顺延一帧。
     */
    @Test
    public void staleTypeRowHidesSynchronouslyAndOnlyTheDataClearIsDeferred() throws Exception {
        String home = read(HOME);
        String clearStale = methodBody(home, "private void clearStaleSiteTypes(");
        String deferred = methodBody(home, "private void clearStaleTypeItems(");

        assertTrue("换源清空必须交给布局守卫，否则就是崩溃根因", clearStale.contains(GUARD + "("));
        assertTrue("隐藏必须留在同步段：旧分类按钮不能多活一帧，否则仍可聚焦、仍能按旧 adapter 触发切换",
                clearStale.contains("mBinding.typeRecycler.setVisibility(View.GONE);"));
        assertTrue("选中的撤销同样必须同步，否则 switchType 还会按旧的待切位置判定",
                clearStale.contains("mPendingTypePosition = -1;"));
        assertTrue("同步隐藏必须发生在延后数据清空之前，语义才是「立刻移除旧分类」",
                clearStale.indexOf("mBinding.typeRecycler.setVisibility(View.GONE);")
                        < clearStale.indexOf(GUARD + "("));

        assertNotNull("延后目标必须存在", deferred);
        assertFalse("可见性不是结构性修改，不得被延后：它一旦进了延后目标，缺陷 B 就复现",
                deferred.contains("setVisibility("));
        assertFalse("选中位置是纯状态量，不得被延后", deferred.contains("mPendingTypePosition"));
        assertTrue("延后目标只承载 adapter 的换数据写入", deferred.contains("mTypeAdapter.addAll("));
        // applyTypes 里的 updateTypeRecyclerVisibility() 依赖新数据算可见性，是合法例外；
        // 换源清空这条路径没有任何依赖新数据的理由，可见性必须同步。
        assertTrue("换源清空链路必须仍然经守卫进入延后目标",
                clearStale.contains(GUARD + "(this::clearStaleTypeItems)"));
    }

    /** 缺陷 A 的落点钉：焦点失效与数据写入是两件事，焦点失效不得作废在途的类型栏写入。 */
    @Test
    public void focusInvalidationNeverDropsPendingTypeWrites() throws Exception {
        String home = read(HOME);
        String slot = deferralSlot(guardBody(home));

        String invalidate = methodBody(home, "private void invalidatePendingFocusRequests(");
        assertNotNull(invalidate);
        assertTrue("焦点失效仍然照旧推进自己的代际号", invalidate.contains("focusGeneration++"));
        assertFalse("焦点失效不得动类型栏的待写入槽位，否则布局中的写入会被无谓作废",
                invalidate.contains(slot));

        String clearCategory = methodBody(home, "private void clearCategoryContent(");
        assertTrue("换源必须仍然清掉上一站点的分类按钮", clearCategory.contains("clearStaleSiteTypes();"));
        assertFalse("换源不需要额外作废在途写入：清理本身就是最新写入，靠后写者覆盖即已足够",
                clearCategory.contains(slot));
    }

    private static String guardBody(String home) {
        String guard = methodBody(home, "private void " + GUARD + "(");
        assertNotNull("类型栏布局守卫必须是 private void " + GUARD + "(Runnable)："
                + "无返回值是实现「不可能重复执行」的前提", guard);
        return guard;
    }

    /** 待写入槽位 = 守卫体里被赋值为本次 update 参数的那个字段，按结构推导，不写死名字。 */
    private static String deferralSlot(String guard) {
        Matcher matcher = Pattern.compile("(\\w+)\\s*=\\s*update\\s*;").matcher(guard);
        assertTrue("守卫必须把本次更新存进一个可被后写者覆盖的槽位", matcher.find());
        return matcher.group(1);
    }

    /** 单 runner 去重标志 = 守卫体外层那句「已在排队就返回」里的字段。 */
    private static String postedFlag(String guard) {
        Matcher matcher = Pattern.compile("if\\s*\\(\\s*(\\w+)\\s*\\)\\s*return\\s*;").matcher(guard);
        assertTrue("守卫必须用「已在排队就直接返回」来去重 runner", matcher.find());
        return matcher.group(1);
    }

    /** runner 从槽位取出的局部变量名。 */
    private static String captureFromSlot(String runner, String slot) {
        Matcher matcher = Pattern.compile("Runnable\\s+(\\w+)\\s*=\\s*" + Pattern.quote(slot) + "\\s*;").matcher(runner);
        assertTrue("runner 必须把槽位里的写入取出来再执行，否则最新写入会变成孤儿", matcher.find());
        return matcher.group(1);
    }

    /** 布局忙碌判定的辅助方法名 = 同步分支里「不忙就立刻执行」那句取反调用的方法。 */
    private static String busyHelperName(String guard) {
        Matcher matcher = Pattern.compile("if\\s*\\(\\s*!\\s*(\\w+)\\(\\)\\s*\\)").matcher(guard);
        assertTrue("同步分支必须用一句取反的忙碌判定决定是否立刻执行（条件只定义一次）", matcher.find());
        return matcher.group(1);
    }

    /** 文件里所有「直接调用守卫」的代码行（排除声明行、javadoc 与注释行），已去掉首尾空白。 */
    private static List<String> guardCallSites(String home) {
        List<String> lines = new ArrayList<>();
        for (String raw : home.split("\n")) {
            String line = raw.trim();
            if (!line.contains(GUARD + "(")) continue;
            if (line.startsWith("private void " + GUARD + "(")) continue;
            if (line.startsWith("*") || line.startsWith("/*") || line.startsWith("//")) continue;
            lines.add(line);
        }
        return lines;
    }

    /**
     * 裸语句形态：整行只有一句 runTypeMutation(...); —— 不参与任何判定或赋值，也不夹带第二条语句。
     *
     * <p>行内只允许一个分号：{@code runTypeMutation(this::x); x();} 这种「守卫先跑一遍、
     * 目标再跑一遍」的同行写法必须被判红，否则它同样会重现双执行。
     */
    private static boolean isBareStatement(String line) {
        return line.matches(Pattern.quote(GUARD) + "\\(.*\\);") && occurrences(line, ";") == 1;
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    /**
     * 被守卫接管的延后目标名（this::name 与 () -> name(...) 两种写法都认）。
     *
     * <p>必须扫整个文件而不是守卫体：调用点在 setTypes / clearStaleSiteTypes 里。
     */
    private static Set<String> guardedTargets(String home) {
        Set<String> names = new LinkedHashSet<>();
        Matcher methodRef = Pattern.compile(GUARD + "\\(\\s*this::(\\w+)\\s*\\)").matcher(home);
        while (methodRef.find()) names.add(methodRef.group(1));
        Matcher lambda = Pattern.compile(GUARD + "\\(\\s*\\(\\)\\s*->\\s*(\\w+)\\(").matcher(home);
        while (lambda.find()) names.add(lambda.group(1));
        return names;
    }

    /** 从方法签名处截到与之配对的右花括号，避免依赖具体的方法书写顺序。 */
    private static String methodBody(String source, String signature) {
        int from = source.indexOf(signature);
        if (from < 0) return null;
        int body = source.indexOf('{', from);
        if (body < 0) return null;
        int depth = 0;
        for (int i = body; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(from, i + 1);
        }
        return source.substring(from);
    }

    /** 定位某个偏移所在的方法名，只认类体内一级缩进的方法声明行。 */
    private static String enclosingMethod(String source, int offset) {
        String name = "<file-scope>";
        int cursor = 0;
        for (String line : source.split("\n", -1)) {
            if (cursor > offset) break;
            Matcher matcher = Pattern.compile("^ {4}(?:private|protected|public)\\s+[\\w<>\\[\\].]+\\s+(\\w+)\\(").matcher(line);
            if (matcher.find()) name = matcher.group(1);
            cursor += line.length() + 1;
        }
        return name;
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path) && relative.startsWith("app/")) path = Path.of(relative.substring(4));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
