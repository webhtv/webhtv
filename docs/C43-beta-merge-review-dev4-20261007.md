# C43：dev4 合并远端 beta 最新代码并复评有效改动

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev4`；远端已删除/回退的提交不得重新带回；评审合并结果与 dev4 已提交未推送改动；发现问题后修复、验证并再次评审，循环至通过；提交当前任务改动、推送 `dev4`，创建 `dev4 -> beta` 中文 PR，**只创建不合并**。
- **验收标准**：① 合并提交第二父为本轮合并前 `origin/beta` tip；② 被 beta 删除/回退的内容零复活；③ beta 有效增量与 dev4 有效改动均无意丢失；④ 评审通过且没有未处理的必修问题；⑤ 定向测试、双 flavor 编译及必要回归验证通过；⑥ 提交带 recovery tag；⑦ `dev4` 推送成功；⑧ PR 已创建、中文排版清晰且保持未合并。
- **lane / scope**：`standard`；`app/`、`docs/`。
- **任务守卫**：`C43-beta-merge-review-dev4`；任务开始 HEAD 为 `9555806b70b66d19132785fd757b0b3e2b990400`；初始工作区干净。
- **当前状态**：`origin/beta` 已 fetch 至 `6ff6a4458cf7d7b46433ea5019442a378a9fb918`；因 beta 在验证期间前进，旧合并已中止并重新合入最新 beta，自动合并 0 冲突。第 1 轮评审发现 beta 已删除的蜘蛛崩溃面包屑功能会被普通 merge 重新带回，已按 beta 权威版本恢复并删除相关路径；第 2 轮复评 episode 并发修复通过；第 3 轮复评 PR#414 短剧源禁用修复通过。最终定向测试、双 flavor Java/AndroidTest Java 编译、空白检查和构建资源清理均通过；当前有效净差异仍为 episode 并发修复 3 个路径及本任务文档，待 task guard 提交、推送和创建 PR。
- **下一动作**：执行最终 beta 路径一致性/回退提交校验与暂存区复评；通过后执行 `task_guard.sh finish`，推送 `dev4` 并创建中文 `dev4 -> beta` PR（不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev4` |
| 合并前 HEAD | `9555806b70b66d19132785fd757b0b3e2b990400` |
| `origin/beta` tip | `6ff6a4458cf7d7b46433ea5019442a378a9fb918` |
| 合并基点 | `414babc1b5c9acae064a4cb6a35c689379155512` |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`；最终由 `task_guard.sh finish` 创建提交 |
| 自动冲突 | 0；`git diff --name-only --diff-filter=U` 为空 |
| 初始本地有效增量 | `790d678c9eb5307d2e0bcdcf35657ecec18c058b`、`9555806b70b66d19132785fd757b0b3e2b990400` |
| 当前 PR 有效净差异 | `SourceEpisodeSeasonCache.java`、`TmdbEpisodeSorter.java`、`SourceEpisodeSeasonCacheTest.java` |

## beta 增量 ledger

相对合并前 HEAD，`origin/beta` 独有提交全部纳入合并父提交：

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `57362eb4f2c052d9caf27c6ef2c2ef7c7a180708` | 修复外观与语言对话框行文字不可读 | 纳入 |
| `663ac5e7da34ecd815ab14ec6f414fffae8828b9` | 修复外观与语言二级弹窗标题不可读 | 纳入 |
| `8624f6c0d7e9b07c89f2bb1e8935b6d0dd78f40b` | 删除未引用的外观行辅助方法 | 纳入 |
| `5c95aa19cde1355710970f75f60b5db0c2545b59` | 修复详情页线路/选集 chip 底色 | 纳入 |
| `8d03e86d82e935898af3eae999d5df006841c3f6` | 合并 PR#410 并复评 TV 浅色模式 | 纳入 |
| `92f9b020f2756d78bd429242ab3a8f9615052556` | 订正 C41 文档差异统计 | 纳入 |
| `9b168a1f3805614547308a638a13ddae45a8f564` | Merge PR#411 from dev1 | 纳入 |
| `009d70842246a4d753fbd7f378dddb89c52a61c5` | 记录 TV 浅色模式最终设备验证 | 纳入 |
| `5ff8037e58ea6ae0e3ad7272e8f51b82a8d635d1` | 补充 TV 浅色模式设备验证 | 纳入 |
| `f77c4a02369279097df1f9eb23cc0b15f243a8cb` | 保持播放加载圈直到新播放器启动 | 纳入 |
| `3374def6413128ee5957c4fc4604b38da6a01024` | 合并 PR#411 并复评加载圈守卫泄漏 | 纳入 |
| `53b54d1b62c3e405790773b5b7c70f38221987a3` | Merge PR#412 from dev2 | 纳入 |
| `12f2639b8d4fc653cbd34b8407c51ece712451ea` | 合并 PR#410/#411/#412 并复评 TV 固定深色面板对比度 | 纳入 |
| `2a2139bebc9cc7dd02ec421a68d0f027baf547ef` | Merge PR#413 from dev3 | 纳入 |
| `6414185aff91edd15ad96f81bffaccea3a72f87e` | 修复短剧源规则无法禁用 | 纳入 |
| `84d76f4c592ad80acdab61e3439f80712498544d` | 合并 PR#412/#413 并补齐短剧源禁用契约 | 纳入 |
| `d1b7e2750e63abbcc9dabc771b2cdb63b66400a5` | 订正 C42 dev1 交付坐标与合并树记录 | 纳入 |
| `6ff6a4458cf7d7b46433ea5019442a378a9fb918` | Merge PR#414 from dev1 | 纳入 |

## dev4 已提交未推送改动

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `790d678c9eb5307d2e0bcdcf35657ecec18c058b` | 崩溃页显示最后加载蜘蛛源 | **不纳入**：beta 已删除 `SpiderCrashBreadcrumb`、对应测试与诊断文档，按远端当前权威状态撤回，不让 PR 复活 |
| `9555806b70b66d19132785fd757b0b3e2b990400` | 修复 TMDB episode 列表并发修改崩溃 | 纳入，保留 3 个有效路径 |

## 第 1 轮评审与修正

### 发现：不能把 beta 已删除的蜘蛛诊断功能重新提交

原始合并结果相对 `origin/beta` 有 11 个路径，其中包括：

- `SpiderCrashBreadcrumb.java`；
- `SpiderCrashBreadcrumbTest.java`；
- `SPIDER-CRASH-DIAG-spider-crash-diagnostics.md`；
- `JarLoader.java`、`CrashActivity.java` 与三语字符串中的配套引用。

`origin/beta` 当前版本明确删除上述功能。若保留，PR 会把远端已移除的提交带回 beta，违反任务约束。已按 `origin/beta` 恢复 `JarLoader.java`、`CrashActivity.java`、三语字符串，并删除 3 个新增路径。

修正后净差异只剩 `SourceEpisodeSeasonCache`、`TmdbEpisodeSorter` 与对应测试，符合本任务有效改动边界。

## 第 2 轮评审对象

### episode 列表并发修复

- `SourceEpisodeSeasonCache` 在遍历共享 `Flag.episodes` 前于同一 list monitor 下复制快照；解析阶段在锁外进行，避免把 resolver/回调带入共享容器锁。
- `TmdbEpisodeSorter` 在同一 list monitor 下完成读取、排序与原位 `set`；不再 `clear()+add()`，因此不递增 `ArrayList.modCount`，不会让已存在的 UI iterator 因结构性替换抛 `ConcurrentModificationException`。
- 排序比较器仍保持单一策略（全量季号元数据才按季号，否则全量按集号），并保留排序失败时原顺序回退。
- 测试覆盖快照与排序交错、既有 iterator 不失效、缓存清理重算、混季判定及原有排序规则。

## 第 3 轮复评：最新 beta PR#414 短剧源禁用修复

- `ShortDramaConfig.isSiteEnabled()` 先检查 `disabledSites`，再按 `configured` 决定使用显式规则或默认规则；`configured=true` 且 `enabledSites=[]` 时不会误用默认规则。
- `ShortDramaSourceDialog.show()` 仅在未配置时初始化默认规则；站点管理使用当前暂存规则计算勾选状态，`updateChipsDisplay()` 直接展示暂存规则，用户清空规则后不会因打开站点管理或刷新 chip 而复活默认关键词。
- `ShortDramaConfigTest` 的三个用例覆盖显式空规则、缺失配置默认规则，以及对话框三个默认规则入口的源码契约；C42 dev1 文档中的四轮评审与反证记录已核对，未发现未处理必修问题。

## 验证与最终复评

| 检查 | 结果 |
| --- | --- |
| 合并冲突 | `git diff --name-only --diff-filter=U`：0；自动合并 0 冲突 |
| beta 删除内容 | `SpiderCrashBreadcrumb.java`、对应测试、`SPIDER-CRASH-DIAG...md` 均不存在；配套 `JarLoader`/`CrashActivity`/三语字符串与 `origin/beta` 一致 |
| PR 净差异 | 3 个有效路径：`SourceEpisodeSeasonCache.java`、`TmdbEpisodeSorter.java`、`SourceEpisodeSeasonCacheTest.java`；另含本任务文档 |
| beta 增量 | beta 独有 18 个完整提交（含 PR#414）均由合并父纳入；beta 侧路径未被有效改动覆盖 |
| 回退/复活评审 | 已按 beta 当前删除结果复核，删除路径零复活；PR 不包含蜘蛛诊断功能 |
| episode/短剧定向 JVM | 移动版 `SourceEpisodeSeasonCacheTest`、`TmdbEpisodeSorterTest`、`ShortDramaConfigTest`，Leanback 对应测试：Gradle 任务成功 |
| 双 flavor Java | `compileMobileArm64_v8aDebugJavaWithJavac`、`compileLeanbackArm64_v8aDebugJavaWithJavac`：成功 |
| 双 flavor AndroidTest Java | `compileMobileArm64_v8aDebugAndroidTestJavaWithJavac`、`compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac`：成功 |
| 总体构建结果 | 最新 beta（含 PR#414）同一轮 Gradle 调用 `BUILD SUCCESSFUL`，耗时 7 分 38 秒 |
| 空白检查 | `git diff --cached --check`：通过 |
| LSP | 变更 Java 文件本轮诊断结果为 0；部分文件在 5 秒预算内未完成扫描，因此以 Gradle 编译/测试作为主要静态与行为证据；文档无适用 LSP |
| 构建资源回收 | 最新验证后执行 `gradlew.bat --no-daemon clean`，`BUILD SUCCESSFUL`，耗时 1 分 7 秒 |

**第 3 轮复评结论：通过。**

- `SourceEpisodeSeasonCache` 的快照只在共享列表锁内完成，resolver 在锁外执行；不存在把外部回调带入容器锁的死锁扩大面。
- `TmdbEpisodeSorter` 的读取、排序和原位 `set` 使用同一列表锁；没有 `clear()+add()` 结构性替换，既有 iterator 不会因 `modCount` 变化失效。
- 原有排序策略、异常回退、缓存清理和混季判定均未被削弱，新增测试覆盖交错排序与 iterator 保持有效。
- beta 的主题、播放加载圈、详情页等变更只存在于合并父，不出现在 PR 净差异；没有未处理的必修评审问题。

## 回滚

在 task guard 提交前，恢复锚点为 `9555806b70b66d19132785fd757b0b3e2b990400`，可用 `git merge --abort`（若仍处于未提交合并状态）或 `git reset --hard` 前先保留当前恢复点。提交后使用 `git revert -m 1 <merge-commit>` 回退合并提交；该操作不回退 beta 分支。
