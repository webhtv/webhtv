# TV-SEARCH-LIGHT-FOCUS-20261025：TV 搜索栏浅色可见性与「焦点色」真正生效

## Recovery anchor

- **目标**：电视版（leanback）上，(1) 调到浅色模式后搜索那一栏的输入框与文字可见；(2) 把主题的「焦点色」改成任意颜色后，TV 上所有 `<selector>` 焦点环与 `android:foreground` 卡片环当场跟随，不再恒为原始颜色。
- **允许路径**：`app/src/main/java/com/fongmi/android/tv/theme/ThemeBinder.java`、`app/src/main/res/values/ids.xml`、`app/src/leanback/res/layout/{activity_search,activity_setting_enhance,dialog_danmaku,dialog_track}.xml`、`app/src/leanback/res/drawable/{shape,selector}_tv_search_field*.xml`、`app/src/leanback/res/drawable/selector_glass_icon_action.xml`、`app/src/leanback/res/drawable/shape_subtitle_{focused,pressed}.xml`、`app/src/main/java/com/fongmi/android/tv/ui/dialog/DanmakuDialog.java`、`app/src/test/java/.../theme/ThemeBinderFocusRingTest.java`、`app/src/testLeanback/java/.../theme/ThemeBinderFocusRingBehaviorTest.java`、`app/src/testLeanback/java/.../ui/activity/{TvSearchSurfaceContractTest,TvFocusRingContractTest,SearchFieldFocusStateTest}.java`、本文件。
- **分支 / 基线**：`dev1` @ `02fc09ede4`，工作树干净。
- **任务守卫**：会话 `C107-BETA-MERGE-REVIEW-DEV1`（mode=standard，scope=`app/src` + `docs`，Base HEAD `a3b7817b3f`，16 个 `--adopt-dirty` 路径）。
- **回滚**：`git checkout -- app/src/leanback/res/layout/activity_search.xml app/src/main/java/com/fongmi/android/tv/theme/ThemeBinder.java` 并删除 3 个新 drawable 与 2 个新测试文件即回到基线；无数据迁移、无 schema 变更。

## 用户报告与判定

用户原话：
1. 「调成浅色模式不知道这个框框移在哪里，就是搜索那一栏。」
2. 「电视版，选中颜色怎么改哪里嘛，焦点选框好像调不了，不管怎么调都是原始颜色。」

经 scoping 确认：界面为**电视版 leanback 原生界面**，期望**直接改代码**让两者生效。

> 注：用户照片里那一行（放大镜 / 蓝色开关 / 文件夹 / 齿轮 + `vodName=` 请求串）在仓库全部原生布局与内置网页中**无匹配**。本任务按「用户实际撞到的两处缺陷」修复，未假设照片的精确入口。

## 缺陷 2 根因：渲染层从未参与主题覆写（机械性）

- TV 全部焦点环都写在 `<selector>` 的 `<item android:state_focused="true">` 里，环色是 `?attr/tvFocusRing`（编译期静态资源）。Android 无公开 API 改写已编译的 `?attr/color*`（`ThemeController.java:384-405` javadoc 已记录）。
- `ThemeBinder` 过去**只改实心填充**：`bindDrawable` 只处理 `InsetDrawable` / `GradientDrawable.setColor` / `MaterialShapeDrawable.setFillColor`，**从不进入 `StateListDrawable` 子项**；`bindView` 也**从不处理 `android:foreground`**（卡片族的环正是 foreground）。
- 于是配置层（`ThemeEditor.Slot.FOCUS` → `ThemeTokens.colorFocus()` → 编辑器预览 / Web 快照）全部正确，**真实界面一处都不跟随** —— 与用户「不管怎么调都是原始颜色」完全一致。
- `docs/THEME-CUSTOMIZATION-LAYERS-20260921.md` 自己把 `StateListDrawable`/`RippleDrawable` 背景列为未完成项，佐证该缺口是已知遗留。

## 缺陷 1 根因：浅色工程漏迁移搜索页

- 2026-10-05 的 TV 浅色工程（`docs/TV-LIGHT-THEME-20261005-*`，方案 B-revised）迁移了 405 处前景，leanback 现有 367 处 `?attr/webhtvColorOnWallpaper`，**但漏掉 `activity_search.xml`**：该文件 0 处 `webhtvColorOnWallpaper`、8 处 `@color/white`。
- 且输入框 `CustomSearchView` 的 `android:background` 是 `@null`，整行**没有任何宿主轮廓** —— 用户「框框去哪了」的答案是该框本就不存在。

## 实现

### A. 焦点环跟随主题（`ThemeBinder.java`）

1. `bindView` 新增 `bindForeground(view, ...)`，覆盖 `selector_vod` / `selector_vod_oval` / `selector_episode_card` 等画在 foreground 上的卡片环。
2. `bindDrawable` 新增 `StateListDrawable` 分支 `bindFocusRingStates`：只改 `state_focused` 子项的 stroke。
3. **改写范围用显式白名单收窄**（`APP_SURFACE_FOCUS_RINGS`，9 项）。`GradientDrawable` 无公开 stroke 宽度/颜色 getter（已用 `javap` 对 android-36/37 `android.jar` 核实），而仓库契约禁止 binder 使用反射/隐藏 API（`ThemeBinderContractTest:105,154-155` 钉住反射关键字；`docs/THEME-CUSTOMIZATION-LAYERS-20260921.md:607,746` 同口径），因此无法「读回旧环色再按需改写」——只能把「确实是应用表面族」的资源逐个列出：
   - 白名单（改写为 `active.colorFocus()`，环宽用唯一 token `R.dimen.webhtv_focus_ring_width`）：`selector_item`、`selector_item_round`、`selector_vod`、`selector_vod_oval`、`selector_keyboard`、`selector_search_hot_word`、`selector_video_item`、`selector_tv_search_field`、`selector_empty_action`；
   - 白名单是**资源名字字符串**而非 `R.drawable.*`：9 个 selector 全在 leanback 源集，本类在 main 源集会被 mobile 变体一起编译，硬引用会让 mobile javac 失败；
   - 入选三条必要条件（已在类内 javadoc 固化）：宿主画在调色板表面 / 焦点态环色走 `?attr/tvFocusRing`（不能是 `?attr/colorOnPrimary` 这类「填充色的配对 on-色」，否则环与填充同色而消失）/ 焦点态宽度已收敛到 token（本类读不回旧宽度，只能按 token 重写）；
   - **主题前置判定保留为必要条件（AND，不是替代）**：只有当视图自身主题解析出的 `?attr/tvFocusRing` 等于 `R.color.tv_item_focus_ring` 时才进白名单比对。原因是 `selector_video_item` 是**双宿主**资源：`dialog_episode_list.xml:13` 与 `dialog_quick_search.xml:14` 根节点用 `@style/ThemeOverlay.WebHTV.GlassFocusRings` 把 `tvFocusRing` 重绑到 `@color/tv_player_focus_ring`，玻璃子树只看内容签名会命中并把环改成 `colorFocus`（浅色表 `#0B57D0` 贴固定深靛玻璃实测仅 1.83:1，不可见）；
   - 显式 `webhtv:focus` tag → 无条件改写（复用 `ThemeRole.fromTag`）。
4. **签名 + 像素指纹两级判定**：先用「填充色 | 圆角 | 内边距」内容签名预筛，再用 48×48 栅格化后的逐像素指纹确认。指纹是必需的——真实碰撞存在（`selector_lab_icon_btn` 与 `shape_search_hot_word_focused` 填充/圆角/内边距完全相同，只差描边 `2dp @color/accent` vs token `?attr/tvFocusRing`），而描边没有公开 getter，只能靠像素区分。
5. **缓存键必须带环色**：`WHITELIST_FINGERPRINTS` 是 `Map<Resources, Map<Integer 环色, Map<签名, Set<指纹>>>>`。`ContextThemeWrapper.getResources()` 返回同一个 `Resources` 实例，但描边色取决于解析主题（应用表面 `#0B57D0` vs 玻璃子树 `#447BF5`），只按 `Resources` 做键会让结果与首次调用顺序绑定。
6. 每个视图的白名单结论（命中**与**未命中）都缓存在 `RING_DECIDED`：仅当「背景实例没换且环色 token 没变」才复用，否则重算（RecyclerView 复用视图会换 drawable）。drawable 用 `WeakReference` 引用，避免「值 → 键」强引用让 `WeakHashMap` 永不回收。

> 方案变更说明（两次）：原批准的 A2-a 是「读回 stroke 原色后匹配」，因「无公开 getter + 禁反射」不可实现；中间版本改为「按视图主题判定 + 无条件改写」，但该判据单独使用恒真（`webhtv_styles.xml:83,134,168` 把所有相关主题都绑到 `tv_item_focus_ring`），实测导致 3dp 卡片族被强制改成 1.5dp（重新露出卡片圆角外月牙）、tvPlayerRing 玻璃族被改色、无描边焦点态被新增描边。最终形态是「显式白名单为主 + 主题判定为辅 + 指纹排除签名碰撞」，三者缺一不可。

### B. 搜索栏浅色可见性

- `activity_search.xml`：8 处 `@color/white` → `?attr/webhtvColorOnWallpaper`（该 attr 两张表恒为 `#FFFFFF`，**深色下零变化**）；hint 用 `@color/webhtv_color_player_control_muted`。
- 新增 `#keywordField` 宿主（`paddingStart/End 16dp`、`paddingTop/Bottom 8dp`），背景 `@drawable/selector_tv_search_field`；输入框本身保持 `background="@null"`，由宿主绘制轮廓。
- **焦点态必须由宿主「借」子控件的状态**：`SearchActivity` 的焦点落点全在子控件 `#keyword`（`SearchActivity.java:185,489,490,500,612` 都作用在它身上），宿主从不 `requestFocus`。若宿主自己 `android:focusable="true"`，`selector_tv_search_field` 的 `state_focused` 分支永远点不亮（浅色下「框又不见了」原症状复发），同时多出一个按下无动作的 D-pad 停靠点。因此宿主改为 `android:addStatesFromChildren="true"` 且**不可聚焦**：子输入框获得焦点时 `ViewGroup` 把子状态并入自己的 drawable 状态，焦点环才真正点亮。此不变量由 `SearchFieldFocusStateTest`（行为级）与 `TvSearchSurfaceContractTest`（源码契约）双向钉住。
- 新增 3 个 drawable：
  - `shape_tv_search_field.xml`：`solid black_40` + 28dp 圆角 + 1dp `?attr/colorOutline`；
  - `shape_tv_search_field_focused.xml`：同底同圆角，stroke 换 `@dimen/webhtv_focus_ring_width` + `?attr/tvFocusRing`；
  - `selector_tv_search_field.xml`：focused → focused shape，默认 → 常态 shape。
- 底色取 `black_40` 的判据与 `shape_keyboard_normal.xml` 同族：白字在 `black_40` 键底约 6.6:1，`black_20` 仅 4.36:1（该文件注释已实测记录）。**未改** `shape_item_normal`（那是全应用表面共用的另一项全局决策）。

## 验证证据

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| Java 编译（电视变体） | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| Java 编译（手机变体） | `:app:compileMobileArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL（`@id/loadHost` 与白名单改名两处 flavor 泄漏确认修复） |
| 电视变体全量单测 | `:app:testLeanbackArm64_v8aDebugUnitTest` | 4697 tests / 8 failed / 3 skipped；8 条失败**全部**是既有 Windows 噪音（2 条 `File.renameTo`、6 条 CRLF 字面量匹配），本任务相关测试全部通过 |
| 手机变体全量单测 | `:app:testMobileArm64_v8aDebugUnitTest` | 5476 tests / 6 failed / 3 skipped；6 条同上噪音类，无本任务相关失败 |
| 整包编译 | `:app:assembleLeanbackArm64_v8aDebug` | BUILD SUCCESSFUL |
| UI token 静态门禁 | `bash scripts/check_ui_tokens.sh --strict` | `violations=4 legacy=0`，**4 处均为既有违规且不在本任务范围**（`fixed_light_focus_icon.xml`、`fixed_light_focus_text.xml`、`shape_dialog_glass_chip_focused.xml`、`shape_player_osd_badge.xml`，`git status` 核对未被本任务修改）。另已核实 **CI 不运行该脚本**（`.github/workflows/` 三个工作流 `android-release.yml` / `cnb-release-sync.yml` / `delayed-dev-promotion.yml` 均未引用它），故不影响 PR/CI |

> `--tests` 过滤器在本机不可用（任何取值都报 `No tests found for given includes`），因此验证一律跑整个 flavor 套件；上表就是套件级结果。

新增测试：
- `ThemeBinderFocusRingTest`（JVM，`src/test` 两 flavor 共用）：钉住 binder 进入 `StateListDrawable`、覆盖 `getForeground()`、只用公开 API（禁反射）、按 `R.attr.tvFocusRing`/`R.color.tv_item_focus_ring`/`R.dimen.webhtv_focus_ring_width` 判定。
- `ThemeBinderFocusRingBehaviorTest`（Leanback，`src/testLeanback`）：**行为级**守卫，逐像素比较 bind 前后位图。白名单 9 项必须被改写且出现活动主题 FOCUS 槽色；排除集 16 项必须逐像素不变；`selector_video_item` 双宿主用 `ContextThemeWrapper(ThemeOverlay.WebHTV.GlassFocusRings)` 正反两种 bind 顺序验证玻璃子树保持视频层环色。
- `SearchFieldFocusStateTest`（Leanback，Robolectric `@GraphicsMode(NATIVE)`）：**行为级**验证 `#keywordField` 在子输入框取得焦点后真的点亮——断言宿主 `addStatesFromChildren()==true` 且不可聚焦、未挂窗时（可靠常态基准）位图不含环色且状态无 `state_focused`、`keyword.requestFocus()` 后宿主 drawable 状态含 `state_focused` 且位图出现 `?attr/tvFocusRing` 实测色。
- `TvSearchSurfaceContractTest`（Leanback）：钉住搜索页无写死白色前景、有 `#keywordField` 实体宿主、环宽/环色唯一 token、圆角与内边距；并断言宿主含 `android:addStatesFromChildren="true"` 且该段内不得出现 `android:focusable="true"`。
- `TvFocusRingContractTest` 增补：`shape_tv_search_field_focused`/`shape_empty_action_focused` 进 themed 数组（唯一宽度 token + `?attr/tvFocusRing`），并新增 `selectorFocus` 断言两个 selector 的焦点态指向对应 shape，防止白名单成员与色板契约静默脱钩。

> 该行为测试曾放在 `src/test`，导致 **mobile 变体单测编译失败**（Robolectric 依赖只声明在 `testLeanbackImplementation`），已移到 `src/testLeanback`。

## 真机验收（5555 模拟器，用户授权）

设备：`127.0.0.1:5555`，API 28，1920x1080，`ro.build.characteristics=tablet`；`adb install -r` 覆盖安装成功。

验收方法说明：设置页内的「外观 → 主题模式 / 主题色」需要遥控逐级导航，成本高且易受弹窗干扰。因此改用**按当前 schema 写入主题 profile 后重启**的方式取证 —— 这条路正是 App 自己的读取路径（`ThemeProfileStore` → `ThemeResolver` → `ThemeController.bindTheme`），并已在过程中定位并排除一个注入坑：**旧键 `theme_profile_json` 已废弃**，当前版本读 `theme_profile_v2_json` / `theme_profile_v2_last_good` / `theme_profile_v2_schema`（`ThemeProfileStore.java:9-12`），schema 版本 2，槽位字段为顶层 `light`/`dark` 对象。

### 焦点色（问题 2）— 已验证生效

| 场景 | 焦点环实测像素 | 结论 |
| --- | --- | --- |
| 默认主题（未覆写） | `#F5F7FF` | 基线 |
| v2 主题 `light.focus=dark.focus=#FF6600` 后重启 | **`#FF6600`** | 焦点环跟随主题 |
| 卡片（`android:foreground` 路径） | 卡片外框同为橙色 | foreground 通道生效 |
| 浅色模式 `theme_mode=0` | 环色随之切换 | 明暗两表都走同一条改写路径 |

覆盖到的控件：首页功能按钮（background `selector_item` 族）、最近观看卡片（foreground `selector_vod` 族）。这两族正好分别验证了 `bindFocusRingStates` 与新增的 `bindForeground`。

### 搜索栏浅色可见性（问题 1）— 已验证

- 默认（深色）：输入框呈可见胶囊轮廓（"关键字…"），不再是裸文字。
- `theme_mode=0`（浅色）：输入框仍有胶囊轮廓，文字/标签可读。实测像素：输入框底 `#4C475E`、标签前景 `#FFFFFF`（对比度约 7:1）。

### 设备复原

验收前已把原偏好备份为 `shared_prefs/prefs.backup.xml`，验收后用其覆盖回 `com.silent.android.webhtv_preferences.xml` 并删除备份；`/data/local/tmp` 下的注入文件已清理。设备偏好已回到验收前状态（键集合与备份一致）。

## 环境说明（已解决）

早期记录过「`bash` 无法在已存在子目录创建文件」。正确结论是：WorkHTV 的 bash 子进程写入被沙箱收敛到工作区，非 ACL 问题（`icacls` 显示 `BUILTIN\\Users:(I)(F)`）。实际可用的执行路径是：

- **源码/文档写入**：DSH 的 `write`/`edit` 工具（不受该限制）。
- **`task_guard.sh`**：经 `G:\Git\bin\bash.exe`（Git for Windows 自带 bash）可正常 `start`/`check`/`finish`。
- **Gradle**：必须在 `run_code` 里用 `spawnSync("cmd.exe", ["/c", "gradlew.bat ..."])`，并显式传 `GRADLE_USER_HOME=G:\GradleCache`、`JAVA_HOME`、`PATH`（`run_code` 的 `process.env` 为空对象）。bash/powershell 直接调 `gradlew.bat` 会因无法创建 `G:\GradleCache\wrapper\dists\...zip.lck` 而失败。

**另一条重要事实**：本项目 `--tests` 过滤器在本机**完全失效**（任何 `--tests` 都报 `No tests found for given includes`，用既有已通过的类 `TvSearchSurfaceContractTest` 也能复现），因此验证只能跑整个 flavor 套件。

## 交付坐标

全部工作已提交并推送，无剩余动作：

| 阶段 | 提交 | 任务守卫 / 标签 |
| --- | --- | --- |
| 合并 `origin/beta 6db0ebab9f` | `a3b7817b3f` | 会话 `C107-BETA-MERGE-REVIEW-DEV1` |
| 评审后修复（白名单 / 搜索栏浅色 / mobile 阻断） | `c8e526321c` | 同上，标签 `recovery/C107-BETA-MERGE-REVIEW-DEV1/20261027183650-c8e526321c40` |
| 再合并 `origin/beta d7d64f4360`（PR #493） | `c99ab92823` | — |
| 搜索栏焦点环真正点亮（`#keywordField`） | `36ef7a63bf` | 会话 `C107-2-SEARCH-FIELD-FOCUS`，标签 `recovery/C107-2-SEARCH-FIELD-FOCUS/20261027203311-36ef7a63bf23` |

PR：[#494](https://github.com/Silent1566/webhtv/pull/494)（`dev1` → `beta`）。
本节之前记录的「下一步唯一动作」现已全部完成；后续同一问题族的新工作应另开任务文档。
