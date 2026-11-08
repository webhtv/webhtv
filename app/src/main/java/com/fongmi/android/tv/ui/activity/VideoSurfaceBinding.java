package com.fongmi.android.tv.ui.activity;

import androidx.annotation.Nullable;

/**
 * 把「当前这个引擎该不该挂到 PlayerView 上」从 {@code PlaybackActivity#attachSurface(boolean)} 里
 * 拆出来，变成一个可单测的纯判定。
 *
 * <p>背景（设备日志 {@code webhtv-debug-log (27).txt}，2026-10-21 21:50）：自动连播时服务侧
 * {@code PlaybackService#startBrowse} 用「浏览下一集」的 {@code VE:...} 键起播，Activity 侧
 * {@code isOwner()} 随之为假，重建回调 {@code onPlayerRebuild()} 整段被跳过；于是
 * {@code attachSurface()} 既没走 {@code render != targetRender} 的重绑分支，也没走「引擎实例已不同」
 * 的强制重绑分支，新引擎从未拿到 Surface —— 解码器可初始化、能到 READY、音频正常推进，但视频帧
 * 零提交（全黑有声）。手工重开 App 会重建整页与整条绑定链，所以「重开就正常」。
 *
 * <p>结论：重绑条件不能依赖「Activity 是否自认归属」，只能依赖「PlayerView 上挂着的引擎实例是否
 * 就是当前引擎」。本类只用实例身份回答这一个问题，不关心引擎类型，因此可被单元测试直接证伪。
 */
final class VideoSurfaceBinding {

    private VideoSurfaceBinding() {
    }

    /**
     * 一次绑定判定的结果。
     *
     * @param applied       是否需要在视图上执行绑定/解绑
     * @param detachedFirst 是否需要先把旧引擎解绑再绑新引擎（旧引擎非空时为真，保证顺序确定）
     * @param player        判定后视图应当挂着的引擎实例
     */
    record Result<T>(boolean applied, boolean detachedFirst, @Nullable T player) {

        static <T> Result<T> skipped(@Nullable T player) {
            return new Result<>(false, false, player);
        }

        static <T> Result<T> rebound(@Nullable T player, boolean detachedFirst) {
            return new Result<>(true, detachedFirst, player);
        }
    }

    /**
     * 判定是否把 {@code next} 绑到视图上。
     *
     * @param current 视图当前挂着的引擎实例（可能已被 {@code release()}，但仍非 null）
     * @param next    当前会话应当挂着的引擎实例
     */
    static <T> Result<T> resolve(@Nullable T current, @Nullable T next) {
        // 实例身份比较：口径是「视图上挂着的引擎是不是当前引擎」。这里刻意不用 equals，
        // 免得将来某个 Player 实现自定义相等语义，把两个不同实例误判为同一个而漏绑。
        if (current == next) return Result.skipped(next);
        return Result.rebound(next, current != null);
    }
}
