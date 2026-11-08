# C29：dev3 合并远端 beta 最新代码并循环复评全部已修改代码

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入本地 `dev3`（且不得把远端已移除/回退的提交重新带上来），循环复评相对 `origin/beta` 与相对 `origin/dev3` 的全部已修改代码（含已提交未推送的 8 个提交），发现问题则最小修复并验证通过，通过后再次复评，直到通过；最后提交本任务改动、推送 `dev3`、创建 `dev3 -> beta` 的中文 PR（只创建，不合并，不擅自通过）。
- **验收**：① 合并结果包含复评时刻的 `origin/beta` tip；② 被远端剔除的提交不在结果祖先中，且远端删除/回退的文件未被带回；③ `dev3` 相对 `origin/beta` 的净差异只含本分支自身改动（统一主题系统 + 8 个未推送提交）；④ 双 flavor 全量单测、双 flavor Java 编译、Debug 打包覆盖安装、设备端主题链路与播放页冒烟全部通过；⑤ PR 描述为中文、说明改动内容且排版清楚。
- **允许路径**：合并与冲突解决所需的全部路径（`.codex`、`app`、`docs`、`scripts`、`README.md`、`gradle`、`build.gradle`、`settings.gradle`）。
- **保护面**：任务开始时 `git status --porcelain` 无输出（0 个脏路径）。
- **分支/HEAD**：`dev3`；任务开始时 HEAD = `c53fce8d61b879b2033ec8d77fb873de9906eb51`，`origin/dev3` = `9554195c980261fe08e2ed14642809b75b534dd6`。
- **合并目标**：`origin/beta` tip（复评时刻为 `b184f7363c724e2074ff3cd93feaae29ed5166e9`，Merge PR #387）。
- **当前状态**：合并与两轮复评、修复、双 flavor 全量单测 / 编译 / 打包 / 设备冒烟全部完成；MERGE_HEAD = `b184f7363`，0 个未解决冲突，merge 结果树已写入 index。
- **下一动作**：以一次 `task_guard.sh finish` 提交（含合并父节点）+ 打恢复标签，推送 `dev3`，创建 `dev3 -> beta` 的中文 PR（只创建，不合并）。

## 时间与设备

- 本地时间：2026-09-30（下午），Asia/Shanghai。
- 设备：`192.168.50.3:5559`（dev3 分配机位，Android 9 / sdk 28），包名 `com.silent.android.webhtv`；未占用其它工作区机位，未卸载现有包（全程覆盖安装）。
- 打包：`bash scripts/build_arm64_debug_install.sh --flavor mobile|leanback --serial 192.168.50.3:5559`（Debug，覆盖安装）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev3` |
| 任务开始时 HEAD | `c53fce8d61b879b2033ec8d77fb873de9906eb51` |
| `origin/dev3` | `9554195c980261fe08e2ed14642809b75b534dd6`（8 个未推送提交） |
| 首次拉取时 `origin/beta` | `b184f7363c724e2074ff3cd93feaae29ed5166e9` |
| 合并基点 | `163cdf037d5aaadacbe542690651155374d02c46`（`origin/beta` 当时的 tip） |
| `MERGE_HEAD`（第二父） | `b184f7363c724e2074ff3cd93feaae29ed5166e9` |
| 合并期间新增 `origin/beta` | `4cb165a0f`（PR #386，dev1）→ `b184f7363`（PR #387，dev2） |
| `dev3` 相对 `origin/beta` 的净差异 | 530 文件、+14083 / −6572 |
| 冲突文件数 | 7（内容冲突）+ 1（修改/删除） |

- **合并期间 beta 两次前进**：`163cdf037 → 4cb165a0f → b184f7363`。第二次前进（PR #387，dev2 的融合/原生唤醒焦点修复）触及 `VideoActivity.java` / `TmdbDetailActivity.java` / `PlayerControlFocusHelper.java`，已把增量 `4cb165a0f..b184f7363` 以三路 apply（`git apply --3way`）落到工作树，并把 `MERGE_HEAD` 更新为 `b184f7363`。最终合并的第二父节点 = 复评时刻的 `origin/beta` tip。
- **被剔除提交核对（两层）**：
  1. `5682f2b054b577b0db5c6f7c1143f2eebb51858e`（“剔除 PR #353 主题系统改动”）**不是** `origin/beta` 祖先，也不是合并结果祖先；本次合并没有把它带回来。
  2. `git diff --diff-filter=D --name-only <beta> <merge结果树>` 只包含 dev3 自身设计删除（旧主题系统），未包含任何“beta 曾删除、又被我恢复”的文件；`be1b02e06`（revert: remove dynamic theme color system / Task C/D/E）删掉的 `ThemeCatalog*/ThemeTransfer*/ThemeTweakCnAdapter*`、mobile `Theme*Dialog*`、`assets/themes/*` 等在合并结果中均不存在。
- **远端已移除的回退点播相关文件未带回**：`70306f1de`（revert: remove ad segment verification）删除的 `AdBlockPreviewActivity` / `AdBlockPreviewStore` / `AdBlockSegmentKey` / `HlsPreviewManifest*` 等全部不在合并结果中。

## 冲突解决原则

dev3 的未推送 8 个提交全部属于**统一主题系统**（`ThemeBinder`/`ThemeRole`/`ThemeColorIndex`/`ThemeEditor`/v2 16 槽 profile）与壁纸/弹窗文字可读性修复。该系统只在 dev3 存在（`origin/beta` 仍是旧的另一套 ThemeCatalog 系统），同一包名下两套实现互斥，冲突按以下原则解决：

1. **dev3 的统一主题系统为存活方**：`ThemeController`/`ThemeProfile`/`ThemeProfileCodec`/`ThemeProfileStore`/`ThemeProfileValidator`/`ThemeResolver`/`ThemeTokens`/`ThemeBinder` 取 dev3 侧；`BaseActivity`（mobile/leanback）恢复 dev3 的 `applyNightModeToApp` + `applyFromPreferences` + `bindTheme` 通道。
2. **删除 beta 旧主题系统的落地文件**：`ThemeCatalog`/`ThemeCatalogStore`/`ThemeColorUtil`/`ThemeTransfer`/`ThemeTweakCnAdapter`、mobile `ThemeEditorDialog`/`ThemeImportDialog`/`ThemeExport`/`ThemeColorPickerDialog`/`ThemePreviewView`、`assets/themes/*`（含 `ThemeTvCatalogSourceTest` 等 5 个旧主题测试）。修正/删除/接受删除的类没有悬空引用。
3. **保留 beta 的非主题改动**：原生增强播放页统一焦点规范（`tvFocusRing`/`tvCurrentRing`/`tvNormalStroke` 三套主题属性 + `selector_video_item`/`selector_episode_card`/`selector_tmdb_cast_focus`/`selector_tmdb_media_focus`）、详情直放切集旧位置回写守卫（`inlinePlayerMediaReady`）、历史卡片重复已看时间移除（`historyProgress` 布局删除 + `HistoryProgressFormatter` 死代码清理）、纵向焦点链（`TmdbRowFocusChain`）与海报行 rowHeight 修复、融合/原生唤醒“记住用户焦点”修复（PR #387 增量）。
4. **`?attr/tvFocusRing` 三属性在双 flavor 都绑定**：因为 `selector_episode_card.xml` 等位于 `main/res` 为双 flavor 共用，mobile 的 `Theme.Base`（parent `Theme.WebHTV.Mobile`）与 leanback 的 `Theme.Base`（parent `Theme.WebHTV.TV`）都显式 `<item name="tvFocusRing">@color/tv_item_focus_ring</item>` 及同行 current/normal，取值与 beta 原版一致（`#FFD166`/`#2CC56F`/`#33FFFFFF`）。对话框上下文经 `Theme.WebHTV.Dialog`/`MaterialAlertDialog.WebHTV.Rounded` 继承到 Activity 主题，同样可解析。

## 冲突与复评修复记录

### 冲突清单（7 内容 + 1 改删）

| # | 文件 | 冲突 | 解决 |
| --- | --- | --- | --- |
| 1 | `TmdbCastPresenter.java` | dev3: `ThemeController` token 化卡面+焦点描边 vs beta: 焦点改前景 selector，常态 1dp | 保留 dev3 token 卡面 + beta 前景焦点环：`setCardBackgroundColor(tokens.colorSurfaceContainerHigh())` + `STROKE_NORMAL`/`STROKE_WIDTH_NORMAL_DP`，去掉双重描边（`colorFocus` 不再由 presenter 画） |
| 2 | `TmdbVideoPresenter.java` | dev3: `colorPlayerControlActive` 描边 vs beta: 前景 selector | 取 beta 前景方案，`bindFocusStyle`/`applyFocusChrome` 完整保留，移除 `ThemeController` 未用 import |
| 3 | `leanback styles.xml` | dev3 `Theme.Base` parent `Theme.WebHTV.TV` vs beta `Theme.Material3.Dark` + tv* 属性 | dev3 parent + beta tv* 三属性；`ThemeOverlay.WebHTV.Dialog`（dev3） |
| 4 | `mobile styles.xml` | 同 | dev3 parent + beta tv* 三属性 |
| 5 | `colors.xml` | dev3 token 化 display_option/site_health vs beta 字面量 + tv_item_* 新增 | dev3 token 侧 + beta `tv_item_focus_ring`/`tv_item_current_ring`/`tv_item_normal_stroke` 新增（值同 beta） |
| 6 | `adapter_vod.xml` | dev3 保留 `historyProgress` vs beta 删除（重复已看时间） | 取 beta 删除（dev3 无 adapter 引用、beta 有 `HistoryAdapterTest` 契约） |
| 7 | `ThemeTvCatalogSourceTest.java`（修改/删除） | dev3 删除（旧主题系统）vs beta 修改 | 接受 dev3 删除（旧主题系统已弃用） |

### 第 1 轮复评发现并修复的问题

| # | 问题 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | `NativeEnhancedPlaybackStyleFocusTest.everySelectableSurfaceSharesOneFocusSpec` FAIL | beta 新测试断言演员卡圆角 = 字面量 `14dp`，但 dev3 已 token 化为 `@dimen/webhtv_card_radius_large`（12dp） | 断言改为“焦点环圆角与卡片使用同一个 token”；`selector_tmdb_cast_focus.xml` 三处 `<corners>` 从 `14dp` 改为 `@dimen/webhtv_card_radius_large`，消除“描边内缩/外溢”不一致 |
| 2 | `ThemeControllerContractTest.followingAndDetailSurfacesUseSemanticAttributes` FAIL | dev3 既有测试断言 presenter 调用 `tokens.colorFocus()` / `colorPlayerControlActive()`，与 beta 的“焦点环走前景 selector”新设计矛盾（双重描边） | dev3 测试改为断言新契约：presenter 保留 `ThemeController.current()` + `colorSurfaceContainerHigh()` + `STROKE_NORMAL`，不再画焦点描边；video presenter 断言 `selector_tmdb_media_focus` |
| 3 | `TmdbCastPresenter.java` 合并后 `STROKE_FOCUSED` 残留语义冲突 | 合并把 dev3 的 `tokens.colorFocus()` 与 beta 的 `STROKE_NORMAL` 并存 | 统一为 beta 前景方案，`applyFocusStyle` 不再写焦点描边（见冲突 #1 解决） |
| 4 | `TmdbVideoPresenter.java` 未用 import `ThemeController`/`ResUtil` | 合并残留 | 移除未用 import；编译零告警 |

### 第 2 轮复评结论

- 全量双 flavor 单测通过（见“验证记录”），含新增 beta 测试 3 个 inspector 类（`NativeEnhancedPlaybackStyleFocusTest` 14 项、`TmdbRowFocusChainTest` 10 项、`PlayerControlFocusIntegrationTest` 10 项含 PR #387 增量 3 项）。
- `?attr/tv*` 链端到端闭环：attrs 声明 → colors 定义 → 双 flavor 绑定 → 4 个 selector/消费者；无悬空、无双绑定、无解析失败路径。
- `git diff --cached --check` 通过；无冲突标记；无同目录重复 view id / 资源名。
- 被剔除/回退提交均未进入合并结果（见“合并台账”）。

## 验证记录

- **双 flavor 全量单测**：`bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest :app:testMobileArm64_v8aDebugUnitTest --continue` → `BUILD SUCCESSFUL`；leanback **4186 项 / 0 failure / 0 error / 2 skipped**，mobile **5049 项 / 0 failure / 0 error / 2 skipped**（合计 9235 项）。
- **双 flavor Java 编译**：`:app:compileLeanbackArm64_v8aDebugJavaWithJavac`、`:app:compileMobileArm64_v8aDebugJavaWithJavac` → 通过。
- **静态守门**：`git diff --cached --check` 通过；仓库内无冲突标记；无重复 view id；无同目录重复资源名；`scripts/check_ui_tokens.sh --strict` 报告 1 个 `item_following.xml` 违规，经核对为 `origin/dev3` 既存（本次合并未触碰该文件，非本次引入）。
- **合并完整性复核**：`git diff --diff-filter=U --name-only` 为空（0 未解决冲突）；`git write-tree` 成功；`git rev-list --count HEAD..MERGE_HEAD` 为 0（beta 全部提交都在合并结果祖先中）；`git diff --diff-filter=D origin/beta <merge树>` 仅含 dev3 设计删除；被剔除提交 `5682f2b05` 非祖先。
- **打包与覆盖安装**：mobile 与 leanback 双 flavor `build_arm64_debug_install.sh --serial 192.168.50.3:5559` 均 `BUILD SUCCESSFUL`（129 tasks），覆盖安装成功（未卸载）。
- **设备冒烟（dev3 机位 `192.168.50.3:5559`）**：
  - 冷启动 `HomeActivityCurrent`，进程存活、无 `FATAL EXCEPTION` / `fatal signal` / `ANR`。
  - 设置 → 外观与语言：弹窗正常渲染「界面大小/主题模式/主题色彩/图片尺寸/语言」，无崩溃。
  - 设置 → 外观与语言 → 主题色彩：dev3 主题编辑器正常打开（浅色/深色、6 预设、16 槽包括「主色/主色容器/次色容器/焦点色」、继承默认/恢复默认/取消），无崩溃。
  - 主题色彩 → 焦点色 → 精确十六进制：颜色选择弹窗正常打开（EditText + 使用此颜色/取消/确定），无崩溃，进程存活。
  - theme 偏好键确认：`theme_profile_v2_json` / `theme_profile_v2_last_good` / `theme_profile_v2_schema` 三键存在。
  - leanback 覆盖安装后冷启动正常（进程存活、无崩溃）。
- **未验证边界**：未执行 native 重建、全 ABI 矩阵、实机播放矩阵（含真放流/切集/唤醒焦点键）、服务端工具链；上述证据不扩展为 ABI/全机型播放验收声明。

## PR 边界

相对 `origin/beta`（`b184f7363`），合并结果净差异 **530 文件、+14083 / −6572**。内容主体：

1. **dev3 统一语义主题系统**（相对 beta 新增 57 个文件）：`ThemeBinder`/`ThemeRole`/`ThemeColorIndex`/`ThemeEditor`/`ThemeMode`/`ThemeSeed`/`ThemeWebBridge`/`WebHtvAlertDialogBuilder`、`webhtv_*.xml` token 体系、`scripts/check_ui_tokens.sh`、简体/繁体/英文主题字符串。
2. **本次合入的 beta 新功能**：原生增强播放页统一焦点视觉与纵向焦点链（`tv*` 三属性 + 4 selector + `TmdbRowFocusChain` + 海报行 rowHeight + `NativeEnhancedPlaybackStyleFocusTest`/`TmdbRowFocusChainTest`）、详情直放与沉浸融合切集旧位置回写守卫（`inlinePlayerMediaReady`）、历史卡片重复已看时间移除（`HistoryAdapter`/`adapter_vod.xml`/死代码清理）、融合/原生唤醒“记住用户焦点”修复（PR #387，`PlayerControlFocusHelper`/`VideoActivity`/`TmdbDetailActivity`）。
3. **dev3 壁纸/弹窗文字可读性 8 个未推送提交** 全部保留。
4. **dev3 相对 beta 的删除（22 个）**：均为 beta 旧主题系统文件。

合并提交父节点：父 1 = `c53fce8d6`（本地 `dev3` HEAD）、父 2 = `b184f7363`（远端 `origin/beta` tip），使 `dev3` 成为 `origin/beta` 的直接后继。

PR 只做创建，不执行合并。

## 回滚锚点

- 合并前状态：`git reset --hard c53fce8d61b879b2033ec8d77fb873de9906eb51`。
- 合并提交（生成后）：`<提交号>`，带 recovery tag `recovery/C29-BETA-MERGE-REVIEW-DEV3-20260930/<时间戳>-<短哈希>`。
- 回滚：`git revert <本任务合并提交>`（全部改动为 UI/焦点/主题与常规功能，无数据格式迁移）。