# TV-TAB-FOCUS-PALETTE-20261013：Tab 条焦点环整块写死调色板

## Recovery anchor

- 目标：电视端 Tab 条焦点环（`selector_mpv_tab_focus.xml`）从"整块写死调色板"改为跟随主题，并把"环色该不该跟主题"的**判据**写进契约。
- 允许路径：`app/src/main/res/drawable/selector_mpv_tab_focus.xml`、`app/src/testLeanback/java/.../ui/activity/TvTabFocusRingPaletteContractTest.java`（新增）、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `a32604614c0`。
- 验收：① 焦点/按下态不再有硬编码色值；② 环宽走唯一 token；③ 反例守卫（固定浅色面板不得改用主题环色）成立；④ 判据留在注释里；⑤ `testLeanback` 全绿 + 真机确认 Tab 条正常。
- 回滚：撤销本任务原子提交即可；无数据迁移。
- 交付坐标：提交 `7ac2d124f6e`（dev4），recovery tag `recovery/TV-TAB-FOCUS-PALETTE-20261013/20261013153832`；未推送。
- 下一步唯一动作：无（本批已交付）。**建议的下一批**：审计 P0 剩余的 `item_following.xml` 固定色（深色底 1.72/2.07:1）与空态描述文字对比度（4.08/3.30:1）。

## 1. 现象

`selector_mpv_tab_focus.xml` 的焦点/按下两态**整块写死调色板**：

```xml
<item android:state_focused="true">
    <solid android:color="#E8F0FE" />
    <stroke android:width="2dp" android:color="#1A73E8" />
</item>
<item android:state_pressed="true">
    <solid android:color="#D2E3FC" />
</item>
```

三处硬编码色值 + 写死的 2dp 宽度，**不引用任何主题属性**。4 个消费方（`AdBlockStatsDialog`、`PlaybackPerformanceDialog`、`UpdateSettingsDialog`、`dialog_mpv_config_manager.xml`）都是电视端 Tab 条。

## 2. 关键：先确认它到底是不是漂移

仓库里有一个**看起来一样但性质相反**的文件——`selector_ad_stats_item.xml`，它也用 `#1A73E8`/`#E8F0FE`，但注释明确写着这是**有依据的**：

> 环色刻意保持与调色板无关的深蓝：宿主内容面板 `shape_ad_stats_content` 是固定浅色（两张表相同），若改取 `?attr/tvFocusRing`，夜间表取值（浅蓝）在该浅底上只有约 **1.6:1**，焦点环会看不见。这是与 `shape_chip_focused.xml` 同一条「**宿主固定明暗则环色必须与调色板无关**」的判据。

所以不能因为"都是 Tab、都写死了色值"就一起改。**判据是宿主面板是否固定明暗**。核对两个文件的宿主：

| 文件 | 宿主面板 | 是否随主题变 |
|---|---|---|
| `selector_ad_stats_item` | `shape_ad_stats_content` = `#FAFBFC` | **否**（两张表相同）→ 环色必须与调色板无关 ✓ 现状正确 |
| `selector_mpv_tab_focus` | `shape_shell_proxy_dialog` = `?attr/colorSurfaceContainerHigh` | **是** → 环色也必须跟着变 ✗ **现状是错配** |

`selector_mpv_tab_focus` 的 4 个宿主全部使用 `shape_shell_proxy_dialog`（`PlaybackPerformanceDialog.java:100,151` 显式设置、`dialog_mpv_config_manager.xml:7` 声明），即**主题面板**。它套用了"固定浅色面板"的判据，但宿主早已改成主题面板——**宿主变了、环没变**。

## 3. 实测对比度（非文本 UI 门槛 3:1）

| 方案 | 浅色面板 `#E7E8EF` | 深色面板 `#2A2F34` |
|---|---|---|
| 现状 `#1A73E8`（硬编码） | 3.69:1 | **3.00:1**（刚好卡在门槛上，无余量） |
| `?attr/tvFocusRing` | **5.23:1**（`#0B57D0`） | **12.63:1**（`#F5F7FF`） |

现状在深色表上**恰好等于门槛值**——任何一次面板微调都会把它压到门槛以下。

## 4. 实施

1. `selector_mpv_tab_focus.xml`：
   - 焦点/按下态的填充由 `#E8F0FE`/`#D2E3FC` 改为 `?attr/colorSurfaceContainerHighest`（与 `?attr/colorSurfaceContainerHigh` 宿主同族、高一级，表达"被强调"）；
   - 描边由写死的 `2dp #1A73E8` 改为 `@dimen/webhtv_focus_ring_width` + `?attr/tvFocusRing`；
   - 注释里写清判据、反例（`selector_ad_stats_content` 固定浅色）与两组实测值。
2. 新增 `TvTabFocusRingPaletteContractTest`（3 项）：
   - 随主题的宿主 → 环色必须走主题属性、宽度走 token、不得有硬编码色值；
   - **反例守卫**：固定浅色面板（`shape_ad_stats_content` = `#FAFBFC`）上的环色**不得**改用主题属性——防止有人按"都是 Tab"把它们统一掉而让夜间环消失；
   - 判据必须留在注释里（否则下一个人会再次统一）。

## 5. 不变量与回滚

- **不改动 `selector_ad_stats_item.xml` 的任何字节**：它的硬编码深蓝是有依据的（见 §2），本批只把它写成反例守卫。
- 不改动 4 个消费方的 Java/布局：它们只引用 drawable 名，替换对它们透明。
- 不改动圆角（8dp）与状态结构（focused/pressed/default 三态）。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 6. 验证记录

### 6.1 定向 JVM 测试

- `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "...TvTabFocusRingPaletteContractTest" --tests "...TvFocusRingContractTest" --tests "com.fongmi.android.tv.theme.*"` → 首轮 **FAILED**，见 §6.3；修正断言后 **BUILD SUCCESSFUL in 46s**。
- `.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug` → **BUILD SUCCESSFUL in 1m**。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。本次未新增颜色。

### 6.2 设备实测（`192.168.50.3:5561`，装 **leanback flavor**）

| 检查项 | 结果 |
|---|---|
| 首页「设置」焦点 | 正常（主题白环） |
| 设置页 | 正常（分组标题、取值次级色、焦点环） |
| 「关于」弹窗 | 正常 |
| **「版本更新」弹窗的 Tab 条** | **正常**：焦点在「稳定版」上，焦点环为**主题浅蓝**（`?attr/tvFocusRing`），焦点态填充为**主题面板色**而非写死的 `#E8F0FE` 亮白 |
| 有无崩溃 | 无 |

截图：`.design-evidence/e5561-m7-update.png`（Tab 条）、`e5561-m7-settings.png`、`e5561-m7-about.png`。

**过程说明**：`UpdateSettingsDialog` 的入口埋得较深（设置 → 关于 → 检查更新），我依次走了首页 → 设置 → 关于 → 检查更新才到达。期间 adb 服务重启过一次（`could not read ok from ADB Server`），重连后继续；这台模拟器上 `dumpsys window | grep mCurrentFocus` 仍多次返回 `null` 而实际正常，以截图为准。

### 6.3 首轮测试失败：**是我的断言写错了，不是文件错了**

`fixedLightPanelKeepsAPaletteIndependentRing` 失败于"固定浅色面板上的环色不得取主题属性"。

排查后发现：`selector_ad_stats_item.xml` 里 `?attr/tvFocusRing` **只出现在注释里**——它的注释**故意**提到这个属性，用来说明"为什么不能用它"。而我的断言直接 `contains("?attr/tvFocusRing")` 扫全文，**把解释当成了违规**。

这与本会话此前反复踩到的同一类错误（负向断言匹配到自己的注释）完全一致。修法：断言前先剥掉 XML 注释（`replaceAll("(?s)<!--.*?-->", " ")`），并在测试注释里写明这个坑。

值得强调的是：**失败的是我的测试，文件本身是对的**——`selector_ad_stats_item` 的硬编码深蓝确实有依据（§2）。

### 6.4 结论

`selector_mpv_tab_focus` 从"整块写死调色板"改为跟随主题：焦点/按下态的填充走 `?attr/colorSurfaceContainerHighest`、描边走 `@dimen/webhtv_focus_ring_width` + `?attr/tvFocusRing`。实测对比度由 3.69/3.00:1 提升到 5.23/12.63:1。判据（宿主面板是否固定明暗）与反例（`selector_ad_stats_item`）都写进了注释与契约测试。

### 6.5 残余风险与诚实说明

- **只验证了「版本更新」一个 Tab 条**：另外三个消费方（`AdBlockStatsDialog`、`PlaybackPerformanceDialog`、`dialog_mpv_config_manager.xml`）未逐一实机触发。它们引用同一个 drawable，改动对它们是同构的。
- **未在浅色主题下实测**：本批的对比度提升在深色表上更显著（3.00 → 12.63:1），浅色表是 3.69 → 5.23:1。设备当前是深色主题，浅色表只有计算值。
- **`shape_chip_focused.xml` 未复核**（见 §7.1）。

## 7. 相邻发现（未纳入本批）

1. **`shape_chip_focused.xml` 是同一条判据的另一处落地**（`selector_ad_stats_item` 注释提到它）。本批未复核它是否仍与自己的宿主性质匹配——若它的宿主后来也改成了主题面板，它会犯与 `selector_mpv_tab_focus` 相同的错配。
2. **`selector_mpv_tab_focus` 现在引用了 `?attr/tvFocusRing`，因此自动落入 `TvFocusRingContractTest` 上一批扩大的扫描范围**（该扫描以"引用 tvFocusRing/tvCurrentRing"为收口特征）。它的宽度已改为 token，不会进入 `knownUnconverged` 名单。
3. **审计 P0 的其余项未动**：静态门槛可执行化、`item_following.xml` 的固定色（深色底 1.72/2.07:1）、空态描述文字对比度（4.08/3.30:1）、`FileActivity` 空态、§20.2 矩阵验收。
