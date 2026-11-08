# C60：Android 9 电视盒子「打开数据库即崩溃」兜底修复（Room 数据完整性）

## Recovery anchor

- **目标**：修复用户反馈的 Android 9（CM311-1sa，API 28）盒子"装完即闪退"的第二个独立缺陷——Room 数据完整性校验失败发生在**打开数据库时**，调用点无法捕获，导致应用每次启动即崩（崩溃循环）。
- **验收标准**：① 同场景（`versionCode` 相同但 `identity_hash` 不匹配的库）不再崩溃且应用可用；② 不可读的原始库文件被保全而非静默删除；③ 与 identity 失败无关的打开失败行为**保持不变**；④ 正常升级路径的数据零丢失；⑤ 聚焦测试 + Android 9 真机验证通过；⑥ 提交 + recovery tag。
- **当前状态**：**已完成**。四种真机场景（全新库 / identity 不匹配 / v45 有数据迁移 / 非 identity 损坏负对照）全部符合预期。
- **配套任务**：本缺陷的触发入口（旧包被当成新版反复覆盖安装）由 `C59` 切断，见 `docs/C59-android9-box-update-downgrade-fix-20261011.md`。
- **回滚锚点**：`git revert <C60 提交>`；改动集中在 `AppDatabase` 的打开期兜底，删除即可回到 Room 默认行为。
- **下一动作**：无（本任务闭环）。

## 时间与设备

- 任务开始：2026-10-11 13:07（Asia/Shanghai）；记录时 13:35。
- 基线：`dev1` HEAD = `d8c9bdc650115daf21d9ea1abfa3efb7d8f0535a`（C59 提交），基线 recovery tag = `recovery/C59/20261011130731-d8c9bdc65011`。
- 设备：`192.168.50.3:5555`（`ro.build.version.sdk=28`、`ro.build.version.release=9`，aosp_marlin 镜像）。
- 取证目录：`/tmp/webhtv-box-evidence/c60/`（原始日志与数据库样本；不进仓库）。

## 缺陷与证据

### 触发条件

两个 APK 声明**相同的 `versionCode`**，但实体/schema 不同 → Room 存储的 `identity_hash` 与本次构建期望值不一致。实测本次发布的两个渠道包正是如此：stable（`edf04f9`）数据库 `VERSION=45`，beta（`841a570`）`VERSION=48`，而两者 `versionCode` 同为 `560`。

### 修复前实测（同一机位，Android 9）

```text
E/AndroidRuntime: FATAL EXCEPTION: main
java.lang.RuntimeException: Unable to start activity ComponentInfo{.../ui.activity.HomeActivity}:
  java.lang.IllegalStateException: Room cannot verify the data integrity.
  Looks like you've changed schema but forgot to update the version number. ...
  Expected identity hash: bbde8a33386ce26c0e0897467faa465d, found: 11111111111111111111111111111111
    at androidx.room.BaseRoomConnectionManager.checkIdentity(RoomConnectionManager.kt:291)
    at androidx.room.BaseRoomConnectionManager.onOpen(RoomConnectionManager.kt:270)
    at androidx.room.RoomConnectionManager$SupportOpenHelperCallback.onOpen(RoomConnectionManager.Android.kt:177)
```

→ `Force finishing activity ...HomeActivityCurrent` → `Process ... has died`，且**每次**启动重复。这与盒子日志 `10:38:34` / `10:38:52` / `10:38:54` 反复出现的 `E/AndroidRuntime` + `Force finishing HomeActivity` + `has died` 形状一致。

### 同一缺陷的另一面（修复前实测）

v48 数据 + v45 包（即 C59 切断的降级路径）→ `user_version` 回落 45、`Keep/History/Config` 全部归 **0**（80 条历史、收藏、配置被 `fallbackToDestructiveMigration(true)` 静默销毁）。C59 已使应用不会自动走到这条路径。

## 修复

`app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java`

新增 `IntegrityRecoveringOpenHelperFactory`（包装 `FrameworkSQLiteOpenHelperFactory`，经 `Room.databaseBuilder(...).openHelperFactory(...)` 接入）与 `IntegrityRecoveringOpenHelper`：

- 仅在 `delegate.getWritableDatabase()/getReadableDatabase()` 抛出、且异常链中出现 **`Room cannot verify the data integrity`** 时介入；其余打开期异常**原样抛出**（不做类型级吞异常）。
- 恢复动作：把不可读的库文件改名保全为 `tv.incompatible-<yyyyMMddHHmmss>`（`-wal/-shm/-journal` 一并清理），随后用全新 helper 重建库；隔离副本最多保留 2 份（`QUARANTINE_KEEP_COUNT`）。
- 重试**只允许一次**（`recovered` 标志），第二次失败照常抛出，不会无限重试。
- 新增 `isIdentityFailure()`（按 Room 原文匹配）、`quarantine()`、`trimQuarantine()`、`databaseFile()`（尊重 `useNoBackupDirectory`）。

### 方案比较与取舍

| 方案 | 结果 |
| --- | --- |
| 不改（仅靠 C59 防降级） | 用户手动侧载或历史遗留的不匹配库仍会**永久崩溃循环**，且必须在 `Application`/`Activity` 层逐点 try-catch，无法覆盖打开期。 |
| `fallbackToDestructiveMigration(true)`（现状） | 覆盖"版本号不同"的降级，但**不覆盖** Room 的 identity 校验失败（版本号相同、schema 不同），实测仍崩。 |
| **改名保全 + 重建（采纳）** | 应用保持可用，原始文件留在设备上（可配合应用自带备份/恢复人工取回），且失败面被限制为一次性重建。 |
| 直接删除坏库 | 应用可用但数据无痕消失，无法事后取证或恢复。 |
| 直接崩溃并提示用户 | 盒子彻底不可用，与用户诉求（能用起来）相悖。 |

## 验证

### 聚焦单元测试

```bash
./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.db.AppDatabaseIntegrityRecoverySourceTest'
```

`BUILD SUCCESSFUL`；**4 tests / 0 failures / 0 errors / 0 skipped**。覆盖：兜底确实接入 `openHelperFactory`、仅限 identity 失败（非类型级吞异常）、单次重试 + 重建前关闭旧 helper、以及"改名保全 + 隔离副本有上限"。

### 真机构建与安装（Android 9）

```bash
bash scripts/build_arm64_debug_install.sh --flavor leanback --abi arm64-v8a --serial 192.168.50.3:5555
```

`✅ 安装成功`（覆盖安装，未卸载、未换签名）。

### 真机场景（`/tmp/webhtv-box-evidence/c60/`）

| # | 场景 | 结果 |
| --- | --- | --- |
| 1 | 全新库启动 | `FATAL=0`、正常启动（`s1-fresh-boot.txt`） |
| 2 | `identity_hash` 不匹配（修复前为 FATAL 崩溃循环） | `FATAL=0`、正常启动；日志 `TV-database: integrity check failed, rebuilding tv error=Room cannot verify the data integrity ...`；坏库保全为 `tv.incompatible-20261011132735`（`s2-identity-mismatch.txt`） |
| 2b | 隔离副本是否真的保住数据 | 隔离副本仍含恢复前的数据：`identity=1111...`、`Keep = [('kq','隔离前源','隔离前剧集')]` → 证明是**保全**而非删除 |
| 3 | v45 有数据 → 新构建迁移到 v48 | `FATAL=0`、正常启动、**未触发**兜底重建；数据完整：`user_version=48`、`Keep 2 / History 60 / Config 1 / Site 1 / Track 1 / Device 1 / TmdbSeasonProgress 1 / PlaybackDeleteTombstone 1`，样本 `('h0','测试剧集0')`（`s3-v45-to-v48.txt`） |
| 4 | 负对照：非 identity 的损坏库（随机字节） | 由 androidx.sqlite 自身处理（`Corruption reported by sqlite ... deleting the database file`），应用正常启动；**兜底未介入**（`integrity check failed` 计数 0、无 `incompatible-*` 文件）→ 证明兜底范围收得很窄（`s4-nonidentity-failure.txt`） |

## 有意保留的行为与残留风险

- **用户手动侧载更旧的包**仍走 `fallbackToDestructiveMigration(true)` 重建库（Room 的降级路径不经异常，无法在打开期拦截），数据仍会丢失。本任务未扩大改动面去关闭它；如需彻底禁止应改为 `fallbackToDestructiveMigrationOnDowngrade(false)` 并评估老用户影响。
- 兜底当前**仅写日志**（`TV-database` 标签，可在应用内 AI 诊断/日志页查看），未弹用户提示。加提示需新增字符串资源，超出本任务声明范围，故记录而不实施。
- "两个不同来源的构建各自把 `VERSION` 设为 48 导致 identity 冲突"的**源头**未追查（本任务只做应用侧兜底）；建议后续在发布流程中确保同一发布线只有一个构建来源。
- 隔离副本按时间保留 2 份，未做容量统计；数据库为 KB~MB 级，风险可忽略。

## 复评补充（C61，2026-10-11）

合并 `origin/beta` 后复评时，在本任务的注释中发现一处真实缺陷 **F2** 并已修复（**行为零改动**）：`IntegrityRecoveringOpenHelperFactory` 的 javadoc 与 `quarantine()` 的 javadoc 原文称「不可读的数据库文件被保全而非删除、原文件留在磁盘上」，但实现中 `-wal/-shm/-journal` 会被清理，且 `renameTo` 失败时会删除主库文件（否则重建无法落盘）。两处注释已改为如实描述「主库改名保全 + 边车文件清理 + 无法改名时删除」。

本任务的代码行为、真机四种场景结论、聚焦用例结论均不受影响（`AppDatabaseIntegrityRecoverySourceTest` 4 例在合并后复跑仍全绿）。详见 `docs/C61-beta-merge-review-dev1-20261011.md` 的 F2 与「复评记录」章节。

## 与既有任务的关系

本任务与上游播放器依赖合并计划（`docs/upstream-player-dependency-merge-assessment-2026-08-20.md`）无关，不涉及 Exo/MPV/common 任何阶段，未改动任何上游依赖、锁文件或原生产物。与 `C59` 构成同一用户反馈的**两个独立原子改动**（按 `AGENTS.md` §6 分别提交）。

## 改动文件

| 文件 | 改动 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java` | 新增 `IntegrityRecoveringOpenHelperFactory`/`IntegrityRecoveringOpenHelper`/`isIdentityFailure()`/`quarantine()`/`trimQuarantine()`/`databaseFile()` 及隔离常量，并接入 `openHelperFactory(...)` |
| `app/src/test/java/com/fongmi/android/tv/db/AppDatabaseIntegrityRecoverySourceTest.java` | 新增：兜底接线、仅限 identity 失败、单次重试、保全原文件的源码契约 |
| `docs/C60-android9-box-database-integrity-recovery-20261011.md` | 本文件 |
