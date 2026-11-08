# C31：dev4 合并 origin/beta 最新代码复评（2026-10-01）

任务 guard id：`beta-merge-review-20261001`（quick-fix）
分支：`dev4`；guard 基线 HEAD：`d4b2f04b11e4d0ae3eb82395623ce07afbf926a0`
合并对象：`origin/beta` = `8ba23ac1449b250b1c8729e85bf425ad783200f5`（PR#385–#391）
日期：2026-10-01（Asia/Shanghai）

## 1. 任务范围

- 把 `origin/beta` 最新代码合并进 `dev4`（PR#385–#391，25 个新提交）。
- 复评 `dev4` 已提交未推送的改动（`62670b64131` 及 4 个 docs 提交，相对 `origin/dev4` ahead 5，
  相对 `origin/beta` 净差异 4 文件）。
- 注意约束：远端 beta 已移除/回退的提交不得顺带提交回去。

## 2. 「远端已回退内容不得带回」核查

- `git log --format="%h %s" 5cba7b00b07..origin/beta --grep="evert" -i` → **0 命中**：
  本次合并窗口（PR#385–#391）内 beta 没有任何 revert 提交。
- 历史上的回退（如 `0616a992f6b revert(exo)`、`70306f1decf` 等）均早于 merge-base，不在本窗口。
- 合并后净差异（`git diff origin/beta HEAD --name-only`）只有 dev4 侧 4 个文件：
  `PlayerButtonSetting.java`、mobile `VideoActivity.java`、`MultiThreadProxyPlayerUiSourceTest.java`、
  `docs/player-button-wiring.md`，与任何已回退内容无交集。

## 3. dev4 未推送改动复评结论（第 1 轮评审）

相对 `origin/beta` 的 4 文件净差异全部复评通过：

1. **`PlayerButtonSetting.java`**：`HIDDEN_SEEDED` 一次性播种逻辑正确——首次 `getHidden()` 把默认
   不可见项写入偏好后落下播种标记，此后完全以用户选择为准；`reset()` 同步清除标记；无并发
   问题（Android 主线程单线程调用），不影响老用户既有显隐/排序偏好。
2. **mobile `VideoActivity.java`**：两行 `setOnClickListener(guarded(...))` 绑定复用既有
   `onMultiThreadProxy()` / `onCodecCapabilityPanel()` 实现，无重复方法，语义与 leanback 侧对齐。
3. **`MultiThreadProxyPlayerUiSourceTest.java`**：新增回归断言与实现完全一致（已在合并后的树上
   逐条 grep 核实：mobile/leanback/fusion 三处监听器、`addActionButton` 登记、
   `buttons.put(PlayerButtonSetting.MULTI_THREAD_PROXY, ...)`、播种/重置/`putVisible` 契约）。
4. **`docs/player-button-wiring.md`**：为任务自述文档，六层证据（代码/单测/编译/偏好/可见性三态/
   端侧点击）均已归档，与仓库证据惯例一致。

合并冲突：**无**（`git merge origin/beta --no-commit --no-ff` 自动合并成功，
`git diff --name-only 5cba7b00b07 origin/beta` 与 dev4 净差异文件集交集为空）。

## 4. 合并后验证（与 §3 复评同树）

验证在合并后树上执行（`git write-tree` = `f5c67c7144be0af8f2d9eb7601b8f7e7ec43389c`，
与最终合并提交树一致）：

- `:app:testMobileArm64_v8aDebugUnitTest`（proxy 全集 + PlayerControlFocus + VideoActivityLayout +
  TmdbDetailActivityLayout + SettingPlaybackDefaults + theme 全集）：**449 tests，0 failures，0 errors**
  （含 `MultiThreadProxyPlayerUiSourceTest` 5/5）。
- `:app:compileMobileArm64_v8aDebugJavaWithJavac` + `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`：
  **BUILD SUCCESSFUL**。
- `:app:testLeanbackArm64_v8aDebugUnitTest` 全量：4077 tests，11 failed，2 skipped。
  11 个失败逐一归因（8 个测试类）：
  - 10 个与本仓库既有记录（`docs/player-button-wiring.md` §4）的 CRLF 检出口径预存在失败完全一致
    （多行 LF 断言在 `w/crlf` 工作树上必然不匹配）；
  - 第 11 个 `NativeEnhancedPlaybackStyleFocusTest.everyTmdbRowCardCarriesItsOwnVerticalFocusTargets`
    为 beta 新增测试（`5cba7b00b07` 中不存在，由 `fc36522d2e1` 引入）。归因：断言字面量为
    `\n`（blob 实测无 `\r`），本机 `core.autocrlf=true` 检出使 `VideoActivity.java` 为 CRLF
    （`LF match: False / CRLF match: True`，git blob 中同位置内容真实存在），故属同一 CRLF
    检出口径问题。beta 自身 LF 环境 9/9 通过有档可查（`docs/C28-beta-merge-review-dev2-20260930.md`
    验证记录）。该测试文件与 leanback `VideoActivity.java` 相对 `origin/beta` 均零差异。
- 上述 11 个失败均非本任务引入、不阻塞本任务收口（Linux/CI 的 LF 检出不受影响）。

## 5. 第 2 轮评审（收口前复查）

- 合并窗口内无 revert 提交，无回退内容被带回（§2）。
- 合并后 `git diff --cached --check` 通过，无空白错误。
- staged 树与验证树一致（`git write-tree` == 合并提交树 `f5c67c7144`）。
- dev4 侧 4 文件净差异两轮评审均无需要修改的问题。
- 无遗留问题，进入收口。

## 6. 第二轮合并（beta 增量 PR#392，2026-10-02）

首次收口后、PR 创建前，`origin/beta` 又前进了一个合并提交 `45d340418a6`
（PR#392：dev2 的站点批量动作焦点修复）。按「合并 beta 最新代码」的目标要求，
开启第二轮 guard 会话 `beta-merge-review-r2-20261002` 合并该增量：

- **合并结果**：无冲突，3 个文件（`SiteDialog.java`、`SiteDialogActionFocusTest.java`、
  dev2 的 `C31-beta-merge-review-dev2-20261001.md`）。
- **回退内容核查**：增量窗口（`8ba23ac1449..45d340418a6`）无 revert 提交。
- **增量评审**：`SiteDialog.setActionEnabled` 把 `select`/`cancel` 的 enabled 改为只反映
  loading 态（不再叠加 `type > 0` 条件），可点击性交由 `setType()` 控制——保证纯切换模式下
  D-pad 焦点链仍可达批量动作按钮。改动小而聚焦，配套 Robolectric 测试
  `SiteDialogActionFocusTest` 覆盖加载/焦点场景，评审通过、无需修改。
- **文档编号说明**：dev2 与本分支同日各自记录了「C31」合并评审文档（命名各自含分支名，
  与 C28/C29 的 dev1/dev2 并存先例一致），无内容冲突。

## 7. 第二轮合并验证

- `:app:testLeanbackArm64_v8aDebugUnitTest`：`SiteDialogActionFocusTest` **3/3 通过**、
  `MultiThreadProxyPlayerUiSourceTest` **5/5 通过**（合并后树）。

## 8. 收口

- 由 task guard finish 创建合并提交并打恢复标签。
- 推送 `dev4` → `origin/dev4`，创建 PR `dev4` → `beta`（仅创建，不合并）。
