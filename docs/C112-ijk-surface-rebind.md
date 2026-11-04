# C112 · IJK 视频表面重绑与可观测性

## Recovery anchor

- 目标：修复 IJK（硬解与软解均）「只有声音、无图像黑屏」，并让 IJK 原生渲染失败可见。
- 验收：Java 编译通过；真机复现时出现 `video-rendering-start`（info=3）或 `surface rebind` 日志行，且画面出现。
- 状态：代码已改，编译验证中；未提交。
- 下一动作：读编译结果，然后写回本文件并交用户真机验证。

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
- 真机（待用户执行）：开诊断后在 IJK 硬解/软解上复现，预期出现 `surface rebind reason=... holder=... valid=...` 日志行与 `video-rendering-start`（info=3）。若仍黑屏，原生 EGL 错误此时会以 `[EGL] ...` 进入 logcat，可据此继续判断。
