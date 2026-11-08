# 手机版默认主题「深色表面上的文字」对比度修复

任务：`mobile-dark-surface-text-contrast`
分支：`dev3`，基线 HEAD `8847705081083a5db10638b8d31da33b30b75911`
时间：2026-10-04 CST

## Recovery anchor

- **目标（完成句）**：修复手机版默认主题下「固定深色表面」的文字/图标对比度，并加守卫测试防回归。
- **验收标准**：受影响的播放器玻璃面板与 TMDB 深色页线路芯片在**日间（默认）调色板**下对比度 ≥ 4.5:1；`ThemeBaseWiringTest` 与相邻套件全绿；`scripts/check_ui_tokens.sh` 相对 HEAD 无新增违规。
- **状态**：已完成并验证（见下）。
- **下一步**：无（已提交）。

## 用户反馈

5 张截图 + 「默认主题下有大量地方字体看不清了」。红圈标注的是原生详情页上三个「转存R原画 / 预览画质 / 转存R原画不外调」芯片：**浅色底 + 纯白字**。

## 根因（代码可证，非像素推测）

手机版**日间**调色板 `app/src/main/res/values/webhtv_tokens.xml` 把 Material 角色映射为浅色主题语义：

| 角色 | 日间值 | 夜间值 |
|---|---|---|
| `webhtv_color_on_surface` | `#1A1C1E`（近黑） | `#E2E2E9` |
| `webhtv_color_on_surface_variant` | `#44474F` | `#C4C6D0` |

但手机版有**大量固定深色表面**，它们不随调色板变化。于是「跟随调色板的前景色」落在「固定深色表面」上就是深底深字。

### 缺陷 A：播放器玻璃子面板（12 个布局，57 处）

`0af2340d4`（2026-09-21「unify dialog and settings visual system」）把这些面板里的
`@color/white` / `white_70` / `white_90` / `white_50/60` 全量替换为
`?attr/colorOnSurface` / `?attr/colorOnSurfaceVariant` / `?attr/colorOutline`。

面板本身是**恒定深色**：

- `shape_player_child_sheet_panel`、`shape_dialog_glass_panel`、`shape_dialog_control_glass_panel`、`shape_quick_search_panel`、`shape_dialog_danmaku_sheet_glass_panel`、`shape_danmaku_setting_panel` = 渐变 `#E62F315E → #D6282955 → #CC303463`
- `shape_danmaku_sheet_panel` = `#FF28282A`

同样的 drawable + 同样的 `?attr/colorOnSurface` 在 **leanback** 上是正确的——因为 leanback 表（`app/src/leanback/res/values/webhtv_tokens.xml`）在**所有**档位都是深色（`on_surface = #E2E2E9`）。手机版日间表把同一角色解析为 `#1A1C1E`，于是变成深底深字。

受影响的 12 个布局：`dialog_danmaku`、`dialog_danmaku_search`、`dialog_danmaku_setting`、`dialog_episode_list`、`dialog_live`、`dialog_live_epg`、`dialog_offset`、`dialog_quick_search`、`dialog_timer`、`dialog_title`、`dialog_track`、`dialog_video_content`。

### 缺陷 B：TMDB 线路芯片深色页变体

`FlagAdapter.applyTmdbTheme` 在两个变体间切换：

- `selector_tmdb_flag_item`（亮色页）：浅底 + 深字 `#12202D` → 正确
- `selector_tmdb_flag_item_dark`（深色页）：文字 `#F3F7FA` / 选中 `#8FE7B6` → 需要深底

`e1ea7ab34`（2026-09-21「unify detail and player control tokens」）把深色页变体的实色换成了
`@color/webhtv_color_surface_container(_high)`、`webhtv_color_success_container`、
`webhtv_color_primary_container` 等**跟随调色板的语义表面**。日间表把它们解析为
`#ECEEF4` / `#E7E8EF` / `#C4EED0`（浅色），于是变成**浅底 + 近白字**。

默认主题下播放页页头是深色的，`isTmdbPlaybackLightTheme()` 返回 false，所以用户看到的就是这个坏掉的变体——正是截图里红圈那三个芯片。

## 修复

### A. 改用与调色板无关的语义角色（而非回退到裸 `@color/white`）

刻意**不**简单回退成 `@color/white`：仓库有 `UiStyleSourceTest.dialogAndSettingLayoutsUseSemanticColors`
（由同一个 `0af2340d4` 加入）禁止 `dialog_*` 布局出现裸色值，且 token 体系本身要求走语义角色。
改用**三张表取值完全相同**的既有角色（`webhtv_on_wallpaper`、`webhtv_color_player_control_muted`、
`webhtv_color_overlay_light` 在 light / night / leanback 三表中分别是 `#FFFFFF` / `#CCFFFFFF` /
`#14FFFFFF`），既修复对比度又不新增资源、不扩大范围、不触发豁免。

| 原值（`0af2340d4` 之前） | 修复后 |
|---|---|
| `@color/white`、`@color/white_90`、`app:tint @color/white_90` | `?attr/webhtvColorOnWallpaper` |
| `@color/white_70`、`@color/white_60`、`@color/white_50` | `@color/webhtv_color_player_control_muted` |
| `app:strokeColor #4DFFFFFF` / `#66FFFFFF` | `@color/webhtv_color_player_control_muted` |
| `app:rippleColor #33FFFFFF` | `@color/webhtv_color_overlay_light` |

### B. 恢复深色页芯片的恒定半透明填充

`selector_tmdb_flag_item_dark.xml` 恢复为 `e1ea7ab34` 之前的恒定值
（`#664B8F72` / `#33FFFFFF` / `#26FFFFFF` + 描边）。同族文件
（`selector_control_sheet_button`、`shape_player_child_sheet_panel` 等）本来就在
`docs/ui-token-allowlist.txt` 中按「播放器/沉浸式叠层使用固定半透明色」豁免，本次为它新增同一条目。
**亮色页变体保持语义表面不变**（那里文字是深色，本来就正确）。

## 守卫测试

`app/src/test/java/com/fongmi/android/tv/theme/ThemeBaseWiringTest.java` 新增 3 项：

1. `darkGlassSheetsUseAConstantLightForeground` — 12 个面板的**每一个**前景属性
   （textColor / textColorHint / tint / strokeColor / rippleColor / boxStrokeColor）
   都必须解析为与调色板无关的恒定亮色。逐属性检查，避免只改对一部分节点。
2. `controlSheetButtonTextStaysPaletteIndependent` — `selector_control_sheet_text`
   不得含 `?attr/`（它同时服务深色面板与浅色选中带）。
3. `tmdbDarkChipVariantKeepsAConstantTranslucentFill` — 深色页芯片不得再引用跟随调色板的表面，
   且必须保留恒定填充；同时钉住亮色页兄弟仍用语义表面。断言前先剥离 XML 注释。

## 验证

| 项目 | 结果 |
|---|---|
| `ThemeBaseWiringTest` | 13 项全过（含 3 项新守卫） |
| 相邻套件（theme / ui.style / ui.dialog / VideoActivityLayoutTest / EpisodeAdapterTest） | 480 项，0 失败 0 错误 |
| `scripts/check_ui_tokens.sh --stage B`（本任务所属阶段） | `violations=0 legacy=0`，对比度 38 组 0 失败 |
| `scripts/check_ui_tokens.sh --strict` | 与 HEAD 完全一致（仅剩既有 `item_following.xml`，非本次引入） |
| 构建安装 | `scripts/build_arm64_debug_install.sh --flavor mobile --serial 192.168.50.3:5559` 成功（覆盖安装，未卸载） |
| 实机（日间模式，1920x1080） | `dialog_timer` 面板实测 **5.85–7.41:1**；修复前同面板反事实为 **1.23–1.50:1** |
| APK 资源校验 | `selector_tmdb_flag_item_dark` 中恒定填充存在、`webhtv_color_*` token 已移除 |

### 实机测量明细（`dialog_timer`，日间）

| 元素 | 面板底色 | 文字 | 对比度 |
|---|---|---|---|
| 标题「定时」 | (58,59,102) | (215,216,225) | 7.41:1 |
| 「5 分钟」 | (50,53,103) | (193,194,209) | 6.47:1 |
| 「15 分钟」 | (51,54,102) | (191,192,207) | 6.27:1 |
| 「30 分钟」 | (51,53,103) | (193,194,209) | 6.45:1 |
| 「1 小时」 | (50,53,102) | (184,184,201) | 5.85:1 |

同一面板反事实：`colorOnSurfaceVariant` `#44474F` → 1.23:1；`colorOnSurface` `#1A1C1E` → 1.50:1。

## 回滚

单次提交，`git revert <commit>` 即可。修复仅涉及
`app/src/mobile/res/layout/`（12 个 `dialog_*`）、
`app/src/mobile/res/drawable/selector_tmdb_flag_item_dark.xml`、
`app/src/test/java/com/fongmi/android/tv/theme/ThemeBaseWiringTest.java`、
`docs/ui-token-allowlist.txt`（+1 行）与本文档。

## 未纳入本次范围（已确认存在，需另行授权）

1. **`dialog_control.xml` / `dialog_player_osd.xml` 等仍走 `?attr/colorOnSurface`**。
   实机截图中「播放 / 屏显设置 / 倍速 / 缩放」等标题仍偏暗。它们的根背景是
   `shape_shell_proxy_dialog`（`?attr/colorSurfaceContainerHigh`）或
   `shape_dialog_control_glass_panel`，属于另一族表面，需要单独判定。
2. **`item_following.xml`**：`--strict` 既有违规，HEAD 即存在，本次未触碰。
3. `scripts/check_ui_tokens.sh --strict` 在 HEAD 就不通过（仅上述 1 项），
   故本次以「相对 HEAD 无新增违规」为验收口径。
