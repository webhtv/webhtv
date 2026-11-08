# THEME-EDITOR-20261001：页面内预览与完整内置配色

## 目标与授权

用户明确要求：默认配置放在第一个；移除底部独立实时预览，让主题色彩设置页本身临时显示按钮、文字、背景等效果；美化编辑页；允许增删改内置模板，避免只换主色、其他角色难以区分。当前 Goal 授权自主实施和端到端验证本单元。

完成标准：默认首位；编辑、导入、重置、浅深预览均只修改草稿，只有保存应用写入全局；关闭/取消保持原配置；模板具有完整浅深语义配色；现有精确颜色/透明度编辑、导入、TV 遥控操作保持可用。

## 边界与基线

- lane：standard；task guard：THEME-EDITOR-20261001。
- 分支/基线：dev3 / d2853d20ad9ee7c81f6b1785fef82702c8027cfd。
- 初始工作树干净，无预存脏路径。
- 范围：两 flavor 的 ThemeDialog.java；共享 theme 包内编辑器/模板/UI 辅助；英文、简繁中文主题字符串；主题相关单测；本文档。
- 不涉及播放器、ABI/依赖升级、原生库、其他设置页、主题 schema 或全局 ThemeController/ThemeBinder 的重构。不属于上游播放器候选，因此不修改上游合并评估索引。
- 开始：2026-10-01 21:00 CST（UTC+8）；预计 50–65 分钟，22:05 前完成。依据/方案约10分钟，实现约25分钟，构建、设备与提交约20–30分钟。设备仅 192.168.50.3:5559；使用 arm64 Debug、覆盖安装，其他构建忙时等待。

## 本地事实与调用链

- mobile/leanback ThemeDialog.java 字节一致：AppearanceDialog → ThemeDialog → ThemeEditor → ThemeProfileStore.apply → RefreshEvent.theme。
- PRESET_SOURCES/PRESET_COLORS 只调用 editor.setSeed；不会填充任何显式角色。ThemeResolver.derive 已生成主色、次色容器、表面等，但 success/warning 来自冻结默认；ThemePreviewView.colorRow 把所有 null 显示成“继承默认”，掩盖自动生成的真实值。
- ThemePreviewView.createPanel 尾部额外创建预览；编辑器标签、按钮仍读取 ThemeController.current（已保存主题）。底部预览还把 wallpaperColor 固定传0，因此壁纸预览会退回默认。
- onStart 的恢复默认立即调用 editor.reset → ThemeProfileStore.reset，违背草稿预览边界；正按钮使用默认 AlertDialog 自动关闭行为，写入失败也可能关闭。
- 每次槽位/滑块变化 removeAllViews 会销毁正在交互的滑块和 TV 焦点；优化必须保留已创建控件，仅刷新颜色/文案。
- ThemeController 是全局静态快照，绝不能用于发布未保存草稿。ThemeResolver 已提供显式 lastGood=null 的纯解析入口，ThemeTokens/ThemeContrast 已有可读性约束。
- 模板显式槽位优先于 paletteStyle，因此增加完整模板同时必须处理风格切换：更新模板派生值，保留用户单独改动的槽位；不能留下看似可点但实际无效的风格按钮。
- 默认冻结色、旧 profile/v2 codec、壁纸动态种子、播放器与健康色隔离继续保留。

## 最佳实践依据（2026-10-01 实际读取）

本次只回答“怎样让既有原生编辑器局部预览真实的完整配色，同时不污染全局与丢失交互状态”。现有依赖足够，不新增库、不复制第三方主题数据。

| 证据类 | 来源/版本 | 级别与支持结论 | WebHTV 决策/限制 |
|---|---|---|---|
| 官方源码/说明 | [Material Android Color.md](https://github.com/material-components/material-components-android/blob/1.14.0/docs/theming/Color.md)，1.14.0 / 66c334b7946dabf33adfe1a2b7cad6bcaa4ea3ad | A；颜色角色区分 primary/container、surface 层次、on*；自定义色应保留 tonal contrast，支持扩展色 harmonization | 复用已安装 HCT/DynamicScheme；按钮和背景分别使用对应角色，不能全刷为主色 |
| 官方源码 | [SchemeTonalSpot.java](https://github.com/material-foundation/material-color-utilities/blob/5b3618b16fdc3825e21d5679bafd144662088ea1/java/scheme/SchemeTonalSpot.java) | A；单个 seed 生成多套 primary/secondary/tertiary/neutral/error tonal palettes | 新模板继续使用现有 Material 算法，补足有辨识度的中性色层次与语义扩展色；不升级到上游新版 API |
| 规范 | [WCAG 2.2 Contrast Minimum](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)，SC 1.4.3 | A；正文至少4.5:1、大字至少3:1 | 以4.5:1验证正文/按钮配对；焦点除颜色外增加清晰描边；自动修正后的真实值要显示给用户 |
| 上游 issue/现场报告 | [Material Android #5046](https://github.com/material-components/material-components-android/issues/5046)，2026-04-03 原文 | C；dark 模式下列表容器与背景层次可能与预期相反 | 不把“使用 Material”当作视觉验收；固定明确的 surface 层级并在设备检查。此 issue 是观察报告，不作为已确认库缺陷或移植修复依据 |
| 成熟相关项目 | [TweakCN theme-presets.ts](https://github.com/jnsahaj/tweakcn/blob/a3b47b37cba97dd637de517aab52c45ec0f83456/utils/theme-presets.ts) | B；完整 preset 分 light/dark 定义 background/foreground/card/secondary/destructive，而不是只有主色 | 借鉴完整角色与缩略配色展示；不执行 CSS、不照搬缺少 Android 对比度保障的色值 |
| 官方设计实践/技术说明 | [Material Theme Builder README](https://github.com/material-foundation/material-theme-builder/blob/main/README.md)，2026-10-01 main 页面 | A/B；扩展色可独立命名并与动态主色协调；标签和解释文字帮助理解角色 | 成功保持绿色系、警告保持琥珀系、错误保持红色系，仅小幅协调，不为辨识度破坏语义。博客旧URL返回404，改用已实际读取的官方实践说明，不以搜索摘要作证据 |
| 本地测试 | ThemeEditorContractTest、ThemePaletteStyleTest、ThemeBinderContractTest、ThemeResolver*Test（基线 HEAD） | A；draft 深拷贝、无全局写入预览、默认冻结、主色/焦点共同映射、播放器隔离 | 增补运行数据断言与两 flavor UI 契约，不削弱现有兼容测试 |

与本需求无直接相关的论文、性能 benchmark、上游 revert 不适用：没有新颜色空间/算法或依赖/渲染引擎升级。没有未取得且会改变本次决策的外部证据。README 示例代理127.0.0.1:7897未运行，当前无代理环境；以上原文均已成功直连获取（404博客未引用结论）。

## 方案比较与采用方案

1. **不变更**：风险最低，但底部重复预览、模板不完整、重置提前落盘都不能满足目标，拒绝。
2. **直接照搬上游动态主题**：只用 seed/DynamicColors 或加载 TweakCN Web/CSS。前者不能可靠刷新已创建的原生控件且缺成功/警告扩展色；后者引入运行时与权限边界，均不采用。
3. **WebHTV 窄适配（采用）**：局部不可变 ThemeTokens 驱动整个 dialog 自有视图；保留全局解析器和持久化格式。完整命名模板生成浅/深13个色槽，默认配置仍为冻结默认、壁纸仍保留动态取色语义。模板风格重生成仅替换未手改槽位。

### 页面布局

- 自有标题/简短说明 + 固定底部取消、恢复默认、保存应用。
- 首个内容分组为内置主题，第一张卡片必为默认；模板卡片展示主色、次容器、表面、成功色组合，并有选中符号/焦点描边。
- 浅/深编辑切换明确为本页预览，不修改 App 的明暗模式；保留导入。
- 风格选择与模板分层呈现；高级颜色按强调、表面、文字、状态分组，大屏双列、小屏单列。
- 每个槽位显示真实色块、解析后的 HEX、自动/显式来源；输入被安全修正时显示输入值与实际显示值。颜色选择器初始使用实际解析色，不再从黑色开始。
- 三个透明度仍保留。遮罩/对话框透明度直接作用本编辑器 window 的 dim/shell；Web-only 浮层仍明确标注作用域，不虚构原生效果。

### 关键不变量

- 编辑/导入/重置/明暗预览不写盘、不刷新宿主、不触碰 ThemeController.current。
- apply 成功才通知父设置页、关闭并触发一次 RefreshEvent.theme；失败留在当前页显示错误。
- 状态切换只 render 已有控件，滑块拖动不销毁控件，不丢 scroll/遥控焦点；旋转重建保留 draft 与明暗预览。
- 模板复制隔离；无自动覆盖旧用户 profile；只有点选模板才换整套值。
- 黑/白前景选择使用 ThemeContrast，而非简单 RGB 加权亮度。
- 仅窗口 shell 使用 dialogOpacity，正文 alpha 不降低。安全对比度修正继续由既有 resolver 执行。
- 所有新逻辑是 Java/UI，无 ABI、安全、网络、数据归属或播放器行为变化；包体仅少量代码/字符串。退出后 dialog 资源随生命周期释放。

## 验收与回滚

1. 主题定向 JVM：完整模板浅深槽位/语义色差异、对比度、不回退、默认不变、风格有效且保留手改、深拷贝、reset/replace/preview 不落盘。
2. 主题 UI 定向测试：无底部样例预览；页面使用草稿 tokens；不重复重建滑块；默认优先；保存失败不关闭；两 flavor 一致。
3. arm64 Debug 两 flavor 编译；按5559当前 flavor优先覆盖安装并实际视觉检查，另一 flavor同设备顺序验证；只用这一台模拟器。
4. 设备验收：默认与至少两种有明显差异模板，浅/深，单槽编辑与清除，连续拖动透明度，取消/返回/恢复默认不落盘，保存后重开保留，TV焦点可见。
5. 保存本次验证摘要和必要截图到任务证据目录；构建完成及时释放本次构建资源，不结束他人任务；最后 guard finish 原子提交并生成 annotated recovery tag，不推送。

回滚：撤销本单元提交即可。Profile schema=2未改变，已保存模板仍是旧解析器可读的普通 slot 值。用户可选默认后保存恢复冻结配色，壁纸和其他设置不被删除。

## 后续修正：小屏/竖屏/带系统栏模式下的主题色彩弹层显示不全（2026-10-05）

用户反馈：主题色彩编辑器的保存按钮外溢，被系统返回键/导航栏遮挡并与底部按钮行重叠。

### 复现与根因

在 192.168.50.3:5559（1920×1080 / 280dpi / API 28）上以 `wm size 1080x1920` + `wm overscan 0,0,0,90` 复现竖屏带导航栏形态，`mStable=[0,42][1080,1830]`（导航栏从 y=1830 开始）。

- 根因：`ThemeDialog.configureWindow` 用 `ResUtil.getScreenWidth/Height` 的**原始显示边界**计算窗口尺寸。该实现返回整块显示（API 30+ 取 `getCurrentWindowMetrics().getBounds()`，不含 insets），于是窗口被设为 1864px 高（`[28,42][1052,1906]` 量级），底部按钮行落到 1796–1850px，被从 1830px 开始的导航栏裁掉约 20px。
- 修复前实测（同一场景）：保存按钮蓝色像素行 `[1796,1850]`，导航栏顶端 1830 → 被遮挡 20px。

### 采用方案：自适应弹层（不改为全屏页）

保持弹层形态，只把尺寸基准从「原始显示边界」换成「系统栏安全区」：

- `ThemeDialogLayout.Insets` 描述要避让的四个系统栏尺寸；`Insets.ofContent` 用「显示尺寸 − 宿主 content 视图尺寸」推导，作为窗口自身 insets 不可用时的后备。
- `ThemeDialogLayout.safeArea(screenW, screenH, insets)` 返回 `显示尺寸 − 各边 insets`，并按屏幕边界钳制；结果非正时回退整屏，绝不产出非正尺寸。
- `ThemeDialog.systemBarInsets(...)` 优先读取弹窗自身窗口的 `systemBars() | displayCutout()` insets（精确值）；为空或为 0 时回退到宿主 content 视图推导值。
- 不设 `params.x/y`：`Gravity.CENTER` 会把浮动窗口居中在系统栏安全区内，实测窗口落在 `[28,94][1052,1778]`，底部距导航栏 52px，无需也不应再做偏移（早期版本加过偏移，会被 WM 按显示边界重新钳制）。
- 不选全屏页的理由：编辑器已有固定底部操作行 + 权重滚动区，安全区内弹层即可完整显示；全屏页会引入新的返回键/生命周期语义并改变 mobile 与 leanback 的既有交互契约，风险大于收益。

### 验证（2026-10-05）

- 定向单测：`ThemeDialogLayoutTest` mobile 10/10、leanback 10/10 通过（新增系统栏安全区、横屏/挖孔、content 后备、退化输入用例）。
- 主题+对话框全量定向：mobile 310、leanback 191，零失败/错误/跳过。
- 设备（5559，arm64 Debug 覆盖安装，未卸载）：
  - 竖屏 1080×1920 + 底部 90px 占位：弹窗 frame `[28,94][1052,1778]`，面板 94–1777，保存按钮行 `[1668,1747]`，距导航栏 83px；取消/恢复默认/保存应用三键均可见可点。
  - 横屏/导航栏分支由 `ThemeDialogLayoutTest` 覆盖安全区计算；leanback 横屏设备实测窗口 `[48,48][1872,1032]`（1824×984），保存按钮行 `[906,997]`，距屏底 83px，与修复前基线一致，无回归。
  - `OnApplyWindowInsetsListener` 会在窗口首次布局后再次收敛尺寸，覆盖旋转、导航栏显示状态变化等时序差异。
  - 草稿边界：点「保存应用」后正常落盘并回到设置页；`theme_profile_v2_json` / `theme_profile_v2_last_good` 在保存前一致、保存后按预期更新；点「取消」后与取消前逐字节一致（sha256 前缀 `dedb21ec79b0848a`），确认不落盘。
  - 设备状态已恢复：`wm size`/`wm overscan` reset、`accelerometer_rotation=1`、`user_rotation=0`、`policy_control=null`；`shared_prefs` 13 个文件全部 SHA-256 与任务开始时一致，应用重新启动可用。

### 回滚

仅回退本单元提交即可；`ThemeDialogLayout` 的宽度/高度函数与 Material 面板背景契约未变，无 schema/数据迁移。

## Recovery anchor

- 目标/验收：主题色彩编辑器在带状态栏/导航栏、竖屏、小屏、横屏和 leanback 模式下完整显示；保存按钮不再被返回键/导航栏遮挡；保留草稿、取消、保存语义。
- 计划状态：已完成实现、测试、mobile/leanback Debug 构建与 5559 覆盖安装验收；当前仅剩最终差异校验、清理并提交/tag。
- 范围/基线/保护：任务 `THEME-EDITOR-INSET-20261005`；基线 `79244f274d15ae1b624bacc6a9f68bae8eafc3e3`；受保护初始脏路径为空；仅改动本文件及本任务 4 个代码/测试文件。
- 实现：`ThemeDialogLayout.Insets/Area` 计算系统栏安全区；ThemeDialog 优先读取 `WindowInsetsCompat` 的 systemBars/displayCutout，未就绪时回退宿主 content 尺寸；`OnApplyWindowInsetsListener` 在首帧后重新收敛窗口尺寸；mobile/leanback ThemeDialog 字节一致；不设 `params.x/y`，避免 WM 重新钳制导致的错位。
- 已验证：主题+对话框全量定向单测 mobile 310、leanback 191，零失败/错误/跳过；`ThemeDialogLayoutTest` mobile/leanback 各 10/10；mobile arm64 Debug 与 leanback arm64 Debug 均成功，并按规则使用 `adb install -r` 覆盖安装。
- 设备证据：5559 竖屏+导航栏模拟下最终窗口 `[28,94][1052,1778]`，保存按钮 `[1668,1747]`，导航栏顶端 1830，余量 83px；leanback 横屏窗口 `[48,48][1872,1032]`，保存按钮 `[906,997]`，无回归；取消草稿后 theme profile 两个持久化键逐字节不变；保存后正常回到设置页。
- 设备恢复：`wm size`、`wm overscan`、`wm density` reset；`accelerometer_rotation=1`、`user_rotation=0`、`policy_control=null`；原始 `shared_prefs` 已恢复并逐文件 SHA-256 一致；应用重新启动可用。
- 回滚锚点：`79244f274d15ae1b624bacc6a9f68bae8eafc3e3`；下一步唯一动作：运行 `task_guard finish`，由 guard 原子提交并创建本地 recovery tag。
