# C42：dev2 合并远端 beta 最新代码（PR#411）并复评已修改代码

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev2`（**远端已移除/回退的提交不得顺带带回**）；复评 dev2 全部已修改代码（含已提交未推送的 `f77c4a0236`）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev2`、创建 `dev2 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并结果第二父为 `origin/beta` tip `9b168a1f38`；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev2 既有改动**零丢失**；④ 双 flavor Java 与 androidTest 编译通过；⑤ 全量 JVM 套件相对合并前基线**零新增回归**；⑥ UI token 门禁相对基线**零新增违规**；⑦ 净差异只含本分支自身改动；⑧ 提交 + recovery tag；⑨ `dev2` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并完成（0 冲突）；3 轮评审完成——第 1 轮发现并修复 1 处真实的加载圈泄漏，第 2 轮订正 1 处被钉死的写法型契约，第 3 轮通过；全部验证通过；待 `task_guard.sh finish`。
- **下一动作**：`task_guard.sh finish` → 推送 `dev2` → `gh pr create`（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始时 HEAD | `f77c4a02369279097df1f9eb23cc0b15f243a8cb`（`fix(tv): keep playback loading visible until new player starts`，领先 `origin/dev2` 2 个提交，其中 1 个是本分支自身未推送提交） |
| `origin/beta` tip | `9b168a1f38`（Merge PR #411 from dev1） |
| 合并基点 | `414babc1b5c9acae064a4cb6a35c689379155512`（Merge PR #410 from dev2） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`，由 task_guard `finish` 创建合并提交 |
| 合并结果 | 6 路径自动合入，**0 冲突、0 冲突标记**；合并树 `8d0126b93e503597e16427947567fb3ef6fb5744` 与 `git merge-tree` 预测**逐字节一致** |
| 回滚锚点 | `f77c4a02369279097df1f9eb23cc0b15f243a8cb` |
| 任务守卫 | `C42-beta-merge-review-dev2`（standard，scope `app` + `docs`） |

### beta 增量 ledger（4 个提交，全部纳入）

`git log --oneline dev2..origin/beta`：

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `9b168a1f38` | Merge PR #411 from dev1 | 纳入 |
| `92f9b020f2` | `docs(c41)`：订正净差异路径数为实测 6 并区分是否含本任务文档 | 纳入 |
| `8d03e86d82` | merge：合并 origin/beta（PR#410 真正打通 TV 浅色模式）并复评 | 纳入 |
| `5c95aa19cd` | `fix(detail)`：修复线路/选集 chip 无底色并恢复光影剧幕浅色白色中间层 | 纳入 |

**无一条与 dev2 既有实现重复或被取代**，故全部纳入。beta 增量内容为：`TmdbDetailActivity.setChipState` 改走 `setBackgroundTintList`、浅色光影剧幕恢复白色中间层（新增 `cinemaLightBackdropShade()`）、删除 `applyLightCinemaCopyPlate()` 与 `LightCinemaCopyPlateDrawable`、浅色剧幕文字色不再强制白字、`TmdbDetailChipFillTest`（leanback）、`TmdbCinemaLightReadabilityTest`（mobile）、`TmdbDetailActivityLayoutTest` 断言订正、C41 与 CINEMA-LIGHT-CHIP-FILL 两份文档。

6 个合入路径在合并结果中与 `9b168a1f38` **逐字节一致**：

```text
identical-to-beta  app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java
identical-to-beta  app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TmdbDetailChipFillTest.java
identical-to-beta  app/src/testMobile/java/com/fongmi/android/tv/ui/activity/TmdbCinemaLightReadabilityTest.java
identical-to-beta  app/src/testMobile/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivityLayoutTest.java
identical-to-beta  docs/C41-beta-merge-review-dev1-20261006.md
identical-to-beta  docs/CINEMA-LIGHT-CHIP-FILL-20261006.md
```

## 用户核心关注点：远端已移除（回退）内容零复活

**程序化校验方法**（按行比对，不依赖人工目测）：对 beta 历史上每个**单亲 revert 提交** `R` 取父 `R^`，得到 `removed(R,f) = lines(R^:f) − lines(R:f)`；复活定义为「该行出现在合并结果中，且合并前 dev2（`f77c4a0236`）中不存在」。

```text
single-parent revert commits on origin/beta : 18
list : 868e1a902, bdb2dfec3, a5aeb3de4, 25956ec50, c9219e49f, aeeb3d130,
       efdb09464, afe85eb9b, 5d4dcc756, 720851094, 64e83ddb1, dd19cadc1,
       40c2bfd9a, 8fd731ea5, fe8f6528f, dd1252b53, 9409b1a8c, d670b208c
file-versions checked    : 48
removed lines scanned    : 795
resurrected file-versions: 0
resurrected lines        : 0
```

**PR 方向反向校验**（关键：PR 的 base 是 beta，若合并结果里有 beta 已删除的行，PR 会把它们带回 beta）：由「净差异只含 dev2 自身 2 个路径」直接推出——合并结果中不存在任何 beta 相对 merge-base 删除的行。实测 `git diff --name-status origin/beta` 仅 2 路径，均为 dev2 自身改动。

## 评审循环记录

### 第 1 轮：发现 1 处真实缺陷（加载圈在「同一结果已在播」时永不消失）

**问题（F1）**：dev2 未推送提交 `f77c4a0236` 引入 `mPlaybackRequestActive` / `mPlaybackPlayerStarted` 守卫，用来阻止**陈旧的 READY 回调**提前收掉新一集的加载圈。但 `setPlayer(Result)` 的重复结果早退分支

```java
if (result == mAppliedPlayerResult && !player().isEmpty()) return;
```

在 `mPlaybackRequestActive = true` 尚未释放时直接返回。此时：

- 本次不会再走 `startPlayer()`，因此**不会有新的 READY 回调**来收圈；
- `onStateChanged(STATE_READY)` 被 `if (mPlaybackRequestActive && !mPlaybackPlayerStarted) break;` 挡下；
- `hidePlaybackProgressIfStale()`——那个专门用来兜底「画面在动、圈不走」的方法——被 `if (mPlaybackRequestActive && !mPlaybackPlayerStarted) return;` 挡下。

于是加载圈**没有任何清除路径**，两个守卫互相锁死。触发路径是真实的：`onReclaim()` 在重新获得播放归属时会把 `mViewModel.getPlayer().getValue()` 再喂给 `setPlayer()`，`SiteViewModel` 的 PLAYER `LiveData` 在回收/重入场景下会重投同一个结果对象；`consumeImmersiveAudioLaunch()` 也会在 `prepareImmersiveAudioPlayback` 之后重新投递同一结果。**这恰好是该兜底方法本来要防的那类故障，而且是由本次修复自身引入的。**

**修复**：在该早退分支内释放守卫并写明理由——此时播放器已在播同一结果，加载态本身是陈旧的。

```java
if (result == mAppliedPlayerResult && !player().isEmpty()) {
    // 同一个结果已经在播，本次不会再走 startPlayer，也就不会有新的 READY 回调来收圈。
    // 守卫若留在这里，onStateChanged 与 hidePlaybackProgressIfStale 都会被它挡下，
    // 圈再没有任何清除路径——正是那个兜底方法要防的「画面在动、圈不走」。
    // 此时播放器已在播同一结果，加载态本身是陈旧的，直接释放。
    mPlaybackRequestActive = false;
    mPlaybackPlayerStarted = false;
    return;
}
```

同步在 `VideoActivityLayoutTest.leanbackPlaybackLoadingIgnoresStaleReadyBeforeNewPlayerStarts` 内追加断言，把该分支纳入同一契约。

**反证（证明测试非空转）**：把该分支回退为修复前的单行写法后 `--rerun-tasks` 重跑：

```text
VideoActivityLayoutTest > leanbackPlaybackLoadingIgnoresStaleReadyBeforeNewPlayerStarts FAILED
154 tests completed, 1 failed / BUILD FAILED in 4m 46s
```

恢复修复后 `BUILD SUCCESSFUL`，154/154 通过。

### 第 2 轮：订正 1 处被钉死的写法型契约（F2）

第 1 轮的修复让共享契约测试 `ReaderPlaybackRoutingSourceTest.reclaimedOrRepeatedPlayerResultsAreNotStartedTwice` 失败：

```text
assertTrue(path + " must ignore a duplicate player result while playback remains active",
        source.contains("if (result == mAppliedPlayerResult && !player().isEmpty()) return;"));
```

该断言把**单行写法**本身当作契约，而单行写法正是 F1 的载体：leanback 侧必须在同一分支里顺带释放守卫，不可能再保持单行。断言的原意（「重复结果不得再起播一次」）依然成立，被钉死的只是大括号风格。

**修复**：改为语义断言——早退判定存在、真的 `return` 掉了、且 `mAppliedPlayerResult = result;` 在其之后（即未重复起播）；`mobile` 侧没有那套守卫状态，保持单行不受影响。注释说明两侧为何形态不同。

**反证**：删掉 mobile 侧的重复结果早退分支后重跑：

```text
ReaderPlaybackRoutingSourceTest > reclaimedOrRepeatedPlayerResultsAreNotStartedTwice FAILED
28 tests completed, 3 failed
```

证明订正后的断言仍然真的在守「不得重复起播」这件事。恢复后该用例通过（只剩 2 个 beta 基线既有失败）。

### 第 3 轮：复评通过

- **合并正确性**：0 冲突；6 路径 beta 增量与 `9b168a1f38` 逐字节一致；2 路径 dev2 增量与 `f77c4a0236` 逐字节一致；18 个 revert 零复活；无冲突标记；`git diff --check` 退出码 0；合并树与 `merge-tree` 预测一致。
- **beta 带入的 3 类改动复评**：
  1. **chip 底色改走 `setBackgroundTintList`**：全文件剩余 7 处 `setBackgroundColor` 调用点（`mNightModeOverlay`、`binding.root/hero/backdropFill/backdrop`、`image`、`content`）**没有一个是 `MaterialButton` 的 chip 填充**，因此无同类遗漏；同一页「第 N 季」按钮一直走 `applyEpisodeTitleButtonFocus` 的 tint 通道，正好解释它为何一直有底色。`TmdbDetailChipFillTest` 用**真实渲染像素**同时证明 tint 通道在背景重建后保留填充、`setBackgroundColor` 写法会掉到全透明——不是空转。
  2. **浅色光影剧幕白色中间层**：`cinemaLightBackdropShade()` 与历史实现 `7e42054deb^` **逐字节一致**（程序化比对）；`cinemaBackdropShade()` 是全文件唯一 shade 写入点；`TmdbCinemaLightReadabilityTest` **从生产源码读出真正发布的 alpha** 再按 WCAG 公式算对比度，因此调淡中间层会直接失败。文字色配对（`tintTmdbSectionTitles`、`personalAiReason`）均按 `isCinemaStyle() && !lightTheme` 判定，与白色中间层一一对应。
  3. **删除面干净**：`applyLightCinemaCopyPlate` / `LightCinemaCopyPlateDrawable` 全仓零引用；删除的 7 个 `android.graphics` import（`Canvas`/`LinearGradient`/`Paint`/`PorterDuff`/`PixelFormat`/`Shader`）全文件出现次数均为 **0**；`Rect`、`GradientDrawable`、`LayerDrawable` 仍在使用，未被误删。
- **dev2 未推送提交 `f77c4a0236` 复评**：修复后守卫的 4 个进入点（`onNewIntent`、`beginPlayerContentRequest`、`setPlayer` 成功路径、`setPlayer` 重复结果路径）与 3 个释放点（`onStateChanged` 消费、`onError`、`hidePlaybackProgressIfStale` 兜底）全部对齐，无残留泄漏；`hideSeekProgressIfReady` 未加守卫是**正确**的——它是 500ms 的 seek 收尾兜底，不是新一集请求的收圈路径。
- **净差异**：`git diff --name-status origin/beta` 全部为 dev2 自身改动，0 个 beta 内容被单方面改写。

## 验证记录汇总

| 验证项 | 命令/方法 | 结果 |
| --- | --- | --- |
| 冲突 | `git diff --name-only --diff-filter=U` / 冲突标记检索 | 0 / 0 |
| 合并树一致性 | `git merge-tree --write-tree` 与 `git write-tree` | `8d0126b93e503597e16427947567fb3ef6fb5744`，一致 |
| 回退内容零复活 | 18 revert × 48 文件版本 × 795 行，行级程序化比对 | **0 复活行 / 0 复活文件** |
| beta 增量零丢失 | 6 路径逐文件与 `9b168a1f38` 比对 | 全部字节一致 |
| dev2 既有零丢失 | 2 路径逐文件与 `f77c4a0236` 比对 | 全部字节一致 |
| 双 flavor Java 编译 | `:app:compile{Leanback,Mobile}Arm64_v8aDebugJavaWithJavac` | `BUILD SUCCESSFUL` |
| 双 flavor androidTest 编译 | `:app:compile{Leanback,Mobile}Arm64_v8aDebugAndroidTestJavaWithJavac` | `BUILD SUCCESSFUL` |
| 手机版全量单测 | `:app:testMobileArm64_v8aDebugUnitTest` | 5200 用例 / 6 失败（= 合并前基线 6 失败，**零新增回归**） |
| TV 版全量单测 | `:app:testLeanbackArm64_v8aDebugUnitTest` | 4351 用例 / 7 失败（= 合并前基线 7 失败，**零新增回归**） |
| 基线对照 | 独立工作树 `/f/temp/c42/beta-base` @ `f77c4a0236` 跑同一命令 | mobile 5195/6、leanback 4349/7，失败集合**完全相同** |
| 修复反证（F1） | 回退早退分支重跑 | `154 tests, 1 failed`（命中新断言） |
| 契约反证（F2） | 删除 mobile 侧早退分支重跑 | `reclaimedOrRepeatedPlayerResultsAreNotStartedTwice FAILED` |
| 定向契约测试 | `VideoActivityLayoutTest`、`TmdbCinemaLightReadabilityTest`、`TmdbDetailActivityLayoutTest`、`LeanbackForegroundContrastTest`、`TmdbDetailChipFillTest` | `BUILD SUCCESSFUL` |
| UI token 门禁 | `bash scripts/check_ui_tokens.sh --strict` | `violations=1 legacy=0`（唯一违规为既有 `mobile/item_following.xml`，相对基线零新增）；`UI_TOKEN_CONTRAST pairs=38 failures=0` |
| 空白检查 | `git diff --check` | 退出码 0 |

### 关于本机 6+7 个失败用例（**非本任务引入，不修**）

两端的失败集合与合并前 `f77c4a0236` **逐条相同**（`comm -13` 双向为空），且每个失败用例读取的目标文件（`values/styles.xml`、`assets/reader.html`、`assets/webhome/eclipse.html`、`PlaybackActivity.java`、`TmdbDetailActivity.java`、`BaseAlertDialog.java`、`VideoActivity.java`、`CollectActivity.java` 等）在 `HEAD` 与 `origin/beta` 之间**逐字节一致**（程序化核验，`identical-to-beta=True`）。根因是本机 `core.autocrlf=true`：这些断言写的是**多行字符串字面量**，而文件在检出后是 CRLF，程序化探针确认全部目标字符串 `has_LF=False / has_CRLF=True`。CI ubuntu（LF）下不触发。

`RealtimeSubtitleTranslatorTest` 的 2 个用例额外说明：该测试文件与 `origin/beta` **blob 哈希完全相同**（`57ca1dc54f`），失败发生在 6 个 Gradle 任务并行的重负载下（2s latch / 300ms 窗口的时序敏感断言），单独运行 `BUILD SUCCESSFUL`，干净重跑时 mobile 端也不再出现，属负载敏感 flake，不是回归。

## 最佳实践依据（本轮实际读取）

| 证据类 | 来源 | 级别与支持结论 | WebHTV 决策/限制 |
| --- | --- | --- | --- |
| 官方 API 文档 | [View.setBackgroundColor](https://developer.android.com/reference/android/view/View#setBackgroundColor(int)) / [MaterialButton.setBackgroundTintList](https://developer.android.com/reference/com/google/android/material/button/MaterialButton) | A；`setBackgroundColor` 设置的是**当前 Drawable 实例**的着色，`backgroundTint` 是 View 上的独立持久字段 | 直接解释 F1 之外的另一类缺陷（chip 掉色）：任何会重建背景的时机（inset/圆角/测量）都按 tint 字段重建，故填充必须走 tint 通道 |
| 官方规范 | [WCAG 2.2 1.4.3 Contrast (Minimum)](https://www.w3.org/TR/WCAG22/#contrast-minimum) | A；正文 4.5:1 | `TmdbCinemaLightReadabilityTest` 采用 4.5:1 作为浅色剧幕正文/小标题阈值 |
| 本仓既有契约 | `ReaderPlaybackRoutingSourceTest.reclaimedOrRepeatedPlayerResultsAreNotStartedTwice` | A；本仓已验证的「重复结果不得重复起播」口径 | 本轮订正只把**写法**换成**语义**，保留该口径本身 |
| 本仓既有契约 | `VideoActivityLayoutTest.leanbackPlaybackLoadingIgnoresStaleReadyBeforeNewPlayerStarts`、`hidePlaybackProgressIfStale()` 的既有注释 | A；本仓已验证的「加载圈必须有兜底收口」口径 | F1 正是用这条既有口径判定的：守卫不得取消兜底路径 |

不适用的证据类：无新增依赖/算法/原生库/性能敏感路径，故论文、benchmark、上游 revert 不适用。未取得且会改变决策的外部证据：无。

## 净差异（最终）

`git diff --name-status origin/beta` 全部为 dev2 自身改动，无 beta 内容被改写。相对合并前 `f77c4a0236`，本任务新增的改动为：

```text
M  app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java   （F1 修复：早退分支释放加载守卫）
M  app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityLayoutTest.java （F1 契约断言）
M  app/src/test/java/com/fongmi/android/tv/ui/activity/ReaderPlaybackRoutingSourceTest.java （F2 语义化订正）
A  docs/C42-beta-merge-review-dev2-20261007.md
```

其余 6 个路径均为合并 `origin/beta` 带入的 beta 内容。

## 回滚

revert 合并提交即回到 `f77c4a02369279097df1f9eb23cc0b15f243a8cb`（`setPlayer` 早退分支为单行、`ReaderPlaybackRoutingSourceTest` 仍钉死单行写法）。
