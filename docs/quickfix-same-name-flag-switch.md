# quickfix-same-name-flag-switch — 同名线路切换误判「只全屏不切源」

## Recovery anchor

- 目标：同一 vod 内两条同名线路（chip 都显示「1」）时，开启「切换线路不自动播放」后切到线路 2 再点该集，必须真正切流起播，而不是只进全屏、实际仍播线路 1。
- 验收标准：
  1. 两条同名线路各自的选集条目被点击时，起播判定按**实际已应用的线路稳定键**（`线路名#索引`）而非线路名/列表选中标记。
  2. 非同名线路的既有行为不变（切线路仍进入内联/播放页、程序化切线路仍自动起播）。
  3. 双 flavor 编译通过 + 定向单测通过。
- 计划状态：已完成（代码 + 测试 + 编译 + 定向单测）。
- 分支/HEAD：`dev4` / base `306b823dc7b7340e13e999c09679eaccab0ee152`；工作树改动仅本任务 scope 内 6 个路径。
- 未验证工作树编辑：无（全部已编译通过）。
- 唯一下一步：`bash .codex/scripts/task_guard.sh finish --verified ... --commit-message ...` 提交并打 recovery 标签。

## 现象与根因

用户报告（术语：同一 vod 内的线路 chip 即「播放源」，显示名可重复）：
> 切换线路不自动播放时有个小问题：比如线路 1 有个叫「1」的播放源，线路 2 也有个叫「1」的，我切到线路 2 点这个「1」时会直接全屏，但实际还在播线路 1，感觉是按名称匹配导致没切过去。

根因是**线路身份在多个判定点退化为「线路名字符串」或「列表选中标记」**：

| 编号 | 位置 | 退化点 | 后果 |
| --- | --- | --- | --- |
| R1 | app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java `onItemClick(Flag)` | `if (resolved.isSelected() && !initialBinding) return;` | 同名线路被 indexOf/equals 退化到同一条，点击直接早退 |
| R2 | 两版 VideoActivity `shouldEnterFullscreen(Episode)` | `item.isSelected()` 只表示列表选中标记 | 切线路（保流）后点该集只进全屏 |
| R3 | 两版 VideoActivity `seamless(Flag)` | `episode.isSelected()` 当成「该线路这集已在播」 | 平滑换线路被跳过 |
| R4 | app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java `isCurrentInlinePlayback` | `TextUtils.equals(selectedFlag.getFlag(), inlinePlaybackFlag)` | 同名线路被判成「仍是当前播放」，只全屏不起播 |

背景约束：sites 协议层（app/src/main/java/com/fongmi/android/tv/api/SiteApi.java `playerContent`）只接受**线路名字符串**，所以修复只能在 UI 身份判定层做，不能改协议。

## 改动

新增「实际已应用的线路稳定键」状态，写入点必须在 `beginPlayerContentRequest(...)`（内部会 `invalidatePlayerContent()`）**之后**：

### app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java
- 字段 `private String mPlaybackFlagKey = "";`
- 新 helper（紧邻 `isCurrentPlayerContentContext`）：
  - `private String playbackFlagKey(Flag flag)`：先 `TmdbUIAdapter.flagIndex(mVod.getFlags(), flag)`，退化 `mFlagAdapter.indexOf(flag)`，均无则 `""`；命中用 `TmdbUIAdapter.flagKey(flag, index)`（= `Flag.stableKey`，`线路名#索引`）。
  - `private boolean isPlaybackFlag(Flag flag)`：`mPlaybackFlagKey` 非空且与之相等。
  - `private boolean isPlayingContent(Episode episode)`：`episode.isSelected() && isPlaybackFlag(getFlag())`。
- `invalidatePlayerContent()` 一并清空 `mPlaybackFlagKey`。
- `getPlayer(Flag, Episode)` 在 begin 调用之后写入 `mPlaybackFlagKey = playbackFlagKey(flag);`。
- 沉浸音频路径（`prepareImmersiveAudioPlayback` / `applyImmersiveAudioSelection`）写入同键。
- 判据替换：`onItemClick(Flag)` 早退 → `resolved.isSelected() && isPlaybackFlag(resolved) && !initialBinding`；`shouldEnterFullscreen` → `!isFullscreen() && isPlayingContent(item)`；`seamless` → `episode == null || (isPlaybackFlag(flag) && episode.isSelected())`。

### app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java
同上镜像；额外在 fast-TMDB 起播路径（`beginPlayerContentRequest` 之后）写入同键。

### app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java
- 字段 `private String inlinePlaybackFlagKey = "";`
- `isCurrentInlinePlayback` 追加 `&& TextUtils.equals(selectedSeasonFlagKey(), inlinePlaybackFlagKey)`。
- `startInlinePlayer(Result, long)` 写入 `inlinePlaybackFlagKey = selectedFlag == null ? "" : selectedSeasonFlagKey();`；两处内联停止点清空。

## 测试

- app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityLayoutTest.java：更新早退断言文本，新增稳定键字段/两个 helper/方法体断言（`shouldEnterFullscreen` 含 `isPlayingContent(item)`）。
- app/src/test/java/com/fongmi/android/tv/setting/FlagSwitchPlaybackWiringTest.java：新增 `bothPlayersIdentifyTheCurrentStreamByTheAppliedFlagKey`（双 flavor 播放页稳定键字段、invalidate 清空、getPlayer 写入点在 begin 之后、三处判据）与 `detailInlinePlaybackAlsoComparesTheAppliedFlagKey`（详情页字段、起播赋值、停止清空、稳定键比较、清空点 ≥2）。
- 新增断言均为纯源码字符串断言，不调用 Android API（mobile 单测源集无 Robolectric 依赖，仓库明确拒绝放宽 `unitTests.returnDefaultValues`）。

## 验证

1. `bash ./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon --console=plain` → BUILD SUCCESSFUL（17m19s，EXIT=0）。
2. `bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.setting.FlagSwitchPlaybackWiringTest --tests com.fongmi.android.tv.ui.activity.VideoActivityLayoutTest --tests com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest --no-daemon --console=plain`
3. `bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.setting.FlagSwitchPlaybackWiringTest --no-daemon --console=plain`

结果：
- mobile flavor：`FlagSwitchPlaybackWiringTest` tests=8 failures=0 errors=0；`VideoActivityLayoutTest` tests=155 failures=0 errors=0；`TmdbDetailActivityLayoutTest` tests=130 failures=0 errors=0（BUILD SUCCESSFUL 4m56s）。
- leanback flavor：`FlagSwitchPlaybackWiringTest` tests=8 failures=0 errors=0（BUILD SUCCESSFUL 1m22s）。
- 依据：app/build/test-results/testMobileArm64_v8aDebugUnitTest/*.xml 与 app/build/test-results/testLeanbackArm64_v8aDebugUnitTest/*.xml。

## 不做项（避免范围外回归）

- 不改 `Flag.equals/hashCode`（bean 级契约，影响面广）。
- 不改 `isCurrentPlayerContentContext` 的线路名比较（旧流归属校验仍按名称，本任务只修「是否已切到新线路」）。
- 不改 `updateHistory` 的 `sameFlag` 名称判断与 `VodBrowse`/`History.findFlag` 名称退化。
- 不改 spider 协议 `SiteApi.playerContent(flag, ...)`。
- 不改 `shouldEnterFullscreen` / `beginPlayerContentRequest` 的方法名与签名（既有源码切片测试依赖）。

## 回滚

本任务为单一原子提交，回滚即 `git revert <commit>`；改动不含数据迁移、无持久化格式变化（`FlagPreferenceCache` 与 `History.sourceBindingKey` 均复用既有稳定键格式）。
