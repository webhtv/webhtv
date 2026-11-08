# LAB-CONFIG-DIALOG-20261008 实验室配置源弹窗对比度与主题跟随

## Recovery anchor

- 目标：实验室「配置源」设置弹窗（`实验室配置源`）在默认主题下必须可读，并且必须跟随应用主题（深浅色 + 用户主题色）。
- 允许路径：`app/src/main/java/com/fongmi/android/tv/lab/**`、`app/src/main/res/values/lab_styles.xml`、`app/src/main/res/values/lab_colors.xml`、`app/src/main/res/values-night/lab_colors.xml`、`app/src/test/**`、`docs/**`。
- 验收标准：默认主题（浅色系统）与深色主题下，弹窗正文/提示/输入框文字与面板对比度 ≥ 4.5:1；设置用户主题色后弹窗面板与文字随之变化（像素级 A/B 非零差异）。
- 当前状态：**已实施、已验证、已交付**。两处修复（弹窗内容与面板同源、下拉列表改用弹窗主题上下文与语义 item 布局）均已落地并通过设备像素实测；随后由 `docs/C50-beta-merge-review-dev1-20261008.md` 记录的合并轮次独立复测复现全部数据，并在该轮修正了本文档 §4.1.1 的两处不实描述（见 §4.1.1 与 §4.4）。
- 下一步唯一动作：无（本轮已随 `dev1 → beta` 的 PR [#424](https://github.com/Silent1566/webhtv/pull/424) 创建交付，只创建未合并；是否合入由用户决定）。

## 1. 现象与证据（2026-10-08，dev1 `192.168.50.3:5555`，mobile arm64 debug）

复现路径：设置 → 增强功能 → 实验室 → 齿轮（实验室配置源）。截图 `/tmp/lab3.png`（默认主题）、`/tmp/lab_theme_dialog.png`（主题色=鸢尾紫）。

| 元素 | 默认主题实测 | 结论 |
| --- | --- | --- |
| 弹窗面板（M3 对话框 shell） | `#C9DBEF`（= 主题色鸢尾紫下的 `#EBDEF3`） | 跟随**编译语义 token**（日/夜表） |
| 弹窗正文/提示/输入框文字 | `#FFFFFF`（`?attr/colorOnSurface` 由 Activity 主题 `Theme.App.Lab` 解析） | 固定白字，不随面板 |
| 弹窗标题、按钮 | 深色（来自 `ThemeOverlay.WebHTV.Dialog`） | 可读 |

根因：`LabActivity.showSettings()` 用 **Activity 的 LayoutInflater** inflate `dialog_lab_settings.xml`，其中的
`?attr/colorOnSurface` / `?attr/colorOnSurfaceVariant` / `?attr/colorPrimary` 全部由 `Theme.App.Lab`
（固定深色：`colorOnSurface=#FFFFFFFF`、`colorPrimary=@color/accent=#2196F3`）解析；
而对话框面板由 `ThemeOverlay.WebHTV.Dialog` / `Theme.App.Lab.DayNight.Dialog` 的
`colorSurfaceContainerHigh` 决定，是日/夜双表。浅色系统下形成
**白字 + `#C9DBEF` 面板 = 1.05:1**，即「默认主题下就看不清了」。

同时因为内容颜色不是语义 token 的基线取值，`ThemeBinder` 的精确匹配改写无法命中它们，
所以「主题色彩对设置页本身没作用」——面板跟着编译表变，正文永远是白字。

## 2. 决策

- `no change`：弹窗在浅色系统下不可读，不满足验收。
- 固定深色（改用模块内其它弹窗的 `Theme.App.Lab.Dialog`）：可读，但主题色仍然完全不生效，
  与用户第二条诉求（主题色对设置页无作用）冲突，且和 `Theme.WebHTV.Dialog` 对话框契约分叉。
- **采用**：让弹窗的**内容与面板同源**——用 `Theme.App.Lab.DayNight.Dialog` 作为 inflate 上下文，
  并把该主题的 `colorPrimary` 从固定 `@color/accent` 改为语义 `@color/webhtv_color_primary`。
  这样浅/深两套表都得到正文 4.5:1 以上对比度，且所有颜色精确等于冻结基线角色值，
  `WebHtvAlertDialogBuilder` → `ThemeController.bindDialog()` 能把它们改写成当前主题 token，
  主题色随之生效（与其它 `Theme_WebHTV_Dialog` 弹窗行为一致）。

## 3. 实施记录

| 文件 | 改动 |
| --- | --- |
| `app/src/main/res/values/lab_styles.xml` | `Theme.App.Lab.DayNight.Dialog`：新增 `colorSurfaceContainerHigh`（M3 对话框容器色，面板真正取值处）、`colorOnPrimary`、`colorControlNormal/Activated`、`colorOutline/Variant`；`colorPrimary` 由固定 `@color/accent`(#2196F3) 改为语义 `@color/webhtv_color_primary` |
| `app/src/main/java/com/fongmi/android/tv/lab/LabActivity.java` | `showSettings()` 用 `LayoutInflater.from(new ContextThemeWrapper(this, R.style.Theme_App_Lab_DayNight_Dialog))` inflate `dialog_lab_settings.xml`（此前用 Activity 的 `getLayoutInflater()`）；下拉适配器改用弹窗主题上下文 + `R.layout.item_lab_dropdown`（此前 `this` + `android.R.layout.simple_dropdown_item_1line`）；导入弹窗的“选择离线包文件”文字色由 `R.color.accent` 改为 `R.color.webhtv_color_primary` |
| `app/src/main/res/layout/dialog_lab_settings.xml` | 下拉控件由 `android.widget.AutoCompleteTextView` 改为 `com.google.android.material.textfield.MaterialAutoCompleteTextView`（只有它应用 `dropDownBackgroundTint` 与主题化 item 布局） |
| `app/src/main/res/layout/item_lab_dropdown.xml` | 新增：下拉列表项布局，前景绑定 `?attr/colorOnSurface`，id 用 `@android:id/text1` |
| `app/src/test/java/com/fongmi/android/tv/lab/LabSettingsDialogThemeTest.java` | 新增 8 条契约测试（源码形态 + 两张 token 表的 4.5:1 对比度门槛） |

## 4. 验证

### 4.1 设备像素实测（dev1 `192.168.50.3:5555`，mobile arm64 debug 覆盖安装，1920x1080）

面板 = 弹窗容器色，正文 = 文字像素核心色，对比度按 WCAG 相对亮度计算。

| 场景 | 面板 | hint | 输入框 label | 输入值 | placeholder | 开关标题 | 开关 hint | 按钮 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 修复前 · 默认主题 | `#C9DBEF` | **1.05:1**（白字） | **1.05:1** | **1.05:1** | **1.05:1** | **1.05:1** | **1.05:1** | 可读 |
| 修复后 · 默认主题 | `#C6D2EA` | 8.04:1 | 6.11:1 | 11.24:1 | 6.11:1 | 11.24:1 | 8.49:1 | 4.20:1 |
| 修复后 · 主题色=鸢尾紫 | `#EBDEF3` | 9.48:1 | 7.20:1 | 12.71:1 | 7.20:1 | 12.71:1 | 10.01:1 | 5.02:1 |
| 修复后 · 主题模式=深色 | `#3C4551` | 5.71:1 | 5.71:1 | 7.53:1 | 5.71:1 | 7.53:1 | 5.71:1 | 5.65:1 |

主题色生效（同一元素 A/B 像素差，`/tmp/fix_default.png` vs `/tmp/fix_purple.png`）：

| 元素 | 默认主题 | 鸢尾紫 | 结论 |
| --- | --- | --- | --- |
| 面板主体色 | `#C6D2EA` | `#EBDEF3` | 跟随 |
| 输入框描边 | `#737A88` | `#88818D` | 跟随 |
| 文件夹图标 | `#0B57D0` | `#8400F0` | 跟随（修复前恒为 `#2196F3`） |
| 取消/确定按钮文字 | `#0B57D0` | `#8400F0` | 跟随 |
| 全图差异像素 | — | 46 万+ | 主题色确实作用到该设置页 |

### 4.1.1 配置源下拉列表（独立缺陷）

首次修复后复测发现弹出列表仍然白字浅底，根因是 `ArrayAdapter` 用 **Activity 上下文**
（`Theme.App.Lab`，固定深色 → 白字）解析 `android.R.layout.simple_dropdown_item_1line`，
而弹出面板跟着日/夜表走。同时下拉控件本身用的是普通 `AutoCompleteTextView`，
不会应用 `Widget.Material3.AutoCompleteTextView.OutlinedBox` 的 `dropDownBackgroundTint`。

| 场景 | 弹出面板 | item 文字 | 对比度 |
| --- | --- | --- | --- |
| 修复前 · 默认主题 | `#F3EDF7` | 近白 | **1.19:1** |
| 修复后 · 默认主题 | `#F3EDF7` | `#1A1C1E` | **14.87:1** |
| 修复后 · 主题模式=深色 | `#212027` | `#E2E2E9` | **12.64:1** |

下拉项现在由 `item_lab_dropdown.xml` 提供（前景 `?attr/colorOnSurface`），适配器改用弹窗主题上下文，
因此 item 文字与弹出面板来自同一套表并可读。

**边界（2026-10-08 C50 轮次实测修正，此前本文档的说法「同时跟随深浅色与主题色」不成立）**：

- **跟随深浅色：成立**。浅色 item `#1A1C1E`、深色 item `#E2E2E9`，恰为两套 token 表中
  `webhtv_color_on_surface` 的取值。
- **跟随用户主题色：不成立**。`ThemeController`/`ThemeBinder` 中没有任何 PopupWindow/ListPopupWindow
  处理（`rg 'PopupWindow|ListPopupWindow|Popup' app/src/main/java/com/fongmi/android/tv/theme/*.java` 命中 0），
  `bindDialog()` 只绑定 `dialog.getWindow().getDecorView()`；弹出列表是独立窗口，其面板与 item 视图
  都不在绑定树内。弹出面板实测在两套模式下恰为 Material 3 编译基线的 `?attr/colorSurfaceContainer`
  （浅色 `#F3EDF7`、深色 `#211F26`，全区域同色不透明），即编译期取值，运行时主题色不会改写它。
- 同时修正：`simpleItemLayout`/`simpleItems` 只在 `setSimpleItems()` 路径生效，本实现走的是自定义
  `ArrayAdapter` + `item_lab_dropdown.xml`，与这两个属性无关；真正起作用的是
  `MaterialAutoCompleteTextView` 经 `materialThemeOverlay` 取得的
  `dropDownBackgroundTint`（Material 1.14.0 AAR 实测 = `?attr/colorSurfaceContainer`）与
  item 布局自身的 `?attr/colorOnSurface`。

面板侧另有 1 处低影响分歧已按 AGENTS.md §2 只记录不修：`Theme.App.Lab.DayNight.Dialog` 未映射
`colorSurfaceContainer`，故弹出面板取 M3 基线 `#F3EDF7` / `#211F26` 而非应用 token
`#ECEEF4` / `#1F2428`（两者对比度仅 1.01:1 / 1.04:1，且补齐映射也不会让弹出列表跟随主题色）。
详见 `docs/C50-beta-merge-review-dev1-20261008.md` 的「发现与处置」。

### 4.2 静态与单测

- `bash scripts/check_ui_tokens.sh --strict`：`violations=1`，唯一命中 `app/src/mobile/res/layout/item_following.xml`；用 `git stash` 单独回退本任务改动后同一命令仍为 `violations=1` 且命中同一文件 → **HEAD 既存问题，与本次改动无关**（按 AGENTS.md 只记录不修）。`UI_TOKEN_CONTRAST pairs=38 failures=0`。
- `./gradlew :app:testMobileArm64_v8aDebugUnitTest :app:testLeanbackArm64_v8aDebugUnitTest`：BUILD SUCCESSFUL。
- 新增 `LabSettingsDialogThemeTest` 8/8 通过；变异检验：把 inflate 换回 `getLayoutInflater()` 并把 `colorPrimary` 改回 `@color/accent` 后，对应 2 条测试立刻转红（`MUTATION-CAUGHT`），恢复后逐字节一致并全绿。

### 4.3 回滚

`git revert` 本任务提交即可；无数据迁移、无 native/ABI 变更。设备上恢复默认主题只需 设置 → 外观与语言 → 主题色彩 → 恢复默认 → 保存应用（本次验证后已恢复为 `theme_color=-1`、`theme_mode=-1`）。

### 4.4 C50 合并轮次独立复测（2026-10-08 23:52，dev1 `192.168.50.3:5555`）

把远端 `beta` 合入 `dev1` 后重新实测（沿用设备上 2026-10-08 23:21 安装的 mobile arm64 debug 包；
`lab` 相关源文件与提交逐字节一致，本次合并未触碰）：

| 场景 | 弹窗面板 | 弹窗正文 / hint | 下拉弹出面板 | 下拉 item | 下拉项对比度 |
| --- | --- | --- | --- | --- | --- |
| 默认主题（浅色系统） | `#C6D2EA` | `#1A1C1E` / `#44474F` | `#F3EDF7` | `#1A1C1E` | **14.87:1** |
| `cmd uimode night yes` | `#3C4551` | `#E2E2E9` / `#C4C6D0` | `#211F26` | `#E2E2E9` | **12.64:1** |

- 数值与 §4.1、§4.1.1 逐个复现（弹窗正文对面板：浅色 11.24:1 / 6.11:1，深色 7.53:1 / 5.71:1），
  说明修复在合并后依然成立。
- 功能流实测：「配置源」下拉切到「网络 URL」后 URL 输入框出现、文件夹图标隐藏、`确定/取消` 正常，
  随后点「取消」未写入 `LabConfig`。
- 设备恢复：`cmd uimode night no`（已核对 `Night mode: no`），未卸载/重装任何包。

## 5. 回滚

仅改 `lab_styles.xml` 与 `LabActivity` 的 inflate 上下文，回滚即恢复旧行为；无数据迁移、无 native 变更。
