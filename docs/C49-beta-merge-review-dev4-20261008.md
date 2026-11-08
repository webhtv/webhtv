# C49：dev4 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入 `dev4`（**远端已移除/回退的提交不得顺带带回**）；复评 dev4 全部已修改代码（含已提交未推送的 `23e5280f37d` / `1a4110178fc`）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev4`、创建 `dev4 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并提交第二父为最终 `origin/beta` tip `7df98107bef`；② 远端被回退内容**零复活**（含可证伪自检）；③ beta 增量 **31 路径零丢失、零内容漂移**；④ dev4 既有 6 路径**零丢失、零改写**；⑤ 双 flavor Java 编译 + 双 flavor AndroidTest Java 编译通过；⑥ 双 flavor 全量 JVM 套件零失败；⑦ UI token 门禁相对基线零新增违规；⑧ 净差异只含本分支自身改动与任务文档；⑨ 提交 + recovery tag；⑩ `dev4` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并完成（0 冲突，合并树与 `git merge-tree` 预测逐字节一致）；5 轮评审完成——第 1/2 轮各修复 1 处 C47 文档事实错误，第 3 轮定位并消除 2 项**验证环境**根因（工作区行尾、Gradle JVM 选择），第 4 轮复评全部契约断言通过，第 5 轮评审新合入的 PR #424（实验室配置源弹窗）无必修缺陷；全部验证通过。
- **交付坐标**：提交与 recovery tag 由 `task_guard.sh finish` 生成，并记录在 PR 描述中（见文末）。
- **下一动作**：无（任务已收口；PR 由用户决定是否合并）。

## 时间与设备

- 开始时本地时间：2026-10-08 22:10（Asia/Shanghai）；收口时间 2026-10-09 10:4x（Asia/Shanghai，期间机器曾休眠、Java 运行时被自动更新，且 `origin/beta` 两次前进）。
- 设备：本次**未使用模拟器/实机**（零生产代码改动，无新增运行时行为；C47 的设备证据见 `docs/C47-seek-loading-progress.md`，beta 增量的设备证据见 `docs/C47-beta-merge-review-dev2-20261008.md`、`docs/C49-beta-merge-review-dev2-20261008.md`、`docs/site-injection-search-review.md`）。未占用任何机位。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev4` |
| 任务开始时 HEAD | `1a4110178fccddc656122e08fc8d3a056ae5e2f2`（`docs(c47): 记录模拟器验收证据与精度边界`，领先 `origin/dev4` 24 个提交，其中 2 个提交不在 beta） |
| 合并基点（merge-base） | `24265785a33d09ca1d1d3d36370d010ff1e89eac`（Merge PR #419 from dev3） |
| 首次合并的 beta tip | `4174c65ea3dd30c9ee9ebb55db64839970d85a29`（Merge PR #422 from dev1） |
| 第二次合并的 beta tip | `765dce2e2a4e12d66c30bf07eaaa0d3a92a5f899`（Merge PR #423 from dev2） |
| **最终** `origin/beta` tip | `7df98107befd6bdd49f7aae5c7fc9c1efeb19b34`（Merge PR #424 from dev1） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`（保留合并提交由 `task_guard.sh finish` 创建） |
| 合并结果 | **0 冲突、0 冲突标记**，31 路径合入 |
| 合并预测一致性 | 三次合并的 `git merge-tree --write-tree` 预测树均与真实合并树**逐字节相同**（`db133af56` / `f48e2206f` / 终轮 `df59dec76`） |
| 终轮合并树 | `df59dec7687b81ff14b483fe0e130201895c1be6` |
| 初始脏路径 | 无（任务开始时 `git status` 干净） |
| 回滚锚点 | `1a4110178fccddc656122e08fc8d3a056ae5e2f2` |
| 任务守卫 | `C49-beta-merge-review-dev4`（standard，scope `app` + `docs` + `scripts` + `.codex/scripts` + `.codex/task-state`） |

### beta 中途推进的处置（遵守「提交前 beta 若前进则重做」约定）

验证期间 `origin/beta` **两次**前进：

| 次序 | 从 → 到 | 触发 | 处置 |
| --- | --- | --- | --- |
| 1 | `4174c65ea3d` → `765dce2e2a4` | PR #423（历史集数行对比度） | `git merge --abort` 放弃未提交的 `db133af56` → 以新 tip 重做合并与全部程序化校验 → 重跑编译与双 flavor 全量单测 |
| 2 | `765dce2e2a4` → `7df98107bef` | PR #424（实验室配置源弹窗） | 已由 `task_guard.sh finish` 提交的 `f4e095bea41` 因「beta 已前进」判定为过期交付 → 本地删除其 recovery tag 并 `git reset --hard` 回任务起始 HEAD，**该提交从未推送**；随后以最终 tip 重做合并、校验、编译与全量单测 |

**结果**：`db133af56`、`f48e2206f` 两棵中间合并树与提交 `f4e095bea41` 均**未进入推送历史**，最终历史只含以 `7df98107bef` 为第二父的一次合并提交。

两次新增路径（合计 7 条：`adapter_vod.xml`、`HistoryAdapterTest.java`、`docs/C49-beta-merge-review-dev2-*.md`、`LabActivity.java`、`dialog_lab_settings.xml`、`item_lab_dropdown.xml`、`lab_styles.xml`、`LabSettingsDialogThemeTest.java`、`docs/C50-beta-merge-review-dev1-*.md`、`docs/LAB-CONFIG-DIALOG-*.md`）与 dev4 自身 6 个改动路径**零交集**（`comm -12` 为空）。

### beta 增量 ledger（`git log --oneline <起始HEAD>..origin/beta`）

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `7df98107befd6bdd49f7aae5c7fc9c1efeb19b34` | Merge PR #424 from dev1 | 纳入（合并线，**终轮第二父**） |
| `cb8022bf777b6e9cbc1c2738b6eb4a8f669b62d4` | docs(c50) 交付坐标 | 纳入（仅 `docs/`） |
| `1e83bdc25a6819b1767f8526dbebeda9bab11cfc` | docs(c50) 复评实验室配置源弹窗并修正主题跟随描述 | 纳入（仅 `docs/`） |
| `a8b30e09dcc788713b6b91ce170385f476728ba9` | dev1 合并 PR#423 增量并复评实验室弹窗 | 纳入（合并线） |
| `60e657c074842555b1e70c46d3b0442c813a3914` | fix(lab): 配置源下拉列表改用弹窗主题上下文与语义 item 布局 | **纳入**（实质增量，见第 5 轮） |
| `a7154d35e4f7f39e0fcfc8a55c441723f4fdaefe` | fix(lab): 配置源弹窗内容与面板同源，修复默认主题下白字看不清 | **纳入**（实质增量，见第 5 轮） |
| `765dce2e2a4e12d66c30bf07eaaa0d3a92a5f899` | Merge PR #423 from dev2 | 纳入（合并线） |
| `a36a1e9c8f2...`（docs(c49) 交付坐标，dev2 侧） | 文档 | 纳入（仅 `docs/`） |
| `a51e6f366f2...`（dev2 合并 beta 并复评历史集数行对比度） | 合并线 | 纳入 |
| `0582c722216...` | fix(mobile): 修复历史记录集数行在壁纸上的对比度不足 | **纳入**（实质增量） |
| `4174c65ea3dd30c9ee9ebb55db64839970d85a29` | Merge PR #422 from dev1 | 纳入（合并线） |
| `0a0bed42089` / `87380b5cf76` | docs(c48) 交付坐标 / C48 文档收口 | 纳入（仅 `docs/`） |
| `b8adf3cc1c9` | C48 合并提交（0 冲突） | 纳入（合并线） |
| `5096941a403` | 修复自动下一集黑屏：Surface 未绑定到重建后的引擎 | **纳入**（实质增量） |
| `46917be7965` / `1a4d70410c8` / `74c39d6dea3` | PR #421 / C46 dev2 合并线与文档 | 纳入 |
| `ce01b45c3c5` | 合并上游 `webhtv/webhtv` 站点注入搜索与手机缓存排版修复 | 纳入（合并线） |
| `05469a967ad` | fix: 修复站点注入搜索空状态与排序刷新回归 | **纳入**（实质增量） |
| `7357b2c37d6` | feat: add site injection search | **纳入**（实质增量） |
| `58ebb653ff9` | fix: repair mobile cache management layout | **纳入**（实质增量） |

### 三方无损校验（程序化逐 blob 比对，终轮合并树 `df59dec76`）

```text
beta tip 文件总数                          : 4977（随 PR #423/#424 增长）
合并树文件总数                             : beta 全部 + dev4 独有 2
只在 beta 存在（合并丢失）                  : 0
只在合并树存在（dev4 独有新增）             : 2（C47SeekLoadingProgressSourceTest.java、docs/C47-seek-loading-progress.md）
两侧共有路径中 blob 不同                    : 4（全部属于本分支自身改动集）
blob 差异中「不在本分支改动集内」（必须 0）  : 0
beta 增量路径数 / 丢失 / 非自身改动导致漂移  : 31 / 0 / 0
dev4 自身改动路径 丢失 / blob 被改写         : 0 / 0
```

## 用户核心关注点：远端已移除（回退）内容零复活

采用**三层递进证据 + 可证伪自检**，不依赖人工目测；终轮合并树上重算。

### 第一层：结构性证明（最强证据）

净差异路径集（`git diff --name-only origin/beta`，6 路径）与本地自身提交改动路径集（`git diff --name-only <merge-base> <起始HEAD>`，6 路径）**完全一致（IDENTICAL）**：

```text
app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java
app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java
app/src/test/java/com/fongmi/android/tv/ui/activity/C47SeekLoadingProgressSourceTest.java
app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityLayoutTest.java
docs/C47-seek-loading-progress.md
docs/upstream-player-dependency-merge-assessment-2026-08-20.md
```

因此「净差异路径集之外的路径」在合并树与 beta tip 中逐 blob 相同 ⇒ 其**每一行都存在于 beta tip**，按定义不可能是复活行。据此，第二层的行级扫描只需覆盖净差异路径集即为**完备扫描**（不是抽样）。

### 第二层：行级复活扫描（对净差异路径集完备）

对 beta 历史上每个**单亲 revert/移除/删除类提交** `R`（主题行锚定 `^(Revert|revert|回退|撤销|剔除|移除|删除|remove|Remove|delete|Delete|drop|Drop)` 且为 beta 可达），取父 `R^` 得 `removed(R,f) = lines(R^:f) − lines(R:f)`；复活定义为「该行出现在合并树 `MT` 中，且该行在 beta tip 中**也不存在**」。

```text
候选单亲 revert/移除类提交（beta 可达）      : 54
其中触及净差异路径集的提交数                 : 6
净差异路径集内累积 removed 行（去重）         : 36
复活行数                                     : 0
```

### 第二层自检：检测器可证伪（防止空转）

用一个**确实「被 revert 集删除且不在 beta 树」的真实路径**做注入：

```text
哨兵路径（确实被 revert 且不在 beta 树）: app/src/main/java/com/fongmi/android/tv/ad/audio/AdAudioConsumer.java
该路径被 revert 集删除的行数             : 171
注入正例（被删除行 + 无关行）命中行数     : 170（应等于被删除行数）
注入负例（纯无关行）命中行数              : 0（应为 0）
SELFCHECK PASS: 检测器对注入的复活内容敏感，且对无关内容零命中
```

即：检测器不恒返回空集，对真实复活内容敏感、对无关内容不误报。

### 第三层：净差异行级核对

```text
合并树独有、beta tip 中不存在的行 : 362
  43 app/src/leanback/.../VideoActivity.java         （C47 seek 窗口）
  38 app/src/mobile/.../VideoActivity.java           （C47 seek 窗口）
 162 app/src/test/java/.../C47SeekLoadingProgressSourceTest.java（C47 新增契约测试）
  13 app/src/testMobile/.../VideoActivityLayoutTest.java（C47 改写断言）
 105 docs/C47-seek-loading-progress.md               （C47 任务文档，含本任务 2 处订正）
   1 docs/upstream-player-dependency-merge-assessment-2026-08-20.md（C47 索引行）
```

这 362 行**全部**是本分支自身改动（C47 及其文档），无一来自 beta 历史。

**关键目标提交 `5682f2b05`（剔除 PR #353 主题系统改动）复核**：该提交不在 `origin/beta` 祖先中。第一层证明已覆盖：其触碰路径若不在本分支 6 路径改动集内，则合并树与 beta 逐字节相同；本分支只改 2 个 flavor `VideoActivity` 的 seek 窗口相关代码与对应测试/文档，与主题系统零交集。

**结论：零复活。**

## 复评记录（4 轮）

复评对象 = dev4 全部已修改代码（`23e5280f37d` + `1a4110178fc`，含已提交未推送）+ 本次合并带入的 beta 增量（4 个实质提交）。

### 第 1 轮：C47 seek 加载圈窗口逐点代码复核 + 发现 1 处文档事实错误

| 复核项 | 证据 | 判定 |
| --- | --- | --- |
| 根因链路是否真实 | `CustomSeekView.onScrubStop` → `onSeekStarted()` 先开窗后 `showProgress()`，后者立即 `App.post(mR3/mR2, 0)` 投递网速 ticker；ticker 首跳 `hidePlaybackProgressIfStale()` 在 seek 生效前读到 READY 即收圈 | 与 C47 记录一致 |
| 闸门语义自洽 | `canHideSeekProgress()`：窗口未开即放行；窗口开着时才要求「≥1200ms」且「service/player 有效、非 released、非 empty、READY、`!isLoading() \|\| isPlaying()`」 | 通过 |
| 无自旋/重试循环 | 收尾仅两条路径（1200ms 计时器 + 每秒一跳 ticker；`setTraffic()` 末尾无条件 `App.post(mR*, 1000)` 保证窗口开着期间被每秒重判，必然收口） | 通过 |
| 无「圈永久残留」新路径 | `onStateChanged(STATE_READY)` 收圈不依赖 `isOwner()`（与 C47 前一致），归属失配时仍有收口路径 | 通过 |
| `showProgress()` 保留窗口计时器安全 | `if (!mSeekProgressPending && mSeekProgressFallback != null)`：仅窗口未开时才摘；窗口开着时那次 `showProgress()` 属 seek 自身（BUFFERING 分支） | 通过 |
| 两 flavor 对称 | mobile `mR2 = setTraffic`、leanback `mR3 = setTraffic`，契约测试同时接受两者 | 通过 |
| 定时器/回调存活期 | `mSeekProgressFallback` 在 `initView` 早于任何 seek 初始化；`onDestroy` 统一移除 | 通过 |
| 引擎 `isLoading()` 语义 | `PlayerManager.isLoading()` 直通 Media3；IJK 实现在 `BUFFERING_END`/首帧即清，无「暂停即永久 loading 卡住圈」证据 | 通过 |

**发现并修复（文档层，真实缺陷 #1）**：`docs/C47-seek-loading-progress.md` 断言「`onSeekStarted()` 唯一调用者是 `CustomSeekView`，且 `onSeekEnd` 不调用 `onSeekStarted()`」。实测不成立：`PlaybackActivity.seekTo(long deltaMs)` 第 477 行同样调用 `onSeekStarted()`，两个 flavor 的 `onSeekEnd(time)` 都走 `seekTo(time)`（leanback `VideoActivity:8295`、mobile `VideoActivity:9613`）。该错误会让后续复现/验收误判「横滑路径不受影响」。已改为准确表述（两个调用点 + 横滑抬手同样开窗 + 验收仍须用进度条 `app:id/timeBar`）。

### 第 2 轮：发现并修复 1 处文档表述错误

核对根因／实现两节对 `onControllerReadyReconciled()` 的描述：C47 父提交（`24265785a33`）的 leanback `VideoActivity` 中该覆写出现 **0** 次（不存在，基类为空实现）；mobile 侧为既有覆写，C47 只把 `showPlaybackContent()` 改为受闸门约束。原文「`onControllerReadyReconciled()` 无条件收圈 / 改走同一道闸门」对 leanback 不成立——它是本次**新增**的「控制器晚绑定补发收口」行为。已按事实订正（根因节注明 mobile 已有覆写而 leanback 此前没有；实现节明确 leanback 系新增且同样受闸门约束）。

### 第 3 轮：定位并消除 2 项验证环境根因（无生产代码改动）

**根因 A：工作区行尾（CRLF）与仓库 blob（LF）不一致。**
本仓库 `.git/config` 带有 `core.autocrlf=true`，而仓库内所有 blob 均为 LF。此前一轮合并把 24 条合入路径写成了 CRLF，形成「同名文件行尾混杂」状态。以源码文本做**多行契约断言**的测试因此失败：

```text
PlayerPlaybackRegressionSourceTest#livePlaybackAlwaysAutoplaysWhileVodUsesTheConfiguredPolicy
  （断言 PlaybackActivity.java 的 "…shouldAutoPlay() {\n        return PlayerSetting.isAutoPlay();\n    }" 等多行片段，CRLF 下必然不匹配）
```

同理存在既有 CRLF 敏感用例：`ReaderPlaybackRoutingSourceTest`、`SearchResultDownFocusTest`、`WebThemeTokenSourceTest`、`NativeEnhancedPlaybackStyleFocusTest`、`TmdbSourceOnlyInteractionTest`（LF 规范化后全部转绿）。
**处置**：`git config core.autocrlf false` + 把工作区**逐字节**对齐到索引 blob（仅 24 条合入路径需要纠正，且纠正后与索引 blob **零内容差异**）。此处置只影响本机 `.git/config` 与工作区文件表示，**不改变任何被提交的内容**（索引树前后均为 `f48e2206f`）。

**根因 B：Gradle 选用的 JVM 变更，导致 Windows 上 `File.renameTo` 语义不同。**
期间本机 Java 运行时被自动更新（`C:\Program Files\Java\jre1.8.0_501` 被移除，`JAVA_HOME` 指向失效目录 → Gradle 启动即失败）。补齐 `JAVA_HOME` 后为验证 JVM 选择做了一次独立可复现实验：

```text
File.renameTo(已存在的目标文件)：
  JBR 21.0.10（项目 provisioned 工具链，G:\GradleCache\jdks\jetbrains_s_r_o_-21-amd64-windows.2）: true
  Microsoft JDK 21.0.12（我临时指定的 JAVA_HOME）                                             : false
Files.move(..., REPLACE_EXISTING, ATOMIC_MOVE)：两者均 OK
```

生产代码 `MpvFontConfig.writeIfChanged()`（`temporary.renameTo(file)`）与 `MpvHlsCacheCoordinator`（`reservation.tempFile.renameTo(...)`）使用 `File.renameTo` 覆盖已存在文件，因此在 Microsoft JDK 21.0.12 下**确定性失败**：

```text
MpvFontConfigTest > writeIfChanged_replacesStaleConfigurationAndCleansTemporaryFile      → IOException: Unable to commit fontconfig configuration
MpvHlsCacheCoordinatorTest > successfulCommitPublishesCompleteFileAndReleasesReservation → assertTrue(commit(...)) 失败
（两者在 Microsoft JDK 21.0.12 下可稳定复现；在 JBR 21.0.10 下 3/3、13/13 全通过）
```

**处置**：验证统一使用项目 provisioned 的 **JBR 21.0.10** 作为 `JAVA_HOME`（即本机原始可用环境），不再引入外部 JDK。上述两个测试类随即全绿。
**说明（不扩大范围）**：`File.renameTo` 的行为差异属**环境/JDK 补丁级差异**，非本次合并或 C47 改动引入；`MpvFontConfig.java`、`MpvHlsCacheCoordinator.java` 与 beta 逐字节相同、且不在本分支改动集内，按 AGENTS.md §2 仅记录不修改（如后续要跨 JDK 稳健，宜改为 `Files.move(..., REPLACE_EXISTING, ATOMIC_MOVE)`，属独立任务）。

**附带记录**：`RealtimeSubtitleTranslatorTest`（subtitle 包，类与测试 blob 与 beta 完全相同、不在改动集内）在**高并发负载**下会出现 1–3 个用例的时序抖动失败（本任务同时运行校验脚本时），安静环境下单独运行两次均 6/6 通过、终轮全量重跑亦 0 失败。属负载敏感的既有测试。

### 第 4 轮：终轮复评（全部契约断言逐一实测）

对两个 flavor 逐项实测文档与代码的一致性：

```text
SEEK_PROGRESS_MIN_VISIBLE_MS = 1200L 存在        : mobile 1 / leanback 1
canHideSeekProgress() 闸门出现次数                : mobile 5 / leanback 5
hidePlaybackProgressIfStale 内含闸门（症状执行者） : mobile 1 / leanback 1
onStateChanged(READY) 分支走闸门、且无无条件收圈   : mobile 通过 / leanback 通过
onControllerReadyReconciled 走闸门                : mobile 1 / leanback 1
onSeekStarted 顺序（开窗 → showProgress → 挂计时器）: mobile 通过 / leanback 通过
hideProgress() 关闭窗口                           : mobile 1 / leanback 1
```

并按源码方法体（`case STATE_READY:` → `case STATE_ENDED:`）确认两 flavor 均**不存在**无条件的 `showPlaybackContent();`。极值路径复核：未开窗时闸门放行（正常起播无回归）、窗口开着时由 1200ms 计时器与每秒 ticker 兜底收口（无残留、无新增循环）。
**第 4 轮结论：全部通过，无剩余阻塞项。**

### 第 5 轮：评审新合入的 PR #424（实验室配置源弹窗）

该增量由 PR #424 带入（`a7154d35e4f`、`60e657c0748`），按「逐点复核 + 实测断言」评审：

| 复核项 | 证据 | 判定 |
| --- | --- | --- |
| 缺陷是否真实且定位准确 | `LabActivity.showSettings()` 原用 `getLayoutInflater()`（= `Theme.App.Lab`，固定深色 `colorOnSurface=#FFFFFF`）inflate `dialog_lab_settings.xml`，而面板由 `ThemeOverlay.WebHTV.Dialog`（日/夜双表）决定 → 浅色系统下白字 + 浅色面板 | 与增量说明一致（对比度 ~1.05:1） |
| 修法是否对症 | `ContextThemeWrapper(this, R.style.Theme_App_Lab_DayNight_Dialog)` + `LayoutInflater.from(dialogContext)`，使 `?attr/colorOnSurface` 与面板同源；实测 `LabActivity:366` 与 `:251` 均使用该上下文 | 通过 |
| 下拉项为何必须换布局 | `ArrayAdapter` 用**自身 context** 解析 item 布局，原 `android.R.layout.simple_dropdown_item_1line` + Activity 上下文 → 下拉项固定白字；改为 `new ArrayAdapter<>(dialogContext, R.layout.item_lab_dropdown, items)`，新布局用 `?attr/colorOnSurface` + `@android:id/text1`（`ArrayAdapter` 约定 id） | 通过 |
| 是否消灭硬编码色 | `colorPrimary` 由 `@color/accent` 改为语义 `@color/webhtv_color_primary`（并补 `colorOnPrimary`），`LabActivity` 中 `R.color.accent` 改为 `R.color.webhtv_color_primary` → 满足 `ThemeBinder` 精确匹配改写，主题色可作用到该弹窗 | 通过 |
| 是否有契约测试锁住回归 | 新增 `LabSettingsDialogThemeTest`（8 用例），两 flavor 均 **8/8 通过**；断言「内容用弹窗主题上下文 inflate」「弹窗强调色为语义 token」「不得回退到 `android.R.layout.simple_dropdown_item_1line` + Activity 上下文」 | 通过 |
| 是否影响 UI token 门禁 | `check_ui_tokens.sh` → PASS，`violations=1` 仍为预先存在的 `item_following.xml`；新布局基线计入 `layouts=385`（+1）但**零新增违规** | 通过 |
| 是否回归既有行为 | 布局/主题改动仅作用于实验室设置弹窗；编译与两 flavor 全量单测（含 8 个新用例）零失败 | 无回归证据 |

**第 5 轮结论：无必修缺陷，不改动。**

**记录不修改（AGENTS.md §2，非本次引入）**：`app/src/main/java/com/fongmi/android/tv/ui/dialog/AiConfigDialog.java:429` 仍使用 `super(context, android.R.layout.simple_dropdown_item_1line, items)`，与本次修好的实验室下拉属同类隐患（其 context 若为 Activity 主题，浅色面板下同样可能字色不可读）。该文件**不在本次改动集内**，按约定仅记录、不修改，建议作为独立任务评估。

## 改动清单（本次 C49 相对任务起始 HEAD）

| 路径 | 类型 | 说明 |
| --- | --- | --- |
| 合并带入的 31 条 beta 增量路径 | 合并（0 生产改写） | 见 ledger；合并树与其 beta 侧 blob 逐字节一致 |
| `docs/C47-seek-loading-progress.md` | 修改（第 1/2 轮订正） | 修正 `onSeekStarted()` 调用点、leanback `onControllerReadyReconciled()` 新增属性 |
| `docs/C49-beta-merge-review-dev4-20261008.md` | 新增 | 本任务文档 |

**生产代码零改动**：`app/src` 下本次无任何新增/修改/删除（C47 的 2 个 `VideoActivity` 与测试 blob 与任务起始 HEAD 完全一致）；净差异收敛为本分支自身改动 + 本任务文档。

## 验证（终轮，安静环境）

1. **双 flavor Java 编译** + **双 flavor AndroidTest Java 编译**：`bash ./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac :app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac` → `BUILD SUCCESSFUL`。
2. **双 flavor 全量单元测试**（同一 `BUILD SUCCESSFUL in 4m 41s`，无并发负载）：

   | flavor | tests | failures | errors | skipped |
   | --- | --- | --- | --- | --- |
   | mobile | **4933** | **0** | **0** | 2 |
   | leanback | **4092** | **0** | **0** | 2 |

   定向核对（两 flavor 均 0 失败）：`C47SeekLoadingProgressSourceTest` 5、`VideoActivityLayoutTest`（mobile）154、`PlaybackOwnershipSourceTest` 16/16（含 beta 带入的 `kernelRebuildBindsTheNewEngineToThePlayerViewByInstanceIdentity`）、`CustomCspSettingTest` 3、`CustomCspDialogTest` 5（leanback）、`HistoryAdapterTest`（mobile，PR #423 增量）3、`LabSettingsDialogThemeTest`（两 flavor，PR #424 增量）8、`PlayerPlaybackRegressionSourceTest`（mobile）14、`MpvFontConfigTest` 3、`MpvHlsCacheCoordinatorTest` 13。
3. **UI token 门禁**：`bash scripts/check_ui_tokens.sh` → `UI_TOKEN_STATUS PASS`（`violations=1` 为预先存在的 `item_following.xml`，不在改动集内）→ 相对基线**零新增违规**。`UI_TOKEN_CONTRAST failures=0`。
4. **零复活程序化校验**：三层证据 + 可证伪自检，resurrected = 0。
5. **零丢失程序化校验**：beta 增量 31 路径 0 丢失 0 漂移；dev4 自身 6 路径 0 丢失 0 改写；31 条已暂存路径与索引 blob **零内容差异**。
6. **良构性**：`git diff --cached --check`（排除 `*.patch`）无空白错误；`app/**` 无冲突标记；合并树与 `git merge-tree` 预测一致。
7. **设备端**：**未执行**（零生产代码改动；不把 JVM 测试描述为设备验证）。合并带入的 beta 增量其设备证据见各任务文档。

## 环境说明（如实记录，不影响被提交内容）

1. **行尾**：本仓库 `.git/config` 原为 `core.autocrlf=true`，检出 CRLF，而仓库 blob 全为 LF。已改为 `core.autocrlf=false` 并把工作区逐字节对齐到索引 blob（`git diff` 除本任务 2 个文档外为空）。该配置**不被提交**，因此 PR 净差异中不含任何行尾churn。后续在本工作区构建/测试建议保持 `core.autocrlf=false`。
2. **JDK**：本机 `JAVA_HOME` 因 Java 自动更新指向已删除的 `jre1.8.0_501`。验证使用项目 provisioned 工具链 **JBR 21.0.10**（`G:\GradleCache\jdks\jetbrains_s_r_o_-21-amd64-windows.2`）；使用 Microsoft JDK 21.0.12 会因 `File.renameTo` 语义差异让 2 个既有 MPV 测试确定性失败（详见第 3 轮）。
3. **索引 stat 缓存**：行尾规范化后曾出现「内容与索引一致但 `git status` 仍报 M」的假阳性（索引记录的 size 为 CRLF 尺寸）。已用内容哈希逐条核对确认**零差异**，`git diff` 为空；提交时由 `git add -A` 自然刷新。

## 交付坐标

| 项 | 值 |
| --- | --- |
| 任务起始 HEAD | `1a4110178fccddc656122e08fc8d3a056ae5e2f2` |
| 最终 `origin/beta` tip | `7df98107befd6bdd49f7aae5c7fc9c1efeb19b34` |
| 终轮合并树 | `df59dec7687b81ff14b483fe0e130201895c1be6`（= `git merge-tree` 预测） |
| C49 改动 | 2 路径（`docs/`；`app/src` 零改动） |
| 合并提交 | `8dd441b1ccc3a65d5486be09de96b6b996e471c1`（父：`1a4110178fc` + `7df98107bef`） |
| recovery tag | `recovery/C49-beta-merge-review-dev4/20261009023809-8dd441b1ccc3` |
| 推送 | `dev4` → `origin/dev4`（`2b108243613..8dd441b1ccc`，快进）；recovery tag 已推送 |
| PR | [#425](https://github.com/Silent1566/webhtv/pull/425) `dev4 → beta`，中文描述，**OPEN、未合并**（`state=OPEN`、`mergedAt=null`、`MERGEABLE`），改动 7 文件 +686 −22 |
| PR 文件集校验 | `gh api .../pulls/425/files` 合计 **7**，与 `git diff --name-only origin/beta HEAD` **逐项一致** |

## 备注

- `PlayerManager.java` 的 `com.fongmi.android.tv.player.exo.TrackUtil` 未使用 import 在 beta tip 中已存在（`git blame` 指向 `daaa0a80a`），**非本次引入**，按 AGENTS.md §2 仅记录不修改。
- `CustomCspDialog.showOtherEdit(...)` 与 `CustomCspSetting.matchesSearch(String, String...)` 在 beta tip 中即为无调用方的遗留方法，**非本次引入**，仅记录不修改。
- beta 带入的文档中记录的构建环境（Windows、`F:/temp/`、`G:/Git/` 等）为各分支作者本机环境描述，不影响本仓库在本机的构建与验证。
