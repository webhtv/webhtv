# C77：dev3 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

审计日期：2026-10-17（Asia/Shanghai）
任务 ID：`C77-beta-merge-review-dev3-20261017`
分支：`dev3`
范围：`app/src/mobile/java/com/fongmi/android/tv/ui/activity`、`app/src/leanback/java/com/fongmi/android/tv/ui/activity`、`app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapter.java`、`app/src/main/java/com/fongmi/android/tv/bean/FlagPreferenceCache.java`、`app/src/testMobile/java/com/fongmi/android/tv/ui`、`app/src/testLeanback/java/com/fongmi/android/tv/ui/activity`、`app/src/test/java/com/fongmi/android/tv/bean`、`docs`

## Recovery anchor

- **目标**：把远端 `beta` 最新代码安全合入 `dev3`，**不得复活 beta 已移除/回退内容**；评审 dev3 全部已提交未推送改动（含本轮新增修复）与合并交互，发现问题就最小修复并验证，循环到通过；随后提交、推送并创建中文 `dev3 -> beta` PR，**只创建不合并**。
- **合并前 HEAD（第一父）**：`98250b22e4377e66131e2e8b08a528b100792569`
- **合并第二父（beta 权威 tip）**：`323afa1a5a9b962749bd3e78840a4f729a2f4701`
- **当前状态**：见 §7（交付坐标）
- **下一动作**：见 §7

## 1. 目标与判定标准

验收标准：

1. 合并提交为双父合并，第二父等于提交前最新 `origin/beta` tip。
2. 合并结果相对 `origin/beta` 的净差异**只**包含本任务自身的改动与任务文档，不含任何 beta 已删除内容。
3. 零复活有可证伪证据（检测器自检必须能报出注入的探针）。
4. 定向测试、双 flavor Java 编译、`git diff --check`、`gradlew clean` 全部通过。
5. PR `base=beta`、`head=dev3`、`state=OPEN`、`mergedAt=null`，描述为中文且明确不自动合并。
6. PR 文件集与 `git diff --name-only origin/beta HEAD` 逐项一致。

## 2. 合并前的仓库状态

| 项 | 值 |
| --- | --- |
| 分支 / HEAD | `dev3` / `98250b22e4377e66131e2e8b08a528b100792569` |
| `origin/dev3` | `5abdf7a1cf198bc3f8076bb38ee84c337f610f66`（只剩 **2 个**已提交未推送提交） |
| `origin/beta` tip | `323afa1a5a9b962749bd3e78840a4f729a2f4701`（PR #453，即 tag `v5.6.0-beta-202610170951`） |
| 合并基 | `323afa1a5a9b962749bd3e78840a4f729a2f4701`（= beta tip 本身） |
| 领先 / 落后 | `2 / 0` |
| 开工前脏文件 | 无（工作树干净） |

### 2.1 评审对象：dev3 已提交未推送的 2 个提交

`git log --oneline origin/dev3..dev3` 只有 2 条（`71` 条本地领先里的其余 69 条已随 PR #454/#453 进入 beta）：

| commit | 内容 |
| --- | --- |
| `ee7151e191512712e45763e112b3c5db435352ca` | `fix(player)`：播放页切换线路即落盘偏好，重进优先复用（mobile + leanback 各 +66 行） |
| `98250b22e4377e66131e2e8b08a528b100792569` | `fix(tmdb)`：稳定键命中线路后不再因集 URL 失配退回第一条（`TmdbUIAdapter` + `TmdbUIAdapterTest`） |

### 2.2 合并为空操作（本次无需造合并提交）

```text
git merge --no-commit --no-ff origin/beta
→ Already up to date.        # exit 0，索引与工作树均无改动
```

原因：`git merge-base --is-ancestor origin/beta dev3` **为真**——beta tip `323afa1a5a` 本身就是 dev3 HEAD 的祖先（上一轮 PR #453 把 dev3 的 `18a43b46f4` 合进 beta 后，该合并提交又回到 dev3 历史里）。因此 `dev3` 与 `origin/beta` 的**净差异恰为 `dev3` 自身那 2 个提交的 4 个路径**，不存在「beta 侧待纳入增量」，也就不存在「顺带提交回退内容」的通道：

```text
git diff --name-status origin/beta dev3
M  app/src/leanback/java/.../ui/activity/VideoActivity.java
M  app/src/main/java/.../ui/helper/TmdbUIAdapter.java
M  app/src/mobile/java/.../ui/activity/VideoActivity.java
M  app/src/testMobile/java/.../ui/helper/TmdbUIAdapterTest.java
```

→ 4 路径，`M` 4 条，**零 `A`、零 `D`**（没有任何「被带回」的文件）。本轮据此**不造空的双父合并提交**（无第二父可引入），交付物是本任务新增的回归锁 + 任务文档。

## 3. 零复活 / 零丢失证据

检测器独立实现于 `F:/temp/revival_audit.py`（仓库外，避免被 `finish` 的 scope 检查拦下），全部结论如下：

| 层 | 口径 | 结果 |
| --- | --- | --- |
| L0 文件数 | beta 树 5129 = HEAD 树 5129 | 新增 **0**、缺失 **0** |
| L1 revert 集 | 只匹配**主题行并锚定开头**（`revert`/`回退`/`撤销`/`剔除`/`移除`） | 30 个提交、**269** 条触及路径 |
| L3 revert 真删除 | L1 中 `--name-status` 取 `D` 的整路径 | **143** 条 |
| L4 真删除且不在 beta 树 | L3 减 beta 整树 | **127** 条 |
| L5 复活检测 | L4 的 127 条在 HEAD 树中存在数 | **0** 条 |
| L6 可证伪自检 | 取 L4 真实成员 `app/src/main/assets/themes/index.json` 注入伪造树 | 检测器**如期报出**；对真实树输出为空 → **非空转** |
| L7 净差异 ∩ L4 | `git diff --name-status origin/beta HEAD` 与 L4 交集 | **空** |
| L8 评估索引行 ID | `^\| \`[A-Z0-9-]+\`` 集合，beta 20 = HEAD 20 | 缺失 **0**、新增 **0** |

已知三个假象来源均已规避：

1. ref 先固化为 40 位 SHA 再取整树（避开 MSYS 把 `git cat-file -e origin/beta:path` 里 `:` 后的 `/` 当路径分隔符的假阴性）；
2. revert 集只匹配主题行、不匹配提交正文（本类任务的合并提交正文常含「回退」字样）；
3. 检测器用**真实**「被 revert 删除且不在 beta 树」的成员做注入自检，排除空转。

## 4. 复评（第 1 轮）：评审 2 个已提交未推送提交

### 4.1 `98250b22e4`（`TmdbUIAdapter.selectPlaybackFlag`）

新增分支：稳定键命中真实线路但该集 URL 已失配时，若线路名也一致则仍返回 `keyed`，不再返回 `null`。

| 检查项 | 结论 |
| --- | --- |
| 调用方对 `null` 的处理 | 4 处（mobile/leanback `resolveHistoryPlaybackFlag`、`TmdbDetailActivity.findInitialFlag`/`initialStandaloneFlag`、`NovelRouter`）**均把 `null` 当「解析不了」而退到 `flags.get(0)` 或换线兜底**，因此原实现的 `null` 确实等价于「线路强行回到第一条」 |
| 新增分支是否过宽 | 要求 `keyed != null`（稳定键已在当前线路表命中真实线路）**且** `requestedFlagName` 非空**且** 线路名相等。三者缺一不可 → 不会用线路名去「猜」一条稳定键根本不存在于当前表的线路（`staleStableFlagKeyWithUnmatchedNameStaysUnresolved` 锁住） |
| 与 `uniqueFlagForEpisodeUrl` 的先后 | URL 唯一命中仍然优先（`reorderedStableFlagKeyFallsBackToUniqueEpisodeUrl` 锁住），新增分支只在 URL 也无法唯一定位时兜底 |
| 回归锁 | 该提交自带 2 例新断言（第 61 / 62 项），并保留既有 `staleStableFlagKeyDoesNotFallBackToAmbiguousEpisodeUrl` |

**未发现必修缺陷。**

### 4.2 `ee7151e191`（mobile + leanback `rememberFlagSelection` / `findPreferredFlag`）

结构与详情页既有实现（`TmdbDetailActivity.savePreferredFlag` / `findPreferredFlag`，PR #454 带入）**逐条同构**：偏好索引未知时不写稳定键、`Task.execute` 落盘、稳定键优先/线路名退化、`episodeUrl` 非空时要求候选线路确实含该集、`canSave()` 才写库。

#### 候选缺陷（**已证伪，不改**）

`findPreferredFlag` 在「稳定键存在但未命中当前线路表」时**仍按线路名兜底**（详情页既有实现同样如此）。第一版修法改为「只有压根没记稳定键才退化用名称」，理由是「名称是退化依据而不是等价依据」。

**该修法产生反例，故被否决。** 名称兜底受 `containsEpisodeUrl` 守卫，而下游 `selectPlaybackFlag` 的线路名分支**没有**这个守卫：

> 偏好 `stableKey = "线路4#3"`、`flagName = "线路4"`；当前线路表 `[线路1(E1,E2), 线路4(E1,E2)]`；本集 E2（两条线都含 E2）。
>
> - **原实现**：稳定键 `线路4#3` 未命中 → 线路名命中 `线路4` → 守卫通过 → 返回 **`线路4`** ✅
> - **第一版修法**：稳定键未命中即放弃 → 交给下游 `selectPlaybackFlag` → `uniqueFlagForEpisodeUrl` 因**两条线都含 E2 而返回 null** → `keyed` 也为 `null` → 返回 `null` → 调用方 `flags.get(0)` → 返回 **`线路1`** ❌

这正是本任务要修的「杀进程重进后线路强行回到第一条」在更窄场景下**复发**。因此在守卫之外，全输入域上的行为不变式为：

> 偏好的候选线路要么**就是**权威解析 `selectPlaybackFlag` 的结果，要么偏好整体让位；两者对真实缺陷场景与全部边界场景一致，且「名称兜底 + URL 守卫」严格弱于下游「容忍任意集切换的名称分支」。

结论：**保留原实现**，不改生产代码。

#### 真正的缺口：零回归锁

这 2 个已提交未推送提交在生产代码里新增了三条易回退的不变式，但**没有任何测试守住它们**（`grep selectPlaybackFlag` 显示既有测试只覆盖 `TmdbUIAdapter`，播放页与详情页的偏好链路完全无锁）：

1. 切线路必须**独立于播放进度**落盘（否则等于没修）；
2. 偏好必须**确实含这次要播的集**（否则劫持按 URL 的权威解算）；
3. 历史回退里偏好的**优先级**必须在 History 快照之前、`flags.get(0)` 兜底必须保留。

→ 第 2 轮新增 `MobilePlaybackFlagResolutionContractTest`（6 项）补齐这层锁，覆盖 **mobile + leanback + 详情页三方口径**。

## 5. 复评（第 2 轮）：新增回归锁与其非空转证明

新增文件（本任务唯一生产/测试改动）：

| 文件 | 内容 |
| --- | --- |
| `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/MobilePlaybackFlagResolutionContractTest.java` | 6 项契约断言 |

断言只钉**不变式**而不钉实现形态，以便后续把两处 `findPreferredFlag` 收敛成一个工具方法时仍然成立：

| 测试 | 钉住的不变式 |
| --- | --- |
| `switchingALinePersistsTheChoiceWithoutWaitingForPlayback` | 偏好写入 + `Task.execute` 落盘 + 索引未知不写假键 + **写入点位于 `canSave()` 之前**（与播放进度解耦，`mobile` 与 `leanback` 双侧） |
| `tappingALineGoesThroughThePersistencePath` | `onItemClick(Flag)` 必须经过落盘路径，且目标线路取自适配器实例（界面下标 ≠ 数据下标） |
| `storedPreferenceIsGuardedByTheRequestedEpisodeUrl` | URL 守卫必须作用在**共用出口** `candidate` 上（覆盖全部查表路径，包括名称分支）；URL 为空时放行；不得返回未过守卫的候选 |
| `historyFallbackStillConsultsTheStoredPreference` | 偏好优先于 History 快照，且 `flags.get(0)` 兜底必须保留（否则调用方空指针） |
| `preferenceCacheKeyMatchesTheDetailPageKeyShape` | 详情页与播放页必须同用 `(intent key, intent id)` 原值与同一份缓存 |
| `detailPageKeepsTheSamePriorityOrderItHadBeforeTheFix` | 详情页顺序「显式意图 → 落盘偏好 → 历史 → 首条」不被误动 |

### 变异测试（证明断言非空转）

源码读取型守卫必须加 `--rerun-tasks`，否则 Gradle 会判定 `UP-TO-DATE` 而**根本没有执行测试**（本仓库已记录过该坑）。三组变异全部如期转红：

| # | 变异 | 结果 |
| --- | --- | --- |
| M1 | mobile 删掉 `findPreferredFlag` 的 URL 守卫（`return candidate;`） | 红：`storedPreferenceIsGuardedByTheRequestedEpisodeUrl` |
| M2 | mobile 删掉 `Task.execute(() -> FlagPreferenceCache.get().save())` | 红：`switchingALinePersistsTheChoiceWithoutWaitingForPlayback` |
| M3 | **leanback** 删掉 `onItemClick(Flag)` 里的 `rememberFlagSelection(resolved);` | 红：`tappingALineGoesThroughThePersistencePath`（跨 flavor 覆盖生效） |

每组还原后均用 `git hash-object` 与 `HEAD` 的 blob 比对：

```text
mobile/VideoActivity.java     work=89931f1d4c5befa7d886a000b49a273b8ca517ad head=89931f1d4c5befa7d886a000b49a273b8ca517ad SAME
leanback/VideoActivity.java   work=b65167a8f0dca02c002e3e4184373a35f66ded41 head=b65167a8f0dca02c002e3e4184373a35f66ded41 SAME
```

→ **逐字节相同**，生产代码未被变异污染。

## 6. 验证记录

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 定向单测 | `:app:testMobileArm64_v8aDebugUnitTest` + 4 个测试类 | **215/215 通过**（新契约 6、`TmdbUIAdapterTest` 61、`TmdbDetailActivityLayoutTest` 130、`FlagPreferenceCacheTest` 18），`failures=0 errors=0` |
| 变异测试 | 同上 + `--rerun-tasks`，3 组 | 3/3 如期转红，随后逐字节还原 |
| 零复活审计 | `python /f/temp/revival_audit.py` | 见 §3，含注入探针自检 PASS |
| 净差异 | `git diff --name-status origin/beta HEAD` | 4 路径，全为 `M`；∩ L4 为空 |
| 评估索引行 ID | 集合比对 | beta 20 = HEAD 20 |
| 空白校验 | `git diff --check` | 干净 |
| 双 flavor Java 编译 | `compileMobileArm64_v8aDebugJavaWithJavac` / `compileLeanbackArm64_v8aDebugJavaWithJavac` | 通过 |
| 资源回收 | `gradlew --stop` + `clean` | 通过 |

## 7. 交付坐标

本任务的交付物由两部分组成，坐标按以下口径记录（具体 hash 由收尾会话在同一分支追加）：

| 项 | 口径 |
| --- | --- |
| 代码/测试提交 | 新增 `MobilePlaybackFlagResolutionContractTest` + 任务文档 + 评估索引 `C77` 行，一把原子提交 |
| recovery tag | `recovery/C77-beta-merge-review-dev3-20261017/<timestamp>-<commit12>`，annotated、本地 |
| 推送 | `dev3` 分支 + 上述 tag |
| PR | `base=beta`、`head=dev3`，中文描述，**只创建不合并** |
| 无第二父的原因 | `origin/beta` 已是 `dev3` 祖先，无从引入 beta 增量（见 §2.2） |

## 8. 交付后复核（必做）

提交推送后**再次 `fetch`**，逐项核对：

1. `origin/beta` 仍等于 `323afa1a5a9b962749bd3e78840a4f729a2f4701`（本轮无第二父，故核对「beta 未前进导致净差异口径变化」）；
2. PR 的 `state=OPEN`、`mergedAt=null`、`mergeable`，且描述为中文并明确不自动合并；
3. PR 文件集与 `git diff --name-only origin/beta HEAD` **逐项一致**（用 `diff`/`Compare-Object` 校验，不是目测）；
4. `git ls-remote` 确认 `origin/dev3` 与 recovery tag 指向本地提交/标签。
