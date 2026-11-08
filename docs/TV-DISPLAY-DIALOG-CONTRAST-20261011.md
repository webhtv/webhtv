# TV-DISPLAY-DIALOG-CONTRAST-20261011：屏显入口对比度复核

## Recovery anchor

- 目标：核对用户截图对应的 TV「屏显设置」入口，确认它实际使用的弹窗链路、修复漏掉的资源接线，并在 5561 覆盖安装验证。
- 允许路径：`app/src/leanback/res/layout/adapter_player_osd.xml`、`app/src/mobile/res/layout/adapter_player_osd.xml`、对应 `PlayerOsdDialog.java`、屏显回归测试、本任务文档。
- 基线：`bd78d7b528fb61c4dc89f8eb0655085c878b4a87`（上一轮 TV 全面修复提交）。
- 根因：截图并非 `dialog_display.xml` 的旧 `DisplayControl` 芯片，而是 `PlayerOsdDialog` 的 `adapter_player_osd.xml` 列表；该链路已由上一轮新增的 `PlayerSettingsRowTheme` 统一处理。截图中的白卡片/白字是旧包或旧入口状态，不能再修改无关的 `display_option_*` 资源。
- 当前状态：已用最终 leanback Debug 包在 `192.168.50.3:5561` 覆盖安装；屏显弹窗最终源码契约测试通过，旧 `display_option_*` 误改已全部撤销。待停止 Gradle、清理本轮临时资源、task guard finish。
- 下一步唯一动作：完成清理并提交本次“入口核对 + 回归锁”文档/测试变更。

## 1. 实际链路核对

截图中的标题为「屏显设置」，行项目为「时间 / 网速 / 分辨率 / 进度 / 迷你 / 标题」，右侧为显示/隐藏眼睛按钮。该界面对应：

```text
SettingPlayerActivity / SettingPlayerFragment
  -> PlayerOsdDialog
  -> adapter_player_osd.xml
```

不是：

```text
DisplayDialog -> dialog_display.xml -> DisplayControl
```

上一轮提交已经将 TV/mobile 两份 `adapter_player_osd.xml` 从 `selector_git_cloud_card` 改为 `selector_player_settings_row`，并将眼睛按钮接入 `selector_player_settings_switch` 与 `player_settings_icon_tint.xml`。

## 2. 验证

### 2.1 源码回归

`PlayerDisplaySettingSyncTest` 最终 **7/7** 通过，新增契约确认：

- leanback/mobile 两份屏显布局都使用 `selector_player_settings_row`；
- 眼睛按钮使用 `selector_player_settings_switch`；
- 图标使用 `player_settings_icon_tint`；
- 两个 `PlayerOsdDialog` 都调用 `PlayerSettingsRowTheme.apply` / `applySwitch`。

### 2.2 设备验证

设备：`192.168.50.3:5561`。

- 最终 leanback Debug APK 使用 `adb install -r` 覆盖安装，未卸载现有包。
- 启动入口正常，包版本 5.6.0。
- 屏显弹窗截图中，普通文字、隐藏文字、眼睛图标和焦点眼睛按钮均可见；焦点节点可由 UIAutomator 读到 `visible` 控件。
- 上一轮像素复核：普通文字在 `#2A2F34` 上为 10.48:1，次要文字为 7.93:1，焦点眼睛图标配对对比度为 7.04:1。

### 2.3 误判处理

本轮曾临时修改 `display_option_*` 颜色，试图对应截图中的白卡片；复核实际链路后确认那是另一条播放器控制层弹窗，所有误改已恢复，工作区只保留本任务的屏显入口回归测试与文档。

## 3. 资源清理与回滚

无生产行为、数据库、协议、ABI 或用户偏好格式变化。最终验证后停止 Gradle daemon 并删除临时截图/XML/日志；设备只覆盖安装，不卸载、不清数据。回滚为撤销本任务提交即可。

## 4. 交付状态

- 当前：入口核对、源码回归与设备验证完成。
- 提交：待 task guard finish 创建。
- recovery tag：待 task guard finish 创建。
