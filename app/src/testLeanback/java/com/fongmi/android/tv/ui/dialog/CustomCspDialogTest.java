package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogCustomCspBinding;
import com.fongmi.android.tv.setting.CustomCspSetting;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CustomCspDialogTest {

    private CustomCspDialog dialog;
    private DialogCustomCspBinding binding;
    private RecyclerView.Adapter<?> adapter;
    private List<CustomCspSetting.Item> items;

    @Before
    public void setUp() throws Exception {
        // Initialize only App's Gson/handler, without starting production services.
        new App();
        dialog = new CustomCspDialog();
        ContextThemeWrapper context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(),
                R.style.Theme_WebHTV_Dialog);
        binding = DialogCustomCspBinding.inflate(LayoutInflater.from(context));
        items = new ArrayList<>(Arrays.asList(live("Alpha"), live("Beta"), live("Gamma")));
        Class<?> type = Class.forName(CustomCspDialog.class.getName() + "$CspAdapter");
        Constructor<?> constructor = type.getDeclaredConstructor(CustomCspDialog.class, List.class);
        constructor.setAccessible(true);
        adapter = (RecyclerView.Adapter<?>) constructor.newInstance(dialog, items);
        ReflectionHelpers.setField(dialog, "binding", binding);
        ReflectionHelpers.setField(dialog, "adapter", adapter);
        ReflectionHelpers.setField(dialog, "registry", new CustomCspSetting.Registry());
        dialog.initEvent();
    }

    @Test
    public void deletingLastSearchResultShowsEmptyStateAndKeepsHiddenItems() {
        CustomCspSetting.Item alpha = items.get(0);
        CustomCspSetting.Item gamma = items.get(2);
        binding.siteSearch.setText("beta");
        assertEquals(1, adapter.getItemCount());
        assertEquals(View.GONE, binding.searchEmpty.getVisibility());

        remove(0);

        assertEquals(0, adapter.getItemCount());
        assertEquals(View.VISIBLE, binding.searchEmpty.getVisibility());
        assertEquals(Arrays.asList(alpha, gamma), items);
        binding.siteSearch.setText("");
        assertEquals(2, adapter.getItemCount());
        assertEquals(View.GONE, binding.searchEmpty.getVisibility());
    }

    @Test
    public void filteredReverseMappingEditsAndRemovesOriginalItems() {
        items.get(0).setName("Match Alpha");
        items.get(2).setName("Match Gamma");
        CustomCspSetting.Item hidden = items.get(1);
        binding.siteSearch.setText("match");
        assertEquals(2, adapter.getItemCount());
        ReflectionHelpers.callInstanceMethod(adapter, "setReverseOrder", ClassParameter.from(boolean.class, true));
        assertEquals(2, itemIndex(0));
        assertEquals(0, itemIndex(1));
        CustomCspSetting.Item replacement = live("Match Delta");
        ReflectionHelpers.callInstanceMethod(adapter, "replace", ClassParameter.from(int.class, itemIndex(0)),
                ClassParameter.from(CustomCspSetting.Item.class, replacement));
        assertSame(replacement, items.get(2));
        remove(1);
        assertSame(hidden, items.get(0));
        assertSame(replacement, items.get(1));
        binding.siteSearch.setText("nothing");
        assertEquals(0, adapter.getItemCount());
        binding.siteSearch.setText(" ");
        assertEquals(2, adapter.getItemCount());
        assertEquals(1, itemIndex(0));
    }

    @Test
    public void movementKeepsGranularNotificationsAndReverseCoordinates() {
        List<String> notifications = new ArrayList<>();
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { notifications.add("reset"); }
            @Override public void onItemRangeMoved(int from, int to, int count) {
                notifications.add("move:" + from + ":" + to + ":" + count);
            }
            @Override public void onItemRangeChanged(int start, int count) {
                notifications.add("change:" + start + ":" + count);
            }
        });
        CustomCspSetting.Item alpha = items.get(0);
        assertEquals(2, move(0, 2));
        assertSame(alpha, items.get(2));
        assertEquals(Arrays.asList("move:0:2:1", "change:0:3"), notifications);
        ReflectionHelpers.callInstanceMethod(adapter, "setReverseOrder", ClassParameter.from(boolean.class, true));
        notifications.clear();
        assertEquals(1, move(0, 1));
        assertSame(alpha, items.get(1));
        assertEquals(Arrays.asList("move:0:1:1", "change:0:2"), notifications);
    }

    @Test
    public void filteringBlocksReorderWithoutChangingStoredOrder() {
        List<CustomCspSetting.Item> original = new ArrayList<>(items);
        binding.siteSearch.setText("a");
        assertEquals(-1, move(0, 2));
        assertEquals(original, items);
        assertEquals(-1, itemIndex(-1));
        assertEquals(-1, itemIndex(3));
    }

    @Test
    public void searchUsesActualItemFieldsWithoutDroppingUnmatchedItems() {
        assertTrue(CustomCspSetting.matchesSearch(items.get(1), "BETA"));
        assertTrue(CustomCspSetting.matchesSearch(items.get(1), "example.com/Beta"));
        assertFalse(CustomCspSetting.matchesSearch(items.get(1), "missing"));
        binding.siteSearch.setText("beta");
        assertEquals(1, adapter.getItemCount());
        assertEquals(3, items.size());
    }

    private static CustomCspSetting.Item live(String name) {
        CustomCspSetting.Item item = new CustomCspSetting.Item();
        item.setKind("live");
        item.setName(name);
        item.setUrl("https://example.com/" + name + ".m3u8");
        return item;
    }

    private int itemIndex(int position) {
        return ReflectionHelpers.callInstanceMethod(adapter, "itemIndex", ClassParameter.from(int.class, position));
    }

    private int move(int from, int to) {
        return ReflectionHelpers.callInstanceMethod(adapter, "moveDisplay", ClassParameter.from(int.class, from),
                ClassParameter.from(int.class, to));
    }

    private void remove(int position) {
        ReflectionHelpers.callInstanceMethod(adapter, "remove", ClassParameter.from(int.class, position),
                ClassParameter.from(View.class, null));
    }
}
