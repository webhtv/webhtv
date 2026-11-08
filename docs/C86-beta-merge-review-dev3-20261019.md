# C86-beta-merge-review-dev3-20261019

## Recovery anchor

- **目标**：把 `origin/beta@0bb2601e3871f58b727873837112abe86784d61f` 合并进 `dev3`（零复活已被回退的内容、零丢失），复评合并带入的改动与 dev3 已提交未推送的 4 个提交，修复并验证通过，提交、推送、创建到 `beta` 的中文 PR（只创建不合并）。
- **验收标准**：
  1. 合并结果不含任何在 `beta` 上已被 revert 删除的路径（零复活），且不丢失 `beta` 树上的任何路径（零丢失）。
  2. 合并带入的 24 个 `beta` 路径与 dev3 未推送的 4 个提交全部经只读复评，发现的必修项已修并有可证伪证据。
  3. 双 flavor arm64 Debug Java 编译通过；定向单测全绿；`scripts/check_ui_tokens.sh` PASS。
  4. 合并提交保留双父（`--no-ff`），经任务守卫 `finish` 落盘并创建 recovery tag。
  5. 推送 `dev3`，创建到 `beta` 的 PR（中文描述，只创建不合并）。
- **Lane**：`standard`
- **Scope**：`app/`、`docs/`
- **任务守卫**：`C84-beta-merge-review-dev3-20261019`（本任务后续因评估索引 ID 三次撞号让位改号 `C86`；已落盘的提交 subject 与 recovery tag 名仍含 `C84`，按 AGENTS.md §6 不回写历史），base HEAD `c88f2896ac00d4ea663d7a56b4c16dfa4274a1cf`，branch `dev3`，受保护脏路径 0
- **基线**：merge-base `0e81bae1c7d1a91cd229dc5e1663d62f202548b3`（= `dev3` HEAD~2，`beta` 已验证包含它）
- **当前状态**：**已完成**（合并、复评、修复、验证、提交、推送、PR 全部落地）
- **交付坐标**：合并提交 `13a093ad3745b85f6cece8e501d45cb76762b917`；recovery tag `recovery/C84-beta-merge-review-dev3-20261019/20261019184918-13a093ad3745`；已推送 `dev3`；PR **#463**（`base=beta`/`state=OPEN`/`mergedAt=null`，只创建不合并）
- **下一动作**：无。后续由评审方决定是否合并 PR #463。

---

## 1. 合并对象

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `0bb2601e3871f58b727873837112abe86784d61f` |
| 本地 | `dev3` = `c88f2896ac00d4ea663d7a56b4c16dfa4274a1cf`（相对 `origin/dev3` ahead 4，全部未推送） |
| merge-base | `0e81bae1c7d1a91cd229dc5e1663d62f202548b3` |
| 冲突 | 唯一 `UU`：`docs/upstream-player-dependency-merge-assessment-2026-08-20.md` |
| 合并提交 | `13a093ad3745b85f6cece8e501d45cb76762b917`（双父 `c88f2896ac00d4ea663d7a56b4c16dfa4274a1cf` + `0bb2601e3871f58b727873837112abe86784d61f`） |

`git merge-tree --write-tree --name-only dev3 origin/beta` 预演树 `133d1ffd775291e2fa36d01f20415163cc3febc6`，唯一冲突同上；`git merge --no-ff --no-commit origin/beta` 实合并冲突复现一致。

### 1.1 冲突根因与解决

**根因是评估索引 ID 撞号**：dev3 未推送的 `C81` 行（上游同步任务）与 `beta` 已发布的 dev4 `C81` + dev2 `C82` 行占据同一编号段。

**解决**：让位原则——未推送让位已发布。`beta` 的两行保留在前，dev3 的行改号 `C83` 排在其后。历史先例：`dev3` 的 PR #455 占用 `C77` → dev2 任务由 `C77` 改 `C78`；`dev4` 的 PR #458 占用 `C80` → dev4 任务由 `C80` 改 `C81`。

配套改名（同一原子改动内完成）：

- `git mv docs/C81-upstream-sync-silent1566.md docs/C83-upstream-sync-silent1566.md`（本任务之后，因 beta 又发布 dev4 的 `C83`，该文档在 `C84-conflict` 会话中二次改名为 `docs/C85-upstream-sync-silent1566.md`）
- 文档内 `# C81 —` → `# C83 —`、`C81-upstream-sync-silent1566` → `C83-upstream-sync-silent1566`、改动清单路径行、索引行说明
- 索引行 `[C83-upstream-sync-silent1566.md](C83-upstream-sync-silent1566.md)` 链接与撞号让位说明

冲突标记残留 0（`git grep -E '^(<<<<<<<|>>>>>>>|=======)$'` 为空），`git diff --name-only --diff-filter=U` 为空。

---

## 2. 零复活 / 零丢失证据

口径与 `C78`/`C79`/`C82` 一致，脚本 `.codex/tmp-c84-revival-check.sh`（仅做集合计算，不改仓库）。

树对象：合并结果 `f81fd3b1395448694da8cf678e7d775ab73544f3`、`beta` `6893f0f5f1f21ca0fd38a383503510509b40e5a1`、`dev3` `38b556d1d1abe42733606b0fc5924ace47fcc39d`。

| 判据 | 含义 | 结果 |
| --- | --- | --- |
| 路径数 | merge / beta / dev3 | 5152 / 5151 / 5145 |
| **A** | 合并树 − `beta` 树（路径级） | **1**，仅 `docs/C85-upstream-sync-silent1566.md`（本任务自己的新文档） |
| **B** | `beta` 树 − 合并树（丢失面） | **0** |
| revert 集合 | 主题行锚定（`tolower(subject)` 以 `revert`/`回退`/`撤销` 开头，限 dev3+beta lineage） | 27 提交 / 44 条删除路径 |
| **C** | revert 删除 ∧ 不在 `beta` 树 ∧ 在合并树（复活面） | **0** |
| **D** | 检测器自检 | 探针 `app/src/main/assets/themes/index.json` 确为 revert 删除且不在 `beta` 树（有效探针），注入伪造合并树后如期报出 ⇒ 检测器非空转 |
| **E** | 内容级 `git diff origin/beta <merge_tree>` | 恰 **4** 路径，全部为本地未推送内容 |

**E 的 4 路径**：`app/src/main/java/com/fongmi/android/tv/ui/dialog/OneKeySyncDialog.java`、`app/src/main/res/layout/dialog_one_key_sync.xml`、`docs/C85-upstream-sync-silent1566.md`、`docs/upstream-player-dependency-merge-assessment-2026-08-20.md`。

### 2.1 共享 UI 文件核查（关键）

merge-base `0e81bae1c7` = `review(ui): 补全观影记录同步守卫契约并消除配平器静默失准`。

- `git diff <base> origin/beta -- OneKeySyncDialog.java dialog_one_key_sync.xml` = **空** ⇒ `beta` 未触碰这两个文件。
- `git diff dev3 <merge_tree> -- <同两文件>` = **空** ⇒ 合并结果逐字节保留 dev3 版本（含 `C85` 的主题 token 化与方向选择器）。
- `git diff dev3 origin/beta` 显示 `beta` 侧这两个文件仍是**上游旧版**（`modeButton`/`toggleMode`），即 `beta` 确实落后于 dev3 的 `C81`/`C85` 成果，合并方向正确。

---

## 3. 合并带入的 `beta` 改动面

25 路径 / 1641 insertions / 32 deletions。

**生产代码**：`HistoryDao.java`(+15)、`PlaybackApi.java`(+81)、`PlaybackFieldPolicy.java`(+18)、`PlaybackProgressInput.java`(+12)、`PlaybackProgressWriter.java`(+47)、`PlaybackRecord.java`(+30)、`PlayerManager.java`(+6)、`exo/PreCache.java`(+17)、`server/Nano.java`(+2)、`server/process/PlaybackHistoryApi.java`(+144，新增)、`ui/dialog/SiteHealthReportDialog.java`(+48)。

**测试**：`HistoryDaoTest.java`、`PlaybackEpisodeIdTest.java`(新)、`PlaybackHistoryDiagnosticsWiringTest.java`(新)、`PlaybackHistoryLimitTest.java`(新)、`PlaybackProgressWriterEpisodeTest.java`、`PlayerManagerSwitchCauseSourceTest.java`、`exo/PreCachePolicyTest.java`、`PlaybackHistoryApiTest.java`(新)、`SiteHealthReportDialogSourceTest.java`。

**文档**：`docs/C82-beta-merge-review-dev2-20261019.md`(新)、`docs/MPV-BUFFERING-STALL-WATCHDOG-20261017.md`、`docs/PC-REALTIME-SYNC-01-android-contract-gap.md`(新)。

---

## 4. 复评发现的缺陷与修复

两路只读复评（各 2 份回报，共 4 份，结论一致）：**A 路**评 `beta` 带入的 24 路径，**B 路**评 dev3 未推送的 4 提交。**无 blocker**。首要核查项通过：`PreCache.isHardAutomaticPause()` 已含 `"foreground-eof-recovery"`（`app/src/main/java/com/fongmi/android/tv/player/exo/PreCache.java:963`），`C82` §3.1 的 blocker 在 `beta` 侧已修并被本次合并带入；dev3 的主题 token 化、`PlayerManager` switch-cause 归因、`SiteHealthReportDialog` 对比度均未被回退。

### 4.1 已修复（代码侧 8 项）

| # | 位置 | 问题 | 修复 |
| --- | --- | --- | --- |
| 1 | `app/src/main/java/com/fongmi/android/tv/server/process/PlaybackHistoryApi.java:70` | 无观影记录同步门控：关闭后局域网仍可 `GET` 走整站最多 100 条历史（`vodName`/`episodeName`/`positionMs`），暴露面远大于 `/api/playback/current`（1 条且要求正在播放） | 与写端点 `PlaybackProgressApi.java:43` 同口径加 `if (!ViewingRecordSyncStore.isEnabled()) return cors(error(FORBIDDEN, 403, "观影记录同步未开启"), session);`，位置在 `OPTIONS`(204) → 非 `GET`(405) 之后、`siteKey` 校验(400) 之前 |
| 2 | 同文件 `:88` | 500 回显 `e.getMessage()`，本端点局域网免鉴权可达，SQLite 异常文本泄露表名/列名 | 改固定文案 `"读取播放历史失败"`，诊断仍走 `SpiderDebug.log` + `printStackTrace` |
| 3 | `app/src/test/java/com/fongmi/android/tv/db/dao/HistoryDaoTest.java` | `substr` 而非 `LIKE` 是核心安全决策（siteKey 可能含 `%`/`_`），但原断言只查「查询里有 `limit`」，变异成 `LIKE` 仍全绿 | 增断言锁 `substr(\`key\`, 1, length(:keyPrefix)) = :keyPrefix` 且 `assertFalse(... contains("LIKE"))` |
| 4 | `app/src/test/java/com/fongmi/android/tv/playback/PlaybackHistoryDiagnosticsWiringTest.java:57` | `catchBlock` 用字面缩进 `"\n        }"` 定位块尾，失配时回退 `source.length()` ⇒ 块体延伸到文件尾，两条断言可恒真 | 改花括号配平扫描；缺配对时抛 `IllegalStateException` |
| 5 | `app/src/test/java/com/fongmi/android/tv/server/process/PlaybackHistoryApiTest.java` | 类 javadoc 称「HTTP 状态码/CORS/响应体由集成路径覆盖」，但该集成测试不存在 | 改为如实声明「HTTP 层目前无自动化覆盖，靠真机手工验证」 |
| 6 | `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java:1047` | `logSwitchCause` javadoc 写 `chosen=` 是「是否用户显式选定」，与本次合并带入的 `docs/MPV-BUFFERING-STALL-WATCHDOG-20261017.md` §4.3 权威口径相反；`kind=kernel\|decode` 漏了实现会产出的 `none` | `chosen` 改为「本次切换是否把用户选定的内核记为会话内核（即是否执行 `PlayerSetting.putActivePlayer(type)`）」；`kind` 补 `none` |
| 7 | `app/src/main/java/com/fongmi/android/tv/playback/PlaybackFieldPolicy.java` | `customSafe()` 含 `fields.addAll(API_SAFE_EXTRA)`，使 `episodeId` 进入 webhook 自定义字段白名单，而 `PlaybackWebhookDialog.FIELD_KEYS`（23 项）不含它 ⇒「能被配置但界面上发现不了」的隐式放宽 | 移除该行，恢复 webhook 白名单既有行为 |
| 8 | `app/src/test/java/com/fongmi/android/tv/player/exo/PreCachePolicyTest.java` | 硬暂停守卫只维护「硬暂停表」再逐项比对，新增 `Reason` 时两张表都是 `false`，`false == false` 恒真 ⇒ javadoc 声称的「新增原因时会立即失败」不成立 | 补「降级表」，断言两表并集恰好覆盖 `Reason.values()` 全集 |

### 4.2 已修复（`OneKeySyncDialog` 2 项，既有缺陷，非本任务引入）

| # | 位置 | 问题 | 修复 |
| --- | --- | --- | --- |
| 9 | `app/src/main/java/com/fongmi/android/tv/ui/dialog/OneKeySyncDialog.java` `updateDirectionCard()` | 4 处 Java 裸 hex（`#E8F0FE`/`#F8FAFD`/`#174EA6`/`#5F6368`）在运行时覆盖布局 token，深色/自定义主题下两张方向卡仍是浅底深字。来源 `7e5106e055`，在 merge-base 中已存在 | 改用 `ThemeController.current()` 的成对语义色：sender 卡 `colorPrimaryContainer` + `colorOnPrimaryContainer`，非 sender 卡 `colorSurfaceContainerLow` + `colorOnSurfaceVariant`；描边 sender `0` / 非 sender `ResUtil.dp2px(1)` |
| 10 | 同文件 `startSync()` / `setSyncing()` | `toRemote` 非 volatile：主线程取 `mode` 拼 URL，工作线程再读 `toRemote` 决定请求体（`backup`/`device`/`remoteRelay`），同步中翻方向会产生方向自相矛盾的请求 | 入口快照 `final boolean toRemote = this.toRemote;` 并贯穿 `buildBody(...)`；同步期间禁用两个方向按钮（`ViewGroup.setEnabled` 不传播到子 View，故逐个按钮设） |

`scripts/check_ui_tokens.sh` 只扫 `res/` 资源、**不扫 Java**，故第 9 项不被该门禁覆盖，靠编译 + 人工核验。

### 4.3 已修复（文档侧）

- `docs/C85-upstream-sync-silent1566.md`：§9「交付坐标与下一步」由「（实施后填写）」改为实际坐标；Recovery anchor 的「当前状态」/「下一动作」更新为已交付；§5.2 净值陈述改为与命令相符（预览树只含上游 2 路径，真实交付树净差 4 路径）；§6.2 编译命令拆成可照抄的多行；§7 补记任务文档自身的 `R097` 重命名；补 `C81`→`C83` 标识符映射说明；§6.5 补 `C84` 复评对该弹窗的两处修复。
- `docs/PC-REALTIME-SYNC-01-android-contract-gap.md`：§10.1 的「17 个用例」改为 18（8+4+6）；§6 V1 补 `substr` 守卫判据、V2 标注未自动化并点明 405/403 无证据；§10.3 第 1 项标注静态初始化顺序约束已废止；新增 §11.7 记录本轮 403 门控、固定文案、`total` 语义、`episodeId` 已知限制与同名不同义。
- `docs/upstream-player-dependency-merge-assessment-2026-08-20.md`：`C83` 行的验证陈述改为与实测相符。

### 4.4 明确不修（记录为已知限制）

- **`episodeId` 由带时效签名的 `episodeUrl` 做 SHA-256 ⇒ 签名刷新即漂移**：不改派生方式。剥 query 会让「同路径不同 query 区分不同集」的站点塌缩，风险更大；`apiSafe()` 已同时输出 `flag`/`episodeName` 供对端兜底；改派生会破坏已发布到 `beta` 的 PC 侧契约（跨仓协同），超出本任务范围。
- **`total` = 本次返回条数**：以文档写明语义解决，不新增 `COUNT(*)` 查询（多一次 DB 往返且当前无分页消费方）。
- **`episodeId` 同名不同义**（`PlaybackRecord` 是集地址哈希，`app/src/main/java/com/fongmi/android/tv/web/WebHomeVodContract.java:185` 的 `String episodeId = playRef;` 是会话令牌）：只记文档，不重命名（重命名破坏契约）。
- **`C81` 字样保留在历史 tag 与提交标题**（`recovery/C81-upstream-sync-silent1566/...`、`merge(C81)`/`docs(C81)`）：按 AGENTS.md §6 不回写已推送历史，只在文档补映射说明。
- **`PlaybackProgressApi.java:57` / `PlaybackRecordApi.java:37` 仍回显 `e.getMessage()`**：属既有口径未统一，不在本任务改动面。
- **`OneKeySyncDialog` 的 `prepareStart`/`prepareProgress` 非 volatile**：同类竞态但属既有代码，未扩大改动面。

---

## 5. 验证

| # | 项 | 命令 | 结果 |
| --- | --- | --- | --- |
| 1 | 双 flavor 编译 | `bash ./gradlew --console=plain :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| 2 | 定向单测 | `bash ./gradlew --console=plain :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.db.dao.HistoryDaoTest' --tests 'com.fongmi.android.tv.playback.*' --tests 'com.fongmi.android.tv.server.process.PlaybackHistoryApiTest' --tests 'com.fongmi.android.tv.player.exo.PreCachePolicyTest' --tests 'com.fongmi.android.tv.player.PlayerManagerSwitchCauseSourceTest' --tests 'com.fongmi.android.tv.ui.dialog.*' --tests 'com.fongmi.android.tv.ui.style.*' --tests 'com.fongmi.android.tv.theme.UiTokenGatePortabilityContractTest'` | BUILD SUCCESSFUL；聚合 208 例 0 失败 0 错误 |
| 3 | UI token 门禁 | `bash scripts/check_ui_tokens.sh` | `UI_TOKEN_STATUS PASS`（`violations=0 legacy=0`、`hex_layouts=0 hex_drawables=0 hex_colors=0`、`UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28`） |
| 4 | 冲突标记 | `git grep -E '^(<<<<<<<\|>>>>>>>\|=======)$'` | 0 |
| 5 | whitespace | `git diff --check` | 通过 |
| 6 | 零复活 / 零丢失 | §2 的 A–E 判据 | A=1（本任务新文档）、B=0、C=0、D 自检有效、E=4（全为本地未推送内容） |

### 5.1 变异测试（证明新增断言可失败，非恒真）

| 变异 | 操作 | 预期 | 结果 |
| --- | --- | --- | --- |
| M-a | `HistoryDao.findBySiteKey` 的 `substr(...)` 改为 `LIKE :keyPrefix \|\| '%'` | `HistoryDaoTest` 转红 | 如期 FAILED，已还原 |
| M-b | `PlaybackHistoryDiagnosticsWiringTest.catchBlock` 把第一个 `catch` 块体整体加 4 空格缩进 | 守卫保持绿（旧实现会失配） | BUILD SUCCESSFUL ⇒ 配平对缩进免疫 |
| M-c | 删掉 `PlaybackApi` 的第一个 `catch (Throwable e)` | `index=1` 的用例转红 | 如期 FAILED，已还原 |
| M-d | `PreCache.isHardAutomaticPause` 去掉 `"foreground-eof-recovery"` | `PreCachePolicyTest` 转红 | 如期 FAILED，已还原 |

### 5.2 未执行（如实声明边界）

- **未做设备端实机验证**：本轮修复不改成功路径的响应形态（第 1 项只在开关关闭时生效、第 2 项只换文案、第 9/10 项是 UI 着色与请求自洽），故沿用 `docs/PC-REALTIME-SYNC-01-android-contract-gap.md` §10.2 的 `192.168.50.3:5561` 实测证据，未重装 APK。
- **新增 `403` 门控与 `405` 优先级无实测证据**：已在 §4.3 的文档修正中标注为已知缺口。
- **`PC` 端联调未覆盖**：`episodeId` 匹配与回推的端到端效果属对侧验收。
- **方向选择器的接线仍无回归锁**：仓库现有唯一涉及该弹窗的测试只断言圆角 drawable，见 `docs/C85-upstream-sync-silent1566.md` §6.5。

---

## 6. 回滚

- 合并提交为普通双父提交，回滚方式：`git revert -m 1 <merge-commit>`（保留 `dev3` 一侧），或直接 `git reset --hard c88f2896ac`（本任务改动全部集中在一次提交内）。
- 本轮修复项均可独立回滚：4.1 的第 1/2 项在 `PlaybackHistoryApi` 内、第 3–5/8 项为测试、第 6/7 项为 javadoc/白名单、4.2 的两项在 `OneKeySyncDialog` 内。
- recovery tag 指向已提交且已验证的状态。

---

## 7. 交付坐标

- **合并提交**：`13a093ad3745b85f6cece8e501d45cb76762b917`（`--no-ff` 双父：`c88f2896ac00d4ea663d7a56b4c16dfa4274a1cf` + `0bb2601e3871f58b727873837112abe86784d61f`）。
- **recovery tag**：`recovery/C84-beta-merge-review-dev3-20261019/20261019184918-13a093ad3745`。
- **推送**：`dev3` 已推送（`0e81bae1c7..13a093ad37`）。
- **PR**：<https://github.com/Silent1566/webhtv/pull/463>（`base=beta`、`head=dev3`、`state=OPEN`、`mergedAt=null`；按用户要求**只创建不合并**）。

---

## 8. 第二轮：PR #463 冲突解决（守卫 `C84-conflict`，2026-10-20）

PR #463 创建后 `beta` 又前进到 `e0cb0ea2d5`（`Merge pull request #462 from Silent1566/dev4`，7 个提交，
主体是 TV 焦点环第二轮：主按钮聚焦态改 container 配对 `49b28bd91f`、版本更新弹窗补环 `f5d08659fc`），
GitHub 报告 `mergeable=CONFLICTING` / `mergeStateStatus=DIRTY`。按用户要求「合并冲突请解决」重新执行
`git merge --no-ff --no-commit origin/beta`，出现 **2 处冲突**。

### 8.1 冲突一：`app/src/main/res/layout/dialog_one_key_sync.xml`

- **beta 侧**（`0bb2601e38..e0cb0ea2d5`）对本文件只改 3 行：给 `@+id/start` 追加
  `app:strokeWidth="@dimen/webhtv_focus_ring_width"` + `app:strokeColor="@color/focus_ring_primary_container"`。
- **dev3 侧**（`C85` 上游同步）把 `actionBar` 由 `horizontal` 重排为 `vertical`，新增
  `MaterialButtonToggleGroup @+id/directionGroup`，并把 `@+id/start` 移入新的内层 `LinearLayoutCompat`。
- **解决**：结构取 dev3（方向选择器是本次功能主体），把 beta 的两个焦点环属性搬到内层容器里的
  `@+id/start` 上。二者不冲突——一个是结构重排，一个是给主按钮补描边环。
- **这不是可选美化而是硬约束**：beta 新增的 `TvFocusRingContractTest#everyPrimaryButtonCarriesItsOwnRingAndStatefulText`
  会枚举三个 `layout/` 目录下**所有**消费 `@color/dialog_primary_button_bg` 的按钮，要求同时具备
  `app:strokeColor="@color/focus_ring_primary_container"`、`app:strokeWidth="@dimen/webhtv_focus_ring_width"`、
  `android:textColor="@color/dialog_primary_button_text"`，且 `sites >= 40`。漏搬即转红。
- 本地 token 均存在：`values/webhtv_dimens.xml` 的 `webhtv_focus_ring_width` = `1.5dp`、
  `res/color/focus_ring_primary_container.xml`。
- 合并后 id 唯一性核对：`start`/`directionGroup`/`directionPush`/`directionPull`/`mode`/`localCard`/
  `remoteCard`/`selectAll` 各 1 处；XML 可解析。

### 8.2 冲突二：`docs/upstream-player-dependency-merge-assessment-2026-08-20.md`（ID 二次撞号）

beta 这次发布的是 **dev4 的 `C83`**（TV 焦点环任务），而 dev3 上一轮已把自己的上游同步任务让位改号为 `C83`
——于是同一编号再次相撞。

- **解决**：沿用「未推送让位已发布」先例，beta 的 `C83` 行保留，dev3 的上游同步任务**二次让位改号 `C85`**
  （`C85` 全仓零引用，确认可用）。索引行、任务文档 `git mv docs/C83-upstream-sync-silent1566.md →
  docs/C85-upstream-sync-silent1566.md`、该文档内标题/守卫 id/改动清单/§9 下一步，以及本文档内对它的
  引用（路径、`C83` 的主题 token 化、`dev3` 的 `C81`/`C83` 成果）全部同步。
- **标识符沿革（如实记录）**：该上游同步任务经历 `C81`（提交与 recovery tag 里的原名）→ `C83` → `C85`。
  已发布的历史 tag 与 commit subject 按 AGENTS.md §6 不回写，映射关系记录在
  `docs/C85-upstream-sync-silent1566.md` 的 §Recovery anchor。

### 8.3 本轮发现并修复的既有缺陷（beta 侧，非合并引入）

`TvFocusRingContractTest#focusedVersionCardKeepsItsTextReadable` 在 Windows 检出上**恒红**：

- 断言用 `Pattern.compile("                <androidx\.appcompat\.widget\.LinearLayoutCompat
(.*?)>")`
  匹配「16 空格缩进 + 换行」，但 `core.autocrlf=true` 让检出的 XML 是 CRLF，`
` 不匹配 `
` ⇒ 计数恒为 0。
- **证明是 beta 自身缺陷而不是合并引入**：`git hash-object` 与 `git rev-parse e0cb0ea2d5:<path>` 逐字节相同
  ——`TvFocusRingContractTest.java` `1a581101f3`、`leanback/dialog_update.xml` `5e3b24f885`、
  `mobile/dialog_update.xml` `1d42ba6bbb` 三个 blob 与 beta tip 完全一致，且 dev3 相对 merge-base 未改这三个文件。
- **修复**：正则改为 `
?
`，并注明显式理由（硬写 `
` 会在 Windows 恒红、在 LF 的 Linux CI 恒绿）。

### 8.4 验证（第二轮）

| # | 项 | 结果 |
| --- | --- | --- |
| 1 | 双 flavor arm64 Debug Java 编译 | BUILD SUCCESSFUL |
| 2 | 定向单测（含 `TvFocusRingContractTest` 27、`Theme*` 全家、`HistoryDaoTest`、`playback.*`、`PreCachePolicyTest`、`ui.dialog.*`、`ui.style.*`、`UiTokenGatePortabilityContractTest`） | **396 例 0 失败 0 错误** |
| 3 | `bash scripts/check_ui_tokens.sh` | PASS（`violations=0 legacy=0`、`hex_layouts=0 hex_drawables=0 hex_colors=0`、38 对 0 失败 min=4.28） |
| 4 | 变异探针：移除 `dialog_one_key_sync.xml` 中 `start` 的两个环属性 | `everyPrimaryButtonCarriesItsOwnRingAndStatefulText` **如期 FAILED**；还原后 `git hash-object` = `8ffe6b4036`，复跑 BUILD SUCCESSFUL ⇒ 该契约确实守护了本次冲突解决结果 |
| 5 | 冲突标记与空白 | `git grep -E '^(<<<<<<<|>>>>>>>|\|\|\|\|\|\|\|)'` 为空、`git diff --check` 通过 |
| 6 | 实机（`emulator-5558`，即 adb 控制台端口 **5559**，V1923A / Android 9 / SDK 28） | APK 安装 `Success`；`com.silent.android.webhtv` 启动进首页并正常渲染片单；设置 → 增强功能 → 一键同步弹窗可打开（空态「未发现设备」）；`logcat` 无 `FATAL EXCEPTION` |

**实机验证的边界（如实声明）**：`actionBar`（含 `directionGroup` 与两张方向卡）只在发现对端设备后
`setSelected()` 才 `setVisibility(VISIBLE)`，本机无第二台同网段 WebHTV 设备，故方向选择器**未在实机上目视**。
其正确性由 8.4 第 4 项的变异探针 + 第 2 项的 27 条焦点环契约 + XML 结构核对共同保证。

### 8.5 第二轮提交坐标

- 见本文件所在提交（守卫 `C84-conflict`，`--no-ff` 双父：`a004e4a18e` + `e0cb0ea2d5`）。
- PR #463 随 `dev3` 推送自动更新。

---

## 9. 第三轮：beta 再次前进导致的冲突（守卫 `C84-conflict2`，2026-10-20）

第二轮提交 `4e7b24b5f7` 推送后，`beta` 又前进到 `f289207bce`（`Merge pull request #464 from Silent1566/dev2`，
5 个提交，主体是 dev2 的「切换线路不自动播放」开关 `ad7f5834de` 与它自己的 `C84` 复评 `70a3fe757f`）。
再次 `git merge --no-ff --no-commit origin/beta`，**2 处冲突**。

### 9.1 冲突一：`TvFocusRingContractTest.java`（同一缺陷被两侧各自修复）

- **dev2 独立修了与本任务第二轮完全相同的 CRLF 缺陷**（`70a3fe757f`）：把
  `Pattern.compile("...LinearLayoutCompat
(.*?)>")` 改为 `?
`。
- `git diff :2: :3:` 显示两侧**语义完全相同**，只差注释措辞（我方写「Windows 恒红 / LF 恒绿」，
  dev2 写「headerCount 恒为 0，以找不到容器而非容器缺状态失败」）。
- **解决**：取 beta 已发布版本（`git checkout --theirs`），不重复造轮子；我方注释措辞放弃。
  理由：beta 侧已发布，按「未推送让位已发布」同源原则处理，且两者正则逐字符相同、行为无差异。

### 9.2 冲突二：评估索引 `C84` 第三次撞号

beta 这次发布的是 **dev2 的 `C84`**，而本任务上一轮已占用 `C84`。按同一让位原则：

- beta 的 dev2 `C84` 行保留在前；
- 本任务（dev3）**三次让位改号 `C86`**（`C86` 全仓零引用）；
- 索引行、`git mv docs/C84-beta-merge-review-dev3-20261019.md → docs/C86-...`、
  本文档标题与守卫说明、`docs/C85-upstream-sync-silent1566.md` 内两处指向本任务的引用全部同步。

**标识符沿革汇总**（四个任务、三个编号段）：

| 任务 | 沿革 | 已发布标识符是否回写 |
| --- | --- | --- |
| 上游同步（dev3） | `C81` → `C83` → `C85` | 否（`merge(C81)`/`docs(C81)` 与 `recovery/C81-*` tag 保留） |
| 本任务（dev3 beta 复评） | `C84` → `C86` | 否（`merge+review(C84)` 提交与 `recovery/C84-*` tag 保留） |
| dev4 TV 焦点环 | `C83` | 是 beta 已发布 |
| dev2 beta 复评 | `C84` | 是 beta 已发布 |

### 9.3 验证（第三轮）

| # | 项 | 结果 |
| --- | --- | --- |
| 1 | 双 flavor arm64 Debug Java 编译 | BUILD SUCCESSFUL |
| 2 | 定向单测（含新带入的 `FlagSwitchPlaybackPolicyTest`/`FlagSwitchPlaybackWiringTest`、`TvFocusRingContractTest` 27、`Theme*`、`playback.*`、`PreCachePolicyTest`、`ui.dialog.*`、`ui.style.*`、`HistoryDaoTest`） | **404 例 0 失败 0 错误** |
| 3 | `bash scripts/check_ui_tokens.sh` | PASS（`violations=0 legacy=0`、`hex_layouts=0 hex_drawables=0 hex_colors=0`、38 对 0 失败 min=4.28） |
| 4 | 全量 leanback 单测（4419 例） | 8 失败，**全部为既有基线**：测试文件与所读资产与 beta tip `f289207bce` 逐字节相同（`WebThemeTokenSourceTest`/`eclipse.html`/`eclipse-detail.html`/`NativeEnhancedPlaybackStyleFocusTest`/`SearchResultDownFocusTest` 的 `git hash-object` 与 `git rev-parse f289207bce:<path>` 相等），`TmdbSourceOnlyInteractionTest`/`MpvFontConfigTest` 的 `git diff` 对 beta 为空（仅工作区 CRLF 归一差异）；均不在本任务 staged 面内 |
| 5 | 冲突标记与空白 | `git grep -E '^(<<<<<<<|>>>>>>>)'` 为空、`git diff --cached --check` 通过 |
| 6 | 实机（`emulator-5558` = adb 控制台端口 **5559**，V1923A / Android 9 / SDK 28） | APK `install -r` **Success**；`com.silent.android.webhtv` 启动进首页并渲染片单；点开影片详情页正常（标题/评分/线路/选集齐全）；设置 → 播放设置可见新带入的「**切换线路不自动播放**」行（默认 `关`）；点击切到 `开`、再点回 `关`，磁盘 `shared_prefs/com.silent.android.webhtv_preferences.xml` 中 `player_defer_flag_switch` 已落盘为 `false`；`logcat` 无 `FATAL EXCEPTION` |

**实机验证的边界（如实声明）**：`WebThemeTokenSourceTest` 的 2 条断言用 `":root {
"` 锚定 CSS 块，
在 CRLF 检出下同样恒红（与 §8.3 同源），属 beta 既有缺陷，**未在本次修**（超出冲突解决范围，
且修复点在 beta 自己的测试与资产，建议由 beta 侧统一处理）。方向选择器仍因无第二台同网段设备未目视。

### 9.4 第三轮提交坐标

- 见本文件所在提交（守卫 `C84-conflict2`，`--no-ff` 双父：`4e7b24b5f7` + `f289207bce`）。
- PR #463 随 `dev3` 推送自动更新。
