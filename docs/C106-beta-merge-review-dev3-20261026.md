# C106-beta-merge-review-dev3-20261026

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev3`；评审 `dev3` 已提交未推送的 10 个提交；修复必修项并验证；
  修复后再次评审直至通过；然后提交、推送 `dev3`、创建到 `beta` 的中文 PR（含具体改动内容，只创建不合并），
  最后拉取远端最新代码。
- **验收标准**：
  1. `dev3` 相对 `origin/beta` 落后 0 个提交（`origin/beta` 成为 HEAD 祖先），合并 0 冲突、`beta` 的 223 个路径一个不丢、不夹带评审外改动。
  2. 评审覆盖 10 个未推送提交（相对 `origin/dev3`）的 31 个 `app/` 路径；每条结论有可复核证据。
  3. 每个必修项均已修复并有回归断言；修复后复评通过。
  4. 双 flavour 编译通过；单测失败集与 `origin/beta` 基线**逐条一致**（无新增失败）；`scripts/check_ui_tokens.py` PASS；`git diff --check` 空。
  5. 改动经任务守卫 `finish` 落盘并打 recovery tag；推送 `dev3`；创建到 `beta` 的中文 PR；随后拉取远端最新代码。
- **Lane**：`upstream`（`app` + `docs` + `chaquo`）
- **任务守卫**：`C106-beta-merge-review-dev3-20261026`，base HEAD `9996d787ec0c23ddbec0b48ff714134e68801b83`，branch `dev3`
- **当前状态**：合并、评审、必修项修复、复评、验证完成；交付坐标见 §8

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 合并目标 | `origin/beta` = `236a8fc02f5a2ce4ea54001c63fa0cfc0d66ffdd`（"Merge pull request #489 from Silent1566/dev4"） |
| 合并前本地 | `dev3` = `9996d787ec0c23ddbec0b48ff714134e68801b83`（"feat(tmdb): 未显式配置语言时请求语言跟随界面…"） |
| 祖先关系（合并前） | `git merge-base --is-ancestor origin/beta HEAD` → **false** |
| 落后 / 领先（合并前） | `git rev-list --left-right --count origin/beta...HEAD` = **28 / 10** |
| merge-base | `17bd235c248ca3c40c7194bc3b09d57293465858`（C102 的 docs 回填提交） |
| 冲突预检 | `git merge-tree --write-tree --name-only dev3 origin/beta` → 仅返回 tree `3feceac0791be0f853dd435b1d4ae4f5df72f0b8`，**无冲突文件段** |
| 合并动作 | `git merge --no-commit --no-ff origin/beta` → "Automatic merge went well"，暂存 **192 个路径 / +4162 −116**，**0 冲突、0 手工解冲突**；`.git/MERGE_HEAD` = `236a8fc02f…`，与 `origin/beta` 一致 |
| 合并纯净性（正向） | `git diff --cached --name-only HEAD` 的 192 条中，**没有任何一条**是 `beta` 之外的路径（`comm -23 staged beta` 为空） |
| 合并纯净性（反向） | `git diff --cached --stat origin/beta` = **仅 dev3 自己的 32 个路径 / +1537 −57**，`beta` 侧一个路径都没丢 |

`beta` 侧本次带入的 28 个提交（全部为 `beta` 已评审/已合并内容，本轮不重复评审）：`236a8fc02f`（PR #489 合并）、
`4058fb7cf8`、`db0b4483b3`、`772a25a454`（同名线路切换稳定键）、`ee285f8e85`、`02fc09ede4`、`3cfe93f389`、
`11be83da8b`（OSD 徽标扩充）、`8322438787`、`306b823dc7`、`9b0a547eae`、`192b55c2fc`、`62f0b777d2`、
`9689118851`、`39e1b4a0d4`、`1322505938`、`64ee84e68e`、`b829d104ef`、`0dc165a9a2`、`7c725e4d7e`、
`1d80913f77`、`e8551e321c`、`762f5ae6ae`、`8ef4dbf34f`（PY-BRIDGE-DEPEND-01）、`35516a58ff`、`7d4bee6ce1`、
`49e5b2c4bc`、`4a55998f35`。

## 2. 评审范围

本轮需要新评审的是 `dev3` 相对 `origin/dev3` 的 **10 个未推送提交**，净差异 **31 个 `app/` 路径 / +1274 −57**（另含
`docs/TMDB-HANT-MATCH-handoff.md` +263）：

- `d638f6b9a9` 直播源身份与界面语言解耦
- `adcf52019b` 上次频道/频道同步按语言无关归一名匹配
- `c9fb2731b1` TMDB 匹配前简繁归一
- `d1616410bb` 缓存键与标题清洗不受界面语言影响
- `2b405b4b7b` 分组名与 keep 键不随界面语言漂移
- `07b9c20b60` 站点名能力标签不随界面语言变形
- `024ac3f661` 语言资源入口加锁容错
- `6c2de578dc` 详情页匹配与分季缓存键简繁归一
- `abaef4f1b7` 回填设备实测证据（文档）
- `9996d787ec` TMDB 请求语言跟随界面 + `languageConfigured`

## 3. 评审结论与必修项

评审发现 **3 个必修项**（均为用户可见的功能回退/一致性缺陷），全部已修复：

### F1 `LiveBrowse` 浏览树分组标题回退

- **问题**：`Group.trans()` 改为只写 `displayName` 后，`LiveBrowse.getGroups()` 仍用 `group.getName()` 作
  `MediaItem` 标题。旧版本 `name` 被就地译文，标题即译文；新版本下直播浏览树的分组名在繁体界面**不再翻译**。
- **证据**：`app/src/main/java/com/fongmi/android/tv/browse/LiveBrowse.java:40`；
  `GroupAdapter`/`LiveAdapter`/`LiveActivity` 均已改用 `getDisplayName()`，只有浏览树漏改。
- **修复**：`BrowseTree.folder(LIVE_GROUP + group.getName(), group.getDisplayName())` —— **id 保持身份名**（要能长期持久化、
  与 `Live.keep` 同源），**标题改用展示名**。
- **回归**：`GroupDisplayNameTest.browseTreeUsesDisplayNameForTitleButIdentityNameForId`。

### F2 TMDB 缓存语言与请求语言不同源

- **问题**：本次提交把请求语言改成跟随界面语言，但**只有 `TmdbService` 一处**换成了两参 `requestLanguage(config, Setting.getLanguage())`；
  `TmdbDetailActivity`(9 处)、`TmdbUIAdapter`(8 处)、`TmdbSourceAdapter`(3 处)、`History`、`SubtitleTmdbResolver`、
  `MediaTitleResolver`、leanback `VideoActivity` 仍用单参重载（只看配置，默认 `zh-CN`）。
- **后果**：繁体界面下网络按 `zh-TW` 取回繁体标题，却按 `zh-CN` 写进 `TmdbMatchCache.Entry.language` /
  `TmdbDetailCache.Entry.language`；`TmdbLanguagePolicy.matchesTarget:91-100` 明确 **zh-TW 不满足 zh-CN**，
  于是切回简体界面时语言守卫被绕过，繁体标题被当成简体标题显示。
  这与 `TmdbService.java:658` 自己声明的"请求语言与缓存分键必须同源"契约直接冲突。
- **证据**：`git grep 'TmdbLanguagePolicy.requestLanguage'` 的调用点清单；`TmdbDetailCache.java:122`
  （`matchesTarget(expectedLanguage, language)`）；`TmdbMatchCache.java:342`（`toDisplayItem` 同机制）。
- **修复**：上述调用点全部改为两参重载并传 `Setting.getLanguage()`（共 22 处），
  单参重载的 javadoc 明确标注"只反映配置、不跟随界面，发请求/写读带语言分键的缓存必须用两参"。
  同时把 `TmdbSourceAdapter.episodes()` 的语言查找提到循环外，避免按集重复读 `Setting`。
- **回归**：`TmdbLanguageFollowUiTest`（6 例，含旧配置兼容）。

### F3 旧配置的显式 TMDB 语言被静默覆盖

- **问题**：`languageConfigured` 是本轮新增字段，旧配置升级后为 `null`。只看标记的话，
  早已把 TMDB 语言设成 `en-US`/`ja-JP`/`zh-TW` 的用户会被判为"从没配置过"，
  在中文界面下被强制改成 `zh-CN`/`zh-TW` —— 正是该字段 javadoc 声称要避免的覆盖。
- **证据**：`TmdbConfig.java:173`（`sanitize()` 只在空值时填 `DEFAULT_LANGUAGE`，不会改写用户值）；
  新增字段 `languageConfigured` 无历史数据。
- **修复**：`isLanguageConfigured()` 增加兜底 —— `language` 非空且不等于 `DEFAULT_LANGUAGE` 即视为用户表达过偏好。
  这正是"非缺省值本身就是显式偏好证据"的可靠推断。
- **回归**：`TmdbLanguageFollowUiTest.legacyConfigWithAnExplicitNonDefaultLanguageStillWins` /
  `legacyConfigHoldingTheDefaultValueStaysUnconfiguredAndFollowsUi`。

## 4. 建议项的处理

| 建议 | 处理 |
| --- | --- |
| `App.getResources()` 失败后不重试（"静默永久降级"） | **已修**：失败只缓存 `Resources` 不认领语言，下轮同语言再试 |
| 上述重试是热路径，可能无限重算 + 刷日志 | **已修**：新增 `MAX_RESOURCES_APPLY_ATTEMPTS = 3`，连续失败到上限后放弃该语言（认领语言恢复 volatile 快路径），语言变化或 `invalidateResources()` 才解除 |
| `catch (Throwable)` 过宽，连 OOM 一并吞 | **已修**：收窄为 `RuntimeException` |
| `TmdbConfig.setLanguage` 无 javadoc | **已补** |
| `languageForInterface` 硬编码 1/2 | 保留（纯策略类不依赖 Android 是既定设计），由 `TmdbLanguageFollowUiTest` 锁定取值 |
| 繁简噪声词表三处分叉、归一工具重复 5 份 | **本轮不动**：跨模块收敛属重构，超出本次评审范围；已在 §7 记为后续项 |
| `Site.trans()` 仍原地改写 name | **本轮不动**：标签段已冻结、`name` 未被任何持久化键派生，改动会扩散到站点规则匹配，收益不足 |
| `docs/TMDB-HANT-MATCH-handoff.md` 第二层仍标"待办" | 见 §7 说明 |

## 5. 验证证据

| 项 | 结果 |
| --- | --- |
| 双 flavour 编译 | `compileMobileArm64_v8aDebugJavaWithJavac` + `compileLeanbackArm64_v8aDebugJavaWithJavac` → **exit 0** |
| `testMobileArm64_v8aDebugUnitTest` | 5434 用例 / **6 失败** |
| `testLeanbackArm64_v8aDebugUnitTest` | 4646 用例 / **8 失败** |
| 基线对照（`origin/beta` = `236a8fc02f` 干净 worktree） | mobile **6 失败**、leanback **8 失败**，**失败用例名逐条一致** → **零回归** |
| 定向回归 | `GroupDisplayNameTest`、`LiveDisplayNameTest`、`SiteTransBracketTest`、`TmdbMatchCacheHantTest`、`TmdbSeasonMatchCacheHantTest`、`MediaTitleParserHantTest`、`TmdbDetailActivityHantMatchTest`、`TmdbLanguageFollowUiTest`、`TmdbLanguagePolicyTest`、`AppResourcesWebViewCompatibilityTest` → **全绿** |
| UI token | `scripts/check_ui_tokens.py` → `UI_TOKEN_STATUS PASS`（38 组对比 / 0 失败） |
| 空白与冲突 | `git diff --check` / `git diff --cached --check` 空；改动文件无冲突标记 |

**基线失败清单（与本轮改动无关，均为 `origin/beta` 上已存在）**：

`MpvFontConfigTest.writeIfChanged_replacesStaleConfigurationAndCleansTemporaryFile`（沙箱无法提交 fontconfig 文件）、
`MpvHlsCacheCoordinatorTest.successfulCommitPublishesCompleteFileAndReleasesReservation`、
`ReaderPlaybackRoutingSourceTest.readerDefinesTheMonotonicClockUsedByRestore`、
`ReaderPlaybackRoutingSourceTest.horizontalComicProgressUsesItsOwnPageState`、
`TmdbSourceOnlyInteractionTest.nativeVideoSourceOnlyHidesNetworkActionsInBothFlavors`、
`WebThemeTokenSourceTest.webHomeTokenDeclarationsStayInsideRootScope`、
`NativeEnhancedPlaybackStyleFocusTest.everyTmdbRowCardCarriesItsOwnVerticalFocusTargets`（仅 leanback）、
`SearchResultDownFocusTest.scrollCallbacksDeferResultLoadingUntilAfterLayout`（仅 leanback）。

## 6. 复评

修复后重跑评审：3 个必修项均已闭环（F1 标题/id 职责分离、F2 22 处调用点语言同源、F3 旧配置兼容），
新增的 `MAX_RESOURCES_APPLY_ATTEMPTS` 防止了 F-建议修复引入的热路径风险；未发现新问题。**复评通过**。

## 7. 遗留与后续项（本轮不做）

- 归一工具在 `TmdbMatchCache`/`TmdbSeasonMatchCache`/`TmdbMatcher`/`TmdbDetailActivity`/`Group`/`LiveConfig` 各有一份
  `Trans.s2t(false, …)` 包装，行为一致但实现分散，后续可收敛到单一工具类。
- 繁简噪声词表在 `MediaTitleParser`/`TmdbMatcher`/`TmdbDetailActivity` 三处手写且集合已轻微分叉
  （`TmdbMatcher.isNoiseTag` 缺 `高碼率`/`普通話版`），后续应提取共享词表。
- `docs/TMDB-HANT-MATCH-handoff.md` 第 220 行仍把"第二层：TMDB 请求语言跟随界面"标为待办，而 `9996d787ec` 已实现；
  该文档是上一任务的交接记录，本轮不改写其历史结论。

## 8. 交付坐标

见文末「交付坐标」小节（提交后回填）。
