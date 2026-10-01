# NATIVE-ENHANCED-PLAYBACK-STYLE-FOCUS-20260927

原生增强播放页「选中/按钮风格统一 + 焦点链路补全」。

用户原始报告（本任务的验收依据）：

> 请全面梳理优化完善"原生增强"模式的播放页选中效果和按钮风格，现在存在多种不同的按钮风格请统一，
> 选中后的高亮效果也是有白色的，黄色的和没有边框颜色的，看起来很乱。再就是焦点不完善比如：
> 当前聚焦在分组按钮上时遥控往下不会到达选季度的按钮上，评分与数据下的卡片无法选中容易遥控跳过视觉效果不好

## Recovery anchor

- **目标**：统一原生增强播放页的焦点/选中视觉为唯一规范，并修复两条断裂的纵向焦点链路。
- **验收标准**：
  1. 播放页所有可选控件共用一套焦点视觉（唯一取色来源、唯一描边宽度、统一圆角）。
  2. 分段（分组）行按「下」先到集数表头「选集 · 第 N 季」，不再跳过季度按钮。
  3. 「评分与数据」卡片可被遥控选中，处于选集网格与演员行之间，不再被整行跳过。
- **范围**：仅 `app/src/leanback`（TV 播放页）+ `app/src/main` 中播放页专属/主题语义资源 + 对应测试。
- **分支/基线**：`dev1` @ `09f8882251bff5fae9da2d87341242bd359ad4aa`
- **回滚锚点**：本任务提交前的 `dev1` HEAD（同上 40 位 commit id）。
- **状态**：实现完成，双端测试与设备实测均通过，待提交 + recovery tag。
- **下一个动作**：`task_guard.sh finish` 生成原子提交与 `recovery/<task-id>/<timestamp>` 标签。

## 1. 问题定位（为什么会出现三套风格）

播放页（`app/src/leanback/.../activity/VideoActivity.java` + `activity_video.xml`）此前存在多套彼此独立、
在各自文件里写死字面量的焦点规则：

| 控件 | 旧焦点外观 | 旧常态 |
| --- | --- | --- |
| 线路 / 分段 / 清晰度 / 选集文本（`selector_video_item.xml`） | `?attr/colorPrimary` 2dp、4dp 圆角 | 无描边 |
| 集数表头工具按钮 | `selector_button` 1.5dp、28dp 圆角 | 另一套 |
| 选集卡片（`selector_episode_card.xml`） | `#FFD166` 3dp | 无描边 |
| 演员卡（`TmdbCastPresenter`） | `0xFFFFFFFF` 白 3dp | `0x26FFFFFF` 1dp |
| 相关视频（`TmdbVideoPresenter`） | `#FFD166` 2dp | `#33FFFFFF` 1dp |
| 推荐卡（`TmdbRecommendationPresenter`） | **没有任何焦点描边** | 无 |
| 评分与数据卡片（`VideoActivity` 内联构造） | `0x33FFFFFF` 1dp（且 **不 focusable**） | 同左 |

关键事实：**TV flavor 的 `colorPrimary` 硬编码为白色**
（`app/src/leanback/res/values/styles.xml`：`<item name="colorPrimary">@color/white</item>`），
所以「用 `?attr/colorPrimary` 当焦点环」在 TV 上就是用户看到的「白色的」。这正是多套风格同时存在的根因。

同时 `?attr/colorPrimary` 还驱动着 leanback 约 100 个布局的聚焦文字色（`color/text.xml` 等），
因此**不能**把它直接改成焦点环色——必须另立语义属性。

## 2. 决策：唯一焦点规范 + 可主题覆写的语义属性

### 2.1 统一规范（唯一取值来源）

`app/src/main/res/values/colors.xml`：

```xml
<color name="tv_item_focus_ring">#FFD166</color>    <!-- 获得焦点：3dp 圆环 -->
<color name="tv_item_current_ring">#2CC56F</color>  <!-- 当前播放/当前生效：2dp 圆环 -->
<color name="tv_item_normal_stroke">#33FFFFFF</color> <!-- 常态描边：1dp -->
```

统一的**圆角 8dp**；演员卡是 14dp 卡片，其焦点环跟随卡片同半径（14dp），避免描边内缩/外溢。

### 2.2 为什么新增主题属性而不是直接用 `@color/...`

`app/src/main/res/values/attrs.xml` 新增三个可被主题覆写的属性：

```xml
<attr name="tvFocusRing" format="color" />
<attr name="tvCurrentRing" format="color" />
<attr name="tvNormalStroke" format="color" />
```

并在**两个 flavor** 的 `Theme.Base` 里绑定到上面的唯一取值：

- `app/src/leanback/res/values/styles.xml`
- `app/src/mobile/res/values/styles.xml`（`selector_episode_card.xml` 与手机端共用，不绑定会在手机端丢描边）

理由：`selector_episode_card.xml` 被详情页（`TmdbDetailActivity`）和 TV 播放页共用，而详情页有自己的焦点契约
（由 `TmdbDetailActivityLayoutTest` 断言）。走主题属性既能保持「语义化、可主题覆写」的既有设计意图
（原 `selector_video_item.xml` 用 `?attr/colorPrimary` 就是为了可主题化），又不会污染全局白色 `colorPrimary`。

同时更新了原先把 `?attr/colorPrimary` 写进断言的 `ThemeTvCatalogSourceTest`
——它保护的是「焦点语义必须是主题属性而非硬编码」这一**意图**，属性名从 `colorPrimary` 收敛到 `tvFocusRing`，
意图不变、语义更准。

### 2.3 `state_selected` 不是「当前播放」

`selector_video_item.xml` 的消费方（`adapter_episode` / `adapter_flag` / `adapter_quality` / `adapter_array`）
都用了 `android:ellipsize="marquee"`，而 Android 的跑马灯只在 View 处于 `selected` 或 `focused` 时滚动，
因此 `EpisodeAdapter` 里是 `setSelected(isSelected() || hasFocus())`。

-> `state_selected` 在这里是**跑马灯开关**，不是语义上的「当前生效」。
给它加描边会把「标题过长正在滚动」的项误显示成「当前播放」。
「当前生效」态统一走 `state_activated`（`ArrayAdapter.setActivated`）。这一约束已写进该 selector 的注释与测试。

## 3. 实现

### 3.1 视觉统一

- `app/src/leanback/res/drawable/selector_video_item.xml`：焦点 3dp `?attr/tvFocusRing`，当前 2dp `?attr/tvCurrentRing`，
  常态补 1dp `?attr/tvNormalStroke`（消除用户说的「没有边框颜色的」），圆角 4dp -> 8dp。
- `app/src/main/res/drawable/selector_episode_card.xml`：加 `state_activated` 2dp 当前态，圆角统一 8dp。
- `app/src/main/res/drawable/selector_tmdb_cast_focus.xml`：白色 4dp -> 3dp `?attr/tvFocusRing`，激活态改 2dp 当前态。
- `app/src/leanback/res/layout/activity_video.xml`：集数表头 4 个工具按钮从 `selector_button`（28dp 圆角、
  白色 1.5dp 焦点环，同一页最显眼的另一套风格）改为复用统一芯片 selector。
- 三个 leanback presenter 收敛到同一组常量：
  - `TmdbCastPresenter`：常态改 1dp `0x33FFFFFF`，**移除**自己的白色焦点描边（改由 foreground 的
    `selector_tmdb_cast_focus` 统一绘制，避免「卡片描边 + foreground」双重描边）。
  - `TmdbVideoPresenter`：焦点 2dp -> 3dp `0xFFFFD166`，常态 1dp `0x33FFFFFF`。
  - `TmdbRecommendationPresenter`：**新增** `applyUnifiedFocus`（此前完全没有焦点描边，遥控移过去看不出停在哪）。
- 评分卡片（`styleRatingChipBackground`）：常态保留原有深色玻璃底 `0x6610141A` + 1dp 半透明描边
  （保证白/黄文字在明亮剧照上可读），获得焦点时换 3dp `tv_item_focus_ring` 圆环，并把
  「评分与数据」标签文字同步染色。

**边界决策（重要）**：`adapter_tmdb_*.xml` 这 5 个布局与独立详情页共用。曾尝试直接改共享布局，
但立刻触发 `TmdbDetailActivityLayoutTest` 的 5 个断言失败——那 5 项是详情页的既有契约。
故**回滚共享布局改动**，把统一焦点环落在 leanback 专属的 presenter 上。
测试改为同时锁定「共享布局不被写入播放页规则」这一边界。

### 3.2 焦点链路

#### 问题一：分段行按「下」跳过季度按钮

（`VideoActivity.onArrayKey`）

旧代码直接 `selectEpisodeSegment(position, true)`，把焦点推进选集网格，跳过了整行集数表头。
改为：只装载分段（`selectEpisodeSegment(position, false)`，不抢焦点）-> 由新增的
`focusEpisodeHeaderTool(View.FOCUS_DOWN)` 显式把焦点交给声明的下方目标「选集 · 第 N 季」->
只有该目标不可用时才回退 `scrollToEpisode(...)`。

#### 问题二：评分与数据卡片被整行跳过

根因有两条，都要修：

1. 评分卡片由 `createOmdbRatingChip` 内联构造，是纯装饰 `LinearLayout`，**没有** `setFocusable(true)`，
   所以遥控上下键必然跳过整行。改为 `setFocusable(true)` + 焦点监听。
2. 即使可聚焦，它也不在纵向焦点链里。`R.id.tmdbOmdbRatings` 加入 `getEpisodeFocusOrders()`
   （位置：`episodeGrid` 之后、`tmdbCast` 之前），并新增 `updateRatingChipFocus()`
   在 `updateFocus()`、`renderTmdbRatingChips()`、`hideTmdbRatingChips()` 中重算各 chip 的
   `nextFocusUpId` / `nextFocusDownId`——**必须在异步到达后重算**，因为 OMDB 回包是异步的。

   注意 trim 后的边界：当没有评分数据时该容器是 `GONE`，`isEpisodeFocusTarget` 会因 `isVisible` 为 false
   而跳过它，焦点链自动退回「网格 -> 演员行」，不会卡死。

3. 集数卡片由 adapter 动态构建，不会把 `nextFocusDown` 写回卡片项；所以选集网格**末行**向下必须由
   `onEpisodeKey` 显式接管：新增 `focusRatingRow()`，仅在网格内确实没有下一行
   （`TmdbEpisodeGridPolicy.NO_FOCUS_TARGET`）时才生效。

## 4. 验证

### 4.1 契约测试（静态、确定性）

`app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/NativeEnhancedPlaybackStyleFocusTest.java`（新增，9 项）：

```text
tests 9 failures 0 errors 0
  ok  unifiedFocusTokensAreDeclaredOnceAndThemeable
  ok  everySelectableSurfaceSharesOneFocusSpec
  ok  chipSelectorKeepsCurrentStateAtTwoDpAndNormalStateAtOneDp
  ok  playbackCardsOwnTheUnifiedRingInLeanbackPresentersWithoutTouchingSharedLayouts
  ok  episodeHeaderToolsUseTheSameChipSelectorAsRouteAndSegmentRows
  ok  presentersUseTheUnifiedFocusRingAndWidth
  ok  segmentRowDownReachesTheSeasonButtonBeforeTheEpisodeList
  ok  ratingCardsSitBetweenTheEpisodeListAndTheCastRowInTheFocusChain
  ok  ratingCardsAreFocusableAndCarryTheUnifiedRing
```

命令：`bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.ui.activity.NativeEnhancedPlaybackStyleFocusTest"`

> 说明：该文件曾被一次「测试优于实现」的反向修补，出现 3 项与既定决策不符的陈旧断言
> （把注释里的 `#FFD166` 当作实现硬编码、要求演员卡 8dp 圆角、依赖已回滚的共享布局改动、期望不存在的
> `applyUnifiedChipFocus`）。已改为锁定真实最终契约，并新增 `values()` 助手在断言前剥离 XML 注释
> ——注释里的取值映射是文档，不是实现。

### 4.2 回归（既有用例）

- `:app:testMobileArm64_v8aDebugUnitTest` + `:app:testLeanbackArm64_v8aDebugUnitTest`
  全量：**4955 tests, 2 failed, 2 skipped**。
- 这 2 项为 `TmdbUIAdapterTest.leanbackDirectTmdbPlaybackHydratesSynopsisWithoutFullDetailBind` /
  `tmdbDetailActivityPassesMemoryDetailCacheKeyToDirectPlayback`，已用 `git stash` 在**干净基线上复现**
  （干净基线：62 tests, 2 failed），属既存失败，与本次改动无关。
- 曾因改动而失败的 3 项已随实现收敛而通过：`ThemeTvCatalogSourceTest.coreTvResourcesUseSemanticAttributes`、
  `TmdbDetailActivityLayoutTest.nativeEnhancedEpisodeCardsUseUnifiedTvFocusAndPlayingState`、
  `VideoActivityLayoutTest.leanbackHistoryPlaybackBindsAndSelectsTheCurrentEpisodeSegment`
  ——这三项原本编码的是用户报告的**错误**行为（分段行向下直接抢焦点），已随契约修正更新。

### 4.3 最终 APK 的编译产物证明

对 17:29 的最终 leanback APK 用 `aapt2 dump resources` 核对，
确认属性绑定与 selector 引用真的进了包（而不只是源码里写了）：

```text
0x7f060555 color/tv_item_current_ring
0x7f060556 color/tv_item_focus_ring
0x7f060557 color/tv_item_normal_stroke
tvCurrentRing(0x7f0406ec)=@color/tv_item_current_ring
tvFocusRing(0x7f0406ed)=@color/tv_item_focus_ring
tvNormalStroke(0x7f0406ee)=@color/tv_item_normal_stroke
```
（上面三条绑定挂在 leanback `Theme.Base`，其 parent 为 `Theme.Material3.Dark.NoActionBar`）

`selector_video_item.xml` 编译后：
`state_focused` -> `stroke width=3.0dp color=?0x7f0406ed`；
`state_activated` -> `width=2.0dp color=?0x7f0406ec`；
默认 -> `width=1.0dp color=?0x7f0406ee`；圆角 `8.0dp`。

### 4.4 设备实测（192.168.50.3:5555，覆盖安装，未卸载）

覆盖安装最终包：`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5555`。

**问题一（分段行向下 -> 季度按钮）**，逐步焦点追踪：

```text
start        FOCUS: video         [48,48][848,498]
DOWN#1       FOCUS: text | baidu  [48,578][175,653]
DOWN#2       FOCUS: text | 1-20   [272,677][383,752]     <- 分段/分组按钮
DOWN#3       FOCUS: episodeTitle | 选集 · 第 1 季 [48,776][352,857]   <- 到得了季度按钮
DOWN#4       FOCUS: cardContainer [48,546][480,926]      <- 再往下才进选集
```

**问题二（评分与数据卡片可选中、不被跳过）**，以《陈情令 第 1 季》为样本
（TMDB 匹配成功：年份 2019、类型、简介、集数均由 TMDB 提供），纵向焦点链：

```text
DOWN#4  cardContainer  [1416,700][1848,1080]  kids=['2019-07-10 / 4','20. 魏无羡涅槃重生']  <- 选集网格
DOWN#5  LinearLayoutCompat [48,962][288,1080] kids=['TMDB', '8.6/10']      <- 评分与数据卡片（此前被整行跳过）
DOWN#6  CardView       [48,721][228,1056]  kids=['肖战','Wei Wuxian']      <- 演员行
DOWN#7  CardView       [48,832][488,1080]
DOWN#9  CardView       [48,745][228,1080]  kids=['郑伟文','导演']          <- 主创
DOWN#10 CardView       [48,756][264,1080]  kids=['TMDB 8.0','豆瓣 8.5','山河令']   <- 推荐
```

顺序与 `getEpisodeFocusOrders()` 完全一致：`episodeGrid -> tmdbOmdbRatings -> tmdbCast -> ... -> tmdbRecommendations`。

**焦点环视觉证据**：聚焦「TMDB 8.6/10」卡片截图，像素扫描其边框：

```text
top  edge @x=168: y=546..551 = (255,215,121)   <- 连续 6px
left edge @y=605: x=48..53   = (255,215,121)   <- 连续 6px
```
即 **3dp**（该模拟器密度下 6px）黄色圆环，同半径圆角；同排未聚焦的「豆瓣 7.7/10」「IMDB 8.7」保持 1dp 半透明描边。
肉眼可辨：聚焦项为黄环、非聚焦项为细灰描边，不再有「白色的」「没有边框颜色的」两种风格。

环境备注（与本次改动无关，均为该模拟器既存问题）：设备 `window` 服务 dump 会 hang（`SERVICE 'window' DUMP
TIMEOUT`），需 `adb reboot` 恢复；启动期 `systemui`/`launcher3` 出现 ANR 对话框，已用
`settings put global hide_error_dialogs 1` 抑制；播放期 IJK 引擎 ANR 是既存问题。

## 5. 涉及文件

聚焦语义与视觉（`app/src/main`）：
`res/values/colors.xml`、`res/values/attrs.xml`、`res/drawable/selector_episode_card.xml`、
`res/drawable/selector_tmdb_cast_focus.xml`、`java/.../ui/custom/TmdbHeaderView.java`

TV 播放页（`app/src/leanback`）：
`res/drawable/selector_video_item.xml`、`res/layout/activity_video.xml`、`res/values/styles.xml`、
`java/.../ui/activity/VideoActivity.java`、
`java/.../ui/presenter/TmdbCastPresenter.java`、`TmdbVideoPresenter.java`、`TmdbRecommendationPresenter.java`

手机 flavor 主题绑定：`app/src/mobile/res/values/styles.xml`

测试：`app/src/testLeanback/.../NativeEnhancedPlaybackStyleFocusTest.java`（新增）、
`app/src/testMobile/.../theme/ThemeTvCatalogSourceTest.java`、
`app/src/testMobile/.../ui/activity/TmdbDetailActivityLayoutTest.java`、
`app/src/testMobile/.../ui/activity/VideoActivityLayoutTest.java`

## 6. 回滚与风险

- **回滚**：单个原子提交，直接 `git revert <commit>` 或 `git reset --hard` 到本任务提交前的 `dev1` HEAD
  （`09f8882251bff5fae9da2d87341242bd359ad4aa`）。改动不含数据库、不含原生 ABI、不含构建配置，回滚无副作用。
- **兼容性**：新增的三个属性都带 `format="color"` 且在两个 flavor 的 `Theme.Base` 绑定；
  第三方自定义主题若不绑定，则 `selector` 取不到值（表现为无描边），不会崩溃。既有主题（含 `Theme.App.Lab`）均继承 `Theme.Base`。
- **未改动**：`colorPrimary` 保持白色不变，避免影响 leanback 约 100 个布局的聚焦文字色。
- **性能**：仅新增少量属性查找与焦点监听，无新增网络/解码/布局层级。

## 7. 最佳实践研究（简要）

- Android 官方 `StateListDrawable` 语义：`state_focused` 表「输入焦点」、`state_activated` 表「持久选中」、
  `state_selected` 表「瞬时选择/常用于跑马灯」。本任务据此把「当前播放」从误用的 `state_selected` 收敛到 `state_activated`，
  并保留 `state_selected` 只做跑马灯开关——与 `TextView.setSelected` + `ellipsize="marquee"` 的既有实现一致。
- Android TV 焦点最佳实践：相邻控件应显式声明 `nextFocusUpId`/`nextFocusDownId`，
  否则 `FocusFinder` 依赖几何位置，在网格/滚动容器里会产生跳行。
  集数卡片由 adapter 动态构建（无法写回 XML 属性），故末行必须由按键处理显式接管——这是本任务 `focusRatingRow()` 的依据。
- Material `MaterialCardView`：`strokeColor` 与 `foreground` 同时画描边会产生双重描边，
  故演员卡把焦点环完全交给 foreground selector。
