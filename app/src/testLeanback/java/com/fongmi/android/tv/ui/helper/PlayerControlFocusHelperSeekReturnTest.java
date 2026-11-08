package com.fongmi.android.tv.ui.helper;

import android.app.Activity;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.appcompat.view.ContextThemeWrapper;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 电视端控制栏「从按钮 DOWN 到进度条，再 UP 回到刚才那颗按钮」的控件级证据。
 *
 * <p>用户报告：遥控焦点从控制栏按钮按 DOWN 落到进度条后，再按 UP 没有回到原来那颗按钮。
 * 根因是 FocusFinder 只认进度条（{@code timeBar}）自己的 nextFocusUpId，而控制栏只把它
 * 固定指向下一集（或干脆不设，退化成几何搜索）。
 *
 * <p>本测试用真实 {@link View} 语义验证两件事：
 * <ol>
 *   <li>{@link PlayerControlFocusHelper#seekReturnTarget} 的判定：哪颗控件可以被记成回来目标，
 *       哪些情况必须放弃写入（否则会把记忆覆盖成进度条自身）；</li>
 *   <li>{@link PlayerControlFocusHelper#handleKey} 在遥控按键路径上确实做了这次同步，
 *       且焦点落在进度条上时不会覆盖记忆。</li>
 * </ol>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = App.class)
public class PlayerControlFocusHelperSeekReturnTest {

    private Context context;
    private LinearLayout controls;
    private FrameLayout seek;
    private View timeBar;
    private View action;
    private View decode;

    @Before
    public void setUp() {
        context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_App);
        controls = new LinearLayout(context);
        controls.setId(View.generateViewId());

        // 真实容器 CustomSeekView 在构造函数里 setFocusable(true) 并把 requestFocus 转发给 timeBar，
        // 因此这里的容器必须同样可聚焦，否则「容器不得覆盖记忆」的断言会因为「不可聚焦」而恒真。
        seek = new FrameLayout(context);
        seek.setId(R.id.seek);
        seek.setFocusable(true);
        seek.setFocusableInTouchMode(false);
        timeBar = focusable(R.id.timeBar);
        seek.addView(timeBar);

        action = focusable(R.id.playerNext);
        decode = focusable(R.id.playerDecode);

        controls.addView(seek);
        controls.addView(action);
        controls.addView(decode);
    }

    @Test
    public void buttonAboveTheSeekBecomesTheReturnTarget() {
        assertSame("从「下集」落到进度条后，UP 必须回到「下集」",
                action, PlayerControlFocusHelper.seekReturnTarget(timeBar, controls, action));
        assertSame("用户从哪颗按钮下来，就回到哪颗按钮，而不是固定第一颗",
                decode, PlayerControlFocusHelper.seekReturnTarget(timeBar, controls, decode));
    }

    @Test
    public void seekItselfNeverOverwritesTheRememberedTarget() {
        assertNull("焦点落在进度条（timeBar）上时不得覆盖记忆，否则 UP 目标会变成进度条自身",
                PlayerControlFocusHelper.seekReturnTarget(timeBar, controls, timeBar));
        assertTrue("反空转前置：容器必须可聚焦，否则下面这条断言会因「不可聚焦」而恒真", seek.isFocusable());
        assertNull("焦点落在进度条容器上时同样不得覆盖记忆",
                PlayerControlFocusHelper.seekReturnTarget(timeBar, controls, seek));
    }

    @Test
    public void focusableSeekContainerIsNeverWrittenAsItsOwnUpTarget() {
        PlayerControlFocusHelper.syncSeekReturnTarget(controls, seek);
        assertEquals("可聚焦的进度条容器不能成为进度条自己的 UP 目标，否则 UP 会被转发回进度条、表现为按了没反应",
                View.NO_ID, timeBar.getNextFocusUpId());
    }

    @Test
    public void realCustomSeekViewIsNotWrittenAsItsOwnReturnTarget() {
        LinearLayout bar = new LinearLayout(context);
        bar.setId(View.generateViewId());
        CustomSeekView realSeek = new CustomSeekView(context);
        bar.addView(realSeek);
        View realTimeBar = realSeek.findViewById(R.id.timeBar);

        assertTrue("真实 CustomSeekView 必须可聚焦（构造函数 setFocusable(true)）", realSeek.isFocusable());
        assertNull("真实进度条容器不得成为回来目标",
                PlayerControlFocusHelper.seekReturnTarget(realTimeBar, bar, realSeek));

        PlayerControlFocusHelper.syncSeekReturnTarget(bar, realSeek);
        assertEquals("真实 CustomSeekView 不得被写成进度条自己的 UP 目标",
                View.NO_ID, realTimeBar.getNextFocusUpId());
    }

    /**
     * 实测 {@link CustomSeekView} 的焦点语义，说明为什么「容器成为回来目标」在当前代码里
     * 不可达（即 {@code seekReturnTarget} 的祖先方向是契约加固而非可达缺陷修复）：
     *
     * <ul>
     *   <li>{@code timeBar} 可聚焦时：{@code requestFocus} 委托给它，{@code findFocus()} 返回
     *       {@code timeBar}，容器自己拿不到焦点；</li>
     *   <li>{@code timeBar} 不可聚焦（手机版布局形状）时：委托分支直接返回 {@code false}，
     *       **不会**回退到 {@code super.requestFocus()}，容器仍然拿不到焦点。</li>
     * </ul>
     *
     * <p>这条测试是防退化锁：一旦上游把委托改成「失败就回退」，容器就会变成可达的焦点持有者，
     * 此时 {@link PlayerControlFocusHelper#seekReturnTarget} 的祖先方向就从加固变成必需。
     */
    @Test
    public void containerNeverHoldsFocusBecauseRequestFocusDelegatesToTheSeekBar() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout bar = new LinearLayout(context);
        bar.setId(View.generateViewId());
        CustomSeekView realSeek = new CustomSeekView(context);
        bar.addView(realSeek);
        activity.setContentView(bar);
        View realTimeBar = realSeek.findViewById(R.id.timeBar);

        assertTrue("前置：真实 CustomSeekView 可聚焦", realSeek.isFocusable());
        assertTrue("前置：进度条可聚焦（电视版布局形状）", realTimeBar.isFocusable());
        assertTrue("前置：容器能拿到焦点（实际转发给进度条）", realSeek.requestFocus());
        assertSame("可聚焦时焦点落在进度条上，容器不是 findFocus() 的结果",
                realTimeBar, bar.findFocus());

        realTimeBar.setFocusable(false);
        realTimeBar.setFocusableInTouchMode(false);
        realSeek.clearFocus();
        assertFalse("进度条不可聚焦时委托分支直接失败，不会回退到容器自己", realSeek.requestFocus());
        assertNull("因此容器始终不会成为 findFocus() 的结果", bar.findFocus());
    }

    /**
     * 手机版形状的树**不是** no-op：{@code timeBar} 仍然存在于布局里（只是不可聚焦），
     * 所以 {@code syncSeekReturnTarget} 会照常执行并把可聚焦按钮写进去；
     * 真正让手机端无观感变化的是「写入的目标不可聚焦」，而不是「函数没跑」。
     */
    @Test
    public void mobileShapedTreeIsNotANoop() {
        LinearLayout bar = new LinearLayout(context);
        bar.setId(View.generateViewId());
        CustomSeekView realSeek = new CustomSeekView(context);
        bar.addView(realSeek);
        View realTimeBar = realSeek.findViewById(R.id.timeBar);
        realTimeBar.setFocusable(false);
        View fullscreen = focusable(View.generateViewId());
        bar.addView(fullscreen);

        PlayerControlFocusHelper.syncSeekReturnTarget(bar, fullscreen);

        assertEquals("函数在手机版形状的树上照常执行（不是 no-op），只是目标不可聚焦所以无观感变化",
                fullscreen.getId(), realTimeBar.getNextFocusUpId());
    }

    @Test
    public void focusOutsideTheControlBarIsIgnored() {
        View outside = focusable(View.generateViewId());
        assertNull("控制栏之外的焦点不得成为进度条的 UP 落点",
                PlayerControlFocusHelper.seekReturnTarget(timeBar, controls, outside));
    }

    @Test
    public void idlessOrUnfocusableViewsAreIgnored() {
        View idless = focusable(View.NO_ID);
        assertNull("没有 id 的控件无法写进 nextFocusUpId，必须放弃",
                PlayerControlFocusHelper.seekReturnTarget(timeBar, controls, idless));

        View notFocusable = new View(context);
        notFocusable.setId(View.generateViewId());
        controls.addView(notFocusable);
        assertNull("不可聚焦的控件不能成为 UP 落点",
                PlayerControlFocusHelper.seekReturnTarget(timeBar, controls, notFocusable));
    }

    @Test
    public void syncWritesTheFocusedButtonIntoTheSeekUpTarget() {
        PlayerControlFocusHelper.syncSeekReturnTarget(controls, decode);
        assertEquals("进度条的 UP 目标必须被改写为刚才停留的那颗按钮",
                decode.getId(), timeBar.getNextFocusUpId());
    }

    @Test
    public void syncKeepsTheMemoryWhileTheSeekItselfIsFocused() {
        PlayerControlFocusHelper.syncSeekReturnTarget(controls, decode);
        PlayerControlFocusHelper.syncSeekReturnTarget(controls, timeBar);
        PlayerControlFocusHelper.syncSeekReturnTarget(controls, seek);
        assertEquals("焦点已经在进度条上时不得把记忆覆盖成进度条自身",
                decode.getId(), timeBar.getNextFocusUpId());
    }

    @Test
    public void syncWithoutASeekBarIsANoop() {
        LinearLayout bare = new LinearLayout(context);
        bare.setId(View.generateViewId());
        bare.addView(focusable(View.generateViewId()));
        PlayerControlFocusHelper.syncSeekReturnTarget(bare, decode);
        assertEquals("没有进度条的控制栏不得被写入任何 UP 目标", View.NO_ID, timeBar.getNextFocusUpId());
    }

    @Test
    public void remoteKeyPathSyncsTheReturnTargetBeforeAnyFocusMove() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setContentView(controls);
        assertTrue("测试前置：焦点必须先停在按钮上", action.requestFocus());

        PlayerControlFocusHelper.handleKey(controls, action, key(KeyEvent.KEYCODE_DPAD_DOWN));

        assertEquals("遥控按键路径必须在焦点移走之前把回来目标记好",
                action.getId(), timeBar.getNextFocusUpId());
    }

    @Test
    public void remoteKeyPathKeepsTheMemoryWhileTheSeekIsFocused() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setContentView(controls);
        assertTrue("测试前置：焦点必须先停在按钮上", decode.requestFocus());
        PlayerControlFocusHelper.handleKey(controls, decode, key(KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals(decode.getId(), timeBar.getNextFocusUpId());

        assertTrue("测试前置：焦点必须能落到进度条上", timeBar.requestFocus());
        PlayerControlFocusHelper.handleKey(controls, decode, key(KeyEvent.KEYCODE_DPAD_UP));

        assertEquals("进度条上按 UP 时不能把回来目标改写成进度条自身",
                decode.getId(), timeBar.getNextFocusUpId());
    }


    private View focusable(int id) {
        View view = new View(context);
        view.setId(id);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        return view;
    }

    private static KeyEvent key(int keyCode) {
        return new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
    }
}
