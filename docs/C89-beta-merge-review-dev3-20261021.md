# C89-beta-merge-review-dev3-20261021

## Recovery anchor

- **目标**：把 `origin/beta@b89af5e548` 合并进 `dev3`（零复活、零丢失），复评 `dev3` 唯一已提交未推送的提交 `26e77bb728`（控制栏「直播源」按钮显示当前直播源名称），修复必修项，验证通过后提交、推送、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突、合并结果树与 `git merge-tree` 预演逐字节一致；B（`beta` 树 − 合并树）= 0；C（revert 删除路径 ∧ 不在 `beta` ∧ 在合并树）= 0。
  2. `26e77bb728` 的全部行为（两个 flavor 的 `LiveActivity`、mobile `LiveControlDialog`、契约测试）只读复评；必修项已修且每条有可证伪测试或可复核证据。`beta` 本次未带入任何内容变更（树与 merge-base 相同），无可复评的第三方改动。
  3. 双 flavor arm64 Debug Java 编译通过；定向单测全绿；`scripts/check_ui_tokens.sh` PASS。
  4. 合并提交保留双父，经任务守卫 `finish` 落盘并创建 recovery tag。
  5. 推送 `dev3`，创建到 `beta` 的 PR（中文描述，只创建不合并），随后拉取远端最新代码。
- **Lane**：`standard`
- **Scope**：`app/`、`docs/`
- **任务守卫**：`C89-beta-merge-review-dev3-20261021`，base HEAD `26e77bb7280e80c8bdea6fdb6001febbf6b2d6e0`，branch `dev3`，受保护脏路径 0
- **基线**：`dev3` HEAD = `26e77bb728`（相对 `origin/dev3` ahead 1，未推送）；merge-base `cf828bf8a7`
- **当前状态**：全部完成；交付坐标见 §7
- **下一动作**：无

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `b89af5e548930563757c41059562fd5029c3c160`（PR #466 合入后） |
| 本地 | `dev3` = `26e77bb728`（1 个未推送提交） |
| merge-base | `cf828bf8a7` |
| 冲突 | **0**（`git merge --no-ff --no-commit origin/beta` → `Automatic merge went well`；`git diff --name-only --diff-filter=U` 为空） |
| 预演/实合并树 | `97f5d73bc2b58561f3c9637dd227f26b861c8588`（`git merge-tree --write-tree` 预演与合并后 HEAD 树**逐字节相同**） |
| `beta` 树 / merge-base 树 | `7a35dab4e3a7d91fb0676f59786159b817e9e627` / `7a35dab4e3a7d91fb0676f59786159b817e9e627`（**相同**） |

D 结论：`beta` 侧自 merge-base 起**没有任何内容变更**。`b89af5e548` 是 PR #466（合并 `dev3@cf828bf8a7`）的 merge 提交，其树与 `cf828bf8a7` 完全一致，因此本次合并是**纯记录性合并**（双父、不改内容）；这也决定了本次复评对象只有 dev3 未推送的 `26e77bb728`。

## 2. 复评范围与跳过依据

- **复评**：`26e77bb728`，4 个路径：
  - `app/src/leanback/java/.../LiveActivity.java`（`getLive()` 前刷新控制栏「直播源」按钮文案）
  - `app/src/mobile/java/.../LiveActivity.java`（同上 + 同步已打开的控制面板）
  - `app/src/mobile/java/.../LiveControlDialog.java`（面板打开/换源时镜像源名）
  - `app/src/testMobile/java/.../LiveActivityLayoutTest.java`（2 个源码级契约测试）
- **跳过**：`beta` 带入的第三方改动。依据：本次 `origin/beta` 树 = merge-base 树（`7a35dab4e3`），**未带入任何路径变更**，无第三方代码可复评；上一轮 C88 的合并与复评结论（`docs/C88-beta-merge-review-dev3-20261021.md`）仍然成立。
- 上一轮 C88 已复评的 5 个播放器提交未被本次改动触碰（`git diff --stat cf828bf8a7..HEAD` 恰为 `26e77bb728` 的 4 个路径）。

## 3. 零复活 / 零丢失证据

| 判据 | 含义 | 结果 |
| --- | --- | --- |
| 路径数 | merge / beta / merge-base | 5169 / 5169 / 5169 |
| **A** | 合并树 − `beta` 树 | **0** |
| **B** | `beta` 树 − 合并树（丢失面） | **0** |
| revert 集合 | 主题行锚定（`revert`/`回退`/`撤销` 开头，`HEAD`+`origin/beta` 全历史去重） | 27 提交 / 44 条删除路径 |
| **C** | revert 删除 ∧ 不在 `beta` 树 ∧ 在合并树（复活面） | **0** |
| **D** | 检测器自检 | 探针 `app/src/main/assets/themes/index.json` 在 revert 删除集且不在 `beta` 树（有效探针） |
| **E** | 内容级 `git diff`（`beta` 树 → 合并树） | 恰 **4** 路径，全部为 `26e77bb728` 的未推送改动 |
| 树等价 | `beta` 树 vs merge-base 树 | **相同**（`7a35dab4e3`）⇒ 合并无内容可丢、无内容可复 |

## 4. 复评发现与修复

| # | 级别 | 位置 | 问题 | 修复 |
| --- | --- | --- | --- | --- |
| 1 | medium | `mobile/res/layout/view_control_live_action.xml:22`、`leanback/res/layout/view_control_live_action.xml:29` | 按钮文案改为**用户自定义的源名**后没有任何单行/省略/宽度约束。控制栏动作行是 `HorizontalScrollView` + `wrap_content`（测量宽度无上界），因此长源名（或含换行的源名）会被完整摊开：既把 `播放器/解码/参数` 等主操作挤出可视区，含换行时还会让动作行多占一行高度。固定文案时代不存在该风险，属本次改动引入的输入面。 | 两处 `@id/source` 增加 `android:maxLines="1"` + `android:ellipsize="end"` + `android:maxWidth`（mobile 160dp / leanback 240dp）；新增源码级契约测试断言三条约束，防止回退 |
| 2 | low | `mobile/.../LiveActivity.java:514`、`leanback/.../LiveActivity.java:335`、`mobile/.../LiveControlDialog.java:84/187` | 文案被源名取代后，读屏/电视无障碍只剩一个孤立的源名（如「默认」），**丢失「这是切换直播源」的动作语义**；控制面板同步了文案却没同步语义。 | 新增 `live_control_source_named`（默认/zh-rCN/zh-rTW 三语），`setSource` 在有源名时设置 `contentDescription`、无名时清空回落默认文案；`LiveControlDialog` 的 `initView`/`setPlayer` 同步镜像 `contentDescription`；契约测试断言 |

**明确不修（记录为已知边界）**：

- `setSource(Live)` 命名保持：与同类 `setWidth(...)`/`setLive(...)` 的简名风格一致，Javadoc 已说明它是刷新按钮文案而非切换源。
- `live == null` 分支：`LiveConfig.getHome()` 已保证非 null（`home == null ? new Live() : home`），该分支为纯防御，保留不影响行为。
- 未做设备端视觉走查（无可用模拟器）：修复只影响「长/含换行源名」与无障碍标签，`26e77bb728` 提交时的成功路径实测（12 例单测 + 双 flavor 编译）仍有效。

## 5. 可证伪证据（新增/更新的锁）

- `LiveActivityLayoutTest.liveControlSourceChipClampsLongSourceNames`：读取两个 flavor 的 `view_control_live_action.xml`，断言 `@id/source` 同时具备 `maxLines="1"`、`ellipsize="end"` 与各自 `maxWidth`。删掉任一约束即转红。
- `LiveActivityLayoutTest.liveControlSourceButtonShowsTheCurrentLiveSourceName`：除既有「换源必经 `getLive()`/空名回落」断言外，新增 `setContentDescription` + `live_control_source_named` 断言；去掉无障碍语义即转红。
- `LiveActivityLayoutTest.liveControlSheetMirrorsTheCurrentLiveSourceName`：新增面板 `initView`/`setPlayer` 同步 `getContentDescription()` 断言。

## 6. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 双 flavor arm64 Debug Java 编译 | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL in 4m 15s**（JDK 21 --no-daemon） |
| 定向单测 | `:app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.activity.LiveActivityLayoutTest'` | **13 例，0 失败 0 错误**（含新增 `liveControlSourceChipClampsLongSourceNames`；原 12 例 + 1） |
| UI token 门禁 | `bash scripts/check_ui_tokens.sh` | **PASS**：`violations=0`、`hex_layouts=0`、`hex_drawables=0`、`hex_colors=0`，对比度 38 对 0 失败（min=4.28） |
| 行尾/冲突 | `git diff --check`；`git grep -I '^<<<<<<<'` | 空 / 0（`grep` 直接扫 `app/src` 命中的两个 `.so` 为二进制字节误报，非冲突） |
| 修复后二次复评 | 9 个改动路径逐行复核 | **通过，无新增问题**（唯一后续调整是一处注释措辞与读屏语义一致） |

如实记录的边界：未做设备端视觉走查（无模拟器），因此「长源名省略号表现」与「TalkBack 朗读」只有源码级契约锁，没有实机截图/朗读证据；两处修复都不改成功路径的文案/点击行为。

## 7. 交付坐标

- 合并提交：`5351ade5be4c2ca4b59b770311a49cb36759fd3b`（双父 `26e77bb7280e80c8bdea6fdb6001febbf6b2d6e0` + `b89af5e548930563757c41059562fd5029c3c160`）
- Recovery tag：`recovery/C89-beta-merge-review-dev3-20261021/20261021023221-5351ade5be4c`
- 推送：`dev3`（`cf828bf8a7..5351ade5be`）
- PR：**#467** <https://github.com/Silent1566/webhtv/pull/467>（`base=beta` / `head=dev3` / `state=OPEN` / `mergedAt=null`，只创建不合并）
- 拉取远端最新代码：`git pull --ff-only origin dev3` → `Already up to date`；`git fetch origin beta` → `b89af5e548`（= 本次合并目标）
- 口径提示：本仓库有两个 remote，`gh pr create` 不带 `-R Silent1566/webhtv` 时解析到 upstream `fish2018/webhtv`，会报 `No commits between beta and dev3`；创建 PR 必须显式指定 `-R`。
