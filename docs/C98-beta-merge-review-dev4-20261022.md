# C98-beta-merge-review-dev4-20261022

## Recovery anchor

- **目标**：把 `origin/beta@dec93c43bf8` 合并进 `dev4`（零复活、零丢失），独立复评 `dev4` 已提交未推送的 5 个 TMDB 详情性能提交（`1a16fd163c3` 至 `7fe44116aaf`），修复必修项并验证，循环至通过后提交、推送、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突；丢失面 B（`beta` 树 − 合并树）= 0。
  2. 5 个未推送提交只读复评（12 文件），必修项已修且每项有可复核证据；`beta` 带入的 C93–C97 内容按「已由 dev1/dev2/dev3 复评 + 本轮 0 冲突 + 同文件改动区域不相交」跳过。
  3. 合并树验证通过：leanback 编译 + mobile 单测（TMDB\* + FullscreenMenuKeyTest）全绿；复评修复后再次复查一致。
  4. 复评修复经任务守卫 `finish` 落盘并创建 recovery tag。
  5. 推送 `dev4`，创建到 `beta` 的 PR（中文描述，含当前改动具体内容），随后拉取远端最新代码。
- **Lane**：`standard`
- **Scope**：`app/src`、`docs`、`scripts`
- **任务守卫**：`C98-BETA-MERGE-DEV4-20261022`，base HEAD `fb7028f7a42dce5f6848807a7044727fee9feb07`（合并提交），branch `dev4`，受保护脏路径 0
- **基线**：`dev4` HEAD = `7fe44116aaf`（相对 `origin/dev4` ahead 10；相对 `origin/beta` ahead 5，未推送）；merge-base = `3964eeae7cc`
- **当前状态**：合并、复评、文档修复、验证完成；交付坐标见 §7
- **下一动作**：无

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `dec93c43bf8`（PR #472–#478 合入后） |
| 本地 | `dev4` = `7fe44116aaf`（含 5 个未推送的 TMDB 详情性能提交） |
| merge-base | `3964eeae7cc`（= PR #471 合入） |
| 合并提交 | `fb7028f7a42dce5f6848807a7044727fee9feb07`（双父：`7fe44116aaf` + `dec93c43bf8`） |
| 冲突 | **0**（`git merge` 直接生成合并提交；`git diff --diff-filter=U` = 0） |

`beta` 本次带入 38 个提交（PR #472–#478 与 C93–C97 的合并/复评/文档），净差异 74 路径 / +4485 −228。

## 2. 零复活 / 零丢失证据

| 判据 | 含义 | 结果 |
| --- | --- | --- |
| 路径数 | 合并树 / `beta` / `dev4@7fe4411` | **5212 / 5206 / 5186** |
| **A** | 合并树 − `beta` 树 | **6**，恰为 `dev4` 独有：5 个新增测试 + `docs/TMDB-DETAIL-PERF-01-tmdb-detail-load-render.md` |
| **B** | `beta` 树 − 合并树（丢失面） | **0** |
| 内容级净差（`dev4` 树 → 合并树） | 本次合并实际带入 | 74 路径 / +4485 −228，与 `git diff 3964eeae7cc..origin/beta` 完全一致 |
| 反向核对 | `beta` 树 → 合并树 | 12 路径 / +1031 −98，全部为 5 个 TMDB 提交自身改动 |

## 3. 复评范围与跳过依据

- **复评**：`dev4` 未推送的 5 个提交，12 个文件：
  - `1a16fd163c3` 消歧 lite 详情 + 有界并行 + 图片档位
  - `fa2f31812e5` identity 已知并行预热 + C16 契约放宽
  - `67e1f6330ac` 设备 A/B 证据文档
  - `c9b2e769a87` W5 实测结论文档（已回滚）
  - `7fe44116aaf` 阻塞段移出共享 5 线程池
- **代码路径**：`TmdbService.detailForDisambiguation/disambiguationAppend/disambiguationCacheKey`、`fetchDisambiguationDetails/loadDisambiguationWinner/TmdbCandidate`、`startTmdbDetailPrefetch/loadPrefetchedTmdbBundle/toTmdbBundle`、`completeTmdbLoad` 的线程/代际/取消语义、`tierUrl/setRail*/heroPosterTier` 与查看/保存升级链、C16 §6.3 契约一致性、6 个新增测试的有效性。
- **跳过**：`beta` 带入的 C93–C97 等改动已在各自分支完整复评并附实测证据，本次合并 0 冲突；唯一与 `dev4` 同文件的 `TmdbDetailActivity` 由 `beta` 带入的「全屏菜单键下一集」4 行与 TMDB 改动区域不相交，合并后由 `FullscreenMenuKeyTest` 与编译回归覆盖。

## 4. 复评发现与修复

| # | 级别 | 位置 | 问题 | 修复 |
| --- | --- | --- | --- | --- |
| 1 | 中 | `docs/TMDB-DETAIL-PERF-01-tmdb-detail-load-render.md` 状态头/§1/§7/§8 | W1 仍写「待用户裁决」、W2/W5 仍写「暂缓」、§8 标题只写「W3 + W4」，与同一文件的 §6（已裁决并实施）、§9.3（W5 已试做回滚）、§9.4（W2 已实施）及 5 个提交矛盾。任务文档是唯一 durable 记录，会让后续会话误判进度、重复实施或重复测量 | 状态头改为 W1/W2/W3/W4 已实施并验证、W5 已试做回滚；§1 三行状态改为「已优化 / 已优化 / 已试做并回滚」；§7 改为「决策变更」，记录 W2 最终采用更小等价方案（整段提交 largeExecutor）与 W5 实测回滚；§8 标题补 W1 |
| 2 | 中 | 同文件 §1 图片行、§5、§4 推论 | §1「优化后」写「轮播在 ≤1080p 屏用 w1280 并限制解码尺寸」、§5 写「rail/轮播降档」，与 §8.3 和代码矛盾：`refreshBackdropSlideshow`/`loadBackdropImage` 仍经 `highResTmdbImage`→original，未做 `override` 限解码；字节收益 88%–93% 也未计入仍为 original 的轮播与大背景 | 统一改为「rail 用 w500/w780、hero 海报 w780；hero 大背景/背景轮播/全屏查看/保存仍用 original」；§4 推论按实际口径改为 83%–93% 并注明未计入项；§5「不做」列表同步为「仅新增 §6.3 预热例外」 |
| 3 | 低 | 同文件 §8.3 | `tierUrl/tierUrl(url,size)` 笔误 | 改为 `tierUrl(url, size)` |
| 4 | 低 | 同文件 §9.2 | 未记录 W3 新增的窄失败窗口：lite 打分通过、赢家完整详情请求失败且无陈旧缓存时，本次自动匹配放弃并回落手动选择 | 补「已知代价」条目，并说明改造前该候选完整详情已在打分阶段取到 |
| 5 | 低 | 同文件 §10 | 编号 1→3 缺 2；「内嵌块占载荷 23%+22%，合计 46%」未注明基数，与 §4 的 446,404 B 样例混读会自相矛盾（17,391+16,908 B 仅占 7.7%） | 编号改为 1/2；注明百分比按同次约 76 KB 缓存样本计，与 §4 泛样本不是同一基数 |
| 6 | 低 | 同文件 §11 | 回滚只列 W1/W3/W4，漏掉已实施的 W2 | 补 W2 回滚（把 `completeTmdbLoad` 并回协调任务） |

### 4.1 代码只读复核结论（不修，均已取证）

- **旧代际 worker 的 `detailPrefetch.cancel()` 无代际守卫**：旧 worker 通过 2473 的代际检查后，若 `onConfigEvent`/`onNewIntent` 恰好在其调用 cancel 前完成新 load，会误取消新页面的预热。后果仅为丢失一次预热收益（回落常规 `loadTmdbBundle`，正确性不变）；加 `generation == loadGeneration` 守卫仍存在 TOCTOU，本轮不改。
- **DIRECT 模式理论浪费**：正常入口 `normalizeDetailMode()` 会把非 TMDB 模式归一为 `DETAIL_OPEN_ENHANCED`；只有外部启动且 Intent 无 `detail_mode`、全局模式为 DIRECT 时才会「先预热后取消」，概率极低且不影响渲染。
- **CatAction yield 分支**（`shouldYieldDetail`）不立即取消预热，由 `onDestroy` 兜底，最多一次在途请求。
- **lite 缓存固定 7 天**（完整详情对在播剧为 1 天）：打分输入是 credits/标题/季数，变化极慢；赢家仍取完整详情上屏，不影响渲染。
- **预热使用 load 开始时的 `tmdbConfig` 快照**：站点请求期间若配置/语言变更，仅可能拿到旧语言 payload；正常配置变更走 `onConfigEvent` 重新 load，旧代际结果被 generation 丢弃。
- **打分输入等价性**：`cast()`（TV 优先 `aggregate_credits`）、`creators()`（`credits` + `aggregate_credits` 的 crew）、`splitSeasonDetailScore`（根对象 name/original_name/title/original_title）、`scoreTmdbCountry`（origin_country/production_countries）都只读 lite 保留的块；lite 缓存 type=`detail-lite` 与 full/core 键隔离，不会污染完整详情缓存。
- **图片档位可回退**：`originalUrl(tier(x)) == x`，全屏查看/保存经 `highResTmdbImage` 升级回 original；`tierUrl` 只替换 `/t/p/` 段，wsrv 包装线路（`?url=.../t/p/...`）同样可换档，编码或未知域名原样返回。

## 5. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 合并树编译（leanback） | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL**（与单测同批次，4m34s） |
| 合并树单测（mobile） | `:app:testMobileArm64_v8aDebugUnitTest --tests Tmdb* --tests FullscreenMenuKeyTest` | **BUILD SUCCESSFUL**，50 类 / 453 例，0 失败 0 错误 |
| 未合并路径 | `git diff --diff-filter=U` | 0 |
| 丢失面 | `comm -23 <beta> <merged>` | 0 |

本轮修复仅改文档、未改代码：以上编译/单测即为当前代码的验证结论；文档修复后做了关键词一致性复查（`待裁决`、`暂缓`、`w1280`、`轮播降档` 等无残留矛盾），并再次以 grep 复核状态与实现一致。

## 6. 回滚

撤销本轮复评修复提交即可恢复原文档。无代码、接口、协议、ABI 变化；合并提交可单独 revert 回到 `7fe44116aaf`。

## 7. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并目标 | `origin/beta@dec93c43bf85b42c6920a8ce262f5632e63bd9ae`（PR #472–#478 合入后，38 提交） |
| 合并提交 | `fb7028f7a42dce5f6848807a7044727fee9feb07`（父：`7fe44116aaf` + `dec93c43bf8`） |
| 复评修复提交 | `389a9a4148eaf64440ecb3b3ccf9f73d62d0bdad`（仅文档，2 文件 +109 −16） |
| Recovery tag | `recovery/C98-BETA-MERGE-DEV4-20261022/20261022100541-389a9a4148ea` |
| 推送 | `git push origin dev4`：`0f7e833b59f..389a9a4148e` |
| PR | **#479** https://github.com/Silent1566/webhtv/pull/479 （base `beta`，只创建不合并） |
| 拉取最新 | 推送后 `git fetch origin --prune`；本地 `dev4` 与 `origin/dev4` 同步（本坐标提交随后再推一次），`origin/beta` 仍为 `dec93c43bf8`（PR 未合并） |
