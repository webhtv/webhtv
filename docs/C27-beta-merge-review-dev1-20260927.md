# C27：dev1 合并远端 beta 并复评未推送改动（含站点注入默认关闭）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入本地 `dev1`，循环复评相对 `origin/beta` 与相对 `origin/dev1` 的全部已修改代码（含已提交未推送部分），发现问题则最小修复并验证通过；通过后提交、推送并创建中文 PR 到 `beta`，只创建 PR 不合并；不得把远端已移除/剔除的回退提交重新带上来。
- **验收**：合并结果包含远端 `beta` 最新提交；被剔除提交不在合并结果祖先中；`dev1` 相对 `origin/beta` 的净差异只包含本分支自身改动；组合后定向单元测试、Leanback Java 编译、Debug 打包与覆盖安装、启动冒烟通过；PR 描述用中文说明改动内容并保持排版清楚。
- **允许路径**：`docs/C27-beta-merge-review-dev1-20260927.md`、`app/src/leanback/java/com/fongmi/android/tv/bean/HomeButton.java`、`app/src/testLeanback/java/com/fongmi/android/tv/ui/bean/`、`docs/TV-CUSTOM-CSP-20260909-home-shortcut.md`。
- **分支/HEAD**：`dev1`；本任务开始时 `3aae6d9e5b2e799d20fccd2419369cf2ae4855a8`（未产生新的代码改动）。
- **保护面**：任务开始前仓库根目录的 18 个未跟踪验证产物（`theme_verify_*.png/xml`、`runtime_verify_*.png`、`window_dump.xml`、`window_state.txt`、`surfaceflinger_state.txt`、`transparency_verification.png`）均未纳入本任务。
- **当前状态**：合并状态核对、2 轮复评、定向验证、设备实测与设备状态回滚均已完成，进入收尾提交与 PR。
- **下一动作**：提交本任务文档，推送 `dev1`，创建 `dev1 -> beta` 的中文 PR（只创建，不合并）。

## 时间与设备

- 当前本地时间：2026-09-27 11:05（Asia/Shanghai）。
- 设备：`192.168.50.3:5555`（dev1 分配设备，Android 9 / sdk 28），Leanback Debug 包 `com.silent.android.webhtv`；未使用 dev2/dev3/dev4 的模拟器。

## 基线与合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1` |
| 本任务开始时 HEAD | `3aae6d9e5b2e799d20fccd2419369cf2ae4855a8` |
| 远端 `origin/beta` HEAD | `b5a1105163c5285e3479cb3beb02cb0bf97df919` |
| 远端 `origin/dev1` HEAD | `e55a3a3f78bc6145127c48a8127b0c0f4f595bb8` |
| `dev1` 与 `origin/beta` 合并基点 | `b5a1105163c5285e3479cb3beb02cb0bf97df919` |
| `origin/beta..dev1` 提交数 | 0（远端 beta 已是 `dev1` 的直接祖先） |
| `origin/dev1..dev1` 未推送提交数 | 8 |

- **远端 beta 已完整合入**：`git rev-list --count dev1..origin/beta` 为 `0`，且 `git merge-base dev1 origin/beta` 等于 `origin/beta` 的 HEAD，说明 `dev1` 已包含远端 `beta` 全部最新提交，无需再产生新的合并提交。
- **`dev1` 相对 `origin/beta` 的净差异只有本分支自身的 2 个提交**：
  - `2072bf4d65c1f91652f907fbd65bfc29863bf47f` fix(tv): 站点注入首页按钮默认不开启
  - `3aae6d9e5b2e799d20fccd2419369cf2ae4855a8` docs(tv): 记录站点注入默认不开启的设备实测
- **`dev1` 相对 `origin/dev1` 的 8 个未推送提交**：`3ebc9ccd4`、`e65243842`、`e55f03f57`、`1a2782836`、`7465b91f0`、`b5a110516`、`2072bf4d6`、`3aae6d9e5`。其中前 6 个已随 PR #378/#379 进入远端 `beta`，因此对 `beta` 而言本 PR 只增补最后 2 个提交。
- **被移除/剔除提交核对**：`5682f2b054b577b0db5c6f7c1143f2eebb51858e`（“剔除 PR #353 主题系统改动”，即 `remove-pr353-theme-changes` 分支头）经 `git merge-base --is-ancestor` 判定**不是** `dev1` 的祖先；`dev1` 相对 `origin/beta` 的净差异中也不存在任何主题系统文件。结论：远端已移除的回退/主题提交没有被顺带带上来。

## 评审记录

### 第 1 轮：改动本体 + 测试有效性

复评范围：`dev1` 相对 `origin/beta` 的净差异（2 个提交、4 个文件、+156/−5）。

- `app/src/leanback/java/com/fongmi/android/tv/bean/HomeButton.java`：`getDefaultButtons()` 删除 `ids.add("9")`，并加一行中文注释说明“站点注入默认不开启、仍保留在 `all()`/`sortedAll()` 目录中”。`ALL = "0,1,2,3,8,4,5,6,7,9"`、`all()` 中的 `new HomeButton(9, R.string.home_custom_csp)`、`getVisibleButtons()` 过滤条件、`sortedAll()` 补齐逻辑、`reset()` 均未改动。
- `HomeButtonDialog` 交叉复核：条目勾选状态来自 `HomeButton.getButtonsMap().containsKey(id)`，默认集合不含 `9` 时该行 `check` 为未勾选；`toggle()` 仍可把 `9` 加入并 `saveSelectedInSortedOrder()` 保存，`bind()` 的 `up/down` 排序按钮不依赖默认集合。因此“默认不开启”和“可手动启用/排序”两条路径同时成立。
- 调用方全量检索：`getDefaultButtons()` 只有 `HomeButton.getButtons()` 一处调用；`home_button`/`home_button_sorted` 两个 key 只在该类内读写；`home_custom_csp` 只用于 `Func` 状态文案、`HomeActivity` 点击入口与对话框；无第二处默认值来源，手机端（`app/src/mobile`、`app/src/main`）没有 `HomeButton` 实现，不存在需要同步的 flavor。
- 测试有效性核对：
  - `SiteInjectHomeButtonSourceTest#siteInjectionIsNotEnabledByDefault` 用 `methodBody(source, "getDefaultButtons()")` 截取方法体后断言不含 `"9"`，同时断言 `"7"` 仍在默认串、`id 9` 仍在 `all()` 中。恢复 `ids.add("9")` 会让该断言失败，具备防回归能力。
  - `SiteInjectHomeButtonDefaultStateTest`（Robolectric，真实执行 `HomeButton`）覆盖新安装默认不勾选、目录仍含该条目、手动启用后首页恢复显示、旧保存列表不被改写、重置后回到不含站点注入的默认值，与需求一一对应。
  - `FollowingUiSourceTest#leanbackHomeButtonUsesStableIdEightAndDefaultOrder` 只依赖 `ids.add("8")` 相对 `ids.add("3")` 的顺序与完整 `ALL` 串，与新默认值不冲突。
- **第 1 轮结论**：未发现必须修改的问题。

### 第 2 轮：交付边界 + 设备行为复评

- 交付边界：`git diff --name-only origin/beta..dev1` 只有 `HomeButton.java`、`SiteInjectHomeButtonDefaultStateTest.java`、`SiteInjectHomeButtonSourceTest.java`、`docs/TV-CUSTOM-CSP-20260909-home-shortcut.md` 四个文件；无 `app/build/**` 产物、无验证截图/dump、无 `docs/C27-*.md` 之外的临时记录被纳入。`git diff --check origin/beta..dev1` 无空白错误。
- 设备行为复核（见下节实测）：清空 `home_button`/`home_button_sorted` 后重建应用默认态，首页功能按钮行为 7 项且不含“站点注入”，与“默认不开启”一致；改动前该行为 8 项（含站点注入）。
- 兼容性复评：`Prefers.getString(KEY_BUTTON, getDefaultButtons())` 只在用户从未保存过列表时使用新默认值；已显式保存过列表（含此前保存的 `9`）的用户配置不被改写，`reset()` 语义仍为“回到默认值”。默认值变更对“从未保存过列表的老用户”表现为首页站点注入按钮消失，这是需求本身要求的默认态变化，已在 `docs/TV-CUSTOM-CSP-20260909-home-shortcut.md` 的“兼容”一节记录，无需额外迁移代码。
- 交叉影响复评：本次净差异不触及 `CustomCspSetting`、`Registry.enabled`、注入路径、`Func` 状态文案与 `HomeActivity` 点击入口；与 `beta` 侧其它改动（代理重定向、主题资源）无文件交集。
- **第 2 轮结论**：未发现必须修改的问题，改动可交付。

## 验证记录

- **定向单元测试**：`bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.bean.SiteInjectHomeButtonSourceTest' --tests 'com.fongmi.android.tv.ui.bean.SiteInjectHomeButtonDefaultStateTest' :app:compileLeanbackArm64_v8aDebugJavaWithJavac --continue` → `BUILD SUCCESSFUL in 28s`；`SiteInjectHomeButtonSourceTest` 4/4、`SiteInjectHomeButtonDefaultStateTest` 5/5，0 failure、0 error、0 skipped；Leanback Debug Java 编译通过。
- **未推送提交组合回归（覆盖已进入 beta 的 6 个未推送提交）**：`bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.adapter.QuickAdapterSelectionTest' --tests 'com.fongmi.android.tv.ui.adapter.SiteAdapterSelectionTest' --tests 'com.fongmi.android.tv.ui.bean.SiteInjectHomeButtonSourceTest' --tests 'com.fongmi.android.tv.ui.bean.SiteInjectHomeButtonDefaultStateTest' --continue` → `BUILD SUCCESSFUL in 36s`；`QuickAdapterSelectionTest` 2/2、`SiteAdapterSelectionTest` 1/1、`SiteInjectHomeButtonSourceTest` 4/4、`SiteInjectHomeButtonDefaultStateTest` 5/5，合计 12 项、0 failure、0 error、0 skipped。
- **打包与覆盖安装**：`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5555` → `BUILD SUCCESSFUL in 14s`（129 tasks），APK `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`（196M）生成并以 `adb install -r` 覆盖安装成功（未卸载）。
- **启动冒烟**：强制停止后重新启动，进程存在（PID 7451/8027/8472 三次均正常），前台 Activity 为 `com.fongmi.android.tv.ui.activity.HomeActivityCurrent`，日志内无 `FATAL EXCEPTION`、`fatal signal`、`ANR in com.silent.android.webhtv`。
- **设备默认态实测（决定性证据）**：先备份 `shared_prefs/com.silent.android.webhtv_preferences.xml`（md5 `1dd71ff68cf394816f638fabd358f9ce`，设备内 `.taskbak`、`/sdcard/dev1_prefs_backup.xml` 与本地副本三方一致），再移除 `home_button`、`home_button_sorted` 两个 key（`grep -c home_button` 为 0）后重启应用，`uiautomator dump` 得到首页功能按钮行为 `点播、直播、搜索、收藏、追更、推送、设置` **7 项，不含“站点注入”**；改动前默认包含站点注入（8 项）。
- **设备状态回滚**：验收后应用被强制停止，prefs 用备份原样覆盖回写，md5 恢复为 `1dd71ff68cf394816f638fabd358f9ce`，`home_button` 值恢复为 `0,8,6,1,2,3,4,7`；设备侧临时文件（`.taskbak`、`/sdcard/dev1_prefs_backup.xml`、`prefs_nodefault.xml`、各次 UI dump）已删除，本地临时目录已清理；未卸载现有包，未占用其它工作区模拟器。
- **未验证边界**：未重复执行 `testLeanbackArm64_v8aDebugUnitTest` 全量回归（本任务无新增代码改动，全量 4051 项已在 `2072bf4d6` 提交时通过并记录）；未在本轮重复“点击设置页重置按钮后摘要变为 6/10、手动勾选后变 7/10”的 UI 逐项走查（同代码树的 2026-09-27 设备实测已记录于 `docs/TV-CUSTOM-CSP-20260909-home-shortcut.md`，本轮以清空 key 的新安装默认态直测替代）；未执行多设备矩阵与原生/Go/Rust 工具链测试（与本任务改动无关）。

## PR 边界

相对 `origin/beta`，`dev1` 净差异为 4 个文件、156 行新增、5 行删除：

- `app/src/leanback/java/com/fongmi/android/tv/bean/HomeButton.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/ui/bean/SiteInjectHomeButtonDefaultStateTest.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/ui/bean/SiteInjectHomeButtonSourceTest.java`
- `docs/TV-CUSTOM-CSP-20260909-home-shortcut.md`

## 回滚锚点

- 合并前/本任务基线：`3aae6d9e5b2e799d20fccd2419369cf2ae4855a8`
- 远端 `beta`：`b5a1105163c5285e3479cb3beb02cb0bf97df919`
- 远端 `origin/dev1`：`e55a3a3f78bc6145127c48a8127b0c0f4f595bb8`
- 上一轮 dev1 交付记录：`docs/C25-beta-sync-review-dev1-20260926.md`（PR #378）

## 状态与下一步

- 合并状态核对、2 轮复评、定向验证、打包覆盖安装、启动冒烟、设备默认态实测与设备状态回滚均已完成，无需要修改的问题。
- 下一步：提交本任务文档，推送 `dev1`，创建 base=`beta`、head=`dev1` 的中文 PR；只创建 PR，不执行合并。
