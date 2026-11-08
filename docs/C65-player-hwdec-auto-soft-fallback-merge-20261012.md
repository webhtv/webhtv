# C65 · 合并 webhtv/webhtv 分支 Silent1566 的硬解自动软解回落并修订解码合同

状态：机制已由 `C66`（2026-10-13）复评并修订，见 `docs/C66-beta-merge-review-dev3-20261013.md` §3。本文件保留当时的合并与决策记录。

- 任务 ID：`C65-player-hwdec-auto-soft-fallback-merge-20261012`
- 分支：`dev3`；合并前 tip = `37bca767373804c381ffbe3e8f565ebf33b4b281`
- 合并对象：`webhtv/webhtv` 分支 `Silent1566` = `590942503addf2db6b921875432f642221eb8f1f`（同一提交也是该仓库 `refs/pull/148/head`）
- 来源：`https://github.com/webhtv/webhtv.git`，以 `git fetch <url> Silent1566` 取得，未新增 remote
- 访问日期：2026-10-12（Asia/Shanghai）

## 1. 合并范围

`git merge --no-ff --no-commit 590942503addf2db6b921875432f642221eb8f1f`。

merge-base = `814935ceea6122984607a97f7c33513e5f4f6889`（dev3 祖先，2026-10-09）。该分支相对 dev3 只多 3 个提交，全部是 Exo 硬解回落：

| 提交 | 说明 |
| --- | --- |
| `edb1cf8eaa92e9768dff78e9c6944f8d969f06a2` | `ExoUtil.getVideoCodecSelector` 在硬解下保留平台软解作最后兜底（避免整条视频轨被 ExoPlayer 静默丢弃）；`PlayerManager` 新增 `maybeFallbackToSoftDecode`；OSD 新增「视频轨无可用解码器」结论行 |
| `bd24f9d53df8042775c7fa4255234ab09b08a394` | 检测扩展为 `needsSoftDecode`：硬解下该编码没有硬件解码器也触发一次自动切软解；新增 `ExoUtil.hasHardwareVideoDecoder` |
| `590942503addf2db6b921875432f642221eb8f1f` | 修正误判：`video/dolby-vision` 等由 App 自有渲染器改写 mime 的轨道，平台查不到解码器属「未证实」，不再触发切换；同步修正 `ExoUtil` 注释 |

带入路径 3 个，全部是 Java 源文件：`PlayerManager.java`、`exo/ExoUtil.java`、`ui/custom/PlayerOsdController.java`。

## 2. 冲突与解决

`git merge-tree --write-tree` 预演与实际合并一致：只有 2 个文件冲突，共 3 处，且都不涉及新逻辑本身。

| 位置 | HEAD（dev3） | 分支（Silent1566） | 解决 |
| --- | --- | --- | --- |
| `ExoUtil` 硬解渲染器注释 | beta 已简化为「hardware mode must never register a software fallback」 | 说明扩展渲染器禁止 FFmpeg 视频回退、`getVideoCodecSelector` 是唯一的 MediaCodec 级例外 | 采用分支版本：信息更完整，且与合并后行为一致（`if (videoRenderMode == EXTENSION_RENDERER_MODE_OFF) return;` 仍在，未注册 `CompatFfmpegVideoRenderer`） |
| `PlayerOsdController` 诊断主体 | 新增 `TextUtils.isEmpty(startup) ? "" : row("起播", startup)` 行 | `getDiagnosis` 调用增加 `videoTrack` 参数 | 两者都保留 |
| `PlayerOsdController` `getDiagnosis` 之前 | 新增 `getStartupText` 方法与 javadoc | 修改 `getDiagnosis` 签名（增加 `VideoTrackState`） | 两者都保留 |

`PlayerManager.java` 自动合并，无冲突。

## 3. 逻辑一致性校验

合并后对 3 个文件逐一执行 `git diff 590942503addf2db6b921875432f642221eb8f1f -- <file>`，确认新增逻辑与源提交**逐字节一致**：`+/-` 行中没有命中 `needsSoftDecode`、`hasHardwareVideoDecoder`、`softDecodeFallbackAttempted`、`maybeFallbackToSoftDecode`、`fallbackToSoftDecode`、`firstDecodableVideoFormat`、`infos.isEmpty()`、`hardwareInfos.isEmpty()`、`视频轨无可用解码器` 中的任何一条。

差异全部来自 dev3 自 merge-base 以来的独立改动（`PlayerManager` 1220 行、`ExoUtil` 131 行、`PlayerOsdController` 186 行）。因此作者在 NX627J 上的设备验证结论可按同一逻辑沿用，剩余风险只在 dev3 侧新代码的交互面。

交互面复核结论：

- dev3 既有的 `retryHardDecodeSwitch` 与 `fallbackDecode(PlaybackException)` 都是**错误驱动**路径，需要 `PlaybackException`；新代码处理的是**静默丢轨**路径，两者不重复、不互相吞掉。
- 新 `fallbackToSoftDecode()` 的调用序列与 dev3 现有 `toggleDecode()` 的硬解→软解分支一致（`hardDecodeSwitchRetryArmed = false` → `beginPlaybackTrace` → `engine.setDecode(SOFT)` → `prepareFrameSchedulingForNextPlayback()` → `rebuildPlayer(false)` → `setMediaItem()`）。
- 仅对 Exo 生效（`!isExo()` 直接返回），MPV 与音频策略不受影响。

## 4. 契约修订（本任务必须同时完成）

合并引入「硬解无硬件解码器时自动切软解」，与 dev3 原有两条记录直接冲突：

- `docs/C-AVS3-video-decoding.md` 记录的用户合同：「视频只能人工手动切换硬解/软解，绝对禁止自动切软解；音频允许自动回退」。
- `0616a992f6b1378891ca765c693d6970dc7a6622 revert(exo): keep video decode mode manual`（guard `E-SP9-manual-decode-contract`）及其文档 `docs/E-SP9-exo-unsupported-video-soft-fallback.md`，该回滚把「提示而不接管」列为推迟的独立产品需求。

用户在 2026-10-12 本轮明确选择「照单合并，并同步修订契约文档」，因此本任务同时完成：

- 把 `E-SP9` 文档改写为「已由 C65 修订」，保留原回滚的历史记录；
- 在评估索引更新 `E-SP9` 行并新增 `C65` 行；
- 在 `C-AVS3` 的 Recovery anchor 顶部增加一条指向 C65 的日期说明，不改写其历史条目。

**修订后的合同**：视频解码模式仍以人工切换为准。唯一例外是 Exo 硬解下**经证实**没有该编码的硬件解码器（`MediaCodecSelector.DEFAULT` 列出该 mime 且全部为软件解码器），或整组视频轨没有任何可解码轨道时，在 `PlaybackPerformanceSetting` 的「解码器兜底」（默认开）开启的前提下，每次播放只自动回落一次到软解，并写入 `switch-decode-auto` 播放轨迹。平台不声明该 mime（空列表，例如 `video/dolby-vision` 由 App 自有渲染器接管）不算证据，不改变解码模式。MPV 硬解、音频回退策略与 `CompatFfmpegVideoRendererSourceTest` 的「硬解不注册 FFmpeg 视频回退」断言均不变。

## 5. 验证

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| Leanback ARM64 Java 编译 | `.\gradlew.bat :app:compileLeanbackArm64_v8aDebugJavaWithJavac --console=plain` | `BUILD SUCCESSFUL in 5m 57s`，exit 0 |
| 解码模式定向测试 | `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests ...CompatFfmpegVideoRendererSourceTest --tests *MpvHardwareDecodePolicyTest --tests *ExoAudioCodecSelectorTest` | `BUILD SUCCESSFUL in 1m 21s`，exit 0；9 tests / 0 failures / 0 errors |

定向测试明细（`app/build/test-results/testLeanbackArm64_v8aDebugUnitTest`）：

- `CompatFfmpegVideoRendererSourceTest` 3/3：`hardwareOnlyMode_neverRegistersFfmpegFallback`、`platformOwnershipCheck_asksOnlyAboutMimeTypeNotExactFormat`、`platformOwnershipCheck_usesTheInjectedSelectorNotTheDefaultOne`。
- `MpvHardwareDecodePolicyTest` 2/2：`hardwareModeDisablesAutomaticSoftwareFallback`、`explicitSoftwareModeAllowsSoftwareDecoder`。
- `ExoAudioCodecSelectorTest` 4/4：`audioDecoders_areStableAndHardwareFirst`、`audioQuery_isCachedPerSecurityAndTunnelingKey`、`nonAudioQuery_isDelegatedWithoutSortingOrCaching`、`alacDecoders_areRoutedToFfmpeg`。

即：本次合并后，Exo 硬解模式**仍然不注册 FFmpeg 视频回退**（改的是 MediaCodec 层的平台软解兜底），MPV 硬解禁止自动软解、音频回退策略均未被破坏。

**未验证（明确保留）**：本合并树未做设备实测。作者的设备证据（NX627J `127.0.0.1:5563`，4K HEVC 硬解起播后自动切软解，`decode=software`、`decoder=OMX.google.hevc.decoder`、OSD 由硬解变软解）来自源提交 `edb1cf8eaa` / `bd24f9d53d` 所在树；本节只证明该逻辑在 dev3 上逐字节保留且编译、契约测试通过。若需复现现场，需要原播放源 URL 与一台对该编码无硬件解码器的设备。

## 6. 回滚

- 代码回滚锚点：合并前 `dev3` tip `37bca767373804c381ffbe3e8f565ebf33b4b281`；`git reset --hard` 或 revert 合并提交即可恢复。
- 契约回滚：撤销本任务对 `docs/E-SP9-*`、`docs/C-AVS3-video-decoding.md` 与评估索引的改动。
- 不涉及 Media3/nextlib AAR、FFmpeg、MPV native、lock、输出面或音频路由。

## 实施记录

- 2026-10-12：识别分支只多 3 个提交、冲突仅 3 处且不涉及新逻辑；向用户报告与 `E-SP9`/`C-AVS3` 的合同冲突，用户选择「照单合并，并同步修订契约文档」。
- 2026-10-12：guard `C65-player-hwdec-auto-soft-fallback-merge-20261012`（upstream）启动，基线 `37bca76737`，保护面 0 个既有脏文件。
- 2026-10-12：`git merge --no-ff --no-commit 590942503a`，3 处冲突按上表解决；逐文件 `git diff 590942503a -- <file>` 确认新逻辑逐字节一致。
- 2026-10-12：同步修订 `E-SP9`、`C-AVS3` 与评估索引。
- 2026-10-12：Leanback ARM64 Java 编译通过（5m57s）；解码模式定向测试 9/9 通过（1m21s）。设备复测未执行，原因与边界见第 5 节。
- 环境注记：本机 `bash` 指向 WSL 且不可用（`HCS_E_SERVICE_NOT_AVAILABLE`），guard 改用 `G:\Git\bin\bash.exe` 执行；Gradle 用 `gradlew.bat`（`JAVA_HOME` = JDK 21）。

## 后续修订（C66，2026-10-13）

`C66-beta-merge-review-dev3-20261013` 复评本轮 4 个已提交未推送的提交后，修正了 4 处实现并补齐了 `AGENTS.md` §8 要求的 best-practice 依据：

| 编号 | 修订 |
| --- | --- |
| F1 | `ExoUtil.getVideoCodecSelector` 的平台软解兜底原本不受「解码器兜底」开关约束，开关关闭时会让软解顶着硬解标签出帧；现与 `PlayerManager.maybeFallbackToSoftDecode` 共用同一开关 |
| F2 | 硬解判据由 `hardwareAccelerated` 改为 `hardwareAccelerated && !softwareOnly`，与仓库既有 `ExoAudioCodecSelector.priority` 一致；依据是 Media3 自身注释 `codecInfo.isHardwareAccelerated() != codecInfo.isSoftwareOnly() is not necessarily true` |
| F3 | `needsSoftDecode` 由「任一视频分组缺硬件通路即回落」改为「由选中轨决定，或整组视频轨都不可解码」，并复用 `getSelectedFormat`/`hasTrack` |
| F4 | OSD「视频轨无可用解码器」结论行改用 `!isHandled()`（覆盖 `EXCEEDS_CAPABILITIES`）、带上 `supportText`，并对 DRM 不支持单独给建议 |

best-practice 证据表（含上游 Media3 源码行号）、方案比较、回归锁与验证见 `docs/C66-beta-merge-review-dev3-20261013.md` §4–§6。本文件的合并范围、冲突解决、契约修订与回滚锚点均不受影响。
