# C28：dev2 合并远端 beta 并复评未推送改动（含 TV 控制栏焦点恢复）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入本地 `dev2`，循环复评相对 `origin/beta` 与相对 `origin/dev2` 的全部已修改代码（含已提交未推送部分），发现问题则最小修复并验证通过；通过后提交、推送并创建中文 PR 到 `beta`，只创建 PR 不合并；不得把远端已移除/回退的提交重新带上来。
- **验收**：合并结果包含远端 `beta` 最新提交；远端已移除的回退提交不在合并结果中，且净差异中不含被回退内容；`dev2` 相对 `origin/beta` 的净差异只包含本分支自身的 3 个焦点修复提交；定向单元测试通过、受影响 flavor 打包通过、覆盖安装后启动无崩溃；PR 描述用中文说明改动内容并保持排版清楚。
- **允许路径**：`docs/C28-beta-merge-review-dev2-20260930.md`（本任务只新增该文档；代码经复评未发现需要修改的问题）。
- **分支/HEAD**：`dev2`；本任务开始时 HEAD = `678a7d148391b4721c1bf0f697f4dc313b8709bb`（本地 9 个未推送提交）。
- **保护面**：任务开始前仓库根目录的 9 个未跟踪验证产物（`.tmp-task/` 下的 `progress.txt`、`logs/*.txt`）均未纳入本任务。
- **当前状态**：合并、2 轮复评、定向验证、打包覆盖安装与启动冒烟均已完成，无需要修改的问题，进入收尾提交与 PR。
- **下一动作**：提交本任务文档，推送 `dev2`，创建 `dev2 -> beta` 的中文 PR（只创建，不合并）。

## 时间与设备

- 当前本地时间：2026-09-30 14:46（Asia/Shanghai）。
- 设备：`emulator-5556`（Leanback 包）、`emulator-5554`／`emulator-5558`（Mobile 包）；本任务只做覆盖安装与启动冒烟，全程 `adb install -r`，未卸载现有包。

## 基线与合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 合并前 HEAD | `678a7d148391b4721c1bf0f697f4dc313b8709bb` |
| 远端 `origin/beta` HEAD | `8f7376295018e63747714b853f987550f9adae3f` |
| 远端 `origin/dev2` HEAD | `7465b91f0bc1591ffdb83e2e8fa49f491db32a91` |
| 合并基点（合并前） | `09f8882251bff5fae9da2d87341242bd359ad4aa` |
| 合并提交 | `c14d146f0b1440b45ce734ce984acc85e450941b`（父：`678a7d1483`、`8f73762950`） |
| `origin/beta..dev2` 合并后提交数 | 4（1 个合并提交 + 3 个本地修复提交） |
| `dev2..origin/beta` 合并后提交数 | 0 |

- **远端 beta 最新提交已完整合入**：合并基点 `09f8882251` 与 `origin/beta` 之间的 11 个提交全部随合并进入 `dev2`，合并后 `git rev-list --count dev2..origin/beta` 为 `0`。
- **合并无冲突**：`git merge origin/beta --no-edit` 未产生冲突标记；`git grep -n -E '^(<<<<<<<|>>>>>>>|=======$)'` 在 `app`、`docs` 下无命中。
- **远端已移除的回退提交未被带上来（决定性核对）**：
  - 合并后 `git log --format='%H %s' origin/beta..dev2` 只有 4 条：合并提交 `c14d146f0b` 与本地 3 个修复提交 `678a7d1483`、`593c04d052`、`c7fba76a46`。任何“远端已移除但 dev2 仍保留”的提交都会出现在该列表中，实测不存在。
  - `5682f2b054b577b0db5c6f7c1143f2eebb51858e`（“剔除 PR #353 主题系统改动”）经 `git merge-base --is-ancestor` 判定既不是 `dev2` 的祖先，也不是 `origin/beta` 的祖先，该回退提交不存在于两条分支历史中。
  - `git log --all -S "tv_item_current_ring" -- app/src/main/res/values/colors.xml` 只命中 `15b738cc83`（beta 侧新增统一焦点语义 token 的提交），说明这些 token 不是“被回退后又被 dev2 恢复”的内容；dev2 侧不含任何主题系统文件改动。
  - 净差异核对：`git diff --stat origin/beta..dev2` 仅 4 个文件、+148/−13，全部为本分支 3 个聚焦“唤醒控制栏焦点落点”的修复提交，不含任何被回退代码。

## 本轮待复评的改动（已提交未推送）

`origin/beta..dev2` 净差异 4 个文件：

| 文件 | 改动 |
| --- | --- |
| `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` | 抽出 `hasRememberedFocus()`；`getFocus2()` 复用同一判定；`onKeyUp()` 仅在无有效记忆焦点时才走片头/片尾快捷聚焦 |
| `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java` | 抽出 `hasRememberedInlineControlFocus()`（遥控端排除片头/片尾）；`getInlineControlFocus()` TV 分支复用；`setupInlineControl` 在遥控端补 `ACTION_DOWN` 触摸记录 |
| `app/src/main/java/com/fongmi/android/tv/ui/helper/PlayerControlFocusHelper.java` | `ensureFocus()` 改为“调用方显式目标优先，其次才沿用 root 内已有焦点，最后回退首个可聚焦控件” |
| `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/PlayerControlFocusIntegrationTest.java` | 新增 3 个防回归用例 |

## 评审记录

### 第 1 轮：改动本体语义 + 调用面复评

- `VideoActivity.hasRememberedFocus()` 与改动前的 `getFocus2()` 三元条件逐项等价核对：`mFocus2 != null`、`getVisibility() == VISIBLE`、`PlayerControlFocusHelper.isDescendant(mBinding.control.getRoot(), mFocus2)`、`mFocus2 != action.opening`、`mFocus2 != action.ending` 五个条件一一对应，`getFocus2()` 由 `hasRememberedFocus() ? mFocus2 : mBinding.control.action.next` 表达，语义严格等价，属于纯抽取。
- `VideoActivity.onKeyUp()` 复评：片头/片尾快捷聚焦仅在 `!hasRememberedFocus()` 时执行；`dispatchOpeningEndingAdjust()`（左右键微调片头/片尾）仍在 `dispatchKeyEvent` 的独立入口先于 `handleKey` 执行，未受影响；`onKeyDown()`（向下唤醒）仍直接 `showControl(getFocus2())`。`hideControl()` 把控制栏置 `GONE` 时 `mFocus2` 引用仍保留自身 `VISIBLE`，因此“唤醒时优先回到上次控件”的前提成立。
- `TmdbDetailActivity.hasRememberedInlineControlFocus()` 复评：额外要求 `isVisibleInHierarchy` 与 `isEnabled`，并排除 `binding.playerOpening`／`binding.playerEnding`；`Util.isMobile()` 分支保持原有“记住上次控件”语义，手机端触控导航未变；TV 分支回退候选 `{playerNext, playerPrev, playerEpisodes, playerRefresh, playerChangeSource, playerFullscreenAction}` 不含片头/片尾，因此“回退落点不会是片头”成立。
- `setupInlineControl()` 复评：`android.view.MotionEvent`、`com.fongmi.android.tv.utils.Util` 均已 import；触摸监听仅在 `!Util.isMobile()` 时挂载，且 `ACTION_DOWN` 只记录、返回 `false` 不吞事件，原有点击链路（`OnClickListener`、外层手势检测）不受影响；`hideInlineControls()` 的 `binding.playerPanel.requestFocus()` 仍会把焦点移出控制栏，唤醒时由 `getInlineControlFocus()` 决定落点。
- `PlayerControlFocusHelper.ensureFocus()` 复评（**本轮最关键的语义变更**）：逐个核对全部生产调用点。
  - `VideoActivity`（leanback）`showControl(View view)`：调用方（`onFullscreen()`、`mDialogReturnFocus` 回焦、`onKaraokeMode`）本来就要求“聚焦到显式传入的控件”，新顺序使该意图不再被 root 内已有焦点静默覆盖，与本次修复的是同一根因。
  - `TmdbDetailActivity.focusInlineDefaultControl()`：`preferred` 来自 `getInlineControlFocus()`；遥控端只有当“记忆焦点是普通控件”时才等于当前焦点（此时 `requestFocus` 是幂等空操作），若记忆焦点是片头/片尾则按设计改用候选回退。`showInlineControls(true, false)` 的全部调用点（`onDoubleTap`、`onInlinePanelConfirm`、`toggleInlineLock`、`STATE_READY`、`onBackInvoked`）都发生在控制栏隐藏或刚变可见时，不存在“控制栏已持有焦点且用户在片头/片尾按钮上”时被强制改焦点的可达路径。
  - `handleKey()`：只在 `!containsFocus(root)` 时调用 `ensureFocus()`，此时 `root.findFocus()` 必为空，新旧实现走同一分支，无行为差异。
  - `VideoActivity`（mobile）`showControl()` 与 `handleKey()`：`preferred` 为 `mBinding.control.play`。实测该控件是手机 flavor `view_control_vod.xml` 里无 `style`、无 `android:focusable` 的 `AppCompatImageView`；从设备拉取 `framework-res.apk` 后用 `aapt2 dump resources` 核对，框架资源表**不存在** `style/Widget.ImageView`，也不存在 `imageViewStyle` 属性，因此 `ImageView`／`AppCompatImageView` 默认 `isFocusable()` 为 `false` 且不是 `ViewGroup`，`firstFocusable(play)` 恒为 `null`，新旧实现同样落到“保留已有焦点，否则回退 `firstFocusable(root)`”分支。**结论：手机 flavor 的运行时行为不变**，该共享助手的改动只影响 leanback 侧“显式目标”语义。
- 交叉影响复评：四处改动均在 TV 焦点链上，不触及播放器、解封装、代理、缓存与站点解析；与 beta 侧新增的“统一焦点视觉 token（`tvFocusRing`／`tvCurrentRing`／`tvNormalStroke`）”“历史卡已看时长移除”“沉浸切集残留进度修复”无文件交集，但语义相邻——beta 侧新增的 `attrs.xml` 主题属性与 `selector_video_item.xml` 已在合并后共存核对，`selector_video_item.xml` 仍引用 `?attr/tvFocusRing`／`?attr/tvCurrentRing`，`attrs.xml`、`colors.xml`、leanback／mobile `styles.xml` 声明齐全，未被 dev2 侧改动影响。
- **第 1 轮结论**：未发现必须修改的问题；`ensureFocus()` 的顺序变更经调用面穷举后确认不会给手机 flavor 带来行为变化。

### 第 2 轮：验证通过后的交付边界 + 测试有效性复评

- **测试有效性核对**（source-based 契约测试，需人工确认断言确实具备防回归能力）：
  - `leanbackControlWakeKeepsUserChosenFocusOutsideTheIntroRange` 断言 `hasRememberedFocus()` 位于 `getFocus2()` 之前、`getFocus2()` 复用该判定、`onKeyUp()` 中 `if (!hasRememberedFocus())` 出现在 `canSetOpening` 之前，并断言片头/片尾被排除——把旧三元式写法写回会让断言失败。
  - `fusionControlWakeKeepsUserChosenFocusOutsideTheIntroRange` 断言 `hasRememberedInlineControlFocus()` 排除 `binding.playerOpening`／`binding.playerEnding`、`getInlineControlFocus()` 复用该判定且保留非片头回退 `binding.playerNext`、唤醒走 `PlayerControlFocusHelper.ensureFocus(..., getInlineControlFocus())`。
  - `tvControlClickIsRememberedAndPreferredFocusWinsOnWake` 断言触摸记录受 `!Util.isMobile()` 保护、`ACTION_DOWN` 会写入 `inlineControlFocus`、监听返回 `false`，并断言 `ensureFocus()` 中 `firstFocusable(preferred)` 出现在 `root.findFocus()` 之前。第三项断言的有效范围仅是“顺序契约 + 手机端隔离”，无法覆盖 `firstFocusable(preferred)` 为 `null` 的分支；该分支的结论来自第 1 轮对手机 flavor 布局与框架资源表的实测核对，已在上面记录。
- 交付边界：`git diff --name-only origin/beta..dev2` 只有上表 4 个文件；无 `app/build/**` 产物、无设备截图/dump、无 `.tmp-task/**` 被纳入。`git diff --check origin/beta..dev2` 通过，无空白错误。
- 兼容性复评：`hasRememberedFocus()` 与改动前的 `getFocus2()` 严格等价，因此不改变“无记忆焦点时仍聚焦 play_next”的既有回退；片头/片尾区间自动聚焦只在用户从未在控制栏落过焦点时生效，这是用户报告问题所要求的收敛，已在本任务与提交信息中记录。手机 flavor 行为不变（第 1 轮已给出证据）。
- 预存在缺陷记录（不属于本任务改动，仅记录不修改）：`TmdbDetailActivity.showInlineControls(boolean show, boolean focus)` 的 `focus` 形参在方法体内从未被使用，`focusInlineDefaultControl()` 一律执行；该方法与形参由 `b87cb0b813`（“feat: migrate tmdb detail modes”）引入，经 `git merge-base --is-ancestor b87cb0b813 origin/beta` 判定已是 `origin/beta` 的祖先，即合入前就存在于 beta。按“不修无关缺陷、先报告”的约定本任务不做修改。
- **第 2 轮结论**：净差异干净、断言具备防回归能力、无需要修改的问题，改动可交付。

## 验证记录

### 定向单元测试（合并后代码）

- Mobile（`:app:testMobileArm64_v8aDebugUnitTest`）：`PlayerControlFocusIntegrationTest` 10/10（含本分支新增 3 项）、`TmdbDetailActivityLayoutTest` 129/129、`VideoActivityLayoutTest` 153/153、`ThemeTvCatalogSourceTest` 3/3、`HistoryAdapterTest` 3/3，合计 298 项，0 failure／0 error／0 skipped，`BUILD SUCCESSFUL in 1m 13s`。
- Leanback（`:app:testLeanbackArm64_v8aDebugUnitTest`）：`NativeEnhancedPlaybackStyleFocusTest` 9/9（beta 新增用例，验证统一焦点 token 与纵向焦点链）、`QuickAdapterSelectionTest` 2/2、`SiteAdapterSelectionTest` 1/1，合计 12 项，0 failure／0 error／0 skipped，`BUILD SUCCESSFUL in 2m 54s`。
- 两轮合计 310 项通过。

### 打包、覆盖安装与启动冒烟

- 打包：`./gradlew :app:assembleLeanbackArm64_v8aDebug` → `BUILD SUCCESSFUL in 1m 52s`（129 tasks）；APK `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`，201,288,733 bytes。
- 覆盖安装：`adb -s emulator-5556 install -r app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk` → `Success`；使用 `-r` 覆盖安装，未卸载已有包。
- 启动冒烟：`adb -s emulator-5556 shell monkey -p com.silent.android.webhtv 1` 注入启动事件成功；8 秒后 `dumpsys activity activities` 显示前台 `com.fongmi.android.tv.ui.activity.HomeActivityCurrent`；随后检查设备 logcat，未发现 `FATAL EXCEPTION`、`ANR in com.silent.android.webhtv` 或 `Fatal signal`。
- 编译/打包产物回收：验证完成后执行 `./gradlew clean` 清理本次产生的 Gradle/APK 中间产物；设备上已安装包保留。

## PR 边界

相对 `origin/beta`，`dev2` 净差异为 4 个文件、148 行新增、13 行删除：

- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`
- `app/src/main/java/com/fongmi/android/tv/ui/helper/PlayerControlFocusHelper.java`
- `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/PlayerControlFocusIntegrationTest.java`

按提交划分：

1. `c7fba76a465023965f8ad5f487b7479a91be743c` fix(tv): 影视原生唤醒控制栏优先恢复用户记忆焦点
2. `593c04d052deffc6ae6f8f9070843db27794f12d` fix(tv): 沉浸融合唤醒控制栏不再被片头/片尾抢占
3. `678a7d148391b4721c1bf0f697f4dc313b8709bb` fix(tv): 修复融合控制栏点击后唤醒焦点落错按钮

## 回滚锚点

- 合并前：`678a7d148391b4721c1bf0f697f4dc313b8709bb`
- 合并后：`c14d146f0b1440b45ce734ce984acc85e450941b`
- 远端 `beta`：`8f7376295018e63747714b853f987550f9adae3f`
- 远端 `origin/dev2`：`7465b91f0bc1591ffdb83e2e8fa49f491db32a91`
- 上一轮 dev2 交付记录：`docs/C26-beta-merge-dev2-review-20260927.md`

## 状态与下一步

- 合并、2 轮复评、定向验证、打包覆盖安装与启动冒烟均已完成，无需要修改的问题。
- 下一步：提交本任务文档，推送 `dev2`，创建 base=`beta`、head=`dev2` 的中文 PR；只创建 PR，不执行合并。
