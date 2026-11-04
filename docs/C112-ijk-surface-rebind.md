# C112 · IJK 视频表面重绑与可观测性

## Recovery anchor

- 目标：修复 IJK（硬解与软解均）「只有声音、无图像黑屏」，并让 IJK 原生渲染失败可见。
- 验收：Java 编译通过；真机复现时出现 `video-rendering-start`（info=3）或 `surface rebind` 日志行，且画面出现。
- 状态：已提交 `e370242207`；已合并 `origin/beta` `6e23ec560d`（合并提交 `f6ab19f76a`，无冲突）；C118 复评已修 2 处并双 flavor 编译通过；待真机验证。
- 下一动作：真机按下方验收步骤复现，确认日志行与画面；失败则据 `[EGL]` 原生行继续定位。

## 症状与证据

用户报告（2026-11-03/04）：py 源红果短剧，IJK 硬解与软解**均**只有声音、无图像黑屏；同一源 exo/mpv/系统解码正常。

第三份诊断包 `G:\下载\webhtv-av-report (ijk解码黑屏)\`（av-events.jsonl 2082 条事件行）：

| 观测点 | 值 | 含义 |
| --- | --- | --- |
| `ijk.event` info **10004** | ×5 | MEDIA_INFO_VIDEO_DECODED_START，视频解码确实出过首帧数据 |
| `ijk.event` info **3** | **×0** | MEDIA_INFO_VIDEO_RENDERING_START 从未出现 |
| `video.output.summary` frameRate | 恒 **0.0**（×9） | 渲染器零输出帧 |
| `video.output.format` | 1920x1080（×25） | 解码侧尺寸正常 |
| `video.first-output` | ijk 0 条（10 条全为 exo） | 无首帧提交 |
| `play.attempt.end` | videoEvidenceLevel=**0** / audioEvidenceLevel=**3** | 视频零证据、音频正常 |
| `ijk.event` 10002 | ×5 | MEDIA_INFO_AUDIO_RENDERING_START 正常触发 |

硬解与软解表现完全一致（5 次尝试同模式）→ 共同点不是解码器，而是 **Surface**。

## 根因

`app/src/main/java/com/fongmi/android/tv/player/engine/IjkSimplePlayer.java` 的 `openCurrent()`：

```
:801   configureOptions(sourceUri, dash);
:802   bindVideoOutput();          // 下发 Surface
:803   ijk.setDataSource(...);
:809   ijk.prepareAsync();         // native pipeline 在此之后才创建
```

Surface 在 native 视频输出存在**之前**下发，此后没有任何重新下发时机：`onPrepared` 只打诊断事件，`onInfo` 只处理 info 码。唯一补救是 `surfaceCallback` 的 `surfaceCreated`/`surfaceChanged`（:1216-1238），而现场 holder 恒为 640x360、尺寸不变 → `surfaceChanged` 不触发（日志 `surface-size` 行 player=IJK 共 89 条，恒 `holder=640x360/valid=true`）。

对照证明该链本身可用：`app/src/main/java/com/fongmi/android/tv/player/engine/SystemSimplePlayer.java:332-362` 与 IJK 的 Surface 代码逐行同构（`setSurfaceHolder`→`addCallback`→`setDisplay(holder)`），系统解码在同一 SurfaceView 正常出画。

### 附带缺陷：原生失败不可见

`app/src/main/java/tv/danmaku/ijk/media/player/IjkMediaPlayer.java`：

- `:484 native_setLogLevel(IJK_LOG_SILENT);` 在 `initNativeOnce()` 中**硬编码静音**；
- `setLogEnabled(boolean)` 是**空方法体**，全仓无人调用。

因此 `bind surface failed`/`ANativeWindow`/`EGL` 在日志中 0 命中并非「没出错」，而是错误进不了可见通道（`libijksdl.so` 内确实含 `[EGL] ANativeWindow_fromSurface: failed`、`[EGL] IJK_EGL_prepareRenderer failed` 等串，且 `libijkplayer.so`/`libijksdl.so` 均引用 `__android_log_print`）。

## 改动

1. `IjkSimplePlayer`：新增 `rebindVideoOutput(String reason)`，在 `prepareAsync()` 之后与 `onPrepared()` 各补绑一次；无 holder 且无有效 surface 时直接返回，失败仍走既有静默路径。
2. `IjkMediaPlayer`：`setLogEnabled` 真正驱动 `native_setLogLevel`（开→`IJK_LOG_DEBUG`，关→`IJK_LOG_SILENT`），并对 `UnsatisfiedLinkError` 降级。
3. `IjkSimplePlayer` 构造中 `ijk.setLogEnabled(SpiderDebug.isEnabled())`，让原生日志跟随诊断开关。

## 决策记录

- 用户批准「按上述 2 处改动实施补丁」。
- 用户明确「surface_size 本任务不动，先只修绑定时序」→ 不修改 `PlaybackActivity.syncVideoSurfaceSize` 的 `setSizeFromLayout` 行为（`app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java:826-848`），避免引入第二个变量。

## 回滚

`git checkout -- app/src/main/java/com/fongmi/android/tv/player/engine/IjkSimplePlayer.java app/src/main/java/tv/danmaku/ijk/media/player/IjkMediaPlayer.java`

## 验证

- 编译：`./gradlew :app:compileLeanbackArmeabi_v7aDebugJavaWithJavac --offline` → **BUILD SUCCESSFUL in 1m 11s**（1 个既有 Room 警告，与本改动无关）。
  - 注：`compileLeanbackDebugJavaWithJavac` 不是有效任务名；flavor 维度为 `mode`（leanback/mobile）× `abi`（arm64_v8a/armeabi_v7a），见 `app/build.gradle:63-80`。
- 真机（待用户执行）：开诊断后在 IJK 硬解/软解上复现，预期出现 `surface rebind reason=... holder=... valid=... action=...` 日志行与 `video-rendering-start`（info=3）。若仍黑屏，原生 EGL 错误此时会以 `[EGL] ...` 进入 logcat，可据此继续判断。

## C118 合并 beta 后的复评修复（2026-11-04）

合并 `origin/beta` `6e23ec560d` 后双轴复评，修 2 处，均为可观测性缺陷而非主修复路径缺陷：

1. `IjkSimplePlayer.bindVideoOutput()` 改为返回 `boolean`；`rebindVideoOutput` 用它区分真实下发与空操作，日志新增 `action=rebound|none`。原实现无条件打印 `surface rebind`，会把「持 holder 但 surface 无效、实际什么都没绑」记成已重绑，削弱本任务第二目标（让失败可见）；`action=` 也与既有 `bind surface failed ... action=keep-current` 词表对齐。
2. `IjkMediaPlayer` 删除 `mLogEnabled` 字段，`applyNativeLogLevel(boolean enabled)` 直接接收参数。该字段唯一读者就在同方法内，属冗余状态。

复评保留：`prepareAsync()` 之后（`:812`）与 `onPrepared()`（`:583`）两处补绑均保留——后者是真正出画时机，前者为 spec 明确要求且无害。

评审判定为**非缺陷、不改**的项：
- `volatile` 争议：字段已删除，随该修复消失。
- 原生符号风险：`initNativeOnce()` 原本就无保护地调用 `native_setLogLevel(IJK_LOG_SILENT)`（`app/src/main/java/tv/danmaku/ijk/media/player/IjkMediaPlayer.java:484`），随包 `.so` 必然导出该符号；本次仅改传参，未新增符号依赖。
- 运行中切换诊断开关不即时生效：`setLogEnabled` 仅在构造时调用一次（`IjkSimplePlayer.java:152`）；因开关在设置页、每次播放都会重建 player，实际影响可忽略。

验证：`./gradlew :app:compileLeanbackArmeabi_v7aDebugJavaWithJavac :app:compileMobileArmeabi_v7aDebugJavaWithJavac --offline` → **BUILD SUCCESSFUL in 2m 19s**。

回归基线：`./gradlew :app:testLeanbackArmeabi_v7aDebugUnitTest :app:testMobileArmeabi_v7aDebugUnitTest --offline` → 4886 tests, 8 failed, 3 skipped。8 项**全部为既有失败**，与本改动无关：`origin/beta` 与 `HEAD` 的差异仅本任务 3 个文件，且这 7 个测试文件与 beta 逐字节相同、无一引用被改文件。失败清单：`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`、`NativeEnhancedPlaybackStyleFocusTest`、`ReaderPlaybackRoutingSourceTest`×2、`SearchResultDownFocusTest`、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`。
