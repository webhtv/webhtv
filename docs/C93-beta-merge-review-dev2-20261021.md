# C93-beta-merge-review-dev2-20261021

## Recovery anchor

- **目标**：把 `origin/beta@8f7acb519c`（PR #472 合入后）合并进 `dev2`（零复活、零丢失），复评 `dev2` 已提交未推送的 C93 改动（缓存行手势反转）与本次合并带入的 LIVE-UI-01 代码，修复必修项，验证通过后再次评审直至通过，然后提交、推送 `dev2`、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突；丢失面 B（`beta` 树 − 合并树）= 0；复活面 A 恰为本轮新增的 1 个文档与本轮新增的 dev2 C93 任务文档。
  2. 复评覆盖：dev2 自有 16 路径（缓存行手势反转 + 文档）与 `beta` 带入的 LIVE-UI-01 8 路径；`beta` 带入内容按「已在 dev1 复评 + 0 冲突 + 与 dev2 改动零路径交集」跳过，理由与证据齐备。
  3. 必修项已修且每项有可复核证据；修复后第二轮评审确认通过。
  4. 双 flavor arm64 Debug 编译通过、缓存包单测与 `LiveActivityLayoutTest` 全绿、`check_ui_tokens.sh --strict` PASS。
  5. 本轮改动经任务守卫 `finish` 落盘并创建 recovery tag；推送 `dev2`；创建到 `beta` 的中文 PR（含具体改动内容）；随后拉取远端最新代码。
- **Lane**：`upstream`
- **Scope**：`app/`、`docs/`
- **任务守卫**：`C93-beta-merge-dev2-20261021`，base HEAD `3e73bd375378bf60023644f770191e25f70669b8`，branch `dev2`，受保护脏路径 `.wh_diag/`（未跟踪，保持不动）
- **当前状态**：合并、复评、修复、第一轮验证完成；修复后第二轮评审通过；待提交/推送/PR/拉取
- **下一动作**：任务守卫 `finish` 提交并打 tag，推送 `dev2`，创建到 `beta` 的 PR，拉取远端最新代码

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `8f7acb519c4cf91df0441c7d61253e6d1c87e6cd`（PR #472 合入后，2026-10-21 `git fetch --prune` 取得；上一轮 dev2 记录的上游 tip 为 `3964eeae7c`） |
| 本地 | `dev2` = `3e73bd375378bf60023644f770191e25f70669b8`（含 2 个自有未推送提交：`5262c92e3f` 合并上游缓存行手势、`3e73bd3753` 文档回填） |
| merge-base | `3964eeae7ccf4f10f5a2e3696a2909c7a92b3952` |
| 合并命令 | `git merge --no-ff --no-commit origin/beta` |
| 冲突 | **0**（`Automatic merge went well`，工作区无 `U` 状态） |

`beta` 本次带入 5 个提交：

| 完整 commit ID | 标题 | 处置 |
| --- | --- | --- |
| `d9a0cc5c69cd2aca44b5f4627fb67ad584b9121b` | fix(live): 直播按钮跟随播放器按钮设置并支持平板全屏旋转 | 合并（已在 dev1 复评） |
| `560d5e626315069050e5b20721e92a2810cf3207` | Merge remote-tracking branch 'origin/beta' into dev1 | 合并 |
| `4e8fa44848361f0eab2b9c4b1534f74b1df49539` | merge+review(C93): 合并 origin/beta 3964eeae7c 并复评 LIVE-UI-01，修复唤出控制栏未重算轨道按钮 | 合并（含 dev1 复评修复） |
| `225d2bbcce3d2f947c191ff83bd400d10b55c0e4` | docs(C93): 回填交付坐标 | 合并 |
| `8f7acb519c4cf91df0441c7d61253e6d1c87e6cd` | Merge pull request #472 from Silent1566/dev1 | 合并（`beta` tip，本次合并第二父） |

带入的净内容 = **8 路径 / +452 −4**：两份 `LiveActivity`、`LiveControlDialog`、两份 `view_control_live_action.xml`、`LiveActivityLayoutTest`、`docs/C93-beta-merge-review-dev1-20261021.md`、`docs/LIVE-UI-01-live-buttons-and-pad-rotate.md`。

## 2. 零复活 / 零丢失证据

| 判据 | 含义 | 结果 |
| --- | --- | --- |
| 路径数 | 合并树 / `beta` / `dev2`（合并前） | **5183 / 5182 / 5181** |
| **B** | `beta` 树 − 合并树（丢失面） | **0**（`comm -23` 为空） |
| **A** | 合并树 − `beta` 树（复活/新增面） | **1**：`docs/C93-upstream-sync-silent1566-cache-row-gesture.md`（dev2 上一轮自有任务文档；本轮再新增本文档后为 2） |
| 内容级净差（合并树 → `beta`） | 仅 dev2 自身改动 | 16 路径 / +228 −78（缓存行手势反转 14 路径 + 本文档 + 上游同步 C93 文档 + 评估索引行） |
| 双方路径交集 | dev2 自有路径 ∩ `beta` 带入路径 | **0**（dev2 未触碰任何 `LiveActivity`/直播布局/`LiveControlDialog`/`LiveActivityLayoutTest`，`git diff --name-only 3964eeae..3e73bd3753 | grep -i live` 为空） |

## 3. 复评范围与跳过依据

- **复评（dev2 自有 16 路径）**：`SettingActivity`（leanback）、`SettingFragment`（mobile）、`CacheManagementDialog`、`CacheCleanupManager`/`CacheCleanupMode`/`CachePolicyEngine`、两套设置布局、三语 `strings.xml`、两条缓存测试、`docs/CACHE-MGMT-01-cache-management-design.md`、评估索引与 C93 任务文档。
- **跳过（`beta` 带入 8 路径）**：LIVE-UI-01 已在 `dev1` 完整复评并修复（`docs/C93-beta-merge-review-dev1-20261021.md` §4：`showControl` 唤出控制栏未重算轨道按钮 ⇒ 两 flavor 各补 `setTrackVisible()` + 新增契约测试 `liveControlRevealRefreshesTrackButtons`），本次带入的正是修复后的版本（`git diff --cached` 中两份 `LiveActivity#showControl` 均含该调用）；合并 0 冲突，且 dev2 与该 8 路径零交集 ⇒ 合并结果 = `beta` 内容，无需二次复评。合并后仅按「与 dev2 现有代码的集成面」抽查：`PlayerButtonSetting.applyOrder` 保留未登记子节点（`reorderViewsPreservingUnmanagedChildren`），直播专属按钮（配置/直播源/线路/反色/跨类）不会被重排丢失；`LiveControlDialog.syncConfiguredButtonVisibility()` 仅同步 player/decode/playParams，与点播 `ControlDialog` 的同步集合一致，未扩大范围。

## 4. 复评发现与修复

| # | 级别 | 位置 | 问题 | 修复 |
| --- | --- | --- | --- | --- |
| 1 | 低 | `app/src/test/java/com/fongmi/android/tv/cache/CacheFullCleanupTest.java:179,192` | 手势反转后仍有两处陈旧注释称「长按快捷入口」（`turns the long-press shortcut back into a tiered run`、`The long-press shortcut must dispatch to the full clean`）。该类另两处与 `CachePolicyEngineTest` 已在上游 `15e8f6af48` 同步，唯独这两处（同文件的方法 `everyCleanupCallSiteUsesTheSharedRule`，来自 `beta` 侧早期改动）漏改，注释与「短按=全清、长按=面板」的当前 UI 矛盾，会误导后续维护者对手势入口的判断 | 改为「设置行单击（`the settings row's plain click`）」，并注明手势已于 2026-10-21 移出长按、长按现打开面板 |

其余复核结论（不修，均已取证）：

- **手势映射**：leanback/mobile 两入口 `onCacheClear`→`CacheManagementDialog.cleanEverything()`（FULL 全清、后台执行、Toast 报结果）、`onCacheManage`→`CacheManagementDialog.show(...)`（打开面板）；`onCacheManage` 返回 `true` 抑制跟随单击（AOSP `View.onKeyUp` 在 `mHasPerformedLongPress` 为真时不再 `performClickInternal()`，见 `C93-upstream-*` 文档 §3 的平台源码行号取证）。
- **开关关闭分支**：`CachePolicyStore.isManagementEnabled()==false` 时单击走 `FileUtil.clearCache(callback)`、长按复用单击且不弹面板，与设计 §23.1 一致。
- **清理进行中**：`cleanEverything()` 在 `CacheCleanupManager.isRunning()` 时提示新增串 `cache_cleanup_busy`（三语齐备：en/zh-rCN「正在清理，请稍候」/zh-rTW「正在清理，請稍候」），不再静默 return。
- **刷新链**：`CacheCleanupManager` 结束 → `CacheCenter.publishChanged` → `RefreshEvent.Type.CACHE` → `SettingActivity/Fragment#onRefreshEvent` → `setCacheText()`，无需面板即可刷新行数值。
- **布局**：两套设置布局缓存行保持单行（标题 `@string/setting_cache` + 数值），标题色仍为本地语义角色 `?attr/webhtvColorOnWallpaper`，未带回上游 `@color/white`；行高与「关于」行一致。
- **文案键**：`setting_cache` 三语（Cache/缓存/暫存）与 `cache_cleanup_busy` 三语均存在，无缺失 locale（仓库仅 `values`/`values-night`/`values-zh-rCN`/`values-zh-rTW`）。

## 5. 验证

| 项 | 命令/方式 | 结果 |
| --- | --- | --- |
| 双 flavor 编译 + 定向单测（一次执行） | `bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.cache.*' :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.activity.LiveActivityLayoutTest'` | **BUILD SUCCESSFUL in 1m 16s**；缓存包 **72 例 / 0 失败 / 0 错误 / 1 跳过**；`LiveActivityLayoutTest` **18 例 / 0 失败**（两个 test 任务同时承担对应 flavor 的 Java 编译，含本轮合并带入的两份 `LiveActivity`、`LiveControlDialog` 与两份直播动作栏布局） |
| UI token 门禁 | `bash scripts/check_ui_tokens.sh --strict` | **PASS**：`violations=0 legacy=0`、`pairs=38 failures=0 min=4.28` |
| 空白/冲突标记 | `git diff --check`、`git diff --cached --check`、冲突标记扫描 | 全空；`app/`、`docs/` 无 `<<<<<<<`/`>>>>>>>` |
| 合并语义 | 见 §2 路径数、A/B 面、路径交集 | 0 冲突、0 丢失、A 面恰为自有文档 |

本轮未重跑全量单测与设备 smoke：改动仅 1 处测试注释（行为零变化）与 `beta` 已复评代码的机械合入；缓存包定向单测已覆盖手势语义相关用例，`LiveActivityLayoutTest` 覆盖直播动作栏契约。

## 6. 第二轮评审（修复后）

对修复后的完整 diff（`git diff origin/beta` = 16 路径 / +228 −78，`git diff --cached` = `beta` 带入 8 路径 / +452 −4）逐条复核：

1. 修复项本身仅改注释，未触碰断言与生产代码，`CacheFullCleanupTest` 仍读取 `CacheCleanupManager` 生产源并断言 `FULL` 走 `cleanEverything(plan, progress)`，语义未削弱。
2. 文件内注释已自洽：类级 javadoc、`everyCleanupCallSiteUsesTheSharedRule` javadoc、方法内两条断言注释统一为「设置行单击=全清」，与 `CacheManagementDialog.cleanEverything()`（`reason=settings_row`）及两套设置入口一致；全仓缓存相关文件已无 `long-press shortcut` 残留（`grep` 为空）。
3. `beta` 带入的 8 路径内容与 `origin/beta` 逐字节相同（合并 0 冲突），dev2 自有 16 路径与 `beta` 零交集，无新引入语义冲突面。
4. 结论：**评审通过**，无需再修。

## 7. 回滚

- 撤销本轮修复：`git checkout origin/beta -- app/src/test/java/com/fongmi/android/tv/cache/CacheFullCleanupTest.java`（仅注释回到陈旧表述）。
- 撤销合并：`git revert -m 1 <本轮合并提交>` 回到 `3e73bd3753`（`beta` 带入内容随之撤销，dev2 缓存行手势改动不受影响）；本轮无 lock/native/数据结构/ABI 变更，回滚无副作用。

## 8. 交付坐标

- 合并提交：`bf0566640cee3e1790a9ab019a3404b6be6fc4be`（双父 `3e73bd375378bf60023644f770191e25f70669b8` + `8f7acb519c4cf91df0441c7d61253e6d1c87e6cd`）
- recovery tag：`recovery/C93-beta-merge-dev2-20261021/20261021112407-bf0566640cee`
- 推送：`origin/dev2`（`84c6d3bf15..bf0566640c`，13 个提交，含上一轮 C93 的 2 个未推送提交）
- PR：<https://github.com/Silent1566/webhtv/pull/473>（`dev2` → `beta`，中文描述，只创建不合并；创建时 `MERGEABLE` / `CLEAN`）
- 拉取：`git fetch origin --prune && git pull --ff-only origin dev2` → `Already up to date`；`origin/dev2...HEAD` = `0/0`；拉取时 `origin/beta` 仍为 `8f7acb519c`（未前进）
- 回填提交：`docs(C94): 回填交付坐标`（见本文件 git 历史）

## 9. 交付回填

| 项 | 值 |
| --- | --- |
| 合并提交 | `bf0566640cee3e1790a9ab019a3404b6be6fc4be` |
| recovery tag | `recovery/C93-beta-merge-dev2-20261021/20261021112407-bf0566640cee` |
| 推送范围 | `84c6d3bf15..bf0566640c`（`origin/dev2`） |
| PR | #473 <https://github.com/Silent1566/webhtv/pull/473>，`base=beta`/`head=dev2`/`OPEN`/`mergedAt=null`/`MERGEABLE`+`CLEAN` |
| 拉取结果 | `Already up to date`，`origin/dev2...HEAD` = `0/0` |
| `origin/beta` 当时 tip | `8f7acb519c4cf91df0441c7d61253e6d1c87e6cd` |
