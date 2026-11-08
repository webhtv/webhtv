# C103-beta-merge-review-dev4-20261024

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev4`（本轮实测落后 6 个提交，冲突预检 0 冲突），评审 `dev4` 已提交未推送的
  两个修复提交（`96891188510` 固定浅底前景资源化、`b829d104ef4` 退出确认次要按钮对比度）以及合并带入的 `beta` 内容，
  修复必修项并验证，修复后再次评审直至通过，然后提交、推送 `dev4`、创建到 `beta` 的中文 PR（只创建，不合并），
  最后拉取远端最新代码。
- **验收标准**：
  1. `dev4` 相对 `origin/beta` 落后 0 个提交（`origin/beta` 成为 HEAD 祖先），合并没有丢失 `beta` 侧任何路径、
     也没有夹带评审外的改动。
  2. 评审覆盖两个未推送修复提交的 23 个路径（19 + 4），以及合并带入的 5 个路径 / +771 −0；每个结论都有可复核证据；
     上一轮 `C102`/`C101` 已评审且合并未改写其内容的路径按用户约定跳过，并写明跳过依据。
  3. 每个必修项均已修复；修复有回归用例并经负向对照证明其真实有效；修复后复评通过。
  4. 定向单测与合并后回归套件双 flavour 全绿（theme 51 类 / 380 例、ui.dialog 59 类 / 279 例，0 失败 0 错误 0 跳过）、
     `scripts/check_ui_tokens.py` PASS、`git diff --check` 与 `git diff --cached --check` 为空、无冲突标记。
  5. 本轮改动经任务守卫 `finish` 落盘并创建 recovery tag；推送 `dev4`；创建到 `beta` 的中文 PR（含具体改动内容）；
     随后拉取远端最新代码。
- **Lane**：`standard`（`app/src` + `docs` + `scripts`）
- **Scope**：`app/src`、`docs`、`scripts`
- **任务守卫**：`C103-BETA-MERGE-DEV4-20261024`，base HEAD `62f0b777d2a11fcd0891cb67474221956cb05c18`，
  branch `dev4`，受保护既有脏路径 0 条（任务开始时工作树干净）
- **当前状态**：合并、评审、F1 修复、负向对照、复评、验证均完成；提交/推送/PR/拉取见 §8
- **下一动作**：无；等待 PR 评审/合并（本轮只创建 PR，不合并）

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `7c725e4d7ee43ca0a187e420b224b33bb0a39a6a`（"Merge pull request #485 from Silent1566/dev3"，2026-10-24 08:55:38 +0800） |
| 合并前本地 | `dev4` = `96891188510efa26803b290e1dc1192480ab2560`（"fix(tv): pin palette-independent foregrounds on fixed light surfaces"，2026-10-24 16:14:07 +0800） |
| 祖先关系（合并前） | `git merge-base --is-ancestor origin/beta HEAD` → **false**（落后 6 个提交） |
| 落后 / 领先（合并前） | `git rev-list --left-right --count origin/beta...HEAD` = **6 / 2** |
| merge-base | `e8551e321cef5d7d8c064c0b0cb0658956871e5a`（"test(py): guard the dual-channel spider bridge…"，PR #486 线路） |
| `beta` 独有提交（6） | `7c725e4d7ee`（PR #485 合并）、`1d80913f77d`（PR #486 合并）、`17bd235c248`（C102 评审文档回填）、`db553538b28`（dev3 合并 beta + C102 评审）、`7067d62b7b5`（版本卡片调色板）、`d9da14dcb77`（PR #484 合并） |
| 已在本地（不重复计入） | `e8551e321ce`、`762f5ae6ae8`、`8ef4dbf34fd`、`35516a58ffc` 在 `96891188510` 中**已是祖先**（`git merge-base --is-ancestor` = 0） |
| 冲突预检 | `git merge-tree --write-tree --name-only dev4 origin/beta` → **rc=0**，无冲突文件段 |
| 合并动作 | `git merge --no-ff origin/beta` → 无冲突、0 手工解冲突 → 合并提交 `62f0b777d2a11fcd0891cb67474221956cb05c18`（双亲 `96891188510` + `7c725e4d7ee`） |
| 祖先关系（合并后） | `git merge-base --is-ancestor origin/beta HEAD` → **true**；`git rev-list --left-right --count origin/beta...HEAD` = **0 / 3** |
| 合并带入路径 | `git diff --stat 96891188510 62f0b777d2a` = **5 files changed, 771 insertions(+)**（M leanback `UpdateDialog.java` +18、A `UpdateVersionRowTheme.java` +279、M mobile `UpdateDialog.java` +25、A `UpdateVersionRowThemeTest.java` +240、A `docs/C102-beta-merge-review-dev3-20261023.md` +209） |
| 合并纯净性（正向） | `git diff --name-status 96891188510 62f0b777d2a` 恰为上述 5 条（1 M + 4 A），无多余路径 |
| 合并纯净性（反向） | `git diff --name-status 62f0b777d2a origin/beta` = 22 条（18 M + 4 D）—— 4 个 D 正是 dev4 自己尚未推送到 beta 的两个修复提交（`fixed_light_focus_icon.xml`、`fixed_light_focus_text.xml`、`TvFixedLightSurfaceContrastTest.java`、两份修复文档），即 `beta` 一个路径都没丢 |
| 合并是否触碰已评审路径 | **否**。合并带入的 5 个路径全部来自 C102 已评审的 `beta` 侧改动（`7067d62b7b` 线路 + C102 文档），与 dev4 两个修复提交的 23 个路径**零交集** |

需评审的未推送提交（3 个，其中一个是 merge commit）：

| 提交 | 说明 | 是否本轮新评审 |
| --- | --- | --- |
| `96891188510` | fix(tv) 固定浅底前景资源化（19 路径 / +406 −129） | **是**（dev4 线独有；仓库内无该提交的评审记录） |
| `b829d104ef4` | fix(tv) 退出确认次要按钮对比度（4 路径 / +580 −4） | **是**（同上） |
| `62f0b777d2a` | merge：合入 `origin/beta`（5 路径 / +771 −0） | 是（但内容即 C102 已评审的 `beta` 侧代码，见 §2） |

结论：`beta` 侧 6 个提交全部进入 `dev4`；需要新评审的是 `96891188510` + `b829d104ef4` 的 23 个路径，
加上对合并带入 5 路径的「未被改写」核对。

## 2. 评审范围与跳过依据

**本轮全量评审对象**：`96891188510`（19 路径 / +406 −129）与 `b829d104ef4`（4 路径 / +580 −4），合计 23 个路径。
`git rev-list --count origin/dev4..HEAD` = 9（远端 dev4 已含 `beta` 侧 PY-BRIDGE 提交，故三个提交即全部未推送内容）。

| 路径 | 提交 | 评审结论 |
| --- | --- | --- |
| `app/src/main/res/color/fixed_light_focus_text.xml`（新增） | `96891188510` | 全文读完。selector：focused #174EA6 / pressed #174EA6 / 默认 #202124，两份色表取值完全相同 → 前景恒定，正是「固定浅底必须配固定前景」的正确做法。注释列出固定浅底族清单与守卫类名 → §4-1 |
| `app/src/main/res/color/fixed_light_focus_icon.xml`（新增） | `96891188510` | 同上（同值，供 `tint` 使用）；额外注释说明夜间 #C4C6D0 压在 #D2E3FC 上仅 1.31:1 → §4-1 |
| `app/src/main/res/values/colors.xml` | `96891188510` | 新增 `fixed_light_band` #E5E7EB、`fixed_light_text_muted` #44474F、`fixed_light_accent` #0B57D0、`fixed_light_success` #146C2E、`fixed_light_error` #B3261E，注释给出取值来源与最低对比度（11.73 / 6.77 / 4.65 / 4.76 / 5.13）；无 `values-night` 覆盖 → 两表恒定 → §4-2 |
| `app/src/leanback/res/layout/dialog_ad_block_stats.xml` | `96891188510` | 86 行改动：日志页列头带 `webhtv_color_scrim` → `fixed_light_band`；12 个列头文字与 8 个筛选按钮改固定前景 + 固定描边 → §4-3 |
| `app/src/mobile/res/layout/dialog_ad_block_stats.xml` | `96891188510`（**未包含**） | **F1 必修项**：同族缺陷在 mobile 版被漏掉，见 §3-F1 / §5 |
| `app/src/leanback/res/layout/adapter_ad_block_log.xml` | `96891188510` | 12 处 `?attr/` 前景改 `fixed_light_focus_text`/`fixed_light_text_muted`；宿主为 `selector_ad_rule`（默认透明 → 实际压随表面板）与固定浅底卡头 → §4-4 |
| `app/src/leanback/res/layout/adapter_ad_rule.xml`、`adapter_ad_stats_item.xml` | `96891188510` | 各 2 / 6 行，均为把落在 #FAFBFC、#E8F0FE 等固定浅底上的 `?attr/` 前景换成 fixed 资源 → §4-4 |
| `app/src/leanback/res/layout/dialog_update.xml`、`app/src/main/res/layout/dialog_update_settings.xml` | `96891188510` | 各 2 行，`tint` 换 `fixed_light_focus_icon` → §4-5 |
| `app/src/main/res/layout/adapter_custom_csp.xml`、`adapter_mpv_config_profile.xml`、`dialog_ai_recommendation_info.xml`、`dialog_custom_csp.xml`、`dialog_mpv_config_create/manager/rename.xml` | `96891188510` | 逐条核对宿主填充是否为固定浅底，全部成立 → §4-5 |
| `app/src/mobile/res/layout/adapter_github_proxy.xml` | `96891188510` | 4 行：`:23` 文字、`:48` `tint` 改 fixed；`:34` 的 `?attr/colorOnSurfaceVariant` 父布局**无背景**，不构成固定浅底缺陷 → §4-6 |
| `app/src/test/java/com/fongmi/android/tv/theme/TvFixedLightSurfaceContrastTest.java` | `96891188510` + `b829d104ef4` + 本轮 | 守卫本体：解析器升级（`@color/` 状态选择器、`textColorHint`、`tint`、`tabTextColor`/`tabSelectedTextColor`、扫描 `src/mobile`）、`EXEMPT_LAYOUTS` 清空、`surfaceFills` 支持 `@color/` 带；本轮再加 2 处 → §4-7 / §3-F1 |
| `app/src/leanback/res/layout/dialog_exit_confirm.xml`、`app/src/leanback/res/drawable/selector_exit_confirm_secondary.xml`、`app/src/leanback/res/color/exit_confirm_secondary_text.xml` | `b829d104ef4` | 退出确认两个次要按钮（negative / backgroundPlay）由 `?attr/colorOnSurfaceVariant` 改为同族固定深色；selector 注释同步重写守住「不得改回调色板角色」→ §4-8 |
| `docs/TV-LIGHT-FILL-CONTRAST-ALL-02-tv-fixed-light-surface-foregrounds.md` | `96891188510` + 本轮 | 任务文档，本轮补第四轮（mobile 补修）整节 → §4-9 |
| `docs/TV-EXIT-CANCEL-CONTRAST-01-tv-exit-cancel-contrast.md` | `b829d104ef4` | 任务文档，含真机夜间实测对比度表；本轮无新发现 → §4-9 |

**跳过依据**（用户约定：之前已评审且无合入冲突、且评审已覆盖当前全部修改才可跳过）：

1. **合并带入的 5 个路径**：全部是 `beta` 侧 C102 已评审内容（`7067d62b7b` 线路的版本卡片调色板修复 +
   `UpdateVersionRowTheme` 与其测试 + C102 评审文档）。本轮已复核 **C102 的 F1 修复仍在**：
   `app/src/main/java/com/fongmi/android/tv/theme/UpdateVersionRowTheme.java:129` `focusFill()` 返回
   `safe(tokens).colorPrimary()`、`:139` `focusInk` → `readableOn(safe.colorOnPrimary(), safe.colorPrimary())`、
   `:196-208` `background()` 三态互换、`:222-224` `focusRingWidth()` 走 `R.dimen.webhtv_focus_ring_width`；
   `UpdateVersionRowThemeTest.java` 的 `:96`/`:122` 两个回归用例仍在 → 该评审结论对当前代码仍然成立。
2. **已在 `96891188510` 中的 `beta` 侧提交**（`e8551e321ce`/`762f5ae6ae8`/`8ef4dbf34fd`/`35516a58ffc`）：
   这些内容在本地早已存在，不属于本轮新代码；本轮不做第二次全量重读（AGENTS.md §3「不要重复已完成的仓库级搜索」）。
3. **本轮没有跳过任何 dev4 线自有改动**：两个修复提交的 23 个路径全部逐条读过，见上表与 §4。

## 3. 评审发现与处置

| 编号 | 严重度 | 位置 | 问题 | 处置 |
| --- | --- | --- | --- | --- |
| F1 | **高（必修）** | `app/src/mobile/res/layout/dialog_ad_block_stats.xml` 的日志页 88dp 横带（`:336-742`）及其 12 个列头 / 8 个筛选按钮 | `96891188510` 修好了 leanback 版同一处带，但 **mobile 版整个文件没进该提交**，于是保留了同族缺陷：带宽 `android:background="@color/webhtv_color_scrim"`（日 #52000000 / 夜 #8A000000，两表不同），带内 12 个列头用 `@color/dialog_outlined_button_text`、8 个筛选按钮沿用 `style="@style/Widget.WebHTV.Button.Outlined"`（`app/src/main/res/values/webhtv_styles.xml:200-203` = `textColor ?attr/colorPrimary` + `strokeColor ?attr/colorOutline`），全部是**调色板角色**。scrim 压在随表面板 `shape_shell_proxy_dialog` 上把带压成中灰：日间 #52000000 over #E7E8EF ≈ #9D9DA2、夜间 #8A000000 over #2A2F34 ≈ #131618；夜间 #A8C7FA 尚可，但**日间 #0B57D0 on #9D9DA2 只有 2.37:1**（远低于 4.5）。更关键：守卫**抓不到它** —— `TvFixedLightSurfaceContrastTest.surfaceFills()` 的 `@color/` 分支要求填充是「两表恒定的字面量」(`fixedLiteral`) 且 alpha ≥ 0x66，而 `webhtv_color_scrim` 两表取值不同 → 返回 `null` → 整条带被静默跳过，即「测试全绿但缺陷仍在」 | **已修**：① 带宽改 `@color/fixed_light_band`（#E5E7EB，两表恒定）；② 12 个列头 `textColor` 改 `@color/fixed_light_focus_text`；③ 8 个筛选按钮各补 `android:textColor="@color/fixed_light_focus_text"` + `app:strokeColor="@color/fixed_light_text_muted"`，与 leanback 版逐行同形。修复后日间 #202124 on #E5E7EB = **13.00:1**，焦点态 #174EA6 on #E5E7EB = **7.51:1**。**不改**的项与理由：`:143`/`:799` 两条 1dp 分隔线仍是 `webhtv_color_scrim`（纯装饰、无可读前景）；`:14` 标题与 `:29` `app:tabTextColor` 压在随表的 `shape_shell_proxy_dialog` 上（正确配对）；4 个 `AppCompatEditText`（`:610/:642/:674/:706`）自身背景 `selector_dialog_input` 的两层（focused/default solid = `?attr/colorSurfaceContainerHighest`）都随表 → 前景随表是正确配对；`:807` 起底部按钮同理 |
| F1-守卫 | **中（必修，随 F1）** | `app/src/test/java/com/fongmi/android/tv/theme/TvFixedLightSurfaceContrastTest.java` | F1 之所以没被抓住，根因是守卫**缺少对「`@color/` 固定带」这条路径的锁**：`surfaceFills()` 的 `@color/` 分支（`:293-297`）虽已支持，但没有正例钉住它；一旦有人为消假阳性而把该分支整段排除，同类缺陷会再次静默通过 | **已修**：① `fixedLightPanelsKeepPaletteIndependentForegrounds()` 的期望表新增 `{"src/mobile/res/layout/dialog_ad_block_stats.xml", "@color/fixed_light_band", "@color/fixed_light_focus_text"}`（`:468`，期望数 17 → 18）；② `theGuardDetectsTheDefectItWasWrittenFor()` 新增 `bandDriven` 探针（`:430-435`）：`<LinearLayout android:background="@color/fixed_light_band"><TextView android:textColor="@color/dialog_outlined_button_text" /></LinearLayout>`，断言 `inspect()` 非空且 `worst < MIN_CONTRAST`，把 `@color/` 分支钉死。负向对照见 §5 |
| F2 | 信息（不改） | `app/src/main/res/layout/dialog_ai_recommendation_info.xml:178`（`tmdbRating`）与 `:316`（`reasonLabel`） | 两处仍用 `@color/webhtv_color_warning`（日 #7A5900 / 夜 #F2C66D），看起来与「固定浅底必须固定前景」同族。实测：`:178` 落在 `shape_tmdb_rating_chip_light`（solid = `@color/webhtv_color_warning_container`，日 #FFDF9A / 夜 #5C4200 + 描边 `@color/webhtv_color_warning`）；`:316` 落在 `MaterialCardView` 的 `app:cardBackgroundColor="@color/webhtv_color_warning_container"` 上。**填充与前景都是成对的调色板角色**（`warning` on `warning_container`）：日间 5.00:1、夜间 5.85:1 / 7.28:1，**不是**「角色前景压固定浅底」 | 不改：`warning_container` 在 `values-night` 有独立取值（#5C4200），属随表配对，改动会破坏夜间配色 |
| F3 | 信息（不改，已由 F1 覆盖） | `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/AdBlockStatsDialog.java:150-160` | `updateCategoricalButton()` 只 `setText` + `setEnabled(!currentLogs.isEmpty())`，**没有**任何 `setTextColor`/`setStrokeColor` 运行时覆盖；因此按钮 `disabled` 态会走 MaterialButton 自带行为，可能落在 `fixed_light_band` 带上 | 不改：静态 XML 已给 `textColor` 覆盖了 style 的 `?attr/colorPrimary`，禁用态与 leanback 版行为完全一致（同一套 style、同样无运行时覆盖）；扩到禁用态配色属新的 UI 变更，超出本任务范围（AGENTS.md §2）。记录备查 |

**本轮必修项：2 个（F1 + F1-守卫，同一根因），已修复并复评通过。**

## 4. 复核未发现问题的项（正面证据，非「没看」）

1. **固定前景资源本身正确**：`app/src/main/res/color/fixed_light_focus_text.xml` 与 `fixed_light_focus_icon.xml`
   在 `values`/`values-night` 下**取值完全相同**（focused #174EA6 / pressed #174EA6 / 默认 #202124），
   且 `colors.xml` 里 5 个 `fixed_light_*` 都只在 `values` 定义、无 `values-night` 覆盖 → 天然两表恒定。
   重算关键配对：#202124 on #E5E7EB = 13.00、#174EA6 on #E5E7EB = 7.51、#202124 on #FAFBFC = 15.54、
   #44474F on #FAFBFC = 8.97、#0B57D0 on #E8F0FE = 5.57、#146C2E on #EAF6EE = 5.88 —— 全部 ≥ 4.5。
2. **`fixed_light_band` 是唯一正确解**：带宽原本是 `@color/webhtv_color_scrim`（日 #52000000 / 夜 #8A000000），
   而 `scrim` 的语义是「在面板上压一层半透明黑」，合成结果取决于宿主面板色 → 永远不可能有恒定对比度。
   要嘛前景随表、要嘛填充固定；该带内的文字与按钮必须与 leanback 版一致（leanback 已改固定），
   因此填充改固定是唯一能同时满足「带外观统一」与「前景恒定」的方案。已核实 `shape_shell_proxy_dialog` 的取值
   （`?attr/colorSurfaceContainerHigh`：日 #E7E8EF / 夜 #2A2F34）→ 中灰计算成立。
3. **leanback 版 `dialog_ad_block_stats.xml` 逐行核对无遗漏**：852 行、`fixed_light_band` 2 处（`:345` 横带、
   `:821` 底部 1dp 线）、`fixed_light_focus_text` 28、`fixed_light_text_muted` 20；残留的 5 处
   `dialog_outlined_button_text` 全在随表宿主上（`:14` 标题、4 个 `AppCompatEditText` 的 `:642/:674/:706/:738`），
   `:149` 概览页 1dp 线仍 `webhtv_color_scrim`（装饰线，无前景）。改后 mobile 版计数：
   830 行、`fixed_light_band` 1、`fixed_light_focus_text` 20、`fixed_light_text_muted` 8、
   `dialog_outlined_button_text` 14（全部在随表宿主或 4 个 EditText 上）、`scrim` 2（`:143`/`:799` 装饰线）。
4. **`adapter_ad_block_log.xml` / `adapter_ad_rule.xml` / `adapter_ad_stats_item.xml` 的宿主填充逐条核对**：
   `app/src/leanback/res/drawable/shape_ad_stats_content.xml` solid #FAFBFC、
   `shape_ad_stats_tabs.xml` solid #F4F7FB + stroke #E2E8F0、
   `selector_ad_stats_item.xml` focused #E8F0FE / pressed #D2E3FC / 默认 #00FFFFFF + stroke #E5E7EB、
   `selector_ad_rule.xml` 默认 transparent → 实际压在随表面板上（该文件里保留 `?attr/` 的位置因此是正确的）→
   改成 `fixed_light_*` 的位置全部落在固定浅底上，没有一处改错，也没有一处漏改。
5. **main 源集 9 个布局的宿主填充逐条核对**（用 `selector_mpv_profile_card.xml` selected #F4F8FF / focused #F1F6FF /
   pressed #E8F0FE / 默认 #FAFBFC、`shape_mpv_active_badge.xml` #E8F0FE、
   `selector_mpv_icon_button.xml` focused #E8F0FE / pressed #D2E3FC / 默认 transparent、
   `shape_recommendation_source_chip.xml` #E8F0FE + stroke #D2E3FC、`shape_douban_rating_chip_light.xml` #EAF6EE 验证）：
   `adapter_mpv_config_profile.xml` 全文 86 行读完，改后 `:26/:41/:52/:68/:83` 全为 `fixed_light_*`，无残留调色板角色；
   `adapter_custom_csp.xml` 3 处 `app:tint` → `fixed_light_focus_icon`；
   `dialog_mpv_config_create/manager/rename.xml` 各 1 处、`dialog_update_settings.xml` 1 处同理 → 全部正确。
6. **`adapter_github_proxy.xml` 的分歧点已判定**：`:23` `textColor` 落在 `@drawable/selector_custom_csp_item`
   （#FFFFFF / #E8F0FE / activated #E8F5E9 / 默认 #F3F5F7，全部固定浅底）→ 必须固定，已改；
   `:48` `app:tint` 落在 `@drawable/selector_dialog_switch`（focused/pressed #E8F0FE）→ 已改；
   而 `:34` 的 `?attr/colorOnSurfaceVariant`（延迟文字）父布局**没有任何背景**，实际压在随表面板上 → 保持随表是对的。
7. **守卫的新扫描面真实生效**：`TvFixedLightSurfaceContrastTest` 现在扫 `src/leanback/res/layout`、
   `src/main/res/layout`、`src/mobile/res/layout` 三处全部 xml（`EXEMPT_LAYOUTS` 清空），
   且 `FOREGROUND` 正则覆盖 `textColor`/`textColorHint`/`tint`/`app:tint`/`tabTextColor`/`tabSelectedTextColor`。
   本轮另用**独立写的 Node 解析器**做了第二次全库复算（不复用被测代码）：day/night 色表取自
   `src/main|leanback|mobile` 的 `values{,-night}/colors.xml` + `webhtv_tokens.xml`（128 个色名）、
   `@color/` 状态选择器取自三源集 `res/color/*.xml`；全库命中仅 2 条且均为**假阳性**：
   `src/{leanback,mobile}/res/layout/dialog_ad_block_stats.xml` 里 4 个 `AppCompatEditText`
   自身带 `background=@drawable/selector_dialog_input`（随表），简版扫描把外层 `fixed_light_band` 误当成表面
   → 1.39:1 为误报。**除 F1 外没有第二个同类缺陷。**
8. **`b829d104ef4`（退出确认）复核通过**：`app/src/leanback/res/color/exit_confirm_secondary_text.xml` 的 selector
   （focused #174EA6 / pressed #174EA6 / 默认 #202124）两表恒定；`dialog_exit_confirm.xml` 只有 leanback 一份，
   `textColor` 分布 `:28` `?attr/colorPrimary`、`:45` `?attr/colorOnSurface`、`:57` `?attr/colorOnSurfaceVariant`
   （均在随表容器 `shape_exit_confirm_dialog` / `?attr/colorSurfaceContainer` 上，正确）、`:93`/`:112`
   已改 `@color/exit_confirm_secondary_text`、`:128` `?attr/webhtvColorOnWallpaper`（壁纸层，正确）。
   `selector_exit_confirm_secondary.xml` 的 focused/pressed solid #DCEAFF、默认 solid #F8FAFD —— 都是固定浅底，
   故前景必须固定，改动成立；该 selector 注释已重写为「文字必须用同族固定深色，不得改用 `?attr/colorOnSurfaceVariant`」，
   把约束留在了资源旁。
9. **两份任务文档已同步**：`docs/TV-LIGHT-FILL-CONTRAST-ALL-02-…md` 新增「第四轮（C103 合并 beta 后的复审补修）」
   整节，写明改前行号、守卫为什么没抓（`surfaceFills` 需 `fixedLiteral`）、21 处补修清单、仍然不改的项与理由、
   无 Java 引用（仅 `AdBlockStatsDialogLayoutTest` 以文本方式读取该布局），并在验证节补本轮证据；
   `docs/TV-EXIT-CANCEL-CONTRAST-01-…md` 内容未改（本轮无新发现）。
10. **无运行时旁路**：`grep R.layout.dialog_ad_block_stats` 全仓无 Java 引用；mobile 版仅被
    `app/src/testMobile/java/com/fongmi/android/tv/ui/dialog/AdBlockStatsDialogLayoutTest.java` 以**文件文本**方式断言
    （13 个用例，含「必须 1660dp」「不得有 MaterialCardView」「不得出现 `@color/white` / `white_50`」），
    与本轮改动的属性不冲突；实测该 13 个用例全绿（§6）。
11. **差异卫生与门禁**：`git diff --check` / `git diff --cached --check` 均为空；冲突标记扫描仅命中
    `app/src/main/assets/VodPlus/examples/*.txt` 的数据分隔线（非冲突段）；
    `scripts/check_ui_tokens.py` = `UI_TOKEN_STATUS PASS`（`CONTRAST pairs=38 failures=0 min=4.28`），
    新代码未引入裸 hex（`hex_layouts=0`）。

## 5. 复核

- **F1 复评对照**：修复后 mobile 版与 leanback 版**逐行同形**（带底 → `fixed_light_band`；12 个列头与 8 个按钮
  → `fixed_light_focus_text`；8 个按钮 `strokeColor` → `fixed_light_text_muted`），日间 13.00:1 / 焦点 7.51:1
  均远超 4.5；五个「故意不改」点（装饰线、标题、TabLayout、4 个 EditText、底部按钮）逐条给出宿主填充证据（§3-F1）。
- **F1-守卫复评对照**：期望表 18 条（新增 mobile 那条）与 `bandDriven` 探针一起把「`@color/` 固定带 + 调色板前景」这条路径钉死，任何一方回退都会挂。
- **负向对照（证明新增期望真实有效、非空跑）**：把 `app/src/mobile/res/layout/dialog_ad_block_stats.xml` 用 `git checkout HEAD` 临时还原（`grep webhtv_color_scrim` = 3）后单跑 `:app:testMobileArm64_v8aDebugUnitTest --tests "*TvFixedLightSurfaceContrastTest*"` → `fixedLightPanelsKeepPaletteIndependentForegrounds FAILED` / `java.lang.AssertionError at TvFixedLightSurfaceContrastTest.java:485`（失败点是 `:485` 的 `source.contains(expectation[1])` —— 还原后文件不含 `@color/fixed_light_band`），`BUILD FAILED in 42s`；随后按备份逐字节复原本轮修复内容（`fixed_light_band` = 1）。即 §6 的全绿结果出自**还原后**的内容，且新期望确实能抓住修复前状态。
- **未回退核对**：`96891188510` 与 `b829d104ef4` 是 dev4 线对这两个功能的首次评审，无前序修复需要核对；合并未触碰这两个提交的 23 个路径（§1 末行），也未改写 C102 已评审路径的语义（§2 跳过依据第 1 条已复核 F1 修复仍在）。
- **复评涉及面**：23 个净差异路径 + 合并带入 5 路径的「未被改写」证明 + 双 flavour 回归套件（theme 51 类 / 380 例、ui.dialog 59 类 / 279 例）+ 独立全库复算。
- **结论：复评通过。** F1 / F1-守卫 已闭环，F2 / F3 为记录项不改。

## 6. 验证（2026-10-24 17:0x–19:2x +08:00）

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 定向组合单测（双 flavour） | `gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "*TvFixedLightSurfaceContrastTest*" --tests "*UpdateVersionRowThemeTest*" :app:testMobileArm64_v8aDebugUnitTest --tests "*TvFixedLightSurfaceContrastTest*" --tests "*UpdateVersionRowThemeTest*" --tests "*AdBlockStatsDialogLayoutTest*"` | **BUILD SUCCESSFUL in 3m 42s**（130 tasks，15 executed / 7 from cache / 108 up-to-date） |
| 定向单测明细 | 读 `app/build/test-results/*/TEST-*.xml` | leanback：`TvFixedLightSurfaceContrastTest` **4 例**、`UpdateVersionRowThemeTest` **10 例**；mobile：`TvFixedLightSurfaceContrastTest` **4 例**、`UpdateVersionRowThemeTest` **10 例**、`AdBlockStatsDialogLayoutTest` **13 例**；全部 **0 失败 0 错误 0 跳过** |
| 合并后 theme 包回归（双 flavour） | `gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*" :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*"` | **BUILD SUCCESSFUL in 2m 6s**，exit 0；leanback **28 类 / 196 例**、mobile **23 类 / 184 例**，合计 **51 类 / 380 例 / 0 失败 0 错误 0 跳过** |
| 合并后 ui.dialog 包回归（双 flavour） | `gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.dialog.*" :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.dialog.*"` | **BUILD SUCCESSFUL in 5m 6s**，exit 0；leanback **17 类 / 81 例**、mobile **42 类 / 198 例**，合计 **59 类 / 279 例 / 0 失败 0 错误 0 跳过** |
| 负向对照 | 临时 `git checkout HEAD` 还原 mobile 布局后单跑 mobile 守卫 | `fixedLightPanelsKeepPaletteIndependentForegrounds FAILED` / `TvFixedLightSurfaceContrastTest.java:485` / `BUILD FAILED in 42s`（预期失败，证明新期望非空跑；随后逐字节还原） |
| 独立全库复算 | 自写 Node 解析器（day/night 色表 + `@color/` selector，不复用被测代码） | 全库仅 2 条命中且均为已知假阳性（4 个随表 `AppCompatEditText`），**无第二个同类缺陷** |
| 差异卫生 | `git diff --check`、`git diff --cached --check` | 均 rc=0、输出为空 |
| 冲突标记 | `git grep -nE '^(<{7}|={7}|>{7})' -- app docs` | 无冲突段（仅 assets 下 `.txt` 数据分隔线） |
| UI token 门禁 | `G:/Python310/python.exe scripts/check_ui_tokens.py` | `UI_TOKEN_STATUS PASS`（`layouts=385 hex_layouts=0 drawables=563 hex_drawables=1 colors=66 hex_colors=2 allowlisted=190`；`SCOPE stage=A violations=3 legacy=0`；`CONTRAST pairs=38 failures=0 min=4.28`），exit 0 |


环境备注（不属本任务改动）：执行前需 `ANDROID_HOME=C:\Users\Maple\AppData\Local\Android\Sdk`（仓库无 `local.properties`）、
`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`、`GRADLE_USER_HOME=G:\GradleCache`；
DSH bash 工具无法写入 `.git`（`FETCH_HEAD`/索引写入报 Permission denied），故 fetch/merge/commit/push 与 `gradlew` 均由 pwsh 执行（必要时经 `G:\Git\bin\bash.exe -lc`）。

## 7. 回滚

- **本轮评审记录**：删除 `docs/C103-beta-merge-review-dev4-20261024.md`（无生产代码影响）。
- **F1 修复**（紧急回退）：`git checkout 62f0b777d2a -- app/src/mobile/res/layout/dialog_ad_block_stats.xml app/src/test/java/com/fongmi/android/tv/theme/TvFixedLightSurfaceContrastTest.java` —— 回到 mobile 带仍是 `webhtv_color_scrim`、守卫无 mobile 期望的状态。**不推荐**：会同时恢复日间 2.37:1 的不可读带与守卫盲区。
- **合并本身**：`git revert -m 1 62f0b777d2a` 可整体撤回 `beta` 合入（会连带撤回版本卡片调色板修复与 C102 评审文档）；如需单独回退某一功能，可针对对应提交 `git revert`。
- 无偏好写入、无数据库/协议/二进制/schema/依赖变更；资源未删除，回滚不需要恢复资源。

## 8. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `62f0b777d2a11fcd0891cb67474221956cb05c18`（双亲 `96891188510` + `7c725e4d7ee`，5 路径 / +771 −0） |
| 评审提交 | `9b0a547eae202bb24ade60cfabc820c2fc500506`（4 路径 / +293 −17：F1 修复 `app/src/mobile/res/layout/dialog_ad_block_stats.xml`、F1-守卫 `app/src/test/java/com/fongmi/android/tv/theme/TvFixedLightSurfaceContrastTest.java`、本评审文档、任务文档更新） |
| Recovery tag | `recovery/C103-BETA-MERGE-DEV4-20261024/20261024113249-9b0a547eae20`（守卫 `finish` 创建并已单独推送） |
| 推送 | `git push origin dev4`（`e8551e321ce..9b0a547eae2`，PUSH_EXIT=0）；recovery tag 单独 `git push origin <tag>`（`[new tag]`，TAGPUSH_EXIT=0） |
| PR | **新建** [PR #488](https://github.com/Silent1566/webhtv/pull/488) dev4 → beta：中文标题与描述，描述含具体改动内容 = 「合并 `origin/beta` `7c725e4d7ee`（6 提交，PR #485/#486 线路：版本卡片调色板修复与 PY-BRIDGE 守卫）+ 评审并修复未推送提交 `96891188510`/`b829d104ef4` 的 mobile 版固定浅底漏改（F1）与守卫盲区（F1-守卫）」；**只创建，不合并** |
| 拉取 | `git fetch origin --prune --tags`（FETCH_EXIT=0，新增 `v5.6.0-beta-202610240855` 与 1 个历史 recovery tag，dev1 前进）+ `git pull --ff-only origin dev4`（PULL_EXIT=0，`Already up to date.`）；见 §9 |
| 交付时工作树 | 跟踪文件干净（`git status --porcelain` 为空），`git rev-list --left-right --count origin/dev4...HEAD` = `0 0` |

## 9. 拉取与文档收尾提交

| 项 | 值 |
| --- | --- |
| 拉取命令 | `git fetch origin --prune --tags`（FETCH_EXIT=0）+ `git pull --ff-only origin dev4`（PULL_EXIT=0，`Already up to date.`） |
| 拉取后状态 | 拉取后 `git rev-list --left-right --count origin/dev4...HEAD` = `0 0`（本地与远端 dev4 完全一致）；HEAD 仍为 `9b0a547eae202bb24ade60cfabc820c2fc500506`；`origin/beta` 仍为 `7c725e4d7ee43ca0a187e420b224b33bb0a39a6a`（本轮拉取时 beta 未前进）；新增的 `v5.6.0-beta-202610240855` tag 与 `origin/dev1` 前进与本次评审无关，仅记录 |
| 文档收尾提交 | 本节把 §8 的占位符换成实测坐标，属于**提交后才获得的交付 ID 的纯文档回填**，按 AGENTS.md §6「不可避免的 post-commit ID/tag 只能再做一个仅文档的收尾提交」处理：一次仅文档提交（不触发任何构建/测试/研究），随后推送。评审对象与已验证代码在上面的评审提交中已冻结不变 |
