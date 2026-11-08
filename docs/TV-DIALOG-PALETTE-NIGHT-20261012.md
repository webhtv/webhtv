# TV-DIALOG-PALETTE-NIGHT-20261012：TV 对话框面板与前景取自不同色表，「新增点播」文字看不清

## Recovery anchor

- 目标：TV 版「新增点播」对话框（以及所有走 `LightDialog` 面板的对话框）的**面板与同屏前景取自同一张色表**，使正文、输入框提示、按钮文字在系统日间模式（TV 产品默认强制深色）下均 ≥ 4.5:1（WCAG 2.2 §1.4.3）。
- 允许路径：`app/src/main/java/com/fongmi/android/tv/theme/ThemeController.java`、`app/src/test/java/com/fongmi/android/tv/theme/ThemeControllerContractTest.java`、`app/src/test/java/com/fongmi/android/tv/theme/ThemeBinderContractTest.java`、`app/src/testLeanback/java/com/fongmi/android/tv/theme/TvDialogPaletteModeTest.java`（新增）、本文件。
- 保护面：任务开始时工作区干净（预先存在脏路径 0 个）；分支 `dev4`，HEAD `af9a18fa470`。
- 验收：① 面板取表 == Activity 实际解析出的表（新增测试钉住）；② 同一台模拟器上前后对照，正文由 **1.06:1** 提升到 **10.48:1**，提示 1.39→**7.93**，描边按钮文字 1.41→**7.86**，主按钮 7.5:1 不变；③ `com.fongmi.android.tv.theme.*` 全族 JVM 测试通过；④ 修复后日间模式下的对话框渲染与夜间模式渲染**逐字节相同**；⑤ 用户电视（`192.168.50.3:5557`，青色自定义主题）同机前后对照：正文 1.05→**11.11**、提示 1.31→**6.55**、描边按钮 1.40→**8.47**、主按钮 **7.74**。
- 回滚：撤销本任务原子提交即可回到「面板取自应用上下文/系统夜间位」的旧行为。
- 下一步唯一动作：无（已交付）。

## 1. 现象与根因

用户现场照片（TV 版设置 → 点播 → 新增）里，「新增点播」面板是浅色，而面板上的说明文字、三个描边按钮文字几乎看不见；标题「新增点播」却是清楚的深色。像素级复现（模拟器，1920×1080，系统日间模式，主题设置全默认）得到同样的取值：

| 元素 | 实测色 | 来自哪张表 |
|---|---|---|
| 对话框面板 | `#E7E8EF` | **日间表** `webhtv_color_surface_container_high` |
| 标题「新增点播」 | `#1A1C1E` | **日间表** `webhtv_color_on_surface` |
| 说明文字（info） | `#E2E2E9` | **夜间表** `webhtv_color_on_surface` |
| 输入框提示 | `#C4C6D0` | **夜间表** `webhtv_color_on_surface_variant` |
| 「确定」填充/文字 | `#A8C7FA` / `#062E6F` | **夜间表** `webhtv_color_primary` / `on_primary` |
| 「选择/文件夹/取消」文字、描边 | `#A8C7FA` / `#8E9099` | **夜间表** `webhtv_color_primary` / `outline` |

同一块面板上，底色来自日间表、前景全部来自夜间表，于是出现「浅底浅字」。

**取色来源分裂**（这是根因，不是配色偏好）：

1. 面板与标题由 `LightDialog.shell()` / `LightDialog.root()` 从 `ThemeController.current()` 画出（`ThemeController.java`）。
2. 其余前景来自布局 XML 的 `?attr/color*` 与 `@color/webhtv_color_*` 选择器，由 **Activity** 的资源解析（`app/src/leanback/res/layout/dialog_config.xml`）。
3. TV 侧 `ThemeController.applyNightModeToApp()` 按产品默认（`theme_mode` 未设置 = 跟随系统）对 leanback 强制 `AppCompatDelegate.MODE_NIGHT_YES`，所以 **Activity 永远解析夜间表**。
4. 但 `ThemeController.resolvedDark()` 判「深/浅」时用的是**进程级应用上下文**（`compiledDarkPalette()` 里的 `App.get()`）与**原始系统夜间位**（`Resources.getSystem()`）。`setDefaultNightMode` 改写的是它代理的 Activity 的资源，API < 31 **不会**改写 Application 自己的资源；电视盒子的系统夜间位通常也是「日间」。两个来源都判成浅色 → `current()` = 日间表。

对照实验（同一台模拟器 `emulator-5554`，只改系统夜间位）：系统日间时面板 `#E7E8EF`，`cmd uimode night yes` 后同一界面面板变 `#2A2F34`（夜间表）。即面板确实跟着 `resolvedDark()` 走，而前景始终跟着 Activity 的夜间表走。

## 2. 证据来源

访问日期：2026-10-12（China Standard Time）。

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| 用户现场照片 | 本会话附图（1817×1048 PNG） | A | 面板浅、前景浅；标题清楚 → 面板与标题同源、其余前景另源 |
| 本仓库 `ThemeController.java` | HEAD `af9a18fa470` | A（本地） | `shell()`/`root()` 取 `current()`；`resolvedDark()` 用 `App.get()` + `Resources.getSystem()`；`applyNightModeToApp()` 对 leanback 强制深色 |
| 本仓库 `LightDialog.java` / `dialog_config.xml` / `webhtv_tokens.xml`（`values` 与 `values-night`） | HEAD `af9a18fa470` | A（本地） | 面板 = `colorSurfaceContainerHigh`；前景 = `?attr/colorOnSurface(Variant)`、`dialog_outlined_button_text` 等；两张表的十六进制值可逐项对上实测像素 |
| 设备实测 `emulator-5554` | 1920×1080，系统日间，`theme_mode` 未设置 | A | 修复前复现；`cmd uimode night yes` 后面板转深 → 面板跟随 `resolvedDark()` |
| 设备实测 `emulator-5560` | 1920×1080，系统日间，`theme_mode=-1`、`theme_color=-1` | A | **同机前后对照**：换装修复前/后 APK，其余不变，见 §6.2 |
| 用户电视 `192.168.50.3:5557` | arm64-v8a，Android 9（API 28），1920×1080，系统日间，`theme_color=-16,732,991`（青色自定义主题），无 `theme_mode` | A | **同机前后对照**（装 leanback 包，其余不变）：复现用户现场现象并验证修复，见 §6.4 |
| 本仓库既有契约测试 `ThemeBinderContractTest` / `ThemeControllerContractTest` | HEAD `af9a18fa470` | A（本地） | 已把「基线必须描述编译表」钉成源码断言；本次沿用同一风格把「探针必须是 inflate 的 context」也钉住 |
| WCAG 2.2 §1.4.3 | `https://www.w3.org/TR/WCAG22/` | A | 正文阈值 4.5:1，作为验收数字 |
| Robolectric 行为 | `TvDialogPaletteModeTest` 实跑 | A（本地） | Robolectric 会把 `setDefaultNightMode` 也应用到应用上下文，真机 API 28 不会 → 纯 JVM 测试复现不出设备现象，故补一条显式 context 的确定性断言，端到端以设备实测为准 |

不适用类别记录：本改动只涉及运行时取色来源，不涉及解码/渲染/ABI/打包，无需上游播放器依赖类证据。

## 3. 方案比较与采用

1. **不变更**：面板继续取自 `current()`，前景取自 Activity。拒绝，正是用户报告的不可读现象。
2. **只把 `LightDialog.shell()` 改成解析 `?attr/colorSurfaceContainerHigh`**：面板会立刻正确，但标题仍取 `current().colorOnSurface()`（日间深色）压到夜间面板上同样不可读，得连标题一起改；更要紧的是 `ThemeController.bindWindowBackground()` 的改写通道按基线精确匹配，面板换成 Activity 表后与错误基线不再匹配，用户自定义主题将**彻底**无法改面板色。拒绝。
3. **把 `current()` 改成恒等于 Activity 表（采用）**：面板、标题、基线、前景一次对齐。`resolvedDark()` 的既有注释本来就要求「基线必须描述 inflation 实际产生的颜色」，只是探针取错了上下文；本次把探针换成真正 inflate 的那个 context，属于把已确立的设计修回自洽，不新增概念。
4. **顺手删掉 `compiledDarkPalette()` 探针、只用模式/系统位**：会丢掉「某 flavor 把 `values/` 钉死成一张表」时的识别能力（该探针存在的唯一理由），属于扩大范围。拒绝。

取舍：采用 3 后，日间模式的电视会从「浅面板 + 夜间前景」变成**全夜间表**——这正是 TV 产品默认深色的既定设计（`applyNightModeToApp()` 注释与 `ThemeControllerContractTest` 均已记录），也与同屏其它对话框（`BaseAlertDialog` 家族的面板取自 `ThemeOverlay_WebHTV_Dialog`，本来就是夜间表）一致。移动端「跟随系统」「显式浅色」「显式深色」三条路径的表选择与改动前逐项相同（见 §5）。

## 4. 实施

1. `ThemeController.java`：
   - `resolvedDark()` / `compiledDarkPalette()` 增加 `Context` 入参：有 Activity 时用 `isNight(context)` 与 `ContextCompat.getColor(context, …)` 探测（即真正 inflate 的那个 context），无 Activity 时才退回 `App.get()` + 系统夜间位（`resolveFromPreferences()` 与设备测试探针走这条）。
   - `resolveWith()` / `frozenPalette()` 透传该 context；`applyFromPreferences(activity)` 传入 `activity`，使 `baseline` 与 `current` 同时描述该 Activity 实际解析的表。
   - 两个方法由 `private` 放宽为包内可见，供同包测试直接断言（与既有的 `darkPaletteFor()` 同一先例）。
2. `ThemeControllerContractTest` / `ThemeBinderContractTest`：把钉住方法形状的源码断言更新到新签名，并新增「探针必须来自 inflate 的 context、不得再以应用上下文为首选来源」的断言。
3. 新增 `app/src/testLeanback/java/com/fongmi/android/tv/theme/TvDialogPaletteModeTest.java`：
   - `dialogShellMatchesTheTableTheActivityInflated`：面板必须等于 Activity 解析出的 `webhtv_color_surface_container_high`；
   - `addVodDialogForegroundsMeetWcagAaOnTheShell`：info / 提示 / 描边按钮文字对面板 ≥ 4.5:1；
   - `paletteDecisionFollowsTheInflatingContext`：用显式构造的日/夜 context 钉住「探针跟着入参 context」（补偿 Robolectric 复现不出设备差异）。

## 5. 不变量与回滚

- 不改动 `LightDialog`、`dialog_config.xml`、`webhtv_tokens.xml`、`dialog_*_button_*.xml` 与任何 `BaseAlertDialog` 家族对话框：它们的渲染路径与取值不变。
- 不改动 `ThemeResolver` / `ThemeProfile` / `ThemeBinder` 的算法与契约；`darkPaletteFor()` 语义不变，其单元测试原样通过。
- 模式选择语义不变：`theme_mode` = 浅色 → Activity 日间 → 浅色表；= 深色 → 夜间表；未设置 → mobile 跟随系统、leanback 深色。改动只是让「取表」与「Activity 实际解析的表」不再可能分叉。
- 无 Activity 的入口（`resolveFromPreferences()`、设备测试探针）行为不变。
- 回滚：撤销本任务原子提交。

## 6. 验证记录

### 6.1 定向 JVM 测试

`.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.theme.*'` → `BUILD SUCCESSFUL in 4m 44s`，24 个测试类全绿。

新增 `TvDialogPaletteModeTest`：3 项，0 失败（`tests=3 failures=0 errors=0`）。

### 6.2 设备实测（`emulator-5560`，1920×1080，系统日间模式，`theme_mode=-1`、`theme_color=-1`）

同一台模拟器、同一路径（首页 → 设置 → 点播 → 新增），只换安装包（修复前 APK 与修复后 APK 同签名、同 versionCode，`adb install -r` 覆盖安装）。区域取样取该区域内出现次数最多的非底色像素。

| 元素 | 修复前 | 修复后 |
|---|---|---|
| 面板 | `#E7E8EF`（日表） | `#2A2F34`（夜表） |
| 标题 | `#1A1C1E`（日表） | `#E2E2E9`（夜表） |
| info 文字 | `#E2E2E9` | `#E2E2E9` |
| 输入框提示 | `#C4C6D0` | `#C4C6D0` |
| 「确定」填充/文字 | `#A8C7FA` / `#062E6F` | 同左 |
| 「取消/选择」文字、描边 | `#A8C7FA` / `#8E9099` | 同左 |

WCAG 对比度（按实测十六进制值计算）：

| 前景 / 面板 | 修复前 | 修复后 |
|---|---:|---:|
| info `#E2E2E9` on 面板 | **1.06:1** | **10.48:1** |
| 提示 `#C4C6D0` on 面板 | **1.39:1** | **7.93:1** |
| 描边按钮文字 `#A8C7FA` on 面板 | **1.41:1** | **7.86:1** |
| 「确定」`#062E6F` on `#A8C7FA` | 7.50:1 | 7.50:1 |

旁证：修复后日间模式下的对话框截图与 `emulator-5554` 在系统夜间模式下的同界面截图 **SHA-256 相同**（`609E390E5F7A738B1D4A94E80FA4422DFE7BEB64C0787E9D083D7FF78F35985F`）——即修复把日间模式的电视渲染成了本来就自洽的那套夜间配色。

### 6.3 结论

用户报告的「TV 版新增点播界面对比度不够看不清文字」在模拟器与用户电视上均已消除：最差一项由 1.05:1 提升到 6.55:1（模拟器口径最差 7.50:1），全部 ≥ 4.5:1。

### 6.4 用户电视实测（`192.168.50.3:5557`，青色自定义主题，系统日间）

**环境前置发现**：该电视此前安装的是 **mobile flavor**（`dumpsys package` 只有 `android.intent.category.LAUNCHER`，无 `LEANBACK_LAUNCHER`；弹窗 id 为 `title/input/url`，即 `app/src/mobile/res/layout/dialog_config.xml`）。mobile 的默认夜间模式是「跟随系统」，系统日间 → Activity 也是日间 → 该弹窗浅底深字、本来就自洽，因此**看不到用户报告的现象**。用户截图里的带二维码弹窗（`code`/`info`/`choose`/`chooseDir` 四个按钮）来自 leanback 版，所以本次把 leanback 包装上去做对照（同包名同签名，`adb install -r` 覆盖安装，未卸载、未清数据）。

同一台电视、同一路径（首页 → 设置 → 点播 → 新增），只换装修复前/后的 leanback 包：

| 元素 | 修复前 | 修复后 |
|---|---|---|
| 面板 | `#E3E9EA`（青色种子**浅色**表） | `#252B2C`（青色种子**深色**表） |
| 标题 | `#171D1E` | `#DEE3E5` |
| info 文字 | `#E2E2E9`（编译夜间表） | `#DEE3E5` |
| 输入框提示 | `#C9CCD4` | `#ACAFB7` |
| 「确定」填充/文字 | `#A8C7FA` / — | `#82D3E2` / `#00363E` |
| 「选择/文件夹/取消」文字、描边 | `#A8C7FA` / `#8E9099` | `#82D3E2` / `#899294` |

WCAG 对比度（按实测十六进制值计算）：

| 前景 / 面板 | 修复前 | 修复后 |
|---|---:|---:|
| info `#E2E2E9` / `#DEE3E5` on 面板 | **1.05:1** | **11.11:1** |
| 提示 `#C9CCD4` / `#ACAFB7` on 面板 | **1.31:1** | **6.55:1** |
| 描边按钮文字 `#A8C7FA` / `#82D3E2` on 面板 | **1.40:1** | **8.47:1** |
| 「确定」文字 on 其填充 | — | **7.74:1** |

修复前后 `#E3E9EA` / `#252B2C` 的浅深翻转同时说明：**用户的自定义青色主题此前只在面板上「走了一半」**（面板取了种子派生表，前景却取编译夜间表），修复后两者同源，青色主题在弹窗里完整生效。其余界面复核无回归：首页（`tv_home_after`）、设置页与另一处 `LightDialog` 家族弹窗（推送剪贴板二维码）在修复后均正常可读；安装后进程存活、无崩溃。

> 附带说明：修复同时让 `baseline` 与实际 inflate 的颜色对齐，因此**用户自定义主题在 TV 上的改写通道由「静默 no-op」恢复为生效**（`resolvedDark()` 注释里记录过这个 no-op）。这是主题功能本来的设计行为，但会表现为「电视整体配色比之前更贴自定义色」，属预期变化而非回归。


## 7. 相邻但未修复（超出本次范围，仅报告）

1. `ThemeController.refresh()` 仍直接用 `Resources.getSystem()` 的夜间位取表，且当前仓库内无调用方。若将来把它接进 UI 路径，会重新造出「无 Activity 上下文时按系统位取表」的同类分叉，届时需要显式传入 context。
2. `ThemeBinder.bindDrawable()` 不解包 `StateListDrawable`（`docs/TV-HOME-MENU-CONTRAST-20261009.md` §7 已记录）：自定义主题下凡「状态列表内 `<shape>` 填充」的控件仍保持基线色，属主题系统范围，建议单独建任务。
3. 环境说明（与本次修复无关）：`emulator-5554` 上已安装包的签名与本工作区 debug keystore 不一致，`adb install -r` 会 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`；本次设备复验因此在签名匹配的 `emulator-5560` 上完成。
4. 环境说明：本会话无可用 bash（仅 WSL `bash.exe`，`LxssManager`/`vmcompute` 未运行，返回 `HCS_E_SERVICE_NOT_AVAILABLE`），`.codex/scripts/task_guard.sh` 无法执行。按其意图手工执行：仅暂存本任务声明范围内的路径、原子提交、创建本地带注释 recovery tag，未推送。
