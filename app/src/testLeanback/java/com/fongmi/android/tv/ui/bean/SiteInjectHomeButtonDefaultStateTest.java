package com.fongmi.android.tv.ui.bean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.HomeButton;
import com.github.catvod.utils.Prefers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.Map;

/**
 * 真实执行 {@link HomeButton} 的默认按钮逻辑，锁定“电视版个性设置 → 首页按钮”里
 * 站点注入（id 9）默认不勾选、但仍可由用户手动启用。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = App.class)
public class SiteInjectHomeButtonDefaultStateTest {

    private static final String KEY_BUTTON = "home_button";
    private static final String KEY_SORTED = "home_button_sorted";

    @Before
    public void clearSavedSelection() {
        Prefers.remove(KEY_BUTTON);
        Prefers.remove(KEY_SORTED);
    }

    @Test
    public void freshInstallDoesNotSelectSiteInjection() {
        List<HomeButton> selected = HomeButton.getButtons();
        assertFalse("站点注入必须默认不开启（个性设置 → 首页按钮不得默认勾选）", contains(selected, R.string.home_custom_csp));
        assertFalse("默认首页按钮列表不得显示站点注入", contains(HomeButton.getVisibleButtons(), R.string.home_custom_csp));
        assertTrue("默认值仍必须保留其余既有按钮", contains(selected, R.string.home_live) && contains(selected, R.string.home_search)
                && contains(selected, R.string.home_keep) && contains(selected, R.string.home_push)
                && contains(selected, R.string.home_setting));
    }

    @Test
    public void siteInjectionStaysSelectableInTheButtonDialog() {
        assertTrue("站点注入必须仍出现在“个性设置 → 首页按钮”目录中以便手动启用", contains(HomeButton.sortedAll(), R.string.home_custom_csp));
        assertTrue(contains(HomeButton.all(), R.string.home_custom_csp));
    }

    @Test
    public void userCanStillEnableSiteInjectionManually() {
        Map<Integer, HomeButton> selected = HomeButton.getButtonsMap();
        selected.put(siteInjection().getId(), siteInjection());
        HomeButton.save(selected);

        assertTrue("用户手动勾选后必须显示站点注入", contains(HomeButton.getButtons(), R.string.home_custom_csp));
        assertTrue(contains(HomeButton.getVisibleButtons(), R.string.home_custom_csp));
    }

    @Test
    public void legacySavedSelectionKeepingSiteInjectionIsPreserved() {
        HomeButton.save(HomeButton.getMap(List.of(siteInjection())));

        assertEquals(1, HomeButton.getButtons().size());
        assertTrue("升级用户已保存的列表不得被强制改写", contains(HomeButton.getButtons(), R.string.home_custom_csp));
    }

    @Test
    public void resetFallsBackToTheDefaultWithoutSiteInjection() {
        Map<Integer, HomeButton> selected = HomeButton.getButtonsMap();
        selected.put(siteInjection().getId(), siteInjection());
        HomeButton.save(selected);
        assertTrue(contains(HomeButton.getButtons(), R.string.home_custom_csp));

        HomeButton.reset();

        assertFalse("重置后必须回到站点注入默认不开启", contains(HomeButton.getButtons(), R.string.home_custom_csp));
    }

    private static HomeButton siteInjection() {
        for (HomeButton button : HomeButton.all()) if (button.getResId() == R.string.home_custom_csp) return button;
        throw new IllegalStateException("站点注入按钮不在 HomeButton.all() 目录中");
    }

    private static boolean contains(List<HomeButton> buttons, int resId) {
        for (HomeButton button : buttons) if (button.getResId() == resId) return true;
        return false;
    }
}
