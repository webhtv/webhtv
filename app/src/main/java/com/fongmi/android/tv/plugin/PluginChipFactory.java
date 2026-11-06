package com.fongmi.android.tv.plugin;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.theme.ThemeController;

/**
 * 插件入口 chip 的构造与配色。
 *
 * <p><b>为什么单独一层。</b>三个挂载点（控制条 chip、悬浮球、电视端可聚焦浮标）造出来的东西
 * 几乎一样，差别只在主题包装与背景来源；把差异收进一处，"挂到哪、怎么定位"那条逻辑就不用
 * 反复解释一遍配色为什么这样写。
 *
 * <p><b>为什么点击监听放在这里而不是各挂载点。</b>默认语义三条路径完全一致（点一下 → 派发），
 * 唯独手机端悬浮球要被拖动状态机覆盖掉"刚拖完的那一次抬手"。把默认值留在这里、由唯一需要的
 * 那条路径重设，才看得出"哪里不一样"；反过来在三条路径各装一次，默认值就成了三份重复。
 */
final class PluginChipFactory {

    private PluginChipFactory() {
    }

    /**
     * 创建控件。
     *
     * <p>toolbar 与 floating 的背景来源刻意不同：toolbar 用静态资源
     * {@code R.drawable.selector_chip} 以继承既有焦点环（两个 flavor 都有）；floating 走代码
     * {@link #ringBackground}，因为它的描边色来自运行时主题，静态资源拿不到——
     * 这与 {@code docs/THEME-CUSTOMIZATION-LAYERS} 的结论一致。
     */
    static TextView create(PluginUiPresenter owner, Context context, PluginHost.PluginSlot entry, boolean toolbar) {
        return create(owner, context, entry, toolbar ? Surface.TOOLBAR : Surface.FLOATING);
    }

    /** chip 的三种落点。外观差异只在背景来源与内边距，焦点语义共用一份。 */
    enum Surface {
        /** 播放页控制条：沿用 {@code @style/Control}，与控制条其余按钮一致。 */
        TOOLBAR,
        /** 手机悬浮球 / 电视可聚焦浮标：代码描边环。 */
        FLOATING,
        /** 详情页「插件」行：与线路、清晰度那批芯片同一套「应用表面」规范。 */
        ROW
    }

    /**
     * 创建控件。
     *
     * <p><b>为什么 ROW 不能复用 TOOLBAR。</b>{@code @style/Control} 是给播放页控制条写的：
     * 它的 {@code nextFocusDown=@id/timeBar} 只在控制条里成立，背景 {@code selector_chip} 也依赖
     * 控制条那块深色玻璃。放进详情页后 chip 只剩一行白字、没有按钮外观，和紧邻的线路按钮
     * 明显不同族。详情页该用 {@code selector_video_item}——线路/清晰度/选集那一批就用它。
     */
    static TextView create(PluginUiPresenter owner, Context context, PluginHost.PluginSlot entry, Surface surface) {
        String label = entry.slot().label();
        if (label == null || label.isEmpty()) label = "插件";
        // toolbar 用四参构造把 @style/Control 当作 defStyleRes 套上去：三种 flavor 各自持有该
        // 样式（main:117 基线、leanback:80 带 selector_chip + focusable + nextFocusDown=@id/timeBar、
        // mobile:32 用 ?attr/selectableItemBackgroundBorderless），因此一次构造就取到正确的那份。
        //
        // 早先这里写的是 new TextView(new ContextThemeWrapper(context, R.style.Control))，那是错的：
        // ContextThemeWrapper 只换 theme，不会把 style 里的 android:* 视图属性套到程序化创建的 View
        // 上——android:focusable / background / nextFocusDown 一个都不生效，电视端 chip 于是既没有
        // 焦点环也没接上跳转链。仓库里唯一被验证过的正确写法是四参构造（见 TmdbDetailActivity 同款）。
        TextView chip = surface == Surface.TOOLBAR
                ? new TextView(context, null, 0, R.style.Control)
                : new TextView(context);
        chip.setText(label);
        chip.setTextSize(surface == Surface.ROW ? 14f : 12f);
        chip.setTextColor(0xFFFFFFFF);
        chip.setGravity(Gravity.CENTER);
        chip.setTag(PluginUiPresenter.tagOf(entry));
        chip.setClickable(true);
        if (surface == Surface.TOOLBAR) {
            // Control 已声明 focusable/textColor/background，此处只补间距。
            chip.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        } else if (surface == Surface.ROW) {
            // 与线路芯片同族：应用表面底色 + 焦点环 + 可聚焦，但背景代码化以跨 flavor 可用。
            chip.setBackground(rowBackground(context, false));
            chip.setFocusable(true);
            chip.setFocusableInTouchMode(true);
            chip.setPadding(dp(context, 20), dp(context, 10), dp(context, 20), dp(context, 10));
            // 详情行是电视端的主要遥控器落点：背景静态时"选中"完全看不出来，必须跟着焦点换一份。
            chip.setOnFocusChangeListener((view, focused) -> view.setBackground(rowBackground(context, focused)));
        } else {
            chip.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
            chip.setBackground(ringBackground(context));
        }
        chip.setOnClickListener(view -> owner.dispatch(entry, view));
        return chip;
    }

    /** 半透明底 + 主题焦点环描边，自动跟随深浅色主题。 */
    static GradientDrawable ringBackground(Context context) {
        return ringBackground(context, false);
    }

    /**
     * 同上，但可指定焦点态。
     *
     * <p>未聚焦时与旧实现逐像素相同（{@code 0xCC000000} 底 + 1dp 描边），保证手机端观感不变；
     * 聚焦时加深底色并加粗到 2dp，让电视端"选中了"这件事一眼可见。
     */
    static GradientDrawable ringBackground(Context context, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(focused ? 0xF0000000 : 0xCC000000);
        drawable.setCornerRadius(dp(context, 20));
        drawable.setStroke(dp(context, focused ? 2 : 1), ThemeController.focusRingColor(context));
        return drawable;
    }

    /**
     * 详情页「插件」行的 chip 背景：方形小圆角 + 常态描边 + 焦点环。
     *
     * <p><b>为什么用代码画而不是引 {@code selector_video_item}。</b>那个 drawable 只存在于
     * leanback source set，而本类在 {@code main}——插件行同样会出现在手机端
     * {@code activity_tmdb_detail.xml}，引用它会让 mobile 变体直接编译不过（实际发生过）。
     * 代码绘制跨 flavor 可用，描边色仍取主题的 {@link ThemeController#focusRingColor}，
     * 因此深浅色都能跟随。
     *
     * <p>未聚焦时与旧实现逐像素相同（{@code 0xB3101820} 底 + 1dp 描边），详情页常态观感不变；
     * 聚焦时提亮底色并加粗到 2dp，让电视端"选中了"这件事一眼可见。
     */
    static GradientDrawable rowBackground(Context context, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(focused ? 0xE0182430 : 0xB3101820);
        drawable.setCornerRadius(dp(context, 8));
        drawable.setStroke(dp(context, focused ? 2 : 1), ThemeController.focusRingColor(context));
        return drawable;
    }

    /** dp → px。放在本类是因为调用方只有 chip 的尺寸、内边距与焦点环描边。 */
    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
