# C37：合并上游 `webhtv/webhtv` 分支 `Silent1566` 最新代码（缓存管理 P0–P4）

## Recovery anchor

- **目标**：把上游 `https://github.com/webhtv/webhtv` 的 `Silent1566` 分支最新代码合并进本地 `dev2`，吸收其全部功能性增量，同时保留本地既有能力与本地主题/UI 语义 token 合同；合并后完成编译、单测、UI token 检查与 TV 实机验证，并提交、打本地恢复 tag。
- **验收标准**：① 上游 tip `d042cd542b8768ec9dbd2582923088e54a7bfeb1` 成为合并提交第二父；② 上游 72 个变动路径（50 新增 + 22 修改）的**每一行新增内容**都存在于合并结果中（程序化校验，允许列表/枚举因并集而改写）；③ 本地既有改动零丢失（22 个双方都改的文件全部为并集保留）；④ 双 flavor Java 编译通过；⑤ 缓存包 13 个测试类全部通过；⑥ `scripts/check_ui_tokens.sh --strict` 相对合并前零新增违规；⑦ TV 实机可进入缓存管理并完成真实清理、设置行数值随 `CACHE` 事件即时刷新；⑧ 提交 + 本地 annotated recovery tag。
- **当前状态**：合并完成（0 未解决冲突），全部验证通过，待 `task_guard.sh finish`。
- **下一动作**：`bash .codex/scripts/task_guard.sh finish --verified <evidence> --commit-message <msg>`（原子合并提交 + recovery tag），不推送。

## 冻结基线

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始时 HEAD | `e72239063b4122c5cfc3658231fed2b06798848a`（Merge PR #403 from dev3） |
| 上游仓库 | `https://github.com/webhtv/webhtv`（默认分支 `main`） |
| 上游分支/tip | `Silent1566` / `d042cd542b8768ec9dbd2582923088e54a7bfeb1` |
| 上游本地引用 | `refs/heads/upstream-silent1566` = `d042cd542b8768ec9dbd2582923088e54a7bfeb1` |
| Merge base | `d187f6ae8bfaf0a4720724281d0a91186c57f73d`（2026-09-24，`test(exo): avoid package-private recoverable access`，也是上游 `main` 的历史头） |
| 本地领先量 | `d187f6ae8b..HEAD` = 2915 个提交 |
| 上游增量 | `d187f6ae8b..upstream-silent1566` = 58 个提交 |
| 初始脏路径 | 无（`git status` 干净） |
| 合并方式 | `git merge --no-commit --no-ff upstream-silent1566`，由 task_guard `finish` 创建合并提交 |
| 回滚锚点 | `e72239063b4122c5cfc3658231fed2b06798848a` |

## 上游 commit ledger（58 个提交，全部纳入）

上游全部 58 个提交属于**同一个功能波次**：设置页「缓存管理」升级（P0 清单统计 → P1 分级清理 → P2 模块上限/总上限 → P3 自动清理调度 → P4 运行中治理与清理日志）。无一条与本地既有实现重复、冲突或被本地后续提交取代，因此**全部纳入**。

| 阶段 | 完整 commit ID | 内容 | 处置 |
| --- | --- | --- | --- |
| 设计 | `987f19e90e7524855d4c0dcbb25a16dacdc5386a` | `docs: add cache management upgrade design` | 纳入（`docs/CACHE-MGMT-01-cache-management-design.md`） |
| P0 | `77b5d104212e617945de405844c66de96e31499a` | `feat(cache): add P0 cache inventory and management overview` | 纳入 |
| P1 | `d6393243d6a8a034b601c6f361791916222ec8ae` | `feat(cache): add P1 tiered module cleanup` | 纳入 |
| P2 | `eeca921eb6ba78e02b7d7ae24a6620ad78acc471` | `feat(cache): add P2 module limits and retention policy` | 纳入 |
| P2 | `0392c5e09501d630d0417d27228766ac58129c5a` | `feat(cache): add P2 total cache soft limit` | 纳入 |
| P2 | `0790ca363aa8db36a95544c0e29d02c9a93e3bd3` | `fix(cache): enforce temp file limit and hide unimplemented plugin limit` | 纳入 |
| P2 | `d404d93aaeee59fd4f0cfe8e31892eaf0a6079e7` | `feat(cache): add safe plugin cache retention` | 纳入 |
| P3 | `b3e89479ae9fc9a8e699d0c6a6bdbff6638bfddf` | `feat(cache): add P3 automatic cleanup scheduler` | 纳入 |
| P3 | `214eae7ba171faf40fbbc7af9a5e80ae2ede0958` | `fix(cache): persist auto cleanup across reboot and guard symlinks` | 纳入 |
| P4 | `b4051671634b9c7eb011b1a2f81bfc56b3d46285` | `feat(cache): add P4 runtime governance and cleanup journal` | 纳入 |
| 修复 | `41fb1689a6131ef9c6df60eea2021b4434f40808` | `docs(cache): record mobile and TV acceptance evidence` | 纳入 |
| 修复 | `aa6abd4dd408dc16c5915a1f6a5935c870a2cf30` | `feat(cache): persist inventory scan metrics` | 纳入 |
| 修复 | `b4969267341772ad486f9a59736887c94f08d92f` | `docs(cache): reconcile acceptance checklist with device evidence` | 纳入 |
| 修复 | `9d9a644c163d8b55c01ff3589679ef34d939acd7` | `docs(cache): remove stale head reference` | 纳入 |
| 修复 | `9b827c8195dc418e358521495f0ed3978d89a364` | `docs(cache): record compatibility acceptance evidence` | 纳入 |
| 修复 | `15df276114370545afde922dbb0e779606722b78` | `docs(cache): record persistent job scheduling evidence` | 纳入 |
| 修复 | `202529c5302dc1105ec9f483102b0ecdb03e667d` | `docs(cache): record config preservation evidence` | 纳入 |
| 修复 | `561b65fd0f90123433646907e47628395a8f10c4` | `docs(cache): record playback regression blockers` | 纳入 |
| 修复 | `6e430268e5a61cbb8efb1af2f208a51c5a24bcbc` | `docs(cache): record vod config blocker root cause` | 纳入 |
| 修复 | `b143c13e96ade0094a2370d89447420ed5cedc6f` | `fix(cache): keep total-limit rescan off the main thread` | 纳入 |
| 修复 | `91fdd30412f4be61f1309f33f0d04331413dd660` | `feat(cache): validate module registry root invariants` | 纳入 |
| 修复 | `a2d4e7467e59033e856c49ade4f8ca8dd0d34810` | `docs(cache): record playback hot-path diff audit` | 纳入 |
| 修复 | `44d9afb9ef314b12a6906e2cc0b5894bc82821fc` | `fix(cache): confine retention deletions to the declared root` | 纳入 |
| 修复 | `46b19b91116cdc188072f3c6476d73a9d9205776` | `docs(cache): record recovery anchor and device blocker` | 纳入 |
| 修复 | `43307d50d3642016ef8ac7588518fb52c93d1258` | `fix(cache): make TV cache dialog 90% adaptive and fully scrollable` | 纳入 |
| 修复 | `cfbafad5ecbdfcfbd7fae2328164127c2a3e7c2d` | `fix(cache): make TV cache dialog full-height with clear focus and contrast` | 纳入 |
| 修复 | `c59a13ca5f06e3324b8b9089c81462a4187fbdc0` | `fix(cache): keep deep cleanup confirmation in one dialog` | 纳入 |
| 修复 | `b93e7ac3bb361139b582d388a9be8e82940f4477` | `fix(cache): stop ancestor symlinks from hiding cache roots` | 纳入 |
| 修复 | `306b627fd9db073653294b39dbf3c6148274aa93` | `docs(cache): record 5563 regression verification evidence` | 纳入 |
| 修复 | `7f3f32e58df68d546656b8784d493297355f5779` | `docs(cache): correct acceptance evidence after symlink fix` | 纳入 |
| 修复 | `35e7d678b5d902974e5df359799220e62080a6a2` | `docs(cache): record design-to-implementation deltas` | 纳入 |
| 修复 | `1cb7cffdc0fc6fe2ed5a84ab61a3dba1cb52fff3` | `fix(cache): protect in-flight temp transfers from cleanup` | 纳入 |
| 修复 | `d89c7624f2e32df59855be49510a44a1484f1079` | `docs(cache): record lyrics and subtitle device evidence` | 纳入 |
| 修复 | `0486a05f036c607eeb2ea2ab01dfc125f22c6ce7` | `docs(cache): record EPG retention device evidence` | 纳入 |
| 修复 | `4a83d02f6893653adba87d1b76778770f6a1d765` | `docs(cache): record rollback switch and legacy entry deltas` | 纳入 |
| 修复 | `9c491312ccff128dfae2faeb02a2c11340bd5264` | `feat(cache): add rollback feature switch for cache management` | 纳入 |
| 修复 | `61156ab6299a8487cae500289ed2d07433b6c777` | `docs(cache): record low-space trigger device evidence` | 纳入 |
| 修复 | `aec62ca27b32b5faf59427d8d51cf8b28420fbd6` | `docs(cache): record EPG live refresh verification` | 纳入 |
| 修复 | `53d1149c09aab6dc97e2cbffb0fa514032d7fc3d` | `docs(cache): record karaoke module cache isolation evidence` | 纳入 |
| 修复 | `4089b4b91b2f082a5af956565728398bb4dc8aeb` | `docs(cache): record mobile flavor device verification` | 纳入 |
| 修复 | `c66f7f187410c36c71284bccfc5db377588ce793` | `docs(cache): record system quota enforcement evidence` | 纳入 |
| 修复 | `d762e8ee218f746a947501f1448c49c895781bf8` | `docs(cache): document plugin limit safety decision` | 纳入 |
| 修复 | `5faf3d8b4dcc2df80dd7041ddb377ce83e7a6551` | `docs(cache): clarify update verification scope` | 纳入 |
| 修复 | `98f27f59fc07a0f68f2e541abba07beaf0d710ac` | `docs(cache): finalize recovery anchor status` | 纳入 |
| 修复 | `3ea56bc079502ff973a352f113528338744fa55f` | `docs(cache): record update endpoint network blocker` | 纳入 |
| 修复 | `ae0b26e8eeca8ccc062190a727aed0314b5efd35` | `fix(cache): notify cleanup result instead of asking to confirm it` | 纳入 |
| 修复 | `f7d8327548c2e2841fa32ee9d88b59369aedca82` | `fix(cache): treat BACK as cancel request while cleanup runs` | 纳入 |
| 修复 | `225a3fc37493d511a95b3074711e0c7076fce0bb` | `fix(cache): keep panel open when closing during cleanup` | 纳入 |
| 修复 | `37eb7f4f0d29e7ead58057c2817166a0176ae68d` | `docs(cache): correct update blocker root cause` | 纳入 |
| 修复 | `45734f7b48d773b1eb1622d74fe8468c09864538` | `fix(cache): focus cancel by default in cleanup confirmations` | 纳入 |
| 迁移 | `689c2b5b377a9b0d79709d113d405dcb5dbf39a4` | `feat(cache): migrate cache policy schema` | 纳入 |
| 修复 | `5f2277724cf1f35912c77368abb3d39895871984` | `fix(cache): restore focus to cleanup trigger` | 纳入 |
| 修复 | `5e59ef54d9b9bdd188c309df1082134d565431fc` | `fix(cache): clean legacy paths by a versioned rule table` | 纳入 |
| 合并 | `e092453640a0cb4e79b0255f5df71bb68af3ca21` | `Merge remote-tracking branch 'origin/main' into Silent1566`（第二父即 merge base，无净增量） | 纳入 |
| 修复 | `e24d6db5cddbb2219b28c4014fa9781c5cac489f` | `fix(cache): refresh settings cache value right after cleanup` | 纳入 |
| 修复 | `ff3f8d7045470d7d3a8b8aff7cc19258e63acad6` | `fix(cache): 修复自动清理与临时文件清理的评审问题` | 纳入 |
| 修复 | `d042cd542b8768ec9dbd2582923088e54a7bfeb1` | `fix(cache): 评审修复死代码、测试可移植性与繁体翻译缺失`（上游 tip） | 纳入 |

## 上游净变更构成（72 个路径）

- **新增 50 个**：
  - `app/src/main/java/com/fongmi/android/tv/cache/` 33 个类（`CacheInventory`/`CacheCenter`/`CacheCleanupManager`/`CachePolicyEngine`/`CachePolicyStore`/`CacheRetentionManager`/`CacheScheduler`/`CacheCleanupJobService`/`CacheModuleRegistry`/`CachePathSafety`/`CacheLegacyRules` 等）；
  - `app/src/main/java/com/fongmi/android/tv/ui/dialog/CacheManagementDialog.java`；
  - `app/src/main/res/layout/dialog_cache_management.xml`、`app/src/main/res/drawable/selector_cache_button_focus.xml`；
  - `app/src/test/java/com/fongmi/android/tv/cache/` 13 个测试类；
  - `docs/CACHE-MGMT-01-cache-management-design.md`（1825 行设计 + 验收规范）。
- **修改 22 个**：设置页入口（leanback `SettingActivity`/`activity_setting.xml`、mobile `SettingFragment`/`fragment_setting.xml`）、`AndroidManifest.xml`（JobService + `RECEIVE_BOOT_COMPLETED`）、`App.java`（延迟 30s 启动调度）、`RefreshEvent`（新增 `CACHE`）、三套 `strings.xml`（各 61 条）、以及为 owner 级清理新增接口的 `Updater`/`ApkUrlPush`/`EpgParser`/`KaraokeTrackRepository`/`WebHomeRawAdapter`/`MediaSourceFactory`/`MpvHlsCacheCoordinator`/`OkGlideModule`/`BaseLoader`+三个 Loader。

## 冲突与解决决定

`git merge-tree` 预演与实合并共产生 5 个冲突文件，**全部为"本地已改造 + 上游新增功能"的并集型冲突**，解决原则统一为：**保留本地既有行为/主题语义，同时完整吸收上游新功能**。

| 文件 | 冲突内容 | 解决 |
| --- | --- | --- |
| `app/src/leanback/res/layout/activity_setting.xml` | 缓存行文案：本地 `setting_cache` + `?attr/colorOnSurface` vs 上游 `cache_management_title` + `@color/white` | 取上游新文案 `cache_management_title`（新功能入口名称），**保留本地语义前景色** `?attr/colorOnSurface`（拒绝上游硬编码 `@color/white`，否则破坏本地主题 token 合同与深色/浅色一致性） |
| `app/src/mobile/res/layout/fragment_setting.xml` | 同上 | 同上，前景保留 `?attr/webhtvColorOnWallpaper` |
| `app/src/main/AndroidManifest.xml` | 本地 `.lab.LabRuntimeService`、`.node.NodeService` vs 上游 `.cache.CacheCleanupJobService` | **并集**：保留两个本地服务，追加 `CacheCleanupJobService`（`BIND_JOB_SERVICE` 权限） |
| `app/src/main/java/com/fongmi/android/tv/event/RefreshEvent.java` | 本地枚举含 `UI_SCALE`、`VOD_CORE`、`VOD_RECOMMENDATIONS`、`VOD_PERSONAL`、`VOD_EPISODE_TITLES`、`VOD_RELATED_VIDEOS` vs 上游新增 `CACHE` | **并集**：本地全部枚举保留，追加 `CACHE` |
| `app/src/main/java/com/fongmi/android/tv/App.java` | 本地 `registerContentHandlers()` + `resumeBackgroundServices()` vs 上游 `post(() -> CacheScheduler.get().start(), 30_000L)` + `post(this::startBackgroundServices, 1200)` | **并集**：保留本地内容分发注册与可恢复后台服务（`backgroundServicesStarter`），**追加** `CacheScheduler` 30 秒延迟启动。未采用上游的 `post(this::startBackgroundServices, 1200)`——本地 `resumeBackgroundServices()` 已包含同一 1200ms 延迟，重复调度会双启后台服务 |

### 本地既有改动零丢失核对

`git diff --name-only d187f6ae8b HEAD`（本地净差异 2522 个路径）与 `git diff --name-only d187f6ae8b upstream-silent1566`（上游 72 个路径）的**交集为 17 个路径**，逐个复核后全部为并集保留（上表 5 个冲突文件 + 12 个非冲突自动合并文件：`SettingActivity`/`SettingFragment` 上游把 `setCacheText`/`onCache` 改为分支入口、Loader 系列上游仅新增 `activeKeys()` 方法、`MediaSourceFactory`/`MpvHlsCacheCoordinator`/`Updater`/`ApkUrlPush`/`WebHomeRawAdapter`/`EpgParser`/`KaraokeTrackRepository`/`OkGlideModule` 上游仅新增 owner 清理入口，均为纯新增，不覆盖本地逻辑）。

## 强制最佳实践评审（设计研究门）

上游已自带 `docs/CACHE-MGMT-01-cache-management-design.md`（1825 行），覆盖 §2 业界证据、§22 风险与缓解、§23 回滚策略、§20 验收标准、§19 测试矩阵，并记录逐阶段设备验收。本轮合并的评审聚焦"**是否应原样采用上游设计**"：

| 备选 | 评估 | 结论 |
| --- | --- | --- |
| 不合并 | 无法满足"合并上游代码"目标；且上游增量是本地完全缺失的用户可见能力（可观测缓存统计、分级清理、上限与自动维护），本地只有 `FileUtil.getCacheSize()`/`clearCache()` 全删 | 拒绝 |
| 原样采用上游（含硬编码前景色） | 上游在 leanback 布局用 `@color/white`、在 `selector_cache_button_focus.xml` 用 `#FFFFFFFF`/`#33FFFFFF`。本地已完成语义 token 迁移并带 `scripts/check_ui_tokens.sh --strict` 门禁，原样采用会**引入 1 个新的 UI token 违规**，并在手机版日间表下出现白环不可见 | 拒绝（局部适配） |
| **本地适配后采用（已选）** | 功能与结构 100% 采用上游；仅把两处硬编码前景/焦点色改为语义角色：布局 `?attr/colorOnSurface`（leanback）/`?attr/webhtvColorOnWallpaper`（mobile）；`selector_cache_button_focus.xml` 的 `#FFFFFFFF`→`?attr/colorPrimary`、`#33FFFFFF`→`?attr/colorOnSurface_20`。保留上游"焦点态只画描边、不加填充"的设计意图（避免前景层压暗文字）与 3dp/28dp 几何 | **采用** |

安全/回滚/兼容性评估：

- **安全性**：上游自带 `CacheModuleRegistry.validate()`（模块 ID 唯一、根路径必须落在 cacheDir 内、禁止重复根、禁止树形根嵌套）、`CachePathSafety`（只比较最终路径组件，避免祖先符号链接误判）、`CacheRetentionManager` 要求显式 `allowedRoot` 且跳过符号链接。这些不变量在合并后原样生效（`CacheModuleRegistryTest`/`CachePathSafetyTest`/`CacheRetentionManagerTest` 全部通过）。
- **兼容性/回滚**：全部新配置使用 `cache_mgmt_` 前缀，不修改播放器既有容量 key；提供 `cache_mgmt_enabled` 功能开关（`CachePolicyStore.isManagementEnabled()`），关闭即回到本地原 `FileUtil.getCacheSize()`/`clearCache()` 行为。删除新配置不影响任何旧设置。
- **性能**：扫描在单线程执行器上后台执行并带 3 秒快照缓存；播放热路径为纯新增（不改写既有播放/seek/预载代码）；`App` 侧调度延迟 30 秒，不进入冷启动关键路径。
- **包体/依赖**：仅新增 Java/资源，未引入新依赖（JobScheduler 为平台 API，未引入 WorkManager）；`docs/` 新增一份设计文档。

## 实施记录

- 2026-10-04：固定上游引用 `refs/heads/upstream-silent1566` = `d042cd542b…`（避免 `FETCH_HEAD` 被后续 fetch 覆盖）。执行 `git merge --no-commit --no-ff upstream-silent1566`：72 个路径自动合入，5 个冲突，0 个未解决冲突。逐个解决并按上表做并集保留。
- 2026-10-04：合并后立即发现上游引入的 `selector_cache_button_focus.xml` 触发本地 UI token 门禁（新增 1 个违规）。按强制评审结论改为语义角色，门禁回到"仅 1 个既有违规（`item_following.xml`）"，与合并前完全一致。

## 验证记录

| 验证项 | 命令/方法 | 结果 |
| --- | --- | --- |
| 冲突解决完整性 | `git diff --name-only --diff-filter=U` / `git grep "^<<<<<<< \|^>>>>>>> "` | 0 个未合并路径、0 个残留冲突标记 |
| 上游增量零丢失（行级） | 程序化比对：上游 diff 的每条新增行（长度 ≥6）是否存在于合并结果 | 72 个路径中仅 2 个"缺失"，均为**预期改写**：`RefreshEvent.java` 枚举行因并集扩写而不同（`CACHE` 已在枚举内，第 127 行）；`selector_cache_button_focus.xml` 的 4 行硬编码色为**有意替换**（评审结论） |
| 上游新增文件全部就位 | 按 `git diff --name-status` 的 `A` 列表逐个检查存在性 | 50/50 存在 |
| 上游删除文件未复活 | 按 `D` 列表检查 | 无删除项，0 复活 |
| 上游修改文件全部存在 | 按 `M` 列表检查 | 22/22 存在 |
| 上游新增字符串三语齐全 | 程序化提取缓存包 + 弹窗 + 设置页引用的 73 个 `R.string`，逐语言比对定义 | `values`/`zh-rCN`/`zh-rTW` 缺失均为 0 |
| 新增资源引用可解析 | 程序化解析缓存包 + 弹窗的 `R.drawable`/`R.id`/`R.string` | `R.drawable` 0 未解析；`R.id`（`autoCleanup`/`cancel`/`close`/`refresh`/`totalLimit`）在 `dialog_cache_management.xml` 均声明 |
| 双 flavor Java 编译 | `./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac --no-daemon` | `BUILD SUCCESSFUL`，退出码 0 |
| 缓存包单元测试 | `./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.cache.*" --no-daemon` | `BUILD SUCCESSFUL`；13 个测试类、**64 个用例全通过**（`fail=0 err=0`）：`CacheAutoCleanupPolicyTest` 3、`CacheAutoPlanAndTempFamilyTest` 6、`CacheCleanupJournalTest` 1、`CacheInventoryTest` 7、`CacheLegacyRulesTest` 6、`CacheModuleRegistryTest` 7、`CachePathSafetyTest` 2、`CachePolicyEngineTest` 10、`CachePolicyStoreTest` 3、`CacheRetentionManagerTest` 8、`CacheRootPrefixTest` 4、`CacheTempFilePolicyTest` 3、`CacheTotalLimitPolicyTest` 3 |
| UI token 门禁（合并前基线） | 以 `git archive e72239063b` 独立检出合并前树运行 `bash scripts/check_ui_tokens.sh --strict` | `layouts=382 hex_layouts=1 drawables=552 hex_drawables=0`、`violations=1`（既有 `item_following.xml`）、`contrast failures=0` |
| UI token 门禁（合并后） | 工作树运行 `bash scripts/check_ui_tokens.sh --strict` | `layouts=383 hex_layouts=1 drawables=553 **hex_drawables=0**`、`violations=1`（同为既有 `item_following.xml`）、`contrast failures=0 min=4.28` → **相对基线零新增违规** |
| TV 实机：覆盖安装与启动 | `adb -s emulator-5556 install -r app-leanback-arm64_v8a-debug.apk`；`am start …HomeActivityCurrent` | `Success`；`mResumedActivity` = `HomeActivityCurrent`；无 `FATAL EXCEPTION` |
| TV 实机：设置页缓存行 | 进入 `SettingActivity` 滚动至缓存行 | `cache` 行文案 `缓存管理`，`cacheText` = `203.4 MB / 755.9 MB` |
| TV 实机：缓存管理弹窗渲染 | 确认键打开 | 窗口实测 `[96,54][1824,1026]` = 1728×972 ≈ 90%×90%（1920×1080）；标题/`summary`=`共 203.4 MB / 系统配额 755.9 MB`/`status`=`扫描于 …`；15 个模块（图片与封面 73.5%、未归类缓存 15.3%、诊断日志 6.6%、JS/Python/Jar 4.4%、MPV shader 0.2%、Exo/MPV HLS/歌词/K歌/WebHome 扩展/WebHome HTTP/EPG/临时文件/遗留路径 …）全部渲染 |
| TV 实机：弹窗操作控件可达 | 连续 DPAD_DOWN | `autoCleanup`（自动清理：关）、`retention`（保留：30d）、`totalLimit`（总计：不限）、`cleanupLight`/`cleanupStandard`/`cleanupDeep`、`refresh`（刷新）、`close`（确定）全部可见且 focusable |
| TV 实机：确认框默认焦点 | 点击「轻度清理」 | `确认清理缓存？` / `清理过期节目单、临时文件和遗留缓存？`，**默认焦点在「取消」**（与上游 `focus cancel by default` 修复一致） |
| TV 实机：真实清理（轻度） | 确认后 | 写入 `cache_mgmt_cleanup_history`：`mode=LIGHT, reason=manual, status=COMPLETED, durationMs=54` |
| TV 实机：真实清理（模块级） | 点击「诊断日志」模块「清理」并确认 | 界面 `清理完成\n释放 13.4 MB · 删除 6 个文件`，`summary` 由 `203.4 MB` → `190 MB`；历史记录 `bytesBefore=14067731 → bytesAfter=4072, deletedFiles=6, skippedFiles=2, mode=MODULE, status=COMPLETED` |
| TV 实机：`CACHE` 事件即时刷新 | 关闭弹窗后读取设置行 | `cacheText` 由 `203.4 MB / 755.9 MB` → **`190 MB / 755.9 MB`**，无需离开页面重进 → 上游 `RefreshEvent.cache()` + `@Subscribe onRefreshEvent` 链路在本合并树中真实生效 |
| TV 实机：配置持久化 | 读取 `shared_prefs` | `cache_mgmt_schema_version=1`、`cache_mgmt_retention_days=30`、`cache_mgmt_total_limit_bytes=0`、各模块 `cache_mgmt_limit_*` 均已落盘 |
| TV 实机：既有本地能力未回归 | 启动后首页 | 本地首页按钮（点播/追更/历史/直播/搜索/收藏/推送）、WebHome 站点导航、最近观看列表、`versionText=5.6.0-202610042347` 全部正常；`LabRuntimeService`/`NodeService` 仍在清单中 |
| 移动端 APK 打包 | `./gradlew :app:assembleMobileArm64_v8aDebug --no-daemon` | `BUILD SUCCESSFUL` |
| 手机实机：覆盖安装与启动 | `adb -s emulator-5560 install -r app-mobile-arm64_v8a-debug.apk`；`am start …HomeActivityCurrent` | `Success`；`mResumedActivity` = `HomeActivityCurrent`；`FATAL EXCEPTION` 计数 0 |
| 手机实机：设置页缓存行 | 首页 → 设置 → 滚动至缓存行 | `cache` 行文案 `缓存管理`，`cacheText` = `54 MB / 783.1 MB` |
| 手机实机：缓存管理弹窗渲染 | 点击缓存行 | 标题 `缓存管理`、`summary`=`共 53.3 MB / 系统配额 783.1 MB`、`status`=`扫描于 …`；模块列表正常渲染 |
| 手机实机：弹窗操作控件可达 | 连续上滑至底部 | `autoCleanup`（自动清理：关）、`retention`（保留：30d）、`totalLimit`（总计：不限）、`cleanupLight`/`cleanupStandard`/`cleanupDeep`、`refresh`（刷新）、`close`（确定）全部可见 |
| 手机实机：确认框默认焦点 | 点击「轻度清理」 | `确认清理缓存？` / `清理过期节目单、临时文件和遗留缓存？`，默认焦点在「取消」 |
| 手机实机：真实清理（轻度 + 模块级） | 确认后 + 点击「诊断日志」模块「清理」并确认 | 界面 `清理完成\n释放 12.5 MB · 删除 5 个文件`，`summary` 由 `53.3 MB` → `40.8 MB`；历史记录 `bytesBefore=13124209 → bytesAfter=4064, deletedFiles=5, skippedFiles=2, mode=MODULE, status=COMPLETED` |
| 手机实机：`CACHE` 事件即时刷新 | 关闭弹窗后读取设置行 | `cacheText` 由 `54 MB / 783.1 MB` → **`40.8 MB / 783.1 MB`**，无需离开页面重进 |
| 空白校验 | `git diff --check` / `git diff --cached --check` | 均退出码 0 |
| 变更路径均在声明范围内 | `git status --porcelain` 过滤非 `app/`/`docs/` | 空（73 个路径全部在 scope 内） |

### 说明与边界

- **实机设备**：`emulator-5556`（SM-N9700 / Android 9 / 1920×1080），签名与本机 debug keystore 一致（SHA-256 `95e4b2e7…`），按项目约定**覆盖安装、未卸载任何现有包**。
- **未验证项（记录，不阻塞本次合并）**：上游设计文档自述的三项环境/设计边界与本地无关——①应用更新完整闭环受 `ApkUrlPolicy` 私网/HTTP 限制；②插件模块上限仅持久化配置、自动淘汰按安全设计暂不启用；③冷启动首次扫描可能被每模块 2 秒预算截断。这三项均为上游既有已知边界，非本次合并引入，且上游已在其设计文档中显式记录。
- **`item_following.xml` 既有违规**：合并前后均为 `violations=1`，路径与本次上游 72 个路径**零交集**，属本地既有问题，按 AGENTS.md 范围规则仅记录、不扩大修复面。
- 未执行完整 Android Instrumentation 测试与低空间/系统配额极限场景；本次合并为"上游功能引入 + 本地适配"，风险驱动的决定性验证已覆盖编译、单测、门禁与 TV 主路径实机。

## 回滚

- 任务前回滚锚点：`e72239063b4122c5cfc3658231fed2b06798848a`。
- 本次为单个 merge commit；回滚方式为 `git revert -m 1 <merge-commit>` 或重置到锚点。因全部新增配置使用 `cache_mgmt_` 前缀、且提供 `cache_mgmt_enabled` 开关，即使保留代码也可通过关闭开关恢复本地原行为。
- 上游引用 `refs/heads/upstream-silent1566` 为本任务新建的本地引用，不推送；如需清理可 `git update-ref -d refs/heads/upstream-silent1566`。

## 当前状态与下一步

- 状态：合并 + 双 flavor 编译 + 64 项缓存单测 + UI token 门禁 + TV 实机主路径全部通过；净新增违规 0；上游 72 个路径全部落地。
- 下一动作：`bash .codex/scripts/task_guard.sh finish --verified "<evidence>" --commit-message "merge: 合并上游 webhtv/webhtv Silent1566 缓存管理（P0-P4）并适配本地主题语义 token"`，随后由脚本自动创建本地 annotated recovery tag。不推送。
