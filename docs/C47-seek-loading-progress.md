# C47：拖拽进度时加载中/网速不显示

## Recovery anchor

- **目标**：参考上游修复「拖拽进度时大概率不显示网速等加载中效果」的问题——拖动播放进度条后，加载圈（转圈 + 网速）基本不出现或一闪即没。
- **验收标准**：① 两个 flavor 的 `VideoActivity` 在 seek 期间打开「最小可见 + 确实可收」窗口，任何收圈路径不得在窗口未关时收圈；② 窗口时长盖过一个完整网速采样间隔，保证网速读数能渲染；③ 双 flavor Java 编译通过；④ 新增契约测试与受影响回归测试全绿且非 skip；⑤ 变异验证能精确命中；⑥ 提交 + 本地 annotated recovery tag。
- **当前状态**：已实施并完成全部自动化验证，待 `task_guard.sh finish`。
- **下一动作**：`bash .codex/scripts/task_guard.sh finish --verified <evidence> --commit-message <msg>`（原子提交 + recovery tag），不推送。

## 冻结基线

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev4` |
| 任务开始时 HEAD | `24265785a33d09ca1d1d3d36370d010ff1e89eac`（Merge PR #419 from Silent1566/dev3） |
| 参考上游 | `https://github.com/webhtv/webhtv` 分支 `Silent1566` = `05469a967ad32818b6e76e080100bfae1787ab76`；`main` = `4e30ffaf219b1db15fe5662aeff6f9820e2b32bf` |
| 初始脏路径 | 无（`git status` 干净） |
| 回滚锚点 | `24265785a33d09ca1d1d3d36370d010ff1e89eac` |
| 时间/时区 | 2026-10-08 约 18:30，UTC+08:00 |

## 上游取证与结论

用户要求「参考上游修复」。先确认上游到底怎么处理这条链路，避免凭空发明实现。

- `webhtv/Silent1566` 与 `webhtv/main` 以及 `feature-menu`、`feature/mpv-dv7-fel`、`media/release-1.11.0-alpha01-fongmi`、`upgrade/media3-1.11-fongmi-20260705` 全部分支：`onSeekStarted`、`SeekListener`、`mSeekProgressFallback`、`mSeekProgressPending`、`canHideSeekProgress` 的出现次数均为 **0**。
- 上游这两个文件合计 6442 / 6607 行，是「本地进度控制增强之前」的版本；`2839bb40955`（`feat(video): 添加视频播放进度控制功能`）、`4e303d30d34`（`fix(tv): stabilize Exo loading indicator state`）、`08b8bbc5401`（`refactor(exo): restore upstream playback mode`）都不是上游祖先。
- 上游的加载圈是**纯状态驱动**的：`showProgress()` 只在 `STATE_BUFFERING` 分支被调用，`STATE_READY` 分支 `hideProgress()`。也就是说上游依赖播放器在 seek 期间**如实上报 BUFFERING**，本身没有任何 seek 专属窗口。
- 上游确实已经修过「seek 期间播放器谎报 READY」这一层：本地 `dev4` 已包含 `6a5d93cf48f`（`MpvPlaybackState.resolveAfterSeekRequest` + `MPV_EVENT_SEEK` + seek 缓冲窗口 + 15s 超时兜底），该提交**只存在于本地 `dev4`**，不在上游任何分支。
- 也就是说：上游把「seek 要如实进 BUFFERING」这件事修在了播放器层，本地又额外加了 seek 专属的进度控制与「加载圈兜底收口」；本次症状正是**本地新增的兜底与 seek 提交时序**之间的竞态，上游没有对应代码可直接摘取。

**决策**：不照搬上游（上游无对应实现，且回退到纯状态驱动会丢掉本地 seek 进度控制这一已交付能力）。按上游的语义方向（seek 期间必须如实显示加载态）在本地补上缺失的「seek 窗口」这一环，即「本地适配后采用」。

## 根因

用户拖拽进度条走 `CustomSeekView.onScrubStop` → `seekToTimeBarPosition`：

1. `seekListener.onSeekStarted()` **先**回调，`VideoActivity.onSeekStarted()` 里 `showProgress()` 让圈可见，并 `App.post(mR2/mR3, 0)` **立刻**投递网速 ticker；
2. 紧接着才 `commandPlayer.seekTo(positionMs)` 把 seek 交给引擎；
3. ticker 在第一跳执行 `setTraffic()` → `hidePlaybackProgressIfStale()`，此时引擎**尚未开始 seek、仍报 READY**，于是 `showPlaybackContent()` → `hideProgress()` 把圈收掉。

结果：圈在同一帧（毫秒级）被自己亮起又收掉，用户「大概率看不到加载中」；而且 `hideProgress()` 会 `Traffic.reset()` 重置计数基线，第二跳（+1000ms）本来才是能算出速度的那一跳，圈既然已经收了，网速永远没机会渲染。

次要缺口：`onStateChanged(STATE_READY)` 无条件 `showPlaybackContent()`；`onControllerReadyReconciled()` 在 mobile 已有覆写且无条件收圈（leanback 此前没有该覆写，`PlaybackActivity` 里是空实现，即完全不会补发收口）。引擎在 seek 真正生效前的那一次 READY 读数同样会收圈。

## 方案比较

| 方案 | 决策 | 理由 |
| --- | --- | --- |
| 不变更 | 拒绝 | 无法满足「拖拽后要看到加载中/网速」。 |
| 照搬上游（纯 BUFFERING 驱动，删掉本地 seek 兜底） | 拒绝 | 上游没有这段代码可摘；且会回退 `4e303d30d34` 已交付的「加载圈兜底收口」，重新留出「转圈不消失」缺口。 |
| 关掉网速 ticker 上的兜底收口 | 拒绝 | 兜底收口本身是修「圈永久残留」的，去掉会回归旧缺陷。 |
| **seek 期间开一个「最小可见 + 确实可收」窗口**（采用） | 采用 | 只在 seek 这一次交互上加闸门，不动其余路径；窗口由显式收圈关闭，无新增循环。 |

## 实现

仅两个 flavor 的 `VideoActivity`（无新生产类，无 native/ABI/依赖改动）：

- 新增 `SEEK_PROGRESS_MIN_VISIBLE_MS = 1200L`、`mSeekProgressPending`、`mSeekProgressStartedAtMs`。
- `onSeekStarted()`：**先**开窗口（`pending = true` + 记时），**再** `showProgress()`，最后挂 `SEEK_PROGRESS_MIN_VISIBLE_MS` 计时器。顺序是关键——`showProgress()` 会立刻投递 ticker。
- 新增 `canHideSeekProgress()`：窗口未开即放行；窗口开着时要求「已过最小可见时长」且「播放器有效、READY、且不在真实加载阻塞中（`!isLoading() || isPlaying()`）」。
- `hidePlaybackProgressIfStale()`（网速 ticker 的兜底收口）：加同一道闸门。这是症状的直接执行者。
- `hideSeekProgressIfReady()`（最小可见计时器）：窗口未关时不得收圈；通过闸门才关窗口并收圈。
- `onStateChanged(STATE_READY)` 与 `onControllerReadyReconciled()`：改走同一道闸门，消除「旧 READY 读数收圈」。其中 mobile 是既有覆写改走闸门；**leanback 此前没有该覆写**（基类为空实现），本次为补齐与 mobile 一致的「控制器晚绑定补发收口」而**新增**该覆写，同样受闸门约束（`if (canHideSeekProgress()) showPlaybackContent();`）。
- `hideProgress()`：任何显式收圈同时关闭窗口，保证窗口不比圈活得久（音频舞台路径也借此自动收口）。
- `showProgress()`：窗口开着时不摘掉最小可见计时器（BUFFERING 分支那次 `showProgress` 属于 seek 自身）。

### 为什么最小可见时长取 1200ms 而不是 500ms

网速读数由 `PlaybackSpeedMeter` 相邻两次采样的字节差算出，而 `setTraffic` 每秒一跳（`App.post(mR2/mR3, 1000)`），且 `hideProgress()` 每次都 `Traffic.reset()` 重置基线——所以**首跳必然空白**，只有跨过一跳之后才有读数。若窗口沿用旧实现的 500ms，圈虽能停留，网速仍会来不及渲染，只修到用户诉求的一半。取 1200ms 留 200ms 余量，避免收圈与首跳采样同帧竞争。

代价（有意接受并锁定）：缓冲内的瞬时 seek 也会多显示约 0.7s 的圈。方向上偏向「显示」而非「不显示」，与本次诉求一致；代价为纯粹观感冗余，不涉及功能或数据。

### 不做重试循环

曾写过一个 200ms 重试以更快收口，随后移除：网速 ticker 在圈可见期间本就每秒重判一次，已足够收尾；自发重试会在「播放器长期报 loading」时变成一个每秒 5 次的空转唤醒，属于净负担。故窗口收尾只有两条路径：1200ms 计时器 + 每秒 ticker。

## 验证

- **编译**：`:app:compileMobileArm64_v8aDebugJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 均 BUILD SUCCESSFUL。
- **定向回归**（真实执行，`skipped=0`）：
  - `C47SeekLoadingProgressSourceTest`：5 用例 × mobile/leanback = 10，0 failure 0 error。
  - `VideoActivityLayoutTest`（mobile）：154 用例，0 failure（含本次按新契约改写的 `assertSeekProgressFallback`）。
  - `PlaybackOwnershipSourceTest`：15 用例 × 2 flavor = 30，0 failure（归属与收圈闸门断言未回退）。
  - 合计 **194 次用例执行，0 failure 0 error 0 skipped**。
- **变异验证（两处，均精确命中）**：
  1. 删掉 `hidePlaybackProgressIfStale()` 里的 `if (!canHideSeekProgress()) return;`（重现原始缺陷）→ `theMinimumVisibleGateIsTheOnlyWayToCloseTheSeekWindow` 变红（断言点「网速 ticker 不得收掉挂起的 seek」）。
  2. 把 `SEEK_PROGRESS_MIN_VISIBLE_MS` 从 1200 改回 500 → 同测试在「最小可见时长必须盖过一个网速采样间隔」断言变红。
  - 两处变异均在断言后按 md5 校验恢复为变异前内容（`e21ac2279f16fba59deed4be73053ebc` / `5a3134b05ab83ab4400bc95b4899aaf8`）。
- **接口面核查**：全仓测试源集内无其它用例钉住旧的 500ms/`App.post(mSeekProgressFallback, 500)` 形状；`TmdbDetailActivity` 内嵌播放器不走 seek 专属亮圈（`onSeekStarted` 未覆写、加载态纯由 `isLoading()` 驱动），不存在同款「先亮后收」竞态，故未纳入本次范围。
- **实机（模拟器）验收**：设备 `192.168.50.3:5561`（= `emulator-5560`，HD1910，x86_64 + ARM 转译），mobile/arm64-v8a Debug 覆盖安装（签名一致，`-r`，未卸载）。
  - 环境阻塞与排除：安装后 App 启动即崩 `Room IllegalStateException: Migration didn't properly handle: History`（`HomeActivity.initView`）。经只读排查为**设备预存状态**：`PRAGMA user_version=37` 但 `History` 仅 17 列，缺 `MIGRATION_36_37` 应加的 5 列（`typeName/area/actor/director/year`，Room 期望 31 列），且 `tv` 文件时间戳早于本次安装。C47 提交不触碰 `db/`（仅 2 个 VideoActivity + 测试 + 文档），故与本次改动无关。为完成验收把 `databases/tv` 暂时移开让 Room 重建，**验收后已完整还原**。
  - 关键判据纠正：`dumpsys activity top` 报的 `V`/`G` 是视图自身标志，**对 overlay 容器不可靠**（根 `app:id/progress` 为 `G` 时其子 `app:id/traffic` 仍可能显示 `V`）。必须用**根节点** `app:id/progress` 判定覆盖层可见性。
  - 可复现路径辨析：`onSeekStarted()` 有两个调用点——① 进度条 `CustomSeekView` 的 `onScrubStop`；② `PlaybackActivity.seekTo(long deltaMs)`（`PlaybackActivity.java:477`），后者是相对 seek 入口。两个 flavor 的手势/按键横滑在抬手时都走 `onSeekEnd(time)` → `seekTo(time)`（leanback `VideoActivity:8295`、mobile `VideoActivity:9613`），因此**横滑路径同样会开窗**（拖动期间 `onSeeking()` 先 `hideProgress()` 关窗，抬手后又由 `seekTo → onSeekStarted()` 开窗并按最小可见 1200ms 收尾）。用户所述「拖拽进度」对应**进度条**路径，验收须用 `app:id/timeBar`（`uiautomator` 实测绝对坐标 `[178,597][794,675]`），不能用横滑代替；横滑路径的行为变化属上述「最小可见 1200ms」的既有代价。
  - 进度条拖拽实测（设备端紧循环采样 `app:id/progress`，单次 ~30–160ms）：基线 `G`（隐藏）→ 抬手瞬间 `V`（出现）→ 持续约 1.0–2.0s 后回到 `G`；越过缓冲区拖拽时可见窗口更长（实测 s19–s22 + t1、t2 共约 2s 后干净收口）。与「最小可见 1.2s + 闸门」的预期一致，**未复现「抬手即被收圈」**。
  - 采样精度边界（如实记录）：该实例 `loadavg≈9–12`（x86_64 + ARM 转译 + 软件渲染），`uiautomator dump` 需 ~3.9s、`dumpsys` 读取后取时戳会滞后 150–800ms，因此**无法逐帧分辨 1.2s 窗口的起止**，也无法稳定读到覆盖层内的网速文本（`traffic` 文本在 dump 中不落 `mText`）。故本次**不宣称**「网速观感已逐帧确认」，只宣称：进度条拖拽会立即亮圈并保持到可收口为止，且收口后不残留。
  - 设备已还原：原 APK 重新覆盖安装（sha256 `315e7d03…` 与安装前一致）、`databases/tv` 还原为 `user_version=37` / 17 列、移除全部临时文件；未打正式包（遵守 webhtv 约定）。
- **仍未覆盖**：`screenrecord`/`screencap` 逐帧观感（需宿主 ffmpeg 抽帧，本机无 ffmpeg 且模拟器负载使 `screencap` 单次达 3.2–4.2s）；leanback 侧的实机验收（该设备装的是 mobile flavor）。

## 回滚

按本任务 `recovery/C47/<timestamp>-<commit>` 标签整体回滚该原子提交即可；改动只涉及两个 flavor 的 `VideoActivity` 与测试，不触碰 native、AAR、依赖或构建配置。

## 交付

- 提交与 tag 见本文件末尾由 `task_guard.sh finish` 生成；未推送。
