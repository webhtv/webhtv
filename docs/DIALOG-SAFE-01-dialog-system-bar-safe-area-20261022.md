# DIALOG-SAFE-01 全屏/近全屏弹窗统一按系统栏安全区定尺寸

- 任务：`DIALOG-SAFE-01`（standard）
- 日期：2026-10-22（Asia/Shanghai）
- 基线：`dev3` @ `1bb216efec156709ca3d1b027701c6331749d986`（`SITE-HEALTH-UI-01` 之后）
- 前序任务：`SITE-HEALTH-UI-01`（`docs/SITE-HEALTH-UI-01-health-report-bottom-inset-20261022.md`）

## 背景

`SITE-HEALTH-UI-01` 修好了「播放链路健康报表」弹窗底部按钮被导航栏裁切的问题，根因是**窗口尺寸取自
整块显示（`ResUtil.getScreenHeight` 等），而不是系统栏安全区**。用户要求把同型风险的地方一起修掉。

全仓排查后，同型写法分三类：

1. **窗口高度 = 屏高 × 系数**（系数 ≥ 0.9 时必然越出安全区；0.88 在底部导航栏 + 状态栏占高 ≥ 12% 的
   设备上也会越界，横屏 1080 高时 0.88 已到 950 > 948）。
2. **窗口高度 = 屏高 − 固定边距**（等价于系数接近 1）。
3. **窗口 wrap，但内部滚动/列表上限 = 屏高 × 系数**（上限过大时内容仍会把窗口顶出安全区）。

三类的表现一致：窗口被窗口管理器放进系统栏安全区并居中，超出部分落在安全区外，最后一行（通常是
40dp 的操作按钮）被导航栏/手势条盖住。电视（insets 恒为 0）与沉浸式播放页不受影响。

## 方案：单一实现 + 逐弹窗替换尺寸基准

新增 `app/src/main/java/com/fongmi/android/tv/ui/dialog/DialogSafeArea.java`（包级可见）：

- `of(WindowInsetsCompat)`：`systemBars() | displayCutout()`，`bottom` 再与 `systemGestures().bottom` 取
  max（部分 OEM 手势导航把 `systemBars().bottom` 报小/报 0；左右手势区不参与，避免三键导航手机弹窗被
  无谓缩窄）。
- `area(Activity, Window)`：弹窗窗口自身 insets（`follow` 缓存）→ 宿主根窗口 insets →「显示尺寸 − 宿主
  content 视图尺寸」三级后备；**全 0 视为未知**（API 28 浮动窗口会报 0）。数学仍用
  `ThemeDialogLayout.safeArea/width/height`，不另写一套。
- `width/height(Activity, Window)`、`width/height(Context)`（非 Activity 上下文退化为屏宽/屏高）。
- `follow(Activity, Window, Runnable)`：insets 变化时回调重算（只在实际变化时触发，不会与
  `window.setLayout` 形成布局循环），覆盖「首帧 insets 未就绪」与旋转/系统栏显隐。

各弹窗只把**尺寸基准**从 `ResUtil.getScreen*` 换成 `DialogSafeArea.*`，系数、dp 上限、方位、焦点逻辑
全部保持原样；insets 为 0 时数值与改动前逐位相同，电视几何不变。

## 改动清单

main（26）：

| 文件 | 原写法 → 现写法 |
| --- | --- |
| `SiteHealthReportDialog` | 自带 insets 梯度 → `DialogSafeArea.area` + `follow`（行为不变，去重） |
| `SiteHealthDialog` | 宽度 0.44/0.92 → 安全宽 |
| `CacheManagementDialog` | 宽高 × 0.9 → 安全尺寸 + `follow` |
| `GitCloudDialog` | 高 × 0.86 → 安全高 |
| `LoginStatePathDialog` | 宽 0.82/0.96、高 0.96/0.86、输入区高 0.52 → 安全尺寸 |
| `MpvConfigDialog` / `MpvConfigEditorDialog` / `MpvCustomButtonDialog`(2 处) | 高 0.82–0.92 → 安全高 |
| `MultiThreadProxyDialog` / `PlayerButtonConfigDialog` / `ShellProxyDialog` | 高 0.82–0.98 → 安全高 |
| `ViewingRecordSyncDialog` / `PlaybackRemoteSyncDialog` / `PlaybackWebhookDialog` | 高 0.82/0.94/0.96 → 安全高 |
| `WebHomeExtensionDialog` / `WebHomeExtensionDebugDialog` | 高 0.94/0.90 → 安全高 |
| `GroupRuleDialog` | AI 预览窗口高按屏高推算 → 安全尺寸 |
| `AiRecommendationInfoDialog` | 自适应高按屏高推算 → 安全高 |
| `UpdateSettingsDialog` | 滚动上限 `0.90×屏高 − 150dp` → 安全高 |
| `LoginStateLearnDialog` / `ManagePageDialog` | 滚动/列表高 0.34 / factor → 安全高 |
| `CodecCapabilityDialog` | 报告区高 ≤ 0.58×屏高 → 安全高 |
| `PanNetworkDiagnosticDialog` | 内容高 5/8×屏高（TV 为屏高−300dp）→ 安全高 |
| `PlaybackPerformanceDialog`(2 处) / `PlaybackPerformanceModal` | 滚动高 2/3、3/5×屏高 → 安全高 |
| `RemoteTrustDialog` | 配置区高 0.34/0.44×屏高 → 安全高 |

flavor（2）：`mobile/AdBlockStatsDialog`、`leanback/AdBlockStatsDialog` —— 由「屏宽/屏高 − 16dp(24dp)」
改为「安全宽/安全高 − 同上边距」。

## 明确不改及原因

- `AboutDialog`：已内建「content 视图高度优先 + 系统栏扣减」回退，本就不是原始显示尺寸。
- `LightDialog`（leanback Alert 尺寸）与 `ThemeDialog`（主题编辑器）：前者仅在 leanback 路径生效且已被
  Material 的 24dp 垂直 inset 包住；后者已在 `SITE-HEALTH-UI-01` 之前独立修好，并由
  `ThemeDialogLayoutTest` 的源码契约锁定，统一改造收益低、回归面大。
- 所有 `BottomSheetBehavior` 面板（`ControlDialog`、`Live*Dialog`、`QuickSearchDialog`、`LutPanelDialog`、
  `VideoContentDialog`、`DanmakuSettingDialog`、`OneKeySyncDialog` 等）：尺寸作用在 sheet 而非浮动窗口，
  且 Material BottomSheet 自行处理 insets 与拖拽状态。
- 播放器沉浸态浮层（`PlayerOsdDialog` 等）：宿主隐藏系统栏（insets = 0），数值不变。
- 仅用屏宽定宽、高度 wrap 的弹窗（`AdRule*`、`ConfigDialog`、`HistoryDialog`、`SiteDialog`、`UpdateDialog`
  等）：导航栏在底部，不产生底部裁切；横向挖孔风险留待需要时统一处理。
- 竖屏比例弹窗（leanback 侧为主，`WebDialog` 0.8 等）：电视无系统栏，数值不变。

## 验证

- 定向单测（两端）：新增 `DialogSafeAreaTest` 2/2（helper 契约 + 28 个弹窗源码均走共享实现且不再用
  `params.height = (int) (ResUtil.getScreenHeight(`）；`AdBlockStatsDialogLayoutTest` 13/13；
  `AiRecommendationInfoDialogLayoutTest` 2/2；`SiteHealthReportDialogSourceTest` 3/3（双端）；
  `ThemeDialogLayoutTest` 11/11；`SiteHealthReportSourceTest` 更新为新契约后通过。
- 双 flavor Java 编译：`BUILD SUCCESSFUL in 4m 25s`。
- 双 flavor 全量单测：mobile **5284 项 / 6 失败 / 0 error / 3 skipped**，leanback **4494 项 / 8 失败 /
  0 error / 3 skipped**。失败全部为**本任务之前就存在**、且断言文件不在本次改动内：
  `MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`、`ReaderPlaybackRoutingSourceTest`(2)、
  `TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`（双端共同），
  `NativeEnhancedPlaybackStyleFocusTest`、`SearchResultDownFocusTest`（仅 leanback）。
  差分证据：改造前同一套全量单测为 5284 项 / 7 失败，多出的唯一失败是本任务需要更新的
  `SiteHealthReportSourceTest.healthReportUsesAdStatsStyleNearFullScreenSizing`，更新后消失。
- 设备复核（`emulator-5554`，API 28，1080x1920 竖屏 + `overscan 0,0,0,90`，`mStable=[0,42][1080,1830]`）：
  重构后的健康报表弹窗窗口 frame 仍为 `[28,70][1052,1802]`（1024×1732，四边 16dp，距导航栏 28px），
  与 `SITE-HEALTH-UI-01` 修复后的实测逐像素一致；「取消」完整可见可点。说明共享 helper 的取值与原先
  内联实现等价。
- 设备状态已还原：`wm size reset`；`wm overscan 0,0,0,90`（基线，`mStable` 回到 `[0,42][1920,990]`）；
  `accelerometer_rotation=1`、`user_rotation=0`。
- 未覆盖：其余 20 余个弹窗未逐个上机点开（数量与时间不允许），以「共享实现 + insets=0 时数值不变 +
  源码契约测试」作为验收；如需真机抽查，优先 `CacheManagementDialog`、`ShellProxyDialog`、
  `LoginStatePathDialog`、`PlaybackWebhookDialog`（系数 0.96–0.98）。

## 风险与回滚

- 风险：`area()` 的三级后备都读不到 inset 时（弹窗窗口在 API 28 恒报 0；宿主根窗口也报 0 且 content
  视图尚未布局）会退化为整块显示，即**回到改动前的旧尺寸**（不引入新的裁切，但也不会事后自动收敛）。
  `follow`（insets 变化时重算）只在 `SiteHealthReportDialog` 与 `CacheManagementDialog` 上安装：其余
  弹窗在 `onStart` 时宿主已 resume、根窗口 insets 通常可同步读到，故首帧就是安全区；若某宿主三者同时为 0，
  窗口保持旧尺寸而不是事后抖动重排 —— 这是本轮有意的取舍（给全仓 28 个弹窗装 listener 会放大布局回环
  面与生命周期风险）。`Area` 本身是无状态计算，`OBSERVED` 只为 `follow` 提供上次结果，缓存值等于本次
  上报值时不会触发重排。
- 风险：系数整体作用于更小的基准，弹窗在带系统栏手机上会比旧版**略小**（差额 = 状态栏 + 导航栏），
  这是把窗口放进安全区的必然结果，与主题编辑器既有修复一致。
- 回滚：回退本单元提交即可；无 schema/持久化/资源/字符串/依赖变化。

## Recovery anchor

- 目标/验收：所有以屏宽/屏高为基准的浮动弹窗窗口与内部滚动上限改用系统栏安全区；底部按钮不再被
  导航栏裁切；电视几何不变。
- 计划状态：实现、契约测试、双 flavor 编译、双 flavor 全量单测、设备复核均完成；剩余提交与 tag。
- 范围：`app/src/main/java/.../ui/dialog/**`、`app/src/{mobile,leanback}/.../ui/dialog/**`、
  `app/src/test/java/.../ui/dialog/**`、`app/src/test/java/.../setting/SiteHealthReportSourceTest.java`、
  `app/src/testMobile/java/.../ui/dialog/**`、本文件。
- 已验证：定向单测全绿；全量单测差分为「仅本任务失败项被修复，其余失败为既有」；设备 frame 与修复前一致。
- 回滚锚点：`1bb216efec156709ca3d1b027701c6331749d986`。
- 下一步唯一动作：`bash .codex/scripts/task_guard.sh finish --verified ... --commit-message ...`。
