# C66 · dev3 合并 beta（无新提交）与 C65 硬解自动软解回落复评

- 任务 ID：`C66-beta-merge-review-dev3-20261013`
- 分支：`dev3`；本任务开始前 tip = `3c230bac1e404840dc50fd984127e62b1e6b9cf6`
- 合并对象：`origin/beta` = `37bca767373804c381ffbe3e8f565ebf33b4b281`
- 复评对象：dev3 已提交未推送的 4 个提交（`3c230bac1e` 合并提交 + `edb1cf8eaa`、`bd24f9d53d`、`590942503a`）
- 访问日期：2026-10-13（Asia/Shanghai）

## 1. 合并范围：beta 没有新提交，合并为空操作

`origin/beta` 的 tip `37bca767373804c381ffbe3e8f565ebf33b4b281` **已经是 dev3 的祖先**：

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 远端 beta tip | `git ls-remote origin refs/heads/beta` | `37bca767373804c381ffbe3e8f565ebf33b4b281` |
| 是否祖先 | `git merge-base --is-ancestor origin/beta dev3` | exit 0（是） |
| merge-base | `git merge-base dev3 origin/beta` | `37bca76737…`（等于 beta tip） |
| 合并预演 | `git merge --no-ff --no-commit origin/beta` | `Already up to date.`（exit 0，无 MERGE_HEAD，工作区保持干净） |
| beta 领先 dev3 | `git rev-list --count dev3..origin/beta` | `0` |

即本轮没有需要带入的远端提交，也没有冲突。dev3 相对 beta 只多 4 个提交（`3c230bac1e` 与它的 3 个源提交），它们就是本轮复评对象。

## 2. 零复活 / 零丢失（三层证据）

用户明确要求：远端已移除/回退的提交不得顺带提交上去。

| 层 | 判据 | 结果 |
| --- | --- | --- |
| 1 · 净差异 | `git diff --name-only origin/beta dev3` | 只有 3 个 Java 源 + 3 个既有文档（`C-AVS3`、`E-SP9`、评估索引）被修改，`C65` 文档为新增；没有第 4 个路径被夹带 |
| 2 · 零丢失（blob 级） | beta tip 5048 个文件逐一对 dev3 树比对 blob | 缺失 **0**、逐字节一致 **5042**，差异仅上表 6 个路径 |
| 3 · 零复活（严格） | beta 历史 26 个单亲 revert 提交共 6273 条 (文件,行) 删除记录 ∩ dev3 的 250 条新增记录 | 原始交集 **6**，逐条核查**全部为结构性样板行**（空行、`}`、`try {`），无一条与被回退的语义内容相关 |

第 3 层 6 条命中的逐条结论（判据：把命中行放回它的 hunk 与删除它的 revert 提交里核对）：

| 文件 | 命中行 | 删除它的 revert | 结论 |
| --- | --- | --- | --- |
| `ExoUtil.java` | 空行 / `}` | `0616a992f6 revert(exo): keep video decode mode manual` | 样板行；新增上下文是全新的 `hasHardwareVideoDecoder` 方法 |
| `ExoUtil.java` | `try {` | `0616a992f6` | 样板行：该 revert 删的是 `buildHardwareOnlyFfmpegVideoRenderer` 注册块的 `try {`，新增的是 `hasHardwareVideoDecoder` 里查询解码器的 `try {`，位置与语义都不同 |
| `PlayerManager.java` | 空行 / `}` | `64e83ddb19`、`dd19cadc18 Revert "Fix LUT warmup source retry loop"` | 样板行；新增上下文是 `maybeFallbackToSoftDecode` 一族方法 |
| `docs/E-SP9-…md` | 空行 | `0616a992f6` | 样板行 |

`E-SP9` 那次回滚真正要求的「硬解不注册 FFmpeg 视频回退」由 `CompatFfmpegVideoRendererSourceTest.hardwareOnlyMode_neverRegistersFfmpegFallback` 独立守住，本轮复跑仍通过（见第 5 节），因此第 3 层的样板行命中不构成复活。

## 3. 复评：dev3 已提交未推送的 C65 改动

### 第 1 轮：发现 5 项问题

| 编号 | 严重度 | 问题 | 事实链 |
| --- | --- | --- | --- |
| F1 | 阻塞 | 「解码器兜底」关闭时，硬解仍会用平台软解出帧，且不写轨迹，OSD 仍显示硬解 | `ExoUtil.getVideoCodecSelector`（`ExoUtil.java:1202`）在硬解（`videoRenderMode == EXTENSION_RENDERER_MODE_OFF`）下无条件 `hardwareInfos.isEmpty() ? infos : hardwareInfos`，把平台软解塞回候选；`PlayerManager.maybeFallbackToSoftDecode`（`PlayerManager.java:1868`）却在开关关闭时直接 return。于是开关关闭 = 平台软解在「硬解」标签下出帧，正是 `E-SP9` 第 13 行声明要避免的状态；开关自身文案也写明关闭是「只想快速暴露硬件问题」，而这条路径恰好把问题盖住 |
| F2 | 阻塞 | 硬解判据漏 `softwareOnly`，会把「自称硬解的软解」当成硬件通路 | `ExoUtil.java:420`、`1196` 只用 `info.hardwareAccelerated`；仓库既有同义判据 `ExoAudioCodecSelector.priority`（`ExoAudioCodecSelector.java:74`）用的是 `hardwareAccelerated && !softwareOnly`。上游 Media3 自己就警告这两个标志不互补（见第 4 节证据 E1）。命中该解码器时 `hasHardwareVideoDecoder` 返回 true，自动回落不触发，回到「软解在硬解标签下出帧」 |
| F3 | 重要 | 任一视频分组缺硬件通路就把整场切软解，且同 index 上 selected 被靠前的 handled 压过 | `PlayerManager.needsSoftDecode`（原 `1876-1886`）对**每个**视频分组单独判定，遇到第一个无可解码轨的分组就 `return true`；容器可以有多个视频轨分组（Media3 每个媒体轨分组只映射到一个渲染器，未映射分组会以全 `FORMAT_UNSUPPORTED_TYPE` 进入 `Tracks`，见第 4 节证据 E2）。`firstDecodableVideoFormat`（原 `1888-1894`）先看 selected 再看 handled，但外层是按 index 递增，靠前的 handled 会先返回，压过靠后的 selected——与既有聚合约定（`PlayerOsdController.getVideoTrackState` 的 selected>handled>…、`PlayerManager.getSelectedFormat` 只认 selected）不一致 |
| F4 | 重要 | OSD 新结论行漏报 `EXCEEDS_CAPABILITIES`，且对 DRM 不支持给「请切软解」的误导建议 | `PlayerOsdController.java:510` 用 `isUnsupportedSupport(videoTrack.support())` 作判据，而 `isUnsupportedSupport`（`791-793`）只含 `UNSUPPORTED_TYPE/SUBTYPE/DRM`。`getVideoTrackState`（`787`）却把 `exceeds` 排在 `unsupported` 之前，`supportText`（`798`）也写明「超出设备声明能力…不应判定为可硬解」；`needsSoftDecode` 同样把 exceeds 视为触发态。即「视频轨被静默丢弃」的一种状态仍会显示结论「正常」。另外 DRM 不支持时提示「请切软解」，而换解码模式并不能解决加密不支持 |
| F5 | 次要 | 上游合并候选缺 best-practice 章节 | `AGENTS.md` §8 要求 upstream 合并候选在任务文档里留下证据表与三方案比较；`C65` 文档只有实现与验证记录。已在本文第 4 节补齐并回填 `C65` 文档指针 |

补充：`docs/upstream-player-dependency-merge-assessment-2026-08-20.md` 首行 UTF-8 BOM 在 C65 合并里被顺带删除（`EF BB BF` → `#`），属无关格式改动，本轮已还原，使该文件相对 beta 只剩 E-SP9 行更新与 C65 行新增两处。

### 修复

| 编号 | 修改 |
| --- | --- |
| F1 | `ExoUtil.getVideoCodecSelector` 的软件兜底改为 `hardwareInfos.isEmpty() && PlaybackPerformanceSetting.isDecoderFallbackEnabled() ? infos : hardwareInfos`：开关关闭时硬解列表保持「只有硬件」，视频轨如实报 unsupported，由 OSD 结论行引导人工切软解，而不是让软解顶着硬解标签出帧 |
| F2 | 新增唯一判据 `ExoUtil.isHardwareDecoder(MediaCodecInfo)` = `hardwareAccelerated && !softwareOnly`，`hasHardwareVideoDecoder` 与 `getVideoCodecSelector` 共用；两处不再各写一份过滤 |
| F3 | `needsSoftDecode` 改为：先取 `getSelectedFormat(tracks, C.TRACK_TYPE_VIDEO)`，有选中轨就只由它决定；没有选中轨时才用「整组视频轨都没有 `FORMAT_HANDLED` 轨道」这一条。复用既有 `getSelectedFormat`/`hasTrack`，新增 `hasHandledTrack`，删除 `firstDecodableVideoFormat` |
| F4 | 判据改为 `videoTrack.hasTracks() && !videoTrack.selected() && !videoTrack.isHandled()`，覆盖 exceeds；结论文案抽出 `videoDiagnosis`，按 `supportText` 的既有约定带上支持码，并对 `FORMAT_UNSUPPORTED_DRM` 单独给「请更换资源」而不是「请切软解」 |
| F5 | 本文第 4 节；`C65` 文档加指向本文的修订说明 |

### 第 2 轮：修复后复审

- F1：开关开/关两条路径现在都由同一个开关决定。开关开——`getVideoCodecSelector` 保留平台软解 → 该轨被选中且 `hasHardwareVideoDecoder` 为 false → 触发一次自动切软解并写 `switch-decode-auto`；开关关——候选保持只有硬件 → 视频轨 unsupported → `needsSoftDecode` 的「整组无可解码轨」成立但开关直接拦下 → 不切模式，OSD 报「视频轨无可用解码器…请切软解或更换资源」。两条路径都不再出现「软解顶着硬解标签出帧」。
- F2：`hardwareAccelerated && !softwareOnly` 比原判据更严；只有真硬解才阻止回落。设备上若唯一解码器是「自称硬解的软解」，现在会正确回落，而 `getVideoCodecSelector` 在同一判据下把该解码器归入软件集合，两者结论一致（开关开时仍会把它作为最后兜底交给 ExoPlayer，实际选中解码器不变）。
- F3：推演四类输入——(a) 单视频分组、有硬解：选中轨有硬解 → 不回落；(b) 单视频分组、无硬解：选中轨是平台软解 → 回落；(c) 多视频分组、在播轨有硬解、另一组不可解码：由选中轨决定 → 不回落（修复点）；(d) 全部视频分组都不可解码：无选中轨且无 `FORMAT_HANDLED` → 回落。DV 场景不变：`video/dolby-vision` 空列表仍按「未证实」返回 true。
- F4：`!isHandled()` 与 F3 的「整组无可解码轨」口径一致；`selected` 为真或存在 handled 轨时不出结论行，不会误报。
- 无新增阻塞问题。既有合同（MPV 硬解禁止自动软解、音频独立回退、硬解不注册 FFmpeg 视频回退）由第 5 节测试复跑确认未受影响。

## 4. best-practice 依据（补 F5）

本机 `web_search` 端点返回 401（`DEEPSEEK_SEARCH_BASE_URL` 未配置可用端点），因此本轮只使用**仓库内可复现的一手来源**：项目锁定的 Media3 源码 jar、仓库自身的选择器与测试、以及本仓库已有的合同文档。这是本节的证据边界。

| 编号 | 来源（可复现路径 / 版本） | 访问日期 | 证据等级 | 支持的结论 | 对 WebHTV 的适用性 | 局限 |
| --- | --- | --- | --- | --- | --- | --- |
| E1 | `third_party/maven/androidx/media3/media3-exoplayer/1.11.0-alpha01-fongmi/media3-exoplayer-1.11.0-alpha01-fongmi-sources.jar` → `androidx/media3/exoplayer/mediacodec/MediaCodecUtil.java:710-727` | 2026-10-13 | 一手（链接库源码，随仓库分发） | 注释原文：`codecInfo.isHardwareAccelerated() != codecInfo.isSoftwareOnly() is not necessarily true.`；Media3 自己的 `isHardwareAccelerated()` 在 API 29+ 用平台 `isHardwareAccelerated()`，低版本用 `!isSoftwareOnly()` 近似 | 直接适用：`hardwareAccelerated` 单独不足以判定硬解，F2 成立 | 只覆盖解码器标记语义，不涉及本 App 的渲染器组合 |
| E2 | 同上 jar → `androidx/media3/exoplayer/trackselection/TrackSelectionUtil.java:136-181`、`MappingTrackSelector.java:374-394` | 2026-10-13 | 一手 | 每个媒体轨分组只关联**一个**渲染器（`findRenderer`），未映射分组仍会以全部 `FORMAT_UNSUPPORTED_TYPE` 进入 `Tracks`；因此 `Tracks` 里可以同时存在「在播且有硬解」的分组和「不可解码」的分组，逐分组判定会误触发 | 直接适用：F3 成立，且说明该场景可达（不是假想） | 未在真机打印分组清单 |
| E3 | `app/src/main/java/com/fongmi/android/tv/player/exo/ExoAudioCodecSelector.java:66-77` + `app/src/test/.../ExoAudioCodecSelectorTest.java` | 2026-10-13 | 一手（仓库既有实现与其测试） | 同一问题（硬解优先）在本仓库既有口径是 `hardwareAccelerated && !softwareOnly`，并有单测与「命名选择器 + 注入 selector」的范式 | 直接适用：F2 的修法沿用既有口径而不是新造 | 音频侧不涉及本次视频渲染器 |
| E4 | `app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java:672,787-802`；`app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:1043-1052,1074-1078` | 2026-10-13 | 一手（仓库既有实现） | 本仓库对「多个轨道分组」的既有聚合是「跨全部分组选候选」，不是「任一分组说了算」；支持码文案统一走 `supportText` | 直接适用：F3/F4 的修法向既有约定收敛 | 既有实现本身不覆盖 exceeds 的结论行，属本轮新增 |
| E5 | `docs/E-SP9-exo-unsupported-video-soft-fallback.md:9-13,28-29,44-45`；`docs/C-AVS3-video-decoding.md` Recovery anchor | 2026-10-13 | 一手（本项目已批准的合同与两次决策） | 合同只授权「经证实」的两种状态触发，且前提是「解码器兜底」开启；历史上正因为「自动软解接管」被回滚过一次 | 直接适用：F1 是合同与实现的门控不一致 | 合同没有描述「开关关闭时」的实现细节，需按开关自身文案裁决 |

方案比较（针对 F1 的门控问题）：

| 方案 | 内容 | 正确性 | 兼容性/性能 | 维护与可验证性 | 结论 |
| --- | --- | --- | --- | --- | --- |
| no change | 保留 `hardwareInfos.isEmpty() ? infos : hardwareInfos` | 开关关闭时与合同冲突（软解顶着硬解标签出帧，且无轨迹） | 开关关闭时「看起来能播」 | 两个门控点不一致，只能靠注释维系 | 否 |
| 原样上游 | 上游 3 个提交的写法（即 C65 现状） | 同上 | 同上 | 同上 | 否（本轮即修正它） |
| WebHTV 适配（采纳） | 软件兜底与自动切模式**共用** `isDecoderFallbackEnabled()`；硬解判据共用 `isHardwareDecoder`；触发判定按选中轨 + 整组口径 | 两条路径都与合同一致，且轨迹与 OSD 一致 | 开关开启（默认）时行为与原样上游完全相同；只有关闭时才回到「如实报不支持」 | 门控只有一个开关、判据只有一个函数，且新增 10 例回归锁 | **采纳** |

## 5. 回归锁

新增 `app/src/test/java/com/fongmi/android/tv/player/exo/ExoVideoHardwareDecodeTest.java`，10 例：

- 行为 5 例：`hardwareDecoder_isAHardwarePath`、`softwareOnlyDecoderThatClaimsHardware_isNotAHardwarePath`（F2 的锁）、`allSoftwareDecoders_areNotAHardwarePath`、`emptyDecoderList_isNotEvidenceOfSoftwareFallback`、`nullAndNonVideoMime_areNotThisChecksBusiness`。
- 源码守卫 5 例：`failedQuery_keepsTheCurrentMode`（`DecoderQueryException` 无可用构造器，按本仓库既有范式做源码守卫）、`videoSelector_reusesTheSharedHardwarePredicate`（F2）、`platformSoftwareFallback_isGatedByTheDecoderFallbackSwitch`（F1）、`autoFallback_decidesOnTheSelectedVideoTrack`（F3）、`videoVerdict_coversEveryUndecodableSupportCode`（F4）。

## 6. 验证

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| Leanback ARM64 Java 编译 + 定向单测 | `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests *ExoVideoHardwareDecodeTest --tests *CompatFfmpegVideoRendererSourceTest --tests *MpvHardwareDecodePolicyTest --tests *ExoAudioCodecSelectorTest --tests *ExoDolbyVisionFallbackPolicyTest --tests *PlaybackPerformanceSettingSourceTest --console=plain` | `BUILD SUCCESSFUL in 35s`，exit 0；**28 tests / 0 failures / 0 errors** |

定向测试明细（`app/build/test-results/testLeanbackArm64_v8aDebugUnitTest`）：

- `ExoVideoHardwareDecodeTest` 10/10（本轮新增）。
- `CompatFfmpegVideoRendererSourceTest` 3/3：`hardwareOnlyMode_neverRegistersFfmpegFallback`、`platformOwnershipCheck_asksOnlyAboutMimeTypeNotExactFormat`、`platformOwnershipCheck_usesTheInjectedSelectorNotTheDefaultOne`。
- `MpvHardwareDecodePolicyTest` 2/2、`ExoAudioCodecSelectorTest` 4/4、`ExoDolbyVisionFallbackPolicyTest` 2/2、`PlaybackPerformanceSettingSourceTest` 7/7。

即：本轮修改后，硬解仍然不注册 FFmpeg 视频回退，MPV 硬解禁止自动软解、音频独立回退、DV7/DV5 回退开关均未被破坏。

## 7. 残留未知与已知限制

- **未做设备实测**：本轮只证明逻辑、编译与合同测试通过。自动回落的真机现场（无该编码硬件解码器的盒子/电视）需要原播放源与对应设备，未在本轮复现。
- **DRM/加密轨的解码器查询参数未对齐（本轮未修，已证不可达）**：`hasHardwareVideoDecoder` 用 `(secure=false, tunneling=false)` 查询，而加密轨的实际播放查询用 `requiresSecureDecoder=true`。Media3 `MediaCodecUtil.getDecoderInfosInternal`（同一 jar，`463-520`、`612-644`）会按 `key.secure` 过滤，因此「只有 secure-only 硬解」的设备上两者结论可能不同。当前不可达：`MediaSourceFactory.setDrmSessionManagerProvider` 只有声明、无调用点，Exo 播放路径不会产生 `cryptoType != C.CRYPTO_TYPE_NONE` 的格式。若将来接入 DRM，需要把该判据的查询参数与实际播放对齐。
- **`softDecodeFallbackAttempted` 的复位边界**：「每次播放一次」按 `start()`/`parse()` 会话计。`toggleDecode()`/`switchDecode(...)` 是人工切模式，不复位该标志，因此同一会话内人工切回硬解后不会再自动回落。若将来把「人工换线路」也视为新播放，需要同步复位。
- **`fallbackToSoftDecode` 不调用 `beginIjkRuntimeManualOverride()`**（`toggleDecode` 会调用）：自动回落不是人工覆盖，故有意不调用。
- **`PlaybackPerformanceSetting` 在渲染器构建时读取**：开关在会话内切换不会重建已构建的渲染器，与既有 `enableDecoderFallback` 的读取时机一致。
- **本轮未采纳的评审建议（记录取舍）**：`fallbackToSoftDecode` 与 `toggleDecode` 尾部 4 句同形（Duplicated Code）。不抽取公共方法，因为两者在轨迹原因、`hardDecodeSwitchRetryArmed` 取值与 `resetVideoSurface` 上各不相同，抽取需要 3 个参数，且会改动本轮范围之外的 `toggleDecode`；`getDiagnosis` 参数增至 7 个（Data Clumps）同理，属既有增长模式，未在本轮重构。

## 8. 回滚

- 代码回滚锚点：本任务开始前 `dev3` tip `3c230bac1e404840dc50fd984127e62b1e6b9cf6`；`git reset --hard` 或 revert 本任务提交即可恢复 C65 原样实现。
- 文档回滚：撤销本任务对 `docs/C65-*`、`docs/E-SP9-*`、评估索引的改动，并删除 `docs/C66-*`。
- 不涉及 Media3/nextlib AAR、FFmpeg、MPV native、lock、输出面或音频路由。

## 实施记录

- 2026-10-13：确认 `origin/beta` tip 已是 dev3 祖先，`git merge --no-ff --no-commit origin/beta` 返回 `Already up to date.`，本轮无远端新提交、无冲突。
- 2026-10-13：guard `C66-beta-merge-review-dev3-20261013`（upstream）启动，基线 `3c230bac1e`，保护面 0 个既有脏文件。
- 2026-10-13：Standards / Spec 双轴并行复评（各自独立上下文），得到 F1–F5；F1/F3 两轴各自独立命中，F2/F4 由 Standards 轴给出仓库既有约定证据。
- 2026-10-13：从仓库锁定的 Media3 源码 jar 取得 E1/E2 一手证据（`web_search` 端点 401，改用仓库内一手来源）。
- 2026-10-13：完成 F1–F5 修复 + 还原评估索引 BOM；Leanback ARM64 编译与 6 个测试类 28 例全绿（35s）。
- 2026-10-13：零复活/零丢失三层证据完成（缺失 0、逐字节一致 5042、revert 交集 6 条全为样板行）。
- 2026-10-13：交付坐标——修正提交 `434fb60fe88c5958a4aa27a5d9a50b0bdc9b11ac`，恢复 tag `recovery/C66-beta-merge-review-dev3-20261013/20261012163803-434fb60fe88c`，已推送 `origin/dev3`；PR [#442](https://github.com/Silent1566/webhtv/pull/442)（base `beta`，9 个文件，只创建不合并）。
- 环境注记：本机 `bash` 指向 WSL 且不可用，guard 用 `G:\Git\bin\bash.exe` 执行；Gradle 用 `gradlew.bat`（`JAVA_HOME` = JDK 21）。本机环境变量 `GIT_CONFIG_COUNT=2` 缺 `GIT_CONFIG_KEY_*`，所有 git 命令前需清空该变量。
