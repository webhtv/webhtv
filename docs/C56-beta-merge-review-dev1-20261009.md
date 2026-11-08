# C56：dev1 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入 `dev1`（**远端已移除/回退的提交不得顺带带回**）；复评 dev1 全部已修改代码（含**已提交未推送**的 2 个提交，即 PR #430 合并后新增的站点注入整页链路）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev1`、创建 `dev1 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① `dev1` 必须包含合并时刻的 `origin/beta` tip；② 远端被移除/回退内容**零复活**、beta 增量**零丢失**、dev1 既有改动**零丢失**；③ 双 flavor Java 编译 + 双 flavor AndroidTest Java 编译通过；④ 双 flavor 全量 JVM 套件零失败且增量可解释；⑤ UI token 门禁相对基线零新增违规；⑥ 复评发现的问题已修复并**双向负对照**锁定（或按 `AGENTS.md` §2 明确记录处置）；⑦ 提交 + recovery tag、`dev1` 已推送、PR 已创建且**未合并**，PR 文件集与 `git diff --name-only origin/beta HEAD` 逐项一致。
- **当前状态**：合并完成（无冲突、内容零变化，`MERGE_HEAD` = beta tip）；2 轮复评完成；第 1 轮发现 **1 个真问题**（F1：新增测试里的横屏“变异检验”是恒真算式、且**真实横屏配置下没有回归锁**——第 1 轮的横屏按比例手算实现在 JVM 层可原样复发而全绿）并已修复（改写负对照 + 新增 `land` qualifier 用例）；双 flavor 编译、双 flavor 全量 JVM 套件（leanback 651/4146、mobile 726/4980，0 失败）、UI token 门禁全部通过；零复活/零丢失三层证据通过。
- **交付坐标**：见文末「交付坐标」与「闭环记录」。
- **下一动作**：无（本任务已闭环）。

## 时间与设备

- 任务开始时本地时间：2026-10-09 19:18（Asia/Shanghai）；记录时 19:29。任务开始时工作区**干净**（`git status --porcelain` 空），protected 脏路径 0 个。
- `dev1` HEAD = `40ba21ed8766fd59d981b77e04c121557268fcb4`，领先 `origin/dev1`（`67d930ed63a4102652b894b1812df42825c8b078`）**2 个提交**，全部属于**已提交未推送**，是本轮复评对象：
  - `22348293b8b5aa3ca4b1c88e223eaf1b98743e78` `fix(csp): 站点注入竖屏铺满整页，底部按钮区不再被内容压扁`
  - `40ba21ed8766fd59d981b77e04c121557268fcb4` `fix(csp): 站点注入横屏也铺满整页，修“没有全屏”`
- 设备：**本轮未使用真机/模拟器**。理由（风险相符原则，`AGENTS.md` §4）：本轮**唯一的代码改动是测试文件**，不改变任何运行时行为；被复评的两个已提交未推送提交已在 `192.168.50.3:5555`（dev1 机位）按时序实测（竖屏 1080×2160@440 五条注入、横屏 1920×1080@280 用户复测机型，见 `docs/SITE-INJECT-FULLPAGE-20261009.md` 第 4、8 节），`git diff` 证明本轮未改一行生产代码（sha256 前后一致）。故未申请机位、未重装 APK（`192.168.50.3:5555` 上的既有包未被触碰）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1`（worktree `/home/maple/Workspace/webhtv/dev1/webhtv`） |
| 任务开始时 HEAD | `40ba21ed8766fd59d981b77e04c121557268fcb4` |
| `origin/dev1` | `67d930ed63a4102652b894b1812df42825c8b078` |
| `origin/beta` tip（`git ls-remote origin refs/heads/beta`） | `841a5707d59832cb18c4e8cc3e36e8841f3e3a3d`（Merge pull request #430 from Silent1566/dev1） |
| 合并基点（merge-base） | `67d930ed63a4102652b894b1812df42825c8b078`（= beta tip 的第二父，= `dev1` 的 `origin/dev1`） |
| 合并命令与结果 | `git merge --no-ff --no-commit origin/beta` → **「自动合并进展顺利，按要求在提交前停止」**（`EXIT=0`，**0 冲突**，`git diff --name-only --diff-filter=U` 为空，**0 文件变化**） |
| `MERGE_HEAD` | `841a5707d59832cb18c4e8cc3e36e8841f3e3a3d`（= beta tip，即合并提交第二父） |
| 合并结果树 `git write-tree` | `79182bfdb509d1a53102fa72ea839d79f205ab87` |
| 合并前 HEAD 树 | `79182bfdb509d1a53102fa72ea839d79f205ab87`（**与合并结果树逐字节相同** → 本次合并对工作区内容零影响） |
| beta tip 树 | `3ac6347257ec4bbf2e41df4ffaf97d61ca825700` |
| 是否需要新合并提交 | **需要**：合并前 `git merge-base --is-ancestor origin/beta HEAD` 为**假**（`841a5707d` 本身不在 dev1 上），故由 `task_guard.sh finish` 生成真实合并提交，第二父 = beta tip |
| 初始脏路径 | 无 |
| 回滚锚点 | `40ba21ed8766fd59d981b77e04c121557268fcb4` |

### 合并增量 ledger（beta 侧 1 个提交，全部纳入）

`git log --oneline HEAD..origin/beta`（本任务开始时）：

| 完整 commit ID | 标题 | 第一父 | 第二父 | 处置 |
| --- | --- | --- | --- | --- |
| `841a5707d59832cb18c4e8cc3e36e8841f3e3a3d` | `Merge pull request #430 from Silent1566/dev1` | `7e51768d881a725754dd68321ab802680ea30e1c`（PR #429 合并） | `67d930ed63a4102652b894b1812df42825c8b078`（本分支自己的交付坐标提交） | 纳入（合并第二父） |

**beta 侧增量 = 0 内容的判据**：`git merge-base HEAD origin/beta` = `67d930ed6`，而 beta tip 的**两个父提交**（`7e51768d8`、`67d930ed6`）**都已经是 HEAD 的祖先**（`git merge-base --is-ancestor` 两个均为真；其中 `7e51768d8` 由 dev1 的 `447934222` 合并提交带入）。因此 `git diff --name-status 67d930ed6 origin/beta` 输出为**空**，`git diff --name-status origin/beta HEAD` 只剩 dev1 自己的 3 个路径。本次合并**没有引入任何 beta 侧内容**，也没有任何“把回退内容捡回来”的空间。

### 合并结果净差异（合并后相对 beta tip）

`git diff --name-status origin/beta HEAD` = **3 路径**（1 修改 + 2 新增），全部来自 dev1 自身（站点注入整页链路），无一条来自 beta 之外：

| 路径 | 类型 | 归属 |
| --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/ui/dialog/CustomCspDialog.java` | 修改 | `22348293b` + `40ba21ed8` |
| `app/src/testLeanback/java/com/fongmi/android/tv/ui/dialog/CustomCspDialogLayoutTest.java` | 新增（本轮再改） | `22348293b` + `40ba21ed8` + 本轮 F1 |
| `docs/SITE-INJECT-FULLPAGE-20261009.md` | 新增 | `22348293b` + `40ba21ed8` |

`git diff --diff-filter=D --name-status origin/beta HEAD` = **空**（无删除，即无 beta 文件被本分支删除/覆盖）。

### 零复活三层证据（严格判据）

| 层 | 判据 | 结果 |
| --- | --- | --- |
| L1 结构性 | ① `git merge-base --is-ancestor origin/beta HEAD` 合并后为真；② `git diff --name-status origin/beta HEAD` 只含本分支 3 路径；③ `git diff --diff-filter=D --name-status origin/beta HEAD` 为空；④ beta 新增/独有文件（`git diff --name-status origin/beta HEAD` 中 A/D 行）为 **0**；⑤ 合并结果树 == 合并前 HEAD 树（`79182bfdb`） | 全部通过 |
| L2 行级 | 扫 beta 上主题命中 `revert\|回退\|回滚\|删除\|移除\|remov` 的**单亲**提交共 **127** 个，取父提交 `git diff --no-renames -U0 P C` 收集删除行；与 `git diff origin/beta HEAD` 的 **314** 条新增行求交，**表面命中 256 条**；逐条用「该行在 beta tip 任何文件中都不存在」的严格判据复核 | 表面命中全部是 `import com.fongmi.android.tv.App;` / `package …` / `import org.junit.Test;` 一类**通用脚手架行**（严格判据 `absent=False`，即它们在 beta tip 里都存在，只是同文件被本分支重写过）；**严格复活行数 = 0** |
| L3 关键移除目标 | 对 beta 已移除的能力关键词在净差异中逐一 `grep -c`：`Fingerprint`/`fingerprint`/`SpeechRecognizer`/`语音广告`/`VoiceAd`/`AdAudioDetect`/`SiteAdMarker`/`Nano` | 全部为 **0** 命中 |

结论：**零复活、零丢失**。本轮唯一的改动面是站点注入弹窗的窗口尺寸策略与它的测试，与 beta 上被移除/回退的音频指纹、语音广告识别、内置规则、动态主题等能力**不相交**。

## 复评第 1 轮（对象：净差异 3 路径 + 2 个已提交未推送提交）

### 生产代码（`CustomCspDialog.java`）

逐行复核 `applyPageSizing` / `expandToWindow` / `onStart` 的调用链与影响面：

| 检查项 | 结论 |
| --- | --- |
| 尺寸策略是否真的消除两处缺陷 | `params.width/height = MATCH_PARENT` + `SOFT_INPUT_ADJUST_RESIZE` + root/面板链 `match_parent` + 滚动区 `height=0/weight=1/maxHeight=0` + 固定 40dp 按钮区，与 dev1 实测（竖屏 40dp、横屏 root `[0,42][1920,1080]`）一致 |
| 是否残留按屏比例手算 | 无：`land`/`screenWidth`/`screenHeight` 三个参数已删除，调用点只传 `window/root/scroll` |
| 调用点唯一性 | `applyPageSizing` 仅 `CustomCspDialog.onStart()` 与测试调用（`grep -rn` 全仓 5 处，无其它生产调用点） |
| `expandToWindow` 终止性 | `for (ViewParent p = root.getParent(); p instanceof ViewGroup; …)` + `if (!(params instanceof ViewGroup.MarginLayoutParams)) break;`，到 DecorView 的 `WindowManager.LayoutParams` 自然结束；幂等，可重复 `onStart` |
| 是否触碰 `res/`、native、锁文件、播放器链路、统计写入 | 未触碰（净差异仅 1 个 Java 文件 + 1 个测试 + 1 个文档；`dialog_custom_csp.xml` 与依赖文件零改动，sha256 前后一致） |
| 回归风险 | 无新增；`applyPageSizing` 纯化后语义更窄（自动变差的风险下降） |

**生产代码无缺陷，本轮未改动生产代码。**

### 测试代码（`CustomCspDialogLayoutTest.java`）——发现 1 个真问题

**F1（真实缺陷，已修复）：横屏“变异检验”是恒真算式，且真实横屏配置下没有回归锁。**

- 证据 1（恒真）：修复前 `legacyParamsReproduceBothReportedDefects` 的横屏部分只有三条断言——`assertEquals((int)(W*0.76f), Math.round(W*0.76f))`（两边是同一条算式的两种取整）、`assertTrue(W - (int)(W*0.76f) > 0)`、`assertTrue(H - (int)(H*0.98f) > 0)`。它们**不调用任何生产代码**，对任意正数宽高都成立，实现无论怎么改都不会转红；文档与提交信息却把它表述成“变异检验…对应‘没有全屏’”。
- 证据 2（更大的缺口）：Robolectric 在本仓**只有 leanback 测试变体装了依赖**（`app/build.gradle:211 testLeanbackImplementation 'org.robolectric:robolectric:4.16'`，共享的 `app/src/test` 与 `app/src/testMobile` 都拿不到 Robolectric，因此布局测量类用例只能放 `testLeanback`），而 Robolectric 默认显示是**竖屏**。第 1 轮“横屏按朝向挑一套 0.76×0.98 手算窗口”的实现只在 `ResUtil.isLand(...)` 为真时才走到，修复前的 6 条（当时 5 条）用例在竖屏环境下**永远执行不到那个分支**，也就锁不住用户第二次反馈的“没有全屏”。
- 负对照 A（缺陷不可发现，实测）：把 `applyPageSizing` 临时改回第 1 轮的按朝向分支实现（`land` 分支 0.76×0.98、root 高度 `land ? params.height : MATCH_PARENT`、`if (!land) expandToWindow(root)`），**用修复前的测试文件**跑 `--tests CustomCspDialogLayoutTest` → `tests=5 failures=0 errors=0`（**全绿**）。即：横屏缺陷可以原样复发而全绿。
- 修复（仅测试）：
  1. `legacyParamsReproduceBothReportedDefects` 的横屏部分改为**用同一套面板链按旧窗口尺寸实测**（`measure(panel, 0.76W, 0.98H - statusBar)` 后断言 root 宽度 < 屏宽、root 高度 < 可用高度），把缺陷钉在布局测量上；再对**当前实现**断言窗口宽/高必须是 `MATCH_PARENT`（回到按比例手算时必然转红）。
  2. 新增 `landscapeOrientationQualifierAlsoFillsTheWholePage()`，用 `@Config(qualifiers = "w686dp-h386dp-land")` 让 `ResUtil.isLand` 为真，并**先自检该 qualifier 生效**（`assertTrue(ResUtil.isLand(...))`，否则用例会退化成竖屏重复），再断言窗口/root/滚动区参数与“铺满 + 按钮区 40dp 贴底”。
- 负对照 B（修复后转红，实测）：同一 M1 变异 + 修复后的测试文件 → `tests=6 failures=1`，`landscapeOrientationQualifierAlsoFillsTheWholePage` 失败：`java.lang.AssertionError: 真实横屏下 root 高度必须 MATCH_PARENT expected:<-1> but was:<378>`。而修复前的测试文件在同一变异下仍全绿。
- 负对照 C（正确实现全绿，实测）：撤销变异（生产文件 sha256 还原为 `311c8e0922aba5f4fdcd5730c50aa5abbe5d76518b62592125539d813e8b1eeb`）后 → `tests=6 failures=0`，双 flavor 全量套件 0 失败。

### 按 `AGENTS.md` §2 只记录的观察项（不修改）

| 编号 | 观察项 | 处置理由 |
| --- | --- | --- |
| O1 | 手机 flavor（`testMobile`）无法跑 Robolectric（依赖只在 `testLeanbackImplementation`），因此这个**共享 main 代码**的弹窗在手机 flavor 下没有 JVM 布局锁 | 构建配置约束，非本任务引入；源码守卫在 `testLeanback` 已覆盖同一份 main 代码，重复加 guard 只会产生噪声 → 记录不改 |
| O2 | 该弹窗同时被 TV/leanback 入口使用（`HomeActivity:982`、`SettingEnhanceActivity:103`），因此“整页”策略在电视上同样生效；本轮仍沿用第 2 轮决策（不在电视加 overscan 内缩），仅在手机/横屏模拟器配置实测 | 用户诉求是这一页统一整页；电视 overscan 收边需真机电视验证，已在 `docs/SITE-INJECT-FULLPAGE-20261009.md` 第 6 节记录为独立任务 → 记录不改 |
| O3 | 测试里 `statusBarPx()` 取固定 `dp(24)` 常量作为可用高度偏移 | 与既有测试一致的口径简化，对所有断言是同一偏移，不影响判定 → 记录不改 |

## 复评第 2 轮（修复后复审）

| 复审项 | 结论 |
| --- | --- |
| 修复是否真的消除 F1 | 是：恒真算式已删除；两条负对照证明修复前的用例对横屏实现变异全绿、修复后转红（`expected:<-1> but was:<378>`） |
| 是否与仓库既有约定一致 | 是：布局测量仍走既有 `Robolectric + 真实测量` 范式；`land` qualifier 与 `ResUtil.isLand()` 的真实语义一致（`Configuration.ORIENTATION_LANDSCAPE`） |
| 是否改变正常行为 | 否：本轮改动**只在测试文件**，生产文件 sha256 前后一致（`311c8e09…1eeb`） |
| 是否泄漏到无关模块 | 否：路径仅 `app/src/testLeanback/.../CustomCspDialogLayoutTest.java` + 两份文档 |
| 新增用例是否可靠 | 是：`land` 用例自带 qualifier 自检；6/6 断言在正确实现下稳定通过（连续两次运行结果一致），不依赖时序/随机 |
| 文档与实现是否一致 | 修复后已同步订正 `docs/SITE-INJECT-FULLPAGE-20261009.md` 的变异检验口径与用例数（5→6） |

## 验证

### 4.1 双 flavor 编译 + AndroidTest 编译 + 双 flavor 全量 JVM 套件

单次 Gradle 调用（避免重复检查），6 个目标任务全部**实际执行**（日志中均为 `> Task :app:<task>`）：

```bash
bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac \
  :app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac :app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac \
  :app:testLeanbackArm64_v8aDebugUnitTest :app:testMobileArm64_v8aDebugUnitTest
```

`BUILD SUCCESSFUL in 1m 24s`、`EXIT=0`。解析 JUnit XML：

| flavor | suites | tests | failures | errors | skipped | 上一份基线 | 差值 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| leanback | 651 | **4146** | **0** | **0** | 2 | 651 / 4145（`docs/SITE-INJECT-FULLPAGE-20261009.md` §8） | **+1 例** |
| mobile | 726 | **4980** | **0** | **0** | 2 | 726 / 4980（同上） | **0** |

差值逐项可解释：leanback `+1` = 本轮 F1 新增的 `landscapeOrientationQualifierAlsoFillsTheWholePage()`；suite 数不变（测试类文件本就已存在，`CustomCspDialogLayoutTest` 由 5 例增至 6 例，XML 实测 `tests=6 failures=0`）。mobile **0 变化**符合预期——改动只在 `app/src/testLeanback` 源集，手机 flavor 不含该源集；合并本身带来 0 内容变化，故也没有任何外部用例数变动。

### 4.2 UI token 门禁

```text
UI_TOKEN_BASELINE layouts=385 hex_layouts=1 drawables=554 hex_drawables=0 colors=59 hex_colors=0 allowlisted=190
UI_TOKEN_SCOPE    stage=A violations=1 legacy=0
UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28
UI_TOKEN_STATUS   PASS
```

四项与 C50/C52/C53/C54/C55 基线**逐项一致**（唯一命中仍不在本次改动集内）→ 相对基线**零新增违规**。

### 4.3 静态与结构校验

- 零复活三层证据通过：严格复活行数 **0**、`--diff-filter=D` **空**、beta 独有文件 **0**、合并结果树 == 合并前 HEAD 树；
- `git diff --check` 与 `git diff --cached --check` 退出码 **0**；`git grep -nE '^(<<<<<<<|>>>>>>>|=======)$' -- app/src docs` 命中 **0**；
- 生产文件在变异后按 sha256 **字节还原**（`CustomCspDialog.java` = `311c8e0922aba5f4fdcd5730c50aa5abbe5d76518b62592125539d813e8b1eeb`，`dialog_custom_csp.xml` = `936563c7…b7c`）；
- 合并状态未被人为破坏：`MERGE_HEAD` 全程为 beta tip，**未使用任何 `git reset`**（避免退化成单父普通提交而把 beta 侧提交当成本分支改动）；
- 改动未触及 `res/layout`、native、依赖锁文件、播放器链路、统计写入。

### 4.4 回滚

- 本轮合并提交是 merge commit（第二父 = `841a5707d`）：`git revert -m 1 <merge-commit>` 即回到 `40ba21ed8`；
- F1 的修复是纯测试：把 `legacyParamsReproduceBothReportedDefects` 的横屏段与 `landscapeOrientationQualifierAlsoFillsTheWholePage()` 还原即回到上一轮状态，**无运行时影响**；
- 无数据迁移、无 ABI/native 变更、无依赖变更、无播放行为变更。

## 改动清单（C56 相对本次合并树）

| 路径 | 类型 | 说明 |
| --- | --- | --- |
| `app/src/testLeanback/java/com/fongmi/android/tv/ui/dialog/CustomCspDialogLayoutTest.java` | 修改（+36 / -5） | F1：删除横屏恒真算式，改为按旧窗口尺寸的**实测负对照** + 当前实现 `MATCH_PARENT` 断言；新增 `land` qualifier 回归锁 `landscapeOrientationQualifierAlsoFillsTheWholePage()`（含 qualifier 自检）；类注释同步 |
| `docs/C56-beta-merge-review-dev1-20261009.md` | 新增 | 本任务文档 |
| `docs/SITE-INJECT-FULLPAGE-20261009.md` | 修改 | 订正变异检验口径（恒真算式 → 实测负对照 + 横屏回归锁）与用例数（5 → 6）、补 C56 复评记录 |

（合并提交本身另含 beta 带入的 0 个路径，见「合并增量 ledger」，即合并未引入任何内容。）

## 交付坐标

| 项 | 值 |
| --- | --- |
| 任务起始 HEAD | `40ba21ed8766fd59d981b77e04c121557268fcb4` |
| 合并前 `origin/beta` tip | `841a5707d59832cb18c4e8cc3e36e8841f3e3a3d` |
| 合并提交（本任务唯一提交） | `fe4d02e076d781f5158d791c6aae8e0d216f07fb`（两个父：`40ba21ed8` + `841a5707d`） |
| 合并结果树 / 合并前 HEAD 树 | `79182bfdb509d1a53102fa72ea839d79f205ab87`（同一棵树） |
| recovery tag | `recovery/C56-beta-merge-review-dev1/20261009193053-fe4d02e076d7` |
| 推送 | `git push origin dev1` → `67d930ed6..fe4d02e07  dev1 -> dev1`（`origin/dev1` = `fe4d02e07` = 本地 HEAD） |
| PR | [#431](https://github.com/Silent1566/webhtv/pull/431)（`dev1 -> beta`，中文正文，**只创建未合并**） |
| PR 校验 | `state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`、`changedFiles=4`；PR 文件集与 `git diff --name-only origin/beta HEAD` 逐项一致 |
| 收尾复核 | `git ls-remote origin refs/heads/beta` 仍为 `841a5707d`（未前进，本合并已含该 tip） |

## 闭环记录

- 2026-10-09 19:30（Asia/Shanghai）：`task_guard.sh finish` 生成合并提交 `fe4d02e07` + recovery tag，`git rev-list --parents -n1 fe4d02e07` 校验为**两个父**且第二父 == beta tip（`841a5707d`），`MERGE_HEAD` 已清空，工作区干净。
- `git push origin dev1` 成功；`gh pr create --base beta --head dev1` 创建 PR #431（**未合并**，`mergedAt=null`）。
- PR 文件集校验：`gh api repos/Silent1566/webhtv/pulls/431/files` 的 4 个文件名与本地 `git diff --name-only origin/beta HEAD` **逐项一致**：`CustomCspDialog.java`、`CustomCspDialogLayoutTest.java`、`docs/C56-beta-merge-review-dev1-20261009.md`、`docs/SITE-INJECT-FULLPAGE-20261009.md`。
- 本闭环只改文档（新增本文件、订正 `docs/SITE-INJECT-FULLPAGE-20261009.md`），再次推送后 PR #431 会自动带上文档更新。
