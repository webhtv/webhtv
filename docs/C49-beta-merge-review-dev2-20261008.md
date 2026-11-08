# C49：dev2 合并远端 beta 最新代码并循环评审已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev2`（**远端已移除/回退的提交不得顺带带回**）；复评 dev2 全部已修改代码（含已提交未推送的 `0582c72221`）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev2`、创建 `dev2 → beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并提交第二父为 `origin/beta` tip `4174c65ea3`；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev2 既有改动**零丢失**；④ 双 flavor Java 与 AndroidTest Java 编译通过；⑤ 双 flavor 全量 JVM 套件相对合并前基线**零新增回归**；⑥ UI token 门禁相对基线**零新增违规**；⑦ 净差异只含本分支自身改动与任务文档；⑧ 提交 + recovery tag；⑨ `dev2` 已推送、PR 已创建且**未合并**。
- **lane / scope**：`standard`；`app/`、`docs/`。
- **任务守卫**：`C49-beta-merge-review-dev2`；任务开始 HEAD 为 `0582c722216a39854c9d9421af25297bffc80daa`；初始工作区干净（0 个受保护脏路径）。
- **当前状态**：合并完成（**0 冲突**）；2 轮评审完成，**发现并修复 1 个必修问题**（新增注释触发 UI token 门禁新增违规）；全部验证通过；待 `task_guard.sh finish`。
- **下一动作**：`task_guard.sh finish` → 推送 `dev2` → `gh pr create`（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始时 HEAD | `0582c722216a39854c9d9421af25297bffc80daa`（领先 `origin/dev2` 2 个提交） |
| 领先 `origin/dev2` 的提交 | `46917be796`（Merge PR #421，已在 beta 上）、`0582c72221`（历史集数行对比度修复，**已提交未推送**） |
| `origin/beta` tip（合并前） | `4174c65ea3dd30c9ee9ebb55db64839970d85a29`（Merge PR #422 from dev1） |
| 合并基点（merge-base） | `46917be7965b7baa0ef75edc2cdeeb41ac411620` |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`，由 task_guard `finish` 创建合并提交 |
| 合并结果 | 4 路径自动合入，**0 冲突、0 冲突标记** |
| 合并结果树（修复前） | `05e1e94b6f02399a26f83036d328eb203cbd67b5` |
| `MERGE_HEAD` | `4174c65ea3dd30c9ee9ebb55db64839970d85a29` = `origin/beta` tip ✅ |
| 初始脏路径 | 无（`git status` 干净） |
| 回滚锚点 | `0582c722216a39854c9d9421af25297bffc80daa` |
| 任务守卫 | `C49-beta-merge-review-dev2`（standard，scope `app` + `docs`） |

### beta 增量 ledger（5 个提交 / 4 路径，全部纳入）

`git log --oneline 46917be796..origin/beta`：

| 完整 commit ID | 标题 | 实质增量 |
| --- | --- | --- |
| `5096941a40304d9aae27c19d706df1454ba4e1e2` | 修复自动下一集黑屏：Surface 未绑定到重建后的引擎 | `PlaybackActivity.java`、`PlaybackOwnershipSourceTest.java` |
| `b8adf3cc1c9bc0e360823dac467b4da0aae6c9de` | merge: 合并 origin/beta 最新代码（PR#421…） | 合并提交，第二父 `46917be796`，无新内容 |
| `87380b5cf76f06b12b5c66948b851450a68bbeda` | merge: 同上（正文含评审/验证） | 单父提交，记录评审与验证 |
| `0a0bed420895b4074b4e6a9b39222487d2dd49a9` | docs(c48): 记录交付坐标 | `docs/C48-beta-merge-review-dev1-20261008.md` |
| `4174c65ea3dd30c9ee9ebb55db64839970d85a29` | Merge pull request #422 from Silent1566/dev1 | 合并提交 |

合并后 `git diff --name-status HEAD <merge-tree>` = **4 路径**：
`M PlaybackActivity.java`、`M PlaybackOwnershipSourceTest.java`、
`A docs/AUTONEXT-01-episode-transition-black-screen.md`、`A docs/C48-beta-merge-review-dev1-20261008.md`。

### 本分支自身改动（相对 beta 的净差异）

| 路径 | 性质 | 说明 |
| --- | --- | --- |
| `app/src/mobile/res/layout/adapter_vod.xml` | M | 历史卡片集数行 `@+id/remark` 由 `?attr/colorOnSurfaceVariant` 改为 `?attr/webhtvColorOnWallpaper`（`0582c72221`），并在评审中修正注释（见第 1 轮评审） |
| `app/src/testMobile/java/com/fongmi/android/tv/ui/adapter/HistoryAdapterTest.java` | M | 新增 `assertMobileEpisodeLineUsesTheWallpaperForegroundRole` 守卫（`0582c72221`） |
| `docs/C49-beta-merge-review-dev2-20261008.md` | A | 本任务文档 |

## 零复活 / 零丢失证据

采用仓库既有口径，全部基于不可变 ref 与整树比对，避免 MSYS 路径分隔符误判：

1. 固化 ref：`origin/beta` = `4174c65ea3dd30c9ee9ebb55db64839970d85a29`，`HEAD` = `0582c722216a39854c9d9421af25297bffc80daa`，合并树 = `git write-tree`。
2. 用 `git ls-tree -r --name-only` 取三棵树的完整路径集合（beta 4972 / HEAD 4970 / 合并树 4972），用 `comm` 做集合差，不对单路径做 `git cat-file -e`。
3. 复活提交集只用**主题行且锚定开头**：`git log --all --format='%H%x09%s' | awk -F'\t' '$2 ~ /^(Revert|revert|回退|撤销|剔除)/ {print $1}'` → **182** 个；其中单亲提交 182 个，触碰路径去重 **852** 个。
4. 「beta 已移除路径」= 被 revert 触碰且不在 beta 树中 → **671** 个；与合并树求交 → **复活 0 个**。
5. 合并树相对 beta 的路径集合**完全相同**（`cmp` 一致）；`HEAD` 相对 beta 的独有路径为 **0**，beta 相对 `HEAD` 的独有路径为 **2**（即被合入的两份 dev1 文档），合并后该 2 条路径补齐。
6. 可证伪自检：把 `ISSUE_TEMPLATE`（确证在 revert 集且不在 beta 树）注入伪造的合并树路径集合，检测器**报出该路径**；移出后报 0，证明检测器非空转。
7. 内容一致性：beta 增量 4 路径在合并树中的 blob 与 `origin/beta` **逐字节相同**；dev2 自身 2 路径的 blob 与任务开始 `HEAD` **逐字节相同**。

## 循环评审记录

评审范围 = 本任务全部「已修改代码」：beta 增量 4 路径 + dev2 已提交未推送的 `0582c72221` + 两者的交互。

### 第 1 轮

| 对象 | 结论 | 依据 |
| --- | --- | --- |
| `PlaybackActivity.attachSurface`（beta 增量） | 通过 | 旧判断 `getExoView().getPlayer() == null` 只在视图为空时绑定；换集时 `PlayerManager.clear()` 置空 spec、`preparePlayer()` 重建引擎，`isOwner()` 为 false 使 `onPlayerRebuild()` 里 `setRender()` 整段跳过，`render` 值不变 → 视图仍挂着已 `release()` 的旧引擎（非空），新引擎永不获得 Surface。新判断按引擎实例身份，是旧语义的**超集**：`current != next` 时才重绑，同实例早退不会产生解绑-绑定循环；`current == next == null` 时仅跳过 `syncVideoSurfaceSize/syncShutter` 等对空引擎的多余副作用，无行为回退。`setRender()` 路径（`render = -1` → `detachSurface` → `attachSurface`）中 `currentPlayer` 必为 null，新分支与旧分支等价。 |
| `PlaybackOwnershipSourceTest` 新用例（beta 增量） | 通过 | 断言与 `attachSurface` 实际文本一致；`indexOf("\n    }", attach)` 取到的方法体边界正确；`源扫描` 断言可在 LF 检出下证伪。合入后双 flavor `PlaybackOwnershipSourceTest` 16/16 通过。 |
| beta 增量文档 2 份 | 通过 | 均为 dev1 的任务记录，不引用任何已回退内容；不影响构建与运行行为。 |
| `adapter_vod.xml` 集数行角色（`0582c72221`） | 通过 | `?attr/webhtvColorOnWallpaper` 由 `values` 与 `values-night` 双方的 `webhtv_on_wallpaper` 解析，**两张表均为 `#FFFFFF`**，与调色板无关；信息区底 `shape_vod_name` 是 `black_20` 半透明黑，压在壁纸上（`BaseActivity` 注入 `CustomWallView`），故必须用恒定亮色角色。同 flavor 的 `adapter_vod_list.xml` 两行、以及同容器的 `@+id/name` 早已使用该角色，本次改动使 `remark` 与之一致。`KeepAdapter` 复用同一 `AdapterVodBinding`，因此手机端「稍后再看」同步受益，无行为回退。 |
| `HistoryAdapterTest` 新守卫 | 通过 | 断言 `remark` 与 `name` 同为该角色，可证伪；合入后 3/3 通过。 |
| **发现问题（必修）** | **已修复** | `0582c72221` 在 `adapter_vod.xml` 新增的注释里写入了裸色值 `#33000000`、`#44474F`，命中 `scripts/check_ui_tokens.sh` 的 `hex_pattern = #[0-9A-Fa-f]{6,8}\b`：该文件的违规计数由基线 **1**（仅 `item_following.xml`）升到 **2**（新增 `adapter_vod.xml`），即**本次改动引入了 UI token 门禁新增违规**。修复方式为**改写注释去掉裸色值**（保留「半透明黑 / 日间表深灰 / 1.4:1」等结论，色值继续留在 `HistoryAdapterTest` 的用例说明里），不弱化门禁、不改任何运行行为。修复后门禁回到基线。 |

### 第 2 轮

| 对象 | 结论 | 依据 |
| --- | --- | --- |
| 修复后的注释文本 | 通过 | XML 可解析；文件内已无 6–8 位裸色值；`check_ui_tokens.sh` 回到 `violations=1 / hex_layouts=1`（与任务开始 `HEAD`、以及与改动前的 `46917be796` 完全一致）；注释新增末行指向 `HistoryAdapterTest`，与测试中保留的色值说明互相对应。 |
| 净差异收敛 | 通过 | 最终 `git diff origin/beta -- app/` 只有 2 个文件：`adapter_vod.xml`（角色 + 无裸色值注释）与 `HistoryAdapterTest.java`（守卫）；无新增路径、无删除路径。 |
| 事实性核对 | 通过 | 注释引用的 `f63b0a81bc`（feat(ui): unify home list and card tokens）确为把该行由 `@color/white_70` 改为 `?attr/colorOnSurfaceVariant` 的提交。 |
| 空白 / 冲突标记 / 重复键 | 通过 | `git diff --check` 与 `git diff --cached --check` 退出码均为 0；无冲突标记；本次未改动任何 `strings.xml`。 |

2 轮评审均无遗留必修问题；第 1 轮发现的唯一必修问题已修复并在第 2 轮复核通过。

### 评审中确认、但**未纳入本次改动**的相邻问题（供用户决定）

- `app/src/leanback/res/layout/adapter_vod.xml` 的 `@+id/remark`（TV 历史卡片集数行）**仍是** `?attr/colorOnSurfaceVariant`，与同容器 `@+id/name`（`?attr/webhtvColorOnWallpaper`）不一致；TV 历史页同样处于 `BaseActivity` 注入的壁纸之上，`shape_vod_name` 也是 `black_20`，因此日间表下会复现同一种「深灰压在暗底上」的低对比度（`LeanbackForegroundContrastTest` 只做「亮色前景不得压在亮底」的单向守卫，不覆盖这一方向）。
- 该文件**不在本次改动范围内**（本任务只评审已修改代码），且改动 TV 次级文字角色属于视觉层级决策，故**只报告、不擅自修改**。若需修复，最小改动为把该行换成 `?attr/webhtvColorOnWallpaper`，并在 `LeanbackForegroundContrastTest`/对应守卫中补一条 TV 侧的对称断言。

## 验证证据

全部命令在 `F:/Workspace/webtv2/webhtv`（dev2）执行。

| 项目 | 命令 / 结果 |
| --- | --- |
| 双 flavor 全量 JVM 套件（合并后） | `gradlew.bat :app:testMobileArm64_v8aDebugUnitTest :app:testLeanbackArm64_v8aDebugUnitTest` → mobile **4920 tests / 0 failures / 0 errors**，leanback **4079 tests / 0 failures / 0 errors**（skipped 各 2）→ `BUILD SUCCESSFUL` |
| 双 flavor Java 编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` → 成功 |
| 双 flavor AndroidTest Java 编译 | `:app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac` → 成功 |
| 定向用例 | `PlaybackOwnershipSourceTest` 16/16 ×2 flavor；`HistoryAdapterTest` 3/3（mobile） |
| UI token 门禁 | `scripts/check_ui_tokens.sh`：`UI_TOKEN_SCOPE stage=A violations=1 legacy=0`（= 基线，任务开始时 `HEAD` 与 `46917be796` 均为 1；**修复前为 2**）、`UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28`、`UI_TOKEN_STATUS PASS` |
| 空白 / 冲突 | `git diff --check` = 0、`git diff --cached --check` = 0、无冲突标记 |
| 资源可解析 | `adapter_vod.xml` XML 解析通过 |

### 合并前基线（用于证明「零新增回归」）

| 基线 | mobile | leanback |
| --- | --- | --- |
| 任务开始 `HEAD`（**Windows CRLF 检出**） | 4919 tests / **6 failures** | 4078 tests / **6 failures** |
| 同一 `HEAD`（**LF 检出**） | 4919 tests / 1 failure | 4078 tests / 2 failures |
| 合并后（LF 检出） | **4920 tests / 0 failures** | **4079 tests / 0 failures** |

- CRLF 基线的 12 个失败**全部**是 `Files.readString` 读到 CRLF、而断言里写死 `\n` 造成的：逐条核对为 `TmdbSourceOnlyInteractionTest:42`、`PlayerPlaybackRegressionSourceTest:320`、`ReaderPlaybackRoutingSourceTest:367/382`、`WebThemeTokenSourceTest:64`、`NativeEnhancedPlaybackStyleFocusTest:179`、`SearchResultDownFocusTest:85`，并用脚本模拟 Java `contains` 复现「原始字节 False / 归一化后 True」。
- LF 基线残留的 `RealtimeSubtitleTranslatorTest`（`resetSuppressesInFlightTranslation`、`boundedQueuePreservesOrderAndDropsOnlyOldestOverflow`）为**既有计时抖动**：同一代码隔离重跑 2 次，第 1 次 mobile 失败、第 2 次双 flavor 全通过，故与本次合并无关。
- 合并后测试数 +1/flavor 来自 beta 合入的新用例，失败数少于两条基线，**零新增回归**成立。

### 环境说明（重要，非代码问题）

本机 Git 系统级配置 `G:/Git/etc/gitconfig` 中 `core.autocrlf=true`，索引内为 LF、工作区为 CRLF。源码扫描类用例断言里带 `\n` 字面量，因此在 CRLF 检出下必然失败（与本次改动无关，四个 dev 工作区一致）。本任务为取得有效验证，临时以 `core.autocrlf=false` + `git checkout-index`（配合 touch 失效 stat 缓存）把工作区归一化为 LF 后运行全部验证；文档类结论不受影响，提交内容始终由索引（LF）决定。若希望本机源码扫描用例通过，可执行 `git config core.autocrlf false` 后重新检出，或为仓库加入 `* text=auto eol=lf` 的 `.gitattributes`。

## 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `a51e6f366f2f50048f95d6c383f816710ebd1a62`（第一父 `0582c722216a39854c9d9421af25297bffc80daa`，第二父 `4174c65ea3dd30c9ee9ebb55db64839970d85a29`） |
| recovery tag | `recovery/C49-beta-merge-review-dev2/20261008140803-a51e6f366f2f` |
| 分支推送 | `origin/dev2` = `a51e6f366f`（推送后 `git fetch` 复核 `origin/beta` tip 未前进，仍为 `4174c65ea3`） |
| PR | `#423`：base `beta` / head `dev2` / state `OPEN` / `mergedAt=null` / 文件 3 个（与 `git diff --name-status origin/beta HEAD` 逐项一致） |
| PR 性质 | **只创建，不合并**（未开启自动合并，未手动合并） |
| 本文件提交 | docs-only 收口提交，仅用于记录以上提交 / tag / PR 坐标，无任何再次构建或验证 |
