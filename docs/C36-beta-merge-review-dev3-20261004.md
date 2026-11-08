# C36：dev3 合并远端 beta 最新代码（PR#399–#402）并复评主题对比度两提交

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev3`（远端已移除/回退的提交不得顺带带回）；复评 dev3 全部已修改代码（含已提交未推送的 `ef8bed60b`/`c39e5748a` 主题对比度修复）；发现问题即修复并验证，循环评审直至通过；通过后提交、推送 `dev3`、创建 `dev3 -> beta` 中文 PR（只创建，不合并）。
- **验收标准**：① 合并结果包含 `origin/beta` tip `faac13dd64b`；② 远端被回退/剔除内容零复活；③ dev3 相对 beta 净差异仅含本分支自身改动（23 个路径，含本文档）；④ 目标 JVM 测试、双 flavor 编译、UI token 检查、实机安装冷启动全部通过；⑤ 中文 PR 已创建且描述排版清楚。
- **当前状态**：合并 + 两轮评审 + 全部验证完成；第 1 轮 1 项、第 2 轮 2 项均判定为既有问题（证据见下），本任务净差异内零缺陷。
- **下一动作**：`task_guard.sh finish`（合并提交 + recovery tag）→ 推送 dev3 → 创建 PR（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev3` |
| 任务开始时 HEAD | `c39e5748a5ee1b3aadfd01cd9f5dbd640662db61`（领先 origin/dev3 2 个提交，未推送） |
| `origin/beta` tip | `faac13dd64b8e7363ed1242573e784ba4b2d197c`（Merge PR #402 from dev4） |
| 合并基点 | `8847705081083a5db10643b8d31da33b30b75911`（dev3 侧 C34 合并提交，已随 PR #401 进入 beta） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`（由 task_guard finish 创建合并提交） |
| 合并结果 | 32 文件合入，**0 冲突** |
| 合并树哈希 | `abc7eae3cb8e081f59e289030c0c0bf6978eeb1e` |

beta 侧增量（`884770508..faac13dd6`，**11 个提交**）：

- `faac13dd6` Merge PR #402（dev4）、`3400cd7b0` + `255059d48`（dev4 侧 TV 横幅按密度输出）、`62527bec4`（dev4 侧 beta 增量合并）
- `13f07c8cb` Merge PR #401（dev3）、`e224f1b8c` Merge PR #400（dev1）、`490218a66` Merge PR #399（dev4）
- `f22e24f5f`（dev1 侧 beta 增量合并）、`f2cd006b0`（启动按 `config_<type>` 偏好选上次使用接口）
- `699635fca`（追更取消跨设备同步：墓碑方案）、`e2304fc1d`（task-guard staged 空白校验分批，规避 Windows argv 上限）

## 被回退/剔除内容核对（用户核心关注点）

1. **路径级零交集**：`dev3` 侧待合入净差异（`c39e5748a..HEAD`）与 beta 侧增量（`884770508..origin/beta`）的**变动路径集合交集为 0**，合并本身不构成任何"带回复退内容"的通道。
2. **回退提交仍在祖先链中且未被改写**：`fd29d76ee`（撤销把电视版暗色配色搬到手机版的两个提交，21 文件）、`5682f2b05`（剔除 PR #353 主题系统改动）在合并前后均为 HEAD 祖先，其回退效果原样保留。
3. **回退产物未被复活**：`fd29d76ee` 涉及的关键路径在合并树中与 `origin/beta` **零差异**：
   - `app/src/mobile/res/values/webhtv_tokens.xml` —— 在 `origin/beta` 与合并树中**都不存在**（回退删除状态保持）；
   - 19 个被还原为上游字面量的 drawable 抽检仍为裸色（如 `selector_dialog_step_button.xml` 的 `#E8F0FE`、`shape_ad_rule_card.xml` 的 `#F8F9FA`/`#DADCE0`）。
4. **beta 自带删除亦被正确带入**：beta 移除的 `drawable/ic_banner.png`、`drawable/ic_banner_foreground.xml`、`mipmap-anydpi-v26/ic_banner.xml` 在合并树中均已删除，5 档 `mipmap-*/ic_banner.png` 全部就位，无任何存活代码引用被删文件。
5. **可验证的合并等价性**：以 `git merge --no-commit --no-ff origin/beta` 在基线 `c39e5748a` 上重放合并，`git write-tree` = `abc7eae3c…`，与前述合并提交的树哈希逐字节一致 → 合并结果确定、可复现，无隐藏改动。
6. **合并带来的空白**：对合并引入的精确差异（`c39e5748a..origin/beta`）运行 `git diff --check` 退出码 0，无空白错误（含 task_guard 新版分批 staged 校验路径）。

## 评审记录

### 第 1 轮：dev3 未推送两提交 —— 手机版深色表面文字对比度

复评对象 `ef8bed60b`（12 个 `dialog_*` 布局 + TMDB 深色页芯片）与 `c39e5748a`（3 个 Java 对话框 + `shape_mpv_action_menu`），逐项核验：

- **面板底色与前景成对性**：12 个手机版布局的根背景确为恒定深色面板（`shape_player_child_sheet_panel`/`shape_dialog_glass_panel`/`shape_dialog_control_glass_panel`/`shape_quick_search_panel`/`shape_danmaku_sheet_panel`/`shape_danmaku_setting_panel`，渐变 `#E62F315E→#D6282955→#CC303463` 或实色 `#FF28282A`），而其前景已全部改为与调色板无关的常量亮色。**程序化扫描**（剥离 XML 注释后逐属性比对 `android:textColor`/`android:textColorHint`/`app:tint`/`app:strokeColor`/`app:rippleColor`/`app:boxStrokeColor`）：12 个文件**零遗漏**，全部解析为 `?attr/webhtvColorOnWallpaper`、`@color/webhtv_color_player_control_muted`、`@color/webhtv_color_overlay_light` 或 `@color/selector_control_sheet_text`。
- **三张 token 表取值一致性**：`webhtv_on_wallpaper`=`#FFFFFF`、`webhtv_color_player_control_muted`=`#CCFFFFFF`、`webhtv_color_overlay_light`=`#14FFFFFF` 在 light/night/leanback 三表中**完全相同**，因此"与调色板无关"的断言成立。
- **对比度精算（合成到最不利的浅色画面上）**：渐变最亮档 `#CC303463` 叠在白底得 `#595D82`，纯白字 6.34:1、`#CCFFFFFF` 4.76:1 —— 两个角色**均 ≥ 4.5:1**，即最坏情形也达标。
- **Java 对话框侧**：`ThemeTokens` 常量与 `ThemeController.current()` 语义角色一一对应（`onSurface`/`onSurfaceVariant`/`primary`/`primaryContainer`/`outline`/`outlineVariant`/`surfaceContainerLowest`/`error`/`onPrimary`），`Color.TRANSPARENT` 仅用于窗口背景与 Tab ripple（非前景，不含调色板假设）。`shape_mpv_action_menu.xml` 已随文字同步迁移为 `?attr/colorSurfaceContainerLowest` + `?attr/colorOutlineVariant`，**菜单底与文字同调色板**，未出现"只改一半"。
- **allowlist 一致性**：`shape_mpv_action_menu.xml` 条目已从 `docs/ui-token-allowlist.txt` 移除（该文件已无裸色），新增 `selector_tmdb_flag_item_dark.xml` 条目（恒定半透明叠层），计数 191 → 190 → 191，与各自改动语义自洽。
- **守卫测试**：`DialogForegroundTokenTest` 3 项 + `ThemeBaseWiringTest` 新增 3 项（12 面板逐属性、sheet 按钮文字状态表、TMDB 深色芯片）断言前均剥离注释，逐条与实现对齐。
- **唯一失败项判定为既有**：全量 JVM 套件中 `MultiThreadProxyRouteTest#proxyStreamsParallelShardsAsSingleOrderedPartialResponse` 失败。证据：① 该文件在合并前后与 `origin/beta` **逐字节一致**；② `c39e5748a` 与 `origin/beta` 之间该目录**零差异**，与本次主题改动无任何路径交集；③ 隔离重跑 4 次全部通过（含 `--rerun-tasks` 3 次）→ 并发/时序型 flaky，非本任务引入。按 AGENTS.md 范围规则不扩大修复面，仅记录。
- 结论：**通过**。

### 第 2 轮：复评"第 1 轮结论 + 合并后最终状态"

第 1 轮修复后不存在新增改动，本轮针对**合并后的最终工作树**再评一次：

- **合并零冲突、净差异精确**：合并树相对 `origin/beta` 的差异**恰好 23 个路径**，全部为 dev3 自身改动，**不含任何 beta 回退内容**。逐项构成：主题改动 19 个（3 个 Java 对话框 + `shape_mpv_action_menu.xml` + `selector_tmdb_flag_item_dark.xml` + 12 个手机版 `dialog_*` 布局 + `docs/ui-token-allowlist.txt`）+ 测试 2 个（`DialogForegroundTokenTest`、`ThemeBaseWiringTest`）+ 文档 3 个（本文档、`dialog-foreground-semantic-tokens-20261004.md`、`mobile-dark-surface-text-contrast-20261004.md`）。程序化校验：`gh pr view 403 --json files` 的文件集与 `git diff --name-only origin/beta HEAD` **完全一致（MATCH）**；
- **beta 增量与 dev3 改动的主题面隔离**：beta 侧增量触及的资源仅为 `leanback` 横幅资产（`drawable/ic_banner.png`、`drawable/ic_banner_foreground.xml`、`mipmap-anydpi-v26/ic_banner.xml` 的移除，以及 5 档 `mipmap-*/ic_banner.png` 的新增），**未触及任何 token 表、`theme/` 包、`/layout/`、`/drawable/` 主题资产或 `webhtv_styles`**（程序化路径过滤实测：该过滤器下 beta 增量仅命中上述 2 个 `drawable` 路径），与 dev3 的主题对比度修复零交集，不存在相互覆盖；
- **既有问题二次确认（2 项，均为既有）**：
  1. `MultiThreadProxyRouteTest`（同上，与合并、与主题改动均无交集）；
  2. `scripts/check_ui_tokens.sh --strict` 报 `item_following.xml` 违规 —— 以 `git archive` 独立检出**纯 `origin/beta`** 与**合并前 `c39e5748a`** 两棵树分别运行，**三棵树输出完全一致**（`violations=1`、`min=4.28`、`pairs=38`），证明为 beta 既有问题，合并与本次改动零新增违规；
- **跨 flavor 前景分歧（记录，不修改）**：leanback 侧 11 个同名布局仍用 `?attr/colorOnSurface`/`?attr/colorOnSurfaceVariant`。这在其固定暗色表下是正确的（`onSurface`=`#E2E2E9` 在深色面板上 11.41:1），**不是本任务净差异**，且 C35 文档已明确"手机版日间表把同一角色解析为 `#1A1C1E` 才是缺陷根因"；按范围规则仅记录，不扩大修改面。
- **范围外线索（记录，不修改）**：`dialog_danmaku_search` 的 `app:indicatorColor="?attr/colorPrimary"` 位于深色面板上，日间档 `#0B57D0` 对面板 `#43456E` 仅 1.42:1。该行**与 beta 逐字节一致**（非本任务或合并引入），且属另一个面板族（`shape_danmaku_sheet_panel`）的既有问题；按 AGENTS.md 范围规则仅报告，建议后续单独立项。
- 结论：**通过**（本轮零新增问题）。

## 验证记录

| 验证项 | 结果 |
| --- | --- |
| 合并冲突 | 0 冲突，32 文件自动合入 |
| 合并树重放一致性 | `git write-tree` = `abc7eae3c…`，与合并提交树哈希逐字节一致 |
| 合并引入差异空白校验（`git diff --check c39e5748a origin/beta`） | 退出码 0 |
| dev3 净差异（vs `origin/beta`） | 恰好 23 个路径，与自身提交文件集一致（PR#403 files 程序化比对 MATCH） |
| dev3 未推送提交 vs beta 增量路径交集 | 0 |
| 12 个深色面板布局前景属性扫描 | 0 遗漏，全部为与调色板无关角色 |
| 最不利合成底色对比度 | 纯白 6.34:1、`#CCFFFFFF` 4.76:1（均 ≥ 4.5:1） |
| `MultiThreadProxyRouteTest` 隔离重跑 | 4/4 通过（含 3 次 `--rerun-tasks`）→ 既有 flaky |
| `check_ui_tokens.sh --strict` 三树对照（beta / 合并前 / 合并后） | 输出完全一致，`violations=1`（既有 `item_following.xml`），零新增 |
| leanback JVM 套件（定向：theme/following/tmdb） | 176 用例，0 失败 |
| mobile JVM 套件（定向：theme/following/tmdb/ui） | 1326 用例，0 失败 |
| 双 flavor 全量 JVM 套件 | leanback 176 / mobile 5117，**仅 1 项既有 flaky 失败**，其余 0 失败 0 错误 |
| 构建安装（覆盖安装，未卸载） | `scripts/build_arm64_debug_install.sh --flavor mobile --serial 192.168.50.3:5559` 成功 |
| 实机冷启动 | `HomeActivity` 正常 resume，`FATAL EXCEPTION`/`ANR` 计数 **0** |

## 提交与推送

- 本任务产物：merge commit（含本文档）+ recovery tag。
- PR：dev3 → beta，中文描述，**只创建不合并**。

## Next action

`task_guard.sh finish` → push dev3 → `gh pr create`（只创建不合并）。
