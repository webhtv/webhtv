# C51：dev2 合并远端 beta 最新代码并循环评审已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev2`（**远端已移除/回退的提交不得顺带带回**）；复评 dev2 全部已修改代码（含已提交未推送的 `c5912111a4`）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev2`、创建 `dev2 → beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并提交第二父为 `origin/beta` tip `75735b58f6`；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev2 既有改动**零丢失**（逐 blob 一致）；④ 双 flavor 主代码与 AndroidTest Java 编译通过；⑤ 定向回归用例（两个 flavor 的被改动面）零失败；⑥ UI token 门禁相对合并前基线**零新增违规**；⑦ 净差异只含本分支自身改动（4 路径）与任务文档；⑧ 提交 + recovery tag；⑨ `dev2` 已推送、PR 已创建且**未合并**。
- **lane / scope**：`standard`；`app/`、`docs/`。
- **任务守卫**：`C51-beta-merge-review-dev2-20261009`；任务开始 HEAD `c5912111a4ab791dc2403a031c91e5e803eb7d02`（相对 `origin/dev2` 领先 8 个提交，但相对与 beta 的合并基点只领先 1 个提交即 `c5912111a4`）；初始工作区干净（0 个受保护脏路径）。
- **当前状态**：合并完成（**0 冲突**）；3 轮评审完成，**发现并修复 1 个必修问题**（任务文档历史引用不实）；全部验证通过；合并提交 `1f8fc83369` 与 recovery tag 已生成并推送；PR #426 已创建（OPEN / 未合并）；本文件已由 docs-only 收口提交记录交付坐标。
- **下一动作**：无（已交付）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始时 HEAD | `c5912111a4ab791dc2403a031c91e5e803eb7d02`（首页菜单键弹窗对比度修复，**已提交未推送**） |
| 合并基点（merge-base） | `7df98107befd6bdd49f7aae5c7fc9c1efeb19b34`（Merge PR #424，已在 beta 上） |
| 合并基点之后的本地提交 | 仅 `c5912111a4`（1 个） |
| `origin/beta` tip（合并前 = 合并后 = 提交前复核） | `75735b58f6f6eb357a0c0b1574da0c10f8864a4f`（Merge PR #425 from dev4） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`，由 task_guard `finish` 创建合并提交（HEAD 在 finish 前保持 `c5912111a4`） |
| 合并结果 | 7 路径自动合入，**0 冲突、0 冲突标记**（对改动路径逐个 `grep -E '^(<<<<<<<\|=======\|>>>>>>>)'` 无命中；`git grep` 全库命中的 `===` 行全部来自 `app/src/main/assets/cacert.pem` 等既有资产文本，非合并产物） |
| 合并结果树（索引） | `9c418730cd099c312853fe471f9071aeae3a5fe4`（文档更正只改工作区未暂存文件，索引树不变） |
| `MERGE_HEAD` | `75735b58f6f6eb357a0c0b1574da0c10f8864a4f` = `origin/beta` tip ✅ |
| 初始脏路径 | 无（`git status --porcelain` 干净） |
| 回滚锚点 | `c5912111a4ab791dc2403a031c91e5e803eb7d02` |
| 任务守卫 | `C51-beta-merge-review-dev2-20261009`（standard，scope `app` + `docs`） |

### beta 增量 ledger（5 个提交 / 7 路径，全部纳入）

`git log --oneline 7df98107be..origin/beta`：

| 完整 commit ID | 标题 | 实质增量 |
| --- | --- | --- |
| `23e5280f37d90e62aa6b20bf3d3fc15cef62f010` | 修复：拖拽进度时加载中/网速大概率不显示 | `app/src/leanback/.../VideoActivity.java`、`app/src/mobile/.../VideoActivity.java`、`app/src/test/.../C47SeekLoadingProgressSourceTest.java`、`app/src/testMobile/.../VideoActivityLayoutTest.java`、`docs/C47-seek-loading-progress.md`、`docs/upstream-player-dependency-merge-assessment-2026-08-20.md`（+1 行 C47 索引行） |
| `1a4110178fccddc656122e08fc8d3a056ae5e2f2` | docs(c47): 记录模拟器验收证据与精度边界 | 追加 `docs/C47-seek-loading-progress.md`（docs-only） |
| `8dd441b1ccc3a65d5486be09de96b6b996e471c1` | merge: 合并 origin/beta 最新代码（PR#424 实验室配置源弹窗）并复评 C47 seek 加载圈窗口 | 合并提交（第二父 `7df98107be`），相对第一父无新内容 |
| `61e483011ed43f836df7df7c12f097171bebbc08` | docs(c49): 记录 dev4 交付坐标 | 新增 `docs/C49-beta-merge-review-dev4-20261008.md` |
| `75735b58f6f6eb357a0c0b1574da0c10f8864a4f` | Merge pull request #425 from Silent1566/dev4 | 合并提交 |

合并后 `git diff --cached --name-status`（= 合并结果相对本地 HEAD 的增量）= **7 路径**：
`M` 两个 `VideoActivity.java`、`A C47SeekLoadingProgressSourceTest.java`、`M VideoActivityLayoutTest.java`、
`A docs/C47-seek-loading-progress.md`、`A docs/C49-beta-merge-review-dev4-20261008.md`、
`M docs/upstream-player-dependency-merge-assessment-2026-08-20.md`（+1 行 C47 索引行）。

### 本分支自身改动（相对 beta 的净差异，4 路径）

| 路径 | 性质 | 说明 |
| --- | --- | --- |
| `app/src/leanback/res/color/home_menu_text.xml` | A | `c5912111a4` 新增：焦点/按下 `?attr/colorOnPrimary`、常态 `?attr/colorOnSurface` |
| `app/src/leanback/res/layout/adapter_home_menu.xml` | M | `c5912111a4`：`android:textColor` 由 `@color/config_history_text` 改为 `@color/home_menu_text`（唯一改动行） |
| `app/src/testLeanback/java/com/fongmi/android/tv/ui/dialog/HomeMenuDialogContrastTest.java` | A | `c5912111a4` 新增：角色接线断言 + 两套 token 表对比度断言 |
| `docs/TV-HOME-MENU-CONTRAST-20261009.md` | A | 该修复的任务文档；本轮复评更正了 §2 的一处历史引用（见「循环评审记录」第 2 轮与 §8） |

`git diff --name-status origin/beta` 与上表逐项一致（A/M 状态亦一致），无多余路径、无删除路径。

## 零复活 / 零丢失证据

采用仓库既有口径，全部基于不可变 ref 与整树比对，不对单路径做 `git cat-file -e`（MSYS 会把 `/` 当路径分隔符导致误判）：

1. 固化 ref：`origin/beta` = `75735b58f6f6eb357a0c0b1574da0c10f8864a4f`；用 `git ls-tree -r --name-only` 取 beta 树与合并树（索引）的完整路径集合：**beta 4980 / 合并树 4983**，用集合差比对。
2. 复活提交集只用**主题行且锚定开头**：`git log --all --no-merges --format='%H%x09%s'` + `awk '$2 ~ /^(Revert|revert|回退|撤销|剔除)/'` → **182** 个单亲 revert 类提交，触碰路径去重 **852** 个（与 C49 记录的 182 / 852 完全一致，说明该口径在本仓库可复现）。
3. 「beta 已移除路径」= 被 revert 触碰且不在 beta 树中 → **671** 个（与 C49 记录的 671 一致）；与合并树求交 → **复活 0 个**。
4. 零丢失：beta 树 4980 条路径在合并树中缺失 **0** 条；合并树多出的 3 条路径恰为本分支新增的 3 个文件，索引树路径集合差 = `git diff --name-status origin/beta` 的 4 路径（其余 1 条为修改）。
5. **可证伪自检**：把确证「在 revert 集且不在 beta 树」的路径 `ISSUE_TEMPLATE` 注入伪造合并树，检测器**报出该路径**；移出后对真实合并树报 **0**，证明检测器非空转。
6. 内容一致性（逐 blob）：beta 增量的 7 路径在合并树中的 blob 与 `origin/beta` **逐字节相同**（7/7 OK）；本地 4 路径的 blob 与任务开始 `HEAD c5912111a4` **逐字节相同**（4/4 OK），即合并未改写任何一方内容。
7. 改动路径无冲突标记：对 11 个改动路径逐个扫描 `<<<<<<<` / `=======` / `>>>>>>>` 行首标记，**0 命中**；`git diff --check` 与 `git diff --cached --check` 退出码均为 0。

## 循环评审记录

评审范围 = 本任务全部「已修改代码」：beta 增量 7 路径 + dev2 已提交未推送的 `c5912111a4` 的 4 路径 + 两者的交互。

### 第 1 轮（合并正确性与代码语义）

| 对象 | 结论 | 依据 |
| --- | --- | --- |
| 合并结果本身 | 通过 | 0 冲突；`MERGE_HEAD` = `origin/beta` tip；7 路径全部来自 beta（对 `HEAD` 无本地改写）；beta 与本地两方 blob 逐字节保留（证据见上节第 6 条）。 |
| beta 增量 `23e5280f37`（C47 seek 加载圈窗口，两个 `VideoActivity.java`） | 通过 | 逐段读代码核对：`onSeekStarted()` 先开窗口（`mSeekProgressPending = true` + 记录 `SystemClock.elapsedRealtime()`）→ 再 `showProgress()`（其内部立刻投递网速 ticker）→ 最后挂 `SEEK_PROGRESS_MIN_VISIBLE_MS=1200L` 计时器；`canHideSeekProgress()` 在窗口未关时要求已过最小可见时长且播放器 READY 且 `!isLoading() \|\| isPlaying()`；收口路径（`hideSeekProgressIfReady`、网速 ticker 的 `hidePlaybackProgressIfStale`、`onStateChanged(STATE_READY)`、`onControllerReadyReconciled`）统一受该闸门约束；`hideProgress()` 显式关窗且先关窗后摘回调；`showProgress()` 在窗口开着时不摘计时器。逻辑自洽、无未收口路径（窗口开着时计时器与网速 ticker 都在重判，不会自激）。 |
| beta 增量 `C47SeekLoadingProgressSourceTest`（5 项）与 `VideoActivityLayoutTest`（seek 段落） | 通过 | 断言均为「窗口先于 showProgress 打开」「最小可见 ≥ 1000ms」「四条收口路径都过闸门」等源码契约；`bodyToBrace`/`indexOf` 口径对 CRLF/LF 均成立（本机为 CRLF 检出，实测通过）。 |
| beta 增量文档 2 份 + 评估索引 1 行 | 通过 | 均为任务记录与索引，不引用已回退内容（复活扫描已覆盖其路径），不影响构建与运行行为。 |
| dev2 自有改动 `home_menu_text.xml` / `adapter_home_menu.xml`（`c5912111a4`） | 通过 | 唯一消费者是 `HomeMenuDialog`（`AdapterHomeMenuBinding` 只在该类与其布局出现）；条目背景固定为 `selector_config_history_item`，其焦点/按下项都是 `shape_config_history_item_focused` 的实心填充，因此焦点文字取 `colorOnPrimary` 与焦点填充 `colorPrimary` 不同角色；常态 `colorOnSurface` 与旧 `config_history_text` 的常态项完全一致（正常项零视觉变化）；`?attr/colorOnPrimary` / `?attr/colorOnSurface` 在 `Theme.WebHTV`（第 8/29 行）与 `ThemeOverlay.WebHTV.Dialog`（弹窗真实上下文）中都映射到 `webhtv_color_on_primary` / `webhtv_color_on_surface`，与测试解析的映射同源。 |
| 对比度数字独立复算 | 通过 | 不依赖被测代码，用 Python 按 WCAG 相对亮度重算：焦点 `#FFFFFF` on `#0B57D0` = **6.39**、`#062E6F` on `#A8C7FA` = **7.50**、常态 `#1A1C1E` on `#ECEEF4` = **14.73**、`#E2E2E9` on `#1F2428` = **12.14**、旧写法 `colorPrimary` on `colorPrimary` = **1.00**、设备自定义主题实测 `#006876` on `#0B57D0` = **1.01** —— 与文档 §6.1/§6.3 逐个一致。 |
| 两个改动的交互 | 通过 | 路径集合不相交（本分支只碰 leanback `res/color`、`res/layout`、`testLeanback`、`docs`；beta 只碰两个 `VideoActivity.java`、`app/src/test`、`testMobile`、`docs`），且功能面无交集（弹窗配色 vs 播放 seek 时序）；不存在共享符号、共享资源或先后依赖。 |
| UI token 门禁 | 通过（需在第 2 轮补基线对照） | 合并后 `scripts/check_ui_tokens.sh` = `violations=1 legacy=0`、`hex_layouts=1`、`pairs=38 failures=0 min=4.28`、`PASS`；`app/src/leanback/res/layout/adapter_home_menu.xml` 无裸色值（`grep -c` = 0）。 |
| **第 1 轮代码层结论** | **无必修问题** | 没有发现行为回归、契约破坏、对比度不达标、门禁新增违规或合并错并。 |

### 第 2 轮（交付文档事实性逐条核对）

| 对象 | 结论 | 依据 |
| --- | --- | --- |
| 门禁基线对照（补第 1 轮） | 通过 | 用 `git archive c5912111a4`（合并前 HEAD）导出 `app/src/{main,mobile,leanback}/res`、`assets`、`docs/ui-token-allowlist.txt` 到临时目录，以同一脚本跑基线 → `layouts=385 hex_layouts=1 drawables=554 hex_drawables=0 colors=59 hex_colors=0 allowlisted=190`、`violations=1 legacy=0`、`pairs=38 failures=0 min=4.28`、`PASS`；与合并后**逐项相同** → 零新增违规。基线数 385 也说明本次没有新增会被扫描的布局文件。 |
| 文档 §2「本仓库 `adapter_home_menu.xml` / `config_history_text.xml` / `shape_config_history_item_focused.xml`」引用 | 通过 | HEAD 上的三处接线与描述一致（焦点文字/填充同为 `?attr/colorPrimary`）。 |
| 文档 §2「本仓库 git 历史」引用 | **不通过 → 已最小修复** | 原表述为「`0503f8e3cf` 把焦点色从 `@color/white` 改成 `?attr/colorPrimary`；上游原意是白字压在蓝色实心胶囊上（`161b896b25` 之前 `adapter_home_menu.xml` 直接写 `@color/white`）」。核对：① `git show 161b896b25^:app/src/leanback/res/layout/adapter_home_menu.xml` → `fatal: ... not in '161b896b25^'`，该布局由 `e95b90d3a8`（2026-08-23）创建；② `git log --all -S'@color/white' -- app/src/leanback/res/layout/adapter_home_menu.xml` → **空**，该文件从未写过 `@color/white`；③ 真实链条是 `0503f8e3cf`（2026-09-14）把焦点文字改为 `?attr/colorPrimary`（当时填充仍是 `#2F6FED`，见 `git show 0503f8e3cf^:<shape>`），`1bb72bd709`（2026-09-26 fix(tv): keep config action focus columns）把填充由 `#2F6FED` 改为 `?attr/colorPrimary`、描边由 `#D7E5FF` 改为 `?attr/colorOnPrimary`，两者叠加才成为「同一角色压自身」。修复方式为把该行改写为可逐条验证的准确表述，并在文末新增 §8 记录更正与再验证；**不改任何代码与测试**。 |
| 文档 §2 `ThemeBinder`/`ThemeColorIndex` 引用 | 通过 | `ThemeBinder.bindDrawable()` 只解包 `InsetDrawable`、处理 `GradientDrawable`/`MaterialShapeDrawable`，无 `StateListDrawable` 分支（源码行 320–335 逐条核对）。 |
| 文档 §2 `config_history_icon.xml` / `site_item_text.xml` 引用 | 通过 | 当前 HEAD 上两者焦点项均为 `?attr/colorOnPrimary`。历史核对：`config_history_icon.xml` 的焦点色由 `0503f8e3cf` 设为 `?attr/colorPrimary`、再由 `1bb72bd709`（2026-09-26 `fix(tv): keep config action focus columns`，其同一提交把 `shape_config_history_item_focused` 填充改为 `?attr/colorPrimary`）改成 `?attr/colorOnPrimary`。也就是说「填充为 `colorPrimary` 时前景必须 `colorOnPrimary`」的约定自 `1bb72bd709` 起已落地，只是当时没有一并改 `config_history_text`（它同时服务 `primaryContainer` 描边按钮）；本文「沿用同弹窗家族已有约定」的表述成立，且本次修复正是补齐这条约定的遗漏项。 |
| 文档 §2 `dialog_outlined_button_bg/text.xml` + `dialog_history.xml` 引用 | 通过 | `app/src/main/res/color/dialog_outlined_button_bg.xml` 的焦点/按下项确为 `@color/webhtv_color_primary_container`，`dialog_history.xml` 第 30 行确用 `@color/config_history_text` → 共享选择器不能改成 `colorOnPrimary` 的结论成立。 |
| 文档 §7 相邻问题引用 | 通过 | `site_action_icon.xml` 焦点色 `?attr/colorPrimary`，其消费者 `selector_site_action.xml` 的焦点/按下/选中项都是 `shape_config_history_item_focused`（填充 `?attr/colorPrimary`）→ 同类缺陷描述成立。 |
| **第 2 轮结论** | **1 个必修问题（文档不实描述）已修复** | 修复后该行每一处事实均可由 `git log`/`git show` 复现；`git diff` 证明只改了文档表述（`docs/TV-HOME-MENU-CONTRAST-20261009.md`：10 插入 1 删除），代码与测试零改动。 |

### 第 3 轮（修复后复评）

| 对象 | 结论 | 依据 |
| --- | --- | --- |
| 更正后的 §2 行与新增 §8 | 通过 | §8 中给出的每条命令与结论都可直接复现（`git log --all --full-history -- <layout>`、`git show 161b896b25^:<layout>`、`git show 0503f8e3cf:<color>`、`git show 1bb72bd709 -- <shape>`）；§8 未写入任何未经核对的提交号或结论。 |
| 净差异是否因修复而变化 | 通过 | `git diff --name-status origin/beta` 仍为同样 4 路径（3 A + 1 M），修复只改其中 1 个已存在路径的内容，未新增路径、未触碰 `app/`。 |
| 空白/冲突/门禁 | 通过 | `git diff --check` 与 `git diff --cached --check` 退出码 0；门禁在修复后重跑仍为基线值（violations=1、failures=0、PASS）；文档为 markdown，不进入门禁扫描面。 |
| 事实修正是否影响既有验证有效性 | 通过 | 第 1 轮的全部代码结论与验证（编译、定向用例、对比度复算、复活扫描）都不依赖被更正的那句表述；更正后这些证据仍然逐项成立，且 `c5912111a4` 的实际代码路径与设备实测（前序任务记录，本分支 blob 未变）不受影响。 |
| **第 3 轮结论** | **通过，无遗留必修问题** | 三轮评审：第 1 轮查代码与合并正确性（0 问题）→ 第 2 轮查交付文档事实性（1 问题，已修复）→ 第 3 轮复核修复并确认净差异与证据链未受污染。 |

## 验证证据

全部命令在 `F:/Workspace/webtv2/webhtv`（dev2）执行；Gradle 使用 `JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`。

| 项目 | 命令 / 结果 |
| --- | --- |
| 双 flavor 主代码编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` → 成功 |
| 双 flavor AndroidTest Java 编译 | `:app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac` → 成功 |
| 合并后一次构建的全部任务 | 上述 4 个编译任务 + `:app:testMobileArm64_v8aDebugUnitTest`（过滤）合并为一次调用 → `BUILD SUCCESSFUL in 4m 54s`（39 executed / 25 from cache / 80 up-to-date，无 FAILED） |
| mobile 定向用例 | `C47SeekLoadingProgressSourceTest` **5/5**、`VideoActivityLayoutTest` **154/154**（XML 报告 `failures=0 errors=0`） |
| leanback 定向用例 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests '…HomeMenuDialog*' --tests '…theme.*' --tests '…C47SeekLoadingProgressSourceTest'` → `BUILD SUCCESSFUL in 1m`；报告合计 **175 项 / 0 失败 / 0 错误**，其中 `theme.*` 23 个类 162 项、`HomeMenuDialogContrastTest` 3/3、`HomeMenuDialogSourceTest` 5/5、`C47SeekLoadingProgressSourceTest` 5/5 |
| UI token 门禁（基线 vs 合并后） | 两处均为 `layouts=385 hex_layouts=1 drawables=554 hex_drawables=0 colors=59 hex_colors=0 allowlisted=190`；`violations=1 legacy=0`；`pairs=38 failures=0 min=4.28`；`UI_TOKEN_STATUS PASS` |
| 净差异 / 冲突 / 空白 | `git diff --name-status origin/beta` = 4 路径；11 个改动路径无冲突标记；`git diff --check` = 0、`git diff --cached --check` = 0 |
| 提交前 beta 未前进 | 创建合并提交前再次 `git fetch origin` → `origin/beta` 仍为 `75735b58f6f6eb357a0c0b1574da0c10f8864a4f` |
| 设备验证 | 未使用模拟器/设备：本分支净差异的 4 个路径 blob 与 `c5912111a4` 逐字节相同（前序任务已在 `192.168.50.3:5557` 实测 1.01:1 → 6.39:1 并记录），重复实机验证不会产生新信息；合并带入的 beta 代码面不在本分支净差异内。 |

### 环境说明（本机 CRLF 检出，非代码问题）

本机系统级 `core.autocrlf=true`（`G:/Git/etc/gitconfig`），索引内为 LF、常规检出为 CRLF；少数源码扫描类用例断言里带 `\n` 字面量，在 CRLF 检出下会因 `\r\n` 误判（仓库既有问题，四个 dev 工作区一致）。本任务为排除这一干扰，先在不改任何 git 配置的前提下用 `git -c core.autocrlf=false checkout-index -f`（配合 `touch` 失效 stat 缓存）把工作区临时归一化为 LF，再用 `git checkout-index -f -a`（按系统配置）还原为本机原本的 CRLF 检出；两次转换后都用 `git add` + `git hash-object --path=<file>` 与索引 blob 逐文件比对，确认 EOL 转换**从未改写索引内容**（`git status --porcelain` 最终只剩本次任务应有的条目）。**最终全部验证是在与任务开始时相同的 CRLF 检出下完成的**（构建前已还原，`git ls-files --eol` 复核 `w/crlf`），因此与合并前基线可逐项对比；本任务运行的定向用例实测在 CRLF 下全部通过。

## 相邻但未修复（超出本次范围，仅报告）

1. `scripts/check_ui_tokens.sh` 的裸色值扫描根只包含 `app/src/main/res/color`，不包含 `app/src/{mobile,leanback}/res/color`（实测 `colors=59` 恰等于 `app/src/main/res/color/*.xml` 文件数）。因此 `app/src/leanback/res/color/` 下 17 个文件（含本次新增的 `home_menu_text.xml` 与既有 `bg_remark.xml`、`bg_site.xml`、`bg_year.xml`、`exit_confirm_secondary_text.xml` 等 4 个已含裸色值的文件）都不在门禁覆盖内。这是门禁覆盖面的既有限制、且与本次改动无关（既有 4 个文件已是同样写法），建议单独建任务决定是否扩大扫描根。
2. `site_action_icon.xml` + `selector_site_action.xml`（`dialog_site` 动作按钮）与 `config_history_text.xml` 的双用途问题（文档 §7 第 2、3 条）仍存在；它们不在本任务净差异内，本次只报告不修改。

## 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `1f8fc83369dbd60703b5d72305e507cd3064a6f9`（第一父 `c5912111a4ab791dc2403a031c91e5e803eb7d02`，第二父 `75735b58f6f6eb357a0c0b1574da0c10f8864a4f` = 合并前 `origin/beta` tip） |
| recovery tag | `recovery/C51-beta-merge-review-dev2-20261009/20261009033846-1f8fc83369db`（annotated；`git ls-remote` 复核该 tag 的 `^{}` 指向 `1f8fc83369`，tag 创建阶段耗时 0s） |
| 分支推送 | `origin/dev2` = `1f8fc83369`（`git ls-remote` 复核一致；推送后再次复核 `origin/beta` 仍为 `75735b58f6`，未前进） |
| PR | `#426`：base `beta` / head `dev2` / state `OPEN` / `mergedAt=null` / `mergeable=MERGEABLE` / 非 draft；PR 文件集为 6 项（含本文件）与 `git diff --name-status origin/beta HEAD` 逐项一致 |
| PR 性质 | **只创建，不合并**（未开启自动合并、未手动合并） |
| 资源回收 | 末次构建后已执行 `gradlew --no-daemon clean`（`BUILD SUCCESSFUL in 1m 12s`），`app/build`、`catvod/build`、`quickjs/build`、`chaquo/build`、`nodejs/build` 已删除 |
| 本文件提交 | docs-only 收口提交，仅用于记录以上提交 / tag / PR 坐标，无任何再次构建或验证 |
