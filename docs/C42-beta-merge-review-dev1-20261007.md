# C42：dev1 合并远端 beta 最新代码（PR#412/#413）并复评已修改代码

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev1`（**远端已移除/回退的提交不得顺带带回**）；复评 dev1 全部已修改代码（含已提交未推送的 `6414185af`）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev1`、创建 `dev1 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并结果第二父为 `origin/beta` tip `2a2139beb`；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev1 既有改动**零丢失**；④ 双 flavor Java 与 androidTest 编译通过；⑤ 双 flavor 全量 JVM 套件零失败；⑥ UI token 门禁相对基线零新增违规；⑦ 净差异只含本分支自身改动；⑧ 提交 + recovery tag；⑨ `dev1` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并完成（0 冲突）；4 轮评审完成——第 1 轮发现并修复 1 处真实的契约覆盖缺口（对应生产缺陷的第三个站点），第 2/3/4 轮为订正我自己新增断言的脆弱性并复评通过；全部验证通过；已提交、已推送、PR 已创建且未合并。
- **交付坐标**：提交 `84d76f4c592ad80acdab61e3439f80712498544d`（第二父 `2a2139bebc9cc7dd02ec421a68d0f027baf547ef`）；recovery tag `recovery/C42-beta-merge-review-dev1/20261007130522-84d76f4c592a`；`dev1` 已推送至 `origin/dev1`（0/0 同步）；PR [#414](https://github.com/Silent1566/webhtv/pull/414) `dev1 → beta`，状态 **OPEN、未合并**、MERGEABLE，4 文件 +273 −10。
- **下一动作**：无（任务已收口）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1` |
| 任务开始时 HEAD | `6414185aff91edd15ad96f81bffaccea3a72f87e`（`修复短剧源规则无法禁用`，领先 `origin/dev1` 2 个提交） |
| `origin/beta` tip | `2a2139bebc9cc7dd02ec421a68d0f027baf547ef`（Merge PR #413 from dev3） |
| 合并基点（merge-base） | `9b168a1f3805614547308a638a13ddae45a8f564`（Merge PR #411 from dev1） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`，由 task_guard `finish` 创建合并提交 |
| 合并结果 | 19 路径自动合入，**0 冲突、0 冲突标记** |
| 合并结果树（本任务文档写入前） | `de7921410bda2fb84759cdff262b936015798d32`（19 路径合入后的索引树，用于 beta 增量/dev1 增量的逐字节比对） |
| 提交树（含本任务文档） | `4351c0fdcb2e8388e45a77fe4f1af7d0181088b8` |
| 初始脏路径 | 无（`git status` 干净） |
| 回滚锚点 | `6414185aff91edd15ad96f81bffaccea3a72f87e` |
| 任务守卫 | `C42-beta-merge-review-dev1`（standard，scope `app/src` + `docs` + `scripts` + `.codex/scripts` + `.codex/task-state`） |

### beta 增量 ledger（10 个提交，全部纳入）

`git log --oneline HEAD..origin/beta`：

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `2a2139bebc9cc7dd02ec421a68d0f027baf547ef` | Merge PR #413 from dev3 | 纳入 |
| `12f2639b8` | merge：合并 origin/beta（PR#410/#411/#412）并复评修复 TV 固定深色面板对比度回归 | 纳入 |
| `53b54d1b6` | Merge PR #412 from dev2 | 纳入 |
| `3374def64` | merge：合并 origin/beta（PR#411）并复评修复播放加载圈守卫泄漏 | 纳入 |
| `f77c4a023` | `fix(tv): keep playback loading visible until new player starts` | 纳入 |
| `5ff8037e5` | `docs(tv)`：补充浅色模式路径的设备验证结果 | 纳入 |
| `009d70842` | `docs(tv)`：记录最终提交后的设备重新验证结果 | 纳入 |
| `8624f6c0d` | `refactor(tv)`：删除 `AppearanceRowTheme` 中未被引用的三个辅助方法 | 纳入 |
| `663ac5e7d` | `fix(tv)`：修复外观与语言二级选择弹窗标题不可读 | 纳入 |
| `57362eb4f` | `fix(tv)`：修复外观与语言对话框行文字不可读 | 纳入 |

**无一条与 dev1 既有实现重复或被取代**，故全部纳入。beta 增量内容为：TV 固定深色面板的调色板跟随前景回归修复（`dialog_exit_confirm` / `dialog_audio_comment` / `dialog_disc_menu` / `dialog_display`，新增 `TvFixedDarkSurfaceContrastTest`）、`AppearanceRowTheme` 行渲染器与两个 flavor 的 `AppearanceDialog` 同源取色、`ChoiceDialog` 标题/消息改用当前调色板、TMDB 豆瓣评分徽标前景改为两套色板恒定的 `tmdb_douban_rating_green`、leanback 播放加载圈守卫（`mPlaybackRequestActive` / `mPlaybackPlayerStarted`）、C42 两份文档与 `TV-APPEARANCE-DIALOG-CONTRAST-20261005` 文档。

## 用户核心关注点：远端已移除（回退）内容零复活

**程序化校验方法**（按行比对，不依赖人工目测）：对 beta 历史上每个**单亲 revert/移除类提交** `R` 取父 `R^`，得到 `removed(R,f) = lines(R^:f) − lines(R:f)`；复活定义为「该行出现在合并结果中，且合并前 dev1（`6414185af`）中不存在」。

```text
candidate single-parent revert/removal commits on beta : 116
file-versions checked                                 : 550
removed lines scanned                                 : 117040
resurrected lines                                     : 0
```

### 关键目标提交：`5682f2b05`「剔除 PR #353 主题系统改动」

该提交位于分支 `remove-pr353-theme-changes`，**从未进入 `origin/beta`**（`git merge-base --is-ancestor 5682f2b05 origin/beta` → 否），正是用户反复强调的「远端已经移除/回退的提交」。它触碰 70 个路径，逐一与合并结果比对：

```text
5682f2b05 touched paths: same=70 differs=0
```

即合并结果中这 70 个路径**全部与 `origin/beta` 逐字节一致**，零带回。被它剔除的旧主题系统文件（`ThemeCatalog` / `ThemeCatalogStore` / `ThemeColorPickerDialog` / `ThemeEditorDialog` / `ThemeImportDialog` / `ThemeExport` / `ThemePreviewView`、`assets/themes/*`、`mobile` 旧主题布局与测试）在合并结果与 `origin/beta` 上**同为缺席**（两侧同为 `MISSING`，故比对判为一致，属预期）。

### 双向完整性

```text
beta 增量零丢失：merge-base..2a2139beb 的 19 个路径
                 → 在合并结果中逐文件与 2a2139beb 比对，全部字节一致
dev1 既有零丢失：6414185af 相对 merge-base 的 3 个路径
                 → 在合并结果中逐文件与 6414185af 比对，全部字节一致
合并结果净差异：git diff --cached --name-status origin/beta
  = 3 个路径，全部为 dev1 自身改动（短剧源规则修复）
```

## 评审循环记录

### 第 1 轮：发现 1 处真实缺陷（用户报告的「无法禁用」在第三个站点仍可复现，且契约抓不到）

**问题（F1）**：dev1 未推送提交 `6414185af` 修复「短剧源规则无法禁用」，做法是把「空规则 ⇒ 回填默认关键词」的判定从 `enabledSites.isEmpty()` 改为 `isConfigured()`，共动了 `ShortDramaSourceDialog` 的 **3 个站点**：

1. `show()` 初始化 `tempEnabledRules`；
2. `showSiteManage()` 计算站点勾选态；
3. `updateChipsDisplay()` 画 chip。

但新增的 `ShortDramaConfigTest.dialogDoesNotRecreateDefaultsAfterRemovingAllRules` 只钉住了 **2 个**（`show()` 的初始值与 `resetToDefault()` 的显式回填），`showSiteManage()` 那一处没有任何契约。

**反证（证明这是真实缺陷，不只是覆盖缺口）**：把 `showSiteManage()` 改回旧写法后重跑测试——**测试仍然全绿**，而行为已经回归：

```text
修复后 enabledRules = tempEnabledRules = []      → 站点勾选=False → 确定后 tempEnabledRules=[]
回退后 enabledRules = 默认['[短]','短剧']          → 站点勾选=True  → 确定后 tempEnabledRules=['[短]','短剧']
```

即用户已清空全部规则（`configured=true, enabledSites=[]`）后，只要打开一次「站点管理」，站点会被重新勾选并把默认关键词写回，**用户报告的缺陷从该路径复现**。这正是本任务要防的回归，而契约抓不到。

**修复**：在 `dialogDoesNotRecreateDefaultsAfterRemovingAllRules` 中补上第三个站点与两条更强的语义不变量：

```java
assertTrue(source.contains("tempEnabledRules = new ArrayList<>(config.isConfigured()"));
assertFalse(source.contains("tempEnabledRules.isEmpty()"));      // 兜底判定式必须彻底消失
assertFalse(source.contains("defaultRulesText"));                // 不得再用它回填默认
assertTrue(source.contains(": ShortDramaConfig.defaultRules());"));                        // ① 未配置时的初始值
assertTrue(source.contains("tempEnabledRules.addAll(ShortDramaConfig.defaultRules());"));  // ② 用户点「恢复默认」
assertTrue(count(source, "ShortDramaConfig.defaultRules()") <= 2);                         // 不得有第三个入口
```

**反证（修复后的契约确实可失败）**，两次注入分别触发：

```text
反证 A：showSiteManage() 注入第三处 defaultRules() 兜底
        ShortDramaConfigTest > dialogDoesNotRecreateDefaultsAfterRemovingAllRules FAILED
反证 B：show() 去掉 isConfigured() 默认初始值
        ShortDramaConfigTest > dialogDoesNotRecreateDefaultsAfterRemovingAllRules FAILED
```

两次注入均已完全回滚：`ShortDramaConfig.java` 与 `ShortDramaSourceDialog.java` 与 `6414185af` 及合并索引**逐字节一致**，本任务**未改动任何生产源码**。

### 第 2 轮：订正我自己新增断言里的「钉死写法」（F2）

第 1 轮补的 `List<String> enabledRules = new ArrayList<>(tempEnabledRules);` 把**不必要的拷贝写法**当成了契约——`new ArrayList<>(...)` 是可选的，正当重构会误报。改为只钉语义不变量（默认规则的合法入口数 ≤ 2），不再依赖具体写法。

### 第 3 轮：订正注释误伤（F3）

新增的 `assertFalse(source.contains("defaultRulesText"))` 会匹配到**注释文本**：`ShortDramaSourceDialog` 与测试自身的注释里都提到该符号，而契约的本意是禁止可执行代码使用它。按仓库既有约定（`AppearanceRowThemeTest`、`TvFixedDarkSurfaceContrastTest` 都用 `codeOnly()` 先剥注释）加 `codeOnly()`，让契约匹配可执行代码。

### 第 4 轮：复评通过

- **合并正确性**：0 冲突；19 路径 beta 增量与 `2a2139beb` 逐字节一致；3 路径 dev1 增量与 `6414185af` 逐字节一致；116 个 revert/移除类提交零复活（550 文件版本 / 117040 行扫描）；`5682f2b05` 的 70 个路径零带回；无冲突标记；`git diff --check` 退出码 0。
- **beta 带入的 3 类改动复评**：
  1. **TV 固定深色面板对比度修复**：4 个面板（`shape_display_dialog_panel` = `webhtv_color_player_scrim`、`shape_disc_menu_panel` = `#F21C2028`、`shape_audio_playlist_panel` = `#E6191B22`、`selector_exit_confirm_primary` = `#174EA6`/`#0B57D0`）在两套 token 表下均为深色且**不含 `?attr/`**；替换后的前景 `?attr/webhtvColorOnWallpaper`（两表均 `#FFFFFF`）与 `@color/webhtv_color_player_control_muted`（两表均 `#CCFFFFFF`）在两套表下同值。复算对比度：日间 `#1A1C1E` 压这些深底仅 1.02–2.68:1（正是被修的回归），替换后 5.08–17.38:1。`TvFixedDarkSurfaceContrastTest` 用结构遍历（而非文件清单）覆盖同类组合，并含**可失败性反证**（`theGuardDetectsTheDefectItWasWrittenFor` 喂入实测前缺陷标记并要求命中）。
  2. **`AppearanceRowTheme` + 两个 flavor 的 `AppearanceDialog`**：行填充/描边/两个文字色全部来自同一个 `ThemeTokens` 实例；`apply()` 是唯一入口（`refresh()/textColors()/color()` 已在 `8624f6c0d` 删除，无推测性接口面）；`onStart()` 与 `onThemeProfileApplied()` 两处重绘保证 profile 变更能到达已建行；`selector_git_cloud_card` 在该对话框已彻底移除（其余引用在 OSD/CSP/播放器按钮配置/`GitCloudDialog`，属 allowlist 内既有用途，未扩大范围）。`AppearanceRowThemeTest` 的对比度断言是**纯算术**（不依赖设备），并在 `theRemovedFixedFillIsExactlyTheMeasuredUnreadableOne` 里把旧 `#F8F9FA` 上的 <2.0:1 钉成数字。
  3. **`ChoiceDialog` 标题/消息改调色板**：标题与消息直接画在 `shape_shell_proxy_dialog`（`?attr/colorSurfaceContainerHigh`）上，故改为 `colorOnSurface()` / `colorOnSurfaceVariant()`；选项行**未动**是**正确**的——它们画在自有不透明白色卡片（`#FFFFFF` / `#E8F0FE`）上，与面板无关，`styleItem()` 里的 `#202124`/`#174EA6` 是刻意的固定浅色卡片配色。
  4. **`tmdb_douban_rating_green`**：新增色只为 `adapter_tmdb_recommendation_landscape` 引入，该徽标底是 `shape_episode_card_badge`（固定 `#B3000000`，压在海报图上，与调色板无关），故前景必须是两套色板恒定的浅绿。复算 `#78E08F` 压在合成底上 12.12:1，而旧写法 `webhtv_color_success` 日间 `#146C2E` 仅 3.03:1。注释里「与 `TmdbRailAdapter` 里既有的 `0xFF78E08F` 保持一致」经核实成立（`TmdbRailAdapter:131` 运行时代码确实设置该值）。
  5. **leanback 播放加载圈守卫**：`mPlaybackRequestActive` 有 1 个设置点（`beginPlayerContentRequest`）与 5 个释放点（`onNewIntent`、`setPlayer` 重复结果早退、`setPlayer` 的 `redirectToContentHandler` 早退、`onStateChanged` 消费、`onError`），另有 `hidePlaybackProgressIfStale` 的兜底门；`hideSeekProgressIfReady` 未加守卫是**正确**的（它是 500ms seek 收尾兜底，不是新一集请求的收圈路径）。守卫与 `mAppliedPlayerResult` 的早退分支已在 `3374def64` 中一并释放，不存在「两个守卫互相锁死」的残留。
- **相邻但未修改（仅报告，不扩大范围）**：`dialog_ai_recommendation_info.xml` 的 `doubanRating` / `tmdbRating` 用固定浅底 `shape_douban_rating_chip_light`（`#EAF6EE`）+ 跟随调色板的 `webhtv_color_success` / `webhtv_color_warning`，夜间表下实测 1.45–1.48:1。该文件在合并前、`origin/beta` 与合并结果中 **sha 完全相同**（`854c2e2fa`），不是本次合并或本任务引入，且不在用户报告路径上，按 AGENTS.md §2 不修改；如需处理建议单独立任务。

## 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 双 flavor Java 编译 | `:app:compile{Leanback,Mobile}Arm64_v8aDebugJavaWithJavac` | `BUILD SUCCESSFUL` |
| 双 flavor androidTest 编译 | `:app:compile{Leanback,Mobile}Arm64_v8aDebugAndroidTestJavaWithJavac` | `BUILD SUCCESSFUL` |
| 手机版全量单测 | `:app:testMobileArm64_v8aDebugUnitTest` | `5214 tests / 0 failures / 0 errors / 2 skipped`（754 suites） |
| TV 版全量单测 | `:app:testLeanbackArm64_v8aDebugUnitTest` | `4365 tests / 0 failures / 0 errors / 2 skipped`（674 suites） |
| 短剧配置契约 | `ShortDramaConfigTest` | 双 flavor 各 `3 tests / 0 failures` |
| 主题契约 | `AppearanceRowThemeTest` 7、`ThemeControllerContractTest` 8、`TvFixedDarkSurfaceContrastTest` 4、`ThemeContractTest` 5、`ThemeBaseWiringTest` 13、`LeanbackForegroundContrastTest` 3、`TvFocusRingContractTest` 8 | 全部 `0 failures / 0 errors` |
| 播放路由/布局契约 | `ReaderPlaybackRoutingSourceTest` 28、`VideoActivityLayoutTest` 154 | 全部 `0 failures / 0 errors` |
| UI token 门禁 | `bash scripts/check_ui_tokens.sh --strict` | `violations=1 legacy=0`；唯一违规 `mobile/item_following.xml` 在合并前同样含 2 处硬编码色且**不在本次净差异中**，相对基线零新增 |
| 空白/冲突门禁 | `git diff --check` / 冲突标记扫描 | 退出码 0 / 0 处 |
| 合并完整性 | 见上「用户核心关注点」 | beta 增量 19 路径零丢失、dev1 增量 3 路径零丢失、revert 零复活、`5682f2b05` 70 路径零带回 |

### 可失败性反证（证明契约非空转）

| 反证 | 注入 | 结果 |
| --- | --- | --- |
| A | `showSiteManage()` 改回 `tempEnabledRules.isEmpty() ? defaultRulesText() : ...` | `ShortDramaConfigTest > dialogDoesNotRecreateDefaultsAfterRemovingAllRules FAILED` |
| B | 注入第三处 `ShortDramaConfig.defaultRules()` 兜底 | 同上 `FAILED` |
| C | `show()` 去掉 `isConfigured()` 默认初始值 | 同上 `FAILED` |

三次注入均已完全回滚，生产源码与 `6414185af` / 合并索引逐字节一致。

## 回滚

revert 合并提交即回到 `6414185aff91edd15ad96f81bffaccea3a72f87e`（短剧源规则修复本身不受影响，本任务未改动生产源码）。
