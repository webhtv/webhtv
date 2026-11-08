# THEME-TV-SITE-PALETTE-20261001：TV 站点弹窗主题与内置配色

## Recovery anchor

- 目标：修复 TV 版站点选择弹窗不消费已应用主题 profile 的问题；将固定紫色站点焦点/选中态改为当前语义主题；以 Material palette style 改善内置配色，并恢复一个与当前 v2 profile 兼容、只允许安全颜色 token 的 TweakCN 配色导入入口。
- 范围：`app/src/leanback/**` 的站点弹窗/适配器/主题编辑器、`app/src/main/java/com/fongmi/android/tv/theme/**` 的 palette/import 适配、两种语言字符串、导入弹窗资源、主题/站点定向测试，以及本任务文档。
- 保护面：任务 guard 启动时工作区干净，无预先存在脏路径。
- 验收：① 裸 `Dialog` 和标准 `DialogFragment` 站点选择路径都调用统一主题绑定；② TV 站点面板、分组按钮、站点卡片、文字、勾选框和操作图标均使用当前 `ThemeController.current()`；③ 内置 palette style 能产生不同且通过对比度约束的 light/dark `ThemeTokens`，旧 v2 profile 缺失 style 时保持 Tonal Spot 默认；④ TweakCN 导入只接受 allowlist 颜色 token、限制输入大小/HTTPS/SAF、仅替换草稿，失败/取消不写偏好；⑤ 主题/站点契约测试、leanback 编译、测试包设备覆盖安装和站点弹窗主题场景通过。
- 回滚：撤销本任务原子提交即可恢复当前站点主题与固定 palette 行为；`theme_profile_v2_json` 旧字段和 `theme_color` 镜像保持兼容。

## 1. 当前根因与证据

- 当前 `app/src/leanback/java/com/fongmi/android/tv/ui/dialog/SiteDialog.java` 的 `show(FragmentActivity)` 走 `showDirect()`，使用 `new Dialog(activity)`，没有进入 `BaseAlertDialog.onCreateDialog()` 的 `ThemeController.bindDialog(dialog)` 通道；同文件的 `onCreateDialog()` 也自行构造裸 `Dialog`，因此两条路径都绕过绑定。
- `ThemeController.bindTheme()` 只绑定 Activity 内容树；站点弹窗的根布局、动态分组按钮和 RecyclerView 子项在独立窗口中，不会自动被 Activity binder 覆盖。
- TV `shape_site_item_focused.xml` / `shape_site_item_selected.xml` 曾被 `e65243842bc5753703a850ec401aa143665374ce` 固定为 `#381E72` / `#6750A4`，这解决了白字压底的可读性，却也让站点焦点/选中态永远不随 profile primary/focus 变化。
- 当前 `ThemeController`/`ThemeBinder` 已有可复用的窗口和视图绑定能力；不能把 TV 弹窗改回旧静态主题。

## 2. 设计研究

访问日期：2026-10-01（Asia/Shanghai）。

| 来源 | 修订/地址 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| aShellYou | `https://github.com/DP-Hridayan/aShellYou`, HEAD `1b0a6fc73226f7e6b11987988d793522ba24be20` | A | `PaletteStyle` 提供 `TONAL_SPOT`、`VIBRANT`、`EXPRESSIVE`、`RAINBOW`、`FRUIT_SALAD`、`FIDELITY`、`CONTENT`、`NEUTRAL`、`MONOCHROME`；其 `TonalSchemeFactory` 用 Material `Scheme*` 从 seed 生成完整语义配色。WebHTV 复用同一 Material 1.14 Java `Scheme*`，只保存 style 名称，不引入 Compose/数据库。 |
| Material Components Android 1.14.0 | 本地 artifact `material-1.14.0.aar`；`SchemeTonalSpot`/`SchemeVibrant`/`SchemeExpressive`/`SchemeRainbow`/`SchemeFruitSalad`/`SchemeFidelity`/`SchemeContent`/`SchemeNeutral`/`SchemeMonochrome` 构造签名均为 `(Hct, boolean, double)` | A | 当前项目已有依赖和 `ThemeResolver`，可低风险扩展 resolver；保持默认 Tonal Spot，profile 缺省字段可读。 |
| Android Developers Material Design for Android Views | `https://developer.android.com/develop/ui/views/theming/look-and-feel` | A | Material 主题/颜色角色应通过主题属性和语义控件消费；TV 站点卡片不能只换 seed，必须让 surface、onSurface、primary、onPrimary 形成可读配对。 |
| 历史 WebHTV beta 主题导入 | commit `cb7229964f77cd43c68157111dae639d3de88531`（D 导入导出）与 `32ace88636f90f507288df859c325afd493b3e89`（TV catalog），均已在 dev3 合并时剥离 | A（本地历史） | 旧实现支持 TweakCN `cssVars.light/dark`、Oklch、HTTPS/SAF，但数据模型已被当前 v2 16 槽 profile 取代，且旧 catalog 只有 Default。只迁移 allowlist 适配思路和有界输入边界，不恢复旧 catalog/store 全套。 |
| 当前 WebHTV v2 主题 | `ThemeProfile`/`ThemeProfileValidator`/`ThemeResolver`/`ThemeBinder` | A（本地） | profile 以 13 色槽+3 opacity 为稳定契约；新 `paletteStyle` 作为兼容字段，不能改变播放器/健康/品牌色和已有 `theme_color` 镜像。 |

## 3. 方案比较与采用

1. **不变更**：保留裸 `Dialog` 和固定紫色。拒绝，无法满足主题弹窗不生效和内置配色改善。
2. **直接恢复 beta 旧 ThemeCatalog/ThemeTweakCnAdapter**：拒绝。旧 v1 profile 与 dev3 v2 profile、旧 asset catalog 与当前设计冲突，范围大且 catalog 只有 Default。
3. **窄化适配（采用）**：
   - SiteDialog 两条构造路径调用 `ThemeController.bindDialog`，并在窗口显示后再次绑定；`ThemeBinder` 的 RecyclerView attach watcher 负责异步子项。
   - 站点布局资源保持语义属性；TV 固定紫色 drawable 改为语义 primary/primaryContainer，但由于主题 resolver 可能由运行时 binder 改色，继续保留固定布局回退。
   - profile 新增 `paletteStyle`，默认 `tonalSpot`；resolver 用已有 Material 1.14 `Scheme*` 生成完整 `ThemeTokens`，仍经过现有 `ThemeContrast`。
   - 导入入口只接受本地 JSON/HTTPS JSON/TweakCN `cssVars.light/dark` 的颜色 allowlist；不执行 CSS/脚本，不写 URL/资源路径；导入只替换 `ThemeEditor` 草稿。

## 4. 实施步骤

1. 新增 palette style 枚举、profile 字段/校验/codec 默认/复制、resolver scheme factory、editor setter 和主题编辑器样式选择行。
2. 新增安全 TweakCN color utility/adapter/transfer 和导入 Dialog；接入主题编辑器导入按钮与草稿回调。
3. 修复 TV SiteDialog 两条裸窗口路径的 binding；补充 SiteDialogTheme helper/adapter programmatic coloring，动态子项和状态色统一当前 profile。
4. 更新 TV site drawable fallback 和定向 source-contract/JVM 测试。
5. 执行一次定向测试/leanback Java 编译与资源处理；如通过再使用分配的 `192.168.50.3:5559` 以 debug 测试包覆盖安装，验证主题编辑器 → 应用 → 站点弹窗。

## 5. 不变量与回滚

- `ThemeProfile.SCHEMA_VERSION=2` 不变；缺失 `paletteStyle` 的旧 JSON 正规化为 `tonalSpot`。
- profile 导入只写内存 draft；只有现有“应用”按钮调用 `ThemeProfileStore.apply`。
- 无法解析的颜色、低对比度 resolver 结果、超限/非 HTTPS/重定向/私有地址输入均拒绝且不改变当前 profile。
- TV 站点 health 状态点、播放器/视频/海报/品牌色仍由现有豁免路径管理。
- 不卸载设备现有包，所有设备验证使用 debug 覆盖安装；构建完成后清理可回收的中间/临时资源，不删除共享 Gradle 缓存。

## 6. 验证记录

- 代码静态检查：对 `ThemeTransfer.java`、`ThemeTweakCnAdapter.java`、`ThemeImportDialog.java`、leanback `SiteDialogTheme.java` 做 LSP 全量诊断，4 个文件共 0 条诊断。
- 定向 JVM 测试：`ThemePaletteStyleTest`、`ThemeTweakCnAdapterTest`、`ThemeProfileCodecTest`、`ThemeResolverTest`、`ThemeResolverOverrideTest`、`SiteAdapterSelectionTest`、`SiteDialogThemeSourceTest` 通过；测试任务显式排除了 AGP 9.5.1 在本仓库既有 assets/unit-test packaging 输出目录上触发的 `FileNotFoundException: Invalid file path` snapshot 阻断，未将该环境问题伪装为测试通过。
- 编译：leanback/mobile arm64 Debug Java 与资源处理通过；保留仓库已有 Android resource namespace/substitution 警告，无新增编译错误。
- 测试包：`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5559` 成功，`assembleLeanbackArm64_v8aDebug` 成功生成约 192M APK，并使用 `adb install -r` 覆盖安装；未卸载设备现有包。构建期间遵守 Gradle 空闲检查，未与其他打包任务并行。
- 设备冒烟：应用启动后进程存活，进入“设置 → 外观与语言 → 主题色彩”可见 9 种 palette style；应用“表现力”后通过首页菜单打开站点弹窗。实测站点面板使用当前主题的深色 surface、浅色 on-surface 文本、橙色焦点/选中环和动态操作图标/状态点；未观察到崩溃或 ANR。
- 构建完成后删除本任务生成的 `app/build` 与根 `build` 中间产物，不删除共享 Gradle 缓存。
- 结论：本任务验收项已完成，下一步为执行 task guard 最终安全检查、原子提交并创建本地恢复标签。
