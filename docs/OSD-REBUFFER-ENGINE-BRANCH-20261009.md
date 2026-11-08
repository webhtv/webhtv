# OSD-REBUFFER-ENGINE-BRANCH-20261009：屏显诊断的重缓冲计数按引擎正确分派

## Recovery anchor

- **目标**：修复 `PlayerOsdController.getDiagnostics()` 中重缓冲计数的引擎分派遗漏——原先只对 MPV 分支，导致 IJK 落到 Exo 快照分支、读数恒为 0；以源级用例锁定。
- **验收标准**：① 非 Exo 一律读引擎无关取数器；② 相关用例零失败；③ 净差异仅本任务文件与本文档；④ 提交 + recovery tag。
- **lane / scope**：`quick-fix`；`app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java`、`app/src/test/java/com/fongmi/android/tv/setting/PlayerDisplaySettingSyncTest.java`、`docs/`。
- **branch / 基线 HEAD**：`dev2`；`d7b9439201cef70c9229b98c7356e0a7397103eb`。
- **受保护脏路径**：0。
- **回滚锚点**：`d7b9439201`。
- **当前状态**：已实施，验证通过；提交与 tag 由 `task_guard finish` 创建。
- **下一动作**：处理最后一项遗漏（IJK/MPV 的 BUFFERING 停滞兜底）。

## 遗漏点

```java
PlaybackAnalyticsListener.Snapshot snapshot = player.isExo() ? …getSnapshot() : Snapshot.empty();
…
// MPV has no Exo analytics snapshot; its buffering tracker belongs to PlayerManager.
int rebufferCount = player.isMpv() ? player.getRebufferCount() : snapshot.rebufferCount();
long rebufferTotalMs = player.isMpv() ? player.getRebufferTotalMs() : snapshot.rebufferTotalMs();
```

分派条件写成了「是否 MPV」，而真实前提是「有没有 Exo analytics 快照」（即是否 Exo）。因此 **IJK** 落到快照分支，读到 `Snapshot.empty()` ⇒ **重缓冲次数恒为 0、总时长恒为 0**。

`PlayerManager.getRebufferCount()` / `getRebufferTotalMs()` 本身是引擎无关的（`PlaybackBufferingTracker` + MPV disc 计数），所以非 Exo 早就有可用取数器，只是 IJK 没走到。

**影响**：这不是播放缺陷，而是**排查工具读数错误**——本系列排查「转圈」时，我就曾据此建议看「重缓冲次数」，对 IJK 无效（已在任务文档中更正）。修掉它可让诊断面板对三种内核都可信。

## 改动

```java
int rebufferCount = player.isExo() ? snapshot.rebufferCount() : player.getRebufferCount();
long rebufferTotalMs = player.isExo() ? snapshot.rebufferTotalMs() : player.getRebufferTotalMs();
```

Exo 行为完全不变（仍读快照）；IJK/MPV 读引擎无关追踪器。

## 验证证据

全部命令在 `F:/Workspace/webtv2/webhtv`（dev2）执行，`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`，`--no-daemon`。

| 项目 | 命令 / 结果 |
| --- | --- |
| 编译 + 定向用例（一次调用） | `:app:compileMobileArm64_v8aDebugJavaWithJavac :app:testMobileArm64_v8aDebugUnitTest --tests PlayerDisplaySettingSyncTest --tests VideoActivityLayoutTest --tests TmdbDetailActivityLayoutTest` → **BUILD SUCCESSFUL in 1m 20s** |
| XML 权威计数 | `PlayerDisplaySettingSyncTest` **7/0/0**（含新增用例，原 6 项）、`VideoActivityLayoutTest` **154/0/0**、`TmdbDetailActivityLayoutTest` **130/0/0**；新用例 `playerOsdDiagnosticsReadRebufferCountPerEngine` 确认出现在结果中 |
| 新断言非空洞性 | 对 base 提交 `d7b9439201` 源码校验：`int rebufferCount = player.isMpv() ? player.getRebufferCount() : snapshot.rebufferCount()`，不含 `player.isExo()` ⇒ 新断言在修复前必然变红 |
| 过程中自查并修回 | 新增用例时我误把方法签名与首行拼成一行（同类失误在本系列第二次出现），复核 `git diff` 时发现并修回；最终 diff 无该副作用 |

## 交付坐标

| 项 | 值 |
| --- | --- |
| 代码提交 | 由 `task_guard.sh finish` 创建（本文件同提交） |
| recovery tag | 由 `finish` 创建 |
| 推送 | **未推送**（未授权） |
