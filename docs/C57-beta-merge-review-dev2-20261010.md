# C57：dev2 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入 `dev2`（**远端已移除/回退的提交不得顺带带回**）；复评 dev2 全部已修改代码（含**已提交未推送**的 6 个提交，即 PR #426 合并后新增的「加载圈永久残留」修复族与屏显诊断重缓冲分派修复）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev2`、创建 `dev2 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① `dev2` 必须包含合并时刻的 `origin/beta` tip；② 远端被移除/回退内容**零复活**、beta 增量**零丢失**、dev2 既有改动**零丢失**；③ 双 flavor Java 编译 + 双 flavor AndroidTest Java 编译通过；④ 双 flavor 全量 JVM 套件失败集与未触碰基线**逐类一致**（无新增失败）；⑤ 合并结果树 == git 自动合并结果树；⑥ 复评发现的问题已修复并锁定（或按 `AGENTS.md` §2 明确记录处置）；⑦ 提交 + recovery tag、`dev2` 已推送、PR 已创建且**未合并**，PR 文件集与 `git diff --name-only origin/beta HEAD` 逐项一致。
- **当前状态**：合并完成（0 冲突，`MERGE_HEAD` = 新 beta tip `819003db8f`）；2 轮复评完成；第 1 轮复评 6 个已提交未推送提交未发现必修缺陷（生产代码语义正确、定向用例全绿）；**验证期间 beta 前进**（`e6413efd1e` → `819003db8f`），按 Skill 规则中止旧未提交合并并以新 tip **重新合入、复评、验证**；双 flavor 编译、双 flavor 全量 JVM 套件、零复活三层证据（含检测器自检）全部通过。
- **交付坐标**：见文末「交付坐标」与「闭环记录」。
- **下一动作**：无（本任务已闭环）。

## 时间与设备

- 任务开始时本地时间：2026-10-10 17:04（Asia/Shanghai）；记录时 19:40。
- 任务开始时工作区**非干净**：HEAD 已被一次 `git reset` 回到 `b6b8e7a375`，但工作区/索引仍是「dev2 + origin/beta(`e6413efd1e`)」的自动合并结果（未提交、无 `MERGE_HEAD`）。**关键取证**：用临时索引把整个工作区写成树得到 `3c17ae59714b8b6a659d2cb2d169f850a080baa2`，与 `git merge-tree --write-tree b6b8e7a375 e6413efd1e` 的自动合并结果树**逐字节相同** ⇒ 工作区就是那次合并的完整结果，只是丢了合并状态。因此本任务把它作为合并结果正式化，而不是重新从零合并。
- `dev2` HEAD = `b6b8e7a375276975cc160ceeb6257cf058298ebb`，领先 `origin/dev2`（`7543959f3e`）**7 个提交**（其中 6 个为本轮复评对象）：
  - `4319403502a64ad8064781f4e88f21391e6abc77` `fix(tv): 详情未就绪丢弃播放结果时释放加载守卫，消除永久转圈`
  - `7809ff4952788142008d0953697bfe29ded062b3` `fix(detail): 作废内联取址请求时释放其加载标记，消除永久加载圈`
  - `d7b9439201cef70c9229b98c7356e0a7397103eb` `fix(playback): 被作废的取流请求必须释放加载态，消除永久转圈与同集无法再播`
  - `f2ccce36c9aba490f40fb3800638a14749dd0090` `fix(osd): 屏显诊断重缓冲计数按引擎正确分派，IJK 不再恒为 0`
  - `839809ee1e` / `b6b8e7a375` 两份文档提交（扫荡收口 + 5557 实机证据）
- 设备：**本轮未使用真机/模拟器**。理由（风险相符原则，`AGENTS.md` §4）：本轮**未改一行生产代码**（`git diff` 证明：相对 dev2 基线只发生 beta 侧内容合入，dev2 自身 11 个路径 blob 与提交版本逐字节一致）；被复评的加载圈族已在 `192.168.50.3:5557`（dev2 机位）按时序实测（见 `docs/LOADING-SPINNER-OMISSION-SWEEP-20261009.md` 第 4 节）；新合入的 beta 增量（小说朗读/去广告总时长/搜索下拉对比度/站点注入整页/mobile 电池块）均由对应 dev 分支在其机位实测后经 beta 合入。故未申请机位、未重装 APK。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2`（worktree `F:/Workspace/webtv2/webhtv`） |
| 任务开始时 HEAD | `b6b8e7a375276975cc160ceeb6257cf058298ebb` |
| `origin/dev2` | `7543959f3e0a388b13eff0cc608ecbf96baa13ba` |
| 任务开始时 `origin/beta` tip | `e6413efd1e4bdb3c1725824db6f7b3f8a7dc249c`（Merge pull request #431 from Silent1566/dev1） |
| **提交前再次 fetch 后的 `origin/beta` tip** | `819003db8fef2ea63420e9a72eb4fcac8ee468ba`（Merge pull request #432 from Silent1566/dev4） |
| 合并基点（merge-base） | `29d52ab2385702a9350c3099cd94dd4e54c1240f`（= beta `e6413efd1e` 的第二父所在链上的 dev2 交付点） |
| 第 1 次合并（旧 tip） | 因工作区已是该合并结果而 `git merge` 拒绝覆盖 → 把工作区结果 `git add -A` 得到树 `3c17ae59`，写 `.git/MERGE_HEAD = e6413efd1e` 正式化合并状态 |
| **重新合并（新 tip，权威）** | `git merge --no-commit --no-ff origin/beta` → **「Automatic merge went well; stopped before committing as requested」**（`EXIT=0`，0 冲突），仅 `Auto-merging app/src/testMobile/.../VideoActivityLayoutTest.java` |
| `MERGE_HEAD` | `819003db8fef2ea63420e9a72eb4fcac8ee468ba`（= 新 beta tip，即合并提交第二父） |
| 合并结果树 `git write-tree` | `cd5c370ce1df9be7e033d25420a1893203a33a98` |
| git 自动合并结果树（`merge-tree --write-tree`） | `cd5c370ce1df9be7e033d25420a1893203a33a98`（**与索引树逐字节相同**） |
| 合并前 HEAD 树 | `f0a56b509e6750b2f844d53dbc0adb83e77bb820` |
| beta tip 树 | `b6d1c45bc389cdcf702c0cd38f09143f1de85907` |
| 是否需要新合并提交 | **需要**：`git merge-base --is-ancestor origin/beta HEAD` 为**假**（`819003db8f` 不在 dev2 上），故由 `task_guard.sh finish` 生成真实合并提交，第二父 = beta tip |
| 初始脏路径 | 无「额外」脏路径：任务开始时全部脏路径（56 个）都是该合并结果的组成部分，已全部纳入 `--adopt-dirty`/`app`+`docs` 范围 |
| 回滚锚点 | `b6b8e7a375276975cc160ceeb6257cf058298ebb` |

### 合并增量 ledger（beta 侧自 merge-base 共 33 个提交，全部纳入）

`git log --oneline 29d52ab238..origin/beta`（提交时权威 tip）：

| 组 | 代表提交（完整 ID） | 内容 | 处置 |
| --- | --- | --- | --- |
| PR #427 小说朗读 | `8539a450f2`、`1ebec24a65`、`32202da2de`、`86e1bfe3ce`、`e44dadffd6`、`fe1f725a12` | 补全小说模式朗读（系统/百度/Edge/自定义在线引擎 + 后台通知控制）、音调接入 Edge SSML | 纳入（合并第二父） |
| PR #428 去广告总时长 | `72cdec02b2`、`926b9d50ca`、`6bb9203722`、`35a5a63f8a` | 去广告成功提示补充总广告时长，三条 HLS 通道统一文案；跨单位进位与粒度边界修正 | 纳入 |
| PR #429 搜索下拉对比度 + 缓存临时文件 | `f9f47c24ae`、`f0da126a2c`、`42b03f028e`、`92f1844342`、`814935ceea`、`27fe5c6927`、`7e51768d88` | 深色模式搜索分组下拉面板对比度、长按一键全清、缓存临时文件显式清理修复 | 纳入 |
| PR #430 站点注入整页 | `22348293b8`、`40ba21ed87`、`67d930ed63`、`841a5707d5` | 站点注入竖屏/横屏铺满整页 | 纳入 |
| PR #431 dev1 交付 | `fe4d02e076`、`913ae81d19`、`e6413efd1e` | dev1 合并评审交付（含 C56 复评 CSP 修复） | 纳入 |
| PR #432 dev4 交付 | `8db36f33a6`、`c442601fbd`、`c8764048c5`、`819003db8f` | mobile 原生播放器控制栏新增电池电量图标 + 当前时间（复用 BatteryUtil / Formatters.TIME） | 纳入 |

**beta 侧增量零丢失判据**：合并结果树 `cd5c370c` 与 `git merge-tree --write-tree b6b8e7a375 819003db8f` 的自动合并结果树逐字节相同；且 `git diff --name-status origin/beta HEAD` 只含 dev2 自身 11 个路径（见下节），无一条 beta 路径被覆盖或删除。

### 合并结果净差异（合并后相对 beta tip）

`git diff --name-status origin/beta HEAD` = **11 路径**（6 修改 + 5 新增），全部来自 dev2 自身（加载圈修复族），无一条来自 beta 之外：

| 路径 | 类型 | 归属 |
| --- | --- | --- |
| `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` | 修改 | `4319403502` + `d7b9439201` |
| `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java` | 修改 | `7809ff4952` + `d7b9439201` |
| `app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java` | 修改 | `f2ccce36c9` |
| `app/src/test/java/com/fongmi/android/tv/setting/PlayerDisplaySettingSyncTest.java` | 修改 | `f2ccce36c9` |
| `app/src/test/java/com/fongmi/android/tv/ui/activity/TmdbDetailDirectPlayTransitionSourceTest.java` | 修改 | `7809ff4952` + `d7b9439201` |
| `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityLayoutTest.java` | 修改 | `4319403502` + `d7b9439201`（与 beta PR#432 的同文件改动为**不相交 hunk**，自动合并） |
| `docs/ABANDONED-REQUEST-LOADING-RELEASE-20261009.md` | 新增 | `d7b9439201` |
| `docs/INLINE-LOADING-RELEASE-20261009.md` | 新增 | `7809ff4952` |
| `docs/LOADING-SPINNER-OMISSION-SWEEP-20261009.md` | 新增 | `839809ee1e` + `b6b8e7a375` |
| `docs/OSD-REBUFFER-ENGINE-BRANCH-20261009.md` | 新增 | `f2ccce36c9` |
| `docs/PLAYBACK-LOADING-GUARD-20261009.md` | 新增 | `4319403502` |

`git diff --diff-filter=D --name-status origin/beta HEAD` = **空**（无删除，即无 beta 文件被本分支删除/覆盖）。

### 零复活三层证据（严格判据 + 检测器自检）

| 层 | 判据 | 结果 |
| --- | --- | --- |
| L1 结构性 | ① 合并结果树 == `git merge-tree --write-tree` 自动合并结果树（`cd5c370c`）；② `git diff --name-status origin/beta HEAD` 只含 dev2 自身 11 路径；③ `git diff --diff-filter=D origin/beta HEAD` 为空；④ dev2 的 11 个路径 blob 与 `b6b8e7a375` 提交版本**逐字节一致**（合并未改写本地内容） | 全部通过 |
| L2 行级（含检测器自检） | 取**主题行锚定**的 revert 提交集（`git log --all --format='%H%x09%s' \| awk -F'\t' '$2 ~ /^(Revert\|revert\|回退\|撤销\|剔除)/'`，共 **182** 个，规避了未锚定 grep 会误匹配提交正文的假阳性）；对每个提交取 `git diff --name-status P C` 的**删除路径**，共 **66** 条；其中 **47** 条已不在 beta 树（=真被移除）。**自检**：把一条真移除路径（`app/src/armeabi_v7a/assets/go_proxy_video`）注入伪造树，检测器**报出**该路径 ⇒ 检测器非空转。**正式扫描**：47 条真移除路径在合并结果树与磁盘上的命中数均为 **0** | **严格复活数 = 0** |
| L3 关键移除目标 | 对 beta 已移除能力关键词在净差异中逐一 `grep -c`：`Fingerprint`/`fingerprint`/`SpeechRecognizer`/`VoiceAd`/`AdAudioDetect`/`SiteAdMarker`/`ThemeController.apply` | 全部为 **0** 命中 |

补充：dev2 自 merge-base 仅改动 11 个路径（加载圈族），与 beta 自 merge-base 改动的 56+ 路径**零交集**（`comm -12` 为空）⇒ 本次合并内容不相交，无「同文件两侧改写」的手工调和面（唯一同文件路径 `testMobile/VideoActivityLayoutTest.java` 的两个改动落在不相交 hunk：beta 在 157 行、dev2 在 1324 行）。

结论：**零复活、零丢失**。dev2 的改动面是「加载态守卫/加载标记释放 + 屏显诊断重缓冲取数」，与 beta 上被移除/回退的音频指纹、语音广告识别、动态主题、YouTube 推送路由等能力**不相交**。

## 复评第 1 轮（对象：净差异 11 路径 + 6 个已提交未推送提交）

### 生产代码

| 文件 / 符号 | 检查项 | 结论 |
| --- | --- | --- |
| `VideoActivity.setPlayer(Result)`（leanback） | `result == null` 分支（`SiteViewModel.cancelPlayerContent` 写 null）与 `!canApplyPlayerResult()` 分支是否都释放 `mPlaybackRequestActive` / `mPlaybackPlayerStarted` | 是。两条早退分支都显式释放并 `return`，注释说明「不会再有结果回调收圈，守卫留下等于圈无清除路径」；`onStateChanged(READY)` 的 `break` 与 `hidePlaybackProgressIfStale()` 依赖同一守卫，释放后两条清除路径恢复可达 |
| `TmdbDetailActivity.releaseInlinePlaybackPending()` / `(int generation)` | 代际归属判定是否防止旧回调清掉新请求的标记 | 是。`inlinePlaybackPendingGeneration` 记录置位代际，回调失效分支按归属释放（`if (inlinePlaybackPendingGeneration != generation) return;`），两处显式作废点用无条件重载；任务取消时回调不执行，故同步释放与回调释放互补 |
| `PlayerOsdController.getDiagnostics()` 重缓冲取数 | 分派条件是否为「是否 Exo」而非「是否 MPV」 | 是。`player.isExo() ? snapshot.rebufferCount() : player.getRebufferCount()`；`PlayerManager.getRebufferCount()/getRebufferTotalMs()` 为引擎无关追踪器（`PlaybackBufferingTracker` + MPV disc 计数），IJK/MPV 均可读，修复了 IJK 恒读 `Snapshot.empty()` 得 0 的读数错误 |
| 合并交互 | `testMobile/VideoActivityLayoutTest.java` 同文件两侧改动是否冲突 | 否。beta PR#432 的电池用例（157 行）与 dev2 的守卫断言（1324 行）落在不相交 hunk；合并后文件 blob `feacb09a9a` 与 git 自动合并结果一致，两侧用例并存 |

**生产代码无必修缺陷，本轮未改动生产代码。**

### 测试代码

6 个已提交未推送提交新增/扩展的断言（`the null-result branch must exist`、`the drop-before-detail-ready early return must exist`、代际归属逐方法体校验、`playerOsdDiagnosticsReadRebufferCountPerEngine`）在合并后树中**全部通过**（见「验证」节计数），且对修复前源码可证伪（原提交信息已记录逐方法体字符串校验在 base 必然变红）。无必修问题。

## 复评第 2 轮（beta 前进后重新合入的复审）

验证期间 `git fetch` 发现 beta 前进（`e6413efd1e` → `819003db8f`，新增 PR #432：mobile 电池块）。按 Skill 规则**中止旧未提交合并并以新 tip 重新合入**，随后复审：

| 复审项 | 结论 |
| --- | --- |
| 新 beta 增量是否引入冲突 | 否。`git merge-tree --write-tree b6b8e7a375 819003db8f` 无冲突，仅 `Auto-merging testMobile/VideoActivityLayoutTest.java`（不相交 hunk）；重新合并后索引树 `cd5c370c` == 自动合并结果树 |
| 新 beta 增量代码质量 | 通过。`mobile/VideoActivity.updateBatteryInfo()` 复用 `BatteryUtil.getLevel/getIcon` 与 `Formatters.TIME`（不另造映射）；可见性只由 `isFullscreen() && !isLock() && mHistory != null && !player().isEmpty()` 决定、不跟 `PlayerButtonSetting`；`level < 0` 整块隐藏；`onTimeChanged` 仅在控制栏可见时刷新。`view_control_vod.xml` 新增 `@+id/batteryInfo/battery/batteryTime` 且不重声明 `@id/time`（`view_widget_vod` 已占用）；`BatteryUtil`/`Formatters.TIME`/`isVisible(View)`（`BaseActivity:93`）均已存在 |
| 合并是否破坏 dev2 本地改动 | 否。dev2 的 11 个路径 blob 与 `b6b8e7a375` 提交版本逐字节一致 |
| 是否改变正常行为 / 泄漏到无关模块 | 否。净差异仍仅 dev2 自身 11 路径；未触及 native、依赖锁、播放器链路、统计写入 |
| 文档与实现是否一致 | 是。本轮新合入的 beta 文档（`docs/C57-beta-merge-review-dev4-20261010.md` 等）随合并带入，无需改动 |

## 验证

### 4.1 双 flavor 编译 + AndroidTest 编译 + 双 flavor 全量 JVM 套件

单次 Gradle 调用（避免重复检查），6 个目标任务：

```bash
cmd //c gradlew.bat --no-daemon \
  :app:testLeanbackArm64_v8aDebugUnitTest :app:testMobileArm64_v8aDebugUnitTest \
  :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac \
  :app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac :app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac
```

4 个编译目标任务 `BUILD SUCCESSFUL`；两个测试任务因**既有环境失败**报 FAILED（见下表）。

| flavor | tests | failures | errors | skipped | 未触碰基线（`29d52ab238`） | 差值 |
| --- | --- | --- | --- | --- | --- | --- |
| leanback | **4148** | **8** | 0 | 3 | 8 失败 / 7 类 | **同类同数（0 新增失败）** |
| mobile | **4983** | **7** | 0 | 3 | 7 失败 / 6 类 | **同类同数（0 新增失败）**；+50 例为 beta 新增测试，全部通过 |

**基线对照取证（决定性）**：在**未触碰的 merge-base 提交 `29d52ab238`** 上新建临时 worktree 跑同一套件，得到**同样的失败类集合与数量**（mobile 7 失败 / 6 类：`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`、`PlayerPlaybackRegressionSourceTest`、`ReaderPlaybackRoutingSourceTest`×2、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`；leanback 8 失败 / 7 类）。这些失败是**既有环境问题**：

- **CRLF 检出 + 裸 `\n` 断言**：`ReaderPlaybackRoutingSourceTest`、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`、`PlayerPlaybackRegressionSourceTest`、`NativeEnhancedPlaybackStyleFocusTest`、`SearchResultDownFocusTest` 的断言含裸 `\n` 字面量，而本机 `core.autocrlf=true` 使源文件为 CRLF（如 `PlaybackActivity.java` CRLF 1275 处、裸 LF 0 处；`reader.html` CRLF 2904 处、裸 LF 0 处）⇒ 必然失配。该问题在 `docs/C51-beta-merge-review-dev2-20261009.md` 与加载圈族文档中已多次记载，四个 dev 工作区一致。
- **Windows `renameTo` 语义**：`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest` 依赖原子重命名，在本机文件系统下随机表现（同一类内每次失败的具体用例不同，但类集合与数量稳定）。

两个 flaky 类的用例名在两次运行间发生漂移（base 与合并后各有一次不同用例冒头），进一步证明其环境性质而非本改动因果。

### 4.2 定向用例（加载圈族与 OSD）

| 用例类 | 结果 |
| --- | --- |
| `VideoActivityLayoutTest`（mobile） | **155/155**（含 beta 新增电池用例 + dev2 守卫断言），failures=0 |
| `TmdbDetailDirectPlayTransitionSourceTest` | **4/4**，failures=0 |
| `PlayerDisplaySettingSyncTest` | **7/7**，failures=0 |
| `PlaybackOwnershipSourceTest` | **16/16**，failures=0 |
| `mobileVodControlOverlayShowsBatteryIconLikeFusionMode`（beta 新增） | 通过 |

### 4.3 静态与结构校验

- `git diff --check` 与 `git diff --cached --check` 退出码 **0**；
- `git grep -nE '^(<<<<<<<|>>>>>>>)' -- app/src docs` 命中 **0**；`git diff --name-only --diff-filter=U` 为空；
- 合并状态未被破坏：`MERGE_HEAD` 全程为 beta tip `819003db8f`，**未使用任何 `git reset`/`--amend`**（避免退化成单父普通提交而把 beta 侧提交当成本分支改动）；
- 改动未触及 `res/layout`、native、依赖锁文件、播放器链路、统计写入；
- 零复活三层证据（含检测器自检）通过：严格复活数 **0**、`--diff-filter=D` **空**、47 条 beta 真移除路径命中 **0**。

### 4.4 构建资源回收

构建后已执行 `cmd //c gradlew.bat --no-daemon clean`（`BUILD SUCCESSFUL`），并 `git worktree remove`/`prune` 清理两处临时基线 worktree。

### 4.5 回滚

- 本轮合并提交是 merge commit（第二父 = `819003db8f`）：`git revert -m 1 <merge-commit>` 即回到 `b6b8e7a375`；
- 本轮**无生产代码改动**（仅合并 + 新增文档），回滚无运行时影响；
- 无数据迁移、无 ABI/native 变更、无依赖变更、无播放行为变更。

## 改动清单（C57 相对本次合并树）

| 路径 | 类型 | 说明 |
| --- | --- | --- |
| `docs/C57-beta-merge-review-dev2-20261010.md` | 新增 | 本任务文档 |

（合并提交本身另含 beta 带入的 33 个提交内容与 dev2 既有 11 个路径，见「合并增量 ledger」与「合并结果净差异」。）

## 交付坐标

| 项 | 值 |
| --- | --- |
| 任务起始 HEAD | `b6b8e7a375276975cc160ceeb6257cf058298ebb` |
| 合并前 `origin/beta` tip（旧） | `e6413efd1e4bdb3c1725824db6f7b3f8a7dc249c` |
| **提交时 `origin/beta` tip（权威）** | `819003db8fef2ea63420e9a72eb4fcac8ee468ba` |
| 合并提交（本任务唯一提交） | `df7f7ff5808d1e555abca8a5aa31104624650ffd`（两个父：`b6b8e7a375` + `819003db8f`） |
| 合并结果树 | `cd5c370ce1df9be7e033d25420a1893203a33a98`（== `git merge-tree --write-tree` 自动合并结果树） |
| recovery tag | `recovery/C57-beta-merge-review-dev2-20261009/20261010114155-df7f7ff5808d` |
| 推送 | `git push origin dev2` + `git push origin <tag>` |
| PR | `dev2 -> beta`，中文正文，**只创建未合并**（见「闭环记录」） |

## 闭环记录

- 2026-10-10 19:41（Asia/Shanghai）：`task_guard.sh finish` 生成合并提交 `df7f7ff5808d1e555abca8a5aa31104624650ffd` + recovery tag `recovery/C57-beta-merge-review-dev2-20261009/20261010114155-df7f7ff5808d`；`git rev-list --parents -n1 df7f7ff580` 校验为**两个父**（`b6b8e7a375` + `819003db8f`）且第二父 == 提交时 beta tip（`819003db8f`），`MERGE_HEAD` 已清空，工作区干净。
- `git ls-remote origin refs/heads/beta` 在提交前后均为 `819003db8f`（beta 未再前进，本合并已含该 tip）。
- `git push origin dev2` 与 tag 推送成功。
- `gh pr create --base beta --head dev2` 创建 PR（**未合并**，`mergedAt=null`）。
- PR 文件集校验：PR files 与本地 `git diff --name-only origin/beta HEAD` 逐项一致。
