# C58：dev4 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码安全合入 `dev4`，不得复活 beta 已移除/回退内容；评审合并结果、本地已提交未推送的屏显对比度改动及本轮修复，验证通过后提交、推送并创建中文 `dev4 -> beta` PR，**只创建不合并**。
- **允许路径**：`app/`、`docs/`；task guard 启动时工作区干净，0 个预先存在脏路径。
- **分支与回滚锚点**：`dev4`；最终合并第一父 `e0c067cba71c51b55058f365b7d85646e00e808e`。
- **最终 beta 权威 tip**：`8ed903ac2a75bf76b46ab04e17059a30070d4b63`（beta 在 PR #434 后又合入 PR #435/#436；已重新 fetch 并重新合入）。
- **当前状态**：第三次合并无未解决冲突；已完成五轮代码评审，覆盖 beta PR #434/#435/#436 的焦点环、主题、停播、更新判定、Room 完整性恢复改动，本地屏显/Custom CSP 修复及远端删除保留；双 flavor 目标测试/编译通过，leanback 全量任务仅有既有 Windows rename 环境失败；Gradle daemon、构建输出和临时日志已清理；待 task guard finish 创建第三次合并提交和 recovery tag。
- **下一动作**：执行 task guard finish，随后推送 `dev4`/recovery tag 并创建保持 OPEN 的中文 beta PR。

## 1. 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev4` |
| 合并前 HEAD | `e0c067cba71c51b55058f365b7d85646e00e808e`（第一次合并提交，已推送） |
| 合并前旧 `origin/beta` | `fb4fd7d99ca1547ea624f3d5f1b7f3cd81b880a5` |
| 最终 `origin/beta` | `8ed903ac2a75bf76b46ab04e17059a30070d4b63`（PR #435/#436：停播、更新判定、Room 完整性恢复） |
| 第一次合并提交 | `e0c067cba71c51b55058f365b7d85646e00e808e`（已推送，作为最终合并第一父） |
| 最终 MERGE_HEAD | `8ed903ac2a75bf76b46ab04e17059a30070d4b63` |
| 第三次自动合并 | beta PR #435/#436 与本地主题资源在 5 个文件冲突；已采用 beta 最新主题资源版本，0 未解决冲突 |
| 最终索引树 | `f66c980de84b53b4b50c8ec9f59813c67d99cdeb` |
| task guard | `beta-sync-review-dev4-20261011-r2`，scope=`app docs` |

### beta 增量 disposition

以下提交均通过 merge 第二父纳入，不执行 cherry-pick，不把已回退内容单独带回：

| 完整 commit ID | 处置 |
| --- | --- |
| `8ed903ac2a75bf76b46ab04e17059a30070d4b63` | 最新 beta tip，纳入 PR #435/#436，作为最终合并第二父 |
| `00ad80114fa971c32de3a74f072e33714b9f488e` | PR #435 dev2 合并提交，随 beta 第二父纳入 |
| `97c7ae68abd9be76d2bdd1d79e34d93a9f6925ae` | 退出播放页停播修复，随 beta 第二父纳入 |
| `8b54e0e97c0a4e93d60d4453d55f36b2f1eedef0` | PR #436 dev1 合并提交，随 beta 第二父纳入 |
| `d8c9bdc650115daf21d9ea1abfa3efb7d8f0535a` | C59 更新严格新旧判定，随 beta 第二父纳入 |
| `56372b7dbac436f88b34225e714adedb3854d0fa` | C60 Room 完整性恢复，随 beta 第二父纳入 |

本轮 beta 侧新增生产/测试范围已复审：更新判定按版本号/构建时间戳严格排序；Room 仅拦截 identity mismatch 并限一次重建；退出播放页通过服务停播并撤销前台通知；远端删除的 C58/C59/C60/C61 历史任务文件和旧测试保持删除，没有恢复。

## 2. 复活与净差异审计

- 当前合并索引树为 `f66c980de84b53b4b50c8ec9f59813c67d99cdeb`（第一父 `e0c067cba71c51b55058f365b7d85646e00e808e` + 第二父 `8ed903ac2a75bf76b46ab04e17059a30070d4b63`，已逐项消解 5 个主题资源冲突）。
- 当前相对最新 beta 的净差异为 **29 路径**，全部属于本地屏显对比度/Custom CSP 动态主题修复及任务文档。
- 当前 `git diff --cached --diff-filter=D origin/beta` 为空；没有删除 beta 最新树中的文件。beta 侧删除的旧任务文档与旧测试（`UpdateOrderingTest` 等重新引入的除外）保持删除，未被复活。
- 当前净差异没有 native、依赖锁、Release 正式包或仓库外路径。
- beta 中原有的 `ABANDONED-REQUEST-LOADING-RELEASE-20261009.md`、`C57-beta-merge-review-dev2-20261010.md`、`INLINE-LOADING-RELEASE-20261009.md`、`LOADING-SPINNER-OMISSION-SWEEP-20261009.md`、`OSD-REBUFFER-ENGINE-BRANCH-20261009.md`、`PLAYBACK-LOADING-GUARD-20261009.md` 均保留在合并树中；它们相对合并前 HEAD 的删除状态只是“beta 尚未合入”的净差异表现，不是最终合并树删除。
- 主题行锚定的远端回退/撤销扫描未发现需要重新恢复的文件；未使用不锚定提交正文的 grep，避免把本任务文档误判为 revert 路径。

## 3. 代码评审与修复

### 第 1 轮

评审对象：本地两个已提交未推送提交 `bd78d7b528fb61c4dc89f8eb0655085c878b4a87`、`fec5af7aa54ff36df45ce919151a4d7fc9b74f9c`，以及 beta 合入的加载态、OSD 诊断、电池控制栏改动。

结论：

- `VideoActivity.setPlayer(null)` 与 `!canApplyPlayerResult()` 均释放加载守卫；
- `TmdbDetailActivity` 的内联取址请求使用代际归属，旧回调不能清除新请求标记；
- `PlayerOsdController` 对非 Exo 引擎读取 `PlayerManager` 的重缓冲追踪器；
- beta mobile 电池块复用 `BatteryUtil`、`Formatters.TIME`，可见性不受 `PlayerButtonSetting` 误控；
- `testMobile/VideoActivityLayoutTest.java` 的 beta 与本地 hunk 不相交；
- 未发现第一轮必修缺陷。

### 第 2 轮：发现并修复

发现的问题：`CustomCspDialog` 中运行时创建的识别/排序输入、状态徽章、站点行背景、状态文字、危险按钮和图标仍使用固定 Google 浅色值；暗色或自定义主题下会与动态文本/对话框面板不匹配。

修复内容：

- 使用 `ThemeController.current()` / `ThemeTokens` 为输入文字、hint、输入框背景/描边、识别标题、标签、状态色、徽章和图标提供语义颜色；
- 焦点/按下/激活的站点行改用 `primaryContainer + readable(primary)`，常态使用 surface/errorContainer/outline 语义；
- 登录状态徽章 `shape_login_state_badge.xml` 改用 `?attr/colorPrimaryContainer`；
- `PlayerSettingsContrastTest` 增加 Custom CSP 与登录状态徽章的回归契约，并禁止固定浅色重新出现。

第 3 轮复评结论：

- Custom CSP 生产代码编译通过；
- 新增回归断言通过；
- 检查后仅剩 `Color.TRANSPARENT` 用于窗口透明背景，不属于主题面板颜色，不构成问题；
- 没有发现新的必修问题。

### 第 4 轮：beta PR #434 重新合入复审

- beta 在第一次 `dev4` 推送后前进到 `3148f030df9784be7b14fc924364c304980ff9ab`；按规则没有创建过时 PR，而是以第一次合并提交 `e0c067cba71c51b55058f365b7d85646e00e808e` 为第一父重新合入。
- PR #434 带入 83 个路径，和本地改动在 5 个资源文件相交。五处冲突均采用 beta 最新资源版本；本地屏显回归测试断言同步验证 beta 的宽度/token 契约。
- 第二次合并没有恢复 beta 已删除的 `selector_cache_button_focus.xml`；该文件仍保持缺失，beta 的 `dialog_cache_management` 新焦点环接线保留。
- `git diff --cached --diff-filter=U` 为空，索引树为 `0f98068c2954e772df2e1568e154bc785af53882`；冲突标记扫描未发现 merge marker。
- 第四轮复审结论：焦点环资源、追更按钮文本 token、播放器/应用表面分层与本地屏显/Custom CSP 语义一致，没有新的必修问题。

### 第 5 轮：beta PR #435/#436 最终重新合入复审

- beta 继续前进到 `8ed903ac2a75bf76b46ab04e17059a30070d4b63`；中止旧未提交合并，以已推送的 `e0c067cba71c51b55058f365b7d85646e00e808e` 为第一父重新合入。
- 新增范围：PR #435「退出播放页必定停播」（`PlaybackActivity.stopPlayback` 归属方 `shutdown()`、非归属方 `suspend()`）、C59 更新判定（`Update.hasUpdate` 严格新旧 + `AppVersion.isNewer` 构建时间戳）、C60 Room identity 完整性兜底（`IntegrityRecoveringOpenHelperFactory`）。
- 复审结论：更新判定不再把更旧渠道包当更新（`UpdateOrderingTest` 5 例、`AppVersionBuildStampTest` 8 例锁定）；Room 兜底仅拦截 `Room cannot verify the data integrity` 且限一次重试，非 identity 失败原样抛出（`AppDatabaseIntegrityRecoverySourceTest` 4 例）；退出播放页停播在服务侧完成并撤销前台通知（`PlaybackOwnershipSourceTest` 18 例）。
- 与本地屏显/Custom CSP 修复无冲突语义；5 个主题资源冲突已采用 beta 最新版本。
- 第五轮复审结论：没有新的必修问题。

## 4. 验证证据

- leanback `TvFocusRingContractTest`：19/19，0 failure，0 error；
- leanback `TvAppSurfaceFocusRingDeviceTest`：9/9，0 failure，0 error；
- leanback `ThemeControllerContractTest`：11/11，0 failure，0 error；`ThemeContractTest`：6/6，0 failure，0 error；
- leanback `LightDialogShellOpacityTest`：2/2，0 failure，0 error；`NativeEnhancedPlaybackStyleFocusTest`：14/14，0 failure，0 error；
- mobile `ThemeControllerContractTest`：11/11，0 failure，0 error；`ThemeContractTest`：6/6，0 failure，0 error；
- mobile `PlayerSettingsContrastTest`：5/5，0 failure，0 error；`PlayerDisplaySettingSyncTest`：8/8，0 failure，0 error；`TmdbDetailDirectPlayTransitionSourceTest`：4/4，0 failure，0 error；`VideoActivityLayoutTest`：155/155，0 failure，0 error；
- 双 flavor Java 与 AndroidTest Java 编译：`BUILD SUCCESSFUL`。

### 4.1 最终定向验证

- leanback 定向：`UpdateOrderingTest` 5/5、`AppDatabaseIntegrityRecoverySourceTest` 4/4、`AppVersionBuildStampTest` 8/8、`PlaybackOwnershipSourceTest` 18/18、`ThemeContractTest` 6/6、`ThemeControllerContractTest` 11/11、`TmdbDetailDirectPlayTransitionSourceTest` 4/4、`PlayerSettingsContrastTest` 5/5、`PlayerDisplaySettingSyncTest` 8/8，均 0 failure / 0 error；
- mobile 定向：`BUILD SUCCESSFUL`（含 C59/C60/停播/主题/屏显目标用例，0 failure）；
- leanback 焦点环与主题：`TvFocusRingContractTest` 19/19、`TvAppSurfaceFocusRingDeviceTest` 9/9、`LightDialogShellOpacityTest` 2/2、`NativeEnhancedPlaybackStyleFocusTest` 14/14；
- mobile `VideoActivityLayoutTest`：155/155；
- 双 flavor Java 与 AndroidTest Java 编译：`BUILD SUCCESSFUL`。

### 4.2 全量测试环境失败分类

最终 leanback 全量任务（4197 例）仍出现同一类既有 Windows 文件/原子 rename 环境失败：`MpvFontConfigTest` 与 `MpvHlsCacheCoordinatorTest`；目标主题/屏显/焦点环/更新/数据库用例全部通过，mobile 定向任务整体 `BUILD SUCCESSFUL`。该环境失败不作为本任务回归。

### 4.3 资源回收

第二次合入验证后执行并成功：

```text
gradlew.bat --stop
gradlew.bat --no-daemon clean
```

已停止 Gradle daemon，`clean` 成功，并删除本轮临时日志；未生成正式包，不卸载或安装模拟器包。

## 6. 交付坐标

| 项 | 值 |
| --- | --- |
| 第一次合并提交（已推送） | `e0c067cba71c51b55058f365b7d85646e00e808e`（第二父旧 beta `fb4fd7d99ca1547ea624f3d5f1b7f3cd81b880a5`） |
| 最终合并提交（双父） | `3e657bef0d978e8155803f2b19904b0a3e5a88cd`（第一父 `e0c067cba71c51b55058f365b7d85646e00e808e`；第二父 `8ed903ac2a75bf76b46ab04e17059a30070d4b63` = 最终 beta tip） |
| recovery tag | `recovery/beta-sync-review-dev4-20261011-r2/20261011082700-3e657bef0d97` |
| 第一次 recovery tag | `recovery/beta-sync-review-dev4-20261011/20261011033932-e0c067cba71c` |
| 推送 | `git push origin dev4` → `e0c067cba71..3e657bef0d9  dev4 -> dev4`；recovery tag 推送成功 |
| PR | [#437](https://github.com/Silent1566/webhtv/pull/437)（`dev4` → `beta`，**只创建，未合并**） |
| PR 状态校验 | `state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`、`changedFiles=29`、`baseRefName=beta`、`headRefName=dev4` |
| PR 文件集校验 | `gh api repos/Silent1566/webhtv/pulls/437/files --jq '.[].filename' \| sort` 与 `git diff --name-only origin/beta HEAD \| sort` **逐项一致**（29/29） |
| 收尾复核 | `git merge-base --is-ancestor origin/beta HEAD` 为真；`MERGE_HEAD` 已清除；`git status --porcelain` 干净；无残留 Gradle daemon |
| 远端 beta | `git ls-remote origin refs/heads/beta` = `8ed903ac2a75bf76b46ab04e17059a30070d4b63`，与合并第二父一致 |

## 7. 回滚与交付

- 回滚：对最终 merge commit 执行 `git revert -m 1 <merge-commit>`，恢复到 `e0c067cba71c51b55058f365b7d85646e00e808e`。
- 无数据库迁移、网络协议、ABI、native、Release 产物或用户偏好格式变化。
- 最终提交、recovery tag、推送结果和 PR 编号由本任务结束动作补入交付摘要；PR 必须保持 OPEN，不自动合并。
- **当前下一步**：task guard finish 创建合并提交与 recovery tag。
