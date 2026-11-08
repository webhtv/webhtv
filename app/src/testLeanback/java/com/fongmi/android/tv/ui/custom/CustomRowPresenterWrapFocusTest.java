package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.RowPresenter;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/**
 * 钉住 {@link CustomRowPresenter#setWrapFocus(boolean)} 的两条对外契约：
 * 边界键被消费时焦点必须真的换到另一端，以及行无法环绕时不得吞掉按键。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = com.fongmi.android.tv.App.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CustomRowPresenterWrapFocusTest {

    private FrameLayout host;
    private HorizontalGridView grid;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar);
        host = new FrameLayout(activity);
        activity.setContentView(host);
    }

    @Test
    public void wrapFocusConsumesTheEdgeKeyAndMovesToTheOtherEnd() {
        bind(3);
        grid.setSelectedPosition(2);
        assertTrue(dispatchKey(KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(0, grid.getSelectedPosition());
        assertTrue(grid.findViewHolderForAdapterPosition(0).itemView.hasFocus());
    }

    @Test
    public void wrapFocusWrapsBackwardsFromTheFirstItem() {
        bind(3);
        grid.setSelectedPosition(0);
        assertTrue(dispatchKey(KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals(2, grid.getSelectedPosition());
    }

    @Test
    public void wrapFocusLeavesANonEdgeKeyToTheGrid() {
        bind(3);
        grid.setSelectedPosition(1);
        assertFalse(dispatchKey(KeyEvent.KEYCODE_DPAD_RIGHT));
    }

    @Test
    public void aRowThatCannotWrapDoesNotSwallowTheEdgeKey() {
        bind(1);
        grid.setSelectedPosition(0);
        assertEquals(0, grid.getSelectedPosition());
        assertFalse(dispatchKey(KeyEvent.KEYCODE_DPAD_RIGHT));
    }

    // 注：「目标项尚未布局」那条路径（grid 自己滚动后再换焦）在 Robolectric 下无法稳定构造，
    // 当前环境里 setSelectedPosition 不会真正产生滚动布局，故不为它写会空转的用例，留待真机验证。

    private void bind(int count) {
        TestablePresenter presenter = new TestablePresenter(16);
        presenter.setWrapFocus(true);
        RowPresenter.ViewHolder holder = presenter.bindRow(host);
        host.addView(holder.view);
        grid = ((ListRowPresenter.ViewHolder) holder).getGridView();
        grid.setAdapter(new ItemsAdapter(count));
        layout();
    }

    private void layout() {
        host.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, 800, 200);
    }

    /**
     * {@code RowPresenter.onCreateViewHolder} 返回的是内部 ContainerViewHolder，拿不到行内 grid；
     * 子类可在同族内直接走 createRowViewHolder + initializeRowViewHolder 这条真实初始化路径。
     */
    private static final class TestablePresenter extends CustomRowPresenter {

        TestablePresenter(int spacing) {
            super(spacing);
        }

        RowPresenter.ViewHolder bindRow(ViewGroup parent) {
            RowPresenter.ViewHolder holder = createRowViewHolder(parent);
            initializeRowViewHolder(holder);
            return holder;
        }
    }

    private boolean dispatchKey(int keyCode) {
        return grid.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
    }

    private static final class ItemsAdapter extends RecyclerView.Adapter<ItemsAdapter.ItemHolder> {

        private final int count;

        ItemsAdapter(int count) {
            this.count = count;
        }

        @NonNull
        @Override
        public ItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = new View(parent.getContext());
            view.setFocusable(true);
            view.setLayoutParams(new ViewGroup.LayoutParams(118, 58));
            return new ItemHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ItemHolder holder, int position) {
        }

        @Override
        public int getItemCount() {
            return count;
        }

        private static final class ItemHolder extends RecyclerView.ViewHolder {

            ItemHolder(View itemView) {
                super(itemView);
            }
        }
    }
}
