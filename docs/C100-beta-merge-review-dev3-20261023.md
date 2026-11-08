# C100-beta-merge-review-dev3-20261023

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev3`（本轮实测已无落后，合并为空操作），全量评审 `dev3` 已提交未推送的
  唯一提交 `759860e70b`（TV-FILTER-RESET-01 TV 分类按钮长按重置筛选），修复必修项并验证，修复后再次评审直至通过，
  然后提交、推送 `dev3`、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. `dev3` 相对 `origin/beta` 落后 0 个提交（`origin/beta` 已是 HEAD 祖先），无冲突、无遗漏合并。
  2. 评审覆盖 `origin/beta...HEAD` 的净差异（11 个路径 / +303 −1），且每个发现都有可复核证据。
  3. 每个必修项均已修复；修复有回归用例或负向对照证明其真实有效；修复后第二轮复评通过。
  4. leanback 定向单测全绿（`TvFilterResetLongPressTest` 4/4）且 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过；
     `git diff --check` 为空。
  5. 本轮改动经任务守卫 `finish` 落盘并创建 recovery tag；推送 `dev3`；创建到 `beta` 的中文 PR（含具体改动内容）；
     随后拉取远端最新代码。
- **Lane**：`standard`
- **Scope**：`app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomTypeView.java`、
  `app/src/leanback/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java`、
  `app/src/testLeanback/java/com/fongmi/android/tv/ui/custom/TvFilterResetLongPressTest.java`、
  `docs/C100-beta-merge-review-dev3-20261023.md`、`docs/TV-FILTER-RESET-01-category-long-press-reset-20261023.md`
- **任务守卫**：`C100-TV-FILTER-RESET-REVIEW`，base HEAD `759860e70b640dbd7b178145f0c10e1aafd79966`，branch `dev3`，
  受保护既有脏路径 35 条（未跟踪 `%SystemDrive%/`、`.design-evidence/` 保持不动、不入库）
- **当前状态**：合并核对、首轮评审、两处修复、验证、第二轮复评、提交、推送、PR、拉取均完成（交付坐标见 §8）
- **下一动作**：无；等待 PR 评审/合并（本轮只创建 PR，不合并）

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `4dda4b56b8d67e0cb78e6617f7b0f776591383a9`（"Merge pull request #481 from Silent1566/dev1"，2026-10-23 12:46 `git fetch origin --prune` 取得） |
| 本地 | `dev3` = `759860e70b640dbd7b178145f0c10e1aafd79966`（TV-FILTER-RESET-01，已提交未推送，领先 `origin/dev3` 7 个提交） |
| 祖先关系 | `git merge-base --is-ancestor origin/beta HEAD` → **true**（`origin/beta` 本身即 `dev3` 的祖先） |
| 落后 / 领先 | `git rev-list --count HEAD..origin/beta` = **0**；`git rev-list --count origin/beta..HEAD` = **1** |
| 合并动作 | **空操作**（无可合并内容），因此无合并提交、无冲突、无 `beta` 带入路径 |
| 需评审的未推送提交 | **1 个**：`759860e70b`（TV-FILTER-RESET-01） |

结论：本轮「合并远端 beta 最新代码」在实测下已是等价状态，唯一评审对象就是这一个未推送提交。
历史 PR 记录（`gh pr list --base beta --head dev3 --state all`）全部为 MERGED，最新为 #480，
**不存在**针对 `759860e70b` 的开放 PR，故需新建。

## 2. 评审范围与跳过依据

- **全量评审对象**：`759860e70b`，按净差异 `origin/beta...HEAD` 评审：**11 个路径 / +303 −1**。
  - `app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomTypeView.java`（+66 −1）：长按计时、`LongPressListener`、清理点；
  - `app/src/leanback/java/com/fongmi/android/tv/ui/adapter/TypeAdapter.java`（+3）：`onLongPress(Class)` 接口方法 + 绑定；
  - `app/src/leanback/java/com/fongmi/android/tv/ui/fragment/TypeFragment.java`（+21）：`resetFilter()`；
  - `app/src/leanback/java/com/fongmi/android/tv/ui/fragment/FolderFragment.java`（+5）：`resetFilter()` 转发；
  - `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VodActivity.java`（+8）、`HomeActivity.java`（+6）：`onLongPress` 守卫；
  - `app/src/leanback/res/values{,-zh-rCN,-zh-rTW}/strings.xml`（各 +1）：`filter_reset`；
  - `app/src/testLeanback/java/com/fongmi/android/tv/ui/custom/TvFilterResetLongPressTest.java`（新增）；
  - `docs/TV-FILTER-RESET-01-category-long-press-reset-20261023.md`（新增）。
- **跳过依据**：`origin/beta` 无任何提交不在 `dev3` 内（`HEAD..origin/beta` = 0），因此本轮不存在「`beta` 带入内容需按已评审跳过」的问题；
  上一轮 C99 之后 `beta` 未产生新提交（`origin/beta` 仍为 `4dda4b56b8`）。
- **集成面复核**（现场读码，非重新全量评审）：`TypeAdapter` 的 `OnClickListener` 在 leanback 源集只有 2 个实现
  （`HomeActivity:109`、`VodActivity:37`），二者均已实现 `onLongPress`；mobile 源集的同名 `TypeAdapter` 是独立类
  （`OnClickListener` 仅 `onItemClick(int, Class)`），不受新增方法影响。

## 3. 首轮评审发现与处置

| 编号 | 严重度 | 位置 | 问题 | 处置 |
| --- | --- | --- | --- | --- |
| C1 | 中 | `CustomTypeView.java:63`（修复前行号） | 长按 latch 可残留：DOWN 分支写作 `if (event.getRepeatCount() == 0 && !longPressFired)`。长按到点后若配对的 `ACTION_UP` 没有送达该 View（宿主 `onPause`、屏幕休眠、焦点未失且未 detach 等），`longPressFired` 保持 `true`；**下一次短按**既不重新装计时器（被 `!longPressFired` 挡住），UP 时又被 `boolean consumed = longPressFired` 判为「已长按」吞掉 —— 结果是一次点击丢失、且该次长按失效。既有 3 个用例都不断言这个状态，缺陷无守护。 | DOWN 且 `repeatCount == 0` 时**无条件**复位 latch 并重新 arm：`longPressFired = false; App.post(longPressRunnable, LONG_PRESS_MS);`。安全性依据：`App.post(Runnable, long)` 内部先 `removeCallbacks` 再 `postDelayed`（`App.java:89-92`），重复 arm 不会叠加；`repeatCount != 0` 的重复键根本不进该分支，因此「重复键不重启计时」的语义逐字不变。 |
| C10 | 低 | `TypeFragment.resetFilter()` | 无选中项时也执行 `hideFilter() + showFilter()`：`showFilter()` 内有 `postDelayed(scrollToPosition(0), 48)`，于是「本来就没有任何筛选」的长按也会把筛选面板滚回首行，并做一次无意义的行重建 —— 与该方法自己「无变化就不做事」的 `changed` 语义不一致。 | 把重建、`onRefresh()`、`Notify.show()` 一并收进 `if (changed)`：无变化时只把 `Value.selected` 归零并返回 `false`，不重建、不重请求、不提示。 |

## 4. 复核未发现问题的项（正面证据，非「没看」）

- **C2 `performClick` 守卫**（`CustomTypeView.java:88-95`）：长按后 UP 已在 `onLongPressKey` 里 `return consumed`，
  不再下发 `super.dispatchKeyEvent(event)`，因此 `View` 的按下态点击路径不会走到；该守卫在当前实现下是冗余的。
  但它是「万一有其它路径把 UP 直接送到 `performClick`」时的最后一道闸（例如触屏 `onTouchEvent` 分支或未来改写分发逻辑），
  保留为防御性代码、不加改动。
- **C3 面板可见时的视觉刷新**：chip 的选中态完全由 `Value.isSelected()` 驱动
  （`FilterPresenter.onBindViewHolder`：`holder.binding.text.setSelected(item.isSelected())`），行对象是一次性构造的，
  因此「重置后必须重建行才能看到未选中」这一设计是正确的；重建走 `hideFilter()+showFilter()` 与既有 `toggleFilter` 完全同路径，
  不新增第二种刷新机制。
- **C4 字符串覆盖**：leanback 只有 `values` / `values-v27` / `values-zh-rCN` / `values-zh-rTW` 四个资源目录，
  `filter_reset` 在 `values`（`values/strings.xml:50` "Filters cleared"）、`values-zh-rCN`（`:50` "已重置筛选"）、
  `values-zh-rTW`（`:46` "已重置篩選"）三处齐全，无遗漏 locale（`values-v27` 只放主题尺寸类资源）；
  全仓 `filter_reset` 唯一使用点为 `TypeFragment.java` 的重置提示，无同名 key 冲突。
- **C5 两处入口守卫等价**：`VodActivity.onLongPress` 用 `mAdapter.indexOf(item) != mBinding.pager.getCurrentItem()`，
  `HomeActivity.onLongPress` 用 `item == null || item.isHome() || !isCurrentCategory(item)`；
  后者 = 「非 home 且是当前显示分类」，与前者「是当前页分类」语义一致，且多排除了 home 项（首页没有筛选面板，必须排除）。
  `HomeActivity.isCurrentCategory` 已包含 `mFolder != null && mFolder.isAdded() && !mFolder.isHidden() && isCategoryVisible()`，
  因此 `mFolder.resetFilter()` 不会 NPE。
- **C6 接口新增方法的编译面**：见 §2 集成面复核，leanback 两个实现齐全，mobile 同名类独立。
- **C8 `HomeActivity.onLongPress` 未调 `invalidatePendingFocusRequests()`**（与 `VodActivity` 的写法不一致）：
  判定**不需要**。`resetFilter()` 不切换分类、不改变 `mCurrentType`，而 `invalidatePendingFocusRequests()` 会连带取消
  `mPendingCategoryFocus` —— 那是一个「刚切到该分类、正在等焦点落位」的合法回调，长按重置期间把它取消反而可能让焦点停在旧位置。
  `VodActivity` 需要调用是因为它同时清内容焦点转发请求（`FolderFragment` → `TypeFragment` 的 generation 链路），
  两者职责不同，此处的不一致是有意取舍。
- **C9 `resetFilter` 清空 vs 回到 `init`**：leanback `FolderFragment.getExtend()` 用 `filter.getInit()` 建立 `mExtends` 初值
  （mobile 侧对应代码是 `filter.setSelected(filter.getInit())`）。本任务的产品语义是「长按 = 取消下面的筛选」，即回到**全部不选**；
  若回到站点 `init` 值，用户在「本来就用默认值」的分类上长按将看不到任何变化、也不会有任何请求差异，
  与「重置」直觉相反。因此清空是正确语义；同时确认 `mFilters` 来自 `Cache.copy(typeId)`（`Filter.copy()` 深拷贝、含 `Value` 副本），
  对 `mFilters` 调 `setSelected(false)` 不会污染 `Cache` 里的共享模板。
- **既有「上方向键 = 刷新」冷却逻辑未被改动**：`hasEvent` / `onKeyDown` / `coolDown` 三个点逐字未变，
  `hasLongPressEvent` 只对 `KeyUtil.isEnterKey` 生效，两条路径（DPAD_UP 与 DPAD_CENTER）互不重叠。

## 5. 第二轮复评（修复后）

- **C1**：`CustomTypeView.onLongPressKey` 复核为
  `if (event.getRepeatCount() == 0) { longPressFired = false; App.post(longPressRunnable, LONG_PRESS_MS); }`，
  UP 分支保持 `consumed` 语义不变。新增回归用例 `aLostReleaseDoesNotSwallowTheNextClick`：
  DOWN → 长按到点（`longPressCount == 1`）→ **不发 UP** → 再 DOWN 100ms → UP → 断言 `longPressCount == 1 && clickCount == 1`。
  **负向对照**（证明用例真的能抓住该缺陷）：临时把该分支回退成 `&& !longPressFired` 并重跑，
  `aLostReleaseDoesNotSwallowTheNextClick` 在 `TvFilterResetLongPressTest.java:121` 以 `AssertionError` 失败
  （`4 tests completed, 1 failed`）；恢复修复后 4/4 通过。
- **C10**：`resetFilter()` 复核为「先归零 `Value.selected` 与 `mExtends`，再 `if (changed)` 内重建 + 刷新 + 提示」，
  无变化路径返回 `false` 且不触碰面板；有变化路径与修复前逐条等价（重建 → `onRefresh()` → `Notify.show`）。
- **回归面**：本轮修复只改 `CustomTypeView.onLongPressKey` 与 `TypeFragment.resetFilter` 两个方法体，
  其余评审对象（`TypeAdapter`、`FolderFragment`、`VodActivity`、`HomeActivity`、三份 strings）逐字未变。
- **结论：通过。**

## 6. 验证（2026-10-23 13:0x +08:00）

| 项 | 命令 | 结果 |
| --- | --- | --- |
| leanback 定向单测 + Java 编译 | `bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.custom.TvFilterResetLongPressTest :app:compileLeanbackArm64_v8aDebugJavaWithJavac --continue -PchaquopyBuildPython=G:/Python310/python.exe` | **BUILD SUCCESSFUL**；`TvFilterResetLongPressTest tests=4 failures=0 errors=0 skipped=0` |
| 负向对照 | 临时回退 C1 修复后重跑同一测试任务 | 预期失败：`aLostReleaseDoesNotSwallowTheNextClick FAILED`（`AssertionError` @ `TvFilterResetLongPressTest.java:121`），`4 tests completed, 1 failed` |
| 差异卫生 | `git diff --check` | 空 |

环境备注（不属于本任务改动）：本机执行前需 `ANDROID_HOME=C:\Users\Maple\AppData\Local\Android\Sdk`（仓库无 `local.properties`）、
`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`，以及 `-PchaquopyBuildPython=G:/Python310/python.exe`（Chaquopy 需 Python 3.10）。

## 7. 回滚

- **本轮评审修复**：单次提交回滚即可（`git revert eaf0ba87c2`），只涉及两个方法体 + 一个测试方法 + 两份文档；
  生产行为回到 `759860e70b` 的原始实现（C1 缺陷与 C10 无意义重建随之回来）。
- **整体回退到评审前**：`git reset --hard 759860e70b`（保留该提交）。
- 无偏好写入、无数据库/协议/二进制/schema/资源格式/依赖变更。

## 8. 交付坐标

| 项 | 值 |
| --- | --- |
| 评审/修复提交 | `eaf0ba87c2cca1f32e6f780b9224f5c13ae67da7`（`review(C100): 复评 TV 分类长按重置筛选并修 2 项（长按 latch 残留 / 无变化时重建面板）`，5 files changed, 180 insertions(+), 10 deletions(-)） |
| Recovery tag | `recovery/C100-TV-FILTER-RESET-REVIEW/20261023051126-eaf0ba87c2cc`（由任务守卫 `finish` 创建） |
| 推送 | `git push origin dev3` → `fe9be22604..eaf0ba87c2  dev3 -> dev3`（`origin` = `https://github.com/Silent1566/webhtv.git`） |
| PR | [#482 feat(tv): 分类按钮长按重置筛选 + C100 复评修复（长按 latch 残留 / 无变化重建面板）](https://github.com/Silent1566/webhtv/pull/482)，base `beta` ← head `dev3`，中文描述含具体改动；只创建，未合并 |
| 拉取 | `git fetch origin --prune` + `git pull --ff-only origin dev3` → `Already up to date.`；`HEAD` = `origin/dev3` = `eaf0ba87c2` |
| 交付时工作树 | 仅 `?? %SystemDrive%/`、`?? .design-evidence/`（既有脏路径，保持不动、不入库）；临时 PR 正文文件已删除 |
