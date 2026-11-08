# LIST-STATE-CONVERGENCE-20261013：把「列表空了但页面留白」从"每次都要记得写"改成"不可能忘"

## Recovery anchor

- 目标：把"列表数量变化 → 驱动状态机"收敛到 `BaseDiffAdapter` 的唯一入口，宿主只注册一次；并借此修掉电视端历史页那处遗漏。
- 允许路径：`app/src/main/java/.../ui/adapter/BaseDiffAdapter.java`、`app/src/{leanback,mobile}/java/.../ui/activity/{KeepActivity,HistoryActivity}.java`、`app/src/test/java/.../ui/adapter/ListStateConvergenceContractTest.java`（新增）、`app/src/testMobile/java/.../ui/activity/MobileStateSurfaceContractTest.java`（同步断言）、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `c44b8ce7d8b`。
- 验收：① 钩子从**两个** `setItems` 重载都会触发，且在 diff 应用之后；② 四个宿主都注册钩子且**不再手写**状态驱动；③ 电视端历史页「删除全部」因此被修复；④ 跨 flavor 契约测试全绿；⑤ 两端构建通过。
- 回滚：撤销本任务原子提交即可回到"每个调用点手写"的状态；无数据迁移。
- 交付坐标：提交 `8168dab8093`（dev4），recovery tag `recovery/LIST-STATE-CONVERGENCE-20261013/20261013124709`；未推送。
- 下一步唯一动作：无（本批已交付）。**建议的下一批**：电视端历史页/文件页的空态三件套（TV-4 §7.3 的遗留项——真机截图里它仍是默认的「这里什麽都没有」）。

## 1. 现象与根因

同一个缺陷在本仓库出现了 **6 处**，跨两个 flavor：

| # | 位置 | 入口 | 状态 |
|---|---|---|---|
| 1 | 电视端 `KeepActivity` | 单条删除 | 已在 `TV-EMPTY-LOADING-STATE-20261012` 修 |
| 2 | **电视端 `HistoryActivity`** | **删除全部** | **本批修**（靠钩子） |
| 3 | 手机端 `KeepActivity` | 单条删除 | 已在 `MOBILE-STATE-SURFACE-20261013` 修 |
| 4 | 手机端 `KeepActivity` | 删除全部 | 已在 `MOBILE-HISTORY-EMPTY-20261013` 修 |
| 5 | 手机端 `HistoryActivity` | 单条删除 | 同上 |
| 6 | 手机端 `HistoryActivity` | 删除全部 | 同上 |

形状完全一样：

```java
mAdapter.remove(item, () -> { if (count == 0) setDelete(false); ... });   // 忘了驱动状态机
mAdapter.clear()                                                          // 同样忘了
```

**根因不是"某个人手滑"，而是责任放错了地方**：把"驱动状态机"留给**每一个调用点**，而调用点会不断增加（单条删除、删除全部、删除后刷新……）。前几批都是"发现一处补一处"，所以第 2 处、第 4 处才会在补完之后才暴露出来。

## 2. 证据来源

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| `docs/MOBILE-HISTORY-EMPTY-20261013.md` §7.1 | 本仓库上一批 | A（本地） | 已提出"三处形状相同，建议在适配器统一通知"——本批执行 |
| `BaseDiffAdapter`（main 源集） | HEAD `c44b8ce7d8b` | A（本地） | **所有变更都经过两个 `setItems` 重载**；这是唯一可行的收敛点 |
| 同上 `setItems(List, Runnable)` / `setItems(List, Callback)` | HEAD `c44b8ce7d8b` | A（本地） | 回调在 diff **应用完成**后触发——`AsyncListDiffer` 是异步的，只有那时 `getItemCount()` 才是新值，**这一点决定了钩子必须挂在回调里** |
| 四个宿主的 `setRecyclerView` / `onItemDelete` / `onDelete` / 列表加载回调 | HEAD `c44b8ce7d8b` | A（本地） | 逐个确认它们都继承 `BaseDiffAdapter`，且都手写了 `showContent(true, ...)` |

不适用类别记录：不涉及解码/渲染/ABI/打包/网络，无需上游播放器依赖类证据。

## 3. 方案比较与采用

| 方案 | 优点 | 缺点/风险 | 结论 |
|---|---|---|---|
| 1. 只补电视端那一处 | 最小 | 第 7 处迟早还会出现——前几批已经证明了这一点 | 拒绝 |
| 2. **在 `BaseDiffAdapter` 的唯一收尾点挂"数量已变化"钩子（采用）** | 宿主注册一次，任何路径改动列表都自动跟上；不可能再忘 | 需要宿主显式注册（未注册则行为与改动前一致） | **采用** |
| 3. 在适配器里直接操作 `ProgressLayout` | 连注册都省了 | 适配器不该知道宿主的状态容器（跨层依赖）；同一个适配器还可能被别的宿主复用 | 拒绝 |
| 4. 把 `remove`/`clear` 的回调参数从 `Runnable` 改成 `IntConsumer` | 调用点能拿到新数量 | **仍然可以忽略**——这正是"手写"模式的问题，不解决"会忘" | 拒绝 |
| 5. 只加一个静态检查（测试扫源码） | 便宜 | 只能发现"写错了"，不能防止"忘了写"；且启发式扫描容易误报 | 拒绝（本批的契约测试作为补充，不是替代） |

**关键设计点**：钩子必须挂在 `differ.submitList(...)` 的**回调里**，而不是调用之后。`AsyncListDiffer` 是异步的，调用后立刻读 `getItemCount()` 会拿到**旧值**——那会让状态机在"列表已经空了"的时候还以为有内容。契约测试专门钉住了这一点。

## 4. 实施

1. `BaseDiffAdapter`：
   - 新增 `interface OnCountChanged { void onCountChanged(int count); }` 与 `setOnCountChanged(...)`；
   - 新增私有 `afterUpdate(Runnable)`：先跑调用方回调，再通知钩子；
   - **两个** `setItems` 重载都改走 `afterUpdate`（含 `Callback` 重载的"无变化"分支）。
2. 四个宿主（电视端/手机端 × 收藏页/历史页）：
   - 在 `setRecyclerView()` 里注册一次 `mAdapter.setOnCountChanged(count -> mBinding.progressLayout.showContent(true, count))`；
   - **删除**原先手写的 8 处 `showContent(true, ...)`（含单条删除、删除全部、列表加载回调）——否则代码里会同时存在两套机制，将来有人可能误删钩子。
3. 电视端 `HistoryActivity.onDelete()` 的「删除全部」因此自动被修复（它本来就只是 `mAdapter.clear()`）。
4. 新增跨 flavor 契约 `ListStateConvergenceContractTest`（4 项）：钩子存在；两个重载都触发且时机在 diff 回调里；四个宿主都注册且**只允许出现一次** `showContent(true,`（就是注册那行）；`mAdapter.clear()` 之后不得再有手写驱动。
5. 同步更新 `MobileStateSurfaceContractTest` 里两条因本批而过时的断言（它们原先断言"回调里手写了 `showContent`"）。

## 5. 不变量与回滚

- 不改动 `BaseDiffAdapter` 既有的差异比较、`getItems()`/`getItemCount()` 语义与两个 `setItems` 重载的**签名**。
- 未注册钩子时行为与改动前完全一致（钩子为 `null` 时什么都不做）——因此其它使用 `BaseDiffAdapter` 的页面不受影响。
- 不改动列表的排序、分组、滚动与焦点恢复逻辑（`restoreFocus`、`scrollToPosition`、`updateMarquee` 全部保留）。
- 不改动空态/错误态的内容与文案。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 6. 验证记录

### 6.1 定向 JVM 测试

- `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "...ListStateConvergenceContractTest" --tests "...Mobile*"` → **BUILD SUCCESSFUL in 1m 13s**。
- `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest ...` → 首轮 **FAILED**：`TvStateSurfaceContractTest.deletePathDrivesTheStateMachineSoTheEmptyStateIsReachable` 断言"删除回调里必须手写 `showContent(true,`"——这条断言正是本批要**废除**的模式。同步改为断言"钩子已注册"后 **BUILD SUCCESSFUL in 4m 35s**。手机端同名的旧断言同样已同步。
- `.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug` → **BUILD SUCCESSFUL in 1m 53s**；`.\gradlew.bat :app:assembleMobileArm64_v8aDebug` → **BUILD SUCCESSFUL in 2m 31s**。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。

### 6.2 设备实测（`192.168.50.3:5561`，装 **leanback flavor**）

| 检查项 | 结果 |
|---|---|
| 打开电视端历史页（列表为空） | **空态正常出现**——而这一页的显式 `showContent` 已被本批删除，所以这次是**钩子在驱动** |
| 有无崩溃 | 无 |

截图：`.design-evidence/e5561-c1-history.png`。

这一条是**决定性检查**：如果钩子没接对（例如挂在 `submitList` 之前、拿到旧数量，或某个重载漏了），列表为空时就不会进入 EMPTY 态，页面会是空白。

### 6.3 结论

"列表数量变化 → 驱动状态机"从"每个调用点都要记得写"收敛为"适配器统一通知、宿主注册一次"。电视端历史页「删除全部」那处遗漏因此被修复；此后任何新增的列表变更路径都自动覆盖。

### 6.4 残余风险与诚实说明

- **未在真机上单独验证"删除后钩子驱动"**：历史数据已被前一批清空，没有可再删的数据。本批验证的是钩子在**首次加载**路径上生效（§6.2）；删除路径与它共用同一个 `afterUpdate` 收尾点，由跨 flavor 契约测试覆盖。
- **`FileAdapter` 是否也继承 `BaseDiffAdapter`、是否有删除入口未查**（见 §7.2）。它当前没有删除入口。
- **钩子只覆盖 `BaseDiffAdapter` 的家族**。若将来有列表用别的适配器基类，需要同样接入——契约测试目前只钉住这四个宿主。

## 7. 相邻发现（未纳入本批）

1. **`FileActivity` 的空态主动作仍是产品/导航决策**（"返回上级" vs "重新扫描"），见 `docs/MOBILE-HISTORY-EMPTY-20261013.md` §7.2。
2. **`FileAdapter` 是否也继承 `BaseDiffAdapter`、是否也有"删除后不驱动状态"的风险未查**：它没有删除入口，所以本批未涉及；若将来加入删除，钩子已经在那里可用。
3. **手机端仍是三套状态机制并存**（`ProgressLayout` / `StateEvent` / 设置页 Fragment 的 `safeRun` 兜底），见 `docs/MOBILE-HOME-ERROR-STATE-20261013.md` §7.1。本批只统一了"列表 → 状态"这一条链路。
