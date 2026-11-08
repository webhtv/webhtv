# FILE-BROWSER-EMPTY-20261013：本地文件浏览器空态（审计 P0 最后一项）

## Recovery anchor

- 目标：给两端 `FileActivity` 的空态补齐三件套（标题 + 解释 + 主动作），主动作按用户决定为**重新扫描当前目录**。
- 允许路径：`app/src/{leanback,mobile}/java/.../ui/activity/FileActivity.java`、`app/src/main/res/values{,-zh-rCN,-zh-rTW}/strings.xml`、`app/src/test/java/.../ui/activity/FileBrowserEmptyStateContractTest.java`（新增）、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `336010d8380`。
- 验收：① 两端接入三件套；② 主动作走 `update(dir)`（唯一列表填充入口）；③ **不得**接成返回/退出；④ 三语字符串齐备；⑤ 契约测试两端全绿 + 真机确认。
- 回滚：撤销本任务原子提交即可；无数据迁移。
- 交付坐标：提交 `7cc0182c84d`（dev4），recovery tag `recovery/FILE-BROWSER-EMPTY-20261013/20261013174020`；未推送。
- 下一步唯一动作：无（本批已交付）。**审计 P0 至此全部完成**；剩余的是流程工作——§20.2 的九行设备验收矩阵从未被系统执行过。

## 1. 这是审计 P0 的最后一项空态缺口

审计 §4 的 §9.9 覆盖表里，两端 `FileActivity` 是**唯一**没有接入 `setEmptyContent` 的宿主。它此前一直被挂着，原因是"主动作是导航/产品决策"——我连续几批都把它记为"卡在决策上"，没有代拟。

**用户 2026-10-13 裁定：主动作 = 重新扫描。**

## 2. 为什么「重新扫描」是对的（决策理由，写进代码注释与契约）

| 候选 | 问题 |
|---|---|
| **返回上级** | **根目录没有上级**——在根目录点它会退出页面，与按钮文案不符；且它不解决"文件刚被放进来"的场景 |
| **重新扫描** ✓ | 在**任何**目录都成立；能反映外部变化（刚用别的应用放进来的文件）——这正是本地文件浏览器最需要的能力 |

## 3. 实现要点

两端 `FileActivity` 结构完全一致：

```java
private void update(File dir) {
    ...
    mAdapter.addAll(this.dir = dir, list(dir), selectDir);
    mBinding.progressLayout.showContent(true, mAdapter.getItemCount());   // 唯一的状态驱动点
}
```

`update(dir)` 是**唯一**的列表填充入口，既重算 `list(dir)` 又驱动状态机。所以重新扫描直接复用它：

```java
private void rescan() {
    update(dir == null ? Path.root() : dir);
}
```

`dir == null` 的兜底是必要的：权限回调（`checkPermission` → `update(Path.root())`）之前 `dir` 还是 null，此时点重新扫描应该退回根目录而不是 NPE。

**为什么必须复用 `update` 而不是"只刷新适配器"**：若接成 `notifyDataSetChanged()` 之类，"扫描后仍为空"就不会重新进入空态，用户会看到留白——这正是本仓库反复出现的那类缺陷（状态机没被驱动）。

### 3.1 一个容易被忽略的细节：`selectDir` 模式只列目录

两端的 `list(dir)` 在 `selectDir` 为真时**只返回子目录**（`dir.listFiles(File::isDirectory)`），所以"这个目录里有文件但没有子目录"在选目录模式下也会是空态。空态文案因此写成"这个目录下没有**可显示的项**"，而不是"这个目录是空的"——避免误导。

## 4. 实施

1. 三语字符串 `file_empty_title` / `file_empty_description` / `file_empty_action`（简中标题「这里没有内容」、动作「重新扫描」；解释里点明"如果是刚放进来的文件，重新扫描即可看到"）。
2. 两端 `FileActivity`：`initView` 调用新增的 `setEmptyContent()`；新增 `rescan()`。
3. `FileBrowserEmptyStateContractTest`（4 项，main 源集，两端共享）：
   - 两端接入三件套且三语齐备；
   - 主动作走 `update(...)`，且 `update` 仍驱动状态机；
   - **反例守卫**：`rescan()` 不得调用 `finish()` / `onBackPressed()` / `getParentFile()`——"返回上级"在根目录会退出页面，这是本契约要防的具体错误；
   - 文案必须表达"这里没有内容"而非"加载失败"（§9.9 把空态与错误态分开）。

## 5. 不变量与回滚

- 不改动 `list(dir)` 的过滤逻辑（`selectDir` 只列目录的行为保持）。
- 不改动权限流程、目录进入/返回、文件选择与阅读器打开的既有行为。
- 不改动 `ProgressLayout`（只调用它的既有接口）。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 6. 验证记录

### 6.1 定向 JVM 测试

- `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "...FileBrowserEmptyStateContractTest"` → 首轮**编译失败**（见 §6.3），补 import 后 **BUILD SUCCESSFUL in 43s**。
- `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "...FileBrowserEmptyStateContractTest"` → **BUILD SUCCESSFUL in 31s**。
- `.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug` → **BUILD SUCCESSFUL in 1m 14s**。

**环境限制**：本会话无可用 bash，`.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。

### 6.2 设备实测（`192.168.50.3:5561`，装 **leanback flavor**）

| 检查项 | 结果 |
|---|---|
| 打开文件浏览器（`am start ... FileActivity`） | 正常，列出 `/sdcard` 顶层 |
| 进入空目录 `Alarms` | **空态三件套完整出现**：插画 + 「这里没有内容」+ 解释「这个目录下没有可显示的项。如果是刚放进来的文件，重新扫描即可看到。」+ 「重新扫描」按钮；**且带 scrim**（壁纸被压暗） |
| **主动作是否真的重新扫描** | **是**——见下 |
| 有无崩溃 | 无 |

**主动作的决定性验证**：往 `/sdcard/Alarms` 里放一个 `rescan-proof.txt`（模拟"刚用别的应用放进来的文件"），然后点「重新扫描」→ **该文件出现**，空态退出、进入 CONTENT 态。

这一次观测同时证明了四件事：① 空态三件套渲染正确；② scrim 生效（上一批的改动在本页也生效）；③ **主动作真的重新扫描**（不是死按钮）；④ 扫描后状态机正确切换。

截图：`.design-evidence/e5561-m9-empty.png`（空态）、`e5561-m9-rescanned.png`（重新扫描后）。

**数据变更披露**：测试文件 `rescan-proof.txt` 已删除，`Alarms` 目录恢复为空（`ls -A | wc -l` = 0）。

**过程说明**：进入目录需要**长按**（`input keyevent DPAD_CENTER` 不会进入，用 `input swipe x y x y 1200` 模拟长按才生效）。该模拟器上 `dumpsys window | grep mCurrentFocus` 仍不可靠，以截图为准。

### 6.3 首轮失败：`R` 未导入（编译错误，不是断言失败）

首轮两个 flavor 都 `BUILD FAILED`，且**没有测试结果文件**——说明是编译失败而非断言失败。编译错误：

```
FileActivity.java:72: 错误: 程序包R不存在
```

两端 `FileActivity` 此前**没有** `import com.fongmi.android.tv.R`（它们原本只用 `databinding.*Binding` 与 `R` 无关的资源）。补上 import 后通过。

**教训**：`BUILD FAILED` 时先看**有没有测试结果 XML**——没有就是编译失败，去跑 `compile*UnitTestJavaWithJavac` 拿错误，不要在测试报告里找。

### 6.4 结论

审计 P0 的**最后一项空态缺口**关闭。两端文件浏览器的空态达到 §9.9 的三件套，主动作按用户裁定为「重新扫描当前目录」并已实机证明有效。

### 6.5 残余风险与诚实说明

- **只在 leanback flavor 上做了实机验证**：手机端 `FileActivity` 的改动是**同构的**（同一套 `setEmptyContent` + `rescan`，同一批字符串），由共享的契约测试覆盖；但未在手机端实机点过。
- **`selectDir` 模式（选目录）的空态未实机验证**：该模式只列子目录，所以"有文件但无子目录"也会是空态。文案写的是"没有可显示的项"以覆盖这种情况，但没有实测。
- **权限被拒的分支未验证**：`checkPermission` 的拒绝路径当前没有可见反馈（列表为空但不显示空态？还是显示空态？）——这取决于 `PermissionUtil.requestFile` 的回调是否被调用。见 §7.2。
- **重新扫描不会自动监测外部变化**：它只在用户点按钮时重扫，没有 `FileObserver`。这是刻意的（避免常驻监听），但意味着"文件放进来了但用户没点重新扫描"时列表仍是旧的。

## 7. 相邻发现（未纳入本批）

1. **审计 P0 至此全部完成**（两处契约盲区、`item_following` 固定色、描述文字对比度、静态门槛可执行化、本项）。**剩余的是流程工作**：§20.2 的九行设备验收矩阵从未被系统执行过。
2. **`FileActivity` 没有错误态**：`checkPermission` 的拒绝分支（用户不授权）当前没有可见反馈，只是列表为空。那属于错误态而非空态——需要先确认"权限被拒"与"目录为空"在用户看来是否应当区分。
3. **`selectDir` 模式的空态文案未做差异化**：现在两种模式共用同一套文案。若将来要为选目录模式单独写一句（例如"这个目录下没有子目录可选"），需要给 `setEmptyContent` 传不同的 res。
