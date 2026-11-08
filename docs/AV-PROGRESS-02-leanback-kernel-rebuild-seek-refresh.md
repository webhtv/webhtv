# AV-PROGRESS-02 · 电视端内核重建后进度条丢失数据

## 1. 现象与来源

- 原始报告（AV-PROGRESS-01 的同一批反馈）：手机端与电视端播放页进度条没有数据，左右都是 `00:00`，视频本身正常播放，重开偶尔正常。
- AV-PROGRESS-01（fec02b27def0）已在 `CustomSeekView` 上补了「引擎未上报时长时用历史 duration 兜底」，覆盖起播阶段时长缺失。本任务补的是同一症候的另一条路径：**内核重建后进度条绑在已释放的旧 Player 上**。
- 来源为 AUTONEXT-01 记录、当时未修的遗留点：`docs/AUTONEXT-01-episode-transition-black-screen.md:164`——换集重建窗口内 `onPlayerRebuild()` 被跳过（其中含 `setProgressPlayer`）。

## 2. 根因链路

1. `PlayerManager.preparePlayer(int type, boolean force)`（`app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:2066-2096`）在重建引擎后立刻回调 `callback.onPlayerRebuild(player, force);`（:2093）。
2. 此刻新的 PlaySpec 尚未建立：Activity 侧 `activePlaybackKey()` 与 `manager.getKey()` 不同 ⇒ `PlaybackActivity.isOwner()`（`PlaybackActivity.java:258-262`）为假。
3. `PlaybackActivity.onPlayerRebuild(...)`（:1141-1153）整段受 `if (isOwner())` 保护，`getSeekView().setProgressPlayer(player)` 被一起跳过。
4. 重建窗口结束后没有任何代码把 seek view 重新绑到新引擎：`CustomSeekView` 的 1 秒轮询（`CustomSeekView.java:139-171`）继续读已释放的旧 Player，取不到时长与位置 ⇒ 进度条恒空、左右 `00:00`。
5. 手机端（`app/src/mobile/.../VideoActivity.java`）在 `applyHistoryPlayerKernel`（:2334）、`applyPendingPlayerKernel`（:2356）、`onServiceConnected`（:1519）三处已有补偿；**电视端（`app/src/leanback/.../VideoActivity.java`）的三处对应代码都没有补偿**，这就是 TV 端独有该路径的原因。

## 3. 修复

`app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`，两条自持的内核重建路径各补一次显式重绑：

- `applyHistoryPlayerKernel(boolean forcePrepare)`（:2175-2191）：`player().preparePlayer(kernel, forcePrepare);` 之后、`setPlayerKernel();` 之前插入 `getSeekView().setProgressPlayer(player().getPlayer());` 与中文注释。
- `applyPendingPlayerKernel()`（:2202-2209）：`player().preparePlayer(kernel);` 之后插入同一行与注释。

选择在重建后立即重绑、而不是等 `onPlayerRebuilt()`：重建发生在 PlaySpec 之前，归属判定在那一刻必然为假，等回调等于永远不等。重绑只换 Player 引用，`CustomSeekView` 自身的兜底逻辑（AV-PROGRESS-01）与轮询节奏不变。

## 4. 契约测试

新增 `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/VideoActivitySeekProgressSourceTest.java`（沿用既有 leanback 源码契约测试风格）：

- `historyKernelRebuildRebindsSeekView`：断言 `applyHistoryPlayerKernel` 内 `preparePlayer(kernel, forcePrepare)` → `setProgressPlayer(player().getPlayer())` → `setPlayerKernel()` 的先后顺序。
- `pendingKernelRebuildRebindsSeekView`：断言 `applyPendingPlayerKernel` 保留内核校验，且 `preparePlayer(kernel)` 之后紧跟重绑。
- `rebindsGoThroughTheSharedSeekAccessor`：断言 `getSeekView()` 仍返回 `mBinding.control.seek`，且重建方法不绕过该访问器直接抓 binding。

## 5. 验证

- 定向：`:app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.activity.VideoActivitySeekProgressSourceTest'` → BUILD SUCCESSFUL。
- 全量 leanback：`:app:testLeanbackArm64_v8aDebugUnitTest` → 4826 tests completed, 8 failed, 3 skipped。失败集合与本仓库既有环境基线逐一同名（见 `docs/C113-beta-merge-review-dev2-20261031.md:88-97`）：`MpvFontConfigTest`（沙箱无法提交 fontconfig 文件）、`MpvHlsCacheCoordinatorTest`（:208）、`ReaderPlaybackRoutingSourceTest` ×2、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`、`NativeEnhancedPlaybackStyleFocusTest`、`SearchResultDownFocusTest` ⇒ **零新增失败**。
- 编译：两个 flavor 均通过（leanback 由上面的测试任务编译，mobile 由 `compileMobileArm64_v8aDebugJavaWithJavac` 验证）。

## 6. 未做与边界

- 未做四内核（EXO / MPV / IJK / System）未上报时长路径的普查——用户明确只授权「修复已知的问题场景」。
- 截图中 `弹幕加载失败` 的 toast 文本在本仓库源码零命中（属第三方吐司/danmaku 库），不在本任务范围。
- 未改 `CustomSeekView`、`PlaybackActivity`、`PlayerManager`：这三处是手机/电视共用或已被既有源码测试钉住的语义，改动面更大且与本症候无因果关系。
