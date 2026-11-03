package com.fongmi.android.tv.theme;

import org.junit.Test;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ThemeContractTest {

    private static final Pattern COLOR = Pattern.compile("<color name=\"(webhtv_color_[a-z0-9_]+)\">#([0-9A-Fa-f]{6,8})</color>");

    @Test
    public void resourceMappingMatchesTheImmutableRecordForEveryColor() throws Exception {
        assertResourceMapping(ThemeTokens.light(), Path.of("src/main/res/values/webhtv_tokens.xml"));
        assertResourceMapping(ThemeTokens.dark(), Path.of("src/main/res/values-night/webhtv_tokens.xml"));
    }

    @Test
    public void allContrastPairsAreFrozenAndPass() {
        ThemeTokens.light().requireContrast();
        ThemeTokens.dark().requireContrast();
        assertEquals(6.386, ThemeContrast.ratio(ThemeTokens.light().colorOnPrimary(), ThemeTokens.light().colorPrimary()), 0.002);
        assertEquals(4.284, ThemeContrast.ratio(ThemeTokens.light().colorOutline(), ThemeTokens.light().colorSurface()), 0.002);
        assertEquals(14.724, ThemeContrast.ratio(ThemeTokens.dark().colorPlayerControlActive(), 0xFF000000), 0.002);
    }

    @Test
    public void playerControlsAreIsolatedFromGeneralFocusAndPrimary() {
        for (ThemeTokens tokens : new ThemeTokens[]{ThemeTokens.light(), ThemeTokens.dark()}) {
            assertNotEquals(tokens.colorPrimary(), tokens.colorPlayerControlActive());
            assertNotEquals(tokens.colorFocus(), tokens.colorPlayerControlActive());
        }
    }

    @Test
    public void allSemanticAttrsAreDeclared() throws Exception {
        String attrs = Files.readString(Path.of("src/main/res/values/webhtv_attrs.xml"), StandardCharsets.UTF_8);
        for (RecordComponent component : ThemeTokens.class.getRecordComponents()) {
            if (!component.getType().equals(int.class)) continue;
            assertTrue(component.getName(), attrs.contains("name=\"" + attrName(component.getName()) + "\""));
        }
        assertFalse(attrs.contains("format=\"string\""));
    }

    /**
     * A semantic attr that is declared and referenced but never assigned is a crash, not a
     * silent fallback.
     *
     * <p>{@code ?attr/webhtvColorOnSurface} and {@code ?attr/webhtvColorOnSurfaceVariant} were
     * declared in {@code webhtv_attrs.xml} and referenced by every {@code TextAppearance.WebHTV.*}
     * style, but no theme ever assigned them - {@code Theme.WebHTV} only maps the standard
     * Material attrs. Reading such a text appearance makes {@code TextView.readTextAppearance}
     * call {@code TypedArray.getColorStateList} on an unresolvable attribute, which throws
     * {@code UnsupportedOperationException} inside the constructor, so inflation dies. That is
     * exactly how "TMDB data configuration" crashed on open (and with it every other dialog
     * using {@code Widget.WebHTV.Label}/{@code Helper}/{@code SliderLabel}).
     *
     * <p>Guard: every referenced {@code webhtv*} attr must also be assigned by a theme, so the
     * attrs and their assignments can never drift apart again.
     */
    @Test
    public void everyReferencedSemanticAttrIsAssignedByATheme() throws Exception {
        Pattern reference = Pattern.compile("\\?attr/(webhtv[A-Za-z0-9_]+)");
        java.util.Set<String> referenced = new java.util.TreeSet<>();
        for (String sourceSet : new String[]{"main", "mobile", "leanback"}) {
            Path base = Path.of("src/" + sourceSet + "/res");
            if (!Files.exists(base)) continue;
            try (java.util.stream.Stream<Path> paths = Files.walk(base)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".xml")).toList()) {
                    Matcher matcher = reference.matcher(Files.readString(path, StandardCharsets.UTF_8));
                    while (matcher.find()) referenced.add(matcher.group(1));
                }
            }
        }

        StringBuilder assignedSources = new StringBuilder();
        Path values = Path.of("src/main/res/values");
        try (java.util.stream.Stream<Path> paths = Files.walk(values)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".xml")).toList()) {
                if (path.getFileName().toString().equals("webhtv_attrs.xml")) continue;
                assignedSources.append(Files.readString(path, StandardCharsets.UTF_8));
            }
        }
        String themes = assignedSources.toString();
        java.util.List<String> unassigned = new java.util.ArrayList<>();
        for (String attr : referenced) {
            // An assignment is an <item name="attr"> entry; the bare declaration is not enough.
            if (!themes.contains("name=\"" + attr + "\">")) unassigned.add(attr);
        }
        assertTrue("these semantic attrs are referenced but no theme assigns them, so any view "
                + "reading them crashes during inflation: " + unassigned, unassigned.isEmpty());
    }

    /**
     * 与 {@link #everyReferencedSemanticAttrIsAssignedByATheme} 同一类陷阱，但针对 {@code tv*} 焦点语义属性。
     *
     * <p>用户报告（2026-10-10）：「现在选中播放器看不出选中的效果，好像是黑色的边框」。
     * 根因：{@code selector_control_sheet_button} 的焦点环从写死半透明白改成了
     * {@code ?attr/tvPlayerRing}，而 {@code Theme.WebHTV.Dialog}（父主题
     * {@code Theme.Material3.DayNight.Dialog.Alert}）与 BottomSheet 用的 Material 默认
     * {@code bottomSheetDialogTheme} 都**不继承** {@code Theme.WebHTV}。
     * 属性解析不到时，{@code ?attr/xxx} 会静默退化为黑色/透明，而且**不会崩溃**，
     * 所以这种回归只有人眼才能发现。
     *
     * <p>本测试把「每个被引用的 {@code tv*} 属性必须被至少一个主题 style 赋值」钉死，
     * 并额外要求基底主题 {@code Theme.WebHTV} 自带全部五项——因为对话框主题的父主题链
     * 绕开了 flavour 的 {@code Theme.Base}，只有在基底上绑定才能覆盖所有入口。
     */
    @Test
    public void everyReferencedTvFocusAttrIsAssignedByATheme() throws Exception {
        Pattern reference = Pattern.compile("\\?attr/(tv[A-Za-z0-9_]+)");
        java.util.Set<String> referenced = new java.util.TreeSet<>();
        for (String sourceSet : new String[]{"main", "mobile", "leanback"}) {
            Path base = Path.of("src/" + sourceSet + "/res");
            if (!Files.exists(base)) continue;
            try (java.util.stream.Stream<Path> paths = Files.walk(base)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".xml")).toList()) {
                    Matcher matcher = reference.matcher(Files.readString(path, StandardCharsets.UTF_8));
                    while (matcher.find()) referenced.add(matcher.group(1));
                }
            }
        }
        assertFalse("必须真的扫到 tv* 属性引用，否则本测试是空断言", referenced.isEmpty());

        // 赋值来源：全部 values 目录（含 values-night / 各 flavour 的 styles）。
        StringBuilder assigned = new StringBuilder();
        for (String sourceSet : new String[]{"main", "mobile", "leanback"}) {
            Path values = Path.of("src/" + sourceSet + "/res/values");
            if (!Files.exists(values)) continue;
            try (java.util.stream.Stream<Path> paths = Files.walk(values)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".xml")).toList()) {
                    assigned.append(Files.readString(path, StandardCharsets.UTF_8));
                }
            }
        }
        String themes = assigned.toString();
        java.util.List<String> unassigned = new java.util.ArrayList<>();
        for (String attr : referenced) {
            if (!themes.contains("name=\"" + attr + "\">")) unassigned.add(attr);
        }
        assertTrue("these tv* focus attrs are referenced but no theme assigns them, so ?attr/xxx "
                + "silently degrades to black/transparent (measured defect): " + unassigned,
                unassigned.isEmpty());

        // 基底主题必须自带全部五项，覆盖那些父主题链绕开 Theme.Base 的对话框。
        String base = Files.readString(Path.of("src/main/res/values/webhtv_styles.xml"), StandardCharsets.UTF_8);
        Matcher baseTheme = Pattern.compile(
                "<style name=\"Theme.WebHTV\"[^>]*>(.*?)\\n    </style>", Pattern.DOTALL).matcher(base);
        assertTrue("必须能读到 Theme.WebHTV 定义", baseTheme.find());
        String baseBody = baseTheme.group(1);
        for (String attr : new String[]{"tvFocusRing", "tvCurrentRing", "tvNormalStroke",
                "tvPlayerRing", "tvPlayerCurrentRing"}) {
            assertTrue("Theme.WebHTV 必须绑定 " + attr
                    + "（对话框/BottomSheet 的父主题链不经过 Theme.Base，未绑定会静默退化）",
                    baseBody.contains("name=\"" + attr + "\">"));
        }
    }

    private static void assertResourceMapping(ThemeTokens tokens, Path path) throws Exception {
        Map<String, Integer> colors = colors(path);
        // 计数只覆盖 webhtv_color_* 这一族（见上面的 COLOR 模式），且刻意冻结：新增语义色
        // 必须在这里显式承认一次，避免"顺手加个颜色"绕过角色契约。
        // 51 → 54：新增 webhtv_color_badge_{progress,site,year}（卡片角标底色，
        // 见 docs/TV-CARD-BADGE-CONTRAST-20261012.md）。它们不属于 ThemeTokens 的 49 项
        // 角色契约，只是同一命名族里的固定语义色，与 webhtv_on_wallpaper 同类。
        // 54 → 55：新增 webhtv_color_on_wallpaper_error（壁纸上的错误前景色，
        // 见 docs/TV-ERROR-STATE-20261012.md），同理不属于角色契约。
        // 55 → 56：新增 webhtv_color_error_value_bg（错误取值文字的实心深色底，
        // 让错误正文稳过 4.5:1，见 docs/TV-ERROR-STATE-CLOSEOUT-20261013.md）。
        // 56 → 58：新增 webhtv_color_item_overlay / webhtv_color_on_item_overlay（条目遮罩的
        // 底色与文字色，见 ThemeTokens.colorItemOverlay / colorOnItemOverlay）。它们**属于**
        // 角色契约——默认值必须与 ThemeTokens 两张表逐值一致，否则默认外观就变了。
        assertEquals(58, colors.size());
        for (RecordComponent component : ThemeTokens.class.getRecordComponents()) {
            if (!component.getType().equals(int.class)) continue;
            String name = component.getName();
            String resource = "webhtv_color_" + snakeCase(name.substring("color".length()));
            Integer actual = colors.get(resource);
            assertTrue(path + " missing " + resource, actual != null);
            assertEquals(resource, component.getAccessor().invoke(tokens), actual);
        }
    }

    private static Map<String, Integer> colors(Path path) throws Exception {
        Map<String, Integer> colors = new LinkedHashMap<>();
        Matcher matcher = COLOR.matcher(Files.readString(path, StandardCharsets.UTF_8));
        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(2), 16);
            colors.put(matcher.group(1), matcher.group(2).length() == 6 ? (int) (0xFF000000L | value) : (int) value);
        }
        return colors;
    }

    private static String attrName(String getter) {
        return "webhtv" + Character.toUpperCase(getter.charAt(0)) + getter.substring(1);
    }

    private static String snakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.US);
    }
}
