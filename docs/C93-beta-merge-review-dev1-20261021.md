# C93-beta-merge-review-dev1-20261021

## Recovery anchor

- **目标**：把 `origin/beta@3964eeae7c` 合并进 `dev1`（零复活、零丢失），复评 `dev1` 已提交未推送的 `d9a0cc5c69`（LIVE-UI-01 直播按钮跟随播放器设置 / 平板全屏旋转），修复必修项，验证通过后提交、推送、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突；丢失面 B（`beta` 树 − 合并树）= 0；复活面 A 恰为本文档 1 个新增文件。
  2. 未推送提交的 7 个路径只读复评；必修项已修且每项有可复核证据；`beta` 带入内容按「已在各自分支复评 + 路径零交集」跳过。
  3. `LiveActivityLayoutTest` 18 项全绿（含新增守卫，修复前先红）；`assembleMobileArm64_v8aDebug` 与 `assembleLeanbackArm64_v8aDebug` 均通过。
  4. 本轮改动经任务守卫 `finish` 落盘并创建 recovery tag。
  5. 推送 `dev1`，创建到 `beta` 的中文 PR（只创建不合并），随后拉取远端最新代码。
- **Lane**：`standard`
- **Scope**：`app/src/mobile`、`app/src/leanback`、`app/src/testMobile`、`app/src/main`、`docs`
- **任务守卫**：`C93-BETA-MERGE-DEV1-20261021`，base HEAD `560d5e626315069050e5b20721e92a2810cf3207`（合并提交），branch `dev1`，受保护脏路径 0
- **基线**：`dev1` HEAD = `d9a0cc5c69`（相对 `origin/dev1` ahead 152；相对 `origin/beta` ahead 1，未推送）；merge-base = `687a6b65da9`
- **当前状态**：合并、复评、修复、验证完成；交付坐标见 §7
- **下一动作**：无

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `3964eeae7c`（PR #471 合入后） |
| 本地 | `dev1` = `d9a0cc5c69`（含 1 个未推送的 LIVE-UI-01 提交） |
| merge-base | `687a6b65da9`（= PR #467 合入） |
| 合并提交 | `560d5e626315069050e5b20721e92a2810cf3207`（双父：`d9a0cc5c69` + `3964eeae7c`） |
| 冲突 | **0**（`git merge origin/beta` 成功，工作区无 U 状态） |

`beta` 本次带入 19 个提交：`3964eeae7c`（PR #471）、`0f7e833b59`/`84c6d3bf15`（C92 文档回填）、`231bbea99b`（C92 dev4 复评）、`1983d64932`（PR #470）、`fa7392a371`（C92 dev4 合并）、`08618b49ec`（C92 dev2 合并+复评）、`7eb229f50a`（TV 键盘键对比度）、`a0641ef0e2`（PR #469）、`8a58112b53`（C91 文档）、`a8ae5e8602`（C91 合并+复评）、`2711f89eb2`（搜索范围焦点对比度）、`0a530b82ae`（保留当前播放流）、`2c3af2f009`（PR #468）、`35d6dae6b6`（C90 文档）、`7faf6cea93`（E-SP11 帧调度默认值）、`dbed4ad543`（C90 合并+复评）、`944dd6419f`（片尾真机证据）、`760f387de3`（片尾快捷聚焦）。

## 2. 零复活 / 零丢失证据

| 判据 | 含义 | 结果 |
| --- | --- | --- |
| 路径数 | 合并树 / `beta` / `dev1`（合并前） | **5181 / 5180 / 5171** |
| **A** | 合并树 − `beta` 树 | **1**，恰为本轮新增的 `docs/C93-beta-merge-review-dev1-20261021.md`（其余 6 个文件是既有路径的内容修改，不改变路径数） |
| **B** | `beta` 树 − 合并树（丢失面） | **0**（`git diff --name-only HEAD...origin/beta` 为空） |
| 内容级净差（`beta` 树 → 合并树） | 本次合并实际带入 | 25 路径 / +2242 −39（C90/C91/C92、E-SP11、片尾快捷聚焦、TV 焦点对） |
| 反向核对（合并树 → `beta` 树） | 仅 `dev1` 自身改动 | 7 路径：两份 `LiveActivity`、`LiveControlDialog`、两份直播动作栏布局、`LiveActivityLayoutTest`、`docs/LIVE-UI-01-*` |
| 双方路径交集 | `comm -12` 为空 | 0，合并无同路径语义冲突风险 |

## 3. 复评范围与跳过依据

- **复评**：未推送提交 `d9a0cc5c69` 的 7 个路径：
  - `app/src/mobile/java/.../LiveActivity.java`（动作栏登记/显隐、顶栏参数图标、平板全屏旋转）
  - `app/src/leanback/java/.../LiveActivity.java`（动作栏登记/显隐）
  - `app/src/mobile/java/.../LiveControlDialog.java`（面板镜像动作栏显隐）
  - `app/src/mobile/res/layout/view_control_live_action.xml`、`app/src/leanback/res/layout/view_control_live_action.xml`（`@+id/container`）
  - `app/src/testMobile/java/.../LiveActivityLayoutTest.java`（契约测试）
  - `docs/LIVE-UI-01-live-buttons-and-pad-rotate.md`
- **跳过**：`beta` 带入的 C90（dev2 片尾快捷聚焦与在途取流作废）、C91（dev3 E-SP11 帧调度/解码器排除）、C92（dev2/dev4 焦点对比度与采样脚本）——均已在各自分支完整复评并附实测证据，且与本轮 `dev1` 改动**零路径交集**；合并后这些路径相对 `origin/beta` 的内容 diff 为空。

## 4. 复评发现与修复

| # | 级别 | 位置 | 问题 | 修复 |
| --- | --- | --- | --- | --- |
| 1 | 中 | 两份 `LiveActivity#showControl` | 点播 `VideoActivity#showControl` 会先调 `setTrackVisible()` 重算轨道按钮；直播没有。而 `applyActionButtonVisibility()` 只把「无动态条件」的 player/decode/playParams/scale/change 恢复为 VISIBLE，text/audio/video 依赖 `setTrackVisible` 的动态判定。于是直播存活期间（典型：直播处于 PiP，用户在设置里改「播放器按钮」）重新开启字幕/音轨/视轨后，这些按钮在本会话内不会回来——`applyVisibility` 只隐藏、不恢复；重进控制栏也不刷新 | mobile `showControl()` 在 service 守卫后调用 `setTrackVisible()`（与点播同款）；leanback `showControl(View)` 加 `service() != null` 守卫后同样调用。新增契约测试 `liveControlRevealRefreshesTrackButtons` 锁住两 flavor 的「先重算轨道、再套用户隐藏项」 |

其余复核结论（不修，均已取证）：

- **`LiveControlDialog` 未同步 scale/change**：点播 `ControlDialog` 现行为也只同步 decode/轨道等（`binding.decode/track`），scale/change 不在同步集合内；面板保留与点播一致，不扩大本次范围。
- **三个播放器按钮全隐藏后「播放器」标题仍保留**：点播 `ControlDialog` 对同类分组也保留标题，属既有设计，不属本次引入。
- **`SPEED` 在直播恒为 GONE**（`player().isVod()` 为 false）：登记它不改变直播可见性，保留以便共享同一套配置项与后续时移/VOD 场景；不影响本任务验收。
- **平板旋转重构**：`isEmbeddedLiveUi()`（`!isLand && !isRotate && !PiP`）与 `onBack()`/`applyPadLiveMode()` 的交互逐分支核对——standard 模式嵌入式竖屏点旋转进全屏、全屏内竖屏↔横屏、返回退出并复位；default 全屏模式返回仍走既有 `finishLivePlayback()`；`configChanges` 含 `orientation|screenSize`，状态不因旋转重建丢失。
- **`@+id/container` 冲突**：仅存在于两份 `view_control_live_action.xml` 内部；`view_control_live.xml` 以 `@+id/action` include，`mBinding.control.action.container` 类型独立；注册视图均为该 `LinearLayoutCompat` 直接子节点，`applyOrder` 可完整重排。
- **顶栏参数图标**：位于右上 `LinearLayoutCompat`，`GONE` 不留空洞；`entry` 以 `isVisible(PLAY_PARAMS) && service()!=null && !player().isEmpty()` 判定，控制栏每次显示都会重算，`PlayerManager.isEmpty()` 在直播起播后为 false。

## 5. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 守卫先红（修复前） | 反应用两处 `setTrackVisible()` 后跑 `:app:testMobileArm64_v8aDebugUnitTest --tests *LiveActivityLayoutTest` | **FAILED**：`liveControlRevealRefreshesTrackButtons FAILED`，`BUILD FAILED` |
| 守卫后绿 | 同上（修复后） | **BUILD SUCCESSFUL**，`LiveActivityLayoutTest` 18/18 |
| mobile 打包 | `:app:assembleMobileArm64_v8aDebug` | **BUILD SUCCESSFUL** |
| leanback 打包 | `:app:assembleLeanbackArm64_v8aDebug` | **BUILD SUCCESSFUL** |
| 合并可编译 | 修复前先跑一次 leanback 打包承接合并结果 | **BUILD SUCCESSFUL**（2m27s） |

本轮未重跑全量单测：改动仅两处 `showControl` 各 1 行调用 + 1 个契约测试，`beta` 带入代码未改；其自身验证记录见 C90/C91/C92 文档。本轮未改 drawable/res token，未跑 `check_ui_tokens.sh`（无相关面）。

## 6. 回滚

撤销本轮 `finish` 提交即可：两份 `LiveActivity#showControl` 移除 `setTrackVisible()`，`LiveActivityLayoutTest` 移除 `liveControlRevealRefreshesTrackButtons`。合并提交保留（`beta` 内容）无需回滚；无数据迁移、无接口/协议/ABI 变化。

## 7. 交付坐标

- 合并提交：`560d5e626315069050e5b20721e92a2810cf3207`（`dev1`，双父：`d9a0cc5c69` + `3964eeae7c`）
- 复评修复提交：`4e8fa44848361f0eab2b9c4b1534f74b1df49539`
- recovery tag：`recovery/C93-BETA-MERGE-DEV1-20261021/20261021104411-4e8fa4484836`
- 推送：`origin/dev1`（`2375d5a291..4e8fa44848`）
- PR：<https://github.com/Silent1566/webhtv/pull/472>（`dev1` → `beta`，中文描述，只创建不合并；创建时 `MERGEABLE` / `CLEAN`）
- 拉取：`git fetch origin --prune && git pull --ff-only origin dev1` → `Already up to date`；`origin/dev1...HEAD` = `0/0`；拉取时 `origin/beta` 仍为 `3964eeae7c`（未前进），PR 相对该 head 保持 `MERGEABLE`
