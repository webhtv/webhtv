package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class InterfaceEntryInteractionTest {

    @Test
    public void managerShowsAllConfigsAndAnExplicitAddAction() throws Exception {
        for (String file : new String[]{
                "app/src/mobile/java/com/fongmi/android/tv/ui/dialog/HistoryDialog.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/dialog/HistoryDialog.java"}) {
            String source = read(file);
            assertTrue(file, source.contains("public HistoryDialog manage()"));
            assertTrue(file, source.contains("protectCurrent(manage).addAll(type, getConfig())"));
            assertTrue(file, source.contains("binding.add.setVisibility(manage ? View.VISIBLE : View.GONE)"));
            assertTrue(file, source.contains("private void onAdd()"));
        }
    }

    @Test
    public void managerKeepsCurrentConfigVisibleButProtectsUseAndDelete() throws Exception {
        for (String file : new String[]{
                "app/src/mobile/java/com/fongmi/android/tv/ui/dialog/HistoryDialog.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/dialog/HistoryDialog.java"}) {
            assertTrue(file, read(file).contains("protectCurrent(manage).addAll(type, getConfig())"));
        }
        for (String file : new String[]{
                "app/src/mobile/java/com/fongmi/android/tv/ui/adapter/ConfigAdapter.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/adapter/ConfigAdapter.java"}) {
            String source = read(file);
            assertTrue(file, source.contains("holder.binding.delete.setVisibility(readOnly ? View.GONE : View.VISIBLE);"));
            assertTrue(file, source.contains("holder.binding.delete.setAlpha(current ? 0.38f : 1f);"));
            assertTrue(file, source.contains("if (!current) listener.onTextClick(item);"));
        }
    }

    @Test
    public void leanbackCurrentConfigRemainsFocusableWithoutAllowingReuse() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/adapter/ConfigAdapter.java");

        assertTrue(source.contains("holder.binding.text.setEnabled(true)"));
        assertTrue(source.contains("holder.binding.text.setFocusable(true)"));
        assertTrue(source.contains("if (!current) listener.onTextClick(item);"));
        assertTrue(source.contains("bindVerticalFocus(holder.binding.text, position)"));
        assertTrue(source.contains("KEYCODE_DPAD_DOWN"));
        assertTrue(source.contains("recycler.stopScroll()"));
        assertTrue(source.contains("recycler.addOnChildAttachStateChangeListener(this)"));
        assertTrue(source.contains("onChildViewAttachedToWindow(@NonNull View view)"));
        assertTrue(source.contains("recycler.getChildAdapterPosition(view) == position"));
        assertTrue(source.contains("recycler.removeOnChildAttachStateChangeListener(this)"));
        assertTrue(source.contains("scrollToPositionWithOffset(position, recycler.getPaddingTop())"));
        assertTrue(source.contains("recycler.postOnAnimation(focus::run)"));
        assertFalse(source.contains("smoothScrollToPosition(position)"));
        assertFalse(source.contains("postDelayed(focus"));
        assertFalse(source.contains("holder.binding.text.setFocusable(!current)"));
    }

    @Test
    public void leanbackConfigActionsKeepTheirColumnAndVisibleFocusBorder() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/ui/adapter/ConfigAdapter.java");
        String layout = read("app/src/leanback/res/layout/adapter_config.xml");
        String icon = read("app/src/leanback/res/color/config_history_icon.xml");
        String normal = read("app/src/leanback/res/drawable/shape_config_history_item_normal.xml");
        String focused = read("app/src/leanback/res/drawable/shape_config_history_item_focused.xml");

        assertTrue(source.contains("requestFocus(recycler, position, source.getId())"));
        assertTrue(source.contains("holder.itemView.findViewById(targetId)"));
        assertTrue(source.contains("target = holder.itemView.findViewById(R.id.text)"));
        assertTrue(layout.contains("android:id=\"@+id/edit\""));
        assertTrue(layout.contains("android:id=\"@+id/delete\""));
        assertTrue(layout.contains("android:contentDescription=\"@string/setting_delete\""));
        assertTrue(icon.contains("?attr/colorOnPrimary"));
        assertTrue(normal.contains("?attr/colorOutline"));
        assertTrue(focused.contains("android:width=\"2dp\""));
        assertTrue(focused.contains("?attr/colorOnPrimary"));
    }

    @Test
    public void leanbackConfigNameLooksClickableBeforeItReceivesFocus() throws Exception {
        String layout = read("app/src/leanback/res/layout/adapter_config.xml");
        String buttonBackground = read("app/src/main/res/color/dialog_outlined_button_bg.xml");
        String buttonStroke = read("app/src/main/res/color/dialog_outlined_button_stroke.xml");

        assertTrue(layout.contains("com.google.android.material.button.MaterialButton"));
        assertTrue(layout.contains("style=\"?attr/materialButtonOutlinedStyle\""));
        assertTrue(layout.contains("android:textColor=\"@color/dialog_outlined_button_text\""));
        assertTrue(layout.contains("app:backgroundTint=\"@color/dialog_outlined_button_bg\""));
        assertTrue(layout.contains("app:strokeColor=\"@color/dialog_outlined_button_stroke\""));
        assertTrue(layout.contains("app:strokeWidth=\"2dp\""));
        assertTrue(buttonBackground.contains("android:color=\"#D2E3FC\""));
        assertTrue(buttonStroke.contains("android:color=\"#C8CDD2\""));
        assertTrue(buttonStroke.contains("android:color=\"#0B57D0\""));
    }

    @Test
    public void newConfigStartsBlankWhileEditingKeepsTheSelectedConfig() throws Exception {
        for (String file : new String[]{
                "app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java",
                "app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java"}) {
            String source = read(file);
            assertTrue(file, source.contains("if (config == null) config = edit ? getConfig() : Config.create(type);"));
        }
    }

    @Test
    public void newConfigInsertsAfterCombiningPrimaryAndBackupAddresses() throws Exception {
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java");
        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java");
        assertTrue(mobile.contains(".urls(addresses(url, addresses)).insert().update()"));
        assertTrue(leanback.contains(".urls(addresses(text, addresses)).insert().update()"));
    }

    @Test
    public void settingsRowsOpenTheManagerInsteadOfUsingLongPressAsEdit() throws Exception {
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingFragment.java");
        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingActivity.java");
        String appearance = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingAppearanceActivity.java");
        for (String source : new String[]{mobile, leanback}) {
            assertTrue(source.contains("HistoryDialog.create().vod().manage().show(this)"));
            assertTrue(source.contains("HistoryDialog.create().live().manage().show(this)"));
            assertTrue(source.contains("HistoryDialog.create().wall().manage().show(this)"));
            assertFalse(source.contains("setOnLongClickListener(this::onVodEdit)"));
            assertFalse(source.contains("setOnLongClickListener(this::onLiveEdit)"));
            assertFalse(source.contains("setOnLongClickListener(this::onWallEdit)"));
        }
        assertTrue(appearance.contains("HistoryDialog.create().wall().manage().show(this)"));
        assertFalse(appearance.contains("setOnLongClickListener(this::onWallEdit)"));
    }

    @Test
    public void bothHistoryLayoutsExposeTheAddButton() throws Exception {
        for (String file : new String[]{
                "app/src/mobile/res/layout/dialog_history.xml",
                "app/src/leanback/res/layout/dialog_history.xml"}) {
            assertTrue(file, read(file).contains("android:id=\"@+id/add\""));
        }
    }

    @Test
    public void leanbackInterfaceDialogsUseAdaptiveScreenDimensionsAndEditableName() throws Exception {
        String history = read("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/HistoryDialog.java");
        String config = read("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java");
        String layout = read("app/src/leanback/res/layout/dialog_config.xml");

        assertTrue(history.contains("ResUtil.getScreenWidth(requireContext())"));
        assertTrue(history.contains("ResUtil.getScreenHeight(requireContext())"));
        assertTrue(history.contains("int width = screenWidth - ResUtil.dp2px(48)"));
        assertTrue(history.contains("window.getDecorView().setPadding(0, 0, 0, 0)"));
        assertTrue(history.contains("window.setLayout(params.width, params.height)"));
        assertTrue(config.contains("ResUtil.getScreenWidth(requireContext())"));
        assertTrue(config.contains("ResUtil.getScreenHeight(requireContext())"));
        assertTrue(config.contains("int width = screenWidth - ResUtil.dp2px(48)"));
        assertTrue(config.contains("window.getDecorView().setPadding(0, 0, 0, 0)"));
        assertTrue(config.contains("window.setLayout(params.width, params.height)"));
        assertTrue(layout.contains("<com.fongmi.android.tv.ui.custom.CustomEditText\n            android:id=\"@+id/name\""));
        assertFalse(layout.contains("android:id=\"@+id/name\"\n            android:layout_width=\"wrap_content\""));
        assertTrue(layout.contains("android:nextFocusDown=\"@id/text\""));
        assertTrue(layout.contains("android:nextFocusDown=\"@id/addresses\""));
        assertTrue(layout.contains("android:nextFocusDown=\"@id/choose\""));
        assertTrue(layout.contains("android:nextFocusRight=\"@id/negative\""));
        assertTrue(config.contains("binding.name.post(binding.name::requestFocus)"));
        assertTrue(config.contains("expandContentToWindow(width, height)"));
        assertTrue(config.contains("contentParams.width = ViewGroup.LayoutParams.MATCH_PARENT"));
        assertTrue(config.contains("contentParams.height = ViewGroup.LayoutParams.MATCH_PARENT"));
        assertTrue(config.contains("LightDialog.create(requireContext(), getDialogTitle(), getBinding().getRoot(), 0.95f, 0.95f, 2000, height)"));
    }

    private static String read(String file) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
