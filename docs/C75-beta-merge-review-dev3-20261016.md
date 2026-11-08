# C75 dev3 合并 origin/beta 并复评（2026-10-16）

审计日期：2026-10-16（Asia/Shanghai）
任务 ID：`C75-beta-merge-review-dev3-20261016`
分支：`dev3`
范围：`app`、`chaquo`、`docs`

## 1. 目标与判定标准

把 `origin/beta` 最新代码合入 `dev3`，**不得把远端已移除/回退的提交顺带带回来**；随后评审本轮已修改的代码（含已提交未推送的改动）与合并交互，发现问题就最小修复并验证，通过后再次评审，循环至通过；最后提交、推送、创建到 `beta` 的 PR（中文描述，只创建不合并）。

验收标准：

1. 合并提交为双父合并，第二父等于提交前最新 `origin/beta` tip。
2. 合并结果相对 `origin/beta` 的净差异**只**包含本任务自身的改动与任务文档，不含任何 beta 已删除内容。
3. 零复活有可证伪证据（检测器自检必须能报出注入的探针）。
4. 定向测试、双 flavor Java 编译、Python 契约测试、`git diff --check`、`gradlew clean` 全部通过。
5. PR `base=beta`、`head=dev3`、`state=OPEN`、`mergedAt=null`，描述为中文。

## 2. 合并前的仓库状态

| 项 | 值 |
| --- | --- |
| 分支 / HEAD | `dev3` / `247629aaea29d045c59b5b2b6c1e9c715275c056` |
| `origin/dev3` | `247629aaea29d045c59b5b2b6c1e9c715275c056`（与本地一致，**无已提交未推送提交**） |
| `origin/beta` tip | `dae133ddc49940dc001e4f3588315cfce6dc971d`（PR #452） |
| 合并基 | `247629aaea29d045c59b5b2b6c1e9c715275c056`（= 上一轮 dev3→beta 的成果 C72） |
| 领先 / 落后 | `0 / 12` |
| 开工前脏文件 | 无（工作树干净） |

### 2.1 评审对象说明：为什么本轮没有「本地已提交未推送改动」

`git log --branches --not --remotes` 在本仓库只返回 `dev`/`release/*` 分支的提交（`0eee9bb7e0`、`20907f9179`、`64ae51c610`），**不属于 `dev3`**；`git log --oneline origin/dev3..dev3` 为空，`git stash list` 的 3 条 stash 也都在 `dev` 上。因此本轮 dev3 侧没有本地未推送提交，评审对象是**合并带入的 beta 增量本身**（PR #451 的 py 桥接/ViewModel 生命周期修复 + PR #452 的影视原生播放页修复）。

### 2.2 beta 侧带入的 12 个提交

`dae133ddc4`、`2375d5a291`、`1e0d3ae0a4`、`2d4259ef7a`、`9f4ea10a29`、`e6791fe1b9`、`0b7ebfd087`、`933ab6d73e`、`12d7d559e5`、`247629aaea`、`96771c8a98`、`317fa9021d`、`9116639e3e`、`b1fe3d5686`、`80816a15f0`（其中 `247629aaea` 即 dev3 自己的 C72 合并提交）。

### 2.3 环境阻塞：陈旧 `index.lock`

首次 `git merge` 报 `fatal: Unable to write index.`。定位为 `.git/index.lock`（0 字节，18:46 遗留）与一个仍在运行的 `git fetch origin beta` 进程。等待该 fetch 自行结束后确认无任何 `git` 进程持有锁，再删除陈旧锁并重跑 merge。**未使用 `--force` 或跳过任何安全检查。**

## 3. 合并执行

```text
git merge --no-commit --no-ff origin/beta
→ Automatic merge went well; stopped before committing as requested
```

- 冲突数：**0**。
- 合并后索引树 `4c3399c186fd9fac540e2d74e921c00ce483df55` **与 `origin/beta` 树对象逐字节相同** → 这是一次**零内容合并**：beta 已经包含了 dev3 的全部提交（`git log --oneline origin/beta..dev3` 为空，`dev3` 是 `origin/beta` 的祖先），因此合并只把 beta 增量加上来，dev3 侧没有需要保留的差异。
- 合并带入的 14 个路径（`git status --short`）：4 个 leanback 播放页路径、2 个 `app/src/main` 路径、3 个测试路径、2 个 chaquo 路径、3 个 docs 路径。

## 4. 零复活 / 零丢失证据

| 层 | 口径 | 结果 |
| --- | --- | --- |
| L0 净差异 | `git diff --cached --name-status origin/beta` | **空**（零内容合并） |
| L0b 路径集 | beta 树 5075 文件 vs 合并树 5075 文件 | 新增 **0**、缺失 **0** |
| L1 revert 集 | 锚定主题行的 revert 类提交（`^(Revert\|revert\|回退\|撤销\|剔除)`） | 111 个提交、731 条触及路径 |
| L2 路径级 | 合并新增路径 ∩ revert 触及路径 | **0**（合并新增路径集为空） |
| L3 revert 真删除 | 111 个提交中按 `--name-status` 取 `D` 的整路径 | 55 条 |
| L4 真删除且不在 beta 树 | L3 减去 beta 树 | **39** 条 |
| L5 复活检测 | 上述 39 条在合并工作区是否存在 | **0** 条存在 |
| L6 可证伪自检 | 取 L4 的真实成员 `app/src/main/assets/themes/index.json`（确在 revert 集且不在 beta 树）注入伪造树 | 检测器**如期报出该路径**；同一检测器对真实树输出为空 → 检测器**非空转** |
| 评估索引 | 行 ID 集合比对（`^\| \`[A-Z0-9-]+\``） | beta 18 = 合并后 18，缺失 **0**、新增 **0** |

- 已知两个假阳性来源均已规避：ref 先固化为 40 位 SHA 再取整树（避开 MSYS 把 `:` 后的 `/` 当分隔符）；revert 集只匹配**主题行并锚定开头**（避开任务自身合并提交正文里的「回退」字样）。
- L2 的判据是「合并新增路径集」而非「净差异路径集」：零内容合并下前者为空，因此路径级交集必然为 0，这一点已由 L0b 的 0 新增/0 缺失独立佐证。

## 5. 复评（3 轮）

### 第 1 轮：评审 beta 增量 + 交互

逐文件评审 10 个代码路径（`VideoActivity.java`、`EpisodeAdapter.java`、`activity_video.xml`、`PyLoader.java`、`SiteViewModel.java`、`app.py`、3 个测试类、1 个 Python 测试）。7 项断言全部通过**变异测试**证明非空转（见 §6）。**未发现必修缺陷。**

### 第 2 轮：评审合并交互与既有失败定性

先用「把本合并改动的文件换成开工前 HEAD 版本后重跑」这一可证伪手段给全量 leanback 套件的 8 项失败定性：

```text
git checkout 247629aaea -- <3 个失败测试读取的被改动路径>
./gradlew --no-daemon :app:testLeanbackArm64_v8aDebugUnitTest --tests <7 个失败类>
→ 69 tests completed, 8 failed          # 8 项全部原样复现
```

一个源码扫描测试的结论是「它读到的全部文件原始字节」的纯函数，而换成开工前 HEAD 版本后失败一一原样复现，**证明这 8 项与本次合并无关**（既有失败，且因零内容合并而必然与 beta 自身一致）：

| 失败 | 性质 |
| --- | --- |
| `MpvFontConfigTest.writeIfChanged_…` | `IOException: Unable to commit fontconfig configuration`（环境/既有） |
| `MpvHlsCacheCoordinatorTest.successfulCommitPublishes…` | MPV HLS 缓存（既有） |
| `NativeEnhancedPlaybackStyleFocusTest.everyTmdbRowCard…` | 跨行字面量在工作区 CRLF 下恒假（既有） |
| `ReaderPlaybackRoutingSourceTest` ×2 | 既有（`reader.html` / `NovelRouter` 等） |
| `SearchResultDownFocusTest.scrollCallbacks…` | 既有（`CollectActivity`） |
| `TmdbSourceOnlyInteractionTest.nativeVideoSourceOnly…` | 跨行字面量恒假（既有） |
| `WebThemeTokenSourceTest.webHomeTokenDeclarations…` | 既有（webhome 资源） |

还原后逐个用 `git hash-object --path=` 确认 14 个路径与 `origin/beta` blob **逐一相同**，且索引树回到 `4c3399c186`。

### 第 3 轮：评审最终状态

复核 §3 的零内容合并、§4 的六层证据、§6 的变异矩阵，确认无新增 TODO/调试残留、无冲突标记、`git diff --check` 干净，且工作区与索引均与 `origin/beta` 逐字节一致。3 轮后未发现必修问题，评审通过。

## 6. 变异测试（证明断言非空转）

全部变异均**如期转红**，还原后用 `git hash-object --path=` 确认与 `origin/beta` blob 逐字节相同。

| # | 变异 | 目标 | 结果 |
| --- | --- | --- | --- |
| M1 | `EpisodeAdapter`：`setActivated(active)` → `setActivated(false)` | 当前集描边锁 | 红（`directEpisodeChipsMarkThePlayingEpisode`） |
| M2 | 布局：`layout_below="@+id/actor"` → `"@+id/director"` | 简介锚点锁 | 红（`synopsisPreviewIsAnchoredBelowTheActorRow`） |
| M3 | 预览写入：门控 → 无条件 `View.VISIBLE` | 沉浸音频收起锁 | 红（`synopsisPreviewDoesNotUnhide…`） |
| M6 | 预览写入：`setText(overviewText)` → `setText("")` | 简介内联渲染锁 | 红（`directPlaybackRendersInlineSynopsisPreview`） |
| M1+M2+M3 同批 | — | — | 6 tests, **3 failed** |
| M4 | `PyLoader`：删除 `e.printStackTrace()` | 无条件 Logcat 通道锁 | 红（`initFailureKeepsAnUnconditionalLogcatChannel`） |
| M7 | `app.py`：`getDependence` 的 `getattr` 兜底 → 原样 `ru.getDependence()` | Python 契约（3 条） | 红（`FAILED (failures=1, errors=2)`） |
| M8 | `SiteViewModel.searchContent`：删除 `if (cleared) return;` | 搜索执行器生命周期锁 | 红（`foregroundEntriesAfterClearDoNotThrow`） |
| M5 | `SiteViewModel.playerContentIsolated`：删除 `if (cleared) return cancelledFuture();` | 隔离取流生命周期锁 | **单独删除时不转红**（见下） |
| M5d | 在 M5 基础上把同一 `catch (RejectedExecutionException)` 的兜底改为 `throw e;` | 同上 | 红（`isolatedResolverAfterClearReturnsCancelledFuture`、`concurrentSubmitDuringClearNeverPropagatesRejection`） |

**关于 M5 的如实记录**：`playerContentIsolated` 对「清理后提交」有**两条**独立防护——入口的 `cleared` 快速返回与 `catch (RejectedExecutionException)` 兜底，两者都返回 `cancelledFuture()`。因此只删其中一条时另一条仍然满足断言，测试不转红。这不是恒真断言（删掉**两条**后 M5d 如期转红，证明该断言确实在保护这条不变量），而是**纵深防御**下的预期结果。记录此点是为了避免后续把「单条变异不红」误判为回归锁失效。

## 7. 验证证据

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 定向单测 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests VideoActivityDetailShellSourceTest --tests SiteViewModelSubmitAfterClearTest --tests PyLoaderDiagnosticsWiringTest --tests PlayerControlFocusHelperSeekReturnTest` | **BUILD SUCCESSFUL** |
| 双 flavor 编译 | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL** |
| 全量 leanback 回归 | `:app:testLeanbackArm64_v8aDebugUnitTest` | 4271 项 / 8 失败 / 3 跳过；**8 项经「换成开工前 HEAD 版本重跑」全部原样复现**，判定为既有失败（§5 第 2 轮） |
| Python 契约 | `py -3.10 -m unittest discover -s chaquo/src/test/python` 与 `python -m unittest discover -s chaquo/src/test/python` | **36/36 OK**（CPython 3.10.11 与 3.14.7 各一次） |
| Python 缓存归因复核 | 独立探针复算 `load_module()` 与 `__pycache__` 行为 | 模块对象复用、类对象重建；同大小+同秒时复用旧字节码、换大小即重编译（3.10 与 3.14 一致）→ 与 `docs/integration/spider.md` 第 4 层口径一致 |
| Python 3.14 兼容 | `py -3.10` / `python` 双版本 | 均通过 |
| 空白/冲突 | `git diff --check`、`git diff --cached --check`、`git grep -nE '^(<<<<<<<\|>>>>>>>\|\|\|\|\|\|\|\|)'` | 通过，0 处冲突标记 |
| 净差异 | `git diff --cached --name-status origin/beta` | 空（零内容合并） |
| 零复活 | §4 六层证据 + 注入真实探针自检 | 0 复活、0 缺失、评估索引行零丢失 |
| 提交前 fetch | `git fetch origin beta` + 比对 `MERGE_HEAD` | `origin/beta` 仍为 `dae133ddc4` = `MERGE_HEAD`，未前进 |
| 构建清理 | `./gradlew.bat --no-daemon clean` | 通过（§9） |

未执行：APK 打包、设备安装、实机播放矩阵、native 重建。因此上表不扩大为设备级验收；本轮为**零内容合并 + 既有代码复评**，未修改任何生产代码，设备侧证据仍以 beta 中各提交自身携带的实测记录为准。

## 8. 当前状态

- 合并 + 3 轮复评已完成，工作树处于未提交的合并状态（`.git/MERGE_HEAD` = `dae133ddc4`），索引树 = `origin/beta` 树 = `4c3399c186`。
- 结论：评审通过，**无遗留必修问题，本轮未修改任何生产代码**。
- 下一动作：由 `task_guard finish` 生成双父合并提交与 recovery tag，推送 `dev3` 与 tag，随后 `gh pr create --base beta` 创建中文 PR（只创建不合并）。

## 9. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交（双父） | 见 §10 |
| 第一父 | `247629aaea29d045c59b5b2b6c1e9c715275c056`（dev3 开工前 HEAD） |
| 第二父 | `dae133ddc49940dc001e4f3588315cfce6dc971d`（提交前最新 `origin/beta` tip） |
| recovery tag | 见 §10 |
| 分支 | `dev3` |
| PR | 见 §10（`base=beta`、`head=dev3`，只创建不合并） |

净差异（`git diff --name-status origin/beta <merge>`）：**空**（零内容合并，除本任务文档外）。

### 交付后复核（创建 PR 之后）

见 §10。
