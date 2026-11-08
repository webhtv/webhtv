# TV-SITE-DIALOG-PANEL-20261014：电视版站点选择弹窗从全屏改为居中浮层面板

## Recovery anchor

- 目标：TV 版站点选择弹窗（`SiteDialog`）不再铺满整屏，改为居中浮层面板 + 电视安全边距 + 内容自适应高度；站点卡片内容左对齐、健康点紧贴站名、末行不再被裁切。
- 范围：`app/src/leanback/**` 的站点弹窗、卡片适配器与主题 helper、对应布局/样式资源、三语言字符串、定向布局契约测试、本任务文档。
- 保护面：任务 guard 启动时工作区干净（`git status` 为空），无预先存在脏路径。基线 HEAD `2ea9700e5d`，分支 `dev3`。
- 验收：① 窗口宽高不再是 `MATCH_PARENT`，改为居中 + 屏宽比例约束 + `WRAP_CONTENT`；② 面板四周留出安全边距，宽度按屏宽比例收敛（0.9）且极窄屏有下限兜底（不设 dp 上限，见 §9）；③ 网格高度收敛到整数行，末行不被裁；④ 卡片常态填充比面板高一层、内容左对齐、健康点紧贴站名；⑤ 定向测试与 leanback 编译通过。
- 回滚：撤销本任务原子提交即可恢复全屏站点弹窗行为。

## 1. 用户报告与根因

用户截图显示 TV 端打开站点选择后弹窗直接铺满全屏，观感突兀。

根因链（全部为本地代码证据）：

1. `SiteDialog.applyWindow()` 把窗口宽高硬设为 `WindowManager.LayoutParams.MATCH_PARENT`。该行为由 commit `98890d15ed`（`feat(tv): TV版站源选择弹窗改为全屏展示并聚焦当前站点`）引入，同一提交把 `dialog_site.xml` 根布局高度也改成 `match_parent`。
2. `dialog_site.xml` 根布局 `layout_height="match_parent"`，网格容器用 `layout_weight="1"` 吃掉剩余空间，因此内容被强行拉伸到全屏高度。
3. 卡片常态填充 `shape_site_item_normal.xml` 用 `?attr/colorSurfaceVariant`，而 `ThemeOverlay` 把 `colorSurfaceVariant` 映射到 `webhtv_color_surface_container`——与面板底色 `colorSurfaceContainer` 完全相同，卡片边界在面板上不可见；运行时 `SiteDialogTheme.siteItem()` 的常态分支同样用 `colorSurfaceContainer` 且描边 0。
4. `SiteAdapter` 的两个 ViewHolder 构造器用 `binding.text.setGravity(Gravity.CENTER)` 覆盖了 XML 的居中设置，导致健康点被甩到卡片左边缘、站名居中、右侧留大片空白。
5. 网格高度按 `layout_weight` 拉伸后，非整数行时最后一行被裁掉半截。

## 2. 设计研究

访问日期：2026-10-14（Asia/Shanghai）。网络检索在本会话不可用（搜索端点返回 401），因此本节依据本地既有设计契约与平台惯例，并记录未取回的证据类别。

| 来源 | 修订/地址 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| WebHTV 本地设计契约 | `docs/THEME-TV-SITE-PALETTE-20261001.md` | A（本地） | 站点弹窗两条构造路径都必须 `ThemeController.bindDialog`，卡片/文字/图标消费当前 `ThemeController.current()`；本次改动保持该不变量，只改尺寸与层次。 |
| WebHTV 焦点环契约 | `docs/TV-FOCUS-RING-20261004-unified-theme-ring.md`（由 `shape_site_item_focused/selected` 注释引用） | A（本地） | 焦点/选中环宽唯一来源 `@dimen/webhtv_focus_ring_width`；本次不改环宽，避免选中↔焦点切换尺寸跳变。 |
| 移动端同组件对照实现 | `app/src/mobile/java/.../SiteDialog.java:322-336` | A（本地） | 手机端早已是「居中 + 屏宽比例 + 宽度上限 + `WRAP_CONTENT` 高度」。TV 端本次采用同一形态，只是把比例换成安全边距 + 绝对上限，两个 flavor 的窗口策略因此重新一致。 |
| Material 3 对话框规范 | `https://m3.material.io/components/dialogs/specs`（仅取回标题，正文未取回） | C（未取回） | 未作为决策依据；仅确认「对话框是浮层而非全屏」这一平台方向与本次改动一致。 |
| Android TV 设计指南 | `https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv?hl=en`（正文未取回） | C（未取回） | 未作为决策依据；电视安全边距取值改用仓库既有惯例（见下）。 |
| 仓库既有安全边距惯例 | `SiteHealthReportDialog.onStart()` 用 `ResUtil.dp2px(24)` 作为横屏边距 | A（本地） | 第一轮曾据此引入 `PANEL_MARGIN_DP = 24`；该常量已在 §9 随「比例优先」的结论一并删除（24dp 在 1080p 上只占 2.5% 屏宽，不足以收窄面板），最终改用屏宽比例 0.9。此处保留第一轮的取舍记录。 |

未取回的类别与原因：官方规范正文（网络受限）、上游 PR/issue 讨论（本次为本地 UI 形态调整，无上游依赖变更）。这两类不改变本设计决策，因为改动不涉及播放、解码、依赖或公共 API。

## 3. 方案比较与采用

1. **不变更**：保留全屏。拒绝，用户明确要求不铺满，且全屏下卡片边界不可见、末行被裁。
2. **直接回退 `98890d15ed`**：拒绝。该提交同时带来了「打开时自动聚焦当前站点」这一有效改进，整体回退会丢掉它；且回退后 `dialog_site.xml` 的 `maxHeight=360dp` 等旧约束与后续主题改造已不匹配。
3. **居中浮层面板（采用）**：
   > 注：本节记录**第一轮**采用的取值（0.82 / 上限 1180dp / 0.74）。这些取值已在 §9 被改为
   > 0.9 / 无 dp 上限 / 0.9，原因见 §9。下文保留第一轮的原始记录以维持审计轨迹。

   - 窗口：`gravity=CENTER`、宽度 = 屏宽 × 0.82（上限 1180dp、下限 480dp）、高度 `WRAP_CONTENT`、`dimAmount=0.62` 暗化背景。
   - 尺寸用**比例**而非 dp 边距：电视常见 density 2.0，24dp 边距在 1080p 上只有 2.5% 屏宽，面板仍会占满横向——第一版实现踩过这个坑，已改为比例约束。
   - 布局：根布局高度改 `wrap_content`，网格容器去掉 `layout_weight`，新增标题行（标题 + 站点计数）。
   - 网格高度：按「整数行 × 行高 + 行间距」计算，预算 = 可用高度 × 0.74 − 面板固定内容高度；行高常量与 `adapter_site*.xml` 的 `52dp`、`SpaceItemDecoration(GRID_COUNT, 16)` 的 16 对齐。
   - 卡片：常态填充改 `colorSurfaceContainerHigh` + `colorOutlineVariant` 描边（比面板高一层），内容左对齐，健康点紧贴站名；移除 Java 里覆盖 XML 的 `setGravity(CENTER)`。
   - 面板外壳：补回 1dp `colorOutlineVariant` 轮廓（全屏时代面板边缘即屏幕边缘，0 描边看不出问题；收窄成浮层后必须可见）。

## 4. 实施步骤

1. `dialog_site.xml`：根布局改 `wrap_content`，新增 `header`/`title`/`count`，网格容器去掉权重，操作列改 `wrap_content` + `layout_gravity="top"`，间距按浮层重新排布。
2. `SiteDialog.java`：`applyWindow()` 改为居中定宽；新增 `panelWidth()`/`gridHeight()`/`gridBudgetHeight()`/`windowVisibleHeight()`/`panelChromeHeight()`/`fixedHeight()`/`updateCountText()`；`setRootWidth()`/`setRecyclerHeight()` 改为内容自适应；首帧后重算一次网格高度与计数；移除失效的 `setMaxHeight(Integer.MAX_VALUE)`。
3. `adapter_site.xml` / `adapter_site_switch.xml`：行高统一 52dp（46dp→52dp，与 `ROW_HEIGHT_DP` 对齐），文字左对齐（`gravity` 由 `center` 改 `start|center_vertical`），健康点紧贴站名（`marginEnd` 8dp→10dp），文字色改用语义 selector `@color/site_item_text`（其常态项即 `?attr/colorOnSurface`，并额外提供焦点/选中两态；`adapter_site_switch.xml` 原先的 `?attr/webhtvColorOnWallpaper` 因此被替换）。同时按收窄后的面板收紧字号与间距：站名 18sp→17sp、勾选框 `marginStart` 12dp→10dp、`groupHint` 14sp→13sp、recycler `paddingStart` 4dp→2dp 并去掉 `paddingTop/Bottom`。
4. `SiteDialogTheme.java`：面板外壳补 1dp 轮廓；卡片常态分支改 `colorSurfaceContainerHigh` + `colorOutlineVariant` + 1dp 描边。
5. `shape_site_dialog.xml` / `shape_site_item_normal.xml`：XML 回退样式与运行时样式保持一致。
6. `SiteAdapter.java`：移除两处覆盖 XML 的 `setGravity(Gravity.CENTER)` 与不再使用的 `Gravity` 导入。
7. 三语言字符串新增 `site_dialog_title` / `site_dialog_title_action` / `site_dialog_count`。
8. 更新 `SiteDialogLayoutTest`：把「必须全屏 MATCH_PARENT」的旧断言改为「必须是居中浮层且不得铺满」，并新增安全边距/宽度上限、整数行高度两条断言。
9. **实机验证中修掉一个自己引入的崩溃**：`updateCountText()` 原本用 `Fragment.getString()` 取计数文案，但 TV 版走的是裸 `Dialog`（`showDirect`），Fragment 从未 attach 到 FragmentManager，`getString()/requireContext()` 会抛 `IllegalStateException: not attached to a context`。改为 `binding.getRoot().getContext().getString(...)`。同文件其余取字符串处本来就走 `getDialogActivity()`，未受影响。

## 5. 不变量与回滚

- 打开时自动聚焦当前站点（`focusSelectedSite()`）、左右键翻组（`onItemKeyHorizontal`）、长按分组排序、搜索/切换/全选/取消等既有交互全部保留。
- `ThemeController.bindDialog` 两条构造路径绑定不变；主题 profile 切换仍即时生效。
- 焦点/选中环宽仍为唯一来源 `@dimen/webhtv_focus_ring_width`，未改动。
- `GRID_COUNT = 3` 与 `SpaceItemDecoration(GRID_COUNT, 16)` 保持不变（`HomeMenuDialogSourceTest` 断言的是 `HomeMenuDialog.java` 的同名常量；本文件的这两个值同样未改动）。
- 回滚：撤销本任务原子提交。

## 6. 验证记录

### 6.1 静态与单元测试

- 编译：`./gradlew :app:assembleLeanbackArm64_v8aDebug` 通过（仅既有 deprecation/unchecked 提示）。
- 定向 JVM 测试（leanback）：`SiteDialogActionFocusTest` 3/3、`SiteDialogThemeSourceTest` 2/2、`HomeMenuDialogSourceTest` 5/5 通过。
- 定向 JVM 测试（mobile，含本次新增/改写断言）：`SiteDialogLayoutTest` 3/3（当时该文件确有 3 个 `@Test`；第 4 个由 §8 追加，见 §8）、`SiteDialogThemeSourceTest` 5/5、`SiteDialogSelectionScrollSourceTest` 1/1 通过。
- 相邻契约测试：`DialogRoundedCornerSourceTest` 2/2、`TouchOptimizationHelperSourceTest` 11/11 通过。
- 合计 32 个测试、0 失败（逐套件读取 `build/test-results` XML 核对，非仅看 Gradle 退出码）。

### 6.2 实机验证（1080p，density 2.0）

设备：`127.0.0.1:5559`（与 `emulator-5558` 同一实例，1920x1080、`wm density 280`、应用实际按 density 2.0 渲染）。安装产物：`leanbackArm64_v8aDebug`（`adb install -r` 覆盖，未卸载、未清数据）。

用 `uiautomator dump` 读到的实测坐标（屏幕 1920x1080）。

> 下表是**第一轮**（`PANEL_WIDTH_RATIO=0.82`、`PANEL_MAX_HEIGHT_RATIO=0.74`）的实测记录，
> 故「判定」列引用的是当时的常量。改为 0.9 之后的实测见 §9。

| 项 | 实测 | 判定 |
|---|---|---|
| 面板宽度 | 1558px = 屏宽 **81%** | 命中当时的 `PANEL_WIDTH_RATIO=0.82`，不再铺满 ✓ |
| 面板高度 | 706px = 屏高 **65%** | 低于当时的 `PANEL_MAX_HEIGHT_RATIO=0.74` 上限 ✓ |
| 居中 | 左右各 181px、上下各 187px | 完全居中 ✓ |
| 网格高度 | 376px = 3×52dp + 2×16dp | 恰好整数 3 行，末行未被裁 ✓ |
| 标题/计数 | `header` 88px、`count`「共 170 个站点」 | 新增标题行生效 ✓ |

观感与交互实测：

- 面板为带圆角与 1dp 轮廓的浮层，四周可见背景，暗化遮罩生效；不再是全屏网格。
- 三列卡片边界清晰可见（常态 `colorSurfaceContainerHigh` + 描边），健康点紧贴左对齐的站名。
- 焦点态（`colorPrimary` 深蓝填充 + 白字）与选中态（`colorPrimaryContainer` 浅蓝 + 主色字）区分明确；按遥控右键焦点在网格内正确移动。
- 点分组标签「4K」后计数变为「共 1 个站点」，**面板高度自动收缩到贴合单行内容**——内容自适应行为正确。
- 全流程 `logcat` 无 `FATAL EXCEPTION`。

### 6.3 过程中的失败与结论

- `testMobile` 首次因 Gradle 构建缓存锁被另一 daemon 占用（`Timeout waiting to lock Build cache`）失败，属环境冲突；原样重试即通过。
- 首版尺寸用 dp 边距，自查发现 1080p 上面板仍占 95% 宽，改为比例约束后重跑全部测试。
- **实机首次打开弹窗即崩溃**（`Fragment ... not attached to a context`），根因是 `updateCountText()` 用了 `Fragment.getString()`；单元测试与编译都无法发现这一类「TV 版不是真正的 Fragment 宿主」的运行时约束。修复后重新构建、覆盖安装并复测通过。

未覆盖：真机电视的遥控器实体按键与过扫描裁切未验证（本会话只有平板形态模拟器）。

## 8. 后续修复：健康点压住卡片边框（2026-10-14 第二轮）

用户复核反馈：「健康检测的状态都和按钮边框重叠了，正常至少得有左间距吧？」

**根因**：`SiteDialogTheme.applySiteItem()` 会用程序化 `GradientDrawable`（`rounded()`，见 `SiteDialogTheme.java:128`）通过 `root.setBackground(...)` **整体替换**卡片背景。而 `selector_site_item` 三个 shape 里声明的 `padding`（`left/right 14dp`、`top/bottom 10dp`）是随 drawable 一起生效的——背景被换掉，这些 padding 就全部归零。于是健康点直接贴在卡片边框上。

这是上一轮改动引入的：为了消除双重缩进，当时删掉了 `adapter_site*.xml` 里 `layout_marginStart="14dp"`，理由是「shape 自带 14dp 内边距」，但没有意识到运行时背景会被替换。

**修复**：把水平内边距显式声明在卡片 View 上（`android:paddingStart/End="14dp"`），不再依赖会被替换掉的 drawable padding。垂直方向留白已由 `gravity="center_vertical"` 提供，不叠加 padding。`dialog_site.xml` 根布局本来就是这个模式（padding 写在 View 上），因此面板在实机截图里一直正常——同一个坑此前只在面板躲过。

**验证**：新增契约测试 `siteCardsDeclareHorizontalPaddingOnTheViewNotOnlyOnTheBackground`（锁住两个卡片布局必须显式声明水平 padding），`SiteDialogLayoutTest` 4/4 通过。1080p 实机 `uiautomator` 实测：健康点左边缘 `x=245`、卡片左边界 `x=213`，**间距 32px**（= 14dp padding + 1dp 描边 = 15dp，在 density 2.0 下为 30px，实测 32px 含取整与边框像素），不再压边框；`logcat` 无 `FATAL EXCEPTION`。

## 9. 后续修复：面板占比偏小 + 分组标签行被裁（2026-10-14 第三轮）

用户复核反馈：「比例不太合理吧按钮显示不全不太好看，而且在电视上占比感觉又调小，是否可以参考其他的弹出框比例比如：90%？上下左右各10%？」

**根因**（两个独立问题）：

1. **占比偏小**：`PANEL_MAX_WIDTH_DP = 1180` 这个 dp 宽度上限是元凶。真机电视的 density 并不统一——本仓库实测的 1080p 模拟器是 density 2.0（§6.2），而不少电视盒子/电视按 density 1.0 渲染；density 1.0 时 1920px 屏即 1920dp 宽，`0.82 × 1920 = 1574dp` 会被硬压到 1180dp——**只剩 61% 屏宽**，比设定的 82% 还小得多。density 越低，压缩越明显，这正是「在电视上占比又调小」的原因。
2. **标签行被裁**：面板变窄后，14 个分组标签（每个 `marginEnd 12dp` + 左右 `padding 16dp`）总宽超出内容区约 80px，末尾标签被面板右缘硬裁掉半个。

**修复**：

- 宽度与高度比例统一改为 `0.9f`，对齐仓库既有大弹窗惯例 `CacheManagementDialog.SCREEN_FRACTION = 0.9f`（该弹窗用纯比例、无 dp 上限）。**删除 `PANEL_MAX_WIDTH_DP`**，只保留 `PANEL_MIN_WIDTH_DP = 480` 作极窄屏兜底。
- 分组标签间距 `12dp → 10dp`、内边距 `16dp → 14dp`，让 15 个标签在 90% 宽度下完整放下；超出时仍由 `groupScroll` 横向滚动兜底。
- 同步修正 `dialog_site.xml` 里过时的「屏宽 86%、上限 1180dp」注释。

**验证**：`SiteDialogLayoutTest` 4/4 通过（断言更新为「必须用 0.9 比例」且「不得再有 dp 宽度上限」）。1080p 实机 `uiautomator` 实测：

- 面板窗口 `1728 × 842` = **屏宽 90% × 屏高 78%**（`dumpsys window` 的 `mFrame=[96,119][1824,961]`），左右各留 96px、上下各留 119px；面板内容区 `x=136..1784`（1648px，扣掉面板自身 20dp padding）。
- 网格高度 512px = **4 行 × 3 列 = 12 个站点**（此前 3 行 9 个）。
- 15 个分组标签宽度均匀（84–112px），末尾「APP」右缘 `x=1784` 与内容区右边界一致，**不再被裁**；修复前末尾标签只剩 36px 宽（其他约 92px）。
- `logcat` 无 `FATAL EXCEPTION`。

> 更正：本节与对应提交信息曾把「12 个站点」误写为「15 个」——15 是分组标签的数量，不是一屏站点数。实测数据以上表为准。

## 10. C69 复评轮：跨行断言恒真（2026-10-14 第四轮）

C69 任务（合并 `origin/beta` 最新代码 + 复评 dev3 已提交未推送的改动）对本任务 4 个提交做 Standards / Spec 双轴复评，发现并修正 1 项重要问题。

**问题（Standards，重要）**：`SiteDialogLayoutTest.siteGridHeightSnapsToWholeRowsSoTheLastRowIsNotClipped()` 的第三条断言

```java
assertFalse("the grid must no longer be stretched to fill the panel",
        source.contains("params.height = ViewGroup.LayoutParams.MATCH_PARENT;\n        binding.recycler.setLayoutParams(params);"));
```

是一个**恒真断言（tautology）**，永远不可能失败，因此完全锁不住它声称要锁的「网格不再被拉伸」回归。

**根因**：仓库开了 `core.autocrlf=true`，git blob 里是 LF，但 **Windows 工作区检出的是 CRLF**（实测 `SiteDialog.java` 工作区 CRLF=811、裸 LF=0；HEAD blob 则是 CRLF=0、裸 LF=811）。Java 字符串字面量里的 `\n` 是单个 LF 字符，而测试读的是工作区文件，跨行位置实际是 `\r\n`——needle 永远匹配不上。

**证据（脚本化复算，非估算）**：把 `origin/beta` 的旧版 `SiteDialog.java` 还原成工作区的 CRLF 形态后：

| 断言实现 | 旧代码（应失败） | 新代码（应通过） | 判定 |
| --- | --- | --- | --- |
| 修复前（不归一化） | 匹配 = **False** → 断言**通过** | 匹配 = False → 通过 | **恒真，无锁** ✗ |
| 修复后（归一化 LF） | 匹配 = **True** → 断言**失败** | 匹配 = False → 通过 | 真锁 ✓ |

**修复**：新增 `read(Path)` 私有助手，读取后 `.replace("\r\n", "\n")` 归一化行尾，四个测试统一走它。这与**同包**既有写法一致——`SiteDialogSelectionScrollSourceTest`、`SiteDialogThemeSourceTest` 等 **16 个**测试文件在本次修复前就已用同一套归一化（仓库既有约定，不是新造的；加本文件后共 17 个）。

**为何单测没暴露**：这类「恒真断言」在 CRLF 工作区里始终通过，只有把断言逻辑对着**旧代码**复算才会暴露；因此本次用「旧代码必须失败」这一反向判据验证修复，而不是只看测试是否变绿。

**同轮修正的文档问题**：

- `Recovery anchor` 验收项 ② 原文「宽度有上限」与 §9「删除 `PANEL_MAX_WIDTH_DP`、不设 dp 上限」自相矛盾 → 改为「宽度按屏宽比例收敛（0.9）且极窄屏有下限兜底（不设 dp 上限，见 §9）」。
- §3 方案比较里的 0.82 / 1180dp / 0.74 是第一轮取值，与 §9 的最终取值不同 → 加注说明「本节记录第一轮取值，已在 §9 改为 0.9 / 无上限 / 0.9」，保留原始记录以维持审计轨迹。
- §2 决策表的 `PANEL_MARGIN_DP = 24` 行 → 注明该常量已在 §9 随「比例优先」结论删除，保留第一轮取舍记录。
- §6.2 实测表 → 加注「本表是第一轮（0.82 / 0.74）的实测」，判定列改为「命中当时的常量」，避免与 §9 的 0.9 冲突；实测数字本身保留原貌（各轮当时的真实测量）。
- §6.1 的 `SiteDialogLayoutTest 3/3` → 注明该文件当时确为 3 个 `@Test`，第 4 个由 §8 追加（已在 git 历史中核实：`3ca1da02c3` 为 3 个，`b66a3397e2` 起为 4 个），故 §6.1 的「合计 32 个测试」是当时口径，无需改动。
- §8 的「32px ≈ 16dp（14dp padding + 1dp 描边）」→ 算术更正：14dp + 1dp = 15dp，density 2.0 下为 30px；实测 32px 含取整与边框像素。
- §9 的「真机电视常见 density 1.0」与 §3/§6.2 的 density 2.0 冲突 → 改为「density 并不统一：本仓库实测的 1080p 模拟器是 density 2.0，而不少电视盒子/电视按 density 1.0 渲染」，消除自相矛盾。
- §5 把 `GRID_COUNT` 断言归给 `HomeMenuDialogSourceTest` → 更正为该测试断言的是 `HomeMenuDialog.java` 的同名常量（已核实 `HomeMenuDialogSourceTest.java:86-88` 读取的是 `HomeMenuDialog.java`）。
- §4 步骤 3 的「文字色改语义 `?attr/colorOnSurface`」→ 更正为实际实现：改用语义 selector `@color/site_item_text`（常态项即 `?attr/colorOnSurface`，另含焦点/选中两态），并补记同轮的字号与间距收紧。
- §7「待用户确认后 finish 提交」→ 更正为 4 个提交与 4 个 recovery tag 均已存在，并新增 §11 交付坐标表。

**Spec 轴经核实不成立的一条（记录取舍，不改）**：`adapter_site*.xml` 的字号（18sp→17sp）、`groupHint`（14sp→13sp）、健康点 `marginEnd`（8dp→10dp）、勾选框 `marginStart`（12dp→10dp）、recycler padding 调整被判为「范围蔓延」。**不成立**：这些不是独立的新需求，而是 §3 已批准的「卡片内容左对齐、健康点紧贴站名、按收窄后的面板重新排布间距」在实现时的必要组成——面板由全屏收窄到 90% 后，原字号与间距是为全屏宽度调的，不收紧就会出现文字过挤与标签行溢出（正是 §9 修掉的那类问题）。已在 §4 步骤 3 补记这些取值，使其成为有据可查的实现细节而非隐性改动。

**验证**：`SiteDialogLayoutTest` 4/4 通过；leanback 资源编译与 Java 编译通过。详见 `docs/C69-beta-merge-review-dev3-20261014.md` §4。

## 11. 交付坐标

| 提交 | 说明 | recovery tag |
| --- | --- | --- |
| `3ca1da02c3` | feat(tv): 站点选择弹窗改为居中浮层面板 | `recovery/tv-site-dialog-panel-20261014/20261014080002-3ca1da02c309` |
| `b66a3397e2` | fix(tv): 站点卡片内边距显式声明，健康点不再压住边框 | `recovery/tv-site-card-padding-20261014/20261014093529-b66a3397e257` |
| `7d7c17ef15` | fix(tv): 站点面板比例改为 90%，分组标签行不再被裁 | `recovery/tv-site-panel-ratio-20261014/20261014104705-7d7c17ef1500` |
| `e84eb4f3e7` | docs(tv): 更正站点面板实测数字 | `recovery/tv-site-doc-fix-20261014/20261014105043-e84eb4f3e731` |

C69 复评轮的修正提交（`aa7a0b56f1`，双父合并）、recovery tag 与 PR [#445](https://github.com/Silent1566/webhtv/pull/445) 见 `docs/C69-beta-merge-review-dev3-20261014.md` §10 交付坐标。

## 7. 当前状态与下一步

- 状态：实现、静态验证与 1080p 实机验证均已完成；实机发现的崩溃已修复并复测通过；三轮用户复核反馈均已修正并复测。
- 交付：4 个原子提交 + 4 个 recovery tag（见 §11 交付坐标）；C69 复评轮修正 1 项重要问题后并入本任务提交。
- 下一步：等用户在真实电视上确认遥控器手感与过扫描观感。
