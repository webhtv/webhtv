# C55：dev1 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入 `dev1`（**远端已移除/回退的提交不得顺带带回**）；复评 dev1 全部已修改代码（含**已提交未推送**的 7 个提交，PR 内容即缓存「临时文件清理」+「长按一键全清」功能）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev1`、创建 `dev1 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① `dev1` 必须包含合并时刻的 `origin/beta` tip；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev1 既有改动**零丢失**；④ 双 flavor Java 编译通过；⑤ 双 flavor AndroidTest Java 编译通过；⑥ 双 flavor 全量 JVM 套件零失败且增量可解释；⑦ UI token 门禁相对基线零新增违规；⑧ 净差异只含本分支自身改动；⑨ 复评发现的问题已修复并锁定（或按 `AGENTS.md` §2 明确记录处置）；⑩ 提交 + recovery tag、`dev1` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并完成（无冲突，`MERGE_HEAD` = beta tip）；2 轮复评完成；第 1 轮发现 **1 个真问题**（F1：缓存缺陷的**调用点级回归锁缺失**，产物在 JVM 层可原样复发而不被发现）并已修复 + 补 1 例锁定用例（负对照双向验证）；双 flavor 编译、双 flavor 全量 JVM 套件（leanback 650/4140、mobile 726/4980，0 失败）、UI token 门禁全部通过；零复活/零丢失四层证据通过。
- **交付坐标**：见文末「交付坐标」与「闭环记录」（合并提交 `447934222b88`、recovery tag、推送 `dev1`、PR #430 已创建且未合并）。
- **下一动作**：无（本任务已闭环：提交 + recovery tag + 推送 `dev1` + PR #430 已创建且未合并）。

## 时间与设备

- 任务开始时本地时间：2026-10-09 17:12（Asia/Shanghai），记录时 17:49；任务开始时工作区**干净**（`git status --porcelain` 空），protected 脏路径 0 个。
- `dev1` 相对 `origin/dev1`（`6bb920372`）**领先 7 个提交**，即 `origin/beta` 之后新增的 C54 上游合并链路：`f9f47c24a`、`92f184434`、`f0da126a2`、`814935cee`、`45bffbb86`（合并提交）、`92ad7c69d`。这 7 个提交全部属于**已提交未推送**，是本轮复评对象。
- 设备：**本轮未使用真机**。理由（风险相符原则，`AGENTS.md` §4）：本次唯一的代码改动是**测试文件**（源码守卫用例），不改变任何运行时行为，JVM 套件对它是决定性验证；而合并带入的两部分内容都是一字未改的既有已实测代码——beta 侧 dev4 的搜索下拉对比度修复已由 dev4 在 4 种组合（深/浅 × 搜索页/分组下拉）实机实测，dev1 侧 C54 的缓存功能已由 C54 在 `192.168.50.3:5555`（本轮 dev1 机位）双形态实机实测，且两者文件集合不相交（缓存包/设置入口 vs `SearchFragment`/`CollectFragment`）。`git diff origin/beta...dev1` 证明 dev1 与 beta 的主题代码逐字节相同，故 dev4 的对比度实测结论对合并后的 dev1 同样成立。因此未申请机位、未重装 APK（`192.168.50.3:5555` 的既有包未受影响）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1`（worktree `/home/maple/Workspace/webhtv/dev1/webhtv`） |
| 任务开始时 HEAD | `92ad7c69d13b1dbcb541f39bb9f87687f559bade`（`docs(c54)`，领先 `origin/dev1` 7 个提交） |
| `origin/dev1` | `6bb920372`（PR #428 交付坐标） |
| `origin/beta` tip（`git ls-remote origin refs/heads/beta`） | `7e51768d881a725754dd68321ab802680ea30e1c`（Merge pull request #429 from Silent1566/dev4） |
| 合并基点（merge-base） | `35a5a63f8a526ca9e6a54d1bf59b941d1f03d840`（= PR #428 合并提交，= `dev1` 中 C54 合并提交的第一父） |
| 合并命令与结果 | `git merge --no-commit --no-ff origin/beta` → **「自动合并进展顺利，按要求在提交前停止」**（`EXIT=0`，**0 冲突**，`git diff --name-only --diff-filter=U` 为空） |
| `MERGE_HEAD` | `7e51768d881a725754dd68321ab802680ea30e1c`（= beta tip，即合并提交第二父） |
| 合并结果树 | `git write-tree` = `0d68f90990fb6d9c3d03c5f523f9ffe22567863c` |
| 是否需要新合并提交 | **需要**：`git merge-base --is-ancestor origin/beta HEAD` 在合并前为**假**（dev1 缺少 beta 的 4 个提交），故本轮产生真实合并提交（由 `task_guard.sh finish` 生成） |
| 初始脏路径 | 无 |
| 回滚锚点 | `92ad7c69d13b1dbcb541f39bb9f87687f559bade` |

### 合并增量 ledger（beta 侧 4 个提交，全部纳入）

`git log --oneline --format='%H %s' dev1..origin/beta`（本任务开始时）：

| 完整 commit ID | 标题 | 主要路径 | 处置 |
| --- | --- | --- | --- |
| `42b03f028e9b8f29ff6f803571952a82f6d91e7e` | `fix(mobile): 修复深色模式搜索分组下拉面板对比度不足` | `SearchFragment.java`、`CollectFragment.java`、`SearchScopePopupLayoutTest.java`（2→4 例）、`docs/MOBILE-SEARCH-DROPDOWN-CONTRAST-20261009.md` | 纳入 |
| `08d4b6611802db1f3349aff828df9d08b42c5d69` | `merge: 合并 origin/beta 最新代码（PR#427 小说朗读 / PR#428 去广告总时长）并复评 dev4 搜索下拉对比度修复` | 合并提交（第二父 `35a5a63f8`，即 dev1 已有的提交） | 纳入 |
| `27fe5c6923bd75b4c1e5cda0d91f7f6a6b5c8d1e` | `docs(c54): 记录 dev4 交付坐标（合并提交 08d4b661180 / recovery tag / 推送 / PR #429）` | `docs/C54-beta-merge-review-dev4-20261009.md` | 纳入 |
| `7e51768d881a725754dd68321ab802680ea30e1c` | `Merge pull request #429 from Silent1566/dev4` | 合并提交（beta tip） | 纳入（合并第二父） |

`git diff --name-status dev1...origin/beta`（三点，仅 beta 侧变化）= **5 路径**：`SearchFragment.java`(M)、`CollectFragment.java`(M)、`SearchScopePopupLayoutTest.java`(M)、`docs/C54-beta-merge-review-dev4-20261009.md`(A)、`docs/MOBILE-SEARCH-DROPDOWN-CONTRAST-20261009.md`(A)。

**0 冲突的理由**：beta 侧 5 个路径与 dev1 侧 13 个路径**完全不相交**（前者在 `app/src/mobile/`+`app/src/testMobile/`+`docs/`，后者在 `app/src/main/java/.../cache/`+`app/src/main/java/.../ui/dialog/`+两个设置入口+三套 `strings.xml`+`app/src/test/`+`docs/`），`docs/` 下文件名亦不重叠；故 `git merge` 走纯 fast-forward-of-paths 的自动合并，无需任何人工取舍，也就不存在「合并时把回退内容捡回来」的空间。

### 合并结果净差异（合并后相对 beta tip）

`git diff --name-status origin/beta 0d68f909` = **13 路径**（11 修改 + 2 新增），全部来自 dev1 自身（C54 缓存功能），无一条来自 beta 之外：

| 路径 | 类型 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/cache/CacheCleanupManager.java` | 修改 |
| `app/src/main/java/com/fongmi/android/tv/cache/CacheCleanupMode.java` | 修改 |
| `app/src/main/java/com/fongmi/android/tv/cache/CachePolicyEngine.java` | 修改 |
| `app/src/main/java/com/fongmi/android/tv/ui/dialog/CacheManagementDialog.java` | 修改 |
| `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingActivity.java` | 修改 |
| `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingFragment.java` | 修改 |
| `app/src/main/res/values/strings.xml` / `values-zh-rCN/` / `values-zh-rTW/` | 修改 |
| `app/src/test/java/com/fongmi/android/tv/cache/CacheFullCleanupTest.java` | 新增 |
| `app/src/test/java/com/fongmi/android/tv/cache/CachePolicyEngineTest.java` | 修改 |
| `docs/C54-upstream-sync-silent1566.md` | 新增 |
| `docs/CACHE-MGMT-01-cache-management-design.md` | 修改 |

规模：13 文件 `+776 −49`（含本轮新增的 67 行守卫用例；不含本任务文档）。

## 用户核心关注点：远端已移除（回退）内容零复活

采用与 C41/C46/C48/C50/C52/C53/C54 一脉的四层递进证据，全部程序化重算。

### 第一层：结构性证明（最强证据，四向闭合）

```text
① git diff --name-status 92ad7c69d <合并树>          = 恰好 5 路径（= beta 带入的 5 路径）
   → 合并树中除这 5 个路径外的每一个路径都与 dev1 HEAD 逐字节相同 ⇒ dev1 既有改动零丢失
② git diff --stat 92ad7c69d <合并树> -- <dev1 的 13 路径> = 空
   → dev1 自己拥有的 13 个路径在整个合并中未被改动一个字节 ⇒ 合并没有覆盖/丢失本地实现
③ git diff --name-status origin/beta <合并树>          = 13 路径，全部为①所列 dev1 自身改动
   git diff --diff-filter=D --name-status origin/beta <合并树> = 空
   → beta 的每一个字节都被保留（含被回退后复出的文件一律不在删除集/替换集中）⇒ beta 增量零丢失
④ git ls-tree -r origin/beta 与合并树逐路径对比：
   beta 有而合并树缺失的文件 = 0
   合并树独有的文件 = 2（CacheFullCleanupTest.java、docs/C54-upstream-sync-silent1566.md，均为 dev1 自身）
   → 合并树中每一个字节要么来自 beta tip，要么来自 dev1 HEAD 的 13 个路径
```

**结论**：合并内容在结构上只能来自「beta tip」或「本分支自身」，**从 beta 之外（含任何历史回退提交）引入内容在结构上不可能** → **结构性零复活 + 结构性零丢失**。这也是对用户特别提示「远端已经移除回退的提交不能再顺带提交上去」的直接回答：本轮合并是纯内容合并，未做任何 `--no-ff` 之外的复活式操作，也未把 beta 已删除的内容从历史里捡回。

### 第二层：行级复活扫描（严格判据）

对 beta 上主题命中 `revert|remove|removal|delete|drop|回退|移除|删除|剔除|撤销` 且**单亲**的提交 `R` 取父 `R^`，用 `git diff --no-renames -U0 R^ R` 收集删除行；复活定义 = 「该行出现在 `git diff origin/beta <合并树>` 的新增行中，且该行在 `origin/beta` tip 的**任何**文件中都不存在」（只有这一层严格判据可信）。

```text
beta 历史提交总数                                    : 4330
主题命中移除类且单亲的提交                            : 139
移除类提交收集到的删除行 distinct                     : 27862
diff origin/beta..<合并树> 的新增行 distinct           : 540
「新增行 ∩ 删除行」表面命中                            : 43
    —— 全部为通用脚手架行：空行、`}`、`} else {`、`break;`、`continue;`、
       `return;`、`return true;`、`try {`、`*` 等，这些行在 beta tip 中大量存在，属扫描噪声
严格判据（该行在 beta tip 中不存在）的复活行数          : 0
```

**结论**：零复活（第二层与第一层一致；表面命中全部为 beta tip 自身存在的通用行）。

### 第三层：关键移除目标与净差异核对

- beta 已知移除项（`1b42d6624` 手机版个性设置触屏优化入口、`be1b02e06` 动态主题色系统 revert）在本轮 `git diff --name-status origin/beta <合并树>` 中**无对应路径**，未被触碰；
- `git grep -nE '^(<<<<<<<|>>>>>>>)' -- app/src docs` 命中 **0**；`git diff --check` 退出码 **0**；
- 合并后 `git fetch origin --prune` 复核 beta 未前进（仍为 `7e51768d8`）。

**结论：零复活 + 零丢失。**

## 复评记录

复评对象 = `dev1` 全部已修改代码（含 7 个已提交未推送提交），即 `git diff origin/beta <合并树>` 的 13 路径 / `+776 −49`。

### 第 1 轮：逐模块复核

| 模块 | 复核项 | 判定 |
| --- | --- | --- |
| `CacheCleanupMode.FULL` + `CachePolicyEngine.modules(FULL)` | 新枚举常量是否使所有 `switch`/等价判断穷尽（`deepModules()` 用 `allOf` 后 `remove`，`modules(mode)` 已补 `FULL` 分支）；是否存在按 `ordinal()` 索引的数组、`EnumMap` 或 mode→文案映射表会因新常量而出错 | 通过（全仓 `CacheCleanupMode` 引用逐一核对：仅 `plan/module/automaticPlan/directCleanupStatus` 与 UI 的 `LIGHT/STANDARD/DEEP/MODULE` 入口，无 ordinal/EnumMap；journal 以 Gson 存枚举名，无渲染映射表） |
| `CacheCleanupManager.run()` 的 FULL 分派 | `plan.mode() == FULL` 时是否确实走 `cleanEverything`，且 tiered 路径未被影响 | 通过（分派在 `run()` 唯一入口；`plan.modules()` 非空的守卫仍在最前） |
| `cleanEverything()` 结构 | `before` 是否在任何删除前测量；`total`/进度是否一致；deferred 模块是否把**注册表里的根**放入保留集；`success`/`deferred`→状态的映射是否与面板文案契约一致；异常是否被捕获为 PARTIAL 而非抛出 | 通过（`measureCache` 在任何模块清理前调用；`total = modules+1` 与末次 `UNCLASSIFIED` 进度对应；`moduleRoots(id, registry)` 取自注册表故与清单计数同源；`success && !deferred`→COMPLETED、否则 PARTIAL，与 `describe()` 的 3 参文案一致） |
| `sweepResidual()` / `sweepEntry()` | 保留集合是否覆盖「deferred 播放缓存根 + 运行中加载器脚本」；`PROTECTED_NAMES`（`mpv-playback-recovery.*`）、符号链接、进行中传输是否豁免；「子项被保留则不删父目录」是否会误删保留项 | 通过（`preserved.contains(file.getAbsolutePath())` 在递归入口即短路；`remaining.length > 0` 时保留父目录；三处豁免在删除前判定） |
| `explicitRequest()` / `explicitRetention()` 收敛 | `executeCleanup` 中是否还有残留的 `mode == MODULE`/窗口字面量；歌词/K 歌/EPG/插件/临时文件/遗留路径六处是否全部经由共享规则；`WEBHOME_EXT` 等未改动分支是否保持原语义 | 通过（`grep -n mode CacheCleanupManager.java` 逐一核对，判定点只剩 `explicitRequest`/`explicitRetention`；`LYRICS/KARAOKE/EPG` 已换 `now`，`PLUGIN_SCRIPTS/TEMP_FILES/LEGACY_FILES` 走 `explicitRetention`） |
| `clearTemporaryFiles(cache, retentionMs, limitBytes, updaterDownloading, apkUrlPushing, now)` | 入参注入后语义是否与原「自读时钟/传输状态」等价；`retentionMs <= 0`（显式请求）是否真的不过期；`isInUse` 是否仍在删除前判定；`enforceFileLimit` 的 `remaining` 是否仍为残留集 | 通过（`isExpired(file, now, 0)` 恒真 ⇒ 除 in-use 外全删；`CacheTempFilePolicy.isInUse` 判定在 `delete()` 之前） |
| `SWEEP`/`cleanEverything` 与自动链路的关系 | `FULL` 是否可能被自动/调度链路产出（`CacheScheduler`/`CacheAutoCleanupPolicy`/`CacheCleanupJobService`） | 通过（自动链路只会产出 `LIGHT`/`STANDARD`；`CacheAutoPlanAndTempFamilyTest.everyAutomaticPlanRespectsTheRegistryDeclaration` 遍历 `values()` 仍通过） |
| 两个设置入口（长按） | 长按是否只做「全清」且不打开面板；单击仍走原路径（管理开=面板、关=旧一键全清）；返回 `true` 是否消费手势 | 通过（`onCacheLongClick` 两处对称；`onCache` 未被改动） |
| `CacheManagementDialog.cleanEverything()/describe()` | 静态入口是否复用唯一文案构造；资源来源是否正确（面板=Activity 资源、长按=`Setting.wrapLanguage(App.get())`）；`isRunning()` 竞态是否有兜底 | 通过（`execute()` 内部对「已在运行」也有 FAILED 回调兜底） |
| 新增/改动测试**有效性** | 断言是否精确（非弱断言）；**回退生产改动时是否会转红** | **不通过 → F1** |
| `docs/CACHE-MGMT-01-…` 与 `docs/C54-upstream-sync-silent1566.md` | 文档所述缺陷/修复/验证数字是否与实现和实测一致 | **不通过（表述过强）→ F1 的文档面** |
| 影响面 | 是否触碰净化判据、删除阈值、播放器链路、统计写入、`res/`、native、依赖锁文件 | 通过（净差异仅 Java + `strings.xml` 三语各 1 行 + 两篇文档；无 `res/layout`、无 native、无锁文件、无依赖变更） |

**第 1 轮结论**：发现 **1 个真问题 F1**（缓存缺陷的**调用点级回归锁缺失**，且功能设计文档把它记为「已锁住」）；其余全部通过。无功能性缺陷。

### 第 2 轮：修复后复审

| 复审项 | 证据 | 判定 |
| --- | --- | --- |
| F1 修复是否真的锁住调用点 | 负对照 B1：把 `case TEMP_FILES` 调用点改回 `TEMP_RETENTION_MS`（精确复现用户报告的缺陷）→ `CacheFullCleanupTest.everyCleanupCallSiteUsesTheSharedRule` **转红**，失败行 = 该断言所在行 206 | 通过 |
| 负对照 B2：全清分派被破坏时是否转红 | `if (plan.mode() == CacheCleanupMode.FULL)` 改为 `if (false && …)`（长按会静默退化为分级清理）→ **转红**，失败信息 `FULL must select the full-clean path: …` | 通过 |
| 正向：正确源码上是否全绿（不是恒真断言） | 正确源码 + 守卫 → `CacheFullCleanupTest` **8/8 通过**，缓存包 **72/72 通过** | 通过 |
| 断言是否精确、是否会被无关改动误伤 | 断言为逐条精确字符串（`case LYRICS -> now`、`explicitRetention(mode, TEMP_RETENTION_MS)` 等）；窗口扫描按行过滤注释行（`//`、`*` 开头行跳过），文档性改动不会误报；`== CacheCleanupMode.MODULE` 只针对修复所替换的那一个比较式，不对「任何提到 MODULE 的地方」过宽 | 通过 |
| 是否改变行为、是否泄漏到无关模块 | 第 2 轮改动仅 `CacheFullCleanupTest.java`（+67 行，纯测试）；生产文件 `CacheCleanupManager.java` 由 `sha256sum -c` 证明字节未变；`git diff --stat` 仅 1 文件 | 通过 |
| 是否与仓库既有约定一致 | 采用仓库既有的「源码守卫」约定（同 `SettingTmdbSourceOnlyWiringTest`、`AppBrandingContractTest`、`ThemeBaseWiringTest` 等的 `Files.readString` + 精确断言写法，`root()` 解析方式一致） | 通过 |
| 结论 | 第 2 轮复审全部通过，无剩余阻塞项；可进入收口 | 通过 |

## 发现与处置

### F1（已修复）：缓存两个缺陷的「调用点级回归锁」缺失，而文档已把它记为「已锁住」

**证据链（三段，全部实测）**

1. **现有用例从不执行产线调用点。** `CacheFullCleanupTest` 的两条临时文件用例自己把保留期算好后交给 `clearTemporaryFiles`（`explicitRetention(CacheCleanupMode.MODULE, DAY_MS)` / `(LIGHT, DAY_MS)`），`sweepResidual` 也是直接调用；而缺陷真正所在的调用点在 `CacheCleanupManager.executeCleanup(...)` 内部，它需要 `App.get().getCacheDir()`、`CachePolicyStore.getLimit()`、`Updater.isDownloading()`、`ApkUrlPush.isActive()` 与播放状态，JVM 单测无法到达。
2. **负对照 A（证明缺口真实存在）**：把调用点改回缺陷形态

   ```java
   // 修复前（缺陷态，= 用户报告的「释放 无 · 删除 0 个文件」）
   case TEMP_FILES -> clearTemporaryFiles(cache, TEMP_RETENTION_MS, limit, ...)
   ```

   然后跑整个缓存测试包 → `BUILD SUCCESSFUL`、**suites=14 tests=71 failures=0 errors=0**。即：**用户报告的缺陷可以在 unittest 全绿的情况下原样复发**。
3. **文档与实现的偏差**：功能设计文档在上一轮评审记录中把这处写成「保留期判定收敛为单一表达式 …，使『临时文件清理无效』缺陷真正被回归锁锁住」，而第 2 段的负对照说明被锁住的只是**共享判定函数**，不是**调用点**；C54 的文档也已把「调用点级回归锁缺失」列为遗留观察。本轮据此把该缺口按 `AGENTS.md` §4「测试有效性：回退生产改动必须转红」的标准收口。

**修复**：按仓库既有的**源码守卫**约定（JVM 套件里锁定 Android-only 调用点的既定做法），在 `CacheFullCleanupTest` 新增 `everyCleanupCallSiteUsesTheSharedRule()`，断言三类不变量：

- 全清分派：`run()` 必须包含 `if (plan.mode() == CacheCleanupMode.FULL) {` 且仍调用 `cleanEverything(plan, progress)`（防止「长按一键全清」被静默降级为分级清理）；
- 共享规则：`executeCleanup()` 必须包含 `boolean now = explicitRequest(mode);`，且 `LYRICS/KARAOKE/EPG` 用 `now`、`PLUGIN_SCRIPTS/TEMP_FILES` 用 `explicitRetention(...)`、`LEGACY_FILES` 委托 `clearLegacyPaths`；`clearLegacyPaths()` 必须包含 `long retentionMs = explicitRetention(mode, LEGACY_RETENTION_MS);`；并禁止 `== CacheCleanupMode.MODULE` 这一被替换掉的旧比较式复现；
- 窗口字面量禁令：`executeCleanup`/`clearLegacyPaths` 两个方法体内，任何含 `TEMP_RETENTION_MS`/`LEGACY_RETENTION_MS` 的**代码行**都必须同时含 `explicitRetention(`（注释行跳过），这条正是负对照 B1 命中的断言。

**锁定验证（负对照双向）**

| 变异 | 期望 | 实测 |
| --- | --- | --- |
| B1：`case TEMP_FILES` 调用点改回 `TEMP_RETENTION_MS`（用户报告的缺陷） | 守卫转红 | `CacheFullCleanupTest 8 tests, 1 failed`，失败点 = `CacheFullCleanupTest.java:206`（正确的调用点断言）✅ |
| B2：`if (plan.mode() == CacheCleanupMode.FULL)` → `if (false && …)` | 守卫转红 | `CacheFullCleanupTest` 失败，信息 `FULL must select the full-clean path: …` ✅ |
| 恢复（两次变异后均以备份文件还原） | 生产文件字节不变 | `sha256sum -c` OK（`f7540b76…`），`git diff` 对该文件为空 ✅ |

### 只记录不修（按 `AGENTS.md` §2，均为上一轮已登记项，本轮逐条复核仍成立）

| 编号 | 观察项 | 复核结论 / 不修理由 |
| --- | --- | --- |
| N1 | 三条 HLS 通道/三条缓存通道的兜底计数表达式不完全相同 | 与 C54 结论一致：逐一验算等价，改动只有重写收益、无行为收益 |
| N2 | `cleanEverything()` 把清理后**剩余文件数**传给 `skippedFiles`（仅 `PARTIAL` 文案会显示「跳过 N 个文件」） | 语义在 `PARTIAL` 下等价于「保留/剩余 N 个文件」；改它需要区分「剩余」与「跳过」两个概念，属范围外 |
| N3 | 清理进行中重复长按会被 `isRunning()` 静默丢弃 | 上游设计已声明该权衡（清理通常 200–650 ms）；`execute()` 内部仍有 FAILED 回调兜底 |
| N4 | `FULL` 会连缓存根下应用自有状态文件一起清（`episode_positions.json`、`*.lck`、`WebView/chaquopy` 等） | 与拆分前 `FileUtil.clearCache()`（`Path.clear(Path.cache())`）语义一致，C54 已双形态实测「冷启动无崩溃、缓存自动重建」 |
| N5 | `sweepResidual` 只豁免进行中的 `update.apk`/`pushed-url-*`，NanoHTTPD 的 `cache/NanoHTTPD-*` 上传分片会被全清 | 与拆分前行为一致；要收口须先改变「全清 = cache 目录清空」的语义，需用户明确同意，属独立需求 |
| N6 | 文案硬编码中文，未走 `strings.xml` | 与三条通道原实现一致，本次未新增本地化缺口 |

**功能性缺陷：0。** 本轮唯一的真实问题是 F1（回归锁 + 文档表述），已修复。

## 验证

日志目录：`build/c55-verify/`（构建产物，不入库）。

### 4.1 负对照（证明 F1 是真实缺口，且修复后锁有效）

见「发现与处置 → F1」的三条实测：负对照 A（缺口感：缓存包 **71/71 全绿**，即缺陷可复发而不被发现）、负对照 B1/B2（修复后转红，且失败点/失败信息指向被保护的调用点）。

### 4.2 定向缓存包单测

```bash
bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.cache.*"
```

`BUILD SUCCESSFUL`、`EXIT=0`。JUnit XML 实测：

| 阶段 | suites | tests | failures | errors |
| --- | --- | --- | --- | --- |
| 修复前（负对照 A，缺陷态源码） | 14 | 71 | 0 | 0 |
| 修复后（正确源码 + 守卫） | 14 | **72** | **0** | **0** |

（`CacheFullCleanupTest` 由 7 例增至 8 例，新增的正是 F1 的调用点守卫。）

### 4.3 双 flavor Java / AndroidTest 编译 + 双 flavor 全量 JVM 套件

单次 Gradle 调用（避免重复检查），6 个目标任务全部**实际执行**（日志中均为 `> Task :app:<task>`，无 `UP-TO-DATE`）：

```bash
bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac \
  :app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac :app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac \
  :app:testLeanbackArm64_v8aDebugUnitTest :app:testMobileArm64_v8aDebugUnitTest
```

`BUILD SUCCESSFUL in 1m 23s`、`EXIT=0`。解析 JUnit XML：

| flavor | suites | tests | failures | errors | skipped | C54 基线 | 差值 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| leanback | 650 | **4140** | **0** | **0** | 2 | 650 / 4139 | **+1 例** |
| mobile | 726 | **4980** | **0** | **0** | 2 | 726 / 4977 | **+3 例** |

差值逐项可解释（suite 数不变，因为两个来源文件本就已存在）：

- leanback `+1` = 本轮 F1 的调用点守卫 1 例（`CacheFullCleanupTest` 只有 `app/src/test` 一份，两 flavor 共享）；
- mobile `+3` = 同一守卫 1 例 + 合并带入的 beta 提交 `42b03f028` 把 `app/src/testMobile/.../SearchScopePopupLayoutTest.java` 由 **2 例增至 4 例**（`git show 92ad7c69d:<该文件>` 计 `@Test` = 2，合并后 = 4；XML 实测 `Mobile SearchScopePopupLayoutTest tests= 4`）。leanback 不含 `app/src/testMobile`，故不受影响。

无失败、无错误、无新增跳过。

### 4.4 UI token 门禁

```text
UI_TOKEN_BASELINE layouts=385 hex_layouts=1 drawables=554 hex_drawables=0 colors=59 hex_colors=0 allowlisted=190
UI_TOKEN_SCOPE    stage=A violations=1 legacy=0
UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28
UI_TOKEN_STATUS   PASS
```

唯一命中 `app/src/mobile/res/layout/item_following.xml`，**不在本次改动集内**，与 C50/C52/C53/C54 基线**逐项一致** → 相对基线**零新增违规**。

### 4.5 静态与结构校验

- 零复活/零丢失：四层证据全部通过（严格判据复活行数 **0**，`--diff-filter=D` 为空，beta 有而合并树缺的文件 **0**）；
- `git diff --check` 退出码 **0**；`git grep -nE '^(<<<<<<<|>>>>>>>)' -- app/src docs` 命中 **0**；
- 生产文件在两次变异后 `sha256sum -c` 字节还原（`f7540b763c584e1551ba2d7080768998cbfd6ffd3a11e7f16c8d0945063d9a64`）；
- `git fetch origin --prune` 收尾复核 beta 未前进（`7e51768d8`）；
- 改动未触及 `res/layout`、native、依赖锁文件、播放器链路。

### 4.6 回滚

- 本轮合并提交是一个 merge commit（第二父 = `7e51768d8`）：`git revert -m 1 <merge-commit>` 即回到 `92ad7c69d`；
- F1 的守卫是纯测试：删除 `CacheFullCleanupTest.everyCleanupCallSiteUsesTheSharedRule()` 与两个私有 helper 即回到上一轮状态，无运行时影响；
- 无数据迁移、无 ABI/native 变更、无依赖变更、无播放行为变更。

## 改动清单（C55 相对本次合并树）

| 路径 | 类型 | 说明 |
| --- | --- | --- |
| `app/src/test/java/com/fongmi/android/tv/cache/CacheFullCleanupTest.java` | 修改（+67） | F1：新增调用点级源码守卫 `everyCleanupCallSiteUsesTheSharedRule()` 与 `method()`/`productionSource()` 两个 helper，锁定「FULL 分派」「六个模块共享保留期规则」「不得在调用点写窗口字面量」三类不变量 |
| `docs/C55-beta-merge-review-dev1-20261009.md` | 新增 | 本任务文档 |
| `docs/CACHE-MGMT-01-cache-management-design.md` | 修改 | 追加本轮（C55）合并与复评记录：调用点回归锁已补齐，订正上一轮「已锁住」的表述 |

（合并提交本身另含 beta 带入的 5 个路径，见「合并增量 ledger」，它们不是本轮新增的工作。）

## 交付坐标

| 项 | 值 |
| --- | --- |
| 任务起始 HEAD | `92ad7c69d13b1dbcb541f39bb9f87687f559bade` |
| `origin/beta` tip | `7e51768d881a725754dd68321ab802680ea30e1c`（= 合并提交第二父） |
| 合并结果树 | `0d68f90990fb6d9c3d03c5f523f9ffe22567863c`（重做合并复现同一树） |
| 合并提交 | `447934222b88934720b387225d7d561e781108ac`（`rev-list --parents` 实测 = `92ad7c69d` + `7e51768d8`，确为 merge commit） |
| recovery tag | `recovery/C55-beta-merge-review-dev1/20261009175202-447934222b88`（annotated，指向合并提交，0s） |
| 各任务守卫 | `C55-beta-merge-review-dev1`（standard，`start` 在任何编辑之前，base HEAD `92ad7c69d`）；坐标守卫 `C55-beta-merge-review-dev1-coordinates`（quick-fix，scope `docs`，base HEAD `447934222`） |
| 推送 | `dev1` → `origin/dev1`：`6bb920372..447934222`；推送后 `git rev-parse origin/dev1` == `git rev-parse dev1` == `447934222b88934720b387225d7d561e781108ac`（0 ahead / 0 behind）；坐标提交再推一次 |
| PR | [#430](https://github.com/Silent1566/webhtv/pull/430) `dev1 -> beta`，**OPEN、未合并**（`mergedAt=null`、`state=OPEN`、`mergeable=MERGEABLE`、`mergeStateStatus=CLEAN`）；**只创建，未合并** |
| PR 文件集校验 | `gh api repos/Silent1566/webhtv/pulls/430/files --paginate` 合计 **14**，与 `git diff --name-only origin/beta HEAD` **逐项一致**（`diff` 无输出） |
| PR 变更规模 | 14 文件 `+1140 −49` |
| 设备 | 本轮未使用真机（理由见「时间与设备」）；`192.168.50.3:5555` 的既有包未受影响 |

### 闭环记录

- **合并判定**：合并前 `git merge-base --is-ancestor origin/beta HEAD` 为**假**（dev1 缺少 beta 的 4 个提交），故本轮产生真实合并提交；`git merge --no-commit --no-ff origin/beta` 返回「自动合并进展顺利」且 **0 冲突**，`MERGE_HEAD=7e51768d8`，`git write-tree=0d68f909`，与四层证据所用树同一。收尾前 `git fetch origin --prune` 复核 beta 未前进（仍 `7e51768d8`），`git merge-base --is-ancestor origin/beta HEAD` 为**真**。
- **意外与恢复**：收口前一次 `git add -A && git reset` 的预检把 `MERGE_HEAD` 一并清除（`git reset` 会清理合并状态）。已按「先把 5 个 beta 路径还原为 HEAD 内容 + 删除 2 个 beta 新增文档（内容保存在 beta tip，无丢失）→ 重跑同一条 `git merge --no-commit --no-ff origin/beta`」恢复，恢复后 `git write-tree` 与恢复前**完全相同的 `0d68f909`**，且本人改动 3 个文件 `sha256sum -c` 逐一 OK；最终提交 `rev-list --parents` 实测为两个父提交，合并语义未被削弱。预检此后改为 `git add` 后立即 `git diff --cached --check`、不再使用 `git reset`。
- **复评循环**：第 1 轮发现 F1（调用点级回归锁缺失）→ 补源码守卫用例 + 两向变异验证 → 第 2 轮复审全部通过 → 收口。
- **交付**：`447934222` 已提交并带 recovery tag → 推送 `dev1`（`6bb920372..447934222`）→ 创建 PR #430（只创建、未合并）→ 本坐标提交再推送一次（PR 自动更新）。
