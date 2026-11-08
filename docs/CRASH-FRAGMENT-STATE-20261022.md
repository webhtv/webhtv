# CRASH-FRAGMENT-STATE 调外观就崩溃：延迟配置回调在已保存状态上提交 Fragment 事务

- Task id: `CRASH-FRAGMENT-STATE-20261022`
- Lane: `quick-fix`
- Started: 2026-10-22 11:04 +08:00 (Asia/Shanghai)
- Branch / base: `dev3` @ `5fa1a242569fe12506e2e31ddaabeb62148bf462`

## Recovery anchor

**Objective.** TV 端首页在「设置 → 外观」调颜色/主题模式/UI 缩放/语言之后不再崩溃：那条来自进程级 `VodConfig` 的延迟加载回调，落到旧实例（FragmentManager 已 `isStateSaved()`）时不得再提交 Fragment 事务。

**Acceptance.**
1. `HomeActivity`（leanback）里由延迟回调驱动的三处 Fragment 相关写操作都受同一个守卫约束。
2. 视图/适配器状态在守卫拒绝提交时仍然被清理（不留下指向即将消失 Fragment 的 `mFolder`）。
3. 新增源码契约测试覆盖守卫，既有 `HomeSiteSwitchClearsTypesTest`/`HomeCategoryNavigationSourceTest`/`HomeSiteLockSourceTest` 不回归。
4. 双 flavor 定向单测全绿。

**Single next action.** 已实现并通过验证；提交并打 recovery tag（见 §6）。

## 1. 现象与真机证据

用户在 TCL `tcl_m7642`（Android 9 / SDK 28 / armeabi-v7a）上调一次颜色即崩溃，提供 `G:\下载\webhtv-debug-log-2.txt`。

| 日志时间 | 事件 |
| --- | --- |
| `08:19:15.709` | `startup: config load success cost=231453ms` |
| `08:19:15.710` | `startup: home showContent start cost=231455ms` |
| `08:19:15.718` | `av-diag: process.recovery … message="Can not perform this action after onSaveInstanceState"` |
| `08:19:15.737` | `process-crash: java.lang.IllegalStateException` + 完整栈 |

栈（`r8-map-id` 混淆了行号映射，类名/方法名保留）：

```
at o63.n                                       (FragmentTransaction.commit)
at androidx.fragment.app.u.v
at androidx.fragment.app.a.g
at HomeActivity.clearCategoryContent:95
at HomeActivity.syncCategorySite:20
at HomeActivity.getVideo:1
at HomeActivity.showContent:51
at HomeActivity$4.success:32                   (VodConfig 加载回调)
at gd.run → Handler.handleCallback → Looper.loop
```

`HomeActivity$4` 对应基线代码 `getCallback()`（`app/src/leanback/.../HomeActivity.java:619`）返回的匿名 `Callback`，`success()` 里调 `showContent()`。

## 2. 根因

三个事实叠加：

1. **回调是进程级的，与 Activity 生命周期无关。** `VodConfig` 是单例；`BaseConfig.loadConfig()` 完成后用 `App.post(callback::success)` 投递到主线程（`app/src/main/java/com/fongmi/android/tv/api/config/BaseConfig.java:122`）。回调持有的是**调用时刻**那个 `HomeActivity` 实例的引用。
2. **分类页清理无条件提交事务。** `syncCategorySite()`（:419）→ `clearCategoryContent()`（:426）在遍历 `getFragments()` 后直接 `transaction.commit()`（原 :442），**没有** `isStateSaved()` 守卫。同文件其它 4 处入口（`switchType` :271、`focusCategoryButton` :305、`showCategoryContent` :357、`openCustomCsp` :1046）都判了 —— 缺的正是这条延迟回调路径。
3. **「调颜色」正是制造重建窗口的动作。** `AppearanceDialog.chooseUiScale()` 直接 `requireActivity().recreate()`（`app/src/leanback/.../dialog/AppearanceDialog.java:128`）；改主题模式/主题色发 `RefreshEvent.theme()`，`BaseActivity.onSubscribe` 对 `THEME`/`UI_SCALE`/`LANGUAGE` 调 `recreate()`（`app/src/leanback/.../base/BaseActivity.java:168`）。

即：`recreate()` 让旧实例进入 `onSaveInstanceState` 之后，231 秒才返回的配置回调仍然落到旧实例，`commit()` 抛 `IllegalStateException`。

> 日志里 `initView start cost=230924ms` 说明这次运行本身已处在重建/恢复态，长时间配置加载与重建窗口正好重叠。

## 3. 方案比较

- **A. 不改**：崩溃保持。网络慢时（本例 231s）几乎必然复现。
- **B. `commitAllowingStateLoss()`**：不崩，但把事务写进已保存的 FragmentManager 状态；重建后恢复出一个指向已被 `VodConfig.clear()` 的缓存页，用「静默的状态损坏」换崩溃，比现状更糟，也违背仓库既有 4 处「已保存即放弃」的统一约定。**否决。**
- **C. 给延迟回调补宿主存活守卫（选定）**：用一个 `private` 判定 `!isFinishing() && !isDestroyed() && !isStateSaved()`，与既有 4 处条件逐字一致；拒绝提交但照旧清理纯内存状态；重建后的实例由 `initAfterFirstFrame() → initConfig()` 自己加载。

无新增依赖、无公开 API 变更、无数据/偏好/协议变更。

## 4. 改动

全部在生产代码 `app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java`：

| 位置 | 改动 |
| --- | --- |
| `syncCategorySite()` 之后 | 新增 `private boolean canCommitFragmentTransaction()`，条件 = `!isFinishing() && !isDestroyed() && !getSupportFragmentManager().isStateSaved()`（KDoc 记录崩溃证据与为何不用 `commitAllowingStateLoss`） |
| `clearCategoryContent()` | 视图/适配器/`mCurrentType`/`mFolder` 清理**之后**再判守卫：能提交就调 `removeCategoryFragments()`；不能提交但实例仍存活（只是 `isStateSaved()`）时置 `mPendingCategoryCleanup`，回到前台补做 |
| `getVideo(boolean)` | `syncCategorySite();` 之后判守卫：实例仍存活时置 `mPendingContentRefresh`（含 `forceNative` 意图）推迟到 `onResume`；正在结束/已销毁则丢弃 |
| `onResume()` | 末尾调 `resumeDeferredFragmentWork()` —— 先补做分类页移除、再补做被推迟的内容刷新，进入前仍过一次 `canCommitFragmentTransaction()` |
| `canDeferFragmentWork()` / `removeCategoryFragments()` | 新增两个私有方法：前者区分「仍存活但状态已保存」与「正在结束/已销毁」；后者把原先内联在 `clearCategoryContent()` 里的移除事务抽成单点，供即时与推迟两条路径共用 |
| `showHomeContent()` | `hide(mFolder).commit()` 改为 `if (canCommitFragmentTransaction()) …hide(mFolder).commit()`；`clearContentFocusRequest()`/`setUserVisibleHint(false)` 等非事务状态照旧清理 |

关键取舍：`clearCategoryContent()` 的守卫放在状态清理**之后**而不是方法开头。若整段提前 return，`mFolder` 会继续指向一个即将被移除的 Fragment，`isCurrentCategory()`（:1063）会把「当前分类」误判为有效，重建窗口里 `updateFilter`/`focusCategoryButton` 会作用到错误对象。

**未改动**：`showCategoryContent()`（已自带早退）、`FolderFragment`（:105/:121 两次 `commit()` 都在 `initView()`/`openFolder()` 的即时交互路径，日志中无对应栈）、mobile flavor（`HomeActivity` 走 `FragmentStateManager`，`VodFragment` 自己收状态浮层，无此形状栈）。

## 5. 验证（2026-10-22 15:2x–15:3x +08:00，含评审轮修复后重跑）

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| TV 编译 + 定向单测 | `bash ./gradlew --console=plain :app:testLeanbackArm64_v8aDebugUnitTest --tests '…HomeFragmentCommitStateGuardTest' --tests '…HomeSiteSwitchClearsTypesTest' --tests '…HomeCategoryNavigationSourceTest' --tests '…HomeSiteLockSourceTest' --no-daemon` | **BUILD SUCCESSFUL in 1m 16s**；实际执行 `HomeFragmentCommitStateGuardTest` 6/6、`HomeSiteSwitchClearsTypesTest` 1/1，0 failure 0 error |
| mobile 侧同源测试 | `bash ./gradlew --console=plain :app:testMobileArm64_v8aDebugUnitTest --tests '…HomeCategoryNavigationSourceTest' --tests '…HomeSiteLockSourceTest' --tests '…HomeSiteSwitchClearsTypesTest' --no-daemon` | **BUILD SUCCESSFUL in 3m 2s**；13 + 4 + 1 = 18 项，0 failure 0 error |

> 口径更正：`HomeCategoryNavigationSourceTest`（13 项）与 `HomeSiteLockSourceTest`（4 项）在 `app/src/testMobile/` 源集，只有 mobile flavor 会执行；leanback 侧本轮实际跑到的是上面 7 项。两份 flavor 的 Java 源码都在这些任务里编译通过。

新增/更新的 `app/src/test/java/com/fongmi/android/tv/ui/activity/HomeFragmentCommitStateGuardTest.java`（6 项）：

1. `guardHelperRejectsFinishingDestroyedAndSavedState`（守卫三条件 + 推迟谓词只接受存活宿主）
2. `categoryStateIsClearedEvenWhenCommitIsSkipped`（重置项 < 守卫 < `removeCategoryFragments()`；存活宿主必须置 `mPendingCategoryCleanup`；不得退化为 `commitAllowingStateLoss`）
3. `savedCategoryPageNeverCommitsWithoutTheGuard`（`clearCategoryContent()` 内 0 处 `transaction.commit();`，只委托；`removeCategoryFragments()` 内恰 1 处）
4. `homeContentRefreshStopsBeforeReshapingASavedStateHost`（`syncCategorySite()` < 守卫 < `WebHomeTarget.canLoad`；推迟时保留 `forceNative` 意图）
5. `deferredWorkIsResumedWhenTheHostReturnsToForeground`（`onResume()` 调 `resumeDeferredFragmentWork()`；补做前仍判守卫，标志只消费一次）
6. `hidingTheHomeChromeNeverCommitsWithoutTheGuard`

放在 `test/` 而非 `testLeanback/`：与既有 `HomeSiteSwitchClearsTypesTest` 同源集，两个 flavor 都会跑到，且不需要 Robolectric。

**未做的验证（明确记录）**：真机复现。本次故障是「重建窗口 + 慢回调」的时序竞态，本机无该 TCL 设备，需要用户在真机上按压测步骤确认（见下）。这不影响代码级守卫的正确性，但「不再复现」这一条只能由真机数据闭环。

真机压测（用户侧）：① 设置 → 外观 → 改主题色 → 返回首页，连续 3 次；② 改主题模式（系统/浅/深）各一次；③ 改 UI 缩放一次；④ 按 Home 退后台 → 最近任务返回 → 切换站点。确认无崩溃，且新日志中不再出现 `Can not perform this action after onSaveInstanceState`。

## 6. 回滚与交付

- 回滚：撤销本任务的两个原子提交（原实现提交 `cae6e4b6e9` + 评审轮修复提交）即可；无数据迁移、无偏好写入、无 schema 变更。
- 重建路径上被丢弃的那一次刷新由重建后的实例重做；未重建（只是退后台）的路径由 `onResume` 补做，因此不存在「丢了内容」的可观察差异。

## 7. 评审轮

见 `docs/C97-beta-merge-review-dev3-20261022.md` §3 F1：初版把「守卫拒绝提交」等同于「丢弃工作」，在**实例没有重建、只是被保存了状态**（例如配置加载期间用户切到别的应用，回来仍是同一实例）时会丢掉一次首页刷新，并让旧站点的 `FolderFragment` 留在容器里；评审轮改为「存活即推迟、回到前台补做」，并已补测（本节 §5 为修复后重跑结果）。
