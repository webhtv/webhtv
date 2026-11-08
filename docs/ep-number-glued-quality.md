# 集数解析：剥离粘连画质标记与标记链整体化

任务 ID：`ep-number-glued-quality` / 后续跟进会话 `ep-quality-chain-strip`
分支：`dev5` · 面向 `beta` 的 PR

## Recovery anchor

- 目标：`Util.getEpisodeNumber` 在文件名含粘连画质/编码标记（`4KHQHDR`、`1080pHEVC`、`HDR10`、`10bit`）时仍返回正确集号。
- 验收：`gradlew :app:testMobileArm64_v8aDebugUnitTest --tests '*UtilTest*'` 全部通过；既有无画质输入结果不变。
- 状态：实现完成，双轴评审通过并已修正评审指出的注释错误，等待提交/推送/PR。
- 下一步：提交、打恢复标签、推送 `dev5`、开 PR 到 `beta`。

## 问题

`preprocessEpisodeText` 原先把画质/编码/音轨/片源拆成 5 条互不覆盖的 `replaceAll`：

| 分支 | 覆盖 | 缺口 |
| --- | --- | --- |
| `(?i)(?<![a-z0-9])(?:2160|1080|720|480)p(?![a-z0-9])` | 裸分辨率 | 粘连时后随标记会触发链尾断言，整段不匹配 |
| `(?i)...(?:4K|2K|HD|SD|FHD|UHD)(?:[\s._-]?...)*(?![a-z0-9])` | 画质链 | 表内无 `8K`/`HQ`/`SDR`/`HDR10`/`10bit` |
| `\b(?:x264|x265|H264|H265|AVC|HEVC)\b` | 编码 | 大小写敏感，且 `H.264` 点号形式不匹配 |
| `\b(?:AAC|AC3|DTS|FLAC)\b` | 音轨 | 同上 |
| `\b(?:WEB[-._\s]*DL|BluRay|BDRip|REMUX|HDTV)\b` | 片源 | 同上 |

后果：`01 4KHQHDR(4.5 GB)` 完全不剥离，`4KHQHDR` 里的 `4` 被当成集号；`06 HDR10` 的 `10` 被当成集号。

## 方案

不再逐类打补丁，改为一条「标记链」正则一次剥离：链内允许零分隔符，链两端锚 ASCII 字母数字边界。既覆盖粘连写法，也把画质/编码/音轨/片源统一到一张表，避免重复维护。

`app/src/main/java/com/fongmi/android/tv/utils/Util.java:44-49`

```java
private static final Pattern QUALITY_NOISE_CHAIN = Pattern.compile(
        "(?i)(?<![a-z0-9])(?:(?:2160p|1080p|720p|480p|4K|8K|2K|UHD|FHD|HDR10\\+?|HDR|SDR|HD|HQ|SD|DV|10bit|8bit"
                + "|x264|x265|H\\.?264|H\\.?265|AVC|HEVC|AAC|AC3|DTS|FLAC|WEB[-._\\s]*DL|BluRay|BDRip|REMUX|HDTV)[\\s._-]?)+(?![a-z0-9])");
```

替换点 `Util.java:258-259`：

```java
// 移除画质、编码、音轨与片源标记（含互相粘连的链式写法，如 4KHQHDR、1080pHEVC）
processed = QUALITY_NOISE_CHAIN.matcher(processed).replaceAll(" ");
```

`getEpisodeNumber` 与 `preprocessEpisodeText` 其余 9 个步骤逐字节未变，无新增 import（`Pattern`/`Matcher` 已在 `Util.java:33-34`）。

### 边界语义

- 链尾 `(?![a-z0-9])` 是 ASCII 边界：`HDRip`、`SDTV`、`HDRipx`、`DVDRip` 这类拉丁正文词不被吞。
- 中文正文词前的 ASCII 标记仍会被剥离（`SD高达` 的 `SD`），这与旧的 `(?<![a-z0-9])...(?!\b)` 语义一致，**不是回归**。
- 编码/片源由 `\b` 改为字母数字边界后有意的宽容度提升：`Movie_x264_05` 旧实现误取 `264`，新实现取 `5`。

## 评审记录

| 轮次 | 维度 | 结论 |
| --- | --- | --- |
| 1 | Standards | 无硬性违规；4 条 smell，其中「同一 token 表在一行里写两遍」已由本常量消除 |
| 1 | Spec | 缺陷：分辨率粘连不支持、`HDR10`/`10bit` 缺失 → 已修 |
| 2 | Standards | 通过。确认常量组织与 `Util.java:38-43` 一致、5 合 1 与顺序执行等价、作用域闭合 |
| 2 | Spec | 实现达标；唯一缺陷是注释事实陈述与实测不符（`SD高达` 的 `SD` 实际被吞）→ 已修 |
| 2 复审 | 自检 | 注释收窄为「拉丁正文词受保护 / 中文正文词前 ASCII 标记仍被剥离」，并把恒真断言 `SD高达 第3集` 之外补上 `SDTV`/`HDRipx`/`DVDRip` 三条真正能守护边界的用例 |

## 验证

- `cmd /c gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests '*UtilTest*'` → BUILD SUCCESSFUL，`tests=11 failures=0 errors=0 skipped=0`。
- 评审方独立复核 104 条断言（90 条自动抽取 + 5 条具名探针 + 9 条规格输入）全部成立；65 条对抗串差分 10 处不同，无一处把正确集号改错。
- 无指数回溯：57/81/101 字符重复 token 串耗时 0–1 ms。

## 已知副作用（有意接受）

| 输入 | 旧 | 新 | 说明 |
| --- | --- | --- | --- |
| `05 X264` | -1 | 5 | 大小写不敏感化 |
| `05 H.264` | 264 | 5 | 点号形式纳入 |
| `Show.1080pHEVC.09` | 1080 | 9 | 粘连整体剥离 |
| `Show 8K` | 8 | -1 | 纯画质标题不再返回画质数字 |
| `Show 4K HDR10` | 10 | -1 | 同上 |

`-1` 本就是 `getEpisodeNumber` 的合法返回值，调用方 `bean/Episode.java:32`、`bean/Flag.java:185`、`history/HistorySourceResolver.java:294` 的契约未破。

## 未纳入本轮的既存缺陷

音频声道数字污染集号：`05 DDP5.1`、`05 AAC5.1`、`05 4K HEVC 10bit DTS-HD MA 5.1`、`05 2160p.WEB-DL.DDP5.1.Atmos.HEVC` 均返回 `1`（期望 `5`）。

机制：预处理后残留 `5.1`，被 `Util.java:43` `TRAILING_SEASON_EPISODE` 读成「第 5 季第 1 集」，或由 `Util.java:38` `EPISODE` 在 `.1` 上命中。与 `origin/beta` 逐字节相同，非本次改动引入。建议另开任务：token 表并入 `DDP|DD\+|EAC3|TrueHD|AV1|MPEG2|MA`，并在 `TRAILING_SEASON_EPISODE` 之前掩掉 `\d\.\d` 码率小数。

## 回滚

单一提交，无 schema / 依赖 / 二进制变更。`git revert` 该提交即可回到 5 条 `replaceAll` 的旧行为。
