# E-SP9：Exo 硬解模式无硬件解码器时的处理策略

状态：合同已由 `C65` 修订（2026-10-12，Asia/Shanghai），口径已由 `C66` 细化（2026-10-13，Asia/Shanghai；见 `docs/C66-beta-merge-review-dev3-20261013.md`）。下方 2026-09-20 的回滚记录作为历史保留，不再是当前合同。

## 修订后的合同（2026-10-12 `C65`，2026-10-13 `C66` 细化）

用户在 2026-10-12 明确批准合并 `webhtv/webhtv` 分支 `Silent1566`（`590942503addf2db6b921875432f642221eb8f1f`）的 3 个提交，并同步修订本任务此前记录的「绝对禁止自动切软解」合同。修订后的口径：

- 视频解码模式仍以人工切换为准；自动回落是**唯一且收窄的例外**。
- 例外只覆盖两种**经证实**的状态：`MediaCodecSelector.DEFAULT` 列出该视频 mime 且**全部为软件解码器**；或整组视频轨没有任何可解码轨道。平台不声明该 mime（空列表，例如非原生 Dolby Vision 设备上的 `video/dolby-vision`，由 `ExoDv5GpuRenderer` 与 Dolby Vision HDR10 回退渲染器改写为 `video/hevc` 接管）**不算证据**，不改变解码模式。
- 「硬件解码器」的判据是 `hardwareAccelerated && !softwareOnly`（与 `ExoAudioCodecSelector.priority` 一致）：平台允许解码器同时上报 `isHardwareAccelerated() == true` 与 `isSoftwareOnly() == true`，只看前者会把软解误判成硬解通路。
- 「经证实」只对**在播的那条视频轨**成立：容器可以带多条视频轨，未在播且不可解码的分组不是证据。只有在没有任何选中轨、且整组视频轨都没有 `FORMAT_HANDLED` 轨道时才走第二条。
- 触发前提是 `PlaybackPerformanceSetting` 的「解码器兜底」开启（默认开），每次播放最多自动回落一次，并写入 `switch-decode-auto` 播放轨迹。
- 该开关同时约束 `ExoUtil.getVideoCodecSelector` 的平台软解兜底：关闭时硬解候选保持只有硬件，视频轨如实报不支持并由 OSD 结论行引导人工切软解，**不允许**软解顶着「硬解」标签出帧。
- 仅适用于 Exo 硬解。MPV 硬解禁止自动软解、音频独立回退、以及 `CompatFfmpegVideoRendererSourceTest` 的「硬解不注册 FFmpeg 视频回退」断言均保持不变。
- 与 2026-09-20 被回滚的 `4a9f0fd14d` 的差别：当时是硬解模式**无条件注册** `CompatFfmpegVideoRenderer(systemDecoderFallbackOnly=true)` 由 FFmpeg 直接接管出帧；现在是 `ExoUtil.getVideoCodecSelector` 在 MediaCodec 层保留平台软解兜底，再由 `PlayerManager.needsSoftDecode` 判定无硬件通路后**显式把整次播放切到软解模式**，使 OSD 与诊断面板显示的模式与实际一致，而不是让 FFmpeg 在「硬解」标签下出帧。

实现提交（合并提交见 `docs/C65-player-hwdec-auto-soft-fallback-merge-20261012.md`）：

- `edb1cf8eaa92e9768dff78e9c6944f8d969f06a2`：`getVideoCodecSelector` 不再丢弃平台软解；`PlayerManager.maybeFallbackToSoftDecode`；OSD「视频轨无可用解码器」结论行。
- `bd24f9d53df8042775c7fa4255234ab09b08a394`：`needsSoftDecode` + `ExoUtil.hasHardwareVideoDecoder`。
- `590942503addf2db6b921875432f642221eb8f1f`：空解码器列表按「未证实」处理，修正 Dolby Vision 误判。

## 历史记录：2026-09-20 回滚（已由 C65 修订）

状态（历史）：已回滚，保持「视频只能人工手动切换硬解/软解」的既有合同（2026-09-20，Asia/Shanghai）。

### 目标与验收（历史）

- 复评提交 `4a9f0fd14d04aa5f45c8601d9f77334666b435de` 是否违反现有播放合同。
- 结论：该提交让硬解模式在无硬件候选时自动使用 FFmpeg 软解，属于自动软解回退，与 `C-AVS3` 已确认的「视频只能人工手动切换硬解/软解，绝对禁止自动切软解」合同冲突。
- 修复：恢复硬解模式不注册任何 FFmpeg 视频 renderer，音频独立回退策略不变。硬件模式无候选时保持原有行为，用户需手动切换软解。

### 现场证据（历史）

- 用户给出的最后可用发布为 `v5.6.0-beta-202609141608`，目标提交 `7e8fdbd4c2c27c141f89ff2e9a84aaf7f52a6966`；首次失效发布为 `v5.6.0-beta-202609160047`，目标提交 `32640b193f7585353d3120c236b932afa1548165`。
- 两个发布之间的播放器链路差异是 Exo HLS DataSource 包装，入口提交为 `6bc447a14c1334100bd52a3cb5577f4c95a29c4c`；该差异没有修改 `ExoUtil` 的渲染器选择。
- 5561 现场 URL 为 `http://45.192.97.170:8880/play/1.m3u8`，302 后得到咪咕 H.265 主清单；媒体清单为 4 个 `6s` 分片。
- 同一现场日志中，Media3 解析出 `video/hevc`、`hvc1.1.2.L153.80`、`1280x720`，但报告 `supported=NO_UNSUPPORTED_SUBTYPE`；AAC 音频被选中并初始化，链路没有视频解码器初始化、视频尺寸或首帧事件。UI 因此一直停留在 shutter，形成「有声音没画面」。
- `ExoUtil.getVideoRenderMode()` 在硬解时返回 `EXTENSION_RENDERER_MODE_OFF`，`buildVideoRenderers()` 因此直接返回，不注册任何 FFmpeg 视频渲染器。MediaCodec 没有候选解码器时不会产生可归因的播放错误，音频继续推进。

### 方案比较与决策（历史）

- 方案 A（不改 Exo 硬解分支）：硬解模式下无硬件候选时保持无声画面；用户手动切到软解即可播放。与既有合同一致。
- 方案 B（原提交 `4a9f0fd14d04aa5f45c8601d9f77334666b435de`）：硬解模式注册 `CompatFfmpegVideoRenderer(systemDecoderFallbackOnly=true)`，无硬件候选时自动由 FFmpeg 软解接管。画面恢复，但属于自动软解回退，违反 `C-AVS3` 已确认合同。
- 方案 C（提示而不接管）：无硬件候选时向用户提示并引导手动切软解。这是可选的产品改进，需要独立的 UI/状态设计，不在本次修复范围。
- 决策：采用方案 A，回滚方案 B；不引入方案 C。现有 `CompatFfmpegVideoRenderer` 本体的「平台有候选时让轨」合同保留，仅撤销 `ExoUtil` 中的自动注册。
- 2026-10-12 修订说明：方案 C 的「提示」部分已随 `C65` 合并的 OSD 结论行落地；自动接管部分由 `C65` 按修订后的合同重新引入，机制与被回滚的方案 B 不同（见上）。

### 验证计划（历史）

1. 运行 `CompatFfmpegVideoRendererSourceTest`，覆盖硬解模式不注册 FFmpeg 视频回退的源码合同，以及 `CompatFfmpegVideoRenderer` 本体的让轨合同。
2. 运行 `MpvHardwareDecodePolicyTest` 和 `ExoAudioCodecSelectorTest`，确认 MPV 硬解禁止自动软解、Exo 音频独立回退未受影响。
3. 编译 Mobile ARM64 Debug；如需设备确认，走 `bash scripts/build_arm64_debug_install.sh --serial 192.168.50.3:5561` 覆盖安装，验证硬解模式确实不出现自动软解出帧，手动软解仍可用。

### 实施边界与回滚（历史）

- 只修改 `app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java`、定向源码合同测试和本任务文档/索引。
- 不修改 Media3 AAR、nextlib、FFmpeg、MPV、native lock、输出面或音频路由。
- 回滚：本提交的父提交是 `4a9f0fd14d04aa5f45c8601d9f77334666b435de`；撤销本回滚即可恢复该提交的自动软解行为。

### 实施记录（历史）

- 2026-09-20：确认 `4a9f0fd14d04aa5f45c8601d9f77334666b435de` 自动软解回退违反 `C-AVS3` 既有合同；开启 `E-SP9-manual-decode-contract` guard 进行窄回滚。
- 回滚实现：移除硬解模式下的 `CompatFfmpegVideoRenderer` 注册和对应构造帮助方法，恢复 `if (videoRenderMode == EXTENSION_RENDERER_MODE_OFF) return;`。
- 定向测试更新：`CompatFfmpegVideoRendererSourceTest` 现在断言硬解模式不注册 FFmpeg 视频回退，同时保留 `CompatFfmpegVideoRenderer` 本身「平台有候选时让轨」的单元合同。
- 回滚原因：设备的「有声音无画面」不是原提交可绕过的自动软解场景；正确操作是用户手动切换软解。若需要改善硬解无候选的提示文案，应作为独立产品需求评估，不在本任务中改变解码选择。
- 回滚锚点：本提交的父提交 `4a9f0fd14d04aa5f45c8601d9f77334666b435de`。撤销本回滚即可恢复该提交行为。
- 2026-10-12：`C65` 按用户明确批准修订本合同；上述回滚提交与锚点保持原样，仅合同口径更新。

## 2026-11-04 追加：硬解 + 兜底开时改由让轨渲染器接管（`E-SP9-ffmpeg-yield-to-platform`）

### 现场证据

- 同一网络环境、同一批短剧源，`红果.py` 的 1080p 及以下分辨率几乎全部卡顿；影视＋、biecuile、avbox 同片流畅。属播放器侧差异，不是网络问题。
- 用户实测：设置 → 播放 → 播放性能 → 关闭「解码器兜底」即流畅，其余项未动。
- 另一例：Exo 兜底开启时画面卡、声音正常；日志显示卡顿时走 FFmpeg 软解；把 PY 里的 HEVC 等级从 L186 改成 L150 后，保留兜底也能用华为硬解流畅播放。
- 结论：兜底开启时 FFmpeg 视频渲染器抢走了本可由 MediaCodec 硬解的视频轨。

### 根因

`ExoUtil.buildVideoRenderers` 在硬解 + 「解码器兜底」开（`EXTENSION_RENDERER_MODE_ON`）时追加的是
`FfmpegVideoRenderer`。该类 `supportsFormat` 对任何它能解码的 mime 无条件返回 `FORMAT_HANDLED`，
按 Media3 的渲染器打分顺序（`FORMAT_HANDLED` > `FORMAT_EXCEEDS_CAPABILITIES`）压过 MediaCodec 渲染器对
`hvc1.*.L186` 这类超出设备声明等级的 HEVC 给出的 `FORMAT_EXCEEDS_CAPABILITIES`，于是整条视频轨被交给
FFmpeg 软解：声音正常、画面卡顿。降低声明等级（PY 改 L150）之所以有效，正是因为它让 MediaCodec 不再
报 EXCEEDS，从而重新赢回打分。

### 方案

- 只改 `EXTENSION_RENDERER_MODE_ON` 这一个分支：改用仓库内已有的 `CompatFfmpegVideoRenderer(systemDecoderFallbackOnly=true, platformDecoderSelector=videoCodecSelector)`。
  它「平台对该 mime 有解码器就让轨」，且刻意不做 Format 级判定，因此 L186 场景下 FFmpeg 退场、
  MediaCodec 硬解接管；平台根本没有该编码解码器（AVS2/AVS3 等）时 FFmpeg 仍然接手，不会重现
  「整轨被丢弃、只有声音」。
- 软解（`EXTENSION_RENDERER_MODE_PREFER`）分支保持 `FfmpegVideoRenderer` 不变；MPV 与音频回退不变。
- 安全网：`PlayerManager.maybeFallbackToSoftDecode` 仍在，若让轨后视频轨确实无可硬解通路，会按修订后的
  `C65` 合同显式切软解并写入 `switch-decode-auto` 轨迹。

### 与 2026-09-20 回滚的区别

那次是从「硬解不注册任何 FFmpeg」改成「无条件由 FFmpeg 接管出帧」，与人工切换合同冲突；
本次不改变解码模式判定，只把**最后一级视频回退**从「抢轨」换成「让轨」，解码模式仍由 MediaCodec 与
`C65` 的收窄例外决定。

### 验证

- `CompatFfmpegVideoRendererSourceTest`（3 项，含新增 `hardwareModeWithFallbackAppendsTheYieldingRenderer`）通过；
  `ExoVideoHardwareDecodeTest`、`ExoAudioCodecSelectorTest` 通过，0 失败。
- 回滚锚点：`bb8fceda8ddebc829389307368d18c9876c9665d`。

### 断言口径变更（2026-11-04）

- 测试方法 `hardwareModeKeepsFfmpegAsTheLastVideoRenderer` 改名为 `hardwareModeWithFallbackAppendsTheYieldingRenderer`，
  断言从「硬解不注册任何 FFmpeg 视频回退（`assertFalse(exoUtil.contains("new CompatFfmpegVideoRenderer("))`）」
  改为「`MODE_ON` 分支追加 `new CompatFfmpegVideoRenderer(...)` 且带上与 MediaCodec 渲染器同一个
  `videoCodecSelector`，`MODE_PREFER` 分支保持 `FfmpegVideoRenderer`」。
- 本条同步修订 [C-AVS3-video-decoding.md](C-AVS3-video-decoding.md) 顶部解码合同里引用该断言的表述：
  硬解不再是无条件不注册任何 FFmpeg 视频回退，而是「兜底开时追加会让轨的 Compat 视频回退」；
  「不让轨给软件解码器」由 `CompatFfmpegVideoRenderer.platformHandlesMimeType` 只问 mime、不问 Format 保证。
- 复评记录：定向测试 `CompatFfmpegVideoRendererSourceTest` 3 + `ExoVideoHardwareDecodeTest` 10 +
  `ExoAudioCodecSelectorTest` 4 = 17 项，0 失败，0 错误（`:app:testMobileArm64_v8aDebugUnitTest`）。
