# AUTONEXT-02：自动连播「有声音没画面」

## Recovery anchor

- 目标：修好「自动连播/下一集后有声无画、重开 App 才恢复」。
- 证据：用户设备日志 `webhtv-debug-log (27).txt`（2026-10-21 21:50，`MAA-AN10` / Android 16 / `5.6.0(560)` mobile）。
- 状态：窄修复已实现，定向单测与双 flavor 编译已通过；2026-10-22 在 C96（`docs/C96-beta-merge-review-dev1-20261022.md`）完成独立评审，修复 4 项（诊断白名单、补绑运行面、投机 API、实例身份口径）后复评通过；未做设备端连播复现（见「验证边界」）。
- 回滚：撤销本单元提交；未改依赖、lock、native、布局与播放策略。

## 1. 日志读出的因果链

时间轴（`logSeq` 为日志内序号）：

```text
21:50:10.722 audio-auto-next: service ended owner=false navigation=true action=browse-next
21:50:11.441 config.change trace=p-g6erb2-d reason=request
21:50:11.541 video.flush-reuse-release trace=p-g6cnlc-b operation=flush     ← 旧媒体在解码器里被 flush
21:50:12.421 video.first-output trace=p-g6cnlc-b epoch=3 first-input-queued ← 输入一直走 p-g6cnlc-b 的适配器
21:50:12.461 video.configure trace=p-g6erb2-d reuseResult=3
21:50:12.508 player: state=READY trace=p-g6erb2-d
21:50:16.772 video.output.summary trace=p-g6erb2-d submittedBuffers=0 skippedBuffers=106
21:50:21.818 video.output.summary trace=p-g6erb2-d submittedBuffers=0 skippedBuffers=232
21:50:22.112 playback-lifecycle: service startCommand action=com.silent.android.webhtv.next
21:50:22.651 clear → 21:50:22.719 reprepare → 21:50:23.784 READY（trace=p-g6ezzn-e）
21:50:26.857 video.output.summary trace=p-g6ezzn-e submittedBuffers=0 skippedBuffers=75
21:50:27.858 又一次上报 → 用户在这一刻前后重开 App
```

三条硬事实：

1. 每次换集，`player-engine` 都用**同一个** `playerInstanceId=player-2` 重新 `prepare`，没有重建引擎实例。
2. 新媒体的视频帧确实被解码出来了，但**一帧都没提交**：`submittedBuffers=0`、`skippedBuffers` 每个采样周期都在涨（106 → 232 → 254 → 75）。
3. 整份日志里 `audio-auto-next` 反复打出 `owner=false`，即使服务自己用记录键起播（`|3`）时也是 `false`。

`skippedBuffers` 持续增长 + `submittedBuffers=0`，说明渲染器判定「无处可画」：帧到点就被跳过。
这与 `SurfaceDiagnosticCollector` 记录的 `surfaceId=none` 一致 —— 解码器从未绑上有效输出面。

## 2. 根因

`owner=false` 是主因，而它来自 key 形态不一致：

| 侧 | key 形态 | 生产者 |
| --- | --- | --- |
| 页面 | `<siteKey>@@@<vodId>@@@<cid>`（如 `php_蓝莓红果ᴸᴹ@@@7694249839906786328@@@3`） | `VideoActivity#getHistoryKey()` → `startPlayer(key,…)` → `PlaySpec` |
| 服务「浏览下一集」 | `VE:<historyKey>\|<线路名>\|<集序号>` | `PlaybackService#navigateItem` → `VodBrowse#navigateEpisode` → `startBrowse` → `PlaySpec` |

两者指向**同一次播放会话**，但字符串不相等。旧判定是纯 `String.equals`：

- `PlaybackActivity#isOwner()`（`key == null || key.equals(manager.getKey())`）判假 → 重建/绑定相关回调整体失效；
- `PlaybackService#isNavigationOwner()`（`navigationKey.equals(player.getKey())`）判假 → 下一次 `STATE_ENDED` 被当成「无主」，服务再 `navigateItem(1)` 跳一集，连播变连跳。

`isOwner()` 判假后，`PlaybackActivity` 里**所有**受它把关的路径一起失效，其中最致命的是：

- `onPlayerRebuild()`：`setRender()` 被跳过（`detachSurface() + attachSurface()` 不再执行）；
- `attachSurface()` 里 `if (render != targetRender)` 的重绑分支也被跳过（`render` 值没变）；
- 唯一还以引擎实例身份判断的 `currentPlayer != nextPlayer` 分支，也因为**引擎实例被复用**而永不成立。

三者叠加 → `PlayerView` 上挂着的引擎从未与新 Surface 对齐，视频帧全部 skip。音频走独立渲染器，因此不受影响：**有声、全黑**。重开 App 会重建整条页面与绑定链（新 `PlayerView` + 新引擎），所以「重开就正常」。

## 3. 修复

三处，都是窄口径：

1. **单点判定收敛**：新增 `com.fongmi.android.tv.player.PlaybackOwnershipKeys`，把「两个 key 是否同属一次播放会话」做成纯字符串函数（生产面只有 `sessionKeyOf` / `sameSession`）。选集键 `VE:<historyKey>|…|…` 从右往左裁掉「集序号」「线路名」两段得到记录键；结构不完整时**退回原字符串**，绝不因为解析失败把两个不同会话判成同一会话。
2. **两处归属判定改为同会话比较**：`PlaybackActivity#isOwner()` 与 `PlaybackService#isNavigationOwner()` 都改走 `sameSession(...)`。
3. **绑定判定可测化 + 兜底**：新增 `VideoSurfaceBinding.resolve(current, next)` 取代 `attachSurface()` 里的 `currentPlayer != nextPlayer` 内联判断（按**实例身份**比较，不用 `equals`）；并在 `onPlaybackStateChanged` 的 `BUFFERING/READY` 上调一次 `ensureVideoSurfaceBound(...)`。兜底只在页面 `RESUMED` 且 `PlayerManager` 未失效时执行，引擎未就绪直接返回——它只绑不卸，也绝不接入 render 切换或 ASS/字幕会话，避免后台旧页面抢走共享引擎。
4. **诊断证据**：`SurfaceDiagnosticCollector.rebind(...)` 新增 `surface.rebind` 事件（`reason` / `detachedFirst` / `surfaceViewPresent` / `attached` / `shown` / `width` / `height` / `surfaceId` / `valid`），把「没触发重绑」与「触发了但 Surface 不可用」在下次现场区分开。事件名与字段名已登记进 catvod `DiagnosticEvent` 白名单（未登记会被 `emit` 吞成 `collectorFailure`，等于没有证据），并由 `SurfaceRebindDiagnosticTest` 锁住。

## 4. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 定向单测 | `:app:testMobileArm64_v8aDebugUnitTest --tests PlaybackOwnershipKeysTest --tests SurfaceRebindDiagnosticTest --tests VideoSurfaceBindingTest --tests PlaybackOwnershipSourceTest` | **38 例通过**（10 / 2 / 6 / 20，0 失败 0 错误 0 跳过） |
| 双 flavor 编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac` | `BUILD SUCCESSFUL in 1m 8s` |

`PlaybackOwnershipKeysTest` 覆盖 key 形态等价/不等价矩阵（同会话、跨站点、跨 VOD、裸前缀不得吞噬、缺一侧、结构不完整退回原串、记录键含 `|`）；`SurfaceRebindDiagnosticTest` 锁住诊断白名单（正向可写入 + 反向确会拒绝未登记名字）；`VideoSurfaceBindingTest` 覆盖引擎相同/被替换/视图为空/会话清空四个分支；`PlaybackOwnershipSourceTest` 增加「服务侧归属比较」「选集键前缀跨包同值」「补绑只在 RESUMED 且绝不解绑」三条源码契约。

### 验证边界

- 未做设备端「自动连播 → 出画」端到端复现：该场景要求服务侧 ENDED → `browse-next` 分支真的被走到（日志里它被 `navigation=true` 分流），无法在无 UI 自动化条件下稳定构造。
- 本轮修复的可证伪性由 `PlaybackOwnershipKeysTest`（key 形态等价/不等价矩阵）与 `VideoSurfaceBindingTest`（引擎实例相同/被替换/视图为空/会话被清空）承担；源码级断言 `kernelRebuildBindsTheNewEngineToThePlayerViewByInstanceIdentity` 锁定 `attachSurface()` 仍按引擎实例身份执行重绑。

## 5. 风险与回滚

- `sameSession` 在结构不完整的选集键上退回字符串比较，行为与修复前一致，不会放宽归属；`VE:` 解析从右裁两段，线路名本身含 `|` 时记录键会多并入一段，属已知限制（有测试固化）。
- `ensureVideoSurfaceBound` 只在 `BUFFERING/READY` 且页面 `RESUMED` 时运行，`PlayerManager` 失效或引擎未就绪直接返回，绑定已正确即返回；它只做绑定、不重设 render、不接 ASS/字幕会话、绝不解绑，不改变正常播放路径。
- 诊断事件名/字段名是 catvod 白名单契约，后续改名必须同步 `DiagnosticEvent`，否则 `emit` 会静默记成 `collectorFailure`；`SurfaceRebindDiagnosticTest` 负责在构建期发现。
- 回滚：撤销本单元提交，恢复三处 `equals` 与内联实例判断即可。
