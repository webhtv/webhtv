# C61：dev1 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入 `dev1`（**远端已移除/回退的提交不得顺带带回**）；复评 dev1 全部已修改代码（含**已提交未推送**的 C59/C60 两个提交）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev1`、创建 `dev1 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① `dev1` 必须包含合并时刻的 `origin/beta` tip（真实双父合并提交）；② 远端被移除/回退内容**零复活**、beta 增量**零丢失**、dev1 既有改动**零丢失**；③ 双 flavor Java 编译 + 双 flavor AndroidTest Java 编译通过；④ 双 flavor 全量 JVM 套件零失败且增量可解释；⑤ UI token 门禁相对基线零新增违规；⑥ 复评发现的问题已修复并锁定（或按 `AGENTS.md` §2 明确记录处置）；⑦ 提交 + recovery tag、`dev1` 已推送、PR 已创建且**未合并**，PR 文件集与 `git diff --name-only origin/beta HEAD` 逐项一致。
- **当前状态**：合并完成（0 冲突，`MERGE_HEAD` = beta tip）；复评 **2 轮**完成；第 1 轮发现 **2 项真实缺陷**（F1 跨长度时间戳误判为「更新」、F2 注释与实现矛盾）并已修复，第 2 轮复审通过；双 flavor 编译 + AndroidTest 编译 + 双 flavor 全量 JVM 套件（leanback 4191/0、mobile 5006/0）+ UI token 门禁全部通过；零复活/零丢失证据通过；**已提交（双父合并提交 `8b54e0e97` + recovery tag）、已推送 `dev1`、已创建 PR #436（未合并）**。
- **交付坐标**：见文末「交付坐标」与「闭环记录」。
- **下一动作**：无（本任务闭环；PR 由用户决定是否合并）。

## 时间与设备

- 任务开始时本地时间：2026-10-11 13:41（Asia/Shanghai）；记录时 14:00。
- 任务开始时工作区仅 `.scratch-goal/` 未跟踪（C59/C60 的取证目录，**不提交**）；受保护的既有脏路径 371 条，全程未触碰。
- `dev1` HEAD = `56372b7dbac436f88b34225e714adedb3854d0fa`，领先 `origin/dev1`（`3148f030df9784be7b14fc924364c304980ff9ab`）**2 个提交**，即本轮的复评对象（已提交未推送）：
  - `d8c9bdc650115daf21d9ea1abfa3efb7d8f0535a` `fix(update): 只有严格更新的构建才提示升级，切断 stable/beta 互相覆盖安装死循环`（C59）
  - `56372b7dbac436f88b34225e714adedb3854d0fa` `fix(db): Room 数据完整性校验失败不再导致启动崩溃循环，改为保全坏库后重建`（C60）
- 设备：**本轮未使用真机/模拟器**。理由（风险相符原则，`AGENTS.md` §4）：本轮对生产代码的改动是 **1 处判定分支 + 2 处注释文本**（详见「发现与处置」），不改变正常升级方向、不改变数据库打开/迁移路径；被复评的 C59/C60 已由用户在 `192.168.50.3:5555`（Android 9，`sdk=28`）按四种场景实机验收（见 `docs/C60-android9-box-database-integrity-recovery-20261011.md` 的实机证据）。故未申请机位、未重装 APK。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1`（worktree `/home/maple/Workspace/webhtv/dev1/webhtv`） |
| 任务开始时 HEAD | `56372b7dbac436f88b34225e714adedb3854d0fa` |
| `origin/dev1` | `3148f030df9784be7b14fc924364c304980ff9ab` |
| `origin/beta` tip（`git ls-remote origin refs/heads/beta`） | `00ad80114fa971c32de3a74f072e33714b9f488e`（Merge pull request #435 from Silent1566/dev2） |
| 合并基点（merge-base） | `3148f030df9784be7b14fc924364c304980ff9ab`（Merge pull request #434 from Silent1566/dev1） |
| 合并命令与结果 | `git merge --no-commit --no-ff origin/beta` → **「自动合并进展顺利，按要求在提交前停止」**（`EXIT=0`，**0 冲突**，`git diff --name-only --diff-filter=U` 为空） |
| `MERGE_HEAD` | `00ad80114fa971c32de3a74f072e33714b9f488e`（= beta tip，即合并提交第二父） |
| 合并结果树 `git write-tree` | `34e0d3ec96c30201d249dc1eb2bfb62840f1e3a3` |
| 合并前 HEAD 树 | `10a766982aeea85fcfb69dbab8d2eef9755acc25`（与合并结果树**不同** → 本次合并确实引入 beta 侧内容，必须产生真实合并提交） |
| beta tip 树 | `dc0da01734037a902e4ee46c9ae380e858c1426e` |
| 是否需要新合并提交 | **需要**：合并前 `git merge-base --is-ancestor origin/beta HEAD` 为**假**（`00ad80114` 不在 dev1 上），故由 `task_guard.sh finish` 生成真实合并提交，第二父 = beta tip |
| 初始脏路径 | `.scratch-goal/`（371 条，登记为 protected，不提交） |
| 回滚锚点 | `56372b7dbac436f88b34225e714adedb3854d0fa` |

### 合并增量 ledger（beta 侧 5 个提交，全部纳入）

`git log --format="%H %P %s" HEAD..origin/beta`（本任务开始时）：

| 完整 commit ID | 父 | 标题 | 处置 |
| --- | --- | --- | --- |
| `00ad80114fa971c32de3a74f072e33714b9f488e` | `3148f030d` + `2275a50e7` | `Merge pull request #435 from Silent1566/dev2` | 纳入（合并第二父） |
| `2275a50e7140447ec61717de1957a150faee1512` | `8f20fab65` | `docs(c58): 记录 dev2 交付坐标（合并提交 8f20fab65a / recovery tag / beta tip 3148f030df）` | 纳入 |
| `8f20fab65aa2986b984b7848d948f102005bfb38` | `c27f34b95` + `3148f030d` | `merge: 合并 origin/beta 最新代码（PR#434 dev1 主题焦点环族 11 提交）并复评 dev2 已提交未推送的「退出播放页必定停播」修复` | 纳入（嵌套合并提交） |
| `c27f34b9506875873ebafcceab9f6785f8510f3d` | `97c7ae68a` | `docs(c58): 复评 dev2 已提交未推送提交（退出播放页必定停播）并确认无需合并，双 flavor 全量验证通过` | 纳入 |
| `97c7ae68abd9be76d2bdd1d79e34d93a9f6925ae` | `fb4fd7d99` | `fix(playback): 退出播放页必定停播，堵住「退回详情页还有声音/通知栏仍显示播放中」` | 纳入 |

**beta 侧本轮增量无任何删除/回退提交**：`git log --diff-filter=DR --name-status 3148f030d..origin/beta` 输出为**空**。

### 合并结果净差异（合并后相对 beta tip）

`git diff --name-status origin/beta 34e0d3ec96c30201d249dc1eb2bfb62840f1e3a3` = **8 路径**（3 修改 + 5 新增），全部来自 dev1 自身的 C59/C60 改动：

| 类型 | 数量 | 说明 |
| --- | --- | --- |
| M | 3 | `Update.java`、`AppVersion.java`（C59）；`AppDatabase.java`（C60） |
| A | 5 | C59/C60 的 3 个测试类 + 2 份任务文档 |

`git diff --diff-filter=D --name-status origin/beta <合并树>` 为**空**。

## 零复活三层证据（严格判据）

### 第 1 层：结构性

| 判据 | 结果 |
| --- | --- |
| `git merge-base --is-ancestor origin/beta HEAD`（合并前） | **假**（需真实合并） |
| 合并命令退出码 / 冲突数 | `0` / `0` |
| `MERGE_HEAD` | `00ad80114fa971c32de3a74f072e33714b9f488e` = beta tip |
| 净差异只含本分支路径 | 8 路径全部为 `app/src/**` 与 `docs/**`（dev1 自己的 C59/C60 改动） |
| `git diff --diff-filter=D` | 空（无删除） |
| `git diff --check` | 退出码 `0`；无冲突标记 |

### 第 2 层：行级（唯一可信判据）

对 beta 历史上**单亲回退/移除提交**取父，收集删除行，与 dev1 合并结果的新增行求交集，再逐行检查该行是否在 **beta tip 任何文件中都不存在**（只有这一层严格判据可信；表面命中通常是 `}`、`import`、`/**`、markdown 表格分隔等脚手架行）。

| 轮次 | 扫描提交数 | 删除行 | dev1 新增行 | 交集 | **严格复活行数** |
| --- | --- | --- | --- | --- | --- |
| 第 1 轮（合并后、修复前） | 336 | 69914 | 651 | 48 | **0** |
| 终检（修复后、工作区状态） | 407 | 53889 | 432 | 56 | **0** |

- 交集行 100% 为通用脚手架行（`}`、`try {`、`import …`、`/**`、`| --- | --- |`、`## Recovery anchor`、```` ```java ```` 等），**每一行都在 beta tip 中存在**。
- 最需要防范的回退点 `be1b02e06b22a4fa2f08c791555536e3e6154c95`（`revert: remove dynamic theme color system`）与 `5682f2b05`（`剔除 PR #353 主题系统改动`，**不在 beta 历史**，仅在 `remove-pr353-theme-changes`）已按 C58 的方法复扫，复活行数为 **0**。

### 第 3 层：关键移除目标逐个核对（本轮最硬的结构性证据）

对 **beta tip 的 5025 个文件逐个做 blob 比对**：

| 判据 | 结果 |
| --- | --- |
| beta tip 有而合并树没有的文件（潜在丢失） | **0** |
| 合并树新增文件（dev1 自己的新文件） | 5（3 个测试类 + 2 份任务文档） |
| beta 文件在合并树中逐字节一致 | **5021 / 5025** |
| 内容不同（= dev1 自己的 C59/C60 改动） | 4（`AppDatabase.java`、`Update.java`、`AppVersion.java`、`AppVersionBuildStampTest.java`） |

即：**合并树 ≡ beta tip + dev1 自己的 8 个路径**，无任何 beta 内容丢失，也没有任何 beta 已删除内容被重新写回。

## 复评记录

### 第 1 轮（合并后，对 dev1 全部已修改代码 = 相对 beta tip 的 8 个路径）

复评对象与方法：以 `git diff --name-status origin/beta <合并树>` 为全集，逐模块复核（生产代码 + 测试有效性 + 任务文档与实现是否一致 + 影响面）。除人工审读外，套用 C58 已验证的三个可机械化扫描：

1. **悬空 javadoc**（`*/` 结尾的下一行以 `/**` 开头）→ 命中 **0**。
2. **中文错别字**（本批次新增行的 CJK 2-gram 统计，找「只出现 1 次但与高频词仅差一字」的候选）→ 逐个人工核对，**无真实错别字**（候选均为正常词，如 `一分`/`一发`/`一并`/`一是`/`一机` 等）。
3. **注释与实现矛盾**（把注释里的断言性短语拿去对照实现常量与分支）→ 命中 **2 项真实缺陷（F1、F2）**。

影响面复核结论（均为「无泄漏」）：本轮净差异**不触碰** `app/src/**/res/**`、`native`、第三方锁文件（`third_party/*.json`、`gradle/libs.versions.toml`）、播放器链路（`player/**`、`PlaybackActivity`）、统计写入与回退闸门。其中 `AppDatabase` 的 `openHelperFactory` 只在 Room 打开期生效，`quarantine()` 仅作用于数据库目录内以库名开头的文件；仓库内对 `databases/` 目录的唯一其它引用是 `LoginStateSync` 的「学习期变化打分」（只看文件是否变化，不读写、不清理），`Path.clear()` 亦只在 `AppDatabase` 内部使用，未见副作用。

#### F1（真实缺陷）：跨长度构建时间戳被判为「更新」，可能重新推荐更旧的包

- **位置**：`app/src/main/java/com/fongmi/android/tv/utils/AppVersion.java` 的 `isNewer(current, target)`。
- **缺陷**：`if (targetStamp.length() != currentStamp.length()) return true;` 把「两个长度不同的**可用**时间戳」当成「无法比较」，直接判定目标为更新。
- **负对照（修复前实测，`/tmp/c61/negctl`，用 stub `BuildConfig` 单独编译并运行 `AppVersion`）**：

  ```text
  current stamp      = 20261009175522   (14 位，秒级)
  older target stamp = 202609221328     (12 位，更早)
  isNewer(current, olderTarget) = true   (expected false)   <-- 缺陷输出
  ```

  即：14 位时间戳的装机版本会把**更早**的 12 位时间戳渠道包当作更新推荐安装——正是 C59 要切断的「自动降级 / 跨渠道互相覆盖」那一类行为。
- **为什么是真实缺陷而不是「不可达分支」**：`BUILD_STAMP` 正则为 `\d{12,14}`，`buildStamp()` 的 javadoc 明确写 `yyyyMMddHHmm[ss]`，说明该分支被设计为支持秒级时间戳；而 `isNewer` 的 javadoc 声称只有「缺少可用时间戳」才回退到历史语义。两者互相矛盾，且一旦命中就是降级方向（CI 发布标签固定 12 位，`app/build.gradle` 的 `buildTimestamp` 也固定 12 位，因此正常发布不触发；触发条件是带 14 位标签的本地/非标构建与 CI 清单同 `versionCode` 相遇）。
- **最小修复**：新增 `STAMP_LENGTH = 14` 与 `padStamp()`，把分钟级时间戳补齐为「该分钟的第 0 秒」后再比较，删除长度不等即返回真的分支；只有真正缺失时间戳时才回退到历史语义。
- **锁定用例**：`AppVersionBuildStampTest#stampsOfDifferentLengthsAreStillOrdered`（4 条断言，覆盖「更旧的 12 位不得被当成更新」「同分钟更晚的秒级仍是更新」「同分钟无秒不算更晚」）。

### 第 2 轮（修复后复审）

| 复审项 | 结论 |
| --- | --- |
| F1 是否真的被消除 | **是**。修复后同一探针：`isNewer("v5.6.0-beta-20261009175522", "v5.6.0-202609221328") = false`；10 条探针断言全部 OK（含原有 12/12 位方向性用例） |
| F2 是否真的被消除 | **是**。两处注释已改为与实现一致的表述，并逐字复核无夸大 |
| 是否改变正常行为 | **否**。同长度时间戳（CI 全部发布标签均为 12 位）比较逻辑未变；`hasUpdate()` 的三个分支（更高/更低 `versionCode`、同版本号比时间戳）未变；缺时间戳回退语义未变 |
| 是否与仓库既有约定一致 | **是**。仅改判定函数内部与注释；新增用例沿用仓库既有的 JVM 单测风格；未引入新依赖、未改资源与门禁文件 |
| 是否泄漏到无关模块 | **否**。`padStamp()` 为 `private static`，`isNewer`/`buildStamp` 的公开签名与语义（除上述修正）未变；`AppVersion` 的生产调用点仍只有 `Update.hasUpdate()`、`CrashActivity`、`AboutDialog` |
| 第 1 轮的三项机械扫描是否复现新问题 | 悬空 javadoc 0、错别字 0、注释与实现矛盾 0 |

## 发现与处置

| 编号 | 类别 | 位置 | 处置 |
| --- | --- | --- | --- |
| **F1** | 真实缺陷（判定方向错误，可导致自动降级） | `AppVersion.isNewer` | **已修复**：长度不同的可用时间戳改为补齐后比较；补 `stampsOfDifferentLengthsAreStillOrdered` 用例锁定；负对照 + 修复后探针双向验证 |
| **F2** | 真实缺陷（注释与实现矛盾） | `AppDatabase` 的 `IntegrityRecoveringOpenHelperFactory` javadoc 与 `quarantine()` javadoc | **已修复**：原文称「不可读的数据库文件被保全而非删除、原文件留在磁盘上」，但实现中 `-wal/-shm/-journal` 会被清理，且 `renameTo` 失败时会删除主库文件（否则重建无法落盘）。注释改为如实描述「主库改名保全 + 边车文件清理 + 无法改名时删除」，行为零改动 |
| O1 | 观察项（`AGENTS.md` §2：只记录，不在本任务范围） | `Update.hasUpdate()` 与 `Updater.getPreferredUpdate()` | 强制检查时若两个渠道都没有更新，`getPreferredUpdate()` 仍会返回 stable 清单用于展示；但 `Updater.show()` 第 380 行 `if (selected == null \|\| !selected.hasUpdate())` 会直接提示「已是最新」并返回，**不会**走到下载/安装，因此不构成降级路径。属既有行为，未改动 |
| O2 | 观察项 | `C59` 文档「同版本号且**标题**相同」 | 「标题」应为「标签」（指发布标签 `name`）；属 C59 文档措辞，且该文档不在本任务净差异内（相对 beta tip 与本轮 HEAD 均一致），按 §2 只记录 |
| O3 | 观察项 | C59 文档 §残留风险「缺少可比时间戳的标签…实际不触发」 | 该表述在本轮 F1 修复后更加准确（长度不同不再被归入「缺少可比时间戳」）。C59 文档无需回改 |

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

`BUILD SUCCESSFUL in 7m 8s`（`EXIT=0`）。

| flavor | 用例数 | failures | errors | skipped | 与上一份基线对比 |
| --- | --- | --- | --- | --- | --- |
| leanback | **4191** | 0 | 0 | 2 | C58 = 4172 → **+19** |
| mobile | **5006** | 0 | 0 | 2 | C58 = 4987 → **+19** |

**增量逐项解释（双 flavor 各 +19，两 flavor 增量相同）**：

| 来源 | 增量 | 说明 |
| --- | --- | --- |
| C59（已提交未推送） | +12 | `AppVersionBuildStampTest` 7 + `UpdateOrderingTest` 5 |
| C60（已提交未推送） | +4 | `AppDatabaseIntegrityRecoverySourceTest` 4 |
| 合并带入的 beta 用例 | +2 | dev2 `97c7ae68a` 在 `app/src/test`（**两 flavor 共享**）给 `PlaybackOwnershipSourceTest` 新增 2 例 |
| 本轮 C61 的 F1 锁定用例 | +1 | `AppVersionBuildStampTest#stampsOfDifferentLengthsAreStillOrdered` |
| 合计 | **+19** | 12 + 4 + 2 + 1 = 19 |

（C58 基线 4172 / 4987 取自 `docs/C58-beta-merge-review-dev1-20261011.md` 的表格；两个 flavor 差值一致，说明新增用例全部落在两 flavor 共享的 `app/src/test`，与「Robolectric 只装在 leanback 变体」无关。）

关键用例实测执行且通过（JUnit XML 直读）：

```text
com.fongmi.android.tv.utils.AppVersionBuildStampTest        tests=8 failures=0 errors=0   （含 stampsOfDifferentLengthsAreStillOrdered）
com.fongmi.android.tv.bean.UpdateOrderingTest               tests=5 failures=0 errors=0
com.fongmi.android.tv.db.AppDatabaseIntegrityRecoverySourceTest tests=4 failures=0 errors=0
```

### UI token 门禁

```text
UI_TOKEN_BASELINE layouts=385 hex_layouts=1 drawables=553 hex_drawables=0 colors=59 hex_colors=0 allowlisted=190
UI_TOKEN_SCOPE    stage=A violations=1 legacy=0
UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28
UI_TOKEN_STATUS   PASS
```

`violations=1` 与 C56/C58 基线完全一致（**零新增**）。

### F1 的负对照与修复后探针（本任务新增的可证伪证据）

- 修复前（`/tmp/c61/negctl`，单独编译 `AppVersion` + stub `BuildConfig`）：`isNewer("v5.6.0-beta-20261009175522", "v5.6.0-202609221328") = true`（缺陷真实存在）。
- 修复后：同一调用返回 `false`，且 10 条探针断言（含 12/12 位方向性、缺时间戳回退、`null`）全部 `OK`。

## 改动清单

| 文件 | 改动 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/utils/AppVersion.java` | F1 修复：新增 `STAMP_LENGTH`/`padStamp()`，长度不同的可用时间戳改为补齐后比较；同步订正 javadoc |
| `app/src/test/java/com/fongmi/android/tv/utils/AppVersionBuildStampTest.java` | 新增 `stampsOfDifferentLengthsAreStillOrdered` 锁定 F1 |
| `app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java` | F2 修复：两处注释改为与实现一致（行为零改动） |
| `docs/C61-beta-merge-review-dev1-20261011.md` | 本文件 |
| （合并带入，非本任务改动） | `PlaybackActivity.java`、`PlaybackOwnershipSourceTest.java`、`docs/C58-beta-merge-review-dev2-20261011.md` |

## 与既有任务的关系

本任务与上游播放器依赖合并计划（`docs/upstream-player-dependency-merge-assessment-2026-08-20.md`）无关，不涉及 Exo/MPV/common 任何阶段，未改动任何上游依赖、锁文件或原生产物。被复评的 C59/C60 是同一用户反馈的两个独立原子改动，本任务只做「合并 + 复评 + 修复」，不重开其结论。

## 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交（双父） | `8b54e0e97c0a4e93d60d4453d55f36b2f1eedef0` |
| 第一父（合并前 dev1） | `56372b7dbac436f88b34225e714adedb3854d0fa` |
| 第二父（= 当时的 beta tip） | `00ad80114fa971c32de3a74f072e33714b9f488e` |
| recovery tag | `recovery/C61-beta-merge-review-dev1-20261011/20261011140009-8b54e0e97c0a` |
| 推送 | `git push origin dev1` → `5ad9025f9..8b54e0e97  dev1 -> dev1`；`git rev-parse origin/dev1` == `git rev-parse dev1` == `8b54e0e97c0a4e93d60d4453d55f36b2f1eedef0` |
| PR | [#436](https://github.com/Silent1566/webhtv/pull/436)（`dev1` → `beta`，**只创建，未合并**） |

## 闭环记录

| 复核项 | 结果 |
| --- | --- |
| `git rev-list --parents -n1 <合并提交>` | `8b54e0e97c0a… 56372b7dba… 00ad80114fa…` → **两个父**，第二父 == 当时的 beta tip（beta 侧提交未被当成 dev1 自己的提交） |
| `git merge-base --is-ancestor origin/beta HEAD`（收尾） | **真** |
| `MERGE_HEAD` | 已被 `git commit` 清除（`.git/MERGE_HEAD` 不存在） |
| `git diff --diff-filter=D --name-status origin/beta HEAD` | 空 |
| 工作区 | `git status --porcelain` 仅 `.scratch-goal/`（受保护脏路径，未提交） |
| PR 状态 | `state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`、`changedFiles=9`、`baseRefName=beta`、`headRefName=dev1` |
| PR 文件集校验 | `gh api repos/Silent1566/webhtv/pulls/436/files --jq '.[].filename' \| sort` 与 `git diff --name-only origin/beta HEAD \| sort` **逐项一致**（9 项，diff 无输出） |
| 守卫会话 | 主会话 `C61-beta-merge-review-dev1-20261011`（standard）、坐标会话 `…-coords` 与订正会话 `…-coords2`（quick-fix，scope `docs`）均已 finished；`.codex/task-state/current` 不存在（无 active 会话） |
| 收尾复核远端 | `git fetch origin --prune` 后 `git ls-remote origin refs/heads/beta` 仍为 `00ad80114fa971c32de3a74f072e33714b9f488e`，且 `git merge-base --is-ancestor origin/beta HEAD` 仍为**真**（合并基点未被推进） |
| 临时资源清理 | `/tmp/c61/**` 已删除 |

- 本次交付只创建 PR，未执行任何合并操作（`mergedAt=null`）。
- **本任务闭环完成。** 三项用户要求均已满足：① 合并远端 beta 最新代码且远端已移除/回退的内容**零复活**；② 复评全部已修改代码（含 2 个已提交未推送提交）并修复 2 项真实缺陷、2 轮评审通过；③ 提交（双父合并提交 + recovery tag）、推送 `dev1`、创建 PR #436（中文描述、只创建未合并）。
