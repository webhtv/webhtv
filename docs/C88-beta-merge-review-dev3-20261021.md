# C88-beta-merge-review-dev3-20261021

## Recovery anchor

- **目标**：把 `origin/beta@77e2fbefdcd0c2cdd9bcbcb3a1e25b9a6c8ae1fd` 合并进 `dev3`（零复活、零丢失），复评 dev3 已提交未推送的 5 个播放器修复并修复必修项，验证通过后提交、推送、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突、合并树与 `git merge-tree` 预演逐字节一致；B（`beta` 树 − 合并树）= 0；C（revert 删除路径 ∧ 不在 `beta` ∧ 在合并树）= 0。
  2. 5 个未推送播放器提交全部只读复评；必修项已修且每条有可证伪测试或可复核证据。`beta` 带入的 C87 改动已在 `C87-beta-merge-review-dev2-20261020.md` 评审且本次 0 冲突，按任务约定跳过复评（理由记录在 §2）。
  3. 双 flavor arm64 Debug Java 编译通过；定向单测全绿；`scripts/check_ui_tokens.sh` PASS。
  4. 合并提交保留双父（`--no-ff`），经任务守卫 `finish` 落盘并创建 recovery tag。
  5. 推送 `dev3`，创建到 `beta` 的 PR（中文描述，只创建不合并），随后拉取远端最新代码。
- **Lane**：`standard`
- **Scope**：`app/`、`docs/`
- **任务守卫**：`C88-beta-merge-review-dev3-20261021`，base HEAD `8d47a5dd34618af520ba8f01c2d6aaa6a28f78e7`，branch `dev3`，受保护脏路径 0
- **基线**：`dev3` HEAD = `8d47a5dd34`（相对 `origin/dev3` ahead 5，全部未推送）；merge-base `f70fa8c5fd2ebb289f2c161b6502ff5ce9e9d1a2`
- **当前状态**：合并 + 复评 + 修复 + 验证完成；提交/推送/PR 见 §7（交付坐标）
- **下一动作**：无。

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `77e2fbefdcd0c2cdd9bcbcb3a1e25b9a6c8ae1fd`（PR #465 合入后） |
| 本地 | `dev3` = `8d47a5dd34`（5 个未推送播放器修复） |
| merge-base | `f70fa8c5fd2ebb289f2c161b6502ff5ce9e9d1a2` |
| 冲突 | **0**（`git merge --no-ff --no-commit origin/beta` → `Automatic merge went well`；`git diff --name-only --diff-filter=U` 为空） |
| 预演/实合并树 | `c691b9276e713f8b7c7c46386d0746f8a562e9e2`（`git merge-tree` 预演与 `git write-tree` 实际结果**逐字节相同**） |
| `beta` 树 / `dev3` 树 | `858e17e45cc31b40118c624dc2806ac5a6ff7eec` / `8324731f400b0bfc7c7fd49b82a26e76eee14c58` |

`beta` 自 merge-base 带入 7 个提交 / 11 路径（dev2 的 C87 首页导航、`OneKeySyncDialog` 代际守卫、两份文档与索引行）。

## 2. 复评范围与跳过依据

- **复评**：dev3 未推送的 5 个播放器提交（`e79ed9058a`、`48f59db5ae`、`2d403b2a31`、`203bb7d567`、`8d47a5dd34`），共 6 个 Java 路径。
- **跳过**：`beta` 带入的 C87 改动。依据：1）已在 `C87-beta-merge-review-dev2-20261020.md` 完成三轮复评并修复 2 项 medium；2）本次合并 0 冲突且合并树与预演逐字节一致；3）带入路径与 dev3 未推送路径交集为空（`app/src/main/java` 内仅 `OneKeySyncDialog` 与播放器包无交集）。仍对 3 个带入契约测试做了回归执行（§6）。

## 3. 零复活 / 零丢失证据

树对象：合并结果 `c691b9276e`、`beta` `858e17e45c`、`dev3` `8324731f40`。

| 判据 | 含义 | 结果 |
| --- | --- | --- |
| 路径数 | merge / beta / dev3 | 5167 / 5166 / 5164 |
| **A** | 合并树 − `beta` 树 | **1**，仅 `LiveManifestRetryPolicy.java`（dev3 未推送的新文件） |
| **B** | `beta` 树 − 合并树（丢失面） | **0** |
| revert 集合 | 主题行锚定（`revert`/`回退`/`撤销` 开头，`HEAD`+`origin/beta` 全历史去重） | 27 提交 / 44 条删除路径 |
| **C** | revert 删除 ∧ 不在 `beta` 树 ∧ 在合并树（复活面） | **0** |
| **D** | 检测器自检 | 探针 `app/src/main/assets/themes/index.json` 确在 revert 删除集且不在 `beta` 树（有效探针） |
| **E** | 内容级 `git diff`（beta 树 → 合并树） | 恰 **6** 路径，全部为 dev3 未推送播放器改动 |

## 4. 复评发现与修复

| # | 级别 | 位置 | 问题 | 修复 |
| --- | --- | --- | --- | --- |
| 1 | **high** | `PlayerManager.java:9334`（原 `isKernelFallbackEligible`） | 原实现把**整类** `MEDIA_PARSING` 都挡在内核回退之外。`ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` 是内容能力边界（RMVB/AVS3 等 EXO 不支持的容器），换 IJK/MPV 往往可播；原实现把「换核可播」退化成「直接报错」，属明确回归 | 抽出 `PlaybackErrorClassifier.isTransientSourceFailure(stage, errorCode)`：`NETWORK_IO`/`LOCAL_ENDPOINT` 与 **仅** `PARSING_MANIFEST_MALFORMED` 视为临时失败；容器不支持/损坏、manifest 不支持仍允许换核 |
| 2 | medium | `PlayerManager.java:9311` | 临时失败重试对点播同样按直播窗口（6 次、最坏 ~12.8s）执行，死链会让用户等十几秒才看到换源入口 | 直播 6 次、点播 `TRANSIENT_SOURCE_VOD_MAX_RETRY=2`（~1.8s），日志带 `attempt=%d/%d` |
| 3 | medium | `PlayerManager.java:6049` | `transientSourceRetry` 只在 `reset()` 归零，正常换集走 `start()` 会跨条目累积，长会话后新条目拿不到重试预算 | `start()`（新播放条目入口）与 `reset()` 同步归零；重试自身走 `setMediaItem()` 不受影响 |
| 4 | medium | `ExoHlsAdblockDataSource.java:155` | 新增的 `isSegmentContentType` 把 `application/octet-stream` 当分片证据：不少静态服务器未配 m3u8 MIME，真播放列表也返回 octet-stream，这些源会整体失去广告过滤（原实现靠 URL 后缀仍会清洗） | octet-stream 不算分片证据，交回 URL 后缀 + `looksLikePlaylist` 正文明判（TS 分片仍原样透传） |
| 5 | medium | `ExoUtil.java:388` | 硬解无条件改 `EXTENSION_RENDERER_MODE_ON`，关闭「解码器兜底」后 FFmpeg 仍会在硬解标签下静默接管，违背该开关合同（「只想快速暴露硬件问题」）与 `PlayerManager` 软解切换判断 | 仅在 `PlaybackPerformanceSetting.isDecoderFallbackEnabled()` 时硬解用 ON；关闭时保持 OFF（默认开启路径与提交实测行为一致） |
| 6 | medium | `LiveManifestRetryPolicy.java:54` | `getMinimumLoadableRetryCount` 对所有 data type 抬到 ≥5，点播分片/死链无谓延长失败时间 | 只对 `C.DATA_TYPE_MANIFEST` 抬高到 8；媒体分片沿用默认策略 |
| 7 | low | `ExoUtil.java:1200` | 上一提交留下重复注释行，且新注释与「兜底关闭」分支不符 | 删除重复行并按条件行为改写 |
| 8 | low | `ExoPlayerEngine.java:168`、`LiveManifestRetryPolicy.java:66` | 原因链遍历无深度上限，循环 cause（A↔B）可挂死错误回调 | 深度上限 8 / 12 |
| 9 | low | `CompatFfmpegVideoRendererSourceTest.java` | 契约测试名为 `hardwareOnlyMode_neverRegistersFfmpegFallback`，与新行为相反且断言不覆盖命名合同（恒真） | 改为 `hardwareModeKeepsFfmpegAsTheLastVideoRenderer`，断言兜底开关分支、MODEE ON/OFF、FFmpeg 追加与 `CompatFfmpegVideoRenderer` 不回流 |
| 10 | low | `PlayerManager.java:9307` | `retryTransientSourceFailure` 未复用分类器判定，两处判定日后易漂移 | 统一走 `isTransientSourceFailure`，日志保留 `stage`/`errorCode` |

明确不修（记录为已知边界）：`LiveManifestRetryPolicy` 对空 message 的任意 `IOException` 都按临时失败（宁多试几次，不误杀抖动端点）；`ExoPlayerEngine` 错误日志无条件 `Log.e` 的 release 噪声按提交意图保留；`ExoUtil.getVideoCodecSelector` 的硬解选择器收窄逻辑未改（与前端切换判断一致）。

## 5. 可证伪证据（新增/更新的锁）

- `PlaybackErrorClassifierTest.transientSourceFailuresAreSeparatedFromUnsupportedContent`：网络/本机端点/manifest malformed 为 true，容器 unsupported/容器 malformed/manifest unsupported/decoder 为 false。若把 `MEDIA_PARSING` 整类或容器 code 判为临时，断言即转红。
- `ExoHlsAdblockDataSourceTest.octetStreamDoesNotVetoARealPlaylist` / `segmentBytesUnderAnM3u8SuffixAreNotPlaylists`：octet-stream 真 m3u8 仍进入清洗；TS 字节、空体、BOM/空白前缀判定按 `#EXTM3U` 正文。
- `LiveManifestRetryPolicyTest`：临时失败消息集合、404/`upstream reset` 保持致命、manifest 重试上限 ≥ 8 且大于媒体分片。
- `CompatFfmpegVideoRendererSourceTest.hardwareModeKeepsFfmpegAsTheLastVideoRenderer`：硬解模式受「解码器兜底」控制、FFmpeg 仍追加且不回流 `CompatFfmpegVideoRenderer`。

## 6. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 双 flavor arm64 Debug Java 编译 | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL in 2m 41s** |
| 播放器/EXO/FFmpeg 定向单测 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.player.*' --tests 'com.fongmi.android.tv.player.exo.*' --tests 'io.github.anilbeesetti.nextlib.media3ext.ffdecoder.*'` | 210 个测试类 / **1515 例，0 失败 0 错误** |
| 合并带入契约测试 | leanback `TvErrorStateContractTest`（14）、mobile `MobileHomeStateContractTest`（8）+ `OneKeySyncGenerationGuardTest`（9） | 全部通过 |
| UI token 门禁 | `bash scripts/check_ui_tokens.sh` | **PASS**：`violations=0`、`hex_layouts=0`、`hex_drawables=0`、包含 `hex_colors=0`，对比度 38 对 0 失败（min=4.28） |
| 行尾/冲突 | `git diff --check`；冲突标记扫描 | 空 / 0 |

如实记录的边界：本轮未做设备端实机回归（修复不改成功路径；`ExoUtil` 条件化只影响关闭兜底开关的分支，默认路径与提交实测一致）；`LiveManifestRetryPolicy` 的 8 次 manifest 重试上限未单独做设备端空体复现。

## 7. 交付坐标

- 合并提交：`977ddf4343882cffe3623a126e76bc490f884c29`（双父 `8d47a5dd34618af520ba8f01c2d6aaa6a28f78e7` + `77e2fbefdc30c501c3646b066826a714f54b3f38`）
- Recovery tag：`recovery/C88-beta-merge-review-dev3-20261021/20261021010236-977ddf434388`
- 推送：`dev3`（`f70fa8c5fd..977ddf4343`）
- PR：**#466** <https://github.com/Silent1566/webhtv/pull/466>（`base=beta` / `state=OPEN` / `mergedAt=null`，只创建不合并）
- 拉取远端最新代码：`git pull --ff-only origin dev3` → `Already up to date`；`git fetch origin beta` → `77e2fbefdc`（= 合并目标）
- 口径提示：本仓库有两个 remote，`gh pr create` 不带 `-R Silent1566/webhtv` 时解析到 upstream `fish2018/webhtv`，会报 `No commits between beta and dev3`；创建 PR 必须显式指定 `-R`。
