# TV-THEME-DIALOG-20261007：电视外观弹窗残余主题未生效

## 问题

电视版浅色模式下，`外观与语言` 弹窗出现“浅色行内容 + 深色外层面板”的混合状态：行文字已经来自 `ThemeController.current()`，但 `LightDialog` 的自定义弹窗根和窗口外壳仍使用静态 `shape_shell_proxy_dialog` / 编译期资源。标题文字因此落在错误的深色背景上，截图中“外观与语言”几乎不可读。

## 修复

- `LightDialog` 的自定义 shell 改为从 `ThemeController.current()` 生成 `colorSurfaceContainerHigh` 圆角背景。
- `LightDialog.apply(AlertDialog)` 的窗口背景也从当前语义 token 生成，并继续通过共享 binder 处理用户 profile 覆盖。
- `LightDialog` 两个入口都保留 `ThemeController.bindDialog(...)`；AlertDialog 入口额外绑定当前 shell drawable，避免窗口背景漏掉主题覆盖。
- 标题继续使用 `ThemeController.current().colorOnSurface()`，现在与实际弹窗 surface 属于同一 palette，浅色/深色均保持可读。

## 验证

- Leanback 主题测试：`159 tests completed`，`BUILD SUCCESSFUL`。
- 覆盖的回归契约包括：LightDialog 的窗口 shell 绑定、当前 surface token、标题语义颜色，以及 Appearance 行的浅深色对比度。
- 待本任务结束前：构建 leanback arm64 Debug、在 `192.168.50.3:5559` 使用 `adb install -r` 覆盖安装，截图确认外观弹窗外层和标题跟随当前模式；不卸载、不打正式包。

## 回滚

回退本任务提交即可；不修改主题 profile schema、用户偏好格式或播放器路径。
