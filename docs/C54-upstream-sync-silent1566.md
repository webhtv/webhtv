# C54：合并上游 `webhtv/webhtv` 分支 `Silent1566` 最新代码（缓存临时文件显式清理修复 + 长按一键全清）

## Recovery anchor

- **目标**：把上游 `https://github.com/webhtv/webhtv` 的 `Silent1566` 分支最新 4 个提交合并进本地 `dev1`（起点 `35a5a63f8a526ca9e6a54d1bf59b941d1f03d840` = `origin/beta` tip），吸收其全部功能性增量，同时保留本地既有能力、本地主题/语言/UI token 合同；合并后完成双 flavor 编译、全量单测、UI token 门禁与 mobile/leanback 双形态实机验证，并原子提交 + 本地恢复 tag（**不推送、不打正式包**）。
- **验收标准**：① 上游 tip `814935ceea6122984607a97f7c33513e5f4f6889` 成为合并提交第二父；② 上游 12 个变动路径的功能性增量全部落地（净增行 = 上游净增行）；③ 本地既有改动零丢失（含 `WebHtvAlertDialogBuilder` 主题契约 3 处调用）；④ 双 flavor Java 与 androidTest Java 编译通过；⑤ 双 flavor 全量 JVM 套件相对 C53 基线零失败（新增用例 = 上游新增 8 例/flavor）；⑥ `scripts/check_ui_tokens.sh` 相对基线零新增违规；⑦ mobile 与 leanback 两形态实机验证「临时文件行清理真实删除」「长按一键全清不弹面板、Toast 报结果、缓存目录清空、设置行自动刷新」「单击仍打开面板」；⑧ 提交 + 本地 annotated recovery tag。
- **lane / scope**：`upstream`；`app/`、`docs/`。
- **任务守卫**：`C54-upstream-sync-silent1566`；开始 HEAD `35a5a63f8a526ca9e6a54d1bf59b941d1f03d840`；初始工作区干净（0 个受保护脏路径）。
- **当前状态**：合并完成（1 个 import 冲突已解决）、复评完成、全部验证通过、实机双形态通过；待 `task_guard.sh finish`。
- **下一动作**：`bash .codex/scripts/task_guard.sh finish --verified <evidence> --commit-message <msg>`（原子合并提交 + recovery tag），不推送。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1`（worktree `/home/maple/Workspace/webhtv/dev1/webhtv`） |
| 任务开始时 HEAD | `35a5a63f8a526ca9e6a54d1bf59b941d1f03d840`（= `origin/beta` tip，Merge PR #428） |
| 上游仓库 | `https://github.com/webhtv/webhtv` |
| 上游分支/tip | `Silent1566` / `814935ceea6122984607a97f7c33513e5f4f6889` |
| 上游本地引用 | `refs/remotes/upstream/Silent1566`（`05469a967…` → `814935cee…`，fast-forward fetch） |
| Merge base | `05469a967ad32818b6e76e080100bfae1787ab76`（上一轮 **C45** 合并的上游 tip） |
| 本地领先量 | `05469a967…HEAD` = 422 文件 / +51077 −734（dev1 自有 + 其它 PR 集成历史） |
| 上游增量 | `05469a967…upstream/Silent1566` = **4 个提交 / 12 个路径 / +585 −49** |
| 初始脏路径 | 无 |
| 合并方式 | `git merge --no-commit --no-ff upstream/Silent1566`（由 task_guard `finish` 创建合并提交） |
| 回滚锚点 | `35a5a63f8a526ca9e6a54d1bf59b941d1f03d840` |

## 上游 commit ledger（4 个提交，全部纳入）

| 完整 commit ID | 标题 | 主要路径 | 处置 |
| --- | --- | --- | --- |
| `f9f47c24ae1d7f4dc15f2f5d1e5ef9e037de305b` | `fix(cache): 修复临时文件显式清理无效并补回长按一键全清` | `CacheCleanupManager`（+251）、`CacheManagementDialog`（+41）、leanback `SettingActivity`（+19）、mobile `SettingFragment`（+19）、`CacheCleanupMode`、`CachePolicyEngine`、`CacheFullCleanupTest`（新增）、`CachePolicyEngineTest`、设计文档 | 纳入 |
| `92f1844342deae8f5494a234627281a807c8bdf3` | `docs(cache): 记录长按一键全清与临时文件修复的设备实测证据` | `docs/CACHE-MGMT-01-cache-management-design.md` | 纳入 |
| `f0da126a2c066f785f90f3f983bafc10a0c349a4` | `fix(cache): 长按一键全清不再打开缓存管理面板` | `CacheManagementDialog`（−61/+36，删除 `showFullCleanup` 三重载与 `ARG_CLEANUP_MODE`）、两个设置入口、三语 `strings.xml`、设计文档 | 纳入 |
| `814935ceea6122984607a97f7c33513e5f4f6889` | `fix(cache): 修复临时文件缺陷回归锁缺失与结果文案语言回退` | `CacheCleanupManager`（`explicitRetention`）、`CacheManagementDialog`（`describe(result, resources)`）、`CacheFullCleanupTest`（5→7 例）、设计文档 | 纳入（上游 tip） |

上游 4 个提交属于同一功能波次（缓存管理「临时文件行清理无效」缺陷修复 + 复刻拆分前「一键全清」的长按快捷入口 + 回归锁与语言路径修正），与本地已有实现无重复、无被取代项，**全部纳入**。

### 上游 net 变更构成（12 个路径）

- **新增 1 个**：`app/src/test/java/com/fongmi/android/tv/cache/CacheFullCleanupTest.java`（7 例）。
- **修改 11 个**：`cache/CacheCleanupManager.java`、`cache/CacheCleanupMode.java`、`cache/CachePolicyEngine.java`、`ui/dialog/CacheManagementDialog.java`、leanback `ui/activity/SettingActivity.java`、mobile `ui/fragment/SettingFragment.java`、三套 `strings.xml`、`test/.../CachePolicyEngineTest.java`、`docs/CACHE-MGMT-01-cache-management-design.md`。
- **删除 0 个**。

## 冲突与解决决定

`git merge` 产生 **1 个冲突文件**（`CacheManagementDialog.java`，`MERGE_HEAD=814935cee`），性质为 import 块并集冲突：

| 冲突位置 | 本地 HEAD | 上游 `Silent1566` | 解决 |
| --- | --- | --- | --- |
| `CacheManagementDialog.java` import 块 | `import com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder;`（C45 已把三处确认框统一为本地主题化 builder，无 `MaterialAlertDialogBuilder`） | `import com.fongmi.android.tv.setting.Setting;`（长按 Toast 需要用 `Setting.wrapLanguage` 取应用内语言资源） | **两者都保留**（按字典序 `setting` 在前、`theme` 在后）；**拒绝带回 `MaterialAlertDialogBuilder`**，本地 `WebHtvAlertDialogBuilder` 3 处调用全部保留 |

无其它冲突、0 个未解决冲突、0 个残留冲突标记（`git diff --name-only --diff-filter=U` 为空；`git grep -nE '^(<<<<<<<|>>>>>>>)'` 命中 0）。

## 零复活 / 零丢失证据（四层）

| 层 | 判据 | 结果 |
| --- | --- | --- |
| 结构性 | 上游独占的 5 个代码/测试路径（`CacheCleanupManager`/`CacheCleanupMode`/`CachePolicyEngine`/`CacheFullCleanupTest`/`CachePolicyEngineTest`）与上游 tip **逐字节相同**；设计文档 `docs/CACHE-MGMT-01-…` 仅多出本任务追加的 **1 行**合并/实机记录 | 代码/测试：`git diff upstream/Silent1566 -- <5 路径>` 为空 ✅；文档：`1 insertion(+) ✅ |
| 净值 | 合并净差 = `HEAD…worktree` `+586 −49 / 12 文件`（= 上游 `+585` + 本任务文档追加 1 行），上游 `05469a967…upstream/Silent1566` 为 `+585 −49 / 12 文件` | 差值可解释 ✅ |
| 行级（外来内容） | 合并新增的 585 行，逐行在 `upstream/Silent1566` 全树 `git grep -F` 命中；找不到的 = **0** | 0 行外来内容 ✅ |
| 行级（本地丢失） | `05469a967…HEAD` 的本地新增行逐行在合并树中命中；真实缺失 = **0**（脚本报的 7 处为 `+++ /dev/null` 头部解析噪声） | 0 行丢失 ✅ |
| 删除面 | `git diff --diff-filter=D --name-status HEAD` | 空（无路径被删除）✅ |
| 复活面 | 上游 49 处删除行的“复活候选”28 处全部为**移动/重排后的同文本**（循环体移入 `else` 分支、`describe()` 从 `notifyOutcome` 抽为静态方法），`git diff upstream/Silent1566` 为空即证明无复活 | 0 复活 ✅ |

> 补充判据：合并后 `git diff upstream/Silent1566 -- <12 路径>` 只剩本地既有差异（`CacheManagementDialog.java` 4 行 = 主题化 builder ×3 + import；两个设置入口/三套 `strings.xml` 为本地历史增量；设计文档 1 行 = 本任务合并记录），证明「上游增量全落地、本地历史未回退」。

## 复评（第 1 轮：合并结果逐文件复核）

复评对象 = 上游 4 个提交带入的 12 个路径 + 与本地交集路径。

| 复评点 | 结论 | 证据 |
| --- | --- | --- |
| 上游新增代码用到的 API 在本地 HEAD 均存在且签名匹配 | 通过 | `CacheCleanupManager.execute(plan, reason, callback)` / `isRunning()`、`CacheInventory.measureRoots(id, roots)`、`CacheRoot.tree(File)`、`CacheModuleRegistry.modules(File)`、`CachePathSafety.isSymbolicLink`、`CacheTempFilePolicy.isInUse`、`Updater.isDownloading`、`ApkUrlPush.isActive`、`CachePolicyEngine.directCleanupStatus`、`Setting.wrapLanguage(Context)` 全部实测存在 |
| `CacheCleanupResult` 分量序 | 通过（1 项观察，见 F2） | 记录为 `(id, status, bytesBefore, bytesAfter, deletedFiles, skippedFiles, warnings)`；FULL 传 `after.files()` 作 `skippedFiles` |
| 回调线程 | 通过 | `run()` 末尾 `App.post(() -> callback.accept(result))` → `Notify.show(String)`→`Toast` 在主线程，无跨线程崩溃 |
| `Notify` 文案语言路径 | 通过（上游 tip 已修正） | `describe(result, resources)` 由调用方给资源；面板给 `getResources()`，长按给 `Setting.wrapLanguage(App.get()).getResources()` |
| 枚举新增 `FULL` 的穷尽性 | 通过 | `CachePolicyEngine.modules(mode)` 的 `switch` 已补 `case FULL -> EnumSet.allOf(...)`；全仓无 `CacheCleanupMode` 的 `ordinal()` 索引用法；`CacheAutoPlanAndTempFamilyTest.everyAutomaticPlanRespectsTheRegistryDeclaration` 遍历 `values()` 仍通过（`automaticPlan()` 按 `allowsAutomatic` 过滤） |
| 本地主题契约未回退 | 通过 | `CacheManagementDialog` 3 处 `WebHtvAlertDialogBuilder`、0 处裸 `MaterialAlertDialogBuilder` |
| UI token 合同 | 通过 | 增量不含任何布局/颜色资源；门禁与基线逐项一致 |
| 三语字符串 | 通过 | `cache_cleanup_full_started` 在 `values`/`zh-rCN`/`zh-rTW` 齐备 |
| 与自动清理调度/分级清理的关系 | 通过 | `CacheAutoCleanupPolicy.cleanupMode()` 只会产出 `LIGHT`/`STANDARD`，FULL 不进自动链路；`explicitRequest()` 只让 `MODULE`/`FULL` 忽略后台保留期 |
| 长按与单击关系（设计 §15.2） | 通过 | `onCacheLongClick` 返回 `true` 消费长按（AOSP `View` 不会在同一次手势后再派发单击）；单击仍 `onCache` → 面板；实机双向验证 |

**真实缺陷：0**。发现 4 项观察（按 `AGENTS.md` §2 记录、不扩大改动面）：

- **F1（回归锁缺口，真问题但非功能缺陷）**：上游评审记录声称「把行内按钮改回 24 小时窗口仍会全绿」的缺陷已被锁住，但**负对照实测显示只锁住了共享判定函数、没锁住调用点**：把 `case TEMP_FILES` 调用点改回 `TEMP_RETENTION_MS` 字面量（即精确复现用户报告的缺陷）后，`com.fongmi.android.tv.cache.*` 全部用例仍 **BUILD SUCCESSFUL**（0 失败）；只有把 `explicitRequest()` 退回 `mode == MODULE` 时 `CacheFullCleanupTest.onlyExplicitRequestsIgnoreTheBackgroundRetentionWindow` 才失败（7 例 1 失败）。调用点级回归锁缺失，单元层无法覆盖 `executeCleanup()`（依赖 `App.get().getCacheDir()`），该缺陷的决定性验证只能落在实机（本轮已双形态实测）。**处置：记录，不改上游设计**。
- **F2（文案语义）**：`cleanEverything()` 把清理后剩余文件数传给 `skippedFiles`；仅当状态为 `PARTIAL`（播放中保留的缓存根/失败）时文案会显示「跳过 N 个文件」，此时语义等价于「保留/剩余 N 个文件」。`COMPLETED` 路径不受影响。**处置：记录**。
- **F3（交互）**：清理进行中重复长按会被 `CacheCleanupManager.isRunning()` 静默丢弃（只有首次长按会提示「正在清理全部缓存…」）。上游文档已声明该权衡（清理通常 200–650ms）。**处置：记录**。
- **F4（全清副作用）**：FULL 会连缓存根下应用自有的状态文件一起清（实测清掉 `webhtv-debug-log.txt`、`episode_positions.json`、`flag_preferences.json`、`proc_auxv`、`tv.lck`、`following.lck`、WebView/chaquopy 目录），只保留 `mpv-playback-recovery.*`、进行中的传输与被保留的播放缓存根、以及运行中加载器的脚本（实测只剩 `cache/jar/<active>.jar`）。与拆分前 `FileUtil.clearCache()`（`Path.clear(Path.cache())`）语义一致，且冷启动实测无崩溃、缓存自动重建。上游另记录了 NanoHTTPD 上传分片的相邻风险。**处置：记录**。

## 验证记录

### 1. 单次 Gradle 调用（编译 + 双 flavor 全量单测）

```bash
bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac \
  :app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac :app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac \
  :app:testLeanbackArm64_v8aDebugUnitTest :app:testMobileArm64_v8aDebugUnitTest
```

`BUILD SUCCESSFUL in 2m 12s`、`EXIT=0`、6 个目标任务全部实际执行。JUnit XML 统计：

| flavor | suites | tests | failures | errors | skipped | C53 基线 | 差值 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| leanback | 650 | **4139** | **0** | **0** | 2 | 649 / 4131 | +1 suite / +8 例 |
| mobile | 726 | **4977** | **0** | **0** | 2 | 725 / 4969 | +1 suite / +8 例 |

+8 例/flavor = 上游新增 `CacheFullCleanupTest` 7 例 + `CachePolicyEngineTest.fullPlanCoversEveryModuleExactlyOnce` 1 例；无失败、无错误、跳过数与基线一致。

### 2. 定向负对照（证伪能力）

| 变异 | 期望 | 实测 |
| --- | --- | --- |
| `explicitRequest()` 退回 `mode == MODULE`（等价于 FULL 出现前的语义） | 新锁失败 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests CacheFullCleanupTest` → `7 tests completed, 1 failed`，失败用例 = `onlyExplicitRequestsIgnoreTheBackgroundRetentionWindow` ✅ |
| 仅 `case TEMP_FILES` 调用点改回 `TEMP_RETENTION_MS`（精确复现用户报告的缺陷） | —— | `--tests "com.fongmi.android.tv.cache.*"` 全绿 → 调用点无单元级锁（见 F1 观察） |

两轮变异后均以备份文件字节还原（`sha256sum -c` OK），未污染合并结果。

### 3. UI token 门禁

```text
UI_TOKEN_BASELINE layouts=385 hex_layouts=1 drawables=554 hex_drawables=0 colors=59 hex_colors=0 allowlisted=190
UI_TOKEN_SCOPE    stage=A violations=1 legacy=0
UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28
UI_TOKEN_STATUS   PASS
```

唯一命中 `app/src/mobile/res/layout/item_following.xml`（**不在本次改动集内**），与 C50/C52/C53 基线**逐项一致** → 相对基线零新增违规。

### 4. 静态与结构校验

- `git diff --check` 退出码 **0**；冲突标记命中 **0**；`git diff --name-only --diff-filter=U` 为空；
- 合并净差与上游净差相等（`+585 −49 / 12`）；合并新增行外来内容 = 0；本地新增行丢失 = 0；
- 无新增/未跟踪文件，改动全部落在 `app/`、`docs/` scope 内。

### 5. 实机验证（`192.168.50.3:5555`，LIO-AN00 / Android 9 / 1920×1080 / 280dpi）

打包与安装：`bash scripts/build_arm64_debug_install.sh --flavor mobile|leanback --serial 192.168.50.3:5555` → 两次 `BUILD SUCCESSFUL` + `Success`（**覆盖安装 `-r`，未卸载任何包**）。安装前用 `apksigner` 核对设备现有包与本地 debug keystore 证书 SHA-256 均为 `32d245c5…df2e`，故按项目约定直接覆盖安装；仅改动本项目包 `com.silent.android.webhtv`，未触及同机其它包。`versionText` 回显 `5.6.0-202610091644`（mobile）/ `5.6.0-202610091649`（leanback）与本机构建时间一致。

**mobile flavor**

| 场景 | 方法 | 结果 |
| --- | --- | --- |
| 用户报告的缺陷：临时文件行清理无效 | 预置新建 `cache/webhtv-sync-test1.zip`（3 MiB）+ `cache/js/zz-idle-test.js`（64 KiB）+ `cache/stray-test.bin`（256 KiB），面板「临时文件」行如实报告 `3 MB · 1 个文件`，点该行「清理」→ 确认框 → 确定 | 状态行 **`清理完成 释放 3 MB · 删除 1 个文件`**，总量 `46.9 MB → 43.9 MB`；`run-as` 复核 zip **已删除**，`js/zz-idle-test.js` 与 `stray-test.bin` **原样保留**（模块隔离未破坏）✅ |
| 长按一键全清 | 设置页缓存行（`id=cache`，中心 `(960,733)`）只发一次长按 `input swipe x y x y 1600`（不预先单击） | **不弹面板**：`mResumedActivity` 仍为 `HomeActivityCurrent`，无障碍树无面板节点；+1s 抓屏同时可见设置页在前台、缓存行已刷新为 `610.7 KB / 64 MB`、Toast **`清理完成 / 释放 43.3 MB · 删除 470 个文件`** ✅ |
| 全清效果 | 长按前后统计 `cache` 目录条目数与占用 | 23 项 / 45M → **1 项（`jar`，运行中加载器的脚本被保留）/ 624K** ✅ |
| 机器可读 journal | `shared_prefs/…_preferences.xml` 的 `cache_mgmt_cleanup_history` | `{"mode":"FULL","reason":"shortcut","bytesBefore":46039607,"bytesAfter":625387,"deletedFiles":470,"status":"COMPLETED","warnings":[]}`（与 Toast 的 43.3 MB / 470 完全一致）✅ |
| 单击无回归（设计 §15.2） | 再次单击缓存行 | 面板正常打开：`title=缓存管理`、`summary=共 610.7 KB / 系统配额 64 MB` ✅ |
| 确认流程未回归（§15.1） | 观察模块级确认框 | 默认焦点仍在「取消」✅ |
| 全清后冷启动 | `am force-stop` + 冷启动 + 18s | 进程正常存活、`logcat` 无 `FATAL EXCEPTION`/`ANR`；应用自行重建 `WebView`、`chaquopy`、`data`、`jar`、`plugin-preheat`、`proc_auxv`、`py`、`tv.lck`、`following.lck`、`webhtv-debug-log.txt` ✅ |

**leanback flavor**（同一设备；TV 设置页 `id=cache` 中心 `(496,991)`）

| 场景 | 方法 | 结果 |
| --- | --- | --- |
| 临时文件行清理（TV 面板） | 预置 `cache/webhtv-sync-tv1.zip`（2 MiB）+ `cache/js/zz-idle-tv.js` + `cache/stray-tv.bin`，面板「临时文件」行 `2 MB · 1 个文件` → 「清理」→ 确定 | 状态行 **`清理完成 释放 2 MB · 删除 1 个文件`**，总量 `10 MB → 8 MB`，zip 已删除、js 与 stray 保留 ✅ |
| 长按一键全清 | 长按缓存行（1600ms） | **不弹面板**、`mResumedActivity` 仍为 `SettingActivity`；Toast **`清理完成 / 释放 7.4 MB · 删除 162 个文件`**，缓存行自动刷新为 `610.7 KB / 64 MB` ✅ |
| 全清效果 | 长按前后 | 14 项 / 8.3M → **1 项（`jar`）/ 624K** ✅ |
| journal | 同上 | `{"mode":"FULL","reason":"shortcut","bytesBefore":8344355,"bytesAfter":625387,"deletedFiles":162,"status":"COMPLETED","warnings":[]}` ✅；另有 `{"mode":"MODULE","bytesBefore":2097152,"deletedFiles":1,…,COMPLETED}` 对应临时文件行清理 ✅ |
| 单击无回归 | 再次单击缓存行 | 面板打开（`共 610.7 KB / 系统配额 64 MB`）✅ |
| 崩溃扫描 | `logcat -d | grep -E "FATAL EXCEPTION|ANR in com.silent"` | 0 命中 ✅ |

**测试装置说明（设备状态已还原）**：该机 `home_button` 配置为 `0,8,6,1,2,3,4,7`，首页按钮行只渲染前 7 个（「设置」在行外），MENU 键打开的是分类/站点筛选而非首页菜单弹窗，`SettingActivity` 未导出无法外部 `am start`。为触达 TV 设置页，临时把设备 `home_button` 置为 `7`（等价于用户在「首页按钮」里只勾选设置），测试后已 `sed` 还原为 `0,8,6,1,2,3,4,7` 并删除临时 prefs 备份文件，冷启动复核首页恢复为 7 个按钮。测试夹具由清理自身删除；设备最终重新覆盖安装 mobile flavor（与测试前形态一致）。

## 改动清单

- 合并提交（由 `task_guard.sh finish` 生成）：12 个路径，第二父 `814935ceea6122984607a97f7c33513e5f4f6889`。
- 本任务文档：`docs/C54-upstream-sync-silent1566.md`。
- `docs/CACHE-MGMT-01-cache-management-design.md` 追加本轮「合并到 dev1 + 双形态实机」记录（见该文档 Recovery anchor 首条）。

## 回滚

- 任务前回滚锚点：`35a5a63f8a526ca9e6a54d1bf59b941d1f03d840`。
- 本次为单个 merge commit：`git revert -m 1 <merge-commit>`，或重置到锚点。
- `refs/remotes/upstream/Silent1566` 为本仓既有上游引用，不推送；本次未推送任何远端、未创建 PR、未打正式包。

## 交付坐标与下一步

| 项 | 值 |
| --- | --- |
| 合并提交 | `45bffbb860dc22267052c21238e7cf63e8adfd8b`（第二父 `814935ceea6122984607a97f7c33513e5f4f6889`） |
| Recovery tag | `recovery/C54-upstream-sync-silent1566/20261009170756-45bffbb860dc`（annotated，指向合并提交） |
| 合并前锚点 | `35a5a63f8a526ca9e6a54d1bf59b941d1f03d840` |
| 本任务分支 | `dev1`（未推送） |
| 远端/PR | 未推送任何远端、未创建 PR、未打正式包（按 `AGENTS.md` §6 需显式授权） |
| 设备收尾 | `192.168.50.3:5555` 已还原测试装置（`home_button` = `0,8,6,1,2,3,4,7`、临时 prefs 备份已删、/sdcard 抓屏与 UI dump 已删），并重新覆盖安装 mobile flavor 与测试前形态一致 |

- 状态：合并完成（1 冲突解决）+ 复评（真实缺陷 0、观察项 4）+ 双 flavor 编译/全量单测零新增回归 + UI token 零新增违规 + 双形态实机主路径与冷启动通过 + 定向负对照完成；已原子提交并打本地恢复 tag。
- 下一动作（需用户授权后才可执行）：若需把该合并交付到远端，先推送 `dev1` 并创建 PR 到 `beta`（只创建不合并）；若用户要求收口 F1 的调用点级回归锁，则作为独立小任务（例如为 `cleanModule(TEMP_FILES, MODULE)` 增加可注入 cache 的覆盖或用本地既有 Source 锁约定锁定调用点），不在本次合并内扩大改动面。
