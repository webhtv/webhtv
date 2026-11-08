# AD-NOTICE-01 去广告成功提示补充总广告时长

状态：已实现并完成定向验证 + 设备实测；C53 复评发现并修复 2 个时长格式化缺陷（见文末「C53 复评补充」），待交付 PR。

## 恢复锚点

- 目标（用户原始需求）：目前去广告成功后提示信息只包含了总切片数，现在再新增总广告时长。
- 验收标准：
  1. 去广告成功后的提示同时包含**广告片段数（切片数）**与**总广告时长**；
  2. 三条 HLS 去广通道（nano `/m3u8` 代理、Exo `ExoHlsAdblockDataSource`、MPV/IJK `MpvHlsProxy`）行为一致；
  3. 结构化规则通道与 legacy 兜底通道**都**显示时长（原实现只有结构化通道显示，兜底通道只有条数——这正是用户看到「只包含总切片数」的根因）；
  4. 时长未知（0/NaN/负数）时退回只显示条数，不出现「0.0 秒」误导；
  5. 两个 flavor 编译通过、定向单测通过、设备实测 toast 文案含时长。
- 分支/基线：`dev1`，任务起点 `fe1f725a12925b9d3ce84801f52c9d2a047e26b6`。
- 保护 dirty：任务开始时工作区干净，protected 为 0。
- 回滚锚点：本任务提交及其 `recovery/AD-NOTICE-01/*` 标签。

## 缺陷定位

原提示文案在三处各自内联（重复三份）：

```java
String message = clean.structured() && clean.removedDurationSec() > 0
        ? String.format(Locale.US, "已跳过 %d 个广告片段（%.1f 秒）", removed, clean.removedDurationSec())
        : "已跳过 " + removed + " 个广告片段";
```

关键判据是 `clean.structured()`。`HlsAdblockPipeline.apply` 在结构化规则未命中而 legacy 兜底命中时返回
`structured=false, legacy=true`，此时**即使 `removedDurationSec` 已经算好**（
`HlsAdblockPipeline.legacyRemovedSegments` 逐片累加 `durationSec`），文案仍走无时长分支。
设备侧日志可直接复现：`removed=2 structured=false legacy=true` → 提示只有条数。

## 方案

- 新增 `HlsAdblockNotice.message(int removedSegments, double removedDurationSec)` 作为唯一文案入口，
  三条通道统一调用；只要时长 > 0 就追加「总广告时长 X」，不再以 `structured` 为条件。
- `HlsAdblockNotice.durationText` 负责时长呈现：< 60s 用 `%.1f 秒`，≥ 60s 用「N 分钟 / N 分 M 秒」，
  避免长广告块显示成 `185.0 秒`。
- 文案格式：`已跳过 2 个广告片段，总广告时长 45.5 秒`（保留原有条数表述，用户已熟悉的措辞不变）。

## 变更文件

| 文件 | 变更 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/utils/HlsAdblockNotice.java` | 新增 `message(...)` 与 `durationText(...)` |
| `app/src/main/java/com/fongmi/android/tv/server/process/M3u8.java` | 改用共享文案入口 |
| `app/src/main/java/com/fongmi/android/tv/player/exo/ExoHlsAdblockDataSource.java` | 改用共享文案入口 |
| `app/src/main/java/androidx/media3/mpvplayer/MpvHlsProxy.java` | 改用共享文案入口 |
| `app/src/test/.../HlsAdblockNoticeTest.java` | 新增文案/时长/分钟进位/三通道契约测试 |
| `app/src/test/.../HlsAdblockPipelineTest.java` | 结构化与 legacy 两条通道都断言含时长 |
| `app/src/test/.../ExoHlsAdblockDataSourceTest.java` | 断言精确文案（原为 `startsWith("已跳过 ")` 弱断言） |

## 验证证据

### 定向单测（mobile + leanback 双 flavor，各 15 项）

```bash
bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests com.fongmi.android.tv.utils.HlsAdblockNoticeTest \
  --tests com.fongmi.android.tv.utils.HlsAdblockPipelineTest \
  --tests com.fongmi.android.tv.player.exo.ExoHlsAdblockDataSourceTest
```

`BUILD SUCCESSFUL`；XML 结果：两个 flavor 均 `tests=6/6/3, failures=0, errors=0`（合计 30 项）。
其中 `everyHlsAdblockChannelUsesTheSharedDurationNotice` 为源码契约测试，锁死三条通道必须走共享入口
且不得再出现无时长的内联文案。

### 设备实测（`192.168.50.3:5555`，LIO-AN00 / Android 9，mobile debug 覆盖安装）

用设备自身 `/m3u8` 代理触发真实去广链路，读取系统 Toast 队列中应用提交的原文
（Android 会打印 `Toast callstack! strTip=...`）：

| 场景 | 清单广告片段 | 设备日志 `strTip` | 日志旁证 |
| --- | --- | --- | --- |
| 修复前（旧 APK） | 2 片 | `已跳过 2 个广告片段` | 截图 `/tmp/adnotice/before_legacy.png`，无时长 |
| 修复后 legacy 兜底 | 2 片 × (20.0 + 25.5)s | `已跳过 2 个广告片段，总广告时长 45.5 秒` | `removed=2 structured=false legacy=true` |
| 修复后 legacy 兜底（单广告） | 1 片 × 7.0s | `已跳过 1 个广告片段，总广告时长 7.0 秒` | `removed=1 structured=false legacy=true` |

调用栈确认文案来自本任务改动的入口：

```text
at com.fongmi.android.tv.utils.Notify.show(Notify.java:55)
at com.fongmi.android.tv.server.process.M3u8.lambda$recordAndNotify$0(M3u8.java:120)
```

结构化通道在设备上未命中（内置 `hls_rules.json` 的 `rules` 为空数组，接口规则为用户自定义 host 规则），
因此结构化通道的时长断言由单测覆盖：`HlsAdblockPipelineTest` 对 `outcome.structured()==true` 的结果
断言 `已跳过 1 个广告片段，总广告时长 7.0 秒`。

### 产物核对

安装后的 APK dex 中只剩新文案 `总广告时长`，旧的 `已跳过 %d 个广告片段（%.1f 秒）` 已不存在；
源码中 `个广告片段（` 残留数为 0。

## 影响与风险

- 仅改提示文案构造，不触碰净化判据、删除阈值、回退闸门、序号重写或统计写入，播放行为不变。
- 统计写入（`AdBlockStatsStore.recordBlocks`）与去抖（`HlsAdblockNotice.shouldNotify`）均未改动。
- 文案为硬编码中文，与三条通道原实现一致（原本就是硬编码字符串，未走 `strings.xml`），未新增本地化缺口。
- MPV 通道仅在「已产出有效删除计划」时提示，逻辑保持原样。

## C53 复评补充（2026-10-09）

`docs/C53-beta-merge-review-dev1-20261009.md` 的复评在**时长文案格式化**上发现 2 个真实缺陷并已修复（同一提交）：

- **F1 时长跨单位边界自相矛盾**：原 `durationText` 用未舍入的原值分档，`[59.95, 60)` 被格式化成 `60.0 秒`（负对照实测），与仓库既有约定 `AdBlockTimeFormatter.formatSeconds`（`59.96 → 00:01:00.0`）冲突。已改为「先按 0.1 秒粒度舍入再分档」。
- **F2 低于显示粒度仍显示「0.0 秒」**：本文件验收标准第 4 条要求不出现「0.0 秒」误导，但 `0 < d < 0.05` 的正值仍会走秒档并被 `%.1f` 格式化成 `0.0 秒`（负对照实测）。已改为显示粒度以下返回空串、退回只显示条数。

两条缺陷均新增锁定用例（`HlsAdblockNoticeTest` 6 → 8 项）；修复后双 flavor 定向用例 34 项、双 flavor 全量 JVM 套件（leanback 4131 / mobile 4969）零失败。上文「缺陷定位 / 方案 / 变更文件 / 验证证据」的设备实测结论不受影响（文案主体与三条通道接线未变）。

## 下一动作

随 `docs/C53-beta-merge-review-dev1-20261009.md` 一并提交、推送 `dev1` 并创建 `dev1 -> beta` PR（只创建不合并）。
