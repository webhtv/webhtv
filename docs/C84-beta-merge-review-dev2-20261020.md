# C84-beta-merge-review-dev2-20261020

## Recovery anchor

- Objective: 在 dev2 上合并 `origin/beta` 最新代码（远端已回退/删除的提交不得被顺带带回），复评本次合并带入的改动与本地 1 个已提交未推送的提交，循环评审-修复-验证直到通过，然后提交、推送并创建到 `beta` 的中文 PR（只创建、不合并）。
- Lane: `standard`。Scope: `app`、`docs`。
- Baseline: branch `dev2`，HEAD `ad7f5834de429a35fb729c27744110226d800c0c`。
- Protected dirty paths: `.wh_diag/**`（任务前即存在的 127 个未跟踪诊断文件，未纳入 scope，未提交）。
- Status: 合并完成、复评 2 轮并修正 1 项 blocker，验证全部通过，待提交。
- Next action: 无。

## 1. 合并对象

| 项 | 值 |
| --- | --- |
| 合并前 dev2 HEAD | `ad7f5834de429a35fb729c27744110226d800c0c` |
| `origin/beta` tip | `e0cb0ea2d53a48cb2ed463ba169e2712c405c238`（Merge PR #462 from dev4） |
| 合并基 | `51d805e4a8c93eec4367b231963a5ce4aeb00ed6`（`review(C82)`） |
| 合并方式 | `git merge --no-ff --no-commit origin/beta` → `Automatic merge went well`，**0 冲突** |
| 合并树（预测） | `git merge-tree --write-tree dev2 origin/beta` → `84797a91e2a6ee3e164151d62dfda51f7d225eab`，exit 0 |
| 合并提交 | `c5323524c1b3c2cb27314e7365abfefc5f726876`（双父 `ad7f5834de` + `e0cb0ea2d5`） |

beta 自合并基起 **12 个提交**，触及 **56 条路径**；dev2 自合并基起 **1 个提交**（`ad7f5834de`），触及 **13 条路径**。两侧路径集合**交集为空**（`comm -12` = 0），故 0 冲突是预期结果而非巧合。

- beta 侧 12 个提交：`e0cb0ea2d5` Merge PR #462 from dev4、`4076b78a11` docs(C83) 交付坐标、`c0b1ffd3c2` merge 合并 origin/beta 并复评 TV 焦点环改动、`1b35b6e3e8` docs(tv) 第二轮 container 配对交付坐标、`49b28bd91f` fix(tv) 主按钮聚焦态改用 container 配对、`0bb2601e38` Merge PR #461 from dev3、`0e81bae1c7` review(ui) 补全观影记录同步守卫契约并消除配平器静默失准、`4f3e942bb3` 记录 TV-FOCUS-RING-DIALOG-20261019 交付坐标、`f5d08659fc` 修复电视版版本更新弹窗的焦点环缺失与关于弹窗齿轮图标不可见、`93ea56a61d` Merge PR #460 from dev2、`b414bc3444` review(ui) 复评观影记录同步弹窗深色模式改动并加固守卫、`ad21137c0f` fix(ui) 观影记录同步弹窗深色模式改用语义 token。
- beta 侧 56 条路径（`git diff --name-only 51d805e4 origin/beta` 分类计数）= 37 个布局（main 26 / leanback 6 / mobile 5）+ 7 个 `res/color` + 2 个 `res/drawable` + `res/values/colors.xml` + 3 个 Java 弹窗 + 2 个测试 + 4 个 docs。
- dev2 侧 13 条路径 = 「切换线路不自动播放」开关（`FlagSwitchPlaybackPolicy` + `PlayerSetting` + 两 flavor `VideoActivity` + 两设置页 + 两布局 + 三语字符串 + 2 个测试）。

## 2. 零复活 / 零丢失证据（可证伪）

用户显式约束：「远端已经移除回退的提交不能再顺带提交上去」。方法沿用 C80/C82/C83，用**集合等价**代替逐层计数。

### L1 整树完整性（强判据）

```
git ls-tree -r --name-only origin/beta | sort > beta_tree
git ls-files | sort > idx_tree
comm -23 beta_tree idx_tree | wc -l   →  0
```

`beta files=5157  index files=5160  missing=0` —— 合并结果未丢失 beta 的任何一个路径；多出的 3 条是 dev2 侧新增文件。

### L2 集合等价（决定性，4 条命令全部 SETS-IDENTICAL）

| 判据 | 命令 | 结果 |
| --- | --- | --- |
| A | `git diff --name-only 51d805e4 ad7f5834de` | **13 条**（dev2 自有改动） |
| B | `git diff --cached --name-status origin/beta` | 恰为 A 的 13 条（含 3 个 A 新增） |
| C | `diff A B` | **SETS-IDENTICAL** |
| D | `git diff --name-only 51d805e4 origin/beta` | **56 条**（beta 自有改动） |
| E | `git diff --cached --name-only HEAD` | 恰为 D 的 56 条 |
| F | `diff D E` | **SETS-IDENTICAL** |

含义：合并结果相对 beta **只多出 dev2 自己的 13 条改动**（beta 侧文件内容零改动，`git diff --shortstat origin/beta` = `13 files changed, 299 insertions(+)`），相对 dev2 HEAD **只多出 beta 自己的 56 条改动**。两侧互为补集，且 `comm -12 A D` = **0**。

### L3 合并树与干净 ort 结果逐字节一致

`git merge-tree --write-tree HEAD $(cat .git/MERGE_HEAD)` = `84797a91e2a6ee3e164151d62dfda51f7d225eab`，`git write-tree`（索引树）= 同值 → 判定 **MERGE-RESULT-EXACT**，索引就是无人工干预的合并结果。

### L4 revert 集合交集

```
git log --oneline HEAD..origin/beta --grep="[Rr]evert"  →  空
```

本批 beta 12 个提交中**无 revert 主题提交**，故「revert 删除 ∧ 不在 beta 树 ∧ 在合并树」在数学上必然为 0。beta 历史中主题含 revert/回退/撤销 的提交（近 120 条）抽样核查 `b2bb4a249b`（`revert(lab): 弹窗按钮保留几何就近上行焦点`）与 `3eee5e8c9d`（`fix(mpv): 恢复电视端自动电视直出，移除遗留全局暂停开关`）**均已是 HEAD 祖先**（`git merge-base --is-ancestor` = in-HEAD），未被本次合并重新引入。

### 已知检测边界

L4 的主题行锚定不完备（纯描述式撤换标题会漏）；完备性由 L1 整树 + L2 集合等价承担——两者都是与 revert 语义无关的强判据。

## 3. 复评发现与修复

### 3.1 【blocker】beta 自带契约测试在 CRLF 检出下恒空转（本次合并引入的净新增失败）

**位置**：`app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvFocusRingContractTest.java:1392`（`focusedVersionCardKeepsItsTextReadable` 内）。

**根因**：该测试用**字面量缩进 + `\n`** 锚定版本卡的 header 容器：

```java
java.util.regex.Matcher headers = java.util.regex.Pattern
        .compile("                <androidx\\.appcompat\\.widget\\.LinearLayoutCompat\n(.*?)>",
                java.util.regex.Pattern.DOTALL)
        .matcher(body);
...
assertEquals(layout + " 必须有 stable/beta 两个版本卡 header 容器", 2, headerCount);
```

`read()` 读的是**工作区文件**，而本机 `core.autocrlf=true`（`.gitattributes` 只声明了 `*.patch`）会把签出的 XML 还原成 CRLF 行尾。于是 `\n` 前多出一个 `\r`，正则**永远匹配不到**，`headerCount` 恒为 0，测试以「找不到容器」而不是「容器缺状态」失败。

**实测证据**（同一份 blob，只差行尾）：

| 输入 | 行尾 | 匹配到的 header 数 |
| --- | --- | --- |
| `git cat-file blob :app/src/leanback/res/layout/dialog_update.xml` | LF | **2** |
| 工作区 `app/src/leanback/res/layout/dialog_update.xml` | CRLF | **0** |

**为什么它是本次合并引入的**：`git rev-parse` 证明测试文件与布局在合并树与 `origin/beta` 上**逐字节相同**（`1a581101f3` / `5e3b24f8...`），即缺陷源自 beta 而非本合并的解决动作；但本合并必须交付绿树，且该断言属**假失败**（与测试名声明的不变式不一致：它想验「header 缺 `duplicateParentState`」，实际报的是「header 不存在」）。

**修法**（容忍 CRLF，不动被测契约）：

```java
.compile("                <androidx\\.appcompat\\.widget\\.LinearLayoutCompat\r?\n(.*?)>",
        java.util.regex.Pattern.DOTALL)
```

并补注释说明「行尾必须容忍 CRLF」的原因，避免后人改回。

**验证**：

- 修复前：`:app:testLeanbackArm64_v8aDebugUnitTest --tests '*TvFocusRingContractTest*'` → `27 tests completed, 1 failed`。
- 修复后：同命令 → `tests=27 skipped=0 failures=0 errors=0`，`BUILD SUCCESSFUL in 44s`。
- 修复后两布局的 header 均被真正检查到且均带状态：leanback 与 mobile 各 `header 1 duplicateParentState: True` / `header 2 duplicateParentState: True`，`total = 2`。

### 3.2 变异测试（证明修复后的断言非空转）

| 变异 | 期望 | 实测 |
| --- | --- | --- |
| 删掉 leanback `dialog_update.xml` 第 1 个版本卡 header 容器的 `android:duplicateParentState="true"` | 转红 | `27 tests completed, 1 failed`，消息 `app/src/leanback/res/layout/dialog_update.xml 的版本卡 header 容器必须声明 android:duplicateParentState="true"，否则 Name/Expand 的文字色拿不到卡片焦点状态` ✅ |

变异后 `cp` 还原，`git status --porcelain` 对该文件为空（工作区与索引一致），确认逐字节还原。

### 3.3 同类模式全仓扫描（确认无第二处）

```
grep -rn 'compile("[^"]*\\n' --include=*.java app/src/test app/src/testLeanback app/src/testMobile
```

命中 2 处：本处（已修）与 `app/src/testMobile/.../HomeCategoryNavigationSourceTest.java:67` 的 `"new CustomRowPresenter\\([^\\n]*this::onContentHorizontalEdge\\)"`——后者的 `[^\n]*` 是**字符类里的否定**，CRLF 下 `\r` 属于被排除集合之外仍能匹配（`[^\n]` 接受 `\r`），不构成同类缺陷。

```
grep -rn 'compile(" \{8,\}' --include=*.java app/src/test app/src/testLeanback app/src/testMobile
```

仅命中本处（已修）。

### 3.4 复核后判定为**非缺陷**、按范围只报告不修

- **`nextFlag(int position)` 被标为 programmatic**：`grep -rn "nextFlag(" --include=*.java app/src` 证明其唯一调用者是**无参** `checkFlag()`，而后者只由 `startFlow()`（前置 `if (!PlayerSetting.isAutoChange()) return; if (!getSite().isChangeable()) return;`）与 `checkParse()` 触发——纯自动换源路径，**无用户主动入口**。标记正确。
- **`mDeferFlagSwitchPlayback` 泄漏**：两 flavor 各恰好 **5** 处出现（声明 / `onItemClick(Flag)` 置位 / 同方法末尾无条件复位 / `onRefresh()` 的守卫读取 / 守卫内消费复位）；`onRefresh()` 的两个早退分支（flag/episode 适配器为空）在消费前返回，但方法末尾的复位兜底，无泄漏。
- **`onRefresh()` 守卫越界**：`onRefresh()` 有 **10**（leanback）/ **12**（mobile）个真实调用点（不含定义行与注释），守卫只在 `mDeferFlagSwitchPlayback == true` 时生效，而该标记仅在「手动切线路 + 非首屏绑定 + 开关开启」路径被置位并在同方法内复位，其余调用点读到 false。
- **`player_auto_play` 同族先例**：新键 `player_defer_flag_switch` 未登记进 `Backup.APP_PREFS`，但同族的 `player_auto_play`、`player_remember_brightness`、`player_brightness` 同样未登记（`grep -c` 分别为 0 / 0 / 0），且 `BackupPreferenceFilterTest` 无「白名单覆盖全部 PlayerSetting 键」的全局断言 → 与既有惯例一致，非本次引入。
- **import 未按字母序**：`FlagSwitchPlaybackPolicy` 被插在 `PlayerSetting` 与 `Setting` 之间。该 import 块本身已非字母序（`PlayerSetting` → `Setting` → `SiteHealthStore` → `DetailRuntimeModePolicy` → `TmdbSitePolicy`），且仓库无 checkstyle/spotless（`grep checkstyle|spotless|ImportOrder` 在 `build.gradle`/`app/build.gradle` 无命中，`config/` 目录不存在）→ 低危风格问题，不修。
- **`mProgrammaticFlagSwitch` 非异常安全**：`onItemClick(Flag)` 抛异常会残留 true。但该字段的三处调用点都是主线程同步三段式（置位 → 调用 → 复位），且 `onItemClick` 内的前置早退发生在读取该字段之前；仓库既有代码普遍采用同种写法 → 按范围只报告不修。
- **`docs/` 缺失 `TV-FLAG-SWITCH-DEFER-20261019` 任务文档**：`ad7f5834de` 的 trailer 引用了该 Task-Guard id，但 `docs/` 下无对应文件。该提交的 guard session（`.codex/task-state/finished-20261019112348-TV-FLAG-SWITCH-DEFER-20261019/`）与 recovery tag（`recovery/TV-FLAG-SWITCH-DEFER-20261019/20261019112347-ad7f5834de42`）均已完整落盘，属**文档缺口**而非功能缺陷；本任务文档即为该改动的复评记录，不再另建平行文档。
- **beta 侧 56 条改动**：已在 `docs/C83-beta-merge-review-dev4-20261019.md` 复评 2 轮（修正 1 项 blocker + 2 处文档/注释失真），本次合并为纯并集（L2 交集为 0），不重复评审。

## 4. 验证

执行环境：2026-10-20 Asia/Shanghai，Windows，`gradlew.bat --no-daemon --no-build-cache`（构建缓存被另一 workspace `F:/Workspace/webhtv` 长期持有，用 `--no-build-cache` 规避锁竞争；非代码问题）。`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`。

### 4.1 定向测试（合并树 + 本任务修复）

```
./gradlew.bat --no-daemon --no-build-cache :app:testLeanbackArm64_v8aDebugUnitTest \
  --tests '*TvFocusRingContractTest*' --tests '*FlagSwitchPlayback*' \
  --tests '*DialogForegroundTokenTest*' --tests '*ThemeBinderContractTest*'
```

→ `BUILD SUCCESSFUL in 51s`。逐类计数：

| 测试类 | tests | skipped | failures | errors |
| --- | --- | --- | --- | --- |
| `FlagSwitchPlaybackPolicyTest` | 2 | 0 | 0 | 0 |
| `FlagSwitchPlaybackWiringTest` | 6 | 0 | 0 | 0 |
| `TvFocusRingContractTest` | 27 | 0 | 0 | 0 |
| `DialogForegroundTokenTest` | 5 | 0 | 0 | 0 |

### 4.2 全量双 flavor（修复前后对照）

| 目标 | 修复前 | 修复后 | 既有基线 | 结论 |
| --- | --- | --- | --- | --- |
| `:app:testLeanbackArm64_v8aDebugUnitTest` | 4419 tests / **9 failed** | 4419 tests / **8 failed** / 3 skipped | 8（C82 记录） | **零新增失败** |
| `:app:testMobileArm64_v8aDebugUnitTest` | 5203 tests / 6 failed | 5203 tests / **6 failed** / 3 skipped | 6（C82 记录） | **零新增失败** |

修复前 leanback 的第 9 项即 3.1；修复后失败集与 C82 基线**逐条一致**：`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`、`ReaderPlaybackRoutingSourceTest`×2、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`（mobile 6 项）+ leanback 额外 `NativeEnhancedPlaybackStyleFocusTest`、`SearchResultDownFocusTest`。

这些既有失败均为 Windows CRLF 检出产物：`file` 显示工作区同名文件为 `CRLF line terminators`，而 `git cat-file blob HEAD:<path>` 为 LF（`Java source, ASCII text`），即断言 needle 在 blob 中成立、在 CRLF 工作区不成立，与本任务净差异无交集。

### 4.3 静态门槛

- `bash scripts/check_ui_tokens.sh` → `UI_TOKEN_BASELINE layouts=385 hex_layouts=0 drawables=562 hex_drawables=0 colors=64 hex_colors=0 allowlisted=190` / `UI_TOKEN_SCOPE stage=A violations=0 legacy=0` / `UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28` / `UI_TOKEN_STATUS PASS`（exit 0）。
- `git diff --check` 为空。
- 冲突标记扫描 `git diff origin/beta | grep -cE '^\+(<<<<<<<|=======|>>>>>>>)'` = **0**。
- 净差异 `git diff --shortstat origin/beta` = `15 files changed, 304 insertions(+), 1 deletion(-)`（13 条 dev2 未推送改动 + 本任务的 1 行测试修复 + 1 行评估索引 + 新增任务文档）。

## 5. 影响与风险

- 生产代码**零改动**：本任务只改 1 个测试文件里 1 行正则 + 4 行注释。无 Java 生产代码、无资源、无 ABI、无 API、无依赖锁、无补丁、无 native 资产、无包体积影响。
- 行为变化**仅限测试**：`TvFocusRingContractTest.focusedVersionCardKeepsItsTextReadable` 在 CRLF 检出下由「恒失败」变为「真正校验 header 容器」。LF 检出（CI/Linux）下行为不变——`\r?` 对 LF 输入是可选的零宽匹配。
- 合并本身是纯并集（0 冲突、两侧路径零重叠、`comm -12` = 0），不改变 beta 侧任何文件的内容：相对 beta 的差异恰为 dev2 自己的 13 条改动。
- 残留风险：`\r?\n` 只覆盖 CRLF/LF 两种行尾；若将来出现 CR-only（经典 Mac）检出，该正则仍会失配。当前仓库无此场景（`.gitattributes` 只声明 `*.patch`，全仓无 CR-only 文件），按范围不做额外加固。

## 6. 回滚

- 合并本身：`git revert -m 1 c5323524c1b3c2cb27314e7365abfefc5f726876`。
- 3.1 修复：`git revert <本任务提交>`（单文件单行，无持久化 schema）。

## 7. 交付坐标

- 合并提交：`c5323524c1b3c2cb27314e7365abfefc5f726876`（双父：`ad7f5834de429a35fb729c27744110226d800c0c` + `e0cb0ea2d53a48cb2ed463ba169e2712c405c238`）。
- 复评修复提交：`70a3fe757fb8ae78866d35e0b9a0ec7389d7c6f0`（3 files changed, 208 insertions(+), 1 deletion(-)）。
- recovery tag：`recovery/C84-beta-merge-review-dev2-20261020/20261020034215-70a3fe757fb8`。
- 分支 `dev2` 已推送（`51d805e4a8..70a3fe757f`）。
- PR [#464](https://github.com/Silent1566/webhtv/pull/464) → `beta`，`state=OPEN`、`mergedAt=null`、`mergeable=MERGEABLE`、`changedFiles=16`（+507 / -1），**只创建、不合并**。
- PR 文件集与 `git diff --name-only origin/beta origin/dev2` 逐项一致（16/16）。
