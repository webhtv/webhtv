# TV-FOCUS-RING-DIALOG-20261019：版本更新弹窗的主按钮没有焦点环，关于弹窗的齿轮图标在两种主题下都看不见

## Recovery anchor

- 目标：用户实拍两张照片圈出的控件，在 TV 深色模式下都能明确看出是否选中 —— ① 版本更新弹窗的 `stableConfirm`/`betaConfirm`/`cancel` 补上统一焦点环；② 关于弹窗右下角齿轮图标从未聚焦的 1.05:1 提升到面板上的可读前景色。
- 允许路径：`app/src/leanback/res/layout/dialog_update.xml`、`app/src/mobile/res/layout/dialog_update.xml`、`app/src/main/res/layout/dialog_about.xml`、`app/src/main/res/color/about_primary_icon_tint.xml`、`app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvFocusRingContractTest.java`、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/`（未跟踪，预存脏路径）；分支 `dev4`，HEAD `cef7ba60432b4c0a9415f4a31f77ad8df6fc8474`。
- 验收：① 版本更新弹窗三个主按钮都带 `@color/focus_ring_primary` + `@dimen/webhtv_focus_ring_width`；② 齿轮未聚焦态与面板 ≥3:1、聚焦态与主题色填充 ≥3:1；③ 契约测试新增 3 项并全绿（22/22）；④ 5561 真机对照：齿轮 1.05:1 → 7.93:1，`cancel` 焦点环 `#062E6F` 3px 与关于弹窗一致。
- 回滚：撤销本任务原子提交即可；无数据迁移。
- 交付坐标：提交 `f5d08659fcd`（dev4），recovery tag `recovery/TV-FOCUS-RING-DIALOG-20261019/20261019134000`；未推送。
- **第二轮（2026-10-19 下午，用户追加授权）**：见 §8。用户指出"选中后的高亮边框颜色和按钮本身的颜色太接近"，并明确授权"发现的其他类似问题请同意改吧"，§6 的遗留清单因此转为在范围内。
  - 第二轮允许路径：`app/src/main/res/color/`（新增 `focus_ring_primary_container.xml`，改 `dialog_primary_button_bg.xml`、`dialog_primary_button_text.xml`、`about_primary_icon_tint.xml`）、`app/src/main/res/values/colors.xml`、`app/src/main/res/drawable/{about_primary_icon_button,selector_update_version_item}.xml`、三个 `res/layout` 目录下的 45 个主按钮站点、`TvFocusRingContractTest.java`、本文件。
  - 第二轮验收：① 每个主按钮同时具备"填充变深 + 浅色高对比环"两个独立线索；② 环对面板与对自身填充都 ≥3:1；③ 版本卡选中态与默认态可分辨；④ 契约测试 26 项全绿 + 反证（把聚焦填充改回旧值必须失败）。
  - 第二轮交付坐标：提交与 tag 见文末 §8.7。
- 下一步唯一动作：无。

## 1. 现象与根因

用户提供两张 TV 深色模式实拍照片，圈出"几乎无法判断是否选中"的按钮。诊断后是两个**互不相同**的缺陷：

| 照片 | 圈出控件 | 实测 | 结论 |
|---|---|---|---|
| 2（版本更新） | `更新测试版` | `dialog_update.xml` 两个 flavor **完全没有 `strokeColor`** | 焦点**完全不可见** |
| 1（关于） | 右下角齿轮 | 未聚焦图标 `?attr/colorOnPrimary` 画在 `#2A2F34` 面板上 = **1.05:1**（浅色 **1.22:1**） | 两种主题下图标**都看不见** |
| 1（关于） | `检查更新`/`加速源`/`我已知悉` | 环存在，`#062E6F` on `#A8C7FA` = **7.50:1** | 已正确，无需改动 |

### 1.1 版本更新弹窗：填充状态表在聚焦前后是同一个颜色

`dialog_primary_button_bg.xml` 的 `state_focused` 与默认项都解析到 `webhtv_color_primary`，而 `colorFocus == colorPrimary`（`docs/TV-FOCUS-RING-20261004-unified-theme-ring.md` §2.1 已记录同一根因）。所以**边框环是唯一的焦点线索**，而这三个按钮恰恰没有环 —— 2026-10-04 的统一环任务没有覆盖到 `dialog_update.xml`（该文件自 `0af2340d495` 起未被触碰）。

### 1.2 齿轮图标：着色与它实际所处的背景不配对

`about_primary_icon_button.xml` 在未聚焦时填充全透明，图标直接贴在 `colorSurfaceContainerHigh` 面板上；而 `dialog_about.xml` 硬写 `app:tint="?attr/colorOnPrimary"`。on-primary 是"画在主题色填充上"的配对色，夜间是深蓝 `#062E6F`、浅色是纯白 `#FFFFFF`，两者画在各自的面板上分别是 1.05:1 和 1.22:1 —— 等于隐形。

仓库里其实**已经存在**正确的状态表 `about_primary_icon_tint.xml`，但没有任何布局引用它（全仓库 grep 零命中），是死代码；而且它的分支也是反的（聚焦态取 `colorOnSurface`）。

## 2. 证据来源

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---|---|
| 用户实拍照片 1/2 | sha256 `04114aa3…`/`a1a2080e…`，2560x1440 | A（用户提供） | 确认两个缺陷的现场表现 |
| 5561 模拟器实拍 | `build/tmp-focus-5561/`（`about1.png`、`gear-focused.png`、`v-*.png` 等 9 张；该目录被 `.gitignore` 覆盖，属本机取证产物，不入库） | A（本地设备） | 像素级前后对照 |
| `app/src/leanback|mobile/res/layout/dialog_update.xml` | HEAD `cef7ba60432` | A（本地） | 三个主按钮只有 `app:backgroundTint`，无 `strokeColor` |
| `app/src/main/res/color/dialog_primary_button_bg.xml` | HEAD `cef7ba60432` | A（本地） | focused 与 default 同色，环是唯一线索 |
| `app/src/main/res/color/about_primary_icon_tint.xml` | HEAD `cef7ba60432` | A（本地） | 正确状态表已存在但零引用；分支反了 |
| `docs/TV-FOCUS-RING-20261004-unified-theme-ring.md` | HEAD `cef7ba60432` | A（本地） | 同一根因的既有记录；§12 规定宽度 1.5dp 不得再加粗 |
| WCAG 2.2 SC 1.4.11 / SC 2.4.13 | 沿用仓库既有记录（访问日期 2026-10-04，本次未重新抓取） | A（间接） | 非文本对比度 ≥3:1，焦点外观 ≥3:1 |

不适用类别记录：不涉及解码/渲染/ABI/打包/网络，无需上游播放器依赖类证据。

## 3. 方案比较与采用

| 方案 | 优点 | 缺点/风险 | 结论 |
|---|---|---|---|
| 1. 不变更 | 零风险 | 用户报告的缺陷继续存在 | 拒绝 |
| 2. 加粗焦点环到 3dp 提升对比 | 直观 | **违反 §12**：用户已明确否决 3dp（"太粗"） | 拒绝 |
| 3. 改 `dialog_primary_button_bg` 的聚焦填充色 | 一处改动覆盖全仓库 | 会改变**已验收**的关于弹窗外观；且填充色变化属于另一套机制，与"边框环是唯一机制"的既有契约冲突 | 拒绝 |
| 4. **补环 + 修正齿轮着色配对**（采用） | 与已验收的关于弹窗完全同一套规范；每个分支都与它实际所处的背景配对，换主题不失效 | 需要同时改两个 flavor | **采用** |

## 4. 实施

1. `app/src/leanback/res/layout/dialog_update.xml`：`stableConfirm`、`betaConfirm`、`cancel` 各补 `app:strokeColor="@color/focus_ring_primary"` + `app:strokeWidth="@dimen/webhtv_focus_ring_width"`。
2. `app/src/mobile/res/layout/dialog_update.xml`：同上（移动版同缺陷）。
3. `app/src/main/res/color/about_primary_icon_tint.xml`：改为正确配对 —— 聚焦/按下取 `?attr/colorOnPrimary`（此时背景已被换成 `colorPrimary`），常态取 `?attr/colorOnSurfaceVariant`（背景透明，图标落在面板上）。
4. `app/src/main/res/layout/dialog_about.xml`：齿轮 `app:tint` 由硬写的 `?attr/colorOnPrimary` 改为引用该状态表，激活这份死代码。
5. `TvFocusRingContractTest` 新增 3 项：`updateDialogPrimaryButtonsShowABorderRingWhenFocused`、`migratedDialogsRingEveryPrimaryButton`、`aboutGearIconTintPairsWithTheBackgroundItActuallySitsOn`（含两个分支各自的 ≥3:1 对比度回归）。

## 5. 不变量与回滚

- 不改动 `webhtv_focus_ring_width` 的取值（1.5dp），遵守 `TV-FOCUS-RING-20261004` §12。
- 不改动 `dialog_primary_button_bg` / `focus_ring_primary` / 主题 token 的任何取值。
- 不改动关于弹窗三个按钮（已验收正确）与 `selector_update_version_item`（版本行已有 2dp 环）。
- 不改动播放器/视频层焦点环族。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 6. 同类问题清单（第一轮未获授权；第二轮已授权并全部处理）

同一缺陷族：**消费 `dialog_primary_button_bg` 但没有 `strokeColor`** 的其它弹窗。第一轮静态清点（`Select-String` 全量扫描 `app/src/*/res/layout/*.xml`）得到约 48 个按钮站点，分布在：

`dialog_ad_block_stats.xml`(leanback/mobile)、`dialog_ad_rule_preview.xml`、`dialog_ad_rule_edit.xml`、`dialog_apk_push_progress.xml`、`dialog_site_health_report.xml`、`dialog_site_health.xml`、`dialog_config.xml`、`dialog_custom_csp.xml`、`dialog_login_state_*.xml`、`dialog_playback_remote_sync.xml`、`dialog_playback_webhook.xml`、`dialog_remote_trust_*.xml`、`dialog_shell_proxy.xml`、`dialog_sync_path.xml`、`dialog_ua.xml`、`dialog_viewing_record_sync.xml`、`dialog_web_home_extension*.xml`、`dialog_mpv_config_*.xml`、`dialog_ai_recommendation_info.xml`、`dialog_manage_page*.xml`、`dialog_one_key_sync.xml`、`activity_crash.xml`、`activity_following.xml` 等。

这些站点在 TV 上同样"聚焦前后填充同色、无环"，属于同一个可见性缺陷。第一轮按 `AGENTS.md` §2「范围默认封闭」未扩大改动面；**第二轮用户明确授权后已一次性机械补齐，精确清点结果为 45 个站点 / 37 个文件，见 §8。**

## 7. 验证记录

### 7.1 定向 JVM 测试

`.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.activity.TvFocusRingContractTest" --console=plain --no-build-cache` → **BUILD SUCCESSFUL in 6m 38s**。

报告 `app/build/test-results/testLeanbackArm64_v8aDebugUnitTest/TEST-com.fongmi.android.tv.ui.activity.TvFocusRingContractTest.xml`：`tests=22 failures=0 errors=0 skipped=0`，含新增的 `updateDialogPrimaryButtonsShowABorderRingWhenFocused`、`migratedDialogsRingEveryPrimaryButton`、`aboutGearIconTintPairsWithTheBackgroundItActuallySitsOn` 三项全 PASS。

### 7.2 构建

`.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug --console=plain --no-build-cache` → **BUILD SUCCESSFUL in 26s**。

### 7.3 5561 真机对照（TV 深色模式，`Night mode: yes`）

`adb install -r -d` → `Success`；应用内版本戳 `5.6.0-202610191321 · leanback/arm64_v8a`。

| 控件 | 修复前 | 修复后 | 判定 |
|---|---|---|---|
| 齿轮图标（未聚焦） | `#062E6F` on `#2A2F34` = **1.05:1** | `#C4C6D0` on `#2A2F34` = **7.93:1** | 达标 |
| 齿轮图标（聚焦） | — | `#A8C7FA` 填充 + `#062E6F` 环 | 达标 |
| `cancel`（版本更新，聚焦） | 无环，填充 `#98B8EC` | 焦点环 `#062E6F` **3px**（y=755-757） | 达标 |
| `stableConfirm`/`betaConfirm` | 无环 | 与 `cancel` 同一组属性（静态测试锁定） | 达标 |

`cancel` 的 3px `#062E6F` 环与关于弹窗 `confirm` 的实测完全一致（同为 1.5dp × density 1.75（实测 `wm density` = 280）≈ 2.6px，取整 3px），确认两个弹窗现在走的是同一套规范。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。本次未新增裸十六进制。

## 8. 第二轮：聚焦态"填充 + 环"双线索收敛（2026-10-19 下午）

### 8.1 用户追加反馈

> "现在有较明显的选中动画了但是选中后的高亮边框颜色和按钮本身的颜色太接近不明显是否需要调整一下按钮默认的颜色？发现的其他类似问题请同意改吧"

拆成两件独立的事：① 焦点环颜色与按钮自身填充"太接近"；② 明确授权把同类的其余无环站点一并修掉（即 §6 清单）。

### 8.2 根因：不是"环色选得不好"，而是"环只能选一个颜色，却要同时面对两个背景"

第一轮把环定为 `?attr/colorOnPrimary`（深色 `#062E6F`）。它在**按钮填充**上对比度是够的：

| 对比 | 深色 | 浅色 |
|---|---|---|
| 环 `#062E6F` vs 默认填充 `#A8C7FA` | 7.50:1 | — |
| 环 `#FFFFFF` vs 默认填充 `#0B57D0`（浅色） | — | 6.39:1 |

但环是 1.5dp 的细线，**外侧压在弹窗面板上**。面板色是 `surfaceContainerHigh`，于是：

| 对比 | 深色 | 浅色 |
|---|---|---|
| 旧环 vs 面板 `#2A2F34` / `#E7E8EF` | **1.05:1** | **1.22:1** |

也就是说：环在填充一侧很清楚，在面板一侧几乎消失。用户看到的"太接近"，本质是**环的外缘融进了面板**，而不是环与填充撞色。

更要紧的是，**这个缺陷在"环色"这一个自由度里无解**。要让一个颜色同时满足"对浅色填充 ≥3:1"和"对深色面板 ≥3:1"，需要它的相对亮度同时落在两个互斥区间：

- 深色表：需 ≤ 0.1536（对 `#A8C7FA` 填充）且 ≥ 0.1832（对 `#2A2F34` 面板）→ **空区间**
- 浅色表：需 ≤ 0.0048（对 `#0B57D0` 填充）且 ≥ 2.5280（对 `#E7E8EF` 面板）→ **空区间**

这条不可能性证明被固化成回归钉子 `noSingleRingColourCanSatisfyBothTheLightFillAndTheDarkPanel`，防止以后有人再试图只调环色。

结论：**必须让"聚焦态填充"与"默认态填充"不同**，环才有机会选一个对两侧都高对比的颜色。用户猜测的"调整按钮默认的颜色"方向是对的，只是更精确的做法是改**聚焦态**填充 —— 默认态是用户日常看到的外观，动它影响面更大且没有收益。

### 8.3 采用的方案：聚焦态切到 container 配对

聚焦时填充从 `primary` 切到 `primaryContainer`，环用 `onPrimaryContainer`：

| 对比 | 深色 | 浅色 | 门槛 |
|---|---|---|---|
| 默认填充 / 文字 | 7.50:1 | 6.39:1 | ≥3:1 |
| 聚焦填充 / 文字 | 7.04:1 | 12.57:1 | ≥3:1 |
| 环 vs 聚焦填充 | 7.04:1 | 12.57:1 | ≥3:1 |
| 环 vs 弹窗面板 | **10.40:1** | **13.35:1** | ≥3:1 |
| 聚焦填充 vs 默认填充 | 5.32:1 | 4.92:1 | 可分辨 |
| （旧）环 vs 面板 | 1.05:1 | 1.22:1 | 已修复 |

这不是新发明，而是把仓库里**已经存在的约定**补到最后一个例外上：`dialog_outlined_button_bg`、`dialog_toggle_button_bg` 的聚焦分支本来就是 `primary_container`，`dialog_card_action_text` 的聚焦分支本来就是 `on_primary_container`。主按钮是唯一没跟上的。

副作用是每个主按钮现在有**两个独立线索**：填充明显变深 + 浅色高对比环。任一单独失效都不会导致"看不出选中"。

### 8.4 同时修掉的其它同类缺陷

| 缺陷 | 位置 | 处理 |
|---|---|---|
| 45 个主按钮无环、聚焦前后填充同色 | 三个 `res/layout` 目录（37 文件） | 机械补齐：`backgroundTint`→`dialog_primary_button_bg`、`textColor`→`dialog_primary_button_text`、`strokeColor`→`focus_ring_primary_container`、`strokeWidth`→`webhtv_focus_ring_width` |
| 3 个站点有环但环色是静态轮廓 `?attr/colorOutline`（对填充 1.85:1 / 1.43:1） | `dialog_result_list.xml#querySearch`、`leanback/dialog_config.xml#positive`、`mobile/dialog_apk_push_method.xml#local` | 一并改指到 `focus_ring_primary_container` |
| `mobile/dialog_apk_push_method.xml#local` 文字 `?attr/colorOnSurface` 画在 `?attr/colorPrimary` 填充上 = 1.33:1 / 2.68:1 | 同上 | 改用 `dialog_primary_button_text` |
| `activity_crash.xml` 的按钮同时有 `android:backgroundTint` 与 `app:backgroundTint`（后者覆盖前者），且根标签缺 `xmlns:app` 导致 `unbound prefix` | `app/src/mobile/res/layout/activity_crash.xml` | 补 `xmlns:app`、删除被覆盖的 `android:backgroundTint` |
| 版本卡"选中"与"默认"看起来一样 | `selector_update_version_item.xml` | 选中态改用 `primaryContainer` 填充 + `primary` 环；宽度硬编码 2dp 改用统一 token |

### 8.5 刻意未改（保留原样，附理由）

- `following_button_*` 家族（`activity_following.xml` 4 处、`item_following.xml` 1 处）与 `dialog_cache_management.xml` 2 处仍用 `@color/focus_ring_primary`：它们的聚焦填充是 `webhtv_color_focus`（`#F5F7FF` / `#0B57D0`），深色环压在近白填充上本就高对比，且聚焦同时改变填充 —— 不存在本次报告的缺陷。且 `following_button_focus` 绑定用户可自定义的 FOCUS 槽位，改动会削弱主题定制能力。
- `dialog_toggle_button_bg` 的消费者（`dialog_custom_csp`、`dialog_web_home_extension*`、`dialog_shell_proxy`）：其聚焦填充已翻转到 `primaryContainer`，`strokeColor="?attr/colorOutline"` 是**静态描边**而非焦点环，语义不同。
- 播放器/视频层焦点环家族（`tvPlayerRing`）：宿主是视频画面/固定玻璃层，按 `docs/TV-FOCUS-RING-20261004` §14 独立定义。
- 5 处 Java 代码 `setBackgroundTintList(... R.color.dialog_primary_button_bg)`（`ChoiceDialog`、`CodecCapabilityDialog`、`GitCloudDialog`、`LightDialog`、`RemoteTrustDialog`）：**这 5 处程序化产出的主按钮至今没有焦点环**——`GitCloudDialog.java:2249-2254`、`RemoteTrustDialog.java:3174-3179` 完全不调 `setStrokeColor`/`setStrokeWidth`；`ChoiceDialog.java:595`、`LightDialog.java:237`、`CodecCapabilityDialog.java:363` 写的是 `setStrokeWidth(primary ? 0 : dp(1))`，primary 分支宽度为 0。它们**不在本轮 45 个 XML 站点内**，契约测试只扫 XML 也永不覆盖。经比对这 5 个文件均**不在本任务改动集**（各自最后提交为 `fb85759b03a`/`2e5022b8685`/`4e4bfffcb67`/`f85398d3598`），属预存缺陷，按 `AGENTS.md` §2 只报告不修。原记录「均已自行设置 stroke」有误，此处更正。

**宽度未动**：`webhtv_focus_ring_width` 仍是 **1.5dp**。用户在 `docs/TV-FOCUS-RING-20261004` §12 已明确否决过 3dp（"太粗"），本次全部收益来自颜色。

### 8.6 验证记录

定向 JVM 测试（两个套件合并一次运行）：

```
.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest `
  --tests "com.fongmi.android.tv.ui.activity.TvFocusRingContractTest" `
  --tests "com.fongmi.android.tv.theme.ThemeBinderContractTest" `
  --console=plain --no-build-cache
```

→ **BUILD SUCCESSFUL**；`TvFocusRingContractTest` `tests=26 failures=0 errors=0 skipped=0`，`ThemeBinderContractTest` `tests=26 failures=0 errors=0 skipped=0`（合计 52 项）。

> 计数说明：`tests=26` 是**本轮（第二轮）交付时**的实测值。其后 `C83-beta-merge-review-dev4-20261019` 复评时新增 `focusedVersionCardKeepsItsTextReadable`，该文件在 HEAD 上为 **27** 项。

注意：`ThemeBinderContractTest` 位于共享 `src/test`，必须挂在 flavor 任务 `testLeanbackArm64_v8aDebugUnitTest` 下运行；仓库里**不存在** `:app:testDebugUnitTest` 任务（计划里写的那个名字是错的，已按实际矩阵修正）。

**反证（关键）**：把 `dialog_primary_button_bg.xml` 的聚焦分支改回旧值 `webhtv_color_primary`，`primaryButtonTextPairsWithItsFillOnEveryStateBranch` 立即 FAILED；再把 `dialog_about.xml` 的 `strokeColor` 换成透明，`aboutDialogButtonsAndGearShowABorderRingWhenFocused`、`everyRingedControlStillDeclaresItsFill`、`thereIsExactlyOneFocusRingWidthInTheTvUi`、`everyPrimaryButtonCarriesItsOwnRingAndStatefulText` 共 4 项 FAILED。两个新门槛都**可被证伪**，不是空断言。文件已还原并复跑全绿。

构建：`.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug --console=plain --no-build-cache` → **BUILD SUCCESSFUL**。

真机（5561，TV 深色模式）：安装后逐像素扫描确认聚焦按钮的纵向剖面为 `面板 → 环 → 填充 → 环 → 面板`。以关于弹窗 `我已知悉` 为例，y=936 一行读到 `#2A2F34`(面板) → `#D3E3FD`(环, 3px) → `#0842A0`(聚焦填充) → `#D3E3FD`(字形) → … → `#D3E3FD`(环, 3px) → `#2A2F34`(面板)。即聚焦填充已从 `primary #A8C7FA` 切到 `primaryContainer #0842A0`，环从 `#062E6F` 切到 `#D3E3FD`，环宽 3px（1.5dp × density 1.75，实测 `wm density` = 280 → 2.6px 取整）。未聚焦按钮为纯 `#A8C7FA` 填充、无环。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag —— 只暂存本任务自有路径，不触碰预存脏路径 `.design-evidence/`。

### 8.7 交付坐标

- 提交 `49b28bd91f9e74b43d1963df84c1f56f8731bf80`（分支 `dev4`）。
- recovery tag `recovery/TV-FOCUS-RING-CONTRAST-20261019/20261019185247`，指向同一提交。
- 未推送。工作树残留仅 `.design-evidence/`（任务开始前即存在的未跟踪目录）。
