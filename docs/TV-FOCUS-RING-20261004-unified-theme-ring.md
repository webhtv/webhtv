# TV-FOCUS-RING：电视版焦点高亮统一为「主题色边框环」

- 任务 ID：`tv-focus-ring-unified`
- 日期：2026-10-04
- 车道：standard（TV 视觉契约 + 主题系统交叉）
- 来源：用户两张实机截图（关于 WebHomeTV 弹窗、追更页）+ 明确要求

## 1. 用户原始要求

> 像图片中这种本身就自带主题色的图标，焦点移动到图标上基本上看不出来，建议在焦点周围再加一圈高亮框。
> 请完善统一电视版焦点高亮效果，统一采用边框高亮色彩的这种方案，并受主题色彩设置控制。

拆成三条可验收要求：

| 编号 | 要求 | 验收方式 |
| --- | --- | --- |
| R1 | 自带主题色填充的控件，获得焦点时**必须出现边框环**，不能只靠填充色变化 | 静态契约测试 + 实机截图 |
| R2 | 电视版焦点高亮**统一为同一种机制/同一宽度**，不再出现"有的只有填充、有的白环、有的黄环" | 静态契约测试 |
| R3 | 焦点环颜色**受主题色板设置控制**，用户可以改 | 主题 token 链路断言（`tv_item_focus_ring` → `webhtv_color_focus`） |

两张截图中用户圈出的目标：关于弹窗的 `检查更新 / 加速源 / 我已知悉` 三个按钮 + 齿轮图标；追更页顶栏 `检查更新/全部已读/显示全部/导入订阅` 与卡片内 `继续看/检查/标记已读/提醒开/换源/取消追更`。

## 2. 根因（硬证据）

### 2.1 焦点态只有填充变化，且填充色与常态**字节相同**

`app/src/main/res/color/dialog_primary_button_bg.xml`

```xml
<item android:color="@color/webhtv_color_primary" android:state_focused="true" />
<item android:color="@color/webhtv_color_primary" />          <!-- 与 focused 完全相同 -->
```

`app/src/main/res/color/following_button_primary_bg.xml`

```xml
<item android:state_focused="true" android:color="@color/following_button_focus" />  <!-- = webhtv_color_focus -->
<item android:color="@color/following_accent" />                                     <!-- = webhtv_color_primary -->
```

`colors.xml` 里 `following_accent = @color/webhtv_color_primary`、`following_button_focus = @color/webhtv_color_focus`，
而 `ThemeTokens` 冻结色板中 **light/dark 两套 `colorFocus == colorPrimary`**（`#0B57D0` / `#A8C7FA`，
leanback 编译值 `#A8C7FA`）。因此这两条 selector 的 focused 与 default 解析结果**完全相同** → 视觉零差异。
这正是截图里 `继续看`（`?attr/colorPrimary` 填充）和 `我已知悉`（`dialog_primary_button_bg`）看不出焦点位置的原因。

### 2.2 主题色填充的按钮完全没有边框通道

`app/src/main/res/layout/dialog_about.xml` 的 `checkUpdate / githubProxy`：

```xml
app:backgroundTint="?attr/colorPrimary"   <!-- 填充即主题色 -->
app:cornerRadius="8dp"                    <!-- 没有任何 strokeColor / strokeWidth -->
```

`confirm` 用 `@color/dialog_primary_button_bg`，`updateSettings`（齿轮）用
`android:background="@drawable/about_primary_icon_button"`，其中 focused 填充 **硬编码 `#0B57D0`**，
既不随主题走，也没有边框环。

`app/src/main/res/layout/item_following.xml` 与 `app/src/leanback/res/layout/activity_following.xml`
的 action 按钮同样只有 `app:backgroundTint`，没有描边通道。

### 2.3 焦点环 token 本身不受主题控制

`app/src/main/res/values/colors.xml:46`

```xml
<color name="tv_item_focus_ring">#FFD166</color>   <!-- 写死黄色，主题编辑器改不动 -->
```

`?attr/tvFocusRing`（`attrs.xml` 声明、两个 flavor 的 `Theme.Base` 绑定）取值就是它。
`docs/webhtv-unified-visual-design-system-20260920.md:414` 早已把 `focus` 定义为
"TV 焦点环/焦点容器"，但代码没有接线到 `webhtv_color_focus`（FOCUS 槽，`ThemeRole.isUserSlot()==true`）。

## 3. 最佳实践研究

| 证据等级 | 来源 | 支持的结论 | WebHTV 适用性 | 决策影响 |
| --- | --- | --- | --- | --- |
| A（平台官方） | Android TV *Focus system*，<https://developer.android.com/design/ui/tv/guides/styles/focus-system>，2026-10-04 读取 | "Focus indicators are visual devices that emphasize focused elements"；焦点必须始终明显可见 | 直接适用：本仓库正反例都在 TV | 采纳"必须可见"为验收底线 |
| A（平台官方） | Android *Optimizing Navigation for TV*：**"use uniform highlight scheme across your application"** | 一个应用必须使用统一高亮方案 | 直接适用，正是 R2 | 采纳"统一机制 + 统一宽度" |
| A（平台官方） | 同上，推荐用 Drawable State List Resources 实现 focus/selected 高亮 | 状态驱动、按状态切换视觉 | 直接适用 | 采纳 state list（`app:strokeColor` / `<stroke>`） |
| A（W3C 标准） | WCAG 2.2 SC 1.4.11 Non-text Contrast，<https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast> | 指示"是否聚焦"的视觉信息需与相邻色 ≥3:1 | 适用（本仓库文档已自设 ≥3:1 门槛） | 边框环对相邻填充必须 ≥3:1 |
| A（W3C 标准） | WCAG 2.2 SC 2.4.13 Focus Appearance，<https://www.w3.org/WAI/WCAG22/Understanding/focus-appearance.html> | 焦点指示至少相当于 2 CSS px 周长的面积，且焦点/非焦点像素 ≥3:1 | 适用 | 3dp 环宽满足面积要求；**纯色相填充变化被视为不达标** |
| B（成熟实现） | Material 3 *States* / Compose `RippleConfiguration.Focus.InsetRing`，<https://m3.material.io/foundations/interaction/states/applying-states> | 聚焦用"环"（ring）而非仅换填充，是 M3 的既定做法 | 适用（本项目已用 Material 1.14.0） | 采纳 ring 方案 |

结论：官方与标准都要求"统一 + 可见 + 高对比"。**填充色 == 焦点色**这类实现同时违反 2.4.13 与本仓库自设门槛，
必须改为边框环。无可用的"不做改动"选项。

## 4. 现有实现与调用链

| 位置 | 现状 |
| --- | --- |
| `app/src/main/res/values/attrs.xml` | 声明 `tvFocusRing` / `tvCurrentRing` / `tvNormalStroke` |
| `app/src/leanback/res/values/styles.xml`、`app/src/mobile/res/values/styles.xml` | `Theme.Base` 绑定三者到 `@color/tv_item_*` |
| `app/src/main/res/values/colors.xml` | `tv_item_focus_ring = #FFD166`（不随主题） |
| `theme/ThemeRole.java` | `FOCUS` 是用户可编辑槽（`isUserSlot()`），`colorOf()` → `tokens.colorFocus()` |
| `theme/ThemeController.java` | `colorResourceOf(FOCUS) = R.color.webhtv_color_focus`；`bindTheme/bindDialog` |
| `theme/ThemeBinder.java` | 只改写"精确等于基线角色色"的颜色；`bindButton` 已支持 `strokeColor` 通道 |
| `theme/ThemeResolver.java` | 由用户 seed 重新生成 on-色并做对比度校验 |

已有先例可复用：`app/src/main/res/color/dialog_outlined_button_stroke.xml` 就是
"stateful color list + `app:strokeColor`"，并在 `adapter_custom_csp.xml` 中用于 `MaterialButton`。

## 5. 方案比较与决策

| 方案 | 一致性 | 可见性保证 | 主题控制 | 回归风险 | 工作量 |
| --- | --- | --- | --- | --- | --- |
| A. 不改动 | — | 不保证（现状即缺陷） | 不满足 R3 | 0 | 0 |
| B. 全树运行时统一挂环（`ThemeBinder` 遍历 focusable 逐帧设 foreground） | 最高 | 高 | 中 | **高**：会覆盖既有 foreground（`selectableItemBackground`、`selector_tmdb_media_focus`），全站视觉需逐屏回归 | 大 |
| C. **状态化边框环 + 主题 token（采纳）** | 高（同一机制、同一宽度、同一语义规则） | 满足（环色取"填充的配对 on-色"，色板对比度门保证 ≥4.5:1） | 满足（全部走 `ThemeRole`） | 低（只新增描边通道，不动既有填充/文字链路） | 小 |

采纳 **C**。理由：C 是"最小且足够"的改动——只补上缺失的描边通道并把环色接线到主题角色，
不触碰任何既有填充、文字色、布局与测量；同时对每个填充家族都给出有对比度保证的环色。

### 统一规范（R1/R2/R3 的落地定义）

1. **机制**：焦点态一律由 **边框环** 表达（`MaterialButton` 走 `app:strokeColor`+`app:strokeWidth`，
   普通 `shape` 走 `<stroke>`）。填充色变化可以保留，但不得作为唯一焦点线索。
2. **宽度**：唯一定义 `@dimen/webhtv_focus_ring_width`。**取值以实机验收为准**：2026-10-10
   实机复核后定为 `1.5dp`（§12），不要再按「视频层更粗」的印象调粗——已在首页卡片上被用户判为「太粗」。
2b. **圆角**：环作为卡片 `foreground` 时，声明圆角必须取 `宿主表面圆角 - w/2`（再留最多 0.5dp 的
   像素取整余量），详见 §12.2。自成一个控件的背景环（`shape_item_focused` 等）无此问题。
3. **颜色**：环色 = **该元素填充色的配对 on-色**（由主题色板生成，天然满足对比度门）：

| 焦点态填充 | 环色 |
| --- | --- |
| `webhtv_color_primary` / `webhtv_color_focus` | `@color/webhtv_color_on_primary` |
| `webhtv_color_secondary_container` | `@color/webhtv_color_on_secondary_container` |
| `webhtv_color_error` | `@color/webhtv_color_on_error` |
| `webhtv_color_error_container` | `@color/webhtv_color_on_error_container` |
| 透明 / 表面（图标按钮） | `?attr/tvFocusRing`（= FOCUS 槽） |

4. **主题控制**：`tv_item_focus_ring` 由 `#FFD166` 改为 `@color/webhtv_color_focus`，
   于是 `?attr/tvFocusRing` 的全部既有消费者自动变成"主题设置可控"。

### 非目标（明确不做，附理由）

- 播放器视频层焦点形状（`shape_video_focused`、`shape_subtitle_focused`、`shape_vod_focused`、
  `shape_live_focused`、`shape_keyboard_focused`、`selector_control_sheet_button` 等）保持白色不变。
  理由：它们叠在视频画面上而非应用表面，仓库已用
  `ThemeControllerContractTest` / `NativeEnhancedPlaybackStyleFocusTest` 固化"播放器焦点与通用焦点
  角色隔离"的契约；本次报告不涉及，改动会引入播放器可读性回归。
- 不改 `ThemeBinder` 的遍历/改写规则（不扩大运行时可写颜色面）。

## 6. 最小实施步骤

1. `app/src/main/res/values/colors.xml`：`tv_item_focus_ring` → `@color/webhtv_color_focus`。
2. `app/src/main/res/values/webhtv_dimens.xml`：新增 `webhtv_focus_ring_width`（首版 3dp，
   2026-10-10 实机验收后改为 1.5dp，见 §12）。
3. 新增 `app/src/main/res/color/focus_ring_primary.xml`、`focus_ring_secondary.xml`、
   `focus_ring_error.xml`、`focus_ring_error_container.xml`（focused/pressed → 配对 on-色，其余 transparent）。
4. `app/src/main/res/drawable/about_primary_icon_button.xml`：focused/pressed 改为
   `?attr/colorPrimary` 填充 + 焦点环宽度 token 的 `?attr/colorOnPrimary` 环（去掉写死 `#0B57D0`）。
5. `app/src/main/res/layout/dialog_about.xml`：`checkUpdate`/`githubProxy`/`confirm` 增加描边通道。
6. `app/src/main/res/layout/item_following.xml`：7 个 action 按钮增加描边通道（TV 实际使用的 item 布局）。
7. `app/src/leanback/res/layout/activity_following.xml`：4 个顶栏按钮增加描边通道。
8. 更新 `NativeEnhancedPlaybackStyleFocusTest` 中写死 `#FFD166` 的断言为新的主题接线。
9. 新增 `TvFocusRingContractTest` 固化 R1/R2/R3。
10. 更新 `docs/webhtv-unified-visual-design-system-20260920.md` 的焦点规范条目。

## 7. 验证计划（风险比例最小）

- 便宜且决定性：`./gradlew :app:testLeanbackDebugUnitTest --tests '*Focus*' --tests '*FollowingUi*'`（静态契约）。
- 编译门：`scripts/build_arm64_debug_install.sh`（覆盖安装，不清数据），在 dev1 模拟器 `192.168.50.3:5555`。
- 实机：关于弹窗 用 D-pad 依次聚焦 3 个按钮 + 齿轮，确认环可见；追更页顶栏与卡片按钮确认环可见。
- 主题控制：切换主题色板（改动 FOCUS 槽）后确认环色跟随变化。

## 7.1 实机验收记录与缺陷修正（2026-10-04）

首次实机截图已确认 R1 成立（3dp 环随焦点出现/消失），但像素级对比度计算暴露出本方案自身的
**两处配对错误**——环色必须配对“焦点态**实际**填充色”，而不是控件常态填充色：

| 控件 | 焦点态填充 | 首版环色 | 对比度 | 修正后环色 | 对比度 |
| --- | --- | --- | --- | --- | --- |
| `readAll` / `filter` / `alistImport` / 卡片 `read` | FOCUS `#A8C7FA` | `on_secondary_container` `#E2E2E6` | **1.33:1 ❌** | `on_primary` `#062E6F` | 6.09:1 ✅ |
| `delete`（`colorErrorContainer` 填充） | `#93000A` | `on_error` `#690005` | **1.40:1 ❌** | `on_error_container` `#FFDAD6` | 6.41:1 ✅ |

根因：`following_button_primary_bg` / `following_button_secondary_bg` 在 `state_focused` 时都把填充换成
`following_button_focus`（= FOCUS 色 = primary），所以这两个家族的环必须按 primary 配对；
`?attr/colorErrorContainer` 则必须配 `on_error_container` 而不是 `on_error`。

修正：`colors.xml` 用 `focus_ring_on_error_container` 取代 `focus_ring_on_error`；
`activity_following.xml` 的 readAll/filter/alistImport 与 `item_following.xml` 的 read 改引用
`@color/focus_ring_primary`；`focus_ring_error.xml` 改用 `focus_ring_on_error_container`。
并在 `TvFocusRingContractTest` 新增 `everyRingColourKeepsNonTextContrastAgainstItsFocusFill`，
对 light / night / leanback 三套生效 token 逐对计算 WCAG 对比度，把“环色必须与焦点填充 ≥3:1”
固化成回归测试（而非依赖人眼）。

### 实机证据（leanback，dev1 `192.168.50.3:5555`）

| 项 | 结果 |
| --- | --- |
| 焦点环出现/消失跟随焦点 | ✅ 聚焦时按钮外侧出现 6px(=3dp) 环带，失焦后消失 |
| `检查更新 check` | 环 `(28,64,123)` vs 填充 `(174,202,248)` = **6.09:1** |
| `全部已读 readAll`（修正后） | 环 `(28,64,123)` vs 填充 `(174,202,248)` = **6.09:1**（修正前 1.30:1） |
| `取消追更 delete`（修正后） | 环 `(252,219,215)` vs 填充 `(155,23,32)` = **6.41:1**（修正前 1.40:1） |
| 契约测试 | `TvFocusRingContractTest` 7/7、`NativeEnhancedPlaybackStyleFocusTest` 14/14、`FollowingUiSourceTest` 15/15，共 36 项全绿 |

## 8. 回滚

全部为资源与测试文件的新增/取值替换，无 SQL、无协议、无持久化格式变更。
回滚锚点：`git revert <本任务提交>` 或 `git reset --hard <提交前 HEAD>`；
无需要回滚的运行时数据。

---

## 9. 追加：应用表面焦点环统一（2026-10-09）

### 9.1 用户原始要求

> TV端上面两行按钮是 蓝色边框，下面的是 白色边框，边框粗细也貌似不一样，请统一风格并受到主题色彩控制

两张实机截图（`/tmp/orca-paste-1791555196409-*.png`、`/tmp/orca-paste-1791555204528-*.png`）：
同一电视首页上，`搜索/历史` 两行功能按钮是**蓝色**边框，`最近观看` 内容卡片是**白色**边框，
且两边粗细不同。

### 9.2 根因（像素级硬证据）

截图逐像素采样（`960x1280`，物理 1920x1080）：

| 控件 | 实测环色 | 实测厚度 |
| --- | --- | --- |
| `搜索` 按钮（`shape_item_focused`） | `(13,77,201)` ≈ `#0B57D0`（= day primary） | ≈2px 游程 |
| `最近观看` 卡片（`shape_vod_focused`） | `(243,242,248)` ≈ 白 | ≈4px 游程（含光晕） |

代码侧根因：应用表面的焦点环分裂成三族，各写各的取值。

| 家族 | 原取值 | 问题 |
| --- | --- | --- |
| `shape_item_focused` / `shape_item_round_focused` | `1.5dp ?attr/colorPrimary` | 蓝；`colorPrimary` 在 TV 上同时驱动约 100 个布局的聚焦文字色，不能兼作焦点环语义 |
| `shape_vod_focused` / `shape_vod_oval_focused` / `shape_keyboard_focused` / `shape_search_hot_word_focused` / `shape_chip_*_focused` | `1.5dp`~`2dp @color/white` | 白；完全不随主题走 |
| `shape_config_history_item_focused` / `shape_site_item_*` / `shape_group_button_focused` | `2dp` | 宽度又不同 |

`@color/white` 是**与调色板无关**的常量，因此用户改主题的「焦点色」槽时这些边框纹丝不动；
宽度也从 1.5dp 到 3dp 散布，正是「粗细也貌似不一样」。

### 9.3 判据：宿主决定环色来源

统一不能只看「都改成 `?attr/tvFocusRing`」——**宿主背景决定环色必须来自哪里**，
否则会引入新的「焦点看不见」缺陷。逐对计算 WCAG 对比度后确定边界：

| 宿主 | 背景（两张表相同？） | `?attr/tvFocusRing`（day 深蓝） | 白色 |
| --- | --- | --- | --- |
| 调色板表面 / 对话框面板 | 跟随调色板 | 5.2–7.9:1 ✅ | 1.2–1.3:1（day 浅底）❌ |
| 固定深色玻璃（`shape_dialog_glass_panel` 等） | 恒定深色 | **1.91:1** ❌ | 12.20:1 ✅ |
| 固定浅色面板（`shape_exit_confirm_dialog`、`shape_ad_stats_content`） | 恒定浅色 | **1.6–2.8:1**（night 浅蓝）❌ | 1.03–1.29:1 ❌ |
| 视频画面 / 透明控制条 | 不可预测 | 无保证 ❌ | 有保证 ✅ |

因此本次统一拆成两类：

- **统一到主题环**（宿主是调色板表面）：`shape_item_focused`、`shape_item_round_focused`、
  `shape_item_selected`、`shape_vod_focused`、`shape_vod_oval_focused`、`shape_keyboard_focused`、
  `shape_search_hot_word_focused` → `@dimen/webhtv_focus_ring_width` + `?attr/tvFocusRing`；
  自带主题色填充的三族（`shape_config_history_item_focused`、`shape_site_item_focused`、
  `shape_site_item_selected`、`shape_group_button_focused`）→ 环色取焦点态填充的配对角色
  （`?attr/colorOnPrimary` / `?attr/colorPrimary`），沿用 §「统一规范」的配对规则。
- **只统一宽度**（宿主固定明暗或视频层，环色保持与调色板无关）：`shape_chip_focused`、
  `shape_chip_round_focused`、`shape_live_focused`、`shape_video_focused`、
  `shape_subtitle_focused`、`shape_subtitle_pressed`、`selector_ad_stats_item`、
  `selector_search_scope_item`、`selector_exit_confirm_primary`、`selector_exit_confirm_secondary`
  → 宽度一律 `@dimen/webhtv_focus_ring_width`。

`selector_exit_confirm_*` 与 `selector_ad_stats_item` 保持字面量环色还有一条独立理由：
它们由 `TvFixedDarkSurfaceContrastTest` 的
`theFixedPanelsAreStillPaletteIndependentAndDark` 固化——面板固定浅色 + 按钮文字恒为白，
改走调色板会让夜间表的白字对比度掉到 1.72:1。

### 9.4 变更清单

| 文件 | 变更 |
| --- | --- |
| `shape_item_focused.xml` / `shape_item_round_focused.xml` / `shape_item_selected.xml` | `1.5dp ?attr/colorPrimary` → `@dimen/webhtv_focus_ring_width` + `?attr/tvFocusRing` |
| `shape_vod_focused.xml` / `shape_vod_oval_focused.xml` | `1.5dp/2dp @color/white` → token + `?attr/tvFocusRing` |
| `shape_keyboard_focused.xml` / `shape_search_hot_word_focused.xml` | 同上 |
| `shape_config_history_item_focused.xml` / `shape_site_item_focused.xml` / `shape_site_item_selected.xml` / `shape_group_button_focused.xml` | `2dp` → token（环色配对规则不变） |
| `shape_chip_focused.xml` / `shape_chip_round_focused.xml` / `shape_live_focused.xml` / `shape_video_focused.xml` / `shape_subtitle_focused.xml` / `shape_subtitle_pressed.xml` | `1.5dp` → token（环色保持白色，附宿主对比度理由） |
| `selector_ad_stats_item.xml` / `selector_search_scope_item.xml` | `2dp` → token |
| `selector_exit_confirm_primary.xml` / `selector_exit_confirm_secondary.xml` | `2dp` → token |
| `TvFocusRingContractTest.java` | 新增 `appSurfaceFocusRingsShareOneWidthTokenAndAThemeColour`、`homeFunctionButtonsAndContentCardsShareTheSameRingSpec` |
| `TvAppSurfaceFocusRingDeviceTest.java`（新增） | 像素级运行时证据（见 §9.5） |
| `InterfaceEntryInteractionTest.java` | 把写死 `2dp` 的断言改为断言唯一宽度 token |

### 9.5 验证证据

**像素级运行时证据**（`TvAppSurfaceFocusRingDeviceTest`，Robolectric `GraphicsMode.NATIVE`
真实栅格化后逐像素测量，6/6 通过）：

| 断言 | 结果 |
| --- | --- |
| 首页功能按钮与内容卡片的实测环**厚度**相同 | ✅ |
| 首页功能按钮与内容卡片的实测环**颜色**相同 | ✅ |
| 6 个应用表面 selector 的实测厚度 == `@dimen/webhtv_focus_ring_width` | ✅ |
| 实测环色 == 主题 FOCUS 槽（`webhtv_color_focus`） | ✅ |
| day 表与 night 表渲染出**不同**环色（真正的主题可控） | ✅ |
| 环完全不透明（焦点可见） | ✅ |
| 度量方法能区分细环与粗环（变异检验，防空断言） | ✅ |

**变异检验（证明测试真的能抓到用户报告的缺陷）**：把 `shape_vod_focused.xml` 改回
`1.5dp @color/white`（即修复前的状态）后重跑，**3 项断言转红**：
`appSurfaceRingsFollowTheThemePaletteAcrossDayAndNight`、
`homeFunctionButtonsAndContentCardsRenderTheSameRing`、
`appSurfaceRingsRenderInTheThemeFocusColour`。恢复后重新全绿。

**回归门**：

| 检查 | 结果 |
| --- | --- |
| `:app:testLeanbackArm64_v8aDebugUnitTest` | 4154 tests / 0 failures |
| `:app:testMobileArm64_v8aDebugUnitTest` | 4980 tests / 0 failures |
| `scripts/check_ui_tokens.sh` | `hex_drawables=0`、`violations=1`（= 基线，仅既存 `item_following.xml`） |
| `:app:assembleLeanbackArm64_v8aDebug` | BUILD SUCCESSFUL |

**未完成的实机复核**：dev1 模拟器（`192.168.50.3:5555`）在本轮全程离线
（`ping` 100% 丢包、ARP FAILED、5555/5557/5559/5561 全部 closed），因此本轮未能做
D-pad 逐项截图复核。同一批 drawable 的上一轮（§7.1）已在同一设备上做过实机验收，
且本轮的像素级栅格化证据与变异检验已覆盖用户报告的三个具体现象（蓝/白、粗细、主题跟随）。

## 10. 回滚（本轮追加）

全部为资源取值替换与测试新增，无 SQL、无协议、无持久化格式变更。
回滚锚点：`git revert <本任务提交>` 或 `git reset --hard <提交前 HEAD>`。

---

## 11. 追加：剩余焦点环的宽度收口与选集弹窗环色修正（2026-10-10）

§9 统一了 21 个文件后，全库扫描仍发现 8 个 leanback 焦点态描边没有引用唯一宽度 token。
本节把它们收口，并顺手修掉扫描时暴露的一处「焦点完全看不见」缺陷。

### 11.1 剩余 8 个文件的分类与处置

| 文件 | 原宽度 | 现状/处置 | 理由 |
| --- | --- | --- | --- |
| `selector_control_sheet_button.xml` | `2dp`（焦点/按下/当前三态） | → token | 播放器控制面板按钮；宿主是固定深色玻璃面板，环色保持白 |
| `selector_episode_dialog_item.xml` | `2dp` | → token + 环色改白 | 选集弹窗剧集行；同时修掉 §11.2 的缺陷 |
| `selector_episode_dialog_page.xml` | `3dp`（字面量） | → token + 环色改白 | 选集弹窗分页条；值虽相同但不是唯一来源 |
| `selector_danmaku_result_item.xml` | `2dp` | → token | 无消费者的旧文件，避免以后接回时带回旧规范 |
| `selector_danmaku_search_action.xml` | `2dp` | → token | 同上 |
| `shape_video_item_focused.xml` | `1.5dp` | → token | 无消费者的旧文件（已被 `selector_video_item` 取代） |
| `selector_video_item.xml` | `3dp`（字面量） | **保留** | 它是**视频层**焦点环规范的**样板文件**，其他文件注释都指向它；`NativeEnhancedPlaybackStyleFocusTest` 把它当规范基准逐字断言。**视频层自成一档（焦点 3dp / 当前 2dp / 常态 1dp），与应用表面 token（现为 1.5dp）分属不同语义**，两者不要求同值（2026-10-10 §12 修正） |
| `shape_audio_action_icon_focused.xml` | `1dp` | **保留** | 它不是容器焦点环，而是播放页音频按钮上带 `inset=3dp` 的**图标内描边环**（40dp 图标 / 17dp 圆角），角色与尺寸均不同，套 3dp 会把图标糊成一团 |

### 11.2 顺带修掉的缺陷：选集弹窗焦点不可见

统一时逐对计算对比度，发现选集弹窗（`dialog_episode` / `adapter_episode_dialog` /
`adapter_episode_page`，宿主面板 `shape_episode_dialog_panel` 为固定深色 `#DD111820`）
的环色取自填充色的近似色，**环与自身填充几乎同色**：

| 状态 | 填充 | 原环色 | 原对比度 | 改白后 |
| --- | --- | --- | --- | --- |
| `episode_dialog_item` focused | `#2196F3` | `#1976D2` | **1.47:1** | **3.12:1** |
| `episode_dialog_item` selected | `#CC2AA46B` | `#2AA46B` | **1.00:1**（完全不可见） | **3.17:1** |
| `episode_dialog_page` focused | `#552196F3` | `#0077FF` | **1.32:1** | **3.12:1** |
| `episode_dialog_page` selected | `#332196F3` | `#2196F3` | **1.00:1**（完全不可见） | **3.12:1** |

环外侧贴 `#111820` 面板另有 17.87:1。改白后四项均达到 WCAG 2.2 SC 1.4.11 非文本
对比度 3:1 门槛，同时与仓库里所有其它「固定深色宿主」的焦点环规则一致
（`shape_chip_*`、`selector_search_scope_item`、`selector_exit_confirm_*` 都是白环）。

另外修正了选择器分支顺序：原稿把 `state_selected` 放在 `state_focused` 之前，
所以「正在播放 + 获得焦点」的剧集行会命中 selected 分支而**吞掉焦点环**。
现在 `state_focused` 在最前，焦点环永远优先；`state_selected` 仍保留绿色填充
作为「正在播放」的持久标记（与 `tv_item_current_ring` 的绿色语义一致）并配 2dp 白环，
与焦点态的 3dp 形成既有的「焦点 3dp / 当前 2dp」分档。

### 11.3 新增全库不变量

§9 的宽度断言是**白名单式**的（只检查名单内文件），新增文件不会被覆盖。
本轮新增 `TvFocusRingContractTest.noLeanbackFocusRingHardcodesItsWidth`：
直接扫描整个 `app/src/leanback/res/drawable`，对**每个**焦点态描边断言宽度必须等于
`@dimen/webhtv_focus_ring_width`。只有上面表格里那两个文件豁免，豁免理由写在该测试的
Javadoc 里，豁免列表本身即契约。

变异检验：把 `selector_episode_dialog_page.xml` 的焦点环宽度改回 `2dp` 后重跑，
`noLeanbackFocusRingHardcodesItsWidth` 与
`appSurfaceFocusRingsShareOneWidthTokenAndAThemeColour` **两项转红**；恢复后全绿。
测试还断言至少扫描到 15 处焦点态描边，避免以后目录结构调整导致它变成空断言。

### 11.4 本轮验证证据

| 检查 | 结果 |
| --- | --- |
| 全库焦点态描边宽度扫描（脚本，独立于测试） | 仅剩 2 个已豁免文件 |
| `:app:testLeanbackArm64_v8aDebugUnitTest` | 4155 tests / 0 failures |
| `:app:testMobileArm64_v8aDebugUnitTest` | 4980 tests / 0 failures |
| `TvFocusRingContractTest` | 11/11 |
| `TvAppSurfaceFocusRingDeviceTest`（像素级栅格化） | 6/6 |
| `scripts/check_ui_tokens.sh` | `hex_drawables=0`、`violations=1`（= 基线） |
| 变异检验 | 改回 2dp 后 2 项转红，恢复后全绿 |

**仍未完成的实机复核**：dev1 模拟器（`192.168.50.3:5555`）在整轮工作中持续离线
（`ping` 100% 丢包、ARP FAILED、四个端口全部 closed），因此未能做 D-pad 逐项截图复核。
本轮的替代证据是 Robolectric `GraphicsMode.NATIVE` 真实栅格化后的逐像素厚度/颜色度量
（含 day/night 跟随与变异检验），覆盖用户报告的三个具体现象（蓝/白、粗细、主题跟随）。

## 12. 追加：应用表面焦点环宽度回到 1.5dp 与卡片环圆角对齐（2026-10-10 实机验收）

### 12.1 用户原始要求

§9/§11 上线后（dev1 实机 `192.168.50.3:5555`，三处环色已统一为蓝色 `?attr/tvFocusRing`），
用户对首页实机截图提出两点：

> 现在颜色是统一了但是默认的边框太粗了，一起的白色的那种宽度更好，再就是边框在卡片上的
> 圆角部分匹配不完美感觉是圆角比卡片小导致卡片的圆角还露出一节了

即：(1) 统一后的 3dp 环比原来「白色那一族」的宽度粗，要求回到 1.5dp；
(2) 卡片焦点环在圆角处与卡片自身圆角不匹配，卡片圆角露出环外侧一段。

### 12.2 根因（像素级硬证据）

宽度：`@dimen/webhtv_focus_ring_width` 首版取 3dp，来自 `selector_video_item`（**视频层**）的
3dp 字面量；§9 把它套到**应用表面**后，首页全部环从原来的 1.5dp 变为 3dp。dev1 实机
（1920x1080 / `wm density`=280，应用内有效缩放 ≈2.35px/dp）量到环带 7.06px = 3dp，
而修复前同一位置是 3.5px ≈ 1.5dp。

圆角：`shape_vod_focused` 是卡片的 **foreground**，卡片的可见表面另有自己的 8dp 圆角
（`@style/Vod.Grid` 图片上圆角 / `shape_vod_name` 标题块下圆角 / `shape_vod_list` 列表四角）。
描边型 `<shape>`（`GradientDrawable`）把描边中心线画在「内缩 w/2、但圆角半径仍取声明值」的
路径上（Robolectric 栅格化扫描确认），于是：

| 量 | 取值 |
| --- | --- |
| 环外边界圆角半径 | 声明值 + w/2 = 8 + 1.5 = **9.5dp** |
| 卡片表面圆角半径 | **8dp**（`Vod.Grid` / `shape_vod_name` / `shape_vod_list`） |
| 实机量到的环外弧 | ≈21.2px ÷ 2.35 ≈ **9dp** |
| 实机量到的卡片弧 | ≈13.5px ÷ 2.35 ≈ **5.7dp**（同一张 m1.png 截图） |

环外弧比卡片弧大 ⇒ 沿角落对角线卡片弧更靠外，露出 1–2px 月牙（实机 RGB 复核：
`y=238` 行蓝色止于 x=396，而 x=397 是米色 `221,207,194`，卡片平边在 396.5px；
`y=244` 行 x=402 同样是米色 `200,186,186`）。这正是用户所说「卡片的圆角还露出一节」。

### 12.3 修法

1. `webhtv_focus_ring_width`：3dp → **1.5dp**（= §9 统一前「白色那一族」的实机宽度，
   也是用户认可的那种宽度）。视频层/固定明暗宿主仍是 `selector_video_item` 的 3dp/2dp/1dp
   自成一套档位，与本次改动无关。
2. `shape_vod_focused` 声明圆角：8dp → **7dp**。理论值 = 宿主圆角 − w/2 = 7.25dp，但描边宽度
   按整数像素向上取整（1.5dp @ density 3 = 4.5px → 5px，实际 w/2 = 2.5px），写满 7.25dp 时
   在部分密度下仍会超出宿主弧 0.25–0.5px；7dp 在 density 1.5/2/3/4 下的实测溢出为
   0（density 2 下仅剩半像素平局：5px，已在测试里设每角 2px 的亚像素容差）。
   `selector_vod` 的 6 个宿主可见表面都是 8dp，所以单一环仍能同时对齐全部宿主。

### 12.4 新增不变量

| 测试 | 断言 |
| --- | --- |
| `TvAppSurfaceFocusRingDeviceTest.cardRingOuterBoundaryContainsEveryCardSurfacePixel` | 真实 `selector_vod` 焦点分支与 8dp 卡片表面栅格化后，**卡片 0 像素**落在环外边界之外（主档密度 xxhdpi 严格 0；其余档位允许每角 2px 亚像素平局）；覆盖 hdpi/xhdpi/xxhdpi/xxxhdpi 四档密度 |
| `TvAppSurfaceFocusRingDeviceTest.theCornerMeasurementCatchesTheReportedCardCornerBleed` | 变异检验：把用户报告时的旧取值（声明 8dp + 3dp 环宽）搭回来，上面的度量必须报出溢出 |
| `TvFocusRingContractTest.cardRingCornerRadiusIsTheHostRadiusMinusHalfTheRingWidth` | 卡片环声明圆角 ≤ `宿主圆角 − w/2`，且不低于该理论值 0.5dp；6 个宿主表面圆角必须相同 |
| `TvFocusRingContractTest.thereIsExactlyOneFocusRingWidthInTheTvUi` | token 只声明一次（不再钉死具体 dp 值，避免每次宽度调整都要改断言） |

栅格化扫描（Robolectric `GraphicsMode.NATIVE`，240×240，卡片溢出像素数）：

| 声明圆角 | hdpi(1.5) | xhdpi(2) | xxhdpi(3) | xxxhdpi(4) |
| --- | --- | --- | --- | --- |
| 8dp（旧） | 25 | 52 | 114 | 181 |
| 7.25dp（纯理论值） | 0 | 2 | 3 | 2 |
| **7dp（采纳）** | **0** | **0** | **0** | **0** |
| 8dp + 3dp（用户报告的旧组合） | 62 | 91 | 205 | 379 |

### 12.5 本轮验证证据

| 检查 | 结果 |
| --- | --- |
| `TvAppSurfaceFocusRingDeviceTest` | 8/8 |
| `TvFocusRingContractTest` | 12/12 |
| `:app:testLeanbackArm64_v8aDebugUnitTest` | 4158 tests / 0 failures |
| `:app:testMobileArm64_v8aDebugUnitTest` | 4980 tests / 0 failures |
| `scripts/check_ui_tokens.sh` | `violations=1`（= 基线）、`hex_drawables=0`、`contrast failures=0` |
| 全库焦点态描边宽度扫描 | 仍只剩已豁免的 `selector_video_item` 与 `shape_audio_action_icon_focused` |

实机复核（dev1 `192.168.50.3:5555`，1920x1080 / `wm density`=280；用
`scripts/build_arm64_debug_install.sh --flavor leanback` **覆盖安装**同签名 Debug 包，未卸载）：

| 量 | 修复前 | 修复后 |
| --- | --- | --- |
| 首页功能按钮环带宽度（精确 #0B57D0 像素游程） | 6px | **3px** |
| 首页内容卡片环带宽度（同一度量） | 6px | **3px** |
| 两族环色 | 已统一 | 已统一（实测像素 `(11,87,208)` = 主题 FOCUS 槽） |
| 卡片圆角外侧「无法用 环色↔壁纸 混合解释」的像素数 | **25** | **0** |
| 卡片环总像素（同屏同状态） | 11447 | 5789 |

最后一行就是用户说的「卡片的圆角还露出一节」的量化定义：修复前那里是 `(222,209,195)`
`(221,207,194)` 这类米色（卡片表面色）像素，修复后同一位置的像素全部是环色→壁纸的
抗锯齿过渡。逐像素放大图（16×）已人工比对：修复前蓝色弧外侧有一条米色月牙，修复后弧外侧
直接过渡到壁纸。

已知取舍：WCAG 2.2 SC 2.4.13 的「至少 2 CSS px 周长」要求需要密度 ≥1.5（1.5dp × 1.5 = 2.25px）；
电视端实际密度 1.5–3.5，dev1 上实测环带 3px，满足门槛。若以后出现 1x 密度的电视机型，
需要重新评估而不是把 token 调粗（先看实机可读性）。

### 12.6 回滚

只改一个 dimen 取值与一个 drawable 的 `corners` 属性：把 `webhtv_focus_ring_width` 改回 `3dp`、
`shape_vod_focused` 的 `android:radius` 改回 `8dp` 即回到 §11 的状态（本轮两个新测试会同时转红，
正好是判据）。两条注释性 prose 与本文档属于记录，不需要回滚。

## 13. 追加：详情页/播放页同类环圆角对齐 + 卡片集数名可读性（2026-10-10 第二轮）

### 13.1 用户原始要求

> 播放页，详情页估计都有类似问题请一起修复吧。再就是卡片下方的集数名称一栏字体颜色不对看不清，
> 手机版是改成和剧名一个颜色的电视版你看怎么处理比较好

### 13.2 根因（与 §12.2 同一几何规则，只是宿主不同）

§12 只修了首页卡片的 `selector_vod`。同一个「环是 foreground、宿主表面自带圆角」的组合在详情页/
播放页还有 5 处，全部仍是「声明值 = 宿主圆角」的旧写法，于是环外边界圆角比宿主大 w/2，
宿主圆角露出环外：

| 环 | 宿主 | 宿主圆角 | 旧声明 | 新声明 |
| --- | --- | --- | --- | --- |
| `selector_episode_card`（详情页选集卡） | `adapter_episode_card` CardView | 8dp | 8dp | **6dp** |
| `selector_tmdb_media_focus`（剧照/相关视频） | `adapter_tmdb_video`/`photo` MaterialCardView | 8dp | 8dp | **6dp** |
| `selector_tmdb_cast_focus`（演员卡） | `adapter_tmdb_cast` MaterialCardView | 12dp | 12dp | **9.5dp** |
| `shape_episode_photo_focused`（剧照详情） | `dialog_episode_detail#stillCard` CardView | 8dp | 8dp | **6dp** |
| `shape_video_focused`（播放页画面框） | `activity_video#video` FrameLayout（**直角**） | 0dp | 8dp | **0dp** |

实测（Robolectric 栅格化真实宿主 + 真实 drawable，四档密度 hdpi/xhdpi/xxhdpi/xxxhdpi 的溢出像素）：

| 声明值 | 8dp 宿主 | 12dp 宿主 | 直角宿主 |
| --- | --- | --- | --- |
| 旧值 8dp / 12dp | 26 / 34 / 61 / 123 | 4 / 0 / 0 / 0 → 10.5dp 时 9 | 4 / 11 / 20 / 30 |
| **新值 6dp / 9.5dp / 0dp** | **0 / 2 / 0 / 2** | **0 / 0 / 0 / 0** | **0 / 0 / 0 / 0** |

（8dp 宿主在 xhdpi/xxxhdpi 的 2px 是半径与描边取整的半像素平局，属既有容差口径；
旧值同密度下是 34/123，相差一个数量级。）

### 13.3 cache 管理弹窗按钮：不能再用 foreground 描边环

`selector_cache_button_focus` 是 `MaterialButton` 的 foreground 环，声明 28dp。实测确认
**几何上无法对齐**：M3 按钮圆角来自 `ShapeAppearance.M3.Comp.Button.Small.Container.Shape.Round`
→ `ShapeAppearance.M3.Sys.Shape.Corner.Full` → `cornerSize = 50%`（52dp 高按钮 = 26dp 胶囊），
而 foreground 环画在整个 view 上、按钮填充却被 4dp inset（实测填充 bbox y 12..143 / 156，
环 bbox 0..155）——环的盒位与圆角都和按钮轮廓不同，任何固定 dp 都只能近似。

因此改为 Material 自带的描边通道（`app:strokeColor` + `app:strokeWidth`，与 `dialog_about` /
`item_following` 同一做法）：Material 用**同一个** `ShapeAppearanceModel` 同时画填充与描边，
几何天然一致。环色按既有规则取「焦点态实际填充色的配对 on-色」：

- tonal（secondaryContainer 填充）→ `@color/focus_ring_secondary`（浅色表 13.18:1 / 夜间 7.24:1）
- text/outlined（无容器填充）→ `@color/focus_ring_primary`（浅色表对 secondaryContainer 4.91:1）

`selector_cache_button_focus.xml` 已删除；`CacheManagementDialog` 的两个 Java 工厂方法
（`moduleButton`/`limitButton`）改用 `applyFocusRing(...)` 走 `setStrokeColor`/`setStrokeWidth`。
两个 flavor 的 `dialog_cache_management.xml` 共 18 处 `android:foreground` 改为 stroke 属性。

### 13.4 集数名可读性

`app/src/leanback/res/layout/adapter_vod.xml` 的 `@id/remark`（卡片下方「第01集. 海边散步」那一行）
此前用 `?attr/colorOnSurfaceVariant`（浅色表 #44474F），画在 `shape_vod_name` 的深色半透明条带上：

| 行 | 取值 | 实测对比度 |
| --- | --- | --- |
| `@id/name`（剧名，未变） | `?attr/webhtvColorOnWallpaper` = #FFFFFF | 6.89:1 |
| `@id/remark`（集数名，修复前） | `?attr/colorOnSurfaceVariant` = #44474F | **1.35:1** ← 用户报告的「看不清」 |
| `@id/remark`（修复后） | `?attr/webhtvColorOnWallpaper` = #FFFFFF | **7.26:1** |

手机版同一行本来就是 `?attr/webhtvColorOnWallpaper`，TV 对齐该取值后两版语义一致。
`adapter_vod_rect` / `adapter_search` 的 remark 本来就是这个取值，无差异。

### 13.5 本轮验证证据

| 检查 | 结果 |
| --- | --- |
| `TvFocusRingContractTest` | 14/14（新增 `foregroundRingsDeclareTheirHostCornerMinusHalfTheRingWidth`、`leanbackCardEpisodeLineUsesTheSameReadableColourAsItsTitle`） |
| `TvAppSurfaceFocusRingDeviceTest` | 9/9（新增 `realDetailAndPlayerHostsContainTheirFocusRingCorner`：inflate 真实宿主 + 真实环，四档密度逐像素判定） |
| `:app:testLeanbackArm64_v8aDebugUnitTest` | 4161 tests / 0 failures |
| `:app:testMobileArm64_v8aDebugUnitTest` | 4980 tests / 0 failures |
| `scripts/check_ui_tokens.sh` | `violations=1`（= 基线）、`hex_drawables=0`、`contrast failures=0` |
| `NativeEnhancedPlaybackStyleFocusTest` | 同步更新：选集卡/演员卡不再要求写死宿主圆角，改为要求「小于宿主圆角且走实测安全值」 |

实机复核（dev1，覆盖安装同签名 Debug 包）：

| 页面 | 修复前 | 修复后 |
| --- | --- | --- |
| 首页卡片集数名 | 近白像素 0、近 #44474F 像素 12–62（深灰字） | 全白，实测对比度 **7.26:1**（与剧名同色） |
| 详情页选集卡圆角 | 环外边界外有宿主像素（月牙） | 「无法用 环色↔背景 混合解释」的环外像素 = **0** |
| 播放页画面框 | 白环 8dp 圆角切进黑底直角 | 白环收成方角（0dp），黑角不再被切 |

### 13.6 回滚

- 环圆角：把 5 个 drawable 的 `android:radius` 改回宿主圆角（8dp/12dp/8dp）即回到旧状态。
- cache 按钮：恢复 `selector_cache_button_focus.xml` 与两个布局的 `android:foreground`、
  `CacheManagementDialog` 的 `setForeground`。
- 集数名：把 `adapter_vod.xml` 的 remark 改回 `?attr/colorOnSurfaceVariant`。

## 14. 追加：播放页环宽度统一 + 环色统一并受主题控制（2026-10-10 第三轮）

### 14.1 用户原始要求

> 播放页按钮的边框太粗了，按钮和播放器选中后的边框颜色也不统一，请统一且都受到主题色彩控制

### 14.2 根因（dev1 实机实测，非推测）

播放页同时存在**两族环**，各自写各自的宽度与颜色：

| 族 | 消费方 | 旧宽度 | 旧环色 |
| --- | --- | --- | --- |
| `selector_chip` → `shape_chip_focused/round_focused` | `view_control_vod_action.xml` 26 个 + `view_control_live_action.xml` 14 个 `style=Control` 按钮（线路诊断/硬解能力/1x/原始/LUT/字幕/音轨/视轨/片头/片尾/弹幕/广告/定时/循环…） | 1.5dp（token） | **写死 `@color/white`** |
| `selector_video_item` | 播放页 11 个文本按钮（简介/短显/搜索/换源/追更/重匹配/选集标题/倒序/视图/文件名）+ 4 个 adapter | **写死 3dp** | `?attr/tvFocusRing` |
| `selector_control_sheet_button` | `dialog_control` 的 ControlSheetButton | 1.5dp（token） | 写死半透明白 |

「当前播放/当前生效」态更乱：`shape_chip_activated` / `shape_chip_round_activated` **完全没有描边**，
`selector_video_item` 的 activated 用写死绿 `#2CC56F`。

实机量到（`pp_osd2.png`，2.35px/dp）：焦点按钮环带 **6px = 3dp**（`selector_video_item`），
而同一屏的芯片族是 3px = 1.5dp —— 用户说的「边框太粗」就是 3dp 那一族。

### 14.3 关键约束：播放页不能直接用调色板原色

播放页控制条**直接叠在视频上**（`view_control_vod.xml` 背景 `@color/transparent`，
`backdropMask` 的黑色渐变在播放态被视频遮住），另一类宿主是控制面板的固定深靛玻璃
（`shape_dialog_control_glass_panel` 三档 `#2F315E/#282955/#303463`）。实测：

| 候选环色 | 对纯白视频 | 对最暗玻璃 `#303463` |
| --- | --- | --- |
| 白 `#FFFFFF`（旧 A/C 族） | **1.00:1** | 11.69:1 |
| FOCUS 浅色表 `#0B57D0` | 6.39:1 | **1.83:1** |
| FOCUS 深色表 `#A8C7FA` | **1.72:1** | 6.80:1 |
| 写死绿 `#2CC56F` | **2.25:1** | 5.19:1 |

**没有任何调色板原色能同时在两类极端背景上达到 3:1** —— 白环在亮视频上消失、
浅色表深蓝在玻璃上消失。这就是仓库既有契约把播放页列为「固定明暗宿主、环色与调色板无关」
的原因，也是不能简单换成 `?attr/tvFocusRing` 的原因。

### 14.4 方案：播放页专属主题派生环色（两类极端背景可读性夹取）

新增 `ThemeTokens.colorPlayerFocusRing` / `colorPlayerCurrentRing`，由 `ThemeResolver`
用仓库**已有的** `readableAccent(color, minimum, backdrops)` 派生：
保持用户 FOCUS / `playerCurrent` 槽的**色相与彩度**、只走明度，直到在
`PLAYER_RING_BACKDROPS = {纯白视频, 三档玻璃}` 上全部达到 `MIN_PLAYER_RING_CONTRAST = 3.0`
（WCAG 2.2 SC 1.4.11 非文本门槛，与仓库 `outline`/`focus` 的 `ensureContrast(..., 3.0)` 同口径；
不用 4.5 是因为那是正文文本档位，会把浅色表的蓝压得过暗）。

派生结果（编译期默认值，写入 `webhtv_tokens.xml`）：

| 槽位 | light | dark |
| --- | --- | --- |
| `colorPlayerFocusRing`（← FOCUS 槽） | `#447BF5`（FOCUS `#0B57D0` 夹取） | `#7695C5`（FOCUS `#A8C7FA` 夹取） |
| `colorPlayerCurrentRing`（← playerCurrent 槽） | `#00A95A` | `#00A95A` |

主题属性 `?attr/tvPlayerRing` / `?attr/tvPlayerCurrentRing`（`attrs.xml` 声明，
两个 flavor 的 `Theme.Base` 绑定），与 `?attr/tvFocusRing` 同族、同样受主题表控制。

**分层规则**（本轮固化，与 §9.3「宿主决定环色来源」一致）：

- **视频层**（叠在视频/固定玻璃上）→ `tvPlayerRing` / `tvPlayerCurrentRing`
- **应用表面**（叠在调色板表面上，含与详情页/手机版共用的 `selector_episode_card`、
  `selector_tmdb_*`）→ `tvFocusRing` / `tvCurrentRing`

### 14.5 新增用户可编辑槽位：播放中环色

「当前播放/当前生效」环色此前是写死绿，本轮提升为**独立用户槽位**：

- `ThemeProfile.SlotSet.playerCurrent`（JSON 字段 `playerCurrent`，与 `focus` 同级）
- `ThemeEditor.Slot.PLAYER_CURRENT`（`isColor()` 覆盖）
- `ThemeProfileValidator.validateSlots` 校验
- `ThemePreviewView` 新增「播放器」分组（标签「播放中环色」/“Playing ring”）
- `ThemePresets` 各内置色板同步写入（默认沿用成功色语义绿）

主题编辑器改该槽时，`ThemeResolver` 会重新夹取，因此用户选的任何颜色都会落到
两类极端背景上可读的档位。

### 14.6 变更清单

| 文件 | 变更 |
| --- | --- |
| `ThemeTokens.java` | 新增 `colorPlayerFocusRing` / `colorPlayerCurrentRing` 两个 record 分量 + 两个字面量构造 |
| `ThemeResolver.java` | `PLAYER_RING_BACKDROPS` / `MIN_PLAYER_RING_CONTRAST`；`readableAccent` 增加显式门槛重载；`applyProfile` 派生两个环色；`derive` 透传 |
| `attrs.xml` / `webhtv_attrs.xml` | 声明 `tvPlayerRing` / `tvPlayerCurrentRing` 与 `webhtvColorPlayer*Ring` |
| `{leanback,mobile}/values/styles.xml` | `Theme.Base` 绑定两个新属性 |
| `webhtv_tokens.xml` / `values-night/` | 新增两个 token（编译期默认 = 夹取后的可读值） |
| `colors.xml` | `tv_player_focus_ring` / `tv_player_current_ring` 代理色 |
| `strings.xml` ×3 | `theme_editor_slot_player_current` / `theme_editor_group_player` |
| `ThemeProfile/ThemeEditor/ThemeProfileValidator/ThemePreviewView/ThemePresets` | `playerCurrent` 槽位全链路 |
| `selector_video_item.xml` | 焦点 3dp→token、`tvFocusRing`→`tvPlayerRing`；当前态 2dp→token、`tvCurrentRing`→`tvPlayerCurrentRing` |
| `shape_chip_focused.xml` / `shape_chip_round_focused.xml` | 写死白 → `?attr/tvPlayerRing` |
| `shape_chip_activated.xml` / `shape_chip_round_activated.xml` | **新增**当前态描边（token 宽 + `?attr/tvPlayerCurrentRing`） |
| `selector_control_sheet_button.xml` | 三态写死半透明白 → `?attr/tvPlayerRing` / `?attr/tvPlayerCurrentRing`；常态 → `?attr/tvNormalStroke` |
| `shape_video_focused/subtitle_focused/subtitle_pressed/live_focused` | 写死白 → `?attr/tvPlayerRing` |

### 14.7 本轮验证证据

| 检查 | 结果 |
| --- | --- |
| `TvFocusRingContractTest` | 16/16（新增 `playerFocusRingsShareOneWidthAndAThemeControlledColour`、`playerRingColoursClearNonTextContrastOnVideoAndGlass`） |
| `NativeEnhancedPlaybackStyleFocusTest` | 同步更新为「统一宽度 + 视频层主题色」 |
| `ThemeContractTest` / `ThemeEditor*` / `ThemePresets*` | 全绿（色板资源数 49→51、槽位新增 `playerCurrent`） |
| `:app:testLeanbackArm64_v8aDebugUnitTest` | 4163 tests / 0 failures |
| `:app:testMobileArm64_v8aDebugUnitTest` | 4980 tests / 0 failures |
| `scripts/check_ui_tokens.sh` | `violations=1`（= 基线）、`hex_drawables=0`、`contrast failures=0` |

实机复核（dev1，覆盖安装同签名 Debug 包）：

| 量 | 修复前 | 修复后 |
| --- | --- | --- |
| 焦点按钮环带宽度 | **6px = 3dp** | **3px = 1.5dp** |
| 当前态按钮环带宽度 | 2dp（或芯片族完全无描边） | **3px = 1.5dp** |
| 焦点环色 | 写死白 `#FFFFFF` | **`#447BF5`**（= 派生值，逐字节一致） |
| 当前态环色 | 写死绿 `#2CC56F`（对亮视频 2.25:1） | **`#00A95A`**（= 派生值，逐字节一致） |

焦点环与当前态环在同一屏上现在**同宽同源**，且都随主题 FOCUS / 播放中环色槽变化。

### 14.8 回滚

- 环：把 9 个 drawable 的 `android:color` 改回 `@color/white`（或 `?attr/tvFocusRing`）、
  `selector_video_item` 的两个宽度改回 `3dp`/`2dp`。
- 槽位：移除 `SlotSet.playerCurrent` 与 `ThemeEditor.Slot.PLAYER_CURRENT` 及编辑器分组。
- 派生：移除 `ThemeResolver` 的两个环色派生与 `ThemeTokens` 的两个分量。

## 15. 追加：修复对话框主题属性缺失回归 + 收口代码挂环的硬编码宽度（2026-10-10 第四轮）

### 15.1 用户原始要求

> 现在选中播放器看不出选中的效果，好像是黑色的边框，不应该是统一的选中边框颜色吗？
> 还有个性推荐等卡片的边框粗细没有改小没有统一

### 15.2 缺陷一：对话框主题解析不到 `tv*` 属性（上一轮引入的回归）

§14 把 `selector_control_sheet_button`（播放器控制面板按钮）的焦点环从**写死半透明白**
改成了 `?attr/tvPlayerRing` / `?attr/tvPlayerCurrentRing`。但这两个属性当时只在
`Theme.Base`（= `Theme.App`）里绑定，而：

| 对话框 | 主题来源 | 是否继承 `Theme.WebHTV` |
| --- | --- | --- |
| 播放器控制面板 `ControlDialog`（BottomSheet） | `bottomSheetDialogTheme` = Material 默认 `ThemeOverlay.Material3.BottomSheetDialog` | **否** |
| Alert 对话框 | `Theme.WebHTV.Dialog`，父主题 `Theme.Material3.DayNight.Dialog.Alert` | **否** |

属性解析不到时，`?attr/xxx` **静默退化为黑色/透明，且不崩溃**。Robolectric 实测（修复前）：

```
Theme_App (Activity)          tvPlayerRing -> #FF447BF5   ✓
Theme.WebHTV.Dialog           tvPlayerRing -> UNRESOLVED   ← 黑框来源
裸 ThemeOverlay.Material3.BottomSheetDialog  -> UNRESOLVED  ← 黑框来源
```

复现的渲染结果（`dialog_control#player`）：修复前 `activated` 分支**完全无描边**，
修复后 `focus=#FF447BF5 / selected=#FF00A95A / activated=#FF00A95A`。

**修法**：在 `Theme.WebHTV`（基底）、`Theme.WebHTV.Dialog`、`ThemeOverlay.WebHTV.Dialog`
三处都绑定全部五项 `tv*` 属性；`selector_control_sheet_button` 补上缺失的
`state_activated` 分支（与 `state_selected` 同色同宽——部分入口用
`ArrayAdapter.setActivated` 表达「当前生效」，缺该分支就完全看不到选中环）。

**新增防回归契约** `ThemeContractTest.everyReferencedTvFocusAttrIsAssignedByATheme`：
扫描全部 `?attr/tv*` 引用，断言每个都被至少一个主题 style 赋值，且基底主题
`Theme.WebHTV` 自带全部五项。这类回归不崩溃、只有人眼能发现，必须由测试钉住。

### 15.3 缺陷二：代码挂环的控件漏掉了宽度统一

前几轮统一的是 **drawable 层**的环宽，但有一批控件是**代码挂环**
（直接 `setStrokeWidth` / `GradientDrawable.setStroke`），全部漏掉：

| 位置 | 旧值 | 说明 |
| --- | --- | --- |
| `TmdbRecommendationPresenter` | `FOCUS_WIDTH_DP = 3` | 个性推荐卡（用户报告的直接对象） |
| `TmdbCardFocusHelper` | `FOCUS_STROKE_DP = 3` | 详情页多张卡共用的助手 |
| `TmdbEpisodeAdapter` | `FOCUS_STROKE_DP = 3` | 原生增强选集卡 |
| `TmdbVideoAdapter` | `focused ? 2 : 1` | 相关视频卡 |
| `InlineEpisodeAdapter` | `active \|\| focused ? 2 : 1` | 内嵌选集芯片 |
| `TmdbDetailActivity` | `FOCUS_STROKE_DP = 3`（**11 处**） | 详情页面板/按钮/芯片 |

**修法**：全部改为从 `@dimen/webhtv_focus_ring_width` 读像素
（`focusRingWidthPx()` 助手 / `getDimensionPixelSize`），`TmdbCardFocusHelper.foregroundBorder`
的参数语义从 dp 改为 px（避免又一次隐式 dp 写死）。`TmdbRecommendationPresenter` 的环色
也改为 `ThemeController.focusRingColor()`（原来直接取 `R.color.tv_item_focus_ring` 常量，
绕过了主题覆写通道）。

**新增防回归契约** `TvFocusRingContractTest.noJavaCodeHardcodesAFocusRingWidth`：
扫描 9 个 Java 源，找出所有「按焦点分支设描边宽度」的语句，断言其**真分支**不含
dp 字面量或 `*_DP` 常量（实测扫到 12 处）；配套
`noJavaCodeHardcodesAFocusRingColour` 断言环色走 `ThemeController.focusRingColor`。
该测试在实施中真的抓出一处漏改（`TmdbDetailActivity.applyInlineEpisodeModeButtonState`）。

### 15.4 本轮验证证据

| 检查 | 结果 |
| --- | --- |
| `ThemeContractTest` | 全绿（新增 `everyReferencedTvFocusAttrIsAssignedByATheme`） |
| `TvFocusRingContractTest` | 18/18（新增 `noJavaCodeHardcodesAFocusRingWidth`、`noJavaCodeHardcodesAFocusRingColour`） |
| `:app:testLeanbackArm64_v8aDebugUnitTest` | 4166 tests / 0 failures |
| `:app:testMobileArm64_v8aDebugUnitTest` | 4981 tests / 0 failures |
| `scripts/check_ui_tokens.sh` | `violations=1`（= 基线）、`hex_drawables=0`、`contrast failures=0` |

实机 + Robolectric 复核：

| 量 | 修复前 | 修复后 |
| --- | --- | --- |
| `Theme.WebHTV.Dialog` / `bottomSheetDialogTheme` 的 `tvPlayerRing` | UNRESOLVED（黑框） | `#FF447BF5` |
| `dialog_control#player` activated 态 | 无描边 | `#FF00A95A` |
| 选集项（`selector_video_item`）焦点环 | 3dp 写死色 | **3px `#447BF5`**（dev1 实机实测 5738px） |
| 个性推荐卡焦点环宽 | 3dp 硬编码 | `@dimen/webhtv_focus_ring_width` |

### 15.5 回滚

- 属性：从三个主题 style 移除 `tv*` 五项绑定，并删除 `state_activated` 分支。
- 宽度：把 6 个 Java 位置的 `focusRingWidthPx()` 换回 `ResUtil.dp2px(3)` 常量。
- 契约测试与本文档属记录，随代码一并回滚即可。

## 16. 追加：全屏播放画面**不要**焦点环（2026-10-10 第五轮，含一次被纠正的设计）

### 16.1 用户要求与纠正过程

用户先报告（附大窗形态播放截图）：「这里没有选中效果，或者说看不出来」。
据此实施了一版「全屏时在画面内侧画内缩环」的改动（commit `0209015d6`）。

随后用户纠正：

> 全屏播放器内部不需要选中效果呀
> 控制栏需要但是怎么整个播放界面套了一个？

即：**需要焦点环的是控制栏按钮，不是整个播放画面**。`0209015d6` 把环套在整个播放界面上，
属于过度设计，已用 `git revert` 完整回退。

### 16.2 结论（本轮的最终设计决定）

| 区域 | 是否需要焦点环 | 说明 |
| --- | --- | --- |
| 全屏播放画面（`@id/video` / `playerPanel` 铺满时） | **不需要** | 全屏是「沉浸观看」状态，画面本身不是可选控件；加环反而干扰观感 |
| 控制栏 / OSD 按钮（`selector_video_item`、`selector_chip`、`selector_control_sheet_button`） | **需要** | 这些才是遥控焦点真正停留的可选控件，环必须存在且已统一（见 §14） |
| 内嵌（非全屏）播放面板 | **需要** | 此时面板是页面里的可选卡片，环表达「焦点在播放区」，且与卡片圆角对齐（见 §13） |

因此全屏入口保持移除环的既有行为：

- `VideoActivity.enterFullscreen()` → `mBinding.video.setForeground(null)`
- `TmdbDetailActivity.updatePlayerPanelFocus()` → `inlineFullscreen || inlinePiPLayout` 分支清零描边后返回

这两处**不是缺陷**，而是刻意的产品行为；后续不要再把「全屏无环」当成 bug 去加环。

### 16.3 教训（供后续排查参考）

排查「看不出焦点」时，必须先确认**焦点当前停在哪个控件**（`uiautomator dump` 看
`focused="true"` 的节点），再决定该控件是否**应当**有环。
`@id/video` 在全屏时确实 `focused="true"`，但它是画面容器而非可操作控件——
「有焦点」不等于「需要焦点环」。

### 16.4 回滚记录

- `0209015d6`（全屏内缩环）已被完整 revert，两个入口恢复为移除环。
- 同批次新增的两条契约测试（`fullscreenPlayerPathsKeepAnInsetFocusRing`、
  `fullscreenPlayerPanelDrawsAnInsetThemeFocusRing`）随之移除，避免它们反过来要求错误行为。
- 控制栏按钮的环（§14 的统一宽度 + 主题派生色）**未受影响**。

## 17. 追加：默认配色向上游对齐（2026-10-10 第六轮）

### 17.1 用户原始要求

> 最好是吧默认主题配色靠近上游 https://github.com/webhtv/webhtv/tree/Silent1566
> 比如：默认选中边框是白色，播放器加载中效果是白色等，手机和电视版都注意一下
>
> 后续补充：电视版应该默认深色更好吧？大白天看的是时候浅色可能看不清，大晚上的时候浅色可能太刺眼？

### 17.2 上游基线（clone `Silent1566` 分支实测）

| 项目 | 上游 | 本仓库（改前） |
| --- | --- | --- |
| TV 主题 | `Theme.Material3.Dark.NoActionBar`（**强制深色**，`windowBackground=#000000`） | DayNight（TV 默认解析**浅色表**） |
| TV `colorPrimary` | `#FFFFFF` | 主题蓝 `#0B57D0` |
| 焦点环色 | **`@color/white`**（32 处 drawable 写死） | `?attr/tvFocusRing`（浅色 `#0B57D0` / 夜间 `#A8C7FA`） |
| 焦点环宽度 | 1.5dp | 1.5dp（§12 已对齐）✓ |
| 播放器加载中 | **`@color/white`** | `?attr/colorPrimary` |
| 加载中网速文字 | `@color/white` | `?attr/webhtvColorOnWallpaper`（=白）✓ |

上游之所以能用白环，是因为它 **TV 强制深色**（纯黑底，白环 21:1）。本仓库 TV 是 DayNight
且默认落到浅色表，白环对 `#F8FAFD` 只有 **1.05:1**——直接用白环会让焦点彻底消失，
并且会被 `ThemeContrast` 的 `focus/surface ≥3:1` 门拒绝（启动即 fallback）。

### 17.3 决策：TV 产品默认深色（与上游一致）

用户判断与上游一致，且同时解开了白环的可读性阻塞。**TV 的“跟随系统”不再等于“跟随一个
通常恒为浅色的系统默认”**，而是产品默认深色；用户仍可在外观设置里显式选浅色/深色。
mobile 保持“跟随系统”不变。

理由：电视多在暗环境观看，大面积浅色亮底夜间刺眼；且深色底上白环（17–18:1）才成立。

### 17.4 变更清单

| 文件 | 变更 |
| --- | --- |
| `ThemeController.applyNightModeToApp()` | `default` 分支按 flavour 分流：leanback → `MODE_NIGHT_YES`，mobile → `MODE_NIGHT_FOLLOW_SYSTEM`（显式浅色/深色仍优先） |
| `values-night/webhtv_tokens.xml` | `webhtv_color_focus` → **`#F5F7FF`**（近白，见 17.5） |
| `ThemeTokens.dark()` | `colorFocus` 字面量同步为 `#F5F7FF` |
| `{leanback,mobile}/res/layout/view_progress.xml` | `indicatorColor` → **`@color/white`**（与上游一致；两处都叠在视频上） |
| `color/following_button_primary_text.xml` | 写死白 → `@color/webhtv_color_on_primary`（见 17.5） |
| `color/following_button_secondary_text.xml` | focused/pressed 写死白 → `@color/webhtv_color_on_primary` |

### 17.5 顺带修掉的两个连带缺陷（都是本轮改动暴露/引入的）

**（a）追更页按钮「白底白字」**：这两个按钮的 `backgroundTint` 在 focused/pressed 时取
`following_button_focus`（= `webhtv_color_focus`），而文本此前写死 `@android:color/white`：

| 表 | 焦点填充 | 白文本对比度 |
| --- | --- | --- |
| 浅色 | `#0B57D0` | 6.39:1 ✓（所以此前没被发现） |
| 深色 | `#A8C7FA` | **1.72:1** ✗（已经坏了，但 TV 默认浅色时不显形） |

TV 默认深色后必然显形。改为与填充配对的 `webhtv_color_on_primary`：浅色 `#FFFFFF`
（6.39:1）、深色 `#062E6F`（7.50:1）。

**（b）深色焦点环不能用纯白**：本仓库有 `ThemeBinder` 运行时改写通道，它按「颜色 → 语义角色」
**精确匹配**；而 `webhtv_on_wallpaper` 两张表恒为 `#FFFFFF`。若焦点也取纯白，
`ThemeColorIndex` 会把纯白映射到 `FOCUS` 角色，用户切换/自定义主题时**壁纸上的白色文字会被
改写成焦点色**（`ThemeBaseWiringTest.wallpaperForegroundIsLightAndNotBinderRewritable` 实测抓出：
`replacementFor(#FFFFFF, light)` 由 `null` 变成 `#0B57D0`）。
故取 **`#F5F7FF`**：与纯白视觉上无法区分，对深色 surface 仍有 **17.29:1**，且不与任何 dark 角色同值。

### 17.6 新增契约测试（`ThemeControllerContractTest`）

- `tvDefaultsToDarkWhileMobileKeepsFollowingTheSystem`：follow-system 分支必须按 flavour 分流，
  且显式浅色/深色仍优先。
- `darkFocusRingIsNearWhiteButNeverPureWhite`：深色焦点必须亮（对 surface ≥10:1）且**不等于纯白**
  （防 binder 碰撞回归），并与 `ThemeTokens.dark()` 字面量同值。
- `followingButtonTextPairsWithItsFocusFill`：两个文本 selector 不得再写死白色，必须取配对 on-色，
  且两张表的 `onPrimary/focus` 配对都必须 ≥4.5:1。

### 17.7 本轮验证证据

| 检查 | 结果 |
| --- | --- |
| `ThemeControllerContractTest` | 11/11（新增 3 条） |
| `:app:testLeanbackArm64_v8aDebugUnitTest` | 4169 tests / 0 failures |
| `:app:testMobileArm64_v8aDebugUnitTest` | 4984 tests / 0 failures |
| `scripts/check_ui_tokens.sh` | `violations=1`（= 基线）、`hex_colors=0`、`contrast failures=0` |

实机复核（dev1，覆盖安装同签名 Debug 包；系统 `cmd uimode night no` 即**白天**）：

| 量 | 修复前 | 修复后 |
| --- | --- | --- |
| 白天 + 未自定义主题时的焦点环 | 浅色表 `#0B57D0`（45484 处为 0） | **深色表 `#F5F7FF` = 45484 px** |
| 浅色表焦点环像素 | 有 | **0**（确认 TV 默认已走深色表） |
| 播放器加载中指示器 | `?attr/colorPrimary` | `@color/white`（两版） |

### 17.8 回滚

- TV 默认深色：`applyNightModeToApp()` 的 `default` 分支改回 `MODE_NIGHT_FOLLOW_SYSTEM`。
- 深色焦点环：`values-night/webhtv_tokens.xml` 与 `ThemeTokens.dark()` 改回 `#A8C7FA`。
- 加载指示器：两个 `view_progress.xml` 改回 `?attr/colorPrimary`。
- 追更页文本：两个 `following_button_*_text.xml` 改回 `@android:color/white`。

## 18. 追加：播放页只留一种高亮色（宿主分层修正 + 深色表视频层环取近白）（2026-10-11）

### 18.1 用户原始要求

> 播放页 \[两张实机截图\] 存在多个不同颜色的高亮效果

截图 1（手机版详情页）：「个性推荐 · TMDB」卡片获得焦点时是**近白环**；
截图 2（电视版播放页）：「1-10」分段芯片获得焦点时是**灰蓝环**，而同一页其它元素是近白环。

### 18.2 根因（像素级硬证据）

| 环来源 | 深色表取值 | 消费者 |
| --- | --- | --- |
| `?attr/tvFocusRing`（应用表面） | `#F5F7FF` | 首页卡片、详情页选集卡、演员卡、剧照卡 |
| `?attr/tvPlayerRing`（视频层） | `#7695C5` | 控制条芯片、控制面板按钮、画面框、字幕/直播环、**`selector_video_item`** |

`selector_video_item` 同时被两类宿主使用，却整批归到了视频层：
播放页内容区（`@+id/scroll` 里的线路/清晰度/数组分段/分段/选集/快捷源芯片，以及
简介/短显/搜索/收藏按钮行、集数表头）实际画在**页面背景**上，与 `selector_episode_card`
同类；只有两处固定深色玻璃对话框（`dialog_episode_list` / `dialog_quick_search`）复用同一批
芯片布局，才是真正的玻璃宿主。

实机复核（dev1 `192.168.50.3:5555`，深色表）：

```text
「1-10」分段芯片焦点环 = #7695C5（灰蓝，与截图 2 逐像素一致）
```

### 18.3 决策（A + C 组合）

- **A：按宿主分层修正归类。** `selector_video_item` 归回「应用表面」族
  （`?attr/tvFocusRing` / `?attr/tvCurrentRing`）；两处固定玻璃对话框用
  `@style/ThemeOverlay.WebHTV.GlassFocusRings` 在**宿主子树内**把这两项重绑回视频层派生色，
  因此仍然只有一份 drawable、不需要给共享布局复制副本。
- **C：深色表的视频层环取与应用表面同值的近白。** 深色表两类宿主的底色都是深色
  （深色 surface、固定玻璃、多数视频画面），没有任何可读性理由保留第二种高亮色。
  浅色表**必须保留**派生夹取：浅色 FOCUS 深蓝 `#0B57D0` 画在固定玻璃上只有 1.83:1。
- 纯白视频（雪/白墙）从 `ThemeResolver.PLAYER_RING_BACKDROPS` 移除：把它当作约束背景，
  正是把深色表环压成 `#7695C5` 的原因。代价是亮场景视频上的近白环对比度不足，
  但该场景只在焦点落在画面框/播控条时出现，而绝大多数视频层宿主的局部底色是深玻璃。

### 18.4 变更清单

| 文件 | 变更 |
| --- | --- |
| `leanback/res/drawable/selector_video_item.xml` | 焦点/当前态环 → `?attr/tvFocusRing` / `?attr/tvCurrentRing` |
| `leanback/res/values/styles.xml` | 新增 `ThemeOverlay.WebHTV.GlassFocusRings` |
| `leanback/res/layout/dialog_episode_list.xml`、`dialog_quick_search.xml` | 根上应用该覆盖层 |
| `values-night/webhtv_tokens.xml`、`ThemeTokens.dark()` | `webhtv_color_player_focus_ring` `#7695C5` → `#F5F7FF` |
| `ThemeResolver.PLAYER_RING_BACKDROPS` | 去掉纯白视频，只保留三档固定玻璃 |
| 环色注释（`colors.xml`、`webhtv_tokens.xml`、`shape_chip_*`、`selector_control_sheet_button`、`shape_video_focused`） | 改写为新的分层与取舍说明 |

### 18.5 新增/更新的契约测试

- `TvFocusRingContractTest.playerFocusRingsShareOneWidthAndAThemeControlledColour`：
  视频层族名单移除 `selector_video_item`，新增应用表面族断言、覆盖层存在性断言、
  两处玻璃对话框必须应用覆盖层的断言，以及「芯片不得再出现视频层环色」的反向断言。
- `TvFocusRingContractTest.playerRingColoursClearNonTextContrastOnGlassBackdrops`：
  背景集合改为三档玻璃，期望值深色表为 `#F5F7FF`；保留「浅色 FOCUS 深蓝对玻璃不可读」的回归钉子。
- `TvFocusRingContractTest.playerRingColoursStayOneUnifiedHighlightInTheDarkTable`（新）：
  深色表视频层环必须**等于**应用表面焦点环、浅色表必须**不等**，且两张表都要与 frozen 色板同值。
- `NativeEnhancedPlaybackStyleFocusTest`、`VideoActivityLayoutTest`：芯片期望由视频层族改为应用表面族。

### 18.6 本轮验证证据

| 检查 | 结果 |
| --- | --- |
| `:app:testLeanbackArm64_v8aDebugUnitTest --tests TvFocusRingContractTest`、`NativeEnhancedPlaybackStyleFocusTest` | 通过 |
| `:app:testMobileArm64_v8aDebugUnitTest --tests VideoActivityLayoutTest` | 通过 |
| `:app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*"`（含 51 色 XML/色板一致性） | 通过 |
| Robolectric 一次性探针（验证用，未入库）：白天表 | 普通上下文 `?attr/tvFocusRing` = `#FF0B57D0`；玻璃对话框子树内 = `#FF447BF5`；对话框内芯片栅格化描边 = `#FF447BF5`；页面内容区芯片 = `#FF0B57D0` |
| dev1 实机（深色表，覆盖安装同签名 Debug 包） | 数组分段芯片「1-16」焦点环 `#F5F7FF`（该区域 `#7695C5` 像素数 **0**）；选集芯片「第09集」焦点环 `#F5F7FF`；当前生效分段环 `#2CC56F`；视频画面框环 `#F5F7FF` |

### 18.7 回滚

- 芯片分层：`selector_video_item.xml` 两处环属性改回 `?attr/tvPlayerRing` / `?attr/tvPlayerCurrentRing`，
  并删除两处 `android:theme="@style/ThemeOverlay.WebHTV.GlassFocusRings"` 与该 style。
- 深色表近白：`values-night/webhtv_tokens.xml` 与 `ThemeTokens.dark()` 改回 `#7695C5`，
  并把 `0xFFFFFFFF` 加回 `ThemeResolver.PLAYER_RING_BACKDROPS`。
