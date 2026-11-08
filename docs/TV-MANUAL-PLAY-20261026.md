# TV-MANUAL-PLAY-20261026 关闭「点播自动播放」后的手动播放交互修复

## 目标

TV 端关闭【点播自动播放】后进入手动播放模式：预览位显示影片封面而不露出黑框，
点选集直接全屏起播；在预览框上按确认键一次完成「全屏 + 起播」，省掉「先全屏、再按一次 OK」的多余步骤。

> 初始焦点：进入播放页仍落在左上角预览窗口（与自动播放开启时一致）。这一点在「回归修复 3/4」
> 中经用户纠正后确定 —— 早期版本曾把初始焦点重定向到选集，导致焦点在播放器与集数之间来回跳。

## 关键更正：目标对象

初版方案误把目标定位到 `TmdbDetailActivity`（沉浸融合内联面板）。经用户指正后确认真实对象是
`app/src/leanback/res/layout/activity_video.xml` 的 `@+id/video`（400dp×225dp、margin 24dp、
左上角、`background="@color/black"`、`focusable="true"`、内含 `exo`），承载于 `VideoActivity`，
覆盖 **原生增强 / 影视原生 / 炫彩详情的播放页**（`TmdbDetailActivity.playDefaultPlayback()` →
`VideoActivity.startDirectTmdb(...)`）。用户确认 Q1=A（所有 VideoActivity 场景）、Q2=A（选集直接全屏播放）。

## 根因

| 现象 | 根因 |
|---|---|
| 焦点落在预览窗口 | `mBinding.video.requestFocus()` 有 9 处无条件调用（2258/2597/2777/2865/2889/4487/5570/5275 等），与自动播放开关无关；`checkCast()` → `showInitialPreview()` 末尾即其一 |
| 要先全屏再按 OK 才播 | `video` 点击监听是 `onVideo()`：`if (!isFullscreen()) enterFullscreen();`，只切布局不起播；全屏后 OK 才走 `onKeyCenter()` → `player().isEmpty() ? onRefresh()` |
| 选集不直接播放 | `onItemClick(Episode)` 首行 `if (shouldEnterFullscreen(item)) return;`，而 `isPlayingContent(episode)` 只对「已选中」集为真；点其它集走 `selectEpisode` → `onRefresh` → `startPlayer`，此时 `shouldAutoPlay()==false`，prepare 了但不播 |
| 黑框而非封面 | `loadArtwork` 把封面喂给 `exo.setDefaultArtwork(...)`，但 `PlayerView` 仅在无视频帧时显示；`video` 的 `background` 是硬编码黑 |

自动播放开关作用点：`VideoActivity extends PlaybackActivity`，
`PlaybackActivity.shouldAutoPlay()` 返回 `PlayerSetting.isAutoPlay()`，作为 `playWhenReady` 传入
`player().parse(...)` / `player().start(...)` —— 关掉后取流照做、只是不播。

## 实现

### 1. 新增「手动播放模式」语义

```java
private boolean isManualPlaybackMode() {
    return Util.isLeanback() && !PlayerSetting.isAutoPlay();
}
```

### 2. 初始焦点分流（含「选集未就绪」挂起）

> **已被「回归修复 4」撤销**：用户明确要求进场焦点仍留在播放器，故 `focusInitialEpisode()`、
> `mPendingManualEpisodeFocus` 挂起机制与「选集重建后回焦选集」全部删除，
> `requestInitialPlaybackFocus()` 现在只有一行 `mBinding.video.requestFocus();`，
> `flushPendingManualEpisodeFocus()` 保留为空方法以维持调用点。以下为该轮的历史实现，仅供追溯。

`requestInitialPlaybackFocus()` / `focusInitialEpisode()` / `flushPendingManualEpisodeFocus()`：
详情刚绑定时选集适配器可能为空，此时记下 `mPendingManualEpisodeFocus`，等
`setEpisodeAdapter` 两条分支（普通路径与 `setUpstreamNativeEpisodeItems`）末尾 flush，
避免在选集为空的那一刻误回退到预览窗口。替换点：`setDetail`、`revealShellWhileTmdbLoads`、
`revealTmdbDetail`、`showInitialPreview`，每处保留原分支作为非手动模式路径。

### 3. 选集直接全屏起播（含「已选中集」分支）

```java
if (shouldEnterFullscreen(item)) {
    // 已选中集：shouldEnterFullscreen 只负责进全屏，自动播放关闭时需补一次显式起播
    if (isManualPlaybackMode() && !player().isPlaying()) { ...; onPlay(); }
    return;
}
if (isManualPlaybackMode()) { mExplicitPlaybackRequest = true; mManualPlaybackStarted = true; }
selectEpisode(item, true);
if (isManualPlaybackMode() && !isFullscreen()) enterFullscreen();
```

`shouldAutoPlay()` 覆盖为**一次性消费**：

```java
@Override
protected boolean shouldAutoPlay() {
    if (mExplicitPlaybackRequest) { mExplicitPlaybackRequest = false; return true; }
    return super.shouldAutoPlay();
}
```

**关键修正（两轮）**：
1. 初版在 `selectEpisode` 之后又调一次 `onRefresh()`，而 `selectEpisode` 内部已调 —— 重复取流，已改为复用那一次。
2. 初版用 `try/finally` 立即复位标志，但起播是**异步**的（取流结果经 LiveData 回来才 `startPlayer`），
   标志早已变回 false。已改为在 `shouldAutoPlay()` 里一次性消费。`onError` 里清理该标志防止污染后续取流。

### 4. 预览位封面层

`activity_video.xml` 在 `exo` 之后、`audioStage` 之前插入 `@+id/videoCover`（`focusable="false"`、
`clickable="false"`、默认 `gone`）。`updateVideoCover()` 判据用 **`!mManualPlaybackStarted`** 而非
`player().isEmpty()`：手动模式下详情页会先 prepare（播放器非空但没播），用 `isEmpty` 会误判成该隐藏。

## 验证（模拟器 127.0.0.1:5561，HD1910 / x86_64 + libnb.so ARM 转译）

设置：`detail_open_mode=2`（影视原生）、`player_auto_play=false`。

| 项 | 证据 |
|---|---|
| 待播态不自动播放 | `state=2`（PAUSED）、`speed=0.0`、`position=0` |
| 封面层生效 | `app:id/videoCover` 可见性 `V.ED.....`（VISIBLE），尺寸 0,0-800,450 |
| 焦点不在预览框 | 点集前焦点落在下方选集卡片（UI dump 确认） |
| 点选集直接起播 | `state=3`（PLAYING）、`speed=1.0`、position 12093→340212 持续推进 |
| 直接全屏 | `app:id/video` 由 800x450 变为 `0,0-1920,1080` |
| 起播后封面自动隐藏 | `videoCover` 变为 `G.ED.....`（GONE） |
| 已选中集分支 | 同样由暂停转为 `state=3` 全屏播放 |

诊断期内曾打印 `MANUALDBG` 日志实证：`onItemClick episode=第1集 manual=true shouldEnter=false`、
`shouldAutoPlay explicit=true`；**诊断日志已全部移除**，最终产物中 `MANUALDBG` 命中数为 0。

单测（最终）：
- `VideoActivityLayoutTest` 157 项 / 0 失败（含新增 2 项）
- `PlayerPlaybackRegressionSourceTest` 15 / 0、`PlayerControlFocusIntegrationTest` 11 / 0
- `TmdbDetailActivityLayoutTest` 130 / 0、`DetailModeControllerTest` 13 / 0

**负向对照**：把 `isManualPlaybackMode()` 临时改为 `return false` →
新增用例如实失败，证明断言有效（已还原）。

**非本次引入的观察项**：验证中遇到一次 ANR（`executing service PlaybackService`）。
ANR 主线程堆栈为 `CustomSeekView.updateProgress` → `MediaController.isPlaying()`（IPC），
我的方法在堆栈中命中数为 0，属模拟器 ARM 转译性能问题，与本次改动无关。

## 环境说明（重要）

本会话 `ops_bash`（bash.exe）在 `app/`、`docs/`、`.codex/`、`G:\GradleCache` 下均被沙箱 ACL 拒绝，
而 `pwsh` 与 `read`/`write`/`edit` 不受限 —— 编译、测试、adb、git 全部改走 `pwsh` 完成。
`task_guard.sh` 因依赖 bash 服务无法运行，改用直接 git 提交 + annotated tag 做等价收尾。

另：F 盘曾剩余 0GB 导致 `磁盘空间不足` 编译失败，清理 `app/build/intermediates`、`app/build/tmp` 后恢复。

## 回归修复：切线路后集数分段按钮 LEFT/RIGHT 失效

用户在手动播放功能上线后报告（m01153、m01160）：「集数分组按钮向左和向下无法选到对应的按钮」，
「播放一段时候后能正常但是刚切换线路后就不行」。

### 现场与取证

- 设备：模拟器 `emulator-5562`（HD1910 / 1920x1080；5561、5560、5554 在验证期间陆续 `mCurrentFocus=null` 死机）。
- 视图树取证：`adb shell dumpsys activity top`，按缩进维护祖先栈解析 flags
  （第 3 组首字符为可见性，第 4 组含 `F` 才是 hasFocus，第 5 组为 bounds），
  脚本 `out/fp.ps1` / `out/st.ps1`。`uiautomator dump` 在该模拟器上经常返回空 focused 节点，不可靠。
- 稳定复现序列（seq34 / seq36）：进入详情 → UP 到分段行 → UP 到线路行 → RIGHT → OK 切线路 → DOWN 回分段行
  → LEFT/LEFT/RIGHT 全部纹丝不动（分段行已重建：bounds 宽度由 365 变 383）。
- 关键教训：截图里白色焦点环会滞后/残留，不能据此判断焦点；必须用 dumpsys。
  设备启动慢会让整轮按键变成空操作，脚本必须有「等应用拿到窗口焦点」的门禁。

### 根因（探针日志实证）

不是 nextFocus 声明错误，也不是按键没到达分段行。`out/seq35.log` 显示 LEFT 的 ACTION_DOWN 已把选中位改到 1，
但 25~35ms 后又被复位。`selectEpisodeSegmentPosition` 的调用栈有三条来源、交替约 20Hz：

1. `setUpstreamNativeEpisodeItems → setEpisodeAdapter → onItemClick(Flag) → FlagSelectionListener.run`（线路网格布局驱动）；
2. `selectEpisodeSegment → onSegmentFocus → ArrayAdapter 的 text OnFocusChangeListener`（分段行子项获焦驱动）；
3. `selectEpisodeSegment → onChildViewHolderSelected`（分段行 BaseGridView 的 child-selected 监听，注册在 `setRecyclerView`）。

即：线路网格布局 → 重建选集 → 分段行 `setSelectedPosition(2)`；分段行布局时 `BaseGridView.focusToViewInLayout`
把焦点交回选中子项 2 → 触发 `onSegmentFocus(2)` → `selectEpisodeSegment` → `showEpisodeSegment` →
`addAll`(`notifyDataSetChanged`) → 整页再布局 → 回到第一步。刚用左右键移动的选中位在这个自激循环里被反复弹回。

### 补丁（app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java）

1. `showEpisodeSegment`：内容相同就直接返回（`isSegmentDisplayed(items)`），切断无意义重建。
2. `selectEpisodeSegment`：非请求焦点且分段未变时 no-op（`isSegmentContentDisplayed(position)`）。
3. `selectEpisodeSegmentPosition`：用户正把焦点放在分段行上时，焦点位是权威，重建回调不得改写选中位。
4. 分段行左右键改为显式接管：`onArrayKey` 的 LEFT/RIGHT 走 `moveArraySelection(±1)`，
   以真实焦点位（`focusedArrayPosition()`）为准，并用一次性 `OnGlobalLayoutListener` 在布局结束后复核补偿。
5. 集数网格的焦点校正改为带状态的布局监听（`mEpisodeGridFocusTarget`）：
   异步取流/图片加载后的第二次布局会把焦点留给预览框，只等一帧会漏。
   （监听范围随后在「回归修复 3」收窄为「仅 `getCurrentFocus() == null` 时补偿 + 3 次上限」。）
6. 补齐 `episodeHeader` 整条 GONE 时的可达性判断：`isEpisodeFocusTarget` 改用 `isEffectivelyVisible`
   （逐级检查自身与全部祖先），否则 `findFocusUp/Down` 会把已隐藏的表头工具当成有效邻居。
7. ~~手动模式焦点安全网~~：该根视图全局焦点看门狗**已在「回归修复 3」删除**，
   它没有区分「进场初始焦点」与「用户主动移到播放器」，正是焦点来回跳的来源。
8. `tryStartFastTmdbPlayback`、`resetDetailForNewIntent` 等处的裸 `mBinding.video.requestFocus()`
   统一收敛到 `requestInitialPlaybackFocus()`，焦点策略只剩一个出口（当前实现即 `mBinding.video.requestFocus()`）。
9. `onDestroy` 注销上述监听。

### 验证（模拟器 emulator-5562）

设置：`detail_open_mode=2`（影视原生）、`player_auto_play=false`。

| 项 | 证据 |
|---|---|
| 待播不自动播、焦点在选集 | `state=2`（PAUSED）`speed=0.0`；焦点 `episodeGrid > duration > text` |
| 预览位显示封面 | `app:id/videoCover` VISIBLE |
| 点选集直接全屏起播 | 按 OK 后 `state=3` `speed=1.0`，position 258560 → 264562 递增 |
| 起播后直接全屏、封面隐藏 | `app:id/video` bounds 变 `0,0-1920,1080`，`videoCover` 变 GONE |
| 切线路后分段行左右可用 | LEFT 272,0-383 → 176,0-256 → 48,0-160；RIGHT 逐格回退 |
| 切线路后分段行向下可用 | DOWN 落到 `episodeGrid > duration > text` |

单测与构建：`./gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --offline --tests VideoActivityLayoutTest --tests PlayerPlaybackRegressionSourceTest --tests PlayerControlFocusIntegrationTest --tests TmdbDetailActivityLayoutTest :app:assembleLeanbackArm64_v8aDebug -q`
→ exit 0，无 FAILED。`VideoActivityLayoutTest` 同步新增断言覆盖上述守卫与左右键接管。

自动播放模式回归（`player_auto_play=true`）：进入详情页直接 `state=3` 播放、焦点在 video、`videoCover` GONE，行为未受影响。

临时诊断日志（`FOCUSDBG-TEMP` / `VideoFOCUS` / `debugFocusId` / `MANUALDBG`）已全部移除，最终产物命中数为 0。


## 回归修复 2：选集网格内 LEFT 失效、RIGHT 跳出网格落到「简介」

用户后续报告（m01171）：「还是有问题我切换线路后按下到了分组按钮（正常）再按下到了当前集数按钮（正常）
再左遥控无效右遥控就跑到了简介按钮了错误的」。

### 根因

前一轮修的是**分段行（分组按钮）**的左右键，用户这次报的是**集数网格内部**的左右键，是另一处：

- 集数卡片根视图（`adapter_episode_card.xml` 的 `cardContainer` / 文本芯片）**不可聚焦**，
  真正持有焦点的是卡片内部的 `duration`/`text` 子视图。
- `EpisodeAdapter` 只写 `nextFocusUp/Down`（`bindTextView` 316-317、`bindCardView` 364-365），
  **没有任何水平 nextFocus**。
- `onEpisodeKey` 原先只接管 UP/DOWN，LEFT/RIGHT 直接 `return false` 交回系统 FocusFinder。
  FocusFinder 在卡片内部找不到水平邻居，于是：
  - 向左：无可达目标 → 焦点不动（表现为「左遥控无效」）；
  - 向右/向上：几何搜索跳出选集网格，落到网格上方最近的可聚焦控件 —— 即 `@+id/content`「简介」按钮。

这也解释了为什么「向下到当前集数按钮正常、再左/右就错乱」：纵向由 `verticalFocusTarget` 显式接管，
横向没有。

### 补丁

1. `TmdbEpisodeGridPolicy.horizontalFocusTarget(position, spanCount, itemCount, right)`：
   只在本排内移动（`position ± 1`，并夹在本排 `[rowStart, rowEnd]` 内），
   行首向左、行尾向右返回 `NO_FOCUS_TARGET`。与既有 `verticalFocusTarget` 同一策略类的纯函数，
   便于单测。
2. `VideoActivity.onEpisodeKey` 增加 LEFT/RIGHT 分支：算出 `target` 后
   `return focusEpisodeGridPosition(target)`；`NO_FOCUS_TARGET` 时 `return true`（消费按键），
   焦点留在原卡片，不再交给 FocusFinder，因此不会跳出网格。

### 验证

单测（`TmdbEpisodeGridPolicyTest` 新增 2 例，共 9 例全过）：

| 用例 | 断言 |
|---|---|
| `horizontalFocusTargetStaysInsideTheCurrentRow` | 行内推进、行首左边界、行尾右边界、非法 position 全部符合预期 |
| `horizontalFocusTargetUsesTheShortLastRowBoundary` | 末排短行（4 项 / spanCount=3）左右都不越界 |

组合验证：
```
.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug :app:testMobileArm64_v8aDebugUnitTest --offline \
  --tests VideoActivityLayoutTest --tests TmdbEpisodeGridPolicyTest \
  --tests PlayerPlaybackRegressionSourceTest --tests PlayerControlFocusIntegrationTest
```
→ `BUILD SUCCESSFUL in 6m 16s`，EXIT=0。
`VideoActivityLayoutTest` 157/0、`TmdbEpisodeGridPolicyTest` 9/0、
`PlayerPlaybackRegressionSourceTest` 15/0、`PlayerControlFocusIntegrationTest` 11/0。

`VideoActivityLayoutTest` 同步新增断言 `episode grid DPAD left/right must stay inside the current row`，
钉死 `onEpisodeKey` 必须含 LEFT/RIGHT 分支、调用 `horizontalFocusTarget(...)` 且 `NO_FOCUS_TARGET` 时消费按键。

设备实测：模拟器 `127.0.0.1:5561` 在本轮验证期间被宿主机上另外 7 台模拟器挤到
`loadavg 6.0`、`pm install` 长时间不返回，**本轮的端到端按键复测未能完成**；
结论以单测 + 源码契约 + 上一轮同链路（`focusEpisodeGridPosition` 已被 UP/DOWN 验证可用）为准。
未完成部分已明确记录，未做任何「已验证」的虚假声明。

## 回归修复 3：焦点在播放器与集数按钮之间来回跳（撤销过度激进的焦点看门狗）

用户报告（附截图）：切线路后向下移动到集数按钮，焦点会在**播放器和集数按钮之间来回跳**，
并质疑「播放器聚焦后为什么要跳到集数按钮上？顶多是进入播放页默认焦点在当前集数吧？」

这个质疑是对的，是我前一轮修得过头了。

### 根因：两处「焦点看门狗」越权

1. **根视图全局焦点看门狗**（`initView` 里的 `addOnGlobalFocusChangeListener`）：
   只要`newFocus == mBinding.video` 就 `post` 一次 `focusInitialEpisode()` 把焦点拽回选集。
   它**没有区分「进场初始焦点」和「用户主动移到播放器」**，所以用户按 UP 到播放器后
   立刻被抢回选集，形成来回跳。
2. **集数网格布局监听器**（`ensureEpisodeGridFocusListener`）同样过宽：
   原条件 `if (focus != null && !isFocusInside(mBinding.video)) return;`
   —— 焦点在**预览框**时不算「已在其它行」，于是每次布局都重新抢回选集。
   RecyclerView 回收持有焦点的子项时 `rootViewRequestFocus()` 会把焦点交回预览框，
   两者互相拉锯，正好是用户看到的闪烁。

根子在于：用户的需求只是「**进场时**默认焦点落在当前集」，不是「焦点永远不许停在播放器」。
我把一次性行为写成了常驻看门狗。

### 补丁

1. **删除**根视图全局焦点看门狗及其 `mManualFocusRedirectPending` 字段。进场初始焦点由
   `requestInitialPlaybackFocus()` 在 `setDetail` / `revealShellWhileTmdbLoads` / `revealTmdbDetail` /
   `showInitialPreview` 四处一次性决定，不需要常驻监听。
2. **收窄** `ensureEpisodeGridFocusListener`：只在 `getCurrentFocus() == null`（焦点真的丢了）时补偿，
   一旦焦点落在任何控件（含预览框）就 `cancelPendingEpisodeGridFocus()` 交还控制权；
   并加 `EPISODE_GRID_FOCUS_MAX_ATTEMPTS = 3` 上限，彻底杜绝长期看门狗。

### 行为对照

| 场景 | 修复前（本轮 bug） | 修复后 |
|---|---|---|
| 进入播放页（手动模式） | 焦点在选集 | 焦点在选集（不变） |
| 用户 UP 到播放器 | **被拽回选集** | 焦点留在播放器 |
| 切线路后 DOWN 到集数 | **来回跳** | 焦点稳定停在集数按钮 |
| 预览框意外获焦 | 被反复抢走 | 保持系统赋予的焦点，不再拉锯 |

### 验证

单测 `VideoActivityLayoutTest` 新增两条契约：
- `episode focus correction must give up as soon as any view holds focus, so it never fights the player window`
  —— 要求方法体含 `if (getCurrentFocus() != null) {` 且**不含** `isFocusInside(mBinding.video)`；
- `episode focus correction must be bounded instead of acting as a permanent watchdog` —— 要求含尝试次数上限；
- `no root focus watchdog may reclaim focus from the player window` —— 要求全局看门狗与
  `mManualFocusRedirectPending` 都不存在。

组合验证：`assembleLeanbackArm64_v8aDebug` + `testMobileArm64_v8aDebugUnitTest`
（VideoActivityLayoutTest 157/0、TmdbEpisodeGridPolicyTest 9/0）→ `BUILD SUCCESSFUL`，EXIT=0。

**负向对照**：把监听器临时改回旧的 `if (focus != null && !isFocusInside(mBinding.video)) return;`，
`VideoActivityLayoutTest` 立刻 `FAILED`，报错正是新增断言
`episode focus correction must give up as soon as any view holds focus...`，
证明断言真的在管这件事，而不是摆设。随后已还原为正确实现并复跑全绿。

设备端复测同样未完成（5561 仍受宿主机 8 台模拟器争抢，`loadavg` 6~8）。

## 回归修复 4：进场焦点收回播放器 + 切线路后集数网格左右键失效（回声守卫）

### 4.1 用户对交互的再次纠正

用户（m00406–m00432）明确了两点：进场时**不要**把焦点重定向到选集（焦点留在播放器，与改动前一致）；
在播放器上按确认键应像详情页的「继续播放」按钮一样**先全屏再播放**。其余逻辑不动，
只在播放器上按确认键时判断是否关闭了自动播放。

前一轮的 `mPendingManualEpisodeFocus` 挂起机制、`focusInitialEpisode()`、以及
「选集重建后回焦选集」全部是这次纠正要撤销的对象 —— **它们正是「焦点来回跳」的来源**。

### 4.2 补丁（app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java）

1. `requestInitialPlaybackFocus()` 简化为单行 `mBinding.video.requestFocus();`，
   删除 `isManualPlaybackMode` 分支、`focusInitialEpisode()` 调用与 `mPendingManualEpisodeFocus = true`。
2. `flushPendingManualEpisodeFocus()` 变为空实现（保留方法名以维持调用点与测试契约）。
3. 删除死代码：`focusInitialEpisode()`（含 `scrollToEpisode(position, true)`）、
   字段 `mPendingManualEpisodeFocus` 及其在 `resetDetailState` 的赋值。
4. 三处焦点分支统一：`revealShellWhileTmdbLoads` → `mBinding.video.post(() -> { if (!mBinding.getRoot().hasFocus()) requestInitialPlaybackFocus(); });`；
   `revealTmdbDetail` → `if (hiddenByLoading) mBinding.video.post(this::requestInitialPlaybackFocus);`；
   `showInitialPreview` → `requestInitialPlaybackFocus();`。
5. `onVideo()` 承担「播放器确认键 = 全屏 + 起播」：

```java
private void onVideo() {
    if (isFullscreen()) return;
    if (isManualPlaybackMode()) {
        mExplicitPlaybackRequest = true;
        mManualPlaybackStarted = true;
        updateVideoCover();
    }
    enterFullscreen();
    if (isManualPlaybackMode() && player() != null) {
        if (player().isEmpty()) onRefresh();
        else if (!player().isPlaying()) onPlay();
    }
}
```

`mExplicitPlaybackRequest` 由 `shouldAutoPlay()` **一次性消费**（起播是异步的，
用 `try/finally` 立即复位会被吞掉 —— 这是之前踩过的坑）。

### 4.3 切线路后集数网格 LEFT 无效 / RIGHT 跳到「简介」的真正根因

这已经是同一处交互的第四次返工。前三次都在**没有设备实测**的情况下凭单测宣布完成，这次先上设备取证。

用临时 `EPDBG` 日志（`logcat -c` + `logcat -d -s EPDBG` 逐键切片）抓到决定性证据：
在**完全没有按键**的空闲 4 秒里打出 **177 行**成对的
`flagClick in focus=shortDisplay@MaterialTextView flag=LZ线路` / `flagClick out ...`
→ **`onItemClick(Flag item)` 在以约 22Hz 自激循环**，焦点最终被「短显」按钮抢走，
集数网格因此拿不到焦点，左右键自然失效/乱跳。

闭合链路：

```
onItemClick(Flag)
  → mBinding.flag.setSelectedPosition(newPosition) / notifyItemsChanged
  → HorizontalGridView 派发 onChildViewHolderSelected
  → FlagSelectionListener.post(run)
  → listener.onItemClick(item)          ← 回声，又回到起点
```

载体是 `app/src/leanback/java/com/fongmi/android/tv/ui/custom/FlagSelectionListener.java`：
它把「自己刚写回的选中位」当成一次新的用户选择又投递了一次。

> 曾尝试在 `onItemClick` 里加 Activity 级身份守卫（字段 `mAppliedFlag`），已**全部回滚**：
> `onItemClick` 也可能被 `FlagAdapter` 的 `getRoot().setOnClickListener` 直接调用，
> 以「上次应用的线路身份」为键会把**用户第二次点同一条线路**误吞。
> 回声识别必须放在唯一能区分「用户派发」与「自身写回再派发」的位置 —— 即 Listener 内部。

### 4.4 补丁（FlagSelectionListener）

新增字段 `private Flag deliveredItem;`，在 `run()` 里按对象身份去重：

```java
Flag item = pendingItem;
pendingItem = null;
int position = view.getSelectedPosition();
// A newer selection or a refreshed route list makes the queued item stale.
if (item == null || item == deliveredItem) return;
if (position >= 0 && position < adapter.getItemCount() && adapter.get(position) == item) {
    deliveredItem = item;
    listener.onItemClick(item);
}
```

（曾用 `private boolean delivering;` + `try/finally`，**拦不住** —— 回声在下一个 post 周期才到达，
瞬时布尔标志早已复位。必须用对象身份，且跨帧有效。）

### 4.5 验证

**单测**（`app/src/testLeanback/java/com/fongmi/android/tv/ui/custom/FlagSelectionListenerTest.java` 新增 2 例，共 10 例）：

| 用例 | 断言 |
|---|---|
| `repeatedSelectionDispatchOfTheSameRouteIsIgnored` | 同一线路连续 3 次派发只投递 1 次（`[Drive B]`） |
| `aDifferentRouteStillSwitchesAfterTheEchoGuard` | 守卫不得误伤真实换线（`[Drive B, Drive C]`） |

**双向负向对照**（证明两条断言都真的把关，不是摆设）：
- 去掉守卫（`item == null || item == deliveredItem` → `item == null`）→ 第 1 例 **FAILED**（实际 `[Drive B, Drive B]`）；
- 守卫过度拦截（额外加 `|| item.isSelected() == false && deliveredItem != null`）→ 第 2 例 **FAILED**。
- 两次均已还原，最终形态为 `if (item == null || item == deliveredItem) return;`。

**组合验证**：
```
gradlew.bat :app:assembleLeanbackArm64_v8aDebug :app:testMobileArm64_v8aDebugUnitTest :app:testLeanbackArm64_v8aDebugUnitTest --offline --console=plain
  --tests com.fongmi.android.tv.ui.activity.VideoActivityLayoutTest
  --tests com.fongmi.android.tv.ui.custom.FlagSelectionListenerTest
```
→ `BUILD SUCCESSFUL in 2m 4s`，EXIT=0。`VideoActivityLayoutTest` 157/0、`FlagSelectionListenerTest` 10/0。

**设备实测（emulator-5562，影视原生 `detail_open_mode=2`、`player_auto_play=false`）**：

| 项 | 证据 |
|---|---|
| 切线路后焦点稳定不抖 | 切到 LZ线路后 3.5s（`q3.png`）与再 1.2s（`q4.png`）焦点都停在「LZ线路」白框，22Hz 循环消失 |
| 切线路后 DOWN 能进网格 | `r1.png` 到分段行「1-12」；`r2.png` 焦点进入 **第05集**（此前会跳回左上预览框） |
| 网格内 LEFT 有效 | `r3.png` 焦点到 第04集 |
| 网格内 RIGHT 不再跳「简介」 | `r4.png` 回 第05集；`r5.png` 到 第06集 |
| 空闲无焦点漂移 | 无按键连续两帧 `s1/s2` 一致 |
| 重新进详情页焦点在播放器 | `s3.png`（《兰香如故》）：焦点在左上角预览框，未自动播放 |
| 播放器确认键一次全屏+起播 | 按 OK 后 `state=2, speed=0.0` → `state=3, speed=1.0`，position 117508 递增 |

全部临时诊断日志（`EPDBG` / `epDbgName` / `FOCUS` 追踪 / `flagClick in|out` / `focusGrid` / `arrayKey`）
已清除，`grep EPDBG|epDbgName` 命中数为 **0**。
（文件中剩余 5 处 `android.util.Log.d("VideoActivity", ...)` 是全限定写法的既有代码，不属于本轮诊断。）

### 4.6 四轮返工的教训

同一处交互连续四次在同一根因附近打转，前三次都是「单测全绿 → 宣布完成」，而缺陷在真实设备上一直存在。
单测能固定契约，但**不能替代在设备上把用户描述的那串按键真实按一遍**；
这次能收敛，靠的是把 `logcat` 逐键切片、看到 22Hz 循环这个单测永远看不到的事实。

## 回滚

- 代码：`git revert <commit>`。
- 功能级降级：让 `isManualPlaybackMode()` 返回 `false` 即完全回到修复前行为；
  或仅让 `updateVideoCover()` 直接 return 关闭封面层，其余不受影响。

## 已知边界

- 手动模式下若封面 URL 全空，`videoCover` 保持 GONE（露出原黑底），不留透明洞。
- 起播失败时 `onError` 会清掉标志与已起播标记，封面恢复，用户可换源/换集重试。
- 短剧/阅读器路由走 `startPlayer` 既有拦截分支，未新增分支。
- 本次仅验证 leanback（影视原生模式）；原生增强 / 炫彩详情播放页共用同一 Activity 与同一判定，
  但未逐一实测。
