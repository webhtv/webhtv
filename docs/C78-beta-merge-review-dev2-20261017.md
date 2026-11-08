# C78-beta-merge-review-dev2-20261017

## Recovery anchor

- Objective: 在 dev2 上合并 `origin/beta` 最新代码（含远端已回退/删除的提交不得被顺带带回），复评本次合并带入的改动与本地 3 个已提交未推送的 MPV 提交，循环评审-修复-验证直到通过，然后提交、推送并创建到 `beta` 的中文 PR（只创建、不合并）。
- Lane: `standard`。
- Baseline: branch `dev2`，HEAD `0f034b69b524f8ec5b2ee365a39950536ca54caf`。
- Protected dirty paths: `.wh_diag/**`（任务前已存在，未纳入 scope）。
- Status: 合并完成、复评 2 轮并修正 3 项必修缺陷、验证全部通过，待提交。
- Next action: 无。

## 1. 合并对象与中途前进

| 轮次 | `origin/beta` tip | 说明 |
| --- | --- | --- |
| 首轮 | `323afa1a5a9b962749bd3e78840a4f729a2f4701` | PR #453（dev4），0 冲突 |
| 最终 | `332df0c9260549ecbf51a00b2b83176bb796356d` | PR #455（dev3）在首轮验证**中途**推进 |

首次合并后、验证进行中 beta 由 `323afa1a5a` 前进到 `332df0c926`（PR #455，dev3 的线路落盘与稳定键复用）。按既有规程中止旧合并、以新 tip 重新合并并重跑全部验证。最终合并提交的第二父为 `332df0c926`。

**任务编号撞车**：dev3 的 PR #455 占用了 `C77`（`docs/C77-beta-merge-review-dev3-20261017.md`），本任务由 `C77` 改为 `C78`（`C78` 在 `git log --all`、`docs/`、`origin/beta` 三处查重均为空）。guard state 的 `id` 同步改写。

## 2. 零复活 / 零丢失证据

对最终合并树 `0495292ec4b6fecdd5235fa58ea7dd5b85d0b83c`：

| 层 | 判据 | 结果 |
| --- | --- | --- |
| A 合法新增 | 合并树 − beta 树 | 5 条，全部为本任务自身新增（`BufferingStallWatchdog.java`、`BufferingStallWatchdogTest.java`、`PlayerManagerSwitchCauseSourceTest.java`、2 个 docs） |
| B beta 丢内容 | beta 树 − 合并树 | 2 条，全部为本任务 `git mv` 的**重命名源路径**（`exo/ExoBufferingStallWatchdog.java`、`exo/ExoBufferingStallWatchdogTest.java`），非丢失 |
| C 复活 | 「revert 删除且不在 beta 树」的 28 条路径 ∩ 合并树 | **0** |
| D 检测器自检 | 向伪造树注入 `app/src/main/assets/themes/index.json` | 如期报出（非空转） |

- revert 集合由**主题行锚定**提取（`tolower(substr($2,1,6))=="revert" || 回退 || 撤销`），共 27 个提交，避免误匹配提交正文里的「回退」字样。
- 整树比对用 `git ls-tree -r --name-only` + `comm`，规避 MSYS 把 `git cat-file -e origin/beta:path` 的 `/` 当路径分隔符导致的假阴性。
- 净差异（beta → 合并树）恰为本任务自身 10 个路径，与 A/B 两表一致。

## 3. 复评发现的必修缺陷（3 项）

### 3.1 快门在手机版白等（最严重，属本轮本地提交引入的回归）

`3eee5e8c9d` 删除全局开关 `SURFACE_AUTO_STABILITY_GUARD_ENABLED` 后，`isAutoSurfaceDirectEnabled()` 只剩「设备是否被零拷贝保护拦住」这一个条件，**不再包含电视版与硬解**。而 `shouldKeepVideoShutterClosed()` 恰好只问这一个条件：

```java
return isMpv()
        && MpvPerformanceSetting.isAutoSurfaceDirectEnabled()   // 只答「设备未被保护」
        && MpvPerformanceSetting.getOutputMode() == OUTPUT_AUTO
        && !mpvAutoOutputEvaluated ...
```

`resolveSurfaceDirect()` 要求 `leanback && autoEligible` 才可能返回 true，因此手机版上「前者为真、后者恒假」：快门会遮住一个**永远不会出现**的直出探测帧，声音先出来、画面晚几秒——正是 `6a458ec11c`（2026-08-23「修复：MPV 播放先出声后出画面」）修掉的回归。该提交当时之所以成立，是因为全局开关恒为 `false`，`isAutoSurfaceDirectEnabled()` 恰好等价于「不可达直出」；开关一删，这个等价关系就断了。

**独立探针证实**（用 `MpvPerformanceSetting.resolveSurfaceDirect(OUTPUT_AUTO, true, /*leanback=*/false, true, false)` 断言手机版不可达直出，同时断言设备未被保护时该判定为真，两条同时成立即证明二者不一致），随后删除探针。

**修复**：快门补齐与 `resolveSurfaceDirect` 同口径的两个廉价条件——`Util.isLeanback()` 与 `engine.isHard()`。刻意**不**复用 `prepareMpvOutputForNewItem()` 里那套含 `MpvConfigStore.hasGpuVideoProcessing()` 的完整判定：该方法会做目录扫描与文件读取，而快门谓词由 `PlaybackActivity.syncShutter()` 在多条 UI 路径上同步调用，引入磁盘 I/O 属性能回归。补齐的两个条件已足以消除「手机版白等」这一实际缺陷。

### 3.2 超时日志的实测停滞时长恒为 -1

`c2a09e7647` 新增 `stalledDurationMs(now)`，目的是让超时日志报告**实测**停滞时长而非只报配置上限。但 `onBufferingStall()` 先调 `cancelBufferingStallWatchdog()`（内部 `reset()` 把看门狗置为未布防），**之后**才读 `stalledDurationMs()`；该方法在未布防时返回 `-1`：

```java
public long stalledDurationMs(long nowMs) {
    return armed ? Math.max(0, nowMs - lastProgressAtMs) : -1L;   // 已 reset → 恒 -1
}
```

结果 `stalledMs` 字段永远写 `-1`，等于不存在——排查时无法分辨「刚过 20s 就超时」与「卡了 90s 才超时」。**修复**：把读取移到 `cancelBufferingStallWatchdog()` 之前。

### 3.3 日志位置读在引擎释放之后

`preparePlayer()` 的 `switch-cause` 日志位于 `engine.release()` **之后**，却直接以 `getPosition()` 为实参。Java 实参求值是**立即**的，因此这里读的是已释放播放器的状态，而非内核被换掉时播放真正所在的位置——而「按剧集记住的内核覆盖了设置」正是该字段要回答的问题。**修复**：在 `resetPlayerFallback()` 之后、任何释放之前先读出 `positionBeforeRelease` 再传入。

同时更正两处随 3.1 失效的注释：`shouldKeepVideoShutterClosed()` 与 `MpvOutputShutterSourceTest` 的类注释原先都以「stability guard 把自动档钉在 GPU」为前提，该前提已随开关删除而不成立，改写为「设备零拷贝保护 + 电视版 + 硬解」的真实口径。

### 3.4 复评中否决的两项改动（记录以免重复尝试）

- 曾为 3.3 引入 `PlaybackTrace.isEnabled()` 与 `positionBeforeEngineRelease()` 辅助方法，并声称 `PlaybackTrace.log` 的参数是「惰性求值」。**该前提是错的**：Java 变长参数立即求值。改用直接读取 + 注释说明真实原因，并删除未被使用的 `PlaybackTrace.isEnabled()`（已 `git checkout` 还原该文件）。
- 曾把 `prepareMpvOutputForNewItem()` 与快门统一到一个共享谓词，因该谓词含 `hasGpuVideoProcessing()` 的磁盘 I/O 而否决（见 3.1）。

## 4. 验证

执行环境：2026-10-17 Asia/Shanghai，Windows，`gradlew.bat --no-daemon`。

1. **定向用例**：`BufferingStallWatchdogTest`、`PlayerManagerSwitchCauseSourceTest`、`PlayerManagerLifecycleSourceTest`、`PlaybackTraceTest`、`MpvStabilityGuardSourceTest`、`MpvOutputShutterSourceTest`、`MpvOutputModePolicyTest` 共 **68 例 0 失败**。
2. **全量单测（最终合并树）**：mobile `5140 tests / 6 failed / 3 skipped`、leanback `4345 tests / 8 failed / 3 skipped`。
3. **基线对照**：同一命令在**未加复评修复**的合并树上为 mobile `5129 / 6 failed`、leanback（同失败集 8 项，逐条一致）。失败集合**逐条相同**（`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`、`ReaderPlaybackRoutingSourceTest`×2、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`、leanback 另加 `NativeEnhancedPlaybackStyleFocusTest`、`SearchResultDownFocusTest`），即**零新增失败**；用例数增加来自本任务新增的 3 个测试方法与 beta 自身新增用例。
4. 上述既有失败均为 Windows CRLF 跨行字面量恒假等既有缺陷（`docs/C71`、`docs/C73`、`docs/C74` 已记录同一名单），相关测试类与被断言文件在 beta 与合并树两处 blob 逐一相同，既不在本任务净差异内也不在 beta 增量内，按范围记录不修。
5. **四变体编译**：`compileMobileArm64_v8aDebugJavaWithJavac`、`compileLeanbackArm64_v8aDebugJavaWithJavac`、`compileMobileArmeabi_v7aDebugJavaWithJavac`、`compileLeanbackArmeabi_v7aDebugJavaWithJavac` 与两个 flavor 的 `*DebugAndroidTestJavaWithJavac` 全部 `BUILD SUCCESSFUL`。
6. **静态门槛**：`python scripts/check_ui_tokens.py` → `violations=0`、`UI_TOKEN_CONTRAST pairs=38 failures=0`、`UI_TOKEN_STATUS PASS`。
7. **Python 契约**：`python -m unittest discover -s chaquo/src/test/python` → `Ran 36 tests OK`。
8. `git diff --check` 通过；全仓无冲突标记。

### 4.1 变异测试（证明新增断言可失败，非恒真）

| # | 变异 | 期望 | 实测 |
| --- | --- | --- | --- |
| A | `preparePlayer()` 的位置读取移回 `engine.release()` 之后 | 转红 | `switchCausePositionIsReadBeforeTheEngineIsReleased` FAILED |
| B | `onBufferingStall()` 恢复「先取消后读」 | 转红 | `stallDurationIsMeasuredBeforeTheWatchdogIsDisarmed` FAILED |
| C | 快门删掉 `Util.isLeanback()` / `engine.isHard()` | 转红 | `shutterAsksTheSameGatesAsTheDirectOutputDecision` FAILED |

三次变异均从备份还原，并以 `git hash-object` 确认与修复后版本逐字节相同（`PlayerManager.java` = `9ca7bfb86292151de8e2e944cafb44701fc5c5cc`、`MpvOutputShutterSourceTest.java` = `21aac232a4550d393980c8e12852acf2f755ea42`）。

**变异测试自身的一次假绿**：B 的首版断言直接 `indexOf("stalledDurationMs(")`，而该字符串也出现在**说明性注释**里，于是恢复缺陷顺序后断言仍为绿。改为先剥离注释（`stripComments`）后，变异如期转红。该假绿与仓库既有规程记录的「基于源码文本的守卫必须先剥注释」一致。

## 5. 影响与风险

- 纯 Java 改动，无格式、ABI、Android API、依赖锁、补丁、native 资产或包体积影响。
- 3.1 的修复只让快门在**手机版与软解**下不再关闭，电视版硬解行为与修复前一致。
- 3.2、3.3 只影响诊断日志的字段取值，不改变任何播放决策。
- 主要残留风险：`BufferingStallWatchdog` 对 MPV/IJK 的布防仍只有单测覆盖，停滞超时的设备端到端证据仍缺（限速方式待补，见 `docs/MPV-BUFFERING-STALL-WATCHDOG-20261017.md` §7.2）。

## 6. 回滚

- 合并本身：`git revert -m 1 <合并提交>`。
- 3.1/3.2/3.3 三项修复：`git revert <本任务提交>` 即可完整回退；不涉及持久化 schema。
