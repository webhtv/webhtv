# C85 — 合并上游 `webhtv/webhtv` 分支 `Silent1566`（一键同步方向选择器）

## Recovery anchor

- **目标**：把上游仓库 `webhtv/webhtv` 分支 `Silent1566` 相对本地的 2 个新提交合并进 `dev3`，落地「一键同步改用推送/拉取两段式方向选择」这一用户可见能力，且不复活任何本地已删除内容、不丢失任何本地已有改动。
- **验收标准**
  1. 上游 `3f30bebe7ea97dff6ffcdc8e82649e8a8d970e1a` 成为 `dev3` 的祖先，合并提交为双父 `--no-ff` 合并提交。
  2. 合并后 `git diff <上游 tip> -- app/src/main/java/com/fongmi/android/tv/ui/dialog/OneKeySyncDialog.java app/src/main/res/layout/dialog_one_key_sync.xml` 只剩**本地增量**（`follow` 复选框 + 主题语义 token），无第三方内容。
  3. `modeButton` / `toggleMode` 在合并树中零残留引用。
  4. 合并后布局文件裸 hex 硬编码色数量 = 0（保持 `dev3` 已完成的 token 化成果，不因合并回退）。
  5. 定向编译（双 flavor arm64 Debug Java）通过。
  6. 定向单测（同步弹窗相关 + 主题/token 相关守卫）无新增失败。
  7. `scripts/check_ui_tokens.sh` 相对合并前基线零新增违规。
  8. 任务文档与评估索引行原子提交，并生成 annotated recovery tag。
- **Lane**：`upstream`
- **Scope**：`app/`、`docs/`
- **任务守卫**：`C85-upstream-sync-silent1566`，base HEAD `0e81bae1c7d1a91cd229dc5e1663d62f202548b3`，branch `dev3`，受保护脏路径 0
- **当前状态**：**已完成**（合并、验证、提交、tag 全部落地；未推送）
- **交付坐标**：合并提交 `f6e122c03847e1fc8c5ef83acdad0cbc3e024609`（双父 `0e81bae1c7` + `3f30bebe7e`）；回填提交 `c88f2896ac00d4ea663d7a56b4c16dfa4274a1cf`；recovery tag `recovery/C81-upstream-sync-silent1566/20261019074414-f6e122c03847` 与 `recovery/C81-coords/20261019074552-c88f2896ac00`
- **下一动作**：无（本任务已闭环）。后续由 `C86-beta-merge-review-dev3-20261019`（原号 `C84`）接手：把 `origin/beta` 合并进 `dev3` 并复评本任务带入的改动
- **标识符映射说明**：本任务在提交与 tag 落盘后才因评估索引 ID 撞号改号为 `C83`，其后 beta 发布 dev4 的 `C83`，本任务二次让位改 `C85`，已提交的 commit subject（`merge(C81)` / `docs(C81)`）与 recovery tag 名仍含 `C81`。按 AGENTS.md §6「不回写已发布历史」，这些标识符**保持原样**，仅在此记录映射：提交/tag 中的 `C81` ≡ 本文档的 `C83`；仓库中另有一个真实的 dev4 `C81`（评估索引 `docs/upstream-player-dependency-merge-assessment-2026-08-20.md` 第 59 行），两者不可混淆。

---

## 1. 上游 commit ledger

基线：`dev3@0e81bae1c7d1a91cd229dc5e1663d62f202548b3`
共同祖先（merge-base）：`590942503addf2db6b921875432f642221eb8f1f`（= 上游 PR #148 head，已由 `C65` 合并）
目标：`webhtv/webhtv:Silent1566@3f30bebe7ea97dff6ffcdc8e82649e8a8d970e1a`（2026-10-19 11:06:14 +0800）

`git rev-list --count 0e81bae1c7..3f30bebe7e` = **2**（`git log Silent1566..dev3` = 3254，本地大幅领先）

| 完整 commit ID | 日期 | 标题 | 主要路径 | 处置 |
| --- | --- | --- | --- | --- |
| `b6f8e0fda7f92e24e9f8a2ea045d1eee55fd26e5` | 2026-10-18 19:54:46 +0800 | `feat(sync): 一键同步改用推送/拉取两段式方向选择` | `app/src/main/java/com/fongmi/android/tv/ui/dialog/OneKeySyncDialog.java`、`app/src/main/res/layout/dialog_one_key_sync.xml` | **合并** |
| `3f30bebe7ea97dff6ffcdc8e82649e8a8d970e1a` | 2026-10-19 11:06:14 +0800 | `fix(sync): 修复方向选择器的评审缺陷` | 同上 | **合并** |

**范围外提交**：无。`dev3..Silent1566` 区间恰好只有上述 2 个提交，无其他在范围内提交需要处置。

## 2. 上游净变更构成

`git diff --stat 590942503a 3f30bebe7e` = **2 files changed, 86 insertions(+), 50 deletions(-)**

- 修改 2 个路径，**新增 0 个路径，删除 0 个路径**（`git diff --name-status` 全为 `M`）。
- 无新增资源文件、无新增字符串、无依赖/版本变更、无 native/二进制变更。
- 两个提交均为纯 UI 交互重构，不触及播放器、网络、代理、数据库或任何跨模块契约。

### 2.1 Java 侧（`OneKeySyncDialog.java`）

- `initEvent()`：删除 `binding.modeButton.setOnClickListener(v -> toggleMode());`，改为
  `binding.directionGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> { if (!isChecked) return; setToRemote(checkedId == R.id.directionPush); });`
- `toggleMode()`（原 `toRemote = !toRemote; updateDirection();`）被删除，替换为幂等的
  `private void setToRemote(boolean value) { if (toRemote == value) return; toRemote = value; updateDirection(); }`
- 新增 `private void updateDirectionGroup()`：按 `toRemote` 计算目标 `checked` id，仅在 `getCheckedButtonId()` 不一致时 `check(checked)`（避免 `selectionRequired` 下的重入循环）。
- `updateDirection()` 末尾的 `binding.modeButton.setText(toRemote ? R.string.sync_push : R.string.sync_pull);` 改为 `updateDirectionGroup();`；`binding.mode.setText(...)` 与 `setCompoundDrawablesWithIntrinsicBounds(0, toRemote ? ic_sync_arrow_right_blue : ic_sync_arrow_left_blue, 0, 0)` **保留不变**。
- `3f30bebe7e` 额外删除 `binding.mode.setOnClickListener(v -> toggleMode());`，即 `@+id/mode` 不再可点。

### 2.2 布局侧（`dialog_one_key_sync.xml`）

- `@+id/actionBar` 由「水平 4 按钮一行」改为「垂直两段」：外层 `LinearLayoutCompat` 的 `orientation` 由 `horizontal` 改为 `vertical`（去掉 `gravity="center_vertical"`）。
- 新增 `com.google.android.material.button.MaterialButtonToggleGroup @+id/directionGroup`（`app:selectionRequired="true"`、`app:singleSelection="true"`），内含两个 `style="@style/Widget.Material3.Button.OutlinedButton"` 按钮 `@+id/directionPush`（`@string/sync_push`）与 `@+id/directionPull`（`@string/sync_pull`），均 `layout_weight=1`、`layout_height=40dp`。
- 原 `@+id/start`、`@+id/selectAll`、`@+id/changeDevice` 移入新的内层 `LinearLayoutCompat`（`match_parent` / `wrap_content` / `marginTop 8dp` / `gravity center_vertical` / `orientation horizontal`），**按钮属性逐字保留**。
- `@+id/modeButton` 按钮整块删除；`3f30bebe7e` 还删除 `@+id/mode` 上的 `android:clickable="true"`。

## 3. 强制最佳实践门（§7 / §8）

### 3.1 三案对比

| 方案 | 正确性 | 兼容性 | 性能 | 维护性 | 结论 |
| --- | --- | --- | --- | --- | --- |
| **不合并** | 保留单一「切换」按钮 + `mode` 文本点击切换 | 无风险 | 无影响 | 无收益；与上游持续分叉，后续同步成本累积 | 否决 |
| **原样采用上游** | 功能正确，但会把上游布局中的 **32 处裸 hex 硬编码色**（`#202124`/`#5F6368`/`#2F6FED` 等）带回，直接回退 `dev3` 已完成的主题 token 化 | **回退既有主题能力**：深色模式/自定义主题下方向选择器与操作按钮会失去语义着色 | 无影响 | 差：新增 UI token 门禁违规 | 否决 |
| **本地适配后采用**（采纳） | 功能与结构 100% 采用上游，仅保留本地已有的语义 token | 无回退：合并后布局裸 hex = 0 | 无影响 | 与仓库既有 9 个 `MaterialButtonToggleGroup` 用法一致 | **采纳** |

### 3.2 证据分级

| 证据 | 来源 | 修订 | 支持的主张 | 适用性 | 结论影响 |
| --- | --- | --- | --- | --- | --- |
| 上游源码与提交 | `webhtv/webhtv:Silent1566@3f30bebe7e` | `b6f8e0fda7`、`3f30bebe7e` | 两段式方向选择器的确切实现与评审修复 | 直接适用 | 决定合并范围 |
| 本地既有同构实现 | `app/src/main/java/com/fongmi/android/tv/ui/dialog/AboutDialog.java:90/95`、`CustomCspDialog.java:238/259/261/262`、`ShellProxyDialog.java:155/199`、`WebHomeExtensionDialog.java:152/174/177` | 本地 HEAD | `MaterialButtonToggleGroup` + `addOnButtonCheckedListener` + 程序化 `check()` 是仓库既有成熟模式，非新引入 | 直接适用 | 排除「新组件风险」 |
| 本地既有布局先例 | `app/src/main/res/layout/dialog_github_proxy.xml:48-71`（`@+id/modeGroup`，`selectionRequired="true"` + `singleSelection="true"` + 两个 OutlinedButton） | 本地 HEAD | 上游布局写法与本地完全同构 | 直接适用 | 排除「布局模式风险」 |
| Material 组件版本 | `gradle/libs.versions.toml:48` `material = "1.14.0"` | 本地 HEAD | `selectionRequired` 属性受支持 | 直接适用 | 排除「API 不可用」 |
| 主题 token 合同 | `app/src/main/res/color/dialog_toggle_button_text.xml`、`dialog_toggle_button_bg.xml`、`dialog_outlined_button_stroke.xml` | 本地 HEAD | 上游引用的三个状态列表在本地**已存在**，且已被 `adapter_custom_csp.xml`(:98/:363/:421)、`dialog_custom_csp.xml`(:202) 使用 | 直接适用 | 排除「缺资源」 |

**未检索类别的理由**：本变更为纯 UI 交互重构，不涉及编解码、网络、代理、native、ABI、安全或数据所有权；官方规范/论文/基准类证据对此决策无增量信息，故不检索。

### 3.3 决策

**采纳上游设计，并保留本地主题 token 化**（即「本地适配后采用」）。上游把「点按钮切换方向」改为「显式二选一」，消除了原设计中「当前方向只能从按钮文字推断、且点击 `mode` 文本会隐式翻转」的可发现性问题，属于真实可用性改进；`setToRemote` 的幂等化与 `updateDirectionGroup` 的「不一致才 `check`」写法则消除了 `selectionRequired` 下的重入风险。本地唯一需要保护的是主题语义 token，该保护通过三方合并自动达成（本地改动区与上游改动区不相交）。

## 4. 冲突与解决决定

`git merge-tree --write-tree 0e81bae1c7 3f30bebe7e` → `114802c50ef9e268221ffdd1851acdb4c11fbd6a`（`git cat-file -t` = `tree`），**输出无任何 `CONFLICT` 行** ⇒ 三方合并干净。

无冲突的原因：本地自 merge-base 以来的改动集中在 `boxes()` / `updateSyncPathSummary()`（Java）与复选框区 + 主题 token 替换（布局），上游改动集中在 `initEvent()` / `toggleMode`→`setToRemote` / `updateDirectionGroup` / `updateDirection` 末尾（Java）与 `actionBar` 区块（布局），两组改动**行区间不相交**。

**解决决定**：无需人工解决，直接采用三方合并结果。

## 5. 零复活 / 零丢失证据

### 5.1 结构性（上游增量逐字节落地）

`git diff <上游 tip> 114802c50e -- <两文件>` 只剩**本地增量**：

- Java：仅 `boxes()` 中的 `binding.follow` 与 `updateSyncPathSummary()` 中的 `.follow(binding.follow.isChecked())`。
- 布局：仅本地 token 替换 + `@+id/follow` 复选框块 + `@+id/settings` 的 `android:checked="true"`。

即合并树 = 上游增量 + 本地增量，**无第三方内容**。

### 5.2 净值

合并预览树 `114802c50e`（`git merge-tree --write-tree` 的预演结果）只含上游 2 个代码路径的增量，本任务文档与索引行**不在**该树中——`git diff --stat 0e81bae1c7 114802c50e` 恰为 2 路径（86 insertions / 50 deletions），`git ls-tree 114802c50e docs/` 无 C81/C83 文档。

真实交付树（`c88f2896ac`）相对基线 `0e81bae1c7` 的净差是 **4 路径**：

```
$ git diff --name-status 0e81bae1c7 c88f2896ac
M  app/src/main/java/com/fongmi/android/tv/ui/dialog/OneKeySyncDialog.java
M  app/src/main/res/layout/dialog_one_key_sync.xml
A  docs/C81-upstream-sync-silent1566.md
M  docs/upstream-player-dependency-merge-assessment-2026-08-20.md
```

即上游净差（2 路径）+ 本任务文档（新增 1 路径）+ 评估索引行（修改 1 路径）。

### 5.3 删除面与复活面

- 上游本次**无删除路径**（`--name-status` 全为 `M`），故不存在「复活上游已删内容」的通道。
- 本地删除面：`dev3` 相对 merge-base 未删除任何路径，故不存在「合并把本地已删内容带回」的通道。

### 5.4 关键残留核查

- `git show 114802c50e:<Java> | grep -nE 'modeButton|toggleMode'` → **0 命中**。
- 全树 `git grep -n 'modeButton' 114802c50e` 仅命中 `ThemeDialog.java` 与 `CspWarmupDialog.java` 的**同名私有方法**，与 `dialog_one_key_sync` 无关。
- 布局裸 hex 计数：`dev3` = 0、上游 = 32、**合并树 = 0** ⇒ 主题 token 化成果未被回退。

## 6. 验证记录

### 6.1 决定性检查（最便宜且能否证伪）

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 预演合并树与实合并工作区一致 | `git diff --stat 114802c50e -- <两文件>` | **空**（exit 0）⇒ 实合并结果与 `merge-tree` 预演逐字节相同，无意外改动 |
| 上游增量逐字节落地 | `git diff 3f30bebe7e -- <两文件>` | 只剩本地增量（Java 2 处 `follow`、布局 42+/33− 为 token 化与复选框） |
| `modeButton`/`toggleMode` 零残留 | `grep -nE 'modeButton\|toggleMode' <Java>` | **0 命中** |
| 主题 token 化未回退 | `grep -cE '#[0-9A-Fa-f]{6,8}' <布局>` | **0**（上游为 32） |
| 冲突标记 | `grep -nE '^(<<<<<<<\|=======\|>>>>>>>)' <两文件>` | 无 |

### 6.2 编译

```
# 编译（双 flavor arm64 Debug Java）
bash ./gradlew --console=plain \
  :app:compileLeanbackArm64_v8aDebugJavaWithJavac \
  :app:compileMobileArm64_v8aDebugJavaWithJavac
→ BUILD SUCCESSFUL in 2m 31s (110 actionable tasks: 26 executed, 84 up-to-date)

# 定向单测（4 个测试类，逐类执行，见 §6.3）
bash ./gradlew --console=plain :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests 'com.fongmi.android.tv.ui.dialog.DialogRoundedCornerSourceTest' \
  --tests 'com.fongmi.android.tv.ui.style.UiLayoutSourceTest' \
  --tests 'com.fongmi.android.tv.ui.style.UiStyleSourceTest' \
  --tests 'com.fongmi.android.tv.theme.UiTokenGatePortabilityContractTest'
```

双 flavor arm64 Debug Java 编译均通过（`compileLeanbackArm64_v8aDebugJavaWithJavac`、`compileMobileArm64_v8aDebugJavaWithJavac`）。

### 6.3 定向单测

| 测试类 | tests | failures | errors |
| --- | --- | --- | --- |
| `com.fongmi.android.tv.ui.dialog.DialogRoundedCornerSourceTest` | 2 | 0 | 0 |
| `com.fongmi.android.tv.ui.style.UiLayoutSourceTest` | 4 | 0 | 0 |
| `com.fongmi.android.tv.ui.style.UiStyleSourceTest` | 7 | 0 | 0 |
| `com.fongmi.android.tv.theme.UiTokenGatePortabilityContractTest` | 4 | 0 | 0 |
| **合计** | **17** | **0** | **0** |

### 6.4 UI token 门禁

```
bash scripts/check_ui_tokens.sh
UI_TOKEN_BASELINE layouts=385 hex_layouts=0 drawables=562 hex_drawables=0 colors=60 hex_colors=0 allowlisted=190
UI_TOKEN_SCOPE stage=A violations=0 legacy=0
UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28
UI_TOKEN_STATUS PASS
```

`violations=0`，`hex_layouts=0` ⇒ 相对合并前基线**零新增违规**。

### 6.5 未执行与已知缺口

- **未做设备端实机验证**：本次为纯 UI 交互重构，编译 + 源码契约 + token 门禁已足以证伪结构性问题；方向选择器的实际点击/焦点行为未在真机或模拟器上手工走查。
- **已知缺口（如实记录）**：合并带入的方向选择器接线（`directionGroup` 监听 + `setToRemote` 幂等 + `updateDirectionGroup` 的「不一致才 `check`」重入守卫）**没有回归锁**。仓库现有唯一涉及本弹窗的测试 `DialogRoundedCornerSourceTest` 只断言圆角 drawable，不涉及方向选择器；上游 `3f30bebe7e` 修的正是这类评审缺陷，故后续本地改动若破坏该守卫将不会被自动捕获。本任务按「scope 默认关闭」未新增测试文件。
- **`C84` 复评补充（2026-10-19）**：该弹窗在 `dev3` 合并 `origin/beta` 的复评中被发现两处缺陷并已修复——① `updateDirectionCard()` 用 4 处 Java 裸 hex（`#E8F0FE`/`#F8FAFD`/`#174EA6`/`#5F6368`）在运行时覆盖布局 token，深色/自定义主题下两张方向卡仍是浅底深字；已改为 `ThemeController.current()` 的 `colorPrimaryContainer`/`colorOnPrimaryContainer`/`colorSurfaceContainerLow`/`colorOnSurfaceVariant` 成对语义色。**注意 `scripts/check_ui_tokens.sh` 只扫 `res/` 资源、不扫 Java**，故这处修复不被该门禁覆盖。② `startSync()` 的 `toRemote` 竞态：主线程取 `mode` 拼 URL、工作线程再读 `toRemote` 决定请求体（`backup`/`device`/`remoteRelay`），同步中翻方向会产生方向自相矛盾的请求；已在入口快照为局部 `final boolean toRemote` 并贯穿 `buildBody(...)`，同步期间同时禁用两个方向按钮。两处均**非**本任务（C83）引入：裸 hex 来自更早的 `7e5106e055`，竞态在 merge-base `0e81bae1c7` 已存在。

## 7. 改动清单

| 路径 | 来源 | 说明 |
| --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/ui/dialog/OneKeySyncDialog.java` | 上游 `b6f8e0fda7` + `3f30bebe7e` | `modeButton` 删除、`toggleMode`→`setToRemote`、新增 `updateDirectionGroup()`、`directionGroup` 监听接线 |
| `app/src/main/res/layout/dialog_one_key_sync.xml` | 上游 `b6f8e0fda7` + `3f30bebe7e` | `actionBar` 改垂直两段、新增 `directionGroup`/`directionPush`/`directionPull`、删除 `modeButton`、`mode` 去 `clickable` |
| `docs/C85-upstream-sync-silent1566.md` | 本任务 | 任务文档（新增；原名 `docs/C81-upstream-sync-silent1566.md`，因评估索引 ID 撞号在合并冲突解决时 `git mv` 改名） |
| `docs/upstream-player-dependency-merge-assessment-2026-08-20.md` | 本任务 | 评估索引新增 `C85` 行（原 `C81` 与 beta 已发布的 dev4 `C81` 撞号，本任务让位改 `C83`；随后 beta 又发布 dev4 的 `C83`，本任务二次让位改 `C85`） |

合并提交为双父 `--no-ff` 合并提交（父 = `0e81bae1c7` + `3f30bebe7e`）。

## 8. 回滚

- **回滚锚点**：合并前 HEAD `0e81bae1c7d1a91cd229dc5e1663d62f202548b3`。
- **回滚方式**：`git reset --hard 0e81bae1c7`（合并提交为单一 `--no-ff` 合并提交，回滚不丢失本地历史）；或 `git revert -m 1 <merge-commit>` 保留审计轨迹。
- 合并未引入任何依赖、native 二进制、数据库迁移或不可逆状态，回滚无副作用。

## 9. 交付坐标与下一步

- **合并提交**：`f6e122c03847e1fc8c5ef83acdad0cbc3e024609`（双父 `--no-ff`：`0e81bae1c7d1a91cd229dc5e1663d62f202548b3` + `3f30bebe7ea97dff6ffcdc8e82649e8a8d970e1a`）
- **回填提交**：`c88f2896ac00d4ea663d7a56b4c16dfa4274a1cf`（`docs(C81): 回填评估索引交付坐标与验证结论`）
- **recovery tag**：`recovery/C81-upstream-sync-silent1566/20261019074414-f6e122c03847`、`recovery/C81-coords/20261019074552-c88f2896ac00`
- **推送状态**：未推送（`dev3` 相对 `origin/dev3` ahead 4）
- **下一步**：无。本任务已闭环，两次改号（`C81`→`C83`→`C85`）的映射见 §Recovery anchor；后续 `origin/beta` 合并与复评由 `C86-beta-merge-review-dev3-20261019`（原号 `C84`）承担。
