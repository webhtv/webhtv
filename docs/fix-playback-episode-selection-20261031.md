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
