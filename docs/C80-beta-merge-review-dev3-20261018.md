# C80：dev3 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

审计日期：2026-10-18（Asia/Shanghai）
任务 ID：`C80-beta-merge-review-dev3-20261018`
分支：`dev3`
范围：`app/src/main/java/com/fongmi/android/tv/impl`、`app/src/testLeanback/java/com/fongmi/android/tv/ui/activity`、`app/src/testMobile/java/com/fongmi/android/tv/ui/fragment`、`docs`

## Recovery anchor

- **目标**：把远端 `beta` 最新代码安全合入 `dev3`，**不得把 beta 已移除/回退的提交顺带带回来**；复评 dev3 全部已提交未推送改动（含本轮修复）与合并交互，发现问题就最小修复并验证，循环到通过；随后提交、推送并创建中文 `dev3 -> beta` PR，**只创建不合并**。
- **提交前 HEAD**：`a8d4e4bd5c5d080b8e498a70f5b3e69a7125f49e`
- **提交前 beta tip（第二父）**：`c9eca9bc1ab7dc2e23efaf4c84311e19e11b29ec`（PR #457）
- **评审对象**：dev3 已提交未推送的 3 个提交（首页「未配置」空态与状态自收）
- **当前状态**：已交付（提交 64ebe4be5806 / recovery tag / 推送 dev3 / PR #458）
- **下一动作**：无（本批已交付）

## 1. 目标与判定标准

验收标准：

1. 合并提交为**双父合并**，第二父 = 提交前最新 `origin/beta` tip（本轮 beta 已前进，确实存在第二父）。
2. 合并结果相对 `origin/beta` 的净差异**只**包含本任务自身的改动与任务文档，不含任何 beta 已删除/已回退内容。
3. 零复活有**可证伪**证据（含检测器阳性对照），不是只看「无冲突标记」。
4. 定向测试、双 flavor Java 编译、`git diff --check` 全部通过。
5. PR `base=beta`、`head=dev3`、`state=OPEN`、`mergedAt=null`，中文描述且明确不自动合并。
6. PR 文件集与 `git diff --name-only origin/beta HEAD` 逐项一致。

## 2. 合并前的仓库状态

| 项 | 值 |
| --- | --- |
| 分支 / HEAD | `dev3` / `a8d4e4bd5c5d080b8e498a70f5b3e69a7125f49e` |
| `origin/dev3` | `44e2dad6b0d73cca92e57ba253b04bd3a8605c0a`（落后本地 **3** 个提交） |
| `origin/beta` tip | `c9eca9bc1ab7dc2e23efaf4c84311e19e11b29ec`（PR #457） |
| 领先 / 落后 vs beta | `3 / 1` |
| 开工前脏文件 | 无（工作树干净，`.git/index.lock` 不存在） |

### 2.1 为什么本轮 **有**第二父（与 C77/C79 相反）

PR #457 是**先用 `git diff` 而非本地 dev3 生成**的：`c9eca9bc1a` 的两个父亲是 `4e3f6bcf3a`（beta 侧）与 `44e2dad6b0`（dev3 侧），而本地 dev3 的 `a8d4e4bd5c` 并不包含 `c9eca9bc1a`。

`git merge --no-commit --no-ff origin/beta` 报 `Automatic merge went well; stopped before committing as requested`，随后两项独立判据证明本轮合并**零内容变更**：

- `git diff HEAD --stat` 为空、`git status --porcelain` 为 0 行；
- `git write-tree` = `00bae7852a49d25c0bd5306f6f4552fb4b3deaf7` 与 `git rev-parse HEAD^{tree}` 相同。

即：`c9eca9bc1a` 相对 `44e2dad6b0` **没有带来任何内容差异**（判定依据：`git diff 44e2dad6b0 c9eca9bc1a` 输出为空；`git diff dev3 origin/beta` 恰好只等于 dev3 侧那 3 个提交）。

**「已移除的回退提交」风险面**：本次合并**唯一**的引入通道，就是 `c9eca9bc1a` 相对 `44e2dad6b0` 的内容差异——该差异经实测为**空**。因此「顺带把 beta 已移除的回退带回来」在本轮不存在任何通道，而不是「靠合并没有冲突」。

## 3. 零复活审计（三层 + 阳性对照）

**准绳**：全部以 `git rev-parse origin/beta` 的固化 SHA 取树，不用 `origin/beta:path`（规避 MSYS 路径分隔符误判）。

### 3.1 第一层：整树比对（文件级零复活 / 零丢失）

| 项 | 结果 |
| --- | --- |
| beta 树 | 5141 条 |
| HEAD 树 | 5142 条 |
| HEAD − beta（新增） | **1** 条：`docs/HOME-STATE-NO-CONFIG-20261018.md`（本任务自身新增） |
| beta − HEAD（丢失） | **0** 条 |

### 3.2 第二层：行级净差异

`git diff --stat origin/beta HEAD` = **12** 个路径（11 `M` + 1 `A`），`436 insertions(+), 2 deletions(-)`。2 条删除行属 `BaseConfig` 自身的改写（`callback.error("")` → `callback::noConfig`），非 beta 侧删除。

### 3.3 第三层：revert 提交交集（**含阳性对照**）

**先记录一次检测器假阴性**：首版用 `git log --all --name-only --pretty=format:'@@%H %s'` 逐行解析，报出 `revert-topic commits: 0 / distinct paths: 0`——即被评审的 `HEAD` 本身也「不是 revert 提交」，说明**解析器坏了**而非世界干净。根因：`--pretty=format` 中的 `%H` 在逐行 `Out-String` 后与 `%s` 的字段切分被破坏，主题行匹配 `^(revert|回退|撤销)` 全部失配。

**重建解析（`%x00` 记录分隔 + `%H%x09%s` 字段分隔）并加阳性对照**：

| 项 | 值 |
| --- | --- |
| 主题行锚定的 revert/回退/撤销提交 | **110** 条 |
| 其触及路径去重 | **728** 条 |
| 阳性对照：`fd29d76ee7`（revert 电视版暗色配色）的 21 条触及路径是否全部进入集合 | **是（21/21）** |
| 文件级：净差异 ∩ revert 触及路径 | **4** 条 |

命中的 4 条与**行级**核对结果（把每个 revert 提交在**该路径上删除的行**与净差异**新增行**取交集）：

| 路径 | 命中的 revert 提交 | 净差异新增行 | 该路径被 revert 删除的行（去重） | 行级交集 |
| --- | --- | --- | --- | --- |
| `app/src/main/res/values/strings.xml` | `be1b02e06b`(remove dynamic theme color system)、`13a91c4f3e`(Revert "Make cyber audio background the default preset") | 9 | 45 | **0** |
| `app/src/main/res/values-zh-rCN/strings.xml` | 同上 | 8 | 45 | **0** |
| `app/src/main/res/values-zh-rTW/strings.xml` | 同上 | 8 | 45 | **0** |
| `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/VodFragment.java` | `a5aeb3de47`(Revert "fix: 修复系统栏图标与 WebView 不可用时的点播页异常") | 43 | 21 | **0** |

四条全部为**历史共触**（这些文件是长期被反复改写的热点，被 revert 碰过并不等于本轮复活），且**行级交集为 0**：本轮新增的 `home_no_config_*` 三语文案与 `showNoConfig()/dismissStateIfConfigured()` 没有一行是被撤回过的旧代码。

- 净差异 `A` 集合（1 条，任务文档）∩ revert 触及路径 = **空**。

结论：**零复活成立**，且证据可证伪（阳性对照先证明检测器能看见已知 revert，再给出非空文件级交集并逐条降到行级证否）。

### 3.4 混淆项排除（`--all` 范围偏差）

上述 revert 遍历用 `--all`（含所有远端分支，更宽），而正确基线是 `origin/beta` 可达历史即 `4e2e3f6bcf3a`。两者相差的只是**被 beta 回退掉的旧提交标题**——`--all` 造成的额外触及路径**只可能使交集变大**，因此用更宽的集合仍得到 4 条全部为历史共触，结论对范围偏差**稳健**（放宽后仍不成立，收紧后更不成立）。

### 3.5 已知检测边界（诚实记录）

历史上归入「回退」主题的提交可能不严格以 `revert/回退/撤销` 开头（例如 `9409b1a8cd`「Revert mobile episode layout to …」会命中，但纯描述式的撤换标题不会）。因此第三层**不是**「全部回退提交」的完备检测，完备性由第一层（整树，`beta − HEAD = 0`）承担：任何被 beta 删掉的路径只要在 HEAD 复现，都会在 §3.1 暴露。

## 4. 复评：3 个未推送提交

### 4.1 评审通过的实现（保留原样）

| 位置 | 判据 |
| --- | --- |
| `BaseConfig.loadConfig` 的空地址分支 | `TextUtils.isEmpty(config.getUrl())` 确实对应「接口列表为空」（`Config.vod()` 为空时为 `""`），且该分支只在配置解析抛错时到达；`noConfig` 与 `error` 语义分离正确 |
| `StateEvent.NO_CONFIG` | 新增枚举常量挂在 switch 末尾，`VodFragment` 已处理该分支；`EMPTY/CONTENT/PROGRESS` 语义未被挪用 |
| 两端 `showNoConfig()` | 走 `ProgressLayout.showEmpty()`（空态：插画保留、标题不用错误色），主动作 `openVodConfig()` → `HistoryDialog.create().vod().manage()`（该 API 在 leanback/mobile 两 flavor 均存在，`manage()` 会显示「新增」） |
| `dismissStateIfConfigured()` 判据 | 仅当「浮层可见 且 配置真有站点」才收；TV 端额外要求 `isEmpty() || isError()`，不会把 PROGRESS 顺手收掉——本轮已把这两条补成断言 |
| `AppBackup.reload()` 前提 | 实测确认其调用 `RefreshEvent.home()`，与注释所述「恢复备份走 HOME」一致（`AppBackup.java:275`） |
| `VodConfig.getSites()` | 空安全（`sites == null` 返回 `Collections.emptyList()`），`getSites().isEmpty()` 不会 NPE |

### 4.2 发现并修复的必修缺陷（1 项，真缺陷）

**缺陷**：`Callback.noConfig()` 是**空实现**。

**为什么是必修**：`start()` 与 `error(String)` 在既有宿主里是「加载开始 / 加载结束」的**一对**。`noConfig()` 是本次新增的**终态分支**，默认留空 ⇒ 所有**没来得及覆写它**的宿主在无配置时**永远停在加载中**。

**实测可达路径（本机代码推演，非假设）**：TV 标题长按 → `CustomTitleView` → `HomeActivity.reloadConfig()` → `progressLayout.showProgress()`；该匿名回调**只覆写** `start/success/error`。在 `Config` 为 `Config.vod()`（URL 空）的设备上，`loadConfig` 走到空地址分支：

- 旧代码：`App.post(() -> callback.error(""))` → 走 `reloadConfig()` 里的 `error(String)`（`Notify.show("")` 在 `Notify.show(String)` 里被 `TextUtils.isEmpty` 直接返回，**不会弹空吐司**）→ `showContent()`，转圈被收掉；
- 新代码（空实现）：**没有任何调用**能收掉 `showProgress()` 转入的转圈。

**修复（最小，默认实现回退到旧收尾行为）**：

```java
public void noConfig() {
    error("");
}
```

改动前 `noConfig` 的默认行为**就是** `error("")`（`BaseConfig` 直接调 `callback.error("")`），因此这不是新增行为，而是**逐字保留旧行为**；两端首页已覆写 `noConfig()`，不受影响。

**为什么不是「改成空实现更干净」**：全仓有 10 处 `load(new Callback(){...})` 匿名回调（`Backup`、`AppBackup`、`Manage.reloadConfigs`、`CustomCspDialog.reloadConfigs`、`VodFragment.setConfig`、`leanbackHome.reloadConfig` 等），它们的收尾约定都是 `error(String)`。让「未配置」这条路径从既有约定上掉队，代价与收益不成比例。

### 4.3 复评第 2 轮：文档失真（1 处）

同一任务文档 `docs/HOME-STATE-NO-CONFIG-20261018.md` §3 自述 `Callback.java`「新增 `noConfig()` 钩子（默认空实现…）」在修复后**失真**，已改为「默认转调 `error("")`」，并补记 §5 的 `noConfig` 行为边界与 `ProgressLayout` 过期注释。

### 4.4 复评第 3 轮：无新增必修问题

- `grep` 复核 `noConfig` 的两个覆写点（两端首页）仍存在且未被默认实现影响。
- 复核 net-diff 无冲突标记、`git diff --check` 无空白错误。
- 复核新增守卫断言在**正确**语义上（见 §5 变异测试）。

## 5. 验证

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| TV 端契约测试 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests *TvErrorStateContractTest` | **14 例 0 失败**（含本轮新增 `defaultNoConfigStillTerminatesTheLoadingState`） |
| 手机端契约测试 | `:app:testMobileArm64_v8aDebugUnitTest --tests *MobileHomeStateContractTest` | **9 例 0 失败** |
| 双 flavor Java + UnitTest Java 编译 | `compile{Mobile,Leanback}Arm64_v8aDebugJavaWithJavac` + `…DebugUnitTestJavaWithJavac` | BUILD SUCCESSFUL |
| `git diff --check` | — | 无空白错误 |
| 冲突标记扫描 | `git diff origin/beta \| grep -E '^\+(<<<<<<<\|=======\|>>>>>>>)'` | 0 条 |

### 5.1 变异测试（证明本轮守卫非空转）

- **M1**：把 `Callback.noConfig()` 的默认实现改回**空实现**（即被判定为缺陷的写法）。
- 结果：`TvErrorStateContractTest.defaultNoConfigStillTerminatesTheLoadingState` **如期转红**——14 例中 **1 例失败**，`java.lang.AssertionError: 默认实现必须结束加载态（转调 error("")），否则既有宿主会永久停在加载中`。
- 还原：`git hash-object` = `c63b39aea75f5a41cdbdf165012307b3b5cca35f`，与变异前**逐字节一致**。

## 6. 未完成/未验证边界（诚实记录）

- **本轮不新增设备证据**。设备侧结论沿用 `docs/HOME-STATE-NO-CONFIG-20261018.md` §0（5554 新 Android 用户干净安装）与 §0.2（5559 本地配置源 + 同进程收卡）。本轮修复只影响**未覆写 `noConfig()` 的宿主**，两端首页均覆写，故真机行为不受本轮改动影响。
- 该任务文档 §0.2 已诚实记录两项未覆盖：`LiveConfig` 空态、以及「恢复整包备份 zip」的端到端（当时是用界面新增复刻 `reload()` 的事件序列）。
- `ProgressLayout.applyEmptyContent()` 的注释与实际布局不符（见任务文档 §5），属预存问题，**按 AGENTS.md §2 只报告不修**。
- 任务守卫未运行：本机 `bash` 不可用（`HCS_E_SERVICE_NOT_AVAILABLE`），按任务文档「允许路径」逐项手工核对后提交。

## 7. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `64ebe4be58063075f5139d175412bc3e9ce95edd`（双父：`a8d4e4bd5c` + `c9eca9bc1a`） |
| 第二父 | `c9eca9bc1ab7dc2e23efaf4c84311e19e11b29ec`（PR #457） |
| recovery tag | `recovery/C80-beta-merge-review-dev3-20261018/20261018183241-64ebe4be5806`（annotated，已推送） |
| 净差异 | **14** 个路径（12 个实现/文档 + 本任务 2 个文档），仅本任务自身改动；`665 insertions(+), 2 deletions(-)` |
| 分支 | `dev3`，推送后 `origin/dev3` = `64ebe4be5806` = 本地 HEAD |
| PR | **#458** → https://github.com/Silent1566/webhtv/pull/458 |

### 7.1 交付后复核（已执行）

| 复核项 | 结果 |
| --- | --- |
| `gh pr view 458 --repo Silent1566/webhtv` | `baseRefName=beta`、`headRefName=dev3`、`state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`、`isDraft=false` |
| `git ls-remote origin` | `refs/heads/dev3` = `64ebe4be58063075f5139d175412bc3e9ce95edd`；tag 存在且 `^{}` 解引用同值（tag 指向正确） |
| 创建 PR 后复核 beta | `origin/beta` 仍为 `c9eca9bc1ab7dc2e23efaf4c84311e19e11b29ec`（**未前进**） |
| PR 文件集 vs `git diff --name-only origin/beta HEAD` | GitHub API 返回 **14** 个文件，与本地 **逐项一致**（`Compare-Object` 输出为空） |
| compare 状态 | `ahead_by=4`、`behind_by=0`、`status=ahead` |
| 分支保护 | **未合并、未关闭**，仅创建（符合任务要求「不要擅自通过 PR」） |

### 7.2 工具坑（本轮实测，供后续任务复用）

`gh` 在本工作区的**默认仓库上下文是 `webhtv/webhtv`**（`gh repo view` 返回它），而不是 `origin` 指向的 `Silent1566/webhtv`。因此：

- 不带 `--repo` 的 `gh pr create` 会报 `No commits between beta and dev3` / `Head ref must be a branch`——这是**上下文错了**，不是分支或提交有问题；
- 不带 `--repo` 的 `gh pr view <N>` 会报 `Could not resolve to a PullRequest`（即使该 PR 真实存在）。

**规避**：所有 `gh pr` 子命令显式带 `--repo Silent1566/webhtv`。

## 8. 回滚

本轮可交付改动有两部分：① 3 个已提交未推送提交（首页未配置空态与状态自收），② 本轮复评修复（`Callback.noConfig()` 默认实现 + 两端守卫断言 + 文档更正）。回滚即 revert 本轮合并提交：合并本身零内容变更（§2.1），故 revert 只影响 ① 与 ②；`a8d4e4bd5c` 是其父，行为基线可直接回到改动前的 `callback.error("")` 路径。
