package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.activity.VideoDanmakuOwnershipTest.TestApp;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.DanmakuTitle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = TestApp.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class DanmakuSearchOrderTest {

    private FragmentActivity activity;
    private DanmakuSearchDialog dialog;
    private RecyclerView recycler;

    private void open() {
        org.robolectric.android.controller.ActivityController<FragmentActivity> controller =
                Robolectric.buildActivity(FragmentActivity.class);
        activity = controller.get();
        activity.setTheme(R.style.Theme_WebHTV_Dialog);
        controller.setup();
        dialog = DanmakuSearchDialog.create().keyword("尚公主");
        dialog.showNow(activity.getSupportFragmentManager(), "order");
        recycler = ReflectionHelpers.getField(dialog, "recycler");
        TextView keyword = ReflectionHelpers.getField(dialog, "keywordView");
        keyword.setText("尚公主");
    }

    @Test
    public void togglingOrderKeepsExpandedGroupAndRestoresNumericAscendingOrder() {
        open();
        publish(item("tencent", "尚公主", "第10集"), item("tencent", "尚公主", "第2集"),
                item("tencent", "尚公主", "第1集"));
        clickRow(0);
        assertEquals(Arrays.asList(1, 2, 10), episodes());

        TextView order = orderButton();
        assertTrue(order.isFocusable());
        order.performClick();
        assertEquals(Arrays.asList(10, 2, 1), episodes());
        assertEquals(4, recycler.getAdapter().getItemCount());
        assertTrue(label(0).startsWith("[-]"));

        order.performClick();
        assertEquals(Arrays.asList(1, 2, 10), episodes());
    }

    @Test
    public void descendingOrderPersistsAcrossSourcesAndNewResultsAndKeepsUnknownLast() {
        open();
        publish(item("tencent", "尚公主", "第1集"), item("tencent", "尚公主", "第10集"),
                item("hongguo", "尚公主", "第2集"), item("hongguo", "尚公主", "第12集"));
        orderButton().performClick();
        List<TextView> sources = ReflectionHelpers.getField(dialog, "sourceViews");
        sources.get(1).performClick();
        clickRow(0);
        assertEquals(Arrays.asList(12, 2), episodes());

        publish(item("tencent", "尚公主", "第2集"), item("tencent", "尚公主", "第10集"),
                item("tencent", "尚公主", "特别篇"));
        clickRow(0);
        assertEquals(Arrays.asList(10, 2, DanmakuTitle.EPISODE_UNKNOWN), episodes());
    }

    @Test
    public void sortingDoesNotExpandCollapsedGroupsOrReverseTitleRanking() {
        open();
        publish(item("tencent", "尚公主", "第1集"), item("tencent", "尚公主", "第2集"),
                item("tencent", "其他剧", "第1集"), item("tencent", "其他剧", "第2集"));
        String first = label(0);
        assertTrue(first.contains("尚公主"));
        String second = label(1);
        orderButton().performClick();
        assertEquals(2, recycler.getAdapter().getItemCount());
        assertEquals(first, label(0));
        assertEquals(second, label(1));
        clickRow(0);
        assertEquals(Arrays.asList(2, 1), episodes());
    }

    @Test
    public void emptyResultsHideOrderAndSingleItemsAreSafe() {
        open();
        publish(item("tencent", "尚公主", "第1集"));
        TextView order = orderButton();
        order.performClick();
        assertEquals(Arrays.asList(1), episodes());
        ReflectionHelpers.callInstanceMethod(dialog, "showProgress");
        assertEquals(View.GONE, order.getVisibility());
        publish();
        assertEquals(View.GONE, order.getVisibility());
    }

    @Test
    public void ascendingInitialResultsSortFlatItemsAfterSingleGroupsAreAdded() {
        open();
        publish(item("tencent", "尚公主", "第10集"), item("tencent", "其他剧", "第2集"));
        assertEquals(Arrays.asList(2, 10), episodes());
        assertEquals(activity.getString(R.string.setting_order_normal), orderButton().getText().toString());
    }

    @Test
    public void ascendingNewResultsSortFlatItemsAfterSingleGroupsAreAdded() {
        open();
        publish(item("tencent", "尚公主", "第1集"));
        publish(item("tencent", "尚公主", "第10集"), item("tencent", "其他剧", "第2集"));
        assertEquals(Arrays.asList(2, 10), episodes());
    }

    @Test
    public void ascendingFlatOrderAfterRoundTripSurvivesNewResults() {
        open();
        publish(item("tencent", "尚公主", "第10集"), item("tencent", "其他剧", "第2集"));
        TextView order = orderButton();
        order.performClick();
        assertEquals(Arrays.asList(10, 2), episodes());
        order.performClick();
        assertEquals(Arrays.asList(2, 10), episodes());

        publish(item("tencent", "尚公主", "第10集"), item("tencent", "其他剧", "第2集"));
        assertEquals(Arrays.asList(2, 10), episodes());
        assertEquals(activity.getString(R.string.setting_order_normal), order.getText().toString());
    }

    @Test
    public void ascendingFlatOrderPersistsAcrossSources() {
        open();
        publish(item("tencent", "尚公主", "第10集"), item("tencent", "其他剧", "第2集"),
                item("hongguo", "尚公主", "第20集"), item("hongguo", "其他剧", "第3集"));
        TextView order = orderButton();
        order.performClick();
        order.performClick();
        assertEquals(Arrays.asList(2, 10), episodes());

        List<TextView> sources = ReflectionHelpers.getField(dialog, "sourceViews");
        sources.get(1).performClick();
        assertEquals(Arrays.asList(3, 20), episodes());
        sources.get(0).performClick();
        assertEquals(Arrays.asList(2, 10), episodes());
        assertEquals(activity.getString(R.string.setting_order_normal), order.getText().toString());
    }

    @Test
    public void unknownFlatItemStaysLastAcrossRebuildsAndBothDirections() {
        open();
        Danmaku unknown = Danmaku.from("https://example.com/tencent/special");
        unknown.setName("(2026)【电视剧】 from tencent - 特别篇");
        assertNull("The unknown fixture must take the ungrouped path", DanmakuTitle.titleKey(unknown));
        assertEquals("tencent", unknown.getSourceName());
        assertEquals(DanmakuTitle.EPISODE_UNKNOWN, DanmakuTitle.getEpisode(unknown));
        publish(item("tencent", "尚公主", "第10集"), item("tencent", "其他剧", "第2集"), unknown);
        assertEquals(Arrays.asList(2, 10, DanmakuTitle.EPISODE_UNKNOWN), episodes());
        assertEquals(unknown.getName(), label(2));

        TextView order = orderButton();
        order.performClick();
        assertEquals(Arrays.asList(10, 2, DanmakuTitle.EPISODE_UNKNOWN), episodes());
        assertEquals(unknown.getName(), label(2));
        order.performClick();
        assertEquals(Arrays.asList(2, 10, DanmakuTitle.EPISODE_UNKNOWN), episodes());
        publish(item("tencent", "尚公主", "第10集"), item("tencent", "其他剧", "第2集"), unknown);
        assertEquals(Arrays.asList(2, 10, DanmakuTitle.EPISODE_UNKNOWN), episodes());
        assertEquals(unknown.getName(), label(2));
    }

    @Test
    public void descendingNewResultsSortFlatItemsAfterSingleGroupsAreAdded() {
        open();
        publish(item("tencent", "尚公主", "第1集"));
        orderButton().performClick();
        publish(item("tencent", "尚公主", "第2集"), item("tencent", "其他剧", "第10集"));
        assertEquals(Arrays.asList(10, 2), episodes());
    }
    @Test
    public void remoteUpFromKeywordFocusesOrderWithoutChangingSearchText() {
        open();
        publish(item("tencent", "尚公主", "第1集"));
        ReflectionHelpers.callInstanceMethod(dialog, "bindEvents");
        TextView keyword = ReflectionHelpers.getField(dialog, "keywordView");
        assertTrue(keyword.requestFocus());
        keyword.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP));
        assertTrue("Remote UP must move focus from the input to the order button", orderButton().isFocused());
        assertEquals("尚公主", keyword.getText().toString());
    }
    @Test
    public void remoteUpKeepsInputFocusWhenOrderIsHiddenDuringSearch() {
        open();
        ReflectionHelpers.callInstanceMethod(dialog, "bindEvents");
        ReflectionHelpers.callInstanceMethod(dialog, "showProgress");
        TextView keyword = ReflectionHelpers.getField(dialog, "keywordView");
        assertTrue(keyword.requestFocus());
        keyword.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP));
        assertTrue(keyword.isFocused());
    }
    private Danmaku item(String source, String title, String episode) {
        Danmaku item = Danmaku.from("https://example.com/" + source + "/" + title + "/" + episode);
        item.setName(title + "(2026)【电视剧】 from " + source + " - " + episode);
        return item;
    }

    private void publish(Danmaku... items) {
        ReflectionHelpers.callInstanceMethod(dialog, "onSuccess",
                ReflectionHelpers.ClassParameter.from(List.class, new ArrayList<>(Arrays.asList(items))));
    }

    private TextView orderButton() {
        TextView order = findText(dialog.requireDialog().getWindow().getDecorView(),
                activity.getString(R.string.setting_order_normal));
        assertNotNull("Search results must provide an ascending/descending control", order);
        return order;
    }

    private TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private RecyclerView.ViewHolder holder(int position) {
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter =
                (RecyclerView.Adapter<RecyclerView.ViewHolder>) recycler.getAdapter();
        RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, adapter.getItemViewType(position));
        adapter.bindViewHolder(holder, position);
        return holder;
    }

    private String label(int position) {
        return ((TextView) holder(position).itemView).getText().toString();
    }

    private void clickRow(int position) {
        recycler.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST));
        recycler.layout(0, 0, 1080, recycler.getMeasuredHeight());
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
        assertNotNull(holder);
        holder.itemView.performClick();
    }

    private List<Integer> episodes() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < recycler.getAdapter().getItemCount(); i++) {
            if (recycler.getAdapter().getItemViewType(i) == 1) result.add(DanmakuTitle.parseEpisode(label(i)));
        }
        return result;
    }
}
