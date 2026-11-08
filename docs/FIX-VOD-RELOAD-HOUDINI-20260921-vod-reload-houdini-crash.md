# 接口重载在 x86 模拟器转译环境闪退

## Recovery anchor

- 目标：修复“重新拉取点播接口后 App 立即闪退”的问题，且不降低真机（原生 ARM）行为、性能与解耦能力。
- 允许路径：`app/src/main/java/com/fongmi/android/tv/api/loader/**`、`app/src/test/java/com/fongmi/android/tv/api/loader/**`、`docs/**`。
- 验收标准：同一接口连续重载 3 次不崩溃；ARM 真机行为与现状一致；单测与编译通过。
- 当前状态：已修复并完成 3 轮设备验证，待提交。
- 下一步唯一动作：提交本任务并生成 recovery tag。

## 1. 现象与复现

在 `192.168.50.3:5559`（vivo V1923A / 真实机型伪装、Android 9、`ro.product.cpu.abi=x86_64`，但 `ro.dalvik.vm.native.bridge=libnb.so`）上：

1. 设置页点击点播接口 URL，弹出“新增点播”对话框；
2. 点击“确定”触发 `VodConfig.load(config, callback)`；
3. 进程无 Java 异常地整体消失，返回桌面（“闪退”）。

## 2. 根因证据

### 2.1 设备 tombstone

`adb -s 192.168.50.3:5559 shell dumpsys dropbox --print SYSTEM_TOMBSTONE`，2026-09-21 11:34:15 记录：

```text
pid: 9141, tid: 9155, name: HeapTaskDaemon  >>> com.silent.android.webhtv <<<
signal 11 (SIGSEGV), code 2 (SEGV_ACCERR), fault addr 0x7fff727cf2f8
backtrace:
    #00 pc 00000000003558c0  /system/lib64/libhoudini.so
    #01 pc 000000000035b6b0  /system/lib64/libhoudini.so
    #02 pc 000000000035f42d  /system/lib64/libhoudini.so
stack:
    00007fff5b16e308  00007fff7246eac8  /system/lib64/libart.so (_ZN3art9Libraries21UnloadNativeLibrariesEv+1384)
```

崩溃线程是 GC 的 `HeapTaskDaemon`，栈顶全部位于 Intel Houdini（x86 上运行 ARM 代码的转译层），调用来源是 ART 卸载本地库。设备侧同一问题的历史 tombstone 共 9 条，形态完全一致（首次记录 2026-09-20 17:55:50）。

### 2.2 与接口重载的因果链

抓到的 logcat 显示重载时同一时刻发生：`cache/jar/<md5>.jar` 被删除并重新下载、`csp-warmup`/`jar-loader` 完成 spider 初始化，随后进程在 `HeapTaskDaemon` 崩溃。原因是：

- 接口自带的 CSP jar 会被 `JarLoader` 用 `DexClassLoader` 加载（`CspDexClassLoader`）；
- 重载时 `VodConfig.clear()` → `BaseLoader.clear()` → `JarLoader.clear()` 清空 `loaders`，被替换的 `DexClassLoader` 失去强引用；
- GC 回收该 loader 时 ART 调用 `UnloadNativeLibraries()`，在 Houdini 转译环境下回调转译器并踩到已失效映射，直接 SIGSEGV。

即：崩溃不由任何 Java 异常引起，因此 `CrashActivity`、`FATAL EXCEPTION` 与 `try/catch` 都捕获不到。

## 3. 决策

三种候选：

| 方案 | 说明 | 结论 |
| --- | --- | --- |
| 无变化 | 只报告问题 | 不解决用户可见闪退 |
| 停止重载接口 | 重载后重启进程 | 破坏用户体验，代价过大 |
| 仅在转译运行环境保留旧 loader 强引用 | 让 ART 不进入不安全的卸载回调；真机路径完全不变 | **采用** |

实现要点（`NativeBridgeGuard` + `JarLoader.retireLoaders()`）：

- 判据一：`ro.dalvik.vm.native.bridge` 非空且非 `0`；
- 判据二（后备）：主 ABI 与受支持 ABI 同时含 x86 族与 ARM 族；
- 命中时，`clear()` 只把这些 loader 移入进程级保留列表，不做任何状态保留或行为改变；
- 未命中（真机、模拟器直跑原生 ABI）时行为与之前完全一致；
- 保留列表仅存在于转译环境，属模拟器专用代价，真机零额外常驻内存。

## 4. 验证

- 单测：`NativeBridgeGuardTest`（native bridge 属性、外来 ABI 族、ARM 主 ABI 三种判据）+ 既有 `JarLoaderTest`/`LoaderQueueTest` 全部通过。
- 编译：`assembleMobileArm64_v8aDebug` 通过。
- 设备：`192.168.50.3:5559` 覆盖安装后，同一接口**连续重载 3 轮全部成功**，进程保持在前台，`FATAL EXCEPTION=0`；日志出现 `jar-loader | retained translated loaders count=N`，证明判据在设备上生效。
- 回滚：仅回滚本提交即可；无数据、无格式、无 native 二进制变更。

## 5. 遗留

- 真机（原生 ARM）路径未受影响，但本机无 ARM 真机可复验，属既有环境限制；
- 转译环境下重载会累积保留的 loader，属该环境专用取舍，已在代码注释与本文记录。
