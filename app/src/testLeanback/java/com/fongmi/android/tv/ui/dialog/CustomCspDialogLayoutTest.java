package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Dialog;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.appcompat.widget.LinearLayoutCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogCustomCspBinding;
import com.fongmi.android.tv.ui.custom.CustomNestedScrollView;
import com.fongmi.android.tv.utils.ResUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/**
 * 用户报告两次，都是同一个界面「增强功能 → 站点注入」：
 * <ol>
 *   <li>竖屏手机条目多时最下方取消/确定被压扁。设备实测（dev1 模拟器 1080×2160@440dpi 竖屏，
 *       5 条注入）：按钮由声明高度 40dp 变 34.9dp，末张卡片操作行只剩 9.1dp。</li>
 *   <li>在 1920×1080 横屏设备上“没有全屏”。设备实测：窗口 1459×1058 居中，左右各空 230px
 *       （旧横屏策略是 0.76×0.98 比例弹窗）。</li>
 * </ol>
 *
 * <p>根因是旧尺寸策略两种朝向都不铺满：竖屏 {@code WRAP_CONTENT} 窗口 + 滚动区
 * {@code wrap_content} + {@code 0.58H} 上限 + 无 weight（内容超出即把底部按钮区挤出窗口裁掉）；
 * 横屏 {@code 0.76×0.98} 居中比例弹窗。修复统一为全屏整页。</p>
 *
 * <p>本测试用真实 framework 测量代码把修复后的契约固定下来：</p>
 * <ol>
 *   <li>竖屏与横屏两种朝向都必须铺满：root 高度等于窗口可用高度、按钮区贴底、滚动区吃剩余空间、</li>
 *   <li>Material 弹窗面板（{@code @id/custom} → customPanel → parentPanel）默认 {@code wrap_content}，
 *       必须一并改成 {@code match_parent}，否则内容比窗口短时页面底部会留空隙、按钮区不贴底；</li>
 *   <li>短内容（文本模式 JSON 编辑器、空搜索结果）同样铺满；</li>
 *   <li>真实横屏配置（{@code land} qualifier，{@code ResUtil.isLand} 为真）也必须铺满，锁死
 *       “按朝向挑一套比例手算窗口”的横屏分支回归；</li>
 *   <li>变异检验：同一套断言在修复前的两种旧尺寸策略下都必然失败，证明断言确实能抓住这两次缺陷。</li>
 * </ol>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CustomCspDialogLayoutTest {

    /** 竖屏：用户现场机型 1080×2400@440dpi ≈ 392.7×872.7dp，另附小屏/大屏/竖屏平板与键盘顶掉 300dp 的窗口。 */
    private static final int[][] PORTRAIT_SCREENS = {
            {393, 873},   // 用户现场机型
            {360, 640},   // 小屏手机
            {412, 915},   // 大屏手机
            {617, 1097},  // 竖屏平板
            {393, 573},   // 用户机型 + 屏幕键盘顶掉 300dp（ADJUST_RESIZE 后的窗口）
    };

    /** 横屏：用户复测机型 1920×1080@280dpi ≈ 685.7×385.7dp，另附电视 960×540 与横屏手机 915×412。 */
    private static final int[][] LANDSCAPE_SCREENS = {
            {686, 386},   // 用户复测机型（dev1 模拟器 1920×1080@280）
            {960, 540},   // Android TV 1080p 常用 dp 尺寸
            {915, 412},   // 横屏手机
    };

    private DialogCustomCspBinding binding;

    @Before
    public void setUp() {
        new App();
        ContextThemeWrapper context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_WebHTV_Dialog);
        binding = DialogCustomCspBinding.inflate(LayoutInflater.from(context));
    }

    /** 两种朝向都必须铺满整页，且底部按钮区保持 40dp 并贴底。 */
    @Test
    public void bothOrientationsFillTheWholePageAndKeepFooterAtNaturalHeight() {
        for (int[] screen : PORTRAIT_SCREENS) assertFullPage(screen, "竖屏");
        for (int[] screen : LANDSCAPE_SCREENS) assertFullPage(screen, "横屏");
    }

    /** 窗口参数本身也必须铺满：宽高 MATCH_PARENT，横屏不再按 0.76 手算宽度。 */
    @Test
    public void windowIsMatchParentInsteadOfProportionalForEveryOrientation() {
        for (int[] screen : PORTRAIT_SCREENS) assertWindowFills(screen, "竖屏");
        for (int[] screen : LANDSCAPE_SCREENS) assertWindowFills(screen, "横屏");
    }

    /**
     * 内容比窗口短时（文本模式 JSON 编辑器、空搜索结果）也必须撑满整页：面板链默认 wrap_content，
     * 靠 {@code expandToWindow} 把 {@code @id/custom → customPanel → parentPanel} 一起改成 match_parent，
     * 否则 root 的 match_parent 会退化成按内容高度，页面底部露出后面的界面、按钮区不贴底。
     */
    @Test
    public void shortContentStillFillsTheWholePage() {
        int[][] screens = {{393, 873}, {686, 386}};
        for (int[] screen : screens) {
            int width = dp(screen[0]);
            int available = dp(screen[1]) - statusBarPx();
            ViewGroup panel = panelChain();
            ViewGroup.LayoutParams rootParams = binding.root.getLayoutParams();
            rootParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            binding.root.setLayoutParams(rootParams);
            CustomNestedScrollView scroll = binding.contentScroll;
            LinearLayoutCompat.LayoutParams scrollParams = (LinearLayoutCompat.LayoutParams) scroll.getLayoutParams();
            scrollParams.height = dp(120);
            scrollParams.weight = 0;
            scroll.setLayoutParams(scrollParams);
            CustomCspDialog.applyPageSizing(hostWindow(), binding.root, scroll);
            measure(panel, width, available);
            String label = describe(screen);
            assertEquals(label + " 短内容也必须撑满整页", available, binding.root.getMeasuredHeight());
            assertEquals(label + " 短内容时按钮区仍要贴底", available - binding.root.getPaddingBottom(), binding.footer.getBottom());
            assertEquals(label + " 短内容时按钮区仍保持 40dp", dp(40), binding.footer.getMeasuredHeight());
        }
    }

    /**
     * 变异检验：换回修复前的两套旧参数，在内容超过窗口可用高度的机型上按钮区必然被摆到页面之外，
     * 且横屏窗口宽度只有 0.76×屏宽——分别对应“被压扁”和“没有全屏”两次用户报告。
     */
    @Test
    public void legacyParamsReproduceBothReportedDefects() {
        // 旧竖屏：窗口按内容高度、滚动区 0.58H 上限、无 weight → 按钮区被挤出页面。
        int portraitWidth = dp(360);
        int portraitScreenHeight = dp(480);
        ViewGroup portraitPanel = panelChain();
        tallList();
        ViewGroup.LayoutParams rootParams = binding.root.getLayoutParams();
        rootParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.root.setLayoutParams(rootParams);
        LinearLayoutCompat.LayoutParams scrollParams = (LinearLayoutCompat.LayoutParams) binding.contentScroll.getLayoutParams();
        scrollParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        scrollParams.weight = 0;
        binding.contentScroll.setLayoutParams(scrollParams);
        binding.contentScroll.setMaxHeight((int) (portraitScreenHeight * 0.58f));
        measure(portraitPanel, portraitWidth, portraitScreenHeight - statusBarPx());
        assertTrue("旧竖屏参数下按钮区应当被挤出页面（用户看到的压扁）",
                binding.footer.getBottom() > binding.root.getMeasuredHeight());

        // 旧横屏：窗口 0.76×屏宽、0.98×屏高并居中 → 页面四周留白（用户看到的“没有全屏”）。
        // 旧实现缩的是窗口本身，这里用同一套面板链按旧窗口尺寸实测，把缺陷钉在布局测量上，
        // 而不是用「(int)(W*0.76f) 等于 W*0.76f 取整」这类恒真算式冒充变异检验。
        int screenWidth = dp(686);
        int screenHeight = dp(386);
        ViewGroup landscapePanel = panelChain();
        tallList();
        measure(landscapePanel, (int) (screenWidth * 0.76f), (int) (screenHeight * 0.98f) - statusBarPx());
        assertTrue("旧横屏窗口宽度只有 0.76×屏宽，页面左右留白（用户看到的“没有全屏”）",
                binding.root.getMeasuredWidth() < screenWidth);
        assertTrue("旧横屏窗口高度只有 0.98×屏高，页面上下留白",
                binding.root.getMeasuredHeight() < screenHeight - statusBarPx());

        // 当前实现接在同一条面板链上必须让窗口铺满：一旦回归“按屏宽/屏高比例手算”这两条必然转红。
        CustomCspDialog.applyPageSizing(hostWindow(), binding.root, binding.contentScroll);
        WindowManager.LayoutParams params = hostWindow().getAttributes();
        assertEquals("当前实现在横屏也不得按屏宽比例手算窗口宽度",
                ViewGroup.LayoutParams.MATCH_PARENT, params.width);
        assertEquals("当前实现在横屏也不得按屏高比例手算窗口高度",
                ViewGroup.LayoutParams.MATCH_PARENT, params.height);
    }

    private void assertFullPage(int[] screen, String orientation) {
        int width = dp(screen[0]);
        int available = dp(screen[1]) - statusBarPx();
        ViewGroup panel = panelChain();
        tallList();
        CustomCspDialog.applyPageSizing(hostWindow(), binding.root, binding.contentScroll);
        measure(panel, width, available);
        String label = orientation + describe(screen);
        assertEquals(label + " root 必须铺满整页", available, binding.root.getMeasuredHeight());
        assertEquals(label + " 页面宽度必须铺满", width, binding.root.getMeasuredWidth());
        assertEquals(label + " 取消按钮被压扁", dp(40), binding.negative.getMeasuredHeight());
        assertEquals(label + " 确定按钮被压扁", dp(40), binding.positive.getMeasuredHeight());
        assertEquals(label + " 按钮区被压扁", dp(40), binding.footer.getMeasuredHeight());
        assertEquals(label + " 按钮区必须贴底", available - binding.root.getPaddingBottom(), binding.footer.getBottom());
        assertTrue(label + " 滚动区必须吃到剩余空间", binding.contentScroll.getMeasuredHeight() > 0);
        assertTrue(label + " 滚动区不能压在按钮区上", binding.contentScroll.getBottom() <= binding.footer.getTop());
    }

    private void assertWindowFills(int[] screen, String orientation) {
        panelChain();
        CustomCspDialog.applyPageSizing(hostWindow(), binding.root, binding.contentScroll);
        WindowManager.LayoutParams params = hostWindow().getAttributes();
        String label = orientation + describe(screen);
        assertEquals(label + " 窗口宽度必须 MATCH_PARENT", ViewGroup.LayoutParams.MATCH_PARENT, params.width);
        assertEquals(label + " 窗口高度必须 MATCH_PARENT", ViewGroup.LayoutParams.MATCH_PARENT, params.height);
        assertEquals(label + " root 高度必须 MATCH_PARENT", ViewGroup.LayoutParams.MATCH_PARENT, binding.root.getLayoutParams().height);
        assertEquals(label + " 滚动区高度交给权重", 0, ((LinearLayoutCompat.LayoutParams) binding.contentScroll.getLayoutParams()).height);
        assertEquals(label + " 滚动区吃权重", 1f, ((LinearLayoutCompat.LayoutParams) binding.contentScroll.getLayoutParams()).weight, 0f);
    }

    /**
     * 真实横屏配置下的回归锁。Robolectric 默认显示是竖屏，上面几条用例只能用合成尺寸验证共享的
     * 尺寸策略，因此分辨不出“实现里按朝向挑一套比例手算窗口”——第 1 轮的横屏 0.76×0.98 正是用户
     * 复测反馈的“没有全屏”，而它在竖屏 Robolectric 环境里永远走不到。这里显式加 {@code land}
     * qualifier 让 {@code ResUtil.isLand} 为真，横屏分支一旦再按屏比例手算窗口，本用例必然转红。
     */
    @Test
    @Config(qualifiers = "w686dp-h386dp-land")
    public void landscapeOrientationQualifierAlsoFillsTheWholePage() {
        assertTrue("land qualifier 必须让 ResUtil.isLand 为真，否则本用例退化成竖屏重复",
                ResUtil.isLand(binding.getRoot().getContext()));
        ViewGroup panel = panelChain();
        tallList();
        CustomCspDialog.applyPageSizing(hostWindow(), binding.root, binding.contentScroll);
        WindowManager.LayoutParams params = hostWindow().getAttributes();
        assertEquals("真实横屏下窗口宽度必须 MATCH_PARENT，不得按屏宽比例手算",
                ViewGroup.LayoutParams.MATCH_PARENT, params.width);
        assertEquals("真实横屏下窗口高度必须 MATCH_PARENT，不得按屏高比例手算",
                ViewGroup.LayoutParams.MATCH_PARENT, params.height);
        assertEquals("真实横屏下 root 高度必须 MATCH_PARENT",
                ViewGroup.LayoutParams.MATCH_PARENT, binding.root.getLayoutParams().height);
        assertEquals("真实横屏下滚动区高度交给权重",
                0, ((LinearLayoutCompat.LayoutParams) binding.contentScroll.getLayoutParams()).height);
        assertEquals("真实横屏下滚动区吃权重",
                1f, ((LinearLayoutCompat.LayoutParams) binding.contentScroll.getLayoutParams()).weight, 0f);
        int width = binding.getRoot().getResources().getDisplayMetrics().widthPixels;
        int height = binding.getRoot().getResources().getDisplayMetrics().heightPixels;
        measure(panel, width, height);
        assertEquals("真实横屏下 root 必须铺满整页", height, binding.root.getMeasuredHeight());
        assertEquals("真实横屏下页面宽度必须铺满", width, binding.root.getMeasuredWidth());
        assertEquals("真实横屏下按钮区必须保持 40dp", dp(40), binding.footer.getMeasuredHeight());
        assertEquals("真实横屏下按钮区必须贴底", height - binding.root.getPaddingBottom(), binding.footer.getBottom());
    }

    /**
     * 窗口被键盘压到比页面最小高度还短时的诚实契约：按钮区仍然保持 40dp（不会被压缩），
     * 滚动区也不会拿到负高度；只是整个页面被裁在窗口下方。这是尺寸策略无法解决的物理限制，
     * 不能因为“看不见”就把它伪装成通过。
     */
    @Test
    public void footerNeverShrinksEvenWhenWindowIsShorterThanTheMinimumPage() {
        ViewGroup panel = panelChain();
        tallList();
        int width = dp(686);
        int tooShort = dp(120);
        CustomCspDialog.applyPageSizing(hostWindow(), binding.root, binding.contentScroll);
        measure(panel, width, tooShort);
        assertEquals("窗口过短时按钮区仍必须保持 40dp", dp(40), binding.footer.getMeasuredHeight());
        assertEquals("窗口过短时取消按钮仍必须保持 40dp", dp(40), binding.negative.getMeasuredHeight());
        assertTrue("窗口过短时滚动区不能拿到负高度", binding.contentScroll.getMeasuredHeight() >= 0);
    }

    /** 模拟"条目很多"：列表内容远高于任何窗口可用高度，行为由尺寸策略决定而不是内容撑高。 */
    private void tallList() {
        binding.recycler.setMinimumHeight(dp(2000));
    }

    /** 复刻 MaterialAlertDialog 装载自定义视图的面板链：全部 wrap_content 高度。 */
    private ViewGroup panelChain() {
        FrameLayout decorContent = new FrameLayout(binding.getRoot().getContext());
        LinearLayoutCompat parentPanel = new LinearLayoutCompat(binding.getRoot().getContext());
        parentPanel.setOrientation(LinearLayoutCompat.VERTICAL);
        FrameLayout customPanel = new FrameLayout(binding.getRoot().getContext());
        FrameLayout custom = new FrameLayout(binding.getRoot().getContext());
        // AlertController: custom.addView(view, new LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        detachRoot();
        custom.addView(binding.getRoot(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        customPanel.addView(custom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parentPanel.addView(customPanel, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        decorContent.addView(parentPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return decorContent;
    }

    /** 每条用例都会把 root 挂进新链，先摘掉旧父容器避免重复 addView 抛异常。 */
    private void detachRoot() {
        ViewGroup oldParent = (ViewGroup) binding.getRoot().getParent();
        if (oldParent != null) oldParent.removeView(binding.getRoot());
    }

    private Window hostWindow() {
        return new Dialog(binding.getRoot().getContext()).getWindow();
    }

    private void measure(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private int statusBarPx() {
        return dp(24);
    }

    private String describe(int[] screen) {
        return screen[0] + "x" + screen[1] + "dp:";
    }

    private int dp(int value) {
        return Math.round(value * binding.getRoot().getResources().getDisplayMetrics().density);
    }
}
