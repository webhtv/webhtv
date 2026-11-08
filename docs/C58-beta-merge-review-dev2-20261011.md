# C58：dev2 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入 `dev2`（**远端已移除/回退的提交不得顺带带回**）；复评 dev2 全部已修改代码（含**已提交未推送**的 `97c7ae68ab`「退出播放页必定停播」修复）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev2`、创建 `dev2 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① `dev2` 必须包含合并时刻的 `origin/beta` tip；② 远端被移除/回退内容**零复活**、beta 增量**零丢失**、dev2 既有改动**零丢失**；③ 双 flavor Java 编译 + 双 flavor AndroidTest Java 编译通过；④ 双 flavor 全量 JVM 套件失败集与未触碰基线**逐类一致**（无新增失败）；⑤ 复评发现的问题已修复并锁定（或按 `AGENTS.md` §2 明确记录处置）；⑥ 提交 + recovery tag、`dev2` 已推送、PR 已创建且**未合并**，PR 文件集与 `git diff --name-only origin/beta HEAD` 逐项一致。
- **当前状态**：**两阶段**。阶段一：`origin/beta` tip（`fb4fd7d99c`）当时已是 `dev2` 的直系父提交 ⇒ 无需合并；2 轮复评未发现必修缺陷，未改生产代码，仅提交任务文档 `c27f34b950`（+ recovery tag）并推送。阶段二：**推送后发现 beta 前进**（`fb4fd7d99c` → `3148f030df`，PR #434 dev1 主题焦点环族 11 个提交）⇒ 按既有规则以**新 tip 重新合入、复评（第 3 轮）、重新验证**；合并 0 冲突，合并结果树 == git 自动合并结果树。
- **交付坐标**：见文末「交付坐标」与「闭环记录」。
- **下一动作**：无（本任务已闭环）。

## 时间与设备

- 任务开始时本地时间：2026-10-11 11:28（Asia/Shanghai）；阶段一提交 11:48；阶段二合并与验证至 12:4x。
- 任务开始时工作区**干净**（`git status --porcelain` 为空；仅有两份被 `.gitignore:27 *.log` 忽略的验证日志，未进入版本控制）。
- 设备：**本轮未使用真机/模拟器**。理由（风险相符原则，`AGENTS.md` §4）：本轮**未改一行生产代码**——复评对象是已提交未推送的 `97c7ae68ab`（`PlaybackActivity.stopPlayback()/startPlayer()` 两处语义 + 两条源码契约测试）；阶段二新合入的 beta 增量（dev1 主题焦点环族）已由 dev1 在其机位实测并经 beta 合入（见随合并带入的 `docs/C58-beta-merge-review-dev1-20261011.md`）。故未申请机位、未重装 APK。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2`（worktree `F:/Workspace/webtv2/webhtv`） |
| 任务开始时 HEAD | `97c7ae68abd9be76d2bdd1d79e34d93a9f6925ae` |
| 任务开始时 `origin/dev2` | `34a93e243f660df19b2b23eef724f0f882002e8b` |
| 阶段一 `origin/beta` tip | `fb4fd7d99ca1547ea624f3d5f1b7f3cd81b880a5`（Merge PR #433 from dev2） |
| 阶段一 merge-base | `fb4fd7d99ca1547ea624f3d5f1b7f3cd81b880a5`（**= 当时的 beta tip**）⇒ `git merge-base --is-ancestor origin/beta dev2` **EXIT=0**、反向 **EXIT=1**，**无需合并** |
| **阶段二 `origin/beta` tip（提交时权威）** | `3148f030df9784be7b14fc924364c304980ff9ab`（Merge PR #434 from Silent1566/dev1） |
| 阶段二合并方式 | `git merge --no-commit --no-ff origin/beta` → **「Automatic merge went well; stopped before committing as requested」**（`EXIT=0`，**0 冲突**，无 `Auto-merging` 提示） |
| `MERGE_HEAD` | `3148f030df9784be7b14fc924364c304980ff9ab`（= beta tip，即合并提交第二父） |
| 合并结果树 `git write-tree` | `c681b54ccfcb24a30043a80df24933736735dce6` |
| git 自动合并结果树（`merge-tree --write-tree`） | `c681b54ccfcb24a30043a80df24933736735dce6`（**与索引树逐字节相同**） |
| 合并前 HEAD 树 / beta tip 树 | `5db5b7461ab6dbb86f327049467704f4b6374362` / `e4e6ab993c19d10038d7cbabe780c36a9404bc52` |
| 阶段二合并结果净差异 | `git diff --cached --name-status origin/beta` = **3 路径**（2 修改 + 1 新增），全部为 dev2 自身（见下节） |
| dev2 本地内容是否被合并改写 | 否。`PlaybackActivity.java` blob `d29bd54fd31f9e586c9729aa7d6ac1412778bc1d`、`PlaybackOwnershipSourceTest.java` blob `138fb42c41a08634ea541ce04f068838d7ee488b` 与合并前提交版本**逐字节一致** |
| 推送方式 | 阶段一 `git push origin dev2` = `34a93e243f..c27f34b950`（快进）；阶段二合并提交后快进推送 |
| 历史完整性 | 本轮**未使用** `git reset`/`--amend`/`rebase`；阶段一提交 `c27f34b950` 单父 = `97c7ae68ab`；阶段二为真实合并提交（双父：`c27f34b950` + `3148f030df`）；上一轮 amend 掉的悬空提交 `3c5fc73d21` **不在**祖先链 |
| 初始脏路径（阶段二） | 合并结果全部路径（`app/**`、`docs/**`），已按 `--scope app --scope docs --adopt-dirty app --adopt-dirty docs` 纳入 |
| 回滚锚点（阶段二） | `c27f34b9506875873ebafcceab9f6785f8510f3d`（阶段一提交，也是合并提交第一父） |

### 合并结果净差异（合并后相对 beta tip）

| 路径 | 类型 | 归属 |
| --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java` | 修改 | `97c7ae68ab` |
| `app/src/test/java/com/fongmi/android/tv/ui/activity/PlaybackOwnershipSourceTest.java` | 修改 | `97c7ae68ab` |
| `docs/C58-beta-merge-review-dev2-20261011.md` | 新增 | 本任务文档（`c27f34b950`） |

`git diff --cached --diff-filter=D --name-status origin/beta` = **空**（无删除，即无 beta 文件被本分支删除/覆盖）。

### beta 增量 ledger（阶段二，`fb4fd7d99c..3148f030df` 共 11 个提交，全部纳入）

| 组 | 代表提交（完整 ID） | 内容 | 处置 |
| --- | --- | --- | --- |
| 主题默认配色 | `6be5c5ee40` | 默认配色向上游对齐（TV 默认深色 + 白色焦点环 + 白色加载指示器） | 纳入 |
| 播放页高亮收敛 | `649275b762` | 播放页只留一种高亮色（芯片宿主分层修正 + 深色表视频层环取近白） | 纳入 |
| 焦点环统一（TV 应用表面） | `d07a8454f7`、`d8f958671a`、`540d70b046`、`b8581afc41`、`c3bb6a63d9`、`7948c8e979` | 统一 TV 端应用表面焦点环并接线主题色板、收口宽度与圆角、修正对话框主题缺 `tv*` 属性导致的黑色边框 | 纳入 |
| 全屏播放画面焦点环（加后回退） | `0209015d62` → `8ba33b11fc`（revert） | 先加内缩环，后按用户纠正**完整 revert**（全屏画面不加环、控制栏/OSD 保留） | 纳入（净效果 = 已回退） |
| dev1 交付与合并 | `5ad9025f93`、`2ee22ce216`、`3148f030df` | dev1 合并评审文档 + 其合并提交 + PR #434 合并提交 | 纳入 |

**beta 侧增量零丢失判据**：合并结果树 `c681b54c` 与 `git merge-tree --write-tree dev2 origin/beta` 的自动合并结果树**逐字节相同**；且净差异只含 dev2 自身 3 路径，无一条 beta 路径被覆盖或删除。本轮**两侧改动零交集**（beta 增量未触及 `PlaybackActivity.java`，dev2 亦未触及主题族任何文件）⇒ 不存在"同文件两侧改写"的手工调和面，与"0 冲突、无 Auto-merging"一致。

### 零复活三层证据（判据与 C57 同构，含检测器自检）

| 层 | 判据 | 结果 |
| --- | --- | --- |
| L1 结构性 | ① 合并结果树 == `git merge-tree --write-tree` 自动合并结果树（`c681b54c`）；② 净差异只含 dev2 自身 3 路径；③ `--diff-filter=D` 为空；④ dev2 两个改动文件 blob 与提交版本**逐字节一致** | 全部通过 |
| L2 路径级 | 主题行锚定的 revert 提交集共 **183** 个（`^(Revert\|revert\|回退\|撤销\|剔除)`）；其 `--diff-filter=D` 删除路径去重 **66** 条；其中 **47** 条已不在 beta 树（=真被移除）。**正式扫描：47 条真移除路径在合并结果树 `c681b54c` 中的命中数 = 0**；66 条与本次净差异的交集 = **0** | **严格复活数 = 0** |
| L3 关键移除目标 | 对 beta 已移除能力关键词在本次净差异中计数：`Fingerprint`/`fingerprint`/`SpeechRecognizer`/`VoiceAd`/`AdAudioDetect`/`SiteAdMarker`/`ThemeController` | 全部为 **0** 命中 |

结论：**零复活、零丢失**。dev2 的改动面是"播放页显式退出的停播确定性"，与 beta 上被移除/回退的音频指纹、语音广告识别等能力**不相交**；本次 beta 增量中的 revert（`8ba33b11fc` 回退全屏画面焦点环）是 beta 的**既定净效果**，随合并正确带入，未出现"被回退内容复活"。

## 复评第 1 轮（对象：唯一已提交未推送提交 `97c7ae68ab`）

复评对象：`git diff --stat origin/beta HEAD` = 2 文件、75 插入 / 4 删除（`PlaybackActivity.java` +24/-4，`PlaybackOwnershipSourceTest.java` +51/-0）。

### 生产代码

| 文件 / 符号 | 检查项 | 结论 |
| --- | --- | --- |
| `PlaybackActivity.stopPlayback()` | 三条分支是否覆盖"退出必定停播"：服务在且本页归属 → `shutdown()`；服务在但本页非归属 → `suspend()`；服务不在 → 控制器兜底 `mController.stop()` | 是。旧实现 `mService != null && isOwner() ? shutdown() : mController != null ? mController.stop()` 的两个漏播窗口被堵住：控制器异步连接未就绪时整次停播为空操作；播放器 IDLE（取流中退出）时会话 stop 被忽略；且旧路径从不撤前台通知。`PlaybackService.suspend()`（`PlaybackService.java:267`）= `stopAndClear()`（停播 + `clearMediaItems()`）+ `removeForeground()`，正好对应"还有声音/通知栏仍显示播放中"两个症状 |
| `stopPlayback()` 与 `PlaybackServiceReleasePolicy` 的分层 | 非归属方停播是否与既有"非归属方离场不停播"契约（`PlaybackServiceReleasePolicy.decide` 的 `DETACH`/`RESET_SESSION` 及其测试 `PlaybackServiceReleasePolicyTest.keepsTheExistingReleaseMatrix`）冲突 | **不冲突，属两层语义**：`stopPlayback()` 只在**显式退出**路径被调用（返回键/系统返回/通知栏停止/退出首页），语义是"退出即停播"；`PlaybackServiceReleasePolicy` 管的是 `onDestroy → releasePlaybackService()` 的**释放**路径，本轮**未改动**该策略与其测试。旧实现经 `mController.stop()` 表达的同样是"显式退出停播"意图，新实现只是把它变得确定（且更强：清媒体项 + 撤通知），**非新增语义**。残留边界见「残留风险」 |
| `PlaybackActivity.startPlayer(...)`（六参重载） | 退出/结束/销毁后是否拒绝再起播；守卫位置是否早于既有分流（阅读器路由、DRM、parse/start） | 是。守卫在方法体首部，早于 `NovelRouter.isReaderUrl(result)` 路由与 `player().parse()/start()`；被拦的是自动换源、换线路、取流结果这类"迟到链路"，避免页面已退出仍在服务里起播并挂前台通知。非退出态正常起播不受影响（`isPlaybackExiting()` 仅由 `markPlaybackExiting()` 置位，调用点均为退出路径） |
| 两 flavor 覆盖 | 修复位于 `app/src/main`（两 flavor 共享基类），leanback/mobile 出口路径是否都经 `stopPlayback()` | 是。leanback `VideoActivity.finishVideoPlayback()`（`:8563`）与 mobile `onBackInvoked`/`finishVideoPlaybackFromSystemBack`（`:9849`、`:11345`）均为 `markPlaybackExiting() → stopPlayback()`；基类 `finishPlayback()`（`:141`）同样是 `markPlaybackExiting() → stopPlayback() → finish()` |
| 未触碰面 | 是否改动 native、依赖锁、`res/layout`、播放器链路、统计写入 | 否。净差异仅上述 2 文件；`PlaybackService`、`PlayerManager`、`PlaybackServiceReleasePolicy` 均未改动 |

### 测试代码

| 用例 | 检查项 | 结论 |
| --- | --- | --- |
| `exitingThePlayerPageAlwaysStopsThroughTheService` | 是否对**修复前**源码可证伪；断言是否稳定（CRLF 检出下不误报） | 可证伪：旧方法体不含 `mService.suspend();`，该断言在 base 必然变红。稳定：断言只用**单行**字面量与 `"\n    }"` 行首边界（CRLF 检出下 `\n    }` 仍成立），不含裸 `\n` 多行字面量（本机 CRLF 失败族正是后者所致） |
| `anExitingPlayerPageNeverStartsPlaybackAgain` | 是否锁定"退出后拒绝起播"且不误伤阅读器路由 | 可证伪：旧代码无该守卫，`guard.contains("isPlaybackExiting()")` 在 base 为假。断言取 `startPlayer` 方法体到 `NovelRouter.isReaderUrl(result)` 之间的片段，既锁定守卫存在，又要求阅读器路由仍是首个分流 |
| 是否削弱既有断言 | 测试文件改动是否只增不删 | 是。`--numstat` = `51 0`（0 删除），未删除或放宽任何既有断言 |

**生产代码无必修缺陷，本轮未改动生产代码。** 复评未发现需要修复的问题，故不进入"修复 → 验证"分支。

## 复评第 2 轮（阶段一验证通过后的复审）

| 复审项 | 结论 |
| --- | --- |
| 验证结果与代码语义是否一致 | 是。双 flavor 全量套件中 `PlaybackOwnershipSourceTest` **18/18 通过（failures=0）**，含两条新增用例；失败集与文档基线逐类一致 |
| 是否有新增失败被"环境性"掩盖 | 否。失败类集合与 C57 文档基线**完全一致**（mobile 6 类 / leanback 7 类），测试总数恰好 **+2**（= 本次新增 2 条契约测试） |
| 失败是否确为环境性 | 是。以 `SearchResultDownFocusTest:85` 为证：断言用裸 `\n` 多行字面量 `"@Override\n    public void onItemClick"`，而本机 `core.autocrlf=true` 检出为 CRLF ⇒ `indexOf` 必然失配；另一族 `MpvFontConfigTest`/`MpvHlsCacheCoordinatorTest` 依赖 Windows 原子重命名语义 |
| 合并/历史是否被破坏 | 否。阶段一提交单父 = `97c7ae68ab`；无 `reset`/`amend`/`rebase`；无冲突标记、无 unmerged 路径 |
| 是否引入无关改动 | 否。阶段一唯一改动 = 本任务文档；生产代码与测试代码 0 改动 |

## 复评第 3 轮（beta 前进后以新 tip 重新合入的复审）

验证/交付期间 `git fetch` 发现 beta 前进（`fb4fd7d99c` → `3148f030df`，新增 PR #434：dev1 主题焦点环族 11 个提交）。按既有规则以**新 tip 重新合入**后复审：

| 复审项 | 结论 |
| --- | --- |
| 新 beta 增量是否引入冲突 | 否。`git merge-tree --write-tree dev2 origin/beta` 无冲突；实际 `git merge --no-commit --no-ff origin/beta` 输出「Automatic merge went well」且**无任何 `Auto-merging` 行**；索引树 `c681b54c` == 自动合并结果树 |
| 新增量是否与本次改动相互干扰 | 否。增量**未触及** `PlaybackActivity.java`（本次唯一改动的生产文件）；且增量对 `TmdbDetailActivity.java`（+22/-12，纯主题焦点环）的改动**不含** `stopPlayback`/`finishPlayback`/`inlineStarted`/`isOwner`/`markPlaybackExiting`/`startPlayer` 任一符号 ⇒ 与"退出停播"语义**零交集** |
| 合并是否破坏 dev2 本地改动 | 否。dev2 两个改动文件 blob 与提交版本逐字节一致（`d29bd54fd3`、`138fb42c41`） |
| 增量自身的正确性 | 通过（本轮为**合并复核**，非重新评审）：增量已由 dev1 在其机位实测并经 beta 合入（随合并带入 `docs/C58-beta-merge-review-dev1-20261011.md`）；其中 `8ba33b11fc` 是对 `0209015d62` 的**完整 revert**（用户纠正"全屏播放画面不加焦点环"），合并后净效果与 beta 一致 |
| 是否改变正常行为 / 泄漏到无关模块 | 否。净差异仍只含 dev2 自身 3 路径；未触及 native、依赖锁、播放器链路、统计写入 |
| 文档与实现是否一致 | 是。本轮新合入的 beta 文档（`docs/C58-beta-merge-review-dev1-20261011.md`、主题族文档更新）随合并带入，无需改动 |

## 验证

### 4.1 双 flavor 编译 + AndroidTest 编译 + 双 flavor 全量 JVM 套件

单次 Gradle 调用（`--continue` 保证失败任务不阻断其余任务），6 个目标任务：

```bash
cmd /c gradlew.bat --no-daemon --continue \
  :app:testLeanbackArm64_v8aDebugUnitTest :app:testMobileArm64_v8aDebugUnitTest \
  :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac \
  :app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac :app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac
```

4 个编译目标任务 **BUILD SUCCESSFUL**；两个测试任务因**既有环境失败**报 FAILED。

| 阶段 | flavor | tests | failures | errors | skipped | 失败类 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 阶段一（合并前） | leanback | 4150 | 8 | 0 | 3 | 7 类 | 与 C57 文档基线同类同数 |
| 阶段一（合并前） | mobile | 4985 | 7 | 0 | 3 | 6 类 | 与 C57 文档基线同类同数 |
| **阶段二（合并后，权威）** | leanback | **4174** | **8** | 0 | 3 | **7 类** | **同类同数（0 新增失败）**；+24 例来自 beta 增量 |
| **阶段二（合并后，权威）** | mobile | **4989** | **7** | 0 | 3 | **6 类** | **同类同数（0 新增失败）**；+4 例来自 beta 增量 |

阶段二失败类集合（逐类核对，与基线同族）：

- mobile（6 类 / 7 例）：`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`、`PlayerPlaybackRegressionSourceTest`、`ReaderPlaybackRoutingSourceTest`×2、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`
- leanback（7 类 / 8 例）：`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`、`NativeEnhancedPlaybackStyleFocusTest`、`ReaderPlaybackRoutingSourceTest`×2、`SearchResultDownFocusTest`、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`

环境性根因（本轮取证，合并后仍成立）：

- **CRLF 检出 + 裸 `\n` 断言**：`SearchResultDownFocusTest.java:85` 的 `"@Override\n    public void onItemClick"`；beta 增量改写后的 `NativeEnhancedPlaybackStyleFocusTest.java:213` 同样使用裸 `\n` 多行字面量 `"applyTmdbRowFocusChain();\n        updateFocus();"` ⇒ 本机 CRLF 检出下必然失配（该用例失败行号由 179 变为 213，属**同一环境性失败**，非新增）。
- **Windows `renameTo` 语义**：`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`。

该族已在 `docs/C51-beta-merge-review-dev2-20261009.md`、`docs/C57-beta-merge-review-dev2-20261010.md` 多次记载，四个 dev 工作区一致。

### 4.2 定向用例（本次改动面）

| 用例类 | 阶段一 | 阶段二（合并后） |
| --- | --- | --- |
| `PlaybackOwnershipSourceTest`（mobile flavor） | **18/18**，failures=0 | **18/18**，failures=0 |
| `PlaybackOwnershipSourceTest`（leanback flavor） | **18/18**，failures=0 | **18/18**，failures=0 |
| `exitingThePlayerPageAlwaysStopsThroughTheService` | PASSED | **PASSED** |
| `anExitingPlayerPageNeverStartsPlaybackAgain` | PASSED | **PASSED** |

（取自 `app/build/test-results/**/TEST-com.fongmi.android.tv.ui.activity.PlaybackOwnershipSourceTest.xml`。）

### 4.3 静态与结构校验

- `git diff --check` / `git diff --cached --check` 退出码 **0**；
- `git grep -nE '^(<<<<<<<|>>>>>>>)' -- app/src docs` 命中 **0**；`git diff --name-only --diff-filter=U` 为空；
- 合并状态未被破坏：`MERGE_HEAD` 全程为 beta tip `3148f030df`，**未使用任何 `git reset`/`--amend`**（避免退化成单父普通提交而把 beta 侧提交当成本分支改动）；
- 零复活三层证据通过：47 条 beta 真移除路径在合并结果树命中 **0**、66 条与净差异交集 **0**、关键词命中 **0**、`--diff-filter=D` **空**；
- 推送安全性：阶段一 `git push --dry-run origin dev2` = `34a93e243f..c27f34b950`（快进）。

### 4.4 回滚

- **阶段二**：合并提交是 merge commit（第二父 = `3148f030df`），`git revert -m 1 <merge-commit>` 即回到 `c27f34b950`；
- **阶段一**：`git revert c27f34b950`（或删除本文件）即回到 `97c7ae68ab`；
- 本轮**无生产代码改动**（阶段一仅新增文档，阶段二仅合并 + 文档更新），回滚无运行时影响；无数据迁移、无 ABI/native 变更、无依赖变更、无播放行为变更。

## 残留风险（按 `AGENTS.md` §2 记录，不在本轮范围内展开）

1. **显式退出时非归属方的停播强度提升**：`stopPlayback()` 的非归属分支由旧的"经控制器停会话（控制器未连上时为空操作）"变为"经服务确定停播（`suspend()`：停播 + 清媒体项 + 撤前台通知）"。在"本页非归属方且服务正被另一会话使用"的极端组合下，新实现比旧实现更可能停掉那次会话；但旧实现在控制器已连接时同样会停掉它，且非归属分支的触发前提是本页正在**显式退出**，故判定为**可接受的语义强化**而非回归。`onDestroy` 释放路径仍由 `PlaybackServiceReleasePolicy` 保护（非归属方 `DETACH`/`RESET_SESSION`）。
2. **`suspend()` 无 `running` 守卫**（`shutdown()` 有 `if (!running) return;`）：经核查 `mService != null` 蕴含绑定仍在 ⇒ 服务未销毁 ⇒ 播放器未 `release()`（`player.release()` 只在 `PlaybackService.onDestroy()`），故 `suspend()` 不会作用在已释放的播放器上；`onServiceDisconnected` 会把 `mService` 置空，其后走控制器兜底分支。判定为无实际风险，未做改动。

## 改动清单（C58 相对本轮起点）

| 路径 | 类型 | 说明 |
| --- | --- | --- |
| `docs/C58-beta-merge-review-dev2-20261011.md` | 新增 | 本任务文档（阶段一提交） |
| （阶段二合并提交） | 合并 | 第二父 = `3148f030df`；带入 beta 增量 11 个提交与本文档的阶段二更新 |

（本次 PR 另含已提交未推送的 `97c7ae68ab`：`PlaybackActivity.java` +24/-4、`PlaybackOwnershipSourceTest.java` +51/-0。）

## 交付坐标

| 项 | 值 |
| --- | --- |
| 任务起始 HEAD | `97c7ae68abd9be76d2bdd1d79e34d93a9f6925ae` |
| 阶段一 `origin/beta` tip | `fb4fd7d99ca1547ea624f3d5f1b7f3cd81b880a5` |
| **提交时 `origin/beta` tip（权威）** | `3148f030df9784be7b14fc924364c304980ff9ab` |
| 阶段一提交（文档） | `c27f34b9506875873ebafcceab9f6785f8510f3d`（单父 = `97c7ae68ab`） |
| 阶段一 recovery tag | `recovery/C58-beta-merge-review-dev2/20261011034817-c27f34b95068` |
| **阶段二合并提交** | `8f20fab65aa2986b984b7848d948f102005bfb38`（两个父：`c27f34b950` + `3148f030df`） |
| 阶段二合并结果树 | `c681b54ccfcb24a30043a80df24933736735dce6`（== `git merge-tree --write-tree` 自动合并结果树） |
| 阶段二 recovery tag | `recovery/C58-beta-merge-dev2-beta434/20261011035739-8f20fab65aa2` |
| 坐标补记提交 | 见「闭环记录」 |
| 推送 | `git push origin dev2`（快进）+ 两个 tag 推送 |
| PR | `dev2 -> beta`，中文正文，**只创建未合并** |

## 闭环记录

- 2026-10-11 11:48（Asia/Shanghai）：阶段一。`git fetch` 后确认 `origin/beta` tip `fb4fd7d99c` 即 `dev2` HEAD 的父提交 ⇒ **无需合并**；2 轮复评未发现必修缺陷，未改生产代码；`task_guard.sh finish` 生成文档提交 `c27f34b9506875873ebafcceab9f6785f8510f3d` + recovery tag `recovery/C58-beta-merge-review-dev2/20261011034817-c27f34b95068`；`git push origin dev2` = `34a93e243f..c27f34b950`（快进）+ tag 推送成功。
- 2026-10-11 12:0x：**推送后发现 beta 前进**（`git ls-remote` = `3148f030df`，PR #434 dev1 主题族 11 提交）⇒ 以新 tip 重新合入：`git merge --no-commit --no-ff origin/beta`（0 冲突，`MERGE_HEAD` = `3148f030df`），索引树 `c681b54c` == `git merge-tree --write-tree` 自动结果树；复评第 3 轮通过（增量与本次改动零交集、零复活、dev2 内容逐字节未变）。
- 2026-10-11 12:4x：阶段二重新验证：4 个编译目标任务 BUILD SUCCESSFUL；leanback **4174 / 8 失败 / 7 类**、mobile **4989 / 7 失败 / 6 类**，失败类集合与文档基线同族（0 新增失败）；`PlaybackOwnershipSourceTest` 双 flavor **18/18**，两条新增用例 PASSED。
- 2026-10-11 11:57（Asia/Shanghai）：阶段二合并提交 `8f20fab65aa2986b984b7848d948f102005bfb38` + recovery tag `recovery/C58-beta-merge-dev2-beta434/20261011035739-8f20fab65aa2`；`git rev-list --parents -n1 8f20fab65a` 校验为**两个父**（`c27f34b950` + `3148f030df`）且第二父 == 提交时 beta tip，`MERGE_HEAD` 已清空，工作区干净。
- 2026-10-11 11:57：`git ls-remote origin refs/heads/beta` 复核 beta 未再前进（`3148f030df`，本合并已含该 tip）。
- 2026-10-11 11:58：坐标补记提交（本文件更新）；`git push origin dev2` + 两个 recovery tag 推送。
- 2026-10-11 11:59：`gh pr create --base beta --head dev2` 创建 PR（**未合并**，`mergedAt=null`）；PR 文件集校验：与本地 `git diff --name-only origin/beta HEAD` 逐项一致。
