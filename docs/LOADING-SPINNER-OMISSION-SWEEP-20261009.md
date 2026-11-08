# LOADING-SPINNER-OMISSION-SWEEP-20261009：加载圈「遗漏分支」扫荡收口

本文档收口「加载圈偶尔一直转圈、但不影响播放」这一排查线里**代码上确实漏掉的分支**的处理结果，
并明确记录**经核实属有意契约、故不修改**的项，避免后续会话重复推演。

## 已修复（3 项，各一个 task guard 会话）

| # | 遗漏点 | 机制 | 提交 | recovery tag |
| --- | --- | --- | --- | --- |
| 1 | leanback `setPlayer()` 的 `!canApplyPlayerResult()` 早退分支不释放守卫 | 守卫残留使 `onStateChanged(READY)` 的 `break` 与 `hidePlaybackProgressIfStale()` 同时为假 ⇒ 圈无清除路径 | `4319403502` | `recovery/PLAYBACK-LOADING-GUARD-20261009/20261009083845-4319403502a6` |
| 2 | 详情页内播：`inlinePlaybackPending` 在「只自增代际、不接替请求」的作废点未释放 | 圈永久留存；且 `isSamePendingInlinePlayback()` 恒真使**同一集无法再次起播** | `7809ff4952` | `recovery/INLINE-LOADING-RELEASE-20261009/20261009090735-7809ff495278` |
| 3 | ① leanback `setPlayer(null)` 早退不释放守卫；② `inlinePlaybackPending` 缺归属判定（选择面变更路径） | `SiteViewModel.cancelPlayerContent()` 会向 PLAYER LiveData 写 null ⇒ `setPlayer(null)` 可达；详情重载/外部播放返回会让在途回调因请求失效直接返回 | `d7b9439201` | `recovery/ABANDONED-REQUEST-LOADING-RELEASE-20261009/20261009100617-d7b9439201ce` |
| 4 | 屏显诊断重缓冲计数按 `isMpv()` 而非 `isExo()` 分派 ⇒ **IJK 恒为 0** | 非 Exo 时 `snapshot` 为 `Snapshot.empty()`；`PlayerManager.getRebufferCount()` 本已引擎无关 | `f2ccce36c9` | `recovery/OSD-REBUFFER-ENGINE-BRANCH-20261009/20261009101109-f2ccce36c9ab` |

（表中 1/2 与 3 的前半属同一族：**请求被放弃时，加载态必须有人释放**。）

## 玩家加载圈的族闭合证明

两个 flavor 的玩家加载圈结构完全相同，写点各自唯一：

| 项 | leanback | mobile |
| --- | --- | --- |
| 置 `VISIBLE` | 仅 `showProgress()`（`VideoActivity.java:5085`） | 仅 `showProgress()`（`:5445`） |
| 置 `GONE` | 仅 `hideProgress()`（`:5096`） | 仅 `hideProgress()`（`:5457`） |
| `hideProgress()` 调用者 | `showProgress()` 音频舞台分支、`showPlaybackContent()`、`showError()`、`onSeeking()`、`setAudioStageVisible()`、`showNativeDetailFallback()` | `showProgress()` 音频舞台分支、`showPlaybackContent()`、`showError()`、`onSeeking()`、`setAudioStageVisible()` |
| 自动收圈 | `setTraffic()`（每秒一跳）→ `hidePlaybackProgressIfStale()` | 同 |

因此圈能否被清除，只取决于三组门控；三组现已全部审计：

| 门控 | 结论 |
| --- | --- |
| `mPlaybackRequestActive` / `mPlaybackPlayerStarted` | 置位点仅 `beginPlayerContentRequest()`；**全部放弃路径已释放**（错误、重复结果、重定向、详情未就绪、null 作废、`onNewIntent`） |
| `mSeekProgressPending`（`canHideSeekProgress()`） | 见下「有意契约」，不修改 |
| `isOwner()` / `player().isEmpty()` | 见下「有意契约」，不修改 |

## 经核实属有意契约、故不修改（含证据，避免重复推演）

1. **停滞看门狗只对 Exo 生效**：`if (isExo()) armBufferingStallWatchdog();`（两处）与 `checkBufferingStall()` 的 `if (!isExo() …)` 来自**专门提交** `60ad0d0969 fix(player): restrict buffering stall watchdog to Exo`（`Task-Guard: EXO-BUFFERING-WATCHDOG-SCOPE`），并由 `PlayerManagerLifecycleSourceTest.exoBufferingStateArmsOnlyTheExoStallWatchdog` 明确钉住（断言「restricted to Exo playback」「polling must stop for non-Exo」）。
   另外：该看门狗仅在 **position 与 buffered 两轴均无进展** 20 s（非 loading）/60 s（loading）才触发，**对「进度在推进」的已报告现象不会触发**。故启用它不改善该现象，只会改变真卡死时的行为，并引入误报与内核回退链风险 ⇒ 需用户明确决定另立任务（含设计门与真机验证）。
   （顺带记录一处文档不一致：`MpvSeekBufferingSourceTest.openWindowCannotLatchForever` 的注释称「publishing READY would disarm the stall watchdog, which checkBufferingStall() cancels on READY」，即按「看门狗对 MPV 也生效」书写；而实际对非 Exo 无条件取消。属注释与实现不一致，未修改。）
2. **暂停时 seek 窗口关不掉**：`canHideSeekProgress()` 的 `(!player().isLoading() || player().isPlaying())` 在「暂停且引擎持续报 loading」时恒假，四条清圈路径共用该闸门。`C47SeekLoadingProgressSourceTest` 第 72–75 行明确钉住该子句（「已 READY 但仍在真实加载（且没在播）时不能收圈，那正是『卡画面』的那一帧」）⇒ 有意契约，根治依赖引擎状态复位而非改 UI 判据。
3. **`!isOwner()` 与 `player().isEmpty()`**：由 `PlaybackOwnershipSourceTest.theSpinnerFallbackStaysOwnerScoped` 逐条断言（含「不能抢详情页自己的加载态」）⇒ 不可删。
   （该测试同文件 KDoc 称「这里不依赖归属」，与测试/实现矛盾，属陈旧文档，未修改。）
4. **IJK 的 `BUFFERING_START` 是预判式**：上游 `ff_ffplay.c` 中 START 由 `packet_queue_get_or_buffering` 在 buffer-indicator 队列饥饿时发出，`ffp_check_buffering_l` 只发 END。故「引擎报 BUFFERING 而画面继续」在 IJK 语义下是可能的正常状态，不是状态损坏的证据。

## 已报告未修（本系列范围外）

1. **本机 CRLF 检出使若干源级用例恒红**：`ReaderPlaybackRoutingSourceTest` 2 项、`TmdbSourceOnlyInteractionTest` 1 项。取证：断言要求裸 `\n` 字面量，而 `reader.html` 为 `i/lf w/crlf`（CRLF 2627 处、裸 LF 0 处）、`leanback/VideoActivity.java` 同为 `i/lf w/crlf`（CRLF 12081 处、裸 LF 0 处），且失败断言读取的文件均未被本系列改动触及 ⇒ 既有环境问题，需单独任务处理断言写法或 EOL。
2. **`E-SP3` 文档与代码的记载差异**：`docs/E-SP3-exo-buffering-stall-watchdog.md` 未记录上述「限制为 Exo」的后续决定（该提交本身无文档，仅改 2 文件 6 行）。

## 残留风险（必须明示）

本系列修复的是**加载态被持有而无人释放**（守卫/标记泄漏）与**诊断读数错误**。已修复项中，1/2/3①/3② 都能直接造成「圈在屏上而播放继续」。

但**仍有一条未经修改的路径**：若引擎（IJK/MPV）持续报 BUFFERING/`isLoading()` 而画面实际在推进，则圈的收口仍被 `getPlaybackState() == STATE_READY` 挡住（`hidePlaybackProgressIfStale()` 的末行）。截图取证显示 `0 KB/s`（无流量）+ 画面在走，与此形态相符。闭合它需要在 UI 侧引入「位置确有推进 ⇒ 视为已恢复」的**独立判据**（或为 IJK/MPV 补等价状态重判），这属**产品/UX 决定**（会改变合法缓冲期间的显示），需用户明确授权并按 AGENTS.md 完成设计门与真机验证后再实施。

## 5557 实机验证（2026-10-09 19:0x）

**设备**：`192.168.50.3:5557`（本工作区分配口），leanback flavor，Android 9 / SDK 28，x86_64 模拟器 + arm64 转译（已装 APK 为 arm64-v8a）。

**构建与安装**：`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5557` → **Debug 变体**（符合“不打正式包”），`BUILD SUCCESSFUL in 4m 54s`，APK 191 MB；脚本自带 `install -r` 覆盖安装。

**签名与产物同一性**：设备原 APK 证书 SHA-256 与本机 `~/.android/debug.keystore` 逐字节相同（`95e4b2e7…ff7e4d`），且 `local.properties` 无 release 签名项 ⇒ 可直接覆盖安装、**未卸载**；安装后设备 `base.apk` 的 SHA-256 与本地产物一致（`d5953c0c…`），确保验证的就是本系列构建。

| 检查 | 结果 |
| --- | --- |
| A/B 对照（关键） | 本轮开始时已备份改动前设备 APK（`73c6567a…`）；装回旧版跑同一场景，**复现完全相同的失败**（`连接超时` + 位置冻结 + `duration=C.TIME_UNSET`）⇒ 该失败与本系列改动**无因果** |
| `ijk-smoke.mp4`（139 KB, 1080p） | 新旧构建都冻结（旧 85 ms / 新 203 ms）并报 `连接超时`；日志伴 `ACodec setPortMode … failed w/ err -1010` 与 `EGL_BAD_MATCH` ⇒ 模拟器硬解该样本失败，属环境/样本问题 |
| `sintel-trailer.mp4`（52 s，技能推荐样本） | **正常播放到片尾**：`duration=52209` 已知、`position` 推进至 52208，无崩溃 |
| 加载圈滞留（覆盖修复 1/2/3 的观感面） | 播放中与注入 3 次右方向键 seek 后，`id/progress` 节点数均为 **0**（无滞留圈） |
| 修复 3 的 `setPlayer(null)` 分支 | 连续 3 轮重复投递同一 VIEW intent（`onNewIntent → cancelPlayerContent() → setPlayer(null)`）：每轮仍能起播、圈不滞留、无崩溃 |
| 崩溃 | `logcat -b crash` 为空 |

### 本轮未能在设备上定证的部分（诚实记录）

1. **修复 4（IJK 重缓冲读数）**：本地文件不产生重缓冲，修复前后都读 0，**无法用设备读数区分**；其决定性证据仍是源级用例 + 代码审查。
2. **修复 2/3 的「同一集无法再次起播」语义**：需详情页内播路径（沉浸融合/详情直放，依赖站点/详情数据），本轮未构造该路径，故该语义**未在设备上直接验证**；已验的是同一族作废路径不导致圈滞留。
3. 我尝试直接写 prefs 切到 IJK（`player=1`）并开屏显诊断，但**应用启动时把内核重置回 EXO**（`player` 键消失），故未取得 IJK 诊断面板读数。prefs 事后校验完好（XML 可解析、141 项、无重复键），无损坏残留。

### 资源回收与设备终态

- `gradlew --no-daemon clean` → `BUILD SUCCESSFUL in 1m 51s`；`app/build` 等 5 个构建目录残留 **0**
- `gradlew --stop` 停掉 1 个 Gradle 守护进程；残留 `java.exe` **0**
- 设备上临时 xml、本地 `/tmp/dev-5557.apk` 与取证目录已删；工作区 `dirty=0`
- 设备最终安装的是**本系列构建**（sha256 `d5953c0c…`），prefs 干净

## 交付坐标

| 项 | 值 |
| --- | --- |
| 本系列提交（时间序） | `4319403502` → `7809ff4952` → `d7b9439201` → `f2ccce36c9` → 本文档提交 |
| 分支 | `dev2` |
| 推送 | **未推送**（未授权） |
| 资源回收 | 各会话末次构建后均执行 `gradlew --no-daemon clean`；末次收口时复核 `app/build`、`catvod/build`、`quickjs/build`、`chaquo/build`、`nodejs/build` 已删除且无残留 Java/Gradle 进程 |
