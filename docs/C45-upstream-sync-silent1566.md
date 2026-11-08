# C45：合并上游 `webhtv/webhtv` 分支 `Silent1566` 最新代码（站点注入搜索 + 手机缓存布局）

## Recovery anchor

- **目标**：把上游 `https://github.com/webhtv/webhtv` 的 `Silent1566` 分支最新代码合并进本地 `dev2`，吸收其全部功能性增量，同时保留本地既有能力与本地主题/UI 语义 token 合同；合并后完成双 flavor 编译、单测、UI token 门禁与 TV/手机实机验证，并提交、打本地恢复 tag（不推送、不打正式包）。
- **验收标准**：① 上游 tip `05469a967ad32818b6e76e080100bfae1787ab76` 成为合并提交第二父；② 上游 14 个变动路径的功能性增量全部落地（允许硬编码颜色被本地语义 token 有意替换、测试主题基座被本地适配）；③ 本地既有改动零丢失；④ 双 flavor Java 与 androidTest Java 编译通过；⑤ 双 flavor 全量 JVM 套件相对合并前基线**零新增失败**；⑥ `scripts/check_ui_tokens.sh --strict` 相对基线零新增违规；⑦ TV 实机站点注入搜索可用、手机窄屏缓存弹窗排版修复可见；⑧ 提交 + 本地 annotated recovery tag。
- **lane / scope**：`upstream`；`app/`、`docs/`。
- **任务守卫**：`C45-upstream-sync-silent1566`；任务开始 HEAD 为 `d8daedf86c57d58c18823343d8fc6510319289b9`；初始工作区干净（0 个受保护脏路径）。
- **当前状态**：合并完成（1 个 import 冲突已解决）；全部验证通过；待 `task_guard.sh finish`。
- **下一动作**：`bash .codex/scripts/task_guard.sh finish --verified <evidence> --commit-message <msg>`（原子合并提交 + recovery tag），不推送。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始时 HEAD | `d8daedf86c57d58c18823343d8fc6510319289b9`（Merge PR #418 from dev1） |
| 上游仓库 | `https://github.com/webhtv/webhtv`（默认分支 `main`） |
| 上游分支/tip | `Silent1566` / `05469a967ad32818b6e76e080100bfae1787ab76` |
| 上游本地引用 | `refs/remotes/webhtv-upstream/Silent1566` = `05469a967a…`（本次新建，`--no-tags`，不推送） |
| Merge base | `d042cd542b8768ec9dbd2582923088e54a7bfeb1`（上一轮 C37 合并的上游 tip） |
| 本地领先量 | `d042cd542b..HEAD` 大量本地提交（dev2 集成历史） |
| 上游增量 | `d042cd542b..webhtv-upstream/Silent1566` = **3 个提交 / 14 个路径** |
| 初始脏路径 | 无（`git status` 干净） |
| 合并方式 | `git merge --no-commit --no-ff webhtv-upstream/Silent1566`，由 task_guard `finish` 创建合并提交 |
| 回滚锚点 | `d8daedf86c57d58c18823343d8fc6510319289b9` |

## 上游 commit ledger（3 个提交，全部纳入）

| 完整 commit ID | 标题 | 内容 | 处置 |
| --- | --- | --- | --- |
| `58ebb653ff9386b292bd1e4957c0544e7582c035` | `fix: repair mobile cache management layout` | 手机窄屏下缓存管理模块文字被同排按钮挤成窄列、底部策略/清理按钮被裁切。新增 mobile 专用布局 `app/src/mobile/res/layout/dialog_cache_management.xml`（177 行）、`CacheManagementDialog.addRow` 增加 mobile 竖排 + 两列等宽操作按钮分支、`wireFocusOrder` 在 mobile 下跳过 TV 列式焦点链、新增 `CacheManagementDialogLayoutTest`（4 个屏幕/字体/语言用例）、设计文档追加修复记录 | 纳入 |
| `7357b2c37d6b73887e56e3375cfa6e30655196c7` | `feat: add site injection search` | 站点注入管理弹窗新增搜索：`CustomCspSetting.matchesSearch(Item/query, fields…)`、`CustomCspDialog` 搜索框接线 + `CspAdapter.visibleIndices` 过滤视图 + 搜索时禁用排序/移动 + 空状态提示；新增 `ic_search.xml`、`dialog_custom_csp.xml` 搜索布局与空状态、三语字符串、`CustomCspSettingTest`、`CustomCspDialogTest` | 纳入 |
| `05469a967ad32818b6e76e080100bfae1787ab76` | `fix: 修复站点注入搜索空状态与排序刷新回归` | 评审修复两项缺陷：① `remove()` 删除最后一个匹配项后未重算 `searchEmpty` 可见性；② `moveItemToIndex` 把 `notifyItemMoved`/`notifyItemRangeChanged` 退化为 `notifyDataSetChanged`。恢复局部通知、删除后刷新模式/空状态可见性；新增评审记录 `docs/site-injection-search-review.md` | 纳入（上游 tip） |

上游 3 个提交属于同一功能波次（站点注入搜索 + 手机缓存排版修复），与本地既有实现无重复、无被取代项，**全部纳入**。

### 上游 net 变更构成（14 个路径）

- **新增 6 个**：`app/src/androidTest/.../CacheManagementDialogLayoutTest.java`、`app/src/main/res/drawable/ic_search.xml`、`app/src/mobile/res/layout/dialog_cache_management.xml`、`app/src/test/.../CustomCspSettingTest.java`、`app/src/testLeanback/.../CustomCspDialogTest.java`、`docs/site-injection-search-review.md`。
- **修改 8 个**：`CustomCspSetting.java`、`CacheManagementDialog.java`、`CustomCspDialog.java`、`dialog_custom_csp.xml`、三套 `strings.xml`、`docs/CACHE-MGMT-01-cache-management-design.md`。
- **删除 0 个**。

## 冲突与解决决定

`git merge` 产生 **1 个冲突文件**（`CacheManagementDialog.java`），性质为"本地已主题化改造 + 上游新增 mobile 分支"的并集型冲突：

| 冲突位置 | 本地 HEAD | 上游 `Silent1566` | 解决 |
| --- | --- | --- | --- |
| `CacheManagementDialog.java` import 块 | 已有 `WebHtvAlertDialogBuilder`，无 `Util`/`MaterialAlertDialogBuilder` | 新增 `Util` + `MaterialAlertDialogBuilder` | **取上游 `Util`**（mobile 分支必需 `Util.isMobile()`），**拒绝带回 `MaterialAlertDialogBuilder`**——本地已把三处确认框统一为 `WebHtvAlertDialogBuilder`（主题绑定入口），带回裸 builder 会回退本地主题契约 |

无其它冲突、0 个未解决冲突、0 个残留冲突标记。

## 强制最佳实践评审（设计研究门）

上游自带 `docs/site-injection-search-review.md`（52 行）记录了其自身红/绿验证与评审结论。本轮合并的评审聚焦"**是否应原样采用上游实现**"：

| 备选 | 评估 | 结论 |
| --- | --- | --- |
| 不合并 | 无法满足"合并上游代码"目标；上游增量是本地完全缺失的用户可见能力（站点注入搜索、手机缓存排版修复） | 拒绝 |
| 原样采用上游 | 上游新增搜索控件在 `ic_search.xml` 用 `#5F6368`，在 `dialog_custom_csp.xml` 用 `@color/white`/`@color/black`/`#5F6368`/`#666666`。本地已完成语义 token 迁移并带 `scripts/check_ui_tokens.sh --strict` 门禁（基线违规 1 个，路径与本增量零交集），原样采用会**新增 2 个 UI token 违规**；且 `@color/white` 面板 + `@color/black` 文字在本地深色表下不可用。此外上游测试用 M2 `Theme_MaterialComponents_DayNight_NoActionBar` 作主题基座，M2 无 `colorOutline`，而本地布局已用 `?attr/colorOutline`，原样采用会**直接 inflate 失败**（实测 5/5 用例 `InflateException`） | 拒绝（局部适配） |
| **本地适配后采用（已选）** | 功能与结构 100% 采用上游；仅做三类最小适配：① 布局/矢量颜色改用本地语义角色（`?attr/colorOnSurface`/`?attr/colorOnSurfaceVariant`/`?attr/colorSurfaceContainerHighest`，`ic_search` 的 `fillColor` 用 `?attr/colorOnSurfaceVariant`，与本地既有 `ic_detail_plus/minus` 同法）；② 测试主题基座改为本地 `Theme.WebHTV.Dialog`（与生产 `WebHTVAlertDialogBuilder` 实际使用的主题一致）；③ 冲突处取 `Util` 而不带回裸 `MaterialAlertDialogBuilder` | **采用** |

安全/兼容性/回滚/性能评估：

- **安全性**：搜索为纯本地内存过滤（`visibleIndices` + `toLowerCase(Locale.ROOT).contains`），无网络、无脚本执行、不改持久化格式（`pendingDeleteIds`/JSON 脏标记语义不变）；过滤状态下 `getItems()` 仍返回完整底层集合，编辑/删除按底层索引定位，隐藏条目不受影响。
- **兼容性/回滚**：搜索为纯新增 UI 能力，空查询时 `hasSearchQuery()` 为 false，`visibleIndices` 等于全量索引，行为与合并前逐条等价；`addRow`/`wireFocusOrder` 的 mobile 分支由 `Util.isMobile()` 守卫，TV 路径完全不变。回滚为 `git revert -m 1 <merge-commit>`。
- **性能**：过滤仅在 `setSearchQuery`/结构变更时重建索引（O(n) 且 n 为站点注入条目数，实机为个位数），未进入播放/启动热路径；恢复的局部通知（`notifyItemMoved` + 单区间 `notifyItemRangeChanged`）优于上游原功能提交的全量 `notifyDataSetChanged`。
- **包体/依赖**：仅新增 Java/资源，未引入新依赖；mobile 布局新增 177 行 XML。

## 实施记录

- 2026-10-08 12:31：`git fetch origin` + `git fetch --no-tags https://github.com/webhtv/webhtv.git Silent1566:refs/remotes/webhtv-upstream/Silent1566`；确认 merge-base = `d042cd542b`（即上一轮 C37 已合并的上游 tip），上游新增恰为 3 个提交。
- 2026-10-08：`task_guard.sh start --id C45-upstream-sync-silent1566 --mode upstream --scope app --scope docs`。
- 2026-10-08：`git merge --no-commit --no-ff webhtv-upstream/Silent1566` → 1 冲突（import 块），按上表解决；随后按强制评审结论把 6 处硬编码颜色替换为本地语义 token。
- 2026-10-08：解决后立即发现门禁新增 2 个违规（`ic_search.xml`、`dialog_custom_csp.xml`）。按评审结论适配后，门禁回到"仅 1 个既有违规（`item_following.xml`）"，与合并前完全一致。
- 2026-10-08：上游定向测试首轮 5/5 失败（`InflateException`，M2 主题缺 `colorOutline`）。按评审结论把测试主题基座改为本地 `Theme.WebHTV.Dialog`，未削弱任何断言、未删除任何用例。

## 验证记录

| 验证项 | 命令/方法 | 结果 |
| --- | --- | --- |
| 冲突解决完整性 | `git diff --name-only --diff-filter=U` / `git grep "^<<<<<<< \|^>>>>>>> "` | 0 个未合并路径、0 个残留冲突标记 |
| 上游增量零丢失（行级） | 程序化比对：上游 diff 的每条新增行（长度 ≥6）是否存在于合并结果 | 14 个路径中仅 3 个"缺失"，全部为**预期改写**：`ic_search.xml` 1 行硬编码色、`dialog_custom_csp.xml` 5 行硬编码色（评审结论：改语义 token）、`CustomCspDialogTest.java` 2 行 M2 主题基座（评审结论：改本地主题） |
| 上游新增文件全部就位 | 按 `git diff --name-status` 的 `A` 列表逐个检查存在性 | 6/6 存在 |
| 上游删除文件未复活 | 按 `D` 列表检查 | 无删除项，0 复活 |
| 上游修改文件全部存在 | 按 `M` 列表检查 | 8/8 存在 |
| 上游新增字符串三语齐全 | 提取弹窗/设置/布局引用的 66 个 `R.string`，逐语言比对定义 | `values`/`zh-rCN`/`zh-rTW` 缺失均为 0 |
| 上游修复真实生效（反证） | 程序化检查 `remove()` 内 `updateModeVisibility()`+`refreshVisibleIndices()`；`moveItemToIndex` 使用 `notifyItemMoved`+`notifyItemRangeChanged` 且**不含** `notifyDataSetChanged()`；`hasSearchQuery()` 禁止移动 | 6/6 通过 |
| 本地主题契约未被回退 | `grep -c WebHtvAlertDialogBuilder` / `grep -c "new MaterialAlertDialogBuilder"` | `CacheManagementDialog` 4 处主题化 builder、0 处裸 builder；`CustomCspDialog` 2 处主题化 builder |
| mobile 布局与 main 布局 ID 一致 | 提取两文件 `android:id` 集合做 diff | 25 个 ID **集合完全一致**（同一 binding 类可服务两套布局） |
| 本地既有改动零丢失 | 交集 7 个路径逐行比对本地新增行是否仍在合并树 | 仅 `CacheManagementDialog.java` 2 行为**预期改写**（`setOrientation(HORIZONTAL)`/`setPadding(0,10,0,10)` 被上游 mobile 分支参数化为 `mobile ? … : …`，语义保留且 TV 取值不变）；其余 6 个路径 0 缺失 |
| 双 flavor Java 编译 | `./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon` | `BUILD SUCCESSFUL`，退出码 0 |
| 双 flavor androidTest Java 编译 | `./gradlew :app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac :app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac --no-daemon` | `BUILD SUCCESSFUL`（含上游新增 `CacheManagementDialogLayoutTest`） |
| 上游定向单测 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests CustomCspDialogTest --tests CustomCspSettingTest --tests "com.fongmi.android.tv.theme.*" --tests SiteInjectHomeButtonSourceTest --tests SiteDialogThemeSourceTest` | `BUILD SUCCESSFUL`；**27 个测试类 / 176 个用例，failures=0 errors=0 skipped=0**（含 `CustomCspDialogTest` 5、`CustomCspSettingTest` 3） |
| Leanback 全量单测 vs 基线 | 基线用独立 worktree（`d8daedf86c`）跑同一命令，逐条比对失败集合 | 基线 9 失败 / 合并后 8 失败，**新增失败 0**；1 个基线失败用例（`RealtimeSubtitleTranslatorTest.boundedQueuePreservesOrder…`）合并后通过，属既有 flaky，非本次引入 |
| Mobile 全量单测 vs 基线 | 同上 | 基线 `tests=5217 skipped=3 failures=7 errors=0` / 合并后 `tests=5220 skipped=3 failures=7 errors=0`，失败集合**逐条一致，新增 0** |
| UI token 门禁（合并前基线） | 合并前工作树 `bash scripts/check_ui_tokens.sh --strict` | `layouts=383 hex_layouts=1 drawables=553 hex_drawables=0`、`violations=1`（既有 `item_following.xml`）、`contrast failures=0 min=4.28` |
| UI token 门禁（合并后，适配前） | 工作树运行 | `layouts=384 hex_layouts=2 drawables=554 **hex_drawables=1**`、`violations=3` → 相对基线**新增 2 个违规** |
| UI token 门禁（合并后，适配后） | 工作树运行 | `layouts=384 **hex_layouts=1** drawables=554 **hex_drawables=0**`、`violations=1`（同为既有 `item_following.xml`）、`contrast failures=0 min=4.28` → **相对基线零新增违规** |
| 双 flavor debug APK 打包 | `./gradlew :app:assembleLeanbackArm64_v8aDebug :app:assembleMobileArm64_v8aDebug --no-daemon` | `BUILD SUCCESSFUL`（未打正式包） |
| TV 实机：覆盖安装与启动 | `adb -s 192.168.50.3:5557 install -r app-leanback-arm64_v8a-debug.apk`；monkey LEANBACK_LAUNCHER | `Success`；`mResumedActivity` = `HomeActivityCurrent`；`FATAL EXCEPTION` 计数 0 |
| TV 实机：既有本地能力未回归 | 首页截图 + UIAutomator | 本地首页按钮（首页/闪电电影/剧集/综艺/动漫/短剧、直播/搜索/收藏/推送）、WebHome 站点导航、最近观看列表、`versionText=5.6.0-202610081311` 全部正常 |
| TV 实机：设置页缓存行（本地语义前景色） | 进入 `SettingActivity` 滚动至缓存行 | 文案 `缓存管理`，`cacheText` = `42.7 MB / 760.6 MB`，前景 `?attr/webhtvColorOnWallpaper` 渲染正常（截图确认） |
| TV 实机：缓存管理弹窗（TV 路径未被 mobile 分支影响） | 确认键打开 | 窗口标题/`summary`=`共 42.7 MB / 系统配额 760.6 MB`/`status`=`扫描于 13:25:52`；模块行保持**横排**（`未归类缓存 72.6%` 详情占 `[152,308][1324,357]`，`由模块管理`/`上限` 同排右侧）→ TV 分支未受影响 |
| TV 实机：站点注入搜索框渲染 | `SettingEnhanceActivity` → 站点注入 → 打开 `CustomCspDialog` | `siteSearch` = `搜索站点注入…` 渲染于 `[270,250][1649,361]`，`ic_search` 起始图标可见；弹窗其余控件（倒序/启用/位置/新增/识别/编辑/文本/取消/确定）全部正常 |
| TV 实机：搜索过滤生效 | 输入 `webhome` | 2 条站点注入 → 仅 `1. WebHome 1` 保留，`homePage`/`key` 明细正常渲染 |
| TV 实机：无匹配空状态（上游 tip 修复） | 输入 `zzzz` | `searchEmpty` = `没有匹配的站点注入` 可见（`com.silent.android.webhtv:id/searchEmpty`）→ 上游 tip 的空状态修复真实生效 |
| 手机实机：覆盖安装与启动 | `install -r app-mobile-arm64_v8a-debug.apk`；monkey LAUNCHER | `Success`；`mResumedActivity` = `HomeActivityCurrent`；`FATAL EXCEPTION` 计数 0 |
| 手机实机：设置页缓存行 | 底部导航 → 设置 → 滚动至缓存行 | 文案 `缓存管理`，`cacheText` = `43.5 MB / 760.6 MB` |
| 手机实机：缓存弹窗模块行竖排（上游修复） | 点击缓存行 | 模块详情占满整行宽度（`未归类缓存  71.3%` 于 `[124,304][1796,347]`），两个操作按钮在**下一行**分成独立两列（`由模块管理` `[124,380][953,464]` / `上限` `[967,380][1796,464]`）→ 上游 mobile 竖排修复生效 |
| 手机实机：窄屏 + 1.3x 字体（上游修复的决定性场景） | `wm size 720x1600` + `wm density 320` + `font_scale 1.3`，重进缓存弹窗 | 模块详情占满宽度 `[68,466][652,529]`（修复前该场景明细宽度塌缩至 16px），操作按钮两列 `[68,624][352,720]`/`[368,624][652,720]`；**滚动后底部策略行与清理按钮全部完整可见**：`自动清理：关`/`保留：30d`/`总计：不限`/`轻度清理`/`标准清理`/`深度清理`/`刷新`/`确定` |
| 手机实机：模块级确认框（本地主题化 builder） | 点击 `诊断日志` 的「清理」 | `确认清理缓存？` / `清理 诊断日志？可重建数据将在需要时重新下载。`，`取消`/`确定` 正常 → 本地 `WebHtvAlertDialogBuilder` 与上游 mobile 分支共存无冲突 |
| 实机设备设置已恢复 | `wm size reset` / `wm density reset` / `font_scale 1.0` | 恢复为 `Physical size: 1920x1080`、`Physical density: 280`、`font_scale=1.0` |
| 空白校验 | `git diff --check` | 退出码 0 |
| 变更路径均在声明范围内 | `git status --porcelain` 过滤非 `app/`/`docs/` | 空（全部路径在 scope 内） |
| 无残留调试代码 | `git diff HEAD -- app` 过滤 `System.out.println`/`Log.d`/`TODO`/`FIXME` | 空 |

### 说明与边界

- **实机设备**：`192.168.50.3:5557`（SM-N9700 / Android 9 / 1920×1080 / 280dpi），符合 `F:/Workspace/webtv2/webhtv > 192.168.50.3:5557` 的分配规则。签名与本机 debug keystore 一致，按项目约定**覆盖安装、未卸载任何现有包**。
- **未验证项（记录，不阻塞本次合并）**：上游新增的 `CacheManagementDialogLayoutTest` 为 **androidTest**（需连接设备执行 instrumentation）。本次只验证其**编译通过**（双 flavor androidTest Java 编译 `BUILD SUCCESSFUL`），未在设备上执行该 instrumentation 套件；其覆盖的排版结论已用实机 UIAutomator 边界测量独立复核（见上表 1.3x 字体窄屏两行），结论一致。
- **既有失败用例**：Leanback 基线 9 / mobile 基线 7 个失败用例分布在 MPV fontconfig、WebHome CSS token、播放路由、TMDB 焦点等与本次 14 个路径**零交集**的领域，属本地既有问题，按 `AGENTS.md` 范围规则仅记录、不扩大修复面。
- **既有 UI token 违规**：`app/src/mobile/res/layout/item_following.xml` 在合并前后均为 `violations=1`，与本次路径零交集，属本地既有问题，仅记录。

## 回滚

- 任务前回滚锚点：`d8daedf86c57d58c18823343d8fc6510319289b9`。
- 本次为单个 merge commit；回滚方式为 `git revert -m 1 <merge-commit>` 或重置到锚点。
- 上游引用 `refs/remotes/webhtv-upstream/Silent1566` 为本任务新建的本地引用，不推送；如需清理可 `git update-ref -d refs/remotes/webhtv-upstream/Silent1566`。
- 本次未推送任何远端、未创建 PR、未打正式包。

## 当前状态与下一步

- 状态：合并 + 1 冲突解决 + 6 处语义 token 适配 + 1 处测试主题基座适配；双 flavor Java/androidTest 编译、双 flavor 全量单测零新增回归、UI token 门禁零新增违规、TV 与手机实机主路径全部通过。
- 下一动作：`bash .codex/scripts/task_guard.sh finish --verified "<evidence>" --commit-message "merge: 合并上游 webhtv/webhtv Silent1566 站点注入搜索与手机缓存排版修复"`，随后由脚本自动创建本地 annotated recovery tag。不推送。
