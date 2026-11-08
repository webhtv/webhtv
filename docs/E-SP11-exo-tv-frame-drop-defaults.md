# E-SP11 电视端 EXO 掉帧：默认调度/丢帧阈值归因

- **任务 ID**：`E-SP11`（Exo 性能专项，`E-SP*` 家族）
- **开始时间**：2026-10-21 11:05 (+08:00, Asia/Shanghai)
- **lane**：诊断 + 默认值治理（非上游合并、非 ABI/原生改动）
- **当前状态**：**2026-10-22 更新：10-21 测试版（`590c424568`）相对 10-17 测试版（`323afa1a`）在**同一台 X7 Ultra、同一个 4K HEVC HDR10 60fps 文件**（`contentLength=5566387403`/`duration=2889979`/`15408796 bps` 逐字节相同）上实测掉帧 0.17/s → 1.14/s（约 6.7 倍）；证据、假设与判定规则见 §14，设备分组操作单见 §15，强制最佳实践评审见 §16；阶段 B 仍在进行中**
- **阻塞**：本机 shell 无法启动（`bash` 与 `pwsh` 均报 `DSH workspace preparation failed (not a user permission denial)`），因此 `task_guard.sh`、Gradle 编译/单测、`git` 校验、AAR 反编译在本轮均无法执行；电视盒真机不在本会话可达范围。

---

## 0. 用户问题与复现口径

**现象**：同一个视频，当前应用（WebHTV）播放掉帧；`FongMi/TV`、`OK影视 5.1.6`、本仓 `Silent1566` 分支能稳定播放。历史上怀疑与「音频去广告」有关，该功能已删除（`docs/C45-remove-ad-audio-fingerprint-speech.md`）后仍有反馈。

**已排除**：去广告链路本身（代码已移除）。残留的 `ExoStartupAudioRenderer` 只是把 `MediaCodecAudioRenderer` 外层包裹一次（`app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java:937-942`），不解释「与内容相关的掉帧」，可在阶段 B 顺带确认其为透传。

**代码结构前提**：电视端与手机端共用同一套 `app/src/main` EXO 实现（`src/leanback` 无独立播放器代码，仅 `testLeanback` 为测试源集）。因此「电视版才掉帧」的差异只能来自**运行时配置 + 设备时序**，不存在「电视版另一套播放器」。

---

## 1. 结论（可判定部分）

本仓库默认开启了一条**成链的 media3 省电/低唤醒调度路径**，并把「解码输入丢帧阈值」压到上游默认值的 **1/3**：

| 环节 | WebHTV 默认 | 上游（media3 / FongMi / OK影视 基线） | 位置 |
|---|---|---|---|
| 动态调度 `experimentalSetDynamicSchedulingEnabled` | **开**（`perf_dynamic_scheduling=true`） | 未启用（opt-in 实验 API） | `ExoUtil.java:305-306`；开关 `PlaybackPerformanceSetting.java:314-321`、`PlaybackPerformanceSetting.java:580` |
| `setEnableDurationToProgressUs`（按解码耗时推迟唤醒） | **开** | **false** | `ExoFrameSchedulingRendererSettings.java:33-46`、`ExoUtil.java:952-953`；开关 `PlaybackPerformanceSetting.java:332-339`、`:581` |
| 强制异步 `MediaCodec` 队列 | **开**（`forceEnableMediaCodecAsynchronousQueueing`） | 不强制 | `ExoUtil.java:945-951`；开关 `PlaybackPerformanceSetting.java:323-330`、`:579` |
| 解码输入丢帧阈值 | **5 000 µs** | **15 000 µs**（1.9.0-rc01 起） | `ExoUtil.java:103`（`ENHANCED_LATE_THRESHOLD_TO_DROP_INPUT_US`）、`:954`、`ExoRuntimeAwareVideoRenderer.java:109-112` |
| Surface 固定尺寸 | 开 | 上游无此行为（`PlayerView` 用布局尺寸） | `PlaybackActivity.java:716-738` |

**上游口径证据**
- `MediaCodecVideoRenderer.DEFAULT_LATE_THRESHOLD_TO_DROP_DECODER_INPUT_US = 15000`，且上游**默认就会丢**迟到帧：[MediaCodecVideoRenderer 参考](https://developer.android.com/reference/androidx/media3/exoplayer/video/MediaCodecVideoRenderer)、[release 分支源码](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultRenderersFactory.java)
- 1.9.0-rc01 才把该默认值调到 15 ms：[release notes](https://github.com/androidx/media/releases/tag/1.9.0-rc01)
- `setEnableDurationToProgressUs` 上游默认 **false**：[Builder 参考](https://developer.android.com/reference/androidx/media3/exoplayer/video/MediaCodecVideoRenderer.Builder)
- `VideoFrameReleaseControl` 的早释放上限本身写死 50 ms：[androidx/media issue #2990](https://github.com/androidx/media/issues/2990)

**机制（为什么这三条会掉帧）**
`durationToProgress` 的语义是「播放器按解码器预计可出帧时间推迟唤醒」，它只在「动态调度开 + 解码器异步 + 注册了 Callback」三者同时成立时生效 —— 本仓库默认恰好三条全开。收益是 CPU 空转减少；代价是**唤醒预算更紧**。电视盒 SoC/内核定时器精度差、且常被其他后台（预加载、站源请求、WebHome 页面）抢占时，按「预计可出帧时间」算出的唤醒点会落到帧截止之后：输出帧错过 vsync → `onDroppedVideoFrames`；同时 5 ms 阈值让解码输入在「预测将迟到」时就被丢弃（上游给 15 ms 余量）。两者叠加表现为**内容相关、设备相关、与音频链路无关**的掉帧，与用户反馈的三个特征完全吻合。

---

## 2. 为什么其他播放器不卡

同为 media3 内核（`FongMi/TV`、`OK影视`）时，上述四项都取上游默认：不启用动态调度、不算 durationToProgress、不强制异步队列、丢帧阈值 15 ms。本仓库为了「省 CPU / 4K 掉帧缓解」把它们全打开并把阈值收紧，等于**用画面连续性换唤醒次数**。这解释了「同一个视频、别的播放器稳、本应用掉帧」。

`Silent1566` 分支若不含这组默认值，则它不掉帧也是同一解释。

---

## 3. 需一并更正的既有说法（防止继续误导排查）

`PlaybackPerformanceCatalog.java:113` 的选项说明写着「动态调度…保持开启（默认）通常更省 CPU、**掉帧更少**」。本轮证据只能支持「更省 CPU」，**不支持「掉帧更少」**，且方向可能相反。该文案在定案前不应作为结论引用；定案后需按实测结论改写。

---

## 4. 阶段 B：零改动 bisect（不需要重新打包，可直接在已发布包上做）

四个开关都是运行时设置，且设置页已有对应项（`PlaybackPerformanceCatalog.java:112-116`）。因此**可以先用发布包定位，再决定是否改代码**。

1. 设置 → 播放性能 → 切「自定义」（避免预设回写覆盖单项开关）。
2. 固定同一片源、同一电视盒、同一网络，每组连续播放 10 分钟：

| 组 | 改动 | 目的 |
|---|---|---|
| A | 全默认 | 基线（复现掉帧） |
| B | 只关「输入丢帧阈值」 | 判定 5 ms 阈值贡献 |
| C | B + 关「Media3 动态调度」 | 判定动态调度贡献 |
| D | C + 关「解码耗时推进」 | 判定 durationToProgress 贡献 |
| E | D + 「MediaCodec 队列」改自动 | 判定强制异步贡献 |
| F | E + 关「Surface 固定尺寸」 | 判定 Surface 尺寸贡献 |
| G | F + 「帧率匹配」改关闭 | 判定刷新率节拍贡献 |

3. 判读：优先看设置页/诊断导出的 `droppedFrames` 增量与 `onVideoFrameProcessingOffset` 分位（已有实现：`PlaybackAnalyticsListener.java:501-516`、`AV-DIAG-01-playback-diagnostics.md`）；用 `logcat` 的 `droppedFrames=` 行看是否成串。
4. 对照：同片同盒跑 `FongMi/TV` 与 `G:\下载\OK影视-5.1.6`，取同量级指标（二者无同款诊断，退化为 `dumpsys SurfaceFlinger --latency` + 肉眼卡顿点对齐）。

**判定规则**：某一组相对上一组丢帧率降幅 > 70% 即锁定该环节为主线；若 F、G 才有明显收益，则 Surface/帧率方向为主，动态调度链降为次要。

**注意**：23.976/24p 内容在 60/50 Hz 面板上本身就是 3:2 pulldown 抖动，属「节拍抖动」而非「丢帧」，G 组用于把两者分开，避免误判修好。

---

## 5. 阶段 C：候选修复（按阶段 B 结果择一，均不删除开关）

### C-1 对齐上游默认（推荐作为起点）
- `PlaybackPerformanceSetting.putRecommendedFlags()`（`:578-587`）与四个 getter 兜底（`:314-348`）：`perf_dynamic_scheduling`、`perf_video_duration_progress` 默认改 `false`；`perf_codec_async_queueing` 改由 `ExoPerformanceSetting` 的 `CODEC_QUEUE_AUTO` 决定，不再在预设里写死 `true`。
- 新增迁移键（沿用本文件既有 `migrateXxx()` 模式，见 `:69-89`），只对「未自定义」的老用户生效。

### C-2 只收阈值（最小改动）
- `ExoUtil.ENHANCED_LATE_THRESHOLD_TO_DROP_INPUT_US`：`5_000L` → 上游 `15_000L`（或直接不再覆盖，交给依赖默认值）。
- 优点：不动调度链，回归面最小；缺点：若主线是动态调度则无效。

### C-3 按设备/profile 分级
- 动态调度链只在「推荐/自动」之外的低端兼容预设里开启；电视端默认走 `false`。
- 需要 `PlaybackPerformanceSetting` 增加「电视端」维度的默认值分叉，改动面比 C-1 大，仅当 C-1 在手机端造成明显功耗/温度回退时才采用。

**共同约束**：三个开关必须成组思考 —— 关 `dynamic_scheduling` 后 `duration_progress` 自动失效（其文档明确仅在动态调度下生效），单独关后者可能只是「看起来改了」。

---

## 6. 验证矩阵（阶段 C 之后）

- **单测**：`PlaybackPerformanceSetting` 预设写值、迁移幂等、缺键兜底；`ExoFrameSchedulingRendererSettingsTest`、`ExoFrameSchedulingExperimentPolicyTest` 保持通过。现有测试是否断言了旧默认值需先检索（本轮无法运行 Gradle，未确认）。
- **设备**：用户回报片源 + 4K HEVC 23.976 + 1080p AVC 25 + DV5（Vulkan 路径）；无外挂字幕 / ASS / 双字幕；硬解 / 软解；seek 后 60 s 稳态。
- **指标**：10 分钟丢帧率、`processingOffset` p95、首帧时间、seek 完成时间、音画偏移。
- **验收**：A 组现象消失，且与 FongMi 对照差距落在噪声内；手机端不出现功耗/温度回退。

---

## 7. 回滚

不删除任何开关与代码路径，只改默认值与阈值；回滚 = 恢复原默认值 + 让迁移键失效重新初始化。任何单台设备都可现场切回。

---

## 8. 未决与如实记录

1. **未实测**：本会话无电视盒，§1 的机制是**代码与上游默认值对比推出的可证伪假设**，不是实测结论。阶段 B 是唯一的判定手段。
2. **依赖源码未终审**：`media3 = "1.11.0-alpha01-fongmi"`（`gradle/libs.versions.toml:50`）是 FongMi fork，其内建默认值理论上应与 1.9+ 一致；本轮因 shell 不可用无法反编译 `libs/` 内 AAR 交叉确认。若 fork 已改过默认值，§1 表格的「上游」列需以 fork 源码复核。
3. **A/B 实验未定位是否生效**：`ExoFrameSchedulingExperimentPolicy` 需要设备 `deviceDigest` 被分配才应用（否则 `Reason.DEVICE_NOT_ENROLLED` 回落 `stable`）；阶段 B 前需确认无有效分配，否则会污染测量。
4. **`isAuto` 与预设关系**：新装默认 `PROFILE_AUTO`，`putRecommendedFlags()` 会在首次初始化写入上述 `true`；因此「关掉」必须走自定义或新预设，单纯改代码里的 `Prefers.getBoolean(..., true)` 兜底对老用户无效 —— 迁移键是必需品。

---

## 9. 实施记录（2026-10-21，本会话）

按「只消除本仓相对上游的偏差、不动省电主干」的原则落地两处改动，两者互相独立、可单独回滚：

| # | 改动 | 文件 | 说明 |
|---|---|---|---|
| 1 | `ENHANCED_LATE_THRESHOLD_TO_DROP_INPUT_US` `5_000L` → `15_000L` | `app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java:105` | 与 media3 1.9.0-rc01+ 默认一致。纯放宽：原先被提前丢弃的迟到帧现在会照常呈现。两处使用点（`DefaultRenderersFactory` 路径与 `ExoRuntimeAwareVideoRenderer` builder）共用该常量，自动一并生效。 |
| 2 | `perf_video_duration_progress` 默认 `true` → `false` | `PlaybackPerformanceSetting.java:337`（getter 兜底）、`:584`（`putRecommendedFlags`） | 与上游默认一致。仍保留开关：动态调度开着时不再按「预计出帧时间」推迟唤醒，从而保留动态调度的 CPU 收益、去掉最激进的唤醒预算。 |
| 3 | 既有用户迁移 | `PlaybackPerformanceSetting.java:734-746`、`:84`、`:35-37` | 新增 `KEY_EXO_FRAME_SCHEDULING_PARITY_MIGRATED`（`..._parity_v1`）+ `migrateExoFrameSchedulingParity()`，沿用既有 `migrateXxx` 惯用法：只对非 `PROFILE_CUSTOM` 且该项未被显式覆盖的用户写回 `false`，幂等。 |
| 4 | 选项说明更正 | `PlaybackPerformanceCatalog.java:113-114` | 「动态调度…掉帧更少」改为条件表述（本轮证据不支持该结论）；「解码耗时推进」改为默认关闭并说明代价。 |

**未改动**（留给阶段 B 判定）：`perf_dynamic_scheduling`（仍默认开）、`perf_codec_async_queueing`（仍默认开）、`perf_surface_fixed_size`（仍默认开）、`FRAME_RATE_SEAMLESS`。

**验证状态：未编译、未装机、未实测。** 本会话 `bash` 与 `pwsh` 均报 `DSH workspace preparation failed`，REA 的 Windows 进程通道也不可用（`capability_unavailable`），因此 Gradle 编译、单测、`task_guard.sh`、`git` 提交/打 tag 全部无法执行；`emulator-5558`（adb 端口 5559，已装 `com.silent.android.webhtv` 5.6.0 debuggable）无法接收新包。
**恢复后的验证顺序**：`bash .codex/scripts/task_guard.sh start --id E-SP11 --mode quick-fix --scope app/src/main/java/com/fongmi/android/tv/player/exo app/src/main/java/com/fongmi/android/tv/setting docs/E-SP11-exo-tv-frame-drop-defaults.md` → 双 flavor Java 编译 → `./gradlew :app:testLeanbackDebugUnitTest --tests '*player.exo.*'`（该目录 600+ 例，无测试断言这两个默认值，预期零改动失败）→ 装机 `adb -s emulator-5558 install -r` → 用 §4 的 A/B 两组对比。

## 10. 首轮模拟器观测（2026-10-21 11:21 装机，11:23–11:24 采样）

**环境**：`emulator-5558`（x86，`app_process32`），`com.silent.android.webhtv` 5.6.0，`last_update_time=2026-10-21 11:21:36`（含本次改动）；片源 730 902 ms / `video/mp4` / 4 073 199 bps / 372 MB，路由 `EXTERNAL_LOOPBACK_PROXY`，`audioOutput=PCM`、`tunnel=false`。

**观测**（`TV-playback-telemetry` 逐样本）：

| 指标 | 值 | 判读 |
|---|---|---|
| `firstFrameMs` | 3 491 | 正常 |
| `rebufferCount` / `rebufferTotalMs` | 0 / 0 | 无重缓冲 |
| `bufferedMs` | 21 000–31 000 | 缓冲健康 |
| `dropped` | ≥100 累计（回调按 50 递增） | 掉帧仍在 |
| `renderedFps` | 12.500 | 渲染帧率异常偏低 |
| `bandwidthBps` vs `mediaBps` | 2 400 000 vs 4 073 199 | 带宽低于码率 |
| 解码器 | `FfmpegVideoDecoder` + `VIDEO_DECODER_ERROR_READ_FRAME` 反复 | **FFmpeg 软解** |

**结论：本轮不能用于判定改动效果。** 可见瓶颈是模拟器侧的软件解码（x86 无真实硬件解码器，`/dev/fastpipe` ioctl 被 SELinux 拒绝）叠加回环代理带宽低于码率；这与电视盒的硬件解码路径不是同一种负载。可作为「无崩溃、无重缓冲、缓冲健康」的健全性证据，但不能作为「掉帧已修复」的证据。模拟器仍有一个有效用法：**环境恒定**，在同一模拟器上做 A/B 两组对照，差值仍有参考价值。

**顺带发现（与本任务无关，仅记录不修）**
1. `DefaultTrackSelector: Audio channel count constraints cannot be applied without reference to Context` —— TrackSelector 用了不带 `Context` 的已废弃构造，音频声道数约束被**静默忽略**；每次 prepare/seek 都出现。
2. `System: A resource failed to call release` 反复出现 —— 有资源未释放（Surface/Codec 类），属泄漏迹象。
3. `TV-SpiderDebug: Executor did not terminate in time for range ...` —— 预加载线程池关闭超时（1 s 未退出），低端机上是潜在卡顿源。
4. `AudioActivity`/`)` 无关；`TV-ai-rec` 推荐接口超时不涉及播放。

## 10.1 设置状态核对（11:28，`run-as` 读取 SharedPreferences）

改动**已真实生效**，且迁移按设计执行：

```
perf_video_duration_progress = false                                  ← 默认值改动
playback_performance_exo_frame_scheduling_parity_v1 = true            ← 迁移键已落盘
perf_dynamic_scheduling = true                                        ← 按计划未动
perf_late_drop_input = true                                           ← 开关仍在，阈值已放宽到 15 ms
perf_codec_async_queueing = true                                      ← 按计划未动
perf_exo_profile = 4 (AUTO) / perf_exo_frame_rate_mode = 1 (仅无缝) / tunnel=false
```

解码运行时存档显示正在播放的内容为 **`video/hevc` 3840×1606**（另有多条 `video/hevc` 3840×1608/1920、`video/avc` 1080p 记录），帧率字段为 `25000`/`60000` → **4K HEVC、25 fps**。

## 10.2 第二轮观测：切「硬解」后仍走 FFmpeg 软解

用户切换硬解后，会话 `p-24rf8a-5`（位置 649–679 s）指标：

| 指标 | 值 |
|---|---|
| 解码器 | **仍为 `FfmpegVideoDecoder`**（`VIDEO_DECODER_ERROR_READ_FRAME` 持续每 ~1–10 s 出现） |
| `dropped` | **5 920**（会话累计） |
| `firstFrameMs` | **9 995**（软解会话为 3 491） |
| `rebufferCount` | 1（142 ms） |
| `bufferedMs` | 27 000–33 000 |
| `renderedFps` | unknown |

**判读**：x86 模拟器没有 4K HEVC 硬件解码器，硬解请求必然失败并按 `E-SP9` 的「解码器兜底」自动回落软解——日志里没有 `MediaCodecVideoRenderer`，只有 FFmpeg。因此：
1. 本环境**无法**用来验证掉帧修复；掉帧由解码吞吐决定（~10 fps 丢弃对应 25 fps 内容）。
2. 首帧从 3.5 s 恶化到 10 s，是「先试硬解失败再回落」的代价，属可优化项（不在本任务范围，记录待评估）。
3. 两个会话的 `dropped` 不能直接对比：会话内容/位置不同，且第二会话多了硬解尝试与回落过程。

**结论**：模拟器这条线到此为止，除「改动已生效、应用未回归」外无法提供掉帧判据。真正的验收必须在硬解可用的设备（电视盒）上做。

## 10.3 第三轮观测：1080p + 硬解（健康基线）

会话 `p-25aj5m-b`：47:21 片源（2 841 680 ms）、`mediaBps=2 707 721`、`bandwidthBps=45 443 801`（**16.8× 余量**）。

| 指标 | 值 | 判读 |
|---|---|---|
| 解码器 | **MediaCodec 硬解**（整个会话 `FfmpegVideoDecoder` 计数 = 0） | 硬解真正生效 |
| `renderedFps` | **25.000** | 与片源一致 |
| `dropped` | **0**（0 次 `droppedFrames` 回调、0 个非零 rebuffer 采样） | 无掉帧 |
| `firstFrameMs` | 1 028 | 起播快 |
| `bufferedMs` | 42 575 | 缓冲充足 |
| `rebuffer` | 0 | 无重缓冲 |

**三会话对比（同一构建、同一进程，仅换片源与解码路径）**

| | 4K HEVC 软解 | 4K HEVC 切硬解后（仍软解） | 1080p 硬解 |
|---|---|---|---|
| 解码器 | FFmpeg | FFmpeg | MediaCodec |
| `renderedFps` | 12.5 | unknown | 25.000 |
| `dropped` | 100 / 73 s | 5 920 | **0** |
| `firstFrameMs` | 3 491 | 9 995 | 1 028 |
| `rebuffer` | 0 | 1（142 ms） | 0 |

**结论**：掉帧与**解码路径**强相关，与帧调度/丢帧参数无关。本轮同时证明改动后的构建在硬解路径上**无回归**（0 掉帧、25 fps、1 s 首帧）。

## 10.4 由本次对照得出的新假设（优先级高于调度参数）

模拟器的表现说明：**只要落到软解，4K 就会大面积掉帧；其他播放器不卡，很可能是因为它们仍在硬解。**

本仓有一个上游 media3 没有的机制：**按设备持久化排除解码器**。

- `ExoDecoderRuntimeSession.shouldExclude()`（`:154-167`）在每次选解码器时按 `(环境, 解码器名, 格式签名, secure, output)` 判黑名单，命中即从候选中剔除：`profiles.isBlacklisted(key, nowEpochMs)`。
- `ExoDecoderRuntimeProfileStore.ENTRY_TTL_MS = 30 天`（`:17`、`:241`）：黑名单条目 30 天未刷新才过期。
- 结果是：**某台设备只要历史上记录过一次该解码器+该格式的失败，之后 30 天内都会静默改用软解**；用户只感知到「同一个视频掉帧、别的播放器不卡」，且该状态跨版本升级保留——与「删掉音频去广告后仍然掉帧」的现象一致。
- stock media3 / FongMi / OK影视 没有这个机制，因此不存在这种静默降级。

**现场判定方法**（可让用户执行，或从应用诊断导出读取）：

```bash
adb shell run-as <包名> cat shared_prefs/<包名>_preferences.xml | grep perf_exo_decoder_runtime_v1
```

若条目中出现该片源的 `video/hevc` + 对应分辨率组合，即说明硬解被本机黑名单排除。当前模拟器会话**没有**出现排除日志，1080p 走的是硬解，符合预期。

**待验证**：用户设备上该 store 的实际内容。这是把「掉帧」从调度问题改判为解码路径问题的关键证据。

## 12. 实施记录（2026-10-21，解码器排除显性化）

按 §10.4 的假设补充可观测性，使「静默降级到软解」在设置页可见：

| 改动 | 文件 | 内容 |
|---|---|---|
| 存储层读取接口 | `ExoDecoderRuntimeProfileStore.java` | 新增 `activeBlacklist(long)`：返回当前生效的排除条目，按 `updatedAtEpochMs` 倒序；只读，会顺带执行既有 `prune` |
| 门面层摘要 | `ExoDecoderRuntimeProfiles.java` | 新增 `blacklistedCount()`、`blacklistSummary()`（无命中返回 `""`）、`mimeLabel()`；摘要形如 `硬解已排除 1 条（HEVC 3840×2160）` |
| 设置页展示 | `PlaybackPerformanceSetting.java` | `getSummary()` 在存在排除时追加 `"，" + 摘要`，否则保持原样 |
| 回归锁 | `ExoDecoderRuntimeProfilesSummaryTest.java`（新增） | 4 例：无排除为空、两次连续失败后摘要含格式与分辨率、单次失败不产生摘要、`activeBlacklist` 倒序且跳过健康条目 |

**验证（本会话实测）**
- `bash ./gradlew :app:assembleLeanbackArmeabi_v7aDebug --offline` → **BUILD SUCCESSFUL（2m30s）**
- `:app:testLeanbackArmeabi_v7aDebugUnitTest --tests '*ExoDecoderRuntime*'` → **34 例 / 0 失败**（Store 13、Session 13、Key 4、新 Summary 4）
- `adb -s emulator-5558 install -r` → Success；启动无 `FATAL`/`AndroidRuntime`

**边界**：设置页那一行的**实际渲染未做屏幕走查**；目前证据是「摘要逻辑单测通过 + 编译通过 + 装机可启动」。若需要，可在构造一条黑名单条目后截图确认。

## 13. C91 复评修复（2026-10-21，`7faf6cea93` 与本轮改动）

`docs/C91-beta-merge-review-dev3-20261021.md` 对提交 `7faf6cea93` 的复评结论：4 项必修，已全部落地并各有可证伪锁。

| # | 级别 | 问题 | 修复 | 锁 |
|---|---|---|---|---|
| 1 | **high** | 「输入丢帧阈值」关闭时不调用 setter，而 media3 fork（`1.11.0-alpha01-fongmi`）的 `lateThresholdToDropDecoderInputUs` 默认就是 15 ms。E-SP11 把开启值也改成 15 ms 后，**开/关完全等价**，开关变 no-op（关掉仍丢输入帧）。fork 源码 `MediaCodecVideoRenderer.java:700-707` 明确只有 `C.TIME_UNSET` 才不丢。 | `ExoUtil.java`、`ExoRuntimeAwareVideoRenderer.java` 两条构建路径：开 → `15_000L`；关 → `C.TIME_UNSET`（后者补 `else` 分支与 `import androidx.media3.common.C`）。 | `PlaybackPerformanceSettingSourceTest.lateDropThresholdSwitchCanActuallyDisableDropping` |
| 2 | medium | `blacklistedCount()/blacklistSummary()` 统计 store 内**所有**黑名单条目，但键含 `environmentDigest`（fingerprint + app 版本 + media3 版本）且条目 30 天 TTL 跨升级保留 ⇒ 升级后旧条目仍被显示成「当前生效的硬解排除」。 | 新增 `ExoDecoderRuntimeProfiles.activeBlacklist()`，按当前 `environment.digest()` 过滤后再计数/取最新。 | `ExoDecoderRuntimeProfilesSummaryTest.summaryIgnoresExclusionsRecordedUnderAnotherEnvironment` |
| 3 | low | `PlaybackPerformanceSetting.getSummary()` 无条件追加 EXO 摘要，而该行同时服务 EXO/MPV/IJK；后两者会显示该内核不可能发生的降级。 | 仅 `PlayerSetting.getPlayer() == PlayerSetting.EXO` 时追加。 | 复核（无单测，行为受内核选择控制） |
| 4 | low | 两项设置说明与实现不符（把掉帧风险写死成「电视盒」；「输入丢帧阈值」未说明 15 ms 与关闭代价）。 | `PlaybackPerformanceCatalog` 改为条件表述，补 15 ms 与「关闭后不丢输入帧、可能累积音画延迟」。 | 复核 |

**验证**：双 flavor arm64 Debug Java 编译 `BUILD SUCCESSFUL in 3m 19s`；`--tests '*ExoDecoderRuntime*' --tests '*PlaybackPerformance*'` **50 例 0 失败**；exo 全包 **617 例 / 1 skipped / 0 失败**；`check_ui_tokens.sh` PASS。

**仍未解决（与本任务原有边界一致）**：阶段 B 设备 bisect 与电视盒实测未执行。本轮 `adb devices` 只有 `127.0.0.1:5557` 与 `emulator-5556`，均为 x86_64 / SDK 28 镜像，且未构建 x86_64 产物 ⇒「阈值关闭后的丢帧行为」与「摘要那一行的实际渲染」都没有设备端证据。

## 11. 下一步（历史记录，已被 §14 / §15 取代）

（原文保留）装上新包后先只做 §4 的 A（全默认）与 B（关「输入丢帧阈值」）两组对照：若新默认下 A 组已明显好于旧版，说明「5 ms 阈值 + durationToProgress」就是主线，收工；若 A 仍掉帧而 B 好转，再单独评估把 `perf_late_drop_input` 默认关掉（此时阈值已放宽，需重新取值）。

---

## 14. 最新 TV 测试版回归归因（2026-10-22，本会话，证据级：观测）

**用户现象**：最新 TV 测试版 EXO 掉帧、变卡；给出两份导出日志（`webhtv-debug-log (28).txt`、`(29).txt`）。

### 14.1 构建坐标（两份日志同进程、同构建）

- `appVersion=5.6.0` / `versionCode=560` / `buildTime=202610212003` / `buildTag=v5.6.0-beta-202610211951`
- `gitRevision=590c424568d5d1eaa713fe8a06c6c57cd013f42c`（当时 `origin/beta` 的 tip；本地 `dev2` 已并入）
- `media3=1.11.0-alpha01-fongmi`、flavor `leanback`、abi `arm64_v8a`
- 设备：`Dangbei X7 Ultra`（`device=horse_popular`，Android 14 / API 34，MTK）
- 片源：`video/hevc`、`hvc1.2.4.L153.90`、3840×2160、HDR10、**60 fps**（PTS 实测 60.0 帧/秒）、平均码率 ≈15.3 Mbps、时长 3 200 615 ms
- 解码：`c2.mtk.hevc.decoder`（**硬解**，非 FFmpeg 软解）；`tunneling=false`、音频 `offload=false` → PCM；`renderTarget=surface-view`；Surface 固定 `1920x1080`；显示 `1920x1080@60Hz`
- 线路：`EXTERNAL_LOOPBACK_PROXY`（外部回环代理，upstream 不可见）；`bandwidthBps=13 276 094 < mediaBps=15 344 176`、`pathTrust=blocked`、`throughputConfidence=low`

### 14.2 跨构建对照（同一台 X7 Ultra、同类 4K HDR10 60 fps 片源、同一硬解路径与线路类型）

| 构建 | 日志 | 稳态窗口 | 解码输入 | 提交渲染 | 丢帧 |
|---|---|---|---|---|---|
| `v5.6.0-beta-202610170951` = `323afa1a5a9b962749bd3e78840a4f729a2f4701` | (25) | 15:33:02→15:34:17（75 s） | 57.1/s | 56.3/s | **0**（`drop` 32→32） |
| `v5.6.0-beta-202610211951` = `590c424568d5d1eaa713fe8a06c6c57cd013f42c` | (29) | 00:14:37→00:15:02（25 s） | 61.1/s | 55.0/s | **+154（6.2/s）** |

整段会话按 `playback-telemetry` 的 `dropped` 增量/时间跨度：10-17 版 **0.17/s**（32 / 188 s）；10-21 版 **0.90–2.31/s**（66 / 74 s、235 / 102 s）。两版 `renderedFps=58.824`（PTS 中位数推出的内容节拍，不是渲染吞吐）。

### 14.3 同时确认的事实（含误读纠正）

1. 掉帧发生在**缓冲充足**时：`bufferedMs` 18–52 s、`loading=false`、`state=READY`、`rebufferCount=0` ⇒ 不是网络饥饿的直接结果。
2. 10-21 版另有 2 次 seek 后 5–10 s 的 `BUFFERING`（位置 730038 / 1279570，`bufferedMs=0`），其中一次 `input.open` 返回 0 字节。这是「变卡」的另一半，必须与掉帧分开陈述。
3. `skippedBuffers=815` 与 `discardedBuffers` 含 seek/flush 的 drop-to-keyframe（media3 `MediaCodecVideoRenderer.skipOutputBuffer` / `dropToKeyframe` 路径），不能全部算作稳态掉帧。
4. `ptsRegressions ≈ 输入的一半` 是 HEVC B 帧解码序的**正常现象**（`ExoDiagnosticCodecAdapter.input()` 只比较相邻入队 PTS），不是缺陷。
5. 10-21 版解码输入仍是**满帧率 60/s** ⇒ 15 ms 输入丢帧阈值窗口内几乎没有输入帧被丢；阈值是否被触发过**未被观测**。
6. 导出日志里**没有任何 `perf_*` 取值行** ⇒ 设备实际开关值未知；`droppedFrames` 只统计渲染器侧（`onDroppedVideoFrames`），解码输入阶段的丢弃不可见。这两点是 §16 要补的观测缺口。

### 14.4 窗口内 EXO 相关提交（`323afa1a5a9b962749bd3e78840a4f729a2f4701..590c424568d5d1eaa713fe8a06c6c57cd013f42c`，仅 `player/` + `setting/`）

| commit（40 位） | 内容 | 与帧节奏的关系 |
|---|---|---|
| `7faf6cea93026f477f0fefc29e0a20555126db9b` | E-SP11：`ENHANCED_LATE_THRESHOLD_TO_DROP_INPUT_US` 5 000→15 000；`perf_video_duration_progress` 默认 true→false + 迁移 | **直接**：唤醒时点 / 解码输入时点 |
| `a8ae5e8602afd47c5a2079a9f1a79d215f1a08f7` | C91 复评：两条构建路径统一「关=`C.TIME_UNSET`」；解码器排除摘要按 environment 过滤 | 直接（阈值语义） |
| `e79ed9058aab6cfce6f9433116e673cb89aeb140` | 硬解下保留 ffmpeg 视频回退（`EXTENSION_RENDERER_MODE_OFF→ON`） | 间接：改变渲染器列表（HEVC 仍先命中 MediaCodec） |
| `c1fd4aa13f3de8d43729ba8f28407ea3cac1e725` | E-SP10：回环代理 EOF 恢复退避 + 预加载让位 | 间接：预加载争用 |
| `ad7f5834de429a35fb729c27744110226d800c0c`、`48f59db5ae8e577cd0fba8c25be787876882d022`、`8d47a5dd34618af520ba8f01c2d6aaa6a28f78e7`、`203bb7d567ec56960aa2276e865db680b983b020`、`2d403b2a31fe96adfd4eb6fb9d848168d598d433`、`51d805e4a8c93eec4367b231963a5ce4aeb00ed6`、`d6dae3da12fec4c6054d63aa402d8d13bd175581` | 取流 / 线路切换 / 直播 manifest 重试 / 错误分类 | 与稳态渲染无直接关系 |

### 14.5 media3 fork 口径复核（`third_party/sources/media` @ `e3e922d`，`1.11.0-alpha01-fongmi`）

- `DEFAULT_LATE_THRESHOLD_TO_DROP_DECODER_INPUT_US = 15_000`（`MediaCodecVideoRenderer.java:153`）；只有 `C.TIME_UNSET` 才真正不丢输入帧（`:698-705`、`:1816-1823`）。
- `minEarlyUsToDropDecoderInput = -threshold`：阈值越小 = 越早丢迟到输入帧；越大 = 越晚丢。
- `enableDurationToProgressUs=false` 走 `Renderer` 兜底（`Renderer.java:157` `DEFAULT_DURATION_TO_PROGRESS_US=10 000`、`:166` `DEFAULT_IDLE_DURATION_TO_PROGRESS_US=1 000 000`）；`true` 时按下一帧 `earlyUs` 精确推迟唤醒（`MediaCodecVideoRenderer.java:1557-1600`）。
- `DEFAULT_EARLY_SCHEDULING_THRESHOLD_US = 50_000`（`:212`），与本仓 `ExoFrameSchedulingExperimentPolicy.BASELINE_EARLY_SCHEDULING_THRESHOLD_US` 一致，**窗口内未变**。

### 14.6 假设与判定规则（可证伪）

- **H1（阈值）**：15 ms 让更多迟到帧进入解码器，渲染器在释放时才丢。若成立 ⇒ 把开关设为「关闭」（`C.TIME_UNSET` = 永不丢输入）应让掉帧**进一步恶化**。
- **H2（唤醒）**：`duration_progress` 默认 true→false 改变了播放循环唤醒时点。若成立 ⇒ 把「解码耗时推进」打开应**显著降低**掉帧。
- **H3（都不是）**：两开关均无效果 ⇒ 回归来自 `e79ed9058a…` / `c1fd4aa13f…` 或更外层（缓存/预加载），改为逐个 revert 构建 bisect。
- **H4（不是代码）**：两版同片源对照无显著差值 ⇒ 改判片源/线路（估算带宽 13.27 Mbps < 码率 15.34 Mbps + 外部回环代理），交付结论与替代方案而不是代码补丁。

### 14.7 边界（不得含糊）

- 两份 10-21 日志是**同一构建、同一进程**，不能做构建级 A/B；跨构建结论依赖与 10-17 版（`323afa1a…`）的机器内对照，仍须补一次严格同片源 A/B。
- 两条开关都必须写成**条件表述**：`duration_progress` 只在「动态调度开 + 解码器异步队列」时生效；输入丢帧阈值只在帧「预测将迟到」时生效。
- 4K HDR10 60 fps ≈15 Mbps 可能已接近该 SoC 的舒服区（10-17 版更早窗口也有 24 帧掉落）⇒ 验收用**相对基线差值**，不要求 0 丢帧。

---

## 15. 设备 bisect 操作单（零改动，用户在 X7 Ultra 上执行）

**第 0 步：先把「未观测」变成事实**（读现场开关值，而不是按默认值推断）：

```bash
adb shell run-as com.silent.android.webhtv cat shared_prefs/com.silent.android.webhtv_preferences.xml | grep -E 'perf_(late_drop_input|video_duration_progress|dynamic_scheduling|codec_async_queueing)'
```

**设置入口**：播放器设置 → 播放性能（`PlaybackPerformanceDialog`）。**先把预设切到「自定义」**，否则预设回写会覆盖单项开关。相关项名称：「Media3 动态调度」「解码耗时推进」「输入丢帧阈值」「MediaCodec 队列」。

**分组（同片源、同位置、同一次安装；每组稳态 ≥5 min）**

| 组 | 改动 | 目的 |
|---|---|---|
| A | 当前默认 | 复现基线（预期 0.9–2.3 drops/s） |
| B | 只把「解码耗时推进」打开 | 判 H2 |
| C | B + 「输入丢帧阈值」关闭 | 判 H1（若 H1 成立应更差） |
| D | C + 「MediaCodec 队列」改自动 | 排除强制异步队列 |

**取数**（每组结束导出一次诊断日志，或看设置页摘要里的掉帧数）：
- `playback-metrics: droppedFrames=<n> total=<n> elapsed=<ms>ms` → `drop/s = n / (elapsed/1000)`；
- `av-diag` 的 `video.output.summary`（`source=media3-decoder-counters`）：`droppedBuffers` 增量 ÷ `inputBuffers` 增量；
- 同时记录 `bufferedMs` 与是否出现 `BUFFERING`（把掉帧与 seek 缓冲分开）。

**判定**：某组相对 A 的 `drop/s` 降幅 ≥70% ⇒ 该开关为主线（H1/H2）；四组都无明显收益 ⇒ H3；若 A 组本身就无法复现（与 10-17 版无差别）⇒ H4。

**对照**：用 10-17 版 APK（`v5.6.0-beta-202610170951`，`323afa1a5a9b962749bd3e78840a4f729a2f4701`）同片源跑一次，确认基线量级；若用户认为「上一个好版本」是别的 tag，以用户指认为准。

**回填**：把每组的开关值、`drop/s`、位置区间、是否 `BUFFERING` 写回本节。

**追加要求（2026-10-22 修订，见 §15.1 / §16.3）**：A/B 必须用**同一文件、同一位置区间**。日志 (25) 与日志 (28) 已经证实播的是**同一个文件**（`contentLength=5566387403`、`duration=2889979`、`bitrate=15408796` 三者逐字节相同），但**位置区间不同**（(25) 在 1413–2128 s，(28) 在 14–1254 s）。由于外部证据（§16.3）显示同型号 MTK 解码器的迟到帧与**编码内容**强相关，切换开关时请固定在**同一位置**（建议从 2:00 起连续 5 分钟）。

### 15.1 同文件跨构建对照（更强的证据）

| 构建 | 文件 | 位置区间 | `dropped` 增量 / 时长 | 速率 |
|---|---|---|---|---|
| `v5.6.0-beta-202610170951` = `323afa1a5a9b962749bd3e78840a4f729a2f4701`（日志 25） | 玩偶 4K HEVC HDR10 60fps、`contentLength=5566387403`、`duration=2889979 ms`、15 408 796 bps | 1413–2128 s | 32 / 188 s（其中稳态 75 s 增量为 **0**） | **0.17/s** |
| `v5.6.0-beta-202610211951` = `590c424568d5d1eaa713fe8a06c6c57cd013f42c`（日志 28） | **同一文件**（三个字段完全一致） | 14–1254 s | 206 / 181 s | **1.14/s** |

同文件、同设备、同解码器（`c2.mtk.hevc.decoder`）、同固定 Surface 1920×1080、同线路类型 ⇒ **构建间相差约 6.7 倍**。
残余混淆：两段**位置不同**（内容相关性未被完全排除）；这是把 §15 的分组测试固定到同一位置的原因。

---

## 16. 强制最佳实践评审（2026-10-22，§5 gate；证据分级：一手源码/官方文档/上游 issue）

**决策问题（单一）**：E-SP11 把 `perf_video_duration_progress` 默认 true→false、丢帧阈值 5→15 ms（「对齐上游」）对**电视盒**是否是正确的默认值？应保留、回退还是改成设备分级？

### 16.1 上游实际基线（一手源码）

| 来源 | 修订 | 证据 | 结论 |
|---|---|---|---|
| `/f/Workspace/_refs/FongMiTV-fongmi`（本地 clone） | `a00ebd2571d5b7635a768b0241635a45a07bb4a2`（2026-05-14，**早于**锁定的 `1a19fee2…`） | `app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java`：`buildRenderersFactory()` 只有 `setAudioPrefer/setVideoPrefer/setEnableDecoderFallback(true)/setExtensionRendererMode`；`getRenderMode(HARD)=EXTENSION_RENDERER_MODE_ON` | 五个帧节奏开关**全部未调用** |
| `https://github.com/FongMi/TV` @ `1a19fee278fa2234da725d61a53bf59b69fe9127`（锁文件 `third_party/fongmi-repositories-lock.json` 的 `repositories.tv.selected_commit`，2026-10-22 经 raw 获取 HTTP 200） | 同上 | `ExoUtil.buildPlayer()` = `new ExoPlayer.Builder(...).setSkipSilenceEnabled(...)` + `preloadManagerBuilder.buildExoPlayer(...)`；`buildRenderersFactory()` = `DefaultRenderersFactory` + `setEnableDecoderFallback/setDolbyVisionOutputPolicy/setExtensionRendererMode(ON)` | 确认：上游**没有** `experimentalSetDynamicSchedulingEnabled`、`setEnableDurationToProgressUs` / `setEnableMediaCodecVideoRendererDurationToProgressUs`、`experimentalSetLateThresholdToDropDecoderInputUs`、`setVideoRendererEarlySchedulingThresholdUs`、`forceEnableMediaCodecAsynchronousQueueing` ⇒ 上游对这些全部吃 media3 默认 |
| `third_party/sources/media`（本仓 media3 fork 源码） | `e3e922d`（`1.11.0-alpha01-fongmi`） | `MediaCodecVideoRenderer.java:153/212/305-306/481-483/698-705/1816-1823`；`Renderer.java:157/166` | fork 默认与上游一致：late-drop **15 ms**、early scheduling **50 ms**、`enableDurationToProgressUs` **false**、dynamic scheduling 由调用方 opt-in |

**⇒ 「未修改的上游」= 四个开关全关（dynamic scheduling off / durationToProgress false / late-drop 15 ms / 队列 auto）。** 本仓是**主动偏离**：`perf_dynamic_scheduling=true`、`perf_codec_async_queueing=true`、`perf_surface_fixed_size=true`，并由 E-SP11 把 duration_progress 与阈值改成上游值。

### 16.2 官方文档口径（一手）

- `setEnableDurationToProgressUs`：默认 **false**；仅在「动态调度开启 + 解码器异步且已注册 Callback」时生效；`true` 的作用是**增加 CPU 空闲、降低功耗**（[Builder 官方文档](https://developer.android.com/reference/kotlin/androidx/media3/exoplayer/video/MediaCodecVideoRenderer.Builder)）。
- `experimentalSetLateThresholdToDropDecoderInputUs`：默认 `DEFAULT_LATE_THRESHOLD_TO_DROP_DECODER_INPUT_US = 15000`；`TIME_UNSET` 才不丢（同上文档）。
- `1.9.0-rc01` release notes：该默认值改为 15 ms，并「enable more efficient dropping of video frames **before decoding** for eligible videos」；同版本还改了 `getMinDurationToProgressUs` 在「无需 render 时返回更大值」（[release](https://github.com/androidx/media/releases/tag/1.9.0-rc01)）。

### 16.3 上游 issue（与本案高度同型，改变判断权重）

- [androidx/media#3347](https://github.com/androidx/media/issues/3347)：**`c2.mtk.hevc.decoder`（MediaTek MT8696）** 在特定 HEVC 片源上 **~30% 的帧迟到 30–130 ms**（中位 ~70 ms），同一硬件同一内容 **VLC 无卡顿**；作者直接给 `MediaCodecVideoRenderer` 加日志量测 `earlyUs` 后确认根因是**解码器交付延迟**，并明确「放宽 `shouldDropOutputBuffer` 阈值（-30 ms → -500 ms）**不解决问题**，只是把『跳帧』换成『顿住』」；触发因素被定位到**编码器世代**（x265 3.3 编码的同一素材可复现，x265 4.1 不复现）。
- [androidx/media#3286](https://github.com/androidx/media/issues/3286)：动态调度会让 `getCurrentPosition()` 最多滞后 200 ms（1.11.0 alpha 起明显）。

**对本决策的影响**：
1. `late drop` 阈值是**观测输入侧**的开关，只影响「预测将迟到」的输入帧；上述实测到的 30–130 ms 迟到**两侧阈值都会命中**，所以「5 ms vs 15 ms」未必能区分现象 ⇒ H1 的先验概率下调。
2. 「放宽阈值=修好」已被上游实测**否证**；本仓若照此方向调参，需要设备端证据而不是文档推断。
3. 掉帧可能由**片源编码器世代 + 该 MTK 解码路径**决定，而非本仓代码 ⇒ H4 权重上调。**但本条不否定构建差异**：§15.1 的两条记录播的是**同一个文件**（`contentLength`/`duration`/`bitrate` 逐字节相同），构建间仍相差约 6.7 倍；残余混淆只剩「位置区间不同」，因此设备复测必须固定同一位置。

### 16.4 当前 WebHTV 实现复核（具体文件/符号）

- 生效链路：`PlaybackPerformanceSetting.isVideoDurationProgressEnabled()` / `isLateDropInputEnabled()` → `ExoUtil.resolveFrameSchedulingDecision()`（`ExoUtil.java:963-990`）→ `ExoFrameSchedulingRendererSettings.apply(DefaultRenderersFactory)`（`:953-958`）与 `ExoRuntimeAwareVideoRenderer.builder()`（`:108-118`）**两条路径**；硬解 + 兜底开时走后者（`ExoUtil.java:1175-1190`）。
- `ExoFrameSchedulingExperimentPolicy.stable()`：`durationToProgress = isVideoDurationProgressEnabled() && !synchronousCodecQueue`；`earlySchedulingThresholdUs` 恒定 50 ms（与 fork 默认一致）。
- 观测缺口：`ExoFrameSchedulingPlayerSettings` 已持有 decision + 队列模式，但**没有任何一行进入导出日志**（`grep -c scheduling` = 0）⇒ §17 的 D 项。
- 既有测试与契约锁：`PlaybackPerformanceSettingSourceTest.lateDropThresholdSwitchCanActuallyDisableDropping`（C91 新增）、`ExoFrameSchedulingRendererSettingsTest`、`ExoFrameSchedulingExperimentPolicyTest`。
- 与本窗口其它改动的关系：`e79ed9058a`（硬解保留 ffmpeg 回退 = `EXTENSION_RENDERER_MODE_ON`）实际上是**回到上游口径**（上游 `getRenderMode(HARD)=ON`），因此它不是本仓相对上游的新偏离，嫌疑下调。

### 16.5 三方案比较与推荐

| 方案 | 内容 | 利 | 弊/风险 |
|---|---|---|---|
| 不改（no change） | 保持 15 ms / duration_progress=false | 与上游一致、与 E-SP11 既定结论一致 | 与本机 10-17 版实测（0/s vs 6.2/s）方向相反；若 B 阶段给不出构建差异，则等于无解释 |
| 完全照上游 | 再关掉 dynamic scheduling 与强制异步队列 | 最大化上游一致性 | 主动放弃本仓为省 CPU/4K 设计的主干；手机端功耗/温度可能回退；无证据表明必要 |
| 本仓适配（**推荐起点**） | 按 B 阶段结果**只回退**造成回归的那一个默认值（duration_progress 或阈值），其余保留；并补 §17 的配置观测行 | 单点回滚、可证伪、保留省电主干 | 若真实原因是 §16.3 的解码器/片源路径，则改默认值无效，必须转 H4 |

**推荐**：先完成 B（同集同位置、两组构建），再决定回退哪一项；**不在缺少设备证据时凭文档改默认值**。若 B 显示构建间无差异，则按 H4 交付结论（片源编码器世代 + MTK 解码路径），并把「4K 单轨无降级余地」列为产品侧风险。

### 16.6 类别覆盖与限制（如实记录）

- 已覆盖：上游源码（本地 clone + 锁定 commit 的 raw）、本仓 media3 fork 源码、官方 API 文档、上游 release notes、上游 issue。
- **未覆盖/限制**：`OK影视-5.1.6` 只有 APK 无源码，未反编译（时间盒与「源码已能决定问题」）；`_refs/FongMiTV-fongmi` 本地 clone 为 `a00ebd25…`，早于锁定的 `1a19fee2…`（关键文件已用 raw 拉取锁定版本交叉确认）；未查学术论文（该问题属工程实现细节，论文类别不改变决策）。

---

## 17. 实施记录（2026-10-22）：帧调度配置进入诊断日志 + 阈值单一取值口

**动机**：§14.3 第 6 条 —— 导出日志里没有任何 `perf_*`，`droppedFrames` 报告无法归属到某个开关组合；同时两条渲染器构建路径各自重复写了一遍「开=15 ms / 关=`C.TIME_UNSET`」，任何一次单边改动都会让「设置页显示的」与「实际生效的」分叉。

| # | 改动 | 文件 | 说明 |
|---|---|---|---|
| 1 | 新增单一取值口 `effectiveLateDropThresholdUs()` | `ExoUtil.java` | `isLateDropInputEnabled()` → `ENHANCED_LATE_THRESHOLD_TO_DROP_INPUT_US`，否则 `C.TIME_UNSET`；`DefaultRenderersFactory` 路径与 `ExoRuntimeAwareVideoRenderer` 路径**都只读它**，消除双写漂移 |
| 2 | 新增一行帧节奏配置日志 | `ExoFrameSchedulingRendererSettings.describe()`、`ExoPlaybackDiagnostics.logFrameScheduling()`、`ExoUtil.buildRenderersFactory()` | 每次建播放器输出 `exo-frame: renderer dynamicScheduling=… codecQueue=… decision=… decisionUnit=… durationToProgress=… earlySchedulingUs=… lateDropInputUs=…(或 unset) metricScope=settings-applied-only; not a smoothness measurement` |
| 3 | 契约锁更新/新增 | `PlaybackPerformanceSettingSourceTest` | 原 `lateDropThresholdSwitchCanActuallyDisableDropping` 改为断言两条路径都读共享取值口且渲染器**不再**自带 `isLateDropInputEnabled()`；新增 `frameSchedulingConfigurationReachesTheDiagnosticLog` 断言日志链路字段齐全、关闭态渲染为 `unset` |

**行为影响**：**无播放行为变化**。取的值与改动前逐个相同（开=15 000 µs，关=`C.TIME_UNSET`）；新增的只有一行日志与一个静态取值口。

**验证（本会话实测）**
- `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` → **BUILD SUCCESSFUL**
- `:app:compileMobileArm64_v8aDebugJavaWithJavac` → **BUILD SUCCESSFUL**
- `:app:testLeanbackArm64_v8aDebugUnitTest --tests '*PlaybackPerformanceSettingSourceTest*' --tests '*ExoFrameScheduling*'` → **30 例 0 失败**（含新增 1 例）
- `:app:testLeanbackArm64_v8aDebugUnitTest --tests '*tv.player.exo.*'` → **617 例 / 1 skipped / 0 失败**（与该包既有基线一致）

**未验证（如实记录）**：新日志行**未在真机/模拟器上读出过**——`exo-frame` 的 `SpiderDebug.isEnabled()` 门控与导出日志通道尚未在设备端确认；电视盒（X7 Ultra）本会话未连接。这条需要在下一次装机时用 `grep exo-frame` 确认。

**下一步（唯一动作）**：§15 的设备分组测试（同文件同位置），并在该轮日志里确认 `exo-frame:` 行出现、且 `lateDropInputUs` 与设置页一致。

---

## 18. 实施记录（2026-10-22）：补齐掉帧归因缺的两类证据

**动机**：§17 只解决了「配置未知」。真正卡住归因的是两处**测量缺失**——①「输入丢帧阈值」丢在解码前的帧**根本不计**；②每帧迟到分布只在 A/B 实验投放的设备上采集，普通设备上永远拿不到，导致「跳帧」与「顿住」在日志上不可区分（[androidx/media#3347](https://github.com/androidx/media/issues/3347) 的定案正是靠这个量测）。

| # | 改动 | 文件 | 作用 |
|---|---|---|---|
| 1 | 已有 `video.output.summary` 行补三个计数器：`droppedInputBuffers` / `skippedInputBuffers` / `droppedToKeyframe`，并把 `metricScope` 写明「droppedInputBuffers 是迟到阈值造成的解码前丢弃」 | `ExoDiagnosticCollector.counters()` | **零新增日志行**（字段挂在既有 5 s 采样上）；阈值开关是否真在丢帧从此可见 |
| 2 | 迟到分布采集门槛从「实验投放」放宽到「实验投放**或**诊断日志开启」，并新增节流输出 `exo-frame-timing`（30 s 一行） | `PlaybackAnalyticsListener.onVideoFrameProcessingOffset()` + 新增 `logFrameTimingIfDue()` | 能回答「帧晚了多少毫秒」；离线包 `SpiderDebug` 关闭时行为与原来完全一致（不采集、不输出） |
| 3 | 遥测行给 `renderedFps` 补 `renderedFpsScope=pts-cadence-estimate;not-throughput` | `PlaybackTelemetryMapper.runtimeLogSummary()` | 明确 `58.824` 是 PTS 节拍估计、不是渲染吞吐（§14.2 差点被它误导） |
| 4 | 契约锁：新增 1 个行为测试 + 3 个源码锁 | `ExoFrameTimingMetricsTest`、`PlaybackPerformanceSettingSourceTest` | ①②③ 任一项被改回去，单测直接红 |

**新增日志样例**

```
exo-frame-timing: trace=p-… frames=3120 avgOffsetMs=-3 lateBatches=41 codecErrors=0
  offsetBucketsUs=210/96/1180/1310/240/60/24
  metricScope=buckets=<-20ms/-20..0/0..20/20..50/50..75/75..100/>=100ms;
  positive=early, negative=position-passed-the-frame; renderer-side only
```

`offsetBucketsUs` 七个桶直接对应「晚了多少」：前两桶（<-20 ms、-20..0）就是迟到帧，正是 #3347 里 30–130 ms 迟到那类现象的观测口径。

**行为影响**：**离线包无变化**（`SpiderDebug.isEnabled()` 默认关，采集与输出都被门控）；诊断开时每帧多几次整数累加、每 30 s 一行日志。不改任何播放决策。

### 18.1 开销核对（2026-10-22，用户提问"会不会带来性能问题"）

先把调用频率量清楚，不靠推断：

| 观测点 | 实际触发 | 依据 |
|---|---|---|
| `onVideoFrameProcessingOffset` | **每次渲染器停止 1 次**（不是每帧、也不是每批） | fork 源码 `MediaCodecVideoRenderer.java:1187` 是 `maybeNotifyVideoFrameProcessingOffset()` 的**唯一**调用点（在 `onStopped()` 内）；`:2402-2403` 只做累加 |
| `SpiderDebug.isEnabled()` | 一次 **volatile 静态读** | `DebugLogStore.java:25` `return enabled;`，不查 SharedPreferences |
| `counters()`（§17/§18 的计数行） | 既有 **5 秒 tick** + 停止时 | `ExoDiagnosticCollector.sample()` 末尾 `postDelayed(tick, 5000)` |
| `exo-frame-timing` | 30 秒节流；触发源是 **5 秒 tick**（不是帧回调） | `PlaybackAnalyticsListener.logFrameTimingIfDue()` 由 `ExoDiagnosticCollector` 的 tick 调用 |

| 增量 | 量级 |
|---|---|
| 计数器三字段 | 约 +90 B / 5 s ⇒ **+130 KB/h** |
| `exo-frame-timing` | 约 +300 B / 30 s ⇒ **+36 KB/h** |
| `exo-frame`（§17） | 每次建播放器 1 行，可忽略 |
| 合计 | **≈ +165 KB/h**；对照实测导出量 3.4 MB / 约 15 min ≈ 13 MB/h ⇒ **+1.3%**；磁盘窗口上限 40 MB（`diag.health` `diskLimitBytes`）⇒ 留存时长基本不变 |

**更正（本会话已修）**：§18 表格第 2 行原写"每渲染批回调"，实测为**仅 `onStopped()` 交付**。这带来两个后果并已处理：①注释与测试锁改成正确表述；②**只在停止时交付会让"播放中导出日志"没有迟到数据**，因此把输出挂到既有 5 秒 tick（仍 30 s 节流），不新增定时器/线程。

**仍未测量**：电视盒上诊断开启时由入桶带来的实际掉帧影响（理论上可忽略：每 5 秒几次整数累加 + 每 30 秒一次字符串拼接，且都不在渲染线程的每帧路径上）。真机确认方式：同片源对比"诊断开 / 关"两轮的 `dropped` 速率。

**验证（本会话实测，含 18.1 修正后）**
- `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` → **BUILD SUCCESSFUL**
- `:app:compileMobileArm64_v8aDebugJavaWithJavac` → **BUILD SUCCESSFUL**
- `:app:testLeanbackArm64_v8aDebugUnitTest --tests '*PlaybackPerformanceSettingSourceTest*' --tests '*ExoFrameTimingMetricsTest*' --tests '*tv.player.exo.*'` → **631 例 / 1 skipped / 0 失败**（含 18.1 修正后的 tick 接线锁）
- （§18 首轮）`:app:testLeanbackArm64_v8aDebugUnitTest --tests '*tv.player.*'` → **1511 例 / 2 skipped / 0 失败**（208 个测试类）

**未验证（如实记录）**：`exo-frame-timing` 与三个新计数器的**设备端输出尚未读到**（X7 Ultra 未连接）；`SpiderDebug` 开启时每帧入桶的实际开销也未测量，理论上可忽略，但需要在电视盒上确认掉帧率没有因此上升到可感知。

**下一步（唯一动作）**：§15 的设备分组测试。读取三样东西即可完成归因：`exo-frame`（生效配置）、`exo-frame-timing`（每帧迟到分布）、以及 `video.output.summary` 里的 `droppedInputBuffers` 增量。

---

## 19. 复评复盘（2026-10-22，C97）：§17/§18 的三个新观测字段未进白名单，整条事件被丢弃

**结论先行**：§18 表格第 1 行新增的 `droppedInputBuffers` / `skippedInputBuffers` / `droppedToKeyframe` **从未真正出现在导出日志里**。它们不在 `DiagnosticEvent.FIELDS` 白名单内，`DiagnosticEvent.put()` 直接抛 `IllegalArgumentException`，而 `PlaybackDiagnosticCollector.emit()` 的 `catch (RuntimeException ignored) { DebugLogStore.collectorFailure(); }` 会把异常吞掉 ⇒ **整条 `video.output.summary` 不导出**（连原有的 `inputBuffers`/`submittedBuffers`/`droppedBuffers` 一起消失），并每 5 秒累计一次 collectorFailure。因此 §18 的「零新增日志行」在当时实际等价于「整行消失」。

**证据链**

| # | 事实 | 位置 |
| --- | --- | --- |
| 1 | 未登记字段即抛异常 | `DiagnosticEvent.put()`：`if (!FIELDS.contains(name) …) throw new IllegalArgumentException("Unknown diagnostic field/status")` |
| 2 | 异常被吞，整条事件丢失 | `PlaybackDiagnosticCollector.emit()` 的 `try { facts.accept(event); DebugLogStore.event(event); } catch (RuntimeException ignored) { collectorFailure(); }` |
| 3 | 机械比对：57 个 `observed` 字面量中恰好这 3 个不在 303 个白名单字段内（其余 54 个全中） | `ExoDiagnosticCollector.java` × `DiagnosticEvent.FIELDS` |
| 4 | 触发条件 = 诊断开启，即该采集器唯一的运行条件 | `PlaybackDiagnosticCollector.enabled() == DebugLogStore.isEnabled()` |

**为什么原验证没抓到**：§17/§18 的 5 个新契约测试全是「读源码断言字符串」，没有一个真正 `new DiagnosticEvent(...).observed(新字段, …)`；`:app:test*` 只编译 `catvod` 主源集，不执行事件构造，所以编译/单测全绿与「事件被丢弃」可以同时成立。

**修复（本轮已落地并验证）**

| # | 改动 | 文件 |
| --- | --- | --- |
| 1 | 白名单追加 `droppedInputBuffers` / `skippedInputBuffers` / `droppedToKeyframe`（放在既有 `droppedBuffers`/`skippedBuffers` 之后，纯增量、不升 `schemaVersion`） | `catvod/.../diagnostics/DiagnosticEvent.java` |
| 2 | 新增跨模块契约锁：扫描 `ExoDiagnosticCollector` 全部 `observed("…")` 字面量，逐个用真实 `DiagnosticEvent` 构造验证白名单接受；断言三个输入侧字段仍在采集端（防空转）；自证未登记字段必抛异常 | `app/src/test/.../exo/DiagnosticEventFieldAllowlistTest.java` |

**验证**：`:app:compileLeanback…JavaWithJavac` + `:app:compileMobile…JavaWithJavac` BUILD SUCCESSFUL；定向单测 **633 例 / 1 skipped / 0 失败**（含新增 2 例）；全量 leanback 单测 8 例失败与合并前基线逐条相同（均为既有平台/陈旧契约，与本改动无交集）。详见 `docs/C97-beta-merge-review-dev2-20261022.md` §4、§5。

**同类缺陷（已报告，未在本轮修）**：`ExoDiagnosticCodecAdapter` 的 `video.candidates` 事件用了同样未登记的 `snapshotId`/`queryCount`，同样会被整条丢弃（`ExoDiagnosticCodecAdapter.java:116-137`）；该文件未被本轮任一分支改动，属既有缺陷。

**仍未验证（不变）**：`exo-frame` / `exo-frame-timing` / 三个新计数器在设备端的**实际读数**仍未采集（X7 Ultra 未连接）。修复保证的是「事件会被导出」，不是「数值已正确」；§15 的设备分组测试仍是下一步唯一动作。
