package com.fongmi.android.tv.ui.helper;

import android.content.Context;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * 整窗左右环形焦点的纵向容器：左右方向只在同一水平带内找最近邻，
 * 走到该带尽头就回绕到最左/最右，形成一个闭环。
 * 可横向滚动的 RecyclerView（例如顶部仓库栏）仍然交给系统自己滚动，
 * 避免把尚未布局出来的列表项抢掉。
 */
public class FocusRingLayout extends LinearLayout {

    public FocusRingLayout(Context context) {
        super(context);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (Util.isLeanback() && event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                View focused = findFocus();
                if (focused != null) {
                    boolean right = code == KeyEvent.KEYCODE_DPAD_RIGHT;
                    if (!canScrollInside(focused, right)) {
                        View next = findNeighbor(focused, right);
                        if (next != null && next != focused) {
                            next.requestFocus();
                            return true;
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static boolean canScrollInside(View view, boolean right) {
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if (parent instanceof RecyclerView) {
                if (((RecyclerView) parent).canScrollHorizontally(right ? 1 : -1)) return true;
            }
            parent = ((View) parent).getParent();
        }
        return false;
    }

    private View findNeighbor(View current, boolean right) {
        List<View> candidates = new ArrayList<>();
        collect(this, current, candidates);
        if (candidates.isEmpty()) return null;
        Rect cur = bounds(current);
        if (cur.isEmpty()) return null;
        View nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        int nearestOffset = Integer.MAX_VALUE;
        for (View view : candidates) {
            Rect rect = bounds(view);
            if (rect.isEmpty() || !overlapsVertically(cur, rect)) continue;
            int distance = right ? rect.left - cur.right : cur.left - rect.right;
            if (distance < 0) continue;
            int offset = Math.abs(rect.centerY() - cur.centerY());
            if (distance < nearestDistance || (distance == nearestDistance && offset < nearestOffset)) {
                nearest = view;
                nearestDistance = distance;
                nearestOffset = offset;
            }
        }
        if (nearest != null) return nearest;
        // 接口列表和站点网格是两个独立 RecyclerView，行高不一致时同一水平带里可能没有对方：
        // 这里显式跨容器补一次（只按方向取最近的另一个容器项），保证左右能走到另一个列表。
        ViewGroup container = containerOf(current);
        View cross = null;
        int crossDistance = Integer.MAX_VALUE;
        int crossOffset = Integer.MAX_VALUE;
        for (View view : candidates) {
            ViewGroup other = containerOf(view);
            if (other == null || other == container) continue;
            // 只跨同一水平区域的容器（左接口列表/右站点网格）；顶部仓库栏是另一条横带，不参与。
            Rect otherBounds = bounds(other);
            if (otherBounds.isEmpty() || !overlapsVertically(cur, otherBounds)) continue;
            Rect rect = bounds(view);
            if (rect.isEmpty()) continue;
            int distance = right ? rect.left - cur.right : cur.left - rect.right;
            if (distance < 0) continue;
            int offset = Math.abs(rect.centerY() - cur.centerY());
            if (distance < crossDistance || (distance == crossDistance && offset < crossOffset)) {
                cross = view;
                crossDistance = distance;
                crossOffset = offset;
            }
        }
        if (cross != null) return cross;
        // 这一行到头了：回绕到同一水平带的最左/最右；没有同带项时回绕到整窗最左/最右。
        Rect bestRect = null;
        for (View view : candidates) {
            Rect rect = bounds(view);
            if (rect.isEmpty() || !overlapsVertically(cur, rect)) continue;
            if (bestRect == null || (right ? rect.left < bestRect.left : rect.right > bestRect.right)) {
                bestRect = rect;
                nearest = view;
            }
        }
        if (nearest != null) return nearest;
        for (View view : candidates) {
            Rect rect = bounds(view);
            if (rect.isEmpty()) continue;
            if (bestRect == null || (right ? rect.left < bestRect.left : rect.right > bestRect.right)) {
                bestRect = rect;
                nearest = view;
            }
        }
        return nearest;
    }

    private static ViewGroup containerOf(View view) {
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if (parent instanceof ViewGroup && parent instanceof RecyclerView) return (ViewGroup) parent;
            parent = ((View) parent).getParent();
        }
        return null;
    }

    private static boolean overlapsVertically(Rect a, Rect b) {
        return Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top) > 0;
    }

    private static Rect bounds(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return new Rect(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight());
    }

    private static void collect(View view, View skip, List<View> out) {
        if (view == null || view == skip || view.getVisibility() != View.VISIBLE || !view.isShown()) return;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), skip, out);
        } else if (view.isFocusable() && view.isEnabled()) {
            out.add(view);
        }
    }
}
