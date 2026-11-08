# C39：dev3 合并远端 beta 最新代码（PR#407）并复评修复两处真实缺陷

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev3`（远端已移除/回退的提交不得顺带带回）；复评 dev3 全部已修改代码（含已提交未推送的 `3e4e49331` 主题编辑器安全区改动），发现问题即修复并验证，循环评审直至通过；通过后提交、推送 `dev3`、创建 `dev3 -> beta` 中文 PR（只创建，不合并）。
- **验收标准**：① 合并结果包含 `origin/beta` tip `03c239e32`；② 远端被回退/剔除内容零复活；③ dev3 相对 beta 净差异仅含本分支自身改动；④ 定向 + 全量 JVM 测试、双 flavor 编译、UI token 检查、实机设备用例全部通过；⑤ 中文 PR 已创建且描述排版清楚。
- **当前状态**：合并（零冲突）+ 4 轮评审 + 全部验证完成。第 1 轮发现 1 项真实缺陷（合并带入代码违反主题契约门禁），第 2 轮发现并修复 1 项真实缺陷（主题编辑器全零 insets 导致竖屏导航栏场景按钮仍被裁 48px），第 3/4 轮复评通过。
- **已完成**：合并提交（待 finish）；`CacheManagementDialog` 3 处构造改用 `WebHtvAlertDialogBuilder`；`ThemeDialog`（mobile/leanback 字节一致）+ `ThemeDialogLayout` 新增 `isEmpty()` 与三级 insets 回退；`ThemeDialogLayoutTest` 新增 1 个用例 + 2 条源码守卫；本文档。
- **下一动作**：`task_guard.sh finish`（合并提交 + recovery tag）→ 推送 `dev3` → 创建 PR（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev3` |
| 任务开始时 HEAD | `3e4e49331a4d72879e8df341c1c79275c9d25a7f`（领先 origin/dev3 5 个提交，未推送） |
| `origin/beta` tip | `03c239e32e1f4501d44adb65b762af104f5b8724`（Merge PR #407 from dev2） |
| 合并基点（merge-base） | `79244f274d15ae1b624bacc6a9f68bae8eafc3e3`（Merge PR #405 from dev4） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`（由 task_guard finish 创建合并提交） |
| 合并结果 | **0 冲突**，76 文件合入（+6623 / -27） |
| 合并树哈希 | `5b0ad6bb52bb5bbfa034ca7800fb6fcc0caaa3d0`（与 `git merge-tree --write-tree` 预测值逐字节一致） |
| 初始脏路径 | 无（`git status` 干净） |
| 回滚锚点 | `3e4e49331a4d72879e8df341c1c79275c9d25a7f` |

## beta 增量 ledger（61 提交，全部纳入）

`git log --oneline dev3..origin/beta` 的 61 个提交全部属于 beta 自身演进，其中 **57 个是缓存管理升级（P0–P4）单一功能波次**（`987f19e90` 设计 → `77b5d1042` P0 清单 → `d6393243d` P1 分级清理 → `eeca921eb`/`0392c5e09` P2 模块与总上限 → `b3e89479a` P3 自动调度 → `b40516716` P4 运行治理与清理日志 → 若干评审修复），其余为上游 `Silent1566` 同步与 PR #407 回流。

**关键判定：无一条与 dev3 既有实现重复、冲突或被取代**，因此全部纳入。dev3 合并前相对 beta 仅 5 个路径（`3e4e49331` 主题编辑器安全区改动），与缓存管理波次零交集，这也是本次 0 冲突的直接原因。

## 被回退/剔除内容核对（用户核心关注点）

beta 历史上有 **16 个单亲 revert 提交**，全部是 `03c239e32` 的祖先（即已在合并 base 内，其"移除效果"必须保留）。程序化逐路径比对：

```text
revert 触及路径总数 : 54
与 origin/beta 存在差异 : 0
```

**零复活**。即 16 个 revert 提交的移除效果在合并树中与 `origin/beta` 逐字节一致。

补充核对：

1. **净差异只含本分支改动**：合并后 `git diff --name-status origin/beta` 恰好 **6 个路径**，全部为本任务修复与本分支未推送改动，不含任何 beta 内容被改写。
2. **PR #353 剔除内容零复活**：`ThemeCatalog`/`ThemeCatalogStore`/`assets/themes/*` 在合并树中计数为 **0**。
3. **`fd29d76ee`（撤销把电视版暗色配色搬到手机版）产物零复活**：该提交涉及的 21 个路径全部与 `origin/beta` 逐字节一致；`webhtv_tokens.xml` 的删除状态保持（现存 3 个均为 beta 侧文件）。
4. **`git diff --check`**：对本次全部改动退出码 0，无空白错误。

## 评审循环记录

### 第 1 轮：合并带入代码违反本仓主题契约门禁（真实缺陷，已修复）

**发现**：合并后运行全量 JVM 套件，mobile 出现 1 个失败：

```text
ThemeBinderContractTest > materialAlertDialogsAreBuiltThroughTheThemedBuilder FAILED
    java.lang.AssertionError: build these through WebHtvAlertDialogBuilder instead:
      [main/CacheManagementDialog.java -> MaterialAlertDialogBuilder]
```

**根因**：beta 缓存管理波次新增的 `CacheManagementDialog` 在 3 处（`confirm` / `confirmModule` / `confirmDeep`）直接用 `new MaterialAlertDialogBuilder(requireContext())` 构建确认框。该 builder 是 `MaterialAlertDialogBuilder` 的子类 `WebHtvAlertDialogBuilder` 存在的唯一理由：`MaterialAlertDialogBuilder.show()` 实现为 `create().show()` 且 `create()` 是虚方法，只有走子类才能把对话框窗口绑定到当前主题 token（`ThemeController.bindWindowBackground` + `bindDialog`）。裸用 Material builder 的对话框永远停在编译期 `webhtv_color_*` 配色，用户切换主题后确认框不跟随。

**判定为非既有问题**：`git grep "new MaterialAlertDialogBuilder(" HEAD -- app/src` 在合并前 **零命中**（除测试自身源码字符串），说明该违规由本次合并首次引入；beta 侧该测试类存在但缓存管理波次未跑通它。

**修复**（最小改动，仅 3 行构造调用 + 1 处 import）：三处改为 `new WebHtvAlertDialogBuilder(requireContext())`，删除 `com.google.android.material.dialog.MaterialAlertDialogBuilder` import，新增 `com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder` import。**零行为回归**：子类只是重写 `create()` 增加主题绑定，其余 API 与父类完全一致，`focusNegativeOnShow` / `restoreFocusOnDismiss` 等既有逻辑不受影响。

**验证**：`ThemeBinderContractTest` mobile/leanback 双 flavor 全部通过；`git grep "new MaterialAlertDialogBuilder(" -- app/src`（排除测试源码字符串）零命中。

### 第 2 轮：主题编辑器全零 insets 未回退（真实缺陷，已修复）

**发现**：在 192.168.50.3:5559（1920×1080 / 280dpi / API 28）上以 `wm size 1080x1920` + `wm overscan 0,0,0,90` 复现竖屏带导航栏形态（`mStable=[0,42][1080,1830]`），打开主题色彩编辑器并像素级测量保存按钮：

```text
保存按钮蓝色像素行 : [1796, 1850]
导航栏顶端         : y=1830
→ 裁切 20px，与 3e4e49331 声称的"已修复"不符
```

**根因**：`3e4e49331` 引入的 `systemBarInsets()` 用 `windowInsets != null` 作为"窗口自身 insets 可用"的判据，但 **API 28 上浮动对话框窗口的 `systemBars()` 恒为全零**（实测窗口 `Lst insets: overscan=[0,0][0,0] content=[0,0][0,0] visible=[0,0][0,0] stable=[0,0][0,0]`，而同屏显示 stable 区为 `[0,42][1080,1830]`）。全零报告被当成"没有系统栏"，于是：

- `systemBarInsets()` 返回 `Insets(0,0,0,0)`，宿主 content 后备**永远走不到**；
- 窗口被设为 `1920-56 = 1864px` 高（整屏减去 2×28 margin），WM 再按 stable 区钳制成 `[28,42][1052,1830]`；
- 编辑器内部按 1864px 排版，底部按钮行落到屏幕 1796–1850px，被从 1830px 开始的导航栏裁掉。

**修复（三层回退 + 全零视为未知）**：

1. `ThemeDialogLayout.Insets` 新增 `isEmpty()`：四边均 ≤0 即"来源无有效信息"。
2. `ThemeDialog.systemBarInsets()` 改为三级来源：① 弹窗窗口自身报告**且非全零**；② 宿主 Activity 根窗口 insets（`ViewCompat.getRootWindowInsets(requireActivity().getWindow().getDecorView())`，与 `Util.isFullscreen` 同一来源，API 28 上该来源正确）；③ 宿主 content 视图与显示的差值。
3. 新增 `hostWindowInsets()`，异常时安全回退为空 insets。

**不变量**：全零报告与"未知"语义等价，绝不覆盖真实来源；非零报告（含仅单边非零，如 `(0,0,0,132)`）永远优先。

**验证**：

- **定向单测**：新增 `ThemeDialogLayoutTest#anAllZeroInsetReportIsTreatedAsUnknown`（5 条断言，覆盖全零、导航栏真值、单边非零），并在既有 `bothFlavoursApplyTheSharedSizingContract` 增加 2 条源码级守卫（`!windowInsets.isEmpty()`、`getRootWindowInsets(requireActivity().getWindow().getDecorView())`）。mobile 11/11、leanback 11/11 通过。
- **守卫非空断言（反向证伪）**：修复前源码不含 `!windowInsets.isEmpty()` 子串，故该断言对未修复代码**必然失败**；修复后通过。
- **设备证据（5559，arm64 Debug 覆盖安装，未卸载）**：
  - 修复前：窗口 `Requested h=1864`、`mFrame=[28,42][1052,1830]`、按钮 `[1796,1850]`、**裁切 20–48px**；
  - 修复后：窗口 `Requested w=1024 h=1732`、按钮 `[1692,1770]`、距导航栏 **余量 60px**、**裁切 0px**；窗口高度恰好等于 `1920 − 42(状态栏) − 90(导航栏) − 2×28(margin) = 1732`，证明系统栏被正确扣除。
  - 取消草稿边界：点"取消"后 `theme_profile_v2_json` 与 `theme_profile_v2_last_good` 的 SHA-256 前缀均为 `dedb21ec79b0848a`，与打开前逐字节一致 → 确认不落盘。
- **leanback 零回归**：leanback flavor 实机窗口 `[48,48][1872,1032]`（1824×984），与修复前文档基线**逐字节一致**；`mStable=[0,42][1920,1080]` 横屏无导航栏场景下宿主根窗口 insets 与弹窗自身报告一致，未引入额外收缩。

### 第 3 轮：修复后全量回归对照

| flavor | 合并前基线 | 修复后 | 新增回归 |
| --- | --- | --- | --- |
| mobile | 5190 用例 / 1 失败（第 1 轮缺陷） | **5191 用例 / 0 失败 / 0 错误 / 2 跳过** | **0** |
| leanback | 4345 用例 / 0 失败 | **4346 用例 / 0 失败 / 0 错误 / 2 跳过** | **0** |

失败集合为**空**（合并前的唯一失败即第 1 轮修复项）。`compileMobileArm64_v8aDebugAndroidTestJavaWithJavac` + `compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac` 均 `BUILD SUCCESSFUL`。

`scripts/check_ui_tokens.sh --strict`：

```text
合并前基线 : violations=2  hex_colors=1  min=4.28  pairs=38
合并后     : violations=1  hex_colors=0  min=4.28  pairs=38
UI_TOKEN_VIOLATION app/src/mobile/res/layout/item_following.xml
```

violations 由 2 **降为 1**（beta PR#404 的 `focus_ring_error.xml` 注释裸 hex 已在 beta 侧修掉），剩余 1 项为既有 `item_following.xml`，与本次改动零交集 → **相对基线零新增违规**。

### 第 4 轮：复评合并后最终状态

- **合并树一致性**：`git write-tree` = `5b0ad6bb52bb5bbfa034ca7800fb6fcc0caaa3d0`，与 `git merge-tree --write-tree dev3 origin/beta` 预测值逐字节一致 → 合并无冲突、无隐藏内容变化。
- **净差异精确**：6 个路径，全部为本任务修复 + 本分支未推送改动。
- **回退零复活**：54 个 revert 触及路径全部与 `origin/beta` 一致，差异 0。
- **同类遗漏排查**：全仓 `git grep "new MaterialAlertDialogBuilder("` 仅剩测试源码中的字符串字面量，生产代码零命中。
- **`git diff --check`**：退出码 0。
- **设备状态恢复**：`wm size reset`、`wm overscan reset`、`wm density` 保持 280、`accelerometer_rotation=1`、`user_rotation=0`、`policy_control=null`；应用重新启动可用（`HomeActivityCurrent` 获得焦点）。

**结论：第 4 轮通过，无新问题。**

## 验证记录汇总

| 验证项 | 命令/方法 | 结果 |
| --- | --- | --- |
| 冲突 | `git merge-tree --write-tree` 与实测 `git write-tree` 比对 | 0 冲突，树哈希一致 |
| 回退内容零复活 | 16 revert × 54 路径，逐路径 `git rev-parse` 比对 | **差异 0** |
| 净差异 | `git diff --name-status origin/beta` | 6 路径，全部为 dev3 自身改动 |
| 双 flavor Java 编译 | `compile{Mobile,Leanback}Arm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| 双 flavor androidTest 编译 | `compile{Mobile,Leanback}Arm64_v8aDebugAndroidTestJavaWithJavac` | BUILD SUCCESSFUL |
| 全量 JVM（mobile） | `:app:testMobileArm64_v8aDebugUnitTest` | 5191 用例 / 0 失败 / 0 错误 |
| 全量 JVM（leanback） | `:app:testLeanbackArm64_v8aDebugUnitTest` | 4346 用例 / 0 失败 / 0 错误 |
| 定向测试 | `theme.*` + `ui.dialog.*` + `cache.*` | 0 失败 |
| 主题契约门禁 | `ThemeBinderContractTest` 双 flavor | 通过（修复前 mobile 失败） |
| UI token 门禁 | `scripts/check_ui_tokens.sh --strict` | `violations=1`（既有 `item_following.xml`）、`hex_colors=0` → 零新增 |
| 空白校验 | `git diff --check` | 退出码 0 |
| 设备（mobile 竖屏+导航栏） | 5559，arm64 Debug 覆盖安装 | 按钮 `[1692,1770]`，余量 60px，裁切 0px |
| 设备（mobile 取消草稿） | prefs SHA-256 比对 | `dedb21ec79b0848a` 前后一致，不落盘 |
| 设备（leanback 横屏） | 5559，arm64 Debug 覆盖安装 | 窗口 `[48,48][1872,1032]`，与基线一致，零回归 |
| 设备状态恢复 | `wm`/`settings` 回读 | size/density/rotation/policy_control 全部恢复 |
| 缓存管理主路径 | 5559 mobile 实机 | 弹窗渲染 15 模块、确认框默认焦点在"取消"、真实清理释放 14 MB / 删除 7 文件、设置行经 `RefreshEvent.CACHE` 即时刷新 841.8→827.2 MB |

### 说明与边界

- **未执行**：完整 Instrumentation 测试、低空间/系统配额极限场景、native 重建；本次为"beta 增量合并 + 两处真实缺陷修复"，风险驱动的决定性验证已覆盖编译、全量单测、门禁、结构与实机主路径。
- **既有 UI token 违规**：`app/src/mobile/res/layout/item_following.xml` 的 1 项裸 hex 在合并前后均为同一项，路径与本次改动零交集，按 AGENTS.md 范围规则仅记录、不修改。
- **leanback 设置入口**：leanback 侧无直接设置页入口，需经首页菜单键 → "设置页面" 进入；已实测走通并验证缓存管理与主题编辑器。

## 回滚

- 任务前回滚锚点：`3e4e49331a4d72879e8df341c1c79275c9d25a7f`。
- 本次为单个 merge commit；回滚方式为 `git revert -m 1 <merge-commit>` 或重置到锚点。
- 代码改动仅 2 处真实缺陷修复：`CacheManagementDialog` 3 处构造调用改用主题 builder；`ThemeDialog`（mobile/leanback 字节一致）+ `ThemeDialogLayout` 的全零 insets 回退逻辑，均无 schema/数据迁移。

## 当前状态与下一步

- 状态：合并 + 4 轮评审 + 全部验证通过，待收尾。
- 下一动作：`task_guard.sh finish` → 推送 `dev3` → `gh pr create`（base `beta`，只创建不合并）。
