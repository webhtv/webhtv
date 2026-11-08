package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/**
 * 改「总上限」必须让面板上的「收至」重新开出来。
 *
 * <p>播放缓存空闲态的目标值由总上限推导（{@code desiredCapacityBytes(IDLE)} 读
 * {@code CachePolicyStore.getTotalLimitBytes()}），而摘要行里的该数字是在
 * {@code render(CacheSnapshot)} 里现算的。改上限若只调 {@code updatePolicyButtons()}，
 * 就只刷新三个按钮文案，摘要行会继续显示改之前的目标值——用户看到的是一个已经
 * 不成立的承诺。</p>
 *
 * <p>因此 {@code chooseTotalLimit()} 的回调必须与 EXO 单项上限走同一条应用路径
 * （{@code applyPlaybackLimit()}），而不是仅重绘按钮。</p>
 */
public class CacheTotalLimitApplyContractTest {

    private static final String DIALOG =
            "app/src/main/java/com/fongmi/android/tv/ui/dialog/CacheManagementDialog.java";

    @Test
    public void totalLimitChangeAppliesAndRepaintsTheSummary() throws Exception {
        String body = methodBody(read(DIALOG), "private void chooseTotalLimit()");
        assertTrue("改总上限必须落到播放缓存的应用路径",
                body.contains("applyPlaybackLimit()"));
        assertTrue("改总上限仍要重绘上限按钮",
                body.contains("updatePolicyButtons()"));
        assertTrue("先落盘再应用", body.indexOf("putTotalLimitBytes") < body.indexOf("applyPlaybackLimit()"));
    }

    @Test
    public void bothLimitEntriesShareThePlaybackApplyPath() throws Exception {
        String source = read(DIALOG);
        assertTrue("总上限走应用路径",
                methodBody(source, "private void chooseTotalLimit()").contains("applyPlaybackLimit()"));
        assertTrue("EXO 单项上限也走同一条应用路径",
                methodBody(source, "private void chooseLimit(CacheModuleId id)").contains("applyPlaybackLimit()"));
    }

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
