package com.fongmi.android.tv.theme;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.github.catvod.utils.Prefers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 行为级守卫：直播列表遮盖层是**整块统一**的，不是左浓右淡的渐变。
 *
 * <p>为什么需要它：上一版把三档渐变（0.80 / 0.40 / 0.05）整体缩放，滑块拉到 100% 时右端
 * 只从 5% 涨到 6%，用户实测「只有左边缘变黑，右边没变化」。源码断言看不出这件事——它必须
 * 把生产路径返回的 drawable 真正栅格化，逐点比对整块宽度上的 alpha。
 *
 * <p>本测试因此直接 {@link LiveListScrim#create(android.content.Context)} 取真实 drawable，
 * 画到 1xN 位图上，断言每一列 alpha 都相同，且数值等于滑块值本身。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = App.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LiveListScrimRenderTest {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 8;

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
    }

    @Test
    public void theShippedDefaultIsOneFlatEightyPercentBlack() {
        try (var host = Robolectric.buildActivity(Host.class).setup()) {
            ThemeController.applyFromPreferences(host.get());
            Bitmap bitmap = rasterize(LiveListScrim.create(host.get()));
            assertEveryColumnIs(bitmap, 0xCC, "the shipped default must be one flat 80% black");
        }
    }

    @Test
    public void theSliderAtOneHundredPercentBlacksOutTheWholeBlock() {
        applyOpacity(1f);
        try (var host = Robolectric.buildActivity(Host.class).setup()) {
            ThemeController.applyFromPreferences(host.get());
            Bitmap bitmap = rasterize(LiveListScrim.create(host.get()));
            assertEveryColumnIs(bitmap, 0xFF, "100% must black out the whole block, edge to edge");
        }
    }

    @Test
    public void theSliderAtTwentyPercentDimsTheWholeBlockEvenly() {
        applyOpacity(0.20f);
        try (var host = Robolectric.buildActivity(Host.class).setup()) {
            ThemeController.applyFromPreferences(host.get());
            Bitmap bitmap = rasterize(LiveListScrim.create(host.get()));
            assertEveryColumnIs(bitmap, 0x33, "20% must leave the whole block at 20% black");
        }
    }

    @Test
    public void theUnsetSlotRendersOneFlatBlockWithoutARightEdgeFade() {
        try (var host = Robolectric.buildActivity(Host.class).setup()) {
            ThemeController.applyFromPreferences(host.get());
            Bitmap bitmap = rasterize(LiveListScrim.create(host.get()));
            assertEquals("the left edge must equal the static shape_live_list colour",
                    0xCC, alphaAt(bitmap, 0));
            assertEquals("the right edge must not fade: it must equal the left edge",
                    alphaAt(bitmap, 0), alphaAt(bitmap, WIDTH - 1));
        }
    }

    private static void applyOpacity(float opacity) {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.light.liveListScrimOpacity = opacity;
        profile.dark.liveListScrimOpacity = opacity;
        assertTrue("the fixture profile must validate", ThemeProfileStore.apply(profile).success());
    }

    private static void assertEveryColumnIs(Bitmap bitmap, int expected, String message) {
        for (int x = 0; x < bitmap.getWidth(); x++) {
            assertEquals(message + " (column " + x + ")", expected, alphaAt(bitmap, x));
        }
    }

    private static int alphaAt(Bitmap bitmap, int x) {
        return bitmap.getPixel(x, bitmap.getHeight() / 2) >>> 24;
    }

    private static Bitmap rasterize(Drawable drawable) {
        assertNotNull("the scrim must exist for an available theme snapshot", drawable);
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, WIDTH, HEIGHT);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }
}
