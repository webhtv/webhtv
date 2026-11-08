# C119 · dev2 合并 origin/beta 后的改动评审

- 日期：2026-11-05（合并与首轮评审），收尾于 2026-11-06 凌晨
- 分支：`dev2`
- 评审对象：合并 `origin/beta` 后 dev2 上未推送的全部改动
- 结论：**通过**（修复 1 处 P1 + 4 处 P2 后）

## 1. 合并坐标

| 项 | 值 |
| --- | --- |
| 合并前 `dev2` | `4f24d6dc1f`（fix(lab): preserve command editor parameter focus order） |
| 目标 `origin/beta` | `e1ba1e18e1`（Merge pull request #529 from Silent1566/dev1） |
| 合并方式 | ort，无冲突 |
| 带入提交 | 4 个：`ec56e4a598`（详情页芯片内边距改 dp 换算）、`a19433f7ef`（详情内联旋转保持用户选择）、`37bba96d24`/`e1ba1e18e1`（PR #526 / #529 合并提交） |
| 合并后 `dev2` | `efa732aa7b` |

合并前工作区干净（`git status` 无输出），无需 stash。合并带入的 `docs/C117`、`docs/C118` 分别是 dev3、dev1 的评审记录。

## 2. 评审范围裁剪

用户允许「先前已评审过且无合入冲突的改动可跳过」。据此核对：

- beta 带入的 `ec56e4a598`、`a19433f7ef` 已分别在 `docs/C118-beta-merge-review-dev1-20261105.md`、`docs/C117-beta-merge-review-dev3-20261105.md` 中评审通过并交付，本次不重复评审。
- 合并前 dev2 相对 `origin/beta` 领先的 4 个提交是本轮唯一未被任何评审覆盖的改动，全部落在 Lab（实验室）命令列表与编辑弹窗的遥控器焦点/长按行为上：

| 提交 | 自述目标 |
| --- | --- |
| `c2c73f18fe` | 遥控器焦点在「运行」按钮上时长按 OK 也能打开编辑弹窗 |
| `343338678d` | 编辑弹窗上下键能在控件间移动焦点 |
| `2728842b87` | 触摸模式下上下键仍能离开输入框 |
| `4f24d6dc1f` | 显式焦点链要覆盖参数行，不能跳过 |

## 3. 评审方式

Standards 轴（仓库 `AGENTS.md` + Fowler 坏味道基线）与 Spec/行为轴（修复是否成立、是否引入回归）分开评审。Standards 轴出结论后主智能体自行复核；Spec 轴子智能体多次未产出结论，改由主智能体直接读代码 + 上机验证补足。

## 4. 发现的问题与修复

### P1（严重）：焦点链里的 null 槽位会静默吞掉上下键

`AlertDialog` 只在「编辑命令」时调用 `setNeutralButton`（`LabCommandEditDialog.java:68-73`），「新建命令」场景下 `dialog.getButton(AlertDialog.BUTTON_NEUTRAL)` 返回 `null`。原实现无条件 `chain.add(...)`，于是链里出现 null 元素；`releaseVerticalKeys` 取到 null 后既不移动焦点又 `return true`，按键被消费掉——从「自动运行」开关按 DOWN、从「取消」按 UP 都毫无反应，遥控到不了「取消/添加」。

修复：新增 `addToChain(List<View>, View)`，null 与不可见按钮都不占链槽（`:121-129`），三个调用点改为 `addToChain`。同时删掉 `releaseVerticalKeys` 里因此不再可达的 `next == null` 分支。

### P2：迁入 `onShow` 的保存逻辑未重新缩进

`setOnShowListener` 包裹 `setPositiveButton` 的点击实现后，内部 14 行仍是原缩进，代码层级与视觉层级不符。修复：整体 +4 缩进（`:84-99`）。

### P2：布局的 `nextFocusUp/Down` 与 Java 焦点链是两套机制

`dialog_add_command.xml` 新增的 4 个 `nextFocus*` 属性永远不会生效：`View.OnKeyListener` 先于 `onKeyDown`/焦点搜索执行，而链上的监听器对 `DPAD_UP`/`DPAD_DOWN` 一律 `return true`。更糟的是 `desc → inputCommand` 指向的控件在带变量的命令里会被 `setFocusable(false)`。两套机制并存时改错一处也不会有测试察觉。

修复：删除这 4 个属性，布局回落为与 `origin/beta` 完全一致（本提交对该文件的净改动为 0）；焦点顺序的唯一来源是 `LabCommandEditDialog` 的链，并由测试锁定。

### P2：`LabCommandAdapter` 两个长按监听器逐字重复

`itemView` 与 `btnAction` 的 `OnLongClickListener` 函数体完全相同。修复：提取一个 `View.OnLongClickListener` 局部变量同时设给两个 View（`LabCommandAdapter.java:108-115`）。这正是语义要求——焦点落在按钮上时按键只发给按钮，两处必须同一行为。

### P2：注释里存在无仓库证据的因果陈述

原代码与测试注释写了「实测（emulator-5560）… 在「删除」按钮与最后一行参数之间来回弹跳」「mInTouchMode=true」等结论，但 `docs/LAB-TV-REMOTE-FOCUS-20261013.md:194` 明确把 `LabCommandEditDialog` 列为当时不在范围内的页面，仓库内没有该弹窗的实机记录；且同文档 `:15` 为遥控路径记录的正是 `mInTouchMode=false`。修复：改为意图描述（「顺序取自布局树而不是几何搜索，因为可滚动弹窗里 focusSearch 的结果取决于当前滚动位置」「触摸模式下文本控件同样吃掉上下键」），不再声称实测。

### P3：焦点链按具体控件类型白名单收集会漏收

原 `collectFocusChain` 只收 `instanceof EditText || instanceof MaterialSwitch`。参数行实际用了 `TextInputEditText`（`input`）、`MaterialAutoCompleteTextView`（`dropdown`）与 `MaterialSwitch`（`switchView`），白名单式收集在类型变化时会静默漏收。修复：改为「VISIBLE 且非 ViewGroup 且 `isFocusable()`」收集，并显式排除已被 `setFocusable(false)` 的命令框（`:109-119`）。

### 其余收敛项

- 删除 `if (!chain.isEmpty())` 死防御（链尾至少有「添加/保存」按钮）：`chain` 恒非空。
- `android.view.ViewGroup` 行内全限定名改为 `import android.view.ViewGroup`。
- `addToChain` 的 JavaDoc 补齐「null 或不可见的按钮都不占链槽」的实际行为。
- `LabTvFocusSourceTest`：三条用例改用既有 `methodBody()` 做区间断言，避免整文件 `contains` 被同文件其它代码意外满足；补齐 4 条缺失的断言 message；给「布局不得声明 nextFocus」补一条正向锚点，避免读错文件时断言恒真。

## 5. 验证

```
.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.lab.*"
→ BUILD SUCCESSFUL
→ test-results: 7 个套件 / 53 个用例 / 0 失败 / 0 错误
   （含新增 commandEditorBuildsTheFocusChain、commandEditorDoesNotDeclareNextFocus）
```

```
.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug
→ BUILD SUCCESSFUL
→ app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk
```

APK 已安装到 `emulator-5554`（`adb install -r` → `Success`）。`LabActivity` 在 manifest 中是 `android:exported="false"`，`adb shell am start` 与 `run-as ... am start` 均被系统以 SecurityException 拒绝，因此本轮**未取得设备端的焦点行为证据**，见第 6 节。

## 6. 未定证项与残余风险

- **弹窗焦点链没有设备端实测**：`LabActivity` 未导出，无法用 adb 直达实验室页面。链的正确性目前由源码级契约测试（`LabTvFocusSourceTest`）与 framework 语义推导保证，等有可直达的调试入口或从应用内导航进入后再补一次 `uiautomator dump` 复核。
- **`file` / `directory` 参数行的 endIcon 遥控可达性未验证**：修复后的链收集非 ViewGroup 的可聚焦叶节点，`TextInputLayout` 的 endIcon 是其内部子 View。核对 `origin/beta` 版本（只有 `setEndIconOnClickListener`、没有任何焦点链与 `OnKeyListener`）可确认这不是本轮引入的回归，而是 beta 既有的能力缺失；若放宽后的链把它收进来，属可达性增强。
- **`addToChain` 的参数序与 `collectFocusChain` 相反**（`(chain, view)` vs `(view, chain)`）：保留现状，因为两者语义不同（一个是追加按钮、一个是递归收集），强行统一会牺牲可读性。
- `chain.get(0).requestFocus()` 未判空：链尾的「添加/保存」按钮恒存在且可见，链为空不可达；若将来改成可隐藏主按钮，需重新评估。该返回值也未检查——目标不可聚焦时焦点留在原地，属可接受的降级。

## 7. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `efa732aa7b` |
| 评审修复提交 | 见本次 task_guard finish 产生的提交 |
| recovery tag | 见本次 task_guard finish 产生的 `recovery/C119/*` |
| 推送分支 | `dev2` |
| PR | 到 `beta` |
