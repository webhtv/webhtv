# C112 合并 beta 后评审与修复（dev2）

> 任务编号：C112-beta-merge-review-dev2-20261029
> 分支：dev2
> 起始 HEAD：efdf8be13e（含 998172a4c8 与 efdf8be13e 两个未推送提交）
> 合并后 HEAD：3639181e3e9618f15738c95b1f1603447aa289e4
> 任务模式：standard（task_guard scope = app/src, docs）

## Recovery anchor

- **目标**：合并远端 beta 最新代码 → 评审本轮修改（含已提交未推送的 998172a4c8 / efdf8be13e 与合并带入内容）→ 有问题修复并验证 → 复评循环直到通过 → 提交、推送 dev2、开 PR 到 beta（中文描述需包含改动具体内容）→ 拉取远端最新。
- **验收标准**：leanback 与 mobile 双变体编译通过；following / History / PlaybackProgressWriter 相关单测零失败；Spec 轴复评通过；改动只落在 app/src 与 docs。
- **当前状态**：合并完成、修复完成、双变体编译与单测通过、Spec 轴两轮评审完成（第 2 轮唯一阻塞项 F-07 已由仪器测试补齐并双次实跑通过）、待 Spec 轴第 3 轮确认收口。
- **下一步唯一动作**：派 Spec 轴第 3 轮复评确认 F-07 收口；通过后 task_guard.sh finish 提交并打恢复 tag。

## 1. 合并远端 beta

| 项 | 值 |
| --- | --- |
| 合并来源 | origin/beta = ba802dc432 |
| 合并前预判 | git merge-tree --write-tree origin/beta HEAD 退出码 0，无冲突 |
| 合并提交 | 3639181e3e9618f15738c95b1f1603447aa289e4（双亲 = efdf8be13e + ba802dc432） |
| 带入规模 | 25 个提交 / 45 文件 +3184 −107，含 PR #498(dev1) #499(dev4) #500(dev5) #501(dev3) |
| 合并后 | git rev-list --count HEAD..origin/beta = 0，工作区干净，无冲突标记 |

ort 策略自动合并了 4 个文件，已逐一核对：

- app/src/main/java/com/fongmi/android/tv/bean/History.java：beta 的 findByName(int cid, String)(:397-403) 与 syncTo(int cid, List)(:442-457) 与本方的 setTmdbEpisodePosition(Episode)(:922-936)、setTmdbEpisodePosition(int, int)(:946-953) 共存，无字段级破坏。
- 三个 strings.xml（values / values-zh-rCN / values-zh-rTW）：合并后无重复 key（2498 / 2495 / 2440 条，dupGroups 全 0）。beta 侧新增的是 string-array select_media_badge_position 的第 5 个 item（Off），不是 string key，不撞名。

## 2. 评审组织

按 code-review-zh skill 的双轴思路，用 4 个并行只读子代理 + 协调者独立复核：

| 轴 | 范围 | 结论 |
| --- | --- | --- |
| 合并语义冲突核对 | beta 45 文件 vs 本轮 11 文件 | **无冲突、无回归** |
| FOLLOW-3 设计稿事实核对 | docs/FOLLOW-3-tmdb-following-match-design.md | **不宜按「事实准确」提交**，已修订 |
| Spec 轴（第 1 轮） | 相对规范 docs/FOLLOW-1-following-updates-design.md | **不通过**，4 类问题 |
| Standards 轴 | 同 | 两次重派均 failed 无输出 → 按 R6 由协调者自审 |

合并语义核对的关键证据（协调者已独立复核）：

- History.save() → merge(true) → copyTo 只搬 opening/ending/speed，从不写 tmdbSeasonNumber/tmdbEpisodeNumber；shouldMerge 在 Setting.isHistoryAggregationEffective() 为真时直接返回 false，与 syncTo 契约一致。
- 推送指定源走 Action.pushScopedHistory → VodSiteCatalog.findConfig(configKey) → cid = config.getId()，全程不读 VodConfig.getCid()；VodSiteCatalog 只读不 insert，未知 key 抛 IllegalArgumentException 不回落激活配置。
- -1 季号哨兵不污染季进度与备份：TmdbSeasonProgressStore.isEligible 与 Backup 均以 >= 0 为门槛。

## 3. Spec 轴第 1 轮的四类问题与修复

### P1 F-07 缺「失败保留旧快照」的测试

规范 docs/FOLLOW-1-following-updates-design.md:321：| F-07 | 手动检查失败保留旧快照 | 异常单测 + 数据库 DAO 测试 |

原状态：FollowingUpdateCoordinatorErrorTest 只有 6 例纯函数，无任何落库断言。

修复：新增 app/src/test/java/com/fongmi/android/tv/following/FollowingFailureSnapshotContractTest.java（4 例源码契约断言）：

- failurePathNeverTouchesThePlayableSnapshot：FollowingUpdatePolicy.markFailure 方法体不含 playable / lastProbeAt。
- probeCatchRecordsSourceErrorWithoutInventingPlayableCounts：probeBestEffort 的 catch 片段写 source.lastError + source.lastProbeAt，但不含 playable。
- probeFailedUsesTheNoPlayablePredicate：锁定零可播集数必须走 probeYieldedNoPlayable。
- uiFailureJudgementNoLongerDependsOnLastProbeAt：两处 UI 判据不再含 source.lastProbeAt > 0。

**能力边界更正**：第一轮曾据「`app/src/test` 下无 Room in-memory / Robolectric 基础设施」把 F-07 收敛为源码契约断言，这个判断不完整 —— 仓库的 DAO 级测试惯例在 **androidTest** 而非 `app/src/test`：`FollowingDatabaseTest.java:32` 已有 `Room.inMemoryDatabaseBuilder(context, FollowingDatabase.class).build()`（规范 `:1756` 明确记录该做法为既定），`FollowingDeviceDataRule.java:26-34` 已提供真库快照/还原，`FollowingUpdateCoordinatorDeviceTest.java:102` 已提供 `FollowingUpdateCoordinator(metadata, probe)` 构造器注入。因此 F-07 的「数据库 DAO 测试」按 androidTest 补齐，见第 7 节。源码契约断言保留为防回退的廉价守卫。

### P2 检查失败筛选与卡片文案分叉

规范 :1728（五类筛选含「检查失败」）与 :1725（全部无效时不伪装成可播）。

原问题：FollowingSourceProbe 成功路径 :50-53 把 lastError 置空串，即使 playableEpisode == 0；于是 item.lastError 为空 → FILTER_FAILED 抓不到，而 FollowingAdapter/FollowingActivity 却用 source.lastProbeAt > 0 显示「检查失败」。

次生误判（协调者自查发现）：AlistSubscriptionImporter.toSource 在订阅导入时**无条件预写** source.lastProbeAt = now，因此「Alist 导入且 currentEpisodes == 0」的源从未被探针检查过也会显示「检查失败」。

修复（FollowingUpdateCoordinator）：

- static final String TMDB_DEGRADED = "TMDB不可用，已回退原站";（:11-12）、static final String SOURCE_FAILED = "来源检查失败";（:14-15）。
- static boolean probeYieldedNoPlayable(snapshot)（:142-144）：snapshot == null || snapshot.playableEpisode <= 0。
- public static boolean sourceCheckFailed(String lastError)（:150-152）：非空白且 != TMDB_DEGRADED。必须 public 才能被 com.fongmi.android.tv.ui.* 复用。
- 早退条件扩为 sourceSnapshot == null || sourceSnapshot.source == null || sourceSnapshot.playableEpisode <= 0（:74）。
- probeFailed = probed.attempted && probeYieldedNoPlayable(probed.snapshot);（:95）。

修复（UI 三处统一口径）：

- FollowingAdapter：卡片文案改用 FollowingUpdateCoordinator.sourceCheckFailed(item.lastError)，新增 import。
- FollowingActivity：case FILTER_FAILED -> FollowingUpdateCoordinator.sourceCheckFailed(item.lastError);（:229）与详情弹窗同口径（:375）。

语义判定：TMDB 降级文案只说明元数据缺失、来源本身仍可用，故被排除在「来源检查失败」之外，与规范 :1727「TMDB 主 provider 失败但绑定来源探测成功时…把本轮视为成功」一致。

### P3 检查失败时跳过进度重建

规范自身矛盾：:610「Worker 每次检查前对 due 记录执行一次进度 reconcile，修复投影丢失」vs :758-763 的 9.5 流程图「provider 失败 → 3.4 continue」。

原状态：代码取了 9.5 一侧，失败早退直接 return false，跳过 FollowingStore.reconcile(item)。本轮修复又把零可播集数并入失败早退，**扩大了不重建进度的记录集合** —— 越是探不到集的源，进度越不会被修。

修复（由用户裁定「修」）：在失败早退分支的 FollowingStore.update(item); **之后**、FollowingScheduler.scheduleNext(...) **之前**插入 FollowingStore.reconcile(item);（:82）。

顺序有硬理由，已用源码核实：

- FollowingStore.projectHistory(:310-341) 内部**重新从 DAO 查行**（findByTmdb/findBySource）再 insertOrUpdate，因此读到的是 update(item) 刚落库的失败态，不会覆盖 lastError/failureCount/nextCheckAt。
- 反过来先 reconcile 后 update：FollowingStore.update(:162-165) 是整行 insertOrUpdate(item)，会把 check() 开头加载的旧 watchedEpisode 写回去，抹掉刚重建的进度。
- FollowingScheduler.scheduleNext(:55-75) 只读 nextCheckAt 不写库，放最后才拿到 markFailure 算好的退避时间。

新增 FollowingFailureReconcileContractTest（3 例）锁定：失败分支含 reconcile、reconcile 位置严格在 update 之后、"来源检查失败"带引号字面量全文件只出现 1 次。

### P4 季号未知导致集数进度永久停更（根因）

机制链（协调者逐行核实）：

1. History.setTmdbEpisodePosition(Episode) 在 TMDB 集号缺失时写 setTmdbEpisodePosition(-1, sourceNumber)，季号为 -1 表示「未知季」。
2. FollowingStore.projectHistory 原来用 Math.max(0, history.getTmdbSeasonNumber()) 归零 → -1 变 0。
3. FollowingDao.findByTmdb/findBySource 与 reconcile/historyFor 都对 tracked_season 做**等值**匹配。
4. 于是 trackedSeason >= 1 的记录永远命中不到未知季历史 → watchedEpisode 永久落后 → unwatchedCount/hasUpdate 不前进。

修复（FollowingStore，不新增 Room 字段、不改 DAO @Query、不做迁移）：

- static int resolveSeason(int historySeason, int trackedSeason)（:296-299）：historySeason >= 0 ? historySeason : Math.max(0, trackedSeason)。
- static boolean seasonMatches(int historySeason, int trackedSeason)（:301-304）：historySeason < 0 || historySeason == trackedSeason。
- projectHistory(History) 委托 projectHistory(history, -1)；新重载 projectHistory(History, int trackedSeasonHint)（:310）内 int season = resolveSeason(...)，两处 DAO 查询与 watchedSeason/trackedSeason 写入统一用 season。
- reconcile(:236) 与 historyFor(:254) 的季号过滤改用 seasonMatches；reconcile 调用 projectHistory(latest, item.trackedSeason) 把季号提示钉住。

新增 FollowingSeasonProjectionTest（10 例）覆盖 resolveSeason 与 seasonMatches 的边界。

跨季归属分析：seasonMatches 对 -1 全放行，同一 tmdbId 有多条不同 trackedSeason 时，一条未知季历史会成为多条记录的候选。reconcile 路径用 hint 钉住季号；二义性只落在「无追更记录时 project(history) 直投影」路径，此时 season 仍为 0（与修复前行为一致）。

watchedSeason 语义变更已核实安全：grep 全部 10 处读写点，**没有任何 UI 依赖 watchedSeason == 0 表示未知**。

刻意不改的同类点：FollowingPlaybackBridge.java:89 仍有 Math.max(0, history.getTmdbSeasonNumber())，那是新建追更行时的初始 watchedSeason（其时记录刚创建、无历史投影响应），属既有行为，避免越界。

### P5 静默失败面

修复：FollowingUpdateWorker 的两个 catch 补日志 —— catch (IOException) → Log.w(TAG, "following check retry", e) 后 Result.retry()；catch (Throwable) → Log.e(TAG, "following check failed", e) 后 Result.success()。返回值语义不变。

刻意保留的 best-effort 静默（既有设计，非缺陷）：FollowingStore.project 的 catch (Throwable ignored)（投影失败不得影响播放与历史保存）、FollowingMetadataClient 的降级返回 null。

### P6 scope creep

把 "TMDB不可用，已回退原站" 与 "来源检查失败" 提为常量属合理重构：两处字面量在 androidTest 的 FollowingUpdateCoordinatorDeviceTest 里被逐字断言锁定，集中一处后取值一字未改，同时让 sourceCheckFailed 能可靠识别降级文案。

## 4. FOLLOW-3 设计稿修订

事实核对发现 3 处与真实代码不符的断言 + 行号整体过期（基线 998172a4c8 之后已 27 个提交）。按「只修正事实错误、不代用户决定产品取舍」的范围修订：

| 原断言 | 真实情况 |
| --- | --- |
| 「tmdbId 只有两个来源」 | 共 6 处写入路径；只有播放页 stampHistoryTmdbId() 与 History.enrichTmdbId() 会主动补盖且被 Setting.isHistoryAggregationEffective() 拦住，另 4 处（FollowingPlaybackBridge / TmdbDetailActivity.addFollowing / AlistSubscriptionImporter / FollowingMergePolicy.mergeOne / FollowingStore.resolveTmdb / Following.copy() 中的非门闸部分）只搬运已有身份 |
| 「resolveTmdb 返回 null 表示非 tv / 目标行是墓碑」 | 目标行是墓碑时返回**该墓碑行本身**（C9 防复活语义）；找不到可迁移源时返回既有 target。故失败判据必须是 migrated == null 或 migrated.isDeleted() |
| Following.copy() 在 :194 | :154（item.tmdbId = tmdbId; 在 :164） |

另处理：

- 内部矛盾消解：persistMatchError 与 autoMatch 的 catch 统一改走 FollowingUpdatePolicy.markFailure，明确禁止直接 item.lastError = msg（否则绕过 160 字符截断、failureCount 自增与退避）；6.2/6.3 的返回语义表统一。
- 不可执行验收改写：AC-15 / AC-17 拆成「源码契约断言 + 代码评审 + 仪器测试」三层（FollowingUiSourceTest 是源码字符串断言，不能证明运行期落库或实际库版本）；AC-16 删去未经验证的「基线 223 tests/flavor」。
- 行号更正：enrichTmdbId 在 HEAD 为 History.java:1170-1180（守卫 :1171）、Setting.isHistoryAggregationEffective 本体 Setting.java:1401-1403（语义 :1393-1395）、saveManualTmdbMatch :3805-3813、FollowingUpdatePolicy.applyMetadata :26-67。
- 对正被并行编辑的 5 个文件改为按符号名引用，不写行号。

## 5. 验证证据

### 编译（合并后）

| 命令 | 结果 |
| --- | --- |
| :app:compileLeanbackArm64_v8aDebugJavaWithJavac | BUILD SUCCESSFUL in 2m22s（.codex/tmp-c112-compile-leanback.log） |
| :app:compileMobileArm64_v8aDebugJavaWithJavac | BUILD SUCCESSFUL in 1m50s（.codex/tmp-c112-compile-mobile.log） |

仅有既有 deprecation/unchecked 告警（TypeFragment.java 未经检查操作），无新增错误。

### 定向单测（修复后，双变体，全部零失败）

命令：:app:testMobileArm64_v8aDebugUnitTest 与 :app:testLeanbackArm64_v8aDebugUnitTest，--tests 覆盖 com.fongmi.android.tv.following.* + FollowingUiSourceTest + HistoryPlaybackTest + PlaybackProgressWriterEpisodeTest + HistoryScopeSyncTest。

| 测试类 | mobile | leanback |
| --- | --- | --- |
| FollowingSeasonProjectionTest（新增） | 10 / 0 / 0 | 10 / 0 / 0 |
| FollowingFailureSnapshotContractTest（新增） | 4 / 0 / 0 | 4 / 0 / 0 |
| FollowingFailureReconcileContractTest（新增） | 3 / 0 / 0 | 3 / 0 / 0 |
| FollowingUpdateCoordinatorErrorTest（原 6 + 新增 3） | 9 / 0 / 0 | 9 / 0 / 0 |
| FollowingSourceProbeTest | 6 / 0 / 0 | 6 / 0 / 0 |
| FollowingAlistImportTest / FollowingIdentityTest / FollowingMergePolicyTest | 4 / 2 / 7 | 4 / 2 / 7 |
| FollowingMetadataClientTest / FollowingPlaybackBridgeTest / FollowingSchedulePolicyTest | 3 / 3 / 6 | 3 / 3 / 6 |
| FollowingUpdatePolicyTest / FollowingWorkStartupSourceTest | 3 / 3 | 3 / 3 |
| FollowingUiSourceTest | 15 / 0 / 0 | 15 / 0 / 0 |
| HistoryPlaybackTest | 66 / 0 / 0 | 66 / 0 / 0 |
| PlaybackProgressWriterEpisodeTest | 15 / 0 / 0 | 15 / 0 / 0 |
| HistoryScopeSyncTest | 7 / 0 / 0 | 7 / 0 / 0 |

（格式：tests / failures / errors。日志 .codex/tmp-c112-fix2-test-mobile.log、.codex/tmp-c112-fix2-test-leanback.log。）

### 合并语义维度的契约测试

HistorySeasonDeleteScopeTest 1、HistoryScopeSyncTest 7、VodApiTest 39 —— 全部零失败（.codex/tmp-c112-test-scope.log）。

## 6. 已知边界与残留风险

1. **F-07 已补齐但覆盖仍有边界**：运行时落库断言已由第 7 节的仪器测试提供（失败后读库断言 playable*/officialStatus 未变、failureCount/lastError/nextCheckAt 已记账），但仅有「TMDB 失败 + 探针抛异常」这一种组合；`probeBestEffort` 的 `required=true` 分支与 `FollowingStore.project` 异步投影失败路径仍无仪器覆盖。
2. **零可播集数纳入失败后触发退避**：长期探不出集号的源会稳定停留在失败态（failureCount 递增、nextCheckAt 后退），直到探出集数。这是把此前的静默成功「诚实化」的必然结果。
   - 两条失败路径的记账**刻意不对称**：TMDB 不可用 + 零可播走 `markFailure`（failureCount+1 并退避）；TMDB 正常 + 探针零可播只置 `lastError`，本次检查仍按成功收敛（`failureCount` 归零、正常周期）。理由是后者官方数据与来源绑定都还在，记录不该因一次「本季还没出集」就长期退避。
3. **失败分支 reconcile 若抛异常**：会被 check() 外层 catch (Throwable) 接住再 markFailure 一次，导致 failureCount 双计。这是既有结构问题（正常路径同样如此），本轮未改以守住边界。
4. **跨季归属二义性**：seasonMatches 对 -1 全放行，无追更记录的 project(history) 直投影路径在同一 tmdbId 多季时仍会归到 season 0（与修复前一致）。
5. **watchedSeason 由 0 变为真实季号**：已 grep 全部消费点确认无 UI 依赖 0 表示未知，但若将来新增依赖需注意。


## 7. F-07 仪器测试（数据库 DAO 级证据）

Spec 轴第 2 轮评审的唯一阻塞项：`docs/FOLLOW-1-following-updates-design.md:321` 的 F-07 要求「手动检查失败保留旧快照 | 异常单测 + 数据库 DAO 测试」，而此前补的 3 个测试类均为源码文本断言，无法证明失败后数据库中的快照行未被改写。

### 补齐方式

在既有 `app/src/androidTest/java/com/fongmi/android/tv/following/FollowingUpdateCoordinatorDeviceTest.java` 追加 1 例 `failureKeepsPreviousSourceAndOfficialSnapshot()`（+45 行，含 1 个 import），复用该类既有的 `FollowingDeviceDataRule` 真库快照/还原规则与构造器注入能力，未改动主代码、未引入新依赖。

测试构造：

1. 先给库中的 preferred source 种上旧快照：`playableSeason=1`、`playableEpisode=8`、`playableCount=8`、`lastProbeAt=1000L`、`lastError=""`，`FollowingStore.updateSource(seeded)` 落库。
2. 双失败桩：`FollowingMetadataClient.fetch` 抛 `IOException("TMDB unavailable")`；`FollowingSourceProbe.probe` 先 `probes.incrementAndGet()` 再抛 `IllegalStateException("来源没有可播放线路")`。
3. `assertFalse(new FollowingUpdateCoordinator(metadata, probe).check(item, true, false));` —— 用三参版 `check(item, manual, foreground)`，`foreground=false` 避开 `App.isForeground()` 依赖。
4. **读库断言**（这是 F-07 要求的数据库 DAO 级证据）：
   - `source.playableSeason == 1`、`source.playableEpisode == 8`、`source.playableCount == 8`（来源侧旧快照未被清空/归零）
   - `stored.officialStatus == RETURNING`、`stored.latestReleasedEpisode == 6`（官方快照未被降级覆盖）
   - `stored.failureCount == 1`、`stored.lastCheckedAt >= before`、`stored.lastError` 非空、`stored.nextCheckAt > before`（失败态确实记账并退避）
   - `probes.get() == 1`（手动检查 `manual=true` 使 `FollowingSettings.shouldProbe` 为真、探针确实执行，排除 `NOT_RUN` 的可能）
   - `source.lastError.equals("来源没有可播放线路")`（只有 `probeBestEffort` 的 catch 分支会把异常消息写入 `source.lastError` 并经 `FollowingStore.updateSource` 落库，证明「探针抛异常」路径被真实覆盖）

### 执行方式与结果

按仓库既有约束**不使用 `connectedAndroidTest`**（AGP 跑完会卸载被测应用并清空用户数据，见 `docs/NOVEL-TTS-novel-read-aloud.md:189-192` 与 `docs/C52-beta-merge-review-dev1-20261009.md:14`），改用 assemble + `adb install -r` + `am instrument`。

```
.\gradlew.bat :app:assembleMobileArm64_v8aDebug :app:assembleMobileArm64_v8aDebugAndroidTest --console=plain
BUILD SUCCESSFUL in 3m 17s（161 actionable tasks: 21 executed, 140 up-to-date）

adb -s emulator-5556 install -r app\build\outputs\apk\mobileArm64_v8a\debug\app-mobile-arm64_v8a-debug.apk
Success
adb -s emulator-5556 install -r app\build\outputs\apk\androidTest\mobileArm64_v8a\debug\app-mobile-arm64_v8a-debug-androidTest.apk
Success

adb -s emulator-5556 shell am instrument -w \
  -e class com.fongmi.android.tv.following.FollowingUpdateCoordinatorDeviceTest \
  com.silent.android.webhtv.test/androidx.test.runner.AndroidJUnitRunner
com.fongmi.android.tv.following.FollowingUpdateCoordinatorDeviceTest:..
Time: 0.771
OK (2 tests)
```

`OK (2 tests)` 由协调者独立复跑一次确认（第二次 Time: 0.771），非仅采信子代理自报。相邻类 `FollowingProjectionDeviceTest` 同步复跑 `OK (1 test)`，无回归。

### 对 F-07 的兑现说明

- 「异常单测」：`FollowingUpdateCoordinatorErrorTest`（9 例，含 `probeThatYieldedNoPlayableEpisodeCountsAsFailure`、`zeroPlayableEpisodeFallsBackToTheGenericFailureText`、`sourceCheckFailedIgnoresBlankAndTmdbDegradedText`）。
- 「数据库 DAO 测试」：本节仪器测试，失败分支执行后**把 Following 与 FollowingSource 从 Room 读回**断言快照未变、失败态已记账。
- 两条失败路径均被覆盖：TMDB provider 失败（`metadataError != null`）与来源探针抛异常（`source.lastError` 落库为探针异常消息）。
- 行为级断言取代了原先的源码文本断言作为主证据；源码契约断言保留为「防回退」的廉价守卫。

### 环境注意（后续复用）

- `run_code` 前台有 120s 执行上限，gradle 必须以后台 job 启动再轮询，否则会被截断。
- coordinator 在仪器进程内会真实调用 `FollowingScheduler.scheduleNext(App.get(), item)`（WorkManager enqueue），`tearDown` 的 `cancelNext` 是必要清理；测试不依赖调度时刻，两条时序断言均为宽松不等式，无 sleep 依赖。
- 依赖 `emulator-5556` 环境；换设备时后台 WorkManager 任务可能干扰 instrument 结果（既有风险）。

## 8. 改动清单

相对 origin/beta（合并 + 修复后的完整净差异）：

- app/src/main/java/com/fongmi/android/tv/bean/History.java
- app/src/main/java/com/fongmi/android/tv/following/FollowingStore.java
- app/src/main/java/com/fongmi/android/tv/following/FollowingUpdateCoordinator.java
- app/src/main/java/com/fongmi/android/tv/following/FollowingUpdateWorker.java
- app/src/main/java/com/fongmi/android/tv/playback/PlaybackProgressWriter.java
- app/src/main/java/com/fongmi/android/tv/ui/activity/FollowingActivity.java
- app/src/main/java/com/fongmi/android/tv/ui/adapter/FollowingAdapter.java
- app/src/main/res/values/strings.xml / values-zh-rCN/strings.xml / values-zh-rTW/strings.xml
- app/src/test/java/com/fongmi/android/tv/bean/HistoryPlaybackTest.java
- app/src/test/java/com/fongmi/android/tv/following/FollowingUpdateCoordinatorErrorTest.java
- app/src/test/java/com/fongmi/android/tv/following/FollowingSeasonProjectionTest.java（新增）
- app/src/test/java/com/fongmi/android/tv/following/FollowingFailureSnapshotContractTest.java（新增）
- app/src/test/java/com/fongmi/android/tv/following/FollowingFailureReconcileContractTest.java（新增）
- app/src/test/java/com/fongmi/android/tv/playback/PlaybackProgressWriterEpisodeTest.java
- app/src/androidTest/java/com/fongmi/android/tv/following/FollowingUpdateCoordinatorDeviceTest.java（追加 F-07 失败分支用例）
- docs/FOLLOW-3-tmdb-following-match-design.md
- docs/C112-beta-merge-review-dev2-20261029.md（本文件）