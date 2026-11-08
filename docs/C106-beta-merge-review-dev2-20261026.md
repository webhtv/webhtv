# C106-beta-merge-review-dev2-20261026

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev2`，评审本轮全部未推送改动（12 个 C101/chaquo 提交 + 合并结果），
  修复必修项并验证，复评通过后提交、推送 `dev2`、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. `dev2` 相对 `origin/beta` 落后 0 个提交，合并无冲突、无遗漏。
  2. 评审覆盖 `dcf9828cba...HEAD` 的全部未推送改动与合并结果，每条结论有可复核证据。
  3. 每个必修项已修复并有验证证据；修复后复评通过。
  4. 定向单测与编译通过；`git diff --check` 为空。
  5. 本轮改动经任务守卫 `finish` 落盘并创建 recovery tag；推送 `dev2`；创建到 `beta` 的中文 PR（含具体改动内容）；随后拉取远端最新代码。
- **基线**：`dev2` = `056626b2e6`（12 个未推送提交），`origin/beta` = `236a8fc02f`，
  merge-base = `dcf9828cba616f07ea7b21ebc4053b691522a208`。
- **合并提交（两轮，因为评审期间 beta 又前进）**：`472a71ec7a`（`origin/beta = 236a8fc02f`，0 冲突，
  206 文件 / +5745 −131）→ 推送与 PR 后的 `git fetch` 发现 `origin/beta` 前进到 `6d0b3eb478`（PR #491）
  → 第二轮合并 `ebcbeb9330`（0 冲突，39 文件 / +1830 −84，见 §6）。
- **当前状态（历史记录：第二轮合并已本地提交、评审与双 flavor 验证全绿）**：两轮合并均 0 冲突且机械核验通过；
  30 + 39 个受影响文件评审完成，无必修项，评审—修复—复评一次收敛（未产生额外代码改动）；
  `dev2` 已推送、PR [#492](https://github.com/Silent1566/webhtv/pull/492) 已创建；
  第二轮合并在 §6 记录后仍需一次提交与推送以更新 PR。
- **下一动作**：把 §6 的第二轮记录经任务守卫提交并推送 `dev2`，更新 PR #492 描述，最后 `git fetch`。

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `236a8fc02f5a2ce4ea54001c63fa0cfc0d66ffdd`（"Merge pull request #489 from Silent1566/dev4"） |
| 本地 | `dev2` = `056626b2e6`（领先 `origin/dev2` 12 个提交，全部为 C101 与 chaquo 修复） |
| merge-base | `dcf9828cba616f07ea7b21ebc4053b691522a208` |
| 落后 / 领先 | `HEAD..origin/beta` = 39；`origin/beta..HEAD` = 12 |
| 冲突 | **0 处**（`git merge-tree --write-tree` 预演与真实合并结果一致） |
| 合并提交 | `472a71ec7a` |
| 合并净差异 | 206 文件 / +5745 −131（`app` 188、`docs` 15、`chaquo` 3） |

合并带入的 beta 内容（PR #483–#489）：同名线路切换按稳定键判定（`TmdbUIAdapter.flagKey/flagIndex` +
`mPlaybackFlagKey/inlinePlaybackFlagKey/isPlayingContent`）、OSD 媒体信息徽标常驻与一屏一行分类色板、
版本卡片按当前调色板上色、退出确认次要按钮与固定浅色面前景可读性、PY-BRIDGE 依赖下载契约加固、
移动端广告拦截日志带修复、E-SP12 掉帧按播放时长归一。

## 2. 评审范围

- **未推送改动**：`origin/beta..HEAD`（13 个提交 = 12 个 C101/chaquo 提交 + 合并提交 `472a71ec7a`），
  真实代码改动 30 个文件 + 1 个生成物 `app/src/main/baseline-prof.txt`（+31712 行）。
- **合并结果**：`origin/beta` 带入的 206 个路径，重点是与 `dev2` 改动重叠的 3 个文件：
  `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`、
  `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`、
  `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`。

## 3. 评审发现与处置

### 3.1 通则

30 个代码文件全部逐文件 diff 通读，并对其中的并发、幂等、缓存失效与生命周期四处高风险改动做了
调用点反查（`setResizeMode` / `applyResizeMode` / `tmdb_episode_titles` 写入点 / `Task.execute`）。
另由两名独立评审员分别复核「C101 性能/生命周期改动」与「beta 合并结果及重叠文件交互」，
以及一名验证员复核「合并后编译与单测」，三份结论与本轮自查一致：
**未发现必修项**，因此本轮除合并提交 `472a71ec7a` 外没有产生新的代码改动，评审—修复—复评循环一次收敛。

### 3.2 合并结果的机械核验

- 父提交：`472a71ec7a^1 = 056626b2e6`（`dev2` 顶端）、`^2 = 236a8fc02f`（`origin/beta` 顶端）；
  `git diff --stat 472a71ec7a^1 472a71ec7a` = 206 files / +5745 −131。
- **三方重放逐字节等价**：以 `base = dcf9828cba`、`ours = 056626b2e6`、`theirs = 236a8fc02f` 跑
  `git merge-file`，三个重叠文件的输出与 `git show 472a71ec7a:<file>` **完全一致**（`exit=0`），
  说明合并是机械式三方合并，没有手工改写造成的语义漂移。
- **逐行存活校验**：对 beta 侧与 dev2 侧相对 base 的每一行新增行，在 234 个受影响文件中检查其是否出现在合并树里，
  两侧 `lossy files = 0`（无新增行丢失）。
- **文件集闭环**：`git diff --name-only 472a71ec7a origin/beta` 恰好等于 dev2 独有的 31 个文件，
  即 beta 侧无丢失、dev2 侧无覆盖。
- **hunk 区间两两不相交**（base 坐标，`git diff --unified=0`）：三个重叠文件上 beta 与 dev2 的改动区间不重叠，
  这是 0 冲突的必然原因。
- **无冲突遗留**：双向 `git diff --check` 为 0，`git grep -E '^(<{7}|={7}|>{7})( |$)' 472a71ec7a` 无命中，
  无 `*.orig`/`*.rej`。
- **稳定键语义未被削弱**：C101 未触碰 `TmdbUIAdapter`/`Flag`/`TmdbEpisodeSorter`，flag 列表顺序仍为
  `vod.getFlags()` 原序；β 带入的 `mPlaybackFlagKey`/`inlinePlaybackFlagKey`/`isPlayingContent` 在合并后全部在位。

### 3.3 逐项结论（按验证手段分组）

**幂等/去重（C101 卡顿主线）**
- `app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java`：`applyResizeModeNow(int)`
  在 `logSurfaceState("applyResizeMode before ...")`、`setVideoAspect`、`setResizeMode`、`requestLayout`
  之前插入 `isResizeModeApplied(...)` 守卫；键含 `resizeMode`、`effectiveResizeMode`、`targetAspectRatio`、
  `contentAspectRatio`、`stretch`，比例用 `Float.floatToIntBits` 比较，覆盖全部影响结果的输入。
  `invalidateAppliedResizeMode()` 仅在 `setPlayer` 重绑与重建渲染两处调用，都是引擎/PlayerView 状态被重置之后。
  反查 `setResizeMode` 生产调用点只有 `PlaybackActivity.java:501` 一处；`applyResizeMode` 的 30+ 调用点
  （Cast/Live/Video/TmdbDetail 双 flavor）最终都经 `applyResizeModeNow`，无绕过守卫的写入路径；
  `applyResizeMode(int)` 仍保留 `view.post` 第二段并按 `requestedAspectMode == mode` 校验，未自递归。
- `app/src/main/java/com/fongmi/android/tv/App.java`：`warmSubtitleProviders()` 走
  `Task.execute(Runnable)`（`Task.java:100` 直接 `executor.execute(task)`），后台线程预热，不阻塞 `onCreate`。
- `app/src/main/java/com/fongmi/android/tv/subtitle/SubtitleMatchService.java`：`registry()` 双重检查锁
  惰性初始化；`provider_unavailable` 判定搬进后台线程后 `jobs.remove(playbackKey)` 与正常路径一致，无泄漏。
- `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`：`applyLoaded`/`applyTmdbResultNow`
  改为 `ArrayDeque` 分帧渲染，每块有 `generation != loadGeneration` 守卫，队列末端才 `loading.setVisibility(GONE)`；
  `loadedVod == null` 与 `history == null` 两条早退分支都显式 GONE，不存在「加载圈永不隐藏」；
  `clearRenderChunks()` 覆盖 reset、两条加载入口与 `onDestroy`；`runNextRenderChunk` 的 `catch (Throwable ignored)`
  只降级单块异常，仍继续调度。
- `app/src/main/java/com/fongmi/android/tv/ui/custom/ProgressLayout.java`：`showView`/`hideView` 增加可见性
  幂等早退与 `animate().cancel()`，无 animator 残留。

**并发（chaquo）**
- `chaquo/src/main/java/com/fongmi/chaquo/Loader.java`：`private static synchronized void startPythonOnce()`
  用本类锁串行化 `Python.start`，已启动直接返回；`IllegalStateException` 只在 `Python.isStarted()` 复查后吞掉，
  真失败仍然 rethrow。修复了 check-then-act 竞态导致的 `NoClassDefFoundError` 连锁崩溃。
- `catvod/src/main/java/com/github/catvod/crawler/diagnostics/DiagnosticLogBuffer.java`：`ThreadLocal<String>`
  缓存线程标签（`sanitizeTag` 后截断 64），仓库内无线程重命名点；`safeTag` 保留 `av-diag → legacy-av-diag`
  防伪名不变。

**等价改写（无语义变化）**
- `app/src/main/java/com/fongmi/android/tv/bean/Device.java`：`ensureIdentity()` + `volatile identityReady`
  双重检查锁缓存 uuid/name/serial/eth/wlan/type；`ip` 与 `time` 仍每次实时。新增 `identityUuid()`（null 归一为空串）。
- `app/src/main/java/com/fongmi/android/tv/playback/PlaybackRecord.java`：`clientKey()` 改用 `Device.identityUuid()`，
  语义等价（同一 uuid），省掉每秒 tick 的网卡枚举。
- `app/src/main/java/com/fongmi/android/tv/utils/ImageHeaderPolicy.java`：`host.matches(...)` → 预编译
  `Pattern` + `matcher(host).matches()`，匹配语义相同。
- `app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java`：删除 `AudioCapabilities.getCapabilities(...)`
  硬件探测（设备实测该调用独占 354–757ms），日志改记 configured 值；`AudioAttributes` 仍在
  `ExoUtil.java:330` `player.setAudioAttributes(AudioAttributes.DEFAULT, true)` 使用，import 保留，编译无影响。
- `app/src/main/java/com/fongmi/android/tv/player/cache/PlaybackDiskBufferStore.java`：`log(...)` 由 `static`
  改实例方法，按 `action|mediaKey` 在 `queryLogs` 内做 2 秒节流；所有调用点都在 `synchronized` 方法内。
- `catvod/.../diagnostics/DiagnosticText.java`：`clean()` 增加按首字符预判跳过正则，预判条件与各正则必需字符
  一致（`:`/`=` → HEADER/SECRET，`b`/`B` → BEARER，`/` → URI_TEXT/LOCAL_PATH，`.`/`:` → ADDRESS）；
  `escape()` 无需转义时直接返回原串，`MAX_CHARS` 截断语义保持。
- `catvod/.../SpiderDebug.java` / `DebugLogStore.java`：改为「先 `clean` 一次再 `addCleaned`」，去掉重复清洗。

**缓存失效唯一性**
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`：`episodeTitleValues()` 缓存只在
  `onNewIntent` 的 `putExtras` 之后失效；grep 确认 `tmdb_episode_titles` 的写入点仅有 leanback `VideoActivity.java:970`
  与 mobile `VideoActivity.java:939` 的 intent 构造，运行时改写只有 mobile 的 putExtras 路径。
  leanback 侧未加同款缓存（读取仅在 3084/3098 两处、低频），属刻意不对称而非缺陷。
- `app/src/leanback/.../VideoActivity.java` 与 `app/src/mobile/.../VideoActivity.java` 各新增
  `@Override protected boolean deferPlaybackServiceBinding() { return true; }`，两个 flavor 行为一致；
  `PlaybackActivity` 侧新增 `bindPlaybackServiceIfIdle()` + 500ms 兜底延迟，与 `bound` 去重共同保证不重复绑定。

**测试文件**
- 10 个新增契约测试（`AppStartupWarmupSourceTest`、`DeviceIdentityCacheTest`、`PythonStartIdempotenceSourceTest`、
  `DiagnosticTextRedactionTest`、`ExoUtilAudioCapabilityLogTest`、`SubtitleProviderRegistryLazyInitTest`、
  `PlaybackActivityResizeModeIdempotenceTest`、`ProgressLayoutContractTest`、`ImageHeaderPolicyTest`、
  `VideoActivityEpisodeTitlesCacheTest`）均为源码断言式，与该仓库既有测试风格一致，且断言与实现一一对应。

### 3.4 非必修观察（记录处置理由，本轮不改）

- **[low] `TmdbDetailActivity.java:2791` 队尾无条件隐藏 `binding.loading`**：`runNextRenderChunk()` 在
  `renderChunks` 排空后直接 `binding.loading.setVisibility(View.GONE)`，而同一个 loading 控件也被
  `showManualTmdbMatchDialog`（`TmdbDetailActivity.java:3720`/`3733`）、换源（`4007`/`4019`/`4036`）、
  集详情弹窗（`6870`/`6890`）复用；理论上 1–4 帧窗口内可能提前收掉别的流程的圈。
  **处置：不改。** 改动前 `applyLoaded` 首行就 GONE（见 `2849`/`2866` 两条早退分支的同款写法），
  新实现在队尾才隐藏，只会更晚而不是更早，因此不构成对既有行为的回归；
  且被提前隐藏的只是视觉状态，那些流程各自的收尾 GONE 仍会执行，没有状态残留。
  若日后确需彻底消除该竞态，加 `loadingOwner` 令牌或 `hideLoadingIfOwner(owner)` 即可。
- **[low] `TmdbDetailActivity.java:2779-2782` `!canTouchUi()` 分支清空队列后直接 return，不隐藏 loading**：
  该分支只在 `isFinishing() || isDestroyed()` 时进入，页面已在销毁，无可见后果。
  **处置：不改。**

### 3.5 已知但不属于本任务的既有缺陷

- `:catvod:testDebugUnitTest` 无法编译：`catvod/src/test/java/com/github/catvod/crawler/DebugLogStoreTest.java:22`
  与 `:34` 仍引用已删除的 `DebugLogStore.trimLines` / `readTail`。这是 C101 提交遗留，不属于本轮合并范围，
  本轮仅记录不改动（`app` 模块单测不受影响）。

## 4. 验证

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 合并预演 | `git merge-tree --write-tree HEAD origin/beta` | 退出 0，无冲突 |
| 空白/冲突标记 | `git diff --check`（双向） | 空输出 |
| 主源码编译（双 flavor，含 chaquopy） | `./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL in 6m 49s（58 tasks；`:chaquo:mergeArm64_v8aDebugPythonSources`、`:chaquo:generateArm64_v8aDebugPythonProxies` 实际执行） |
| 单测源码编译 | `./gradlew :app:compile{Mobile,Leanback}Arm64_v8aDebugUnitTestJavaWithJavac` | BUILD SUCCESSFUL in 3m 35s（102 tasks） |
| 本轮 10 个定向测试类（双 flavor） | `./gradlew :app:test{Mobile,Leanback}Arm64_v8aDebugUnitTest --tests <10 个类>` | BUILD SUCCESSFUL in 53s，全绿 |

定向测试逐类结果（tests/failures/errors）：`AppStartupWarmupSourceTest` 2/0/0、`DeviceIdentityCacheTest` 4/0/0、
`PythonStartIdempotenceSourceTest` 2/0/0、`DiagnosticTextRedactionTest` 9/0/0、`ExoUtilAudioCapabilityLogTest` 3/0/0、
`SubtitleProviderRegistryLazyInitTest` 4/0/0、`PlaybackActivityResizeModeIdempotenceTest` 4/0/0、
`ProgressLayoutContractTest` 4/0/0、`ImageHeaderPolicyTest` 6/0/0，mobile 与 leanback 两个 flavor 均通过；
`VideoActivityEpisodeTitlesCacheTest` 4/0/0（属 `testMobile` 源集，仅 mobile flavor 存在）。
这 10 个类正是 `origin/beta..dev2` 区间内改动过的全部测试文件。

编译统一使用 `-PchaquopyBuildPython=G:/Python310/python.exe`（chaquopy 需要本机 CPython 解释器）。

**全量单测的既有环境性失败（非本轮回归）**：mobile 5415 tests / 6 failed，leanback 4628 tests / 8 failed，
14 条失败收敛为 7 个方法，全部实测为本机 Windows 环境问题：
1. 6 个方法因本机 `core.autocrlf=true`（工作区 CRLF）与测试内的 LF 多行断言不符
   （`ReaderPlaybackRoutingSourceTest` ×2、`WebThemeTokenSourceTest`、`TmdbSourceOnlyInteractionTest`、
   `NativeEnhancedPlaybackStyleFocusTest`、`SearchResultDownFocusTest`）；LF 检出（CI/Linux）下成立。
2. 2 个方法因 `java.io.File.renameTo` 在 Windows 不覆盖已存在目标
   （`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`）；Android/Linux 目标不受影响。
   上述 7 个方法涉及的源码与测试在 `056626b2e6` 与 `472a71ec7a` 的 blob 哈希完全相同。

## 5. 交付坐标

| 项 | 值 |
| --- | --- |
| 分支 | `dev2` |
| 第一轮合并提交 | `472a71ec7a` — `merge: 合并远端 beta 236a8fc02f（PR #483-#489）到 dev2` |
| 第二轮合并提交 | `ebcbeb9330` — `merge: 合并远端 beta 6d0b3eb478（PR #491）到 dev2`（见 §6） |
| 本任务提交 / recovery tag | `0fc6878533`；`recovery/C106-beta-merge-review-dev2-20261026/20261026171403-0fc687853310`，第二轮记录见 §6.4 |
| 评审文档 | 本文件 |
| PR | `dev2` → `beta`：[#492](https://github.com/Silent1566/webhtv/pull/492)（只创建、不合并） |
| 回滚 | `git reset --hard 056626b2e6`（回到两轮合并前的 `dev2`）；远端回滚为 revert 对应合并提交 |

## 6. 第二轮合并（origin/beta 前进到 `6d0b3eb478`）

### 6.1 为什么还要合一次

第一轮合并与推送完成后执行 `git fetch origin --prune`，发现 `origin/beta` 在评审期间又前进了：
`236a8fc02f..6d0b3eb478`（`Merge pull request #491 from Silent1566/dev3`，11 个提交）。
为满足「合并远端 beta 最新代码」的要求，必须在更新后的 beta 之上再做一轮合并、评审与验证。

### 6.2 合并对象与结果

| 项 | 值 |
| --- | --- |
| 合并前 `dev2` 顶端 | `0fc6878533`（§1 第一轮合并 + 评审文档） |
| 合并目标 | `origin/beta = 6d0b3eb478`（`Merge pull request #491 from Silent1566/dev3`） |
| 冲突预演 | `git merge-tree --write-tree HEAD origin/beta` 退出 0，无冲突 |
| 实际结果 | `Automatic merge went well`，39 files / +1830 −84 |
| 合并提交 | `ebcbeb9330` — `HEAD^1 = 0fc6878533`、`HEAD^2 = 6d0b3eb478` |

beta 本轮带入的能力（`236a8fc02f..6d0b3eb478`）：

- `9996d787ec` TMDB 未显式配置语言时请求语言跟随界面语言，并新增 `languageConfigured` 标记；
- `024ac3f661` `App.getResources` 语言资源入口加锁并容错（受限重试 + 放弃后固化）；
- `c9fb2731b1`/`d1616410bb`/`6c2de578dc` TMDB 匹配、缓存键与标题清洗的简繁归一；
- `d638f6b9a9`/`adcf52019b`/`2b405b4b7b`/`07b9c20b60` 直播源身份、上次频道、分组名与站点能力标签与界面语言解耦；
- `abaef4f1b7` 繁体界面 TMDB 匹配的设备实测证据文档。

**与 dev2 改动的重叠只有 3 个文件**（`App.java`、`TmdbDetailActivity.java`、leanback `VideoActivity.java`），
且都是行级不相交（dev2 改 `onCreate` 尾部与分帧渲染，beta 改语言判定行），这是 0 冲突的必然原因。

### 6.3 第二轮评审结论（无必修项）

- **合并完整性**：冲突标记正则无命中、双向 `git diff --check` 为空；dev2 侧关键符号全部在位
  ——`TmdbDetailActivity.java:481` `renderChunks`、`:2766` `addLast`、`:2781` `clear`、`:2791` 重新调度、
  `:2796` `clear`；`App.java:57` `MAX_RESOURCES_APPLY_ATTEMPTS`、`:59` `resourcesLock`、`:142` 调用与
  `:154` 定义 `warmSubtitleProviders()`；`deferPlaybackServiceBinding()` 在 `PlaybackActivity.java:212/1155/1157`、
  leanback `VideoActivity.java:6075`、mobile `VideoActivity.java:6649`；
  分帧渲染的 `clearRenderChunks` 在 `:796/:2848/:2968/:11456`，`enqueueRenderChunk` 在 `:2872/:2877/:2882/:2889`
  与 `:2976/:2985/:2991`，每块仍带 `if (generation != loadGeneration) return;`。
- **请求语言与缓存同源**：`TmdbLanguagePolicy.java:58` 新增双参 `requestLanguage(TmdbConfig, int uiLanguage)`，
  未显式配置时按界面语言给出 `zh-CN`/`zh-TW`；`TmdbService.java:662` 与 `:1008` 的缓存分键共用同一判定；
  生产代码已无遗漏的单参调用（`History.java:1150`、`SubtitleTmdbResolver.java:27`、`MediaTitleResolver.java:137`、
  `TmdbSourceAdapter`/`TmdbUIAdapter`/`TmdbDetailActivity` 全部调用点、leanback `VideoActivity.java:2450`），
  仅 `TmdbLanguagePolicyTest.java:20` 与 `TmdbLanguageFollowUiTest.java:45` 保留单参调用，后者正是断言二者等价。
- **显式配置不被覆盖**：`TmdbConfig.java:293-296` `isLanguageConfigured()` 兼容旧数据（语言非默认值即视为已配置），
  `:299-301` `setLanguage` 置位标记，用户显式选择不会被界面语言反转。
- **`App.getResources` 加锁容错**：快路径为一次 `volatile` 读；慢路径 `synchronized (resourcesLock)` 内双检，
  失败时写 `resourcesLanguage = Integer.MIN_VALUE` **不认领语言**（下次重试），达到
  `MAX_RESOURCES_APPLY_ATTEMPTS = 3` 才认领并放弃——既不会把过渡失败固化成永久降级，也不会每帧重算
  `Configuration` 刷日志；锁内只做 `Setting.wrapLanguage` 与 `updateConfiguration`，无回调、无 UI、无重入。
  契约测试 `AppResourcesWebViewCompatibilityTest` 两个方法逐条断言了旧行为保留与上述失败安全语义。
- **缓存语言守卫**：`TmdbDetailCache` 入库 `normalize(language)`、命中用 `matchesTarget`，不匹配时拒绝复用，
  简繁不会再互相冒充。
- **既有 `[low]` 观察不变**：§3.4 的两条 `[low]` 在本轮合并后仍成立且仍不改（理由同前）。

### 6.4 第二轮验证

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 冲突预演 | `git merge-tree --write-tree HEAD origin/beta` | 退出 0，无冲突 |
| 空白/冲突标记 | `git diff --check`（双向）+ 冲突标记正则 | 空 / 无命中 |
| 主源码编译（双 flavor） | `./gradlew :app:compile{Mobile,Leanback}Arm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL in 1m 13s（58 tasks，4 executed） |
| mobile 定向单测 | `./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests <21 个类>` | BUILD SUCCESSFUL in 20s，21 类全绿 |
| leanback 定向单测 | `./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests <20 个类>` | BUILD SUCCESSFUL in 17s，20 类全绿 |

定向类覆盖本轮全部受影响测试：新增的 `GroupDisplayNameTest` 8、`LiveDisplayNameTest` 5、`SiteTransBracketTest` 9、
`TmdbMatchCacheHantTest` 5、`TmdbSeasonMatchCacheHantTest` 5、`MediaTitleParserHantTest` 6、
`TmdbDetailActivityHantMatchTest` 5、`TmdbLanguageFollowUiTest` 8、`TmdbLanguagePolicyTest` 4、
`AppResourcesWebViewCompatibilityTest` 2；以及 dev2 侧 10 个契约测试与
`TmdbUIAdapterTest` 61、`TmdbDetailCacheTest` 14、`TmdbDetailDirectPlayTransitionSourceTest` 4
（`TmdbMatcherHantTest` 属 `testMobile` 源集，leanback 不需要）。全部 tests/failures/errors 计数为 0 或非零失败数 0。
