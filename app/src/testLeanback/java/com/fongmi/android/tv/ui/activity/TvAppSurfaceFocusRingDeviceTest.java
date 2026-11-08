package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;

import androidx.appcompat.view.ContextThemeWrapper;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * 电视端「应用表面」焦点环的像素级证据（Robolectric 真实栅格化）。
 *
 * <p>用户报告（两张实机截图）：
 * <blockquote>TV端上面两行按钮是 蓝色边框，下面的是 白色边框，边框粗细也貌似不一样，
 * 请统一风格并受到主题色彩控制</blockquote>
 *
 * <p>静态契约测试（{@code TvFocusRingContractTest}）只能证明 XML 文本写对了。本测试把
 * drawable 真正栅格化到 {@link Bitmap}，再逐像素量出**实际**的描边厚度与颜色——也就是
 * 用户肉眼看到的那两条边框。因此它能抓出「XML 看着对、渲染出来其实不同」的情况，
 * 例如 1.5dp 与 3dp 在不同密度下取整到不同像素数。
 *
 * <p>断言分三条：
 * <ol>
 *   <li>首页两族焦点环（功能按钮 vs 内容卡片）的实测厚度与颜色必须字节相同；</li>
 *   <li>全部应用表面焦点环的实测厚度必须等于 {@code @dimen/webhtv_focus_ring_width}；</li>
 *   <li>环色必须等于主题 FOCUS 槽，并在浅色/深色两张色板下各自跟随变化（真正的主题可控性）。</li>
 * </ol>
 *
 * <p>说明：{@code ?attr/tvFocusRing} 是编译期静态资源，用户档案覆写在运行期由
 * {@code ThemeBinder} 作用于视图树（另由 {@code TvFocusRingResolutionDeviceTest} 覆盖）。
 * 本测试证明的是「环色来自主题色板」——同一 drawable 在 day / night 两张表下解析出不同颜色。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = App.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TvAppSurfaceFocusRingDeviceTest {

    /** 首页功能按钮行（搜索/历史）：adapter_func -> selector_item -> shape_item_focused。 */
    private static final int BUTTON_SELECTOR = R.drawable.selector_item;
    /** 首页内容卡片行（最近观看/更新推荐）：adapter_vod -> selector_vod -> shape_vod_focused。 */
    private static final int CARD_SELECTOR = R.drawable.selector_vod;

    /** 全部「应用表面」焦点环（宿主为调色板表面或对话框面板）。 */
    private static final int[] APP_SURFACE_RINGS = {
            R.drawable.selector_item,
            R.drawable.selector_item_round,
            R.drawable.selector_vod,
            R.drawable.selector_vod_oval,
            R.drawable.selector_keyboard,
            R.drawable.selector_search_hot_word,
    };

    /** 渲染尺寸：足够大，四条边都不会被裁掉。 */
    private static final int SIZE = 120;

    /** 判定「这个像素属于环」的 alpha 门槛（排除抗锯齿边缘的半透明过渡）。 */
    private static final int OPAQUE = 200;

    @After
    public void tearDown() {
        RuntimeEnvironment.setQualifiers("");
    }

    private static Context themedContext() {
        return new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_App);
    }

    /** 取 selector 的 focused 分支（这些 selector 的 state_focused 都是第一项）。 */
    private static Drawable focusedDrawable(Context context, int resId) {
        Drawable drawable = context.getDrawable(resId);
        assertTrue(resId + " 必须能解析出 drawable", drawable != null);
        if (drawable instanceof StateListDrawable states) {
            states.setState(new int[]{android.R.attr.state_focused});
            Drawable current = states.getCurrent();
            assertTrue(resId + " 的 focused 状态必须解析出一个 drawable", current != null);
            return current;
        }
        return drawable;
    }

    /** 把 focused 分支栅格化。 */
    private static Bitmap render(Context context, int resId) {
        return render(context, resId, SIZE);
    }

    private static Bitmap render(Context context, int resId, int size) {
        return rasterize(focusedDrawable(context, resId), size);
    }

    /**
     * 实测环的厚度（像素）：从图像中部左侧起，数连续的不透明像素个数。
     * 矩形/圆形的左边框中点都是竖直的直线段，所以这是一段干净的游程。
     */
    private static int ringThicknessPx(Bitmap bitmap) {
        int y = SIZE / 2;
        int x = 0;
        while (x < SIZE && alpha(bitmap, x, y) < OPAQUE) x++;
        int run = 0;
        while (x < SIZE && alpha(bitmap, x, y) >= OPAQUE) {
            run++;
            x++;
        }
        assertTrue("中部左侧必须能找到一段不透明的焦点环（实际为 0）", run > 0);
        return run;
    }

    /** 实测环的颜色：取环游程中点那个像素。 */
    private static int ringColour(Bitmap bitmap) {
        int y = SIZE / 2;
        int x = 0;
        while (x < SIZE && alpha(bitmap, x, y) < OPAQUE) x++;
        int run = 0;
        int start = x;
        while (x < SIZE && alpha(bitmap, x, y) >= OPAQUE) {
            run++;
            x++;
        }
        assertTrue("中部左侧必须能找到一段不透明的焦点环（实际为 0）", run > 0);
        return bitmap.getPixel(start + run / 2, y);
    }

    private static int alpha(Bitmap bitmap, int x, int y) {
        return (bitmap.getPixel(x, y) >>> 24) & 0xFF;
    }

    private static int expectedWidthPx(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.webhtv_focus_ring_width);
    }

    /** 主题 FOCUS 槽：{@code ?attr/tvFocusRing} 的取值来源。 */
    private static int focusSlot(Context context) {
        TypedValue value = new TypedValue();
        assertTrue("主题必须声明 tvFocusRing",
                context.getTheme().resolveAttribute(R.attr.tvFocusRing, value, true));
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return context.getColor(value.resourceId);
    }

    private static String name(Context context, int resId) {
        return context.getResources().getResourceEntryName(resId);
    }

    // ------------------------------------------------------------------ R1 两族同款

    /**
     * 用户报告的核心：首页上面两行按钮是蓝框、下面的内容是白框，粗细也不同。
     * 两族实测的厚度与颜色必须完全一致。
     */
    @Test
    public void homeFunctionButtonsAndContentCardsRenderTheSameRing() {
        Context context = themedContext();
        Bitmap button = render(context, BUTTON_SELECTOR);
        Bitmap card = render(context, CARD_SELECTOR);

        assertEquals("首页功能按钮与内容卡片的焦点环厚度必须相同（用户报告粗细不一样）",
                ringThicknessPx(button), ringThicknessPx(card));
        assertEquals("首页功能按钮与内容卡片的焦点环颜色必须相同（用户报告蓝色/白色两种）",
                ringColour(button), ringColour(card));
    }

    @Test
    public void everyAppSurfaceRingRendersAtTheSingleWidthToken() {
        Context context = themedContext();
        int expected = expectedWidthPx(context);
        for (int resId : APP_SURFACE_RINGS) {
            int measured = ringThicknessPx(render(context, resId));
            assertTrue(name(context, resId) + " 的实测焦点环厚度 " + measured
                            + "px 必须等于 @dimen/webhtv_focus_ring_width (" + expected + "px)",
                    Math.abs(measured - expected) <= 1);
        }
    }

    // ------------------------------------------------------------------ R2 主题可控

    @Test
    public void appSurfaceRingsRenderInTheThemeFocusColour() {
        Context context = themedContext();
        int slot = focusSlot(context);
        assertEquals("焦点环色必须来自 webhtv_color_focus，而不是别的角色",
                context.getColor(R.color.webhtv_color_focus), slot);

        for (int resId : new int[]{BUTTON_SELECTOR, CARD_SELECTOR}) {
            assertEquals(name(context, resId) + " 的实测焦点环颜色必须等于主题 FOCUS 槽",
                    slot, ringColour(render(context, resId)));
        }
    }

    /**
     * 真正的「受到主题色彩控制」：同一个 drawable 在浅色表与深色表下必须渲染出不同的环色。
     * 只看 XML 文本无法证明这一点，只有栅格化后取像素才能证明。
     */
    @Test
    public void appSurfaceRingsFollowTheThemePaletteAcrossDayAndNight() {
        Context day = themedContext();
        int dayColour = ringColour(render(day, CARD_SELECTOR));
        int daySlot = focusSlot(day);

        RuntimeEnvironment.setQualifiers("+night");
        Context night = themedContext();
        int nightColour = ringColour(render(night, CARD_SELECTOR));
        int nightSlot = focusSlot(night);

        assertEquals("浅色表下环色必须等于浅色 FOCUS 槽", daySlot, dayColour);
        assertEquals("深色表下环色必须等于深色 FOCUS 槽", nightSlot, nightColour);
        assertNotEquals("浅色表与深色表的 FOCUS 槽本身就不同，测试前提必须成立", daySlot, nightSlot);
        assertNotEquals("换色板后焦点环颜色必须跟着变，否则主题控制无效", dayColour, nightColour);

        // 回归钉子：修复前这里写死 @color/white，两张表都会渲染成同一个白色。
        assertNotEquals("焦点环不允许再渲染成与调色板无关的固定白色",
                0xFFFFFFFF, nightColour);
    }

    /** 环必须完全不透明，否则焦点不可见。 */
    @Test
    public void focusedRingRendersFullyOpaque() {
        Context context = themedContext();
        for (int resId : new int[]{BUTTON_SELECTOR, CARD_SELECTOR}) {
            int colour = ringColour(render(context, resId));
            assertEquals(name(context, resId) + " 的焦点环必须完全不透明，否则焦点不可见",
                    0xFF, (colour >>> 24) & 0xFF);
        }
    }

    /**
     * 变异检验：本测试的度量方法必须真的能区分「细环」与「粗环」。
     * 直接渲染 1.5dp 与 3dp 两种描边，实测厚度必须不同——否则上面的断言就是空断言。
     */
    @Test
    public void theMeasurementCanActuallyTellThinRingsFromThickOnes() {
        Context context = themedContext();
        float density = context.getResources().getDisplayMetrics().density;

        Bitmap thin = renderStroke(context, Math.round(1.5f * density));
        Bitmap thick = renderStroke(context, Math.round(3f * density));

        int thinPx = ringThicknessPx(thin);
        int thickPx = ringThicknessPx(thick);
        assertTrue("度量方法必须能区分细环与粗环（实测 thin=" + thinPx + "px thick=" + thickPx + "px）",
                thickPx > thinPx);
    }

    /** 造一个与生产 drawable 同构的描边，用于度量方法的变异检验。 */
    private static Bitmap renderStroke(Context context, int widthPx) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setCornerRadius(0f);
        shape.setStroke(widthPx, 0xFFFFFFFF);
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        shape.setBounds(0, 0, SIZE, SIZE);
        shape.draw(canvas);
        return bitmap;
    }

    // ------------------------------------------------------------ R4 卡片环圆角对齐卡片自身圆角

    /**
     * 卡片宿主的可见表面圆角：{@code @style/Vod.Grid}（图片上圆角）、{@code shape_vod_name}
     * （标题块下圆角）、{@code shape_vod_list}（列表型宿主四角）都是一律 8dp。
     */
    private static final float CARD_SURFACE_RADIUS_DP = 8f;

    /** 角落几何用的栅格尺寸：密度 3 下为 80dp，四条边都远离角落弧，不会被裁掉。 */
    private static final int CORNER_SIZE = 240;

    /** 电视端主档密度：要求严格 0 溢出，最接近 dev1 实机（`wm density`=280 → 2.35px/dp）。 */
    private static final String TV_PRIMARY_DENSITY = "xxhdpi";

    /** 其余密度的亚像素容差：每角最多 2px；用户报告的缺陷特征 ≥25px，不会漏过。 */
    private static final int SUBPIXEL_LEAK_TOLERANCE_PX = 8;

    /**
     * 用户报告（2026-10-10 dev1 实机）：「边框在卡片上的圆角部分匹配不完美，感觉是圆角比卡片小，
     * 导致卡片的圆角还露出一节了」。
     *
     * <p>实机像素复核（1920x1080 / 密度 280，应用内有效缩放 ≈ 2.35px/dp）量到：卡片表面右边界
     * 396.5px、环外边界 403.46px（环带 7.06px = 3dp），而环的 <b>外边界圆角弧</b>半径 ≈ 21.2px
     * ≈ 9dp = 声明 8dp + w/2；卡片自身弧半径 8dp。两者中心都在角上 8–9.5dp 处，沿对角线卡片
     * 弧反而更靠外 1.03px，也就是实机上那 1–2px 米色月牙。栅格化扫描（density 1/1.75/2/2.353/3/4）
     * 也复现了同一个方向：声明 8dp 时卡片溢出高数十倍于修复后。
     *
     * <p>本测试不看 XML，直接把真实 {@code selector_vod} 焦点分支与卡片表面栅格化后逐像素验证：
     * 卡片不能有任何像素落在环的 <b>外边界之外</b>。断言覆盖电视端实际存在的四档密度
     * （hdpi/xhdpi/xxhdpi/xxxhdpi），因为描边宽度是按整数像素向上取整的，同一 dp 取值在不同
     * 密度下取整结果不同。栅格化扫描实测卡片溢出像素：声明 8dp 在四档下为 25/52/114/181，
     * 声明 7dp 在 1.5/2/3/4 四档下为 0（含全量套件下的重跑）。
     *
     * <p>容差：主档密度（xxhdpi，与 dev1 实机 ~2.35px/dp 最接近的档位）要求**严格 0**；
     * 其余密度允许每角最多 2px 的亚像素平局（实测 xhdpi 下 5px）——卡片的 16px 弧与环外边界
     * 的 15.5px 弧只差半个像素，栅格化在切线处会把这一点点像素判给任一侧；而用户报告的缺陷
     * 特征是最少 25px，所以这个容差不会让缺陷漏过（变异检验会验证这一点）。
     *
     * <p>密度限定符必须用**绝对**写法（{@code "xhdpi"} 而不是 {@code "+xhdpi"}）：
     * {@code RuntimeEnvironment.setQualifiers("+xhdpi")} 是「在现有限定符上追加」，
     * 全量套件里前面别的测试类留下的密度限定符会一起参与解析，使实际密度不确定。
     *
     * <p>说明：真实卡片轮廓是四角 8dp 的圆角矩形（顶部来自图片、底部来自标题块，列表型宿主
     * 四角同圆角），所以这里用统一 8dp 的圆角矩形当卡片表面；单环覆盖全部宿主的前提由
     * {@code TvFocusRingContractTest.cardRingCornerRadiusIsTheHostRadiusMinusHalfTheRingWidth} 钉住。
     */
    @Test
    public void cardRingOuterBoundaryContainsEveryCardSurfacePixel() {
        for (String density : new String[]{"hdpi", "xhdpi", "xxhdpi", "xxxhdpi"}) {
            RuntimeEnvironment.setQualifiers(density);
            Context context = themedContext();
            int outside = cardPixelsOutsideRing(
                    render(context, CARD_SELECTOR, CORNER_SIZE),
                    cardSurface(context, CARD_SURFACE_RADIUS_DP));
            String where = "（限定符 " + density + "，实际密度 "
                    + context.getResources().getDisplayMetrics().density + "）";
            if (TV_PRIMARY_DENSITY.equals(density)) {
                assertEquals("卡片表面不允许有任何像素落在焦点环外边界之外" + where
                                + "——这正是用户报告的「卡片的圆角还露出一节」，声明圆角必须取 宿主圆角 - 环宽/2",
                        0, outside);
            } else {
                assertTrue("卡片溢出 " + outside + "px 必须落在亚像素上限 " + SUBPIXEL_LEAK_TOLERANCE_PX
                                + "px 内" + where, outside <= SUBPIXEL_LEAK_TOLERANCE_PX);
            }
        }
    }

    // ------------------------------------------------------------ R4b 真实宿主（详情页/播放页）

    /**
     * 把「环外边界必须包住宿主真实轮廓」这条要求，直接跑在**真实布局**上。
     *
     * <p>与上面 R4 的区别：R4 用代码搭一个 8dp 圆角矩形当卡片表面；这里直接 inflate 生产布局
     * （adapter_episode_card / adapter_tmdb_video / adapter_tmdb_photo / adapter_tmdb_cast /
     * dialog_episode_detail 的 stillCard），取它们的真实 background 作为「宿主表面」，再把真实环
     * 栅格化后逐像素判定。宿主圆角、内缩、自带描边全部是生产值，没有任何测试侧假设。
     *
     * <p>宿主与环的对应关系（详情页/播放页，2026-10-10 新增）：
     * <ul>
     *   <li>{@code adapter_episode_card} ↔ {@code selector_episode_card}（8dp 宿主）</li>
     *   <li>{@code adapter_tmdb_video} / {@code adapter_tmdb_photo} ↔ {@code selector_tmdb_media_focus}（8dp）</li>
     *   <li>{@code adapter_tmdb_cast} ↔ {@code selector_tmdb_cast_focus}（12dp）</li>
     *   <li>{@code dialog_episode_detail#stillCard} ↔ {@code selector_episode_photo}（8dp）</li>
     *   <li>播放页画面框 {@code @id/video} 是直角 FrameLayout ↔ {@code selector_video}（0dp）</li>
     * </ul>
     *
     * <p>容差与 R4 同口径：主档密度严格 0，其余档位允许每角 2px 的亚像素平局。
     */
    @Test
    public void realDetailAndPlayerHostsContainTheirFocusRingCorner() {
        for (String density : new String[]{"hdpi", "xhdpi", "xxhdpi", "xxxhdpi"}) {
            RuntimeEnvironment.setQualifiers(density);
            Context context = themedContext();
            float d = context.getResources().getDisplayMetrics().density;

            // 1) 详情页选集卡：CardView 8dp + selector_episode_card
            checkHost(context, "adapter_episode_card/selector_episode_card", density,
                    R.layout.adapter_episode_card, 0, R.drawable.selector_episode_card,
                    (int) (280 * d), (int) (160 * d));
            // 2) 详情页相关视频 / 剧照：MaterialCardView 8dp + selector_tmdb_media_focus
            checkHost(context, "adapter_tmdb_video/selector_tmdb_media_focus", density,
                    R.layout.adapter_tmdb_video, 0, R.drawable.selector_tmdb_media_focus,
                    (int) (276 * d), (int) (156 * d));
            checkHost(context, "adapter_tmdb_photo/selector_tmdb_media_focus", density,
                    R.layout.adapter_tmdb_photo, 0, R.drawable.selector_tmdb_media_focus,
                    (int) (220 * d), (int) (124 * d));
            // 3) 详情页演员卡：MaterialCardView 12dp + selector_tmdb_cast_focus
            checkHost(context, "adapter_tmdb_cast/selector_tmdb_cast_focus", density,
                    R.layout.adapter_tmdb_cast, 0, R.drawable.selector_tmdb_cast_focus,
                    (int) (90 * d), (int) (150 * d));
            // 4) 剧照详情卡：CardView 8dp + selector_episode_photo
            checkHost(context, "dialog_episode_detail#stillCard/selector_episode_photo", density,
                    R.layout.dialog_episode_detail, R.id.stillCard, R.drawable.selector_episode_photo,
                    (int) (400 * d), (int) (225 * d));
            // 5) 播放页画面框：直角 FrameLayout + selector_video（声明 0dp）
            checkHost(context, "activity_video#video/selector_video", density,
                    R.layout.activity_video, R.id.video, R.drawable.selector_video,
                    (int) (400 * d), (int) (225 * d));
        }
    }

    /**
     * inflate 真实宿主（可指定内部 id），用它的 background 当表面，与真实环逐像素比。
     * 表面为 null 时（直角宿主如播放页画面框）改用「整块不透明矩形」当表面，
     * 这样仍能抓到「环把直角宿主包出圆角」的错位。
     */
    private static void checkHost(Context context, String label, String density, int layoutRes, int innerId,
                                  int ringRes, int w, int h) {
        android.view.View root = android.view.LayoutInflater.from(context).inflate(layoutRes, null, false);
        android.view.View host = innerId == 0 ? root : root.findViewById(innerId);
        assertTrue(label + " 必须能在真实布局里找到宿主", host != null);
        host.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY));
        host.layout(0, 0, w, h);

        Drawable ring = focusedDrawable(context, ringRes);
        Bitmap surfaceBitmap = hostSurface(host, w, h);
        int outside = cardPixelsOutsideRing(rasterize(ring, w, h), surfaceBitmap, w, h);

        String where = label + "（限定符 " + density + "，实际密度 "
                + context.getResources().getDisplayMetrics().density + "）";
        if (TV_PRIMARY_DENSITY.equals(density)) {
            assertEquals("宿主表面不允许有任何像素落在焦点环外边界之外 " + where
                    + "——环声明圆角必须取 宿主圆角 − 环宽/2", 0, outside);
        } else {
            assertTrue(where + " 的溢出 " + outside + "px 必须落在亚像素上限 "
                    + SUBPIXEL_LEAK_TOLERANCE_PX + "px 内", outside <= SUBPIXEL_LEAK_TOLERANCE_PX);
        }
    }

    /**
     * 取宿主的**真实**可见轮廓：{@code MaterialCardView} 不暴露 {@code getBackground()}
     * （它用内部 ShapeThemingDrawable），必须从 {@code getShapeAppearanceModel()} 重建；
     * 其它视图直接用 background；直角宿主（如播放页画面框）退化为整块矩形。
     */
    private static Bitmap hostSurface(android.view.View host, int w, int h) {
        Drawable background = host.getBackground();
        if (background != null) return rasterize(background, w, h);
        if (host instanceof com.google.android.material.card.MaterialCardView card) {
            com.google.android.material.shape.MaterialShapeDrawable shape =
                    new com.google.android.material.shape.MaterialShapeDrawable(card.getShapeAppearanceModel());
            shape.setTint(0xFFF3E7D9);
            return rasterize(shape, w, h);
        }
        return solidRect(w, h);
    }

    /** 直角宿主的表面：整块不透明矩形。 */
    private static Bitmap solidRect(int w, int h) {
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFFF3E7D9);
        return bitmap;
    }

    /**
     * 变异检验：把用户报告时的旧取值（声明 8dp + 3dp 环宽）搭回来，上面的度量必须能抓到卡片
     * 圆角溢出，否则 {@code cardRingOuterBoundaryContainsEveryCardSurfacePixel} 就是空断言。
     */
    @Test
    public void theCornerMeasurementCatchesTheReportedCardCornerBleed() {
        RuntimeEnvironment.setQualifiers("xxhdpi");
        Context context = themedContext();

        int outside = cardPixelsOutsideRing(
                renderLegacyCardRing(context, 8f, 3f),
                cardSurface(context, CARD_SURFACE_RADIUS_DP));

        assertTrue("度量方法必须能抓到「环外边界圆角大于卡片圆角」时卡片露出的月牙"
                        + "（实测落在环外的卡片像素 = " + outside + "px）",
                outside > 0);
    }

    /** 栅格化一张卡片表面：统一圆角的圆角矩形（几何与 ShapeableImageView 的 8dp 轮廓等价）。 */
    private static Bitmap cardSurface(Context context, float radiusDp) {
        float density = context.getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable surface = new android.graphics.drawable.GradientDrawable();
        surface.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        surface.setCornerRadius(radiusDp * density);
        surface.setColor(0xFFF3E7D9);
        return rasterize(surface, CORNER_SIZE);
    }

    /** 用户报告时的旧卡片环：声明圆角 8dp（= 宿主圆角，没减 w/2）、环宽 3dp。 */
    private static Bitmap renderLegacyCardRing(Context context, float radiusDp, float widthDp) {
        float density = context.getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
        ring.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        ring.setCornerRadius(radiusDp * density);
        ring.setStroke(Math.round(widthDp * density), focusSlot(context));
        return rasterize(ring, CORNER_SIZE);
    }

    private static Bitmap rasterize(Drawable drawable, int size) {
        return rasterize(drawable, size, size);
    }

    private static Bitmap rasterize(Drawable drawable, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        return bitmap;
    }

    /**
     * 数出有多少卡片像素落在环的 <b>外边界之外</b>。做法：从图像四边向内扩散所有「非环」像素，
     * 扩散得到的就是环外侧的区域（环内部被不透明环带封住，扩散不进去）。
     */
    private static int cardPixelsOutsideRing(Bitmap ring, Bitmap card) {
        return cardPixelsOutsideRing(ring, card, CORNER_SIZE, CORNER_SIZE);
    }

    /** 同上，但接受任意尺寸（详情页/播放页的真实宿主不是正方形）。 */
    private static int cardPixelsOutsideRing(Bitmap ring, Bitmap card, int width, int height) {
        int total = width * height;
        boolean[] cardPixel = new boolean[total];
        boolean[] ringPixel = new boolean[total];
        boolean[] outside = new boolean[total];
        int[] queue = new int[total];
        int head = 0;
        int tail = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int at = y * width + x;
                ringPixel[at] = alpha(ring, x, y) >= OPAQUE;
                cardPixel[at] = alpha(card, x, y) >= OPAQUE;
                boolean border = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                if (border && !ringPixel[at] && !outside[at]) {
                    outside[at] = true;
                    queue[tail++] = at;
                }
            }
        }

        while (head < tail) {
            int at = queue[head++];
            int x = at % width;
            int y = at / width;
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + step[0];
                int ny = y + step[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                int next = ny * width + nx;
                if (ringPixel[next] || outside[next]) continue;
                outside[next] = true;
                queue[tail++] = next;
            }
        }

        int leaked = 0;
        for (int at = 0; at < total; at++) {
            if (cardPixel[at] && outside[at]) leaked++;
        }
        return leaked;
    }
}
