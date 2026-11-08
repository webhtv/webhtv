# C82-beta-merge-review-dev2-20261019

## Recovery anchor

- Objective: 在 dev2 上合并 `origin/beta` 最新代码（含远端已回退/删除的提交不得被顺带带回），复评本次合并带入的改动与本地 7 个已提交未推送的 MPV 提交，循环评审-修复-验证直到通过，然后提交、推送并创建到 `beta` 的中文 PR（只创建、不合并）。
- Lane: `standard`。Scope: `app`、`docs`、`scripts`。
- Baseline: branch `dev2`，HEAD `e7188138a92067f48ec4e4e0c988171729409966`。
- Protected dirty paths: `.wh_diag/**`（任务前已存在、未被 Git 跟踪差异，未纳入 scope）。
- Status: 合并完成、复评 2 轮并修正 2 项缺陷、验证全部通过，待提交。
- Next action: 无。

## 1. 合并对象

| 项 | 值 |
| --- | --- |
| 合并前 dev2 HEAD | `e7188138a92067f48ec4e4e0c988171729409966` |
| `origin/beta` tip | `c3540076db`（Merge PR #459 from dev4） |
| 合并基 | `4e3f6bcf3a`（Merge PR #456 from dev2） |
| 并入提交数 | 18（PR #457 / #458 / #459 及其内部提交） |
| 冲突 | **0** |
| 合并提交 | 见 `git log --first-parent`（第二父为 `c3540076db`） |

两侧改动面**文件级零重叠**：beta 侧未触碰 `PlayerManager.java` / `SiteHealthReportDialog.java`，本地提交未触碰 beta 改动的 41 个路径，因此无内容冲突。

## 2. 零复活 / 零丢失证据

用户显式约束：「远端已经移除回退的提交不能再顺带提交上去」。对合并树 `11218493f6` 逐层验证：

| 层 | 判据 | 结果 |
| --- | --- | --- |
| A | 合并树 − beta 树（路径级） | **0** |
| B | beta 树 − 合并树（路径级） | **0** |
| C | （revert 删除）∧（不在 beta 树）∧（在合并树） | **0** |
| D | 检测器自检：向伪造树注入 `webhtv_tokens.xml`（真实被 revert 删除且不在 beta） | 如期报出（**非空转**） |
| E | 内容级净差异 `git diff origin/beta..HEAD` | **恰 5 个路径**，全部为本任务本地未推送提交所改 |

- revert 集合由**主题行锚定**提取（`tolower(subject)` 以 `revert` 开头 或 `回退`/`撤销` 开头），在 `origin/beta`+`dev2`  lineages 上得 **27 个提交 / 44 条删除路径**。
- **一次自证伪记录**：首版脚本用 `git log --all` 取 revert 集合，得 183 个提交（混入 `third_party` 与上游 ExoPlayer 的 `Revert`/`Rollback` 历史），且漏掉「不在 beta 树」这一条件，报出 19 条假阳性。按既有 C78 口径改为「主题行锚定 + 限定项目 lineage + 要求不在 beta 树」后归零。`app/src/main/assets/themes/index.json` 等 16 条 `revert: remove dynamic theme color system` 删除的路径**确实存在于 beta 树**（后被重新加入），属 beta 合法内容而非复活，故不计入 C 层。
- 因 A/B 均为 0，C 层在数学上必然为 0；仍单独计算以保留可证伪形式。

## 3. 复评发现的缺陷（2 项，均已修复并变异验证）

### 3.1 前台 EOF 让位被 `PreCache` 静默改回「继续预载」（blocker）

E-SP10 阶段 2（beta `c1fd4aa13f`）新增 `Reason.FOREGROUND_EOF_RECOVERY` 并放入 `AutoPreloadPolicy.hardPauseReason()`，设计文档明确要求它「与 `NETWORK_UNAVAILABLE` / `POWER_SAVE` 同级，确保 `evaluate()` 立即暂停」。该判据本身正确：`evaluate()` 返回 `Decision(PAUSED_THREADS=0, 0, "paused", "foreground-eof-recovery")`。

但 `PreCache.effectiveAutoDecision()` 有一条「保持磁盘预载连续」的既有改造分支：

```java
if (automaticPreload && !decision.enabled()
        && !isHardAutomaticPause(decision.reason())) {
    return new Decision(NORMAL_THREADS, DEGRADED_DURATION_MS, "degraded",
            decision.reason() + "-continuous");
}
```

`isHardAutomaticPause()` 的硬暂停名单（`session-mismatch`/`memory-pressure`/`network-unavailable`/`network-unvalidated`/`data-saver`/`power-save`/`thermal-pressure`）**未随新增原因一起增补**。于是：

- 自动档（`PlaybackPerformanceSetting.isAuto(EXO, "preload")`，默认 profile 为 `PROFILE_AUTO` 且未覆写 → **默认即为真**）下，暂停结论被改写为 `1 线程 / 10 s / degraded / foreground-eof-recovery-continuous`；
- 预载**并未让位**，仍与前台恢复争抢同一条脆弱连接——正是 `ExoForegroundEofSignal` 类注释记录的实测故障（176/177 次 EOF 落在预载活跃窗口内，唯一 37 s 预载被抑制的区间一次都没有）。

**为什么既有测试全绿**：`AutoPreloadPolicyTest` 的 4 条让位用例直接 `new AutoPreloadPolicy().evaluate(inputs)`，**完全不经过 `PreCache.effectiveAutoDecision()`**。这与 E-SP10 文档 §10.7 自己记录的 `Inputs.capture()` 缺口是同一类「测试绿、生产 inert」。

**修复**：`app/.../player/exo/PreCache.java` 的 `isHardAutomaticPause()` 增补 `"foreground-eof-recovery"`，并补 Javadoc 说明「该名单必须跟着 Reason 一起长」的理由；可见性由 `private` 放宽到包级，使守卫可直接断言。**不改** `AutoPreloadPolicy` 与 `ExoForegroundEofSignal`（beta 策略本身正确）。

**刻意不扩大范围**：`FOREGROUND_RECOVERY`（`foreground-recovery`）同样不在硬名单内，但那是 beta 既有且早于本合并的行为（`2338464abf`），属「缓冲区类原因降级继续跑」的既有设计，本次**只报告不修改**，以免夹带未评审的行为变更。

### 3.2 `HistoryDaoTest` 在 beta 自身即为红（merge 引入的新失败）

`e55f19276f` 给 `HistoryDao` 增加 `findBySiteKey(... LIMIT :limit)`，但**未同步更新** `HistoryDaoTest`。该守卫用**整文件**子串检查 `assertFalse(lower.contains("limit"))` 表达「`find(int cid)` 不得有时间/条数上限」，被另一个方法上**合法**的 `LIMIT` 击穿。

- 两个文件在合并树与 `origin/beta` 上**逐字节相同**（`git rev-parse HEAD:path` == `origin/beta:path`），故失败源自 beta 而非本合并；但本合并必须交付绿树，且该断言属**假失败**（与测试名声明的不变式不一致）。
- 全量 mobile 由基线 6 失败升到 7 失败，第 7 项即它。

**修复**：把断言**按方法取查询文本**后再判：`queryOf(source, "public abstract List<History> find(int cid);")` 不得含 `limit`；并增反向对照，断言 `findBySiteKey` 的查询**确实**含 `limit`，证明按方法取查询生效（否则 scoping 写错会静默全绿）。不改 `HistoryDao.java`（`LIMIT` 是其正当需求，`PlaybackApi.effectiveHistoryLimit()` 已做 1..100 夹取）。

## 4. 验证

执行环境：2026-10-19 Asia/Shanghai，Windows，`gradlew.bat --no-daemon --no-build-cache`（构建缓存被另一 workspace `F:/Workspace/webhtv` 长期持有，改用 `--no-build-cache` 规避锁竞争；非代码问题）。

1. **编译（双 flavor，含本任务修复）**：`:app:compileMobileArm64_v8aDebugJavaWithJavac` + `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` → `BUILD SUCCESSFUL`（无 Java 错误）。
2. **定向（exo + playback + server）**：127 个测试类、**856 例 0 失败 0 错误 1 跳过**。
3. **全量 mobile**：`5193 tests / 6 failed / 3 skipped`，失败集与既有基线**逐条一致**（`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`、`ReaderPlaybackRoutingSourceTest`×2、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`）→ **零新增失败**（修复前为 7 失败，第 7 项即 3.2）。
4. **全量 leanback**：`4401 tests / 8 failed / 3 skipped`，失败集与既有基线**逐条一致**（同上 6 项 + `NativeEnhancedPlaybackStyleFocusTest`、`SearchResultDownFocusTest`）。
5. **静态门槛**：`python scripts/check_ui_tokens.py` → `UI_TOKEN_SCOPE stage=A violations=0 legacy=0`、`UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28`、`UI_TOKEN_STATUS PASS`。
6. `git diff --check` 通过；全仓无冲突标记。
7. 合并带入 3198 行新代码在双 flavor 下编译通过、其自带 12 个测试文件（含 `PlaybackHistoryApiTest`、`ExoForegroundEofSignalTest`、`HttpEofRecoveryCancellationTest` 等）全部通过。

### 4.1 变异测试（证明新增断言可失败，非恒真）

| # | 变异 | 期望 | 实测 |
| --- | --- | --- | --- |
| A | `PreCache.isHardAutomaticPause()` 去掉 `"foreground-eof-recovery"`（还原缺陷） | 转红 | `hardAutomaticPauseMatchesTheResourceReasonSet` + `foregroundEofRecoveryIsNotDowngradedBackToContinuousPreload` **双双 FAILED** |
| B | `HistoryDao.find(int cid)` 的 `@Query` 追加 `LIMIT 5` | 转红 | `findReturnsHistoryWithoutTimeOrCountLimits` **FAILED** |

两次变异均从备份还原，`git diff` / `git rev-parse` 确认与还原前逐字节相同（`PreCache.java` 仅保留本任务修复，`HistoryDao.java` 与 `origin/beta` blob `c72e227394` 一致）。

## 5. 影响与风险

- 生产代码只改 1 处（`PreCache.java` 一个字符串 + 注释 + 可见性），纯 Java，无格式、ABI、API、依赖锁、补丁、native 资产或包体积影响。
- 行为变化**仅限自动档下的 `foreground-eof-recovery` 原因**：由「1 线程/10 s 继续预载」变为「真正停止预载」，这正是 E-SP10 阶段 2 的设计意图。缓冲区/吞吐类原因的「降级继续跑」保持不变。
- 残留风险：E-SP10 的设备端效果（退避是否降低 EOF 频率、让位是否降低掉帧）仍**无设备证据**，`15 s` 让位窗口亦未实测校准（beta 文档 §10.5 的诚实边界，本任务未扩大验证范围）。
- 既有 6/8 项全量失败为 Windows CRLF 跨行字面量等既有缺陷，与本任务净差异无交集，按范围记录不修。

## 6. 回滚

- 合并本身：`git revert -m 1 <合并提交>`。
- 3.1 修复：`git revert <本任务提交>`（单文件，无持久化 schema）。
- 3.2 修复：同为测试守卫，随本任务提交一并回滚。
