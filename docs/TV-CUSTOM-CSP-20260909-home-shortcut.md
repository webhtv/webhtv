# TV-CUSTOM-CSP-20260909：站点注入界面前置到首页

## Recovery anchor

- 目标：让 TV 用户从首页通过一个可聚焦的快捷按钮直接打开“增强功能 → 站点注入”管理界面，避免进入设置页多层操作。
- 验收：TV 首页出现可配置的“站点注入”按钮；点击后打开现有 `CustomCspDialog`，用户可在界面内启用/禁用全局开关并编辑条目；保存后由现有对话框重新加载当前 VOD/直播配置并刷新首页按钮状态；手机端行为不变。
- 车道/范围：`standard`；仅撤销前一版误加的 TV 去广告按钮，增加站点注入首页按钮、站点注入开关复用、TV 文案、针对性测试和任务记录。
- 当前分支/基线：`dev2` / `6eb76c5bca0b00e69520256fffee900bc9ed8dac`。
- 初始工作区：干净，无需保护的 pre-existing dirty path。
- 已完成：用户目标纠正、站点注入开关与配置重载路径定位、方案决策、task guard 启动、首页入口实现、点击行为修复和定向验证。
- 当前待办：原子提交并创建恢复 tag。
- 未验证风险：当前无 Android TV 设备；真实遥控器焦点、文件权限弹窗和首页按钮在不同屏幕宽度下的视觉效果需设备验证。
- 回滚锚点：本任务 guard 基线 HEAD；回滚仅还原 TV 首页点击分发、移除上一版未使用的保存辅助方法，无数据迁移、依赖或 native 变更。
- 下一步：执行 `bash .codex/scripts/task_guard.sh finish` 完成本次点击行为修复的提交和恢复 tag。

## 需求与现状证据

用户澄清的目标是“增强功能 → 站点注入”的启用/禁用，而不是广告功能。当前站点注入由 `CustomCspSetting.Registry.enabled` 控制：

- `CustomCspSetting.load()` 从 `TV/CustomCsp/registry.json` 读取注册表；缺少字段时 `Registry.isEnabled()` 默认返回 `true`。
- `CustomCspSetting.save(Registry)` 会保留全部站点/直播/其它注入条目并写回同一注册表，但要求文件访问权限。
- VOD、直播和根配置注入均在配置重新加载时读取这个全局开关；单纯写文件而不重载，当前页面可能继续使用旧的注入结果。
- TV `SettingEnhanceActivity` 的“站点注入”行当前进入完整 `CustomCspDialog`，不是单独切换；手机 `SettingEnhanceFragment` 保持原有完整编辑入口。
- TV 首页已有可排序、可隐藏的 `HomeButton`/`Func` 按钮行，适合承载状态型快捷开关；首页菜单键是一次性导航动作弹窗。

因此首页快捷入口应打开既有 `CustomCspDialog`，让用户沿用原有的全局开关、条目编辑、权限和保存流程；不能在首页另写一套隐式切换逻辑，也不能只改首页文案而不进入管理界面。

## 方案比较

### 方案 A：不变

- 优点：零代码风险。
- 缺点：TV 用户继续需要进入设置、增强功能、站点注入后才能切换，不满足用户明确的快捷操作需求。
- 结论：拒绝。

### 方案 B：加入首页菜单键

- 优点：可以复用菜单键动作分发。
- 缺点：菜单键可能已被绑定为切源、切线路、搜索、历史等动作；菜单动作没有常驻状态显示；首次使用仍需进入个性设置配置菜单键；站点注入保存还可能触发权限请求和配置重载，不适合隐藏在一次性动作列表中。
- 结论：不采用。

### 方案 C：加入首页按钮并直接打开站点注入界面（采用）

- 使用现有 `HomeButton` 配置体系新增独立稳定 id；按钮文案按注册表全局 `enabled` 状态显示“站点注入：开/关”。
- 点击时先走现有 `PermissionUtil.requestFile`；获得权限后直接调用 `CustomCspDialog.show(...)`。对话框内部负责全局开关、条目编辑、保存和 VOD/直播重载。
- 新安装或没有显式自定义首页按钮列表的用户默认可见；已有自定义列表不被强制改写，仍可在“个性设置 → 首页按钮”中手动启用、隐藏和排序。
- 无文件权限时沿用现有权限提示；对话框打开失败不修改状态；不改变手机端完整编辑流程。
- 结论：实施。

## 最佳实践与设计决定

- TV 首页按钮比菜单键更适合高频、需要显示状态的全局开关；这也延续项目已有首页按钮和 D-pad 焦点模型。
- 不把全局开关迁移到 `Setting.java`：它不是普通 preference，而是与站点条目、插入位置和本地文件同一注册表的一部分。
- 不在首页按钮点击时直接改内存对象或静默写文件：`CustomCspSetting.inject(...)` 在 VOD/直播配置加载阶段读取注册表，现有 `CustomCspDialog` 已负责保存并触发配置重载；首页只负责打开这个经过验证的管理入口。
- 不覆盖用户自定义首页按钮顺序；新按钮使用新 id `9`，避免把前一版误加的去广告 id `8` 解释成新的站点注入动作。
- 按钮状态只显示“开/关”，不在首页读取并展示条目计数，避免首页功能按钮每次刷新承担不必要的完整统计文案；详细条目数量仍由增强功能页展示。

## 实施设计

1. 删除前一版 TV 去广告按钮的 id、文案、状态读取和点击分发；删除其源测试，避免错误行为继续存在。
2. 在 `HomeButton` 中加入站点注入按钮 id `9`，更新默认/排序目录；保留旧 id `8` 不再复用，避免旧版本自定义列表发生语义转换。
3. 在 `Func` 中绑定站点注入图标和动态状态文案；内容比较包含开关状态，确保刷新后按钮视图更新。
4. 在 TV `HomeActivity` 中处理权限并调用现有 `CustomCspDialog.show(...)`；对话框保存成功后通过回调调用 `setFunc()`。
6. 手机端不增加首页按钮、不改变 `SettingEnhanceFragment` 的站点注入编辑入口。

## 接受标准

- [x] TV 首页按钮目录包含“站点注入”（id `9`），但**默认不开启**：新安装/重置默认值时首页不显示该按钮。“个性设置 → 首页按钮”中的复选框默认未勾选，用户手动启用后才加入首页。
- [x] “个性设置 → 首页按钮”可看到、启用/禁用和排序“站点注入”；旧的自定义列表不会被强制覆盖。
- [x] 点击按钮打开现有站点注入管理界面，不能静默切换或无反馈。
- [x] 在管理界面内修改全局 `enabled` 后，由现有保存流程重新加载当前 VOD/直播配置，使注入开关对当前页面生效。
- [x] 没有文件权限时沿用现有权限提示；管理界面保存失败时不刷新成错误状态。
- [x] 前一版误加的去广告首页按钮、文案和测试被移除。
- [x] 手机端源码、布局和行为未修改。
- [x] Leanback 定向源测试与 ARM64 Java 编译通过。
- [x] 无真实 Android TV 设备时，已明确记录焦点/视觉/权限弹窗仍需设备验收。

## 验证计划

- 定向源测试：`SiteInjectHomeButtonSourceTest`，覆盖新按钮 id、默认值（站点注入默认不开启）、状态文案、`CustomCspDialog` 打开入口、权限检查以及旧去广告入口移除。
- 编译：`:app:compileLeanbackArm64_v8aDebugJavaWithJavac`。
- 测试：`:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.bean.SiteInjectHomeButtonSourceTest`。
- 静态检查：`git diff --check` 和 task guard scope 检查。
- 设备场景（当前不可用）：首页聚焦“站点注入”→确认；有权限时直接打开站点注入管理界面，在界面内切换并保存后按钮状态与当前 VOD/直播注入结果刷新；无权限时出现既有授权提示；首页按钮设置中的隐藏/排序和左右焦点循环正常。

## 回滚与实施记录

- 回滚：还原本任务原子提交即可；不需要清理用户数据或重建 native assets。新 id `9` 不复用误加的 id `8`，避免历史自定义按钮出现错误语义迁移。
- 发布影响：仅 Leanback 首页快捷入口和站点注入注册表的既有保存/重载路径；不新增依赖、网络请求、公开 API 或手机行为。
- 最终验证（2026-09-09）：`bash .codex/scripts/task_guard.sh check` 通过；`:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.bean.SiteInjectHomeButtonSourceTest` 通过；`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过；`git diff --check` 通过。
- 构建过程仅出现仓库既有资源命名空间、字符串格式和 CXX 32-bit 警告，没有本任务新增错误。
- 设备审计：当前连接设备均为 Android 9 tablet，不是 Android TV；因此没有把手机/平板运行结果冒充 TV 遥控器、权限弹窗或视觉验收。
- 2026-09-09 点击修复：将首页按钮从静默调用注册表切换改为调用现有 `CustomCspDialog.show(this, this::setFunc)`；站点注入界面、全局开关和保存重载统一由既有对话框负责。
- 2026-09-09 用户反馈的根因：首页按钮原先只修改注册表并重载配置，没有打开任何可见界面，因此用户看不到反馈；修复为复用增强功能页已有的权限申请和 `CustomCspDialog` 入口。
- 点击修复最终验证（2026-09-09）：`bash .codex/scripts/task_guard.sh check` 通过；`:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.bean.SiteInjectHomeButtonSourceTest` 通过；`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过；`git diff --check` 通过。
- 状态（2026-09-09 阶段）：点击行为修复和定向验证完成并已提交。

## 2026-09-27：站点注入首页按钮改为默认不开启

- 用户需求：`电视版个性设置-首页按钮-站点注入默认不开启`。
- 根因：`HomeButton.getDefaultButtons()` 在默认按钮串末尾追加 id `9`，因此新安装和“重置”后 `站点注入` 复选框默认处于勾选状态、首页直接显示该按钮。
- 判定：需求指向“个性设置 → 首页按钮”列表内该条目的默认勾选状态，不是 `CustomCspSetting.Registry.enabled`（默认 `true` 仅决定注入是否生效，且其唯一入口在“增强功能 → 站点注入”，在无任何条目时不会产生注入）。因此只改默认勾选集合，不改注册表开关与注入语义。
- 改动：`HomeButton.getDefaultButtons()` 移除 `ids.add("9")`；`HomeButton.all()`、`ALL` 排序串和“个性设置 → 首页按钮”条目保持不变，用户仍可手动启用、排序。
- 兼容：已显式保存过首页按钮列表的用户保留原配置（含此前保存的 `9`）；未保存过列表（含新安装与点过“重置”）的用户默认不再显示站点注入按钮。
- 未改动：`HomeButton.ALL`（完整目录）、`Func` 状态文案、`HomeActivity` 点击入口、`CustomCspSetting` 及其注入路径、手机端行为。
- 验证：见下方“2026-09-27 验证结果”。

### 2026-09-27 验证结果

- 定向源测试：`SiteInjectHomeButtonSourceTest#siteInjectionIsNotEnabledByDefault`，断言 `getDefaultButtons()` 方法体内不出现 id `9`，同时断言 id `9` 仍在 `all()` 目录中（可手动启用）。
- 运行时单元测试（新增）：`app/src/testLeanback/java/com/fongmi/android/tv/ui/bean/SiteInjectHomeButtonDefaultStateTest.java`，Robolectric 真实执行 `HomeButton`：
  - `freshInstallDoesNotSelectSiteInjection`：未保存过首页按钮时 `getButtons()`/`getVisibleButtons()` 均不含站点注入，其余既有按钮保留。
  - `siteInjectionStaysSelectableInTheButtonDialog`：`sortedAll()`/`all()` 仍含站点注入，可手动启用。
  - `userCanStillEnableSiteInjectionManually`：手动勾选后 `getButtons()`/`getVisibleButtons()` 恢复含站点注入。
  - `legacySavedSelectionKeepingSiteInjectionIsPreserved`：已保存的旧列表不被强制改写。
  - `resetFallsBackToTheDefaultWithoutSiteInjection`：“重置”后回到站点注入不开启的默认值。
- 测试结果：`:app:testLeanbackArm64_v8aDebugUnitTest --tests ...SourceTest --tests ...DefaultStateTest` → `BUILD SUCCESSFUL`，`SiteInjectHomeButtonSourceTest` 4/4、`SiteInjectHomeButtonDefaultStateTest` 5/5，无 failure/error。
- 回归：`:app:testLeanbackArm64_v8aDebugUnitTest` 全量 → `BUILD SUCCESSFUL`，630 个测试类 / 4051 个测试、0 failure、0 error、2 skipped。
- 编译与打包：`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过；`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5555` 打包并 `adb install -r` 覆盖安装成功（未卸载）。

### 2026-09-27 设备实测（dev1 分配设备 192.168.50.3:5555，Android 9 / sdk 28，TV(leanback) 包 `com.silent.android.webhtv`）

- 设备原本保存过自定义首页按钮 `0,8,6,1,2,3,4,7`；先备份 `shared_prefs/com.silent.android.webhtv_preferences.xml`（md5 `374cac5b5fb8fced84f8dc1303f4eb5a`），验收后已原样恢复并校验 md5 一致。
- 首页功能按钮行（保存列表）未出现“站点注入”，与旧列表一致。
- 个性设置 → 首页按钮：点“重置”写入默认值后，界面摘要从 `已启用 8/10`（旧自定义列表）变为 **`已启用 6/10`**；把列表滚到底，**“站点注入”行复选框为未勾选（checked=false）**，且该行仍存在于列表中可手动启用。旧行为为默认 `7/10`，因此设备侧直接证明默认不开启。
- 手动启用：DPAD 确认“站点注入”后摘要变为 `已启用 7/10`；返回首页，功能按钮行出现「站点注入：开」（位于“设置”右侧），证明只改变默认值而不改变可启用能力。
- 新安装默认态直测：临时移除设备上的 `home_button`/`home_button_sorted` 两个 key（先备份）后重新启动应用，首页功能按钮行实测为 `点播、直播、搜索、收藏、追更、推送、设置` **7 项，不含“站点注入”**（改动前默认包含，共 8 项）；同时个性设置 → 首页按钮摘要为 `已启用 6/10`。随后已将 prefs 原样恢复并校验 md5 一致。
- 设备状态：验收后已 force-stop 并还原原 prefs 文件，设备侧临时 dump 文件已清理；未卸载现有包，未占用 dev2/dev3/dev4 模拟器。
