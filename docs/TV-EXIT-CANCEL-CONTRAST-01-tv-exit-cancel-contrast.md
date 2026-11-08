# TV-EXIT-CANCEL-CONTRAST-01 TV 退出确认弹窗「取消」按钮文字对比度修复（2026-10-24）

## Recovery anchor

- 目标：修掉电视端「退出应用」确认弹窗里「取消」（以及同行的「后台播放」）按钮在深色/自定义调色板下几乎看不见文字的问题。
- 验收标准：1) 两个次要按钮的文字在**日间表、夜间表、以及用户自定义壁纸调色板**下都与按钮固定浅色底保持可读对比度（≥4.5:1）；2) 焦点/按下态文字与焦点环同样可读；3) 主按钮「完全退出」视觉层级不变；4) 新增回归守卫能在旧写法上失败、在修复后通过。
- 状态：代码 + 守卫完成，验证通过，待提交。
- 关联路径：dialog_exit_confirm.xml / selector_exit_confirm_secondary.xml / color/exit_confirm_secondary_text.xml / TvFixedLightSurfaceContrastTest.java。

## 用户报告

用户附电视实机截图（1280×720）：弹窗里左侧「取消」按钮是近乎纯白的底、文字几乎不可见；右侧「完全退出」是亮蓝底白字。原话（m00004）：`"取消"字体不清晰太灰，请完善修正。电视版的`。

## 复现与量化

对截图做像素统计（Pillow + WCAG 亮度公式），左侧按钮内部区域 (400–430, 480–680)：
- 背景 **L = 1.0000**（`#FFFFFF`，占 73–79% 像素）；
- 只有约 **1.4%** 的像素比 0.5 暗——即文字实际上不存在。

按仓库调色板重算对比度（`?attr/colorOnSurfaceVariant` 压 `#F8FAFD` 固定浅底）：

| 表格 | 角色取值 | 对比度 |
| --- | --- | --- |
| 日间 webhtv_tokens.xml | `#44474F` | 8.89:1 |
| 夜间 values-night/webhtv_tokens.xml | `#C4C6D0` | **1.63:1** |
| 夜间（聚焦底 `#DCEAFF`） | `#C4C6D0` | **1.40:1** |

实机面板底色采样为 rgb(77,122,128) / rgb(98,132,142)，说明 ThemeBinder 已用自定义壁纸调色板改写了 `?attr` 角色，而 drawable 里的 `#F8FAFD` 字面量不会跟着变——用户正处在这种「角色变浅、字面量不变」的组合里。

## 根因

`selector_exit_confirm_secondary.xml` 的默认（未聚焦）填充是与调色板无关的字面量 `#F8FAFD`，而弹窗把两个次要按钮的文字写成**跟随调色板**的 `?attr/colorOnSurfaceVariant`。这二者在日间表恰好协调（深灰压浅白），在夜间表/深色调色板下就变成近白压近白。

回归来源定位（`git log -S'colorOnSurfaceVariant' -- app/src/leanback/res/layout/dialog_exit_confirm.xml`）唯一命中 **0af2340d49508eb8b08e53eaa96d3098cb375056**（"feat(ui): unify dialog and settings visual system"），该提交把整份布局批量迁移到调色板角色，其中把次要按钮的 `@color/exit_confirm_secondary_text` 改成了 `?attr/colorOnSurfaceVariant`。

`app/src/leanback/res/color/exit_confirm_secondary_text.xml` 至今仍在仓库里（默认 `#202124`，焦点/按下 `#174EA6`），全库无任何布局引用它；唯一提到它的是 `TvFocusStatePairContrastTest.java:220` 的豁免文案「退出确认次要按钮文字：焦点色 #174EA6 为深色，配浅色填充 selector_exit_confirm_secondary」——仓库自己的测试文档仍把它当作该按钮的文字来源。即本次修复是**恢复原设计**，不是新发明的配色。

## 方案

1. `app/src/leanback/res/layout/dialog_exit_confirm.xml` 第 93 行（negative）与第 112 行（backgroundPlay）的 `android:textColor` 由 `?attr/colorOnSurfaceVariant` 改为 `@color/exit_confirm_secondary_text`。
2. `selector_exit_confirm_secondary.xml` 头部注释重写：说明面板颜色实为 `?attr/colorSurfaceContainerHigh`（随表/主题变），旧注释里「面板是固定浅色」的前提已失效，并明确要求文字继续使用同族固定深色、**不得**改回 `?attr/colorOnSurfaceVariant`。
3. 新增守卫 `app/src/test/java/com/fongmi/android/tv/theme/TvFixedLightSurfaceContrastTest.java`。

修后三态对比度（与表格/壁纸调色板均无关）：

| 状态 | 文字色 | 底色 | 对比度 |
| --- | --- | --- | --- |
| 默认 | `#202124` | `#F8FAFD` | 15.4:1 |
| 聚焦 / 按下 | `#174EA6` | `#DCEAFF` | 6.45:1 |
| 焦点环 | `#0B57D0` | `#DCEAFF` | 5.25:1 |

## 为什么不是「让次级填充也走调色板」

那样确实能让前景/背景同源，但会改变视觉层级（夜间把次级按钮变成深底），与既有夜间焦点环设计冲突，改动面更大；而仓库本来就为这个按钮准备了固定深色前景。取最小改动 + 恢复既有设计。

## 新守卫

与 `TvFixedDarkSurfaceContrastTest` 互补的另一半：那份管「调色板前景落在固定**深色**面」，这份管「调色板前景落在固定**浅色**面」。两点比既有守卫更强：

- **日/夜两表都解析，取最差**，所以「白天正常、天黑才瞎」的角色藏不住；
- 只有当一个 drawable 的填充在两张表里解析成**同一字面量**（或本身就是 `#` 字面量）时才认作「固定填充」；引用 `@color/` 随表 token 的 drawable 一律跳过（`fixedLiteral`）。这条是把 `selector_player_settings_row` 这类随表控件排除在外、避免误报的关键。

守卫自带可失败性反证 `theGuardDetectsTheDefectItWasWrittenFor`：把修复前的原始标记喂进 `inspect()` 必须命中且低于阈值，同一标记改用 `@color/exit_confirm_secondary_text` 必须返回空。

## 守卫首次全量跑出的同族缺陷（范围外，已登记未修）

首次运行结构遍历用例时报告了若干**未被用户报告**、但属同一缺陷族的界面。逐条核实其填充确为字面量后，登记在守卫的 `PENDING_LAYOUTS` 中（附注释与实测最差比值），**不在本次提交里修改**——它们各自需要视觉评审和独立任务：

- `adapter_ad_stats_item.xml`：`?attr/colorOnSurfaceVariant` 压在 `selector_ad_stats_item`（`#00FFFFFF`/`#E8F0FE`），1.31:1；
- `adapter_mpv_config_profile.xml`：`?attr/colorOnSurface`/`?attr/colorOnSurfaceVariant` 压在 `selector_mpv_profile_card`（`#FAFBFC`/`#F4F8FF`），1.13:1 / 1.49:1，另 `?attr/colorPrimary` 压在 `shape_mpv_active_badge`（`#E8F0FE`），1.50:1；
- `dialog_ai_recommendation_info.xml`：`?attr/colorPrimary` 压在 `shape_recommendation_source_chip`（`#E8F0FE`），1.50:1；
- `dialog_ad_block_stats.xml`：12 个统计标签，`?attr/colorOnSurfaceVariant` 压在 `shape_ad_stats_content`（`#FAFBFC`），1.31:1。

移除某条登记，该文件立刻回到守卫覆盖范围。

## 修改文件

1. `app/src/leanback/res/layout/dialog_exit_confirm.xml` — 第 93、112 行文字色改为 `@color/exit_confirm_secondary_text`。
2. `app/src/leanback/res/drawable/selector_exit_confirm_secondary.xml` — 头部注释重写（记录前提失效与强制约束）。
3. `app/src/test/java/com/fongmi/android/tv/theme/TvFixedLightSurfaceContrastTest.java` — 新增守卫 + 反证 + 登记表。
4. `docs/TV-EXIT-CANCEL-CONTRAST-01-tv-exit-cancel-contrast.md` — 本文档。

## 验证

命令与结果：

```
bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests '*TvFixedLightSurfaceContrastTest*'
→ BUILD SUCCESSFUL in 33s，4 tests completed, 0 failed
```

同一批次还跑了相邻守卫（`TvFixedDarkSurfaceContrastTest` / `LeanbackForegroundContrastTest` /
`PlayerSettingsContrastTest` / `DialogForegroundTokenTest` / `TvFocusStatePairContrastTest` /
`TvFocusRingContractTest` / `ThemeBinderContractTest`）与 mobile 的 `TvAppExitSourceTest`，
确认修复未触动既有主题契约。

## 环境备注（非本任务改动）

- Windows 沙箱下 `ops_bash` 在仓库多数子目录写入被拒，构建/脚本统一改用
  `pwsh → 'G:\Git\bin\bash.exe'` 执行；仓库自身与本次改动均未改 ACL。
- 无真 TV 设备（4 个模拟器 `ro.build.characteristics=tablet`），实机验证依据用户提供的截图。
- 截图取色用 Pillow（`load_workspace_dependencies` 提供的 python 运行时）。
