# TV-ERROR-STATE-20261012：TV 端没有任何错误态——失败只弹一个会消失的吐司

## Recovery anchor

- 目标：TV 端按设计文档 §9.9 具备「错误色 + 原因 + 重试动作」的错误态；新增与壁纸同族的 palette-independent 错误前景色并如实记录其实测对比度；设置页内联错误与正常取值可区分且能复位。
- 允许路径：`app/src/main/res/values{,-night}/webhtv_tokens.xml`、`values/webhtv_attrs.xml`、`values/webhtv_styles.xml`、`{leanback,mobile}/res/layout/view_empty.xml`、`app/src/main/java/com/fongmi/android/tv/ui/custom/ProgressLayout.java`、`app/src/leanback/java/.../activity/{HomeActivity,SettingEnhanceActivity,SettingAdActivity}.java`、三语 strings、`app/src/test/java/.../theme/ThemeContractTest.java`、`app/src/testLeanback/java/.../ui/activity/TvErrorStateContractTest.java`（新增）、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `d90c5473835`。
- 验收：① 错误角色两表同值、独立 attr、不复用 `colorError`；② 大字号门槛 3:1 在最不利实测底材上成立；③ 正文对比度缺口被如实钉住而非掩盖；④ `ProgressLayout` 有 ERROR 态与重试、并隐藏空态插画；⑤ 首页配置失败进入错误态且重试真的重新加载；⑥ 设置页内联错误可区分且成功时复位；⑦ 定向测试全绿 + 真机对照。
- 回滚：撤销本任务原子提交即可回到「失败只弹吐司 + 无错误态」；无数据迁移。
- 交付坐标：提交 `f8388701349`（dev4），recovery tag `recovery/TV-ERROR-STATE-20261012/20261013002710`；未推送。
- 下一步唯一动作：无（TV-5 已交付）。后续候选见 §7（错误正文的底材改造、播放器错误层重试、其余宿主的错误态接入）。

## 1. 现象与根因

设计文档 §9.9 的第三条：

> 错误态：**错误色 + 原因 + 重试动作**；错误详情可折叠，避免撑破弹窗。

而 TV 端**一条都不满足**，且根本没有"错误态"这个东西：

| 场景 | 改动前 | 违反 |
|---|---|---|
| 首页内容源加载失败 | `Notify.show(msg)` 弹一个会消失的吐司，然后 `showContent()` → 一个没有任何内容源的空首页 | 没有错误色、原因转瞬即逝、**没有重试入口** |
| 设置页取值读取失败 | `setError(view)` 把 `error_config_get` 写进普通的取值 TextView | 与正常值**颜色完全相同**，用户看不出这是错误 |
| 播放失败 | 播放器内嵌错误层 + 吐司 | 有原因，无重试动作 |

**根因**有两条：

1. 状态容器 `ProgressLayout` 只有 `CONTENT / PROGRESS / EMPTY` 三态，**没有 ERROR 态**，宿主想表达错误也没有地方表达。
2. 没有"壁纸上的错误前景色"这个角色。直接用 `?attr/colorError` 不行——它是**跟随调色板**的角色（阶段 M 已记录过这一类陷阱），浅色表的深红压在深色壁纸上根本看不见。实测见 §2。

## 2. 证据来源

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| 本仓库 `docs/webhtv-unified-visual-design-system-20260920.md` §9.9（第 729–734 行） | 仓库既有设计权威 | A（本地） | 错误态 = 错误色 + 原因 + 重试动作 |
| 同上 §6.2 字阶表（第 502–511 行） | 仓库既有设计权威 | A（本地） | 空/错误态属 Display 档（电视 40–48sp）→ 大字号门槛 3:1 |
| `HomeActivity.getCallback().error(msg)` | HEAD `d90c5473835` | A（本地） | 失败路径确为「吐司 + `showContent()`」，无错误态无重试 |
| `SettingEnhanceActivity.setError` / `SettingAdActivity.setError` | HEAD `d90c5473835` | A（本地） | 内联错误只改文案不改颜色，与正常值无法区分 |
| `ProgressLayout` 的 `State` 枚举 | HEAD `d90c5473835` | A（本地） | 只有三态，无 ERROR |
| **实机取样**（`192.168.50.3:5561`，1920×1080，280dpi，截图 `.design-evidence/e5561-tv4-empty.png` 与 `e5561-after-settings.png`） | 本任务 | A（实测） | 壁纸亮度 L=0.1217–0.2019（均值 0.1476）；行色带 L=0.1157–0.1506（均值 0.1306）。**这是选值与判定门槛的唯一依据**，不再沿用此前文档里"假设的"底材亮度 |
| Material 3 深色表 `onErrorContainer` 定义 | 平台既有规范（沿用，本次未重新抓取） | A（间接） | 候选值 `#FFDAD6` |

### 2.1 候选值的实测对比度（本次唯一权威数字）

| 候选 | 对壁纸（均值／最亮） | 对行色带（均值／最亮） | 判定 |
|---|---:|---:|---|
| `#B3261E`（浅色表 `colorError`） | 1.23 / 1.11 | 1.06 / 1.00 | 不可读，**否决** |
| `#FFB4AB`（深色表 `colorError`） | 3.13 / 2.63 | 4.06 / 3.52 | 正文不足，大字号也不稳 |
| **`#FFDAD6`（采用）** | **4.11 / 3.23** | **4.50 / 4.05** | 大字号稳达标；正文压线到略低 |
| `#FFE9E4`（替代） | 4.51 / 3.68 | 4.96 / 4.56 | 正文达标，但观感已接近纯白 |

## 3. 方案比较与采用

| 方案 | 优点 | 缺点/风险 | 结论 |
|---|---|---|---|
| 1. 不变更 | 零风险 | §9.9 第三条继续不满足；失败后无重试入口 | 拒绝 |
| 2. 用 `?attr/colorError` 当错误色 | 不新增资源 | **浅色表深红对壁纸仅 1.23:1 / 对色带 1.06:1**，等于看不见 | 拒绝 |
| 3. 用近白的 `#FFE9E4` 以让正文也达 4.5:1 | 正文严格达标 | 与普通白字观感无异，"错误色"失去语义；等于没做 | 拒绝 |
| 4. **新增 palette-independent 错误角色（`#FFDAD6`）+ `ProgressLayout` ERROR 态 + 首页接入重试（采用）** | 满足 §9.9；大字号门槛稳达标；缺口如实记录 | 正文对比度 4.05–4.50:1，略低于 4.5:1 | **采用** |
| 5. 为错误正文改底材（更暗的错误行底） | 能同时满足语义与 AA | 改动面更大，涉及设置页行底绘制，应独立成批 | 记录为 §7 的后续 |

采用方案 4 后的分工：

| 位置 | 用什么 | 实测 |
|---|---|---|
| 错误态**标题**（Display 40sp） | `?attr/webhtvColorOnWallpaperError` | 对壁纸 3.23:1、对色带 4.05:1（大字号门槛 3:1 ✓） |
| 错误态**原因**（16sp 正文） | `?attr/webhtvColorOnWallpaper`（纯白） | 对壁纸 4.17–5.23:1 ✓ |
| 错误态**重试按钮** | 与空态主动作同一套样式 | — |
| 设置页**内联错误** | `?attr/webhtvColorOnWallpaperError` | 对色带 4.05–4.50:1（见 §7 缺口说明） |

## 4. 实施

1. `values{,-night}/webhtv_tokens.xml` 新增 `webhtv_color_on_wallpaper_error` = `#FFDAD6`，两表同值（壁纸在两种模式下都是深色），并把实测对比度与**用法边界**写进注释。
2. `webhtv_attrs.xml` 新增 `webhtvColorOnWallpaperError`；`webhtv_styles.xml` 的 `Theme.WebHTV` 接线。
3. `ProgressLayout`：
   - `State` 增加 `ERROR`；
   - 新增 `showError(int titleRes, CharSequence reason, int retryRes, OnClickListener retry)` 及资源 id 重载（原因常来自服务端 message，所以需要 `CharSequence` 版本）；
   - 错误态复用空态的三个槽位，但**隐藏插画**（空箱子表达的是"没有内容"，用在错误上会误导）并把标题染成错误色；
   - 原因正文刻意**不**用错误色（壁纸上的 16sp 正文需要 4.5:1，错误色只够大字号）；
   - 已在 ERROR 态时再次 `showError` 会重放内容，使"重试又失败"能刷新原因。
4. `view_empty.xml`（两个 flavor）给 Lottie 加 `@+id/illustration`，供错误态隐藏；仍用 `findViewById` + 空值守卫，避免 main 源集依赖 flavor 专属字段。
5. `HomeActivity`：配置加载失败改为进入错误态（标题 `error_config_load`、原因取服务端 message、重试重新 `initConfig()`），**保留原有吐司**。
6. `SettingEnhanceActivity` / `SettingAdActivity`：`setError` 改用错误角色；新增 `setSummary` 在写摘要时复位到常规壁纸前景色——**必须有这一步**，因为这些摘要复用同一个 TextView，不复位的话某行失败一次后会一直是红的。
7. 三语字符串 `error_config_load` / `error_retry`。
8. `ThemeContractTest` 冻结计数 54 → 55。
9. 新增 `TvErrorStateContractTest`（8 项），并把实测对比度写进断言（含"不许再低"的正文下限）。

## 5. 不变量与回滚

- 不改动 `Notify` 吐司机制：失败仍然弹吐司，错误态是**新增**的页面表达，不是替换。
- 不改动 `ProgressLayout` 既有三态语义与 `hideContent()` / `addOverlayView()`。
- 不改动空态的三件套与外观：错误态只借用槽位。
- 不改动 `ThemeTokens` 的 49 项角色契约：错误色是**同命名族里的固定语义色**（与 `webhtv_on_wallpaper` 同类），不进记录。
- 不改动播放器、解码、渲染、字幕、音轨、网络与数据行为。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 6. 验证记录

### 6.1 定向 JVM 测试

- `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*" --tests "com.fongmi.android.tv.ui.activity.Tv*"` → 首轮 1 项失败：新增的 `homeConfigFailureEntersTheErrorStateWithAWorkingRetry` 命中了**我自己写在注释里的 `showContent()`**（注释在解释"改动前是 showContent()"）。改为先剥离 Java 注释再查；复跑 **BUILD SUCCESSFUL in 3m 3s**，全绿。这已是本仓库第三次踩"负向断言命中说明文字"，契约测试里统一加了 `withoutJavaComments` / `withoutComments`。
- 新增 `TvErrorStateContractTest` 8 项：错误角色两表同值且为 `#FFDAD6`；独立 attr 且不复用 `colorError`；大字号门槛 3:1 在最不利实测底材上成立；正文对比度下限被钉住（≥4.0，含缺口说明）；`ProgressLayout` 有 ERROR 态/重试/隐藏插画/失败重放；两个 flavor 的空态布局都暴露 `@id/illustration`；首页失败回调进入错误态且不再走 `showContent()`、重试真的调用 `initConfig()`；两个设置页的 `setError` 用错误角色且 `setSummary` 会复位。
- `.\gradlew.bat :app:compileMobileArm64_v8aDebugUnitTestJavaWithJavac` → **BUILD SUCCESSFUL in 3m 3s**（`ProgressLayout` 在 main 源集，必须验手机端仍可编译）。
- `.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug` → **BUILD SUCCESSFUL in 2m 8s**。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。本次未新增裸十六进制。

### 6.2 设备实测（`192.168.50.3:5561`，API 28 / 1920×1080 / 280dpi）

**触发方式**：模拟器可 `adb root`，用 `iptables -I OUTPUT 1 -d 192.168.50.50 -j REJECT` 临时拒绝配置主机流量，制造一次**真实**的加载失败（不是伪造状态）。验证后已 `-D` 移除，并确认 `iptables -L OUTPUT | grep -c REJECT` 为 **0**。

| 检查项 | 结果 |
|---|---|
| 失败后是否进入错误态 | 是：页面显示「内容源加载失败」+ 原因「配置取得失败 / Failed to connect to /192.168.50.50:4567」+ 「重试」按钮 |
| 错误色是否可见 | 标题为暖粉调（`?attr/webhtvColorOnWallpaperError`），与下方**纯白**的原因正文肉眼可区分——这正是改动前"与正常值同色"所缺失的 |
| 是否隐藏插画 | 是，空态那只"空箱子"没有出现 |
| 重试是否可达 | 方向键可聚焦「重试」 |
| 重试是否真的生效 | 移除规则后按确认键，首页完整恢复（顶栏、分类、最近观看五张卡全部回来） |
| 是否仍弹吐司 | 是（保留既有行为） |
| 有无崩溃 | 无 |

截图：`.design-evidence/e5561-tv5-error.png`（错误态）、`e5561-tv5-error-focus.png`（聚焦重试）、`e5561-tv5-recovered.png`（重试后恢复）。

### 6.3 结论

TV 端首次具备设计文档 §9.9 意义上的错误态：错误色 + 原因 + 重试动作，且首页不再在失败后留一个空页面。错误角色是独立的 palette-independent 语义色，不复用跟随调色板的 `colorError`。

### 6.4 残余风险与诚实说明

- **正文对比度缺口（已量化、未掩盖）**：`#FFDAD6` 对行色带最亮处 4.05:1、均值 4.50:1，**略低于正文门槛 4.5:1**。这不是算错或疏忽，而是底材决定的：壁纸/色带是中亮度（L≈0.13–0.15），带明显红相的前景色要达到 4.5:1 必须接近纯白（L≥0.86），那时"错误色"与普通白字观感无异。契约测试把下限钉在 4.0 而不是 4.5——写 4.5 会是一个假断言。修法见 §7.1。
- 设置页的内联错误色本次**只做了单元/契约级验证**，没有在真机上复现出一条失败的取值（需要构造某个设置项读取抛异常）。它的逻辑（着色 + 成功复位）由契约测试覆盖。
- 错误态的标题字号沿用空态的 40sp（Display 档），本次未单独调整。

## 7. 相邻发现（未纳入本批）

1. **正文对比度的缺口无法靠调色解决，只能改底材。** 壁纸/行色带是中亮度（L≈0.13–0.15），要让带明显红相的前景色达到正文 4.5:1 就必须接近纯白（L≥0.86），那时错误色与普通白字在观感上已无区别。若产品要求设置页内联错误严格达 AA，正确做法是给错误行一块**更暗的底**（例如在错误态叠加一层更深的 scrim），而不是继续在色值上找折中。这是一次涉及行底绘制的独立改动。
2. **播放器错误层仍没有重试动作。** §9.9 要求错误态有"重试动作"，而播放失败目前只有内嵌错误文案 + 吐司。播放器控制层已有既有的重试语义，接入时要注意不要与"自动重试/切源"逻辑打架，建议独立成批。
3. **`ProgressLayout.ERROR` 目前只有首页接入。** 历史/收藏/文件的加载失败同样可以走它，但每处的"原因"与"重试"都是各自的数据源语义，需要逐个确认。
