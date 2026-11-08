# C95-beta-merge-review-dev2-20261021

## Recovery anchor

- **目标**：把 `origin/beta@590c424568`（PR #473 合并提交）合并进 `dev2`，复评 `dev2` 唯一已提交未推送的 `ae0c82e7b6`（全屏菜单键新增「下一集」动作，7 路径），修复必修项并验证，通过后再次评审直至通过，然后提交、推送 `dev2`、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突；丢失面 B（`beta` 树 − 合并树）= 0；复活面 A 恰为 `dev2` 自有的本轮 7 路径（6 改 + 1 新测试）。
  2. 复评覆盖 `ae0c82e7b6` 全部 7 路径（`Setting`、leanback `VideoActivity`、`TmdbDetailActivity`、三语 `strings.xml`、`FullscreenMenuKeyTest`），逐路径给出可复核结论。
  3. 必修项为 0 时明确记录理由；若有必修项必须修复并留下验证证据，修复后第二轮评审确认通过。
  4. 合并结果编译通过、`FullscreenMenuKeyTest` 全绿，`git diff --check` 与冲突标记扫描为空。
  5. 本轮改动经任务守卫 `finish` 落盘并创建 recovery tag；推送 `dev2`；创建到 `beta` 的中文 PR（含具体改动内容）；随后拉取远端最新代码。
- **Lane**：`upstream`
- **Scope**：`app/`、`docs/`
- **任务守卫**：`C95-beta-merge-dev2-20261021`，base HEAD `ae0c82e7b66fbd452803525f1f0f85dd68c3e740`，branch `dev2`，受保护脏路径 `127` 条（全部在 `.wh_diag/` 下，未跟踪，保持不动）
- **当前状态**：已完成；合并提交 `573c8e5114`、recovery tag 已生成、`dev2` 已推送、PR #474 已创建、远端最新已拉取
- **下一动作**：无（等待 PR #474 人工评审/合并；本任务不再改动代码）

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `590c424568d5d1eaa713fe8a06c6c57cd013f42c`（`Merge pull request #473 from Silent1566/dev2`，2026-10-21 `git fetch --prune` 取得） |
| 本地 | `dev2` = `ae0c82e7b66fbd452803525f1f0f85dd68c3e740`（1 个自有未推送提交：全屏菜单键新增「下一集」动作；recovery tag `recovery/fullscreen-menu-next-episode/20261021153432-ae0c82e7b66f` 已存在） |
| merge-base | `9c7b3ce1fbe5839d977aef3adf46eb91ca1ec92b`（上一轮 dev2 交付的 C94 文档回填提交） |
| 合并命令 | `git merge --no-ff --no-commit origin/beta` |
| 冲突 | **0**（`Automatic merge went well; stopped before committing as requested`，工作区无 `U` 状态） |
| 净内容 | **0 文件差异**：`beta` tip 树 `7cffd64dc5d0f922f09f2c5b3fdfa3446e5629a9` 与 merge-base 树**逐字节相同**（同一 SHA-256），本次合并提交只记录双父拓扑 |

`beta` 本次带入 1 个提交：

| 完整 commit ID | 标题 | 处置 |
| --- | --- | --- |
| `590c424568d5d1eaa713fe8a06c6c57cd013f42c` | Merge pull request #473 from Silent1566/dev2 | 合并（PR #473 合并提交；其树与上一轮 dev2 tip `9c7b3ce1fb` 相同，无新增/改写内容） |

## 2. 零复活 / 零丢失证据

| 判据 | 含义 | 结果 |
| --- | --- | --- |
| 路径数 | 合并树 / `beta` / `dev2`（合并前） | **5185 / 5184 / 5185** |
| **B** | `beta` 树 − 合并树（丢失面） | **0**（`comm -23` 为空） |
| **A** | 合并树 − `beta` 树（复活/新增面） | **7** = `dev2` 自有 7 路径（6 个既有文件修改 + 1 个新测试文件），无 `beta` 侧文件被复活 |
| 内容级净差（`beta` → 合并树） | 仅 dev2 自身改动 | **7 路径 / +136 −3**（`Setting.java`、leanback `VideoActivity`、`TmdbDetailActivity`、三语 `strings.xml`、`FullscreenMenuKeyTest`） |
| 双方路径交集 | `beta` 本轮带入路径 ∩ dev2 自有路径 | **0**（`beta` 本轮零文件差异，不存在交集） |
| 树等价 | `beta` tip 树 / merge-base 树 | 均为 `7cffd64d…` ⇒ 合并不会带回旧内容，也不会覆盖 dev2 改动 |

## 3. 复评范围与逐路径结论

复评对象：`ae0c82e7b6` 的 7 路径（上一轮 `fullscreen-menu-next-episode` 为 `quick-fix` 车道，守卫记录只有编译/单测证据，**没有评审记录或评审文档**，因此本轮必须完整复评；合并 0 冲突、0 内容差异，复评对象与合并前完全一致）。

| # | 路径 | 改动 | 复评结论 |
| --- | --- | --- | --- |
| 1 | `app/src/main/java/com/fongmi/android/tv/setting/Setting.java` | 新增 `FULLSCREEN_MENU_KEY_CONTROL/EPISODES/NEXT_EPISODE` = 0/1/2；`getFullscreenMenuKey()` 增加 `[0,2]` 钳制 | **通过**。0/1 既有语义未变（仅改用命名常量）；新增值**只能追加**（注释与实测一致）；钳制保证 `SettingPersonalActivity` 用该值做 `string-array` 下标时不会越界（`SettingPersonalActivity:104/184` 为唯一数组下标使用点）；全仓无其他 `getFullscreenMenuKey()==1` 之类的取值耦合（`grep -rn FullscreenMenuKey` 仅剩 leanback `VideoActivity` 与 `TmdbDetailActivity`） |
| 2 | `app/src/leanback/.../VideoActivity.java` | 新增 `onMenuNextEpisode()`；`dispatchKeyEvent` 菜单键分支改用常量并派发新动作 | **通过**。①`checkNext()`/`onPrev()` 是底栏「下一集」按钮（`VideoActivity:1631/1683`）与片尾自动续播（`NavigationCallback.onNext`）的同一条链路，倒序播放由 `checkNext()` 内部改走 `onPrev`，语义一致；②`flag == null || flag.getEpisodes().isEmpty()` 守卫是**必要**的：`getFlag()` 在无详情/快播时会 `new Flag()`（空分集），缺少守卫会让 `onNext()` 拿到空 `Episode` 并走 `onItemClick`（`onEpisodeKey` 之外的一条误切路径）；③`KeyUtil.isMenuKey()` 要求 `ACTION_UP`，一次按键只触发一次 ⇒ 不会连跳两集；④菜单键分支保持既有「不 return、继续下沉」结构，未新增对 `super.dispatchKeyEvent` 的二次处理（MPV 光盘菜单场景在 `dispatchDiscMenuKey` 首行已拦截，UP 亦被消费，不会与切集叠加） |
| 3 | `app/src/main/java/.../TmdbDetailActivity.java` | 内嵌播放器菜单键分支改用常量派发 `checkInlineNext()` | **通过**。`checkInlineNext()`→`advanceInlineEpisode()` 对 `history == null` 空安全，倒序改走 `onInlinePrev`，末集/首集由 `playAdjacentEpisode()` 返回 false 后提示 `error_play_next`/`error_play_prev`；与既有「控制栏可见时菜单键先收起控制栏」的顺序保持一致，未改变 0/1 两项交互 |
| 4 | `app/src/main/res/values/strings.xml` | `select_fullscreen_menu_key` 追加 `<item>Next episode</item>` | **通过**。数组 3 项且新项在末尾（存量下标 0/1 语义不变） |
| 5 | `app/src/main/res/values-zh-rCN/strings.xml` | 追加 `<item>下一集</item>` | **通过**。3 项、位置末尾、与 en 顺序一致 |
| 6 | `app/src/main/res/values-zh-rTW/strings.xml` | 追加 `<item>下一集</item>` | **通过**。3 项、位置末尾；仓库仅 `values`/`values-night`/`values-zh-rCN`/`values-zh-rTW` 四套资源，该数组只存在于上述三套，无漏配 locale |
| 7 | `app/src/test/java/.../setting/FullscreenMenuKeyTest.java` | 新增 4 例源契约回归 | **通过**。覆盖三语数组同长/新项在末尾、常量与钳制表达式、leanback 派发与 `checkNext()` 复用（并断言不得出现 `onNext(` 直调）、内嵌播放器派发；`root()` 同时兼容 Gradle（`app/` 工作目录）与 IDE（仓库根）运行；读取源文件做契约与 `LiveActivityLayoutTest`/缓存类契约测试同属既有模式，不引入运行期依赖 |

## 4. 复评发现

**必修项：0**。修复后验证与第二轮评审因此无需进入修复循环。以下 3 条为已取证但不修的观察项：

| # | 级别 | 观察 | 处置与理由 |
| --- | --- | --- | --- |
| 1 | 低（既有边界） | 新版本把 `fullscreen_menu_key` 存为 `2` 后，若用户降级回只认 2 项数组的旧版本，旧版 `SettingPersonalActivity.setText()` 会以越界下标取 `string-array` 而崩溃；本轮钳制只保护「新版本读到未来值」，无法约束旧版本代码 | **不修**。降级不受支持，且旧版本代码无法在本次改动内修复；记录为已知边界 |
| 2 | 低（交互差异） | 内嵌播放器在控制栏可见时，菜单键先收起控制栏、下一次才切集；leanback 全屏播放器则立即切集 | **不修**。该优先级是 0/1 两项的既有交互，改动会波及既有行为，超出本次「新增一个可选动作」的范围 |
| 3 | 低（设计选择） | 末集/单集时按菜单键会提示「没有下一集」（`error_play_next`），与底栏「下一集」按钮一致；无详情/空分集时静默忽略 | **不修**。与既有按钮反馈一致，且静默分支避免把空 `Flag` 当作真实剧集 |

## 5. 验证

| 项 | 命令/方式 | 结果 |
| --- | --- | --- |
| 合并结果编译 | `bash ./gradlew :app:assembleLeanbackArm64_v8aDebug` | **BUILD SUCCESSFUL in 1m 56s**（142 actionable tasks；含 `src/main` 共享代码与 leanback 源集） |
| 定向单测（与编译同一次执行） | `bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.setting.FullscreenMenuKeyTest'` | **4 例 / 0 失败 / 0 错误 / 0 跳过**（`app/build/test-results/testLeanbackArm64_v8aDebugUnitTest/TEST-com.fongmi.android.tv.setting.FullscreenMenuKeyTest.xml`：`tests="4" skipped="0" failures="0" errors="0"`） |
| 空白/冲突标记 | `git diff --check`、`git diff origin/beta --check`、`app/`+`docs/` 冲突标记扫描 | 全空；无 `<<<<<<<`/`>>>>>>>` |
| UI token 门禁 | 未执行 | 本轮 0 布局/颜色/样式文件改动（仅 Java + 字符串数组 + 测试 + 文档），该门禁不覆盖这些文件类型 |
| 合并语义 | 见 §2 树等价、路径数、A/B 面 | 0 冲突、0 丢失、A 面 = dev2 自有 7 路径 |

## 6. 第二轮评审（验证后）

- 复评对象在验证前后**完全一致**：`git diff origin/beta --stat` 仍为 7 路径 `+136 −3`，无验证期意外的代码改动，合并仍处 `MERGE_HEAD=590c424568` 未提交状态。
- 重新逐条对照 §3 的 7 路径结论：常量与钳制、leanback 派发链路、内嵌派发链路、三语数组、契约测试均与第二轮检查一致，无新增缺陷暴露。
- **结论：通过**。无需修复；交付条件（§5 验证全绿 + 必修项 0）已满足。

## 7. 交付坐标

- 合并/复评提交：`573c8e5114d988c2285416785d2ea651f04916df`（双父 `ae0c82e7b66fbd452803525f1f0f85dd68c3e740` + `590c424568d5d1eaa713fe8a06c6c57cd013f42c`），树 `ace0f9afc626ff5dd067df4255f13c06f6c15795`；相对 `origin/beta` 净差 = 9 路径 `+231 −3`（7 个功能路径 `+136 −3` + 本评审文档 + 评估索引 1 行）。
- recovery tag：`recovery/C95-beta-merge-dev2-20261021/20261021160321-573c8e5114d9`
- 推送 `dev2`：`9c7b3ce1fb..573c8e5114`（含此前未推送的 `ae0c82e7b6` 与本轮合并提交），推送后 `origin/dev2 = 573c8e5114`、`git pull --ff-only origin dev2` 为 `Already up to date`。
- PR：**#474** <https://github.com/Silent1566/webhtv/pull/474>（`base=beta`/`head=dev2`/`OPEN`/`mergedAt=null`/`MERGEABLE`+`CLEAN`，只创建不合并）。
- 拉取远端最新：`git fetch origin --prune` 后 `origin/beta` 仍为 `590c424568d5d1eaa713fe8a06c6c57cd013f42c`（PR #474 未合并，符合预期）；本次回填提交推送后再执行一次 `git pull --ff-only origin dev2`，保持本地与远端一致。
