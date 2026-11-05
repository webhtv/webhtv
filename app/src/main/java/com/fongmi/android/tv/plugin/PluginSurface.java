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
