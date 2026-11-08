# SITE-INJECT-FULLPAGE-20261009：站点注入改为全屏整页，底部按钮区不再被压缩、横屏也铺满

## Recovery anchor

- 目标：手机打开「增强功能 → 站点注入」（用户现场照片 `/tmp/orca-paste-1791539810953-cd64f9a7-ed87-4eea-8220-82182fe8e580.png`，竖屏 1080×2400@440dpi ≈ 392.7×872.7dp）时，① 条目较多时最下方取消/确定不被压缩；② 按用户要求把该界面改为**全屏整页**形式（两种朝向都铺满），条目列表改为滚动视口。
- 允许路径：`app/src/main/java/com/fongmi/android/tv/ui/dialog/CustomCspDialog.java`、`app/src/testLeanback/java/com/fongmi/android/tv/ui/dialog/CustomCspDialogLayoutTest.java`、本文件（`app/src/main/res/layout/dialog_custom_csp.xml` 在首轮 scope 内但两轮都无需改动）。
- 保护面：两轮 guard 启动时工作区均干净（pre-existing dirty path 0 个）；分支 `dev1`。
- 交付状态：两轮都已提交 + 恢复 tag。
  - 第 1 轮（竖屏整页、修压缩）：`22348293b8b5aa3ca4b1c88e223eaf1b98743e78`，tag `recovery/SITE-INJECT-FULLPAGE-20261009/20261009184323-22348293b8b5`。
  - 第 2 轮（横屏也整页、修“没有全屏”）：`40ba21ed8766fd59d981b77e04c121557268fcb4`，tag `recovery/SITE-INJECT-FULLPAGE-20261009/20261009190557-40ba21ed8766`。
  - **C56 复评（2026-10-09）**：两个提交在合并远端 beta 时被逐行复评；生产代码无缺陷（本轮未改动），测试代码发现 1 个真问题（见第 7 节「C56 复评订正」）并已修复、双向负对照锁定。
- 回滚：`git revert` 对应提交即可；无数据迁移、无依赖或 native 变更。
- 下一步唯一动作：无（已交付）。

## 第 1 轮：竖屏铺满整页，修「底部按钮被压缩」

### 1. 根因（设备实测证据）

修复前 `CustomCspDialog.onStart()` 竖屏走「按屏幕比例手算高度」：

```java
params.width  = (int) (screenWidth * 0.94f);
params.height = WindowManager.LayoutParams.WRAP_CONTENT;      // 窗口按内容高度
rootParams.height = WRAP_CONTENT;                             // 根布局按内容高度
scrollParams.height = WRAP_CONTENT; scrollParams.weight = 0;   // 滚动区不参与分配
binding.contentScroll.setMaxHeight((int) (screenHeight * 0.58f));
```

窗口、根布局、滚动区三者都是 `wrap_content`，且没有 weight 让任何子视图收缩。内容高度一旦超过窗口可用高度，`LinearLayout` 无处可缩，最后两个子视图（列表 + 按钮区）被摆到窗口下沿之外裁掉。

dev1 模拟器实测（`192.168.50.3:5555`，`wm size 1080x2160` + `wm density 440` → 392.7×785.5dp 竖屏，`/sdcard/TV/CustomCsp/registry.json` 临时写 5 条注入，装修复前包）：

| 位置 | 声明高度 | 实测（uiautomator bounds） | 结论 |
|---|---:|---|---|
| 取消 | 40dp | `[87,2031][528,2127]` = 96px = **34.9dp** | 被压扁 |
| 确定 | 40dp | `[550,2031][992,2127]` = 96px = **34.9dp** | 被压扁 |
| 第 3 张卡片操作行（启用/修改/首页/删除） | 36dp | `[82,1973][998,1998]` = 25px = **9.1dp** | 被挤出可视区 |
| 弹窗面板宽度 | — | 内容区 `[87…992]`（窗口 0.94×屏宽） | 非整页 |

对照用户照片：按钮圆角上下被切、第 3 张卡片操作行只剩一条边——同一现象；用户机型屏更高（2400 vs 2160），条目更长（`api: file:///TV/CustomCsp/...` 折行），压缩更明显。

### 2. 证据来源

访问日期：2026-10-09（China Standard Time）。

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| 用户现场照片 | `/tmp/orca-paste-1791539810953-cd64f9a7-ed87-4eea-8220-82182fe8e580.png`（576×1280，即 1080×2400@440dpi） | A | 竖屏、条目多时底部按钮被压扁；用户同时提出「内容多，可改成全屏整页形式」 |
| 本仓库 `CustomCspDialog.onStart()` | HEAD `67d930ed6`（修复前） | A（本地） | 竖屏三分支全为 wrap_content + 滚动区 0.58H 上限 + 无 weight，是根因的静态证据 |
| 设备实测（修复前/后） | dev1 模拟器 `192.168.50.3:5555`，`uiautomator dump` bounds + `dumpsys window` 窗口 frame | A | 修复前 34.9dp/9.1dp → 修复后 40dp/滚动视口；见第 1、4 节 |
| 本仓库 `AboutDialog.configureFullscreenWindow()` | HEAD `67d930ed6` | A（本地） | 仓库既有的「铺满全屏 + 权重滚动区 + maxHeight 0 + 必须 ADJUST_RESIZE」范式与大量注释；本次直接沿用而不是新造模型 |
| 本仓库 `MpvConfigEditorDialog` / `TmdbSearchDialog` / `GithubProxyDialog` | HEAD `67d930ed6` | A（本地） | 全屏/全宽且带输入框的弹窗统一 `SOFT_INPUT_ADJUST_RESIZE`，确认键盘处理口径 |
| Android `LinearLayout` 测量语义（EXACTLY 下 match_parent 子视图可拿满窗口高度；wrap_content 父级会把 match_parent 退化为按内容） | 同上设备实测反证（文本模式短内容时 root 只到内容高度） | A | 决定追加 `expandToWindow()`：只把 root 改成 match_parent 仍会在短内容时不贴底 |
| Material 3 对话框结构（`setView` 的自定义视图被 AlertController 以 `MATCH_PARENT × WRAP_CONTENT` 放进 `@id/custom`） | `com.google.android.material:material:1.14.0` | A | 说明面板链默认 wrap_content，需一并撑开；也是 `expandToWindow` 只改高度不改宽度的依据 |

不适用类别记录：本改动只涉及单个弹窗的窗口尺寸与线性布局权重分配，不涉及解码/渲染/ABI/打包/依赖，无需上游播放器依赖类证据；无新增依赖与规格变更。

### 3. 方案比较与采用（第 1 轮）

1. **不变更**：拒绝。设备实测 34.9dp，用户报告仍成立。
2. **只把竖屏窗口高度改成整屏、其余不动**：拒绝。窗口给了高度但 root 仍是 `wrap_content`、滚动区无 weight，内容溢出时按钮区照样被裁。
3. **竖屏铺满整页 + 滚动区吃权重 + 面板链撑开（采用）**：窗口/root/面板链 `MATCH_PARENT`，滚动区 `height=0 + weight=1 + maxHeight=0`，按钮区固定 40dp 贴底；短内容同样铺满；键盘走 `ADJUST_RESIZE`。
4. **改成独立 Activity/整页导航**：拒绝。站点注入的保存、权限、配置重载、剪贴板浮层都挂在对话框生命周期上；换承载容器会牵动保存时序与焦点策略，收益不比方案 3 大。

### 4. 第 1 轮验证

设备实测（`bash scripts/build_arm64_debug_install.sh`，mobile/arm64-v8a Debug，覆盖安装、签名一致、未卸载）：

| 场景 | 屏幕/密度 | 修复前 | 修复后 |
|---|---|---|---|
| 用户机型近似（5 条注入） | 1080×2160 @440 竖屏 | 取消/确定 96px=34.9dp；第 3 卡操作行 25px=9.1dp | root `[0,66][1080,2160]`，取消/确定/按钮区 **110px=40dp**，列表 viewport 1238px，按钮区贴底 |
| 小屏手机 | 1080×1920 @480 竖屏 | — | 按钮区 120px=**40dp**，列表 viewport 915px |
| 短屏手机 | 1080×1800 @440 竖屏 | — | 按钮区 110px=**40dp**，列表 viewport 878px |
| 文本模式（JSON 编辑器，短内容） | 1080×2160 @440 | 页面底部留空隙 | root 撑满 2094px，按钮区 40dp 贴底 |

JVM：`CustomCspDialogLayoutTest` 4/4 通过（含变异检验：回退旧参数后 3/4 失败）。

## 第 2 轮：横屏也铺满整页，修「我在 5555 上测试没有全屏」

### 5. 根因（用户复测反馈 + 设备实测）

第 1 轮只把**竖屏**改成整页，横屏仍保留旧的 `0.76×0.98` 居中比例弹窗。用户在 dev1 模拟器（`192.168.50.3:5555`，物理 1920×1080@280dpi，横屏）复测，看到的正是这一版：

| 项 | 实测 |
|---|---|
| 窗口属性 | `mAttrs={(0,0)(1459x1058) gr=CENTER ...}` → `Requested w=1459 h=1058` |
| root 位置 | `[230,42][1689,1080]` → 左右各空出 230px、上边留 42px（状态栏） |
| 结论 | 不是整页，左右大块背景露出，与用户「没有全屏」的反馈一致 |

进一步核对：该界面内容多（标题行 + 位置/新增/识别/排序 + 搜索框 + 编辑/文本切换 + 滚动列表 + 底部按钮区），竖屏在 1080×2160/1800/1920 三种比例下都已验证必须整页；横屏的 0.76 比例在这台设备上比竖屏的整页更“窄”，视觉割裂。用户诉求是这一页统一整页。

### 6. 第 2 轮方案与实施

采用：**两种朝向统一铺满整页**，不再有横屏特例。

```java
params.width  = MATCH_PARENT;
params.height = MATCH_PARENT;
params.gravity = Gravity.CENTER;
window.setSoftInputMode(SOFT_INPUT_ADJUST_RESIZE);
window.setLayout(MATCH_PARENT, MATCH_PARENT);
root.height = MATCH_PARENT; expandToWindow(root);
scroll.height = 0; scroll.weight = 1; scroll.setMaxHeight(0);
```

- 删除 `land` / `screenWidth` / `screenHeight` 三个参数（不再有任何按屏比例的手算），`applyPageSizing(window, root, scroll)` 签名更窄。
- 页面左右正文不贴边由 XML 的 20dp padding 保证（`paddingStart/End=20dp`），未改动。
- 横屏键盘：与竖屏统一走 `ADJUST_RESIZE`（AboutDialog 同一口径）。

方案比较：

| 方案 | 结论 |
|---|---|
| 保持横屏 0.76×0.98 | 拒绝，正是用户复测反馈的「没有全屏」 |
| 横屏用 0.9×0.95 等其它比例 | 拒绝。仍是按屏比例手算，同样会在某些机型/比例下出现空隙或裁切，用户诉求是整页 |
| 两种朝向统一整页（采用） | 与 `AboutDialog`（leanback 全屏）/`GithubProxyDialog`（全宽）既有范式一致；配合 `expandToWindow` + 权重滚动区，按钮区永远 40dp 贴底 |
| 横屏整页但电视加 overscan 内缩 | 暂不采用。XML 已有 20dp padding 作为正文安全边；如需再收边，应作为独立任务用真机电视验证，不在此处猜测性加入 |

### 7. 第 2 轮测试（`CustomCspDialogLayoutTest` 重写，5/5 通过）

| 用例 | 覆盖 |
|---|---|
| `bothOrientationsFillTheWholePageAndKeepFooterAtNaturalHeight` | 竖屏 5 种比例（393×873 用户机型 / 360×640 / 412×915 / 617×1097 竖屏平板 / 393×573 键盘顶掉 300dp）+ 横屏 3 种（686×386 用户复测机型 / 960×540 TV / 915×412）；逐个断言 root 铺满高度与宽度、取消/确定/按钮区均 40dp、按钮区贴底、滚动区 viewport > 0 且不与按钮区重叠 |
| `windowIsMatchParentInsteadOfProportionalForEveryOrientation` | 两种朝向的窗口宽高必须是 `MATCH_PARENT`、root 高度 `MATCH_PARENT`、滚动区 `height=0 + weight=1`（横屏不再按 0.76 手算） |
| `shortContentStillFillsTheWholePage` | 竖屏 + 横屏的短内容（文本模式 JSON 编辑器、空搜索结果）同样铺满并贴底 |
| `footerNeverShrinksEvenWhenWindowIsShorterThanTheMinimumPage` | 诚实契约：窗口被压到比页面最小高度还短时，按钮区仍必须 40dp（不被压缩），滚动区不得为负；只是整页被裁在窗口下方 |
| `legacyParamsReproduceBothReportedDefects` | 变异检验：旧竖屏参数下按钮区被挤出页面（“被压扁”）；旧横屏宽度只有 0.76×屏宽、高度只有 0.98×屏高（“没有全屏”） |

变异检验（真实执行）：把 `applyPageSizing` 临时改回第 1 轮实现（横屏 0.76×0.98）→ **4/5 失败**（`bothOrientations…`、`windowIsMatchParent…`、`shortContent…`、`footerNeverShrinks…`），恢复后 5/5 通过。

#### C56 复评订正（2026-10-09，`docs/C56-beta-merge-review-dev1-20261009.md`）

上面这条“4/5 失败”的**口径不成立，已订正**：

- 旧横屏那三条断言是**恒真算式**（`assertEquals((int)(W*0.76f), Math.round(W*0.76f))` 比较的是同一条算式的两种取整，另两条 `W - (int)(W*0.76f) > 0` 对任意正宽高都成立），**不调用任何生产代码**，无法转红；
- 更关键：本仓 Robolectric **只装在 leanback 测试变体**（`app/build.gradle` 的 `testLeanbackImplementation`），而 Robolectric 默认显示是**竖屏**。第 1 轮“横屏按朝向挑一套 0.76×0.98 手算窗口”只有在 `ResUtil.isLand(...)` 为真时才走到，因此它在 JVM 层**永远不可达**——实测负对照 A：把 `applyPageSizing` 改回第 1 轮按朝向分支实现、用修复前的测试文件跑 → `tests=5 failures=0`（**全绿，缺陷可原样复发而不被发现**）。

订正后的实现（只改测试，5 例 → 6 例）：

1. `legacyParamsReproduceBothReportedDefects` 的横屏部分改为用同一套面板链按**旧窗口尺寸实测**（root 宽度 < 屏宽、root 高度 < 可用高度），再对**当前实现**断言窗口宽高必须是 `MATCH_PARENT`；
2. 新增 `landscapeOrientationQualifierAlsoFillsTheWholePage()`：`@Config(qualifiers = "w686dp-h386dp-land")` 让 `ResUtil.isLand` 为真（用例自带 qualifier 自检），断言真实横屏下窗口/root/滚动区参数与“铺满 + 按钮区 40dp 贴底”。

负对照 B（同一变异 + 订正后测试）→ `tests=6 failures=1`：`landscapeOrientationQualifierAlsoFillsTheWholePage` 报 `真实横屏下 root 高度必须 MATCH_PARENT expected:<-1> but was:<378>`；负对照 C（撤销变异）→ 6/6 全绿。生产文件 sha256 在变异前后字节一致（`311c8e09…1eeb`）。

### 8. 第 2 轮验证

设备实测（同一模拟器，`bash scripts/build_arm64_debug_install.sh`，覆盖安装、未卸载；`registry.json` 先备份为用户原文件后临时写入 5 条注入用于“条目多”场景）：

| 场景 | 屏幕/密度 | 第 2 轮前（第 1 轮实现） | 第 2 轮后 |
|---|---|---|---|
| 用户复测机型（横屏） | 1920×1080 @280（686×386dp） | 窗口 `1459×1058` 居中，root `[230,42][1689,1080]` | 窗口 `fill x fill`、`sim={adjust=resize}`，root `[0,42][1920,1080]` = **整页 1920×1038**；取消/确定 70px=**40dp**，按钮区 `[35,989][1885,1059]` 贴底，列表 viewport 493px 且不与按钮区重叠 |
| 用户机型近似（5 条注入，竖屏） | 1080×2160 @440（392.7×785.5dp） | 已整页（第 1 轮） | root `[0,66][1080,2160]` 仍整页；取消/确定/按钮区 110px=**40dp**，列表 viewport 1238px |

全量单测：

- `:app:testLeanbackArm64_v8aDebugUnitTest` → BUILD SUCCESSFUL，**4145 项 / 0 failure / 0 error / 2 skipped**（含新增 1 项）。
- `:app:testMobileArm64_v8aDebugUnitTest` → BUILD SUCCESSFUL，**4980 项 / 0 failure / 0 error / 2 skipped**。

C56 复评订正后（同一命令，`CustomCspDialogLayoutTest` 5 → 6 例）：leanback **4146 项 / 0 failure / 0 error / 2 skipped**（651 suites），mobile **4980 项 / 0 failure / 0 error / 2 skipped**（726 suites）。

设备状态回收：`registry.json` 还原为用户原文件（`md5 bce0777921bc78ec1991f4d89f1b3d87` 一致）、`wm size reset` / `wm density reset` / `accelerometer_rotation=1` 恢复、应用已 force-stop、`/sdcard/ui.xml` 已删除。

## 9. 复现与回滚（两轮通用）

- 复现：条目数 ≥3 的注入注册表，打开「增强功能 → 站点注入」，用 `adb shell uiautomator dump` 取 `root` / `footer` / `positive` 的 bounds 与窗口属性 `dumpsys window | grep wanim` 对比页面是否铺满与按钮高度。
- 回滚：`git revert` 对应提交；只影响该弹窗窗口尺寸与布局权重，无数据/依赖/native 变更。
