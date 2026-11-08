# C115-beta-merge-review-dev3-20261031

## Recovery anchor

- **目标**：合并 `origin/beta` 最新代码后，评审 `dev3` 上「已提交未推送」的改动（含合并提交）；有必修项则修复并验证，
  通过后再次评审，循环直到通过；然后提交、推送 `dev3`、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. `dev3` 相对 `origin/beta` 落后归零（`origin/beta` 是 HEAD 的祖先），或明确证明无需再合并。
  2. 评审覆盖 `origin/beta..HEAD` 的全部净差异；每条结论有可复核的 `路径:行号` 证据。
  3. 每个必修项均已修复并配「会变红」的守卫；无必修项时须用变异验证证明现有守卫非空转；修复后复评通过。
  4. 定向单测 0 失败；工作区除初始脏项外无残留改动。
  5. 改动经任务守卫 `finish` 落盘并打 recovery tag；推送 `dev3`；创建到 `beta` 的中文 PR；随后拉取远端最新代码。
- **Lane**：`assessment`（`docs`）
- **任务守卫**：`C115-beta-merge-review-dev3-20261031`，base HEAD `d1686cb44d9f84371a65a34aed709426701c549c`，branch `dev3`
- **当前状态**：评审通过（无必修项），遗留项经用户裁定「仅记录」；交付坐标见 §7

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 合并目标 | `origin/beta` = `a5469c5ceac088d824fbd79df8a80e49580a6887`（"Merge pull request #506 from Silent1566/dev2"，2026-10-31 13:59:20 +0800） |
| 本次评审基线 | `dev3` HEAD = `d1686cb44d9f84371a65a34aed709426701c549c` |
| 祖先关系 | `git merge-base --is-ancestor origin/beta HEAD` → **exit 0（true）** |
| 落后 / 领先 | `git rev-list --left-right --count origin/beta...HEAD` = **0 / 1** |
| 合并动作 | **无需合并**：`origin/beta` 已全部包含在 `dev3` 中，再 merge 只会产生空提交，故不做 |
| 待评审净差异 | `git diff --stat origin/beta..HEAD` = **2 files changed, 136 insertions(+), 0 deletions** |

`dev3` 领先 `origin/beta` 的提交只有一个：

- `d1686cb44d9f84371a65a34aed709426701c549c` Fix TV speed adjustment UP key being swallowed by control focus helper（Silent1566，2026-10-31 15:35:09 +0800）

其余 9 个未推送到 `origin/dev3` 的提交（`a5469c5cea`/#506、`454b4aaaeb`/#507、`9d8ea553da`/#509、`707dc2a1c6`、`7f4e148892`、`f9ebfec8d4`、`21c10db4a1`、`086a25cc25`、`0acd75b104`）的内容**已在 `beta` 中**，
因此净差异只有这一个提交。

### 1.1 为什么不能沿用上一轮评审跳过

仓库内已有 `docs/C114-beta-merge-review-dev3-20261031.md`，但其覆盖范围止于合并提交 `a2d4a04faa62d9ec4f5997f7527825692e87c4e4`（PR #509）。
对该文档 grep `d1686cb` / `dispatchSpeedAdjust` / `SpeedAdjustKey` → **0 命中**，即上一轮评审**没有覆盖** `d1686cb44d`。
用户给出的跳过前提（"之前的评审已覆盖当前所有修改的代码"）不成立，本轮必须评审该提交。

### 1.2 为什么必须新建 PR

PR #509（base `beta` ← head `dev3`）已于 2026-10-31T05:58:57Z **MERGED**；`gh pr list -R Silent1566/webhtv --state open` → **[]**。
`d1686cb44d` 是 #509 之后新增的提交，需要一个新的 PR 才能进入 `beta`。

---

## 2. 评审范围

`git --no-pager diff origin/beta..HEAD` 全量（7754 字符）逐行读毕，两处改动：

| 文件 | 改动 |
| --- | --- |
| `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` | +22（新方法 `dispatchSpeedAdjust` 及其调用点） |
| `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/SpeedAdjustKeyContractTest.java` | +114（新文件，4 个 `@Test`） |

为判定其正确性，另外取证了被改文件之外的 6 处调用链与样式定义（见 §3、§4 的 `路径:行号`）。

---

## 3. 缺陷根因认定（成立）

**现象**：TV 端把焦点停在控制栏的「倍速」按钮上，按 UP 不加速；按 DOWN 却正常减速。

**根因链**（每一步都有代码证据）：

1. `app/src/leanback/res/values/styles.xml:80-89` 的 `<style name="Control">` 只给了 DOWN 方向邻居：
   :86 `<item name="android:nextFocusDown">@id/timeBar</item>`。
2. 全仓 `nextFocus` 普查：**没有任何 chip 设置 `nextFocusUp`**。倍速按钮
   `app/src/leanback/res/layout/view_control_vod_action.xml:108-113`（`CustomUpDownView`，`style=@style/Control`）因此 UP 方向在控制栏内没有可聚焦目标。
3. `app/src/main/java/com/fongmi/android/tv/ui/helper/PlayerControlFocusHelper.java` 的 `handleKey`：
   第 39 行 `if (next == null || !isDescendant(root, next)) return true;` —— `focusSearch(FOCUS_UP)` 返回 null 时判定为
   「会把焦点甩出控制栏」并**消费掉按键**（返回 true）。
4. 于是 UP 事件根本到不了 `app/src/leanback/.../VideoActivity.java:1678` 绑定的 `speed.setUpListener(this::onSpeedAdd)`；
   而 DOWN 能生效是因为 `CustomUpDownView.dispatchKeyEvent`（`app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomUpDownView.java`）
   在 `hasEvent` 为真时自行拦截并 `return true`，事件在到达 `handleKey` 之前就被消费。

⇒ 用户的「UP 被吞」描述与代码路径完全吻合，修复方向（在 `handleKey` 之前接管 UP）正确。

---

## 4. 修复内容评审

### 4.1 新增方法（`VideoActivity.java:8793-8812`）

```java
    private boolean dispatchSpeedAdjust(KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || !isVisible(mBinding.control.getRoot())) return false;
        if (getCurrentFocus() != mBinding.control.action.speed) return false;
        if (KeyUtil.isUpKey(event)) {
            onSpeedAdd();
            return true;
        } else if (KeyUtil.isDownKey(event)) {
            onSpeedSub();
            return true;
        }
        return false;
    }
```

三道门禁顺序正确：**先判动作与可见性，再判焦点身份，最后才判方向**。若把方向判断提前，
任意控件上的 UP/DOWN 都会被 `onSpeedAdd/onSpeedSub` 抢走。javadoc（:8793-8800）把根因写在代码旁，
未来改 `Control` 样式或 `handleKey` 的人能直接看到为什么需要这个补丁。

### 4.2 调用点（`VideoActivity.java:8837-8843`）

```java
        if (isVisible(mBinding.control.getRoot())) {
            setR1Callback();
            if (dispatchOpeningEndingAdjust(event)) return true;
            if (dispatchSpeedAdjust(event)) return true;      // ← 8840，本次新增
            if (PlayerControlFocusHelper.handleKey(mBinding.control.getRoot(), getFocus2(), event)) return true;
            if (PlayerControlFocusHelper.containsFocus(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        }
```

- 位置正确：在 `handleKey` **之前**，否则 UP 已被吞，补丁无效。
- 与相邻的 `dispatchOpeningEndingAdjust`（:8763-8769）保持同一写法与同一门禁
  （`!KeyUtil.isActionDown(event) || !isVisible(control.getRoot())`），其分派体
  `dispatchOpeningAdjust`(:8771-8780) / `dispatchEndingAdjust`(:8782-8791) 就是 `isUpKey→onXxxAdd / isDownKey→onXxxSub`。
  新方法把「门禁 + 焦点分派 + 上下判断」合并在一个方法内，语义等价，可读性更好。
- 仍在 `isVisible(control.getRoot())` 块内 ⇒ 控制栏隐藏时不接管，不干扰全屏下的
  `onEpisodeKey`(:8844) / `mAudioStageVisible && dispatchAudioStageKey`(:8846) / `mKeyDown.hasEvent`(:8847) 等后续分支。

### 4.3 非回归论证

| 场景 | 修复前 | 修复后 | 结论 |
| --- | --- | --- | --- |
| speed 上按 DOWN | `CustomUpDownView` 拦截 → `onSpeedSub` | 同（`dispatchSpeedAdjust` 走同一处理器） | 语义不变 |
| speed 上按 UP | 被 `handleKey` 吞掉 | `onSpeedAdd` | 目标修复 |
| speed 上按 LEFT/RIGHT | `handleKey` 判定邻居并放行/拦截 | 新方法对非 UP/DOWN 返回 false → 仍走 `handleKey` | 不变 |
| speed 上按 CHANNEL_UP/DOWN、PAGE_UP/DOWN、MEDIA_PREVIOUS/NEXT | `CustomUpDownView` 拦截 → add/subSpeed | 同（这些键都被 `KeyUtil.isUpKey/isDownKey` 覆盖，且 `handleKey` 的 `isFocusKey` 只含 DPAD/confirm） | 不变 |
| 焦点不在 speed（其他 chip / 进度条） | `handleKey` 处理 | 新方法第二道门禁返回 false → 仍走 `handleKey` | 不变 |
| 控制栏隐藏 | 直接跳过该块 | 直接跳过该块 | 不变 |
| `mFocus2` 记忆 | speed 会被记住（`hasRememberedFocus`:8753-8757 只排除 opening/ending） | 同；且 UP 时在 `handleKey` 前 return true，不会写入 `mFocus2` | 无副作用 |

另核对了另外两处 `handleKey` 调用点，均不受影响：
`app/src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java:1297-1302` 的 `dispatchKeyEvent` **从不调用** `PlayerControlFocusHelper.handleKey`，
故 LIVE 控制栏不存在被吞 UP 的问题（其 `speed.setUpListener/setDownListener` 在 :239-240 一直有效）；
`app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:5739` 属手机 flavour 且 `app/src/mobile` 下
**没有任何** `CustomUpDownView`/`setUpListener`/`setDownListener` 引用 ⇒ 本次修复对手机 flavour 零影响。

### 4.4 契约测试评审（`SpeedAdjustKeyContractTest.java`，114 行）

4 个 `@Test`，均为源码文本断言（`org.junit.Test` + `java.nio.file.Files/Path`，不依赖 Robolectric）：

| 用例 | 断言内容 |
| --- | --- |
| `speedButtonAdjustsOnBothArrows` | 方法体含 `onSpeedAdd()`/`onSpeedSub()`；`isUpKey` 出现在 `isDownKey` 之前；两分支各自带 `return true` |
| `adjustmentOnlyRunsForTheFocusedSpeedButtonWhileControlsAreShown` | `isActionDown`、`isVisible(control.getRoot())`、`mBinding.control.action.speed` 三者位置均 > 0 且都早于 `isUpKey`（锁死「门禁先于方向」） |
| `speedAdjustmentIsDispatchedBeforeTheFocusFinderSwallowsUp` | `dispatchKeyEvent` 内 `dispatchSpeedAdjust` 位置 > 0、早于 `handleKey`；含字面量 `if (dispatchSpeedAdjust(event)) return true;`；且 `opening` 早于 `speed`（锁死相邻顺序） |
| `theFixDoesNotWeakenTheFocusFinderGuard` | helper 的 `handleKey` 仍含 `if (next == null || !isDescendant(root, next)) return true;`，且剥掉注释后**不含** `speed`（禁止把特殊判断塞回 helper） |

最后一条尤其重要：它防止后人为了「省事」把倍速判断塞进 `PlayerControlFocusHelper` ——
那会让一个通用焦点工具依赖具体业务控件，并把 LiveActivity/TmdbDetailActivity 一起卷进来。
断言用 `withoutJavaComments`（正则剥 `/* */` 与 `//`）实现，注释里出现 `speed` 不会误伤。

风格上与本目录既有 Robolectric 测试（`PlayerControlFocusHelperSeekReturnTest.java`）不同，
但对象是**按键分派顺序**这类结构性契约，源码断言是恰当且更廉价的工具。

---

## 5. 验证证据

| 项 | 命令 / 位置 | 结果 |
| --- | --- | --- |
| 修复态定向单测 | `gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.SpeedAdjustKeyContractTest` | **BUILD SUCCESSFUL in 34s**（后台 job `pwsh-8ff007a2365f40ae`，exit 0）；`TEST-...SpeedAdjustKeyContractTest.xml`：`tests="4" skipped="0" failures="0" errors="0"`，timestamp `2026-10-31T08:50:05Z` |
| **变异验证** | 临时删除 `VideoActivity.java:8840` 的 `if (dispatchSpeedAdjust(event)) return true;` 后重跑同一测试 | **`speedAdjustmentIsDispatchedBeforeTheFocusFinderSwallowsUp` FAILED / `java.lang.AssertionError at SpeedAdjustKeyContractTest.java:73` / 4 tests completed, 1 failed / BUILD FAILED in 55s / exit 1** |
| 变异后复原 | 用同一编辑反向恢复该行，`git status --porcelain=v1` | 只剩 3 个初始脏项（`?? %SystemDrive%/`、`?? .dbg.txt`、`?? .design-evidence/`），工作区与 HEAD 一致 |

变异验证是本节的核心：它证明 4 个用例不是「读一遍源码永远为真」的空转断言 —— 一旦修复被移除，测试立刻变红并指向确切行号。

---

## 6. 遗留项（用户裁定：仅记录，不改）

同路径存在**既有**（非本提交引入）的守卫缺口：

- `VideoActivity.java:5276-5280` `onSpeedAdd()`、:5282-5286 `onSpeedSub()`、:5288-5293 `onSpeedLong()`、:5295-5297 `saveUserSpeed()`
  都没有 `isServiceReady() || !isOwner() || player().isEmpty()` 守卫，而同文件的 `onSpeed()`（:5267-5274）有。
- `protected PlayerManager player()` = `app/src/main/java/com/fongmi/android/tv/ui/activity/PlaybackActivity.java:110-112`
  `return mService == null ? null : mService.player();`；`mService` 在 :1011-1013（releaseBinding）与 :1339-1342（onServiceDisconnected）被置 null。
  服务断开后这些方法会 NPE。
- 片头/片尾的 `onOpeningAdd/onOpeningSub/onEndingAdd/onEndingSub` 完全同构，缺口一致。

**为什么不修**：DOWN 与长按在本修复之前就走这条路径，本提交只是让 UP 也走上来；补守卫会同时改变 DOWN/长按/片头/片尾的行为，
超出「UP 被吞」的范围，属行为扩张。按 `AGENTS.md` §2「相邻既有缺陷只报告不顺手修」，且经用户裁定，
记录于此并在 PR 描述中列为遗留项，另开任务处理。

---

## 7. 交付坐标

| 项 | 值 |
| --- | --- |
| 评审提交 | `d1686cb44d9f84371a65a34aed709426701c549c`（2 files, +136 −0） |
| 评审结论 | **通过，无必修项**（根因认定成立、位置正确、非回归论证见 §4.3、守卫经变异验证非空转） |
| 验证（评审时） | 定向单测 4/4 通过（34s）；变异态 1 失败（55s，AssertionError at `SpeedAdjustKeyContractTest.java:73`） |
| 遗留项 | `onSpeedAdd/onSpeedSub/onSpeedLong/saveUserSpeed` 缺播放服务守卫（既有、非本次引入），仅记录，见 §6 |
| 评审文档提交 | `a2ffd76d459116ae6269575e787568c5aa1dd1cb` "docs(C115): 评审 dev3 倍速 UP 键修复 d1686cb44d 并回填交付坐标"（1 file, +206） |
| 恢复标签 1 | `recovery/C115-beta-merge-review-dev3-20261031/20261031103431-a2ffd76d4591` |
| 推送 dev3（第一次） | `d1686cb44d..a2ffd76d45  dev3 -> dev3` |
| PR 到 beta | **#511** OPEN，`dev3` → `beta`，`mergeable=MERGEABLE`，https://github.com/Silent1566/webhtv/pull/511 |
| 拉取远端 | `git fetch --all --prune --tags` → `origin/beta` `a5469c5cea..9c695e9982`、`origin/dev2` `7f4e148892..cddb92832b`、新增 tag `v5.6.0-beta-202610311359` 与 `recovery/beta-sync-review-dev4-20260913/20260913181725-d67e92aab666` |

### 7.1 拉取后发现 beta 已前进，补做第二次合并（§8）

`git fetch` 让 `origin/beta` 从评审基线 `a5469c5cea` 前进到 `9c695e9982`，本节第 1 版写的「无需再合并」在那一刻已经过期。
用户裁定「合并最新 3 提交、验证后推送」，据此补做合并，详见 §8。

---

## 8. 补做合并：评审之后 beta 又前进 3 个提交

### 8.1 发生了什么

评审基线取的是 `origin/beta` = `a5469c5ceac088d824fbd79df8a80e49580a6887`。交付末尾执行 `git fetch --all --prune --tags`
时 `origin/beta` 已前进到 `9c695e998298b178dc216a54b6e9e19832ae7133`，多出 3 个提交（merge-base 仍是 `a5469c5cea`）：

| 提交 | 标题 |
| --- | --- |
| `9c695e998298b178dc216a54b6e9e19832ae7133` | Merge pull request #510 from Silent1566/dev2 |
| `cddb92832b41ed467c0376385c4c0538cf8a9fe4` | fix: end TV hold-speed only on the up-key release |
| `7174df94571585e8c65adc907affb7ff332e32b8` | fix: keep TV hold-speed release from opening the playback menu |

此时 `git rev-list --left-right --count origin/beta...HEAD` = **3 / 2**。用户明确要求「合并远端 beta 最新代码」，
且这 3 个提交不改 `dispatchSpeedAdjust` 本身，故按用户裁定补做合并，而不是让 PR 基于过期基线。

### 8.2 冲突与自动合并

`git merge-tree --write-tree HEAD origin/beta` → **exit 0，零冲突**；随后 `git merge --no-ff --no-edit origin/beta`
由 ort 策略自动合并成功，产出 `5bcb9a3838c4b580583ccb9cf175b08d2628b6d8`，工作区无 `U` 状态文件：

```
 app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java             |   3 +-
 app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityLayoutTest.java |  12 ++-
 docs/C115-tv-speed-hold-residue-20261031.md                                            | 114 +++++++++++++++++++++
 3 files changed, 127 insertions(+), 2 deletions(-)
```

### 8.3 合并后按键链路的交互复核（关键）

beta 侧把 `dispatchKeyEvent` 第 8826 行改成
`if (mKeyDown.isChangingSpeed() && KeyUtil.isActionUp(event) && KeyUtil.isUpKey(event)) { ... return true; }`。
它与本次 `dispatchSpeedAdjust` 的关系必须逐条确认，否则两条修复可能互相吃掉：

| 键位 / 状态 | 合并后的实际走向 | 结论 |
| --- | --- | --- |
| 控制栏可见、焦点在 speed、UP **按下**（ACTION_DOWN） | 8826 行 `isActionUp` 为假 → 不拦截；进入 8838 块 → **8841 `dispatchSpeedAdjust` 接管 → `onSpeedAdd()`** | 本次修复仍然生效 |
| 控制栏可见、焦点在 speed、UP **抬起**（ACTION_UP） | 若 `mKeyDown.isChangingSpeed()`（长按加速态）为真且是 UP 键 → 8826 行 `releaseSpeed()` 后 `return true`，不再落到 `onKeyUp` 的 `showControl`（beta #510 的修复）；否则继续向下 | 两个修复分别负责按下/抬起，不重叠 |
| 控制栏隐藏时的长按加速 | 8850 行的 `isGone(control)` 分支走 `mKeyDown.onKeyDown`，与 8826 行配合 | 不受本次改动影响 |
| 焦点不在 speed、UP 按下 | 8826 行 `isActionUp` 为假；8841 第二道门禁返回 false → 仍由 8842 `handleKey` 处理 | 不变 |
| speed 上按 DOWN | 8841 走 `onSpeedSub`；8826 行只认 UP 键 | 不变 |

即：**UP 的 ACTION_DOWN 归 `dispatchSpeedAdjust`，UP 的 ACTION_UP 归 beta 的 `isChangingSpeed` 分支**，两者按 action 天然分离。

### 8.4 合并后验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| leanback 编译 | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| 本次契约测试 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests ...SpeedAdjustKeyContractTest` | `tests=4 failures=0 errors=0 skipped=0` |
| beta 带来的回归测试 | `:app:testMobileArm64_v8aDebugUnitTest --tests *VideoActivityLayoutTest*` | `tests=157 failures=0 errors=0 skipped=0` |
| 整体 | 三条目标一次跑完 | **BUILD SUCCESSFUL in 2m 58s**，exit 0 |

### 8.5 第二次交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `5bcb9a3838c4b580583ccb9cf175b08d2628b6d8` "Merge remote-tracking branch 'origin/beta' into dev3" |
| 恢复标签 2 | `recovery/C115-beta-merge-followup-20261031/20261031190323-5bcb9a3838c4` |
| 任务守卫 | 该合并由 `git merge` 直接产生提交，守卫检出 HEAD 漂移后以 `abort` 归档（工作区未受影响） |
| 推送 dev3（第二次） | 见提交后的回填 |
| PR | #511 自动纳入该合并（`dev3` → `beta`），无需新建 |
| 文档编号说明 | beta 侧同日另有 `docs/C115-tv-speed-hold-residue-20261031.md`（#510 的文档）；主题不同、文件名不冲突，本文件保留 C115 前缀 |
