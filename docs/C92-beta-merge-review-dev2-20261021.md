# C92-beta-merge-review-dev2-20261021

## Recovery anchor

- **目标**：把 `origin/beta@a0641ef0e2` 合并进 `dev2`（零复活、零丢失），复评 `dev2` 唯一已提交未推送的提交 `0a530b82ae`（切换线路不自动播放时保留当前播放流）以及 `beta` 本次带入的 C91/E-SP11 代码，修复必修项，验证通过后提交、推送、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突、合并结果树与 `git merge-tree` 预演逐字节一致；B（`beta` 树 − 合并树）= 0；C（revert 删除 ∧ 不在 `beta` ∧ 在合并树）= 0。
  2. `0a530b82ae` 的 3 个路径逐行复评；`beta` 带入的 11 个路径做 dev2 上下文集成复评（其自身已在 C91 复评并修复 4 项）。必修项已修且每条有可证伪测试或可复核证据。
  3. 双 flavor arm64 Debug Java 编译通过；定向单测与双 flavor 全量单测相对 C90 基线**零新增失败**；`scripts/check_ui_tokens.sh` PASS。
  4. 合并提交保留双父，经任务守卫 `finish` 落盘并创建 recovery tag。
  5. 推送 `dev2`，创建到 `beta` 的 PR（中文描述，只创建不合并），随后拉取远端最新代码。
- **Lane**：`standard`
- **Scope**：`app/src/leanback/.../ui/activity`、`app/src/mobile/.../ui/activity`、`app/src/main/java/.../player/exo`、`app/src/main/java/.../setting`、`app/src/test/java/...`、`docs/`
- **任务守卫**：`C92-beta-merge-review-dev2-20261021`，base HEAD `0a530b82ae48d1b9a91b6aa7ad8f2360c313de08`，branch `dev2`，受保护脏路径为任务前既有的未跟踪 `.wh_diag/`（127 个文件）
- **基线**：`dev2` HEAD = `0a530b82ae`（相对 `origin/dev2` ahead 1，未推送）；merge-base = `35d6dae6b6`
- **当前状态**：全部完成；交付坐标见 §6
- **下一动作**：无

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `a0641ef0e2`（PR #469 合入后） |
| 本地 | `dev2` = `0a530b82ae`（1 个未推送提交） |
| merge-base | `35d6dae6b69bf2f117c895942156df42842583f6`（= C90 文档回填，PR #468 的 dev2 侧 tip） |
| 冲突 | **0**（`git merge --no-ff --no-commit origin/beta` → `Automatic merge went well`；`git diff --name-only --diff-filter=U` 为空） |
| 预演/实合并树 | `79af61b946e3f21c8e4dc7474562ea75f165e748`（`git merge-tree --write-tree HEAD origin/beta` 与 `git write-tree` **逐字节相同**，判定 MERGE-RESULT-EXACT） |
| 带入提交 | 5 个：`7faf6cea93`（E-SP11 实现）、`2c3af2f009`（PR #468，dev2 上轮交付）、`a8ae5e8602`（C91 合并+复评）、`8a58112b53`（C91 文档回填）、`a0641ef0e2`（PR #469） |
| 带入路径 | 11 个（`beta` 相对 `origin/dev2`）：exo 运行时画像 4 个源码 + 播放性能设置 2 个源码 + 2 个测试 + C91/E-SP11 文档与评估索引 |

## 2. 复评范围与跳过依据

- **必评 1：`0a530b82ae`**（3 路径）——两 flavor `VideoActivity.onRefresh()` 与 `FlagSwitchPlaybackWiringTest`。该提交产生于 C90 复评（`dbed4ad543`，2026-10-21 04:00 UTC）之后（recovery tag `20261021051146`），此前任何一轮复评都未覆盖，故本轮完整复评。
- **必评 2：`beta` 带入的 11 路径**——C91 已在 `dev3` 上完成逐行复评与 4 项修复，本轮做 **dev2 上下文集成复评**：路径交集、调用方/依赖方向、既有约定（Java 21、`third_party` media3 fork、`Backup.APP_PREFS`、设置页取数线程）、测试基线；并独立复核 C91 的关键判据（不采信文档结论）。
- **不重复复评**：`beta` 带入的 C90 内容（`760f387de3`/`944dd6419f`）与 dev2 同源（经 PR #468 已在 beta），内容级 diff 为 0，沿用 C90 结论。
- **路径交集**：`0a530b82ae` 的 3 路径 ∩ `beta` 11 路径 = **空集**，无文本冲突，风险仅剩语义集成。

## 3. 零复活 / 零丢失证据

| 判据 | 含义 | 结果 |
| --- | --- | --- |
| 路径数 | merge / beta / base | **5175 / 5175 / 5172** |
| **A** | 合并树 − `beta` 树 | **0**（未推送提交不新增路径） |
| **B** | `beta` 树 − 合并树（丢失面） | **0** |
| revert 集合 | 主题行锚定（`revert`/`回退`/`撤销` 开头，`dev2`+`origin/beta` 全历史去重） | 27 提交 / 44 删除路径 |
| **C** | revert 删除 ∧ 不在 `beta` 树 ∧ 在合并树（复活面） | **0** |
| 检测器自检 | 探针 `app/src/main/assets/themes/index.json` 在 revert 删除集且不在 `beta` 树 | 有效探针（命中 1 次 / `beta` 树 0 次） |
| 内容级净差（merged vs `HEAD`） | 本次合并实际带入内容 | **11 路径 / +693 −8**，与 §1 的 `beta` 带入面逐条一致 |
| 内容级净差（merged vs `beta`） | 反向核对：只应有 dev2 未推送内容 | **3 路径 / +22 −16**，恰为 `0a530b82ae` 的 3 个路径，`beta` 侧内容零丢失 |

## 4. 复评发现与修复

| # | 级别 | 位置 | 问题 | 修复 |
| --- | --- | --- | --- | --- |
| 1 | **medium** | leanback/mobile `VideoActivity.onRefresh()` 的抑制分支 | `0a530b82ae` 把抑制守卫移到 `onRefresh()` 顶部，修好了「先拆流导致旧流被停」，但同时把原有**无条件执行**的 `mViewModel.cancelPlayerContent()` + `invalidatePlayerContent()` 一起跳过。若切线路时上一集的 `playerContent` 请求仍在途（慢源常见），该请求不会被作废：`setPlayer()` 只校验 adapter 是否就绪（`canApplyPlayerResult()`），不校验请求身份，结果返回后会替用户**自动起播旧线路**的集，与「切换线路不自动播放」直接冲突 | 在抑制分支内先 `cancelPlayerContent()` + `invalidatePlayerContent()` 再早退：两者都不触碰 `player()`，当前播放流保持不动，但旧线路在途结果被作废、取流上下文复位。两 flavor 同步修改；`FlagSwitchPlaybackWiringTest` 新增 2 条断言（抑制分支内必须出现 `cancelPlayerContent()` / `invalidatePlayerContent()`） |

**明确不修（记录为已知边界）**：

- `beta` 侧 low 观察（C91 已记录，本轮复核成立）：摘要字符串硬编码中文（与同屏其余文案一致）；旧环境条目保留至 30 天 TTL 并占 `MAX_ENTRIES = 64` 名额（`trimToCapacity()` 优先淘汰黑名单条目）；`getSummary()` 在设置页主线程同步读一次 prefs（可能触发 prune 写，量级一次）；`blacklistedCount()` 目前仅测试使用。
- `0a530b82ae` 在换线路时 `saveHistory()` 会用**新**线路 flag 写 `EpisodePositionCache`，而 `mHistory` 仍指向旧线路集。该语义在 C84 评审前就已存在（本提交只是把 `saveHistory()` 从「拆流之后」提前到「拆流之前」，位置与旧流一致，未新增错位），且同集跨线路的播放位置可比，故只记录。
- 本轮无设备端复跑：本机可达设备均为 x86_64/SDK 28 镜像，未构建对应产物；本次修复只涉及「请求生命周期作废」，不改变成功播放路径，沿用既有实机证据边界。

## 5. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 双 flavor arm64 Debug Java 编译 | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL in 2m 1s**（修复前）/ **1m 40s**（含修复目标的增量编译+定向测试） |
| 定向单测（复评面） | `:app:testLeanbackArm64_v8aDebugUnitTest --tests '*FlagSwitch*' --tests '*ExoDecoderRuntime*' --tests '*PlaybackPerformance*' --tests '*ExoFrameScheduling*' --tests '*PlayerControlFocusIntegrationTest'` | **78 例 / 0 失败 / 0 错误** |
| 可证伪验证（变异） | 删去抑制分支的 `cancelPlayerContent()` + `invalidatePlayerContent()` 后重跑 `FlagSwitchPlaybackWiringTest` | **如期转红**（`refreshKeepsTheCurrentStreamOnlyWhileTheFlagSwitchIsArmed` FAILED）；还原后**转绿**（逐字节还原，`invalidatePlayerContent();` 计数 5/5） |
| 全量 leanback | `:app:testLeanbackArm64_v8aDebugUnitTest` | **4440 tests / 8 failed / 3 skipped** |
| 全量 mobile | `:app:testMobileArm64_v8aDebugUnitTest` | **5228 tests / 6 failed / 3 skipped** |
| 失败集比对 | 与 `docs/C90-beta-merge-review-dev2-20261021.md` §5 基线逐条一致 | **零新增失败**（+7 例为 `beta` 新增测试：`ExoDecoderRuntimeProfilesSummaryTest` 5 + `PlaybackPerformanceSettingSourceTest` 2）；失败均为 Windows CRLF 检出产物（`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`、`ReaderPlaybackRoutingSourceTest`×2、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`，leanback 额外 `NativeEnhancedPlaybackStyleFocusTest`、`SearchResultDownFocusTest`） |
| 第三方事实独立复核 | `third_party/maven/androidx/media3/media3-exoplayer/1.11.0-alpha01-fongmi/...-sources.jar` | `MediaCodecVideoRenderer.java:153` 默认阈值 **15 000 µs**；`:429-440` 明确「传 `C.TIME_UNSET` 才不丢输入帧」；`DefaultRenderersFactory.java:143` 同默认值 ⇒ 支撑 C91 对 `C.TIME_UNSET` 修复的判据 |
| UI token 门槛 | `bash scripts/check_ui_tokens.sh` | **PASS**：`violations=0`、`hex_layouts=0`、`hex_drawables=0`、`hex_colors=0`，对比度 38 对 0 失败（min=4.28） |
| 工作区卫生 | `git diff --check` / 冲突标记扫描 | 空 / 0 |

## 6. 交付坐标

- 合并提交：`08618b49ec1177344f9f389f47e596cdef022962`（双父 `0a530b82ae48d1b9a91b6aa7ad8f2360c313de08` + `a0641ef0e22e6b1353ecfbc5f86c93c34f91c051`）
- recovery tag：`recovery/C92-beta-merge-review-dev2-20261021/20261021073428-08618b49ec11`
- 推送：`dev2`（`35d6dae6b6..08618b49ec`）
- PR：**#470** <https://github.com/Silent1566/webhtv/pull/470>（`base=beta` / `head=dev2` / `state=OPEN` / `mergedAt=null`，只创建不合并）
- 拉取远端最新代码：`git fetch origin --prune` + `git pull --ff-only origin dev2` → `Already up to date`；`origin/beta` 仍为 `a0641ef0e2`（= 本次合并目标）
