# LIVE-UI-01 直播按钮跟随播放器设置 / 平板直播旋转

## 目标

1. 直播播放界面里与点播同名的播放器按钮，统一由「设置 → 播放 → 播放器按钮」的开关与顺序控制；重点修复直播中「播放参数」无法关闭的问题。
2. 平板上的直播全屏支持点播语义的旋转：在全屏内竖屏 ↔ 横屏切换，不再只能退出全屏。

## 现状与根因

- 点播：`PlayerButtonSetting.applyOrder/applyVisibility`（`app/src/main/java/com/fongmi/android/tv/setting/PlayerButtonSetting.java:146,202`）由两侧 `VideoActivity` 注册映射后生效。
- 直播：两份 `LiveActivity` 从未读取 `PlayerButtonSetting`，只按轨道条件写死显隐：
  - `app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java` 的 `setTrackVisible()` / `showControl()`
  - `app/src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java` 的 `setTrackVisible()`
  - 顶栏 `osdDiagnostics` 在 `app/src/mobile/res/layout/view_control_live.xml` 写死可见，代码从不修改其可见性。
  - 动作栏容器没有 id，无法交给 `applyOrder` 重排。
- 平板旋转：`showControl()` 在 `isPadLiveFullscreenMode()`（平板默认「平板直播=全屏」，`PlayerSetting.java:285-295`）时直接隐藏旋转按钮；`onRotate()` 只有「进/退全屏」两态，`getFullscreenOrient()` 对平板恒为 `USER_LANDSCAPE`，因此平板不存在全屏竖屏态。
- 语义陷阱：直播的 `rotate` 字段表示「是否全屏」（`isEmbeddedLiveUi()` 依赖它），不能照搬点播把 `rotate` 当「是否竖屏」，必须新增独立方向覆盖状态。

## 决策

- 直播动作栏只登记**与点播同名**的按钮：`PLAYER / DECODE / PLAY_PARAMS / SPEED / SCALE / CHANGE / TEXT / AUDIO / VIDEO`；直播专属按钮（配置、直播源、线路、反色、跨类、TV 端 OSD 设置与播放/暂停）不登记，保持既有布局行为。
- 显隐顺序固定为「动态条件在前、`PlayerButtonSetting.applyVisibility` 在后」：因为 `applyVisibility` 只负责隐藏、不负责恢复，顺序颠倒会让用户重新开启后仍不显示。
- 平板旋转使用独立字段 `livePortraitFullscreen`，`rotate` 语义保持不变；平板全屏内旋转只改方向、不退出全屏；`exitFullscreenLive()` 复位该字段，保证下次进全屏回到默认横屏。
- 顶栏「播放参数」快捷图标跟随 `PLAY_PARAMS` 开关隐藏；**不**额外绑定 `PlayerSetting.isOsdDiagnostics()`，避免把直播现有的一键开关入口一并收走（与点播 `VideoActivity` 的写法有意不同，属本次受控偏差）。
- 平板「标准」模式保持原行为：嵌入式点旋转进入全屏；全屏内旋转改为方向切换，退出全屏仍走返回键（`onBack` → `exitFullscreenLive()`）。手机行为完全不变。

## 改动清单

| 文件 | 改动 |
| --- | --- |
| `app/src/mobile/res/layout/view_control_live_action.xml` | 动作栏 LinearLayoutCompat 增加 `@+id/container` |
| `app/src/leanback/res/layout/view_control_live_action.xml` | 同上 |
| `app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java` | 新增 `mActionButtons`、`setupActionButtons()`、`applyActionButtonVisibility()`；`setTrackVisible()`/`setPlayParamsState()` 接线；旋转按钮可见性放宽；`onRotate()` 平板分支 + `toggleFullscreenOrientation()`；`getFullscreenOrient()` 平板竖屏支持；`exitFullscreenLive()` 复位 |
| `app/src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java` | 新增 `mActionButtons` 与同套注册/套用逻辑，接入 `setTrackVisible()`/`setPlayParamsState()` |
| `app/src/mobile/java/com/fongmi/android/tv/ui/dialog/LiveControlDialog.java` | 新增 `syncConfiguredButtonVisibility()`，面板跟随动作栏隐藏 player/decode/playParams |
| `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/LiveActivityLayoutTest.java` | 新增 4 个源码契约测试 |

## 验收

- 设置中关闭「播放参数」：直播底部动作栏 chip、顶栏参数图标、控制面板按钮同时消失；重新开启后立即恢复（重进控制栏即生效）。
- 设置中关闭「播放器/解码/倍速/画面比例/换源/字幕/音轨/视轨」：直播同名按钮同步消失，重开恢复；直播专属按钮不受影响。
- 平板（`smallestScreenWidthDp >= 600`）全屏：旋转按钮可见；点击在竖屏全屏 ↔ 横屏全屏切换且不退出全屏；退出再进恢复横屏默认。
- 手机：旋转仍为进/退全屏；平板「标准」模式嵌入式点旋转仍进全屏。

## 验证命令

```bash
bash gradlew :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.activity.LiveActivityLayoutTest'
bash gradlew :app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArm64_v8aDebug
```

真机/平板补充：`adb install -r app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk`。

## 回滚

- 按钮设置接入：还原两份 `view_control_live_action.xml` 的 `container` id，并移除两份 `LiveActivity` 的 `setupActionButtons/applyActionButtonVisibility` 接线与 `LiveControlDialog.syncConfiguredButtonVisibility()`。
- 平板旋转：`showControl()` 恢复 `isLock() || isPadLiveFullscreenMode()`、`onRotate()` 恢复两分支、`getFullscreenOrient()` 恢复恒定横屏、`exitFullscreenLive()` 移除复位（`livePortraitFullscreen` 字段可保留不使用）。
- 两处改动互相独立，可分别回滚。

## 验收结果（2026-10-21，emulator-5554 / 平板 1920×1080@280dpi ≈ 617dp / Android 9）

构建：`app-mobile-arm64_v8a-debug.apk`（15:52 构建，`classes15.dex` 内含 `rotate-fullscreen-pad`，确认包含本次改动），`adb install -r` 覆盖安装。

| 用例 | 步骤 | 结果 |
| --- | --- | --- |
| 问题1 真实设置路径 | 设置 → 播放设置 → 播放器按钮 → 「播放参数」显示→隐藏 → 确定 | 设备 prefs 变为 `player_button_hidden=play_params,multi_thread_proxy` ✅ |
| 问题1 直播生效 | 进入直播 → 唤出控制栏 | 动作栏无 `app:id/playParams` 节点；可见项为 配置/直播源/EXO/硬解/原始/音轨/视轨/反转/跨类/换源 ✅ |
| 问题1 反向恢复 | 恢复设置后再进直播 | `app:id/playParams` 节点回归：`text="播放参数" bounds="[429,1004][557,1066]"` ✅ |
| 问题2 平板旋转 | 平板全屏点右侧旋转 | 日志 `rotate fullscreen portrait=true` → `request reason=rotate-fullscreen-pad from=11 to=12` → `configuration changed new=1`（竖屏），`rotate=true` 仍为全屏；再点一次 `portrait=false` / `from=12 to=11` 回横屏 ✅ |
| 问题2 按钮可见 | 平板全屏（`isPadLiveFullscreen=true`）唤出控制栏 | 右侧旋转按钮可见（改动前被 `isPadLiveFullscreenMode()` 隐藏）✅ |

单元测试：`bash gradlew :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.activity.LiveActivityLayoutTest'` → BUILD SUCCESSFUL（17 tests）。首次运行暴露测试自身问题（`syncConfiguredButtonVisibility` 之后紧跟 `setScale`，`setTrackVisible` 在其之前，用 `section` 取不到方法体），已改用 `methodBody` 辅助方法修正后通过。

截图与日志位于 `C:\Users\Maple\AppData\Local\Temp\webhtv-verify-4\`（control.png=显示态、live_hidden_real2.png=隐藏态、rotate2.png=竖屏全屏、live_restored2.png=恢复态）。

## C93 复评补强（2026-10-21 18:40，dev1）

- **必修项（中）**：两份 `LiveActivity#showControl` 未重算轨道按钮。点播 `showControl` 会调 `setTrackVisible()`，直播没有；而 `applyActionButtonVisibility()` 只恢复无动态条件的 player/decode/playParams/scale/change，text/audio/video 依赖动态判定。直播存活期间（如 PiP）在设置里重新开启字幕/音轨/视轨后，这些按钮本会话不会回来。
- **修复**：mobile `showControl()` 在 service 守卫后调用 `setTrackVisible()`；leanback `showControl(View)` 以 `service() != null` 守卫后同样调用。
- **守卫测试**：`LiveActivityLayoutTest#liveControlRevealRefreshesTrackButtons`（两 flavor 断言 `setTrackVisible()` 存在且先于 `setPlayParamsState()`）。修复前 FAILED，修复后 18/18 BUILD SUCCESSFUL；`assembleMobileArm64_v8aDebug` 与 `assembleLeanbackArm64_v8aDebug` 均通过。完整记录见 `docs/C93-beta-merge-review-dev1-20261021.md`。

## Recovery anchor

- 状态：实现 + 单测 + 平板/手机真机（模拟器）验收完成；C93 复评补强（控制栏唤出重算轨道按钮）已修复并验证，设备 prefs 已恢复原值（`player_button_multithread` 语义等价：仅 `multi_thread_proxy` 隐藏）。
- 下一步（唯一）：无；如需继续可基于本文件扩展直播专属按钮（配置/直播源/线路/反色/跨类）的可配置化。
