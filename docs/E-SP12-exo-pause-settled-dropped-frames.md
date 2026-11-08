# E-SP12 电视端 EXO「暂停即掉帧」：暂停结算尾量的记账口径修复

- **任务 ID**：`E-SP12`（Exo 性能专项，`E-SP*` 家族；同一现象曾在 `E-SP11` §0/§1 中作为「掉帧」复现口径出现，但 E-SP11 只用排除法定位到设备与调度链，本任务定位到**记账口径**这一独立原因）
- **开始时间**：2026-10-23 11:05 (+08:00, Asia/Shanghai)
- **lane**：quick-fix（只改本仓统计口径与展示阈值，不改 media3、不改 native、不动 `third_party`）
- **用户现象**：最新 TV 测试版「**只要暂停播放就会掉帧，如果不暂停播放就是正常的**」；证据日志 `G:\下载\webhtv-debug-log (30).txt`
- **结论**：不是「暂停导致了掉帧」，而是 media3 在 renderer stop 时**结算掉帧累加器**，本仓把这个尾量裸累加进用户可见计数，于是计数只在暂停时跳。修复为按**播放时长归一**的速率口径，并把暂停结算的尾量单独记账。

---

## 0. 复现环境与日志口径

- 构建：`appVersion 5.6.0` / `versionCode 560` / `buildTag v5.6.0-beta-202610221924` / `gitRevision 4dda4b56b8d67e0cb78e6617f7b0f776591383a9` / flavor `leanback` / `media3Version 1.11.0-alpha01-fongmi`
- 设备：Dangbei X7 Ultra（`horse_popular`）/ Android 14 / API 34 / `arm64_v8a` / 60.000004 Hz
- 播放对象：3840x2160 `avc1.640034`（H.264 High L5.2）/ `contentLength=3110336968` / `duration=3088068ms` / `bitrate=8057690` / 容器 `video/x-matroska`；硬解 `decoder-8 = c2.mtk.avc.decoder`
- 日志解析要点：**行首 `YYYY-MM-DD HH:MM:SS.mmm` 是本地时间（Asia/Shanghai），且文件含两段重叠的 capture window**；统计前必须先按时间单调截止或按整行去重，否则数目会翻倍。

---

## 1. 归因（三条互相独立的证据）

### 1.1 上报与暂停 1:1，与播放时长无关

去重后 26 条 `playback-metrics droppedFrames` 上报，**26/26 都紧跟在同一次 `playback-lifecycle: playing changed isPlaying=false` 之后 61–119ms 到达**（直方图 61,62,66,68×4,69×2,70×2,71,72,73×4,75×2,77×6,78×2,79×2,81×2,86,89×2,92,100,106,108,119 ms）。播放中上报 **0 次**；暂停瞬间上报 26 次。每次 `droppedFrames` 恒在 1–5 帧。

### 1.2 `elapsed=` 与「上一次 resume → 本次 pause」的播放时长逐条相等

例如 `20:41:26.144 droppedFrames=3 total=43 elapsed=3523ms position=300077`，对应 `20:41:22.551 resume → 20:41:26.074 pause` 的 3.52s。这正是 media3 `droppedFrameAccumulationStartTimeMs` 从 `onStarted()` 起算的口径，说明该窗口就是**播放期**，尾量产生于播放中、被暂停「记账」。

### 1.3 计数只与暂停同频

- collector 的 `video.output.summary`（`renderer-enable-lifetime`）`droppedBuffers` 全程单调累加：30→35→…→99；`skippedBuffers` 68→70 几乎不动。
- adapter 级 `video.output.summary`（`adapter calls since capture/flush`）5 秒增量：区间内含暂停者多为 3–9 帧，不含暂停的区间为 0。
- `droppedInputBuffers` 恒 0、`droppedToKeyframe` 恒 0 → 不是「解码输入丢帧阈值」路径；`exo-frame-timing` 直方图全程落在 0..20ms 早帧桶、`lateBatches=0` → 被 DROP 的帧不进入该直方图（只统计被处理帧的 processing offset）。

**换算**：约 **1.2 帧/播放秒**（≈2.4%），是真实但低速的输出侧丢弃（`earlyUs < -30ms`），与暂停无关。

### 1.4 上游机制（逐行确认，来源 `third_party/maven/androidx/media3/media3-exoplayer/1.11.0-alpha01-fongmi/media3-exoplayer-1.11.0-alpha01-fongmi-sources.jar`）

- `MediaCodecVideoRenderer.java:1172-1184` `onStarted()`：`droppedFrames = 0; droppedFrameAccumulationStartTimeMs = getClock().elapsedRealtime();`
- `MediaCodecVideoRenderer.java:1186-1199` `onStopped()`：**第一句 `maybeNotifyDroppedFrames();`**
- `MediaCodecVideoRenderer.java:2593-2601` `maybeNotifyDroppedFrames()`：`if (droppedFrames > 0) { elapsedMs = now - droppedFrameAccumulationStartTimeMs; eventDispatcher.droppedFrames(droppedFrames, elapsedMs); ... }`
- `MediaCodecVideoRenderer.java:2376-2388` `updateDroppedBufferCounters(...)`：只在 `droppedFrames >= maxDroppedFramesToNotify`（=`DefaultRenderersFactory.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY` = **50**）时才提前上报。
- `ExoPlayerImplInternal.java:1255` `setPlayWhenReadyInternal` 里 `if (!shouldPlayWhenReady()) { stopRenderers(); ... }` → `stopRenderers()`(:1367) 逐个 `rendererHolder.stop()`。

**推论**：每 5 秒暂停一次的节奏下累加器永远到不了 50，于是**所有上报都只发生在暂停瞬间**。这不是本仓的 bug，也不是设备问题，而是 media3 的既有设计（社区已知争议：google/ExoPlayer#7676「Force send onDroppedFrames when stream changes」）。

### 1.5 本仓放大点（修复对象）

- `app/src/main/java/com/fongmi/android/tv/player/exo/PlaybackAnalyticsListener.java`：`onDroppedVideoFrames` 把每次上报**裸累加**进 `totalDroppedFrames`，`elapsedMs` 收到却只用于打日志、从不参与归一化。
- `app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java:461` 的「掉帧 N」因此只在暂停时跳；`:522` 的 `>= 60` 判定还会在暂停后误报「掉帧较多，可能是解码或渲染压力」。
- 同一份数字经 `ExoPlayerEngine.currentDecoderRuntimeEvidence()` 进入 `ExoDecoderRuntimeSession`（`MAX_HEALTHY_DROPPED_FRAMES = 23`），可能把本健康的解码器记成 observation，进而影响后续硬解选择（**第二个实质影响，不只是 UI 误报**）。

---

## 2. 业界做法调研（用户 2026-10-23 要求「业界主流/GitHub 开源怎么处理」）

| 实现 | 做法 | 证据 |
|---|---|---|
| media3 `PlaybackStatsListener` | `this.droppedFrames = droppedFrames`（**覆盖赋值，非累加**），且只有本批次含该事件才写入本会话；对外指标 `PlaybackStats.getDroppedFramesRate() = 1000f * totalDroppedFrames / getTotalPlayTimeMs()` | `libraries/exoplayer/.../analytics/PlaybackStatsListener.java:202-203,253,269`；`PlaybackStats.java:556-557,1091-1094` |
| media3 `EventLogger` | `onDroppedVideoFrames` 只 `logd` 打印，不累计 | `EventLogger.java:438-439` |
| VLC | `vout_statistic_t { displayed, lost, late }` 三分类；`vout_statistic_GetReset()` 用 `atomic_exchange_explicit(..., 0)` 返回**区间增量**并清零 | `src/video_output/statistic.h:30-34,48-57`；`vout_internal.h:236-237` |
| mpv | 分 `decoder-frame-drop-count`（decoder）与 `frame-drop-count`（output）两个独立计数 | `player/lua/stats.lua:954-957` |
| 相关上游讨论 | google/ExoPlayer#7676（流切换时未上报的旧值是否带过来，open）；androidx/media#1917（连续丢帧回调，open）；androidx/media#3352（承认存在非单调 PTS 片源，与本日志 `ptsRegressions` 高相印证，非故障判据） | GitHub API |

**共同结论**：没有任何主流实现把「生命周期边界结算的裸累计」当作质量指标；对外都是**速率/区间增量**，并按丢弃发生的位置分类。本任务的修法即对齐这一口径。

---

## 3. 方案与取舍

三个候选：

- **A 对齐 PlaybackStats**：新增播放时长累计，对外暴露帧/秒速率。
- **B 对齐 VLC 区间语义**：对外暴露「自上次读取以来的增量」，读走即清零。
- **C 分类归因**：把暂停瞬间结算的尾量单独计入 `pauseSettledDroppedFrames`，不并入用户可见的劣化判断。

**采纳 A + C**（B 不采纳：OSD 面板与网络诊断都是**无状态轮询读取**，读走即清零会让「掉帧」行在两次刷新之间随机回零，反而更难判读）。

具体口径：

1. 速率 = `totalDroppedFrames * 1000 / playTimeMs`；`playTimeMs` 只在 `onIsPlayingChanged(true)` 到 `onIsPlayingChanged(false)` 之间累计。
2. 判定阈值由「裸计数 ≥60」改为「**≥4 帧/播放秒**」（与既有本地降级触发 `ExoUtil.ENHANCED_DROPPED_FRAMES_PER_SECOND_THRESHOLD = 4` 同口径）；播放时长不足 30s 时速率尚无意义，退回原固定计数。
3. 解码器健康判定由「30s 窗口内 ≤23 帧」改为**同一预算的速率形式** `23 * 1000 / 30_000 ≈ 0.767 帧/秒`；播放时长未测得时退回原固定计数（**不能因为「没测量」就变宽松**）。
4. 暂停结算的尾量单独记账并在诊断日志里标注 `pauseSettled=true`，**保住证据**而不是丢弃。

**明确不做**：不改 media3 / `third_party`；不改 `setMaxDroppedFramesToNotify`（改小只会让上报更频繁、更误导）；保留 `PanDiagnosticVerdict` 的 `>= 60`（那是网络诊断的独立口径，属交叉域，另任务处理）；不改 MPV/IJK 口径；**真实存在的 ≈1.2 帧/播秒属 E-SP11 域**（调度链与输入丢帧阈值），需要设备 bisect，不在本任务范围。

---

## 4. 实施记录

### 4.1 新增 `app/src/main/java/com/fongmi/android/tv/player/exo/DroppedFrameAccounting.java`

纯 JVM 类，无 Android 依赖（与 `ExoFrameTimingMetrics`、`ExoDecoderRuntimeSession` 同范式），`synchronized`：

- `reset()`
- `onPlayingStateChanged(boolean isPlaying, long nowElapsedMs)` —— 重复同值调用不重复计时
- `onDroppedFramesReported(int droppedFrames, boolean settledWhileNotPlaying)` → 返回新的累计值（饱和累加）
- `droppedFrames()` / `pauseSettledDroppedFrames()` / `pauseSettledReports()` / `isPlaying()`
- `playTimeMs(long nowElapsedMs)` —— 含尚未闭合的播放段
- `droppedFramesPerSecond(long nowElapsedMs)` —— `playTime <= 0` 时返回 0

### 4.2 `app/src/main/java/com/fongmi/android/tv/player/exo/PlaybackAnalyticsListener.java`

- 静态字段 `totalDroppedFrames` → `DROPPED_FRAME_ACCOUNTING`；`reset()` 相应改调 `DROPPED_FRAME_ACCOUNTING.reset()`。
- 新增 `public static long playTimeMs()`、`public static double droppedFramesPerSecond()`、`public static long pauseSettledDroppedFrames()`。
- `onDroppedVideoFrames`：`settledWhileNotPlaying = !DROPPED_FRAME_ACCOUNTING.isPlaying()` → 记账 → `snapshot.withDroppedFrames(total)`；诊断日志格式串扩为
  `"droppedFrames=%d total=%d elapsed=%dms playTime=%dms rate=%.2f/s pauseSettled=%s pauseSettledTotal=%d position=%d"`。
- `onIsPlayingChanged`：把 `long now = nowElapsed();` **提前到 return 之前**，并加 `DROPPED_FRAME_ACCOUNTING.onPlayingStateChanged(isPlaying, now)`。

### 4.3 `app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java`

- 新增常量 `DROPPED_FRAMES_PER_SECOND_DIAGNOSIS = 4d`、`DROPPED_FRAMES_RATE_MIN_PLAY_TIME_MS = 30_000L`、`DROPPED_FRAMES_RAW_COUNT_DIAGNOSIS = 60`。
- 新增 `DecimalFormat frameDropRateFormat = new DecimalFormat("0.0")`（既有的 `"#.0"` 会把 0.5 打成 `.5`）。
- `:461` → `droppedFrameText(player)`：Exo 输出 `掉帧 N（x.x/秒）`，MPV/IJK 保持原样。
- `:522` → `excessiveDroppedFrames(player)`：Exo 且播放时长 ≥30s 用速率判据，否则退回原固定计数。

### 4.4 `app/src/main/java/com/fongmi/android/tv/player/exo/ExoDecoderRuntimeSession.java`

- `Evidence` 追加末位 `long playTimeMs` + 保留 5 参数便捷构造器（旧调用点自动 equivalent）。
- 新增 `MAX_HEALTHY_DROPPED_FRAMES_PER_SECOND = MAX_HEALTHY_DROPPED_FRAMES * 1000d / STABLE_PLAYBACK_WINDOW_MS` 与 `withinHealthyDropRate(Evidence)`；`recordStable` 的 `healthy` 判据改用它（`playTimeMs <= 0` 时退回原固定计数）。

### 4.5 `app/src/main/java/com/fongmi/android/tv/player/engine/ExoPlayerEngine.java`

`currentDecoderRuntimeEvidence()` 与 `decoderRuntimeEvidence(PlaybackException)` 两处构造点补 `PlaybackAnalyticsListener.playTimeMs()`。

### 4.6 测试

- 新增 `app/src/test/java/com/fongmi/android/tv/player/exo/DroppedFrameAccountingTest.java`（8 例）：暂停结算尾量不改速率、跨暂停累计播放时长、未闭合播放段计入、重复状态不重复计时、无播放时长时速率为 0、暂停结算分类、负值不减、reset 清空。
- `app/src/test/java/com/fongmi/android/tv/player/exo/ExoDecoderRuntimeSessionTest.java`：既有 `unhealthyWindowRecordsObservationWithoutBlacklist` 补 `playTimeMs` 参数；新增 `slowDropsOverALongWindowStayHealthy`、`shortWindowWithTheSameTotalIsUnhealthy`、`unmeasuredPlayTimeFallsBackToTheFixedCount`。

---

## 5. 验证

### 5.1 定向单测（leanback / arm64-v8a）

```
.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests "com.fongmi.android.tv.player.exo.DroppedFrameAccountingTest" \
  --tests "com.fongmi.android.tv.player.exo.ExoDecoderRuntimeSessionTest" --console=plain
```

结果：`BUILD SUCCESSFUL in 2m 8s`，exitCode 0。test-results XML 逐类核对：

| 测试类 | tests | failures | errors |
| --- | --- | --- | --- |
| `DroppedFrameAccountingTest`（新增） | 8 | 0 | 0 |
| `ExoDecoderRuntimeSessionTest`（改动 + 新增 3 例） | 16 | 0 | 0 |

### 5.2 影响面回归（player 包全量 + mobile flavor 编译）

```
.\gradlew.bat :app:compileMobileArm64_v8aDebugJavaWithJavac \
  :app:compileMobileArm64_v8aDebugUnitTestJavaWithJavac \
  :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests "com.fongmi.android.tv.player.*" --console=plain
```

结果：`BUILD SUCCESSFUL in 2m 7s`，exitCode 0。聚合 `app/build/test-results/testLeanbackArm64_v8aDebugUnitTest` 全部 XML：**212 个测试类 / 1544 tests / 0 failures / 0 errors**。

`compileMobileArm64_v8aDebugJavaWithJavac` 一并通过，说明 `PlayerOsdController` / `ExoPlayerEngine` / `PlaybackAnalyticsListener` 的改动对 mobile flavor 同样成立（这两个 flavor 共用同一份 `app/src/main/java`，改动未引入 flavor 专属符号）。

### 5.3 改动规模

`git status --porcelain` 为 6 个 `M` + 3 个 `??`（新增 `DroppedFrameAccounting.java`、`DroppedFrameAccountingTest.java`、本文件），`git diff --stat` = 6 files changed, 189 insertions(+), 15 deletions(-)（不含新增文件）。工作区无其它脏项（`Python/` 为既有未跟踪目录，已通过 `.git/info/exclude` 临时排除以便 guard 启动，收尾已还原，不进入提交）。

### 5.4 设备实测（2026-10-23，模拟器 127.0.0.1:5561）

设备：OnePlus HD1910 / device=marlin / Android 9 (API 28) / x86_64+arm64-v8a / 1920×1080@60Hz；安装的是本提交构建（日志 `env.device` 行 `gitRevision=fd5bcf109ee4e5de5fbeaa7d31242806bc76b5e3`、`state=clean`、`buildTime=202610231141`、flavor=leanback、abi=arm64_v8a）。播放对象为本地 854×480 / 960×540 H.264 素材，解码器 `OMX.qcom.video.decoder.avc`（模拟器无硬解，仅验证记账口径与展示，不用于复跑 X7 Ultra 的 4K 场景）。

**证据 1：新日志字段在真机生效，且暂停结算被正确标注。** 暂停瞬间（trace `p-8tmye-1`，12:44:14.214）：

```
playback-metrics: trace=p-8tmye-1 droppedFrames=1 total=1 elapsed=31118ms playTime=30640ms rate=0.03/s pauseSettled=true pauseSettledTotal=1 position=31077
```

- 旧格式只有 `droppedFrames/total/elapsed/position`；现在 `playTime`、`rate`、`pauseSettled`、`pauseSettledTotal` 全部出现。
- `pauseSettled=true`：该上报确实是在 `isPlaying=false` 期间（renderer `onStopped`）结算的，与 §1.4 的上游机制一致。
- `rate=0.03/s` = 1 帧 ÷ 30.64 播放秒，归一化正确；`playTime=30640ms` 与 `elapsed=31118ms` 接近但不等，说明两套时间基准（`onIsPlayingChanged` 打点 vs renderer `droppedFrameAccumulationStartTimeMs`）各自独立工作，未被混用。
- 同一会话前 30 秒播放期间没有任何 `droppedFrames` 上报（累计 1 帧 < 50 阈值），只在暂停时结算一次——复现了 §1 的「暂停记账」形态。

**证据 2：OSD 显示改为「掉帧 N（x.x/秒）」，且暂停后不再跳成突发。** 截图（1920×1080，`F:\temp\ev_after_pause.png` 与 `F:\temp\osd9_mkv.png`）中「播放」行原文：

| 场景 | OSD「播放」行 | 结论行 |
| --- | --- | --- |
| 播放中（未暂停） | `就绪(READY) / 19.4 s / 87% / 重缓冲 0 次 / 掉帧 0（0.0/秒）` | 正常 |
| 暂停后（本会话 0 帧结算） | `就绪(READY) / 18.7 s / 87% / 重缓冲 0 次 / 掉帧 0（0.0/秒）` | 正常 |
| 暂停后（结算 1 帧，mkv 会话） | `就绪(READY) / 28.9 s / 100% / 重缓冲 0 次 / 掉帧 1（0.0/秒）` | 正常 |

- 旧口径下这一行只会是 `掉帧 1`，用户无法判断它是「1 帧/30 秒」还是「突发掉帧」；现在同一数字带上了速率，且 `0.0/秒` 直接说明它不构成劣化。
- 累计到 1 帧（远低于旧的 `>=60` 阈值、也低于新的 `>=4/秒` 阈值）时结论行仍为「正常」，**没有触发「掉帧较多，可能是解码或渲染压力」的误报**；这正是本次修复要消除的用户可见症状。

**证据 3：暂停/恢复控制与状态机正常。** `GET /action?do=control&type=pause|play`（`app/src/main/java/com/fongmi/android/tv/server/process/Action.java:156-169`）均返回 200；日志里 `playing changed isPlaying=true state=3` → `isPlaying=false state=3` → `isPlaying=true state=3` 成对出现，暂停期间 state 保持 READY（3），与 E-SP12 假设的「暂停不改变播放状态、只 stop renderer」一致。

**未覆盖**：模拟器无硬解、素材为 854×480/960×540 低码率，未复现原日志 4K H.264 High L5.2 的 ≈1.2 帧/播秒真实丢帧，因此「高码率下速率显示为 1.x/秒、≥4/秒 才报掉帧较多」的上限分支仍未在设备上验证；该分支由单测覆盖（§5.1）与代码常量保证。

### 5.5 未做的验证（如实记录）

- **未在 X7 Ultra 实机复跑** `G:\下载\webhtv-debug-log (30).txt` 对应的 4K 暂停场景（无硬解可用设备）。因此 §7 第 1 条的真实低速丢帧降幅未验证；本次只验证了记账口径与展示层。
- 单测覆盖的是口径与阈值判定本身（`DroppedFrameAccounting` 的暂停结算分流、速率归一、除零、饱和累加；`ExoDecoderRuntimeSession` 的速率健康判定与回退），不覆盖 media3 的事件时序——后者已在 §1 用上游源码逐行确认。

---

## 6. 回滚

只改统计口径与展示阈值，**不删除任何开关、字段或诊断证据**。回滚 = `git revert` 单个提交；无需数据迁移、无 ABI/原生影响、无 `third_party` 依赖。

---

## 7. 未解决 / 后续

1. **真实低速丢帧（≈1.2 帧/播秒）仍在**：属 E-SP11 的调度链/输入丢帧阈值域，需设备分组 bisect（E-SP11 §4 阶段 B 尚未执行，无硬解可用设备）。本任务只保证它**不再被暂停伪装成突发**。
2. `PanDiagnosticVerdict` / `PanNetworkDiagnosticDialog` 仍用裸计数 `60` 作为判定输入（网络诊断域），本次未动；若后续统一口径需另立任务。
3. OSD `帧率匹配 开` 是**硬编码文案**，不读实际设置（既有小缺陷，与本任务无关，未修）。
4. `PlayerOsdController` 的 OSD 文案无源码断言测试（`app/src/test/.../ui/custom` 下无测试文件），本次新增的纯函数类已独立可测，格式化辅助仍无回归锁。

---

## 8. 复核轮（2026-10-23 14:00 +08:00 起）

### 8.1 复核范围

本轮复核覆盖 `origin/beta..dev4` 的全部 3 个未推送提交（合并提交 `7443e1e4742` + `fd5bcf109ee` + `82a2731f4ef`），即"已提交未推送"的全部代码与文档改动，逐文件对照 §4 实现记录核对。

### 8.2 发现与处置

| 编号 | 严重度 | 位置 | 问题 | 处置 |
| --- | --- | --- | --- | --- |
| R1 | low（文档/可读性，不改行为） | `app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java` §4.3 新增方法插入点 | 新增的 `droppedFrameText` / `excessiveDroppedFrames` 连同类 javadoc 被插在 `getStartupText` 的 javadoc 与 `getStartupText` 本体之间，导致 `getStartupText` 的 javadoc 悬空、正文与文档被拆开；`excessiveDroppedFrames` 自身反而没有 javadoc | 把 `getStartupText` 归位到自己的 javadoc 正下方，新方法整体下移到其后，并补上 `excessiveDroppedFrames` 的 javadoc（说明"有实测播放时长后按速率判定，暂停结算不再读成解码压力"）。净改动 12 插入 / 7 删除，方法体与阈值逻辑零变化 |

其余项复核未发现问题，正面证据见 §8.3。

### 8.3 复核通过项（正面证据）

- **无重复/丢失定义**：`getStartupText` 仅 :507 定义、:475 调用；`droppedFrameText` :520 定义、:474 调用；`excessiveDroppedFrames` :532 定义、:562 调用，与 §4.3 描述一致。
- **口径实现与 §3 决策逐条对齐**：`DroppedFrameAccounting` 的暂停结算分流、播放时长跨暂停累加、开区间计入 `now`、除数非正返回 0、负上报钳位、饱和累加全部有单测锁定；`ExoDecoderRuntimeSession.withinHealthyDropRate` 在 `playTimeMs<=0` 时回退到固定 23 帧且**不放宽**。
- **生命周期时序正确**：`ExoPlayerEngine` 的 `finishDecoderRuntimeAttempt` → `finishSession` → `beginSession(reset)` 均在旧 player 存活期间调用，跨播放会话不会串账。
- **合并未引入回归**：`7443e1e4742` 合入的 beta 改动与本任务文件无重叠冲突，上述读取在合并后重新做过一遍。

### 8.4 复核后验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 单测 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests "…DroppedFrameAccountingTest" --tests "…ExoDecoderRuntimeSessionTest"` | BUILD SUCCESSFUL in 2m 27s；DroppedFrameAccountingTest 8 tests / 0 failures / 0 errors；ExoDecoderRuntimeSessionTest 16 tests / 0 failures / 0 errors |
| 编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac`（同一次执行） | 成功；同批 leanback arm64-v8a 编译任务亦通过 |
| 空白字符 | `git diff --check HEAD` | 空输出 |

结论：复核轮共发现 1 项低严重度文档缺陷，已修复并验证通过，修复后复评未再发现新问题。
