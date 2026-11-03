package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 控制栏「倍速」按钮上下微调的契约。
 *
 * <p>用户报告：打开播放菜单后选中倍速，按遥控器 DOWN 会降速，按 UP 完全没有反应。
 *
 * <p>根因不在控件也不在播放器：{@code CustomUpDownView} 上下对称，{@code PlayerManager}
 * 的加减方向也正确。缺口在 Activity 层——控制栏样式只给了 DOWN 的邻居
 * （{@code Control.nextFocusDown=timeBar}），UP 方向在控制栏内没有可聚焦目标，
 * {@link com.fongmi.android.tv.ui.helper.PlayerControlFocusHelper#handleKey} 于是把 UP 判成
 * 「会把焦点甩出控制栏」并吞掉，事件根本到不了 {@code speed.setUpListener()}。
 *
 * <p>本契约钉住三件事：
 * <ol>
 *   <li>倍速按钮持有焦点时，UP 走 {@code onSpeedAdd()}、DOWN 走 {@code onSpeedSub()}，
 *       且两者都在门禁之前返回，不再依赖焦点搜索；</li>
 *   <li>接管发生在 {@code PlayerControlFocusHelper.handleKey} 之前，但排在片头/片尾之后
 *       ——片头/片尾的既有语义不能被抢；</li>
 *   <li>不靠放宽 helper 的吞键来解决：那是
 *       {@code timeBar.setNextFocusUpId} 回跳与片头快捷修复依赖的行为。</li>
 * </ol>
 */
public class SpeedAdjustKeyContractTest {

    private static final String VIDEO = "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java";
    private static final String HELPER = "app/src/main/java/com/fongmi/android/tv/ui/helper/PlayerControlFocusHelper.java";

    private static final String SPEED_SIGNATURE = "private boolean dispatchSpeedAdjust(KeyEvent event)";
    private static final String DISPATCH_SIGNATURE = "public boolean dispatchKeyEvent(KeyEvent event)";

    @Test
    public void speedButtonAdjustsOnBothArrows() throws Exception {
        String body = methodBody(read(VIDEO), SPEED_SIGNATURE);
        assertTrue("倍速按钮上的 UP 必须加档", body.contains("onSpeedAdd()"));
        assertTrue("倍速按钮上的 DOWN 必须减档", body.contains("onSpeedSub()"));
        int up = body.indexOf("KeyUtil.isUpKey(event)");
        int down = body.indexOf("KeyUtil.isDownKey(event)");
        assertTrue("必须分别识别上下键", up > 0 && down > 0);
        assertTrue("UP 分支必须排在 DOWN 之前", up < down);
        assertTrue("UP 命中后必须把事件吃掉", body.substring(up, down).contains("return true"));
        assertTrue("DOWN 命中后必须把事件吃掉", body.substring(down).contains("return true"));
    }

    @Test
    public void adjustmentOnlyRunsForTheFocusedSpeedButtonWhileControlsAreShown() throws Exception {
        String body = methodBody(read(VIDEO), SPEED_SIGNATURE);
        int actionDown = body.indexOf("KeyUtil.isActionDown(event)");
        int visible = body.indexOf("isVisible(mBinding.control.getRoot())");
        int focus = body.indexOf("mBinding.control.action.speed");
        int up = body.indexOf("KeyUtil.isUpKey(event)");
        assertTrue("必须只在按下时接管（长按重复也要走到这里，但抬起不能改倍速）", actionDown > 0);
        assertTrue("控制栏不可见时不得接管", visible > 0);
        assertTrue("必须要求焦点就是倍速按钮，否则会抢走别人（例如片头/片尾、字幕）", focus > 0);
        assertTrue("三道门禁必须排在方向判断之前", actionDown < up && visible < up && focus < up);
    }

    @Test
    public void speedAdjustmentIsDispatchedBeforeTheFocusFinderSwallowsUp() throws Exception {
        String dispatch = methodBody(read(VIDEO), DISPATCH_SIGNATURE);
        int opening = dispatch.indexOf("dispatchOpeningEndingAdjust(event)");
        int speed = dispatch.indexOf("dispatchSpeedAdjust(event)");
        int handle = dispatch.indexOf("PlayerControlFocusHelper.handleKey(");
        assertTrue("dispatchKeyEvent 必须调用 dispatchSpeedAdjust", speed > 0);
        assertTrue("倍速接管必须排在 PlayerControlFocusHelper.handleKey 之前，否则 UP 已被吞",
                speed < handle);
        assertTrue("接管必须真的生效，不能被条件短路掉（例如写成 false && ...）",
                dispatch.contains("if (dispatchSpeedAdjust(event)) return true;"));
        assertTrue("片头/片尾的接管必须仍在最前面，它们的上下键语义不能被倍速抢走",
                opening > 0 && opening < speed);
    }

    /**
     * 修在 Activity 的按键分发上，而不是把 helper 的「无邻居就吞键」打开一个口子。
     */
    @Test
    public void theFixDoesNotWeakenTheFocusFinderGuard() throws Exception {
        String helper = read(HELPER);
        String handleKey = methodBody(helper, "public static boolean handleKey(View root, View preferred, KeyEvent event)");
        assertTrue("无邻居时吞键的保护必须保留",
                handleKey.contains("if (next == null || !isDescendant(root, next)) return true;"));
        assertFalse("helper 不得为倍速开特例（那会让所有按钮的 UP 都往外甩）",
                withoutJavaComments(handleKey).contains("speed"));
    }

    // ------------------------------------------------------------------ helpers

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("必须存在方法 " + signature, start > 0);
        int end = source.indexOf("\n    }", start);
        return source.substring(start, end > 0 ? end : source.length());
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }

    /** 剥掉 Java 注释，避免负向断言命中说明文字（本仓库已踩过同类坑）。 */
    private static String withoutJavaComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }
}
