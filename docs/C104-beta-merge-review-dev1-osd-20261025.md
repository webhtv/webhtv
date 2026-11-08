# C104-beta-merge-review-dev1-osd-20261025

## Recovery anchor

- **目标**：把 `origin/beta@8322438787` 合并进 `dev1`（零冲突、零丢失），逐行评审 `dev1` 仅有的 2 个已提交未推送提交（`192b55c2fc` 徽标常驻语义、`11be83da8b` 扩充包并入 127 条规则 / 127 张牌图 / 同行等高），修复必修项并定向验证，复评通过后提交、推送 `dev1`、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突；`beta` 带入的 24 路径内容在合并树中与 `beta` 逐 blob 一致（无自动合并改写）。
  2. 评审覆盖 `dev1` 相对 `origin/dev1` 的全部 34 个改动路径（6 个非二进制 + 28 张新增 + 1 张修改牌图）；`beta` 带入内容因「已评审 + 0 冲突 + 与本地改动路径交集为空」按用户规则跳过并留证。
  3. 每个必修项修复可复核；修复后第二轮复评通过。
  4. 定向单测（双 flavor arm64 Debug）一次通过：徽标解析、设置同步、控制栏 OSD、掉帧口径、固定浅色对比度。
  5. 本轮改动经任务守卫 `finish` 落盘并创建 recovery tag；推送 `dev1`；创建到 `beta` 的中文 PR（描述含具体改动内容）；随后拉取远端最新代码。
- **Lane**：`standard`
- **Scope**：`app/src`、`docs`
- **任务守卫**：`C104-BETA-MERGE-DEV1-OSD-20261025`，branch `dev1`，base HEAD `11be83da8b345f51a9615bbd111f84f420fe7b91`，受保护脏路径 0
- **当前状态**：合并、评审、修复、验证、复评已完成；提交/推送/PR/拉取进行中
- **下一动作**：`task_guard.sh finish`（`MERGE_HEAD` 在树中，finish 生成合并提交）+ recovery tag，然后推送 `dev1`、创建到 `beta` 的中文 PR、拉取远端最新

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `8322438787d8f3e769ef244308d634dbd72d8307`（6 个提交：PR #488 合并 `8322438787`、C103 dev4 文档回填 `306b823dc7`、移动端广告拦截日志条固定浅色 `9b0a547eae`、dev4 内并 `7c725e4d7ee` 的合并 `62f0b777d2`、固定浅色面板前景色 `9689118851`（19 路径 +406/-129）、退出确认次级文案可读性 `b829d104ef`（4 路径 +580/-4）） |
| 本地 | `dev1` = `11be83da8b345f51a9615bbd111f84f420fe7b91`（领先 `origin/dev1` 2 个提交：`192b55c2fc`、`11be83da8b`，均未推送） |
| merge-base | `7c725e4d7ee43ca0a187e420b224b33bb0a39a6a`（与 C103 相同，即 C103 合并点） |
| 冲突 | **0**（`git merge --no-ff --no-commit origin/beta` 自动合并，`git diff --diff-filter=U` 为空） |
| 自动合并文件 | **0**（本轮 `beta` 带入的 24 路径与本地未推送改动**零路径交集**，故不存在「beta 内容 + 本地改动」的交汇文件） |
| 带入路径 | **24** 个（20 M + 4 A），`1216 insertions / 87 deletions` |
| 路径交集 | **0**：`dev1` 未推送改动路径（34）∩ `beta` 带入路径（24）= 空 |
| 丢失面 | 0（暂存区 24 路径的 blob 与 `origin/beta` **逐字节一致，24/24**） |

带入路径清单（20 M + 4 A）：

`app/src/leanback/res/drawable/selector_exit_confirm_secondary.xml`、`app/src/leanback/res/layout/{adapter_ad_block_log,adapter_ad_rule,adapter_ad_stats_item,dialog_ad_block_stats,dialog_exit_confirm,dialog_update}.xml`、`app/src/main/res/color/fixed_light_focus_icon.xml`（A）、`app/src/main/res/color/fixed_light_focus_text.xml`（A）、`app/src/main/res/layout/{adapter_custom_csp,adapter_mpv_config_profile,dialog_ai_recommendation_info,dialog_custom_csp,dialog_mpv_config_create,dialog_mpv_config_manager,dialog_mpv_config_rename,dialog_update_settings}.xml`、`app/src/main/res/values/colors.xml`、`app/src/mobile/res/layout/adapter_github_proxy.xml`、`app/src/mobile/res/layout/dialog_ad_block_stats.xml`、`app/src/test/java/com/fongmi/android/tv/theme/TvFixedLightSurfaceContrastTest.java`（A）、`docs/C103-beta-merge-review-dev4-20261024.md`（A）、`docs/TV-EXIT-CANCEL-CONTRAST-01-tv-exit-cancel-contrast.md`（A）、`docs/TV-LIGHT-FILL-CONTRAST-ALL-02-tv-fixed-light-surface-foregrounds.md`（A）。

## 2. 评审范围与跳过依据

- **全量评审对象**：`dev1` 相对 `origin/dev1` 的全部改动，共 34 路径（`git diff --name-status origin/dev1..HEAD`）：
  - 规则表：`app/src/main/java/com/fongmi/android/tv/player/MediaBadgeCatalog.java`（100 → 127 条，新增 `VIDEO_TECH`/`EDITION` 两组）。
  - 解析器：`app/src/main/java/com/fongmi/android/tv/player/MediaBadgeResolver.java`（8K / 12bit / VVC / LPCM / 7.1.4 / 5.1.4 / 5.1.2 七条新运行时事实）。
  - 屏显渲染：`app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java`（控制栏唤醒无条件显示；常驻分支按开关；`topInset`/`bottomInset` 随控制栏；胶囊固定 `dp(20)` 等高）。
  - 测试：`app/src/test/java/com/fongmi/android/tv/player/MediaBadgeResolverTest.java`（27 例，总数断言 100 → 127）、`app/src/test/java/com/fongmi/android/tv/setting/PlayerDisplaySettingSyncTest.java`（新增源码文本断言用例）。
  - 资产：28 张新增 + 1 张修改（`badge_gv_10bit.webp` 7512 → 11570 字节）`app/src/main/res/drawable-nodpi/badge_*.webp`。
  - 文档：`docs/osd-media-badges.md`。
- **跳过**：`beta` 带入的 24 路径内容已在 `docs/C103-beta-merge-review-dev4-20261024.md` 中同日评审（`9689118851` + `b829d104ef`），本轮合并 0 冲突、24/24 blob 与 `beta` 逐字节一致、且与本地改动路径交集为空，满足用户「已评审 + 无合入冲突即可跳过（前提是先前评审已覆盖当前全部修改代码）」的规则。

## 3. 首轮评审发现与处置

**必修项：2（均为文档口径错误，无运行时代码缺陷）；已修复。**

| 编号 | 位置 | 问题 | 处置 |
| --- | --- | --- | --- |
| F1 | `app/src/main/java/com/fongmi/android/tv/player/MediaBadgeCatalog.java:6` | 类 javadoc 写「全部 **124** 条牌（100 条来自参考实现，**24** 条来自扩充包）」，与实际 127 条（100 + **27**）不符；同文件 `:176` 已写 127，同一文件内自相矛盾 | 改为「全部 127 条牌（100 条来自参考实现，27 条来自扩充包）」；同时把 `:12` 的「其余组」列举补齐为「发布来源、画质、**版别**、特别标签、IMAX、流媒体、**视频技术**」（原列举漏掉新增两组） |
| F2 | `docs/osd-media-badges.md:35/70/149` | 127 张牌图体积记为「约 620 KB」，实测 `ls -l badge_*.webp` 合计 **661706 字节（约 646 KB）** | 三处改为 646 KB，`:149` 补上实测字节数；另把 `:65` 的「13 个分组」标注第六轮已扩到 15 组、`:66` 的「365 行」改为实测 367 行 |

逐面复核结论（未发现缺陷）：

| 复核面 | 结论 |
| --- | --- |
| `Group` 枚举新增两值的安全性 | 全仓库仅两处 `switch(Group)`，均有 `default`：`PlayerOsdController.mediaBadgeColor`（15 case + default）与 `MediaBadgeResolver.displayOrder`（15 case + default）；无 `Group.values()`/`ordinal()` 依赖，新增枚举值不会破坏编译或顺序 |
| 三向牌键契约 | `catalog` 127 ↔ `PlayerOsdController.plates()` 127 ↔ `drawable-nodpi/badge_*.webp` 127 ↔ `MediaBadgeResolver.P_*` 42 六项集合差**全空**（无缺牌、无孤图、无未引用常量） |
| `patterns()` 长度 | `Pattern[] next = new Pattern[RULES.length]` 结构性相等，且 `MediaBadgeResolverTest.catalogIsConsistent` 断言 `patterns().length == rules.length` |
| 常驻徽标与控制栏时序 | 移动端 `VideoActivity` 先 `setOsdSuppressed(true)` 后 `setControlsVisible(true)`（5622 → 5661），隐藏时反向（5675 → 5677），`render()` 的 `suppressed && controlsVisible` 徽标专用分支可达；`!osdEnabled` 早退在徽标判定之前，开关全关时不会被徽标复活 |
| 角落内边距 | `view_player_osd.xml` 内层 `FrameLayout` 自带 `padding=14dp`；常驻 `bottomInset=dp(22)` 使胶囊底边距容器底 `14+22=36dp`，14sp 进度/码率文本占 `14~32dp`，不相压；控制栏唤醒时 `topInset=64dp`/`bottomInset=80dp` 对应控制栏占位 |
| 胶囊等高 | `createMediaBadge` 用 `LayoutParams(WRAP_CONTENT, dp(20))`；牌图 `dp(16)` + 上下 padding `2+2` = 恰好 20dp，无裁切；设备实测同一行 9 个胶囊高度全部 35px（改动前纯文本 30px / 有牌 34px） |
| 运行时优先语义 | `RUNTIME_GROUPS` 只含分辨率/动态范围/编码/音频/声道；`addSourceBadges` 首轮跳过 runtime 规则并按组去重（非运行时组每组最多一枚），次轮仅在运行时缺该组时回填，符合「有证据才显示」 |
| 二进制资产 | 28 张新增牌图 + 1 张重绘（`badge_gv_10bit` 7512 → 11570 字节）；`plates()` 显式常量引用防 release `shrinkResources` 误删 |

**记录但未修（不阻塞、不顺手清理）**：

- **O1**（承 C103）：`app/src/main/res/drawable/shape_player_osd_badge.xml`（269 字节，全文 0 引用），第 3 个提交的旧卡片背景残留。
- **O2**：127 张牌图合计 661706 字节（约 646 KB），是本功能的一次性 APK 体积成本。
- **O3**（承 C103）：`MediaBadgeCatalog.java:82` 的 `gq_hc_hdrip` 规则行长达 4552 字符，内嵌 U+2063/U+200C/U+200D 隐形字符。
- **O4**（承 C103）：`AudioPlaybackDiagnostics` 的 `mp4a.40` 兜底无对应单测，由设备实测覆盖。
- **O5**：`PlayerDisplaySettingSyncTest` 的新用例是**源码文本断言**（读 `.java` 字符串判断门控表达式），能在无 Android 运行时验证门控语义，但改名/重排即失效；属该测试文件既有风格，本轮沿用未改。

## 4. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| mobile 定向单测 | `:app:testMobileArm64_v8aDebugUnitTest --tests "*MediaBadgeResolverTest*" --tests "*PlayerDisplaySettingSyncTest*" --tests "*ControlDialogOsdSettingsTest*" --tests "*DroppedFrameAccountingTest*"` | **BUILD SUCCESSFUL in 3m 32s**，exit 0；27 / 9 / 9 / 8 例，合计 **53 例，0 失败 0 错误 0 跳过** |
| leanback 定向单测 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests "*MediaBadgeResolverTest*" --tests "*TvFixedLightSurfaceContrastTest*" --tests "*ThemeEditorPanelTest*"` | **BUILD SUCCESSFUL in 3m 30s**，exit 0；27 / 4 / 2 例，合计 **33 例，0 失败 0 错误 0 跳过** |
| 差异卫生 | `git diff --cached --check` | 空（无空白错误） |
| 合并完整性 | 24 路径 `git rev-parse :<path>` vs `git rev-parse origin/beta:<path>` | **24/24 逐字节一致**，无自动合并改写 |
| 路径交集 | 带入路径（24）∩ 未推送改动路径（34） | **空**（跳过依据成立） |
| 牌键契约 | catalog ↔ `plates()` ↔ `drawable-nodpi/badge_*.webp` ↔ `P_*` 集合差 | 六项检查**全空** |

## 5. 第二轮复评（修复与验证后）

复评对象为「合并后 + F1/F2 修复后 + 双 flavor 单测通过」的最终树：

- **修复面**：F1/F2 只改注释与文档口径（`MediaBadgeCatalog.java` 类注释 2 行、`docs/osd-media-badges.md` 4 处计数/体积），未触碰任何可执行语句；复核确认改动仅落在这 2 个文件。
- **合并面**：`beta` 带入的 24 路径在修复后仍与 `origin/beta` 逐字节一致（修复文件 `MediaBadgeCatalog.java`/`docs/osd-media-badges.md` 不属带入路径，无交集），无回退。
- **代码面**：`MediaBadgeResolver.resolve` → `addSourceBadges` → `displayOrder` 排序 → `PlayerOsdController.render` → `setMediaBadges` → `applyMediaBadgePosition` → `createMediaBadge` → `categoryBackground/mediaBadgeColor/plateResource` 全链路重读；新增 7 条运行时事实与 27 条文件名事实均有单测锁定；`catalogIsConsistent` 的 127 条 + 唯一性 + runtime 与分组一致三条断言全绿。
- **资产面**：127 张牌图与 127 条规则一一对应，无孤图、无缺图、无未引用 `P_*` 常量。

结论：**通过**，无遗留必修项。

## 6. 交付坐标

- 合并提交：`3cfe93f389615bb32e00540afe23c66b1ec160e1`（双亲 `11be83da8b345f51a9615bbd111f84f420fe7b91` + `8322438787d8f3e769ef244308d634dbd72d8307`），由 `task_guard.sh finish` 在 `MERGE_HEAD` 在场时生成。
- recovery tag：`recovery/C104-BETA-MERGE-DEV1-OSD-20261025/20261024181204-3cfe93f38961`（本地，按约定不推送）。
- 推送：`dev1` → `origin/dev1`，`39e1b4a0d4..3cfe93f389`，成功；推送后 `HEAD == origin/dev1`。
- PR：`dev1` → `beta`，**#487**（`https://github.com/Silent1566/webhtv/pull/487`，OPEN，只创建不合并）。
  - 说明：#487 原为 C103 建立、head 同为 `dev1` 的未合并 PR；本次提交已把它的 head 推进到 `3cfe93f389`，GitHub 不接受对同一 head/base 再开新 PR（`a pull request for branch "dev1" into branch "beta" already exists`），因此改为**就地更新** #487 的标题与正文为 C104 内容，而不是另开重复 PR。
- 拉取：`git fetch origin --prune --tags` 已执行。tag 更新中出现若干 `would clobber existing tag`（本地已存在同名 `v5.5.2-sdk28-*` 标签，远端重写），按「不移动已发布标签」原则未强制覆盖；新增 `v5.6.0-beta-202610241939`。拉取后 `origin/dev1 = 3cfe93f389`（与本地一致）、`origin/beta = 8322438787`（已全部并入 HEAD，`git merge-base --is-ancestor origin/beta HEAD` 成立）、`origin/dev4 = 306b823dc7`。

### 回填（提交后补记）

- 2026-10-25 02:25（Asia/Shanghai）：以上交付坐标已核对并回填。合并提交、recovery tag、推送、PR（#487 更新）、远端拉取全部完成；`dev1` 与 `origin/dev1` 同步，工作区干净。
