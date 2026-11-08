# C38：合并 `origin/beta` 最新代码并评审 dev2 未推送改动（dev2）

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合并进本地 `dev2`，确保 beta 上**被回退提交移除的内容零复活**；随后对已修改代码（含已提交未推送部分）逐轮评审并修复验证，直至通过；最后提交、推送 `dev2`、创建指向 `beta` 的 PR（只创建，不合并）。
- **验收标准**：① `origin/beta` tip 成为合并提交第二父，0 未解决冲突；② 25 个 beta 回退提交移除的每一行内容在合并结果中**零复活**；③ beta 增量**零丢失**、dev2 既有改动**零丢失**；④ 双 flavor Java 编译 + androidTest 编译通过；⑤ 全量 JVM 套件失败集合为合并前基线的**严格子集**（零新增回归）；⑥ UI token 门禁相对基线**零新增违规**；⑦ 提交 + 本地 annotated recovery tag；⑧ `dev2` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并完成（0 冲突），3 轮评审完成（第 1 轮修复 1 个门禁回归，第 2/3 轮通过），全部验证通过，待 `task_guard.sh finish`。
- **下一动作**：`task_guard.sh finish` → 推送 `dev2` → `gh pr create`（只创建不合并）。

## 冻结基线

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始时 HEAD | `c6307261f7b5e01b1ea43b6eecf39e5e4b8ba34c`（merge 上游 Silent1566 缓存管理 P0–P4） |
| 目标分支/tip | `origin/beta` / `79244f274d15ae1b624bacc6a9f68bae8eafc3e3`（Merge PR #405 from dev4） |
| Merge base | `e72239063b4122c5cfc3658231fed2b06798848a`（Merge PR #403 from dev3） |
| 合并前 dev2 领先 | `origin/dev2..dev2` = 86 个提交（含缓存管理 P0–P4 全波 + C37 文档） |
| 合并前 dev2 净差异 | `origin/beta...dev2` = 113 个路径（全部在 `app/`、`docs/` 内） |
| 初始脏路径 | 无（`git status` 干净） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`，由 task_guard `finish` 创建合并提交 |
| 回滚锚点 | `c6307261f7b5e01b1ea43b6eecf39e5e4b8ba34c` |

## beta 增量 ledger（11 提交，全部纳入）

`git log --oneline dev2..origin/beta` 的全部提交均属 beta 自身演进（PR #403 之后的 TV 焦点环统一、详情页追更墓碑判定、直播线路回退修复、诊断文档订正），**无一条与 dev2 既有实现重复或被取代**，因此全部纳入。

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `79244f274d15ae1b624bacc6a9f68bae8eafc3e3` | Merge PR #405 from dev4 | 纳入 |
| `8724d665fb3` | Merge PR #406 from dev3 | 纳入 |
| `b1ce6f88d4b` | `docs(c37): 订正 dev3 净差异路径数为实测 11 并补 PR 文件集比对证据` | 纳入 |
| `7673a81411c` | `merge: 合并 origin/beta 最新代码（PR#403/#404）并复评修复详情页追更墓碑判定` | 纳入 |
| `03dd0e76b9e` | `merge: 合并 origin/beta 最新代码（PR#402/#403）并修复直播线路回退的缓冲超时、回退耗尽与 reload 反馈缺陷` | 纳入 |
| `6d152c4a24c` | Merge PR #404 from dev1 | 纳入 |
| `f1c64ca0c8d` | `merge: 合并 origin/beta 最新代码（PR#399-#403）并复评统一 TV 焦点环为主题色边框环` | 纳入 |
| `aa5345c8354` | `fix(following): 播放页追更按钮改为就地取消，不再跳转追更页` | 纳入 |
| `96531595dc6` | `fix(live): 直播线路失效优先在本频道线路间轮换，仅节目列表拉取失败才切换下一个直播接口` | 纳入 |
| `94b7fba363a` | `统一电视版焦点高亮为主题色边框环` | 纳入 |
| `e72239063b4` | Merge PR #403 from dev3（= merge base，无净增量） | 纳入 |

## 用户核心关注点：beta 已移除（回退）内容零复活

beta 历史上有 **25 个单亲 revert 提交**（`git log --format='%H|%s' origin/beta | grep -iE '^[0-9a-f]+\|revert'`），全部是 `e72239063b` 的祖先（即已在合并 base 内，其"移除效果"必须被保留）。

**程序化校验方法**（`/tmp/c38/zero_resurrect.py`，按行比对，不依赖人工目测）：

1. 对每个 revert 提交 `R`（单亲）取其父 `R^`，得到 `removed(R, f) = lines(R^:f) − lines(R:f)`；
2. 复活定义为：该行出现在合并结果中，且**合并前 dev2 中不存在**（即它不是本地本来就有的内容）；
3. 文件被 revert 删除的情形单列（合并树中存在而本地不存在即复活）。

**结果**：

```
revert commits checked : 25
file-versions checked  : 171
removed lines scanned  : 1146
resurrected lines      : 0
resurrected files      : 0
```

**零复活**。反向交叉验证：25 个 revert 提交共触及 147 个路径，与本次 dev2 净差异（113 路径）的交集仅 10 个（`AndroidManifest.xml`、`App.java`、三套 `strings.xml`、leanback/mobile `VideoActivity`、mobile `LiveActivity`、`TmdbDetailActivityLayoutTest`、上游评估文档），逐个复核其净差异内容均为**纯新增**（缓存管理入口/权限/字符串/追更就地取消/直播回退保护），**不含任何被回退符号**。

## 合并结构与零丢失校验

| 校验 | 方法 | 结果 |
| --- | --- | --- |
| 冲突 | `git diff --name-only --diff-filter=U` + `git grep "^<<<<<<< "` | **0 未合并路径、0 冲突标记**（42 路径自动合入） |
| beta 增量零丢失 | 每条 beta 新增行是否存在于合并结果（`/tmp/c38/beta_delta_check.py`） | `beta added=11 modified=31 deleted=0`；546 条新增行**全部存在**；新增文件 0 缺失、删除文件 0 复活 |
| 本地零丢失 | 双方均改动的路径上，本地新增行是否仍可达（`/tmp/c38/local_preserve_check.py`） | 路径并集 113（beta-only 39 / dev2-only 71 / both 3）；**dev2-only 丢失 0、beta-only 未应用 0、both 丢失 0** |
| 合并结果 vs beta 净差异 | `git diff --name-status origin/beta` | 74 路径，**全部为 dev2 自身改动**（缓存管理 33 类 + 弹窗 + 布局/选择器 + 13 测试类 + 设计文档 + C37 文档），无 beta 内容被改动 |

## 评审循环记录

### 第 1 轮：发现 1 个真实缺陷并修复

**问题**：`scripts/check_ui_tokens.sh --strict` 由合并前的 `violations=1` 变为 `violations=2`。

- 新增违规文件 `app/src/main/res/color/focus_ring_error.xml`（beta PR#404 新增），与 `origin/beta` 逐字节一致；
- 根因：违规内容是该文件 **XML 注释里的裸十六进制** `#93000A` / `#690005` / `#FFDAD6`（非真实色值，仅用于解释"为什么配对 on_error_container 而不是 on_error"）。门禁按**整行正则**匹配裸 hex，注释同样触发；
- 同目录另外两个环文件（`focus_ring_primary.xml`/`focus_ring_secondary.xml`）注释中**没有**裸 hex，因此不违规 —— 说明这是该文件单独的疏漏。

**修复**：把注释中的三处裸 hex 改写为文字描述（"TV 暗色表下是一支深红"、"on_error"、"on_error_container"），并补一句说明"说明文字里也不写裸十六进制色值"。**语义零损失**（对比度数值 1.40:1 / 7.24:1 保留），**零生产行为影响**（只改注释）。

修复过程中触发一次中间失败并修正：初版注释写成 `scripts/check_ui_tokens.sh --strict`，XML 注释中**不允许出现 `--` 序列**，导致 `mergeMobileArm64_v8aDebugResources` 报 `XMLStreamException: ParseError at [row,col]:[11,34]`；改为"的 strict 模式"后编译恢复。

**修复后门禁**：`violations=1`（与合并前基线完全一致，同为既有 `item_following.xml`）、`hex_colors=0`、`contrast failures=0 min=4.28` → **相对基线零新增违规**。

### 第 2 轮：修复后复评 + 全量回归对照

**双 flavor 编译**：`compileMobileArm64_v8aDebugJavaWithJavac` + `compileLeanbackArm64_v8aDebugJavaWithJavac` → `BUILD SUCCESSFUL`；`compileMobileArm64_v8aDebugAndroidTestJavaWithJavac` + `compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac`（覆盖 beta 新带入的 2 个 androidTest）→ `BUILD SUCCESSFUL`。

**全量 JVM 套件对照**（决定性回归证据，在合并前树独立工作树 `/tmp/c38/pre` 上跑同一命令）：

| flavor | 合并前 | 合并后 | 新增回归 | 被合并修好 |
| --- | --- | --- | --- | --- |
| mobile | 5180 用例 / **11 失败** | 5185 用例 / **9 失败** | **0** | 2（`LiveActivitySourceFallbackSourceTest` ×2） |
| leanback | 4328 用例 / **12 失败** | 4340 用例 / **10 失败** | **0** | 2（同上） |

失败集合为合并前基线的**严格子集**。剩余失败全部是本机 `core.autocrlf=true` 行尾环境下的多行文本断言脆弱（CI ubuntu LF 下不触发），**且失败类读取的目标文件与 `origin/beta` 逐字节一致**（程序化核验：`activity_following.xml`、`reader.html`、`TmdbDetailActivity.java`、`activity_video.xml`、`view_tmdb_header.xml` 全部 `IDENTICAL-to-beta`），并已用 `git hash-object` 逐字节确认。

**定向测试（本次改动范围）**：`com.fongmi.android.tv.cache.*`（13 测试类）+ `LiveSourceFallbackPolicyTest` + `LiveActivitySourceFallbackSourceTest` + `LiveActivityLayoutTest` + `TmdbDetailActivityLayoutTest` + `VideoActivityLayoutTest` → `BUILD SUCCESSFUL`，0 失败。

### 第 3 轮：复评通过

- 逐文件复核 beta 带入的 3 类改动：TV 焦点环统一（`ThemeController.focusRingColor` 成为唯一代码取值源，与 `?attr/tvFocusRing` 同源，替换 5 处 Java 里写死的 `0xFFFFD166`）、详情页追更墓碑判定（`isFollowed()` 排除墓碑 + 内联播放中就地处取消）、直播线路回退（`decideLineFailure`/`decideSourceFailure` 拆分 + `advanceLineForFallback` 绕圈耗尽保护）。三者在两个 flavor 的 `LiveActivity` 中对称实现，且都有对应契约测试锁定。
- 缓存管理包（dev2 已提交未推送部分）逐类评审：`CacheModuleRegistry.validate()`（模块 ID 唯一、根必须落在 cacheDir 内、禁止重复根与树形根嵌套）、`CachePathSafety.isSymbolicLink()`（只比较最终路径组件，规避 Android `/data/user/0 -> /data/data` 别名误判）、`CacheRetentionManager`（显式 `allowedRoot` + 跳过符号链接 + 饱和加法）、`CacheCleanupManager`（运行中互斥、取消、BACK 即取消、清理后 `invalidate`+`publishChanged` 刷新）、`CacheScheduler`（每 tick 重读开关，避免关闭后仍清理）。**缓存包与上游 tip `d042cd542b` 逐字节一致**（程序化核验 0 差异）。
- `git diff --check` 退出码 0；`git status` 中全部改动路径均在 `app/`、`docs/` 内。

**结论：第 3 轮通过，无新问题。**

## 验证记录汇总

| 验证项 | 命令/方法 | 结果 |
| --- | --- | --- |
| 冲突 | `git diff --name-only --diff-filter=U` / 冲突标记检索 | 0 / 0 |
| 回退内容零复活 | 25 revert × 171 文件版本 × 1146 行，行级程序化比对 | **0 复活行 / 0 复活文件** |
| beta 增量零丢失 | 546 条 beta 新增行逐行存在性 | 0 缺失 |
| 本地零丢失 | 113 路径并集三分法 | 0 丢失 |
| 双 flavor Java 编译 | `:app:compile{Mobile,Leanback}Arm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| 双 flavor androidTest 编译 | `:app:compile{Mobile,Leanback}Arm64_v8aDebugAndroidTestJavaWithJavac` | BUILD SUCCESSFUL |
| 全量 JVM（mobile） | `:app:testMobileArm64_v8aDebugUnitTest` | 5185 用例 / 9 失败，**相对基线零新增** |
| 全量 JVM（leanback） | `:app:testLeanbackArm64_v8aDebugUnitTest` | 4340 用例 / 10 失败，**相对基线零新增** |
| 合并前基线对照 | 独立工作树 `/tmp/c38/pre`（同机同配置） | mobile 11 失败 / leanback 12 失败 |
| 定向测试 | cache.* + 直播回退 + 3 个 Layout 契约 | 0 失败 |
| UI token 门禁 | `scripts/check_ui_tokens.sh --strict` | `violations=1`（既有 `item_following.xml`）、`hex_colors=0`、`min=4.28` → 零新增 |
| 空白校验 | `git diff --check` | 退出码 0 |
| 变更范围 | `git status --porcelain` 过滤非 `app/`、`docs/` | 空 |

### 说明与边界

- **既有失败不扩大修复面**：剩余 9（mobile）/10（leanback）个失败全部为 beta 侧既有问题（本机 CRLF 行尾下的多行文本断言脆弱），失败类读取的文件与 `origin/beta` 逐字节一致，按 AGENTS.md 范围规则仅记录、不修改。
- **既有 UI token 违规**：`app/src/mobile/res/layout/item_following.xml` 的 2 处裸 hex 在合并前后均为 `violations=1` 的同一项，路径与本次改动零交集，仅记录。
- 未执行设备实机验证与低空间极限场景；本次为"beta 增量合并 + 注释级门禁修复"，风险驱动的决定性验证已覆盖编译、全量单测对照、门禁与结构校验。

## 回滚

- 任务前回滚锚点：`c6307261f7b5e01b1ea43b6eecf39e5e4b8ba34c`。
- 本次为单个 merge commit；回滚方式为 `git revert -m 1 <merge-commit>` 或重置到锚点。
- 唯一的代码改动是 `focus_ring_error.xml` 的注释文本，无运行时行为变化。

## 当前状态与下一步

- 状态：合并 + 3 轮评审 + 全部验证通过，待收尾。
- 下一动作：`task_guard.sh finish` → 推送 `dev2` → `gh pr create`（base `beta`，只创建不合并）。
