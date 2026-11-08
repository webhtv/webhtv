# MPV-BUFFERING-STALL-WATCHDOG-20261017

## Recovery anchor

- Objective: (1) 给 MPV 补上缓冲停滞看门狗，让卡在 BUFFERING 且无任何进展的会话有界退出；(2) 把内核/软硬解切换的原因补齐成统一、可机读的日志，使“为什么跳内核/切软硬解”能从导出日志直接回答。
- Lane: `standard`。
- Baseline: branch `dev2`, HEAD `3eee5e8c9d618c4f7845e6bd0100357f28ef82d0`。
- Protected dirty paths: `.wh_diag/**`（任务前已存在，未纳入 scope）。
- Status: 已实现，单测与四变体编译通过并已提交（`c2a09e7647dd`）；模拟器实测已完成（§7.1 + §7.1.1 + §7.1.2：MPV 布防、不误杀、切换归因、**停滞超时 `action=timeout stalledMs=60934`** 均已实证，超时后三级失败回退齐全）。第三轮（2026-10-18）复核了存疑的 `History.player` 语义：对照实验证明「-1 = 无偏好」语义**完好**，先前的「3→0」属测量假象，**无需修改生产代码**；另查实 `chosen` 的文档口径与代码不符（已更正文档，代码未改）。
- Next action: 无（可选后续：① §7.2 第 2–4 项（弱网不误杀、手动换内核/软硬解归因、32 位与手机版布防）仍待补；② `chosen` 语义是否按「用户 vs 自动」重命名/修正，属独立任务）。

## 1. 用户可见能力

1. **MPV 卡缓冲不再只能干等**：MPV 播放中若进入 BUFFERING 且位置与缓冲末端都不再前进，会在有界时间内结束该次尝试并走既有失败回退，而不是无限转圈。
2. **切换原因可追溯**：导出的播放日志里，每次换内核或换软硬解都会有一行 `switch-cause`，写明原因（是否为用户显式选择看 `cause`）、从哪个内核/软硬解换到哪个。

## 2. 现场证据与根因

### 2.1 15 秒启动看门狗对 MPV 实际不生效

`Constant.TIMEOUT_PLAY = 15s`（上游 fongmi 原值，鱼壳同样）。`PlayerManager` 的 `onPlaybackStateChanged`：

```java
if (state != Player.STATE_IDLE) App.removeCallbacks(runnable);
```

**任何非 IDLE 状态都取消定时器，包括 BUFFERING。** 而 `MpvPlayer.openCurrent()` 在 `handlePrepare()` 里第一步就 `playbackState = Player.STATE_BUFFERING; loading = true;`，因此 MPV 在真正开始解码之前就上报 BUFFERING，15 秒定时器随即被取消。

用户提供的导出日志印证：`setMediaItem timeout=15000`（22:09:38.016）→ `state=BUFFERING`（22:09:38.115，99ms 后）→ `state=READY`（22:09:49.204，11522ms）。虽然接近 15s，但**没有触发超时**，因为定时器在 99ms 时已被取消。

结论：对 MPV 起播路径，调大 15 秒没有意义；真正缺的是缓冲停滞兜底。

### 2.2 停滞看门狗只挂在 Exo 上

```java
private void armBufferingStallWatchdog() {
    if (!isExo() || player == null || spec == null) return;   // ← 只有 Exo
```

`checkBufferingStall()` 同样以 `!isExo()` 提前退出，seek 重臂点也是 `if (isExo()) armBufferingStallWatchdog();`。

后果：MPV 一旦卡在 BUFFERING，15 秒启动看门狗已被取消、停滞看门狗不存在，**没有任何兜底**。

### 2.3 看门狗本身是引擎无关的，MPV 已假设它存在

`ExoBufferingStallWatchdog` 只消费 position、buffered end、loading 三个量，全部引擎都能提供：

- `MpvPlayer` 的 `bufferedPositionMs()` 走 `MpvBufferedPositionPolicy`（日志 `playback-progress: action=buffer-read source=direct-engine` 证实会前进）；
- `isLoading()` 映射 mpv 的 `loading` 字段；
- 超时值无需调整：Exo 阈值 `MAX_STREAMING_REBUFFER_MS = 15s`，MPV 的 `MpvPerformanceSetting.getRebufferMs()` 最大映射到 8s（驱动 mpv 自身 `cache-pause-wait`），都远低于看门狗的 20s/60s 上限。

关键佐证：`MpvPlayer.timeOutSeekBuffering()` 的注释**已经假设存在这个看门狗**——

> Publishing READY would hide the progress indicator over a frozen frame … and would also cancel the stall watchdog, since checkBufferingStall() disarms on READY. Leave the state alone and let that watchdog own the stall.

对 MPV 来说，那个被引用的看门狗此前是空的。

### 2.4 切换原因日志此前无法回答“为什么换”

切换分散在多个路径，措辞与字段各异：

| 路径 | 原日志 |
| --- | --- |
| 失败回退内核 | `player: fallback player from=… to=…` |
| 失败回退软解 | `player: fallback decode player=… from=hard to=soft` |
| 手动换内核 | `player: switch player type=… chosen=…` |
| 自动软解 | `playback-stage: stage=switch-decode-auto`（无前后值） |
| 手动换软硬解 | `player: switch decode fresh decode=…`（无原因、无来源） |

没有统一字段，也没有“这次是用户选的还是自动回退的”这一维度，导致“设置 MPV 却跳到 EXO”这类问题无法从日志定性。

## 3. 方案比较

| 方案 | 收益 | 缺陷/风险 | 决定 |
| --- | --- | --- | --- |
| 只调大 `TIMEOUT_PLAY` | 改动最小 | 对 MPV 起播无效（定时器已被 BUFFERING 取消）；且与站点取址超时共用常量，等于放宽所有源站超时 | 否决 |
| 把看门狗复制一份 MPV 版 | 不动 Exo 路径 | 重复实现与重复维护，两套阈值会漂移 | 否决 |
| 看门狗上移为引擎无关，各引擎共用 | 单一实现、单一阈值；MPV 立即获得兜底 | 需确认 MPV 的 position/buffered/loading 语义与 Exo 一致（已确认）；需确认 20s/60s 高于两个引擎的 rebuffer 阈值（已确认） | **采用** |
| 给每个切换点加 `cause` 参数贯穿到 Activity | 原因最精确 | 需改 3 个 Activity、5 个调用点，破坏 4 个既有 source-text 测试的断言字符串；公开签名扩张 | 否决 |
| 在 `PlayerManager` 内部按入口推导原因 | 零公开签名变化、零 Activity 改动、既有测试断言全部保持有效 | 原因粒度取决于入口数量（当前每个入口语义唯一，足够） | **采用** |

## 4. 实现

### 4.1 看门狗引擎无关化

- `git mv` 把 `player/exo/ExoBufferingStallWatchdog.java` 移到 `player/BufferingStallWatchdog.java`，类名同步；测试同样移动。用 `git mv` 保留历史。
- 新增 `stalledDurationMs(now)`，在超时日志里报告**实测停滞时长**而不是只报配置上限。
- 类注释补上 MPV 维度：说明为何两个引擎共用、以及两个引擎各自的 rebuffer 阈值都必须低于它。

### 4.2 三个 Exo 专属门禁放开

- `armBufferingStallWatchdog()`：`!isExo() || player == null || spec == null` → `player == null || spec == null`。
- `checkBufferingStall()`：同上放开。
- seek 重臂点与 `STATE_BUFFERING` 分支：去掉 `if (isExo())`。

### 4.3 统一切换原因日志

新增 `switch-cause` 日志与两个 `logSwitchCause` 重载：

```text
switch-cause kind=kernel|decode|none cause=… chosen=true|false from=<内核>/<软硬解> to=<内核>/<软硬解> position=…
```

- `kind` **由实际变化推导**（内核变了→`kernel`，内核没变而软硬解变了→`decode`），不由调用方声明，避免“同内核只换软硬解”被误报成换内核。
- `chosen` 表示**本次切换是否把用户选定的内核记为会话内核**（代码里它同时驱动 `switchEngine` 的 `if (chosen) PlayerSetting.putActivePlayer(type)`），不是「本次切换是否由用户发起」。实测反例：用户点击切换软硬解走 `cause=user-choice` 却记 `chosen=false`（`switchDecode` 两处），而用户换内核的 `cause=user-choice` 记 `chosen=true`。只看 `cause` 判断「用户 vs 自动」更准；`chosen` 适合回答「会话内核是否被这次切换改写」。
- `from/to` 同时给出内核名与软硬解，避免只看到其中一个维度。

原因取值：

| cause | 触发点 |
| --- | --- |
| `user-choice` | 所有 `switchPlayer`/`switchDecode` 入口（均为 UI 用户操作） |
| `history-kernel-before-play` | `preparePlayer(force=false)`：按剧集记住的内核 |
| `history-kernel-retry-after-error` | `preparePlayer(force=true)`：onError 重试（`force` 本身就是归因信号） |
| `fallback-decode:<trigger>` | 失败后切软解 |
| `fallback-kernel:<trigger>` | 失败后换内核 |
| `kernel-unavailable` | 内核不可用（非播放失败） |
| `auto-no-hardware-decoder` | Exo 硬解无可用解码器时自动转软解 |
| `hard-decode-switch-retry` | 硬解初始化失败后重试硬解 |

`<trigger>` 由 `fallbackTrigger(PlaybackException)` 把 errorCode 归成短标签（`timeout`/`decoder-init`/`decoding`/`format-unsupported`/`network-timeout`/… 其余 `code-<n>`），保留数值以免丢失区分度。

**关键约束：未改动任何公开签名**（`preparePlayer`/`switchPlayer`/`switchDecode` 全部保持原样），因此 3 个 Activity 文件与 4 个既有 source-text 测试完全未动。这一点是设计选择的结果，不是巧合——见 §3 最后两行。

## 5. 影响与风险

- **兼容性**：无格式、ABI、Android API、包体积影响；纯 Java。
- **性能**：看门狗是 1s 轮询，此前只对 Exo 生效，现在对 MPV/IJK 同样生效。每 tick 只读 position/buffered/loading 三个已有属性，不新增 native 调用。MPV 的 `getBufferedPosition()` 读的是缓存属性而非同步 `mpv_get_property`，不会阻塞主线程。
- **主要回归风险**：误判正常慢速缓冲为停滞。已用三重保护约束：停滞判据要求 position 与 buffered **都不前进**；loading 时用 60s 上限；90s episode ceiling 额外要求段内净进展不足 15s 才触发。
- **次要风险**：MPV 的 buffered 读数在 seek 后会回退，可能被当成 discontinuity 而重锚（这是期望行为，`DISCONTINUITY_TOLERANCE_MS` 已覆盖）。
- **日志量**：`switch-cause` 每次切换一行、`buffering-stall` 每次布防/超时各一行，均为低频事件级日志，非每帧。

## 6. 验证

执行（2026-10-17 Asia/Shanghai）：

1. **定向用例**：`BufferingStallWatchdogTest` 23/23、`PlayerManagerSwitchCauseSourceTest` 8/8、`PlayerManagerLifecycleSourceTest` 3/3，失败/错误/跳过均为 0。
2. **全量单测**：`:app:testMobileArm64_v8aDebugUnitTest` → `5085 tests completed, 7 failed, 3 skipped`。
3. **基线对照**：同一命令在 `git stash` 后的 `3eee5e8c9d` 上跑出 `5076 tests completed, 7 failed, 3 skipped`；**失败集合逐条一致**（`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`、`ReaderPlaybackRoutingSourceTest`×2、`TmdbSourceOnlyInteractionTest`、`TmdbUIAdapterTest`、`WebThemeTokenSourceTest`），即**零新增失败**，用例数 +9。
4. **四变体编译**：`compileMobileArm64_v8aDebugJavaWithJavac`、`compileLeanbackArm64_v8aDebugJavaWithJavac`、`compileLeanbackArmeabi_v7aDebugJavaWithJavac`、`compileMobileArmeabi_v7aDebugJavaWithJavac` 全部 `BUILD SUCCESSFUL`。
5. **变异测试**（证明断言可失败，非恒真）：
   - 把 `armBufferingStallWatchdog()` 重新用 `if (isExo())` 门禁 → `bufferingStateArmsTheStallWatchdogForEveryEngine` 如期 FAILED。
   - 把 `previousType/previousDecode` 的捕获移到 `playerType = type` **之后** → `everyKernelSwitchFlowsThroughTheSingleCauseLogger` 如期 FAILED。
   - 两次均从备份还原并复跑全量，回到 7 个预存失败。
   - 另有一次变异**未**失败（把捕获提前），已如实记录：那不是缺陷变异，因为捕获仍在覆盖之前，不构成对不变量的破坏。
6. `git diff --check` 通过。

### 6.1 设计过程中修正的一次错误

首版把 `cause` 作为参数贯穿到 3 个 Activity，导致 4 个既有 source-text 测试失败（`VideoActivityLayoutTest`×2、`TmdbUIAdapterTest`×1、`TmdbDetailDirectPlayTransitionSourceTest`×1）。经基线对照确认这 4 项确为本次引入后，改为在 `PlayerManager` 内部按入口推导原因，撤销全部 Activity 改动，4 项失败随之消失。这 4 项失败**不是**被放宽或删除的断言，而是改动被改回后自然恢复。

## 7. 设备验证记录

### 7.1 模拟器实测（2026-10-17，已执行）

**设备**：`192.168.50.3:5557`（本仓库约定的 2 号模拟器），Android 9 / x86_64 + houdini 翻译 / 应用 arm64-v8a。

**方式**：覆盖安装 `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`（200,391,926 字节，debug key SHA-1 `d92b568f…`，与设备已装包签名一致，数据保留）。**未打正式包**。

#### 通过：看门狗在 MPV 上布防

```text
buffering-stall: action=arm player=3 decode=1 position=11952 buffered=11952 loading=true
VO: [gpu] 1920x1080 => 1920x1080 yuv420p
```

- `player=3` 即 **MPV**。改动前 `armBufferingStallWatchdog()` 的 `if (!isExo()) return;` 会让这行日志根本不可能出现，因此这是门禁已放开的直接证据。
- `VO: [gpu] 1920x1080` 证明 MPV 确实加载并渲染了媒体，不是空转。
- 全程**没有** `action=timeout`，即正常播放未被误杀（§7 第 2 项“不误杀”在此场景下成立）。

#### 顺带复现并定性了「设置 MPV 却跳到 EXO」

```text
switch-cause: kind=kernel cause=history-kernel-before-play chosen=true from=MPV/hard to=EXO/hard position=0
```

全局默认内核已设为 MPV（`player=3`），但**该文件的历史记录里 `player=0`（EXO）**，于是起播前被切回 EXO。设备上历史分布为 `-1:12 / 0:170 / 3:4`，即绝大多数记录是 EXO。

这条正是本任务新增日志的价值所在：一行即可区分「历史记录覆盖了设置」与「失败自动回退」。**注意**：这行 `cause=history-kernel-before-play` 已经说明来源是历史记录；`chosen=true` 只表示本次切换改写了会话内核（回退路径一律 `chosen=false`），**不能**据此推断「用户显式选过 EXO」。用户是否真的选过那一集的内核，要看历史记录的 `player` 字段本身。

#### 未通过：停滞超时（`action=timeout`）无法在模拟器上端到端触发

尝试了 7 种构造方式，全部退化为**源站取址的 30s 超时**（`TimeoutFutureException` + `player finish cost=30017ms`，`start-file`=0），MPV 没有机会加载媒体：

| # | 尝试 | 结果 |
| --- | --- | --- |
| 1 | 32KB 全零流 | 非有效 MP4，取址超时 |
| 2 | 真实 MP4 + 声明超大 Content-Length | 代理等待剩余字节，取址超时 |
| 3 | 无 Content-Length 的连续流 | 同上 |
| 4 | `moov` 在文件前部的 vt.mp4 | 同上 |
| 5 | 完整 HTTP 语义（HEAD / Range / 206） | 同上 |
| 6 | `adb reverse` 走 `127.0.0.1` | 同上 |
| 7 | 按请求序号区分 spider 探测与播放 + 手工 faststart 重排的 sintel | 同上 |

**根因（测试环境限制，非改动缺陷）**：`push_agent` 站点的 spider 会先探测 URL，探测阶段被停滞就自己挂到 30s 取址超时；而 `SiteApi.playerContent` 只有 `file:` 前缀或站点为空时才绕过 spider（`if (PUSH.equals(key) && (site.isEmpty() || isLocalFileUrl(requestId))) return pushPlayer(...)`），HTTP URL 必走 spider。

另注：`SIGSTOP` 冻结进程不可行——它会连同看门狗一起冻结，无法验证超时。

**因此停滞超时目前只有单测覆盖**（`BufferingStallWatchdogTest` 23 例，含 episode ceiling、seek 重锚、慢速填充不误杀、暂停不累积等边界），尚无设备端到端证据。

#### 建议的超时验证方式

真机/模拟器上改用**限速**而非服务端停滞：把设备带宽限到低于媒体码率，让 MPV 自然耗尽缓冲。这样取址能正常完成、媒体能加载，`position` 与 `buffered` 随后双双冻结，才是看门狗要捕获的状态。服务端停滞在 `push_agent` 路径上先天不可用。

**但限速本身不够**：第二轮实测（§7.1.1）证明会话若在 READY 之前就被冻结，总是 15 s 启动守卫先生效；要打到看门狗，还需「已 READY + 之后无引擎重建」的干净窗口。

### 7.1.1 第二轮实测（2026-10-17 23:00 – 2026-10-18 00:55，同一分配设备）

**目的**：补上 §7.2 第 1 项（`action=timeout`）。**结果：仍未取得该行**；但把上一轮到此轮全部失败的原因定位到了确切的代码路径（见本节末），并新增 3 项实测证据。

**工具链**（本轮自建，可复用）：宿主 Python 带 Range 服务的限速/冻结服务器（路径式限速 `/r<字节每秒>/<文件>`，`POST /freeze` 冻结全部大区间连接），配合 `adb reverse tcp:18181`，把「设备带宽低于媒体码率」做成可复现构造；30 秒内即可让 MPV 自然进入 `STATE_BUFFERING` 且 `position`/`buffered` 双双冻结。

#### 通过 A：MPV 上布防（复测）

```text
buffering-stall: action=arm player=3 decode=1 position=0 buffered=0 loading=true
buffering-stall: action=arm player=3 decode=1 position=2042 buffered=2411 loading=true
```

`player=3` 即 MPV，多条不同 `position/buffered/loading` 组合，确认布防在真实 MPV 会话上重复成立（上一轮 §7.1 只有一条）。

#### 通过 B：不误杀（限速但高于码率）

码率约 84 KB/s 的样片按 **200 KB/s** 与 **110 KB/s** 限速各播一遍：`position` 全程单调前进，直到 `STATE_ENDED`，**全程无 `action=timeout`、无 `switch-cause`**。即“慢但真在前进”的会话不会被判停滞。

#### 通过 C：切换归因日志（`kind` 判定正确）

本轮实际触发出三类 `switch-cause`，`kind` 由实际变化推导的规则（`PlayerManager.logSwitchCause`）在设备上表现正确：

```text
kind=decode cause=fallback-decode:timeout chosen=false from=MPV/hard to=MPV/soft position=0
kind=kernel cause=fallback-kernel:timeout chosen=false from=EXO/soft to=IJK/hard position=30410
kind=kernel cause=fallback-kernel:timeout chosen=false from=MPV/soft to=EXO/hard position=23500
kind=kernel cause=history-kernel-before-play chosen=true from=MPV/hard to=EXO/hard position=0
```

「内核没变只换软硬解」记为 `decode`、内核变化记为 `kernel`、「来源是历史记录」靠 `cause=history-kernel-before-play` 识别，三者可直接从导出日志区分（`chosen` 一律按 §4.3 的实测口径读，不要拿它当「用户 vs 自动」的判据）。§7.2 第 3 项中的「播放失败自动回退」类已覆盖；「用户手动换内核/软硬解」两类仍需手按遥控器，本轮未做。

#### 未通过：`action=timeout` 的根因（本轮新结论）

所有构造最终都由 **15 秒启动守卫** `App.post(runnable, Constant.TIMEOUT_PLAY)` 先超时，走 `fallback-*:timeout`，而看门狗的超时门槛是 `STALL_TIMEOUT_MS=20s`（非 loading）或 `LOADING_STALL_TIMEOUT_MS=60s`（loading），**两者都大于 15 s**。更关键的是：

1. `runnable` 在 `onPlaybackStateChanged(STATE_READY)` 之前每一条准备/重建路径（`setMediaItemNow`、`restartWithProxy`、`rebuildAndRestartMpv`、`awaitLocalProxyAndSetMediaItem` 等 20+ 处）都会重新 `App.post(runnable, TIMEOUT_PLAY)`；
2. MPV 在本机会连续做输出策略重建（日志可见 `hardware safety override applied hwdec=mediacodec-copy vo=gpu` → 又切 `hwdec=no vo=gpu-next`），每次重建都重置这 15 s；
3. 因此「停滞发生在会话尚未稳定 READY 时」这一类场景，**必然**由启动守卫在 15 s 收尾，看门狗没有机会到点——这不是看门狗缺陷，而是两条兜底的分工：启动守卫管「一直没起来」，看门狗管「起来后又卡住」。
4. 要打到看门狗，需要「已 READY 且此后 20–60 s 内没有任何引擎重建」的干净窗口。本轮多次尝试都在窗口内被 MPV 输出策略重建打断；而且应用的磁盘缓冲（日志 `TV-playback-disk-buffer action=query … resultMs=52125`）会把区间直接从本地补齐，进一步压缩窗口。

**结论**：`action=timeout` 的设备端到端证据在本轮（§7.1.1）仍缺，但已不再是「不知道怎么触发」——需要的构造条件已明确为「READY 之后、零重建、持续 20 s 以上零进展」。下一轮应先在设置里固定 MPV 输出模式（避免自动档触发重建），再冻结网络。**→ 已按此构造在 §7.1.2 取得证据。**

#### 本轮发现、但**不属于**本任务的既有缺陷

1. **【已核实为我的测量假象，非缺陷】** 我曾在报告里写「全新历史记录的 `player` 为 0，被当成用户显式选过 EXO」。随后用预置值对照实验证伪：应用从零新建的行是 **`-1`**；预置 `3` 回读仍 `3`；预置 `-1` 回读仍 `-1` 且正确回退全局默认。`History.player` 的「-1 = 无偏好」语义完好，`setPlayer` 三个调用点都带 `isPlayer()` 守卫。**无需修复。**
2. **设备库里 169 条 `player=0` 的真实来源（旧 bug 遗留数据）**：`4db26d042b`（2026-08-31「修复：播放器里切内核不再全局生效，改回按剧集记住」）删掉了一处例行存档写内核的旧代码 `updateInlineHistoryPlayer()` → `history.setPlayer(player().getPlayerType())`。该旧代码会在**每次例行存档**把当前会话内核写进历史，EXO(0) 因此被批量盖章。数据时间分布与判断完全吻合：`player=0` 最晚 **2026-10-16**、`player=-1` 一直延续到 **2026-10-18**（修复后的构建开始产生 `-1`）。这是**已修复 bug 的存量残留**，不是活跃缺陷；是否清理存量请另行决定，不在本任务范围。
3. **`chosen` 字段的文档口径与代码不符（真实缺陷，未修）**：文档原写「`chosen` 区分用户显式选定与自动回退」，但用户点击切换软硬解（`switchDecode`，`cause=user-choice`）传的是 `false`，与用户换内核（`chosen=true`）不一致。`chosen` 实际含义是「本次切换是否改写会话内核」（它同时驱动 `switchEngine` 的 `putActivePlayer`）。已按实测口径改写文档；**代码未改**——修正它会牵动 C78 的变异验证集，超出本任务范围，记为待办。
4. **覆盖安装会清空应用数据**（prefs、`tv` 库、运行时权限全部回到默认）。本轮因此需要重新写入 `player=3`、打开 `debug_log`，并用 `pm grant` 补 `READ_EXTERNAL_STORAGE`/`WRITE_EXTERNAL_STORAGE`/`RECORD_AUDIO`/`CAMERA`，否则应用会卡在权限对话框。**`debug_log` 关闭时 `PlaybackTrace` 一行都不输出**——上一轮与本轮前半段「日志空白」正是这个原因。
5. 本轮 23:16 左右出现一次**非本任务操作导致的包被卸载**（`pm list packages` 中包消失，随后重新 `install -r` 恢复）。未定位触发者，记录以备复查。

### 7.1.2 第三轮实测：`action=timeout` 已取得（2026-10-18 12:05，同一分配设备）

**目的**：补上 §7.2 第 1 项。**结果：已取得完整证据链。**

**与前两轮失败的关键差异**：把 MPV 输出模式从「自动」(0) 钉到 **GPU 渲染 (1)**（`perf_mpv_output_mode=1`），消除了自动档的输出策略重建——那正是前两轮每次都在 15 s 启动守卫就被打断的原因。

**构造**：宿主限速服务器（路径式 `/r150000/<file>`，即 150 KB/s，高于该样片约 84 KB/s 的码率，保证能正常起播）→ `adb reverse` → 推送 URL → 等 `position` 真的前进到 READY → `POST /freeze` 冻结全部大区间连接。冻结时 `position=15187`，随后 `position` 与 `buffered` 双双冻结在 `20458/20458`。

**决定性证据（超时行 + 实测停滞时长）**：

```text
buffering-stall: action=arm    player=3 decode=1 position=0     buffered=0     loading=true
buffering-stall: action=arm    player=3 decode=1 position=20458 buffered=20750 loading=true
buffering-stall: action=timeout stalledMs=60934 loading=true position=20458 buffered=20779 player=3 decode=1
```

- `player=3` 即 **MPV**，`stalledMs=60934` 是**实测**停滞时长，命中 `LOADING_STALL_TIMEOUT_MS=60_000`（`loading=true` 走 60 s 门槛，与设计一致）。
- **同时验证了 C78 修复 3.2**：`stalledMs` 不再是恒为 `-1` 的失效字段（修复前 `onBufferingStall()` 先 `cancelBufferingStallWatchdog()` 再读，未布防时返回 `-1`）。60934 ms 与冻结时刻到超时时刻的墙钟差吻合。
- `buffered` 从 20750 只涨到 20779（29 ms 抖动，远小于 `DISCONTINUITY_TOLERANCE_MS=1000`），因此未被误判为 seek 而重新锚定——正是设计意图。

**超时后走既有失败回退，未无限转圈**（§7.2 第 1 项的后半句）：

```text
switch-cause: kind=decode cause=fallback-decode:timeout chosen=false from=MPV/hard to=MPV/soft position=20458
player: fallback player from=MPV to=EXO ... cause=androidx.media3.common.PlaybackException
switch-cause: kind=kernel cause=fallback-kernel:timeout chosen=false from=MPV/soft to=EXO/hard position=20458
```

即：先换软解（`chosen=false`，自动回退）→ 仍失败 → 换内核到 EXO，三级回退全部带 `cause=*:timeout` 归因。

**由此确认 §7.1.1 的分工结论**：15 s 启动守卫管「一直没起来」，看门狗管「起来后又卡住」；两者不冲突，且本次两者都在同一会话里各自正确生效过（`player finish cost=30010ms` 属前者）。

**本轮踩到的两个自建工具缺陷（已修，记录以免重踩）**：

1. 限速服务器把 `.srt`/`.ass` 字幕探测请求也回退成 4 MB 样片，于是字幕请求本身触发「大区间冻结」规则，播放还没开始就被自己的服务器卡住。修正为：只有媒体扩展名（`.mp4`/`.mkv`/`.ts`/`.m4v`）或无扩展名才回退到样片，字幕一律 404。
2. `/freeze` 标记是**进程级持久**的，上一轮冻结后没重启服务器，导致下一轮所有连接从 0 字节就冻结。修正为每轮重启服务器。

另外记录一次我自己造成的干扰：把本地脚本命名为 `inspect.py` 遮蔽了 Python 标准库 `inspect` 模块，导致限速服务器启动即崩（`AttributeError: module 'inspect' has no attribute 'signature'`）；已重命名为 `dbcheck.py`。

### 7.1.3 第四轮实测：§7.2 第 2/4 项通过，并发现一个真实缺陷（2026-10-18 13:30–18:20）

#### 通过：不误杀（弱网）+ 暂停不累积（§7.2 第 2 项）

| 场景 | 构造 | 结果 |
| --- | --- | --- |
| 弱网持续前进 | 60 KB/s 限速（码率约 84 KB/s） | `buffered` 从 217 持续增长到 52208，`position` 同步前进至 ENDED，**全程零 `action=timeout`**（多次 `action=arm` 后都自然取消） |
| 暂停超 ceiling | 播到 3.8 s 后 `control&type=pause` + 冻结网络 100 s（`EPISODE_CEILING_MS=90_000`） | 状态保持 `state=2`（PAUSED），**零 `action=timeout`** |

即“位置不动但 buffered 在涨”和“用户主动暂停”都不会被看门狗误杀，与设计意图一致。

#### 通过：32 位与手机版布防（§7.2 第 4 项）

| 变体 | `primaryCpuAbi` | 日志 |
| --- | --- | --- |
| leanback armeabi_v7a | `armeabi-v7a` | `action=arm player=3 decode=1` |
| mobile arm64_v8a | `arm64-v8a` | `action=arm player=3 decode=1` |

两个变体均为重新构建（含本任务最新修复）后覆盖安装，MPV 上布防成立。

#### 发现并修复的真实缺陷：控制栏「解码」按钮不写 `switch-cause`

按 §7.2 第 3 项核对手动切换归因时发现：`switch-cause` 全仓只有一个产生点（`PlayerManager.logSwitchCause`），而手动软硬解切换有两条路径：

| 入口 | 是否记日志 |
| --- | --- |
| `switchDecode(PlaySpec…)` / `switchDecode(Result…)`（换源/刷新后重取址） | 有 |
| `toggleDecode()`（控制栏「解码」按钮，双 flavor 最常用入口） | **无** |

`toggleDecode` 的调用链（`beginIjkRuntimeManualOverride` / `rebuildPlayer` / `setMediaItem`）均不记日志，所以**日常最常用的手动换软硬解完全不产生 `switch-cause`**——直接违反本任务 §1 第 2 项的公开承诺。

**修复**（`a9a52e2cb7`）：在 `engine.setDecode(next)` **之前**捕获 `previousDecode`，并补一行 `logSwitchCause("decode", "user-choice", …, chosen=false)`；新增回归锁 `manualDecodeButtonLogsItsCauseToo`（含「先捕获后覆盖」顺序断言）。

**两轮变异测试（其中一次抓出了我自己写的假绿）**：

| # | 变异 | 期望 | 实测 |
| --- | --- | --- | --- |
| 1a | 删掉新增的日志行 | 转红 | **假绿** —— 我用 `switchPlayer(int type)` 作方法体结束锚点，它隔着 ~130 行，把 `switchDecode` 里真正的 `logSwitchCause("decode","user-choice",…)` 一起圈进来了 |
| 1b | 同上，锚点改为紧邻的 `maybeFallbackToSoftDecode(` | 转红 | 如期 `manualDecodeButtonLogsItsCauseToo FAILED` |
| 2 | 把 `previousDecode` 捕获移到 `engine.setDecode(next)` 之后 | 转红 | 如期 FAILED |

修完后 `git hash-object` 与修复版逐字节相同，定向 10/10 绿；全量 leanback `4346 tests / 8 failed` 与 C78 记录的基线失败集**逐条一致**（零新增失败，均为 Windows CRLF 与既有夹具缺陷）。

#### 未完成：§7.2 第 3 项的设备端确认

`toggleDecode` 的修复已由单测锁定，但**未能在设备上按遥控器抓到该行**：`uiautomator dump` 在这个播放页持续报 `could not get idle state`（MPV 持续出帧），而绝对坐标 tap 又会被 `HorizontalScrollView` 的横向滚动带偏——本轮多次 tap 实际落在「软解/播放参数」之间而没命中按钮。D-pad 行走可看到焦点环确实落在该按钮上（截图确认），但同一轮里走到末屏会把播放页退回详情页。

**结论**：该项的功能已由“代码路径 + 回归锁 + 变异测试”三重証实；**缺的只是遥控器手按的设备回放**，下一轮可用“先暂停再 D-pad 定位 + CENTER 激活”（暂停后 `uiautomator` 可用，本文已验证暂停能拿到 `state=2`）。

### 7.2 仍需完成的设备验证

1. ~~**停滞兜底**~~ → **已完成，见 §7.1.2**（`stalledMs=60934`，三级回退齐全）。
2. ~~**不误杀（弱网场景）**~~ → **已完成，见 §7.1.3**（弱网持续前进与暂停 100 s 均零 `action=timeout`）。
3. **切换归因**：自动回退类已实证（§7.1.1/§7.1.2）；「用户手动换内核」（`preparePlayer`/`switchPlayer`）与「用户手动换软硬解」的代码路径与回归锁已就位（§7.1.3），**仅差遥控器手按的设备回放**。建议路径：暂停 → D-pad 定位到「解码」→ CENTER 激活 → 查 `switch-cause kind=decode cause=user-choice`。
4. ~~**32 位与手机版**~~ → **已完成，见 §7.1.3**（两个变体均 `action=arm player=3`）。
5. 回退判据：出现误杀或异常回退时，`git revert` 本任务提交即可完整恢复。

### 7.3 环境还原

验证后设备已恢复到任务前状态：prefs（移除临时写入的 `player=3`）、`tv` 数据库（历史分布回到 `-1:12 / 0:170 / 3:4`）、设备临时文件与 `adb reverse` 全部清理；宿主临时目录与测试服务进程已删除。仓库工作区无改动（仅任务前既有的 `.wh_diag/`）。

**第二轮（2026-10-18 00:52）还原记录**：因覆盖安装清空了应用数据，本轮无法还原到原数据，改为还原到**采集前刚备份的状态**——`prefs` 整文件回写（`cmp` 与原始文件比对仅行尾写法不同并具）、`tv` 库回写备份并删除本轮新增的 `127.0.0.1:18*` 测试历史行（1 条）、`/sdcard/Download/c79bak` 与 `adb reverse/forward` 全部清理、`am force-stop` 结束会话。20 KB/s–200 KB/s 限速实测期间一次**非本任务操作导致的卸载**，已用同版本 debug 包覆盖安装恢复。

## 8. 回滚

纯 Java 改动（看门狗移动 + 三个门禁放开 + 原因日志），`git revert <本任务提交>` 即可完整回退；不涉及 native、依赖锁、补丁、产物或持久化 schema。恢复 tag 见提交记录。
