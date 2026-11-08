# C96-beta-merge-review-dev1-20261022

## Recovery anchor

- **目标**：把 `origin/beta@4dc58561bb` 合并进 `dev1`（零冲突、零丢失），评审 `dev1` 唯一已提交未推送的 `da7ac6440a`（AUTONEXT-02 自动连播「有声音没画面」），修复必修项并验证，复评通过后提交、推送 `dev1`、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突；`beta` 带入路径与 `dev1` 自有改动的路径交集为 0。
  2. 评审覆盖 `da7ac6440a` 的全部 9 个路径；`beta` 带入内容按「已评审 + 0 冲突 + 0 路径交集」跳过并留证。
  3. 每个必修项修复可复核；修复后第二轮复评通过。
  4. 定向单测（`PlaybackOwnershipKeysTest` / `SurfaceRebindDiagnosticTest` / `VideoSurfaceBindingTest` / `PlaybackOwnershipSourceTest`）与双 flavor arm64 Debug Java 编译一次通过。
  5. 本轮改动经任务守卫 `finish` 落盘并创建 recovery tag；推送 `dev1`；创建到 `beta` 的中文 PR；随后拉取远端最新代码。
- **Lane**：`standard`
- **Scope**：`app/src`、`catvod/src`、`docs`、`README.md`
- **任务守卫**：`C96-BETA-MERGE-DEV1-20261022`，branch `dev1`，base HEAD `da7ac6440a9d1da247bcabdb4f2ed291bf29001c`，受保护脏路径 0
- **当前状态**：合并、评审、修复、验证、复评、提交、推送、PR、拉取均已完成；工作树干净
- **下一动作**：无；等待 PR 评审/合并（本轮只创建 PR，不合并）

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `4dc58561bb`（PR #474 / #475 合入后，`git fetch origin --prune` 取得，另带入 tag `recovery/beta-sync-review-dev4-20260913/...`） |
| 本地 | `dev1` = `da7ac6440a`（AUTONEXT-02 修复，已提交未推送，领先 `origin/dev1` 10 个提交） |
| merge-base | `590c424568d5d1eaa713fe8a06c6c57cd013f42c` |
| 带入提交 | 9 个：`30324abe36` 崩溃日志地址、`ae0c82e7b6` 全屏菜单键「下一集」、`0c2d6d9ac6`/`573c8e5114`/`b8fdbee6f6`/`c5ef7ec829`/`5fa1a24256`/`2f83884f21`/`4dc58561bb`（dev2/dev3 的合并+复评+文档回填与两个 PR 合并提交） |
| 冲突 | 0（自动合并，`--no-ff --no-commit`） |
| 带入路径 | 25 个：崩溃功能（`CrashReport`/`CrashLogSession`/`CrashLogText`/`CrashActivity`/两套 `activity_crash.xml`/`DebugLogs`/`DebugLogStore`/`InMemoryDiagnosticFile`/三语 `strings.xml`/4 个测试）、全屏菜单键（`Setting`/leanback `VideoActivity`/`TmdbDetailActivity`/`FullscreenMenuKeyTest`）、`Startup`、C95 文档与评估索引 |
| 路径交集 | **0**（`beta` 带入 25 路径 ∩ `dev1` 自有 9 路径 = 空集） |
| 丢失面 | 0（合并树相对 `beta` 只多出 `dev1` 自有 9 路径及其文档） |

## 2. 评审范围与跳过依据

- **全量评审**：`da7ac6440a` 的 9 个路径 —— 新增 `PlaybackOwnershipKeys`、`VideoSurfaceBinding`，改 `PlaybackActivity`、`PlaybackService`、`SurfaceDiagnosticCollector`，新增/改 3 个测试，新增 `docs/AUTONEXT-02-episode-next-video-surface.md`。
- **跳过**：`beta` 带入的 C95 内容（`30324abe36` 崩溃日志地址、`ae0c82e7b6` 全屏菜单键下一集）已分别在 `docs/C95-beta-merge-review-dev2-20261021.md` 与 `docs/C95-beta-merge-review-dev3-20261021.md` 完成复评；本轮合并 0 冲突、路径交集 0，满足「已评审 + 无合入冲突」跳过条件。仅抽查集成面：`Setting.getFullscreenMenuKey()` 的 `[0,2]` 钳制不触碰切集/归属链路；`CrashReport.install` 只在 `Startup` 早于播放器初始化，不改变播放会话 key。

## 3. 首轮评审发现与处置

| 编号 | 严重度 | 位置 | 问题 | 处置 |
| --- | --- | --- | --- | --- |
| A1 | high | `SurfaceDiagnosticCollector#rebind` ↔ `catvod` `DiagnosticEvent` | 新事件 `surface.rebind` 与 6 个字段（`detachedFirst`/`hadPreviousPlayer`/`surfaceViewPresent`/`surfaceViewAttached`/`surfaceViewShown`/`surfaceWidth`/`surfaceHeight`）都不在 catvod 诊断白名单；`DiagnosticEvent` 构造/写入会抛 `IllegalArgumentException`，被 `PlaybackDiagnosticCollector.emit` 的 `catch (RuntimeException)` 吞成 `collectorFailure`。即：本轮新增的「重绑证据」永远拿不到，还会把采集器记成故障——恰与修复目标相反 | 登记 `surface.rebind` 事件与 `detachedFirst`/`surfaceViewPresent` 字段；视图状态复用已登记字段 `attached`/`shown`/`width`/`height`/`surfaceId`/`valid`；去掉与 `detachedFirst` 恒等的冗余参数 `hadPreviousPlayer`；把事实写入抽成静态纯函数 `rebindFacts(...)` 并新增 `SurfaceRebindDiagnosticTest`（正向断言事件名/字段名全部可写入，负向断言白名单确实会拒绝未登记名字） |
| A2 | high | `PlaybackActivity#ensureVideoSurfaceBound` | 补绑没有运行面/状态约束：①后台或返回栈里的旧播放页同样收到服务侧状态回调，在那里把共享引擎绑到自己的 `PlayerView` 会把引擎从用户正在看的页面抢走（又是一次有声无画）；②引擎尚未就绪时 `resolve(current, null)` 走「解绑」分支，兜底路径反而会在 BUFFERING 中途卸掉视图 | 只允许 `Lifecycle.RESUMED` 的页面执行；先做 `manager == null || manager.isReleased()` 空/失效判定；`nextPlayer == null` 直接返回，兜底路径只绑不卸 |
| A3 | medium | `PlaybackOwnershipKeys` | 公开了 4 个生产零调用方法（`isMismatch`/`flagOfEpisode`/`indexOfEpisode`/`displayLabel`），并由自测「自证」；解析里为一次 size 检查整表收集分隔符位置再丢弃 | 收敛为 `sessionKeyOf`/`sameSession`（`historyKeyOfEpisode` 留包内供测试），删除投机 API 与 `List` 分配，解析改为两次 `lastIndexOf`；同步收敛测试 |
| A4 | low | `VideoSurfaceBinding`、`PlaybackActivity` javadoc | 文档口径是「实例身份」，实现用 `Objects.equals`；`{@link #onPlayerRebuild}`、`{@link PlaybackActivity#attachSurface(boolean)}` 指向不存在/私有成员，javadoc 编译必报未解析 | 判定改为 `current == next` 并注释缘由；两处 javadoc 改为 `{@code ...}` |

**记录但未修**：

- **O1**：修复前提（日志里 `owner=false` → `onPlayerRebuild` 跳过 → 引擎未绑 Surface）本轮仍无设备端「自动连播 → 出画」端到端复现；可证伪性由 key 矩阵、`VideoSurfaceBinding` 分支矩阵与源码契约测试承担，`docs/AUTONEXT-02-...md` 已如实标注。
- **O2**：线路名含 `|` 时选集键只能从右侧裁两段，`sessionKeyOf` 会把线路名首段并入记录键；该边界已由 `recordKeyContainingSeparatorIsStillReversible` 固化，属可接受限制。
- **O3**：`:catvod:testDebugUnitTest` 编译失败（`DebugLogStoreTest` 引用早已移除的 `DebugLogStore.trimLines`/`readTail`）在 merge-base `590c424568` 与 `origin/beta` 上**同样存在**，非本轮引入、与本轮改动无关；按范围纪律不修，仅记录。

## 4. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 定向单测 | `:app:testMobileArm64_v8aDebugUnitTest --tests PlaybackOwnershipKeysTest --tests SurfaceRebindDiagnosticTest --tests VideoSurfaceBindingTest --tests PlaybackOwnershipSourceTest` | 38 例全部通过（10 / 2 / 6 / 20，0 失败 0 错误 0 跳过） |
| 双 flavor 编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac` | 同一命令内 `BUILD SUCCESSFUL in 1m 8s` |
| 差异卫生 | `git diff --check`、冲突标记扫描 | 空 |

## 5. 第二轮复评（验证后）

复评对象为 A1–A4 修复后的最终代码，逐项核对：

- `surface.rebind` 事件与全部字段均可在 `DiagnosticEvent` 中构造并 `json()`（测试实测），白名单负向用例确认守卫非空；
- 补绑路径有 RESUMED、manager 失效、引擎空三重前置，且不再存在解绑分支；`attachSurface` 原有语义（含 render 切换与 ASS/字幕会话）未被改动；
- `PlaybackOwnershipKeys` 生产面只剩 `sameSession` 两个调用点（`PlaybackActivity#isOwner`、`PlaybackService#isNavigationOwner`），语义与旧 `equals` 在「expected 为空=不限制、actual 为空=判否」上完全等价，仅新增「选集键归一化」这一条必要放宽；
- `VideoSurfaceBinding` 判定与文档口径一致，5 个分支矩阵测试未削弱；
- 新增源契约测试锁住「服务侧归属比较」「选集键前缀跨包同值」「补绑只在 RESUMED 且绝不解绑」。

结论：**通过**，无遗留必修项。

## 6. 交付坐标

- 合并提交：`41f55daa17d272b0a51602a9998df1956a862d5d`（双亲 `da7ac6440a9d1da247bcabdb4f2ed291bf29001c` + `4dc58561bb`）。
- recovery tag：`recovery/C96-BETA-MERGE-DEV1-20261022/20261022041245-41f55daa17d2`（本地保留，不推送）。
- 推送：`dev1`（`225d2bbcce..41f55daa17`）。
- PR：**#476** <https://github.com/Silent1566/webhtv/pull/476>（`base=beta`/`head=dev1`/`OPEN`/`mergeable=MERGEABLE`/`mergeStateStatus=CLEAN`，只创建不合并）。
- 拉取：`git fetch origin --prune` + `git pull --ff-only origin dev1` 为 up to date；`origin/beta` 仍为 `4dc58561bb`。
- 相对 `beta` 的唯一非合并内容差异：`docs/C95-beta-merge-review-dev3-20261021.md` 末尾多余空行被移除（任务守卫空白门要求，无内容变更）。
