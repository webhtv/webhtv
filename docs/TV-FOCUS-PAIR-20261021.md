# TV-FOCUS-PAIR-20261021：TV 搜索范围下拉「选中行看不清」+ 焦点/状态对对比度全面排查

## Recovery anchor

- 目标：修掉电视端搜索页/搜索结果页范围下拉的**焦点行白字压白底**（用户现场照片 `image.png`，sha256 `83e49a09…50a`），并把「焦点/按下/选中态的**填充**与**同态前景**来自不同色族」这一类缺陷用可执行守卫锁死，避免继续「按截图修一屏」。
- lane：`standard`。
- 允许路径（task guard 实际 scope）：`app/src/leanback/res/drawable/selector_search_scope_item.xml`、`app/src/leanback/res/drawable/shape_keyboard_normal.xml`、`app/src/main/res/drawable/selector_display_option.xml`、`app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvFocusStatePairContrastTest.java`、`scripts/verify_scope_focus_contrast.py`、`docs/TV-FOCUS-PAIR-20261021.md`。
- 保护面：开工时 `dev4` @ `687a6b65da9`，工作树干净（预存脏路径 0 个）。
- 已完成证据：守卫先红后绿（红时复现 **1.19:1**）；变异检验（回退 `white_20`→`white_90` 必红）；`check_ui_tokens.sh --strict` PASS；leanback 4437 项 / mobile 5220 项（失败均为已记录的 Windows `renameTo` / 并发 flaky，见 §5）；`assembleLeanbackArm64_v8aDebug` 成功；**真机（`127.0.0.1:5561`）像素采样 6/6 PASS**（焦点行 6.51:1、焦点+选中 6.80:1、选中 6.14:1、默认 10.17:1、键盘 7.07:1、触发器 6.00:1，见 §6）。
- 未覆盖：搜索结果页（`CollectActivity`）分组下拉与屏显弹窗（`DisplayDialog`）受模拟器「无站源配置 / 无 TMDB 入口」限制，未做设备采样；两者与已验证路径共用同一批资源，并由守卫的接线门与服务端断言覆盖（见 §6.3）。
- 下一步唯一动作：无（本任务已交付）。可选后续：接上站源配置后在搜索结果页复测一次分组下拉。

## 1. 根因（数值证据）

### F1（用户截图）

同一控件的两个状态资源来自不同色族：

- 填充 `app/src/leanback/res/drawable/selector_search_scope_item.xml` → `state_focused` 的 `<solid>` = `@color/white_90`（`#E6FFFFFF`，90% 白，近乎不透明白底）；
- 前景 `app/src/leanback/res/color/selector_search_scope_text.xml` → `state_focused` = `@color/display_option_focused` → `@color/webhtv_color_player_control` = **`#FFFFFF`**。

宿主是固定深色玻璃 `shape_search_scope_popup`（`#E62A2955`，90% 不透明）。合成：

| 场景 | 焦点行底色 | 文字 | 对比度 |
|---|---|---|---:|
| 修复前（三档壁纸 × 日/夜） | `#EBEBF0` / `#EBEBEF` / `#EAEAEE` | `#FFFFFF` | **1.19 / 1.19 / 1.20 : 1** |

截图底部「央视」白块即 `state_focused`；顶部「全部」是 `state_selected`（琥珀 15% 底 + `#FFD35C` 字，5.55:1，本身正常）。
两个 TV 页面共用这对资源：`SearchActivity.showScopeMenu()/addScopeItem()`（332–381 行）与 `CollectActivity.showGroupPopup()/addGroupPopupItem()`（402–460 行）。

### F2（排查中发现的第二处线上实例）

`dialog_display.xml` 的屏显弹窗芯片（`DisplayControl`，`app/src/main/res/values/styles.xml:123-138`）：

- 填充 `selector_display_option.xml` 默认/焦点 = `@color/webhtv_color_player_control_muted`（`#CCFFFFFF`，80% 白）；
- 前景 `display_option_text.xml` 默认 = 同一个 `#CCFFFFFF`、焦点 = `#FFFFFF`。

合成到固定深色面板 `shape_display_dialog_panel`（`webhtv_color_player_scrim`）＋画面：

| 场景 | 填充 | 底色 | 文字 | 对比度 |
|---|---|---|---|---:|
| 默认（日/夜 × 两档画面） | `#CCFFFFFF` | `#D9D9D9`–`#D2D2D2` | `#F7F7F7`/`#F6F6F6` | **1.32–1.40:1** |
| 焦点 | `#CCFFFFFF` | 同上 | `#FFFFFF` | **1.41–1.51:1** |

可达性：`TmdbDetailActivity.showInlineDisplay()` → `DisplayDialog.showPlayerOsd()`，TV 侧 `VideoActivity` 会打开 `TmdbDetailActivity`。

### 为什么既有守卫没拦住

| 守卫 | 覆盖 | 漏点 |
|---|---|---|
| `LeanbackForegroundContrastTest` | 布局 `android:textColor` × 最近祖先背景 | 不看 `<selector>` 状态对；弹窗底色/文字是 Java 里成对设置 |
| `SearchScopePopupLayoutTest`（testMobile） | 移动端面板 `surfaceContainer`/`onSurface`、结构存在性 | 不校验 TV 焦点态配对 |
| `check_ui_tokens.py` | 38 组 token 配对 + 硬编码色清单 | 不解 drawable/color 选择器状态 |
| 历史 `docs/TV-*-CONTRAST-*`、`MOBILE-SEARCH-DROPDOWN-CONTRAST-20261009`、`CONTRAST-P0-20261013` | 每次只修「截图那一屏」 | 没有状态对的通用判据，于是同类缺陷在下一个屏幕复发 |

## 2. 方案比较（采用 4）

1. **不变**：焦点行继续 `white_90` 填充 + 白字 → 1.19:1，正是用户报告的缺陷。拒绝。
2. **只改文字**（焦点字改深色）：白底深字 16:1 达标，但焦点环是白色，白环压白底 1.19:1 消失，违反「焦点由环表达」（审计 §9.1），且与同族其它下拉的白色前景不一致。拒绝。
3. **只改底**（焦点底改为半透明白、文字不动）：可行，但 focused 会吃掉 selected，用户聚焦到当前分组时仍看不出「当前选的是哪个」。不完整。
4. **改底 + 合并态（采用）**：焦点填充 `@color/white_20`（`#33FFFFFF`），焦点环保持白色；新增 `focused+selected` 合并态（焦点填充 + 琥珀环），让「焦点」与「当前选中」同时可辨。

F2 同步按同一判据收敛：填充改半透明叠加层（`white_10`/`white_20`），选中态保留琥珀 + 黑字，合并态用黑环。

## 3. 实施

### 3.1 `selector_search_scope_item.xml`（TV 范围下拉）

| 状态 | 填充 | 环 | 配套文字 | 对比度 |
|---|---|---|---|---|
| pressed | `@color/white_90`（不变） | 1.5dp 白 | black | ≈16.6:1 |
| **focused+selected（新增）** | `@color/white_20` | 1.5dp `@color/display_option_bg_selected`（琥珀） | white | 文字 6.3–7.1:1；琥珀环对底 ≥4.4:1 |
| **focused** | `@color/white_20`（原 `white_90`） | 1.5dp `@color/white`（不变） | white | **6.27 / 6.59 / 7.11:1**（三档壁纸）；白环同值 |
| selected | `#26FFE16A`（不变） | 1dp `#99FFE16A`（不变） | `#FFD35C` | 5.55–6.45:1 |
| default | `@color/black_10`（不变） | 无（不变） | `white_90` | ≈8:1 |

说明：环宽/环色不改（白色是「宿主固定深色玻璃」判据，浅色表 FOCUS 槽在该底只有 1.9:1）；文字选择器不改；所有取值与主题/日夜色板无关，自定义焦点色不会回归。`@color/white_20`（`#33FFFFFF`）**色板里本已存在**（`main/res/values/colors.xml:22`），本任务未新增 token。

### 3.2 `selector_display_option.xml`（屏显弹窗芯片，手机/电视共用）

| 顺序 | 状态 | 填充 | 环 |
|---|---|---|---|
| 1 | **focused+selected（新增）** | 琥珀 `..._control_active` | 1.5dp `@android:color/black` |
| 2 | focused | `@color/white_20` | 1.5dp `?attr/tvPlayerRing` |
| 3 | selected | 琥珀（不变） | 1dp `@color/webhtv_color_player_control`（不变） |
| 4 | pressed（复评删除） | 沿用默认 `white_10` | 1dp `@color/white_40` |
| 5 | default | `@color/white_10` | 1dp `@color/white_40` |

**顺序判据（复评修正）**：Android selector 取首个匹配项，按下不能排在焦点/选中**之前**。电视端按确认键时 `state_pressed` 与 `state_focused` 同时成立，按下若排在最前，按住确认键的瞬间会顶掉焦点分支——焦点环消失、填充从 `white_20` 退回更暗的 `white_10`，形成「按下＝失去焦点」的反向反馈；原实现把按下放在第一位，也与本节「焦点+按下时由 focused 给白字」的描述相互矛盾。

**按下分支删除（复评修正）**：按下若保持 `white_10`，它与默认态完全同底，是无反馈的死分支；若调亮到 `white_20`/`white_30`，默认文字 `muted`（`#CCFFFFFF`）在 `0x7C7C7C` 亮画面上分别只有 **4.33:1 / 3.38:1**（`white_10` 为 5.58:1），会引入新的正文不达标。因此删除按下分支：触摸端按下＝默认态，电视端按下由焦点分支表达。守卫里的 `tv.displayOption.pressed` 注册项保留，它现在解析到默认分支（5.58–8.69:1），仍能拦住「未来新增更亮按下底」这类回归。

`display_option_text.xml` **未改**：选中态黑字不变；焦点/按下在「焦点+按下」时由 focused 分支给白字；触摸单按下走默认 `muted`，压在 `white_10` 上亮画面仍有 5.58:1。

### 3.3 `shape_keyboard_normal.xml`（搜索键盘按键，按实测判定）

同一屏的第二类：键盘按键铺在**用户壁纸**上，文字是 `?attr/webhtvColorOnWallpaper`（恒定浅色），没有表面层兜底。设备实测（浅色壁纸）：

| 键底 | 实测底色 | 白字 | 结论 |
|---|---|---|---:|
| `black_20`（原值） | `#7B768C` | `#FFFFFF` | **4.36:1 ✗**（低于正文 4.5:1） |
| `black_40`（采用） | `#5B5667` / `#5C5869` | `#FFFFFF` | **7.07 / 6.87:1 ✓** |

只改搜索键盘专用的 `shape_keyboard_normal.xml`；全应用表面共用的 `shape_item_normal`（同为 `black_20`）不在本任务范围——那是「normal surface 是否要整体加深」的另一项全局决策，见 §7。

## 4. 守卫测试（本任务的「不再犯」机制）

新增 `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvFocusStatePairContrastTest.java`（纯 JUnit + 文件解析，无新依赖）：

1. **状态对注册表**：解析 `@color/` 链式别名、`@android:color/*`、`?attr/*`（真实接线：`colorPrimary*`、`colorSurfaceContainer*`、`tvFocusRing`、`tvPlayerRing`、`webhtvColorOnWallpaper*` 等）、`color/selector_*.xml` 与 `drawable/selector_*.xml` 的 focused/pressed/selected 分支、`<solid>`/`<stroke>`；半透明填充**先合成到声明宿主**再算亮度；日/夜两表各算一遍。
2. **宿主**：范围下拉 = 玻璃 `#E62A2955` 压三档壁纸；屏显芯片 = `player_scrim` 日/夜压两档画面。
3. **阈值**：正文 4.5:1、焦点环 3:1（WCAG 2.2 SC 1.4.3 / 1.4.11）。
4. **完整性门 A**：全库每个「恒定浅色状态前景」（日/夜亮度都 ≥0.7）必须登记或进带理由的豁免表。
5. **完整性门 B**：全库每个「浅色焦点填充 + 恒定浅色焦点环」组合必须登记或豁免——这正是本次缺陷的形态。
6. **接线门**：`SearchActivity`/`CollectActivity` 必须同时引用共享填充与文字选择器，防止只改一边。

豁免表覆盖约 60 个非风险的 color 选择器与 1 个 drawable（`selector_ad_stats_item`，其固定浅底 + 固定深蓝环已由 `TvTabFocusRingPaletteContractTest`/`PlayerSettingsContrastTest` 覆盖），每条写明理由；新增未登记的风险形态会让测试失败并点名文件。

## 5. 验证

| 项 | 命令 | 结果 |
|---|---|---|
| 守卫先红 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests …TvFocusStatePairContrastTest`（修复前） | FAILED：`tv.searchScope.focused … fill=#E6FFFFFF text=#FFFFFFFF bg=#FFEBEBF0 ratio=1.19:1`；`tv.displayOption.default/focused 1.32–1.51:1` |
| 守卫后绿 | 同上（修复后） | BUILD SUCCESSFUL，4/4 |
| 变异检验 | 焦点填充临时改回 `white_90` | FAILED 且精确复现 `1.19:1`（证明测试钉住缺陷，不是空转）；恢复后绿 |
| token 门禁 | `bash scripts/check_ui_tokens.sh --strict` | `UI_TOKEN_STATUS PASS`（`violations=0`，`pairs=38 failures=0 min=4.28`，allowlisted 190 未变） |
| leanback 单测 | `:app:testLeanbackArm64_v8aDebugUnitTest` | 4437 项，2 失败：`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest` —— 既有 Windows `renameTo` 环境问题（`docs/C57-beta-merge-review-dev2-20261010.md:140-143` 已用 merge-base worktree 取证） |
| mobile 单测 | `:app:testMobileArm64_v8aDebugUnitTest` | 5220 项，3 失败：上述两者 + `MultiThreadProxyRouteTest#proxyStreamsParallelShardsAsSingleOrderedPartialResponse` —— 已记录并发 flaky（`docs/C36-beta-merge-review-dev3-20261004.md:52`，隔离重跑 4/4 通过） |
| 资源编译 | `:app:assembleLeanbackArm64_v8aDebug` | BUILD SUCCESSFUL；`app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk`（205,480,228 B，13:24） |
| 真机像素采样 | `python scripts/verify_scope_focus_contrast.py build/device-verify` | **6/6 PASS**（明细见 §6.2）；键盘 4.36 → 7.07:1 |
| 二次构建+安装 | `:app:assembleLeanbackArm64_v8aDebug`（1m43s）+ `adb -s 127.0.0.1:5561 install -r` | BUILD SUCCESSFUL / `Success`（14:46，含键盘 `black_40`） |

## 6. 真机（模拟器 `127.0.0.1:5561`）像素采样

### 6.1 设备与安装

```
adb connect 127.0.0.1:5561          # 分配机：Android 9 / x86_64 / 1920x1080 / 已装 arm64 包（ROM 带 ARM 转译）
adb -s 127.0.0.1:5561 install -r app/build/outputs/apk/leanbackArm64_v8a/debug/app-leanback-arm64_v8a-debug.apk
adb -s 127.0.0.1:5561 shell am start -n com.silent.android.webhtv/com.fongmi.android.tv.ui.activity.HomeActivity
# 方向键居中 = 搜索；右 = 范围触发器；确认 = 展开下拉；下 = 移动焦点
adb -s 127.0.0.1:5561 exec-out screencap -p > build/device-verify/final-popup-focused.png
```

（`192.168.50.3:5555` 在本轮已不可达：`ping` 通但 adbd 拒绝 TCP，故改用分配机端口 5561；该设备与 `emulator-5560` 是同一实例。）

### 6.2 采样结果（数字截图，非翻拍；脚本 `scripts/verify_scope_focus_contrast.py`）

| 场景 | 截图 | 填充 | 文字 | 对比度 | 门槛 |
|---|---|---|---|---:|---|
| 焦点行「当前站」 | `final-popup-focused.png` | `#5A5B7D` | `#FFFFFF` | **6.51:1** | 4.5 ✓ |
| 焦点+选中「全部」（新增合并态，琥珀环） | `final-popup-merged.png` | `#585879` | `#FFFFFF` | **6.80:1** | 4.5 ✓ |
| 选中未聚焦「全部」 | `final-popup-focused.png` | `#4E485A` | `#FFD35C` | **6.14:1** | 4.5 ✓ |
| 未聚焦默认行「当前站」 | `final-popup-merged.png` | `#323259` | `#EBEBEE` | **10.17:1** | 4.5 ✓ |
| 键盘按键（`black_40` 后） | `search-after2.png` | `#5B5667` | `#FFFFFF` | **7.07:1** | 4.5 ✓ |
| 范围触发器「全部」 | `search-after2.png` | `#695E7D` | `#FFFFFF` | **6.00:1** | 4.5 ✓ |

脚本复跑：`python scripts/verify_scope_focus_contrast.py build/device-verify` → 6/6 PASS，退出码 0。
目视确认：焦点行完整落在面板圆角内（用户照片里「底部白块越过圆角」是翻拍透视，不是裁剪缺陷）。

### 6.3 未覆盖的设备场景（说明，不是缺陷）

- 搜索结果页分组下拉（`CollectActivity`）：模拟器没有站源配置，`搜索 → 结果页` 不可达；它使用**同一对** `selector_search_scope_item` / `selector_search_scope_text`，并由守卫的接线门（两个 Activity 都必须引用同一对资源）与注册表断言覆盖。
- 屏显弹窗芯片（`DisplayDialog`）：需要 TMDB 详情页入口；资源层由注册表 5 个状态（default/focused/focusedSelected/selected/pressed）断言覆盖。
- 键盘按键的上限说明：`black_40` 在**极亮壁纸**上仍可能低于 4.5:1（半透明底压在任意壁纸上没有物理保证）；实测该分配机壁纸 7.07:1。若要绝对保证，需要改成不透明底或给键盘加 scrim——属另一项全局决策（见 §7）。

## 7. 遗留与边界

- **播放层纯白画面上限**：`player_scrim` 只有 48%–56% 黑，纯白画面下任何前景的物理上限约 3.7:1。这是播放层既有设计边界（改它会动整个播控层观感），本任务只把芯片的「字与底几乎同色」修到正常画面上 ≥5.7:1，并在守卫里按中等亮度画面（`#7C7C7C`/`#404040`）取值。
- **F2 焦点环未纳入 3:1 断言**：`?attr/tvPlayerRing` 是播放层分区环色，其对**调暗后的玻璃**已由色板派生保证 ≥3:1；叠加 20% 白后环对填充会降到约 1.9:1，与既有 `selector_control_sheet_button` 同族同性质，故守卫只断言文字 4.5:1，环的 3:1 契约仍由 `TvTabFocusRingPaletteContractTest`/播放层契约覆盖。
- **键盘按键（已修，但只是本机达标）**：`shape_keyboard_normal` 由 `black_20`（实测 4.36:1）上调到 `black_40`（实测 7.07:1）。半透明底压任意壁纸没有物理上限，**极亮壁纸**仍可能低于 4.5:1；若要绝对保证，应改为不透明底或给键盘加 scrim。
- **`shape_item_normal` 仍是 `black_20`（未改）**：它被全应用表面（首页菜单、各类列表行、触发器）共用，实测该分配机上触发器 6.00:1 达标；换到极亮壁纸会逼近门槛。这属于「normal surface 是否整体加深」的全局决策，需要独立任务与整屏回归，本任务不扩大面。
- 未改：布局、焦点导航、弹窗宽高/位置、`shape_search_scope_popup` 玻璃色与透明度、移动端搜索下拉实现、`display_option_*` 颜色 token 本体、主题/Resolver/Binder、`WEBHTV-UI-CONFORMANCE-AUDIT-20261013.md` 的架构级条目。

## 8. 回滚

撤销本任务提交即可完全恢复旧行为（纯资源 + 新增测试文件，无数据迁移、无接口/协议/ABI 变化）。旧值：焦点填充 `@color/white_90`、屏显芯片默认/焦点 `@color/webhtv_color_player_control_muted`、键盘键底 `@color/black_20`。
