# C39：dev4 合并远端 beta 最新代码（PR#407 / 缓存管理 P0–P4）并复评已修改代码

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev4`（**远端已移除/回退的提交不得顺带带回**）；复评 dev4 全部已修改代码（含已提交未推送的 `a455e5a6cce`）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev4`、创建 `dev4 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并结果第二父为 `origin/beta` tip；② 远端被回退/剔除内容**零复活**；③ beta 增量**零丢失**、dev4 既有改动**零丢失**；④ 双 flavor Java 与 androidTest 编译通过；⑤ 全量 JVM 套件相对合并前基线**零新增回归**；⑥ UI token 门禁相对基线**零新增违规**；⑦ 净差异只含本分支自身改动；⑧ 提交 + recovery tag；⑨ `dev4` 已推送、PR 已创建且**未合并**。
- **当前状态**：**两轮合并**已完成——第 1 轮合入 `origin/beta` @ `03c239e32e1`（0 冲突），评审发现并修复 1 个真实回归（缓存确认框未走主题化构造器）；推送后 PR #409 报 `CONFLICTING`，因为 beta 又前进了 PR #408，故第 2 轮合入 `origin/beta` @ `c94e5548be1`（1 个文件冲突，已解）。两轮均为零复活、零丢失，编译/全量单测/门禁/设备实测全部通过。
- **下一动作**：`task_guard.sh finish` → 推送 `dev4` → 刷新 PR #409 描述（base `beta`，只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev4` |
| 任务开始时 HEAD | `a455e5a6cce2ee8bbee8c5683142cf5bfc8068b8`（`fix(following): 详情页追更按钮统一为就地取消并立即生效`，领先 `origin/dev4` 9 个提交） |
| `origin/beta` tip（第 1 轮） | `03c239e32e1f4501d44adb65b762af104f5b8724`（Merge PR #407 from dev2） |
| `origin/beta` tip（第 2 轮，最终） | `c94e5548be162e90b793ec5446d2f1d7231de480`（Merge PR #408 from dev3） |
| 合并基点 | `79244f274d15ae1b624bacc6a9f68bae8eafc3e3`（Merge PR #405 from dev4） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`，由 task_guard `finish` 创建合并提交 |
| 合并结果（第 1 轮） | 76 路径自动合入，**0 冲突、0 冲突标记** |
| 合并结果（第 2 轮） | 8 路径自动合入 + 1 路径冲突（`CacheManagementDialog.java`，双方做了**同一处**修复，取 beta 版），**0 冲突标记** |
| 回滚锚点 | `a455e5a6cce2ee8bbee8c5683142cf5bfc8068b8`（另打本地 tag `recovery/pre-c39-dev4-202610051417-a455e5a6cce`） |

### beta 增量 ledger（9 个提交，全部纳入）

`git log --oneline dev4..origin/beta`：

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `03c239e32e1f4501d44adb65b762af104f5b8724` | Merge PR #407 from dev2 | 纳入 |
| `2162548799f2a494ad5728ec61ff26cfe0885719` | merge：合并 origin/beta（PR#403-#406）并复评修复焦点环注释裸 hex 门禁回归 | 纳入 |
| `c6307261f7b5e01b1ea43b6eecf39e5e4b8ba34c` | merge：合并上游 webhtv/webhtv Silent1566 缓存管理（P0–P4）并适配本地主题语义 token | 纳入 |
| `d042cd542b8` | fix(cache)：评审修复死代码、测试可移植性与繁体翻译缺失 | 纳入 |
| `ff3f8d70454` | fix(cache)：修复自动清理与临时文件清理的评审问题 | 纳入 |
| `e092453640a` | Merge remote-tracking branch 'origin/main' into Silent1566 | 纳入 |
| `e24d6db5cdd` | fix(cache)：refresh settings cache value right after cleanup | 纳入 |
| `5e59ef54d9b` | fix(cache)：clean legacy paths by a versioned rule table | 纳入 |
| `5f2277724cf` | fix(cache)：restore focus to cleanup trigger | 纳入 |

**无一条与 dev4 既有实现重复或被取代**，故全部纳入。beta 增量内容为：缓存管理模块（33 个 `cache` 包类 + 弹窗 + 布局/选择器 + 13 个测试类）、缓存管理设计文档、C37/C38 文档、`SettingActivity`/`SettingFragment` 缓存入口接线、`AndroidManifest` 的 `CacheCleanupJobService` 与 `RECEIVE_BOOT_COMPLETED`、`App` 启动调度、`focus_ring_error.xml` 注释去裸 hex、`upstream-player-dependency-merge-assessment` 增量。

### dev4 侧未推送改动（本次复评对象）

| 路径 | 改动 |
| --- | --- |
| `app/src/main/java/.../ui/activity/TmdbDetailActivity.java` | 详情页「已追更」改为就地取消（移除 `isInlineFollowingPlaybackSurface` 分支与 `FollowingActivity.start` 跳页） |
| `app/src/test/java/.../ui/activity/FollowingUiSourceTest.java` | 新增就地取消源码断言 + 多行片段 CRLF 归一化 |
| `app/src/androidTest/java/.../ui/activity/TmdbDetailFollowingCancelDeviceTest.java` | 新增设备用例（不跳页 + 立即落库 + 按钮立即刷新） |
| `docs/FOLLOW-1-following-updates-design.md` | 8.3/8.3.1/入口表/变更日志同步为统一就地开关 |

## 用户核心关注点：远端已移除（回退）内容零复活

**程序化校验方法**（按行比对，不依赖人工目测）：对 beta 历史上每个**单亲 revert 提交** `R` 取父 `R^`，得到 `removed(R,f) = lines(R^:f) − lines(R:f)`；复活定义为「该行出现在合并结果中，且合并前 dev4 中不存在」。

```text
revert commits checked : 28
file-versions checked  : 194
removed lines scanned  : 1773
resurrected lines      : 0
resurrected files      : 0
```text

**PR 方向反向校验**（关键：PR 的 base 是 beta，若合并结果里有 beta 已删除的行，PR 会把它们带回 beta）：

```text
reverts=28 file-versions=194
PR would re-add removed lines : 0
PR would re-add removed files : 0
```text

**beta 增量零丢失**（`79244f274d1..origin/beta`，76 路径）：

```text
added=52 modified=24 deleted=0
missing added files      : 0
missing added/mod lines  : 0
deleted-but-present files: 0
```text

**dev4 侧零丢失**：dev4 未推送提交的 4 个路径逐字节等于 `a455e5a6cce` 的 blob；beta 增量 76 个路径**逐字节等于** `origin/beta`（CRLF 归一化后比对，76/76 一致）。双方改动路径**零交集**（dev4-only 4 路径、beta-only 76 路径、both 0），因此合并对两侧均为纯叠加。

**合并结果净差异**：合并刚完成、尚未叠加本轮修复与本文档时，`git diff --name-status origin/beta` = 恰好 **4 个路径**（dev4 侧 4 个路径），全部为 dev4 自身改动，0 个 beta 内容被 dev4 单方面改写。

**提交后 PR 净差异（最终）**：`git diff --name-status origin/beta dev4` = **6 个路径**（4 个 dev4 原有路径 + 本轮新增的 `CacheManagementDialog.java` 修复 + 本文档），全部为本分支改动，**零 beta 内容被改写**。

## 评审循环记录

### 第 1 轮：发现并修复 1 个真实回归（合并引入，阻断验证）

**问题**：`ThemeBinderContractTest#materialAlertDialogsAreBuiltThroughTheThemedBuilder` 由合并前 **26/26 通过** 变为合并后 **26 项 1 失败**：

```text
java.lang.AssertionError: build these through WebHtvAlertDialogBuilder instead:
  [main/CacheManagementDialog.java -> MaterialAlertDialogBuilder]
```text

- **归属判定（程序化）**：该测试与 `CacheManagementDialog.java` 在合并前 dev4 中**均不存在**（`CacheManagementDialog` 由 beta 的缓存管理 P0–P4 引入）；在**纯 `origin/beta` 树**上独立复跑，同一用例**同样失败** → 缺陷由 beta 增量带入，不是 dev4 侧改动引入，也不是合并策略问题。
- **为什么必须在本任务修**：CI（`.github/workflows/android-release.yml`）只跑一小撮 release-critical 用例，**不包含**该契约测试，因此该回归不会被 CI 拦住；而 PR 的目标是让 beta 变好，把一个「合并后必然失败的门禁」原样送进 beta 会立即污染 beta 基线。
- **根因**：`CacheManagementDialog` 的 3 处确认框（轻度/标准清理 `confirm()`、单模块 `confirmModule()`、深度清理两步 `confirmDeep()`）直接 `new MaterialAlertDialogBuilder(requireContext())`。裸 Material 构造器的 `create()` 不会把对话框窗口交给 `ThemeController.bindDialog(...)`，因此自定义主题下这些面板与文字仍停留在编译期调色板（`WebHtvAlertDialogBuilder` 的类注释记录的就是这个缺陷模式）。
- **修复**：三处改用 `new WebHtvAlertDialogBuilder(requireContext())`，导入 `com.fongmi.android.tv.theme.WebHtvAlertDialogBuilder`，移除不再使用的 `MaterialAlertDialogBuilder` 导入。与既有 `BaseAlertDialog.builder()` 的写法同源。
- **零行为影响论证**：`WebHtvAlertDialogBuilder` 只在 `create()` 后追加 `bindWindowBackground` + `bindDialog`，而两者在「活动 token == 冻结基线」时**直接 return**（`ThemeBinder.bind` 的 `baseline.equals(active)` 短路、`bindWindowBackground` 同样短路），默认主题下与原来**完全等价**；用户自定义主题时才会额外绑定，即修复目标本身。

### 第 2 轮：修复后复评 + 全量回归对照（决定性证据）

**全量 JVM 对照**（在真实 git worktree `F:/temp/c39/base`（`a455e5a6cce`，带 `.git`）上跑同一命令取得基线）：

| flavor | 合并前基线 | 合并+修复后 | 新增回归 |
| --- | --- | --- | --- |
| mobile | 5122 用例 / **6 失败** | 5185 用例 / **6 失败** | **0** |
| leanback | 4277 用例 / **7 失败** | 4340 用例 / **7 失败** | **0** |

失败集合与基线**逐条完全相同**（不是「数量相同」，而是同一组用例 ID）：mobile `PlayerPlaybackRegressionSourceTest`、`ReaderPlaybackRoutingSourceTest`×2、`TmdbSourceOnlyInteractionTest`、`DialogRoundedCornerSourceTest`、`WebThemeTokenSourceTest`；leanback 上述 6 项（`PlayerPlaybackRegressionSourceTest` 在 leanback 无此用例）+ `NativeEnhancedPlaybackStyleFocusTest`、`SearchResultDownFocusTest`。全部为**本机 `core.autocrlf=true` 行尾环境下多行源码文本断言脆弱**，且失败用例读取的目标文件与 `a455e5a6cce`/`origin/beta` **逐字节一致**（程序化核验 `reader.html`、`PlaybackActivity`、`NovelRouter`、`ReaderHistory`、`WebReaderActivity`、两个 `VideoActivity` 等全部 IDENTICAL）→ 与本次改动零交集，按 AGENTS.md 范围规则仅记录、不修改。

**修复的有效性双向验证**：修复前该用例失败、修复后 26/26 通过（同一棵树、同一命令）。

**其余复评项**：

- **dev4 侧就地取消语义**（`TmdbDetailActivity`）：`onFollowing()` 的「已追更」分支只走 `cancelFollowing`；`cancelFollowing` = `FollowingScheduler.cancelNext` → `FollowingPlaybackBridge.deleteAsync` → 成功后 `updateFollowingState()` + `refreshUnreadCountAsync` + `Notify.show(following_canceled)`，失败时恢复按钮可用并提示错误；`followingActionPending` 在两条回调路径都复位，不会卡死按钮。与 mobile/leanback `VideoActivity` 的 `cancelFollowing` 语义一致。`isFollowed(item)` = `item != null && !item.isDeleted()`，正确处理 `resolveTmdb` 为防复活**故意返回墓碑行**的 C9 契约。`FollowingActivity.start` 已从该页彻底移除（全仓检索确认详情页无残留调用点），列表页仍是唯一管理入口。
- **beta 带入内容**：缓存管理包、`CacheCleanupJobService`（`exported=false` + `BIND_JOB_SERVICE` 权限）、`RECEIVE_BOOT_COMPLETED`（对应 leanback `BootReceiver` 既有消费方）、`App` 启动 30s 后调度（`CacheScheduler.start()` 内部先读开关，关闭时不注册任何任务）。13 个 cache 测试类在合并后树上 0 失败。
- **`git diff --check`**：退出码 0；全部改动路径均在 `app/`、`docs/` 内。

### 第 3 轮：对修复后的合并树再做一次独立复评

对「合并 + 第 1 轮修复」后的整棵树重新逐路径评审，重点是 beta 增量里**新引入**的文件与 dev4 侧改动的交互面：

- **`CacheManagementDialog`（本轮修复对象）**：三处确认框现均为 `WebHtvAlertDialogBuilder`；`focusNegativeOnShow`/`restoreFocusOnDismiss`/深度清理两步切换逻辑未被改动；`ChoiceDialog`（限额/保留期/总计）走的是自带 `ThemeController.bindDialog` 的 `BaseAlertDialog` 路径，无同类遗漏。全仓检索 `new MaterialAlertDialogBuilder(` 只剩 `CrashActivity`（该契约测试明确豁免，见测试内注释）→ **同类问题已无残留**。
- **`CachePathSafety.isSymbolicLink`**：只比较**最终路径组件**的规范名，规避 Android `/data/user/0 -> /data/data` 祖先别名把每个 cache 路径都误判为符号链接（否则清单恒为 0 字节、清理静默跳过全部文件）。逐行复核逻辑正确。
- **`CacheModuleRegistry`**：`UNCLASSIFIED` 的「反向规则」（凡已知所有者未认领的都归它）是唯一有意为之的取反，且该模块声明 `allowManualCleanup=false` + `allowAutomaticCleanup=false`，**结构上不可能删除它自己报告的文件**；`PROTECTED_NAMES`（mpv recovery 三件套）在 `TEMP_FILES` 与 `UNCLASSIFIED` 两处都被排除；`DIAGNOSTIC_LOG_NAMES` 防止未来改后缀时把活跃日志误判为临时文件。`CachePolicyEngine.deepModules()` 显式剔除 `DIAGNOSTIC_LOGS`/`UNCLASSIFIED`，分层清理不会误删诊断证据。
- **`CachePolicyEngine.manualCleanupAllowed`**：从注册表读 `protection().allowManualCleanup()`，不按调用点硬编码，纯函数（只依赖传入的 cache 目录），可测且不随运行时漂移。
- **`CacheCleanupJobService` 接线**：`exported=false` + `android:permission="android.permission.BIND_JOB_SERVICE"`，无越权暴露面；`RECEIVE_BOOT_COMPLETED` 对应既有 leanback `BootReceiver`，不是新增无消费方的权限。
- **`App` 启动调度**：`post(() -> CacheScheduler.get().start(), 30_000L)`；`CacheScheduler.start()` 首行 `CachePolicyStore.migrate()`，随后 `isAutoCleanupEnabled()` 为假**直接 return**（不注册 Job、不起线程），默认关闭状态下零开销、零后台唤醒。
- **dev4 侧就地取消与 beta 增量的交互**：双方改动路径**零交集**；`TmdbDetailActivity` 的追更状态机不依赖缓存管理引入的任何符号（`CacheManagementDialog`/`cache.*` 均未被引用）。
- **`git diff --check` / `git diff --cached --check`**：退出码 0；改动路径全在 `app/`、`docs/` 内。

**结论：第 3 轮通过，无新问题。**

### 第 4 轮：beta 前进后的第二次合并与复评（PR #409 报 CONFLICTING）

第 1 轮合并推送后创建 PR #409，GitHub 报告 `mergeable=CONFLICTING`。`git fetch origin` 显示 `origin/beta` 已从 `03c239e32e1` 前进到 `c94e5548be1`（Merge PR #408 from dev3，3 个新提交），因此冲突是 **base 前进** 造成的，与第 1 轮合入内容无关。

**beta 第二轮增量**（`03c239e32e1..origin/beta`，8 路径）：

| 完整 commit ID | 内容 |
| --- | --- |
| `c94e5548be162e90b793ec5446d2f1d7231de480` | Merge PR #408 from dev3 |
| `bbaf19b97208655b618047f2395af6f4cd67a3a9` | merge：合并 origin/beta（PR#407 缓存管理上游同步）并复评修复主题契约与安全区 insets 缺陷 |
| `3e4e49331a4d72879e8df341c1c79275c9d25a7f` | fix(theme)：fit editor dialog inside system bar safe area |

**唯一冲突及解决**：`app/src/main/java/com/fongmi/android/tv/ui/dialog/CacheManagementDialog.java`。

- **冲突原因**：dev3 在本轮（`3e4e49331a4` 所属的 PR #408）**独立做出了与第 1 轮完全相同的修复**——把三处确认框从裸 `MaterialAlertDialogBuilder` 换成 `WebHtvAlertDialogBuilder`。双方改的是同一处、结果等价，只有我额外加的两行中文注释不同，于是产生文本冲突。
- **解决方式**：取 **beta 版**（`git checkout --theirs`）。理由：两份修复功能等价，取 beta 版可让该文件与 `origin/beta` **逐字节一致**，不把纯注释差异带进 PR，减少 review 噪声与后续冲突面。
- **证据**：解决后 `git diff origin/beta -- .../CacheManagementDialog.java` **为空**；`ThemeBinderContractTest` 在本轮全量套件中 **26/26 通过**（`failures=0`）——门禁目标依然达成，只是修复来源变成了 beta 自身。
- **对本 PR 的影响**：第 1 轮的 `CacheManagementDialog` 修复**已被 beta 的超集覆盖**，不再出现在最终净差异里；本 PR 的净差异回到**纯 dev4 自身改动**。

**第二轮零复活 / 零丢失复核**（合并后重跑，全部程序化）：

```text
beta delta (03c239e32e1..origin/beta): added=1 modified=7 deleted=0
  missing added files=0 / missing lines=0 / deleted-but-present=0
reverts=28 file-versions=194 removed-lines=1773
  resurrected lines=0 files=0
PR direction: would re-add removed lines=0 files=0
beta-side paths byte-identical to origin/beta: 8/8
```

**第二轮全量回归对照**（与前一轮合并树及任务前基线比对）：

| flavor | 任务前基线 `a455e5a6cce` | 第 1 轮合并 | 第 2 轮合并（最终） | 新增回归 |
| --- | --- | --- | --- | --- |
| mobile | 5122 / 6 失败 | 5185 / 6 失败 | **5191 / 6 失败** | **0** |
| leanback | 4277 / 7 失败 | 4340 / 7 失败 | **4346 / 7 失败** | **0** |

失败集合与任务前基线**逐条相同**（`NEW=[]`、`gone=[]`）；用例数增长来自 beta 新增的 `ThemeDialogLayoutTest` 等。

**复评 beta 第二轮带入的改动**（dev3 的 C39 任务，已有 `docs/C39-beta-merge-review-dev3-20261005.md` 记录）：

- `ThemeDialogLayout` 新增 `safeArea`/`Insets`（含 `ofContent` 回退），`ThemeDialog` 两 flavor 改为按系统栏安全区定尺寸，并安装 `OnApplyWindowInsetsListener` 在真实 insets 到达后**重新定尺寸**——修的是「浮动对话框窗口在 API 28 上报 `systemBars()=[0,0,0,0]`，导致页脚行被导航栏遮住」。回退链为「对话框窗口报告 → 宿主窗口 insets → 宿主内容区已让出的空间」，三级都只可能**多留**空间、不会裁切，逻辑保守正确。
- 与 dev4 侧追更就地取消**零文件交集**，无交互风险。
- 双 flavor Java + androidTest 编译 BUILD SUCCESSFUL；UI token 门禁 `violations=1`（既有 `item_following.xml`），与基线相比零新增。

**结论：第 4 轮通过。** 冲突由 base 前进引起且已按「取 beta 等价版本」最小化解决；零复活、零丢失、零新增回归均成立。

## 验证记录

| 验证项 | 命令/方法 | 结果 |
| --- | --- | --- |
| 合并冲突（第 1 轮） | `git diff --name-only --diff-filter=U` + 冲突标记检索 | 0 / 0 |
| 合并冲突（第 2 轮，base 前进后） | 同上 | 1 路径（`CacheManagementDialog.java`，双方同一处修复）→ 取 beta 版解决，0 冲突标记 |
| 回退内容零复活（两轮均重跑） | 28 revert × 194 文件版本 × 1773 行，行级程序化比对 | **0 复活行 / 0 复活文件** |
| PR 反向（会不会把已删内容带回 beta） | 同上，方向取「合并结果有、beta 没有」 | **0 行 / 0 文件** |
| beta 增量零丢失（第 1 轮） | 76 路径逐行存在性 | 0 缺失 |
| beta 增量零丢失（第 2 轮） | 8 路径逐行存在性 | 0 缺失 |
| beta 路径逐字节（第 2 轮） | 8/8 与 `origin/beta` 一致（CRLF 归一化） | 一致 |
| dev4 路径逐字节 | 4/4 与 `a455e5a6cce` 一致 | 一致 |
| 净差异（第 1 轮合并刚完成时） | `git diff --name-status origin/beta` | 恰好 4 路径，全部为 dev4 自身改动 |
| PR 净差异（第 1 轮提交后） | `git diff --name-status origin/beta dev4` | 6 路径（含第 1 轮修复） |
| PR 净差异（第 2 轮合并后，最终） | `git diff --name-status origin/beta` | dev4 自身 4 路径 + 本文档；`CacheManagementDialog` 修复已被 beta 超集覆盖 |
| 双 flavor Java 编译 | `:app:compile{Mobile,Leanback}Arm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| 双 flavor androidTest 编译 | `:app:compile{Mobile,Leanback}Arm64_v8aDebugAndroidTestJavaWithJavac` | BUILD SUCCESSFUL |
| 全量 JVM（mobile） | `:app:testMobileArm64_v8aDebugUnitTest` | 第 2 轮合并后 **5191 用例 / 6 失败**，**相对基线零新增** |
| 全量 JVM（leanback） | `:app:testLeanbackArm64_v8aDebugUnitTest` | 第 2 轮合并后 **4346 用例 / 7 失败**，**相对基线零新增** |
| 合并前基线对照 | 真实 worktree `F:/temp/c39/base` @ `a455e5a6cce` | mobile 5122/6、leanback 4277/7 |
| 门禁回归修复 | `ThemeBinderContractTest` | 第 1 轮修复前 26/1 → 修复后 26/0；第 2 轮 beta 带入 dev3 等价修复后仍 **26/0** |
| UI token 门禁 | `scripts/check_ui_tokens.sh --strict` | `violations=1`（既有 `item_following.xml`）；基线为 `violations=2` → **零新增，且少 1 项** |
| 空白校验 | `git diff --check` / `git diff --cached --check` | 退出码 0 |
| 设备覆盖安装 | `adb -s 127.0.0.1:5561 install -r -t`（mobile arm64 debug，签名一致 `95e4b2e7…7e4d`，未卸载） | `Success` |
| 设备实测：缓存管理入口 | 首页 → 设置 → 缓存管理 | 面板正常渲染，`cacheText` = `68.1 MB / 783.1 MB` |
| 设备实测：模块限额 ChoiceDialog | 点「限额」 | 选项列表正常弹出 |
| 设备实测：轻度清理确认框（修复点） | 点「轻度清理」 | 标题「确认清理缓存？」+ 取消/确定，**焦点落在「取消」** |
| 设备实测：深度清理两步确认（修复点） | 点「深度清理」→「继续」 | 第一步「取消/继续」→ 同窗口第二步「不可撤销」+「取消/开始深度清理」，**焦点两次都落在「取消」**，无叠加弹窗 |
| 设备实测：崩溃检查 | `adb logcat -b crash` | 无本次相关新崩溃 |

### 说明与边界

- **既有失败不扩大修复面**：剩余 6（mobile）/7（leanback）个失败全部为合并前基线的同一组用例，读取目标文件与两侧提交逐字节一致，属本机 CRLF 环境下的多行文本断言脆弱，按 AGENTS.md 范围规则仅记录、不修改。
- **既有 UI token 违规**：`app/src/mobile/res/layout/item_following.xml` 的裸 hex 在合并前后同为既有项，与本次改动零交集。
- 未执行低空间极限场景、JobScheduler 长期调度与真机（非模拟器）验证；本次为「beta 增量合并 + 1 处门禁回归修复」，风险驱动的决定性验证已覆盖编译、全量单测对照、门禁、结构零复活/零丢失校验与设备实测三条确认框路径。

## 回滚

- 任务前回滚锚点：`a455e5a6cce2ee8bbee8c5683142cf5bfc8068b8`（本地 tag `recovery/pre-c39-dev4-202610051417-a455e5a6cce`）。
- 本任务共 2 个 merge commit（第 1 轮 `b19ccde8791`、第 2 轮为最终合并提交）；回滚方式为 `git revert -m 1 <merge-commit>` 或重置到锚点。
- 第 1 轮的 `CacheManagementDialog` 修复已因 beta 带入 dev3 等价修复而在第 2 轮被取 beta 版覆盖（该文件与 `origin/beta` 逐字节一致）；最终 PR 净差异**只含 dev4 自身改动 + 本文档**，无额外代码变更。

## 提交与推送

- 本任务产物：2 个 merge commit（第 2 个含本文档）+ 各自 recovery tag。
- PR：#409 `dev4` → `beta`，中文描述，**只创建不合并**。

## Next action

`task_guard.sh finish` → 推送 `dev4` → 刷新 PR #409（base `beta`，只创建不合并）。
