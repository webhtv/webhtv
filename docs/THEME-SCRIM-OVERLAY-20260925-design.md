# WebHTV `scrimOpacity` / `overlayOpacity` 接线设计研究

- 任务：`THEME-SCRIM-OVERLAY-20260925`
- 状态：**方案定稿候审**。本文只做设计与验收定义，不代表已实施；按 `AGENTS.md` 第 7 节，须经用户明确批准后才可改代码。
- 编写时间：2026-09-25 16:45 CST（Asia/Shanghai）
- 基线：`dev3@8ee61fbdb762222127886796dfd66b286fd14db5`，起始工作树干净。
- 关联文档：`docs/THEME-CUSTOMIZATION-LAYERS-20260921.md`（4.26 发现 + 4.28/4.29 收口）。
- 设备：dev3 `192.168.50.3:5559`（V1923A，API 28，浅色系统），mobile/leanback arm64 debug 覆盖安装。

## Recovery anchor

- 目标：裁定 16 槽中剩余两个死槽 `scrimOpacity` / `overlayOpacity` 的原生语义与接线方案，使编辑器滑块不再是"拖了没反应"的死控件。
- 验收标准：两槽各有明确处置（接线或明确标注不生效），且 `scrimOpacity` 接线后手机/TV 两侧可见、无回归、基线逐字节不变。
- 当前结论：**`scrimOpacity` → 弹层遮罩，建议实施；`overlayOpacity` → 语义不成立，建议暂缓并降级为 Web 只读**。
- 回滚锚点：本任务仅改文档；未来实施的回滚见第 8 节。

## 1. 问题定义

`scrimOpacity` / `overlayOpacity` 是 16 槽编辑器里的用户可见滑块，但**没有任何原生消费点**：`colorScrim` / `colorOverlayLight` 在 `theme` 包之外仅被资源 XML 引用，而不经 `ThemeBinder` 改写——因为 binder 只改写"与基线精确同色且角色已知"的颜色，而 `ThemeRole` 里**根本没有 SCRIM / OVERLAY 角色**：

```
$ grep -c 'SCRIM\|OVERLAY' app/src/main/java/com/fongmi/android/tv/theme/ThemeRole.java
0
```

因此用户在编辑器拖这两个滑块，原生界面不会有任何变化（4.26 记录：`scrimOpacity`、`overlayOpacity` 是"死槽"）。

## 2. 当前实现（文件与符号级）

### 2.1 resolver：两槽的取值语义**不同**

`app/src/main/java/com/fongmi/android/tv/theme/ThemeResolver.java:168-170,184,187`

```java
float scrimOpacity   = slots.scrimOpacity   == null ? (base.colorScrim() >>> 24) / 255f : slots.scrimOpacity;
float overlayOpacity = slots.overlayOpacity == null ? (base.colorOverlayLight() >>> 24) / 255f : slots.overlayOpacity;
...
withAlpha(base.colorScrim(), scrimOpacity), ...          // scrim = 绝对 alpha
withAlpha(base.colorOverlayLight(), overlayOpacity), ... // overlay = 绝对 alpha
```

- `scrimOpacity` 是**绝对 alpha**：基线由 `colorScrim` 的 alpha 派生。
  - `ThemeTokens.light().colorScrim() = 0x52000000` → alpha `0x52` = **0.322**（手机浅色）
  - `ThemeTokens.dark().colorScrim() = 0x8A000000` → alpha `0x8A` = **0.541**（手机深色）；leanback 同值
  - 校验区间 `MIN_SCRIM_OPACITY=0f .. MAX_SCRIM_OPACITY=0.85f`（`ThemeProfileValidator.java:14-15`），两个基线都落在区间内
- `overlayOpacity` 同为绝对 alpha，但**派生自 `colorOverlayLight`**：
  - `colorOverlayLight = 0x14FFFFFF`（白，alpha `0x14` = 0.078），手机/TV 同值
  - 校验区间 `MIN_OVERLAY_OPACITY=0.05f .. MAX_OVERLAY_OPACITY=0.60f`

> 注意语义差异：`dialogOpacity` 是**缩放**（基线 `1.0f`，4.26 已接线为 `scaleAlpha`），而这两个槽是**绝对值**（`withAlpha`）。实施时不能套用 `applyShellOpacity` 的 no-op 判定 `opacity >= 1f`。

### 2.2 binder：不支持纯色 drawable，也没有对应角色

`app/src/main/java/com/fongmi/android/tv/theme/ThemeBinder.java`

- `bindDrawable` 只处理 `InsetDrawable` → `GradientDrawable` → `MaterialShapeDrawable`（`:320-334`），**不处理 `ColorDrawable`**——而 `android:background="@color/..."` inflate 后正是 `ColorDrawable`。
- `singleColor`（`:390-399`）显示显式角色路径仍要求 `baseline != null && baseline == current`，即**即使是显式 `webhtv:` tag，也必须先精确等于该角色的基线色**。
- `isExempt`（`:401-409`）按类名子串豁免：`surface / texture / video / player / danmaku / subtitle / karaoke / wall / logo / rating / media`。`scrim` / `overlay` **不在豁免名单**里，但承载它们的 `View` 通常是无类名的 `android.view.View`，因此不会被误豁免。

## 3. 证据：`scrimOpacity` 有自然的现成落点

`webhtv_color_scrim` 在布局中的**全部**用法（`grep -rln webhtv_color_scrim app/src/*/res/layout`）：

| 文件 | 用法 | 语义 |
| --- | --- | --- |
| `app/src/main/res/layout/dialog_episode_detail.xml:8` | 根 `FrameLayout` 的 `background`，`match_parent×match_parent` | **全屏弹层遮罩** ✅ |
| `app/src/main/res/layout/dialog_tmdb_person.xml:9` | 根 `FrameLayout` 的 `background` | **全屏弹层遮罩** ✅ |
| `app/src/main/res/layout/dialog_tmdb_video_player.xml:50` | 内层 `FrameLayout` 的 `background` | **播放器弹层遮罩** ✅ |
| `app/src/mobile/res/layout/dialog_receive.xml:19` | `ShapeableImageView` 的 `background` | 海报占位底 ⚠️ |
| `app/src/{main,mobile,leanback}/res/layout/dialog_ad_block_stats.xml` 各 **3 处**（如 mobile 版 `:143`、`:339`、`:783`） | `1dp` 高 `View` / `88dp` 行容器 | **分隔线 / 行底** ❌ 非遮罩 |

- 三个全屏弹窗的遮罩语义明确（根背景 + 内容卡片在其上），但**窗口 dim 状态并不一致**，实施时必须区分：
  - `TmdbPersonDialog.java:133` → `setDimAmount(0f)`（关窗口 dim，靠视图 scrim 压暗）
  - `EpisodeDetailDialog.java:240` → `setDimAmount(0f)`（同上）
  - `TmdbVideoPlayerDialog.java:326` → `setDimAmount(0.72f)`（**保留窗口 dim**）
    ⚠️ 该弹窗同时具备"视图 scrim 背景 + 0.72 窗口 dim"，接线视图 scrim 会与窗口 dim **叠加**。因此实施时对它可以只做只读快照、或先裁定是否要降低窗口 dim 以补偿，不能直接套用另两个的处理。
- 这正与官方语义一致：Material 官方 token `mtrl_scrim_color` = **`#52000000`**，与项目浅色 `webhtv_color_scrim` **完全相同**（`material-components-android/lib/java/com/google/android/material/color/res/values/colors.xml`，2026-09-25 访问）。设计文档本就把该槽定义为"页面/Dialog 外遮罩透明度"（`docs/THEME-CUSTOMIZATION-LAYERS-20260921.md` 第 14 槽表格行，原文 `| 14 | scrimOpacity | 页面/Dialog 外遮罩透明度 | 安全范围 0.00..0.85 |`），并在 Dialog 处理清单里列入 scrim（同文原文 `- Dialog/BottomSheet：处理 surface、outline、按钮和 scrim`）。
> 注：以上引用用**原文片段**而非行号，避免主文档编辑后行号漂移；用 `grep -n '页面/Dialog 外遮罩透明度'` 可定位当前行。

**结论**：`scrimOpacity` 是**实现遗漏**，不是设计缺失。但它当前被**混用**（分隔线/占位底），所以不能对 `webhtv_color_scrim` 做全局替换——必须按视图语义限定作用域。

## 4. 证据：`overlayOpacity` 的原生语义不成立

`webhtv_color_overlay_*` 的全部用法：

| Token | 消费点 | 是否可为用户槽 |
| --- | --- | --- |
| `overlay_light`（白，alpha 0.078） | `app/src/mobile/res/layout/dialog_quick_search.xml:80` —— 一个 `1dp` 高 `View` 的 `background`，即**分隔线** | ❌ 不是"轻遮罩" |
| `overlay_dark` | `shape_tmdb_episode_overlay.xml:6`、`shape_tmdb_recommendation_info.xml:5` 的 `android:centerColor`（图片上的渐变 veil） | ❌ 4.23 已明确**冻结**（`colorOverlayDark` 不随 profile 变） |

两个问题互相冲突：

1. **语义错配**：用户槽 `overlayOpacity` 派生自 `overlay_light`，而 `overlay_light` 唯一天然用法是分隔线；真正承担"图片可读性遮罩"的是 `overlay_dark`，它被冻结、且不在用户槽内。
2. **落在豁免区**：那两个渐变位于 TMDB 图片之上，属于 binder 刻意豁免的 media/image 子树；接线会触碰既有豁免契约，并需要扩展 `GradientDrawable` 的 **centerColor** 改写（当前只改写 solid fill 与 stroke）。

可选落点全都不干净：`shape_dialog_glass_panel.xml` 是**硬编码**玻璃渐变色（`#D6282955` 等，非 token）。因此 `overlayOpacity` 目前**没有语义正确的原生落点**。

## 5. 设计方案对比

| 方案 | 内容 | 正确性 | 兼容性 | 风险/成本 | 结论 |
| --- | --- | --- | --- | --- | --- |
| **A. 保持现状** | 两槽继续是死槽 | ❌ 编辑器继续误导用户 | 零 | 低 | 拒绝 |
| **B. `scrim` 接线；`overlay` 暂缓并显式标注**（实施细节见第 11.3 节） | 把 mobile `EpisodeDetailDialog` 与 `TmdbPersonDialog` 的 `overlay` 字面量改为 `ThemeController.current().colorScrim()`；`overlay` 明确"仅 Web 快照生效" | ✅ 与官方 scrim 语义/M3 token 一致，且 4 行级改动 | ✅ 不触碰 binder、布局或 `webhtv_color_scrim` 的其他用法 | 低（原估的 binder 契约扩展经复核**不需要**） | **推荐** |
| C. `scrim` 接线 + `overlay` 改绑图片 veil 并解冻 `overlayDark` | 把 veil 强度交给 `overlayOpacity` | ⚠️ 语义可辩，但破坏 4.23 冻结契约 | ⚠️ 需触碰 media 豁免 | 高 | 暂缓 |
| D. 两槽都接入窗口级 `setDimAmount`/`setDimColor` | 用窗口 dim 取代视图遮罩 | ❌ 现有遮罩是视图不是窗口 dim；`setDimColor` 需 API 37（设备 API 28），仅 `setDimAmount` 可用且表达不了颜色 | ❌ | 高 | 拒绝 |
| E. 从编辑器移除两槽 | 回到 14 槽 | ✅ 不再误导 | ❌ 破坏已批准的 16 槽契约 | 中 | 仅作逃生选项 |

### 5.1 对方案 B 的补充设计（`scrim` 作用域）

由于 `webhtv_color_scrim` 被混用，接线必须**限定作用域**，而不是全局改写：

- 优先：给 3 个遮罩视图加显式标记（`android:tag="webhtv:scrim"`），走 `singleColor(..., explicit, ...)` 路径 → 只改这 3 处。
- 注意 `singleColor` 仍要求 `baseline == current`（`:395`），因此**基线必须精确匹配**。浅色基线 `0x52000000` 与深色/TV `0x8A000000` 是两个不同值，需由 `ThemeColorIndex` 的基线索引提供（binder 已按 baseline 建索引，机制现成）。
- 分隔线/占位底不加标记 → 保持原样，避免"分隔线随用户拖动变透明"的错误行为。
- 风险：`dialog_tmdb_video_player` 已开 `0.72f` 窗口 dim，视图 scrim 叠加会让画面过暗。建议首个实施版本**只接 `dialog_episode_detail` + `dialog_tmdb_person`** 两个纯视图遮罩弹窗，`dialog_tmdb_video_player` 待单独裁定后再接。

## 6. 建议

1. **实施**：`scrimOpacity` 接线到**运行时真正赋予遮罩色的那两处**——mobile `EpisodeDetailDialog` 与 `TmdbPersonDialog` 的 `overlay` 字面量改为 `ThemeController.current().colorScrim()`。**取代第 7 节的旧步骤**（旧步骤基于被证伪的第 3 节前提，见第 11 节）。
2. **暂缓**：`overlayOpacity` 不接线（理由不变）。
3. 若你希望"不留任何不生效控件"，替代做法是采纳方案 E（移出编辑器），但那会改动已批准的 16 槽契约，需要单独确认。

## 7. 最小实施步骤（**已被第 11 节取代，仅保留供追溯**）

> ⚠️ 以下步骤基于"XML 的 scrim 背景是活的"这一前提，该前提已被第 11 节证伪。**不要按本节实施**，请用第 6 节第 1 条 + 第 11.3 节。

1. ~~`ThemeRole` 新增 `SCRIM` 角色~~（不需要）
2. ~~`ThemeBinder.bindDrawable` 增加 `ColorDrawable` 分支~~（不需要）
3. ~~给 3 个遮罩视图加 `android:tag="webhtv:scrim"`~~（无效，背景会被运行时覆盖）
4. 契约测试：断言两处 `overlay` 不再使用硬编码字面量、`colorScrim()` 的 alpha 随 `scrimOpacity` 变化、基线逐字节不变。
5. 编辑器文案：`overlayOpacity` 标注"仅 Web 主题生效"。

## 8. 验收标准与回滚

**验收（实施阶段）**
- 自动化：`:app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.theme.*'` 全绿，含上述 4 条契约断言。
- 设备（dev3 `192.168.50.3:5559`，覆盖安装）：
  - 手机与 TV 各注入 `scrimOpacity=0.00` 与 `0.85` 两个极值，打开 `dialog_tmdb_person`（或 `dialog_episode_detail`）逐像素比对，差异必须 > 0 且变化区域是遮罩而非卡片；
  - 分隔线（`dialog_ad_block_stats`）在两极值下**必须 0 差异**，证明作用域限定成功；
  - `FATAL EXCEPTION = 0`；空 profile 路径与 Default 逐字节一致（Layer 1 不回归）。
- 环境：偏好按字节还原、设备装回 mobile flavor、无残留测试文件。

**回滚路径**
- 代码：revert 该提交即移除 `SCRIM` 角色、`ColorDrawable` 分支与 3 个 tag；不涉及 profile 数据格式、偏好键或资源替换，用户已保存的 profile 仍可正常加载（未知槽忽略）。

## 9. 仍未覆盖（不在本任务范围）

- 半透明填充匹配策略、`StateListDrawable`/`RippleDrawable` 背景、远程 Web 主题页面。
- `overlayOpacity` 的最终归宿（改绑图片 veil / 保留 Web 只读 / 移出编辑器）。

## 10. 待用户裁定

1. 是否批准**方案 B**（`scrimOpacity` 实施 + `overlayOpacity` 暂缓并标注）？
2. `overlayOpacity` 采用"编辑器标注"还是"直接移出编辑器"（后者改动 16 槽契约）？

## 11. 实施前复核发现：第 3 节前提被证伪（2026-09-25，重要）

在按第 7 节动手实施、逐文件核对"这些弹窗究竟在哪里取 scrim 色"时，发现**第 3 节的核心前提不成立**，实施被中止、相关代码改动已全部回退（工作树保持干净）。

### 11.1 事实：三个"遮罩"弹窗的 XML scrim 背景都是死代码

三个全屏弹窗确实在布局根上写了 `android:background="@color/webhtv_color_scrim"`，但**运行时都会用硬编码色覆盖它**：

| 弹窗 | 运行时覆盖 | 覆盖后语义 |
| --- | --- | --- |
| `app/src/mobile/java/.../EpisodeDetailDialog.java:290,297` | `int overlay = light ? 0x99F4F7FA : 0xB3000000; root.setBackgroundColor(overlay);` | 半透明遮罩（**但来自硬编码字面量，不来自 token**） |
| `app/src/main/java/.../TmdbPersonDialog.java:372,383` | `int overlay = light ? 0x99F4F7FA : 0x8F000000; view.setBackgroundColor(overlay);` | 同上 |
| `app/src/leanback/java/.../EpisodeDetailDialog.java:316,322` | `int background = light ? 0xFFFFFFFF : 0xFF101214; root.setBackgroundColor(background);` | **不透明**全屏底色，根本不是遮罩 |
| `app/src/main/java/.../TmdbVideoPlayerDialog.java:322` | `container.setBackgroundColor(Color.BLACK)`（全屏时） | `Color.BLACK`，也不是遮罩 |

即：**`webhtv_color_scrim` 在这三处的 XML 值在运行时会被覆盖，是死代码。**

### 11.2 剩余"活着"的 `webhtv_color_scrim` 用法全都不是遮罩

- `dialog_ad_block_stats.xml`（三个 source set）：2 处 `1dp` 分隔线 + 1 处 `88dp` 行背景
- `dialog_receive.xml`：`ShapeableImageView` 的海报占位底

结论：**当前代码里不存在任何由 `webhtv_color_scrim` 驱动的、活着的遮罩消费点。** 因此第 3 节"三个遮罩语义明确、可直接接线"的判断是错的，第 7 节"加 tag + 让 binder 改写 `ColorDrawable`"的方案即便实施也不会生效（tag 打在会被覆盖的背景上）。

### 11.3 修正后的方案（更简单，但改动文件不同）

正确的接线点是**遮罩色真正被赋予的那一行**，即把上述硬编码字面量换成 token：

```java
// 例：mobile EpisodeDetailDialog
int overlay = ThemeController.current().colorScrim();
if (root != null) root.setBackgroundColor(overlay);
```

- `ThemeTokens.colorScrim()` 已经含用户 `scrimOpacity`（`ThemeResolver` 用 `withAlpha(base.colorScrim(), scrimOpacity)` 写入），所以这样接线后滑块立刻生效。
- **不需要**新增 `ThemeRole.SCRIM`、**不需要** `ThemeBinder` 支持 `ColorDrawable`、**不需要**布局 tag——原方案的三项改动全部可省。
- 影响文件：`app/src/mobile/java/.../EpisodeDetailDialog.java`、`app/src/main/java/.../TmdbPersonDialog.java`（两处真·半透明遮罩）。
- `leanback/EpisodeDetailDialog`（不透明底色）与 `TmdbVideoPlayerDialog`（`Color.BLACK`）**语义不是遮罩**，不应改动；若希望它们也响应，需先裁定其视觉语义。
- 这一路径改动的是**弹窗运行时代码**，超出本任务最初声明的文件范围，需用户在批准后重新开立范围。

### 11.4 教训

设计文档第 2 节只核对了"XML 里 scrim 挂在哪"，没有核对"运行时是否被覆盖"，而本项目多处弹窗采用**运行时硬编码调色板**（`resolveLightTheme` + 局部字面量），与 token 体系并行。后续任何"给某 token 找落点"的评估，都必须验证该处颜色**最终生效来源**，而不是只看 XML 声明。

## 12. 第 11.3 节"修正路径"的进一步阻塞：直接替换会改动默认外观（2026-09-25）

第 11.3 节建议把两处运行时 `overlay` 字面量换成 `ThemeController.current().colorScrim()`。进一步量化后确认：**这会让默认（无自定义 profile）外观发生可见变化，属回归，不能直接做。**

| 位置 | 现有运行时遮罩 | token 基线 `colorScrim()` | 差异 |
| --- | --- | --- | --- |
| mobile `EpisodeDetailDialog` 浅色 | `0x99F4F7FA`（白，alpha 0.600） | `0x52000000`（黑，alpha 0.322） | **色相、明暗、强度全部不同** |
| mobile `EpisodeDetailDialog` 深色 | `0xB3000000`（黑，alpha 0.702） | `0x8A000000`（黑，alpha 0.541） | 强度不同 |
| `TmdbPersonDialog` 浅色 | `0x99F4F7FA`（白，alpha 0.600） | `0x52000000`（黑，alpha 0.322） | **色相、明暗、强度全部不同** |
| `TmdbPersonDialog` 深色 | `0x8F000000`（黑，alpha 0.561） | `0x8A000000`（黑，alpha 0.541） | 接近（差 0.02） |

即：浅色下这两个弹窗用的是**半透明白**遮罩，而 `colorScrim` 基线是**半透明黑**。直接把字面量换成 `colorScrim()` 会让浅色弹窗背景由"提亮"变"压暗"，是明显的视觉回归；深色下强度也会改变。

### 12.1 因此该槽的正确处置只有两条路

- **路径 1（保默认外观）**：不动现有默认值，仅在用户**显式设置** `scrimOpacity` 时才覆盖——例如保留字面量作为默认，用 `profile.scrimOpacity != null` 判定后改用 `withAlpha(<既有遮罩色>, userOpacity)`。这样默认逐像素不变、用户调整后生效。代价：需要把"用户是否覆盖"这一信息暴露到 `ThemeTokens`（当前 `colorScrim` 已把用户值烘进 alpha，无法区分"用户设为 0.322"与"未设置"）。
- **路径 2（接受外观统一）**：把默认遮罩也统一到 `colorScrim` 基线，属于**有意的视觉变更**，须作为独立视觉变更评审，不能混在"接线死槽"里。

### 12.2 结论

`scrimOpacity` 的接线**比原评估更复杂**：真正的阻塞不是"找消费点"，而是"现有默认遮罩色与 token 基线不一致"。在选定路径 1 或路径 2 之前不应实施。本任务据此保持**未实施**状态。

---

## 13. 实施记录：路径 1（保默认外观）已落地（2026-09-26）

用户批准"路径 1（保默认外观）"，并要求同时完成 `overlayOpacity` 的编辑器标注。任务 `THEME-SCRIM-WIRE-P1-20260926`。

### 13.1 实现

| 文件 | 改动 |
| --- | --- |
| `ThemeController.java` | 新增 `configuredScrimOpacity(boolean light)`：返回**用户显式设置**的 scrim alpha，未设置为 `null`（这是保住默认外观的关键——必须区分"未设置"与"恰好等于默认值"）；新增纯函数 `applyScrimOpacity(int base, Float opacity)`：`null` 原样返回，否则**只替换 alpha 字节**、保留 RGB |
| mobile `EpisodeDetailDialog.java` | `int overlay = applyScrimOpacity(light ? 0x99F4F7FA : 0xB3000000, light)`；本地 helper 委托给 `ThemeController` |
| `TmdbPersonDialog.java` | 同上，基色为 `light ? 0x99F4F7FA : 0x8F000000` |
| `values/strings.xml`、`values-zh-rCN`、`values-zh-rTW` | `overlayOpacity` 文案追加"仅 Web 主题生效"（`web theme only` / `仅 Web 主题生效` / `僅 Web 主題生效`） |

**设计要点**：没有把 token 的 `colorScrim` 替换进弹窗，而是保留每个弹窗自己的遮罩色相（浅色半透明白、深色半透明黑），只让用户控制 alpha。这直接回应第 12 节的阻塞——既有默认值原样保留，用户在编辑器拖动时才有变化。

### 13.2 自动化验证（通过）

- `:app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.theme.*'` → **BUILD SUCCESSFUL**，新增 3 条契约：
  - `scrimOpacityKeepsTheDialogOwnColourAndOnlyMovesAlphaWhenSet`：两弹窗必须保留 `0x99F4F7FA` 字面量、必须**不**出现 `colorScrim()`、必须委托共享函数；并直接断言算术行为——`null` 严格等于原值（`0x99F4F7FA`/`0xB3000000`）、`0f→0x00F4F7FA`、`1f→0xFFF4F7FA`、越界 `-1f/2f` 被夹取、`0.85f→0xD9F4F7FA`。
  - `configuredScrimOpacityReportsUnsetSeparatelyFromAnyValue`：通过反射注入 profile，断言未设置→`null`、仅设 light 时 dark 仍为 `null`、**设成 0.322f（等于 token 基线）仍报告为用户值而非"未设置"**。
  - `overlayOpacityIsLabelledAsWebOnly`：三语文案存在。
- 回归：`:app:testMobileArm64_v8aDebugUnitTest` 覆盖 `theme.*`、`ui.dialog.*`、`EpisodeAdapterTest`、`TmdbDetailActivityLayoutTest` → **全部通过**（这些测试直接引用被改动文件）。
- 编译：`:app:compileMobileArm64_v8aDebugJavaWithJavac` 与 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 均 **BUILD SUCCESSFUL**。

### 13.3 未完成的验证（如实记录，不冒充通过）

- **未取得设备端截图证据**。两个弹窗都只能从 TMDB 详情路径进入，而当前设备 `192.168.50.3:5559` 无观看历史、无站点配置，无法在合理成本内到达该路径。
- 已排除"网络是阻塞"这一误判：早前 `ping api.themoviedb.org` 100% 丢包与裸请求 401 均具误导性——TMDB 封 ICMP，且裸请求缺少 `api_key`；用设备内配置的 key 实测 `search/person` **返回 200 且带真实数据**。因此该弹窗在**有内容源**的设备上应可打开，但本次未实测。
- 因此 `scrimOpacity` 的**运行时像素级效果**目前只有单元级行为证据，缺设备级确认。建议在有可用内容源时补一次：设 `scrimOpacity` 两个极值打开 `TmdbPersonDialog`，比对遮罩区域像素差异应 > 0，而分隔线（`dialog_ad_block_stats`）应 0 差异。

### 13.4 设备级像素验证补全（2026-09-27）

任务 `THEME-SCRIM-PIXEL-20260927` 在 dev3 指定设备 `192.168.50.3:5559`（Android 9 / API 28，安装 mobile arm64 debug 覆盖包）补齐了此前缺失的设备级截图证据。为了避免真实站点、网络图片和滚动动画干扰，验证使用 debug-only 宿主 `TmdbScrimHostActivity` 与 instrumentation 测试 `TmdbPersonScrimPixelDeviceTest`，直接显示生产 `TmdbPersonDialog`。

- 验证对象：`scrimOpacity` 未设置 vs 产品允许的最大值 `ThemeProfileValidator.MAX_SCRIM_OPACITY = 0.85f`。
- 遮罩采样点（屏幕坐标约 `(4,4)`）：未设置 `#ff9fbad6`，`0.85` 为 `#ffd5e0ed`；两者 RGB/alpha 均不同，证明用户设置确实改变遮罩像素。
- 面板中心采样 `(960,540)`：未设置 `#fff4f7fa`，`0.85` 仍为 `#fff4f7fa`，二者完全相同；证明遮罩透明度没有误伤不透明弹窗面板。
- 自动断言：遮罩区域变化像素数 `> 0`，弹窗面板中心变化像素数 `= 0`。
- 执行命令：

```bash
./gradlew :app:assembleMobileArm64_v8aDebug :app:assembleMobileArm64_v8aDebugAndroidTest
adb -s 192.168.50.3:5559 install -r app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk
adb -s 192.168.50.3:5559 install -r app/build/outputs/apk/androidTest/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug-androidTest.apk
adb -s 192.168.50.3:5559 shell am instrument -w -r \
  -e class com.fongmi.android.tv.theme.TmdbPersonScrimPixelDeviceTest \
  com.silent.android.webhtv.test/androidx.test.runner.AndroidJUnitRunner
```

- 结果：`OK (1 test)`，`Tests run: 1, Failures: 0`。
- 提交：`401b65b83fc2a349234a33e02ce97a3886ffc5b1`；恢复标签：`recovery/THEME-SCRIM-PIXEL-20260927/20260927034658-401b65b83fc2`。
- 回滚锚点：该验证只新增 debug-only 宿主、instrumentation 测试和本文记录，不修改生产运行时行为；回退本提交即可移除验证入口。
