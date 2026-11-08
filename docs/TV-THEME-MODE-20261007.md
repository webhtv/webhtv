# TV-THEME-MODE-20261007：电视版主题色彩深浅模式保存

## 问题

电视版主题色彩编辑器中的“浅色/深色”按钮只切换了当前页面预览：`ThemeDialog.applyDraft()` 原来只保存 `ThemeEditor` 草稿，没有把所选模式写入既有的 `theme_mode` 偏好。因此用户保存后重启，应用仍按旧模式（或系统模式）渲染；重新打开编辑器也可能显示错误的预览模式。

## 修复

- 电视版编辑器打开时读取 `Setting.getThemeMode()`，显式浅色/深色恢复对应预览；跟随系统时继续读取当前 Activity 的夜间状态。
- 电视版点击浅色/深色时同步更新草稿 `ThemeProfile.mode`，但取消不会写盘。
- 保存草稿成功后，只有本次确实选择了模式才写入 `Setting.putThemeMode(0/1)` 并调用 `ThemeController.applyNightModeToApp()`；跟随系统状态不会被无意改成显式模式。
- 新增 Leanback Robolectric 回归测试：深色→浅色双向保存、重开选中状态、取消不落盘、未选模式保持跟随系统。
- 更新 flavor 契约：手机端继续保持“仅本页预览”，电视端承担主题模式保存职责。

## 验证

- `:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.theme.ThemeDialogModeTest`：通过。
- `:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.theme.*`：通过。
- 最终定向测试（`ThemeDialogModeTest` + `ThemeInlinePreviewContractTest`）：通过。
- `bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5559`：Debug 构建成功，193 MB，使用 `adb install -r` 覆盖安装，未卸载原包。
- 设备已成功启动 TV Debug 包并确认设置页运行；UI 自动化树在该页面由嵌套列表提供有限信息，未再修改设备偏好文件。

## 回滚

回退本任务提交即可。主题 profile schema 未改变，既有 `theme_mode` 键与用户数据格式保持兼容。
