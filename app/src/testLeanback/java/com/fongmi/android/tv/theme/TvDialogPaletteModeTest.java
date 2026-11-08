package com.fongmi.android.tv.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.dialog.LightDialog;
import com.github.catvod.utils.Prefers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * TV 对话框面板必须与它自己的前景取自同一张表。
 *
 * <p>{@code LightDialog} 家族（「新增点播」{@code ConfigDialog}、{@code AppearanceDialog}、
 * {@code SpeedDialog} …）的面板由 {@link LightDialog#shell} 从
 * {@link ThemeController#current()} 画出，而面板里的文字来自 XML 的 {@code ?attr/color*}
 * 与 {@code @color/webhtv_color_*}，即由 Activity 解析出的编译表。两者一旦取自不同的表，
 * 就是「浅底浅字」：设备实测（1920×1080，系统日间模式，TV 产品默认强制深色）面板
 * {@code #E7E8EF}（日表）配夜表前景，正文 1.06:1、提示 1.39:1、描边按钮 1.41:1。
 *
 * <p>根因是取表来源：{@code AppCompatDelegate.setDefaultNightMode} 改写的是它代理的
 * Activity 的资源，API &lt; 31 不会改写 Application 自己的资源；而 TV 侧
 * {@code applyNightModeToApp()} 按产品默认强制 {@code MODE_NIGHT_YES}。于是
 * 「应用上下文 + 原始系统夜间位」在日间模式的电视上判成浅色，而实际 inflate 出来的全是深色。
 * 这里断言的就是「必须按 Activity 实际解析出的表来取色」这条不变量。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = App.class)
public class TvDialogPaletteModeTest {

    public static class Host extends AppCompatActivity {
        @Override
        protected void onCreate(android.os.Bundle state) {
            setTheme(R.style.Theme_App);
            super.onCreate(state);
        }
    }

    @Before
    public void reset() {
        Prefers.getPrefers().edit().clear().commit();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    @Test
    public void dialogShellMatchesTheTableTheActivityInflated() {
        // TV 产品默认强制深色：设备本身处于日间模式时，Activity 仍解析 values-night。
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        try (var host = Robolectric.buildActivity(Host.class).setup()) {
            ThemeController.applyFromPreferences(host.get());
            int inflated = ContextCompat.getColor(host.get(), R.color.webhtv_color_surface_container_high);
            assertEquals("fixture: the TV activity must inflate the dark table",
                    ThemeTokens.dark().colorSurfaceContainerHigh(), inflated);
            assertEquals("the dialog shell must come from the table its foregrounds come from",
                    inflated, fill(LightDialog.shell(host.get())));
        }
    }

    @Test
    public void addVodDialogForegroundsMeetWcagAaOnTheShell() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        try (var host = Robolectric.buildActivity(Host.class).setup()) {
            ThemeController.applyFromPreferences(host.get());
            int panel = fill(LightDialog.shell(host.get()));
            assertAtLeastAa(host.get(), panel, R.color.webhtv_color_on_surface, "info text");
            assertAtLeastAa(host.get(), panel, R.color.webhtv_color_on_surface_variant, "field hints");
            assertAtLeastAa(host.get(), panel, R.color.webhtv_color_primary, "outlined action text");
        }
    }

    /**
     * 取表必须跟着「真正 inflate 的那个 context」走，而不是跟着进程级的应用上下文走。
     *
     * <p>Robolectric 会把 {@code setDefaultNightMode} 也应用到应用上下文，真机 API &lt; 31 不会，
     * 所以只靠上面的 Activity 断言在 JVM 里复现不出设备现象。这条用显式构造的日/夜 context
     * 把「探针跟着入参 context」这条机制钉死，与设备实测互为补充。
     */
    @Test
    public void paletteDecisionFollowsTheInflatingContext() {
        Context app = App.get();
        assertEquals("fixture: the day context must resolve the light table",
                ThemeTokens.light().colorSurfaceContainerHigh(),
                ContextCompat.getColor(withNight(app, false), R.color.webhtv_color_surface_container_high));
        assertEquals("fixture: the night context must resolve the dark table",
                ThemeTokens.dark().colorSurfaceContainerHigh(),
                ContextCompat.getColor(withNight(app, true), R.color.webhtv_color_surface_container_high));
        assertEquals(Boolean.TRUE, ThemeController.compiledDarkPalette(withNight(app, true)));
        assertEquals(Boolean.FALSE, ThemeController.compiledDarkPalette(withNight(app, false)));
        assertTrue(ThemeController.resolvedDark(withNight(app, true)));
        assertFalse(ThemeController.resolvedDark(withNight(app, false)));
    }

    private static Context withNight(Context base, boolean night) {
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        int mode = night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | mode;
        return base.createConfigurationContext(configuration);
    }

    private static void assertAtLeastAa(AppCompatActivity host, int panel, int foreground, String what) {
        double ratio = ThemeContrast.ratio(ContextCompat.getColor(host, foreground), panel);
        assertTrue(what + " must reach WCAG AA 4.5:1 on the dialog shell, measured " + ratio + ":1", ratio >= 4.5);
    }

    private static int fill(Drawable drawable) {
        assertTrue("unexpected shell drawable: " + drawable, drawable instanceof GradientDrawable);
        GradientDrawable shape = (GradientDrawable) drawable;
        return shape.getColor().getDefaultColor();
    }
}
