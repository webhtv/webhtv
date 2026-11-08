# C9 追更墓碑同步（取消追更跨设备传播）

## Recovery anchor

**目标**：修复用户反馈"手机取消追更后一键同步无法传播到 TV，需 TV 手动再删一次"。
**验收标准**：手机端取消追更（写墓碑）→ 一键同步 → TV 端该项从追更列表消失且不复活；重追后可正常恢复；混合版本（旧版对端不含墓碑）同步不复活已删项；订阅导入/TMDB 身份迁移不复活墓碑。

**状态**：已实施并验证完成。

## 问题根因

原实现"取消追更"是物理删除（`FollowingStore.delete` → DAO DELETE）。一键同步导出的备份快照只含存活行，TV 端 `FollowingMergePolicy.mergeFollowing` 是纯 union 合并（远端列表没有的键原样保留），删除意图无法表达，TV 上的行永远残留。

## 方案（用户选定：墓碑表）

- `Following` 新增 `deleted_at`（long，0=活跃，>0=墓碑时间戳）+ 索引；`FollowingDatabase` v2→v3 迁移（全量重建表 + v2 时代 `enabled=0` 行转墓碑）。
- 取消追更改为写墓碑：`FollowingDao.markDeleted`（保留行与来源绑定）。
- **合并语义（`FollowingMergePolicy.mergeOne` 墓碑分支）**：
  - 双方墓碑：较新 deletedAt 胜。
  - 一方墓碑 vs 一方活跃：`createdAt > deletedAt` 表示"取消后重新追更"，新意图复活胜出；否则墓碑胜（删除保守）。
  - 旧版快照（createdAt=0 缺失）不复活墓碑。
- **活行/墓碑隔离**：
  - `FollowingStore.list()` → `findActive()`（UI/导入只见活行）。
  - `FollowingStore.find()` → `findActive(identityKey)`（UI 判断走重追路径）。
  - 新增 `FollowingStore.findAny()`（含墓碑，仅供同步/复活判断）。
  - `findDue`/`unreadCount`/`findUnmatchedBySource` 均过滤墓碑。
- **复活**：`FollowingStore.saveNew` 发现墓碑行时经 `mergeRevived` 复活（deletedAt=0、enabled=true、createdAt=now，使重追意图在下次同步中胜过墓碑）；`FollowingPlaybackBridge.addAsync` 对墓碑行调用 saveNew 走复活。
- **防复活**：`FollowingStore.resolveTmdb`（TMDB 身份迁移）遇墓碑目标直接返回，不迁移不复活；`AlistSubscriptionImporter.isTombstoned` 跳过墓碑候选不新建。
- **备份传播**：`FollowingBackupCodec.capture` 用 `findAll`（含墓碑）——墓碑是删除意图的同步载体；`mergeAll` 本地侧也用 `findAll` 参与合并（本地墓碑不能被旧快照 union 复活）。
- `FollowingStore.purge`（物理删除）供测试清理/全量恢复使用。

## 涉及文件

| 文件 | 变更 |
|---|---|
| `following/Following.java` | +deletedAt 字段、copy()、isDeleted() |
| `following/FollowingDatabase.java` | VERSION=3、MIGRATION_2_3（全量重建表+enabled=0→墓碑） |
| `following/FollowingDao.java` | findActive/findActive(key)/markDeleted、findDue/unreadCount/findUnmatchedBySource 过滤墓碑 |
| `following/FollowingMergePolicy.java` | mergeOne 墓碑 LWW 分支 |
| `following/FollowingStore.java` | delete=markDeleted、findAny、saveNew 复活分支、mergeAll 用 findAll、projectHistory 跳过墓碑、purge |
| `following/FollowingPlaybackBridge.java` | addAsync 墓碑复活路径 |
| `following/FollowingBackupCodec.java` | capture 用 findAll（含墓碑） |
| `following/AlistSubscriptionImporter.java` | isTombstoned 跳过墓碑候选 |
| `schemas/.../FollowingDatabase/3.json` | Room 导出 schema |
| 测试 | FollowingMergePolicyTest(+5)、FollowingDeleteDeviceTest(重写+2)、FollowingDatabaseTest(v2→v3 迁移测试+fixture 修正)、FollowingMigrationDeviceTest(清理改 purge)、FollowingDeviceDataRule(+newFollowing 辅助) |

## 验证记录（2026-10-03，emulator-5561 覆盖安装）

- JVM 单测：Following 核心套件 **34/34 通过**（含 5 个新墓碑合并用例：墓碑传播/双墓碑 LWW/重追复活/取消后重追再删/旧快照不复活）。
- 设备测试（am instrument，17 个）：**16/17 通过**。唯一失败 `FollowingActivityDeviceTest.activityRendersStoredFollowingAndFiltersUpdates` 为 HEAD 历史遗留（stash 本任务改动后依然失败，实证与本任务无关）。
- 迁移测试：v1→v2→v3 保留数据；v2→v3 中 `enabled=0` 行转墓碑、活行 deleted_at=0。
- 关键设备用例：取消追更后 UI find 为 null 且 findAny 为墓碑；旧快照合并（空列表/含墓碑）不复活；重追后复活且 createdAt > deletedAt；订阅导入跳过墓碑。
- 安装校验：本地与设备 APK SHA-256 一致（d558a509dba70d0a…）。

## 已知边界

- 墓碑行永久保留（无保留期清理）：追更量级极小，YAGNI；若未来多设备长期同步需要清理，用 `deleted_at` 索引按保留期物理清除即可。
- `notifyEnabled` 的 AND 合并缺陷（两端各关一次无法重开）为既有问题，不在本任务范围。
- 旧版（无墓碑 schema）对端收到墓碑行时按普通行解析：`enabled=false` 不进 UI/调度，无损坏；但删除意图只在新版对端生效——升级双端后语义完整。

## 历史与提交

- 分析阶段：确认 union 合并无法表达删除（2026-10-02）。
- 方案对比：软删除 vs 墓碑（用户拍板墓碑，2026-10-02/03）。
- 实施：2026-10-03，一次性原子提交（见 git log C9-following-tombstone-sync）。

## Next action

已完成。若后续出现跨设备"取消后重追"冲突反馈或云同步立项，将 `enabled` 升级为完整 LWW 状态转换时间戳（deletedAt 与 createdAt 已具备该语义基础）。
