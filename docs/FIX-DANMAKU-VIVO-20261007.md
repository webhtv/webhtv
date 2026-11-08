# FIX-DANMAKU-VIVO-20261007 — 弹幕接口长按编辑菜单崩溃

## 目标与边界

修复手机弹幕接口输入时，长按／复制／粘贴导致的系统浮动编辑菜单崩溃；保留输入、选择、复制、剪切、粘贴、确定／IME 完成保存、取消不保存，以及现有圆角和日夜主题。

- lane：`quick-fix`；分支：`dev2`。
- 起始 HEAD／回滚锚点：`9b168a1f3805614547308a638a13ddae45a8f564`。
- 初始工作区干净，无需保护的已有未提交文件。
- 仅修改公共 `BaseAlertDialog.builder()` 的主题传参和相应测试；不修改播放器、网络、接口校验、依赖、TV 遥控逻辑或包签名。

## 现场证据与可证伪根因

用户截图：`F:\temp\orca-paste-1791342459920-0657d3f5-bc2a-4ec8-b401-4adea0ea37ce.png`。

- 应用：`5.6.0-beta-202610052020`；设备：Vivo V2307A；Android 14 / SDK 34。
- `android:layout/floating_popup_overflow_button` 第 18 行创建 `ImageButton` 失败。
- 异常链：`InflateException -> ClassCastException`，`LayerDrawable$LayerState` 无法转换为 `VivoListViewSelectorDrawable$ListViewSelectorState`，发生在 OEM `mutate()`。
- 本地路径：`SettingDanmakuFragment.onDanmakuApi -> DanmakuApiDialog -> BaseAlertDialog.builder()`。
- 旧代码将 `MaterialAlertDialog_WebHTV_Rounded`（控件样式）传给 builder 的 `themeResId`。该样式的 `backgroundTint` 因此成为上下文级属性，泄漏到浮动编辑菜单的 AppCompat ImageButton。
- 正确的 `ThemeOverlay.WebHTV.Dialog` 已通过 `alertDialogStyle` 引用同一圆角样式，面板本身不需要改变。
- 可证伪检查：旧代码下系统菜单上下文是否具有错误的全局 `backgroundTint`；替换 OEM selector 为 `mutate()` 抛同类异常的测试 drawable，确认旧上下文失败、新上下文无需调用 `mutate()`。

## 依据与方案

访问日期：2026-10-07。局部既有设计修复，非播放器上游合并或新功能。

1. 本地 `app/src/main/java/com/fongmi/android/tv/ui/dialog/BaseAlertDialog.java`、`app/src/main/res/values/styles.xml`、`webhtv_styles.xml`：直接代码证据，控件样式与主题覆盖层混用。
2. 引入传参的提交：`edde56864e2391d42525bf5a4b582243b4768c19`；这里只确定代码来源，不把它直接宣称为所有 ROM 的首次故障版本。
3. [Android 14 LocalFloatingToolbarPopup](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/core/java/com/android/internal/widget/floatingtoolbar/LocalFloatingToolbarPopup.java)：官方源码 A 级；`applyDefaultTheme` 在原上下文上叠加 DeviceDefault 主题，`createOverflowButton` 使用此上下文的 inflater。OEM 可进一步修改，但自定义 AppCompat 属性不会被平台主题清除。
4. [AppCompatBackgroundHelper](https://github.com/androidx/androidx/blob/androidx-main/appcompat/appcompat/src/main/java/androidx/appcompat/widget/AppCompatBackgroundHelper.java)：官方源码 A 级；读取 `ViewBackgroundHelper_backgroundTint` 并设置背景 tint，解释额外 mutate 的来源。最终以项目实际 AppCompat 1.7.1 的运行测试为准。

选择：使用既有 `ThemeOverlay_WebHTV_Dialog`，让圆角和面板 tint 只通过 `alertDialogStyle` 作用于面板。拒绝禁用长按、屏蔽复制粘贴、全局吞异常、按品牌降级到旧式菜单或更换输入控件。

## 验证记录

已完成。设备使用覆盖安装，未卸载、未清除应用数据。设备测试在退出时恢复原接口和剪贴板。

设备：`192.168.50.3:5557`（Android 9 / SDK 28，x86_64），按项目模拟器分配规则使用。

### RED（修复前代码，证明回归测试真的能抓到本缺陷）

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 源码回归 | `testMobileArm64_v8aDebugUnitTest --tests DialogRoundedCornerSourceTest` | FAIL：`BaseAlertDialog must apply a theme overlay, not leak widget tint into the floating toolbar` |
| 设备 tint 泄漏 | `am instrument -e class ...#floatingToolbarContextDoesNotInheritPanelTint` | FAIL：`panel backgroundTint leaked into system text actions` |

设备用例在旧代码上复现的正是崩溃机制本身：控件样式的 `backgroundTint` 成为对话框上下文级属性，被系统编辑菜单的 `AppCompat` `ImageButton` 继承。

### GREEN（修复后）

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 手机源码回归 | `testMobileArm64_v8aDebugUnitTest --tests DialogRoundedCornerSourceTest` | 2 tests, 0 failures |
| TV 源码回归 | `testLeanbackArm64_v8aDebugUnitTest --tests DialogRoundedCornerSourceTest` | 2 tests, 0 failures |
| 手机设备回归 | `am instrument -w -e class com.fongmi.android.tv.ui.dialog.DanmakuApiDialogDeviceTest` | `OK (3 tests)` |

三个设备用例覆盖：

1. `floatingToolbarContextDoesNotInheritPanelTint`：叠加 DeviceDefault 主题后 `backgroundTint` 不再泄漏；且面板仍解析为 `MaterialAlertDialog.WebHTV.Rounded`（外观保持不变的设备侧证据）。
2. `typingFloatingToolbarCopyCutPasteAndPositiveSaveKeepWorking`：输入、全选、复制、剪切、粘贴、浮动工具栏可创建、正负按钮保存（含首尾空格裁剪）。
3. `cancelDoesNotSaveAndImeDoneStillSaves`：取消不保存；IME 完成键保存。

覆盖安装后应用数据目录（`databases/` 下 exoplayer 与 following/tv 库）逐项比对无变化。

### 未完成与限制

- 本地连接设备为 Android 9 模拟器；型号／厂商标签不等于 vivo Android 14 ROM。已通过真实系统编辑菜单上下文与 tint 泄漏路径验证应用侧故障机制，**不宣称已在 V2307A 真机复测**。
- 通过注入触摸事件驱动平台长按（`dispatchTouchEvent` 与 `sendPointerSync` 两种方式）均无法在该 harness 内触发平台 `CheckForLongPress`，属于测试注入限制，已删除该用例，不把它当作产品结论。系统编辑菜单的可创建性由用例 2 的 `startActionMode(..., TYPE_FLOATING)` 覆盖。

## Recovery anchor

- 状态：已完成并提交。
- 任务文件：本文件；生产目标：`BaseAlertDialog.builder()`；测试：`DialogRoundedCornerSourceTest`、`DanmakuApiDialogDeviceTest`。
- 已改文件：`BaseAlertDialog.java`（1 行）、`DialogRoundedCornerSourceTest.java`、`DanmakuApiDialogDeviceTest.java`（新增）、本文件。
- 提交：`7596ebabb73ca6d1b38273c774513f32cbdb7343`（分支 `dev2`）；恢复标签：`recovery/FIX-DANMAKU-VIVO-20261007/20261007062638-7596ebabb73c`。
- 未决风险：仅限无 vivo Android 14 真机；应用侧诱因已由设备证据证实。
- 下一步：无。如需真机确认，请在 vivo V2307A / Android 14 上覆盖安装后长按弹幕接口输入框验证。
