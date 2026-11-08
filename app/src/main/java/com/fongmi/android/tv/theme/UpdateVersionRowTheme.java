package com.fongmi.android.tv.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.TextView;

import com.fongmi.android.tv.R;

/**
 * Row rendering for the version cards inside the “版本更新” dialog.
 *
 * <p>Why this exists (measured defect, not a style preference): both
 * {@code UpdateDialog} flavours painted each version card with the static
 * {@code selector_update_version_item} drawable (resting fill
 * {@code ?attr/colorSurfaceContainerHighest}, selected fill
 * {@code ?attr/colorPrimaryContainer}) while the card text came from
 * {@code @color/update_version_item_title} / {@code _meta} / {@code _accent}, i.e. from
 * {@code ?attr/colorOnSurface} / {@code ?attr/colorOnSurfaceVariant} /
 * {@code ?attr/colorPrimary}. Those are two independent resource graphs: the fill is a
 * {@code StateListDrawable} that {@link ThemeBinder} never rewrites
 * ({@code bindDrawable} only handles {@code GradientDrawable} and
 * {@code MaterialShapeDrawable}), while the text colours are {@code ColorStateList}s that
 * it does rewrite. A user on the default theme sees the compiled pair and everything
 * matches; a user on “壁纸取色” or a custom colour gets text re-derived from the dynamic
 * palette on top of a fill that did not move - and the card goes unreadable.
 *
 * <p>Reported case (user screenshot, 1080x2340, dark wallpaper, “主题色彩 = 壁纸取色”): the
 * beta card rendered as a solid {@code #0F1D26} block carrying {@code #49454F} text, i.e.
 * <b>1.5:1</b>, while the stable card next to it stayed legible - the two cards resolve
 * different {@code ?attr} entries once the profile overlay is involved, so the mismatch
 * only shows up on some cards for some users.
 *
 * <p>The card therefore has to take its fill <em>and</em> every text colour from one
 * {@link ThemeController#current()} snapshot. {@code ?attr/color*} cannot do that: it is
 * a compile-time resource, Android exposes no public API to rewrite it at runtime, and
 * the whole point of the profile feature is that a user override must reach this dialog.
 *
 * <p>The card keeps the three states the static selector defined, with the same role swap
 * between them: resting ({@code colorSurfaceContainerHighest} fill), the expanded/selected
 * channel ({@code colorPrimaryContainer} fill) and focus ({@code colorPrimary} fill). The
 * selector deliberately inverted fill and ring between "focused" and "selected" so that
 * "where the focus is" and "which channel is open" stay two independent clues, and it used
 * a closed ring for both. Collapsing the focus fill into {@code colorPrimaryContainer}
 * would leave the TV dialog - which always opens with {@code stableItem.requestFocus()} -
 * showing a focused card that looks selected; the ring width therefore comes from the
 * single {@code @dimen/webhtv_focus_ring_width} token, not from a literal.
 *
 * <p>Every foreground is additionally re-checked with
 * {@link ThemeEditorUi#readable(int, int)} against the fills it can actually land on, so
 * an arbitrary user palette cannot produce a low-contrast card even when the two roles
 * happen to collide. The card subtree is tagged {@link #IGNORE_TAG} because this class
 * now owns it; otherwise {@link ThemeBinder} would re-colour the text a second time from
 * a different role mapping and undo that guarantee.
 */
public final class UpdateVersionRowTheme {

    /** Same marker {@link ThemeBinder} honours for views that paint themselves. */
    public static final String IGNORE_TAG = "webhtv:ignore";

    /** WCAG AA for body text; the card carries both a title and a version line. */
    private static final double AA = 4.5;

    /** WCAG 1.4.11 non-text contrast, used for the selected/focused ring. */
    private static final double RING = 3.0;

    private static final int CORNER_DP = 8;

    private static final int PILL_RADIUS_DP = 12;

    private UpdateVersionRowTheme() {
    }

    /**
     * The card fill: {@code colorSurfaceContainerHighest} at rest (the same role the
     * static selector used, and the Material 3 "inset list" pattern already used by
     * {@link AppearanceRowTheme}) and {@code colorPrimaryContainer} for the expanded
     * channel, which is what the {@code state_selected} entry used to paint.
     */
    public static int fill(ThemeTokens tokens, boolean expanded) {
        ThemeTokens safe = safe(tokens);
        return expanded ? safe.colorPrimaryContainer() : safe.colorSurfaceContainerHighest();
    }

    /** The card outline: {@code colorPrimary} when expanded, else the quiet hairline. */
    public static int ring(ThemeTokens tokens, boolean expanded) {
        ThemeTokens safe = safe(tokens);
        return expanded ? safe.colorPrimary() : safe.colorOutlineVariant();
    }

    /** Channel name (“稳定版” / “测试版”). */
    public static int nameColor(ThemeTokens tokens, boolean expanded) {
        return readableOn(safe(tokens).colorOnSurface(), fill(tokens, expanded));
    }

    /** Version line (“5.6.0-beta-…”), the role that was unreadable in the report. */
    public static int versionColor(ThemeTokens tokens, boolean expanded) {
        return readableOn(safe(tokens).colorOnSurfaceVariant(), fill(tokens, expanded));
    }

    /** Release notes body. */
    public static int descColor(ThemeTokens tokens, boolean expanded) {
        return readableOn(safe(tokens).colorOnSurface(), fill(tokens, expanded));
    }

    /** The “展开” affordance. */
    public static int expandColor(ThemeTokens tokens, boolean expanded) {
        return readableOn(safe(tokens).colorPrimary(), fill(tokens, expanded));
    }

    /** The “已最新” pill sits on {@code colorSurface}, so it stays visible on either fill. */
    public static int pillFill(ThemeTokens tokens) {
        return safe(tokens).colorSurface();
    }

    /** The pill label; {@code colorPrimary} is already contracted against {@code colorSurface}. */
    public static int pillTextColor(ThemeTokens tokens) {
        return readableOn(safe(tokens).colorPrimary(), pillFill(tokens));
    }

    /**
     * The fill the card paints while it holds focus: {@code colorPrimary}, i.e. the role the
     * static selector used for {@code state_focused} and deliberately swapped with the
     * selected fill so the two cues stay independent.
     */
    public static int focusFill(ThemeTokens tokens) {
        return safe(tokens).colorPrimary();
    }

    /**
     * The ink for the focused card. {@code colorOnPrimary} is already contracted against
     * {@code colorPrimary}, so on a shipped palette this is the exact colour the old
     * {@code update_version_item_*} state lists used; the fallback only fires for a user
     * palette that breaks that pair.
     */
    public static int focusInk(ThemeTokens tokens) {
        ThemeTokens safe = safe(tokens);
        return readableOn(safe.colorOnPrimary(), safe.colorPrimary());
    }

    /**
     * One text role as a state list: focused and pressed take the on-focus ink, everything
     * else the colour clamped against the resting and expanded fills.
     *
     * <p>The card's text views declare {@code android:duplicateParentState="true"} in both
     * layouts; that is what lets the row's focus state reach them, and without it the
     * focused branch would never be selected.
     */
    static ColorStateList textColors(int resting, int focused) {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_focused}, new int[]{android.R.attr.state_pressed}, new int[]{}},
                new int[]{focused, focused, resting});
    }

    /**
     * Applies the palette to one version card.
     *
     * <p>The text colours are checked against both the resting and the expanded fill
     * because the card keeps them while the user toggles it open: a colour that only
     * clears AA on one of the two fills would go unreadable on the other.
     *
     * @param expanded whether this channel is the expanded one
     */
    public static void apply(View row, TextView name, TextView version, TextView status, TextView expand, TextView desc, boolean expanded, ThemeTokens tokens) {
        ThemeTokens safe = safe(tokens);
        int resting = safe.colorSurfaceContainerHighest();
        int expandedFill = safe.colorPrimaryContainer();
        if (row != null) {
            row.setTag(IGNORE_TAG);
            row.setBackground(background(row.getContext(), safe));
        }
        int focusedInk = focusInk(safe);
        if (name != null) name.setTextColor(textColors(readableOn(safe.colorOnSurface(), resting, expandedFill), focusedInk));
        if (version != null) version.setTextColor(textColors(readableOn(safe.colorOnSurfaceVariant(), resting, expandedFill), focusedInk));
        if (desc != null) desc.setTextColor(textColors(readableOn(safe.colorOnSurface(), resting, expandedFill), focusedInk));
        if (expand != null) expand.setTextColor(textColors(readableOn(safe.colorPrimary(), resting, expandedFill), focusedInk));
        if (status != null) {
            status.setTextColor(pillTextColor(safe));
            status.setBackground(pill(status.getContext(), pillFill(safe)));
        }
    }

    /**
     * The card background for the current palette.
     *
     * <p>This is the runtime twin of {@code selector_update_version_item}: focus paints the
     * {@code colorPrimary} fill with an on-primary ring, the selected channel paints
     * {@code colorPrimaryContainer} with a primary ring, and the resting card keeps the
     * quiet hairline. Focus is the only state whose text flips (see
     * {@link #textColors(int, int)}), which is what makes a primary focus fill readable
     * again.
     */
    public static Drawable background(Context context, ThemeTokens tokens) {
        ThemeTokens safe = safe(tokens);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                faced(context, safe.colorPrimary(), ringColor(safe.colorOnPrimary(), safe.colorPrimary())));
        states.addState(new int[]{android.R.attr.state_pressed},
                faced(context, safe.colorPrimary(), ringColor(safe.colorOnPrimary(), safe.colorPrimary())));
        states.addState(new int[]{android.R.attr.state_selected},
                faced(context, safe.colorPrimaryContainer(), ringColor(safe.colorPrimary(), safe.colorPrimaryContainer())));
        states.addState(new int[]{},
                ThemeEditorUi.shape(context, safe.colorSurfaceContainerHighest(), safe.colorOutlineVariant(), 1, CORNER_DP));
        return states;
    }

    /**
     * A card face with the shared focus-ring width. {@link ThemeEditorUi#shape} skips its own
     * stroke when the width is 0, so the width is set here from the single repository token
     * instead of a per-widget literal.
     */
    private static GradientDrawable faced(Context context, int fill, int stroke) {
        GradientDrawable shape = ThemeEditorUi.shape(context, fill, stroke, 0, CORNER_DP);
        shape.setStroke(focusRingWidth(context), stroke);
        return shape;
    }

    /** The single focus-ring width source, {@code app/src/main/res/values/webhtv_dimens.xml}. */
    private static int focusRingWidth(Context context) {
        return Math.max(1, Math.round(context.getResources().getDimension(R.dimen.webhtv_focus_ring_width)));
    }

    /** The ring role when it clears the non-text floor against its own fill, else a readable fallback. */
    private static int ringColor(int preferred, int fill) {
        return ThemeContrast.ratio(preferred, fill) >= RING ? preferred : readableOn(preferred, fill);
    }

    private static GradientDrawable pill(Context context, int fill) {
        return ThemeEditorUi.shape(context, fill, 0, 0, PILL_RADIUS_DP);
    }

    /**
     * {@code preferred} when it clears AA on every fill it can land on, otherwise the
     * better of black and white against all of them.
     *
     * <p>{@link ThemeEditorUi#readable(int, int)} only takes one background; a card whose
     * text survives a fill change needs the conservative variant. One of black/white
     * always clears AA against a given fill, and taking the minimum over the fills keeps
     * that property for the pair.
     */
    static int readableOn(int preferred, int... fills) {
        if (clears(preferred, fills)) return preferred;
        int best = 0xFF000000;
        double bestScore = -1;
        for (int candidate : new int[]{0xFF000000, 0xFFFFFFFF}) {
            double score = Double.MAX_VALUE;
            for (int fill : fills) score = Math.min(score, ThemeContrast.ratio(candidate, fill));
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean clears(int color, int... fills) {
        for (int fill : fills) if (ThemeContrast.ratio(color, fill) < AA) return false;
        return true;
    }

    /**
     * The expanded card draws a real indicator ring, so it must clear the non-text
     * contrast floor against its own fill. The resting card draws a decorative hairline
     * only - the same {@code colorOutlineVariant} hairline the static selector used - so it
     * is checked for distinguishability instead of the 3:1 indicator floor.
     */
    static boolean ringIsVisible(ThemeTokens tokens, boolean expanded) {
        ThemeTokens safe = safe(tokens);
        if (expanded) return ThemeContrast.ratio(safe.colorPrimary(), safe.colorPrimaryContainer()) >= RING;
        return safe.colorOutlineVariant() != safe.colorSurfaceContainerHighest();
    }

    private static ThemeTokens safe(ThemeTokens tokens) {
        return tokens == null ? ThemeTokens.dark() : tokens;
    }
}
