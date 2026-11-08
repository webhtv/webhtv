# TV-THEME-MODE-DARK-20261007：显式深色模式解析修复

## 根因

设备偏好中同时存在 `theme_mode=1`（显式深色）和历史主题 profile `mode=light`，系统夜间模式为 `no`。`ThemeController.resolveWith()` 原来固定使用 `ThemeMode.SYSTEM`，所以 `ThemeResolver` 按系统浅色解析；设置行显示“深色”，实际页面却是浅色。

## 修复

- `ThemeController.resolveWith()` 使用 `currentThemeMode()`，恢复显式模式优先级：显式浅色/深色覆盖系统，未设置时跟随系统。
- `ThemeController.refresh()` 同步使用显式模式，避免运行时刷新又回退到系统模式。
- 保留 `frozenPalette()`/`resolvedDark()` 的编译资源基线逻辑，不改变 ThemeBinder 的基线匹配契约。
- 回归测试在保存深色、保存浅色后调用 `ThemeController.applyFromPreferences(null)`，分别断言 `ThemeController.current()` 的 surface 和 primary 与对应 canonical palette 一致。

## 同批修复：`ChoiceDialog` 选项按钮固定浅色字面量

### 根因

`ChoiceDialog.styleItem()` 把选项按钮的前景/底色/描边写死为手机版浅色字面量：聚焦 `#1A73E8`/`#FFFFFF`、选中 `#E8F0FE`/`#174EA6`、常规 `#FFFFFF`/`#202124`、禁用 `#F1F3F4`/`#9AA0A6`。这些值在日间浅色表上尚可，但在**夜间表**上按钮底色仍是近白，而同屏的弹窗面板来自 `ThemeController.current()`，于是同一弹窗出现两种调色板；用户把主题色改掉后按钮也不再跟随。

### 修复

- `styleItem()` 与 `createView()` 统一从 `ThemeController.current()` 取 token：聚焦 `colorPrimary`/`colorOnPrimary`、选中 `colorPrimaryContainer`/`colorOnPrimaryContainer`、常规 `colorSurfaceContainer`/`colorOnSurface`、禁用 `colorSurfaceContainerHighest`/`colorOutlineVariant`/半透明 `colorOnSurfaceVariant`。
- 弹窗根背景由静态 `shape_shell_proxy_dialog` 改为 `ThemeEditorUi.shape(context, colorSurfaceContainerHigh, 0, 0, 22)`，与其它运行时构建的弹窗同源（同半径、同语义槽）。
- 自定义 `Dialog` 入口补 `ThemeController.bindDialog(dialog)`，让用户 profile 覆写也能到达该窗口（原先只有 `AlertDialog` 入口绑定）。

### 对比度实测（`ThemeContrast.ratio`，浅色/深色）

| 状态 | 前景 / 底色 | 浅色表 | 深色表 |
| --- | --- | ---: | ---: |
| 聚焦 | `colorOnPrimary` / `colorPrimary` | 6.39:1 | 7.50:1 |
| 选中 | `colorOnPrimaryContainer` / `colorPrimaryContainer` | 12.57:1 | 7.04:1 |
| 常规 | `colorOnSurface` / `colorSurfaceContainer` | 14.73:1 | 12.14:1 |
| 禁用 | 45% `colorOnSurfaceVariant` / `colorSurfaceContainerHighest` | 2.12:1 | 2.67:1 |

禁用态沿用原实现的「弱化但仍可辨」口径（原 `#9AA0A6`/`#F1F3F4` = 2.37:1），两表数值接近，未新增不可读组合。

## 当前验证

- `:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.theme.ThemeDialogModeTest --tests com.fongmi.android.tv.theme.ThemeControllerContractTest`：通过。
- 设备权威状态复现：`theme_mode=1`、系统 `Night mode: no`、历史 profile `mode=light`；该组合证明旧实现会显示浅色。
- 下一步：将该修复与站点筛选/选择页面主题审计合并验证，分别在显式浅色和显式深色下冷启动检查“外观与语言”和 SiteDialog。

## 回滚

回退本任务提交即可；不改变主题 profile schema、播放器路径或用户偏好格式。
