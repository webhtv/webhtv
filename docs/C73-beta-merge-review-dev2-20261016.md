# C73 dev2 合并 origin/beta 并复评（2026-10-16）

审计日期：2026-10-16（Asia/Shanghai）
任务 ID：`C73-beta-merge-review-dev2-20261016`
分支：`dev2`
范围：`app`、`docs`

## 1. 目标与判定标准

把 `origin/beta` 最新代码合入 `dev2`，**不得把远端已移除/回退的提交顺带带回来**；随后评审本轮已修改的代码（含 dev2 已提交未推送的 4 个提交）与合并交互，发现问题就最小修复并验证，通过后再次评审，循环至通过；最后提交、推送、创建到 `beta` 的 PR（中文描述，只创建不合并）。

验收标准：

1. 合并提交为双父合并，第二父等于提交前最新 `origin/beta` tip。
2. 合并结果相对 `origin/beta` 的净差异**只**包含 dev2 自身的本地改动，不含任何 beta 已删除内容。
3. 零复活有可证伪证据（检测器自检必须能报出注入的探针）。
4. 定向测试、双 flavor Java 编译、`git diff --check`、`gradlew clean` 全部通过。
5. PR `base=beta`、`head=dev2`、`state=OPEN`、`mergedAt=null`，描述为中文。

## 2. 合并前的仓库状态

| 项 | 值 |
| --- | --- |
| 分支 / HEAD | `dev2` / `933ab6d73e27477c62e84dfb2c3ad546b4741043` |
| `origin/dev2` | `40c0ce860e3acd919bc6ef50660f2fafad86835d`（本地领先 4） |
| `origin/beta` tip | `12d7d559e5c87f196c449e5fadeb9477db70a3fd` |
| 合并基 | `40c0ce860e`（= `origin/dev2`，即 C71 的文档提交） |
| 领先 / 落后 | `4 / 14` |
| 开工前脏文件 | `.wh_diag/`（124 个未跟踪诊断产物，由 `.git/info/exclude` 排除，已列为保护路径） |

### 2.1 本地已提交未推送的改动（评审对象，4 个提交）

| 提交 | 主题 | 路径 |
| --- | --- | --- |
| `b1fe3d5686` | `fix(player)` SiteViewModel 在 `onCleared()` 后把提交降级为已取消请求 | `SiteViewModel.java`、`SiteViewModelSubmitAfterClearTest.java` |
| `317fa9021d` | `fix(py)` 缺失 `getDependence` 的 py 站源不再被静默降级为空 | `PyLoader.java`、`app.py`、`test_app_bridge_contract.py` |
| `96771c8a98` | `docs(spider)` 补充 Python 站源加载与缓存排查说明 | `docs/integration/spider.md` |
| `933ab6d73e` | `fix(py)` 补齐 `app.py` 三个桥接助手缺失的失败防护 | `app.py`、`test_app_bridge_contract.py` |

## 3. 零复活 / 零丢失证据（五层）

| 层 | 方法 | 结果 |
| --- | --- | --- |
| 1. 合并冲突 | `git merge --no-commit --no-ff origin/beta` | **0 冲突** |
| 2. 树对象中性 | 合并结果树 vs `origin/beta` 树 | 差异恰为 dev2 自身 6 路径（方向相反），**零内容合并** |
| 3. 文件级零新增 | `git ls-tree -r --name-only` 整树 `comm`（规避 MSYS 把 `:` 当路径分隔符的假阳性） | beta 5070 路径**缺失 0**；仅新增 3 条，全部为本任务自身的测试文件 |
| 4. 文件级复活扫描 | 184 个 revert 类提交删除的 66 个路径 ∩ 合并树 ∖ beta 树 | **0** |
| 5. 行级复活扫描 | 184 个 revert 提交删除的 7329 条实质行 vs 本轮净新增 407 条实质行 | **0**（排除 import/package 样板行） |

**检测器可证伪自检**：把一条确实「在 revert 集且不在净差异」的行（`String segmentUri, int segmentIndex, String playlistUrl) {`）注入伪造集合，检测器必须报出——**PASS**，排除空转。

**评估索引行 ID 集合比对**：`docs/upstream-player-dependency-merge-assessment-2026-08-20.md` 的 `| \`Cxx\` |` 行 ID 集合，beta = 86、合并后 = 86，**beta 零丢失、零多余**（该文件本轮是普通三方合并，无 union 冲突）。

> 方法与 C71 一致：revert 检测器只匹配**主题行**并锚定开头（`^(Revert|revert|回退|撤销|剔除)`），避免本任务自己的合并提交正文含「回退」字样而被误判为 revert 提交。

## 4. 评审发现与修复

### 4.1 第 1 轮：发现 2 项必修

**P1 — `PyLoader` 初始化失败在默认设置下变成「完全静默」（可观测性回归）。**

- 归属：本轮评审对象 `317fa9021d`。
- 现象：该提交把 `catch (Throwable e)` 里的 `e.printStackTrace()` **替换**为两条 `SpiderDebug.log(...)`。
- 根因：`SpiderDebug.log(tag, …)` 的每一条重载首行都是 `if (!DebugLogStore.acceptsTag(tag)) return;`，而 `acceptsTag` = `enabled && …`；`enabled` 来自 `DebugLogStore.restoreEnabled()` → `Prefers.getBoolean("debug_log")`，**默认值 false**（`Prefers.getBoolean(String)` 默认 false），只有用户在设置里手动打开「调试日志」才为真。
- 影响：默认设置下 py 站源初始化失败既不进诊断日志、也不再进 Logcat —— 而这正是「首页/分类/搜索全空却没有任何线索」的唯一解释点，等于把该提交要修的问题从「只进 Logcat」退化成了「哪都没有」。兄弟实现 `JsLoader` 的同类 catch 同时保留两条通道。
- 修复：恢复 `e.printStackTrace()`，与两条 `SpiderDebug.log` 并存（诊断日志带 key/api/异常类型，Logcat 无条件可见），并把注释改写为与实测一致的口径。
- 新增回归锁：`app/src/test/java/com/fongmi/android/tv/api/loader/PyLoaderDiagnosticsWiringTest.java`。

**P2 — `docs/integration/spider.md` 第 4 层缓存归因错误。**

- 归属：本轮评审对象 `96771c8a98`。
- 原文声称 `SourceFileLoader(...).load_module()`「命中已导入模块时返回 `sys.modules` 中的旧模块对象」，即把「改了 py 不生效」归给 `sys.modules`。
- 实测（CPython 3.10 = chaquo 目标版本，及宿主 3.14 双版本复现，见 §5）：`load_module()` **每次都会重新执行源码**（模块对象复用、类对象重建），因此 `sys.modules` 不是陈旧源；真正会复用旧字节码的是 `__pycache__` 校验，其判据只有源文件的 `(mtime, size)` 两个值。
- 修复：改写第 4 层说明与实测表格，按 `(mtime, size)` 双条件精确表述，并补一行「同大小 + 同 mtime 秒」的实测结论。

### 4.2 第 2 轮：复评修复本身

- **P1 修复自评**：只增加一条无条件日志，不改控制流、不吞异常、不改返回值（仍返回 `SpiderNull()`），与 `JsLoader` 口径一致。三条 `SpiderDebug`/`printStackTrace` 各司其职，无重复堆栈成本问题（异常路径低频）。
- **P2 修复自评（第 3 轮再修正一次）**：第 1 版措辞把该窗口写成持久现象，与实测不符——`writeFile()` 走 `mkstemp + os.replace`，每次写入都会刷新 mtime，因此窗口**只在同大小 + 同 mtime 秒**时出现，秒数一过期即自愈。已改为双条件精确表述，并标注「任一条件不成立即重新编译」。
- **新回归锁自评**：`PyLoaderDiagnosticsWiringTest` 断言前**剥掉注释行**（`//` 与 `*` 开头），否则把 `e.printStackTrace();` 注释掉也能满足「有兜底日志」的断言，守卫会退化成恒真检查（已用变异测试证明剥离前后行为不同，见 §5）。
- **同类横查**：其余 loader 的 catch 块一并核对——`JsLoader` 两通道齐全；`CatSpider` 走 `SpiderDebug.log(tag, e)` 且本就无 `printStackTrace`（该文件不在本轮差异内，按范围不改）；`JarLoader` 的 `printStackTrace` 位置与本轮无关。
- **第 2 轮结论**：无遗留必修项，**通过**。

### 4.3 第 3 轮：复评并复核 beta 侧增量

- 复评 P1/P2 两处修复的最终形态，P2 措辞按实测收敛为双条件（本文件 §4.2）。
- **beta 侧增量**（PR #448/#449/#450，dev3 交付：压缩音频直出、进度条 UP 返焦、手机版站点标签满宽、chip 对比度）本轮为**并入**，其正确性由 dev3 的 `docs/C69-beta-merge-review-dev3-20261014.md` 与 `docs/C72-beta-merge-review-dev3-20261016.md` 轮次负责，本任务不重复评审。仅做一致性核对：
  - 合并后 `app/` 下 beta 增量 10 个路径的 blob 与 `origin/beta` **逐字节相同**（树级 `join` 比对，见 §5）；
  - beta 带来的 `PlayerControlFocusHelperSeekReturnTest`（leanback）本轮实跑 **13/13 通过**。
- **第 3 轮结论**：无遗留必修项，**通过**。

## 5. 验证

| 验证项 | 命令 / 方法 | 结果 |
| --- | --- | --- |
| 合并冲突 | `git merge --no-commit --no-ff origin/beta` | **0 冲突** |
| 合并内容中性 | `git diff <merge tree> origin/beta` | 仅 dev2 自身 6 路径（反向）；逐字节中性 |
| 零新增 / 零丢失 | `git ls-tree -r` + `comm` | beta 5070 路径缺失 **0**；新增 3 条均为本任务自身测试文件 |
| 零复活（文件级） | 66 个 revert 删除路径 ∩ 合并树 ∖ beta 树 | **0** |
| 零复活（行级） | 7329 条 revert 删除实质行 ∩ 407 条净新增实质行 | **0**；自检注入探针 PASS |
| 评估索引行 ID | 行首 `\| \`Cxx\` \|` 的 ID 集合比对 | beta 86 = 合并后 86，**零丢失零多余** |
| beta 增量保持 | 合并树 vs beta 树逐路径 blob 比对 | `app/` 下 beta 增量 10 路径**逐字节相同** |
| 冲突标记 | `git grep -nE '^(<<<<<<<\|>>>>>>>\|=======$)'` | 无 |
| 空白校验 | `git diff --check` / `git diff --cached --check` | 通过 |
| 新回归锁（修复后） | `:app:testMobileArm64_v8aDebugUnitTest --tests PyLoaderDiagnosticsWiringTest` | **3/3**，failures=0 errors=0 |
| dev2 定向单测 | `--tests SiteViewModelSubmitAfterClearTest`、`JarLoaderTest`、`LoaderClearQueueTest` | **4/4、7/7、1/1**，全绿 |
| leanback 焦点用例（beta 增量） | `:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.helper.*` | `PlayerControlFocusHelperSeekReturnTest` **13/13**，BUILD SUCCESSFUL |
| Python 契约测试 | `py -3.10 -m unittest discover -s chaquo/src/test/python`（chaquo 目标 3.10）与宿主 3.14 | **36/36 OK**（两个版本各一次） |
| 双 flavor Java 编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL** |
| chaquo Java 编译 | `:chaquo:compileArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL** |
| UI token 门禁 | `bash scripts/check_ui_tokens.sh --strict` | 对比度 `pairs=38 failures=0 min=4.28` 全过；2 处十六进制扫描违规为 beta 既有（`selector_lab_link.xml`、`item_following.xml`），blob 与 `origin/beta` 相同、**非本次引入**，按范围记录不修 |
| 清理 | `gradlew.bat --no-daemon clean` | 见 §7 |

### 5.1 变异测试（证明新增/修改断言非空转）

| # | 探针 | 预期 | 实际 |
| --- | --- | --- | --- |
| 1 | 删除 `PyLoader` 的 `e.printStackTrace();` | 新回归锁转红 | **FAILED**，`PyLoaderDiagnosticsWiringTest:36` |
| 2 | 删除 `PyLoader` 的 `SpiderDebug.log(PY_TAG, e);` | 新回归锁转红 | **FAILED**，`:48` 与 `:35` |
| 3 | 把 `e.printStackTrace();` 移出 catch（放到 `return` 之后） | 新回归锁转红 | **FAILED**，`:36` |
| 4 | 把 `e.printStackTrace();` 改成 `// e.printStackTrace();`（注释） | 剥离注释后仍须转红 | **FAILED**，`:48`（证明剥离注释这层加固真实生效） |
| 5 | 删除 `JsLoader` 的 `e.printStackTrace();`（兄弟探针） | 新回归锁转红 | **FAILED**，`:69` |
| 6 | 删除 `SiteViewModel` 三处 `if (cleared) return …` | `SiteViewModelSubmitAfterClearTest` 转红 | **FAILED**，`:38`（1/4 失败——见下） |
| 7 | 让已清理路径仍投递结果（`liveData.setValue(Result.empty())`） | `SiteViewModelSubmitAfterClearTest` 转红 | **FAILED**，`:32`、`:61`、`:93`（3/4 失败） |

全部探针均以 `git checkout --` / 备份还原，并用 `git hash-object` 确认与修复后版本**逐字节相同**（`PyLoader.java` = `11f1404045`、`SiteViewModel.java` = `976a508d5c`、`JsLoader.java` = `97d5761b09`）。

> 探针 6 只让 4 个用例中的 1 个失败：这与 `SiteViewModelSubmitAfterClearTest` 的设计一致——`concurrentSubmitDuringClearNeverPropagatesRejection` 覆盖「检查通过后池才被关闭」的竞态 `try/catch` 兜底，该兜底独立于三处提前返回，因此单独删除提前返回不会让它失败；探针 7（同时让已清理路径投递结果）才触发 `:93` 与另外两处。两组探针合起来覆盖了「提前返回」与「竞态兜底」两条独立路径，说明该测试类无恒真断言。

### 5.2 3 项未通过用例的性质（既有，非本任务引入）

全量 `:app:testMobileArm64_v8aDebugUnitTest` 跑出 `1832 tests, 3 failed`：

| 用例 | 位置 |
| --- | --- |
| `ReaderPlaybackRoutingSourceTest > readerDefinesTheMonotonicClockUsedByRestore` | `:382` |
| `ReaderPlaybackRoutingSourceTest > horizontalComicProgressUsesItsOwnPageState` | `:367` |
| `TmdbSourceOnlyInteractionTest > nativeVideoSourceOnlyHidesNetworkActionsInBothFlavors` | `:42` |

**归因：既有缺陷，属 C71 已记录的 O3 类（工作区 CRLF 与跨行字面量断言）**，证据：

- 两个测试类本身及其断言的生产文件（`app/src/main/assets/reader.html`、mobile/leanback `VideoActivity.java`）在 `origin/beta`、本任务开工前 HEAD `933ab6d73e`、本任务最终树三处 **blob 逐一相同**（`f0235cd192`、`60a2763e67`、`3b866eaa87`、`25f506c93f` 等），既不在本任务净差异内、也不在 beta 增量内；
- 两个测试类的 `read()` 都**没有** `\r\n` 归一化（`ReaderPlaybackRoutingSourceTest:608`、`TmdbSourceOnlyInteractionTest:63`），而仓库 `core.autocrlf=true`，Windows 工作区为 CRLF、断言字面量是单 LF，故断言恒假；
- 直接对 git blob（= CI 检出的 LF 内容）复算同一批字面量：`function nowMs(){\n    return (window.performance && performance.now) ? performance.now() : Date.now();`、`comicShowPage(i){\n    comicCur = i;`、`updateComicInfo();\n    reportProgress();` **全部命中**，即同一断言在 LF 内容上成立。
- 按范围规则**记录不修**：修它们需要改 2 个不在本任务差异内的测试类，属另开任务。

## 6. 非阻塞观察（记录不修）

- **O1**：`redirect()` 的 `verify` 默认由 `False` 改为 `True`。仓库内**无任何随包调用方**（`app/src/main/assets/` 与 `chaquo/src/main/python/` 内均无 `redirect(` 调用），`chaquo/requirements.txt` 已含 `certifi`，因此只影响远端用户脚本；需要自签名主机的脚本可显式传 `verify=False`。属安全收紧，保留。
- **O2**：`app.redirect` 超限抛 `ValueError` 而非 `requests.TooManyRedirects`。仓库内无调用方，且原实现是 `RecursionError`，新行为更可控；不引入兼容垫片。
- **O3**：§5.2 的两处 CRLF 恒假测试类，以及 C71 名单里剩余的同类（`C47SeekLoadingProgressSourceTest`、`WebThemeTokenSourceTest`、`NativeEnhancedPlaybackStyleFocusTest`、`SearchResultDownFocusTest`），建议另开任务统一归一化行尾。
- **O4**：`str2json` 把解码错误统一包成 `ValueError`。仓库内无任何代码按名捕获 `json.JSONDecodeError`，无兼容影响。`base/spider.py` 的同名方法保持原样不改（它是给用户脚本用的基类，改动面更大且不在本轮差异内）。
- **O5**：任务编号 `C72` 已被 dev3 的 `247629aaea`（`docs/C72-beta-merge-review-dev3-20261016.md`）占用，故本任务使用 **`C73`**（已核实 `C73` 在全部 ref 的提交主题、文档名、任务状态目录与 tag 中均未被占用）。

## 7. 交付坐标

- 合并提交：`0b7ebfd0872ae8e1cf8ff1ae967016e45bfc9969`（双父：`933ab6d73e` + `12d7d559e5`，第二父 = 提交前最新 `origin/beta` tip；实测 `HEAD^2` == `origin/beta`）
- 恢复标签（本地 + 已推送）：`recovery/C73-beta-merge-review-dev2-20261016/20261016052352-0b7ebfd0872a`
- 推送：`origin/dev2`（`40c0ce860e..0b7ebfd087`），远端引用已核对等于本地 `HEAD`
- PR：`dev2` → `beta`，**#451**（`state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`，中文描述，**只创建不合并**）
- PR 文件集与 `git diff --name-only origin/beta HEAD` **逐项一致**（各 8 条，`diff` 无差异）
- PR 创建后再 `fetch` 一次：`origin/beta` 仍为 `12d7d559e5`，等于合并提交第二父，未出现冲突

## 8. Recovery anchor

- 目标：把 `origin/beta` 最新（`12d7d559e5`）合入 `dev2`（不回带已移除提交）、复评 dev2 已提交未推送的 4 个提交与合并交互、修复问题并验证通过、再复评，通过后提交、推送、开 PR 到 beta（只创建不合并）。
- 状态：**已闭环**。3 轮评审发现 2 项必修并已修 + 7 组变异验证；合并提交 `0b7ebfd087`、tag 与分支已推送，PR #451 已创建（`OPEN`/`MERGEABLE`/`mergedAt=null`，只创建未合并）。
- 关键文件：`app/src/main/java/com/fongmi/android/tv/api/loader/PyLoader.java`（P1 修复点）、`docs/integration/spider.md`（P2 修复点）、`app/src/test/java/com/fongmi/android/tv/api/loader/PyLoaderDiagnosticsWiringTest.java`（新增回归锁）、`docs/C73-beta-merge-review-dev2-20261016.md`（本文）。
- 下一步：无。
