# C105 · beta 合并评审（dev4，2026-10-25）

## 1. 任务与边界

- **目标**：把 `origin/beta@ee285f8e85` 合并进 `dev4`（零冲突），评审 `dev4` 已提交未推送的 `772a25a4548` 与合并后代码，修复必修项并定向验证，复评通过后提交、推送 `dev4`、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **分支**：`dev4`，合并前 HEAD `772a25a4548`（领先 `origin/dev4` 1 个提交）。
- **受保护脏路径**：0（开工时 `git status --porcelain` 为空）。
- **允许路径**：`app/src`、`docs`。
- **时区与时间**：Asia/Shanghai，2026-10-25 11:01 开工。
- **任务守卫**：`C105-BETA-MERGE-DEV4-20261025`。

## 2. 合并坐标

| 项 | 值 |
| --- | --- |
| 合并前 `dev4` | `772a25a45486f72a6ff107eb1af5f9f2fbfd9c2d` |
| 合并前 `origin/dev4` | `306b823dc7b7340e13e999c09679eaccab0ee152` |
| `origin/beta` | `ee285f8e85c153caa7b5b0d758a9279a9d6ce88d`（PR #487 合并） |
| merge-base | `306b823dc7b7340e13e999c09679eaccab0ee152` |
| 落后 / 领先 | `origin/beta...dev4` = 13 / 1 |
| 合并冲突 | **0**（`git merge-tree --write-tree --name-only` rc=0；`git merge --no-ff` 自动合并，`git diff --diff-filter=U` 为空） |

beta 侧 13 个提交：`ee285f8e85`（PR #487 合并）、`02fc09ede4`（C104 文档）、`3cfe93f389`（C104 合并+评审）、`11be83da8b`（徽标行高对齐）、`8322438787`（PR #488 合并）、`192b55c2fc`（徽标常驻）、`39e1b4a0d4`（C103 文档）、`1322505938`（C103 合并+评审）、`64ee84e68e`（完整徽标目录）、`0dc165a9a2`（单行分类底板）、`7d4bee6ce1`（随控制栏显示）、`49e5b2c4bc`（媒体信息胶囊）、`4a55998f35`（可推断媒体信息徽标）。

## 3. 跳过 beta 侧代码的依据（逐条留证）

用户规则允许跳过「已评审且无合入冲突」的内容。本轮证据：

1. **路径交集为空**：`git diff --name-only <merge-base> origin/beta` = 154 条；`git diff --name-only <merge-base> 772a25a4548` = 6 条；两者 `comm -12` 结果**为空**。因此不存在「两边都改过同一文件」的自动合并交汇点需要人工复核。
2. **非交集路径 blob 逐字节一致**：154 条中不属于交集的全部路径，`git rev-parse :<path>` 与 `git rev-parse origin/beta:<path>` 逐一比对，**0 处不一致**，即合并没有改写任何 beta 侧文件。
3. **已评审覆盖**：beta 侧内容分别由 `docs/C103-beta-merge-review-dev4-20261024.md`、`docs/C103-beta-merge-review-dev1-20261024.md`、`docs/C104-beta-merge-review-dev1-osd-20261025.md` 在同期、对同一批提交完成评审。

`dev4..origin/beta` 的净差异里出现 `leanback/mobile VideoActivity.java`、`TmdbDetailActivity.java` 等文件，只是因为 **dev4 单方面改过它们**；beta 侧自 merge-base 起从未改动这 5 个文件（`git log <merge-base>..origin/beta -- <path>` 全部为空）。

## 4. 评审对象：`772a25a4548`（6 路径，+201 / -8）

修复缺陷：两条**同名**线路（都显示「1」）时，开启「切换线路不自动播放」后点击第 2 条线路的该集，只进入全屏、仍在播第 1 条线路。

根因：线路身份退化为「线路名字符串」或「列表选中标记」，共 4 处：
- R1 `onItemClick(Flag)`：`if (resolved.isSelected() && !initialBinding) return;`
- R2 `shouldEnterFullscreen(Episode)`：`item.isSelected()`
- R3 `seamless(Flag)`：`episode.isSelected()`
- R4 `TmdbDetailActivity.isCurrentInlinePlayback`：`TextUtils.equals(selectedFlag.getFlag(), inlinePlaybackFlag)`

协议约束：`SiteApi.playerContent` 只接受线路**名字**字符串，因此修复只能落在 UI 身份层。

修复方式：新增 `mPlaybackFlagKey`，用 `Flag.stableKey`（`线路名#索引`）标识「实际已起播的线路」，写入点必须在 `beginPlayerContentRequest(...)` 之后（该调用内部 `invalidatePlayerContent()` 会清空该键）；`TmdbDetailActivity` 对称新增 `inlinePlaybackFlagKey`。

### 评审结论：无必修项

逐条核对：
- **稳定键确实能区分同名线路**：`Flag.equals` 只比较 `getFlag()`（`Flag.java:314-318`），故同名线路 `indexOf` 会串台；`Flag.stableKey`（`Flag.java:76-79`）拼接索引，`TmdbUIAdapter.flagKey` 直接委托它（`TmdbUIAdapter.java:196-198`），可区分。
- **索引解析是实例同一性优先**：`TmdbUIAdapter.flagIndex`（`TmdbUIAdapter.java:215-219`）先 `==` 再 `indexOf`；`FlagAdapter.indexOf`（`app/src/mobile/java/com/fongmi/android/tv/ui/adapter/FlagAdapter.java:60-64`）同样先 `==` 再 `indexOf`。`mFlagAdapter` 由 `mFlagAdapter.addAll(item.getFlags())`（mobile:2548 / leanback:2776）填充，与 `mVod.getFlags()` 共享同一批 `Flag` 实例，故同一性分支可靠命中，不会退化到按名字匹配。
- **写入点在清空之后**：mobile `getPlayer` 中 `beginPlayerContentRequest`（:2799）先执行，`mPlaybackFlagKey = playbackFlagKey(flag)`（:2800）后写入；leanback 同序（:3146/:3147 与快速 TMDB 路径 :2315/:2316）。`invalidatePlayerContent()`（mobile:3093 / leanback:3558）确实清空该键。
- **其它起播路径也已覆盖**：`mViewModel.playerContent(` 全仓仅 3 处调用（mobile:2801；leanback:2317、:3148），三处均写入键；沉浸音频 `prepareImmersiveAudioPlayback` / `applyImmersiveAudioSelection` 亦写入。
- **行为收窄方向正确**：`shouldEnterFullscreen` 由「该集被标记选中」收窄为「该集被选中 **且** 该线路确实是当前已起播线路」，同名线路误判为「已在播」→ 只进全屏的旧行为被消除。
- **详情页对称且自洽**：`selectedSeasonFlagKey()`（`TmdbDetailActivity.java:3609-3613`）与写入点用同一表达式；`selectedFlag == null` 时两侧同为 `""`，不会误判；`vod` 被富集替换后若线路表重排，索引变化正是本次要修正的歧义。

**未做（明确非目标）**：未改 `Flag.equals/hashCode`、未改 `isCurrentPlayerContentContext` 的名字比较、未改 `updateHistory` / `VodBrowse` / `History.findFlag` 的退化逻辑、未改 `SiteApi.playerContent(flag,...)`、未重命名 `shouldEnterFullscreen` / `beginPlayerContentRequest`。

## 5. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 编译（双 flavor） | `:app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL 9m37s，EXIT=0** |
| 单测 mobile | `FlagSwitchPlaybackWiringTest` / `VideoActivityLayoutTest` / `TmdbDetailActivityLayoutTest` / `TvFixedLightSurfaceContrastTest` | 8/0/0、155/0/0、130/0/0、4/0/0 |
| 单测 leanback | `FlagSwitchPlaybackWiringTest` | 8/0/0 |
| 合计 | — | **305 例，0 失败，0 错误，0 跳过**（BUILD SUCCESSFUL 3m53s，EXIT=0） |

环境：`ANDROID_HOME=C:\Users\Maple\AppData\Local\Android\Sdk`、`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`、`GRADLE_USER_HOME=G:\GradleCache`。

## 6. 环境约束（复现须知）

本机 DSH 的 bash 通道**无法写入 `.git` 与 `G:\GradleCache`**（`FETCH_HEAD: Permission denied`、`gradle-9.5.1-bin.zip.lck: 拒绝访问`），非 ACL 问题（`.git` ACL 为 Authenticated Users Modify 继承）。所有 git 变更与 gradlew 均须经宿主 PowerShell 执行；只读 git 查询可在 bash 内完成。

## 7. 复评

首轮即通过，无必修项、无改动，因此无需「修复→再评审」循环。合并纯净性（0 冲突 + 0 blob 改写）与 `772a25a4548` 的正确性均已留证。

## 8. 状态

- 合并、评审、验证、复评、提交、推送、PR、拉取：**全部完成**，工作树干净。

### 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `db0b4483b35` `merge: 合并 origin/beta ee285f8e85 并评审 dev4 未推送修复` |
| 恢复标签 | `recovery/C105-BETA-MERGE-DEV4-20261025/20261025035025-db0b4483b35a` |
| 推送 | `origin/dev4` `306b823dc7b..db0b4483b35` |
| PR | [#489 dev4 → beta](https://github.com/Silent1566/webhtv/pull/489)（仅创建，未合并） |
| 拉取 | `git fetch origin --prune` + `git pull --ff-only origin dev4` = Already up to date |

### 下一动作

无。等待 PR #489 评审/合并（本轮只创建 PR，不合并）。
