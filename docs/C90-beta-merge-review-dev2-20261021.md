# C90-beta-merge-review-dev2-20261021

## Recovery anchor

- **目标**：把 `origin/beta@687a6b65da` 合并进 `dev2`（零复活、零丢失），复评本次合并带入的代码与 `dev2` 已提交未推送的 2 个提交（`760f387de3` 片尾快捷聚焦修复 + `944dd6419f` 真机证据文档），修复必修项并验证通过，然后提交、打 recovery tag、推送 `dev2`、向 `beta` 开中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突；合并树与 `git merge-tree` 预演逐字节一致；`B`（`beta` 树 − 合并树）= 0，`A`（合并树 − `beta` 树）只含 `dev2` 未推送的新文档。
  2. `dev2` 未推送的 2 个提交全部只读复评；`beta` 带入的 C88/C89 改动因「已由 dev3 评审 + 本次 0 冲突 + 路径与未推送提交零交集」跳过，跳过依据与跨分支检查结论入档（§2、§4）。
  3. 双 flavor arm64 Debug Java 编译通过；定向单测全绿；双 flavor 全量单测零新增失败；`scripts/check_ui_tokens.sh` PASS。
  4. 合并提交保留双父（`--no-ff`），经任务守卫 `finish` 落盘并创建 recovery tag。
  5. 推送 `dev2`，创建到 `beta` 的中文 PR（只创建不合并），随后拉取远端最新代码。
- **Lane**：`standard`
- **Scope**：`app/src/{main,leanback,mobile}/java`、`app/src/{main,leanback,mobile}/res`、`app/src/test`、`app/src/testMobile`、`docs`
- **任务守卫**：`C90-beta-merge-review-dev2-20261021`，base HEAD `944dd6419fee496b1e3237d93e2b9b0f0c84f0fd`，branch `dev2`，受保护脏路径 127（全部为未跟踪的 `.wh_diag/` 诊断产物）
- **基线**：`dev2` HEAD = `944dd6419f`（相对 `origin/dev2@4a85e49fd2` ahead 3；其中 `77e2fbefdc` 已随 PR #465 进入 `beta`，真正未推送的是 `760f387de3` 与 `944dd6419f`）
- **当前状态**：合并 + 复评 + 验证完成；提交/推送/PR 见 §6
- **下一动作**：无

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `687a6b65da9597b10a27ed2cb4c356283cd5f1f2`（PR #467 合入后） |
| 本地 | `dev2` = `944dd6419f` |
| merge-base | `77e2fbefdc30c501c3646b066826a714f54b3f38` |
| 冲突 | **0**（`git merge --no-ff --no-commit origin/beta` → `Automatic merge went well`） |
| 预演树 / 实合并树 | `761519174e6ab77bedad40107c9099ae6bf06d2a`（`git merge-tree --write-tree` 与 `git write-tree` **逐字节相同**） |
| `beta` 树 | `de1904116357ff6ab427a07d5e77dd3fd7527a7f` |
| 路径数 | merge **5171** / beta **5170** |
| **A**（合并树 − beta 树） | **1**：`docs/TV-ENDING-SHORTCUT-20261021-ending-shortcut.md`（dev2 未推送的新文档） |
| **B**（beta 树 − 合并树） | **0**（无丢失） |
| 内容级净差异 `git diff beta树 → 合并树` | 恰 **3** 路径 / `+142 −2`，全为 dev2 未推送内容 |

`beta` 自 merge-base 带入 **12 个提交 / 23 条路径**（C88 的 5 个播放器修复、C89 的直播源名称、两份文档与索引行、PR #466/#467 的 merge commit）。`dev2` 侧净差异只有自己的电视端修复与文档，故本次是「纯并入」：无 `dev2` 既有内容被覆盖。

## 2. 复评范围与跳过依据

- **复评（本文档主体）**：`dev2` 未推送的 `760f387de3`、`944dd6419f`，路径 3 条：
  - `app/src/leanback/java/.../ui/activity/VideoActivity.java`（`onKeyUp()` + 新增 `isEndingShortcutDue()`）
  - `app/src/testMobile/java/.../ui/activity/PlayerControlFocusIntegrationTest.java`（+25）
  - `docs/TV-ENDING-SHORTCUT-20261021-ending-shortcut.md`（+95）
- **跳过**：`beta` 带入的 C88/C89 改动。依据三条同时成立：1）已由 `docs/C88-beta-merge-review-dev3-20261021.md`（10 项修复 + 变异验证）与 `docs/C89-...md` 完成评审并随 PR #466/#467 合入 `beta`；2）本次合并 0 冲突且合并树与预演逐字节一致，无冲突解决产生的第三方改写；3）带入路径与 `dev2` 未推送路径**交集为空**（`dev2` 只动 leanback `VideoActivity` 与 testMobile 一个测试文件）。仍对两个跨分支面做了定向核查，结论见 §4。

## 3. dev2 未推送提交复评（逐条核查）

| # | 核查点 | 结论 | 证据 |
| --- | --- | --- | --- |
| 1 | 触发时机 | 新分支只在「全屏 + 控制栏隐藏 + 遥控方向/确认键」路径可达 | `VideoActivity.dispatchKeyEvent()`：`isFullscreen() && isGone(mBinding.control.getRoot()) && mKeyDown.hasEvent(event)` → `CustomKeyDownVod.check()` → ACTION_UP + UP 键且未处于倍速 → `listener.onKeyUp()` |
| 2 | 控制栏可见时的行为 | 未被改变：可见时先走 `dispatchOpeningEndingAdjust()` / `PlayerControlFocusHelper.handleKey()`，UP/DOWN 仍是片头/片尾微调 | `VideoActivity.java:8260-8285` |
| 3 | 判定条件 | `position > 0 && duration > 0 && position >= duration/2 && canSetEnding(...)`；直播/未知时长（duration ≤ 0）不入分支 | `VideoActivity.java:8488`、`PlayerManager.canSetEnding():741` |
| 4 | 短片/短剧 | 时长落在两侧窗口内时（例如 3 分钟一集，`getOpEdLimit`=3 分钟），前半段仍给片头、后半段才给片尾，符合文档验收④⑤ | `Constant.getOpEdLimit()` + 中点条件 |
| 5 | 长内容 | 片尾窗口 ⊆ 后半程，`position >= duration/2` 恒成立，不改变原本能命中的场景 | `canSetEnding` = `duration - position <= getOpEdLimit(duration)` |
| 6 | 与 09-29 收敛语义的关系 | 片头分支仍受 `hasRememberedFocus()` 门禁，只有片尾分支提前；`mFocus2` 生命周期、`getFocus2()`、`showControl()` 未改 | `VideoActivity.java:8218-8226`、`8456-8473` |
| 7 | 焦点落点 | `showControl(ending)` → `PlayerControlFocusHelper.ensureFocus(root, ending)`：ending 不可见/不可聚焦时**优雅回退**到当前焦点或 root 首个可聚焦控件，不会空焦点或崩溃 | `PlayerControlFocusHelper.java:19-27`、`104-113` |
| 8 | 融合播放页/手机 flavor | 不共享该唤醒路径（`TmdbDetailActivity` 内联控制栏只回退 `playerNext`），修复不影响它们；既有测试 `fusionControlWakeKeepsUserChosenFocusOutsideTheIntroRange` 继续锁住该边界 | `TmdbDetailActivity.java:7904` |
| 9 | 契约测试有效性 | 新测试断言「判定位先于记忆焦点门禁」「仍以 `canSetEnding` 为准」「带后半程条件」「落点为 ending 按钮」；删掉判定位/改判据/把分支塞回门禁内都会转红（断言均带 needle，非恒真） | `PlayerControlFocusIntegrationTest.java:238-260` |
| 10 | 文档与代码一致性 | 文档「实现」片段与 `VideoActivity.java:8456-8490` 逐字一致；验证节记录 11 例（原 10 + 新增 1）与实测 XML `tests=11 failures=0` 一致 | `git diff back` + XML |

**低severity 观察（不修，记录）**：

- 若用户在「播放设置 → 按钮」里隐藏了片尾按钮，新分支仍会先命中 `canSetEnding`，`ensureFocus` 回退到当前焦点/首个可聚焦控件；无记忆焦点时与旧行为（`canSetEnding` 分支）落点相同，有记忆焦点时可能丢失一次「恢复上次控件」。属既有 `opening` 分支同构缺陷，非本次新增；隐藏按钮是显式用户配置，影响不可复现地集中在单一 UI 配置下，故按范围只记录。
- `760f387de3` 的提交信息写「device verification not performed」，而随后的 `944dd6419f` 记录了 5557 真机三例实测。两者是**时间先后**关系（先提交代码、后补设备证据），文档口径以 `944dd6419f` 为准；历史提交信息不回写。

## 4. 跨分支核查（beta 带入代码，仅记录不修）

| # | 面 | 发现 | 处置 |
| --- | --- | --- | --- |
| 1 | Exo 硬解渲染器 | `beta`（C88 `e79ed9058a`）把硬解 + 「解码器兜底」开启时改为 `EXTENSION_RENDERER_MODE_ON`，即硬解标签下追加 FFmpeg 视频渲染器；`docs/E-SP9-exo-unsupported-video-soft-fallback.md` 的 C65 修订口径原文是「不允许软解顶着『硬解』标签出帧」且明确要求 `CompatFfmpegVideoRendererSourceTest` 的「硬解不注册 FFmpeg 视频回退」断言保持不变，C88 已把该断言改名为 `hardwareModeKeepsFfmpegAsTheLastVideoRenderer` | **不改代码**：这是 `beta` 已发布行为（PR #466），回退属跨分支合同变更，需用户裁决；本次只在本文档记录冲突。功能侧自查：默认路径下 `PlayerManager.maybeFallbackToSoftDecode()`（`needsSoftDecode` → `hasHardwareVideoDecoder` 为假）仍会在 TRACKS 事件把整场播放切到软解，OSD/诊断标签随后一致，故未观察到「标签与出帧长期背离」的功能回归 |
| 2 | 直播 manifest 重试 | `LiveManifestRetryPolicy` 被 `MediaSourceFactory.createItemMediaSourceFactory()` 无条件装到**每个** item（含点播 HLS/DASH），`getMinimumLoadableRetryCount(DATA_TYPE_MANIFEST)` 抬到 8、退避 400→3000ms，最坏约 17.8s；与 `PlayerManager` 里「点播死链不该把换源推迟十几秒」的 `TRANSIENT_SOURCE_VOD_MAX_RETRY=2` 意图不完全一致（内层重试耗尽才会抛出给上层） | **不改**：属 `beta` 已评审行为（C88 #6 只按 data type 收窄）。实测风险面仅「点播清单返回 200 空体/非 m3u8 文本」这一少见的坏源；命名（`LIVE_`）与实际适用范围不一致已在 C88 记录范围内 |
| 3 | 直播源名称按钮 | 两 flavor `LiveActivity.setSource()` + mobile `LiveControlDialog` 镜像；`strings` 三语文案 `xliff:g name="name"` 与 `getString(id, name)` 参数一致；布局 `maxWidth`/`ellipsize` 两 flavor 对等；`refreshControlDialog()` 仅回写已存在面板，无递归 | 无需处置 |
| 4 | 临时失败重试生命周期 | `transientSourceRetry` 在 `reset()` 与 `start()`（新条目入口）归零，`App.post` lambda 带 `attempt != transientSourceRetry` 代际校验；重试耗尽后 `isKernelFallbackEligible` 仍放行容器不支持类错误的换核，网络/manifest 类错误则直接报错而非静默黑屏 | 无需处置 |

## 5. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 双 flavor Java 编译 | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac` | `BUILD SUCCESSFUL in 3m 28s` |
| 定向单测（本次相关 6 类） | `:app:testMobileArm64_v8aDebugUnitTest --tests PlayerControlFocusIntegrationTest --tests PlaybackErrorClassifierTest --tests ExoHlsAdblockDataSourceTest --tests LiveManifestRetryPolicyTest --tests CompatFfmpegVideoRendererSourceTest --tests LiveActivityLayoutTest` | **46 例 / 0 失败 / 0 错误**（11 + 11 + 5 + 3 + 3 + 13） |
| 全量 leanback | `:app:testLeanbackArm64_v8aDebugUnitTest` | **4433 tests / 8 failed / 3 skipped** |
| 全量 mobile | `:app:testMobileArm64_v8aDebugUnitTest` | **5221 tests / 6 failed / 3 skipped** |
| 失败集比对 | 与 `docs/C87-...md` §4.2 基线逐条一致 | **零新增失败**（+6 / +10 为 C88 与本次新增测试）；全部为 Windows CRLF 检出产物（`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`、`ReaderPlaybackRoutingSourceTest`×2、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`，leanback 额外 `NativeEnhancedPlaybackStyleFocusTest`、`SearchResultDownFocusTest`） |
| UI token 门槛 | `bash scripts/check_ui_tokens.sh` | `violations=0 hex_layouts=0 hex_drawables=0 contrast 38 对 0 失败 min=4.28` → `PASS` |
| 工作区卫生 | `git diff --check` / 冲突标记扫描 | 空 / 0 |

**如实记录的边界**：未做设备端回归（本次没有改动成功路径行为；`760f387de3` 的 5557 三例实测证据见其任务文档，本轮无法复跑该设备）；C88 的 5 个播放器修复按其 own 证据沿用，本轮未重做设备复现。

## 6. 交付坐标

- 合并提交：`dbed4ad5439c8986df0588209c0726dc5398f01c`（双父 `944dd6419f` + `687a6b65da`）
- recovery tag：`recovery/C90-beta-merge-review-dev2-20261021/20261021040001-dbed4ad5439c`
- 推送：`dev2`（`4a85e49fd2..dbed4ad543`）
- PR：**#468**（`base=beta`，中文描述，只创建不合并）
- 拉取：合并提交后执行 `git fetch origin` + `git pull --ff-only`，确认与远端一致
