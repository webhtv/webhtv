# C107 音轨直通启动无进度（播放页卡住/长时间无画面）

## Recovery anchor

- 目标：电视版（leanback）播放新视频时，厂家直通（vendor direct / offloaded compressed）音轨接受了第一段数据后播放头一直停在 0，界面显示 READY/isPlaying 但音视频都不前进；修复后应在约 1 秒量级内自动回退到 PCM 解码，而不是等 Media3 的 10 秒 STUCK_PLAYING_NO_PROGRESS 超时。
- 验收：`app/src/main/java/com/fongmi/android/tv/player/exo/ExoCompressedAudioDirectPolicy.java` 的 `isStartupStalled()` 在 buffer 指标不可读（capacity/size/threshold 全为 0）时仍能以 “已接收数据 + 原始播放头仍为 0” 判定启动停滞；单测与 Robolectric 集成测试覆盖该分支且阈值路径行为不变。
- 状态：已提交 `687ec4af3c`（修复本体），已打注释标签 `recovery/C107/20261027185704-687ec4af3c95`；增量回归测试与本文档改动已提交 `47d7156c5f` 并推送 dev2（PR #497）。第二次远端 beta 合并 `8e9f8a9d3e`（PR #496 / C108）已合入 dev2，定向编译与单测在合并后复跑通过。
- 下一个动作：等待 PR #497 评审合入；合入后再拉取远端确认 beta 无新增提交，如有则重复合并-复评-验证循环。

## 现场证据（G:\下载webhtv-debug-log(1).txt）

- trace `p-bc7m-f` / player-101 / attempt 2（切集后新视频）：
  - `exo-audio-direct` 17:40:07.485 `event=created encoding=10 capacityBytes=0 sizeBytes=0 startThresholdBytes=0 effectiveThresholdBytes=0`
  - 17:40:07.510 `event=first-write ... acceptedBytes=4762`
  - `audio.output.write` 17:40:18.669 `writeCalls=878 acceptedBytes=339760 zeroWrites=0 writeErrors=0`
  - `audio.output.playhead` 17:40:07.502 / 12.503 / 17.509 / 18.671：`headRaw=0`、`playState=3`（PLAYING）全程不变。
  - `av-diag` 17:40:18.651 `error-117` “Player stuck playing with no progress for 10000 ms”。
- 对比 attempt 1（`p-b5al-e`）走的是 `vendor-direct-failed-force-pcm` 黑名单强制 PCM，播放正常。
- 结论：不是网络/主线程问题。日志中主线程 5 秒诊断 tick 最大间隔 5.006 s、capture→enqueue 偏移极差 24.5 ms，无多秒阻塞；加载动画停转是播放管线冻结的表现。
- 位置从 0 重新开始是新选集 `initialPosition=C.LONG_MIN` 的正常语义，不作为缺陷修改。

## 根因

`isStartupStalled()` 的入场条件包含 `startupBuffer.effectiveThresholdBytes() == 0` 与末尾 “重读阈值” 块中同样的判断；`StartupBuffer.effectiveThresholdBytes()` 在 capacity/size/threshold 任一不可读时返回 0。于是当厂家 AudioTrack 不报告缓冲指标时，启动停滞探测被永久禁用，只能等 Media3 的 10 秒 generic 超时，之后走 `requestPcmFallbackForStuckPlayback` 的迟到路径。

## 修改

- 新增 `ZERO_METRIC_STARTUP_STALL_MS = 1_200`：指标不可读时使用更长窗口（阈值路径仍为 `STARTUP_STALL_MS = 800`）。
- `isStartupStalled()` 入场：以 `acceptedBytes > 0` 作为“已接收数据”的最低证据；仅当指标可读时才要求达到阈值。阈值重读块同理：指标一直不可读则以 `routeAtStall = route; return true;` 认定停滞；原先“指标由可读变为 0”的放弃路径保留。
- 恢复路径未改动：`maybeRequestStartupPcmFallback` → `disableVendorDirect(..., "startup-no-progress")` → `ExoStartupAudioRenderer` 抛 `ERROR_CODE_AUDIO_TRACK_WRITE_FAILED` → `ExoPlayerEngine.retryAudioOutputWithPcm()`（`startInternal(position, ...)` 保持位置）。

## 测试

- `ExoCompressedAudioDirectPolicyTest`：
  - 重写原 `unknownThresholdOrRawHeadDoesNotDeclareStartupFailure`（该用例此前固化了缺陷）为 `unreadableStartupMetricsRecoverFromAcceptedPayloadAndPinnedHead`：零指标 + 有接收数据 + 头为 0，1200 ms 后必须判定停滞，且 1199 ms 前不判定；无接收数据时永远不判定。
  - 新增 `unknownRawHeadNeverDeclaresStartupFailure`：`rawHead = C.LENGTH_UNSET`（头不可读）在可读与不可读指标下都不得判定。
  - 阈值路径用例（800 ms 判定、阈值下调、阈值增长重查、路由/能力变化失效、flush/stop/release 证据失效等）保持不变并通过。
- `ExoStartupRecoveryIntegrationTest`（Robolectric sdk=28，真实 ExoPlayer + ProgressiveMediaPeriod）：
  - 新增 `unreadableBufferMetricsStillRecoverFirstSilentDirectOutput` 与 `unreadableBufferMetricsNeverRecoverHealthyDirectOutput`，通过 `startupBuffer -> StartupBuffer.UNKNOWN` 复现厂家行为；仍断言不重开数据源、只准备一次、PCM 恢复不依赖 10 秒看门狗（零指标预算 3 s，可读指标仍为 2 s）。
- 验证命令与结果：
  - `bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests '*ExoCompressedAudioDirectPolicyTest' --tests '*ExoStartupRecoveryIntegrationTest' --no-daemon --console=plain` → BUILD SUCCESSFUL（3m37s）；`ExoCompressedAudioDirectPolicyTest` 71 tests / 0 failures / 1 skipped，`ExoStartupRecoveryIntegrationTest` 5 tests / 0 failures。
  - 增量后复跑（当前工作区，含新增回归测试）：`gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests '*ExoCompressedAudioDirectPolicyTest' --tests '*ExoStartupRecoveryIntegrationTest' --console=plain` → BUILD SUCCESSFUL（45s）；`ExoCompressedAudioDirectPolicyTest` 72 tests / 0 failures / 1 skipped，`ExoStartupRecoveryIntegrationTest` 5 tests / 0 failures。
  - 第二次 beta 合并 `8e9f8a9d3e` 后复跑：`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` → BUILD SUCCESSFUL（5m2s）；`gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests '*ExoCompressedAudioDirectPolicyTest' --tests '*ExoStartupRecoveryIntegrationTest' --console=plain` → BUILD SUCCESSFUL（2m24s），72 tests / 5 tests 全绿；并入的 C108 测试在 mobile 源集，另跑 `:app:testMobileArm64_v8aDebugUnitTest --tests '*VideoActivityLayoutTest' --tests '*TmdbEpisodeGridPolicyTest'` → BUILD SUCCESSFUL（2m54s），157 tests / 9 tests 全绿。

## 不做

- 不改 Media3 停滞检测默认值与 `experimentalEnableStuckPlayingDetection`。
- 不改 AndroidManifest `targetSdk`/`usesCleartextTraffic` 等既有配置项，未新增依赖。
- 不改播放位置提前量、UI 加载动画、`PlaybackDiskBufferStore` 日志节流等相邻实现。

## 回滚

一个实现文件 + 两个测试文件 + 本文档；回滚本提交即可恢复原行为（零指标设备重新退回 10 秒超时后恢复）。
