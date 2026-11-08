# C29：dev1 合并远端 beta 最新代码并循环复评未推送改动（猫源 node 死进程恢复）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入本地 `dev1`（且不得把远端已移除/回退的提交重新带上来），循环复评 `dev1` 相对 `origin/beta` 的全部已修改代码（含已提交未推送的猫源修复 `bba9e17db`），发现问题则最小修复并验证通过，通过后再次复评，直到通过；最后以一次原子提交（含合并父节点）提交本任务改动、推送 `dev1`、创建 `dev1 -> beta` 的中文 PR（只创建，不合并，不擅自通过）。
- **验收**：① 合并结果包含复评时刻的 `origin/beta` tip；② 被远端剔除的提交不在结果祖先中，且远端删除/回退的功能（Android 9 manifest `<queries>` 等）未被带回；③ `dev1` 相对 `origin/beta` 的净差异只含本分支自身改动；④ 定向单测（含新增回归测试）、Leanback 全量单测（--rerun-tasks）、双 flavor Java 编译、Debug 打包覆盖安装、设备端冒烟全部通过；⑤ PR 描述为中文、说明改动内容且排版清楚。
- **允许路径**：合并带入的 9 个文件 + 净差异评审修复的 3 个文件（`CatSpider.java`、`NodeRuntime.java`、新增 `CatSpiderResolveTest.java`）+ 本文档。
- **分支/HEAD**：`dev1`；任务开始时 HEAD `bba9e17dbb04f538cba91ada28f0dafd11010ba5`（领先 `origin/dev1` 2 个提交，其中 `4cb165a0f` 为 PR #386 合并提交、已随远端 beta 携带；本分支独有改动即猫源修复 `bba9e17db`）。
- **保护面**：任务开始时工作区干净，无受保护脏文件。
- **当前状态**：合并、2 轮复评、修复、单测/编译/打包/设备冒烟全部完成；进入收尾提交与 PR。
- **下一动作**：一次 `task_guard.sh finish` 提交（含合并父节点）+ 打恢复标签，推送 `dev1`，创建 `dev1 -> beta` 的中文 PR（只创建，不合并）。

## 时间与设备

- 本地时间：2026-10-01 16:31–18:55（Asia/Shanghai）。
- 设备：`192.168.50.3:5555`（dev1 分配机位，Android 9 / sdk 28）；未占用 dev2/dev3/dev4 机位。
- WebHTV 打包：`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5555`（Debug 测试包，覆盖安装，未卸载）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1` |
| 任务开始时 HEAD | `bba9e17dbb04f538cba91ada28f0dafd11010ba5` |
| 首次合并 `origin/beta` tip | `bbe7062f423c8e8152057c36744f59d849705675`（PR #388） |
| 二次合并 `origin/beta` tip（最终） | `6ee8a41dc95a68c058e568bccb06663934c450e7`（PR #389） |
| `origin/dev1` HEAD | `fc36522d2e1733ae11524aacf3a6cb3cea167b59` |
| `dev1` 与 `origin/beta` 合并基点 | `4cb165a0fc21aa2b980e0a6a8cb78fe44351775a`（PR #386 合并提交，本身已在 beta 上） |
| `origin/beta..dev1` 独有提交 | 1（`bba9e17db` 猫源 node 死进程恢复） |
| 被剔除提交 `5682f2b054…` 是否为 `origin/beta` 祖先 | **否**（`git merge-base --is-ancestor` 判定失败），未随合并带回 |
| 合并提交父节点 | 父 1 本地 `dev1`（含评审修复）、父 2 `6ee8a41dc`（远端 `origin/beta` tip） |

- **中途远端推进的处理**：首次以 `--no-commit` 合并 `bbe7062f4` 后，远端 beta 又合入了 PR #389（dev2 的 `fix(tv): keep failed search detail on playback page`）。因目标要求合并**最新** beta，`merge --abort` 后对新 tip `6ee8a41dc` 重新合并（工作树未暂存的评审修复在 abort 中完整保留，经 `grep` 核对）。两次合并均输出"自动合并进展顺利"，零冲突、零冲突标记。
- **合并带入文件与远端逐字节一致**：两次合并共带入 9 个文件（`AndroidManifest.xml`、`VideoActivity.java`（leanback，含 PR #387/#388 焦点修复与 PR #389 setEmpty 修复）、`TmdbDetailActivity.java`、`PlayerControlFocusHelper.java`、`PlayerControlFocusIntegrationTest.java`（testMobile）、`VideoActivityDetailShellSourceTest.java`（testLeanback）、两份 dev2 复评文档），全部通过 `git diff origin/beta -- <files>` 为空确认与远端 tip 逐字节一致，无合并语义偏移。
- **远端移除/回退内容未带回（三层核对）**：
  1. 被剔除提交 `5682f2b054`（剔除 PR #353 主题系统改动）不是 `origin/beta` 祖先，也不在合并结果祖先中；
  2. `git diff --diff-filter=D --name-only origin/beta` 为空——远端删除过的文件没有被带回；
  3. Android 9 manifest 修复生效：合并后 `AndroidManifest.xml` 无 `<queries>` 元素（仅保留警示注释），设备 `dumpsys package` 确认合并清单 0 处 `queries`。
- **`dev1` 相对 `origin/beta` 的净差异只有本分支自身改动**：2 个文件、+113/−3（猫源修复本体 + 复评修复），见下文。

## 评审记录

### 第 1 轮：改动本体 + 合并带入内容核对

复评范围：`dev1` 相对 `origin/beta` 的净差异 = 已提交未推送的猫源修复 `bba9e17db`（`CatSpider.java` +50、`NodeRuntime.java` +44），外加合并带入内容与 dev1 既有工作的语义冲突面。

- **合并带入内容逐项核对**：dev2 的三组焦点修复（`hasRememberedFocus()` 记忆焦点判定、`PlayerControlFocusHelper.ensureFocus` 目标优先化、`TmdbDetailActivity` 触摸记忆焦点与 `hasRememberedInlineControlFocus()`）、Android 9 manifest `<queries>` 移除、PR #389 `setEmpty` 搜索失败保留播放页——均与 dev1 既有 TMDB 焦点链工作（PR #386 内容）无语义冲突，文件层面无重叠。
- **猫源修复本体**：`NodeRuntime.serviceAlive()` 用 `ActivityManager` 进程表探活 `:node` 子进程，崩溃无回调的场景补齐；`start()` 复用捷径加探活条件、死进程分支清 `running/port/servingSourceKey`；`CatSpider` 连接类失败触发 `restartIfDead` 异步恢复。逻辑正确、线程边界符合（调用方在后台线程）。
- **发现的问题（本轮修复）**：
  1. **实质 bug**：`CatSpider.resolve()` 的改写条件只看"api 匹配 `/spider/` 路由 + 本地 node 端口非 0"，未区分**本机 bundle** 与**远端 T4 猫源**。远端 api（`CatSourceConfigTest` 已锁定的合法形态 `https://user:pass@catpaw.example.me/spider/muou/3`）同样匹配 `matches()` 与 `BUNDLE_BASE` 正则；一旦本地 node 启动过（`port>0` 常驻），远端站点请求会被错误改写到 `http://127.0.0.1:<port>`，表现为"切换订阅（本地包 → 远端 T4）后远端源全部失效"。修复：新增 `isLoopbackBase()` 判定，只有本机回环 api 才跟随当前端口重建，远端 api 原样请求；端口比对同时改为 `endsWith(":port")`，避免 `…:59988` 误命中 `:9988`。
  2. `restartIfDead` javadoc 中 `{\\@link #running}` 转义写法错误（与同文件其他 javadoc 不一致，渲染成字面文本）。
  3. `start()` 复用捷径与死进程分支连续调用两次 `serviceAlive()`（重复 binder IPC），合并为一次探测结果复用。
- **新增回归测试**：`CatSpiderResolveTest`（6 用例）钉死地址重建契约——远端 base 不得判回环、回环 base 必须判回环、userinfo 不遮蔽 host、`BUNDLE_BASE` 形状、`matches()` 边界、尾斜杠归一。`TextUtils` 经由仓库既有测试桩（`app/src/test/java/android/text/TextUtils.java`）真实断言，非假绿。

### 第 2 轮：修复后终态复评 + 交付边界

- 修复内容复核：`CatSpider.java`（`resolve` 收窄 + `isLoopbackBase` + 测试钩子 `apiForTest`/`BUNDLE_BASE` 包内可见）、`NodeRuntime.java`（javadoc + 探活合并）、新增 `CatSpiderResolveTest.java`；`git diff --check` 与 `git diff --cached --check` 均无空白错误。
- `restartIfDead` 边界再核对：`servingUrl` 为去 `.md5` 的 bundle 地址，`bundleUrl()` 幂等，`same()` 判定成立；远端站点连接失败误触发 `restartIfDead` 时本地 node 健康则直接返回 true 无副作用、已死则重启本地 node 也无害。
- 二次合并（PR #389）后重新核对净差异与带入文件一致性，均通过；被剔除提交核对三层全部保持通过。
- 测试有效性再确认：修复后定向用例与全量单测均重新执行（非沿用旧结果）。
- 交付边界：净差异只有 2 个修改文件 + 1 个新增测试文件 + 本任务文档；无 `app/build/**` 产物、无临时脚本被纳入。
- **第 2 轮结论**：未发现必须修改的问题，改动可交付。

## 验证记录

- **定向单元测试 + 双 flavor Java 编译**：`bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'CatSpiderResolveTest' --tests 'CatSourceConfigTest' --tests 'CatActionTest' --tests 'com.fongmi.android.tv.node.*' :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac --continue` → `BUILD SUCCESSFUL in 1m16s`；XML 核对：`CatSpiderResolveTest` 6 项、`CatSourceConfigTest` 13 项、`CatActionTest` 11 项、Node 三套 58 项，全部 0 failure / 0 error。
- **Leanback 全量单测（--rerun-tasks，最终合并形态）**：`bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --rerun-tasks --continue` → `BUILD SUCCESSFUL in 2m18s`；633 个结果文件汇总 **4075 项、failure 0 / error 0、skipped 2**（既存）。PR #389 带入的 `VideoActivityDetailShellSourceTest` 1 项、`PlayerControlFocusIntegrationTest` 等均通过。
- **打包与覆盖安装**：`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5555` → `BUILD SUCCESSFUL`，`adb install -r` 覆盖安装 `Success`（未卸载）。修复代码确认进 dex：`CatSpider.dex` 含 `isLoopbackBase`/`resolve`/`restartIfDead`，`NodeRuntime.dex` 含 `restartIfDead`/`serviceAlive`。
- **设备冒烟（Android 9 / sdk 28，恰为 manifest 修复目标机型）**：force-stop 后冷启动，`mResumedActivity=HomeActivityCurrent`；`dumpsys package` 合并清单 0 处 `queries`（Android 9 manifest 崩溃修复生效）；logcat 过滤 `FATAL EXCEPTION` / `fatal signal` / `ANR in com.silent` 无命中。
- **明确未验证的边界**：未在设备上端到端复现"猫源 node 崩溃自动恢复"（需现成猫源订阅与杀进程窗口，原提交 `bba9e17db` 的提交信息已记录过该路径的设备验证；本次改动收窄的是地址判定内核，由新增回归测试锁定）；未跑 Mobile 全量单测（净差异不含 mobile 源集，已单独跑通 `compileMobileArm64_v8aDebugJavaWithJavac`）；未做机位矩阵（按约定 dev1 只用 5555）。

## PR 边界

相对 `origin/beta`（`6ee8a41dc95a68c058e568bccb06663934c450e7`），`dev1` 净差异为 **3 个文件**（+113/−3 及新增测试）：

| 状态 | 文件 | 内容 |
| --- | --- | --- |
| M | `app/src/main/java/com/fongmi/android/tv/api/loader/CatSpider.java` | 猫源请求地址每次按 Node 当前端口重建（限本机回环 api）；连接类失败触发 `NodeRuntime.restartIfDead` 异步恢复 |
| M | `app/src/main/java/com/fongmi/android/tv/node/NodeRuntime.java` | `:node` 子进程探活（`serviceAlive`）；复用捷径探活校验；死进程状态复位与自动重启 |
| A | `app/src/test/java/com/fongmi/android/tv/api/loader/CatSpiderResolveTest.java` | 地址重建契约回归测试（6 用例） |

交付提交是一个**合并提交**（父 1 = 本地 `dev1` 含修复，父 2 = 远端 `origin/beta` `6ee8a41dc`），使 `dev1` 成为 `origin/beta` 的直接后继，合并进 `beta` 时可直接快进而无额外冲突面。

PR 只做创建，不执行合并。

## 回滚锚点

- 提交前锚点：`dev1 @ bba9e17db`（远端 `origin/dev1 @ fc36522d2`）。
- 回滚：`git revert <本任务合并提交>`（改动为猫源请求地址判定与进程探活，无偏好键或数据格式变更）。
- 被剔除提交保持非 `origin/beta` 祖先：本次未把 `5682f2b05` 或任何历史 revert/reset 提交作为独立改动带入。
