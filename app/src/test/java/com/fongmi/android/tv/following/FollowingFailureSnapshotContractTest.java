package com.fongmi.android.tv.following;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FollowingFailureSnapshotContractTest {

    @Test
    public void failurePathNeverTouchesThePlayableSnapshot() throws Exception {
        // 失败路径只写 lastError/退避时间；一旦它改写可播快照，卡片会把失败伪装成正常或掩盖真实可播集数。
        String snippet = methodBody(read("app/src/main/java/com/fongmi/android/tv/following/FollowingUpdatePolicy.java"), "static void markFailure");

        assertFalse(snippet.contains("playable"));
        assertFalse(snippet.contains("lastProbeAt"));
    }

    @Test
    public void probeCatchRecordsSourceErrorWithoutInventingPlayableCounts() throws Exception {
        // 探针抛异常时必须落库来源侧原因（供 composeLastError 取用），但不得伪造可播集数。
        String snippet = methodBody(read("app/src/main/java/com/fongmi/android/tv/following/FollowingUpdateCoordinator.java"), "private ProbeOutcome probeBestEffort");
        int catchIndex = snippet.indexOf("catch (Throwable error)");
        assertTrue(catchIndex >= 0);
        String catchSnippet = snippet.substring(catchIndex);

        assertTrue(catchSnippet.contains("source.lastError"));
        assertTrue(catchSnippet.contains("source.lastProbeAt"));
        assertFalse(catchSnippet.contains("playable"));
    }

    @Test
    public void probeFailedUsesTheNoPlayablePredicate() throws Exception {
        // 回归锁定：零可播集数必须走 probeYieldedNoPlayable，而不是只看 snapshot==null。
        String coordinator = read("app/src/main/java/com/fongmi/android/tv/following/FollowingUpdateCoordinator.java");

        assertTrue(coordinator.contains("static boolean probeYieldedNoPlayable(FollowingSourceSnapshot snapshot)"));
        assertTrue(coordinator.contains("probeYieldedNoPlayable(probed.snapshot)"));
        assertTrue(coordinator.contains("sourceSnapshot.playableEpisode <= 0"));
    }

    @Test
    public void uiFailureJudgementNoLongerDependsOnLastProbeAt() throws Exception {
        // 订阅导入会预写 lastProbeAt，用它判失败会让从未探过的源显示「检查失败」；
        // 卡片与筛选必须共用 sourceCheckFailed，且判据不得回退成 lastProbeAt>0。
        // 注意：FollowingActivity 追更下一季时会写 lastProbeAt=0 重置快照，那是数据复位而非判据，
        // 所以这里锁定的是「判据表达式」，不是全文件禁用该字段。
        String adapter = read("app/src/main/java/com/fongmi/android/tv/ui/adapter/FollowingAdapter.java");
        String activity = read("app/src/main/java/com/fongmi/android/tv/ui/activity/FollowingActivity.java");

        assertTrue(adapter.contains("FollowingUpdateCoordinator.sourceCheckFailed(item.lastError)"));
        assertTrue(activity.contains("case FILTER_FAILED -> FollowingUpdateCoordinator.sourceCheckFailed(item.lastError);"));
        assertTrue(activity.contains("FollowingUpdateCoordinator.sourceCheckFailed(item.lastError)"));
        assertFalse(adapter.contains("source.lastProbeAt > 0"));
        assertFalse(activity.contains("source.lastProbeAt > 0"));
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("missing signature: " + signature, start >= 0);
        int end = source.indexOf("\n    }\n", start);
        assertTrue("unterminated method: " + signature, end > start);
        return source.substring(start, end);
    }

    private static String read(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path) && relative.startsWith("app/")) path = Path.of(relative.substring(4));
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
