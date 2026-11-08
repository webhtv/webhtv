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
- 消费方：`app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:836`（调试摘要）、`app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` 的 `getTimelineDurationTime()` 与 `getPositionTime(...)`、`app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java:1710/:1720` 的手势时长文本。

### (B) 判定层把「未知」当成直播

- 改前四个内核的判定一致地写成「时长小于 1 分钟即直播」：
  - （改前行号）`app/src/main/java/com/fongmi/android/tv/player/engine/ExoPlayerEngine.java:715/:720`
  - （改前行号）`app/src/main/java/com/fongmi/android/tv/player/engine/MpvPlayerEngine.java:215/:220`
  - （改前行号）`app/src/main/java/com/fongmi/android/tv/player/engine/SystemPlayerEngine.java:102/:107`
  - `app/src/main/java/com/fongmi/android/tv/player/engine/IjkPlayerEngine.java:158/:163`（IJK 走 `player.getStreamSceneDecision().live()/.vod()`）
- `C.TIME_UNSET` 恒小于该阈值 ⇒ `isLive()` 为真、`isVod()` 为假。下游后果：
  - `app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java:1762/:1765` 与 `app/src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java:1125/:1128`：字幕与倍速入口的可见性依赖 `player().isVod()`，判假即隐藏。
  - `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:2819/:3153/:3238`：IJK 续播位置的取值按 `ijk.isVod()` 分支，判假会丢掉续播点。
  - `app/src/main/java/com/fongmi/android/tv/player/IntroSkipPlayback.java:293`、`:367`：片尾段落的 `duration > 0` 前置条件依赖时长链，配合 `PlayerManager.canSetEnding(...)` 的判定使片尾探测失效。

## 3. 修复

### 3.1 新增判定策略（新建文件）

`app/src/main/java/com/fongmi/android/tv/player/engine/TimelineStreamKindPolicy.java`：

- `public static final long LEGACY_LIVE_DURATION_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(1);`（沿用原门槛，不改阈值）
- `public static boolean isLive(long durationMs, boolean liveManifest)`：清单说是直播就是直播；时长未知返回 `false`；否则按阈值。
- `public static boolean isVod(long durationMs, boolean liveManifest)`：清单说是直播就不是点播；时长未知返回 `true`；否则按阈值。
- `private static boolean isUnknown(long durationMs)`：`durationMs <= 0`（`C.TIME_UNSET` 是负数，已被该条件覆盖，因此不再需要 media3 的 `C` import）。同节补充：`isVod` 用 `>=`、`isLive` 用 `<`，恰好等于阈值时按点播处理，二者互补，不存在既非直播也非点播的第三态。

### 3.2 四引擎改为委托

- `ExoPlayerEngine.java:714/:719`、`MpvPlayerEngine.java:214/:219`、`SystemPlayerEngine.java:101/:106`：
  `return TimelineStreamKindPolicy.isLive(player.getDuration(), player.isCurrentMediaItemLive());` /
  `return TimelineStreamKindPolicy.isVod(player.getDuration(), player.isCurrentMediaItemLive());`
- `IjkPlayerEngine.java:157/:162`：改为 `IjkStreamScenePolicy.isLive/isVod(player.getStreamSceneDecision(), player.getDuration())`，把完整 Decision 与实时时长一起交给策略层。
- `IjkStreamScenePolicy.isLive/isVod(Decision,long)`：清单权威时直接采纳清单；否则回落 `TimelineStreamKindPolicy`，且 `isVod = !isLive`，对外永不出现第三态。
- 四个文件的 `java.util.concurrent.TimeUnit` import 已无其它使用点（改动前逐文件 grep 确认只有这两行用到），随之删除。

### 3.3 展示层兜底

- `PlayerManager.java` 复用 `java.util.function.LongSupplier`（不新增自定义接口）：`private LongSupplier durationFallback;` 与 `public void setDurationFallback(@Nullable LongSupplier)`。
- 新增 `private long resolveDuration()`：引擎 `getDuration() > 0` 直接用；否则先取字段局部快照再调用回调，并在 try/catch 内 `Math.max(0, ·)`；回调为空或抛异常返回 0。
- `getDurationTime()` 改为 `Util.timeMs(resolveDuration())`；`getPositionTime(long delta)` 的上界改为 `duration > 0 ? duration : Long.MAX_VALUE`。
- `getDuration()` 保持原样直通 —— `getBufferedPercentage()`（`PlayerManager.java:784/:796`）与播放遥测（`:7809/:7845` 对 `C.TIME_UNSET` 有独立判断）依赖真实引擎值，不能被兜底污染。

### 3.4 接线

- `app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java`：`onServiceConnected(...)` 里紧跟 `getSeekView().setProgressPlayer(player().getPlayer());` 之后调用 `player().setDurationFallback(this::getDurationFallbackMs);`；`releaseBinding()` 与 `onServiceDisconnected(...)` 原有 `setProgressPlayer(null)` 旁各撤掉一次（`if (player() != null) player().setDurationFallback(null);`）。
- 兜底值来自各页面已有的 `protected long getDurationFallbackMs()`（AV-PROGRESS-01 引入）：mobile / leanback 的 `VideoActivity` 返回本集历史时长，其余 `PlaybackActivity` 子类沿用基类默认 `C.TIME_UNSET` ⇒ `resolveDuration()` 回到 0，与改动前一致。

### 3.5 IJK 非分段资源的协议直播证据（第三轮评审补充）

- `IjkStreamScenePolicy` 非分段分支把 `classification.streamKind() == StreamKind.LIVE` 当直播证据（`PlaybackResourceClassifier` 的 `protocolFromRequest` 对 RTSP/RTMP 直接给出 LIVE），对应 `Reason.PROTOCOL_LIVE`。
- 原先的 `legacy-duration-ambiguous` 枚举值随第三态一并删除（阈值本身现按点播处理，该结果不可达）。

## 4. 契约测试

`app/src/test/java/com/fongmi/android/tv/player/engine/TimelineStreamKindPolicyTest.java`（JUnit4，7 例）：

- `unknownDurationIsVodWhenTheManifestIsNotLive`、`unknownDurationStillFollowsALiveManifest`：`C.TIME_UNSET` 的两种清单事实。
- `zeroDurationIsTreatedAsUnknown`：0 与 UNSET 同等对待。
- `shortKnownDurationIsStillLive`：30 秒仍是直播。
- `thresholdItselfIsVodAlthoughItIsNotLive`：阈值本身非 live 但按 vod 处理（`isVod` 用 `>=`，与 `isLive` 互补）。
- `longKnownDurationIsVod`：15 分钟是点播。
- `liveManifestOverridesALongDuration`：清单优先于时长。

`app/src/test/java/com/fongmi/android/tv/player/engine/IjkStreamScenePolicyTest.java` 补充 IJK 侧契约（清单权威、非分段回落）：

- `thresholdDurationIsVodOnceItIsNotShorterThanTheLegacyBoundary`：阈值时长按点播处理（`decision.vod()` 为真，公开 `isVod` 也为真）。
- `liveProtocolStaysLiveWithoutManifestOrDuration`：RTMP 请求即使时长未知（无清单证据）仍按直播处理，`Reason.PROTOCOL_LIVE`。
- `nonSegmentedResourcesKeepLegacyDurationCompatibility`：未知/30 秒/60 秒整/120 秒四档的兼容行为。
- 其中 `authoritativeManifestBeatsTheDurationHeuristic` 与 `unknownDurationIsVodOnceTheManifestGivesNoLiveEvidence` 是真正能证伪旧实现（未知时长判直播）的判别器。

## 5. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 双 flavor 编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL（2m44s） |
| 定向单测 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.player.engine.TimelineStreamKindPolicyTest'` | BUILD SUCCESSFUL；XML `tests="7" failures="0" errors="0"` |
| 第三轮合并验证 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests '...IjkStreamScenePolicyTest' --tests '...TimelineStreamKindPolicyTest' --tests '...VideoActivitySeekProgressSourceTest' && :app:testMobileArm64_v8aDebugUnitTest --tests '...IjkStreamScenePolicyTest' --tests '...TimelineStreamKindPolicyTest'` | BUILD SUCCESSFUL（1m 30s / 1m 21s）；XML `failures="0" errors="0"` |

## 6. 未做与边界

- `SystemSimplePlayer` 没有像 MPV 那样的状态刷新定时器，「时长可能一直不发布」这一引擎侧场景未在本次处理：本次只在展示层与判定层承认「未知」。
- `mHistory.setDuration(...)` 与兜底之间存在循环依赖风险（历史时长既被展示层兜底读取，又由播放进度写回）：本次不改写入侧，若某集历史时长本身是错的，兜底会把它显示出来而不是修正它。
- 截图里的 toast「弹幕加载失败」在本仓库源码零命中（来自第三方吐司/弹幕库），与时长链路无关，未处理。
- 无清单证据且协议不是 RTSP/RTMP 的直播源（例如 http 上的 .flv 直播流）在时长未知时仍会被判成点播，这是刻意的取舍：协议直播证据只覆盖 `PlaybackResourceClassifier` 明确给出 LIVE 的协议。
- `IjkPlayerEngine.isLive/isVod` 这个生产入口本身没有直测（构造它需要真实 IJK 内核），行为由 `IjkStreamScenePolicy` 的契约测试覆盖。
- `IjkStreamScenePolicy.isLive/isVod(Decision,long)` 在 HLS/DASH 清单观测到达之前（`actualManifest=false`
  ⇒ `manifestRejection` 返回 `MANIFEST_UNKNOWN` ⇒ `authoritative=false`），公开判定回落到时长启发式：
  此时时长未知的 HLS 直播会被判成点播，直播页左右键是 seek、重试次数 2 次。该窗口与改动前的基线逐字相同，
  不是本次引入。
- `PlayerManager.isLive()/isVod()`（`PlayerManager.java:723-730`）在 `engine == null`（播放器尚未就绪）时
  两者同为 `false`，是这个门面上唯一残留的第三态窗口；本次不改其语义（改成 `engine == null || engine.isVod()`
  会让未就绪时提前显示字幕/倍速入口），仅记录。
