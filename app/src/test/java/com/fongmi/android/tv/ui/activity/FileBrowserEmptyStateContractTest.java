package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 本地文件浏览器空态的契约（设计文档 §9.9）。
 *
 * <p>这是审计 P0 里最后一项空态缺口：两端 {@code FileActivity} 此前没有接入
 * {@code setEmptyContent}，空态只显示布局里的默认单行文案。
 *
 * <p><b>主动作裁定为「重新扫描当前目录」</b>（用户 2026-10-13 决定），理由：
 * <ul>
 *   <li><b>根目录没有上级</b>——"返回上级"在根目录会退出页面，与按钮文案不符；</li>
 *   <li>"重新扫描"在**任何**目录都成立，且能反映外部变化（刚用别的应用放进来的文件），
 *       这正是本地文件浏览器最需要的能力。</li>
 * </ul>
 *
 * <p>本契约钉住四件事：三件套齐备；主动作调用 {@code update(dir)}（本页唯一的列表填充
 * 入口，既重算 list 又驱动状态机）；**不得**把主动作接成返回/退出；三语字符串齐备。
 */
public class FileBrowserEmptyStateContractTest {

    private static final String[] HOSTS = {
            "app/src/leanback/java/com/fongmi/android/tv/ui/activity/FileActivity.java",
            "app/src/mobile/java/com/fongmi/android/tv/ui/activity/FileActivity.java",
    };
    private static final String STRINGS = "app/src/main/res/values/strings.xml";
    private static final String STRINGS_CN = "app/src/main/res/values-zh-rCN/strings.xml";
    private static final String STRINGS_TW = "app/src/main/res/values-zh-rTW/strings.xml";

    @Test
    public void bothFlavoursWireTheThreeParts() throws Exception {
        for (String host : HOSTS) {
            String source = read(host);
            assertTrue(host + " 必须接入空态内容", source.contains("mBinding.progressLayout.setEmptyContent("));
            for (String string : new String[]{
                    "file_empty_title", "file_empty_description", "file_empty_action"}) {
                assertTrue(host + " 必须引用 " + string, source.contains(string));
                for (String table : new String[]{STRINGS, STRINGS_CN, STRINGS_TW}) {
                    assertTrue(table + " 必须声明 " + string,
                            read(table).contains("name=\"" + string + "\""));
                }
            }
        }
    }

    /**
     * 主动作必须复用 {@code update(dir)}——本页唯一的列表填充入口。
     *
     * <p>若接成别的路径（例如只 {@code notifyDataSetChanged()}），"扫描后仍为空"就不会
     * 重新进入空态，用户会看到留白；而 {@code update} 既重算 list 又驱动状态机。
     */
    @Test
    public void thePrimaryActionRescansThroughTheSingleListEntryPoint() throws Exception {
        for (String host : HOSTS) {
            String source = read(host);
            String rescan = methodBody(source, "private void rescan()");
            assertTrue(host + " 的重新扫描必须走 update(...)", rescan.contains("update("));
            assertTrue(host + " 尚未拿到目录时必须退回根目录",
                    rescan.contains("dir == null ? Path.root() : dir"));
            // update 必须仍然驱动状态机，否则"扫描后仍为空"会留白
            String update = methodBody(source, "private void update(File dir)");
            assertTrue(host + " 的 update 必须驱动状态机", update.contains("showContent(true,"));
        }
    }

    /**
     * 反例守卫：主动作**不得**接成返回/退出。
     *
     * <p>"返回上级"在根目录会退出页面，与"重新扫描"的按钮文案不符——这是本契约要防的
     * 具体错误，而不只是风格偏好。
     */
    @Test
    public void thePrimaryActionDoesNotNavigateAway() throws Exception {
        for (String host : HOSTS) {
            String rescan = methodBody(read(host), "private void rescan()");
            assertFalse(host + " 的重新扫描不得调用 finish()", rescan.contains("finish()"));
            assertFalse(host + " 的重新扫描不得调用 onBackPressed()", rescan.contains("onBackPressed"));
            assertFalse(host + " 的重新扫描不得调用 getParentFile()（那是「返回上级」的做法）",
                    rescan.contains("getParentFile"));
        }
    }

    /**
     * 空态文案必须解释"这里本来就没有可显示的项"，而不是"加载失败"——两者是不同状态
     * （§9.9 把空态与错误态分开）。
     */
    @Test
    public void theCopyDescribesAnEmptyFolderNotAFailure() throws Exception {
        String cn = read(STRINGS_CN);
        assertTrue("简中标题必须表达「这里没有内容」", cn.contains("这里没有内容"));
        assertTrue("简中动作必须是「重新扫描」", cn.contains("重新扫描"));
        assertFalse("空态文案不得使用「失败」措辞（那是错误态）", cn.contains("file_empty_description\">加载失败"));
    }

    // ------------------------------------------------------------------ helpers

    private static String methodBody(String source, String signature) {
        String cleaned = source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
        int start = cleaned.indexOf(signature);
        assertTrue("必须存在方法 " + signature, start > 0);
        int open = cleaned.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return cleaned.substring(start, i + 1);
        }
        return cleaned.substring(start);
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
