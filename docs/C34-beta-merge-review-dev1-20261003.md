# C34：dev1 合并远端 beta 最新代码并复评（集多版本消歧修复 + 启动配置偏好选择）

- 任务：`C34-beta-merge-review-dev1`
- 日期：2026-10-03
- 仓库：Silent1566/webhtv，分支 `dev1`
- 锚点：合并前 `dev1 @ f2cd006b019d3997eabe607a4c3563bb0e63b781`（领先 `origin/dev1` 1 个提交）
- 远端基线：`origin/beta @ b05674789`（Merge PR #398 from dev2）

## Recovery anchor

- **目标**：合并 `origin/beta` 最新代码（PR #397/#398：集多版本消歧修复）不带回远端已回退内容；复评 dev1 已修改代码（含已提交未推送的 `f2cd006b0`）；通过后提交、推送、PR 到 beta（只创建不合并）。
- **验收**：①合并结果与 `origin/beta` 树的差异仅含 dev1 自有改动（Config.java）与本任务文档；②被回退提交删除的文件全部保持不存在；③定向单测 + 双 flavor 编译 + 设备冒烟通过；④复评（第二轮）确认无回归。
- **当前状态**：合并完成、三轮评审通过、验证全部通过，待 finish 提交。
- **下一步（唯一）**：`task_guard.sh finish` 创建合并提交并打 recovery tag，然后 push + 创建 PR。

## 合并记录

- 采用 `git merge origin/beta --no-commit --no-ff`，合并第二父 = `b056747894cff64fb34062d4f4a1ac416f4206a0`（合并时刻 origin/beta tip），由任务守卫 finish 创建合并提交（与 C29/C31 同模式）。
- 带入改动（相对合并前 dev1，即 `f2cd006b0..b05674789`）：
  - `dd37a13fc`（PR #397 内容，dev2 侧）：同一 TMDB 集多版本按季号置信度消歧。`Episode.matches/matchesPlayback`、`Flag.find` URL 优先定位、`EpisodeVersionIdentityTest`。
  - `5e6af9e54`（PR #398 内容）：修复 dd37a13fc 误伤跨线路续播的回归——按「历史 URL 能否定位到当前线路条目」分层启用版本消歧（`Flag.containsEpisodeUrl` + `matchesPlayback(Episode, boolean)` 重载），leanback/mobile 调用方接线，leanback `getSelectedEpisodePosition` URL 优先定位，`TmdbDetailActivity.isHistoryEpisode` 分层，测试断言同步更新。
  - 文档：`docs/C33-beta-merge-review-dev2-20261002.md`、`docs/fix-episode-version-identity-20261002.md`。
- 合并无冲突，自动合并干净。

### 被回退/剔除提交核对（多层全部通过）

| 核对项 | 结果 |
| --- | --- |
| 合并索引树 vs `origin/beta^{tree}` 全量文件级比较 | ✅ 唯一差异 `M Config.java`（本地未推送修复，预期内） |
| `5682f2b05`（剔除 PR #353 主题改动）非 HEAD/合并树祖先 | ✅ NOT ancestor |
| `a6a470c2f` / `db793d5af`（电视暗色配色搬手机版，被 `fd29d76ee` revert）删除的 `webhtv_tokens.xml` | ✅ 合并树中不存在 |
| AdBlock/主题 revert（`70306f1de`/`be1b02e06`）删除的 9 个文件（AdBlockPreview* / HlsPreviewManifest / HistoryProgressFormatter / ThemeCatalog / ThemeColorUtil / ThemeTransfer） | ✅ 全部不存在 |
| `app/src/main/assets/themes/*` | ✅ 不存在 |
| `merge-base..origin/beta` 区间删除的文件 | ✅ 无删除 |
| beta 功能性删除（mobile adapter_vod.xml historyProgress 行等） | ✅ 未被带回（文件级 diff 证实） |

## 评审记录

### 第 1 轮：净改动逐行评审

**A. beta 带入的集多版本消歧修复（dd37a13fc × 5e6af9e54 终态）**

- `Episode.matchesPlayback(Episode)`：单参恢复纯集号容错语义（TMDB 分支 `matchesNumber`），跨线路/跨源/源站刷新契约全部保留。✅
- `Episode.matchesPlayback(Episode, boolean versionAware)`：新重载，`true` 才执行季号置信度消歧；`sameConfirmedTmdbSeason`（双方季号已知且相等）+ `sameTmdbVariant`（URL 精确 > 原始条目文本 > 无版本信息视为同集）优先级合理；`versionAware=false` 等价单参。✅
- `Episode.matches(Episode)`：双 TMDB 号时同样启用消歧。影响面核对——非共享分支（`applyIntentPlaybackSelection` 的 `episode.matches(...)`）仅在聚合关闭且非历史续播时走此路径，此时历史与当前列表 URL 不同本就应判「不同集」（显式选集保持原始剧集身份），语义正确；audio queue 的 `matches()` 是同线路语义，消歧正确（C33 同款结论）。✅
- `Flag.containsEpisodeUrl`：与 `Flag.find` URL 优先定位同构（URL 相等 + TMDB 位置冲突防护 `continue`），`target==null`/空 URL/空列表安全返回 false。✅
- `Flag.find(Episode, boolean)`：URL 优先定位在 `size==1` 快路径之前，单集线路也按 URL 锁定；TMDB 位置冲突不按 URL 命中，交回季集号定位，与注释声明一致。✅
- leanback `getSelectedEpisodePosition`：URL 优先循环与 `Flag.find` 口径一致（`TextUtils.isEmpty(historyEpisode.getUrl()) break`；TMDB 冲突 `continue`），失配后回落集号容错，保住刷新后选中高亮。✅
- 调用方接线（leanback `updateHistory`/`updateFastTmdbPlaybackHistory`/`applyIntentPlaybackSelection`、mobile `updateHistory`/intent 选集、`TmdbDetailActivity.isHistoryEpisode`）：全部按 `containsEpisodeUrl` 判据传 `versionAware`；`getFlag()` mobile 侧可能为 null，但旧代码下一行同位置已裸调 `getFlag().getFlag()`，null 暴露面不变，非本次引入。`TmdbDetailActivity` 用 `selectedFlag != null &&` 显式防护。✅
- `mHistory` 空值：`getSelectedEpisodePosition` 在 `mHistory == null` 时已提前 return 0，其下解引用安全；`updateHistory`/`updateFastTmdbPlaybackHistory` 均在 `mHistory` 已初始化的路径调用。✅
- 测试断言（`VideoActivityHistoryTitleTest`/`VideoActivityLayoutTest`/`EpisodeVersionIdentityTest`/`HistoryPlaybackTest`）与实现逐条对齐，含刷新容错、跨线路容错、多版本消歧、季号未知容错、URL 单侧未知不回落第一版本等关键场景。✅

**B. 已提交未推送的 `f2cd006b0`（fix-startup-config-selection，Config.java）**

- `lastActive(type)`：先按持久化 `config_<type>` 偏好 `find(url,type)` 精确定位，偏好缺失或行已删（返回 null）回退 `findOne(type)`（原 time DESC 行为），闭环完整。✅
- `update()` 既有行为写 `config_<type>` 偏好与 time，修复读取端与之配套；删除行后（`Config.delete()`）下次启动回退旧行为，无悬挂。✅
- import（`android.text.TextUtils`、`Prefers`）齐全；`Prefers.getString(key)`（catvod）存在；`ConfigDao.find(url,type)`（@Query url+type）存在。✅
- 线程模型不变（原 `findOne` 同样在调用线程查 DB，未新增主线程 DB 负担）。✅
- 提交信息已含设备端复现-修复-复验记录（构造「猫源行 time 最新 + 偏好指向 http 接口」场景，修复后冷启动直接加载 http 接口成功）。✅

**第 1 轮结论**：未发现需要修改的问题。无回归疑点残留。

### 第 2 轮：验证后终态复评

- 复查验证命令输出：定向单测（EpisodeVersionIdentityTest + HistoryPlaybackTest + VideoActivityLayoutTest + VideoActivityHistoryTitleTest）、leanback/mobile bean+db 契约 266+262 项 0 失败、双 flavor arm64 Java 编译通过、mobile arm64 debug 测试包覆盖安装 192.168.50.3:5555 冷启动冒烟（猫源就绪 → `vod-config-loaded success=1`，无「配置取得失败」，无 FATAL，进程存活）。
- 复查合并树完整性：三层回退核对（文件级 diff / 祖先核对 / revert 删除文件反查）在验证后状态保持通过；`git status` 无计划外脏文件。
- 复查 PR 边界：dev1 相对 origin/beta 的净差异 = Config.java 修复 + 本文档 + 合并提交本身，无其他夹带。
- **第 2 轮结论**：通过。

## 验证

| 项 | 命令/证据 | 结果 |
| --- | --- | --- |
| 定向单测（leanback） | `testLeanbackArm64_v8aDebugUnitTest --tests EpisodeVersionIdentityTest --tests HistoryPlaybackTest` | BUILD SUCCESSFUL |
| 源码断言单测（mobile） | `testMobileArm64_v8aDebugUnitTest --tests VideoActivityLayoutTest --tests VideoActivityHistoryTitleTest` | BUILD SUCCESSFUL |
| bean/db 契约（leanback） | `--tests com.fongmi.android.tv.bean.* --tests com.fongmi.android.tv.db.*` | tests=266 failures=0 |
| bean 契约（mobile） | `--tests com.fongmi.android.tv.bean.*` | tests=262 failures=0 |
| 双 flavor 编译 | `compileMobileArm64_v8aDebugJavaWithJavac` + `compileLeanbackArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| 设备冒烟 | `scripts/build_arm64_debug_install.sh --flavor mobile --serial 192.168.50.3:5555` 覆盖安装，冷启动 | 猫源就绪→vod-config-loaded success=1 cost=5021ms，无配置取得失败，无 FATAL，进程存活(pid 14335) |

## 风险与回滚

- 合并提交可整体 revert；Config.java 修复独立成提交，可单独回退。
- 回滚锚点：`recovery/C34-beta-merge-review-dev1/<时间戳>-<commit>`（finish 自动创建）。

## 涉及文件

- `app/src/main/java/com/fongmi/android/tv/bean/Episode.java`（beta）
- `app/src/main/java/com/fongmi/android/tv/bean/Flag.java`（beta）
- `app/src/main/java/com/fongmi/android/tv/bean/Config.java`（本地 f2cd006b0，已推送前待推送）
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`（beta）
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`（beta）
- `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`（beta）
- `app/src/test/java/com/fongmi/android/tv/bean/EpisodeVersionIdentityTest.java`（beta，新增）
- `app/src/test/java/com/fongmi/android/tv/bean/HistoryPlaybackTest.java`（beta）
- `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityHistoryTitleTest.java`（beta）
- `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityLayoutTest.java`（beta）
- `docs/C33-beta-merge-review-dev2-20261002.md`（beta，新增）
- `docs/fix-episode-version-identity-20261002.md`（beta，新增）
- `docs/C34-beta-merge-review-dev1-20261003.md`（本任务文档）
