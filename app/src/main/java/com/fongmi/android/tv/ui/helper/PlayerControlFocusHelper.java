package com.fongmi.android.tv.ui.helper;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import com.fongmi.android.tv.R;

public final class PlayerControlFocusHelper {

    private PlayerControlFocusHelper() {
    }

    /**
     * 明确传入的目标优先；只有当调用方没有给出可用目标时，才保留 root 内已有的焦点。
     * 若「root 里已经有焦点」就提前返回，唤出控制栏时想恢复「上次选择的那个地方」
     * 会被 root 中恰好持有的其它焦点静默覆盖，焦点看起来“自己跑到别的按钮上”。
     */
    public static boolean ensureFocus(View root, View preferred) {
        if (!isVisible(root)) return false;
        View target = firstFocusable(preferred);
        if (target != null && isDescendant(root, target)) return target.requestFocus();
        View current = root.findFocus();
        if (isFocusable(current) && isDescendant(root, current)) return true;
        View fallback = firstFocusable(root);
        return fallback != null && fallback.requestFocus();
    }

    public static boolean handleKey(View root, View preferred, KeyEvent event) {
        if (!isVisible(root) || event == null || !isFocusKey(event)) return false;
        if (!containsFocus(root)) {
            ensureFocus(root, preferred);
            return true;
        }
        syncSeekReturnTarget(root);
        if (!isDirectionKey(event) || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        View focus = root.findFocus();
        View next = focus == null ? null : focus.focusSearch(direction(event));
        if (next == null || !isDescendant(root, next)) return true;
        return false;
    }

    /**
     * 进度条按 UP 时应该回到的控件。
     *
     * <p>焦点已经落在进度条自己身上时返回 {@code null}：此时必须保留上一次的「回来目标」，
     * 否则目标会被改写成 seek 自身，UP 就只能靠几何搜索乱跳。
     *
     * <p>判定必须覆盖「焦点在 seek 子树内」与「焦点是 seek 的祖先」两个方向：进度条的容器
     * {@code CustomSeekView} 自身在构造函数里 {@code setFocusable(true)}，只是当前它的
     * {@code requestFocus} 会把请求转发给 {@code timeBar}，所以容器自己拿不到焦点。一旦上游把该
     * 转发改成「失败就回退到 {@code super.requestFocus}」，容器就会成为可达的焦点持有者；
     * 那时若把它写成 UP 目标，焦点会被转发回进度条，表现为「按 UP 没反应」。这里把两个方向
     * 都拒掉，使该不变不依赖上游委托细节（由
     * {@code containerNeverHoldsFocusBecauseRequestFocusDelegatesToTheSeekBar} 锁住前提）。
     *
     * <p>焦点不在控制栏内、或者控件没有 id / 不可聚焦时同样返回 {@code null}（保留现状）。
     */
    public static View seekReturnTarget(View seek, View controls, View focused) {
        if (seek == null || controls == null || focused == null) return null;
        if (focused.getId() == View.NO_ID || !focused.isFocusable()) return null;
        if (isDescendant(seek, focused) || isDescendant(focused, seek)) return null;
        if (!isDescendant(controls, focused)) return null;
        return focused;
    }

    /**
     * 让进度条的 UP 目标跟着「刚才停留的那颗按钮」走。
     *
     * <p>电视端控制栏里从按钮按 DOWN 落到进度条后，再按 UP 应当回到用户刚才停留的那颗按钮；
     * 而 FocusFinder 只认进度条自己（{@code timeBar}）的 nextFocusUpId，所以每收到一个遥控键、
     * 且焦点还停在控制栏的某颗控件上时，就把它改写成那颗控件。焦点已经在进度条上时保持不动
     * （见 {@link #seekReturnTarget}）。目标之后被隐藏时 FocusFinder 会跳过它并回退到几何搜索，
     * 因此不需要清理失效记忆。
     */
    public static void syncSeekReturnTarget(View root) {
        if (root == null) return;
        syncSeekReturnTarget(root, root.findFocus());
    }

    static void syncSeekReturnTarget(View root, View focus) {
        if (root == null) return;
        View timeBar = root.findViewById(R.id.timeBar);
        if (timeBar == null) return;
        View target = seekReturnTarget(timeBar, root, focus);
        if (target != null) timeBar.setNextFocusUpId(target.getId());
    }

    public static boolean containsFocus(View root) {
        if (!isVisible(root)) return false;
        View focus = root.findFocus();
        return isFocusable(focus) && isDescendant(root, focus);
    }

    public static boolean isDescendant(View root, View child) {
        for (View current = child; current != null; ) {
            if (current == root) return true;
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static View firstFocusable(View view) {
        if (!isVisible(view) || !view.isEnabled()) return null;
        if (view.isFocusable()) return view;
        if (!(view instanceof ViewGroup group)) return null;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = firstFocusable(group.getChildAt(i));
            if (child != null) return child;
        }
        return null;
    }

    private static boolean isFocusKey(KeyEvent event) {
        return isDirectionKey(event) || isConfirmKey(event);
    }

    private static boolean isDirectionKey(KeyEvent event) {
        int keyCode = event.getKeyCode();
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private static boolean isConfirmKey(KeyEvent event) {
        int keyCode = event.getKeyCode();
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private static int direction(KeyEvent event) {
        return switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT;
            default -> View.FOCUS_FORWARD;
        };
    }

    private static boolean isFocusable(View view) {
        return isVisible(view) && view.isEnabled() && view.isFocusable();
    }

    private static boolean isVisible(View view) {
        return view != null && view.getVisibility() == View.VISIBLE && view.isShown();
    }
}
