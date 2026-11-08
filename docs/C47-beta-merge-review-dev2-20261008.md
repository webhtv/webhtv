# C47：dev2 合并远端 beta 最新代码（音频指纹移除 + T3→T4 网关）并循环评审

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev2`（**远端已移除/回退的提交不得顺带带回**）；复评合并结果与 dev2 既有改动；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev2`、创建 `dev2 → beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并提交第二父为 `origin/beta` tip `24265785a3`；② 远端被回退内容**零复活**（beta 已删除的 187 个 revert 路径在合并树中零出现）；③ beta 增量**零丢失**；④ dev2 既有改动**零丢失**；⑤ 双 flavor Java 与 androidTest 编译通过；⑥ 双 flavor 全量 JVM 套件相对合并前基线**零新增回归**；⑦ UI token 门禁相对基线**零新增违规**；⑧ 净差异只含本分支自身改动；⑨ 提交 + recovery tag；⑩ `dev2` 已推送、PR 已创建且**未合并**。
- **lane / scope**：`standard`；`app/`、`docs/`、`gradle/`、`scripts/`、`智能去广-设计文档.md`。
- **任务守卫**：`C47-beta-merge-review-dev2`；任务开始 HEAD 为 `74c39d6dea36d5a4e4e872fc8af0060d1d1591cc`；初始工作区干净（0 个受保护脏路径）。
- **当前状态**：合并完成（**0 冲突**）；评审完成，未发现必修问题；全部验证通过；待 `task_guard.sh finish`。
- **下一动作**：`task_guard.sh finish` → 推送 `dev2` → `gh pr create`（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始时 HEAD | `74c39d6dea36d5a4e4e872fc8af0060d1d1591cc`（C46 文档提交） |
| `origin/beta` tip | `24265785a33d09ca1d1d3d36370d010ff1e89eac`（Merge PR #419 from dev3） |
| 合并基点（merge-base） | `d8daedf86c57d58c18823343d8fc6510319289b9` |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`，由 task_guard `finish` 创建合并提交 |
| 合并结果 | 128 路径自动合入，**0 冲突、0 冲突标记** |
| 合并结果树 | `904ca58fac0a839060240d0a9d008f23073f28a5` |
| 初始脏路径 | 无（`git status` 干净） |
| 回滚锚点 | `74c39d6dea36d5a4e4e872fc8af0060d1d1591cc` |
| 任务守卫 | `C47-beta-merge-review-dev2`（standard，scope `app`+`docs`+`gradle`+`scripts`+`智能去广-设计文档.md`） |

### beta 增量 ledger（9 个提交 / 128 路径，全部纳入）

`git log --oneline d8daedf86c..origin/beta`：

| 完整 commit ID | 标题 | 处置 |
| --- | --- | --- |
| `24265785a33d09ca1d1d3d36370d010ff1e89eac` | Merge PR #419 from dev3 | 纳入 |
| `6d11b98128efb6a09f4d00e8e54334c8cb9dc1c8` | Merge PR #420 from dev1 | 纳入 |
| `2283e15d1358840aeec833515f8612d08d1770ef` | `docs(c46)`: 修正 revert 检测口径与净差异路径计数 | 纳入 |
| `c2761b2ff693fc90f7b1d020ede8dff4dc522b20` | `docs(c46)`: 记录交付坐标（提交 cd88c907d / tag / PR #420） | 纳入 |
| `5e4cc37ba5839ad81e73eb312141b1d2084157d3` | `docs(c46)`: 记录 Nano 资源回退分支的可证伪区分证据 | 纳入 |
| `cd88c907d912d6f728939d6e5c102fb5f9bb655e` | `docs(c46)`: 复评 beta 合并后的 C45 交付并补齐两处遗留缺口 | 纳入 |
| `5d4a7e8e1897a249ba2601d2ddf3cb20d6516193` | merge: 合并 origin/beta 最新代码（PR#417/#418）并复评 T3→T4 本机网关 | 纳入 |
| `56ebd07f10529ce014a20c43289ee34feb3b5892` | **移除**：删除音频指纹去广告与语音广告识别功能，向 Silent1566 上游靠齐 | 纳入 |
| `c388619629291e7dc32d0cefca2202b8df9064da` | `feat(server)`: add local T3-to-T4 gateway with HTTP contract coverage | 纳入 |

**beta 增量构成**：`7 A / 100 D / 21 M`。核心为两件事——**大规模功能移除**（音频指纹去广告 + 语音广告识别，100 个路径）与**新增 T3→T4 本机网关**（`VodApi.java` + `T4Proxy.js` + 31 个契约测试）。

**与 dev2 改动的文件交集仅 3 个**（三套 `strings.xml`），无交互冲突。

## 用户核心关注点：远端已移除（回退）内容零复活

**关键：本次 beta 增量本身包含一次 100 路径的功能移除**，因此零复活核验必须按「beta 当前权威树」判定。

程序化校验（按路径集合运算，不依赖人工目测）：

```text
beta 上 revert/回退/剔除/移除/撤销 类提交命中路径（去重）  : 845
其中在 origin/beta 当前树中已删除的路径                   : 187
这 187 个路径在合并树 904ca58f 中重新出现的数量            : 0     <- 零路径复活
```

**100 个被移除路径在 dev2 侧零本地改动**（逐 blob 比对 `d8daedf86c` vs `74c39d6dea`）：

```text
本次 beta 移除且 dev2 侧存在的路径数                      : 100
其中 dev2 侧相对 merge-base 有本地改动的路径数             : 0     <- 无本地改动被静默丢弃
```

**被移除功能的悬空引用扫描**（全部为 0，无编译期/运行期残留）：

```text
AdAudioSetting / SpeechAdSetting / AudioFingerprintMatcher / SpeechAdMatcher
AdSkipCoordinator / AdSkipPromptPresenter / SignedRulePackageStore / ProbeRuleStore
  -> 引用文件数均为 0
setting_ad_audio_title / speech_ad_enabled / speech_ad_keywords / ad_audio_candidate_title
  -> 三语 strings.xml 定义数均为 0
```

> 注：`RealtimeSubtitleController.java` 中检索 `audioFingerprint` 的命中为 `readAudioHeadroomUs` 的子串巧合，非残留引用。

## 净差异（相对 `origin/beta`）

```text
A  app/src/androidTest/.../ui/dialog/CacheManagementDialogLayoutTest.java
A  app/src/main/res/drawable/ic_search.xml
A  app/src/mobile/res/layout/dialog_cache_management.xml
A  app/src/test/.../setting/CustomCspSettingTest.java
A  app/src/testLeanback/.../ui/dialog/CustomCspDialogTest.java
A  docs/C45-upstream-sync-silent1566.md
A  docs/C46-beta-merge-review-dev2-20261008.md
A  docs/site-injection-search-review.md
M  app/src/main/java/.../setting/CustomCspSetting.java
M  app/src/main/java/.../ui/dialog/CacheManagementDialog.java
M  app/src/main/java/.../ui/dialog/CustomCspDialog.java
M  app/src/main/res/layout/dialog_custom_csp.xml
M  app/src/main/res/values/strings.xml
M  app/src/main/res/values-zh-rCN/strings.xml
M  app/src/main/res/values-zh-rTW/strings.xml
M  docs/CACHE-MGMT-01-cache-management-design.md
------------------------------------------------------------------------------
16 路径（8 A / 8 M / 0 D）
```

**净差异闭合性**：`合并树路径集` 与 `dev2 ∪ beta` 路径集比较——复活 0、丢失 0（100 个差异路径全部为 beta 有意移除）。`合并树 vs beta` 差异 16 路径全部为 dev2 自身改动；`合并树 vs dev2` 差异 128 路径全部为 beta 增量。

**beta 关键文件在合并树中与 beta 逐字节一致**：`app/build.gradle`、`gradle/libs.versions.toml`、`Nano.java`、`PlaybackActivity.java`、`PlayerManager.java`、`智能去广-设计文档.md` 全部 `SAME-AS-BETA`。

## 评审（两轮）

### 第 1 轮：结构性核查

| 检查项 | 方法 | 结果 |
| --- | --- | --- |
| 冲突 / 冲突标记 | `git diff --diff-filter=U` / 标记检索 | 0 / 0 |
| 路径集合闭合 | 合并树 vs `dev2 ∪ beta` | 复活 0、丢失 0 |
| 回退内容零复活 | 187 个 beta 已删除 revert 路径 × 合并树 | **0 复活** |
| 被移除路径本地改动 | 100 路径逐 blob 比对 | **0 丢失** |
| 悬空引用 | 8 个已移除类 + 5 个已移除字符串 | 0 / 0 |
| beta 关键文件一致性 | build.gradle / libs.versions.toml / Nano / PlaybackActivity / PlayerManager | 全部 `SAME-AS-BETA` |
| 三语交集路径完整性 | `strings.xml` 三语：dev2 新增 2 条搜索字符串在、beta 新增字符串在、被移除字符串不在 | 全部符合 |
| 新增文件就位 | T4Proxy.js / VodApi.java / VodApiTest.java / 3 份 docs | 5/5 存在 |
| 空白检查 | `git diff --check` | 退出码 0 |
| 变更路径在 scope 内 | 128 个 dirty 路径逐个匹配 scope | 0 个越界 |

### 第 2 轮：对抗性复评

| 复评项 | 检查方式 | 结论 |
| --- | --- | --- |
| 净差异是否只含本分支改动 | 16 路径逐个溯源到 C45/C46 提交 | 是，无 beta 内容被单方面改写 |
| T3→T4 网关是否真实可用（非仅 JSON 助手） | 设备内 `curl` 到 Nano 端口真实 HTTP 调用 | `ac=config` 返回 200 + 完整 type4 站点信封，每个站点 `api` 指回网关自身 |
| 网关契约：分类请求 | `ac=detail&t=1&pg=1&key=<key>` | 返回苹果CMS 列表信封 `{"page":1,"pagecount":1,"limit":20,"total":20,"list":[]}` |
| 网关契约：OPTIONS 预检 | `-X OPTIONS /vod/api` | 204 |
| 网关契约：相邻端点不认领 | `GET /vod/apiX` | 空响应（非 JSON 信封），未被误路由 |
| 网关契约：URL 编码 key | config 中 `哔哩哔哩` 等中文站点 key | 以 `%E5%93%94...` 正确编码出现 |
| 自导入拒绝 | `VodApi.isSelfGateway` 单元用例 + 设备站点 api 全部指回网关 | 逻辑与设备表现一致 |
| 被移除功能是否真正消失 | 去广告设置页设备渲染 | TV/手机均**无音频检测分组**、无空分组、无崩溃 |
| 本地主题/搜索契约未回退 | 6 个 C45 路径 blob 与 `74c39d6dea` 比对 | 全部 `IDENTICAL` |
| 双 flavor 资源 ID 一致性 | main vs mobile `dialog_cache_management.xml` | 13/13 集合一致 |
| 包体影响 | APK 体积 | leanback 201.6MB→200.2MB、mobile 201.6MB→200.2MB（功能移除后下降） |

**两轮评审结论：通过**，未发现必修问题，因此本次不产生新的代码改动。

## 验证记录汇总

| 验证项 | 命令/方法 | 结果 |
| --- | --- | --- |
| 合并父正确 | `git rev-parse HEAD^2` | `24265785a3` = `origin/beta` tip |
| 冲突 | `--diff-filter=U` / 标记检索 | 0 / 0 |
| 回退内容零复活 | 845 revert 路径 → 187 beta 已删除 → 合并树 | **0 复活** |
| 被移除路径本地改动丢失 | 100 路径 blob 比对 | **0 丢失** |
| 悬空引用 | 8 类 + 5 字符串 | **0** |
| 双 flavor Java 编译 | `:app:compile{Mobile,Leanback}Arm64_v8aDebugJavaWithJavac` | `BUILD SUCCESSFUL` |
| 双 flavor androidTest 编译 | `:app:compile{Mobile,Leanback}Arm64_v8aDebugAndroidTestJavaWithJavac` | `BUILD SUCCESSFUL`（8m 00s） |
| 手机版全量单测 | `:app:testMobileArm64_v8aDebugUnitTest` | `tests=4919 failures=7 errors=0 skipped=3`（用例数下降 301 因音频指纹测试被移除；失败集合与基线**逐条一致**） |
| TV 版全量单测 | `:app:testLeanbackArm64_v8aDebugUnitTest` | `tests=4078 failures=8 errors=0 skipped=3`（失败集合与基线**逐条一致**） |
| 定向契约测试 | `CustomCspDialogTest`、`CustomCspSettingTest`、`theme.*`、`VodApiTest`、`PlaybackMediaSignalHubTest`、`BackupPreferenceFilterTest`（双 flavor） | **204 用例 / failures=0 errors=0**（其中 `VodApiTest` 31/31） |
| UI token 门禁 | `bash scripts/check_ui_tokens.sh --strict` | `layouts=384 hex_layouts=1 drawables=554 hex_drawables=0`、`violations=1`（既有 `mobile/item_following.xml`）、`contrast pairs=38 failures=0 min=4.28` → **零新增违规** |
| 双 flavor APK 打包 | `:app:assemble{Leanback,Mobile}Arm64_v8aDebug` | `BUILD SUCCESSFUL`（7m 16s，未打正式包） |
| TV 实机（`192.168.50.3:5557`） | 覆盖安装 + LEANBACK_LAUNCHER | `Success`；`HomeActivityCurrent`；`FATAL EXCEPTION` = **0** |
| TV 实机：首页 | 截图 + UIAutomator | 顶部菜单（首页/闪电电影/剧集/综艺/动漫/短剧）、二级入口（直播/搜索/收藏/推送/设置）、最近观看 5 张卡片全部正常 |
| TV 实机：去广告设置页 | `am start SettingAdActivity` | 分组仅剩**基础 / 规则与统计 / 片段跳过**，音频检测分组已正确移除，无空分组、无崩溃 |
| TV 实机：T3→T4 网关 | 设备内 `curl http://127.0.0.1:9979/vod/api?ac=config` | 200 + 完整 type4 站点信封（含中文 key 编码），每个 `api` 指回网关 |
| 手机实机 | 覆盖安装 + LAUNCHER | `Success`；`HomeActivityCurrent`；`FATAL EXCEPTION` = **0** |
| 手机实机：缓存弹窗竖排 | 设置 → 缓存管理 | 模块详情占满整行（`124..1796`），操作按钮下一行等宽两列 |
| 手机实机：窄屏 + 1.3x 字体（决定性场景） | `wm size 720x1600` + `density 320` + `font_scale 1.3` | 模块详情占满宽度、两列按钮；**滚动后底部策略行（自动清理/保留/总计）与三档清理按钮、刷新/确定全部完整可见** |
| 手机实机：去广告页 | `SettingAdFragment` | 无音频检测残留分组 |
| 设备设置恢复 | `wm size reset` / `density reset` / `font_scale 1.0` | 恢复为 `1920x1080` / `280` / `1.0` |

### 关于本机 7 + 8 个失败用例（**非本任务引入，不修**）

失败集合与 C46 基线（`d8daedf86c` 独立 worktree 实测）**逐条相同**，且目标文件在合并树与 `origin/beta` 之间逐字节一致：

- `androidx.media3.mpvplayer.MpvFontConfigTest.writeIfChanged_replacesStaleConfigurationAndCleansTemporaryFile`
- `androidx.media3.mpvplayer.MpvHlsCacheCoordinatorTest.successfulCommitPublishesCompleteFileAndReleasesReservation`
- `com.fongmi.android.tv.ui.activity.ReaderPlaybackRoutingSourceTest.*`（2 个）
- `com.fongmi.android.tv.ui.activity.TmdbSourceOnlyInteractionTest.nativeVideoSourceOnlyHidesNetworkActionsInBothFlavors`
- `com.fongmi.android.tv.web.WebThemeTokenSourceTest.webHomeTokenDeclarationsStayInsideRootScope`
- `com.fongmi.android.tv.ui.activity.PlayerPlaybackRegressionSourceTest.livePlaybackAlwaysAutoplaysWhileVodUsesTheConfiguredPolicy`（仅 mobile）
- `com.fongmi.android.tv.ui.activity.NativeEnhancedPlaybackStyleFocusTest.everyTmdbRowCardCarriesItsOwnVerticalFocusTargets`、`SearchResultDownFocusTest.scrollCallbacksDeferResultLoadingUntilAfterLayout`（仅 leanback）

根因是本机 `core.autocrlf=true`：断言使用含 `\n` 的多行字符串字面量，而检出后文件为 CRLF。CI 的 Linux 检出（LF）不触发。**不修，也不属于本任务范围。**

### 边界与未验证项

- **实机设备**：`192.168.50.3:5557`（SM-N9700 / Android 9 / 1920×1080 / 280dpi），符合 `F:/Workspace/webtv2/webhtv > 192.168.50.3:5557` 的分配规则。按项目约定**覆盖安装、未卸载任何现有包**。
- **未验证项**：① 新增的 `CacheManagementDialogLayoutTest` 为 androidTest，本次只验证**编译通过**，未在设备执行 instrumentation；其排版结论已用实机 UIAutomator 独立复核。② T4Proxy.js 为 Node 侧脚本，本次未在 Node 运行时执行；其对应的 App 侧 `/vod/api` 契约已用**设备内真实 HTTP 调用**验证。
- **既有 UI token 违规**：`app/src/mobile/res/layout/item_following.xml` 在合并前后均为 `violations=1`，与本次路径零交集，仅记录。

## 回滚

- 任务前回滚锚点：`74c39d6dea36d5a4e4e872fc8af0060d1d1591cc`。
- 本次为单个 merge commit（第二父 = `24265785a3`）；回滚方式为 `git revert -m 1 <merge-commit>` 或重置到锚点。
- 推送前 `origin/dev2` 仍为 `fb5f2a03df8dfa88bff0807619a497155d3140ef`，可作为远端回滚锚点。
- 本次未打正式包。

## 当前状态与下一步

- 状态：合并完成（0 冲突）；零复活（0/187）、零丢失（0/100）、悬空引用 0；双 flavor 编译与 androidTest 编译通过；双 flavor 全量单测零新增回归；定向 204 用例零失败；UI token 门禁零新增违规；TV/手机实机主路径全部通过（含 T3→T4 网关设备内真实 HTTP 契约验证）；两轮评审均未发现必修问题。
- 下一动作：`bash .codex/scripts/task_guard.sh finish --verified "<evidence>" --commit-message "merge: 合并 origin/beta 最新代码（音频指纹移除与 T3→T4 本机网关）"` → 推送 `dev2` 与 recovery tag → `gh pr create`（base=beta，中文描述，**只创建不合并**）。
