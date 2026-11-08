# HOME-NO-CONFIG-NAV-20261020：清数据后首页只剩一个按钮，点播/直播/设置整栏消失

## Recovery anchor

- 目标：无配置（重置/重装/恢复出厂）时首页仍是**正常首页**——导航可用、**不新增任何入口**（添加接口走既有的「设置 → 点播」管理面板）；不再由全屏状态浮层接管首页。两端（leanback / mobile）同一规则。
- 允许路径：`app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java`、`app/src/mobile/java/com/fongmi/android/tv/ui/activity/HomeActivity.java`、`app/src/mobile/java/com/fongmi/android/tv/ui/fragment/VodFragment.java`、两端 `home-state` 契约测试、本文件。
- 保护面：任务开始时工作树干净（仅未跟踪 `.wh_diag/`）；分支 `dev2`，HEAD `5591f92612`。
- 验收：①5557 上 `pm clear` 后冷启动，首页渲染出导航行而不是空态三件套，且导航行里**没有**「添加接口」按钮；②添加接口仍可从「设置 → 点播」的管理面板进入（`HistoryDialog` 的「添加」→ `ConfigDialog`）；③手机端底部导航同样保留、同样无额外入口；④两端契约测试全绿。
- 回滚：撤销本任务原子提交即回到「无配置 = 全屏空态」的旧行为；无数据迁移、无 schema 变更。

## 0. 现象与根因

现象（用户截图 + 5557 现场 `uiautomator dump`）：`pm clear` 后只有「还没有配置接口 / 添加一个点播接口后即可浏览内容。/ 添加接口」，**点播/直播/设置整栏消失**。

根因不是"被藏起来了"，而是**导航行本身就在被隐藏的内容里**：

- 电视端的点播/直播/设置不是独立导航栏，而是首页 `VerticalGridView` 的第一行：
  `HomeActivity.setAdapter()` 把 `mFuncAdapter` 加成一个 `ListRow`，`setFunc()` 才往里填按钮，而 `setFunc()` 只由 `showContent()` 等路径调用。
- `showNoConfig()` 走的是 `progressLayout.showEmpty()` → `switchState(EMPTY)` → `setContentVisibility(false)` → `hideView(recycler)`（`INVISIBLE`）。**导航行随内容一起被隐藏**；即使不隐藏，`setFunc()` 从未执行，这一行也是空的。

历史：`f838870134` 引入全屏错误态、`0f1dc0c615` 引入全屏无配置空态，两次都把"用状态浮层接管首页"当作统一表达；`f838870134` 之前失败路径是 `Notify.show(msg)` + `showContent()`，导航是在的。因此**裸 revert `0f1dc0c615` 修不了导航**：会退回 `error("")` → 全屏错误态，导航照样没有，还会复活已修的两个 bug。

## 1. 决策

保留 `0f1dc0c615` 的语义修正（无配置 ≠ 失败；`BaseConfig` 不再发空 `error("")`；配置可用时自收状态浮层），**只改呈现**：

| 端 | 无配置时的行为 |
|---|---|
| leanback | `showNoConfig()` → `showContent()`（导航/标题/logo 全部就位），导航行只由既有按钮构成 |
| mobile | `VodFragment.showNoConfig()` → `hideState()` + `showNativeContent()`（不再独占整屏），底部导航只由既有项构成 |

两端同一条规则：**无配置时首页保持正常形态，导航可用；不再用一个铺满首页的空态把导航一起接管。**

> 2026-10-20 追加（按用户要求）：第一版在导航行/底部导航里各加了一个「添加接口」入口，用户看过真机截图后明确 **"不需要这个按钮"**，已整段移除（电视端 `noConfigFunc()` 与 `onItemClick` 分支、手机端 `setNoConfigNavEntry()`/`MENU_NO_CONFIG`/`onNavigationItemSelected` 分支、以及两端的配置对话框路由）。现在两端只是"导航回来、内容为空"，添加接口回归既有的「设置 → 点播」管理面板（`HistoryDialog` 的「添加」）。

## 2. 验证（证据）

构建：`assembleLeanbackArm64_v8aDebug` + `assembleMobileArm64_v8aDebug` 成功；
契约测试：`TvErrorStateContractTest`（14 条）+ `MobileHomeStateContractTest` 全绿。

**5557（SM-N9700 / 1920x1080，TV 包）**：`install -r` → `pm clear` → 冷启动 → `uiautomator dump`：

```
text="搜索"  text="收藏"  text="推送"  text="设置"
text="最近观看"  text="更新推荐"  text="默影视"  text="18:55:31"
```

- 导航行回来了，且**没有**「添加接口」按钮（用户要求移除）。
- 注：清数据后 **直播** 不显示（`LiveConfig.hasUrl()` 为假）、**点播** chip 默认不显示（`HomeButton.getDefaultButtons()` 在"首页自动加载点播"开启时不含 id 0）——两者都是既有设计，不是本次回归。

**emulator-5560（手机包）**：`install -r` → `pm clear` → 冷启动 → dump：

```
content-desc="点播"  content-desc="设置"
appBar / toolbar / type / pager / navigation 均在，state 浮层不存在
```

无「添加接口」项，crash 计数 0；首页是正常形态而不是全屏空态。

## 3. 未在设备上覆盖的残留

- 设备上只验证了「清数据 → 冷启动 → 首页正常形态」；**未**再走一遍「设置 → 点播 → 添加接口 → 返回首页」的完整闭环。配置就绪后状态浮层自收仍只在代码路径上验证，且两支是分开的：手机端收浮层由 `VodFragment.dismissStateIfConfigured()`（经 `onRefreshEvent` 的 HOME 分支）与 `showContent()` 负责，底部导航项可见性由 `HomeActivity.setNavigation()` 负责、且它的 `onResume()` 是**有条件**才调（`mobile/HomeActivity:126`）；电视端 `onResume()` 无条件重算 `setFunc()`（`leanback/HomeActivity:1341`）。
- 三语 `home_no_config_title/description/action` 已无任何 `R.string` 引用（入口移除后成为孤儿资源）；仍有两处契约测试以**字面量**做反向断言（`TvErrorStateContractTest:176` 的 `home_no_config`、`MobileHomeStateContractTest:183` 的 `home_no_config_action`/`MENU_NO_CONFIG`），它们拦的是「有人重新插回入口」，不构成引用。刻意保留不删：`HOME-STATE-NO-CONFIG-20261018` 留下的同族文案，且「错误态也保留导航」这个候选任务若实现独立的无用例说明卡可能会复用；删除属可选清理，不在本次行为范围内。
- 两端"错误态"（配置存在但加载失败）仍走全屏错误浮层，导航依旧不可用——这是 `f838870134` 的既有行为，本次按范围未动；如需统一，另开任务。

## 4. 下一步唯一动作

合并/推送由用户决定；如需继续，候选任务是上面的"错误态也保留导航"。
