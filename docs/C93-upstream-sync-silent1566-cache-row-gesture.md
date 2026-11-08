# C93 — 合并上游 `webhtv/webhtv` 分支 `Silent1566` 最新改动（缓存行手势反转：短按全清 / 长按管理面板）

## Recovery anchor

- **目标**：把上游 `webhtv/webhtv:Silent1566@15e8f6af48c97fd3078b472b5d9735e91fab5d74` 相对本地的 3 个新提交（缓存行手势反转 + 提示行移除 + 陈旧注释同步）合并进 `dev2`，上游功能 100% 吸收，本地主题语义 token、设置页分组与焦点链改动零丢失，并通过编译、定向单测与 UI token 门禁。
- **验收标准**
  1. 上游 tip `15e8f6af48` 成为 `dev2` 合并提交的第二父，合并提交为双父 `--no-ff`。
  2. 合并净差（`git diff 3964eeae..合并树`）等于上游净差集合（14 路径），本地既有改动零丢失。
  3. `SettingActivity`/`SettingFragment` 的手势映射与上游一致：短按 `onCacheClear()` → `CacheManagementDialog.cleanEverything()`；长按 `onCacheManage()` → `CacheManagementDialog.show(this)`；`cache_mgmt_enabled=false` 时短按走 `FileUtil.clearCache()`、长按复用短按。
  4. 两套设置布局缓存行标题为 `@string/setting_cache` 且保留本地语义色角色；无手势提示行（上游 `befa6c60` 的最终形态）。
  5. 双 flavor arm64 Debug Java 编译通过。
  6. 定向单测（cache/setting/theme/ui.style 包）零失败。
  7. `scripts/check_ui_tokens.sh --strict` PASS 且相对基线零新增违规。
  8. 任务文档与评估索引行随合并提交原子提交，并生成 annotated recovery tag。
- **Lane**：`upstream`
- **Scope**：`app/`、`docs/`
- **任务守卫**：`C93-upstream-sync-silent1566-cache-row-gesture`，base HEAD `3964eeae7ccf4f10f5a2e3696a2909c7a92b3952`，branch `dev2`，受保护脏路径 `.wh_diag/`（未跟踪，保持不动）
- **当前状态**：**已完成**（合并、验证、提交、recovery tag 全部落地；已随 C94 轮推送）
- **交付坐标**：合并提交 `5262c92e3fa925a5a84bdfcf27665325422bd11d`（双父 `3964eeae7ccf4f10f5a2e3696a2909c7a92b3952` + `15e8f6af48c97fd3078b472b5d9735e91fab5d74`）；recovery tag `recovery/C93-upstream-sync-silent1566-cache-row-gesture/20261021105141-5262c92e3fa9`；回填提交 `docs(C93)`（见 §6）；已随 C94 轮推送 `origin/dev2`（`84c6d3bf15..bf0566640c`，PR #473，只创建不合并）
- **下一动作**：无（本任务已闭环）。如需设备端手势复验，按 §5「未覆盖」执行

---

## 1. 上游 commit ledger

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev2` |
| 任务开始时 HEAD | `3964eeae7ccf4f10f5a2e3696a2909c7a92b3952`（Merge PR #471 from dev4） |
| 上游仓库/分支 | `https://github.com/webhtv/webhtv` / `Silent1566` |
| 上游 tip | `15e8f6af48c97fd3078b472b5d9735e91fab5d74`（2026-10-21 17:49:09 +0800） |
| Merge base | `3f30bebe7ea97dff6ffcdc8e82649e8a8d970e1a`（2026-10-19，已由 C85/C65 等前序车道带入 `dev2`） |
| 上游本地引用 | `refs/heads/upstream-silent1566`（由 `d042cd542b` 快进到 `15e8f6af48`） |
| 上游 tip 相对 04-10 基线 | `git rev-list --count d042cd542b..15e8f6af48` = 15，其中 12 个已被 HEAD 包含 |
| **本次实际增量** | `git rev-list --count HEAD..15e8f6af48` = **3** |

| 完整 commit ID | 日期 | 标题 | 主要路径 | 处置 |
| --- | --- | --- | --- | --- |
| `ba711e721f1ae504dfed49e210e3a37046011757` | 2026-10-21 16:43 | `feat(cache): 短按直接清理、长按打开缓存管理` | 两个设置入口、`CacheManagementDialog`、两套设置布局、三语 strings、缓存包/测试注释、设计文档 | **合并** |
| `befa6c60ed97321c4aad327ea926e8f0fe435b3e` | 2026-10-21 17:03 | `fix(cache): 缓存行去掉两行提示，恢复与相邻行对称的单行布局` | 两套设置布局（净效果：布局结构回到单行，仅保留标题文案改动） | **合并** |
| `15e8f6af48c97fd3078b472b5d9735e91fab5d74` | 2026-10-21 17:49 | `docs(cache): 同步缓存行手势反转后遗留的陈旧注释` | 缓存包 + 测试 + `CacheManagementDialog` 注释 | **合并** |

**范围外提交**：无。已包含在 HEAD 的 12 个提交（同步方向选择器、硬解回软解兜底、缓存临时文件修复、站点注入搜索、移动端缓存布局等）不再重复处置。

## 2. 上游净变更构成

`git diff --stat 3f30bebe7e 15e8f6af48` = **14 files changed, 112 insertions(+), 68 deletions(-)**，全部为 `M`（0 新增 / 0 删除路径），无依赖、无 native/二进制、无 ABI 变更。

- 手势映射：`SettingActivity`（leanback）与 `SettingFragment`（mobile）`onCache`→`onCacheClear`（FULL 全清）、`onCacheLongClick`→`onCacheManage`（开面板）。
- `CacheManagementDialog`：`cleanEverything()` 由「长按快捷入口」升为设置行主操作，journal `reason` 由 `shortcut` 改为 `settings_row`，`shortcutResources()`→`rowResources()`；清理进行中再次短按由静默 `return` 改为提示新增串 `cache_cleanup_busy`。
- 布局：两套设置布局缓存行标题 `cache_management_title`→`setting_cache`（单击不再「进入管理」）；提示行加入后已被 `befa6c60` 移除，净效果为单行。
- strings：三语各新增 1 条 `cache_cleanup_busy`。
- 注释同步：`CacheCleanupMode`/`CacheCleanupManager`/`CachePolicyEngine` + 两条缓存测试。

## 3. 设计研究（最佳实践门）

| 证据类 | 来源与版本 | 等级 | 支持的结论 | 本仓适用性 | 决策影响 |
| --- | --- | --- | --- | --- | --- |
| 上游源码与提交 | `webhtv/webhtv:Silent1566@15e8f6af48` 的 `ba711e72`/`befa6c60`/`15e8f6af`（2026-10-21 读取） | observed | 手势反转的完整实现与三处注释同步 | 直接适用（同一产品谱系） | 采用 |
| 上游设计文档 | `docs/CACHE-MGMT-01-cache-management-design.md` §14.1 / §15.2 / §11.3 与实施记录（同仓库路径，2026-10-21 读取） | observed | 反转明确改写两条原设计决定（§14.1「不直接把第一层点击改成清空全部」、§15.2「不能依赖长按作为唯一入口」）；核对拆分前 `onCache()` 即 `FileUtil.clearCache()`（历史提交 `3192791d22`，无二次确认），并附 5557 实机 journal `mode=FULL/reason=settings_row/deletedFiles=257/COMPLETED` | 直接适用；该实机证据即本仓可用设备同型号 | 采用；偏离列入已知取舍 |
| 平台规范（一手源码） | 本机 Android SDK `sources/android-37.0/android/view/View.java`：`onKeyDown` 确认键分支 `17729-17745` 在 `clickable || longClickable` 时 `checkForLongClick(...)`；`onKeyUp` `17786-17791` 在 `mHasPerformedLongPress` 为真时不再 `performClickInternal()` | observed | 遥控器「确认键长按」与触摸长按等价，且长按后不会再触发短按 → 电视端不会出现「开了面板又顺手全清」 | 直接适用（`mBinding.cache` 既有 `clickable` + `setOnLongClickListener`） | 采用；无需额外守卫 |
| 本地现有实现 | `SettingActivity:119-141,324-354`、`SettingFragment` 同构、`CacheManagementDialog.cleanEverything()`、本地 `WebHtvAlertDialogBuilder` 与焦点环改造 `b8581afc41`、设置页分组 `3041baf71c`/`59ea0fe6c8` | observed | 本地对手势本身没有独立设计，只有主题语义 token、分组标题与焦点链差异 | 冲突面仅在相邻行 | 本地适配仅保留主题/分组，手势语义 100% 取上游 |
| 第三方项目/论文 | 无适用对象（该改动是本产品自身的交互决定，非通用算法/协议） | — | — | 不适用 | 记录为不适用，不引入外部结论 |

### 方案对比与决定

| 方案 | 正确性 | 兼容性/性能 | 维护性 | 结论 |
| --- | --- | --- | --- | --- |
| **不合并** | 保持 dev2「单击开面板、长按全清」 | 无风险 | 与上游该功能永久分叉，后续每次合并都需重解 | 否 |
| **原样采用上游** | 语义正确 | 无风险 | 与上游一致 | 部分（会把本地 `@color/white` 硬编码与丢失语义 token 带回来） |
| **本地适配后采用（选定）** | 语义 100% 取上游 | 无风险 | 与上游一致，仅保留本地主题层 | **是** |

**决定**：功能与交互 100% 采用上游；本地只做两处等价适配——两套布局缓存行标题取上游 `@string/setting_cache` 但保留本地语义色角色 `?attr/webhtvColorOnWallpaper`（阻止 `befa6c60` 之前的 `@color/white` 回归），设置页分组标题/焦点链保持本地版本。

**接受的偏离（如实记录）**：短按即 FULL 全清且无二次确认，可发现性依赖用户记忆（无手势提示行），与本仓 §14.1/§15.2 原决定相悖——这是上游用户明确要求的取舍，回滚通道为 `CachePolicyStore.putManagementEnabled(false)`。

**回滚路径**：`git revert -m 1 <合并提交>` 或重置回 `3964eeae`；本次无 lock/native/数据结构变更，回滚无副作用。

## 4. 冲突与解决

`git merge --no-ff --no-commit upstream-silent1566` 报 4 处冲突（其余 10 路径自动合并）：

| 冲突路径 | 冲突原因 | 解决 |
| --- | --- | --- |
| `app/src/leanback/java/.../SettingActivity.java` | 双方改同一相邻行区：上游重命名手势回调，本地删除了 `size`/`language` 监听并新增 `tmdb`/`ai`/`ad` | 取上游 `onCacheClear`/`onCacheManage` 与注释；**不复活**上游侧的 `setSize`/`setLanguage` 两行（本地已移除对应视图与方法，复活即编译失败） |
| `app/src/leanback/res/layout/activity_setting.xml` | 上游改标题文案、本地改同一行的 `textColor` 与分组结构 | 标题取 `@string/setting_cache`，颜色保留 `?attr/webhtvColorOnWallpaper` |
| `app/src/mobile/res/layout/fragment_setting.xml` | 同上 | 同上（保留 `?attr/webhtvColorOnWallpaper`） |
| `docs/CACHE-MGMT-01-cache-management-design.md` | 双方都在 Recovery anchor 顶部插入条目 | 并集：上游 `2026-10-21` 新条目置顶 + 本地 `C55`/`C54` 两条保留 + 原 09 条目按上游改名为「上一轮修复」，零删除 |

`git diff upstream-silent1566 -- docs/CACHE-MGMT-01-cache-management-design.md` = 仅上述 2 条本地条目 ⇒ 文档零丢失；`git diff --check` 为空；全仓无冲突标记。

## 5. 验证

| 项 | 命令/方式 | 结果 |
| --- | --- | --- |
| 双 flavor 编译 | `bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac` | `BUILD SUCCESSFUL in 3m 18s` |
| 定向单测 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.cache.*' --tests 'com.fongmi.android.tv.setting.*' --tests 'com.fongmi.android.tv.theme.*' --tests 'com.fongmi.android.tv.ui.style.*'` | **494 tests / 0 failures / 0 errors / 1 skipped**（BUILD SUCCESSFUL in 1m 5s） |
| UI token 门禁 | `bash scripts/check_ui_tokens.sh --strict` | `PASS`：`violations=0`、`pairs=38 failures=0 min=4.28` |
| 净差取证 | `git diff HEAD --stat` | 14 路径 `+112 −75`，与上游 14 路径 `+112 −68` 同集合（多出的 7 行删除来自 anchor 并集） |
| 设备 smoke | `127.0.0.1:5557`（SM-N9700，`adb install -r` 覆盖安装 leanback arm64 debug） | 安装 `Success`；`monkey` 启动后 `mResumedActivity=HomeActivity`、进程存活（pid 23288）、crash buffer 与主 buffer 中 `FATAL EXCEPTION`/`ANR in com.silent` 计数 **0** |
| 平台语义取证 | SDK `View.java` 行号见 §3 | 确认遥控器确认键长按与触摸长按同路径，且长按后抑制短按 |

**未覆盖（如实记录）**：本机**未**实测手势本身（短按不弹面板、长按开面板的界面行为）。原因：`SettingActivity` 在 leanback manifest 中未 `exported`，`adb shell am start` 无法直达；`scripts/` 与 `.codex/scripts/` 内无 UI 导航/断言脚本（`grep -l "uiautomator\|input tap"` 为空），需人工遥控器导航，超出本轮有界验证。该手势的上游实机证据来自**同一设备型号**（`127.0.0.1:5557`，`versionText=5.6.0-202610211618`，journal `mode=FULL/reason=settings_row`）以及本轮的平台语义取证 + 编译/门禁；本地差异仅为文案键与颜色角色。

## 6. 交付坐标

- 合并提交：`5262c92e3fa925a5a84bdfcf27665325422bd11d`（双父 `3964eeae7ccf4f10f5a2e3696a2909c7a92b3952` + `15e8f6af48c97fd3078b472b5d9735e91fab5d74`）
- recovery tag：`recovery/C93-upstream-sync-silent1566-cache-row-gesture/20261021105141-5262c92e3fa9`
- 已推送：随 C94 轮 `git push origin dev2`（`84c6d3bf15..bf0566640c`，含本任务 2 个提交 + 后续合并提交），PR #473 <https://github.com/Silent1566/webhtv/pull/473>（只创建不合并）
- 后续：如需设备端手势复验，应在可导出/可导航的构建上执行，并复用设计文档中 5557 的 journal 断言口径
