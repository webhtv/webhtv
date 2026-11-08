# C28：dev1 合并远端 beta 最新代码并循环复评未推送改动

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入本地 `dev1`（且不得把远端已移除/回退的提交重新带上来），循环复评相对 `origin/beta` 的全部已修改代码（含已提交未推送的 3 个提交），发现问题则最小修复并验证通过，通过后再次复评，直到通过；最后提交本任务改动、推送 `dev1`、创建 `dev1 -> beta` 的中文 PR（只创建，不合并，不擅自通过）。
- **验收**：① 合并结果包含复评时刻的 `origin/beta` tip；② 被远端剔除的提交不在结果祖先中，且远端删除/回退的功能未被带回；③ `dev1` 相对 `origin/beta` 的净差异只含本分支自身改动；④ 定向单测、Leanback 全量单测、双 flavor Java 编译、Debug 打包覆盖安装、设备端到端焦点链实测全部通过；⑤ PR 描述为中文、说明改动内容且排版清楚。
- **允许路径**：`docs/C28-beta-merge-review-dev1-20260930.md`、以及净差异的 7 个文件（见“PR 边界”）。
- **分支/HEAD**：`dev1`；任务开始时 HEAD `5026e2147f47bad56d196a66b6a084d019f03655`。
- **保护面**：任务开始时工作区干净（`git status --porcelain` 无输出），无受保护脏文件。
- **当前状态**：合并台账核对、2 轮复评、修复、单测/编译/打包/设备实测全部完成；`origin/beta` 的合并状态已建立（MERGE_HEAD = `8f7376295`，父 1 = 本地 `dev1` HEAD），进入收尾提交与 PR。
- **下一动作**：以一次 `task_guard.sh finish` 提交（含合并父节点）+ 打恢复标签，推送 `dev1`，创建 `dev1 -> beta` 的中文 PR（只创建，不合并）。

## 时间与设备

- 本地时间：2026-09-30 13:45–14:25（Asia/Shanghai）。
- 设备：`192.168.50.3:5555`（dev1 分配机位，Android 9 / sdk 28）；未占用 dev2/dev3/dev4 机位。
- WebHTV 打包：`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5555`（Debug，覆盖安装，未卸载）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1` |
| 任务开始时 HEAD | `5026e2147f47bad56d196a66b6a084d019f03655` |
| 复评时刻 `origin/beta` HEAD | `8f7376295018e63747714b853f987550f9adae3f` |
| `origin/dev1` HEAD | `15b738cc830a3d2eed4a9209fa8a123d2a09dc16` |
| `dev1` 与 `origin/beta` 合并基点 | `15b738cc830a3d2eed4a9209fa8a123d2a09dc16` |
| `origin/beta..dev1` 提交数 | 3（全部未推送） |
| `dev1..origin/beta` 提交数（合并前） | 1（`8f7376295`，即 `origin/beta` 的 tip） |
| 被剔除提交 `5682f2b054…` 是否为 `origin/beta` 祖先 | **否**（`git merge-base --is-ancestor` 判定失败），未随本次合并带回 |
| 合并提交父节点 | 父 1 `5026e2147f47bad56d196a66b6a084d019f03655`（本地 `dev1`）、父 2 `8f7376295018e63747714b853f987550f9adae3f`（远端 `origin/beta` tip） |

- **远端 beta 已完整合入**：执行 `git merge --no-commit --no-ff origin/beta`，输出“自动合并进展顺利”，无冲突、无冲突标记残留；合并提交固定为**本地 `dev1` 与远端 `origin/beta` 两个父节点**，与既有约定一致。
- **合并未改变任何文件内容**：`origin/beta` 的 tip `8f7376295`（Merge PR #385）只把 `dev1` 本地已有的 `15b738cc8` 作为第二父提交，没有引入任何 `dev1` 缺失的内容，因此合并后 `index` 与 `HEAD` 的差异文件数为 **0**、暂存区零变更；合并后的工作树指纹与合并前逐字节一致（`git hash-object` 三处修复文件指纹不变），故**阶段 1 的验证结论（定向单测 / 全量单测 / 打包 / 设备实测）在合并后继续有效，无需重复执行**。
- 合并动作与本次复评修复、任务文档合成为**一个原子提交**（同一 guard 会话、一次 `finish` 提交并打恢复标签）。
- **`dev1` 相对 `origin/beta` 的净差异只有本分支自身 3 个未推送提交**：
  - `4432937d2f3c13f1699d247097ea79af672bd88b` fix(tv): highlight enhanced media focus
  - `e82cdce5cf5d9f69c6de6a1b3c4932b9da2309b3` fix(tv): restore related video focus highlight and row focus chain
  - `5026e2147f47bad56d196a66b6a084d019f03655` fix(tv): restore poster row height and TMDB row focus chain
- **回退/剔除提交核对（两层）**：
  1. `5682f2b054b577b0db5c6f7c1143f2eebb51858e`（“剔除 PR #353 主题系统改动”）**不是** `origin/beta` 的祖先；`dev1` 相对 `origin/beta` 的净差异中不含任何主题系统文件（`theme`/`appearance` 匹配为空）。
  2. `git diff --diff-filter=D --name-only origin/beta..dev1` 为空——即远端 `beta` 删除过的任何文件都没有被本次净差异带回来；反过来再确认净差异里的 4 个 “? 缺失文件” 全部是 **dev1 新增**（`TmdbRowFocusChain.java`、`selector_tmdb_media_focus.xml`、`TmdbRowFocusChainTest.java`），不是被远端删除后又被恢复的内容。

## 评审记录

### 第 1 轮：改动本体 + 测试有效性 + 设备端到端行为

复评范围：`dev1` 相对 `origin/beta` 的净差异（3 个提交、7 个文件、+419/−19）。

- **`VideoActivity.applyTmdbRowFocusChain()`**：改为“先算 `present[]`，再按 `TmdbRowFocusChain.link()` 取上下邻居，被隐藏/空行整体跳过”；`installRowCardFocusLinks()` 用 `OnChildAttachStateChangeListener` 在卡片重新接入时补写自身 `nextFocusUp/Down`，解决“焦点在卡片上时 Android 优先读卡片自身声明”的问题。核对了 `HorizontalGridView.getNextFocusUpId()/getNextFocusDownId()` 的读取时机：`grid.addOnChildAttachStateChangeListener` 的 `onChildViewAttachedToWindow` 在容器属性写入之后触发（`applyTmdbRowFocusChain` 在所有绑定点末尾调用），顺序正确。
- **`tmdbMediaRows()` 顺序**：与 `getEpisodeFocusOrders()` 中 `tmdbOmdbRatings -> tmdbCast -> tmdbPhotos -> tmdbPosters -> tmdbRelatedVideos -> tmdbCrew -> tmdbRecommendations -> 个性×3` 完全一致，未与既有纵向链冲突。
- **`TmdbRowFocusChain.link()`**：`present == null` 返回空数组；`result[i][0..1]` 先统一铺 `NO_NEIGHBOR` 再按 `active` 列表串联，规避了 Java `int` 默认值 0 被误当成“第 0 行”的坑；不修改入参。10 个用例覆盖 null/空/全存在/跳行/连续跳行/首行缺失/末行缺失/单行/全缺失/长度/不可变。
- **`TmdbPhotoPresenter` / `TmdbVideoPresenter` 焦点外观**：两者都改为 `setForeground(selector_tmdb_media_focus)` + `setRippleColor(透明)` + `setStateListAnimator(null)` + `setDefaultFocusHighlightEnabled(false)`（API ≥ O 守卫），并**按当前真实焦点态补一次**（`card.hasFocus()`），避免 RecyclerView 复用后“停下”的卡片看不到焦点环；`TmdbVideoPresenter.onUnbindViewHolder` 增加了 `animate().cancel()` + 复位 `scale/foreground/activated`，无资源泄漏或状态串味。`selector_tmdb_media_focus.xml` 的 `state_activated` 分支与 `applyFocusStyle` 的 `setActivated(focused)` 语义对齐（这是本套 selector 的既有约定），圆角 8dp 与卡片 `app:cardCornerRadius="8dp"` 一致。
- **`NativeEnhancedPlaybackStyleFocusTest`**：4 个新增用例把新契约钉死（必须用前景 selector / 必须在绑定时同步焦点态 / 必须存在 `applyTmdbRowFocusChain` 与卡片级焦点目标 / 9 个 TMDB 行都必须配 `rowHeight` / 海报标签与卡片同源显隐 / 评分行显隐必须重算行链）。有效性核对：旧的 “`STROKE_FOCUSED = 0xFFFFD166` + `STROKE_WIDTH_FOCUSED_DP = 3`” 断言已随实现替换为前景方案同步更新，没有留下“断言旧实现”的假绿；`everyTmdbRowHasRowHeightConfigured` 用 `setupTmdbGridViews()` 方法体切片断言，恢复“海报行不配 rowHeight”会立即失败。
- **发现的问题（本轮修复）**：
  1. 三处中文注释/断言文案把 **“塌陷”误写为“塔陷”**（`VideoActivity.java` 1 处、测试 2 处）——中文错别字，影响可读性；
  2. `app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbRowFocusChainTest.java` 文件**末尾缺少换行**（`\ No newline at end of file`）。
- **第 1 轮结论**：功能与测试逻辑正确，问题仅限文案错别字与文件末尾换行，已做最小修复，无需改动任何行为代码。

### 第 2 轮：修复后终态复评 + 交付边界

- 修复内容复核：仅 3 个文件、+4/−4 行，且全部是注释/断言字符串与一个行尾换行；**无任何可执行语义变化**（`git diff --stat` 确认规模；`git diff --check` 无空白错误）。
- 错别字复检：`grep -rn '塔陷' app/src docs` 与 `git diff origin/beta..dev1 | grep '塔陷'` 均为空，说明工作区与净差异都已清除，没有“只改工作区、净差异仍残留旧文案”的问题。
- 行尾复检：净差异 7 个文件（含 3 个新增文件）末尾字节全部为 `0a`。
- 测试有效性再确认：定向用例在修复后重新执行（非沿用旧结果），仍通过。
- 交付边界：净差异只有 7 个文件，无 `app/build/**` 产物、无 `/tmp` 验证脚本或截图、无其它任务的文档被纳入；本任务文档为本轮唯一新增的 `docs/` 文件。
- 与远端 `beta` 的关系再确认：本分支净差异与 `beta` 侧内容零交集文件（7 个路径全部不在 `origin/beta..origin/dev1` 的历史改动里产生冲突），不会覆盖或回退远端任何已合入改动。
- **第 2 轮结论**：未发现必须修改的问题，改动可交付。

## 验证记录

- **定向单元测试 + 双 flavor Java 编译**：`bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.helper.TmdbRowFocusChainTest' --tests 'com.fongmi.android.tv.ui.activity.NativeEnhancedPlaybackStyleFocusTest' :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac --continue` → `BUILD SUCCESSFUL in 52s`；`TmdbRowFocusChainTest` 10 项、`NativeEnhancedPlaybackStyleFocusTest` 14 项，均 0 failure / 0 error / 0 skipped（XML 结果文件核对）。
- **Leanback 全量单测（回归）**：`bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --continue` → `BUILD SUCCESSFUL in 47s`；汇总 4068 项、skipped 2、**failure 0 / error 0**，无异常测试文件。
- **打包与覆盖安装**：`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5555` → `BUILD SUCCESSFUL in 17s`（129 tasks），APK `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`（196M），`adb install -r` 覆盖安装 `Success`（未卸载）。
- **设备端到端纵向焦点链实测（决定性证据，陈情令 第 1 季 / TMDB 匹配成功）**：逐次 `input keyevent 20`（下）并 `uiautomator dump` 判定焦点落在哪一行：

  | 步骤 | 焦点行 | 说明 |
  | --- | --- | --- |
  | down#9 | `tmdbOmdbRatings` | 评分与数据行可选中（未被跳过） |
  | down#10 | `tmdbCast` | 演员行 |
  | down#11 | `tmdbPhotos` | 剧照行 |
  | down#12 | `tmdbPosters` | **海报行**（此前 rowHeight 塌陷为 0，卡片一张都看不到） |
  | down#13 | `tmdbRelatedVideos` | 相关视频行 |
  | down#14 | `tmdbCrew` | 主创行 |
  | down#15 | `tmdbRecommendations` | 猜你喜欢 |
  | down#16/#17/#18 | `tmdbPersonalTmdb*` / `tmdbPersonalDouban*` | 个性推荐（AI 行无数据被自动跳过） |

  向上链条反向完整：`tmdbPersonalDouban → tmdbPersonalTmdb → tmdbRecommendations → tmdbCrew → tmdbRelatedVideos → tmdbPosters → tmdbPhotos → tmdbCast → tmdbOmdbRatings → 选集网格`，与 `getEpisodeFocusOrders()` 顺序一致；被隐藏/为空的 TMDB 行（相关视频缺失时等）全部自动跳过，未出现焦点停在不可见 View 或退回几何搜索的现象。
- **海报行可见性实测**：`tmdbPostersLabel` 占位 `(48,571)-(120,620)`、`tmdbPosters` 占位 `(0,636)-(1920,1080)`（**h=444**，此前塌陷；截图中海报卡片正常显示），与标签同源显隐。
- **焦点环像素级证据**：镜头三处聚焦卡片，逐像素扫描 `#FFD166`（`rgb(255,209,102)`）：
  - 剧照卡（`tmdbPhotos`）：bbox `x 48–487 / y 546–793`，水平扫描线在 `x=48` 与 `x=482` 各出现 **6px** 连续段（该密度下 3dp）；
  - 海报卡（`tmdbPosters`）：bbox `x 48–343 / y 636–1063`，水平扫描线同样为 6px 连续段，垂直扫描 `x=51` 得到连续 419px 长直边——构成完整矩形环；
  - 相关视频卡（`tmdbRelatedVideos`）：bbox `x 37–610 / y 750–1063`，水平扫描 **6px** 连续段。
  三处均为 3dp 统一焦点环，与 `selector_tmdb_media_focus.xml` 声明一致；截图肉眼可辨聚焦项为黄环、非聚焦项保持细灰描边。
- **稳定性冒烟**：实测期间进程存活（PID 6677），前台 Activity 为 `VideoActivity`；`logcat` 过滤 `FATAL EXCEPTION` / `fatal signal` / `ANR in com.silent.android.webhtv` / `E AndroidRuntime` **无命中**。
- **明确未验证的边界**：未重复执行 Mobile 全量单测（本任务净差异只含 Leanback 播放页与 `app/src/main` 新增 helper/selector，Mobile 侧改动为零；已单独跑通 `compileMobileArm64_v8aDebugJavaWithJavac`）；未做多设备机位矩阵（按仓库约定 dev1 只使用 `192.168.50.3:5555`）；未执行原生/Go/Rust 工具链测试（本任务无 native 改动）。

## 环境备注（与本次改动无关）

- 设备上 `uiautomator dump` 偶发返回空文件，脚本内已做重试（最多 5 次）；`window` 服务 dump 在该模拟器上有既存 hang 问题，本轮未触发。
- 播放地址加载失败提示（`播放地址加载失败`）为该测试片源的既存网络问题，与焦点链改动无关。

## PR 边界

相对 `origin/beta`（`8f7376295018e63747714b853f987550f9adae3f`），`dev1` 净差异为 **7 个文件、419 行新增、19 行删除**：

| 状态 | 文件 |
| --- | --- |
| M | `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` |
| M | `app/src/leanback/java/com/fongmi/android/tv/ui/presenter/TmdbPhotoPresenter.java` |
| M | `app/src/leanback/java/com/fongmi/android/tv/ui/presenter/TmdbVideoPresenter.java` |
| A | `app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbRowFocusChain.java` |
| A | `app/src/main/res/drawable/selector_tmdb_media_focus.xml` |
| A | `app/src/test/java/com/fongmi/android/tv/ui/helper/TmdbRowFocusChainTest.java` |
| M | `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/NativeEnhancedPlaybackStyleFocusTest.java` |

内容为三组已提交未推送的改动：原生增强播放页 TMDB 区块的**焦点环视觉统一**、**相关视频焦点可见性**、**海报行 rowHeight 与整条纵向焦点链**；此外本次交付提交是一个**合并提交**（父 1 = 本地 `dev1` `5026e2147`，父 2 = 远端 `origin/beta` `8f7376295`），使 `dev1` 成为 `origin/beta` 的直接后继，合并进 `beta` 时可直接快进而无额外冲突面。

PR 只做创建，不执行合并。

## 回滚锚点

- 提交前锚点：`dev1 @ 5026e2147f47bad56d196a66b6a084d019f03655`（远端 `origin/dev1 @ 15b738cc8`）。
- 回滚：`git revert <本任务提交>`（改动为纯 UI 焦点/视觉与新增 helper/selector，无偏好键或数据格式变更）。
- 被剔除提交保持非 `origin/beta` 祖先：本次未产生新的合并提交、未把 `5682f2b05`（剔除 PR #353 主题系统改动）或任何历史 revert/reset 提交作为独立改动带入。
