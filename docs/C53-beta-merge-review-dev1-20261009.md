# C53：dev1 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入 `dev1`（**远端已移除/回退的提交不得顺带带回**）；复评 dev1 全部已修改代码（含**已提交未推送**的 `fe1f725a1`、`72cdec02b`）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev1`、创建 `dev1 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① dev1 必须包含合并时刻的 `origin/beta` tip；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev1 既有改动**零丢失**；④ 双 flavor Java 编译通过；⑤ 双 flavor AndroidTest Java 编译通过；⑥ 双 flavor 全量 JVM 套件零失败；⑦ UI token 门禁相对基线零新增违规；⑧ 净差异只含本分支自身改动；⑨ 复评发现的问题已修复并锁定，或已按 AGENTS.md §2 明确记录处置；⑩ 提交 + recovery tag、`dev1` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并判定完成（`origin/beta` tip `fe1f725a1` 已是 `dev1` HEAD 的父提交，无需新合并提交）；2 轮复评完成；第 1 轮发现 2 个真实缺陷（F1 时长跨单位进位错误、F2 低于显示粒度的时长仍显示「0.0 秒」）并已修复 + 新增锁定用例；双 flavor 编译、双 flavor 全量 JVM 套件（leanback 4131 / mobile 4969，0 失败）、定向 34 项、UI token 门禁全部通过；零复活/零丢失三层证据重算通过。
- **交付坐标**：见文末「交付坐标」与「闭环记录」。
- **下一动作**：无（本任务已闭环：提交 + recovery tag + 推送 `dev1` + PR #428 已创建且未合并）。

## 时间与设备

- 任务开始时本地时间：2026-10-09 15:53（Asia/Shanghai）；开始时工作区**干净**（`git status --porcelain` 空），protected 脏路径 0 个。
- `dev1` 相对 `origin/dev1` 领先 2 个提交（`fe1f725a1` 合并提交 + `72cdec02b` 广告时长提示），即**已提交未推送**，本次复评对象即包含这两者引入的全部改动。
- 设备：**本轮未使用真机**。理由（风险相符原则）：本次复评的修复只改变**时长文本的舍入与显示门槛**（0.1 秒粒度、分钟进位、显示粒度以下退回条数），其输入是既有的 `removedDurationSec` 数值，不触碰净化判据、删除阈值、回退闸门、统计写入与播放链路；该缺陷类在仓库内已有先例与既有约定（`AdBlockTimeFormatter.formatSeconds` 及其测试 `59.96 → 00:01:00.0`），可由 JVM 单测**决定性**证伪；被改文案的设备实测证据已由 `72cdec02b` 记录（`docs/AD-NOTICE-01-ad-notice-total-duration.md`），本轮不重复。因此未申请机位、未重装 APK（`192.168.50.3:5555` 的既有包未受影响）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1` |
| 任务开始时 HEAD | `72cdec02b2b9b335fe5d2acbb9146ca7a927c39d`（`feat(adblock)`，领先 `origin/dev1` 2 个提交，**已提交未推送**） |
| `origin/beta` tip（`git ls-remote origin refs/heads/beta`） | `fe1f725a12925b9d3ce84801f52c9d2a047e26b6`（Merge pull request #427 from Silent1566/dev1） |
| `origin/dev1` | `e44dadffd60d18e0246f6dede5385366f79940b1` |
| 合并基点（merge-base） | `fe1f725a12925b9d3ce84801f52c9d2a047e26b6`（= 当前 beta tip，= `dev1` HEAD 的**直接父提交**） |
| 合并命令与结果 | `git merge --no-ff origin/beta` → **「已经是最新的。」**（`Already up to date.`，HEAD 未变、无新提交、无合并状态文件残留） |
| 是否需要新合并提交 | **不需要**：`git merge-base --is-ancestor origin/beta HEAD` 为真，`dev1` 已完整包含合并时刻的 beta tip |
| 初始脏路径 | 无 |
| 回滚锚点 | `72cdec02b2b9b335fe5d2acbb9146ca7a927c39d` |
| 任务守卫 | `C53-beta-merge-review-dev1`（standard，scope `app/src` + `docs` + `scripts` + `.codex/scripts` + `.codex/task-state`），`start` 发生在**任何编辑之前**，base HEAD = `72cdec02b` |

### 合并增量 ledger

`git log --format='%H %s' origin/dev1..dev1`（本任务开始时）= 2 个提交：

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `fe1f725a12925b9d3ce84801f52c9d2a047e26b6` | Merge pull request #427 from Silent1566/dev1 | 纳入（该对象本身就在 `origin/beta` 上，推送 `dev1` 不会引入 beta 之外的内容） |
| `72cdec02b2b9b335fe5d2acbb9146ca7a927c39d` | feat(adblock): 去广告成功提示补充总广告时长，三条 HLS 通道统一文案 | 纳入（本轮复评对象，见「复评记录」） |

`git diff --name-status origin/beta HEAD` = **8 路径**（7 修改 + 1 新增），全部来自 `72cdec02b`（本分支自身改动），无一条来自 beta 之外的来源：

| 路径 | 类型 |
| --- | --- |
| `app/src/main/java/androidx/media3/mpvplayer/MpvHlsProxy.java` | 修改 |
| `app/src/main/java/com/fongmi/android/tv/player/exo/ExoHlsAdblockDataSource.java` | 修改 |
| `app/src/main/java/com/fongmi/android/tv/server/process/M3u8.java` | 修改 |
| `app/src/main/java/com/fongmi/android/tv/utils/HlsAdblockNotice.java` | 修改 |
| `app/src/test/java/com/fongmi/android/tv/player/exo/ExoHlsAdblockDataSourceTest.java` | 修改 |
| `app/src/test/java/com/fongmi/android/tv/utils/HlsAdblockNoticeTest.java` | 修改 |
| `app/src/test/java/com/fongmi/android/tv/utils/HlsAdblockPipelineTest.java` | 修改 |
| `docs/AD-NOTICE-01-ad-notice-total-duration.md` | 新增（本分支任务文档） |

增量零丢失：合并判定前后 `origin/beta` 未前进（`git ls-remote` 与 `git fetch origin --prune` 均为同一 tip `fe1f725a1`），且 `dev1` HEAD 以该 tip 为直接父提交。

## 用户核心关注点：远端已移除（回退）内容零复活

采用与 C41/C46/C48/C50/C52 相同的三层递进证据，全部程序化重算。

### 第一层：结构性证明（最强证据）

`git merge-base --is-ancestor origin/beta HEAD` = **真**（`fe1f725a1` → `72cdec02b`），即合并后树就是「beta tip + 本分支自身改动」：

```text
beta tip 是 HEAD 的祖先                          : 是
diff origin/beta..HEAD 路径数                    : 8（7 修改 + 1 新增，全部为本分支自身改动）
diff origin/beta..HEAD 中 beta 有而 HEAD 缺失的路径 : 0（--diff-filter=D 空）
```

**结论**：dev1 的每一个字节要么来自 beta tip，要么来自本分支自己的 8 个路径改动 → 从 beta 之外引入内容在结构上不可能 → **结构性零复活 + 结构性零丢失**。

### 第二层：行级复活扫描

对 beta 上每个主题命中 `revert|remove|removal|delete|drop|回退|移除|删除|剔除|撤销` 且**单亲**的提交 `R` 取父 `R^`，用 `git diff --no-renames -U0 R^ R` 收集删除行；复活定义为「该行出现在 `diff origin/beta..HEAD` 的新增行中，且该行在 `origin/beta` tip 的**任何**文件中都不存在」。

```text
beta 历史提交总数（git rev-list --count origin/beta）        : 4322
主题命中移除类且单亲的提交                                   : 139
其中产生删除行的文件版本数                                   : 134（删除行出现次数 42789）
distinct 删除行                                             : 26786
diff origin/beta..HEAD 的新增行 distinct                     : 134
「新增行 ∩ 删除行」表面命中                                  : 13
    —— 全部为通用脚手架行：`/**`、`*/`、`}`、`@Test`、``` 代码围栏、
       `import java.util.Locale;`、`import static org.junit.Assert.assertEquals;`、
       Markdown 表格分隔行 —— 这些行在 beta tip 中大量存在，属扫描噪声
严格判据（该行在 beta tip 中不存在）的复活行数               : 0
```

**结论**：零复活。第一层已从根上排除该可能，第二层为加固（与 C41 的结论一致：表面命中来自 beta 自己后续有意重新引入的内容，判据必须是与 beta tip 对照）。

### 第三层：关键移除目标与净差异核对

- `1b42d6624`（beta 移除手机版个性设置触屏优化入口）：本轮未触碰任何 `mobile` 设置代码，`git diff --name-status origin/beta HEAD` 中无相关路径。
- `be1b02e06`（`revert: remove dynamic theme color system`）：其删除文件在 HEAD 树中仍不存在（第一层已逐路径覆盖）。
- `git grep -nE '^(<<<<<<<|>>>>>>>)' -- app/src docs` 命中 **0**；`git diff --check` 退出码 **0**。

**结论：零复活 + 零丢失。**

## 复评记录

复评对象 = `dev1` 全部已修改代码（含已提交未推送）：`git diff origin/beta HEAD` 的 8 路径 / 180 增 11 删。

### 第 1 轮：逐模块复核

| 模块 | 复核项 | 判定 |
| --- | --- | --- |
| `HlsAdblockNotice.message(...)` | 是否已成为唯一文案入口；`structured` 判据是否已剔除；条数是否仍按既有措辞；未知时长是否退回条数 | 通过（但见 F1/F2） |
| `HlsAdblockNotice.durationText(...)` | 秒/分钟分档、0.1 秒粒度舍入、分钟进位、非有限值与负数、**低于显示粒度**、与仓库既有格式化约定的关系 | **不通过 → F1、F2** |
| 三条 HLS 通道接线 | `M3u8`（nano `/m3u8` 代理）、`ExoHlsAdblockDataSource`（Exo）、`MpvHlsProxy`（MPV/IJK）是否都改用共享入口；`removed` 兜底计数是否与旧行为等价；`shouldNotify` 去抖是否保持 | 通过（计数表达式差异见 N1） |
| `HlsAdblockPipeline` 时长来源 | 结构化路径 `removedDurationSec` 是否确为被删切片时长之和（`HlsManifestCleaner.clean` 逐片累加、`removedCount>0` 才返回 changed）；legacy 兜底路径 `legacyRemovedSegments` 是否逐片累加真实 `#EXTINF` 时长 | 通过（用户「只看到切片数」的根因即 legacy 通道被判据挡掉，修复方向正确） |
| 新增/改动测试有效性 | 断言是否精确（非 `startsWith` 弱断言）、是否会在回退生产改动时转红、三通道源码契约测试是否覆盖三条路径 | 通过（`ExoHlsAdblockDataSourceTest` 已由弱断言改精确文案；契约测试逐文件断言 `HlsAdblockNotice.message(` 且禁止旧格式残留） |
| `docs/AD-NOTICE-01-...md` | 缺陷定位、方案、变更清单、验收标准与实现是否一致；验证数字是否与实测相符 | 通过（定向用例 6/6/3 = 15/flavor 与实测一致；本轮修复后计数更新见 F 记录） |
| 影响面 | 是否触碰净化判据、删除阈值、回退闸门、统计写入、`res/`、native、依赖锁、播放器链路 | 通过（`git diff --name-status` 仅 7 个 Java + 1 个文档，无 `res/`、无 native、无锁文件） |

**第 1 轮结论**：发现 2 个真实缺陷（F1、F2），其余 1 项为按 AGENTS.md §2 只记录的观察项（N1）。

### 第 2 轮：修复后复审

| 复审项 | 证据 | 判定 |
| --- | --- | --- |
| F1 修复是否消除单位矛盾 | `durationText` 先按 0.1 秒粒度舍入再分档：`59.94 → 59.9 秒`、`59.96 → 1 分钟`、`90 → 1 分 30 秒`；秒档上界为 `tenths < 600`，故 `%.1f` 的输出恒 < 60.0，结构上不可能再出现「60.0 秒」 | 通过 |
| F1 修复是否与仓库既有约定一致 | 采用与 `AdBlockTimeFormatter.formatSeconds` 相同的「先舍入后进位」规则（该方法 javadoc 与测试 `59.96 → 00:01:00.0` 即为此约定），未引入第二套舍入语义 | 通过 |
| F2 修复是否消除「0.0 秒」 | `tenths == 0`（含 0、负数、非有限值、以及 `0 < d < 0.05`）时 `durationText` 返回空串，`message` 退回只显示条数；最小可显示值为 `0.1 秒`，结构上不可能再出现「0.0 秒」 | 通过 |
| 修复是否改变既有正常文案 | 既有断言全部保持：`message(3,27.5)`、`message(30,60)`、`message(45,125)`、`message(2,0/NaN/-1)`、Exo 精确文案、结构化/legacy 双通道文案——34 项定向用例全绿 | 通过 |
| 修复是否泄漏到无关模块 | 第 2 轮改动仅 `HlsAdblockNotice.java`（1 生产文件）+ 其单测，`git diff --stat` 为 2 文件 46 增 7 删 | 通过 |
| 结论 | 第 2 轮复审全部通过，无剩余阻塞项；可进入收口 | 通过 |

## 发现与处置

### F1（已修复）：时长跨单位边界产生自相矛盾的「60.0 秒」

**证据链**：复评前 `durationText` 用**未舍入**的原值分档（`if (safe < 60d) return String.format("%.1f 秒", safe)`），于是 `[59.95, 60)` 区间内的时长被格式化成 `60.0 秒`——既超出「秒」档的语义，也与仓库既有约定冲突：同一仓库的 `AdBlockTimeFormatter.formatSeconds` 在 javadoc 中明确写了

> Rounding is done before splitting the fields so values such as 59.96 seconds correctly carry into the next minute.

并用 `assertEquals("00:01:00.0", AdBlockTimeFormatter.formatSeconds(59.96))` 锁定。也就是说，本次新增代码重复了仓库**已经修过**的同一类舍入缺陷。

**负对照**（用复评前的原文逻辑单独执行，证明缺陷真实存在且用例可证伪）：

```text
old durationText(59.96) = [60.0 秒]      ← 自相矛盾
old durationText(59.94) = [59.9 秒]
old durationText(0.04)  = [0.0 秒]       ← 见 F2
```

**修复**：改为「先按 0.1 秒粒度舍入成 `tenths`，再用 `tenths` 分档」，秒档条件是 `tenths < 600`，分钟档由同一 `tenths` 换算，保证进位只发生一次。

**锁定**：`HlsAdblockNoticeTest.noticeRoundsToTenthsBeforeChoosingTheUnit`（`59.94 → 59.9 秒`、`59.96 → 1 分钟`、`90 → 1 分 30 秒`，以及 `message(12, 59.96) = 已跳过 12 个广告片段，总广告时长 1 分钟`）。

### F2（已修复）：低于显示粒度的时长仍显示「0.0 秒」

**证据链**：`AD-NOTICE-01` 的验收标准第 4 条写明「时长未知（0/NaN/负数）时退回只显示条数，**不出现「0.0 秒」误导**」，但复评前的实现只在 `removedDurationSec > 0` 时判断——`0 < d < 0.05` 的正值仍会走秒档并被 `%.1f` 格式化成 `0.0 秒`（负对照实测：`old durationText(0.04) = [0.0 秒]`），即验收标准在「小于显示粒度」这一子区间留有缺口。

**修复**：引入显示粒度 `TENTHS_PER_SECOND = 10`，`displayableTenths()` 对非有限值、`<= 0`、以及舍入后为 0 的值一律返回 0，`durationText` 据此返回空串，`message` 退回只显示条数；最小可显示值为 `0.1 秒`。

**锁定**：`HlsAdblockNoticeTest.noticeHidesDurationsBelowTheDisplayGranularity`（`0.04 → ""`、`0 → ""`、`message(2, 0.04) = 已跳过 2 个广告片段`、`0.16 → 0.2 秒`）。

### 只记录不修（按 AGENTS.md §2）

| 编号 | 观察项 | 不修理由 |
| --- | --- | --- |
| N1 | 三条通道的 `removed` 兜底计数表达式并不完全相同（`M3u8`/`MpvHlsProxy` 用 `(int) fallbackCount`，`Exo` 用 `outcome.legacy() ? 1 : 0`），而 `fallbackCount = max(1, removedSegments)` | 逐一验算两条分支等价（`legacy=true` 且 `removedSegments=0` 时都得到 1；`structured=true` 时 `HlsManifestCleaner` 保证 `removedCount ≥ 1`，都得到真实片数），改动它们只有重写收益、无行为收益，属本轮范围之外的清理 |
| N2 | `message()` 里 `Math.max(0, removedSegments)` 的钳位在所有调用点已由调用方钳过 | 纯防御性冗余，保留可读性收益大于删除收益 |
| N3 | 广告时长超过 60 分钟时显示为「N 分钟」而非小时制 | 结构化路径时长上限为 `HlsManifestCleaner.MAX_REMOVED_DURATION_SEC = 90` 秒，legacy 路径实测为秒级；小时制属当前不可达的展示形态，不为假想输入增加分支 |
| N4 | 同一份播放清单若同时经过 `/m3u8` 代理与 Exo/MPV 数据源，去抖键（上游 URL vs 本地代理 URL）不同，理论上可能提示两次 | `shouldNotify` 的去抖键与调用点均非本次改动内容（本次只改文案构造），且真实提示次数由既有设备实测为一次；不扩大范围 |
| N5 | 文案仍是硬编码中文，未走 `strings.xml` | 与三条通道原实现一致（原本即硬编码），本次未新增本地化缺口；顺带改造属范围外重构 |

## 验证

日志目录：`build/c53-verify/`（构建产物，不入库）。

### 4.1 负对照：证明 F1/F2 不是纸面推论

按复评前（`72cdec02b`）`durationText` 的**原文逻辑**单独编译执行，得到 `60.0 秒` 与 `0.0 秒`（见 F1/F2 的负对照块，JDK 21 实跑）。修复后的同一输入由单测断言为 `1 分钟` 与空串（退回条数）。**意义**：两条新增用例都能抓到复评前的真实输出，不是恒真断言。

### 4.2 定向单测（双 flavor）

```bash
bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests com.fongmi.android.tv.utils.HlsAdblockNoticeTest \
  --tests com.fongmi.android.tv.utils.HlsAdblockPipelineTest \
  --tests com.fongmi.android.tv.player.exo.ExoHlsAdblockDataSourceTest
```

`BUILD SUCCESSFUL in 1m 5s`、`EXIT=0`。JUnit XML 实测：

| flavor | HlsAdblockNoticeTest | HlsAdblockPipelineTest | ExoHlsAdblockDataSourceTest | 合计 | failures | errors |
| --- | --- | --- | --- | --- | --- | --- |
| leanback | 8 | 6 | 3 | 17 | 0 | 0 |
| mobile | 8 | 6 | 3 | 17 | 0 | 0 |

（`HlsAdblockNoticeTest` 由复评前的 6 项增至 8 项，新增的正是 F1、F2 两条锁定用例。）

### 4.3 双 flavor Java / AndroidTest 编译 + 双 flavor 全量 JVM 套件

单次 Gradle 调用（避免重复检查）：

```bash
bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac \
  :app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac :app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac \
  :app:testLeanbackArm64_v8aDebugUnitTest :app:testMobileArm64_v8aDebugUnitTest
```

6 个目标任务全部实际执行：`BUILD SUCCESSFUL in 1m 16s`、`EXIT=0`。解析 JUnit XML：

| flavor | suites | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- | --- |
| leanback | 649 | **4131** | **0** | **0** | 2 |
| mobile | 725 | **4969** | **0** | **0** | 2 |

较 C52 基线（leanback 4125 / mobile 4963）分别 +6 / +6，来源：`72cdec02b` 新增的 4 项文案用例 + 本轮 F1/F2 的 2 项锁定用例（双 flavor 各计一次）。无失败、无错误、无新增跳过。

### 4.4 UI token 门禁

```text
UI_TOKEN_BASELINE layouts=385 hex_layouts=1 drawables=554 hex_drawables=0 colors=59 hex_colors=0 allowlisted=190
UI_TOKEN_SCOPE    stage=A violations=1 legacy=0
UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28
UI_TOKEN_STATUS   PASS
```

唯一命中 `app/src/mobile/res/layout/item_following.xml`，**不在本次改动集内**，与 C50/C52 基线一致 → 相对基线**零新增违规**。

### 4.5 静态与结构校验

- 零复活/零丢失：见「合并台账」与三层证据（严格判据复活行数 **0**，`--diff-filter=D` 为空）；
- `git diff --check` 退出码 **0**；`git grep -nE '^(<<<<<<<|>>>>>>>)' -- app/src docs` 命中 **0**；
- `git diff --name-status origin/beta HEAD` = 8 路径，全部为本分支自身改动，无 beta 之外来源；
- 旧文案残留：`HlsAdblockNoticeTest.everyHlsAdblockChannelUsesTheSharedDurationNotice` 逐文件断言三条通道必须存在 `HlsAdblockNotice.message(`、且不得残留 `个广告片段（`（复评后仍通过）；
- 改动未触及 `res/`、native、依赖锁文件、播放器链路。

### 4.6 回滚

`git revert` 本轮提交（`HlsAdblockNotice.java` + 其单测）即回到 `72cdec02b` 的行为（时长跨单位边界显示「60.0 秒」、低于显示粒度显示「0.0 秒」）；该回滚只影响提示文案的格式化，无数据迁移、无 ABI/native 变更、无依赖变更、无播放行为变更。

## 改动清单（C53 相对 `72cdec02b`）

| 路径 | 类型 | 说明 |
| --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/utils/HlsAdblockNotice.java` | 修改 | F1：先按 0.1 秒粒度舍入再分档，消除「60.0 秒」；F2：显示粒度以下的时长返回空串，`message` 退回只显示条数，消除「0.0 秒」；javadoc 记录与 `AdBlockTimeFormatter` 一致的舍入约定 |
| `app/src/test/java/com/fongmi/android/tv/utils/HlsAdblockNoticeTest.java` | 修改 | 新增 F1、F2 两条锁定用例（单位进位边界、显示粒度边界） |
| `docs/C53-beta-merge-review-dev1-20261009.md` | 新增 | 本任务文档 |

## 交付坐标

| 项 | 值 |
| --- | --- |
| 任务起始 HEAD | `72cdec02b2b9b335fe5d2acbb9146ca7a927c39d` |
| `origin/beta` tip | `fe1f725a12925b9d3ce84801f52c9d2a047e26b6`（= `dev1` HEAD 的父提交，无需新合并提交） |
| 复评修复提交 | `926b9d50ca66853d393c7d144b8e9c196a8244f7`（4 路径：`HlsAdblockNotice.java`、`HlsAdblockNoticeTest.java`、`docs/AD-NOTICE-01-ad-notice-total-duration.md`、`docs/C53-beta-merge-review-dev1-20261009.md`） |
| recovery tag | `recovery/C53-beta-merge-review-dev1/20261009160214-926b9d50ca66`（guard 提交时自动创建，0s）；坐标提交自带 `recovery/C53-beta-merge-review-dev1-coordinates/*` |
| 推送 | `dev1` → `origin/dev1`：`e44dadffd..926b9d50c`（推送后 `origin/dev1 == dev1 == 926b9d50c`，0 ahead / 0 behind）；坐标提交再推送一次 |
| PR | [#428](https://github.com/Silent1566/webhtv/pull/428) `dev1 -> beta`，**OPEN、未合并**（`mergedAt=null`、`state=OPEN`、`mergeStateStatus=CLEAN`）；**只创建，未合并** |
| PR 文件集校验 | `gh api repos/Silent1566/webhtv/pulls/428/files` 分页合计 **9**，与 `git diff --name-only origin/beta HEAD` **逐项一致**（坐标提交只改本任务文档，不改变文件集） |
| PR 变更规模 | 9 文件 `+485 −11`（修复提交时刻值；坐标提交仅在本任务文档内追加内容） |
| 设备 | 本轮未使用真机（理由见「时间与设备」） |

### 闭环记录

- 合并判定：`origin/beta` tip `fe1f725a1` 已是 `dev1` HEAD 的直接父提交，`git merge --no-ff origin/beta` 返回「已经是最新的。」，**无需也无法产生新合并提交**；任务结束前再次 `git fetch origin --prune` 确认 beta 未前进（仍为 `fe1f725a1`），`git merge-base --is-ancestor origin/beta HEAD` 为真。
- 复评循环：第 1 轮发现 F1/F2 → 修复 + 两条锁定用例 → 第 2 轮复审全部通过 → 收口。
- 交付：`926b9d50c` 已提交并打 recovery tag → 推送 `dev1` → 创建 PR #428（只创建、未合并）。
