# C64 · dev2 合并 beta（PR#439）与季集交接改动复评

- 任务 ID：`C64-beta-merge-review-dev2-20261012`
- 分支：`dev2`；合并前 dev2 tip = `d5ceea9682`（本任务上一阶段提交，见 `docs/` 中 EPISODE-CARD-HANDOFF 记录）
- 合并对象：`origin/beta` = `b0b7f1d78b`（Merge pull request #439 from Silent1566/dev4）

## 1. 合并范围

`git merge --no-commit --no-ff origin/beta`，0 冲突，MERGE_HEAD 即 beta tip。beta 侧相对 dev2 上一合并点（`92a0ca0bbe`）新增 4 个提交：

| 提交 | 说明 |
| --- | --- |
| `b0b7f1d78b` | Merge pull request #439 from Silent1566/dev4 |
| `139f2ab5d1` | docs(c63): 记录 dev4 合并评审交付坐标 |
| `a25ac9dd72` | merge: 合并 origin/beta 最新代码（PR#438 dev1 Android 7 兼容修复）并复评 dev4 已提交未推送的 TV 对话框取色修复 |
| `4c5f4dc842` | fix(theme): TV 对话框面板与前景取自同一张色表 |

带入路径 6 个（3 源 + 2 测试 + 2 文档）：`ThemeController.java`、`ThemeBinderContractTest.java`、`ThemeControllerContractTest.java`、`TvDialogPaletteModeTest.java`、`docs/C63-*.md`、`docs/TV-DIALOG-PALETTE-NIGHT-20261012.md`。

## 2. 零复活 / 零丢失（三层证据）

用户明确要求：远端已移除/回退的提交不得顺带提交上去。

| 层 | 判据 | 结果 |
| --- | --- | --- |
| 1 · 净差异 | `git diff --name-only origin/beta` | 仅 dev2 自身 4 个路径，无 beta 之外的内容 |
| 2 · 零丢失（blob 级） | beta tip 5047 文件 vs 合并结果树逐一比对 | 缺失 **0**、逐字节一致 **5043**，差异仅 dev2 自身 4 路径；且 beta 相对 merge-base 从未改过这 4 路径（`git diff 92a0ca0bbe origin/beta -- <4 paths>` 为空） |
| 3 · 零复活（严格） | 27 个单亲回退提交共 4472 条 (文件,行) 删除记录 ∩ dev2 自身 282 条新增记录 | 交集 **0** |

判据口径说明：第 3 层必须按「文件 + 行」而不是仅按行内容求交集——仅按行内容会命中 `import java.util.LinkedHashMap;`、`}`、`/**` 这类在多个文件里反复删除又出现的样板行，产生假阳性（首轮用内容口径得到 53/9 条命中，逐条核查全部为样板行与 dev2 自己的新增 import）。

## 3. 复评：dev2 已提交的季集交接改动

复评对象是上一阶段提交 `d5ceea9682`（详情页已解析季集随详情快照交接给播放页 + 单季剧季号兜底）。

### 第 1 轮：发现 3 项真实问题

| 编号 | 严重度 | 问题 | 事实链 |
| --- | --- | --- | --- |
| F1 | 阻塞 | 单季兜底覆盖用户显式「保持原始剧集列表」（MANUAL_FLAT） | `TmdbSeasonResolver` 只在 MANUAL_FLAT 时返回 `Status.FLAT`（`resolve` 128-130、`resolveManual` 211-213）；旧 `currentEpisodeMetadataSeason()` 对非 RESOLVED 一律返回 null＝不逐集绑定。兜底只要 TMDB 恰有一个正片季就返回该季 → `refreshEpisodeMetadataAfterBinding` 改走单季路径 → `applyEpisodeTitles` 写 `tmdbEpisode`/覆盖 displayName，并 `TmdbEpisodeSorter.sort` **原地重排**选集。同时与详情页相反：详情页 `availableSeasonNumbers` 对 MANUAL_FLAT 返回空、不画卡片。 |
| F2 | 重要 | 单季兜底覆盖「已看到相反证据」的 AMBIGUOUS | 解析器在 151-177 行对 `multiple_explicit_seasons`、`explicit_season_missing_from_tmdb`、`title_and_source_season_conflict`、`title_season_missing_from_tmdb` 故意返回 AMBIGUOUS 拒绝绑定；兜底无视该证据，把第 1 季标题按集号挂到第二季源列表。 |
| F3 | 重要 | 单季兜底把 MULTI_SLICE 的分段结论降级成单季路径 | 切片路径以 null 作为季号一致性校验的期望值（`applyEpisodeTitlesForSlices` 传 null），兜底返回非 null 会让调度侧改走单季绑定、执行侧切片校验又必然否决 → 整个分段（含特别篇）一集都绑不上。 |

### 修复

F1+F2 同一处修复，F3 一行守卫，均在 `TmdbUIAdapter.soleOrdinarySeason()`：

```java
private Integer soleOrdinarySeason() {
    if (isMultiSliceResolution()) return null;                       // F3
    if (seasonResolution != null) {                                   // F1 + F2
        TmdbSeasonResolver.Source source = seasonResolution.getSource();
        if (source != TmdbSeasonResolver.Source.REQUEST
                && source != TmdbSeasonResolver.Source.NONE) return null;
    }
    if (seasonOptions == null || seasonOptions.isEmpty()) return null;
    ...
}
```

设计取舍：兜底只服务「解析器没有可用结论、且没有相反证据」的两条路径——`Source.REQUEST`（请求季不在 TMDB）与 `Source.NONE`（源站路径 `applySourceBundle` 显式传 `allowHeuristicGuessing=false`）。其余来源都代表解析器已看到相反证据或用户显式选择，覆盖只会帮倒忙。

附带修正：`seedSeasonEpisodes` 的注释声称「不会发起网络请求」，而 `loadSource` 新调度在内存未命中时仍会走一次请求，注释与实现不符，已改为如实描述。

### 第 2 轮：修复后复审

- 逐 source 推演：`REQUEST` / `NONE` 仍可兜底（本次要救的两条路径未被误伤）；`MANUAL_FLAT`(FLAT)、`MANUAL`/`MANUAL_MULTI_SLICE`、`EXPLICIT*`、`TITLE` 及各类 AMBIGUOUS 一律不兜底。
- 代次校验自洽：MULTI_SLICE 时调度侧与 `applyEpisodeTitlesForSlices` 都期望 null；RESOLVED 时两侧都取 `selectedSeason`。
- 无新增阻塞问题。

## 4. 回归锁

`TmdbDetailCacheTest` 由 6 例扩到 **14 例**（双 flavor 全绿）：

- 缓存层 11 例：交接快照携带、防御性拷贝（外层 + 内层不可变）、空季与负季号过滤、无季集返回 null，以及既有语言/条目匹配用例。
- 消费端源码守卫 3 例：两条 `load` 入口都 seed 且键与 `seasonEpisodes()` 同源、`loadSource` 补绑定、`soleOrdinarySeason` 的 MULTI_SLICE 守卫与来源白名单。

命名冲突说明：`app/src/testMobile/java/.../TmdbUIAdapterTest.java` 在 HEAD 中已存在同名同包类，共享源集不能再建同名类（mobile 编译会报「类重复」），故消费端守卫并入 `TmdbDetailCacheTest`。

## 5. 已知限制（不在本任务范围）

源站 payload 分支不消费交接快照：`VideoActivity`（leanback/mobile）在 `TmdbSourceState.RENDERABLE && sourceBundle != null` 时走 `loadSource(...)`，只有 else 分支消费 `mFastTmdbDetailCache`。对「payload 只带季集数、不带 episodes」的站点，详情页已取过的季被丢弃，播放页补绑定会在内存未命中时再发一次同季请求（收益②成立、收益①不成立）。修复需改 `VideoActivity` 两个 flavor，未纳入本任务。

## 6. Recovery anchor

- 目标：把 `origin/beta` 最新合入 `dev2`（不回退已移除提交）、复评 dev2 已提交未推送改动、验证通过后提交并推送、开 PR 到 beta。
- 状态：合并已完成待验证提交；复评 2 轮通过（F1/F2/F3 已修并加锁）。
- 下一步（唯一动作）：合并后完整验证通过 → 提交合并 commit → 推送 dev2 → 创建 PR 到 beta（只创建，不合并）。