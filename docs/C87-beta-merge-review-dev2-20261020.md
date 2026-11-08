# C87-beta-merge-review-dev2-20261020

## Recovery anchor

- Objective: 在 dev2 上合并 `origin/beta` 最新代码（远端已回退/删除的提交不得被顺带带回），复评本次合并带入的改动与 dev2 已提交未推送的 2 个提交，循环评审-修复-验证直到通过，然后提交、推送并创建到 `beta` 的中文 PR（只创建、不合并）。
- Lane: `standard`。Scope: `app`、`docs`。
- Baseline: branch `dev2`，HEAD `fbd31c11f4fe73b3f3377d46b68bf94841e89142`（`origin/dev2` = `5591f92612`，本地领先 2 个提交）。
- Protected dirty paths: `.wh_diag/**`（任务前即存在的 127 个未跟踪诊断文件，未纳入 scope、未提交）。
- Status: 合并完成（0 冲突），复评 3 轮并修正 1 项 medium（首页交付文档失真）+ 1 项 medium（一键同步代际守卫线程安全与覆盖不全），验证全部通过。
- Next action: 无。

## 1. 合并对象

| 项 | 值 |
| --- | --- |
| 合并前 dev2 HEAD | `fbd31c11f4fe73b3f3377d46b68bf94841e89142` |
| `origin/beta` tip | `ee8b058a972577e282ed556ed9f0c4defec1869c`（Merge PR #463 from dev3） |
| 合并基 | `5591f92612990c779a0bc8d7d082ac22e01388c0`（= `origin/dev2` tip） |
| 合并方式 | `git merge --no-ff --no-commit origin/beta` → `Automatic merge went well`，**0 冲突** |
| 合并树（预测 vs 实际） | `git merge-tree --write-tree dev2 origin/beta` = `a46ca5c17885a735bbaadf898e717d28c5221b85`；合并后 `git write-tree` = **同值** |
| 合并提交 | `2b098a691b6b6a36a8b64984c6673bcc2674b315`（双父 `fbd31c11f4` + `ee8b058a97`） |

beta 自合并基起 **10 个提交**、触及 **13 条路径**（+773 / -78）：`ee8b058a97` Merge PR #463、`f70fa8c5fd` / `4e7b24b5f7` / `13a093ad37` 三次 dev3 侧合并复评、`a004e4a18e` docs(C84)、`c88f2896ac` docs(C81)、`f289207bce` Merge PR #464（dev2 自己的 C84）、`f6e122c038` merge(C81)、`3f30bebe7e` fix(sync) 方向选择器评审缺陷、`b6f8e0fda7` feat(sync) 一键同步推送/拉取两段式。

dev2 自合并基起 **2 个提交**、触及 **5 条路径**（+97 / -55）：`e1cbbb7735` 无配置保留导航、`fbd31c11f4` 去掉额外「添加接口」入口。

两侧路径集合**交集为 0**（`comm -12` = 0），故 0 冲突是预期结果而非巧合：beta 侧集中在播放历史/一键同步/文档，dev2 侧集中在首页无配置形态。

## 2. 零复活 / 零丢失证据

用户显式约束：「远端已经移除回退的提交不能再顺带提交上去」。方法沿用 C80/C82/C83/C84 的**集合等价**，而不是逐层计数。

### L1 整树完整性

```
git ls-tree -r --name-only origin/beta | sort > beta_tree
git ls-files | sort > idx_tree
comm -23 beta_tree idx_tree | wc -l   →  0
```

`beta files=5163  index files=5164  missing=0`；唯一多出的路径是 dev2 侧新增的 `docs/HOME-NO-CONFIG-NAV-20261020.md`。

### L2 集合等价（决定性）

| 判据 | 命令 | 结果 |
| --- | --- | --- |
| A | `git diff --name-status origin/dev2 dev2` | **5 条**（dev2 自有改动） |
| B | `git diff --cached --name-status origin/beta`（合并索引） | 恰为 A 的 5 条 |
| C | `diff A B` | **SETS-IDENTICAL** |
| D | `git diff --name-only 5591f92612 origin/beta` | **13 条**（beta 自有改动） |
| E | `git diff --cached --name-only HEAD` | 恰为 D 的 13 条 |
| F | `diff D E` | **SETS-IDENTICAL** |

合并索引相对 beta 的净差异 `git diff --cached --shortstat origin/beta` = `5 files changed, 97 insertions(+), 55 deletions(-)`，即**只有 dev2 自己的 2 个提交**；相对 dev2 旧 HEAD 只有 beta 自己的 13 条。两侧互为补集。

### L3 合并树与干净 ort 结果逐字节相同

`git merge-tree --write-tree dev2 origin/beta` = `a46ca5c17885…` = `git write-tree`（合并索引树） ⇒ **MERGE-RESULT-EXACT**，索引就是无人工干预的合并结果，本次没有手工解决冲突。

### L4 revert 集合交集

```
git log --oneline 5591f92612..origin/beta --grep="[Rr]evert"  →  空
```

本批 beta 10 个提交中无 revert 主题提交。历史中主题含 revert 且触及 dev2 这 5 条路径的提交只有 `a5aeb3de47`（`Revert "fix: 修复系统栏图标与 WebView 不可用时的点播页异常"`，2026-07-18），`git merge-base --is-ancestor a5aeb3de47` 对 **dev2 与 beta 都成立** ⇒ 已是共同祖先，不可能被本次合并「顺带带回」。

### 已知检测边界

L4 的主题行锚定不完备（纯描述式撤换标题会漏）。完备性由 L1 整树 + L2 集合等价 + L3 树等价承担：三者都与 revert 语义无关。

## 3. 复评发现与修复

评审分三轮：第 1 轮并行两位评审者（本地 2 个未推送提交 / 合并带入的 beta 侧 9 条代码路径）；第 2 轮复评修复本身；第 3 轮收尾复评（代际全覆盖与措辞）。

### 3.1 【medium】首页交付文档以「常驻添加入口」为验收，与最终代码相反

`docs/HOME-NO-CONFIG-NAV-20261020.md` 的 Recovery anchor 目标与验收①②③、§3 残留项仍写着「有常驻的『添加接口』入口」「点「添加接口」能进入接口列表」，而 `fbd31c11f4` 已把该入口整段删除（§1 的 2026-10-20 追加说明才是最终行为）。属**本地未推送提交自身引入的文档失真**：按该文档验收会判定当前代码不达标。

**修法**：目标改为「导航可用、**不新增任何入口**」；验收改为「导航行里**没有**『添加接口』按钮」「添加接口仍可从『设置 → 点播』的管理面板进入」；§3 第一条改写为实际覆盖范围（只验证了冷启动形态，未走「添加 → 返回首页」闭环），并删掉已失效的「入口自动消失」残留项。

**附带纠错**（评审指出）：原措辞「设置 → 接口管理」在 UI 上不存在——`remote_trust_cap_config`（「接口管理」）只是 `RemoteTrustDialog` 的能力清单文案。真实路径是 设置 → `setting_vod`（「点播」）→ `HistoryDialog`（manage 模式可见的「添加」）→ `ConfigDialog`（`mobile/SettingFragment:220-222`、`leanback/SettingActivity` 同构；`mobile/HistoryDialog:112-120`）。四处代码注释与本文件全局改用该路径。

### 3.2 【medium】一键同步的陈旧回调会把「新一次同步」的界面复位（并可能真发请求）

**位置**：`app/src/main/java/com/fongmi/android/tv/ui/dialog/OneKeySyncDialog.java`。

**根因**：取消/失败/成功都是 OkHttp 线程 → `App.post` 的异步投递。`cancelSync()` 只置 `syncing=false` 并 `cancel()`；若用户在旧 `post` 尚未执行时就重新点「开始同步」，旧回调会无条件 `setSyncing(false)`：进度清零、按钮文案回「开始同步」、**方向选择器被重新启用**（这是本次 beta 改动新增的可见后果）。同一批漏网的还有三处，其中两处会触及真实世界而不只是显示：

| 位置 | 后果 |
| --- | --- |
| `startSync()` body 构建后的 `if (!syncing)` 早退 | 取消后立刻重开时 `syncing` 已是新同步的 `true` ⇒ 陈旧准备线程继续 `request(...)`，**真发一次上一轮的同步请求**并覆盖 `syncCall`（此后点「取消」取消的是错的 Call） |
| `retry()` 的 `Task.schedule` lambda | 延迟重试不校验代际 ⇒ 与新一轮同步并发、同样劫持 `syncCall` |
| `updatePrepare/updatePrepareProgress/updateUpload` | 只判 `syncing` ⇒ 旧同步的上传进度写进新同步的进度条 |

**修法**（`OneKeySyncDialog`）：

1. 新增 `private volatile long syncGeneration`；`startSync()` 入口 `final long generation = ++syncGeneration;` 并贯穿 `request/retry/callback/buildBody`。
2. 全部 4 个 `App.post(() -> { … setSyncing(false) … })` 复位点前加 `if (generation != syncGeneration) return;`。
3. body 构建后的早退条件补成 `!syncing || generation != syncGeneration`；`Task.schedule` 的 lambda 内复核代际并在陈旧时清理归档后返回。
4. 三个进度方法加 `long generation` 入参并在开头判代际（`buildBody` 相应加参；`onPrepareProgress` 由方法引用改为 lambda 传代际）。
5. `onResponse` 成功路径把三个临时归档 `delete()` 提到代际判断**之前**——陈旧回调同样要删掉它这一轮产生的缓存文件。
6. `SyncFiles.createArchive(...)` 的取消供给由 `() -> syncing` 改为 `() -> syncing && generation == syncGeneration`：陈旧准备线程不再继续打包（大包时白烧 I/O），也不再写共享节流字段。
7. `onDestroyView()` 自增代际：对话框已销毁时在途回调不再 `setSyncing(...)` 碰已解绑的视图（归档仍照常清理）。

为什么必须 `volatile`：写只在主线程（点击回调），但读发生在 `Task.execute` 的固定线程池与 OkHttp Dispatcher 线程；非 volatile 时后续自增对旧回调线程没有 happens-before，守卫会退化成「尽力而为」；且非 volatile `long` 在 32 位 ABI 上可撕裂读（JLS 17.7），把**当前**代际误判成陈旧会让该次同步再无回调、界面永久停在「同步中」。同文件的 `syncing` 出于同样原因本来就是 `volatile`。

**回归锁**：新增 `app/src/test/java/com/fongmi/android/tv/ui/dialog/OneKeySyncGenerationGuardTest.java`（**8 例**，源码扫描 + 花括号配平 + 跳过字面量，先剥注释再断言）：入口自增、4 个异步复位点全覆盖且数量锁定 + `App.post` 总数锁（防单表达式形式的复位点逃逸）、发请求前必须代际判断且先于请求调用、延迟重试 lambda 内复核、三个进度方法按代际过滤、三个归档 delete 均在代际判断之前、`onDestroyView` 作废代际、打包取消信号同时判代际。变异验证：删掉 `retry()` 里那行守卫后 `everyPostedResetComparesGeneration` **如期 FAILED**，随后文件 md5（`b47068bdcfdec2fe2d1919f12e2ca336`）逐字节还原。

### 3.3 复核后判定为**非缺陷**、按范围只报告不修

- **`PlaybackFieldPolicy.customSafe()` 不再含 `episodeId`**：`apiSafe()` 仍在运行时 `addAll(API_SAFE_EXTRA)`（`:41-47`），`PlaybackEpisodeIdTest` 因此仍绿。webhook 预设 `custom` 的字段白名单由 `PlaybackWebhookDialog.FIELD_KEYS`（23 项，不含 `episodeId`）驱动，UI 不可达；只有导入备份或手写配置能带进该字段，届时该字段静默不再出现在 webhook 载荷里 —— 属 C86 §4.1 #7 的**有意收窄**，本次只记录不修改。
- **`PlayerManager` 本次只改 javadoc**：`kind=none` 与 `chosen=` 的口径与实现一致（C86 已定论）。
- **`/api/playback/record(s)` 仍无 403 门控**：与写端点同款开关只加在 history 端点，C86 §4.4 记录为已知不对称（该端点只返回 1 条且要求正在播放）。
- **`PlaybackHistoryApi` 500 不再回显 `e.getMessage()`**：诊断双通道（`SpiderDebug.log` + `printStackTrace`）保留，`PlaybackHistoryDiagnosticsWiringTest` 用花括号配平定位 catch 块（本次 beta 已把字面缩进匹配换掉）。
- **`docs/` 里「接口管理」旧措辞**：属既有文本，本轮已随 §1 追加段一并对齐。
- **孤儿资源 `home_no_config_title/description/action`**：三语共 9 条已无 `R.string` 引用（仅两处契约测试用字面量做反向断言）。**刻意保留不删**——同族文案由 `HOME-STATE-NO-CONFIG-20261018` 引入，「错误态也保留导航」候选任务若做独立说明卡可能复用；删除属可选清理，不在本次行为范围。`values-zh-rCN` 里其上方的注释已同步改为「无引用、保留原因」。
- **契约测试用例的断言强度**：`TvErrorStateContractTest` / `MobileHomeStateContractTest` 的正向断言锁 `showContent()`/`showNoConfig()`，负向断言用被删实现的标识符做「指纹」。它们拦的是「有人把入口重新插回来」，但无法阻止用**新** id 重新插入入口。加固属额外设计面（需要结构断言而非字面量），按范围只报告。
- **`OneKeySyncDialog` 的取消在准备阶段**：`syncCall == null` 时 `cancelSync()` 同线程 `setSyncing(false)` 并提示，准备线程随后 `!syncing` 早退并删档，不依赖代际，行为不变。

## 4. 验证

执行环境：2026-10-20 Asia/Shanghai，Windows，`gradlew.bat --no-daemon --no-build-cache`（构建缓存被另一 workspace `F:/Workspace/webhtv` 长期持有，用 `--no-build-cache` 规避锁竞争；非代码问题）。`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`。

### 4.1 定向测试

| 阶段 | 目标 | 结果 |
| --- | --- | --- |
| 合并后（未修复） | leanback：`TvErrorStateContractTest`、`PlaybackHistory*`、`PreCachePolicyTest`、`HistoryDaoTest` | `BUILD SUCCESSFUL`（4m59s） |
| 合并后（未修复） | mobile：`MobileHomeStateContractTest`、`PlaybackHistory*`、`PreCachePolicyTest`、`HistoryDaoTest` | `BUILD SUCCESSFUL`（4m29s） |
| 修复后 | 上述 + `OneKeySyncGenerationGuardTest`（leanback） | `BUILD SUCCESSFUL`（3m31s） |
| 修复后 | 同上（mobile） | `BUILD SUCCESSFUL`（2m37s） |
| 第二轮修复后 | `OneKeySyncGenerationGuardTest` | `BUILD SUCCESSFUL`（12m10s，含资源重编译） |
| 收尾修复后 | `OneKeySyncGenerationGuardTest`（leanback 48s / mobile 48s） | `BUILD SUCCESSFUL`，**8/0/0** |

逐类计数（leanback，修复后）：`HistoryDaoTest` 1/0/0、`PlaybackHistoryDiagnosticsWiringTest` 4/0/0、`PlaybackHistoryLimitTest` 4/0/0、`PreCachePolicyTest` 16/0/0、`PlaybackHistoryApiTest` 6/0/0、`TvErrorStateContractTest` 14/0/0、`OneKeySyncGenerationGuardTest` 4/0/0 → 6/0/0（第二轮）→ **8/0/0（收尾，两 flavor 同值）**。

### 4.2 全量双 flavor（零新增失败）

| 目标 | 本次（收尾代码） | C82/C84 记录的基线 | 结论 |
| --- | --- | --- | --- |
| `:app:testLeanbackArm64_v8aDebugUnitTest` | **4427 tests / 8 failed / 3 skipped** | 4419 / 8 | **零新增失败**（+8 = 新增守卫测试） |
| `:app:testMobileArm64_v8aDebugUnitTest` | **5211 tests / 6 failed / 3 skipped** | 5203 / 6 | **零新增失败**（+8） |

收尾前的中间口径（守卫测试 6 例时）分别为 4425/8/3 与 5209/6/3，失败集不变；下表失败集在两种口径下逐条一致。

失败集与基线**逐条一致**：`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`、`ReaderPlaybackRoutingSourceTest`×2、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`（两 flavor）+ leanback 额外 `NativeEnhancedPlaybackStyleFocusTest`、`SearchResultDownFocusTest`。这些既有失败都是 Windows CRLF 检出产物（断言 needle 在 `git cat-file blob HEAD:<path>` 的 LF 文本里成立、在 CRLF 工作区不成立），与本任务净差异无交集。

### 4.3 静态门槛

- `bash scripts/check_ui_tokens.sh` → `UI_TOKEN_BASELINE layouts=385 hex_layouts=0 drawables=562 hex_drawables=0 colors=64 hex_colors=0 allowlisted=190` / `UI_TOKEN_SCOPE stage=A violations=0 legacy=0` / `UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28` / `UI_TOKEN_STATUS PASS`。
- `git diff --check` 为空；冲突标记扫描 `git diff HEAD | grep -cE '^\+(<<<<<<<|=======|>>>>>>>)'` = **0**。
- 变异还原校验：`md5sum` 与备份一致（`b47068bdcfdec2fe2d1919f12e2ca336`），`git status` 无残留半成品。

## 5. 影响与风险

- **生产代码改动=1 个文件**：`OneKeySyncDialog`（代际守卫 + 进度代际过滤 + 打包取消信号 + 归档清理顺序 + 销毁即作废代际）。其余为 4 处注释、1 处资源注释、1 个测试断言文案、1 个新增测试类、1 份任务文档。
- **行为变化**：陈旧回调不再复位新同步的界面/进度，陈旧准备线程与延迟重试不再发出真实请求、也不再继续打包；代际相等时（正常路径）所有判断与改前等价。
- **有意接受的可见变化**：同步进行中**关闭浮层**（下滑/返回/配置变化触发 `onDestroyView`）现在只取消并清理归档，**不再弹出「已取消」提示**——因为代际在销毁时已作废，`Notify.show` 与 `setSyncing` 一起被守卫拦下。点「取消」按钮走 `cancelSync()`（不自增代际），提示照旧。取舍理由：销毁后继续 toast 需要额外的「销毁 vs 重开」分支，收益仅一条提示，代价是把刚收敛的状态机再分叉；按「关闭即静默取消」记录在此。
- **无** API/ABI/依赖锁/补丁/native 资产/包体积变化；无持久化 schema 变化。
- 合并本身是纯并集（0 冲突、两侧路径零重叠），不改变 beta 侧任何文件内容：相对 beta 的差异恰为 dev2 自己的 5 条改动。
- 残留风险：`OneKeySyncDialog` 的方向选择器与同步状态机**没有运行时自动化覆盖**（无 `IHTTPSession`/UI 测试替身），本任务的守卫测试是源码级契约；真机结论仍以手工验证为准（C85 §6.5 已记录同一缺口）。

## 6. 回滚

- 合并本身：`git revert -m 1 2b098a691b6b6a36a8b64984c6673bcc2674b315`。
- 本任务复评修复：`git revert <本任务修复提交>`（单文件为主，无 schema、无迁移）。

## 7. 交付坐标

- 合并提交：`2b098a691b6b6a36a8b64984c6673bcc2674b315`（双父 `fbd31c11f4fe73b3f3377d46b68bf94841e89142` + `ee8b058a972577e282ed556ed9f0c4defec1869c`），recovery tag：`recovery/C87-beta-merge-review-dev2-20261020/20261020112015-2b098a691b6b`。
- 复评修复提交：`541220ac7cdb5472cd6570ccd5c0ca6031551727`（10 files changed, +467 −44；含新增 `OneKeySyncGenerationGuardTest` 与本任务文档），recovery tag：`recovery/C87-review-fixes/20261020142356-541220ac7cdb`。
- 分支 `dev2` 已推送：`5591f92612..541220ac7c`（首次 `git push` 遇到 GitHub 侧瞬时 `remote: fatal error in commit_refs`，原样重试一次即成功；非本地状态问题）。
- PR [#465](https://github.com/Silent1566/webhtv/pull/465) → `beta`，`state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`、`changedFiles=11`（+558 / −93），**只创建、不合并**。
- PR 文件集与 `git diff --name-only origin/beta origin/dev2` 逐项一致（11/11）；本「交付坐标」文档提交属于同一 PR 的后续提交。
