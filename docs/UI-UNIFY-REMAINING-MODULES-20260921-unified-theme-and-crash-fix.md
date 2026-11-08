# 追更及剩余模块统一主题 + 重载闪退修复

## Recovery anchor

- 目标：把追更、实验室、站点健康、抖音搜索等剩余模块并入统一主题；提供统一的主题模式设置；修复“重新拉取接口闪退”。
- 允许路径：`app/src/**`（res/theme/dialog/fragment/lab/loader）、`docs/**`。
- 验收标准：strict 静态检查零未豁免；主题相关单测与编译通过；同一接口连续重载 3 次不崩溃；外观设置新增“主题模式”并可切换深/浅色。
- 当前状态：全部完成并设备验证，待提交。
- 下一步唯一动作：提交本任务并生成 recovery tag。

## 1. 追更及剩余模块并入统一主题

### 1.1 追更（此前未接入）

`activity_following.xml` / `item_following.xml` 此前使用独立的 `following_*` 浅色调色板（`#F3F7FD/#2563B9/#FF8A00` 等），完全绕过统一 token：

- 布局改为直接消费共享语义属性：`colorSurface`、`colorPrimary`/`colorOnPrimary`、`colorSecondaryContainer`/`colorOnSecondaryContainer`、`colorErrorContainer`/`colorOnErrorContainer`、`colorOnSurfaceVariant`、`colorOutlineVariant`；
- `colors.xml` 中 `following_*` 保留为指向 `webhtv_color_*` 的兼容别名，旧引用不再产生第二种配色；
- 6 个 `following_button_*` 状态列表自动跟随语义色。

### 1.2 剩余独立调色板

- `lab_colors.xml`（含 `values-night`）的 `lab_surface/lab_text_primary/lab_text_secondary` 改为语义 token 别名；
- `site_health_good/warn/bad` 改为 `webhtv_color_health_*` 别名；
- TV 端 `TmdbCastPresenter`、`TmdbVideoPresenter` 的硬编码 ARGB 改为读取 `ThemeController.current()` 的语义色（播放器激活色仍取自 `playerControlActive`）；
- 移动端 `AppearanceDialog`、`SearchFragment`、`CollectFragment` 的硬编码文字色改为 `ThemeController.current()`。

## 2. 统一主题设置功能

- `Setting` 新增持久化 `theme_mode`：`-1` 跟随系统、`0` 浅色、`1` 深色；
- `ThemeController` 新增：`applyNightModeToApp()`（把偏好映射到 `AppCompatDelegate`）、`resolveFromPreferences()`（合并模式 + `theme_color` + `wall_color` seed）、`applyFromPreferences()`（把只读快照应用到 Activity，且不覆盖透明 edge-to-edge 系统栏）；
- `App` 启动时应用模式；`BaseActivity`（mobile/leanback）创建时刷新快照；
- 外观与语言对话框新增“主题模式”一行（跟随系统 / 浅色 / 深色），切换后立即刷新，不重建数据库、不影响播放；
- 新增 `ThemeControllerContractTest` 固化以上契约（模式映射、Activity 接线、旧调色板别名、追更与详情界面语义化）。

## 3. “重新拉取接口”闪退（阻塞级）

### 3.1 复现与日志证据

`192.168.50.3:5559`（Android 9、`ro.product.cpu.abi=x86_64`、`ro.dalvik.vm.native.bridge=libnb.so`）上，设置页确认接口 URL 后进程整体消失。`dumpsys dropbox --print SYSTEM_TOMBSTONE`：

```text
pid: 9141, tid: 9155, name: HeapTaskDaemon  >>> com.silent.android.webhtv <<<
signal 11 (SIGSEGV), code 2 (SEGV_ACCERR), fault addr 0x7fff727cf2f8
backtrace:
    #00 pc 00000000003558c0  /system/lib64/libhoudini.so
    #01 pc 000000000035b6b0  /system/lib64/libhoudini.so
    #02 pc 000000000035f42d  /system/lib64/libhoudini.so
    00007fff5b16e308  /system/lib64/libart.so (_ZN3art9Libraries21UnloadNativeLibrariesEv+1384)
```

同形态 tombstone 在设备上共 9 条（首次 2026-09-20 17:55:50）。重载日志显示恰在同一时刻执行 `cache/jar/<md5>.jar` 删除与重建、`jar-loader`/`csp-warmup` 初始化，因此该崩溃不是 Java 异常（`try/catch` 与 `CrashActivity` 都无法捕获）。

### 3.2 根因

接口自带 CSP jar 由 `DexClassLoader` 加载。重载时 `JarLoader.clear()` 清空 `loaders`，被替换的 loader 失去强引用；GC 时 ART 调用 `UnloadNativeLibraries()`，在 Houdini 转译环境下回调转译器并访问失效映射 → SIGSEGV。真机（原生 ABI）不经过该回调，因此只在 x86 转译模拟器出现。

### 3.3 修复

新增 `NativeBridgeGuard`：

- 判据一：`ro.dalvik.vm.native.bridge` 非空且非 `0`；
- 判据二（后备）：主 ABI 与受支持 ABI 同时包含 x86 族与 ARM 族；

`JarLoader.clear()` 命中判据时把旧 loader 移入进程级保留列表（`retireLoaders()`），让 ART 不进入不安全的卸载路径；真机路径行为完全不变，且不改变数据、配置、播放或网络逻辑。

## 4. 设备实测（`192.168.50.3:5559`）

| 场景 | 结果 |
| --- | --- |
| 同一接口连续重载 3 次 | 3/3 成功，进程保持前台，`FATAL EXCEPTION=0` |
| 日志出现 `retained translated loaders` | 是，证明判据在该设备生效 |
| 追更页进入 | 正常显示（空态 + 顶部动作），无崩溃 |
| 外观与语言 | 显示“5 项”，新增“主题模式”，点击后出现 跟随系统/浅色/深色 |
| 切换到深色 | 写入 `theme_mode=1`，界面即时变深色，`FATAL EXCEPTION=0` |
| 主题切换后崩溃（附带修复） | `AudioMiniPlayer` 在重建后仍调用已置空 `root`，`ViewCompat.requestApplyInsets` NPE；已改为捕获视图并在重建后跳过，复测无崩溃 |

## 5. 验证与回滚

- 静态：`scripts/check_ui_tokens.sh --strict` → `violations=0 legacy=0`，`hex_layouts/hex_drawables/hex_colors` 均为 0，38 组对比度通过；
- 单测：`ThemeControllerContractTest`、`ThemeResolverTest`、`ThemeContractTest`、`UiStyleSourceTest`、`UiLayoutSourceTest`、`WebThemeTokenSourceTest`、`NativeBridgeGuardTest`、`JarLoader*`、`setting.*` 全部通过；
- 编译：mobile/leanback arm64 debug 通过；
- 回滚：Loader 修复与主题统一可分别回滚，无数据迁移、无 native 二进制变更。
