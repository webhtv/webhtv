# C29：dev2 同步远端 beta 并复评未推送改动（搜索空详情保留播放页修复）

## Recovery anchor

- **目标**：确认远端 `beta` 最新代码已合入本地 `dev2`（远端已移除/回退的提交不得顺带带上来）；复评 `dev2` 相对 `origin/beta` 的全部已修改代码（含已提交未推送）；发现问题则最小修复并验证；循环复评通过后提交本任务改动、推送 `dev2`、创建中文 PR 到 `beta`（只创建不合并）。
- **验收**：`dev2` 包含 `origin/beta` 全部提交且未带入任何被回退内容；净差异仅含本分支自身修复；定向测试通过；PR 描述用中文说明改动内容并保持排版清楚。
- **允许路径**：`docs/C29-beta-merge-review-dev2-20261001.md`（本任务只新增该文档；代码经两轮复评未发现需要修改的问题）。
- **分支/HEAD**：`dev2`；任务开始时 HEAD = `0a1ca102e15a0566c2bbd8685d360f0b439fa0cc`，工作区干净。
- **当前状态**：同步核对、两轮复评、定向验证均完成，无需要修改的问题，进入提交、推送与 PR。
- **下一动作**：任务守卫收尾提交本文档并创建恢复标签，推送 `dev2`，创建 `dev2 -> beta` 中文 PR（只创建不合并）。

## 时间与设备

- 当前本地时间：2026-10-01 17:40 开始（Asia/Shanghai）。
- 本任务纯仓库侧评审与验证，未使用模拟器设备。

## 同步核对台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始 HEAD | `0a1ca102e15a0566c2bbd8685d360f0b439fa0cc` |
| 远端 `origin/beta` HEAD | `bbe7062f423c8e8152057c36744f59d849705675`（Merge PR #388） |
| 远端 `origin/dev2` HEAD | `8cd77ba46e24a564442217d933c2451e16b6ed6c` |
| 合并基点 | `bbe7062f423c8e8152057c36744f59d849705675`（即 origin/beta HEAD） |
| `dev2..origin/beta` | 0（远端 beta 无任何 dev2 缺失提交，无需执行 merge） |

- **远端 beta 最新代码已完整包含于本地 dev2**：`git rev-list --count dev2..origin/beta` 为 0，merge-base 等于 `origin/beta` HEAD。远端自上一轮 C28 后仅新增 `8cd77ba46e`（PR #388 合并的 dev2 提交），该提交本身就在 dev2 历史中，无需再次合并动作。
- **远端已移除/回退的提交未被顺带带上来（决定性核对）**：
  - `origin/beta..dev2` 只有 1 条：`0a1ca102e1`（搜索空详情保留播放页修复）。任何“远端已移除但 dev2 仍保留”的提交都会出现在该列表，实测不存在。
  - 上一轮 C28 记录的回退坐标 `5682f2b054`（剔除 PR #353 主题系统改动）经 `git merge-base --is-ancestor` 判定既不是 dev2 也不是 origin/beta 的祖先；beta 上的 revert 提交（`8325175e63`、`0616a992f6`、`70306f1dec`、`be1b02e06b` 等）均为 origin/beta 祖先，dev2 侧净差异不含其回退的对象内容。
  - 净差异内容级核对：`git diff origin/beta dev2` 不含 dynamic theme、ad segment verification、decode mode、MPV HLS segments、LUT warmup 等任何被回退功能的标记。

## 本轮待复评的改动（已提交未推送）

`origin/beta..dev2` 净差异 2 个文件（+34/−1），全部来自提交 `0a1ca102e1`（`fix(tv): keep failed search detail on playback page`，上一任务 tv-search-result-empty-detail 的交付物）：

| 文件 | 改动 |
| --- | --- |
| `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` | `setEmpty(boolean finish)` 由 `if (isFromCollect() \|\| finish)` 改为 `if (finish)` |
| `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/VideoActivityDetailShellSourceTest.java` | 新增源码契约测试（33 行） |

问题背景：搜索结果点击（`VideoActivity.collect(...)` 均带 `collect=true`）进入播放页后，若站源详情接口返回空，旧实现因 `isFromCollect()` 直接 `finish()`，用户看到“播放页闪退回搜索页”，把“详情接口无数据”错误表现为“页面导航退回”。

## 评审记录

### 第 1 轮：改动本体语义 + 调用面穷举

- `setEmpty(boolean finish)` 语义核对：
  - `finish=true`（`setDetail` 中 `result.hasMsg()` 为真，即站源 detail 接口明确报错）：仍然 `finish()` 关闭播放页，与旧行为一致，未削弱错误处理。
  - `finish=false` 且 `getName()` 为空：`showEmpty()` 展示 ProgressLayout 空态，与旧非收藏路径一致。
  - `finish=false` 且 name 非空：保留播放页、`mBinding.name.setText(getName())` 显示标题、`App.post(mR4, 10000)` 十秒兜底转空态、`checkSearch(false)` 走既有自动换源重试路径。这正是本修复的目的行为。
- `isFromCollect()` 调用面穷举（改动后该方法无生产调用点，仅残留定义）：
  - `collect=true` 的唯一生产来源是 `VideoActivity.collect(...)` 家族，调用方为 leanback `CollectActivity.onItemClick` 与 `CollectFragment`（搜索结果/收藏列表点击），与改动目的场景精确重合。
  - 其余全部启动入口（`startDirect*`、`startFromHistory`、`startFromResolvedHistory`、push、cast）一律 `collect=false`，行为不变。
  - `git grep isFromCollect` 全仓核对：leanback 侧定义已无引用；mobile 侧见第 2 轮记录。
- 防循环核对：`checkSearch(false)` 要求 `isAutoMode()` 才 `nextSite()`；`nextSite()` 内 `mQuickAdapter.getItemCount() == 0` 直接返回；`mismatch()` 以 `getId()` 相等与 `mBroken` 列表双重过滤，`nextSite()` 把失败 id 加入 `mBroken`，搜索结果逐源收窄，无死循环风险——与旧非收藏路径共用同一套既有机制，未引入新循环。
- `setEmpty(false)` 的另一调用点 `checkId()`（`msearch:` 前缀/空 id 防御分支）：`"msearch:"` 在生产代码中无写入者（全仓只有这两处 `startsWith` 防御判断），纯防御分支，无行为影响。
- `CatAction.shouldYieldDetail` 提前收页、`mPendingDetail` 挂起、`recordDetailHealth` 上报等相邻逻辑均在 `setEmpty` 之前独立执行，与本改动无交集。
- **第 1 轮结论**：leanback 侧改动语义正确、与既有自动换源机制自洽，未发现必须修改的问题。

### 第 2 轮：测试有效性 + 交付边界 + 跨 flavor 一致性

- **测试有效性核对**（源码契约测试需人工确认防回归能力）：
  - `emptyDetailFromSearchKeepsPlaybackPageForRetry` 三段断言：要求 `setEmpty` 保留独立方法体且仍含 `if (finish) {`（防“空详情一律不关页”的过度修复）、断言不含 `isFromCollect() || finish`（把旧写法写回即失败）、要求 `mBinding.name.setText(getName());` 与 `checkSearch(false);` 同时存在（防“只保留页面不进重试”的半修复）。写回任意一种旧形态都会使测试失败，断言具备防回归能力。
  - 特征串唯一性实测：`mBinding.name.setText(getName());` 在文件中 3 处、`checkSearch(false);` 2 处，但断言以 `setEmpty` 方法体为界切片后只在切片内匹配，切片边界（`\n    private void showEmpty()`）实测命中当前实现，无跨方法误匹配。
  - 路径回退：`src/leanback/...` 不存在时回退 `app/src/leanback/...`，与 Gradle 单测工作目录（模块 `app/`）匹配；实际运行为证据。
  - 无其他测试断言 `setEmpty` 旧结构：`grep -rln "setEmpty\|isFromCollect"` 在 testLeanback/testMobile 中只命中本测试文件；leanback 侧另一读源测试 `NativeEnhancedPlaybackStyleFocusTest` 不触及该方法。
- **交付边界**：`git diff --name-only origin/beta dev2` 只有上表 2 个文件；`git diff --check origin/beta dev2` 通过；无构建产物、无设备临时文件混入。
- **跨 flavor 一致性（本任务只记录，不修改）**：mobile `VideoActivity.setEmpty` 仍保留 `isFromCollect() || finish` 旧逻辑，且 mobile 搜索结果点击同样经 `CollectFragment` 以 `collect=true` 进入。即“空详情被踢回搜索页”在 mobile 端依然存在。但本提交作用域为 `fix(tv)`（用户报告与日志均来自电视端），把修复扩展到 mobile 会超出“评审已修改代码”的授权范围并改变 PR 内容；按“范围默认关闭、缺陷先报告”约定记录于此，留待独立任务决策。
- **第 2 轮结论**：测试断言有效、交付边界干净、无需要修改的问题，改动可交付。

## 验证记录

- 定向单元测试（合并后代码、工作区干净状态）：`bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.activity.VideoActivityDetailShellSourceTest"` → `BUILD SUCCESSFUL in 2m 23s`（99 tasks）。
- `git diff --check origin/beta dev2` 通过，无空白错误。
- 本任务无代码改动，无需打包/装机冒烟；上一任务已对同一 HEAD 做过 `gradlew clean` 与产物回收。

## PR 边界

相对 `origin/beta`，`dev2` 净差异为 2 个文件、34 行新增、1 行删除，单提交 `0a1ca102e15a0566c2bbd8685d360f0b439fa0cc`：

- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`（1 行）
- `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/VideoActivityDetailShellSourceTest.java`（新增）

PR 内容为该提交加本任务文档提交。

## 回滚锚点

- 任务开始 HEAD：`0a1ca102e15a0566c2bbd8685d360f0b439fa0cc`
- 远端 `beta`：`bbe7062f423c8e8152057c36744f59d849705675`
- 远端 `origin/dev2`：`8cd77ba46e24a564442217d933c2451e16b6ed6c`
- 上一轮 dev2 交付记录：`docs/C28-beta-merge-review-dev2-20260930.md`
- 回滚方式：revert 单提交 `0a1ca102e1` 即可恢复旧的收藏入口收页行为。

## 状态与下一步

- 同步核对、两轮复评、定向验证均已完成，无需要修改的问题。
- 下一步：提交本任务文档并创建恢复标签，推送 `dev2`，创建 base=`beta`、head=`dev2` 的中文 PR；只创建 PR，不执行合并。
