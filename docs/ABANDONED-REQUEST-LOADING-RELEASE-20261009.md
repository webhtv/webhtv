# ABANDONED-REQUEST-LOADING-RELEASE-20261009：被作废的取流请求必须释放加载态（两处同族遗漏）

## Recovery anchor

- **目标**：修复两条「取流请求被作废、但加载态无人释放」的遗漏分支——① leanback `VideoActivity.setPlayer(null)`；② 详情页 `TmdbDetailActivity.inlinePlaybackPending` 缺归属判定；以源级用例锁定。
- **验收标准**：① 两处作废路径都能释放加载态；② 双 flavor 主码编译与相关用例零失败；③ 净差异仅本任务文件与本文档；④ 提交 + recovery tag。
- **lane / scope**：`quick-fix`；`app/src/leanback/.../VideoActivity.java`、`app/src/main/.../TmdbDetailActivity.java`、`app/src/testMobile/.../VideoActivityLayoutTest.java`、`app/src/test/.../TmdbDetailDirectPlayTransitionSourceTest.java`、`docs/`。
- **branch / 基线 HEAD**：`dev2`；`7809ff4952788142008d0953697bfe29ded062b3`。
- **受保护脏路径**：0。
- **回滚锚点**：`7809ff4952`。
- **当前状态**：已实施，验证通过；提交与 tag 由 `task_guard finish` 创建。
- **下一动作**：继续处理剩余遗漏点（屏显诊断非 Exo 重缓冲计数、IJK/MPV 停滞兜底）。

## 遗漏点 ①（leanback）：`setPlayer(null)` 早退不释放守卫

`setPlayer(Result)` 原本是：

```java
if (result == null || isFinishing() || isDestroyed()) return;   // 三条路径共用一次早退
```

而 `result == null` **是可达的**：`SiteViewModel.cancelPlayerContent()` 会向 PLAYER LiveData 写入 null（`player.setValue(null)`），`observeForever(this::setPlayer)` 因而会以 null 回调。

调用点共 4 处（leanback）：`onNewIntent`（同方法内自行释放）、`resetDetailForNewIntent`、`beginPlayerContentRequest`（紧接其后重新置位，无净泄漏）、**`onRefresh`**。其中 `onRefresh` 的路径会真正漏掉：

```java
if (mViewModel != null) mViewModel.cancelPlayerContent();   // → setPlayer(null)
...
if (mFlagAdapter.getItemCount() == 0) return;               // 提前返回，未释放守卫
if (mEpisodeAdapter.getItemCount() == 0) return;
getPlayer(...);                                             // 只有走到这里才会重新置位
```

守卫残留后，`onStateChanged(STATE_READY)` 的 `break` 与 `hidePlaybackProgressIfStale()` 的同一条件同时为假 ⇒ 加载圈再无清除路径。

**修法**：把 null 结果视为「请求已作废、不会再有结果回调」的信号，在该分支释放两个守卫，再进行其余早退判断。这样覆盖全部 4 个调用点（含未来的新调用点），而不必逐个补。

## 遗漏点 ②（详情页）：`inlinePlaybackPending` 缺归属判定

上一步（`7809ff4952`）只补了「自增代际、不接替请求」的两处显式作废点。仍有两类作废路径**不自增代际**，因而无法显式释放，只能由回调自行释放——而回调的释放语句写在「是否仍是最新请求」检查**之后**：

- `applyTmdbResultNow()`：TMDB 详情异步装载完成 → 重排剧集 → 重设 `selectedEpisode`（可在 `playInline` 在途时发生）；
- `onResume → refreshSelectionAfterExternalPlayback()`：外部播放返回后重算选择面。

两者都会让在途回调因 `isInlinePlaybackRequestCurrent(...)` 为假而直接返回，标记再无释放路径。**后果**：加载圈永久留在屏上；且 `isSamePendingInlinePlayback()` 恒真 ⇒ `selectInlineEpisode` 早退，**同一集无法再次起播**。

**修法（归属判定）**：新增 `inlinePlaybackPendingGeneration` 记录置位该标记的请求代际；回调在失效分支改为 `releaseInlinePlaybackPending(generation)`，只释放属于自己的标记——这避免了「旧回调清掉新请求标记」的竞态（若无归属判定，无条件释放会误清新请求）。两处显式作废点（换内核/打开外部播放器）改用无条件重载 `releaseInlinePlaybackPending()`。

遗留：若任务在运行前被取消，回调根本不执行，因此显式作废点的同步释放**仍然必要**，二者互补而非替代。

## 改动摘要

| 文件 | 变更 |
| --- | --- |
| leanback `VideoActivity.java` | `setPlayer(null)` 分支释放守卫（+7 行，含注释） |
| `TmdbDetailActivity.java` | 新增归属字段、置位时记录代际、回调失效分支按归属释放、两处作废点改用统一 helper、新增两个 helper 方法 |
| `VideoActivityLayoutTest.java` | 新增断言：null 结果分支必须自行释放守卫 |
| `TmdbDetailDirectPlayTransitionSourceTest.java` | 扩展断言：两处作废点走 helper、置位记录代际、回调两个失效分支均按归属释放、归属重载存在且会比较代际 |

## 验证证据

全部命令在 `F:/Workspace/webtv2/webhtv`（dev2）执行，`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`，`--no-daemon`。

| 项目 | 命令 / 结果 |
| --- | --- |
| 双 flavor 主码编译 + 定向用例（一次调用） | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac :app:testMobileArm64_v8aDebugUnitTest --tests VideoActivityLayoutTest --tests TmdbDetailDirectPlayTransitionSourceTest --tests PlaybackOwnershipSourceTest` → **BUILD SUCCESSFUL in 3m 44s** |
| XML 权威计数 | `VideoActivityLayoutTest` **154/0/0**、`TmdbDetailDirectPlayTransitionSourceTest` **4/0/0**、`PlaybackOwnershipSourceTest` **16/0/0**；两个被改用例确实出现在结果中 |
| 新断言非空洞性 | 对 base 提交 `7809ff4952` 源码逐方法体字符串校验：leanback `setPlayer` 无独立 `if (result == null) {` 分支、无独立 `if (isFinishing()` 守卫；详情页三个方法/helper 均不含 `releaseInlinePlaybackPending*`，`playInline` 不含代际记录语句 ⇒ 新断言在修复前必然变红（确定性判断，未额外跑变异） |
| 过程中自查并修回 | 首轮批量编辑因内容漂移部分失败，且我误删了 `refreshAndSwitchInlinePlayer` 中的 `float speed = player().getSpeed();` 与一处空行；复核 `git diff` 时发现并全部修回（最终 diff 已确认无该两处副作用），随后才运行验证 |

## 已报告未修（本任务范围外，按计划继续）

1. 屏显诊断非 Exo 重缓冲计数恒为 0（`PlayerOsdController` 用 `Snapshot.empty()`）——下一项修复。
2. IJK/MPV 无 BUFFERING 停滞兜底（`PlayerManager` 三处 `isExo()` 把关）——下一项修复。
3. `canHideSeekProgress()` 在「暂停且引擎持续报 loading」时恒假：经查属**有意契约**（`C47SeekLoadingProgressSourceTest` 明确钉住该子句），根治依赖引擎状态复位，非代码遗漏。
4. 本机 CRLF 检出使若干带 `\n` 字面量的源级用例恒红（`ReaderPlaybackRoutingSourceTest` 2 项、`TmdbSourceOnlyInteractionTest` 1 项）：断言写法与 EOL 相关，属既有环境问题，不在本系列代码遗漏点内。

## 交付坐标

| 项 | 值 |
| --- | --- |
| 代码提交 | 由 `task_guard.sh finish` 创建（本文件同提交） |
| recovery tag | 由 `finish` 创建 |
| 推送 | **未推送**（未授权） |
