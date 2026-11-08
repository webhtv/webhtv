package com.fongmi.android.tv.ui.adapter;

import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = App.class)
public class QuickAdapterSelectionTest {

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve(".git"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("repository root not found");
        return path;
    }

    @Test
    public void quickAdapterWiresCurrentSiteThroughDialogAndDetail() throws Exception {
        Path root = repoRoot();
        String adapterSource = read(root.resolve("app/src/leanback/java/com/fongmi/android/tv/ui/adapter/QuickAdapter.java"));
        String dialog = read(root.resolve("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/QuickSearchDialog.java"));
        String activity = read(root.resolve("app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java"));

        assertTrue("adapter must track the current site key", adapterSource.contains("private String currentSiteKey = \"\";"));
        assertTrue("adapter must expose the current site key", adapterSource.contains("public void setCurrentSiteKey(String currentSiteKey)"));
        assertTrue("adapter must activate the current site item", adapterSource.contains("holder.binding.getRoot().setActivated(TextUtils.equals(currentSiteKey, item.getSiteKey()))"));
        assertTrue("dialog must accept the current site key", dialog.contains("public QuickSearchDialog currentSiteKey(String currentSiteKey)"));
        assertTrue("dialog must initialize the adapter", dialog.contains("adapter.setCurrentSiteKey(currentSiteKey);"));
        assertTrue("activity must pass the active site key", activity.contains("invokeQuiet(dialog, \"currentSiteKey\", new Class<?>[]{String.class}, getKeyText())"));
        assertFalse("current state must not use marquee selected state",
                adapterSource.contains("holder.binding.getRoot().setSelected(TextUtils.equals(currentSiteKey, item.getSiteKey()))"));
    }

    @Test
    public void boundCurrentSiteStaysActivatedAndOtherSitesDoNot() {
        App application = App.get();
        // The quick switcher layout resolves its text colours through semantic
        // ?attr/colorOnSurface, which only exists under the app theme; a bare
        // application context would fail to inflate it.
        ContextThemeWrapper themed = new ContextThemeWrapper(application, R.style.Theme_App);
        RecyclerView recycler = new RecyclerView(themed);
        recycler.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        recycler.setLayoutManager(new LinearLayoutManager(themed));

        QuickAdapter adapter = new QuickAdapter(item -> { });
        adapter.setWidth(300);
        adapter.setCurrentSiteKey("current");
        adapter.addAll(List.of(vod("current"), vod("other")));
        recycler.setAdapter(adapter);
        layoutRecycler(recycler);

        assertTrue(recycler.findViewHolderForAdapterPosition(0).itemView.isActivated());
        assertFalse(recycler.findViewHolderForAdapterPosition(1).itemView.isActivated());

        adapter.setCurrentSiteKey("other");
        layoutRecycler(recycler);
        assertTrue(recycler.findViewHolderForAdapterPosition(1).itemView.isActivated());
        assertFalse(recycler.findViewHolderForAdapterPosition(0).itemView.isActivated());
    }

    private static void layoutRecycler(RecyclerView recycler) {
        ShadowLooper.idleMainLooper();
        recycler.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        recycler.layout(0, 0, 600, 300);
    }

    private static Vod vod(String siteKey) {
        Site site = new Site();
        site.setKey(siteKey);
        site.setName(siteKey);
        Vod vod = new Vod();
        vod.setSite(site);
        vod.setName("title");
        vod.setRemarks("remark");
        return vod;
    }
}
