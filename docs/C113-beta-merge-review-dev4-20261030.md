# C113-beta-merge-review-dev4-20261030

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev4`；评审 `dev4` 已提交未推送的代码（含合并提交）；
  发现问题即修复并验证；复评直至通过；然后提交、推送 `dev4`、创建到 `beta` 的中文 PR，最后拉取远端最新代码。
- **验收标准**：
  1. 合并后 `origin/beta` 成为 HEAD 祖先（落后 0），无冲突、无夹带评审外改动。
  2. 评审覆盖 `origin/dev4..HEAD` 中本机独有的全部路径（本轮 = 1 个功能提交 + 1 个文档提交 + 1 个合并提交）。
  3. 发现的必修项已修复；修复后复评通过，双 flavour 编译通过，theme 包单测零失败。
  4. 改动提交并打 recovery tag；推送 `dev4`；创建到 `beta` 的中文 PR；随后拉取远端最新代码。
- **Lane**：`upstream`（`app` 两侧 flavour + `docs`）
- **任务守卫**：**未执行**。本机 `bash` 通道不可用（`bash -c "echo ok"` →
  `Bash/Service/CreateInstance/CreateVm/HCS/HCS_E_SERVICE_NOT_AVAILABLE`），`.codex/scripts/task_guard.sh` 无法启动。
  改用等效手工流程：只暂存任务自有路径 + 手工打 recovery tag。该限制与 C105/C108/C111 记录一致，非本轮引入。
- **当前状态**：合并、评审、必修项修复、复评、验证完成；交付坐标见 §6

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 合并目标 | `origin/beta` = `66b40a1338fa086ae506cb87dfc2b8c72e03d086`（"Merge pull request #503 from Silent1566/dev2"） |
| 合并前本地 | `dev4` = `d039c39f33181aeac0ae6abc609b58c75bb884b3`（"docs(theme): 记录 LIVE_LIST_SCRIM_OPACITY 的 emulator-5560 真机验证证据"） |
| 祖先关系（合并前） | `git merge-base --is-ancestor origin/beta HEAD` → **false** |
| 落后 / 领先（合并前） | `git rev-list --count dev4..origin/beta` = **8** |
| merge-base | `ba802dc4327dcb2433dfecb2f2dfd5c591621e86`（"Merge pull request #500 from Silent1566/dev5"） |
| 冲突预检 | `git merge-tree --write-tree HEAD origin/beta` → 只输出单一 tree 对象，**无冲突** |
| 合并动作 | `git merge --no-ff origin/beta` → **32 个路径 / +2948 −61**，零手工干预 |
| 合并提交 | `f6aea3c4b01de1842d6f78f2b6e764d7c241db5a`（"Merge remote-tracking branch 'origin/beta' into dev4"） |
| 祖先关系（合并后） | `git merge-base --is-ancestor origin/beta HEAD` → **true**；left-right = **0 / 3** |
| 合并纯净性 | `git diff --stat origin/beta..HEAD` = **只有 dev4 自己的 18 个路径 / +517 −5**，beta 侧一个都没丢、也没有任何 beta 路径被本地改写 |

`beta` 本次带入 8 个提交（1 个 merge(#503) + 1 个 merge(#502) + 6 个实质提交），
覆盖追更（following）静默失败与季号未知进度停更、leanback 起播主线程卡顿优化、
C111/C112/FOLLOW-3 三份文档与 `scripts/perf` 四个探测脚本。**其中不含 theme 包任何文件**，
因此与本轮 dev4 的 `LIVE_LIST_SCRIM_OPACITY` 改动零重叠（重叠面只有 3 个 `strings.xml`
和 4 个 `bean/*.java` + `VideoActivity.java`，且后两者的 dev4 侧自 `ba802dc4327` 起无改动，
故合并结果 = beta 原文，见 §2）。

## 2. 合并冲突面核对（重叠路径逐个确认）

`git diff --name-only dev4...origin/beta`（beta 侧改动）与 `git diff --name-only origin/dev4..dev4`
（dev4 自上次推送后的改动）交集为 7 个路径：

```
app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java
app/src/main/java/com/fongmi/android/tv/bean/Backup.java
app/src/main/java/com/fongmi/android/tv/bean/Config.java
app/src/main/java/com/fongmi/android/tv/bean/History.java
app/src/main/res/values-zh-rCN/strings.xml
app/src/main/res/values-zh-rTW/strings.xml
app/src/main/res/values/strings.xml
```

但 `origin/dev4` 落后很多，这个交集是把历史提交算进去的宽口径。真正的判定口径是
**本轮合并点**（`ba802dc4327`）之后 dev4 自己动了什么：

| 路径 | dev4 侧自 `ba802dc4327` 的改动 | 结论 |
| --- | --- | --- |
| `bean/Backup.java`、`bean/Config.java`、`bean/History.java`、`VideoActivity.java` | **零改动**（`git diff --stat ba802dc4327 d039c39f331` 只报 `values/strings.xml +1`） | 合并结果 = beta 原文，无冲突风险 |
| `values/strings.xml` | +1 行 `theme_editor_slot_live_list_scrim_opacity`，插入位置在 `theme_editor_slot_player_scrim_opacity` 之后 | beta 新增的两条 `following_*` 在别处，行级不重叠 |
| `values-zh-rCN/strings.xml`、`values-zh-rTW/strings.xml` | 零改动 | 无重叠 |

合并后三处字符串同时存在，逐条核对通过：

```
values/strings.xml:966          theme_editor_slot_live_list_scrim_opacity = "Live list overlay opacity"
values/strings.xml              following_source_failed = "Current source check failed"
values/strings.xml              following_watched_unknown = "No watch progress yet"
values-zh-rCN/strings.xml:962   theme_editor_slot_live_list_scrim_opacity = "直播列表遮盖层不透明度"
values-zh-rTW/strings.xml:917   theme_editor_slot_live_list_scrim_opacity = "直播列表遮罩不透明度"
```

## 3. 评审范围与"上次评审是否已覆盖"的判定

`git log --oneline origin/dev4..d039c39f331` 共 16 个提交，但其中 14 个已经通过 PR #498/#499/#500/#501
进入 `origin/beta`（`ba802dc4327` 就是 #500 的合并提交），即**已推送、已在 beta 里**。
本机真正独有、尚未进入任何远端分支的只有两个：

- `bcdd344ec5c` feat(theme): 直播列表遮盖层可配置（LIVE_LIST_SCRIM_OPACITY），默认与现状逐值一致
- `d039c39f331` docs(theme): 记录 LIVE_LIST_SCRIM_OPACITY 的 emulator-5560 真机验证证据

判定：**不能跳过评审**。理由（不是"看起来新"，而是可验证的事实）：

- `git merge-base --is-ancestor bcdd344ec5c 66b40a1338f` → **false**；
  `git merge-base --is-ancestor d039c39f331 66b40a1338f` → **false**。
  两个提交都不在合并前的 `origin/beta` 里，说明它们从未经过 beta 侧评审。
- 本机 `docs/` 下检索 `LiveListScrim|LIVE_LIST_SCRIM`，**只命中该功能自己的任务文档**
  `docs/LIVE-LIST-SCRIM-OPACITY-20261029-live-list-scrim.md`，没有任何 `C*-beta-merge-review-*`
  记录覆盖这两笔提交。上一轮 C111（dev4）评审的净差异是 3 个路径 / +60 −1，与本轮 18 个路径 / +517 −5 不同。
- 该功能的任务文档自带真机证据与实现理由，但那是**作者自证**，不等于独立评审。

因此本轮完整评审 `origin/beta..HEAD` 的产品代码（18 个路径）：

```
theme/LiveListScrim.java (新)          theme/ThemeController.java
theme/ThemeEditor.java                 theme/ThemePreviewView.java
theme/ThemeProfile.java                theme/ThemeProfileValidator.java
leanback/ui/activity/LiveActivity.java mobile/ui/activity/LiveActivity.java
values/strings.xml ×3
test/.../theme/{LiveListScrimTest, LiveListScrimWiringTest, ThemeEditorContractTest,
                ThemeProfileCodecTest, ThemeProfileValidatorTest}.java
testLeanback/.../theme/ThemeEditorPanelTest.java
docs/LIVE-LIST-SCRIM-OPACITY-20261029-live-list-scrim.md
```

## 4. 评审结论（逐项核对，含证据）

### 4.1 默认路径与改动前逐值一致（核心承诺）

`LiveListScrim.BASE_ALPHAS = {0.80f, 0.40f, 0.05f}`，`DEFAULT_OPACITY = 0.80f`，
`scale = clamped / 0.80f`，未设置槽位时 `resolveOpacity(null) = 0.80f` → `scale = 1.0` →
`withinAlpha` 逐项 = `round(0.80×255)=204=0xCC`、`round(0.40×255)=102=0x66`、`round(0.05×255)=13=0x0D`，
即 `{0xCC000000, 0x66000000, 0x0D000000}`。与 `app/src/main/res/values/colors.xml` 的
`black_80=#CC000000`、`black_40=#66000000`、`black_05=#0D000000` **逐值相等**，已由
`LiveListScrimTest.defaultOpacityReproducesTheShippedLiveListGradient` 锁定。

### 4.2 方向与三段停靠点与 XML 一致

`shape_live_list.xml` 用 `android:angle="0"`（Android 语义 = 左→右）配
`startColor/centerColor/endColor`。`LiveListScrim` 用 `GradientDrawable.Orientation.LEFT_RIGHT`
+ 3 色构造器；`GradientDrawable` 的 3 色构造与 XML 一样按 0%/50%/100% 均匀停靠，
且 XML 只含 `<gradient>`、无 corners/描边，新代码也没有 `setCornerRadii`。方向、停靠点、形状三项一致。

### 4.3 只缩放 alpha，不改色相

`withAlpha(float)` 返回 `alpha << 24`，RGB 恒 0（纯黑），无任何 `colorPlayerScrim` 参与。
这保证 `shape_live_focused.xml:7` 注释里"白色焦点环对比度依赖宿主深色渐变"的前提不被破坏。
`LiveListScrimTest.aLowSliderValueScalesTheGradientWithoutTouchingTheHue` 与
`theScrimFollowsTheThemeSlotInsteadOfAHardcodedDrawable`（断言源码不含 `colorPlayerScrim`）双重锁定。

### 4.4 槽位链路完整性

`ThemeEditor.Slot` 现为 **19 个常量**（`PRIMARY`…`WARNING` 14 个颜色 +
`SCRIM_OPACITY/DIALOG_OPACITY/OVERLAY_OPACITY/PLAYER_SCRIM_OPACITY/LIVE_LIST_SCRIM_OPACITY` 5 个不透明度），
`LIVE_LIST_SCRIM_OPACITY` 是最后一个，故 `isColor()`（`ordinal() <= WARNING.ordinal()`）判为不透明度槽。
穷举 switch 共 5 处全部补齐：`valueOf`、`setOpacity`、`clamp`、`labelOf`（无 default，漏 case 编译失败）、
`ThemePreviewView` 的 `render/minimumOf/maximumOf/labelOf`。`ThemePreviewView` 的「图层」组直接追加同一常量。

### 4.5 "不烘焙默认值" 语义正确

`ThemeProfile.SlotSet.liveListScrimOpacity` 无字段初始值；`ThemeController.configuredLiveListScrimOpacity`
未设置时返回 `null`（**不**回落成 0.80f），默认观感由 `LiveListScrim.DEFAULT_OPACITY` 提供；
`ThemeProfileCodec` 用 `serializeNulls()`，因此编码 JSON 里键存在、值为 `null`。
`ThemeProfileCodecTest.anUnsetLiveListScrimSlotStaysUnsetInsteadOfDefaultingToANumber` 断言的是
`assertNull(...)` 而不是"键不存在"，语义正确。

### 4.6 两个宿主的接线是最小面

- leanback：`initView()` 里在 `applyPlaybackOverlay()` 之后独立调用 `applyLiveListScrim()`，
  **故意不并入 `applyPlaybackOverlay()`**——后者被 `SettingPlaybackOverlayTest` 做源码断言，
  且抽屉遮罩与 `Setting.isPlaybackOverlayEnabled()` 无关，必须无条件生效。
  `LiveListScrimWiringTest.theLeanbackLiveActivityPaintsItsChannelDrawerFromTheThemeSlot`
  反向断言 `applyPlaybackOverlay()` 方法体内不得出现 `LiveListScrim`，防回归。
- mobile：只在 `applyLiveListStyle()` 的 `liveMenuOverlay` 分支替换一行；
  classic / embedded 两个分支与 `liveCurrent` 背景逻辑一字未改（`git diff` 为 +2 −1）。
- `applyLiveListScrim()` 无条件生效，所以 leanback 布局里 `@drawable/shape_live_list` 静态背景
  被运行时覆盖；XML 保留作未接入主题的入口回落，不是死代码（布局仍引用）。

### 4.7 未发现实质缺陷

- `create(Context)` 在 `context == null` 时返回 `null`，宿主 `setBackground(null)` 只是清背景，
  不会崩溃；与既有 `PlaybackMenuScrim.create` 契约一致。
- `resolveOpacity` / `gradientColors` 双向钳位，`scale = clamped/0.80` 在 `clamped∈[0,1]` 时
  `scale∈[0,1.25]`，逐项 `Math.min(1f, ...)` 封顶，不会出现 alpha > 255。
- validator 区间 `0.20f..1f` 与 `ThemeEditor.clamp` 同源常量，light/dark 两模式都校验
  （`ThemeProfileValidatorTest.enforcesTheLiveListScrimRangeOnEveryMode` 覆盖 9 个边界）。
- `ThemePresets.withPaletteStyle` 遍历 `Slot.values()`，不透明度槽走 `Float.parseFloat(value)` 分支，
  新槽自动纳入、不会抛 `NumberFormatException`。
- 三 locale 与 theme 目录 `values-night` 结构一致（`values-night` 只放 colors，无 strings），不存在漏翻译。

### 4.8 发现的必修项（1 项，已修复）

**F1 — `ThemeProfile` 两处槽位计数注释与实际槽位数不符。**

证据链：改动前注释写 `17 user-editable semantic slots` / `13 color slots plus 4 opacity slots`，
但 `ba802dc4327`（基线）的实际槽位已经是 **18**——`PLAYER_CURRENT` 早已由 `c3bb6a63d9c`
（"fix(theme): 播放页焦点/当前态环统一宽度并统一为受主题控制的派生色"）加入，
当时没有同步注释，**基线注释本身就少算 1**。

本轮改动是在这个已经偏差的注释上做加法（17+1=18、4+1=5），而不是重新计数，
于是颜色一侧仍沿用偏差的 `13`（真实为 14，含 `PLAYER_CURRENT`），总数 `18` 也仍偏差。
本轮新增 `LIVE_LIST_SCRIM_OPACITY` 后实际为 **19 个槽位 = 14 颜色 + 5 不透明度**：

```
$ ThemeEditor.Slot 常量计数 = 19
PRIMARY,PRIMARY_CONTAINER,SECONDARY_CONTAINER,FOCUS,PLAYER_CURRENT,SURFACE,
SURFACE_CONTAINER,SURFACE_CONTAINER_HIGH,ON_SURFACE,ON_SURFACE_VARIANT,OUTLINE,
ERROR,SUCCESS,WARNING,SCRIM_OPACITY,DIALOG_OPACITY,OVERLAY_OPACITY,
PLAYER_SCRIM_OPACITY,LIVE_LIST_SCRIM_OPACITY          # 19
$ ThemeProfile.SlotSet 颜色字段 = 14，不透明度字段 = 5（按 SlotSet 花括号内 `public String/Float` 声明计数）
```

即改动后的注释 `19`/`14` 才与实际一致；原写法 `18`/`13` 各自差 1，是一种**在陈旧注释上做加法**
造成的失实描述（"18 槽 / 13 色 + 5 不透明度" 两项彼此也对不上：13+5=18 自洽，但与真实 14+5=19 不符）。
`ThemeRole` 的 `13 user-editable color slots` 是另一回事（`ThemeRole` 只有 13 个颜色角色 +
6 个前景派生角色，共 19，与其自身注释一致），不动。

修复（`app/src/main/java/com/fongmi/android/tv/theme/ThemeProfile.java`）：

- `:5` `18 user-editable semantic slots` → `19 user-editable semantic slots`
- `:79` `The 13 color slots plus 5 opacity slots` → `The 14 color slots plus 5 opacity slots`

修复后又与源码逐项核对：`ThemeProfile.SlotSet` 为 14 个 `String` 颜色字段 + 5 个 `Float` 不透明度字段 = 19，
与 `ThemeEditor.Slot` 的 19 个常量一一对应。

## 5. 验证

### 5.1 双 flavour 主源码编译

```
gradlew.bat :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac --console=plain
```

- 合并后（修订 F1 前）：**BUILD SUCCESSFUL in 9m 52s**（130 tasks，34 executed / 96 up-to-date），exit 0
- 修复 F1 后：**BUILD SUCCESSFUL in 41s**（58 tasks，4 executed / 54 up-to-date），exit 0

两个 flavour 的 `LiveActivity` 都成功解析 `com.fongmi.android.tv.theme.LiveListScrim`
（类缺失会在这一步编译失败），`ThemePreviewView.labelOf` 的穷举 switch 也是编译期门禁。

### 5.2 theme 包单测（双 flavour，修复前一次、修复后仍全绿）

```
gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*" \
             :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*" --console=plain
```

从 `app/build/test-results/` 的 XML 直接统计（不采信控制台摘要）。**修复 F1 后重跑一次**，
两轮结果逐项相同：

| 口径 | leanback | mobile | 合计 |
| --- | --- | --- | --- |
| 结果文件数 | 32 | 26 | 58 |
| tests | **224** | **208** | **432** |
| failures | **0** | **0** | **0** |
| errors | **0** | **0** | **0** |
| skipped | **0** | **0** | **0** |

修复后那一轮：`BUILD SUCCESSFUL in 2m 49s`（130 tasks，12 executed / 118 up-to-date），exit 0；
`LiveListScrimTest.xml` / `LiveListScrimWiringTest.xml` 的写入时间戳为修复后时间，证明确实重跑过
而不是读到了旧结果。

本次新增/改动用例的存在性与结果：

| 用例类 | leanback | mobile |
| --- | --- | --- |
| `LiveListScrimTest`（本次新增，7 例） | 7 tests / 0 failures | 7 tests / 0 failures |
| `LiveListScrimWiringTest`（本次新增，7 例） | 7 tests / 0 failures | 7 tests / 0 failures |
| `ThemeEditorPanelTest`（testLeanback 独有） | 在 leanback 结果集中，含新增滑块断言 | — |

### 5.3 工作区洁净

`git diff --check` → 空（exit 0），无空白/冲突残留；合并后 `git status --porcelain` 为空，
仅 F1 修复引入 `ThemeProfile.java` 一处 2 行注释修改。

## 6. 交付坐标

- 合并提交：`f6aea3c4b01de1842d6f78f2b6e764d7c241db5a`
- 本轮评审记录：`docs/C113-beta-merge-review-dev4-20261030.md`（本文件）
- 必修项修复：`app/src/main/java/com/fongmi/android/tv/theme/ThemeProfile.java`（注释 2 行）
- 评审提交 / recovery tag / 推送 / PR 号 / 拉取结果：见 §8 回填段（提交后追加，沿用
  C108/C111 的"文档只补已发生事实"惯例）

## 7. 既有限制（非本轮引入）

- `bash` 通道不可用 → `.codex/scripts/task_guard.sh` 无法启动，改用等效手工暂存 + `git tag -a`。
- `app/src/main/baseline-prof.txt` 未补 `LiveListScrim` 四条 profile 规则。该缺口已由功能任务文档
  记录为"性能基线维护、超出该任务 guard scope"；本轮评审**确认它不构成正确性缺陷**（缺失只影响
  ART baseline profile 命中率，不影响渲染结果），故不纳入本轮修复，避免扩大改动面。

## 8. 交付坐标回填（已发生的实际事实）

| 项 | 值 |
| --- | --- |
| 评审提交 | `8a093c73638466599f646edb85963e35077d672b`（2 files changed, +273 −2） |
| recovery tag | `recovery/C113-beta-merge-review-dev4-20261030/20261030-1240-8a093c73638`（指向上述提交） |
| 推送 | `git push origin dev4` → `553570b3a38..8a093c73638  dev4 -> dev4`，exit 0 |
| Pull Request | **#504** → `https://github.com/Silent1566/webhtv/pull/504`，`dev4` → `beta`，state OPEN，19 个文件 |
| 拉取远端最新 | `git fetch origin --prune` exit 0；`git pull --ff-only origin dev4` → **Already up to date**，exit 0 |
| 终态一致性 | `git rev-parse HEAD` = `git rev-parse origin/dev4` = `8a093c73638466599f646edb85963e35077d672b` |
| beta 祖先关系 | `git merge-base --is-ancestor 66b40a1338f HEAD` → **exit 0（是祖先）**；`origin/beta...HEAD` = **0 / 4** |
| 工作区 | `git status --porcelain` 为空 |

发布后再次核对合并完整性：`origin/beta` 已是 HEAD 祖先，落后 0 个提交；领先 4 个提交为
`8a093c73638`（评审）+ `f6aea3c4b01`（合并）+ `d039c39f331` + `bcdd344ec5c`（本轮评审的功能与文档两笔）。
