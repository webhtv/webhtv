# SCRIM-DIM-SCOPE-20261013：弹窗 scrim 收敛的前提不成立（侦察结论，不改代码）

## Recovery anchor

- 目标：为审计 §6.3 提出的"§9.7 弹窗 scrim 未走 token（15+ 处硬编码 dimAmount）"给出**可执行方案或否决理由**。
- 性质：**只读侦察**。本任务**不改任何生产代码**——侦察结论是"前提不成立，需先做独立裁定"。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `2cde387e557`。
- 结论：**否决直接收敛**。`dimAmount` 与规范里的 "scrim" 是**两种不同机制**；且 `webhtv_color_scrim` 被**混用**（6 处分隔线 + 1 处占位底 + 3 处真遮罩），全局替换会破坏非遮罩用途。
- 交付坐标：提交 `1055bd2b55f`（dev4），recovery tag `recovery/SCRIM-DIM-SCOPE-20261013/20261013162255`；未推送。
- 下一步唯一动作：无（本任务已交付结论）。若要继续，需先裁定 §5 的两个问题。

## 1. 审计原命题

审计 §6.3 记录：

> §9.7 scrim 使用 scrim token：仅 5 个布局用 `webhtv_color_scrim`；绝大多数弹窗用**硬编码 dimAmount 常量**（`LightDialog.java:252`=0.58f、`CacheManagementDialog.java:209`=0.6f、`PanNetworkDiagnosticDialog.java:156`=0.62f、`leanback/EpisodeDialog.java:90`=0.18f 等 15+ 处）。

我在 `CONTRAST-P0-20261013.md` §6.2 把它记为"下一批首选"，理由是"刚接线的 `overlayDark` 正好可以收掉"。**这个判断是错的**，本轮侦察推翻了它。

## 2. 为什么错：`dimAmount` 不是颜色

规范 §9.7 原文：

> Dialog 背景使用 `surfaceContainerHigh`，圆角 `shapeExtraLarge`，**scrim 使用 `scrim`**。

而实测的 60+ 处 `dimAmount` 是**窗口级遮罩强度**（`WindowManager.LayoutParams.dimAmount` + `FLAG_DIM_BEHIND`），由系统在窗口后面画一层黑，**与任何颜色资源无关**。它不能"改用某个 color token"——两者不是同一个东西。

真正与规范"scrim 使用 scrim"对应的是**视图级遮罩**（布局里 `android:background="@color/webhtv_color_scrim"` 的全屏 FrameLayout）。

## 3. 更硬的证据：仓库已有一份专门设计文档

`docs/THEME-SCRIM-OVERLAY-20260925-design.md`（2026-09-25）已经深入分析过这个区域，其结论直接适用于本命题：

> **结论**：`scrimOpacity` 是**实现遗漏**，不是设计缺失。但它当前被**混用**（分隔线/占位底），所以**不能对 `webhtv_color_scrim` 做全局替换**——必须按视图语义限定作用域。

同文还记录了 `overlayOpacity` 的裁定：

> `overlayOpacity` → **语义不成立**，建议暂缓并降级为 Web 只读。

以及 `overlay_dark` 的状态：

> `overlay_dark` → `shape_tmdb_episode_overlay.xml:6`、`shape_tmdb_recommendation_info.xml:5` 的 `android:centerColor`（图片上的渐变 veil）→ **4.23 已明确冻结**（`colorOverlayDark` 不随 profile 变）。

**这意味着我在 `CONTRAST-P0-20261013` 里给 `webhtvColorOverlayDark` 接线（赋 `@color/webhtv_color_overlay_dark`）这件事，需要重新审视**：该文档说 `colorOverlayDark` 是**已冻结、不随 profile 变**的。我把它接进 `Theme.WebHTV` 是为了让空态 scrim 能取到值——接线本身让"取得到值"成立，但若将来有人把它做成用户可调槽，就会与 4.23 的冻结决定冲突。见 §6.1。

## 4. 实测：`webhtv_color_scrim` 的混用情况（逐处核对）

| 文件:行 | 视图 | 语义 | 能否走"遮罩"语义 |
|---|---|---|---|
| `main/dialog_episode_detail.xml:8` | 根 FrameLayout `match_parent×match_parent` | **全屏弹层遮罩** | ✅ |
| `main/dialog_tmdb_person.xml:9` | 根 FrameLayout | **全屏弹层遮罩** | ✅ |
| `main/dialog_tmdb_video_player.xml:50` | 内层 FrameLayout | **播放器弹层遮罩** | ✅（但有叠加问题，见 §4.1） |
| `mobile/dialog_receive.xml:19` | `ShapeableImageView` 90×120dp 的 background | **海报占位底** | ❌ 非遮罩 |
| `mobile/dialog_ad_block_stats.xml:143` | `View` `match_parent×1dp` | **分隔线** | ❌ |
| `mobile/dialog_ad_block_stats.xml:339` | `wrap_content×88dp` 行容器 | **行底** | ❌ |
| `mobile/dialog_ad_block_stats.xml:783` | `View` `match_parent×1dp` | **分隔线** | ❌ |
| `leanback/dialog_ad_block_stats.xml:149` | `View` `match_parent×1dp` | **分隔线** | ❌ |
| `leanback/dialog_ad_block_stats.xml:345` | `wrap_content×88dp` 行容器 | **行底** | ❌ |
| `leanback/dialog_ad_block_stats.xml:805` | `View` `match_parent×1dp` | **分隔线** | ❌ |

即 **10 处用法里只有 3 处是真正的遮罩**，其余 7 处是分隔线/行底/占位底。设计文档的"混用"判断**完全成立**。

### 4.1 三个真遮罩的窗口 dim 状态还不一致

设计文档已记录，我复核确认：

| 弹窗 | 视图 scrim | 窗口 dim | 说明 |
|---|---|---|---|
| `EpisodeDetailDialog` | 有 | `setDimAmount(0f)` | 关窗口 dim，靠视图 scrim 压暗 |
| `TmdbPersonDialog` | 有 | `setDimAmount(0f)` | 同上 |
| `TmdbVideoPlayerDialog` | 有 | `setDimAmount(0.72f)` | **保留窗口 dim** → 两者**叠加** |

所以即使只处理这 3 处，`TmdbVideoPlayerDialog` 也不能套用另两个的做法——接线视图 scrim 会与 0.72 的窗口 dim 叠加，需要先裁定是否降低窗口 dim 来补偿。

## 5. 需要裁定的两个问题（本任务不代拟）

1. **`dimAmount` 的 25 处非零值要不要收敛成一个来源？** 现状分布：`0.58f`×13、`0.6f`×3、`0.62f`×2、`0.56f`×2、`0.72f`×1、`0.18f`×1、`0.08f`×1。其中 `0.58f` 占多数，看起来是**同一意图的复制粘贴**而非各自的设计决定；但 `0.18f`（leanback 选集）与 `0.08f`（TMDB 详情）明显是**刻意的弱遮罩**，不能一并统一。要收敛的话，得先定"哪几档是语义档位"。
2. **`webhtv_color_scrim` 的 7 处非遮罩用法要不要先改名？** 它们现在借用了"遮罩"token 表达分隔线/行底/占位底。若要按语义限定作用域（设计文档的建议），第一步应该是给这些非遮罩用途换一个准确的角色名，否则任何"按 token 收口"的改动都会误伤它们。

## 6. 相邻发现

1. **这是仓库已登记的"未完成项"，且方案在候审。** `docs/THEME-CUSTOMIZATION-LAYERS-20260921.md` 的状态行原文：

   > **当前剩余：`scrimOpacity`/`overlayOpacity` 的接线（设计研究已完成、候审：`docs/THEME-SCRIM-OVERLAY-20260925-design.md`）**、半透明填充匹配策略、`StateListDrawable`/`RippleDrawable` 背景、远程 Web 主题页面……因此按第 8 节完成定义，**尚不能宣称"全局主题自定义已完成"**。

   即：这个区域**不是"审计新发现的缺口"**，而是**早就被识别、设计研究已完成、正等用户批准**的既定工作项。审计把它记为"§9.7 scrim 未走 token"时没有引用这份文档，我也在上一批把它误记为"下一批首选"。**正确的做法是先读这份候审设计，而不是自己另起一套。**
2. **`webhtvColorOverlayDark` 的接线与 4.23 冻结决定的关系需要复核**：我在 `CONTRAST-P0-20261013` 里把它接进 `Theme.WebHTV`（为了让空态 scrim 取到值）。设计文档说 `colorOverlayDark` 是"4.23 已明确冻结（不随 profile 变）"。**接线本身不违反冻结**（我传的是固定的 `@color/webhtv_color_overlay_dark`，没有把它做成用户可调槽），但这条边界要写下来，防止后续有人把它接进 `ThemeProfile` 的 16 槽——`ThemeProfile.java:101` 确实有 `overlayOpacity` 槽，两者一旦接上就会互相牵动。
3. **`webhtvColorScrim` 与 `webhtvColorOverlayLight` 仍只声明、从未赋值**：审计的"只声明、没接线"清单里还有它们。按设计文档，`scrimOpacity` 是"实现遗漏"（建议实施）、`overlayOpacity` 是"语义不成立"（建议降级为 Web 只读）——所以这两个属性的接线**不是对称的**，不能一起做。
4. **审计 §6.3 的这条记录应更正**：它把"硬编码 dimAmount"与"scrim 未走 token"并列为同一个问题，但两者是不同机制。已在本文 §2 说明；审计文档本身未改（它是历史记录）。

## 7. 结论

审计提出的"§9.7 弹窗 scrim 未走 token"**不是一个可以直接开工的批次**：

- `dimAmount`（窗口遮罩强度）与 `scrim`（颜色角色）是两种机制，前者无法"改用 color token"；
- `webhtv_color_scrim` 被混用（10 处里只有 3 处是遮罩），全局替换会破坏分隔线/行底/占位底；
- 3 处真遮罩里还有 1 处（`TmdbVideoPlayerDialog`）存在窗口 dim 与视图 scrim 叠加的问题；
- 仓库已有一份专门设计文档（`THEME-SCRIM-OVERLAY-20260925-design.md`）给出过"必须按视图语义限定作用域"的结论，且对 `overlayOpacity` 的裁定是"语义不成立"。

**建议**：先裁定 §5 的两个问题（dimAmount 的语义档位、非遮罩用法的改名），再决定是否开工。本任务只交付这个结论，不动代码。
