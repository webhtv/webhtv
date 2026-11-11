# C128 — Silent1566 最新改动合入 dev3（缓存总上限共享预算与收敛）

## Recovery anchor

- 目标与验收：把 `https://github.com/webhtv/webhtv.git` 分支 `Silent1566` 的最新 2 条提交完整合入本地 `dev3`，保留本地缓存模块增强与既有 UI 定制；唯一冲突按并集闭合；缓存定向单测与双端 Java 编译通过；不推送、不开 PR，交付本地合并提交与 annotated recovery 标签。
- 基线：`dev3@930d1b8097d00330e284b61877f88264b1eda27b`；源 tip `1da5e48154e5e8dd19d73d4939cd9268dda7a7e5`；merge-base `fbe5fe91db9ba41fd380929ebc039a699dba463b`（C123 已合源 tip）。取源日期 2026-11-11 Asia/Shanghai。`origin` 指向 `Silent1566/webhtv`，与本次源不同；`upstream` 指向 `fish2018/webhtv`，均不得作为本次源。
- 初始脏路径：0（工作区仅有未跟踪的 `.tmp-*` 等临时文件，均不在本任务范围）。
- 回滚锚点：上述 `dev3@930d1b8097`；交付后用 `git revert -m 1 <本次合并提交>` 撤销，禁止 `reset --hard`。
- 状态：结果树集成、两轮评审修正与风险定向验证已完成，尚未提交。双端缓存测试各 exit 0 / 0 失败（Leanback 109 中 108 通过、Mobile 97 中 96 通过），唯一跳过为既有 Windows symlink 平台假设；双端 Java 编译在同一 merge 结果树上已 BUILD SUCCESSFUL（修正轮由单测任务的 Java 编译覆盖）。MERGE_HEAD 为源 tip，HEAD 仍为基线。
- 唯一下一步：两轴 run2 复审与协调者独立验收已通过，用户确认既有 Windows symlink 跳过例外；由 task_guard finish 原子提交并立即创建 annotated recovery 标签。此文档随 Task-Guard: C128 合并提交闭合，具体提交/标签由守卫输出与本次交付摘要定位，不推送、不开 PR。

## 需求与边界

用户决定：合入当前本地 `dev3`；交付范围为本地提交 + recovery 标签，**不推送、不开 PR**；不需要在派发前等待额外审批。

范围即上游 14 条路径（7 个 `app/src/main/java/com/fongmi/android/tv/cache/*` 或相关主源码、`service/PlaybackService.java`、`ui/dialog/CacheManagementDialog.java`、三语 `strings.xml`、3 个单测、`docs/CACHE-MGMT-01-cache-management-design.md`）加本文件。禁止依赖升级、无关重构、整文件上游覆盖。

## 全量源提交清单

`fbe5fe91db9ba41fd380929ebc039a699dba463b..1da5e48154e5e8dd19d73d4939cd9268dda7a7e5`，共 2 条，均处置为完整纳入：

| 完整 commit | 内容 | 处置 |
| --- | --- | --- |
| `1da5e48154e5e8dd19d73d4939cd9268dda7a7e5` | fix(cache): 修复总缓存上限调度与收敛状态误报（12h 注册与开关解耦、异步完成恰好一次、按结果解释超额、阻塞判定改为「受保护内容本身超上限」） | 纳入；含新增 `CacheSchedulerConvergenceTest` |
| `474ad8aa9af86bfa3bd1d4de277886a949876f3d` | fix(cache): 总上限改为全部模块合计的共享预算（`PlaybackCacheBudget` 去 64 MB 下限、新增 `CacheCleanupMode.CONVERGE` 与 `CachePolicyEngine.convergencePlan`、播放结束收敛、面板三语口径） | 纳入；含新增 `TotalLimitConvergencePlanTest` |

## 已冻结证据

- `git merge-tree --write-tree --name-only HEAD 1da5e481` exit 1，唯一内容冲突：`app/src/main/java/com/fongmi/android/tv/ui/dialog/CacheManagementDialog.java`。
- 该冲突点：本地 `dev3` 总上限回调调用 `applyPlaybackLimit()`（自 C123 起引入的本地收敛入口），上游改为 `CacheScheduler.get().convergeNow(() -> refresh(true))`，注释与收敛语义不同但目标一致，须按并集闭合，且不得回退本地 UI 定制（`WebHtvAlertDialogBuilder`、`DialogSafeArea`、`applyFocusRing`、`PLUGIN_AUDIT`/`PLUGIN_HTTP_CACHE` 模块名）。
- 本地该模块另有 270 行增强（`CacheModuleId`、`CacheModuleRegistry`、插件 HTTP 模块与相应单测），必须完整保留。
- 证据等级 A：以上均来自实际 `git` 对象与文件内容，非标题推断。
- 不适用类别：本任务不改 native/FFmpeg/lock/ABI/二进制制品，故 native supply-chain 与解码性能类证据不适用；未新增依赖，无需依赖来源审查。

## 验收标准

1. 上游 2 条提交的每一处语义改动都在结果树中生效（含三语字符串、`PlaybackService.onDestroy` 播放结束收敛、`CacheCleanupMode.CONVERGE`、`convergeNow`/`blockedOverBytes` 可编译被调用）。
2. 无冲突标记残留；`git status` 中无意外修改；`docs/CACHE-MGMT-01-cache-management-design.md` 的上游增量保留。
3. `:app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.cache.*'` 与 `:app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.cache.*'` 通过（0 失败 / 0 错误）；用户已明确接受两端各 1 项既有 Windows symlink 平台假设跳过，按实际计数记录，不修改或排除该测试。
4. `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 与 `:app:compileMobileArm64_v8aDebugJavaWithJavac` 通过。
5. 本地既有定制与新增模块名、UI 行为无回退；新增上游测试类在对应 flavor 下被编译执行。

## 不做的事

推送、PR、发布、版本号变更、设备端实测（本轮无真机，端到端收敛效果仅在文档中标注未覆盖）。

## 集成实施记录（执行者，2026-11-11 +08:00）

### 实际合并与判断

- 执行一次 `git merge --no-edit 1da5e48154e5e8dd19d73d4939cd9268dda7a7e5`，exit 1 为预期内容冲突；MERGE_HEAD 为上述源 tip。HEAD 始终保持 `930d1b8097d00330e284b61877f88264b1eda27b`；未 commit、tag、push、fetch、stash 或重写历史。
- 唯一冲突 hunk：`CacheManagementDialog.chooseTotalLimit()`（结果文件 263–268 行）。本地侧调用 `applyPlaybackLimit()`，其实现（618–627 行）仅在工作线程执行 `MediaSourceFactory.enforceCapacityNow()`，主线程以 binding/isAdded 检查后刷新；上游侧调用 `CacheScheduler.get().convergeNow(() -> { if (binding != null && isAdded()) refresh(true); })`。
- 最终采用上游带完成回调的形态；依据：总预算必须覆盖注册表所有可手工清理模块，不能只重建 Exo 缓存；调度器保证清理完成、后台验证之后，才在主线程恰好一次回调。`applyPlaybackLimit()` 方法与 `chooseLimit(EXO)` 调用（607 行）原样保留；所有局部刷新、焦点恢复及布局代码保持本地版本。没有整文件取上游。
- 保护脚本于代码编辑前执行 `G:\Git\bin\bash.exe .codex/scripts/task_guard.sh start --id C128 --mode upstream`，对 14 条上游 merge 产物及本 C128 文档逐条 `--scope` / `--adopt-dirty`（merge 已创建 staged 产物；本文件由协调者预先创建）。exit 0，active，基线 dev3/930d1b8097，保护 276 条原有脏路径。脚本保护采样较慢，未删改任何预存杂项；不调用会提交/打标签的 finish。

### 自动合并的语义核对

- `CacheScheduler.java`、`CacheCleanupMode.java`、`PlaybackCacheBudget.java`、三个上游测试文件与源 tip 的 `git diff --quiet <tip> -- <path>` 均 exit 0。
- `PlaybackCacheBudget.capacityBytes()`（45–46 行）：`min(preload, max(0,total-others))`，无 64 MB 下限；播放态仍保留预加载上限。
- `CacheCleanupManager.explicitRequest()`（398 行）包含 CONVERGE；本地 `PLUGIN_AUDIT` / `PLUGIN_HTTP_CACHE` 清理分支（375–376 行）及通过 owner 清除审计内存环的方法（460 行）仍在。
- `CachePolicyEngine.convergencePlan(File)`（80–89 行）按 `CacheModuleRegistry` 的 allowManualCleanup 选择、按本地扩展后的 enum 排序；`case MODULE, CONVERGE`（140 行）保留调用者模块列表。本地深度清理仍排除 `PLUGIN_AUDIT` / `PLUGIN_HTTP_CACHE`（48–49 行）；注册表的两个模块（123/129 行）未被本次修改。
- `CacheScheduler.start()`：12h 常量 36 行、一次性收敛注册 59–66 行在自动开关早退（68 行）之前；平台 job 与周期清理仍受开关控制。`limitCheck()` 只把原模块维护放在自动开关内，共享总预算仍会触发。
- `submitConvergence()`（165–175 行）：AtomicBoolean 恰好一次、pending 标记防止异步清理启动后过早通知；执行回调进入后台 `verifyConvergence(reason,result)`（221/240 行），之后 complete 在主线程执行。
- `converge()` 提前阻塞仅判断受保护内容自身大于有效上限（212 行）；`blockedOverBytes()`（130 行）及 `settleBlocked(over,protected,effective,retryable)`（275–277 行）可被调用，失败/延期/取消/播放重入不被误报永久阻塞。
- `PlaybackService.onDestroy()`：245 行先将 running=false，253 行 release，256 行调用 `CacheScheduler.get().onPlaybackEnded()`；本地音频历史保存、ownership/session 和 `Server.clearService(this)` 逻辑仍在。
- `CacheManagementDialog.totalLimitLine()` 替换旧 Exo 配额文案，读取 `blockedOverBytes()`；三语 `cache_management_total_limit` / `cache_management_total_unlimited` / `cache_management_total_blocked` 位于英文 1059–1061、简中 1055–1057、繁中 1010–1012 行。`docs/CACHE-MGMT-01-cache-management-design.md` 上游 2026-11-10 三组记录全部保留（cached diff 已审阅）。

### 冲突、空白与本地 UI 证据

- `git diff --check` / `git diff --cached --check` 均无输出、exit 0；`git status --porcelain` 的已跟踪变更为上游 14 路径，dialog 已由 UU 转 M。
- 实际执行指定的递归裸字符串扫描，exit 0 **有预存误报，不能声称无命中**：5 个未修改二进制（arm64/v7a 的 libmpv.so、libmvcodec.so 和 arm64 libsherpa-onnx-jni.so）以及既有 docs 中引用扫描命令的正文（例如 C111、C120、C4、C89 等）。禁止范围外修改，因此没有为了字面验收而删除这些内容；此差异已提前通知协调者。
- 针对真实文本冲突标记执行 `git grep -I -n -E '^([<]{7} |[>]{7} |[|]{7} |[=]{7}\\r?$)' -- app/src docs`：无输出、exit 1（grep 的无命中状态）。
- 本地定制实际 `Select-String` 命中：WebHtvAlertDialogBuilder 45/290/302/342 行；DialogSafeArea 203/210/211 行；applyFocusRing 572/644/656 行；PLUGIN_AUDIT 750 行；PLUGIN_HTTP_CACHE 751 行。局部焦点方法和单模块 Exo 异步 apply 方法没有被本次差分删除。

### 验证运行配置与待收集结果

- 仅使用 `gradlew.bat --offline --console=plain -I app/build/c128-offline.init.gradle`。构建目录临时 init 脚本为测试 JVM 设置 `robolectric.offline=true` 与 `robolectric.dependency.dir=app/build/c128-robolectric-offline`；SDK 27/28/36 的 instrumented jar 从本机 Maven 缓存复制到构建目录。未改依赖、build.gradle、系统/全局配置或远端；没有联网。
- 每个 Gradle 进程使用 PowerShell Start-Process / WaitForExit(1800000)，超时会终止该进程树并记录 exit 124，不无限等待。完整结果写入 `app/build/c128-validation-results.json`，完整日志为 `app/build/c128-compile-dual.log`、`app/build/c128-test-leanback.log`、`app/build/c128-test-mobile.log`。
- 运行矩阵与真实结果（各首次运行，无重试）：

| 实际 Gradle 任务（均带上述离线参数） | 退出码 / BUILD 行 | JUnit XML 计数 | 日志 |
| --- | --- | --- | --- |
| `:app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac`（同一调用） | 0 / `BUILD SUCCESSFUL in 5m 51s`；两条 compile 任务均实际执行 | 不适用 | `app/build/c128-compile-dual.log` |
| `:app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.cache.*"` | 0 / `BUILD SUCCESSFUL in 3m 26s` | 18 类，107 总计：106 通过 / 0 失败 / 0 错误 / 1 跳过 | `app/build/c128-test-leanback.log` |
| `:app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.cache.*"` | 0 / `BUILD SUCCESSFUL in 45s` | 17 类，96 总计：95 通过 / 0 失败 / 0 错误 / 1 跳过 | `app/build/c128-test-mobile.log` |

- XML 读取范围仅 `app/build/test-results/test{Leanback,Mobile}Arm64_v8aDebugUnitTest/TEST-com.fongmi.android.tv.cache.*.xml`，每个文件均在本次编译完成后产生（Leanback 11:29:04、Mobile 11:29:50），排除了其他包的历史报告。汇总和逐类数据保存到 `app/build/c128-test-summary.json`。初次控制台格式化 OrderedDictionary 使用 Select-Object 输出空属性，但持久化数据正确；仅重读结果 JSON 修正显示，没有重跑构建或测试。
- 上游和本地回归：`PlaybackCacheBudgetTest` 两端各 14/14；`TotalLimitConvergencePlanTest` 两端各 6/6；新增 `CacheSchedulerConvergenceTest` 在 testLeanback 源集中 11/11（mobile 不包含该源集，非测试跳过）；本地 `CachePluginHttpModuleTest` 两端各 2/2，均 0 失败 / 0 错误 / 0 跳过。
- **严格 0 跳过未满足的具体原因**：两端同为既有 `CachePathSafetyTest.finalComponentSymlinkIsStillDetected`。XML 的 skipped message 为 `org.junit.AssumptionViolatedException: File.getCanonicalFile() does not follow symbolic links on this host`；源码 61–68 行明确将 Windows canonicalization 不解析最终符号链接的情况视为平台假设不满足（第 67 行 Assume）。此文件不属于上游两提交增量，本轮未修改，不改变安全判定或屏蔽测试来凑数字。问题已向协调者报告，需要协调者接受既有 Windows 平台跳过例外，或在符合该 canonicalization 假设的环境复测；本执行不能声称满足文档中「0 跳过」的原始硬条件。
- `task_guard.sh check` exit 0：`PASS: task=C128 branch/HEAD, protected dirty paths, scope, and staged paths are safe`。守卫 baseline HEAD=`930d1b8097d00330e284b61877f88264b1eda27b`；276 个预存脏路径指纹不变，包括 .tmp-*，没有将它们列入 adopt。协调者预先创建的 C128 文档是唯一被显式收编的未跟踪任务文档，原内容保留，仅在此处续写。
- 裸字符串扫描的既有文档示例/二进制误报已由协调者明确接受改用「忽略二进制、扫描行首真正冲突标记」独立验收；没有清除预存示例。最终保持所有冲突已解决、仍在 merge 中、不 commit/tag/push。
### 评审修正轮（2026-11-11 11:35 起，协调者裁定 3 项必修）

两轴独立审查确认 3 项必修，均由本次合并引入。以下为实际执行过程与证据。

**必修 1（P1）无人值守收敛不得清除本地 never-evict 模块。** 上游 `convergencePlan` 按 `allowManualCleanup` 选模块，会把本地 `PLUGIN_AUDIT`、`PLUGIN_HTTP_CACHE` 纳入启动/12h/播放结束的无人值守收敛；二者注册表声明 `allowAutomaticCleanup=false`、注释写明 "never evicted on its own"，`deepModules()` 也排除它们，而 `CacheCleanupManager` 375/376 行会真的清空审计环与整个插件 HTTP 缓存树。按裁定在 `convergencePlan` 的有序列表生成处显式排除这两个 id（新增 `AUTOMATIC_EXCLUDED` 常量与注释，说明与 `deepModules()` 同源），未改 `CacheModuleRegistry` 的 automatic/manual 声明，未改面板手动清理按钮——手动单模块清理仍可清空二者。
**必修 2（P2）忙碌/播放跳过时旧 blocked 状态必须失效。** `converge()` 的两个提前 return（`CacheCleanupManager.isRunning` / `PlaybackService.isRunning`）原先不清旧 blocked，而面板 `totalLimitLine()` 直接读 `blockedOverBytes()`，于是播放中把上限调大后面板仍谎报「仍超限 N（受保护内容）」。按裁定在两处提前 return 之前调用 `clearBlocked()`（状态未知即视为可重试，与 `settleBlocked` 的 retryable 语义一致）；未用 `totalLimitLine` 兜底、未加 sleep/polling、未在 Getter 里读盘。
**必修 3 回归覆盖（先红后绿，真实保留失败证据）。** 红证据命令：`:app:testLeanbackArm64_v8aDebugUnitTest --tests "...TotalLimitConvergencePlanTest" --tests "...CacheSchedulerConvergenceTest"`，exit 1、`19 tests completed, 2 failed`，日志 `app/build/c128-run2-red-leanback.log`：`TotalLimitConvergencePlanTest > convergencePlanSkipsLocalNeverEvictModules FAILED (AssertionError at TotalLimitConvergencePlanTest.java:61)`，`CacheSchedulerConvergenceTest > raisingLimitDuringPlaybackClearsOutdatedBlockedState FAILED (AssertionError at CacheSchedulerConvergenceTest.java:124)`。测试 XML 快照另存 `app/build/c128-run2-red-results/`。

**修正后绿证据（各 flavor 各首次运行，无重试；本轮未单独跑编译，Java 编译由两个单测任务的 `compile*UnitTestJavaWithJavac` 覆盖）：**

| 实际 Gradle 任务 | 退出码 / BUILD 行 | JUnit XML 计数（缓存包） | 日志 |
| --- | --- | --- | --- |
| `:app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.cache.*"` | 0 / `BUILD SUCCESSFUL in 26s` | 18 类，109 总计：108 通过 / 0 失败 / 0 错误 / 1 跳过 | `app/build/c128-run2-green-leanback.log` |
| `:app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.cache.*"` | 0 / `BUILD SUCCESSFUL in 21s` | 17 类，97 总计：96 通过 / 0 失败 / 0 错误 / 1 跳过 | `app/build/c128-run2-green-mobile.log` |

- 相对 run1 的变化正好是新回归：Leanback 107→109（`TotalLimitConvergencePlanTest` 6→7、`CacheSchedulerConvergenceTest` 11→12），Mobile 96→97（`TotalLimitConvergencePlanTest` 6→7，`CacheSchedulerConvergenceTest` 属 `testLeanback` 源集故不在 mobile，非跳过）。`PlaybackCacheBudgetTest` 两端各 14/14、`CachePolicyEngineTest` 12/12、`CacheModuleRegistryTest` 8/8、本地 `CachePluginHttpModuleTest` 2/2 全部保持 0 失败。
- 两端唯一跳过仍是既有 `CachePathSafetyTest.finalComponentSymlinkIsStillDetected`（XML 逐类确认 skipped=1 仅出现在该类），原因见上一节：Windows `File.getCanonicalFile()` 不跟随符号链接，平台假设不满足。本轮未修改、未排除该测试。
- 逐类计数与运行元数据保存到 `app/build/c128-run2-test-summary.json`，红/绿运行退出码与日志路径见 `app/build/c128-run2-*.json`。
- 全程保持 MERGE_HEAD=`1da5e48154e5e8dd19d73d4939cd9268dda7a7e5`、HEAD=`930d1b8097d00330e284b61877f88264b1eda27b`，`git diff --name-only --diff-filter=U` 为空，工作区为「已解决未提交」（生产修复未暂存、merge 内容已暂存）；未 commit / tag / push，未联网。
- 修正轮编辑 5 次（2 测试 + `CachePolicyEngine` 2 次 + `CacheScheduler` 1 次）+ 文档 1 次；命令调用在 30 次预算内。未发现需要扩修的新问题。

- 未覆盖：无真机端到端验证；真实 owner 在清理到复扫期间重写缓存的竞态只由现有判断与 shadow 单测覆盖，不扩展为压力测试。上游设计文档已有 Thread.start 异常时 running 标记可能残留的风险说明，非本次新增，不顺手修复。编译中的既有 Room query mismatch、过时 API、unchecked 与 Gradle 10 兼容性警告未作为本次范围内缺陷修复。


## 最终独立验收与关闭记录

- 需求仍为：合入本地 dev3，完整包含源 474ad8aa9af86bfa3bd1d4de277886a949876f3d / 1da5e48154e5e8dd19d73d4939cd9268dda7a7e5；保留本地定制；仅本地提交与恢复标签，不推送、不开 PR。
- Standards run2：原硬违反 2 项/可信缺陷 3 项全部 addressed，新增必修 0、启发式 0。Spec run2：原两项实现错误全部 addressed，缺失需求 0、scope creep 0、新增必修 0。两轴实际读取 run1 index→run2 worktree 四文件增量，不以测试绿或执行者自报代替审查。
- 协调者实际读取修正补丁、红/绿完整日志中相关结果、两 flavor 新鲜 JUnit XML、既有 symlink 测试源码 49–78 行。红证据为 19 项/2 失败/exit 1；绿结果 Leanback 18 类 109 项：108 通过/0 失败/0 错误/1 跳过，Mobile 17 类 97 项：96 通过/0 失败/0 错误/1 跳过。两份 green 日志包含实际执行的 compileLeanbackArm64_v8aDebugJavaWithJavac / compileMobileArm64_v8aDebugJavaWithJavac（非仅 unit-test 编译），并 BUILD SUCCESSFUL。
- 用户在验收差异问答明确选择“记录 Windows 既有跳过，继续本地提交”；验收标准据此记录平台例外，不伪称零跳过、不修改/排除该测试。真机端到端、真实 owner 写入竞态与性能未验证，不把编译/单测视为该保证。
- 已发现且已路由风险：插件审计静默清空（修正收敛计划排除）、TTL 内插件 HTTP 缓存清空（同上）、提高总限后持久 blocked 误报（两个提前返回前失效并补红绿回归），全部修复后两轴复审放行。原裸字符串扫描的二进制/文档示例误报改为行首真实冲突标记扫描，未删除无关内容。既有 Windows symlink skip 已由用户明确接受环境例外。
- 守卫晚于 merge 启动的流程偏差已如实记录；补启动仅收编本次 14 合并产物和任务文档，基线 HEAD 与 dev3 未变，276 个预存脏文件受指纹保护；finish 将执行最终范围/保护文件检查，不把预存文件纳入提交。未改 native、ABI、锁文件、依赖与构建配置。
- 回滚：用 git revert -m 1 <Task-Guard: C128 的合并提交>；恢复标签 recovery/C128/<timestamp>-<commit-prefix> 由同次守卫创建。没有使用 reset --hard、改写历史或远端发布。

## 本次 dev3 同步冻结 beta 与发布边界（2026-11-11，集成 run1 / run2）

- **最新用户授权覆盖范围**：原“不推送、不开 PR”是前次 C128 本地交付的历史边界，原记录保留。本次用户已另行授权通过验收后提交/恢复标签、推送 dev3、创建中文 PR 到 beta、最后 pull；不得强推、重写历史或直接合并 PR。本执行轮不 commit/tag/push/PR/fetch/pull，必须等待协调者独立验收及两轴 run2 后由协调者 finish。
- 冻结源 `origin/beta=4038aab74959f763a80cdc132a6da72b4a3e314c`；目标 `dev3/HEAD=6e97bff395af855b774cc2c41c6b53150abd7e6d`；`origin/dev3=7f8e1a966b6ff44c254eecec9ba657b9517aae50`；HEAD/beta 各 3 提交分叉。未拉取新的 refs，也不把冻结源称为此刻远端最新。
- 初始 tracked clean；276 个预存未跟踪路径（含 .tmp-*、.design-evidence、%SystemDrive% 等）均不收编。简报预计22条 beta 增量，实际 `git diff --name-only HEAD...4038aab74959f763a80cdc132a6da72b4a3e314c` 为21条；协调者确认按实际21+C128文件声明22条 scope，不凑数。首次 Count 预检 exit1 位于任何 mutation 之前。
- 本次先用 Git Bash `G:\Git\bin\bash.exe` 启动 `task_guard start --id C128-beta-dev3-20261111 --mode standard`，对22个精确路径逐一 scope，无 adopt；exit0，保护276。随后 `git merge --no-ff --no-commit 4038aab74959f763a80cdc132a6da72b4a3e314c` exit0，无冲突，unmerged0。文档改动前真实 index `write-tree=2c0a9579dc58b758e8f9e7bdb80a4b5bf6f79edf` 精确等于预演；HEAD未变、MERGE_HEAD为冻结 beta。
- 协调者因前次审查快照缺失，对本地C128全部15路径与本次beta覆盖重新进行独立双轴审查，而非只依赖旧结论。两轴及协调者确认 P2：提高总限后，首扫/复扫失败、converge异常或executor拒绝仍保留旧持久blocked，面板把未知状态误报为当前永久超限。发现后明确路由 run2 返工，不以原测试绿色掩盖。

### run1 实际验证（环境失败，不是通过）

- 联合 Gradle：12:26:48–12:38:31 +08:00，exit1，`BUILD FAILED in 11m 42s`。Mobile Java已执行；Leanback dataBinding写全局 build-cache 超时，Owner PID45684 / Our PID32596，锁 `G:\GradleCache\caches\build-cache-1\build-cache-1.lock`；只读确认另一个 webtv2 工作区使用该 daemon，没有杀其他任务或修改全局配置。
- 缓存/crash测试未执行；run1保存的35份缓存XML是历史报告，`xml_all_fresh=false`，不能用旧 Mobile97/Leanback109计数宣称此轮通过。完整日志、退出时间与 source_before/after 位于 `app/build/c128-beta-evidence/`；原采集进程丢失后仅重新附着原 cmd PID26160收集退出码，没有重复启动Gradle。

### run2 守卫、因果与真实 RED / GREEN

- 扩scope前两个cache文件 blob仍精确等于预演T：scheduler `e5993aea2f32f3c745bd10d887ab4d1489be8d05`、现有ConvergenceTest `074ccbc221d867da936e017a8d83c3c30c537b7f`。原 guard check exit0；随后 abort仅归档状态，不丢merge/worktree；同 id/standard 重start exit0，scope24（原22加scheduler与现有testLeanback回归），adopt仅21个本任务merge路径，C128此时clean不adopt，276预存路径继续保护。未改变双父合并、HEAD/MERGE_HEAD。
- 先仅测试新增6项行为回归，建立旧100限/protected150→blocked50，再提高256，分别模拟初扫失败、converge异常、首次提交拒绝、复扫失败、复扫提交拒绝、复扫执行异常。使用现有shadow/QueueExecutor，仅增加失败开关；验证旧blocked必须失效、finished恰好一次且不早于异步验证，未加production test hook。
- **RED**：12:50:33–12:56:41 +08:00，`--no-build-cache --offline` 定向 `CacheSchedulerConvergenceTest`，exit1 / BUILD FAILED 6m3s；18项/6失败/0错误/0跳过，六项均为 `expected:<0> but was:<50>`，生产仍上述原blob。红XML和完整日志原样保存于 `app/build/c128-beta-evidence/run2-red/`。
- 最小生产修复仅 `CacheScheduler.submitConvergence`：每次新测量开始、任何fallible工作之前清除上一次blocked；首次executor拒绝也失效旧值。合法本次测量仍可recordBlocked，保留播放/忙碌保护、never-evict排除、cleanup等级与AtomicBoolean恰好一次/异步完成时序。不在getter读盘，不修改其他生产逻辑。
- **GREEN**：12:57:55–13:00:37 +08:00，一次联合双端Java与全部cache/crash定向测试，exit0 / BUILD SUCCESSFUL 2m41s；测试与两条 `compile*Arm64_v8aDebugJavaWithJavac` 均实际执行。Mobile25类175项（cache97+crash78）=174通过/0失败/0错误/1跳过；Leanback25类186项（cache115+crash71）=185通过/0失败/0错误/1跳过。新增ConvergenceTest18项全通过，原合法protected与可重试行为保留。
- 两端唯一skip仍为用户前次已接受的 `CachePathSafetyTest.finalComponentSymlinkIsStillDetected`；原因 `File.getCanonicalFile() does not follow symbolic links on this host`，未修改/排除用例，不伪称0跳过。50份新鲜GREEN XML原样快照、逐类计数/skip信息、完整日志、JDK21/本机SDK信息及源指纹位于 `app/build/c128-beta-evidence/run2-green/`。source_before/after完全一致。
- 为避免已证实的跨工作区cache锁，run2仅在命令中增加 `--no-build-cache`，不改仓库构建脚本、依赖或全局配置；继续复用既有ignored `c128-offline.init.gradle` / `c128-robolectric-offline`，JDK21通过JAVA_HOME由gradlew.bat调用。run2使用2/3次Gradle（RED一次、GREEN联合一次），不重复成功检查。

### 未验边界、回滚和唯一下一步

- 未进行native/其他ABI矩阵、设备安装运行、恢复页按钮/渲染、OEM平台历史/summary及真实跨进程ack验证；真实缓存owner写入竞态、性能压力仍未验证。不把静态/JVM/Gradle通过称为设备或完整性能保证。原有Room/API/unchecked/resource与Gradle兼容性警告不在本轮顺手修复。
- 当前未提交；回滚锚点仍为 dev3/6e97bff395af855b774cc2c41c6b53150abd7e6d，未提交状态如需撤销须由协调者明确授权安全处理，禁止reset/stash；验收后生成合并提交才能使用 `git revert -m 1 <真实合并提交>`，不写尚不存在的commit/tag/PR坐标。
- **最终独立验收（本次 beta 发布门）**：协调者已实际读取集成报告、候选树 `72c6bbaf0389405c45ae4ba1a3ef380d9aef6b25`（本行收口前）、修复补丁、RED/GREEN 日志与新鲜 XML、最终守卫 PASS 和两轴 run2 报告。Standards：S1/P2 addressed，硬违反/可信缺陷/启发式新增均0；Spec：S-P2-1 addressed，缺失/越界/新增错误实现均0；最终开放缺陷0。源代码与 GREEN 指纹相同，本次只追加该验收记录，不重复成功构建。
- **风险路由结果**：旧评审缺少完整快照，已重审全部15个净差异文件和合并交互；失败/拒绝后的旧 blocked 误报已最小修复并有6项真实红绿回归；跨工作区构建缓存锁已仅用本次 `--no-build-cache` 参数绕过；两端各1项既有 Windows symlink 跳过继续按已接受的平台例外记实，未改断言。没有未处理的本次新增确定缺陷，前述设备/真实 owner 竞态/性能及历史风险边界仍保留。
- **交付决定**：本次最新用户授权仍为合入 beta、提交恢复标签、推送 dev3、具体中文 PR 到 beta、最后拉取；不强推、不直接合并 PR、不删除276个预存路径。发布前只读核验远端 beta/dev3 仍分别为上述冻结4038aab7/7f8e1a96。实际提交、恢复标签、PR及最终拉取结果由执行输出和最终摘要报告，不为回填坐标制造第二个文档提交。
- **唯一下一步**：发布执行者通过当前 `C128-beta-dev3-20261111` 守卫 finish 原子关闭本任务并立即建立 annotated recovery 标签，随后仅推送 dev3 与本次新标签，创建或更新中文 dev3→beta PR，最后 `git pull --ff-only origin dev3`；若直接操作失败，只定位该原因，不改写历史或将失败说成完成。
