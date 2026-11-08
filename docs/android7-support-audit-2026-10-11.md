# Android 7（API 24/25）最低支持能力全面排查

- 排查日期：2026-10-11（CST）
- 排查对象：`dev1` 工作区 HEAD `8ed903ac2`（分支 `dev1`），工作区干净（除既有未跟踪 `.scratch-goal/`）
- **真实设备实证**：2026-10-12 在 `192.168.50.3:5587`（**Android 7.1.2 / API 25 / x86 + houdini ARM 翻译 / 32 位**）上完成端到端验证，坐实本文核心结论（详见 §9）
- 结论：**构建/安装/依赖层仍声明并实际满足 API 24；依赖、Python/Node 运行时、脱糖、签名、原生库对齐全部合规**。排查中确认并修复了 3 个真实缺陷（其中 2 个在 Android 7 上会直接崩溃）；**剩余唯一硬缺口是内置 MPV 播放内核在 Android 7.0/7.1（以及 8.0/8.1）上无法加载**。因此“最低支持安卓 7”目前只对 Exo/IJK/系统内核成立，MPV 内核在 API < 28 上不可用（不崩溃，走回退）。

## 1. 声明的支持范围

| 项 | 值 | 证据 |
| --- | --- | --- |
| `minSdk` | 24（Android 7.0） | `gradle/libs.versions.toml:4`，`app/build.gradle:39`，其余 4 个模块同源 |
| `targetSdk` | 28 | `gradle/libs.versions.toml:5` |
| `compileSdk` | 37 | `gradle/libs.versions.toml:6` |
| 合并后清单 | `minSdkVersion="24"` | `app/build/intermediates/packaged_manifests/leanbackArm64_v8aDebug/.../AndroidManifest.xml` |
| APK 签名 | 仅 v2（v1=false） | `apksigner verify -v`；Android 7.0 支持 v2，可安装 ✔ |
| 打包方式 | `extractNativeLibs="false"` + `.so` Stored + zipalign 4 页对齐 | `zipalign -c -p -v 4` = Verification successful ✔ |

## 2. 依赖最低版本（全部 ≤ 24，无冲突）

解析 191 个模块，逐个读取 AAR 清单 `minSdkVersion`：

- 最高：`androidx.webkit:webkit:1.16.0` → **24**（正好等于我们的下限）
- 次高：`androidx.core:core:1.18.0`、`androidx.lifecycle:*:2.10.0`、`androidx.media:media:1.8.0`、`androidx.work:work-runtime:2.11.2`、`com.google.android.material:material:1.14.0`、`androidx.room:room-runtime:2.8.4`、`com.suyashbelekar:exoplayerhdrutils:0.4.0` → 23
- 本地 AAR：`thunder-release.aar` 24、`hook-release.aar` 24、`tvbus-release.aar` 24、`jianpian-release.aar` 21、`forcetech-release.aar` 21
- 本地 maven：`media3-*:1.11.0-alpha01-fongmi` → 23
- **没有任何依赖要求 > 24**（否则 manifest merger 会直接报错，构建无法产出 minSdk=24 的清单）

## 3. 原生库逐符号排查（NDK r29 各 API 级 stub 对照）

方法：对每个随包发布的 `.so`，用 `llvm-readelf --dyn-syms` 取出 **GLOBAL UND**（弱引用不算）符号，再与 `$NDK/.../sysroot/usr/lib/<triple>/{24,26,28}/` 平台 stub 的导出符号集对照。此 stub 由 AOSP 各 API 级符号表生成，等价于“该 API 级设备上平台库是否导出此符号”。

覆盖范围：APK 内 30 个 `.so`（arm64-v8a）+ `jniLibs`/mpv 资源/v7a 资源（armeabi-v7a）。

### 3.1 唯一硬阻塞：`libmpv.so`

两个 ABI 的 `app/src/<abi>/assets/mpv-libs/<abi>/libmpv.so` 都直接引用 6 个 **Vulkan 1.1 core** 入口：

```text
vkGetPhysicalDeviceProperties2        vkGetPhysicalDeviceFormatProperties2
vkGetPhysicalDeviceImageFormatProperties2
vkGetPhysicalDeviceQueueFamilyProperties2
vkCreateSamplerYcbcrConversion        vkDestroySamplerYcbcrConversion
```

- 平台 `libvulkan.so` 导出情况：API 24/25/26/27 = **不导出**；API 28 起 = 导出（NDK stub 24/26/27 各 148 个符号、28 起 182 个）。
- AOSP 权威标注：`platform/frameworks/native/vulkan/libvulkan/libvulkan.map.txt` 对上述符号标注 `# introduced=28`（Android 9）。即 Android 7/8 的 loader 根本不导出这些名字。
- 绑定方式：`libmpv.so` 同时带 `DT_FLAGS = SYMBOLIC BIND_NOW` 与 `DT_FLAGS_1 = NOW`（即时绑定），且 Java 侧用 `System.load()`（`Runtime.nativeLoad` 走 `RTLD_NOW`）。**缺符号 = 加载失败**，不是延迟到调用才失败。
- 实测（真机 bionic 复现机制）：用 API 24 的 x86_64 `libvulkan.so` stub 作为平台库、构造一个带 `BIND_NOW` 且引用 `vkGetPhysicalDeviceProperties2` 的探针库，在 API 28 设备上得到
  `dlopen failed: cannot locate symbol "vkGetPhysicalDeviceProperties2" referenced by ".../libprobe.so"`；
  换回真实平台 `libvulkan.so`（API 28，有该符号）立即加载成功。即失败原因确为“该符号在平台库中不存在”，与 BIND_NOW 组合即为硬失败。
- 应用侧表现：`is/xyz/mpv/MPVLib.ensureLoaded()` 在 `LOAD_ORDER` 中 `System.load(libmpv.so)` 抛错，被 `catch (Throwable)` 捕获 → `loadError` 记录 → 返回 false。`MpvPlayer.ensureInitialized()` 抛 `IOException`。**不崩溃**，由 `PlayerManager.fallbackPlayer()` 按 `EXO → IJK → MPV → 系统` 顺序回退。因此 Android 7 上：App 可用，但 MPV 内核（含 FEL/DV7、Vulkan 输出、蓝光菜单等全部 MPV 专属能力）不可用。

### 3.2 已被守卫拦截：`libexo_dovi_renderer.so`

`app/src/main/cpp/CMakeLists.txt` 的 `exo_dovi_renderer` 使用 `AHardwareBuffer_describe`、`AImageReader_newWithUsage`、`AImage_getHardwareBuffer`、`ANativeWindow_toSurface`（均为 API 26+，因 `app/build.gradle:57` 传 `-DANDROID_PLATFORM=android-26` 才会链接成功），且带 BIND_NOW。

但 Java 侧在加载前即按 SDK 拦截：`ExoDv5Native.loadLibrary()` / `probe()` 先判断 `Build.VERSION.SDK_INT < ExoDv5GpuMappingPolicy.MIN_GPU_MAPPING_API`（= 26）直接返回 `api-below-26`，**不会** `System.loadLibrary`。故 API 24/25 上不会触发 dlopen 失败；DV5 渲染器在 Android 7 上按设计不可用。

### 3.3 其余原生库全部通过

`libexo_ass.so`、`libijkplayer.so`、`libijkffmpeg.so`、`libijksdl.so`、`libsherpa-onnx-jni.so`、`libtensorflowlite_jni.so`、`libquickjs-android-wrapper.so`、`libavif_android.so`、`libdovi_android.so`、`libjpa.so`、`libxl_stat.so`、`libxl_thunder_sdk.so`、`librtmp-jni.so`、`libpl_droidsonroids_gif.so`、`libmedia3ext.so`、`libavcodec/libavutil/libswscale/libswresample.so`、Chaquopy 全部 `lib*_python.so`/`libpython3.10.so`、mpv 的 `libc++_shared/libmv*/libmw*/libplayer.so` —— 均无 API 26+ 的 GLOBAL 未定义符号（`memfd_create`、`getentropy` 等属弱引用，不阻塞加载）。

32 位侧：`app/src/main/jniLibs/armeabi-v7a/*` 5 个库与 `app/src/armeabi_v7a/assets/mpv-libs/armeabi-v7a/*` 结论与 arm64 一致（`libmpv.so` 同样命中 6 个 Vulkan 1.1 符号）。
注：`third_party/exo-ass-native/prebuilt/` 只有 `arm64-v8a`，v7a 包没有 `libexo_ass.so`（与 Android 版本无关的既有 ABI 覆盖问题）。

## 4. 运行时按需下载组件

- **Chaquopy/Python**：`com.chaquo.python` 17.0.0 插件内置字符串 `minSdk version 24 or higher`，即其下限恰为 24，与项目一致 ✔。随包 Python 原生库无 API 26+ 符号 ✔。
- **nodejs-mobile（猫源）**：`NodeLib.VERSION = v18.20.4`，运行时下载。实测下载官方 `nodejs-mobile-v18.20.4-android.zip` 并逐符号分析 `bin/{arm64-v8a,armeabi-v7a}/libnode.so`：仅依赖 `libc++_shared/libc/libdl/liblog/libm`，**无任何 API 26/28 专属符号**（缺的 122 个符号全是 `std::__ndk1::` C++ ABI 符号，由 APK 自带 `libc++_shared.so` 提供）✔。

## 5. Java/Kotlin 框架 API 排查

`./gradlew :app:lintLeanbackArm64_v8aDebug` → 报告 `app/build/reports/lint-results-leanbackArm64_v8aDebug.txt`。修复后该报告为 236 errors，其中 `NewApi` 33 处（17 处 API 26、1 处 API 27、13 处 API 29、1 处 API 31、1 处 API 33）；本轮三处修复共移除 18 条 `NewApi`（`ViewCompat` 2 + `Process#isAlive` 6 + `Matcher` API 34 重载 10），故修复前为 **51 处**（25 处 API 26、1 处 API 27、13 处 API 29、1 处 API 31、1 处 API 33、10 处 API 34）。（C62 复评订正：原文写作「52 处（23 处 API 26…）」，与报告自身 254 → 252 → 246 → 236 逐级减少 18 条、以及当前报告实测的 33 条不一致。）

逐条核实结果：

| 位置 | 结论 |
| --- | --- |
| `PlayerManager$AudioFocusApi26`、`PlaybackSystemConditionMonitor$Api29Thermal`、`MpvAudioCapabilities.supportsDirect` | 均在 API 隔离内部类中，调用点有 `SDK_INT >= O/Q/S` 判断 ✔ 安全 |
| `ReaderTtsService`/`LabRuntimeService` 的 `NotificationChannel`、`NodeService.startForegroundCompat` | 有 `SDK_INT >= O/Q` 判断 ✔ 安全 |
| `FollowingNotifier`/`Notify` | 用 AndroidX `NotificationChannelCompat`（内部按 API 降级）✔ 安全 |
| `ContextCompat.startForegroundService`、`NotificationManagerCompat` | AndroidX 兼容层 ✔ 安全 |
| `ChineseText`（`android.icu.text.Transliterator`，API 29） | 外层 `try/catch (Throwable)`，失败返回原文 ✔ 安全降级 |
| `ExoFrameSchedulingPerfettoTrace`（`Trace.setCounter/endAsyncSection`，API 29） | 整体 `try/catch (Throwable ignored)` ✔ 安全降级（AndroidX `TraceCompat` 亦有同款 API 隔离） |
| `MpvPlayer.requestSurfaceFrameRate`（`Surface.setFrameRate`，API 31） | `try/catch (Throwable)` ✔ 安全降级 |
| `CacheScheduler`（`JobInfo.Builder#setRequiresBatteryNotLow`，API 26） | 外层 `try/catch (Throwable ignored)`，仅跳过省电条件 ✔ 不崩溃 |
| `GitCloudDialog:2163`/`RemoteTrustDialog:3090`（`View#setImportantForAutofill`，API 26） | **真实缺陷（已实机验证，已修复）**：`TextInputEditText`/`AppCompatEditText`/`EditText`/`TextView` 均未声明该方法，运行时反射解析到 `android.view.View#setImportantForAutofill`（API 26，实机 API 28 上 `getMethod(...).getDeclaringClass()` = `android.view.View`）。调用点位于 `GitCloudDialog.buildView()→input()` 与 `RemoteTrustDialog.input()`，而两者在对话框创建链路上被无条件调用且无 try/catch → **Android 7 上打开「Git 云盘」或「远程信任」设置弹窗会抛 `NoSuchMethodError` 崩溃**（手机版 `SettingEnhanceFragment` 与 TV 版 `SettingEnhanceActivity` 均可达）。已改用 `ViewCompat.setImportantForAutofill(view, ...)`（内部 `SDK_INT < 26` 提前返回，API ≥ 26 委托到同一个 `View#setImportantForAutofill`，行为零变化）✔ 已修复 |
| `WebViewClient#onRenderProcessGone`（API 26 回调，`RenderProcessGoneDetail` 类为 API 26） | 覆写方法在 API < 26 上不会被系统调用；`detail.didCrash()` 的 `RenderProcessGoneDetail` 类级 `since=26`，但 ART 惰性解析 + 该回调仅在 API ≥ 26 触发 → 无崩溃风险（`CatWebActivity`/`WebReaderActivity`/`HomeWebController` 三处一致） |
| `LabRunner`/`LabTerminalActivity`（`Process#isAlive`/`destroyForcibly`，API 26） | **真实缺陷（已修复）**：`LabRunner.isRunning()/runningCount()/runningKeys()` 在 `LabAutoStart`（`App` 启动后 3 秒无条件执行）、`LabPackageAdapter.running()`、`LabCommandAdapter.onBindViewHolder()`、`LabDetailActivity.anyRunning()`、`LabRuntimeService` 中调用。`NoSuchMethodError` 继承自 `Error` 而非 `Exception`，所以 `LabAutoStart` 的 `catch (Exception ignored)` **捕不住**，会沿后台线程抛到 `ThreadGroup` → 进程崩溃。触发条件：用户已安装实验台条目（`LabEnv.installed(item)` 为真且条目带 `commands`）。已新增 `LabRunner.isAlive(Process)`（用 `exitValue()`/`IllegalThreadStateException`，API 1，语义等价，已用 JVM 探针验证）并替换全部 6 处调用；`LabRunner.stop()` 里的 `destroyForcibly` 原本就有 `catch (Throwable)` 兜底 ✔ 已修复 |
| `MpdSanitizer`（`Matcher#appendReplacement/appendTail` 的 `StringBuilder` 重载，**API 34**） | **真实缺陷（已实机复现并修复）**：`desugar_jdk_libs_nio` 2.1.5 不含 regex 回移植（jar 内无 `regex` 包），D8 也未改写该调用；debug 与 Release APK 的 dex 均保留 `appendReplacement:(Ljava/lang/StringBuilder;...)`。真机实测（API 28，修复前）调用 `MpdSanitizer.sanitize()` 抛 `NoSuchMethodError`。该异常继承 `Error`：`MpdSanitizer.sanitize()` 自身的 `catch (Exception e)` 捕不到，`Source.fetch()` 的 `catch (Exception)` 也只包住 `extractor.fetch(result)`（`Source.java:100-104`）、并不覆盖紧随其后的 `MpdSanitizer.sanitize(fetched)`（`Source.java:110`）。因此修复前的失败模式是**上抛 `NoSuchMethodError`**（调用链上典型兜底如 `SiteApi.playerContent` 的 `catch (Exception)` 同样拦不住 `Error`），不是「静默返回未处理 URL」。触发条件：MPD 中确实存在需要清洗的项（`appendReplacement` 只在命中非法值时调用，且 `removed[i] == 0` 会提前 `return xml`），干净 MPD 不受影响。影响：MPD 清洗（frameRate/AdaptationSet id 修复、YouTube jar MPD 处理）在 **API < 34 全平台**不可用（命中即抛错）。**（C62 复评订正）**已改用 `StringBuffer` 重载（API 1），实机重测两个触发路径均返回 `changed=true` ✔ 已修复 |
| `org.jupnp` 3.0.4 `DOMParser.escape()`（同样的 API 34 `StringBuilder` 重载） | 三方库缺陷，同类问题；仅在 jUPnP 内部走 `escape()` 路径时触发（DLNA/投屏相关） |
| `hook-release.aar`（`Hook extends PackageManager`，引用 API 26 的 `ChangedPackages`/`SharedLibraryInfo`/`VersionedPackage`） | 与 API 级别无关的既有 AAR 缺陷：`Hook` 继承 `PackageManager` 但**未实现任何抽象方法**（javap 实测 0 个方法），且引用了 API 26 类型；仅在迅雷/电视直播内核路径被 `TVBus.init()` 使用，包在 `try/catch (Exception ignored)` 内 |

结论：Java 层原本有 **3 个真实缺陷**（Git 云盘/远程信任弹窗崩溃、Lab 实验台启动崩溃、MPD 清洗静默失效），均已修复；其余 API 26+ 调用均有 SDK 判断或 `catch (Throwable)` 兜底。

### 5.1 修正：先前误判

排查过程中我曾把 `GitCloudDialog:2163` / `RemoteTrustDialog:3090` 误判为“Material 内部有 SDK 判断”，后经逐层 `javap` 验证：`TextInputEditText → AppCompatEditText → EditText → TextView` 四层均**未声明** `setImportantForAutofill`，最终落到 `android.view.View`（`api-versions.xml` 标注 `since=26`）；实机（API 28）反射也确认 `declaring class = android.view.View`。该误判已在本文中更正，教训是：**lint 报的 `NewApi` 不能靠“库可能有覆盖”推测否定，必须验证声明类**。

## 6. 最终结论

| 能力 | Android 7.0/7.1（API 24/25） |
| --- | --- |
| 安装/启动 | ✔ 可安装（minSdk 24、v2 签名）、依赖全部满足 |
| Exo 内核（含 libexo_ass 字幕、ijk） | ✔ 可用 |
| IJK / 系统内核 | ✔ 可用 |
| 猫源（nodejs-mobile 运行时下载） | ✔ 可用（libnode.so 无 API 26+ 依赖） |
| Python 爬虫（Chaquopy 3.10） | ✔ 可用（插件下限 24） |
| DLNA/投屏（jUPnP） | △ 基本可用；`DOMParser.escape()` 存在 API 34 缺陷（三方库，全平台 API<34 一致） |
| Git 云盘 / 远程信任设置弹窗 | ✔ **已修复**（原为 `View#setImportantForAutofill` 未守卫崩溃，现改用 API 隔离的 `ViewCompat`） |
| **MPV 内核** | ✘ **不可用**（`libmpv.so` 需 API 28 的 Vulkan 1.1 入口 + BIND_NOW 即时绑定 → dlopen 失败，自动回退 Exo） |
| DV5 独立渲染器 | ✘ 设计上要求 API 26+（Java 侧守卫拦截，不崩溃） |
| Lab 实验台（已安装条目时） | ✔ **已修复**（原为 `Process#isAlive` API 26，且 `NoSuchMethodError` 是 `Error`，`LabAutoStart` 的 `catch (Exception)` 拦不住） |
| MPD 清洗 | ✔ **已修复**（原为 `Matcher` API 34 重载，在 API < 34 命中即抛 `NoSuchMethodError`；现改用 API 1 的 `StringBuffer` 重载） |

**直接回答“还能不能支持安卓 7”**：安装、启动、Exo/IJK 播放、猫源、Python 爬虫这些主干能力仍可在 Android 7 上运行；本轮已修复 3 个真实缺陷（Git 云盘/远程信任弹窗崩溃、Lab 实验台启动崩溃、MPD 清洗静默失效），其中前两个是 Android 7 上会直接崩溃的。但现状仍**不能算完整“最低支持安卓 7”**，剩余唯一硬缺口是：项目最核心的高级播放能力（**MPV 内核**，含 FEL/DV7、Vulkan 输出、蓝光菜单）在 Android 7.0–8.1（API 24–27）上无法加载（Vulkan 1.1 入口直到 Android 9/API 28 才由平台 `libvulkan.so` 导出），会静默回退到 Exo/IJK。

## 7. 修复方向（尚未实施）

1. ~~**修复 Git 云盘 / 远程信任崩溃**~~ ✅ **已完成**：`GitCloudDialog.input()` 与 `RemoteTrustDialog.input()` 的 `View.IMPORTANT_FOR_AUTOFILL_NO` 调用改为 `ViewCompat.setImportantForAutofill(view, ...)`。验证：`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` + `:app:compileMobileArm64_v8aDebugJavaWithJavac` BUILD SUCCESSFUL；`javap` 确认字节码改为 `invokestatic androidx/core/view/ViewCompat.setImportantForAutofill`；lint `NewApi` 中该 2 条 error 消失（254 → 252 errors）；`scripts/build_arm64_debug_install.sh --flavor leanback --abi arm64-v8a` 打包并覆盖安装到 dev1 设备，冷启动无 `NoSuchMethodError` 崩溃；**并实机点开「Git 云盘」弹窗成功渲染（标题「Silent1566 · 请选择仓库」与仓库列表均出现），整个会话 `FATAL EXCEPTION` 计数为 0**，证明修复路径真实可用。
2. **恢复 MPV 在 API 24–27 可用**（唯一能真正恢复“最低支持安卓 7”的路径）：重建 mpv 原生库，使 `libmpv.so` 不再直接引用 Vulkan 1.1 core 入口。可选做法：以 `VK_NO_PROTOTYPES` + `vkGetInstanceProcAddr` 运行时解析（Android 上扩展入口可通过 `vkGetInstanceProcAddr` 获得，即使 loader 未导出 ELF 符号），或让 mpv/libplacebo 在 Vulkan 1.0 loader 上完全走 `VK_KHR_get_physical_device_properties2` 的函数指针路径。注意：仅去掉 `-z now` 无效——`System.load()` 本身即 `RTLD_NOW`。此改动属上游播放器依赖范畴，需按 `AGENTS.md` §8 先做设计研究并获得批准。
3. ~~**LabRunner/LabTerminalActivity**~~ ✅ **已完成**：新增 `LabRunner.isAlive(Process)`（基于 `exitValue()`/`IllegalThreadStateException`，API 1，语义等价）并替换全部 6 处 `Process#isAlive()`。验证：JVM 探针确认语义等价；`javap` 确认编译后类中 `java/lang/Process;.isAlive` 调用为 0；`:app:testLeanbackArm64_v8aDebugUnitTest` 4191 个用例 0 失败（该轮 18:19 实测 XML：655 suites / 4191 tests / 0 failures / 2 skipped；原文把两 flavor 合计的 9197 写成该单任务用例数，且 mobile flavor 该轮未重跑，C62 已双 flavor 重跑，见 §10）；lint 对应 NewApi error 消失（252 → 246）；设备冷启动（会触发 `LabAutoStart`）进程存活且 `FATAL EXCEPTION`/`NoSuchMethodError` 计数为 0。
4. ~~**MpdSanitizer**~~ ✅ **已完成**：5 处 `appendReplacement` + 5 处 `appendTail` 全部改用 `StringBuffer`（API 1）重载（与 `LabRunner.expandNested()` 既有写法一致）。验证：`javap` 确认编译后类中 API 34 的 `StringBuilder` 重载为 0、`StringBuffer` 重载为 5+5；lint 对应 error 消失（246 → 236）；**设备实机重测两个触发路径（invalid-frameRate / invalid-adaptationSetId）均返回 `changed=true` 且不再抛 `NoSuchMethodError`**。
5. **UI 提示**（可选）：在 API < 28 时于内核选择处显式标注“MPV 需要 Android 9+”，避免用户误判为故障。

## 7.1 发布包（Release/apk/mobile-arm64_v8a.apk）54 个原生库全量复核

对发布 APK 内全部 54 个 `.so` 逐个比对 API 24/26/28 平台 stub 导出符号，仅 3 个命中：

| 库 | 绑定 | 结论 |
| --- | --- | --- |
| `assets/mpv-libs/arm64-v8a/libmpv.so` | `BIND_NOW` | 6 个 Vulkan 1.1 core 符号需 API ≥ 28 → Android 7/8 无法加载（已回退 Exo） |
| `lib/arm64-v8a/libexo_dovi_renderer.so` | `BIND_NOW` | 4 个符号需 API ≥ 26，但 Java 侧 `ExoDv5Native` 有 `SDK_INT < 26` 守卫，不会 dlopen |
| `assets/libmitv.so` | 否 | **既存的 ABI 异常，与 Android 版本无关**：该库是 32 位 ARM（EABI5，`file` 实测），却被打进了 **arm64-v8a** 包；其未定义符号是 ARM 编译器运行时（`__aeabi_ddiv`、`_Unwind_Complete` 等），由平台在 32 位进程运行时提供（不在任何 sysroot stub 里，属正常）。`BIND_NOW=false`，且它由三方 AAR（`forcetech-release.aar` 同时带 `jni/armeabi-v7a/libmitv.so`）提供，非本项目代码 |

其余 51 个库（`libc++_shared`、`libmv*`/`libmw*`/`libplayer`、`libexo_ass`、`libijk*`、`libsherpa-onnx-jni`、`libtensorflowlite_jni`、`libquickjs-android-wrapper`、`libavif_android`、`libdovi_android`、`libjpa`、`libxl_*`、`librtmp-jni`、`libpl_droidsonroids_gif`、`libmedia3ext`、`libavcodec/libavutil/libswscale/libswresample`、Chaquopy 全部 `lib*_python`/`libpython3.10`）无 API 26+ 的 GLOBAL 未定义符号。

## 8. 排查证据与可复现命令

- 依赖下限：`./gradlew :app:dependencies --configuration leanbackArm64_v8aDebugRuntimeClasspath`
- Lint：`./gradlew :app:lintLeanbackArm64_v8aDebug` → `app/build/reports/lint-results-leanbackArm64_v8aDebug.txt`
- 符号对照：
  `$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf --dyn-syms -W <so>` 对比
  `$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/{24,26,28}/*.so`
- AOSP 标注：`https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/vulkan/libvulkan/libvulkan.map.txt`（`vkGetPhysicalDeviceProperties2; # introduced=28` 等）
- 真机复现：API 28 设备上以 API 24 `libvulkan.so` stub 替换平台库后 dlopen 带 `BIND_NOW` 的探针库 → `dlopen failed: cannot locate symbol "vkGetPhysicalDeviceProperties2"`
- 运行时验证（设备 API 28）：`MpdSanitizer.sanitize()` → `NoSuchMethodError: appendReplacement(Ljava/lang/StringBuilder;...)`

## 9. 真实 Android 7 设备实证（192.168.50.3:5587，API 25）

前文所有结论均基于「符号分析 + API 28 设备模拟」。2026-10-12 接入真实 Android 7.1.2 设备后完成如下端到端验证，**结论全部成立**。

### 9.1 MPV 硬阻塞（决定性 A/B 对照）

把 APK 内真实的 v7a `libmpv.so` 推到设备，用**设备自带的 ARM linker** 直接 dlopen（`RTLD_NOW`）：

```text
# 真实 Android 7.1.2 (API 25)
$ LD_LIBRARY_PATH=/data/local/tmp/armtest ./armload libmpv.so
FAILED: dlopen failed: cannot locate symbol "vkGetPhysicalDeviceQueueFamilyProperties2"
        referenced by "/data/local/tmp/armtest/libmpv.so"...

# 同一条命令、同一个 libmpv.so，在 Android 9 (API 28) 上
OK: /data/local/tmp/armtest/libmpv.so
```

同时直接读取**设备自身的** `libvulkan.so`（非 NDK stub）：

| 文件 | 架构 | 导出符号总数 | 上述 6 个 Vulkan 1.1 符号 |
| --- | --- | --- | --- |
| `/system/lib/arm/libvulkan.so` | ARM 32-bit (Android 25) | 193 | **全部 0** |
| `/system/lib/libvulkan.so` | x86 32-bit (Android 25) | 191 | **全部 0** |

控制实验（排除误判）：
- 同探针在 API 28 设备上 → `RESULT=OK`（证明探针本身有效）
- 不带 Vulkan 依赖的探针在 API 25 上 → `RESULT=OK`（证明不是环境普遍性问题）
- 构造的 x86-32 探针库（`DT_NEEDED libvulkan.so` + `BIND_NOW` + `UND vkGetPhysicalDeviceProperties2`）在 API 25 上 → `cannot locate symbol "vkGetPhysicalDeviceProperties2"`

### 9.2 已修复缺陷在真机上的 A/B 实证

在 API 25 设备上直接对比修复前后调用的 API 可用性：

```text
SDK_INT=25
OLD  View#setImportantForAutofill MISSING -> java.lang.NoSuchMethodException: setImportantForAutofill [int]
NEW  ViewCompat.setImportantForAutofill exists -> OK
NEW  ViewCompat call returned without touching the API-26 method -> SAFE on API 25
```

即：**修复前在安卓 7 上必然崩溃，修复后安全**（`ViewCompat` 的 `SDK_INT < 26` 提前返回在真机上得到验证）。

### 9.3 真机安装与运行

- 构建/安装：`bash scripts/build_arm64_debug_install.sh --flavor leanback --abi armeabi-v7a --serial 192.168.50.3:5587` → BUILD SUCCESSFUL，覆盖安装成功（160M APK）
- 冷启动（含 `LabAutoStart` +3s 路径）：进程存活，`FATAL EXCEPTION` / `NoSuchMethodError` 计数 **0**
- 打开「增强功能」设置页：正常渲染，无崩溃

### 9.4 一处方法论提醒

在 x86 + houdini 翻译设备上，用 `app_process`（x86 进程）加载 ARM 库时会看到 `undefined symbol: _ZNSt3__115basic_stringbuf...` 这类 **houdini 伪影**（`St3__1` 是设备 x86 系统库如 `libart.so` 的命名空间，而 NDK 产物用 `St6__ndk1`）。改用设备自带的 ARM linker（`armload` 探针）后即得到真实的 `vkGetPhysicalDeviceQueueFamilyProperties2` 报错。排查翻译设备上的原生库问题时应优先使用**目标架构的 linker**，不要被翻译层报错误导。

## 10. C62 复评补充（2026-10-12）

本轮（记录见 `docs/C62-beta-merge-review-dev1-20261012.md`）合并远端 `beta` 后复评本文改动，做三件事：

1. **补齐回归锁**（本文三处修复原先只有 JVM/实机探针证据，未提交任何用例；本仓既有约定是源码守卫用例）：
   - `app/src/test/java/com/fongmi/android/tv/lab/LabRunnerProcessCompatTest`（2 例）：`LabRunner.isAlive(Process)` 的存活/已终止/`null` 语义，以及 `LabRunner.java`/`LabTerminalActivity.java` 不得再出现 `Process#isAlive()`、必须走 `exitValue()` 探针。
   - `app/src/test/java/com/fongmi/android/tv/player/extractor/MpdSanitizerMatcherMethodContractTest`（2 例）：`MpdSanitizer` 的 5 次 `appendReplacement` + 5 次 `appendTail` 目标变量必须是 `StringBuffer`（API 1 重载），并验证该重载仍能正确改写文本。
   - `app/src/test/java/com/fongmi/android/tv/compat/Android7ApiCompatContractTest`（2 例）：两个弹窗只能经 `ViewCompat.setImportantForAutofill` 调用（不得直接调 `View#setImportantForAutofill`）；并全仓扫描 `app/src/main/java`（33 处 matcher append）确认没有任何 `appendReplacement/appendTail` 目标声明为 `StringBuilder`。
   - **变异验证**（改回缺陷形态必须转红，实测）：`StringBuffer output → StringBuilder output`、`ViewCompat.setImportantForAutofill → edit.setImportantForAutofill`、`LabRunner.isAlive → process.isAlive`、`isAlive(null) → true` 四处变异同时存在时，上述 3 个用例类共 6 例中 **4 例转红**（`everyMatcherAppendUsesTheApi1StringBufferOverload`、`autofillCallsGoThroughTheApiIsolatedCompatLayer`、`labSourcesProbeThroughExitValueInsteadOfTheApi26Call`、`probeTreatsOnlyATerminatedProcessAsDead`）；恢复后全绿。
2. **订正 §5 的两处不准确表述**：`NewApi` 计数（51 处 / 25 处 API 26，而非 52 / 23），以及 `MpdSanitizer` 的影响机制（上抛 `NoSuchMethodError`，而非「被 `catch (Exception)` 捕获后静默返回未处理 URL」）。
3. **订正 §7 的用例数口径**：`9197` 是两 flavor 合计（leanback 4191 + mobile 5006），不是 `:app:testLeanbackArm64_v8aDebugUnitTest` 单任务用例数；且该轮 mobile flavor 未重跑（其 `test-results` 目录停留在改动前的 13:57 产物）。

C62 合并 `origin/beta`（`af9a18fa4`）后双 flavor 全量 JVM 套件实测：leanback **4203**（0 failures / 0 errors / 2 skipped）、mobile **5018**（0 failures / 0 errors / 2 skipped），相对 C61 基线（4191 / 5006）各 **+12** = 本文三处修复的 6 个锁定用例 + beta 侧 PR#437 带入的 6 个用例（`PlayerSettingsContrastTest` 5 + `PlayerDisplaySettingSyncTest` +1）。

> 说明：本文的 Android 7 实机结论（§9）未被本轮改动影响——C62 只新增测试与文档，未改动任何生产代码。
