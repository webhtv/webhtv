# WorkManager 设备保护存储启动崩溃修复

## 目标与范围

- 任务：WORKMANAGER-DIRECT-BOOT-20261012；quick-fix，用户已授权完善修复。
- 基线：dev1 / `92a0ca0bbe50da70768b7245ed6dd17a51bffcea`，初始工作区干净。
- 修复 `Cannot initialize WorkManager in direct boot mode`，不改变业务数据位置、追更任务名/周期/约束/取消语义，不变更依赖或播放器。
- 路径：main manifest、following 下 WorkManager 适配/初始化/调度器、对应测试与本文件。禁止正式包、卸载/清数据。

## 证据与设计决策（2026-10-12）

这是已定位异常的局部兼容修复，沿用 WorkManager 支持的显式初始化接口，不属于新的架构或上游合并阶段。

1. **A / 现场日志**：用户 `G:\下载\crash.txt:1560-1588`；9 个独立 PID 同样由 `InitializationProvider -> WorkManagerInitializer.create` 失败。1617 行错误处理器拒绝再启动错误页。发生在 Application.onCreate/首页之前，并非业务数据库完整性检查。
2. **A / 实际库字节码**：本地 Maven 缓存 `androidx.work:work-runtime:2.11.2` AAR，`WorkManagerImpl` 构造器先取 `getApplicationContext()`，再检查 API>=24 且 `isDeviceProtectedStorage()`，真则抛上述异常；`initialize` 工厂也会取 application context。`WorkManager.isInitialized()` 是公开接口。不能仅换普通外层 Context；必须让 WorkManager 看到的 application context 保持 CE。
3. **A / Android 框架实现**：本地 Android SDK `ContextImpl` 源码显示 `createPackageContextAsUser` 将传入 flags 写入 Context；隐藏 flag `CONTEXT_CREDENTIAL_PROTECTED_STORAGE=0x10` 会使文件 API 指向 credential-protected 目录。适配器使用该公开方法的 flag 入口，并用 ContextWrapper 覆盖 `getApplicationContext()`，防止 WorkManager 再归一化回 DE Application。应用自己的 `App`、Prefers、Room 业务数据库不切换目录。
4. **A / 当前调用链**：`app/src/main/AndroidManifest.xml:71` 合并 Startup provider；打包 manifest 含默认 WorkManagerInitializer。`FollowingScheduler` 有五个调度/取消入口；`FollowingUpdateWorker` 通过 `FollowingSettings`/`FollowingStore` 访问现有全局业务数据，不能改 App 的 base context 或迁移业务数据库。上述 manifest、调度器与版本表相对故障 APK 标记 revision `af9a18fa47048893b438ab24c9b338041fa306c1` 无差异。
4. **官方文档定位（未取得网页正文，不作为已验证网络证据）**：https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration 和 https://developer.android.com/privacy-and-security/direct-boot 。网页/Jina 获取失败，停止重复网络尝试；本修复 API 决策依据本地实际库字节码，行为由 Android 框架 Robolectric 与真实 WorkManager 验证。未宣称完成广泛最佳实践研究。论文/基准、跨项目架构替换不适用于这个已确定的局部异常；不因 PR 标题猜测回归来源。

### 方案比较

- 不改：保留 9/9 冷启动失败，不可接受。
- 仅去掉 WorkManagerInitializer：延迟至追更 `getInstance` 时仍失败，且破坏重启任务恢复，不可接受。
- 只等用户解锁：`isDeviceProtectedStorage` 不是 `isUserUnlocked`，默认 DE 的固件解锁后仍可能失败，不可接受。
- 全局改 App 存储：可能隔离/丢失既有配置与数据，拒绝。
- **采用**：只替换 WorkManager 默认 initializer；使用 `createPackageContext(packageName, 0x10)` 创建 credential-protected Context，再用局部 ContextWrapper 让 WorkManager 的 `getApplicationContext()` 仍返回该 CE wrapper；普通 CE 设备直接返回原 app context。真正未解锁时注册 USER_UNLOCKED，重新检查状态关闭注册竞态，解锁后启动并按顺序提交延迟的调度/取消操作。公开入口不保留 Activity Context。其他 Startup 初始化器与业务存储不变。最终合并 manifest 已确认：默认 `WorkManagerInitializer` 被移除；Room 的 `MultiInstanceInvalidationService` 仍保持其原有 `directBootAware=true`，本任务没有修改 Room 或业务数据库行为。

### 验收与边界

- 默认 DE 且已解锁能初始化真实 WorkManager、在 CE 路径创建其任务库；传入与规范化后的 app context 均为 CE。普通设备原路径保持不变。
- 未解锁不打开 WorkManager 数据库；解锁后初始化与待提交操作恢复，重复初始化/广播不重复提交。
- 所有追更调度/取消经同一入口；保持原任务名称、周期、网络约束及 KEEP/UPDATE/REPLACE 策略。
- 不移动/清空业务数据；临时验证文件/本任务进程及时清理。没有用户真实盒子的修复后实测，不将其写成已通过。
- 回滚：revert 本任务原子提交（恢复默认 initializer 与旧 getter），不需要回滚业务数据；注意回滚会重新暴露已知盒子启动崩溃。

## 验证记录

- RED：独立 JUnit 运行 `FollowingWorkStartupSourceTest`（当时该类只有 2 个用例），2 项均按预期失败：默认 initializer 未移除、调度器仍直接取 WorkManager。临时 class 输出已删除。
- GREEN：离线执行 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 与 `:app:compileLeanbackArm64_v8aDebugUnitTestJavaWithJavac`，均通过；随后执行 `:app:processLeanbackArm64_v8aDebugUnitTestManifest`，通过。
- GREEN：执行 `:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.following.FollowingWorkStartupSourceTest --offline --no-daemon`，通过。
- **口径订正（C67 复评，2026-10-13）**：本行原记「XML 报告 `tests=2 skipped=0 failures=0 errors=0`」，与提交信息「3/3 passed」及入库产物的 3 个 `@Test` 互相矛盾。C67 在合并后的树上重跑，JUnit XML 实测 `tests=3 failures=0 errors=0 skipped=0`，以该实测为准。
- GREEN：最终合并 manifest 已确认 `androidx.work.WorkManagerInitializer` 不存在，仅保留 `FollowingWorkInitializer`、应用 Startup、Emoji、Lifecycle、OkHttp、ProfileInstaller 初始化项。
- 环境边界：完整 Robolectric 行为测试曾因 Gradle 任务 5 分钟无结果而超时，之后未重复同一完整任务；一个早期测试草稿因 API 不匹配已删除。没有真实盒子修复后实测，不能声称设备现场已验证。
- 清理：超时任务后确认没有残留测试任务进程，只有正常 Gradle daemon；临时日志、jar、编译输出按任务范围清理。

## C67 复评补充（2026-10-13）

合并 `origin/beta`（`a51e4085f5`）后对本次修复做第二轮复评，改动与证据记在 `docs/C67-beta-merge-review-dev1-20261013.md`。三处修正：

1. **F1（实现缺陷，已修）**：`FollowingWorkManager.start()` 在 `!isUserUnlocked(app)` 分支注册解锁接收器后**直接 return**，没有像本文档 §方案比较 所称的那样「重新检查状态关闭注册竞态」。`ACTION_USER_UNLOCKED` 不会重放，解锁若落在 `isUserUnlocked()` 判定与 `registerUnlockReceiver()` 完成之间，本进程已排队的调度/取消会永久丢失。已补上注册后的复检（仍锁定才返回），使文档描述与实现一致；其余路径行为不变。
2. **F2（回归锁失效，已修）**：原 `workManagerUsesCredentialStorageWithoutChangingAppStorage` 的 5 条断言全是 `source.contains(...)`，其中 `contains("getApplicationContext()")` **恒真**（`start()` 里本来就有该调用），删掉 `ContextWrapper` 里那行承重覆盖（即让缺陷原样复发）也不会转红。已改为 6 条结构性正则断言，覆盖 credential 标志、`getApplicationContext()` 覆盖返回 `this`、CE 设备早返回、`initialize(workContext)`、未解锁推迟、**注册后复检**。
3. **F3（本文档口径，已订正）**：见 §验证记录 的口径订正行。

**C67 复测结论**：`:app:testLeanbackArm64_v8aDebugUnitTest` 全量 662 suites / 4227 tests / 8 failures，其中 `FollowingWorkStartupSourceTest tests=3 failures=0 errors=0 skipped=0`，`following` 相关 11 个用例类（`com.fongmi.android.tv.following` 9 个 + `ui.activity.FollowingUiSourceTest` + `bean.BackupFollowingTest`）全部 0 失败；8 个失败全部落在本修复未触碰的 beta 侧既有用例上，且均为 Windows 环境差异（6 个源码守卫用例断言工作副本里的 `\n` 字面量，被 `core.autocrlf=true` 的 CRLF 检出破坏；2 个 mpv 用例依赖 Linux 下才成立的 `File.renameTo` 语义）。详见 C67 文档的失败分类表。

## Recovery anchor

- 状态：实现完成并经 C67 复评修正；源码与最终合并 manifest 已编译/合并，`FollowingWorkStartupSourceTest` 3 例在双 flavor 通过；未做正式包和设备安装。
- 当前文件：`app/src/main/AndroidManifest.xml`、`app/src/main/java/com/fongmi/android/tv/following/FollowingWorkManager.java`、`FollowingWorkInitializer.java`、`FollowingScheduler.java`、`app/src/test/.../FollowingWorkStartupSourceTest.java`、本任务文档。
- 唯一下一步：随 C67 合并提交一并推送 `dev1` 并创建 `dev1 -> beta` PR；若需盒子验证，必须后续覆盖安装且不能卸载现有包。