# C99-beta-merge-review-dev3-20261022

## Recovery anchor

- **目标**：把 `origin/beta@275c225edc`（PR #479 合入后）零冲突合入 `dev3`，全量评审 `dev3` 已提交未推送的两个提交
  `1bb216efec`（SITE-HEALTH-UI-01 报表弹窗安全区）与 `29733de98d`（DIALOG-SAFE-01 全屏弹窗安全区统一），
  修必修项并验证，修复后再次评审直至通过，然后提交、推送 `dev3`、创建到 `beta` 的中文 PR（只创建不合并），
  最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突；`beta` 带入的 31 个路径相对合并树无丢失；`beta` 新内容与本轮评审对象的路径交集为 0。
  2. 评审覆盖两个提交合并后的净差异（37 个路径）；`beta` 带入内容按「已评审 + 0 冲突 + 0 路径交集」跳过并留证，
     另做集成面抽查。
  3. 每个必修项均已修复且修复有可复核证据；修复后第二轮复评通过。
  4. 双 flavor 定向单测全绿（mobile 42 例 / leanback 27 例），两个 flavor 的 Java 源码在本批任务里编译通过；
     `git diff --check` 与文本冲突标记扫描为空。
  5. 本轮改动经任务守卫 `finish` 落盘并创建 recovery tag；推送 `dev3`；创建到 `beta` 的中文 PR（含具体改动内容）；
     随后拉取远端最新代码。
- **Lane**：`upstream`
- **Scope**：`app/`、`docs/`
- **任务守卫**：`C99-beta-merge-review-dev3-20261022`，base HEAD `39f44ccd1cf1360ce77314616f8be5b3d13397c7`（合并提交），
  branch `dev3`，受保护脏路径 22 条（未跟踪 `.design-evidence/`，保持不动）
- **当前状态**：合并、首轮评审、修复、验证、第二轮复评、提交、推送、PR、拉取均完成（交付坐标见 §7）
- **下一动作**：无；等待 PR 评审/合并（本轮只创建 PR，不合并）

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `275c225edc`（PR #479 合入后，2026-10-22 18:5x `git fetch origin --prune` 取得） |
| 本地 | `dev3` = `29733de98d`（SITE-HEALTH-UI-01 + DIALOG-SAFE-01 两个提交，已提交未推送，领先 `origin/dev3` 2 个提交） |
| merge-base | `fbcd47fbc1`（= `origin/dev3`，上一轮 C97 文档回填） |
| 合并提交 | `39f44ccd1c`（双亲 `29733de98d` / `275c225edc`） |
| 冲突 | 0（自动合并） |
| `dev3..beta` 提交 | 16 个 |
| `beta` 相对旧 tip `3203ca9da0` 的净差异 | 31 个路径 / +2321 −118 |
| 其中已在 `dev3` 内的 4 路径 | PR #477 把 `dev3` 自己的 `cae6e4b6e9` 反射回 `beta`：`HomeActivity`、`HomeFragmentCommitStateGuardTest`、`docs/CRASH-FRAGMENT-STATE-20261022.md`、`docs/C97-beta-merge-review-dev3-20261022.md`；`dev3` 侧内容相同，合并无实质变化 |
| 新进入 `dev3` 的内容 | 27 个路径（TMDB 详情性能、E-SP11 掉帧归因、C97-dev2/C98 文档、C16/TMDB-PERF 文档、评估索引） |
| 路径交集 | **0**（`beta` 新内容 27 路径 ∩ 本轮评审对象 37 路径 = 空集） |
| 丢失面 B | **0**（`beta` 树路径 − 合并树路径 = 0） |
| 新增面 A | **4**（合并树 − `beta` 树 = `DialogSafeArea.java`、`DialogSafeAreaTest.java`、`docs/DIALOG-SAFE-01-...md`、`docs/SITE-HEALTH-UI-01-...md`） |
| 合并副作用 | 无（两侧同内容路径由 `beta` 侧带着 PR #477 的提交进来，逐字相同） |

## 2. 评审范围与跳过依据

- **全量评审**：`dev3` 已提交未推送的两个提交，按**合并后的净差异**评审（`origin/dev3..29733de98d`，37 路径 / +547 −63）：
  - `1bb216efec`（SITE-HEALTH-UI-01）：`SiteHealthReportDialog` 安全区尺寸 + `ThemeDialogLayout` 注释 + 双端源契约测试 + 任务文档；
  - `29733de98d`（DIALOG-SAFE-01）：新增 `DialogSafeArea`，26 个 main 弹窗 + 2 个 flavor 弹窗改为安全区基准，
    并把 `SiteHealthReportDialog` 的内联 insets 逻辑收敛到共享 helper（前一个提交的实现已被重构，评审以净差异为准）。
- **跳过依据**：`beta` 新进入的 27 路径对应的代码提交，均已在其它分支的合并评审中全量评审并复评 ——
  TMDB 详情性能 5 个提交（`1a16fd163c`、`fa2f31812e`、`7fe44116aa`、`c9b2e769a8`、`67e1f6330a`）见
  `docs/C98-beta-merge-review-dev4-20261022.md`；E-SP11 掉帧归因 4 个提交（`b5d87c16b5`、`36f5ba4f8d`、`4ba8f431c0`、
  `14a45844fd`）及其修复 `ae923e1312` 见 `docs/C97-beta-merge-review-dev2-20261022.md` 与评估索引 checkpoint 64；
  PR #477 带入的是 `dev3` 自己已评审的 `cae6e4b6e9`。本轮合并 0 冲突、路径交集 0，满足「已评审且无合入冲突」跳过条件。
- **集成面抽查**（合并后现场复核，非重新全量评审）：
  - 两个 flavor 的 main/test Java 在 §5 的单测任务中编译通过，合并后符号与资源引用无断裂；
  - `beta` 新增/修改的测试类（`DiagnosticEventFieldAllowlistTest`、`TmdbDisambiguationDetailParityTest`、
    `TmdbDetailPrefetchWiringSourceTest`、`PlaybackPerformanceSettingSourceTest` 等）不引用 `ui/dialog` 的尺寸契约，
    与本轮改动无断言耦合；
  - 本轮 28 个弹窗路径与 `beta` 的 player/TMDB 路径无交集，合并不会改变本轮评审对象。

## 3. 首轮评审发现与处置

| 编号 | 严重度 | 位置 | 问题 | 处置 |
| --- | --- | --- | --- | --- |
| F1 | 中 | `docs/DIALOG-SAFE-01-...md` §风险与回滚 | 风险表述与实现不符：文档写「首帧退化为整块显示后由 `follow` 或下一次 `onStart` 收敛」，但 `follow` 只装在 `SiteHealthReportDialog` 与 `CacheManagementDialog` 两个弹窗上，其余 26 个**不会**事后重排；`onStart` 就是首次也是最后一次 sizing。照此表述，读者会误以为所有弹窗都具备自愈能力。 | 按实现重写该条：明确「三级 inset 源都读不到 → 回到改动前旧尺寸（不新增裁切、不自动收敛）」；写明 `follow` 的两处覆盖与「其余弹窗依赖 `onStart` 时宿主已 resume、根窗口 insets 可同步读取」的依据；把「给全仓 28 个弹窗装 listener 会放大布局回环与生命周期风险」记为有意取舍。 |
| F2 | 低 | `DialogSafeAreaTest` | 负向契约过窄：只拒绝 `params.height = (int) (ResUtil.getScreenHeight(` 这一种字面写法，同型缺陷的另外两类写法（内部滚动/列表上限 `Math.min(dp(x), ResUtil.getScreenHeight(...) * f)`、以及 flavor 里的 `metrics.heightPixels`）即使回归也不会被拦住；而本轮改造恰恰以「连内部上限一起改」为范围。 | 负向断言收紧为「这 26 个文件不得出现 `ResUtil.getScreenHeight(`」（逐文件 grep 确认当前均不出现，断言指向真实契约）；两个 flavor 的 `AdBlockStatsDialog` 另加「不得出现 `ResUtil.getScreenHeight(` 与 `heightPixels`」。宽度基准仍允许原始屏宽（导航栏在底部，横向挖孔风险按文档留待独立任务）。 |
| F3 | 低 | `docs/SITE-HEALTH-UI-01-...md` §后续建议 | 该节仍以「建议单独任务审计」列出 13 个同型弹窗，与已交付的 DIALOG-SAFE-01 冲突，属过期建议。 | 在该节补注「以上 13 个已由后续任务 `DIALOG-SAFE-01` 统一覆盖」并给出文档路径，保留历史语境、消除过期指令。 |

**复核未发现问题的项**（正面证据，非「没看」）：

- `DialogSafeArea` 的三级后备与 `1bb216efec` 的内联实现逐条等价：`systemBars() | displayCutout()`、bottom 取
  `max(systemBars, systemGestures)`、全 0 视为未知、宿主根窗口 → content 视图差集；因此报表弹窗几何无漂移，
  SITE-HEALTH-UI-01 记录的设备 frame `[28,70][1052,1802]`（1024×1732）仍然成立。
- `follow` 只在 insets 实际变化时重排（`WeakHashMap` 以 decorView 为键、值是不含视图引用的 record，无泄漏），
  不会与 `window.setLayout` 形成布局回环。
- 电视/沉浸态 insets=0 → `safeArea` = 整块显示，几何与改动前逐位相同；仅由屏宽算宽度的弹窗保持原值，
  与文档「明确不改」清单一致。
- 排除项逐一复核：`AboutDialog` 走「content 高度优先 + systemBars 扣减」；`OneKeySyncDialog` 是
  `BaseBottomSheetDialog`（Material 自管 insets）；`CodecCapabilityDialog`、`PlaybackPerformanceDialog`、
  `PlaybackPerformanceModal`、`RemoteTrustDialog`、`PanNetworkDiagnosticDialog`、`SiteHealthDialog`
  的原始屏宽只用于**宽度**，不会造成底部裁切。
- 两个 flavor 的 `AdBlockStatsDialog` 已无原始显示尺寸引用（无 `ResUtil.getScreen*`、无 `widthPixels/heightPixels`）。

## 4. 第二轮复评（修复后）

- **F1**：`grep -rl 'DialogSafeArea.follow'` 确认调用点为 2（`SiteHealthReportDialog`、`CacheManagementDialog`），
  文档已按此写明覆盖范围与「未安装即不事后收敛」的取舍，不再声称所有弹窗都会收敛；同时把缓存语义
  （`OBSERVED` 仅为 `follow` 提供上次结果、值不变不重排）写清，读者可据代码复核。
- **F2**：收紧后的断言在当前树上成立 —— 逐文件 grep 确认 26 个 main 弹窗内 `ResUtil.getScreenHeight(` 出现 0 次；
  两个 flavor 的 `AdBlockStatsDialog` 同样 0 次。断言覆盖面从「一种字面写法」扩到「整类缺陷」，且不再误报
  合法宽度用法。
- **F3**：SITE-HEALTH-UI-01 的 13 项后续建议已标注被 DIALOG-SAFE-01 覆盖，与 `docs/DIALOG-SAFE-01-...md`
  的「改动清单」一一对应（13 个文件全部在 26 个 main 弹窗清单内）。
- **回归面**：本轮修复只改测试契约与文档，生产代码与首轮评审对象逐字相同；定向单测在修复后重跑（§5）全绿。
- **结论：通过。**

## 5. 验证（2026-10-22 19:0x +08:00）

| 项 | 命令 | 结果 |
| --- | --- | --- |
| mobile 定向单测 | `bash ./gradlew --console=plain :app:testMobileArm64_v8aDebugUnitTest --tests '…DialogSafeAreaTest' --tests '…SiteHealthReportDialogSourceTest' --tests '…ThemeDialogLayoutTest' --tests '…SiteHealthReportSourceTest' --tests '…AdBlockStatsDialogLayoutTest' --tests '…AiRecommendationInfoDialogLayoutTest' --no-daemon` | **BUILD SUCCESSFUL in 3m 1s**；42 项 0 failure 0 error 0 skipped（DialogSafeArea 2 / SiteHealthReportDialogSource 3 / ThemeDialogLayout 11 / SiteHealthReportSource 11 / AdBlockStatsLayout 13 / AiRecommendationInfoDialogLayout 2） |
| leanback 定向单测 | `bash ./gradlew --console=plain :app:testLeanbackArm64_v8aDebugUnitTest --tests '…DialogSafeAreaTest' --tests '…SiteHealthReportDialogSourceTest' --tests '…ThemeDialogLayoutTest' --tests '…SiteHealthReportSourceTest' --no-daemon` | **BUILD SUCCESSFUL in 2m 25s**；27 项 0 failure 0 error 0 skipped（2 / 3 / 11 / 11） |
| 双 flavor Java 编译 | 上述两个测试任务内的 `:app:compileMobileArm64_v8aDebugJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | 均执行通过（非 up-to-date 跳过） |
| 差异卫生 | `git diff --check`、`app/`+`docs/` 文本冲突标记扫描 | 空（exit 0；标记扫描仅命中无关二进制 `libmpv.so`） |
| 合并完整性 | `comm -23 <beta 树> <合并树>` / `comm -13 …` | 丢失面 0；新增面 4（本轮自有文件） |

## 6. 回滚

- **本轮评审修复**：只回退测试契约与两份文档（评审提交），生产代码不动；相对合并提交的单次提交回滚即可。
- **整体回退到合并前**：`git reset --hard 29733de98d`（保留 `dev3` 自有两个提交），或先撤销合并提交
  `39f44ccd1c` 再处理推送历史。
- 无偏好写入、无数据库/二进制/schema/资源/字符串/依赖变更。

## 7. 交付坐标

- 合并提交：`39f44ccd1cf1360ce77314616f8be5b3d13397c7`（双亲 `29733de98d` / `275c225edc`）。
- 评审/修复提交：`631877c7d4869b070b835e46d0c6e1cefd3d2f49`（在合并提交之上，4 文件 +141 −6）。
- Recovery tag：`recovery/C99-beta-merge-review-dev3-20261022/20261022111136-631877c7d486`。
- 推送：`origin/dev3` `fbcd47fbc1..631877c7d4`。
- PR：[#480](https://github.com/Silent1566/webhtv/pull/480)（`dev3` → `beta`，中文描述含具体改动内容，只创建不合并）。
- 拉取：`git fetch origin --prune` + `git pull --ff-only origin dev3`，`dev3` 与 `origin/dev3` 同步（0/0）；
  `origin/beta` 仍为 `275c225edc`。
- 工作树：干净（受保护未跟踪目录 `.design-evidence/` 保持不动）；本节回填为文档单提交。
