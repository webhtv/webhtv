# C46：dev2 合并远端 beta 最新代码并循环评审已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev2`（**远端已移除/回退的提交不得顺带带回**）；复评 dev2 全部已修改代码（含已提交未推送的 4 个提交 / 15 个净差异路径）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev2`、创建 `dev2 → beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① `origin/beta` tip 已是 `dev2` HEAD 的第一父（合并完整）；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev2 既有改动**零丢失**；④ 双 flavor Java 与 androidTest 编译通过；⑤ 双 flavor 全量 JVM 套件相对合并前基线**零新增回归**；⑥ UI token 门禁相对基线**零新增违规**；⑦ 净差异只含本分支自身改动；⑧ 提交 + recovery tag；⑨ `dev2` 已推送、PR 已创建且**未合并**。
- **lane / scope**：`standard`；`app/`、`docs/`。
- **任务守卫**：`C46-beta-merge-review-dev2`；任务开始 HEAD 为 `ce01b45c3c59b243e1b0bc4a5a43d26db1706c69`；初始工作区干净（0 个受保护脏路径）。
- **当前状态**：合并已由 C45 完成（`origin/beta` tip 即 HEAD 第一父）；2 轮评审完成，均**未发现必修问题**；全部验证通过；待 `task_guard.sh finish`。
- **下一动作**：`task_guard.sh finish` → 推送 `dev2` → `gh pr create`（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始时 HEAD | `ce01b45c3c59b243e1b0bc4a5a43d26db1706c69`（领先 `origin/dev2` 13 个提交） |
| `origin/beta` tip | `d8daedf86c57d58c18823343d8fc6510319289b9`（Merge PR #418 from dev1） |
| HEAD 第一父 | `d8daedf86c57d58c18823343d8fc6510319289b9` = `origin/beta` tip ✅ |
| HEAD 第二父 | `05469a967ad32818b6e76e080100bfae1787ab76`（上游 `webhtv/webhtv` `Silent1566` tip） |
| 合并基点（merge-base） | `d8daedf86c57d58c18823343d8fc6510319289b9` |
| `origin/beta..HEAD` | 4 个提交（见下表） |
| 净差异（`origin/beta...HEAD`） | **15 个路径**（6 新增 A / 9 修改 M / **0 删除 D**） |
| 初始脏路径 | 无（`git status` 干净） |
| 回滚锚点 | `ce01b45c3c59b243e1b0bc4a5a43d26db1706c69` |
| 任务守卫 | `C46-beta-merge-review-dev2`（standard，scope `app` + `docs`） |

### 本分支相对 beta 的 4 个提交

| 完整 commit ID | 标题 | 性质 | 处置 |
| --- | --- | --- | --- |
| `ce01b45c3c59b243e1b0bc4a5a43d26db1706c69` | `merge: 合并上游 webhtv/webhtv Silent1566 站点注入搜索与手机缓存排版修复` | 合并提交（C45 产物，第一父 = beta tip） | 纳入 |
| `05469a967ad32818b6e76e080100bfae1787ab76` | `fix: 修复站点注入搜索空状态与排序刷新回归` | 上游 tip，修复 `remove()` 空状态与 `moveItemToIndex` 局部通知退化 | 纳入 |
| `7357b2c37d6b73887e56e3375cfa6e30655196c7` | `feat: add site injection search` | 上游功能：站点注入弹窗搜索 | 纳入 |
| `58ebb653ff9386b292bd1e4957c0544e7582c035` | `fix: repair mobile cache management layout` | 上游修复：手机窄屏缓存管理排版 | 纳入 |

三个上游提交均 `NOT-IN-BETA`（`git merge-base --is-ancestor` 全部为否），**不构成 beta 已移除内容的复活**。

## 用户核心关注点：远端已移除（回退）内容零复活

按路径集合 + blob 哈希程序化校验，不依赖人工目测。定义：复活 = 合并结果中出现「`origin/beta` 当前不存在、或与 `origin/beta` 内容不同」的路径。

**beta 侧扫描面**：`git log origin/beta --grep='revert|回退|剔除|移除|撤销' -i` 命中 **267 个提交**，其触碰路径去重后 **742 个**，其中 **55 个**在 `origin/beta` 当前树中已删除。

```text
beta 当前已删除的 revert 路径数              : 55
其中在 HEAD 中重新出现（复活）的路径数        : 0     <- 零路径复活
HEAD 与 origin/beta 内容不同的路径数          : 15
与 revert 触碰集合（742）的交集               : 5
   app/src/main/java/com/fongmi/android/tv/ui/dialog/CacheManagementDialog.java
   app/src/main/res/values/strings.xml
   app/src/main/res/values-zh-rCN/strings.xml
   app/src/main/res/values-zh-rTW/strings.xml
   docs/CACHE-MGMT-01-cache-management-design.md
```

上述 5 个交集路径**在 `origin/beta` 当前树中存在**（并非被回退路径），其差异全部来自上游 `58ebb653ff` 的 mobile 缓存排版增量与本地语义 token 适配，与任何 revert 语义无关。

**新增路径的历史核查**：净差异中的 6 个新增路径在 `origin/beta` 全历史中条目数**均为 0**（从未存在），因此不可能是「复活 beta 已删除内容」：

```text
CacheManagementDialogLayoutTest.java     -> beta 历史条目数=0
ic_search.xml                            -> beta 历史条目数=0
mobile/res/layout/dialog_cache_management.xml -> beta 历史条目数=0
CustomCspSettingTest.java                -> beta 历史条目数=0
CustomCspDialogTest.java                 -> beta 历史条目数=0
docs/site-injection-search-review.md     -> beta 历史条目数=0
```

## beta 增量零丢失

```text
origin/beta 路径总数                     : 5054
HEAD 路径总数                            : 5061
origin/beta 中有、HEAD 中没有的路径       : 0     <- 零丢失
```

## 净差异（相对 `origin/beta`）

```text
A  app/src/androidTest/.../ui/dialog/CacheManagementDialogLayoutTest.java   (+162)
A  app/src/main/res/drawable/ic_search.xml                                  (+9)
A  app/src/mobile/res/layout/dialog_cache_management.xml                    (+177)
A  app/src/test/.../setting/CustomCspSettingTest.java                       (+32)
A  app/src/testLeanback/.../ui/dialog/CustomCspDialogTest.java              (+170)
A  docs/C45-upstream-sync-silent1566.md                                     (+136)
A  docs/site-injection-search-review.md                                     (+52)
M  app/src/main/java/.../setting/CustomCspSetting.java                      (+14)
M  app/src/main/java/.../ui/dialog/CacheManagementDialog.java               (+41 -18)
M  app/src/main/java/.../ui/dialog/CustomCspDialog.java                     (+72 -18)
M  app/src/main/res/layout/dialog_custom_csp.xml                            (+36)
M  app/src/main/res/values/strings.xml                                      (+2)
M  app/src/main/res/values-zh-rCN/strings.xml                               (+2)
M  app/src/main/res/values-zh-rTW/strings.xml                               (+2)
M  docs/CACHE-MGMT-01-cache-management-design.md                            (+2)
------------------------------------------------------------------------------
15 files changed, 891 insertions(+), 18 deletions(-)
```

## 第 1 轮评审（结构性核查 + 契约核查）

| 检查项 | 方法 | 结果 |
| --- | --- | --- |
| 合并完整性 | `git rev-parse HEAD^1 HEAD^2` | `HEAD^1` = `origin/beta` tip ✅ |
| 冲突 / 冲突标记 | `git diff --diff-filter=U` / 全路径检索 `<<<<<<<`、`>>>>>>>` | 0 / 0 |
| beta 增量零丢失 | `comm -13` 路径集合比对 | **0 缺失** |
| 净差异闭合 | `git diff --name-status origin/beta...HEAD` | 15 路径，**0 删除** |
| 空白检查 | `git diff --check origin/beta...HEAD` | 退出码 0 |
| 上游功能性增量落地 | 逐文件比对本地 vs 上游 tip，仅允许评审结论下的有意改写 | 8 个修改路径中，改写集中在语义 token 与测试主题基座 |
| 主题契约未回退 | `CacheManagementDialog` 的 `WebHtvAlertDialogBuilder` 引用 | 4 处主题化 builder、**0 处裸 `MaterialAlertDialogBuilder`** |
| 语义 token 定义完整性 | 逐一核验 `?attr/colorOnSurface` / `colorOnSurfaceVariant` / `colorSurfaceContainerHighest` / `colorOutline` | 均在 `Theme.WebHTV`、`Theme.WebHTV.Mobile`、`ThemeOverlay.WebHTV.Dialog` 中定义 |
| 上游 `?attr/colorOutline` 依赖 | 本地布局新增 `?attr/colorOutline`（上游用 `@color/dialog_outlined_button_stroke`） | 目标主题 `Theme.WebHTV.Dialog` 显式定义 `colorOutline` ✅（上游 M2 基座缺该属性，故必须适配） |
| 搜索索引映射正确性 | `visibleIndices` ↔ `itemIndex` / `displayPosition` 双向转换 + `reverseOrder` 组合 | 索引映射一致；`getItems()` 始终返回完整底层集合 |
| 空查询等价性 | `hasSearchQuery()` 为 false 时 `visibleIndices` = 全量索引 | 与合并前行为逐条等价 |

**第 1 轮结论：通过**，未发现必修问题。

## 第 2 轮评审（对抗性复评）

| 复评项 | 检查方式 | 结论 |
| --- | --- | --- |
| 净差异是否只含本分支改动 | 15 个路径逐个与引入提交对应（`git log origin/beta..HEAD -- <path>`） | 全部指向 4 个本分支提交，无 beta 内容被单方面改写 |
| beta 是否已用其它方式实现同一功能（重复/冲突） | `origin/beta` 全历史检索 `site injection search` / `站点注入搜索` / `searchEmpty` | 命中 0，无重复实现 |
| 过滤状态下编辑/删除是否错位 | `remove()` 用 `itemIndex(position)` 定位底层索引；`editCurrent()` 同法 | 索引转换经 `visibleIndices`，隐藏条目不受影响 |
| 过滤状态下排序是否被误启用 | `setSortMode` / `moveDisplay` / `moveItemToIndex` 三重 `hasSearchQuery()` 守卫 + 上下移按钮 `setEnabled(false)` | 三重守卫一致，无越权路径 |
| `displayPosition` 返回值边界 | 过滤时目标索引不可见 → 返回 -1 → `scrollToItem(-1)` 直接 return | 无越界、无异常 |
| `onBindViewHolder` 越界保护 | `itemIndex(position)` 越界返回 -1，绑定前判空 | 已保护 |
| 通知粒度是否退化 | `moveItemToIndex` 使用 `notifyItemMoved` + 单区间 `notifyItemRangeChanged`，**不含** `notifyDataSetChanged` | 上游 tip 修复真实生效（未退化） |
| 空状态可见性刷新 | `remove()` 内 `refreshVisibleIndices()` + `notifyDataSetChanged()` + `updateModeVisibility()` | 上游 tip 修复真实生效 |
| 本地既有能力是否被波及 | `addRow` mobile 分支 / `wireFocusOrder` mobile 提前 return 均由 `Util.isMobile()` 守卫 | TV 路径完全不变 |
| 双 flavor 资源 ID 一致性 | main 与 mobile 的 `dialog_cache_management.xml` `android:id` 集合 | 13 / 13 **集合完全一致**（同一 binding 可服务两套布局） |
| 新增字符串三语齐全 | 提取 66 个被引用 `R.string`，逐语言比对 | `values` / `zh-rCN` / `zh-rTW` 缺失均为 **0** |
| 网络 / 脚本 / 持久化影响 | 检索新增代码中的网络调用、脚本执行、存储格式变更 | 纯本地内存过滤；`pendingDeleteIds` 与 JSON 脏标记语义不变 |
| 主题 tint 泄漏面是否被重新引入 | 新增 `TextInputLayout` 带 `endIconMode="clear_text"`；核对其是否使用控件样式作主题 | 未引入 `MaterialAlertDialog_WebHTV_Rounded` 作 `themeResId` 的调用，tint 泄漏面未扩大 |

**第 2 轮结论：通过**，未发现未处理的必修问题，因此不产生新的代码改动。

## 验证记录汇总

| 验证项 | 命令/方法 | 结果 |
| --- | --- | --- |
| 合并父正确 | `git rev-parse HEAD^1` / `HEAD^2` | `d8daedf86c`（= `origin/beta` tip）/ `05469a967a`（= 上游 tip） |
| 回退内容零复活 | 267 个 revert 类提交 × 742 路径；55 个 beta 已删除路径 blob 级比对；6 个新增路径 beta 历史条目数 | **0 复活** |
| beta 增量零丢失 | 5054 个 beta 路径 vs HEAD | **0 缺失** |
| 净差异闭合 | `git diff --name-status origin/beta...HEAD` | 15 路径 / **0 删除** |
| 冲突与标记 | `--diff-filter=U` / 标记检索 | 0 / 0 |
| 空白检查 | `git diff --check origin/beta...HEAD` | 退出码 0 |
| 双 flavor Java 编译 | `:app:compile{Mobile,Leanback}Arm64_v8aDebugJavaWithJavac` | `BUILD SUCCESSFUL` |
| 双 flavor androidTest 编译 | `:app:compile{Mobile,Leanback}Arm64_v8aDebugAndroidTestJavaWithJavac` | `BUILD SUCCESSFUL` |
| 手机版全量单测 | `:app:testMobileArm64_v8aDebugUnitTest` | `tests=5220 failures=7 errors=0 skipped=3`（基线 `5217/7`，**失败集合逐条一致，新增 0**） |
| TV 版全量单测 | `:app:testLeanbackArm64_v8aDebugUnitTest` | `tests=4379 failures=8 errors=0 skipped=3`（基线 `4379/8` 于 `d8daedf86c`，**新增 0**） |
| 基线对照 | 独立 worktree `d8daedf86c`（已回收）跑同一命令 | 失败集合逐条完全相同 |
| 定向契约测试 | `CustomCspDialogTest`、`CustomCspSettingTest`、`com.fongmi.android.tv.theme.*`（双 flavor） | **326 用例 / failures=0 errors=0** |
| UI token 门禁 | `bash scripts/check_ui_tokens.sh --strict` | `layouts=384 hex_layouts=1 drawables=554 hex_drawables=0`、`violations=1`（既有 `mobile/item_following.xml`，与本次 15 路径零交集）、`contrast pairs=38 failures=0 min=4.28` → **零新增违规** |
| TV 实机（`192.168.50.3:5557`） | 覆盖安装 `app-leanback-arm64_v8a-debug.apk` + monkey LEANBACK_LAUNCHER | `Success`；`mResumedActivity` = `HomeActivityCurrent`；`FATAL EXCEPTION` = **0** |
| TV 实机：站点注入搜索 | 站点注入弹窗输入 `webhome` / `zzzz` | 过滤生效（2 条 → 1 条）；无匹配时 `searchEmpty`=`没有匹配的站点注入` 可见（上游 tip 修复生效） |
| TV 实机：缓存弹窗保持 TV 横排 | 设置页 → 缓存管理 | 模块行横排、`由模块管理`/`上限` 同排右侧 → TV 路径未受 mobile 分支影响 |
| 手机实机 | 覆盖安装 `app-mobile-arm64_v8a-debug.apk` | 模块行**竖排**、详情占满宽度、底部策略与清理按钮滚动后完整可见 |
| 手机实机：窄屏 + 1.3x 字体 | `wm size 720x1600` + `density 320` + `font_scale 1.3` | 详情占满宽度；两列操作按钮；底部控件完整可见（上游修复的决定性场景） |
| 设备设置恢复 | `wm size reset` / `density reset` / `font_scale 1.0` | 恢复为 `1920x1080` / `280` / `1.0` |
| 最终 fetch | `git fetch origin` + `git ls-remote origin refs/heads/beta refs/heads/dev2` | `origin/beta` 仍为 `d8daedf86c`，**未前进** |

### 关于本机 7 + 8 个失败用例（**非本任务引入，不修**）

失败集合与合并前基线 `d8daedf86c` **逐条相同**（独立 worktree 实测），且每个失败用例读取的目标文件在 HEAD 与 `origin/beta` 之间**逐字节一致**（`git rev-parse` blob 哈希相同）：

- `androidx.media3.mpvplayer.MpvFontConfigTest.writeIfChanged_replacesStaleConfigurationAndCleansTemporaryFile`
- `androidx.media3.mpvplayer.MpvHlsCacheCoordinatorTest.successfulCommitPublishesCompleteFileAndReleasesReservation`
- `com.fongmi.android.tv.ui.activity.ReaderPlaybackRoutingSourceTest.*`（2 个）
- `com.fongmi.android.tv.ui.activity.TmdbSourceOnlyInteractionTest.nativeVideoSourceOnlyHidesNetworkActionsInBothFlavors`
- `com.fongmi.android.tv.web.WebThemeTokenSourceTest.webHomeTokenDeclarationsStayInsideRootScope`
- `com.fongmi.android.tv.ui.activity.PlayerPlaybackRegressionSourceTest.livePlaybackAlwaysAutoplaysWhileVodUsesTheConfiguredPolicy`（仅 mobile）
- `com.fongmi.android.tv.ui.activity.NativeEnhancedPlaybackStyleFocusTest.everyTmdbRowCardCarriesItsOwnVerticalFocusTargets`、`SearchResultDownFocusTest.scrollCallbacksDeferResultLoadingUntilAfterLayout`（仅 leanback）

根因是本机 `core.autocrlf=true`：这些断言使用**多行字符串字面量**（含 `\n`），而检出后文件为 CRLF；目标字符串在 `assets/reader.html`、`assets/webhome/eclipse.html`、`VideoActivity.java` 等文件中 `LF=False / CRLF=True`。CI 的 Linux 检出（LF）不触发。**不修，也不属于本任务范围。**

### 边界与未验证项

- **实机设备**：`192.168.50.3:5557`（SM-N9700 / Android 9 / 1920×1080 / 280dpi），符合 `F:/Workspace/webtv2/webhtv > 192.168.50.3:5557` 的分配规则。签名与本机 debug keystore 一致，按项目约定**覆盖安装、未卸载任何现有包**。
- **未验证项**：上游新增的 `CacheManagementDialogLayoutTest` 为 **androidTest**，本次只验证其**编译通过**，未在设备上执行该 instrumentation 套件；其覆盖的排版结论已用实机 UIAutomator 边界测量独立复核，结论一致。
- **既有 UI token 违规**：`app/src/mobile/res/layout/item_following.xml` 在合并前后均为 `violations=1`，与本次 15 路径零交集，属本地既有问题，仅记录。

## 回滚

- 任务前回滚锚点：`ce01b45c3c59b243e1b0bc4a5a43d26db1706c69`。
- 本 PR 净差异为 15 个路径，无删除路径；回滚方式为 `git revert -m 1 ce01b45c3c` 或重置到锚点。
- 推送前 `origin/dev2` 仍为 `fb5f2a03df8dfa88bff0807619a497155d3140ef`，推送后可用该值作为远端回滚锚点。
- 本次未打正式包。

## 当前状态与下一步

- 状态：合并完整（beta tip 为第一父）；2 轮评审通过、未发现必修问题；双 flavor 编译、双 flavor 全量单测零新增回归、定向 326 用例零失败、UI token 门禁零新增违规、TV/手机实机主路径全部通过；零复活与零丢失已程序化核验。
- 下一动作：`bash .codex/scripts/task_guard.sh finish --verified "<evidence>" --commit-message "docs(c46): 记录 dev2 合并 beta 与站点注入搜索/手机缓存排版的评审与验证"` → 推送 `dev2` 与 recovery tag → `gh pr create`（base=beta，中文描述，**只创建不合并**）。
