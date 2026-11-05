# C117-beta-merge-review-dev3-20261105

## Recovery anchor

- **目标**：合并 `origin/beta` 最新代码后，评审 `dev3` 上「已修改的代码」（含已提交未推送）；有必修项则修复并验证，
  通过后再次评审，循环直到通过；然后提交、推送 `dev3`、创建到 `beta` 的中文 PR，最后拉取远端最新代码。
- **验收标准**：
  1. `dev3` 相对 `origin/beta` 落后归零（`origin/beta` 是 HEAD 的祖先），或明确证明无需再合并。
  2. 评审覆盖本轮全部净差异；每条结论有可复核的 `路径:行号` 证据。
  3. 每个必修项均已修复并配「会变红」的守卫；无必修项时须用变异验证证明现有守卫非空转。
  4. 定向单测 0 失败；工作区除初始脏项外无残留改动。
  5. 改动经任务守卫 `finish` 落盘并打 recovery tag；推送 `dev3`；创建到 `beta` 的中文 PR；随后拉取远端最新代码。
- **Lane**：`quick-fix`（`app/.../TmdbDetailActivity.java`、`app/src/testMobile/.../TmdbDetailActivityLayoutTest.java`、本文件）
- **任务守卫**：`C117-beta-merge-review-dev3-20261105`，base HEAD `842c03a0e1370eac77ccd98a084d7d09357082cb`，branch `dev3`
- **当前状态**：评审通过（无必修项），遗留项见 §6

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 合并目标 | `origin/beta` = `842c03a0e1370eac77ccd98a084d7d09357082cb`（"Merge pull request #525 from Silent1566/dev2"，2026-11-05） |
| 本次评审基线 | 合并前 `dev3` HEAD = `6e23ec560de5d5b87e1141bf9e3295fbb8579e49` |
| 合并前祖先关系 | `git rev-list --left-right --count origin/beta...HEAD` = **14 / 0** → 可 `--ff-only` |
| 合并动作 | `git merge --ff-only origin/beta` → `Updating 6e23ec560d..842c03a0e1`，13 files changed, 878 insertions(+), 20 deletions(-) |
| 合并后落后 / 领先 | `origin/beta...HEAD` = **0 / 0**；`origin/dev3...HEAD` = 0 / 11 |
| 冲突 | **无**。合并涉及的 13 个文件不包含 `TmdbDetailActivity.java`，工作区两处未提交改动原样保留 |
| 合并顺带结果 | `origin/dev3` 领先的 3 个提交（`e370242207` / `f6ab19f76a` / `dd12105a68`，IJK surface rebind）本就包含在 `beta` 中，本次 `--ff-only` 一并吸收，落后归零 |

合入的 11 个 beta 独有提交均属其他开发分支已评审推送的 IJK HLS AES 解密 / surface rebind / Exo EXCEEDS 能力，
按用户本轮裁定的范围只做合并动作与交互检查（与内嵌全屏方向链路无交集，见 §4）。

### 1.1 为什么不能沿用上一轮评审跳过

用户给出的跳过前提是「之前已经评审过且没有合入冲突」。实测该前提**不成立**：

- `git log --oneline origin/beta..HEAD`（合并前）**为空** → 本轮没有「已提交未推送」的提交；
- 全部待评审内容就是**工作区两处未提交修改**：
  `app/src/main/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivity.java`（+20/-1）与
  `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/TmdbDetailActivityLayoutTest.java`（+25）；
- 对 `docs/` 全量 grep `inlineManualOrientation` / `rotateInlineFullscreen` → **0 命中**；
  上一轮 `docs/C115-beta-merge-review-dev3-20261031.md` 的评审对象是 `d1686cb44d`（TV 倍速 UP 键），与本轮无关。

⇒ 本轮必须评审这两处未提交修改。

---

## 2. 评审范围与结论

| 文件 | 改动 | 结论 |
| --- | --- | --- |
| `TmdbDetailActivity.java` | 新增字段 `inlineManualOrientation`（:428）+ 5 处使用（:10318、:10333-10334、:10344、:10415、:10634） | 通过 |
| `TmdbDetailActivityLayoutTest.java` | 新增 `detailInlineRotateKeepsTheOrientationTheUserChose`，5 条源码断言 | 通过（变异验证见 §5） |

**被修复的用户可见缺陷**：内嵌全屏里点「旋转」第一次完全没反应，要点第二次才转。
根因是 `rotateInlineFullscreen()`（:10327）设置方向后，紧随的 `VideoSize` / `STATE_READY` 回调走
`applyInlineShortDramaMode()`（:10372）→ `setInlineFullscreenOrientation()`（:10413）按视频形态**重算**方向，
把刚转过去的方向改回原方向。新增标记让「用户已表态」优先于自动重算。

---

## 3. 与既有实现的语义对照（不是新发明）

注释声称「与 `VideoActivity.manualRotate` 同一语义」。全仓 grep `manualRotate` → 仅 `app/src/mobile/java/.../VideoActivity.java`（mobile 源集），
三处对照**全部成立**，新实现与既有移动端播放器一致：

| 语义 | mobile `VideoActivity` | 本次 `TmdbDetailActivity` |
| --- | --- | --- |
| 用户点击时置位 | `:3991` `manualRotate = true;`（置于 `setRequestedOrientation` 之前，:3992） | `:10333` 置位，`:10334` 请求方向（顺序相同） |
| 自动重算被抑制 | `:8307` `if (manualRotate) return;`（`checkOrientation`） | `:10415` `if (inlineManualOrientation != …) return;`（`setInlineFullscreenOrientation`） |
| 进全屏重建基线 | `:5516` `manualRotate = false;` | `:10344` 清零（`enterInlineFullscreen`） |
| 退全屏结束覆盖 | `:5560` `manualRotate = false;` | `:10634` 清零（`exitInlineFullscreen`） |

---

## 4. 「谁还能覆盖用户选择的方向」全路径排查

内嵌全屏方向只有 4 个写入点，逐条核实后**没有绕开标记的路径**：

1. `:10417` `setRequestedOrientation(orientation)`（自动重算）—— 已被 `:10415` 早返回拦截。
2. `:10334` 旋转按钮 —— 就是用户意图本身；`:10328` 的 `isLock()` 门禁保证锁屏时不可达。
3. `:10316` 锁屏 —— `PlaybackOrientation.getScreenOrientation(this)`（`app/src/main/java/com/fongmi/android/tv/playback/PlaybackOrientation.java:12-18`）
   返回的是「当前实际方向的固化值」（PORTRAIT / REVERSE_LANDSCAPE / REVERSE_PORTRAIT），属加锁动作；
   解锁分支 `:10318-10320` 已优先恢复 `inlineManualOrientation`，`else` 才回落到自动重算（:10322）。
4. `:10631` 退出内嵌全屏 —— `setRequestedOrientation(requestedOrientation)` 恢复**进入前**记录的原始方向；
   清零放在恢复之后（:10633-10634），不影响恢复值。

另外两条「可能插入的钩子」也已排除：

- `onConfigurationChanged`（:11357-11363）只做侧边间距、集数布局与 `applyResizeMode`，**不触碰方向**。
- `AndroidManifest.xml` 的 7 个播放 Activity 都带 `android:configChanges="…|orientation|screenSize|…"`（:96 起），
  旋转不重建 Activity ⇒ 新增字段不会在方向变化时丢失。

切集链路：`startInlinePlayback`（:11321 / :11334）→ `applyInlineShortDramaMode` → `setInlineFullscreenOrientation`，
在标记有效时保持用户选择，且该路径**不会**重新 `enterInlineFullscreen`（`inlineFullscreen` 已为 true，:10378 的门禁不触发）
⇒ 切集不会误清标记。

---

## 5. 测试质量与变异验证

新增测试 `detailInlineRotateKeepsTheOrientationTheUserChose` 按本仓库既有风格做**源码契约断言**，
用 `javaBlockAt` 按方法名切块、再分别断言，5 条各自锁一个语义（置位先于请求 / 自动重算被抑制 / 解锁走 `else if` 分支 /
进全屏清基线 / 退全屏清覆盖）。

- **正向验证**：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest' --no-daemon`
  → `BUILD SUCCESSFUL in 6m 21s`。
- **变异验证**（证明守卫非空转）：临时删除 `setInlineFullscreenOrientation()` 中的
  `if (inlineManualOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return;`（:10415），
  复跑同一命令 → `TmdbDetailActivityLayoutTest > detailInlineRotateKeepsTheOrientationTheUserChose FAILED`，
  `BUILD FAILED in 1m 33s`；恢复该行后 `git diff --stat` 回到 `20 insertions(+), 1 deletion(-)`，
  复跑 → `BUILD SUCCESSFUL in 2m 27s`。

---

## 6. 遗留项（经评估：不构成回归，仅记录）

1. **短剧形态下保留手动方向、但旋转按钮同时隐藏**：`:8428` 的按钮可见性含 `!inlineShortDramaMode`。
   用户先手动转横屏、随后视频被判为短剧源时，方向被标记压制（不再被自动改成竖屏），而按钮已隐藏，
   要回到自动方向需退出再进内嵌全屏（进出都会清零标记，:10344 / :10634）。
   **与改动前对比不劣化**：改动前同样隐藏按钮，只是方向会被自动改成竖屏；因此不作为必修项，也不扩大本次范围。
2. **`rotateInlineFullscreen` 用 `ResUtil.isLand(this)` 判定当前方向**（:10333，原有实现）：该值取自 `Configuration`，
   在方向动画尚未落地的极小窗口内可能重复请求同一方向。非本次引入，标记机制已消除主要症状。
3. **注释引用跨源集符号**：`{@code VideoActivity.manualRotate}` 位于 mobile 源集，`main` 源集内无同名类；
   `{@code}` 本就不做链接解析，编译与可读性不受影响。

---

## 7. 交付坐标

| 项 | 值 |
| --- | --- |
| 合并提交 | `842c03a0e1370eac77ccd98a084d7d09357082cb`（`git merge --ff-only origin/beta`） |
| 评审净差异 | `git diff --stat`：2 files changed, 44 insertions(+), 1 deletion(-) |
| 验证 | 定向单测 `TmdbDetailActivityLayoutTest` 通过（含变异验证） |
| 任务守卫 | `C117-beta-merge-review-dev3-20261105` / `quick-fix` |
