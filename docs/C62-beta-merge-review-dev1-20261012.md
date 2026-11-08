# C62：dev1 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入 `dev1`（**远端已移除/回退的提交不得顺带带回**）；复评 dev1 全部已修改代码（含**已提交未推送**的 Android 7 支持 8 个提交）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev1`、创建 `dev1 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① `dev1` 必须包含合并时刻的 `origin/beta` tip（真实双父合并提交）；② 远端被移除/回退内容**零复活**、beta 增量**零丢失**、dev1 既有改动**零丢失**；③ 双 flavor Java 编译 + 双 flavor AndroidTest Java 编译通过；④ 双 flavor 全量 JVM 套件零失败且增量可解释；⑤ UI token 门禁相对基线零新增违规；⑥ 复评发现的问题已修复并锁定（或按 `AGENTS.md` §2 明确记录处置）；⑦ 提交 + recovery tag、`dev1` 已推送、PR 已创建且**未合并**，PR 文件集与 `git diff --name-only origin/beta HEAD` 逐项一致。
- **当前状态**：合并完成（0 冲突，`MERGE_HEAD` = beta tip）；复评 **2 轮**完成；第 1 轮发现 **3 项问题**（F1 三处 Android 7 修复缺少回归锁、F2 `MpdSanitizer` 影响机制描述错误、F3 两处数字/口径错误）并已修复，第 2 轮复审通过；双 flavor 编译 + AndroidTest 编译 + 双 flavor 全量 JVM 套件（leanback 4203/0、mobile 5018/0）+ UI token 门禁 + 变异验证全部通过；零复活/零丢失证据通过；**已提交（双父合并提交 `11578a6ee` + recovery tag）、已推送 `dev1`、已创建 PR #438（未合并）**。
- **交付坐标**：见文末「交付坐标」与「闭环记录」。
- **下一动作**：无（本任务闭环；PR 由用户决定是否合并）。

## 时间与设备

- 任务开始时本地时间：2026-10-12 10:31（Asia/Shanghai）；记录时 11:10。
- 任务开始时工作区仅 `.scratch-goal/` 未跟踪（C59/C60 的取证目录，**不提交**）；受保护的既有脏路径 371 条，全程未触碰。
- `dev1` HEAD = `8daf00907e9066c1f6b2c0319e273f9181e536a6`，领先 `origin/dev1`（`565fac1fd27ffac9acf7a1211e15857cf7ab5c5c`）**9 个提交**，其中第 1 个（`8ed903ac2`「Merge pull request #436 from Silent1566/dev1」）已是 `origin/beta` 的祖先（即本轮的合并基点），后 8 个是本轮的复评对象（已提交未推送）：
  - `4e4bfffcb` `fix(android7): guard View#setImportantForAutofill (API 26) behind ViewCompat`
  - `9564c3e58` / `22fe62581` / `0b036f9d0` / `29c281063` / `8daf00907` 文档与结论提交
  - `656d3dcbc` `fix(android7): replace Process#isAlive (API 26) with an API-1 compatible check`
  - `8bb62a790` `fix(mpd): use API-1 StringBuffer Matcher overloads instead of API 34`
- 设备：**本轮未使用真机/模拟器**。理由（风险相符原则，`AGENTS.md` §4）：本轮对生产代码的改动为 **0**（只新增 3 个用例类 + 订正文档），三个 Android 7 修复本身已由上一轮在 `192.168.50.3:5587`（Android 7.1.2 / API 25）实机验证（见 `docs/android7-support-audit-2026-10-11.md` §9）；合并带入的 beta 侧改动（PR#435/#437）已由 dev2/dev4 各自验证。故未申请机位、未重装 APK。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1`（worktree `/home/maple/Workspace/webhtv/dev1/webhtv`） |
| 任务开始时 HEAD | `8daf00907e9066c1f6b2c0319e273f9181e536a6` |
| `origin/dev1` | `565fac1fd27ffac9acf7a1211e15857cf7ab5c5c` |
| `origin/beta` tip（`git ls-remote origin refs/heads/beta`，收尾复核未推进） | `af9a18fa47048893b438ab24c9b338041fa306c1`（Merge pull request #437 from Silent1566/dev4） |
| 合并基点（merge-base） | `8ed903ac2a75bf76b46ab04e17059a30070d4b63`（Merge pull request #436 from Silent1566/dev1） |
| 合并命令与结果 | `git merge --no-commit --no-ff origin/beta` → **「自动合并进展顺利，按要求在提交前停止」**（`EXIT=0`，**0 冲突**，`git diff --name-only --diff-filter=U` 为空） |
| `MERGE_HEAD` | `af9a18fa47048893b438ab24c9b338041fa306c1`（= beta tip，即合并提交第二父） |
| 合并结果树 `git write-tree` | `3270ead81eb4af842a963866019f2e5e5714e6e2` |
| 合并前 HEAD 树 | `1d8a3de7c36d1d4ea4b5aa0133b026b837f60e01`（与合并结果树**不同** → 本次合并确实引入 beta 侧内容，必须产生真实合并提交） |
| beta tip 树 | `c51a7543fc977f2b7647f7033152ea125d73109f` |
| 是否需要新合并提交 | **需要**：合并前 `git merge-base --is-ancestor origin/beta HEAD` 为**假**（`af9a18fa4` 不在 dev1 上），故由 `task_guard.sh finish` 生成真实合并提交，第二父 = beta tip |
| 初始脏路径 | `.scratch-goal/`（371 条，登记为 protected，不提交） |
| 回滚锚点 | `8daf00907e9066c1f6b2c0319e273f9181e536a6` |

### 合并增量 ledger（beta 侧 6 个提交，全部纳入）

`git log --format="%H %P %s" 8ed903ac2..origin/beta`：

| 完整 commit ID | 父 | 标题 | 处置 |
| --- | --- | --- | --- |
| `af9a18fa47048893b438ab24c9b338041fa306c1` | `8ed903ac2` + `4b35e0720` | `Merge pull request #437 from Silent1566/dev4` | 纳入（合并第二父） |
| `4b35e0720c118bd71464abf47dab5a1b55998ad3` | `3e657bef0` | `docs(c58): 记录 dev4 合并评审交付坐标（合并提交 3e657bef0d9 / recovery tag / 推送 / PR #437）` | 纳入 |
| `3e657bef0d978e8155803f2b19904b0a3e5a88cd` | `e0c067cba` + `8ed903ac2` | `merge: 合并 beta 最新代码（PR#435/#436 停播/更新判定/数据库完整性）并复评 dev4 屏显对比度改动` | 纳入（嵌套合并提交） |
| `e0c067cba71c51b55058f365b7d85646e00e808e` | `fec5af7aa` + `fb4fd7d99` | `merge: 合并 beta 最新代码并复评 dev4 屏显对比度改动` | 纳入（嵌套合并提交） |
| `fec5af7aa54ff36df45ce919151a4d7fc9b74f9c` | `bd78d7b52` | `test(tv): 锁定屏显入口对比度链路` | 纳入 |
| `bd78d7b528fb61c4dc89f8eb0655085c878b4a87` | `819003db8` | `fix(tv): 全面修复设置列表与焦点控件对比度` | 纳入 |

**beta 侧本轮增量无任何删除/回退提交**：`git log --diff-filter=DR --name-status 8ed903ac2..origin/beta` 输出为**空**（内容为 dev4 的屏显/设置列表对比度修复与文档）。

### 合并结果净差异（合并后相对 beta tip）

`git diff --name-status origin/beta 3270ead81eb4af842a963866019f2e5e5714e6e2` = **6 路径**（5 修改 + 1 新增），全部来自 dev1 自己的 Android 7 改动：

| 类型 | 路径 |
| --- | --- |
| M | `app/src/main/java/com/fongmi/android/tv/lab/LabRunner.java`、`.../lab/LabTerminalActivity.java`、`.../player/extractor/MpdSanitizer.java`、`.../ui/dialog/GitCloudDialog.java`、`.../ui/dialog/RemoteTrustDialog.java` |
| A | `docs/android7-support-audit-2026-10-11.md` |

`git diff --diff-filter=D --name-status origin/beta <合并树>` 为**空**。

## 零复活三层证据（严格判据）

### 第 1 层：结构性

| 判据 | 结果 |
| --- | --- |
| `git merge-base --is-ancestor origin/beta HEAD`（合并前） | **假**（需真实合并） |
| 合并命令退出码 / 冲突数 | `0` / `0` |
| `MERGE_HEAD` | `af9a18fa47048893b438ab24c9b338041fa306c1` = beta tip |
| 净差异只含本分支路径 | 6 路径全部为 `app/src/main/**` 与 `docs/**`（dev1 自己的 Android 7 改动） |
| `git diff --diff-filter=D` | 空（无删除） |
| `git diff --check` | 退出码 `0`；无冲突标记 |

### 第 2 层：行级（唯一可信判据）

对 beta 历史上**单亲回退/移除提交**（消息命中 `revert|remove|删除|回退|剔除|移除|撤下|去掉|不再|废弃`）取父，收集删除行，与 dev1 相对 beta tip 的**新增行**求交集，再逐行检查该行是否在 **beta tip 任何文件中都不存在**（只有这一层严格判据可信；表面命中通常是 `}`、`import`、`/**`、markdown 表格分隔等脚手架行）。

| 轮次 | 扫描提交数 | 删除行（含重复） | dev1 新增行 | 交集（唯一行） | **严格复活行数** |
| --- | --- | --- | --- | --- | --- |
| 合并后、修复前 | 147 | 51728 | 234 | 14 | **0** |
| 终检（修复后、工作区状态） | 147 | 51728 | 234 | 14 | **0** |

- 14 个交集行 100% 为通用脚手架行（空行、`} `、`try {`、`/**`、`import androidx.core.view.ViewCompat;`、`| --- | --- |`、```` ``` ```` 等），**每一行都在 beta tip 中存在**（逐行 `git grep -F` 命中）。
- 被这些提交删除的 **206 个文件路径**逐个核对：**0 个**「在合并树中存在、但在 beta tip 中不存在」的路径（即无文件级复活）。
- 重点回退点复核：`be1b02e06b22a4fa2f08c791555536e3e6154c95`（`revert: remove dynamic theme color system`，**在 beta 历史中**，已被上述扫描覆盖）；`5682f2b054b577b0db5c6f7c1143f2eebb51858e`（`剔除 PR #353 主题系统改动`，**不在 beta 历史、也不在 dev1 历史**）。后者删除的 `theme/*` 与 `docs/THEME-COLOR-*` 文件在合并树中存在，但按判据顺序先查 beta tip：**这些文件在 beta tip 中同样存在**（上游后来重新恢复），故属上游状态而非复活；且本轮净差异只含 6 个非 theme 路径，theme 文件在合并树中与 beta tip 逐字节一致。

### 第 3 层：关键移除目标逐个核对（本轮最硬的结构性证据）

对 **beta tip 的 5039 个文件逐个做 blob 比对**：

| 判据 | 结果 |
| --- | --- |
| beta tip 有而合并树没有的文件（潜在丢失） | **0** |
| 合并树新增文件（dev1 自己的新文件） | 1（`docs/android7-support-audit-2026-10-11.md`） |
| 内容不同（= dev1 自己的 Android 7 代码改动） | 5（`LabRunner.java`、`LabTerminalActivity.java`、`MpdSanitizer.java`、`GitCloudDialog.java`、`RemoteTrustDialog.java`） |
| beta 文件在合并树中逐字节一致 | **5034 / 5039** |

即：**合并树 ≡ beta tip + dev1 自己的 6 个路径**，无任何 beta 内容丢失，也没有任何 beta 已删除内容被重新写回。

## 复评记录

### 第 1 轮（合并后，对 dev1 全部已修改代码 = 相对 beta tip 的 6 个路径）

复评对象与方法：以 `git diff --name-status origin/beta <合并树>` 为全集，逐模块复核（生产代码 + 测试有效性 + 任务文档与实现是否一致 + 影响面）。除人工审读外，套用 C58 已验证的三个可机械化扫描：

1. **悬空 javadoc**（`*/` 结尾的下一行以 `/**` 开头）→ 5 个改动 Java 文件命中 **0**。
2. **中文错别字**（对 201+ 行中文文档做 CJK 2-gram / 3-gram 单例扫描）→ 命中项逐个人工核对，**无真实错别字**（候选均为正常词，如「三处一（并）」「未提交任何」「未实现任何」「不再直接引用」等）。
3. **注释与实现矛盾**（把注释里的断言性短语拿去对照实现常量、分支与调用点）→ 命中 **3 项真实问题（F1/F2/F3）**。

影响面复核结论（均为「无泄漏」）：本轮净差异**不触碰** `app/src/**/res/**`、`native`、第三方锁文件（`third_party/*.json`、`gradle/libs.versions.toml`）、播放器链路（`player/**` 除 `MpdSanitizer` 的等价重载替换、`PlaybackActivity` 未触碰）、统计写入与回退闸门。逐项核实：

- `LabRunner.isAlive(Process)` 为包内 `static`，语义与 API 26 的 `Process#isAlive()` 一致（`exitValue()` 返回 → 已终止；抛 `IllegalThreadStateException` → 仍在运行），调用点由 6 处 `Process#isAlive()` 改为该探针；`LabRunner.stop()` 中的 `destroyForcibly`（API 26）**未改动**，见观察项 O1。
- `MpdSanitizer` 的 5 处 `appendReplacement` + 5 处 `appendTail` 由 `StringBuilder` 改为 `StringBuffer`（API 1 重载，仓库其余 6 个同类调用点早已使用 `StringBuffer`，本次是唯一例外被修正）；编译后字节码实测 `StringBuffer` 重载 10 处、`StringBuilder` 重载 0 处（leanback/mobile 双 flavor）。
- 两个弹窗的 `View#setImportantForAutofill`（API 26）改为 `ViewCompat.setImportantForAutofill`；从 Gradle 缓存中的 `androidx.core:core:1.18.0` AAR 反编译实测该方法先判 `SDK_INT >= 26` 再委托 `Api26Impl`，低版本直接返回（无 `NoSuchMethodError`）。
- `java.nio.file.Files.readAllBytes`（`LabRunner:106`，API 26）为**既有代码**（合并基点即存在），且 `desugar_jdk_libs_nio-2.1.5.jar` 内实测含 `java/nio/file/Files.class`、`Path.class`（含 `java/util/regex` 为 0），属既有脱糖覆盖范围，非本轮引入。

#### F1（真实问题）：三处 Android 7 修复没有任何已提交的回归锁

- **位置**：`LabRunner.java` / `LabTerminalActivity.java`、`MpdSanitizer.java`、`GitCloudDialog.java` / `RemoteTrustDialog.java`。
- **问题**：8 个已提交未推送提交里**没有新增任何测试**；上一轮的证据是 `/tmp` 下的 JVM 探针、`javap` 与实机验证（未入库）。而本仓既有约定（`app/src/test` 下 118 个源码守卫/契约用例，如 `AppDatabaseIntegrityRecoverySourceTest`）要求把这类「JVM 上无法复现的 API 级别缺陷」用源码守卫锁住，否则后续任何一次改动都可能静默回归。
- **为什么 JVM 行为用例锁不住**：JVM（Java 21）**始终存在** `Process#isAlive()` 与 `Matcher#appendReplacement(StringBuilder, …)`，所以「把实现改回缺陷形态」在 JVM 上依然全绿——只有源码/签名级断言才能转红。
- **最小修复**：新增 3 个用例类共 6 例（`app/src/test`，双 flavor 共享）：
  - `com.fongmi.android.tv.lab.LabRunnerProcessCompatTest`：① `isAlive` 对「仍在运行 / 已终止 / null」的判定语义；② 源码守卫——`LabRunner.java` 与 `LabTerminalActivity.java` 不得出现 `.isAlive()`，必须使用 `process.exitValue()` 探针且 `LabTerminalActivity` 必须经 `LabRunner.isAlive(` 调用。
  - `com.fongmi.android.tv.player.extractor.MpdSanitizerMatcherMethodContractTest`：① `MpdSanitizer` 的 5 次 `appendReplacement` + 5 次 `appendTail` 目标变量必须声明为 `StringBuffer`（并保留解释注释）；② `StringBuffer` 重载在 JVM 上确实完成改写（`a1b2c` → `a#b#c`）。
  - `com.fongmi.android.tv.compat.Android7ApiCompatContractTest`：① 两个弹窗必须经 `ViewCompat.setImportantForAutofill(` 且该类只出现 1 次 `.setImportantForAutofill(`；② 全仓扫描 `app/src/main/java` 的 33 处 matcher append，任何目标变量若在本文件内声明为 `StringBuilder` 即失败。
- **变异验证（改回缺陷形态必须转红，实测）**：同时施加 4 处变异（`StringBuffer output → StringBuilder output`、`ViewCompat.setImportantForAutofill → edit.setImportantForAutofill`、`LabRunner.isAlive(process) → process.isAlive()`、`isAlive(null) → true`）后运行这 3 个用例类：**6 例中 4 例转红**（`everyMatcherAppendUsesTheApi1StringBufferOverload`、`autofillCallsGoThroughTheApiIsolatedCompatLayer`、`labSourcesProbeThroughExitValueInsteadOfTheApi26Call`、`probeTreatsOnlyATerminatedProcessAsDead`）；`git checkout --` 恢复后 4 个文件 sha256 与变异前一致、6 例全绿。

#### F2（真实问题）：文档把 `MpdSanitizer` 的影响机制写错了

- **位置**：`docs/android7-support-audit-2026-10-11.md` §5 表格 `MpdSanitizer` 行（以及 §6 结论表「静默失效」的表述）。
- **问题**：原文称「该路径被 `Source.fetch()` 的 `try/catch (Exception)` 捕获 → 静默返回未处理 URL」。两处不成立：① `Source.fetch(Result, int)` 的 `catch (Exception e)`（`Source.java:100-104`）只包住 `extractor.fetch(result)`，`MpdSanitizer.sanitize(fetched)` 在 `Source.java:110`，不在该 try 内；② `NoSuchMethodError` 继承 `Error`，`catch (Exception)` 根本捕不到（`MpdSanitizer.sanitize()` 自身的 `catch (Exception e)` 同样捕不到），调用链上的典型兜底（如 `SiteApi.playerContent` 的 `catch (Exception)`）也拦不住。
- **真实影响**：修复前在 API < 34 上，**命中需要清洗的项时**（`appendReplacement` 只在命中非法值时调用，且 `removed[i] == 0` 会提前 `return xml`，故干净 MPD 不受影响）会从 `MpdSanitizer.sanitize()` 一路**上抛 `NoSuchMethodError`**，而不是「静默降级」。
- **最小修复**：订正为上述机制描述（行为零改动，缺陷本身早已修复）。

#### F3（真实问题）：两处数字/口径与证据不符

- **位置**：同文档 §5 开头（`NewApi` 计数）与 §7 第 3 条（用例数）。
- **问题**：① 原文「`NewApi` 52 处（23 处 API 26…）」；按报告自身的逐级错误数（254 → 252 → 246 → 236，共移除 18 条 = `ViewCompat` 2 + `Process#isAlive` 6 + `Matcher` API 34 重载 10）与当前报告实测（236 errors / 33 条 `NewApi`：17 处 API 26、1 处 API 27、13 处 API 29、1 处 API 31、1 处 API 33），修复前应为 **51 处（25 处 API 26）**，而非 52/23。② 原文把「`:app:testLeanbackArm64_v8aDebugUnitTest` 全套 9197 个用例」写成该单任务用例数；实测该任务 XML 为 **655 suites / 4191 tests / 0 failures**，而 9197 = leanback 4191 + mobile 5006（且该轮 mobile flavor **未重跑**，其 `app/build/test-results/testMobileArm64_v8aDebugUnitTest` 目录停留在改动前的 13:57 产物）。
- **最小修复**：订正为实测数字与口径，并新增 §10「C62 复评补充」记录本轮补齐的回归锁、变异验证与双 flavor 重跑数字。

### 第 2 轮（修复后复审）

| 复审项 | 结论 |
| --- | --- |
| F1 是否真的被消除 | **是**。3 个用例类 6 例在双 flavor 均实测通过（JUnit XML：`LabRunnerProcessCompatTest` 2、`MpdSanitizerMatcherMethodContractTest` 2、`Android7ApiCompatContractTest` 2，failures/errors 全 0），且 4 处变异使其中的 4 例转红 → 不是恒真断言 |
| F2 是否真的被消除 | **是**。§5/§6 已按 `Source.java:100-104` 与 `Source.java:110` 的实际结构改写，并标注「C62 复评订正」；未夸大（保留「命中即不可用」的原始缺陷结论） |
| F3 是否真的被消除 | **是**。§5 改为 51 处 / 25 处 API 26 并给出推导；§7 改为 4191（单任务实测）+ 说明 mobile 未重跑；§10 记录双 flavor 重跑结果 |
| 是否改变正常行为 | **否**。本轮对生产代码的改动为 **0**（仅新增测试与文档）；三个修复的公开签名、调用点、行为均与上一轮实机验证过的状态逐字节一致（4 个生产文件 sha256 与变异前一致，且等于合并结果树中的 blob） |
| 是否与仓库既有约定一致 | **是**。新增用例沿用 `app/src/test` 既有的「`Files.readString` + 精确字符串断言」源码守卫风格；未引入依赖、未改资源、未动门禁脚本 |
| 是否泄漏到无关模块 | **否**。新增测试只读取源码文本与 JDK 的 `Process`/`Matcher` 语义；`Android7ApiCompatContractTest` 的全仓扫描是只读遍历，不写入任何产物 |
| 第 1 轮的三项机械扫描是否复现新问题 | 悬空 javadoc 0、错别字 0、注释与实现矛盾 0 |

## 发现与处置

| 编号 | 类别 | 位置 | 处置 |
| --- | --- | --- | --- |
| **F1** | 真实问题（缺回归锁，可被静默改回） | 三个 Android 7 修复 | **已修复**：新增 3 个用例类 6 例（源码守卫 + 语义用例），并用 4 处变异证明可转红 |
| **F2** | 真实问题（文档与实现/调用点矛盾） | `android7-support-audit` §5/§6 的 `MpdSanitizer` 影响机制 | **已修复**：订正为「上抛 `NoSuchMethodError`（`Error` 不被 `catch (Exception)` 捕获）」，行为零改动 |
| **F3** | 真实问题（数字与证据不符） | 同文档 §5 `NewApi` 计数、§7 用例数口径 | **已修复**：51 处 / 25 处 API 26；leanback 单任务 4191、mobile 该轮未重跑 |
| O1 | 观察项（`AGENTS.md` §2：只记录，不在本任务范围） | `LabRunner.stop()` 的 `process.destroyForcibly()`（API 26） | 在 API 24/25 上抛 `NoSuchMethodError`，但被 `catch (Throwable ignored)` 兜住；同方法已先调用 `destroy()`（API 1），随后 `LabProcManager.killTree(pid)` 用 `kill -9` 兜底 → 不崩溃、强杀能力由 `kill -9` 保证。属既有实现，本轮未改动 |
| O2 | 观察项 | `docs/android7-support-audit-2026-10-11.md` §5 行内表述「`ChineseText` 13 处 API 29」 | 实测当前报告 API 29 共 13 条（`ExoFrameSchedulingPerfettoTrace` 8 + `ChineseText` 2 + `PlaybackSystemConditionMonitor` 3），与文档一致；文档该行未逐条列出，无需改动 |
| O3 | 观察项（beta 侧既存，非本轮范围） | 合并带入的 `app/src/main/java/com/fongmi/android/tv/ui/dialog/LoginStatePathDialog.java` 等 PR#437 代码质量提示 | 已用 `git diff --stat origin/beta <合并树>` 证明这些路径与 beta tip 逐字节相同（属 beta 侧既存状态），按 §2 只记录 |
| O4 | 观察项（beta 侧既存） | 合并带入的 `docs/TV-PLAYER-SETTINGS-CONTRAST-20261010.md` 等文档的 markdown 风格提示 | 同上，属 beta 侧既存内容（dev4 的 C58 交付），不在本轮范围 |

## 验证

### 编译与全量 JVM 套件（一次 Gradle 调用跑完）

```bash
bash ./gradlew \
  :app:compileLeanbackArm64_v8aDebugJavaWithJavac \
  :app:compileMobileArm64_v8aDebugJavaWithJavac \
  :app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac \
  :app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac \
  :app:testLeanbackArm64_v8aDebugUnitTest \
  :app:testMobileArm64_v8aDebugUnitTest
```

`BUILD SUCCESSFUL in 6m 45s`（`EXIT=0`，末次全量；首轮因 F1 用例自身断言写错（把「唯一变量数」当成「调用数」）在 `:app:testLeanbackArm64_v8aDebugUnitTest` 报 1 例失败，修正断言后重跑通过）。

| flavor | 用例数 | failures | errors | skipped | 与 C61 基线对比 |
| --- | --- | --- | --- | --- | --- |
| leanback | **4203** | 0 | 0 | 2 | C61 = 4191 → **+12** |
| mobile | **5018** | 0 | 0 | 2 | C61 = 5006 → **+12** |

**增量逐项解释（双 flavor 各 +12，两 flavor 增量相同）**：

| 来源 | 增量 | 说明 |
| --- | --- | --- |
| 本轮 C62 的 F1 回归锁 | +6 | `LabRunnerProcessCompatTest` 2 + `MpdSanitizerMatcherMethodContractTest` 2 + `Android7ApiCompatContractTest` 2（全部在双 flavor 共享的 `app/src/test`） |
| 合并带入的 beta 用例（PR#437） | +6 | `PlayerSettingsContrastTest` 5（新文件）+ `PlayerDisplaySettingSyncTest` +1 |
| 合计 | **+12** | 6 + 6 = 12 |

关键用例实测执行且通过（JUnit XML 直读）：

```text
com.fongmi.android.tv.lab.LabRunnerProcessCompatTest                 tests=2 failures=0 errors=0   （双 flavor）
com.fongmi.android.tv.player.extractor.MpdSanitizerMatcherMethodContractTest tests=2 failures=0 errors=0   （双 flavor）
com.fongmi.android.tv.compat.Android7ApiCompatContractTest           tests=2 failures=0 errors=0   （双 flavor）
com.fongmi.android.tv.ui.dialog.PlayerSettingsContrastTest           tests=5 failures=0 errors=0   （双 flavor，合并带入）
```

### 变异验证（证明 F1 的回归锁不是恒真断言）

```text
变异：StringBuffer output → StringBuilder output（MpdSanitizer）
      ViewCompat.setImportantForAutofill → edit.setImportantForAutofill（GitCloudDialog）
      LabRunner.isAlive(process) → process.isAlive()（LabTerminalActivity）
      isAlive(null) → true（LabRunner）
结果：6 tests completed, 4 failed（BUILD FAILED，39s）
      RED everyMatcherAppendUsesTheApi1StringBufferOverload（no matcher append may target a StringBuilder: output）
      RED autofillCallsGoThroughTheApiIsolatedCompatLayer（GitCloudDialog.java must call ViewCompat.setImportantForAutofill）
      RED labSourcesProbeThroughExitValueInsteadOfTheApi26Call（Process#isAlive() … must not be called in LabTerminalActivity）
      RED probeTreatsOnlyATerminatedProcessAsDead（a missing process must be reported dead）
恢复：git checkout -- <4 个生产文件> → sha256sum -c 全部 OK，工作区与索引一致，MERGE_HEAD 仍为 af9a18fa4
```

### 编译产物级核对（javap，双 flavor 一致）

```text
com.fongmi.android.tv.lab.LabRunner            : java/lang/Process.isAlive = 0, exitValue = 1
com.fongmi.android.tv.lab.LabTerminalActivity  : java/lang/Process.isAlive = 0（改为 LabRunner.isAlive 2 处）
com.fongmi.android.tv.player.extractor.MpdSanitizer : Matcher.appendReplacement/appendTail(StringBuilder) = 0,
                                                      Matcher.appendReplacement/appendTail(StringBuffer) = 10
GitCloudDialog / RemoteTrustDialog             : ViewCompat.setImportantForAutofill = 2（各 1）
androidx.core:core:1.18.0 ViewCompat.setImportantForAutofill 字节码：SDK_INT >= 26 才委托 Api26Impl（低版本直接返回）
```

### UI token 门禁

```text
UI_TOKEN_BASELINE layouts=385 hex_layouts=1 drawables=556 hex_drawables=0 colors=60 hex_colors=0 allowlisted=190
UI_TOKEN_SCOPE    stage=A violations=1 legacy=0
UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28
UI_TOKEN_STATUS   PASS
```

`violations=1` 与 C56/C58/C61 基线完全一致（**零新增**）；`drawables/colors` 计数因合并带入 beta 的 3 个 selector/drawable 与 1 个 color 而增加，属 beta 侧新增资源，非违规。

## 改动清单

| 文件 | 改动 |
| --- | --- |
| `app/src/test/java/com/fongmi/android/tv/lab/LabRunnerProcessCompatTest.java` | 新增（F1）：`isAlive` 语义用例 + Lab 源码守卫 |
| `app/src/test/java/com/fongmi/android/tv/player/extractor/MpdSanitizerMatcherMethodContractTest.java` | 新增（F1）：`MpdSanitizer` matcher append 目标必须是 `StringBuffer` + 重载行为用例 |
| `app/src/test/java/com/fongmi/android/tv/compat/Android7ApiCompatContractTest.java` | 新增（F1）：两个弹窗的 `ViewCompat` 守卫 + 全仓 `StringBuilder` matcher append 扫描 |
| `docs/android7-support-audit-2026-10-11.md` | 订正（F2/F3）：`NewApi` 计数、`MpdSanitizer` 影响机制、用例数口径；新增 §10「C62 复评补充」 |
| `docs/C62-beta-merge-review-dev1-20261012.md` | 本文件 |
| （合并带入，非本任务改动） | PR#437 的 25 个路径（dev4 屏显/设置列表对比度修复 + `PlayerSettingsContrastTest` + 3 份文档）与 PR#435 的嵌套合并内容 |

## 与既有任务的关系

本任务与上游播放器依赖合并计划（`docs/upstream-player-dependency-merge-assessment-2026-08-20.md`）无关，不涉及 Exo/MPV/common 任何阶段，未改动任何上游依赖、锁文件或原生产物。被复评的 Android 7 支持工作是同一用户反馈的独立任务（`docs/android7-support-audit-2026-10-11.md`），本任务只做「合并 + 复评 + 补齐回归锁 + 订正文档」，不重开其结论（三处修复本身已实机验证，行为未改动）。

## 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交（双父） | `11578a6eee41a82748a89ddfceafd65e4a7d5cc7` |
| 第一父（合并前 dev1） | `8daf00907e9066c1f6b2c0319e273f9181e536a6` |
| 第二父（= 当时的 beta tip） | `af9a18fa47048893b438ab24c9b338041fa306c1` |
| recovery tag | `recovery/C62-beta-merge-review-dev1-20261012/20261012111013-11578a6eee41` |
| 推送 | `git push origin dev1` → `565fac1fd..11578a6ee  dev1 -> dev1`；`git rev-parse origin/dev1` == `git rev-parse dev1` == `11578a6eee41a82748a89ddfceafd65e4a7d5cc7` |
| PR | [#438](https://github.com/Silent1566/webhtv/pull/438)（`dev1` → `beta`，**只创建，未合并**） |

## 闭环记录

| 复核项 | 结果 |
| --- | --- |
| `git rev-list --parents -n1 <合并提交>` | `11578a6eee41a82748a89ddfceafd65e4a7d5cc7 8daf00907e9066c1f6b2c0319e273f9181e536a6 af9a18fa47048893b438ab24c9b338041fa306c1` → **两个父**，第二父 == 当时的 beta tip（beta 侧提交未被当成 dev1 自己的提交） |
| `git merge-base --is-ancestor origin/beta HEAD`（收尾） | **真** |
| `MERGE_HEAD` | 已被 `git commit` 清除（`.git/MERGE_HEAD` 不存在） |
| `git diff --diff-filter=D --name-status origin/beta HEAD` | 空 |
| `git diff --name-status origin/beta HEAD` | 10 路径（5 个 Android 7 生产改动 + 3 个回归锁用例类 + 2 份文档） |
| 工作区 | `git status --porcelain` 仅 `.scratch-goal/`（受保护脏路径，未提交） |
| PR 状态 | `state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`、`changedFiles=10`、`baseRefName=beta`、`headRefName=dev1` |
| PR 文件集校验 | `gh api repos/Silent1566/webhtv/pulls/438/files --jq '.[].filename' \| sort` 与 `git diff --name-only origin/beta HEAD \| sort` **逐项一致**（10 项，diff 无输出） |
| 守卫会话 | 主会话 `C62-beta-merge-review-dev1-20261012`（standard）、坐标会话 `C62-beta-merge-review-dev1-20261012-coords`（quick-fix，scope `docs`）均已 finished；`.codex/task-state/current` 不存在（无 active 会话） |
| 收尾复核远端 | `git fetch origin --prune` 后 `git ls-remote origin refs/heads/beta` 仍为 `af9a18fa47048893b438ab24c9b338041fa306c1`（合并基点未被推进） |
| 临时资源清理 | `/tmp/c62/**` 已删除（含 gradle 日志、变异 sha256 清单、PR 正文临时文件） |

- 本次交付只创建 PR，未执行任何合并操作。
- **本任务闭环完成。** 三项用户要求均已满足：① 合并远端 beta 最新代码且远端已移除/回退的内容**零复活**；② 复评全部已修改代码（含 8 个已提交未推送提交）并修复 3 项真实问题、2 轮评审通过；③ 提交（双父合并提交 + recovery tag）、推送 `dev1`、创建 PR（中文描述、只创建未合并）。
