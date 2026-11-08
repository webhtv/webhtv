# C63：dev4 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码安全合入 `dev4`，不得复活 beta 已移除/回退内容；评审合并结果、本地已提交未推送的 TV 对话框取色修复（`4c5f4dc8421`）及本轮修复，验证通过后提交、推送并创建中文 `dev4 -> beta` PR，**只创建不合并**。
- **允许路径**：`app/`、`docs/`；任务开始时工作区干净，**0 个预先存在脏路径**。
- **分支与回滚锚点**：`dev4`；第一父 `4c5f4dc842126a079922c74bbeccceab0004733e`（本地已提交未推送 tip）。
- **合并第二父（本轮 beta 权威 tip）**：`92a0ca0bbe50da70768b7245ed6dd17a51bffcea`。
- **当前状态**：已交付。自动合并无冲突、0 未解决冲突；三轮代码评审未发现必修缺陷；双 flavor 定向 JVM 测试全绿（leanback 192 例、mobile 180 例，0 failure / 0 error，见 §5.1）；合并提交、recovery tag、推送与 PR #439 均已完成，PR 保持 OPEN。
- **下一动作**：无（已交付）。

## 1. 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev4` |
| 合并前 HEAD（第一父） | `4c5f4dc842126a079922c74bbeccceab0004733e` |
| 合并前 `origin/dev4` | `4b35e0720c118bd71464abf47dab5a1b55998ad3`（本地领先 2：`af9a18fa470` 合并提交 + `4c5f4dc8421` 主题修复，均未推送） |
| 合并前 `origin/beta` | `af9a18fa47048893b438ab24c9b338041fa306c1`（= 合并基） |
| 最终 `origin/beta` | `92a0ca0bbe50da70768b7245ed6dd17a51bffcea`（PR #438） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`，自动合并无冲突 |
| 未解决冲突 | 0（`git diff --cached --diff-filter=U` 为空） |
| 合并索引树 | `c30e030610e812968c0992195ccea9836d3c657f` |
| 合并带入路径 | 10（5 生产 + 3 测试 + 2 文档），与 beta 增量逐项一致 |
| 本地净差异 | 5 路径（主题修复 4 + 本任务文档 1） |

### beta 增量 disposition

以下提交全部通过 merge 第二父纳入，**不执行 cherry-pick，不把已回退内容单独带回**：

| 完整 commit ID | 处置 |
| --- | --- |
| `92a0ca0bbe50da70768b7245ed6dd17a51bffcea` | 最新 beta tip（PR #438 合并提交），作为合并第二父 |
| `04b32d4e14907bdd72ccf1349d641dc11acf7a9a` | C62 交付坐标文档，随第二父纳入 |
| `11578a6eee41a82748a89ddfceafd65e4a7d5cc7` | dev1 合并 `origin/beta`（PR #437）的合并提交，随第二父纳入 |
| `8daf00907e9066c1f6b2c0319e273f9181e536a6` | Android 7.1.2 真机验证记录，随第二父纳入 |
| `29c2810638f7269cab1d17151946edc9bd3352d9` | Android 7 文档措辞对齐，随第二父纳入 |
| `0b036f9d0187dcee2a6d28f24fa835d9996d92cf` | 三个已修缺陷收尾 + MPV 阻塞重述，随第二父纳入 |
| `8bb62a7906e55b4af204048f2a10387ed8b2cb21` | `MpdSanitizer` 改用 API 1 的 `StringBuffer` matcher 重载，随第二父纳入 |
| `656d3dcbc8ee09f655a83abc5aabf56a56547644` | `LabRunner` 用 API 1 的 `exitValue()` 替代 `Process#isAlive()`（API 26），随第二父纳入 |
| `22fe625814b95890942b924ac2e1ce55c092fadc` | `libmitv.so` 行订正，随第二父纳入 |
| `9564c3e58d6cdf535fd9d8f4c471ae4c513dcf04` | Android 7 真机证据与 release 包原生库复核记录，随第二父纳入 |
| `4e4bfffcb67a6391f2dfde9df5f8d4151a4c35e2` | `ViewCompat.setImportantForAutofill` 守卫 `View#setImportantForAutofill`（API 26），随第二父纳入 |

## 2. 复活与净差异审计

- **beta 侧删除面为空**：`git diff --diff-filter=D --name-only af9a18fa470..origin/beta` 无输出，本轮 beta 增量没有删除任何路径，不存在需要「保持删除」的文件。
- **dev4 侧删除面为空**：`git diff --diff-filter=D --name-only origin/beta...dev4` 无输出。
- **beta 增量内无回退提交**：`git log --grep=revert -i af9a18fa470..origin/beta` 无输出；上一次主题相关的回退提交 `8ba33b11fc8`（全屏播放画面不加焦点环）已是合并基 `af9a18fa470` 的祖先，两条分支同源，不存在被顺带重新提交的可能。
- **合并树未删除 beta 内容**：`git diff --cached --diff-filter=D --name-only origin/beta` 为空。
- **合并树相对 beta 的净差异**（`git diff --cached --name-status origin/beta`）恰为本地 5 路径，无 beta 文件被改写或复活：

```text
M  app/src/main/java/com/fongmi/android/tv/theme/ThemeController.java
M  app/src/test/java/com/fongmi/android/tv/theme/ThemeBinderContractTest.java
M  app/src/test/java/com/fongmi/android/tv/theme/ThemeControllerContractTest.java
A  app/src/testLeanback/java/com/fongmi/android/tv/theme/TvDialogPaletteModeTest.java
A  docs/TV-DIALOG-PALETTE-NIGHT-20261012.md
```

- 净差异不含 native、依赖锁、Release 产物或仓库外路径。

## 3. 代码评审

### 第 1 轮：本地已提交未推送的 TV 对话框取色修复（`4c5f4dc8421`）

评审对象：`ThemeController.java` 的 `resolvedDark`/`compiledDarkPalette`/`frozenPalette`/`resolveWith` 由「应用上下文 + 原始系统夜间位」改为「真正 inflate 的 context」，以及三处测试改动。

结论（通过）：

- 探针一致性：`applyFromPreferences(activity)` 同时把 `activity` 交给 `frozenPalette(activity)` 与 `resolveWith(profile, activity)`，`baseline` 与 `current` 不可能再取自两张不同的表，正是本次缺陷的根因面。
- 调用面完整：全仓库 `applyFromPreferences(...)` 的生产调用方只有 `app/src/{leanback,mobile}/.../ui/base/BaseActivity.java` 与 `FollowingActivity`，**全部传入 `this`（Activity）**；`resolveFromPreferences()` 与 `refresh()` 无生产调用方（仅设备测试探针），`null` 分支保持改动前语义。
- 时序正确：`App.java:121` 在进程启动期调用 `applyNightModeToApp()`，早于任何 Activity 创建，因此 Activity 的资源配置已反映强制夜间模式，`isNight(context)` 与 `ContextCompat.getColor(context, ...)` 读到的是同一张表。
- `darkPaletteFor()` 优先级未变：编译表可识别时仍然权威，不可识别时回落历史 mode/uiMode 规则。
- 契约测试未被弱化：源码断言由旧签名更新到新签名，并**新增**「探针不得以应用上下文为首选来源」的否定断言（`assertFalse(controller.contains("Context context = App.get();"))`）。
- 新增 `TvDialogPaletteModeTest` 三项断言有效：面板必须等于 Activity 解析出的表、info/提示/描边按钮文字对面板 ≥ 4.5:1、显式日/夜 context 下探针跟随入参（补偿 Robolectric 复现不出 API < 31 的设备差异）。

### 第 2 轮：beta 增量生产代码

| 变更 | 评审结论 |
| --- | --- |
| `LabRunner.isAlive(Process)`（新增，包内可见） | `exitValue()` 抛 `IllegalThreadStateException` ⇒ 存活，语义与 `Process#isAlive()` 等价且自 API 1 可用；`null` 与其它异常一律判死，不会让调用方拿到「幽灵存活」。 |
| `LabTerminalActivity` 三处改走 `LabRunner.isAlive(...)` | `process == null \|\| !process.isAlive()` → `!LabRunner.isAlive(process)`、`process != null && process.isAlive()` → `LabRunner.isAlive(process)`，逐处等价，无行为漂移。 |
| `MpdSanitizer` 五处 `StringBuilder` → `StringBuffer` | 仅替换 `appendReplacement`/`appendTail` 的接收者类型，语义一致（局部单线程变量），把 API 34 重载换成 API 1 重载。 |
| `GitCloudDialog` / `RemoteTrustDialog` 改用 `ViewCompat.setImportantForAutofill` | API 26 调用被兼容层吸收，低版本 no-op；两文件各只保留一处调用（与新增契约测试的 `== 1` 断言一致）。 |

beta 增量与本地主题改动**无文件重叠**，且主题取色只影响 `LightDialog`/`ThemeBinder` 通道，与 Lab/MPD/对话框 autofill 路径无交叉语义。

### 第 3 轮：合并语义交叉复评

- beta 增量新增的 `Android7ApiCompatContractTest` 是**全仓源码扫描**（遍历 `app/src/main/java` 的所有 `.java`，要求 `Matcher#appendReplacement/appendTail` 的接收者不得声明为 `StringBuilder`，且扫描量 ≥ 30）。本轮已确认本地主题改动不引入该模式，测试在合并树上实跑通过（§5）。
- 合并后 `LightDialog` 家族面板取表、`ThemeBinder` 基线改写通道、焦点环、Lab 运行时、MPD 清洗、更新判定与 Room 完整性兜底互不影响；没有发现新的必修问题。
- 三轮评审均未发现必修缺陷，评审通过，无需为本轮引入修复补丁。

## 4. 相邻但未修复（超出本次范围，仅报告）

1. `RemoteTrustDialog` / `GitCloudDialog` 内部仍有一套**固定浅色卡片**设计（各 45+ 处 `Color.parseColor` / `Color.BLACK` 硬编码，如 `input.setTextColor(Color.BLACK)` 配 `setBoxBackgroundColor(Color.WHITE)`）。在深色或自定义主题下这类运行时构造的输入框前景不随主题走；本轮合并只在这两个文件各改了一行 API 兼容调用，未触碰配色。该设计由来已久（`74c5c20b78d` / `9fe16f08adc`），属主题系统范围，建议单独建任务。
2. `ThemeController.refresh()` 仍按 `Resources.getSystem()` 的夜间位取表且当前无调用方；若将来接入 UI 路径会重新造出「无 Activity 上下文」的同类分叉。
3. beta 带入的 Android 7 排查结论记录了一项**未实施**的硬缺口：内置 MPV 内核（`libmpv.so` 依赖 Vulkan 1.1 符号）在 API < 28 上无法加载，低版本走回退内核、不崩溃。属原生/上游范围，与本轮合并无关。

## 5. 验证记录

### 5.1 合并树定向 JVM 测试

同一套过滤条件（`com.fongmi.android.tv.theme.*` + `.compat.*` + `.lab.*` + `.player.extractor.*`）在两个 flavor 上各跑一次，覆盖本轮全部被改动的生产/测试范围（主题取色、Android 7 兼容契约、Lab 运行时、MPD 清洗）：

| 任务 | 结果 |
| --- | --- |
| `:app:testLeanbackArm64_v8aDebugUnitTest` | `BUILD SUCCESSFUL in 4m 19s`，30 个测试类 **192 例，0 failure / 0 error** |
| `:app:testMobileArm64_v8aDebugUnitTest` | `BUILD SUCCESSFUL in 2m`，25 个测试类 **180 例，0 failure / 0 error** |

关键用例（`tests / failures / errors`）：

| 测试类 | leanback | mobile |
| --- | --- | --- |
| `TvDialogPaletteModeTest`（本轮主题修复新增） | 3 / 0 / 0 | 仅 leanback 源集 |
| `ThemeControllerContractTest` | 11 / 0 / 0 | 11 / 0 / 0 |
| `ThemeBinderContractTest` | 26 / 0 / 0 | 26 / 0 / 0 |
| `Android7ApiCompatContractTest`（beta 带入，全仓源码扫描） | 2 / 0 / 0 | 2 / 0 / 0 |
| `LabRunnerProcessCompatTest`（beta 带入） | 2 / 0 / 0 | 2 / 0 / 0 |
| `MpdSanitizerMatcherMethodContractTest`（beta 带入） | 2 / 0 / 0 | 2 / 0 / 0 |

其中 `Android7ApiCompatContractTest` 遍历 `app/src/main/java` 全部 `.java`，在合并树上通过即证明本地主题改动没有引入 API 34 的 matcher 重载；`TvDialogPaletteModeTest` 在合并树上通过即证明 beta 增量没有改变对话框面板取表。

### 5.2 androidTest 源集编译

```text
.\gradlew.bat --console=plain :app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac :app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac
BUILD SUCCESSFUL in 32s   (122 actionable tasks: 10 executed, 112 up-to-date)
```

双 flavor 的设备测试源集（含调用 `ThemeController.applyFromPreferences(null)` / `resolveFromPreferences()` 的 `TvFocusRingResolutionDeviceTest`）在合并树上编译通过，取色探针的签名放宽（`private` → 包内可见）与新增 `Context` 入参没有破坏任何既有调用方。

### 5.3 结论

三轮评审未发现必修缺陷，双 flavor 定向 JVM 测试与 androidTest 编译全部通过，本轮合并可直接交付，无需额外修复补丁。

## 6. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交（双父） | `a25ac9dd72d5ca0252242de67221451ddeaafd20`（第一父 `4c5f4dc842126a079922c74bbeccceab0004733e`；第二父 `92a0ca0bbe50da70768b7245ed6dd17a51bffcea` = beta tip） |
| recovery tag | `recovery/beta-sync-review-dev4-20261012/20261012130131-a25ac9dd72d5` |
| 推送 | `git push origin dev4` → `4b35e0720c1..a25ac9dd72d  dev4 -> dev4`；recovery tag 推送成功 |
| PR | [#439](https://github.com/Silent1566/webhtv/pull/439)（`dev4` → `beta`，**只创建，未合并**） |
| PR 状态校验 | `state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`、`changedFiles=6`、`baseRefName=beta`、`headRefName=dev4` |
| PR 文件集校验 | `gh api repos/Silent1566/webhtv/pulls/439/files` 与 `git diff --name-only origin/beta HEAD` **逐项一致**（6/6） |
| 收尾复核 | `git merge-base --is-ancestor origin/beta HEAD` 为真；`MERGE_HEAD` 已清除；`git status --porcelain` 干净 |
| 远端 tip | `git ls-remote origin refs/heads/beta` = `92a0ca0bbe50da70768b7245ed6dd17a51bffcea`（与合并第二父一致）；`refs/heads/dev4` = `a25ac9dd72d5ca0252242de67221451ddeaafd20` |

> 环境说明：本会话无可用 bash（仅 WSL `bash.exe`，`LxssManager`/`vmcompute` 未运行，返回 `HCS_E_SERVICE_NOT_AVAILABLE`），`.codex/scripts/task_guard.sh` 无法执行。按其意图手工执行：仅暂存本任务声明范围内的路径（合并索引 + 本文件）、原子提交、创建本地带注释 recovery tag。

## 7. 回滚

- 回滚：对本轮合并提交执行 `git revert -m 1 <merge-commit>`，恢复到第一父 `4c5f4dc842126a079922c74bbeccceab0004733e`。
- 无数据库迁移、网络协议、ABI、native、Release 产物或用户偏好格式变化。
- PR 必须保持 OPEN，不自动合并。
