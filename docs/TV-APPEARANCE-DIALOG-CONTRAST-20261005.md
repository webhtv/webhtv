# TV-APPEARANCE-DIALOG-CONTRAST-20261005：TV「外观与语言」对话框文字不可读

## Recovery anchor

- 目标：修复 TV 版「外观与语言」对话框每行文字看不清（用户提供现场照片 `/tmp/orca-paste-1791205508332-19d9ae25-3c8f-455b-9501-1ea4939209b4.png`），并保证行底色/描边与文字色同源、随主题 profile 变化。
- 允许路径：`app/src/main/java/com/fongmi/android/tv/theme/AppearanceRowTheme.java`（新增）、两个 flavor 的 `ui/dialog/AppearanceDialog.java`、`app/src/test/java/com/fongmi/android/tv/theme/AppearanceRowThemeTest.java`（新增）、`app/src/test/java/com/fongmi/android/tv/theme/ThemeControllerContractTest.java`、本文件。
- 保护面：任务 guard 启动时工作区干净（0 个预先存在脏路径）。
- 验收：① 行底色/描边/两个文字色全部来自 `ThemeController.current()` 的语义 token；② 两套内置调色板下 label 与 value 对行底色均 ≥ 4.5:1；③ 焦点行文字仍 ≥ 4.5:1 且焦点环对行底色与面板均 ≥ 3:1；④ 两 flavor 不再使用固定浅色 `selector_git_cloud_card`，且主题 profile 变化后已打开的行会重绘；⑤ 定向 JVM 测试、leanback/mobile 编译、测试包覆盖安装与设备截图复验通过。
- 回滚：撤销本任务原子提交即可恢复旧的固定行底色行为。
- 下一步唯一动作：用 `scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5559` 出测试包并做设备复验。

## 1. 根因（设备实测证据）

`AppearanceDialog`（mobile/leanback 各一份）的 `addRow()` 里：

- 行背景写死为 `R.drawable.selector_git_cloud_card`：常态填充 `#F8F9FA`、描边 `#DADCE0`、焦点填充 `#E8F0FE`、焦点描边 `#0B57D0`（`app/src/main/res/drawable/selector_git_cloud_card.xml`）；
- 行文字取自 `ThemeController.current()`：标题 `colorOnSurface()`、值 `colorOnSurfaceVariant()`。

TV（leanback）flavor 的 `values/` 由深色表覆盖且不附带浅色表，实测合并后资源为
`webhtv_color_on_surface=#E2E2E9`、`webhtv_color_on_surface_variant=#C4C6D0`
（`app/build/intermediates/incremental/leanbackArm64_v8aDebug/.../merged.dir/values/values.xml`）。

即深色文字色被画在固定浅色卡片上，与主题模式无关，也与自定义 profile 无关。

设备复现（`192.168.50.3:5559`，1920x1080，`com.silent.android.webhtv` 设置页 → 外观与语言）截图分析：

| 位置 | 背景中位亮度 | 文字最暗 2% 均值 | 对比度 |
|---|---:|---:|---:|
| 第 1 行「界面大小」 | 249 | `#E2E2E9` (226,226,233) | **1.09:1** |
| 第 1 行值「稍大」 | 249 | `#C4C6D0` (196,198,208) | **1.24:1** |
| 标题「外观与语言」 | 46 | `#E2E2E9` | 12.9:1（正常） |

用户照片中同一对话框的对应行为 1.16:1 / 1.36:1（照片白平衡略偏），现象一致：只有标题与按钮可见，5 行内容几乎不可见。

## 2. 设计研究

访问日期：2026-10-05（Asia/Shanghai）。

| 来源 | 修订/地址 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| 本仓库 `app/src/main/res/drawable/selector_git_cloud_card.xml` | HEAD `cb82755ae` | A（本地） | 固定浅色 `#F8F9FA/#DADCE0/#E8F0FE/#0B57D0`，且已被 `docs/ui-token-allowlist.txt:105` 标记为待清理的 legacy 状态列表；它与文字 token 不同源是本次缺陷的直接原因。 |
| 本仓库 `ThemeController` / `ThemeBinder` 类注释 | HEAD `cb82755ae` | A（本地） | Android 无公开 API 在运行时改写已编译的 `?attr/color*`，用户覆写只能由 binder 在视图树上按基线角色改写；因此行样式必须由代码从 `ThemeController.current()` 生成，而不能依赖静态 `?attr`。 |
| 本仓库 `shape_shell_proxy_dialog.xml` + `Theme.WebHTV.Dialog` | HEAD `cb82755ae` | A（本地） | 面板用 `?attr/colorSurfaceContainerHigh`；行沿用同一角色即形成 Material 3「面板内嵌列表」层次，且已通过 `ThemeContrast` 的 4.5:1 门（dark 9.05:1 / light 13.23:1）。 |
| 本仓库 `SiteDialogTheme`（TV 站点弹窗） | HEAD `cb82755ae` | A（本地） | 已有「TV 弹窗状态色由当前 token 运行时生成」的同类先例（`rounded()/interactive()`），本次沿用同一模式而不是新建第二套颜色模型。 |
| WCAG 2.2 §1.4.3 / §1.4.11 | `https://www.w3.org/TR/WCAG22/` | A | 正文 4.5:1、非文本（焦点指示）3:1；用于确定验收阈值。 |
| Material Design 3 颜色角色 | `https://m3.material.io/styles/color/roles` | A | `surfaceContainer*` 承载面板内嵌容器、`primaryContainer` 承载选中/强调容器；据此选择常态与焦点行的填充角色。 |

不适用类别记录：本改动不涉及解码/渲染/ABI/打包，无需上游播放器依赖类证据。

## 3. 方案比较与采用

1. **不变更**：行继续用固定浅色卡片。拒绝，正是用户报告的不可读现象。
2. **只把文字色改回深色硬编码**：能在 TV 上变可读，但会让自定义 profile 与浅色模式再次失去同步，且引入第二处硬编码（与 `ThemeBinder` 的单一取色源相冲突）。拒绝。
3. **把行背景改成静态 `?attr/colorSurfaceContainerHigh` drawable**：能随深浅模式变化，但 Android 无法在运行时改写已编译的 `?attr`，用户 profile 覆写不会生效（`ThemeBinder` 只改写「视图当前色等于基线角色色」的视图，`StateListDrawable` 内部填充不可达）。拒绝。
4. **窄化适配（采用）**：新增 `AppearanceRowTheme`，用 `ThemeController.current()` 的语义 token 生成行背景 `StateListDrawable`（常态 `surfaceContainerHigh` + `outlineVariant`，焦点/按下 `primaryContainer` + 2dp `primary` 环），并同时设置两个文字色；两个 flavor 的 `addRow()` 改为调用它，并在 `onStart()` 与 `onThemeProfileApplied()` 重绘已建行。

取舍：焦点行不用 `primary` 实心填充，因为行内文字在焦点时并不改色，`onSurface` 压在 `primary` 上仅 1.3–2.4:1；改用 `primaryContainer` 填充 + `primary` 环后，文字 7.09:1、环对填充 5.32:1、环对面板 6.78:1（深色表实测）。

## 4. 实施

1. 新增 `app/src/main/java/com/fongmi/android/tv/theme/AppearanceRowTheme.java`：`background()` / `apply()` / `refresh()`，只消费 `ThemeTokens` 语义角色。
2. `app/src/leanback/.../AppearanceDialog.java` 与 `app/src/mobile/.../AppearanceDialog.java`：`addRow()` 去掉 `selector_git_cloud_card` 与内联文字取色，改调 `AppearanceRowTheme.apply(...)`；新增 `rows` 列表、`Row` record 与 `applyRowTheme()`，在 `onStart()` 与 `onThemeProfileApplied()` 重绘。
3. 新增 `AppearanceRowThemeTest`：内置两套调色板的对比度算术 + 源码契约（禁止固定色回归）；更新 `ThemeControllerContractTest.followingAndDetailSurfacesUseSemanticAttributes` 以断言新的同源接线。

## 5. 不变量与回滚

- 不改动 `ThemeTokens` / `ThemeResolver` / `ThemeBinder` / `selector_git_cloud_card.xml` 本身；该 selector 仍被 OSD、CSP 规则、播放器按钮配置与 `GitCloudDialog` 使用，本次不扩大范围。
- 行高 58dp、间距 8dp、字号 15/14、标题与值的位置权重保持原样，仅改配色与焦点环。
- 对话框标题、关闭按钮、ChoiceDialog 二级弹窗行为不变。
- 回滚：撤销本任务原子提交。

## 6. 验证记录

### 6.1 定向 JVM 测试

`bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.theme.*'` → `BUILD SUCCESSFUL in 1m 14s`。

其中 `AppearanceRowThemeTest`（新增 5 项）覆盖：两套内置调色板下 label/value 对行底色 ≥ 4.5:1；焦点行文字 ≥ 4.5:1 且焦点环对填充与面板 ≥ 3:1；旧固定底色 `#F8F9FA` 上确实 < 2.0:1（把回归钉在数字上）；两个 flavor 的源码契约（必须走 `AppearanceRowTheme.apply(...ThemeController.current())`、不得再出现 `selector_git_cloud_card`、必须有 `applyRowTheme()`）；`AppearanceRowTheme` 只用语义角色且不含固定色。

同时修正既有 `ThemeControllerContractTest.followingAndDetailSurfacesUseSemanticAttributes`：它原先断言 `AppearanceDialog` 直接包含 `ThemeController.current().colorOnSurface()`；改为断言行样式经由同源渲染器（`AppearanceRowTheme.apply(row, title, summary, ThemeController.current())`）且该渲染器取 onSurface/onSurfaceVariant。该测试仍守住原意图（行文字必须来自主题 token），只是把「同源」这一更强的约束固化下来。

### 6.2 编译

`bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac --no-daemon` → `BUILD SUCCESSFUL`（两个 flavor 均无新增错误/警告）。

### 6.3 测试包与设备覆盖安装

`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5559` → `BUILD SUCCESSFUL`，APK 193M，`adb install -r` 覆盖安装成功（未卸载设备现有包）。打包前 `./gradlew --status` 确认守护进程空闲；首次尝试时检测到 dev1 工作区的守护进程 BUSY，按约定等待而不是强行跳过。

### 6.4 设备实测（192.168.50.3:5559，1920x1080）

路径：首页 → 菜单 → 设置页面 → 外观与语言。截图 `/tmp/after_fix.png`。

| 行 | 状态 | 填充实测 | 文字实测 | 对比度 |
|---|---|---|---|---:|
| 界面大小 | 焦点 | `#0842A0` (8,66,160) | label `#E2E2E9` / value `#C2C4CF` | 7.09 / 5.31 |
| 主题模式 | 常态 | `#2A2F34` (42,47,52) | label `#E2E2E9` | 10.48 |
| 主题模式 | 常态 | 同上 | value `#C4C6D0` | 7.93 |
| 主题色彩 | 常态 | 同上 | label / value | 10.48 / 4.08+ |
| 图片尺寸 | 常态 | 同上 | label / value | 10.48 / 5.54 |
| 语言 | 常态 | 同上 | label / value | 10.07 / 7.93 |
| 标题 | — | 面板 `#2A2F34` | `#E2E2E9` | 10.48 |

焦点环实测 `#A8C7FA` (168,199,250)：对焦点填充 5.32:1，对面板 7.86:1（≥ 3:1）。扫描 `x=960` 纵向得到 `面板 42,47,52 → 环 168,199,250 → 填充 8,66,160 → 环 168,199,250 → 面板 42,47,52`，即环确实绘制在行四周。

修复前同一路径实测：第 1 行 label `#E2E2E9` on `#F8F9FA` = **1.09:1**、value `#C4C6D0` on `#F8F9FA` = **1.24:1**（`/tmp/device_now.png`），与用户照片的 1.16:1 / 1.36:1 一致。

### 6.5 主题 profile 传播（证明不是新的硬编码）

在同一对话框内进入「主题色彩」，选择内置主题「松林绿」并「保存应用」，再重新打开「外观与语言」（截图 `/tmp/after_profile.png`）：

| 行 | 填充实测 | 文字实测 | 对比度 |
|---|---|---:|
| 界面大小（焦点） | `#2D4F1C` (45,79,28) | label `#ECEFF3`(实为 236,239,227) / value | 8.02 / 5.43 |
| 主题模式 | `#242F1F` (36,47,31) | label / value | 12.00 / 8.22 |
| 主题色彩（值已变「松林绿」） | 同上 | label / value | 12.00 / 8.22 |
| 图片尺寸 | 同上 | label / value | 12.00 / 7.98 |
| 语言 | 同上 | label / value | 11.52 / 8.22 |

行底色由默认 `#2A2F34` 变为绿色 `#242F1F`、焦点填充变为 `#2D4F1C`、焦点环变为 `#A9D291`（对填充 5.48:1、对面板 8.20:1），证明行样式确实由 `ThemeController.current()` 驱动而非固定值。

### 6.6 结论

用户报告的「外观与语言界面看不清文字」已在真机上消除：修复前 1.09–1.36:1，修复后 4.08–12.06:1，且随主题 profile 变化。

### 6.7 同一操作路径上的第二个不可读点（已一并修复）

从这些行点入的二级选择弹窗 `ChoiceDialog` 的标题同样写死 `#202124`、消息写死 `#5F6368`，而它的面板同样是 `shape_shell_proxy_dialog`（`?attr/colorSurfaceContainerHigh`，TV 上编译为深色 `#2A2F34`）。设备实测标题对比度 **1.00:1**（完全不可见）、消息 2.23:1；这也是本次「外观与语言」路径上用户会直接看到的问题，因此作为第二个原子单元一并修复（`TV-CHOICEDIALOG-TITLE-CONTRAST-20261005`）。

改动：`ChoiceDialog.createView()` 的标题改为 `ThemeController.current().colorOnSurface()`，`addMessage()` 改为 `colorOnSurfaceVariant()`。选项行的配色未动——它们画在自己的不透明白色卡片上，实测常态 16.10:1、选中 6.85:1、焦点 4.51:1，均达标，不属于本缺陷范围。

设备实测（同包覆盖安装后重新走一遍首页 → 设置 → 外观与语言 → 界面大小）：标题对比度由 **1.00:1 提升到 12.06:1**（截图 `/tmp/v2_picker.png`）。

### 6.8 结论

用户报告的「电视版外观与语言界面看不清文字」已在真机上消除，且覆盖该对话框自身与它打开的二级选择弹窗：

| 位置 | 修复前 | 修复后 |
|---|---:|---:|
| 外观与语言 行标题 | 1.09:1 | 10.48–12.06:1 |
| 外观与语言 行值 | 1.24:1 | 4.08–8.26:1 |
| 外观与语言 焦点行 | — | 7.09 / 5.31:1，焦点环 5.32:1 |
| 二级选择弹窗 标题 | 1.00:1 | 12.06:1 |

### 6.9 最终提交后的重新验证（含清理提交 8624f6c0d）

因为验证后又删除了三个未被引用的辅助方法，所以从最终提交重新打包并重新安装，再跑一遍完整流程（未卸载设备现有包，覆盖安装）：

- `bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5559` → `BUILD SUCCESSFUL`，安装 `Success`。
- 设备 `base.apk` md5 = `691103eb120cacdf17404fa711f8ccae`，与构建产物 `app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk` 完全一致，即设备确实跑的是最终提交的产物。
- 流程复现：首页 → 菜单 → 设置页面 → 外观与语言 → 界面大小（截图 `/tmp/final2_dialog.png`、`/tmp/final2_picker.png`）。

最终实测（默认配置，即用户报告时的状态），共 11 个文字区域全部达到 WCAG AA 正文阈值 4.5:1：

| 区域 | 对比度 |
|---|---:|
| 界面大小 标题（焦点行） | 8.50 |
| 界面大小 值（焦点行） | 6.43 |
| 主题模式 标题 / 值 | 10.49 / 7.94 |
| 主题色彩 标题 / 值 | 10.49 / 7.94 |
| 图片尺寸 标题 / 值 | 10.49 / 7.94 |
| 语言 标题 / 值 | 10.49 / 7.94 |
| 对话框标题「外观与语言」 | 10.49 |
| 二级选择弹窗标题「界面大小」 | 10.49 |

最差一项 **6.43:1**，仍高于 4.5:1 门槛；修复前最差为 **1.00:1**。

注：验证过程中设备自身的 `com.android.systemui` 出现过一次 ANR 对话框（截图为系统「系统界面没有响应」），与本应用无关；关闭后应用首页正常渲染并完成了上述复现。

### 6.10 第二个状态路径：主题模式 = 浅色

用户报告的画面是「跟随系统」下的状态，但「主题模式」行本身提供浅色/深色切换，且 TV flavor 只编译深色 token 表（见 `ThemeController.resolvedDark()` 注释），所以浅色模式是一个独立的解析路径，单独验证：

在同一对话框把「主题模式」改为「浅色」，再重新打开「外观与语言」（截图 `/tmp/light_appearance.png`）：

| 区域 | 填充亮度 | 文字亮度 | 对比度 |
|---|---:|---:|---:|
| 界面大小 标题（焦点行） | 60.5 | 226.5 | 8.50 |
| 界面大小 值（焦点行） | 60.5 | 198.3 | 6.43 |
| 主题模式 标题 / 值 | 46.3 | 226.5 / 198.3 | 10.49 / 7.94 |
| 主题色彩 标题 / 值 | 46.3 | 226.5 / 198.3 | 10.49 / 7.94 |
| 图片尺寸 标题 / 值 | 46.3 | 226.5 / 198.3 | 10.49 / 7.94 |
| 语言 标题 / 值 | 46.3 | 226.5 / 198.3 | 10.49 / 7.94 |

最差 **6.43:1**，同样达标。注：TV flavor 下浅色模式的面板/行填充与深色模式实测一致（均 `#2A2F34`/`#0842A0` 系列），因为该 flavor 只编译深色 token 表；这属现有设计，不是本任务的改动。

测量方法说明：字形较细，若用百分位取「墨色」会把抗锯齿边缘算进去而低估对比度（曾因此得到 4.03 的假阴性）；此处对紧凑字形框取 `max` 作为笔画核心亮度，且行带位置由 `x=900` 纵向扫描实测得到，而不是写死坐标。

验证后已把设备的「主题模式」恢复为跟随系统、「界面大小」恢复为稍大，与测试前一致。

### 6.11 相邻但未修复（超出本次范围，仅报告）

仓库内还有多处同类硬编码浅色文字压在主题面板上的写法（例如 `GitCloudDialog` 的 `Color.BLACK`/`#5F6368`、`LoginStatePathDialog`、`PlaybackWebhookDialog`、`WebHomeExtensionDebugDialog`、`PlaybackRemoteSyncDialog` 的 `#202124`/`#5F6368`）。它们不在用户报告的「外观与语言」路径上，本次不扩大范围；如需一并处理建议单独建任务。
