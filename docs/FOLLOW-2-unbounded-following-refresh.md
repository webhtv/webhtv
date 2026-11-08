# FOLLOW-2：追更全量刷新与元数据展示

## 状态
已完成；2026-09-25 20:03 CST 开始，分支 `dev2`，基线 `c22cc652a4c8048166291a038110aba01082cff0`。

## 目标
1. 手动“检查更新”不限制条数，逐条刷新全部追更项。
2. 周期/前台到期检查也不再使用 5/20 条批次上限。
3. 在追更卡片补充 TMDB 已可靠提供的本季总集数、全剧总集数和每周更新日。
4. 不改变播放、收藏、提醒和原站可播集数的既有契约。

## 设计结论
- `no change`：维持 5/20 限制，无法满足全量刷新要求，排除。
- `解析全部季推断周几`：会额外请求/遍历大量数据，且用户要求优先查全追更项，首版不做。
- **采用窄改**：`next_episode_to_air.air_date` 已是 TMDB TV 详情主字段，直接持久化其 UTC 周几；`number_of_episodes` 与当前季 `episode_count` 已在现有快照中解析，仅补充到 UI。
- 刷新入口保持单线程逐条执行，宁可慢也不限数；Room 到期查询按 `next_check_at` 排序并返回全部 due 项。

## 实现
- `FollowingActivity.checkAll()` 移除前 5 条限制，遍历全部 `FollowingStore.list()`。
- `FollowingDao.findDue()` 移除 SQL `LIMIT`；`FollowingUpdateCoordinator.checkDue()` 移除前台/后台批次选择。
- `Following` v1→v2 新增 `next_air_weekday` 列，提供保留数据的手写 Migration；`FollowingMetadataSnapshot/Client/UpdatePolicy` 从 TMDB 原始 `air_date` 解析并持久化下一集播出日。
- 追更卡片新增 metadata 行，显示本季总集数、全剧总集数、每周更新日；无数据时隐藏。
- 新增英/简中/繁中文案。

## 验证记录与恢复锚点
- 2026-09-25 20:08 CST：首次定向 Gradle 验证失败，直接原因是 `FollowingMetadataSnapshot.weekday()` 漏导 `java.util.Calendar` 和 `java.util.TimeZone`；已补导，准备重跑同一验证。
- 2026-09-25 20:22 CST：第二次验证仅 `weekdayIsStableForUtcAirDate` 失败。根因是项目 `airTime()` 按本地 20:00 计算而测试用 UTC 星期，星期应直接取 TMDB 的 UTC 日期；已把 `nextAirWeekday` 改为从原始 `air_date` 解析。
- 2026-09-25 20:32 CST：补充检查发现直接改 v1 schema 会让已安装用户启动时因缺少 v1→v2 Migration 崩溃；已升级独立追更库到 v2、手写 `ALTER TABLE` Migration，并准备用设备迁移测试验证。新增 `androidTestImplementation androidx.room:room-testing`。
- 2026-09-25 22:29 CST：最终验证通过。单元测试 23 个通过；设备迁移测试 2 个通过；`scripts/build_arm64_debug_install.sh --flavor mobile --serial 192.168.50.3:5557` 构建、覆盖安装成功，应用可启动且无 fatal 崩溃日志。迁移测试过程中修正了两个测试夹具问题：手工 v1 库需设置 `PRAGMA user_version=1`，且需完整创建 `following_source` 表与索引。
- 目标验证：`./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.following.FollowingMetadataClientTest' --tests 'com.fongmi.android.tv.following.FollowingSchedulePolicyTest' --tests 'com.fongmi.android.tv.ui.activity.FollowingUiSourceTest'`。
- 回滚：撤销本文件所列任务内代码/资源/测试改动；数据库新增列仅影响新安装或自动迁移后的 v1，不需要回滚旧数据。
