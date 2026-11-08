# AUTONEXT-01：自动下一集「取址失败 / 有声音无画面」根因分析与修复

## Recovery anchor

- 目标：解释并修复「一集播放完毕自动跳下一集时，出现取播放地址失败，或取址成功但当次全黑无画面，必须手动切换播放核心或软/硬解才恢复」。
- 验收：定位到唯一根因并给出可证伪证据；修复后切换播放核心/软硬解不再是恢复画面的必要条件；解释两台设备上两种手动恢复方式为何都能奏效；说明「取址失败」是否同一根因。
- 状态：**根因已定案（有设备日志证据），窄修复已实现，单测已通过且被证伪；双 flavor 编译、mobile debug 包构建与覆盖安装、启动渲染均已验证。未做设备端自动连播端到端复现（见「验证边界」）。**
- 基线：`dev1` / `24265785a33d09ca1d1d3d36370d010ff1e89eac`，起始工作区干净，无受保护脏文件。
- 本轮 lane：`quick-fix`；task guard：`autonext-black-screen`。
- 允许路径：`app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java`、`app/src/test/java/com/fongmi/android/tv/ui/activity/PlaybackOwnershipSourceTest.java`、本文件。
- 回滚锚点：撤销本单元提交即可；未改依赖、lock、native、布局、播放策略。

## 1. 结论

**根因：自动下一集换集时，`PlayerManager` 会先 `clear()` 把 `spec` 置空，随后 `preparePlayer()` 重建引擎并回调 `onPlayerRebuild()`；此时 `PlaybackActivity.isOwner()` 为 `false`，整个重建回调被跳过，`render` 值不变。紧接着 `attachSurface()` 只用「`PlayerView.getPlayer()` 是否为空」判断是否需要绑定，而 `PlayerView` 里仍挂着**上一集已 `release()` 的旧引擎**（非空），于是新引擎从未拿到 Surface。**

- 解码器已初始化、`STATE_READY` 已到达、音频正常推进，但没有任何视频帧输出 → **有声音、全黑没画面**。
- 手动「切换播放核心」或「切换软/硬解」都会重建引擎并走 `onPlayerRebuild()`；那时 `spec` 已非空、`isOwner()` 为真，`setRender()` 触发 `detachSurface() + attachSurface()`，新引擎被重新绑定 → **画面立刻恢复**。这正是用户观察到的唯一自救方式。

「取播放地址失败」是**另一件事**：那是源站/spider 取址请求本身失败导致的正常报错路径（见 §5），不是本根因造成的，本次不改变其行为。

## 2. 复现证据（用户设备原始日志，修复前）

证据来源：设备内 App 调试日志 `webhtv-debug-log-2.txt`（设备 `/sdcard/Download/`，本机留档 `/tmp/htvlogs/webhtv-debug-log-2.txt`），时间 `2026-09-12 17:17–17:18`，包 `com.silent.android.webhtv`（mobile）。

### 2.1 换集边界

```text
17:18:08.618 [main] player: state=ENDED spec=trace=p-hl2ip-4 ...
17:18:08.657 [main] audio-auto-next: service ended owner=true navigation=true
                       key=jianpian_app@@@575320@@@4 navigationKey=jianpian_app@@@575320@@@4 action=defer-to-owner
17:18:08.752 [main] short-drama-queue: invalidate reason=manual-episode
17:18:08.781 [main] short-drama-queue: invalidate reason=refresh        ← onRefresh()
17:18:09.008 [main] player: state=IDLE spec=trace=p-hl2ip-4 ...
```

### 2.2 引擎被重建，但属于「无归属」窗口

```text
17:18:09.181 [main] player: prepare player type=0 decode=1     ← preparePlayer() 真的重建了引擎
17:18:09.692 [main] playback-lifecycle: state changed state=1 ... playerKey=null owner=false ...
```

`playerKey=null` 说明此刻 `PlayerManager.spec == null`（`getKey()` 由 `spec` 派生），`owner=false` 说明 `isOwner()` 已经为假 —— 重建回调被挡在门外。

### 2.3 取址成功，但 Surface 没有绑到新引擎

```text
17:18:09.732 [main] video-flow: player finish cost=684ms useParse=false multi=false msg=
17:18:09.742 [main] video-flow: startPlayer dispatch initialPosition=109497 music=false ijk=false
17:18:09.743 [main] surface-size: playback attach start target=0 ... render=0 target=0 ...
17:18:09.748 [main] surface-size: playback attach done  ... render=0 target=0 ...
                                          ↑ 没有 “attach after setPlayer”，新引擎从未绑定
17:18:09.758 [main] playback-stage: trace=p-hle1b-6 stage=request elapsed=2ms reason=start
17:18:09.777 [main] playback-stage: trace=p-hle1b-6 stage=prepare elapsed=23ms
```

关键判据：`attach start` 打印的 `render=0 target=0` 相等，说明 `render != targetRender` 分支（唯一会调用 `setPlayer(null)` 的分支）被跳过；随后 `getExoView().getPlayer() == null` 也不成立，所以**整段绑定被跳过**。同一份日志里其它每一次真正完成绑定的时刻都会打出 `attach after setPlayer`（如 `17:18:12.029`、`17:18:13.879`），本次没有。

### 2.4 解码器起来了、状态到 READY，但没有视频帧

```text
17:18:10.845 [main] playback-stage: trace=p-hle1b-6 stage=tracks elapsed=1091ms video=true audio=true
17:18:10.915 [main] playback-metrics: trace=p-hle1b-6 video decoder=OMX.qcom.video.decoder.avc init=293ms
17:18:11.824 [main] player: state=READY spec=trace=p-hle1b-6 ...
17:18:11.829 [main] playback-stage: trace=p-hle1b-6 stage=ready elapsed=2076ms
                  ↑ READY 已到，但到此刻仍没有 stage=first-frame
```

### 2.5 一次「偶然的」引擎重建把画面救了回来

```text
17:18:11.858 [main] ad-audio: pipeline rebuild requested exo=true attempt=1
17:18:11.953 [main] player-engine: trace=p-hle1b-6 rebuild decode=1
17:18:12.013 [main] surface-size: playback attach start ... render=-1 target=0     ← setRender() 已把 render 置 -1
17:18:12.013 [main] playback-flow: switch render from=-1 to=0
17:18:12.029 [main] surface-size: playback attach after setPlayer                   ← 这次终于绑定了
17:18:12.793 [main] playback-metrics: trace=p-hle1b-6 video decoder=... init=130ms
17:18:13.462 [exo] exo-seek: trace=p-hle1b-6 phase=video-frame-first seq=11 frame=1
17:18:13.581 [main] exo-seek: trace=p-hle1b-6 phase=first-frame seq=11 elapsed=1415
17:18:13.584 [main] playback-stage: trace=p-hle1b-6 stage=first-frame elapsed=3828ms source=media3
```

重新绑定之前没有任何视频帧；重新绑定之后立刻出现首帧。这条时序把「绑定缺失 → 无画面」与「重新绑定 → 出画面」直接连了起来。

> 重要：救回画面的这次重建来自「音频指纹去广告」的 `rebuildAudioPipeline()`。该功能已在 `56ebd07f1`（已并入当前 `dev1` tip）整体删除，`ad-audio` 触发源在现行代码中已不存在。**这解释了为什么用户是「现在」才开始经常遇到：过去存在一个偶然的重建把缺陷掩盖住了，删除该功能后掩盖消失。**

## 3. 代码级根因链（当前基线行号）

| 步 | 位置 | 行为 |
| --- | --- | --- |
| 1 | `app/src/mobile/.../VideoActivity.java:5029` `onRefresh()` | 换集入口先 `player().stop()`，再 `player().clear()` |
| 2 | `PlayerManager.java:1755/1763` `clear()` | `spec = null` |
| 3 | `PlayerManager.java:578` `getKey()` | `spec == null` → 返回 `null` |
| 4 | `PlaybackActivity.java:219` `isOwner()` | `key.equals(null)` 为假 → **`isOwner()` = false** |
| 5 | `VideoActivity.java:2709` → `2216` | `applyHistoryPlayerKernel()` 调 `preparePlayer(kernel, false)` |
| 6 | `PlayerManager.java:1940/1960` | 内核与当前引擎不一致时 `engine.release()` + 重建，并 `callback.onPlayerRebuild(...)` |
| 7 | `PlaybackActivity.java:1009` `onPlayerRebuild()` | `if (isOwner())` 为假 → **`setRender()`（1014）整段跳过**，`render` 保持原值 |
| 8 | `PlaybackActivity.java:648` `attachSurface()` | `render == targetRender` → 跳过重绑分支 |
| 9 | 修复前该处判断 | 只看 `getExoView().getPlayer() == null`；`PlayerView` 仍握着**已被 `release()` 的旧引擎**（非空）→ 跳过绑定 |
| 10 | 结果 | 新引擎无 Surface：解码器可初始化、能到 READY、音频可播，但**零视频帧** |

第 6 步的触发条件（用户问的「为什么偶现」）：`preparePlayer(kernel, false)` 仅在 **当前会话引擎类型 ≠ 本剧记住的内核** 时重建。播放失败后的自动回退 `switchEngine(type, chosen=false, ...)` 会改 `playerType` 但**不改** `PlayerSetting.putActivePlayer()`；于是「上一集曾被自动回退救回」的会话，在换下一集时必然发生内核回切 → 必然进入第 6–10 步。反之上一集全程未回退时两者一致，`preparePlayer` 直接早退、不重建，也就不会黑屏 —— 与用户「可能/偶现」的描述一致。

## 4. 为什么两种手动方式都能恢复

| 用户操作 | 代码路径 | 为何奏效 |
| --- | --- | --- |
| 切换播放核心 | `refreshAndSwitchPlayerKernel` → `PlayerManager.switchPlayer(...)` → `callback.onPlayerRebuild(...)` | 此时 `spec` 已非空 → `isOwner()` 为真 → `setRender()` 执行 `detachSurface() + attachSurface()` → 新引擎重新绑定 |
| 切换软/硬解 | `refreshAndSwitchDecode` → `PlayerManager.switchDecode(...)` → `callback.onPlayerRebuild(...)` | 同上；且 `resetVideoSurfaceForDecoderSwitch()` 还会临时切换 render 强制重绑 |

两者都不是「换解码器/换核心本身修好了播放」，而是**顺带执行了缺失的 Surface 重绑**。

## 5. 「获取播放地址失败」的定性

同目录另有一份日志（`webhtv-debug-log-1.txt`）显示取址类请求超时后 `search` 返回 `error: ... timeout`。取址失败走的是既有报错链路：`startPlayer()` 发现 `result.getRealUrl().isEmpty()` → `onError(error_play_url)`；手机端 `onError()` 随后 `applyHistoryPlayerKernel(true)`（`force=true`，强制重建）并 `startFlow()` 尝试自动换源。

- 这条路径**不是**本次黑屏根因，本次不改其行为。
- 公开结论：取址失败 = 源站/spider 侧失败（其恢复手段是自动换源或提示），黑屏 = App 侧 Surface 绑定缺失。两者症状不同、根因不同，不应合并处理。

## 6. 修复

单点、单根因：`PlaybackActivity.attachSurface(boolean)` 不再以「View 是否为空」判断，改为**按引擎实例身份**判断并重绑。

```java
Player currentPlayer = getExoView().getPlayer();
Player nextPlayer = player().getPlayer();
if (currentPlayer != nextPlayer) {
    if (currentPlayer != null) getExoView().setPlayer(null);
    getExoView().setPlayer(nextPlayer);
    logSurfaceState("attach after setPlayer");
    syncVideoSurfaceSize(null);
    if (restoreExoShutter) syncShutter();
    else hideVideoShutter();
    if (player().isNativePlayer()) getExoView().post(this::syncShutter);
}
```

- 同引擎（未重建）→ 两者相同 → 跳过，行为与修复前完全一致（正常换集不受影响）。
- 引擎已被替换（含 `engine`/`player` 被 `release()` 置空的情形）→ 强制 `setPlayer(null)` 再 `setPlayer(next)`；`PlayerView.setPlayer()` 自身会释放旧引擎的 Surface 绑定，显式先解绑可确保顺序确定。
- `getRender()`、`surfaceDiagnostics.bind(...)` 早已在同一方法内无条件解引用 `player()`，因此本次新增的 `player().getPlayer()` 不引入新的空指针面。

## 7. 验证

| 项 | 命令 / 依据 | 结果 |
| --- | --- | --- |
| 定向单测 | `:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.PlaybackOwnershipSourceTest` | **16/16 通过**，含新增 `kernelRebuildBindsTheNewEngineToThePlayerViewByInstanceIdentity` |
| 单测可证伪 | 暂存（revert）生产改动后重跑同一用例 | **FAILED**（`EXIT=1`），证明断言确实锁定本次修复而非恒真 |
| 双 flavor 编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac` + `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | `BUILD SUCCESSFUL`（1m59s） |
| 测试包构建+覆盖安装 | `bash scripts/build_arm64_debug_install.sh --flavor mobile --serial 192.168.50.3:5555` | `BUILD SUCCESSFUL`（1m32s），`install -r` → `Success`（未卸载，保留用户数据） |
| 启动渲染 | `am force-stop` + `monkey LAUNCHER` + `dumpsys` + `screencap` | 进程存活（PID 7239），`HomeActivityCurrent` resumed；随后进入历史页与 `TmdbDetailActivity` 均正常渲染（`shot1.png`）；`logcat -b crash` 为空、全量 logcat `FATAL EXCEPTION` 计数 **0** |

日志与产物：`build/autonext-verify/gradle.log`、`build/autonext-verify/falsify.log`、`build/autonext-verify/build-install.log`、`build/autonext-verify/shot1.png`。

### 验证边界（未完成项，如实记录）

- **未做设备端「自动连播 → 画面正常」的端到端复现。** 该场景要求「上一集曾发生内核级自动回退、下一集再回切内核」，无法在无 UI 自动化条件下稳定构造；`VideoActivity` 未导出（`adb am start` 被拒），历史条目入口最终进入 `TmdbDetailActivity` 详情页，长剧集单集 30 分钟以上也无法在预算内等到自然播完。
- 因此本单元的缺陷证据是**修复前的用户设备日志**（§2，判据明确且唯一），修复有效性由**被证伪的定向单测**+**双 flavor 编译**+**构建/安装/启动冒烟**支撑，而不是设备端黑屏复现。
- 建议的后续设备复核方式：在「首集取址失败、自动回退到别的内核，第二集正常」的资源上覆盖安装本测试包，观察换集边界不再需要手动切核心即可出画；或直接看 `MPV_SIZE` 日志中换集边界是否出现 `attach after setPlayer`。

## 8. 风险与回滚

- 风险：`attachSurface()` 在「引擎实例已不同」时新增一次 `setPlayer(null)`。该调用只发生在确实需要重绑的时刻（引擎已重建/已释放），不会形成解绑-绑定循环（绑定后 `PlayerView.player` 即等于当前引擎，再次进入必然跳过）。
- 已知但**未处理**（AGENTS.md §2：只报告不顺手改）：换集重建窗口内 `onPlayerRebuild()` 被跳过，除 Surface 外还会跳过 `getSeekView().setProgressPlayer(...)` 与 `applyResizeMode(...)`。Leanback 的 `applyHistoryPlayerKernel()` 不像 mobile 那样补 `setProgressPlayer`，理论上该窗口后进度条可能绑定到旧引擎；这与本次「黑屏」无关，未在本单元扩大范围处理。
- 回滚：撤销本单元提交。未触碰依赖、lock、native 二进制、布局与播放策略，回滚无副作用。

## 9. 唯一下一动作

`task_guard.sh finish` 提交本单元并生成本地恢复标签；不推送、不建 PR（用户未授权）。
