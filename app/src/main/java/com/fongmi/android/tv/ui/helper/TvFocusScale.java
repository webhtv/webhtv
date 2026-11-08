package com.fongmi.android.tv.ui.helper;

import android.view.View;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.theme.ThemeController;

/**
 * 电视端「聚焦时放大」的唯一实现、唯一时长来源，以及两个有据可依的强调档位。
 *
 * <p>改动前，同一个焦点缩放在 TV 端有四种写法：最近观看/收藏/历史用 {@code 1.1f} +
 * {@code 150ms}，TMDB 相关视频与详情页视频卡用 {@code 1.04f} + {@code 120ms}，
 * 网络诊断面板用 {@code 1.035f} + {@code 100ms}。数值与时长都没有记录说明为什么不同，
 * 用户学到的是「聚焦有时放大、有时不放大、幅度还不一样」。
 *
 * <p><b>为什么保留两个档位而不是一个。</b>收敛过程中查到一个反证：
 * {@code TmdbDetailActivityLayoutTest} 记录 {@code TmdbEpisodeAdapter} 刻意**完全不缩放**，
 * 理由是「detail rows clip enlarged cards」——详情页那些密集行会把放大的卡片裁掉。
 * 所以密集宿主上的小倍率不是随意漂移，而是为了避免裁切。把 {@code 1.04} 一律提到
 * {@code 1.1} 会引入真实的裁切回归。因此本类只做真正该做的收敛：
 *
 * <ul>
 *   <li><b>一套实现</b>：缩放/复位只有这里一份，站点不再各自写 animate 链；</li>
 *   <li><b>一个时长</b>：全部取 {@code @integer/webhtv_focus_scale_duration}；</li>
 *   <li><b>两个档位</b>：常规宿主取主题 token 的倍率，密集宿主取本类显式声明的紧凑倍率。</li>
 * </ul>
 *
 * <p>明确不在收敛范围内的（它们不是焦点缩放，别顺手改）：{@code AudioMiniPlayer} 的
 * {@code 1.03f/90ms} 是拖拽反馈、{@code 0.94f→1f/180ms} 是出现动画；
 * {@code PlayerGesture} 的缩放是画面手势；{@code TmdbCardFocusHelper} /
 * {@code TmdbEpisodeAdapter} 的 {@code setScaleX(1f)} 是刻意不缩放——那批卡片用
 * 焦点环 + 抬升表达焦点，是另一套已统一的机制。
 */
public final class TvFocusScale {

    /**
     * 密集宿主的紧凑倍率。
     *
     * <p>用于 TMDB 相关视频/剧集这类行内卡片：放大后会被所在行裁切（见类注释的反证），
     * 因此不能取主题的常规倍率。这个值是**唯一**允许写死缩放字面量的位置，
     * 由 {@code TvFocusScaleContractTest} 钉住；若将来它也需要按主题调整，
     * 应移入 {@code ThemeTokens} 而不是再散落到站点里。
     */
    private static final float COMPACT_SCALE = 1.04f;

    private TvFocusScale() {
    }

    /** 常规卡片/列表项：聚焦放大到主题 token 的倍率，失焦回到 1.0。 */
    public static void apply(View view, boolean focused) {
        animate(view, focused ? ThemeController.current().focusScale() : 1f);
    }

    /** 密集宿主：聚焦放大到紧凑倍率，失焦回到 1.0。 */
    public static void applyCompact(View view, boolean focused) {
        animate(view, focused ? COMPACT_SCALE : 1f);
    }

    /** 不带动画地把视图复位，供 {@code onUnbindViewHolder} 之类的复用清理路径使用。 */
    public static void reset(View view) {
        view.animate().cancel();
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    private static void animate(View view, float scale) {
        view.animate().cancel();
        view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(view.getResources().getInteger(R.integer.webhtv_focus_scale_duration))
                .start();
    }
}
