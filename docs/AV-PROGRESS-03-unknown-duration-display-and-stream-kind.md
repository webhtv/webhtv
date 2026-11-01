# AV-PROGRESS-03 · 时长未知被当成 00:00 与直播

## 1. 现象

- 播放页进度条与中央浮层的时长文案在部分片源上恒为 `00:00`，同时相对拖动（左右键）的位置文本也停在 `00:00`，而画面正常播放。
- 同一批片源上，点播被当成直播：直播页的倍速、字幕、选集入口不出现，续播位置丢失，片尾探测（自动跳过片尾）失效。
- 两者的共同前提是「引擎还没给出总时长」：EXO 在部分 HLS/TS 片源上停在 `C.TIME_UNSET`，IJK/System 的时长查询在起播早期同样可能拿不到值。

## 2. 根因链路

### (A) 展示层把「未知」压成 0

- `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:1238-1240` 改前：`public long getDuration() { return player.getDuration(); }`，直通引擎。
- `PlayerManager.java:1242-1244` 改前：`getDurationTime()` 返回 `Util.timeMs(Math.max(0, getDuration()))` —— `C.TIME_UNSET` 是负数，被 `Math.max(0, ·)` 压成 0，于是文案是 `00:00`。
- `PlayerManager.java:1233-1236` 改前：`getPositionTime(long delta)` 的上界写作 `Math.max(0, getDuration())`。时长未知时上界为 0，`Math.min(position + delta, 0)` 之后恒为 0 ⇒ 相对拖动的位置文本也永远是 `00:00`。
- 消费方：`app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:834`（调试摘要）、`app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` 的 `getTimelineDurationTime()` 与 `getPositionTime(...)`、`app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java:1710/:1720` 的手势时长文本。

### (B) 判定层把「未知」当成直播

- 改前四个内核的判定一致地写成「时长小于 1 分钟即直播」：
  - `app/src/main/java/com/fongmi/android/tv/player/engine/ExoPlayerEngine.java:715/:720`
  - `app/src/main/java/com/fongmi/android/tv/player/engine/MpvPlayerEngine.java:215/:220`
  - `app/src/main/java/com/fongmi/android/tv/player/engine/SystemPlayerEngine.java:102/:107`
  - `app/src/main/java/com/fongmi/android/tv/player/engine/IjkPlayerEngine.java:158/:163`（IJK 走 `player.getStreamSceneDecision().live()/.vod()`）
- `C.TIME_UNSET` 恒小于该阈值 ⇒ `isLive()` 为真、`isVod()` 为假。下游后果：
  - `app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java:1762/:1765` 与 `app/src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java:1125/:1128`：字幕与倍速入口的可见性依赖 `player().isVod()`，判假即隐藏。
  - `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:3127`、`:3212`：IJK 续播位置的取值按 `ijk.isVod()` 分支，判假会丢掉续播点。
  - `app/src/main/java/com/fongmi/android/tv/player/IntroSkipPlayback.java:293`、`:367`：片尾段落的 `duration > 0` 前置条件依赖时长链，配合 `PlayerManager.canSetEnding(...)` 的判定使片尾探测失效。

## 3. 修复

### 3.1 新增判定策略（新建文件）

`app/src/main/java/com/fongmi/android/tv/player/engine/TimelineStreamKindPolicy.java`：

- `public static final long LEGACY_LIVE_DURATION_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(1);`（沿用原门槛，不改阈值）
- `public static boolean isLive(long durationMs, boolean liveManifest)`：清单说是直播就是直播；时长未知返回 `false`；否则按阈值。
- `public static boolean isVod(long durationMs, boolean liveManifest)`：清单说是直播就不是点播；时长未知返回 `true`；否则按阈值。
- `private static boolean isUnknown(long durationMs)`：`durationMs <= 0 || durationMs == C.TIME_UNSET`。

### 3.2 四引擎改为委托

- `ExoPlayerEngine.java:714/:719`、`MpvPlayerEngine.java:214/:219`、`SystemPlayerEngine.java:101/:106`：
  `return TimelineStreamKindPolicy.isLive(player.getDuration(), player.isCurrentMediaItemLive());` /
  `return TimelineStreamKindPolicy.isVod(player.getDuration(), player.isCurrentMediaItemLive());`
- `IjkPlayerEngine.java:158/:163`：把 `player.getStreamSceneDecision().live()` 作为 `liveManifest` 传入，duration 取 `player.getDuration()`。
- 四个文件的 `java.util.concurrent.TimeUnit` import 已无其它使用点（改动前逐文件 grep 确认只有这两行用到），随之删除。

### 3.3 展示层兜底

- `PlayerManager.java` 新增嵌套 `public interface DurationFallback { long getDurationMs(); }` 与字段 `private DurationFallback durationFallback;`，`public void setDurationFallback(@Nullable DurationFallback)`。
- 新增 `private long resolveDuration()`：引擎 `getDuration() > 0` 直接用；否则取回调并在 try/catch 内 `Math.max(0, ·)`；回调为空或抛异常返回 0。
- `getDurationTime()` 改为 `Util.timeMs(resolveDuration())`；`getPositionTime(long delta)` 的上界改为 `duration > 0 ? duration : Long.MAX_VALUE`。
- `getDuration()` 保持原样直通 —— `getBufferedPercentage()`（`PlayerManager.java:784/:796`）与播放遥测（`:7782-7785` 对 `C.TIME_UNSET` 有独立判断）依赖真实引擎值，不能被兜底污染。

### 3.4 接线

- `app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java`：`onServiceConnected(...)` 里紧跟 `getSeekView().setProgressPlayer(player().getPlayer());` 之后调用 `player().setDurationFallback(this::getDurationFallbackMs);`；`releaseBinding()` 与 `onServiceDisconnected(...)` 原有 `setProgressPlayer(null)` 旁各撤掉一次（`if (player() != null) player().setDurationFallback(null);`）。
- 兜底值来自各页面已有的 `protected long getDurationFallbackMs()`（AV-PROGRESS-01 引入）：mobile / leanback 的 `VideoActivity` 返回本集历史时长，其余 `PlaybackActivity` 子类沿用基类默认 `C.TIME_UNSET` ⇒ `resolveDuration()` 回到 0，与改动前一致。

## 4. 契约测试

`app/src/test/java/com/fongmi/android/tv/player/engine/TimelineStreamKindPolicyTest.java`（JUnit4，7 例）：

- `unknownDurationIsVodWhenTheManifestIsNotLive`、`unknownDurationStillFollowsALiveManifest`：`C.TIME_UNSET` 的两种清单事实。
- `zeroDurationIsTreatedAsUnknown`：0 与 UNSET 同等对待。
- `shortKnownDurationIsStillLive`：30 秒仍是直播。
- `thresholdItselfIsNeitherLiveNorVod`：阈值本身既非 live 也非 vod（严格大于/小于）。
- `longKnownDurationIsVod`：15 分钟是点播。
- `liveManifestOverridesALongDuration`：清单优先于时长。

## 5. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 双 flavor 编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL（2m44s） |
| 定向单测 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.player.engine.TimelineStreamKindPolicyTest'` | BUILD SUCCESSFUL；XML `tests="7" failures="0" errors="0"` |

## 6. 未做与边界

- `SystemSimplePlayer` 没有像 MPV 那样的状态刷新定时器，「时长可能一直不发布」这一引擎侧场景未在本次处理：本次只在展示层与判定层承认「未知」。
- `mHistory.setDuration(...)` 与兜底之间存在循环依赖风险（历史时长既被展示层兜底读取，又由播放进度写回）：本次不改写入侧，若某集历史时长本身是错的，兜底会把它显示出来而不是修正它。
- 截图里的 toast「弹幕加载失败」在本仓库源码零命中（来自第三方吐司/弹幕库），与时长链路无关，未处理。
- 未改 `IjkStreamScenePolicy`（它自己已把 `C.TIME_UNSET` 归类为 unknown，只是 `IjkPlayerEngine` 之前没利用 `unknown` 结果）；未改 `CustomSeekView`（AV-PROGRESS-01 已覆盖）。
