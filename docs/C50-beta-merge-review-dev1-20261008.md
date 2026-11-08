# C50：dev1 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入 `dev1`（**远端已移除/回退的提交不得顺带带回**）；复评 dev1 全部已修改代码（含已提交未推送的 `a7154d35e`、`60e657c07`）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev1`、创建 `dev1 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并提交第二父为 `origin/beta` tip `765dce2e2a`；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev1 既有改动**零丢失**；④ 双 flavor Java 编译通过；⑤ 双 flavor AndroidTest Java 编译通过；⑥ 双 flavor 全量 JVM 套件零失败；⑦ UI token 门禁相对基线零新增违规；⑧ 净差异只含本分支自身改动；⑨ 复评发现的问题已修复或已按 AGENTS.md 明确记录处置；⑩ 提交 + recovery tag、`dev1` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并完成（0 冲突）；3 轮评审完成；复评发现 1 处**文档不实描述**（已修正）与 1 处**低影响契约分歧**（按 AGENTS.md §2 只记录不修，见「发现与处置」）；双 flavor 编译、双 flavor 全量单测、UI token 门禁全部通过；设备像素复测复现原修复数据；已提交、已推送，PR [#424](https://github.com/Silent1566/webhtv/pull/424) 已创建且**未合并**。
- **交付坐标**：见文末「交付坐标」。
- **下一动作**：无（任务已收口；PR #424 由用户决定是否合并）。

## 时间与设备

- 任务开始时本地时间：2026-10-08 23:32（Asia/Shanghai），开始时工作区**干净**（`git status --porcelain` 空），`dev1` 领先 `origin/dev1` 2 个提交（**已提交未推送**）。
- 设备：dev1 机位 `192.168.50.3:5555`（LIO-AN00 / Android 9，1920x1080，本机分配规则内）。设备端仅用于复评设备像素与功能流；未申请额外机位。
- 设备副作用与恢复：临时 `cmd uimode night yes` → 复测后 `cmd uimode night no` 已还原；未安装/卸载任何包（沿用 2026-10-08 23:21 已装的 mobile arm64 debug 包）；下拉框切到「网络 URL」后点「取消」未保存，`LabConfig` 未被改写；结束时前台回到 `HomeActivityCurrent`。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1` |
| 任务开始时 HEAD | `60e657c074842555b1e70c46d3b0442c813a3914`（`fix(lab): 配置源下拉列表改用弹窗主题上下文与语义 item 布局，修复白字浅底`，领先 `origin/dev1` 2 个提交，**已提交未推送**） |
| `origin/beta` tip | `765dce2e2a4e12d66c30bf07eaaa0d3a92a5f899`（Merge PR #423 from dev2） |
| 合并基点（merge-base） | `0a0bed420895b4074b4e6a9b39222487d2dd49a9`（= 合并前的 `origin/dev1`） |
| 合并方式 | `git merge --no-ff origin/beta`（真实合并提交，非快进） |
| 合并结果 | **0 冲突、0 冲突标记**，3 路径合入 |
| 合并提交 | `a8b30e09dcc788713b6b91ce170385f476728ba9`，第一父 `60e657c07`（dev1 侧）、第二父 `765dce2e2`（beta 侧） |
| 初始脏路径 | 无 |
| 回滚锚点 | `60e657c074842555b1e70c46d3b0442c813a3914` |
| 任务守卫 | `C50-beta-merge-review-dev1`（standard，scope `app/src` + `docs` + `scripts` + `.codex/scripts` + `.codex/task-state`） |

### beta 增量 ledger

`git log --format='%H %s' 0a0bed420..765dce2e2`（合并前）= 5 个提交：

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `765dce2e2a4e12d66c30bf07eaaa0d3a92a5f899` | Merge pull request #423 from Silent1566/dev2 | 纳入（合并线） |
| `a36a1e9c8f29ac37d2824933acfe5f067a81d80d` | docs(c49): 记录交付坐标（提交 a51e6f366f / tag / PR #423） | 纳入（仅 `docs/`） |
| `a51e6f366f2f50048f95d6c383f816710ebd1a62` | merge: 合并 origin/beta 最新代码（PR#422 自动下一集黑屏修复）并复评 dev2 历史集数行对比度修复 | 纳入（合并线） |
| `4174c65ea3dd30c9ee9ebb55db64839970d85a29` | Merge pull request #422 from Silent1566/dev1 | 纳入（合并线，即本分支上一轮 PR） |
| `0582c722216a39854c9d9421af25297bffc80daa` | fix(mobile): 修复历史记录集数行在壁纸上的对比度不足 | **纳入**（本轮唯一实质增量） |

`git diff --name-status 0a0bed420 765dce2e2` = **3 路径 / +164 −1**，与上表 1 个实质增量提交 + 1 个文档提交一致：

| 路径 | 类型 |
| --- | --- |
| `app/src/mobile/res/layout/adapter_vod.xml` | 修改 |
| `app/src/testMobile/java/com/fongmi/android/tv/ui/adapter/HistoryAdapterTest.java` | 修改 |
| `docs/C49-beta-merge-review-dev2-20261008.md` | 新增（dev2 侧文档） |

## 用户核心关注点：远端已移除（回退）内容零复活

采用与 C41/C46/C48 相同的三层递进证据，全部程序化，不依赖人工目测。

### 第一层：结构性证明（最强证据）

合并后 `dev1` 与 `origin/beta` 的差异路径集，与 dev1 自身未推送提交的改动路径集**完全一致**（同为 6 路径）。因此可做逐 blob 全量比对：

```text
beta tip 文件总数                                  : 4973
dev1 合并树文件总数                                : 4976
两侧共有路径数                                     : 4973
其中 blob 不同的路径数                             : 3（= dev1 自己改的 3 个已有文件）
其中 blob 相同的路径数                             : 4970
只在 beta 存在（dev1 缺失）的路径数                : 0
只在 dev1 存在的路径数                             : 3（dev1 自建的 2 个新文件 + 1 个文档）
```

**结论**：除 dev1 自己改写的 3 个路径外，其余 **4970 个路径与 beta 逐字节相同**。dev1 不可能从 beta 之外引入任何内容 → **结构性零复活**；`只在 beta 存在 = 0` → 结构性零丢失。

### 第二层：行级复活扫描

对 beta 上每个**单亲 revert/移除/删除类提交** `R` 取父 `R^`，得到 `removed(R,f) = lines(R^:f) − lines(R:f)`；复活定义为「该行出现在合并树中，且该行在 beta tip 中**也不存在**」（即确实被 beta 删掉、却被 dev1 带回）。blob 内容经 `git cat-file --batch` 批量读取，二进制安全。

```text
beta 非合并提交总数                                                              : 3315
其中主题命中 revert|remove|removal|delete|drop|回退|移除|删除|剔除|撤销 且单亲的提交 : 139
被这些提交触碰的文件版本数                                                       : 761
其中确实产生删除行的文件版本数                                                   : 658
「合并树存在且 beta tip 不存在」的 distinct 删除行数（= 复活行数）               : 0
```

**结论**：零复活。第一层已从根上排除该可能，第二层为加固。

### 第三层：净差异与关键目标提交核对

- `git diff --name-status origin/beta HEAD` = **6 路径**，全部为 dev1 自身改动：

  | 路径 | 性质 |
  | --- | --- |
  | `app/src/main/java/com/fongmi/android/tv/lab/LabActivity.java` | 修改 |
  | `app/src/main/res/values/lab_styles.xml` | 修改 |
  | `app/src/main/res/layout/dialog_lab_settings.xml` | 修改 |
  | `app/src/main/res/layout/item_lab_dropdown.xml` | 新增 |
  | `app/src/test/java/com/fongmi/android/tv/lab/LabSettingsDialogThemeTest.java` | 新增 |
  | `docs/LAB-CONFIG-DIALOG-20261008-lab-config-source-contrast.md` | 新增（本 C50 轮次内补正，见「发现与处置」） |

- **关键目标提交 `5682f2b054`「剔除 PR #353 主题系统改动」**：`git merge-base --is-ancestor 5682f2b054 origin/beta` → **否**，它从未进入 `origin/beta`。第一层证明已覆盖：该提交触碰的路径若不在 dev1 改动集内则与 beta 逐字节相同；若在改动集内，则是 dev1 自身对 `lab` 模块的改写（与主题系统零交集）。
- `be1b02e06`「revert: remove dynamic theme color system」是 beta 祖先，其删除的文件在合并树中仍为 **absent**（抽样核验 `AdBlockPreviewActivity.java`、`AdBlockPreviewStore.java` → ABSENT）。
- beta 增量零丢失（逐 blob）：上表 3 个 beta 增量路径在合并树中的 blob 与 `origin/beta` **逐一相同（差异 0）**。

**结论：零复活 + 零丢失。**

## 复评记录（3 轮）

复评对象 = dev1 全部已修改代码（含已提交未推送的 `a7154d35e`、`60e657c07` 共 6 路径）+ 本次合并带入的 beta 增量（1 个实质提交）。

### 第 1 轮：未推送的实验室配置源弹窗修复逐点复核

| 复核项 | 证据 | 判定 |
| --- | --- | --- |
| 根因是否成立 | `Theme.App.Lab.DayNight.Dialog` 扩展的是 `Theme.Material3.DayNight.Dialog.Alert`，**不含** `Theme.WebHTV` 的 token 映射；`dialog_lab_settings.xml` 的 `?attr/colorOnSurface` 用 Activity 主题（`Theme.App.Lab`，固定 `colorOnSurface=#FFFFFFFF`）解析，面板却按日/夜双表取容器色 → 浅色系统下白字浅底 | 成立 |
| 修复方向是否最小 | 仅 3 处生产改动（`lab_styles.xml` 角色映射、`showSettings()` 的 inflate/适配器上下文、下拉控件类型），未改任何业务逻辑与数据流 | 通过 |
| 内容与面板是否真的同源 | 弹窗由 `WebHtvAlertDialogBuilder(this, R.style.Theme_App_Lab_DayNight_Dialog)` 创建，inflate 上下文同为 `Theme_App_Lab_DayNight_Dialog` → 面板（`colorSurfaceContainerHigh`）与内容（`colorOnSurface`/`colorOnSurfaceVariant`/`colorPrimary`）来自同一张表 | 通过 |
| 主题色是否真的生效 | `ThemeBinder` 仅改写「颜色精确等于冻结基线角色值且非状态型」的 view；`lab_surface`/`lab_text_primary`/`lab_text_secondary` 分别别名 `webhtv_color_surface_container_high`/`webhtv_color_on_surface`/`webhtv_color_on_surface_variant`，均为基线角色值 → `ThemeController.bindDialog()` 可命中 | 通过 |
| 布局属性是否都能在弹窗主题下解析 | 两个布局用到的 `?attr/` 只有 `colorPrimary`(11)、`colorOnSurface`(9)、`colorOnSurfaceVariant`(7)、`colorOnSurface`(2，item 布局) 与框架 `?android:attr/selectableItemBackgroundBorderless`；**无任何应用自定义 attr**（如 `?attr/webhtvColorOnWallpaper`）→ 换成弹窗主题上下文不会出现「属性在主题中不存在」的静默失效 | 通过 |
| 适配器契约是否成立 | `ArrayAdapter(context, resource, objects)` 的 `mFieldId = 0`，`createViewFromResource()` 直接把 inflate 结果强转 `TextView`；`item_lab_dropdown.xml` 根节点即 `MaterialTextView` → 强转成立；且适配器 `mInflater = LayoutInflater.from(dialogContext)`，item 前景 `?attr/colorOnSurface` 由弹窗主题解析 | 通过（设备复测见 4.4） |
| 下拉控件类型 | `MaterialAutoCompleteTextView` 的默认样式链经 `Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu` 的 `materialThemeOverlay` → `ThemeOverlay.Material3.AutoCompleteTextView.OutlinedBox` 注入；Material 1.14.0 AAR 实测：该控件读 `dropDownBackgroundTint`，`OutlinedBox` 样式把它设为 `@macro/m3_comp_outlined_autocomplete_menu_container_color` = `?attr/colorSurfaceContainer` | 通过（但暴露 1 处不实描述，见 F1） |
| 主题改动影响面 | `rg 'Theme_App_Lab_DayNight_Dialog\|Theme.App.Lab.DayNight.Dialog'` → 仅 `LabActivity`（关于/导入/配置源 3 个弹窗）与测试引用，无其它界面复用 | 通过，无旁路回归 |
| 是否存在功能回退 | 弹窗高度/内边距/控件可见性逻辑未改；设备复测「配置源」下拉切到「网络 URL」后，URL 输入框出现、文件夹图标隐藏、`确定/取消` 正常 | 通过 |
| 单测是否可证伪 | `LabSettingsDialogThemeTest` 8 条为源码形态 + token 表对比度断言，回退生产改动即转红（上一轮已做变异检验） | 有效 |

**第 1 轮结论**：生产代码**未发现必修缺陷**；发现 1 处文档不实描述（F1）与 1 处低影响契约分歧（F2），处置见下。

### 第 2 轮：合并带入的 beta 增量复核

beta 增量 = `0582c7222`「fix(mobile): 修复历史记录集数行在壁纸上的对比度不足」，另加 dev2 侧文档 `a36a1e9c8`。

| 复核项 | 证据 | 判定 |
| --- | --- | --- |
| 修改点是否正确 | `adapter_vod.xml` 历史卡片信息区集数行 `remark` 由 `?attr/colorOnSurfaceVariant` 改为 `?attr/webhtvColorOnWallpaper`，与同区标题行 `name` 一致 | 通过 |
| 「信息区底与调色板无关」是否成立 | 信息区背景为 `shape_vod_name`（`solid @color/black_20` = 半透明黑，压壁纸），与日/夜表无关 → 确实需要恒定亮色前景 | 通过 |
| 该角色是否恒定亮色 | `webhtvColorOnWallpaper` → `@color/webhtv_on_wallpaper`，日表与夜表**均为 `#FFFFFF`**（`webhtv_styles.xml` 的 `Theme.WebHTV` 中接线） | 通过 |
| 是否有同形参照 | 兄弟列表行 `app/src/mobile/res/layout/adapter_vod_list.xml` 两行文字早已使用同一角色（第 35、48 行） | 通过 |
| 影响面是否受控 | 改动在 `app/src/mobile/` 下，leanback 历史布局未触碰；测试位于 `testMobile` 源集 | 通过，TV 侧零影响 |
| 回归是否被锁住 | `HistoryAdapterTest` 新增 `assertMobileEpisodeLineUsesTheWallpaperForegroundRole`，同时断言 `remark` 与 `name` 两个 id 的角色，并断言不再落回 `?attr/colorOnSurfaceVariant` | 有效 |

**第 2 轮结论**：beta 增量逐点复核通过，**无需修改**。

### 第 3 轮：收口复核

- 净差异只含 dev1 自身 6 路径（其中 3 个已有文件是 `lab` 模块改动，2 个新文件，1 个文档）；
- 零复活三层证据重算通过（复活行数 0）；
- `git diff --check`（含 `--cached`）退出码 0，无空白错误；
- `git grep -E '^(<<<<<<<|>>>>>>>)'` 在 `app/**` 与 `docs/**` 下命中 0，无冲突标记；
- `app/src/*/res/values*/strings.xml` 共 9 个文件键名无重复（本次合并未触碰字符串资源，无合并重复）；
- 双 flavor Java 编译、双 flavor AndroidTest 编译、双 flavor 全量单测、UI token 门禁全部通过（见「验证」）；
- 设备端复测复现原修复数据，并补齐「下拉弹出列表 + 功能流」的实测证据（见 4.4）。

**第 3 轮结论**：**全部通过，无剩余阻塞项。**

## 发现与处置

### F1（已修复）：`docs/LAB-CONFIG-DIALOG-…md` 关于下拉列表的两处增量断言不实

原文（§4.1.1 结尾）称下拉项「适配器改用弹窗主题上下文，因此**同时跟随深浅色与主题色**」，并称下拉控件「只有它应用 `dropDownBackgroundTint` 与**主题化 item 布局**」。核对结果：

1. **「跟随主题色」不成立**：`ThemeController`/`ThemeBinder` 中**没有任何 PopupWindow/ListPopupWindow 处理**（`rg 'PopupWindow\|ListPopupWindow\|Popup' app/src/main/java/com/fongmi/android/tv/theme/*.java` 命中 0），`bindDialog()` 只绑定 `dialog.getWindow().getDecorView()`；弹出列表是**独立窗口**，其面板与 item 视图都不在绑定树内。设备实测：弹出面板在两套模式下分别恰为 Material 3 编译基线的 `?attr/colorSurfaceContainer`（浅色 `#F3EDF7`、深色 `#211F26`，像素完全一致，非合成值），即**编译期取值**，运行时主题色不会改写它。
2. **`simpleItemLayout` 描述不准确**：`simpleItemLayout`/`simpleItems` 只在 `setSimpleItems()` 路径生效；本实现走的是自定义 `ArrayAdapter` + `item_lab_dropdown.xml`，与该属性无关。

处置：**修正文档**，改为按实测口径描述（见 `docs/LAB-CONFIG-DIALOG-…md` §4.1.1 与新增 §4.4），不再声称下拉列表跟随用户主题色。

### F2（只记录不修，按 AGENTS.md §2）：`Theme.App.Lab.DayNight.Dialog` 未映射 `colorSurfaceContainer`

`Theme.WebHTV` 顶部契约写明「Every Material color role that layouts resolve through ?attr/color* must be mapped onto a webhtv token here, otherwise the screen silently falls back to the Material baseline palette」，并映射了 `colorSurfaceContainer` → `@color/webhtv_color_surface_container`。但实验室弹窗主题继承的是 `Theme.Material3.DayNight.Dialog.Alert`，**未映射该角色**，于是下拉弹出面板落到 Material 基线：

| 模式 | 弹出面板实测 | 应用 token `webhtv_color_surface_container` | 两者差异 |
| --- | --- | --- | --- |
| 浅色 | `#F3EDF7`（M3 基线 `neutral94`） | `#ECEEF4` | **1.01:1** |
| 深色 | `#211F26`（M3 基线 `neutral12`） | `#1F2428` | **1.04:1** |

**不修的理由（可复核）**：① 两个候选色本身几乎不可区分（1.01:1 / 1.04:1），当前弹出面板在两种模式下对比度均 ≥ 12.6:1，**无用户可见缺陷**；② 补齐映射**并不能**让弹出列表跟随用户主题色——弹出面板在 show 时从主题解析编译值，从不参与运行时改写，因此 F1 描述的边界依然存在，映射只改变一个几乎等色的取值；③ 修改会波及 `LabActivity` 的 3 个弹窗（同一主题），且需要重新构建与设备复测，收益为零；④ 按 AGENTS.md §2「nearby defect 只报告不修」。若将来 `webhtv_color_*` 的容器角色与 M3 基线出现可见差异，最小修复是在该主题补 `<item name="colorSurfaceContainer">@color/webhtv_color_surface_container</item>`。

## 验证

日志目录：`build/c50-verify/`（`verify.log`，构建产物，不入库）。

### 4.1 双 flavor Java / AndroidTest 编译 + 双 flavor 全量 JVM 套件

单次 Gradle 调用（避免重复检查）：

```bash
bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac \
  :app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac :app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac \
  :app:testLeanbackArm64_v8aDebugUnitTest :app:testMobileArm64_v8aDebugUnitTest
```

6 个目标任务**全部实际执行**（无 UP-TO-DATE）：`BUILD SUCCESSFUL in 1m 53s`、`EXIT=0`。解析 JUnit XML：

| flavor | suites | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- | --- |
| leanback | 644 | **4087** | **0** | **0** | 2 |
| mobile | 721 | **4928** | **0** | **0** | 2 |

（相对 C48 基线 leanback 4079 / mobile 4920 各 +8 = 本任务未推送提交新增的 `LabSettingsDialogThemeTest` 8 条。）

定向核对：`LabSettingsDialogThemeTest` leanback 8/8、mobile 8/8；`PlaybackOwnershipSourceTest` leanback 16/16、mobile 16/16；`HistoryAdapterTest` mobile 3/3（含合并带入的新断言 `assertMobileEpisodeLineUsesTheWallpaperForegroundRole`）。

### 4.2 UI token 门禁

`bash scripts/check_ui_tokens.sh` → `UI_TOKEN_STATUS PASS`（EXIT=0）；`UI_TOKEN_CONTRAST pairs=38 failures=0`。
`--strict` 下 `violations=1`，唯一命中 `app/src/mobile/res/layout/item_following.xml`——该文件**不在本次改动集内**，与 C48 基线一致 → 相对基线**零新增违规**。

### 4.3 静态与结构校验

- `git diff --check`（含 `--cached`）EXIT=0；冲突标记命中 0。
- 零复活/零丢失：见上文第一/二/三层证据。
- XML 良构：`dialog_lab_settings.xml`、`item_lab_dropdown.xml`、`lab_styles.xml`、三套 `strings.xml` 解析通过。

### 4.4 设备像素复测（dev1 机位 `192.168.50.3:5555`，mobile arm64 debug，1920x1080）

沿用设备上 2026-10-08 23:21 安装的 debug 包（`lastUpdateTime=2026-10-08 23:21:07`，`versionName=5.6.0`）；该包已包含未推送的实验室修复（`lab` 相关源文件与提交逐字节一致，本次合并未触碰）。`LabActivity` 非 exported，故用 `run-as <pkg> am start --user 0` 直达该界面，再点齿轮打开「实验室配置源」。

| 场景 | 弹窗面板 | 弹窗正文/hint | 下拉弹出面板 | 下拉 item 文字 | 下拉项对比度 |
| --- | --- | --- | --- | --- | --- |
| 默认主题（浅色系统） | `#C6D2EA` | `#1A1C1E` / `#44474F` | `#F3EDF7` | `#1A1C1E` | **14.87:1** |
| `cmd uimode night yes` | `#3C4551` | `#E2E2E9` / `#C4C6D0` | `#211F26` | `#E2E2E9` | **12.64:1** |

弹窗正文/hint 对面板的对比度：浅色 11.24:1 / 6.11:1；深色 7.53:1 / 5.71:1——**均远高于 4.5:1 门槛**，且与上一轮记录的数值逐个复现（说明修复在合并后依然成立）。

功能流实测：打开配置源下拉 → 选「网络 URL」→ URL 输入框出现、文件夹图标隐藏、配置源显示「网络 URL」；随后点「取消」未写入 `LabConfig`。

边界实测（对应 F1/F2）：下拉弹出面板在两套模式下分别恰为 `#F3EDF7` 与 `#211F26`（M3 基线容器色，全区域同色不透明），而 item 文字恰为应用 token `webhtv_color_on_surface`（`#1A1C1E` / `#E2E2E9`）→ **弹出列表跟随深浅色，但不跟随用户主题色**。

设备恢复：`cmd uimode night no`（已核对 `Night mode: no`）；未卸载/重装任何包；结束前台为 `HomeActivityCurrent`。

### 4.5 回滚

`git revert` 合并提交 + 本任务文档提交即可回到 `60e657c074842555b1e70c46d3b0442c813a3914`；无数据迁移、无 native/ABI 变更、无依赖变更。

## 改动清单（本次 C50 相对 `60e657c07`）

| 路径 | 类型 | 说明 |
| --- | --- | --- |
| `docs/C50-beta-merge-review-dev1-20261008.md` | 新增 | 本任务文档 |
| `docs/LAB-CONFIG-DIALOG-20261008-lab-config-source-contrast.md` | 修改 | 修正 F1 的两处不实描述，补录 C50 独立复测证据与交付状态 |

**生产代码零改动**：`app/src` 在本次 C50 轮次内无任何新增/修改/删除；合并只带入了 beta 侧内容（3 路径，逐 blob 与 beta 一致）。合并提交 `a8b30e09d` 本身即本次的代码交付（把 beta 最新代码纳入 dev1）。

## 交付坐标

| 项 | 值 |
| --- | --- |
| 任务起始 HEAD | `60e657c074842555b1e70c46d3b0442c813a3914` |
| `origin/beta` tip | `765dce2e2a4e12d66c30bf07eaaa0d3a92a5f899` |
| 合并提交 | `a8b30e09dcc788713b6b91ce170385f476728ba9` |
| C50 改动 | 2 路径（`docs/`；`app/src` 零改动） |
| 提交 | 合并 `a8b30e09dcc788713b6b91ce170385f476728ba9`；评审文档 `1e83bdc25a6819b1767f8526dbebeda9bab11cfc`；坐标收口提交见 `git log` 最新一条 |
| recovery tag | `recovery/C50-beta-merge-review-dev1/20261008235056-1e83bdc25a68`（及坐标收口提交自己的 tag） |
| 推送 | `dev1` → `origin/dev1`（`0a0bed420..1e83bdc25`，推送后 0 ahead / 0 behind） |
| PR | [#424](https://github.com/Silent1566/webhtv/pull/424) `dev1 → beta`，**OPEN、未合并**（`mergedAt=null`、`state=OPEN`）、MERGEABLE，7 文件 +735 −6 |
| PR 文件集校验 | `gh api .../pulls/424/files` 分页合计 **7**，与 `git diff --name-only origin/beta HEAD` **逐项一致** |

## 备注

- `PlayerManager.java` 的 `com.fongmi.android.tv.player.exo.TrackUtil` 未使用 import 在 beta tip 中已存在（`git blame` 指向 `daaa0a80a`），**非本次引入**，按 AGENTS.md §2 仅记录不修改。
- `CustomCspDialog.showOtherEdit(...)` 与 `CustomCspSetting.matchesSearch(String, String...)` 在 beta tip 中即为无调用方的遗留方法，**非本次引入**，仅记录不修改。
- 合并带入的 dev2 侧文档记录的构建环境（Windows、`F:/temp/`、`G:/Git/`）为作者本机环境描述，不影响本仓库在本机的构建与验证。
- 本机分发的 `dev1` 机位（`192.168.50.3:5555`）本轮为独占使用，未申请额外机位。
