# TMDB 详情加载与渲染速度分析与优化

- 任务 ID：`TMDB-DETAIL-PERF-01`
- 范围：原生 TMDB 详情页（`TmdbDetailActivity`）与它依赖的 `TmdbService` / `TmdbImageSelector`
- 状态：分析完成；W1（identity 已知时站点详情 ∥ TMDB 详情）、W2（阻塞段移出共享线程池）、W3（消歧瘦身）、W4（图片档位）已实施并验证（§8/§9）；W5（主线程渲染去重）已按「先量化再决定」试做并回滚（§9.3）；W3 的真实消歧端到端计时仍缺可复现条目（§10）
- 基线：`dev4 @ 3964eeae7ccf4f10f5a2e3696a2909c7a92b3952`

## 1. 结论：能优化，收益集中在可量化项

| 项 | 现状 | 实测代价 | 优化后 | 结论 |
|---|---|---|---|---|
| 自动匹配消歧 | 串行拉取最多 6 个候选的**完整** detail（含 images/translations/external_ids/content_ratings/recommendations/similar） | TV 样例 `446,404 B`/次；电影样例 `192,107 B`/次 | 只取打分真正用到的 `credits`（TV 另加 `aggregate_credits`）：TV `335,387 B`（−25%）、电影 `56,056 B`（−71%） | **已优化** |
| 详情页图片 | 详情页把所有 TMDB 图片强制升到 `/original/`（hero、轮播、海报/图集 rail） | poster original `1,187,694 B` vs w500 `85,680 B`（−93%）；backdrop original `982,744 B` vs w1280 `121,009 B`（−88%） | rail 用 w500/w780、hero 海报用 w780；hero 大背景、背景轮播、全屏查看/保存仍用 original（§8.3） | **已优化** |
| 首屏串行等待 | `站点详情 → TMDB 详情 → 分季 → 绑定` 全部串行 | 两次网络 RTT + 一次分季 RTT | identity 已知时站点详情 ∥ TMDB 详情，省 1 个 RTT；T3/T4 完整 payload 页面按 C16 §6.3 例外多发 1 次预热请求（结果丢弃） | **已优化**（§6/§8.2） |
| 共享线程池阻塞 | 协调任务占用全局 5 线程池一个线程并阻塞在 `tmdbFuture.get()` | 并发打开详情/首页加载时排队 | 收尾段整体提交到 `Task.largeExecutor()`（20 线程），共享 5 线程池派发后立即释放 | **已优化**（§9.4） |
| 主线程重复渲染 | 一轮 apply 内重复清理/重复渲染/多次 `bindTmdbSection` | 低端 TV 上屏帧偏重 | 去重 + 签名短路（已实现并实测） | **已试做并回滚**（§9.3：中位 164→202 ms，无改善） |

## 2. 研究门记录（AGENTS.md §7/§8）

| 来源 | 版本/访问 | 支持的事实 | 决策影响 |
|---|---|---|---|
| `https://developer.themoviedb.org/docs/append-to-response` | 2026-10-21 | `append_to_response` 只是把子请求拼进**同一个** HTTP 响应，不产生额外请求；`include_image_language` 影响 images 过滤 | 消歧可以用同一接口换参数瘦身，不需要新增请求类型；缩字段不会改变请求语义 |
| `https://developer.themoviedb.org/docs/image-basics` | 2026-10-21 | 图片 URL = `base_url + file_size + file_path`，如 `/t/p/w500/...` | 档位替换只需替换 `/t/p/<size>/` 一段 |
| `https://developer.themoviedb.org/openapi/tmdb-api.json`（官方 OAS） | 2026-10-21 | `poster_sizes=[w92,w154,w185,w342,w500,w780,original]`、`backdrop_sizes=[w300,w780,w1280,original]`、`still_sizes=[w92,w185,w300,original]`、`profile_sizes=[w45,w185,h632,original]` | 只使用官方声明过的档位，避免自造尺寸 |
| `https://bumptech.github.io/glide/javadocs/.../BaseRequestOptions.html` | 2026-10-21 | `override(width,height)` 覆盖 Target 尺寸（即按该尺寸解码） | 轮播/背景可限制解码尺寸而不改 URL 质量 |
| 仓库 `docs/tmdb-crawler-parallel-prefetch-design.md` | 已实现 | `TmdbDetailPrefetch` 单槽 + 身份 + 取消模式已用于 `TmdbUIAdapter`（VideoActivity 路径） | 仓库内已有并行预取先例；但 `TmdbDetailActivity` 未接入 |
| 仓库 `docs/C16-tmdb-source-detail-contract.md` | 已实现 | §6.1/§19：首屏只要求 `core/credits/images`；**完整 payload 打开详情时 TMDB 请求数为 0**；TV 选季后才要求 `season:N` | 决定了 W1 的取舍边界（见 §6） |

## 3. 现状关键路径（代码证据）

冷启动进入详情（identity 已知，TV，单遍独立模式）：

```text
loadContent (TmdbDetailActivity.java:2428, 在 Task.executor() 的 5 线程池内)
  ├─ SiteApi.detailContent(key, id)                    :2446   ← 站点网络
  ├─ 解析 source payload / 运行时策略                    :2474-2496
  ├─ detailTasks.submitCallable(largeExecutor, loadTmdbResult) :2537
  ├─ tmdbFuture.get()  ← 阻塞当前共享池线程               :2555
  │     └─ loadTmdbBundle → TmdbService.detail()        :2906 / TmdbService:119-165  ← TMDB 网络（单个 append 请求）
  ├─ preloadInitialStandaloneSeason → TmdbService.season :2589 / :2625                ← 又一次网络（分季）
  └─ applyLoaded（UI 线程绑定/渲染）                      :2580-2610
```

自动匹配未命中时（无缓存匹配、无 identity）：

```text
loadTmdbResult :2837
  └─ searchResolvedTmdbMatch → 搜索
       └─ chooseTmdbBundle :12523 / chooseSmartTmdbBundle :12607
            依次 loadTmdbBundle(candidate) 最多 6 次 :12541 / :12620   ← 每次都是完整 append
```

图片：

```text
highResTmdbImage :7283 → TmdbImageSelector.originalUrl :82-91（强制 /original/）
  ├─ bindBackdropImage :4007 / showStaticBackdropImage :4025 / loadBackdropImage :4037
  ├─ refreshBackdropSlideshow :4063 → loadNextBackdropSlide :4101（每 10s 一张）
  └─ bindTmdbSection :6424 → posterAdapter / episodePhotoAdapter（rail）
```

## 4. 实测数据

TMDB 真实响应（语言 `zh-CN`，`include_image_language=zh-CN,null`，2026-10-21；样例 `tv/1399`、`movie/11`）：

| 请求 | 字节 |
|---|---|
| TV 完整 append（images,credits,aggregate_credits,translations,external_ids,content_ratings,recommendations,similar） | 446,404 |
| TV 消歧 lite（credits,aggregate_credits） | 335,387（−25%） |
| 电影完整 append（+release_dates,recommendations,similar） | 192,107 |
| 电影消歧 lite（credits） | 56,056（−71%） |

TMDB 图片（同一条目 poster/backdrop）：

| 资源 | 字节 |
|---|---|
| poster `w342` | 43,525 |
| poster `w500` | 85,680 |
| poster `original` | 1,187,694 |
| backdrop `w780` | 48,585 |
| backdrop `w1280` | 121,009 |
| backdrop `original` | 982,744 |

推论：详情页 hero 海报与 rail 图片从 original 降到档位后，图片字节下降约 83%–93%（backdrop 轮播与 hero 大背景仍为 original，未计入）；消歧路径在 TV 上节省 25%、电影上节省 71%，且候选数最多 6 个，字节节省按倍数累积。

## 5. 决策

- 采用「不改动匹配判定输入」的最小改造：消歧用 lite detail，最终赢家再取完整 detail；不改变 `scoreTmdbTitle/Year/MediaType/People`、`TmdbMatchPolicy` 的任何判据。
- 图片只在 rail 与 hero 海报降档（rail w500/w780、hero 海报 w780），hero 大背景/背景轮播/全屏查看/保存仍用 original；档位只使用官方 OAS 声明的尺寸。
- 不做：匹配阈值、源数据优先级、原有 C16 补齐顺序（仅新增 §6.3 预热例外）、线程池容量、新依赖。

## 6. W1（站点 ∥ TMDB）：已由用户裁决为「要速度」，已实施

现状可省的真实收益：站点详情与 TMDB 详情相互独立，可重叠 1 个 RTT（约 200ms–1.5s，取决于站点与网络）。

原契约冲突：C16 规定源内嵌**完整** TMDB payload（T3/T4）时打开详情 **TMDB 补齐请求数为 0**，而该判断只能等站点详情返回后做出。

**裁决（2026-10-21，用户选 B）**：允许 identity 已知的页面提前发 1 次 TMDB 详情请求，T3/T4 源接受「多 1 次预热请求」，但预热结果必须丢弃、不得上屏或覆盖源字段。契约已同步修订为 C16 §6.3（例外条款），§6.1/§12/§19.13/具体分支/阶段 4 记录同步更新。

## 7. W2/W5 决策变更（原暂缓理由已被后续实施/实测取代）

- W2（不阻塞共享 5 线程池）：原计划「把 `Future.get()` 改为回调续接」，因涉及 180 行主流程与代际/凭据校验位置而暂缓。后续改用范围更小的等价方案：不改主流程语义，只把「等结果 → 补搜索/消歧 → 预取首季 → 回 UI 绑定」整段抽成 `completeTmdbLoad(...)` 并提交到 `Task.largeExecutor()`；协调任务派发后即释放共享池线程。收益仍是并发排队改善而非单页首屏时延，已实施并验证（§9.4）。
- W5（主线程去重）：按原计划先量化「loading 消失那一帧」的 UI 线程耗时再决定；设备实测无改善，已回滚（§9.3）。

## 8. 实施结果（W1 + W3 + W4）

### 8.1 W3 消歧瘦身 + 有界并行

- `TmdbService`
  - 新增 `detailForDisambiguation(item, config)`：`append_to_response` 只取 `credits,aggregate_credits`（TV）/`credits`（movie），走独立缓存类型 `detail-lite` 与独立键 `disambiguationCacheKey`。
  - `detailUrl(...)` 增加 `disambiguation` 重载；`disambiguationAppend(...)` 明确排除 images/translations/external_ids/content_ratings/release_dates/recommendations/similar。
  - 保留 `aggregate_credits` 的原因：`cast()` 优先读它，去掉会改变人物打分输入。
- `TmdbDetailActivity`
  - `TmdbCandidate` 由持有完整 `TmdbBundle` 改为持有 lite `JsonObject detail`。
  - 新增 `fetchDisambiguationDetails(...)`：按候选原顺序、每次最多 `DISAMBIGUATION_CONCURRENCY = 3` 个在途请求并行拉取 lite 详情；**打分仍按原顺序串行执行**，因此排序/分差/阈值判定与改造前一致。
  - 新增 `loadDisambiguationWinner(...)`：赢家才回到完整 `loadTmdbBundle(...)`（多命中一次 7 天文件缓存时不产生网络请求）。
  - `chooseTmdbBundle`、`chooseSmartTmdbBundle` 均改为 lite 打分 + 赢家升级；请求数从「N 次完整」变为「N 次 lite + 1 次完整」，字节数与墙钟时间见 §4。

### 8.2 W1 identity 已知时并行预热

- `TmdbDetailPrefetch` 由包内可见放宽为 `public`（类、构造器、`start/take/cancel`、`StartState/StartResult/Result` 与其访问器），供 `TmdbDetailActivity` 跨包复用；行为与既有 `TmdbUIAdapter` 使用路径不变（`TmdbDetailPrefetchTest` 6 例保持通过）。
- `TmdbDetailActivity`
  - 新增字段 `detailPrefetch = new TmdbDetailPrefetch(Task.largeExecutor())`。
  - `loadContent` 入口先 `detailPrefetch.cancel()` 清槽，再 `startTmdbDetailPrefetch(initialTmdbItem, reusableBundle)`：仅在 identity 已知、TMDB 配置就绪、站点允许 TMDB、且没有可复用 bundle 时启动；预取任务调用 `tmdbService.detail(item, config)`。
  - 新增 `loadPrefetchedTmdbBundle(item)`：`take()` 单槽 future 并复用其结果（`toTmdbBundle` 组装，避免第二次网络请求）；缺失/取消/失败时回落 `loadTmdbBundle(item)`。
  - `loadTmdbBundle` 拆分为「网络取详情」+ `toTmdbBundle(item, detail)`，供预取与常规路径共用。
  - 丢弃点：源 payload 分支、可复用 bundle 分支、DIRECT 直放分支、无 TMDB 任务分支均 `detailPrefetch.cancel()`；`onDestroy` 同样取消。
- 代价：T3/T4 完整 payload 页面多发 1 次预热请求（结果丢弃），已在 C16 §6.3 记录为明确例外。

### 8.3 W4 图片档位

- `TmdbImageSelector` 新增 `tierUrl(url, size)`、`posterTier`（w500）、`photoTier`（w780）、`heroPosterTier`（w780）；只替换官方 OAS 声明的档位，非 `/t/p/` 地址原样返回。
- `TmdbDetailActivity` 新增 `setRailPosters/setRailPhotos`，图集 rail、海报 rail、季剧照 rail 全部走小档位（`applyTmdbBundle`、`loadTmdbMediaBlocks`、`bindSeasonTmdbMedia` 三处）。
- hero 海报改用 `heroPosterTier`（w780）；hero 大背景、背景轮播、全屏查看、保存图片**仍是 original**（`highResTmdbImage` 未改）。

### 8.4 改动文件

| 文件 | 改动 |
|---|---|
| `app/src/main/java/com/fongmi/android/tv/service/TmdbService.java` | lite 详情入口、append 策略、缓存键 |
| `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java` | W1 并行预热与消费/丢弃、消歧并行 + 赢家升级、rail 档位、hero 海报档位 |
| `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbDetailPrefetch.java` | 放宽为 public 供详情页跨包复用（行为不变） |
| `app/src/main/java/com/fongmi/android/tv/utils/TmdbImageSelector.java` | 档位工具 |
| `app/src/test/java/.../service/TmdbDisambiguationDetailParityTest.java` | lite 与完整详情打分输入等价性 |
| `app/src/test/java/.../service/TmdbServiceCacheKeyTest.java` | lite 缓存键隔离 |
| `app/src/test/java/.../ui/activity/TmdbDetailDisambiguationPerfSourceTest.java` | 并行/lite/档位的源码级契约 |
| `app/src/test/java/.../ui/activity/TmdbDetailPrefetchWiringSourceTest.java` | W1 预热/消费/丢弃/取消的源码级契约 |
| `docs/C16-tmdb-source-detail-contract.md` | 新增 §6.3 例外条款并同步 §6.1/§12/§19.13/阶段 4 |
| `app/src/test/java/.../utils/TmdbImageSelectorTierTest.java` | 档位替换与可回退性 |

## 9. 验证证据

执行时间 2026-10-21，设备和命令：

```text
bash gradlew :app:assembleMobileArm64_v8aDebug \
             :app:compileLeanbackArm64_v8aDebugJavaWithJavac \
             :app:testMobileArm64_v8aDebugUnitTest \
  --tests 'com.fongmi.android.tv.service.Tmdb*' \
  --tests 'com.fongmi.android.tv.utils.Tmdb*' \
  --tests 'com.fongmi.android.tv.ui.activity.TmdbDetail*' \
  --tests 'com.fongmi.android.tv.ui.helper.Tmdb*' \
  --tests 'com.fongmi.android.tv.bean.Tmdb*' \
  --tests 'com.fongmi.android.tv.setting.SettingTmdb*'
→ BUILD SUCCESSFUL；58 个测试类 519 例，0 失败 0 错误；APK app-mobile-arm64_v8a-debug.apk 205,522,144B
```

新增测试（12 例）：

- `TmdbDisambiguationDetailParityTest`（2）：lite 与完整详情的 `cast()` 输出逐条相同、`splitSeasonDetailScore` 对 4 组源文本取值相同；lite append 只保留 `credits`/`aggregate_credits` 且不含被剥离块。
- `TmdbImageSelectorTierTest`（4）：档位替换、未知域名/空值原样保留、代理包装 URL 同样可换档、`originalUrl(tier(x)) == x`。
- `TmdbServiceCacheKeyTest#disambiguationCacheKeyIsIsolatedFromDetailCaches`（1）：lite 键与 full/core 键不同、语言与媒体类型仍分开、API base/Key 不影响。
- `TmdbDetailDisambiguationPerfSourceTest`（2）：消歧走 lite + 有界并发 + 赢家升级；rail 走小档位而 hero/viewer/保存保持 original。
- `TmdbDetailPrefetchWiringSourceTest`（3）：预热在站点详情之前启动、identity 路径消费预热而不二次请求、源 payload 路径丢弃预热且 `onDestroy` 取消。

### 9.1 设备实测（2026-10-22，`127.0.0.1:5561`）

设备：HD1910 / Android 9 / 1920x1080；debug 构建、`debug_log=true`、`detail_open_mode=1`（单遍独立模式）、TMDB Key 已配置。
条目：站点 `rebang` / `vod 46233` / TMDB `tv 101172`（吞噬星空）。
方法：每次运行前 `force-stop` + 清 `cache/tmdb` 与 `cache/image_manager_disk_cache`（自动匹配另清 `cache/app_cache`）；先启动首页让订阅配置就绪，再用 `run-as am start --user 0` 打开详情；阶段耗时取应用自身 `tmdb-detail-flow` 日志（`load start` → `apply loaded`）。

**W1（identity 已知）成对交替 A/B**（同一时间窗内 base/new 各一次，共 3 对，消除网络漂移）：

| 对 | 基线总耗时 | 新构建总耗时 | 差值 |
| ---: | ---: | ---: | ---: |
| 1 | 4384 ms | 3985 ms | **−399 ms** |
| 2 | 3343 ms | 2835 ms | **−508 ms** |
| 3 | 3739 ms | 2361 ms | **−1378 ms** |

- 3/3 对新构建更快；中位 −508 ms（−15%），均值 −762 ms（−21%）。站点详情在该设备约 450–690 ms，收益上界即这段重叠时间。
- 机制证据（新构建每次都有）：`tmdb prefetch cost=` 的起点等于 `load start`（`prefetch_to_load_ms ≈ prefetch_ms`，即与站点请求并行启动）、`tmdb prefetch hit`、`tmdb bundle cost=2–6 ms`（复用预热结果，**没有第二次详情请求**）。
- 未配对的批量样本（base n=6 / new n=6）不可作为结论：TMDB 详情请求本身在 1.5–4.7 s 之间漂移，完全盖过 0.5 s 级收益。这也是改用成对交替的原因。

**W3（自动匹配消歧）**：该条目上搜索命中后首个候选即被接受，两个构建都只发 1 次详情请求、未进入消歧路径（成对 2 组：−207 ms / +811 ms，差异来自 2.5–3.0 s 的搜索请求抖动）。因此 W3 的收益目前只有载荷级证据（§4：TV −25%、电影 −71%），尚未在真实消歧场景做端到端计时。

**W4（图片档位）**：设备级聚合字节不可用——首页在详情窗口内仍有后台图片加载，`/proc/net/xt_qtaguid/stats` 的 uid 增量把两者混在一起（测得 base 4.1 MB / new 7.5 MB，方向与预期相反，判定为测量污染而不是回归）。W4 仍以真实 TMDB 资源字节为准：poster original 1,187,694 B → w500 85,680 B / w780 201,063 B，backdrop original 982,744 B → w780 48,585 B（§4）。

### 9.2 其它未验证项

- 4K 屏下 hero 海报由 original 降到 w780：hero 显示尺寸远小于原图，按显示尺寸解码后无可见差异；若后续有 4K 实机反馈明显变软，只需把 `heroPosterTier` 改回 `originalUrl`（单点回退）。
- 预热失败时的回落会再发一次详情请求（与改造前单次尝试相比多一次重试机会，属容错放宽）。
- 设备安装环境坑（非代码问题）：本模拟器的 PackageManager 曾卡死在 install-commit，重启模拟器后 `cmd package install-create/write/commit` 正常（≤1 s）。
- W3 赢家升级的失败窗口：lite 打分已通过、但赢家完整详情请求失败且无陈旧缓存时，本次自动匹配放弃并回落「手动选择 / 无 TMDB」。改造前该候选的完整详情在打分阶段已取到，因此这是 W3 新增的极窄窗口（条件是 lite 成功而紧随其后的完整详情失败）。

### 9.3 W5 主线程渲染去重：已试做并**回滚**

按用户选择实现了一版去重（`applyLoaded` 只清一次渲染缓存、不再重复 `bindTmdbSection`、`loadTmdbMediaBlocks` 不再自己先绑定、`applyTmdbResultNow` 不再重复 `renderSeasonSelection`），编译与 521 例单测全绿，但设备实测**没有改善**：

| 构建 | `apply loaded cost` 样本 | 中位 | 均值 |
| --- | --- | ---: | ---: |
| 无 W5 | 153 / 211 / 180 / 166 / 163 / 162 ms | 164 ms | 172 ms |
| 有 W5 | 221 / 180 / 224 / 183 ms | 202 ms | 202 ms |

结论：被删掉的几次调用不在关键路径上（`bindPage` 内的 `bindTmdbSection` 与删除的那次几乎不重叠于可见工作；`rebindAttached` 对同数据本就是空操作），而上屏那一帧的大头是 `bindBackdrop`/`bindFlags`/`renderEpisodes` 与本机调度抖动。按计划中「实测不达标就放弃」的条款，W5 已 `git checkout` 回滚、未提交；结论记在此处，避免以后重复试做。

### 9.4 W2 共享线程池阻塞：已实施

- 结构：`loadContent` 的协调任务只负责站点详情、策略判定与派发；把「等 TMDB 结果 → 必要时补搜索/消歧 → 预取首季 → 回 UI 绑定」整体抽成 `completeTmdbLoad(...)`，提交到 `Task.largeExecutor()`（20 线程）执行。共享 `Task.executor()`（5 线程）在派发后立即释放，不再被一次 1–3 秒的等待长期占用。
- 语义：`detailTasks.submit(...)` 在作用域内继承同一代际，`cancelAll()/close()` 仍会中断该收尾任务；方法内每一处 UI 交接都保留 `generation != loadGeneration` 校验（4 处）；失败路径与首季预取逻辑逐字保留。
- 验证：58→59 个测试类 / 519→521 例全绿（新增 `TmdbDetailPoolBlockingSourceTest` 2 例：协调任务体内不得出现 `tmdbFuture.get()`、收尾任务必须由 `Task.largeExecutor()` 承载且保留代际/季预取/失败回退）；设备 3 次冷启（`w2-known-1..3`）阶段结构不变（`prefetch hit`、`bundle cost` 仍是毫秒级、`tmdb wait` 701/9/409 ms、上屏总时长 1233/1610/1083 ms）。
- 预期收益如实说明：单页首屏时延基本不变（同一段工作只是换了执行线程）；收益在并发场景——首页刷新、搜索、站点加载等共用这 5 个线程时，不再出现"开一个详情页就少一个公共线程"的排队。

## 10. 后续（未做）

1. **W3 端到端复核**：需要一条同名多候选（会真正进入消歧）的条目；拿到后按 §9.1 的成对方法复测。
2. **详情页 `recommendations`/`similar` 拆分**：内嵌块占同次缓存样本（约 76 KB）的 23%+22%（缓存实测 recommendations 17,391 B / similar 16,908 B，合计 46%；与 §4 的 `tv/1399` 446,404 B 样本不是同一基数），但要改成两个独立 `/recommendations`、`/similar` 请求；主机侧实测这两个请求本身各需 ~1.0–1.6 s，而详情里内嵌这些块只多 ~0.2–0.5 s，按当前网络没有收益，除非 TMDB 延迟显著降低或改成后台加载。

## 11. 回滚

W1、W2、W3、W4 互不依赖：W1 回滚 = 去掉 `startTmdbDetailPrefetch`/`loadPrefetchedTmdbBundle` 与 `detailPrefetch` 字段（`TmdbDetailPrefetch` 可继续对包内可见），并把 C16 §6.3 恢复为「请求数为 0」；W2 回滚 = 把 `completeTmdbLoad` 的等待与收尾逻辑并回 `loadContent` 的协调任务（等价于 §9.4 之前的单任务结构）；W3 回滚 = 还原 `TmdbService` 的 lite 入口与 `TmdbCandidate` 结构；W4 回滚 = 去掉 `setRail*` 档位替换与 `heroPosterTier`。四者均不触碰匹配阈值与源数据优先级。
