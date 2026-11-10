# SUB-MATCH-PRECISION 自动匹配的多维精确匹配

## Recovery anchor

- 目标：自动匹配要能把「搜得到但匹配不到」的情况修好，并用尽可能多的维度（剧名、年份、语言、时长、季集、格式）精确区分同一部片子的多个字幕版本。
- 验收：
  1. 同一部片子有几十个版本字幕时，自动匹配能选中最优的一条并直接应用，不再返回 null。
  2. 标题对不上的候选永远不被选中。
  3. 时长不同的候选能被区分开。
  4. 电视版字幕搜索对话框的剧集名在深色主题下清晰可见。
- 当前状态：原多维匹配及 C122 标题/季集身份、动态时长补修已交付；修复提交 `88ea7f4242219abd7c647a69c525f10443651d8d` 与恢复标签已推送，[PR #540](https://github.com/Silent1566/webhtv/pull/540) 到 beta。双变体定向验证与需求审查已闭合，设备冒烟未执行。
- 下一步：等待 PR 审核；设备《战狼2》播放冒烟保留为明确未验证边界。交付记录见 `docs/C122-beta-merge-review-dev4-20261108.md`。

## 1. 用户反馈

> 把电视版字幕搜索界面的输入框剧集名称默认改成白色现在看不清。
> 优化自动匹配逻辑现在这样匹配不到，但是能搜到很多结果，匹配机制可以参考一个tmdb相关实现，我是在emulator-5554模拟器测试的。
> 尽可能的带上更多的信息做最精准的匹配，比如：剧名，年份，语言，时长等等。

设备日志缓冲已滚动（App 进程已退出），`adb logcat -b all` 里没有残留的 `SubtitleMatch` 记录，因此结论来自代码路径而非该次日志。

## 2. 根因

### 2.1 输入框看不见

`SubtitleManualSearchDialog.java:60-61` 把文字写死成 `0xFF202124`（近黑）、hint 写死成 `0xFF5F6368`（深灰）。而对话框底色由 `ThemeController` 决定，深色主题下就是深底 + 深字。截图里「战狼2 2017」正是这个色值。

### 2.2 自动匹配放弃

`SubtitleRanker.pickBest` 原有：

```java
if (!context.hasTmdbIdentity() && ranked.size() > 1 && best.getScore() - ranked.get(1).getScore() < 5) return null;
```

「第二名咬得紧就放弃」——本意是遇到真歧义不要乱选。但同一部片子有几十个版本（简/繁、ASS/SRT、不同压制组）时，它们标题相同、年份相同、语言相同，分差天然小于 5，于是永远放弃。

两条路径的差异正好对上用户观察：

| | 代码 | 结果 |
| --- | --- | --- |
| 手动搜索 | `SubtitleMatchService.manualSearch` → 只 `noMatch(ranked, ...)` 列出全部候选 | 56 条可见可选 |
| 自动匹配 | `matchContext` → `ranker.pickBest` | null → `below_threshold` |

### 2.3 时长维度缺失

`SubtitleCandidate` / `SubtitleContext` / `SubtitleRequest` 都没有时长字段。Xunlei 虽然拿到了 `duration`，只用于拼展示串（`releaseInfo` 里的 `"75m"`）和解码 payload，没有进入排序。所以「标题、年份、语言全一样」的两个候选无法被区分。

## 3. 决策与实现

### 3.1 UI

改用主题语义色：文字 `colorOnSurface()`、hint `colorOnSurfaceVariant()`。写死白色会在浅色主题下变成白底白字，用主题色才不会。

### 3.2 标题改为分层相似度（参考 `TmdbMatcher.titleSimilarityScore`）

- 精确相等：满分
- 包含关系：按「标题字符占候选串比例」给分
- 其余：Levenshtein 距离递减

标题维度权重最高（满分 40），其余维度（年份 14、语言 12、格式 10、季集 30、时长 24）作为次级信号。候选串取自 `displayName + releaseInfo`，并同时比正名、原名、全部别名。

### 3.3 取消「分差不足就放弃」

`pickBest` 现在只有两条拒绝条件：

1. 标题完全没出现在候选里（新加的硬条件，防止为了「选最优」而挂别人的字幕）。
2. 最高分低于准入阈值。

阈值不再随候选数量变化：候选多寡反映的是同一部片子有多少版本，不是匹配可信度。旧实现在「年份未知且候选多于一个」时把线抬到 78，正是同片多版本场景的误伤。

### 3.4 时长贯通

时长从播放器一路传到排序器：

```text
PlayerManager.getDuration()
  → SubtitlePlaybackSession.playbackDurationMs(host)
  → SubtitleRequestFactory.create(..., durationMs)
  → SubtitleRequest.durationMs
  → SubtitleContextBuilder
  → SubtitleContext.durationMs
```

候选侧：`SubtitleCandidate.durationMs`，由 Xunlei provider（`duration` 字段）与脚本协议（`duration`）填充。

打分：差值 ≤5s +24、≤30s +18、≤2min +8、≤10min −6、更大 −20。任何一方时长为 0 时该维度跳过（不惩罚），因为 provider 是否给时长不受控制。

### 3.5 兼容性处理

`SubtitleRequestFactory.create` 新增带时长的重载，旧 8 参签名保留并委托给新的，避免所有既有调用点与测试替身失效。`withDurationMs` 用 `toBuilder()` 复制，不把时长塞进每个构造点。

## 4. 改动清单

| 文件 | 改动 |
| --- | --- |
| `SubtitleManualSearchDialog.java` | 文字/hint 改用主题语义色 |
| `SubtitleRanker.java` | 分层标题相似度、时长打分、`pickBest` 去掉分差放弃、加标题硬条件、阈值不再随候选数变化 |
| `model/SubtitleCandidate.java` | 新增 `durationMs`（保留旧构造器重载） |
| `model/SubtitleContext.java` | 新增 `durationMs` |
| `model/SubtitleRequest.java` | 新增 `durationMs`、`withDurationMs`、`toBuilder` |
| `SubtitleRequestFactory.java` | 新增带时长重载，旧签名委托 |
| `SubtitleContextBuilder.java` | 传递时长 |
| `SubtitlePlaybackSession.java` | 从播放器取时长传入工厂 |
| `XunleiSubtitleProvider.java` | 候选带上 `duration` |
| `SubtitleProtocolMapper.java` | 脚本源候选解析 `duration` |

## 5. 验证

- `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`：通过。
- `subtitle.*` + `player.track.*` + `playback.*` 单测：全绿。
- 新增 4 个回归测试：
  - `pickBest_choosesBestVersionWhenSameMovieHasManySubtitleVersions`（直接用截图里的 5 条真实候选）
  - `pickBest_prefersDurationMatchAmongIdenticalTitles`
  - `pickBest_rejectsCandidateWhoseTitleDoesNotMatch`
  - `rank_prefersSimplifiedChineseWhenThatIsPreferred`
- 反向插桩（逐个确认变红，随后全部还原）：
  - 恢复旧「分差 < 5 就放弃」→ 同片多版本测试失败。
  - 关掉时长打分 + 去掉标题硬条件 → 时长测试与标题拒绝测试双双失败。
- 未执行：真机/模拟器冒烟。这是唯一未验证环节。

## 6. 已知残留

- 时长只有 Xunlei 与脚本源提供，Assrt / Shooter 的候选该维度恒为 0，此时排序退化为其它维度。这是数据源限制，不是缺陷。
- 时长比较是「影片总时长 vs 字幕标注时长」的近似，剪辑版与院线版可能差几分钟；容差已按 5s/30s/2min/10min 分级，不会把对得上的判成错。
- 手动搜索列表的排序同步受益，但列表本身不筛选，用户仍可看到全部候选。