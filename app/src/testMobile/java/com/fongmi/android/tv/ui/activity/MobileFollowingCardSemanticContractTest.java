package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 手机端「追更」卡片的语义化契约。
 *
 * <p>{@code mobile/res/layout/item_following.xml} 有两处写死的调色板色值，而 {@code main}
 * 源集的同名文件在同一位置用的是语义属性——即手机端独有的偏离：
 *
 * <table>
 *   <tr><th>位置</th><th>mobile（改动前）</th><th>main</th></tr>
 *   <tr><td>L73「新 N 集」角标</td><td>{@code #174EA6}</td><td>{@code ?attr/colorPrimary}</td></tr>
 *   <tr><td>L124「检查失败」</td><td>{@code #B3261E}</td><td>{@code ?attr/colorError}</td></tr>
 * </table>
 *
 * <p>底材是语义 token（角标走 {@code shape_sync_direction} = {@code ?attr/colorSurfaceContainer}，
 * 随主题变），文字却不跟——实测深色底上只有 1.72:1 与 2.07:1（正文门槛 4.5:1），
 * 深色模式下基本不可读。而 {@code #B3261E} 正是浅色表的 M3 错误红，承载的却是「检查失败」
 * 这个 §9.9 管辖的错误态。
 */
public class MobileFollowingCardSemanticContractTest {

    private static final String MOBILE = "app/src/mobile/res/layout/item_following.xml";
    private static final String MAIN = "app/src/main/res/layout/item_following.xml";
    private static final Pattern RAW_HEX = Pattern.compile("\"#[0-9A-Fa-f]{6,8}\"");
    private static final Pattern TEXT_COLOR = Pattern.compile("android:textColor=\"([^\"]+)\"");

    /** 两处偏离必须都对齐到语义属性。 */
    @Test
    public void badgeAndErrorUseSemanticRolesLikeTheSharedLayoutDoes() throws Exception {
        String mobile = read(MOBILE);
        String badge = tagBlock(mobile, "@+id/badge");
        String error = tagBlock(mobile, "@+id/error");
        assertTrue("角标必须走语义 primary（与 main 版一致）", badge.contains("?attr/colorPrimary"));
        assertTrue("错误文字必须走语义 error（与 main 版一致）", error.contains("?attr/colorError"));
        assertFalse("角标不得再写死 #174EA6", badge.contains("#174EA6"));
        assertFalse("错误文字不得再写死 #B3261E", error.contains("#B3261E"));
    }

    /**
     * 更强的收口：这个布局里**不应再有任何**写死的 textColor。
     *
     * <p>底材全部是语义 token（{@code following_card_bg} → {@code surface_container_high}、
     * {@code shape_sync_direction} → {@code surface_container}），底材跟着主题走而文字不跟，
     * 就是「宿主变了、文字没变」的错配。
     */
    @Test
    public void noTextColourInThisLayoutIsHardcoded() throws Exception {
        String source = withoutXmlComments(read(MOBILE));
        Matcher colours = TEXT_COLOR.matcher(source);
        while (colours.find()) {
            String value = colours.group(1);
            boolean semantic = value.startsWith("?attr/") || value.startsWith("@color/");
            assertTrue("item_following 的 textColor 必须语义化，发现写死值：" + value, semantic);
        }
    }

    /**
     * 两端同名布局必须用同一套语义角色——这条防止手机端再次漂移。
     */
    @Test
    public void bothFlavoursAgreeOnTheSemanticRoles() throws Exception {
        for (String path : new String[]{MOBILE, MAIN}) {
            String source = read(path);
            assertTrue(path + " 的角标必须走 colorPrimary",
                    tagBlock(source, "@+id/badge").contains("?attr/colorPrimary"));
            assertTrue(path + " 的错误文字必须走 colorError",
                    tagBlock(source, "@+id/error").contains("?attr/colorError"));
        }
    }

    // ------------------------------------------------------------------ helpers

    /** 取某个 id 所在标签的属性文本（到该标签结束为止）。 */
    private static String tagBlock(String source, String id) {
        int at = source.indexOf(id);
        assertTrue("必须存在 " + id, at > 0);
        int end = source.length();
        int selfClosing = source.indexOf("/>", at);
        if (selfClosing >= 0) end = Math.min(end, selfClosing);
        int closing = source.indexOf(">", at);
        if (closing >= 0) end = Math.min(end, closing);
        return source.substring(at, end);
    }

    private static String withoutXmlComments(String source) {
        return source.replaceAll("(?s)<!--.*?-->", " ");
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
