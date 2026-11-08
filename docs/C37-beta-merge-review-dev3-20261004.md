# C37：dev3 合并远端 beta 最新代码（PR#403）并复评详情页追更墓碑判定缺陷

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev3`（远端已移除/回退的提交不得顺带带回）；复评 dev3 全部已修改代码（含已提交未推送的 `aa5345c83` 播放页追更就地取消）；发现问题即修复并验证，循环评审直至通过；通过后提交、推送 `dev3`、创建 `dev3 -> beta` 中文 PR（只创建，不合并）。
- **验收标准**：① 合并结果包含 `origin/beta` tip `6d152c4a2`；② 远端被回退/剔除内容零复活；③ dev3 相对 beta 净差异仅含本分支自身改动；④ 定向 + 全量 JVM 测试、双 flavor 编译、UI token 检查、实机设备用例全部通过；⑤ 中文 PR 已创建且描述排版清楚。
- **当前状态**：合并（零冲突、零内容变化）+ 三轮评审 + 全部验证完成；第 1 轮发现 1 项真实缺陷（详情页墓碑判定），已修复并证明守卫非空断言；第 2 轮修复自查（我的设备用例原为空断言，已改为稳定性等待并重新证伪）；第 3 轮复评通过。
- **下一动作**：`task_guard.sh finish`（合并提交 + recovery tag）→ 推送 dev3 → 创建 PR（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev3` |
| 任务开始时 HEAD | `aa5345c83518ef74aa961de083336dae896a8fac`（领先 origin/dev3 1 个提交，未推送） |
| `origin/beta` tip | `6d152c4a24d2db45355c50b70458d9e90e8d8a49`（Merge PR #404 from dev1） |
| 合并基点（merge-base） | `08d6cca51e998e95e4a5b98fd887769856579804` |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`（由 task_guard finish 创建合并提交） |
| 合并结果 | **0 冲突**，24 文件合入（+1116 / -65） |
| 合并树哈希 | `c991639057c209a34c1f445956c4f67326679cb8` |

### beta 侧增量（两波，合并期间 beta 又前进一次）

**第一波**：`e72239063`「Merge pull request #403 from Silent1566/dev3」，即把 dev3 上一轮（PR #403）的结果回流到 beta。该提交相对合并基点 `08d6cca51` 的树**完全相同**，因此**零内容变化**。

**第二波（合并期间 beta 前进，已重新纳入）**：`6d152c4a2`「Merge pull request #404 from Silent1566/dev1」，含 3 个提交：

- `94b7fba36` 统一电视版焦点高亮为主题色边框环（dev1 侧自有改动）
- `f1c64ca0c` dev1 侧合并 origin/beta 最新代码（PR#399–#403）并复评
- `6d152c4a2` Merge PR #404

带入 24 个路径：新增 `focus_ring_{primary,secondary,error}.xml`、`TvFocusRingResolutionDeviceTest`、`TvFocusRingContractTest`、`C36-…-dev1`/`TV-FOCUS-RING-…` 文档，并修改 `ThemeController`、TV 焦点相关 adapter/drawable/layout、`item_following.xml`、`webhtv_dimens.xml`、`TmdbDetailActivity`（焦点环区域，与本次修复的追更区域无重叠）等。

处理方式：因该波触及 `TmdbDetailActivity.java`，先把本任务的修复与文档 `git stash`，重新以最新 tip 执行 `git merge --no-commit --no-ff origin/beta`（0 冲突），再 `git stash pop` 恢复修复（无冲突，修复完整保留），随后重跑全部门禁。

## 被回退/剔除内容核对（用户核心关注点）

1. **第一波 beta 前进零内容变化**：`git diff 08d6cca51 e72239063` 为空，且在该 tip 上执行 `git merge --no-commit --no-ff` 后 `git write-tree` = `84b0a782f…` = 当时 `HEAD^{tree}` —— 零字节变化，不存在"带回复退内容"的通道。
2. **净差异只含本分支改动**：提交后 `git diff --name-status origin/beta` 恰好 **11 个路径**（9 个修改 + 2 个本任务新增：`TmdbDetailFollowingTombstoneDeviceTest.java`、本文档），全部为 `aa5345c83` 与本任务修复的文件，**不含任何 beta 回退内容**。
3. **`fd29d76ee`（撤销把电视版暗色配色搬到手机版的两个提交）产物零复活**：对该提交涉及的 **21 个路径**逐一与 `origin/beta` 比对，**0 个存在差异**（`differing=0`）。即回退效果原样保留。
4. **`webhtv_tokens.xml`（mobile 暗色表）在 `HEAD` 与 `origin/beta` 中均不存在** —— 回退删除状态保持，未被合并带回。
5. **PR #353 剔除内容零复活**：`ThemeCatalog`/`ThemeCatalogStore`/`assets/themes/*` 在合并树中计数为 **0**（`5682f2b05` 的剔除效果保持）。注：`5682f2b05` 位于独立分支 `remove-pr353-theme-changes`，本就不在 `origin/beta` 祖先链中，因此不可能被合并带入。
6. **`git diff --check`**：对本次全部改动退出码 0，无空白错误。

## 评审记录

### 第 1 轮：dev3 已提交未推送提交 `aa5345c83`（播放页追更就地取消）

复评对象为 `aa5345c83` 新增的 `TmdbDetailActivity` 分支，逐项核验后发现 **1 项真实缺陷**：

**缺陷：详情页"已追更"判定未排除墓碑行，导致取消后无法重新追更。**

- **根因**：`FollowingStore.resolveTmdb`（TMDB 身份迁移）为防复活，遇到目标已是墓碑时会**故意返回该墓碑行而不是 `null`**（C9 防复活守卫，`FollowingStore.java:69-70`）。而 `aa5345c83` 新增的分支用 `existing != null` 判定"已追更"：
  - `onFollowing()`：`if (existing != null) { ... FollowingActivity.start(...) }` —— 墓碑行被当成"已追更"。
  - `updateFollowingState()`：`applyFollowingButtonState(true, item != null)` —— 墓碑行让按钮显示"已追更"。
- **用户可见后果**（TV 剧集、经 TMDB 匹配的身份）：
  1. 详情页取消追更后，按钮仍显示"已追更"（状态不刷新）；
  2. 内联播放中再点"已追更"，`isInlineFollowingPlaybackSurface()` 为真 → 只重复写一次墓碑，**永远无法重新追更**（复活路径 `addFollowing` 永远进不去）。
- **判定为非既有问题**：`git show origin/beta:.../TmdbDetailActivity.java` 显示 beta 侧同一方法也是 `existing != null`，但 beta 侧**没有**"内联播放中就地取消"分支，且 `aa5345c83` 正是把该分支引入 dev3 的提交 —— 缺陷由本次未推送改动引入并放大。
- **修复**（最小改动）：新增 `isFollowed(Following item)`（`item != null && !item.isDeleted()`），在 `onFollowing()` 分支与 `updateFollowingState()` 两处统一替换，并附注释说明与 C9 墓碑语义的接口约定。墓碑行交给 `addFollowing` → `FollowingPlaybackBridge.addAsync` → `FollowingStore.saveNew` 的复活分支处理。
- **守卫（三层，均已证明非空断言）**：
  1. 源码级：`FollowingUiSourceTest` 新增 5 条断言（含 `assertFalse(detail.contains("applyFollowingButtonState(true, item != null);"))`）；
  2. 设备级契约：`FollowingMigrationDeviceTest#tombstonedTargetIsReturnedAsTombstoneNotAsNull` 锁定 `resolveTmdb` 返回墓碑而非 `null`，并跑通"墓碑 → 复活"完整链路；
  3. 设备级 UI：新增 `TmdbDetailFollowingTombstoneDeviceTest`，在真实设备上对同一 TMDB 身份验证按钮文字（活跃行 → "已追更"；写墓碑后 → "加入追更"）。

### 第 2 轮：修复自查 —— 发现并修掉我自己的空断言用例

对第 1 轮新增的 `TmdbDetailFollowingTombstoneDeviceTest` 做反向验证（把修复点改回 `item != null` 重新编译运行），**第一版用例竟然通过了** —— 说明它是空断言：

- **原因**：`updateFollowingState()` 先**乐观地**把按钮写成"加入追更"（`applyFollowingButtonState(true, false)`），再由 Room 异步回调覆盖为最终态。第一版用例"一读到期望值就返回"，读到了乐观中间态，因此对未修复代码也误判通过。
- **修复**：改为**稳定性等待** —— 要求按钮文字连续 1.5s 不变、且总等待不少于 2s，才认为已落定。
- **重新证伪**：对未修复代码运行，用例**失败**并给出精确差异：`expected:<[加入]追更> but was:<[已]追更>`（即真实缺陷现象）；对修复后代码运行 **OK**。

### 第 3 轮：复评"第 1/2 轮结论 + 合并后最终状态"

- **合并零冲突、净差异精确**：两波 beta 增量均 0 冲突（第一波零内容变化，第二波 24 文件）；净差异 **11 个路径**全部为本分支改动；
- **修复完整且无同类遗漏**：全仓检索 `resolveFollowing(` / `resolveTmdbAsync(` 的消费者，只有 `TmdbDetailActivity` 两处（`onFollowing`、`updateFollowingState`），均已修复；`FollowingActivity:431` 用的是**活跃-only** 的 `FollowingStore.find()`，语义正确无需改；
- **守卫非空断言**：源码级断言对修复前文件（`git show aa5345c83:...`）逐条比对，4 条 `assertTrue` 全部为 NO（即修复前必然失败）；设备级用例已用反向编译实测证伪；
- **UI token 检查失败项判定为既有**：`scripts/check_ui_tokens.sh --strict` 报 2 项。证据：① `item_following.xml` 与 `origin/beta` **及**任务前 `HEAD` 均逐字节一致且不在本次改动内；② `focus_ring_error.xml` 为 beta PR#404 新增文件，与 `origin/beta` **逐字节一致**，违规内容是其 **XML 注释里的 `#93000A`/`#FFDAD6`**（非真实色值）。以 `git archive origin/beta` 独立检出**纯 beta 树**运行同一脚本，输出与合并树**完全一致**（`violations=2`、`hex_colors=1`、`min=4.28`、`pairs=38`）→ 合并与本次改动零新增违规。
- **范围外线索（记录，不修改）**：`?attr/colorOnSurface` 在手机版日间表解析为 `#1A1C1E` 的既有问题属 C35 已记录范畴，与本次改动无关，按 AGENTS.md 范围规则仅记录。
- 结论：**通过**（本轮零新增问题）。

## 验证记录

| 验证项 | 结果 |
| --- | --- |
| 合并冲突 | 0 冲突（两波 beta 增量均 0 冲突） |
| 第一波合并内容变化量 | **0 字节**（`git write-tree` == 当时 `HEAD^{tree}` = `84b0a782f…`） |
| 第二波（PR#404）合入 | 24 文件（+1116 / -65），`git stash` 保护修复后重新合并并 `pop`，修复完整保留 |
| `fd29d76ee` 回退产物复活核对 | 21 个路径逐一比对，**0 个差异** |
| PR #353 剔除内容复活核对 | 关键符号计数 **0** |
| dev3 净差异（vs `origin/beta`） | 恰好 11 个路径（9 改 + 2 新增），全部为本分支改动；与 PR #406 文件集程序化比对 MATCH |
| `git diff --check` | 退出码 0 |
| `FollowingUiSourceTest`（mobile / leanback） | 各 15 用例，0 失败 |
| 定向 JVM 套件（mobile：Following*/TmdbDetail*） | 全部 0 失败（含 `TmdbDetailActivityLayoutTest` 129 用例） |
| 定向 JVM 套件（leanback：Following*/TmdbDetail*） | 全部 0 失败 |
| **全量 JVM 套件（mobile）** | **5117 用例，0 失败 0 错误**（skipped 2） |
| **全量 JVM 套件（leanback）** | **4273 用例，0 失败 0 错误**（skipped 2，含 beta 新带入的 `TvFocusRingContractTest`） |
| 双 flavor 编译 | `assembleMobileArm64_v8aDebug` / `assembleLeanbackArm64_v8aDebug` 均 BUILD SUCCESSFUL |
| 设备安装（覆盖安装，未卸载） | `scripts/build_arm64_debug_install.sh --flavor mobile --serial 192.168.50.3:5559` 成功 |
| 设备用例（新增 + 契约） | `TmdbDetailFollowingTombstoneDeviceTest` + `FollowingMigrationDeviceTest` = **3 用例 0 失败** |
| **反向证伪（未修复代码，最终合并树上复验）** | UI 用例**失败**：`expected:<[加入]追更> but was:<[已]追更>` → 守卫有效、非空断言 |
| 修复后重新运行 | **OK (3 tests)** |
| `check_ui_tokens.sh --strict`（合并后） | `violations=2`（`item_following.xml` + beta 新增 `focus_ring_error.xml`） |
| `check_ui_tokens.sh --strict`（**纯 `origin/beta` 树对照**） | 输出**完全一致**（同样 `violations=2`、`hex_colors=1`、`min=4.28`、`pairs=38`）→ 零新增违规 |

## 提交与推送

- 本任务产物：merge commit（含本文档与修复）+ recovery tag。
- PR：dev3 → beta，中文描述，**只创建不合并**。

## Next action

`task_guard.sh finish` → push dev3 → `gh pr create`（只创建不合并）。
