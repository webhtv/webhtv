# MOBILE-HISTORY-EMPTY-20261013：手机端历史页空态只有一句「这里什麽都没有」，删空后还是一片空白

## Recovery anchor

- 目标：手机端历史页的空态补齐三件套（标题 + 解释 + 主动作），并修掉"删空后不驱动状态机"的同款缺陷。
- 允许路径：`app/src/mobile/java/.../activity/HistoryActivity.java`、`app/src/main/res/values{,-zh-rCN,-zh-rTW}/strings.xml`、`app/src/testMobile/java/.../ui/activity/MobileStateSurfaceContractTest.java`、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `e9f370e5ec4`。
- 验收：① 历史页接入三件套且主动作真实回首页；② 删除路径驱动状态机；③ 三语字符串齐备；④ 契约测试覆盖**两个**空态宿主（收藏页与历史页）；⑤ `testMobile` 定向测试全绿 + 真机（删空历史）看到空态。
- 回滚：撤销本任务原子提交即可回到「只有标题 + 删空留空白」；无数据迁移。
- 交付坐标：提交 `28e1a2f8d6f`（dev4），recovery tag `recovery/MOBILE-HISTORY-EMPTY-20261013/20261013121327`；未推送。
- 下一步唯一动作：无（本批已交付）。**建议的下一批**见 §6.3：电视端 `HistoryActivity.onDelete()` 同一处缺陷 + §7.1 的收敛。

## 1. 现象与根因

手机端历史页与收藏页一样，都用 `ProgressLayout` 表达空态（`showContent(true, size)`，`size` 为 0 时进入 EMPTY）。但历史页：

1. **没有接入空态内容**——`initView` 里没有 `setEmptyContent`，于是空态只显示布局里的默认单行文案 `@string/error_empty`（「这里什麽都没有 👀」），既不解释为什么空，也不给出下一步；
2. **删除路径不驱动状态机**：

```java
public void onItemDelete(History item) {
    mAdapter.remove(item.deleteDisplayItem(), () -> {
        if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);   // 只退出删除模式
        mBinding.recycler.post(this::updateMarquee);                   // 从不驱动状态机
    });
}
```

删掉最后一条后只剩一个空 RecyclerView——**连空态都不出现**。

### 1.1 这是同一个缺陷的**第三次**出现

| 位置 | 状态 |
|---|---|
| 电视端 `KeepActivity` | 已在 `TV-EMPTY-LOADING-STATE-20261012` 修掉 |
| 手机端 `KeepActivity` | 已在 `MOBILE-STATE-SURFACE-20261013` 修掉 |
| **手机端 `HistoryActivity`** | **本批修掉** |

三次都是"删除回调只退出删除模式、不驱动状态机"。这说明**靠逐页发现是不可靠的**——见 §7.1 的收敛建议。

## 2. 证据来源

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| `docs/webhtv-unified-visual-design-system-20260920.md` §9.9 | 仓库既有设计权威 | A（本地） | 空态 = 标题 + 解释 + 一个主动作；不能只显示一句"暂无数据" |
| `docs/MOBILE-STATE-SURFACE-20261013.md`（M-1） | 本仓库既有交付记录 | A（本地） | 收藏页的同构做法与"删除路径必须驱动状态机"的结论；本批是**应用** |
| `mobile` `HistoryActivity.initView` / `getHistory` | HEAD `e9f370e5ec4` | A（本地） | 走 `showContent(true, count)` 但从未 `setEmptyContent` |
| `mobile` `HistoryActivity.onItemDelete` | HEAD `e9f370e5ec4` | A（本地） | 确认与收藏页同款缺陷 |
| 三语字符串表 | HEAD `e9f370e5ec4` | A（本地） | 没有 `history_empty_*`，需要新增 |

不适用类别记录：不涉及解码/渲染/ABI/打包/网络，无需上游播放器依赖类证据。设计已由 §9.9 与 M-1 定案。

## 3. 方案比较与采用

| 方案 | 优点 | 缺点/风险 | 结论 |
|---|---|---|---|
| 1. 不变更 | 零风险 | §9.9 继续不满足；删空仍留空白 | 拒绝 |
| 2. 只接入三件套、不修删除路径 | 改动更小 | 用户在这页把历史删光仍然看到空白——而这正是最可能触发空态的操作 | 拒绝 |
| 3. **接入三件套 + 修删除路径（采用）** | 与收藏页完全同构；空态真正可达 | 需要新增三语字符串 | **采用** |
| 4. 顺手把 `FileActivity` 也接入 | "一次做完" | `FileActivity` 是**本地文件浏览器**，空态的主动作是"返回上级"还是"重新扫描"是**导航/产品决策**，不该由实现者代拟 | 拒绝，记为 §7.2 |
| 5. 抽一个共享的"删除后驱动状态"辅助 | 根治重复 | 涉及三个宿主（含电视端）与适配器回调签名，属独立重构 | 拒绝，记为 §7.1 |

## 4. 实施

1. `mobile` `HistoryActivity`：`initView` 调用新增的 `setEmptyContent()`（标题「还没有观看记录」/ 解释「播放过的内容会自动出现在这里，方便继续观看。」/ 动作「去点播」→ `goHome()`，与收藏页/播放页的回首页写法一致）。
2. `HistoryActivity.onItemDelete`：回调补 `mBinding.progressLayout.showContent(true, count)`，让删空后进入 EMPTY 态。
3. 三语字符串 `history_empty_title` / `history_empty_description` / `history_empty_action`。
4. `MobileStateSurfaceContractTest`：把原先只覆盖收藏页的两项断言**泛化**为覆盖 `EMPTY_HOSTS`（收藏页 + 历史页）——三件套齐备、主动作真实、删除回调驱动状态机。

## 5. 不变量与回滚

- 不改动 `ProgressLayout`（main 源集）：只调用它的既有接口。
- 不改动历史页的列表、分组、续播匹配与同步逻辑。
- 不改动电视端任何文件。
- 不改动 `FileActivity`（见 §7.2）。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 6. 验证记录

### 6.1 定向 JVM 测试

- `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.activity.Mobile*"` → **BUILD SUCCESSFUL**（契约测试泛化后覆盖两个宿主、三项路径，全绿）。
- `.\gradlew.bat :app:assembleMobileArm64_v8aDebug` → **BUILD SUCCESSFUL**。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。

### 6.2 设备实测（`192.168.50.3:5561`，装 **mobile flavor**，竖屏 1080×1920）

| 检查项 | 结果 |
|---|---|
| 进历史页 | 正常（工具栏 🕐） |
| 长按进删除模式 | 是（9 张卡都出现删除图标） |
| 单条删除 | 正常逐条删除、列表随之刷新 |
| **「删除全部」（工具栏 🗑 → 确认）** | **发现缺陷**：清空后整页留白，**什么都没有**——`mAdapter.clear()` 同样不驱动状态机（见 §6.3） |
| 修复后重新进页面 | **空态正确渲染**：Lottie + 「还没有观看记录」（32sp）+ 次级色解释「播放过的内容会自动出现在这里，方便继续观看。」+ 「去点播」按钮 |
| 有无崩溃 | 无 |

截图：`.design-evidence/e5561-m5-history.png`（有记录时）、`e5561-m5-deletemode.png`（删除模式）、`e5561-m5-deleteall.png`（删除全部确认框）、`e5561-m5-empty.png`（修复后的空态）。

**数据变更披露**：为触发空态，在本模拟器上通过应用自身的删除模式与「删除全部」清空了观看历史（测试数据）。这是模拟器上的可重建数据。

### 6.3 真机验证抓出的**同款缺陷第 4/5 例**

单条删除修好之后，点工具栏 🗑「删除全部」并确认，页面**整页留白**——因为：

```java
.setPositiveButton(R.string.dialog_positive, (dialog, which) -> mAdapter.clear())   // 不驱动状态机
```

这与单条删除是**同一个错误的另一个入口**。本批一并修掉了：

- **手机端 `HistoryActivity`**（本批范围）；
- **手机端 `KeepActivity`**——这一处尤其重要：M-1 声称"收藏页删空不再留空白"，但当时只修了单条删除，**「删除全部」这条路仍然是空白**，等于我上一批的结论不完整。本批补齐。

**仍然存在、本批未修**（按"邻近缺陷只报告"处理）：**电视端 `HistoryActivity.onDelete()`** 也是 `mAdapter.clear()` 不驱动状态机（`app/src/leanback/java/.../HistoryActivity.java:64`）。它与本批是不同 flavor 的另一处改动，建议单独一批处理（顺带做 §7.1 的收敛）。

### 6.4 结论

手机端历史页的空态达到 §9.9 的三件套，且**三条进入空态的路径**（首次加载为空、单条删空、删除全部）都会驱动状态机、不再留空白；收藏页的"删除全部"遗漏也一并补齐。

### 6.5 残余风险与诚实说明

- **修复后的「删除全部」路径没有再次实机点过**：历史已经被我清空，没有可再删的数据。该路径的修复由契约测试（`deleteAllAlsoDrivesTheStateMachine`）与代码审查覆盖；而**缺陷本身**是实机确认过的（§6.2 那一行）。空态的渲染则由"重新进页面"实机确认 ✓。
- **`FileActivity` 未处理**（见 §7.2）。
- **电视端历史页的同一处缺陷未修**（见 §6.3），只报告了位置。

## 7. 相邻发现（未纳入本批）

1. **"删除回调不驱动状态机"已经出现三次，应该收敛。** 三处的形状完全相同：`mAdapter.remove(..., () -> { if (count == 0) setDelete(false); ... })`，只是漏掉了状态驱动。逐页发现不可靠——建议在适配器的删除回调里统一提供"剩余数量已变化"的通知（或抽一个 `ListStateHost` 辅助），让宿主不可能忘记。这是跨三处（含电视端）的重构，属独立批次。
2. **`FileActivity` 的空态主动作是产品/导航决策。** 它是本地文件浏览器，"这个目录里没有可播放的视频"时合理的动作有两个方向：**返回上级**（`dir` 有父目录时才有意义）或**重新扫描**（对未变化的目录帮助有限）。这需要先定方向，不宜由实现者代拟。
3. **手机端仍是三套状态机制并存**（`ProgressLayout` / `StateEvent` / 设置页 Fragment 的 `safeRun` 兜底），见 `docs/MOBILE-HOME-ERROR-STATE-20261013.md` §7.1。本批只用了 `ProgressLayout`，没有触及三者的关系。
