# TV-HOME-MENU-CONTRAST-20261009：TV 首页菜单键「选项弹窗」焦点条目文字看不清

## Recovery anchor

- 目标：修复 TV 版首页菜单键弹窗（`HomeMenuDialog`）焦点条目的文字不可读（用户现场照片 `F:\temp\orca-paste-1791512481791-4c838749-fd81-4eb2-aed0-ffb986b7b7d7.png`），使 11 个条目在默认调色板、自定义调色板与日夜两套 token 表下文字对条目底色均 ≥ 4.5:1（WCAG 2.2 §1.4.3）。
- 允许路径：`app/src/leanback/res/color/home_menu_text.xml`（新增）、`app/src/leanback/res/layout/adapter_home_menu.xml`、`app/src/testLeanback/java/com/fongmi/android/tv/ui/dialog/HomeMenuDialogContrastTest.java`（新增）、本文件。
- 保护面：任务 guard 启动时工作区干净（预先存在脏路径 0 个）。
- 验收：① 焦点/按下文字角色与焦点实心底色角色不同源；② 两套 token 表下焦点 6.39:1 / 7.50:1、常态 14.73:1 / 12.14:1；③ 设备实测（`192.168.50.3:5557`，用户当前的青色自定义主题）焦点条目由 1.01:1 提升到 6.39:1，其余 10 项保持 14.71:1；④ 焦点在条目间移动后新焦点项同样达标；⑤ 定向 JVM 测试、leanback 编译、覆盖安装全部通过。
- 回滚：撤销本任务原子提交即可恢复到「焦点文字 = colorPrimary」的旧行为。
- 下一步唯一动作：无（已交付）。

## 1. 根因（设备实测证据）

条目布局 `app/src/leanback/res/layout/adapter_home_menu.xml` 的文字色是共享的 `@color/config_history_text`，
而该选择器的焦点/按下项取 `?attr/colorPrimary`；
条目底色 `selector_config_history_item` 的焦点项来自 `shape_config_history_item_focused`，实心填充**同样是** `?attr/colorPrimary`。
于是焦点态文字与焦点态底色是同一个角色，两者之间没有对比度可言：

- 编译表（`app/src/main/res/values/webhtv_tokens.xml`，leanback 合并结果实测 `webhtv_color_primary=#0B57D0`）下为 `#0B57D0` on `#0B57D0` = **1.00:1**，即完全看不见。
- 用户设备上是自定义调色板，缺陷以另一种形式出现（见下）。

设备实测（`192.168.50.3:5557`，1920×1080，安装修复前包，按遥控器菜单键）：

| 位置 | 填充实测 | 文字实测 | 对比度 |
|---|---|---:|---:|
| 焦点项「切换站源」 | `#0B57D0` | `#006876` | **1.01:1** |
| 其余 10 项（常态） | `#ECEEF4` | `#171D1E` | 14.71:1 |

文字为什么是青绿色 `#006876` 而不是蓝色：设备已启用自定义主题（`shared_prefs/com.silent.android.webhtv_preferences.xml`
的 `theme_color=-16732991` 即 `#00ACC1`），`ThemeController`/`ThemeBinder` 把视图上「等于基线角色色」的颜色改写成当前 profile 的角色色。
`ThemeBinder.bindDrawable()` 只解包 `InsetDrawable`/`GradientDrawable`/`MaterialShapeDrawable`，**不处理 `StateListDrawable`**，
所以：文字（普通 `ColorStateList`）被改写成主题主色 `#006876`，而条目焦点底色（`StateListDrawable` 内的 `GradientDrawable`）仍是基线蓝 `#0B57D0`。
同一张截图里弹窗面板是 `#E3E9EA`（`shape_config_history_dialog` 是普通 `<shape>`，被正确改写成主题面板色），正是这条不对称规则的旁证。

因此这不是「主题风格」问题，而是两处缺陷叠加：焦点文字与焦点底色同角色（根因），以及该 `StateListDrawable` 填充不随主题改写（放大器）。

## 2. 证据来源

访问日期：2026-10-09（China Standard Time）。

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| 用户现场照片 | `F:\temp\orca-paste-1791512481791-…png` | A | 焦点项文字压在蓝色实心胶囊上，肉眼不可读；与设备复核一致 |
| 本仓库 `adapter_home_menu.xml` / `config_history_text.xml` / `shape_config_history_item_focused.xml` | HEAD `7df98107be` | A（本地） | 焦点文字与焦点填充同为 `?attr/colorPrimary`，这是根因的静态证据 |
| 本仓库 git 历史 | `161b896b25` / `7e5106e055`（2026-05-30 上游导入）、`0503f8e3cf`（2026-09-14 theme revival）、`1bb72bd709`（2026-09-26） | A（本地） | 上游导入时 `config_history_text` 焦点色是 `@color/white`、`shape_config_history_item_focused` 填充是硬编码 `#2F6FED`，「白字压在蓝色实心胶囊上」的本意与当时实现一致；`0503f8e3cf` 把焦点文字改成 `?attr/colorPrimary`（当时填充仍是 `#2F6FED`），`1bb72bd709` 再把填充由 `#2F6FED` 改成 `?attr/colorPrimary`，两者叠加才成为「同一角色压自身」。`adapter_home_menu.xml` 由 `e95b90d3a8`（2026-08-23）创建、创建即复用共享选择器，历史上从未直接写 `@color/white`（本条原表述有误，已更正，见 §8） |
| 本仓库 `ThemeBinder` / `ThemeColorIndex` 类注释与 `ThemeBinderContractTest` | HEAD `7df98107be` | A（本地） | 说明「无公开 API 改写已编译的 `?attr`」「共享基线色（白）在角色不一致时保持不动」「`StateListDrawable` 不在改写范围内」，决定了解法与不扩大范围的理由 |
| 本仓库 `config_history_icon.xml` / `site_item_text.xml` | HEAD `7df98107be` | A（本地） | 同一弹窗家族已有的正确约定：焦点文字/图标取 `colorOnPrimary`；本次沿用而不是新造模型 |
| `dialog_outlined_button_bg/text.xml` + `dialog_history.xml` | HEAD `7df98107be` | A（本地） | 证明共享的 `config_history_text` 不能直接改成 `colorOnPrimary`：它同时服务于焦点底色为 `primaryContainer` 的描边按钮 |
| WCAG 2.2 §1.4.3 | `https://www.w3.org/TR/WCAG22/` | A | 正文阈值 4.5:1，作为验收数字 |
| Material Design 3 颜色角色 | `https://m3.material.io/styles/color/roles` | A | `onPrimary` 就是 `primary` 填充上的前景角色 |

不适用类别记录：本改动只涉及静态资源角色接线，不涉及解码/渲染/ABI/打包，无需上游播放器依赖类证据。

## 3. 方案比较与采用

1. **不变更**：焦点文字继续与焦点底色同角色。拒绝，正是用户报告的不可读现象。
2. **把 `config_history_text` 的焦点色改成 `?attr/colorOnPrimary`**：会连带 `dialog_history.xml` 的描边按钮，其焦点底色是 `primaryContainer`（`#D3E3FD`），白字压上去约 1.4:1，属于明确的回归。拒绝。
3. **焦点文字硬编码 `@color/white`**：能修好设备现象，但退回硬编码，且与同弹窗家族的 `config_history_icon.xml`（语义角色）不一致，自定义主题的日夜/foreground 语义再次脱钩。拒绝。
4. **窄化适配（采用）**：为菜单条目新增专用选择器 `app/src/leanback/res/color/home_menu_text.xml`（焦点/按下 `?attr/colorOnPrimary`，常态 `?attr/colorOnSurface`），`adapter_home_menu.xml` 改用它；共享的 `config_history_text` 保持原样。
5. **让条目底色也运行时跟随主题**（按 `SiteDialogTheme`/`AppearanceRowTheme` 先例用 `ThemeController.current()` 生成背景）：能同时消除 §1 的「放大器」，但要新增主题渲染器、重绘通道与刷新时机，属独立范围；且会让本弹窗的胶囊与 `adapter_config`、`dialog_site` 的同类胶囊（同样靠 `StateListDrawable`）外观不一致，形成新的不一致。本轮不采用，按 §6 报告。

取舍：采用 4 后，焦点文字在两套编译表下分别是 `#FFFFFF`（对 `#0B57D0`）= 6.39:1 与 `#062E6F`（对 `#A8C7FA`）= 7.50:1；
且 `#FFFFFF` 在基线里被 onPrimary/onError/onSecondary/onTertiary/onSuccess/onWarning 等多个角色共享，`ThemeColorIndex.replacementFor` 判定为歧义并保持原色，
所以自定义主题下焦点文字不会跟着主题乱走——设备实测正是纯 `#FFFFFF`。

## 4. 实施

1. 新增 `app/src/leanback/res/color/home_menu_text.xml`，头注释记录「焦点底色是 `colorPrimary` 实心，焦点文字必须是 `colorOnPrimary`」这条不变量与共享选择器不能合并的原因。
2. `app/src/leanback/res/layout/adapter_home_menu.xml`：`android:textColor` 由 `@color/config_history_text` 改为 `@color/home_menu_text`；背景、字号、自适应字号、`focusable` 等全部不动。
3. 新增 `app/src/testLeanback/java/com/fongmi/android/tv/ui/dialog/HomeMenuDialogContrastTest.java`：从 XML 解析角色接线断言 + 用仓库自身的 token 表算对比度（含「旧写法必然是 1:1」的回归钉）。

## 5. 不变量与回滚

- 不改动 `config_history_text.xml`、`config_history_icon.xml`、`shape_config_history_item_*`、`selector_config_history_item`：`adapter_config` 的编辑/删除图标、`dialog_history` 的描边按钮行为完全不变。
- 不改动 `HomeMenuDialog.java` 的条目数、列数、间距、`onHomeMenuItem` 下标映射与焦点落点。
- 条目尺寸/字号/自适应缩字/圆角/描边宽度不变，仅文字色。
- 回滚：撤销本任务原子提交。

## 6. 验证记录

### 6.1 定向 JVM 测试

`JAVA_HOME="C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot" bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.dialog.HomeMenuDialog*' --tests 'com.fongmi.android.tv.theme.*'` → `BUILD SUCCESSFUL in 2m 42s`。

- `HomeMenuDialogContrastTest`：3 项，0 失败。
  - `menuItemTextUsesOnPrimaryForThePrimaryFilledFocusState`：焦点/按下必须 `?attr/colorOnPrimary`，且不得再出现 `?attr/colorPrimary`；并与 `config_history_icon.xml` 约定一致。
  - `adapterBindsTheOnPrimarySelectorAndLeavesTheSharedOneAlone`：布局必须接新选择器、不得再引用 `config_history_text`；焦点填充确实来自 `colorPrimary`；共享选择器保持 `colorPrimary`（约束它只能服务于 `primaryContainer` 描边按钮）。
  - `focusedAndNormalTextMeetWcagAaInBothPalettes`：按 `values/` 与 `values-night/` 两套 token 表解析真实十六进制值并计算对比度——焦点 6.39:1 / 7.50:1、按下同值、常态 14.73:1 / 12.14:1，全部 ≥ 4.5:1；同时把旧写法 `colorPrimary` on `colorPrimary` = 1.00:1 < 4.5 钉成回归断言。
- `HomeMenuDialogSourceTest`（既有，5 项）与 `com.fongmi.android.tv.theme.*` 全族仍全部通过，证明未触碰导航契约与主题系统契约。

### 6.2 编译与覆盖安装

`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5557` → `BUILD SUCCESSFUL in 1m 25s`，APK 191M，`adb install -r` 覆盖安装成功（未卸载设备现有包，符合约定）；`dumpsys package com.silent.android.webhtv` 的 `lastUpdateTime=2026-10-09 11:01:19` 与本次安装一致。

### 6.3 设备实测（192.168.50.3:5557，1920×1080，用户当前的青色自定义主题）

路径：首页 → 遥控器菜单键。修复前截图 `F:\temp\before_menu.png`、修复后 `F:\temp\after_menu.png`（同机同主题，仅换安装包）。
测量方法：先在弹窗区域内用底色掩膜连通域自动定位 11 个胶囊的位置（不是写死坐标），再对每个胶囊取「出现次数最多的非底色颜色」作为字形核心色，按 WCAG 相对亮度算比。

| 条目 | 修复前 | 修复后 |
|---|---|---|
| 焦点项「切换站源」 | `#006876` on `#0B57D0` = **1.01:1** | `#FFFFFF` on `#0B57D0` = **6.39:1** |
| 「切换线路」/「直播页面」（常态） | `#171D1E` on `#ECEEF4` = 14.71:1 | 同左，未变 |
| 第 2–4 行共 8 项（常态） | 14.71:1 | 同左，未变 |

焦点移动复核（证明修复作用于全部条目而不是第 0 项）：`KEYCODE_DPAD_RIGHT` 后焦点落在「切换线路」→ `#FFFFFF` on `#0B57D0` = 6.39:1；再 `KEYCODE_DPAD_DOWN` 落在「搜索页面」→ 6.39:1（截图 `F:\temp\after_right.png`、`F:\temp\after_down.png`），且原焦点项正确回到常态配色。最差一项 6.39:1 ≥ 4.5:1。

### 6.4 默认调色板与深色模式

- 默认 profile 下 `ThemeBinder.bind()` 在 `baseline.equals(active)` 时是纯 no-op（类注释与 `ThemeBinder` 源码显式保证），且设备实测的焦点填充 `#0B57D0`、焦点文字 `#FFFFFF` 正是编译表 `colorPrimary`/`colorOnPrimary` 的值——即默认 profile 渲染出的就是这两个颜色，6.39:1，无需换设备设置再验一遍（复核默认主题需要改用户现存的青色主题配置，风险大于收益）。
- 深色（`values-night/webhtv_tokens.xml`，`colorPrimary=#A8C7FA`、`colorOnPrimary=#062E6F`）由 6.1 的同一断言覆盖为 7.50:1；`ThemeController.resolvedDark()` 已说明该 flavor 与 mobile 一样走同一套 day/night 表，因此深色下静态解析得到的正是该配对。

### 6.5 结论

用户报告的「电视版首页菜单键界面看不清文字」在该设备上已消除：焦点条目由 1.01:1 提升到 6.39:1，其余条目保持 14.71:1，最差一项 6.39:1 ≥ 4.5:1。

## 7. 相邻但未修复（超出本次范围，仅报告）

1. `ThemeBinder.bindDrawable()` 不解包 `StateListDrawable`，所以凡是「状态列表内 `<shape>` 填充」的控件在自定义主题下都保持基线色（反之其中的文字会被改写）。本弹窗的胶囊、`adapter_config` 的编辑/删除图标、`dialog_site` 的动作按钮都属此类；证据：本设备（青色主题）焦点胶囊填充实测仍是基线蓝 `#0B57D0`，而同屏弹窗面板已被改写为主题面板色 `#E3E9EA`。修复它属于主题系统范围（`ThemeBinder` + 其契约测试），建议单独建任务。
2. `app/src/leanback/res/color/site_action_icon.xml` 的焦点项取 `?attr/colorPrimary`，而它服务的 `selector_site_action` 焦点底色同样是 `colorPrimary` 实心填充（`dialog_site` 的站源动作按钮），与本次是同一类缺陷；该弹窗不在用户报告路径上，本次未改。
3. `config_history_text.xml` 同时服务于「焦点底色为 `colorPrimary` 实心」的旧胶囊与「焦点底色为 `primaryContainer`」的描边按钮，两个消费者对焦点文字的要求相反；本次通过给菜单条目单独建选择器回避，长期建议把两个用途拆开。

## 8. C51 复评更正与再验证（2026-10-09）

复评编号 `docs/C51-beta-merge-review-dev2-20261009.md` 对本文全部事实性引用做了逐条核对，命中 1 处不实描述并已更正：

- **更正**：§2 原表述把「上游设计本意（白字压在 `#2F6FED` 实心胶囊上）」与「`adapter_home_menu.xml` 曾经直接写 `@color/white`」混为一谈。核对 `git log --all --full-history -- app/src/leanback/res/layout/adapter_home_menu.xml` 与 `git show 161b896b25^:<path>`：该布局由 `e95b90d3a8`（2026-08-23）创建、创建即用 `@color/config_history_text`，从未写过 `@color/white`，也不存在于 `161b896b25`（2026-05-30）。根因链更正为：`0503f8e3cf` 把焦点文字由 `@color/white` 改成 `?attr/colorPrimary`（当时填充仍是 `#2F6FED`），`1bb72bd709` 把填充改成 `?attr/colorPrimary`，两者叠加成为同一角色。
- **再验证**：本次更正只改文档表述，代码与测试零改动（`git diff origin/beta` 仍是本任务的 4 个路径）；C51 独立复算对比度（焦点 6.39 / 7.50、常态 14.73 / 12.14、旧写法 1.00）与 §6.1 一致；`scripts/check_ui_tokens.sh` 与合并前基线逐项相同（violations=1 / hex_layouts=1 / pairs=38 / failures=0 / min=4.28，PASS）；leanback `HomeMenuDialogContrastTest` 3/3、`HomeMenuDialogSourceTest` 5/5、`theme.*` 162 项全部通过。
- 本文其余引用均逐条核对通过：`ThemeBinder.bindDrawable()` 只解包 `InsetDrawable` 并处理 `GradientDrawable`/`MaterialShapeDrawable`（不含 `StateListDrawable`）；`dialog_outlined_button_bg.xml` 的焦点/按下底色确为 `webhtv_color_primary_container`；`site_action_icon.xml` 焦点色与 `selector_site_action` 焦点填充同为 `?attr/colorPrimary`（§7 第 2 条）。
- 交付坐标：本修复随 `dev2` 由 C51 的合并提交交付，PR 为 `dev2 → beta`，见 `docs/C51-beta-merge-review-dev2-20261009.md`。
