package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 播放页「起播时用哪条线路」的回归锁（手机端 + 电视端 + 详情页三方口径）。
 *
 * <p>背景：线路选择以前只作为播放的副产物写进 {@code History.vodFlag}，而写库要过
 * {@code History.canSave()} 的 {@code position>0} 门槛。切线路后不一定重新起播（无明显集切换时
 * 不会走 onRefresh），进程被杀后重进就退回旧线路甚至第一条。修复把线路选择拆成两条互补的规则：
 *
 * <ol>
 *   <li><b>切一下就落盘</b>（{@code rememberFlagSelection}）：写独立偏好，不依赖播放进度；
 *       历史已存在（有进度）时同时同步历史快照，避免「播放器里显示的线路」与「重进后的线路」分叉；</li>
 *   <li><b>偏好必须能定位到这次要播的集</b>（{@code findPreferredFlag} 里的
 *       {@code containsEpisodeUrl} 守卫）：偏好是「这部剧选过哪条线路」，但播放请求可能指向另一集。
 *       守卫不成立时偏好让位给调用方按集 URL 的权威解算结果。</li>
 * </ol>
 *
 * <p>本测试只钉**不变式**（谁是权威、守卫是否覆盖两条查表路径、落盘是否与播放进度解耦），
 * 不钉具体实现形态，以便后续重构（例如把两处 findPreferredFlag 收敛成一个工具方法）时仍然成立。
 */
public class MobilePlaybackFlagResolutionContractTest {

    private static final String MOBILE = "app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java";
    private static final String LEANBACK = "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java";
    private static final String DETAIL = "app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java";

    @Test
    public void switchingALinePersistsTheChoiceWithoutWaitingForPlayback() throws Exception {
        for (String path : new String[]{MOBILE, LEANBACK}) {
            String body = methodBody(read(path), "private void rememberFlagSelection(Flag flag)");

            assertTrue(path + "：切线路必须写入独立偏好，不能只靠 History（它要 position>0 才落盘）",
                    body.contains("FlagPreferenceCache.get().put(getKey(), getId(), stableKey, flag.getFlag())"));
            assertTrue(path + "：偏好落盘必须离开主线程",
                    body.contains("Task.execute(() -> FlagPreferenceCache.get().save())"));
            assertTrue(path + "：索引未知时不得写稳定键，Flag.stableKey 会把 -1 夹成 0 留下必然命中首条的假键",
                    body.contains("index >= 0 ?") && body.contains(": \"\";"));
            assertTrue(path + "：写偏好不得依赖播放进度（否则等于没修）",
                    body.indexOf("FlagPreferenceCache.get().put(") < body.indexOf("canSave()"));
        }
    }

    @Test
    public void tappingALineGoesThroughThePersistencePath() throws Exception {
        for (String path : new String[]{MOBILE, LEANBACK}) {
            String body = methodBody(read(path), "public void onItemClick(Flag item)");

            assertTrue(path + "：点击线路必须落盘这次选择", body.contains("rememberFlagSelection(resolved);"));
            assertTrue(path + "：目标线路必须取自适配器实例（界面下标与数据下标可能不同）",
                    body.contains("mFlagAdapter.indexOf(item)"));
        }
    }

    @Test
    public void storedPreferenceIsGuardedByTheRequestedEpisodeUrl() throws Exception {
        for (String path : new String[]{MOBILE, LEANBACK}) {
            String body = methodBody(read(path), "private Flag findPreferredFlag(List<Flag> flags, String episodeUrl)");

            // 守卫必须作用在**结果**上（candidate 是所有查表分支共用的单一出口），
            // 而不是只包住其中一条路径——否则另一条路径能绕过它。
            assertTrue(path + "：偏好线路必须确实含这次要播的集，否则会劫持按 URL 的权威解算结果",
                    body.contains("candidate.containsEpisodeUrl(Episode.create(\"\", episodeUrl))"));
            assertTrue(path + "：URL 守卫必须覆盖全部查表路径（出现在线路名分支之后）",
                    body.indexOf("containsEpisodeUrl") > body.indexOf("flagName"));
            assertTrue(path + "：没有集 URL 可比对时应放行（否则任何偏好都用不上）",
                    body.contains("TextUtils.isEmpty(episodeUrl) || candidate.containsEpisodeUrl("));
            assertFalse(path + "：偏好不得返回未通过 URL 守卫的候选", body.contains("if (candidate == null) return candidate;"));
        }
    }

    @Test
    public void historyFallbackStillConsultsTheStoredPreference() throws Exception {
        for (String path : new String[]{MOBILE, LEANBACK}) {
            String body = methodBody(read(path), "private Flag resolveHistoryPlaybackFlag(List<Flag> flags)");
            int preferred = body.indexOf("findPreferredFlag(flags, episodeUrl)");

            assertTrue(path + "：历史回退必须先看显式偏好，再看 History 快照",
                    preferred > 0 && preferred < body.indexOf("selectPlaybackFlag("));
            assertTrue(path + "：没有偏好时仍要退回首条线路，不能返回 null 让调用方空指针",
                    body.contains("selected == null ? flags.get(0) : selected"));
        }
    }

    @Test
    public void preferenceCacheKeyMatchesTheDetailPageKeyShape() throws Exception {
        String video = read(MOBILE);
        String detail = read(DETAIL);

        // 详情页与播放页必须用同一套 (siteKey, vodId) 键，否则详情页切线路、播放页读不到。
        assertTrue("播放页偏好键必须是意图里的 key/id 原值",
                video.contains("Objects.toString(getIntent().getStringExtra(\"key\"), \"\")")
                        && video.contains("Objects.toString(getIntent().getStringExtra(\"id\"), \"\")"));
        assertTrue("详情页偏好键必须是意图里的 key/id 原值",
                detail.contains("Objects.toString(getIntent().getStringExtra(\"key\"), \"\")")
                        && detail.contains("Objects.toString(getIntent().getStringExtra(\"id\"), \"\")"));
        assertTrue("播放页与详情页的偏好查询必须指向同一份缓存",
                video.contains("FlagPreferenceCache.get().get(getKey(), getId())")
                        && detail.contains("FlagPreferenceCache.get().get(getKeyText(), getIdText())"));
    }

    @Test
    public void detailPageKeepsTheSamePriorityOrderItHadBeforeTheFix() throws Exception {
        String detail = methodBody(read(DETAIL), "private Flag findInitialFlag(List<Flag> flags)");

        // 详情页顺序：显式意图 → 落盘偏好 → 历史 → 首条。本轮不改详情页语义，仅锁住不被误动。
        assertTrue("显式意图必须最高优先",
                detail.contains("TmdbUIAdapter.selectPlaybackFlag(")
                        && detail.indexOf("TmdbUIAdapter.selectPlaybackFlag(") < detail.indexOf("findPreferredFlag(flags)"));
        assertTrue("落盘偏好必须高于历史快照与首条线路",
                detail.indexOf("findPreferredFlag(flags)") < detail.indexOf("history.getSourceBindingKey()")
                        && detail.indexOf("findPreferredFlag(flags)") < detail.indexOf("flags.get(0)"));
    }

    // ------------------------------------------------------------------ helpers

    /** 取方法体：先剥注释再按花括号配对，匿名内部类也能正确收尾。 */
    private static String methodBody(String source, String signature) {
        String cleaned = withoutJavaComments(source);
        int start = cleaned.indexOf(signature);
        assertTrue("必须存在方法 " + signature, start > 0);
        int open = cleaned.indexOf('{', start);
        assertTrue("方法签名后必须有方法体", open > 0);
        int depth = 0;
        for (int i = open; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return cleaned.substring(start, i + 1);
        }
        return cleaned.substring(start);
    }

    private static String withoutJavaComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
