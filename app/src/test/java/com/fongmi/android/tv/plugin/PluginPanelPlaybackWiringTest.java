package com.fongmi.android.tv.plugin;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 面板的播放态接线守卫（纯 JVM，读源码文本）。
 *
 * <p>为什么用源码断言而不是行为测试：暂停/恢复发生在 {@code PlaybackActivity} 的真实播放器上，
 * 纯 JVM 里没有 Looper、没有 ExoPlayer；真机用例只能证明「点了会关」，证明不了「关了就恢复」。
 * 接线一旦被后来的重构悄悄拿掉，用户侧的表现是「视频永远停在暂停态」——静默、且只有真实播放中才会遇到。
 * 这里把三条关键接线钉死在文本上：打开时暂停、失败即恢复、dismiss 时恢复且只恢复一次。
 */
public class PluginPanelPlaybackWiringTest {

    private static final String SHOW_HEAD = "public static void show(@Nullable FragmentActivity activity, @Nullable String title, @Nullable String html,";
    private static final String DISMISS_HEAD = "public void onDismiss(@NonNull DialogInterface dialog)";

    @Test
    public void panelPausesHostPlaybackBeforeItOpens() throws Exception {
        String dialog = dialog();
        String show = methodBody(dialog, SHOW_HEAD);

        assertTrue("面板必须在打开前暂停宿主播放", show.contains("activity instanceof PlaybackActivity"));
        assertTrue("面板必须在打开前暂停宿主播放", show.contains("pauseForOverlayPlayback()"));
        assertTrue("暂停结果必须进 Bundle，不能只留字段", show.contains("args.putBoolean(ARG_RESUME_PARENT, resumeParent);"));
        assertTrue("暂停结果必须进 Bundle，不能只留字段", show.contains("dialog.setArguments(args);"));
        assertTrue("事务提交失败必须立刻恢复播放", show.contains("resumeAfterOverlayPlayback(resumeParent)"));
        assertTrue("恢复必须发生在 catch 里，而不是无条件执行",
                show.contains("catch (RuntimeException e)"));
        assertTrue("恢复必须在 catch 分支之后",
                show.indexOf("catch (RuntimeException e)") < show.indexOf("resumeAfterOverlayPlayback(resumeParent)"));
    }

    @Test
    public void panelResumesHostPlaybackExactlyOnceOnDismiss() throws Exception {
        String dialog = dialog();
        String dismiss = methodBody(dialog, DISMISS_HEAD);

        assertTrue("dismiss 必须恢复播放", dismiss.contains("resumeAfterOverlayPlayback(resumeParent)"));
        assertTrue("恢复必须有 parentResumed 守卫", dismiss.contains("if (!parentResumed)"));
        assertTrue("守卫必须先置位再恢复", dismiss.contains("parentResumed = true;"));
        assertTrue("配置变更时旧 Activity 不该恢复播放", dismiss.contains("isChangingConfigurations()"));
        assertFalse("恢复对象不许来自字段", dismiss.contains("owner"));
        assertTrue("先恢复播放，再还焦点",
                dismiss.indexOf("resumeAfterOverlayPlayback(resumeParent)") < dismiss.indexOf("closed.run()"));
        assertFalse("不该为播放态新开 public 方法", dialog.contains("public void pauseForOverlayPlayback"));
        assertFalse("不该为播放态新开 public 方法", dialog.contains("public void resumeForOverlayPlayback"));
    }

    /**
     * 本轮返工的核心：恢复状态必须活过 Fragment 重建。
     *
     * <p>旋屏时 FragmentManager 用无参构造器重建 Fragment，字段值不会被重放。若「是否要恢复」
     * 存在字段里，重建后的实例会以为自己从未暂停过播放，关闭面板时静默跳过恢复，视频永久停在
     * 暂停帧——用户只能自己按播放键，且没有任何错误可见。
     */
    @Test
    public void resumeIntentSurvivesFragmentRecreation() throws Exception {
        String dialog = dialog();
        String dismiss = methodBody(dialog, DISMISS_HEAD);
        String initView = methodBody(dialog, "protected void initView()");

        assertFalse("不许再用字段持有 Activity（旋屏后为 null，且泄漏 Activity）",
                dialog.contains("private PlaybackActivity playback"));
        assertTrue("恢复意图必须有 Bundle key", dialog.contains("ARG_RESUME_PARENT"));
        assertTrue("打开时写入 Bundle", dialog.contains("setArguments("));
        assertTrue("重建后必须回读 Bundle", initView.contains("getArguments()"));
        assertTrue("重建后必须回读 Bundle", initView.contains("getBoolean(ARG_RESUME_PARENT, false)"));
        assertTrue("恢复对象必须取自 getActivity()",
                dismiss.contains("instanceof PlaybackActivity") || dismiss.contains("(PlaybackActivity) activity"));
        assertTrue("恢复对象必须取自 getActivity()", dismiss.contains("getActivity()"));
    }

    @Test
    public void playbackWiringDidNotDisturbPanelContracts() throws Exception {
        String dialog = dialog();

        assertTrue("四参 show 的签名不许变", dialog.contains(SHOW_HEAD));
        assertTrue("三参重载必须继续转调四参", dialog.contains("show(activity, title, html, null);"));
        assertTrue(dialog.contains("WebViewUtil.configureBase(view, TAG);"));
        assertTrue(dialog.contains("WebViewUtil.configureHome(view);"));
        assertTrue(dialog.contains("view.loadDataWithBaseURL(null, html == null"));
        assertTrue("电视端焦点通道不许丢", dialog.contains("pluginPanelClose.setNextFocusDownId(R.id.pluginPanelWeb)"));
        assertTrue("电视端焦点通道不许丢", dialog.contains("pluginPanelWeb.setNextFocusUpId(R.id.pluginPanelClose)"));
    }

    @Test
    public void playbackActivityStillProvidesTheOverlayContract() throws Exception {
        String playback = read("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "activity", "PlaybackActivity.java");

        assertTrue(playback.contains("public final boolean pauseForOverlayPlayback()"));
        assertTrue(playback.contains("public final void resumeAfterOverlayPlayback(boolean shouldResume)"));
    }

    private static String dialog() throws Exception {
        return read("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "PluginPanelDialog.java");
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing method: " + signature, start >= 0);
        int open = source.indexOf('{', start);
        assertTrue("Missing method body: " + signature, open >= 0);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value == '{') depth++;
            if (value == '}' && --depth == 0) return source.substring(open + 1, i);
        }
        throw new AssertionError("Unclosed method: " + signature);
    }

    private static String read(String... parts) throws Exception {
        Path root = Files.exists(Path.of("src", "main")) ? Path.of("") : Path.of("app");
        return Files.readString(root.resolve(Path.of("", parts)), StandardCharsets.UTF_8);
    }
}
