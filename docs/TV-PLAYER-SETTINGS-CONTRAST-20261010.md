# TV-PLAYER-SETTINGS-CONTRAST-20261010：屏显设置与播放器按钮对比度

## Recovery anchor

- 目标：修复 TV 版「屏显设置」与「播放器按钮」列表中行文字、隐藏态文字、眼睛图标及焦点态对比度不足的问题，并覆盖 leanback / mobile 共用链路。
- 允许路径：TV/共用播放器设置布局、TV/共用对话框与主题渲染器、相关 TV 对比度资源、同步/代理/退出确认/广告规则等同类可见控件资源、定向回归测试、本任务文档。
- 保护面：task guard 启动时工作区干净，0 个预先存在脏路径；分支 `dev4`，基线 `819003db8fef2ea63420e9a72eb4fcac8ee468ba`。
- 最小决定性验证：播放器设置对比度回归 + leanback/mobile 相关主题回归 + 251 个 TV/共用布局的静态对比度审计 + `192.168.50.3:5561` 覆盖安装后的截图复核。
- 当前状态：已实施、已测试、已在 5561 用最终 leanback Debug 包覆盖安装并完成屏显设置/播放器按钮截图复核；待清理构建资源、更新最终验证记录、提交并创建 recovery tag。
- 下一步唯一动作：停止 Gradle 守护进程、清理本轮临时文件和构建输出，执行 task guard finish。

## 1. 现象与根因

用户现场照片显示：

1. 「屏显设置」列表的“时间 / 网速 / 分辨率 / 进度 / 迷你 / 标题”等文字压在浅色卡片上，几乎不可见；右侧眼睛/隐藏图标也不清晰。
2. 「播放器按钮」列表的项目名称、眼睛图标同样不可读；上下箭头在焦点和禁用态下缺乏清晰层次。

对应调用链：

- TV 设置页 → `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/PlayerOsdDialog.java` → `app/src/leanback/res/layout/adapter_player_osd.xml`。
- 手机设置页 / 共用按钮配置页 → `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/PlayerOsdDialog.java`、`app/src/main/java/com/fongmi/android/tv/ui/dialog/PlayerButtonConfigDialog.java` → `adapter_player_osd.xml` / `adapter_player_button_config.xml`。

直接根因是两个播放器列表布局复用了 `@drawable/selector_git_cloud_card`，该 selector 内部固定使用浅色 `#F8F9FA/#E8F0FE` 填充与 `#0B57D0` 描边；但项目文字从 TV 深色语义表取 `colorOnSurface` / `colorOnSurfaceVariant`，形成固定底色与主题前景色跨表混用。此前隐藏项目还调用 `setAlpha(0.55f)`，进一步把已经不匹配的前景压低。眼睛按钮复用 `selector_dialog_switch` 的固定 Google 浅色焦点填充，图标则取对话框按钮色，焦点/常态也不是同源配对。

旧实现的定量回归值由测试固定：TV 深色 `colorOnSurface` 压 `#F8F9FA` 小于 2:1；旧固定焦点卡片与暗色语义图标也小于 2:1。

## 2. 设计决策

### 不变更

继续复用 `selector_git_cloud_card` 或只把文字硬编码为白色：拒绝。前者保留用户看到的缺陷，后者会重新引入固定色并破坏主题语义；直接修改共享 selector 还会影响 GitCloud/规则列表等非本任务界面。

### 采用的窄化修复

新增 `PlayerSettingsRowTheme`，只服务播放器设置行：

- 常态行：`surfaceContainerHigh` + `outlineVariant`，文字用 `onSurface`（可见）或 `onSurfaceVariant`（隐藏）。
- 焦点/按下行：`primaryContainer` + `primary` 2dp 边框，文字分别用 `onSurface` / `onSurfaceVariant`，两者在两套内置调色板下均达到 4.5:1 以上。
- 眼睛按钮：常态 `surfaceContainerHighest` + `outlineVariant`，焦点/按下 `primaryContainer` + `primary` 边框；图标常态 `onSurfaceVariant`，焦点/按下 `onPrimaryContainer`。
- 移除播放器设置名称的 0.55 透明度，改用语义的 `onSurfaceVariant` 表达隐藏状态，避免 alpha 叠加造成不可读。
- 上下箭头仍使用已有 `dialog_tonal_button_*` 状态色；该控件的禁用/焦点色由同一套 Material 语义 token 提供，不改其它对话框。

### 适用范围

这不是主题系统全局改造：没有修改 `ThemeBinder`、共享 `selector_git_cloud_card`、`selector_dialog_switch`，也没有改变播放器运行时行为、按钮顺序、偏好存储或焦点导航。

## 3. 已实施文件

- `app/src/main/java/com/fongmi/android/tv/theme/PlayerSettingsRowTheme.java`
- `app/src/main/res/drawable/selector_player_settings_row.xml`
- `app/src/main/res/drawable/selector_player_settings_switch.xml`
- `app/src/main/res/color/player_settings_icon_tint.xml`
- `app/src/leanback/res/layout/adapter_player_osd.xml`
- `app/src/mobile/res/layout/adapter_player_osd.xml`
- `app/src/main/res/layout/adapter_player_button_config.xml`
- `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/PlayerOsdDialog.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/PlayerOsdDialog.java`
- `app/src/main/java/com/fongmi/android/tv/ui/dialog/PlayerButtonConfigDialog.java`
- `app/src/test/java/com/fongmi/android/tv/ui/dialog/PlayerSettingsContrastTest.java`

## 4. 验证记录

### 4.1 定向回归测试

已执行：

```text
JAVA_HOME=/c/Program Files/Microsoft/jdk-21.0.12.8-hotspot
./gradlew :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests com.fongmi.android.tv.ui.dialog.PlayerSettingsContrastTest \
  --no-daemon --console=plain
```

结果：`BUILD SUCCESSFUL`；最终 JUnit XML 确认 `tests=5, failures=0, errors=0, skipped=0`，不是空跑。

覆盖内容：

- 两个播放器行布局不再引用固定 `selector_git_cloud_card`。
- leanback / mobile `PlayerOsdDialog` 与共用 `PlayerButtonConfigDialog` 都走 `PlayerSettingsRowTheme`。
- light / dark 两套 `ThemeTokens` 的常态、焦点、隐藏态正文对比度门。
- 眼睛按钮的常态与焦点图标对比度门。
- 旧固定浅色实现小于 2:1 的缺陷回归证据。

### 4.2 对比度与静态审计

最终 leanback Debug 包构建：`BUILD SUCCESSFUL`，APK `191M`，未打正式包。

`PlayerSettingsContrastTest` 最终 JUnit XML：**5 项，0 failure，0 error，0 skipped**。它覆盖播放器行布局、两套内置调色板下常态/焦点/隐藏文字、眼睛按钮和本轮扩展资源契约。

扩展静态审计遍历 TV/共用资源的 **251 个布局**，解析出 **4082 个有效前景/背景比较**；修复后仍有 26 个候选，其中 TMDB 图片表面、站源动态主题、实验室动态按钮和播放器专用绘制占主要部分，已逐项核对为运行时代码负责或属于视频/图片专用表面，未把静态误报改成硬编码。

`scripts/check_ui_tokens.sh --strict`：主题对比度 `failures=0`；唯一违规仍是任务开始前已存在的 `app/src/mobile/res/layout/item_following.xml`，本任务没有修改该文件。该既存门禁结果已保留，不作为本任务新增失败。

### 4.3 设备验证（192.168.50.3:5561）

最终包使用 `adb install -r` 覆盖安装，签名 SHA-256 与本机 debug 证书 `95e4b2e7…7e4d` 一致，没有卸载或清除应用数据。包版本仍为 5.6.0，`lastUpdateTime=2026-10-10 23:41:52`。

最终屏显设置截图证据：`屏显设置` 弹窗中，焦点行的文字核心 `#E2E2E9` 在 `#2A2F34` 上为 **10.48:1**；正常项文字 `#E2E2E9` / `#C4C6D0` 在同一底色上分别为 **10.48:1 / 7.93:1**；焦点环核心 `#A8C7FA` 对行底为 **7.86:1**。隐藏项的划线眼睛图标与文字仍清楚，焦点移动到隐藏项时眼睛按钮保持可见。

最终播放器按钮截图证据：首行眼睛按钮获焦时，焦点底色核心 `#0842A0`、图标核心 `#D3E3FD`，对比度 **7.04:1**；其它行文字与眼睛图标保持可读；“多线程”默认隐藏状态仍保持，未改变用户偏好。修复前固定浅色行与 TV 深色文字为 `#E2E2E9` on `#F8F9FA`，仅 **1.22:1**。

### 4.4 编译与测试

- leanback `PlayerSettingsContrastTest`、`AppearanceRowThemeTest`、`ThemeControllerContractTest`、`LeanbackForegroundContrastTest`、`TvFocusRingContractTest`：`BUILD SUCCESSFUL`。
- mobile `DialogGlassChipStyleTest`、`AdRuleManageDialogLayoutTest`：`BUILD SUCCESSFUL`。
- 最终 leanback Debug APK：`assembleLeanbackArm64_v8aDebug` `BUILD SUCCESSFUL`。
- `git diff --check` 与 task guard `check`：通过。


## 5. 本轮修复范围

除播放器设置两条截图路径外，本轮按同源颜色/实际背景配对原则修复了已确认的同类问题：

- TV 历史记录集数文字改为壁纸前景色，避免压在 `shape_vod_name` 上变成低对比度。
- 退出确认、同步设备/路径、同步方向、代理规则、Custom CSP、GitHub Proxy、广告规则/统计列表改用语义 surface、outline 和主题焦点环。
- 登录状态路径编辑器及树形列表的正文、状态、预览输入区改用当前 `ThemeController` 语义颜色。
- 追更按钮焦点文字改用 `colorOnPrimary`，与焦点态填充配对。
- 播放器 OSD/播放器按钮行新增专用主题渲染器，稳定行底、主题化焦点环、隐藏态前景和眼睛图标；为防止 `ThemeBinder` 二次改色，动态设置的文字/图标显式使用 `webhtv:ignore`。

没有修改播放器功能、按钮顺序、偏好格式、数据库、ABI 或视频层专用颜色。

## 6. 回滚与资源

回滚为撤销本任务原子提交；无数据库、网络协议、ABI、播放器状态或偏好格式变化。最终设备偏好保持原值：`theme_mode=1`、`theme_color=-1`、`wall=22`、`display_time/traffic/size/progress=true`、`display_mini/title=false`、`player_button_hidden=reset,multi_thread_proxy`。

构建和设备验证结束后已执行 `./gradlew --stop` 与 `./gradlew clean --no-daemon`，并删除本轮临时截图、XML、日志和静态审计文件；未卸载设备现有包，最终包始终使用 `adb install -r` 覆盖安装。

## 7. 交付状态

- 当前：实现完成，回归测试、最终构建、资源清理和 5561 覆盖安装完成。
- 提交：由 task guard finish 创建。
- recovery tag：由 task guard finish 创建。
