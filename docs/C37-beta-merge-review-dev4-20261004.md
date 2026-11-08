# C37：dev4 合并远端 beta 最新代码（PR#402/#403）并复评直播线路回退改动

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev4`（远端已移除/回退的提交不得顺带带回）；复评 dev4 全部已修改代码（含已提交未推送的 `96531595dc6` 直播线路回退修复）；发现问题修复并验证通过；循环评审直至通过后提交、推送 `dev4`、创建 `dev4 -> beta` 中文 PR（只创建，不合并）。
- **验收标准**：① 合并结果包含 `origin/beta` tip `e72239063b4`；② 远端被回退/剔除内容零复活；③ dev4 相对 beta 净差异仅含本分支自身改动（直播线路回退修复 + 本评审的修复 + 本文档）；④ 目标 JVM 测试与双 flavor 编译通过；⑤ 中文 PR 已创建且描述排版清楚。
- **当前状态**：合并、两轮评审、全部验证完成；净差异内的 24 个用例全通过，两 flavor 编译成功。
- **下一动作**：`task_guard.sh finish`（合并提交 + recovery tag）→ 推送 dev4 → 创建 PR（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev4` |
| 任务开始时 HEAD | `96531595dc66ce5dd02306b7b9f6cdc3a7b769d9`（领先 origin/dev4 1 个提交，未推送） |
| `origin/dev4` | `3400cd7b0abf64670c2c3fd85fa4818593d7c4af` |
| `origin/beta` tip | `e72239063b4122c5cfc3658231fed2b06798848a`（Merge PR #403 from dev3） |
| 合并基点 | `3400cd7b0abf64670c2c3fd85fa4818593d7c4af` |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`（由 task_guard finish 创建合并提交） |
| 合并结果 | 23 文件自动合入，0 冲突 |

beta 侧增量（`3400cd7b0ab..e72239063b4`，6 个提交）：`faac13dd64b`（Merge PR #402 from dev4）、`ef8bed60b56`（修复手机端默认对话框底色与文字对比度）、`c39e5748a5e`（Java 对话框前景色语义化 token，修复夜间模式）、`06f9af40377`（合并 origin/beta 最新代码，携带 dev3 未推送对比度改动）、`08d6cca51e9`（dev3 侧文档与实测对比度证据）、`e72239063b4`（Merge PR #403 from dev3）。

## 被回退/剔除内容核对（用户核心关注点）

1. 合并后树 vs `origin/beta` 全量文件级 diff = **恰好 7 个路径**，全部属于 dev4 自身改动（`96531595dc6` 的 6 个文件 + 本评审新增的修复与本文档），无任何 beta 侧内容被 dev4 单方面改写。
2. beta 上全部 **16 个 revert 提交**，涉及 **33 个文件**；与净差异的交集仅 `app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java` 一个路径，且被回退符号 `updateStatusBarInset` 在合并后源码中**不存在**（程序化检查：ABSENT），净差异中亦无任何 insets / statusBar 相关行 —— **被回退内容零复活**。
3. dev4 未推送提交 `96531595dc6` 只改动直播线路回退决策与相关测试/文档，与任何 revert 无实质交集。

## 评审记录

### 第 1 轮：dev4 未推送提交 `96531595dc6`（直播线路失效优先换线）

**改动意图**：修复"直播线路失效时不换线、反而跳到配置里其它直播接口"。做法是把播放失败与接口拉取失败解耦——`decideLineFailure` 只产出 `NEXT_LINE`，`decideSourceFailure` 只产出 `NEXT_SOURCE`；用 Activity 级 `mLineFallbackAnchor` + `mLineFallbackExhausted` 防止 A→B→A 死循环。

**本轮发现并修复的 3 个真实缺陷（均在 `leanback` 与 `mobile` 两个变体同时修复）**：

1. **缓冲超时路径被 `mFailedThisSession` 静默吞掉（回归风险）**。
   `mBufferingTimeout` 原为 `this::startFlow`；若仅把超时改走 `onError(msg)`，则当上一轮已换线（`mFailedThisSession=true`）后新线路一直卡在解析/缓冲、既不出画面也不报错时，`onError` 的 `if (!mFailedThisSession)` 守卫会直接吞掉这次超时 —— 用户看到进度条常驻、不再换线。
   **修复**：新增 `onBufferingTimeout()`，**不受** `mFailedThisSession` 限制，`if (!startFlow()) showError(...)`；超时路由改为 `mBufferingTimeout = this::onBufferingTimeout;`。

2. **回退耗尽保护被 reload 路径绕过（死循环复活）**。
   `handleSameReloadUrl` 直接调用 `advanceLineForFallback()`，而 exhausted 短路原先放在 `startFlow()` 内。三条线路 A/B/C 全部失败后 `mLineFallbackExhausted=true`、anchor 仍为 A；此时一次"同 URL 重载失败"会经 reload 路径再次 `switchLine(A→B)`，`index != anchor` 判定为成功并 `fetch()` —— A→B→C→A 循环被重新开启。
   **修复**：把 exhausted 短路下移到**唯一改动线路下标的** `advanceLineForFallback()` 入口（`if (isLineFallbackExhausted()) return false;`），使 `onError`、缓冲超时、reload 三条入口都无法重开新一轮。

3. **reload 换不动时无可见失败反馈（进度圈常驻）**。
   reload 路径在"单线路 / 已耗尽"时原先既不换线也不报错，`fetch()` 刚 `showProgress()` 的进度条会一直转、错误文案永不出现。
   **修复**：`if (advanceLineForFallback()) return; App.removeCallbacks(mBufferingTimeout); showError(msg);` —— 同时清掉挂起的超时回调，避免残留回调再次触发。

**同轮修正的契约冲突（测试侧）**：既有契约测试 `LiveActivitySourceFallbackSourceTest` 明确断言 `assertFalse(source.contains("if (mFailedThisSession) return;"))`。早期修复尝试使用该写法会直接打破该断言，故最终采用"保留守卫块 + 新增独立超时入口"的形式，两个契约同时成立。

**新增测试覆盖**：`LiveActivityLayoutTest.lineFallbackExhaustionKeepsErrorFeedbackForMobileAndLeanback`（同时断言 mobile 与 leanback 两变体源码），覆盖：① exhausted 短路位于换线下标唯一变更点；② `startFlow` 返回是否真正换线；③ `onError` 中 `showError` 先于 `startFlow`；④ 超时入口不经 `mFailedThisSession` 且换不动时可见报错；⑤ reload 换不动时可见报错，且不再重复实现换线前置条件。

### 第 2 轮：修复后复评（两变体逐路径确认）

对 `onError` / `onBufferingTimeout` / `handleSameReloadUrl` / `startFlow` / `advanceLineForFallback` 逐条比对 `leanback` 与 `mobile`，两者实现完全一致，且**每条失败路径都终止于可见反馈或一次真实换线**：

| 入口 | 换得动 | 换不动 |
| --- | --- | --- |
| `onError` | `showError` → `startFlow` → 换线 + `fetch()` | `showError` 后 `startFlow` 返回 false（静默停止，但已有可见错误） |
| `onBufferingTimeout` | `startFlow` → 换线 + `fetch()` | `showError(error_play_url)` |
| `handleSameReloadUrl` | 换线 + `fetch()` | 清超时回调 + `showError(msg)` |

`mFailedThisSession` 仅在 `STATE_BUFFERING` 与 `STATE_READY` 复位、在 `onError` 首次失败时置位；`resetLineFallback()` 在 `STATE_READY`、`setChannel`、`setLive`、`prevLine`、`nextLine` 调用，`mChannel` 赋值点（2 处）均已覆盖 —— 绕圈状态不会跨台/跨接口残留。结论：**通过**。

### 第 3 轮：beta 带入内容独立复评（PR#402/#403）

> beta 侧内容已由 dev3（C36-beta-merge-review-dev3）完整评审并入 beta；本轮为 dev4 侧独立增量复评，重点是 23 个路径与 dev4 自身改动的交互面。

- **Java 对话框前景色 token 化（`c39e5748a5e` + `ef8bed60b56`）**：`DebugLogDialog` / `MpvConfigDialog` / `PlaybackPerformanceDialog` 由硬编码颜色改为语义 token，配合 13 个 `dialog_*.xml` 与 2 个 drawable；新增 `DialogForegroundTokenTest`（3 用例）、扩写 `ThemeBaseWiringTest`（13 用例）与 `docs/ui-token-allowlist.txt` 白名单同步。与 dev4 侧直播线路回退（`LiveActivity` 播放状态机）**零文件交集**，无交互风险。✅
- **回归面**：这批改动为纯资源/颜色 token，不改播放器行为、不改持久化数据、不改 Channel bean。✅

## 验证记录

| 验证项 | 结果 |
| --- | --- |
| 双 flavor 编译（leanback/mobile arm64 debug JavaWithJavac） | BUILD SUCCESSFUL |
| `LiveActivityLayoutTest`（mobile flavor，含 leanback+mobile 双变体断言） | **10/10 通过** |
| `LiveSourceFallbackPolicyTest` | **9/9 通过** |
| `LiveActivitySourceFallbackSourceTest` | **5/5 通过** |
| leanback 变体 `LiveSourceFallbackPolicyTest` | **9/9 通过** |
| 定向合计（净差异内） | **24 用例，0 failures / 0 errors** |
| leanback `Live*` 全量 | 102 用例，0 失败 |
| mobile `Live*` 全量 | 118 用例，0 失败 |
| 合并树 vs origin/beta 净差异 | 恰好 7 路径 = dev4 自身改动 |
| revert 涉及 33 文件 vs beta | 交集 1 文件且被回退符号 ABSENT（无复活） |

### 既有失败归类（非本任务引入，仅记录不修）

mobile flavor 全量套件存在 8 个失败，全部为**本机 `core.autocrlf=true` 行尾环境下的多行文本断言脆弱**，且**失败类均不读取本任务修改的任何文件**（程序化检查：reads-LiveActivity-or-policy = 0）：

- `FollowingUiSourceTest`×2、`ReaderPlaybackRoutingSourceTest`×2、`TmdbSourceOnlyInteractionTest`、`DialogRoundedCornerSourceTest`、`WebThemeTokenSourceTest`、`PlayerPlaybackRegressionSourceTest`×1

逐项核对：这些测试读取的目标文件（如 `Func.java`、`reader.html`、`ui.css`、`shape_*.xml`）在工作区为 CRLF、在提交 blob 中为 LF，而测试未做行尾归一化；CI（ubuntu LF）下不触发。按 AGENTS.md 范围规则：既有问题不扩大修复面，仅报告。

> 注：净差异内的 `LiveActivitySourceFallbackSourceTest` 原先在本机也因同一原因失败（2 用例），因属**本任务净差异内**且阻塞验证，已按 C35 先例在测试侧单点归一化（`read(Path)` 内 `replace("\r\n","\n")`），修复后 5/5 通过。

## 提交与推送

- 本任务产物：merge commit（含本评审文档 + 第 1 轮 3 项修复 + 测试同步）+ recovery tag。
- PR：dev4 → beta，中文描述，只创建不合并。

## Next action

`task_guard.sh finish` → push dev4 → `gh pr create`（只创建不合并）。
