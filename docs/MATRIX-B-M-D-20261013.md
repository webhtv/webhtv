# MATRIX-B-M-D-20261013：B-M-D 行验收（mobile / dark）

> **结论更正（2026-10-14）**：原「不通过 + 整族 BottomSheet 主题反转」判定**撤回**。
> 该判定唯一的前提——「标注为 B-M-D 的截图是在深色模式下拍的」——被证伪。
> 实测：面板与它自己的前景文字**始终来自同一张 token 表**，BottomSheet 家族**没有缺陷**。
> 详见 §0–§3。

## Recovery anchor

- 目标：执行 §20.2 矩阵的 B-M-D 行（mobile / dark，通过标准「无浅色孤岛」）。
- 性质：**验收 + 结论更正**。本任务**不改生产代码**（没有缺陷需要修）。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（94 个文件）；分支 `dev4`。
- 基线提交：`555e046bf25f3510e308b825699ae0a3d91c6baf`。
- **当前结论**：B-M-D 行 **通过**；B-M-L 行 **通过**；BottomSheet 家族 **无缺陷**。
- 下一步唯一动作：无（本行已交付更正后的判定）；矩阵其余七行按序继续。

---

## 0. 更正摘要

| 项 | 原文（`e841a75ccfc` / `555e046bf25`） | 更正后 |
|---|---|---|
| B-M-D 行判定 | 不通过 | **通过** |
| 根因 | 整族 BottomSheet 主题与当前模式**完全相反** | **无缺陷**；面板始终取当前模式对应的表 |
| 影响面 | 33 个 BottomSheet 子类 | 33 这个数字**正确**（见 §4），但**不受影响** |
| 生产代码 | 需要独立修复 | **不需要修改** |

原判定依赖的唯一前提是「标注为 B-M-D 的那批截图是在深色模式下拍的」。该前提有两个独立错误（§1），
而面板本身的测量结果恰恰证明它是**正确**的（§2）。

---

## 1. 原判定前提的两个错误

原文 §1 记录设备模式为 `mobile flavor + ui_night_mode=1（深色）`。

### 1.1 `ui_night_mode` 的取值语义写反了

实机核对（API 28）：

```
cmd uimode night no   ⇄  settings secure ui_night_mode = 1   →  MODE_NIGHT_NO   = 浅色
cmd uimode night yes  ⇄  settings secure ui_night_mode = 2   →  MODE_NIGHT_YES  = 深色
```

**`1` 是浅色，`2` 才是深色。** 原文把 `1` 记作「深色」，方向正好相反。

### 1.2 系统夜间位不是应用渲染模式的判据

应用自身的 `Setting.getThemeMode()` 经 `ThemeController.applyNightModeToApp()` 调用
`AppCompatDelegate.setDefaultNightMode` 覆写 Activity 的资源配置。因此：

- 真正的判据是 `dumpsys activity top` 里 `mCurrentConfig` 是否含裸 token `night`
  （出现 = 深色，不出现 = 浅色）；
- 系统夜间位只决定「跟随系统」时的结果，用户显式选过浅色/深色时会被覆盖。

原文 §1.1 用「首页 / 设置页看起来是深色」来佐证模式，但这两页都是 **WebView / 壁纸主导**的画面：
两轮截图的整体颜色直方图几乎逐位相同（§2.4），**不携带任何主题证据**。

---

## 2. 决定性证据：面板与它自己的前景同表

若主题真被「反转」，应当看到**面板来自 A 表、前景文字来自 B 表**。
实测恰好相反——每一张弹窗截图里，面板与全部前景角色都来自**同一张**表。

### 2.1 `B-M-D-08-dialog.png`（原文标注：深色模式，「浅色孤岛」）

| 元素 | 采样区 | 实测主色 | 对应 token |
|---|---|---|---|
| 面板 | (200,700) 200×200 | `#E7E8EF` **100.0%** | **浅色表** `surface_container_high` |
| 标题「一键同步」 | (75,305) 260×50 | `#E7E8EF` 66.6% + `#1A1C1E` **24.0%** | **浅色表** `on_surface` |
| 正文说明 | (70,460) 400×60 | `#E7E8EF` 76.3% + `#44474F` **11.5%** | **浅色表** `on_surface_variant` |
| 右上刷新图标 | (895,285) 90×70 | `#E7E8EF` 84.0% + `#44474F` **11.9%** | **浅色表** `on_surface_variant` |

四个元素**全部**是浅色表取值。这不是「浅色孤岛压在深色应用上」，
而是**整个对话框上下文都解析到了浅色表**——即该截图拍摄时应用本来就处于浅色配置。

### 2.2 `B-M-L-03-sheet.png`（原文标注：浅色模式，「深色面板」）

> 附带更正：这张**不是**「一键同步」弹窗，而是**另一个** BottomSheet（订阅源列表，含编辑/删除/新增）。
> 原文 §1.3 称「打开同一个 BottomSheet」，不准确。但两者同属该家族，证据依然有效。

| 元素 | 采样区 | 实测主色 | 对应 token |
|---|---|---|---|
| 面板 | (200,700) 200×200 | `#2A2F34` **95.4%** | **深色表** `surface_container_high` |
| 列表项文字 | (340,560) 180×60 | `#2A2F34` 86.3% + `#A8C7FA` **7.5%** | **深色表** `primary` |
| 状态栏时钟字形 | 顶部 3% 条带 | 白色像素 337 | 深色配置（白字） |

同样是**同一张表**贯穿面板与前景。

### 2.3 取到的是 WebHTV token，不是 Material 基线

对照 `material-1.14.0` 的基线值，实测值**没有一个**等于 Material 默认：

| 角色 | Material 浅色 | 实测（浅色弹窗） | Material 深色 | 实测（深色弹窗） |
|---|---|---|---|---|
| `surface_container_high` | `#ECE6F0` | **`#E7E8EF`** | `#2B2930` | **`#2A2F34`** |
| `on_surface` | `#1D1B20` | **`#1A1C1E`** | `#E6E0E9` | — |
| `on_surface_variant` | `#49454F` | **`#44474F`** | `#CAC4D0` | — |
| `primary` | `#6750A4` | — | `#D0BCFF` | **`#A8C7FA`** |

即：BottomSheet **确实**接到了 WebHTV 的 token 表（这正是设计系统想要的），
只是接的方向**跟随当前模式**，而不是相反。

### 2.4 批次内模式并不统一（数据质量记录）

用「顶部 3% 条带是否出现纯白像素」（= `windowLightStatusBar`，由 `values/` vs `values-night/`
的 `isLightTheme` 决定，与面板用的是**同一套限定符**）逐图扫描：

| 文件 | 浅色 `primary #0B57D0` | 深色 `primary #A8C7FA` | 顶部白像素 | 实际模式 |
|---|---|---|---|---|
| B-M-D-01-home | 120 | 0 | 0 | **浅色** |
| B-M-D-02-settings | 100 | 0 | 0 | **浅色** |
| B-M-D-03-dialog | 100 | 0 | 0 | **浅色** |
| B-M-D-03-enhance | 100 | 0 | 0 | **浅色** |
| B-M-D-04-lab | 0 | 0 | 362 | **深色** |
| B-M-D-05-lab-dialog | 0 | 0 | 362 | **深色** |
| B-M-D-06-addcmd | 0 | 0 | 364 | **深色** |
| B-M-D-07-exitdialog | 120 | 0 | 0 | **浅色** |
| B-M-D-08-dialog | 0 | 0 | 0（弹窗隐藏状态栏） | **浅色**（由 §2.1 前景定） |
| B-M-L-01-home | 0 | 120 | 352 | **深色** |
| B-M-L-02-settings | 0 | 100 | 356 | **深色** |
| B-M-L-03-sheet | 0 | 2286 | 337 | **深色** |

**结论：文件名与实际模式不对应。** 原文把整批 `B-M-D-*` 当作深色、整批 `B-M-L-*` 当作浅色，
两批实际上近似互换，且 `B-M-D` 那批内部还混了 3 张深色图。

---

## 3. 机制：面板必然跟随当前配置（已核实，非推测）

### 3.1 `bottomSheetDialogTheme` 全仓库从未接线

```
grep -r bottomSheetDialogTheme app/src   →  仅命中 webhtv_styles.xml:80 的一句注释
```

因此 BottomSheet 走 Material 默认值，即 `ThemeOverlay.Material3.BottomSheetDialog`。

### 3.2 Material 1.14.0 侧资源链（逐条核实自 `material-1.14.0.aar`）

```
ThemeOverlay.Material3.BottomSheetDialog              （空壳）
└─ Base.ThemeOverlay.Material3.BottomSheetDialog      （空壳）
   └─ Base.V21.ThemeOverlay.Material3.BottomSheetDialog   （只加 android:statusBarColor=transparent）
      └─ Base.V14.ThemeOverlay.Material3.BottomSheetDialog（只设 windowIsFloating / windowSoftInputMode /
                                                          windowBackground / windowAnimationStyle /
                                                          bottomSheetStyle / enableEdgeToEdge）
         └─ Base.ThemeOverlay.Material3.Dialog        （只设 materialButtonStyle / backgroundDimAmount）
```

**整条 overlay 链不定义任何 M3 颜色角色。** Material 里 `colorSurfaceContainerHigh`
只由 `Theme.Material3.*` 这些**完整主题**定义，overlay 不碰它。

### 3.3 于是 `?attr/color*` 落回被复制的基础主题

`Dialog(Context, themeResId)` 内部用 `ContextThemeWrapper`，其 `initializeTheme()`
**先把基础主题整体复制进来**（`mTheme.setTo(baseContext.getTheme())`），再叠加 overlay。
overlay 不覆盖颜色角色，于是：

```
shape_one_key_sync_dialog.xml 的 ?attr/colorSurfaceContainerHigh
  → Theme.WebHTV 的映射 @color/webhtv_color_surface_container_high
  → values/ = #E7E8EF（浅色）  |  values-night/ = #2A2F34（深色）
```

而 `BottomSheetDialogFragment` 用的 `requireContext()` 就是承载它的 Activity，
与 Activity 的视图共用同一份（已被 `AppCompatDelegate` 覆写过的）资源配置。

**所以面板跟随当前模式是结构性的，不依赖任何运行时状态。** 这也解释了 §2.3 的结果。

### 3.4 原文 §2.2 的猜测不成立

原文猜测根因是 `ThemeController` 的静态 `current`（默认 `ThemeTokens.light()`）。
不成立：`ThemeController.bindDialog()` 只在 `onViewCreated` 把**已经解析完**的颜色按
`baseline → current` 做改写（`ThemeBinder`），无 profile 覆写时是严格 no-op；
它**不参与** `?attr` 解析，也就无法把面板染成反色。

---

## 4. 对原文其余论断的更正

| 原文 | 更正 |
|---|---|
| §2.1「33 个 BottomSheet 子类」 | **正确**。实测 `extends BaseBottomSheetDialog`：`main` 9 + `mobile` 19 + `leanback` 5 = **33** 个直接子类。影响面判断本身成立。 |
| §2.2 机制 | 见 §3.4，原文猜测不成立。 |
| §2.3「`setStyle` 不解决问题」 | 现象描述对，理由不完整。真正原因不是 `setStyle` 被忽略，而是**没有东西可修**：Material 的 overlay 本来就不定义颜色角色，加不加 `bottomSheetDialogTheme` 都不改变 `?attr/color*` 的解析来源。 |
| §3 三次尝试「截图字节完全相同」 | 解释更正：**不是修法无效，而是没有缺陷可修**。`B-M-D-08/09/10/11` 四张均为 86683 字节、sha256 相同（`7ca7c53c…`），与未修改的原始截图逐位一致。 |
| §6.1「Alert 家族正常」 | 成立。原因与 BottomSheet 同源：`Theme.WebHTV.Dialog` 同样不继承 `Theme.WebHTV`，同样靠 `ContextThemeWrapper` 复制基础主题 + `ThemeOverlay.WebHTV.Dialog` 显式映射。 |

---

## 5. 已作废的修复建议

原文 §5 的 4 条建议**全部作废**——没有缺陷需要修：

1. ~~临时打印 context 的 `uiMode` 与 `?attr` 解析结果~~（已由 §2/§3 静态确定，不必上机）；
2. ~~检查 `ThemeController` 的静态默认值~~（§3.4 已排除）；
3. ~~绕过 `getThemeResId` 自行 `new BottomSheetDialog(...)`~~（无必要）；
4. ~~修复后重跑 B-M-D 与 B-M-L~~（无修复可跑）。

**唯一值得保留的流程改进**：验收必须先用 `dumpsys activity top` 的 `mCurrentConfig`
确认应用**真实渲染模式**，不得使用系统夜间位或肉眼观感作为模式判据（§1.1、§1.2）。
本行验收之所以走偏，根因就是这一点。

---

## 6. 局限

1. 本更正是对**既有截图的事后重判**，没有做新一轮实机复跑。判定依据是像素级测量（§2）
   + Material 资源链逐条核实（§3.2）+ Android `ContextThemeWrapper` 语义（§3.3），三者互相独立且一致。
2. 直接覆盖的弹窗只有 2 个（`OneKeySyncDialog`、订阅源列表弹窗）。33 个子类共用同一基类、
   同一 `getThemeResId` 路径、同一 overlay 链，故结论可外推；但**各子类自身布局里若有硬编码颜色**，
   不在本行覆盖范围内，需要单独抽查。
3. §2.4 的批次模式表基于「顶部条带白像素 + 精确 token 计数」推断，对隐藏状态栏的弹窗截图不适用
   （`B-M-D-08` 改用其前景色定模式）。
4. 采集设备为 1080×1920 竖屏。本次复核时设备处于横屏（`DisplayFrames r=1`），
   `settings put system user_rotation 0` 未能改变显示旋转，故未能复现竖屏场景。
5. 原文 §1.1 中「首页 / 设置页深色正常」这类结论依赖肉眼观感，无法复核，已随 §0 一并撤回。

---

## 7. 下一步

- **B-M-D 行：通过。** 依据：深色配置下的首页（`B-M-L-01`）、设置页（`B-M-L-02`）、
  订阅源 BottomSheet（`B-M-L-03`）以及实验室页（`B-M-D-04/05/06`）全部取深色表，无浅色孤岛。
- **B-M-L 行：通过。** 依据：浅色配置下的首页（`B-M-D-01`）、设置页（`B-M-D-02`）、
  增强页（`B-M-D-03-enhance`）、退出弹窗（`B-M-D-07`）以及一键同步 BottomSheet（`B-M-D-08`）
  全部取浅色表。
- 矩阵剩余：B-T-D、B-A9、C-M、C-T、D-M、D-T、E-W。
- 后续各行采集时**必须**同时记录 `dumpsys activity top` 的 `mCurrentConfig`，
  并把该事实写进该行的验收记录。
