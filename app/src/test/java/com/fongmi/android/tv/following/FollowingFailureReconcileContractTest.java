package com.fongmi.android.tv.following;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 锁定「探针失败不得阻断追更进度重建」这一约定：失败早退分支必须补跑 reconcile，且必须在 update 之后。
 */
public class FollowingFailureReconcileContractTest {

    private static final String COORDINATOR = "app/src/main/java/com/fongmi/android/tv/following/FollowingUpdateCoordinator.java";
    private static final String CHECK_SIGNATURE = "boolean check(Following item, boolean manual, boolean foreground)";

    @Test
    public void failureBranchStillReconcilesProgress() throws Exception {
        // 探针探不到可用来源时进度会停更；失败记录同样要按历史重建进度，否则该源的 watchedEpisode 永久落后。
        String branch = failureBranch();

        assertTrue("失败早退分支必须补跑 reconcile", branch.contains("FollowingStore.reconcile(item);"));
    }

    @Test
    public void reconcileRunsAfterTheFailureWrite() throws Exception {
        // 顺序写反会让 update(item) 用内存里旧的 watchedEpisode 覆盖掉 reconcile 刚重建的进度。
        String branch = failureBranch();
        int updateIndex = branch.indexOf("FollowingStore.update(item);");
        int reconcileIndex = branch.indexOf("FollowingStore.reconcile(item);");

        assertTrue("失败分支应先把失败状态落库", updateIndex >= 0);
        assertTrue("失败分支应补跑 reconcile", reconcileIndex >= 0);
        assertTrue("reconcile 必须排在 update 之后", reconcileIndex > updateIndex);
    }

    @Test
    public void failureTextLiteralIsCentralized() throws Exception {
        // 文案对外逐字可见，测试已逐字锁定；集中成常量才能保证三处调用点取值一致。
        String coordinator = read(COORDINATOR);

        assertEquals("字面量只应出现在常量定义处", 1, occurrences(coordinator, "\"来源检查失败\""));
        assertTrue(coordinator.contains("static final String SOURCE_FAILED = \"来源检查失败\";"));
    }

    /** 截取失败早退分支：从 playableEpisode 判空起，到其后第一个 return false 为止。 */
    private static String failureBranch() throws Exception {
        String body = methodBody(read(COORDINATOR), CHECK_SIGNATURE);
        int start = body.indexOf("if (sourceSnapshot == null");
        assertTrue("missing failure branch", start >= 0);
        int end = body.indexOf("return false;", start);
        assertTrue("unterminated failure branch", end > start);
        return body.substring(start, end);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int i = source.indexOf(needle); i >= 0; i = source.indexOf(needle, i + needle.length())) count++;
        return count;
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
