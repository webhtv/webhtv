# C40：dev2 合并远端 beta 最新代码（PR#407–#409）并复评已修改代码

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev2`（**远端已移除/回退的提交不得顺带带回**）；复评 dev2 全部已修改代码（含已提交未推送的 `35a97894a2`）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev2`、创建 `dev2 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并结果第二父为 `origin/beta` tip `cb82755ae8`；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev2 既有改动**零丢失**（除本文档记录的有意修正）；④ 双 flavor Java 与 androidTest 编译通过；⑤ 全量 JVM 套件相对合并前基线**零新增回归**；⑥ UI token 门禁相对基线**零新增违规**；⑦ 净差异只含本分支自身改动；⑧ 提交 + recovery tag；⑨ `dev2` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并完成（0 冲突）；3 轮评审完成——第 1 轮发现并修复 1 类真实日间对比度回归，第 2/3 轮通过；全部验证通过；待 `task_guard.sh finish`。
- **下一动作**：`task_guard.sh finish` → 推送 `dev2` → `gh pr create`（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始时 HEAD | `35a97894a294057db8acf6f6eaf2c72b13bd9da2`（`feat(theme): 真正打通 TV 浅色模式`，领先 `origin/dev2` 1 个提交） |
| `origin/beta` tip | `cb82755ae8fc671f19e26a7771939c7bce0b0580`（Merge PR #409 from dev4） |
| 合并基点 | `2162548799f2a494ad5728ec61ff26cfe0885719`（Merge PR #407 from dev2） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`，由 task_guard `finish` 创建合并提交 |
| 合并结果 | 13 路径自动合入（含 2 个双方同时修改的 `ThemeDialog.java`），**0 冲突、0 冲突标记** |
| 回滚锚点 | `35a97894a294057db8acf6f6eaf2c72b13bd9da2` |
| 任务守卫 | `C40-beta-merge-review-dev2`（standard，scope `app` + `docs`） |

### beta 增量 ledger（9 个提交，全部纳入）

`git log --oneline dev2..origin/beta`：

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `cb82755ae8fc671f19e26a7771939c7bce0b0580` | Merge PR #409 from dev4 | 纳入 |
| `3e6b8dc390` | merge：合并 origin/beta（PR#408）并解决缓存确认框等价修复冲突 | 纳入 |
| `ae09751eed` | `docs(c39)`：订正净差异路径数 | 纳入 |
| `b19ccde879` | merge：合并 origin/beta（PR#407 / 缓存管理 P0–P4）并复评修复缓存确认框主题化门禁回归 | 纳入 |
| `c94e5548be` | Merge PR #408 from dev3 | 纳入 |
| `bbaf19b972` | merge：合并 origin/beta（PR#407 缓存管理上游同步）并复评修复主题契约与安全区 insets 缺陷 | 纳入 |
| `3e4e49331a` | `fix(theme): fit editor dialog inside system bar safe area` | 纳入 |
| `03c239e32e` | Merge PR #407 from dev2（= dev2 已推送部分，无净增量） | 纳入 |
| `a455e5a6cc` | `fix(following)`：详情页追更按钮统一为就地取消并立即生效 | 纳入 |

**无一条与 dev2 既有实现重复或被取代**，故全部纳入。beta 增量内容为：主题编辑器安全区适配（`ThemeDialogLayout` + 双 flavor `ThemeDialog` 的 inset 监听与三级回退）、缓存确认框改用 `WebHtvAlertDialogBuilder`、详情页追更就地取消、`ThemeDialogLayoutTest` 6 个新用例、`TmdbDetailFollowingCancelDeviceTest`、C39 双文档。

## 用户核心关注点：远端已移除（回退）内容零复活

**程序化校验方法**（按行比对，不依赖人工目测）：对 beta 历史上每个**单亲 revert 提交** `R` 取父 `R^`，得到 `removed(R,f) = lines(R^:f) − lines(R:f)`；复活定义为「该行出现在合并结果中，且合并前 dev2 中不存在」。

```text
revert commits checked : 25
file-versions checked  : 171
removed lines scanned  : 1334
resurrected lines      : 0
resurrected files      : 0
```

**PR 方向反向校验**（关键：PR 的 base 是 beta，若合并结果里有 beta 已删除的行，PR 会把它们带回 beta）：

```text
reverts=25 file-versions=171
PR would re-add removed lines : 0
PR would re-add removed files : 0
```

**beta 增量零丢失**（`2162548799..origin/beta`，13 路径）：

```text
added=3 modified=10 deleted=0
missing added files      : 0
missing added/mod lines  : 0
```

**dev2 侧零丢失**：dev2 增量 66 路径中 65 路径逐行完整保留；唯一 8 行的差异是**本轮有意修正**（见下），已逐条列出、可核对。

**合并结果净差异**：`git diff --name-status origin/beta` 全部为 dev2 自身改动，**0 个 beta 内容被单方面改写**。

## 评审循环记录

### 第 1 轮：发现 1 类真实回归（合并引入的 TV 浅色迁移）

**问题**：dev2 未推送提交 `35a97894a2` 为「真正打通 TV 浅色模式」把 405 处 leanback 前景从 `?attr/colorOnSurface*` 迁移到调色板无关角色，迁移是**整文件批量替换**（`?attr/colorOnSurface` → `?attr/webhtvColorOnWallpaper`、`?attr/colorOnSurfaceVariant` → `@color/webhtv_color_player_control_muted`），**没有区分该前景压在什么表面上**。

被迁移的 58 个布局里，有 4 个的**行本身坐在浅色卡片上**：

| 布局 | 行底填充 | 日间取值 | 迁移后前景 | 日间对比度 |
| --- | --- | --- | --- | --- |
| `adapter_device.xml` | `shape_accent` → `selector_button` | `?attr/colorPrimary`（日间浅） | `?attr/webhtvColorOnWallpaper` | 失败 |
| `adapter_player_osd.xml` | `selector_git_cloud_card` | `#F8F9FA` / 焦点 `#E8F0FE` | `?attr/webhtvColorOnWallpaper` | **1.05:1** |
| `adapter_recommendation_feedback.xml` | `selector_light_dialog_item` | `dialog_outlined_button_bg`（日间浅） | `?attr/webhtvColorOnWallpaper` + `player_control_muted` | **1.29:1** |
| `adapter_tmdb_item.xml` | `selector_tmdb_search_item` | `#ECEEF4` / 焦点 `#D3E3FD` | `?attr/webhtvColorOnWallpaper` + `player_control_muted` | **1.16:1** |

日间白字压浅底 = **白底白字**。这三个数字（1.05 / 1.16 / 1.29）都低于非文本 UI 的 3:1 下限，远低于正文 4.5:1。

**根因**：这 4 个布局的前景原本是 `?attr/colorOnSurface`，**在合并前的任何主题模式下都正确**（TV 当时恒深色表 → 浅字压浅卡是唯一缺陷；但迁移前 TV 编译的是深色表，`?attr/colorOnSurface` = `#E2E2E9`，压在 `#F8F9FA` 上同样是 1.22:1）。**也就是说这 4 处属于「既有夜间缺陷」，迁移把它们的失败模式从夜间搬到了日间，并未消除。**

**修复**：把这 4 个布局的前景**回退为 `?attr/colorOnSurface` / `?attr/colorOnSurfaceVariant`**——即恢复它们与 mobile 同名文件一致的语义：卡片随调色板变浅/变深，文字跟随卡片。

| 布局 | 修正前 | 修正后 | mobile 同名文件 |
| --- | --- | --- | --- |
| `adapter_device.xml` | `webhtvColorOnWallpaper` ×2 | `?attr/colorOnSurface` + `?attr/colorOnSurfaceVariant` | `?attr/colorOnSurface` ×2 |
| `adapter_player_osd.xml` | `webhtvColorOnWallpaper` | `?attr/colorOnSurface` | `?attr/colorOnSurface` |
| `adapter_recommendation_feedback.xml` | `webhtvColorOnWallpaper` + `player_control_muted` | `?attr/colorOnSurface` + `?attr/colorOnSurfaceVariant` | `?android:attr/textColorPrimary/Secondary` |
| `adapter_tmdb_item.xml` | `webhtvColorOnWallpaper` + `player_control_muted` ×2 | `?attr/colorOnSurface` + `?attr/colorOnSurfaceVariant` ×2 | （无 mobile 同名文件） |

日间对比度由 1.05–1.29:1 变为 16.21:1（`#1A1C1E` on `#F8F9FA`）。

**这 8 行的「dev2 丢失」是有意修正**，是本轮唯一的 dev2 侧内容差异，已在上面的零丢失校验中逐条列出。

### 第 2 轮：新增结构性契约测试 + 复评

新增 `app/src/test/java/com/fongmi/android/tv/theme/LeanbackForegroundContrastTest.java`（3 个用例），把该口径固化为**结构性检查而非文件白名单**：

1. `constantLightForegroundsStayOnDarkSurfacesInDayMode`——遍历全部 129 个 leanback 布局，解析标签树，对每个调色板无关的浅色前景找到最近的绘制背景的祖先（或自身），解析该背景的**日间**取值；若是调色板角色（日间即浅色）或常量浅色填充，则按 3:1 下限判定失败。描边、padding、ripple mask、ripple 高亮色在解析前剔除，只有真实填充参与判定。
2. `lightCardRowsKeepThePaletteFollowingRole`——钉住这 4 个布局必须保留调色板角色，防止未来「继续迁移剩余 TV 前景」的清扫把浅色常量写回去。
3. `theLightCardsAreStillLightInDayMode`——钉住这三张卡片在日间确实是浅色填充，且**没有 `drawable-night` 变体**（这正是迫使它们的前景必须跟随调色板的事实）。

**测试有效性已反证**：把 4 个布局回退到修复前（`35a97894a2`）状态重跑，`tests=3 failures=2`，失败信息精确复现上述 6 处与实测数字：

```text
adapter_player_osd.xml: MaterialTextView textColor=?attr/webhtvColorOnWallpaper
  sits on @drawable/selector_git_cloud_card with 1.05:1 contrast in day mode
adapter_tmdb_item.xml: ... sits on @drawable/selector_tmdb_search_item with 1.16:1 contrast in day mode
adapter_recommendation_feedback.xml: ... with 1.29:1 contrast in day mode
adapter_device.xml: ... sits on the palette-following background @drawable/shape_accent,
  whose day value is a light surface
```

恢复修复后 `tests=3 failures=0`。**该测试不是空转**。

**全量回归对照**（合并前树独立工作树 `/tmp/c40/pre` @ `35a97894a2`，同一命令）：

| flavor | 合并前失败集合 | 合并后 | 新增回归 |
| --- | --- | --- | --- |
| mobile | 10 个用例失败 | 5194 用例 / **6 失败** | **0** |
| leanback | 10 个用例失败 | 4349 用例 / **7 失败** | **0** |

失败集合均为合并前基线的**严格子集**（`comm -13` 双向为空），合并还修好了 `FollowingUiSourceTest` ×2、`ThemeBinderContractTest` ×1、`MultiThreadProxyRouteTest` ×1。

剩余失败全部是本机 `core.autocrlf=true` 行尾环境下的多行文本断言脆弱（CI ubuntu LF 下不触发），且失败类读取的目标文件与 `origin/beta` 逐字节一致（程序化核验：`styles.xml` ×3、`BaseAlertDialog.java` 全部 `identical-to-beta=True`）。

### 第 3 轮：复评通过

- 逐文件复核 beta 带入的 3 类改动：**主题编辑器安全区**（`ThemeDialogLayout.safeArea/Insets` + 双 flavor `ThemeDialog` 的 `setOnApplyWindowInsetsListener`、`hostWindowInsets()`、`Insets.ofContent()` 三级回退，`isEmpty()` 把 API 28 的 `[0,0,0,0]` 视为 unknown）、**缓存确认框主题化**（`WebHtvAlertDialogBuilder`）、**详情页追更就地取消**（`isFollowed()` 排除墓碑 + 移除 `isInlineFollowingPlaybackSurface` 分支）。三者在两个 flavor 对称实现，且有 `ThemeDialogLayoutTest`（6 新用例）与 `TmdbDetailFollowingCancelDeviceTest` 锁定。
- 复核 dev2 未推送提交：`ThemeController.resolvedDark()` 新增的资源探测与 `compiledDarkPalette()`、`Theme.WebHTV.TV` 去掉 `isLightTheme=false`、删除 leanback 遮蔽表（**零独有、零缺失颜色名，纯遮蔽**）、`ThemeDialog` 预览模式改为 `ThemeController.isNight`。以上与 `webhtv_tokens.xml` 的 day/night 双表一致，`values-night` 与删除的 leanback 表在 CRLF 归一化后**逐字节相同**。
- 复核 58 个 leanback 布局的 405 处迁移：逐处判定「该前景压在什么表面上」，除本轮修复的 4 个布局外，其余 54 个（壁纸页、深色玻璃弹窗、`shape_vod_*` 徽标、视频面）的迁移**方向正确**，`?attr/colorOnSurface` 残留 19 个文件的逐处复核也全部落在调色板表面或浅色卡片上，无遗漏。
- `git diff --check` 退出码 0；`git status` 中全部改动路径均在 `app/`、`docs/` 内。
- **设备实测**（`127.0.0.1:5557`，API 28，leanback arm64，覆盖安装未卸载）：日间与夜间各跑一遍设置页、AI 服务页、屏显设置弹窗；修复后卡片行文字实测 `darkest=(26,28,30) / lightest=(248,249,250)`（深字压浅卡），对比度 16.2:1；缓存管理弹窗、DoH 弹窗、AI 服务页、屏显设置弹窗渲染与焦点环均正常，无白底白字。

**结论：第 3 轮通过，无新问题。**

## 已知遗留（**非本任务引入，不修**）

`adapter_device` / `adapter_player_osd` / `adapter_recommendation_feedback` / `adapter_tmdb_item` 这 4 个布局的卡片是**固定浅色**（无 `drawable-night` 变体），因此它们的 `?attr/colorOnSurface` 在**夜间模式**下仍是浅字压浅卡：

```text
NIGHT 分辨率 text on its card: darkest=(226,226,233) lightest=(248,249,250) contrast=1.22:1
NIGHT 网速  text on its card: darkest=(236,236,241) lightest=(248,249,250) contrast=1.12:1
```

该缺陷**在 `origin/beta` 上同样存在**（`selector_git_cloud_card.xml` 等三张卡片的填充自 `5c05ce4e26 Sync WebHTV 5.4.9` 起未变，`beta` 与合并结果逐字节一致），mobile 侧亦受影响，属独立的既有问题。正确修法是给卡片加 `drawable-night` 变体，会同时改变 mobile 外观，超出本任务范围。`LeanbackForegroundContrastTest` 已用注释记录该事实并**刻意不把它断言为通过**，避免被固化成「预期行为」。

## 验证记录汇总

| 验证项 | 命令/方法 | 结果 |
| --- | --- | --- |
| 冲突 | `git diff --name-only --diff-filter=U` / 冲突标记检索 | 0 / 0 |
| 回退内容零复活 | 25 revert × 171 文件版本 × 1334 行，行级程序化比对 | **0 复活行 / 0 复活文件** |
| PR 方向零回带 | beta 相对 merge-base 删除的行是否被 PR 重新加入 | **0 行 / 0 文件** |
| beta 增量零丢失 | 13 路径全部新增/修改行逐行存在性 | 0 缺失 |
| dev2 零丢失 | 66 路径逐行比对 | 65 路径完整；8 行为有意修正 |
| 双 flavor Java 编译 | `:app:compile{Mobile,Leanback}Arm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| 双 flavor androidTest 编译 | `:app:compile{Mobile,Leanback}Arm64_v8aDebugAndroidTestJavaWithJavac` | BUILD SUCCESSFUL |
| 全量 JVM 套件 | 合并前基线独立工作树对照（失败集合 `comm -13` 双向为空） | mobile 10→6、leanback 10→7，**零新增回归** |
| 新增契约测试反证 | 回退到修复前重跑 | `tests=3 failures=2`，命中 6 处并复现实测数字 |
| UI token 门禁 | `scripts/check_ui_tokens.sh --strict` | `violations=1`（既有 `item_following.xml`），相对基线零新增 |
| 空白检查 | `git diff --check` | 退出码 0 |
| 设备实测 | `127.0.0.1:5557` 日间/夜间各一遍 | 通过 |

## 最佳实践依据（本轮实际读取）

| 证据类 | 来源 | 级别与支持结论 | WebHTV 决策/限制 |
| --- | --- | --- | --- |
| 官方规范 | [WCAG 2.2 1.4.3 Contrast (Minimum)](https://www.w3.org/TR/WCAG22/#contrast-minimum) | A；正文 4.5:1、大字号 3:1；[1.4.11 Non-text Contrast](https://www.w3.org/TR/WCAG22/#non-text-contrast) 要求非文本 UI 3:1 | 直接决定本轮修复阈值：1.05/1.16/1.29:1 全部不合格，16.21:1 合格 |
| 官方指南 | [Android 深色主题](https://developer.android.com/develop/ui/views/theming/darktheme) | A；「Avoid using hardcoded colors… Use theme attributes or night-qualified resources instead」 | 支持把「前景角色」与「该前景实际压在什么表面」绑定，而不是全局统一替换 |
| 官方库文档 | [Material 3 Dark theme](https://github.com/material-components/material-components-android/blob/master/docs/theming/Dark.md) | A；on-* 角色必须与其配对 surface 成对使用，`isLightTheme` 决定角色派生方向 | 解释为什么 `colorOnSurface` 必须与 `colorSurface*` 同源；跨表混用即失效 |
| 官方 API 文档 | [AppCompatDelegate MODE_NIGHT_*](https://developer.android.com/reference/androidx/appcompat/app/AppCompatDelegate) | A；资源限定符是唯一权威 | 佐证「无 `drawable-night` 变体的固定浅卡」无法靠 mode 修复，只能靠跟随调色板的前景或补夜间变体 |
| 本仓既有契约 | `ThemeBaseWiringTest` 的 `DARK_GLASS_SHEETS` + `CONSTANT_LIGHT_FOREGROUNDS` + `WALLPAPER_PAGES` | A；本仓已验证的「深色面板才用调色板无关前景」口径 | 本轮正是它的 leanback 对偶检查；mobile 侧该口径已生效 |

不适用的证据类：无新增依赖/算法/原生库/性能敏感路径，故论文、benchmark、上游 revert 不适用。未取得且会改变决策的外部证据：无。

## 净差异（最终）

`git diff --name-status origin/beta` 全部为 dev2 自身改动，无 beta 内容被改写。相对合并前 `35a97894a2`，本任务新增的改动为：

```text
A  app/src/test/java/com/fongmi/android/tv/theme/LeanbackForegroundContrastTest.java
M  app/src/leanback/res/layout/adapter_device.xml
M  app/src/leanback/res/layout/adapter_player_osd.xml
M  app/src/leanback/res/layout/adapter_recommendation_feedback.xml
M  app/src/leanback/res/layout/adapter_tmdb_item.xml
A  docs/C40-beta-merge-review-dev2-20261006.md
```

其余路径均为合并 `origin/beta` 带入的 beta 内容。
