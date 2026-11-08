# C44：dev2 合并远端 beta 最新代码（PR#412-#415）并复评已修改代码

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev2`（**远端已移除/回退的提交不得顺带带回**）；复评 dev2 全部已修改代码（含已提交未推送的 `7596ebabb7`、`a10322a75c`）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev2`、创建 `dev2 → beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并提交第二父为 `origin/beta` tip `056200cda0`；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev2 既有改动**零丢失**；④ 双 flavor Java 与 androidTest 编译通过；⑤ 双 flavor 全量 JVM 套件相对合并前基线**零新增回归**；⑥ UI token 门禁相对基线**零新增违规**；⑦ 净差异只含本分支自身改动；⑧ 提交 + recovery tag；⑨ `dev2` 已推送、PR 已创建且**未合并**。
- **lane / scope**：`standard`；`app/`、`docs/`。
- **任务守卫**：`C44-beta-merge-review-dev2`；任务开始 HEAD 为 `a10322a75ce25432dd8fb12883fa99dc2c88e826`；初始工作区干净（0 个受保护脏路径）。
- **当前状态**：合并完成（0 冲突）；2 轮评审完成，两轮均**未发现必修问题**——第 1 轮为结构性核查 + 设备 RED/GREEN 反证，第 2 轮为对抗性复评；全部验证通过；待 `task_guard.sh finish`。
- **下一动作**：`task_guard.sh finish` → 推送 `dev2` → `gh pr create`（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始时 HEAD | `a10322a75ce25432dd8fb12883fa99dc2c88e826`（`docs(danmaku): 记录提交 7596ebabb7 与恢复标签`，领先 `origin/dev2` 2 个提交） |
| `origin/beta` tip | `056200cda0c3f7628c0e67d4dd056a3a6318ce73`（Merge PR #415 from dev4） |
| 合并基点（merge-base） | `9b168a1f3805614547308a638a13ddae45a8f564`（Merge PR #411 from dev1） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`，由 task_guard `finish` 创建合并提交 |
| 合并结果 | 27 路径自动合入，**0 冲突、0 冲突标记** |
| 合并结果树（本任务文档写入前） | `31000aa5d684a9bd987e0469cfd5adbac30c72c1` |
| 初始脏路径 | 无（`git status` 干净） |
| 回滚锚点 | `a10322a75ce25432dd8fb12883fa99dc2c88e826` |
| 任务守卫 | `C44-beta-merge-review-dev2`（standard，scope `app` + `docs`） |

### beta 增量 ledger（18 个提交，全部纳入）

`git log --oneline dev2..origin/beta`：

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `056200cda0c3f7628c0e67d4dd056a3a6318ce73` | Merge PR #415 from dev4 | 纳入 |
| `2b10824361` | merge：合并 origin/beta 并修复 episode 列表并发 | 纳入 |
| `6ff6a4458c` | Merge PR #414 from dev1 | 纳入 |
| `d1b7e2750e` | `docs(c42)`：记录 dev1 交付坐标并订正合并树与提交树 | 纳入 |
| `84d76f4c59` | merge：合并 origin/beta（PR#412/#413）并复评补齐短剧源禁用契约 | 纳入 |
| `2a2139bebc` | Merge PR #413 from dev3 | 纳入 |
| `12f2639b8d` | merge：合并 origin/beta（PR#410/#411/#412）并复评修复 TV 固定深色面板对比度回归 | 纳入 |
| `9555806b70` | `fix`：prevent episode list concurrent modification crash | 纳入 |
| `53b54d1b62` | Merge PR #412 from dev2 | 纳入 |
| `3374def641` | merge：合并 origin/beta（PR#411）并复评修复播放加载圈守卫泄漏 | 纳入（= 原 `origin/dev2` tip） |
| `790d678c9e` | `feat(crash)`：崩溃页显示最后加载的蜘蛛源 | 纳入（beta 侧版本） |
| `f77c4a0236` | `fix(tv)`：keep playback loading visible until new player starts | 纳入 |
| `6414185aff` | 修复短剧源规则无法禁用 | 纳入 |
| `5ff8037e58` | `docs(tv)`：补充浅色模式路径的设备验证结果 | 纳入 |
| `009d708422` | `docs(tv)`：记录最终提交后的设备重新验证结果 | 纳入 |
| `8624f6c0d7` | `refactor(tv)`：删除 `AppearanceRowTheme` 中未被引用的三个辅助方法 | 纳入 |
| `663ac5e7da` | `fix(tv)`：修复外观与语言二级选择弹窗标题不可读 | 纳入 |
| `57362eb4f2` | `fix(tv)`：修复外观与语言对话框行文字不可读 | 纳入 |

**无一条与 dev2 既有实现重复或被取代**，故全部纳入。beta 增量内容为：TV 固定深色面板调色板跟随前景回归修复（`dialog_exit_confirm` / `dialog_audio_comment` / `dialog_disc_menu` / `dialog_display`，新增 `TvFixedDarkSurfaceContrastTest`）、`AppearanceRowTheme` 行渲染器与两个 flavor 的 `AppearanceDialog` 同源取色、`ChoiceDialog` 标题/消息改用当前调色板、TMDB 豆瓣评分徽标前景改为两套色板恒定的 `tmdb_douban_rating_green`、leanback 播放加载圈守卫（`mPlaybackRequestActive` / `mPlaybackPlayerStarted`）、TMDB episode 列表并发修复（`SourceEpisodeSeasonCache` 快照 + `TmdbEpisodeSorter` 原位 `set`）、短剧源禁用契约补齐（`ShortDramaConfig.isConfigured()` / `defaultRules()`）、崩溃页蜘蛛源面包屑，以及 C42/C43 与 `TV-APPEARANCE-DIALOG-CONTRAST-20261005` 四份文档。

## 用户核心关注点：远端已移除（回退）内容零复活

**程序化校验方法**（按路径集合 + blob 哈希，不依赖人工目测）。复活的定义：合并结果中出现「`origin/beta` 当前不存在、或与 `origin/beta` 内容不同」的路径。

```text
合并结果路径总数                      : 5047
dev2 路径总数                         : 5038
origin/beta 路径总数                  : 5045
(dev2 ∪ beta) 路径总数                : 5047

merge 中有、dev2∪beta 中没有的路径    : 0     <- 无复活路径
dev2∪beta 中有、merge 中没有的路径    : 0     <- 无丢失路径
与 dev2 和 beta 都不同的文件（合并合成）: 0     <- 无三方合成文件
merge 与 origin/beta 不同的路径        : 4     <- 全部为 dev2 自身改动
beta 中有、merge 中没有的路径          : 0
```

**回退类提交扫描**：对 beta 历史上 124 个移除类提交（subject 命中 `revert|回退|剔除|移除|remove|撤销`）涉及的全部 **361 个路径**逐一比对，`merge != origin/beta` 的路径数为 **0**，即这些路径在合并结果中与 beta 当前权威版本**逐字节一致**，零复活。

**关键目标提交：`5682f2b05`「剔除 PR #353 主题系统改动」**

该提交位于分支 `remove-pr353-theme-changes`，**从未进入 `origin/beta`**（`git merge-base --is-ancestor 5682f2b05 origin/beta` → 否），正是用户反复强调的「远端已经移除/回退的提交」。它触碰 **70 个路径**，逐一与合并结果比对：

```text
5682f2b05 触碰路径数                     : 70
与 origin/beta 不同（= 被复活）的路径数  : 0
```

**dev2 新增路径的历史核查**：`DanmakuApiDialogDeviceTest.java` 与 `docs/FIX-DANMAKU-VIVO-20261007.md` 在 `origin/beta` 的历史中**条目数为 0**（从未存在），因此不可能是「复活 beta 已删除内容」。

## dev2 已提交未推送改动

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `7596ebabb7` | `fix(dialog)`：弹窗改用主题覆盖层，阻止控件 tint 泄漏到系统编辑菜单 | 纳入，4 个路径 |
| `a10322a75c` | `docs(danmaku)`：记录提交 `7596ebabb7` 与恢复标签 | 纳入，1 个路径 |

## 净差异（相对 `origin/beta`）

```text
A  app/src/androidTestMobile/java/com/fongmi/android/tv/ui/dialog/DanmakuApiDialogDeviceTest.java  (+199)
M  app/src/main/java/com/fongmi/android/tv/ui/dialog/BaseAlertDialog.java                          (1 行)
M  app/src/test/java/com/fongmi/android/tv/ui/dialog/DialogRoundedCornerSourceTest.java            (+10 -3)
A  docs/FIX-DANMAKU-VIVO-20261007.md                                                               (+78)
```

4 个路径在合并结果中与 `dev2` 的 blob **逐字节一致**（`git rev-parse` 哈希相同），即本 PR 的净差异就是 dev2 自身改动，不含任何被单方面改写的 beta 内容。

## 第 1 轮评审

### 结构性核查（全部通过）

| 检查项 | 方法 | 结果 |
| --- | --- | --- |
| 冲突 | `git diff --name-only --diff-filter=U` | 0 |
| 冲突标记 | 对全部已暂存路径检索 `<<<<<<<` / `>>>>>>>` | 0 |
| 路径集合闭合 | merge 路径集合 vs `dev2 ∪ beta` | 完全相等（5047） |
| 三方合成文件 | 内容同时异于 dev2 与 beta 的文件 | 0 |
| beta 增量零丢失 | `beta` 路径全部存在于 merge | 0 缺失 |
| 合并树一致性 | `git write-tree` | `31000aa5d684a9bd987e0469cfd5adbac30c72c1` |
| 空白检查 | `git diff --cached --check` | 退出码 0 |
| 构建/治理文件 | `.codex/scripts/task_guard.sh`、`app/build.gradle`、`build.gradle`、`gradle.properties`、`AGENTS.md` | 与 `origin/beta` 逐字节一致 |
| 合并父 | `git rev-parse MERGE_HEAD` | `056200cda0`（= `origin/beta` tip） |

### 缺陷类广度核查（同类 tint 泄漏面）

`BaseAlertDialog.builder()` 把**控件样式** `MaterialAlertDialog_WebHTV_Rounded` 当作 `themeResId` 传入，是该缺陷的形态。核查结论：

- 修复后 `MaterialAlertDialog_WebHTV_Rounded` 在全仓**仅剩 2 处引用**：`ThemeOverlay.WebHTV.Dialog` 的 `alertDialogStyle`（正确的控件样式位置）与设备测试的断言。**没有任何一处再把它当主题传入**。
- 全部 `new *Builder(ctx, R.style.X)` 调用点共 140+ 处，逐类核对：传入的都是**主题**（`Theme_*` / `ThemeOverlay_*` / `Theme_App_Lab_*`），**没有第二处把 `MaterialAlertDialog.*` / `Widget.*` / `ShapeAppearance.*` / `TextAppearance.*` 当 `themeResId` 传入**。
- 定义 `backgroundTint` 的样式共 7 处，全部为**控件样式**（`Widget.WebHTV.*`、`DiscMenuButton`、`Widget.WebHTV.SliderLabel`）或 `MaterialAlertDialog.WebHTV.Rounded` 本身；这些控件样式只被 `style=` 属性或 `alertDialogStyle` 引用，不会成为上下文级属性，因此不存在同类泄漏。
- `Theme.WebHTV.Dialog`（另一个被当主题使用的样式）**不定义 `backgroundTint`**，故也不会把 tint 注入对话框上下文。

### 主题契约语义等价性（反编译验证）

反编译 Material 1.14.0 的 `MaterialAlertDialogBuilder` 构造链，确认传入的 `themeResId` 是经 `getOverridingThemeResId()` 作为**主题**参与 `ContextThemeWrapper` 与 `alertDialogStyle` 解析的：

```text
MaterialAlertDialogBuilder(Context, int)
  → createMaterialAlertDialogThemedContext(context)        // materialAlertDialogTheme 叠加
  → getOverridingThemeResId(context, themeResId)           // themeResId != 0 时直接用 themeResId
  → AlertDialog.Builder(themedContext, overridingThemeResId)
  → MaterialDialogs.getDialogBackgroundInsets(ctx, DEF_STYLE_ATTR, DEF_STYLE_RES)
  → obtainStyledAttributes(..., MaterialAlertDialog, DEF_STYLE_ATTR, DEF_STYLE_RES)
       → backgroundInsetStart/Top/End/Bottom  ← 由 alertDialogStyle 提供
```

`ThemeOverlay.WebHTV.Dialog` 与旧的 `MaterialAlertDialog_WebHTV_Rounded` 在关键属性上的关系：

- `ThemeOverlay.WebHTV.Dialog` 的 `alertDialogStyle` **显式指向** `MaterialAlertDialog.WebHTV.Rounded`，因此 `backgroundTint`、`shapeAppearance`、`shapeAppearanceOverlay` 与四个 `backgroundInset*` 的解析结果**完全相同**，面板外观与内边距语义不变。
- 主题覆盖层额外提供调色板角色（`colorSurface`、`colorOnSurface`、`colorOnSurfaceVariant`、`colorPrimary` 等）与 `android:textCursorDrawable`，正是该对话框族本应继承的主题上下文。

因此本修复**只移除泄漏到对话框上下文的控件级 `backgroundTint`**，不改变面板圆角、颜色与 inset。设备用例同时断言了这一点：`panel backgroundTint leaked into system text actions` 不成立 **且** `alertDialogStyle` 仍解析为 `R.style.MaterialAlertDialog_WebHTV_Rounded`。

### 设备验证（`192.168.50.3:5557`，dev2 分配）

**签名与数据安全**：设备 user 0 上的 `com.fongmi.android.tv` 是第三方 release 签名（SHA-256 `dafec676…`），与本机 debug 签名（`95e4b2e7…`）不同。核查后发现 debug 构建的 `applicationId` 是 **`com.silent.android.webhtv`**（`app/build.gradle`），与用户已装的 `com.fongmi.android.tv` 是**不同应用**，因此**不存在签名冲突，无需卸载，也完全不触碰用户数据**。

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| debug 包安装 | `adb install -r -t app-mobile-arm64_v8a-debug.apk` | `Success` |
| androidTest 包安装 | `adb install -r -t app-mobile-arm64_v8a-debug-androidTest.apk` | `Success` |
| 设备回归 GREEN | `am instrument -w -e class ...DanmakuApiDialogDeviceTest com.silent.android.webhtv.test/androidx.test.runner.AndroidJUnitRunner` | **OK (3 tests)**，12.987s |
| 设备回归 RED（反证） | 把 `BaseAlertDialog.builder()` 回退为 `R.style.MaterialAlertDialog_WebHTV_Rounded` 后重装重跑 | **Tests run: 3, Failures: 1**，精确命中 `panel backgroundTint leaked into system text actions`（`DanmakuApiDialogDeviceTest.java:89`） |
| 修复恢复 | 从备份还原 `BaseAlertDialog.java` | 合并树回到 `31000aa5d684a9bd987e0469cfd5adbac30c72c1`（与 RED 前**逐字节一致**） |
| 设备回归 GREEN（复测） | 重装后重跑同一用例 | **OK (3 tests)** |

RED/GREEN 双向证据说明该设备用例**不是空转**：它真的能区分「控件样式当主题」与「主题覆盖层」两种形态。

用户数据保护：user 0 的 `com.fongmi.android.tv` 数据在测试前后**未被触碰**（不同包名）；测试期间另建的 Android 用户 `10` 与临时数据备份 `/sdcard/dev2-appdata-backup.tar.gz` 为防御性措施，未使用，收尾时已清理。

## 第 2 轮评审（对抗性复评）

| 复评项 | 检查方式 | 结论 |
| --- | --- | --- |
| 净差异是否只含本分支改动 | 4 个路径逐个与 `dev2` blob 比对 | 全部 `SAME`，无 beta 内容被改写 |
| beta 是否已用其它方式修过同一 tint 泄漏（重复/冲突） | `origin/beta` 全历史检索 `floating toolbar` / `floatingToolbar` / `tint 泄漏` / `编辑菜单` / `backgroundTint leak` | 命中数 **0**，无重复实现 |
| dev2 新增路径是否曾存在于 beta 并被删除 | `git log origin/beta -- <path>` | 两条路径历史条目均为 **0** |
| beta 增量与 dev2 改动是否有文件重叠 | `dev2..origin/beta` 路径集合 vs `9b168a1f38..dev2` 路径集合 | 交集为空，无交互冲突 |
| 浅色/深色两套调色板下的一致性 | 比对 day/night token（panel `#E7E8EF`/`#2A2F34`，text `#1A1C1E`/`#E2E2E9`） | 修复走主题覆盖层，两套色板均解析到同一面板样式，无回归 |
| 其它对话框是否被本修复波及 | `LightDialog.apply()` / `BaseBottomSheetDialog` / `ChoiceDialog` 的构造路径 | 均未使用 `MaterialAlertDialog_WebHTV_Rounded` 作主题，行为不变 |
| 是否存在残留的「控件样式当主题」调用 | 全仓 `new *Builder(ctx, R.style.X)` 140+ 处逐类核对 | 0 处 |

**第 2 轮复评结论：通过。** 未发现未处理的必修问题，因此不产生新的代码改动。

## 验证记录汇总

| 验证项 | 命令/方法 | 结果 |
| --- | --- | --- |
| 冲突 / 冲突标记 | `git diff --diff-filter=U` / 标记检索 | 0 / 0 |
| 回退内容零复活 | 124 个移除类提交 × 361 路径 + `5682f2b05` × 70 路径，blob 级比对 | **0 复活** |
| beta 增量零丢失 | 5045 个 beta 路径 vs 合并结果 | **0 缺失** |
| dev2 既有零丢失 | 4 个 dev2 路径 vs 合并结果 | **0 缺失** |
| 三方合成文件 | 内容异于 dev2 与 beta 的文件 | **0** |
| 合并父正确 | `git rev-parse MERGE_HEAD` | `056200cda0` = `origin/beta` tip |
| 双 flavor Java 编译 | `:app:compile{Mobile,Leanback}Arm64_v8aDebugJavaWithJavac` | 成功（含在 assemble 中） |
| 双 flavor androidTest 编译 | `:app:compile{Mobile,Leanback}Arm64_v8aDebugAndroidTestJavaWithJavac` | `BUILD SUCCESSFUL` |
| 手机版全量单测 | `:app:testMobileArm64_v8aDebugUnitTest` | 5216 用例 / 5 失败（= 合并前基线 5 失败，**零新增回归**） |
| TV 版全量单测 | `:app:testLeanbackArm64_v8aDebugUnitTest` | 4365 用例 / 6 失败（= 合并前基线 6 失败，**零新增回归**） |
| 基线对照 | 独立工作树 `/f/temp/baseline-dev2` @ `a10322a75c` 跑同一命令 | mobile 5199/5、leanback 4351/6，失败集合**逐条完全相同** |
| 定向契约测试 | `DialogRoundedCornerSourceTest`、`com.fongmi.android.tv.theme.*`、`TmdbEpisodeSorterTest`、`SourceEpisodeSeasonCacheTest`、`ShortDramaConfigTest` | 171 用例 / 0 失败 |
| 设备回归 | `DanmakuApiDialogDeviceTest`（192.168.50.3:5557） | **OK (3 tests)**；RED 反证 1 failed |
| UI token 门禁 | `bash scripts/check_ui_tokens.sh --strict` | `violations=1 legacy=0`（唯一违规为既有 `mobile/item_following.xml`，相对基线零新增）；`UI_TOKEN_CONTRAST pairs=38 failures=0` |
| 空白检查 | `git diff --cached --check` | 退出码 0 |
| LSP | 3 个变更 Java 文件 | 0 diagnostics（3 个为 push-only 服务器不可确认，非失败） |
| 最终 fetch | `git fetch origin beta dev2` | `origin/beta` 仍为 `056200cda0`，未前进 |

### 关于本机 5 + 6 个失败用例（**非本任务引入，不修**）

两端的失败集合与合并前 `a10322a75c` **逐条相同**（独立工作树实测），且每个失败用例读取的目标文件在合并结果与 `origin/beta` 之间**逐字节一致**（`git rev-parse` blob 哈希相同，`identical-to-beta=True`）：

- `PlayerPlaybackRegressionSourceTest.livePlaybackAlwaysAutoplaysWhileVodUsesTheConfiguredPolicy`
- `ReaderPlaybackRoutingSourceTest.readerDefinesTheMonotonicClockUsedByRestore` / `horizontalComicProgressUsesItsOwnPageState`
- `TmdbSourceOnlyInteractionTest.nativeVideoSourceOnlyHidesNetworkActionsInBothFlavors`
- `WebThemeTokenSourceTest.webHomeTokenDeclarationsStayInsideRootScope`
- `NativeEnhancedPlaybackStyleFocusTest.everyTmdbRowCardCarriesItsOwnVerticalFocusTargets`、`SearchResultDownFocusTest.scrollCallbacksDeferResultLoadingUntilAfterLayout`（仅 leanback）

根因是本机 `core.autocrlf=true`（`file:G:/Git/etc/gitconfig`）：这些断言写的是**多行字符串字面量**（含 `\n`），而检出后文件是 CRLF。程序化探针确认目标字符串在 `app/src/main/assets/reader.html`、`assets/webhome/eclipse.html`、`assets/webhome/eclipse-detail.html`、`PlaybackActivity.java`、`VideoActivity.java` 等文件中 `LF=False / CRLF=True`。CI 的 Linux 检出（LF）不触发。**不修，也不属于本任务范围。**

## 净差异最终确认

`git diff --name-status origin/beta` 仅 4 个路径，全部为 dev2 自身改动（详见上文），无 beta 内容被单方面改写，无远端已删除/回退内容被带回。

## 回滚

- 提交前：恢复锚点 `a10322a75ce25432dd8fb12883fa99dc2c88e826`；若仍处于未提交合并状态可 `git merge --abort`。
- 提交后：`git revert -m 1 <merge-commit>` 回退合并提交；该操作不影响 `beta` 分支。
