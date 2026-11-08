# 观影记录同步弹窗深色模式语义 token 化（评审与修复）

任务：`viewing-record-sync-dark-theme`
分支：`dev3`
时间：2026-10-19 CST
基线：`origin/dev3` = `f6ccddf1313724ed5169fd75f58d655b1ab52641`
被评审提交：`ad21137c0f`（fix(ui): 观影记录同步弹窗深色模式改用语义 token）

## Recovery anchor

- **目标（完成句）**：评审 `ad21137c0f` 已提交未推送的改动，修复评审发现的问题，复评通过后提交并推送。
- **验收标准**：三个弹窗无任何写死颜色；守卫测试对每个调用点具备区分力（单点回退必须失败）；`theme.*` + `ui.dialog.*` 全绿；`check_ui_tokens.py --strict` PASS。
- **状态**：已完成并复评通过。
- **下一步**：无（已提交）。

## 缺陷（原提交已修）

`ViewingRecordSyncDialog` 及其两个二级弹窗（`PlaybackRemoteSyncDialog` / `PlaybackWebhookDialog`）
在代码里构建行卡片，底色/描边/标题/说明/状态色全部写死为浅色字面量。这些值都不等于任何
`webhtv_color_*` 角色，`ThemeBinder` 的精确匹配改写按设计跳过它们，于是深色表下弹窗面板变深
而每行仍是近白卡片配黑字（`#5F6368` 说明在深色面板上仅 2.23:1），即用户报告的「色调太乱」。

原提交已把颜色改为 `ThemeController.current()` 语义 token，方向正确。本次评审针对其**质量与守卫强度**。

## 评审发现与修复

### 1. 守卫测试缺乏区分力（最高价值，已修）

原守卫是**整文件子串存在性检查**。三个文件里 `colorOnSurface()` / `colorOnSurfaceVariant()`
各有多处调用点，因此**只回退其中一处**为 `Color.GRAY` / `0xFF5F6368` / `R.color.<旧字面量>`，
5 项测试仍全绿，缺陷可静默回归。

原守卫只拦 `Color.parseColor("#` 加 9 个禁用字面量，以下四条路径全部漏网：

| 漏网路径 | 真实存在 | 是否已拦 |
|---|---|---|
| 裸 `Color.*` 常量（`Color.BLACK`/`WHITE`/`GRAY`…） | 是 | 已拦（仅放行 `Color.TRANSPARENT`） |
| 8 位整型色 `0xFF5F6368` | 是 | 已拦（排除 `0x7f` 资源 id 前缀） |
| `R.color.<非调色板资源>` | 是 | 已拦（放行 5 个 `res/color/` 选择器） |
| `Color.rgb(32, 33, 36)` 等构造式 | 是（`MpvCustomButtonDialog`、`PlaybackPerformanceModal` 正是这样写死 `#202124`） | 已拦 |

**改法**：新增 `REQUIRED_SITES` + 花括号配平的 `methodBody()`，把断言从「整文件出现过该 token」
收紧为「**该方法的函数体**内必须出现该 token」。签名重复或缺失会显式失败，不会静默匹配错位置。

**区分力实测**：
- 回退到修复前源码：5 项中 **4 项失败**。
- 修复后源码上只回退 `PlaybackRemoteSyncDialog.row()` 标题为 `Color.GRAY`：**失败**。
- 只回退 webhook 暂停行描边为通用描边：**失败**。
- 注入 `Color.rgb(19,115,51)` + `0xFF5F6368`（旧守卫漏网的两条路径）：**失败**。
- 修复后源码：**5 项全过**。

### 2. 暂停行丢失描边级错误线索（已修）

原提交把暂停行保留 `colorErrorContainer()` 底色，但描边改成了通用 `colorOutlineVariant()`，
删掉了唯一的描边级错误线索。而错误底与普通卡片的**填充**对比仅 1.15:1（日）/ 1.83:1（夜），
线索退化为纯色相提示。现恢复为配对的 `colorError()` 描边（与 `CustomCspDialog.rowBackground`
既有范式一致），错误字在该底上 5.14:1 / 5.51:1。

### 3. 死参数（已修）

`PlaybackRemoteSyncDialog.rowBackground(RemoteSyncConfig config)` 在 token 化后已不再读 `config`，
调用点仍传参。已删参，与 `ViewingRecordSyncDialog.rowBackground()` 对齐。

### 4. 引用数字错误（已修）

文档注释称原先两个描边灰阶「对比度仅 1.02:1」，实测 `#DADCE0` vs `#E2E5E8` = **1.09:1**。已更正。

### 5. 重复代码（已修）

`VIEWING_RECORD_SYNC_SOURCES` 逐字重复 `DIALOG_SOURCES` 中 3 项，徽标测试又硬编码同样两个名字第三次。
现 `DIALOG_SOURCES = concat(BASE_DIALOG_SOURCES, VIEWING_RECORD_SYNC_SOURCES)`，徽标断言改为按方法体取。

### 6. 第二轮复评追加修复

第一轮修复提交后复评，又发现 3 项，均已修复：

**(a) `ViewingRecordSyncDialog.actionButton` 漏在契约外（完整性问题）**
两个兄弟弹窗同形状的 `actionButton` 都在 `REQUIRED_SITES` 里，唯独顶层弹窗的这个没有，
其 `danger ? … colorError()` 分支（`:235`）因此无守卫 —— 正是本次重写要拦的「单调用点回退」类型。
已补入。

**(b) 花括号配平器的静默失准风险**
`methodBody()` 直接数原始花括号，一旦这些文件出现字符串/字符字面量里的 `{` 或 `}`，
扫描会错位并**静默返回截断的函数体**，使 `REQUIRED_SITES` 全部契约悄悄失效却仍报绿。
已新增 `assertNoBraceInLiteral()` 把这种形态显式拒绝（当前 6 个文件均无此形态，实测确认），
并在 `methodBody()` 的注释里写明该前提。

**(c) 放行清单注释论证过头**
注释称 5 个选择器「每一项都指向 `webhtv_color_*`」，但 `dialog_outlined_button_bg.xml`
最后一项是 `@color/transparent`（`#00000000`，与明暗无关）。放行决定不变，仅修正论证表述。

### 7. 保留的判断（未改，记录理由）

- **卡片 token 取 `colorSurfaceContainerLow`**：面板是 `colorSurfaceContainerHigh`
  （日 `#E7E8EF` / 夜 `#2A2F34`），卡片日 `#F2F3F9` / 夜 `#181C20`，与面板差 1.10:1 / 1.27:1，
  两表都能分辨；全部文字过 AA。原状态是完全不可读，此为明确改进，故不改以免扩大范围。
- **暂停行前景未改用 `colorOnErrorContainer`**：`CustomCspDialog` 既有范式即为「错误容器底 +
  表面前景」，且实测标题 13.44:1 / 7.26:1、说明 7.31:1 / 5.49:1、错误字 5.14:1 / 5.51:1 全部过 AA，
  故按仓库既有范式保留。

## 验证

| 项目 | 结果 |
|---|---|
| `DialogForegroundTokenTest` | 5 项通过 |
| `theme.*` + `ui.dialog.*` | 60 套件 349 项，0 失败 0 错误 |
| 守卫区分力（修复前源码） | 4/5 失败 |
| 守卫区分力（单点回退注入） | 失败（3 项） |
| 守卫区分力（`actionButton` 语义替换 `colorError`→`colorPrimary`，不引入任何字面量） | 失败（仅新契约可拦） |
| 守卫区分力（字符串字面量内注入 `{`） | 失败于 `assertNoBraceInLiteral`（`:374`） |
| `python scripts/check_ui_tokens.py --strict` | PASS（violations=0 legacy=0，pairs=38 min=4.28） |
| 对比度复算 | 标题 15.43/13.29、说明 8.39/10.06、徽标 6.53/11.78、暂停错误字 5.14/5.51，均过 AA |

命令：`gradlew :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*" --tests "com.fongmi.android.tv.ui.dialog.*"`（JDK 21）。

### 环境说明

本机 `bash` 不可用（`HCS_E_SERVICE_NOT_AVAILABLE`），且 `.codex/scripts/` 仅有 `task_guard.sh`、
无便携入口，故未运行 task_guard，改为逐项手工核对允许路径；`check_ui_tokens.py` 为可执行的 bash-free 入口，已运行。

## 回滚

单次提交，`git revert <commit>` 即可。涉及 3 个 `.java`、1 个测试文件与本文档。
