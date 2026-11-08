# C97-beta-merge-review-dev2-20261022

## Recovery anchor

- **目标**：把 `origin/beta@3203ca9da0`（PR #476 合并提交，含 dev1/dev3 的 C95、C96 交付）合并进 `dev2`，复评 `dev2` 唯一的 4 个已提交未推送提交（E-SP11 帧调度/掉帧归因：`b5d87c16b5`、`36f5ba4f8d`、`4ba8f431c0`、`14a45844fd`，共 10 路径），修复必修项并验证，通过后再次评审直至通过，然后提交、推送 `dev2`、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突；`beta` 树相对合并树无路径丢失（B = 0）；无 `beta` 内容被复活/覆盖（A = 0）；合并树相对 `beta` 的内容净差恰为 `dev2` 自有 10 路径。
  2. 复评覆盖 4 个未推送提交的全部 10 路径（9 代码/测试 + 1 文档），逐路径给出可复核结论；先前 `docs/` 无任何评审文档覆盖这 4 个提交（`grep 14a45844fd|4ba8f431c0|36f5ba4f8d docs/*.md` 为空），因此不适用「已评审可跳过」。
  3. 必修项必须修复并留可证伪的验证证据，修复后第二轮评审确认通过。
  4. 合并结果双 flavor 编译通过；本轮改动相关单测全绿；全量单测相对合并前基线**零新增失败**；`git diff --check` 与冲突标记扫描为空。
  5. 本轮改动经任务守卫 `finish` 落盘并创建 recovery tag；推送 `dev2`；创建到 `beta` 的中文 PR（含具体改动内容）；随后拉取远端最新代码。
- **Lane**：`upstream`
- **Scope**：`app/`、`catvod/`、`docs/`
- **任务守卫**：`C97-beta-merge-dev2-20261022`，base HEAD `14a45844fd6700f3d3cb932d68e97cd127609a28`，branch `dev2`，受保护脏路径 127 条（全部在 `.wh_diag/` 下，未跟踪，保持不动）
- **当前状态**：合并 + 首轮评审 + 修复 + 验证 + 第二轮评审已完成；待 `finish` 提交/推送/PR/回填交付坐标
- **下一动作**：`task_guard.sh finish` 提交本轮改动，随后推送 `dev2` 并创建 PR

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `3203ca9da0`（`Merge pull request #476 from Silent1566/dev1`，2026-10-22 `git fetch --prune` 取得） |
| 本地 | `dev2` = `14a45844fd`（4 个自有未推送提交：E-SP11 文档 + 帧调度配置日志 + 掉帧归因证据 + tick 周期化修正） |
| merge-base | `b8fdbee6f6e8a16bb2236ee7b347b80fef79156f`（上一轮 C95 的文档回填提交） |
| 合并命令 | `git merge --no-ff --no-commit origin/beta` |
| 冲突 | **0**（`Automatic merge went well`，`git diff --diff-filter=U` 为空） |
| 合并树 | `0ad16cd14a2e68fd02d1b0b1436dbf88f61759fc` |

`beta` 本轮带入 10 个提交，净内容 32 文件（+2269 −123）：

| 完整 commit ID | 标题 | 处置 |
| --- | --- | --- |
| `3203ca9da0` | Merge pull request #476 from Silent1566/dev1 | 合并（PR #476 合并提交） |
| `d29f8563b3` | docs(C96): 回填交付坐标… | 合并（文档） |
| `41f55daa17` | merge+review(C96): 合并 origin/beta 4dc58561bb 并评审 AUTONEXT-02 自动连播修复 | 合并 |
| `da7ac6440a` | fix(auto-next): 自动连播有声音没画面 … | 合并（`PlaybackOwnershipKeys`、`VideoSurfaceBinding`、`PlaybackActivity`、`PlaybackService` 等） |
| `4dc58561bb` | Merge pull request #474 from Silent1566/dev2 | 合并（上一轮 dev2 交付） |
| `2f83884f21` | Merge pull request #475 from Silent1566/dev3 | 合并 |
| `5fa1a24256` | docs(C95): 回填交付坐标… | 合并（文档） |
| `c5ef7ec829` | merge+review(C95): 合并 origin/beta 590c424568 并评审崩溃日志地址功能，修复小屏崩溃界面不可滚动等 4 项 | 合并 |
| `30324abe36` | feat(crash): 崩溃界面改为可复制的局域网日志地址… | 合并（`CrashActivity`/`CrashLog*`/`InMemoryDiagnosticFile`/`DebugLogStore` 等） |
| `0c2d6d9ac6` | Merge remote-tracking branch 'origin/beta' into dev3 | 合并 |

## 2. 零复活 / 零丢失证据

| 判据 | 含义 | 结果 |
| --- | --- | --- |
| 路径数 | 合并树 / `beta` / `dev2`（合并前） | **5201 / 5201 / 5186** |
| **B** | `beta` 树 − 合并树（丢失面） | **0** |
| **A** | 合并树 − `beta` 树（复活/新增面） | **0**（`dev2` 的 10 路径全部是既有文件的修改，无新增路径） |
| 内容级净差 `beta` → 合并树 | 仅 `dev2` 自身改动 | **10 路径 / +463 −19**（9 代码/测试 + `docs/E-SP11-…md`） |
| 内容级净差 `dev2`（合并前）→ 合并树 | 仅 `beta` 本轮改动 | **32 路径 / +2269 −123** |
| 双方路径交集 | `dev2` 自有 10 路径 ∩ `beta` 本轮 32 路径 | **0**（无同文件双改，冲突面为空） |
| 冲突标记 / `git diff --check` | 合并残留 | 空 / 退出码 0 |

## 3. 复评范围与逐路径结论（首轮）

复评对象：E-SP11 的 4 个未推送提交，10 路径。`dev2` 与 `beta` 无同文件双改，复评对象与合并前逐字节一致。

| # | 路径 | 改动 | 首轮结论 |
| --- | --- | --- | --- |
| 1 | `player/PlaybackTelemetryMapper.java` | 遥测行追加 `renderedFpsScope=pts-cadence-estimate;not-throughput` | **通过**。该字段与 `renderedFps` 的算法同源（`ObservedVideoFrameRateEstimator` 的 PTS 间隔中位数），标注属实；行内新增字段位于 `renderedFps` 之后，仓内无按位置切分该行的消费者（`grep renderedFps` 仅生产方 + 契约测试） |
| 2 | `player/exo/ExoDiagnosticCollector.java` | `video.output.summary` 增 `droppedInputBuffers`/`skippedInputBuffers`/`droppedToKeyframe`；`sample()` 末尾调用 `logFrameTimingIfDue()` | **不通过 → 已修**（F1）：三个字段名不在 `DiagnosticEvent.FIELDS` 白名单内，`put()` 抛 `IllegalArgumentException`，`emit()` 捕获后计 `collectorFailure()` 并**丢弃整条事件** ⇒ 诊断开启时该行永久消失，正是本轮想增强的那行。修复见 §4 |
| 3 | `player/exo/ExoFrameSchedulingRendererSettings.java` | 新增 `describe()`：把 decision/队列/阈值拼成单行 | **通过**。`null` decision 用 `stableDecision(false,false,false)` 兜底（与 `from()` 的 null 分支同义）；`dynamicScheduling` 取自 `ExoFrameSchedulingPlayerSettings`，与实际 `builder.experimentalSetDynamicSchedulingEnabled(true)` 同源（`ExoUtil:307-309`），不存在「日志值与生效值分叉」 |
| 4 | `player/exo/ExoPlaybackDiagnostics.java` | 新增 `logFrameScheduling()`，`SpiderDebug.isEnabled()` 门控 | **通过**。与同文件既有 `exo-buffer` 行同为 `SpiderDebug.log`（无 trace id），与 build 阶段其它行一致；不新增采集路径 |
| 5 | `player/exo/ExoRuntimeAwareVideoRenderer.java` | 改为读 `ExoUtil.effectiveLateDropThresholdUs()`；删除本地 `isLateDropInputEnabled()` 分支与 `C`/`PlaybackPerformanceSetting` 导入 | **通过**。取值未变（开 = 15 000 µs，关 = `C.TIME_UNSET`）；两条构建路径现在只有一个取值口 |
| 6 | `player/exo/ExoUtil.java` | 新增 `effectiveLateDropThresholdUs()`；`buildRenderersFactory()` 统一取值并输出 `exo-frame` 行 | **通过**。`lateDropThresholdUs` 只算一次，`set` 与日志同值；`frameSchedulingSettings`/`frameSchedulingDecision` 均为该方法入参且已由 record 构造器保证非空 |
| 7 | `player/exo/PlaybackAnalyticsListener.java` | `onVideoFrameProcessingOffset` 的采集门槛改为「实验投放或诊断开启」；新增 `logFrameTimingIfDue()`（30 s 节流） | **通过（含两处已记录的可接受风险）**。①`lastFrameTimingLogMs` 是 volatile 非原子读改写，tick 线程与渲染器停止回调可能各打一行（重复不丢数据）；②`ExoFrameTimingMetrics` 各入口 `synchronized`，跨线程读快照安全；③`onVideoFrameProcessingOffset` 仅在 `MediaCodecVideoRenderer.onStopped()` 触发（fork 源码 `:1187` 是 `maybeNotifyVideoFrameProcessingOffset()` 唯一调用点），因此「停止时才交付」的判断属实，挂到既有 5 s tick 是必要修正 |
| 8 | `test/.../ExoFrameTimingMetricsTest.java` | 新增 `bucketsSeparateLateFramesFromEarlyFrames`（4 帧覆盖 4 个桶） | **通过**。断言 `1/1/1/0/1/0/0` 与 `SignedTimingBucketsAccumulator` 边界（≥ −20/0/20/50/75/100 ms）逐一对应 |
| 9 | `test/.../PlaybackPerformanceSettingSourceTest.java` | 改 1 例（两条路径只读共享取值口、渲染器不再自带 `isLateDropInputEnabled()`）+ 新增 4 例源契约 | **通过**。断言串与生产代码逐字匹配（含 `metricScope=settings-applied-only`、`unset` 渲染），并保留「关闭态必须传 `C.TIME_UNSET`」原锁 |
| 10 | `docs/E-SP11-exo-tv-frame-drop-defaults.md` | 新增 §16 强制评审、§17/§18 实施记录、§18.1 开销核对 | **通过（需补一条复盘）**。§18.1 的调用频率核对与 fork 源码一致；但 §18 的「零新增日志行」在 F1 存在时实际是「整行消失」，且验证只用了源码锁单测、没有任何测试真正构造 `DiagnosticEvent` ⇒ 已在 §19 追加复盘（见 §4.3） |

## 4. 首轮必修项与修复

### 4.1 F1（blocker）：三个新观测字段未进白名单，导致整条 `video.output.summary` 被丢弃

**证据链**

1. `DiagnosticEvent.put()` 对未登记字段直接抛异常：`if (!FIELDS.contains(name) || status == null) throw new IllegalArgumentException("Unknown diagnostic field/status")`（`DiagnosticEvent.java`）。
2. `PlaybackDiagnosticCollector.emit()` 用 `try { … facts.accept(event); DebugLogStore.event(event); } catch (RuntimeException ignored) { DebugLogStore.collectorFailure(); }` 吞掉异常 ⇒ 不是「少三个字段」，而是**整条事件不导出**，并在健康统计里累计 collectorFailure。
3. 机械比对生产方与白名单：`ExoDiagnosticCollector` 的 57 个 `observed("…")` 字面量中，`droppedInputBuffers`、`skippedInputBuffers`、`droppedToKeyframe` 三个不在 303 个白名单字段中（其余 54 个全部命中）。
4. 触发条件 = 诊断日志开启，正是 `ExoDiagnosticCollector` 唯一的运行条件 ⇒ 该行在所有诊断会话中都会消失，且 `sample()` 每 5 s 继续尝试、继续失败。

**为什么原验证没抓到**：原 4 个新契约测试都是「读源码断言字符串」，没有任何一个真正 `new DiagnosticEvent(...).observed(新字段, …)`；而 `:app:test*` 只编译 `catvod` 主源集，不执行其事件构造。

**修复**

| 改动 | 文件 | 说明 |
| --- | --- | --- |
| 白名单补 3 个字段 | `catvod/.../diagnostics/DiagnosticEvent.java` | `"droppedInputBuffers", "skippedInputBuffers", "droppedToKeyframe"` 追加在既有 `droppedBuffers`/`skippedBuffers` 之后；纯增量，不改任何既有字段语义，不升 `schemaVersion` |
| 新增跨模块契约锁 | `app/src/test/.../exo/DiagnosticEventFieldAllowlistTest.java` | ①扫描 `ExoDiagnosticCollector.java` 的全部 `observed("…")` 字面量，逐个用真实 `DiagnosticEvent` 构造验证白名单接受；②断言三个输入侧字段仍在生产代码里（防测试变空转）；③自证非空转：未登记字段名必须抛 `IllegalArgumentException` |

**行为影响**：无播放行为变化；只是让原本被丢弃的 `video.output.summary` 真正导出（含既有字段）。

### 4.2 修复后第二轮评审

| 检查 | 结论 |
| --- | --- |
| 白名单增量的兼容性 | **通过**。新增字段只扩大可接受集合，既有事件的 JSON 不变；`schemaVersion` 不必升（无字段语义变化） |
| 新测试是否会空转 | **通过**。`theAllowlistCheckIsNotVacuous` 用 `assertThrows` 证明夹具真的走到校验；`everyFieldTheExoCollectorRecordsIsAcceptedByTheSchema` 先断言 3 个字段仍在采集端源码中 |
| 新测试的路径/运行环境 | **通过**。沿用仓内既有 `sourcePath()` 双重回退（Gradle `app/` 工作目录与 IDE 仓库根），本轮 Gradle 实跑 2 例全绿 |
| 是否引入新的生产代码路径 | **通过**。仅 `catvod` 白名单 + 1 个测试文件，无生产逻辑改动 |
| 首轮其余 9 路径 | **无回退**，结论维持 §3 |

**结论：第二轮评审通过，必修项为 0。**

### 4.3 已报告但不属于本轮范围（未修）

| ID | 级别 | 问题 | 证据 | 建议 |
| --- | --- | --- | --- | --- |
| R2 | low | `PlaybackAutoContext.logSummary()` 的 `renderedFps=` 没有同步补 `renderedFpsScope`，与 §3#1 已修的那行同源同值（都来自 `PlaybackTelemetryMapper.map()`），读者仍可能把 58.8 当吞吐 | `PlaybackAutoContext.java:734`；`PlaybackTelemetryMapper.java:14` | 单独小任务补标签 + 契约锁 |
| R3 | medium | `:catvod:testDebugUnitTest` 编译失败：`DebugLogStoreTest` 仍引用已被 `cf209ec021 fix: remove debug log retention cap` 删除的 `trimLines`/`readTail` | `git show 14a45844fd:catvod/.../DebugLogStore.java`（合并前）已无这两个方法 ⇒ 与本次合并无关 | 删除/改写该陈旧测试，恢复模块门禁 |
| R4 | medium | `ExoDiagnosticCodecAdapter` 的 `video.candidates` 事件使用未登记的 `snapshotId`、`queryCount`，同样会被 `put()` 抛错整条丢弃 | `ExoDiagnosticCodecAdapter.java:116-137`；两字段均不在 `FIELDS`；该文件未被 `dev2` 或 `beta` 改动（`git diff` 为空） | 后续任务补白名单，并把契约锁扩到全仓 `observed("…")` |
| R5 | low | `ExoFrameTimingMetrics.averageOffsetUs()` 用整数除法换算 ms，−0.4 ms 会打印 0 | `PlaybackAnalyticsListener.java`（`avgOffsetMs` 计算） | 需要更细粒度时改用亚毫秒或定点输出 |

## 5. 验证证据

| # | 命令 | 结果 |
| --- | --- | --- |
| 1 | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` + `:app:compileMobileArm64_v8aDebugJavaWithJavac` + `:app:testLeanbackArm64_v8aDebugUnitTest --tests '*DiagnosticEventFieldAllowlistTest*' --tests '*PlaybackPerformanceSettingSourceTest*' --tests '*ExoFrameTimingMetricsTest*' --tests '*tv.player.exo.*'` | **BUILD SUCCESSFUL**（6m26s）；**633 例 / 1 skipped / 0 失败**（含新增 2 例）；日志 `build/c97/verify-1.log` |
| 2 | `:app:testLeanbackArm64_v8aDebugUnitTest`（全量） | **4492 例 / 3 skipped / 8 失败 / 0 error**；8 例失败分布在 7 个类 |
| 3 | 合并前基线 `14a45844fd`（临时 worktree，跑同样 7 个失败类） | **69 例 / 8 失败**，失败测试名与第 2 项**逐条相同**（`diff` 无差异）⇒ **本轮零新增失败** |
| 4 | `:catvod:testDebugUnitTest` | **编译失败**：`DebugLogStoreTest` 引用已被删除的方法（R3，合并前同样失败） |

失败 8 例的归因（与本次改动无交集）：

- `MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`：Windows 上 `File.renameTo` 不能覆盖已存在目标 ⇒ 平台限制（mpv 代码未被本次合并触碰）。
- `NativeEnhancedPlaybackStyleFocusTest`、`ReaderPlaybackRoutingSourceTest`（2 例）、`SearchResultDownFocusTest`、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`：源码契约断言已过期，其断言的源码文件（`VideoActivity`、`reader.html`、`CollectActivity`、`TmdbDetailActivity`、`ui.css` 等）在本次合并中**逐字节未变**（已逐个用 `git diff` 核对），因此与合并无关。

## 6. 交付坐标

- 合并+评审提交：`ae923e13125c536f06f940409661fead798e1dfe`（双亲 `14a45844fd` / `3203ca9da0`）
- recovery tag：`recovery/C97-beta-merge-dev2-20261022/20261022083445-ae923e13125c`
- 推送：`dev2`（`b8fdbee6f6..ae923e1312`）
- PR：到 `beta` 的 **#478**（中文描述，含 §3/§4 的具体改动）
- 拉取远端最新：`git fetch origin --prune` 已执行；`origin/beta` 自 `3203ca9da0` 前进到 `d72134c5aa`（PR #477 / dev3 的 C97：首页配置回调事务守卫，4 路径），与本轮改动**路径交集为 0** ⇒ PR #478 无冲突面；按本仓既有节奏，该 5 个提交在下一轮任务开头随 `origin/beta` 一并合入 `dev2`。
