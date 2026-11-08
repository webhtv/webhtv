# INLINE-LOADING-RELEASE-20261009：作废内联取址请求时释放其加载标记

## Recovery anchor

- **目标**：修复详情页内播（沉浸融合／详情直放）中「只自增代际、不接替请求」的两处作废点不释放 `inlinePlaybackPending` 的缺陷，消除该标记残留造成的永久加载圈与同一集无法再次起播；以源级用例锁定。
- **验收标准**：① 两处作废点在自增代际时同步释放标记；② 相关源级用例零新增失败；③ 净差异仅本任务两个文件与本文档；④ 提交 + recovery tag。
- **lane / scope**：`quick-fix`；`app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`、`app/src/test/java/com/fongmi/android/tv/ui/activity/TmdbDetailDirectPlayTransitionSourceTest.java`、`docs/`。
- **branch / 基线 HEAD**：`dev2`；`4319403502a64ad8064781f4e88f21391e6abc77`（上一个同族修复）。
- **受保护脏路径**：0。
- **回滚锚点**：`4319403502`。
- **当前状态**：已实施，验证通过；提交与 tag 由 `task_guard finish` 创建。
- **下一动作**：无（残项见「已报告未修」）。

## 缺陷（与上一次修复同族的漏掉分支）

`playInline(...)` 先置位再自增代际：

```java
inlinePlaybackPending = true;                 // 7600 让圈立刻可见
int generation = ++inlinePlaybackGeneration;  // 7602
```

回调里**释放写在「是否仍是最新请求」检查之后**：

```java
if (!isInlinePlaybackRequestCurrent(generation, key, flag, episodeUrl)) return;
inlinePlaybackPending = false;
```

因此凡是「只自增代际、不接替请求」的作废点，回调会直接 return，标记再也没有释放路径。实测两处属此形态（其余自增点均已释放或属置位点）：

| 自增点 | 修复前是否释放 |
| --- | --- |
| `resetDetailState()`（换条目） | 释放（769） |
| `cancelPendingInlinePlayback()`（换集） | 释放（2760） |
| `playInline(...)` | 属置位点 |
| **`refreshAndSwitchInlinePlayer(int)`** | **否 —— 本任务修复** |
| **`cancelPendingInlinePlayerSwitch()`** | **否 —— 本任务修复** |
| `closeDetailFullscreenPlayer()` | 释放（10452） |

`cancelPendingInlinePlayerSwitch()` 的调用方：`switchInlinePlayer` 在选择「当前已生效内核」时（8998）、`openInlineExternal`（打开外部播放器，9295）；`refreshAndSwitchInlinePlayer` 由 8993 在选择其它内核时调用。三者都可能在**取址请求在途**（`inlinePlaybackPending == true`）期间发生。

**后果**（两处症状，均由同一残留标记导致）：

1. `updateInlineLoading()` 的可见条件首项即 `inlinePlaybackPending` ⇒ 圈永久留在屏上，而播放本身继续；
2. `isSamePendingInlinePlayback(episode)` 恒真 ⇒ `selectInlineEpisode` 早退，**同一集无法再次起播**。

另：若提交的任务在运行前被取消，`detailTasks.submit(...)` 的 lambda 根本不会执行（成功与 catch 两条路径都不走），回调式释放永远不触发 —— 这也是必须**在作废点同步释放**、而不能只在回调里释放的原因。

## 改动

`refreshAndSwitchInlinePlayer`：在 `++inlinePlaybackGeneration;` 之后、投递新请求之前同步释放标记并刷新面板（切内核自身的缓冲显示由 `updateInlineLoading` 的引擎条件兜住，不依赖该标记）。`cancelPendingInlinePlayerSwitch`：同一释放语句。

## 验证证据

全部命令在 `F:/Workspace/webtv2/webhtv`（dev2）执行，`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`，`--no-daemon`。

| 项目 | 命令 / 结果 |
| --- | --- |
| 生产码编译 + 定向用例 | `:app:compileMobileArm64_v8aDebugJavaWithJavac :app:testMobileArm64_v8aDebugUnitTest --tests '*TmdbDetailDirectPlayTransitionSourceTest*'` → `BUILD SUCCESSFUL in 56s`；XML：**tests=4 failures=0 errors=0 skipped=0**（含新增 `cancellingAnInlinePlaybackRequestReleasesItsLoadingFlag`） |
| 更宽回归（与改动面成正比） | 同任务显式列出 77 个 Tmdb/Inline/PlaybackOwnership 相关用例类 → **610 tests completed, 1 failed**（唯一失败项见下） |
| 新增断言非空洞性 | 对修复前源码（`git show HEAD:<file>`）逐个方法体做字符串校验：`refreshAndSwitchInlinePlayer` 与 `cancelPendingInlinePlayerSwitch` 均为「有自增代际、无 `inlinePlaybackPending = false;`」⇒ 新断言在修复前必然变红（确定性判断，未额外跑变异） |
| 首轮失败原因 | 首次运行在同一用例第 71 行失败：我把该处自增形式写成后置 `inlinePlaybackGeneration++`，而源码是**前置** `++inlinePlaybackGeneration`。这属我自己的断言错误，已修正为前置形式后重跑通过（有相关编辑，故重跑合规） |

### 失败分类：环境（既有基线），非本任务回归

`TmdbSourceOnlyInteractionTest.nativeVideoSourceOnlyHidesNetworkActionsInBothFlavors` 第 42 行：

```java
assertTrue(leanback.contains("if (runtimeSourceOnly) {\n            renderTmdbRatingChips(...)"));
```

- `git ls-files --eol app/src/leanback/.../VideoActivity.java` → `i/lf  w/crlf`；该文件 CRLF 12081 处、**裸 LF 0 处**。
- 字节取证：断言要求的**裸 LF 序列不存在**（False），**CRLF 变体存在**（True）。
- 断言读的是 **leanback VideoActivity**，而本任务仅改 `TmdbDetailActivity.java` 与测试文件（`git diff --name-only HEAD` 已证）⇒ 无因果关系。

与环境说明同源：本机 `core.autocrlf=true` 使部分文件以 CRLF 检出，少数源码扫描类断言里带 `\n` 字面量因而误判（`docs/C51-beta-merge-review-dev2-20261009.md` 已记载「四个 dev 工作区一致」）。本任务只运行了受影响面的一部分，**未**运行 `ReaderPlaybackRoutingSourceTest`（该类的 2 项同类失败见上一个任务文档）。

## 已报告未修（超出本任务范围）

1. **选择面重算但未自增代际的路径**：`applyTmdbResultNow`（2870，TMDB 详情装载完成）与 `onResume → refreshSelectionAfterExternalPlayback`（11210）会重设 `selectedFlag`/`selectedEpisode` 而不自增代际。若此刻取址请求在途，回调会因选择不匹配而早退、标记残留。未修的理由：可达性未证（窗口窄），且改动会扩散到选择语义；同一场景中的 `openInlineExternal` 已由本次修复覆盖。若要彻底闭合，正确做法是让标记带「所属代际」并在回调早退时按归属释放。
2. `canHideSeekProgress()` 的 `(!player().isLoading() || player().isPlaying())` 在「暂停且引擎持续报 loading」时恒假，四条清圈路径共用该闸门 ⇒ 圈留存。**属有意契约**（`C47SeekLoadingProgressSourceTest` 第 72-75 行明确钉住「已 READY 但仍在真实加载（且没在播）时不能收圈」），非漏写；根治需先解决引擎状态不复位（见下条）。
3. IJK/MPV 无停滞兜底：`PlayerManager` 的 `ExoBufferingStallWatchdog` 由 `if (!isExo())` 把关（arm/check 两处），`docs/E-SP3-exo-buffering-stall-watchdog.md` 记录同症状但修复范围明示只含 Exo。
4. 屏显诊断「重缓冲次数」对非 Exo 恒为 0（`Snapshot.empty()`），排查时应用「状态」项。
5. CRLF 检出会持续让若干带 `\n` 字面量的源级用例在本机变红；如需本机全绿需单独任务处理。

## 交付坐标

| 项 | 值 |
| --- | --- |
| 代码提交 | 由 `task_guard.sh finish` 创建（本文件同提交） |
| recovery tag | 由 `finish` 创建（`recovery/INLINE-LOADING-RELEASE-20261009/<时间戳>-<短SHA>`） |
| 推送 | **未推送**（未授权） |
| 资源回收 | 末次构建后执行 `gradlew --no-daemon clean`，`app/build` 等构建目录已删除，无守护进程残留 |
