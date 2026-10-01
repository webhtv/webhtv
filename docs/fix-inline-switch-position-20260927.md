# 详情直放/沉浸融合切集沿用上一集进度修复（2026-09-27）

## Recovery anchor

- **目标**：修复「详情直放、沉浸融合模式下，当前集播到某一位置后切下一集，下一集直接从上一集的位置开始播放」的问题；其他模式不受影响，正常续播语义保持不变。
- **验收**：中途手动切下一集从 0 开始；片尾自动连播从 0 开始；同集退出重进仍按 history 续播；从头重播（onReplay/refresh）真正从头开始；集数位置缓存不再被切集窗口期污染；新增源码回归测试通过；leanback/mobile 编译通过；设备覆盖安装实测通过。
- **当前状态**：第二轮修复（加载期残留位置回写）已完成并实测；提交与恢复标签待创建。
- **下一动作**：`task_guard.sh finish` 提交并打恢复标签。

## 现象与根因

用户实测：只有**详情直放**与**沉浸融合**两个模式出现「当前集播到哪，切下一集就从哪开始」（如第 1 集播到 1 分钟，切第 2 集直接跳到 1 分钟）；其他模式正常。

根因是这两个模式特有的**异步切集窗口期进度回写**：

1. `playAdjacentEpisode`/`selectInlineEpisode` → `onPlay()` → `saveInlineHistory()`（按旧集正确保存）→ `updateInlineHistory(新集)`（history 重指向新集、position 重置为 `TIME_UNSET`）→ `playInline()` 提交**异步**解析。
2. 窗口期内（数百 ms～数秒）播放器仍在播旧集，`inlinePlaybackEpisode` 仍是旧集；`onTimeChanged` 每秒触发，`canUpdateProgress = inlineStartPositionApplied(旧集遗留 true) || getInlineStartPosition() <= 0` → `updateInlineHistoryProgress` 把**旧集实时位置写进已指向新集的 history**；每 5 秒 `syncInlineHistory` 落库。
3. 解析返回 → `startInlinePlayer` → `inlineStartPosition = getInlineResumePosition()` = 被污染的旧集位置 → 新集 READY 后 `seekTo(上一集位置)`。

只有沉浸融合/详情直放走 `TmdbDetailActivity` 的内联异步播放链路（其他模式由 `VideoActivity` 同步切集，无此窗口），与用户「只有这两个模式复现」完全吻合。

附带缺陷（同一根因）：`onReplay`/`refreshInlinePlayback` 同集从头重播时，窗口期 tick 会把清零前的实时位置回填，「重播」退化为「续播」。

## 最小修复

`TmdbDetailActivity.java` 引入窗口态 `inlinePlaybackSettled`（默认 true）：

- **开窗**（false）：`updateInlineHistory` 中切集（`!sameEpisode`）或同集换线路（`sameEpisode && !sameFlag`）时；`onReplay`、`refreshInlinePlayback` 清零重播时。
- **关窗**（true）：`startInlinePlayer` 顶部（解析结果接管播放器；必须在 `NovelRouter` 拦截之前，防止阅读器路径泄漏窗口态）；`stopInlinePlayerForReload`（播放器清空）；`closeDetailFullscreenPlayer`（退出播放）。
- **消费守卫**：
  - `onTimeChanged`：`canUpdateProgress` 追加 `isInlinePlayerSettledOnSelection()` 校验。
  - `updateInlineHistoryProgress(long,...)`（所有进度写入的单点汇聚）：窗口期只更新 `createTime`，不写 position/duration。
  - `saveInlineHistory`：窗口期跳过 `EpisodePositionCache` 写入（此时 `currentInlineHistoryCacheKey()` 已指向新集，写入即污染缓存）。

## 验证

- **单元/源码回归**：`TmdbDetailActivityLayoutTest` 新增 `inlineEpisodeSwitchDoesNotCarryPreviousEpisodePosition`，锁住字段、开窗点、关窗点顺序（先于 NovelRouter）、三处守卫、安全默认恢复与重播开窗；`:app:testMobileArm64_v8aDebugUnitTest` 该类 129 项全通过（0 failure、0 error）。
- **基线对照**：`FollowingUiSourceTest` 等 4 个类 6 个失败在改动前的基线同样失败（既有 stale 断言，与本改动无关文件），非本任务引入；与本改动相关的测试类全部通过。
- **编译**：`:app:assembleLeanbackArm64_v8aDebug` 成功；leanback 变体单测编译执行通过。
- **设备实测（emulator-5556，覆盖安装未卸载）**：详情直放模式播《雀骨》：
  1. 片尾自动连播（第 1 集 45:00 → 第 2 集）：新集从 0 开始 ✅
  2. 第 2 集播放中手动选集切第 3 集（用户反馈场景）：新集从 0 连续递增（33s→95s→…，无跳变）✅
  3. 第 3 集播到 6:29 手动切第 4 集：第 4 集从 0 开始连续播放（起播时刻与 5:35 读数时间吻合），**未跳到第 3 集的 6:29** ✅
  4. 退出详情页重进第 4 集点「继续播放」：从退出时的 380312ms 续播（首读 381946ms）✅ 正常续播未被误伤
- **设备状态还原**：`detail_open_mode` 已还原为 1；临时 UI dump 已清理；logcat 已清空。

## 第二轮：偶现残留（2026-09-28 凌晨，用户在 5561 实测后）

用户在 5561 上装了修复版仍偶现。设备实测抓到铁证序列：

```text
TV-intro-skip: plan ready key=326904||tv|12|27@45  resumeMs=96939   ← 45秒的第27集，resume 却是上一集末尾位置
TV-intro-skip: plan ready key=326904||tv|12|134@69 resumeMs=84595  ← 与第133集完全相同
TV-intro-skip: plan ready key=326904||tv|12|135@41 resumeMs=139218
TV-intro-skip: plan ready key=326904||tv|12|136@45 resumeMs=139218 ← 原样延续到下一集
TV-intro-skip: plan ready key=326904||tv|12|124@45 resumeMs=110396 ← 超过本集时长91710
```text

**第二轮根因（第一轮守卫未覆盖的时序洞）**：

1. 切集 → `startInlinePlayer` 顶部关窗（settled=true）→ `player().stop()` → prepare 新集。
2. **ExoPlayer `stop()` 后 `getPosition()` 仍返回旧集位置**（不归零，直到新 media READY）。Exo stop 语义就是停止推进但保留位置。
3. 新集 READY 前的每秒 tick：`inlineStartPositionApplied` 已被置 false，但回退分支 `getInlineStartPosition() <= 0`（新集无历史时为 true）让 `canUpdateProgress=true`；settled 已关 → 守卫放行 → **把旧集残留位置（如 139218）写进已指向新集的 history**。
4. 新集 READY → `applyInlineStartPosition()` 读到污染值 → `seekTo(139218)` → 用户看到「下一集从上一集位置开始」。

短剧快切必现（解析快，tick 大概率落在加载窗口内）；长剧切集解析慢时 tick 落在窗口外不触发——这正是「偶现」的机理，也与用户先在长剧上验证通过、后又在短剧上偶现的经历吻合。

**第二轮修复**：新增 `inlinePlayerMediaReady` 媒体就绪态：

- `startInlinePlayer` 里 `player().stop()` 前置 false。
- `onStateChanged(STATE_READY)` 在 `applyInlineStartPosition()` 之前置 true。
- `stopInlinePlayerForReload` 回置 true（播放器已清空）。
- 三处写入守卫升级为双条件：`inlinePlaybackSettled && inlinePlayerMediaReady`（`isInlinePlayerSettledOnSelection()` 一并返回双条件）。

**5561 修复版实测（红果短剧自动连播 + 手动快滑切集）**：

```text
03:32:36 positionMs=71147   ← 上一集播到 71 秒
03:32:40 positionMs=0        ← 切集，新集从 0 ✅
03:33:03 positionMs=0        ← 第二次快切，从 0 ✅
03:33:11 positionMs=0        ← 第三次快切，从 0 ✅
（整段无任何位置跳变事件；另一次自动连播 78563→0 连续推进）
```text

对比修复前：同一设备同一片源 `resumeMs` 跨集延续、超过本集时长；修复后连续三轮快切起播位置全部为 0。

**附带记录（不属本任务）**：`plan ready` 打印中可见 `IntroSkipPlayback.resumeMs` 存在会话级残留（进入会话时首集真实续播位的异步打印线程旧值），不影响起播位置与进度写入，仅可能短暂抑制本集片头自动跳过判定且首轮后自愈；未改动，留待片头跳过专项观察。

## 风险与回滚

- 改动仅限 `TmdbDetailActivity` 内联播放链路，不触及 `VideoActivity`、`PlayerManager`、引擎与公共 API。
- 窗口期跳过的进度写入不丢数据：`onPlay()` 在窗口开启前已按旧集完整保存。
- 回滚：整体 revert 本提交即可。
