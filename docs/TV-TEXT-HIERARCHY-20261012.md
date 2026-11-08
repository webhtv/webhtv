# TV-TEXT-HIERARCHY-20261012：TV 端只有一种壁纸前景色，设置页与卡片都没有信息层级

## Recovery anchor

- 目标：TV 端拥有一个可用的**壁纸次级前景角色**，设置页按四组给出分组标题与标签/取值层级，卡片集数行与刷名恢复层级，详情页不再渲染只有标签的空元数据行。
- 允许路径：`app/src/main/res/values{,-night}/webhtv_tokens.xml`、`app/src/main/res/values/webhtv_attrs.xml`、`app/src/main/res/values/webhtv_styles.xml`、`app/src/main/res/values{,-zh-rCN,-zh-rTW}/strings.xml`、`app/src/leanback/res/drawable/shape_vod_name.xml`、`app/src/leanback/res/layout/{activity_setting,adapter_vod}.xml`、`app/src/leanback/res/values/styles.xml`、`app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`、`app/src/test/java/com/fongmi/android/tv/theme/{ThemeBaseWiringTest,LeanbackForegroundContrastTest}.java`、`app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/{TvFocusRingContractTest,TvSettingsHierarchyTest}.java`、本文件。
- 保护面：任务开始时工作树干净（预存脏路径 0 个）；分支 `dev4`，HEAD `139f2ab5d16`。本轮新增未跟踪目录 `.design-evidence/`（真机截图取证，不进入提交）。
- 验收：① 设置页四个分组标题、顺序与分组一致，字幕设置留在「播放与内容」组；② 只有设置项取值使用次级角色，标签与分组标题保持纯白；③ 重排后所有 `nextFocus*` 引用仍指向存在的行且纵向焦点图连通；④ 详情页值缺失时清空文本；⑤ 卡片条带 40% 黑、集数行走次级角色；⑥ 定向 JVM 测试全绿；⑦ 真机前后对照截图。
- 回滚：撤销本任务原子提交即可回到「单壁纸前景 + 20% 卡片条带 + 无分组设置页」的旧行为；无数据迁移。
- 交付坐标：提交 `3041baf71c4`（dev4），recovery tag `recovery/TV-TEXT-HIERARCHY-20261012/20261012142520`；未推送。
- 下一步唯一动作：无（TV-1 已交付）。下一批建议 TV-2（卡片与内容层），先处理 §8 第 1 项（OSD/TMDB 叠加层的空元数据）。

## 1. 现象与根因

用户要求「全面升级当前 app 的 UI」。按已确认的范围（按端分批、保持 Material 3 体系做精细化提升、TV 优先），先做 TV 端的排版与信息层级。真机取证（`192.168.50.3:5557`，Android 9 / 1920×1080 / 280dpi）看到两个 P1：

1. **TV 端没有"次级文字"角色，全屏纯白。** `values/webhtv_tokens.xml` 的 `webhtv_on_wallpaper` 在明暗两张表里都是 `#FFFFFF`；`activity_setting.xml` 的 17 个设置项**标签与取值同为 18sp 纯白**；`adapter_vod.xml` 的刷名 16sp 白、集数行 14sp 白、角标 14sp 白。层级只剩字号差。
2. **设置页 17 项无分组、无标题。** 信息源（点播/直播/壁纸）、播放能力（增强/播放/弹幕/去广告/字幕）、个性化（TMDB/AI/外观/个性）、数据管理（备份/恢复/缓存/关于/无痕/DoH）混排成一张 24dp 内边距的平铺网格，用户只能整屏扫。

**根因不是配色，而是承载面。** 仓库自己的测试记录了这个因果链：`TvFocusRingContractTest` 的注释写明 `?attr/colorOnSurfaceVariant`（浅色表 `#44474F`）画在深色 `shape_vod_name` 条带上只有 **1.35:1**，所以集数行被迫改成与刷名相同的纯白。也就是说——**任何次级色都会被半透明色带 + 不受控壁纸压到 4.5:1 以下，于是唯一可读的选择就是纯白，层级随之消失。**

结论：要让次级色存在，必须同时降低承载面的亮度，否则只是把 1.35:1 的缺陷换个名字放回来。

附带发现（本任务内一并修掉）：详情页渲染只有标签的空行（真机截图 `导演：` / `演员：` 后面什么都没有）。

## 2. 证据来源

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| 真机截图 | `192.168.50.3:5557`，1920×1080，Android 9（API 28），密度 280 | A | 设置页 17 项平铺无分组；标签与取值同为纯白 18sp；卡片刷名与集数行同色；详情页 `导演：`/`演员：` 为空行。截图存于 `.design-evidence/`（`tv-02-home-clean.png`、`tv-03-settings.png`、`tv-05-player-osd.png`） |
| 本仓库 `values/webhtv_tokens.xml`、`values-night/webhtv_tokens.xml` | HEAD `139f2ab5d16` | A（本地） | `webhtv_on_wallpaper` 两张表恒为 `#FFFFFF`，且注释说明它刻意不参与 `ThemeTokens` 的 49 项角色契约——新增角色沿用同一先例 |
| 本仓库 `app/src/leanback/res/layout/activity_setting.xml` | HEAD `139f2ab5d16` | A（本地） | 17 项平铺、零分组标题；标签与取值都是 `?attr/webhtvColorOnWallpaper` + 18sp；行内显式 `nextFocus*` 链覆盖全部纵向导航 |
| 本仓库 `app/src/leanback/res/drawable/shape_vod_name.xml`（20% 黑）与 `app/src/leanback/res/layout/adapter_vod.xml` | HEAD `139f2ab5d16` | A（本地） | 卡片信息区条带 20% 黑；`@id/name` 与 `@id/remark` 同色 |
| 本仓库 `TvFocusRingContractTest`（第 439–456 行注释） | HEAD `139f2ab5d16` | A（本地） | 记录 `colorOnSurfaceVariant` 在深色条带上实测 **1.35:1**、纯白 **6.89:1**；这是"只能全白"的直接原因，也是本次必须同时压暗条带的依据 |
| 本仓库 `docs/webhtv-unified-visual-design-system-20260920.md` | 仓库既有设计权威 | A（本地） | 语义 token 体系、`on*` 成对生成、正文 4.5:1 / 非文本 3:1 门槛、TV 与手机共享语义不共享尺寸 |
| 该设计文档第 3 节记录的一手来源（Material 3 Color roles / Buttons / Shape、Android TV Color system 与 Design for TV、WCAG 2.2 SC 1.4.3） | 访问日期 2026-09-20（**本次未重新抓取，沿用仓库既有记录**） | A（间接） | 颜色角色模型、TV 深色优先与远距离可读性、正文 4.5:1 门槛；本次改动不引入新规范，只是把既有门槛应用到"壁纸次级前景"这一缺失角色 |
| Android TV 远程可读性 | 本仓库既有记录 + 真机观感 | B | 分组标题直接画在壁纸上，因此不能靠降低亮度做层级（见 §4 的 3.84:1 实测推导） |

不适用类别记录：本改动不涉及解码/渲染/ABI/打包/网络，无需上游播放器依赖类证据；也不涉及架构或公共 API。

## 3. 现状盘点（改动前）

| 面 | 事实 | 影响 |
|---|---|---|
| 壁纸前景角色 | 只有 `webhtvColorOnWallpaper`（恒 `#FFFFFF`） | 标题、正文、取值、角标、集数行全部纯白 |
| 设置页结构 | 17 项平铺，无标题，行高一致 | 无信息架构，只能整屏扫 |
| 设置页标签/取值 | 同为 18sp 纯白，仅靠左右位置区分 | 视觉重量相同，取值不突出 |
| 卡片信息区 | 条带 20% 黑；刷名与集数行同色（字号 16/14 区分） | 两行没有层级 |
| 详情页元数据 | `setText(resId>0)` 渲染 `getString(resId,"")`，即 `"导演："` 这类非空标签；`setVideoDetailsVisible()` 按 `!getText().isEmpty()` 再翻回可见 | 出现只有标签的空行；且旧守卫在换集后保留上一条目文本 |

## 4. 方案比较与采用

对比度按 sRGB 合成计算，壁纸取值由仓库实测反推：纯白在 20% 黑条带上实测 6.89:1 ⇒ 条带等效 `#5A5A5A`（相对亮度 ≈ 0.1024），壁纸等效 `#707070`（≈ 0.1620）。

| 方案 | 优点 | 缺点/风险 | 结论 |
|---|---|---|---|
| 1. 不变更，继续全白 | 零风险 | 层级问题原样保留，正是用户报告的问题 | 拒绝 |
| 2. 只加次级色、不动承载面 | 改动最小 | 次级色在 20% 条带上最高只能到 4.33:1（70% 白）→ 跌破正文门槛；要合规只能到 75–80% 白，与纯白几乎无法区分，层级等于没做 | 拒绝 |
| 3. **加次级角色 + 压暗承载条带（采用）** | 条带变暗后标题与次级行**都**比改动前更清楚，层级真实存在 | 卡片信息区壁纸透出感减弱；条带是 3 个布局共享的 drawable | **采用** |
| 4. 顺带压暗设置行色带 `selector_item` | 设置页取值可更暗 | `selector_item` 被约 130 处引用（全部设置子页、首页功能按钮、类型芯片），且 `FollowingUiSourceTest` 冻结了"追更页顶栏与首页按钮 `black_20` 对齐"；属跨表面改动 | 拒绝，留给独立批次 |
| 5. 分组标题也用次级色 | 层级更明显 | 标题直接画在壁纸上：80% 白在实测壁纸上只有 **3.84:1**，低于 4.5:1 | 拒绝，标题保持纯白 |

采用方案 3 的具体取值：

| 位置 | 承载面 | 前景 | 对比度 |
|---|---|---|---:|
| 卡片刷名 | 40% 黑条带（`#434343`） | 纯白 `#FFFFFF` | **9.82:1**（改动前 6.89:1） |
| 卡片集数行 | 40% 黑条带 | `webhtv_on_wallpaper_variant` `#CCFFFFFF` | **6.99:1**（高于被替换掉的纯白 6.89:1） |
| 设置项取值 | 20% 黑行色带（`#5A5A5A`） | `#CCFFFFFF` | **5.12:1** |
| 设置项标签 / 分组标题 | 同上 / 壁纸 `#707070` | 纯白 `#FFFFFF` | 5.12:1 以上 / **4.95:1** |

即：**没有一处可读性下降**——卡片两行各自都高于或等于改动前的 6.89:1，设置页取值 5.12:1、标签与标题保持纯白。

取舍与残余风险：
- 卡片信息区壁纸透出感减弱（用户已确认接受）。
- 半透明色带叠在任意用户壁纸上无法给出全局保证；这与设计文档「阶段 H2」记录的既有残余风险同源。上述数字基于内置的中深色壁纸。
- `webhtv_on_wallpaper_variant` 刻意不带 `webhtv_color_` 前缀，因为 `ThemeTokens` 的 Material 角色契约被 `ThemeContractTest` 冻结在 49 项；这与 `webhtv_on_wallpaper`、`webhtv_on_surface_20/70/80/90` 的先例一致，`ThemeBinder` 同样不参与改写。

## 5. 实施

1. **新角色**：`webhtv_on_wallpaper_variant` = `#CCFFFFFF`，写入 `values/` 与 `values-night/` 两张表（壁纸在两种模式下都是深色，故同值）；`webhtv_attrs.xml` 新增 `webhtvColorOnWallpaperVariant`；`Theme.WebHTV` 接线。
2. **卡片条带**：`app/src/leanback/res/drawable/shape_vod_name.xml` 由 `@color/black_20` 改为 `@color/black_40`（仅 leanback；mobile 的同名 drawable 是独立文件，未改动）。
3. **卡片集数行**：`adapter_vod.xml` 的 `@id/remark` 改用 `?attr/webhtvColorOnWallpaperVariant`；`@id/name` 保持纯白。
4. **设置页分组**：新增 `Widget.WebHTV.TvGroupHeader`（14sp、纯白、字距 0.1、上 32dp / 下 0dp）与四个字符串 `setting_group_sources/playback/personal/data`（默认、简中、繁中三张表）；布局插入四个标题，并把「字幕设置」从原三列行拆出、移到 `@id/ad` 之后（即「播放与内容」组内）。
5. **焦点链**：随重排同步 `@id/ad`→`subtitle`、`@id/tmdb`→`subtitle`、`@id/ai`→`subtitle/personal`、`@id/appearance`→`tmdb/incognito`、`@id/incognito`→`appearance`、`@id/doh`→`personal`。
6. **设置项取值**：`vodUrl`/`liveUrl`/`wallUrl`/去广告摘要/`incognitoText`/`dohText`/`cacheText`/`versionText` 共 8 处改用次级角色；标签保持纯白。
7. **空元数据**：`VideoActivity.setText` 在值缺失时对 `resId > 0` 的字段清空文本并置 `GONE`（消除"空标签被 `setVideoDetailsVisible()` 翻回可见"与换集后的文本泄漏）；`resId == 0` 的 remark 保留原语义，因为它的文本可能由 TMDB 集数信息经另一条路径写入。

## 6. 不变量与回滚

- 不改动 `ThemeTokens` / `ThemeResolver` / `ThemeBinder` / `ThemeProfile`：新角色是角色契约之外的资源，`ThemeContractTest` 的 49 项契约不变。
- 不改动 `selector_item` 及其任何宿主（设置子页、首页功能按钮、类型芯片），因此追更页与首页按钮的 `black_20` 对齐契约不受影响。
- 不改动 mobile flavor 的任何资源：`shape_vod_name` 与 `adapter_vod` 在两个 flavor 各自独立。
- 不改动播放器解码、渲染、字幕、音轨、网络与数据行为。
- 回滚：撤销本任务原子提交即可；无数据迁移。

## 7. 验证记录

### 7.1 定向 JVM 测试

- `.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*" --tests "com.fongmi.android.tv.ui.activity.TvSettingsHierarchyTest" --tests "com.fongmi.android.tv.ui.activity.TvFocusRingContractTest"` → **BUILD SUCCESSFUL in 52s**（首轮 192 项中 `TvSettingsHierarchyTest` 有 1 项失败，原因是负向断言把样式文件注释里提到的角色名也算了进去，已改为先剥离注释再查；复跑全绿）。
- `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.theme.*"` → **BUILD SUCCESSFUL in 3m 28s**（确认共享 `values/` 的改动没有影响手机端）。
- 新增 `TvSettingsHierarchyTest` 4 项：分组标题与顺序、取值/标签层级、焦点链完整性与纵向连通、空元数据清空。

**环境限制**：本会话无可用的 `bash`（仅 WSL `bash.exe`，`LxssManager`/`vmcompute` 未运行，返回 `HCS_E_SERVICE_NOT_AVAILABLE`），因此 `scripts/check_ui_tokens.sh --strict` 无法执行，`.codex/scripts/task_guard.sh` 也无法执行。按其意图手工执行：仅暂存本任务声明范围内的路径、原子提交、创建本地带注释 recovery tag，未推送。与 `docs/TV-DIALOG-PALETTE-NIGHT-20261012.md` §7.4 记录的环境一致。静态门禁的替代覆盖：本次改动**未新增任何裸十六进制**（新角色写在 token 文件内，布局只引用 `?attr/`），因此 `hex_layouts/hex_drawables/hex_colors=0` 这一条不受影响；其余检查由上述 JVM 契约测试覆盖。

### 7.2 设备实测

**设备口径更正（2026-10-12）**：本任务分配到的模拟器是 `192.168.50.3:5561`（`emulator-5560`，HD1910，x86_64 + native bridge `libnb.so`，API 28，1920×1080 / 280dpi，装 arm64 flavor）。首轮取证误用了 `192.168.50.3:5557`（用户电视，SM-N9700，同为 API 28 / 1920×1080 / 280dpi）。两台的显示参数相同，因此结论一致；下表分别记录，`5561` 为准。

#### 7.2.1 分配设备 `192.168.50.3:5561`（准）

同一台模拟器、同一路径，只换安装包（`adb install -r` 覆盖安装）。改前基线是模拟器上原有的 12:05 构建（早于本任务提交 14:25），因此是同机前后对照。

| 检查项 | 结果 |
|---|---|
| 冷启动 | 正常进入首页，无崩溃（native bridge 下启动较慢，约 30s） |
| 首页卡片 | 信息区色带明显变深；刷名（`无可替代 第1季`）保持纯白，集数行（`第3集`）明显变暗，层级清晰可辨 |
| 设置页上半 | 「内容源」+ 点播/直播/壁纸；「播放与内容」+ 增强功能/播放设置/弹幕设置 + 去广告 + **字幕设置**；「个性化与智能」+ TMDB/AI 服务 |
| 标签 vs 取值 | 取值（两个 URL、`梦幻紫霞`、`总开关、规则、片头片尾`）明显比标签暗 |
| 更强底材复核 | 该机壁纸是 `梦幻紫霞`（比 5557 的青绿壁纸更亮），次级取值仍清晰可读，说明取值不是勉强过线 |
| 详情页（有数据） | `无可替代` 详情页正常显示 `导演：陈铭章 陈世峰` 与两行 `演员：…`——**证明修复只隐藏空值，没有破坏有值的正常分支** |

截图：`.design-evidence/e5561-before-home.png`（改前）、`e5561-after-home.png`（改后）、`e5561-after-row-end.png`、`e5561-after-settings.png`、`e5561-after-detail.png`。

补充事实：该机首页功能行有 8 个按钮（`home_button=0,8,6,1,2,3,4,7`），但首屏只显示前 7 个，`设置` 需要横向滚动才出现——这是该机既有的行宽行为，与本次改动无关。

#### 7.2.2 早先误用的 `192.168.50.3:5557`（用户电视，非分配设备）

同一台电视、同一路径覆盖安装。结果与 5561 一致：

| 检查项 | 结果 |
|---|---|
| 冷启动 | 正常进入首页，无崩溃 |
| 首页卡片 | 刷名与集数行出现可辨层级 |
| 设置页上半/下半 | 四个分组标题、间距与取值层级全部生效 |
| D-pad 纵向导航 | 连续 14 次 DOWN 顺序穿过四个分组抵达底部，无卡死、无跳组 |
| 详情页空元数据 | 原生详情列不再出现「导演：」「演员：」空行 |
| 权威复核 | `dumpsys activity top` 中 `app:id/director` 与 `app:id/actor` 均为 `GONE`（`G.ED.....`） |

截图：`after-01-home.png`、`after-02-settings.png`、`after-03-settings-bottom.png`、`after-04-detail.png`；改前对照 `tv-02-home-clean.png`、`tv-03-settings.png`、`tv-04-detail.png`。

**副作用披露**：本轮曾对该电视执行 `adb install -r` 覆盖安装（未卸载、未清数据，签名一致才得以成功），即把它上面的应用更新成了本任务的 debug 构建。未做其他改动；如需回退到原有构建，需重新安装对应 APK。

### 7.3 结论

TV-1 的四项目标（分组、标签/取值层级、卡片两行层级、空元数据不渲染）在真机上全部成立，且定向测试与手机端回归均通过。

### 7.4 残余风险

- 卡片信息区壁纸透出感减弱（已确认接受）。
- 半透明色带叠在任意用户壁纸上无法给出全局对比度保证；这与设计文档「阶段 H2」记录的既有残余风险同源。
- 分组标题直接画在壁纸上，实测 4.95:1（纯白），余量不大；若将来壁纸整体变亮需要重新核算。
- 本次未验证：TMDB 布局模式下的详情页（真机当时处于原生布局模式）、设置子页（未随本次改动）、手机端设置页（属于手机批次）。

## 8. 相邻但未修复（超出本次范围，仅报告）

1. **播放页 OSD/TMDB 叠加层仍会显示空的「导演：」「演员：」。** 真机截图 `after-04-detail.png` 里，视频窗口内那块紫色面板同时显示了「站源: 推送 / 导演: / 演员:」。经 `dumpsys activity top` 复核，`app:id/director` 与 `app:id/actor` 当时均为 `GONE`，且全仓库只有 `activity_video.xml` 声明这两个 id、只有 `VideoActivity.setText` 渲染这两个标签——因此该面板的元数据来自**另一条渲染路径**（OSD 叠加层，`app:id/osd` 子树内）。本次未定位到它的绑定代码，也未修复。建议在下一批（TV-2 卡片与内容层）中先定位再修。
2. **设置项 URL 取值右侧被裁切。** `vodUrl`/`liveUrl` 用 `android:ellipsize="middle"` + `layout_weight="0.7"`，真机上 URL 末尾直接截断且看不到省略号。属内容/布局问题，留给 TV-2。
3. **首页时钟与品牌标题同尺寸同色。** `activity_home.xml` 的 `@id/clock` 为 24sp 纯白，与标题同权重，在无信息价值的情况下抢视觉。留给 TV-2。
4. **`webhtv_focus_scale` 仍为 1.0 而代码里散落硬编码 1.1。** `ThemeTokens` baseline `focusScale=1.0`、`ThemeResolver` 用 1.1，而 `KeepAdapter`/`HistoryAdapter` 各自硬编码 `scaleX(1.1f)`、`TmdbCardFocusHelper` 又复位为 `1f`。属 TV-3（焦点与动效统一）批次。
5. **TMDB 评分色复用播放器 token 且有 2 处硬编码 hex。** `adapter_tmdb_*` 等 5 个布局的评分文字用 `@color/webhtv_color_player_control_active`（设计文档明确该角色"不用于普通 UI"），`TmdbRailAdapter:130` 与 `TmdbHeaderView:71` 还直接写死 `0xFFFFD35C`，自定义主题改不动评分色。属 TV-2。
