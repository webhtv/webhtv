# WebHTV 全局视觉统一设计文档

> 状态：设计阶段，未修改运行时代码、资源、依赖或运行时行为；本文是本目标的唯一设计交付物。
> 设计日期：2026-09-20
> 设计范围：Android `mobile`、`leanback`、共享原生 UI、`lab` 入口，以及内置 `assets` 页面；播放器只覆盖控制层和外层弹窗，不改变视频画面、解码、渲染或播放器内核。
> 基线：首发提交基于 `dev3@3f94b8e86e3e57f3c31f8dab05699e9968e86369`；完整版在同一文档上的追加修订基线为 `45f71ffcc995f40654ebff032f441bfa40a676ac`。起始工作树干净，写作范围仅 `docs/**`。

## Recovery anchor

- 目标：按阶段 A–E 完成 WebHTV 原生与内置 Web 的语义视觉统一，阶段 F 仅保留清理与独立审批的高级主题入口。
- 验收标准：每阶段满足对应 DoD、静态检查和编译；有设备要求的阶段完成代表场景且保持播放、分页、点击、网络和数据行为不回退；每阶段独立提交并生成 recovery tag。
- 当前证据：阶段 A–F 均已独立提交并打 recovery tag，后续回归修复也已提交；新增 `UiLayoutSourceTest` 固化海报/横向卡片比例、TV 卡片圆角族与图片文字 scrim，并补齐 `--webhtv-surface-variant`。
- 未验证项：播放器长时/字幕/音轨/倍速回归与管理页、reader、WebHome 三页面浏览器视觉逐项人工验收仍需完成；高级主题编辑器按计划继续延后，需独立审批。
- 下一步唯一动作：Layer 1、Layer 2A–2D 的代码与自动化门禁均已落地（Layer 2D 见 `docs/THEME-CUSTOMIZATION-LAYERS-20260921.md` 第 4.11 节）；Layer 2 的设备功能矩阵仍未执行，需在可进入原生“外观”页的环境中补做后才能宣称全局主题自定义完成。

### 阶段 M（电视版统一焦点环）实施记录（2026-10-04）

- 问题：焦点态只改填充色，而 `colorFocus == colorPrimary`，导致自带主题色按钮
  （关于弹窗、追更页）聚焦无视觉差异；齿轮写死 `#0B57D0`。
- 方案：状态化边框环 + 主题 token。新增 `focus_ring_primary/secondary/error.xml` 状态色表，
  环色取焦点态填充的配对 on-色；`tv_item_focus_ring` 由写死 `#FFD166` 接线到
  `@color/webhtv_color_focus`，宽度统一 `@dimen/webhtv_focus_ring_width=3dp`。
- 实机验收又暴露出两处配对错误（环配到常态填充而非焦点态填充，对比度仅 1.33:1 / 1.40:1），
  已修正为 `on_primary` / `on_error_container`，实机复测 6.09:1 / 6.41:1。
- 契约：`TvFocusRingContractTest`（7 项）固化 R1 边框环存在、R2 单机制单宽度、
  R3 主题可控，以及环/填充 ≥3:1 的对比度回归；`NativeEnhancedPlaybackStyleFocusTest` /
  `FollowingUiSourceTest` 全绿无回归。

### 阶段 G（Layer 1 主题来源整合）实施记录（2026-09-21）

- 任务：`L1-THEME-INTEGRATE-20260921`；详细设计、DoD 与验收证据见 `docs/THEME-CUSTOMIZATION-LAYERS-20260921.md`。
- 变更：mobile `Theme.Base` 继承 `Theme.WebHTV.Mobile`、leanback `Theme.Base` 继承 `Theme.WebHTV.TV` 并删除写死的白色 primary；`Theme.WebHTV` 补齐布局实际使用的 Material 角色（tertiary、surfaceDim/Bright、五级 surfaceContainer、surfaceVariant、inverse 三色、controlNormal、onBackground、windowBackground），Dialog/Overlay 同步补齐，页面与弹窗同源。
- 修复既有缺陷：`3f3ab82b1f` 在 `app/src/leanback/res/layout/activity_video.xml` 引用了从未声明的 `colorOnSurface_20/70/80/90`，导致 leanback 资源链接失败；本阶段新增这 4 个 attr 与对应 palette 资源（RGB 跟随 `onSurface` token，alpha `0x33/0xB3/0xCC/0xE6`），leanback 资源链接恢复通过。
- 统一夜间判断：新增 `ThemeController.isNight(Context)`，`WebHomeChromeController.useDarkIcons()` 不再直读系统 night 位。
- 静态证据：`scripts/check_ui_tokens.sh --strict` 输出 `violations=0 legacy=0`，38 组对比度 0 失败（min=4.28），`hex_layouts/hex_drawables/hex_colors` 均为 0，`allowlisted=191`。
- 自动化证据：mobile 705 套件 / 4790 项 0 失败；leanback 614 套件 / 3917 项 0 失败；新增 `ThemeBaseWiringTest`（5 项）与 `ThemeControllerContractTest` 增补 2 项契约。
- 设备证据：dev3 `192.168.50.3:5559`（API 28）按 `scripts/build_arm64_debug_install.sh` 覆盖安装 mobile 与 leanback，均冷启动成功、`FATAL EXCEPTION=0`；mobile 主色为 token 蓝 `#0B57D0`，leanback 为 `#A8C7FA`，不再回落 Material 基线紫。
- 残余风险：未在真实播放器音频面板上确认 alpha 变体的渲染效果；未覆盖 Android 12+ 的 Dynamic Colors 优先级。
- 回滚锚点：回退阶段 G 提交即可恢复原 `Theme.Base` 继承；无数据迁移。

### 阶段 H（追更页壁纸化）实施记录（2026-09-21）

- 任务：`FOLLOWING-WALLPAPER-20260921`；用户确认方案 A（壁纸背景 + 与其它内容页一致的半透明面板），动效跟随全局壁纸，mobile/leanback 两端同时生效。
- 问题：`FollowingActivity` 在 `main` 源集且继承 `AppCompatActivity`，拿不到 `BaseActivity` 插入的 `CustomWallView`；其根布局又写死 `android:background="?attr/colorSurface"`，卡片用不透明 `colorSurfaceContainerHigh`。实测整页为 `#F8FAFD`（`(248,250,253)`），与首页/设置页的壁纸观感割裂；空态图标 `ic_home_following` 还是无条件白色 tint，压在近白底上几乎不可见。
- 变更：`FollowingActivity` 在 `setContentView` 后把 `CustomWallView` 插到 `android.R.id.content` 的 index 0（动效沿用全局设置，未强制关闭）；根布局去掉不透明底色；顶栏与空态容器改用新增的半透明面板 `shape_following_panel`；卡片背景改为 `@color/following_panel_bg`；空态图标 tint 改为 `?attr/colorOnSurfaceVariant`。
- 面板取值：`app/src/main/res/color/following_panel_bg.xml` 用 `android:alpha="0.80"` 叠加 `?attr/colorSurfaceContainerHigh`，明暗模式各自取当前 mode 语义表面，避免了固定 hex 与新增 token 造成的 49 色契约冲突。
- 文字色不变：仍使用 `?attr/colorOnSurface` / `?attr/colorOnSurfaceVariant`，与设置页行面板一致（实测设置页行文字最深像素即 `#1A1C1E`，为 light mode 的 `colorOnSurface`）。
- 静态证据：新文件不含 hex，`scripts/check_ui_tokens.sh --strict` 仍为 `violations=0 legacy=0`、38 组对比度 0 失败、`hex_layouts/hex_drawables/hex_colors=0`；计数变为 `drawables=550 colors=53`。
- 自动化证据：新增 `FollowingUiSourceTest#followingScreenFollowsTheGlobalWallpaperLikeOtherContentPages`（壁纸插入顺序、根布局透明、面板可达、卡片半透明、alpha 契约）；同步更新 `ThemeControllerContractTest` 中写死“根布局必须是 `colorSurface`”的旧断言。mobile/leanback 定向单测分别通过。
- 设备证据：dev3 `192.168.50.3:5559`（API 28）覆盖安装 mobile 与 leanback，`FATAL EXCEPTION=0`。
  - mobile 追更页：面板外像素 `(88,78,113)`（壁纸透出），面板内 `(208,207,217)`（80% 表面叠加壁纸）。
  - leanback 追更页：深色模式下壁纸透出，顶栏与空态面板为半透明深色，空态图标可见。
  - 截图：`/tmp/following-wallpaper-mobile.png`、`/tmp/following-current.png`（改造前对照）、`/tmp/tv-following-final.png`。
- TV 入口说明：leanback 首页按钮受 `HomeButton.getVisibleButtons()` + `FollowingSettings` 门控，设备当时“首页按钮”仅启用 7/10、不含“追更”。为完成 TV 视觉验收曾临时勾选该按钮，验收后已恢复为 7/10，未留下设置改动。
- 回滚锚点：回退阶段 H 提交即可恢复追更页的原不透明底色与卡片表面；无数据迁移。

### 阶段 H2（追更页面板口径对齐）实施记录（2026-09-21）

- 任务：`FOLLOWING-SCRIM-ALIGN-20260921`。用户指出阶段 H 的面板仍与首页不搭：实测阶段 H 顶栏/卡片为 80% 不透明的 `colorSurfaceContainerHigh` 叠加，TV 上呈近实心深灰板（`(57,59,68)`，壁纸被吃掉），而首页按钮是 20% 黑纱（`(93,86,105)`，壁纸透出）。
- 结论：面板必须改为低透明度黑纱，但**必须按模式选极性**。实测若两端一律用黑纱：TV 深色模式正常（正文 6.77–7.17:1），但手机浅色模式正文对比度跌到 `1.95:1`、次要文字 `1.06:1`（既有设置页行也仅 `2.27:1`），属于可读性回归，不可交付。
- 最终取值：深色模式（`res/color-night/`）顶栏 20% 黑、卡片 32% 黑，与 leanback 首页按钮 `shape_item_normal(@color/black_20)` 同源；浅色模式（`res/color/`）顶栏 45%、卡片 60% 的语义浅色 `?attr/colorSurfaceContainerHigh`，保证深色正文可读且壁纸仍透出。
- 设备证据：dev3 `192.168.50.3:5559`（API 28），两端 `FATAL EXCEPTION=0`。
  - TV 深色：顶栏 `(76,73,89)`、卡片 `(71,70,84)`，对 `colorOnSurface #E2E2E9` 分别为 `6.77:1` / `7.17:1`；首页按钮 `(93,86,105)` 为同一 20% 黑纱口径。
  - 手机浅色：顶栏 `(161,157,175)`、卡片 `(185,181,196)`，正文 `8.52:1`、次要文字 `4.63:1`（修复前分别 `1.95:1` / `1.06:1`）。
  - 截图：`/tmp/fs-tv-following-final.png`（改后）、`/tmp/fs-mobile-final.png`（改后）、`/tmp/fs-mobile-following.png`（手机黑纱对照，证明浅色端不能用黑纱）。
- 契约：`FollowingUiSourceTest` 增加模式极性断言——浅色面板不得含黑纱、暗色面板不得含浅色语义表面，并锁定 20/32% 与 45/60% 四档取值。
- 残余风险：面板透明度叠加在任意壁纸上无法给出全局保证，浅色模式在极亮壁纸下次要文字对比度会低于 4.5:1；这是仓库既有的壁纸面板取舍（设置页行为相同），如需硬保证需改为不透明面板。
- 回滚锚点：回退阶段 H2 提交即恢复阶段 H 的 80% 不透明语义表面；`color-night` 变体一并删除，无数据迁移。

### 阶段 L（Layer 2D：Web 快照、备份与收口）实施记录（2026-09-22）

- 任务：`L2D-THEME-CLOSURE-20260922`；完整契约与残余风险见 `docs/THEME-CUSTOMIZATION-LAYERS-20260921.md` 第 4.11 节。
- 变更：`ThemeWebBridge.snapshotJson()` 在保持原 13 字段与 key 名不变的前提下追加 `scrimOpacity`/`dialogOpacity`/`overlayOpacity`；备份键由 Layer 2A 已纳入 `Backup.APP_PREFS`；新增 `ThemeWebBridgeTokenTest` 5 项。
- 自动化证据：mobile/leanback theme + 备份定向单测全部通过；`scripts/check_ui_tokens.sh --strict` → `violations=0 legacy=0`、38 组对比度 0 失败。
- 未完成项：Layer 2 设备功能矩阵未执行。dev3 首页被内置 WebHome 接管，原生外观入口在自动化路径下不可达；编辑器交互、WebHome 快照实测、备份恢复、重启保持与连续 30 次切换均未验证。
- 回滚锚点：回退阶段 L 提交仅撤销 Web 快照三个附加字段，无数据迁移。

### 阶段 K（Layer 2C：主题编辑器与实时预览）实施记录（2026-09-22）

- 任务：`L2C-THEME-EDITOR-20260921`；完整契约、证据与残余风险见 `docs/THEME-CUSTOMIZATION-LAYERS-20260921.md` 第 4.10 节。
- 变更：新增共享 `ThemeEditor`（draft + 校验 + 原子 apply/reset）、`ThemePreviewView`（实时预览 + 16 槽编辑面板）、`ThemeColorPickerDialog`（HSV + 精确 hex）；mobile/leanback `ThemeDialog` 重写为编辑器，`AppearanceDialog` 不再直写 `theme_color`。
- 自动化证据：mobile/leanback theme 单测全部通过，双 flavor Java 编译通过，`scripts/check_ui_tokens.sh --strict` → `violations=0 legacy=0`、38 组对比度 0 失败。
- 未完成项：编辑器真机交互验收（模式/预设、单槽改色、透明度边界、取消/应用/重启、TV 遥控焦点）未执行——dev3 首页被内置 WebHome 接管，原生外观入口在自动化路径下不可达。
- 回滚锚点：回退阶段 K 提交即恢复原 14 色圆点对话框，profile 数据与 Layer 2B 绑定不受影响。

### 阶段 J（Layer 2B：受控 ThemeBinder 与运行时应用）实施记录（2026-09-22）

- 任务：`L2B-THEME-BINDER-20260921`；完整契约、设备证据与残余风险见 `docs/THEME-CUSTOMIZATION-LAYERS-20260921.md` 第 4.9 节。
- 变更：新增 `ThemeRole`/`ThemeColorIndex`/`ThemeBinder`；`ThemeController` 读取 v2 profile 并暴露 baseline/active 双快照与 `bindTheme`/`bindDialog`；mobile+leanback `BaseActivity` 在 `setContentView` 与 `initView` 后各绑定一次；`BaseBottomSheetDialog` 经共享通道绑定；`RecyclerView` 动态 child 自动绑定。
- 安全与性能：默认 profile 时零遍历零改写；仅重写与冻结基线精确同色且角色无歧义的颜色；状态化 ColorStateList 用公开构造器重建并保留状态 alpha；播放器/媒体/品牌/健康子树豁免。
- 设备证据：dev3 `192.168.50.3:5559`（API 28）mobile 覆盖安装后，默认路径无 binder 活动、`FATAL EXCEPTION=0`；注入 profile 后 `bound=10`、6–7ms，列表滚动与对话框正常。
- 本轮修复的设备专属缺陷：API 28 上 `Class.getRecordComponents()` 不存在曾导致启动崩溃，已移除；`ColorStateList.createFromXml()` 的平台解析器限制已改用公开构造器规避。
- 回滚锚点：回退阶段 J 提交即恢复 Layer 1 静态主题；v2 profile 数据保留但不再生效。

### 阶段 I（Layer 2A：B-safe 16 槽 profile 与 resolver）实施记录（2026-09-21）

- 任务：`L2A-THEME-PROFILE-20260921`；完整契约、证据与残余风险见 `docs/THEME-CUSTOMIZATION-LAYERS-20260921.md` 第 4.8 节，本文件不重复细节。
- 变更：新增 `ThemeProfile`/`ThemeProfileCodec`/`ThemeProfileValidator`/`ThemeProfileStore`，`ThemeResolver` 增加 profile 重载与 last-good 回退，`ThemeTokens` 增加 `dialogOpacity` 分量，`Backup.APP_PREFS` 纳入 `theme_mode` 与 profile 三键。
- 关键不回归约束：空 profile 的解析结果与不传 profile 逐字段相同；用户未覆盖的槽一律继承内置 token 或 seed 结果，现有页面视觉与 Layer 1 完全一致。
- 自动化证据：mobile/leanback 各 69 项定向单测 0 失败；`scripts/check_ui_tokens.sh --strict` 输出 `violations=0 legacy=0`、38 组对比度 0 失败。
- 边界：本轮不接线编辑器、不修改 `ThemeController` 与任何布局/资源；profile 只有在 Layer 2B 接线后才会影响界面。
- 回滚锚点：回退阶段 I 提交即回到 Layer 1 的纯 `theme_color` 路径，不涉及数据库迁移。

### 阶段 A 实施记录（2026-09-20）

- 新增资源：`webhtv_tokens.xml`（light/`values-night` 各 49 色）、attrs、dimens、type、shapes、styles、4 个交互 selector。
- 新增 Java：`ThemeMode`、`ThemeSeed`、不可变 `ThemeTokens`、19 组配对的 `ThemeContrast`、Tonal Spot `ThemeResolver`、只读控制器与 Web 快照 bridge。
- 兼容偏差：当前 `dev3` 基线没有设计盘点中提到的 `SiteDialogTheme`/`ColorRoles` 调用点，因此阶段 A 保持 resolver 独立、不强行制造业务接线；显式 seed 使用 Material 1.14 官方 `SchemeTonalSpot` 生成完整 M3 surface container 角色。
- 静态证据：`scripts/check_ui_tokens.sh --baseline` 输出 378 个布局、549 个 drawable、47 个 color state list；38 组对比度全部通过，最低为 `outline/surface=4.28:1`。
- 测试证据：`ThemeResolverTest`、`ThemeContractTest`、`UiStyleSourceTest` 共 13 项通过；mobile/leanback arm64 debug Java 编译通过。
- 回滚锚点：本阶段只新增 token/测试/脚本，不接现有页面；直接回滚阶段 A 提交即可恢复阶段开始前的运行行为。

### 阶段 B 实施记录（2026-09-21）

- 迁移范围：137 个 `dialog_*.xml`、19 个设置页布局、16 个 `dialog_*` 状态列表和 63 个 Java 调用点；`Theme.WebHTV.LightDialog`/`ThemeOverlay.WebHTV.LightDialog` 只保留别名。
- 设计修正：共享组件样式统一消费 Material `?attr/color*`，Dialog 主题与 overlay 使用固定语义资源，避免在主题定义中自引用导致 `Theme.AppCompat` 校验失败；TV 只保留深色 token 覆盖。
- 静态证据：`scripts/check_ui_tokens.sh --stage B` 输出 `violations=0 legacy=0`，38 组对比度全部通过；`git diff --check` 通过。
- 自动化证据：`ThemeResolverTest`、`ThemeContractTest`、`DialogRoundedCornerSourceTest`、`UiStyleSourceTest` 全部通过；mobile/leanback arm64 debug Java 编译通过。
- 设备证据：dev3 `192.168.50.3:5559` 覆盖手机浅色/深色，均可进入去广告规则管理 Dialog，且 `FATAL EXCEPTION` 为 0；截图见 `/tmp/webhtv-b-mobile-dialog-light-final.png`、`/tmp/webhtv-b-mobile-dialog-dark-final.png`。
- 回滚锚点：仅回滚阶段 B 提交即可恢复阶段 A 的页面视觉与旧 LightDialog 别名行为，不涉及数据或播放器内核。

### 阶段 C 实施记录（2026-09-21）

- 迁移范围：首页、搜索、历史、收藏、站点、推荐、剧集、EPG、实验室配置等列表/卡片布局；共迁移 71 个 `adapter_*`/`item_*`/`view_empty`/`view_progress` 资源文件。
- 阶段边界：检查器将 `adapter_tmdb_*`、`adapter_player_osd` 等详情与播放器控制文件保留给阶段 D，避免跨阶段提前修改。
- 静态证据：`scripts/check_ui_tokens.sh --stage C` 输出 `violations=0 legacy=0`，38 组对比度全部通过；`git diff --check` 通过。
- 自动化证据：`UiStyleSourceTest`、`ThemeResolverTest`、`ThemeContractTest` 全部通过；mobile/leanback arm64 debug Java 编译通过。
- 设备证据：dev3 `192.168.50.3:5559` 重新覆盖安装后进入去广告规则管理 Dialog，列表与卡片正常显示，`FATAL EXCEPTION=0`；截图见 `/tmp/webhtv-c-ad-dialog.png`。
- 回滚锚点：仅回滚阶段 C 提交即可恢复阶段 B 的列表与卡片视觉，不影响阶段 A/B 的 token 与 Dialog 契约。

### 阶段 D 实施记录（2026-09-21）

- 迁移范围：TMDB 详情、人员、搜索条目、推荐/剧集/照片卡片、详情图标与形状资源，以及 `view_player_osd`、`adapter_player_osd`、移动端/电视端播放控制样式。
- 播放器隔离：新增 `webhtv_selector_tmdb_*`、`webhtv_tmdb_button_*`，`player_control_text`、`display_option_text`、`selector_display_option` 与旧 `display_option_*` 颜色全部改为消费 `playerControl*`/`focus` 语义 token；测试断言 `playerControlActive` 不等于 `focus` 或 `primary`。
- 静态证据：`scripts/check_ui_tokens.sh --stage D` 输出 `violations=0 legacy=0`，38 组对比度全部通过；`git diff --check` 通过。
- 自动化证据：`UiStyleSourceTest` 新增播放器隔离断言后与 `ThemeResolverTest`、`ThemeContractTest` 共 16 项全部通过；mobile/leanback arm64 debug Java 编译通过。
- 设备证据：dev3 `192.168.50.3:5559` 进入 TMDB 详情并播放第一集，进入 `VideoActivity` 后 OSD 正常显示、控制层可用、`FATAL EXCEPTION=0`；截图见 `/tmp/webhtv-d-detail2.png`、`/tmp/webhtv-d-player-osd.png`。
- 回滚锚点：仅回滚阶段 D 提交即可恢复阶段 C 的详情与播放器控制视觉，不涉及解码、渲染、字幕、音轨或网络路径。

### 阶段 E 实施记录（2026-09-21）

- 迁移范围：`assets/css/ui.css` 建立 `--webhtv-*` 基准 token、light/dark 与显式 `data-theme` 切换，旧 `--md-*` 保留为兼容别名；reader 与 Eclipse WebHome 页面将样式色值迁移为页面受控变量。
- 原生桥接：`theme.info` 新增只读 `tokens` 字段，由 `ThemeWebBridge.snapshotJson(ThemeController.current())` 生成；Eclipse 首页/详情调用 `applyNativeTokens` 后设置 `data-theme-source="native"`，不提供写回原生主题的入口。
- 静态证据：修正检查器使其真正扫描 `assets/**/*.{css,html}`；`scripts/check_ui_tokens.sh --stage E` 输出 `violations=0 legacy=0`、allowlist 2 项（reader TTS 主题数据与 Eclipse 占位海报渐变），对比度 38 组全部通过。
- 自动化证据：新增 `WebThemeTokenSourceTest` 并连同 `WebTheme*`、`Theme*` 测试全部通过；mobile/leanback arm64 debug Java 编译通过。
- 设备证据：dev3 `192.168.50.3:5559` 覆盖安装后启动 `HomeActivityCurrent`，`FATAL EXCEPTION=0`；截图见 `/tmp/webhtv-e-home.png`。管理页与 reader 的真实浏览器截图仍作为最终人工验收项记录。
- 回滚锚点：仅回滚阶段 E 提交即可恢复阶段 D 的原生视觉与旧 Web CSS；不影响原生 token、播放器或数据。

### 阶段 F 实施记录（2026-09-21）

- 静态清理：将 `about_*`、`selector_card`、`selector_*`、`site_button_*`、`sync_device_*` 等 13 个颜色状态列表，以及 lab/cat-web/reader/remote-trust/TV video 等 6 个布局中的业务色迁到共享语义 attr。
- 资产豁免：为 189 个无法在阶段 F 安全语义化的历史 drawable 逐文件登记 allowlist，分类为品牌/图标矢量、lab 开发工具色、播放器/沉浸层半透明覆盖、旧状态列表和待拆分的旧 shape；不使用目录通配，新增文件仍会被 strict 检查拦截。
- 静态证据：`scripts/check_ui_tokens.sh --strict` 输出 `violations=0 legacy=0`、`hex_layouts=0 hex_drawables=0 hex_colors=0`、allowlisted=191，38 组对比度全部通过；`git diff --check` 通过。
- 自动化证据：`UiStyleSourceTest`、`WebThemeTokenSourceTest`、`ThemeResolverTest`、`ThemeContractTest` 全部通过；mobile/leanback arm64 debug Java 编译通过。
- 高级主题：按设计文档继续延后，需独立审批；本阶段没有引入可写主题编辑器、远程 CSS 覆盖或新的原生主题写入接口。
- 回滚锚点：仅回滚阶段 F 提交即可恢复阶段 E 的残余 drawable 样式与检查器 allowlist；不涉及数据、播放器内核或依赖。

### 验收契约补强（2026-09-21）

- 新增 `UiLayoutSourceTest`：固化 `adapter_tmdb_recommendation`、`adapter_tmdb_rail_item` 海报比例，`adapter_tmdb_recommendation_landscape`、`adapter_tmdb_video`、`adapter_episode_card` 横向比例，TV 卡片圆角族，以及卡片文字 scrim。
- 新增卡片圆角 dimens：`webhtv_card_radius_default=8dp`、`webhtv_card_radius_large=12dp`，TMDB 卡片、TV 剧集卡片和追更卡片改引共享 dimen。
- 补齐 `--webhtv-surface-variant` 的 light/dark 与 manage 页面值，修复旧 `--md-surface-variant` 悬空引用。
- 浏览器截图：Chromium 1600x1200 已生成 `index/manage/reader/webhome/webhome-detail` 的 light/dark 截图于 `~/webhtv-shots/`；reader 在浏览器预览下依赖原生 bridge 占位，最终验收仍需设备内 WebView 场景。
- 全量回归：`testMobileArm64_v8aDebugUnitTest` 4775 项中仅剩 `TmdbDetailActivityLayoutTest` 对卡片圆角的 raw `8dp` 期望未同步到 `@dimen/webhtv_card_radius_default`；该 source contract 已改为共享 dimen 后单测通过，全量回归随后复核。

---

## 0. 结论摘要

1. **统一基础采用 Material 3，不另造一套视觉语言。** 项目已依赖 Material Components 1.14.0，`mobile` 已使用 Material 3 Dynamic Colors/DayNight，`leanback` 已使用 Material 3 Dark，内置 Web CSS 也以 `--md-*` token 命名。继续另建一套设计语言会把问题从“样式分叉”扩大为“两套设计系统并存”。
2. **问题不是“主色不对”，而是“语义层缺失”。** 当前颜色直接写在布局、drawable、颜色状态列表和 Java 代码中；同一个“次要文字”至少有 `#5F6368`、`#666666`、`#8A8F98`、`@color/white_70`、`?attr/colorOnSurfaceVariant` 等表达。只要没有语义 token，下一次新增页面仍会复制新的固定颜色。
3. **第一阶段不重启 TweakCN 风格主题编辑器。** 历史任务已经有可回退的 profile/editor 实现记录，但它被整体回退过；当前最紧迫的是收敛现有 UI，而不是先增加可编辑主题面。建议先把主题解析器收敛为 M3 语义 token，把编辑器降为后续可选阶段，并复用已验证的迁移/导入/回滚设计。
4. **手机与电视共享语义，不共享尺寸和交互。** 颜色、字体层级、形状、间距的“角色”共享；按钮焦点缩放、焦点环、安全区、列表密度、播放器 OSD 仍按 `mobile`/`leanback` 分 flavor 实现。
5. **网页纳入同一语义体系，但保持独立运行边界。** `app/src/main/assets/css/ui.css` 改为消费 `--webhtv-*` 语义变量；内置页面不允许再散落原始 hex。WebTheme V2 远程主题只能读取受控 token，不能覆盖原生颜色或调用原生主题写入接口。
6. **默认策略：电视深色优先，手机跟随系统；壁纸只提供种子，不直接充当文字/表面颜色。** 这同时符合 Android TV 的深色观影建议、Material 3 的 tonal palette 规则，以及项目当前 `Setting.getDynamicColor()`/壁纸模型。
7. **优先解决顺序：弹窗和设置页 → 列表/卡片/首页 → 详情页与播放器控制层 → Web 页面 → 可选高级主题编辑。** 这按“用户最容易看到、分叉最多、迁移风险最低”排序，而不是按模块数量排序。

---

## 1. 背景、目标与边界

### 1.1 用户观察到的问题

当前页面同时存在几类视觉语言：

- 手机设置弹窗、单选框、输入框偏浅色 Material 3，使用 `#202124`、`#5F6368`、`#1A73E8`、`#E8F0FE` 等固定色；
- 电视端在保留壁纸背景的页面使用深色玻璃面板，在部分弹窗又切换到浅色 palette；
- 首页、TMDB 详情、剧集卡片、音频页、实验室页分别定义了自己的深色背景和高亮色；
- `lab` 入口使用 `#111318`、`#1B1B1F` 等独立色板；
- Web 管理页使用 `#2563EB`、`#F6F7FB`，WebHome 内置页使用绿色 `#006C4C`，用户壁纸又提供另一组种子色；
- 选中态、聚焦态、按下态和禁用态在不同页面使用不同的色值或透明度，电视端尤其容易出现“同样是焦点，有时是黄色、有时是蓝色、有时只有文字变亮”。

这些不是单个页面的 bug，而是缺少**全局语义层**后的自然结果。

### 1.2 设计目标

1. 建立一份跨端、跨页面、跨 Web/原生的语义 token 单一事实来源。
2. 明确背景、文字、边框、强调色、焦点、危险、成功、遮罩、播放器控制层的角色和对比度要求。
3. 统一按钮、输入、列表项、卡片、标签、开关、弹窗、底部面板、加载态、空态和错误态的视觉规则。
4. 保留现有功能、数据、播放器内核和业务行为；不因换肤改变可见性、权限、数据源或播放逻辑。
5. 让 Android 9 等没有系统动态色的设备也能通过 Material Color Utilities fallback 获得一致主题。
6. 为电视端保留大屏专属的焦点、安全区、密度和可读性规则，而不是把手机样式简单放大。
7. 让内置 Web 页面与原生页面共享同一套颜色/形状/间距语义，避免 Web 与 App 之间继续漂移。
8. 给出可逐阶段提交、逐阶段回滚、可通过静态检查和设备场景验证的迁移路线。

### 1.3 非目标

- 不在第一阶段实现公开主题市场、远程字体或签名主题包。
- 不引入 Compose、第三方 UI 框架或新的视觉依赖；当前项目是 View/XML 体系。
- 不用 WebView 替换原生设置页、详情页或播放器控制层。
- 不改变播放器解码、渲染、字幕、弹幕、音轨、倍速、代理或网络行为。
- 不改变 TMDB 详情模式、首页内容源、站点弹窗业务顺序或数据契约。
- 不在本文阶段删除现有 drawable、布局或颜色资源；删除属于迁移完成后的独立清理阶段。
- 不把“电视端永远暗色、手机永远亮色”写成硬编码；它只是默认产品策略，最终由主题模式解析。

---

## 2. 现状盘点：问题不是感觉，而是可量化的分叉

本节全部为 2026-09-20 对当前 `dev3` 工作树的只读事实；命令以 `app/src/**/res`、`app/src/**/java`、`app/src/main/assets` 为范围，排除 `build/`。

### 2.1 资源规模与固定色分布

| 指标 | 当前值 | 说明 |
| --- | ---: | --- |
| 布局文件 | 382 | `main`、`mobile`、`leanback` 三个来源集 |
| drawable 文件 | 641 | 其中 212 个包含原始 hex 颜色 |
| 颜色状态列表 | 77 | 大量按钮/文字/描边分别维护 |
| values XML | 24 | 含 `main`、`mobile`、`leanback`、`values-night` |
| `<style>` 定义 | 65 | `main` 31、`mobile` 15、`leanback` 13、`lab` 3、`styles_disc_menu` 1，另有 2 个 v27 |
| 布局中含 hex 的文件 | 120 | 固定色直接进入页面布局 |
| Java 中含颜色字面量/`Color.parseColor` 的文件 | 107 | 固定色绕过资源系统 |
| UI Java 中 `setBackgroundColor`/`setTextColor`/`setTint`/`getColor` | 517 处 | 动态样式与布局样式并行 |
| XML 颜色/系统白黑引用匹配 | 1,888 处匹配（1,887 行）、620 个文件 | 统计包含 values、layout、drawable、color 等资源；不是全部都应删除，但说明分叉面很大 |
| 出现最多的固定文字色 | `#5F6368` 194 次、`#202124` 113 次 | 两者几乎构成了“手机浅色弹窗”的隐式主题 |
| 出现最多的强调色 | `#174EA6` 37 次、`#1A73E8` 30 次、`#0B57D0` 30 次 | 同一套蓝色被拆成多个作用不明的值 |
| 固定边框/分隔色 | `#DADCE0` 17 次、`#C8CDD2`、`#D2E3FC` 等 | 缺少统一的 outline/divider 语义 |

> 解释：1,888 处匹配这个数字包含 `@android:color/white`、透明色和播放器遮罩等合理用途，不能直接作为“技术债行数”承诺；它用于说明“固定色已经进入大量页面”，而不是要求一次性清零。

### 2.2 六类可见风格簇

| 风格簇 | 代表文件 | 观察到的颜色/表面 | 结果 |
| --- | --- | --- | --- |
| 手机浅色弹窗 | `app/src/main/res/values/styles.xml`、`app/src/main/res/color/dialog_*` | `#FFFFFF` 表面、`#202124` 主文字、`#5F6368` 次文字、`#1A73E8`/`#174EA6` 强调 | 在深色壁纸或电视上出现浅色孤岛 |
| 手机深色弹窗/面板 | `app/src/mobile/res/values/styles.xml`、`shape_dialog_glass_panel.xml` | 深蓝紫玻璃渐变、`#D6282955`、`#E62F315E` | 与浅色弹窗并存，语义不一致 |
| 电视深色面板 | `app/src/leanback/res/values/styles.xml`、`shape_display_dialog_panel.xml` | `display_dialog_panel_bg`、白/白 70% 文字、`#2196F3` 强调 | 电视端内部相对统一，但与手机 token 不共享 |
| TMDB 详情/卡片 | `activity_tmdb_detail.xml`、`adapter_episode_card.xml`、TMDB adapter | `#0F141A`、`#141A20`、`#E6FFFFFF`、`#3A4652` | 页面自建深色体系，难以响应主题 |
| 实验室入口 | `Theme.App.Lab*`、`lab_colors.xml` | `#111318`、`#1B1B1F`、`#2196F3` | 与主应用主题脱节，切换主题不会同步 |
| 内置 Web | `assets/css/ui.css`、`manage.html`、`reader.html`、`webhome/eclipse*.html` | `--md-primary: #006C4C`、管理页 `#2563EB`、reader/eclipse 内联 hex | Web 内部也分出“管理页蓝、WebHome 绿、页面自带色” |

补充事实：

- `app/src/main/res/values/styles.xml` 的 `Theme.WebHTV.LightDialog` 固定写入了 `#202124`、`#5F6368`、`#8A8F98`、`#1A73E8`，而 `app/src/leanback/res/values/styles.xml` 的 `ThemeOverlay.WebHTV.LightDialog` 已在 `2456a3af52` 改成深色面板；同名主题在两个 flavor 表达两套语义。
- 已有 `DialogRoundedCornerSourceTest` 只保证 22dp 圆角，不保证颜色、文字、状态或焦点一致；它能作为“形状回归测试”的起点，但不能替代颜色 token 测试。
- `app/src/main/assets/css/ui.css` 顶层已有 Material 风格变量，但管理页、WebHome、reader 页面又覆盖或追加变量；说明 Web 端已经证明“token 化可行”，只是没有和原生统一。

### 2.3 当前主题链路的事实

| 事实 | 位置 | 设计影响 |
| --- | --- | --- |
| `Setting.getThemeColor()` 只保存一个整数；`-1` 关闭，`0` 表示跟随壁纸，其他值为种子色 | `app/src/main/java/com/fongmi/android/tv/setting/Setting.java` | 可以保留为用户入口，但不能再让页面直接消费原始整数值 |
| `BaseActivity.enableDynamicColor()` 只在系统动态色可用时生效；Android 9 等设备会直接跳过 | `app/src/mobile/java/.../BaseActivity.java` | 必须有 Material `ColorRoles`/fallback，而不能把 DynamicColors API 当唯一实现 |
| `SiteDialogTheme` 已经用 `ColorRoles` 和 `getSurfaceContainerFromSeed` 生成浅/深配对色 | `app/src/mobile/java/.../SiteDialogTheme.java` | 这是项目内已验证的“低版本 fallback”参考，应抽取为通用 resolver，而不是每个弹窗复制 |
| 当前移动主题对话框提供 14 个固定种子色，包含关闭/跟随壁纸/自定义 | `mobile`/`leanback` `ThemeDialog.java` | 第一阶段保留入口语义，实际颜色由 resolver 转为语义 token；高级编辑器推迟 |
| 历史提交 `341da7f9d0`、`9c77828cfd`、`cb7229964f`、`32ace88636` 实现过 profile/editor/TV catalog，后续被 `be1b02e06b` 回退，`0503f8e3cf` 又从验证历史恢复，`8523d049e2` 增加默认关闭开关 | 主题包与 `docs/theme-color-system-design-20260907.md` 的历史 | 不能重新做一遍已有设计；后续若恢复，应复用已审查的迁移、导入、回滚边界；当前 `dev3` 工作树实际没有 `theme` 包，说明跨分支状态仍未收敛 |
| WebTheme V2 已有 Manifest、页面白名单、回滚、缓存和能力注册 | `app/src/main/java/com/fongmi/android/tv/web/**` | 远程网页主题应与原生 token 解耦；只允许受控只读 token 快照 |

### 2.4 当前组件形状与状态分叉

| 组件 | 当前情况 | 统一目标 |
| --- | --- | --- |
| 主要按钮 | `Widget.Material3.Button`、`Widget.Material3.Button.TonalButton`、`Widget.WebHTV.LightDialog.Button.Tonal` 等并存 | 一套角色：filled / tonal / outlined / text / icon |
| 输入框 | `Widget.Material3.TextInputLayout.OutlinedBox` 与 `Widget.WebHTV.LightDialog.Input` 并存 | 统一 outlined 输入，颜色与错误态来自同一组 token |
| 卡片 | `shape_item.xml`、`selector_item.xml`、TMDB 卡片、音频卡片各自定义 | 统一 surface/card、圆角、描边、焦点和选中规则 |
| 弹窗 | `LightDialog.create`、`BaseAlertDialog`、Material builder、自定义全屏 Dialog 并存 | 统一四类弹窗模板：alert、single choice、form、full-screen/bottom sheet |
| 电视焦点 | `selector_item`、`selector_video_item`、`selector_group_button`、黄色选中色等并行 | 统一 focus ring、scale、选中语义；颜色不得成为唯一状态信号 |
| 分隔/边框 | `strokeColor`、`#DADCE0`、`white_15`、`display_option_stroke_default` 等 | 统一 outline / outlineVariant / divider 三档 |
| 危险/成功/警告 | `B3261E`、`site_health_*`、`#0B8043`、`#AAFFAA` 等 | 统一 error/success/warning/info 语义及容器色 |

---

## 3. 业界主流实现与证据记录

本节是设计决策的证据基础。访问日期均为 **2026-09-20**，仓库路径/URL、支持的事实、适用性和决策影响逐项记录；链接正文已实际读取，不是只看搜索结果摘要。

| 来源与 revision/访问日期 | 等级 | 支持的事实 | WebHTV 适用性与决策影响 |
| --- | --- | --- | --- |
| Material Design 3, Color roles, <https://m3.material.io/styles/color/roles>，2026-09-20 读取 | A（官方规范） | 颜色角色是 UI 元素与“颜色应该去哪里”之间的连接组织；语义角色比单色值更重要 | 采用 `primary`、`on*`、`surface`、`outline`、`error` 等角色命名；禁止页面直接消费原始 seed |
| Material Design 3, Buttons, <https://m3.material.io/components/buttons/overview>，2026-09-20 读取 | A | 按钮用于促使用户行动；不同强调层级需要不同按钮类型 | 明确 filled/tonal/outlined/text/icon 五种角色及使用场景，替代当前多种自定义 button style |
| Material Design 3, Dialogs, <https://m3.material.io/components/dialogs/overview>，2026-09-20 读取 | A | Dialog 是用户流程中的重要提示，应使用户对信息作出行动 | 统一 alert、single-choice、form、full-screen 四类；限制按钮数量和焦点顺序 |
| Material Design 3, Shape, <https://m3.material.io/styles/shape/shape-scale-tokens>，2026-09-20 读取 | A | Shape scale 用一组级别表达圆角程度，并传达状态和品牌 | 保留现有 22dp 大弹窗圆角作为 `extraLarge`，但建立 4/8/12/16/22 的 token 尺度 |
| Material Design 3, Elevation, <https://m3.material.io/styles/elevation/overview>，2026-09-20 读取 | A | Elevation 是界面沿 z 轴的相对距离，用来表达层级 | 用 tonal surface + shadow/overlay 表达层级，禁止每页自创玻璃渐变替代所有层级 |
| Android Developers, Dynamic Color, <https://developer.android.com/develop/ui/views/theming/dynamic-colors>，2026-09-20 读取 | A（平台文档） | Dynamic Color 从 Android 12 开始提供；app/activity 可通过 `applyToActivitiesIfAvailable()` 注册；不同用户变体仍访问同一组 token | 系统动态色作为可选输入，不是唯一输入；Android 9 或不支持时用 Material Color Utilities/`ColorRoles` fallback，页面仍只读同一 token |
| Android TV, Color system, <https://developer.android.com/design/ui/tv/guides/styles/color-system>，2026-09-20 读取 | A（官方 TV 指南） | 颜色方案应由五组关键色/tonal palette 生成；Primary 用于重要组件、激活态和 elevated surface 着色；TV 不支持壁纸动态方案，建议使用内容驱动的 key color；深色主题适合影院体验 | 电视端默认深色、手机跟随系统；TV 不从壁纸取动态色，而允许从当前海报/背景内容产生受限 key color，且必须经过对比度校验 |
| Android TV, Design for TV, <https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv>，2026-09-20 读取 | A | TV 主要依赖 D-pad；必须在按钮按下时提供即时、清晰反馈；TV 常是共享设备 | 焦点、选中、按下必须有独立的视觉和可访问反馈；共享设备场景不得把隐私信息暴露在无遮罩的公共区域 |
| Android TV, Buttons, <https://developer.android.com/design/ui/tv/guides/components/buttons>，2026-09-20 读取 | A | 六类按钮中 filled 用于重要最终动作，outlined 用于次级动作；按钮容器可在焦点时以约 1.1x 缩放并保持内边距；图像按钮需要渐变遮罩保证文字对比度 | 电视 filled/outlined 的层级和 1.1x 焦点缩放进入规范；图像卡片必须有 scrim，不得只靠图片对比 |
| Android TV, Cards, <https://developer.android.com/design/ui/tv/guides/components/cards>，2026-09-20 读取 | A | 卡片常用 16:9、1:1、2:3；横向卡片间距推荐 20dp；图片上的文字需要半透明黑色渐变遮罩 | TMDB 海报、剧集卡片和推荐卡片统一比例/间距/遮罩规则；避免每页自己定义不同海报尺寸 |
| Android TV, Layouts, <https://developer.android.com/design/ui/tv/guides/styles/layouts>，2026-09-20 读取 | A | 设计基准 960x540dp；1080p asset；左右约 5% 安全区（48dp/58dp 两套描述）；12 列、20dp gutter；不要裁切背景，只保证内容安全 | TV 共享外层使用统一安全区和 12 列/20dp 规则；手机使用 16dp 内容边距，不照搬 TV 数值 |
| WCAG 2.2 SC 1.4.3 Contrast (Minimum), <https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html>，2026-09-20 读取 | A（国际规范） | 普通文本至少 4.5:1；大文本至少 3:1；非文本 UI 状态还由相邻标准覆盖 | 作为 token resolver 的最低可读性门槛；正文 4.5:1、大字/图标/焦点环 3:1，并在静态检查中覆盖关键配对 |
| Apple Human Interface Guidelines, tvOS, <https://developer.apple.com/design/human-interface-guidelines/tvos>，2026-09-20 读取 | C（平台设计指南，但页面正文需 JavaScript；本次未取得完整正文） | 仅验证官方 tvOS HIG 页面存在；未把未读到的具体规则当成决策依据 | 作为“电视是远距离、焦点驱动、共享设备”的行业方向参考，不据此引入平台特有控件或视觉方案 |

### 3.1 方案比较

| 方案 | 优点 | 缺点/风险 | 结论 |
| --- | --- | --- | --- |
| A. 不改，继续按页面修颜色 | 改动小，短期最快 | 分叉继续增长；每次新增页面都要重新猜颜色；主题切换永远不完整 | 拒绝 |
| B. 直接复用 TweakCN/shadcn 编辑器方案 | 编辑器体验完整，已有设计文档 | 当前痛点是核心一致性；历史实现曾被回退，跨分支状态未收敛；会把风险和范围转向配置模型、导入和持久化 | 暂缓，作为核心 token 完成后的独立高级阶段 |
| C. 以 Material 3 语义 token 为底座，增加 WebHTV 业务扩展 token；原生与 Web 共用命名 | 复用现有依赖；TV/手机/网页都能映射；Android 9 fallback 路径清晰；可渐进迁移 | 需要一次性建立检查脚本和迁移规范；存量固定色需要分批处理 | **采用** |
| D. 自建完整设计语言和组件库 | 品牌自由度高 | 与现有 Material 控件、对话框、ViewBinding 冲突；维护成本和回归面最大 | 仅保留品牌扩展 token，不另建控件库 |

### 3.2 决策

采用 **C 方案：Material 3 语义底座 + WebHTV 扩展 token + 分阶段迁移**。

- Material 3 提供颜色角色、按钮层级、shape/elevation、动态色和 View 组件。
- WebHTV 只新增业务语义：`success`、`warning`、`focus`、`scrim`、`playerControl`、`playerControlActive`、`healthGood/Warn/Bad`。
- 所有页面只消费语义 token；原始 seed、壁纸色和用户选择只存在于 resolver 输入层。
- 远程 WebTheme 只读 token 快照；不允许远程页面写回原生主题。

---

## 4. 目标架构

```text
用户偏好 / 壁纸 / 内容海报
              │
              ▼
     ThemeSeedResolver
  （动态色、ColorRoles fallback、内容 key color）
              │
              ▼
       ThemeTokens（单一事实来源）
  color / type / shape / spacing / elevation / motion
              │
      ┌───────┼───────────────────────────┐
      ▼       ▼                           ▼
 Android attrs/styles      内置 Web CSS variables      WebTheme bridge
 color state lists         --webhtv-*                （只读快照）
      │                       │                           │
      └───────────┬───────────┴──────────────┬────────────┘
                  ▼                          ▼
          原生页面与对话框               内置/受信主题页面
```

### 4.1 分层职责

| 层 | 职责 | 不应做的事 |
| --- | --- | --- |
| Seed 输入层 | 读取当前主题模式、用户 seed、壁纸色、内容 key color | 不直接把 seed 画到背景或文字上 |
| Resolver | 生成 light/dark 两套 M3 角色；校验对比度；缺角色时用 `ColorRoles`/`MaterialColors` 补全；输出不可变 token | 不读取业务状态、不执行页面逻辑 |
| Android 资源层 | 将 token 映射到 `?attr/color*`、style、ColorStateList、dimen、shape | 不在 layout/drawable 里写业务专属固定色 |
| Web 资源层 | 将 token 映射到 `--webhtv-*` CSS 变量，提供 light/dark 和 `data-theme` 覆盖 | 不读取原生设置，不直接修改原生主题 |
| 组件层 | 用统一 style/组件模板消费 token，提供 focus/selected/disabled/error 状态 | 不自行新增 palette 或另定义状态色 |
| 页面层 | 选择组件与布局，不定义颜色 | 不绕过 token 直接写 hex |

### 4.2 主题模式解析顺序

1. 读取用户主题模式：`system`、`light`、`dark`。
2. `mobile` 的 `system` 跟随系统；`leanback` 默认 `dark`，除非用户明确选择全局 light。
3. 读取主题输入：
   - 用户显式 seed：用户选择的颜色；
   - 跟随壁纸：从当前壁纸色或内置壁纸的稳定色取值；
   - TV 内容驱动：仅在明确启用时，从当前海报/背景提取 key color；
   - 无输入：使用 WebHTV 品牌默认 seed。
4. 用 Material `ColorRoles`/`MaterialColors` 生成关键角色。
5. 对 `on*`、`outline`、`focus` 做对比度修正。
6. 输出 light/dark 两套 token；页面只取当前模式的一套。
7. 用户切换主题或模式时，沿用现有 `RefreshEvent.THEME` 重建宿主，不新增页面级主题订阅。

---

## 5. 颜色系统

### 5.1 命名原则

- 命名表达**用途**，不表达颜色：用 `onSurfaceVariant`，不用 `grey600`。
- 一个语义只允许一个 token：次要文字统一 `onSurfaceVariant`，不再区分 `#5F6368` 与 `#666666`。
- 成对生成：每个容器色必须同时定义 `on*` 前景；不允许 UI 只拿到背景色后自行猜文字色。
- 固定色只允许出现在四类位置：resolver 默认 seed、播放器画面遮罩、品牌资产、临时兼容映射；后两类必须在静态检查的 allowlist 中并写明原因。

### 5.2 核心 token（冻结默认 palette）

默认 palette 固定为下表值。`primary` 等主色允许被用户 seed/动态色替换，但替换后必须由 resolver 重新生成配对角色并满足 5.3 的对比度门槛；以下值是可执行实现与 JVM 测试的 baseline，不是“大约参考”。

| Token | 用途 | Light 默认值 | Dark 默认值 | 必须配对/约束 |
| --- | --- | --- | --- | --- |
| `primary` | 主操作、选中、激活态、焦点环 | `#0B57D0` | `#A8C7FA` | 与 `onPrimary` 成对 |
| `onPrimary` | 主操作文字/图标 | `#FFFFFF` | `#062E6F` | 正文 ≥4.5:1 |
| `primaryContainer` | 次级主操作、选中容器 | `#D3E3FD` | `#0842A0` | 与 `onPrimaryContainer` 成对 |
| `onPrimaryContainer` | 容器文字/图标 | `#041E49` | `#D3E3FD` | ≥4.5:1 |
| `secondary` | 过滤、辅助操作 | `#5B5F63` | `#C6C6C9` | 与 `onSecondary` 成对 |
| `onSecondary` | 辅助操作前景 | `#FFFFFF` | `#2F3133` | ≥4.5:1 |
| `secondaryContainer` | chip 选中、轻量强调 | `#DFE2E6` | `#44474A` | 与 `onSecondaryContainer` 成对 |
| `onSecondaryContainer` | 容器前景 | `#191C1E` | `#E2E2E6` | ≥4.5:1 |
| `tertiary` | 高注意但非主操作的强调 | `#00639B` | `#8ECDFF` | 限制使用 |
| `onTertiary` | tertiary 前景 | `#FFFFFF` | `#003353` | ≥4.5:1 |
| `error` | 失败、危险主操作 | `#B3261E` | `#FFB4AB` | 与 `onError` 成对 |
| `onError` | 危险操作前景 | `#FFFFFF` | `#690005` | ≥4.5:1 |
| `errorContainer` | 错误提示面板 | `#F9DEDC` | `#93000A` | 与 `onErrorContainer` 成对 |
| `onErrorContainer` | 错误容器前景 | `#410E0B` | `#FFDAD6` | ≥4.5:1 |
| `success` | WebHTV 扩展：成功、健康 | `#146C2E` | `#8EDB9F` | 与 `onSuccess` 成对 |
| `onSuccess` | 成功前景 | `#FFFFFF` | `#003918` | ≥4.5:1 |
| `successContainer` | 成功提示面板 | `#C4EED0` | `#00522A` | 与 `onSuccessContainer` 成对 |
| `onSuccessContainer` | 成功容器前景 | `#00210B` | `#C4EED0` | ≥4.5:1 |
| `warning` | WebHTV 扩展：警告 | `#7A5900` | `#F2C66D` | 与 `onWarning` 成对 |
| `onWarning` | 警告前景 | `#FFFFFF` | `#402D00` | ≥4.5:1 |
| `warningContainer` | 警告提示面板 | `#FFDF9A` | `#5C4200` | 与 `onWarningContainer` 成对 |
| `onWarningContainer` | 警告容器前景 | `#261A00` | `#FFDF9A` | ≥4.5:1 |
| `surface` | 页面主表面/画布 | `#F8FAFD` | `#101418` | 可透出壁纸时需叠 scrim |
| `surfaceDim` | 低层级画布 | `#D8DAE0` | `#0B0E11` | — |
| `surfaceBright` | 高层级画布 | `#F8FAFD` | `#363A3F` | — |
| `surfaceContainerLowest` | 最低层容器 | `#FFFFFF` | `#0B0E11` | 卡片底色基准 |
| `surfaceContainerLow` | 工具栏/分组 | `#F2F3F9` | `#181C20` | — |
| `surfaceContainer` | 普通卡片、设置组 | `#ECEEF4` | `#1F2428` | — |
| `surfaceContainerHigh` | Dialog、浮层、面板 | `#E7E8EF` | `#2A2F34` | Dialog 默认 |
| `surfaceContainerHighest` | 输入框、前景浮层 | `#E1E2E9` | `#34393E` | 与 `onSurface` ≥4.5:1 |
| `onSurface` | 主要文字/图标 | `#1A1C1E` | `#E2E2E9` | ≥4.5:1 |
| `onSurfaceVariant` | 次要文字、提示、未激活图标 | `#44474F` | `#C4C6D0` | ≥4.5:1 |
| `outline` | 输入边框、按钮描边、重要分隔 | `#74777F` | `#8E9099` | 与 surface ≥3:1 |
| `outlineVariant` | 普通分隔、弱边框 | `#C4C6CF` | `#44474F` | 仅用于非关键分隔 |
| `inverseSurface` | Snackbar、反色表面 | `#2F3033` | `#E2E2E9` | 与 inverseOnSurface 成对 |
| `inverseOnSurface` | 反色表面前景 | `#F1F0F4` | `#2F3033` | ≥4.5:1 |
| `inversePrimary` | 反色表面上的主操作 | `#A8C7FA` | `#0B57D0` | — |
| `scrim` | Dialog/全屏遮罩 | `#000000` @ 32% | `#000000` @ 54% | 只做遮罩 |
| `shadow` | 阴影基色 | `#000000` | `#000000` | 深色下优先用 tonal surface |
| `focus` | TV 焦点环/焦点容器 | `#0B57D0` | `#A8C7FA` | 与所在背景 ≥3:1 |
| `focusScale` | 焦点容器缩放 | 1.0 | 1.1 | 仅 TV，保持内边距 |
| `playerControl` | 播放器控制默认前景 | `#FFFFFF` | `#FFFFFF` | 播放器画面专用 |
| `playerControlMuted` | 播放器次控制 | `#CCFFFFFF` | `#CCFFFFFF` | — |
| `playerControlActive` | 播放器控制选中 | `#FFD35C` | `#FFD35C` | 不用于普通 UI |
| `playerScrim` | 控制层后方遮罩 | `#000000` @ 48% | `#000000` @ 56% | 保证控制可读 |
| `healthGood` | 站点健康良好 | `#146C2E` | `#8EDB9F` | 等同 success 角色，可单独覆盖 |
| `healthWarn` | 站点健康警告 | `#7A5900` | `#F2C66D` | 等同 warning 角色，可单独覆盖 |
| `healthBad` | 站点健康失败 | `#B3261E` | `#FFB4AB` | 等同 error 角色，可单独覆盖 |
| `overlayLight` | 图片浅色叠加 | `#FFFFFF` @ 8% | `#FFFFFF` @ 8% | 渐变/图片专用 |
| `overlayDark` | 海报压暗、图片文字底 | `#000000` @ 32–72% | `#000000` @ 40–80% | 文字必须落在 ≥4.5:1 区域 |

#### 5.2.1 默认对照度（由上述值计算，作为测试断言）

| 配对 | Light | Dark | 门槛 |
| --- | ---: | ---: | ---: |
| `onPrimary` / `primary` | 6.39:1 | 7.50:1 | 4.5:1 |
| `onPrimaryContainer` / `primaryContainer` | 12.57:1 | 7.04:1 | 4.5:1 |
| `onSurface` / `surface` | 16.34:1 | 14.35:1 | 4.5:1 |
| `onSurfaceVariant` / `surface` | 8.89:1 | 10.87:1 | 4.5:1 |
| `onSurface` / `surfaceContainerHigh` | 13.99:1 | 10.48:1 | 4.5:1 |
| `outline` / `surface` | 4.28:1 | 5.81:1 | 3:1 |
| `onSuccess` / `success` | 6.53:1 | 8.00:1 | 4.5:1 |
| `onWarning` / `warning` | 6.45:1 | 8.21:1 | 4.5:1 |
| `onError` / `error` | 6.54:1 | 7.72:1 | 4.5:1 |
| `playerControlActive` / `playerScrim` 区域 | ≥4.5:1 | ≥4.5:1 | 4.5:1 |

#### 5.2.2 需要同时进入契约测试的其余配对

下表同样由 5.2 默认值计算得到，必须写成 `ThemeContractTest` 的断言，不能只覆盖主色。

| 配对 | Light | Dark | 门槛 |
| --- | ---: | ---: | ---: |
| `onSecondary` / `secondary` | 6.44:1 | 7.66:1 | 4.5:1 |
| `onSecondaryContainer` / `secondaryContainer` | 13.18:1 | 7.24:1 | 4.5:1 |
| `onTertiary` / `tertiary` | 6.45:1 | 7.71:1 | 4.5:1 |
| `onErrorContainer` / `errorContainer` | 12.77:1 | 7.24:1 | 4.5:1 |
| `onSuccessContainer` / `successContainer` | 13.49:1 | 7.36:1 | 4.5:1 |
| `onWarningContainer` / `warningContainer` | 13.23:1 | 7.28:1 | 4.5:1 |
| `onSurface` / `surfaceContainerHighest` | 13.23:1 | 9.05:1 | 4.5:1 |
| `inverseOnSurface` / `inverseSurface` | 11.63:1 | 10.24:1 | 4.5:1 |
| `playerControlActive` / 纯黑底 | 14.72:1 | 14.72:1 | 4.5:1 |

> 说明：`outline` 与 surface 的 Light 值为 4.28:1，只要求满足 ≥3:1 的非文本门槛；如果某个页面把 `outline` 用作正文文字，必须改用 `onSurfaceVariant`，否则契约测试应失败。

### 5.3 对比度与可读性规则

Resolver 必须执行，静态检查必须验证：

- 普通正文/正文按钮：`on*` 与背景 ≥ 4.5:1。
- 大字（≥18sp 或 ≥14sp bold）、图标、焦点环、非文本 UI：≥ 3:1。
- 输入框 outline 与 surface ≥ 3:1；禁用态可低但必须仍可辨认。
- 焦点环、选中底色和选中文字必须同时可区分；不能只改变色相。
- 图片上的文字必须有 scrim/gradient；不得假设海报亮度足够。
- 玻璃/半透明面板只允许在其上叠加足够不透明的 scrim；透明度不能成为可读性的唯一保障。

### 5.4 壁纸与内容色的边界

- 壁纸色只作为 `seed`，不直接作为 `surface`、`onSurface` 或文字色。
- 内置壁纸的稳定色可继续从 `Setting.getBuiltInWallColor()` 获取，但必须经过 tonal/对比度转换。
- TV 内容驱动色只在内容页明确启用；提取失败时回退到默认 seed，不阻塞页面。
- 原始壁纸视觉可作为页面 canvas 的底图，但所有内容表面必须有不透明或可控的 surface 层。

---

## 6. 字体与排版

### 6.1 字体策略

- 继续使用系统字体族（Roboto/系统中文回退），不引入远程字体。
- 中文、英文、数字、标点使用统一的 fontFamily 与 fallback 顺序。
- 以 sp 为单位，尊重系统字体缩放；禁止布局里只给固定高度并裁剪多行文本。

### 6.2 字阶

| 角色 | 手机 | 电视 | 用途 |
| --- | --- | --- | --- |
| Display | 32–40sp | 40–48sp | 空态、重大结果 |
| Headline | 24–28sp | 28–32sp | 页面标题 |
| Title Large | 20–22sp | 24sp | 弹窗标题、区块标题 |
| Title Medium | 16sp | 18–20sp | 列表主标题 |
| Body Large | 16sp | 18sp | 正文 |
| Body Medium | 14sp | 16sp | 次要正文 |
| Label Large | 14sp | 16sp | 按钮、标签 |
| Label Small | 12sp | 14sp | 辅助信息、状态 |

规则：

- 电视端最小正文不小于 16sp，关键操作不小于 16sp；手机端最小正文不小于 12sp，正文优先 14/16sp。
- 标题行高与字号成比例；不能靠固定 layout_height 裁剪。
- 数字、时间、码率等可等宽显示，避免进度跳动。
- 可点击文字不要只用 10–11sp；如果空间不足，优先减少文案而不是继续缩小。

---

## 7. 形状、层级与动效

### 7.1 圆角尺度

| Token | 值 | 用途 |
| --- | ---: | --- |
| `shapeNone` | 0dp | 全屏面板边缘、画面遮罩 |
| `shapeExtraSmall` | 4dp | 标签、微小 chip |
| `shapeSmall` | 8dp | 列表项、输入框、普通按钮 |
| `shapeMedium` | 12dp | 卡片、分组容器 |
| `shapeLarge` | 16dp | 大卡片、底部面板顶部 |
| `shapeExtraLarge` | 22dp | Dialog、全屏浮层、现有已验证的大圆角 |
| `shapeFull` | 50% | 头像、圆形图标按钮 |

保留 22dp 作为大弹窗统一圆角，避免把已有已验证的 `DialogRoundedCornerSourceTest` 推回多套值。

**补充：密集海报网格的卡片为什么用 8dp 而不是 12dp（2026-10-13 审计后明确）。**

上表的 `shapeMedium` 12dp 是"卡片、分组容器"的默认值，**大卡片与分组容器照此执行**。但**密集海报网格里的内容卡**（`adapter_vod`、`adapter_tmdb_video`、`adapter_episode_card` 等）使用 `@dimen/webhtv_card_radius_default` = **8dp**，这是**有依据的取舍，不是漂移**：

- 这些卡片的选中/聚焦由**焦点环**表达，而环画在卡片**外边界**上，其外边界半径 = 卡片声明半径 + 环宽/2。声明 8dp 时外边界为 `8dp + w/2`，比卡片自身弧更大——沿角落对角线，卡片弧反而更靠外，**露出 1–2px 月牙**。
- 因此卡片半径与环几何是**耦合**的：改动卡片半径必须同步重算环几何并**重验月牙**。`TvFocusRingContractTest` 已把 8dp 固化为契约正是这个原因。
- 结论：**网格内容卡 = `shapeSmall` 8dp（本补充明确豁免上表的 12dp）；大卡片/分组容器 = `shapeMedium` 12dp。** 需要改网格卡半径时，按"改半径 → 重算环几何 → 重验月牙 → 同步契约"四步走，不要只改 dimen。

反向的一处偏离**已按本表修正**：输入框此前用 4dp（属 `shapeExtraSmall` 档），已改为 `shapeSmall` 8dp（`selector_dialog_input.xml`）——输入框没有上述耦合，属纯粹偏离。

### 7.2 Elevation / 表面层级

| 层级 | 表面 | 使用 |
| --- | --- | --- |
| 0 | `surface` | 页面 canvas、全屏背景 |
| 1 | `surfaceContainerLow` | 页面内分组、工具栏 |
| 2 | `surfaceContainer` | 卡片、设置分组 |
| 3 | `surfaceContainerHigh` | Dialog、浮层、底部面板 |
| 4 | `surfaceContainerHighest` | 前景浮层、选中输入 |
| 5 | `inverseSurface` | Snackbar、反色提示 |

深色模式优先使用 tonal surface 差异表达层级，阴影只做辅助；玻璃效果只用于播放器、图片叠加或明确的沉浸场景，不作为全部弹窗的默认背景。

**补充：玻璃的判据是"底材是否可控"，不是"宿主是不是播放器"（2026-10-13 审计后明确）。**

上句把玻璃限定在"播放器、图片叠加或明确的沉浸场景"，但实测中**手机端的分类/筛选底部面板**（`dialog_type.xml`、`dialog_filter.xml`，服务浏览页）也用了播放控制玻璃。审计时曾把它记为"越界"，复核后判定**不应按越界处理**，理由是原判据过窄：

- 玻璃真正的风险不是"出现在哪个页面"，而是**透明度成为可读性的唯一保障**——§5.3 已明文要求"玻璃/半透明面板只允许在其上叠加足够不透明的 scrim；透明度不能成为可读性的唯一保障"。
- 分类/筛选面板是**内容之上的浮层**，其下是应用自身的浏览页表面（可控、可测），并且它们**确实带了 scrim**。因此它们满足 §5.3，不属于越界。
- 真正需要禁止的是：玻璃**直接压在不可控底材上**（任意视频画面、任意海报、用户壁纸）且没有 scrim。播放器控制层属于这一类，所以它有专门的 `?attr/tvPlayerRing` 环族与 `player_control*` token 族。

**判据（替代"仅播放器"）**：一个面板可以用玻璃，当且仅当 ①其底材是应用可控的表面，或 ②它在其上叠加了足够不透明的 scrim。底材不可控且无 scrim 时，必须改用不透明 surface。

### 7.3 动效

| 交互 | 时长 | 规则 |
| --- | ---: | --- |
| 按钮按下/松开 | 100–150ms | 颜色或 ripple，不改变布局 |
| TV focus 进入/离开 | 150ms | 缩放至约 1.1x，保持内边距；不引起相邻项重排 |
| Dialog/Sheet 进入/退出 | 200–250ms | 使用系统/现有窗口动画；不得自定义无限动画 |
| 加载态 | 由系统 spinner 控制 | 不在同一屏同时出现多个竞争动画 |
| 主题切换 | 一次性重建 | 沿用 `RefreshEvent.THEME`，不做逐 View 动画 |

无动画能力和低性能设备上必须能完整工作；动画不能改变焦点顺序、返回栈或数据状态。

---

## 8. 间距、布局与安全区

### 8.1 通用间距 token

| Token | 值 | 用途 |
| --- | ---: | --- |
| `space1` | 4dp | 图标与文字的最小间隔 |
| `space2` | 8dp | 紧凑控件内部 |
| `space3` | 12dp | 列表项内/小分组 |
| `space4` | 16dp | 手机页面边距、卡片内边距 |
| `space5` | 24dp | 弹窗内边距、区块间距 |
| `space6` | 32dp | 页面大分块 |
| `space8` | 48dp | TV 安全区基准、超大区块 |

### 8.2 手机布局

- 页面内容左右边距 16dp；大屏/横屏可增至 24dp。
- 设置项最小高度 56dp；列表项最小可点击区域 48x48dp。
- 弹窗内容内边距 24dp，底部操作区与内容分离。
- 长文本可换行；禁止用 `singleLine` 吃掉关键错误信息。

### 8.3 电视布局

- 以 960x540dp 为设计基准，asset 面向 1080p。
- 重要内容保留 48dp 左右（现代电视可用 58dp 作为更安全值）和 27–28dp 上下边距；背景可越过安全区，但内容不可被裁切。
- 12 列、20dp gutter 作为横向内容网格。
- 卡片比例统一：海报 2:3、横向内容 16:9、方形资料 1:1。
- 横向列表项间距 20dp；卡片文字必须有 scrim，长标题最多两行。
- D-pad 方向必须有清晰导航路径；焦点移动不能依赖列表位置或 scroll 回弹。

---

## 9. 组件规范

### 9.1 按钮

| 角色 | 用途 | 手机默认 | 电视默认 | 状态 |
| --- | --- | --- | --- | --- |
| Filled | 主操作、确认、保存 | 48dp 高，primary/onPrimary | 48dp 高，1.1x focus | normal/focus/pressed/disabled |
| Tonal | 次级主操作、分组操作 | 48dp，primaryContainer/onPrimaryContainer | 同左，焦点填色 | 与 filled 不混用为同级 |
| Outlined | 取消、次要替代、危险但非破坏 | 48dp，outline/onSurface | 焦点时 outline+fill | 描边有对比度 |
| Text | 低强调、弹窗内取消/稍后 | 48dp 点击区 | 保持可读 | 不允许用于主确认 |
| Icon | 播放、收藏、更多、返回 | 40–48dp 点击区 | 40–48dp，图标放大 | 必须 contentDescription |
| Long/Image | 全宽或图像操作 | 仅移动端 | 图像按钮加渐变 scrim | 必须有文字或 contentDescription |

规则：

- 一屏只允许一个最高强调动作；不要把所有操作都做成 filled。
- 弹窗底部最多两个强调动作；危险操作使用 `error`，并放在明确位置。
- 禁用态必须同时改变对比度/透明度并提供不可用语义，不能只改 cursor。
- 焦点/选中/按下分别定义；TV 焦点必须有 ring 或 1.1x 容器变化，不能只改文字颜色。

**电视版统一焦点环规范（2026-10-04 明文化）**：

- 焦点态一律由 **边框环** 表达，填充色变化不得作为唯一焦点线索（Android TV 要求统一高亮方案，
  Material 3 亦以 ring 为聚焦做法）。
- 环宽唯一来源：`@dimen/webhtv_focus_ring_width` = 3dp；新增可聚焦控件必须引用它，禁止写死描边宽度。
- 环色 = 该控件**焦点态实际填充色**的**配对 on-色**，由主题解析器保证对比度（参考 §5.3 焦点环 ≥3:1 门槛）：
  primary/focus 填充 → `@color/focus_ring_on_primary`，secondaryContainer 填充 →
  `@color/focus_ring_on_secondary_container`，errorContainer 填充 → `@color/focus_ring_on_error_container`。
  配对对象必须是“焦点态**实际**填充”，而非常态填充：`following_button_*_bg` 聚焦时填充换成 FOCUS 色
  （冻结色板里 == primary），因此这两个家族的环都取 `on_primary`；
  `?attr/colorErrorContainer` 必须配 `on_error_container` 而不是 `on_error`。错配会使环贴在近似色上，
  对比度降到 1.3–1.4:1（已由 `everyRingColourKeepsNonTextContrastAgainstItsFocusFill` 固化为回归测试）。
- 透明/表面型图标按钮的焦点环沿用 `?attr/tvFocusRing`，其取值已接线到主题 FOCUS 用户槽
  （`tv_item_focus_ring = @color/webhtv_color_focus`），用户改主题即跟随。
- 非聚焦态保留**同宽透明描边**（strokeWidth 常量、只切换 strokeColor），避免焦点切换引起尺寸变化。
- 已落地：关于弹窗三按钮+齿轮、追更页顶栏与卡片按钮（`focus_ring_primary/secondary/error.xml`
  状态色表；契约测试 `TvFocusRingContractTest` 固化 R1/R2/R3）。

**电视版焦点环的宿主分层规则（2026-10-09 补充）**：

环色不能一律取 `?attr/tvFocusRing`——**宿主背景决定环色必须来自哪里**，否则会把「蓝白不一致」
换成「焦点看不见」。逐对计算 WCAG 对比度后确定三层：

| 宿主层 | 背景特性 | 环色来源 | 反例实测对比度 |
| --- | --- | --- | --- |
| 调色板表面 / 对话框面板 | 跟随调色板 | `?attr/tvFocusRing`，或焦点态填充的配对 on-色 | day 浅底上白色环只有 1.22:1 |
| 固定明暗面板（`shape_dialog_glass_panel`、`shape_exit_confirm_dialog`、`shape_ad_stats_content`…） | 两张表相同 | 与调色板无关的常量（白或深蓝），**只统一宽度** | 深色玻璃上 `?attr/tvFocusRing` 只有 1.91:1；固定浅色面板上 night 浅蓝只有 1.6:1 |
| 视频画面 / 透明控制条 | 不可预测 | 与调色板无关的白色，**只统一宽度** | 浅色表的深蓝环在视频/玻璃控制层无可用对比度 |

同层内不得再出现第二种宽度或第二种环色语义。已落地：首页功能按钮行与内容卡片行
（`shape_item_focused` vs `shape_vod_focused`）统一为 `@dimen/webhtv_focus_ring_width` +
`?attr/tvFocusRing`；固定明暗宿主与视频层的 10 个 drawable 只统一宽度。
契约测试 `TvFocusRingContractTest.appSurfaceFocusRingsShareOneWidthTokenAndAThemeColour`
固化分层清单，`TvAppSurfaceFocusRingDeviceTest` 用真实栅格化逐像素量厚度与颜色
（含 day/night 跟随与变异检验）。

宽度收口是**全库不变量**（2026-10-10 补充）：`TvFocusRingContractTest.noLeanbackFocusRingHardcodesItsWidth`
扫描整个 `leanback/res/drawable`，断言**每一个**焦点态描边都引用
`@dimen/webhtv_focus_ring_width`。只有两个文件豁免，豁免理由即契约：
`selector_video_item.xml`（本规范的样板文件，字面量 `3dp` 与 token 取值相同，
且被 `NativeEnhancedPlaybackStyleFocusTest` 当基准逐字断言）与
`shape_audio_action_icon_focused.xml`（播放页音频按钮的 `inset=3dp` **图标内描边环**，
40dp 图标 / 17dp 圆角，套 3dp 会把图标糊成一团）。

同一轮还修掉了选集弹窗（固定深色面板 `#DD111820`）的一处「焦点不可见」缺陷：
原环色取自填充色的近似色，与自身填充只有 `1.47:1` / `1.32:1`，
两个 selected 态更是环与填充同色 = **`1.00:1`**；改白后为 `3.12:1` / `3.17:1`，
达到 §5.3 的 3:1 门槛，并与所有其它固定深色宿主的白环规则一致。

### 9.2 输入框

- 统一 `Widget.Material3.TextInputLayout.OutlinedBox` 或迁移后的 `Widget.WebHTV.Input.Outlined`。
- 默认 surface/onSurface，聚焦 primary，错误 error，辅助文字 onSurfaceVariant。
- 输入框背景不能写死 `@color/white`；必须使用 `?attr/colorSurfaceContainerHighest` 或对应 token。
- 密码、路径、代理、Webhook 等长文本允许换行/滚动；错误信息不能被裁剪。
- 光标、选择柄、选中背景都使用 primary 派生色。

### 9.3 列表项与分组

- 默认 `surface`；选中/聚焦使用 `secondaryContainer` 或 `primaryContainer`，危险操作使用 `errorContainer`。
- 主标题 `onSurface`，副标题 `onSurfaceVariant`，第三行/状态 `labelSmall`。
- 列表项分隔使用 `outlineVariant`；卡片边框使用 `outline`。
- 选中标记不能只靠颜色：加 check、icon 或文字状态。
- 手机长列表支持 ripple；TV 列表使用 focus scale/ring + 可读的选中态。

### 9.4 卡片与海报

- 手机卡片圆角 12dp、内边距 12–16dp、整体 surfaceContainer。
- TV 卡片圆角 8–12dp，焦点缩放约 1.1x；横向间距 20dp。
- 海报 2:3，横向卡片 16:9，人物头像 1:1。
- 图片内文字使用 `overlayDark` 渐变或 scrim；不得依赖图片本身足够暗。
- 加载失败、空海报、长标题不得改变卡片尺寸。

### 9.5 Chip / Tab / 标签

- 普通标签：`surfaceContainerHighest` + `onSurfaceVariant`。
- 选中：`secondaryContainer` + `onSecondaryContainer`，或 `primaryContainer` + `onPrimaryContainer`。
- Tab：选中 primary + indicator，未选中 onSurfaceVariant；TV 必须扩大焦点区域。
- 状态标签（成功/警告/错误）使用对应 container/on-container 配对，不能只改文字色。

### 9.6 Switch / Checkbox / Radio

- 选中/激活使用 primary；禁用使用 onSurface 的低透明度。
- 开关轨道、滑块、复选框勾选状态必须来自同一颜色状态列表。
- 每个控件必须有可访问文本；不能只靠颜色区分开/关。

### 9.7 Dialog、Bottom Sheet 与全屏浮层

统一为四类：

| 类型 | 用途 | 结构 | 关闭方式 |
| --- | --- | --- | --- |
| Alert | 确认、危险提示 | 标题 + 正文 + 最多两个动作 | 返回/取消/确认 |
| Single Choice | 模式、线路、选项 | 标题 + 单选列表 + 取消/确认 | 选择后可自动关闭 |
| Form | 编辑配置、输入地址 | 标题 + 可滚动表单 + 操作栏 | 取消不保存、确认校验后保存 |
| Full-screen / Sheet | 复杂列表、播放器控制、小屏编辑 | 全屏或底部面板 + 固定操作区 | 返回优先关闭浮层，再返回页面 |

规则：

- 所有 `MaterialAlertDialogBuilder` 使用统一 `materialAlertDialogTheme`；不再传 `Theme.WebHTV.LightDialog` 这种固定浅色主题。
- 手机浅色/深色由 DayNight token 决定；电视默认深色面板，避免亮白孤岛。
- Dialog 背景使用 `surfaceContainerHigh`，圆角 `shapeExtraLarge`，scrim 使用 `scrim`。
- 内容可滚动时固定操作区；键盘/遥控焦点不能落到被遮挡区域。
- 长文本不能强制单行；错误、风险、删除对象名称必须完整可见。

### 9.8 Player / OSD / 控制层

- 播放器画面不加普通主题背景；仅控制层、OSD、弹窗消费 token。
- `playerControl` 默认白色，`playerControlActive` 用于选中/激活；场景压缩、浅色画面时使用 scrim。
- 控制条按钮的圆角、间距、焦点和选中态与全局按钮一致；播放器专用 token 只允许在 `playerControl*` 命名空间。
- 进度条、音量、亮度、倍速等状态要同时有形状/位置/文本反馈。

### 9.9 加载、空态、错误态

- 加载使用统一 spinner/progress，背景 `surface`，不使用多种颜色动画。
- 空态：标题 + 解释 + 一个主动作；不能只显示一句“暂无数据”。
- 错误态：错误色 + 原因 + 重试动作；错误详情可折叠，避免撑破弹窗。
- 网络/解析错误不清空已显示内容；沿用现有失败回退和 last-known-good 行为。

**例外：播放器错误层不使用"错误色"，用纯白（2026-10-13 审计后明确）。**

上句的"错误色"在**应用表面**上成立，并且已落地（`?attr/webhtvColorOnWallpaperError` + 实心深底 `shape_error_value`，实测 8.84:1）。但**播放器错误层是明文例外**：

- 它画在 `shape_widget`（50% 黑）之上，而下面叠的是**任意视频画面**——底材不可控，任何带色相的前景色都给不出对比度保证；纯白在该底材上最稳（现有实现即 `@color/white`）。
- 要让它也满足"错误色"，就得给它一块**不透明实心底**，那会改变播放页的观感，属独立决策；审计时已把它列为待决策项，**决定为不做**。
- 因此播放器错误层的契约是：**错误文案 + 重试动作**（两者均已落地，见 `view_widget_vod.xml` 的 `@id/retry` / `@id/errorRetry`），**颜色沿用播放器层 token（白）**。

同理，播放器 OSD 的白字、以及 `PlayerOsdController` 的 `0xFFFFFFFF` 都属于这一类，**不计为偏离**。

---

## 10. Android 资源落地设计

### 10.1 新增资源职责

建议在实施阶段新增（名称可按实际工程调整，但职责必须固定）：

```text
app/src/main/res/values/webhtv_tokens.xml
app/src/main/res/values-night/webhtv_tokens.xml
app/src/main/res/values/webhtv_styles.xml
app/src/main/res/values/webhtv_dimens.xml
app/src/main/res/values/webhtv_type.xml
app/src/main/res/color/webhtv_*.xml
app/src/main/res/values/attrs.xml           （仅增加必要的 WebHTV 语义 attr）
```

约束：

- `webhtv_tokens.xml` 只放语义色别名和默认值；业务页面不直接引用 seed。
- `values-night` 只覆盖模式相关的色值，不复制整份 style。
- `mobile`/`leanback` 的 style 只保留尺寸、焦点、密度、安全区差异。
- 旧的 `colors.xml` 可以保留基础透明/黑白/壁纸兼容项；逐步将业务色迁到语义 token。

### 10.2 组件 style 命名

| Style | 用途 | 规则 |
| --- | --- | --- |
| `Theme.WebHTV` | 共享视觉入口 | 只定义颜色、typography、shape、dialog 主题 |
| `Theme.WebHTV.Mobile` | 手机尺寸/输入法/edge-to-edge | 继承共享主题 |
| `Theme.WebHTV.TV` | 电视 overscan/D-pad/focus | 继承共享主题；默认 dark |
| `Widget.WebHTV.Button.Filled` | 主操作 | 不使用页面内 tint |
| `Widget.WebHTV.Button.Tonal` | 次主操作 |  |
| `Widget.WebHTV.Button.Outlined` | 次级操作 |  |
| `Widget.WebHTV.Button.Text` | 低强调 |  |
| `Widget.WebHTV.Input` | 输入框 |  |
| `Widget.WebHTV.ListItem` | 列表项 |  |
| `Widget.WebHTV.Card` | 卡片 |  |
| `Widget.WebHTV.Dialog` | 统一 Dialog |  |
| `Widget.WebHTV.BottomSheet` | 手机底部面板 |  |
| `Widget.WebHTV.PlayerControl` | 播放器控制 | 专用 token |

页面布局优先使用这些 style；若某个布局仍需要局部差异，只能覆盖尺寸/布局，不得覆盖 palette。

### 10.3 迁移映射表（核心）

| 现有表达 | 新语义 | 迁移动作 |
| --- | --- | --- |
| `#202124` / `@color/black` 作为主文字 | `onSurface` | 替换为主题 attr 或 `Widget.WebHTV.Label` |
| `#5F6368` / `#666666` / `#8A8F98` 作为次文字 | `onSurfaceVariant` | 统一一个 token；输入提示同样使用该角色 |
| `#1A73E8` / `#174EA6` / `#0B57D0` | `primary` / `onPrimary` / `primaryContainer` | 按“前景/背景/容器”区分，不能只换一个色值 |
| `#E8F0FE` / `#D2E3FC` | `primaryContainer` 或 `secondaryContainer` | 选中容器与按钮容器分别映射 |
| `#FFFFFF` 表面 | `surface` / `surfaceContainer*` | 根据层级选择，不直接写白 |
| `white_70` / `white_60` / `white_50` | `onSurfaceVariant` 或 `onSurface` + alpha | 电视面板按语义替换；播放器专用透明度可保留在 player token |
| `#DADCE0` / `#C8CDD2` | `outlineVariant` / `outline` | 分隔用 variant，输入/按钮边框用 outline |
| `display_dialog_panel_bg` | `surfaceContainerHigh` | TV Dialog 面板统一 |
| `#0F141A` / `#141A20` / `#111318` | `surface` / `surfaceContainerLow` | 页面 canvas 与卡片分组分层 |
| `B3261E` / `site_health_bad` | `error` / `healthBad` | 业务健康状态保留专用 token，但值来自语义色 |
| `site_health_good` / `#0B8043` | `success` / `healthGood` |  |
| `site_health_warn` / `#B7791F` | `warning` / `healthWarn` |  |
| `display_option_bg_selected`（黄色） | `playerControlActive` 或 `focus` | 播放器选项保留专用选中色，普通 UI 不使用黄色表示焦点 |
| `Theme.WebHTV.LightDialog` 固定色 | `ThemeOverlay.WebHTV.Dialog` | 移除固定浅色；由 DayNight/TV 模式决定 |
| `Widget.WebHTV.LightDialog.*` | `Widget.WebHTV.*` | 改名并挂到共享 token |
| `shape_shell_proxy_dialog` 白底 | `shape_webhtv_dialog` + `surfaceContainerHigh` | 保留 22dp，去掉固定白 |
| `shape_dialog_glass_panel` 蓝紫渐变 | 播放器/沉浸场景专用或 `surfaceContainerHigh` + scrim | 不让所有弹窗使用玻璃 |
| `@android:color/white` 作为通用文字 | `onSurface` / `inverseOnSurface` | 仅 logo/画面覆盖允许保留系统白 |

---

## 11. Web / WebTheme 统一设计

### 11.1 内置 Web CSS

`app/src/main/assets/css/ui.css` 调整为：

```css
:root {
  --webhtv-primary: ...;
  --webhtv-on-primary: ...;
  --webhtv-surface: ...;
  --webhtv-surface-container: ...;
  --webhtv-surface-container-high: ...;
  --webhtv-on-surface: ...;
  --webhtv-on-surface-variant: ...;
  --webhtv-outline: ...;
  --webhtv-outline-variant: ...;
  --webhtv-error: ...;
  --webhtv-success: ...;
  --webhtv-warning: ...;
  --webhtv-focus: ...;
  --webhtv-shape-small: 8px;
  --webhtv-shape-medium: 12px;
  --webhtv-shape-large: 16px;
  --webhtv-space-1: 4px;
  --webhtv-space-2: 8px;
  --webhtv-space-3: 12px;
  --webhtv-space-4: 16px;
}
```

要求：

- 保留现有 `--md-*` 作为兼容别名，至少一个发布周期；新代码只写 `--webhtv-*`。
- `prefers-color-scheme`、`data-theme="light|dark"`、`data-theme-source="native|web"` 只切换变量，不复制组件规则。
- 管理页、reader、WebHome 页面不得再新增原始 hex；reader/eclipse 现有的内联颜色分批迁移。
- Web 主题切换不得读取原生私有设置；原生宿主只注入只读 token 快照。

### 11.2 WebTheme V2 边界

- `theme.info` 可以返回 token 快照和页面上下文，但不能返回可写主题句柄。
- Manifest 声明的 token 只作为页面自身样式输入；不能改变原生 Dialog、系统栏、播放器控制层或状态栏。
- 远程页面失败必须逐页回退原生页面；不能因主题页面异常改变播放器状态。
- 任何 token 注入都必须经过 schema/范围/对比度校验，不能执行任意 CSS 或脚本。

---

## 12. 分阶段实施计划

每个阶段独立提交、独立 recovery tag，可单独回滚；不把“全量替换”作为一个提交。

| 阶段 | 目标 | 主要路径 | 验收 | 预计风险 |
| --- | --- | --- | --- | --- |
| A. Token 层与静态检查 | 新增共享 token、resolver、检查脚本；页面暂不换血 | `res/values*/webhtv_*`、`theme/` resolver、`scripts/check_ui_tokens.sh`、对应 JVM 测试 | resolver 生成 light/dark；对比度测试通过；检查脚本能识别未豁免 hex | 低 |
| B. Dialog / 设置页统一 | 让用户最先看到的弹窗和设置页不再浅/深分叉 | `values/styles*`、`color/dialog_*`、`ui/dialog/*`、`layout/dialog_*`、`layout/fragment_setting_*` | 手机 light/dark、TV dark 三组场景；22dp 圆角回归；按钮/输入/文字 state 一致 | 中 |
| C. 首页 / 列表 / 卡片 | 统一站点、首页、搜索、历史、收藏、推荐卡片 | `layout/adapter_*`、`ui/adapter/*`、`Home*`、`Search*`、`History*` | 卡片尺寸/焦点/选中一致；不改变分页、顺序、点击行为 | 中 |
| D. 详情 / 播放器控制层 | TMDB 详情、播放页控制、OSD、弹层接入 token | `ui/detail/*`、`ui/player/*`、`view_control_*`、player OSD 布局 | 播放内核/解码/字幕/音频不受影响；焦点、选中、进度可读 | 中高 |
| E. Web 资源统一 | 内置 CSS/HTML 接入 `--webhtv-*` | `assets/css/ui.css`、`index/manage/reader/webhome` 页面 | 管理页、WebHome、reader 三种页面 light/dark 一致；旧主题不崩 | 中 |
| F. 清理与可选高级主题 | 删除冗余 drawable/style；再评估 ThemeSpec 编辑器 | 旧资源、`theme/` 高级模型、设计文档更新 | 静态检查零未豁免；高级主题独立审批，不阻塞 A–E | 中 |

### 阶段 A 最小步骤

1. 定义 token 名称、默认 seed、light/dark 参考值和 allowlist。
2. 抽出 `ThemeTokens` 与 `ThemeResolver`（只读，不持久化新格式）。
3. 把 `SiteDialogTheme` 的 `ColorRoles` fallback 逻辑提炼为 resolver，保留已有行为。
4. 增加静态检查：禁止新增未豁免 hex、禁止固定 LightDialog、检查关键对比度。
5. 写 JVM 测试；不改一个页面，先证明 token 层可独立回滚。

### 阶段 B 最小步骤

1. 把 `Theme.WebHTV.LightDialog`/`ThemeOverlay.WebHTV.LightDialog` 收敛为共享 Dialog overlay。
2. 把 `Widget.WebHTV.LightDialog.*` 迁移为 `Widget.WebHTV.*`。
3. 替换弹窗布局中的 `#202124`/`#5F6368`/`#1A73E8`/`#E8F0FE`/`@color/white`。
4. 保留现有 `LightDialog` 的宽高/列表尺寸逻辑，只替换视觉来源。
5. 跑定向 JVM 测试 + mobile/leanback 编译；设备只做代表场景，不跑全 ABI。

### 后续阶段原则

- 每次只迁移一个组件族，禁止顺手重构业务逻辑。
- 每阶段保留旧资源别名，至少一个发布周期，确认无回退后再删除。
- 需要设备验证时优先使用 `scripts/build_arm64_debug_install.sh` 和目标模拟器：dev3 为 `192.168.50.3:5559`，TV/电脑可用 `192.168.50.3:5561`。
- 任何阶段都不打正式包；只做测试包并行覆盖安装，不卸载现有包。

---

## 13. 验证与验收

### 13.1 静态验证（每阶段必做）

1. `git diff --check`。
2. 定向 JVM/source 测试：token 解析、状态列表、禁止固定色、圆角。
3. `:app:compileMobileArm64_v8aDebugJavaWithJavac` 与 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`，或至少覆盖本次改动 flavor 的编译任务。
4. `scripts/check_ui_tokens.sh`：
   - 列出仍未迁移的 hex 及所在文件；
   - 对 allowlist 之外的布局/drawable 固定色失败；
   - 对 `Theme.WebHTV.LightDialog`、`Widget.WebHTV.LightDialog` 的回归失败；
   - 验证 token 配对对比度。

   **在 Windows / 无可用 bash 的主机上改用 `python scripts/check_ui_tokens.py`（2026-10-13 补）。**

   两者是同一个门槛的两个入口：`.sh` 是权威定义，`.py` 是它的等价移植（CLI、五项检查、输出行、退出码全部对齐）。之所以需要它——`.sh` 把全部检查体放在内嵌的 `python3` heredoc 里，真正依赖 bash 的只有参数解析与 `git rev-parse`；在没有可用 bash 的主机上（Windows + WSL 服务不可用）该门槛**根本无法运行**，于是它在相当长一段 UI 工作里**静默地没有被执行过**，期间有固定色被引入而无人拦下。

   验证等价性的方式：`--strict` 下两者输出同一组 `UI_TOKEN_*` 行；并做过负向测试（临时注入一个 `#174EA6` → 立刻 `UI_TOKEN_VIOLATION` + `UI_TOKEN_STATUS FAIL` + 退出码 1，还原后 `violations=0`）。

   常用形式：`python scripts/check_ui_tokens.py --strict`（等价于 `.sh --strict`）；`--baseline` 关 strict、`--stage B|C|D|E|F` 自动开 strict，与原脚本一致。

### 13.2 视觉场景矩阵

| 场景 | 手机 | 电视 | 重点 |
| --- | --- | --- | --- |
| Light 系统 | 是 | 可选手动 | 文字、输入、按钮、Dialog |
| Dark 系统 | 是 | 默认 | 面板、玻璃、焦点 |
| 壁纸 seed | 是 | 不适用 | seed→tonal→surface 不直接上色 |
| Android 9 fallback | 是 | 是 | 不依赖系统动态色 |
| D-pad focus | 不适用 | 是 | ring、scale、selected 可区分 |
| 长文本/错误 | 是 | 是 | 不裁剪、不溢出 |
| 播放页控制 | 是 | 是 | 画面/解码不变，控制层可读 |

### 13.3 验收标准

- 所有新增/修改的页面只消费语义 attr/style 或 CSS 变量；allowlist 之外没有新增原始色。
- 手机和电视各自的 Dialog 在 light/dark 下背景、文字、按钮、输入状态一致。
- TV 焦点、选中、按下、禁用四种状态均存在，且不依赖单一颜色。
- 正文对比度 ≥4.5:1，大字/图标/焦点 ≥3:1。
- Web 管理页、WebHome、reader 在浅色/深色下与原生 token 角色一致。
- 播放器画面、解码、渲染、字幕、音轨、弹幕和网络行为无回归。
- 每一阶段可以单独回滚，不需要回滚整个视觉系统或数据库。

### 13.4 不进入本目标的验证

- 不做全 ABI、全设备矩阵。
- 不为视觉统一重建 native、FFmpeg、mpv、Media3 或第三方二进制。
- 不因颜色变化而修改站点数据、播放协议、同步格式或备份格式。

---

## 14. 风险、取舍与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 一次性替换全部固定色导致内容页视觉大幅变化 | 高回归、难定位 | A–F 分阶段、逐组件族提交、保留旧别名 |
| 手机浅色弹窗在电视上不可读 | 电视端严重可读性问题 | TV 默认深色 Dialog；对比度测试；不在 TV 复用手机 LightDialog |
| 壁纸色直接作为主背景导致文字不可读 | 低对比度 | seed 只进 resolver；surface 做 tonal/去饱和；正文配对校验 |
| 玻璃/半透明过度使用 | 图片/壁纸穿透，层级混乱 | 玻璃限定沉浸/播放器/图片叠加；普通 Dialog 用 container |
| 黄色焦点色与成功/选中色混淆 | 状态不可区分 | 普通 UI 焦点统一 primary/focus；黄色只保留播放器专用激活 token |
| WebTheme 远程主题试图覆盖原生 UI | 权限/安全/回滚风险 | 只读 token 快照；Manifest 校验；逐页回退；禁止任意 CSS/脚本 |
| 存量硬编码数量大 | 迁移周期长 | 静态检查 + allowlist；按用户可见优先级推进；不承诺一次清零 |
| 跨分支主题实现分叉 | 重复实现或冲突 | 先统一共享 token；高级 ThemeSpec 恢复前核对 dev1 历史实现与回退记录 |
| 主题切换重建宿主造成闪白 | 体验问题 | 启动读取已验证 token；主题变更一次重建；避免页面内多次切换 |
| 动态色 API 在旧设备不生效 | 主题看起来无效 | resolver 始终生成 fallback；不依赖 Android 12 可用性门槛 |

### 14.1 回滚

- 阶段 A 只新增 token/resolver/检查，不接页面；回滚删除该阶段提交即可。
- 阶段 B–E 每阶段保留旧资源别名；回滚对应提交恢复旧视觉，不涉及数据迁移或播放器内核。
- 不移动已发布 tag、不重写历史；使用 `task_guard.sh finish` 生成每阶段独立 recovery tag。
- 若某阶段发现公开行为/播放性能回归，先回滚该阶段，再拆成更小单元；不把半套视觉接入留在主分支。

---

## 15. 与既有设计文档的关系

| 文档 | 关系 | 处理 |
| --- | --- | --- |
| `docs/theme-color-system-design-20260907.md` | TweakCN/主题 profile/编辑器的历史设计 | 作为高级主题阶段的参考；当前不在 A–E 阶段实施；恢复前必须核对 dev1 验证历史与当前 `theme` 包缺失状态 |
| `docs/universal-webhome-theme-design.md` | WebTheme V2 的页面、Manifest、安全、回滚设计 | 本设计的 WebTheme 边界与其一致；本轮补充“与原生 token 共用语义”的映射 |
| `docs/universal-webhome-theme-development.md` | WebTheme 开发指南 | 后续阶段 E 更新 CSS 变量和页面约定时必须同步 |
| `docs/mobile-site-theme-20260906.md` | 手机站点弹窗 fallback 的已验证实现 | `ColorRoles` fallback 是共享 resolver 的参考来源，不重复造轮子 |
| `docs/webtheme-compatibility-matrix.md` | WebTheme Host API 兼容矩阵 | 只读 token 能力必须写进兼容矩阵，不能偷偷新增写接口 |
| `docs/settings-classification-review.md` | 设置分类边界 | 视觉设置仍属于个性化/增强的现有入口，不改变功能归类 |

---

## 16. 待用户确认的边界

以下问题会影响实施范围，但在本文阶段不阻塞设计：

1. **是否接受“电视默认深色、手机跟随系统”**：推荐接受；电视深色更适合影片内容，手机保留 DayNight。
2. **是否保留现有壁纸/主题色入口**：推荐保留入口和 `-1/0/custom` 语义，但将实际着色改为 resolver 输出。
3. **高级主题编辑器是否延后**：推荐延后到阶段 F；A–E 先完成现有 UI 统一，避免把“编辑器 + 统一”绑成一个不可回滚的大任务。
4. **TMDB “光影/剧幕”模式是否保留**：推荐保留为内容呈现模式，但必须消费统一 token；不能继续维护独立深色板。
5. **播放器控制层允许使用专用 token 吗**：推荐允许 `playerControl*` 专用 token；播放器画面本身不参与普通主题换色。
6. **是否允许第一阶段保留少量豁免固定色**：推荐允许，但必须有 allowlist 和理由；豁免只用于播放器画面遮罩、品牌资产和兼容别名。

批准后，建议先执行阶段 A，目标是在不改变任何页面视觉的前提下，证明 token resolver、fallback、对比度检查和静态检查可用；随后再单独批准阶段 B。

---

## 17. 设计决策记录（ADR 摘要）

- **ADR-001：为什么不用 TweakCN 作为统一底座**

  统一的是语义和组件，不是编辑器。TweakCN 可以映射到 token，但不能替代 token。

- **ADR-002：为什么共享 token 而 flavour 保留尺寸**

  颜色/字体角色在手机和电视可以共享；D-pad 焦点、安全区、点击尺寸和播放器 OSD 无法共享同一物理尺寸。

- **ADR-003：为什么保留 22dp 大弹窗圆角**

  现有测试已锁定该几何值，且它不属于当前分叉痛点；统一颜色和层级比改圆角收益更高、风险更低。

- **ADR-004：为什么 Web 只读 token 而不可写原生**

  远程主题来自不受信内容；写原生主题会扩大权限、持久化和回滚面。只读快照既统一视觉，又保持 WebTheme 的安全边界。

- **ADR-005：为什么先统一再扩展自定义能力**

  当前基线本身有多种隐式主题；先扩展配置只会让更多页面复制更多分叉。先建立单一语义层，再在其上增加高级编辑能力。

---

## 18. 开发执行手册（可直接开工）

本节把前文的设计约束转换为逐阶段、逐文件的开发任务。每个任务只有一份完成定义（DoD）：实现、静态验证、编译/测试、设备场景、提交与 recovery tag 全部齐备，缺一项即未完成。开发时先建 guard，再按任务顺序修改；不允许跳过阶段直接全局替换。

### 18.1 统一资源与命名契约（阶段 A 冻结）

```text
app/src/main/res/values/webhtv_tokens.xml
app/src/main/res/values-night/webhtv_tokens.xml
app/src/main/res/values/webhtv_dimens.xml
app/src/main/res/values/webhtv_type.xml
app/src/main/res/values/webhtv_shapes.xml
app/src/main/res/values/webhtv_styles.xml
app/src/main/res/values/webhtv_attrs.xml
app/src/main/res/color/webhtv_*.xml
app/src/main/java/com/fongmi/android/tv/theme/ThemeMode.java
app/src/main/java/com/fongmi/android/tv/theme/ThemeSeed.java
app/src/main/java/com/fongmi/android/tv/theme/ThemeTokens.java
app/src/main/java/com/fongmi/android/tv/theme/ThemeResolver.java
app/src/main/java/com/fongmi/android/tv/theme/ThemeController.java
app/src/main/java/com/fongmi/android/tv/theme/ThemeWebBridge.java
scripts/check_ui_tokens.sh
app/src/test/java/com/fongmi/android/tv/theme/ThemeResolverTest.java
app/src/test/java/com/fongmi/android/tv/theme/ThemeContractTest.java
app/src/test/java/com/fongmi/android/tv/ui/style/UiStyleSourceTest.java
```

命名与类型契约：

| 资源类型 | 名称前缀 | 示例 | 约束 |
| --- | --- | --- | --- |
| 语义色 | `webhtv_color_*` | `webhtv_color_on_surface_variant` | 只作为语义别名，不写页面名 |
| 颜色状态列表 | `webhtv_selector_*` | `webhtv_selector_button_filled` | 必须覆盖 enabled/focused/pressed/disabled/selected |
| 尺寸 | `webhtv_space_*`、`webhtv_size_*` | `webhtv_space_4` | 不写业务尺寸 |
| 形状 | `webhtv_shape_*` | `webhtv_shape_extra_large` | 4/8/12/16/22/full |
| 文字 | `webhtv_text_*` | `webhtv_text_body_medium` | 至少 12sp，正文 14/16sp |
| 组件 style | `Widget.WebHTV.*` | `Widget.WebHTV.Button.Filled` | 只消费 token |
| 主题 | `Theme.WebHTV.*` | `Theme.WebHTV.Mobile` | 共享主题 + flavour 差异 |
| Java API | `ThemeTokens.*` | `ThemeTokens.colorOnSurface()` | 不可变、只读 |

### 18.2 阶段 A：Token 层与检查器（必须先完成）

**允许路径**：上述 `theme/`、`res/values*/webhtv_*`、`res/color/webhtv_*`、`scripts/check_ui_tokens.sh`、对应测试。
**禁止**：修改任何既有 layout/drawable/style/业务 Java；阶段 A 必须做到“引入后页面像素不变”。

实现步骤：

1. 新建 `ThemeMode`：`SYSTEM`、`LIGHT`、`DARK`；解析顺序为显式模式 > 系统 WebHTV 默认 > 产品默认（mobile 跟随系统、leanback 深色）。
2. 新建 `ThemeSeed`：`NONE`、`WALLPAPER`、`EXPLICIT`、`CONTENT`；输入来源仅允许 `Setting.getThemeColor()`、`Setting.getWallColor()`、内置壁纸色和显式内容 key。
3. 新建不可变 `ThemeTokens`，字段与 5.2 表逐项一致；提供 `light()`/`dark()` 工厂和 `requireContrast()`。
4. 新建 `ThemeResolver`：
   - 显式 seed：用 Material `ColorRoles` 生成 tonal roles；
   - 无 seed/Android 9：使用 5.2 默认 palette；
   - 壁纸 seed：只进入色相/色调生成，不直接返回原色；
   - 所有输出经 `requireContrast()`；失败则回退到默认 palette 并记录诊断。
5. 把 `SiteDialogTheme` 的 fallback 逻辑改为调用 `ThemeResolver`，但保持站点弹窗现有视觉行为不变（阶段 A 只接线 resolver，不改样式）。
6. 新建 `scripts/check_ui_tokens.sh`，至少执行：
   - `rg` 扫描 `layout*/**/*.xml`、`drawable*/**/*.xml`、`color/**/*.xml`、`assets/**/*.{css,html}` 中的原始 hex；
   - 读取 `docs/ui-token-allowlist.txt`（阶段 A 同提交新增），allowlist 外命中即失败；
   - 检查 `Theme.WebHTV.LightDialog` / `Widget.WebHTV.LightDialog` 不新增；
   - 运行 `ThemeContractTest` 断言 5.2.1 所有对照度；
   - 检查 `webhtv_tokens.xml`、`webhtv_styles.xml` 的关键 style 与 token 名存在；
   - 输出未迁移数量、文件数和 allowlist 命中数，便于阶段 B–F 递减。
7. 新增 `docs/ui-token-allowlist.txt`，初始允许：播放器画面遮罩、品牌资产、系统透明/黑白、历史兼容别名、`android.graphics` 测试替身。
8. compile：`:app:compileMobileArm64_v8aDebugJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugJavaWithJavac`。

阶段 A DoD：

- 页面资源 diff 为零（除新增 token/resolver/脚本/测试）。
- `ThemeResolverTest` 覆盖 light/dark、显式 seed、壁纸 seed、Android 9 fallback、低对比度回落。
- `ThemeContractTest` 固化默认 palette 的全部配对对比度。
- `scripts/check_ui_tokens.sh` 可执行、输出当前基线数量、非零退出可用。
- 无设备验证要求；阶段 A 合并后页面像素不变。

### 18.3 阶段 B：Dialog 与设置页（用户最先看到）

**逐文件清单**：

- 精确数量：共享 `main` 58 个、mobile 42 个、leanback 37 个，共 137 个 `dialog_*.xml`；设置页为 mobile 9 个 `fragment_setting*.xml` + leanback 10 个 `activity_setting*.xml`。
- 共享主题与样式：`app/src/main/res/values/styles.xml`、`app/src/main/res/values/webhtv_styles.xml`、`app/src/main/res/values/webhtv_dimens.xml`、`app/src/main/res/values/webhtv_shapes.xml`、`app/src/main/res/values/webhtv_tokens.xml`、`app/src/main/res/values-night/webhtv_tokens.xml`。
- 共享颜色状态：`app/src/main/res/color/dialog_*.xml`、`app/src/main/res/color/webhtv_selector_*.xml`。
- 共享 dialog Java：`app/src/main/java/com/fongmi/android/tv/ui/dialog/LightDialog.java`、`BaseAlertDialog.java`、`ChoiceDialog.java`、`DialogFragment` 统一基类；
- mobile dialog Java：`app/src/mobile/java/com/fongmi/android/tv/ui/dialog/*.java`；
- leanback dialog Java：`app/src/leanback/java/com/fongmi/android/tv/ui/dialog/*.java`；
- 共享 dialog layout：`app/src/main/res/layout/dialog_*.xml`；
- mobile dialog layout：`app/src/mobile/res/layout/dialog_*.xml`；
- leanback dialog layout：`app/src/leanback/res/layout/dialog_*.xml`；
- 设置页：`app/src/mobile/res/layout/fragment_setting*.xml`、`app/src/leanback/res/layout/activity_setting*.xml` 及其 style 引用。

迁移规则：

1. `Theme.WebHTV.LightDialog` 与 `ThemeOverlay.WebHTV.LightDialog` 只保留兼容别名，内部引用共享 `ThemeOverlay.WebHTV.Dialog`。
2. `Widget.WebHTV.LightDialog.Button.*`、`Input`、`Label`、`Helper` 保留旧名一个发布周期，但改为继承 `Widget.WebHTV.*`；新 layout 只写新名。
3. 所有 137 个 `dialog_*.xml`、19 个设置布局逐批替换：`#202124`→`?attr/colorOnSurface`；`#5F6368`/`#666666`/`#8A8F98`→`?attr/colorOnSurfaceVariant`；`#1A73E8`/`#174EA6`/`#0B57D0`→`?attr/colorPrimary`/`onPrimary`/`primaryContainer`；`#E8F0FE`/`#D2E3FC`→`?attr/colorPrimaryContainer`/`secondaryContainer`；固定白 surface→`?attr/colorSurfaceContainerHigh`。
4. `LightDialog` 保留 `resolveAlertWidth`、`resolveAlertListMaxHeight`、`resolveAlertWindowHeight` 和 `InsetDrawable` 行为；只替换 background/shape/color 来源。
5. 所有 `MaterialAlertDialogBuilder(context)` 改为 `MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_WebHTV_Dialog)`；所有 `LightDialog.apply` 继续只做窗口几何。
6. 表单输入统一 `Widget.WebHTV.Input`；文本、光标、选中柄、错误态走 token；长文本允许换行。
7. 每个 dialog 的按钮层级固定为：主确认 Filled、次操作 Outlined/Tonal、取消 Text；危险动作 error 色。
8. TV dialog 使用 `surfaceContainerHigh` + `scrim`，不使用手机浅色 palette；手机跟随 DayNight。
9. 逐批设备截图点（任一布局改动后）：`AppearanceDialog`、`ChoiceDialog`、`dialog_one_key_sync`、`dialog_playback_webhook`、`dialog_ad_rule_preview`、`dialog_update`、`dialog_site`、`dialog_episode_detail`。

阶段 B DoD：

- 137 个 dialog 布局中 allowlist 外无固定业务色；
- `DialogRoundedCornerSourceTest` 通过且覆盖统一 22dp；
- `UiStyleSourceTest` 断言无 `Theme.WebHTV.LightDialog` 新引用、button/input style 存在；
- mobile/leanback 编译通过；
- 手机 light/dark 与 TV dark 三组截图/目视验收通过；
- 无播放、站点、网络行为变化。

### 18.4 阶段 C：首页、列表与卡片

**逐文件清单**：

- 精确数量：共享 `main` 24 个、mobile 46 个、leanback 51 个，共 121 个 `adapter_*.xml`，另有 item/view 布局在阶段 C/D 中按组件族迁移。
- adapter 布局：`app/src/main/res/layout/adapter_*.xml`、`app/src/mobile/res/layout/adapter_*.xml`、`app/src/leanback/res/layout/adapter_*.xml`；
- `item_*.xml`、`view_empty*.xml`、`view_progress*.xml`、`view_wall*.xml`；
- 适配器 Java：`app/src/main/java/com/fongmi/android/tv/ui/adapter/*.java` 及 mobile/leanback 对应 adapter；
- 首页/搜索/历史/收藏相关 Activity/Fragment 的布局引用。

规则：

1. 列表项统一 `Widget.WebHTV.ListItem`；主标题 `onSurface`，副标题 `onSurfaceVariant`，分隔 `outlineVariant`。
2. 选中使用 `secondaryContainer`/`onSecondaryContainer` 或 `primaryContainer`/`onPrimaryContainer`；禁用使用 `onSurface` 低透明度。
3. 卡片统一 `Widget.WebHTV.Card`：手机 12dp、TV 8–12dp 圆角；surfaceContainer；图片文字叠 `overlayDark`。
4. 海报 2:3、横向卡片 16:9、头像 1:1；TV 横向间距 20dp；不因标题长度改变卡片尺寸。
5. TV 焦点统一 `focus` ring + `focusScale=1.1`；颜色不能是唯一状态。
6. 首页站点、搜索范围、站源、历史、收藏、推荐、剧集网格逐批迁移；每批后运行站点弹窗回归和列表点击/长按/分页回归。

阶段 C DoD：

- allowlist 外 adapter/item 布局无原始业务色；
- 列表/卡片几何测试（新增 `UiLayoutSourceTest`）断言比例、20dp TV 间距、2:3/16:9；
- mobile/leanback 编译通过；
- 手机与 TV 各完成一张首页、一张搜索结果、一张历史/收藏截图；
- 分页、顺序、点击、长按、选中、焦点恢复无回归。

### 18.5 阶段 D：详情与播放器控制层

**逐文件清单**：

- `app/src/main/res/layout/activity_tmdb_detail.xml`、TMDB adapter/item 布局；
- `app/src/main/java/com/fongmi/android/tv/ui/detail/**`、`ui/activity/TmdbDetailActivity.java`、`TmdbHeaderView`、`TmdbCinemaTheme`；
- `app/src/mobile/res/layout/view_control_*.xml`、`app/src/leanback/res/layout/view_control*.xml`；
- `view_player_osd.xml`、`adapter_player_osd.xml`、`PlayerOsdController`、`VodPlayerUiController`、`VodPlayerUiHost`；
- 播放器控制相关 `Control`、`DisplayControl`、`VideoActionButton`、`AudioActionButton`、`ControlSheet*` styles。

规则：

1. 播放器画面、解码、字幕、弹幕、音轨、倍速、渲染路径不改；只改控制层/OSD/外层 Dialog。
2. `Control`/`DisplayControl` 统一 `playerControl`、`playerControlMuted`、`playerControlActive`；浅色画面上使用 `playerScrim`。
3. 黄色只保留给 `playerControlActive`，不再作为普通 UI 焦点或成功色。
4. TMDB 详情的 `#0F141A`/`#141A20`/`#3A4652` 映射到 surface 层级；详情页面仍可保留轻量专属深度效果，但必须通过 token。
5. 详情按钮、线路、选集、相关视频的选中/焦点统一；图标按钮必须有 contentDescription。
6. 播放器 OSD 的最小字号、对比度、进度可读性按 6.2/13.3 验证。

阶段 D DoD：

- allowlist 外详情/播放器控制布局无业务固定色；
- `UiStyleSourceTest` 覆盖 playerControl 不得等于 primary/focus；
- mobile/leanback 编译通过；
- 设备播放一个真实 VOD 与一个直播流，完成进入、暂停、切换线路、换集、倍速、字幕、返回；
- 明确记录播放内核/解码/性能未变（不重建 native）。

### 18.6 阶段 E：Web 资源统一

**逐文件清单**：`app/src/main/assets/css/ui.css`、`index.html`、`manage.html`、`parse.html`、`reader.html`、`webhome/eclipse.html`、`webhome/eclipse-detail.html`，以及 `app/src/main/java/com/fongmi/android/tv/web/WebHomeThemeBridge.java` 的只读 token 暴露。

规则：

1. `ui.css` 顶层改为 `--webhtv-*`；保留 `--md-*` 别名一轮。
2. 管理页删除 `body.manage-page` 蓝色覆盖，改为共享 `--webhtv-primary`；WebHome 删除绿色专属 palette，改为共享 token。
3. reader/eclipse 页面的内联 hex 迁移为 CSS 变量或受控页面 token；不得新增内联样式。
4. `prefers-color-scheme` 与 `data-theme` 只切换变量；`data-theme-source` 区分 native 注入和页面本地。
5. WebTheme bridge 只注入颜色/形状/间距的只读快照；页面无法覆盖原生 token。

阶段 E DoD：

- `assets/**/*.{css,html}` 在 allowlist 外无原始 hex；
- Web 静态检查通过；浏览器/模拟器分别验证手机浅色/深色、TV 深色；
- 管理页、WebHome、reader 三个页面截图对比一致；
- 远程主题失败只回退页面，不影响原生设置或播放。

### 18.7 阶段 F：清理与高级主题

规则：

1. 先删除已无引用的旧 `LightDialog` 别名、重复 `dialog_*` 颜色状态列表、`shape_*` 重复资源；
2. `scripts/check_ui_tokens.sh` 必须显示 allowlist 外为零；
3. 高级主题编辑器只有在 A–E 全部验收后独立审批；可复用 `docs/theme-color-system-design-20260907.md` 的 profile/import/rollback 设计，但不得阻塞统一。

阶段 F DoD：

- 静态检查零未豁免；
- 无旧固定 palette 引用；
- 主题编辑导入/导出/回滚测试通过（若实现）；
- 回滚可单独回到 E 阶段状态。

### 18.8 接口级实现骨架（阶段 A 可直接照此实现）

以下签名是开发契约，不是最终代码；字段名必须与 5.2 token 表一致。实现时把默认值同时放在 XML 和 Java 会导致漂移，因此约定：**唯一默认值来源是 `ThemeTokens`，Android 资源只做映射与 flavour 覆盖；`ThemeController` 负责把当前 token 写入主题属性。**

```java
package com.fongmi.android.tv.theme;

public enum ThemeMode { SYSTEM, LIGHT, DARK }

public enum ThemeSeed { NONE, WALLPAPER, EXPLICIT, CONTENT }

public final class ThemeTokens {
    // 唯一默认值来源：light/dark 各一份，字段名与 5.2 一致。
    public static ThemeTokens light();
    public static ThemeTokens dark();
    public int colorPrimary();
    public int colorOnPrimary();
    public int colorPrimaryContainer();
    public int colorOnPrimaryContainer();
    public int colorSecondary();
    public int colorOnSecondary();
    public int colorSecondaryContainer();
    public int colorOnSecondaryContainer();
    public int colorTertiary();
    public int colorOnTertiary();
    public int colorError();
    public int colorOnError();
    public int colorErrorContainer();
    public int colorOnErrorContainer();
    public int colorSuccess();
    public int colorOnSuccess();
    public int colorSuccessContainer();
    public int colorOnSuccessContainer();
    public int colorWarning();
    public int colorOnWarning();
    public int colorWarningContainer();
    public int colorOnWarningContainer();
    public int colorSurface();
    public int colorSurfaceDim();
    public int colorSurfaceBright();
    public int colorSurfaceContainerLowest();
    public int colorSurfaceContainerLow();
    public int colorSurfaceContainer();
    public int colorSurfaceContainerHigh();
    public int colorSurfaceContainerHighest();
    public int colorOnSurface();
    public int colorOnSurfaceVariant();
    public int colorOutline();
    public int colorOutlineVariant();
    public int colorInverseSurface();
    public int colorInverseOnSurface();
    public int colorInversePrimary();
    public int colorScrim();
    public int colorShadow();
    public int colorFocus();
    public float focusScale();
    public int colorPlayerControl();
    public int colorPlayerControlMuted();
    public int colorPlayerControlActive();
    public int colorPlayerScrim();
    public int colorHealthGood();
    public int colorHealthWarn();
    public int colorHealthBad();
    public int colorOverlayLight();
    public int colorOverlayDark();
    public ThemeTokens requireContrast();
}

public final class ThemeResolver {
    public static ThemeTokens resolve(ThemeMode mode, ThemeSeed seed, int seedColor,
                                      int wallpaperColor, boolean systemDark);
    // 失败策略：单个 seed 推导失败 -> 默认 palette；整个 resolve 不得抛出到 Activity。
}

public final class ThemeController {
    public static void apply(AppCompatActivity activity, ThemeTokens tokens);
    public static ThemeTokens current();
    public static void refresh();
    // apply 只设置主题属性、status/navigation bar 与 theme overlay，不重建页面。
}

public final class ThemeWebBridge {
    public static String snapshotJson(ThemeTokens tokens); // 只读、受控字段、固定上限
}
```

Android 资源骨架（实现时必须覆盖全部字段，缺一即契约测试失败）：

```xml
<!-- app/src/main/res/values/webhtv_attrs.xml -->
<resources>
    <attr name="webhtvColorPrimary" format="color" />
    <attr name="webhtvColorOnPrimary" format="color" />
    <attr name="webhtvColorSurface" format="color" />
    <attr name="webhtvColorSurfaceContainer" format="color" />
    <attr name="webhtvColorOnSurface" format="color" />
    <attr name="webhtvColorOnSurfaceVariant" format="color" />
    <attr name="webhtvColorOutline" format="color" />
    <attr name="webhtvColorFocus" format="color" />
    <attr name="webhtvColorPlayerControl" format="color" />
    <attr name="webhtvColorPlayerControlActive" format="color" />
</resources>

<!-- app/src/main/res/values/webhtv_tokens.xml -->
<resources>
    <color name="webhtv_color_primary">#0B57D0</color>
    <color name="webhtv_color_on_primary">#FFFFFF</color>
    <color name="webhtv_color_surface">#F8FAFD</color>
    <color name="webhtv_color_on_surface">#1A1C1E</color>
    <color name="webhtv_color_on_surface_variant">#44474F</color>
    <color name="webhtv_color_outline">#74777F</color>
</resources>

<!-- app/src/main/res/values-night/webhtv_tokens.xml -->
<resources>
    <color name="webhtv_color_primary">#A8C7FA</color>
    <color name="webhtv_color_on_primary">#062E6F</color>
    <color name="webhtv_color_surface">#101418</color>
    <color name="webhtv_color_on_surface">#E2E2E9</color>
    <color name="webhtv_color_on_surface_variant">#C4C6D0</color>
    <color name="webhtv_color_outline">#8E9099</color>
</resources>
```

阶段 A 的测试 fixture 约定：

| Fixture | 输入 | 期望 |
| --- | --- | --- |
| `defaultLight` | `LIGHT, NONE` | 等于 5.2 light 默认值 |
| `defaultDark` | `DARK, NONE` | 等于 5.2 dark 默认值 |
| `seedBlueLight` | `LIGHT, EXPLICIT, #0B57D0` | primary/onPrimary 派生且对照度达标 |
| `seedWallpaperDark` | `DARK, WALLPAPER` | 不直接返回壁纸色；surface 仍为深色族 |
| `badSeed` | 极亮/极暗 seed | 自动修正或回退，不抛异常 |
| `allContrastPairs` | light/dark 默认与 seed | 5.2.1 + 5.2.2 全部达标 |


---

## 19. 可执行检查脚本设计

阶段 A 新增 `scripts/check_ui_tokens.sh`，职责是把“视觉规则”变成每次提交可验证的门槛。脚本必须无额外依赖，除 `bash`、`rg`、`python3` 外不调用网络。

### 19.1 检查项与参数

```text
scripts/check_ui_tokens.sh
scripts/check_ui_tokens.sh --baseline      # 只报告计数，不失败
scripts/check_ui_tokens.sh --strict        # allowlist 外任一命中即失败
scripts/check_ui_tokens.sh --stage B       # 检查 B 阶段路径集合
scripts/check_ui_tokens.sh --tokens-file path
scripts/check_ui_tokens.sh --allowlist docs/ui-token-allowlist.txt
```

| 检查 | 失败条件 | 适用阶段 |
| --- | --- | --- |
| 原始 hex | allowlist 外命中且 `--strict` | A 基线报告；B–F 强制 |
| 旧 LightDialog 名称 | 新引用 `Theme.WebHTV.LightDialog` / `Widget.WebHTV.LightDialog` | B+ |
| 固定浅色 palette | 新增 `#202124`/`#5F6368`/`#1A73E8`/`#E8F0FE` | B+ |
| 播放器控制色 | `playerControl*` 之外使用 `#FFD35C` 等播放器专用色 | B+ |
| token 契约 | 5.2 必需 token/ style/ selector 缺失 | A+ |
| 对照度 | 5.2.1 任一对低于门槛 | A+ |
| Web token | assets 页面引用未定义 `--webhtv-*` | E+ |
| 状态覆盖 | 交互 selector 缺少 enabled/focused/pressed/disabled/selected 中必需项 | B+ |

### 19.2 脚本输出契约

脚本必须输出可对比的基线摘要，例如：

```text
UI_TOKEN_BASELINE layouts=382 hex_layouts=120 drawables=641 hex_drawables=212 colors=77
UI_TOKEN_SCOPE stage=B dialogs=137 settings=19 violations=0 allowlisted=41
UI_TOKEN_CONTRAST pairs=18 failures=0 min=4.28 (outline/surface, warning threshold=3.0)
UI_TOKEN_STATUS PASS
```

开发验收要求：

- 阶段 A 允许 baseline 模式报告非零，因为存量未迁移。
- 阶段 B–E 对应 scope 的 `violations` 必须为 0；其他未迁移 scope 以 `WARN` 显示，不阻断本阶段。
- 阶段 F `--strict` 全仓库必须为 0。
- 任何 “降低门槛”“扩大 allowlist” 修改都必须单独提交并在文档记录理由，不能顺手放宽。

### 19.3 需要新增/更新的自动化测试

| 测试文件 | 断言 |
| --- | --- |
| `app/src/test/java/com/fongmi/android/tv/theme/ThemeResolverTest.java` | seed 解析、fallback、模式、不可变、低对比度回落 |
| `app/src/test/java/com/fongmi/android/tv/theme/ThemeContractTest.java` | 5.2 全套 token 存在、5.2.1 对照度全部达标、player token 隔离 |
| `app/src/test/java/com/fongmi/android/tv/ui/style/UiStyleSourceTest.java` | 旧 style 不再新增、按钮/输入/style 名称存在、无旧浅色 palette |
| `app/src/test/java/com/fongmi/android/tv/ui/style/UiLayoutSourceTest.java` | 卡片比例、TV 20dp、安全区、焦点/状态、无未豁免固定色 |
| `app/src/test/java/com/fongmi/android/tv/ui/dialog/DialogRoundedCornerSourceTest.java` | 保持 22dp，扩展到新 dialog 模板 |
| `app/src/test/java/com/fongmi/android/tv/web/WebThemeTokenBridgeTest.java` | token 只读、schema/范围校验、失败不写原生 |

### 19.4 阶段 A 的最小验证命令

```bash
git diff --check
bash scripts/check_ui_tokens.sh --baseline
./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.theme.*'
./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac
./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac
```

阶段 B–D 追加对应 source test；阶段 E 追加 Web 资源扫描。每个阶段只跑本阶段最小命令，不重复已通过的阶段 A 全量测试。

---

## 20. 验收执行手册

### 20.1 每阶段固定验收闭环

1. **代码/资源自查**：`git diff --check`、对应 `--stage` 检查。
2. **单元/静态测试**：本阶段新增/更新测试全部通过。
3. **编译**：覆盖 mobile 与 leanback 的 Java/资源任务。
4. **设备覆盖安装**：使用 `bash scripts/build_arm64_debug_install.sh --flavor mobile --serial 192.168.50.3:5559` 和 `--flavor leanback --serial 192.168.50.3:5561`；不卸载现有包。
5. **场景截图/目视**：按 20.2 矩阵执行，保留截图路径、设备、分辨率、主题模式、结果。
6. **行为回归**：播放、站点、搜索、历史、同步、代理至少各一条代表用例；播放器阶段需真实 VOD + 直播。
7. **主题边界**：浅色/深色切换、壁纸 seed、Android 9 fallback、远程主题失败逐项验证。
8. **任务文档**：更新本文件的阶段状态、证据、未验证项和下一动作。
9. **提交与 recovery tag**：`task_guard.sh finish` 原子提交；不推送、不改历史。

### 20.2 设备验收矩阵

| ID | 设备/模式 | 场景 | 通过标准 | 证据 |
| --- | --- | --- | --- | --- |
| B-M-L | mobile / light | 设置页 + 弹窗 | 文字、输入、按钮、Dialog 对比度达标 | 截图 + 静态测试 |
| B-M-D | mobile / dark | 同上 | 无浅色孤岛 | 截图 + 对比度 |
| B-T-D | leanback / dark | Dialog + 设置 | 深色面板统一，焦点清晰 | TV 截图 + 焦点遍历 |
| B-A9 | mobile / Android 9 | 手动 seed / 壁纸 seed | 不依赖系统动态色，fallback 正确 | 设备截图 + resolver 日志 |
| C-M | mobile | 首页、搜索、历史、收藏 | 卡片、列表、选中一致，功能无回归 | 截图 + 点击/长按 |
| C-T | leanback | 首页/列表/卡片 | D-pad 焦点、20dp、2:3/16:9 正确 | TV 截图 + 方向键遍历 |
| D-M | mobile | 详情 + 播放 | 控制层可读，播放/字幕/倍速无回归 | 截图 + 播放日志 |
| D-T | leanback | 详情 + 播放 | OSD、焦点、进度、选中无回归 | 截图 + D-pad 场景 |
| E-W | mobile/TV WebView | 管理页、WebHome、reader | 浅/深一致，远程失败页面回退 | 截图 + 日志 |

### 20.3 视觉/可读性通过标准

- 普通文本 ≥4.5:1；大字、图标、焦点环、非文本组件 ≥3:1。
- 焦点、选中、按下、禁用四种状态同时可区分；颜色不是唯一信号。
- 长标题、长错误、长路径、放大字体不裁剪关键操作。
- TV 所有可交互元素可用 D-pad 到达并按返回键退出；焦点不会落入不可见区域。
- 图片上的文字区域满足对比度；否则视为不通过。
- 播放画面、字幕、弹幕、音轨、倍速、清晰度和性能相对基线无明显变化。

### 20.4 回归与异常处理

- 出现播放回归、音频/字幕异常、站点数据错误：立即回滚当前阶段，不继续下一阶段。
- 出现视觉不一致但功能正常：必须落到具体 token/组件规则修正，不接受“看起来差不多”。
- 某个页面无法在不改变业务行为的情况下迁移：保留 allowlist 并记录具体原因、负责阶段和替代方案，不允许无记录地继续硬编码。
- 任何阶段失败都不修改已发布 tag；用新的修复提交和新 recovery tag 记录。

---

## 21. 需求追溯矩阵

| 用户要求 | 设计章节 | 实施阶段 | 验收证据 |
| --- | --- | --- | --- |
| 背景色统一 | 5.2 `surface*`、7.2、9.3–9.7 | A/B/C/D/E | 截图 + 静态检查 + 对比度 |
| 字体颜色统一 | 5.2 on*、6.2、9.1–9.9 | A/B/C/D/E | 文本对比度 + 设备场景 |
| 按钮风格统一 | 9.1、10.2、20.2 | A/B/C/D | style/source test + 截图 |
| 弹出框统一 | 9.7、10.2、18.3 | A/B | Dialog source test + 三端截图 |
| 多种风格收敛为一种 | 3.1、3.2、5–10、12 | A–F | 18.2–18.7 DoD + `--strict` 零违例 |
| 结合业内主流实现 | 3、5–8 | A–F | Material 3 / Android TV / WCAG 证据表 |
| 先输出设计文档 | 本文 | 本次 | 文档已提交并带 recovery tag |
| 可直接指导开发 | 18、19 | A–F | 文件清单、步骤、脚本、测试和命令齐备 |
| 可直接指导验收 | 20、21 | A–F | 矩阵、门槛、证据和回滚齐备 |
| 可直接指导阶段 A 开发 | 18.1、18.2、18.8、19 | A | 文件清单 + 接口骨架 + fixture + 命令 |
| 可直接指导阶段 B 开发 | 18.3 | B | 137 dialog/19 setting 精确清单与 DoD |
| 可直接指导阶段 C 开发 | 18.4 | C | 121 adapter 精确清单与 DoD |
| 可直接指导阶段 D 开发 | 18.5 | D | 详情/控制层清单与播放回归 |
| 可直接指导阶段 E 开发 | 18.6、19 | E | Web 变量、页面回退与静态检查 |
| 可直接指导阶段 F 清理 | 18.7、19.1 | F | strict 零违例与独立回滚 |


---

## 22. 文档完成判定

本文达到“完整版”的条件是：

1. 设计决策、业界证据、token 默认值和对比度门槛已冻结，开发无需再猜颜色。
2. 每个阶段都有 allowlist 路径、逐文件范围、实现步骤、自动化测试、编译命令和设备场景。
3. 验收使用可执行脚本 + 可测量对比度 + 设备截图/行为回归，而不是“看起来统一”。
4. 每阶段独立可提交、可回滚；高级主题编辑器不阻塞统一工作。
5. 用户批准后从阶段 A 开始；阶段 A 不允许改动任何页面像素。
