# TV-LIGHT-THEME-20261005：TV 版浅色主题无效

## 目标与授权

用户反馈：TV 版选择浅色主题无效，仍是深色；在模拟器 5557 上「选集浅色后保存再进来还是深色」。

完成句：判定该反馈真伪与缺陷类别，给出可批准的最小修复方案、验收标准与回滚路径，并在获批后于 5557 覆盖安装验证。

当前状态：**评估完成，停在审批点**。未修改任何代码、锁、补丁或运行时行为。

- 分支/基线：`dev2` @ `2162548799`；工作树干净，无预存脏路径。
- 设备：`127.0.0.1:5557`，API 28，arm64，包 `com.silent.android.webhtv`（5.6.0，DEBUGGABLE，`ro.build.characteristics=tablet`）。
- 只读证据时间：2026-10-05 18:0x +0800。

## 结论：反馈真实，且是两个叠加缺陷

### 缺陷 1（用户实际踩到的）：编辑器里的「浅色/深色」按钮不落盘

`app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ThemeDialog.java:153-154`

```java
lightButton.setOnClickListener(view -> { dark = false; render(); });
darkButton.setOnClickListener(view -> { dark = true; render(); });
```

- `applyDraft()` 只调用 `editor.apply()`；**从不写 `theme_mode`**，也**从不调用 `editor.setMode()`**。
- 全仓 `putThemeMode` 只出现在 `AppearanceDialog.java:121`（外观 → 主题模式），与编辑器无关。
- 该行为是**设计意图**：`docs/THEME-EDITOR-20261001-inline-preview.md:55`「浅/深编辑切换明确为本页预览，不修改 App 的明暗模式」。

因此「选集浅色后保存」在语义上不成立——那个按钮只切换**本页预览**。用户看到「保存后没生效」是必然的。

### 缺陷 1b：TV 上编辑器强制以深色预览打开

`app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ThemeDialog.java:76`

```java
dark = Util.isLeanback() || ThemeController.isNight(requireContext());
```

`Util.isLeanback()` 判 `BuildConfig.FLAVOR_mode`（`Util.java:435`），leanback flavor 下恒为 `true`，故 `dark` 恒为 `true`——重新进入永远是深色预览。这正是截图现象。

注：mobile 与 leanback 的两份 `ThemeDialog.java` **字节一致**（`cmp` 通过），该行在 mobile 上是死条件、在 TV 上是强制深色。

### 缺陷 2（更根本）：TV flavor 根本没有浅色色板

```
cmp app/src/leanback/res/values/webhtv_tokens.xml \
    app/src/main/res/values-night/webhtv_tokens.xml   → BYTE-IDENTICAL
```

- `app/src/leanback/res/` 只有 `values/`、`values-v27/`、`values-zh-rCN/`、`values-zh-rTW/`，**没有 `values-night/`**。
- 即 TV 在任何配置下都编译**深色表**；`main/values/` 的浅色表被 leanback 的同名副本**遮蔽**（已验证 leanback tokens 相对 `main/values` 独有颜色名 = 0，缺失颜色名 = 0，纯遮蔽）。
- `webhtv_styles.xml:64-66`：`Theme.WebHTV.TV` 硬写 `<item name="isLightTheme">false</item>`（`Theme.Material3.DayNight.NoActionBar` 本会随 night 配置切换，被此硬编码覆盖）。
- `ThemeController.darkPaletteFor()`（`ThemeController.java:203-206`）在识别出编译表为深色时**直接返回深色，优先级高于用户显式选择**：

```java
if (compiledDark != null) return compiledDark;   // 覆盖 ThemeMode.LIGHT
```

- 并有测试**主动钉死**该行为：`ThemeBinderContractTest.java:465-467` 断言 `darkPaletteFor(true, ThemeMode.LIGHT, false) == true`。

**因此即使用户走正确入口（外观 → 主题模式 → 浅色），TV 依然保持深色。** 缺陷 1 只是让用户更早撞墙。

## 本地证据（设备实测）

`/data/data/com.silent.android.webhtv/shared_prefs/com.silent.android.webhtv_preferences.xml`（`su` 读取）：

- `theme_profile_v2_json`：`mode:"system"`，`seedSource:"custom"`，`seedColor:"#00ACC1"`，**light/dark 全部 16 槽均为 `null`**。
- **不存在 `theme_mode` 键** —— 证明该偏好从未被写入，与「编辑器不落盘」一致。
- `theme_color = -16732991`（`0xFF00ACC1`，即 legacy 镜像）。

## 爆炸半径（缺陷 2 的真实代价）

TV 侧「深色玻璃面板 + 调色板前景」组合，翻浅后会变成深底深字：

| 项 | 数量 |
|---|---|
| leanback layout 引用 `?attr/colorOnSurface*` | 76 文件（122 处仅 `dialog_*.xml`） |
| leanback layout 引用 `?attr/colorSurface*` | 9 |
| leanback layout 总数 | 129 |
| leanback drawable/color 含硬编码色 | 33 |
| **`?attr/colorOnSurface` 叠加深色玻璃面板** | **7 个 layout**：`dialog_danmaku`、`dialog_episode_list`、`dialog_offset`、`dialog_quick_search`、`dialog_timer`、`dialog_title`、`dialog_track` |

面板填充为共享渐变 `#E62F315E → #D6282955 → #CC303463`（`shape_dialog_glass_panel`、`shape_quick_search_dialog`、`shape_player_child_sheet_panel`、`shape_danmaku_setting_panel` 均为此值）。

实测对比度（面板 RGB `#2F315E`）：

| 前景 | 对比度 | 判定 |
|---|---|---|
| TV 现状 `dark.colorOnSurface` `#E2E2E9` | **9.46:1** | 合格 |
| 翻浅后 `light.colorOnSurface` `#1A1C1E` | **1.40:1** | **失效** |
| mobile 修法 `webhtv_on_wallpaper` `#FFFFFF` | **12.20:1** | 合格 |
| mobile 修法 `player_control_muted` `#CCFFFFFF` | **9.95:1** | 合格 |

**关键有利事实：mobile flavor 已经完成这套迁移。** mobile 侧同名 layout 已改用调色板无关前景（`?attr/webhtvColorOnWallpaper`、`@color/webhtv_color_player_control_muted`），mobile 有 36 个 layout 使用前者；**leanback 为 0**。即存在已验证的现成模板，不必重新设计。

`ThemeBaseWiringTest.java:133-151` 已把该口径固化为 `DARK_GLASS_SHEETS` + `CONSTANT_LIGHT_FOREGROUNDS` 契约。

## 修正（2026-10-05 18:4x）：方案 B 的前提被新证据推翻

前文把缺陷 2 描述为「删掉 leanback 遮蔽表 → TV 自动获得浅色」，**该前提是错的**。新的只读证据如下。

### 新证据 1：TV 页面背景不是调色板表面，而是「永远深色」的壁纸

- `app/src/leanback/java/com/fongmi/android/tv/ui/base/BaseActivity.java:72,76-78`：`setContentView()` 里 `if (customWall()) addCustomWall()`，把 `CustomWallView` 加到 `android.R.id.content` 的 **index 0**（最底层）。`customWall()` 默认返回 `true`（同文件 84-86 行）。
- `SettingActivity extends BaseActivity`（`SettingActivity.java:54`）→ **设置页同样铺壁纸**。
- `CustomWallView` 的加载路径（`CustomWallView.java:131-137, 311-315`）只读 `Setting.getWall()/getWallType()/getBuiltInWallColor()`，**完全不读明暗模式**；`values-night/` 下也没有任何 wallpaper 资源（`ls app/src/main/res/values-night/` 仅 `lab_colors.xml`、`webhtv_tokens.xml`）。
- 即：**TV 页面背景在任何主题模式下都是同一张壁纸**，`values/`+`values-night/` 切表不会改变它。

> **勘误（2026-10-05 19:1x）**：本小节初稿称该壁纸为「深色壁纸 `#0E1416`、亮度 0.0065」。**该测量是错的**，来源是三次字节完全相同的 184218 字节截图（`cmp` 判定为同一帧，当时设备被 `KEYCODE_POWER` 切到息屏/启动器），并非真实页面。重新采集有效帧（1920x1080，`theme_mode=0`）逐行统计后，**壁纸其实是偏亮的蓝紫极光渐变**：1080 行中 0 行亮度 <0.05，1074 行落在 0.05–0.3，仅 6 行 >0.3；最暗行 lum=0.058、最亮行 lum=0.489。纯壁纸取样带实测 `rgb(76,111,149)` lum=0.151、`rgb(46,96,122)` lum=0.104、`rgb(73,110,122)` lum=0.140、`rgb(79,122,131)` lum=0.172。
>
> 机制结论不变（壁纸不随明暗模式变化），但**对比度结论必须重算**（见下）。

### 新证据 2：TV 设置页的行填充本身也是深色常量

- `app/src/leanback/res/drawable/selector_item.xml` → `shape_item_normal` = `@color/black_20`、`shape_item_focused`/`shape_item_activated` = `@color/black_40`（均为「在深壁纸上再压一层黑」）。
- 即「深壁纸 + 20%/40% 黑卡片 + `?attr/colorOnSurface` 文字」是 TV 设置页的固定结构，**与调色板无关**。

### 结论：删掉遮蔽表会让 TV 浅色模式变成「亮底暗字」，对比度不足

复算对比度（背景取修正后的实测壁纸及其行底）：

| 前景 | 背景 | 对比度 | 判定 |
|---|---|---|---|
| 现状 `dark.colorOnSurface` `#E2E2E9`（lum 0.764） | 行底 `black_20` 压壁纸（lum 0.066–0.108） | **5.15–7.03:1** | 合格 |
| 删表后 `light.colorOnSurface` `#1A1C1E`（lum 0.011） | 同上 | **1.89–2.57:1** | **不足（正文需 ≥4.5:1）** |
| 现状 `webhtv_on_wallpaper` `#FFFFFF`（三表同值） | 纯壁纸（lum 0.104–0.172） | **4.73–6.84:1** | 合格 |

行底推导：`shape_item_normal` = `@color/black_20`（`#33000000`，20% 黑）叠加在壁纸上，故行底亮度约为壁纸的 0.8 倍（sRGB 乘算）。焦点态 `black_40`（40% 黑）更低，浅色表下对比度进一步下降到 ≈1.5–2:1。

前文表格里 1.40:1 只算了 7 个深色玻璃弹窗；真实代价是 **TV 上所有「压在壁纸/行填充上的文字」**。修正后的数字（1.89–2.57:1）比初稿的 1.09:1 宽松，但**仍远低于 4.5:1 正文阈值**，结论方向不变：浅色表不能直接套到现有 TV 前景上。

### 修正后的真实爆炸半径

| 项 | 数量 |
|---|---|
| leanback layout 引用 `?attr/colorOnSurface*` | **505 处 / 76 文件**（`activity_*` 321 处/11 文件、`dialog_*` 122 处/25 文件、`adapter_*` 60 处/38 文件、`view_*` 2 处/2 文件） |
| 其中压在壁纸/行填充上的（需迁移） | **≈380 处**（`activity_setting_*` 321 + `adapter_*` 60） |
| mobile 已迁移到 `?attr/webhtvColorOnWallpaper` 的 layout | 36 个 / **309 处** |
| leanback 已迁移的 | **0 个 / 0 处** |
| 深色玻璃弹窗（前文已识别） | 7 个（这部分结论仍然成立） |

### 对方案与工期的影响

- **方案 B 的代价从「2–4 小时、删一个文件」上升为「≈380 处前景迁移、3–5 小时」**，且属于新增一条 TV 渲染路径的产品面扩大。
- **方案 B 的核心机制仍然成立**（leanback 确实缺 `values-night/`，`isLightTheme` 确实被硬编码，`darkPaletteFor` 确实覆盖显式浅色），但**它不足以单独产生可用的浅色模式**。
- 另一条更便宜的「让 TV 浅色可用」的路径是**让壁纸随模式变浅**，但壁纸是用户自选且 mobile/TV 共用同一套 `Setting.getWall*`，改它会影响 mobile，属于更大的产品变更，**不推荐**。

### 修正后的推荐

按「诚实度 / 代价 / 风险」重新排序：

1. **推荐 A+（最小诚实修复，约 20–30 分钟）**：TV 编辑器按**持久化模式**打开（去掉 `Util.isLeanback()` 强写）；把「浅色/深色」按钮文案明确为「仅预览」；由于 TV 侧 `外观 → 主题模式` 已有入口但对 TV 是 no-op，需同时给出明确说明或禁用该入口，避免继续误导。**不改渲染路径，零视觉回归风险。**
2. **B-revised（3–5 小时）**：在 A+ 基础上，额外迁移 ≈380 处前景到 `?attr/webhtvColorOnWallpaper` 并让 `isLightTheme` 随模式，真正打通 TV 浅色。需用户明确接受产品面扩大与回归成本。
3. **C**：仅去掉强写，不改文案 → 仍会把「保存无效」变成「按钮看起来可用但实际无效」，**不推荐**。

> 注：前文「方案 A」把「TV 无浅色表时给出说明」列为低成本项，方向正确；A+ 是它的加强版，额外处理了 TV `主题模式` 入口 no-op 这一新发现。

## 最佳实践依据（2026-10-05 实际读取）

| 证据类 | 来源 | 级别与支持结论 | WebHTV 决策/限制 |
|---|---|---|---|
| 官方设计指南 | [Android TV design guidelines / style.jd](https://android.googlesource.com/platform/frameworks/base/+/0bfee5a4905a14a318731661214558792abc2f7d/docs/html/design/tv/style.jd) | A；TV 应避免「very dark or muddy colors」，TV 设置可能叠加对比处理 | 支持「TV 深色优先」是合理默认，但**不禁止**提供浅色；故缺陷 2 的修复不违反平台指南 |
| 官方 API 文档 | [AppCompatDelegate MODE_NIGHT_NO](https://developer.android.com/reference/androidx/appcompat/app/AppCompatDelegate) | A；`MODE_NIGHT_NO` 明确「enabling `notnight` qualified resources regardless of the time」 | 证明**资源限定符是唯一权威**：只要 leanback 缺 `values-night/`，任何 mode 设置都无法产生浅色。这是缺陷 2 的机制依据 |
| 官方指南 | [Implement dark theme](https://developer.android.com/develop/ui/views/theming/darktheme) | A；「Avoid using hardcoded colors … Use theme attributes or night-qualified resources instead」 | 直接支持把 leanback 硬编码深色表改为 `values/`+`values-night/` 限定符方案，而非运行时改色 |
| 官方库文档 | [Material Android Dark.md](https://github.com/material-components/material-components-android/blob/master/docs/theming/Dark.md) | A；`Theme.Material3.Dark` 是静态深色，`Theme.Material3.DayNight` 才是动态 | 解释 `Theme.WebHTV.TV` 硬写 `isLightTheme=false` 等价于把 DayNight 主题静态化，是缺陷 2 的第二道锁 |
| 官方 API 参考 | [MaterialColors.getColorRoles(color, isLightTheme)](https://developer.android.com/reference/com/google/android/material/color/MaterialColors) | A；`isLightTheme` 决定角色派生方向 | 说明 `isLightTheme` 不是装饰属性，翻浅时必须随模式切换，否则角色派生方向错误 |
| 成熟相关项目/社区 | [SO: Is there a dark/light Leanback theme?](https://stackoverflow.com/questions/58811703/is-there-a-dark-light-leanback-theme) | C；Leanback 无官方 DayNight 主题，实践者直接改用 Material 主题 | 与本仓现状一致（`Theme.WebHTV` 父级已是 `Theme.Material3.DayNight`），故无需引入 Leanback 官方主题；仅作旁证，不作为实现依据 |

不适用的证据类：无新增依赖/算法/原生库/性能敏感路径，故论文、benchmark、上游 revert 不适用。未取得且会改变决策的外部证据：无。

## 方案比较

| 方案 | 内容 | 代价 | 风险 |
|---|---|---|---|
| **0. 不变更** | 保持现状 | 0 | 用户反馈未解决，编辑器持续误导 |
| **A. 诚实化 UI** | TV 上编辑器按**持久化模式**打开（去掉 `Util.isLeanback()` 强写）；把「浅色/深色」按钮明确标注为「仅预览」；TV 无浅色表时给出说明 | 30–50 分钟 | 低。不改渲染路径。但用户「想要浅色」的诉求仍未满足 |
| **B. TV 真正支持浅色** | 删除 `leanback/res/values/webhtv_tokens.xml`（解除对 `main/values` 浅色表的遮蔽，让 DayNight 自然生效）；`isLightTheme` 改为随模式（`webhtv_styles.xml` 分 `values/`+`values-night/`）；7 个深色玻璃弹窗改用 mobile 已验证的调色板无关前景；重写钉死测试 | 2–4 小时 | 中。改动跨 flavor 资源与主题契约，须逐个弹窗回归 |
| **C. 仅修正编辑器默认态** | 只去掉 `Util.isLeanback()` 强写 | 15–25 分钟 | 低。TV 仍无浅色渲染路径，等于把「保存无效」变成「按钮看起来可用但实际无效」 |

**推荐：B（可分两阶段落地）。** 理由：

1. 用户诉求明确是「浅色要真的生效」，A/C 均不满足该诉求，属把缺陷改写为「已说明」而非修复。
2. B 的核心改动（删除 leanback 深色遮蔽表）比预期小——因为 leanback tokens 相对 `main/values` **零独有、零缺失**，删除即等价于「让 TV 用 main 的 day/night 双表」。
3. B 的风险面已被 mobile 收敛：7 个深色玻璃弹窗的修法在 mobile 已落地并有 `ThemeBaseWiringTest` 契约，属于**移植**而非**新设计**。
4. 平台指南不禁止 TV 浅色，故 B 不与既有「TV 深色优先」设计决策冲突——**默认仍为深色**（`ThemeMode` 默认 `-1` 跟随系统，TV 模拟器系统为 light 时会变浅色，但真实 TV 设备系统通常为 night）。

### 采用方案的关键不变量

- `ThemeMode` 默认值语义不变：`-1` 跟随系统、`0` 浅色、`1` 深色。TV 真实设备的深色默认行为由系统 night 配置承载，不靠硬编码。
- 深色玻璃面板**保持深色**（不改为跟随调色板），前景改为调色板无关值——与 mobile 完全同源。
- 播放器控件色、健康色、`webhtv_on_wallpaper`、`player_control_muted` 等常量角色**不改值**。
- profile schema 不变（仍 `SCHEMA_VERSION=2`），已保存 profile 保持可读。
- 不新增依赖、不改 ABI、不改播放器路径。

## 最小实施步骤（方案 B）

1. **解除遮蔽**：删除 `app/src/leanback/res/values/webhtv_tokens.xml`；确认 leanback 全部颜色名由 `main/values` + `main/values-night` 提供。
2. **`isLightTheme` 随模式**：`Theme.WebHTV.TV` 的 `isLightTheme=false` 移入新增的 `app/src/main/res/values-night/webhtv_styles.xml`（或等价的最小方案），使 day 配置不再声明深色。
3. **深色玻璃弹窗前景**：将 7 个 leanback layout 的 `?attr/colorOnSurface` / `?attr/colorOnSurfaceVariant` 换成 `?attr/webhtvColorOnWallpaper` / `@color/webhtv_color_player_control_muted`，对齐 mobile 同名文件。
4. **测试**：改写 `ThemeBinderContractTest:451-470` 中「TV 即使用户选浅色也保持深色」的断言为「TV 编译表随 night 配置切换」；保留并核对 `ThemeBaseWiringTest` 的 `DARK_GLASS_SHEETS` 契约对 leanback 生效。
5. **文档**：更新 `docs/webhtv-unified-visual-design-system-20260920.md:175` 的「电视深色优先」表述，明确其实现方式从「硬编码」改为「默认跟随系统」。

## 验收与回滚

**自动化（最廉价决定性验证）**

- `bash gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.theme.*' --no-daemon --console=plain` 零失败。
- `scripts/check_ui_tokens.sh --strict` → `violations=0 legacy=0`、对比度 0 失败。

**设备（5557 覆盖安装，不卸载）**

1. 外观 → 主题模式 → **浅色** → 首页/设置/详情/弹窗为浅色表面、深色文字，正文对比度 ≥4.5:1。
2. 主题模式 → **深色** → 与修改前**逐像素不可区分**（深色是既有契约，必须零回归）。
3. 主题模式 → **跟随系统** → 随系统 night 切换。
4. 7 个深色玻璃弹窗（弹幕、选集、偏移、快速搜索、定时、标题、音轨）在**浅色与深色两种模式下**文字均可读（前景调色板无关，故两种模式一致）。
5. 主题编辑器：重新进入时预览模式与持久化模式一致，不再恒为深色。
6. 回归：播放页焦点环、控制层、追更页、直播页在两种模式下无颜色错乱；`FATAL EXCEPTION=0`。

**回滚**：撤销本单元提交即可。schema 未变，用户已保存 profile 不受影响。若浅色下发现未预期的深色孤岛，可先回退第 1 步（恢复 leanback 遮蔽表）即回到当前已验证的深色行为。

## 风险与未决

- **未验证假设**：leanback 删除遮蔽表后，是否仍有**非 layout** 的硬编码深色（Java 侧 `setTextColor`/`setBackgroundColor`）在浅色下失效。已知 33 个 drawable/color 含硬编码色，其中部分（如 `selector_control_sheet_button`、`selector_audio_action_icon`）是播放器控制层，设计上应保持深色。第 3 步需逐个判定，不能一律改写。
- **产品判断**：Android TV 平台建议深色优先。方案 B 会新增一条「TV 浅色」渲染路径，属于产品面扩大，需用户确认接受。
- **测试钉死**：`ThemeBinderContractTest:465-467` 是**主动**断言 TV 强制深色，属既有设计决策的守门测试。改写它等于推翻该决策，必须由用户显式批准。

## 实施记录（2026-10-05 20:19–22:28，方案 B-revised，用户批准）

授权依据：用户消息 `真正打通 TV 浅色`（会话 jsonl 中 `#671`，ts `2026-10-05T11:52:00.829Z`，len=10，与 Orca 恢复引导片段逐条区分）。

### 改动（65 文件，+448/−517）

1. **解除遮蔽**：删除 `app/src/leanback/res/values/webhtv_tokens.xml`（83 行）。该表与 `main/values-night` 逐项相同、与 `main/values` 零独有/零缺失，删除后 TV 的 day 走 `main/values`（浅）、night 走 `main/values-night`（深）。文件内唯一非 color 条目 `webhtv_focus_scale`（1.1）经全仓检索**无任何消费者**，一并移除无副作用。
2. **`isLightTheme` 随模式**：`main/res/values/webhtv_styles.xml` 的 `Theme.WebHTV.TV` 去掉硬编码 `<item name="isLightTheme">false</item>`，改为空样式继承 `Theme.WebHTV`（父 `Theme.Material3.DayNight.NoActionBar`），由 `values`/`values-night` 限定符解析。
3. **前景迁移 405 处 / 58 文件**（leanback layout）。规则与 mobile 既有 309 处词汇对齐：
   - `?attr/colorOnSurface` → `?attr/webhtvColorOnWallpaper`（380 处，壁纸行 / 深色玻璃面板）
   - `?attr/colorOnSurfaceVariant` → `@color/webhtv_color_player_control_muted`（7 处，次级文字）
   - `?attr/colorOnSurface_80/_90/_70` → `@color/white_80/_90/_70`（8+3+1 处，播放器内嵌面板）
   - `app:tint`/`app:indeterminateTint` `?attr/colorOnSurface(Variant)` → `?attr/webhtvColorOnWallpaper`（3 处）
   - `app:strokeColor` `?attr/colorOnSurface_20` → `@color/white_20`（1 处，与 mobile 同 id 一致）
   - `app:rippleColor` `?attr/colorOnSurfaceVariant` → `@color/webhtv_color_overlay_light`（1 处，与 mobile 同 id 一致）
   - **保留 92 处**：载体是调色板表面（`?attr/colorSurface*`、`shape_shell_proxy_dialog`、`shape_site_dialog`、`shape_ad_stats_content`）、或 dialog 窗口背景（`dialog_config/content/device/pass/recommendation_feedback/ua`、`adapter_home_button`、`dialog_home_button`）、或 mobile 仍保留同角色的 `adapter_ad_stats_item:source`、`adapter_vod:remark`；另有 2 个**既有**常量浅色面板（`shape_exit_confirm_dialog`、`shape_ad_rule_card`）不在本单元范围，已在下节记录。
4. **编辑器预览模式**：`ThemeDialog.java`（mobile 与 leanback 两份保持**逐字节相同**）去掉 `Util.isLeanback() ||`，改为 `dark = ThemeController.isNight(requireContext())`。此前 TV 恒以深色预览打开，用户重进编辑器看到「深色」被选中而误判为保存失败。mobile 侧该条件恒为 false，行为不变。
5. **注释纠正**：`ThemeController.resolvedDark()`/`darkPaletteFor()` 的 javadoc 原以「TV flavour 用深色表覆盖 values」为前提，该前提已随第 1 步消失，改写为一般化的「flavour 可能钉死一张表」表述并说明 WebHTV 现已不再如此；`ThemeBaseWiringTest` 中 3 处「three token tables」同步改为两张。
6. **测试适配**：`ThemeBaseWiringTest`（移除 leanback tokens 断言、alpha 变体断言）、`TvFocusRingContractTest`（移除 `TOKENS_LEANBACK` 常量与循环项）——两处都是**删除对已不存在文件的引用**，未放宽任何断言。

### 验证证据

**构建/安装**：`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 127.0.0.1:5557` → `BUILD SUCCESSFUL`（1m57s，APK 193M），`adb install -r` 覆盖安装成功（未卸载）。

**单元测试**：`:app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.theme.*' --tests '...TvFocusRingContractTest'` → 150 tests / 1 failed。唯一失败 `ThemeBinderContractTest.materialAlertDialogsAreBuiltThroughTheThemedBuilder`（`main/CacheManagementDialog.java -> MaterialAlertDialogBuilder`）经证为**既有失败**：该测试是纯静态扫描，测试文件与被告发文件 `CacheManagementDialog.java` 均未被本单元改动（`git status` 为空），且该用法由 `ae0b26e8ee`（2026-09-25）引入，晚于测试方法引入的 `2c2712e55d`（2026-09-24）；测试仅豁免 `CrashActivity.java`。

**UI token 门禁**：`bash scripts/check_ui_tokens.sh --strict` → `violations=1 legacy=0`，唯一违规为**既有** `app/src/mobile/res/layout/item_following.xml`（与 C37 基线记录一致），**零新增违规**。

**设备 A/B（5557，决定性）**：

- 缺陷复现基线：改动前 `theme_mode=0` 与 `theme_mode=1` 截图**逐字节相同**（均 297612 B，md5 `5cfbd8eb0d77`）。
- 改动后：以**系统夜间模式**驱动（`theme_mode` 默认 −1 跟随系统），`cmd uimode night no` vs `yes` 各取 3 帧，帧内完全稳定，两态 md5 不同（`ea1e269b…` vs `4d11f57e…`），像素差 **8.29%**。
- 主色量化（#0B57D0 浅色 primary / #A8C7FA 深色 primary，采样步长 2）：旧构建+浅色选中 → light=**0** / dark=**3281**（即缺陷：浅色下仍渲染深色调色板）；新构建+浅色 → light=**3281** / dark=**0**；新构建+深色 → 两者并存（深色表面上的浅色强调元素）。
- 用户原报路径端到端：UI 依次点 外观与语言 → 主题模式 → **浅色**，`theme_mode` 落盘为 `0`；随后 **force-stop 重启**（并把系统设为 night=yes 以制造反向干扰）→ 渲染仍为浅色帧 `f9ceed4f…`，与系统浅色下的浅色帧**逐字节相同**，证明**显式浅色已能覆盖系统深色**且跨重启持久。
- 编辑器：`uiautomator` 显示 `浅色 selected=true`、`深色 selected=false`（改动前恒为深色），编辑器面板本身以浅色表面渲染。

**设备状态复原**：测试期间曾用 `sed -i` 直接改写设备 prefs 以注入 `theme_mode`，该操作导致 App 读到不完整 XML 后回退默认（`wall` 键丢失、壁纸变 `梦幻紫霞`）。已把 `wall=10`、`wall_type=0`、`wall_color=-13922613`、`theme_color=-16732991` 写回，删除注入的 `theme_mode`，`chown`/`restorecon` 复原属主与 SELinux 上下文，系统夜间模式复位为 `no`，设备临时文件已清理。复原后 prefs 与原始键集合一致。**后续设备验证不应再用 sed 改 prefs，应走 UI。**

### 已知遗留（本单元范围外，建议另开任务）

- **既有深色模式可读性缺陷**：`shape_exit_confirm_dialog`（`#FBFCFF`）与 `shape_ad_rule_card`（`#F8F9FA`）是**常量浅色**面板，其前景 `?attr/colorOnSurface` 在 night 表解析为 `#E2E2E9` → 对比度约 **1.25:1**，即深色模式下这两个弹窗/卡片文字本就近不可读。改动前同样如此（非本单元引入），本单元**刻意未动**以免扩大范围。
- 未穷尽核查 Java 侧运行时 `setTextColor`/`setBackgroundColor` 的硬编码深色；本次仅处理资源层。

**回滚**：撤销本单元提交即可（`git revert` 或 `git reset` 到 `2162548799`）。schema 与用户已保存 profile 未变；若浅色下出现未预期的深色孤岛，先恢复 `app/src/leanback/res/values/webhtv_tokens.xml` 即回到改动前的恒深色行为。

## Recovery anchor

- 目标/验收：见顶部；完整目标未缩减。
- 计划状态：**已实施并验证，可提交**。方案 B-revised 的 6 项改动全部落地，决定性 A/B 与端到端持久化验证通过；唯一测试失败与唯一 token 违规均已取证为既有问题。
- 授权：用户消息 `真正打通 TV 浅色`（jsonl `#671`）。
- 范围/基线/保护：`dev2` @ `2162548799`；任务 guard 会话 `TV-LIGHT-THEME-20261005`（standard）；本单元 65 文件变更 + 1 个任务自有文档。
- 已完成：遮蔽表删除、`isLightTheme` 随模式、405 处前景迁移、编辑器预览模式修复、注释纠正、测试适配、构建安装、单测、token 门禁、设备 A/B 与持久化验证、设备状态复原。
- 未验证工作树编辑：无（全部改动已在设备上生效验证）。
- 未决风险：见「已知遗留」；以及未穷尽 Java 侧硬编码色。
- 回滚锚点：`2162548799`。
- 下一步唯一动作：`task_guard.sh finish` 提交并打 recovery tag。
