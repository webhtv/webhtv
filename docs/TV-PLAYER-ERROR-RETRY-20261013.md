# TV-PLAYER-ERROR-RETRY-20261013：播放器错误层只有图标和文案，没有重试动作

## Recovery anchor

- 目标：按设计文档 §9.9 给播放器错误层补上「重试动作」，复用既有的 `onReset()` 而不是新写播放路径，并处理浮层与控制层的焦点归属。
- 允许路径：`app/src/leanback/res/layout/view_widget_vod.xml`、`app/src/leanback/java/.../activity/VideoActivity.java`、`app/src/testLeanback/java/.../ui/activity/TvPlayerErrorRetryContractTest.java`（新增）、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `c4b6a20811a`。
- 验收：① 错误层有可聚焦的重试按钮；② 焦点环取**播放器面板族**（`?attr/tvPlayerRing`）而非应用表面族；③ 显示错误时把焦点交给重试、隐藏时只在重试持有焦点的情况下交还视频面；④ 重试复用 `onReset()`；⑤ 契约测试全绿 + 真机端到端（制造真实失败 → 重试 → 恢复）。
- 回滚：撤销本任务原子提交即可回到「错误层只有图标与文案」；无数据迁移。
- 交付坐标：提交 `a2932aeac21`（dev4），recovery tag `recovery/TV-PLAYER-ERROR-RETRY-20261013/20261013095449`；未推送。
- 下一步唯一动作：无（本批已交付）。可选后续见 §6.4（自动换源竞态补测、全屏路径补测）。

## 1. 现象与根因

设计文档 §9.9 要求错误态有「重试动作」。改动前播放器的错误层（`view_widget_vod.xml` 的 `@id/error`）只有：

- 一个 56dp 警告图标
- 一行 16sp 文案（`showError(text)` 写入的失败原因）

**没有任何可操作的东西**。播放失败后用户唯一的办法是退出重进——而播放器其实**已经有**正确的动作：控制层的「重置」按钮（`control.action.reset` → `onReset()`）。

根因不是缺能力，是**错误层从来没有把已有能力暴露出来**：

1. 错误层是纯展示的浮层，没有任何 `focusable` 控件（`view_widget_vod.xml` 里 `android:focusable` 出现 0 次）。
2. 播放页平时把焦点放在视频面上（`mBinding.video.requestFocus()` 在文件里出现 6 处），所以即使加了按钮，不主动交焦点的话遥控也未必走得到。

## 2. 证据来源

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| `docs/webhtv-unified-visual-design-system-20260920.md` §9.9 | 仓库既有设计权威 | A（本地） | 错误态 = 错误色 + 原因 + 重试动作 |
| `view_widget_vod.xml` 的 `@id/error` 块 | HEAD `c4b6a20811a` | A（本地） | 只有图标 + 文案，无重试 |
| `VideoActivity.showError` / `hideError` | HEAD `c4b6a20811a` | A（本地） | 纯展示切换；`showError` 只有一个调用点（`onError`） |
| `VideoActivity.onReset()` | HEAD `c4b6a20811a` | A（本地） | `if (isReplay()) onReplay(); else onRefresh();` —— 应用自己的「重做本次播放」动作，且**已有控制层入口**（`control.action.reset`） |
| `VideoActivity` 的 `requestFocus()` 分布 | HEAD `c4b6a20811a` | A（本地） | 播放页把焦点放在视频面上（6 处），浮层按钮必须主动拿焦点 |
| `shape_widget.xml`（leanback） | HEAD `c4b6a20811a` | A（本地） | 错误面板是 `@color/black_40` 半透明深色、**两张表相同** → 属**固定深玻璃族** |
| `selector_control_sheet_button.xml` 的注释 | HEAD `c4b6a20811a` | A（本地） | 明确记录：固定玻璃上 `?attr/tvFocusRing`（浅色表）只有 1.83:1，必须用 `?attr/tvPlayerRing` |
| `showProgress()` | HEAD `c4b6a20811a` | A（本地） | 会顺带 `hideError()` → "重试成功 → 出现缓冲圈 → 错误层自动隐藏"这条链路是现成的 |

不适用类别记录：不改解码/渲染/ABI/打包；未改动播放内核选择与网络栈，因此无需上游播放器依赖类证据。

## 3. 方案比较与采用

| 方案 | 优点 | 缺点/风险 | 结论 |
|---|---|---|---|
| 1. 不变更 | 零风险 | §9.9 第三条继续不满足 | 拒绝 |
| 2. 重试直接调 `onRefresh()` | 语义就是"重新拉流" | 绕过了用户既有的「重置」设置（`isReplay()`），与该设置的预期不符 | 拒绝 |
| 3. **重试复用 `onReset()`（采用）** | 与控制层「重置」按钮**完全同一条路径**，尊重用户设置；不新写恢复逻辑 | 需要处理焦点归属 | **采用** |
| 4. 重试另写一套"重拉当前集"逻辑 | 可精细控制 | 会与既有的自动换源 `startFlow()` 形成**第二套恢复逻辑**，两者可能互相打断 | 拒绝 |
| 5. 焦点靠方向键自己走到 | 不抢焦点 | 播放页把焦点放在视频面上，浮层按钮不一定走得过去；电视上"焦点到不了"等于没有按钮 | 拒绝 |
| 6. **显示错误时主动交焦点、隐藏时安全交还（采用）** | 错误态下重试就是唯一该做的事，给焦点是正确的 UX | 需要避免抢走其它控件的焦点 | **采用**（交还时判断 `retry.hasFocus()`） |

### 3.1 焦点环必须取播放器面板族

错误面板背景是 `shape_widget`（`@color/black_40`，两张表相同）——**固定深玻璃族**。因此重试按钮的背景用既有的 `selector_control_sheet_button`（`?attr/tvPlayerRing` + `@dimen/webhtv_focus_ring_width`），**不能**复用空态那套 `selector_empty_action`（它取 `?attr/tvFocusRing`，浅色表在深玻璃上只有约 1.8:1，会看不见）。这条边界在 `selector_control_sheet_button.xml` 的注释里已有记录，本批只是遵守它，并由契约测试钉住。

## 4. 实施

1. `view_widget_vod.xml`（leanback）：在 `@id/error` 内新增 `@id/retry`（`focusable`/`clickable`，46dp 高、最小 140dp 宽、16sp，文案 `@string/error_retry`，背景 `@drawable/selector_control_sheet_button`）。
2. `VideoActivity`：
   - `showError(text)` 在显示浮层后 `mBinding.widget.retry.requestFocus()`；
   - `hideError()` 在隐藏后**仅当** `mBinding.widget.retry.hasFocus()` 时 `mBinding.video.requestFocus()`（否则会抢走剧集网格等控件的焦点）；
   - 在 `initEvent()` 里紧挨控制层那行接线：`mBinding.widget.retry.setOnClickListener(guarded(this::onReset));`——与控制层「重置」同一条路径。
3. 复用 TV-5 已加的 `@string/error_retry`，无新字符串。
4. 新增 `TvPlayerErrorRetryContractTest`（4 项）：错误层有可聚焦重试；环族是播放器面板族而非应用表面族；显示/隐藏的焦点处理；重试复用 `onReset()` 且 `onReset` 仍按 `isReplay()` 分流。

## 5. 不变量与回滚

- **不新写任何播放/恢复路径**：重试就是控制层「重置」按钮的同一个 `onReset()`。
- 不改动 `onError` 的既有清理（`player().reset()/stop()`、`applyHistoryPlayerKernel`、`startFlow()` 自动换源）。
- 不改动 `hideError` 被 `showProgress()` 顺带调用的时序。
- 不改动错误层的图标、文案、面板外观与 `0 KB/s` 速率显示。
- 不改动手机端（`app/src/mobile/res/layout/view_widget_vod.xml`）：`VideoActivity` 是 leanback 源集，只有它引用 `R.id.retry`。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 6. 验证记录

### 6.1 定向 JVM 测试

- `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.activity.Tv*" --tests "com.fongmi.android.tv.theme.*"` → 首轮 1 项失败：`retryUsesThePlayerPanelRingFamilyNotTheAppSurfaceOne` 命中了 `selector_control_sheet_button.xml` **注释里**"不能用 ?attr/tvFocusRing"这句说明文字。改为先剥离 XML 注释再查；复跑 **BUILD SUCCESSFUL in 1m 7s**。这已是本仓库第四次踩"负向断言命中说明文字"，各契约测试均已带 `withoutComments` / `withoutJavaComments`。
- 新增 `TvPlayerErrorRetryContractTest` 4 项全绿。
- `.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug` → **BUILD SUCCESSFUL in 3m 34s**。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。本次未新增裸 token 之外的色值（重试按钮复用既有 selector）。

### 6.2 设备实测（`192.168.50.3:5561`，API 28 / 1920×1080 / 280dpi）

**触发方式**：模拟器可 `adb root`，用 `iptables -I OUTPUT 1 -m owner --uid-owner 10097 -j REJECT` **只拦应用自身**的出网（不影响 adb），然后从详情页按「继续播放」——首次加载即失败，是**真实**的播放失败，不是伪造状态。验证后已 `-D` 移除，并确认 `iptables -L OUTPUT | grep -c REJECT` 为 **0**。

| 检查项 | 结果 |
|---|---|
| 错误层是否出现 | 是：警告图标 + 「播放地址加载失败」+ **「重试」按钮** + `0 KB/s` |
| 重试是否可聚焦 | **是，且自动获得焦点**——截图里按钮带明显焦点环，无需方向键寻找 |
| 焦点环是否可见 | 可见（浅色表下取 `?attr/tvPlayerRing`，在 `black_40` 面板上清晰） |
| 按重试是否真的恢复 | **是**：移除拦截后按确认键，错误层消失、画面恢复播放，时间轴回到 `30:16 / 36:30`（走了 `onRefresh()` 分支，即"重新拉流并续播"） |
| 恢复后焦点是否归还 | 是（浮层隐藏后焦点回到视频面，控制层按键正常） |
| 有无崩溃 | 无 |

截图：`.design-evidence/e5561-tv7-error.png`（错误层 + 重试焦点环）、`e5561-tv7-recovered.png`（重试后恢复播放）。

### 6.3 结论

播放器错误层首次具备 §9.9 要求的「重试动作」，且与控制层「重置」按钮共用同一条 `onReset()` 路径，没有引入第二套恢复逻辑。焦点归属在错误态下交给重试、恢复后安全交还视频面。

### 6.4 残余风险与诚实说明

- **与自动换源（`startFlow()`）的交互没有单独验证。** 本机这次失败后错误层是**持续显示**的（说明 `PlayerSetting.isAutoChange()` 未开启，或已无可用源），所以"自动换源进行中用户又按重试"这条竞态没有实测。设计上二者不会互相打断（重试走 `onReset()`，自动换源走 `startFlow()`，都由播放器自身串行处理），但这是**推理而非实测**。若要覆盖，需要在开启自动换源且存在多个可用源的配置下再验一次。
- 本次验证的是 `VideoActivity` 的**详情页视频预览面板**（同一个 `mBinding.widget` 错误层）。全屏播放路径用的是同一个 `showError`/`hideError`，但没有单独截图。
- 重试按钮在错误层里**始终可见**（不区分"还有其它源可换"）。若将来想让自动换源优先、把重试作为兜底，需要先读取 `PlayerSetting.isAutoChange()` 与站点可换源性——本批刻意不做这个判断，以免与自动换源逻辑耦合。
