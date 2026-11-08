# C79：dev3 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

审计日期：2026-10-18（Asia/Shanghai）
任务 ID：`C79-beta-merge-review-dev3-20261018`
分支：`dev3`
范围：`app/src/main/java/com/fongmi/android/tv/player/exo`、`app/src/test/java/com/fongmi/android/tv/player/exo`、`app/src/testLeanback/java/com/fongmi/android/tv/player/exo`、`docs`、`.codex/scripts`、`AGENTS.md`

## Recovery anchor

- **目标**：把远端 `beta` 最新代码安全合入 `dev3`，**不得把 beta 已移除/回退的提交顺带带回来**；复评 dev3 全部已提交未推送改动（含本轮修复）与合并交互，发现问题就最小修复并验证，循环到通过；随后提交、推送并创建中文 `dev3 -> beta` PR，**只创建不合并**。
- **合并前 HEAD（第一父）**：`3866292f2a73855d87bc1ee4aedf0cbbf5076103`
- **合并第二父**：**无**（`origin/beta` 已是 HEAD 祖先，`Already up to date`）
- **beta 权威 tip**：`4e3f6bcf3a20a8c71711470dd1aa0361303acc5a`
- **当前状态**：见 §7（交付坐标）
- **下一动作**：见 §7

## 1. 目标与判定标准

验收标准：

1. 若 beta 已前进则合并提交为双父合并、第二父等于提交前最新 `origin/beta` tip；若 beta 已是本地分支祖先，则记录「本轮无第二父」并以「净差异 vs beta」替代该项。
2. 合并结果相对 `origin/beta` 的净差异**只**包含本任务自身的改动与任务文档，不含任何 beta 已删除/已回退内容。
3. 零复活有可证伪证据，不是只看「无冲突标记」。
4. 定向测试、双 flavor Java 编译、`git diff --check`、`gradlew clean` 全部通过。
5. PR `base=beta`、`head=dev3`、`state=OPEN`、`mergedAt=null`，描述为中文且明确不自动合并。
6. PR 文件集与 `git diff --name-only origin/beta HEAD` 逐项一致。

## 2. 合并前的仓库状态

| 项 | 值 |
| --- | --- |
| 分支 / HEAD | `dev3` / `3866292f2a73855d87bc1ee4aedf0cbbf5076103` |
| `origin/dev3` | `7e83931c5baa38b40581fac88f10f72781fa734c`（落后本地 **5** 个提交） |
| `origin/beta` tip | `4e3f6bcf3a20a8c71711470dd1aa0361303acc5a`（PR #456） |
| 领先 / 落后 vs beta | `0 / 5`，`git merge-base --is-ancestor origin/beta HEAD` = **真** |
| 开工前脏文件 | 无（工作树干净） |

### 2.1 为什么本轮没有第二父

`git merge --no-commit --no-ff origin/beta` 报 `Already up to date.`（exit 0），且索引与工作树零改动；`git rev-list --left-right --count origin/beta...HEAD` = `0 5`。

这不是「漏做合并」，而是 beta 的 PR #456（dev2 的 C78 合并）已把 `4e3f6bcf3a` 这个合并提交放进了 dev3 的历史（dev3 的 `5c454afbed` 就基于它）。按仓库既有规程（C66/C77 同类情形），**不为凑形式造一个空的双父合并提交**，验收口径相应改为「净差异 vs beta 只含本任务自身改动」+ 文件级/行级零复活。

### 2.2 评审对象：dev3 已提交未推送的 5 个提交

`git log --oneline origin/dev3..dev3`：

| 提交 | 主题 |
| --- | --- |
| `3866292f2a` | docs(E-SP10): 记录 5559 设备验证结果与未完成边界 |
| `dbd4416f86` | fix(guard): 修 task_guard start 写序缺陷并新增 abort 逃生口 |
| `625bb0d921` | test(E-SP10): 补 Inputs.capture 端到端守卫，防让位逻辑静默失效 |
| `c1fd4aa13f` | feat(E-SP10): Exo 外部回环代理 EOF 恢复退避与预加载让位 |
| `5c454afbed` | docs(E-SP10): Exo 外部回环代理 EOF 恢复退避与预加载让位设计 |

> 注：`origin/dev3` 落后于本地，`git log` 列出 5 条中含已被 beta 带回的公共提交，真实待推送集合见 §7。

## 3. 零复活审计（三层证据）

**准绳**：`origin/beta` 固化 ref `4e3f6bcf3a20a8c71711470dd1aa0361303acc5a`（避免 MSYS 把 `origin/beta:path` 的 `/` 当路径分隔符而误报）。

### 3.1 第一层：整树比对（文件级零复活 / 零丢失）

用 `git ls-tree -r --name-only` 取 beta 树与 HEAD 树后 `comm` 比对：

| 项 | 结果 |
| --- | --- |
| beta 树 | 5135 条 |
| HEAD 树 | 5140 条 |
| HEAD − beta（新增） | **5** 条，全为本任务自身新增文件 |
| beta − HEAD（丢失） | **0** 条 |

新增 5 条与历史删除集合的交集为**空**，即没有任何一条是「历史上被删、现在又被带回来」的路径。

### 3.2 第二层：行级净差异

`git diff --name-status origin/beta HEAD` = **13** 个路径（11 `M` + 5 `A` 中的 5 个新增；`A`/`M` 合计 13），`1118 insertions(+), 21 deletions(-)`。

21 条删除行逐条核查，全部属于本任务自身改写，无一条来自 beta 的删除/回退：

- `task_guard.sh` 的会话初始化重构（12 行）
- `AGENTS.md` 的 `abort` 说明补写（1 行）
- `HttpEofRecoveryDataSource` 日志格式加 `backoffMs`、`Factory` 调用点加角色实参（3 行）
- `AutoPreloadPolicyTest` 三处构造改为新 arity（3 行）
- `MediaSourceFactory` 两处角色标注（2 行）

### 3.3 第三层：revert 提交交集（可证伪）

按规程规避三个已实测的检测器假象来源：

1. **ref 固化**：先 `git rev-parse origin/beta`，再用 `ls-tree` 取整树，不用 `git cat-file -e origin/beta:path`。
2. **行尾统一**：`tr -d '\r'` 后再 `comm`（Python 文本模式写出的 CRLF 会让 `comm` 按字节比较时查不出注入路径）。
3. **主题行锚定**：`grep -iE '^[0-9a-f]+ (revert|回退|撤销)'`，只匹配主题行开头，避免匹配到提交**正文**里的「revert-like」字样而把任务自己的提交误判为 revert 提交。

结果：

| 项 | 值 |
| --- | --- |
| 主题行锚定的 revert/回退提交 | 110 条 |
| 其触及路径去重 | 5726 条 |
| 其中真实删除且不在 beta 树 | 5670 条 |
| **∩ 本轮净差异** | **2 条**，逐条核查为**历史共触、非复活** |

命中的 2 条逐条核查：

- `app/src/main/java/com/fongmi/android/tv/player/exo/MediaSourceFactory.java`：命中的 revert 是 `5d4dcc756f` / `720851094b`「Revert "fix: bypass cache for local proxy exo playback"」，撤掉的是**缓存旁路**逻辑。本轮在该文件只新增了 `HttpEofRecoveryDataSource.Factory` 的角色标注（`false`/`true`），与缓存旁路**无关**（净差异新增行已逐行核对，见 §3.2）。
- `docs/upstream-player-dependency-merge-assessment-2026-08-20.md`：命中的 revert 是 `0616a992f6`「revert(exo): keep video decode mode manual」，与评估索引的表格行无关。本轮在该文件只新增 **1 行**（`E-SP10` 索引行，`+` 计 2 行含文件头）。

- **净差异 `A` 集合（5 条）∩ revert 触及路径 = 空**，这是最强的一条：新增文件不可能是「复活」。

结论：**零复活成立**，且证据可证伪（三条独立判据，其中两条给出非空交集后逐条定位为无关共触，而非「检测器空转」）。

## 4. 复评：5 个未推送提交

### 4.1 评审通过的实现（保留原样）

| 位置 | 判据 |
| --- | --- |
| `HttpEofRecoveryDataSource.RECONNECT_BACKOFF_MS = {100,250,500}` | 只用 `consecutiveReconnects` 索引，`read()` 有进展即清零，不会把一次长连接的多次独立截断累加成永久延迟；`backoffMs(<=0)=0` 保证首次重试零延迟，不惩罚单次瞬时 EOF |
| `backoffBeforeReconnect()` 后的 `dataSpec == null` 再检查 | **真实缺陷修复**：退避 sleep 位于 `read()` 的 `dataSpec` 守卫**之后**，sleep 期间 `close()` 会让随后的 `dataSpec.subrange()` 解引用空指针。检查位置正确 |
| `InterruptedIOException` + `Thread.currentThread().interrupt()` | 中断语义正确，不会吞掉中断标志 |
| `ExoForegroundEofSignal.ageMs()` 未观测返回 `Long.MAX_VALUE` | 哨兵远高于任何让位窗口，「从未发生」永不触发让位；`reset` 仅测试用 |
| `AutoPreloadPolicy.EOF_YIELD_WINDOW_MS = 15_000` | 大于设备实测恢复簇 2–6 s，且小于 `EXTERNAL_LOOPBACK_DURATION_MS = 40_000`，单次停顿不会吞掉整个预加载窗口 |
| 前后台角色接线 | 预加载 `false`、前台 `true`，方向正确：预加载侧恢复若也发布信号会让预加载向自己让位而永不重启 |

**时钟基一致性（重点核对）**：`ExoForegroundEofSignal.record()` 用 `SystemClock.elapsedRealtime()`，而 `AutoPreloadPolicy.Inputs.capture()` 的 `nowElapsedMs` 由 `PreCache.getAutoDecision()` 以 `SystemClock.elapsedRealtime()` 传入（`PreCache.java:891`）。**同一时钟基，无混用**，`ageMs` 不会出现数量级错误。

**单实例论证复核**：全仓 `ExoPlayer.Builder` 只有 2 个构造点——`ExoUtil.java:292`（接入本任务的 `MediaSourceFactory`）与 `CustomWallView.java:295`（壁纸播放器，**未**调用 `setMediaSourceFactory`，故拿不到 `HttpEofRecoveryDataSource`）。已在本轮重新实测确认，进程级单值信号的并发覆盖风险排除。

### 4.2 发现并修复的必修缺陷（4 处注释事实性错误）

**根因**：`ExoForegroundEofSignal` 类 javadoc 与 `MediaSourceFactory` 方法内注释都声称该信号有**两个**消费者（预加载 + 网络守卫），但 `ExoNetworkGuardController` **并未接线**读取该信号——全仓 `grep ForegroundEof` 只有 `AutoPreloadPolicy` 一个消费点。这与本任务自己的文档 §10.5「该信号目前只有预加载一个消费者」**直接矛盾**，且 `MediaSourceFactory` 的 `//` 注释被夹在方法调用链中间，内容写在 `.setCache(getCache())` 之后，读起来像在描述缓存而非数据源角色。

**修复**（最小、纯注释，零行为变更）：

| 文件 | 修改 |
| --- | --- |
| `ExoForegroundEofSignal.java` | 「Preload and the network guard read it to yield」→「Preload reads it to yield …; `ExoNetworkGuardController` is not wired to it yet」 |
| `MediaSourceFactory.java` | 「these recoveries are the signal preload and the network guard yield to」→「the signal preload yields to」 |

修复后复核：`grep 'network guard\|网络守卫'` 在这两个文件中**无残留**。

**为什么这是必修而非吹毛求疵**：该注释是下一阶段（阶段 3：降速区分故障类型）设计者判断接线状态的唯一入口。若注释声称已接线而实际未接，后续维护者会据此认为「网络守卫已在消费该信号」而跳过接线，导致阶段 3 静默失效——这正是本任务在阶段 2 已经踩过一次的坑（`Inputs.capture` 未读信号时测试仍全绿），文档 §10.7 有记录。

### 4.3 复评第 2 轮：无新增必修问题

对修复后的 4 个生产文件与 5 个测试文件再次逐文件复评：

- 注释改动未触及任何行为路径，`git diff` 仅 4 行变化（2 处删除 + 2 处新增）。
- 复核 `AutoPreloadPolicyTest` 旧 arity 构造点：`grep -n "new AutoPreloadPolicy.Inputs("` 命中 6 处，逐处确认**全部**已带 `foregroundEofRecoveryAgeMs`，无遗漏（遗漏会导致编译失败，编译已通过，二者互证）。
- 复核任务文档自相矛盾项已消除：代码与文档 §10.5 口径现已一致。

**复评结论：通过，无必修问题。**

## 5. 验证

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 定向 JVM 测试（全 exo 包） | `:app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.player.exo.*'` | **605 例 0 失败**（79 个测试类，1 skip） |
| 本轮相关 5 个测试类 | 同上 + 类名过滤 | **44/44 通过**（`AutoPreloadPolicyTest` 27、`ExoForegroundEofSignalTest` 7、`HttpEofRecoveryPolicyTest` 5、`ExoEofRecoveryWiringSourceTest` 3、`HttpEofRecoveryCancellationTest` 2） |
| 双 flavor Java 编译 | `compile{Leanback,Mobile}Arm64_v8aDebugJavaWithJavac` + `…DebugUnitTestJavaWithJavac` | BUILD SUCCESSFUL |
| `git diff --check` | — | 无空白错误 |
| 冲突标记扫描 | `git diff origin/beta \| grep -E '^\+(<<<<<<<\|=======\|>>>>>>>)'` | 无 |
| `gradlew clean` | 见 §7 | 见 §7 |

### 5.1 变异测试（证明本轮依赖的守卫非空转）

本轮评审的核心安全判据是「预加载路径被标注为 `foreground=false`」。用变异证明承载它的守卫不是恒真：

- **M1**：把 `MediaSourceFactory.java:102` 的 `new HttpEofRecoveryDataSource.Factory(upstream, false)` 改为 `true`（模拟预加载错标为前台）。
- 结果：`ExoEofRecoveryWiringSourceTest.preloadPathIsMarkedNonForeground` **如期转红**（`AssertionError: preload must build its recovery source explicitly`），3 例中 1 例失败。
- 还原：`git hash-object` = `950a399aa49e593e3d1c4606dbcafd050f0dcd71`，与变异前**逐字节一致**；随后重跑 5 个测试类 **44/44 通过**。

**工具注意事项（本轮实测）**：变异测试必须带 `--rerun-tasks`（源码读取型守卫在运行时读仓库文件，Gradle 无法把它纳入任务输入，否则会显示 `UP-TO-DATE` 而**根本没执行测试**）。但 `--rerun-tasks` 会连带强制重跑 `:chaquo:installArm64_v8aDebugPythonRequirements`（该任务失败），此时应以**测试结果 XML 的 `failures` 计数**为判据而非只看 BUILD 状态——`testLeanbackArm64_v8aDebugUnitTest` 自身的失败信息会明确给出 `There were failing tests.`，据此判定转红有效。

## 6. 未完成/未验证边界（诚实记录）

- **设备端效果仍未验证**。本任务（C79）不新增设备验证，沿用 `E-SP10` 文档 §11 的结论：退避是否真的降低 EOF 频率、让位是否真的降低掉帧，仍缺设备证据；15 s 窗口取值属推理而非实测。这两项**不因本轮复评而改变**。
- `E-SP10` 阶段 3（降速区分故障类型）仍暂缓，且本轮修复的注释已如实写明网络守卫**尚未**接线，避免后续误判。

## 7. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 / 第二父 | 本轮 **无第二父**（`origin/beta` 已是 HEAD 祖先）；本任务提交的第一父为 `3866292f2a73855d87bc1ee4aedf0cbbf5076103` |
| 本任务提交 | `d6dae3da12fec4c6054d63aa402d8d13bd175581`（4 files changed, 197 insertions(+), 4 deletions(-)） |
| recovery tag | `recovery/C79-beta-merge-review-dev3-20261018/20261017162842-d6dae3da12fe`（annotated，已推送，指向 `d6dae3da12`） |
| 提交前 beta tip | `4e3f6bcf3a20a8c71711470dd1aa0361303acc5a`（提交前二次 `fetch` 复核：**未前进**，仍为 HEAD 祖先） |
| 净差异 | **14** 个路径（13 个实现/文档净差异 + 本任务文档），仅本任务自身改动 |
| 分支 | `dev3`，推送后 `origin/dev3` = `d6dae3da12` = 本地 HEAD |
| PR | **#457** → https://github.com/Silent1566/webhtv/pull/457 |

### 7.1 交付后复核（已执行）

| 复核项 | 结果 |
| --- | --- |
| `gh pr view 457` | `baseRefName=beta`、`headRefName=dev3`、`state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`、`isDraft=false` |
| `git ls-remote origin` | `refs/heads/dev3` = `d6dae3da12fec4c6054d63aa402d8d13bd175581`；tag 存在 |
| `git rev-list -n1 <tag>` | = `d6dae3da12fec4c6054d63aa402d8d13bd175581`（tag 指向正确） |
| 创建 PR 后再次 `fetch` | `origin/beta` 仍为 `4e3f6bcf3a20a8c71711470dd1aa0361303acc5a`（**未前进**），故本轮「无第二父」结论继续成立 |
| PR 文件集 vs `git diff --name-only origin/beta HEAD` | GitHub compare API 返回 **14** 个文件，与本地 **逐项一致**（`diff` 输出为空） |
| compare 状态 | `ahead_by=6`、`behind_by=0`、`status=ahead` |
| 分支保护 | **未合并、未关闭**，仅创建（符合任务要求「不要擅自通过 PR」） |

## 8. 回滚

本轮改动为**注释级**（4 行），回滚即还原 `ExoForegroundEofSignal.java` 与 `MediaSourceFactory.java` 的注释行；行为基线不受影响。更大的 `E-SP10` 阶段 1+2 回滚见 `docs/E-SP10-exo-eof-recovery-backoff-and-preload-yield.md` §10.6。
