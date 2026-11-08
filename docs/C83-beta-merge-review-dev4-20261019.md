# C83-beta-merge-review-dev4-20261019

## Recovery anchor

- Objective: 在 dev4 上合并 `origin/beta` 最新代码（远端已回退/删除的提交不得被顺带带回），复评本次合并带入的改动与本地 4 个已提交未推送的 TV 焦点环提交，循环评审-修复-验证直到通过，然后提交、推送并创建到 `beta` 的中文 PR（只创建、不合并）。
- Lane: `standard`。Scope: `app`、`docs`。
- Baseline: branch `dev4`，HEAD `1b35b6e3e80554e79654455a3d8a986bb1236b66`。
- Protected dirty paths: `.design-evidence/**`（任务前即存在的未跟踪证据目录，未纳入 scope，未提交；仅写入本地 `.git/info/exclude` 使守卫可用，未改动任何被跟踪文件）。
- Status: 合并完成、复评 2 轮并修正 1 项 blocker + 2 处文档/注释失真，验证全部通过，待提交。
- Next action: 无。

## 1. 合并对象

| 项 | 值 |
| --- | --- |
| 合并前 dev4 HEAD | `1b35b6e3e80554e79654455a3d8a986bb1236b66` |
| `origin/beta` tip | `0bb2601e3871f58b727873837112abe86784d61f`（Merge PR #461 from dev3） |
| 合并基 | `cef7ba60432b4c0a9415f4a31f77ad8df6fc8474`（Merge PR #459 from dev4） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta` → `Automatic merge went well`，**0 冲突** |
| 合并树（预测） | `git merge-tree --write-tree dev4 origin/beta` → `ec3493c1bb3910715fbc3f380461c70684ff4f71`，exit 0 |

beta 自合并基起 15 个提交，触及 **15 条路径**（Java 6 / Test 5 / Docs 4）；dev4 自合并基起触及 **46 条路径**。两侧路径集合**交集为空**，故 0 冲突是预期结果而非巧合。

- beta 侧 15 条：`app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java`、`.../player/exo/PreCache.java`、`.../ui/dialog/{PlaybackRemoteSyncDialog,PlaybackWebhookDialog,SiteHealthReportDialog,ViewingRecordSyncDialog}.java`、`app/src/test/java/com/fongmi/android/tv/db/dao/HistoryDaoTest.java`、`.../player/PlayerManagerSwitchCauseSourceTest.java`、`.../player/exo/PreCachePolicyTest.java`、`.../theme/DialogForegroundTokenTest.java`、`.../ui/dialog/SiteHealthReportDialogSourceTest.java`、`docs/{C82-beta-merge-review-dev2-20261019,MPV-BUFFERING-STALL-WATCHDOG-20261017,upstream-player-dependency-merge-assessment-2026-08-20,viewing-record-sync-dark-theme-20261019}.md`。
- dev4 侧 46 条 = 焦点环/颜色/布局改动 + `docs/TV-FOCUS-RING-DIALOG-20261019.md` + `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvFocusRingContractTest.java`。

## 2. 零复活证据（可证伪，含阳性对照）

远端已回退/删除的提交不得被合并顺带带回。方法沿用 C80/C82，三层 + 两个阳性对照。

### L1 整树完整性（强判据）

```
git ls-tree -r --name-only origin/beta | sort > beta_tree
git ls-files | sort > idx_tree
comm -23 beta_tree idx_tree | wc -l   →  0
```

`beta files=5151  index files=5153  missing=0` —— 合并结果未丢失 beta 的任何一个路径。

### L2 行级交集（逐路径，决定性）

revert 提交集合按**主题行首锚定**重建（`^(revert|Revert|REVERT|撤销|回滚)`）→ **27 个提交**；对净差异的 49 条路径（46 条已跟踪 + 3 条新增）逐路径取「所有 revert 提交在该路径删除的行」∩「该路径净新增的行」：

| 路径 | revert 删除行 | 净新增行 | 命中 |
| --- | --- | --- | --- |
| `app/src/leanback/res/layout/dialog_ad_block_stats.xml` | 15 | 3 | **0** |
| `app/src/mobile/res/layout/dialog_ad_block_stats.xml` | 15 | 3 | **0** |
| `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvFocusRingContractTest.java` | 40 | 359 | 5 |
| 其余 46 条路径 | — | — | **0** |

唯一命中的 5 行全部是通用样板：`*`、`*/`、`/**`、`@Test`、`}` —— **无一条是被回退的业务代码**。

唯一触及这两个 layout 的 revert 是 `70306f1decf8e924020a9f93e189e9df4ecd8794 revert: remove ad segment verification`，它删除的是「切片键 / 切片值」等验证列（`MaterialTextView` 列，共 15 行）；而 dev4 在该文件只新增了底部 `cancel` 按钮的 `app:strokeWidth`/`app:strokeColor` 两行，位置与内容均不相交。

### 阳性对照（证明检测器非空转）

1. **同路径对照**：把「revert 在该路径删除的行」改为与**该文件当前全文**取交集，两个 layout 立刻各命中 **3** 行（`android:layout_height="match_parent"`、`android:layout_width="180dp"`、`<com.google.android.material.textview.MaterialTextView`）—— 说明扫描确实读到了该路径的 revert 删除行，只是它们不在净新增集里。
2. **注入对照**：把 revert 删除行 `android:text="切片键"` 人工注入净新增集，交集计数立刻由 0 变 **1**（全库口径由 21 变 22）—— 说明交集运算本身有效。

### 已知检测边界

主题行锚定 revert/回退/撤销 不完备（纯描述式撤换标题会漏）；完备性由 L1 整树承担。

## 3. 复评发现与修复

### 3.1 【blocker】版本卡聚焦时卡内文字不可读（本任务改动引入的回归）

**位置**：`app/src/main/res/drawable/selector_update_version_item.xml` + `app/src/leanback/res/layout/dialog_update.xml`、`app/src/mobile/res/layout/dialog_update.xml` 里版本卡的静态 `textColor`。

**根因**：第二轮提交 `49b28bd91f9` 把版本卡的 `state_focused` 填充从 `?attr/colorPrimaryContainer` 改成 `?attr/colorPrimary`（浅色），但卡内文字色仍是静态的 `?attr/colorOnSurface` / `?attr/colorOnSurfaceVariant` / `?attr/colorPrimary`，没有跟着翻。三色对浅色填充的实测对比度（WCAG 相对亮度）：

| 卡内文字 | 对 focused 填充 `colorPrimary` | 判定 |
| --- | --- | --- |
| `colorOnSurface`（`stableName`/`stableDesc`） | 夜间 **1.33:1** / 浅色 **2.68:1** | 不可读 |
| `colorOnSurfaceVariant`（`stableVersion`） | 夜间 **1.01:1** / 浅色 **1.46:1** | 不可读 |
| `colorPrimary`（`stableExpand`「展开/收起」） | 两主题均 **1.00:1** | 完全消失 |

**可达性**：`app/src/leanback/java/com/fongmi/android/tv/ui/dialog/UpdateDialog.java:110` 在 `onStart()` 里主动 `binding.stableItem.requestFocus()`，弹窗一打开就落在该状态。

**为什么不能靠换填充色解决**：要让「focused 填充」与「selected 填充」达到契约测试要求的 ≥3:1，两张卡的填充必然要引入一个极性与静态文字相反的 `colorPrimary`；而 `colorPrimary` 在夜间是浅色、浅色主题下是深色，与任何静态文字色都至少有一侧不达标。设计空间穷举（3 个互异填充 × 环色，约束含三填充两两 ≥3:1、静态文字对三填充均 ≥4.5:1）在浅色与夜间主题下**均为 0 个解**。故只能让文字变为状态感知。

**修法**（不动三态填充表，保留第二轮 selected 态的真实改进）：

1. 新增 3 个状态表 `app/src/main/res/color/`：
   - `update_version_item_title.xml`：focused/pressed → `@color/webhtv_color_on_primary`，默认 → `@color/webhtv_color_on_surface`
   - `update_version_item_meta.xml`：focused/pressed → `@color/webhtv_color_on_primary`，默认 → `@color/webhtv_color_on_surface_variant`
   - `update_version_item_accent.xml`：focused/pressed → `@color/webhtv_color_on_primary`，默认 → `@color/webhtv_color_primary`
2. 两张卡（`stableItem`/`betaItem`）内给 header 容器、`*Name`、`*Expand`、`*Version`、`*Content`、`*Desc` 共 11 处加 `android:duplicateParentState="true"`，并把 `textColor` 换成上述三个表（每 flavor 各 8 处绑定）。**未动** `*Status`（自带 `shape_sync_direction` chip 背景，实测 5.51:1 / 9.11:1 已达标）与 `*Confirm`/`cancel`。
3. `TvFocusRingContractTest` 新增 `focusedVersionCardKeepsItsTextReadable`：断言 `contrast(on_primary, primary) >= 4.5`、三个新表的 focused/pressed/默认分支取值、两布局 8 个节点的 `textColor` 字面量绑定与 `duplicateParentState`。

修复后 `on_primary` 对 focused 填充 = 夜间 **7.50:1** / 浅色 **6.39:1**；默认态三个原色对 `surfaceContainerHighest` 分别为 13.23/9.05、7.19/6.85、4.94/6.78，全部 ≥4.5。

`duplicateParentState` + 文字状态表在本仓库有先例：`app/src/leanback/res/layout/adapter_channel.xml:17,19,34,37`、`app/src/mobile/res/layout/adapter_live_program_item.xml:17,20,30,34,43,46`。

### 3.2 【medium】新测试首版未钉住调用点（假绿，已修正）

`focusedVersionCardKeepsItsTextReadable` 的第一版只断言了三个状态表自身与 `duplicateParentState`，**没有断言布局里的 `textColor` 绑定**。反证：把两布局全部 8 处 `android:textColor="@color/update_version_item_title"` 改回 `?attr/colorOnSurface`（等于在绑定点整体撤销修复），测试仍 `tests=27 failures=0` **全绿**。这正是仓库历史踩过的「只测辅助函数未测调用点」陷阱。

修正后重做反证：同样改回 `?attr/colorOnSurface` → `tests=27 failures=1`，失败消息 `app/src/leanback/res/layout/dialog_update.xml 的 stableName 必须把 android:textColor 绑到 @color/update_version_item_title……实际读到 ?attr/colorOnSurface`；还原后 `git hash-object` 与修改前**逐字节一致**（`5e3b24f8851cc55b4f49ea93023720f6056788c9` / `1d42ba6bbbd0b031c112c73a011fd8c6d27db3a3`）。

### 3.3 文档与源码注释失真（已更正）

`docs/TV-FOCUS-RING-DIALOG-20261019.md`：

| 位置 | 原文 | 实测 | 处置 |
| --- | --- | --- | --- |
| L12 / L82 / L166 | 「46 个站点 / 38 个文件」 | **45 处 / 37 文件**（main 29/26、leanback 8/6、mobile 8/5） | 已更正 |
| L14 | 交付坐标见「§8.6」 | 实为 **§8.7**（§8.6 是验证记录） | 已更正 |
| L46 / L70 / L179 | 引 `TV-FOCUS-RING-20261004` **§5.2** | 该文档无 §5.2，1.5dp 规则在 **§12** | 已更正 |
| L42 | 证据文件 `about1.png`、`gear-focused.png`、`v-*.png` | 实际在 `build/tmp-focus-5561/`（9 张 `v-*.png`），该目录被 `.gitignore` 覆盖 | 已补全路径与说明 |
| L107 / L200 | 「1.5dp × 480dpi ≈ 3px」 | 本机 4 台设备 `wm density` 实测均为 **280**（density 1.75）→ 1.5dp ≈ 2.6px 取整 3px | 已更正 |
| L177 | 5 处 Java `setBackgroundTintList(...)`「均已自行设置 stroke」 | **不成立**：`GitCloudDialog.java:2249-2254`、`RemoteTrustDialog.java:3174-3179` 完全不调 `setStrokeColor`/`setStrokeWidth`；`ChoiceDialog.java:595`、`LightDialog.java:237`、`CodecCapabilityDialog.java:363` 写的是 `setStrokeWidth(primary ? 0 : dp(1))`，primary 分支宽度为 0 | 已更正为「这 5 处程序化按钮至今无焦点环，属预存缺陷，只报告不修」 |
| L196 | 「共 5 项 FAILED」 | 其后只列 4 个测试名 | 已更正为 4 |
| L192 | `TvFocusRingContractTest tests=26` | 第二轮交付时确为 26；本任务新增 1 项后为 **27** | 已加计数说明 |

源码注释：

| 位置 | 原文 | 实测 | 处置 |
| --- | --- | --- | --- |
| `app/src/main/res/color/focus_ring_primary_container.xml:22` | 环对弹窗面板「12.06:1（夜间）/ 14.06:1（浅色）」 | **10.40 / 13.35**（12.06/14.06 是环对 `surface_container` 的值） | 已更正 |
| `app/src/main/res/drawable/selector_update_version_item.xml:13` | selected 环 `colorPrimary` 对 selected 填充「7.86:1」 | **5.32（夜间）/ 4.92（浅色）**（7.86 实为 `#A8C7FA` vs `#2A2F34` 的值） | 已更正 |
| `app/src/main/res/color/update_version_item_meta.xml:7` | 默认色对填充「1.19:1 / 2.43:1」 | **1.01 / 1.46** | 已更正 |

经复算**正确**、未改动的数值：L24 齿轮 1.05/1.22；L102 修复后 7.93；L125/L151 环 vs 默认填充 7.50/6.39；L151 聚焦填充/文字 7.04/12.57；L155 聚焦 vs 默认填充 5.32/4.92；L138-139 空集区间四数；L167 的 1.85/1.43；L168 的 1.33/2.68；L196 反证（1.32/2.55 < 4.5 → 确会 FAIL）；L174 `@color/focus_ring_primary` 残留计数（`activity_following.xml` 4、`item_following.xml` 1、`dialog_cache_management.xml` 2）；L29 `dialog_update.xml` 自 `0af2340d495` 起未被触碰；L35 `about_primary_icon_tint.xml` 曾是死代码且分支反了。

### 3.4 复核后判定为**非缺陷**、按范围只报告不修

- **残留 `@color/focus_ring_primary` 站点**（全仓 7 处）：`app/src/leanback/res/layout/activity_following.xml:41,58,75,91`、`app/src/main/res/layout/dialog_cache_management.xml:158`、`app/src/main/res/layout/item_following.xml:199`、`app/src/mobile/res/layout/dialog_cache_management.xml:157`。`focus_ring_primary` 的 focused 分支取 `on_primary`，而这些按钮的焦点填充是 `webhtv_color_focus`（夜间 `#F5F7FF` / 浅色 `#0B57D0`），与 `on_primary` **极性正好配对**，是正确配对。其中只有 `item_following.xml` 在本任务 46 路径内，且本任务只改了 `continue` 按钮（`focus_ring_primary` → `focus_ring_primary_container`），`read` 按钮的 `focus_ring_primary` 是预存引用。
- **`dialog_cache_management.xml` 两处**：cancel 是 `Widget.Material3.Button.TextButton`（填充透明），环直接压在面板上 = 1.05:1（夜）/ 1.22:1（浅），确为真缺陷，但**不在本任务 46 路径内**（`git diff --cached --name-only origin/beta | grep -c dialog_cache_management` = 0）。
- **5 处 Java 弹窗工厂无环**（见 3.3 表末行）：`ChoiceDialog`/`CodecCapabilityDialog`/`GitCloudDialog`/`LightDialog`/`RemoteTrustDialog` 均不在本任务改动集（各自最后提交 `fb85759b03a`/`2e5022b8685`/`4e4bfffcb67`/`f85398d3598`）。契约测试只扫 XML，永不覆盖这 5 处。
- **mobile flavor 裸 `MaterialButton`**：经核对 `app/src/mobile/res/layout/dialog_update.xml` 的 `stableConfirm`/`betaConfirm`/`cancel` 均带 `style="@style/Widget.Material3.Button"` + `backgroundTint`/`strokeColor`/`strokeWidth`，与 leanback 一致，不成立。
- **`Theme.WebHTV.Dialog` 未声明 `colorPrimaryContainer`/`colorOnPrimaryContainer`**：当前消费该 attr 的 layout 都不走完整 `Theme.WebHTV.Dialog`，暂无活缺陷。

## 4. 验证

### 4.1 定向 JVM 测试

```
export JAVA_HOME="C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot"
./gradlew.bat --no-daemon --no-build-cache :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests '*TvFocusRingContractTest*' --tests '*ThemeBinderContractTest*'
```

→ **BUILD SUCCESSFUL in 2m 10s**；`TvFocusRingContractTest` `tests=27 skipped=0 failures=0 errors=0`，`ThemeBinderContractTest` `tests=26 skipped=0 failures=0 errors=0`。

### 4.2 变异测试（证明新门槛可证伪）

| 变异 | 期望 | 实测 |
| --- | --- | --- |
| 两布局 8 处 `textColor="@color/update_version_item_title"` → `?attr/colorOnSurface` | 转红 | `tests=27 failures=1`，`focusedVersionCardKeepsItsTextReadable` FAILED ✅ |
| `update_version_item_accent.xml` focused 分支 → `@color/webhtv_color_primary` | 转红 | `tests=27 failures=1`，消息「必须取填充的配对 on-色」 ✅ |

两次变异均**逐字节还原**（`git hash-object` 与变异前一致）。

### 4.3 静态检查

- `bash scripts/check_ui_tokens.sh` → `UI_TOKEN_BASELINE layouts=385 hex_layouts=0 drawables=562 hex_drawables=0 colors=64 hex_colors=0 allowlisted=190` / `UI_TOKEN_SCOPE stage=A violations=0 legacy=0` / `UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28` / `UI_TOKEN_STATUS PASS`（exit 0）。
- `git diff --check` 与 `git diff --cached --check` 均为空。
- 冲突标记扫描 `git diff origin/beta | grep -cE '^\+(<<<<<<<|=======|>>>>>>>)'` = **0**。
- 净差异 `git diff --shortstat origin/beta` = `46 files changed, 973 insertions(+), 90 deletions(-)`。

### 4.4 已知的既有失败（与本任务净差异无交集）

`:app:testLeanbackArm64_v8aDebugUnitTest` 全量运行有 2 项失败，均**与本任务无关**，且与 `origin/beta`、合并基 `cef7ba60432` 上的同一文件**逐字节相同**（`git rev-parse <rev>:<path>` 三处一致）：

- `androidx.media3.mpvplayer.MpvFontConfigTest.writeIfChanged_replacesStaleConfigurationAndCleansTemporaryFile` → `java.io.IOException: Unable to commit fontconfig configuration`（`MpvFontConfig.java:74`，`File.renameTo` 在 Windows 上跨卷/占用时失败）。
- `androidx.media3.mpvplayer.MpvHlsCacheCoordinatorTest.successfulCommitPublishesCompleteFileAndReleasesReservation` → `java.lang.AssertionError`（`MpvHlsCacheCoordinatorTest.java:208`）。

两者重跑一次仍稳定复现，属 Windows 平台既有缺陷，按 `AGENTS.md` §2 只报告不修。

## 5. 影响与风险

- 生产代码只改 3 个新增颜色状态表 + 2 个布局的 `textColor`/`duplicateParentState`，纯资源，无 Java、无 ABI、无 API、无依赖锁、无补丁、无 native 资产、无包体积影响。
- 行为变化**仅限版本更新弹窗两张版本卡在聚焦/按下时的文字色**：由「静态色压在浅色填充上」变为「配对 on-色」。默认态与选中态文字色不变。
- 合并本身是纯 fast-forward 式的树合并（0 冲突、两侧路径零重叠），不改变 beta 侧任何文件的内容：相对 beta 限定在 beta 15 条路径上的差异为 **0 行**。
- 残留风险：`duplicateParentState` 的跨层传递（header 容器 → `*Name`/`*Expand`）依赖 Android 框架行为，本轮以契约测试断言声明层面锁定，未做真机像素级复验；版本卡聚焦态的文字可读性以 WCAG 计算值验收（7.50/6.39），未新增设备截图。

## 6. 回滚

- 合并本身：`git revert -m 1 <本任务合并提交>`。
- 3.1 修复：`git revert <本任务提交>`（3 个新增资源 + 2 个布局 + 1 个测试，无持久化 schema）。
- 3.3 更正：纯文档/注释，随本任务提交一并回滚。

## 7. 交付坐标

- 合并提交：`c0b1ffd3c2d048e7e975a85cb480dc7589f18c9b`（双父：`1b35b6e3e80554e79654455a3d8a986bb1236b66` + `0bb2601e3871f58b727873837112abe86784d61f`）。
- recovery tag：`recovery/C83-beta-merge-review-dev4-20261019/20261019175417-c0b1ffd3c2d0`。
- 分支 `dev4` 已推送（`cef7ba60432..c0b1ffd3c2d`）；PR [#462](https://github.com/Silent1566/webhtv/pull/462) → `beta`，`state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`、`changedFiles=51`（+1204 / -90），**只创建、不合并**。
- PR 文件集与 `git diff --name-only origin/beta HEAD` 逐项一致（51/51）。
