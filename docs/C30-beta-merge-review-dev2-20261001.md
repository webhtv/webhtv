# C30：dev2 合并远端 beta 最新代码并复评未推送改动（首页菜单追更/站点注入动作）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码（`890934423d`，Merge PR #390）合入本地 `dev2`，确认未顺带带回任何远端已移除/回退的提交内容；复评 `dev2` 相对 `origin/beta` 的全部已修改代码（含已提交未推送的 `b2e1174d76`）；发现问题最小修复并验证；循环复评通过后提交本任务改动、推送 `dev2`、创建中文 PR 到 `beta`（只创建不合并）。
- **验收**：`dev2` 包含 `origin/beta` 全部提交且未带回任何被回退内容（负向核对）；净差异仅含本分支自身改动；定向单测通过；PR 描述用中文、排版清楚。
- **允许路径**：`app/src`（评审修复）与本文档；合并产生路径由 `git merge origin/beta` 决定。
- **分支/HEAD**：任务开始 `dev2` HEAD = `b2e1174d769913b49397f283febaebc61fe13b58`（领先 origin/dev2 两个提交），工作区干净。
- **当前状态**：合并完成（`14556212b1`，无冲突）、四轮评审完成（发现并修复 4 处过时注释）、定向单测通过，准备提交推送与创建 PR。
- **守卫时序说明**：本任务首步就是合并 beta，守卫在合并前启动（base_head=`b2e1174d76`）；合并提交即本任务交付物，故 finish 前将守卫 base_head 同步为合并提交 `14556212b1`（与 C28 同场景先例一致：合并提交本身不带 Task-Guard trailer，守卫会话只覆盖评审修复与文档提交）。
- **下一动作**：任务守卫收尾提交本文档与评审修复，创建恢复标签，推送 `dev2`，创建 `dev2 -> beta` 中文 PR（只创建不合并）。

## 时间与设备

- 当前本地时间：2026-10-01 19:40 开始（Asia/Shanghai）。
- 本任务纯仓库侧合并、评审与单测验证，未使用模拟器设备。

## 同步核对台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始 HEAD | `b2e1174d769913b49397f283febaebc61fe13b58` |
| 远端 `origin/beta` HEAD | `890934423d`（Merge PR #390 from dev1） |
| 远端 `origin/dev2` HEAD | `6ee8a41dc9`（Merge PR #389，dev2 已含） |
| beta 新增提交（相对 dev2 起点） | `890934423d`、`5decbb96bf`（dev1 合并+猫源复评）、`bba9e17dbb`（猫源 node 子进程崩溃恢复修复） |
| 合并结果 | `14556212b1` = merge `b2e1174d76` × `890934423d`，无冲突 |
| `dev2..origin/beta` | 0（远端 beta 无任何 dev2 缺失提交） |

- **远端已移除/回退的提交未被顺带带上来（决定性核对）**：
  - 合并前 `dev2..origin/beta` = 3，合并后 = 0；`origin/beta..dev2` 只有本分支自有提交（`b2e1174d76` + 合并提交）。
  - 上一轮记录的回退坐标 `5682f2b054`（剔除 PR #353 主题系统改动）经 `git merge-base --is-ancestor` 判定**不是** dev2、也不是 origin/beta 的祖先。
  - 负向内容核对：`HistoryProgressFormatter.java` / `HistoryProgressFormatterTest.java` 合并后仍不存在；`git diff origin/beta dev2` 不含 `history_watched_time` 生产代码（testMobile 中命中为断言“不存在”的防回归测试，系 beta 既有内容）、不含 dynamic theme / AdSegmentVerifier / DecodeMode / LUT warmup / HlsSegment 等任何被回退功能标记。
  - 结构性决定论核对：dev2 树相对 beta 的净差异只有本分支 8 个文件（见下），**不可能**夹带 beta 不存在的内容；被剔除提交所涉 4 个公共文件（Setting.java、三个 strings.xml）的交集部分经逐 hunk 复查，改动内容全部是本次菜单扩容 hunk，与主题系统无关。

## 合并带入内容核对

合并相对第一父（`b2e1174d76`）带入 4 个文件 +286/−3，与 beta 侧（`6ee8a41dc9..origin/beta`）完全一致，无丢失、无夹带：

- `app/src/main/java/com/fongmi/android/tv/api/loader/CatSpider.java`（猫源远端 T4 api 误改写修复）
- `app/src/main/java/com/fongmi/android/tv/node/NodeRuntime.java`
- `app/src/test/java/com/fongmi/android/tv/api/loader/CatSpiderResolveTest.java`（新增 6 用例）
- `docs/C29-beta-merge-review-dev1-20261001.md`（dev1 任务文档）

## 本轮待复评的改动（已提交未推送）

`origin/beta..dev2` 净差异为单提交 `b2e1174d76`（`feat(tv): add following and site injection home menu actions`）：首页菜单键「选项弹窗」从 9 个动作扩到 11 个，新增「追更页面」「站点注入」两项，均映射到既有动作（与功能按钮区一致的 `FollowingActivity.start(this, null)` 与 `openCustomCsp()`）。

| 文件 | 改动 |
| --- | --- |
| `HomeActivity.java` | `onHomeMenuItem` 新增 `case 10 -> FollowingActivity.start(this, null)`、`case 11 -> openCustomCsp()`；javadoc 1..9 → 1..11 |
| `Setting.java` | `getHomeMenuKey()` 上限 9 → 11（防越界回落 0） |
| `values/strings.xml` 及 zh-rCN/zh-rTW | `select_home_menu_key` 数组各 +2 项（追更页面/站点注入），三 locale 均衡 |
| `HomeMenuDialogSourceTest.java` | 新增 `menuOptionsIncludeFollowingAndSiteInjectionAndKeepDialogDefault` 用例，数组数 10→12 断言更新，注释同步 |

## 评审记录

### 第 1 轮：改动本体语义 + 调用面穷举

- `select_home_menu_key` 三 locale 数组同步扩为 12 项（0=选项弹窗，1..11=动作），`HomeMenuDialog` 运行时 `items.remove(0)` 后 position+1 正确映射到 case 1..11，`NO_POSITION` 防递归、防叠开、进程恢复回退宿主等既有机制不受影响。
- `FollowingActivity.start(this, null)`：与第 969 行功能按钮区既有用法完全一致（null → 无 extra → `getStringExtra` 返回 null → `TextUtils.isEmpty` 兜底 -1），签名 `start(Activity, String)` 匹配。
- `openCustomCsp()`：既有私有方法（第 978 行），带文件权限请求与 `isFinishing/isDestroyed/isStateSaved` 防护，菜单键复用同一入口无重复实现。
- `Setting.getHomeMenuKey()` 上限 11 与数组下标 0..11 精确匹配，旧偏好值 ≤9 不受影响；`HomeMenuKeyDialog` / `SettingPersonalActivity` 均直接读数组，自动显示新项。
- 布局核算：去掉首项后 11 项 = 4 行（4×40dp + 3×16dp 间距 = 208dp + 48dp padding = 256dp < maxHeight 352dp），弹窗仍一屏显示，无需滚动。
- **发现问题（3 处过时注释）**：本次扩容更新了 HomeActivity javadoc 与测试注释，但遗漏了 `HomeMenuDialog.java` 两处（`GRID_COUNT` 旁"9 项按 3 列排布"注释、Listener javadoc "取值 1..9"）与 `dialog_home_menu.xml` 的 `tools:itemCount="9"`。会误导后续维护。
- **第 1 轮结论**：语义正确，需修 3 处过时注释。

### 修复 1（3 处注释 + tools:itemCount）

- `HomeMenuDialog.java:29` 注释改为"去掉首项后 11 项共 4 行，仍在 maxHeight 一屏内显示完"。
- `HomeMenuDialog.java:38` Listener javadoc "1..9" → "1..11"。
- `dialog_home_menu.xml` `tools:itemCount` 9 → 11。

### 第 2 轮：复扫 + 编译/单测验证

- `grep -rn "1\.\.9|itemCount=\"9\"|9 项|共 10 项"` 全 leanback/main 复扫为空。
- 定向单测 `bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.dialog.HomeMenuDialogSourceTest"` → **BUILD SUCCESSFUL**（99 tasks，含 leanback 单测源集编译验证）。
- `git diff --check origin/beta` 通过。
- **第 2 轮结论**：注释修复到位，但发现测试内第 82 行注释"英文文案最长 13 字符"也已过时（新增 "Following page"/"Site injection" 均为 14 字符）。

### 修复 2（1 处字符数注释）

- `HomeMenuDialogSourceTest.java:82` 改为"英文文案最长 14 字符（Following page/Site injection）"。该注释解释 autoSize 兜底的存在理由，文案加长不削弱该理由，注释须与事实一致。

### 第 3 轮：全仓假设扫描 + 联动测试

- 全仓扫"其他假设旧项数"的代码/测试：仅 `HomeMenuDialogSourceTest` 涉及，其余 assertEquals(10/100) 命中均为无关文件（EpisodeGridLayoutPolicyTest、TrackDialogTest 等）。
- mobile flavor 不引用 `select_home_menu_key` 数组，无跨 flavor 影响。
- 修复 2 后重跑定向单测 → **BUILD SUCCESSFUL**（32s，相关编辑后的必要重跑）。
- beta 带入的 `CatSpiderResolveTest` 在合并后 dev2 树上复跑 → **BUILD SUCCESSFUL**（28s），确认合并不破坏 beta 侧契约。
- `home_custom_csp` 等引用字符串位于 leanback flavor 专属 strings.xml，资源完整。
- **第 3 轮结论**：无新问题。

### 第 4 轮：最终全量净差异复查

- `git diff origin/beta`（含工作区修复）逐 hunk 复查：8 个文件 +36/−9，全部是本次菜单扩容及其注释同步，无越权改动、无夹带。
- 被剔除提交（5682f2b05）文件清单与净差异交集（Setting.java、三 strings.xml）逐 hunk 核对：改动内容全部是菜单数组/clamp hunk，与主题系统无关。
- `git diff --check` 通过。
- **第 4 轮结论**：评审通过，可交付。

## 验证记录

- 合并后 `git rev-list --count dev2..origin/beta` = 0。
- 定向单测 `HomeMenuDialogSourceTest`（修复 1 后 1m24s、修复 2 后 32s）与 `CatSpiderResolveTest`（28s）均 BUILD SUCCESSFUL。
- `git diff --check origin/beta dev2` 无空白错误。
- Gradle daemon 已 `--stop` 回收。
- 本任务改动为菜单映射 + 注释同步，已由编译 + 源码契约测试覆盖，无需装机冒烟。

## PR 边界

相对 `origin/beta`，`dev2` 净差异 8 个文件、+36/−9，两个部分：

1. 合并 `origin/beta`（890934423d，含 dev1 猫源修复 + C29 dev1 文档），带入内容与 beta 完全一致。
2. dev2 自有提交 `b2e1174d76` + 本任务评审修复（4 处注释同步）：首页菜单键选项弹窗新增「追更页面」「站点注入」两个动作项。

## 回滚锚点

- 任务开始 HEAD：`b2e1174d769913b49397f283febaebc61fe13b58`
- 远端 `beta`：`890934423d`；远端 `origin/dev2`：`6ee8a41dc9`
- 合并提交：`14556212b1`
- 回滚方式：`git revert 14556212b1` 撤销合并；revert 单提交 `b2e1174d76` 即可恢复 9 项菜单映射。
- 上一轮 dev2 交付记录：`docs/C29-beta-merge-review-dev2-20261001.md`

## 状态与下一步

- 合并、四轮评审、修复与验证均已完成，评审通过。
- 下一步：任务守卫收尾提交（含评审修复与本文档），创建恢复标签，推送 `dev2`，创建 base=`beta`、head=`dev2` 的中文 PR；只创建 PR，不执行合并。
