# 阶段 1 本地可运行 fixture

`OriginIsolationPlaybackTest` 将 AndroidTest APK 的资源复制到 target Context 的
`noBackup/origin-isolation-test/<UUID>`，用测试自身的 `127.0.0.1:0` NanoHTTPD 只读服务。
A/B 配置真实加载；同站 key `origin_fixture`，A=`alice`，B=`bob`，不同 ext。
JS/Py/Jar 均使用真实生产 loader；Node 使用两份本地 bundle 与独立进程。
主当前配置、配置/Site/非 B History 行、偏好与当前 Node PID/Proxy 都在原源租约前后比较。
用户当前配置及其原始配置行/偏好在 finally 精确恢复；不删除用户配置/历史。

## 文件与真实性

- `config/config-{a,b}.json` 是配置模板，设备测试填写本次真实 base URL、API、ext 与 headers。
- `runtime/js-spider.js` 为有效 `export default` QuickJS spider；模块变量记录账号/初始化次数。
- `runtime/py-spider.py` 为真实单文件 Python spider，模块全局状态可拒绝同解释器伪隔离。
- `runtime/OriginFixture.java` 为本任务自行编写的原生 Jar spider；static 账号使 namespace-only 必红。
- `runtime/node/` 为有效 CatPawOpen `start(config)` bundle，支持 DEV_HTTP_PORT，真实提供 T4 与 PID/root/proxy 身份。
- `generate-media.mjs` 仅用 Node 内置 fs/crypto：读取已有跟踪的
  `app/src/debug/assets/exo-ass/sample-embedded.mkv`，取实际 AVC 压缩帧和 avcC，
  无转码地封装 fMP4，再 AES-128 加密为 HLS。输出只在 ignored `app/build/origin-isolation/`。
  当前输出：960×540，968 帧，32.267 秒，4 个真实视频 segment，含独立 init/map、key、签名 query。
- `hls/` 与 `media/tone.wav` 是第一轮的协议/音频样本，不供成功门使用；`map.bin` 是明确的占位字节，
  **不是**可解码的视频 init。成功门只使用生成的 AVC HLS，并在设备 MediaExtractor/真实播放页检查视频。

## 本地准备（不运行 Gradle、不下载二进制）

```powershell
$jdk='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot\bin'
$android='C:\Users\Maple\AppData\Local\Android\Sdk\platforms\android-36\android.jar'
$out='app/build/origin-isolation/jar-fixture'
New-Item -ItemType Directory -Force "$out/classes" | Out-Null
& "$jdk/javac.exe" --release 8 -encoding UTF-8 -proc:none -cp "$android;catvod/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes" -d "$out/classes" app/src/androidTest/assets/origin-isolation/runtime/OriginFixture.java
& "$jdk/java.exe" -cp 'C:\Users\Maple\AppData\Local\Android\Sdk\build-tools\36.0.0\lib\d8.jar' com.android.tools.r8.D8 --min-api 26 --lib $android --classpath catvod/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes --output "$out/origin-fixture.jar" "$out/classes/com/github/catvod/spider/OriginFixture.class"
node app/src/androidTest/assets/origin-isolation/generate-media.mjs
```

已实际执行：javac+d8 exit=0，Jar 2556 bytes，耗时 4.67 秒；媒体脚本 exit=0。
没有 Jar/Dex/HLS 新二进制进入 tracked assets。

## 设备输入与命令（核心统一编译/安装后）

```powershell
$adb='C:\Users\Maple\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$dest='/sdcard/Android/data/com.silent.android.webhtv/files/origin-isolation-input'
& $adb -s emulator-5554 shell mkdir -p $dest
& $adb -s emulator-5554 push app/build/origin-isolation/jar-fixture/origin-fixture.jar "$dest/origin-fixture.jar"
& $adb -s emulator-5554 push app/build/origin-isolation/media-fixture "$dest/media-fixture"
& $adb -s emulator-5554 shell am instrument -w -r -e class com.fongmi.android.tv.playback.origin.OriginIsolationPlaybackTest com.silent.android.webhtv.test/androidx.test.runner.AndroidJUnitRunner
```

默认 AndroidJUnitRunner 支持这里的 JUnit3 TestCase；使用现有 AndroidX InstrumentationRegistry，
没有新增依赖或启用 platform legacy runner。可用 `-e class ...OriginIsolationPlaybackTest#testRealRelayResponsesPreserveBobHlsKeyMapRedirectAndRange206`
只跑真实 relay 子场景；完整成功门是同类的 `testSingleMobileRuntimeSequenceRequiresRealOriginPlayback`。

成功门硬要求 READY → PLAY_READY → 原第 2 集 → 真实 AVC 视频/首帧和续播位置，
BLOCKED/UNAVAILABLE 均算失败，不 skip，不改 WebHome/两端 VideoActivity 的第二阶段既有测试。
故意起播后 segment404 必须是真实请求，且当前源 search 计数保持 0；背景保留 worker，真退出等待 worker 消失。
单测/fixture 编译成功不等于设备成功；以 instrumentation 原始输出为准。
