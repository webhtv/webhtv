# C97-beta-merge-review-dev3-20261022

## Recovery anchor

- **目标**：把 `origin/beta@3203ca9da0`（PR #476 合入后）零冲突合入 `dev3`，全量评审 `dev3` 已提交未推送的 `cae6e4b6e9`（首页延迟配置回调的 Fragment 事务守卫），修必修项并验证，修复后再次评审直至通过，然后提交、推送 `dev3`、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突；`beta` 带入路径与 `dev3` 自有改动的路径交集为 0；`beta` 树相对合并树无路径丢失。
  2. 评审覆盖 `cae6e4b6e9` 的 3 个路径；`beta` 带入内容按「已评审 + 0 冲突 + 0 路径交集」跳过并留证，另做集成面抽查。
  3. 每个必修项均已修复且修复有可复核证据；修复后第二轮复评通过。
  4. 双 flavor 定向单测全绿（leanback 7 例 / mobile 18 例），两个 flavor 的 Java 源码在同批任务里编译通过；`git diff --check` 与冲突标记扫描为空。
  5. 本轮改动经任务守卫 `finish` 落盘并创建 recovery tag；推送 `dev3`；创建到 `beta` 的中文 PR（含具体改动内容）；随后拉取远端最新代码。
- **Lane**：`upstream`
- **Scope**：`app/`、`docs/`
- **任务守卫**：`C97-beta-merge-review-dev3-20261022`，base HEAD `6a36571e51be6ab5fff30e413eb63761ce40ec6d`（合并提交），branch `dev3`，受保护脏路径 1 条（未跟踪 `.design-evidence/`，保持不动）
- **当前状态**：合并、首轮评审、修复、验证、第二轮复评、提交、推送、PR、拉取均完成（交付坐标见 §7）
- **下一动作**：无；等待 PR 评审/合并（本轮只创建 PR，不合并）

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `3203ca9da0`（PR #476 合入后，2026-10-22 15:0x `git fetch origin --prune` 取得，另带入 tag `recovery/beta-sync-review-dev4-20260913/...`） |
| 本地 | `dev3` = `cae6e4b6e9`（首页 Fragment 事务守卫，已提交未推送，领先 `origin/dev3` 1 个提交） |
| merge-base | `5fa1a242569fe12506e2e31ddaabeb62148bf462`（上一轮 C95 文档回填） |
| 合并提交 | `6a36571e51be6ab5fff30e413eb63761ce40ec6d`（双亲 `cae6e4b6e9` / `3203ca9da0`） |
| 冲突 | 0（自动合并） |
| 带入提交 | 13 个：`30324abe36`、`0c2d6d9ac6`、`ae0c82e7b6`、`573c8e5114`、`b8fdbee6f6`、`c5ef7ec829`、`5fa1a24256`、`2f83884f21`、`4dc58561bb`、`da7ac6440a`、`41f55daa17`、`d29f8563b3`、`3203ca9da0` |
| 带入路径 | 37 个：AUTONEXT-02（`PlaybackOwnershipKeys`/`VideoSurfaceBinding`/`PlaybackActivity`/`PlaybackService`/`SurfaceDiagnosticCollector`/4 个测试/文档）、全屏菜单键「下一集」（`Setting`/leanback `VideoActivity`/`TmdbDetailActivity`/三语 `strings.xml`/`FullscreenMenuKeyTest`）、C95/C96 文档、评估索引 |
| 路径交集 | **0**（`beta` 37 路径 ∩ `dev3` 自有 3 路径 = 空集） |
| 丢失面 B | **0**（`beta` 树路径 − 合并树路径 = 0） |
| 新增面 A | **2**（合并树 − `beta` 树 = 新增 `HomeFragmentCommitStateGuardTest`、`docs/CRASH-FRAGMENT-STATE-20261022.md`；`HomeActivity` 属既有路径的内容修改，不计入路径差） |
| 合并副作用 | 仅 `docs/C95-beta-merge-review-dev3-20261021.md` 尾部多删/少留一个空行（`beta` 侧同文件的换行归一化结果），无语义影响 |

## 2. 评审范围与跳过依据

- **全量评审**：`cae6e4b6e9` 的 3 个路径 —— leanback `HomeActivity`（守卫 + 清理顺序 + 刷新守卫 + `showHomeContent` 隐藏事务）、新增 `HomeFragmentCommitStateGuardTest`、`docs/CRASH-FRAGMENT-STATE-20261022.md`。
- **跳过依据**：`beta` 带入的 13 个提交里，`da7ac6440a`（AUTONEXT-02）已在 `docs/C96-beta-merge-review-dev1-20261022.md` 完成评审+复评；`ae0c82e7b6`（全屏菜单键「下一集」）已在 `docs/C95-beta-merge-review-dev2-20261021.md` 复评；`30324abe36` 崩溃日志地址功能已在 `docs/C95-beta-merge-review-dev3-20261021.md` 复评。本轮合并 0 冲突、路径交集 0，满足「已评审且无合入冲突」跳过条件。
- **集成面抽查**（合并后现场复核，非重新全量评审）：
  - 菜单键「下一集」：`select_fullscreen_menu_key` 三语各 3 项（`values`/`values-zh-rCN`/`values-zh-rTW`），`Setting.getFullscreenMenuKey()` 把取值钳制在 `[FULLSCREEN_MENU_KEY_CONTROL, FULLSCREEN_MENU_KEY_NEXT_EPISODE]`，`SettingPersonalActivity` 按返回值取数组下标 —— 数组长度与钳制区间一致，无越界。
  - AUTONEXT-02：合并树里 `PlaybackOwnershipKeys`/`VideoSurfaceBinding` 与 leanback `HomeActivity` 无符号交集，本轮改动不触碰播放归属链路。
  - 两个 flavor 的 Java 源码都在 §5 的单测任务中编译通过，合并后的符号/资源引用无断裂。

## 3. 首轮评审发现与处置

| 编号 | 严重度 | 位置 | 问题 | 处置 |
| --- | --- | --- | --- | --- |
| F1 | 中 | leanback `HomeActivity#clearCategoryContent` / `#getVideo` | 守卫把「拒绝提交」等同于「丢弃工作」。`isStateSaved()` 只是「已保存状态」，**实例可以仍然存活**（用户在配置加载/刷新期间切到别的应用或别的 Activity，回来还是同一实例，`onStart` 会把 `isStateSaved` 清掉）。这种窗口里被丢掉的是：①首页内容刷新——`VodConfig` 回调是一次性的，重建才有第二次，没重建就再也不会加载，回来看到的是空首页；②旧站点 `FolderFragment` 的移除——纯内存状态已清、页面却仍挂在容器里，等容器再次可见就和新站点页面叠加，反复切站点还会累积。 | 拆成两个判定：`canCommitFragmentTransaction()`（能不能**现在**提交）与新增 `canDeferFragmentWork()`（实例是否仍存活可推迟）。取消失败时：正在结束/已销毁 → 直接丢弃；仍存活 → 置 `mPendingCategoryCleanup` / `mPendingContentRefresh`（含 `forceNative` 意图），在 `onResume()` 末尾的 `resumeDeferredFragmentWork()` 里先过一次 `canCommitFragmentTransaction()` 再补做。移除事务抽成 `removeCategoryFragments()` 单点，供即时与推迟两条路径共用。 |
| F2 | 低 | `docs/CRASH-FRAGMENT-STATE-20261022.md` §5 | 验证口径不实：把 `HomeCategoryNavigationSourceTest`（13 例）与 `HomeSiteLockSourceTest`（4 例）记在 leanback 命令下，但这两个类在 `app/src/testMobile/` 源集，只有 mobile flavor 会执行；leanback 侧当次实际只跑了守卫测试与 `HomeSiteSwitchClearsTypesTest`。 | 按实际执行结果重写验证表（leanback 7 例 / mobile 18 例）并显式标注口径更正；同时更新 §4 改动表与 §6 回滚口径到修复后的实现。 |

**记录但未修**：

- **O1**：崩溃仍有「真机闭环」缺口。本机无 TCL `tcl_m7642` 设备，`docs/CRASH-FRAGMENT-STATE-20261022.md` §5 的压测步骤仍需用户在真机确认；代码级守卫与推迟路径由源契约测试覆盖。
- **O2**：`showContent()` 里 `App.post(this::prewarmWebView, 1500)` 只判 `isFinishing()`，守卫生效后它可能在一个「仍存活但已保存状态」的实例上创建预热 WebView。影响仅为后台一次视图预热（下一次前台 `getVideo` 会复用它），不改文案/不写偏好/不提交事务；本轮不扩大改动面，记录在案。
- **O3**：mobile flavor 无此形状（`HomeActivity` 不使用 `FragmentTransaction`，`VodFragment` 自管状态浮层），复核结论与上一轮一致，本轮不改。

## 4. 第二轮复评（修复后）

- **守卫语义**：`canCommitFragmentTransaction()` 三条件与文件内既有 4 处守卫逐字一致；`canDeferFragmentWork()` 只放行「仍存活」的宿主，正在结束/已销毁的实例依旧丢弃 —— 没有把「推迟」做成新的状态损坏源。
- **清理顺序**：`clearCategoryContent()` 仍在纯内存清理（`mCurrentType`/`mFolder`/容器可见性）之后才判守卫，保证 `isCurrentCategory()` 不会把即将消失的 Fragment 当当前分类；移除动作只在能提交时立即执行，否则挂 `mPendingCategoryCleanup`。
- **推迟补做**：`resumeDeferredFragmentWork()` 位于 `onResume()` 末尾（`super.onResume()` 之后，`isStateSaved` 已被 `dispatchResume` 清掉），进入前再判一次守卫；先做分类页移除、再做内容刷新（顺序保证新站点加载时旧页面已摘除）；两个标志各自消费一次，`forceNative` 用 `|=` 单调累积，不会重复触发。
- **回归面**：`showHomeContent()` 的隐藏事务仍受守卫包裹且本地可见性照旧清；`switchType`/`focusCategoryButton`/`showCategoryContent`/`openCustomCsp` 四处既有守卫未动；`onPause`/`onBack` 行为未变。
- **测试**：6 项源契约断言与修复后的实现逐条对得上（含「不得退化为 `commitAllowingStateLoss`」「推迟标志只消费一次」两条负向断言）。结论：**通过**。

## 5. 验证（2026-10-22 15:2x–15:3x +08:00）

| 项 | 命令 | 结果 |
| --- | --- | --- |
| leanback 定向单测 | `bash ./gradlew --console=plain :app:testLeanbackArm64_v8aDebugUnitTest --tests '…HomeFragmentCommitStateGuardTest' --tests '…HomeSiteSwitchClearsTypesTest' --tests '…HomeCategoryNavigationSourceTest' --tests '…HomeSiteLockSourceTest' --no-daemon` | **BUILD SUCCESSFUL in 1m 16s**；实际执行 `HomeFragmentCommitStateGuardTest` 6/6、`HomeSiteSwitchClearsTypesTest` 1/1，0 failure 0 error 0 skipped |
| mobile 定向单测 | `bash ./gradlew --console=plain :app:testMobileArm64_v8aDebugUnitTest --tests '…HomeCategoryNavigationSourceTest' --tests '…HomeSiteLockSourceTest' --tests '…HomeSiteSwitchClearsTypesTest' --no-daemon` | **BUILD SUCCESSFUL in 3m 2s**；13 + 4 + 1 = 18 项，0 failure 0 error |
| 差异卫生 | `git diff --check`、`^<<<<<<<`/`^>>>>>>>` 扫描 | 空（exit 0，无冲突标记） |

首轮曾有一次测试失败：`savedCategoryPageNeverCommitsWithoutTheGuard` 把 `clearCategoryContent()` 的取样区间划到 `clearStaleSiteTypes()`，把后面 `removeCategoryFragments()` 里的 `transaction.commit();` 也算了进去；改取样边界到 `private void removeCategoryFragments() {` 后通过。属测试取样错误，不是实现缺陷。

## 6. 回滚

- 本轮评审修复：还原 `HomeActivity` 的推迟逻辑与 `removeCategoryFragments()` 抽取、测试与文档改动即可（相对合并提交 `6a36571e51` 的单次提交回滚）。
- 整体回滚到合并前：`git reset --hard cae6e4b6e9`（保留 `dev3` 自有提交），或撤销合并提交后再处理推送历史。
- 无偏好写入、无数据库/二进制/schema 变更。

## 7. 交付坐标

- 合并提交：`6a36571e51be6ab5fff30e413eb63761ce40ec6d`（双亲 `cae6e4b6e9` / `3203ca9da0`）。
- 评审/修复提交：`bccb5b5a35dd7fc5a74cf3d8a38be844c6ba1416`（在合并提交之上，4 文件 +210 −23）。
- Recovery tag：`recovery/C97-beta-merge-review-dev3-20261022/20261022074657-bccb5b5a35dd`。
- 推送：`origin/dev3` `5fa1a24256..bccb5b5a35`。
- PR：[#477](https://github.com/Silent1566/webhtv/pull/477)（`dev3` → `beta`，中文描述含具体改动内容，只创建不合并）。
- 拉取：`git fetch origin --prune` + `git pull --ff-only origin dev3`，`dev3` 与 `origin/dev3` 同步；`origin/beta` 仍为 `3203ca9da0`。
- 工作树：干净；本节回填为文档单提交（受保护未跟踪目录 `.design-evidence/` 保持不动）。
