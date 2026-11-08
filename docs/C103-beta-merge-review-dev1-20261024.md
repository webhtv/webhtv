# C103-beta-merge-review-dev1-20261024

## Recovery anchor

- **目标**：把 `origin/beta@7c725e4d7e` 合并进 `dev1`（零冲突、零丢失），逐行评审 `dev1` 仅有的 5 个已提交未推送提交（屏显媒体信息徽标 `4a55998f35`/`49e5b2c4bc`/`7d4bee6ce1`/`0dc165a9a2`/`64ee84e68e`）与合并后的交汇点，修复必修项并定向验证，复评通过后提交、推送 `dev1`、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突；`beta` 带入 54 路径的内容在合并树中与 `beta` 逐 blob 一致（仅 4 个自动合并文件除外）。
  2. 评审覆盖 `dev1` 相对 `beta` 的全部 24 个非二进制路径（16 M + 5 A 非图 + 100 张 `badge_*.webp` 资产 + 测试）；`beta` 带入内容按「已评审 + 0 冲突 + 路径交集仅 4 个且已逐行复核」处理并留证。
  3. 每个必修项修复可复核；修复后第二轮复评通过。
  4. 定向单测（双 flavor arm64 Debug）一次通过：徽标解析、设置同步、控制栏 OSD、掉帧口径、主题/遮盖层/更新卡、Python 桥、TV 分类筛选。
  5. 本轮改动经任务守卫 `finish` 落盘并创建 recovery tag；推送 `dev1`；创建到 `beta` 的中文 PR（描述含具体改动内容）；随后拉取远端最新代码。
- **Lane**：`standard`
- **Scope**：`README.md`、`app/src`、`catvod/src`、`chaquo/src`、`docs`
- **任务守卫**：`C103-BETA-MERGE-DEV1-OSD-20261024`，branch `dev1`，base HEAD `64ee84e68e5b036feb7a1ca2c581c3d996e82178`，受保护脏路径 0
- **当前状态**：合并、评审、验证、复评、提交、推送、PR、拉取均已完成；工作树干净
- **下一动作**：无；等待 PR #487 评审/合并（本轮只创建 PR，不合并）

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `7c725e4d7ee43ca0a187e420b224b33bb0a39a6a`（23 个提交，含 14 个非合并提交：C100/C101/C102、E-SP12 掉帧按播放时长归一、PY-BRIDGE-DEPEND-01 依赖下载桥、主题第 17 槽 `playerScrimOpacity`、TV 分类长按重置筛选、更新卡调色板等） |
| 本地 | `dev1` = `64ee84e68e5b036feb7a1ca2c581c3d996e82178`（领先 `origin/dev1` 5 个提交：`4a55998f35`/`49e5b2c4bc`/`7d4bee6ce1`/`0dc165a9a2`/`64ee84e68e`，均未推送） |
| merge-base | `4dda4b56b8d67e0cb78e6617f7b0f776591383a9` |
| 冲突 | **0**（`git merge --no-ff --no-commit origin/beta` 自动合并，`git diff --diff-filter=U` 为空） |
| 自动合并文件 | 4 个：`app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java`、`app/src/main/res/values/strings.xml`、`app/src/main/res/values-zh-rCN/strings.xml`、`app/src/main/res/values-zh-rTW/strings.xml` |
| 带入路径 | **54** 个（`app/src` 39、`chaquo/src` 3、`docs` 8、其余 4），`2889 insertions / 36 deletions` |
| 路径交集 | 4 个（上表 4 个自动合并文件）；`beta` 不含任何 `badge_*.webp`，本地 `dev1` 的 100 张牌图无冲突来源 |
| 丢失面 | 0（暂存区 54 路径中 50 个 blob 与 `origin/beta` 完全一致，4 个自动合并文件为「beta 内容 + 本地徽标改动」的并集，逐行复核见 §3） |

## 2. 评审范围与跳过依据

- **全量评审对象**：`dev1` 相对 `origin/beta` 的全部非二进制差异，共 24 个路径（`git diff --name-status 4dda4b56b8 64ee84e68e`：16 M + 5 A 非图 + 1 M 测试 + 100 A `badge_*.webp`；合计 1635 insertions / 13 deletions）：
  - 新增实现：`app/src/main/java/com/fongmi/android/tv/player/MediaBadgeCatalog.java`（100 条规则 / 13 个 Group）、`app/src/main/java/com/fongmi/android/tv/player/MediaBadgeResolver.java`（运行时优先 + 文件名兜底）、`app/src/test/java/com/fongmi/android/tv/player/MediaBadgeResolverTest.java`（24 例）。
  - 屏显渲染：`app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java`（+329/-3）、`app/src/main/res/layout/view_player_osd.xml`。
  - 设置与开关：`app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java`、`app/src/main/java/com/fongmi/android/tv/bean/Backup.java`、`app/src/main/java/com/fongmi/android/tv/ui/dialog/DisplayDialog.java`、`app/src/main/res/layout/dialog_display.xml`、`app/src/{leanback,mobile}/res/layout/dialog_control.xml`、`app/src/{leanback,mobile}/java/.../ControlDialog.java`、`app/src/leanback/java/.../activity/SettingPlayerActivity.java`、`app/src/mobile/java/.../ui/fragment/SettingPlayerFragment.java`、`app/src/{leanback,mobile}/res/layout/` 设置页布局、三语 `strings.xml`。
  - 音频兜底：`app/src/main/java/com/fongmi/android/tv/player/AudioPlaybackDiagnostics.java`。
  - 测试与文档：`app/src/testMobile/java/.../ControlDialogOsdSettingsTest.java`、`docs/osd-media-badges.md`。
- **跳过**：`beta` 带入的 54 路径内容全部已在 `docs/C100-*`/`docs/C101-*`/`docs/C102-*`/`docs/E-SP12-*`/`docs/PY-BRIDGE-DEPEND-01-*`/`docs/TV-FILTER-RESET-01-*` 中评审；本轮合并 0 冲突，54 路径中 50 个 blob 与 `beta` 逐字节一致（`git rev-parse :<path>` vs `origin/beta:<path>`），因此带入内容满足「已评审 + 无合入冲突」。4 个自动合并文件不跳过，见 §3 交汇点复核。

## 3. 首轮评审发现与处置

**必修项：0**。以下为逐面复核结论与留档记录项。

| 复核面 | 结论 |
| --- | --- |
| `PlayerOsdController` 与 beta `E-SP12` 掉帧口径交汇 | 共存无冲突：beta 的 `droppedFrameText`/`excessiveDroppedFrames`/`DROPPED_FRAMES_PER_SECOND_DIAGNOSIS=4d`/`DROPPED_FRAMES_RATE_MIN_PLAY_TIME_MS=30_000L`/`DROPPED_FRAMES_RAW_COUNT_DIAGNOSIS=60`/`frameDropRateFormat=new DecimalFormat("0.0")` 全部保留；本地只在 `suppressed` 分支加 `renderMediaBadgesOnly()`、在控制栏可见分支加 `setMediaBadges(player)`、在隐藏分支加徽标 `GONE`+清签名，未触碰掉帧逻辑 |
| 三语 `strings.xml` | 各 +9 行：`select_player_osd` 末尾追加 `@string/display_media_badges`（第 8 项），新增 `display_media_badges`/`osd_media_badge_position`/`select_media_badge_position`（四角）；与 beta 新增的 `theme_editor_slot_player_scrim_opacity` 无重复键、无冲突 |
| 索引措辞一致性 | `getDisplayChecked()` 8 元素 ↔ 两 flavor `ControlDialog` 的 `displays` 8 项（`toggleDisplaySetting(index)` 按位翻写）；`getLiveDisplayChecked()` 保持 5 元素 ↔ `LiveControlDialog`；`DisplayDialog` 的 `enum Display` 7 项与 `initDisplayEvent`/`initPlayerOsdEvent` 覆盖一致 |
| 控制栏与 OSD 时序 | mobile `VideoActivity.showControl()` 先 `setOsdSuppressed(true)`（5622）后 `setControlsVisible(true)`（5661），`hideControl()` 反向（5675 → 5677），故 `suppressed && controlsVisible` 的徽标分支成立；leanback 只调 `setControlsVisible`，不会误触 `suppressed` 路径 |
| 徽标资产一致性 | `plates()` 100 个 `map.put` ↔ `MediaBadgeCatalog` 的 `plate` 键 ↔ `MediaBadgeResolver` 的 `P_*` 键 三方集合相等；`drawable-nodpi/badge_*.webp` 恰好 100 个、无缺失无多余、合计 496850 字节（约 485 KB），显式常量引用避免 release 资源压缩误删 |
| 空值安全 | `MediaBadgeResolver.resolve` 对 `video`/`details`/`audio`/`url`/`title` 全空返回空列表；`setMediaBadges` 对 `mediaBadges == null`/`player == null`/`audio == null` 均有保护；`MediaBadgeResolverTest` 24 例覆盖全 null 与单侧 null |
| 备份与迁移 | `Backup.APP_PREFS` 追加 `player_osd_media_badges`/`player_osd_media_badge_position`；`sanitizeMediaBadgePosition` 越界归 3；新键默认 `false` 不触发旧 OSD 迁移 |

**记录但未修（不阻塞、不顺手清理）**：

- **O1**：`app/src/main/res/drawable/shape_player_osd_badge.xml`（269 字节）在 `app/src` 与 `docs` 全文 0 引用，是第 3 个提交 `7d4bee6ce1` 的旧卡片背景残留（后被 `categoryBackground(GradientDrawable)` 取代）。按「不顺手清理」保留，可后续单独删除。
- **O2**：100 张牌图全在 `drawable-nodpi`（不参与密度缩放），合计约 485 KB，是本功能的一次性 APK 体积成本；如需瘦身可改 `webp` 有损参数或按实际使用挑图。
- **O3**：`app/src/main/java/com/fongmi/android/tv/player/MediaBadgeCatalog.java:82` 的 `gq_hc_hdrip` 规则行长达 4552 字符，正则内嵌 U+2063/U+200C/U+200D/U+2064 隐形字符并含约 12 个排除分支；功能由单测锁定，但可读性差，后续可拆常量或加注释。
- **O4**：`AudioPlaybackDiagnostics` 的 `value.contains("mp4a.40")` 兜底是「media3 对 MP4/M4A 上报 `mp4a.40.x` 不含 `aac`」的实证结论，尚无对应单测；当前由设备实测覆盖。

## 4. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| mobile 定向单测 | `:app:testMobileArm64_v8aDebugUnitTest --tests "*MediaBadgeResolverTest*" --tests "*PlayerDisplaySettingSyncTest*" --tests "*ControlDialogOsdSettingsTest*" --tests "*DroppedFrameAccountingTest*" --tests "*ExoDecoderRuntimeSessionTest*"` | BUILD SUCCESSFUL（4m38s，exit 0） |
| leanback 定向单测 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests "*ThemeEditorPanelTest*" --tests "*TvFilterResetLongPressTest*" --tests "*MediaBadgeResolverTest*"` | BUILD SUCCESSFUL；`MediaBadgeResolverTest` 24 例、`ThemeEditorPanelTest` 2 例、`TvFilterResetLongPressTest` 4 例，均 0 失败 0 错误 |
| mobile 主题/桥接定向单测 | `:app:testMobileArm64_v8aDebugUnitTest --tests "*ThemeEditorContractTest*" --tests "*PlaybackMenuScrimTest*" --tests "*ThemeProfileCodecTest*" --tests "*ThemeProfileValidatorTest*" --tests "*UpdateVersionRowThemeTest*" --tests "*SettingPlaybackOverlayTest*" --tests "*PythonSpiderDiagnosticsWiringTest*"` | BUILD SUCCESSFUL（2m9s，exit 0）；测试报告 12/5/8/9/10/3/2 例全绿（合计 49 例，0 失败 0 错误） |
| 差异卫生 | `git diff --cached --check` | 空（无空白错误） |
| 合并完整性 | 54 路径 `git rev-parse :<path>` vs `git rev-parse origin/beta:<path>` | 仅 4 个自动合并文件不同，其余 50 个逐字节一致 |

## 5. 第二轮复评（验证后）

复评对象为「合并后、验证通过」的最终树（本轮未产生代码修复，因此复评即对首轮结论的独立重读）：

- `PlayerOsdController` 从 `start()`/`stop()`/`setSuppressed()`/`setControlsVisible()`/`update()`/`render()`/`renderMediaBadgesOnly()` 到 `setMediaBadges()`/`applyMediaBadgePosition()`/`createMediaBadge()`/`categoryBackground()`/`mediaBadgeColor()`/`plateResource()` 全链路重读：返回 `true` 才续排 1s 轮询，徽标可见时循环持续、控制栏隐藏即 `GONE` 且清签名，签名去重避免每秒 `addView` 触发测量/布局。
- `MediaBadgeResolver` 的运行时优先、文件名兜底、`.ts` 后缀不误判容器、`2560×1440` 到 `256×144` 九档边界与 `200×120` 文本兜底均由 24 例单测锁定，两个 flavor 均绿。
- 设置链路（`PlayerSetting` 8 项 ↔ `ControlDialog` 8 项 ↔ `DisplayDialog.MEDIA_BADGES` ↔ 设置页 `osdMediaBadgePosition` 行 ↔ `Backup.APP_PREFS`）闭合，无遗漏分支、无索引错位。
- `beta` 侧的 C100/C101/C102/E-SP12/PY-BRIDGE-DEPEND-01/主题第 17 槽内容在合并树中完整保留，未因自动合并被裁剪。

结论：**通过**，无遗留必修项；本轮无需代码修改，按已验证状态提交合并。

## 6. 交付坐标

- 合并提交：`1322505938cecc897fa2a60f9edffc9a75402bc6`（双亲 `64ee84e68e5b036feb7a1ca2c581c3d996e82178` + `7c725e4d7ee43ca0a187e420b224b33bb0a39a6a`）。
- recovery tag：`recovery/C103-BETA-MERGE-DEV1-OSD-20261024/20261024064903-1322505938ce`（本地保留，不推送）。
- 推送：`dev1`（`0f297ce6da..1322505938`），`origin/dev1` 与本地一致。
- PR：**#487** <https://github.com/Silent1566/webhtv/pull/487>（`base=beta`/`head=dev1`，只创建不合并）。
- 拉取：`git fetch origin --prune --tags` 后 `origin/beta` 仍为 `7c725e4d7e`（无新推进）；拉入新标签 `v5.6.0-beta-202610240855`（及 `v5.6.0-beta-202610221924`、若干个 `recovery/*`），本地已有同名发布标签按「不覆盖已发布标签」被 git 拒绝更新，符合预期。
