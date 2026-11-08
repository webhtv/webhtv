# Java 构建对话框的前景色改用语义角色（方案 B）

任务：`dialog-foreground-semantic-tokens`
分支：`dev3`，基线 HEAD `ef8bed60b56ac18028c7d956d110679a7e8b48e9`
时间：2026-10-04 CST

## Recovery anchor

- **目标（完成句）**：把 `DebugLogDialog` / `MpvConfigDialog` / `PlaybackPerformanceDialog` 里硬编码的前景色换成语义角色，使其在**日间与夜间**都随 `shape_shell_proxy_dialog` 面板正确取值，并加守卫测试。
- **验收标准**：三个对话框在日间与夜间两种模式下文字对比度 ≥ 4.5:1；守卫测试在修复前失败、修复后通过；`check_ui_tokens.sh` 相对 HEAD 无新增违规。
- **状态**：已完成并双模式实机验证。
- **下一步**：无（已提交）。

## 缺陷

三个 Java 构建的对话框把前景色**写死**为 Material 灰色/蓝色，但它们的容器是
`shape_shell_proxy_dialog`，其填充为 `?attr/colorSurfaceContainerHigh` ——
**跟随调色板**（日间 `#E7E8EF` 浅色 / 夜间 `#2A2F34` 深色）。写死的前景色只能在其中一种模式下正确。

| 类 | 硬编码 | 日间 | 夜间 |
|---|---|---|---|
| `DebugLogDialog` | `#5F6368`（说明）/ `#202124`（开关标签） | 4.95:1 / 13.18:1 ✅ | **2.23:1 / 1.19:1 ❌** |
| `MpvConfigDialog` | `#5F6368`/`#1A73E8`（Tab）、`#202124`/`#C5221F`（操作菜单） | ✅ | **❌** |
| `PlaybackPerformanceDialog` | 9 种字面量共 27 处 | ✅ | **❌** |

这是「面板跟随调色板、前景写死」这一类缺陷——与已修复的「面板固定深色、前景跟随调色板」正好相反。

## 为什么选方案 B

两个候选：

- **方案 A**：把面板改成恒定亮色（写死 `shape_shell_proxy_dialog` 的填充）。
  日间正确，但**夜间对话框会突然变白**，与深色页面/播放页撕裂；且该 drawable 被 19 个布局共享，影响面大。本质是**绕过**调色板。
- **方案 B（采用）**：前景改用语义角色，面板保持跟随调色板。
  面板与前景**成对**跟随，日间浅底深字、夜间深底浅字，两种模式永远自洽。只动赋值点，影响面小，且与既有范式一致（`LightDialog.java:160` 已是 `ThemeController.current().colorOnSurface()`，仓库内共 13 处先例）。

## 改动

### 1. 语义映射（三张表取值见 `webhtv_tokens.xml` / `values-night` / `leanback`）

| 原字面量 | 语义角色 | 用途 |
|---|---|---|
| `#202124` | `colorOnSurface()` | 标题、行名、开关标签 |
| `#5F6368`、`#3C4043` | `colorOnSurfaceVariant()` | 说明、次要文字、未选中 Tab |
| `#174EA6`、`#1A73E8` | `colorPrimary()` | 小节标题、选中 Tab、聚焦态 |
| `#8AB4F8` | `colorOutline()` / `colorPrimary()` | 按钮描边 |
| `#E8F0FE` | `colorPrimaryContainer()` | 覆盖态行底 |
| `#C4C7C5` | `colorOutlineVariant()` | 常态行描边 |
| `Color.WHITE`（按钮底） | `colorSurfaceContainerLowest()` | 按钮底 |
| `#C5221F`（删除项） | `colorError()` | 危险操作 |
| `Color.WHITE`（聚焦文字） | `colorOnPrimary()` | 聚焦态按钮文字 |

取色统一走 `ThemeController.current()`（返回缓存的 `ThemeTokens`，无额外开销）。

### 2. 连带修复：`shape_mpv_action_menu.xml`

该弹出菜单原本**写死白底**（`#FFFFFF` + `#E1E5EA` 描边），而 `MpvConfigDialog.actionItem`
的文字也是硬编码深色——两者原本自洽。若只改文字，夜间会变成「近黑底 + 近黑字」，
**引入新缺陷**。因此同步迁移为 `?attr/colorSurfaceContainerLowest` +
`?attr/colorOutlineVariant`，并从 `docs/ui-token-allowlist.txt` **移除**其豁免条目
（该文件已不再含裸色；allowlist 约定「条目须在文件迁移后移除」）。
allowlist 计数由 192 回到 191，即 HEAD 原值。

### 3. 明确排除项

- `Color.TRANSPARENT`（窗口背景、Tab ripple）与 `ColorDrawable` 保留：它们不是前景，不含调色板假设。
- 未纳入：其余 19 个使用 `shape_shell_proxy_dialog` 的布局本身已正确（`?attr/colorOnSurface` 配跟随调色板的面板）。

## 守卫测试

新建 `app/src/test/java/com/fongmi/android/tv/theme/DialogForegroundTokenTest.java`，3 项：

1. `javaBuiltDialogsDoNotPinAFixedForeground` — 三个类不得出现 9 种禁用字面量，也不得再用 `Color.parseColor("#…")`。
2. `javaBuiltDialogsReadTheActiveTokens` — 必须从 `ThemeController.current()` 取前景并导入该类。
3. `mpvActionPopupFollowsTheSamePaletteAsItsForeground` — 弹出菜单的底与描边必须跟随调色板，不得保留固定值（钉住「底与前景同调色板」这一对，防止只改一半）。

三项断言前均剥离 XML/Java 注释，避免文件内说明文字影响判定。

**区分力验证**：把守卫逻辑跑在 `HEAD`（修复前）源码上，三个类**全部报违规**；跑在修复后源码上全部通过。

## 验证

| 项目 | 结果 |
|---|---|
| `DialogForegroundTokenTest` | 3 项全过 |
| theme + setting + ui.dialog + ui.style 套件 | 322 项，0 失败 0 错误 |
| `scripts/check_ui_tokens.sh --strict` | 与 HEAD 一致（仅剩既有 `item_following.xml`） |
| 构建安装 | `scripts/build_arm64_debug_install.sh --flavor mobile --serial 192.168.50.3:5559` 成功（覆盖安装） |
| 实机 `FATAL EXCEPTION` | **0** |
| 实机日间 · 播放性能对话框 | 标题 16.35:1、Tab 7.20/8.70:1、分组标题 8.70:1、行 16.35:1 |
| 实机夜间 · 播放性能对话框 | 标题 16.54:1、Tab 11.32/11.30:1、分组标题 11.30:1、行 16.54:1 |

修复前同一批元素在夜间为 1.19–2.23:1。

### 设备状态复原

验证期间曾切到夜间模式，结束前已 `cmd uimode night no` 恢复日间，并 `force-stop` 应用。
安装使用 `install -r` 覆盖，未卸载既有包。

## 回滚

单次提交，`git revert <commit>` 即可。涉及 3 个 `.java`、1 个 `drawable`、
1 个测试文件、`docs/ui-token-allowlist.txt`（-1 行）与本文档。
