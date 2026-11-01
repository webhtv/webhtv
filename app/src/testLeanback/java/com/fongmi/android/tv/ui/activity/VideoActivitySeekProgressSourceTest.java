package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 进度条时长/进度改为追随 Player 后，电视端仍有 00:00 无数据的问题：
 * preparePlayer() 重建引擎发生在 PlaySpec 建立之前，此时 PlaybackActivity.onPlayerRebuild()
 * 的归属判定为假，整段回调（含 setProgressPlayer）被跳过，CustomSeekView 会继续读已释放的旧引擎。
 * 这里钉住两条自持的内核重建路径都必须在重建后显式跟上新引擎。
 */
public class VideoActivitySeekProgressSourceTest {

    @Test
    public void historyKernelRebuildRebindsSeekView() throws Exception {
        String activity = readActivity();
        String body = methodBody(activity, "private int applyHistoryPlayerKernel(boolean forcePrepare)");

        int prepare = body.indexOf("player().preparePlayer(kernel, forcePrepare);");
        int rebind = body.indexOf("getSeekView().setProgressPlayer(player().getPlayer());", prepare);
        int setKernel = body.indexOf("setPlayerKernel();", prepare);

        assertTrue("applyHistoryPlayerKernel must still prepare the remembered kernel", prepare >= 0);
        assertTrue("the seek view must be rebound to the rebuilt engine, otherwise the TV progress bar "
                + "keeps reading the released player and shows 00:00", rebind > prepare);
        assertTrue("the rebind must happen right after preparePlayer(), before the rest of the kernel "
                + "switching work", setKernel > rebind);
    }

    @Test
    public void pendingKernelRebuildRebindsSeekView() throws Exception {
        String activity = readActivity();
        String body = methodBody(activity, "private void applyPendingPlayerKernel()");

        int guard = body.indexOf("if (!PlayerSetting.isPlayer(kernel)) return;");
        int prepare = body.indexOf("player().preparePlayer(kernel);", guard);
        int rebind = body.indexOf("getSeekView().setProgressPlayer(player().getPlayer());", prepare);

        assertTrue("applyPendingPlayerKernel must keep its kernel validation", guard >= 0);
        assertTrue("applyPendingPlayerKernel must still prepare the pending kernel", prepare > guard);
        assertTrue("the pending kernel rebuild happens after onServiceConnected() and before any PlaySpec "
                + "exists, so it must rebind the seek view on its own", rebind > prepare);
    }

    @Test
    public void rebindsGoThroughTheSharedSeekAccessor() throws Exception {
        String activity = readActivity();
        String accessor = methodBody(activity, "protected CustomSeekView getSeekView()");

        assertTrue("getSeekView() must keep returning the control bar seek view",
                accessor.contains("mBinding.control.seek"));
        assertFalse("the rebinds must go through getSeekView() rather than binding the seek view directly",
                methodBody(activity, "private int applyHistoryPlayerKernel(boolean forcePrepare)")
                        .contains("mBinding.control.seek"));
    }

    private static String readActivity() throws Exception {
        Path path = Path.of("src", "leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "VideoActivity.java");
        if (!Files.isRegularFile(path)) path = Path.of("app").resolve(path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("missing method: " + signature, start >= 0);
        int end = source.indexOf("\n    }", start);
        assertTrue("unterminated method: " + signature, end > start);
        return source.substring(start, end);
    }
}
