# fix-episode-version-identity：同一 TMDB 集多版本的版本身份消歧

> **复评修订（C33，2026-10-02）**：本任务描述的「季号已知即启用 URL 消歧」在 dev2 合并 beta 复评中被判定误伤跨线路/跨源续播与源站刷新契约（换线路后 URL 必然不同，会被判换集并丢进度）。已改为：`matchesPlayback(Episode)` 恢复纯集号容错，消歧仅由调用方在确认「历史 URL 仍能定位到当前线路条目」（`Flag.containsEpisodeUrl`）时通过 `matchesPlayback(Episode, boolean versionAware)` 启用；选中定位由 leanback `getSelectedEpisodePosition` 的 URL 优先命中承担。详见 `docs/C33-beta-merge-review-dev2-20261002.md`。

## Recovery anchor

- **目标 / 验收标准**：同一 TMDB 季集号下存在多个版本（同名不同 URL 的源站条目）时，用户点第二个版本必须被认出为第二个版本，不再回落到第一个版本的选中/续播；同时不得破坏既有跨源续播容错契约。
- **范围**：`Episode.matches`/`matchesPlayback`/`sameTmdbVariant`、`Flag.find(Episode, boolean)` 与对应单测。
- **分支 / HEAD**：`dev2` @ `45d340418a62f7b6a844b5ed67510308c6d84cd3`。
- **验证**：`./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests ...HistoryPlaybackTest --tests ...EpisodeVersionIdentityTest` → BUILD SUCCESSFUL，77 用例（15 + 62）全通过，0 失败。
- **状态**：实现与验证完成，未提交。
- **下一步**：`task_guard.sh finish` 提交并打恢复标签。

## 问题现象

炫彩详情模式点击选集卡片跳到播放页后，用户点“第二版本”却播放/选中“第一版本”。

## 根因

`Episode.matchesPlayback`（及 `matches`）在双方都绑定 TMDB 集号时**直接** `return matchesNumber(other)`：

```java
if (hasTmdbEpisodeNumber() && other.hasTmdbEpisodeNumber()) return matchesNumber(other);
```

同一 TMDB 集（如 S1E3）的多个版本必然共享同一个 TMDB 季集号，因此 `matchesNumber` 恒为 `true`，URL 与源站集名从未参与比较——第二版本被判定为第一版本。

`Flag.find(Episode, boolean)` 的 TMDB 分支同样“集号相同即返回流中第一个”匹配条目，构成第二个覆盖点。

## 设计决策：按季号置信度分层

存在两条互相拉扯的契约：

1. **版本独立性**（本次报障）：同一 TMDB 集、URL 不同 → 应视为不同播放实体。
2. **跨源续播容错**（既有测试）：同一 TMDB 集号、URL 与集名都不同、且一侧季号未知 → 仍须视为同一集。

二者唯一的可分界信息是**季号是否已确认**。因此分层：

- **两侧季号均已知且相等**（确认“同一季同一集”）→ 才允许用 `sameTmdbVariant` 在版本层消歧：URL 精确相等优先；URL 缺失时退回原始条目文本（`getRawDisplayName()` = desc + 源站集名）；两者皆无可比信息时视为同集。
- **任一侧季号未知** → 无法断定是同集的不同版本还是跨源续播的同一集，维持原有集号容错语义。

### 被拒绝的备选

- **无条件按 URL 收紧**：直接破坏跨源续播契约（换源后 URL 必变），已由失败测试证伪。
- **用 `getDisplayName()` 作版本身份**：该字段可被刮削标题覆盖，不是稳定身份，故只用 `getRawDisplayName()`。

## 实现

`Episode.java`
- `matches` / `matchesPlayback`：TMDB 分支改为先 `matchesNumber` 硬校验，再 `!sameConfirmedTmdbSeason(other) || sameTmdbVariant(other)`。
- 新增 `sameConfirmedTmdbSeason(Episode)`：双方 `getSeasonNumber() >= 0` 且相等。
- 新增 `sameTmdbVariant(Episode)`：URL 精确相等 > 原始条目文本相等 > 视为同集。

`Flag.java`
- `find(Episode, boolean)` 新增 URL 优先定位块，但**跳过 TMDB 位置冲突的条目**：源站播放地址可能跨季/跨集复用，若按 URL 盲命中会定位到另一季条目。冲突时交回后续季集号定位。

## 验证与回归

| 阶段 | 结果 |
|---|---|
| 修复前 | 4 失败：`flagFindPrefersTmdbPositionOverConflictingUrl`、`flagFindRejectsSingleBoundEpisodeFromDifferentTmdbSeason`（URL 块越权命中）、`playbackEpisodeMatchAllowsUnknownSeasonWhenTmdbEpisodeNumberMatches` 与 `findWithoutUrlKeepsStrictMissSemantics`（测试侧数据/重载误用） |
| 修复后 | BUILD SUCCESSFUL，77/77 通过 |

新增回归覆盖：季号已确认时按 URL 区分版本、季号未知时保留集号容错、URL 跨季复用不得越权命中、`find` 严格未命中语义（须用多集线路，单集线路会无条件返回该集）。

## 风险与回滚

- 风险：分层依赖 `TmdbEpisode.getSeasonNumber()` 的准确性；季号误报为已确认时可能收紧过度。缓解：仅当两侧 `>= 0` 且相等才消歧，未知季（`-1`）一律走原容错路径。
- 回滚：`git revert` 本次提交即可；无数据迁移、无持久化格式变更。

## 涉及文件

- `app/src/main/java/com/fongmi/android/tv/bean/Episode.java`
- `app/src/main/java/com/fongmi/android/tv/bean/Flag.java`
- `app/src/test/java/com/fongmi/android/tv/bean/HistoryPlaybackTest.java`
- `app/src/test/java/com/fongmi/android/tv/bean/EpisodeVersionIdentityTest.java`
