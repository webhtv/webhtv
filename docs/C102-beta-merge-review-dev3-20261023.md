# C102-beta-merge-review-dev3-20261023

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev3`（本轮实测落后 11 个提交，冲突预检 0 冲突），评审 `dev3` 已提交未推送
  的 `7067d62b7b`（版本更新对话框的版本卡片改为跟随当前调色板取色），修复必修项并验证，修复后再次评审直至通过，
  然后提交、推送 `dev3`、创建到 `beta` 的中文 PR（只创建，不合并），最后拉取远端最新代码。
- **验收标准**：
  1. `dev3` 相对 `origin/beta` 落后 0 个提交（`origin/beta` 成为 HEAD 祖先），合并没有丢失 `beta` 侧任何路径、
     也没有夹带评审外的改动。
  2. 评审覆盖 `7067d62b7b` 的 4 个路径 / +436 −0，以及合并带入的 30 个路径 / +1036 −27；每个结论都有可复核证据；
     `C101` 已评审且本轮合并未改写其内容的路径按用户约定跳过，并写明跳过依据。
  3. 每个必修项均已修复；修复有回归用例并经负向对照证明其真实有效；修复后复评通过。
  4. 定向单测与合并后回归套件双 flavour 全绿（合计 93 个测试类 / 601 个用例 / 0 失败 0 错误 0 跳过）、
     `scripts/check_ui_tokens.py` PASS、`git diff --check` 与 `git diff --cached --check` 为空、无冲突标记。
  5. 本轮改动经任务守卫 `finish` 落盘（含合并提交）并创建 recovery tag；推送 `dev3`；
     创建到 `beta` 的中文 PR（含具体改动内容）；随后拉取远端最新代码。
- **Lane**：`upstream`（`app` + `docs`）
- **Scope**：`app`、`docs`
- **任务守卫**：`C102-beta-merge-review-dev3-20261023`，base HEAD `7067d62b7b51bf413eacd9501a02ca65422b8d5e`，
  branch `dev3`，受保护既有脏路径 35 条（`%SystemDrive%/`、`.design-evidence/`，保持不动、不入库）
- **当前状态**：合并、评审、F1 修复、负向对照、复评、验证均完成；提交/推送/PR/拉取见 §8
- **下一动作**：无；等待 PR 评审/合并（本轮只创建 PR，不合并）

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `d9da14dcb77e84d4beb158e83efa74124a1116f7`（"Merge pull request #484 from Silent1566/dev4"，2026-10-23 15:30:07 +0800） |
| 合并前本地 | `dev3` = `7067d62b7b51bf413eacd9501a02ca65422b8d5e`（"fix(update-dialog): paint version cards from the active palette so text and fill cannot diverge"，2026-10-23 19:30:25 +0800） |
| 祖先关系（合并前） | `git merge-base --is-ancestor origin/beta HEAD` → **false**（落后 11 个提交） |
| 落后 / 领先（合并前） | `git rev-list --left-right --count origin/beta...HEAD` = **11 / 1** |
| merge-base | `3b02e02c07f30a5ee9361f548d94cd4486a64749`（"docs(C100)…"，即 PR #482 合并点） |
| `beta` 独有提交（11） | `d9da14dcb7`（PR #484 合并）、`0bccba71c5`（review C101，仅新增评审文档 157 行）、`e377db6cf2`（dev4 合并 beta db4a96cec32）、`36c6ed5324`（review E-SP12：PlayerOsdController javadoc 修正 + 文档）、`db4a96cec3`（PR #483 合并）、`7443e1e474`（dev4 合并 beta）、`dcf9828cba`（dev4 拉取 beta）、`ea678c0fd4`（PR #482 合并）、`82a2731f4e`（docs E-SP12 模拟器实测证据）、`fd5bcf109e`（fix E-SP12：掉帧按播放时长归一）、`29d357d882`（feat theme：第 17 槽 `playerScrimOpacity` 播放菜单遮盖层） |
| 冲突预检 | `git merge-tree --write-tree --name-only dev3 origin/beta` → **rc=0**，仅返回 tree `80d3c477f475feca07c1540e1429cd137bc212f7`，无冲突文件段 |
| 合并动作 | `git merge --no-commit --no-ff origin/beta` → "Automatic merge went well; stopped before committing as requested"；暂存区 **30 个路径 / +1036 −27**，0 冲突、0 手工解冲突。合并提交由守卫 `finish` 在**同一会话内**生成（`.git/MERGE_HEAD` = `d9da14dcb77e84d4beb158e83efa74124a1116f7`，父提交 `7067d62b7b` + `d9da14dcb7`），因此本轮的「合并」与「评审修复」落在同一个原子提交里，不需要像 C101 那样在守卫会话之外先合并 |
| 合并纯净性（正向） | `git diff --cached --name-status HEAD` = **30 条**，与 `git diff --name-only 7067d62b7b origin/beta` 的 `beta` 独有路径集逐条一致 → 合并只带入 `beta` 内容 |
| 合并纯净性（反向） | `git diff --cached --stat origin/beta` = **仅 dev3 的 4 个路径 / +436 −0** → 合并后的树与 `origin/beta` 只差 dev3 自己的改动，`beta` 一个路径都没丢 |
| 合并是否触碰 dev3 已评审路径 | **否**。`git diff 3b02e02c07 HEAD -- app/src/main/java/com/fongmi/android/tv/theme/ app/src/main/java/com/fongmi/android/tv/ui/` 输出**仅** `UpdateVersionRowTheme.java`（dev3 自己的新增），说明合并没有静默覆盖 dev3 在这两个目录下的任何内容 |

需评审的未推送提交（1 个）：

| 提交 | 说明 | 是否本轮新评审 |
| --- | --- | --- |
| `7067d62b7b` | fix(update-dialog) 版本卡片改由当前调色板取色（4 路径 / +436 −0） | **是**（dev3 线独有；仓库内无该提交的评审文档、无 recovery 之外的评审记录） |
| `d9da14dcb7` 等 11 个 `beta` 提交 | `beta` 侧内容 | 否（C101 已完整评审，见 §2 跳过依据） |

结论：`beta` 侧 11 个提交全部进入 `dev3`；唯一需要新评审的是 `7067d62b7b` 的 4 个路径。

## 2. 评审范围与跳过依据

**本轮全量评审对象**：`7067d62b7b`。双口径核对：对 `origin/beta` 的净差异 = **4 路径 / +436 −0**；
合并带入 = **30 路径 / +1036 −27**。

| 路径 | +/- | 评审结论 |
| --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/theme/UpdateVersionRowTheme.java`（新增） | +208 | 全文读完。**F1 必修项**：卡片背景改由 Java 绘制后，「聚焦」与「选中」填充退化成同一个色，且描边宽度硬编码 2dp/1dp、绕开仓库唯一宽度 token；已修复，见 §3-F1 / §5 |
| `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/UpdateDialog.java` | +25 | 接线正确：`renderItem()` 在 `content.setVisibility(...)` 之后、`setText(...)` 之前调用 `applyCardTheme(channel, expanded)`；`toggle()` 两条分支都会经 `renderItem` —— 见 §4-2 |
| `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/UpdateDialog.java` | +18 | 与 leanback 同形（差异仅 "stable"/"beta" 行 id 与风格 overlay），无分叉 —— 见 §4-2 |
| `app/src/test/java/com/fongmi/android/tv/theme/UpdateVersionRowThemeTest.java`（新增） | +185 | 原 8 例覆盖 AA、填充切换、胶囊、环可见性、截图色对、回退、双 flavour 源码契约、语义角色；**F1 修复后新增 2 例**补上「焦点 vs 选中」与「三态 + token 宽度」运行时契约 —— 见 §4-6 |

**净差异中属上一轮（C101）已评审、按用户约定跳过重评的 30 个路径**（合并带入，本轮未改写其内容）：

- E-SP12 播放器（9）：`player/engine/ExoPlayerEngine.java`(+4−2)、`player/exo/DroppedFrameAccounting.java`(+108)、
  `player/exo/ExoDecoderRuntimeSession.java`(+34−2)、`player/exo/PlaybackAnalyticsListener.java`(+51−8)、
  `ui/custom/PlayerOsdController.java`(+42−2)、`test/.../player/exo/DroppedFrameAccountingTest.java`(+103)、
  `test/.../player/exo/ExoDecoderRuntimeSessionTest.java`(+62−1)、`docs/E-SP12-exo-pause-settled-dropped-frames.md`(+250)、
  `docs/upstream-player-dependency-merge-assessment-2026-08-20.md`(+1)
- 第 17 槽播放菜单遮盖层主题（17）：`theme/PlaybackMenuScrim.java`(+54)、`theme/ThemeController.java`(+17)、
  `theme/ThemeEditor.java`(+8−1)、`theme/ThemePreviewView.java`(+6−1)、`theme/ThemeProfile.java`(+8−2)、
  `theme/ThemeProfileValidator.java`(+3)、`{leanback,mobile}/.../activity/{LiveActivity,VideoActivity}.java`(各 +3−1)、
  `res/values{,-zh-rCN,-zh-rTW}/strings.xml`(各 +1)、`test/.../setting/SettingPlaybackOverlayTest.java`(+4−2)、
  `test/.../theme/PlaybackMenuScrimTest.java`(+62)、`test/.../theme/ThemeEditorContractTest.java`(+5)、
  `test/.../theme/ThemeProfileCodecTest.java`(+11)、`test/.../theme/ThemeProfileValidatorTest.java`(+15)、
  `testLeanback/.../theme/ThemeEditorPanelTest.java`(+3)、`docs/THEME-CUSTOMIZATION-LAYERS-20260921.md`(+13−2)
- C101 评审文档（1）：`docs/C101-beta-merge-review-dev4-20261023.md`(+157) —— 本轮已**全文读过**（§4-11）

**跳过依据（可复核，三条同时成立）**：

1. **内容零改写**：`git diff --cached --stat origin/beta` 只剩 dev3 的 4 个路径 → 上述 30 个路径在合并后的内容与
   `origin/beta` **逐字节相同**；而合并本身冲突预检 rc=0（`merge-tree` 单 tree 返回），不存在手工解冲突引入的改写。
2. **评审对象同一**：C101 §1 的合并对象是 `db4a96cec32`（`beta` 当时的头），其评审范围覆盖 PR #483 的 20 路径与
   E-SP12 全部路径；从 `db4a96cec32` 到本轮 `d9da14dcb7` 只多两个提交 —— `0bccba71c5`（正是 C101 自己的评审文档，
   本轮已在 §4-11 全文复核）与 `d9da14dcb7`（PR #484 合并提交，无独立代码）。
3. **前序闭环已完成**：C101 §5 记录其上一轮必修项（`PlayerOsdController` javadoc 错位）已在 `36c6ed5324c` 修复并复评通过，
   该修复随本轮合并进入 dev3，本轮回归套件（§6）再次编译并通过它所在的测试类。

符合用户「已评审过且没有合入冲突可以跳过，前提是之前的评审已覆盖当前所有修改的代码」。

## 3. 评审发现与处置

| 编号 | 严重度 | 位置 | 问题 | 处置 |
| --- | --- | --- | --- | --- |
| F1 | **高（必修）** | `app/src/main/java/com/fongmi/android/tv/theme/UpdateVersionRowTheme.java` 的 `background()`；对照 `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvFocusRingContractTest.java:63`（`VERSION_ITEM`）与其 `versionCardSelectedStateIsDistinguishableFromDefault()` | 7067d62b7b 让版本卡片背景改由 Java 绘制，但把「聚焦」与「选中」的**填充写成同一个** `colorPrimaryContainer`（只靠 1dp/2dp 硬编码描边区分），直接违反 `selector_update_version_item.xml` 自述的设计意图「focused 与 selected 的填充与环色恰好互换，所以『焦点在哪』与『选中哪个』是两个互相独立的线索」；描边宽度 2dp/1dp 同时绕开仓库唯一来源 `@dimen/webhtv_focus_ring_width`（1.5dp，`values/webhtv_dimens.xml:26`）。更关键的是守卫用例 `TvFocusRingContractTest` 只读那份 XML，改成 Java 绘制后它对运行时失效——全部测试仍绿，但契约已经被破坏（正是仓库自述的「只测辅助函数未测调用点」陷阱）。leanback `UpdateDialog.onStart()` 必定 `binding.stableItem.requestFocus()`，首帧就是聚焦态，此时**未展开**的频道卡片会画成已展开/选中的填充，「焦点」与「展开」两个线索合并为一个 | **已修**（`7067d62b7b` 之上）：`background()` 恢复三态互换 —— focused/pressed → fill `colorPrimary` + ring `colorOnPrimary`，selected → fill `colorPrimaryContainer` + ring `colorPrimary`，default 不变；描边宽度改由 `R.dimen.webhtv_focus_ring_width` 提供（新增 `faced()`/`focusRingWidth()`，宽度 0 占位使 `ThemeEditorUi.shape` 不再叠加自带描边）；文本改回**随焦点变化**的状态色列表（新增 `focusFill()`/`focusInk()`/`textColors()`：focused/pressed 用 `readableOn(colorOnPrimary, colorPrimary)`，其余沿用 `readableOn(角色色, restingFill, expandedFill)`），与历史修复 49b28bd91f9 同一机制（8 个文本节点在两份 `dialog_update.xml` 中都声明 `duplicateParentState`）；环色取 `ringColor(preferred, fill)`（≥3:1 否则回退可读色）。**新增 2 个回归用例**（`theFocusedCardStaysDistinguishableFromTheSelectedCard`、`theCardFaceKeepsItsStatesAndTheSharedRingWidth`）把运行时契约钉在双 flavour 共用的测试类里，补上失效的守卫面；负向对照见 §5 |
| F2 | 信息（不改，已补契约） | `res/drawable/selector_update_version_item.xml`、`res/color/update_version_item_{title,meta,accent}.xml` | 卡片改为代码绘制后，这四处资源在运行时只剩「inflate 到首帧之前」的默认背景与文字色作用，`TvFocusRingContractTest` 对 XML 的断言对本对话框不再是运行时契约 | 不改资源：XML 仍是两份 layout 声明的默认态，删除需同时改 layout 与两处守卫用例，超出本任务范围（AGENTS.md §2）。本轮改以新增用例覆盖**运行时**面（F1），把原先无人覆盖的缺口补上；同时保留 XML 契约对其它未来消费者的约束。记录备查 |
| F3 | 信息（不改） | `app/src/mobile/.../UpdateDialog.java:171/180`、`app/src/leanback/.../UpdateDialog.java:182/191` | 两处 `stableConfirm`/`betaConfirm` 恒为 `View.GONE`（死 UI） | 不改：非本轮改动引入，删除属未授权 UI 变更（AGENTS.md §2）。记录备查 |
| F4 | 信息（不改） | `UpdateVersionRowTheme.ring(ThemeTokens, boolean)` | 生产路径不使用该方法（仅测试引用），生产描边走 `ringColor(...)` | 不改：它表达的正是「展开态环色取 `colorPrimary`、静态态取 `colorOutlineVariant`」的角色语义，与新建的 selected/default 描边一致（`ringColor(colorPrimary, colorPrimaryContainer)` 在两套出厂调色板上都保留 `colorPrimary`，由 `TvFocusRingContractTest` 的 ≥3.0 断言背书），保留作后续统一入口 |

**本轮必修项：1 个（F1），已修复并复评通过。**

## 4. 复核未发现问题的项（正面证据，非「没看」）

1. **卡片子树的所有权与剪枝**：`apply()` 先 `row.setTag(IGNORE_TAG)`（`"webhtv:ignore"`），命中 `ThemeBinder.isExempt`，
   而 `ThemeBinder.walk` 的剪枝判断在入队子节点**之前**，因此整棵卡片子树不再被主题绑定器改写；卡片子树里只有恒 GONE 的
   Confirm 按钮与手工着色的文本，剪枝不会漏掉任何需要跟随主题的控件。这与 `PlayerSettingsRowTheme`（逐 view 打标）不同、
   与 C101 文档记录的 `shape_controller_scrim` 并存关系一致：旧资源未被删除，仍被其它布局使用。
2. **调用点接线（双 flavour 同形）**：`applyCardTheme()` 在 `renderItem()` 中位于 `content.setVisibility(...)` 之后、
   全部 `setText(...)` 之前，因此每次展开/收起都是「先定色、后填文案」，不存在先写文字再被改色或反过来的中间态；
   `toggle()` 的两条分支（已展开 → `update()`；未展开 → 置 `selected/stableExpanded/betaExpanded` 后 `render()`）
   都最终经过 `renderItem`。`ThemeTokens tokens = ThemeController.current();` 每次渲染重取当前快照，避免持有过期调色板。
3. **对比度算术自洽**：dark 下 `readableOn(0xFFE2E2E9 onSurface, 0xFF34393E resting, 0xFF0842A0 expanded)` 与
   light 下 `readableOn(0xFF1A1C1E, 0xFFE1E2E9, 0xFFD3E3FD)` 都 ≥ 4.5；新增 `focusInk`：dark = `0xFF062E6F`
   （对 `0xFFA8C7FA` ≈ 7.5:1）、light = `0xFFFFFFFF`（对 `0xFF0B57D0` ≈ 6.39:1），与仓库既有
   `ThemeContrast.require` 对 onPrimary/primary 的 4.5 门槛同源 —— 即「墨色不变、只是可读性下限托底」。
4. **回退策略保守**：`readableOn(...)` 仅当首选色对**全部**填充都不达标时才改选黑白中「最小对比度更大」的一侧，
   因此 `aCollidingProfileFallsBackToAReadableForeground` 的两组人工色（`0xFF11202A`/`0xFF0F1D26` → 白、
   `0xFFDDDEE6`/`0xFFE1E2E9` → 黑）是算术结果而非硬编码特例；已能达标的颜色原样保留。
5. **状态色列表与布局机制一致**：`duplicateParentState="true"` 在 leanback `dialog_update.xml` 出现 **12 处**（84/92/119/
   132/143/151/196/204/231/244/255/263），mobile 同构；行的 focused/pressed 状态会下发给卡片内 8 个文本节点，因此
   `textColors(...)` 的 focused/pressed 分支确实会生效（新增用例按 flavour 断言 ≥ 8 处，防止将来被删）。
6. **测试契约真实有效**：`theReportedScreenshotPairIsExactlyTheUnreadableOne` 钉住的 `#0F1D26`/`#49454F` 是用户截图实测对
   （<2.0:1），不是随手取的常量；`thePillAndItsLabelStayReadable` 断言胶囊与卡片底色 ≥1.2 才保证「不再是一块看不见的色块」；
   `bothFlavoursRouteTheCardsThroughTheSharedPalette` 与 `theHelperOwnsTheCardSubtreeAndUsesOnlySemanticRoles` 是源码契约
   （含「不得出现硬编码色值」的断言，正对 F1 的同类风险）。
7. **描述文本与状态胶囊**：`MarkdownText` 只用 `StyleSpan`/`RelativeSizeSpan`（不产生颜色 span），故描述文字由 TextView 的
   `textColor` 决定，手绘的 `descColor` 不会被 Markdown 渲染覆盖；状态胶囊用恒定 `colorSurface` 作填充 +
   `readableOn(colorPrimary, colorSurface)` 作文字，与卡片底色无关，因此展开/收起切换不会让胶囊突然不可读。
8. **资源与调用面收敛**：`selector_update_version_item` 仅被两份 `dialog_update.xml` 引用
   （leanback `:73/:185`、mobile `:70/:176`）；`update_version_item_{title,meta,accent}` 仅被这两份 layout 与三个
   `res/color` 文件引用；`UpdateVersionRowTheme` 的生产调用点仅 4 处（mobile/leanback 各 2），`grep` 全仓无其它消费者 →
   改动面可控、无旁路；两个恒 GONE 的 Confirm 按钮仍在同一子树内（F3）。
9. **合并集成面**：合并带入的 `beta` 改动（第 17 槽 `playerScrimOpacity`、E-SP12 掉帧归一）与 dev3 的版本卡片改动
   **没有共享文件**（`git diff 3b02e02c07 HEAD -- theme/ ui/` 仅 `UpdateVersionRowTheme.java`）；合并后双 flavour Java 编译与
   主题/对话框回归套件全绿（§6），即 `ThemeController.current()` 这一新依赖在合并后的代码上确实可用。
10. **差异卫生与门禁**：`git diff --check` 与 `git diff --cached --check` 均为空；冲突标记扫描无命中
    （assets 下 `.txt` 里的 `====` 是数据内容，非冲突段）；`scripts/check_ui_tokens.py` = `UI_TOKEN_STATUS PASS`
    （`layouts=385 hex_layouts=0 drawables=563 hex_drawables=1 colors=64 hex_colors=0 allowlisted=190`、
    `CONTRAST pairs=38 failures=0 min=4.28`），新代码未引入裸 hex。
11. **合并带入的评审文档全文复核**：`docs/C101-beta-merge-review-dev4-20261023.md`（157 行）已逐行读完，其 §2 的跳过对象
    与本轮 §2 完全一致（E-SP12 + 第 17 槽 20 路径），§3 记录「本轮必修项 0 个」，§5 记录其上一轮必修项未回退。
    该文档在合并中未被改写（内容等于 `origin/beta` 版本），不存在同名不同内容的第二份评审结论。

## 5. 复核

- **必修项 F1 的复评对照**：修复后 `background()` 恢复三态互换、宽度取自单一 token、文本回到状态色列表，逐条对上
  `TvFocusRingContractTest` 在 XML 面要求的不变式（现在由运行时用例承担）：
  ① focused 填充 ≠ selected 填充，且比值 ≥ 3.0（dark `0xFFA8C7FA` vs `0xFF0842A0`、light `0xFF0B57D0` vs `0xFFD3E3FD`，
  由 `theFocusedCardStaysDistinguishableFromTheSelectedCard` 断言）；② selected 环 vs selected 填充 ≥ 3.0；
  ③ selected 环 vs default 环 ≥ 3.0；④ focused 墨色在 focused 填充上 ≥ 4.5（dark 7.5:1 / light 6.39:1）。
- **负向对照（证明回归用例真实有效、非空跑）**：把 `focusFill()` 临时改为返回 `colorPrimaryContainer()`（即回退到修复前的
  「聚焦＝选中」状态），单跑 mobile 的 `UpdateVersionRowThemeTest` → `theFocusedCardStaysDistinguishableFromTheSelectedCard FAILED`，
  `10 tests completed, 1 failed`，`BUILD FAILED in 18s`，`GRADLE_EXIT=1`；随后按备份逐字节还原
  （还原后 sha256 前 16 位 `f1bb085d244bc58a`，与备份一致，`restored === backup: true`）——
  即 §6 的全绿结果出自**还原后**的内容，且该用例确实能抓住 F1 的退化形态。
- **未回退核对**：`7067d62b7b` 是 dev3 线对该对话框的首次评审，无前序修复需要核对；合并未触碰 dev3 主题/UI 路径
  （见 §1 末行），也未改写上一轮已评审路径（见 §2 跳过依据第 1 条）。
- **复评涉及面**：4 个净差异路径 + 合并带入 30 路径的「未改写」证明 + 双 flavour 回归套件（93 类 / 601 例）。
- **结论：复评通过。** F1 已闭环，F2/F3/F4 为记录项不改。

## 6. 验证（2026-10-24 01:4x–02:0x +08:00）

| 项 | 命令 | 结果 |
| --- | --- | --- |
| F1 定向单测（双 flavour） | `gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.UpdateVersionRowThemeTest" :app:testLeanbackArm64_v8aDebugUnitTest --tests "...UpdateVersionRowThemeTest" --tests "...TvFocusRingContractTest" --tests "...TvFocusStatePairContrastTest"` | **BUILD SUCCESSFUL in 2m 52s**（130 tasks，22 executed / 106 up-to-date），exit 0 |
| F1 定向单测明细 | 读 `app/build/test-results/*/TEST-*.xml` | mobile **1 类 / 10 例**；leanback **3 类 / 41 例**（UpdateVersionRowThemeTest 10、TvFocusRingContractTest 27、TvFocusStatePairContrastTest 4）；0 失败 0 错误 0 跳过 |
| 合并后主题 + 对话框回归（双 flavour） | `gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*" --tests "com.fongmi.android.tv.ui.dialog.*" :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*" --tests "...TvFocusRingContractTest" --tests "...TvFocusStatePairContrastTest"` | **BUILD SUCCESSFUL in 52s**（130 tasks，12 executed / 118 up-to-date），exit 0 |
| 回归套件明细 | 读 `app/build/test-results/*/TEST-*.xml` | mobile **64 类 / 378 例**、leanback **29 类 / 223 例**，合计 **93 类 / 601 例 / 0 失败 0 错误 0 跳过**；含合并带入的 `PlaybackMenuScrimTest` 5、`ThemeProfileCodecTest` 8、`ThemeProfileValidatorTest` 9、`ThemeEditorContractTest` 12、`ThemeEditorPanelTest` 2，以及本对话框的 `UpdateDialogChannelVisibilityTest` 1、`UpdateVersionRowThemeTest` 10×2 flavour |
| 负向对照 | 临时回退 `focusFill` 后单跑 mobile 定向用例 | `theFocusedCardStaysDistinguishableFromTheSelectedCard FAILED` / `10 tests completed, 1 failed` / exit 1（预期失败，证明用例非空跑；随后逐字节还原） |
| 差异卫生 | `git diff --check`、`git diff --cached --check` | 均 rc=0、输出为空 |
| 冲突标记 | `git grep -nE '^(<{7}\|={7}\|>{7})' -- app docs` | 无命中（assets 下数据文件的 `====` 非冲突段） |
| UI token 门禁 | `G:/Python310/python.exe scripts/check_ui_tokens.py` | `UI_TOKEN_STATUS PASS`（`CONTRAST pairs=38 failures=0 min=4.28`） |

环境备注（不属本任务改动）：执行前需 `ANDROID_HOME=C:\Users\Maple\AppData\Local\Android\Sdk`（仓库无 `local.properties`）、
`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`、`GRADLE_USER_HOME=G:\GradleCache`；
DSH bash 工具无法写入 `.git`（`FETCH_HEAD`/索引写入报 Permission denied），故 fetch/merge/commit/push 与
`gradlew.bat` 均由 pwsh 执行。

## 7. 回滚

- **本轮评审记录**：删除 `docs/C102-beta-merge-review-dev3-20261023.md`（无生产代码影响）。
- **F1 修复**（紧急回退）：`git checkout 7067d62b7b -- app/src/main/java/com/fongmi/android/tv/theme/UpdateVersionRowTheme.java
  app/src/test/java/com/fongmi/android/tv/theme/UpdateVersionRowThemeTest.java` —— 回到修复前。**不推荐**：会同时恢复
  「聚焦＝选中」的焦点线索缺陷。
- **合并本身**：`git revert -m 1 <§8 合并提交>` 可整体撤回 `beta` 合入（会连带撤回第 17 槽遮盖层与 E-SP12 掉帧口径）；
  如需单独回退某一功能，可针对对应提交 `git revert`。
- 无偏好写入、无数据库/协议/二进制/schema/依赖变更；资源未删除，回滚不需要恢复资源。

## 8. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `db553538b2896a0177ee6c2f6d0f138b9ccf7666`（合并提交，父提交 `7067d62b7b` + `d9da14dcb77e84d4beb158e83efa74124a1116f7`；`git rev-list --parents` 3 token 确认双亲），33 路径 / +1373 −38（其中合并面 30 路径 / +1036 −27，F1 修复 + 评审文档 3 路径）；由守卫 `finish` 在本会话内一次提交完成合并、F1 修复与评审文档（这是与 C101 的差别） |
| 评审提交 | 同上 `db553538b2896a0177ee6c2f6d0f138b9ccf7666`（含合并结果 + F1 修复 2 路径 + 本评审文档 1 路径） |
| Recovery tag | `recovery/C102-beta-merge-review-dev3-20261023/20261023182242-db553538b289`（`finish` 创建并已单独推送） |
| 推送 | `git push origin dev3`（`3b02e02c07..db553538b2`，PUSH_EXIT=0）；recovery tag 单独 `git push origin <tag>`（`[new tag]`，TAGPUSH_EXIT=0） |
| PR | **新建** [PR #485](https://github.com/Silent1566/webhtv/pull/485) dev3 → beta（本轮开始时 dev3 无开放 PR，历史 dev3→beta PR #482/#480/#477/#475… 均已合并；同 head/base 无法再建第二个）：中文标题与描述，描述含具体改动内容 = 「合并 `origin/beta` `d9da14dcb7`（11 提交，PR #484 线路：第 17 槽播放菜单遮盖层主题、E-SP12 掉帧归一、C101 评审记录）+ 评审并修复未推送提交 `7067d62b7b` 的焦点/选中线索合并缺陷（F1）」；**只创建，不合并** |
| 拉取 | `git fetch origin --prune --tags` + `git pull --ff-only origin dev3`（`Already up to date`，见 §9） |
| 交付时工作树 | 跟踪文件干净（仅剩受保护既有脏路径 `%SystemDrive%/`、`.design-evidence/` 保持不动、不入库） |

## 9. 拉取与文档收尾提交

| 项 | 值 |
| --- | --- |
| 拉取命令 | `git fetch origin --prune --tags`（FETCH_EXIT=0，新增 3 个历史 recovery tag）+ `git pull --ff-only origin dev3`（PULL_EXIT=0，`Already up to date.`） |
| 拉取后状态 | `git rev-list --left-right --count origin/dev3...HEAD` = `0 0`（本地与远端 dev3 完全一致）；HEAD 仍为 `db553538b2896a0177ee6c2f6d0f138b9ccf7666`；`origin/beta` 仍为 `d9da14dcb7`（本轮拉取时 beta 未前进） |
| 文档收尾提交 | 上面这一节把 §8 的占位符换成实测坐标，属于**提交后才获得的交付 ID 的纯文档回填**，按 AGENTS.md §6「不可避免的 post-commit ID/tag 只能再做一个仅文档的收尾提交」处理：一次仅文档提交（不触发任何构建/测试/研究），随后推送。评审对象与已验证代码在 `db553538b2` 中已冻结不变 |
| 收尾提交的坐标 | 见本提交自身的 `git log -1`（提交信息 `docs(C102): 回填交付坐标（提交 db553538b2 / tag / PR #485）与拉取结果`） |
