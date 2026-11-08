# CRASH-LOG-01 崩溃界面局域网日志下载

- Task id: `crash-log-address`
- Lane: `standard`
- Started: 2026-10-21 20:15 +08:00 (Asia/Shanghai)
- Branch / base: `dev3` @ `3964eeae7ccf4f10f5a2e3696a2909c7a92b3952`

## Recovery anchor

**Objective.** 崩溃界面不再以异常堆栈为主要内容，改为显示可复制的局域网地址；同一局域网的手机/电脑打开该地址即可查看并下载本次崩溃日志。即使用户从未开启「增强功能 → 调试日志」，崩溃会话也自动开启并提供下载，且不改变用户的调试日志偏好。

**Acceptance.**
1. 崩溃界面显示日志地址 + 「复制地址」「在浏览器打开」；无局域网或服务启动失败时给出明确提示且不崩溃。
2. `/debug/logs` 与 `/debug/logs.txt` 至少包含：崩溃栈、版本/构建、设备/进程/线程、同 UID 系统 logcat 尾部（或 unavailable 说明）；用户原本已开启调试日志时还包含上一进程恢复的诊断日志。
3. 重启后调试日志偏好保持原值。
4. 崩溃界面销毁（重启）后日志服务关闭。
5. 既有崩溃界面行为与测试不回归。

**Single next action.** 已完成实现与验证；提交并打 recovery tag（见 §7）。

## 1. 现状（已查证）

| 事实 | 位置 |
| --- | --- |
| 崩溃界面只有重启/静默重启/错误信息；错误信息展示 `CustomActivityOnCrash` 堆栈 | `app/src/main/java/com/fongmi/android/tv/ui/activity/CrashActivity.java:37-58` |
| 崩溃界面运行在独立进程 `:error_activity` | `app/src/main/AndroidManifest.xml:87-91` |
| 崩溃时原进程内存态不可用（CAOC 启动新进程错误界面后杀掉原进程） | 依赖 `cat.ereza:customactivityoncrash:2.4.0` 官方 README |
| 局域网日志服务与页面已存在（NanoHTTPD，9978..9998） | `server/Nano.java`、`server/Server.java:44-80`、`server/process/DebugLogs.java` |
| 增强功能日志入口已展示局域网/本机地址并可复制 | `ui/dialog/DebugLogDialog.java:31-36,77` |
| 调试日志关闭时 `DebugLogStore.add/event` 直接丢弃 | `catvod/.../DebugLogStore.java:113-140` |
| 日志开启时已有链式崩溃处理器，把崩溃写入 incident/journal | `DebugLogStore.java:67-88` |
| 崩溃进程 `App.onCreate` 会恢复上一进程诊断日志 | `App.java:123-128` |
| 关键失败日志已无条件写 logcat（如 `TV-exo-error`） | `player/engine/ExoPlayerEngine.java:163-179` |
| 测试任务：`:app:testMobileArm64_v8aDebugUnitTest`、`:app:testLeanbackArm64_v8aDebugUnitTest` | `.github/workflows/android-release.yml:99-120` |

约束：`app/src/testMobile/.../CrashActivityDetailsTest.java` 断言错误信息对话框的版本号顺序与重启按钮接线；`app/src/test/.../DebugLogsPageTest.java` 断言日志页标记。改动需保留这些断言。

## 2. 最佳实践研究（决策级）

| 来源 | 版本/日期 | 等级 | 支撑结论 | 本项目适用性 | 影响 |
| --- | --- | --- | --- | --- | --- |
| [Ereza/CustomActivityOnCrash README](https://github.com/Ereza/CustomActivityOnCrash) | 2.4.0 / 2026-10-21 访问 | 一手（依赖官方） | 未处理异常处理器捕获堆栈 → 以 extra 启动错误 Activity（新进程）→ 杀掉当前进程；`restartApplication` 也会杀掉错误进程 | 直接适用：崩溃现场必须落盘交接 | 决定采用文件交接 + 崩溃界面进程内重建会话 |
| CAOC 错误 Activity 必须声明 `process=":error_activity"` | 同上 | 一手 | 错误界面与崩溃进程隔离 | 与现有 manifest 一致 | 不能用内存/静态变量传递崩溃上下文 |
| [Android logcat 同 UID 限制](https://stackoverflow.com/questions/16222766/how-can-i-get-logcat-on-my-device-to-show-logs-from-all-processes)、[Jelly Bean 起仅本 UID](https://android.stackexchange.com/questions/28857/how-can-i-access-android-log-files-on-my-nexus-7-without-root-access) | 访问 2026-10-21 | 社区证据（与平台行为一致） | Android 4.1+ 普通应用只能读取本 UID 日志；`READ_LOGS` 需系统签名 | 崩溃进程已死但同 UID 的崩溃界面进程仍可读取其 logcat 尾部 | 关闭调试日志时的崩溃前证据来源；不申请 `READ_LOGS` |
| 本项目 `DiagnosticText` | 当前 HEAD | 一手（仓库代码） | 统一脱敏 URI/路径/IP/token，单条上限 12000 字符 | 直接复用 | LAN 暴露内容不新增敏感原文 |
| 本项目 `DiagnosticLogBuffer` / `RollingDiagnosticFile` | 当前 HEAD | 一手（仓库代码） | 一个磁盘 owner；`Persistence` 接口支持内存实现 | 复用接口即可得到页面/下载/ZIP | 临时会话使用内存实现，避免跨进程争用诊断文件 |
| [AndroidX App Startup](https://developer.android.com/topic/libraries/app-startup) | 访问 2026-10-21 | 官方文档 | Initializer 由 ContentProvider 在启动早期执行（早于 `Application.onCreate`） | 崩溃处理器可在 `Startup` 中与 CAOC 同处安装 | 覆盖早期崩溃 |

未使用的证据类：无相关论文/基准；该需求不涉及性能基准。

## 3. 方案比较

- **A. 不改**：崩溃界面仍只显示堆栈；未开启日志的用户无法下载日志。不满足需求。
- **B. 只加地址**：复用现有服务，但日志关闭时页面/下载只有「调试日志未开启」，下载无实际内容。不满足需求。
- **C. 崩溃现场落盘 + 临时（内存）崩溃会话 + 地址展示（选定）**：
  - 未处理异常处理器**无条件**落盘崩溃报告（不依赖日志开关）；
  - 崩溃界面进程读取报告，补充同 UID 系统 logcat 尾部，用内存存储的临时诊断会话注入现有页面/下载；
  - 崩溃界面启动 `Server`，展示/复制局域网地址；销毁时关闭；
  - 临时会话不写偏好，重启恢复用户原状态。

取舍：相比 B，多一次崩溃路径文件写入（≤96KB、有界、try/catch），换来关闭日志时的可用证据；相比“常驻开启调试日志”，不引入持续采集的性能/隐私回归，但关闭日志时崩溃前只有系统 logcat（明确标注覆盖范围）。

## 4. 实现顺序

1. `CrashLogText`（纯逻辑：报告格式/脱敏/上限/新鲜度/pid 过滤/会话拼装/URL 换行）+ 单测。
2. `CrashReport`（安装链式处理器、原子落盘、轮转 3 份、读取最新且 ≤5 分钟）。
3. `InMemoryDiagnosticFile`（`DiagnosticLogBuffer.Persistence` 内存实现）+ `DebugLogStore.beginCrashSession/appendCrashContext/isCrashSession/s...Temporary`（不写偏好）。
4. `CrashLogSession`（读取报告 + `logcat -d` 尾部 + 注入会话）。
5. `CrashActivity` + 两份布局 + 三份 strings（地址、复制、打开、失败提示、保屏、返回键不关闭、销毁停服）。
6. `DebugLogs` 页面崩溃会话状态（临时会话隐藏清空/采集开关并说明）。
7. `Startup` 安装 `CrashReport`；README 说明；测试与文档。

## 5. 文件格式

`filesDir/crash/webhtv-crash-<epochMs>.txt`，UTF-8，≤96KB，`webhtv-crash-report v1`：

```
# webhtv-crash-report v1
appVersion=<AppVersion.fullName()>
wallTime=<yyyy-MM-dd'T'HH:mm:ss.SSSZ>
elapsedMs=<SystemClock.elapsedRealtime()>
process=<进程名>
pid=<pid>
uid=<uid>
thread=<线程名>
debugLogEnabled=<true|false>
device=<厂商 型号>
system=<Android 版本/sdk>
javaClass=<异常类>
message=<脱敏消息>
--- stack ---
<逐行脱敏的堆栈，超限时 [truncated]>
```

崩溃会话正文（注入日志页，tag `crash`）包含 `# crash-session v1`、覆盖范围行、报告、`# system-logcat` 段或不可用说明。

## 6. 验证

### 6.1 自动化（2026-10-21 20:2x–21:0x +08:00）

```bash
bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest \
  --tests 'com.fongmi.android.tv.utils.CrashLogTextTest' \
  --tests 'com.fongmi.android.tv.utils.CrashLogWiringTest' \
  --tests 'com.fongmi.android.tv.ui.activity.CrashActivityDetailsTest' \
  --tests 'com.fongmi.android.tv.server.process.DebugLogsPageTest' --no-daemon
bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests 'com.fongmi.android.tv.utils.CrashLogWiringTest' \
  --tests 'com.fongmi.android.tv.utils.CrashLogTextTest' \
  --tests 'com.fongmi.android.tv.server.process.DebugLogsPageTest' --no-daemon
```

结果：两次 `BUILD SUCCESSFUL`；`CrashLogTextTest` 10/10、`CrashLogWiringTest` 7/7、`CrashActivityDetailsTest` 7/7、`DebugLogsPageTest` 2/2，无失败/跳过。另执行 `:app:assembleMobileArm64_v8aDebug` 成功（用于真机验证）。

### 6.2 设备实测（emulator-5554：Android 9 / sdk 28，arm64 转译，安装 `mobileArm64_v8aDebug`）

- `adb shell am crash <主进程 pid>` 触发 Java 未捕获异常 → `:error_activity` 启动 `CrashActivity`；界面显示「日志下载地址 / http://172.16.1.4:9978/debug/logs」、复制地址、在浏览器打开，原有重启/静默重启/错误信息仍在。
- 设备原本 `debug_log=true`（历史遗留）：`/debug/logs.txt` 为 14.9 MB，含 `# crash-session v1`、`# webhtv-crash-report v1`、`# system logcat`，验证日志开启时复用恢复会话并可注入崩溃现场。
- 将 `debug_log` 改为 `false` 后重启并再次崩溃：页面标题为「崩溃会话 · 自动开启」，出现临时会话横幅，`清空` 与采集开关被隐藏；`/debug/logs.txt` 为 136 KB，含
  `# crash-session v1`、`report=fresh`、`logcat=own-uid-tail`、`debugLog=off`、`# webhtv-crash-report v1`（`javaClass=android.app.RemoteServiceException`、`message=shell-induced crash`、堆栈）、`# system logcat`（`FATAL EXCEPTION` 等）。
- 崩溃后与重启后 `debug_log` 仍为 `false`，证明临时会话未写偏好；点「重新启动」后日志服务关闭（对转发端口的请求失败）。
-「在浏览器打开」成功拉起系统浏览器（`com.android.browser/.BrowserActivity`）。
- 验证结束后已把设备 `debug_log` 恢复为 `true`；`emulator-5554` 上保留本次 debug 构建（覆盖安装，数据保留）。
- 注意：`am crash <package>` 可能命中 WebView 沙箱进程造成 native abort（无崩溃界面），验证 Java 路径需用 `am crash <主进程 pid>`。

### 6.3 未覆盖

- native 崩溃/ANR 不经过 CAOC，不在本功能范围；关闭调试日志时崩溃前只有同 UID 系统 logcat（已在下载内容中显式声明覆盖范围）。
- 真机品牌 ROM 若限制应用读取自身 logcat，下载仍包含崩溃报告并标注 `logcat=unavailable`。

## 7. 回滚

单一提交回滚即可；无偏好/数据库迁移。`filesDir/crash/` 最多 3 个 ≤96KB 文件，随应用数据清除。

## 8. 评审（2026-10-21 23:1x–23:5x +08:00，C95 合并 beta 后复评）

首轮评审 5 项，修复后第二轮复评通过；详细证据见 `docs/C95-beta-merge-review-dev3-20261021.md` §3–§5。

- **高（已修）**：两份 `activity_crash.xml` 原为不可滚动的居中 `LinearLayout`，新增地址与两个按钮后，横屏/小屏会把「重新启动/静默重启/错误信息」裁出屏幕，而返回键被屏蔽，用户可能无法重启。改为 `ScrollView(fillViewport)`；`wm size 800x480; wm density 320` 下滚动可到达全部三个原按钮。
- **中（已修）**：调试日志页轮询 1.5 秒后把「崩溃会话 · 自动开启」改写为「记录中」。轮询脚本注入 `crashSession` 标记，只刷新字节数。
- **低（已修）**：`logcat` 尾巴为空时页面仍声明 `logcat=own-uid-tail`；改为 `logcat != null && !logcat.isEmpty()`，与正文“系统日志不可用”一致。
- **低（已修）**：`CrashReport.readRecent` 遇第一份过期报告即停止，墙钟回拨时可能漏读较新报告；改为遍历最多 3 份并取第一份新鲜报告。
- **提示（已固化）**：崩溃写入器必须位于 CAOC 处理器外层（依赖 `CaocInitProvider` 的 `initOrder="101"` 先于 androidx.startup 初始化）；已在 `CrashReport.install()` 注释中记录该顺序约束。
- 验证：mobile 26 例 / leanback 19 例定向单测全绿，`assembleMobileArm64_v8aDebug` 成功；模拟器复测临时会话标题、下载内容与关闭日志时 `debug_log` 保持 `false`。
