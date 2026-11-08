# FOLLOW-3 追更 TMDB 匹配设计（手动匹配 + 严格自动兜底）

> 状态：设计稿，待评审；本文只定义方案与验收标准，不代表已获准实施
> 任务编号：FOLLOW-3-TMDB-MATCH-20261029
> 设计日期：2026-10-29
> 评估基线 commit：dev2 @ 3639181e3e9618f15738c95b1f1603447aa289e4（merge origin/beta ba802dc432 之后的合并提交）
> 参考版本：WebHTV 4.x（following 模块 v3 schema，FollowingDatabase VERSION=3）
> 适用范围：追更（following）页面与追更更新链路；不改变播放、详情页、探针、DAO 语义
> 目标读者：实现该功能的开发者、验收者、代码评审者

---

## 0. 结论摘要

追更卡片的「官方已更新至第 N 集」目前只依赖 `Following.tmdbId > 0`。`Following.tmdbId` 共有 6 处写入路径（见 2.1），但其中只有播放页 `stampHistoryTmdbId()` 与 `History.enrichTmdbId()` 这两条会**主动补盖**身份，且都被 `Setting.isHistoryAggregationEffective()` 这一道门闸拦住；其余四条只搬运上游已有的 tmdbId 或依赖外部数据（Alist 订阅、备份/同步合并），都不经该门闸，也无法在日常原生站源路径上凭空造出身份。因此在「未配置 TMDB / 关闭『TMDB 聚合』/ 自动匹配未命中」三种常见情况下，原生模式追更的剧集永远显示「官方状态未知」，点「检查」也不会改变——这是设计缺环，不是缺陷。

本设计补齐这条缺环，采用**手动匹配为主、严格自动匹配为辅**的双通道方案：

1. **手动匹配（阶段 A，必做）**：追更卡片「官方状态未知」行可点击，卡片详情弹窗新增「匹配 TMDB」项；复用现成 `TmdbSearchDialog` 搜索、`TmdbMatcher` 预填查询、`FollowingStore.resolveTmdb` 迁移身份，选中后立即刷新官方状态。
2. **严格自动兜底（阶段 B，默认关闭）**：新增设置开关「自动匹配官方状态」，仅在**用户手动点「检查」**且该条 `tmdbId <= 0` 时，用**严格匹配**（`TmdbMatcher.chooseStrictMatch`：标题归一化后完全一致 + 年份同年，且不含「被拆分的季条目」）尝试一次；命中则自动迁移身份；未命中保持原状。

不做「智能模式自动写入」「批量匹配全部」「放宽 `isHistoryAggregationEffective` 默认」。误匹配会把官方集数写错，并连带 `unwatchedCount`、角标、通知一起错，比没有官方状态更糟，因此默认保守。

### 0.1 决策摘要

| 决策 | 结果 | 原因 |
| --- | --- | --- |
| 是否要做 | **做** | 官方状态是追更页的核心信息，缺环可低成本补齐 |
| 主入口 | 手动匹配（卡片官方行点击 + 详情弹窗项） | 用户确认语义、零误写风险、复用现成组件 |
| 自动匹配 | 做，但**严格模式 + 默认关闭 + 仅手动检查触发** | 严格控制误匹配与请求放大 |
| 智能模式自动写入 | **不做** | 智能匹配放宽到「去年份后相等/年份差<=1」，追更侧无法二次确认 |
| 是否新增 Room 字段/表/迁移 | **不新增** | `resolveTmdb` + `TmdbMatchCache` 已足够；失败原因复用 `Following.lastError` |
| 是否修改 `FollowingSourceProbe` / `FollowingDao` 季号匹配 / `isHistoryAggregationEffective` | **不修改** | 与 FOLLOW-1/FOLLOW-2 已定契约解耦，避免连带回归 |
| 是否新增卡片按钮 | **不新增** | mobile `FlexboxLayout` + `following_action_basis` 换行会被打破，且有现成点击目标 |
| 匹配结果落盘 | 同时写 `TmdbMatchCache.putManual` | 与详情页/播放页共享绑定，且手动条目不会被后续自动匹配覆盖 |

---

## 1. 背景与目标

### 1.1 用户可见问题

追更卡片现有 5 行信息（`item_following.xml`）：官方更新 / 本季与全剧集数 / 当前源可播集数 / 观看进度 / 未看提醒。第一行在未关联 TMDB 时恒为「官方状态未知」（`following_official_unknown`），而用户在原生站源模式（不经过 TMDB 详情页）添加追更时几乎必然处于该状态：

- 用户未配置 TMDB 账号/代理 → `Setting.isTmdbReady() == false`；
- 用户配置了 TMDB 但关闭了「TMDB 聚合」（`history_aggregation_by_tmdb`）→ `isHistoryAggregationEffective() == false`；
- 用户在原生播放页从站源直接开播并加追更，`stampHistoryTmdbId()` 拿不到 `mTmdbUIAdapter.getTmdbItem()` / Intent extra `tmdbItem` → 不盖章；
- `History.enrichTmdbId()` 的兜底依赖 `TmdbMatchCache.find` 命中缓存，未命中则不补。

### 1.2 目标

| 编号 | 目标 | 可观测结果 |
| --- | --- | --- |
| G1 | 用户能为任意一条追更手动指定 TMDB 条目 | 选中后卡片首行变成「官方已更新至第 N 集」 |
| G2 | 能指定「跟随哪一季」 | 「官方已更新至第 N 集」的集数来自用户选的季 |
| G3 | 可在检查链路里低成本尝试自动关联 | 开启开关后，点「检查」能自动补上明显可判定的条目 |
| G4 | 匹配失败必须可见 | 卡片出现 `lastError` 提示行，可在「检查失败」筛选里看到 |
| G5 | 任何情况下不得吞掉用户已有的未看集数/已读水位 | 匹配前后 `watchedEpisode`、`unwatchedCount` 语义不变 |

### 1.3 非目标（明确不做）

- 不修改 `FollowingSourceProbe`（chooseFlag / matchesSeason / episodeNumber）与「不自动换线路」的既有决定。
- 不修改 `FollowingDao.findByTmdb / findBySource` 的季号精确匹配规则，不修改 `FollowingStore.projectHistory` 的投影语义。
- 不修改 `Setting.isHistoryAggregationEffective()` 默认值，不修改 `stampHistoryTmdbId()` / `enrichTmdbId()`。
- 不在追更页做 TMDB 详情页（第几集剧情、演员等）。
- 不做「一键匹配全部未关联追更」（阶段 C，暂缓）。
- 不做 TMDB 条目人工改绑之外的任何自动迁移（例如按 History 反推）。

---

## 2. 现状取证（基线 HEAD 3639181e3e9）

> 本文行号以该提交为准；`FollowingStore` / `FollowingUpdateCoordinator` / `FollowingAdapter` / `FollowingActivity` / `FollowingUpdateWorker` 五个文件正在被并行编辑、行号会漂移，因此对它们一律以符号名引用，不写行号。

### 2.1 官方状态为何为空（根因链）

```
FollowingAdapter.onBindViewHolder (ui/adapter/FollowingAdapter.java)
  released = FollowingUpdatePolicy.releasedEpisode(item)
  released > 0 ? following_official : following_official_unknown
        ↑
FollowingUpdatePolicy.releasedEpisode (following/FollowingUpdatePolicy.java:8-13)
  latestReleasedSeason == trackedSeason ? latestReleasedEpisode : seasonReleasedEpisodes
        ↑ 全部来自 FollowingMetadataSnapshot
FollowingUpdateCoordinator.check (following/FollowingUpdateCoordinator.java)
  if (item.tmdbId > 0) metadataClient.fetch(item, manual)   ← tmdbId<=0 时完全不拉官方数据
        ↑
FollowingMetadataClient.fetch (following/FollowingMetadataClient.java:32)
  if (item.tmdbId <= 0 || !Setting.isTmdbReady()) return null;
        ↑
Following.tmdbId 的真实写入路径共 6 处（均不检查 Setting.isHistoryAggregationEffective()）：
  FollowingPlaybackBridge            item.tmdbId = history.getTmdbId();                                  （播放页加追更，取自 History）
  TmdbDetailActivity.addFollowing    item.tmdbId = tmdb == null ? 0 : tmdb.getTmdbId();                  （详情页加追更，:11968）
  AlistSubscriptionImporter          candidate.tmdbId = parseProviderId(object, "tmdb"); item.tmdbId = candidate.tmdbId;  （Alist 订阅导入，:101/:151）
  FollowingMergePolicy.mergeOne      result.tmdbId = remote.tmdbId > 0 ? remote.tmdbId : local.tmdbId;    （备份/同步合并，:59）
  FollowingStore.resolveTmdb         migrated.tmdbId = tmdb.getTmdbId();                                 （身份迁移）
  Following.copy()（Following.java:154） item.tmdbId = tmdbId;                                            （拷贝既有 tmdbId）
上游只有 2 处会主动补盖身份，且都被门闸拦住：
  mobile/VideoActivity.java:1422-1431 stampHistoryTmdbId()  守卫 !Setting.isHistoryAggregationEffective()
  leanback/VideoActivity.java:6265   stampHistoryTmdbId()  同一守卫
  bean/History.java:1170-1180        enrichTmdbId()        同一守卫（守卫在 :1171）
        ↑
Setting.isHistoryAggregationEffective() (setting/Setting.java:1401-1403)
  return isHistoryAggregationByTmdb();   // 语义见 :1393-1395
  isTmdbReady() && Prefers.getBoolean("history_aggregation_by_tmdb", true)
```

结论：**tmdbId 为空时整套官方状态链路整体短路，且没有任何用户可达的补救入口**。这是本设计的唯一要补的缺环。

### 2.2 可复用资产（不重复造轮子）

| 能力 | 现成实现 | 关键约束 |
| --- | --- | --- |
| 搜索 + 严格/智能打分 | `ui/helper/TmdbMatcher.java:60-95,130-198` | `chooseTmdbMatch` 先严格（:136-137），再含年份（:138），`!Setting.isTmdbSmartMatch()` 时到此为止（:139） |
| 严格阈值参考（**另一套实现，不要与之混用**） | `ui/activity/TmdbDetailActivity.java:12654-12690` | 那是「详情页 bundle 级」严格匹配：标题完全一致 + 年份同年，且 `score >= 360 && gap >= 50`。追更侧复用 `TmdbMatcher.chooseStrictMatch`（:143-155），它的判据是**标题 `compareKey` 完全相等 + 年份同年 + `isUnwantedSplitSeasonMatch` 过滤**，不使用 360/50 打分 |
| 搜索对话框 | `ui/dialog/TmdbSearchDialog.java` | `create(Activity)` 即可，`FollowingActivity` 是 Activity，可直接用 |
| 季选择对话框 | `ui/dialog/ChoiceDialog.java:94-127 / :155` | `showTmdbSeason` 只接受 `FragmentActivity`；`showSingle(FragmentManager, ...)` 可用于 `AppCompatActivity` |
| 身份迁移（含墓碑守卫/来源绑定迁移/合并） | `following/FollowingStore.resolveTmdb` / `selectMigrationSource` / `migrateSourceToTmdb`（`FollowingStore.java`，按符号名定位）+ `FollowingPlaybackBridge.resolveTmdbAsync` (:134-145) | 只在 `tmdb.mediaType == tv` 且 `tmdbId > 0` 时生效；目标 identityKey 是墓碑时**返回该墓碑行本身**（不复活、不迁移），调用方必须用 `isDeleted()` 判定失败 |
| 匹配缓存 + 手动锁定 | `bean/TmdbMatchCache.java:102-127 / :135-154` | `put()` 遇 `isManual` 直接 return；`putManual()` 写 manual 条目+别名 |
| 官方状态抓取 | `following/FollowingMetadataClient.java:30-89` | `detailForFollowing`（30 分钟 TTL）+ `season`（3 天 TTL） |
| TMDB 模式开关 | `setting/Setting.java:1121-1139` | `isTmdbSmartMatch()` / `isTmdbMatchDialog()`，追更侧目前未消费 |

### 2.3 已逐字核对的调用签名（实现时直接照抄）

| API | 签名 | 位置 |
| --- | --- | --- |
| 搜索对话框 | `static TmdbSearchDialog create(Activity)`；链式 `title/items/query/selectedItem/listener/searchListener/skipListener` 返回 this；`show()`、`loading()`、`updateItems(List<TmdbItem>)` 返回 **void** | `ui/dialog/TmdbSearchDialog.java:56/66-96/101/124/130` |
| 选择对话框 | `static void showSingle(FragmentManager, CharSequence title, CharSequence[] items, int selected, OnChoice)`；`interface OnChoice { void onChoice(int which); }` | `ui/dialog/ChoiceDialog.java:65-67/155` |
| 匹配器 | `public TmdbMatcher(TmdbService, TmdbConfig)`；`public List<TmdbItem> search(String) throws Exception`；`private TmdbItem chooseStrictMatch(List<TmdbItem>, String keyword, Vod, int sourceYear)` | `ui/helper/TmdbMatcher.java:37/112/143` |
| TMDB 服务 | `new TmdbService()`；`public JsonObject detail(TmdbItem, TmdbConfig)`；`public JsonObject season(TmdbItem, int, TmdbConfig)` | `service/TmdbService.java:120/197` |
| 配置 | `TmdbConfig.effectiveCurrent()`；`TmdbConfig.isReady()`；`Setting.isTmdbReady()` | `bean/TmdbConfig.java:109/354`、`setting/Setting.java:991` |
| 语言 | `TmdbLanguagePolicy.requestLanguage(TmdbConfig, int uiLanguage)` + `Setting.getLanguage()`（返回 **int**） | `utils/TmdbLanguagePolicy.java:58`、`setting/Setting.java:415` |
| 存储 | `FollowingStore.find(String)`、`update(Following)`、`resolveTmdb(TmdbItem, int, int, String, String, FollowingMetadataSnapshot)`、`preferredSource(String)`、`list()` | `following/FollowingStore.java`（按符号名定位：`find` / `update` / `resolveTmdb` / `preferredSource` / `list`） |
| 元数据 | `public FollowingMetadataClient()`；`fetch(Following, boolean refresh)` | `following/FollowingMetadataClient.java:22/30` |
| 实体 | `Following.copy()`；`TmdbItem.isTv()` | `following/Following.java:154`（`copy()` 方法体在 :154，`item.tmdbId = tmdbId;` 在 :164）、`bean/TmdbItem.java:140` |
| 季解析参考 | `private Map<Integer,Integer> seasonEpisodeCounts(JsonObject)` / `private int firstSeasonNumber(JsonObject)` / `addSeasonCount(Map, JsonElement, boolean regularOnly)`（均为 private，需复制实现到新服务） | `ui/activity/TmdbDetailActivity.java:13220/13195/13228` |

### 2.4 现有测试对改动的硬约束

`app/src/test/java/com/fongmi/android/tv/ui/activity/FollowingUiSourceTest.java` 是源码字符串断言，**以下断言必须继续通过**：

- `:177` `for (Following item : items) success |= coordinator.checkNow(item.identityKey, true);` 逐字匹配 → 不得改动 `checkAll()` 的这一行。
- `:232-244` 详情弹窗必须仍包含 `following_continue` / `following_check` / `following_read` / `following_change_source` / `following_cancel`（**不校验项数**，因此可扩为 6 项）。
- `:273-348` `followingActivity` 不得含 `FollowingStore.delete(item.identityKey)`；必须走 `FollowingPlaybackBridge.deleteAsync`；`store` 必须含 `history == null || !FollowingSettings.isEnabled()` 与 `migrateSourceToTmdb`。
- `:44-60` mobile `item_following.xml` 的 FlexboxLayout 断言与 7 个既有按钮 id 必须全部存在 → 本设计**不新增卡片按钮**。
- `:246-271` leanback 布局 `check → readAll → filter` 的 nextFocus 链必须保持。

其它基线：`FollowingSourceProbeTest` 6 用例、`FollowingUpdateCoordinatorDeviceTest`（含 `assertEquals("TMDB不可用，已回退原站", stored.lastError)`、`assertEquals(8, source.playableEpisode)`、`assertEquals(1, probes.get())`）、`FollowingProjectionDeviceTest`、`FollowingMigrationDeviceTest`（已覆盖 `resolveTmdb` 迁移路径）必须保持通过。

---

## 3. 方案对比

### 3.1 候选方案

| 方案 | 做法 | 优点 | 缺点/风险 | 结论 |
| --- | --- | --- | --- | --- |
| A. 不改 | 保持现状，官方状态只在 TMDB 详情页链路可用 | 零风险 | 用户问题不解决 | 否 |
| B. 手动匹配 | 追更页提供 TMDB 搜索选择，选中后 `resolveTmdb` 迁移身份 | 语义确定、零误写、复用度最高 | 需要用户操作 | **采用（阶段 A）** |
| C. 严格自动兜底 | 检查时按严格匹配尝试一次，命中即迁移 | 惯例条目全自动补齐 | 需要在检查链路引入网络搜索；阈值外的条目仍失败 | **采用（阶段 B，默认关）** |
| D. 智能模式自动写入 | 直接走 `chooseSmartMatch` | 命中率更高 | 一旦误配，官方集数/未看数/角标/通知全错，且用户难以察觉 | 否 |
| E. 放宽 `isHistoryAggregationEffective` 默认 | 让盖章不再依赖聚合开关 | 改动最小 | 改动全局历史语义，影响范围远超追更 | 否 |

### 3.2 关键权衡

1. **误匹配代价 > 缺失代价**。`releasedEpisode` 会驱动 `FollowingUpdatePolicy.refreshDerived`（`unwatchedCount`、`hasUpdate`、角标、通知文案）。因此自动通道只允许「严格匹配」，且默认关闭。
2. **`tmdbId` 一旦写入是粘性的**。`FollowingMetadataClient.fetch` 与 `FollowingUpdateCoordinator` 都以 `tmdbId > 0` 为分叉点，一旦错写，后续「检查」会持续拉错的官方数据。所以正确性优先级高于自动化率。
3. **不新增字段**。失败原因写入既有 `Following.lastError`（`Following.java:142`），复用 `FILTER_FAILED` 筛选与错误行，避免 `FollowingDatabase` 4.0 迁移。
4. **不改季号语义**。FOLLOW-1/FOLLOW-2 已把「`trackedSeason` 精确匹配」定为契约，本设计的季号由用户在匹配时显式确认，恰恰强化该契约。

---

## 4. 功能需求分解

| 编号 | 功能 | 阶段 | 触发点 |
| --- | --- | --- | --- |
| F1 | 入口：卡片「官方状态未知」行可点击；详情弹窗新增「匹配 TMDB」项 | A | 用户点击 |
| F2 | 搜索对话框：预填站源剧名、后台搜索、结果列表、选中回调、空结果提示 | A | F1 之后 |
| F3 | 季选择：多季时弹季列表，单季直接采用，0 季（电影/无季）拒绝 | A | F2 选中后 |
| F4 | 迁移与刷新：`resolveTmdb` 迁移身份 + 保护已读水位 + 写 `TmdbMatchCache.putManual` + 立即检查一次 + 刷新列表 | A | F3 之后 |
| F5 | 失败可见：非 tv 条目、迁移失败、TMDB 未配置、搜索异常 → 用户可见提示 + `lastError` 落库 | A | 任意失败分支 |
| F6 | 严格自动兜底：设置开关 + 手动检查时的一次严格匹配尝试 | B | `checkNow(id, true)` |
| F7 | 详情弹窗引导行：未关联时提示可匹配 | A | 打开弹窗 |
| F8 | 批量匹配全部未关联条目 | C（暂缓） | — |

---

## 5. 详细设计

### 5.1 新增类：`FollowingTmdbMatchService`

路径：`app/src/main/java/com/fongmi/android/tv/following/FollowingTmdbMatchService.java`

职责：把「搜索 / 取季清单 / 迁移」三件事收成一个可单测的深模块，Activity 只做 UI 编排。

```java
public class FollowingTmdbMatchService {
    public FollowingTmdbMatchService();                       // 内部 new TmdbService()
    FollowingTmdbMatchService(TmdbService service, TmdbConfig config); // 包内，供单测注入

    /** 站源剧名 -> 预填查询串（清洗季/年份噪声，交给既有 matcher 的 cleanVideoName）。 */
    public String suggestQuery(String vodName);

    /** 后台搜索：返回候选（已按 TMDB 语言偏好排序），异常时抛 TmdbService.AuthException 等。 */
    public List<TmdbItem> search(String keyword) throws Exception;

    /** 取该条目的季号清单与每季集数（LinkedHashMap，保持 TMDB 顺序）；非 tv 或空季返回空 Map。 */
    public Map<Integer, Integer> seasons(TmdbItem item) throws Exception;

    /** 落库：保护已读水位 -> 拉一次追更详情快照 -> resolveTmdb 迁移 -> 写手动绑定。 */
    public Following resolve(Following source, TmdbItem target, int season) throws Exception;
}
```

要点：

- `search`：`new TmdbMatcher(tmdbService, tmdbConfig).search(keyword)`（`TmdbMatcher.java:112`），已内置「清洗后为空则用原文再搜 + `sortSearchResults`」。**只保留 `item.isTv()` 的条目**（追更只支持剧集；`FollowingMetadataClient` 只解析 tv 结构）。
- `seasons`：`tmdbService.detail(item, config)`（`TmdbService.java:120`，7 天 TTL）→ 解析 `seasons[]`；参照 `TmdbDetailActivity.seasonEpisodeCounts(JsonObject)`（:13220）与 `firstSeasonNumber(JsonObject)`（:13195）：跳过 `season_number < 0`，`episode_count` 缺失记 0，保持插入顺序。
- `resolve` 的步骤顺序**不可调换**：
  1. 守卫：`target == null || target.getTmdbId() <= 0 || !target.isTv() || season < 0` → 抛 `IllegalArgumentException`。
  2. **水位保护**（见 5.4）：若 `source.readWatermarkEpisode <= 0 && source.watchedEpisode > 0`，写一份 `readWatermarkEpisode = source.watchedEpisode`、`lastNotifiedEpisode = max(source.lastNotifiedEpisode, source.watchedEpisode)` 的副本并 `FollowingStore.update(copy)`（在调用线程写，Room DAO 调用；与 `FollowingStore.markRead` 同类）。
  3. 构造探针行取快照：`Following probe = source.copy(); probe.tmdbId = target.getTmdbId(); probe.mediaType = target.getMediaType(); probe.trackedSeason = season;` 然后 `FollowingMetadataSnapshot snapshot = new FollowingMetadataClient().fetch(probe, true);`（失败返回 null，不致命）。
  4. `Following migrated = FollowingStore.resolveTmdb(target, season, source.cid, source.siteKey, source.vodId, snapshot);`（`FollowingStore.resolveTmdb`，按符号名定位）。真实返回语义见 6.2：`null` 只表示「非法参数（`tmdb == null` / `getTmdbId() <= 0` / 媒体类型不是 tv）」，目标行是墓碑时返回的是**该墓碑行本身**；因此本步骤的失败判据必须是 `migrated == null || migrated.isDeleted()`，为真则抛出「目标追更已取消」。
  5. **手动绑定落盘**：`synchronized (Setting.class) { TmdbMatchCache cache = Setting.getTmdbMatchCache(); cache.putManual(source.siteKey, source.vodId, List.of(source.vodName), target, TmdbLanguagePolicy.requestLanguage(tmdbConfig, Setting.getLanguage())); Setting.putTmdbMatchCache(cache); }`（参照 `TmdbDetailActivity.saveManualTmdbMatch` :3805-3813）。**必须在迁移后写**，因为 `putManual` 用源站剧名作别名，与 `resolveTmdb` 无耦合。
  6. 返回 `migrated`。

> 链式 API 注意：`TmdbSearchDialog.title/query/items/listener/searchListener/skipListener` 均返回 `TmdbSearchDialog`，但 `show()`、`loading()`、`updateItems(...)` 返回 `void`（`TmdbSearchDialog.java:101/124/130`），因此必须先持有实例再分别调用，不能把 `show().loading()` 串起来。

> 为什么不用 `FollowingPlaybackBridge.resolveTmdbAsync`：它是 `Task.execute + App.post` 的薄封装（:134-145），内部直接调用　`FollowingStore.resolveTmdb`。本服务已在后台线程（`Activity` 侧 `Task.execute`），因此直接调 `FollowingStore.resolveTmdb` 以减少一层线程与回调嵌套；两者语义完全一致。若实现者偏好统一入口，允许改调 `resolveTmdbAsync` 并把步骤 5、6 放进回调，但**必须在回调里重新 `load()`**。

### 5.2 入口一：详情弹窗新增「匹配 TMDB」项

文件：`app/src/main/java/com/fongmi/android/tv/ui/activity/FollowingActivity.java`，`onOpenDetail`（按符号名定位）。

改动（保持 which 0..4 语义不变）：

```java
String[] actions = new String[]{
        getString(R.string.following_continue),
        getString(R.string.following_check),
        getString(R.string.following_read),
        getString(R.string.following_change_source),
        getString(R.string.following_cancel),
        getString(R.string.following_match_tmdb)          // 新增第 6 项
};
// setItems 分派：which == 5 -> onMatchTmdb(item)
```

同时在 message 末尾追加引导行（F7）：当 `item.tmdbId <= 0` 时 `message.append("\n").append(getString(R.string.following_tmdb_unmatched_hint));`。

> 既有断言只检查五个既有字符串是否存在，不检查项数与顺序，扩为 6 项安全。

### 5.3 入口二：卡片「官方状态未知」行点击

文件：`app/src/main/java/com/fongmi/android/tv/ui/adapter/FollowingAdapter.java` 的 `onBindViewHolder` 与 `FollowingAdapter.Listener`（均按符号名定位）。

- `Listener` 新增 `void onMatchTmdb(Following item);`（第 9 个回调）。
- `onBindViewHolder` 内，官方行（`binding.official`）绑定：

```java
if (item.tmdbId <= 0) {
    holder.binding.official.setOnClickListener(v -> listener.onMatchTmdb(item));
} else {
    holder.binding.official.setOnClickListener(null);
    holder.binding.official.setClickable(false);
}
```

- `FollowingActivity implements FollowingAdapter.Listener` 需实现 `onMatchTmdb`，直接转调 5.5 的 `onMatchTmdb(item)`。
- 不新增按钮、不改 `item_following.xml`，因此 FlexboxLayout 换行与既有断言不受影响。

### 5.4 水位保护（关键正确性约束）

`FollowingUpdatePolicy.applyMetadata`（:26-67）在 **firstMetadata** 成立时把 `readWatermarkEpisode`、`lastNotifiedEpisode` 直接抬到 `releasedEpisode`（:59-63）：

```java
boolean firstMetadata = item.metadataUpdatedAt <= 0 && item.readWatermarkEpisode <= 0 && item.lastNotifiedEpisode <= 0;
```

一条刚被手动匹配的追更行通常满足这三条 → 首次元数据会**一键吞掉全部历史未看集数**（`unwatchedCount` 变 0、`hasUpdate` 变 0）。这是本设计最容易引入的回归。

规避方式（5.1 步骤 2）：匹配前把 `readWatermarkEpisode` 预置为用户已看到的集数（语义上「看到第 N 集 ⇒ 前 N 集已读」，与 `markRead` 等价），从而 `firstMetadata == false`。该预置是幂等的，且只在 `readWatermarkEpisode <= 0 && watchedEpisode > 0` 时执行。

验收要求：见 AC-9；不得以「先匹配再手动标记已读」的方式绕过。

### 5.5 Activity 编排

```java
private void onMatchTmdb(Following item) {
    if (item.tmdbId > 0) { Notify.show(R.string.following_tmdb_already_matched); return; }
    if (!Setting.isTmdbReady()) { Notify.show(R.string.following_tmdb_unavailable); return; }
    matchService = new FollowingTmdbMatchService();
    String keyword = matchService.suggestQuery(item.vodName);
    TmdbSearchDialog dialog = TmdbSearchDialog.create(this)
            .title(getString(R.string.detail_tmdb_match_title))
            .query(keyword)
            .listener(target -> onTmdbPicked(item, target))
            .searchListener(this::searchTmdbForFollow);
    dialog.show();                       // show() 返回 void，不可链式
    dialog.loading();                    // 首屏进入 loading 态
    searchTmdbForFollow(keyword, dialog); // 首屏立即发起搜索
}

private void searchTmdbForFollow(String keyword, TmdbSearchDialog dialog) {
    int generation = ++matchSearchGeneration;
    Task.execute(() -> {
        List<TmdbItem> items;
        try {
            items = matchService.search(keyword);
        } catch (Exception e) {
            items = new ArrayList<>();
        }
        List<TmdbItem> result = items;
        App.post(() -> {
            if (generation != matchSearchGeneration) return;   // 丢弃过期结果
            dialog.updateItems(result);
            if (result.isEmpty()) Notify.show(R.string.detail_tmdb_empty);
        });
    });
}

private void onTmdbPicked(Following item, TmdbItem target) {
    if (!target.isTv()) { Notify.show(R.string.following_tmdb_not_tv); return; }
    Task.execute(() -> {
        Map<Integer, Integer> seasons;
        try {
            seasons = matchService.seasons(target);
        } catch (Exception e) {
            App.post(() -> Notify.show(R.string.following_tmdb_match_failed));
            return;
        }
        App.post(() -> chooseSeasonThenResolve(item, target, seasons));
    });
}

private void chooseSeasonThenResolve(Following item, TmdbItem target, Map<Integer, Integer> seasons) {
    List<Integer> numbers = new ArrayList<>(seasons.keySet());
    if (numbers.isEmpty()) { Notify.show(R.string.following_tmdb_not_tv); return; }
    if (numbers.size() == 1) { resolveMatch(item, target, numbers.get(0)); return; }
    int selected = numbers.indexOf(item.trackedSeason);                 // 优先与当前追更季一致
    if (selected < 0) selected = 0;
    CharSequence[] labels = new CharSequence[numbers.size()];
    for (int i = 0; i < labels.length; i++) {
        int season = numbers.get(i);
        labels[i] = season == 0
                ? getString(R.string.tmdb_season_special, seasons.get(season))
                : getString(R.string.tmdb_season_option, season, seasons.get(season));
    }
    ChoiceDialog.showSingle(getSupportFragmentManager(), getString(R.string.tmdb_season_match_title),
            labels, selected, which -> resolveMatch(item, target, numbers.get(which)));
}

private void resolveMatch(Following item, TmdbItem target, int season) {
    binding.loading.setVisibility(View.VISIBLE);
    Task.execute(() -> {
        boolean ok;
        Following migrated = null;
        try {
            migrated = matchService.resolve(item, target, season);
            // resolveTmdb 在目标 identityKey 是墓碑时返回墓碑行本身，必须用 isDeleted() 判定失败。
            ok = migrated != null && !migrated.isDeleted();
        } catch (Exception e) {
            ok = false;
        }
        Following result = migrated;
        App.post(() -> {
            binding.loading.setVisibility(View.GONE);
            if (!ok) {
                Notify.show(R.string.following_tmdb_match_failed);
                persistMatchError(item, R.string.following_tmdb_match_failed);
                return;
            }
            Notify.show(R.string.following_tmdb_matched);
            new FollowingUpdateCoordinator().checkNow(result.identityKey, true);   // 立即刷新官方状态
            load();
        });
    });
}
```

说明：

- `ChoiceDialog.showSingle(FragmentManager, CharSequence, CharSequence[], int, OnChoice)`（`ChoiceDialog.java:155`）可被 `AppCompatActivity` 使用，绕开 `showTmdbSeason` 的 `FragmentActivity` 限制。**不修改 `FollowingActivity` 的基类**（会撞壁纸/主题断言 `FollowingUiSourceTest:131-168`）。
- `persistMatchError`：**必须**走 `FollowingUpdatePolicy.markFailure(item, new IllegalStateException(msg), System.currentTimeMillis())` + `FollowingStore.update(item)`，**不得**直接 `item.lastError = msg`。`markFailure` 是唯一同时负责 160 字符截断（`shortError`）、`failureCount` 自增与 `nextCheckAt` 退避的计算点；绕过它会写入超长文案并破坏失败计数与退避（见 6.1）。走通后「检查失败」筛选才可见（F5）。
- 全部 IO（搜索、季清单、快照、迁移）都在 `Task.execute` 里，主线程只做 UI；这是 `FollowingUiSourceTest` 对追更页「不阻塞主线程」的一贯要求。

### 5.6 严格自动兜底（阶段 B）

**开关**：`FollowingSettings` 新增

```java
private static final String TMDB_AUTO_MATCH = "following_tmdb_auto_match";   // 默认 false
public static boolean isTmdbAutoMatchEnabled() { return Prefers.getBoolean(TMDB_AUTO_MATCH, false); }
public static void setTmdbAutoMatchEnabled(boolean enabled) { Prefers.put(TMDB_AUTO_MATCH, enabled); }
```

UI 入口（照 `FollowingSettings.ENABLED` 的既有做法）：

- mobile：`SettingPersonalFragment.setFollowing`（:133-145）同级新增 `setFollowingTmdbAutoMatch(View)` + `followingTmdbAutoMatch` 行（`fragment_setting_personal.xml` 复制 `following` 行结构，id 为 `followingMatch`/`followingMatchText`）。
- leanback：`SettingPersonalActivity`（:140-152）同构。
- 文案 `setting_following_tmdb_auto_match`。

**匹配器**：自动通道必须只走严格匹配，与用户全局「智能/严格」设置解耦。为此在 `ui/helper/TmdbMatcher.java` 新增一个只读入口（不改变既有方法行为）：

```java
/** 只做严格匹配（标题归一化完全一致 + 年份同年），供追更等需要高置信度的场景使用。 */
public TmdbItem searchStrictMatch(String videoName) throws Exception {
    if (TextUtils.isEmpty(videoName) || !tmdbConfig.isReady()) return null;
    String clean = cleanVideoName(videoName);
    List<TmdbItem> items = filterByMediaType(search(clean), "tv");
    return chooseStrictMatch(items, clean, null, sourceYear(clean, null));
}
```

**挂载点**：`FollowingUpdateCoordinator` 的 `check(item, manual, foreground)`（按符号名定位），插在方法最前面（早于 `preferredSource` 与 `if (item.tmdbId > 0)`）：

```java
public boolean check(Following item, boolean manual, boolean foreground) {
    if (item != null && item.tmdbId <= 0 && manual && FollowingSettings.isTmdbAutoMatchEnabled()) {
        Following migrated = autoMatch(item);            // 内部 try/catch，失败返回 null
        if (migrated != null) return checkNow(migrated.identityKey, true);   // 以新身份重跑
    }
    ... 原有实现不变 ...
}
```

**为什么不「就地改写 item 继续跑」**：`FollowingStore.migrateSourceToTmdb` 迁移成功后会 `getFollowingSourceDao().deleteForFollowing(旧 key)` 再 `getFollowingDao().delete(旧行)`（均在 `FollowingStore.java`，按符号名定位）。此时 `FollowingStore.update(item)` / `updateSource(source)` 作用在已删除的旧 `identityKey` 上会静默无效（`FollowingStore.update` / `updateSource`，按符号名定位），本轮检查的结果就丢了。因此迁移成功后**必须重新取新行并重跑一次完整检查**；递归深度恒为 1，因为迁移后 `tmdbId > 0` 使守卫立即为假。

```java
private Following autoMatch(Following item) {
    try {
        TmdbConfig config = TmdbConfig.effectiveCurrent();
        if (!config.isReady()) return null;
        TmdbMatcher matcher = new TmdbMatcher(new TmdbService(), config);
        TmdbItem target = matcher.searchStrictMatch(item.vodName);        // 见下方新增入口
        if (target == null || !target.isTv()) return null;
        Map<Integer, Integer> seasons = new FollowingTmdbMatchService().seasons(target);
        if (seasons.isEmpty()) return null;
        int season;
        if (seasons.size() == 1) season = seasons.keySet().iterator().next();
        else if (seasons.containsKey(item.trackedSeason)) season = item.trackedSeason;
        else return null;                                     // 多季且对不上：不猜
        return new FollowingTmdbMatchService().resolve(item, target, season);
    } catch (Throwable e) {
        FollowingUpdatePolicy.markFailure(item, new IllegalStateException("自动匹配 TMDB 失败"), System.currentTimeMillis());
        FollowingStore.update(item);   // 复用 markFailure，保留 160 字符截断 / failureCount / 退避
        return null;
    }
}
```

`autoMatch` 的硬规则（与上面的代码块一一对应）：

1. **严格匹配**：`searchStrictMatch` 内部只调 `chooseStrictMatch`（`TmdbMatcher.java:143`：标题归一化完全一致 + 年份同年，单候选还过 `isUnwantedSplitSeasonMatch`），**不看** `Setting.isTmdbSmartMatch()`，因此即使用户全局是「智能模式」，追更自动通道也不会放宽。
2. **只接剧集**：`!target.isTv()` 直接放弃。
3. **季号必须命中**：季清单唯一 → 用它；多季 → 只接受 `item.trackedSeason`；不在清单 → **放弃（不猜）**。
4. **同一份落库语义**：复用 `FollowingTmdbMatchService.resolve`，即水位保护 + 快照 + `resolveTmdb` + `TmdbMatchCache.putManual` 全部一致。
5. **失败不阻塞检查**：`autoMatch` 内任何异常只写 `lastError` 并返回 null，随后继续原有的探针链路（保持 `FollowingUpdateCoordinatorDeviceTest` 的既有断言）。

**只在 `manual == true` 触发**：`checkDue(now)` 与 `FollowingScheduler` 的 6h / 15min 定时走 `check(item, false, ...)`，永远不触发搜索，避免后台批量请求放大。

**与手动路径的复用**：自动通道写走的也是 `putManual`（而非 `put`），语义上等价于「系统代替用户确认了一次」，因此之后即便用户再开智能模式也不会被自动覆盖改写（`TmdbMatchCache.put` 的 `isManual` 守卫）。

### 5.7 字符串（三份 values 都要加）

| key | values（en） | values-zh-rCN | values-zh-rTW |
| --- | --- | --- | --- |
| `following_match_tmdb` | Match TMDB | 匹配 TMDB | 匹配 TMDB |
| `following_tmdb_matched` | Matched, refreshing official status | 已匹配，正在刷新官方状态 | 已匹配，正在重新整理官方狀態 |
| `following_tmdb_match_failed` | TMDB match failed, try again later | 匹配 TMDB 失败，请稍后重试 | 匹配 TMDB 失敗，請稍後重試 |
| `following_tmdb_not_tv` | Only TV show entries can be matched | 只能匹配剧集条目 | 只能匹配影集條目 |
| `following_tmdb_unavailable` | TMDB is not configured | 未配置 TMDB，无法匹配 | 未設定 TMDB，無法匹配 |
| `following_tmdb_unmatched_hint` | No TMDB entry linked; tap "Match TMDB" for official updates | 未关联 TMDB，点「匹配 TMDB」可获取官方更新 | 未關聯 TMDB，點「匹配 TMDB」可取得官方更新 |
| `following_tmdb_already_matched` | Already matched | 已关联 TMDB | 已關聯 TMDB |
| `setting_following_tmdb_auto_match` | Auto match official status | 自动匹配官方状态 | 自動匹配官方狀態 |

复用既有：`detail_tmdb_match_title`（匹配 TMDB 条目）、`tmdb_season_match_title`、`tmdb_season_option`、`tmdb_season_special`、`detail_tmdb_empty`。

---

## 6. 数据与一致性

### 6.1 不新增持久化结构

- 不新增 Room 字段、表、索引、迁移（`FollowingDatabase.VERSION` 保持 3）。
- 失败原因 → `Following.lastError`（已有 160 字符截断，见 `FollowingUpdatePolicy.markFailure`）。
- 匹配绑定 → `TmdbMatchCache`（`putManual`，manual 条目后续自动匹配不可覆盖）。
- 官方状态 → `FollowingMetadataSnapshot` + `FollowingUpdatePolicy.applyMetadata`。

### 6.2 身份迁移语义（复用既有）

`FollowingStore.resolveTmdb` 的真实返回语义（共四种，**返回值不是判据，语义才是**）：

| 前置条件 | 返回 | 调用方应如何判定 |
| --- | --- | --- |
| 非法参数：`tmdb == null` 或 `getTmdbId() <= 0` 或媒体类型不是 `tv` | `null` | 失败（`following_tmdb_not_tv` / 参数错误） |
| 目标 `tmdb:tv:<id>:s<season>` 行存在且是墓碑（`deletedAt > 0`） | **该墓碑行本身**（C9 防复活语义：不复活、不迁移） | 失败，判据 `isDeleted()`；**不能靠 null** |
| 找不到可迁移的同源行（`selectMigrationSource` 返回 `null`） | 既有 target（可能为 `null`） | target 非空且非墓碑 → 视为「已匹配，无需再迁移」；target 为 `null` → 失败 |
| 否则 | `migrateSourceToTmdb` 迁移后的行 | 成功 |

迁移内部行为：

| 场景 | 行为 |
| --- | --- |
| 同源存在多行（`findUnmatchedBySource`） | `selectMigrationSource` 优先 `trackedSeason == season`，其次 `<= 0`，最后首行 |
| 目标行已存在且非墓碑、并找到可迁移同源行 | `FollowingMergePolicy.mergeOne(target, migrated)`：进度按 `updatedAt` LWW、水位取 max、开关取 AND |
| 来源绑定 `following_source` | 改 `followingKey` 迁到新 key；`playableSeason <= 0` 时补 `season` |
| 旧行 | `deleteForFollowing(旧 key)` + `dao.delete(旧行)` |

设计上**不做**任何额外补偿逻辑。统一失败判据：`migrated == null || migrated.isDeleted()`——只有它同时覆盖「非法参数」与「目标是墓碑」两种情况。

### 6.3 幂等与并发

- 重复点「匹配 TMDB」且选同一条目：第二次调用时目标行已存在（上一次迁移写入），`findUnmatchedBySource` 为空，`selectMigrationSource` 返回 `null` → `resolveTmdb` 返回**既有 target**（不是 `null`）。只要它不是墓碑就视为「已匹配，无需再迁移」，提示 `following_tmdb_already_matched` 或直接 `load()` 刷新即可，不得报失败。
- 迁移与「检查」并发：`FollowingStore.resolveTmdb` 内部 `synchronized (MIGRATION_LOCK)` + `runInTransaction`，无需额外加锁。
- Activity 侧用 `matchSearchGeneration` 丢弃过期搜索结果，避免旧回调 `updateItems` 到已关闭对话框。

---

## 7. 边界与异常处理

| 情况 | 行为 |
| --- | --- |
| 未配置 TMDB / TMDB 不可用 | 点入口即提示 `following_tmdb_unavailable`，不发请求 |
| 搜索无结果 | `dialog.updateItems(空)` + 提示 `detail_tmdb_empty` |
| 搜索抛 `TmdbService.AuthException` | 与空结果同样处理（不弹原始异常） |
| 选中电影条目 | 提示 `following_tmdb_not_tv`，不迁移 |
| 条目季清单为空 | 提示 `following_tmdb_not_tv` |
| 目标追更已被取消（墓碑） | `resolveTmdb` 返回**该墓碑行本身**（不是 null），调用方用 `isDeleted()` 判定 → 提示 `following_tmdb_match_failed` + `lastError` |
| 快照拉取失败 | 不致命：`snapshot = null` → `migrateSourceToTmdb` 走 `refreshDerived`，官方状态等下一次「检查」补齐 |
| 用户取消季选择对话框 | 不做任何改动 |
| 用户在搜索结果里手输空关键字 | 由 `TmdbSearchDialog` 既有的 searchListener 行为接管，无结果即空列表 |

---

## 8. 验收标准

每条都必须可验证（单测名 / 设备步骤）。

| 编号 | 验收项 | 验证手段 |
| --- | --- | --- |
| AC-1 | 未关联（`tmdbId<=0`）的追更卡片，点「官方状态未知」行会打开 TMDB 搜索对话框，预填查询为站源剧名清洗后的关键字 | 设备场景 S1 + 单测 `FollowingTmdbMatchServiceTest.suggestQueryCleansSeasonAndYear` |
| AC-2 | 详情弹窗项数为 6，第 6 项文案为「匹配 TMDB」，既有 5 项顺序与语义不变 | 单测 `FollowingUiSourceTest` 增补断言 + 设备 S2 |
| AC-3 | 选中 tv 条目且只有 1 季时，不再弹季选择，直接迁移 | 单测 `FollowingTmdbMatchServiceTest.singleSeasonSkipsChoice` |
| AC-4 | 多季时弹出季列表，标签使用 `tmdb_season_option`/`tmdb_season_special`，默认选中与 `item.trackedSeason` 相同的季 | 单测 `...seasonLabelsAndDefaultSelection` + 设备 S3 |
| AC-5 | 迁移成功后：`identityKey == "tmdb:tv:<id>:s<season>"`、`tmdbId` 正确、`seriesKey` 改为 tmdb 形态、`following_source` 绑定迁到新 key、旧行被删除 | 设备/仪器 `FollowingTmdbMatchDeviceTest`（复用 `FollowingMigrationDeviceTest` 的断言风格） |
| AC-6 | 迁移后 `TmdbMatchCache.findManual(siteKey, vodId, vodName)` 命中且 `entry.manual == true`；随后 `TmdbMatchCache.put(...)`（自动通道）**不能**覆盖它 | 单测 `FollowingTmdbMatchServiceTest.manualBindingWinsOverAutomaticPut` |
| AC-7 | 迁移完成后无需用户再点，卡片首行变为「官方已更新至第 N 集」（N 来自所选季） | 设备 S1 端到端 |
| AC-8 | `watchedEpisode`、`position`、`duration` 在迁移前后不变 | 设备 AC-5 用例内断言 |
| AC-9 | **水位不被吞**：`watchedEpisode = 3`、`readWatermarkEpisode = 0`、官方 released = 10 时，迁移后 `readWatermarkEpisode = 3`、`unwatchedCount = 7`、`hasUpdate = 1` | 设备 `FollowingTmdbMatchDeviceTest.migrationPreservesUnwatchedCount` |
| AC-10 | 目标行已是墓碑时不复活、不迁移、给用户失败提示 | 设备 `...tombstoneTargetIsNotRevived` |
| AC-11 | 自动兜底：开关关闭时 `checkNow(id, true)` 不发任何 TMDB 搜索（`probes` 计数与现有 `FollowingUpdateCoordinatorDeviceTest` 一致，搜索次数 0） | 单测（注入假 TmdbService 计数） |
| AC-12 | 自动兜底：开关开启 + `manual=true` + 严格匹配命中 → 身份迁移并立即写官方状态 | 单测 `FollowingAutoMatchTest.strictMatchMigratesOnManualCheck` |
| AC-13 | 自动兜底：智能匹配才可命中（严格失败）的条目**不得**被自动写入 | 单测 `...smartOnlyCandidateIsRejected` |
| AC-14 | 自动兜底：`checkDue`（后台）即使开关开启也不触发搜索 | 单测 `...periodicCheckDoesNotSearch` |
| AC-15 | 任意匹配失败都会在卡片错误行显示原因，并能被「检查失败」筛选命中 | ① 源码契约断言（`FollowingUiSourceTest` 只能断言源码字符串，因此它只断言「失败分支存在 `FollowingUpdatePolicy.markFailure(...)` + `FollowingStore.update(item)` 调用点，且不存在 `item.lastError =` 直写」）+ ② 代码评审；③ **运行期落库必须另做**：新增 `FollowingTmdbMatchDeviceTest` 仪器用例断言 `stored.lastError` 非空且长度 ≤ 160、`failureCount` 自增（当前 `FollowingUiSourceTest` 无 `lastError` 断言，不能替代）+ 设备 S4 |
| AC-16 | 既有回归全部通过：`FollowingSourceProbeTest`(6)、`FollowingUpdateCoordinatorDeviceTest`、`FollowingProjectionDeviceTest`、`FollowingMigrationDeviceTest`、`FollowingUiSourceTest` 全量、`HistoryPlaybackTest`、`HistorySeasonSnapshotProjectionTest`、`PlaybackProgressWriterEpisodeTest` | `.gradlew.bat` 双 flavor 定向单测全部通过（不预设通过用例总数，以命令零失败为准） |
| AC-17 | 未新增 Room 字段/表/迁移：`FollowingDatabase.VERSION == 3`，`Following.class` 字段集合不变 | ① 源码契约断言（`FollowingUiSourceTest` 断言 `FollowingDatabase` 中 `VERSION = 3` 字面量与 `Following.class` 字段声明集合未变——它只是源码字符串断言，不能证明运行期实际库版本）+ ② 代码评审；③ 若需证明真机上没有触发迁移，必须另加仪器测试（打开既有库后断言 `getOpenHelper().getReadableDatabase().getVersion() == 3`） |
| AC-18 | 追更页主线程无阻塞：所有 TMDB 调用都在 `Task.execute` 内 | 代码评审 + 单测断言 `Task.execute` 出现位置 |

---

## 9. 验证计划

### 9.1 单测（JVM，双 flavor 各一次）

```powershell
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.following.*" --tests "com.fongmi.android.tv.ui.activity.FollowingUiSourceTest" --tests "com.fongmi.android.tv.bean.HistoryPlaybackTest"
.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.following.*" --tests "com.fongmi.android.tv.ui.activity.FollowingUiSourceTest"
```

新增单测文件：

- `app/src/test/java/com/fongmi/android/tv/following/FollowingTmdbMatchServiceTest.java`（AC-1/3/4/6）
- `app/src/test/java/com/fongmi/android/tv/following/FollowingAutoMatchTest.java`（AC-11/12/13/14）
- `app/src/test/java/com/fongmi/android/tv/ui/activity/FollowingUiSourceTest.java` 增补（AC-2/15/17/18 的**源码契约部分**；AC-15 的运行期落库与 AC-17 的实际库版本不在其能力范围，见各条验证手段）

### 9.2 编译与仪器测试（真机/模拟器，跑一次）

```powershell
.\gradlew.bat :app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac :app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac
.\gradlew.bat :app:connectedMobileArm64_v8aDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.fongmi.android.tv.following.FollowingTmdbMatchDeviceTest
```

新增仪器测试：`app/src/androidTest/java/com/fongmi/android/tv/following/FollowingTmdbMatchDeviceTest.java`（AC-5/7/8/9/10，并承载 AC-15 的运行期 `lastError` 落库断言与 AC-17 的实际库版本断言）。

### 9.3 设备手工场景（一次性）

- **S1 未配置 TMDB 旁路**：关闭 TMDB → 点官方行 → 提示「未配置 TMDB，无法匹配」，不发网络请求。
- **S2 正常匹配**：配置 TMDB，从原生站源播放页加一条追更（`tmdbId=0`）→ 详情弹窗选「匹配 TMDB」→ 搜到条目 → 选择正确季 → 卡片首行变「官方已更新至第 N 集」，观看进度与未看数与匹配前一致。
- **S3 多季选择**：选一部多季剧，确认季列表标签与默认选中季正确。
- **S4 失败可见**：故意选一个无季条目/断网匹配，确认卡片出现错误行并可被「检查失败」筛选。
- **S5 自动兜底**：开启「自动匹配官方状态」，构造一条标题与 TMDB 完全同名的追更，点「检查」→ 自动补上官方状态；等待一次后台定时（或直接触发 `checkDue`）确认**不**再搜索。

---

## 10. 风险与回滚

| 风险 | 等级 | 缓解 |
| --- | --- | --- |
| 误匹配写错官方集数 | 高 | 手动确认 + 自动通道严格匹配 + 默认关闭 + 不做智能自动写入 |
| 首次元数据吞掉未看集数 | 高 | 5.4 水位预置 + AC-9 专项用例 |
| 迁移破坏既有追更行（墓碑/重复行） | 中 | 复用 `resolveTmdb` 的墓碑守卫与 `mergeOne`；失败判据统一为 `migrated == null \|\| migrated.isDeleted()`（见 6.2），不能只看 null |
| `checkNow` 内身份变更导致后续操作指向旧 key | 中 | 迁移后立即 `FollowingStore.find(newKey)` 重新取行；失败则本条检查返回 false |
| 请求放大（后台 15min/6h 定时） | 中 | 自动通道仅 `manual == true` 触发；`TmdbService` 搜索缓存 1 天兜底 |
| 破坏 `FollowingUiSourceTest` 字符串断言 | 中 | 不新增卡片按钮、不改 `checkAll` 语句、不删除既有 5 个 action 字符串；改后立即跑该测试 |
| `ChoiceDialog.showSingle` 在追更页的兼容性 | 低 | 若实现时发现不可用，退化方案：`new WebHtvAlertDialogBuilder(this).setTitle(...).setItems(labels, ...)`（`showServerImportDialog` 已是同一范式） |

**回滚**：单次 `git revert` 即可（无 schema 变更、无迁移、无二进制/依赖改动）。已写入的 `TmdbMatchCache` 手动条目是纯缓存，可在设置里清空 TMDB 缓存自愈；已迁移的追更行由用户「取消追更」可回到原状。

---

## 11. 实施分期与工作量（agent 墙钟估算）

| 阶段 | 内容 | 估算 | 验证 |
| --- | --- | --- | --- |
| A-1 | `FollowingTmdbMatchService` + 单测 | 35 min | 定向单测 |
| A-2 | `FollowingActivity` 编排（入口/搜索/季选/迁移/失败） | 30 min | 单测 + 设备 S1/S2 |
| A-3 | `FollowingAdapter` 官方行点击 + Listener + 详情弹窗 6 项 + 三份字符串 | 20 min | `FollowingUiSourceTest` |
| A-4 | A 阶段合并验证（双 flavor 单测 + 编译 + 仪器编译） | 20 min | AC-1~10、AC-15~18 |
| B-1 | `TmdbMatcher.searchStrictMatch` + `FollowingSettings` 开关 + `FollowingUpdateCoordinator` 挂钩 + 单测 | 30 min | AC-11~14 |
| B-2 | 设置页两套 layout 与点击逻辑 | 15 min | 手工 + 编译 |
| C | 批量匹配 / 播放页放宽 | 未排期 | — |

总量约 2.5 小时，可分两次 task guard 会话提交（A 一个 commit，B 一个 commit）。

---

## 12. 未决问题（需评审确认）

1. 匹配成功后是否需要在卡片上短暂显示「已匹配 TMDB：<标题>」的可撤销提示？（当前设计只做 Toast 提示，不做撤销入口；重匹配需再次点入口。）
2. 自动兜底是否允许在「后台定时检查」也触发一次（更自动，但会放大请求）？当前设计为**仅手动**。
3. 是否允许用户对**已匹配**条目重新匹配（改绑到另一 TMDB 条目）？当前设计 F1 入口仅在 `tmdbId <= 0` 时可见；若需要改绑，应新增「更换 TMDB 匹配」项并补 AC。
4. 是否需要「一键匹配全部未关联」批量入口（阶段 C）。批量需要串行队列 + 请求节流 + 汇总结果，工作量约 1 小时，建议等 A/B 稳定后再说。

---

## 13. 恢复锚点

- **目标**：按本文实现「追更 TMDB 手动匹配（阶段 A）+ 严格自动兜底（阶段 B）」，并全部通过第 8 节验收。
- **允许路径（建议 scope）**：
  - `app/src/main/java/com/fongmi/android/tv/following/`（新增 `FollowingTmdbMatchService.java`、改 `FollowingSettings.java`、`FollowingUpdateCoordinator.java`）
  - `app/src/main/java/com/fongmi/android/tv/ui/activity/FollowingActivity.java`
  - `app/src/main/java/com/fongmi/android/tv/ui/adapter/FollowingAdapter.java`
  - `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbMatcher.java`
  - `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingPersonalFragment.java` 与 `app/src/mobile/res/layout/fragment_setting_personal.xml`
  - `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingPersonalActivity.java` 与 `app/src/leanback/res/layout/activity_setting_personal.xml`
  - `app/src/main/res/values{,-zh-rCN,-zh-rTW}/strings.xml`
  - `app/src/test/java/com/fongmi/android/tv/following/`、`app/src/test/java/com/fongmi/android/tv/ui/activity/FollowingUiSourceTest.java`
  - `app/src/androidTest/java/com/fongmi/android/tv/following/`
- **禁止**：改 `FollowingSourceProbe`、`FollowingDao`、`FollowingDatabase`、`FollowingStore.projectHistory` 守卫、`Setting.isHistoryAggregationEffective`、`stampHistoryTmdbId` / `enrichTmdbId`、`checkAll` 的那一行循环语句、`item_following.xml` 的按钮集合。
- **下一步（唯一）**：评审本文 → 若通过阶段 A，用 `task_guard.sh start --id FOLLOW-3A-TMDB-MATCH-MANUAL-<date> --mode standard --scope <上列 A 期路径>` 开工。
