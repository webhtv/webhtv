# C58：dev1 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入 `dev1`（**远端已移除/回退的提交不得顺带带回**）；复评 dev1 全部已修改代码（含**已提交未推送**的 10 个 TV 焦点环主题提交）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev1`、创建 `dev1 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① `dev1` 必须包含合并时刻的 `origin/beta` tip；② 远端被移除/回退内容**零复活**、beta 增量**零丢失**、dev1 既有改动**零丢失**；③ 双 flavor Java 编译 + 双 flavor AndroidTest Java 编译通过；④ 双 flavor 全量 JVM 套件零失败且增量可解释；⑤ UI token 门禁相对基线零新增违规；⑥ 复评发现的问题已修复并双向负对照锁定（或按 `AGENTS.md` §2 明确记录处置）；⑦ 提交 + recovery tag、`dev1` 已推送、PR 已创建且**未合并**，PR 文件集与 `git diff --name-only origin/beta HEAD` 逐项一致。
- **当前状态**：合并完成（无冲突、内容零变化，`MERGE_HEAD` = beta tip）；3 轮复评完成；第 1 轮发现 **3 类真实缺陷**（F1 错别字「深箭」8 处、F2 注释与实现矛盾 2 处、F3 悬空 javadoc 1 处）并已修复；双 flavor 编译、双 flavor 全量 JVM 套件（leanback 4172/0、mobile 4987/0）、UI token 门禁全部通过；零复活/零丢失三层证据通过。
- **交付坐标**：见文末「交付坐标」与「闭环记录」。
- **下一动作**：提交 + recovery tag → 推送 `dev1` → 创建 PR 到 `beta`。

## 时间与设备

- 任务开始时本地时间：2026-10-11 10:52（Asia/Shanghai）；记录时 11:25。
- 任务开始时工作区**干净**（`git status --porcelain` 空），protected 脏路径 **0** 个。
- `dev1` HEAD = `649275b76279d7a96a17eb2d48d04578d1a2b34e`，领先 `origin/dev1`（`913ae81d19845e6205d0d10360acba3da08e934b`）**11 个提交**（1 个合并提交 + 10 个 TV 焦点环主题提交），全部属于**已提交未推送**，是本轮复评对象：
  - `d07a8454f` `fix(theme): 统一 TV 端应用表面焦点环并接线到主题色板`
  - `d8f958671` `fix(theme): 收口剩余 TV 焦点环宽度并修正选集弹窗不可见焦点`
  - `540d70b04` `fix(theme): 应用表面焦点环改回 1.5dp 细环并让卡片环圆角对齐卡片 8dp 圆角`
  - `b8581afc4` `fix(theme): 详情页/播放页焦点环圆角对齐宿主，并修正卡片集数名颜色`
  - `c3bb6a63d` `fix(theme): 播放页焦点/当前态环统一宽度并统一为受主题控制的派生色`
  - `7948c8e97` `fix(theme): 修复对话框主题缺 tv* 属性导致的黑色边框，并收口代码挂环的硬编码宽度`
  - `0209015d6` `fix(theme): 全屏播放不再失去焦点环，改为画面内侧内缩环`
  - `8ba33b11f` `revert: 全屏播放画面不加焦点环（用户纠正：控制栏需要，整个播放界面不需要）`
  - `6be5c5ee4` `feat(theme): 默认配色向上游对齐（TV 默认深色 + 白色焦点环 + 白色加载指示器）`
  - `649275b76` `fix(theme): 播放页只留一种高亮色（芯片宿主分层修正 + 深色表视频层环取近白）`
- 设备：**本轮未使用真机/模拟器**。理由（风险相符原则，`AGENTS.md` §4）：本轮**唯一的代码改动是注释文本与注释位置**（10 个文件、21 增 15 删、零逻辑改动，编译产物等价），不改变任何运行时行为；被复评的 10 个主题提交已由用户在同一机位（`192.168.50.3:5555`）逐轮实机验收（见 `docs/TV-FOCUS-RING-20261004-unified-theme-ring.md` §12–§16 的实机证据）。故未申请机位、未重装 APK。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1`（worktree `/home/maple/Workspace/webhtv/dev1/webhtv`） |
| 任务开始时 HEAD | `649275b76279d7a96a17eb2d48d04578d1a2b34e` |
| `origin/dev1` | `913ae81d19845e6205d0d10360acba3da08e934b` |
| `origin/beta` tip（`git ls-remote origin refs/heads/beta`） | `fb4fd7d99ca1547ea624f3d5f1b7f3cd81b880a5`（Merge pull request #433 from Silent1566/dev2） |
| 合并基点（merge-base） | `e6413efd1e4bdb3c1725824db6f7b3f8a7dc249c`（Merge pull request #431 from Silent1566/dev1） |
| 合并命令与结果 | `git merge --no-commit --no-ff origin/beta` → **「自动合并进展顺利，按要求在提交前停止」**（`EXIT=0`，**0 冲突**，`git diff --name-only --diff-filter=U` 为空）；自动合并 2 个文件（`TmdbDetailActivity.java`、`VideoActivityLayoutTest.java`） |
| `MERGE_HEAD` | `fb4fd7d99ca1547ea624f3d5f1b7f3cd81b880a5`（= beta tip，即合并提交第二父） |
| 合并结果树 `git write-tree` | `8c810dbf240569e26f23632b93abba61f0be326b` |
| 合并前 HEAD 树 | `199602c0d…`（与合并结果树**不同** → 本次合并确实引入了 beta 侧内容，必须产生真实合并提交） |
| beta tip 树 | `34a93e243…` 的树（见 `git rev-parse origin/beta^{tree}`） |
| 是否需要新合并提交 | **需要**：合并前 `git merge-base --is-ancestor origin/beta HEAD` 为**假**（`fb4fd7d99` 不在 dev1 上），故由 `task_guard.sh finish` 生成真实合并提交，第二父 = beta tip |
| 初始脏路径 | 无 |
| 回滚锚点 | `649275b76279d7a96a17eb2d48d04578d1a2b34e` |

### 合并增量 ledger（beta 侧 13 个提交，全部纳入）

`git log --format="%H|%s" e6413efd1..origin/beta`（本任务开始时）：

| 完整 commit ID | 标题 | 处置 |
| --- | --- | --- |
| `fb4fd7d99ca1547ea624f3d5f1b7f3cd81b880a5` | `Merge pull request #433 from Silent1566/dev2` | 纳入（合并第二父） |
| `34a93e243f660df19b2b23eef724f0f882002e8b` | `docs(c57): 记录 dev2 交付坐标…` | 纳入 |
| `df7f7ff5808d1e555abca8a5aa31104624650ffd` | `merge: 合并 origin/beta 最新代码（PR#427-#432…）并复评 dev2 加载圈族…` | 纳入 |
| `819003db8fef2ea63420e9a72eb4fcac8ee468ba` | `Merge pull request #432 from Silent1566/dev4` | 纳入 |
| `c8764048c5db27286709f81c9301d5d3497b41bb` | `docs(c57): 记录 dev4 合并评审交付坐标…` | 纳入 |
| `c442601fbd2d530829fddfbab371c11955cff1ff` | `feat(mobile): 影视原生播放器控制栏电池块补齐当前时间…` | 纳入 |
| `8db36f33a6298bbe2bced429ddcade9f2bfadf28` | `feat(mobile): 影视原生播放器控制栏新增电池电量图标…` | 纳入 |
| `b6b8e7a375276975cc160ceeb6257cf058298ebb` | `docs: 记录加载圈系列在 5557 的实机验证证据与未定证部分` | 纳入 |
| `839809ee1e0f56f2ea2b8537ddc11949b280e060` | `docs: 收口加载圈遗漏分支扫荡…` | 纳入 |
| `f2ccce36c9aba490f40fb3800638a14749dd0090` | `fix(osd): 屏显诊断重缓冲计数按引擎正确分派，IJK 不再恒为 0` | 纳入 |
| `d7b9439201cef70c9229b98c7356e0a7397103eb` | `fix(playback): 被作废的取流请求必须释放加载态…` | 纳入 |
| `7809ff4952788142008d0953697bfe29ded062b3` | `fix(detail): 作废内联取址请求时释放其加载标记…` | 纳入 |
| `4319403502a64ad8064781f4e88f21391e6abc77` | `fix(tv): 详情未就绪丢弃播放结果时释放加载守卫…` | 纳入 |

**beta 侧无任何删除/回退提交**：`git log --diff-filter=DR --name-status e6413efd1..origin/beta` 输出为**空**。

### 合并结果净差异（合并后相对 beta tip）

`git diff --name-status origin/beta 8c810dbf240569e26f23632b93abba61f0be326b` = **82 路径**（80 修改 + 1 新增 + 1 删除），全部来自 dev1 自身的 TV 焦点环主题改动：

| 类型 | 数量 | 说明 |
| --- | --- | --- |
| M | 80 | 主题系统 + leanback/main/mobile 资源 + 测试 + 文档 |
| A | 1 | `app/src/testLeanback/.../TvAppSurfaceFocusRingDeviceTest.java`（像素级回归锁） |
| D | 1 | `app/src/main/res/drawable/selector_cache_button_focus.xml`（被 dev1 新的主题环 selector 取代） |

**唯一删除是 dev1 自己的改动**（非 beta 内容丢失）：合并后全仓库 `git grep selector_cache_button_focus` **零命中**，无悬空引用；beta tip 对该文件的 12 处引用已被 dev1 在 `dialog_cache_management.xml`（`app:strokeColor="@color/focus_ring_secondary"` + `app:strokeWidth="@dimen/webhtv_focus_ring_width"`）与 `CacheManagementDialog.java` 中替换。

## 零复活三层证据（严格判据）

### 第 1 层：结构性

| 判据 | 结果 |
| --- | --- |
| `git merge-base --is-ancestor origin/beta HEAD`（合并前） | **假**（需真实合并） |
| 合并命令退出码 / 冲突数 | `0` / `0` |
| `MERGE_HEAD` | `fb4fd7d99ca1547ea624f3d5f1b7f3cd81b880a5` = beta tip |
| 净差异只含本分支路径 | 82 路径全部为 `app/src/**` 与 `docs/**`（dev1 主题改动） |

### 第 2 层：行级（唯一可信判据）

对 beta 历史上**单亲回退/剔除提交**取父，收集删除行，与 dev1 合并结果的新增行求交集，再逐行检查该行是否在 **beta tip 任何文件中都不存在**（只有这一层严格判据可信）。

| 回退提交（beta 祖先） | 完整 ID | 删除行数 | ∩ dev1 新增行 | 严格复活行数 |
| --- | --- | --- | --- | --- |
| `revert: remove dynamic theme color system (Task C/D/E)` | `be1b02e06b22a4fa2f08c791555536e3e6154c95` | 2388 | 31 | **0** |
| `剔除 PR #353 主题系统改动`（**不在 beta 历史**，仅在 `remove-pr353-theme-changes`） | `5682f2b05` | 2652 | 31 | **0** |

- **`be1b02e06` 是本轮最需要防范的回退点**（它删除了整套 `ThemeController`/`ThemeTokens`/`ThemeProfile`/… 主题系统）。核查发现：这些文件在 **beta tip 中已重新存在**（beta 在 revert 之后又恢复了主题系统），因此 dev1 合并结果中的同名文件**不是复活**，而是与 beta 一致的上游状态。
- 交集 31 行全部是通用脚手架行（`}`、`import`、`/**`、markdown 表格分隔、`@Test`），**每一行都在 beta tip 中存在**（`git grep -F` 逐个确认），严格复活行数 = **0**。
- `5682f2b05` 不在 `origin/beta` 也不在 `origin/dev1` 历史中（`git branch --contains` 仅 `remove-pr353-theme-changes`），**不构成本轮约束**；仍按其删除行做了同样的严格检查，复活行数 = **0**。

### 第 3 层：关键移除目标逐个核对

| 关键目标 | beta tip | 合并结果 | 判定 |
| --- | --- | --- | --- |
| `app/src/main/assets/themes/**`（主题目录） | 缺失（`be1b02e06` 已删且未恢复） | 缺失 | 未复活 ✅ |
| `app/src/mobile/.../ThemeEditorDialog.java` 等 mobile 主题对话框 | 缺失 | 缺失 | 未复活 ✅ |
| `app/src/testMobile/.../ThemeCatalogTest.java` 等 | 缺失 | 缺失 | 未复活 ✅ |
| `app/src/main/java/com/fongmi/android/tv/theme/ThemeController.java` 等 | 存在 | 存在（与 beta 内容一致处） | 非复活（beta 本身就有） ✅ |
| `app/src/main/res/drawable/selector_cache_button_focus.xml` | 存在 | 缺失 | dev1 自己的删除，非 beta 丢失 ✅ |

### 零丢失检查

`git diff --name-only e6413efd1 origin/beta` = **15 个文件**（beta 侧增量）。逐个比对 beta tip 与合并结果树：

| 结果 | 数量 | 文件 |
| --- | --- | --- |
| 完整保留 | 13 | beta 侧其余全部改动 |
| 被合并修改（语义合并，beta 内容保留 + dev1 改动共存） | 2 | `TmdbDetailActivity.java`、`VideoActivityLayoutTest.java` |

对那 2 个自动合并点逐 hunk 复核：`TmdbDetailActivity.java` 的差异**全部是 dev1 把 `FOCUS_STROKE_DP = 3` 硬编码替换为 `focusRingWidthPx()`**（beta 侧内容一行未丢）；`VideoActivityLayoutTest.java` 的差异是 dev1 更新的断言文本与期望字符串。**beta 增量零丢失** ✅

## 复评记录

### 第 1 轮：dev1 全部 82 路径净差异 + 11 个未推送提交

**评审范围**：`git diff --name-status origin/beta 8c810dbf2` 全集（生产代码 + 资源 + 测试有效性 + 任务文档与实现一致性 + 影响面）。

**逐模块复核结论**：

| 模块 | 复核要点 | 结论 |
| --- | --- | --- |
| `theme/ThemeTokens.java` | 新增 `colorPlayerFocusRing` / `colorPlayerCurrentRing` 两个 record 组件；`light()`/`dark()` 取值与 XML token 同步 | 接线正确；发现 F1、F3 相关问题 |
| `theme/ThemeResolver.java` | `PLAYER_RING_BACKDROPS`（3 档固定玻璃）+ `MIN_PLAYER_RING_CONTRAST = 3.0`；`readableAccent`/`clearsContrast` 重载保持旧调用点语义（默认 `MIN_ACCENT_CONTRAST`） | 逻辑正确；发现 F1、F2 |
| `theme/ThemePresets/Profile/Validator/Editor/PreviewView` | `playerCurrent` 槽位 6 层接线（预设/序列化/校验/编辑/预览）完整，无遗漏 case | 一致 ✅ |
| `theme/ThemeController.java` | TV 产品默认改深色（`Util.isLeanback() ? MODE_NIGHT_YES : FOLLOW_SYSTEM`），mobile 语义不变 | 与上游 TV 深色默认一致 ✅ |
| `ui/adapter/TmdbCardFocusHelper.java` | `foregroundBorder` 参数语义从 dp 改为 px；所有调用点同步（`TmdbEpisodeAdapter` 传 px `focusWidth` / `ResUtil.dp2px(...)`） | 无漏改 ✅ |
| `TmdbEpisodeAdapter` / `TmdbVideoAdapter` / `InlineEpisodeAdapter` | 焦点环宽度统一到 `@dimen/webhtv_focus_ring_width`，硬编码 `FOCUS_STROKE_DP` 已清除 | 一致 ✅ |
| `TmdbRecommendationPresenter.java` | `FOCUS_WIDTH_DP = 3` 改为 token + `ThemeController.focusRingColor()` | 正确 ✅ |
| `TmdbDetailActivity.java` | 11 处代码挂环改走 `focusRingWidthPx()` | 正确；发现 F3 |
| `leanback/styles.xml` | `ThemeOverlay.WebHTV.GlassFocusRings`（`parent=""`）在 `dialog_episode_list`/`dialog_quick_search` 子树重绑 `tvFocusRing`/`tvCurrentRing` 到视频层派生色 | 宿主分层实现正确 ✅ |
| 40+ 个 selector/shape drawable | 宽度一律 `@dimen/webhtv_focus_ring_width`；环色按宿主分族（应用表面 vs 视频层） | 一致；发现 F1 |
| 新增测试 `TvAppSurfaceFocusRingDeviceTest.java` | Robolectric **真实栅格化**到 Bitmap 后逐像素量描边厚度与颜色（非恒真算式）；覆盖两族同款、token 宽度、主题 FOCUS 槽、day/night 跟随 | 真实回归锁 ✅（非 skill 警告的恒真断言形态） |
| 任务文档 `TV-FOCUS-RING-*.md`（+826 行）与 `webhtv-unified-visual-design-system-*.md`（+31 行） | 与实现一致（宽度 1.5dp、豁免清单、宿主分层理由） | 一致 ✅ |
| 资源文件（`res/`、无 native、无锁文件、无播放器链路、无统计写入） | 影响面仅限 UI 主题与焦点环 | 无越界 ✅ |

**发现的真实缺陷**：

| 编号 | 缺陷 | 证据（负对照） | 位置 |
| --- | --- | --- | --- |
| **F1** | 错别字「深**箭**玻璃」应为「深**靛**玻璃」（靛=indigo，箭=arrow；同批次其它注释均用「深靛」） | `grep -c 深箭` 在 8 个生产文件各命中 1 处；`grep -rn 深靛` 在 13 个文件命中（含 `docs/TV-FOCUS-RING-*.md`、`shape_chip_focused.xml` 等） | `ThemeTokens.java:108`、`ThemeResolver.java:205`、`attrs.xml:13`、`webhtv_tokens.xml:48`、`colors.xml:56`、`values-night/webhtv_tokens.xml:51`、`leanback/styles.xml:13`、`selector_control_sheet_button.xml:12` |
| **F2** | 注释与实现**互相矛盾**：`attrs.xml` 与 `webhtv_attrs.xml` 声称环色是"经过**两类极端背景**（视频亮场景 + 固定玻璃）夹取"，但 `ThemeResolver.PLAYER_RING_BACKDROPS` **只有 3 档深玻璃、注释明确写"纯白视频（雪/白墙）刻意不在列表里"** | `grep "两类极端背景"` 命中 2 处；`grep "纯白视频（雪/白墙）刻意不在列表里" ThemeResolver.java` 命中 1 处（同一批改动内的自述冲突） | `attrs.xml:11-19`、`webhtv_attrs.xml:43` |
| **F3** | **悬空 javadoc**：原属于 `focusStroke()` 的文档被新方法 `focusRingWidthPx()` 插入后成为孤立注释，`focusStroke()` 丢失文档 | 结构扫描命中 `TmdbDetailActivity.java:2345`（唯一一处：`*/` 紧接新的 `/**`）；`sed -n '2343,2355p'` 显示旧注释悬在新方法之上 | `TmdbDetailActivity.java:2345` |

### 第 2 轮：修复后复审

**修复**（10 个文件、21 增 15 删、**零逻辑改动**）：

| 编号 | 修复 |
| --- | --- |
| F1 | 8 处「深箭」→「深靛」（与仓库既有 13 个文件的用词统一） |
| F2 | 改写 `attrs.xml` 与 `webhtv_attrs.xml` 的注释：明确夹取背景**只有三档固定玻璃**、纯白视频**刻意不在约束里**、并指向 `ThemeResolver.PLAYER_RING_BACKDROPS` 的取舍说明 |
| F3 | 把 `/** TV 焦点环的唯一代码来源… */` 移回 `focusStroke()` 正上方，新方法保留自己的文档 |

**复审逐项**：

| 复审问题 | 结论 |
| --- | --- |
| 修复是否真的消除缺陷？ | ✅ `grep -rn 深箭` = **0**；`grep "两类极端背景"` = **0**；悬空注释扫描 = **0**（`EpisodeDetailDialog.java:483` 的同类问题在 beta tip 完全相同，见 O2） |
| 是否与仓库既有约定一致？ | ✅ 术语统一为「深靛玻璃」，与 `docs/TV-FOCUS-RING-*.md`、`shape_chip_focused.xml`、`TvFocusRingContractTest` 等 13 个文件一致 |
| 是否改变正常行为？ | ✅ 零代码逻辑改动（仅注释文本 + 注释位置），编译产物等价；双 flavor 全量单测用例数与失败数**与修复前逐项一致** |
| 是否泄漏到无关模块？ | ✅ 10 个文件全部在本轮 82 路径净差异内；未触碰 `res/` 逻辑资源值、native、锁文件、播放器链路、统计写入、回退闸门 |

### 第 3 轮：收口复评

- 全量术语一致性扫描：`深靛` 13 个文件、`深箭` **0** 处。
- 修复后 `attrs.xml` 注释与 `ThemeResolver.PLAYER_RING_BACKDROPS` 实现**逐条对齐**（三档玻璃 + 排除纯白视频）。
- 未发现新的真实缺陷 → **通过**。

## 发现与处置汇总

| 编号 | 类型 | 处置 |
| --- | --- | --- |
| F1 | 真实缺陷（错别字 ×8） | **已修复** + 全量扫描锁定 |
| F2 | 真实缺陷（注释与实现矛盾 ×2） | **已修复** + 改写为与实现一致的描述 |
| F3 | 真实缺陷（悬空 javadoc ×1） | **已修复** + 结构扫描锁定 |
| O1 | 观察项（不修） | `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbPersonActivity.java` 仍写死 `FOCUS_STROKE_DP = 3`（第 75/602/836 行），未接线 `@dimen/webhtv_focus_ring_width`。**判定依据**：该文件在 beta tip 与合并结果树**逐字节相同**（`git diff --stat origin/beta <merge-tree>` 为空），不在本轮 82 路径净差异内，也不在 dev1 的 11 个未推送提交中，属 beta 侧既存状态。按 `AGENTS.md` §2「相邻缺陷只报告不擅自扩大范围」记录。建议后续单独任务处理（若用户要求，可把三处宽度改走 token，并同步更新 `TvFocusRingContractTest` 的豁免清单）。 |
| O2 | 观察项（不修） | `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/EpisodeDetailDialog.java:483` 存在同样的悬空 javadoc（两段注释相邻）。**判定依据**：与 beta tip 逐字节相同，不在本轮净差异内，属 beta 侧既存。 |

## 验证

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 双 flavor Java 编译 + 双 flavor AndroidTest Java 编译 | `bash ./gradlew :app:compile{Leanback,Mobile}Arm64_v8aDebugJavaWithJavac :app:compile{Leanback,Mobile}Arm64_v8aDebugAndroidTestJavaWithJavac` | **BUILD SUCCESSFUL**（修复前 55s / 修复后 27s） |
| 双 flavor 全量 JVM 套件 | `bash ./gradlew :app:test{Leanback,Mobile}Arm64_v8aDebugUnitTest` | **BUILD SUCCESSFUL**（1m 38s） |
| JUnit XML 统计（修复前 / 修复后） | 见下表 | 逐项一致，**failures=0 errors=0** |
| UI token 门禁 | `bash scripts/check_ui_tokens.sh` | `UI_TOKEN_STATUS PASS`；`violations=1`（与基线一致，零新增）；`contrast pairs=38 failures=0 min=4.28` |
| 空白/冲突残留 | `git diff --check`、`grep -E "^(<<<<<<<\|>>>>>>>)"` | 退出码 0；冲突标记 0 |

### JUnit XML 统计与基线对比

| Flavor | 修复前 tests | 修复后 tests | failures | errors | skipped | 与 C56 基线对比 |
| --- | --- | --- | --- | --- | --- | --- |
| leanback | 4172 | **4172** | 0 | 0 | 2 | C56 = 4146 → **+26** |
| mobile | 4987 | **4987** | 0 | 0 | 2 | C56 = 4980 → **+7** |

**增量解释**（逐项可解释，非只看总数）：

- leanback +26：beta 带入的加载圈族/OSD/电池块相关新用例（`df7f7ff580`/`819003db8f` 等合并提交带来）+ dev1 主题改动新增的 `TvAppSurfaceFocusRingDeviceTest`（4 项）与 `TvFocusRingContractTest`/`NativeEnhancedPlaybackStyleFocusTest` 的扩充。
- mobile +7：beta 带入的 `VideoActivityLayoutTest`/`TmdbDetailActivityLayoutTest` 用例 + dev1 主题改动对 `InterfaceEntryInteractionTest` 等的扩充。
- 修复前后用例数与失败数**逐项完全一致**，证明本轮注释修复对测试契约零影响。

## 改动清单（本任务相对 `origin/beta` 的净差异，82 路径）

分类摘要（完整清单见 `git diff --name-status origin/beta HEAD`）：

| 分类 | 路径数 | 代表 |
| --- | --- | --- |
| 主题核心 Java | 9 | `theme/ThemeController/ThemeEditor/ThemePresets/ThemePreviewView/ThemeProfile/ThemeProfileValidator/ThemeResolver/ThemeTokens` |
| UI Java | 6 | `TmdbDetailActivity`、`TmdbCardFocusHelper`、`TmdbEpisodeAdapter`、`TmdbVideoAdapter`、`InlineEpisodeAdapter`、`CacheManagementDialog` |
| leanback 资源 | 38 | 30+ selector/shape drawable、`adapter_vod.xml`、`dialog_episode_list.xml`、`dialog_quick_search.xml`、`view_progress.xml`、`styles.xml` |
| main 资源 | 18 | `values/attrs.xml`、`webhtv_attrs.xml`、`colors.xml`、`webhtv_dimens.xml`、`webhtv_styles.xml`、`webhtv_tokens.xml`、`values-night/webhtv_tokens.xml`、3 个 locale `strings.xml` 等 |
| mobile 资源 | 4 | `dialog_cache_management.xml`、`view_progress.xml`、`styles.xml` |
| 测试 | 9 | 含新增 `TvAppSurfaceFocusRingDeviceTest.java` |
| 文档 | 2 | `TV-FOCUS-RING-20261004-unified-theme-ring.md`、`webhtv-unified-visual-design-system-20260920.md` |

## 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `2ee22ce21655791e87164468c3c94d4db761601c`（**双父**：第一父 `649275b76279d7a96a17eb2d48d04578d1a2b34e` = 合并前 dev1；第二父 `fb4fd7d99ca1547ea624f3d5f1b7f3cd81b880a5` = 合并时刻 beta tip） |
| recovery tag | `recovery/C58-beta-merge-review-dev1-20261011/20261011112617-2ee22ce21655` |
| 推送结果 | `git push origin dev1` → `913ae81d1..2ee22ce21  dev1 -> dev1`；`git rev-parse origin/dev1` == `2ee22ce21655791e87164468c3c94d4db761601c` == 本地 HEAD |
| PR | **#434** `https://github.com/Silent1566/webhtv/pull/434`（`--base beta --head dev1`，**只创建未合并**） |
| PR 状态校验 | `state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`、`changedFiles=83` |
| PR 文件集校验 | `gh api repos/Silent1566/webhtv/pulls/434/files --paginate --jq '.[].filename' \| sort` 与 `git diff --name-only origin/beta HEAD \| sort` **逐项一致**（83/83，`diff` 无输出） |
| 提交后净差异 | `git diff --name-only origin/beta HEAD` = **83 路径**（82 功能/测试/文档 + 本任务文档 `docs/C58-beta-merge-review-dev1-20261011.md`） |
| 提交后删除清单 | 仅 `app/src/main/res/drawable/selector_cache_button_focus.xml`（dev1 自身主题环改造，非 beta 丢失） |
| 提交后严格复活行数 | **0**（对 `be1b02e06` 的 2388 条删除行 ∩ dev1 新增行 32 行逐行核对） |
| 收尾复核 | `git merge-base --is-ancestor origin/beta HEAD` 为**真**；`MERGE_HEAD` 已清除；`git status --porcelain` 干净 |

## 闭环记录

| 步骤 | 结果 |
| --- | --- |
| 推送前复核远端 beta | `git ls-remote origin refs/heads/beta` = `fb4fd7d99ca1547ea624f3d5f1b7f3cd81b880a5`，与合并第二父一致（合并基点未被推进，无需增量折入） |
| 提交父结构验证 | `git rev-list --parents -n1 HEAD` = `2ee22ce21 649275b76 fb4fd7d99`（**2 个父**）；`git rev-parse HEAD^2` == beta tip → beta 侧提交以合并父身份进入历史，**未顺带作为 dev1 自己的提交** |
| PR 创建 | `gh pr create --repo Silent1566/webhtv --base beta --head dev1 --body-file /tmp/c58/pr_body.md` → PR #434 |
| 未合并确认 | `gh pr view 434 --json state,mergedAt` → `OPEN` / `null`（未执行任何 merge 操作，符合「只负责创建」） |
| 临时文件清理 | `/tmp/c58/**` 已清理 |

**本任务闭环完成。** 三项用户要求均已满足：① 合并远端 beta 最新代码且远端已移除/回退的提交零复活；② 复评全部已修改代码（含 11 个已提交未推送提交）并修复 3 类真实缺陷、3 轮评审通过；③ 提交、推送、创建 PR #434（中文描述、只创建未合并）。
