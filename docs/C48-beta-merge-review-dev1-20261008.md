# C48：dev1 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入 `dev1`（**远端已移除/回退的提交不得顺带带回**）；复评 dev1 全部已修改代码（含已提交未推送的 `5096941a4`）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev1`、创建 `dev1 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并提交第二父为 `origin/beta` tip `46917be796`；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev1 既有改动**零丢失**；④ 双 flavor Java 编译通过；⑤ 双 flavor AndroidTest Java 编译通过；⑥ 双 flavor 全量 JVM 套件零失败；⑦ UI token 门禁相对基线零新增违规；⑧ 净差异只含本分支自身改动；⑨ 提交 + recovery tag；⑩ `dev1` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并完成（0 冲突）；3 轮评审全部通过（**本任务未修改任何生产代码**）；双 flavor Java/AndroidTest 编译、双 flavor 全量单测、UI token 门禁、零复活/零丢失程序化校验均通过；已提交、已推送、PR #422 已创建且未合并。
- **交付坐标**：见文末「交付坐标」。
- **下一动作**：无（任务已收口；PR #422 由用户决定是否合并）。

## 时间与设备

- 开始时本地时间：2026-10-08 19:46（Asia/Shanghai），任务收口时间见文末。
- 设备：本次**未使用模拟器/实机**（本任务零生产代码改动，无新增运行时行为需要设备证据）；未占用任何机位。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1` |
| 任务开始时 HEAD | `5096941a40304d9aae27c19d706df1454ba4e1e2`（`修复自动下一集黑屏：Surface 未绑定到重建后的引擎`，领先 `origin/dev1` 7 个提交，**已提交未推送**） |
| `origin/beta` tip | `46917be7965b7baa0ef75edc2cdeeb41ac411620`（Merge PR #421 from dev2） |
| 合并基点（merge-base） | `24265785a33d09ca1d1d3d36370d010ff1e89eac`（Merge PR #419 from dev3） |
| 合并方式 | `git merge --no-ff origin/beta`（真实合并提交，非快进） |
| 合并结果 | **0 冲突、0 冲突标记**，17 路径合入 |
| 合并提交 | `b8adf3cc1c9bc0e360823dac467b4da0aae6c9de`，父为 `5096941a4`（第一父，dev1 侧）与 `46917be79`（第二父，beta 侧） |
| 初始脏路径 | 无（`git status` 干净） |
| 回滚锚点 | `5096941a40304d9aae27c19d706df1454ba4e1e2` |
| 任务守卫 | `C48-beta-merge-review-dev1`（standard，scope `app/src` + `docs` + `scripts` + `.codex/scripts` + `.codex/task-state`） |

### beta 增量 ledger

`git log --oneline 5096941a4..origin/beta`（合并前）= 7 个提交：

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `46917be7965b7baa0ef75edc2cdeeb41ac411620` | Merge PR #421 from dev2 | 纳入（合并线） |
| `1a4d70410c84ecb00cf2bfde93260fc93b5e4dd0` | merge: 合并 origin/beta 最新代码（音频指纹移除与 T3→T4 本机网关） | 纳入（合并线） |
| `74c39d6dea36d5a4e4e872fc8af0060d1d1591cc` | docs(c46): dev2 评审记录 | 纳入（仅 `docs/`） |
| `ce01b45c3c59b243e1b0bc4a5a43d26db1706c69` | merge: 合并上游 webhtv/webhtv 站点注入搜索与手机缓存排版修复 | 纳入（合并线） |
| `05469a967ad32818b6e76e080100bfae1787ab76` | fix: 修复站点注入搜索空状态与排序刷新回归 | **纳入**（实质增量） |
| `7357b2c37d6b73887e56e3375cfa6e30655196c7` | feat: add site injection search | **纳入**（实质增量） |
| `58ebb653ff9386b292bd1e4957c0544e7582c035` | fix: repair mobile cache management layout | **纳入**（实质增量） |

`git diff --stat 5096941a4 origin/beta` = 17 路径 / +1280 −18，全部为上述 3 个实质增量提交的产物。

### 三方无损校验（程序化逐 blob 比对）

```text
beta 增量零丢失：merge-base..origin/beta 的 17 个路径 → 合并树与 origin/beta 逐 blob 一致（差异数 0）
dev1 既有零丢失：24265785a..5096941a4 的 3 个路径 → 合并树中全部存在（丢失 0）
                 且 PlaybackActivity.java 的 blob 与 5096941a4 完全相同
合并树文件总数 4971 = beta 4970 + dev1 独有 1（docs/AUTONEXT-01-…md）
合并树中「只在 beta 存在」的路径数：0（beta 路径零缺失）
```

### 净差异（`git diff --name-status origin/beta HEAD`）

仅 3 个路径，全部为 dev1 自身改动：

| 路径 | 性质 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java` | 修改（`5096941a4`） |
| `app/src/test/java/com/fongmi/android/tv/ui/activity/PlaybackOwnershipSourceTest.java` | 修改（`5096941a4`） |
| `docs/AUTONEXT-01-episode-transition-black-screen.md` | 新增（`5096941a4`） |

**关键区分**：`origin/beta..5096941a4` 列出 **20** 个路径（因为 `5096941a4` 的第一父 `24265785a` 尚不含 beta 的 PR #421 增量），而 `origin/beta..HEAD`（合并后）只剩 **3** 个路径。两者相减的 17 个路径正是本次合并纳入的 beta 增量——即合并**恰好**补齐了这 17 个路径，净差异因此收敛为 dev1 自身改动。合并未引入任何 dev1 侧的新内容，也未丢弃 beta 侧任何内容。

合并后净差异行数（`git diff --numstat origin/beta HEAD`）：`PlaybackActivity.java` +9 −2、`PlaybackOwnershipSourceTest.java` +26、`docs/AUTONEXT-01-…md` +169。

## 用户核心关注点：远端已移除（回退）内容零复活

本次采用**三层递进证据**，不依赖人工目测。

### 第一层：结构性证明（最强证据）

合并后 `dev1` 与 `origin/beta` 的差异路径集与 dev1 自身提交 `5096941a4` 的改动路径集**完全一致**（3 个路径）。

因此可做逐 blob 比对：**`5096941a4` 改动集之外的 4968 个路径，dev1 与 beta 的 blob 哈希 100% 相同**：

```text
beta tip 文件总数                                  : 4970
dev1 文件总数                                      : 4971
两侧共有路径数                                     : 4970
其中 blob 不同的路径数                             : 2（= dev1 自己改的 2 个 app/src 路径）
其中 blob 相同的路径数                             : 4968
只在 beta 存在（dev1 缺失）的路径数                : 0
只在 dev1 存在的路径数                             : 1（docs/AUTONEXT-01-…md，dev1 自建文档）
```

**结论**：dev1 不可能从 beta 之外引入任何内容，因为除 `5096941a4` 自身改写的 3 个路径外，其余全部路径与 beta 逐字节相同。

### 第二层：行级复活扫描

对 beta 历史上每个**单亲 revert/移除/删除类提交** `R` 取父 `R^`，得到 `removed(R,f) = lines(R^:f) − lines(R:f)`；复活定义为「该行出现在合并树中，且该行在 beta tip 中**也不存在**」（即确实被 beta 删掉、却被 dev1 带回）。

```text
候选单亲 revert/移除类提交（主题含 revert|remove|removal|delete|drop|回退|移除|删除|剔除|撤销） : 104
其中触碰 dev1 净差异路径的文件版本数                                                          : 137
扫描出的 removed 行中「出现在合并树且不在 beta tip」的复活行数                                : 0
```

**结论**：零复活。第一层结构性证明已从根上排除该可能，第二层是对净差异两个 Java 路径的定向加固。

### 第三层：净差异行级核对

```text
合并树与 beta tip 共有的路径数                        : 4970
合并树独有、beta tip 中不存在的路径                   : 1（docs/AUTONEXT-01-…md，纯新增文档）
dev1 相对 beta 的代码净差异行                          : 仅 attachSurface() 内 +9 −2 与对应单测 +26
```

这 9 个代码行**全部**是 `5096941a4` 自身的改写行，无一来自 beta 历史。

**关键目标提交 `5682f2b05`「剔除 PR #353 主题系统改动」**：该提交位于分支 `remove-pr353-theme-changes`，`git merge-base --is-ancestor 5682f2b05 origin/beta` → **否**，即它从未进入 `origin/beta`。第一层证明已覆盖：该提交触碰的路径若不在 `5096941a4` 改动集内，则 dev1 与 beta 逐字节相同；若在改动集内，则属于 dev1 自身改写（`5096941a4` 只改 `PlaybackActivity.java` 的 `attachSurface()` 与对应单测，与主题系统零交集）。

**结论：零复活。**

## 复评记录（3 轮）

复评对象 = dev1 全部已修改代码（含已提交未推送的 `5096941a4`）+ 本次合并带入的 beta 增量（3 个实质提交）。

### 第 1 轮：`5096941a4` 自动下一集黑屏修复的逐点复核

对修复本身做代码级复核，重点验证「是否真的锁定根因、是否引入新缺陷」：

| 复核项 | 证据 | 判定 |
| --- | --- | --- |
| 缺陷路径是否真实存在 | `PlayerManager.preparePlayer(type, force)` 在 `callback.onPlayerRebuild(player, force)` **之后**才 `spec = null`（`PlayerManager.java:1960/1961`）；而 `PlaybackActivity.isOwner()` 读 `manager.getKey()`，`getKey()` 由 `spec` 派生。故回调时 `spec` 仍为旧值，`isOwner()` 为真 | 与修复说明一致 |
| 修复前为何跳过绑定 | `attachSurface()` 原判断 `if (getExoView().getPlayer() == null)`。`PlayerView.player` 仍指向上一集已 `release()` 的引擎（非空）→ 整段跳过 | 与修复说明一致 |
| 新判断是否等价覆盖旧语义 | 新代码 `currentPlayer != nextPlayer`。旧语义「View 为空」对应 `currentPlayer == null`；此时 `null != nextPlayer` 成立 → 仍然绑定，**旧行为是超集**，无功能回退 | 通过 |
| 是否可能形成解绑-绑定循环 | `PlayerView.setPlayer()` 首行即 `if (player == newPlayer) return;`（字节码已确认），且绑定后 `getExoView().getPlayer() == player().getPlayer()` → 再次进入必然跳过 | 无循环风险 |
| 新增 `setPlayer(null)` 是否安全 | `PlayerView.setPlayer(null)` 的旧引擎分支被 `if (oldPlayer != null)` 保护；且该分支内 `isCommandAvailable(27)` 对已 `release()` 的 `ExoPlayerImpl` 返回 false → 跳过 `clearVideoSurfaceView`，不会触碰已释放对象 | 无空指针/崩溃风险 |
| `player().getPlayer()` 是否引入新空指针面 | 同一方法内 `getRender()`（第 651 行）与 `surfaceDiagnostics.bind(..., player().getPlaybackTraceId())`（第 664 行）早已无条件解引用 `player()`；新增解引用不扩大空指针面 | 无新增风险 |
| 是否覆盖全部引擎类型 | `ExoPlayerEngine`/`MpvPlayerEngine`/`IjkPlayerEngine`/`SystemPlayerEngine` 的 `release()` 与 `rebuild()` 均在原对象上 `player.release()` 并替换为新实例，故重建后实例身份必然不同 | 覆盖完整 |
| 单测是否可证伪 | 断言为源码级 `contains`，且显式断言旧字符串 `if (getExoView().getPlayer() == null)` **不存在** → 回退生产改动即失败（`5096941a4` 提交信息记录了实测 FAILED） | 有效 |

**第 1 轮结论**：`5096941a4` 根因定位准确、修复最小、无回退与新增风险。**无需修改。**

### 第 2 轮：合并带入的 beta 增量逐点复核

**A. 站点注入搜索（`7357b2c37` + `05469a967`）**

复核 `CustomCspDialog.CspAdapter` 的 `visibleIndices` 索引层：

| 复核项 | 证据 | 判定 |
| --- | --- | --- |
| 显示索引 ↔ 底层索引双向映射是否自洽 | `itemIndex(position)` 经 `visibleIndices` 映射（含倒序），`displayPosition(index)` 用 `visibleIndices.indexOf(index)` 反向查找，越界返回 −1；`onBindViewHolder` 与 `editCurrent`/`showSortActions` 均已加 `index < 0` 守卫 | 通过 |
| `getItemCount()` 改为可见数后，全部 `items.size()` 边界是否同步 | 逐处核对：`moveDisplay`/`moveItemToIndex` 已改用 `getItemCount()`；`remove` 用 `getItemCount()` + `index` 二次校验；`replace` 仍以 `items.size()` 校验——因其入参 `position` 来自 `editingPosition`，该值由 `itemIndex()` 产出（底层索引），语义正确 | 通过 |
| 筛选时是否仍可改动隐藏条目 | 筛选态下 `moveDisplay`/`moveItemToIndex` 直接返回 −1，`up`/`down` 置灰（`setEnabled(false)` + alpha 0.45）；删除/编辑按可见位映射到底层条目，隐藏条目不受影响 | 通过 |
| 是否丢失原设计「局部通知」 | `moveItemToIndex` 保留 `notifyItemMoved` + `notifyItemRangeChanged` 局部通知（`05469a967` 已修复 `7357b2c37` 的全量失效回归）；仅 `add`/`replace`/`remove` 用 `notifyDataSetChanged`，与筛选重算需求一致 | 通过 |
| 空状态是否同步 | `remove()` 末尾补 `updateModeVisibility()`，`searchEmpty` 依 `hasSearchQuery() && getItemCount() == 0` 判定 | 通过 |
| 搜索范围是否覆盖真实字段 | `CustomCspSetting.matchesSearch(Item, query)` 对 `other` 类走 `otherKey/otherKeys/otherText`，其余走 `name/key/api/homePage/url/ext/jar/extensionsText`；`trim().toLowerCase(Locale.ROOT)` 处理空白与大小写 | 通过 |
| 布局与资源完整性 | `dialog_custom_csp.xml` 新增 `searchLayout`/`siteSearch`/`searchEmpty` 三个 id，`ic_search.xml` 存在；三套 `strings.xml` 各新增 2 条且无重复键；XML 解析通过 | 通过 |
| 排序入口是否被搜索正确屏蔽 | `setSortMode(true)` 在 `hasSearchQuery()` 时早退；`updateModeVisibility()` 同步隐藏排序按钮（`mobileSort` 增加 `!hasSearchQuery()`） | 通过 |
| 主题契约 | 新增控件使用语义 token（`?attr/colorSurfaceContainerHighest`/`colorOnSurface`/`colorOnSurfaceVariant`/`@color/dialog_outlined_button_stroke`），无硬编码颜色 | 通过 |

**B. 手机缓存管理排版（`58ebb653f`）**

| 复核项 | 证据 | 判定 |
| --- | --- | --- |
| 手机竖排是否真正解决按钮挤压 | `addRow()` 在 mobile 下把 `detailColumn` 设为 `MATCH_PARENT`，动作按钮移入独立 `actions` 行并各占 `weight=1`，同时清 `minWidth/minimumWidth`、置 `minHeight=48dp` | 通过 |
| TV 横排是否零回归 | 非 mobile 走原 `else` 分支（`weight=1` + 两个 wrap 按钮），与合并前逐行相同 | 通过 |
| 焦点链是否被破坏 | `wireFocusOrder()` 在 mobile 下直接返回（手机触屏无需几何焦点链），TV 保持原有 `linkVertical` + 首尾闭环逻辑 | 通过 |
| 主题契约是否保持 | 合并冲突解决为保留本地 `WebHtvAlertDialogBuilder`（`git grep MaterialAlertDialogBuilder` 在 `CacheManagementDialog.java` 命中数 **0**），并新增 `com.fongmi.android.tv.utils.Util` import | 通过 |
| 两个 flavor 布局 id 是否一致 | `app/src/main/res/layout/dialog_cache_management.xml` 与 `app/src/mobile/res/layout/dialog_cache_management.xml` 的 `android:id` 集合**完全相同**（`comm` 双向无差异）→ 新增的 `CacheManagementDialogLayoutTest`（androidTest，仅 mobile）引用的 13 个 binding 字段在两个 flavor 均可解析 | 通过 |
| androidTest 是否可编译 | `:app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac` + `:app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac` → `BUILD SUCCESSFUL` | 通过 |

**C. 被合并带入的 beta 侧文档（`74c39d6de` 等）**

`docs/C45-upstream-sync-silent1566.md`、`docs/C46-beta-merge-review-dev2-20261008.md`、`docs/C47-beta-merge-review-dev2-20261008.md`、`docs/site-injection-search-review.md`、`docs/CACHE-MGMT-01-cache-management-design.md` 为 beta 侧记录，属被动纳入。抽查 `docs/site-injection-search-review.md` 的断言与代码一致（空状态同步、局部通知恢复均已落地）；其记录的 Windows 环境路径（`F:/temp/...`、`G:/Git/...`）为本机环境描述，不影响本仓库构建。

**第 2 轮结论**：beta 增量逐点复核通过，**未发现必修缺陷。**

### 第 3 轮：最终复核

对全部改动重跑静态核对：

- 净差异只含 dev1 自身 3 个路径，其中 2 个 app/src 路径的 blob 与 `5096941a4` 完全相同；
- 零复活三层证据重算通过（复活行数 0）；
- `git diff --check`（含 `--cached`）退出码 0，无空白错误；
- `git grep -E '^(<<<<<<<|>>>>>>>)'` 在 `app/**` 下命中 **0**，无冲突标记；
- 三套 `strings.xml` 无重复键，`git diff` 显示各自只新增 2 条（与 beta 增量一致，无合并重复）；
- 双 flavor Java 编译、双 flavor AndroidTest 编译、双 flavor 全量单测、UI token 门禁全部通过（见「验证」）。

**第 3 轮结论**：**全部通过，无剩余阻塞项。**

## 改动清单（本次 C48 相对 `5096941a4`）

| 路径 | 类型 | 说明 |
| --- | --- | --- |
| `docs/C48-beta-merge-review-dev1-20261008.md` | 新增 | 本任务文档 |

**生产代码零改动**：`app/src` 下本次无任何新增/修改/删除；净差异与 `5096941a4` 提交后完全一致（合并只带入了 beta 侧内容，未改写 dev1 侧内容）。合并提交 `b8adf3cc1` 本身即本次的代码交付（把 beta 最新代码纳入 dev1）。

## 验证

1. **双 flavor Java 编译**：`bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac` → `BUILD SUCCESSFUL`（1m 18s，EXIT=0）。日志 `build/c48-verify/compile.log`。
2. **双 flavor AndroidTest Java 编译**：`bash ./gradlew :app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac :app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac` → `BUILD SUCCESSFUL`（27s，EXIT=0）。日志 `build/c48-verify/androidtest-compile.log`。
3. **双 flavor 全量单元测试**：`bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest :app:testMobileArm64_v8aDebugUnitTest` → `BUILD SUCCESSFUL`（2m 6s，EXIT=0）。解析 JUnit XML：

   | flavor | suites | tests | failures | errors | skipped |
   | --- | --- | --- | --- | --- | --- |
   | leanback | 643 | **4079** | **0** | **0** | 2 |
   | mobile | 720 | **4920** | **0** | **0** | 2 |

   定向核对：`PlaybackOwnershipSourceTest` leanback 16/16、mobile 16/16（含 `5096941a4` 新增用例 `kernelRebuildBindsTheNewEngineToThePlayerViewByInstanceIdentity`）；`CustomCspDialogTest` 5/5（leanback）；`CustomCspSettingTest` 3/3（leanback 与 mobile）。

4. **UI token 门禁**：`bash scripts/check_ui_tokens.sh` → `UI_TOKEN_STATUS PASS`（EXIT=0）；`violations=1`，该 1 项为预先存在的 `item_following.xml`，**不在本次改动集内**，与基线一致 → 相对基线**零新增违规**。
5. **零复活程序化校验**：见上文三层证据（结构性证明 + 行级扫描 + 净差异核对），resurrected = 0。
6. **零丢失程序化校验**：beta 增量 17 路径在合并树中与 beta tip 逐 blob 一致（差异 0）；dev1 自身 3 路径在合并树中全部存在（丢失 0）；合并树中「只在 beta 存在」的路径数 0。
7. **良构性**：`dialog_custom_csp.xml`、三套 `strings.xml`、`ic_search.xml`、两个 `dialog_cache_management.xml` XML 解析通过；`git diff --check`（含 `--cached`）无空白错误；无冲突标记。
8. **资源一致性**：三套 `strings.xml` 无重复键；两个 flavor 的 `dialog_cache_management.xml` `android:id` 集合完全相同。
9. **设备端**：**未执行**。本任务零生产代码改动，无新增运行时行为；`5096941a4` 的设备证据见其自身任务文档 `docs/AUTONEXT-01-episode-transition-black-screen.md`，beta 增量的设备证据见 `docs/C47-beta-merge-review-dev2-20261008.md` 与 `docs/site-injection-search-review.md`。不将 JVM 测试描述为设备验证。

## 交付坐标

| 项 | 值 |
| --- | --- |
| 任务起始 HEAD | `5096941a40304d9aae27c19d706df1454ba4e1e2` |
| `origin/beta` tip | `46917be7965b7baa0ef75edc2cdeeb41ac411620` |
| 合并提交 | `b8adf3cc1c9bc0e360823dac467b4da0aae6c9de` |
| C48 改动 | 1 路径（`docs/` 文档；`app/src` 零改动） |
| 提交 | `87380b5cf76f06b12b5c66948b851450a68bbeda`（文档收口） |
| recovery tag | `recovery/C48-beta-merge-review-dev1/20261008204055-87380b5cf76f` |
| 推送 | `dev1` → `origin/dev1`，0 ahead / 0 behind |
| PR | [#422](https://github.com/Silent1566/webhtv/pull/422) `dev1 → beta`，**OPEN、未合并**（`mergedAt=null`、`state=OPEN`）、MERGEABLE，4 文件 +436 −2 |
| PR 文件集校验 | `gh api .../pulls/422/files` 分页合计 **4**，与 `git diff --name-only origin/beta HEAD` **逐项一致** |

## 备注

- `PlayerManager.java` 的 `com.fongmi.android.tv.player.exo.TrackUtil` 未使用 import 在 beta tip 中已存在（`git blame` 指向 `daaa0a80a`），**非本次引入**，按 AGENTS.md §2 仅记录不修改。
- `CustomCspDialog.showOtherEdit(...)` 与 `CustomCspSetting.matchesSearch(String, String...)` 在 beta tip 中即为无调用方的遗留方法（`showOtherEdit` 在 beta 中仅 1 处出现，即定义处），**非本次引入**，按 AGENTS.md §2 仅记录不修改。
- 合并带入的 beta 侧文档中记录的构建环境（Windows、`F:/temp/`、`G:/Git/`）为 dev2 作者本机环境描述，不影响本仓库在本机的构建与验证。
