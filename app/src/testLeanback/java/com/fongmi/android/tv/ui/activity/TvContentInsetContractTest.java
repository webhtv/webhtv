package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 电视端内容区必须等于整块窗口：底部不允许再出现整条黑带。
 *
 * <p>实机证据（模拟器 5554，1920x1080/280dpi）：该屏带底部 overscan 90px
 * （{@code dumpsys window displays} → {@code overscan (0,0,0,90)}），系统把这条内边距
 * 下发给窗口。AppCompat 的 decor 里有两层各自应用了一次 fitsSystemWindows 内边距
 * （外层 window content 与内层 {@code android:id/content}），于是内容区被扣掉
 * 2×90=180px —— 实测 {@code android:id/content} 只有 {@code 0,0-1920,900} 而窗口是
 * {@code 0,0-1920,1080}；同一块屏上 launcher 的内容区是 990（只扣一次）。表现就是
 * 首页卡片标题被齐腰切断、{@code 900..1080} 整条是窗口背景黑。
 *
 * <p>修复走 leanback {@code BaseActivity#bindFullWindowContent()}：在 DecorView 上装
 * "只消费、不加内边距"的监听器，inset 不再下发给任何一层，内容铺满窗口。
 * 本守卫锁住三件事，防止以后回退或被复制到手机端：
 * <ol>
 *   <li>leanback 基类确实按上面的方式消费 inset；</li>
 *   <li>它没有把 inset 转成 padding/margin（那就又变成黑带）；</li>
 *   <li>手机端基类不消费 inset（手机需要系统栏内边距，行为必须分开）。</li>
 * </ol>
 */
public class TvContentInsetContractTest {

    private static final String LEANBACK_BASE = "app/src/leanback/java/com/fongmi/android/tv/ui/base/BaseActivity.java";
    private static final String MOBILE_BASE = "app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java";

    @Test
    public void leanbackActivitiesFillTheWholeWindow() throws Exception {
        String source = read(LEANBACK_BASE);

        assertTrue("电视端必须有一个统一的内容铺满窗口入口",
                source.contains("private void bindFullWindowContent()"));
        assertTrue("setContentView 之后必须立即调用该入口",
                source.contains("setContentView(content);\n        bindFullWindowContent();"));
        assertTrue("必须显式关闭 decor 的 fitsSystemWindows 布局（否则系统仍会把内容压进安全区）",
                source.contains("WindowCompat.setDecorFitsSystemWindows(getWindow(), false)"));
        assertTrue("必须在 DecorView 上只消费 inset，不再向下分发",
                source.contains("ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView()")
                        && source.contains("WindowInsetsCompat.CONSUMED"));
        assertTrue("内层 android:id/content 不能再自行应用一次内边距",
                source.contains("decorContent.setFitsSystemWindows(false)"));
    }

    @Test
    public void theInsetConsumerNeverTurnsInsetsIntoPadding() throws Exception {
        String source = sourceOfMethod(read(LEANBACK_BASE), "bindFullWindowContent");

        // 正向锚点：方法体必须真的被切出来，否则下面两条否定断言会对着空串空转。
        assertTrue("必须能定位到 bindFullWindowContent 的方法体",
                source.contains("void bindFullWindowContent()")
                        && source.contains("setDecorFitsSystemWindows"));
        assertFalse("消费 inset 后不允许再用 bottom inset 设置 padding（那会把黑带换个形式留下）",
                source.contains("getSystemWindowInsetBottom")
                        || source.contains("Type.systemBars()")
                        || source.contains("setPadding("));
    }

    @Test
    public void mobileKeepsItsSystemBarInsets() throws Exception {
        String source = read(MOBILE_BASE);

        assertFalse("手机端不能被改成消费 inset：状态栏/导航栏区域会盖住内容",
                source.contains("WindowInsetsCompat.CONSUMED"));
        assertFalse("手机端不能复用电视端的铺满窗口入口",
                source.contains("bindFullWindowContent"));
    }

    private static String sourceOfMethod(String source, String name) {
        // 必须从方法声明起切，不能从调用点起：否则先撞上 javadoc 里的 {@link ...} 花括号，
        // 返回的是一段注释片段，否定断言就永远对着空串空转。
        int start = source.indexOf("void " + name + "(");
        if (start < 0) return "";
        int brace = source.indexOf('{', start);
        if (brace < 0) return "";
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        return source.substring(start);
    }

    private static String read(String path) throws Exception {
        Path root = Files.exists(Path.of(path)) ? Path.of(".") : Path.of("..");
        // 仓库里的 Java 源码是 CRLF，多行断言必须先归一化，否则永远匹配不上。
        return new String(Files.readAllBytes(root.resolve(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
