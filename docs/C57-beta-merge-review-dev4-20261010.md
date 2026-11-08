# C57：dev4 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入 `dev4`（**远端已移除/回退的提交不得顺带带回**）；复评 dev4 全部已修改代码（含**已提交未推送**的 2 个提交，即影视原生播放器控制栏电池电量图标 + 电池块补齐当前时间）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev4`、创建 `dev4 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① `dev4` 已包含合并时刻的 `origin/beta` tip；② 远端被移除/回退内容**零复活**、beta 增量**零丢失**、dev4 既有改动**零丢失**；③ 双 flavor Java 编译 + 双 flavor AndroidTest Java 编译通过；④ mobile 全量 JVM 套件零失败且增量可解释；⑤ 复评发现的问题已修复并验证（本轮未发现必修问题）；⑥ 提交 + recovery tag、`dev4` 已推送、PR 已创建且**未合并**，PR 文件集与 `git diff --name-only origin/beta HEAD` 逐项一致。
- **当前状态**：评审完成（2 轮，**无必修问题，生产代码零新增改动**）；验证全部通过（双 flavor Java/AndroidTest 编译 + mobile 全量 JVM 单测 4981 项 0 失败）；零复活三层证据通过；已提交任务文档并推送、创建中文 PR。
- **交付坐标**：见文末「交付坐标」与「闭环记录」。
- **下一动作**：无（本任务已闭环）。

## 时间与设备

- 任务开始时本地时间：2026-10-10 16:40（Asia/Shanghai）。任务开始时工作区**干净**（`git status --porcelain` 空），protected 脏路径 0 个。
- `dev4` HEAD = `c442601fbd2`（`feat(mobile): 影视原生播放器控制栏电池块补齐当前时间…`），领先 `origin/dev4`（`27fe5c6927`）17 个提交；其中**只有 2 个**不在 `origin/beta`，是本轮复评对象：
  - `8db36f33a629` `feat(mobile): 影视原生播放器控制栏新增电池电量图标（对齐沉浸融合模式，全屏未锁定时显示，BatteryUtil 档位复用）`
  - `c442601fbd2d` `feat(mobile): 影视原生播放器控制栏电池块补齐当前时间（同构沉浸融合 batteryInfo 竖排容器，时间复用 Formatters.TIME）`
- 设备：**本轮未使用真机/模拟器**。理由（风险相符原则，`AGENTS.md` §4）：本轮**未改动任何生产代码**（净差异 3 路径均来自 2 个已提交未推送的电池提交，评审未发现必修问题）；该电池 UI 属纯布局/显示逻辑，由 VideoActivityLayoutTest 静态结构守卫 + 双 flavor 编译锁定；用户未要求设备验证。未申请机位、未触碰 `192.168.50.3:5557`（dev4 机位，addendum 分配规则）上的既有包。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev4` |
| 任务开始时 HEAD | `c442601fbd2d530829fddfbab371c11955cff1ff` |
| `origin/dev4` | `27fe5c692794b45bbb3a84608963151210101c8c` |
| `origin/beta` tip（fetch 后 `git rev-parse origin/beta`） | `e6413efd1e4bdb3c1725824db6f7b3f8a7dc249c`（Merge pull request #431 from Silent1566/dev1） |
| merge-base | `e6413efd1e4bdb3c1725824db6f7b3f8a7dc249c`（= beta tip，**本身即为 HEAD 祖先**） |
| 是否需要新合并提交 | **不需要**：合并前 `git merge-base --is-ancestor origin/beta HEAD` 为**真**，`git log HEAD..origin/beta` 为**空**（beta 无任何 HEAD 未含的提交） |
| 初始脏路径 | 无 |
| 回滚锚点 | `27fe5c692794b45bbb3a84608963151210101c8c`（= 合并前的 origin/dev4；另有备份分支 `backup/dev4-before-beta-reset-20261010140606`） |

**beta 侧增量 = 0 内容的判据**：`git merge-base origin/beta HEAD` = `e6413efd1e4`（= beta tip 自身），且 `git log --oneline HEAD..origin/beta` 输出**为空**。即 dev4 的祖先链已完整包含 beta tip `e6413efd1e4`（PR #431 合并提交，由 dev1 的 `913ae81d198` 坐标提交链带入）。因此**没有可合并的 beta 新提交**，也没有任何「把回退内容捡回来」的空间——分支关系是纯线性祖先包含。

### dev4 未推送提交 ledger（`origin/dev4..HEAD` = 17 个，其中真正不在 beta 的仅 2 个）

`git log --format='%H %s' origin/dev4..HEAD --not origin/beta`：

| 完整 commit ID | 标题 | 归属 |
| --- | --- | --- |
| `c442601fbd2d530829fddfbab371c11955cff1ff` | `feat(mobile): 影视原生播放器控制栏电池块补齐当前时间…` | 本轮复评对象 |
| `8db36f33a6298bbe2bced429ddcade9f2bfadf28` | `feat(mobile): 影视原生播放器控制栏新增电池电量图标…` | 本轮复评对象 |

其余 15 个 `origin/dev4..HEAD` 提交（`e6413efd1e4`、`913ae81d198`、`fe4d02e076d`、`40ba21ed876`、`22348293b8b`、`841a5707d59`、`67d930ed63a`、`447934222b8`、`92ad7c69d13`、`45bffbb860d`、`7e51768d881`、`814935ceea6`、`f0da126a2c0`、`92f1844342d`、`f9f47c24ae1`）**全部已是 `origin/beta` 的祖先**（经 dev1 合并链带入 beta），不属于本分支独有改动。

### 合并结果净差异（相对 beta tip）

`git diff --name-status origin/beta HEAD` = **3 路径**，全部来自 2 个电池提交，无一条来自 beta 之外：

| 路径 | 类型 | 归属 |
| --- | --- | --- |
| `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` | 修改（+24） | `8db36f33a62` + `c442601fbd2` |
| `app/src/mobile/res/layout/view_control_vod.xml` | 修改（+38） | `8db36f33a62` + `c442601fbd2` |
| `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityLayoutTest.java` | 修改（+43） | `c442601fbd2`（新增 1 个 layout 守卫用例） |

`git diff --diff-filter=D --name-status origin/beta HEAD` = **空**（无删除，未删任何 beta 文件）。

## 零复活校验（远端已回退内容不许带回来）

方法：以**当前 `origin/beta` 整树**为唯一准绳，用集合层证据 + 可证伪自检。

1. **净差异层**：`git diff --name-status origin/beta HEAD` 只输出 3 个本地路径（见上表）——合并树 = beta 树 + 本地改动，任何「beta 已删除/已回退但被合并带回」的路径都会在这里暴露。
2. **集合层**：`resurrected = (净差异路径) ∩ (revert 类提交触及路径 − beta 树现有路径)` = **0**。
   - revert 集按主题行锚定提取（`^(Revert|revert|回退|撤销|剔除)`，只匹配主题行避免匹配正文）：**109 个提交 / 725 个路径**；
   - 远端已删除（在 revert 集且不在 beta 树）路径 = **601 个**（含 `ISSUE_TEMPLATE`、`RELEASENOTES.md`、`app/src/main/assets/themes/*`、`ThemeCatalog*`、`AdBlockPreview*`、`docs/doc/reference/*` 等）；
   - 净差异 3 路径逐一比对：**均不在 601 个已删除路径中**。
3. **反向丢失层**：净差异中无 D 行（无 beta 文件被本分支删除）。

**可证伪自检**：把一条确实「在 revert 集且不在 beta 树」的路径（`ISSUE_TEMPLATE`）注入伪造净差异，检测器**精确报出**；真实净差异下报 0。检测器不是空转。

结论：**零复活、零丢失**。净差异仅触达影视原生播放器控制栏布局/电池显示逻辑，与 beta 上被移除/回退的主题系统、广告段验证、音频指纹、内置壁纸等能力**不相交**。

## 复评第 1 轮（对象：净差异 3 路径 + 2 个已提交未推送提交）

### 生产代码（`VideoActivity.java` + `view_control_vod.xml`）

| 检查项 | 证据 | 判定 |
| --- | --- | --- |
| 可见性规则与融合模式同构 | `mBinding.control.batteryInfo.setVisibility(isFullscreen() && !isLock() && mHistory != null && !player().isEmpty() ? VISIBLE : GONE)`；对照 `TmdbDetailActivity.updateMobileInlineControlStatus`：`showBattery = hasPlayer && inlineFullscreen && !isLock()`（`TmdbDetailActivity.java:8252`）——本地语义 = 全屏 ∧ 未锁 ∧ 已开始播放，与融合模式一致 | 通过 |
| 电量档位复用同源工具 | `updateBatteryInfo()` 用 `BatteryUtil.getLevel(this)` + `BatteryUtil.getIcon(level)`；`BatteryUtil`（`app/src/main/.../utils/BatteryUtil.java`）档位 `≥95 full / ≥85 6 / ≥65 5 / ≥50 4 / ≥25 3 / ≥15 2 / >0 1 / 0`，`level<0` 时调用方整条隐藏 | 通过（与融合模式 `updateMobileInlineBatteryIcon` 同系列逻辑） |
| 时间格式复用 | `LocalDateTime.now().format(Formatters.TIME)`；`Formatters.TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)`（`Formatters.java:11`），与融合模式 `updateMobileInlineControlTime` **逐字节同式** | 通过 |
| 空指针安全 | `showControl()` 首行 `if (service() == null \|\| isInPictureInPictureMode()) return;` 早退；既有代码同方法内已多处直接 `player().isEmpty()`（osdDiagnostics/info/cast），`player()` 在 service 非空后不返回 null（`PlaybackActivity.player()` → `mService.player()`，`PlaybackService.player` 在绑定后初始化）；`updateBatteryInfo()` 由 `showControl()` 与 `onTimeChanged` 调用，均受 `isVisible(mBinding.control.getRoot())` 守卫，且自身先查 `isVisible(mBinding.control.batteryInfo)` 才读电量 | 通过 |
| 刷新节奏 | `onTimeChanged`（每秒回调）中 `if (isVisible(mBinding.control.getRoot())) updateBatteryInfo();`，控制栏可见时每秒刷新电量与时间，与融合模式刷新内联时间节奏一致 | 通过 |
| 布局 id 冲突 | 新增 id `batteryInfo`/`battery`/`batteryTime`；`battery` 另仅存在于 `view_control_vod_tmdb.xml`（融合模式布局，不在 activity_video 树）；刻意**避开** `@id/time`——`view_widget_vod.xml:42` 已有 `@id/time`，与 `view_control_vod` 同处 `activity_video.xml` 视图树（`widget` 与 `control` 两个 include），重名会让 ViewBinding 按 id 查找命错控件。测试也锁定 `view_control_vod` 不得再引入 `time` | 通过 |
| drawable 资源 | `ic_battery_0..6`、`ic_battery_full` 共 8 个 drawable 均存在于 `app/src/main/res/drawable/`；`BatteryUtil.getIcon` 引用的档位全部有对应资源 | 通过 |
| 布局结构 | `batteryInfo` 为 48dp 竖排 LinearLayoutCompat（22×18dp 图标 + 8sp 时间），与 `view_control_vod_tmdb.xml` 的 batteryInfo **同构**；默认 `visibility="gone"`；位于控制栏 action 区右侧 dock | 通过 |
| 是否引入新权限/依赖/导出面 | 无 manifest / gradle / 依赖改动 | 通过 |

### 测试代码（`VideoActivityLayoutTest.java` 新增用例）

| 检查项 | 证据 | 判定 |
| --- | --- | --- |
| 断言是否真实 | 锁定四件事：①布局含 `batteryInfo/battery/batteryTime` 三 id；②时间与图标都嵌在 `batteryInfo` 容器内（`hasAncestorAndroidId`）；③可见性语句含 `isFullscreen() && !isLock() && mHistory != null` 且**不**跟 `PlayerButtonSetting`；④helper 用 `BatteryUtil.getLevel/getIcon`、`Formatters.TIME`、`level<0` → GONE、`onTimeChanged` 调 `updateBatteryInfo()` | 通过 |
| 辅助方法健全 | `methodBody`/`collectAndroidIds`/`findAndroidId`/`hasAncestorAndroidId` 均为既有已实测辅助；`methodBody` 两 token 缺失即断言失败（不能静默放过） | 通过 |
| 防 id 冲突断言 | `assertFalse(ids.contains("time"))` 明确锁定本布局不重声明 `@id/time` | 通过 |
| 实测 | mobile 全量套件中 `mobileVodControlOverlayShowsBatteryIconLikeFusionMode` **通过**（XML time=0.017s，无 failure） | 通过 |

**第 1 轮结论：无必修问题，生产代码零新增改动。**

## 复评第 2 轮（评审复核）

- 净差异重算：`git diff --name-status origin/beta HEAD` 仍为 3 路径，与第 1 轮一致。
- 复查两提交的 recovery tag 与独立守卫会话：`recovery/ui-battery-20261010/20261010071709-8db36f33a629`、`recovery/ui-battery-time-20261010/20261010082101-c442601fbd2d`；`finished-20261010071710-ui-battery-20261010`、`finished-20261010082101-ui-battery-time-20261010`——两提交此前已独立完成提交/打标/记录，本轮为合并评审拟推送，未改写历史。
- 复核未产生任何代码修改，故无需第 3 轮。

**第 2 轮结论：通过，无剩余阻塞项。**

## 验证（终轮）

1. **定向 JVM 测试**：`:app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.VideoActivityLayoutTest` → `BUILD SUCCESSFUL`；XML 实测 **155 项 / 0 失败 / 0 错误 / 0 跳过**，含新增 `mobileVodControlOverlayShowsBatteryIconLikeFusionMode`。
2. **双 flavor Java 编译 + 双 flavor AndroidTest Java 编译**：`:app:compileLeanbackArm64_v8aDebugJavaWithJavac`、`:app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac` → `BUILD SUCCESSFUL`（1m 9s）。
3. **mobile 全量 JVM 单测**（JBR 21.0.10，`--no-daemon`，见 §环境说明）：`:app:testMobileArm64_v8aDebugUnitTest` → `BUILD SUCCESSFUL`（2m 36s）；汇总 **726 suites / 4981 tests / 0 failures / 0 errors / 2 skipped**；`VideoActivityLayoutTest` 155 项 0 失败。
4. **静态与结构校验**：`git diff origin/beta..HEAD --check` 退出码 **0**；冲突标记扫描（`^(<<<<<<<|=======|>>>>>>>)`）命中 **0**；净差异 3 路径无一条命中 601 个远端已删除路径（含可证伪自检）。
5. **零复活 / 零丢失**：见「零复活校验」。

### 环境说明（如实记录）

1. **JDK 选择决定 2 个既有 MPV 测试结果**：本机默认 `JAVA_HOME` = Microsoft JDK 21.0.12.8，在该 JDK 下 `MpvFontConfigTest.writeIfChanged_replacesStaleConfigurationAndCleansTemporaryFile` 与 `MpvHlsCacheCoordinatorTest.successfulCommitPublishesCompleteFileAndReleasesReservation` 失败（`File.renameTo` 覆盖已存在文件在 Windows 上返回 false）。切到项目 provisioned 工具链 **JBR 21.0.10**（`G:/GradleCache/jdks/jetbrains_s_r_o_-21-amd64-windows.2`）并 `./gradlew.bat --stop` 后 `--no-daemon` 重跑，两类连同全量单测全部 0 失败（与 C54 §7.1 记录一致）。生产代码与 beta 逐字节相同、不在本分支改动集内，按 `AGENTS.md` §2 仅记录不修改。
2. 验证后按 addendum 约定执行 `./gradlew.bat --no-daemon clean`（技能流程第 5 步）回收构建资源。

## 改动清单（C57 相对 HEAD）

| 路径 | 类型 | 说明 |
| --- | --- | --- |
| `docs/C57-beta-merge-review-dev4-20261010.md` | 新增 | 本任务交付坐标文档 |

（生产代码 3 路径来自既有的 2 个电池提交，非本轮新增工作；本轮评审未修改任何生产代码/测试。）

## 交付坐标

| 项 | 值 |
| --- | --- |
| 任务起始 HEAD | `c442601fbd2d530829fddfbab371c11955cff1ff` |
| `origin/beta` tip | `e6413efd1e4bdb3c1725824db6f7b3f8a7dc249c`（= HEAD 祖先，beta 无新内容可合） |
| 任务提交 | `docs/C57-beta-merge-review-dev4-20261010.md`（经 task_guard finish 原子提交） |
| recovery tag | `recovery/C57-beta-merge-review-dev4-20261010/<timestamp>-<commit>`（annotated，指向任务提交） |
| 各任务守卫 | `C57-beta-merge-review-dev4-20261010`（standard，scope `app`/`docs`/`scripts`/`.codex`，base HEAD `c442601fbd2`）；首启 scope 过窄（仅 3 代码路径），已放弃重启为完整 scope |
| 推送 | `dev4` → `origin/dev4`（含 2 个电池提交 + 任务文档提交） |
| PR | `dev4 -> beta`，**OPEN、未合并**（只创建，不合并） |
| PR 文件集校验 | 与 `git diff --name-only origin/beta HEAD` 逐项一致 |
| 设备 | 本轮未使用真机（理由见「时间与设备」）；`192.168.50.3:5557` 的既有包未受影响 |

### 闭环记录

- **合并判定**：合并前 `git merge-base --is-ancestor origin/beta HEAD` 为**真**（beta tip `e6413efd1e4` 已在 dev4 祖先链），`git log HEAD..origin/beta` 为空 → **本轮无 beta 新提交可合并**，不产生合并提交；无需 `git merge`，因此也不存在 merge conflict 处理。收尾前 `git fetch origin --prune` 复核 beta 未前进（仍 `e6413efd1e4`）。
- **guard 重启**：首启 scope 仅 3 个代码路径，无法提交任务文档（`docs/` 不在 scope），按既有 `abandoned-*` 惯例放弃首启（工作区仍干净），以完整 scope（`app`/`docs`/`scripts`/`.codex`，与 C54 一致）重启。
- **复评循环**：第 1 轮逐项评审生产代码（可见性规则、BatteryUtil/Formatters 复用、NPE 安全、id 冲突、drawable 资源）与测试（断言真实性、辅助方法健全、防 id 冲突），未发现必修问题 → 第 2 轮复核通过 → 无第 3 轮。
- **验证**：定向单测 → 双 flavor Java/AndroidTest 编译 → mobile 全量单测（JBR 21.0.10）→ 静态校验/零复活 → 全部通过。
- **交付**：任务文档经 task_guard finish 提交并打 recovery tag → 推送 `dev4` → 创建 PR（只创建、未合并）。