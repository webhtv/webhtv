# C42：dev3 合并远端 beta 最新代码（PR#410/#411）并复评修复 TV 固定深色面板对比度回归

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev3`（**远端已移除/回退的提交不得顺带带回**）；复评 dev3 全部已修改代码（含已提交未推送的 `57362eb4f`…`5ff8037e5` 五个提交）；发现问题即修复并验证通过；循环评审直至通过；然后提交、推送 `dev3`、创建 `dev3 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并结果第二父为 `origin/beta` tip `9b168a1f3`；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev3 既有改动**零丢失**；④ 双 flavor JVM 全量套件零失败；⑤ UI token 门禁相对基线零新增违规；⑥ 净差异只含本分支自身改动；⑦ 实机（`192.168.50.3:5559`）复现并确认修复；⑧ 提交 + recovery tag；⑨ `dev3` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并完成（0 冲突）；2 轮评审完成——第 1 轮发现并修复 1 类真实回归（合并删除 TV 恒深色表后，4 个固定深色面板上的调色板跟随前景在日间模式不可读）；第 2 轮复评通过；全量测试与实机验证通过；待 `task_guard.sh finish`。
- **下一动作**：`task_guard.sh finish` → 推送 `dev3` → `gh pr create`（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev3` |
| 任务开始时 HEAD | `5ff8037e58ea6ae0e3ad7272e8f51b82a8d635d1`（领先 `origin/dev3` 11 个提交，未推送） |
| `origin/beta` tip | `53b54d1b62c3e405790773b5b7c70f38221987a3`（Merge PR #412 from dev2） |
| 合并基点（merge-base） | `cb82755ae8fc671f19e26a7771939c7bce0b0580`（Merge PR #409 from dev4） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`（由 task_guard `finish` 创建合并提交） |
| 合并结果 | **0 冲突、0 冲突标记**，75 路径合入 |
| 合并树哈希 | `3005b6f2ae280571fbf57b8d65112b4234c1281d`（与 `git merge-tree --write-tree` 预测值逐字节一致） |
| 初始脏路径 | 无（`git status` 干净） |
| 回滚锚点 | `5ff8037e58ea6ae0e3ad7272e8f51b82a8d635d1` |
| 任务守卫 | `C42-beta-merge-review-dev3-20261007`（standard，scope `app/src` + `docs`） |

> **合并期间 beta 再次前进**：本任务首轮合并时 `origin/beta` tip 为 `9b168a1f3`（PR #410/#411）；复评与实机验证期间 beta 又合入 PR #412（`3374def64` / `f77c4a023` / `53b54d1b6`，leanback 播放加载圈守卫泄漏修复）。已 `git merge --abort` 首轮合并（保留修复到 stash），改为对**新 tip `53b54d1b6`** 重新合并（同样 0 冲突），再恢复修复并重跑全部验证。最终合并树 `3005b6f2a` 同时包含 PR #410/#411 与 PR #412 的全部内容。

### beta 增量 ledger（10 个提交，全部纳入）

`git log --oneline dev3..origin/beta`：

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `53b54d1b62c3e405790773b5b7c70f38221987a3` | Merge PR #412 from dev2 | 纳入 |
| `3374def64` | merge：合并 origin/beta（PR#411）并复评修复播放加载圈守卫泄漏 | 纳入 |
| `f77c4a023` | `fix(tv): keep playback loading visible until new player starts` | 纳入 |
| `9b168a1f3805614547308a638a13ddae45a8f564` | Merge PR #411 from dev1 | 纳入 |
| `92f9b020f` | docs(c41)：订正净差异路径数 | 纳入 |
| `8d03e86d8` | merge：合并 origin/beta（PR#410）并复评 | 纳入 |
| `5c95aa19c` | fix(detail)：修复线路/选集 chip 无底色并恢复光影剧幕浅色白色中间层 | 纳入 |
| `414babc1b5c9acae064a4cb6a35c689379155512` | Merge PR #410 from dev2 | 纳入 |
| `990f8039d` | merge：合并 origin/beta（PR#407–#409）并复评修复 TV 浅色迁移的日间白底白字回归 | 纳入 |
| `35a97894a294057db8acf6f6eaf2c72b13bd9da2` | `feat(theme): 真正打通 TV 浅色模式` | 纳入 |

**无一条与 dev3 既有实现重复或被取代**，故全部纳入。beta 增量内容为：删除 leanback 恒深色 token 遮蔽表、`Theme.WebHTV.TV` 去掉硬编码 `isLightTheme=false`、58 个 leanback 布局共 405 处前景从 `?attr/colorOnSurface*` 迁移到调色板无关角色、`ThemeDialog` 预览模式按 `ThemeController.isNight` 解析、`ThemeController` 文档订正、新增 `LeanbackForegroundContrastTest`、C40/C41/CINEMA-LIGHT-CHIP-FILL/TV-LIGHT-THEME 四份文档，以及 chip 底色与浅色剧幕中间层修复。

### 三方无损校验

```text
beta 增量零丢失：cb82755ae..53b54d1b6 的 75 个路径 → 合并树与 53b54d1b6 逐文件字节一致
                （其中 PR #412 的 4 个路径 VideoActivity.java / ReaderPlaybackRoutingSourceTest.java /
                  VideoActivityLayoutTest.java / C42-beta-merge-review-dev2-20261007.md 逐一 SAME）
dev3 既有零丢失：5ff8037e5 相对 cb82755ae 的 7 个路径 → 合并树与 5ff8037e5 逐文件字节一致
合并结果净差异：git diff --name-status origin/beta <合并树>
  = 7 个路径（本任务文档创建前），全部为 dev3 自身改动
```

## 用户核心关注点：远端已移除（回退）内容零复活

程序化逐路径比对（不依赖人工目测）：

```text
beta 历史单亲 revert/撤销/剔除 提交 : 26
其触及路径总数                      : 157
与 origin/beta 存在差异的路径        : 1（mobile/AppearanceDialog.java，内容 == dev3 自身改动）
被回退删除文件在合并树中复活数       : 0
```

补充核对：

1. **`be1b02e06`（revert: remove dynamic theme color system）** 删除的 16 个路径中有 16 个在当前树中存在，但它们**在 `origin/beta` 中同样存在且与合并树逐字节一致**（逐一 `SAME`）——即 beta 自己在后续提交中有意重新引入了这些文件，不是 dev3 单方面带回。真正属于「被剔除且未复活」的关键标志：`ThemeCatalog`/`ThemeCatalogStore`/`assets/themes/*` 在合并树中计数为 **0**。
2. **`fd29d76ee`（撤销把电视版暗色配色搬到手机版）** 触及的 21 个路径与 `origin/beta` 逐字节一致；`app/src/mobile/res/values/webhtv_tokens.xml` 的删除状态保持。
3. **PR #353 剔除内容零复活**：`webhtv_tokens.xml` 相关旧目录在合并树中不存在。
4. **`git diff --check`** 退出码 0，无空白错误。

## 评审循环记录

### 第 1 轮：合并删除 TV 恒深色表后，固定深色面板上的调色板跟随前景在日间模式不可读（真实回归，已修复）

**发现方式**：合并后写程序化扫描器，按 flavor 建模（leanback = `main/res` + `leanback/res`，合并前 leanback 的 `values/webhtv_tokens.xml` 覆盖**两种模式**），逐布局解析标签树，对每个前景取最近的绘制背景的祖先，解析其填充并计算日间对比度，再与合并前（`cb82755ae`）逐元素比较，只报「合并前 ≥4.5:1 或不存在、合并后 <4.5:1」的组合。

```text
leanback flavor 新增不可读组合 : 4 个面板 / 5 处前景
mobile  flavor 新增不可读组合 : 0
```

| 布局 | 面板（两表恒定深色） | 前景（跟随调色板） | 合并前 | 合并后 |
| --- | --- | --- | ---: | ---: |
| `leanback/dialog_exit_confirm.xml` | `selector_exit_confirm_primary` `#0B57D0`/`#174EA6` | `?attr/colorOnSurface` | 4.95:1 | **2.18:1** |
| `main/dialog_display.xml` | `shape_display_dialog_panel`（`webhtv_color_player_scrim` 半透明黑） | `?attr/colorOnSurface` | 15.53:1 | **1.16:1** |
| `main/dialog_disc_menu.xml` | `shape_disc_menu_panel` `#F21C2028` | `?attr/colorOnSurface` | 12.81:1 | **1.04:1** |
| `main/dialog_audio_comment.xml` | `shape_audio_playlist_panel` `#E6191B22` | `?attr/colorOnSurface` ×2 / `colorOnSurfaceVariant` ×3 | 13.48/10.21:1 | **1.02/1.87:1** |

**根因**：这 4 个布局的**面板填充是两套色板恒定的深色**（设计意图：深色玻璃/播放器面板），因此其前景必须是调色板无关的常量浅色。但它们的前景写的是 `?attr/colorOnSurface*`，该角色**跟随调色板**：TV 此前编译恒深色表（`?attr/colorOnSurface` = `#E2E2E9`）所以正确；`35a97894a2` 删除该表后，TV 日间模式下同一属性解析为 `#1A1C1E`，变成深字压深底。

**这是合并引入的回归，不是既有缺陷**：4 个布局文件与 `origin/beta` **逐字节一致**（合并未改动它们），差异纯粹来自 beta 删除 leanback 遮蔽表后 token 解析路径的改变。mobile flavor 无此问题（`0` 处回归），因为 mobile 的表一直是 day/night 双表、面板与前景自洽。

**修复**（最小改动，只改前景角色，不动面板与布局几何）：

| 文件 | 改动 |
| --- | --- |
| `app/src/leanback/res/layout/dialog_exit_confirm.xml` | `positive` 的 `?attr/colorOnSurface` → `?attr/webhtvColorOnWallpaper`（1 处） |
| `app/src/main/res/layout/dialog_display.xml` | 标题 `?attr/colorOnSurface` → `?attr/webhtvColorOnWallpaper`（1 处） |
| `app/src/main/res/layout/dialog_disc_menu.xml` | 2 处标题 → `?attr/webhtvColorOnWallpaper` |
| `app/src/main/res/layout/dialog_audio_comment.xml` | 标题/标签/加载指示器 → `?attr/webhtvColorOnWallpaper`；`meta`/`empty` → `@color/webhtv_color_player_control_muted`（共 8 处） |

`?attr/webhtvColorOnWallpaper`（`#FFFFFF`）与 `@color/webhtv_color_player_control_muted`（`#CCFFFFFF`）在**两套色板中取值相同**，正是本仓 `ThemeBaseWiringTest` 的 `CONSTANT_LIGHT_FOREGROUNDS` 已固化的"深色面板才用调色板无关前景"口径，因此修复与既有设计契约同源，不是新造规则。

**修复后对比度（按合成后的实际面板色计算）**：

| 位置 | 修复前 | 修复后 |
| --- | ---: | ---: |
| 退出确认框 主按钮 | 2.18:1 | **6.39 / 7.85:1** |
| 屏显设置弹窗 标题 | 1.16:1 | **19.80:1** |
| 光盘菜单弹窗 标题 | 1.04:1 | **16.51:1** |
| 音频评论弹窗 标题/标签 | 1.02:1 | **17.38:1** |
| 音频评论弹窗 meta/empty | 1.87:1 | **17.38:1** |

**同类第 5 处一并修复**：`adapter_tmdb_recommendation_landscape.xml` 的 `doubanRating` 用的是 `@color/webhtv_color_success`（日间 `#146C2E` 深绿 / 夜间 `#8EDB9F` 浅绿），压在固定半透明黑的评分徽标 `shape_episode_card_badge`（`#B3000000`）上——合并前 TV 恒深色表取浅绿（12.34:1），合并后日间取深绿（**3.10:1**）。修复：新增常量角色 `@color/tmdb_douban_rating_green`（`#78E08F`，与 `TmdbRailAdapter` 既有 Java 常量 `0xFF78E08F` 同值）并改用它，修复后 5.26–12.42:1。

### 第 2 轮：复评 + 反证门禁有效性

1. **逐项核实扫描器候选，排除假阳性**：
   - **Lab 模块**（`activity_lab_output`、`adapter_lab_package`、`adapter_lab_command_compact`、`dialog_lab_command_sheet`）：`Theme.App.Lab` / `Theme.App.Lab.Dialog` 把 `colorOnSurface` 固定为 `#FFFFFFFF`，其 `?attr/colorOnSurface` **不跟随应用调色板**，且该模块本身按固定深色设计（`android:colorBackground=#111318`）。**假阳性，非本缺陷类**。
   - **TMDB 卡片/标题**（`activity_tmdb_detail`、`activity_tmdb_person`、`view_tmdb_header`、`adapter_tmdb_*`、`dialog_tmdb_person`、`item_tmdb_person_work` 等 62 处）：背景由 `TmdbCardFocusHelper.apply` 的 `card.setCardBackgroundColor(...)` 在运行时覆盖、前景由 `tintTmdbSectionTitles()` / 各适配器的 `setTextColor` 覆盖，XML 值不生效。**假阳性**。
   - **`adapter_tmdb_rail_item` / `adapter_tmdb_rail_landscape` 的 `doubanRating`**：`TmdbRailAdapter:131` 无条件 `setTextColor(0xFF78E08F)` 覆盖，XML 为死代码。**假阳性**。
   - **`activity_video.xml` 音频横幅**（`white_70`/`white_80` 压在 `shape_audio_player_background`）：该底是**彩色渐变 + 深色 scrim 的 layer-list**，合并前用 `?attr/colorOnSurface_70` 时中心色标处同样只有 1.67:1（合并后 2.16:1）。**属既有缺陷，且修复方向正确（白字压彩底），非本次引入**。
2. **门禁反证**：新增 `TvFixedDarkSurfaceContrastTest`（4 用例）后，把 `dialog_display.xml` 临时改回 `?attr/colorOnSurface` 重跑 → `4 tests completed, 2 failed`，失败信息精确指出 `dialog_display.xml: ?attr/colorOnSurface sits on the fixed dark surface @drawable/shape_display_dialog_panel`；恢复修复后 `BUILD SUCCESSFUL`。另含一个**合成探针**用例，把修复前的实测标记喂给同一解析器，要求它必须被标记为低于阈值——因此该门禁在"当前无违规"时**不是空转**。
3. **门禁归属说明**：`LeanbackForegroundContrastTest`（C40/dev2 拥有）只覆盖"调色板无关浅色前景压在日间浅色表面"；本缺陷是其**镜像类**（调色板跟随深色前景压在恒定深色表面），beta 侧无对应检查，故由本任务新增独立测试类，不改动 C40 的文件。

## 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| TV 全量 JVM | `:app:testLeanbackArm64_v8aDebugUnitTest` | **4362 用例 / 0 失败 / 0 错误 / 2 skipped** |
| 手机全量 JVM | `:app:testMobileArm64_v8aDebugUnitTest` | **5211 用例 / 0 失败 / 0 错误 / 2 skipped** |
| 新增回归门禁 | `TvFixedDarkSurfaceContrastTest` | 4 用例通过；反证可失败 |
| UI token 门禁 | `scripts/check_ui_tokens.sh --strict` | `violations=1 legacy=0 hex_colors=0`（唯一违规为既有 `mobile/item_following.xml`，相对基线**零新增**） |
| 空白门禁 | `git diff --check` | 退出码 0 |
| 冲突标记 | 全量扫描 | 0 处 |

### 设备级验证（`192.168.50.3:5559`，API 28，1920x1080，arm64，leanback flavor）

设备状态：系统 `night=no`、`theme_mode=-1`（跟随系统），即**日间模式**——正是本回归的触发条件。

**修复前复现**（合并结果原样打包）：

- 构建安装：`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5559` → `BUILD SUCCESSFUL`，`adb install -r` **覆盖安装**（未卸载）。
- 路径：首页 → `KEYCODE_BACK` 触发退出确认框，截图像素采样主按钮区域：

```text
主按钮背景 (11,87,208) = #0B57D0，按钮文字 (26,28,30) = #1A1C1E  → 2.68:1（不可读）
```

即设备实测确认了扫描器预测的缺陷（浅色面板 + 蓝底深字）。

**修复后复验**：重新打包覆盖安装后走同一路径，主按钮文字为 `#FFFFFF`，对 `#0B57D0` / `#174EA6` 实测 **6.39 / 7.85:1**，达到 WCAG AA 正文阈值。

**最终合并（含 PR #412）重新验证**：对 `53b54d1b6` 重新合并、恢复修复后重新打包覆盖安装，并校验设备实跑产物与本地构建产物**字节一致**：

```text
设备 base.apk md5 = 1e4dcde4ac3b3deb3513843960f5ecfb
本地 APK    md5 = 1e4dcde4ac3b3deb3513843960f5ecfb
```

同一路径（首页 → `KEYCODE_BACK`）截图采样主按钮：

```text
主按钮背景 (11,87,208) = #0B57D0
按钮文字 (255,255,255) = #FFFFFF → 6.39:1（修复前 (26,28,30) = #1A1C1E → 2.68:1）
```

设备实测确认修复在**最终合并产物**上生效。设备状态未修改（未卸载、未改持久化偏好）。

## 相邻但未修复（非本次引入，仅报告）

1. **`dialog_exit_confirm.xml` 的面板是固定浅色 `#FBFCFF`**，其标题/消息/取消按钮用调色板跟随的 `?attr/colorOnSurface*`：
   - 日间 `#1A1C1E` / `#44474F` 压 `#FBFCFF` = 16.66 / 9.06:1（**合并把它修好了**）；
   - 夜间 `#E2E2E9` / `#C4C6D0` 压 `#FBFCFF` = **1.26 / 1.66:1**（合并前 TV 恒深色表时**同样是 1.26:1**，即**既有缺陷**，`origin/beta` 上一致）。
   - 同文件 `!` 徽标用 `?attr/colorPrimary` 压固定浅色 `#E8F0FE`：日间 5.57:1（修复），夜间 **1.50:1**（既有）。
   - 正确修法是给该面板加 `drawable-night` 变体或改为调色板表面角色，会改变 TV 退出弹窗的既有外观（白卡 → 主题卡），属独立任务；C40 已对同类固定浅色卡片采取同一处置（记录不修）。
2. **`activity_video.xml` 音频横幅**（`@color/white_70`/`white_80` 压在彩色渐变 `shape_audio_player_background` 上）：合并前用 `?attr/colorOnSurface_70` 时中心色标处 **1.67:1**，合并后 **2.16:1**——数值略升且方向正确（浅字压彩底），非本次引入。
3. **Lab 模块**（`activity_lab_output`、`adapter_lab_package`、`adapter_lab_command_compact`、`dialog_lab_command_sheet`）：其 `Theme.App.Lab` 把 `colorOnSurface` 固定为 `#FFFFFFFF`，且模块整体按固定深色设计（`android:colorBackground=#111318`）。静态扫描会命中，但不属本缺陷类；如需统一到应用 token 应另建任务。

## 回滚

revert 合并提交即回到 `5ff8037e58ea6ae0e3ad7272e8f51b82a8d635d1`；修复本身是 4 个布局的前景色 + 1 个常量角色，可独立 revert。
