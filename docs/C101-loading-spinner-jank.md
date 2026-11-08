# C101 — 详情页/播放页「加载中圆圈」卡顿修复

任务 ID：`C101`（C10x 已被 C10-beta-conflict-resolution / C10-binary-upstream-alignment / C100-beta-merge-review-dev3 占用，故取 C101）
分支：`dev2`　基线 HEAD：`dcf9828cba616f07ea7b21ebc4053b691522a208`
开始时间：2026-10-24（Asia/Shanghai）
状态：**实施中**

## Recovery anchor

- 目标：让「进入详情页/播放页时加载中动画（含播放器加载圈）」不再卡顿（转圈中途停住再恢复）。
- 验收：同一探针脚本 `scripts/perf/loading_jank_probe.sh`、debug_log 开/关两态各 3 次采样，进入卡片窗口 **Janky ≤20%、p99 ≤120ms、无 `Skipped ≥60 frames!`**；功能不变（详情渲染、内联自动播放、详情直放、换源/重匹配）；回归门禁全绿。
- 当前文件/符号：见 §5 分阶段。
- 已完成：探针脚本打通并采到基线；根因全部有证据（§3）；S1–S5、S7、S9 已实现（§9/§11/§12），其中 S1–S7 已提交并带 recovery tag。
- 未验证的工作区改动：无（S9 代码与测试已通过定向 + 双 flavor 全量单测）。
- 未决风险：设备是 interpret-only debug 构建，绝对帧时被放大（故用相对基线）；模拟器 uiautomator 不可用（已绕开）；模拟器 janky% 有 ~44.6% 的输入延迟地板（§10.2），判定看 p99 与 framestats 相位。
- 下一步（唯一）：完成 S9 的 guard finish 提交与 recovery tag，然后用探针做冷/热 A/B 复测（对比 §12.1 的 992ms/365ms 与 `Skipped 65 frames`）。

## 1. 问题陈述（用户可见现象）

用户报告：进入详情页与播放页时，加载中动画（包含播放器加载圈）都会卡顿，**转圈动画中途停住再恢复**，体验很差。

机制：加载动画是 `CircularProgressIndicator` / `ProgressBar` 的 indeterminate 动画，由 RenderThread/动画时钟驱动，本身不吃主线程。**一旦主线程在某一帧被长段 Java 工作占住，动画就停帧**——「停住再恢复」就是主线程单帧超时。

## 2. 复现与量化基线

设备：`emulator-5556`（SM-N9700 / marlin / Android 9 / SDK 28），安装的是**本仓库 mobile flavor debug 构建**（base.apk 203,989,727 字节，与 `app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk` 字节数一致）。

**重要导航事实**：mobile flavor 上点击首页卡片**直接打开 `VideoActivity`，不经过 `TmdbDetailActivity`**（`mCurrentFocus=...VideoActivity`）——详情壳、TMDB 详情加载、内嵌播放器全在同一个 Activity 内。因此探针按「一次点击、两个窗口」测量：`load-early`（tap+3s，圈在位）与 `load-full`（等到 `canSave=true`）。

探针：`scripts/perf/loading_jank_probe.sh`（`--tag/--runs/--serial/--debug-log on|off|keep/--launch-wait/--early-wait/--ready-timeout/--item-xy`），落盘 `.wh_diag/perf/<tag>/run<N>/`（gfxinfo、framestats、logcat、截屏、markers、summary）。

### 基线实测（2026-10-24 10:00，debug_log=off）

| run | total | janky | janky% | p99 | maxSkipped | davey |
|---|---|---|---|---|---|---|
| 1 | 172 | 99 | 57.56% | 650ms | 47 | 1 |
| 2 | 150 | 85 | 56.67% | 550ms | 55 | 1 |
| 3 | 151 | 43 | 28.48% | 450ms | 49 | 1 |

中位 **≈56.7%**，p99 450–650ms，均远超验收线（≤20% / ≤120ms）。
另一轮独立基线（`.wh_diag/perf/off_gfx.txt`）：Total 168 / Janky 79 (47.02%) / 99th 350ms / Missed Vsync 4 / Slow UI thread 4。logcat 另有 `Skipped 189/109/87/70/34 frames!` 与 `Davey! duration=723–1825ms`（`DrawStart→FrameCompleted` 跨 800–1400ms，而 `PerformTraversalsStart` 紧跟 Vsync → 卡在 traversal 内部）。

## 3. 根因（全部有直接证据）

### F1 详情页 onCreate/initView/initPage 同步段过重
`TmdbDetailActivity.initView`（主线程）顺序执行 `inflate(activity_tmdb_detail.xml)`（1590 行、78,955 字节，`getBinding()` 每次 inflate）→ `initPage()`（20+ setOnClickListener、4 个 setOnKeyListener、6 个 nextFocus、7 个 adapter 注册）→ `applyDetailTemplate()`（逐条 setLayoutParams/setPadding/setHeight/setRadius/setTextSize）。
调用图证据：`BaseActivity.onCreate` inclusive **99.71%**、`CustomRecyclerView.onMeasure` **99.46%**、`EpisodeAdapter.onCreateViewHolder` 93.10%。
XML 事实：`@+id/loading`（148dp×112dp，含 `ProgressBar indeterminate` + `loadingText`）**没有 android:visibility 属性 → 默认 VISIBLE**，即 inflate 完成瞬间圈就在屏上，而紧接着的主线程工作会立刻把它冻住。

### F2 结果应用整段塞进同一主线程 tick
`applyLoaded(...)`（`TmdbDetailActivity.java:2803-2833`）在**同一主线程 tick** 内连跑：`binding.loading.setVisibility(GONE)`（首行）→ `TmdbEpisodeSorter.sort` → `clearEpisodeRenderCaches` → `applyTmdbBundle` → `enrichVod` → `initHistory` → `bindPage()`（内含 bindBackdrop/Header/Meta/Ratings/Overview/Source/Flags/loadRelatedVideosForCurrentContext/bindTmdbSection/updateKeepState/updateFollowingState/requestDetailPlayFocus）→ `focusInlinePlayerPanel` → `maybeAutoPlayInline` → `loadTmdbMediaBlocks`/bindTmdbSection → 可能 `showTmdbMatchDialog`。
`applyTmdbResultNow(...)`（:2905-2927）同构（再叠加 `renderEpisodes()`）。
`runOnAliveUi(Runnable)`（:2750-2755）只是 `runOnUiThread`，**没有任何帧对齐/分片能力** → 整段落在单帧里，圈停帧。

### F3 播放服务绑定在主线程同步构造整个播放器
`PlaybackActivity.initView`（:1105）→ `bindPlaybackService()`（:608-616）→ `startService+bindService` → `PlaybackService.onCreate`（:140-161）全同步在主线程：`new PlayerManager(this)` → `buildEngine` → `ExoPlayerEngine.<init>` → `ExoUtil.buildPlayer` → `ExoPlayer.Builder.build()`（含 renderers/trackSelector/analytics），再 `new MediaLibrarySession.Builder(...).build()`、`setupNotification()`。
调用图证据：`PlaybackService.onCreate` inclusive **99.52%** → `PlayerManager.<init>` 96.45% → `PlayerManager.buildEngine` 96.77% → `ExoPlayerEngine.<init>` 98.00% → `ExoPlayerImpl.<init>` 98.84%。

### A1 诊断日志在调用线程做多轮正则清洗（仅在 debug_log 打开时）
`catvod` 是**仓库内 Gradle 子模块**（`settings.gradle:28-32`），`DebugLogStore/SpiderDebug/DiagnosticText/DiagnosticLogBuffer` 都是**被跟踪的源码**（可改）。
一次 `SpiderDebug.log(tag, msg, args)` = acceptsTag + String.format + `DiagnosticText.clean(message)` ×2 + `clean(tag)` ×1；`DiagnosticLogBuffer.offer` 再 clean(线程名) + 整串 UTF-8 计量。
`DiagnosticText.clean`（:27-49）**顺序跑 6 个 Pattern.matcher(value).replaceAll(...)**（HEADER→SECRET→BEARER→URI_TEXT→LOCAL_PATH→ADDRESS），短 ASCII 也全跑；`origins` 再跑一遍 URI_TEXT。
调用图（决定性）：`Choreographer.doFrame → ViewRootImpl traversal → SpiderDebug.log → DebugLogStore.add → DiagnosticLogBuffer.add → DiagnosticText.clean (83.33%) → Matcher.replaceAll (80%) → icu_60::RegexMatcher::MatchChunkAt`；主线程自占比第一 **4.39%**。全仓 `SpiderDebug.log(` 调用点 **1079 个**；[main] 线程 10 分钟约 **2900 条**诊断写入。
注：设备 prefs 里 debug_log 本次实测为 true（用户此前手动开启），是量级放大器；关掉后 5s 内日志不再增长（6KB/s → 0）。**但关日志只降量级不消除卡顿**（关后仍 47% janky）——F1/F2/F3 才是主因。

### A3 ProgressLayout 每次 CONTENT 都新建动画
`ProgressLayout.showView(View)`（:331-335）= setAlpha(0f); setVisibility(VISIBLE); `view.animate().alpha(1f).setDuration(100);`——每次 showContent() 都新建 ViewPropertyAnimator 并触发额外 invalidate/重绘。`hideView` = setVisibility(INVISIBLE)（无 animate().cancel()）。

### A4 ImageHeaderPolicy 每次调用重编译正则
`ImageHeaderPolicy.java:25-27`：`host.matches("img\\d+\\.doubanio\\.com")` 每次调用都重新编译 Pattern（列表/详情 item 绑定高频路径，调用图 `ImageHeaderPolicy.doubanImageReferer` 出现占比 80.00%）。

### 已排除（避免重复试做）
- `ThemeController.bindTheme` 在 leanback/mobile BaseActivity 各调两次：`ThemeBinder.bind` 在 `baseline.equals(active)` 时**直接 return**（未自定义主题零成本）；且 `ThemeBinderContractTest` 主动断言这两次调用存在，**不可删**。
- `PlaybackPerformanceSetting.ensureInitialized()` 每次串行 13 个 migrate：每个 migrate 首行都是 Prefers 读 → 稳态就是 13 次 SP 读，轻；调用频度高但非瓶颈（无自占比证据）。
- `ResUtil.getScreenWidth/Height` 无缓存、`History.findPlayback` 主线程 Room、`AppDatabase.allowMainThreadQueries()`：只有 inclusive 占比，缺自占比证据，**本轮不授权**。
- 首页 WebView JNI（CheckJNI::CallMethodV）ANR 栈属于 HomeActivity 路径，**不是详情/播放页原因**。
- `grep cost= .wh_diag/perf` 无命中 → 设备日志已被滚动覆盖，需重采。

### 度量设施现状
全仓 `grep Choreographer|FrameMetrics|getFrameMetrics|jank app/src/main/java` **零命中** → 项目没有任何掉帧度量设施；也没有 baseline profile（全 gradle 零命中）。设备 `Dexopt state: x86_64: [status=verify] [reason=boot]`（interpret-only，主线程 simpleperf 全是 art::interpreter::DoCall / Class::FindClassMethod / FindFieldFast）→ **绝对帧时被放大**，验收用相对基线。

## 4. 最佳实践调研（设计门禁）

| 来源 | 证据等级 | 支持的结论 | WebHTV 适用性 | 限制 |
|---|---|---|---|---|
| https://developer.android.com/topic/performance/jankstats （JankStats Library，访问 2026-10-24） | 官方文档 | 官方掉帧度量手段是 JankStats/FrameMetrics，可在生产环境采集每帧状态与卡顿原因 | 本项目**完全没有**掉帧度量设施；后续可引入 | 新增依赖，超出本次最小修复范围 → 列为后续建议 |
| https://developer.android.com/topic/performance/vitals/render （Slow rendering，访问 2026-10-24） | 官方文档 | slow rendering 以慢帧占比衡量，建议先定位主线程长任务 | 与本次 gfxinfo janky% + p99 口径一致 | **web_fetch 对 developer.android.com 只返回 ~100KB devsite 外壳 HTML，无法抽取正文**；结论取自 web_search 摘要 |
| https://developer.android.com/reference/androidx/media3/session/MediaSessionService （Media3 服务，访问 2026-10-24） | 官方文档（摘要） | 媒体会话服务创建是重活，官方推荐服务可后台创建、UI 不阻塞等待 | 本项目 PlaybackService.onCreate 全在主线程同步构造 PlayerManager+ExoPlayer | 文档正文未能直接抓取 |
| https://stackoverflow.com/questions/78378101/circularprogressindicator-shows-too-late-when-time-consuming-code-has-finished （访问 2026-10-24） | 社区实证 | CircularProgressIndicator 会因主线程耗时被推迟显示/停滞 | 与用户现象「转圈停住再恢复」完全对应 | 社区证据非权威，仅用于解释机制 |
| https://mintlify.wiki/AmolPardeshi99/android-performance-skills/anr-jank/view-system （访问 2026-10-24） | 社区整理 | view 系统卡顿主因：layout inflation、RecyclerView 绑定、onDraw | 与 F1/F2 证据一致 | 同上 |
| 仓库内 `docs/TMDB-DETAIL-PERF-01-tmdb-detail-load-render.md`（W5 §9.3） | 一手（本项目实测） | **主线程渲染去重（W5）已试做并回滚：中位 164→202ms 无改善** | 直接排除「去重」路线 | 同一设备类 |

### 方案对比

| 方案 | 正确性 | 兼容性 | 性能 | 风险 | 结论 |
|---|---|---|---|---|---|
| **no change** | — | — | 维持 47–57% janky | 无 | 不满足用户诉求 |
| **上游/官方原样**（JankStats + baseline profile + AsyncLayoutInflater 全面异步膨胀） | 高 | 需新增依赖与 profileinstaller | 长期收益最大但短期不可测 | 改动面大、需 ABI/构建配套、超出本问题范围 | **暂缓**，列后续建议 |
| **本仓最窄适配**（本次采纳） | 不改功能，仅让帧/降本 | 零新依赖、零 API 变更 | 直接命中 F1/F2/F3 + A1/A3/A4 | 每阶段可独立回滚 | **采纳** |

**决策**：采纳「本仓最窄适配」，按 S1→S5 分阶段，先做有 inclusive 证据的 F3/F2（让帧），再做 A1/A3/A4（降本）。**不引入新依赖、不改日志格式/保留策略/分类语义、不删调用点、不重构 TmdbDetailActivity 结构、不修 catvod 既有失败测试**（DebugLogStoreTest 引用已删的 trimLines/readTail，merge-base 与 origin/beta 上同样失败）。

**回滚**：每阶段一个独立 guard 会话 + 独立 commit + `recovery/<id>/<ts>` tag；回滚 = `git revert <该阶段 commit>`。S1 单独回滚只需删两处覆写。

## 5. 分阶段实施

### S1（P0）播放页延后绑定服务
- 两 flavor 的 `VideoActivity` 各覆写 `protected boolean deferPlaybackServiceBinding() { return true; }`。
- 基类已有闲置闸门：`PlaybackActivity.java:206`（定义，全 app/src 仅 3 处命中、**无任何子类覆写**）、`:1105`（判定）、`:618-630 bindPlaybackServiceAfterFirstFrame()`（addOnPreDrawListener → root.post）。
- 增加兜底：`bindPlaybackServiceAfterFirstFrame()` 里 `root.postDelayed(this::bindPlaybackServiceIfIdle, 500L)`，新增 `private void bindPlaybackServiceIfIdle() { if (!isFinishing() && !isDestroyed() && !bound) bindPlaybackService(); }`。
- **不得改** `bindPlaybackService()` 内 `if (bound || shouldRejectPlaybackConnection()) return;` 字面量与两个方法签名（`VideoActivityLayoutTest.java:1873` 用它们作 methodBody 锚点）。
- 安全性已核：service()/player()/isServiceReady() null 安全；isOwner() 在 mService==null 返回 false；onResume/onStop 有 mService != null 守卫；syncKeepScreenOn() 仍在 onServiceConnected 内。

### S2（P0）详情页结果应用按帧分片（TmdbDetailActivity.java）
- 新增 `ArrayDeque<Runnable> renderChunks` + `boolean renderChunkScheduled`（:472 字段区）与 enqueueRenderChunk/scheduleRenderChunk/runNextRenderChunk/clearRenderChunks（插在 runOnAliveUi/canTouchUi（:2756）之后，避开测试 methodBody 切片）。
- scheduleRenderChunk 用 `binding.getRoot().postOnAnimation(...)`（未 attach 回落 App.post）；runNextRenderChunk 先查 canTouchUi()，pollFirst()，try/catch 兜底并强制 loading GONE，队列非空续排。
- applyLoaded：保留同步段（早退分支、vod 赋值/sort/clearEpisodeRenderCaches/applyTmdbBundle/saveTmdbMatch/enrichVod/initHistory/history==null 早退），捕获 `final int generation = loadGeneration`，入队四片：①generation 校验 + bindPage() + applySourceOnlyActionVisibility()；②focusInlinePlayerPanel() + maybeAutoPlayInline()；③loadTmdbMediaBlocks 或 bindTmdbSection；④**首行才** binding.loading.setVisibility(GONE) + showTmdbMatchDialog + 原 apply loaded cost=%dms 日志。
- applyTmdbResultNow 同构分片。
- resetDetailState() 与 onDestroy() 首部各加 clearRenderChunks()。
- **不变量**：`applyLoaded(finalVod, initialBundle, new ArrayList<>(), finalError, false);` 五参调用字面量保持（`TmdbDetailSourcePayloadTest.java:18` 断言）。

### S3（P1）诊断降本（catvod）
- DiagnosticText：6 个 Pattern 各加必要条件预判（HEADER/SECRET 需含 : 或 =；URI_TEXT 需含 / 或 :；LOCAL_PATH 需含 /；ADDRESS 需含 . 或 :；BEARER 需含 b/B）；新增 `public static String sanitizeTag(String)`（无正则）。
- DiagnosticLogBuffer.offer：线程名 ThreadLocal 缓存；safeTag 有界 LinkedHashMap(128) 缓存。
- DebugLogStore 新增包内 `addCleaned(String, DiagnosticText.Clean, boolean)`；SpiderDebug.log(tag,msg,args) 改为清洗一次复用同一 Clean，safeTag 换 sanitizeTag。
- PlaybackDiskBufferStore.log（:73-79，无去重）比照 logQuery（:81-89，已有 resultMs+间隔去重）加间隔去重。
- 必须保持通过：DiagnosticTextTest 7 例（含 10 万字符 timeout 用例与 8 个 secret 抹除断言）、DiagnosticLogBufferTest 8 例、CrawlerLogCategoryTest、DebugEventLimiterTest。

### S4（P1）ProgressLayout 幂等
- showView 加 `if (view.getVisibility() == VISIBLE && view.getAlpha() == 1f) return;` + `view.animate().cancel()`；hideView 加 `view.animate().cancel()`。
- 语义不变（CONTENT 仍 100ms 淡入，其余仍 INVISIBLE）。
- 新增 `app/src/test/java/com/fongmi/android/tv/ui/custom/ProgressLayoutContractTest.java`（源码文本契约风格；mobile 侧无 Robolectric，行为型测试只能放 app/src/testLeanback）。

### S5（P2）ImageHeaderPolicy 正则预编译
- 改为 `private static final Pattern DOUBAN_IMAGE_HOST` + `DOUBAN_IMAGE_HOST.matcher(host).matches()`。

### S6（P2，门禁后才决定）
仅当批次一+二后详情页进入仍 Janky>40%，才做最窄 F1 收窄（首屏外区块 ViewStub **或** 最重子块 AsyncLayoutInflater），且先补自占比证据（`simpleperf record --app <pkg> --duration 20`，**禁用 -p**，实测 -p 恒 0 samples）。

## 6. 验证

一次性跑完、不重复：
- 探针：`MSYS_NO_PATHCONV=1 bash scripts/perf/loading_jank_probe.sh --tag <tag> --runs 3 --serial emulator-5556`（debug_log 开/关各一轮）。
- 编译门禁：`bash ./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac`
- 单测：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest :app:testLeanbackArm64_v8aDebugUnitTest`
- S3 追加：`bash ./gradlew :catvod:compileDebugJavaWithJavac` + `bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests '*DiagnosticTextRedactionTest'`。**`:catvod:testDebugUnitTest` 不可用**：`catvod/src/test/java/com/github/catvod/crawler/DebugLogStoreTest.java:22/:34` 仍引用已删除的 `DebugLogStore.trimLines/readTail`，catvod 测试源集整体无法编译（既有缺陷，本任务不修），S3 的契约测试因此改落在 app 侧可运行源集。
- 安装采样：`bash scripts/build_arm64_debug_install.sh --flavor mobile --abi arm64-v8a --serial emulator-5556`。
- leanback 复验 1 次：装 emulator-5558。

## 7. 明确不做

不加依赖（JankStats/profileinstaller/baseline profile 仅作后续建议）；不改日志格式/保留策略/分类/开关，不删 SpiderDebug.log 调用点；不修 catvod 既有失败测试；不重构 TmdbDetailActivity 结构；**不动 ThemeController.bindTheme 双次调用**（ThemeBinderContractTest 断言其存在）；除 S6 授权外不引入 AsyncLayoutInflater；不动 allowMainThreadQueries 与 History.findPlayback 主线程调用；不 push、不 stage .wh_diag/。

## 8. 假设与风险

- debug_log 此前为用户手动开启（设备实测 true，本轮已置 false，还原点 /data/local/tmp/prefs.bak）→ A1/S3 的收益只在开日志时体现；F1/F2/F3 与日志无关。
- 设备为 interpret-only debug 构建 → 绝对帧时被放大，验收以相对基线（同脚本、同设备、3 次采样）判定。
- applyTmdbResultNow 分片可能让剧集列表出现一帧「先空后满」；若不接受，把 chunk② 合并回①。
- S1 若与未知子类时序冲突，以「详情内联起播 + 直进播放页」两条路径各 3 次采样否决。

## 9. 实施记录

（每阶段完成后追加：改动文件、验证命令、结果、commit/tag）

- 2026-10-24 09:5x　探针 scripts/perf/loading_jank_probe.sh 打通（修复：$@ 占位符被替换、set -o pipefail 下 dumpsys|grep -q 的 SIGPIPE 141 假阴性、grep -c 双输出、debug_log sed 引号）。基线 baseline-off 3 次：57.56% / 56.67% / 28.48% janky，p99 450–650ms。

- 2026-10-24 13:5x　**S1 完成**（播放页延后绑定服务）。改动：`app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java`（`bindPlaybackServiceAfterFirstFrame()` 内加 `root.postDelayed(this::bindPlaybackServiceIfIdle, 500L)` 兜底 + 新增 `private void bindPlaybackServiceIfIdle()`）、`app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` 与 `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`（各加 `@Override protected boolean deferPlaybackServiceBinding() { return true; }`）。验证：双 flavor `:app:compile{Leanback,Mobile}Arm64_v8aDebugJavaWithJavac` BUILD SUCCESSFUL（6m25s）；全量单测 5320 tests / 4 failed（4 个均为既有 CRLF 冤案）；设备 emulator-5556 实测 s1-off3 中位 janky 37.3%、Davey 0、maxSkipped 36（基线中位 56.7%）→ 保留。commit `f0976cdb2499e481c421fa3afd00b20b214ed361`，tag `recovery/C101-S1/20261024024932-f0976cdb2499`。
- 2026-10-24 03:5x　**S2 完成**（详情页结果应用按帧分片）。改动：仅 `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`（新增 `import java.util.ArrayDeque;`、字段 `renderChunks`/`renderChunkScheduled`、助手 `enqueueRenderChunk/scheduleRenderChunk/runNextRenderChunk/clearRenderChunks`，applyLoaded 切 4 片、applyTmdbResultNow 切 3 片，每片带 generation 校验；resetDetailState 与 onDestroy 首部 clearRenderChunks）。96 insertions / 25 deletions。验证：双 flavor compile SUCCESSFUL；全量单测与 S1 同一批 4 failed（无新增）。设备实测 3 次：45.45% / 27.27% / 77.87% janky，p99 750–850ms（仍高于 ≤120ms 验收线，未达门禁）。commit `442ee30e9ac3531581d288cc18e521e7d7128478`，tag `recovery/C101-S2/20261024035727-442ee30e9ac3`。
- 2026-10-24 13:0x–14:0x　**S3 完成**（诊断降本）。commit `800945de7c3e83373ca6746532ff5e32e04529b0`（7 files changed, 276 insertions(+), 26 deletions(-)），tag `recovery/C101-S3/20261024060418-800945de7c3e`。改动 5 个源文件：`catvod/src/main/java/com/github/catvod/crawler/diagnostics/DiagnosticText.java`（6 个 Pattern 前置必要条件 gate + `escape()` 先扫描决定是否重建 + 新增 `public static String sanitizeTag(String)`）、`catvod/src/main/java/com/github/catvod/crawler/diagnostics/DiagnosticLogBuffer.java`（`THREAD_LABEL` ThreadLocal 缓存线程标签、新增 `public void addCleaned(String, DiagnosticText.Clean, String raw, boolean)`、`add` 改写为调用它）、`catvod/src/main/java/com/github/catvod/crawler/DebugLogStore.java`（新增包内 `addCleaned`）、`catvod/src/main/java/com/github/catvod/crawler/SpiderDebug.java`（`log` 全路径清洗一次复用，`safeTag` 换 `sanitizeTag`）、`app/src/main/java/com/fongmi/android/tv/player/cache/PlaybackDiskBufferStore.java`（`log` 加 `RANGE_LOG_INTERVAL_MS=2000` 同 resultMs 节流）。共 100 insertions / 25 deletions，另有新增测试 `app/src/test/java/com/fongmi/android/tv/diagnostics/DiagnosticTextRedactionTest.java`（9 例）。
  - 行为等价证据（仓库外 `/f/Workspace/webtv2/whdiag/s3/` harness）：对设备真实诊断日志 4 段共 15,182 行逐行比较 `clean().text()/truncated()` 与 `origins()` → `cleanDiffs=0 originsDiffs=0 truncationDiffs=0`；26 个合成对抗用例同样全 0；`sanitizeTag` 414 例 `diffs=0`。
  - 收益证据（同 harness 按真实调用配方「一次 `SpiderDebug.log(tag,msg,args)`」计时，15,428 行）：原实现 38.9–41.5s（2,521–2,692 us/行）→ 新实现 12.0–14.7s（778–953 us/行），即 2.6–3.2x 加速、每行省 ~1.6–1.8ms 主线程。正则应用次数从恒定 6 次/行降到按字符类 gate（语料实测 84,507 vs 120,366 次应用），真实命中仅 62/1408/1672/1330 次。
  - 验证：`:catvod:compileDebugJavaWithJavac` BUILD SUCCESSFUL（38s）；`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests '*DiagnosticTextRedactionTest'` BUILD SUCCESSFUL（9 tests, 0 failed）；`:app:testMobileArm64_v8aDebugUnitTest` 全量 5329 tests / 4 failed（全是既有 CRLF 冤案：ReaderPlaybackRoutingSourceTest ×2、TmdbSourceOnlyInteractionTest、WebThemeTokenSourceTest，均与 S3 无关）；`:app:testLeanbackArm64_v8aDebugUnitTest` 全量 4546 tests / 6 failed = mobile 那 4 个 + `SearchResultDownFocusTest.scrollCallbacksDeferResultLoadingUntilAfterLayout` 与 `NativeEnhancedPlaybackStyleFocusTest.everyTmdbRowCardCarriesItsOwnVerticalFocusTargets`；后两者同样是工作区 CRLF 冤案（`git show <任意版本>` 里该字面量含 LF 且 `grep -Pzo` 能命中，全仓工作区被 CRLF 转换，测试硬编码 LF）→ 6 个失败全部与 S3 无关。
  - 环境备注：本轮误触发了 Python 安装管理器，它把 3.15.1 装进了仓库根 `Python/`（未跟踪），已 `rm -rf` 清除；后续需要脚本时改用 JDK 21 的 `jshell`/`javac` 或仓库外目录。
- 2026-10-24 14:1x–14:2x　**S4 完成**（ProgressLayout 幂等切换）。改动：`app/src/main/java/com/fongmi/android/tv/ui/custom/ProgressLayout.java` 的 `showView(View)` 加「已 VISIBLE 且 alpha==1f 直接返回」幂等守卫 + `view.animate().cancel()` 后再 `setAlpha(0f)/setVisibility(VISIBLE)/animate().alpha(1f).setDuration(100)`；`hideView(View)` 加「已 INVISIBLE 直接返回」+ `cancel()` 后再 `setVisibility(INVISIBLE)`。淡入本身保留（删掉是另一种回退）。新增测试 `app/src/test/java/com/fongmi/android/tv/ui/custom/ProgressLayoutContractTest.java`（4 例，源码契约风格；helper 先把 CRLF 归一化再切片，避开本仓既有行尾冤案）。
  - 验证：`:app:testMobileArm64_v8aDebugUnitTest --tests '*ProgressLayoutContractTest'` BUILD SUCCESSFUL（4 tests, 0 failed，test-results XML `tests="4" failures="0" errors="0"`）；双 flavor compile SUCCESSFUL；`:app:testMobileArm64_v8aDebugUnitTest` 全量 **5333 tests / 4 failed**、`:app:testLeanbackArm64_v8aDebugUnitTest` 全量 **4550 tests / 6 failed**——与 S3 时同一批既有 CRLF 冤案（mobile: ReaderPlaybackRoutingSourceTest ×2、TmdbSourceOnlyInteractionTest、WebThemeTokenSourceTest；leanback 再加 SearchResultDownFocusTest、NativeEnhancedPlaybackStyleFocusTest），无新增失败。
  - 收益定位：进度圈本身由 RenderThread 驱动，切换时省掉一次 `setAlpha(0)`（会请求一次绘制）+ 避免新建 ViewPropertyAnimator，等于把加载窗口内的无效帧请求去掉；本项单独不足以跨过 ≤120ms p99 门禁，与 F1/F2 是叠加关系。
- 2026-10-24 14:3x　**S5 完成**（豆瓣图片 host 正则预编译）。改动：`app/src/main/java/com/fongmi/android/tv/utils/ImageHeaderPolicy.java` 新增 `private static final Pattern DOUBAN_IMAGE_HOST = Pattern.compile("img\\d+\\.doubanio\\.com");`，`isDoubanImageHost` 由 `host.matches(...)`（每次调用重新编译正则）改为 `DOUBAN_IMAGE_HOST.matcher(host).matches()`；`app/src/test/java/com/fongmi/android/tv/utils/ImageHeaderPolicyTest.java` 增 2 例（全名匹配边界：`imgx` 不匹配、`img1.doubanio.com.evil.test` 不匹配、`x.img1.doubanio.com` 不匹配、null/空/非 URL 返回空；以及 100 次重复查询稳定）。
  - 验证：`:app:testMobileArm64_v8aDebugUnitTest --tests '*ImageHeaderPolicyTest'` BUILD SUCCESSFUL（6 tests, 0 failed，test-results XML `tests="6" failures="0" errors="0"`）；模块编译随该任务通过。
  - 收益定位：本项是「主线程固定小开销」类的清理，单次省的是正则编译（数十微秒级），只在图片列表反复走 referer 判定时累积；与 F1/F2/S1 属叠加关系，不单独作为验收判据。

## 10. 验收复测与归因结论（2026-10-24 16:0x–16:3x）

### 10.1 验收数据（emulator-5556，mobile debug ≥196MB，`debug_log=false`，探针 `scripts/perf/loading_jank_probe.sh --runs 3`）

| 路径 | tag | Total | Janky | janky% | p50 | p95 | p99 | Slow UI thread | High input latency | Missed Vsync |
|---|---|---|---|---|---|---|---|---|---|---|
| 详情页 TmdbDetailActivity | s5-off | 615/596/581 | 364/253/373 | 59.19/42.45/64.20 | 18/10/18ms | 23/22/24ms | **53/48/38ms** | 6/5/2 | 579/444/543 | 3/3/3 |
| 播放页 VideoActivity | s5-play2 | 100/131/119 | 36/81/71 | 36.00/61.83/59.66 | 14/17/17ms | 200/150/-ms | **750/500/500ms** | 1/3/- | 99/118/- | 1/3/3 |
| 基线（改动前） | baseline-off | 120/143/145 | 63/81/40 | 52.50/56.64/27.59 | 17/18/7ms | - | **650/550/450ms** | 0–12 | 110/78/122 | - |

- **p99 收益明确**：详情页从基线 450–650ms 降到 **38–53ms**（已优于 ≤120ms 门禁）；播放页仍为 500–750ms。
- 详情页 `Slow UI thread` 只剩 2–6 帧，`Slow issue draw commands` 7–15 帧；播放页 `Slow UI thread` 1–3 帧。**主线程长任务（用户抱怨的「转圈中途停住」）在详情页已实质消除。**

### 10.2 janky% 不能作为本机判据（模拟器底噪实测）

- 空载对照：停在首页只做无点击滑动 12s → `Total 307 / Janky 137 = 44.63%`，p50 10ms、p99 89ms，`High input latency 307`（等于总帧数）、`Slow UI thread 0`、`Missed Vsync 0`、`Frame deadline missed 0`。
- 即 `emulator-5556` 的 input 注入延迟约 18ms/帧 > 16.7ms 帧预算，**任何界面都有 ~44.6% 的 janky 地板**；因此 §2 里「Janky ≤20%」在这台模拟器上物理不可达，本轮判定改用 **p99 + framestats 主线程耗时**。
- 该项判定修正也解释了为什么 s5-off 的 janky%（42–64%）远高于 p99 表现：计数器被 `High input latency` 撑起，与主线程无关。

### 10.3 framestats 三分解（真正的「圈停住」证据）

解析 `gfxinfo framestats` 16 列，定义 `starve = Vsync − IntendedVsync`（主线程没按时申请到 vsync = 被长任务占住）、`trav = DrawStart − PerformTraversalsStart`（主线程真实 measure/layout/draw）：

| 路径 | run | starve>100ms 帧数 | 最大 starve | trav>40ms 帧数 | 最大 trav |
|---|---|---|---|---|---|
| 播放页 baseline | 1/2/3 | 7/6/5 | 916.7/816.7/783.3ms | 3/3/3 | 296.7/295.3/243.9ms |
| 播放页 s5-play2 | 1/2/3 | 9/5/6 | 750/700/616.7ms | 3/6/3 | 238.7/243.7/236.5ms |
| 详情页 s5-off | 1/2/3 | **2/2/2** | 683.3/616.7/516.7ms | 1/1/0 | 107.4/112.8/2.2ms |

- **详情页已把长帧压到每轮只 2 次 starve、trav 基本 <115ms**；播放页仍稳定出现 5–9 次 starve>100ms 与 3–6 帧 trav>40ms。
- 播放页长帧 logcat 精确对位（s5-play2/run2）：Davy `duration=859ms` 的帧其 `IntendedVsync=8344.349s → Vsync=8345.049s`（starve 700ms）、`trav=147.5ms`，落在 `15:15:48.983 VideoActivity: onStart: calling mClock.stop().start()` → `15:15:49.046 Skipped 42 frames!` 之间；其余长帧（trav 47.2/243.7/113.9/43.3/45.7ms）落在 `49.526 ExoPlayerImpl: Init` → `49.629 vulkan searching for layers` → `49.722 MediaSessionImpl: Init` → `49.826/49.867 MediaController: Init` → `49.850 Skipped 31 frames!` 这一段，即 **ExoPlayer/MediaSession/MediaController 初始化 + native bridge 加载 `libexo_dovi_renderer.so` 全压在首帧窗口的主线程**。
- 排除项复核：`ExoDv5Native.java:26 private static final LoadResult LOAD_RESULT = loadLibrary();` 已是类初始化期一次性执行（logcat 里 `libnb: enter native_bridge2_getTrampoline ... nativeProbeCapabilities` 连打 7 行是 native bridge 每个 trampoline 两行的固有形态，不是重复加载）；`setContextWall` 切换背景与图片完成相隔 3ms（缓存命中）→ **两者均非热点，勿再怀疑**。

### 10.4 结论

1. **S1–S5 达成目标主体**：详情页「转圈中途停住」的长任务已从每轮 7 帧级降到 2 次 starve、trav<115ms，p99 450–650ms → 38–53ms。
2. **播放页仍有残留**：首帧窗口内 ExoPlayer/MediaSession/MediaController/vulkan/native bridge 的主线程初始化仍造成 3–6 帧 trav 40–244ms、5–9 次 starve>100ms，p99 500–750ms。
3. **janky% 门禁作废**（见 10.2），后续验收统一看 p99 与 starve/trav。

### 10.5 S6 决策：本轮不做

§5 的 S6 触发条件是「详情页进入仍 Janky>40% 且需补自占比证据」。按 10.2 修正判据后，详情页的真实主线程指标（trav>40ms 仅 1/1/0 帧、p99 38–53ms）已说明 **F1（XML inflate 过重）不是当前瓶颈**，做 ViewStub/AsyncLayoutInflater 收窄属无证据优化，故不启动。若后续仍要压播放页 p99，建议的下一步（需另行授权、独立任务 ID）是「把 `PlaybackService` 的 ExoPlayer/MediaSession 构建从首帧窗口挪到首帧之后」，而不是再收紧详情页 XML。

### 10.6 期间发现但不属于本任务的范围外缺陷（记录，不修）

- `catvod/src/test/java/com/github/catvod/crawler/DebugLogStoreTest.java:22/:34` 仍引用已删除的 `DebugLogStore.trimLines/readTail` → `:catvod:testDebugUnitTest` 整体不可编译（既有缺陷）。
- 工作区 `core.autocrlf=true` 导致 6 个源码契约测试硬编码 LF 断言恒失败（mobile 4、leanback +2），与本任务无关。
- `emulator-5556` 上 `simpleperf record -p <pid>` 恒 0 samples、`-e cpu-cycles` 与 dwarf callchain 均不支持、`atrace` 无 trace folder、`kill -3` 不产 `/data/anr/` → 该模拟器无法做栈级归因，播放页归因改用 logcat 时间线 + framestats。

## 11. S7（播放页首帧窗口内的固定开销）实施记录（2026-10-24 20:0x 起）

在 §10.4 的结论上继续追播放页残留。以下四项全部来自设备实测证据，不含推测性优化。

### 11.1 证据来源

- AOT 后 + `debug_log=false` 的 `s6-aot-off` 采样（emulator-5556）：load-early janky 42.86% / 36.90%，p99 1100 / 950ms；framestats 显示长帧几乎只来自 `starve`（p99 1000 / 350ms）与偶发 `trav` 383 / 434ms，`after`/`sync`/`draw` 三相均 <50ms。
- `trav=383ms` 那一帧（PT 19:44:25.719 → DS 19:44:26.102）的 logcat 覆盖 `19:44:25.523 TV-Shell: Shell command 'getprop ro.serialno'`、`getEpisodeTitles` 三次重算与 `applyResizeMode before/after` 成对刷屏。
- 一次进入播放页实测重复：`applyResizeMode before/after` 16 / 14 组、`MPV_SIZE: video layout new` 17 / 21 行；`exo-audio` 日志步骤独占 354–757ms（`19:24:45.566`→`:46.852` 中最后一段）。
- 进度 tick（`Clock.doJob` 每秒 → `PlaybackEventCollector.onProgress` → `PlaybackRecord.clientKey()` → `Device.get()`）每次都会重扫 `NetworkInterface.getByName(eth0/wlan0)`、`Settings.Secure`、`Build` 字段。

### 11.2 S7-1 `applyResizeMode` 幂等（`app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java`）

- `applyResizeModeNow` 先算完 `effectiveResizeMode` 与 `contentAspectRatio`，再用 `isResizeModeApplied(...)` 比对「上一次真正应用过的参数」（模式、有效模式、目标比例、内容比例的 `Float.floatToIntBits`、stretch）；全等则直接返回，不再打日志、不再 `setVideoAspect`、不再请求 layout。
- 新增字段 `resizeApplied / resizeAppliedMode / resizeAppliedEffectiveMode / resizeAppliedTargetAspect / resizeAppliedContentAspect / resizeAppliedStretch` 与 `invalidateAppliedResizeMode()`；后者在 `resetVideoSurfaceForDecoderSwitch()`（`render = -1;` 之后）与 `attachSurface(...)`（`setPlayer(...)` 之后）调用，保证换了 surface 一定重算。
- `applyResizeMode(int)` 的 `view.post` 二次调用保留（首帧尺寸可能尚未确定），但第二次通常命中守卫直接返回。
- 新增 `app/src/test/java/com/fongmi/android/tv/ui/activity/PlaybackActivityResizeModeIdempotenceTest.java`（4 例）：守卫先于一切副作用、六个比较项齐全、两类重绑都会失效缓存、二次 post 仍在且不自递归。

### 11.3 S7-2 移除音频能力探测（`app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java`）

- `buildAudioSink(...)` 的诊断块原本调用 `AudioCapabilities.getCapabilities(...)` 取通道数/AC3 支持，该路径会在主线程同步创建并释放 `AudioTrack`，设备实测这一行日志独占 354–757ms。
- 改为只记录本方法已算好的配置量：`SpiderDebug.log("exo-audio", "configured passthrough=%s floatOutput=%s playbackParams=%s offloadPolicy=%s", ...)`，不再触碰音频硬件；`AudioCapabilities` 的 import 一并删除。
- offload 策略装配顺序不变（`directPolicy.setAudioPassthroughEnabled(passthrough)` → `AudioTrackAudioOutputProvider.Builder`）。
- 新增 `app/src/test/java/com/fongmi/android/tv/player/exo/ExoUtilAudioCapabilityLogTest.java`（3 例）：日志不再触发硬件探测、日志本身与 `isEnabled()` 门禁保留、装配顺序不变、未用 import 不回流。

### 11.4 S7-3 设备身份进程内缓存（`app/src/main/java/com/fongmi/android/tv/bean/Device.java`、`playback/PlaybackRecord.java`）

- 稳定身份（serial / eth MAC / wlan MAC / Android ID / 型号 / 设备形态）在进程生命周期内不变，改为 `ensureIdentity()` 双检锁一次性探测，字段 `identitySerial/identityEth/identityWlan/identityUuid/identityName/identityType` 缓存。
- `Device.get()` 仍返回新的可变实体（调用方会 setter 后 `save()`），`setTime(App.time())` 与 `setIp(Server.get().getAddress())` 继续每次实时取——地址会随网络变化。
- 新增 `Device.identityUuid()`（空值归一为空串）；播放中的每秒进度 tick 走 `PlaybackRecord.clientKey()` → `Device.identityUuid()`，不再构造整个 `Device`、不再枚举网卡。
- 新增 `app/src/test/java/com/fongmi/android/tv/bean/DeviceIdentityCacheTest.java`（4 例）：`get()` 不再逐次探测、双检锁形态、热调用点用廉价访问器、返回值新鲜度约束仍成立。

### 11.5 S7-4 mobile 播放页日志与 intent 解析降本（`app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`）

- `getEpisodeTitles()` 结果缓存（`episodeTitles/episodeTitlesReady`），intent extras 由 `episodeTitleValues()` 只反序列化一次；`onNewIntent` 在 `getIntent().putExtras(intent);` 之后调用 `invalidateEpisodeTitlesCache()` 失效两层缓存。方法名与调用形态未变（多处源码契约测试依赖）。
- 去掉三处每次调用都打的 `Log.d`（`集数标题数量`、`getEpisodeTitles - values`、`isIntentTmdbPlayback`），`applyTmdbEpisodeTitles` 不再逐集打印，改为一行 `applyTmdbEpisodeTitles applied=N/M`；`Setting.isTmdbEpisodeFileSize()` 提到循环外。
- leanback 侧同名方法本来就没有这些日志，本次不动（保持两侧行为一致，仅补齐 mobile 的历史遗留）。
- 新增 `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityEpisodeTitlesCacheTest.java`（4 例）：extras 只读一次、换条目必须失效、不再逐集打日志、对外方法名不变。

### 11.6 验证

- 定向：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests '*PlaybackActivityResizeModeIdempotenceTest' --tests '*ExoUtilAudioCapabilityLogTest' --tests '*DeviceIdentityCacheTest' --tests '*VideoActivityEpisodeTitlesCacheTest'` → `BUILD SUCCESSFUL`（15 tests, 0 failed）。
- 全量：`:app:testMobileArm64_v8aDebugUnitTest` **5350 tests / 4 failed / 2 skipped**；`:app:testLeanbackArm64_v8aDebugUnitTest` **4563 tests / 6 failed / 2 skipped**。失败集合与 §10.6 记录的既有 CRLF 冤案完全一致（mobile：`ReaderPlaybackRoutingSourceTest` ×2、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`；leanback 再加 `SearchResultDownFocusTest`、`NativeEnhancedPlaybackStyleFocusTest`），无新增。
- 双 flavor `:app:compile{Mobile,Leanback}Arm64_v8aDebugJavaWithJavac` 均随上述任务通过。

## 12. S9（字幕 ProviderRegistry / CPython 初始化移出主线程）实施记录（2026-10-25 起）

### 12.1 证据：冷/热进入播放页的 A/B 对照

同一 app 进程内先冷进播放页、BACK 回首页、再热进播放页（emulator-5556，mobile，detail_open_mode=2 直进 VideoActivity，debug_log=false，已 cmd package compile -m speed -f AOT），产物在仓库外 F:/Workspace/webtv2/whdiag/perf/cold2/：

| 指标 | 冷入口 | 热入口 |
|---|---|---|
| START ... VideoActivity → VideoActivity: Clock started in initView | 992ms | 365ms |
| 主线程（tid == pid）python.stderr: <frozen importlib._bootstrap>:283: DeprecationWarning: the load_module() method is deprecated | 有 | 无 |
| Choreographer: Skipped N frames! | Skipped 65 / Skipped 30 / Skipped 43 | 无 |
| Number Slow UI thread / Missed Vsync | 15 / 14 | 11 / 11 |
| Davey | duration=791ms（starve≈333ms、trav≈372ms）、duration=984ms（starve≈717ms） | 无 |

- 冷入口那 ~1.1s 主线程停顿 = 第二次 new Loader()（字幕侧）落在主线程；首页首次 Python.start 在后台线程（home.log 里 tid=31191），所以热入口只差这一份。
- debuggerd <pid> 连续抓栈（coldseq/dbg1..8.txt）命中主线程停在 libpython3.10.so 帧 9 处 + libhoudini.so + art::ArtField::GetTypeDescriptor / FindFieldFast+613（解释执行），#6-8 次已回到 __epoll_pwait 空闲。
- 触发链（全部在 Activity 字段初始化期，即构造期、主线程）：
  VideoActivity.java:450（mobile）/ :409（leanback）/ TmdbDetailActivity.java:317 的 private final SubtitlePlaybackSession subtitlePlaybackSession = new SubtitlePlaybackSession(this);
  → SubtitlePlaybackSession.DefaultController 的 private final SubtitleAutoController delegate = new SubtitleAutoController(...) 与 private final SubtitleMatchService manualService = new SubtitleMatchService();
  → SubtitleMatchService() 里的 SubtitleProviderRegistry.get()
  → 静态 INSTANCE → installBuiltinSources() → SubtitleSourceManager.install() → PythonSubtitleScriptRuntime → chaquo/Loader.java（Python.start + getModule("webhtv_logging") + getModule("app")，全同步）。

### 12.2 S9-1 SubtitleMatchService 注册表改惰性（app/src/main/java/com/fongmi/android/tv/subtitle/SubtitleMatchService.java）

- 字段由 private final SubtitleProviderRegistry registry; 改为 private volatile SubtitleProviderRegistry registry;，无参构造器改为 this(new SubtitleContextBuilder(), new SubtitleQueryPlanner(), null, new SubtitleRanker());——构造期不再触发 SubtitleProviderRegistry.get()，Activity 字段初始化因此不再付 CPython 启动成本。
- 新增惰性访问器（双检锁，包内可见性不变）：registry() 先读 volatile 字段，为空时 synchronized 再取一次并调 SubtitleProviderRegistry.get()；包私有四参构造器（测试注入用）保持原样。
- 就绪检查（registry().enabledProviders().isEmpty()）从 autoMatch / manualSearch 的调用线程搬进它们各自已提交到 Task.searchExecutor() 的任务体首部，语义不变（仍是同一 skipped("provider_unavailable") 结果），但类初始化改在后台线程发生。
- 其余 4 处引用（search ×2、resolve ×2）全部改走 registry()。

### 12.3 S9-2 App 启动后台预热（app/src/main/java/com/fongmi/android/tv/App.java）

- onCreate() 在 resumeBackgroundServices(); 之后、post(() -> CacheScheduler.get().start(), 30_000L); 之前插入 warmSubtitleProviders();；新增 private void warmSubtitleProviders() { Task.execute(SubtitleProviderRegistry::get); }（Task.execute 走 5 线程共享池）。
- 目的：把注册表初始化提前到 App 启动的后台线程；即使某些路径（如 SubtitleManualSearchDialog 主线程调 manualSearch）仍可能首触，预热通常已把 INSTANCE 建好。
- 新增 import：com.fongmi.android.tv.subtitle.provider.SubtitleProviderRegistry、com.fongmi.android.tv.utils.Task（按现有字母序插在 PreviousProcessExitLogger 与 WebViewDataDirectoryGuard 之间）。

### 12.4 新增契约测试（源码文本断言，JVM 侧，不依赖 Android 运行时）

- app/src/test/java/com/fongmi/android/tv/subtitle/provider/SubtitleProviderRegistryLazyInitTest.java（4 例）：无参构造器不再 eager 解析注册表；所有 provider 使用点都走惰性访问器；可用性检查已移入后台任务；注入构造器仍接受显式 registry。
- app/src/test/java/com/fongmi/android/tv/AppStartupWarmupSourceTest.java（2 例）：warmSubtitleProviders() 使用 SubtitleProviderRegistry::get 与 Task.execute(（且不得出现直接调用 SubtitleProviderRegistry.get();）；public void onCreate() 内预热调用早于 post(() -> CacheScheduler.get().start()，且 ThemeController.applyNightModeToApp(); 与 resumeBackgroundServices(); 仍在。
- 两个测试都按 S4/S7 的既有范式写：readSource() 兼容 Gradle 工作目录为模块目录或仓库根，读取后把 CRLF 归一化为 LF 再按括号深度切片方法体。

### 12.5 验证

- 定向：bash ./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:testMobileArm64_v8aDebugUnitTest --tests '*SubtitleProviderRegistryLazyInitTest' --tests '*AppStartupWarmupSourceTest' → 双 flavor 编译通过，BUILD SUCCESSFUL（6 tests, 0 failed）。
- 全量 mobile：5356 tests completed, 4 failed, 2 skipped；全量 leanback：4569 tests completed, 6 failed, 2 skipped。失败集合与 §10.6 / §11.6 记录的既有 CRLF 冤案完全一致（mobile：ReaderPlaybackRoutingSourceTest ×2、TmdbSourceOnlyInteractionTest、WebThemeTokenSourceTest；leanback 再加 SearchResultDownFocusTest、NativeEnhancedPlaybackStyleFocusTest），无新增。
- 每条新增测试均由定向命令单独验证；未放宽任何既有断言。

## 13. S10（baseline profile）实施记录（2026-10-26 起）

### 13.1 为什么这条能解释「上游 APK 更流畅」

对齐反查（上游 OK影视 5.1.6 与上游 leanback armeabi_v7a）后确认：两个上游包都带 `assets/dexopt/baseline.prof` + `.profm`，本仓此前完全没有这个资产。

改动前的运行证据（emulator-5556，mobile debug）：

- `Dexopt state: x86_64: [status=run-from-apk]`（首次安装后从未 AOT，全解释执行）
- tap 窗口主线程 simpleperf：`libart.so 45.38%`（`artMterpAsmInstructionStart` 2.53%、`interpreter::DoCall` 2.49%、`Class::FindClassMethod` 5.63%、`ArtField::GetTypeDescriptor`/`FindFieldFast` 各 2.14%、`ClassLinker::ResolveMethod` 1.96%）
- 台上那 34s 的 `service onCreate` 与 ANR 也是同一机制在无 AOT 下的放大

### 13.2 设计决策（AGENTS.md §7 门禁）

对比了三条路径：

| 方案 | 依赖/构建成本 | 效果 | 决策 |
|---|---|---|---|
| `androidx.baselineprofile` Gradle 插件 + Macrobenchmark 模块（官方推荐生成链路） | 新增 test 模块 + `benchmark-macro-junit4` + uiautomator，需 root 设备或 GMD | 规则最贴近真实旅程 | **否决**：为一个 profile 引入新模块与依赖，超出本问题范围 |
| 手写 `baseline-prof.txt`（挑类/方法） | 零依赖 | 覆盖不全、易过时 | **否决**：无法证明覆盖进页路径 |
| **用 `profman --dump-classes-and-methods` 从设备已积累的真实 ART profile 生成**（官方「手动创建」路径） | 零依赖、零模块 | 规则来自真实执行 | **采用** |

关键机制确认（官方 baseline-profiles 文档）：

- AGP 7.4 起支持 app 模块的 `src/main/baseline-prof.txt`；AGP 8.0 起支持 `src/main/generated/baselineProfiles/` 多文件源集。本仓 AGP 9.2.1 属支持范围。
- AGP 把人工可读规则（`[HSP]*L<class>;-><method><sig>`）编成 `assets/dexopt/baseline.prof`（要求 < 1.5 MB），由 ART 在安装期使用。
- API 24–27 由 `androidx.profileinstaller` 在首跑安装；本仓该库已由 appcompat 等传递依赖带入（`androidx.profileinstaller:profileinstaller:1.4.0`，release 运行时类路径实测存在），**无需新增依赖**。
- 上游包内同时存在 `.prof` 与 `.profm`，与本仓产物一致。

### 13.3 生成方式与产物

采集（emulator-5556，root 取 profile）：设备上已积累 41 KB 真实 `primary.prof`，用官方命令导出：

```
adb shell profman --dump-classes-and-methods \
  --profile-file=/data/misc/profiles/cur/0/com.silent.android.webhtv/primary.prof \
  --apk=<base.apk>
```

产出 `app/src/main/baseline-prof.txt`：**31,712 条规则**，其中本应用 9,299 条、启动标记（`S`）6,890 条。热点类分布与前面各阶段定位的瓶颈吻合：`VideoActivity` 397、`PlayerManager` 251、`PlaybackActivity` 92、`History` 89、`PlaybackService` 49、`ExoUtil` 45。

构建验证（`:app:assembleMobileArm64_v8aRelease -PfastRelease=true`）：

- `app/build/intermediates/merged_art_profile/mobileArm64_v8aRelease/.../baseline-prof.txt` = 33,996 行（依赖库 + 本应用 9,299 等），确认 AGP 真的合并了本应用规则；
- release APK 内出现 `assets/dexopt/baseline.prof`（**29,571 B**）与 `assets/dexopt/baseline.profm`（2,203 B），均 < 1.5 MB 上限。

一个必须记录的 AGP 行为：**debug 变体不产出 dexopt 资产**。同一份 `baseline-prof.txt` 在 `mobileArm64_v8aDebug` 下 `merged_art_profile` 只有依赖库的 2,283 行、本应用 0 行，APK 内无 `assets/dexopt/`。即 baseline profile 只作用于 release（非 debuggable）包，用 debug 包测不出收益。

### 13.4 验收（同一镜像、同一 fastRelease release 包、profile 开/关 A/B）

两包仅差 `app/src/main/baseline-prof.txt` 是否存在；均为 `-PfastRelease=true`（未混淆）。每组 5 次真实冷启动（`am force-stop` + `am start -W`，逐次确认取到 `TotalTime`）：

| 组 | 冷启动 TotalTime（ms） | 中位数 |
|---|---|---|
| profile 开（含本应用 9,299 条规则） | 1752 / 1282 / 1097 / 1232 / 1173 | **1232** |
| 对照（仅依赖库规则 4,077 B） | 2470 / 1580 / 1125 / 1182 / 1196 | **1196** |

安装期状态均从 `run-from-apk` 变为 `x86_64: [status=speed-profile] [reason=install]`，即 ART 确实按 profile 做了 AOT。

**必须诚实标注的限制**：本机冷启动中位数两组几乎相同（1232 vs 1196），差异落在噪声内，**未能复现上游那种量级差异**。原因有二：

1. 这台 x86_64 模拟器跑 arm64 包要靠 houdini 翻译，且空载 janky 地板已有 44.6%（§10.2），模拟器固定成本盖过了 ART 解释执行那部分；
2. `am start -W` 的 `TotalTime` 只到首个窗口显示，而进页后「转圈停住」发生在之后的 measure/layout/draw 与播放器构建阶段，冷启动指标对这段不敏感。

因此本阶段的结论是**结构性归位**：把上游有、本仓缺的 profile 资产补齐并打通生成链路，**收益需在真机 release 包上用探针脚本（`scripts/perf/loading_jank_probe.sh`）复测**，不能用本机冷启动数字宣称已改善。

### 13.5 复现与回滚

- 重新生成：`bash scripts/perf/baseline_profile_build.sh --serial emulator-5556`（`--reset` 会先清设备 profile 并留 60s 手动走「首页→详情→播放」旅程）。
- 回滚：删除唯一新增文件 `app/src/main/baseline-prof.txt` 即回到改动前状态（`git revert` 本提交等价）；不涉及任何代码、依赖或构建脚本改动。
- 合并产物自查：`app/build/intermediates/merged_art_profile/<variant>/…/baseline-prof.txt` 的行数与本应用规则数，以及 release APK 内 `assets/dexopt/baseline.prof` 的大小。

### 13.6 与既有工作项的关系

- 与 S1/S2/S3/S7/S9 全部叠加，互不冲突：那些是把主线程工作移出/削减，本阶段是让 ART 直接编译热点，两者相乘。
- S8（拆 `PlaybackService.onCreate`）仍独立待办，与 profile 无关。

### 13.7 真机/独立设备复测（emulator-5557 / 127.0.0.1:5557，2026-10-26 13:47–14:50）

13.4 的平坦结论是在 emulator-5556 上用「同一安装、反复 `am force-stop`」测的——那种测法复用了 ART 已按 profile 完成的编译结果，profile 的实际差异被掩盖。本轮改为**每组都全新安装**（`uninstall` + `install` 触发 `speed-profile [reason=install]` 重编译），得到明确差异。

两包仅差 `app/src/main/baseline-prof.txt` 是否存在，均 `-PfastRelease=true`（未混淆），同一设备串行测试：

| 组 | n | 冷启动 TotalTime（ms，已排序） | 中位数 | 均值 |
|---|---|---|---|---|
| 对照（仅依赖库规则 4,076 B） | 10 | 1522 / 1545 / 1660 / 1668 / 2166 / 2297 / 2303 / 2378 / 3493 / 8706 | **2231** | 2774 |
| profile 开（本应用 9,299 条） | 10 | 1273 / 1291 / 1453 / 1582 / 1751 / 1764 / 1787 / 1892 / 2591 / 3427 | **1758** | 1881 |

- 中位数差：**−474 ms（−21.2%）**；均值差 −893 ms（−32.2%）。
- 剔除每组两个最大离群值（control 3493/8706、profile 2591/3427）后：对照中位 2231 ms、profile 中位 1758 ms，**结论不变（仍为 −474 ms / −21.2%）**。
- 两包安装后 `Dexopt state` 均为 `x86_64: [status=speed-profile] [reason=install]`，说明差异来自 profile 内容而非 dexopt 模式。

**方法学修正（重要）**：验收 baseline profile 必须每次 `uninstall` + `install`，否则反复 `force-stop` 会复用既有 AOT 结果，把收益测没。13.4 的平坦数据应按此更正理解。

同一轮顺带在真实设备上确认了 13.3 记录的 AGP 行为：设备上装的 debug 包（`flags=[ DEBUGGABLE ... ]`）`unzip -l` 结果**无 `assets/dexopt/` 任何条目**，与构建侧观察一致。

## 14. S9 回归修复：恢复备份时 `Python already started` 崩溃（2026-10-26）

### 14.1 现象与根因

设备 emulator-5557（`127.0.0.1:5557`）上执行「恢复备份」后进程直接崩溃：

```
FATAL EXCEPTION: pool-3-thread-1
java.lang.NoClassDefFoundError: com.fongmi.android.tv.api.loader.BaseLoader$Loader
  at BaseLoader.get(BaseLoader.java:42)
  at VodConfig.clear(VodConfig.java:149)
  at AppBackup.reload(AppBackup.java:269)
  at AppBackup.restore(AppBackup.java:240)
Caused by: ExceptionInInitializerError
  at BaseConfig.loadConfig(BaseConfig.java:114)
Caused by: java.lang.IllegalStateException: Python already started
  at com.chaquo.python.Python.start(Python.java:46)
  at com.fongmi.chaquo.Loader.<init>(Loader.java:23)
  at com.fongmi.android.tv.api.loader.PyLoader.<init>(PyLoader.java:24)
  at com.fongmi.android.tv.api.loader.BaseLoader.<init>(BaseLoader.java:35)
  at BaseLoader$Loader.<clinit>(BaseLoader.java:165)
```

这是 **S9 引入的回归**，与 baseline profile（S10）无关。`Loader` 的构造原本是 check-then-act：

```
if (!Python.isStarted()) Python.start(Platform.create());
```

S9 把字幕 `SubtitleProviderRegistry` 放到 `App` 启动的后台线程预热，于是字幕侧（`PythonSubtitleScriptRuntime` → `Loader`）与点播配置侧（`PyLoader`/`JsLoader` → `Loader`）会**并发**构造 `Loader`。两个线程同时通过 `isStarted()` 判断后，后到者调用 `Python.start()` 抛 `IllegalStateException`。

伤害被放大在两层：

1. `Python.start` 的异常从 `BaseLoader$Loader.<clinit>` 抛出，JVM 会把该类的静态初始化**永久标记为失败**；
2. 之后任何 `BaseLoader.get()` 都变成 `NoClassDefFoundError`，而 `AppBackup.reload()` 第一句就是 `VodConfig.get().clear(...)` → `BaseLoader.get()`，于是恢复备份必然崩溃。

### 14.2 修复（`chaquo/src/main/java/com/fongmi/chaquo/Loader.java`）

把启动 CPython 收敛成唯一入口，类级同步 + 双重检查 + 容忍败者：

```java
public Loader() {
    startPythonOnce();
    ...
}

private static synchronized void startPythonOnce() {
    if (Python.isStarted()) return;
    try {
        Python.start(Platform.create());
    } catch (IllegalStateException alreadyStarted) {
        // Another thread won between the check and the call; the runtime is up either way.
        if (!Python.isStarted()) throw alreadyStarted;
    }
}
```

锁加在 `Loader` 而不是 `Python` 上，因为后者属于 chaquo。catch 里必须再判一次 `isStarted()`：只有确认运行时真的起来时才吞掉异常，否则原样抛出，避免掩盖真正的启动失败。

### 14.3 验证

- **契约测试** `app/src/test/java/com/fongmi/android/tv/chaquo/PythonStartIdempotenceSourceTest.java`（2 例，通过）：锁住构造函数必须走 `startPythonOnce()`、不得直接调 `Python.start()`、不得恢复 `if (!Python.isStarted()) Python.start(` 形态，且 starter 必须 `static synchronized` + 捕获 `IllegalStateException` + 中途再验 `isStarted()`。
- **相邻既有契约**：`AppStartupWarmupSourceTest`（2 例）、`SubtitleProviderRegistryLazyInitTest` 同批通过，S9 的预热语义未被破坏。
- **字节码核对**：`javap -p -c` 确认编译产物含 `private static synchronized void startPythonOnce();`，构造函数为 `invokestatic startPythonOnce`，异常表覆盖 `Python.start` 并二次 `isStarted`。
- **设备复验**（emulator-5557，debug 包，已 `cmd package compile -m speed`）：连续 5 次冷启动，`FATAL EXCEPTION` 计数 0/0/0/0/0，进程 pid 每次存活；`Python already started` 与 `NoClassDefFoundError: ...BaseLoader` 在 logcat 中均为 0 次。

### 14.4 说明

本轮未在 UI 内实跑「恢复备份」动作（设备上无现存备份文件），崩溃路径由崩溃栈直接锁定，修复由契约测试 + 字节码 + 冷启动压测三重佐证。若需端到端确认，可在有备份文件的设备上执行一次恢复。
