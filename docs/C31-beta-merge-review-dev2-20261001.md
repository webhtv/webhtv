# C31：dev2 合并远端 beta 最新代码并复评未推送改动（站点批量动作遥控可达）

## Recovery anchor

- **目标**：将远端 `beta` 最新代码（`8ba23ac144`，Merge PR #391）合入本地 `dev2`，确认未顺带带回任何远端已移除/回退的提交内容；复评 `dev2` 相对 `origin/beta` 的全部已修改代码（含已提交未推送的 `2bf29bc4c8`）；循环复评通过后提交本任务改动、推送 `dev2`、创建中文 PR 到 `beta`（只创建不合并）。
- **验收**：`dev2` 包含 `origin/beta` 全部提交且未带回任何被回退内容（负向核对）；净差异仅含本分支自身改动；定向单测通过；PR 描述用中文、排版清楚。
- **允许路径**：`app/src`（评审修复）与本文档；合并产生路径由 `git merge origin/beta` 决定。
- **分支/HEAD**：任务开始 `dev2` HEAD = `2bf29bc4c84ed50e751da88cb212b93f8a371a99`（领先 origin/dev2 一个提交），工作区干净。
- **当前状态**：合并完成（`1b04e7a33b`，无冲突）、四轮评审完成（未发现问题，无需修复）、定向单测通过，准备提交推送与创建 PR。
- **守卫时序说明**：本任务首步就是合并 beta，守卫在合并前启动（base_head=`2bf29bc4c8`）；合并提交即本任务交付物，故合并后立即将守卫 base_head 同步为合并提交 `1b04e7a33b`（与 C28/C30 同场景先例一致：合并提交本身不带 Task-Guard trailer，守卫会话只覆盖评审修复与文档提交）。
- **下一动作**：任务守卫收尾提交本文档，创建恢复标签，推送 `dev2`，创建 `dev2 -> beta` 中文 PR（只创建不合并）。

## 时间与设备

- 当前本地时间：2026-10-01 23:00 开始（Asia/Shanghai）；评审与验证约 30~45 分钟。
- 本任务纯仓库侧合并、评审与单测验证，未使用模拟器设备（D-pad 行为已由既有 `SiteDialogActionFocusTest` Robolectric 测试在 PR 中的之前任务以模拟器实测覆盖，本任务只做代码评审与合并树复跑）。

## 同步核对台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始 HEAD | `2bf29bc4c84ed50e751da88cb212b93f8a371a99` |
| 远端 `origin/beta` HEAD | `8ba23ac144`（Merge PR #391 from dev2） |
| 远端 `origin/dev2` HEAD | `de66627174`（dev2 分叉基点之上三提交） |
| beta 新增提交（相对本地起点） | `8ba23ac144`（PR #391 合并）、`de66627174`（C30 注释同步）、`14556212b1`（C30 beta 合并）、`b2e1174d76`（首页菜单追更/站点注入两项） |
| 合并结果 | `1b04e7a33b` = merge `2bf29bc4c8` × `8ba23ac144`，无冲突 |
| `HEAD..origin/beta` | 0（远端 beta 无任何 dev2 缺失提交） |

- **远端已移除/回退的提交未被顺带带上来（决定性核对）**：
  - 合并前 `HEAD..origin/beta` = 4，合并后 = 0；`origin/beta..HEAD` 只有本分支自有提交 `2bf29bc4c8` + 合并提交本身。
  - 上一轮记录的回退坐标 `5682f2b054`（剔除 PR #353 主题系统改动）经 `git merge-base --is-ancestor` 判定**不是**合并后 HEAD 的祖先。
  - 负向内容核对：`HistoryProgressFormatter.java` / `HistoryProgressFormatterTest.java` 合并后仍不存在；净差异不含 `history_watched_time` 生产代码、dynamic theme / AdSegmentVerifier / DecodeMode / LUT warmup / HlsSegment 等任何被回退功能标记。
  - 结构性决定论核对：合并后 dev2 树相对 beta 的净差异只有本分支 2 个文件（见下），**不可能**夹带 beta 不存在的内容。

## 合并带入内容核对

合并相对第一父（`2bf29bc4c8`）带入 9 个文件 +163/−9，与 beta 侧（`890934423d..8ba23ac144`）逐字节完全一致（`diff` 比对两条 diff 输出 IDENTICAL），无丢失、无夹带：

- `HomeActivity.java`（菜单键 javadoc 1..11）
- `HomeMenuDialog.java`（`GRID_COUNT` 行数注释、Listener javadoc 1..11）
- `dialog_home_menu.xml`（`tools:itemCount` 11）
- `Setting.java`（`getHomeMenuKey()` clamp 11）
- 三个 `strings.xml`（`select_home_menu_key` 追更页面/站点注入两项）
- `HomeMenuDialogSourceTest.java`（用例 +2 项与断言更新）
- `docs/C30-beta-merge-review-dev2-20261001.md`（dev2 任务文档）

带入内容即上一轮 C30 的已推送交付物（PR #391），dev2 侧为首次接收。

## 本轮待复评的改动（已提交未推送）

`origin/beta..dev2` 净差异为单提交 `2bf29bc4c8`（`fix(tv): keep site bulk actions reachable by remote`）：TV 站源弹窗「选项」模式下，纯切换模式（type=0）下「全选」「取消全选」两个批量按钮原本被 `setEnabled(enabled && type > 0)` 移出 D-pad 焦点链，遥控下焦点从「切换」直接跳过两个按钮；修复为 enabled 只反映加载状态，点击资格改由 `setType()` 的 `setClickable(type > 0)` 控制。

| 文件 | 改动 |
| --- | --- |
| `SiteDialog.java` | `setActionEnabled()` 中 `select`/`cancel` 两行 `setEnabled(enabled && type > 0)` → `setEnabled(enabled)`，附两行设计注释 |
| `SiteDialogActionFocusTest.java`（新增） | 3 个 Robolectric 用例：焦点链保持可达（type=0 下 change→select→cancel→select→change）、点击资格仍需 search/change 模式（type=0 不可点、type=1/2 可点）、加载态全按钮禁用 |

## 评审记录

### 第 1 轮：改动本体语义 + 调用面穷举

- **设计意图核对**：原实现用 `enabled` 同时承担「加载态」与「模式资格」两个正交语义，导致 type=0 下按钮被 `enabled=false` 移出焦点链。修复将两语义解耦：`enabled` 只反映 `listLoaded && adapter != null`（加载态），模式资格由 `setType()` 已有的 `setClickable(type > 0)` 控制。语义正交、无重叠。
- **调用面穷举**（`setActionEnabled` 4 个调用点）：
  - `initShellView():171` 传 false → 弹窗壳层构建期全按钮禁用（含 select/cancel），焦点不会落上；与修复前一致。
  - `loadList():202` 传 true → 列表加载完成后全按钮启用；type=0 下 select/cancel 可聚焦但不可点，正确。
  - `setType():271` 传 `listLoaded && adapter != null` → 模式切换时刷新；type=0 时 select/cancel enabled=true、clickable=false。
  - `setActionEnabled` 定义处（`SiteDialog.java:289`）。
- **时序核对**：leanback `show()` → `showDirect()` 中 `initEvent()`（`setOnClickListener` 隐含 clickable=true）先于 `loadList()` → `setType()` 执行，`setType` 的 `setClickable(type > 0)` 覆盖监听器注册时的隐含 true，type=0 下确认键无法触发 `performClick` → `selectAll()/cancelAll()` 不会误执行。fragment `onCreateDialog` 路径同构（`initView` → `loadList(true)`）。
- **点击资格挡板验证**：Android `View.performClick()` 前置 `isClickable()` 检查；`OnTouchListener`/`KEYCODE_DPAD_CENTER` → `performClick` 路径均被 `clickable=false` 挡住。
- **焦点链几何**：`dialog_site.xml` 显式 `nextFocusUp/Down` 链 config→search→change→select→cancel→mode；`mode` 由 `setMode()` `setEnabled(false)` 移出链尾，`cancel.focusSearch(FOCUS_UP)` 依 nextFocusUp 回 `select`。测试断言与布局链一致。
- **视觉核对**：`site_action_icon.xml` 与 `selector_site_action.xml` 均无 `state_enabled` 条目（仅 focused/pressed/selected），enabled 变化不影响外观，type=0 下按钮与其它按钮视觉一致，无「灰显」暗示可点的误导。
- **`SiteDialog.create()` 反射注入核对**：`binding` 为私有字段（`SiteDialog.java:56`），`ReflectionHelpers.setField` 注入合法；`setType`/`setActionEnabled` 均只依赖 `binding` 与字段，不触库、不触网，测试隔离注释与实现一致。
- **`action` 标志核对**：`initShellView()` 仅在 `action=true` 时显示五个按钮，`search()`（type=1）与 `action()`（true）两个入口独立；`HomeActivity:1113`/`SettingActivity:207`（action 模式）、`SearchActivity:402`（search 模式）不受影响。测试未设置 `action` 字段而直接 `binding.action.setVisibility(VISIBLE)`，走纯 UI 焦点链断言，不依赖 shell 初始化，隔离合理。
- **第 1 轮结论**：语义正确，无问题。

### 第 2 轮：合并树定向单测验证

- 合并后在 dev2 树（`1b04e7a33b`）复跑两个定向测试源集：`bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "...SiteDialogActionFocusTest" --tests "...HomeMenuDialogSourceTest"` → **BUILD SUCCESSFUL**（7m32s，99 tasks）。
- 测试报告：`SiteDialogActionFocusTest` tests=3 failures=0 errors=0；`HomeMenuDialogSourceTest` tests=5 failures=0 errors=0。
- `HomeMenuDialogSourceTest` 是 beta 带入的菜单契约测试，合并树复跑通过确认 C30 交付与本分支改动无交叉冲突。
- 过时假设复扫（`type > 0`、`enabled && type`）：全仓仅剩 `setType()` 两行正当 `setClickable` 控制，无其它残留。
- `git diff --check origin/beta HEAD` 通过。
- **第 2 轮结论**：无新问题。

### 第 3 轮：全量净差异逐 hunk 复查

- `git diff origin/beta HEAD` 全量逐 hunk 复查：2 文件 +113/−2，全部是本次焦点可达修复与新增测试，无越权改动、无夹带。
- 被剔除提交（5682f2b05）文件清单与本净差异**零交集**，无主题系统内容混入风险。
- **第 3 轮结论**：无新问题。

### 第 4 轮：不回退核对 + 回滚锚点

- `git rev-list --count HEAD..origin/beta` = 0。
- 回退坐标 `5682f2b054` 非合并后 HEAD 祖先；`HistoryProgressFormatter` 文件在 HEAD 树中不存在（`git ls-tree -r` 计数 0）。
- 守卫 base_head 已同步为合并提交 `1b04e7a33b`（C28/C30 先例），守卫会话只覆盖评审修复与文档提交。
- **第 4 轮结论**：评审通过，可交付。

## 验证记录

- 合并后 `git rev-list --count HEAD..origin/beta` = 0；合并带入 diff 与 beta 侧逐字节一致。
- 定向单测 `SiteDialogActionFocusTest`（3 用例）与 `HomeMenuDialogSourceTest`（5 用例）于合并树上 BUILD SUCCESSFUL，0 失败 0 错误。
- `git diff --check origin/beta HEAD` 无空白错误。
- 本任务改动为焦点链语义修复 + 契约测试，已由 Robolectric 焦点链测试覆盖；无需装机冒烟。

## PR 边界

相对 `origin/beta`，`dev2` 净差异 2 个文件、+113/−2，两个部分：

1. 合并 `origin/beta`（`8ba23ac144`，含 PR #391 的 C30 菜单注释同步交付），带入内容与 beta 完全一致。
2. dev2 自有提交 `2bf29bc4c8`：TV 站源弹窗选项模式下批量按钮的 D-pad 焦点可达修复 + 3 个 Robolectric 契约测试。

## 回滚锚点

- 任务开始 HEAD：`2bf29bc4c84ed50e751da88cb212b93f8a371a99`
- 远端 `beta`：`8ba23ac144`；远端 `origin/dev2`：`de66627174`
- 合并提交：`1b04e7a33b`
- 回滚方式：`git revert 1b04e7a33b` 撤销合并；revert 单提交 `2bf29bc4c8` 即可恢复批量按钮仅搜索/换源模式可用且不入焦点链的原行为。
- 上一轮 dev2 交付记录：`docs/C30-beta-merge-review-dev2-20261001.md`

## 状态与下一步

- 合并、四轮评审、验证均已完成，评审通过。
- 下一步：任务守卫收尾提交（含本文档），创建恢复标签，推送 `dev2`，创建 base=`beta`、head=`dev2` 的中文 PR；只创建 PR，不执行合并。
