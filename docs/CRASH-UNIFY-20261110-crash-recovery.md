# 崩溃入口统一与异常死亡补救 Implementation Plan

**Goal:** 可捕获应用致命异常进入可操作的独立恢复页；确认的主进程异常死亡在下一次用户启动、配置加载前进入同页，不误报正常退出。
**Architecture:** 保留 CAOC 依赖但不完全依赖其默认路由和启动冲突抑制；错误进程最小初始化、恢复页不经过业务 BaseActivity。独立恢复状态记录 Java 事件和 Android 11+ 主进程历史死亡，持久一次消费；主进程入口在配置加载前检查。
**Tech Stack:** Android minSdk24 / Java21 / CAOC2.4.0 / JUnit4 / 两端 flavor。
**Spec:** 本文件“确认记录”；唯一任务记录，不另建 plans/spec 文件。

## 确认记录 / Global Constraints
- 用户 Q1/Q3 确认：可捕获异常即时恢复；确认的主进程异常死亡下一次打开补救；正常退出和用户强停不提示。
- 用户 Q2/Q3 确认：保留复制日志地址，不新增复制正文；保留普通重启、静默重启、错误信息。
- 静默重启仍为仅一次跳过远程点播接口加载；不改播放器/native二进制、依赖、系统设置，不推送。
- 系统强杀、Native信号、无历史证据、恢复页面自身失败及处理器安装前失败不承诺现场拦截；图片没有复现证据不得声称已定位具体运行时故障。

## 工作区、时间与回滚
- 分支 dev1；基线 930d1b8097d00330e284b61877f88264b1eda27b；初始工作区干净，保护初始脏路径 0 个。
- Task guard CRASH-UNIFY-20261110 / standard，20:28:58 +08:00 已启动；范围以 guard scope 为准。
- 20:28 起后续预计35–50分钟，预计21:04–21:19 +08:00结束。实现20–30分钟，验收/收尾15–20分钟；构建或子任务等待造成显著延迟时停止可选工作并更新估计。
- 回滚：交付本地原子提交及 recovery/CRASH-UNIFY-20261110/* 注释标签；必要时由该提交整体回退。未验证前不提交。

## 根因与设计证据（2026-11-10读取）
|证据|级别/修订|支持结论与影响|
|---|---|---|
|Startup.java:33-36 / Manifest:88-91|项目源码，基线HEAD|已经配置自定义CrashActivity且在:error_activity；不是简单忘了配置错误页。|
|两端 activity_crash.xml 的 customactivityoncrash_error_image|项目源码，基线HEAD|自定义页与CAOC默认页共用翻倒瓢虫，不能只凭图标认定默认页。|
|CAOC2.4.0缓存AAR，javap -c -p -l；lambda$install$0源码行117-136、isStackTraceLikelyConflictive行478-498|实际使用依赖字节码，一手|少于3000ms连续崩溃、handleBindApplication栈及错误进程被拒绝弹页。默认页也有文字/按钮，没有已证实的纯图模式。|
|App.attachBaseContext:123 / onCreate:131,147-153|项目源码，基线HEAD|只隔离:playback_recovery，没有隔离:error_activity，错误页会再次初始化业务/插件/CPython等。|
|CrashActivity:164-173|项目源码，基线HEAD|裸Intent缺配置会再次抛异常，历史补救需要安全重启与明确详情上下文。|
|PreviousProcessExitLogger:28,49-54 / App:138-142|项目源码，基线HEAD|历史退出受调试开关且只取全包最近1条，无主进程筛选，不能直接当恢复闸门。|
|SDK37.0 sources/android/app/ActivityManager.java:4787-4817,4821-4844 / ApplicationExitInfo.java:79-115,135-149,889-895,944-949,986-1010；api-versions.xml|本机官方SDK公开源码，一手API30/31|取最新主进程后分类，只接受CRASH、CRASH_NATIVE、ANR（初始化失败需核对作为可确认启动失败是否纳入）；SIGNALED/SIGKILL可能只是低内存，强停/正常退出不提示；trace缺失或混旧，native protobuf不按UTF8输出；state摘要最多128字节可关联runId。|
|两端Home/BaseActivity初始化链|项目源码，基线HEAD|mobile super.onCreate过程中已同步initConfig；恢复闸门必须在配置加载处阻断，而非之后跳页面。|
|CrashReport:77-118 / CrashLogSession / CrashRestartMode|项目源码，基线HEAD|报告独立日志开关落盘；5分钟elapsed新鲜窗口不能当隔夜恢复状态；消费状态与静默标记和debug游标必须分离。|

在线 developer.android.com 读取因 modsearch代理/keyless失败；CAOC GitHub猜测路径404。未以搜索摘要替代证据，采用实际缓存SDK与依赖。没有上游移植候选；额外论文/性能基准不适用于这一恢复路由修复，不继续网络扩展；不声称已完成全面外部最佳实践调研。

## 方案比较与决定
1. 不修改：已有页但处理器抑制、错误进程业务初始化、历史日志开关缺口仍在，不满足用户要求。
2. 仅提前CAOC配置/原库默认处理：成本小，但保留handleBindApplication与连续崩溃抑制，不能覆盖已确认Java启动异常需求。
3. 窄适配（采用）：外层报告处理器显式启动独立恢复页；安装前后顺序确保CAOC不覆盖报告处理器；错误进程不递归处理。应用实际用户配置入口统一闸门，系统主进程退出白名单与Java持久确认状态一次消费。不做监护服务、signal hook或native依赖。

Ruling: VIEW/SEND等能进入Home的用户入口与桌面启动同受配置闸门约束——避免外部Intent绕过致命退出补救；不拦截正常运行的非致命播放器/WebView错误，不扩大业务错误为进程崩溃。
Ruling: 本地原子状态为跨进程消费来源，不复用SharedPreferences缓存/debug游标/静默标记——避免错误页确认或重启循环；错误的代价是需要修复事件关联而非重置用户配置。

## 文件分工与接口
### Task A — 处理器、错误进程隔离和独立恢复页（实现运行0/3）
**Goal / Boundary:** 仅 App.java、Startup.java、CrashReport.java、CrashActivity.java、main/AndroidManifest.xml、两端activity_crash.xml、已有CrashLogWiringTest.java、CrashActivityDetailsTest.java，及新增CrashHandlerTest.java / CrashActivityIsolationTest.java。若无必要不改布局。A不编辑Home、CrashLogSession、历史恢复类和本记录。
**Constraints:** 页面脱离两端BaseActivity副作用，保留兼容主题/遥控焦点/全部动作；日志服务失败不影响重启/详情。必须安全处理缺CAOC配置的Intent；主/背景线程致命异常与provider/Application已安装后启动崩溃显式路由；避免错误进程循环，写报告失败仍尝试页与确定终止，不吞异常留半死进程。
**Consumes:** B提供以下public static接口：PreviousProcessCrashRecovery.initialize(Context), checkBeforeConfig(Activity,Runnable), decorateJavaCrashIntent(Context,Intent), acknowledgeShown(Context,Intent), getRecoveryDetails(Intent)（历史详情或null）。A在主进程最早安全点initialize，在已构造Java错误Intent上decorate；恢复页显示后acknowledge，详情优先非null getRecoveryDetails。B方法必须内部容错；A不要创建stub或修改B文件。
**Produces:** CrashActivity对显式Intent可启动且不依赖CAOC配置才可重启；CAOC公开extra仍可使用，必须从实际依赖验证符号。CrashReport作为唯一Java事实落盘，不改变5分钟日志新鲜度契约。
**Limits:** 运行最多3次，按简报累计；编辑最多11个文件，累计；命令最多40条，累计；测试次数无限，仅相关编辑或不确定失败才重跑；联网0次，累计；等待年龄上限3协调回合，累计。禁止提交/guard操作/子代理/独立Gradle矩阵；构建由协调者统一一次。
**Acceptance / Required Evidence:** 先加失败测试并报告RED，再最小实现与自检；指出安装顺序与错误进程隔离、裸Intent普通/静默重启和详情安全、保持服务器生命周期。报告具体文件、测试命令/退出码与限制，不能只说已完成。
- [ ] 测试现有初始化隔离和缺配置失败，运行RED。
- [ ] 最小修改处理器和独立页，实际依赖证明extra与重启契约。
- [ ] 运行自检；报告后等待父代理独立审查，不提交。

### Task B — 主进程死亡恢复和启动配置闸门（实现运行0/3）
**Goal / Boundary:** 仅新增utils/PreviousProcessCrashRecovery.java、CrashRecoveryState.java，修改两端HomeActivity.java、CrashLogSession.java；新增test/utils/PreviousProcessCrashRecoveryTest.java、CrashRecoveryStateTest.java、CrashRecoveryGateTest.java。B不编辑A文件、本记录和既有A测试。
**Constraints:** 最新主进程退出（不能继续找更老崩溃）分类；不依赖调试开关；正常/强停/低内存/不确证信号退出不提示。持久消费在启动恢复页之前，失败安全放行；当次错误页已展示不在下次启动重放，未来新异常仍可恢复。旧Android只接受明确Java持久事实，不能用未正常退出启发式断言native崩溃。原生栈缺失/二进制明确注明，不错配readRecent旧报告。主线程不做无界I/O，异步闸门完成之前不能继续配置加载，保留单次静默消费。
**Produces:** 前述5个接口，用原始Intent小字段（含恢复详情和事件标识）与AtomicFile或等价跨进程锁状态；A可不懂状态内部。initialize可早期调用且幂等；decorate关联当前Java事件，acknowledge只确认当前Intent对应事件，getRecoveryDetails无字段返回null；checkBeforeConfig决定一次并且旋转/singleTop不重复恢复。CrashLogSession把正确历史摘要送入自动临时崩溃日志。
**Limits:** 运行最多3次，按简报累计；编辑最多8个文件，累计；命令最多40条，累计；测试次数无限，仅相关编辑或不确定失败才重跑；联网0次，累计；等待年龄上限3协调回合，累计。禁止提交/guard操作/子代理/独立Gradle矩阵；构建协调者统一一次。
**Acceptance / Required Evidence:** 先行为测试RED再实现；覆盖三类确认死亡、非主进程过滤、最近正常不回溯、重复启动/旋转/重启不循环、失败安全放行、API24/29降级、静默标记互不混用。文件/接口清单、RED与自检命令结果、未验证设备限制必须报。
- [ ] 增加退出分类/消费/闸门行为失败测试并运行RED。
- [ ] 实现最小状态、API30查询与配置前闸门，接历史详情日志归属。
- [ ] 运行自检；报告后等待父代理独立审查，不提交。

## 预审表
|项目|生产/消费对照|结论|
|---|---|---|
|A内部|独立页保留日志/详情/重启，测试缺配置与隔离|不新增业务初始化，不依赖BaseActivity。|
|B内部|先闸门再配置、状态与静默分离，行为测试覆盖|白名单与最新主进程过滤，正常启动必须不变。|
|A/B共享接口|B提供5接口，A仅调用；文件所有权不交叉|并行可编辑；最终统一构建在两项完成后，不运行互斥Gradle。|

## 协调者验收与闭环
- [ ] 读取关键diff逐条验收Spec和Quality；缺陷以证据回派，每简报累计最多3轮，不能记录后直接带缺陷交付。
- [ ] 显式JDK21、G:\GradleCache、SDK37.0。运行两个flavor定向unitTest和Java编译，不构建native矩阵；完整日志保存且不为过滤重复运行。
- [ ] 可用模拟器5554/5556仅选代表恢复页验证，先检查设备/app归属，避免误击WebView pid；日志关闭场景/按钮/一次恢复必须有证据，设备无法执行则准确声明未测。
- [ ] 更新本记录验证、风险处置、唯一下一步；task_guard finish记录证据并原子提交+注释recovery标签，不推送。

## 执行与审查记录
- 2026-11-11 00:06 +08:00：原预计结束已失效，原因B原会话两次提前失败且无报告；send_message第一次续接成功仍失败。父代理核实B无残留后新实例完成第3/3轮，停止可选研究，继续最小实现与验证。
- A首轮编辑9个限定文件，独立JDK21/JUnit 24项自检；父代理发现重入提前terminate及主进程按包名判断两缺陷，回派第2/3轮，25项自检返回。父代理读修复符号，两项已修；Android编译/运行仍需验证。
- B第3/3轮完成8个指定文件、8项自检，但父代理读代码审查发现：60秒窗口误吞另一真实死亡、仅时间seen不支持时钟回拨、pending未清可再次展示、超时后仍后台消费、写盘未fsync。累计预算已用完且未通过审查，R8兜底仅适用B，父代理修B状态/闸门/测试，不编辑A模块。
- 审查C a8289db9-40e5-45cb-ab44-f701e3ac7e04运行1/3，核对CAOC辅助/错误进程日志服务。
- 设备只读检查：emulator-5554/5556均API28，均装com.silent.android.webhtv；5554前台桌面，5556前台另一包com.fongmi.android.tv。不能用它们宣称API30系统历史死亡实测。

## Recovery anchor
- 目标/不可变约束见确认记录；guard CRASH-UNIFY-20261110已启动，初始无脏路径，基线dev1/930d1b8097d00330e284b61877f88264b1eda27b。
- A ec065f6b-c787-4aa8-8cc6-194a71cae190已返修运行2/3；B eb576b11-1c0a-488b-a413-32246b1770ed运行3/3未通过review，父代理仅B兜底；C审查待证据。
- 两项生产差异都在工作区，未提交。.verify2/crash-unify/task-a-review.diff为A首轮固定审查包。首个统一Gradle定向测试+编译执行中，日志.verify2/crash-unify/gradle-initial.log。
- 风险已明确路由，不把源码测试当设备验证、不提交未编译代码；最小下一步仅B失败测试与状态/闸门修复，完成后再核对构建与C结果。

## 最终验证（2026-11-11 01:0x +08:00）
- 命令（一次，完整日志.verify2/crash-unify/gradle-final2.log）：`gradlew.bat --offline --console=plain :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.utils.Crash*Test' --tests 'com.fongmi.android.tv.utils.PreviousProcessCrashRecoveryTest' --tests 'com.fongmi.android.tv.ui.activity.CrashActivityDetailsTest' :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.utils.Crash*Test' --tests 'com.fongmi.android.tv.utils.PreviousProcessCrashRecoveryTest'`
- 结果：BUILD SUCCESSFUL（1m18s）；两端 Java 编译通过；mobile 38 项、leanback 38 项共享崩溃测试 + mobile 7 项 CrashActivityDetailsTest，全部 0 failures / 0 errors（CrashActivityIsolation 5、CrashHandler 6、CrashLogText 10、CrashLogWiring 7、CrashRecoveryGate 2、CrashRecoveryState 7、PreviousProcessCrashRecovery 1、CrashActivityDetails 7）。
- 父代理复现并修复的缺陷（真实失败先行）：60 秒 grace 窗口吞掉另一次真实崩溃、墙钟回拨隐藏确认死亡 → 2 项失败测试先红后绿（8 项状态测试通过）。
- 未执行：真机/模拟器运行验证。设备 emulator-5554/5556 均为 x86_64 API28，而仓库只产出 arm64_v8a/armeabi_v7a 产物，因此按钮、静默一次、跨进程 ack 与恢复页渲染均无设备实测证据；Android 11+ 系统退出历史同样无设备实测。

## 风险处置（主角发现问题与去向）
|发现|处置|
|---|---|
|CAOC 抑制 handleBindApplication 与 3 秒连续崩溃|改由 App.attachBaseContext 早装自有 FatalHandler 并移除 CaocInitProvider 自动安装（A，已修复并测试）。|
|错误进程重复业务初始化/坏偏好|App 轻量进程早退 + 仅 Init.set（A，已修复并测试）。|
|裸 Intent 重启抛异常|恢复页独立安全重启分支与详情降级（A，已修复并测试）。|
|stack 抽取测试依赖 javax.tools/URLClassLoader，Android 单测无法编译|父代理改为直接驱动生产 FatalHandler；这也是 Gradle 首次编译失败的根因（已修复）。|
|FatalHandler 重入提前 terminate|A 第 2/3 轮修复（已修复并测试）。|
|isMainProcess 用包名|改用 ApplicationInfo.processName + 回退（已修复并测试）。|
|60 秒窗口吞掉新崩溃、墙钟回拨|改用确定性事件身份 + 平台 runId（已修复并测试）。|
|超时后后台仍消费、pending 重复跳页|改为过期无条件放行、晚到确认仍展示（已修复并测试）。|
|闸门超时被 worker 抢占而失效（审查 D 的 Important）|expire() 独立放行，不再依赖 worker 提交完成（已修复并测试）。|
|ack 在主线程做文件事务（审查 D 的 Important）|ack 移出主线程，重启前有界等待确认落盘（已修复并测试）。|
|GateTest 依赖仓库根 CWD|改为按模块/仓库根自适应（已修复，两端测试通过）。|
|silent/restart 失败提示文案不符（Minor M1）|新增 crash_action_failed（3 locale）并替换（已修）。|
|CrashReport.install 去掉早启动防御（Minor M2）|恢复 RuntimeException 防御（已修）。|
|恢复页启动后主进程继续运行 | 启动错误页后结束并终止本主进程，避免与错误进程日志服务争用（已测试）。|

## Rulings（父代理代用户决定，若错需返工）
1. 能进入 Home 的所有用户入口（含 VIEW/SEND）同受恢复闸门约束 —— 依据：外部 Intent 绕过会让确认崩溃不显示；代价：极端下多等一次 3 秒上限。
2. 跨进程消费只认自有锁文件状态，不复用 SharedPreferences/debug 游标/静默标记 —— 依据：读者是另一个进程；代价：需要维护事件身份格式。
3. 移除 CAOC 自动 provider，自有处理器成为唯一 Java 致命入口 —— 依据：CAOC 抑制启动期崩溃且会覆盖外层 handler；代价：不再享有其抑制逻辑（本轮正是要覆盖它）。
4. 闸门到期无条件放行，晚到确认仍展示页面 —— 依据：已持久消费的事件不会循环，同时避免无限挂起；代价：极端超时下页面出现在配置加载之后。
5. ack 移出主线程、重启前有界等待 —— 依据：UI 不可做无界磁盘 I/O；代价：极端下重启前最多等 1.5 秒。
6. B 第 3 轮未通过审查后由父代理直接兜底修复（R8 任务级例外）—— 依据：该简报 3 次运行已用尽且缺陷集中在同一状态/闸门文件；代价：该模块最后一次修复未经独立子代理审查，改由本轮测试与本记录覆盖。
7. 设备运行验证放弃（模拟器 x86_64 与 ARM 产物不匹配）—— 依据：安装不可能成功，不虚报；代价：按钮/渲染/跨进程路径保持未实测。

## beta 合并后复审与修复（2026-11-11，+08:00）
- 用户授权：合并 origin/beta 后完整评审当前分支改动（含已提交未推送），修复、验证、再次评审直到通过，随后提交、推送 dev1、创建中文 PR 到 beta，最后拉取远端最新代码。用户明确不再请求批准；不强推、不重写历史、不自动合并 PR。
- 前置事实：origin/beta=930d1b8097d00330e284b61877f88264b1eda27b 已合入 dev1（Merge PR #552）；初始工作区干净、无冲突；HEAD=6509359e9a0cf98189e1feb707c1fb56648a8e55，5 个未推送提交；beta...HEAD 审查面为21文件、+1386/-109。
- Guard：CRASH-UNIFY-20261110-BETA-20261111 / standard；scope=上述21路径，protected初始脏路径0。后续修复保留本任务已有修改，不丢弃、不越界。唯一任务记录仍为本文件。

### 两轮独立评审结论与修复处置
- 第一轮：Spec 不通过（H1墙钟过滤吞确认死亡、H2 pending恒空丢交付）；Standards 无硬违规，提出报告归属、生命周期及行为测试等判断问题。
- 首批修复：删除fresh直接墙钟判据，恢复同进程pending交付，initialize回退base context，read优先有效published代，报告暂以PID比较，App资源隔离拆分crashPageProcess，并增两个State行为测试及一个Wiring谓词测试。
- 首批定向验证：双flavor Java编译通过，mobile48项、leanback41项，failures/errors/skipped均0；BUILD SUCCESSFUL 1m26s。此前一次失败是损坏状态被误当空状态（StateTest第76行），已收回为不可读时fail-open，不能把此失败描述成新增用例的RED。
- 验证日志：`%TEMP%/webhtv-crash-beta-review-2-20261111.log`。实际过滤器为 `com.fongmi.android.tv.utils.Crash*Test`、`com.fongmi.android.tv.utils.PreviousProcessCrashRecoveryTest`，mobile另含 `com.fongmi.android.tv.ui.activity.CrashActivityDetailsTest`；任务为两端 `compile*Arm64_v8aDebugJavaWithJavac` 与 `test*Arm64_v8aDebugUnitTest`，使用 `gradlew.bat --offline --console=plain`。
- 第二轮：Spec不通过、Standards不通过。不能据首批测试通过宣布全量评审通过，不能把下列缺陷留在风险表后交付。

|问题|第二轮已读代码证据与结论|处置|
|---|---|---|
|H1可靠身份缺口|无runId时仍以PID+[java.start,java.end)墙钟区间推断同死亡；PID复用/回拨会误吞新CRASH/NATIVE/ANR或重放已确认事件|已交残余修复子代理，先RED后去除不可靠身份推断，明确无摘要降级边界|
|H2跨进程待交付丢失|pending仅内存；页面尚未出现就持久认领，若无宿主或启动失败后进程退出，下次也不恢复|已交修复；区分持久待交付与确实展示确认，补跨实例/宿主行为覆盖|
|J2报告误归属|同PID不代表同死亡；5分钟内PID复用的Native/ANR可拼入旧Java报告|已交修复；依据可靠事件/run身份关联，不明关联宁可不混，保持无history入口行为|
|H-S2a保存协议不闭合|read选current，save却先删current、保留更旧bak；中断后回读旧代复活消费标记|已交修复；保护被选有效代，补真实文件事务与发布中断覆盖|
|H-S2b解析异常绕过备份|Properties.load坏Unicode转义抛IllegalArgumentException，但read只catch IOException；有效bak也被绕过|已交修复；解析失败统一回退有效备份，两份均损坏保持fail-open|
|W4生命周期及测试缺口|queued.next仍由this::loadConfig等捕获Activity，未解除value→key强引用；但3秒/settle有界清理，不能称永不回收|已交修复与生产内核行为测试；每个动作执行前复核宿主存活，不只做源码contains|
|W7早期资源保护|crashPageProcess默认false减弱早期防护，但未证实实际attach前取资源；正常error已隔离，helper语言恢复基线|作为局部加固交修复，不把假设当必现崩溃，不扩播放器行为|

### 已确认的依赖与验证边界
- CAOC 2.4.0实际AAR字节码证明：Builder.apply不调用install；restartApplicationWithIntent只修饰Intent、finish、startActivity、killCurrentProcess，不要求handler已安装。证据位于 `.git/crash-beta-review-20261111/caoc-bytecode.txt`。不把字节码核对冒充设备按钮实测。
- CrashLogSession不构造CrashRecoveryState或打开其锁，不能归因其与ack争锁。
- helper仍有Android默认异常处理，只是未安装WebHTV定制FatalHandler；不新增监护服务或native处理器。
- 两份状态均不可读时fail-open是保守降级；有效bak被绕过是缺陷，两者不能混为一个已接受风险。
- 当前无真机/模拟器运行证据：已有模拟器为x86_64/API28，产物仅ARM，不能宣称恢复页渲染、按钮、跨进程ack或Android11+平台历史已设备验证；源码接线测试不能代替这些验证。


### 第二輪残余修复与真实构建证据
- 原第1轮残余修复经全量复审再次不通过：旧handoff遮蔽新已观测死亡、Java报告仍仅PID关联、损坏gen绕过备份、坏new轮转毁有效bak、提交失败next重复执行，已全部回派第2/3轮，不作为已接受风险交付。
- 第2轮新增统一`java:<run UUID>`事件token、报告前置event元数据与恢复详情精确event/process校验；应用私有`previous-process.state.events`顺序台账逐条持久保存已观测确认死亡，ack按事件幂等确认；旧状态迁移不丢待交付事实，SIGNALED不误报也不遮蔽先前已记录死亡。
- 保存协议：load内验证gen、Unicode解析失败统一回退；publish保护read实际选中的完整有效代，坏/旧new不能替换唯一有效bak；不扫描可能半写的tmp。Gate.submit同步拒绝统一finish并只释放一次，保留原3秒超时、迟到决定与Home销毁注销。
- 子代理JVM测试57项通过（State23/Gate10/Wiring11/Previous2/Handler6/Isolation5）。Gate10为7行为＋3源码接线；真实写入/观测序列核对eventId，报告不把纯JVM当设备验证。
- 协调者真实构建：`gradlew.bat --offline --console=plain`，双端`compile*Arm64_v8aDebugJavaWithJavac`和`test*Arm64_v8aDebugUnitTest`；过滤器为`com.fongmi.android.tv.utils.Crash*Test`、`com.fongmi.android.tv.utils.PreviousProcessCrashRecoveryTest`，mobile另含`com.fongmi.android.tv.ui.activity.CrashActivityDetailsTest`。
- 结果：BUILD SUCCESSFUL 2m07s，exit0；mobile74项、leanback67项，failures/errors/skipped均0。日志`.git/crash-beta-review-20261111/gradle-fix2-final.log`。构建后冻结20源码SHA256不变；限定修复Spec/Standards独立验收仍待结论，不据测试绿宣称通过。
- 明确代价：事件台账保留已确认身份用于精确去重，不自动截断事件数量；磁盘及后台流式扫描成本随真实死亡数累积，工作内存单条有界、主线程不执行台账I/O。无summary无法可靠跨通道关联时，允许Java与平台各显示一次，绝不PID/墙钟猜身份误吞新确认死亡；设备页面、按钮、真实平台history/ack仍无实测。

### 限定修复独立验收（第1轮）
- Spec轴：`通过`。五项独立核对均成立：FatalHandler 先写报告后交接且 writer/decorate 共用同一 `java:<run UUID>`；报告 `event=` 元数据前置在 stack 边界之前，LogSession 按 event+process 关联、不凭 PID/墙钟/stack；`begin()` 先持久记录已观测确认死亡再交付旧待确认事件，旧 handoff/Java 迁移不覆盖；Events 台账 COMMIT/fsync、只截断未提交尾、单字节幂等 ack，Properties 发布中断不回滚已提交台账；坏 gen 参与 fallback、publish 保护实际选中代；`Gate.submit` 同步拒绝统一 finish 且 deadline 不重复、晚到仍可交付、两端 Home 注销接线保留。报告：`.git/crash-beta-review-20261111/fix2-spec-verdict.md`。
- Standards轴：`不通过`，唯一确定缺陷为代号边界：`load` 接受 `gen=Long.MAX_VALUE-1`，`save` 生成读取器明确拒绝的 `Long.MAX_VALUE` 并在 `publish` 删除唯一有效旧备，下一次事务 begin/ack 全部 fail-open。属常数规模输入边界，不是死亡数无限推断；已回派第3/3轮做保存前可读性检查与真文件回归，不静默降级为风险。
- 其余 Standards 项（台账 scan/append/ack、迁移原子性、统一身份、Gate 一次释放、注销、API 守卫）本轮未发现确定缺陷；台账按身份精确去重、无自动数量截断属已声明代价。

## 最终验收与交付（2026-11-11，+08:00）
- 残余修复简报第3/3轮完成；gen保存前检查为单点补丁，仅State与StateTest变化。其余18个代码文件SHA256与已接受修复面一致，不重复已通过的无关检查。
- 限定双轴第2/3轮最终均通过：Spec报告`.git/crash-beta-review-20261111/fix2-spec-verdict.md`、Standards报告同目录`fix2-standards-verdict.md`。旧“Spec先通过后撤销”仅为中间记录，最终以本节及更新后两份报告为准。
- 最终真实验证：`gradlew.bat --offline --console=plain :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.utils.Crash*Test' --tests 'com.fongmi.android.tv.utils.PreviousProcessCrashRecoveryTest' --tests 'com.fongmi.android.tv.ui.activity.CrashActivityDetailsTest' :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.utils.Crash*Test' --tests 'com.fongmi.android.tv.utils.PreviousProcessCrashRecoveryTest'`。
- 结果：BUILD SUCCESSFUL 4m51s，exit0；mobile78项、leanback71项，failures/errors/skipped均0，双端Java编译通过。日志`.git/crash-beta-review-20261111/gradle-closure-final.log`及exit文件；State27项（新增4个gen边界真实文件用例）通过，不把测试夹具gen恢复当生产自动功能。
- 远端新鲜度检查：再次fetch origin beta/dev1，beta仍为930d1b8097d00330e284b61877f88264b1eda27b、dev1仍为886ea11896aefb8105e61fd1f13113f011e86832，二者均已包含于当前HEAD，无新的合并冲突或未审覆盖差异。

### 具体改动与风险闭环
|内容|落点与结论|
|---|---|
|统一崩溃入口、独立可操作恢复页|App/Startup/Manifest/CrashReport/CrashActivity，两端Home配置加载前闸门；保留普通/静默重启、详情、复制日志地址；错误进程跳过业务初始化，双轴已覆盖|
|避免PID复用/回拨误判及日志混死因|去除PID+墙钟窗口归并，只接受可靠run身份；writer/decorate共用事件token，LogSession按event/process关联并拒绝不确定报告；已定向测试及独立复审|
|跨进程待交付与多次死亡不漏报|顺序事件台账在交旧事件前保存新已观测事实，Java B/C不覆盖A，确认前保留，ack幂等，SIGNALED不伪造确认退出；真实序列/迁移/尾半写测试通过|
|保存中断、损坏代与gen边界|保护实际选中有效代，Unicode/坏gen回退，坏new不毁唯一备份；保存前拒绝不可读下一代并保源；真实故障形状与最后合法代测试通过|
|启动与生命周期|生产Gate行为内核，提交拒绝只放行一次、3秒独立timeout、迟到交付、逐步存活检查、Home销毁注销；执行型与接线测试及双端编译通过|
|质量记录|曾发现的真实缺陷全部回派修复并再次独立验收，无确定未处理缺陷；中文PR包含具体功能/修复/验证与下面边界|

### 明确边界与代价
- 无平台summary时无法可靠合并Java与延迟平台记录，可能分别显示一次；优先保留确认死亡，不伪造PID/墙钟身份去吞新死亡。
- 精确去重台账保留已确认事件身份和记录，磁盘/后台流式扫描成本随真实死亡数累积；工作内存单条有界，主线程不执行台账I/O，无正常场景material回归证据，不擅自引入通用压缩/数据库。
- gen耗尽时保守fail-open，保留有效状态源及已sync事件/ack；不自动重置。事务返回false不代表已sync台账回滚，恢复可写gen仅测试夹具，不承诺耗尽期间正常交付。
- 本次未进行设备安装运行、恢复页按钮/渲染、OEM历史/summary及跨进程ack实测，不把静态/JVM/Gradle通过称作设备验证。

## Recovery anchor（beta复审）
- 目标已完成本地质量门槛：完整beta改动经独立双轴复审，发现缺陷回派三轮残余修复，最终双轴通过、双端149项定向测试与Java编译通过。
- 分支dev1；guard CRASH-UNIFY-20261110-BETA-20261111 / standard；初始干净、保护路径0，13个本轮改动路径均在21路径scope内。回滚以task_guard生成的原子提交与注释recovery标签为准。
- 当前处于交付阶段：记录已核验，马上task_guard finish提交并打tag；提交后推送dev1和本次新recovery标签，创建中文PR到beta，最后pull dev1。
- 唯一下一步：执行已授权的提交、推送、PR与最终拉取；不强推、不重写历史、不自动合并PR。交付坐标由Git/PR记录及最终回复提供，不为回填ID再追加代码提交或重跑构建。
