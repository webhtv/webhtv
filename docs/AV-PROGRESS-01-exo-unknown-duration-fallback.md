# AV-PROGRESS-01 · EXO 未上报时长导致进度条 00:00 的兜底修复

## 问题

用户在手机端与电视端都看到播放页进度条总时长恒为 `00:00`、进度条无长度（视频正常播放，重开偶尔恢复）。
两端的进度条控件是同一个 `app/src/main/java/com/fongmi/android/tv/ui/custom/CustomSeekView.java`，改动前它只有一处取值：

- `updateTimeline()`：`long duration = progress.getDuration(); if (duration < 0) duration = 0;`
- `updateProgress()`（1 秒轮询）：同样的取值与钳制
- `effectiveBufferedPosition()`：用同一时长约束缓冲条

EXO 内核对部分 HLS/TS 片源返回 `C.TIME_UNSET`（负数）。负数被钳成 0，于是总时长文本与 timeBar 长度都是 0，
进度条自然没有数据。这是「引擎未上报」而非片源或网络问题：位置数字会正常走动。

同类先例 `docs/C20260906161136-mpv-duration-recovery-fix.md` 修的是 MPV 首次播放不发布 duration 事件，
修在 `MpvPlayer.refreshPlaybackState`，未覆盖 EXO 路径。本次只修已知的 EXO 场景，不做四内核普查。

## 方案（最小改动，引擎时长始终优先）

1. `CustomSeekView` 复用 `java.util.function.LongSupplier`（不新增自定义接口）与
   `setDurationFallback(@Nullable LongSupplier)`，把 `updateTimeline()`、`updateProgress()`、
   `effectiveBufferedPosition()` 三处（`CustomSeekView.java:166/:181/:213`）直接读
   `progress.getDuration()` 统一收敛到新增的 `private long resolveDuration(Player progress)`：
   - 引擎返回 `> 0` → 直接用引擎值（引擎一旦拿到真实时长就立刻覆盖兜底，不需要额外失效逻辑）；
   - 否则调用兜底回调，回退值 `<= 0` 时仍返回 0，行为与改动前完全一致；
   - 使用兜底时按 5 秒限流打一条 `playback-progress action=duration-fallback` 日志。
   - 拖动预览与提交（`previewSeekPosition` / `commitSeekPreview`，`CustomSeekView.java:291/:302`）
     走 `currentDuration` 优先、有意不复用兜底，因为预览期间需要与 timeBar 已显示的时长保持一致。
2. `PlaybackActivity` 新增 `protected long getDurationFallbackMs() { return C.TIME_UNSET; }` 钩子，
   在 `handleControllerConnected()` 中于 `setSeekListener(...)` 之后、`onControllerReady` 之前调用
   `getSeekView().setDurationFallback(this::getDurationFallbackMs)`。
3. 两端 `VideoActivity` 覆写该钩子为当前集历史的时长：`mHistory == null ? C.TIME_UNSET : mHistory.getDuration()`。
   历史时长由已有的 `updatePlaybackHistoryPosition()` 成功播放后写入，换集时由
   `updateHistoryAfterShortDramaTransition()` 用 `EpisodePositionCache` 刷新或置回 `C.TIME_UNSET`，
   因此不会把上一集的时长带到新集。
4. leanback 的中央浮层时间文本（`showInfo()`、`onSeeking(long)` 两处 `mBinding.widget.duration`）
   改用新增的 `getTimelineDurationTime()`，与进度条同源。

其余 `PlaybackActivity` 子类（Live/Cast/TmdbDetail/Audio）不覆写钩子，回退值为 `C.TIME_UNSET`，
行为与改动前一致。

## 未采取的做法

- 改引擎层（`ExoPlayerEngine`）补发 duration：EXO 的 `getDuration()` 直接来自 media3，未知时无本地可信来源，
  在引擎层伪造时长会影响 `isLive()` / `isVod()`（`ExoPlayerEngine.java:715/:720`）与短片判定，风险高于收益。
- 通过 `MediaItem.mediaMetadata` 传时长：media3 是依赖 jar（`gradle/libs.versions.toml:50`），仓内无源码，
  且 metadata 不参与 `Player#getDuration`。

## 验证

- `gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过。
- `gradlew :app:testMobileArm64_v8aDebugUnitTest --tests '*VideoActivityLayoutTest*'` 与
  `gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests '*PlayerControlFocusHelperSeekReturnTest*'` 通过
  （前者按字面断言 `handleControllerConnected` 内的调用顺序与 `CustomSeekView` 的 seek 链路，本次未破坏）。

## 回滚

单独回滚本次提交即可；无数据迁移、无格式变更、无配置项。

## 后续（未纳入本次范围，仅记录）

- `docs/AUTONEXT-01-episode-transition-black-screen.md:164` 记载换集重建窗口内 `onPlayerRebuild()` 会被跳过，
  其中包含 `getSeekView().setProgressPlayer(...)`；本次的兜底依赖控件侧读取，不受该跳过影响。
- MPV/IJK/System 内核是否有其他未上报时长的路径未做普查；本次修复对它们保持「引擎优先、无兜底即旧的 0」语义。
- 截图中的 toast「弹幕加载失败」在仓库源码中零命中，与本次问题不同源，未处理。
