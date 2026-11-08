# C112-beta-merge-review-dev5-20261030

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev5`；评审工作区未提交改动；发现问题即修复并验证；
  复评直至通过；然后提交、打 recovery tag、推送 `dev5`、创建到 `beta` 的中文 PR（含具体改动内容），最后拉取远端最新代码。
- **验收标准**：
  1. 合并后 `origin/beta` 成为 HEAD 祖先（落后 0），无冲突、无夹带评审外改动。
  2. 评审覆盖工作区全部未提交路径；发现的必修项全部修复且有回归断言。
  3. 复评通过（双轴均判定无必修项）。
  4. 三变体编译通过；单测失败集与既有基线逐条一致（无新增）；`git diff --check` 空。
  5. 改动提交并打 recovery tag；推送 `dev5`；创建到 `beta` 的中文 PR；随后拉取远端最新代码。
- **Lane**：`upstream`（`app` leanback + `docs`）
- **任务守卫**：**未执行**。本机 `bash` 通道不可用
  （`bash -c "echo ok"` → `Bash/Service/CreateInstance/CreateVm/HCS/HCS_E_SERVICE_NOT_AVAILABLE`），
  与 C105/C108/C111 记录的限制一致，非本轮引入。改用等效手工流程：只暂存任务自有路径 + 手工打 recovery tag。
- **当前状态**：合并、评审、四轮修复与复评完成；交付坐标见文末。

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 合并目标 | `origin/beta` = `66b40a1338fa086ae506cb87dfc2b8c72e03d086`（"Merge pull request #503 from Silent1566/dev2"） |
| 合并前本地 | `dev5` = `ba802dc4327dcb2433dfecb2f2dfd5c591621e86`（"Merge pull request #500 from Silent1566/dev5"） |
| 祖先关系（合并前） | `git merge-base --is-ancestor HEAD origin/beta` → false；left-right = **8 / 0** |
| 冲突预检 | `git merge-tree --write-tree HEAD origin/beta` → **无冲突** |
| 合并动作 | `git merge --no-ff origin/beta` → **32 个路径 / +2948 −61**，无手工干预 |
| 合并提交 | `8566ea696a0b0d78e8347a4fbcacf82c223d638a` |
| 祖先关系（合并后） | `is-ancestor` → **true**；left-right = **0 / 1** |

beta 本次带入 8 个提交（含 2 个 merge），主要非 merge 内容：

- `998172a4c8` fix(following): 修复追更源检查静默失败与集数进度停更
- `23f51a7736` perf(leanback): 降低进入播放页主线程卡顿（prewarm、跨帧分阶段、prefs/identity 缓存）
- `efdf8be13e` docs(following): 新增 FOLLOW-3 追更 TMDB 匹配设计

以上均为 `beta` 已评审并已通过 PR #502/#503 合入的内容，本轮不重复评审，仅验证在本分支可用。

## 2. 评审范围

工作区未提交改动共 **2 个路径**（改动前为 3 个，`HomeSiteSwitchClearsTypesTest.java` 在修复过程中恢复为与 HEAD 字节一致，不再计入）：

| 路径 | 状态 |
| --- | --- |
| `app/src/leanback/.../ui/activity/HomeActivity.java` | M |
| `app/src/test/.../ui/activity/HomeTypeRecyclerLayoutGuardTest.java` | 新增 |

改动意图：类型栏 `mBinding.typeRecycler`（`HorizontalGridView`，继承 `RecyclerView`）在 RecyclerView 计算布局期间
被结构性修改（`mTypeAdapter.addAll` → `notifyDataSetChanged`）会让子 View 拿不到 ViewHolder，
RecyclerView 随后在 `dispatchLayoutStep1` 崩溃。

## 3. 评审与修复轮次

采用双轴并行评审（Standards / Spec），每轮修完重新派两个只读评审子代理复评。

| 轮次 | 发现 | 处理 |
| --- | --- | --- |
| 1 | 撤销 setVisibility 延后（非结构性修改，延后破坏同步焦点）；闭包必须复查布局；结构性写入需统一入口；命名与返回值语义；测试钉死实现 | D1-D8 |
| 2 | **孤儿写入**：代际号丢弃 + `invalidatePendingFocusRequests()` 在失焦/onPause 也推进代际，无重放路径 → 类型栏永久空白；**GONE 被延后**：与「立刻移除旧分类」矛盾 | 改单槽合并 + 必执行 runner；GONE 与 `mPendingTypePosition` 移回同步段 |
| 3 | **同步分支双执行**：`deferTypeRecyclerUpdate` 自己 `update.run()` 又 `return true`，调用点再执行一次 | 改为无返回值 `runTypeMutation(Runnable)`，调用点只能是裸语句 |
| 4 | — | 双轴均判定**无必修项** |

## 4. 最终实现要点

`HomeActivity.java`：

- `runTypeMutation(Runnable)`（L288）：无返回值，自己决定「立即执行」或「入槽延后」。
- 同步分支（L290-296）：先 `mPendingTypeUpdate = null` 再 `update.run()`，保证旧写入不反盖新写入。
- 入槽分支（L298-301）：单槽合并（后写者覆盖）+ `mTypeUpdatePosted` 单 runner 去重。
- runner（L304-320）：`mTypeUpdatePosted=false` → 宿主结束/销毁则清槽返回 → 布局仍忙则置 true 重新 `postOnAnimation(this)` → 取出 pending 执行（必执行，不孤儿）。
- `isTypeLayoutBusy()`（L328）：`typeRecycler` 或内容行 `recycler` 任一在算布局。
- 调用点：L573 / L905 / L911，均为裸语句。
- `clearStaleSiteTypes()`：`mPendingTypePosition=-1` 与 `setVisibility(GONE)` 在同步段先执行，只有 `addAll` 走守卫。

## 5. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 三变体编译 | `compileLeanbackArm64_v8aDebugJavaWithJavac`、`compileLeanbackArmeabi_v7aDebugJavaWithJavac`、`compileMobileArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL，exit 0 |
| leanback 活动单测 | `testLeanbackArm64_v8aDebugUnitTest --tests=com.fongmi.android.tv.ui.activity.*` | 258 tests，**5 failed**（全为既有基线） |
| mobile 活动单测 | `testMobileArm64_v8aDebugUnitTest --tests=com.fongmi.android.tv.ui.activity.*` | 577 tests，**3 failed**（全为既有基线） |
| 改动自带回归 | `HomeTypeRecyclerLayoutGuardTest` | **8/8 通过** |
| 空白检查 | `git diff --check` | 空 |

既有失败基线（本机 CRLF 检出导致，与本改动无关，见 C106/C110 记录）：

- `NativeEnhancedPlaybackStyleFocusTest.everyTmdbRowCardCarriesItsOwnVerticalFocusTargets`
- `ReaderPlaybackRoutingSourceTest.readerDefinesTheMonotonicClockUsedByRestore`
- `ReaderPlaybackRoutingSourceTest.horizontalComicProgressUsesItsOwnPageState`
- `SearchResultDownFocusTest.scrollCallbacksDeferResultLoadingUntilAfterLayout`
- `TmdbSourceOnlyInteractionTest.nativeVideoSourceOnlyHidesNetworkActionsInBothFlavors`
- `androidx.media3.mpvplayer.MpvFontConfigTest.writeIfChanged_...`
- `androidx.media3.mpvplayer.MpvHlsCacheCoordinatorTest.successfulCommitPublishesCompleteFile...`
- `com.fongmi.android.tv.web.WebThemeTokenSourceTest.webHomeTokenDeclarationsStayInsideRootScope`

另在改动前的干净 HEAD 上跑过对照：失败集与本轮一致，证明无新增失败。

**变异检验**（证明回归断言有齿，非碰巧变绿）：

| 变异 | 结果 |
| --- | --- |
| runner 不执行 pending（孤儿写入回归） | `theNewestDeferredWriteIsAlwaysExecuted` 红 |
| `setVisibility(GONE)` 挪回延后体内（缺陷 B 回归） | `staleTypeRowHidesSynchronouslyAndOnlyTheDataClearIsDeferred` 红 |
| 调用点后重复调用目标（双执行回归） | `everyStructuralTypeMutationGoesThroughTheLayoutGuard` 红 |
| 恢复 boolean 返回值 + `if (guard(...)) target();` | 7 项红 |

每次变异后均字节级还原并复核 SHA256。

## 6. 残留风险

1. 单槽合并的前提是「槽位写入都是整表替换」。将来若放入增量写入（只 add 一项），被覆盖的那次会静默丢失。已写入 javadoc，但无编译期强制。
2. 回归断言是**源码文本级**，不是行为级：能钉住结构，但无法证明运行期 adapter 回调次数（仓库既有守卫测试同风格，无 Robolectric）。
3. 未做真机/模拟器验证；崩溃场景（布局中 `notifyDataSetChanged`）的最终确认仍需 TV 设备。
4. `clearTypes()` 的 `showHomeContent()` 副作用在「被 `applyTypes` 顶掉」时不重放（空/非空结果同帧交错才可达，后果有界）。

## 7. 回滚

- 回滚锚点：合并提交 `8566ea696a` 的父提交 `ba802dc432`（本轮改动前的 dev5 状态）。
- 单独回滚本任务的类型栏修复：`git revert` 本任务的修复提交即可，合并提交保留。

## 8. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `8566ea696a0b0d78e8347a4fbcacf82c223d638a` |
| 修复提交 | `448d33d7a4`（HomeActivity + GuardTest + 本任务文档） |
| Recovery tag | `recovery/C112-beta-merge-review-dev5-20261030/20261030-192522-448d33d7a4` |
| 推送 | `git push origin dev5` → `40bb057562..448d33d7a4` |
| PR | #505 → `dev5` → `beta` |

## 9. 下一动作

等待 PR #505 评审合入；合入后 dev5 相对 beta 落后归零。
