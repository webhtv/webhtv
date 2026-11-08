# C91-beta-merge-review-dev3-20261021

## Recovery anchor

- **目标**：把 `origin/beta@2c3af2f009` 合并进 `dev3`（零复活、零丢失），复评 `dev3` 唯一已提交未推送的提交 `7faf6cea93`（E-SP11：对齐上游帧调度默认值 + 暴露解码器排除状态），修复必修项，验证通过后提交、推送、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突、合并结果树与 `git merge-tree` 预演逐字节一致；B（`beta` 树 − 合并树）= 0；C（revert 删除路径 ∧ 不在 `beta` ∧ 在合并树）= 0。
  2. `7faf6cea93` 的 6 个代码路径与 2 个文档只读复评；必修项已修且每条有可证伪测试或可复核证据。`beta` 本次带入的 C90（dev2 片尾快捷聚焦）按「已由 C90 复评 + 零路径交集」跳过复评。
  3. 双 flavor arm64 Debug Java 编译通过；exo 全包单测 + 播放性能定向单测全绿；`scripts/check_ui_tokens.sh` PASS。
  4. 合并提交保留双父，经任务守卫 `finish` 落盘并创建 recovery tag。
  5. 推送 `dev3`，创建到 `beta` 的 PR（中文描述，只创建不合并），随后拉取远端最新代码。
- **Lane**：`standard`
- **Scope**：`app/`、`docs/`
- **任务守卫**：`C91-beta-merge-review-dev3-20261021`，base HEAD `7faf6cea93026f477f0fefc29e0a20555126db9b`，branch `dev3`，受保护脏路径 0
- **基线**：`dev3` HEAD = `7faf6cea93`（相对 `origin/dev3` ahead 1，未推送）；merge-base = `3a170afeec`
- **当前状态**：全部完成；交付坐标见 §7
- **下一动作**：无

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `2c3af2f009`（PR #468 合入后） |
| 本地 | `dev3` = `7faf6cea93`（1 个未推送提交，E-SP11） |
| merge-base | `3a170afeec85df5340bad9c92bd02a9d370da4bb`（= PR #467 的 C89 文档回填） |
| 冲突 | **0**（`git merge --no-ff --no-commit origin/beta` → `Automatic merge went well`；`git diff --name-only --diff-filter=U` 为空） |
| 预演/实合并树 | `f2fbd4518b2b60ebb9579d2113fffc0caeb13ac6`（`git merge-tree --write-tree` 预演与合并后 index 树**逐字节相同**） |

本次 `beta` 带入 6 个提交（`760f387de3` 片尾快捷聚焦修复、`944dd6419f` 5557 真机证据、`687a6b65da` PR #467、`dbed4ad543` C90 合并+复评、`35d6dae6b6` C90 文档回填、`2c3af2f009` PR #468）。

## 2. 复评范围与跳过依据

- **复评**：`7faf6cea93`，8 个路径：
  - `app/src/main/java/.../player/exo/ExoDecoderRuntimeProfileStore.java`（新增 `activeBlacklist`）
  - `app/src/main/java/.../player/exo/ExoDecoderRuntimeProfiles.java`（新增 `blacklistedCount`/`blacklistSummary`/`mimeLabel`）
  - `app/src/main/java/.../player/exo/ExoUtil.java`（丢帧阈值 5 ms → 15 ms）
  - `app/src/main/java/.../setting/PlaybackPerformanceCatalog.java`（两项说明文案）
  - `app/src/main/java/.../setting/PlaybackPerformanceSetting.java`（默认值、迁移、`getSummary` 追加排除摘要）
  - `app/src/main/java/.../player/exo/ExoRuntimeAwareVideoRenderer.java`（同一阈值的 builder 路径）
  - `app/src/test/java/.../player/exo/ExoDecoderRuntimeProfilesSummaryTest.java`（新增 4 例）
  - `docs/E-SP11-exo-tv-frame-drop-defaults.md`、评估索引 1 行
- **跳过**：`beta` 带入的 C90 内容（leanback `VideoActivity.onKeyUp()`/`isEndingShortcutDue`、`PlayerControlFocusIntegrationTest`、C90 与片尾快捷聚焦文档）。依据：该提交已在 `dev2` 上由 C90 完整复评（含 5557 三例实测证据），且与本次 `dev3` 改动**零路径交集**；合并后这些路径相对 `origin/beta` 内容级 diff 为空（见 §3-E 的反向核对）。
  本轮仍做了一次独立抽查：`isEndingShortcutDue()` 的 `player()` 提前解引用（原代码只在「无记忆焦点」分支解引用）在 `service()==null` 时理论可空，但 `onKeyUp` 只在该 Activity 持有焦点且服务已绑定期间派发，同文件其余按键处理同样无条件调用 `player()`，且 `position > 0 && duration > 0` 守卫屏蔽直播/未知时长；判定不构成新增风险，不修。

## 3. 零复活 / 零丢失证据

| 判据 | 含义 | 结果 |
| --- | --- | --- |
| 路径数 | merge / beta / base | **5174 / 5172 / 5172** |
| **A** | 合并树 − `beta` 树 | **2**，恰为 `dev3` 未推送的 E-SP11 新增文件（`ExoDecoderRuntimeProfilesSummaryTest.java`、`docs/E-SP11-…md`） |
| **B** | `beta` 树 − 合并树（丢失面） | **0** |
| revert 集合 | 主题行锚定（`revert`/`回退`/`撤销` 开头，`dev3`+`origin/beta` 全历史去重） | 22 提交 / 44 条删除路径；其中「删除∧不在 beta」28 条 |
| **C** | revert 删除 ∧ 不在 `beta` 树 ∧ 在合并树（复活面） | **0** |
| **D** | 检测器自检 | 探针 `app/src/main/assets/themes/index.json` 在 revert 删除集且不在 `beta` 树（有效探针） |
| 内容级净差（`dev3` 树 → 合并树） | 本次合并实际带入内容 | 5 路径 / +238 −2（`VideoActivity` 24、`PlayerControlFocusIntegrationTest` 25、C90 文档 95、片尾文档 95、评估索引 1） |
| 反向核对 | `beta` 树 → 合并树 | 8 路径，全部为 E-SP11 自身改动；C90 路径的 diff 为空 |

## 4. 复评发现与修复

| # | 级别 | 位置 | 问题 | 修复 |
| --- | --- | --- | --- | --- |
| 1 | **high** | `ExoUtil.java:956`、`ExoRuntimeAwareVideoRenderer.java:109` | 「输入丢帧阈值」开关在**关闭**时不调用 setter，而 media3（含本仓 fork `1.11.0-alpha01-fongmi`）的 `lateThresholdToDropDecoderInputUs` 默认就是 **15 ms**（`MediaCodecVideoRenderer.java:153`、`DefaultRenderersFactory.java:143`）。E-SP11 把「开启」值也改成 15 ms 后，**开与关完全等价**：用户关掉开关仍会丢输入帧，开关变成 no-op。fork 源码明确「传 `C.TIME_UNSET` 才不丢」（`MediaCodecVideoRenderer.java:700-707`）。 | 两条构建路径均改为：开 → `15_000L`；关 → `C.TIME_UNSET`（`ExoUtil` 用三元式，`ExoRuntimeAwareVideoRenderer` 补 `else` 分支 + `import androidx.media3.common.C`）。新增源码级契约测试 `lateDropThresholdSwitchCanActuallyDisableDropping` |
| 2 | medium | `ExoDecoderRuntimeProfiles.java:115-133` | `blacklistedCount()/blacklistSummary()` 直接统计 store 里**所有**黑名单条目。条目键含 `environmentDigest`（= `Build.FINGERPRINT` + app versionCode/versionName + media3 版本），而 store 有 **30 天 TTL**、跨升级保留。升级后旧条目永远不可能再命中，却仍被显示成「硬解已排除 N 条」，把「历史上失败过」误报成「当前正在软解」。 | 抽出 `activeBlacklist()`：按当前 `environment.digest()` 过滤后再统计/取最新。新增测试 `summaryIgnoresExclusionsRecordedUnderAnotherEnvironment`（跨环境条目被排除，仅当前环境计入） |
| 3 | low | `PlaybackPerformanceSetting.java:500` | `getSummary()` 无条件追加 EXO 的硬解排除摘要，而该行同时服务 EXO/MPV/IJK 三个内核；MPV/IJK 下 App 会显示一个在该内核下不可能发生的「硬解已排除」。 | 仅当 `PlayerSetting.getPlayer() == PlayerSetting.EXO` 时追加 |
| 4 | low | `PlaybackPerformanceCatalog.java:114-115` | 两项说明与实现不符：①「解码耗时推进」把掉帧风险写死成「电视盒时序精度差时容易出现」（本轮证据只支持「时序精度较差的设备上可能」）；②「输入丢帧阈值」未说明实际阈值（15 ms）与「关闭后会怎样」，而关闭语义本轮刚被修正。 | 改写为条件表述，并补「15 毫秒，与上游默认一致」「关闭后不丢输入帧，解码跟不上时可能累积音画延迟」 |

**明确不修（记录为已知边界）**：

- 摘要字符串为硬编码中文（`硬解已排除 N 条（HEVC 3840×2160）`）。同屏的 `getProfileName()`（「自动/均衡/轻量」）与 `PlaybackPerformanceCatalog` 全部选项说明本来就是硬编码中文，整块「播放性能」设置页在默认（英文）locale 下已如此；单独资源化一行会与该屏其余文案不一致，且会把 `Context` 依赖引入当前可纯 JVM 单测的 `ExoDecoderRuntimeProfiles`。
- 旧环境条目在 store 中保留到 30 天 TTL，期间占用 `MAX_ENTRIES = 64` 名额。`trimToCapacity()` 已优先淘汰黑名单条目，且真实设备条目量远小于上限；收紧淘汰策略属存储层改动，超出本轮复评范围。
- 未做设置页实机渲染走查：本轮 `adb devices` 只有 `127.0.0.1:5557` 与 `emulator-5556`，二者均为 **x86_64 / SDK 28** 镜像，且当前工作树未构建 x86_64 产物；因此「摘要那一行的实际渲染」与「阈值关闭后的丢帧行为」没有设备端证据。E-SP11 已记录该环境无法验证硬解掉帧（x86 镜像无 4K HEVC 硬解），本轮不再重复。

## 5. 可证伪证据（新增/更新的锁）

- `PlaybackPerformanceSettingSourceTest.lateDropThresholdSwitchCanActuallyDisableDropping`：断言阈值常量为 `15_000L`、`ExoUtil` 与 `ExoRuntimeAwareVideoRenderer` 都出现 `C.TIME_UNSET`、`C` 已 import。把「关闭」分支改回「不调用 setter」即转红。
- `PlaybackPerformanceSettingSourceTest.durationProgressKeepsUpstreamDefaultUntilMigrated`：断言 getter 兜底为 `false`、`putRecommendedFlags()` 写 `false` 且不再写 `true`、`ensureInitialized()` 调用 `migrateExoFrameSchedulingParity()`。回退默认值或漏挂迁移即转红。
- `ExoDecoderRuntimeProfilesSummaryTest.summaryIgnoresExclusionsRecordedUnderAnotherEnvironment`：写入 1 条他环境 + 1 条当前环境黑名单，断言计数为 1、摘要含 `H.264` 且不含 `HEVC`。去掉环境过滤即转红。
- 既有 4 例（空摘要、两次连续失败后摘要含格式与分辨率、单次失败无摘要、`activeBlacklist` 倒序且跳过健康条目）保持通过。

## 6. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 双 flavor arm64 Debug Java 编译 | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL in 3m 19s**（JDK 21，`--offline --no-daemon`） |
| 定向单测（复评+修复面） | `:app:testLeanbackArm64_v8aDebugUnitTest --tests '*ExoDecoderRuntime*' --tests '*PlaybackPerformance*'` | **50 例 / 0 失败 / 0 错误**（Summary 5、Store 13、Session 13、Key 4、SettingSource 9、UiPolicy 6） |
| exo 全包单测 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.player.exo.*'` | **617 例 / 1 skipped / 0 失败 / 0 错误** |
| UI token 门禁 | `bash scripts/check_ui_tokens.sh` | **PASS**：`violations=0`、`hex_layouts=0`、`hex_drawables=0`、`hex_colors=0`，对比度 38 对 0 失败（min=4.28） |
| 第三方依赖事实核对 | `third_party/maven/.../media3-exoplayer-1.11.0-alpha01-fongmi-sources.jar` | `DEFAULT_LATE_THRESHOLD_TO_DROP_DECODER_INPUT_US = 15_000`；`DefaultRenderersFactory` 默认同值；builder 仅在 `!= C.TIME_UNSET` 时启用预测丢帧 ⇒ 支撑发现 #1 的判据 |
| 修复后二次复评 | 8 个改动路径逐行复核 | **通过，无新增必修项**（余下 3 条已记录为已知边界） |

## 7. 交付坐标

- 合并提交：`a8ae5e8602afd47c5a2079a9f1a79d215f1a08f7`（双父 `7faf6cea93026f477f0fefc29e0a20555126db9b` + `2c3af2f009430db2e595941de7b53fed37f62718`）
- Recovery tag：`recovery/C91-beta-merge-review-dev3-20261021/20261021055048-a8ae5e8602af`
- 推送：`dev3`（`3a170afeec..a8ae5e8602`）
- PR：**#469** <https://github.com/Silent1566/webhtv/pull/469>（`base=beta` / `head=dev3` / `state=OPEN` / `mergedAt=null`，只创建不合并）
- 拉取远端最新代码：`git pull --ff-only origin dev3` → `Already up to date`；`git fetch origin beta` → `2c3af2f009`（= 本次合并目标）
- 口径提示：本仓库有两个 remote，`gh pr create` 不带 `-R Silent1566/webhtv` 时解析到 upstream `fish2018/webhtv`，会报 `No commits between beta and dev3`；创建 PR 必须显式指定 `-R`。
