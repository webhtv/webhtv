# SITE-HEALTH-UI-01 播放链路健康报表弹窗底部「取消」按钮被系统导航栏裁切

- 任务：`SITE-HEALTH-UI-01`（quick-fix）
- 日期：2026-10-22（Asia/Shanghai）
- 基线：`dev3` @ `fbcd47fbc1a49b004a9f4d2026a91cb3a1b56c75`

## 现象

用户小米手机（竖屏、手势导航）反馈：从「设置 → 增强功能 → 站点健康排序 → 查看报表」打开的「播放链路健康报表」弹窗，最底部「取消」按钮只露出上半截，被屏幕下沿切掉。

## 根因

`SiteHealthReportDialog.onStart` 在修改前直接用**原始显示尺寸**决定弹窗窗口大小：

```java
int margin = ResUtil.dp2px(ResUtil.isLand(requireContext()) ? 24 : 16);
params.width  = Math.max(1, ResUtil.getScreenWidth(requireContext())  - margin * 2);
params.height = Math.max(1, ResUtil.getScreenHeight(requireContext()) - margin * 2);
window.setLayout(params.width, params.height);
```

- `ResUtil.getScreenHeight()`（`app/src/main/java/com/fongmi/android/tv/utils/ResUtil.java:60-73`）不扣系统栏 insets：API 30+ 取 `getCurrentWindowMetrics().getBounds()`；应用本身贴边绘制（实测本机 `HomeActivity` 窗口就是 `[0,0][1080,1920]`），API 28 的 `getDefaultDisplay().getMetrics()` 同样返回整块显示。
- 浮动对话框窗口由窗口管理器放进「系统栏安全区」并居中。窗口比安全区高出一个状态栏 + 导航栏/手势条的高度，多出来的部分落在安全区之外，最后一行（`close` 按钮 40dp + 根布局 `paddingBottom=12dp`）就被导航栏/手势区盖住。
- 同一缺陷、同一修法在本仓库已有先例：主题色彩编辑器的保存按钮被导航栏裁切，见 `docs/THEME-EDITOR-20261001-inline-preview.md`，修复落在 `ThemeDialogLayout` + `ThemeDialog.configureWindow`。

## 采用方案

只把**尺寸基准**从「原始显示边界」换成「系统栏安全区」，复用已被单测覆盖的 `ThemeDialogLayout` 数学，不再发明第二套计算：

- `SiteHealthReportDialog.onStart` 改为调用 `configureWindow(Window)`：
  `ThemeDialogLayout.safeArea(screenW, screenH, insets)` → `ThemeDialogLayout.width/height(area, margin)`；边距保持原有的 16dp（竖屏）/24dp（横屏），电视几何不变。
- insets 三级取值（与 `ThemeDialog` 同一契约）：① 弹窗窗口自身的 `systemBars() | displayCutout()`；② 全 0 视为未知 → 宿主 Activity 根窗口 insets；③ 仍未知 → `ThemeDialogLayout.Insets.ofContent(显示尺寸 − content 视图尺寸)`。
- `bottom` 额外取 `max(systemBars.bottom, systemGestures.bottom)`：部分 OEM 手势导航把 `systemBars().bottom` 报小/报 0，而手势条仍盖住窗口；左右手势区不参与 max，避免三键导航手机上弹窗被无谓缩窄。
- `params.gravity = Gravity.CENTER`，不设 `params.x/y`（显式偏移会被窗口管理器按显示边界重新钳制）。
- 装 `ViewCompat.setOnApplyWindowInsetsListener`：首帧 insets 可能未就绪，值变化时重算窗口，只在实际变化时重设，避免布局循环。

### 备选与拒绝理由

| 备选 | 结论 |
| --- | --- |
| 改成全屏页 | 拒绝：改变返回键/生命周期与 mobile/leanback 既有交互契约，风险大于收益 |
| 只给根布局加底部 padding | 拒绝：窗口本身越出屏幕，padding 只能把内容上移，底部那条仍在屏幕外 |
| 把安全区逻辑搬进 `BaseAlertDialog` | 本单拒绝：会触及 77 个弹窗的基类；列为后续独立审计 |
| 调小/去掉 margin | 拒绝：不解决窗口越界，只把裁切藏起来 |

## 验证

- 定向单测 + 双 flavor 编译（一次调用，`BUILD SUCCESSFUL in 2m29s`）：
  - mobile `SiteHealthReportDialogSourceTest` 3/3（含新增 `dialogSizesItsWindowFromTheSystemBarSafeArea`）、`ThemeDialogLayoutTest` 11/11；
  - leanback `SiteHealthReportDialogSourceTest` 3/3；
  - 0 failure / 0 error / 0 skipped。
- 设备实测（`emulator-5554`，LIO-AN00，API 28，280dpi；`wm size 1080x1920` 竖屏 + `wm overscan 0,0,0,90` 模拟底部导航栏，`mStable=[0,42][1080,1830]`）：
  - 报表弹窗窗口 frame `[28,70][1052,1802]`，尺寸 1024×1732；左/上/右/下到安全区各 28px = 16dp；
  - 计算核验：宽 `1080−2×28 = 1024` ✓，高 `1920−42−90−2×28 = 1732` ✓；
  - 「取消」按钮完整可见，窗口底距导航栏顶端 28px；`input tap 540 1739` 后弹窗窗口消失，证明按钮不在导航栏下方且可点；
  - 旧实现按整块显示计算：高 `1920−56 = 1864` > 安全区 1788，越界 76px，底部行正好落在导航栏里 —— 与用户截图现象一致；修复后窗口矮 132px，即去掉了重复计入的状态栏+导航栏空间。
- 设备状态已恢复：`wm size reset`；`wm overscan 0,0,0,90`（初始基线，`mStable` 回到 `[0,42][1920,990]`）；`accelerometer_rotation=1`、`user_rotation=0`；`debug_log=true`（`run-as` 回读确认，过程中误触已还原）。
- 未覆盖：用户小米真机的最终目视确认（本机只有 API 28 设备；用户装包后自查即可）。数值上修复已把窗口放进安全区，剩余风险仅是 OEM 手势条完全没有 insets 上报的极端情况，已用手势区 bottom 兜底。

## 回滚

回退本单元提交（`task_guard finish` 生成的 commit 及 recovery tag）即可。无 schema、无持久化格式、无资源/字符串、无依赖变化；`ThemeDialogLayout` 计算方法未动，主题编辑器行为保持原样。

## 后续建议（不在本单范围）

仍按「原始显示尺寸 × 百分比」定窗口高度的全屏弹窗存在同型风险，建议单独任务审计：`GroupRuleDialog`、`PanNetworkDiagnosticDialog`、`ShellProxyDialog`、`WebHomeExtensionDialog`、`PlayerButtonConfigDialog`、`ViewingRecordSyncDialog`、`PlaybackRemoteSyncDialog`、`PlaybackWebhookDialog`、`ManagePageDialog`、`MpvConfigDialog`、`GitCloudDialog`、`MultiThreadProxyDialog`、`LoginStatePathDialog`。（以上 13 个已由后续任务 `DIALOG-SAFE-01` 统一覆盖，见 `docs/DIALOG-SAFE-01-dialog-system-bar-safe-area-20261022.md`。）

## Recovery anchor

- 目标/验收：带底部导航栏/手势条的手机上，报表弹窗「取消」完整可见可点；电视与横屏尺寸不变。
- 计划状态：实现、守卫单测、双 flavor 编译、设备 frame/点击验证均完成；剩余提交与 recovery tag。
- 范围：`SiteHealthReportDialog.java`、`ThemeDialogLayout.java`（仅注释）、`SiteHealthReportDialogSourceTest.java`、本文件。
- 已验证：mobile/leanback 定向单测 3/3 + 11/11 + 3/3；双 flavor Java 编译通过；设备 frame `[28,70][1052,1802]`、点「取消」后窗口消失。
- 回滚锚点：`fbcd47fbc1a49b004a9f4d2026a91cb3a1b56c75`。
- 下一步唯一动作：`bash .codex/scripts/task_guard.sh finish --verified ... --commit-message ...`（原子提交并创建本地 recovery tag）。
