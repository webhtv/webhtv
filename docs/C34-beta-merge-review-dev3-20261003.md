# C34：dev3 合并远端 beta 最新代码（PR#395–#398）并复评全部未推送改动

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码（PR#395 详情直放跟随光影剧幕主题、PR#396 影视原生模式播放页简介按钮、PR#397/#398 同一 TMDB 集多版本按季号置信度消歧及其误伤修复）合入本地 `dev3`，且不得把远端已移除/回退的提交重新带上来；复评 `dev3` 相对 `origin/beta` 的全部未推送改动（TMDB 线路自定义 4 提交），发现问题则最小修复并验证，通过后提交本任务改动、推送 `dev3`、创建 `dev3 -> beta` 的中文 PR（只创建，不合并）。
- **验收**：① 合并结果包含复评时刻的 `origin/beta` tip；② 远端回退内容未被带回；③ `dev3` 相对 `origin/beta` 的净差异只含本分支自身改动；④ 目标单测通过（覆盖 PR#395–#398 与 dev3 TMDB 线路契约）；⑤ PR 描述为中文、说明改动内容且排版清楚。
- **允许路径**：`.codex`、`app`、`docs`、`scripts`、`README.md`、`gradle`、`build.gradle`、`settings.gradle`。
- **保护面**：任务开始时 `git status --porcelain` 无输出（0 个脏路径）。
- **分支/HEAD**：`dev3`；任务开始时 HEAD = `bfbd26bb98cd8c2a9c7609e550133be570a74000`。
- **合并目标**：`origin/beta` tip（复评时刻为 `b056747894cff64fb34062d4f4a1ac416f4206a0`，Merge PR #398）。
- **当前状态**：合并与两轮复评、定向单测验证全部完成，等待 finish。
- **下一动作**：`task_guard.sh finish`（合并提交）→ 推送 `dev3` → 创建 `dev3 -> beta` 中文 PR（只创建，不合并）。

## 时间与设备

- 本地时间：2026-10-03（下午），Asia/Shanghai。
- 设备：未占用其它工作区机位，未卸载现有包（本次仅单测，不打包覆盖安装）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev3` |
| 任务开始时 HEAD | `bfbd26bb98cd8c2a9c7609e550133be570a74000` |
| `origin/beta` tip | `b056747894cff64fb34062d4f4a1ac416f4206a0`（Merge PR #398） |
| 合并基点 | `bf9e6bcb190efd8d3499294ba9b4f060a13b0a0c`（Merge PR #382，dev3 与 beta 的共同祖先） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta` |
| 合并结果 | 16 文件全部自动合入，0 冲突（beta 9 个提交，文件与 dev3 未推送改动无重叠） |
| 三路预检 | `git merge-tree --write-tree` exit 0 |

beta 侧新增 9 个提交：`dd37a13fc`（集多版本消歧）、`6288fd4bd`（详情直放跟随光影剧幕）、`b6877389d`、`76a923fcc`（Merge PR#396）、`dfd4e5b3f`（简介按钮不显示修复）、`a24c85f54`、`59d481e72`（Merge PR#397）、`5e6af9e54`（消歧误伤跨线路续播修复）、`b05674789`（Merge PR#398）。

## 被回退内容核对

1. `5682f2b05`（“剔除 PR #353 主题系统改动”）不是 `origin/beta` 祖先，也不是合并结果祖先，未带回。
2. `fd29d76ee`（“撤销把电视版暗色配色搬到手机版”）是 `origin/beta` 祖先；其回退的 21 个文件（含 `app/src/mobile/res/values/webhtv_tokens.xml` 删除、19 个 drawable 恢复上游字面量）在合并树与 dev3 HEAD 之间零差异——合并不会带回被回退的暗色表内容。
3. dev3 树内 `ThemeColorUtil.java` 等主题文件为 dev3 统一主题系统自有内容，且 `origin/beta` tip 本身已含同名文件（经 PR#382 合入），不属被剔除内容。

## 评审结论（第一轮：beta 侧 9 个提交）

- **PR#396/#397 前身 `dfd4e5b3f`（简介按钮）**：`activity_video.xml` 仅移除 `btn_desc` 的 `android:visibility="gone"`，恢复简介按钮显示；`VideoActivityDetailShellSourceTest` +21 行覆盖。
- **PR#395 `6288fd4bd`（详情直放主题）**：`PlayerDetailController.isCinemaStyle()` 委托 `host.isCinemaStyle()`，直放模式跟随光影剧幕主题；`DetailModeControllerTest` +12 行覆盖。
- **PR#398/#397 消歧（核心）**：
  - `Episode.matches()`：TMDB 季集号双侧确认相等后，要求 `sameTmdbVariant`（URL 或原始条目文本一致）才算同一集；任一侧季号未知退回原容错语义。
  - 新增 `matchesPlayback(Episode, boolean versionAware)`：仅在调用方确认对侧 URL 仍能定位到本线路条目（`Flag.containsEpisodeUrl`）时启用版本消歧；换线路/换源/源站刷新后 URL 必然失配，保留集号容错，保住跨线路续播——即 `5e6af9e54` 对 `dd37a13fc` 的误伤修复，两提交配套合入。
  - `Flag.find()`：定位请求带 URL 时先按 URL 锁定版本，URL 跨季复用时按季集号回退。
  - leanback/mobile `VideoActivity` 四处 `matchesPlayback` 调用点全部改为 `versionAware` 参数形式，判据统一为 `flag.containsEpisodeUrl(mHistory.getEpisode())`；`TmdbDetailActivity.isHistoryEpisode` 同步改造。
  - **dev3 侧回归核对**：dev3 音频队列路径（`containsAudioQueueEpisode`/`indexOfAudioQueueEpisode`）调用 `Episode.matches()`，队列条目由 `Episode.create(name, url)` 构造、不带 TMDB 集号，双侧 `hasTmdbEpisodeNumber()` 为 false，走 URL 相等分支，语义与收紧前一致，无回归。
  - **null 安全核对**：`getFlag()` 空列表返回 `new Flag()` 非 null；`History.getEpisode()` 恒构造新实例非 null；`containsEpisodeUrl` 内部对 null/空 URL/空列表防护——beta 新代码无 NPE 风险。
  - `EpisodeVersionIdentityTest` 217 行、`HistoryPlaybackTest` +44 行覆盖同集多版本与跨线路续播两侧用例。
- 测试文件改动（`VideoActivityHistoryTitleTest`、`VideoActivityLayoutTest` 等 ±8 行）均为适配上述契约的断言更新。

## 评审结论（第二轮：dev3 未推送 4 提交，TMDB 线路自定义）

- **`41ab47182`（下拉自定义选项与自定义输入框）**：`TmdbSourceDialog` 增加自定义线路选项（`withCustomLabel` 追加“自定义”）、`apiCustomInput`/`imageCustomInput` 输入框（默认 `gone`）、`showCustomInput`/`hideCustomInput` 显隐与焦点迁移、`setupRouteDropdown` 改为确认键/点击弹出 Picker（`setKeyListener(null)` 禁软键盘直接编辑）。
- **`024c13b5f`（输入框移位 + 确认键弹出）**：布局把自定义输入框移至对应线路下拉框正下方；Picker 不再聚焦自动弹出，`showRoutePicker` 后 `setSelection(focusIndex)+requestFocus` 保证 D-pad 可达。
- **`9d0deec46`（官方直连回显修复）**：`isCustomDisplay` 改为 display 未命中任何内置选项标签才判自定义，修复选官方直连保存后重开误显示自定义。
- **`bfbd26bb9`（D-pad 焦点进入列表项）**：`showRoutePicker` 后 `picker.getListView().post()` 中 `setSelection(checked)` + `requestFocus()`，修复 Picker 弹出后焦点停在容器导致 DOWN 直接跳取消按钮的问题。
- **保存链路核对**：`onSave` 自定义模式经 `TmdbProxy.normalizeConfig`/`normalizeImageConfig` 归一化；空自定义值回退官方直连；`apiRouteMode`/`imageRouteMode` 写 `auto|direct|custom`；`TmdbConfigCustomRouteModeTest` 5 项覆盖保存→重读往返（含 itv666/wsrv 以 custom 模式保存的往返一致性）。
- **焦点链核对**：`wireConfigDialogFocus` 在自定义输入框可见/隐藏时经 `rewiringFocusAfterVisibilityChange` 重接线，`apiHost ↔ apiCustom ↔ imageHost ↔ imageCustom ↔ omdbApiKey` 链路可达；`TmdbSourceDialogInflationContractTest` 源码契约断言与实现一致。
- 字符串三语（默认/zh-rCN/zh-rTW）齐全。

两轮复评结论：beta 侧 9 提交与 dev3 侧 4 提交均通过评审，未发现需要修复的问题，无需代码改动。

## 验证（合并树定向单测）

`./gradlew :app:testLeanbackArm64_v8aDebugUnitTest` / `:app:testMobileArm64_v8aDebugUnitTest`，覆盖类：`EpisodeVersionIdentityTest`、`HistoryPlaybackTest`、`TmdbConfigCustomRouteModeTest`、`TmdbSourceDialogInflationContractTest`、`VideoActivityDetailShellSourceTest`、`VideoActivityHistoryTitleTest`、`VideoActivityLayoutTest`、`DetailModeControllerTest`。

| flavor | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| Leanback（定向） | 90 | 0 | 0 | 0 |
| Mobile（定向） | 258 | 0 | 0 | 0 |

## 提交与 tag

- 合并提交：见 `task_guard.sh finish` 输出。
- 恢复 tag：见 `task_guard.sh finish` 输出。

## 下一动作

`task_guard.sh finish`（合并提交 + tag）→ 推送 `dev3` → `gh pr create --base beta --head dev3`（中文描述，只创建不合并）。
