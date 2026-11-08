package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * UI token 静态门槛的可执行性契约。
 *
 * <p>设计文档 §13.1 把 {@code scripts/check_ui_tokens.sh} 列为**每阶段必做**的静态验证。
 * 但该脚本把全部检查体放在内嵌的 {@code python3} heredoc 里，真正依赖 bash 的只有参数解析与
 * {@code git rev-parse}——在没有可用 bash 的主机上（Windows + WSL 服务不可用）它**根本无法
 * 运行**，于是这个门槛在相当长一段 UI 工作里**静默地没有被执行过**，期间有固定色被引入而
 * 无人拦下（审计实测：{@code violations=1}、{@code hex_layouts=1}）。
 *
 * <p>本契约钉住三件事：
 * <ol>
 *   <li>存在一个**不依赖 bash** 的等价入口；</li>
 *   <li>两个入口的 CLI 契约一致（选项名、strict 推导规则、退出码语义）；</li>
 *   <li>门槛的五项检查一项都没少（防止有人把移植版简化成"只看对比度"）。</li>
 * </ol>
 *
 * <p>注意：这里断言的是**脚本源码的内容契约**，不是运行结果——运行结果由 CI/人工执行
 * {@code python scripts/check_ui_tokens.py --strict} 得到。之所以用源码断言，是因为 JVM
 * 单测里无法可靠地 spawn 一个 python 进程（且不该依赖宿主装了 python）。
 */
public class UiTokenGatePortabilityContractTest {

    private static final String SH = "scripts/check_ui_tokens.sh";
    private static final String PY = "scripts/check_ui_tokens.py";

    /** 必须存在一个不依赖 bash 的入口。 */
    @Test
    public void aShellIndependentEntryPointExists() throws Exception {
        Path py = resolve(PY);
        assertTrue("必须存在 " + PY + "（无 bash 主机上的等价入口）", Files.isRegularFile(py));
        String source = Files.readString(py, StandardCharsets.UTF_8);
        assertFalse("移植版不得再调用 bash/sh", source.contains("subprocess.run([\"bash\""));
        assertFalse("移植版不得依赖内嵌 heredoc", source.contains("<<'PY'"));
        assertTrue("必须声明它是 .sh 的移植（否则下一个人会以为是第二套实现）",
                source.contains("Python port of scripts/check_ui_tokens.sh"));
    }

    /**
     * CLI 契约必须一致：选项名、strict 推导规则、退出码。
     */
    @Test
    public void bothEntryPointsShareTheSameCliContract() throws Exception {
        String sh = read(SH);
        String py = read(PY);
        for (String option : new String[]{"--baseline", "--strict", "--stage", "--tokens-file", "--allowlist"}) {
            assertTrue(".sh 必须接受 " + option, sh.contains(option));
            assertTrue(".py 必须接受 " + option, py.contains(option));
        }
        // 退出码语义：PASS=0 / FAIL=1 / 用法错误=2
        assertTrue(".sh 必须用 1 表示失败", sh.contains("raise SystemExit(1 if failed else 0)"));
        assertTrue(".py 必须用 1 表示失败", py.contains("return 1 if failed else 0"));
        assertTrue(".py 必须用 2 表示用法错误", py.contains("return 2"));
        // strict 推导：--baseline 关；stage 非 A 开
        for (String source : new String[]{sh, py}) {
            assertTrue("必须保留「--baseline 关 strict」的规则", source.contains("baseline"));
            assertTrue("必须保留「stage 非 A 开 strict」的规则", source.contains("\"A\""));
        }
    }

    /**
     * 五项检查一项都不能少。逐项按输出行断言——那些行是脚本对外的可观测契约。
     */
    @Test
    public void allFiveChecksSurviveThePort() throws Exception {
        String py = read(PY);
        // 1) token 完整性  2) style 完整性  3) 对比度  4) 固定色扫描  5) 遗留主题名
        assertTrue("缺 token 完整性检查", py.contains("UI_TOKEN_STATUS FAIL missing tokens="));
        assertTrue("缺 style 完整性检查", py.contains("UI_TOKEN_STATUS FAIL missing styles="));
        assertTrue("缺对比度检查", py.contains("UI_TOKEN_CONTRAST_FAILURE"));
        assertTrue("缺固定色扫描", py.contains("UI_TOKEN_VIOLATION"));
        assertTrue("缺遗留主题名检查", py.contains("UI_TOKEN_LEGACY"));
        // 基线行与状态行是既有记录引用的可观测输出，必须保持。
        // 注意按**前缀**断言：这些行是 f-string 拼接的（例如 f"UI_TOKEN_SCOPE {scope} ..."），
        // 所以 "UI_TOKEN_SCOPE stage=" 这种含具体值的字面量在源码里并不存在。
        assertTrue("缺 UI_TOKEN_BASELINE 行", py.contains("UI_TOKEN_BASELINE layouts="));
        assertTrue("缺 UI_TOKEN_SCOPE 行", py.contains("UI_TOKEN_SCOPE "));
        assertTrue("缺 UI_TOKEN_CONTRAST 行", py.contains("UI_TOKEN_CONTRAST pairs="));
        assertTrue("缺 UI_TOKEN_STATUS 行", py.contains("UI_TOKEN_STATUS "));
        // 扫描范围必须与 .sh 一致（少了任何一类都会让门槛变松）
        for (String root : new String[]{
                "app/src/main/res/layout", "app/src/mobile/res/layout", "app/src/leanback/res/layout",
                "app/src/main/res/drawable", "app/src/mobile/res/drawable", "app/src/leanback/res/drawable",
                "app/src/main/res/color", "app/src/main/assets"}) {
            assertTrue("扫描范围缺 " + root, py.contains(root));
        }
    }

    /**
     * 规范必须指向新入口，否则下一个人仍然只会去跑 .sh 然后卡住。
     */
    @Test
    public void theSpecPointsAtTheRunnableEntryPoint() throws Exception {
        String spec = read("docs/webhtv-unified-visual-design-system-20260920.md");
        assertTrue("§13.1 必须给出无 bash 主机上的替代入口",
                spec.contains("python scripts/check_ui_tokens.py"));
        assertTrue("必须说明两者是同一门槛的两个入口", spec.contains("同一个门槛的两个入口"));
    }

    // ------------------------------------------------------------------ helpers

    private static Path resolve(String path) {
        Path direct = Path.of(path);
        return Files.exists(direct) ? direct : Path.of("..").resolve(path);
    }

    private static String read(String path) throws Exception {
        return Files.readString(resolve(path), StandardCharsets.UTF_8);
    }
}
