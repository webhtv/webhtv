# C24：dev1 合并远端 beta 最新代码与合并后复评

## Recovery anchor

- **目标**：将远端 `beta` 最新代码合入 `dev1`，循环复评相对 `origin/dev1` 的全部未推送改动（含已提交未推送的主题改动），发现问题则最小修复并验证；通过后提交、推送并创建中文 PR 到 `beta`，只创建 PR，不合并。
- **验收**：合并结果包含远端 beta 最新提交；不重新带入远端已移除/剔除的回退或主题提交；本地未推送改动与 beta 新改动组合后通过定向静态检查、单元/源码测试和 Debug 测试包覆盖安装启动验证；PR 描述用中文说明改动内容并保持排版清楚。
- **当前状态**：合并、4 轮复评、2 处最小修复和验证已完成；准备执行任务守卫收尾、推送并创建 PR。
- **基线**：合并前本地 `dev1@8da7bd625373ec1028b834dbfb01d8edf69a1600`；远端 `beta@b15b6cdfe044cf7498df3ab5885fa4e1be663cf7`；远端 `origin/dev1@5d788440bb8d6d592482f7e297f326298bffc144`。
- **合并提交**：`5e51a2c0eb2e392d4cd3fa01389652a2fc33782f`，父提交为上述本地 dev1 和远端 beta。
- **当前关键文件**：本地未推送主题/详情页改动（`TmdbDetailActivity`、`DetailModeHost`、`EnhancedDetailController`、`TmdbCinemaTheme` 等）与 beta 新增的 TMDB 语言策略、追更元数据、配置同步、播放器 Surface 修复和 Native loader 保护。
- **已完成证据**：合并无冲突；`git diff --check` 通过；被剔除提交 `5682f2b054b577b0db5c6f7c1143f2eebb51858e`、`2faa1f37757f` 均不在合并结果祖先中。
- **未验证边界**：没有执行多设备真实远端同步矩阵；Go/Rust 工具链当前不可用，本轮未重复运行其测试；播放器自动线路回退和超级解析修复沿用远端任务文档中的源码评审、测试与设备证据，本任务补充组合后启动冒烟。
- **下一动作**：执行任务守卫收尾，推送 `dev4`，并创建只打开不合并的中文 PR。

## 范围与提交台账

- 相对 `origin/dev1` 的本地未推送提交包含 PR #366 合入记录和 7 个主题/详情页修复提交（`ac5606571`、`d964faca5`、`245ddc0fb`、`40c7b3122`、`4d24168e8`、`7e42054de`、`8da7bd625`）。
- 远端 beta 侧本任务合入 20 个本地 dev1 之外的非合并提交，范围包括：TMDB 中文语言身份、VOD/直播接口同步、追更卡片与刷新元数据、全局历史设置、自动线路 Surface 绑定、Houdini loader 保留，以及相关测试和文档。
- 合并前已核对：远端已剔除提交不在本地 dev1、远端 beta 或合并结果祖先中，不会顺带提交。

## 评审记录

### 第 1 轮

- 合并结果 `HEAD` 的两个父提交分别为本地 `dev1@8da7bd625373ec1028b834dbfb01d8edf69a1600` 和远端 `beta@b15b6cdfe044cf7498df3ab5885fa4e1be663cf7`；`origin/beta` 是直接祖先，满足“合入远端 beta 最新代码”。
- 相对 `origin/beta` 的净差异只有 7 个主题/详情页文件；`origin/beta` 与本地之间没有主题系统运行时、编辑器、资源目录差异，远端已剔除的主题改动没有通过合并树重新出现。
- 复评本地主题改动：
  - `DetailModeHost` 新增 `isCinemaStyle()`，`EnhancedDetailController` 从硬编码 `true` 改为委托 host；Activity 绑定 `rawCinemaMode()`，恢复 Profile/Cinema 的布局与主题区分。
  - `backdropSlideAlpha()` 保持 Cinema light/dark 和 Profile light/dark 各自历史语义；light Profile 与 light Cinema 均不再叠透明水洗。
  - `LightCinemaCopyPlateDrawable` 仅在 light Cinema 激活，绘制为局部羽化白色薄雾，不覆盖海报；非 light Cinema 时清除背景和 padding。
  - light Cinema 调色板改为暖幕布/金色强调，与 Profile 冷白/绿色系区分；相关测试覆盖该约束。
- 复评远端 beta 新改动与本地主题改动组合：TMDB 语言策略、追更全量刷新与元数据、VOD/直播接口同步、播放器强制重建、Houdini loader 保留和全局历史设置均未与本地主题净差异产生冲突；合并后 `TmdbDetailActivity` 同时保留语言策略和主题修复。
- **发现问题**：`TmdbSourcePayloadParser` 对 payload `language` 只做 trim/长度限制，没有使用统一 `TmdbLanguagePolicy`。这会让协议对象仍携带 `ZH-Hans`、`zh-sg` 等别名，影响序列化、相等性和来源记录一致性；后续能力判断虽会再次归一，但协议边界不一致。

### 第 1 轮修复

- `TmdbSourcePayloadParser.normalizeLanguage()`：空语言保持为空，以保留“语言未知需推断/补齐”的既有语义；非空语言统一走 `TmdbLanguagePolicy.normalize()`。
- `TmdbSourcePayloadTest`：将现有 trim 用例升级为 `ZH-Hans` 归一化用例，并新增空语言保持空、`zh-sg`→`zh-CN`、`zh-Hant`→`zh-TW` 用例。

### 第 2 轮

- 复查修复差异：运行时代码只改 `TmdbSourcePayloadParser` 一处归一化，不改变网络请求、合并策略或页面渲染；测试明确覆盖协议语言别名与未知语言语义。
- 复查相对 `origin/beta` 的净差异：除上述修复外，仍仅包含本地 7 个主题/详情页文件，无被剔除主题系统文件，无验证截图/dump 被纳入。
- **发现问题**：合并后的 `TmdbDetailActivityLayoutTest.tmdbDetailNormalizesCachedTitleBeforeNativeEnhancedPlayback` 失败。运行时代码已经按 beta 语言策略改为 `tmdbService.preferredTitle(item, detail, tmdbConfig)`，但旧测试仍要求 `tmdbDetailTitle()` 内直接读取 `detail.name/title`，与 C21 设计“所有标题最终选择经过语言策略”相抵触。

### 第 2 轮修复

- 将该测试断言更新为要求 `tmdbDetailTitle()` 委托统一语言策略，不再要求旧的直接字段读取实现。

### 第 3 轮

- 复查测试断言：`tmdbDetailNormalizesCachedTitleBeforeNativeEnhancedPlayback` 仍验证 normalize 管线、播放历史标题和详情标题使用路径，仅把具体实现契约从直接字段读取改为 `preferredTitle()`。
- 复查运行时代码：`TmdbService.preferredTitle()` 按电影/剧集读取顶层 `title/name`，并在 translations 有目标语言时优先目标语言；比旧测试约束更完整，不会放回英文缓存标题。
- 未发现新的必须修改问题；继续执行合并后验证。

## 验证记录

- 通过：`git diff --check`。
- 通过：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.bean.TmdbSourcePayloadTest' --tests 'com.fongmi.android.tv.utils.TmdbLanguagePolicyTest' --tests 'com.fongmi.android.tv.ui.helper.TmdbSourceCapabilityPlannerTest' --tests 'com.fongmi.android.tv.ui.helper.TmdbSourceMergerTest'`（第 1 次测试失败为测试夹具在 parse 后再 setLanguage，未覆盖 parse 入口；修正夹具后同一验证通过）。
- 通过：`bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest' --tests 'com.fongmi.android.tv.ui.detail.DetailModeControllerTest' --tests 'com.fongmi.android.tv.ui.helper.TmdbCinemaThemeTest' :app:compileLeanbackArm64_v8aDebugJavaWithJavac --continue`，144 个测试通过，Leanback Java 编译通过（第一次组合验证失败为上述过期标题测试断言，修正后通过；同轮 Mobile Java 编译已由测试任务完成）。
- 通过：`bash scripts/build_arm64_debug_install.sh --flavor mobile --abi arm64-v8a --serial 192.168.50.3:5555`，Debug APK 覆盖安装成功；打包后 Gradle daemon 已停止。
- 通过：`./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.server.process.ProxyRedirectResponseTest --tests com.fongmi.android.tv.server.process.ProxyRangeResponsePolicyTest --continue`，确认短数组响应不会越界，且 302 空响应/Range 策略行为保持正确。
- 通过：指定模拟器启动 `com.silent.android.webhtv` 后进程存在（PID 3899），焦点为 `HomeActivityCurrent`，启动窗口内无 `FATAL EXCEPTION`、fatal signal 或相关 App error。

## 交付状态

- 待提交、推送和创建 PR。
