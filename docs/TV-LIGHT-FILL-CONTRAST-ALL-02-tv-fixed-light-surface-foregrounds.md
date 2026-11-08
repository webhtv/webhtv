# TV-LIGHT-FILL-CONTRAST-ALL-02 — 固定浅底上的调色板前景（同族全量修复）

## Recovery anchor

- **Objective**: 电视端与移动端所有「固定浅色字面量填充」上的文字/图标前景，都必须在日间与夜间两套色板下保持可读；用户报告的「退出确认弹窗取消按钮几乎隐形」是首个实例，本任务修复其余同族实例。
- **Lane / scope**: `quick-fix`，路径范围见本节末尾的 guard scope 列表。
- **Branch / base HEAD**: `dev4` @ `b829d104ef4480110f684003d37865e2af9e4aa6`（上一个任务 TV-EXIT-CANCEL-CONTRAST-01 的提交）。
- **Completed evidence**:
  - 全库 385 个 layout（leanback + main + mobile）结构化扫描，逐态（default/focused/pressed/selected/activated）判定有效填充，去重后共 **59 处 / 10 个文件**的真缺陷。
  - `TvFixedLightSurfaceContrastTest` 解析器升级（支持 `@color/` 状态选择器、`textColorHint`、`tint`、`tabTextColor`/`tabSelectedTextColor`，并把扫描范围扩到 mobile 源集），随后又扫出第二轮的 5 处同族实例，一并修复。
  - 单测 `:app:testLeanbackArm64_v8aDebugUnitTest`（本守卫 + 9 个相邻守卫）BUILD SUCCESSFUL。
  - `:app:assembleLeanbackArm64_v8aDebug` BUILD SUCCESSFUL，APK 已装到 `127.0.0.1:5561` 模拟器（`ui_night_mode=2`，即夜间模式）做端到端验证。
- **Unresolved risk**: `selector_exit_confirm_secondary`/`selector_ad_stats_item` 等宿主 drawable 的默认态是透明，文字实际压在更外层的面板上；守卫按「最近的有填充祖先」取值，已覆盖该路径，但若将来有人给这些 selector 加不透明的随表填充，需要重新评估。
- **后续修正（C103 合并后复审）**: 本任务原先只修了 leanback 版 `dialog_ad_block_stats.xml` 的 88dp 日志带，**mobile 版同族缺陷被遗漏**（mobile 源集在扫描范围内，但那条带用两表不同的 `@color/webhtv_color_scrim` 做底，`surfaceFills()` 的 `@color/` 分支要求「两表恒定」，于是整条带被静默跳过——测试通过而缺陷仍在）。C103 复审时补修并加固守卫，详见「## 第四轮」与「## 守卫升级」。
- **Rollback anchor**: `git reset --hard b829d104ef4`（本任务最初的单提交）。第四轮补修的 rollback 取 C103 会话的 base `62f0b777d2a`：`git reset --hard 62f0b777d2a`。

## 缺陷类别

同一个根因的三段式：

1. 某张 drawable 的填充是**字面量浅色**（`#F8FAFD` / `#FAFBFC` / `#E8F0FE` / `#D2E3FC` / `#F4F8FF` / `#EAF6EE` / `#F3F5F7` / `#F4F7FB` / `#FFFFFF`），两张色板下都不变。
2. 该 drawable 上的文字/图标却用**调色板角色**（`?attr/colorOnSurface` / `?attr/colorOnSurfaceVariant` / `?attr/colorPrimary` / `@color/dialog_outlined_button_text` / `@color/webhtv_color_success` / `?attr/colorError`）。
3. 夜间表把那些角色映射成浅色（`#E2E2E9` / `#C4C6D0` / `#A8C7FA` / `#8EDB9F` / `#FFB4AB`），于是近白压近白，实测 **1.13–1.66:1**。

既有守卫都盖不住这一类：`LeanbackForegroundContrastTest` 只管「恒定浅色前景落浅面」，`TvFixedDarkSurfaceContrastTest` 只管「前景落固定**深**面」，`TvFocusStatePairContrastTest` 的完整性门只管「恒定浅色状态前景」。

## 修法

新增四个两表恒定的前景资源，取值全部取自**日间色板的角色值**（它们压低所有已用固定浅底都 ≥4.5:1，实测最低值括号内）：

| 资源 | 值 | 用途 | 最低对比度 |
| --- | --- | --- | --- |
| `@color/fixed_light_focus_text` | 默认 `#202124` / 焦点·按下 `#174EA6` | 固定浅底上的文字 | 11.73 |
| `@color/fixed_light_focus_icon` | 同上 | 固定浅底上的图标 `tint` | 11.73 |
| `@color/fixed_light_text_muted` | `#44474F` | 固定浅底上的次要说明文字 | 6.77 |
| `@color/fixed_light_accent` | `#0B57D0` | 固定浅底上的强调文字/徽标 | 4.65 |
| `@color/fixed_light_success` | `#146C2E` | 豆瓣评分徽标文字 | 4.76 |
| `@color/fixed_light_error` | `#B3261E` | 固定浅底上的删除图标 | 5.13 |
| `@color/fixed_light_band` | `#E5E7EB` | 日志页列头 + 筛选按钮下方那条**固定浅带**（原来是半透明 scrim） | 见下节 |

这与仓库既有成文判据一致：`app/src/main/res/values/colors.xml` 里 `tmdb_douban_rating_green` 的注释早已写明「固定底 → 前景必须两表恒定」，只是当时没有把它做成可复用的资源，也没有守卫强制。

## 修复清单（15 个布局文件 + 颜色资源与守卫）

**第一轮（扫描器直出，59 行）**

- `app/src/leanback/res/layout/adapter_ad_block_log.xml`：12 行 `@color/dialog_outlined_button_text` → `@color/fixed_light_focus_text`（宿主 `selector_ad_stats_item`，1.32:1）。
- `app/src/leanback/res/layout/adapter_ad_stats_item.xml`：`:25`/`:47` → `fixed_light_focus_text`；`:35` `?attr/colorOnSurfaceVariant` → `fixed_light_text_muted`（1.31:1）。
- `app/src/leanback/res/layout/dialog_ad_block_stats.xml`：20 行 `@color/dialog_outlined_button_text` → `fixed_light_focus_text`（1.66:1）；11 行 `?attr/colorOnSurfaceVariant` → `fixed_light_text_muted`（1.64:1）；`:32`/`:34` TabLayout 的 `tabSelectedTextColor`/`tabTextColor` → `fixed_light_accent`/`fixed_light_text_muted`（宿主 `shape_ad_stats_tabs` `#F4F7FB`）。**未动**：`:14`（压随表 `shape_shell_proxy_dialog`）、四个 `AppCompatEditText`（`selector_dialog_input` 随表）、`:829` 主按钮。
- `app/src/main/res/layout/adapter_mpv_config_profile.xml`：`:26`/`:68` `?attr/colorPrimary` → `fixed_light_accent`（1.50:1）；`:41` → `fixed_light_focus_text`（1.13:1）；`:52` → `fixed_light_text_muted`（1.49:1）；`:83` tint → `fixed_light_focus_icon`（1.31:1）。
- `app/src/main/res/layout/dialog_ai_recommendation_info.xml`：`:41` → `fixed_light_accent`（1.50:1）；`:194` `@color/webhtv_color_success` → `fixed_light_success`（1.48:1）。**未动** `:178` `webhtv_color_warning`（实测 5.00:1 达标）。
- `app/src/main/res/layout/dialog_custom_csp.xml`：`:98`/`:124` → `fixed_light_focus_text`（宿主 `selector_dialog_step_button` 焦点 `#E8F0FE`，1.49:1）。
- `app/src/main/res/layout/dialog_mpv_config_create.xml` `:36`、`dialog_mpv_config_manager.xml` `:50`、`dialog_mpv_config_rename.xml` `:34`：`tint` → `fixed_light_focus_icon`（宿主 `selector_mpv_icon_button` 按压 `#D2E3FC`，1.31:1）。
- `app/src/mobile/res/layout/adapter_github_proxy.xml`：`:23` → `fixed_light_focus_text`（宿主 `selector_custom_csp_item`，1.13:1）。

**第二轮（守卫升级后自动扫出）**

- `app/src/leanback/res/layout/adapter_ad_rule.xml` `:80`、`app/src/main/res/layout/adapter_custom_csp.xml` `:70`/`:82`/`:94`、`app/src/main/res/layout/dialog_update_settings.xml` `:37`、`app/src/leanback/res/layout/dialog_update.xml` `:40`：→ `fixed_light_focus_icon`（宿主 `selector_dialog_switch` / `selector_dialog_step_button`，1.49:1）。
- `app/src/mobile/res/layout/adapter_github_proxy.xml` `:48`：`?attr/colorError` → `fixed_light_error`（1.48:1）。

**第三轮（真机验证才发现：半透明 scrim 把浅面板压成中灰）**

`dialog_ad_block_stats.xml` 日志页的 88dp 列头 + 筛选按钮带（`:342`–`:764`）与底部 1dp 分隔线（`:805`）原用 `@color/webhtv_color_scrim`（日间 `#52000000` / 夜间 `#8A000000`）。压在固定浅面板 `#FAFBFC` 上合成日间 `#AAAAAB` / 夜间 `#737374`（与 5561 实机实测 `rgb(115,115,116)` 吻合），于是带内：

- 12 个列头（`:361`/`:397`/`:433`/`:469`/`:505`/`:541`/`:577`/`:613`/`:645`/`:677`/`:709`/`:741`）用 `fixed_light_focus_text` 时夜间带上只剩 **3.40:1**，聚焦态 `#174EA6` 更是 **1.66:1**——**比修复前更差**，属我引入的回归，必须一并解决。
- 8 个筛选按钮（`siteFilter`/`siteDomainFilter`/`vodFilter`/`lineFilter`/`episodeFilter`/`ruleFilter`/`adDomainFilter`/`pipelineFilter`）自身不写 textColor，继承 `Widget.WebHTV.Button.Outlined` 的 `?attr/colorPrimary`（夜间 `#A8C7FA`）→ **2.76:1**。

修法：带改成两表恒定的 `@color/fixed_light_band` = `#E5E7EB`（与 `#FAFBFC`/`#E8F0FE` 同族观感），8 个筛选按钮补 `android:textColor="@color/fixed_light_focus_text"` 与 `app:strokeColor="@color/fixed_light_text_muted"`（`#44474F` 压 `#E5E7EB` = 7.51:1，非文字描边 3:1 门槛同样满足）。带内前景现在全部达标：`#202124` 13.00、`#44474F` 7.51、`#174EA6` 6.34、`#0B57D0` 5.16。

**故意不改**：带内的 4 个 `AppCompatEditText`（`blockedAtFilter`/`segmentStartFilter`/`segmentEndFilter`/`segmentDurationFilter`，background `selector_dialog_input`，其 solid 是 `?attr/colorSurfaceContainerHighest`）自带不透明随表填充盖住带，前景随表是正确配对；`:14` 标题与 `:829` 主按钮压在随表的 `shape_shell_proxy_dialog` 上，同样正确。

**第四轮（C103 合并 beta 后的复审补修：mobile 版日志带被漏掉）**

第三轮只处理了 leanback 版 `app/src/leanback/res/layout/dialog_ad_block_stats.xml`。移动版 `app/src/mobile/res/layout/dialog_ad_block_stats.xml` 带着**同一处缺陷**留了下来：

- 88dp 日志列头/筛选带（`:336`–`:742`）底色仍是 `@color/webhtv_color_scrim`（日间 `#52000000` / 夜间 `#8A000000`），压在固定浅面板 `shape_ad_stats_content` `#FAFBFC` 上合成日间 `#9D9DA2`，夜间更深。带内文字却用调色板角色：
  - 12 个列头（改前行 `:357`/`:391`/`:425`/`:459`/`:493`/`:527`/`:561`/`:595`/`:627`/`:659`/`:691`/`:723`）的 `@color/dialog_outlined_button_text` → 夜间解析到 `#A8C7FA`，**2.37:1**。
  - 8 个筛选按钮（`siteFilter`/`siteDomainFilter`/`vodFilter`/`lineFilter`/`episodeFilter`/`ruleFilter`/`adDomainFilter`/`pipelineFilter`）只写 `style="@style/Widget.WebHTV.Button.Outlined"`，继承 `?attr/colorPrimary`（夜间 `#A8C7FA`）+ `?attr/colorOutline`，同样低对比度。
- **守卫当时为什么没抓**：扫描范围含 `src/mobile`，但 `surfaceFills()` 的 `@color/` 分支要求「两表字面量相同」（`fixedLiteral`）才认作固定浅面；`webhtv_color_scrim` 两表不同 → 返回 `null` → 整条带被跳过，带内文字转而和更下层的 `#FAFBFC` 面板比较，看起来「合格」。**这是守卫的盲区，不是布局的正确配对**。

补修（21 处，全部在带内）：

- `:339` 带底 `@color/webhtv_color_scrim` → `@color/fixed_light_band`；12 个列头 `@color/dialog_outlined_button_text` → `@color/fixed_light_focus_text`；8 个筛选按钮各补 `android:textColor="@color/fixed_light_focus_text"` + `app:strokeColor="@color/fixed_light_text_muted"`（与 leanback 版逐属性对齐）。
- **仍然不改**并说明理由：`:14` 标题与 `:29` `TabLayout` 的 `tabTextColor` 压在随表的 `shape_shell_proxy_dialog` 上；带内 4 个 `AppCompatEditText`（`:610`/`:642`/`:674`/`:706`）background `selector_dialog_input` 的 solid 是 `?attr/colorSurfaceContainerHighest`，自带不透明随表填充盖住带；`:807` 起底部 `reset`/关闭按钮同理。`:143`/`:783` 两条 1dp 分隔线是纯装饰、无前景，保持 `scrim`。
- 该 layout 无 Java 引用（`grep -r 'R.layout.dialog_ad_block_stats'` 无命中），mobile 侧只有 `app/src/testMobile/java/com/fongmi/android/tv/ui/dialog/AdBlockStatsDialogLayoutTest.java` 以文件文本方式断言，改动后 13 个用例仍全绿。

## 守卫升级：`TvFixedLightSurfaceContrastTest`

原版只认 `android:textColor` 且只解析 `?attr/`，因此漏掉三类同族实例。升级点：

1. **`FOREGROUND` 正则**覆盖 `android:textColor`、`android:textColorHint`、`android:tint`、`app:tint`、`app:tabTextColor`、`app:tabSelectedTextColor`。
2. **`foregroundColors()` 解析器**沿 `?attr/ → @color/ → 状态选择器 → @color/` 递归下降，解析出每张表里该属性可能画出的**全部**字面量；两表集合相同时返回 `null`（那正是修复后的期望状态，不是缺陷）。
3. **扫描范围**从 `src/leanback` + `src/main` 扩到 `src/mobile`。
4. **`PENDING_LAYOUTS` 清空**并改名为 `EXEMPT_LAYOUTS`；这批文件现在全部由守卫覆盖。
5. **表面侧新增 `surfaceFills()`**：除 `@drawable/` 外，`android:background="@color/X"` 也会被解析——当 X 两表恒定且不透明、亮度 ≥0.6 时视作固定浅面。此前它只认 drawable，日志页那条 `@color/webhtv_color_scrim` 带因此完全不可见（带内文字被拿去和下面的 `#FAFBFC` 面板比，反而显得合格）。
6. **`fixedLightPanelsKeepPaletteIndependentForegrounds`** 从 1 条期望扩到 **18 条**，逐文件钉住「宿主 drawable 或 `@color/` 带 + 应使用的固定前景资源」；C103 复审补入第 18 条 `src/mobile/res/layout/dialog_ad_block_stats.xml` + `@color/fixed_light_band` + `@color/fixed_light_focus_text`，把第四轮漏掉的 mobile 带钉死。
7. **`theGuardDetectsTheDefectItWasWrittenFor`** 增加三个正例：`@color/dialog_outlined_button_text` 状态选择器、`android:tint` 角色，以及 C103 新增的 **band 探针**——`<LinearLayout android:background="@color/fixed_light_band"><TextView android:textColor="@color/dialog_outlined_button_text" /></LinearLayout>` 必须被判为 `< MIN_CONTRAST`。该探针专门锁住 `surfaceFills()` 的 `@color/` 分支，防止有人为了消掉假阳性而把 `@color/` 填充整体排除，从而重新打开第四轮的盲区。

## 验证

- `:app:testLeanbackArm64_v8aDebugUnitTest`（`TvFixedLightSurfaceContrastTest`、`TvFixedDarkSurfaceContrastTest`、`LeanbackForegroundContrastTest`、`PlayerSettingsContrastTest`、`TvFocusStatePairContrastTest`、`TvFocusRingContractTest`、`TvTabFocusRingPaletteContractTest`、`DialogForegroundTokenTest`、`ThemeBinderContractTest`、`ThemeBaseWiringTest`、`TvAppExitSourceTest`）：BUILD SUCCESSFUL。
- `:app:assembleLeanbackArm64_v8aDebug`：BUILD SUCCESSFUL。
- `git diff --check`：干净。
- 设备验证：`127.0.0.1:5561`（`ui_night_mode=2`，1080p）安装 debug APK 后逐一截图核对。**注意包名是 `com.silent.android.webhtv`**（`applicationId`），启动 `com.fongmi.android.tv` 会跑到另一个旧包；`Setting*` 弹窗入口 activity 未导出，需要先 `adb root` 才能 `am start`。截图是 1920×1080，而预览图被缩到 1708×961，用 Pillow 量像素要乘 1.1241。

### 5561 实机像素实测（夜间模式，1920×1080）

| 界面 | 位置 | 实测值 | 对比度 |
| --- | --- | --- | --- |
| 退出确认弹窗（m00004 原始缺陷） | 「取消」按钮底 / 文字 | `#F8FAFD` / `#303133` | **12.45:1**（修复前约 1.6:1） |
| 同上，焦点态 | 底色 / 文字 | `#DCEAFF` / `#2357AB` | **5.71:1** |
| 广告拦截统计 · 日志页列头带 | 带底 | `rgb(229,231,235)` = `#E5E7EB` | 固定不变 |
| 同上，列头文字 | 前景 | `rgb(32,33,36)` = `#202124` | **13.0:1** |
| 同上，8 个筛选按钮 | 文字 / 描边 | `#202124` / `rgb(68,71,79)` = `#44474F` | 13.0:1 / 7.51:1 |
| 广告拦截统计 · 概览页 | `#FAFBFC` 面板上的数字与说明 | `#202124` / `#44474F` | 15.54:1 / 8.97:1 |
| 同上，Tab 选中 / 未选 | `#F4F7FB` 底上 | `#0B57D0` / `#44474F` | 5.94:1 / 8.65:1 |

三张验证截图（`.shots/v_exit.png`、`v_stats2.png`、`v_log1.png`）核对后已删除，不留在仓库里。

### C103 复审轮的验证（第四轮补修）

- `./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "*TvFixedLightSurfaceContrastTest*" :app:testMobileArm64_v8aDebugUnitTest --tests "*TvFixedLightSurfaceContrastTest*" --tests "*AdBlockStatsDialogLayoutTest*"` → **BUILD SUCCESSFUL**（4m 30s）。用例级：leanback 守卫 `tests=4`、mobile 守卫 `tests=4`、`AdBlockStatsDialogLayoutTest tests=13`，全部 0 failures / 0 errors / 0 skipped。
- **负向对照**：把 `app/src/mobile/res/layout/dialog_ad_block_stats.xml` 临时 `git checkout HEAD` 还原后重跑 mobile 守卫 → `fixedLightPanelsKeepPaletteIndependentForegrounds FAILED`（`TvFixedLightSurfaceContrastTest.java:485`，`source.contains(expectation[1])` 断言：还原文件不含 `@color/fixed_light_band`），BUILD FAILED in 42s；复原后再跑即绿。说明新增的第 18 条期望确实钉住修复前状态，不是空断言。
- **全库独立复算**：另写一次性 Node 解析器（三源集 day/night 色表 + `res/color/*` 状态选择器，栈式取最近背景，只认「两表恒定 + alpha ≥ 0x66 + 亮度 ≥ 0.6」的浅底）扫全部 `src/{main,leanback,mobile}/res/layout/*.xml`，剩余 2 条命中均为假阳性——两版 `dialog_ad_block_stats.xml` 里 4 个 `AppCompatEditText` 自身 background 是随表的 `selector_dialog_input`，简版扫描把外层 `fixed_light_band` 误当表面（1.39:1）。与「故意不改 4 个 EditText」的既有结论一致，无新增缺陷。

## 环境备注

- 本机 `ops_bash` 的 MSYS2 bash 在仓库子树内被 ACL 拒绝写入；所有写操作走宿主 `write`/`edit` 工具或 `pwsh` + `G:\Git\bin\bash.exe`。
- 判定「填充是否固定」不能只看 `#RRGGBB`：`@color/` 可能是状态选择器，且 selector 里可能有 `@android:id/mask` 的白色实心块（必须先剥离），否则会误报十余个文件。
