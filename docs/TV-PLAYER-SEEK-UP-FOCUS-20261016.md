# TV-PLAYER-SEEK-UP-FOCUS-20261016 电视端控制栏「进度条按 UP 回到刚才那颗按钮」

## Recovery anchor

- 目标（完成句）：电视端（leanback）控制栏里，遥控焦点从任意按钮按 DOWN 落到进度条后，再按 UP 必须回到**刚才停留的那颗按钮**，而不是固定跳到第一颗按钮（下集）或由几何搜索乱跳。
- 允许路径：`app/src/main/java/com/fongmi/android/tv/ui/helper/PlayerControlFocusHelper.java`、`app/src/testLeanback/java/com/fongmi/android/tv/ui/helper/PlayerControlFocusHelperSeekReturnTest.java`（新增）、`docs/TV-PLAYER-SEEK-UP-FOCUS-20261016.md`（本文）。
- 验收标准：同一条控制栏、同一串按键，修复前「下集 → DOWN（落到进度条）→ UP」焦点落到 `codecCapability`；修复后回到 `next`/`decode` 等**用户刚才停留的那颗按钮**（设备实测，见 §4）。
- 当前状态：**已实施、已验证、已提交待推送；C72 复评轮已修正 1 项实现 + 3 项文档缺陷**。
- 下一步唯一动作：`C72` 合并提交后推送 `dev3` 并创建到 `beta` 的 PR（见 `docs/C72-beta-merge-review-dev3-20261016.md`）。

## 1. 现象与证据（2026-10-16，leanback arm64 debug，设备 192.168.50.3:5559）

用户报告：TV 版控制栏遥控从按钮往下到进度条后，再往上遥控没有回到之前的按钮。

复现路径（设备 192.168.50.3:5559，平板分辨率 1920x1080，`mInTouchMode=false` 的真遥控路径）：
本地样本 `file:///sdcard/Movies/mov_bbb.mp4` → 详情页「立即播放」→ 进入影视原生全屏 → DOWN 唤出控制栏 → 方向键走位 → 读 `dumpsys activity top` 里的焦点标志位。

### 1.1 根因：FocusFinder 只认进度条自己的 `nextFocusUpId`

`View.focusSearch(FOCUS_UP)` 由 `FocusFinder` 实现，**只**使用候选视图自身的 `nextFocusUpId`；只有该属性为 `NO_ID`（无目标、或目标不可见/不可聚焦被跳过）时才回退到几何最近匹配。
而进度条（`DefaultTimeBar`）的 `nextFocusUpId` 此前没有任何「用户刚才在哪」的信息：

- 影视原生 `VideoActivity`（`app/src/leanback/.../VideoActivity.java`）：控制栏靠 `PlayerControlFocusHelper.handleKey(mBinding.control.getRoot(), getFocus2(), event)` 驱动，从未写过 `timeBar` 的 `nextFocusUpId` → UP 只能靠几何搜索。
- 详情直放 `TmdbDetailActivity`（`setupInlineFocusNavigation()`）曾把它**硬钉**成 `R.id.playerNext`（提交 `1f1180b2a7`）。

### 1.2 修复前设备实测（决定性 A/B 的 BEFORE 半）

控制栏已唤醒，焦点在首颗按钮「下集」，连按 `KEYCODE_DPAD_DOWN`/`UP`(20/19)：

| 步骤 | 焦点落点（`dumpsys activity top`） | 说明 |
| --- | --- | --- |
| 初始（`showControl` 后） | `MaterialTextView#next` | 首颗按钮 |
| DOWN | `DefaultTimeBar#timeBar` | 落到进度条，符合预期 |
| **UP** | `MaterialTextView#codecCapability` | **缺陷**：跳到第 5 颗按钮，不是「下集」 |
| 再 UP | `MaterialTextView#codecCapability` | 停在那里，连按也无法回到原位 |

即用户的观感：从任意按钮下到进度条后上不来，焦点被扔到别处。

## 2. 决策

| 方案 | 结论 |
| --- | --- |
| `no change` | 用户已把行为当成缺陷报告，不满足验收 |
| 只在 `TmdbDetailActivity` 里把 `nextFocusUpId` 钉成 `playerNext` | 是既有实现（`1f1180b2a7`），治不了「回到**刚才那颗**按钮」，且完全没覆盖影视原生控制栏 |
| 给每颗按钮各挂 `setNextFocusDownId(R.id.timeBar)` + 每颗按钮的 `setNextFocusUpId(自己)` | 要逐个维护两套控制栏（原生 `view_control_vod_action.xml` + 详情直放动态按钮 + MPV 动态自定义按钮），改一次按钮就要改一次；且「按钮 UP 到自身」与「进度条 UP 回到按钮」是两件事，仍需额外状态 |
| 给进度条重写 `focusSearch` 拦截 UP | 改变 `View` 的焦点语义，影响所有调用 `timeBar.focusSearch` 的路径（含 `CustomSeekView.requestFocus` 的委托链），面过大 |
| **采用：在遥控按键路径上把「刚才停留的控件」同步进 `timeBar.nextFocusUpId`** | 单点改动、复用已有 helper；内置按钮、详情直放按钮、MPV 自定义按钮一律自动覆盖；不新增状态、不改变任何 `View` 语义 |

为什么在 `handleKey` 里同步是安全的：该 helper 是电视端控制栏唯一的遥控按键入口（`VideoActivity:8154`、`TmdbDetailActivity:10887`），它已经保证「焦点在控制栏内」，因此这里读到的 `root.findFocus()` 就是用户当前停留的控件，且同步发生在框架做焦点搜索**之前**。

## 3. 实施

### 3.1 `PlayerControlFocusHelper`（唯一改动文件）

- 新增 `seekReturnTarget(seek, controls, focused)`：判定哪颗控件可以被记成「回来目标」。**焦点已经在进度条自己身上时返回 `null`**——否则用户一到进度条就按 UP，记忆会被覆盖成进度条自身，UP 目标自我指向，等于丢记忆。同样拒绝：控制栏外的焦点、`NO_ID`、不可聚焦的控件，以及**进度条的容器**（`seek` 子树双向判定：焦点在 `seek` 内或 `seek` 是焦点祖先都拒绝）。
- 新增 `syncSeekReturnTarget(root[, focus])`：从 `root` 里按 `R.id.timeBar` 取真实进度条（两套控制栏 id 相同），通过 `seekReturnTarget` 判定后写入 `timeBar.setNextFocusUpId(target.getId())`；取不到进度条时是 no-op。注意：手机版 `view_control_seek.xml` 的 `timeBar` 仍存在于布局中（只是不可聚焦），所以该函数在手机版树上**照常执行并写入**，只是写入的目标永不被咨询（焦点到不了 `timeBar`），因此手机端观感不变——这与「函数没跑」不同，已由 `mobileShapedTreeIsNotANoop` 固定。
- `handleKey` 在确认「焦点在控制栏内」之后、`focusSearch` 之前调用一次 `syncSeekReturnTarget(root)`。

不改任何布局、不新增 View 状态、不碰 `TmdbDetailActivity` 与 `VideoActivity` 的现有接线，因此两套控制栏与手机端行为来源一致。

### 3.2 新增契约测试

`app/src/testLeanback/java/com/fongmi/android/tv/ui/helper/PlayerControlFocusHelperSeekReturnTest.java`（Robolectric 真实 View 语义，13 条）：
`buttonAboveTheSeekBecomesTheReturnTarget`、`seekItselfNeverOverwritesTheRememberedTarget`、`focusableSeekContainerIsNeverWrittenAsItsOwnUpTarget`、`realCustomSeekViewIsNotWrittenAsItsOwnReturnTarget`、`containerNeverHoldsFocusBecauseRequestFocusDelegatesToTheSeekBar`、`mobileShapedTreeIsNotANoop`、`focusOutsideTheControlBarIsIgnored`、`idlessOrUnfocusableViewsAreIgnored`、`syncWritesTheFocusedButtonIntoTheSeekUpTarget`、`syncKeepsTheMemoryWhileTheSeekItselfIsFocused`、`syncWithoutASeekBarIsANoop`、`remoteKeyPathSyncsTheReturnTargetBeforeAnyFocusMove`、`remoteKeyPathKeepsTheMemoryWhileTheSeekIsFocused`。

## 4. 验证

### 4.1 单元测试（本轮新生成产物）

首轮（9 条）：

```text
bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.helper.PlayerControlFocusHelperSeekReturnTest"
→ BUILD SUCCESSFUL in 5m 59s
app/build/test-results/testLeanbackArm64_v8aDebugUnitTest/TEST-com.fongmi.android.tv.ui.helper.PlayerControlFocusHelperSeekReturnTest.xml
  tests="9" skipped="0" failures="0" errors="0"   (mtime 00:56，属首轮)
```

C72 复评轮（13 条，含新增 4 条容器/手机版形状回归锁）：

```text
bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.helper.PlayerControlFocusHelperSeekReturnTest"
→ BUILD SUCCESSFUL in 23s
  tests="13" skipped="0" failures="0" errors="0"   (mtime 10:53:58，属 C72 轮)
```

### 4.2 设备 A/B（192.168.50.3:5559，覆盖安装，未卸载）

BEFORE 见 §1.2（UP → `codecCapability`）。AFTER（同一台设备、同一控制栏、同一串按键）：

| 步骤 | BEFORE | AFTER |
| --- | --- | --- |
| 唤出后 | `next` | `next` |
| DOWN | `timeBar` | `timeBar` |
| UP | `codecCapability` ❌ | **`next`** ✅ |
| DOWN 到进度条后右移再 UP（第 5 颗按钮 `decode`） | — | **`decode`** ✅（回到用户停留的那颗，而非固定首颗） |
| 焦点已在进度条上连按 UP | — | 记忆保持不变（仍回 `next`/`decode`），未退化成几何搜索 |

安装证据：`app-leanback-arm64_v8a-debug.apk` mtime `01:04:40`（本轮构建），`adb install -r` 返回 `Success`，`dumpsys package` 的 `lastUpdateTime` 与本轮一致。

### 4.3 详情内嵌控制栏设备复核（2026-10-16 补测，消除原未验项）

上一轮遗留的未验项即本控制栏。补测条件：设备 `192.168.50.3:5559`，临时把偏好 `detail_open_mode` 改为 `0`（沉浸融合，`TmdbDetailActivity` 内嵌控制栏 `playerControls` 才可达），测试结束后按原字节还原。

**为什么用沉浸融合模式验证**：这条内嵌控制栏属于 `TmdbDetailActivity`，`detail_open_mode` 为 `0`(融合) 时由 `FusionDetailController` 驱动（`rawFusionMode()` → `new FusionDetailController`），其 `showInlinePlayer()` 返回 `true`，即默认就展示内嵌播放器与 `playerControls`，因此最易稳定取样，故本轮选它。

作为对照，本轮先用 `detail_open_mode=2` 走 `am start -d file:///sdcard/Movies/mov_bbb.mp4` 本地推送入口时，落到了影视原生 `VideoActivity`：该卡片是 PUSH 站点的本地推送条目（`start()` 开头的 `PushParser.fromId(id).shouldSkipAutoTmdbMatch()` 分支直接转 `VideoActivity.startDirect`），并非模式路由所致。两边最终都走同一个 `PlayerControlFocusHelper.handleKey` 入口与同一个 `R.id.timeBar`。

内嵌控制栏实测（`dumpsys activity top` 读焦点；控制栏有 10s 自动隐藏，故把按键与取样放在同一次 `adb shell` 内完成）：

| 步骤 | 焦点落点 | 判定 |
| --- | --- | --- |
| 唤出后 DOWN | `playerEpisodes` | 按钮行可达 |
| 再 DOWN | `timeBar` | 落到进度条 |
| 右移 3 格 | `playerFullscreenAction`（第 4 颗） | 停在非首颗按钮 |
| DOWN | `timeBar` | 再落到进度条 |
| **UP** | **`playerFullscreenAction`** | ✅ 回到刚才那颗按钮（该栏修复前被硬钉为 `playerNext`，会强制跳「下集」） |

另外确认合理的边界行为：焦点已停在进度条上时继续按 UP，焦点交给控制栏外层的 `playerPanel`（进度条本来就是控制栏最下面一行，再往上离开控制栏是正确的，不是缺陷）。

**环境还原**：`shared_prefs/com.silent.android.webhtv_preferences.xml` 用测试前备份回写，md5 与测试前完全一致（`46624c3ab03d9e3bfc8a70a7b3ec825a`），`detail_open_mode=1`、`detail_theme_mode=1`、`tmdb_enabled=true`、`detail_interaction_mode=0` 均复原；未卸载应用，未改动其它设置。

### 4.4 未覆盖 / 残余未知

- 详情直放控制栏（`detail_open_mode=2`）本轮**未单独做设备走位**：该模式由 `EnhancedDetailController` 驱动且 `showInlinePlayer()=false`，需先点击播放才出现 `playerControls`。已设备复核的是同一套 `playerControls` 视图 + 同一个 `handleKey` 入口 + 同一个 `R.id.timeBar` 的沉浸融合模式（§4.3），故不再重复走位。
- 手机端（`mobile` flavor）的 `view_control_seek.xml` 里 `timeBar` 不可聚焦，所以即使 `syncSeekReturnTarget` 照常写入 UP 目标，焦点也到不了 `timeBar`、该目标永不被咨询，手机端观感不变；本改动不触及手机端判定路径，未做手机端回归。

## 5. 回滚

单文件回滚：`git revert <本任务提交>`（或还原 `PlayerControlFocusHelper.java`，并删除上述测试文件与本文档）。回滚后行为回到「进度条 UP 依赖几何搜索 / 详情直放钉死 `playerNext`」，即 `1f1180b2a7` 的状态。

## 6. 提交与标签

- 任务：`TV-PLAYER-SEEK-UP-FOCUS-20261016`（lane：quick-fix）
- 提交信息：`fix(tv): 控制栏进度条按 UP 回到刚才停留的按钮`
- 恢复标签：`recovery/TV-PLAYER-SEEK-UP-FOCUS-20261016/<timestamp>`（由 `task_guard.sh finish` 创建）
