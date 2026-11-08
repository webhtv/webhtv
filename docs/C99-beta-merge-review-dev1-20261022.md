# C99-beta-merge-review-dev1-20261022

## Recovery anchor

- **目标**：把 `origin/beta@275c225edc` 合并进 `dev1`（零冲突、零丢失），逐行评审 `dev1` 仅有的 2 个已提交未推送提交（`4c00f2dae8` 视轨焦点/选中分离、`e90de26e63` 电视端内容区铺满），修复必修项并定向验证，复评通过后提交、推送 `dev1`、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突；`beta` 带入路径与 `dev1` 自有改动的路径交集为 0。
  2. 评审覆盖 `4c00f2dae8`（8 路径）与 `e90de26e63`（2 路径）的全部 10 个路径；`beta` 带入内容按「已评审 + 0 冲突 + 0 路径交集」跳过并留证。
  3. 每个必修项修复可复核；修复后第二轮复评通过。
  4. 定向单测（`TrackUtilTest`/`TvContentInsetContractTest`/`TvFocusStatePairContrastTest`/`TvFocusRingContractTest`/`DialogGlassChipStyleTest`）与双 flavor arm64 Debug 单测编译一次通过。
  5. 本轮改动经任务守卫 `finish` 落盘并创建 recovery tag；推送 `dev1`；创建到 `beta` 的中文 PR；随后拉取远端最新代码。
- **Lane**：`standard`
- **Scope**：`app/src`、`catvod/src`、`docs`、`README.md`
- **任务守卫**：`C99-BETA-MERGE-DEV1-20261022`，branch `dev1`，base HEAD `e90de26e630b83ca85c84423a6029e2949bfc45a`，受保护脏路径 0
- **当前状态**：合并、评审、修复、验证、复评、提交、推送、PR、拉取均已完成；工作树干净
- **下一动作**：无；等待 PR 评审/合并（本轮只创建 PR，不合并）

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `275c225edc`（PR #476 已把 dev1 的 C96 合入 beta；此后 #477/#478/#479 合入 dev2/dev3/dev4 的 C97/C98） |
| 本地 | `dev1` = `e90de26e63`（领先 `origin/dev1` 2 个提交：`4c00f2dae8` TV-TRACK-FOCUS-01、`e90de26e63` TV-INSET-01，均未推送、未评审） |
| merge-base | `d29f8563b3c0b4f57ab51237d5b2a54ae66c1d10` |
| 带入提交 | 22 个，含 dev2/dev3/dev4 的 C97/C98 合并+复评+文档回填、E-SP11 掉帧归因与帧调度、TMDB 详情性能 5 个提交、首页配置回调事务守卫，以及 #477/#478/#479 三个 PR 合并提交 |
| 冲突 | **0**（`git merge --no-ff --no-commit origin/beta` 自动合并，`git diff --diff-filter=U` 为空） |
| 带入路径 | **31** 个（`app/src` 26、`catvod/src` 1、`docs` 8） |
| 路径交集 | **0**（带入 31 路径 ∩ 本地 10 路径 = 空集） |
| 丢失面 | 0（合并树相对 `beta` 只多出本地 10 路径 + 本任务文档） |

## 2. 评审范围与跳过依据

- **全量评审**：`dev1` 相对 `beta` 的唯一内容差异，即 2 个未推送提交的 10 个路径：
  - `4c00f2dae8`：`TrackUtil.java`（新增 `uniqueActiveFormat`/`sameRendition`）、`CustomRecyclerView.java`（焦点重试）、`TrackDialog.java`（按正在解码格式收敛选中）、`selector_dialog_glass_chip.xml`/`selector_dialog_glass_chip_text.xml`（焦点分支独立）、新增 `shape_dialog_glass_chip_focused.xml`、`TrackUtilTest.java`、`DialogGlassChipStyleTest.java`。
  - `e90de26e63`：`leanback/.../BaseActivity.java`（`bindFullWindowContent`）、新增 `TvContentInsetContractTest.java`。
- **跳过**：`beta` 带入的 C97/C98/E-SP11/TMDB 内容均已在对应 dev2/dev3/dev4 的 C97/C98 评审文档中评审；本轮合并 0 冲突、路径交集 0，满足「已评审 + 无合入冲突」跳过条件。仅抽查集成面：`catvod` `DiagnosticEvent` 白名单与本次 TV 改动无交集；`E-SP11` 帧调度/Renderer 与 `TrackUtil` 的 `Format` 判定无耦合；`HomeActivity` 事务守卫不影响 `BaseActivity#bindFullWindowContent` 的 inset 路径。

## 3. 首轮评审发现与处置

| 编号 | 严重度 | 位置 | 问题 | 处置 |
| --- | --- | --- | --- | --- |
| R1 | low | `TvContentInsetContractTest#sourceOfMethod` | 从调用点 `bindFullWindowContent();` 起找首个 `{`，实际先撞到 javadoc 里的 `{@link Util#hideSystemUI}`，返回的是注释片段；于是 `theInsetConsumerNeverTurnsInsetsIntoPadding` 的三条否定断言对着空串空转，守卫形同虚设（未来给 decor 加 `setPadding` 不会红） | 改为从方法声明 `void bindFullWindowContent(` 起切，并从声明处返回；同时加正向锚点（必须切到含 `setDecorFitsSystemWindows` 的方法体），使否定断言非空转 |
| R2 | low | `selector_dialog_glass_chip_text.xml` 注释 | 注释称黑字压 `#FF1B45B8` 约 3.2:1；按 WCAG 2.x 实算 ≈ **2.59:1**（`#1B45B8` 相对亮度 0.0794）。结论方向正确，数字失实 | 改为「约 2.6:1」 |

**记录但未修**：

- **O1**：新焦点外观用固定高饱和蓝（`#FF1B45B8`）作填充，与本仓库「焦点＝半透明浅色叠层 + 环」的通用语言（`docs/TV-FOCUS-PAIR-20261021.md`）不同；但该 chip 家族宿主是固定深色玻璃、选中态是固定浅灰，若焦点再用半透明浅层会与「选中」在同一明度带内不易区分，正是本次用户报告的缺陷形态。判定为**有理由的家族内例外**，不修；环宽仍取唯一 token，文字/环对比度由测试锁定。
- **O2**：`bindFullWindowContent` 让所有 leanback 页面内容区从 900 变 1080（模拟器 1920x1080、底部 overscan 90）。对「系统报 overscan 且物理裁掉底部」的电视，播放页底部控件可能落入被裁区。当前取舍与用户「铺满整块窗口、消掉 180px 黑带」的诉求一致，且电视端各页面自带安全边距；本轮无设备可复核物理裁切，按已报告缺陷的方向保留，风险留档。
- **O3**：`BaseActivity` 消费所有 inset 不覆盖弹层窗口（`BottomSheetDialog` 独立 window），`Util.showKeyboard` 的 3 个调用点全部在 BottomSheetDialog 内；leanback 页面内容区无系统 IME 输入框，故「消费 IME inset」当前无可见影响。
- **O4**：两个 TV 提交是 `quick-fix` lane 提交、无各自任务文档；其评审记录并入本文件。

## 4. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| leanback 定向单测 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests TrackUtilTest --tests TvContentInsetContractTest --tests TvFocusStatePairContrastTest --tests TvFocusRingContractTest` | BUILD SUCCESSFUL；44 例全绿（10 / 3 / 4 / 27，0 失败 0 错误 0 跳过） |
| mobile 定向单测 | `:app:testMobileArm64_v8aDebugUnitTest --tests DialogGlassChipStyleTest` | BUILD SUCCESSFUL；6 例全绿 |
| 差异卫生 | `git diff --check` | 空 |

## 5. 第二轮复评（验证后）

复评对象为 R1/R2 修复后的最终代码：

- `sourceOfMethod` 现在从声明处返回完整方法体，正向锚点证明切到的片段含 `setDecorFitsSystemWindows` 与声明文本；否定断言（无 `setPadding`/无 bottom inset 取值）因此真正有效，且测试仍绿。
- 注释比值与实算一致（2.59:1，取「约 2.6:1」）；文字色、环色、填充色的真实对比度由 `DialogGlassChipStyleTest` 的合成计算断言保证（焦点文字 ≥4.5:1、环 ≥3:1）。
- 复核 2 个提交的其余路径，无新增必修项：`uniqueActiveFormat` 只在「同组多选」时收敛、匹配不上回退原生标记；`isSelected` 按引用比对避免同规格双高亮；`CustomRecyclerView` 重试 250ms 封顶且列表内已有焦点即停；焦点背景/文字选择器顺序一致（焦点 > 选中 > 默认，禁用最前）；`BaseActivity` 只在 leanback 基类生效，mobile 基类由测试锁住不得复用。

结论：**通过**，无遗留必修项。

## 6. 交付坐标

- 合并提交：`d6b5d4cf4f0dc357c11da422a681851ce608951c`（双亲 `e90de26e630b83ca85c84423a6029e2949bfc45a` + `275c225edc`）。
- recovery tag：`recovery/C99-BETA-MERGE-DEV1-20261022/20261022111807-d6b5d4cf4f0d`（本地保留，不推送）。
- 推送：`dev1`（`d29f8563b3..d6b5d4cf4f`），`origin/dev1` 与本地一致。
- PR：**#481** <https://github.com/Silent1566/webhtv/pull/481>（`base=beta`/`head=dev1`/`OPEN`/`mergeable=MERGEABLE`/`mergeStateStatus=CLEAN`，只创建不合并）。
- 拉取：`git fetch origin --prune` 后 `origin/beta` 前进到 `b9c39f85ec`（他人新推进的 beta 更新留在下一轮合并）；`git pull --ff-only origin dev1` 为 up to date。
