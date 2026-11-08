# TV-FOCUS-SCALE-20261012：同一个焦点缩放在 TV 端有四种数值与时长，且声明 token 无人读取

## Recovery anchor

- 目标：TV 焦点缩放收敛为**一套实现 + 一个时长来源**，倍率保留两个有据可依的档位；`ThemeTokens.focusScale` 成为唯一倍率来源并去掉重复声明。
- 允许路径：`app/src/main/res/values{,-night}/webhtv_tokens.xml`、`app/src/main/java/com/fongmi/android/tv/ui/helper/TvFocusScale.java`（新增）、`app/src/main/java/com/fongmi/android/tv/theme/ThemeTokens.java`、`HistoryAdapter`/`KeepAdapter`/`TmdbVideoPresenter`/`TmdbVideoAdapter`/`PanNetworkDiagnosticDialog`、`app/src/test/java/com/fongmi/android/tv/theme/ThemeWebBridgeTokenTest.java`、`app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvFocusScaleContractTest.java`（新增）、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `e7371abcb48`。
- 验收：① 五个焦点缩放站点全部委托 `TvFocusScale`，自身不再出现 `.scaleX(`/`.scaleY(`；② 常规档取 `ThemeTokens.focusScale()`、时长取 `@integer/webhtv_focus_scale_duration`；③ 紧凑倍率只允许出现在助手内且显式命名；④ 站点档位归属被测试钉住；⑤ 拖拽/画面手势站点不得引用助手；⑥ `focusScale` 与明暗无关且为 1.1；⑦ 死 dimen 已删除；⑧ 定向测试全绿 + 5561 真机焦点对照。
- 回滚：撤销本任务原子提交即可回到各站点写死 1.1/1.04/1.035 + 90/100/120/150ms 的状态；无数据迁移。
- 交付坐标：提交 `9f672ce2b24`（dev4），recovery tag `recovery/TV-FOCUS-SCALE-20261012/20261012155105`；未推送。
- 下一步唯一动作：无（TV-3 已交付）。下一批建议 TV-4（空态/加载/错误态），或处理 §7.3 提到的动效 token 族。

## 1. 现象与根因

TV-1 勘察时记录：同一个「聚焦」手势在不同列表里的放大效果不一致。本任务把范围查清后，实际是**四种写法**：

| 站点 | 倍率 | 时长 | 备注 |
|---|---:|---:|---|
| `HistoryAdapter`（最近观看/历史） | 1.1 | 150ms | |
| `KeepAdapter`（收藏） | 1.1 | 150ms | |
| `TmdbVideoPresenter`（相关视频 rail） | 1.04 | 120ms | 同时画前景焦点环 |
| `TmdbVideoAdapter`（详情页视频 rail） | 1.04 | 120ms | 同时画前景焦点环 |
| `PanNetworkDiagnosticDialog`（网络诊断面板） | 1.035 | 100ms | |
| `TmdbCardFocusHelper` / `TmdbEpisodeAdapter` | 不缩放 | — | **刻意**：改用焦点环 + 抬升 |

两个问题：

1. **同一效果四套数值、四种时长，且没有任何记录说明为什么不同。** 用户学到的是"聚焦有时放大、有时不放大、幅度还不一样"。
2. **声明的 token 是死代码。** `values/webhtv_tokens.xml` 与 `values-night/webhtv_tokens.xml` 各声明了一个 `<dimen name="webhtv_focus_scale">`（1.0 / 1.1），而**全仓库没有任何代码读取它**；真正被消费的是 `ThemeTokens.focusScale()`（经 `ThemeWebBridge` 暴露给网页层，网页层也未使用）。同一语义有两份声明、零个原生消费点。

## 2. 证据来源

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| 本仓库五个站点的源码 | HEAD `e7371abcb48` | A（本地） | 确认四套数值/时长；且都不读 token |
| 本仓库 `values{,-night}/webhtv_tokens.xml` | HEAD `e7371abcb48` | A（本地） | `webhtv_focus_scale` 仅被声明、无消费点（全仓库 grep 仅命中声明本身与测试里的 `focusScale` 字样） |
| 本仓库 `ThemeTokens.java` / `ThemeResolver.java` / `ThemeWebBridge.java` | HEAD `e7371abcb48` | A（本地） | 倍率的真实来源是 `ThemeTokens.focusScale()`；浅色表 1.0、深色表 1.1 |
| **反证**：`app/src/testMobile/java/.../TmdbDetailActivityLayoutTest.java` | HEAD `e7371abcb48` | A（本地） | 断言 `TmdbEpisodeAdapter` 必须**不**含 `FOCUS_SCALE` 且不调用 `scaleX/scaleY`，理由原文是「focused episode cards must avoid scale focus because **detail rows clip enlarged cards**」。**这条直接否定了"把所有站点统一到 1.1"的方案**——密集行的小倍率是为了避免裁切，不是随意漂移 |
| `app/src/test/java/.../theme/ThemeWebBridgeTokenTest.java` | HEAD `e7371abcb48` | A（本地） | 冻结了浅色快照的 `"focusScale":1.00`，是本任务需要显式更新的契约 |
| `app/src/testLeanback/java/.../TvFocusRingContractTest.java`（R2） | HEAD `e7371abcb48` | A（本地） | 已为「环宽」「环色」建立了"唯一来源 + 不得在 Java 写死"的范式；**缩放没有对应约束**，这正是缺口 |
| Android TV 按钮指南 / 设计文档 §5.2 | 沿用仓库既有记录（访问日期 2026-09-20，本次未重新抓取） | A（间接） | TV 焦点容器约 1.1x 缩放并保持内边距；`focusScale` 列为 1.1（TV） |

不适用类别记录：不涉及解码/渲染/ABI/打包/网络，无需上游播放器依赖类证据。

## 3. 方案比较与采用

| 方案 | 优点 | 缺点/风险 | 结论 |
|---|---|---|---|
| 1. 不变更 | 零风险 | 四种手感继续存在，token 继续是死代码 | 拒绝 |
| 2. 全部统一到 1.1 / 150ms | "一个值"最彻底 | **与反证冲突**：`TmdbEpisodeAdapter` 因"detail rows clip enlarged cards"而刻意不缩放，说明密集行的放大空间受限；把 rail 的 1.04 提到 1.1 会引入裁切回归 | 拒绝 |
| 3. **一套实现 + 一个时长 + 两个有据可依的档位（采用）** | 消除四套写法与死代码；保留密集宿主的必要克制，并把"为什么不同"从口头变成测试约束 | 仍是两个倍率 | **采用** |
| 4. 只收敛时长，倍率不动 | 改动最小 | 留下三份倍率字面量，问题 1 只解决一半 | 拒绝 |

采用方案 3 后的形态：

| 档位 | 倍率来源 | 时长来源 | 适用站点 |
|---|---|---|---|
| 常规 | `ThemeTokens.focusScale()` = **1.1** | `@integer/webhtv_focus_scale_duration` = **150ms** | `HistoryAdapter`、`KeepAdapter` |
| 紧凑 | `TvFocusScale.COMPACT_SCALE` = **1.04**（显式命名常量） | 同上 | `TmdbVideoPresenter`、`TmdbVideoAdapter`、`PanNetworkDiagnosticDialog` |

同时把 `focusScale` 改为**与明暗无关**（浅色表 1.0 → 1.1）：它是动效属性而不是配色属性；而各站点此前一律写死 1.1，若浅色表保持 1.0，改走 token 后浅色主题的电视会**丢掉**缩放反馈（真实回归）。取 1.1 既保住既有实机行为，也与设计文档 §5.2 一致。

## 4. 实施

1. 新增 `app/src/main/java/com/fongmi/android/tv/ui/helper/TvFocusScale.java`：`apply(view, focused)` 走主题倍率、`applyCompact(view, focused)` 走紧凑倍率、`reset(view)` 供复用清理；时长统一从资源读取；缩放/复位只有这一份实现。
2. `ThemeTokens.light()` 的 `focusScale` 由 `1.0f` 改为 `1.1f`（附推导注释）。
3. `values/webhtv_tokens.xml`：删除死 dimen `webhtv_focus_scale`，新增 `<integer name="webhtv_focus_scale_duration">150</integer>`（时长与明暗无关，故只声明在 `values/`）；`values-night/webhtv_tokens.xml` 同步删除该死 dimen。
4. 五个站点改为委托助手：`HistoryAdapter`、`KeepAdapter` 用 `apply`；`TmdbVideoPresenter`、`TmdbVideoAdapter`、`PanNetworkDiagnosticDialog` 用 `applyCompact`。`TmdbVideoPresenter.onUnbindViewHolder` 改用 `reset`。
5. `ThemeWebBridgeTokenTest` 的浅色快照断言 `1.00` → `1.10`。
6. 新增 `TvFocusScaleContractTest`（7 项）：助手是唯一实现与唯一字面量位置；五个站点全部委托且不再直接动缩放；**档位归属被钉住**；拖拽/画面手势站点不得引用助手；`focusScale` 与明暗无关且为 1.1；时长 token 存在且为正整数、死 dimen 已消失；无站点遗漏。

## 5. 不变量与回滚

- 不改动 `TmdbCardFocusHelper` / `TmdbEpisodeAdapter`：它们**刻意不缩放**（焦点环 + 抬升），是另一套已统一的机制，本任务明确排除。
- 不改动 `AudioMiniPlayer`（拖拽反馈 1.03/90ms、出现动画 0.94→1/180ms）与 `PlayerGesture`（画面手势缩放）：它们不是焦点缩放。
- 不改动焦点环的宽度、颜色、状态与抬升（`translationZ`）语义：`HistoryAdapter`/`KeepAdapter` 仍保留 `translationZ` 的 10f/0f。
- 不改动 `ThemeTokens` 的 49 项角色契约：只改了一个既有 float 分量的取值。
- 不改动播放器解码、渲染、字幕、音轨、网络与数据行为。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 6. 验证记录

### 6.1 定向 JVM 测试

- `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*" --tests "com.fongmi.android.tv.ui.activity.Tv*"` → 首轮 1 项失败：新增的 `theDurationTokenExistsOnceAndTheDeadDimenIsGone` 命中了自己写在 token 文件里的**说明注释**（注释里引用了 `<dimen name="webhtv_focus_scale">` 这个字符串）。改为先剥离 XML 注释再查；复跑 **BUILD SUCCESSFUL in 3m 49s**，全绿。
- 新增 `TvFocusScaleContractTest` 7 项：助手是唯一实现与唯一字面量位置；五个站点全部委托且不再直接动缩放（实测五站点 `.scaleX(`/`.scaleY(` 计数均为 **0**）；档位归属被钉住；拖拽/画面手势站点不得引用助手；`focusScale` 与明暗无关且为 1.1；时长 token 存在且为正整数、死 dimen 已消失；无站点遗漏。
- `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*" --tests "com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest"` → **BUILD SUCCESSFUL in 8m 44s**。本轮特意带上 `TmdbDetailActivityLayoutTest`，因为改动涉及它检查的 `TmdbVideoAdapter`，以及它记录的"密集行会裁切放大卡片"这条反证。
- `.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug` → **BUILD SUCCESSFUL in 2m 53s**。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。本次未新增裸十六进制。

### 6.2 设备实测（`192.168.50.3:5561`，API 28 / 1920×1080 / 280dpi）

| 检查项 | 结果 |
|---|---|
| 冷启动 | 正常进入首页，无崩溃 |
| 首页卡片聚焦 | 聚焦卡片明显放大并带焦点环（`TvFocusScale` 端到端可用） |
| **历史页聚焦** | 进入「历史」（`HistoryActivity`）后聚焦卡片同样放大并带环——这是本次**实际改动过**的站点（`HistoryAdapter`），确认实现替换没有破坏行为 |
| 焦点环 | 环宽/环色与改动前一致（本任务未触碰环机制） |

截图：`.design-evidence/e5561-tv3-focus.png`（首页聚焦）、`e5561-tv3-history.png`（历史页聚焦）、`e5561-tv3-home-focus.png`。

### 6.3 结论

焦点缩放收敛为一套实现 + 一个时长 + 两个有据可依的档位；死 token 已清除；`focusScale` 不再随明暗漂移；契约测试把"档位归属"和"哪些缩放不是焦点缩放"都变成了可执行约束。定向测试与手机端回归（含 TMDB 详情页那批断言）全绿。

### 6.4 残余风险与诚实说明

- **本任务的用户可见变化很小**：常规档本来就是 1.1/150ms，行为不变；实际变化是 TMDB 相关视频 rail 与网络诊断面板的时长由 120/100ms 统一到 150ms（±30–50ms），以及网络诊断面板倍率由 1.035 微调到 1.04。它的主要价值是**实现与来源的收敛**（消除四套写法与一个死 token），不是观感提升。
- 常规档与紧凑档的差异仍然存在（1.1 vs 1.04）。这是**刻意保留**的：反证表明密集行会裁切放大卡片。若将来详情页改成不裁切，可把密集档也提到常规档，届时只需删掉 `COMPACT_SCALE` 与契约测试里的档位断言。
- 紧凑倍率 `1.04` 仍写在 Java 里（助手内的显式命名常量）。它没有进 `ThemeTokens`：该记录已冻结 49 项角色契约，为一个非主题化取值扩容不划算。若将来它需要按主题调整，应移入 `ThemeTokens` 并同步契约测试。

## 7. 相邻发现（未纳入本批）

1. **`TmdbCardFocusHelper` / `TmdbEpisodeAdapter` 的"不缩放"是刻意设计，不是遗漏。** 它们用焦点环 + 8dp 抬升表达焦点，且 `TmdbDetailActivityLayoutTest` 已把"密集行会裁切放大卡片"钉住。本任务把这一点写进了 `TvFocusScale` 的类注释与契约测试，避免后人把它当成"还没统一"而误改。
2. **非焦点缩放仍是散落的字面量。** `AudioMiniPlayer` 的 `1.03f/90ms`（拖拽起手）与 `0.94f→1f/180ms`（出现动画）、`PlayerGesture` 的复位 `1.0f/250ms` 都还没有动效 token 族。若要继续收敛，建议单独立"动效 token"任务（时长/缓动/距离），不要混进焦点缩放。
3. **`ThemeWebBridge` 暴露的 `focusScale` 网页层并未消费**（`app/src/main/assets` 全量 grep 无命中）。本次因统一而把它的浅色取值从 1.00 改为 1.10；若将来网页层要真正使用它，需先确认 Web 侧的用法。
