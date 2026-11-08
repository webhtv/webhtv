package com.fongmi.android.tv.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.fongmi.android.tv.R;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** The settings themselves are the preview: stable, locally themed controls, with no sample panel. */
public final class ThemePreviewView extends LinearLayout {

    public interface Callbacks {
        void onColorSlotClicked(ThemeEditor.Slot slot, boolean dark);
        void onDraftChanged();
    }

    private final Callbacks callbacks;
    private final EnumMap<ThemeEditor.Slot, ColorRow> colors = new EnumMap<>(ThemeEditor.Slot.class);
    private final EnumMap<ThemeEditor.Slot, OpacityRow> opacities = new EnumMap<>(ThemeEditor.Slot.class);
    private final List<Group> groups = new ArrayList<>();
    private ThemeEditor editor;
    private boolean dark;
    private boolean binding;

    private ThemePreviewView(Context context, Callbacks callbacks) {
        super(context);
        this.callbacks = callbacks;
        setOrientation(VERTICAL);
        boolean wide = context.getResources().getConfiguration().screenWidthDp >= 700;
        addGroups(wide,
                colorGroup(R.string.theme_editor_group_accent, ThemeEditor.Slot.PRIMARY,
                        ThemeEditor.Slot.PRIMARY_CONTAINER, ThemeEditor.Slot.SECONDARY_CONTAINER, ThemeEditor.Slot.FOCUS),
                colorGroup(R.string.theme_editor_group_surfaces, ThemeEditor.Slot.SURFACE,
                        ThemeEditor.Slot.SURFACE_CONTAINER, ThemeEditor.Slot.SURFACE_CONTAINER_HIGH));
        // 「当前播放环色」单独一组：它不是页面角色，而是播放页叠在视频上的状态色，
        // 解析时会在固定玻璃面板上做可读性夹取。
        addView(colorGroup(R.string.theme_editor_group_player, ThemeEditor.Slot.PLAYER_CURRENT).view,
                ThemeEditorUi.fullWidth(context, 12));
        addGroups(wide,
                colorGroup(R.string.theme_editor_group_text, ThemeEditor.Slot.ON_SURFACE,
                        ThemeEditor.Slot.ON_SURFACE_VARIANT, ThemeEditor.Slot.OUTLINE),
                colorGroup(R.string.theme_editor_group_states, ThemeEditor.Slot.SUCCESS,
                        ThemeEditor.Slot.WARNING, ThemeEditor.Slot.ERROR));
        Group layers = group(R.string.theme_editor_group_layers);
        for (ThemeEditor.Slot slot : new ThemeEditor.Slot[]{ThemeEditor.Slot.SCRIM_OPACITY,
                ThemeEditor.Slot.DIALOG_OPACITY, ThemeEditor.Slot.OVERLAY_OPACITY,
                ThemeEditor.Slot.PLAYER_SCRIM_OPACITY,
                ThemeEditor.Slot.LIVE_LIST_SCRIM_OPACITY}) {
            OpacityRow row = new OpacityRow(slot);
            opacities.put(slot, row);
            layers.view.addView(row.view, ThemeEditorUi.fullWidth(context, 8));
        }
        addView(layers.view, ThemeEditorUi.fullWidth(context, 12));
        // 条目遮罩就是列表/按钮聚焦时的同一个视觉机制：遮罩底色、遮罩上的文字色、遮罩强度
        // 三者必须一起调才不会互相打架，所以单独成组；强度滑杆也跟着留在这里，不拆去 layers 组，
        // 否则用户调这一处机制要跨两个分组来回找。
        Group itemOverlay = colorGroup(R.string.theme_editor_group_item_overlay,
                ThemeEditor.Slot.ITEM_OVERLAY, ThemeEditor.Slot.ON_ITEM_OVERLAY);
        OpacityRow itemOverlayOpacity = new OpacityRow(ThemeEditor.Slot.ITEM_OVERLAY_OPACITY);
        opacities.put(ThemeEditor.Slot.ITEM_OVERLAY_OPACITY, itemOverlayOpacity);
        itemOverlay.view.addView(itemOverlayOpacity.view, ThemeEditorUi.fullWidth(context, 8));
        addView(itemOverlay.view, ThemeEditorUi.fullWidth(context, 12));
    }

    public static ThemePreviewView createPanel(Context context, ThemeEditor editor, boolean dark,
                                               ThemeTokens tokens, Callbacks callbacks) {
        ThemePreviewView panel = new ThemePreviewView(context, callbacks);
        panel.render(editor, dark, tokens);
        return panel;
    }

    /** Rebinds in place so dragging, accessibility focus and the scroll position cannot be lost. */
    public void render(ThemeEditor editor, boolean dark, ThemeTokens tokens) {
        this.editor = editor;
        this.dark = dark;
        binding = true;
        for (Group group : groups) {
            group.view.setBackground(ThemeEditorUi.shape(getContext(), tokens.colorSurfaceContainer(), 0, 0, 18));
            group.title.setTextColor(ThemeEditorUi.readable(tokens.colorOnSurfaceVariant(), tokens.colorSurfaceContainer()));
        }
        for (ColorRow row : colors.values()) row.render(tokens);
        for (OpacityRow row : opacities.values()) row.render(tokens);
        binding = false;
    }

    private Group colorGroup(int label, ThemeEditor.Slot... slots) {
        Group group = group(label);
        for (ThemeEditor.Slot slot : slots) {
            ColorRow row = new ColorRow(slot);
            colors.put(slot, row);
            group.view.addView(row.view, ThemeEditorUi.fullWidth(getContext(), 6));
        }
        return group;
    }

    private Group group(int label) {
        LinearLayout view = ThemeEditorUi.column(getContext());
        ThemeEditorUi.padding(view, 12, 12);
        TextView title = ThemeEditorUi.text(getContext(), getContext().getString(label), 13, 0xFF44474F);
        ThemeEditorUi.heading(title);
        view.addView(title, ThemeEditorUi.fullWidth(getContext(), 0));
        Group group = new Group(view, title);
        groups.add(group);
        return group;
    }

    private void addGroups(boolean wide, Group first, Group second) {
        if (!wide) {
            addView(first.view, ThemeEditorUi.fullWidth(getContext(), 12));
            addView(second.view, ThemeEditorUi.fullWidth(getContext(), 12));
            return;
        }
        LinearLayout row = ThemeEditorUi.row(getContext());
        row.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams left = ThemeEditorUi.weighted();
        left.setMarginEnd(ThemeEditorUi.dp(getContext(), 12));
        row.addView(first.view, left);
        row.addView(second.view, ThemeEditorUi.weighted());
        addView(row, ThemeEditorUi.fullWidth(getContext(), 12));
    }

    private final class ColorRow {
        final ThemeEditor.Slot slot;
        final LinearLayout view;
        final TextView swatch;
        final TextView label;
        final TextView value;
        final Button clear;

        ColorRow(ThemeEditor.Slot slot) {
            this.slot = slot;
            Context context = getContext();
            view = ThemeEditorUi.row(context);
            view.setTag("theme-slot:" + slot.name());
            ThemeEditorUi.padding(view, 8, 8);
            view.setMinimumHeight(ThemeEditorUi.dp(context, 64));
            view.setFocusable(true);
            view.setOnClickListener(ignored -> callbacks.onColorSlotClicked(slot, dark));

            swatch = ThemeEditorUi.text(context, symbol(slot), 14, 0xFF000000);
            swatch.setGravity(Gravity.CENTER);
            swatch.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            int size = ThemeEditorUi.dp(context, 32);
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(size, size);
            swatchParams.setMarginEnd(ThemeEditorUi.dp(context, 10));
            view.addView(swatch, swatchParams);

            LinearLayout text = ThemeEditorUi.column(context);
            label = ThemeEditorUi.text(context, context.getString(labelOf(slot)), 14, 0xFF1A1C1E);
            value = ThemeEditorUi.text(context, "", 11, 0xFF44474F);
            value.setTypeface(Typeface.MONOSPACE);
            text.addView(label);
            text.addView(value, ThemeEditorUi.fullWidth(context, 5));
            view.addView(text, ThemeEditorUi.weighted());

            clear = ThemeEditorUi.button(context, R.string.theme_editor_clear_short);
            clear.setContentDescription(context.getString(R.string.theme_editor_clear) + " · " + label.getText());
            clear.setOnClickListener(ignored -> {
                editor.clear(slot, dark);
                callbacks.onDraftChanged();
            });
            view.addView(clear, new LinearLayout.LayoutParams(ThemeEditorUi.dp(context, 48), ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        void render(ThemeTokens tokens) {
            int color = colorOf(tokens, slot);
            int fill = slot == ThemeEditor.Slot.SURFACE_CONTAINER_HIGH
                    ? tokens.colorSurfaceContainerHigh() : tokens.colorSurfaceContainer();
            String raw = editor.valueOf(slot, dark);
            String actual = ThemeEditorUi.hex(color);
            value.setText(raw == null ? getContext().getString(R.string.theme_editor_value_auto, actual)
                    : raw.equalsIgnoreCase(actual) ? actual
                    : getContext().getString(R.string.theme_editor_value_adjusted, raw, actual));
            label.setTextColor(ThemeEditorUi.readable(tokens.colorOnSurface(), fill));
            value.setTextColor(ThemeEditorUi.readable(tokens.colorOnSurfaceVariant(), fill));
            swatch.setBackground(ThemeEditorUi.shape(getContext(), color, tokens.colorOutline(), 1, 9));
            swatch.setTextColor(ThemeEditorUi.readable(tokens.colorOnSurface(), color));
            view.setBackground(ThemeEditorUi.interactive(getContext(), fill, tokens.colorOutlineVariant(), tokens.colorFocus(), false));
            clear.setEnabled(raw != null);
            ThemeEditorUi.buttonColors(clear, fill, tokens.colorOnSurfaceVariant(), tokens, false);
        }
    }

    private final class OpacityRow {
        final ThemeEditor.Slot slot;
        final LinearLayout view;
        final TextView label;
        final TextView value;
        final SeekBar bar;
        final Button clear;

        OpacityRow(ThemeEditor.Slot slot) {
            this.slot = slot;
            Context context = getContext();
            view = ThemeEditorUi.column(context);
            LinearLayout header = ThemeEditorUi.row(context);
            label = ThemeEditorUi.text(context, context.getString(labelOf(slot)), 14, 0xFF1A1C1E);
            header.addView(label, ThemeEditorUi.weighted());
            value = ThemeEditorUi.text(context, "", 12, 0xFF44474F);
            header.addView(value);
            view.addView(header);

            LinearLayout controls = ThemeEditorUi.row(context);
            bar = new SeekBar(context);
            bar.setTag("theme-opacity:" + slot.name());
            bar.setMax(100);
            bar.setMinimumHeight(ThemeEditorUi.dp(context, 48));
            controls.addView(bar, ThemeEditorUi.weighted());
            clear = ThemeEditorUi.button(context, R.string.theme_editor_clear_short);
            clear.setContentDescription(context.getString(R.string.theme_editor_clear) + " · " + label.getText());
            clear.setOnClickListener(ignored -> {
                editor.clear(slot, dark);
                callbacks.onDraftChanged();
            });
            controls.addView(clear, new LinearLayout.LayoutParams(ThemeEditorUi.dp(context, 48), ViewGroup.LayoutParams.WRAP_CONTENT));
            view.addView(controls);
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser || binding) return;
                    float opacity = minimumOf(slot) + (maximumOf(slot) - minimumOf(slot)) * progress / 100f;
                    if (editor.set(slot, dark, null, opacity).success()) callbacks.onDraftChanged();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    seekBar.getParent().requestDisallowInterceptTouchEvent(true);
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    seekBar.getParent().requestDisallowInterceptTouchEvent(false);
                }
            });
        }

        void render(ThemeTokens tokens) {
            String raw = editor.valueOf(slot, dark);
            float opacity = switch (slot) {
                case SCRIM_OPACITY -> ThemeEditorUi.alpha(tokens.colorScrim());
                case DIALOG_OPACITY -> tokens.dialogOpacity();
                case PLAYER_SCRIM_OPACITY -> PlaybackMenuScrim.DEFAULT_OPACITY;
                case LIVE_LIST_SCRIM_OPACITY -> LiveListScrim.DEFAULT_OPACITY;
                // 遮罩强度就是遮罩色的 alpha，所以「自动」时的取值直接读解析后的 alpha，
                // 不另立一个默认常量，避免两处默认值漂移。
                case ITEM_OVERLAY_OPACITY -> ThemeEditorUi.alpha(tokens.colorItemOverlay());
                default -> ThemeEditorUi.alpha(tokens.colorOverlayLight());
            };
            if (raw != null) opacity = Float.parseFloat(raw);
            String percentage = Math.round(opacity * 100) + "%";
            value.setText(raw == null ? getContext().getString(R.string.theme_editor_value_auto, percentage) : percentage);
            int fill = tokens.colorSurfaceContainer();
            label.setTextColor(ThemeEditorUi.readable(tokens.colorOnSurface(), fill));
            value.setTextColor(ThemeEditorUi.readable(tokens.colorOnSurfaceVariant(), fill));
            bar.setProgress(Math.round((opacity - minimumOf(slot)) / (maximumOf(slot) - minimumOf(slot)) * 100));
            bar.setContentDescription(label.getText() + " · " + percentage);
            bar.setProgressTintList(ColorStateList.valueOf(tokens.colorPrimary()));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(tokens.colorOutline()));
            bar.setThumbTintList(ColorStateList.valueOf(tokens.colorPrimary()));
            bar.setBackground(ThemeEditorUi.interactive(getContext(), fill, fill, tokens.colorFocus(), false));
            clear.setEnabled(raw != null);
            ThemeEditorUi.buttonColors(clear, fill, tokens.colorOnSurfaceVariant(), tokens, false);
        }
    }

    private record Group(LinearLayout view, TextView title) { }

    private static String symbol(ThemeEditor.Slot slot) {
        return switch (slot) {
            case ON_SURFACE, ON_SURFACE_VARIANT, ON_ITEM_OVERLAY -> "Aa";
            case SUCCESS -> "✓";
            case WARNING -> "!";
            case ERROR -> "×";
            case PLAYER_CURRENT -> "▶";
            default -> "";
        };
    }

    public static int colorOf(ThemeTokens tokens, ThemeEditor.Slot slot) {
        return switch (slot) {
            case PRIMARY -> tokens.colorPrimary();
            case PRIMARY_CONTAINER -> tokens.colorPrimaryContainer();
            case SECONDARY_CONTAINER -> tokens.colorSecondaryContainer();
            case FOCUS -> tokens.colorFocus();
            case PLAYER_CURRENT -> tokens.colorPlayerCurrentRing();
            case ITEM_OVERLAY -> tokens.colorItemOverlay();
            case ON_ITEM_OVERLAY -> tokens.colorOnItemOverlay();
            case SURFACE -> tokens.colorSurface();
            case SURFACE_CONTAINER -> tokens.colorSurfaceContainer();
            case SURFACE_CONTAINER_HIGH -> tokens.colorSurfaceContainerHigh();
            case ON_SURFACE -> tokens.colorOnSurface();
            case ON_SURFACE_VARIANT -> tokens.colorOnSurfaceVariant();
            case OUTLINE -> tokens.colorOutline();
            case ERROR -> tokens.colorError();
            case SUCCESS -> tokens.colorSuccess();
            case WARNING -> tokens.colorWarning();
            default -> throw new IllegalArgumentException("Not a colour slot: " + slot);
        };
    }

    private static float minimumOf(ThemeEditor.Slot slot) {
        return switch (slot) {
            case SCRIM_OPACITY -> ThemeProfileValidator.MIN_SCRIM_OPACITY;
            case DIALOG_OPACITY -> ThemeProfileValidator.MIN_DIALOG_OPACITY;
            case PLAYER_SCRIM_OPACITY -> ThemeProfileValidator.MIN_PLAYER_SCRIM_OPACITY;
            case LIVE_LIST_SCRIM_OPACITY -> ThemeProfileValidator.MIN_LIVE_LIST_SCRIM_OPACITY;
            // 条目遮罩的有效区间是 5%..100%（见 ThemeProfileValidator），与 OVERLAY_OPACITY 的
            // 5%..60% 不同；不显式列出就会落到 default 分支，滑杆永远推不到 100%。
            case ITEM_OVERLAY_OPACITY -> ThemeProfileValidator.MIN_ITEM_OVERLAY_OPACITY;
            default -> ThemeProfileValidator.MIN_OVERLAY_OPACITY;
        };
    }

    private static float maximumOf(ThemeEditor.Slot slot) {
        return switch (slot) {
            case SCRIM_OPACITY -> ThemeProfileValidator.MAX_SCRIM_OPACITY;
            case DIALOG_OPACITY -> ThemeProfileValidator.MAX_DIALOG_OPACITY;
            case PLAYER_SCRIM_OPACITY -> ThemeProfileValidator.MAX_PLAYER_SCRIM_OPACITY;
            case LIVE_LIST_SCRIM_OPACITY -> ThemeProfileValidator.MAX_LIVE_LIST_SCRIM_OPACITY;
            case ITEM_OVERLAY_OPACITY -> ThemeProfileValidator.MAX_ITEM_OVERLAY_OPACITY;
            default -> ThemeProfileValidator.MAX_OVERLAY_OPACITY;
        };
    }

    public static int labelOf(ThemeEditor.Slot slot) {
        return switch (slot) {
            case PRIMARY -> R.string.theme_editor_slot_primary;
            case PRIMARY_CONTAINER -> R.string.theme_editor_slot_primary_container;
            case SECONDARY_CONTAINER -> R.string.theme_editor_slot_secondary_container;
            case FOCUS -> R.string.theme_editor_slot_focus;
            case PLAYER_CURRENT -> R.string.theme_editor_slot_player_current;
            case SURFACE -> R.string.theme_editor_slot_surface;
            case SURFACE_CONTAINER -> R.string.theme_editor_slot_surface_container;
            case SURFACE_CONTAINER_HIGH -> R.string.theme_editor_slot_surface_container_high;
            case ON_SURFACE -> R.string.theme_editor_slot_on_surface;
            case ON_SURFACE_VARIANT -> R.string.theme_editor_slot_on_surface_variant;
            case OUTLINE -> R.string.theme_editor_slot_outline;
            case ERROR -> R.string.theme_editor_slot_error;
            case SUCCESS -> R.string.theme_editor_slot_success;
            case WARNING -> R.string.theme_editor_slot_warning;
            case SCRIM_OPACITY -> R.string.theme_editor_slot_scrim_opacity;
            case DIALOG_OPACITY -> R.string.theme_editor_slot_dialog_opacity;
            case OVERLAY_OPACITY -> R.string.theme_editor_slot_overlay_opacity;
            case PLAYER_SCRIM_OPACITY -> R.string.theme_editor_slot_player_scrim_opacity;
            case LIVE_LIST_SCRIM_OPACITY -> R.string.theme_editor_slot_live_list_scrim_opacity;
            case ITEM_OVERLAY -> R.string.theme_editor_slot_item_overlay;
            case ON_ITEM_OVERLAY -> R.string.theme_editor_slot_on_item_overlay;
            case ITEM_OVERLAY_OPACITY -> R.string.theme_editor_slot_item_overlay_opacity;
        };
    }
}
