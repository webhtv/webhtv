# PLAYBACK-LOADING-GUARD-20261009：释放「播放结果被丢弃」分支的加载守卫

## Recovery anchor

- **目标**：修复 leanback `VideoActivity.setPlayer()` 中 `!canApplyPlayerResult()` 早退分支不释放播放加载守卫（`mPlaybackRequestActive` / `mPlaybackPlayerStarted`）导致加载圈无任何清除路径的缺陷；以源级用例锁定。
- **验收标准**：① 该分支在放弃结果时释放两个守卫；② 双 flavor 相关契约用例零新增失败；③ 净差异仅本任务两个文件与本文档；④ 提交 + recovery tag。
- **lane / scope**：`quick-fix`；`app/src/leanback/.../VideoActivity.java`、`app/src/testMobile/.../VideoActivityLayoutTest.java`、`docs/`。
- **branch / 基线 HEAD**：`dev2`；`29d52ab2385702a9350c3099cd94dd4e54c1240f`（Merge PR #426）。
- **受保护脏路径**：0（任务开始时工作区干净）。
- **回滚锚点**：`29d52ab238`。
- **当前状态**：已实施，验证通过；提交与 tag 由 `task_guard finish` 创建。
- **下一动作**：无（待用户决定是否继续处理其余可疑点）。

## 缺陷（代码上确实漏掉的分支）

`setPlayer(Result)` 在守卫持有期间有 6 条退出路径，其中 5 条已正确释放或依法持有：

| 路径 | 是否释放 |
| --- | --- |
| `result == null \|\| isFinishing() \|\| isDestroyed()` | 不可达（`SiteApi.playerContent` 为 `@NonNull` 契约）；不动 |
| `service() == null` → 存 pending 后重试 | 合法持有 |
| `hasMsg() \|\| getRealUrl().isEmpty()` → `onError` | 释放（`onError` 内两条赋值） |
| **`!canApplyPlayerResult()`** | **漏掉 —— 本任务修复** |
| `result == mAppliedPlayerResult && !player().isEmpty()` | 释放（C42 已修，`3374def641`） |
| `redirectToContentHandler` | 释放 |

命中漏掉的分支时，`mPlaybackRequestActive` 永久为真：`onStateChanged(STATE_READY)` 的 `if (mPlaybackRequestActive && !mPlaybackPlayerStarted) break;` 与 `hidePlaybackProgressIfStale()` 的同一条件**同时**被挡下，而这两处是加载圈仅有的清除路径 ⇒ 旧内容继续播放、圈永久留存（「画面在动、圈不走」）。这与 C42 修复另一分支时注释记录的机理同源。

范围仅 leanback：mobile `VideoActivity` 没有这套守卫字段（`grep -c` = 0），故无对应分支。

## 验证证据

全部命令在 `F:/Workspace/webtv2/webhtv`（dev2）执行，`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`，`--no-daemon`。

| 项目 | 命令 / 结果 |
| --- | --- |
| 生产码编译 | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` → 成功（未阻断后续测试任务） |
| 定向用例（一次调用） | `:app:testMobileArm64_v8aDebugUnitTest --tests '*VideoActivityLayoutTest*' --tests '*ReaderPlaybackRoutingSourceTest*' --tests '*PlaybackOwnershipSourceTest*'` |
| 结果（XML 权威计数） | `VideoActivityLayoutTest` **tests=154 failures=0 errors=0**（含本任务新增断言）；`PlaybackOwnershipSourceTest` **16/0/0**；`ReaderPlaybackRoutingSourceTest` 28/2/0 |
| 新增断言非空洞性 | 未修复时该分支只有 `SpiderDebug.log` + `return;`，不含 `mPlaybackRequestActive = false;` ⇒ 断言必然变红（字符串包含关系，确定性判断，未额外跑变异） |

### 失败分类：环境（既有基线），非本任务回归

`ReaderPlaybackRoutingSourceTest` 的 2 项失败（`horizontalComicProgressUsesItsOwnPageState`、`readerDefinesTheMonotonicClockUsedByRestore`）由**本机 CRLF 检出**造成：

- `git ls-files --eol app/src/main/assets/reader.html` → `i/lf  w/crlf`
- 该文件 CRLF 2627 处、**裸 LF 0 处**；而两处断言要求 `"...function nowMs(){\n"`、`"...comicShowPage(i){\n"` 等**裸 `\n`** 字面量 ⇒ 必然失配。
- 断言只读该资源文件，本任务未触及（`git status` 仅两个 `.java`）⇒ 与本改动无因果关系。

这是仓库既有问题（`docs/C51-beta-merge-review-dev2-20261009.md` 已记载「少数源码扫描类用例断言里带 `\n` 字面量，在 CRLF 检出下会因 `\r\n` 误判，四个 dev 工作区一致」），超出本任务范围，仅报告。

## 相邻但未修复（超出范围，仅报告）

1. **暂停时 seek 窗口关不掉**：`canHideSeekProgress()` 的 `(!player().isLoading() || player().isPlaying())` 在「暂停且引擎仍报 loading」时恒假，而最小可见计时器、每秒 ticker、`onStateChanged(READY)`、`onControllerReadyReconciled()` 四条路径共用该闸门 ⇒ 圈留存。属「缺独立逃逸分支」，需另行决定上限语义。
2. **IJK/MPV 无停滞兜底**：`PlayerManager` 的 `ExoBufferingStallWatchdog` 由 `if (!isExo())` 把关（arm/check 两处），IJK/MPV 无等价物；`docs/E-SP3-exo-buffering-stall-watchdog.md` 记录同症状但修复范围明示只含 Exo。
3. **屏显诊断「重缓冲次数」对非 Exo 恒为 0**（`PlayerOsdController` 用 `Snapshot.empty()`，仅 MPV 走 tracker）⇒ 不能作 IJK 判据，应看「状态」项。
4. `PlaybackOwnershipSourceTest` 的 KDoc 称「这里不依赖归属」，而测试与代码都要求 `!isOwner()` —— 文档与实现不一致（陈旧，非代码缺陷）。
5. `reader.html` 的 CRLF 检出会持续让上述 2 项用例在本机变红；如需在本机全绿，需单独任务处理 EOL 或断言写法。

## 交付坐标

| 项 | 值 |
| --- | --- |
| 代码提交 | 由 `task_guard.sh finish` 创建（本文件同提交） |
| recovery tag | 由 `finish` 创建（`recovery/PLAYBACK-LOADING-GUARD-20261009/<时间戳>-<短SHA>`） |
| 推送 | **未推送**（未授权） |
| 资源回收 | 本任务使用 `--no-daemon`，构建结束后无守护进程残留；未清理他人缓存 |
