# BETA-SYNC-DEV3-20260927：dev3 合并远端 beta 最新代码与两轮复评

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入本地 `dev3`，循环复评相对 `origin/beta` 与相对 `origin/dev3` 的全部已修改代码（含已提交未推送部分），发现问题即最小修复并验证到通过；通过后提交、推送 `dev3`、创建中文 PR 到 `beta`，只创建不合并；不得把远端已移除/剔除的回退提交重新带上来。
- **验收**：合并结果包含远端 `beta` 全部最新提交；被剔除提交不在结果祖先中；`dev3` 相对 `origin/beta` 的净差异只包含本分支自身改动；双 flavor 单测、编译、Debug 打包与覆盖安装、启动冒烟与主题链路实测通过；PR 用中文说明改动内容并保持排版清楚。
- **允许路径**：合并与冲突解决所需的全仓库路径（`.codex`、`README.md`、`app`、`docs`、`gradle`、`serverless`）。
- **保护面**：任务开始时工作树干净（0 个脏路径）。
- **分支/HEAD**：`dev3`；合并前 `dee79790408b67ce92fd0b2825b30a8ab9b07195`。
- **当前状态**：任务已完成。合并提交 `c900dac20e0a35229633d070c080a774d210d448`（双父：`dee797904` + `163cdf037`）已生成并带 recovery tag，`dev3` 已推送，PR #382（base `beta`）已创建。
- **下一动作**：无。等待 PR #382 评审；本方只创建、不代为合并。

## 时间与设备

- 开始时本地时间：2026-09-27 11:40（Asia/Shanghai）；收尾时：2026-09-28 03:03。
- 设备：`192.168.50.3:5559`（dev3 分配机位，Android 9 / sdk 28），`com.silent.android.webhtv`；未使用其它工作区机位。

## 基线与合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev3` |
| 合并前 HEAD | `dee79790408b67ce92fd0b2825b30a8ab9b07195` |
| 第一次拉取时 `origin/beta` | `09f8882251bff5fae9da2d87341242bd359ad4aa` |
| 合并期间新增的 `origin/beta` | `163cdf037`（PR #381，含 `5306e1bce`） |
| 最终合并第二父提交 | `163cdf037d5aaadacbe542690651155374d02c46` |
| 合并基点 | `16ee00828da7f7ab00ae6e53e1222264a54d1745` |
| `origin/beta..dev3` 合并前提交数 | 52 |
| 冲突文件数 | 29 |

- **远端 beta 最新代码已完整合入**：合并期间 `origin/beta` 从 `09f888225` 前进到 `163cdf037`（新增 PR #381「修复详情直放与沉浸融合模式切集沿用上一集播放进度」）。该增量触及我正在修改的 `TmdbDetailActivity.java`，因此把 `09f888225..163cdf037` 的增量应用到已解决冲突的工作树上，并把 `MERGE_HEAD` 更新为 `163cdf037`。已逐一核对 `5306e1bce` 的三处文件（`TmdbDetailActivity.java` 49+/2−、`TmdbDetailActivityLayoutTest.java` 39+/0−、`docs/fix-inline-switch-position-20260927.md` 51+/0−）全部落地，且 `git diff origin/beta -- TmdbDetailActivity.java` 中不再含 `inlinePlaybackSettled` 相关差异。
- **被移除/剔除提交核对**：`5682f2b054b577b0db5c6f7c1143f2eebb51858e`（“剔除 PR #353 主题系统改动”，`remove-pr353-theme-changes` 分支头）经 `git merge-base --is-ancestor` 判定**不是**合并结果祖先。该提交并未进入 `origin/beta`（只在同名特性分支上），因此本次合并不会把它带回来。
- **远端已移除的主题系统没有被重新提交**：`origin/beta` 自合并基点后经 `ac9586619`/`6aabb5b23` 重新引入的旧主题系统（`ThemeCatalog`/`ThemeCatalogStore`/`ThemeColorUtil`/`ThemeTransfer`/`ThemeTweakCnAdapter`、mobile `ThemeEditorDialog`/`ThemeImportDialog`/`ThemeExport`/`ThemeColorPickerDialog`/`ThemePreviewView`、`app/src/main/assets/themes/*` 与其 5 个 testMobile 测试）在合并中被判定为**已被 dev3 自身实现取代**，按 `docs/THEME-CUSTOMIZATION-LAYERS-20260921.md` 第 27/305/543 行的既定设计（“Layer 3：暂缓；不建议直接恢复历史分支中的完整 TweakCN/导入导出实现”）删除，未重新带入。

## 冲突解决原则

dev3 的 52 个未推送提交全部属于**统一主题系统**（`ThemeBinder`/`ThemeRole`/`ThemeColorIndex`/`ThemeEditor`/v2 16 槽 profile）。该系统**只在 dev3 存在**（`origin/beta`、`origin/dev1`、`origin/dev2`、`origin/dev4` 的主题包均为另一套不兼容实现；`origin/dev3` 主题包为 0 个文件），且 `ThemeBinder`/`webhtv_tokens.xml` 从未推送。因此同一包名下的两套实现互斥，冲突按以下原则解决：

1. **dev3 的统一主题系统为存活方**：`ThemeController`/`ThemeProfile`/`ThemeProfileCodec`/`ThemeProfileStore`/`ThemeProfileValidator`/`ThemeResolver`/`ThemeTokens` 及其 4 个单测取 dev3 侧；`BaseActivity`（mobile/leanback）恢复 dev3 的 `applyNightModeToApp` + `applyFromPreferences` + `bindTheme` 通道。
2. **删除 beta 旧主题系统的落地文件**（上一节所列），并移除其悬空引用。
3. **保留 beta 的非主题改动**：例如 `registerFragmentLifecycleCallbacks` 的 `TouchOptimizationHelper.sync`、`HistoryDialog` 的 `isProtectedCurrent` 保护、`dialog_tmdb_source.xml` 的路由下拉（`MaterialAutoCompleteTextView` + `ExposedDropdownMenu`）、`adapter_config.xml` 的 `MaterialButton` 边框、`Backup.java` 的 `SpeechAdSetting.sanitizePreferences` 等。
4. **采用 beta 的功能移除**：beta `1b42d6624`「移除手机版个性设置中的触屏优化入口」为有意的功能删除，且 `TouchOptimizationHelperSourceTest` 明确断言 `fragment_setting_personal.xml` 不含 `@+id/touchOptimization`，故该布局冲突取 beta 侧（删除该行）。

## 修复记录（复评发现并已修复）

第 1 轮复评与第 2 轮复评共发现 11 个失败测试，全部定位到根因并按“以 dev3 主题契约为准、保留 beta 非主题改动”的原则最小修复：

| # | 失败测试 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | `ThemeBinderContractTest#materialAlertDialogsAreBuiltThroughTheThemedBuilder` | 自动合并让 `TmdbSourceDialog` 路由选择器重新使用裸 `MaterialAlertDialogBuilder` | 改回 `WebHtvAlertDialogBuilder(dialogContext, R.style.Theme_WebHTV_Dialog)` |
| 2 | `ThemeBinderContractTest#binderRewritesTheMaterialButtonAndBackgroundTintChannels` | `activity_following.xml` 主按钮被 beta 的 `@color/following_button_*` 覆盖，丢失 `app:backgroundTint="?attr/colorPrimary"` | 三个按钮恢复 `?attr/colorPrimary` / `?attr/colorOnPrimary` / `?attr/colorOnSecondaryContainer` 语义属性 |
| 3 | `ThemeControllerContractTest#followingAndDetailSurfacesUseSemanticAttributes` | 同上 | 同上（修复 #2 后通过） |
| 4 | `UiStyleSourceTest#legacyDialogNamesRemainAliasesOnly` | 自动合并把 `dialog_tmdb_source.xml` 的一处样式换成 legacy `Widget.WebHTV.LightDialog.Helper` | 改回 `Widget.WebHTV.Helper` |
| 5 | `UiStyleSourceTest#dialogAndSettingLayoutsUseSemanticColors` | beta 新增的 `dialog_config.xml` 文本块使用 `#202124`/`#5F6368`/`#8A8F98`/`@color/white` 固定浅色 | 均改为 `?attr/colorOnSurface`/`?attr/colorOnSurfaceVariant`/`?attr/colorSurfaceContainerHighest`（mobile 1 处、leanback 4 处） |
| 6 | `InterfaceEntryInteractionTest#leanbackConfigNameLooksClickableBeforeItReceivesFocus` | beta 断言固定浅色 `#D2E3FC`/`#C8CDD2`/`#0B57D0`，与 dev3 的「选择器必须解析 webhtv token」守门契约冲突 | 断言改为语义 token（`@color/webhtv_color_primary_container`、`@color/webhtv_color_outline`、`@color/webhtv_color_primary`），保留「未聚焦也有 2dp 边框」的原意 |
| 7 | `BackupPreferenceFilterTest#themeProfilePreferencesFollowSettingsOption` | beta 的测试仍断言 v1 键 `theme_profile_*`，而 dev3 契约明确只用 `theme_profile_v2_*` | 断言改为 v2 键并显式断言 v1 键不得回归 |
| 8 | `QuickAdapterSelectionTest#boundCurrentSiteStaysActivatedAndOtherSitesDoNot` | beta 测试用裸 application context 加载依赖 `?attr/colorOnSurface` 的 leanback 布局 | 改用 `ContextThemeWrapper(application, R.style.Theme_App)` |
| 9 | `TmdbUIAdapterTest#tmdbDetailActivityPassesMemoryDetailCacheKeyToDirectPlayback` | beta `d73c7eecb` 给 `TmdbDetailCache.put` 加了 language 参数但未更新本测试 | 断言改为匹配调用前缀（容忍尾部 language 实参） |
| 10 | `TmdbUIAdapterTest#leanbackDirectTmdbPlaybackHydratesSynopsisWithoutFullDetailBind` | beta `d73c7eecb` 把 `take` 改 3 参、把简介改为 `TmdbService.translatedOverview`，但未更新本测试 | 断言改为当前契约；`cachedTmdbOverviewForLanguage` 的断言改为 `translatedOverview(detail, currentTmdbConfig())`（beta 自己的 `TmdbDetailDirectPlayTransitionSourceTest` 同步禁止旧字符串，二者原本互相矛盾） |
| 11 | （合并期间新增 PR #381 后不计入） | — | — |

**关于 #9/#10 的判定证据**：`git log -S` 证明 `TmdbDetailCache.take(..., getTmdbItem())` 两参形式自 `d73c7eecb`（2026-09-25）起就不再存在，而该提交的 `--stat` 不含 `TmdbUIAdapterTest`，即 beta 自 2026-09-25 起该测试即为红灯（陈旧断言，非本次合并引入）。修复只改测试断言，不改运行时代码。

## 验证记录

- **双 flavor 全量单测**：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest :app:testLeanbackArm64_v8aDebugUnitTest` → `BUILD SUCCESSFUL`；mobile **5032 项 / 0 failure / 0 error / 2 skipped**，leanback **4160 项 / 0 failure / 0 error / 2 skipped**（合计 9192 项）。
- **双 flavor Java 编译**：`:app:compileMobileArm64_v8aDebugJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` → 通过；两个测试源集编译任务同样通过。
- **静态守门**：`git diff --cached --check` 通过；全仓库无冲突标记残留；布局无重复 view id；无同目录重复资源名。
- **合并完整性复核（提交前）**：`git diff --diff-filter=U --name-only` 为 0（无未解决冲突）；全仓库无冲突标记；被剔除提交 `5682f2b05` 经 `git merge-base --is-ancestor` 判定既非 `origin/beta` 祖先也非合并结果祖先；`app/src/main/java/com/fongmi/android/tv/theme/ThemeCatalog.java`、`ThemeCatalogStore.java` 在工作树中不存在（beta 旧主题系统未被带回），dev3 侧 `ThemeBinder` / `ThemeEditor` / `ThemeTokens` 等存活。
- **打包与覆盖安装**：`bash scripts/build_arm64_debug_install.sh --flavor mobile --serial 192.168.50.3:5559` → `BUILD SUCCESSFUL in 1m`（129 tasks），APK 193M，`adb install -r` 覆盖安装成功（未卸载）。
- **设备实测（dev3 机位 `192.168.50.3:5559`，mobile arm64 debug）**：
  - 冷启动进入 `HomeActivityCurrent`，进程存活、无 `FATAL EXCEPTION` / `fatal signal` / `ANR`。
  - 设置 → 外观与语言：弹窗正常渲染「主题模式 / 主题色彩 / 界面大小 / 语言 / 图片尺寸」，无崩溃。
  - 设置 → 外观与语言 → 主题色彩：dev3 主题编辑器正常打开（16 槽「主色 / 主色容器 / 次色容器 / 焦点色」、8 个预设、浅色/深色、「继承默认」「恢复默认」「应用」），精确十六进制弹窗可打开，无崩溃，进程存活。
  - 选择预设并「应用」后返回设置页，应用继续运行；设备偏好确认写入 **`theme_profile_v2_json` / `theme_profile_v2_last_good` / `theme_profile_v2_schema`** 三个 v2 键。
  - 验收后取设备偏好快照比对，`shared_prefs` 键集合与取值**完全未变**（无新增/删除/修改键），未卸载现有包，未占用其它工作区机位。
- **未验证边界**：未执行 native 重建、全 ABI 矩阵、实机播放矩阵、Go/Rust/Deno/Vercel 服务端工具链测试；上述证据不扩展为 ABI 或全机型验收声明。

## PR 边界

相对 `origin/beta`，合并提交 `c900dac20` 的净差异为 **532 个文件、+13472 / −6570**（`app/src` 525 个文件；`docs/` 5 篇设计文档；`scripts/check_ui_tokens.sh`）。其中相对 beta 新增 55 个文件、删除 22 个（均为 beta 旧主题系统文件）、修改 455 个。

合并提交本身相对其第一父提交 `dee797904` 的差异为 **258 个路径**，即本次冲突解决与复评修复的全部落地改动。

内容主体是 dev3 的统一语义主题系统（Layer 1 + B-safe 16 槽）及其配套的移动/电视端接入、追更页壁纸面板、`WebHtvAlertDialogBuilder` 弹窗通道、`scrimOpacity` 接线与 `WebThemeTokenSourceTest` 等；同时完整包含本次从 `origin/beta` 合入的全部远端改动。

## 回滚锚点

- 合并前状态：`git reset --hard dee79790408b67ce92fd0b2825b30a8ab9b07195`，另有注释 tag `backup/dev3-before-beta-merge-20260927`。
- 合并提交：`c900dac20e0a35229633d070c080a774d210d448`，recovery tag `recovery/BETA-SYNC-DEV3-20260927/20260928110441-c900dac20e0a`。
- 远端分支：`origin/dev3` 已从 `e36207984` 快进到 `c900dac20`。
- 本轮不涉及依赖升级、ABI、原生二进制或数据迁移；未修改 `theme_profile_v2_*` 数据格式。

## 状态与下一步

- 两轮复评、修复与全部验证已通过。
- 合并提交 `c900dac20e0a35229633d070c080a774d210d448` 已创建（父提交 `dee797904` + `163cdf037`）并带 recovery tag；`dev3` 已推送；PR #382（base `beta` / head `dev3`，中文说明）已创建：https://github.com/Silent1566/webhtv/pull/382
- 下一步唯一动作：无。等待 PR #382 评审；只创建、不代为合并。
