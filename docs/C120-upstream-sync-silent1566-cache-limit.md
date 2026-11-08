# C120 — 合并上游 `webhtv/webhtv` 分支 `Silent1566` 最新改动（播放缓存上限接入容量计算 + 空闲收敛）

## Recovery anchor

- **目标**：把上游 `webhtv/webhtv:Silent1566@47454687f504955775b398905ba09d73aeb9f20c` 相对本地 `dev2` 的 4 个新提交（播放缓存总量约束、仅播放结束生效、MPV HLS 空闲收敛、上限接入 EXO 容量计算并修正评审缺陷）合并进 `dev2`，上游功能 100% 吸收，本地焦点环语义描边、主题 token、缓存面板既有行为零丢失，并通过双 flavor 编译与定向单测。
- **验收标准**
  1. 上游 tip `47454687f5` 成为 `dev2` 合并提交的第二父，合并提交为 `--no-ff` 双父。
  2. 唯一冲突 `CacheManagementDialog.java` 两侧改动全保留：上游 `applyPlaybackLimit()` 与本地 `applyFocusRing()` 并存，本地语义描边通道不回退到已删除的 `selector_cache_button_focus`。
  3. 新增 `PlaybackCacheBudget` 及其单测随合并进入，`ceilingBytes` 语义为「只降低、不限则沿用预加载设置」。
  4. 双 flavor arm64 Debug Java 编译通过。
  5. 定向单测 `com.fongmi.android.tv.cache.*` 零失败。
  6. 任务文档与评估索引行随合并提交原子提交，并生成 annotated recovery tag。
- **Lane**：`upstream`
- **Scope**：13 个路径（见 §1）
- **任务守卫**：`C120`，base HEAD `85afd14593d8d0900e98836f4b1cf24f8b4e9b1d`，branch `dev2`，受保护脏路径 0
- **当前状态**：合并与验证已完成，待提交与 tag
- **下一动作**：`task_guard.sh finish` 提交并打 recovery tag

---

## 1. 上游 commit ledger

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始时 HEAD | `85afd14593d8d0900e98836f4b1cf24f8b4e9b1d`（先由 `e1ba1e18e1` 快进 `origin/dev2`） |
| 上游仓库/分支 | `https://github.com/webhtv/webhtv` / `Silent1566` |
| 上游 tip | `47454687f504955775b398905ba09d73aeb9f20c`（2026-11-06 23:56:12 +0800） |
| Merge base | `15e8f6af48c97fd3078b472b5d9735e91fab5d74`（即 C93 已合并的上游 tip） |
| **本次实际增量** | `git rev-list --count dev2..47454687f5` = **4** |
| 等价性核验 | `git cherry dev2 47454687f5` 四行全部为 `+`；`PlaybackCacheBudget.java` 在 `dev2` 不存在 ⇒ 确属新代码 |

| 完整 commit ID | 日期 | 标题 | 处置 |
| --- | --- | --- | --- |
| `ff960e107242dd7cd0bccdc34c083e29b9a5e1e3` | 2026-11-06 19:15 | `fix(cache): bound playback cache by the total limit and fix misleading panel labels` | **合并** |
| `2303a8b9bcd8878dd077302cd85c264d3a0503a1` | 2026-11-06 20:10 | `fix(cache): apply the total limit only after playback ends` | **合并** |
| `900e58341c32e208fab831b1446143f884618cc1` | 2026-11-06 20:23 | `feat(cache): converge the MPV HLS cache to the total limit after playback` | **合并** |
| `47454687f504955775b398905ba09d73aeb9f20c` | 2026-11-06 23:56 | `fix(cache): 播放缓存上限接入容量计算并修正评审缺陷` | **合并** |

**范围外提交**：无。

## 2. 上游净变更构成

`git diff --stat 15e8f6af48 47454687f5` = **12 files changed, +396 −33**，全部为 `M`/`A`，无依赖、无 native/二进制、无 ABI 变更。

- 新增 `PlaybackCacheBudget`（容量预算纯函数）+ `PlaybackCacheBudgetTest`（11 例）。
- `MediaSourceFactory`：引入 `CapacityPhase{PLAYBACK,IDLE}`，容量改为 `PlaybackCacheBudget.capacityBytes` 计算；新增 `targetCapacityBytes()`（面板展示）与 `enforceCapacityNow()`（不等待下次播放结束）。
- `MpvHlsProxy.release()`：会话真正结束时在独立线程 `pruneToIdleBudget()`，并对线程内未捕获异常兜底。
- `CacheScheduler`：启动时预热快照、12 小时兜底调用 `enforceCapacityNow()`、总上限清理前先判断「自动可清理模块是否能压到限值以下」。
- `CachePolicyStore`：`EXO` 加入可持久化上限集合（默认 0 = 不限，老用户行为不变）。
- `CacheManagementDialog`：播放缓存行出现上限入口、摘要行新增「播放缓存 X（退出播放后收至 Y）」、选择上限改后台应用。
- 三语 `strings`：`cache_total_limit_summary` 文案由「Total:」改正为「Total limit:」，新增 `cache_limit_title_exo`、`cache_management_playback_budget`。

## 3. 设计研究（最佳实践门）

| 证据类 | 来源与版本 | 等级 | 支持的结论 | 本仓适用性 | 决策影响 |
| --- | --- | --- | --- | --- | --- |
| 上游源码与提交 | `webhtv/webhtv:Silent1566@47454687f5` 的 4 个提交（2026-11-07 读取） | observed | 全部实现与注释理由；`PlaybackCacheBudget` 三类容量语义（播放态只受预加载/面板上限约束、空闲态受总上限−其他模块约束、`MIN_BUDGET_BYTES` 保底） | 直接适用（同一产品谱系） | 采用 |
| 上游设计文档 | `docs/CACHE-MGMT-01` §7/§9.1/§20.6（同 commit 内更新） | observed | 面板上限作为「预加载设置之上的额外天花板」，默认不限故不改老用户行为；播放态刻意不受总上限约束，避免削掉正在读的缓冲余量 | 直接适用 | 采用；偏离已在文档记录 |
| 平台规范（一手） | `androidx.media3.datasource.cache.SimpleCache` 重建时按 `LeastRecentlyUsedCacheEvictor` 加载索引并淘汰超限 span（Google 官方 API 文档，2026-11-07 读取） | observed | 只有「以更小容量重建」才能在空闲时真正缩小缓存，事后清理无法约束容量型缓存 | 直接适用 | 采用重建方案 |
| 本地现有实现 | `MediaSourceFactory`（E-SP10/C79 的 EOF 退避与预加载让位）、`DiskCacheCapacityPolicy`、`CacheCapacityState.canReleasePending()`、`MpvHlsCacheCoordinator.prune/ClientLease` | observed | 本地容量计算原本只由预加载设置决定，`EXO` 面板上限无消费者；会话持有期间 `canReleasePending()` 为假，`enforceCapacityNow()` 天然不会在播放中削减容量 | 直接适用 | 无需额外守卫 |
| 第三方项目/论文 | 无适用对象（本产品自身的缓存预算取舍） | — | — | 不适用 | 记录为不适用 |

### 方案对比与决定

| 方案 | 正确性 | 兼容性/性能 | 结论 |
| --- | --- | --- | --- |
| **不合并** | 面板播放缓存上限继续无消费者，设备上最大单缓存不受总上限约束 | 无风险 | 否 |
| **原样采用上游** | 语义正确 | 无风险 | **是**（本轮无本地改写需要） |
| **本地改写（如播放态也受总上限约束）** | 会在播放中削减缓冲余量，代价是 seek 范围与重缓冲 | — | 否 |

**决定**：原样采用上游 4 个提交，本地只解决冲突，不改写上游语义。

**回滚路径**：`git revert -m 1 <合并提交>`，或重置回 `backup/dev2-before-cache-limit-20261107003000`（= `e1ba1e18e1` 快进后的 `85afd14593`）。无 lock/native/数据结构变更，回滚无副作用。

## 4. 冲突与解决

`git merge --no-ff 47454687f5` 报 **1 处冲突**，其余 8 路径自动合并：

| 冲突路径 | 原因 | 解决 |
| --- | --- | --- |
| `app/src/main/java/com/fongmi/android/tv/ui/dialog/CacheManagementDialog.java` | 双方在同一位置插入：上游在 `chooseLimit()` 后新增 `applyPlaybackLimit()`，本地在 `chooseLimit()` 后为 `applyFocusRing()` 加了长段实测因果注释 | **两侧全保留**：先放上游 `applyPlaybackLimit()`，再接本地 `applyFocusRing()` 注释块与方法；同时清理合并产生的重复 `/**` 起始行。本地语义描边调用点保持 `applyFocusRing(button, R.color.focus_ring_*)`，不复活上游已删除的 `selector_cache_button_focus` |

`git grep` 扫描：无残留冲突标记；`CacheManagementDialog.java` 内 `setForeground(...selector_cache_button_focus)` 与 `selector_cache_button_focus.xml` 引用均为 0（该 drawable 在本地已不存在，复活即编译失败）。花括号 104/104 平衡，文件无 BOM。

## 5. 验证

| 项 | 命令/方式 | 结果 |
| --- | --- | --- |
| 移动端编译+单测 | `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.cache.*"` | `BUILD SUCCESSFUL in 23m 4s`；测试报告 **88 tests / 0 failures / 0 errors / 100%**，16 个 suite 含 `PlaybackCacheBudgetTest` |
| TV 端编译 | `.\gradlew.bat :app:compileLeanbackArm64_v8aDebugJavaWithJavac` | `BUILD SUCCESSFUL in 2m 2s` |
| 冲突标记扫描 | `git grep -n -E "^(<<<<<<<|>>>>>>>) "` | 空 |
| 三语占位符 | 检查三段 `strings.xml` 的 `cache_management_playback_budget` | 三语均为 `%1$s`+`%2$s`，占位符一致 |
| 会话门禁语义核对 | 读 `CacheCapacityState.canReleasePending()` / `MpvHlsProxy.release()` / `ClientLease.close()` | `enforceCapacityNow()` 与 `pruneToIdleBudget()` 都只在 `activeSessions==0` / lease 已关闭后生效，播放中不削减容量 |

**未覆盖（如实记录）**：未做设备端实测（面板显示「播放缓存 X（退出播放后收至 Y）」、退出播放后 EXO 目录实际缩小、选择上限后即时收敛）。本机当前无连接设备，属有界验证范围外。

## 6. 交付坐标

- 合并提交与 recovery tag：由 `task_guard.sh finish` 生成后回填
- 回滚锚点：`backup/dev2-before-cache-limit-20261107003000`
- 未推送：本轮只落本地 `dev2`
