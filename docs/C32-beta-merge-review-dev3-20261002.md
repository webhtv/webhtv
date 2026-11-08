# C32：dev3 合并远端 beta 最新代码（PR#392/#393/#394）并复评全部未推送改动

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码（含 PR #392 站点批量动作焦点修复、PR #393 播放器多线程/硬解按钮接线 + 多线程按钮默认隐藏、PR #394 猫源启动超时修复）合入本地 `dev3`，且不得把远端已移除/回退的提交重新带上来；复评 `dev3` 相对 `origin/beta` 与相对 `origin/dev3` 的全部未推送改动，发现问题则最小修复并验证，通过后提交本任务改动、推送 `dev3`、创建 `dev3 -> beta` 的中文 PR（只创建，不合并，不擅自通过）。
- **验收**：① 合并结果包含复评时刻的 `origin/beta` tip；② 被远端剔除的提交不在结果祖先中，且远端删除/回退的文件未被带回；③ `dev3` 相对 `origin/beta` 的净差异只含本分支自身改动（统一主题系统 + 全部未推送提交）；④ 目标单测通过（覆盖 PR#392/#393/#394 与 dev3 主题契约）；⑤ PR 描述为中文、说明改动内容且排版清楚。
- **允许路径**：`.codex`、`app`、`docs`、`scripts`、`README.md`、`gradle`、`build.gradle`、`settings.gradle`。
- **保护面**：任务开始时 `git status --porcelain` 无输出（0 个脏路径）。
- **分支/HEAD**：`dev3`；任务开始时 HEAD = `01f14fc8ea8c4ddf8be6ce07c1982e03c012af1c`。
- **合并目标**：`origin/beta` tip（复评时刻为 `f33710935c82c6fc05b63b4494335183eac07894`，Merge PR #394）。
- **当前状态**：合并与复评、验证全部完成；merge 提交已创建。
- **下一动作**：`task_guard.sh finish`（含合并父节点）+ 打恢复标签，推送 `dev3`，创建 `dev3 -> beta` 的中文 PR（只创建，不合并）。

## 时间与设备

- 本地时间：2026-10-02（下午），Asia/Shanghai。
- 设备：未占用其它工作区机位，未卸载现有包（本次仅做单测，不打包覆盖安装）。
- 打包：本次不改产品行为，仅单测验证（合并为纯增量、无冲突）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev3` |
| 任务开始时 HEAD | `01f14fc8ea8c4ddf8be6ce07c1982e03c012af1c` |
| `origin/beta` tip | `f33710935c82c6fc05b63b4494335183eac07894` |
| 合并基点 | `8ba23ac1449b250b1c8729e85bf425ad783200f5`（`origin/beta` 当时的 tip，Merge PR #391） |
| MERGE_HEAD（第二父） | `f33710935c82c6fc05b63b4494335183eac07894` |
| 合并结果 | 16 文件、+952 / −24；0 冲突（仅 2 文件自动合并） |
| `dev3` 相对 `origin/beta` 的净差异 | 仅 dev3 自身未推送改动（统一主题系统 + 各未推送提交） |

## 被剔除提交核对

1. `5682f2b054b577b0db5c6f7c1143f2eebb51858e`（“剔除 PR #353 主题系统改动”）**不是** `origin/beta` 祖先，也不是合并结果祖先；本次合并没有把它带回来。
2. 该剔除提交删除的 `assets/themes/*`、`ThemeCatalog*`、`ThemeColorUtil`、mobile `Theme*Dialog*`、`ThemePreviewView` 等文件在合并结果中均不存在；仅 dev3 自有的 `ThemeController`/`ThemeTokens`/`ThemeProfile*`/`ThemeBinder`/`ThemeResolver` 等（属 dev3 统一主题系统，非被剔除内容）保留。

## 评审结论

- 合并为纯增量、无内容冲突，仅 2 个文件自动合并（`SiteDialog.java`、`VideoActivity.java`，均为 dev3 主题改动与 beta #392/#393 改动的非重叠区）。
- 三路 merge 预检通过（`git merge-tree --write-tree` exit 0，仅自动合并 2 文件）。
- **PR #392（站点批量动作焦点）**：`setActionEnabled` 改 `select/cancel` 仅在 `type > 0` 时 enabled → 改为仅反映 loading 状态（`enabled`），`setType` 用 `clickable` 控制是否可点击；`SiteDialog.java` 收到 dev3 `SiteDialogTheme.applyShell` 与 beta 焦点修复，两者不改同一行，集成无冲突。`SiteDialogActionFocusTest` 3/3 通过。
- **PR #393（播放器按钮接线 + 默认隐藏）**：`PlayerButtonSetting` 增加 `MULTI_THREAD_PROXY`（DEFAULT `visible=false`）+ `CODEC_CAPABILITY`；`HIDDEN_SEEDED` 一次性种子注入与 `reset()` 清除核对一致；mobile `VideoActivity` 接线 `onMultiThreadProxy`/`onCodecCapabilityPanel`，两 flavor 方法名各自匹配无错绑。`MultiThreadProxyPlayerUiSourceTest` 5/5、`MultiThreadProxyDialogFocusTest` 3/3 通过。
- **PR #394（猫源启动超时）**：`NodeRuntime.START_TIMEOUT_MS` 由 55s 扩到 12min，新增 READY/TRANSFER/LIB_TRANSFER/METADATA/READY_PROBE 预算；`NodeService.waitReady` 改用 deadline + OkHttp 探活。与 dev3 先前合并的 `bba9e17db`（node 崩溃恢复：`serviceAlive`/`restartIfDead`）并存，`NodeRuntime.java` 集成完整（`isRunning`=running&&serviceAlive、`restartIfDead`、`start` 复用捷径带 serviceAlive 校验）。`NodeBundleInstallTest` 21/21、`NodeLocalBundleTest` 22/22、`NodePortSelectionTest` 17/17 通过。
- **dev3 主题契约**：`SiteDialogTheme`、`ThemeController.bindDialog`、`WebHtvAlertDialogBuilder`、`SiteDialogTheme.applyShell/applyGroup` 均存在于合并树，调用点实现匹配。`SiteDialogThemeSourceTest` 5/5、`HomeMenuDialogSourceTest` 5/5 通过。

## 验证（合并树定向单测）

`./gradlew :app:testLeanbackArm64_v8aDebugUnitTest :app:testMobileArm64_v8aDebugUnitTest --tests '<目标类>'`
`BUILD SUCCESSFUL in 1m 25s`。

| flavor | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| Leanback | 4225 | 0 | 0 | 2 |
| Mobile（定向） | 30 | 0 | 0 | 0 |

覆盖类：`SiteDialogActionFocusTest`（3）、`SiteDialogThemeSourceTest`（5）、`MultiThreadProxyPlayerUiSourceTest`（5）、`MultiThreadProxyDialogFocusTest`（3）、`HomeMenuDialogSourceTest`（5）、`NodePortSelectionTest`（17）、`NodeBundleInstallTest`（21）、`NodeLocalBundleTest`（22）等。

## 提交与 tag

- 合并提交：`7d7ed7fb7 Merge remote-tracking branch 'origin/beta' into dev3`（第二父 `f33710935`）。
- 恢复 tag：见 `task_guard.sh finish` 输出。
