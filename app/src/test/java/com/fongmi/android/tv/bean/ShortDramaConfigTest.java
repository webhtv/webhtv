package com.fongmi.android.tv.bean;

import com.google.gson.Gson;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShortDramaConfigTest {

    @Test
    public void explicitEmptyRulesDisableDefaults() {
        ShortDramaConfig config = new Gson().fromJson(
                "{\"configured\":true,\"enabledSites\":[],\"disabledSites\":[]}",
                ShortDramaConfig.class);
        config.sanitize();

        assertTrue(config.isConfigured());
        assertEquals("", config.getDisplayRules());
        assertFalse(config.isSiteEnabled("nodejs_short", "[短]短剧站"));
    }

    @Test
    public void missingConfigurationStillUsesDefaults() {
        ShortDramaConfig config = new ShortDramaConfig().sanitize();

        assertFalse(config.isConfigured());
        assertEquals(Arrays.asList("[短]", "短剧"), ShortDramaConfig.defaultRules());
        assertTrue(config.isSiteEnabled("nodejs_short", "[短]短剧站"));
    }

    @Test
    public void dialogDoesNotRecreateDefaultsAfterRemovingAllRules() throws Exception {
        // 只对可执行代码断言：注释里提到这些符号（本文件的注释就会）不应影响契约。
        String source = codeOnly(read("app/src/main/java/com/fongmi/android/tv/ui/dialog/ShortDramaSourceDialog.java"));

        // 三个「空规则不得回填默认」的站点：show() 打开弹窗时、showSiteManage() 勾选态计算时、
        // updateChipsDisplay() 画 chip 时。不钉具体写法（`new ArrayList<>(...)` 这类拷贝是可选的），
        // 只钉语义不变量：默认规则只能从两处合法入口进入。
        assertTrue(source.contains("tempEnabledRules = new ArrayList<>(config.isConfigured()"));
        // 兜底本身必须彻底消失：`tempEnabledRules.isEmpty()` 曾经是「用户清空 -> 默认规则复活」
        // 的判定式，而 defaultRulesText() 正是它回填的内容。
        assertFalse("空规则判定式必须已从对话框中移除",
                source.contains("tempEnabledRules.isEmpty()"));
        assertFalse("对话框不得再用 defaultRulesText() 回填默认规则",
                source.contains("defaultRulesText"));
        // defaultRules() 只有两个合法入口：① 未配置过时 show() 的初始值；② 用户主动点「恢复默认」。
        // 第三个入口就意味某条路径在用户清空后又把默认规则塞了回来——正是本缺陷的形态。
        assertTrue("未配置过时初始值必须回到默认规则（configured 为 false 的归一分支）",
                source.contains(": ShortDramaConfig.defaultRules());"));
        assertTrue("「恢复默认」按钮必须真的写回默认规则",
                source.contains("tempEnabledRules.addAll(ShortDramaConfig.defaultRules());"));
        assertTrue("默认规则不得出现第三个入口（每条路径都要尊重用户清空）",
                count(source, "ShortDramaConfig.defaultRules()") <= 2);
    }

    private static int count(String source, String needle) {
        int total = 0;
        for (int index = source.indexOf(needle); index >= 0; index = source.indexOf(needle, index + needle.length())) total++;
        return total;
    }

    /** 去掉注释，让源码契约匹配可执行代码，而不是描述这些符号的说明文字。 */
    private static String codeOnly(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (current == '/' && next == '/') {
                while (index < source.length() && source.charAt(index) != '\n') index++;
            } else if (current == '/' && next == '*') {
                index += 2;
                while (index + 1 < source.length() && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) index++;
                index = Math.min(source.length(), index + 2);
            } else {
                out.append(current);
                index++;
            }
        }
        return out.toString();
    }

    private static String read(String file) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
