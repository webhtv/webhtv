# MOBILE-SEARCH-DROPDOWN-CONTRAST-20261009：手机版搜索界面分组下拉在深色模式下文字看不清

## Recovery anchor

- 目标：修复手机版搜索页（`SearchFragment`）与搜索结果页（`CollectFragment`）的分组/范围下拉面板在深色模式下文字不可读（用户现场照片 `F:\temp\orca-paste-1791526273044-e3e06447-e594-43e7-8ecd-831788105f90.png`、`…281422-c832986b…png`、`…287371-317adc18…png`），使面板文字对面板底色在日/夜两套 token 表下均 ≥ 4.5:1（WCAG 2.2 §1.4.3）。
- 允许路径：`app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SearchFragment.java`、`app/src/mobile/java/com/fongmi/android/tv/ui/fragment/CollectFragment.java`、`app/src/testMobile/java/com/fongmi/android/tv/ui/activity/SearchScopePopupLayoutTest.java`、本文件。
- 保护面：任务 guard 启动时工作区干净（预先存在脏路径 0 个）。
- 验收：① 面板底色不再写死，取与 item 文字同表的语义角色；② 修复前 1.29:1 → 修复后深色 12.14:1、浅色 14.73:1；③ 设备实测（`192.168.50.3:5561`，webhtv4 分配机）四个组合（搜索页/搜索结果页 × 日/夜）像素采样一致；④ 定向 JVM 测试含变异检验；⑤ 覆盖安装（`install -r`，签名一致 `4dbe7298`）后编译/测试通过。
- 回滚：撤销本任务原子提交即可恢复到「面板 = `Color.WHITE`」的旧行为。
- 下一步唯一动作：无（已交付）。

## 1. 根因（设备实测证据）

两个下拉面板的底色由代码直接构造，写死为不透明白色：

- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SearchFragment.java` → `getScopePopupBackground()`：`drawable.setColor(Color.WHITE)`。
- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/CollectFragment.java` → `getGroupPopupBackground()`：同样 `drawable.setColor(Color.WHITE)`。

而同一面板的 item 文字走主题语义角色 `ThemeController.current().colorOnSurface()`（`addScopePopupItem()` / `addGroupPopupItem()`），深色表该角色是近白色 `#E2E2E9`。
两者来自两张不同的表，于是深色模式下就是「近白字压在白底上」。

设备实测（`192.168.50.3:5561`，1080×1920，`cmd uimode night yes`，安装修复前包）：

| 位置 | 面板实测 | 文字实测 | 对比度 |
|---|---|---|---:|
| 搜索页「全部/当前站/分组…」下拉 | `#FFFFFF` | `#E2E2E9` | **1.29:1** |
| 搜索结果页「全部/官/音/盘…」下拉 | `#FFFFFF` | `#E2E2E9` | **1.29:1** |

用户照片复现：面板为纯 `#FFFFFF`（42936 px 采样），文字 `#E6E1E5` 量级 → 1.29:1；照片分辨率 1152×2560 经重采样，故文字与编译表 `#E2E2E9` 有 ±4/255 偏移，数值口径一致。

## 2. 证据来源

访问日期：2026-10-09（China Standard Time）。

| 来源 | 地址/修订 | 证据等级 | 结论与决策影响 |
|---|---|---:|---|
| 用户现场照片 ×3 | `F:\temp\orca-paste-1791526273044-…png`（深色搜索页下拉）、`…281422-…png`（外观与语言弹窗）、`…287371-…png`（主题色彩页） | A | 深色搜索页下拉面板纯白、文字近白，肉眼不可读；另两张用于确认设备处于深色模式与默认主题 |
| 本仓库 `SearchFragment.java` / `CollectFragment.java` | HEAD `29d52ab238` | A（本地） | 面板 `Color.WHITE` 与 item 文字 `colorOnSurface()` 同处一个方法族，是根因的静态证据 |
| 本仓库 `app/src/main/res/values/webhtv_tokens.xml` / `values-night/webhtv_tokens.xml` | HEAD `29d52ab238` | A（本地） | `webhtv_color_on_surface` = `#1A1C1E`（日）/ `#E2E2E9`（夜）；`webhtv_color_surface_container` = `#ECEEF4`（日）/ `#1F2428`（夜） |
| 本仓库 `ThemeResolver.java` / `ThemeContrast.java` | HEAD `29d52ab238` | A（本地） | 主题解析器对 `onSurface` 有 `enforceBase(surface, surfaceContainer, surfaceContainerHigh) ≥ 4.5:1` 的修复通道（profile 一旦改动 surface/container 即触发），因此面板与文字改挂同一张表后，自定义主题下也不会重现该缺陷 |
| 本仓库 git 历史 | `60e657c074`（2026-10-08，实验室配置源下拉）、`0582c72221`（2026-10-08，历史集数行）、`c5912111a4`（2026-10-09，TV 首页菜单弹窗） | A（本地） | 同一类缺陷（前景与背景来自不同表/同角色）在本仓库已有先例与既有约定：面板与 item 文字必须同源；本修复沿用而不是新造模型 |
| 本仓库 `scripts/check_ui_tokens.sh --strict` | HEAD `29d52ab238` | A（本地） | 修复后 `violations=1`（既存 `item_following.xml`）、`contrast failures=0`，与基线一致，无新增 |
| WCAG 2.2 §1.4.3 | `https://www.w3.org/TR/WCAG22/` | A | 正文阈值 4.5:1，作为验收数字 |
| Material Design 3 颜色角色 | `https://m3.material.io/styles/color/roles` | A | 菜单/下拉容器用 `surfaceContainer`，其上文字用 `onSurface` |

不适用类别记录：本改动只涉及静态资源角色接线与两处弹窗背景构造，不涉及解码/渲染/ABI/打包，无需上游播放器依赖类证据，也无新增依赖或规格变更。

## 3. 方案比较与采用

1. **不变更**：面板继续 `Color.WHITE`。拒绝，正是用户报告的不可读现象（深色 1.29:1）。
2. **面板改为 `colorSurface`**：深色表 `#101418` 能修好深色，但浅色表等于 `#F8FAFD`，与页面底色几乎同色，下拉面板失去层次（无色块边界），属可读性之外的新缺陷。拒绝。
3. **面板改为 `colorSurfaceContainerHighest` / `surfaceContainerHigh`**：也能满足对比度，但容器层级高于菜单语义，会与同页其他下拉（如实验室配置源用 `colorSurfaceContainer`）分层不一致。拒绝，取一致的 `surfaceContainer`。
4. **窄化适配（采用）**：面板与 item 文字改用同一张表——面板 `ThemeController.current().colorSurfaceContainer()`，item 文字保持 `colorOnSurface()`（M3 菜单容器 + 其上文字的标准配对），并保留既有圆角与 `selectableItemBackground` 水波纹。仅两行行为改动。
5. **让面板走 XML 主题化 drawable（`?attr/colorSurfaceContainer`）**：PopupWindow 的 `setBackgroundDrawable` 需要 Drawable 实例，改 XML 就要新增 drawable 且仍要在 Java 里 resolve，收益为零；而且本仓库移动端这两个面板本就以代码构造（宽度/高度按分组文本计算），改动面反而更大。不采用。

## 4. 本次实现

- `SearchFragment.getScopePopupBackground()`：`Color.WHITE` → `ThemeController.current().colorSurfaceContainer()`（含根因注释）。
- `CollectFragment.getGroupPopupBackground()`：同上。
- `SearchScopePopupLayoutTest` 增加两条守卫：
  - `mobileScopePopupsTakeTheirSurfaceFromThePalette`：源码扫描，禁止 `/drawable.setColor(Color.WHITE)`，要求面板与文字分别取 `colorSurfaceContainer()` / `colorOnSurface()`；
  - `scopePopupSurfaceAndTextClearTheBodyContrastFloorInBothPalettes`：直接解析两套 token 表并计算 WCAG 对比度，要求 ≥ 4.5:1。
- 未改动任何布局、token、Resolver、Binder、其他弹窗。

## 5. 验证

- **JVM 定向测试**：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.SearchScopePopupLayoutTest` → `BUILD SUCCESSFUL`，`tests=4 skipped=0 failures=0 errors=0`。
- **变异检验（证明测试真的钉住缺陷）**：把 `SearchFragment` 的 `colorSurfaceContainer()` 改回 `Color.WHITE` → `mobileScopePopupsTakeTheirSurfaceFromThePalette FAILED (AssertionError @ :70)`，`4 tests completed, 1 failed`；恢复后回到 4/4 通过。
- **token 门禁**：`bash scripts/check_ui_tokens.sh --strict` → `UI_TOKEN_CONTRAST pairs=38 failures=0`，`violations=1`（既存 `app/src/mobile/res/layout/item_following.xml`，与本改动无关，与历史基线一致）。
- **构建与安装**：`bash ./gradlew :app:assembleMobileArm64_v8aDebug` → `BUILD SUCCESSFUL`；`adb -s 192.168.50.3:5561 install -r`（覆盖安装，未卸载；签名 `4dbe7298` 与本地 debug key 一致）→ `Success`。
- **设备像素实测（修复后最终包，四个组合）**：

| 场景 | 模式 | 面板实测 | 文字实测 | 对比度 |
|---|---|---|---:|---|
| 搜索页范围下拉 | 深色 | `#1F2428` | `#E2E2E9` | **12.14:1** |
| 搜索结果页分组下拉 | 深色 | `#1F2428` | `#E2E2E9` | **12.14:1** |
| 搜索页范围下拉 | 浅色 | `#ECEEF4` | `#1A1C1E` | **14.73:1** |
| 搜索结果页分组下拉 | 浅色 | `#ECEEF4` | `#1A1C1E` | **14.73:1** |

- 回归面：面板圆角、水波纹点击、条目高度、宽度计算、选中回调（`onScopeSelected` / `onGroupFilterSelected`）均未改动，设备上逐个条目点击后下拉正常收起并生效；浅色模式面板由 `#FFFFFF` 变为 `#ECEEF4`（仍有明确色块边界，层次未丢）。
- 设备状态：测试用的 `cmd uimode night` 已恢复为进入时的 `no`；应用数据、主题配置、站源配置均未改动。

## 6. 遗留与边界

- 本任务只覆盖手机版搜索/搜索结果页的两个下拉面板（用户报告的范围）。其他仍在代码里写死 `Color.WHITE` 的背景（如 `CollectFragment` 内某图标 tint、`BackupProgressDialog`、`CustomCspDialog`）不属本任务范围；其中图标 tint 是对着深色图标的着色，不是面板底色，未发现同类缺陷，未纳入改动。
- `check_ui_tokens --strict` 的既存违规 `item_following.xml` 为历史基线，未在本任务修复。
