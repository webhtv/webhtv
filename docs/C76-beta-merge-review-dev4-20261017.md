# C76：dev4 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码安全合入 `dev4`，不得复活 beta 已移除/回退内容；评审合并结果与 dev4 全部已提交未推送改动（含本轮修复），验证通过后提交、推送并创建中文 `dev4 -> beta` PR，**只创建不合并**。
- **允许路径**：`app/`、`chaquo/`、`scripts/`、`docs/`；任务开始时工作区仅有 `.design-evidence/`（未跟踪，按预存脏路径保护，不入提交）。
- **分支与回滚锚点**：`dev4`；第一父 `58de02cd78553afda3fbb52c0ab4f018a85e05a1`（上一轮 C75 合并提交，已推送）。
- **合并第二父（本轮 beta 权威 tip）**：`c1f0a5c3716ac1b44ed12a846b66b0a0a299523c`（PR #454）。
- **当前状态**：已交付（见 §5、§6）。
- **下一动作**：无（已交付）。

## 0. 本轮（第 2 轮）背景：PR #453 变 CONFLICTING 后的重新合并

上一轮交付后 `origin/beta` 由 `dae133ddc49` 前进到 `c1f0a5c3716`（PR #454，dev3 的合并评审），PR #453 由 MERGEABLE 变为 **CONFLICTING**。按 Skill 规定重新合并新 tip：

- 冲突面：**仅 1 个文件**（`docs/upstream-player-dependency-merge-assessment-2026-08-20.md`），且为 Skill 已记录的 **union 冲突**（各分支各自向任务索引表追加行）。
- **冲突根因：任务编号并发撞车。** dev3 的 `C75-beta-merge-review-dev3-20261016` 已随 PR #454 进入 beta，而 dev4 本轮任务在分配时 `C75` 确实空闲（`git log --all`/`docs/`/`origin/beta` 三处均无占用），属**并发占用**。
- **处置**：按仓库既有约定（后到者改名，与 C70 撞车时同口径），保留 beta 的 `C75`（dev3 权威行），把 dev4 本轮任务改号为 **`C76`**：
  - 评估索引：删除我方 C75 行、以 `C76` 行重新插入（beta 行零丢失，见 §3 行 ID 集合比对）；
  - 任务文档 `git mv` 为 `docs/C76-beta-merge-review-dev4-20261017.md` 并更新标题与 guard 引用；
  - guard state 的 `id` 同步改为 `C76-beta-merge-review-dev4-20261017`。
- **本轮 beta 增量是纯文档**（`docs/C75-beta-merge-review-dev3-20261016.md` 新增 + 评估索引修改），**无任何代码路径变更**，故上一轮的 4 项代码修复不受影响（6 个修复文件 blob 与上一轮提交逐一相同，已用 `git rev-parse` 核对）。

## 1b. 合并台账（第 2 轮：解决 PR #453 冲突）

| 项 | 值 |
| --- | --- |
| 合并前 HEAD（第一父） | `58de02cd78553afda3fbb52c0ab4f018a85e05a1`（上一轮 C75 合并提交，已推送） |
| 合并前 `origin/beta` | `dae133ddc49940dc001e4f3588315cfce6dc971d`（PR #452） |
| 本轮 `origin/beta` | `c1f0a5c3716ac1b44ed12a846b66b0a0a299523c`（PR #454） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta` |
| 冲突文件 | 1（`docs/upstream-player-dependency-merge-assessment-2026-08-20.md`，union 冲突） |
| 解决方式 | 保留 beta 的 `C75`（dev3）行 + 我方行改号为 `C76` 后插入；冲突标记 0 |
| 合并索引树 | `7ba55f47e3ad971b87e03867284867076fc246d8` |
| 本轮 beta 增量 | 2 路径，**均为文档**（`docs/C75-beta-merge-review-dev3-20261016.md` 新增、评估索引修改），**无代码变更** |
| PR #453 状态 | 解决前 `CONFLICTING` → 解决并推送后应为 `MERGEABLE` |

### beta 增量 disposition（第 2 轮）

| 完整 commit ID | 处置 |
| --- | --- |
| `c1f0a5c3716ac1b44ed12a846b66b0a0a299523c` | 本轮 beta tip（PR #454 合并提交），作为合并第二父 |
| `5abdf7a1cf198bc3f8076bb38ee84c337f610f66` | dev3 合并 `origin/beta`（PR #451/#452）的合并提交（零内容合并），随第二父纳入 |

## 1. 合并台账（第 1 轮）

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev4` |
| 合并前 HEAD（第一父） | `a29bbbed5efad5f9e3267843abe784d39158c520` |
| 合并前 `origin/dev4` | `139f2ab5d160be0c17dac9b48558b223fad41212`（本地领先 65，全部未推送） |
| 首次合并时 `origin/beta` | `9f4ea10a29d347d4a9290bdecdba7966374320fa`（PR #451） |
| 最终 `origin/beta` | `dae133ddc49940dc001e4f3588315cfce6dc971d`（PR #452） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`，自动合并无冲突 |
| 未解决冲突 | 0（`git diff --cached --diff-filter=U` 为空） |
| 合并索引树 | `cb3ddeec84697864daeb19155af9e5a23ef39883` |
| 本地净差异 | 6 路径（见 §5.2） |

### beta 增量 disposition

全部通过 merge 第二父纳入，**不执行 cherry-pick，不把已回退内容单独带回**：

| 完整 commit ID | 处置 |
| --- | --- |
| `dae133ddc49940dc001e4f3588315cfce6dc971d` | 最终 beta tip（PR #452 合并提交），作为合并第二父 |
| `2375d5a291f2f4f97a50f4c93e0fbe77f91f5c1e` | C74（dev1）交付坐标文档，随第二父纳入 |
| `1e0d3ae0a4ad6a3bc6e0b0a3a1a1d1ab5ff917a4` | dev1 合并 `origin/beta`（PR #450/#451）的合并提交，随第二父纳入 |
| `2d4259ef7a3` | dev1 已提交未推送的「电视影视原生播放页简介不显示与当前集无高亮」修复，随第二父纳入 |

### 中途 beta 前进的处置（本任务的关键风险事件）

首次合并后、验证期间 beta 由 `9f4ea10a29d` 前进到 `dae133ddc49`（PR #452）。按 Skill 规定**中止未提交的合并、按新 tip 重新合并并重跑验证**：

1. 备份本轮 6 个修复文件到工作区外（`git hash-object` 逐字节校验 MATCH）；
2. `git merge --abort` 回到第一父（注意：Gradle daemon 占用导致 `.git/index.lock` 残留，先 `gradlew --stop` 再手工清理 0 字节残留锁）；
3. 重新 `git merge --no-commit --no-ff origin/beta`（新 tip `dae133ddc49`），0 冲突；
4. 6 个修复文件逐一 `git hash-object` 复核仍与备份一致；
5. 复活/丢失审计与双 flavor 验证全部在新合并树上重跑（§3、§5）。

## 2. 任务编号调整

原计划使用 `C74`。合并中途发现 dev1 的 PR #452 已把 `C74-beta-merge-review-dev1-20261016` 合入 beta，编号被占用。按「C* 任务编号必须全分支查重后再分配」的既有约定，本次改为空闲的 **`C75`**（`git log --all`、`docs/`、`origin/beta` 三处均无 C75 占用），guard state 的 `id` 同步更新。

## 3. 复活与净差异审计（在最终合并树 `7ba55f47e3` 上）

判据均以**整树文件名集合**比较（规避 MSYS 把 `git cat-file -e <ref>:path` 的 `/` 当路径分隔符的已知假阴性）：

| 检查 | 第 1 轮（`cb3ddeec84`） | 第 2 轮（`7ba55f47e3`） |
| --- | --- | --- |
| A) 合并树有、beta 无、合并基也无（＝dev4 自身未推送新增，合法） | 52 | **53**（+1：`docs/C76-…` 文档改名） |
| B) 合并树有、beta 无、合并基有（＝复活候选） | **0** | **0** |
| C) beta 有、合并树无（＝合并丢内容） | **0** | **0** |
| D) revert 提交删除路径 ∩ 合并树独有路径（＝复活） | **0** | **0** |
| E) revert 删除且不在 beta 树、却出现在合并树（＝带回已移除内容） | **0** | **0** |

- **检测器非空转自检**（两轮均执行）：把一条真实「被 revert 删除且不在 beta 树」的路径（`app/src/main/assets/themes/index.json`）注入伪造合并树，检测器如期报出该路径。对照组真合并树该集合为空 → 证明「零复活」不是空转结论。
- **评估索引行 ID 集合比对（第 2 轮）**：beta 的 89 个行 ID（含 `C`/`E`/`P` 族）在工作区**全部保留**，仅新增 `C76`（`comm -23` 零丢失、`comm -13` 仅 `| \`C76\` |`）；C 族行数 beta 17 → 工作区 18。
- 自检期间另发现并规避两个既有陷阱：Python 文本模式写出的 CRLF 会让 `comm` 逐行比较失配（已用 `newline=''` 写 LF）；`git cat-file -e origin/beta:<path>` 在 MSYS 下会假报「不存在」，已改用 `git ls-tree -r --name-only` 整树比对。

## 4. 代码评审

### 第 1 轮：beta 增量 + dev4 已提交未推送改动

评审面覆盖 beta 增量 84 个路径（含播放器 `ExoCompressedAudioDirectPolicy` 的 offload 停滞探针、`FollowingWorkManager` 的直启存储适配、`TmdbDetailCache`/`TmdbUIAdapter` 的季集交接、Lab 遥控焦点族、站点弹窗面板）与 dev4 未推送 65 个提交（状态面三件套、焦点缩放收敛、token 接线、契约守卫族）。结论：合并交互点（4 个双方同时修改的文件）全部为干净的追加式合并；beta 侧删除面为空；两轮比对未发现由**合并本身**引入的必修缺陷。

### 第 2 轮：定向静态门槛全量执行 → 发现 3 项必修

以 `--strict` 跑静态门槛并跑双 flavor 全量 JVM 单测，暴露 3 项必修缺陷（全部属同一类：**源码文本守卫与实现脱钩，导致假红或误报**）：

**必修 ①（dev4 未推送引入，假红）** `UiStyleSourceTest.sharedComponentStylesExistAndOnlyConsumeTokens` 失败：dev4 未推送提交 `cb0bcc5c0bb` 在 `webhtv_styles.xml` 的**审核注释**里写了实测色 `#52000000`，而该 hex 守卫直接扫原始文本、未剥 XML 注释。删注释才能过是错误修法（会删掉证据）。
- 修法：守卫先剥 XML 注释再扫描（`withoutComments`），与仓库既有惯例一致（`TvFocusScaleContractTest`、`LabSettingsDialogThemeTest`、`Android7ApiCompatContractTest`）。
- 同类面一并修：`UiLayoutSourceTest` 的同一族 hex 断言（dev4 改动文件）。

**必修 ②（beta 带入，误报 + 设备已实装的不变量无人拦）** `check_ui_tokens.py/.sh --strict` 报 `violations=1`，指向 beta 带入的 `selector_lab_link.xml`——其唯一 hex 同样只在注释里。基线对照证明这不是既有违规：`HEAD` 树 = `violations=0`，合并前 `origin/beta` 树 = `violations=0`（该文件的 violation 由本文件的注释 hex 造成，且 `item_following.xml` 的旧违规已在 dev4 侧修复）。
- 修法：静态门槛 `has_hex()` 先剥 XML 注释（`.sh` 与 `.py` 两个入口同步，二者 `strip_xml_comments`/`has_hex` 逐字节等价），并顺手清掉该文件在本机被 lint 报出的既有小额债务（未用 import、`startswith` 元组化、f-string、EOF 换行），**不改变任何输出**：8 种 CLI 模式（空/`--strict`/`--baseline`/`--stage B..F`）两入口 stdout 与退出码逐字节相同。

**必修 ③（dev4 未推送引入，假红）** 双 flavor 全量单测中 `HomeCategoryNavigationSourceTest` 失败：dev4 未推送提交 `fad60a9b1e6` 把 TV 卡片间距从字面量 `16` 改为 token `ResUtil.dpOf(R.dimen.webhtv_size_tv_card_gap)`，但该守卫仍钉死 `new CustomRowPresenter(16, this::onContentHorizontalEdge)`。
- 修法：断言改为语义判定——卡片行确实把水平边缘回调装进 presenter（正则允许间距参数取任意值，含嵌套括号）；「间距必须来自 token」由 `UiLayoutSourceTest` 单独钉住，不再重复。

### 第 3 轮：全量回归暴露第 4 项必修（beta 自身即为红）

合并新 tip（PR #452）后，`TmdbUIAdapterTest.leanbackDirectTmdbPlaybackKeepsDetailPageTextStableDuringTmdbBind` 失败。

- **归属判定**：该测试文件在 `HEAD`、`origin/beta`、旧 beta `9f4ea10a29d` 三处 blob **逐一相同**；失败原因是 PR #452 把简介文本写入者从 `applyTmdbDetailFields()` 内联挪进了 `updateOverviewPreview()`，而守卫仍 `indexOf("mBinding.tmdbOverview.setVisibility(View.VISIBLE);")`。用 beta 自己的源码直接代入断言复算，判定**该守卫在 `origin/beta` 上自身即为红**，与本轮合并无关，但按「合并后必须通过」的要求属必修。
- 修法：断言保留其真实不变式（直接播放必须在接管前早退），接管点改为「两个写入者中实际存在的那个」——优先 `updateOverviewPreview();`，回退旧的内联赋值，因此对重构与旧写法都成立。

### 复评结论

第 4 项修复后重跑：`TmdbUIAdapterTest` 目标用例通过；再对全部 6 个修复文件做逐项复评，未发现新的必修问题（含第 2 轮自己放宽的 `HomeCategoryNavigationSourceTest` 断言——已二次收紧为语义正则并用变异证明非空转）。

## 5. 验证记录

### 5.1 双 flavor 全量 JVM 单测 + androidTest 源集编译（项目 provisioned 工具链 JBR 21.0.10）

最终合并树 + 全部修复：

| 任务 | 结果 |
| --- | --- |
| `:app:testLeanbackArm64_v8aDebugUnitTest` | `BUILD SUCCESSFUL`，677 suites / **4330 用例，0 failure / 0 error / 2 skipped** |
| `:app:testMobileArm64_v8aDebugUnitTest` | `BUILD SUCCESSFUL`，747 suites / **5117 用例，0 failure / 0 error / 2 skipped** |
| `:app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac` | `BUILD SUCCESSFUL` |
| `:app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac` | `BUILD SUCCESSFUL` |

### 5.2 静态门槛与净差异

- `python scripts/check_ui_tokens.py --strict` → `UI_TOKEN_SCOPE stage=A violations=0 legacy=0`、`UI_TOKEN_STATUS PASS`。
- `bash scripts/check_ui_tokens.sh --strict` → 同上（两入口 8 种模式输出逐字节一致）。
- `git diff --check`（工作区与暂存）均通过，无空白错误。
- 最终净差异相对 beta 为 6 路径，全部为本轮修复：
  `app/src/test/java/com/fongmi/android/tv/ui/style/UiLayoutSourceTest.java`、
  `app/src/test/java/com/fongmi/android/tv/ui/style/UiStyleSourceTest.java`、
  `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/HomeCategoryNavigationSourceTest.java`、
  `app/src/testMobile/java/com/fongmi/android/tv/ui/helper/TmdbUIAdapterTest.java`、
  `scripts/check_ui_tokens.py`、`scripts/check_ui_tokens.sh`。

### 5.3 变异测试（证明新增/加强的断言非空转）

| 探针 | 变异 | 期望 | 实测 |
| --- | --- | --- | --- |
| A1 | `selector_lab_link.xml` 注入真渲染 hex | 两入口转红 | ✅ `violations=1` / FAIL |
| A1 还原 | 恢复后 | 恢复 PASS 且与合并索引逐字节相同 | ✅ |
| B1 | `webhtv_styles.xml` 注入真渲染 hex | 守卫转红 | ✅ 2 例失败 |
| B1 还原 | 恢复后 | 逐字节还原 | ✅ |
| C1 | `TypeFragment` 卡片行边缘回调改 `null` | 转红 | ✅ |
| D1 | `applyTmdbDetailFields` 删掉 direct 早退 | 转红 | ✅ |
| E1 | `withoutComments` 改成恒等（退回不剥注释） | 转红 | ✅ 2 例失败 |
| E1 还原 | 恢复后 | 转红消失 | ✅ |

- 所有变异还原后均以 `git hash-object` 与合并索引逐字节比对确认（`UiStyleSourceTest`、`HomeCategoryNavigationSourceTest`、`TmdbUIAdapterTest`、`TypeFragment`、`webhtv_styles.xml`、`selector_lab_link.xml`、`adapter_episode_card.xml`、`VideoActivity.java`）。
- **重要工具陷阱（已实测）**：这类守卫在**运行时**读取仓库源文件，Gradle 无法把被读文件纳入任务输入，因此变异后直接重跑会出现 `UP-TO-DATE` 而**未真正执行**（C1 首测即如此，误得「未转红」的错误结论）。对源码读取型守卫做变异测试必须加 `--rerun-tasks`。

### 5.4 环境说明（JDK 差异，非本次改动）

- 本机默认工具链为 Microsoft JDK 21.0.12.8（`JAVA_HOME`），在该 JDK 下 `MpvFontConfigTest` 与 `MpvHlsCacheCoordinatorTest` 的 `File.renameTo` 覆盖已存在文件用例**确定性失败**（Windows 语义差异），项目 provisioned 工具链 **JBR 21.0.10**（`G:/GradleCache/jdks/jetbrains_s_r_o_-21-amd64-windows.2`）下通过。C49/C54/C57/C58 已记录同一差异。
- 生产代码 `MpvFontConfig.java`、`MpvHlsCacheCoordinator.java` 与 beta 逐字节相同、不在本轮净差异与 beta 增量内，按 AGENTS.md §2 仅记录不修改。

### 5.5 未执行

APK 打包、设备安装与实机播放矩阵、native 重建、完整 Instrumentation 测试。本轮为合并评审任务，判定性验证取「双 flavor 全量 JVM 单测 + androidTest 源集编译 + 静态门槛 + 复活审计 + 变异测试」，不扩大为设备级验收。

### 5.6 已记录但不在范围内

`scripts/check_ui_tokens.py` 在 ruff 下另有 6 条既有告警（未用 `argparse`、import 排序、`startswith` 元组化、`%` 格式化、EOF 换行）。已用 ruff 对 `HEAD` 版与工作区版逐条比对，证明这 6 条在 `HEAD` 即存在、与本轮改动无关；其中 5 条在本轮顺带清掉（行为不变），仓库内无 Python lint 门禁配置。

## 6. 交付坐标

- 合并提交（双父，第一父 `a29bbbed5ef`、第二父 `dae133ddc49`）与 recovery tag 由本轮 C76 guard 生成后回填。
- 推送 `dev4` 与 tag，创建中文 `dev4 -> beta` PR，**只创建不合并**。
