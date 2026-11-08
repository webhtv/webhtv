package com.fongmi.android.tv.theme;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps a concrete ARGB value back to the semantic role it played in the frozen
 * baseline palette.
 *
 * <p>The binder never guesses: a view color is only rewritten when it exactly
 * equals a baseline role value, and only when every role sharing that value
 * resolves to the same active color. Ambiguous or unknown colors are left alone.
 *
 * <p>条目遮罩族（{@code ITEM_OVERLAY}/{@code ON_ITEM_OVERLAY}）不入表：它们的默认值
 * {@code #66000000}（{@code @color/black_40}）与白字在仓库里被大量固定资源共用，入表会让
 * 一次遮罩色编辑连带改掉刻意固定的深玻璃族。见 {@link ThemeRole#rewritesBaseline()}。
 */
public final class ThemeColorIndex {

    private final Map<Integer, Set<ThemeRole>> rolesByColor;
    private final Map<ThemeRole, Integer> baselineByRole;

    private ThemeColorIndex(Map<Integer, Set<ThemeRole>> rolesByColor, Map<ThemeRole, Integer> baselineByRole) {
        this.rolesByColor = rolesByColor;
        this.baselineByRole = baselineByRole;
    }

    public static ThemeColorIndex of(ThemeTokens baseline) {
        Map<Integer, Set<ThemeRole>> byColor = new LinkedHashMap<>();
        Map<ThemeRole, Integer> byRole = new EnumMap<>(ThemeRole.class);
        if (baseline != null) {
            for (ThemeRole role : ThemeRole.values()) {
                if (!role.rewritesBaseline()) continue;
                int color = role.colorOf(baseline);
                if (color == 0) continue;
                byRole.put(role, color);
                byColor.computeIfAbsent(color, ignored -> new LinkedHashSet<>()).add(role);
            }
        }
        Map<Integer, Set<ThemeRole>> frozen = new LinkedHashMap<>();
        byColor.forEach((color, roles) -> frozen.put(color, Collections.unmodifiableSet(roles)));
        return new ThemeColorIndex(Collections.unmodifiableMap(frozen), Collections.unmodifiableMap(byRole));
    }

    public boolean isEmpty() {
        return rolesByColor.isEmpty();
    }

    public Set<ThemeRole> rolesFor(int color) {
        Set<ThemeRole> roles = rolesByColor.get(color);
        return roles == null ? Set.of() : roles;
    }

    /**
     * The single role a baseline color belongs to, or {@code null} when the color
     * is unknown or shared by several roles.
     */
    public ThemeRole uniqueRoleFor(int color) {
        Set<ThemeRole> roles = rolesFor(color);
        return roles.size() == 1 ? roles.iterator().next() : null;
    }

    /**
     * The replacement color for a baseline view color, or {@code null} when the
     * color must be left untouched.
     *
     * <p>When several roles share the baseline color the replacement is only
     * returned if every one of those roles agrees on the active value; otherwise
     * the correct role cannot be determined and the view keeps its static color.
     */
    public Integer replacementFor(int color, ThemeTokens active) {
        Set<ThemeRole> roles = rolesFor(color);
        if (roles.isEmpty() || active == null) return null;
        Integer candidate = null;
        for (ThemeRole role : roles) {
            int resolved = role.colorOf(active);
            if (candidate == null) candidate = resolved;
            else if (candidate != resolved) return null;
        }
        return candidate == null || candidate == color ? null : candidate;
    }

    /** Baseline value of a role, used for explicit role tags. */
    public Integer baselineOf(ThemeRole role) {
        return baselineByRole.get(role);
    }

    /**
     * A practical, ordered set of common state combinations. Android exposes no
     * public accessor for a ColorStateList's original state array, so a stateful
     * list is rebuilt against this skeleton: every entry keeps the colour the
     * original list resolves to for that state, and unknown states therefore keep
     * their original value instead of being dropped.
     */
    public static final int[][] STATE_SKELETON = {
            {-android.R.attr.state_enabled},
            {android.R.attr.state_enabled, android.R.attr.state_pressed},
            {android.R.attr.state_enabled, android.R.attr.state_focused},
            {android.R.attr.state_enabled, android.R.attr.state_hovered},
            {android.R.attr.state_enabled, android.R.attr.state_selected},
            {android.R.attr.state_enabled, android.R.attr.state_activated},
            {android.R.attr.state_enabled, android.R.attr.state_checked},
            {android.R.attr.state_enabled, -android.R.attr.state_checked, android.R.attr.state_checkable},
            {android.R.attr.state_enabled, android.R.attr.state_expanded},
            {android.R.attr.state_enabled, -android.R.attr.state_expanded},
            {android.R.attr.state_enabled},
            {},
    };

    /** The ordered state skeleton used when a stateful list must be recolored. */
    public static int[][] stateSkeleton() {
        int[][] copy = new int[STATE_SKELETON.length][];
        for (int index = 0; index < STATE_SKELETON.length; index++) copy[index] = STATE_SKELETON[index].clone();
        return copy;
    }

}
