# C33 dev4 合并 beta 评审（2026-10-03）

## Recovery anchor

**目标**：将远端 `origin/beta` 最新代码合并进 `dev4`，评审全部已修改代码（含已提交未推送的 C9 墓碑同步提交 `699635fca77`），问题修复并验证通过后提交、推送、创建 PR 到 beta（只创建不合并）。
**验收标准**：合并干净完成且远端已回退的提交不复活；C9 提交代码评审通过；JVM/设备验证通过；提交+tag+推送+PR 完成。

**状态**：合并、评审与验证完成；本提交即收尾提交（含 merge + 本文档），后续动作：推送 dev4、创建 PR。

## 合并范围

- dev4 合并前 HEAD：`699635fca77`（fix(following): propagate unfollow across one-key sync via tombstones，未推送）
- beta 增量：`8e4dac7606a..59d481e7264`（PR#394/#395/#396/#397：影视原生增强播放页线路高亮恢复 c92ada1015e、inline 主题编辑器 f912cd41ee1、直放模式主题适配 6288fd4bd1a、影视原生播放页简介按钮 dfd4e5b3f38，及 dev3 统一主题系统大批量提交）
- merge：无冲突（beta 增量对 FollowingActivity 仅做主题/壁纸适配，与 following 数据层无语义冲突；合并后该文件含 beta 的 `WebHtvAlertDialogBuilder` 弹窗改造，行为不变）

## 回退不复活验证（用户核心关注点）

beta 上 `fd29d76ee77`（revert: 撤销把电视版暗色配色搬到手机版的两个提交）已撤销 `a6a470c2fa6`、`db793d5af6b`。合并后对 revert 涉及的全部 21 个文件执行
`git diff origin/beta HEAD -- <21 files>` → **空**（与 beta 逐字节一致），证明被回退的内容没有通过 dev4 侧复活、也不会随本 PR 重新提交上去。

## C9 墓碑同步提交评审结论（通过）

- `Following.deletedAt` 字段 + 索引；`FollowingDatabase` v3 迁移：RECREATE SQL 与 Room 导出 schema `3.json` 的 createSql 逐字段程序化比对**完全一致**，6 个索引齐备；v2 `enabled=0` 行转墓碑语义正确。
- 活行/墓碑隔离：`list/find/findDue/unreadCount/findUnmatchedBySource` 过滤墓碑；`findAny` 供同步/复活判断。
- 合并策略墓碑分支：双墓碑 LWW、重追复活（`createdAt>deletedAt`）、旧快照不复活（保守）逻辑自洽；`mergeRevived` 复活保留进度水线并重置 createdAt。
- 防复活三处：TMDB 身份迁移遇墓碑返回、订阅导入 `isTombstoned` 跳过、备份捕获/合并用 `findAll`（本地墓碑不被旧快照 union 复活）。
- UI 取消链 `onDelete → deleteAsync → markDeleted` 完整；重追链 `build()/onFollowNextSeason` 均设 `createdAt=now`，复活意图必然胜出。
- 评审疑点逐项排查：`AlistSubscriptionImporter.findExisting` 可命中墓碑行但 `updateExisting` 仅更新元数据不复活（`refreshDerived` 不触碰 deletedAt/enabled）。

## 验证记录（2026-10-03）

| 验证项 | 结果 |
| --- | --- |
| JVM Following 套件 leanback variant | **34/34 通过**（含 5 个墓碑合并用例） |
| JVM Following 套件 mobile variant | **34/34 通过** |
| 双 flavor 编译（leanback/mobile arm64 debug） | BUILD SUCCESSFUL |
| 设备 androidTest following 包（emulator-5561 覆盖安装，APK SHA-256 与本地一致 d64287a1…6690） | **17 用例 16 通过**，唯一失败 FollowingActivityDeviceTest（见下） |

### 既有失败分类（与本任务无关，证据充分）

1. `FollowingActivityDeviceTest.activityRendersStoredFollowingAndFiltersUpdates`：在**纯 origin/beta 参照 worktree**（独立构建安装）上同样失败 → 历史遗留（C9 提交时已用 stash 实证记录在案）。
2. `FollowingUiSourceTest` 的 2 个用例（`mobileFollowingHeaderWraps…`、`detailAndPlaybackScreensWire…`）：在纯 beta 参照、tombstone 提交基线（`699635fca77` worktree）、合并后 HEAD 三方均同样失败。探针实证根因：**本机 `core.autocrlf=true` 使工作区 XML 为 CRLF，测试断言用 `\n` 精确匹配多行文本必然失败**——环境性脆弱断言（beta 侧 `c900dac20e0` 引入），非本任务改动引入。按 AGENTS.md 范围规则报告不修。

## 过程修复（治理维护独立提交）

- `e2304fc1d64`：`task_guard.sh check_staged_whitespace` 分批执行——大合并 563 文件约 42KB 参数超 Windows CreateProcess 32KB argv 上限报 `Argument list too long`，使 finish 误判空白校验失败。每批 100 路径分批校验，语义不变（recovery tag：`recovery/MAINTENANCE-TASK-GUARD-ARGV-20261003/*`）。

## 提交与推送

- 本任务产物：merge commit（含本评审文档）+ recovery tag。
- PR：dev4 → beta，中文描述，只创建不合并。

## Next action

推送 origin dev4 → `gh pr create` 到 beta（只创建不合并）。若后续反馈 `FollowingUiSourceTest` 的 CRLF 断言问题，建议在 beta 侧把多行断言改为容忍行尾差异的写法。
