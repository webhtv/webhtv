# C111-beta-merge-review-dev3-20261029

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev3`；评审 `dev3` 已提交未推送的 5 个提交（若之前评审已覆盖当前全部改动且无合入冲突可跳过）；
  有必修项就修复并验证，通过后再评审，循环直到通过；然后提交、推送 `dev3`、创建到 `beta` 的中文 PR（描述含具体改动，只创建不合并），
  最后拉取远端最新代码。
- **验收标准**：
  1. `dev3` 相对 `origin/beta` 落后 0（`origin/beta` 成为 HEAD 祖先），合并 0 冲突，`beta` 侧 69 个路径一个不丢，不夹带评审外改动。
  2. 评审覆盖 5 个未推送提交（相对 `origin/dev3`）的 17 个路径；每条结论有可复核的 `路径:行号` 证据。
  3. 每个必修项均已修复并有会变红的回归守卫；修复后复评通过。
  4. 双 flavour 编译通过；单测失败集与 `origin/beta` 基线**逐条同名**（无新增失败）；`git diff --check` 空。
  5. 改动经任务守卫 `finish` 落盘并打 recovery tag；推送 `dev3`；创建到 `beta` 的中文 PR；随后拉取远端最新代码。
- **Lane**：`upstream`（`app` + `docs` + `catvod` + `chaquo` + `scripts`）
- **任务守卫**：`C111-beta-merge-review-dev3-20261029`，base HEAD `382df25e267375d7f3c6e7792430d3d25233d9d7`，branch `dev3`
- **当前状态**：合并、6 轮评审、必修项修复、复评、验证、提交、推送、PR 全部完成；交付坐标见 §8

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 合并目标 | `origin/beta` = `aab55dfcca94904a4ca6b7775b4f57bda343e35c`（"Merge pull request #498 from Silent1566/dev1"） |
| 合并前本地 | `dev3` = `382df25e267375d7f3c6e7792430d3d25233d9d7`（"fix(osd): media badges keep a uniform 16dp gap and re-measure the control bar after layout"） |
| 祖先关系（合并前） | `git merge-base --is-ancestor origin/beta HEAD` → **false** |
| 落后 / 领先（合并前） | `git rev-list --left-right --count origin/beta...HEAD` = **53 / 5** |
| merge-base | `3220d20d6c3d4a9f90b192340dae06d10e512c58`（C106 的 merge+review 提交） |
| 冲突预检 | `git merge-tree --write-tree --name-only HEAD origin/beta` → rc=0，仅返回 tree `f491be7ded02f6fb6b7838c59e11bc938964b382`，**无冲突文件段** |
| 合并动作 | `git merge --no-commit --no-ff origin/beta` → "Automatic merge went well"，暂存 **69 个路径 / +37644 −191**，**0 冲突、0 手工解冲突**；`.git/MERGE_HEAD` = `aab55dfcca…` |
| 合并纯净性（正向） | `git diff --name-only 3220d20d6c origin/beta` 与暂存清单做 `Compare-Object` 的 `<=` 侧为空 → 没有一条是 `beta` 之外的路径 |
| 合并纯净性（反向） | `git diff --cached --name-only origin/beta` = **仅 dev3 自己的 17 个路径**，`beta` 侧 69 个路径一个都没丢 |

`beta` 侧本次带入的 53 个提交（全部为 `beta` 已评审/已合并内容，本轮不重复评审）：`aab55dfcca`、`abff821ba3`、`f9ac1bf37f`、
`c7200138e5`、`93b57d4f23`、`69d87e28c3`、`8e6a4016a9`、`8e9f8a9d3e`、`47d7156c5f`、`169bc59860`、`6bef992767`、`afdb2b92c5`、
`ed85084a89`、`c56ece5f16`、`04c5d5afa7`、`bebac6ed83`、`004b1f5d36`、`fe51f3a246`、`ce2fcc2095`、`36ef7a63bf`、`687ec4af3c`、
`c99ab92823`、`c8e526321c`、`d7d64f4360`、`09245faf83`、`bbb6d68ff2`、`0af0e0afc8`、`ad285c4ad2`、`b1ef0aa03a`、`168a485514`、
`c8dcbbeef0`、`a3b7817b3f`、`6db0ebab9f`、`cc34fb5914`、`31b29f5c6d`、`ebcbeb9330`、`0fc6878533`、`6d0b3eb478`、`472a71ec7a`、
`b04fcc8abc`、`056626b2e6`、`87d09dcd21`、`abe05f2bc6`、`f435ff4c0c`、`539408b81d`、`0363493c98`、`82ff8d4be9`、`a5095df386`、
`13be53307c`、`800945de7c`、`442ee30e9a`、`f0976cdb24`、`fe77e61cc5`。

本篇不是 `beta` 侧的评审记录：合并内容只做保真性核对（上表后三行）。

## 2. 评审范围

需要新评审的是 `dev3` 相对 `origin/dev3` 的 **5 个未推送提交**，净差异 **17 个路径 / +541 −53**：

- `572af99c33` feat(osd): 媒体信息新增「关闭」选项并成为唯一总闸，关闭后保留最近一次角位
- `e8ccde109e` feat(osd): 媒体信息只在全屏显示，位置与屏显开关解耦，无重叠时贴边
- `6feba34d32` fix(osd): 屏显/媒体信息关闭后，全屏唤醒控制栏也不再显示媒体信息
- `c5b0900c93` fix(osd): 媒体信息按真实控制栏高度避让，拆开总开关与常驻开关
- `382df25e26` fix(osd): media badges keep a uniform 16dp gap and re-measure the control bar after layout

交集判定：`beta` 侧 69 个路径与 `dev3` 侧 17 个路径的**交集只有 3 个**——`app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`、
`app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`、`app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`，
三者均为 git 自动合并成功（无冲突段），故本轮**必须重新评审**而非复用上一轮结论。另核实 `PlayerOsdController.java` / `PlayerSetting.java` 在
`git log HEAD..origin/beta` 上为空——`beta` 未触碰这一系列文件，语义层面不存在与本系列的直接碰撞。

## 3. 评审结论与必修项

共 **7 个必修项**，全部已修复；分散在 6 轮评审中（Standards 与 Spec 两条轴各出结论，修完再复评，直到两轴都 PASS）：

### F1 存量用户升级后「常驻」语义静默降级

拆分前只有 `player_osd_media_badges` 一个键（`origin/dev3` 的 `PlayerSetting.java:577` `isDisplayMediaBadges()` 是唯一键，
`PlayerOsdController.java:438` 的 `if (player == null || (!controlsVisible && !PlayerSetting.isDisplayMediaBadges())) return;` 让该键为 `true` 时控制栏隐藏也常驻）。
拆分后该键只剩「总开关」，`player_osd_media_badges_persist` 才是「常驻」，而 `migrateLegacyOsd()`（`PlayerSetting.java:1018`）只迁 `display_*` 与旧 `player_osd_*`，
两个 media-badges 键都不在迁移范围 ⇒ 老用户升级后 `persist` 缺失（`Prefers.getBoolean` 默认 `false`），常驻静默降级为「仅控制栏唤醒」。

修法：`catvod/src/main/java/com/github/catvod/utils/Prefers.java` 新增 `contains(String)`；`app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java` 新增
`migrateMediaBadgePersist()`——`player_osd_media_badges_persist` **缺失时**把旧键 `player_osd_media_badges` 的值抄**一次**，键已存在直接返回，
`isDisplayMediaBadgesPersistent()` 先迁移再读值。幂等，且不会把用户显式关闭改回 `true`。

### F2 文档与实现自相矛盾

`docs/osd-media-badges.md` 多处方圆仍写「2dp 呼吸位」「+ dp(8)」「无参 `controlBarTopInset()`」「同一对键」，与现行 `badgeGap()=dp(16)`、
`Math.max(0, gap - padding)`、`private int controlBarTopInset(View bar, int edgeGap)`、三个键不符；`:11`/`:13` 的 lane、base HEAD 与轮次列表也停在第十轮。
此外 Spec 轴点出 `:104` 把载体写成不存在的 `PlayerSettingsRow`（真实链路是两个 flavor 的 `PlayerOsdDialog` + `adapter_player_osd.xml:30` 的眼睛按钮）、
`:344` 三处行号错（真实为 `activity_tmdb_detail.xml:1292` `@+id/playerControls`、`:1340` `@+id/playerBottom`、`:1546` `@+id/detailControlHost`）。

修法：按现行实现逐条改写，并新增「第十二轮」小节记录本轮合并与修正；`git show HEAD` 标题集合与现文对比 `LOST=0`（30 → 35 个标题，新增 5 条全在本轮小节内）。

### F3 详情页内嵌全屏拿不到真实控制栏高度

`PlayerOsdController.controlBar()` 只查 `root.getParent().findViewById(R.id.control)`，而 `app/src/main/res/layout/activity_tmdb_detail.xml` 里**没有**
`@+id/control`，只有 `:1292` 的 `@+id/playerControls`（电视端容器，其内 `:1340` 是真正占位的 `@+id/playerBottom`）与 `:1546` 的 `@+id/detailControlHost`
（手机端，运行时装入 `app/src/main/res/layout/view_control_vod_tmdb.xml`，其 `:223` 有 `@+id/bottom`）。两者互斥：`TmdbDetailActivity.java:1346`（电视端）把
`detailControlHost` 设 GONE，`:1350`（手机端）把 `playerControls` 设 GONE。于是 `inlineFullscreen=true`（`:10290`/`:10566` 赋值，`:1143` 返回）时
`controlBarTopInset()` 恒落到 `bar == null` 分支的 `dp(80) + edgeGap`，徽标位置与真实控制栏无关。

修法：`controlBar()` 依次尝试 `R.id.control` → `R.id.detailControlHost` → `R.id.playerControls`，用新增的 `firstVisible(View...)` 优先返回**可见**的那一个；
`controlBarTopInset(View bar, int edgeGap)` 的高度测量三级回退 `R.id.bottom`（手机主播放器 / 手机内联）→ `R.id.playerBottom`（电视详情页）→ `bar.getHeight()`，
仍 `<= 0` 或 `>= root.getHeight()` 才回退 `dp(80) + edgeGap`。注：只补 `playerControls` 不够——它是 `match_parent`，量高会撞上「占满 root」判定而回退写死值，
所以 `playerBottom` 这级回退是必需的。

### F4 控制栏布局监听泄漏

`observeControlBar(View bar)` 每找到一根控制栏就 `addOnLayoutChangeListener`，但 `release()` 只调 `stop()`、从不注销（全仓 `removeOnLayoutChangeListener` 0 处）⇒
播放器重建（进出详情页、横竖屏切换）会累积回调，回调还持有已销毁的 root。

修法：新增字段 `private View.OnLayoutChangeListener observedControlBarListener;` 与 `stopObservingControlBar()`（判空 → `removeOnLayoutChangeListener` → 两字段置 `null`），
`observeControlBar()` 先 `stopObservingControlBar()` 再注册，`release()` 里 `stop()` 之后调用它。

### F5 `applyMediaBadgePosition()` 缺判空

OSD 布局尚未 include 或被替换时 `findViewById(R.id.osdMediaBadges)` 为 `null`，原实现第一处 `mediaBadges.getLayoutParams()` 直接 NPE。
修法：方法首行 `if (mediaBadges == null) return;`。

### F6 屏显第 8 位丢失「与总开关的与关系」

合并后 `PlayerSetting.java:643` 的 `getDisplayChecked()` 第 8 项只剩 `isDisplayMediaBadgesPersistent()`；`origin/beta` 该位原本是单一开关
`isDisplayMediaBadges()`，拆两键时被整体替换，属拆分引入的回归。复现：`PlayerSetting.java:633-640` 的 `putMediaBadgeChoice(MEDIA_BADGE_POSITION_OFF)`
只调 `putDisplayMediaBadges(false)` 就 `return`，**不动** `persist` 键 ⇒ 总开关已关、`persist` 仍 `true` ⇒ 第 8 位仍读到 `true` ⇒ 两 flavor 的 `ControlDialog`
（`app/src/leanback/.../ControlDialog.java:272-275`、`app/src/mobile/.../ControlDialog.java:376-379`）照 `displays.get(i).setSelected(i < checked.length && checked[i])`
把芯片显示成「已勾选」，屏显摘要 `getOsdText`（`SettingPlayerActivity.java:258-267` / `SettingPlayerFragment.java:273-282`）仍输出「媒体信息」，
而徽标实际全程不显示（`PlayerOsdController.java:309` 与 `:469-470` 由总开关门控）⇒ UI 与真实显示状态 desync。

修法：第 8 项改为 `isDisplayMediaBadges() && isDisplayMediaBadgesPersistent()`，与同语义另两处齐平——`PlayerSetting.java:1010-1013` 的 `isOsdEnabled()`
（AND 追加在 `:1012`）与 `DisplayDialog.java:77`。写侧 `putDisplayChecked()`（`:654-657`）勾上时 `putDisplayMediaBadges(true)` + 写常驻、取消时只关常驻，
四种 (总开关, 常驻) 组合点击均能翻转；且历史脏态 (总开关=false, 常驻=true) 点一下即被静默归一到 (true, true)，不存在「点亮着但点一下没反应」。

### F7（同属必修）直播端媒体徽标缺全屏门控

`app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java:295-310` 的 OSD `Source` 只覆写 `getPlayer()`/`getTitle()`，`isFullscreen()` 继承默认 `true`
⇒ 手机竖屏内嵌直播窗格（`isEmbeddedLiveUi()`，见 `:1020-1022` 的 `return !ResUtil.isLand(this) && !isRotate() && !isInPictureInPictureMode();`）也会出媒体徽标，
违反「只在全屏显示」。修法：补 `public boolean isFullscreen() { return !isEmbeddedLiveUi() && !isInPictureInPictureMode(); }`（`:306-310`）。
这里的 `!isInPictureInPictureMode()` 不是冗余：横屏 PiP 时 `isLand=true` 使 `isEmbeddedLiveUi=false`，只靠内嵌判定会把 PiP 误判为全屏。
电视端不覆写——`app/src/leanback/res/layout/activity_live.xml:10-12` 的 `@+id/video` 是 `match_parent`，直播恒全屏。

## 4. 建议项的处理

两轴累计给出的建议级意见按主题去重后如下，处置标准是「会不会改变用户可见行为」：

| 建议 | 处置 |
| --- | --- |
| 上两角固定 `dp(64)` 未走实测栏高 | **不改**。这是有意为之：控制栏唤醒时会强制显示上角文本，手机 `view_control_vod.xml` 的 `@+id/top` 是 `?attr/actionBarSize`≈56dp，用栏高会贴角。记入 `docs/osd-media-badges.md` 遗留项 |
| `renderMediaBadgesOnly()` 两块逐字重复 | **不改**。YAGNI，抽方法只增加一层跳转 |
| `badgeGap()` 的 16dp 与 `view_player_osd.xml` 的 `android:padding="14dp"` 隐式耦合 | **改注释**（Javadoc 说明实际肉眼间距 = 16dp − 14dp） |
| 单测全是源码字符串断言 | **留**。这是本仓库既有风格，真正的布局数值仍靠设备实测；已在文档遗留项记录弱点 |
| 分支覆盖缺口（`dp(80)` 四条 return、`persistentNeighbour` 四角、`sanitizeMediaBadgePosition` 越界） | **留**。字符串断言式单测无法覆盖分支，补行为断言需引入 Robolectric，超出本轮范围 |
| `getDisplayChecked()` 第 8 位与 `DisplayDialog` 同标签可能显示相反 | **已由 F6 消除**（两处现在都是 AND） |
| `PlayerSetting.java:654` 的 `valueAt` fallback 仍只取 `persist`，与 `:643` 的 AND 不对称 | **不改**。所有调用点都传长度 8 的数组，该分支不可达 |
| 用旧版本备份恢复时可能把「非常驻」变「常驻」 | **不改代码**。`restorePrefers` 先 `clear` 再写入，`persist` 缺失会被迁移抄成旧键值；这是「升级不降级」的代价，记入文档 |
| `firstVisible` 无 Javadoc、`mobile VideoActivity.java:1619-1623` 多余空行 | **不改**。纯观感，且空行属 `beta` 侧内容 |
| 单测 `:360`/`:362` 建议改按行 `trim` 并排除 `//` 行 | **不改**。属已记录的「字符串断言式」固有弱点 |
| `docs` 「锁住这四点」实为 5 条断言 | **改文档**（改「五点」并点名 `:361-362` 那条最强断言） |

## 5. 变异验证（证明新守卫真的会红）

每条新增/收紧的断言都做过反向验证，避免「假绿」：

| 守卫 | 变异 | 结果 |
| --- | --- | --- |
| `mediaInfoMasterSwitchIsIndependentFromThePersistentToggle`（`PlayerDisplaySettingSyncTest.java:256-263`） | 去掉 `isDisplayMediaBadges() &&` / 调换两者顺序 / 把 `&&` 换成 `\|\|` | 三种全部**变红**；恢复后双口味各 14 例 0 失败 |
| 迁移调用点（`:282-288`） | 删除 `migrateMediaBadgePersist();` 行 / 把该行注释掉 | 两种全部**变红**（断言已从 `contains` 收紧为整行 `trim()` 后严格相等——只 `contains` 时注释掉仍全绿，这是本轮实测发现并修掉的假绿） |
| 迁移顺序（`:290-298`） | 把 `Prefers.put(...)` 挪到 `Prefers.contains(...)` 之前 / 删掉 `contains` 判断行 | 两种全部**变红** |
| 直播端门控（`:351-372`） | 删整个 `isFullscreen()` 覆写 / 退回 `return !isEmbeddedLiveUi();` / 把 `&&` 换成 `\|\|` | 三种全部**变红** |

## 6. 验证证据

| 检查 | 结果 |
| --- | --- |
| 双 flavour 编译 | `./gradlew compileMobileArm64_v8aDebugJavaWithJavac compileLeanbackArm64_v8aDebugJavaWithJavac` → `BUILD SUCCESSFUL`，EXIT=0 |
| 定向单测 | `PlayerDisplaySettingSyncTest` mobile **14** 例 / leanback **14** 例，`failures=0 errors=0`；`ControlDialogOsdSettingsTest` **9** 例 0 失败 |
| 全量双 flavour | `testMobileArm64_v8aDebugUnitTest` **5489** 例 / `testLeanbackArm64_v8aDebugUnitTest` **4708** 例；失败 mobile **6**、leanback **8** |
| 基线对照 | 8 个失败类与 `origin/beta` 基线**逐一同名**（`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`、`ReaderPlaybackRoutingSourceTest`×2、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`，leanback 另加 `NativeEnhancedPlaybackStyleFocusTest`、`SearchResultDownFocusTest`）⇒ **零回归** |
| 空白/冲突 | `git diff --check` 空；`git diff --name-only --diff-filter=U` 空；`git grep -n -E '^(<<<<<<<\|>>>>>>>\|=======)$'` 空 |
| 未做 | 未走 CI；未做设备实测（本轮是评审修正，布局数值未变） |

基线失败清单的逐条出处见 `docs/C106-beta-merge-review-dev3-20261026.md` §5。测试总数较合并首轮 +2（mobile 5487→5489、leanback 4706→4708），正是本轮新增的两个用例（`legacyBadgeUpgradeKeepsPersistentDisplayAndReleasesTheBarListener` 与 `mobileLiveOsdSourceGatesMediaBadgesOnTheEmbeddedShelf`）。

## 7. 遗留与后续项（本轮不做）

- **上两角固定 `dp(64)`**：控制栏可见时上两角 `topMargin` 没走实测栏高，属已知未实测项。手机端暂未观察到压角，电视端栏更高，若后续复现需按同一套 `controlBarTopInset()` 收口。
- **单测仍是字符串断言式**：修泄漏、补判空这类「存在性」改动能覆盖，真正的布局数值仍靠设备实测。
- **直播端门控只做了源码与单测验证**：手机竖屏内嵌窗格不出徽标这条口径没有设备实测。
- **`isOsdEnabled()` 三读两写的语义**已是「总开关 AND 常驻」，但 `DisplayDialog` 与 `ControlDialog` 用同一个 `@string/display_media_badges` 标签表达两个入口，文案层面仍有歧义；本轮只对齐语义，未改文案。

## 8. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `50e68ac9d64198d4787b7407e4a81aa435b43c1e`（"merge: 合并 origin/beta aab55dfc 并修正 OSD 媒体徽标评审问题"），父提交 `382df25e26…` + `aab55dfcca…`，相对第一父 75 个路径 / +37889 −223 |
| 恢复标签 | `recovery/C111-beta-merge-review-dev3-20261029/20261028232901-50e68ac9d641`（本地 + 已推送） |
| 推送 | `git push origin dev3` → `3220d20d6c..50e68ac9d6  dev3 -> dev3` |
| PR | **#501** <https://github.com/Silent1566/webhtv/pull/501>（base `beta` ← head `dev3`，`changedFiles=19`、`additions=763`、`deletions=62`，中文描述含具体改动；**只创建，不合并**） |
| 拉取远端 | `git fetch --all --prune --tags`；收尾时 HEAD = `origin/dev3` = `50e68ac9d6…`，`origin/beta` 仍 `aab55dfcca…`，工作区只剩 3 个任务前既有的未跟踪路径 |
| 本轮修正产物 | `app/src/main/java/com/fongmi/android/tv/setting/PlayerSetting.java`、`app/src/main/java/com/fongmi/android/tv/ui/custom/PlayerOsdController.java`、`app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java`、`app/src/test/java/com/fongmi/android/tv/setting/PlayerDisplaySettingSyncTest.java`、`catvod/src/main/java/com/github/catvod/utils/Prefers.java`、`docs/osd-media-badges.md` |

`gh` 未设默认仓库（`gh repo set-default --view` → "No default remote repository has been set"），而 `origin` 是 `Silent1566/webhtv`、`upstream` 是 `fish2018/webhtv`，
首次 `gh pr create` 因此解析到 `webhtv/webhtv` 报 `No commits between beta and dev3`；所有 `gh` 命令需显式带 `-R Silent1566/webhtv`。
