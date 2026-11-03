# AV-PROGRESS-04 · 电视端「设置内核 ≠ 历史记录内核」时进度条恒 00:00

## 1. 现象与来源

- 第二轮反馈（AV-PROGRESS-02 修复后的复测）：「新版进度条问题还存在，设置里切换播放内核，打开历史播放不切换内核然后进度条就没了」，补充「只有设置和历史记录播放的内核一致才显示进度，不一致的话进度条 0000」。
- 截图：电视端播放页，弹幕开启、FHD/AVC/AAC/2.0/25FPS/2.8MBPS/MP4 徽标正常，但进度条左右均为 `00:00`，视频本身正常播放。
- AV-PROGRESS-01（`fec02b27def`）补的是「引擎未上报时长时用历史 duration 兜底」；AV-PROGRESS-02（`6e9a2b9de7`）补的是 leanback `VideoActivity` 两条自持的内核重建路径。本任务是同一症候在**第三条、尚未覆盖的重建路径**上的残留。

## 2. 入口定位（为什么前两次修复没生效）

电视端「打开历史播放」并不走 `leanback/VideoActivity`：

```
leanback/HistoryActivity.java:148（HomeActivity.java:1320 同）
  → HistoryResumeCoordinator.open
  → main/HistoryResumeCoordinator.java:44-45
  → leanback/VideoActivity.java:823-825 VideoActivity.startFromHistory
  → main/TmdbDetailActivity.java:584 TmdbDetailActivity.startFromHistory(activity, item)
```

即落到 `TmdbDetailActivity` 的内联播放（`startInlinePlayer`）。手机端 `mobile/VideoActivity.java:774-776` 走的也是同一条路径，所以补丁打在 `main` 源集同时覆盖手机与电视。

## 3. 根因链路

1. `TmdbDetailActivity.startInlinePlayer(Result, long)`（`app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java:7882`）里先 `player().stop(); player().clear();`（:7932-7933）——`PlayerManager.clear()`（`PlayerManager.java:1853-1861`）把 `spec` 置为 null。
2. 随后 `player().preparePlayer(inlineHistoryPlayerKernel());`（:7937）。`inlineHistoryPlayerKernel()`（:11698-11700）= `history.getPlayerOrDefault()`，也就是**历史记录记住的内核**；与设置里当前活动内核不一致时，`PlayerManager.preparePlayer(int, boolean)`（`PlayerManager.java:2097-2127`）在 :2100 不做早退，走到 :2114-2120 释放旧引擎并新建引擎。
3. 重建后 :2124 立刻回调 `callback.onPlayerRebuild(player, force)`，而 :2125 才清 `spec`。此时 `PlayerManager.getKey()` 返回 null（:587-589），`PlaybackActivity.isOwner()`（`PlaybackActivity.java:258-262`）判假。
4. `PlaybackActivity.onPlayerRebuild(...)`（:1148-1155）整段包在 `if (isOwner())` 内，`getSeekView().setProgressPlayer(player)` 被一起跳过。
5. 回到 `startInlinePlayer`：`startPlayer(getHistoryKey(), ...)`（:7943）此后才建立新的 PlaySpec，但已没有任何代码把 seek view 重新绑到新引擎。`CustomSeekView.progressPlayer` 仍指向已被 release 的旧 Player 实例（非 null，因此 `getProgressPlayer()` 不会退回 controller 通道），1 秒轮询读到 duration=0、position=0 ⇒ 左右恒 `00:00`。
6. 内核**一致**时 `PlayerManager.preparePlayer` 在 :2100 直接 return、不重建，回调不发生，进度条照常 ⇒ 与用户「只有内核一致才显示进度」的实测完全吻合。

反向核对：播放器内手动换内核不受影响——`PlayerManager.java:2177` 的 `callback.onPlayerRebuild` 发生在 spec 被改写之前（旧 spec 仍在、同会话），`isOwner()` 为真，重绑正常执行。

## 4. 修复

`app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`，`startInlinePlayer` 内 `preparePlayer(inlineHistoryPlayerKernel())` 之后、`setInlineSpeed(...)` 之前，显式重绑一次：

```java
player().preparePlayer(inlineHistoryPlayerKernel());
// 内核不一致时 preparePlayer() 会在 PlaySpec 建立之前就重建引擎，那一刻 getKey() 还是
// null、归属判定必为假，PlaybackActivity.onPlayerRebuild() 会整段跳过——其中就包含
// setProgressPlayer()。进度视图于是继续读已被 release 的旧引擎，读到时长 0、位置 0，
// 进度条恒为 00:00。这里在重建刚发生后显式跟上新引擎，对齐 leanback VideoActivity 的同一处理。
getSeekView().setProgressPlayer(player().getPlayer());
```

选择在重建后立即重绑而不是等 `onPlayerRebuilt()`：重建发生在 PlaySpec 之前，那一刻归属判定必然为假，等回调等于永远不等。重绑只替换 Player 引用，`CustomSeekView` 的兜底（AV-PROGRESS-01）与轮询节奏不变。

延迟/恢复起播分支自动覆盖，无需重复增强：`startPendingInlinePlayer()`（:11248-11251）与 `restoreInlinePlaybackIfNeeded()`（:11253-11259）都最终汇聚到 `startInlinePlayer(...)`。

## 5. 契约测试

新增 `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TmdbDetailSeekProgressSourceTest.java`（沿用 AV-PROGRESS-02 的纯源码断言风格，非 Robolectric）：

- `inlineKernelRebuildRebindsSeekView`：断言 `startInlinePlayer` 内 `preparePlayer(inlineHistoryPlayerKernel())` → `getSeekView().setProgressPlayer(player().getPlayer())` → `startPlayer(getHistoryKey(), result,` 的先后顺序。
- `pendingInlinePlaybackReusesTheSameStartPath`：断言 `startPendingInlinePlayer()` 与 `restoreInlinePlaybackIfNeeded()` 仍委托给 `startInlinePlayer`（保证上面的重绑覆盖延迟与重连分支）。
- `inlineRebindGoesThroughTheSharedSeekAccessor`：断言 `getSeekView()` 仍返回 `inlineSeek()`，且 `startInlinePlayer` 不绕过访问器直接抓 `binding.seek`。

## 6. 验证

- 定向（新增）：`:app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.activity.TmdbDetailSeekProgressSourceTest' --tests 'com.fongmi.android.tv.ui.activity.VideoActivitySeekProgressSourceTest'` ⇒ BUILD SUCCESSFUL，两个 suite 各 3 tests / 0 failed（既有 AV-PROGRESS-02 测试未回归）。
- 关联回归：`:app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest' --tests 'com.fongmi.android.tv.ui.activity.VideoActivityLayoutTest'` ⇒ 130 tests / 0 failed 与 158 tests / 0 failed，含 `playerKernelSwitchStaysScopedToCurrentPlayback`、`inlineEpisodeSwitchDoesNotCarryPreviousEpisodePosition` 等既有内联起播顺序断言。
- 编译：两个 flavor 均通过（leanback 与 mobile 测试任务各自编译对应变体）。
- 未做真机/设备复测：本机无可用电视设备，改为上述源码契约 + 布局顺序断言覆盖；真机复测由用户在内核不一致场景下确认。

## 7. 未做与边界

- 未改 `CustomSeekView`、`PlaybackActivity`、`PlayerManager`：归属判定的根在「重建发生在 PlaySpec 之前」，属共用语义，改动面大于本症候，且已被既有源码测试钉住。
- 未做四内核（EXO / MPV / IJK / System）在别的重建入口的全量普查；本任务只覆盖已确认的内联起播路径。若后续发现第四条路径，沿用同一「重建后立即重绑」处理并追加对应契约测试。
