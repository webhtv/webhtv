# C74 dev1 合并 origin/beta 并复评（2026-10-16）

审计日期：2026-10-16（Asia/Shanghai）
任务 ID：`C74-beta-merge-review-dev1-20261016`
分支：`dev1`
范围：`app`、`chaquo`、`docs`

## 1. 目标与判定标准

把 `origin/beta` 最新代码合入 `dev1`，**不得把远端已移除/回退的提交顺带带回来**；随后评审本轮已修改的代码（含 dev1 已提交未推送的 1 个提交）与合并交互，发现问题就最小修复并验证，通过后再次评审，循环至通过；最后提交、推送、创建到 `beta` 的 PR（中文描述，只创建不合并）。

验收标准：

1. 合并提交为双父合并，第二父等于提交前最新 `origin/beta` tip。
2. 合并结果相对 `origin/beta` 的净差异**只**包含 dev1 自身的本地改动与任务文档，不含任何 beta 已删除内容。
3. 零复活有可证伪证据（检测器自检必须能报出注入的探针）。
4. 定向测试、双 flavor Java 编译、Python 契约测试、`git diff --check`、`gradlew clean` 全部通过。
5. PR `base=beta`、`head=dev1`、`state=OPEN`、`mergedAt=null`，描述为中文。

## 2. 合并前的仓库状态

| 项 | 值 |
| --- | --- |
| 分支 / HEAD | `dev1` / `2d4259ef7a3bb18478f301d21c3d3a388e37b46b` |
| `origin/dev1` | `0c2eb46d595a153b9dfc1ea26a469b9cf353b6dc`（本地领先 1） |
| `origin/beta` tip | `9f4ea10a29d347d4a9290bdecdba7966374320fa`（PR #451） |
| 合并基 | `1125b6c9c0727d572ad14a7c38937433afa51d71`（= 上一轮 dev1→beta 的成果） |
| 领先 / 落后 | `1 / 11` |
| 开工前脏文件 | 无（工作树干净） |

### 2.1 本地已提交未推送的改动（评审对象，1 个提交）

| 提交 | 主题 | 路径 |
| --- | --- | --- |
| `2d4259ef7a` | `fix(tv)` 修复影视原生播放页简介不显示与当前集无高亮 | `VideoActivity.java`、`EpisodeAdapter.java`、`activity_video.xml`、`VideoActivityDetailShellSourceTest.java` |

### 2.2 beta 侧带入的 11 个提交

`9f4ea10a29`、`e6791fe1b9`、`0b7ebfd087`、`933ab6d73e`、`12d7d559e5`、`247629aaea`、`96771c8a98`、`317fa9021d`、`9116639e3e`、`b1fe3d5686`、`80816a15f0`，全部为 dev3 的进度条 UP 返焦与 dev2 的 py 桥接/ViewModel 生命周期改动（C72/C73 的成果），与 dev1 的 4 个路径**零交集**（见 §4）。

## 3. 合并执行

```text
git merge --no-commit --no-ff origin/beta
→ Automatic merge went well; stopped before committing as requested
```

- 冲突数：**0**。
- 合并结果相对 `origin/beta` 的净差异恰为 dev1 自身的 4 个路径：

```text
M  app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java
M  app/src/leanback/java/com/fongmi/android/tv/ui/adapter/EpisodeAdapter.java
M  app/src/leanback/res/layout/activity_video.xml
M  app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/VideoActivityDetailShellSourceTest.java
```

- 合并后索引树 `7427691a20`，开工前 HEAD 树 `a5abd77a8f`；4 个路径的工作区内容与 `2d4259ef7a` 的 blob **逐一相同**：

| 路径 | `2d4259ef7a` blob | beta blob | 合并后工作区 |
| --- | --- | --- | --- |
| `VideoActivity.java` | `464bf27a85` | `25f506c93f` | `464bf27a85`（本地版保留） |
| `EpisodeAdapter.java` | `2bf0a55d86` | `292a2deb02` | `2bf0a55d86` |
| `activity_video.xml` | `4ea582b8bf` | `7032470dd4` | `4ea582b8bf` |
| `VideoActivityDetailShellSourceTest.java` | `d15abc8759` | `25e1ce61e3` | `d15abc8759` |

即合并只是把 beta 增量加上去，**没有覆盖也没有丢回退** dev1 的未推送改动。

## 4. 零复活 / 零丢失证据

| 层 | 口径 | 结果 |
| --- | --- | --- |
| L0 净差异 | `git diff --name-status origin/beta` | 4 路径（§3），全部为 dev1 自身的本地改动 |
| L0b 路径集 | beta 树 5074 文件 vs 合并树 5074 文件 | 新增 0、缺失 0 |
| L1 revert 集 | 锚定主题行的 revert 类提交（`^(Revert\|revert\|回退\|撤销\|剔除)`） | 111 个提交、731 条触及路径 |
| L2 路径级 | 合并新增路径 ∩ revert 触及路径 | **0** |
| L3 行级 | 合并新增实质行(136) ∩ revert 删除实质行(7348) | 9 条命中，经分类**全部为通用样板**（`return;`、`}`、`*`、`*/`、`-->`、`/**`、`<!--`、`@Test`、`}`），无业务语义 |
| L4 revert 真删除 | 101 个减法式 revert 提交实际删除的整路径 | 49 条，其中 33 条**不在** beta 树内 |
| L5 复活检测 | 上述 33 条路径在合并工作区是否存在 | **0** 条存在 |
| L6 可证伪自检 | 把 `ISSUE_TEMPLATE`（确在 revert 集且不在 beta 树）注入伪造树，检测器必须报出 | 实测报出 `ISSUE_TEMPLATE`；同一检测器对真实树输出为空 → 检测器**非空转** |
| 评估索引 | 行 ID 集合比对（`^\| \`[A-Z0-9-]*\``） | beta 18 = 合并后 18，缺失 **0** |

- 检测器脚本：`detector.sh` 只做两件事——(a) 输入路径集去掉 beta 树路径，(b) 与「revert 真删除且不在 beta 树」集合求交；结果非空即判定复活。
- 已知两个假阳性来源均已规避：ref 先固化为 `origin/beta` 的 40 位 SHA 再取整树（避开 MSYS 把 `:` 后的 `/` 当分隔符）；revert 集只匹配**主题行并锚定开头**（避开任务自身合并提交正文里的「回退」字样）。

## 5. 复评（3 轮）

### 第 1 轮：评审 `2d4259ef7a` + beta 增量 + 交互

先按「测试读到的文件是否被本合并改动」给全量 leanback 套件的 8 项失败定性。定性方法：把 4 个被改动文件**换成 beta 自己的版本**后重跑同一批测试。

```text
git checkout origin/beta -- <4 个路径>      # 4 个文件与 beta blob 逐一相同
./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests <8 个失败类>
→ 69 tests completed, 8 failed             # 8 项全部原样复现
```

因为一个源码扫描测试的结论是「它读到的全部文件原始字节」的纯函数，而换成 beta 版本后失败一一复现，**这 8 项与本次合并无关**，属既有失败：

| 失败 | 性质 |
| --- | --- |
| `MpvFontConfigTest.writeIfChanged_…` | MPV 字体配置写入（环境/既有） |
| `MpvHlsCacheCoordinatorTest.successfulCommitPublishes…` | MPV HLS 缓存（既有） |
| `NativeEnhancedPlaybackStyleFocusTest.everyTmdbRowCard…` | 跨行字面量 `applyTmdbRowFocusChain();\n        updateFocus();` 在工作区 CRLF 下**恒假**（LF blob 中存在） |
| `ReaderPlaybackRoutingSourceTest` ×2 | 既有（`app/src/main/assets/reader.html` 等） |
| `SearchResultDownFocusTest.scrollCallbacks…` | 既有（`CollectActivity`） |
| `TmdbSourceOnlyInteractionTest.nativeVideoSourceOnly…` | 跨行字面量 `if (runtimeSourceOnly) {\n            renderTmdbRatingChips(…` 在工作区 CRLF 下恒假 |
| `WebThemeTokenSourceTest.webHomeTokenDeclarations…` | 既有（webhome 资源） |

随后评审 4 个被改动文件的实现，**发现 1 项必修缺陷**（见 §6）。

### 第 2 轮：评审第 1 轮的修复

复核 §6 的修复后确认：门控 `mAudioStageVisible` 与「详情列被收起」严格等价——全仓 `setVideoDetailsVisible(false)` 的两个入口（`syncAudioStageSurface`、`updateAudioStageControls`）都以 `mAudioStageVisible` 为真为前提，因此该门控不会误伤非音频场景。

同时收紧了第 1 轮我自己放宽的一处断言：`directPlaybackRendersInlineSynopsisPreview` 原断言 `body.contains("View.VISIBLE")` 过宽（任意位置出现即可），改为断言 `mBinding.tmdbOverview.setVisibility(`，避免「节点写入被删除但别处仍有 `View.VISIBLE`」时恒真。

排查但**判定为非缺陷**（附理由，避免误改上台）：
- `hydrateFastTmdbPlaybackDetail()` 内 `@id/tmdbOverview` 的无条件 `View.VISIBLE`（现第 2288 行）：该方法只在 `isTmdbMode()`（intent `tmdbMode=true`）时走该分支，而全仓 `VideoActivity.start(...)` 的 11 个真实调用点**没有一个**传入非 null 的 `tmdbItem`，故该分支不可达；且该行在 `origin/beta` 中已存在，不属本任务改动。
- `tmdbOverview` 的 `maxLines` 在列收起期间重算：`updateTmdbOverviewButton()` 的夹取以 `view.getLayout() != null` 为前提，`setMaxLines` 只在值变化时写入并自我重投，恢复显示后按新几何重算，不残留错误行数。

### 第 3 轮：评审最终 diff

逐行复核 §3 的净差异与 §2.1 的 4 个路径，确认：

- 修复只收紧了 `tmdbOverview` 一个节点的可见性判定，未改动 `row1`/`row2`/`scroll`/`name` 等既有行为。
- 简介按钮（`@id/content`）位于 `row2` 内，本就随 `row2` 一起收起，无需单独门控——这正是「只补 `tmdbOverview` 一处」的原因。
- 新增的 4 个 `@Test` 全部使用**单行**字面量或 `substring` 区间断言，规避工作区 CRLF 导致的跨行字面量恒假。
- 无新增 TODO/调试残留，行尾与同目录既有文件一致（CRLF）。

3 轮后未再发现必修问题，评审通过。

## 6. 修复：简介区消费 `mAudioStageVisible`（必修）

**现象**：`2d4259ef7a` 本身新增了「沉浸音频/纯音频播放时简介区必须跟着一起收」的修复（`setVideoDetailsVisible()` 内），但同一提交里的写入点 `updateOverviewPreview()` 在末尾**无条件** `setVisibility(View.VISIBLE)`，会把刚被收起的简介区重新点亮。

**可达链路**（均为实测行号，非推测）：

1. `setDetail(Vod)` 中 `checkFlag(item)` 在**第 2704 行**，`setText(item)` 在**第 2706 行**——前者先执行。
2. 沉浸音频续播（`mImmersiveAudioResolved` 为 null 的普通路径）走 `checkFlag()->onItemClick()->onRefresh()->getPlayer()`。
3. `getPlayer()` 第 3051 行：`if (shouldUseImmersiveAudio()) setAudioStageVisible(true);`
4. `setAudioStageVisible(true)` → `syncAudioStageSurface(true)` → `setVideoDetailsVisible(false)`，把整列（含 `tmdbOverview`）压成 GONE。
5. 回到 `setDetail(Vod)` 第 2706 行 `setText(item)` → `updateOverviewPreview()` → 旧代码无条件 `View.VISIBLE`，把简介区重新显示在音频面板旁边。

**最小修复**（`updateOverviewPreview()` 末尾）：

```java
// 详情列可能已经被沉浸音频收起：setDetail() 里 checkFlag() 排在 setText() 之前，
// checkFlag()->onItemClick()->onRefresh()->getPlayer() 会先 setAudioStageVisible(true)
// 把整列（含本节点）收掉，这里再无条件下 show 就会把刚收起的简介重新亮在音频面板旁边。
// 详情列的显示/隐藏以 setVideoDetailsVisible() 为唯一所有者，本写入只负责是否“有内容”。
mBinding.tmdbOverview.setVisibility(mAudioStageVisible ? View.GONE : View.VISIBLE);
```

**回归锁**：`VideoActivityDetailShellSourceTest.synopsisPreviewDoesNotUnhideTheDetailColumnWhileImmersiveAudioIsShown`，钉住三件事——预览写入必须受 `mAudioStageVisible` 约束、写入点内不得再有无条件 `View.VISIBLE`、`setVideoDetailsVisible()` 仍是详情列显隐的唯一所有者。

**变异测试**（证明断言非空转，全部如期转红并干净还原）：

| 变异 | 内容 | 结果 |
| --- | --- | --- |
| M1 | `EpisodeAdapter`：`setActivated(active)` → `setActivated(false)` | 红（`directEpisodeChipsMarkThePlayingEpisode`） |
| M2 | 预览写入：`setText(overviewText)` → `setText("")` | 红（`directPlaybackRendersInlineSynopsisPreview`） |
| M4 | 布局：`layout_below="@+id/actor"` → `"@+id/director"` | 红（`synopsisPreviewIsAnchoredBelowTheActorRow`） |
| M3 | 批次 A 同跑 | 5 tests, 3 failed |
| B | `updateOverviewPreview`：门控改回无条件 `View.VISIBLE` | 红（新增锁） |
| C | `mAudioStageVisible ? View.VISIBLE : View.VISIBLE` | 红（新增锁） |
| D | 收紧断言后再次注入无条件 `View.VISIBLE` | 红（新增锁，证明收紧后仍非空转） |

还原后 `git hash-object --path=` 与修复版逐一相同（`07b27fa310` / `526251dd16` / `2bf0a55d86` / `4ea582b8bf`）。

**关于布局锚点的补充核查**：`activity_video.xml` 把 `@id/tmdbOverview` 的 `layout_below` 由 `@id/director` 改为 `@id/actor`，依据是「RelativeLayout 会把 GONE 锚点沿链上溯」。该行为在 SDK 源码中得到确认（`android-37.0/android/widget/RelativeLayout.java:1028` `getRelatedView()`：`while (v.getVisibility() == View.GONE)` 时改取其 `rules[relation]` 继续上溯，遇 null 或自依赖终止）——TMDB/原生增强模式下 `actor` 被 `suppressTmdbNativeTextFields()` 压成 GONE，上溯即落回 `director`，因此两种模式都不重叠。

## 7. 验证证据

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 定向单测 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests VideoActivityDetailShellSourceTest --tests PlayerControlFocusHelperSeekReturnTest --tests SiteViewModelSubmitAfterClearTest --tests PyLoaderDiagnosticsWiringTest` | **BUILD SUCCESSFUL** |
| 用例明细 | 见 `app/build/test-results/testLeanbackArm64_v8aDebugUnitTest/` | `VideoActivityDetailShellSourceTest` 6/6（原 5 + 新增 1）、`PlayerControlFocusHelperSeekReturnTest` 13/13、`SiteViewModelSubmitAfterClearTest` 4/4、`PyLoaderDiagnosticsWiringTest` 3/3 |
| 全量 leanback 回归 | `:app:testLeanbackArm64_v8aDebugUnitTest` | 4271 项 / 8 失败 / 3 跳过；**失败集与开工前基线完全一致**（8 项全部已证既有），未新增失败；用例数 4270 → 4271（本轮新增 1 条） |
| 双 flavor 编译 | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL** |
| Python 契约 | `py -3.10 -m unittest discover -s chaquo/src/test/python` 与 `python -m unittest discover -s chaquo/src/test/python` | **36/36 OK**（CPython 3.10.11 与 3.14.7 各一次） |
| 空白/冲突 | `git diff --check`、`git diff --cached --check`、`git grep -nE '^(<<<<<<<\|>>>>>>>\|\|\|\|\|\|\|\|)'` | 通过，0 处冲突标记 |
| 净差异 | `git diff --name-status origin/beta` | 仅 §3 的 4 路径 + 本任务文档 |
| 零复活 | §4 六层证据 + 注入探针自检 | 0 复活、0 缺失、评估索引行零丢失 |
| 构建清理 | `./gradlew.bat --no-daemon clean` | 通过（§9） |

未执行：APK 打包、设备安装、实机播放矩阵、native 重建。因此上表不扩大为设备级验收；设备侧证据仍以 `2d4259ef7a` 自身携带的实测记录为准（简介区内联显示并省略号截断、E01 当前环与焦点环行为），本轮修复只收紧了节点可见性判定，未改变已实测的显示内容。

## 8. 当前状态

- 合并 + 复评修复已完成，工作树处于未提交的合并状态（`.git/MERGE_HEAD` = `9f4ea10a29`）。
- 结论：评审通过，无遗留必修问题。
- 下一动作：由 `task_guard finish` 生成双父合并提交与 recovery tag，推送 `dev1` 与 tag，随后 `gh pr create --base beta` 创建中文 PR（只创建不合并）。

## 9. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交（双父） | `1e0d3ae0a4add1ed99ba62ee6300c1faff846671` |
| 第一父 | `2d4259ef7a3bb18478f301d21c3d3a388e37b46b`（dev1 开工前 HEAD） |
| 第二父 | `9f4ea10a29d347d4a9290bdecdba7966374320fa`（提交前最新 `origin/beta` tip） |
| recovery tag | `recovery/C74-beta-merge-review-dev1-20261016/20261016100405-1e0d3ae0a4ad` |
| 分支 | `dev1` |
| PR | [#452](https://github.com/Silent1566/webhtv/pull/452)（`base=beta`、`head=dev1`、`state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`，只创建不合并） |

净差异（`git diff --name-status origin/beta 1e0d3ae0a4`）：

```
M  app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java
M  app/src/leanback/java/com/fongmi/android/tv/ui/adapter/EpisodeAdapter.java
M  app/src/leanback/res/layout/activity_video.xml
M  app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/VideoActivityDetailShellSourceTest.java
A  docs/C74-beta-merge-review-dev1-20261016.md
M  docs/upstream-player-dependency-merge-assessment-2026-08-20.md
```

即 4 个 dev1 自身路径 + 本任务文档；无任何 beta 已删除内容被带回。

### 交付后复核（创建 PR 之后）

- 再次 `git fetch origin beta`：`origin/beta` 仍等于合并提交第二父 `9f4ea10a29`（未前进，PR 不会转为 CONFLICTING）。
- `gh pr view 452`：`baseRefName=beta`、`headRefName=dev1`、`state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`。
- 远端 `refs/heads/dev1` = `1e0d3ae0a4`，annotated tag 解引用（`^{}`）后同样指向 `1e0d3ae0a4`。
- PR 文件集（6 项）与 `git diff --name-only origin/beta HEAD`（6 项）逐项一致（用 `diff` 校验，非目测）。
- **未合并 PR**：`mergedAt=null`，仅创建。
