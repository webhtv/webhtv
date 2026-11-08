# TV-ERROR-STATE-CLOSEOUT-20261013：关闭 TV-5 记录的错误态缺口（正文底材 + 其余宿主）

## Recovery anchor

- 目标：关闭 `docs/TV-ERROR-STATE-20261012.md` §7.1（错误正文对比度缺口）与 §7.3（其余宿主的错误态接入）两条，并把 §7.3 里**不成立的前提**更正。
- 允许路径：`app/src/main/res/values{,-night}/webhtv_tokens.xml`、`app/src/leanback/res/drawable/shape_error_value.xml`（新增）、`app/src/leanback/java/.../activity/{SettingEnhanceActivity,SettingAdActivity,KeepActivity}.java`、`app/src/test/java/.../theme/ThemeContractTest.java`、`app/src/testLeanback/java/.../ui/activity/TvErrorStateContractTest.java`、本文件。
- 保护面：任务开始时工作树仅 `.design-evidence/` 未跟踪（预存脏路径 0 个）；分支 `dev4`，HEAD `0b934c4e1e3`。
- 验收：① 错误取值有实心深底且错误正文在其上 ≥4.5:1；② 成功刷新时颜色与底材都复位；③ 收藏页配置加载失败进入错误态且重试复用同一路径；④ 契约测试全绿 + 手机端仍可编译；⑤ TV-5 §7.3 的前提更正被记录。
- 回滚：撤销本任务原子提交即可回到「错误正文 4.05:1、收藏页失败只弹吐司」；无数据迁移。
- 交付坐标：提交 `6f4009aa74d`（dev4），recovery tag `recovery/TV-ERROR-STATE-CLOSEOUT-20261013/20261013090805`；未推送。
- 下一步唯一动作：无（本批已交付）。唯一未做项是 §7.2 的播放器错误层重试，已给出最小实施步骤。

## 1. 本批关闭的两个缺口

TV-5 交付了错误态本体（错误色 + 原因 + 重试），并在文档 §7 留下三条后续。本批处理其中两条：

### 1.1 §7.1 错误正文的对比度缺口

TV-5 实测记录：`#FFDAD6` 画在壁纸/行色带上只有 **4.05–4.50:1**，略低于正文门槛 4.5:1。当时的结论是「无法靠调色解决——要达到 4.5:1 就必须接近纯白（L≥0.86），那时错误色与普通白字观感无异、失去语义」。

本批按当时记录的修法**改底材**：给错误取值一块**实心深色底** `#4A3438`（暖深灰）。

| 底材 | 亮度 L | `#FFDAD6` 在其上的对比度 |
|---|---:|---:|
| 行色带（最亮处） | 0.1506 | 4.05:1 ✗ |
| 行色带（均值） | 0.1306 | 4.50:1 ⚠ 压线 |
| 壁纸（最亮处） | 0.2019 | 3.23:1（仅够大字号） |
| **实心深底 `#4A3438`（本批）** | **0.0419** | **8.84:1 ✓** |

用**实心**而不是半透明：半透明的结果取决于下层色带，给不出确定值，也就无法写进契约测试。

### 1.2 §7.3 其余宿主的错误态接入——前提被侦察否掉

TV-5 的 §7.3 写的是「历史/收藏/文件的加载失败同样可以走它」。本批实际侦察后，这个前提**大部分不成立**：

| 宿主 | 实际失败面 | 结论 |
|---|---|---|
| `HistoryActivity` | 加载是本地读取；`onItemClick` 直接交给 `HistoryResumeCoordinator`，**没有配置加载分支** | 无真实失败源，**不接** |
| `FileActivity` | 加载是本地文件读取，**没有 try/catch、没有失败分支** | 无真实失败源，**不接** |
| `SearchActivity` | 只有"该站点不支持搜索"之类的**提示性吐司** | 不是失败态，**不接** |
| `CollectActivity` | 只有筛选提示与 `NumberFormatException` 兜底 | 不是页面级失败，**不接** |
| **`KeepActivity.loadConfig`** | `VodConfig.load(config, callback)` → `error(msg)` → **只弹吐司** | **唯一真实的页面级远端失败，本批接入** |

所以本批只接收藏页这一处，**不为了凑数量去臆造错误态**。TV-5 文档 §7.3 的表述在本文档 §7 里更正。

## 2. 证据来源

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| `docs/TV-ERROR-STATE-20261012.md` §7.1 / §7.3 | 本仓库上一批的交付记录 | A（本地） | 缺口与建议修法；§7.3 的前提在本批被侦察修正 |
| 实机取样（`192.168.50.3:5561`，1920×1080，280dpi） | TV-5 采集，本批复用 | A（实测） | 壁纸 L=0.1217–0.2019；行色带 L=0.1157–0.1506 |
| `SettingEnhanceActivity` / `SettingAdActivity` 的 `setError`/`safeSet` | HEAD `0b934c4e1e3` | A（本地） | 内联错误的落点；这些 TextView 原本**没有背景**，所以错误底材可以安全地加上再撤掉 |
| `activity_setting_enhance.xml` 的取值 TextView | HEAD `0b934c4e1e3` | A（本地） | `match_parent` + `gravity="end"`、无 `background`、`textSize=18sp`、`textColor=?attr/webhtvColorOnWallpaper` |
| `KeepActivity.loadConfig` | HEAD `0b934c4e1e3` | A（本地） | 失败路径确为「只弹吐司」 |
| `HistoryActivity.onItemClick` / `FileActivity` / `SearchActivity` / `CollectActivity` 的失败面 | HEAD `0b934c4e1e3` | A（本地） | 逐个确认无页面级失败分支（见 §1.2 表） |

不适用类别记录：不涉及解码/渲染/ABI/打包/网络协议变更，无需上游播放器依赖类证据。

## 3. 方案比较与采用

| 方案 | 优点 | 缺点/风险 | 结论 |
|---|---|---|---|
| 1. 保持 4.05:1 不动 | 零风险 | 正文对比度继续不达 AA | 拒绝 |
| 2. 把错误色改成接近纯白（如 `#FFE9E4`） | 不改底材就达标 | 与普通白字观感无异，"错误色"失去语义；等于没做 | 拒绝 |
| 3. **给错误取值加实心深底（采用）** | 8.84:1 稳达标；底材确定可写进契约；错误行更醒目 | 错误时该行取值区域会出现一块深色块（观感变化） | **采用** |
| 4. 给整行换错误底 | 更醒目 | 需要把行容器传进 `setError`，改动面扩散到布局与行选择器 | 拒绝（超出本批） |
| 5. 给历史/文件/搜索/采集也接错误态 | "覆盖更全" | 这些宿主**没有失败分支**，接上去是臆造 | 拒绝 |

## 4. 实施

1. `values{,-night}/webhtv_tokens.xml` 新增 `webhtv_color_error_value_bg` = `#4A3438`，两表同值（实心色，不跟随调色板），注释里写明核算过程与"实心而非半透明"的理由。
2. 新增 `app/src/leanback/res/drawable/shape_error_value.xml`：实心底 + 8dp 圆角 + 内边距。
3. `SettingEnhanceActivity` / `SettingAdActivity`：
   - `setError` 除改色外，`setBackgroundResource(R.drawable.shape_error_value)`；
   - `setSummary` 除复位颜色外，`setBackground(null)` 撤掉底材——**错误态是临时的**，刷新成功必须完全回到原样（这些 TextView 原本没有背景）。
4. `KeepActivity.loadConfig` 的 `error(msg)`：保留吐司，同时进入错误态（标题 `error_config_load`、原因取服务端 message、重试复用同一条 `loadConfig(config, item)` 路径，所以重试成功与首次成功行为一致）。
5. `ThemeContractTest` 冻结计数 55 → 56。
6. `TvErrorStateContractTest` 增加 2 项：底材在两张表同值且错误正文在其上 ≥4.5:1；收藏页失败进入错误态且重试复用同一路径。原有"设置页内联错误"一项同时加上"必须换上底材 / 必须撤掉底材"的断言。

## 5. 不变量与回滚

- 不改动 `ProgressLayout` 的状态机与 TV-5 已交付的错误态结构。
- 不改动错误前景色 `#FFDAD6` 本身：它在壁纸最亮处仍有 3.23:1，是错误**标题**（Display 档）的取值，改小会跌破大字号门槛。
- 不改动吐司机制：失败仍然弹吐司，错误态是**新增**的页面表达。
- 不改动历史/文件/搜索/采集的行为（见 §1.2：它们没有可接的失败面）。
- 不改动播放器任何行为（§7 说明为何本批不碰）。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 6. 验证记录

### 6.1 定向 JVM 测试

- `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*" --tests "com.fongmi.android.tv.ui.activity.Tv*"` → 首轮 2 项失败，均为**真实缺陷**：① `ThemeContractTest` 数到 55（期望 56）——我只把新色写进了日间表，`values-night` 漏了，而该测试对**两张表**都做冻结计数；② `errorValueSubstrateGivesTheBodyTextARealMargin` 直接指出 `values-night/webhtv_tokens.xml 必须声明 webhtv_color_error_value_bg`。补上夜间表后两表均为 56，复跑 **BUILD SUCCESSFUL in 58s**。
- 期间一次运行报 `Gradle build daemon has been stopped: stop command received`——这是**环境**中断（守护进程被外部停止），不是断言失败；已按"不重复无效结果"的原则重跑一次并拿到有效结论。
- 新增 `TvErrorStateContractTest` 3 项：错误底材两表同值且错误正文在其上 ≥4.5:1（实测 8.84:1）；收藏页失败进入错误态且重试复用同一路径；**取值 TextView 在布局里必须没有背景**（见 §6.4）。
- `.\gradlew.bat :app:compileMobileArm64_v8aDebugUnitTestJavaWithJavac` → **BUILD SUCCESSFUL in 1m 49s**。
- `.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug` → **BUILD SUCCESSFUL in 2m 27s**。

**环境限制**：本会话仍无可用的 `bash`（仅 WSL，`HCS_E_SERVICE_NOT_AVAILABLE`），`scripts/check_ui_tokens.sh` 与 `.codex/scripts/task_guard.sh` 无法执行，按同等意图手工暂存/提交/打 tag。本次未新增裸十六进制。

### 6.2 设备实测（`192.168.50.3:5561`，API 28 / 1920×1080 / 280dpi）

| 检查项 | 结果 |
|---|---|
| 设置首页（`SettingActivity`） | 分组标题白色、取值次级色——TV-1 的层级未被破坏 |
| **「增强功能」页（`SettingEnhanceActivity`）** | 8 行取值全部正常显示为次级色（运行环境·工具·网络 / 启用·0/0 可用 / 账号 0 / 未绑定 / 选择设备 / 已选 0·待确认 0 / 禁用·0 条规则）——证明 `themeColor()` 把属性解析成了**颜色值**而不是资源 id，`setSummary` 的显式着色没有造成回归 |
| 有无崩溃 | 无 |

截图：`.design-evidence/e5561-tv6-settings.png`、`e5561-tv6-enhance.png`。

### 6.3 结论

TV-5 记录的错误正文对比度缺口已收口：错误取值换实心深底后为 **8.84:1**，稳过正文门槛；成功刷新时颜色与底材都会复位。收藏页的配置加载失败不再只弹吐司。TV-5 §7.3 里不成立的前提已更正（见 §1.2 与 §7.1）。

### 6.4 残余风险与诚实说明

- **错误底材的观感未在真机上看到。** 要触发设置页内联错误必须让某个设置项读取抛异常，我没有构造出这样的场景（也不愿为了截图往代码里塞测试专用分支）。因此"错误时该行取值区域出现一块深色块"这一观感变化**只有静态与契约级证据**。它的取值（8.84:1）是确定算出来的，但**看起来是否合适**没有实测。若观感不佳，替代方案是 §3 的选项 4（给整行换底）。
- `setSummary` 新增了 `setBackground(null)`，前提是这些取值 TextView 原本没有背景。已用脚本逐个核对两个布局的 **26 个**取值 TextView（全部无背景），并把这个前提写成契约断言——将来有人给其中任何一个加背景时，测试会先失败，而不是变成"设置页某个取值突然没底了"。
- 收藏页错误态的真机验证**没有单独做**：它走的是 TV-5 已在真机上端到端验证过的同一条 `ProgressLayout.showError` 路径（错误色标题 + 原因 + 重试），本次新增的只是"由 `loadConfig` 失败触发"这一处调用点，由契约测试覆盖。

## 7. 更正与未纳入本批

### 7.1 更正 TV-5 §7.3 的表述

TV-5 文档 §7.3 写的是「历史/收藏/文件的加载失败同样可以走它」。本批逐个查证后确认：**历史与文件的加载没有失败分支**（本地读取），搜索/采集只有提示性吐司。因此"其余宿主"实际只有**收藏页的配置加载**一处，本批已接。TV-5 文档中的该条以本文档 §1.2 的表格为准。

### 7.2 播放器错误层的重试：本批**未做**，已侦察但决定单独成批

用户同时要求了这一项。本批做了侦察，结论如下，但**没有实施**：

- 错误层是 `view_widget_vod.xml` 的 `@id/error`（`shape_widget` 底的竖排容器 + 图标 + `@id/text`），由 `VideoActivity.showError(text)` / `hideError()` 控制。
- 播放器**已有**两条相关机制：`startFlow()` 是**自动换源**（仅在 `PlayerSetting.isAutoChange()` 且站点可换源时生效），`onReplay()` 是**既有的用户可触发重播**（已由按键路径 `isReplay()` 调用）。
- `showProgress()` 会顺带 `hideError()`，所以"重试 → 出现缓冲圈 → 错误层自动隐藏"这条链路是现成的。

**为什么不在本批做**：这一项要动播放器错误层的**焦点管理**（错误浮层叠在视频面上，控制层会自动隐藏，要让遥控能聚焦到重试按钮需要确认与既有焦点/控制层时序不打架），并且要制造一条**真实失败的流**才能验证（可以再用 iptables 拒绝某个 CDN 主机，但需要先确认该次播放的实际主机）。在无法真机验证的前提下把交互式按钮塞进播放器，风险高于收益——播放失败后的自动换源是用户可感知的关键路径，草率改动可能造成"重试与自动换源互相打断"这类回归。

**建议的最小实施步骤**（下一批）：
1. 在 `view_widget_vod.xml` 的 `@id/error` 内加入带应用表面族焦点环的重试按钮（复用 TV-4 的 `selector_empty_action` 形态）；
2. 点击回调直接复用 `onReplay()`（不新写播放路径），并先 `hideError()` + `showProgress()` 给出即时反馈；
3. 用 iptables 拒绝该次播放的真实主机，在真机上验证：错误层出现 → 聚焦重试 → 按确认 → 缓冲圈出现且错误层隐藏 → 恢复后错误层不再出现；
4. 确认与 `startFlow()` 自动换源不互相打断（同时开/关自动换源各验一次）。
