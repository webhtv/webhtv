# 播放页选集定位对齐（详情页 / 历史页 → 播放页）

任务：playback-episode-selection-fix-20261031　lane：standard　日期：2026-10-31

## 1. 完成句

用户在任意详情页模式或历史页点选"第 N 季第 M 集 / 同集第 k 个版本"后，播放页必须起播**同一条集身份**；
任何一处无法定位时不得静默退化为"线路第一条 / 第 1 集"，而应退回可解释的身份通道（URL → 原始条目文本 → TMDB 集号）。

历史记录里已经是第 198 集时，重新进入必须仍然续播第 198 集（含源站刷新、季绑定漂移之后的场景）。

## 2. 用户上报

- 详情页点了第 4 季某集，进入播放页仍从第 1 季第 1 集开始，且按播放列表顺序播第一个视频。
- 历史记录已是最新一集（凡人修仙传第 198 集），隔一段时间进入却跳回第 1 集，并触发 TMDB 重新匹配。
- 同一集有多个版本（网盘源）时，明明选的是第二个高码率版本，播放页却播第一个低版本。

三条反馈共用一条链路：**详情页/历史页 → intent 选集 extra → 播放页选集定位 → 历史回写**。
其中"写"侧基本完整，缺陷集中在"读"侧：播放页用文本（mark / vodRemarks）或"第一条"重新推导集身份。

## 3. 现状缺陷（证据）

### 3.1 播放页选集定位（根本原因）

- leanback `VideoActivity.java:4000-4006 seamless(Flag)`：
  `Episode episode = getMark().isEmpty() ? flag.find(mHistory.getEpisode(), true) : flag.find(mHistory.getVodRemarks(), false);`
  mark 非空（从历史/详情带进来的刮削标题）时走 `Flag.find(String, boolean)`（`Flag.java:182-194`），
  该重载**没有 URL 入参**：同名多版本 `Episode.getScore`（`Episode.java:127-133`）同分 100，
  `Stream.max` 并列取首个 → 恒选第一版本。随后 `selectEpisode`→`Flag.toggle`（`Flag.java:177-180`）把 isSelected
  钉在第一版本上，使 `getSelectedEpisodePosition`（`VideoActivity.java:1335-1352`）的 URL 优先分支永不可达。
- mobile `VideoActivity.java:3622-3630 seamless(Flag)` 同构，且在 3627-3628 把定位到的 URL 回写 mHistory，
  把错版本固化成"下次进来还是它"。
- mobile `VideoActivity.java:3607-3610 getSelectedEpisodePosition(List<Episode> items)` 只认 isSelected，
  否则 `return 0`（leanback 版有 URL + TMDB 集号 + matchesPlayback 三级兜底）→ 手机端更脆。
- leanback 快速 TMDB 链 `findFastTmdbPlaybackFlag`（2662-2668）/`findFastTmdbPlaybackEpisode`（2670-2674）
  在 intent 选集不命中时静默 `item.getFlags().get(0)` / `flag.getEpisodes().get(0)`，
  并由 `selectFastTmdbPlaybackEpisode`（2692-2695）把"第一条"设成选中态，正常链 (`checkFlag` 5871-5877) 沿用。
- `findIntentPlaybackEpisode`（leanback 6116-6122）URL 不命中即 `flag.find(playName, true)`；
  playName 位在历史入口被塞入 vodRemarks（`VideoActivity.java:824-825`、`TmdbDetailActivity.java:6805`），
  `Flag.find(String,true)` 在无高分命中时仍可返回 position 槽位集（`Flag.java:189-193`）。

### 3.2 详情页出播传参

- `TmdbDetailActivity.playbackEpisodeName()`（6804-6806）selectedEpisode==null 时退化为 `playbackMark()`（刮削 mark 文本），
  让 mark 冒充集名进入 intent。`playbackEpisodeUrl()`（6808-6810）此时为空 → 名称成为唯一通道。
- `renderEpisodes()`（5021-5072）：季过滤后 `visibleEpisodes` 不含 selectedEpisode 时直接
  `selectedEpisode = visibleEpisodes.get(0)`，把高季选择吞成该季第一集。
- `applyReloadedHistorySelection()`（6748-6755）重置 selectedFlag / selectedEpisode=null 后只调 `renderFlagSelection()`，
  不重新推导选集。
- `refreshSelectionAfterExternalPlayback()`（11419-11441）守卫缺 `playbackSelectionTouched`，从播放页返回会覆盖手选。
- `leanback startDirectTmdb` 17 参重载（963-968）/ mobile（915-920）把 `resumeHistory.getSourceBindingKey()`
  塞进 playFlagKey 位（flagKey 语义被顶掉）；`if (playEpisodeNumber > 0)` 守卫（992-995）会整段丢弃季集号。
- `playbackEpisodeUrl()` 为空时 `putIntentPlaybackSelection`（1002-1012）不写该 extra，
  消费侧 `applyIntentPlaybackSelection`（6080-6109）四 extra 全空直接 return，只靠历史兜底。

### 3.3 历史数据层

- `History.rebindPlaybackRoute`（526-549）第 548 行 `if (episode != null) playback.setEpisodeUrl(episode.getUrl());`
  —— 换线路且找不到对应集时保留旧 URL，落库成"新线路 + 旧 URL"矛盾态。
- `History.isSeasonEligible`（499-509）季号已知且不等即丢弃整条记录 → `findPlaybackCandidate`（461-489）
  三轮全空 → null → `createHistory` → `History.findEpisode`（1328-1356）固定 `flags.get(0).getEpisodes().get(0)` = 第 1 集。
- `TmdbSeasonProgressStore.fromHistory`（25-38）第 36 行用 `history.getCreateTime()` 当 updatedAt，
  而 createTime 是持久字段、updateTime 才是真实新旧（`History.java:1138-1144`）→ 季快照排序与真实新旧脱钩。
- `Setting.isHistoryAggregationEffective()` 一个开关同时关掉 `findPlaybackByTmdb`（`History.java:278-280`）
  与播放页 `getHistoryTmdbItem`（leanback 1410-1415），是"回第 1 集"和"重新匹配"部分同源之处。

### 3.4 详情页模式传参矩阵（全量结论）

| 模式 | 值 | 出播路径 | 选集对齐 |
| --- | --- | --- | --- |
| 沉浸融合 | 0 | `FusionDetailController.play()` → `host.playInline()`，进程内 | 部分：flag+episodeUrl 完整，季号不进播放请求 |
| 炫彩详情 | 1 | `BaseTmdbDetailModeController.play()` → `playDefaultPlayback()` → `startDirectTmdb` | 部分：URL 强匹配，季号被 number>0 守卫截断 |
| 影视原生 | 2 | 详情页即 VideoActivity（进程内） | 对齐；但从详情页 fallback（2902-2907 → leanback 691-707）时零选集 extra |
| 影院（保留） | 3 | 运行时不存在：`Setting.getDetailOpenMode()` 读到 3 立即改写为 1（1186-1189） | 等价模式 1 |
| 详情直放 | 4 | `PlayerDetailController.play()` → `host.playDetailFullscreen()` | 部分可靠：7748 / 7779 两处静默 return |
| 原生增强 | 5 | 详情页即 VideoActivity | 不可靠：无显式集身份通道，纯文本/历史匹配 |

## 4. 决策

用户选择"治本"（C）：先修读取侧（B），再补数据层（C），不引入新的选集传输通道。

- **B1** leanback/mobile `seamless` 改"身份优先"：`flag.find(mHistory.getEpisode(), true)`（URL → 原始条目文本 → TMDB 集号）
  先于 `flag.find(getMark(), false)`；mark 仅在身份失败时作后备。
- **B2** mobile `getSelectedEpisodePosition` 对齐 leanback 的三级兜底。
- **B3** mobile `seamless` 不再无条件把定位结果回写 mHistory 的 URL（只有确认同一集/同一版本时才回写）。
- **B4** leanback 快速链两个 `get(0)` 兜底删除，返回 null 交给既有回落分支（`applyCachedTmdbDetailNextFrame` + 普通链）。
- **B5** `findIntentPlaybackEpisode` 在名称通道之后再补历史集身份通道，避免 position 槽位集冒充命中。
- **B6** `startDirectTmdb` 17 参重载不再用 `sourceBindingKey` 顶替 flagKey（改传空串；resume 身份由 `EXTRA_RESUME_HISTORY_KEY` 承载）。
- **C1** 详情页统一用 `playbackEpisode()` 单一身份派生出 name/url/季集号/快速标题；不再让 mark 冒充集名。
- **C2** `renderEpisodes` 不再用 `visibleEpisodes.get(0)` 覆盖已存在的选中集；`applyReloadedHistorySelection` 补 `renderEpisodes()`；
  `refreshSelectionAfterExternalPlayback` 守卫加入 `playbackSelectionTouched`。
- **C3** `History.rebindPlaybackRoute` 换线路且找不到集时清空 episodeUrl（消除矛盾态）。
- **C4** `findPlaybackCandidate` **不改** `isSeasonEligible`（`HistoryPlaybackTest` 519/546/557/684 依赖其跨源季号判据），
  改为在其后追加第 4 轮"同 key 放宽"：selected 仍为 null 时，允许同 key、季号不同但 `canResume` 且能在当前线路绑定的记录续播。
- **C5** `TmdbSeasonProgressStore.fromHistory` 的 updatedAt 改用 `max(createTime, updateTime)`。

被否决的方案：新增独立"选集传递"通道（如 Parcelable Episode 直传）——需要新增 API 与兼容分支，收益不及让现有 extra 语义收敛；
把 `Flag.find(String)` 改造为 URL 感知——该重载无 URL 入参，改签名会波及 30+ 调用点。

## 5. 验收标准

1. leanback 与 mobile 的 `seamless(Flag)` 均为身份优先，源码断言测试覆盖。
2. 两处 `findFastTmdbPlayback*` 不再出现 `get(0)` 兜底。
3. mobile `getSelectedEpisodePosition` 具备 URL + TMDB 集号 + matchesPlayback 三级兜底。
4. `playbackEpisodeName()` 不再回退 `playbackMark()`；`renderEpisodes` 不以第一集覆盖已有选择。
5. `History.rebindPlaybackRoute` 失配清空 episodeUrl；`findPlaybackCandidate` 第 4 轮生效；
   `fromHistory` updatedAt 取 max(createTime, updateTime)。
6. 定向单测（双 flavor）+ 双 flavor Java 编译通过。

## 6. 验证与回滚

### 6.1 已执行的验证（2026-11-01）

| 命令 | 结果 |
| --- | --- |
| `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` + `:app:compileMobileArm64_v8aDebugJavaWithJavac` | 通过（双 flavor 主源集编译无错） |
| `:app:testLeanbackArm64_v8aDebugUnitTest` | 4834 例，8 失败，3 跳过 |
| `:app:testMobileArm64_v8aDebugUnitTest` | 5599 例，6 失败，3 跳过 |

本任务相关的 5 个测试类双 flavor 全部 0 失败：
`PlaybackSelectionLocatorSourceTest`(6)、`DetailPlaybackHandoffSourceTest`(4)、`HistoryPlaybackTest`(70)、
`TmdbSeasonProgressStoreTest`(8)、`GlobalHistoryResumeSourceTest`(31)。

剩余失败（8/6 条）与本任务无关，为本机既有环境问题：

- `MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`：媒体层既有失败（IOException / AssertionError）。
- `NativeEnhancedPlaybackStyleFocusTest`、`ReaderPlaybackRoutingSourceTest`、`SearchResultDownFocusTest`、
  `TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`：断言里写死了 `\n` 换行，而本机工作区文件为 CRLF
  （`\r\n`），`String.contains` 必然失配；所读文件均不在本任务改动范围内。本次新增的两个源码断言测试用花括号配平取方法体，
  不含 `\n`，因此对本机换行免疫。

### 6.2 回滚

全部改动集中于 5 个源文件 + 5 个测试文件（3 改 2 新建）+ 本文件，单一提交；`git revert` 即回到本任务前状态，
数据层无迁移、无落盘格式变更（`History`/`TmdbSeasonProgress` 字段不变）。

## 7. 相关文档

- `docs/fix-episode-version-identity-20261002.md`：同集多版本消歧的季号置信度分层契约（本次在其上补齐播放页定位层）。
- `docs/tmdb-playable-episode-availability-design.md`：`Flag.getEpisodes()` 是可播放内容的唯一事实源。
- `DETAIL_PAGE_MODES.md`：TV 详情页五种模式的路由说明。

## 8. 第二轮双轴评审（Standards / Spec）与修复

评审对象：提交 `2a0cc43fc7` "fix(playback): keep detail/history episode selection in the player"。
双轴（Standards / Spec）逐条结论与本轮落点如下。

### 8.1 已修复

| 编号 | 问题 | 结论 | 落点 |
| --- | --- | --- | --- |
| M2 | mobile `seamless` 无条件写 `vodRemarks`、有条件写 `episodeUrl`，会落库 (remarks=新集名, url=旧URL) 的矛盾身份 | 修复：`setVodRemarks` 移入 `sameHistoryIdentity` 分支，与 `setEpisodeUrl` 同进同退 | app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:3644-3648 |
| M3 | `TmdbSeasonProgressStore` 里 `fromHistory` 用 `max(createTime,updateTime)`、`reconcile` 只比 `createTime`，两套"最新"定义并存 | 修复：抽出 `freshness(History) = max(createTime, updateTime)`，两处共用 | app/src/main/java/com/fongmi/android/tv/playback/TmdbSeasonProgressStore.java:37、:66、:115 |
| M4 | 两个源码断言测试里三条无约束力的断言（整份源文件全否定、断言 17 参死重载、断言注释文本） | 修复：`fastTmdbPlaybackNoLongerFallsBackToTheFirstItem`（现 :51-58）内删除 2 条整文件全否定断言；整段删除 `directTmdbDelegatesNoLongerReuseTheSourceBindingKeyAsFlagKey`（原 :73-80，断言的是 17 参死重载）；`renderEpisodesNeverReplacesTheSelectedEpisodeWithTheFirstVisibleOne`（现 :47-58）内删除 1 条注释文本断言。C1 收敛后该测试另有 2 条断言对象消失（`if (selectedEpisode != null)`、`if (availableSeasons.contains(episodeSeason)) selectedSeasonNumber = episodeSeason;`），已删除并补 1 条正向锁形断言，用例数仍为 4 | app/src/test/java/com/fongmi/android/tv/bean/PlaybackSelectionLocatorSourceTest.java:51-58（用例数 6→5）；app/src/test/java/com/fongmi/android/tv/ui/activity/DetailPlaybackHandoffSourceTest.java:47-58 |
| N2 | `renderEpisodes` 末尾 `else if (selectedSeasonNumber < 0 \|\| !availableSeasons.contains(...))` 在"四通道解析后必非空"之后不可达 | 修复：先改为纯 `else`；第二轮清理时进一步收敛为单行三元式，删掉"`selectedEpisode != null` 恒真""`availableSeasons.contains(episodeSeason)` 恒真""`else` 恒不可达"三条死判据（`seasonForEpisode` 在可选季非空时六条返回路径全部落在可选季内，仅可选季为空时返回 -1） | app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java:5046-5050 |
| N3 | mobile `seamless` 在 `mHistory==null` 且 mark 命中路径上会解引用空历史（既有 NPE，非本轮引入） | 修复：补 `if (mHistory == null) return false;`（返回 false 表示未处理，保持既有对外行为） | app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:3640-3642 |
| S3 | leanback 快速起播失败回落分支没有收起 `showProgress()` 的加载圈；唯一收圈路径要求已起播（`STATE_READY`），失败时永不到达，转圈永久残留 | 修复：失败分支补 `hideProgress()` | app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2463-2465 |

### 8.2 不改（含理由）

| 编号 | 问题 | 结论 | 理由与落点 |
| --- | --- | --- | --- |
| M1 | `History.findPlaybackCandidate`（History.java:491-499）第 4 轮"同 key 放宽"循环 | **不修，保留** | 用户已明确裁决保留。若删掉，用户上报的"历史已第 198 集 → 重新进入跳回第 1 集"会退化复发。该循环是跨源/换季绑定漂移场景下续播的关键兜底，属数据层（C4）既定设计。落点：app/src/main/java/com/fongmi/android/tv/bean/History.java:491-499（本轮未改动） |
| N1 | leanback `startDirectTmdb` 17 参重载（VideoActivity.java:963-967）无生产调用方（唯一生产调用点 TmdbDetailActivity.java:6789 走 18 参重载） | 不修 | 删除公开重载属 API 变更，超出本轮"修播放页选集定位"的窄范围；保留既有形状、仅删除断言它的死测试（见 M4），并在本文件登记为已知死代码。补充：此前把 `playFlagKey` 改为 `""` 的两个 16/17 参重载在生产代码中同样没有调用方（唯一生产调用走 18 参重载），故那处改动零覆盖、仅属形状修正，不构成行为变更。 |

### 8.3 残余风险

- 页面季号 2 与历史记录季号 1 不一致时，仍可能以"旧季标签 + 同一集号"续播同一集号内容（季绑定漂移场景下的语义错配，非崩溃）。这是 M1 放宽策略的已知代价，本轮不做进一步收敛。

### 8.4 已知不对称（本次不改）

- mobile `findIntentPlaybackEpisode`（app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:6187-6193）只有"URL + 集名"两条通道，没有 leanback 那样的历史集身份通道。属既有形状，本轮按用户裁决不改动；如需对齐可另开任务。
- 双端判据漂移（Standards 轴指出）：mobile 的同名方法末行仍是裸 `flag.find(playName, true)`（:6192），而 leanback 已在名称通道加 `flag.getEpisodes().size() == 1 || episode.getScore(playName, Util.getEpisodeNumber(playName)) > 0` 挡 position 槽位伪命中（:6127-6128）。也就是说 `Flag.find` 的位置兜底仍可能在 mobile 侧造成集名伪命中。用户已裁决本轮不动，留待下一个任务对齐两端判据。

### 8.5 本轮修复的补充说明

- F1（leanback 集名通道）在名称通道加了"真实命中"判据（`size()==1` 或 `getScore(playName, Util.getEpisodeNumber(playName)) > 0`），避免 `Flag.find(String,true)` 在打分全 0 时用 position 槽位兜底、伪命中与该集名无关的条目，使历史集身份通道真正可达（VideoActivity.java:6123-6131）。
- F2 的 N3 空守卫放在 `sameHistoryIdentity` 计算之前（即 `getEpisodeUrl()` 解引用之前），因为 `mHistory` 在后续两处写回都会被解引用；返回 `false` 与 3639 行既有"未处理"语义一致。

### 8.6 第二轮清理批次（代码等价 + 注释与行号纠偏）

| 项 | 内容 | 落点 | 等价性 |
| --- | --- | --- | --- |
| C1 | `renderEpisodes` 挑选季号的三条恒真/恒假判据收敛为一行三元式；原注释"绝不拿别的季第一集顶替用户手选"是假的（`seasonForEpisode` 的兜底正是 `availableSeasons.get(0)`），已一并删除 | app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java:5046-5050 | 等价：`selectedEpisode` 由 :5038-5044 保证非空；`seasonForEpisode` 在可选季非空时六条返回路径（:6476/:6481/:6486/:6487/:6494/:6495）全部落在 `availableSeasons` 内，故旧代码的 `contains` 判断恒真、赋值必然发生；仅可选季为空时返回 -1，与旧行为一致 |
| C2 | 只改注释：说明"换线路失配时只清 `episodeUrl`、刻意不清 `vodRemarks`"的理由（URL 是线路内身份，remarks 是跨线路集号来源，`matchesEpisode` 会退化到 `matchesNumber` 用它） | app/src/main/java/com/fongmi/android/tv/bean/History.java:562-564（:561 与 :565 代码文本未变） | 等价：纯注释，零逻辑改动；History.java:491-499 的"同 key 放宽"循环未触碰 |
| C3 | 只改注释：`mHistory` 守卫的表述由"可能为空"改为"防御性守卫" | app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:3640-3642 | 等价：纯注释；`mHistory` 的赋值点仅 :1379/:6010/:6013，历史建立后恒非空，守卫保留不删 |
| T1-T3 | C1 收敛导致 `DetailPlaybackHandoffSourceTest#renderEpisodesNeverReplacesTheSelectedEpisodeWithTheFirstVisibleOne` 两条字符串存在性断言失去对象，删除并按新形状补一条正向断言：`render.contains("availableSeasons.isEmpty() ? -1 : seasonForEpisode(selectedEpisode, episodes)")` | app/src/test/java/com/fongmi/android/tv/ui/activity/DetailPlaybackHandoffSourceTest.java:51-57（用例数 4 不变；:51 的 `visibleEpisodes.get(0)` 否定断言、:52 的 `seasonForEpisode` 断言、:55-57 的 intent 早于 `episodes.get(0)` 顺序断言全部保留） | 断言口径等价于原意：`renderEpisodes` 的季号只由 `selectedEpisode` 推导，且仅在无可选季时为 -1；新断言锁定的是正向存在的表达式，不是整文件否定 |

