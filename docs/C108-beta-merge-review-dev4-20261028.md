# C108-beta-merge-review-dev4-20261028

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev4`；评审 `dev4` 已提交未推送的 6 个提交（含 PR #489 之后的
  `fe51f3a246a`，此提交未被 C105 评审覆盖）；发现问题即修复并验证；复评直至通过；然后提交、推送 `dev4`、
  创建到 `beta` 的中文 PR（含具体改动内容），最后拉取远端最新代码。
- **验收标准**：
  1. `dev4` 合并后 `origin/beta` 成为 HEAD 祖先（落后 0），冲突全部解决且不夹带评审外改动。
  2. 评审覆盖 6 个未推送提交（相对 `origin/dev4`）的 8 个路径；每条结论有可复核证据。
  3. 发现的必修项均已修复并有回归断言；修复后复评通过。
  4. 双 flavour 编译通过；单测失败集与既有基线**逐条一致**（无新增失败）；`git diff --check` 空。
  5. 改动提交并打 recovery tag；推送 `dev4`；创建到 `beta` 的中文 PR；随后拉取远端最新代码。
- **Lane**：`upstream`（`app` leanback/mobile + `docs`）
- **任务守卫**：**未执行**。本机 `bash` 通道不可用（`bash -c "echo hello"` →
  `Bash/Service/CreateInstance/CreateVm/HCS/HCS_E_SERVICE_NOT_AVAILABLE`，退出码为空），
  `.codex/scripts/task_guard.sh` 无法启动。改用等效手工流程：只暂存任务自有路径 + 手工打 recovery tag。
  该限制与 `docs/C105-beta-merge-review-dev4-20261025.md` 记录一致，非本轮引入。
- **当前状态**：合并、评审、必修项修复、复评、验证完成；交付坐标见 §8

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 合并目标 | `origin/beta` = `004b1f5d363ed32a48a040dd4497311d50179c23`（"Merge pull request #494 from Silent1566/dev1"） |
| 合并前本地 | `dev4` = `fe51f3a246a`（"fix(tv-play): 修正进场焦点并修复切线路后集数网格左右键失效"） |
| 祖先关系（合并前） | `git merge-base --is-ancestor origin/beta HEAD` → **false** |
| 落后 / 领先（合并前） | `git rev-list --left-right --count origin/beta...HEAD` = **42 / 6** |
| merge-base | `4058fb7cf8ae1f1fad700bdb06891646a64e6d01`（"docs(C105): 回填交付坐标与 PR #489"） |
| 冲突预检 | `git merge-tree --write-tree HEAD origin/beta` → 仅 2 个冲突文件 |
| 合并动作 | `git merge --no-commit --no-ff origin/beta` → 暂存 **91 个路径 / +37823 −286**，2 处冲突手工解决 |
| `.git/MERGE_HEAD` | `004b1f5d363ed32a48a040dd4497311d50179c23`，与 `origin/beta` 一致 |
| 合并纯净性（正向） | 暂存 91 条中相对 `4058fb7cf8a..origin/beta` 的 beta 侧路径集差集为 **0**，无夹带 |
| 合并纯净性（反向） | `git diff --cached --stat origin/beta` = 仅 `dev4` 自己的 8 个路径 / **+1030 −20**，beta 侧一个都没丢 |

`beta` 本次带入 42 个提交（32 个非 merge），含 C101 性能系列（`f0976cdb249` 延后 PlaybackService 绑定、
`0363493c980` 削减首帧主线程、`442ee30e9ac` TMDB 详情分帧应用、`f435ff4c0ca` baseline profile）、
`168a485514c`（线路行选中回声不再自激）、`056626b2e65`（chaquo 加类锁单次启动 CPython）、
`9996d787ec0`（TMDB 请求语言跟随界面）、`36ef7a63bf2`（搜索栏焦点环合并子控件状态）等。
这些均为 `beta` 已评审/已合并内容，本轮不重复评审。

## 2. 冲突解决

冲突仅两处，且是两侧对**同一缺陷**的独立修复：

| 文件 | 我方（`fe51f3a246a`）做法 | beta（`168a485514c`）做法 | 采用 |
| --- | --- | --- | --- |
| `app/src/leanback/java/com/fongmi/android/tv/ui/custom/FlagSelectionListener.java` | 字段 `deliveredItem`；`run()` 里 `item == deliveredItem` 直接 return | 字段 `pendingItem` + `dispatchedItem`；`onChildViewHolderSelected` 中**只有选到另一条线路**才清回声窗口，行刷新途中的短暂 `NO_POSITION` 不清窗口 | **beta** |
| `app/src/testLeanback/java/com/fongmi/android/tv/ui/custom/FlagSelectionListenerTest.java` | 2 条用例（忽略同线路重复派发、换线路仍能切换） | 4 条用例（额外覆盖 `NO_POSITION` 不清窗口、同名不同实例按身份区分、真实 A→B→C→B→A 不被吞、同帧 A→B→A 末态补派发） | **beta** |

判定理由：beta 版是语义**超集** —— 我方版本在「行刷新途中派发 `NO_POSITION`」场景会重新接上回声自激，
beta 版正确处理并把它写进了注释与回归用例。测试集 beta 也为超集（12 例 > 我方 10 例）。
两文件以 `git checkout --theirs` 取 beta 版，`git diff --name-only --diff-filter=U` 为空，无残留冲突标记。

`VideoActivity.java` 自动合并，beta 侧 C101 改动全部保留（已核实：`:2461`
`return TmdbLanguagePolicy.requestLanguage(currentTmdbConfig(), Setting.getLanguage());`、
`:6398` `protected boolean deferPlaybackServiceBinding() { return true; }`）。
我方 6 个提交的符号全部在场且无重复定义：`isEffectivelyVisible`×2、`horizontalFocusTarget`×1、
`moveArraySelection`×2、`requestEpisodeGridFocus`×2、`isSegmentDisplayed`×3、
`requestInitialPlaybackFocus`×8、`updateVideoCover`×10、`isManualPlaybackMode`×8、
`mExplicitPlaybackRequest`×6、`shouldAutoPlay`×3；
`shouldAutoPlay`/`isManualPlaybackMode`/`updateVideoCover`/`deferPlaybackServiceBinding` 各仅 1 处定义。

## 3. 评审范围

本轮新评审的是 `dev4` 相对 `origin/dev4` 的 **6 个未推送提交**，净差异 **8 个路径 / +1030 −20**：

- `abe05f2bc66` 关闭点播自动播放后不再抢占预览窗口焦点并显示封面
- `b04fcc8abc3` 关闭点播自动播放后不抢预览框焦点、显示封面、选集直接全屏起播
- `cc34fb59143` 修复切线路后集数分段按钮左右键失效并加固焦点链
- `c8dcbbeef01` 选集网格内左右键改为显式接管（新增 `TmdbEpisodeGridPolicy.horizontalFocusTarget`）
- `0af0e0afc87` 撤销常驻焦点看门狗，焦点不再在播放器与集数按钮间来回跳
- `fe51f3a246a` 修正进场焦点并修复切线路后集数网格左右键失效

为什么 `fe51f3a246a` 必须新评审：`git merge-base --is-ancestor fe51f3a246a 236a8fc02f5` 返回 1
（非祖先）→ 它**不在** PR #489 里；C105 文档末尾的下一动作是「无。等待 PR #489 评审/合并」，
说明 C105 的评审覆盖不含此提交。前 5 个提交已被 C105 评审，本轮做增量复核。

## 4. 评审结论与必修项

### 4.1 必修项（已修）

**F1 · 注释/文档与代码语义相反（用户可见行为描述错误）**

`isManualPlaybackMode()` 的 javadoc 与 `resetDetailForNewIntent` 处的行内注释仍写着
「进入页面不再把焦点交给预览窗口／手动播放模式下焦点属于选集，不能落回预览框」，
而 `requestInitialPlaybackFocus()` 的实际实现是单行 `mBinding.video.requestFocus();`
（回归修复 4 之后的既有事实），`showInitialPreview` 也已统一收敛到它。
`0af0e0afc87` 撤销看门狗时没有同步这两处注释，后续读代码的人会据注释推断出错误的焦点契约。

修复：

- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`：javadoc 改为
  「预览位改显示影片封面，点选集直接全屏起播。初始焦点仍留在预览窗口（见 `{@link #requestInitialPlaybackFocus()}`）。」
- 同文件 `resetDetailForNewIntent` 处注释改为「换条目后初始焦点照旧交给预览框：
  手动模式下在预览框上按确认键即完成「全屏 + 起播」。」
- `docs/TV-MANUAL-PLAY-20261026.md`：目标段改写为与实现一致的描述；补丁清单第 5/7/8 条标注
  「已在回归修复 3 收窄／看门狗已删除／统一收敛到 `requestInitialPlaybackFocus()`」；
  §2「初始焦点分流」加引用块说明已被回归修复 4 撤销。

**F2 · 冗余分支（同一语义的两次分叉）**

`resetDetailForNewIntent` 里 `if (isManualPlaybackMode()) requestInitialPlaybackFocus(); else mBinding.video.requestFocus();`
两个分支现在是同一效果（`requestInitialPlaybackFocus()` 体就是 `mBinding.video.requestFocus();`），
属于回归修复 4 收敛时漏掉的死分支，会让「焦点策略只有一个出口」的约定重新分叉。

修复：合并为单行 `requestInitialPlaybackFocus();`（随后 `updateVideoCover();`）。
`VideoActivityLayoutTest` 的 `:3698` `assertFalse(preview.contains("if (isManualPlaybackMode()) requestInitialPlaybackFocus();"))`
只针对 `showInitialPreview` 方法体，不受影响；`requestInitialPlaybackFocus()` 全文件仍 8 处调用，未减少。

### 4.2 复核通过、无需改动

| 复核点 | 结论 |
| --- | --- |
| `TmdbEpisodeGridPolicy.horizontalFocusTarget` 边界（position<0／span<=0／itemCount<=0／行首向左／行尾向右／末行短行） | 纯函数，行内约束 `target ∈ [rowStart, rowEnd]`，全部返回 `NO_FOCUS_TARGET`；由 `horizontalFocusTargetStaysInsideTheCurrentRow` / `horizontalFocusTargetUsesTheShortLastRowBoundary` 锁定 |
| `onArrayKey`（`VideoActivity.java:4474-4488`）LEFT/RIGHT 显式接管、DOWN 只装分段不抢焦点 | 与 `focusEpisodeHeaderTool` / `scrollToEpisode` 的声明式 `nextFocusDown` 目标自洽，未破坏「往下到不了选季按钮」的既有修复 |
| `selectEpisodeSegmentPosition` 守卫（`focused >= 0 && focused != position && isFocusInside(mBinding.array)` → return） | 焦点位优先，打断「布局→`onSegmentFocus`→重建→再布局」自激；与 `showEpisodeSegment` 的 `isSegmentDisplayed` 短路互补 |
| `shouldAutoPlay()` 一次性消费 `mExplicitPlaybackRequest` | 只对用户显式选集起效，不改变自动播放默认行为；`PlayerPlaybackRegressionSourceTest` 覆盖 |
| `activity_video.xml` 新增 `videoCover`（`clickable=false`/`focusable=false`/`importantForAccessibility=no`/`visibility=gone`，位于 exo 与 audioStage 之间） | 不参与焦点与无障碍树；`VideoActivityLayoutTest` 断言层级顺序 |
| `FlagSelectionListener` 采用 beta 版 | 见 §2，语义与用例均为超集 |
| `PlaybackActivity.player()` 可返回 null 而 `player().` 直接调用 | 该文件已有 173 处同类调用（`:1484`、`:1994` 等），属既有约定，非本轮引入 |

## 5. 验证证据

| 项 | 结果 |
| --- | --- |
| 双 flavour 编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` → **成功** |
| leanback 打包 | `:app:assembleLeanbackArm64_v8aDebug`（含 `:app:packageLeanbackArm64_v8aDebug`）→ **成功** |
| `testMobileArm64_v8aDebugUnitTest` | **5480 用例 / 2 失败 / 3 跳过** |
| `testLeanbackArm64_v8aDebugUnitTest` | **4697 用例 / 2 失败 / 3 跳过** |
| 失败集对照 | 两个 flavour 的失败**同为**下面两条，与 C106/C49 基线记录**逐条一致** → **零回归** |
| 定向回归 `VideoActivityLayoutTest` | **157 例 / 0 失败** |
| 定向回归 `FlagSelectionListenerTest`（beta 版用例集） | **12 例 / 0 失败** |
| 定向回归 `TmdbEpisodeGridPolicyTest` | **9 例 / 0 失败** |
| 定向回归 `PlayerPlaybackRegressionSourceTest` | **15 例 / 0 失败** |
| 定向回归 `PlayerControlFocusIntegrationTest` | **11 例 / 0 失败** |
| 空白与冲突 | `git diff --check` / `git diff --cached --check` 空；无冲突标记 |

**基线失败（与本轮改动无关，均为 `origin/beta` 上已存在的沙箱环境失败）**：

- `androidx.media3.mpvplayer.MpvFontConfigTest.writeIfChanged_replacesStaleConfigurationAndCleansTemporaryFile`
  → `java.io.IOException` at `MpvFontConfigTest.java:53`（沙箱无法提交 fontconfig 文件）
- `androidx.media3.mpvplayer.MpvHlsCacheCoordinatorTest.successfulCommitPublishesCompleteFileAndReleasesReservation`
  → `java.lang.AssertionError` at `MpvHlsCacheCoordinatorTest.java:208`

**零回归三重证据**：

1. 两个测试文件在基线 `4058fb7cf8a` 已存在；
2. `git diff --name-only 4058fb7cf8a origin/beta` 与 `4058fb7cf8a HEAD` 对
   `app/src/test/java/androidx/media3/mpvplayer/` 均为空（本任务与 beta 都没碰过）；
3. `docs/C106-beta-merge-review-dev3-20261026.md:126-127`、`docs/C49-beta-merge-review-dev4-20261008.md:195-196`
   已有同名记录，且 C106 给出的验收方法正是「以失败用例名与干净 `origin/beta` worktree 逐条对照，
   名称一致即零回归」。

## 6. 复评

修复后重跑评审：F1（注释/文档与实现相反）、F2（同语义死分支）均已闭环；
改动仅 2 个路径（`VideoActivity.java` 3 处、`docs/TV-MANUAL-PLAY-20261026.md` 4 处），
不触及任何运行时分支逻辑，定向 5 个测试类全绿、双 flavour 编译与 leanback 打包成功。
**复评通过**。

## 7. 遗留与后续项（本轮不做）

- `docs/TV-MANUAL-PLAY-20261026.md` 记录了 5 轮迭代（初始方案 + 4 次回归修复），
  历史段落都以「已被后续修复撤销」的引用块保留；后续若要再改焦点契约，应先把它压缩成单一现状描述。
- `FlagSelectionListener` 的回声守卫依赖「`onChildViewHolderSelected` 里选到另一条线路才清窗口」这一
  Leanback 行为假设；若将来把线路行换成非 `HorizontalGridView` 宿主，需要重新验证该假设。
- 本机 `bash` 通道不可用导致 `.codex/scripts/task_guard.sh` 无法执行，recovery tag 由手工 `git tag -a` 补足；
  恢复 bash 后应复核守卫状态文件是否被跳过。

## 8. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `04c5d5afa79a342c69d8d4687914acac0b8130a3`（`dev4`，父提交 `fe51f3a246a` + `004b1f5d363`） |
| Recovery tag | `recovery/C108-BETA-MERGE-DEV4-20261028/20261028115633-04c5d5afa79`（annotated，指向合并提交） |
| 分支 | `dev4`，已推送 `4058fb7cf8a..c56ece5f167` |
| PR | **#496** `merge(C108): dev4 合并 beta 004b1f5d363，评审 6 个未推送提交并修正焦点契约注释` → base `beta`（只创建，未合并） |
| 回滚 | `git reset --hard fe51f3a246a`（回到合并前），或 `git revert -m 1 04c5d5afa79`（保留历史） |
| 验证坐标 | 合并提交内的 `app/` 与 `docs/TV-MANUAL-PLAY-20261026.md` 即 §5 验证过的状态；本 §8 为文档回填，不含代码改动 |
