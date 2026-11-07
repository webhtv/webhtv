package com.fongmi.android.tv.plugin;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.ui.activity.PlaybackActivity;

import java.util.Set;

/**
 * 页面归属判定：把宿主 Activity 归到 {@code home} / {@code detail} / {@code playback} /
 * {@code other} 之一，供槽位声明决定"这个入口该在哪些页面上出现"。
 *
 * <p><b>为什么需要这一层。</b>豆瓣短评这类入口只有拿到剧集上下文才有意义，它声明在
 * {@code detail} 与 {@code playback} 上；首页若照挂，用户点开只会看到"宿主没有传片名"——
 * 那不是可选功能，而是一个明确的坏体验。页面归属交给宿主判定，插件不必也不该猜自己在哪一页。
 *
 * <p><b>为什么判定顺序必须是 Detail → Playback → Home → Other。</b>
 * {@code TmdbDetailActivity} 的父类就是 {@link PlaybackActivity}（详情页继承播放基类以获得起播
 * 能力），所以先判 {@code instanceof PlaybackActivity} 会把 {@code detail} 吞成
 * {@code playback}，反过来让只在播放页生效的入口跑到详情页上去。这里的继承是"详情复用播放
 * 基类"而非"详情是一种播放态"，因此只能靠类名先把两者切开——顺序写错，本类就是错的。
 *
 * <p><b>为什么只用 Activity 的类信息、不碰 Context。</b>本类跑在渲染路径上，任何一次资源或
 * WindowManager 查询都可能抛；类名与 instanceof 是零成本且不会失败的判定。
 */
public final class PluginSurface {

    /** 首页。 */
    public static final String HOME = "home";
    /** 条目详情页。 */
    public static final String DETAIL = "detail";
    /** 播放页。 */
    public static final String PLAYBACK = "playback";
    /**
     * 全屏播放。**排他范围**：处于这个形态时，当前范围集合里只有它，不再包含
     * {@link #PLAYBACK} 或 {@link #DETAIL}。
     *
     * <p>排他是这个值能成立的前提。若全屏时同时算作 playback，那么所有声明了
     * {@code playback} 的插件都会照旧显示（豆瓣 chip 挡画面正是这个现象）；有了排他，
     * "全屏默认干净、要显示必须显式声明 {@code fullscreen}"才成为默认行为。
     */
    public static final String FULLSCREEN = "fullscreen";
    /** 其余页面（设置、插件管理、缓存管理等）。 */
    public static final String OTHER = "other";

    private PluginSurface() {
    }

    /**
     * 判定当前 Activity 属于哪个页面。
     *
     * <p>无法判定时返回 {@link #OTHER} 而不是抛：渲染是可选增强，判不出页面只该导致"这个入口
     * 不显示"，不该让宿主页面崩掉。
     */
    @NonNull
    public static String of(@Nullable Activity activity) {
        if (activity == null) return OTHER;
        try {
            String name = activity.getClass().getSimpleName();
            // 必须先判名字：TmdbDetailActivity 也是 PlaybackActivity 的子类。
            if (name.contains("Detail")) return DETAIL;
            if (activity instanceof PlaybackActivity) return PLAYBACK;
            if (name.startsWith("Home")) return HOME;
            return OTHER;
        } catch (Throwable ignored) {
            return OTHER;
        }
    }

    /**
     * 当前页面是否处于「沉浸观看」态——此时插件入口应当整批隐藏。
     *
     * <p><b>为什么不复用 {@code Util.isFullscreen}。</b>那个方法在电视端恒为 {@code true}
     * （{@code isLeanback() || ...}），拿它当隐藏条件会让 TV 上的插件入口永久消失。这里只
     * 关心手机端的全屏播放：手机端有两种全屏实现，分别由两个页面自己声明。
     *
     * <p>判定一律走 {@link PluginHostedActivity} 这类页面自报，而不是宿主去查
     * WindowInsets：查 insets 在渲染路径上可能抛，而且"系统栏不可见"与"用户在全屏看片"
     * 并不等价（画中画、分屏下系统栏也可能不可见）。
     */
    public static boolean immersive(@Nullable Activity activity) {
        if (activity == null) return false;
        try {
            if (activity instanceof ImmersiveAware aware) return aware.isImmersive();
            return false;
        } catch (Throwable ignored) {
            // 判不出来按"不隐藏"处理：宁可多一个入口，也不要因为一次异常让插件静默消失。
            return false;
        }
    }

    /**
     * 页面自报沉浸态的能力。
     *
     * <p>由宿主页面（{@code TmdbDetailActivity} 等）实现，而不是由插件层反射页面字段：
     * 全屏与否只有页面自己知道得最准，插件层去猜一定会在某个形态上猜错。
     */
    public interface ImmersiveAware {

        /** 当前是否处于沉浸观看态（全屏播放）。 */
        boolean isImmersive();
    }

    /**
     * 纯函数的范围计算，供单测直接覆盖。
     *
     * <p>抽出来是因为真机之外拿不到 {@link ImmersiveAware}：判定逻辑若只长在读取 Activity 的
     * 那一层里，"全屏到底算不算排他"这条最关键的语义就永远测不到。
     *
     * <p>为什么是集合而不是单值：全屏是排他的，但把"页面"与"形态"表达成同一维度后，
     * 未来再加"分屏""画中画"之类的形态时，只需往集合里加元素，消费侧一行都不用改。
     *
     * <p><b>电视端为什么把 playback 同时算成 detail。</b>电视端没有独立的详情页：详情与播放
     * 同在一个 {@code VideoActivity} 里（插件行就挂在 {@code activity_video.xml} 上），类名不含
     * Detail，{@link #of(Activity)} 只能把它判成 {@code playback}。若这里仍按单一页面算，清单里
     * {@code surfaces:["detail"]} 的插件在电视上就永远没有入口——手机端出现、电视端消失，而电视端
     * 的详情插件行恰好收不到它们。这一页同时算 detail 与 playback，才能与手机端两页的语义对齐。
     */
    @NonNull
    static Set<String> scopesOf(@NonNull String page, boolean immersive, boolean leanback) {
        if (immersive) return Set.of(FULLSCREEN);
        if (leanback && PLAYBACK.equals(page)) return Set.of(DETAIL, PLAYBACK);
        return Set.of(page);
    }

    /** 两参重载：等价于非电视端（单页面语义），既有测试与调用方按它表达"这一页是什么"。 */
    @NonNull
    static Set<String> scopesOf(@NonNull String page, boolean immersive) {
        return scopesOf(page, immersive, false);
    }

    /**
     * 槽位声明的生效范围里，是否有任何一个与当前范围集合相交。
     *
     * <p><b>空集返回 {@code true}</b>：这是与 {@link #matches} 完全一致的向后兼容语义
     * （清单不写 {@code surfaces} = 全页显示），所以老插件在全屏下依然可见。想让入口在全屏
     * 让位，就必须显式声明 {@code surfaces}，此时全屏集合里没有它声明的值，自然不显示。
     */
    public static boolean matchesAny(@Nullable Set<String> declared, @Nullable Set<String> active) {
        try {
            if (declared == null || declared.isEmpty()) return true;
            if (active == null || active.isEmpty()) return false;
            for (String scope : declared) {
                if (scope != null && active.contains(scope)) return true;
            }
            return false;
        } catch (Throwable ignored) {
            // 与 matches 同一取舍：判定失败按"显示"处理，宁可多一个入口也不要让插件静默消失。
            return true;
        }
    }

    /**
     * 槽位声明的生效页面里是否包含当前页面。
     *
     * <p><b>空集返回 {@code true} 是刻意的向后兼容命门。</b>历史清单没有 {@code surfaces}
     * 字段，若把"未声明"当成"哪都不显示"，升级一次宿主就会让所有既有插件集体消失。缺省＝全页
     * 显示，作者想收窄范围时才声明。
     *
     * <p>参数实际由 {@link PluginManifest.UiSlot#surfaces()} 提供且非空；这里仍接受 {@code null}
     * 并按空集处理，因为判定的代价只在渲染路径上一次，而误判的代价是入口凭空消失。
     */
    public static boolean matches(@Nullable Set<String> surfaces, @Nullable String current) {
        try {
            if (surfaces == null || surfaces.isEmpty()) return true;
            if (current == null || current.isEmpty()) return false;
            for (String surface : surfaces) {
                if (surface == null) continue;
                if (surface.equalsIgnoreCase(current)) return true;
            }
            return false;
        } catch (Throwable ignored) {
            // 判定失败按"显示"处理：宁可多一个入口，也不要因为一次异常让插件静默消失。
            return true;
        }
    }
}
