package com.fongmi.android.tv.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.google.android.material.textview.MaterialTextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = App.class, qualifiers = "w1280dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ThemeBinderDrawableBoundsTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 240;
    private static final int WALLPAPER = 0xFF182231;

    @Test
    public void playbackChipBordersStillEncloseTheirLabelsAfterThemeRebinding() throws Exception {
        Context context = themedContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(WALLPAPER);
        LinearLayout flags = row(root);
        String[] names = {"夸克原画#0102", "夸克无限#0102", "优视原画#0103",
                "优视无限#0103", "百度原画#0101", "百度无限#0101"};
        for (String name : names) addChip(flags, R.layout.adapter_flag, name);
        flags.getChildAt(5).setActivated(true);
        flags.getChildAt(5).setSelected(true);

        LinearLayout ranges = row(root);
        MaterialTextView reverse = new MaterialTextView(context);
        reverse.setText("倒序");
        reverse.setTextSize(16);
        reverse.setTextColor(0xFFFFFFFF);
        reverse.setGravity(android.view.Gravity.CENTER);
        reverse.setMinWidth(76);
        reverse.setBackgroundResource(R.drawable.selector_video_item);
        ranges.addView(reverse);
        addChip(ranges, R.layout.adapter_array, "1-20");
        addChip(ranges, R.layout.adapter_array, "21-36").setActivated(true);
        addChip(row(root), R.layout.adapter_flag, "很长的线路名称用于验证跑马灯按钮背景仍然覆盖整个可见标签#0102");

        ThemeBinder.bind(root, ThemeTokens.dark(), ThemeTokens.light());
        Bitmap initial = render(root);
        assertNotEquals("test must start with a visible full-width chip background", WALLPAPER,
                rightFillPixel(initial, (TextView) flags.getChildAt(0)));
        ThemeBinder.bind(root, ThemeTokens.light(), ThemeTokens.dark());
        Bitmap bound = draw(root);
        capture(bound);

        for (int r = 0; r < root.getChildCount(); r++) {
            LinearLayout row = (LinearLayout) root.getChildAt(r);
            for (int c = 0; c < row.getChildCount(); c++) {
                TextView chip = (TextView) row.getChildAt(c);
                Rect expected = new Rect(0, 0, chip.getWidth(), chip.getHeight());
                Drawable background = chip.getBackground();
                assertEquals(chip.getText() + ": background must cover the measured button",
                        expected, background.getBounds());
                assertEquals(chip.getText() + ": visible state border must cover the label",
                        expected, background.getCurrent().getBounds());
                assertEquals(chip.getText() + ": binding must not shrink the visible chip fill",
                        rightFillPixel(initial, chip), rightFillPixel(bound, chip));
            }
        }
    }

    @Test
    public void standaloneOverlayBackgroundRetainsItsMeasuredBounds() {
        Context context = themedContext();
        FrameLayout root = new FrameLayout(context);
        View item = new View(context);
        item.setBackgroundResource(R.drawable.shape_item_normal);
        root.addView(item, new FrameLayout.LayoutParams(320, 80));
        render(root);
        Rect expected = new Rect(0, 0, 320, 80);
        assertEquals(expected, item.getBackground().getBounds());

        ThemeBinder.bind(root, ThemeTokens.light(), ThemeTokens.dark());
        draw(root);
        assertEquals("overlay classification must not resize the live background",
                expected, item.getBackground().getBounds());
    }

    private static Context themedContext() {
        return new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_App);
    }

    private static LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(parent.getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 20;
        parent.addView(row, params);
        return row;
    }

    private static TextView addChip(LinearLayout row, int layout, String label) {
        TextView chip = (TextView) LayoutInflater.from(row.getContext()).inflate(layout, row, false);
        chip.setText(label);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) chip.getLayoutParams();
        params.rightMargin = 12;
        row.addView(chip, params);
        return chip;
    }

    private static Bitmap render(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, WIDTH, HEIGHT);
        return draw(root);
    }

    private static Bitmap draw(View root) {
        preDraw(root);
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static void preDraw(View view) {
        if (view instanceof TextView text) text.onPreDraw();
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) preDraw(group.getChildAt(i));
        }
    }

    private static int rightFillPixel(Bitmap bitmap, TextView chip) {
        View row = (View) chip.getParent();
        int x = row.getLeft() + chip.getRight() - 6;
        int y = row.getTop() + chip.getTop() + chip.getHeight() / 2;
        return bitmap.getPixel(x, y);
    }

    private static void capture(Bitmap bitmap) throws Exception {
        String directory = System.getenv("WEBHTV_BOUNDS_CAPTURE_DIR");
        if (directory == null || directory.isEmpty()) return;
        File target = new File(directory);
        if (!target.isDirectory() && !target.mkdirs()) {
            throw new java.io.IOException("Cannot create capture directory: " + target);
        }
        try (FileOutputStream stream = new FileOutputStream(new File(target, "playback-chips.png"))) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw new java.io.IOException("Cannot encode chip capture");
            }
        }
    }
}
