# C67：dev1 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入 `dev1`（**远端已移除/回退的提交不得顺带带回**）；复评 dev1 全部已修改代码（含**已提交未推送**的 WorkManager 直启崩溃修复 `bc756d1d7a`）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev1`、创建 `dev1 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① `dev1` 必须包含合并时刻的 `origin/beta` tip（真实双父合并提交）；② 远端被移除/回退内容**零复活**、beta 增量**零丢失**、dev1 既有改动**零丢失**；③ 双 flavor Java 编译 + 双 flavor 单测 Java 编译通过；④ 合并后 manifest 确认默认 `WorkManagerInitializer` 被移除且 `FollowingWorkInitializer` 就位；⑤ 复评发现的问题已修复并被回归锁锁住（含变异验证）；⑥ 提交 + recovery tag、`dev1` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并完成（0 冲突，`MERGE_HEAD` = beta tip）；零复活/零丢失三层证据通过；复评第 1 轮发现 3 项问题（F1 文档声称的注册竞态复检未实现且存在丢失唤醒、F2 承重行的回归锁是恒真断言、F3 任务文档测试数字与产物不符）已全部修复，第 2 轮复审通过；双 flavor 编译 + 双 flavor 全量 JVM 套件（leanback 4227 / mobile 5039）跑完，8 + 7 个失败逐条分类为 Windows 环境差异（CRLF 检出 / `File.renameTo` 语义），`following` 相关 11 个用例类双 flavor 0 失败；变异验证证明新增断言可转红。**待执行**：提交 + recovery tag、推送 `dev1`、创建 PR。
- **下一动作**：`task_guard.sh finish` 生成双父合并提交与 recovery tag，然后 `git push origin dev1` 并创建 `dev1 -> beta` PR（中文描述，只创建不合并）。

## 时间与环境

- 任务开始时本地时间：2026-10-13 10:00（Asia/Shanghai）。
- 平台：Windows 10（仓库 `F:\Workspace\webhtv`，分支 `dev1`）。
- 工具链：`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`，`GRADLE_USER_HOME=G:\GradleCache`，Android SDK `C:\Users\Maple\AppData\Local\Android\Sdk`（minSdk 24 / targetSdk 28 / compileSdk 37）。
- `bash` 由 Git for Windows 提供（`G:\Git\bin\bash.exe`）；系统 `bash.exe` 是 WSL 启动器且 WSL 服务不可用（`HCS_E_SERVICE_NOT_AVAILABLE`），不能用于守卫脚本。
- **环境缺陷（本次已绕过，不影响结论）**：DSH 会话注入了 `GIT_CONFIG_COUNT=2` 但缺少 `GIT_CONFIG_KEY_0/1`，使任何 `git` 调用直接 `fatal: unable to parse command-line config`。本任务所有 git / 守卫调用前均先 `unset GIT_CONFIG_COUNT`。
- 任务开始时工作区**干净**（`git status --porcelain` 为 0 条），受保护的既有脏路径 **0** 条，`dev1` HEAD = `bc756d1d7a114ce91acc206245c391a39403b3ff`。
- 设备：**本轮未使用真机/模拟器**。理由（风险相符原则，`AGENTS.md` §4）：本轮对生产代码的改动是 1 处局部竞态修复（不改变公开行为与存储位置），且该修复的宿主缺陷本轮无法在本机复现（需要把应用强制为 device-protected storage 的电视固件）；未申请机位。

## 被复评的对象（dev1 相对 beta tip 的全部改动）

`dev1` 领先 `origin/dev1` **2 个提交**，其中第 1 个 `92a0ca0bbe`（`Merge pull request #438 from Silent1566/dev1`）**已是 `origin/beta` 的祖先**，故本轮唯一「已提交未推送」的实质改动是：

- `bc756d1d7a` `fix(tv): guard WorkManager against direct boot storage`（6 路径：manifest + `following/FollowingScheduler.java` + 新增 `FollowingWorkInitializer.java` / `FollowingWorkManager.java` / `FollowingWorkStartupSourceTest.java` + 任务文档 `docs/WORKMANAGER-DIRECT-BOOT-20261012.md`）

`git log --oneline origin/beta..dev1` 输出只有 `bc756d1d7a` 一条（**1 个**），即 dev1 相对 beta 的净差异只有 6 个路径，见下文合并结果净差异。

### 关于「远端已经移除回退的提交」

本仓 dev1 的 reflog 记录了 2026-10-12 的一次 `reset: moving to origin/beta`：

```text
bc756d1d7a dev1@{0}: commit: fix(tv): guard WorkManager against direct boot storage
92a0ca0bbe dev1@{1}: reset: moving to origin/beta
6e667a236f dev1@{2}: commit (amend): fix(ci): 修复守护进程 JVM 供给失败导致流水线在测试前中断
75523754ff dev1@{3}: commit: fix(ci): stop pinning a JetBrains JDK 21 that foojay no longer publishes
6e3037c569 dev1@{4}: reset: moving to 6e3037c569b9307f0ab1a0329d7302a1beb7cc20
```

被那次 reset 丢弃的中间产物是 `75523754ff`（被 `6e667a236f` amend 取代）。逐个核对可达性：

| 提交 | origin/beta | origin/dev1 | dev1 | 说明 |
| --- | --- | --- | --- | --- |
| `6e667a236f`（amend 后的 CI 修复） | 是 | 是 | 是 | 已被各分支正式纳入，**不是**被移除的提交 |
| `75523754ff`（amend 前版本） | **否** | **否** | **否** | 被 amend 取代的弃用产物，任何分支都不含 |
| `6e3037c569`（PR#417 合并） | 是 | 是 | 是 | 正常历史 |

即：远端移除的是 `75523754ff` 这一被 amend 取代的提交，它**不在本任务将要推送的任何历史中**（`git log --oneline origin/beta..dev1` 只有 `bc756d1d7a`）。因此本轮推送不会顺带把它带回去。为防止「内容级复活」，仍按下文三层证据对**合并树**逐行/逐文件核验。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1` |
| 任务开始时 HEAD | `bc756d1d7a114ce91acc206245c391a39403b3ff` |
| `origin/dev1` | `04b32d4e14907bdd72ccf1349d641dc11acf7a9a` |
| `origin/beta` tip（`git ls-remote origin refs/heads/beta`） | `a51e4085f53e76cba64614b32f6cdf62267c5e0a`（Merge pull request #442 from Silent1566/dev3） |
| 合并基点（merge-base） | `92a0ca0bbe50da70768b7245ed6dd17a51bffcea`（Merge pull request #438 from Silent1566/dev1） |
| 合并命令与结果 | `git merge --no-commit --no-ff origin/beta` → **「Automatic merge went well; stopped before committing as requested」**（`EXIT=0`，**0 冲突**，`git diff --name-only --diff-filter=U` 为空） |
| `MERGE_HEAD` | `a51e4085f53e76cba64614b32f6cdf62267c5e0a`（= beta tip，即合并提交第二父） |
| 合并结果树 `git write-tree` | `75af164cc854f9821a9b25de5d989af80f77dada` |
| 合并前 HEAD 树 | `2251c81367ed6eca8de685782a30078b181849fd`（与合并结果树**不同** → 本次合并确实引入 beta 侧内容，必须产生真实合并提交） |
| beta tip 树 | `e7ef4f224a18877cfffc7974b41f8c8da89759e5` |
| 合并暂存路径数 | **28**（= `git diff --name-only 92a0ca0bbe origin/beta`，全部来自 beta 侧） |
| 是否需要新合并提交 | **需要**：合并前 `git merge-base --is-ancestor origin/beta HEAD` 为**假**（`a51e4085f5` 不在 dev1 上），故由 `task_guard.sh finish` 生成真实合并提交，第二父 = beta tip |
| 初始脏路径 | 无（0 条），无需 `--adopt-dirty` |
| 回滚锚点 | `bc756d1d7a114ce91acc206245c391a39403b3ff` |

### 合并增量 ledger（beta 侧 18 个提交，全部纳入）

`git log --oneline 92a0ca0bbe..origin/beta`（新→旧）：

| 完整 commit ID | 标题 | 处置 |
| --- | --- | --- |
| `a51e4085f53e76cba64614b32f6cdf62267c5e0a` | `Merge pull request #442 from Silent1566/dev3` | 纳入（合并第二父） |
| `b3393c6092` | `Merge pull request #441 from Silent1566/dev2` | 纳入 |
| `fc790db709` | `docs(c66): 记录 dev3 合并评审交付坐标（修正提交 434fb60fe8 / recovery tag / 推送 / PR #442）` | 纳入 |
| `434fb60fe8` | `merge: 合并 origin/beta 最新代码（无新提交）并复评修正硬解自动软解回落的开关门控与硬解判据` | 纳入 |
| `4913d6ff23` | `docs(c65): 记录 dev2 交付坐标（合并提交 b6c36bdf1a / recovery tag / 推送 / PR #441）` | 纳入 |
| `b6c36bdf1a` | `merge: 合并 origin/beta 最新代码（PR#440 已把 dev2 并入 beta）并复评 dev2 已提交未推送的 TV 横幅高清化改动` | 纳入 |
| `3c230bac1e` | `merge: 合并 webhtv/webhtv 分支 Silent1566 的 3 个硬解自动软解回落提交并修订 Exo 解码合同` | 纳入 |
| `c0ba017885` | `fix(tv): TV 横幅按 320x180dp 出图，消除启动器放大导致的字标锯齿` | 纳入 |
| `590942503a` | `fix(player): 修正硬解回软解对 Dolby Vision 等改写 mime 渲染器的误判` | 纳入 |
| `bd24f9d53d` | `fix(player): 硬解无硬件解码器时自动切换软解模式` | 纳入 |
| `37bca76737` | `Merge pull request #440 from Silent1566/dev2` | 纳入 |
| `edb1cf8eaa` | `fix(player): 硬解无硬件解码器时不再丢弃视频轨并自动回落软解` | 纳入 |
| `8b5f8e9026` | `merge: 合并 origin/beta 最新代码（PR#439 dev4 对话框取色修复）并复评 dev2 已提交的季集交接改动` | 纳入 |
| `d5ceea9682` | `fix(tv): 详情页已解析季集随详情快照交接给播放页，并补单季剧季号兜底` | 纳入 |
| `b0b7f1d78b` | `Merge pull request #439 from Silent1566/dev4` | 纳入 |
| `139f2ab5d1` | `docs(c63): 记录 dev4 合并评审交付坐标（合并提交 a25ac9dd72d / recovery tag / 推送 / PR #439）` | 纳入 |
| `a25ac9dd72` | `merge: 合并 origin/beta 最新代码（PR#438 dev1 Android 7 兼容修复）并复评 dev4 已提交未推送的 TV 对话框取色修复` | 纳入 |
| `4c5f4dc842` | `fix(theme): TV 对话框面板与前景取自同一张色表` | 纳入 |

### 合并结果净差异（合并后相对 beta tip）

`git diff --name-status origin/beta 75af164cc854f9821a9b25de5d989af80f77dada` = **6 路径**（2 修改 + 4 新增），全部来自 dev1 自己的 WorkManager 修复：

| 类型 | 路径 |
| --- | --- |
| M | `app/src/main/AndroidManifest.xml`、`app/src/main/java/com/fongmi/android/tv/following/FollowingScheduler.java` |
| A | `app/src/main/java/com/fongmi/android/tv/following/FollowingWorkInitializer.java`、`app/src/main/java/com/fongmi/android/tv/following/FollowingWorkManager.java`、`app/src/test/java/com/fongmi/android/tv/following/FollowingWorkStartupSourceTest.java`、`docs/WORKMANAGER-DIRECT-BOOT-20261012.md` |

`git diff --diff-filter=D --name-status origin/beta <合并树>` 为**空**；`git diff --check` 退出码 `0`。

## 零复活三层证据（严格判据）

### 第 1 层：结构性

| 判据 | 结果 |
| --- | --- |
| `git merge-base --is-ancestor origin/beta HEAD`（合并前） | **假**（需真实合并） |
| 合并命令退出码 / 冲突数 | `0` / `0` |
| `MERGE_HEAD` | `a51e4085f53e76cba64614b32f6cdf62267c5e0a` = beta tip |
| 净差异只含本分支路径 | 6 路径全部为 `app/src/main/**`、`app/src/test/**`、`docs/**`（dev1 自己的 WorkManager 改动） |
| `git diff --diff-filter=D` | 空（无删除） |
| `git diff --check` | 退出码 `0`；无冲突标记 |
| 合并暂存路径 ⊆ 声明的 scope | 28/28 命中（守卫 `finish` 的 scope 校验覆盖） |

### 第 2 层：行级（唯一可信判据）

对 beta 历史上**单亲回退/移除提交**（消息命中 `revert|remove|删除|回退|剔除|移除|撤下|去掉|不再|废弃`）取删除行，与 dev1 相对 beta tip 的**新增行**求交集，再逐行检查该行是否在 **beta tip 任何文件中都不存在**（只有这一层严格判据可信；表面命中通常是 `}`、`import`、`/**`、markdown 表格分隔等脚手架行）。

| 轮次 | 扫描提交数 | 删除行集合 | dev1 新增唯一行 | 交集（唯一行） | **严格复活行数** |
| --- | --- | --- | --- | --- | --- |
| 合并后、修复前（合并树 `75af164cc8`） | 416 | 39950 | 235 | 41 | **0** |

- 41 个交集行**每一行都在 beta tip 中存在**（逐行 `git grep -F` 命中）→ 无任何「beta 已删除的行被写回」。
- 本层扫描的候选提交数（416）比 C62（147）更多，判据相同且更严；`75523754ff` 不在任何分支历史中，故其内容不可能经合并回流。

### 第 3 层：全树逐文件核对（本轮最硬的结构性证据）

对 **beta tip 的 5052 个文件**逐个做 blob 比对：

| 判据 | 结果 |
| --- | --- |
| beta tip 有而合并树没有的文件（潜在丢失） | **0** |
| 合并树新增文件（dev1 自己的新文件） | 4（`FollowingWorkManager.java`、`FollowingWorkInitializer.java`、`FollowingWorkStartupSourceTest.java`、`docs/WORKMANAGER-DIRECT-BOOT-20261012.md`） |
| 内容不同（= dev1 自己的改动） | 2（`app/src/main/AndroidManifest.xml`、`following/FollowingScheduler.java`） |
| beta 文件在合并树中逐字节一致 | **5052 / 5052** |

即：**合并树 ≡ beta tip + dev1 自己的 6 个路径**，无任何 beta 内容丢失，也没有任何 beta 已删除内容被重新写回。

## 复评记录

### 第 1 轮（合并后，对 dev1 全部已修改代码 = 相对 beta tip 的 6 个路径）

复评对象与方法：以 `git diff --name-status origin/beta 75af164cc8` 为全集，逐模块复核（生产代码 + 测试有效性 + 任务文档与实现是否一致 + 影响面）。除人工审读外，沿用 C62 已验证的三个可机械化扫描（悬空 javadoc、中文错别字、注释/文档与实现矛盾），并对本修复的**承重假设逐条对真实库/框架字节码取证**：

| 假设 | 取证 | 结论 |
| --- | --- | --- |
| 崩溃文案可被 `contains("direct boot mode")` 命中 | `work-runtime:2.11.2` AAR → `javap -c androidx.work.impl.WorkManagerImpl`：构造器 `getApplicationContext()` 后调 `WorkManagerImpl$Api24Impl.isDeviceProtectedStorage`，真则 `IllegalStateException("Cannot initialize WorkManager in direct boot mode")` | **成立** |
| `WorkManager.isInitialized()` / `initialize(Context,Configuration)` 存在 | 同 AAR `javap androidx.work.WorkManager` | **成立** |
| `0x10` 就是 credential-protected storage 标志 | Android SDK `sources/android-37.0/android/content/Context.java:8086`：`CONTEXT_CREDENTIAL_PROTECTED_STORAGE = 0x00000010`；`ContextImpl.isDeviceProtectedStorage()` = `(mFlags & CONTEXT_DEVICE_PROTECTED_STORAGE) != 0` | **成立** |
| `ContextWrapper` 覆盖 `getApplicationContext()` 是**承重行**（去掉即复发） | `ContextImpl.getApplicationContext()` 返回 `mPackageInfo.getApplication()`，即应用自己的 `Application` 实例（该固件下基上下文为 DE）→ WorkManager 再归一化后仍看到 DE → 抛异常 | **成立**（因此该行必须有回归锁，见 F2） |
| minSdk 24 下 `isDeviceProtectedStorage()`（API 24）可无守卫调用 | `gradle/libs.versions.toml`：`minSdk = "24"` | **成立**（非缺陷） |
| 移除默认 initializer 不丢应用自定义配置 | 全仓 `Configuration.Provider` / `workManagerConfiguration` / `WorkerFactory` 命中 **0**；`WorkManagerInitializer` 在无 Provider 时同样用 `Configuration.Builder().build()` | **成立**（非缺陷） |
| 无其它绕过门控的 `WorkManager` 直接调用 | 全 `app/src/main` 只有 `FollowingWorkManager` 3 处（`isInitialized`/`initialize`/`getInstance(workContext)`）+ `FollowingWorkInitializer` 1 处；`FollowingScheduler` 的 5 个调度/取消入口全部走 `withManager` | **成立** |
| 移除默认 initializer 不引入多进程启动异常 | `javap -c WorkManagerImpl.tryInitializeMultiProcessSupport`：反射加载 `androidx.work.multiprocess.RemoteWorkManagerClient`，整个 try 被 `catch (Throwable)` 吞掉并 `Logger.debug("Unable to initialize multi-process support")` | **成立**（非缺陷） |
| 调度语义未变 | 逐行比对 `FollowingScheduler` 前后：任务名、6h/1h 周期、`NetworkType.CONNECTED`、`BackoffPolicy.EXPONENTIAL/15min`、`TAG`、`ExistingPeriodicWorkPolicy.UPDATE`、`ExistingWorkPolicy.KEEP`、`ExistingWorkPolicy.REPLACE`、`MIN_ONE_SHOT_DELAY`、`identityKey` 空值保护均**逐一对应** | **成立**（非缺陷） |

影响面复核结论（均为「无泄漏」）：本轮净差异**不触碰** `app/src/**/res/**`、`native`、第三方锁文件（`third_party/*.json`、`gradle/libs.versions.toml`）、播放器链路（`player/**` 未被本修复触碰）、主题与 UI。合并带入的 beta 改动集中在 `player/**`、`theme/**`、`ui/**`、`utils/**`、`scripts/**`、`app/src/leanback/res/mipmap-*/ic_banner.png` 与 `docs/**`，与 `following/**` **零路径重叠**，不存在语义交互。

#### F1（真实问题）：任务文档声称的「注册后重新检查状态关闭注册竞态」在实现中不存在，且存在真实的丢失唤醒

- **位置**：`app/src/main/java/com/fongmi/android/tv/following/FollowingWorkManager.java:54-61`；对应声明 `docs/WORKMANAGER-DIRECT-BOOT-20261012.md:26`「真正未解锁时注册 USER_UNLOCKED，**重新检查状态关闭注册竞态**，解锁后启动并按顺序提交延迟的调度/取消操作」。
- **问题**：原实现为

  ```java
  if (!isUserUnlocked(app)) {
      registerUnlockReceiver(app);
      return;                       // ← 注册后没有复检
  }
  ```

  `ACTION_USER_UNLOCKED` 是**一次性广播、系统不会重放**。若解锁发生在 `isUserUnlocked()` 返回 false 与 `registerUnlockReceiver()` 完成之间，该进程既收不到广播、也不会再有第二次机会：`pending` 队列中已排队的追更调度/取消操作**永远不会执行**，直到进程重启。文档把「复检」写成已完成的事实，属于**文档与实现矛盾**（与 C62 的 F2 同类），同时是一个真实的窄窗口正确性缺陷。
- **最小修复**：注册后复检一次，仍锁定才返回：

  ```java
  if (!isUserUnlocked(app)) {
      registerUnlockReceiver(app);
      if (!isUserUnlocked(app)) return;
  }
  ```

  复检为真时直接落入原有初始化路径（该路径会先 `unregisterUnlockReceiver()`，不会留下悬挂接收器）。**行为边界**：只改变「解锁恰好落在注册窗口内」这一种时序，其余路径（正常 CE 设备、持续锁定、初始化成功）逐字节不变。

#### F2（真实问题）：本次修复最承重的一行（`getApplicationContext()` 覆盖）没有任何能转红的回归锁

- **位置**：`app/src/test/java/com/fongmi/android/tv/following/FollowingWorkStartupSourceTest.java:45-52`（修复前）。
- **问题**：原用例 5 条断言全是 `source.contains("...")` 的存在性检查。其中 `assertTrue(source.contains("getApplicationContext()"))` **恒真**——`start()` 里本来就有 `context.getApplicationContext()`，即使删掉 `ContextWrapper` 中的覆盖（即让 WorkManager 重新归一化回 DE `Application`、原样复发用户上报的 9/9 冷启动崩溃），该断言依然通过。同理 `contains("isUserUnlocked")` 也锁不住「注册后复检」。这与本仓既有约定（`app/src/test` 下大量源码守卫/契约用例）不一致：JVM 上无法复现该缺陷，只有源码级断言才能转红，而现有断言恰恰无法转红。
- **最小修复**：把 5 条存在性断言换成 6 条**结构性**正则断言，分别锁定：① credential 标志传入 `createPackageContext`；② 匿名 `ContextWrapper` 覆盖 `getApplicationContext()` 且 `return this`；③ 普通（CE）设备保持原存储路径的早返回；④ 只把 `workContext` 交给 `WorkManager.initialize`；⑤ 未解锁时推迟；⑥ **注册后复检**（F1 的锁）。新增私有 `matches(source, regex)`（`Pattern.DOTALL`）。
- **变异验证**：见「验证」一节（去掉覆盖 / 去掉复检必须转红）。

#### F3（真实问题）：任务文档的测试数字与产物不符

- **位置**：`docs/WORKMANAGER-DIRECT-BOOT-20261012.md:38` 与 `:40`。
- **问题**：用例类现有 **3** 个 `@Test`（`onlyWorkManagerDefaultInitializerIsReplaced`、`workManagerUsesCredentialStorageWithoutChangingAppStorage`、`allFollowingSchedulingAndCancellationUsesTheStorageGate`），提交信息也写 `FollowingWorkStartupSourceTest 3/3 passed`；但文档 `:40` 的 GREEN 记录写「XML 报告 `tests=2`」，`:38` 的 RED 记录写「2 项均按预期失败」。同一交付内三处口径互斥。
- **最小修复**：按本轮实测重写 RED/GREEN 两行并标注口径（RED 阶段当时只有 2 个用例；GREEN 以本轮 `tests=3` 为准），行为零改动。

#### 观察项（按 `AGENTS.md` §2 只记录，不在本任务范围）

| 编号 | 位置 | 说明 |
| --- | --- | --- |
| O1 | `FollowingWorkManager.java:26,127` | 使用 `@hide` 常量 `0x10` 与 `createPackageContext`，而 minSdk 24 已可直接用公开 API `Context.createCredentialProtectedStorageContext()`（`ContextImpl.java:3281` 证实其语义等价且不需隐藏常量）。当前实现已通过验证，改造属重构，本轮不改。 |
| O2 | `FollowingWorkManager.java:97-98` | 解锁接收器用 `ContextCompat.RECEIVER_EXPORTED`。`ACTION_USER_UNLOCKED` 是受保护系统广播，其他应用无法伪造，故不构成安全缺口；改用 `RECEIVER_NOT_EXPORTED` 属加固，不在本次修复范围。 |
| O3 | `FollowingWorkManager.java:72-75` | `catch` 分支在「用户已解锁但 `WorkManager.initialize` 仍报 direct-boot」时注册一个永远不会再触发的接收器，并让 `scheduleRetry` 以 1s 周期持续重试。经第 1 轮取证，传入 CE wrapper 后该异常**不可达**（`ContextImpl.getApplicationContext()` 已被覆盖、`isDeviceProtectedStorage()` 为 false），故仅记录。 |
| O4 | 合并带入（beta 侧既存） | PR#439–#442 的 `player/**`、`theme/**`、`ui/**`、`utils/**`、`scripts/gen_app_icon.py`、`ic_banner.png`、`docs/C63`–`docs/C66` 等 28 路径与 beta tip **逐字节一致**（第 3 层证据），属 beta 侧既存状态，已由 dev2/dev3/dev4 各自任务复评，不在本轮范围。 |

### 第 2 轮（修复后复审）

| 复审项 | 结论 |
| --- | --- |
| F1 是否真的被消除 | **是**。`start()` 现为「注册接收器 → 复检 → 仍锁定才返回」，解锁落在注册窗口内时立即落入原初始化路径（该路径先 `unregisterUnlockReceiver()`，不留下悬挂接收器；晚到的广播再次进入时为幂等空操作）。变异验证：删掉复检行后用例转红，断言消息为 `The unlock state must be re-checked after registering, because the broadcast is not replayed` |
| F2 是否真的被消除 | **是**。5 条恒真/弱断言换成 6 条结构性正则断言（`Pattern.DOTALL`）。两次变异分别使其转红：把承重覆盖改成 `super.getApplicationContext()` → 红（`WorkManager re-normalizes the Context, so the adapter must keep the CE wrapper as the application Context`）；删掉复检行 → 红。即修复前「删掉承重行仍然全绿」的漏洞已关闭 |
| F3 是否真的被消除 | **是**。任务文档的 RED/GREEN 两行已按实测重写并标注口径；`docs/WORKMANAGER-DIRECT-BOOT-20261012.md` 的 `tests=2` 与提交信息 `3/3` 不再互斥，实测 XML 为 `tests=3 failures=0 errors=0 skipped=0` |
| 是否改变正常行为 | **否**。生产代码只新增「注册后复检」这一条判断，仅影响「解锁恰好落在注册窗口内」的时序；正常 CE 设备、持续锁定、初始化成功三条路径逐字节不变。三个改动文件在变异回退后 sha256 与变异前基线一致（`FollowingWorkManager.java` = `D5D5D80673ACEF5172DBC5F2CF8CACA777386A2754C6B06FBA4E01CF7433733A`，`FollowingWorkStartupSourceTest.java` = `344256F7ABD52EF7E5CD1B1CDF4D97CAAD3831088FE6AC1F709836B7F0C88009`） |
| 是否与仓库既有约定一致 | **是**。新断言沿用 `app/src/test` 既有的「`Files.readString` + 精确字符串/正则断言」源码守卫风格（与 C62 新增的三个契约用例同类）；未引入依赖、未改资源、未动门禁脚本 |
| 是否泄漏到无关模块 | **否**。净差异 6 路径全部位于 `following/**`、`AndroidManifest.xml`、`docs/**`；合并带入的 beta 改动与 `following/**` 零路径重叠 |
| 第 1 轮的机械扫描是否复现新问题 | 悬空 javadoc **0**（5 个改动 Java 文件）；行尾空白/制表符 **0**；`git diff --check` 退出码 `0`；注释与实现矛盾 **0**（F1 的文档-实现矛盾已由实现修复消解，F3 的数字矛盾已订正） |

## 验证

### 环境分类（先说结论）

本轮所有失败都可归入**环境类**，没有一条可归因于本任务改动或合并带入的内容：判据是「涉及的测试文件与生产代码在本任务前后逐字节一致」——第 3 层证据已证明合并树 ≡ beta tip + dev1 自己的 6 个路径，而下列失败所涉的 7 个用例类、2 个生产类都不在这 6 个路径内（`git diff --name-only origin/beta HEAD -- <file>` 逐个为空）。

### 构建尝试 1（环境失败，已绕过）

```text
gradlew.bat --offline :app:compileLeanbackArm64_v8aDebugJavaWithJavac ... （含两个 process*UnitTestManifest）
→ BUILD FAILED in 8m43s
FAILURE: Execution failed for task ':app:mergeMobileArm64_v8aDebugResources'
  Failed to store cache entry ... Timeout waiting to lock Build cache (G:\GradleCache\caches\build-cache-1)
  Owner PID: 39192 / Our PID: 47464
```

- 分类：**环境**。两个失败任务都停在「写构建缓存」这一步，`G:\GradleCache\caches\build-cache-1` 被另一个 Gradle daemon（PID 39192，10:03 启动）持有。
- 处置：**没有**杀掉别人的 daemon，改用 `--no-build-cache` 重跑（离线依赖缓存仍可用）。

### 构建尝试 2/3（编译 + 双 flavor 全量 JVM 套件）

```text
gradlew.bat --offline --no-build-cache --console=plain `
  :app:compileLeanbackArm64_v8aDebugJavaWithJavac `
  :app:compileMobileArm64_v8aDebugJavaWithJavac `
  :app:compileLeanbackArm64_v8aDebugUnitTestJavaWithJavac `
  :app:compileMobileArm64_v8aDebugUnitTestJavaWithJavac `
  :app:processLeanbackArm64_v8aDebugUnitTestManifest `
  :app:processMobileArm64_v8aDebugUnitTestManifest `
  :app:testLeanbackArm64_v8aDebugUnitTest `
  :app:testMobileArm64_v8aDebugUnitTest
→ 15m49s，在 :app:testLeanbackArm64_v8aDebugUnitTest 停下（8 例失败），mobile 套件尚未执行

gradlew.bat --offline --no-build-cache --console=plain --continue `
  :app:compileMobileArm64_v8aDebugUnitTestJavaWithJavac `
  :app:testMobileArm64_v8aDebugUnitTest
→ 3m16s，mobile 套件 7 例失败
```

四个编译任务全部通过（`compileLeanback/Mobile Arm64_v8aDebugJavaWithJavac`、`compileLeanback/Mobile Arm64_v8aDebugUnitTestJavaWithJavac`），两个 `process*UnitTestManifest` 通过。

| flavor | suites | tests | failures | errors | skipped | C62 基线（Linux） | 增量 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| leanback | 662 | **4227** | 8 | 0 | 3 | 4203 | +24 |
| mobile | 735 | **5039** | 7 | 0 | 3 | 5018 | +21 |

（数字直读 JUnit XML 汇总，与 Gradle 的 `4227 tests completed, 8 failed, 3 skipped` / `5039 tests completed, 7 failed, 3 skipped` 一致。）

**增量归因（可归因部分）**：

| 来源 | leanback | mobile | 说明 |
| --- | --- | --- | --- |
| 合并带入 `ExoVideoHardwareDecodeTest`（新文件，`app/src/test` 双 flavor 共享） | +10 | +10 | beta 侧硬解自动软解回落契约 |
| 合并带入 `TmdbDetailCacheTest` 新增用例（双 flavor 共享） | +8 | +8 | 季集随详情快照交接契约 |
| 合并带入 `TvDialogPaletteModeTest`（新文件，仅 `app/src/testLeanback`） | +3 | 0 | beta 侧 TV 对话框取色 |
| **本任务自身改动** | **0** | **0** | `FollowingWorkStartupSourceTest` 在改动前后都是 3 个 `@Test`（只替换断言，不增删用例） |
| 已归因小计 | +21 | +18 | |
| 未归因残差 | +3 | +3 | 见下 |

- **残差 +3/ flavor 未归因**：本任务改动的 6 个路径不新增任何用例（`@Test` 计数 3→3 实测），且残差在两个 flavor 上相同，指向共享测试发现层面而非本任务；C62 基线记录于 Linux，跨平台小差异无法用本仓证据消除，故如实记录而不臆测归因。

### 本次修复的直接证据（决定性判据）

```text
com.fongmi.android.tv.following.FollowingWorkStartupSourceTest  tests=3 failures=0 errors=0 skipped=0   （leanback + mobile 双 flavor）
```

`following` 相关 11 个用例类（`com.fongmi.android.tv.following` 9 个 + `ui.activity.FollowingUiSourceTest` 15 例 + `bean.BackupFollowingTest` 2 例）在双 flavor 均 **0 失败**：

```text
FollowingAlistImportTest 4 / FollowingIdentityTest 2 / FollowingMergePolicyTest 7 / FollowingMetadataClientTest 3
FollowingPlaybackBridgeTest 3 / FollowingSchedulePolicyTest 6 / FollowingSourceProbeTest 6 / FollowingUpdatePolicyTest 3
FollowingWorkStartupSourceTest 3 / FollowingUiSourceTest 15 / BackupFollowingTest 2
```

### 合并后 manifest 核对（主 manifest，非单测 manifest）

`app/build/intermediates/merged_manifest/leanbackArm64_v8aDebug/processLeanbackArm64_v8aDebugMainManifest/AndroidManifest.xml`：

| 判据 | 结果 |
| --- | --- |
| `androidx.work.WorkManagerInitializer` 出现次数 | **0**（默认初始化器已从合并 manifest 中移除） |
| `com.fongmi.android.tv.following.FollowingWorkInitializer` 出现次数 | **1** |
| 其余 Startup 初始化项是否保留 | 保留：`com.fongmi.android.tv.Startup`、`androidx.emoji2.text.EmojiCompatInitializer`、`androidx.lifecycle.ProcessLifecycleInitializer`、`okhttp3.internal.platform.PlatformInitializer`、`androidx.profileinstaller.ProfileInstallerInitializer` |

> 附带说明：`:app:processMobileArm64_v8aDebugUnitTestManifest` 输出过一条 `meta-data#androidx.work.WorkManagerInitializer was tagged ... to remove other declarations but no other declaration present` 警告。这是**单元测试** manifest 的合并结果（该变体不合并 work-runtime 的库 manifest），与上面的**主** manifest 结论不冲突；主 manifest 的核对结果如上表。

### 变异验证（证明新增断言不是恒真）

| 变异 | 施加位置 | 结果 |
| --- | --- | --- |
| 把 `ContextWrapper` 的覆盖体改成 `return super.getApplicationContext();`（保留方法、去掉保护，即让缺陷原样复发） | `FollowingWorkManager.java` | **RED**：`3 tests completed, 1 failed`，断言消息 `WorkManager re-normalizes the Context, so the adapter must keep the CE wrapper as the application Context`（`FollowingWorkStartupSourceTest.java:50`） |
| 删掉注册后的复检行 `if (!isUserUnlocked(app)) return;` | `FollowingWorkManager.java` | **RED**：`3 tests completed, 1 failed`，断言消息 `The unlock state must be re-checked after registering, because the broadcast is not replayed`（`FollowingWorkStartupSourceTest.java:58`） |
| 回退 | 两次变异后逐条反向编辑 | `FollowingWorkManager.java` sha256 = `D5D5D80673ACEF5172DBC5F2CF8CACA777386A2754C6B06FBA4E01CF7433733A`、`FollowingWorkStartupSourceTest.java` sha256 = `344256F7ABD52EF7E5CD1B1CDF4D97CAAD3831088FE6AC1F709836B7F0C88009`，与变异前基线**逐字节一致**；双 flavor 全量套件已在该基线上测得 `tests=3 failures=0` |

### 8 + 7 个失败的逐条分类（全部环境类）

| 用例类 | flavor | 失败例数 | 机制（已取证） | 与本任务的关系 |
| --- | --- | --- | --- | --- |
| `WebThemeTokenSourceTest` | 双 | 1 | 断言 `home.contains(":root {\n      --eclipse-color-1: …")`；`core.autocrlf=true` 使工作副本为 CRLF，字面量 `\n` 不再命中（`git ls-files --eol` 实测该资产 `i/lf w/crlf`） | 文件与 beta tip 逐字节一致；Linux 上通过 |
| `TmdbSourceOnlyInteractionTest` | 双 | 1 | 同上，断言含 `"if (runtimeSourceOnly) {\n            renderTmdbRatingChips(…)"` | 同上 |
| `ReaderPlaybackRoutingSourceTest` | 双 | 2 | 同上，断言含 `"comicShowPage(i){\n    comicCur = i;"`、`"function nowMs(){\n    return …"` | 同上 |
| `PlayerPlaybackRegressionSourceTest` | 仅 mobile | 1 | 同上，断言含 `"protected boolean shouldAutoPlay() {\n        return PlayerSetting.isAutoPlay();\n    }"` | 同上（位于 `app/src/testMobile`） |
| `NativeEnhancedPlaybackStyleFocusTest` | 仅 leanback | 1 | 同上，断言含 `"applyTmdbRowFocusChain();\n        updateFocus();"` | 同上（位于 `app/src/testLeanback`） |
| `SearchResultDownFocusTest` | 仅 leanback | 1 | 同上，断言含 `"if (bottom) mScroller.checkMore();\n            return bottom;"` | 同上（位于 `app/src/testLeanback`） |
| `MpvFontConfigTest` | 双 | 1 | `java.io.IOException: Unable to commit fontconfig configuration`（`MpvFontConfig.java:74`）：`temporary.renameTo(file)` 在目标文件已存在时于 Windows 返回 false（`File.renameTo` 的 Javadoc 明示「might not succeed if a file with the destination abstract pathname already exists」）；用例第 51 行先写入了旧内容 | 生产类与用例均与 beta tip 逐字节一致；Linux 上通过 |
| `MpvHlsCacheCoordinatorTest` | 双 | 1 | 断言 `new File(directory, "commit.bin.meta").isFile()`：`writeMetaLocked` 在 `try (OutputStream …)` **尚未关闭**时执行 `temp.renameTo(meta)`，Windows 下重命名被打开的文件失败，`.meta` 未生成 | 同上 |

合计：leanback 8 例（含 `ReaderPlaybackRoutingSourceTest` 2 例）、mobile 7 例（mobile 不含两个 `testLeanback` 类、多一个 `testMobile` 类）。**7 个用例类全部与 beta tip 逐字节一致**，且 C62 在 Linux 上对同一批内容记录过 `leanback 4203/0、mobile 5018/0`。

### 未验证边界（如实声明）

- **没有真实电视盒子/模拟器实测**。本修复针对的是「固件把应用默认存储强制为 device-protected storage」的现场崩溃，本机无法复现该前提，因此「默认 DE 且已解锁时 WorkManager 任务库落在 CE 路径」这一条仍属**验收标准而非已验证事实**（与上一轮文档 §验收与边界 的声明一致）。
- 未做 Robolectric 行为级测试（上一轮尝试因 Gradle 任务超时中止）；本轮改用「真实库/框架字节码取证 + 源码契约锁 + 变异验证」，未重复同一超时任务。
- 未重跑 UI token 门禁与 `git diff --check` 之外的门禁脚本；本轮净差异不含 `res/**`（`app/src/leanback/res/mipmap-*/ic_banner.png` 属 beta 侧带入，与 beta tip 逐字节一致）。

## 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交（双父） | `9d58d40588af6d5466f7b39f144db9af6c58e723` |
| 第一父（合并前 dev1） | `bc756d1d7a114ce91acc206245c391a39403b3ff` |
| 第二父（= 合并时刻 beta tip） | `a51e4085f53e76cba64614b32f6cdf62267c5e0a` |
| recovery tag | `recovery/C67-beta-merge-review-dev1-20261013/20261013033852-9d58d40588af` |
| 推送 | `git push origin dev1` → `04b32d4e14..9d58d40588  dev1 -> dev1`；`git rev-parse origin/dev1` == `git rev-parse dev1` == `9d58d40588af6d5466f7b39f144db9af6c58e723` |
| PR | [#443](https://github.com/Silent1566/webhtv/pull/443)（`dev1` → `beta`，**只创建，未合并**） |

## 闭环记录

| 复核项 | 结果 |
| --- | --- |
| `git rev-list --parents -n1 9d58d40588` | `9d58d40588af6d5466f7b39f144db9af6c58e723 bc756d1d7a114ce91acc206245c391a39403b3ff a51e4085f53e76cba64614b32f6cdf62267c5e0a` → **两个父**，第二父 == 合并时刻 beta tip（beta 侧提交没有被当成 dev1 自己的提交） |
| `git merge-base --is-ancestor origin/beta HEAD`（收尾） | **真** |
| `MERGE_HEAD` | 已被 `git commit` 清除（`.git/MERGE_HEAD` 不存在） |
| `git diff --diff-filter=D --name-status origin/beta HEAD` | 空 |
| `git diff --name-status origin/beta HEAD` | 7 路径（2 修改 + 5 新增：manifest、`FollowingScheduler.java`、`FollowingWorkInitializer.java`、`FollowingWorkManager.java`、`FollowingWorkStartupSourceTest.java`、`docs/WORKMANAGER-DIRECT-BOOT-20261012.md`、本文件） |
| 工作区 | `git status --porcelain` 为空 |
| PR 状态 | `state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`、`changedFiles=7`、`baseRefName=beta`、`headRefName=dev1` |
| PR 文件集校验 | `gh api repos/Silent1566/webhtv/pulls/443/files --jq '.[].filename' \| sort` 与 `git diff --name-only origin/beta HEAD \| sort` **逐项一致**（7 项，`Compare-Object` 无输出） |
| 守卫会话 | 主会话 `C67-beta-merge-review-dev1-20261013`（standard）与坐标会话 `C67-beta-merge-review-dev1-20261013-coords`（quick-fix，scope `docs`） |
| 临时资源 | `%TEMP%\c67work\**`（gradle 日志、AAR 解包、变异日志、PR 正文临时文件）按任务范围清理 |

- 本次交付只创建 PR，未执行任何合并操作。
- **本任务闭环完成。** 三项用户要求均已满足：① 合并远端 beta 最新代码且远端已移除/回退的内容**零复活**；② 复评全部已修改代码（含已提交未推送的 WorkManager 直启修复）并修复 3 项真实问题、2 轮评审通过；③ 提交（双父合并提交 + recovery tag）、推送 `dev1`、创建 PR（中文描述、只创建未合并）。
