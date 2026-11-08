# C33：dev2 合并远端 beta 最新代码并复评未推送改动（集多版本消歧回归修复）

## Recovery anchor

- **目标**：将远端 `beta` 最新代码（`bf9e6bcb19`，含 PR #382/#393/#394/#395 合入）合入本地 `dev2`，确认未顺带带回任何远端已移除/回退的提交内容；复评 `dev2` 相对 `origin/beta` 的全部已修改代码（含已提交未推送的 `dd37a13fc5`）；发现问题修复并验证，循环复评直到通过；提交本任务改动、推送 `dev2`、创建 `dev2 -> beta` 中文 PR（只创建不合并）。
- **验收**：`dev2` 包含 `origin/beta` 全部提交；净差异只含本分支自身改动（结构性排除被回退内容夹带）；评审发现的问题修复且定向单测通过；PR 描述中文、排版清楚。
- **允许路径**：`app/src`（评审修复）、`docs/C33-beta-merge-review-dev2-20261002.md`（本文档）；合并提交带入路径由 `git merge origin/beta` 决定，未额外编辑。
- **分支/HEAD**：任务开始 `dev2` HEAD = `dd37a13fc5c38dc6975125c6c22b298e1dfeb959`（领先 origin/dev2 两个提交），工作区干净，0 个保护脏路径。
- **守卫时序**：守卫在合并前启动（base_head=`dd37a13fc5`）；合并提交即任务交付物，合并后把守卫 base_head 同步为合并提交 `b6877389da`（与 C28/C30/C31 同场景先例一致，守卫会话只覆盖评审修复与文档提交）。
- **当前状态**：合并完成（`b6877389da`，无冲突）→ 四轮评审完成：第一轮发现 `dd37a13fc5` 的版本消歧误伤跨线路/跨源续播 → 修复（`versionAware` 分层）→ 定向单测通过 → 第二/三轮复评通过。
- **下一动作**：`task_guard.sh finish` 提交并打恢复标签，推送 `dev2`，创建 `dev2 -> beta` 中文 PR（只创建不合并）。

## 时间与设备

- 当前本地时间：2026-10-02 23:10 开始（Asia/Shanghai）；合并 2 分钟、评审约 50 分钟、修复与验证约 60 分钟，纯仓库侧工作，未使用模拟器（改动为纯 Java 逻辑与单测，由单测覆盖验证）。

## 同步核对台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始 HEAD | `dd37a13fc5c38dc6975125c6c22b298e1dfeb959` |
| 远端 `origin/beta` HEAD | `bf9e6bcb19`（Merge PR #382 from dev3） |
| 远端 `origin/dev2` HEAD | `410b76a4ca` |
| beta 新增提交（相对本地分叉点 `45d340418a`） | 82 个（PR #382/#393/#394/#395 及 dev1/dev3/dev4 合并） |
| 合并结果 | `b6877389da` = merge `dd37a13fc5` × `bf9e6bcb19`，无冲突 |
| `HEAD..origin/beta`（合并后） | 0 |

- **远端已移除/回退的提交未被顺带带上（决定性核对）**：
  - 合并树相对 `origin/beta` 的净差异与 `dd37a13fc5` 自身 diff **逐字节一致**（`diff` 比对 IDENTICAL）：仅 `Episode.java`/`Flag.java`/`EpisodeVersionIdentityTest.java`/`HistoryPlaybackTest.java`/`docs/fix-episode-version-identity-20261002.md` 5 个文件。合并树不可能夹带 beta 不存在的内容。
  - 关键回退坐标 `5682f2b054`（剔除 PR #353 主题系统改动）经 `git merge-base --is-ancestor` 判定**不是**合并后 HEAD 的祖先，也不是 `origin/beta` 的祖先。
  - `fd29d76ee7`（撤销电视版暗色配色搬到手机版）在 `origin/beta` 历史内，合并带入的是**回退后的状态**；其触及的 18 个文件在合并树中与 `origin/beta` 逐字节一致（含缺失一致）。
  - 被回退功能标记（`history_watched_time`/`AdSegmentVerifier`/`DecodeMode`/`LUT`/`HlsSegment`/dynamic theme）在净差异中 0 命中。
  - revert 提交触及文件 ∩ 本分支净差异文件 = **空**。

## 合并带入内容核对

合并相对第一父（`dd37a13fc5`）带入 564 个文件（app/docs/scripts），与 beta 侧 `45d340418a..bf9e6bcb19` 一致：PR #382（dev3 统一主题系统 + C32 dev3 合并复评）、PR #393（dev4 播放器多线程/硬解按钮接线与默认隐藏）、PR #394（猫源启动超时修复）、PR #395（dev1 合并）、`c92ada1015`（原生增强播放页当前线路绿色高亮描边）等。

## 评审过程与发现

### 第一轮：净改动逐行评审

`dd37a13fc5`（fix(tv): 同一 TMDB 集多版本按季号置信度消歧）——修复"炫彩详情点第二版本却被当作第一版本"。

**发现问题（真实回归）**：`matchesPlayback` 被改为"两侧 TMDB 季号已知且相等时，URL 不同即判不同集"。但以下既有契约场景恰好都是"URL 不同但同一集"：

1. **跨线路续播**（`1fd83c6f53` 特性："切换源或线路后进度不再丢失"）：换线路后 URL 必然不同；`EpisodePositionCache` 缓存键含线路名（`siteKey|vodId|flag`），换线路必 miss → `updateHistory` 判"换集" → `setPosition(C.TIME_UNSET)` → **进度从 0 开始**。
2. **跨源续播**（`History.forPlaybackKey` 注释："跨源时线路名与剧集 URL 必然不同"）：`updateFastTmdbPlaybackHistory` 注释明说"允许同一标准季集跨线路共享进度"，新语义直接与该处意图矛盾。
3. **源站刷新**（`matchesPlayback` 自身 javadoc："源站刷新后 URL 可能变化……回退到集名和集号"）：刷新后旧 URL 不在列表 → 判"换集" → `getSelectedEpisodePosition` 回落 0 → **选中高亮错回第一集**。
4. **炫彩内联播放**（`TmdbDetailActivity.updateInlineHistory`）：同集换线路会误触 `stopInlinePlaybackSync` 与进度重置。

原分层"季号未知时保留容错"只保住了一小部分场景；TMDB 绑定模式（聚合主路径）下两侧季号都已知，回归面覆盖主路径。

### 修复设计：按"历史 URL 能否定位到当前线路条目"分层

多版本消歧与跨线路容错的唯一可靠区分信号是**对侧（历史）URL 仍能在线路条目中命中**：

- 命中 → 历史与当前列表同属一条线（多版本并存）→ 启用版本消歧（保住 `dd37a13fc5` 的原始修复）。
- 未命中 → 换线路/换源/源站刷新（旧 URL 已不在列表）→ 保留集号容错（保住跨线路续播契约）。

该判据同时正确处理全部场景：多版本切换（url-v1→url-v2 同线路：消歧，版本间不共享进度）、同版本续播（URL 相等：同集）、刷新（旧 URL 消失：容错同集）、换线路（旧 URL 在另一线路：容错同集）。

### 实现

- `Episode.matchesPlayback(Episode)`：恢复纯容错语义（TMDB 分支 `matchesNumber`），跨线路/跨源/刷新全部保契约。
- `Episode.matchesPlayback(Episode, boolean versionAware)`：新重载，`true` 时执行 `dd37a13fc5` 的季号置信度消歧；`false` 等价单参。
- `Flag.containsEpisodeUrl(Episode)`：消歧启用判据（与 `Flag.find` URL 优先定位同构，含 TMDB 位置冲突防护）。
- 调用方（leanback `updateHistory`/`updateFastTmdbPlaybackHistory`/intent 选集/`getSelectedEpisodePosition`；mobile `updateHistory`/intent 选集；`TmdbDetailActivity.isHistoryEpisode`）：按判据传入 `versionAware`。
- leanback `getSelectedEpisodePosition` 增加与 `Flag.find` 同构的 URL 优先定位（多版本选中由 URL 命中，换线路/刷新回落集号容错）。
- `Flag.find` 的 URL 优先定位（`dd37a13fc5` 已有）保持不变——它是版本定位的正确入口。

### 第二/三轮：修复复评

- 逐块复查修复 diff：`versionAware` 判据与 `Flag.find` 口径一致；`getFlag()` 空值安全（空 flag → false → 容错）；`HistoryResumeCoordinator`（beta 新增）用自有字段级比较，不经过 `Episode` 匹配语义，无冲突；`VodBrowse`/`PlaybackProgressWriter`/`AudioHistory`/`HistorySourceResolver` 单参调用恢复原行为，无回归；audio queue 的 `matches()`（同线路语义）保持消歧，正确。
- 场景闭环推演：多版本切换/同版本续播/刷新/换线路/跨源全部通过。

## 验证

| 轮次 | 命令 | 结果 |
| --- | --- | --- |
| 修复后定向 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests ...EpisodeVersionIdentityTest --tests ...HistoryPlaybackTest` | 80 项中 1 项失败（旧断言未带 versionAware）→ 修正断言后全过 |
| 断言对齐 | `...EpisodeVersionIdentityTest` + `...HistoryPlaybackTest` | 17 + 63 全通过 |
| 源码断言 | `:app:testMobileArm64_v8aDebugUnitTest --tests ...VideoActivityLayoutTest --tests ...VideoActivityHistoryTitleTest` | 153 + 4 全通过 |
| 契约回归 | leanback `bean.*`/`history.*`/`playback.*` | 404 tests, 0 failures |
| 修复后复验 | leanback 定向（bean/history/playback/Reader/GlobalHistoryResume） | 248 通过；仅 `ReaderPlaybackRoutingSourceTest` 2 失败 |

**预存失败甄别（与本任务无关，不扩界修复）**：在纯合并树 `b6877389da`（stash 修复后）复跑，同样 12 项失败（`LiveActivitySourceFallbackSourceTest` 2、`FollowingUiSourceTest` 2、`NativeEnhancedPlaybackStyleFocusTest` 1、`ReaderPlaybackRoutingSourceTest` 2、`SearchResultDownFocusTest` 1、`TmdbSourceOnlyInteractionTest` 1、`DialogRoundedCornerSourceTest` 1、`TmdbSourceDialogInflationContractTest` 1、`WebThemeTokenSourceTest` 1）——全部是 beta 带入的源码断言测试与当前源码口径不一致，修复前后一致存在，归属 beta 侧 dev1/dev3/dev4 改动，已按 AGENTS.md 记录不修。

## 风险与回滚

- 风险：`containsEpisodeUrl` 判据依赖源站列表稳定；若源站把多版本 URL 全部改写（刷新）同时保留多版本，消歧退化为容错（按集号命中第一个版本）——与 `dd37a13fc5` 之前的旧行为一致，不劣化。
- 回滚：`git revert` 本任务提交即可；无数据迁移、无持久化格式变更。

## 涉及文件

- `app/src/main/java/com/fongmi/android/tv/bean/Episode.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Flag.java`
- `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/test/java/com/fongmi/android/tv/bean/EpisodeVersionIdentityTest.java`
- `app/src/test/java/com/fongmi/android/tv/bean/HistoryPlaybackTest.java`
- `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityHistoryTitleTest.java`
- `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityLayoutTest.java`
- `docs/C33-beta-merge-review-dev2-20261002.md`（本文档）
