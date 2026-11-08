# C92-beta-merge-review-dev4-20261021

## Recovery anchor

- **目标**：把 `origin/beta@a0641ef0e2` 合并进 `dev4`（零复活、零丢失），复评 `dev4` 已提交未推送的 TV 焦点对比度两提交（`2711f89eb2`、`7eb229f50a`），修复必修项，验证通过后提交、推送、创建到 `beta` 的中文 PR（只创建不合并），最后拉取远端最新代码。
- **验收标准**：
  1. 合并 0 冲突；丢失面 B（`beta` 树 − 合并树）= 0。
  2. 两提交的 3 个资源文件 + 1 个守卫测试 + 1 个采样脚本只读复评；必修项已修且每项有可复核证据。`beta` 带入的 C90/C91 内容按「已由 dev2/dev3 复评 + 零路径交集」跳过复评。
  3. 守卫测试 `TvFocusStatePairContrastTest` 全绿；`scripts/check_ui_tokens.sh --strict` PASS；双 flavor 资源链接通过（leanback 由单测任务覆盖、mobile 由 `processMobileArm64_v8aDebugResources` 覆盖）；采样脚本可编译。
  4. 复评修复经任务守卫 `finish` 落盘并创建 recovery tag。
  5. 推送 `dev4`，创建到 `beta` 的 PR（中文描述，只创建不合并），随后拉取远端最新代码。
- **Lane**：`standard`
- **Scope**：`app/src/leanback`、`app/src/main`、`app/src/testLeanback`、`docs`、`scripts`
- **任务守卫**：`C92-BETA-MERGE-DEV4-20261021`，base HEAD `fa7392a371572e85a5e2239630bd3784b1ee89d4`（合并提交），branch `dev4`，受保护脏路径 0
- **基线**：`dev4` HEAD = `7eb229f50aa`（相对 `origin/dev4` ahead 35，未推送）；merge-base = `687a6b65da9`
- **当前状态**：合并、复评、修复、验证完成；交付坐标见 §7
- **下一动作**：无

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 目标 | `origin/beta` = `a0641ef0e2`（PR #468、#469 合入后） |
| 本地 | `dev4` = `7eb229f50aa`（含 2 个未推送的 TV 焦点对比度提交） |
| merge-base | `687a6b65da9`（= PR #467 合入） |
| 合并提交 | `fa7392a371572e85a5e2239630bd3784b1ee89d4`（双父：`7eb229f50aa` + `a0641ef0e22`） |
| 冲突 | **0**（`git merge origin/beta` → `Merge made by the 'ort' strategy`，工作区无 U 状态） |

`beta` 本次带入 9 个提交：`760f387de3`（片尾快捷聚焦）、`944dd6419f`（5557 真机证据）、`dbed4ad543`（C90 合并+复评）、`35d6dae6b6`（C90 文档）、`7faf6cea93`（E-SP11 帧调度默认值 + 解码器排除状态）、`2c3af2f009`（PR #468）、`a8ae5e8602`（C91 合并+复评）、`8a58112b53`（C91 文档）、`a0641ef0e2`（PR #469）。

## 2. 零复活 / 零丢失证据

| 判据 | 含义 | 结果 |
| --- | --- | --- |
| 路径数 | 合并树 / `beta` / `dev4` | **5178 / 5175 / 5173** |
| **A** | 合并树 − `beta` 树 | **3**，恰为 `dev4` 独有文件：`TvFocusStatePairContrastTest.java`、`docs/TV-FOCUS-PAIR-20261021.md`、`scripts/verify_scope_focus_contrast.py` |
| **B** | `beta` 树 − 合并树（丢失面） | **0** |
| 内容级净差（`dev4` 树 → 合并树） | 本次合并实际带入 | 15 路径 / +931 −10（C90/C91/E-SP11/片尾文档与代码） |
| 反向核对 | `beta` 树 → 合并树 | 6 路径，全部为 `dev4` 自身改动（3 个 TV 焦点/键盘资源与文档、守卫测试、采样脚本、`selector_display_option`） |

## 3. 复评范围与跳过依据

- **复评**：`dev4` 未推送的 2 个提交（`2711f89eb2`、`7eb229f50a`），5 个代码/脚本路径：
  - `app/src/leanback/res/drawable/selector_search_scope_item.xml`（焦点填充 `white_90`→`white_20`，新增 focused+selected 合并态）
  - `app/src/main/res/drawable/selector_display_option.xml`（屏显芯片填充改半透明白，新增合并态与焦点环）
  - `app/src/leanback/res/drawable/shape_keyboard_normal.xml`（`black_20`→`black_40`）
  - `app/src/testLeanback/java/.../TvFocusStatePairContrastTest.java`（状态对对比度守卫，745 行）
  - `scripts/verify_scope_focus_contrast.py`（真机像素采样）
- **跳过**：`beta` 带入的 C90（dev2 片尾快捷聚焦）、C91（dev3 E-SP11 帧调度）——均已在各自分支完整复评并附实测证据，且与本轮 `dev4` 改动**零路径交集**；合并后这些路径相对 `origin/beta` 的内容级 diff 为空（见 §2 反向核对）。

## 4. 复评发现与修复

| # | 级别 | 位置 | 问题 | 修复 |
| --- | --- | --- | --- | --- |
| 1 | 中 | `selector_display_option.xml` | `state_pressed` 排在选择器**第一位**。电视端按确认键时 `state_pressed` 与 `state_focused` 同时成立，按下分支会顶掉焦点分支：按住确认键瞬间焦点环消失、填充从 `white_20` 退回更暗的 `white_10`，形成「按下＝失去焦点」的反向反馈；也与同文档「焦点+按下时由 focused 给白字」的描述自相矛盾 | 删除按下分支，分支顺序固定为「focused+selected → focused → selected → default」。按下态沿用默认底：保持 `white_10` 时它与默认完全同底（死分支），调亮到 `white_20`/`white_30` 则会使默认文字 `muted`（`#CCFFFFFF`）在 `0x7C7C7C` 亮画面上只剩 **4.33:1 / 3.38:1**（`white_10` 为 5.58:1），引入新的正文不达标 |
| 2 | 低 | `scripts/verify_scope_focus_contrast.py` | 截图缺失时仅打印 `SKIP` 并最终返回 0：目录写错或没截图时这条「验证」在任何环境下都算通过，等于没有验证 | 空跑返回 2 并打印 `FAIL`；部分截图缺失同样返回 2（覆盖不完整不算通过）；docstring 记录退出码契约 |

其余复核结论（不修，均已取证）：
- `selector_search_scope_item` 的 pressed 分支（`white_90` + 白环）排在最前是**改动前既有**行为：按下时白字变黑字（`display_option_selected`），实测 ≈16.6:1，白环压白底虽不可见但按下由填充本身表达，不属本次引入。
- `selector_display_option` 的 `?attr/tvPlayerRing` 在 `ThemeOverlay.WebHTV.Dialog`（`DisplayDialog` 使用）里未直接声明，但该 overlay 以 Activity 主题为基底解析，`Theme.WebHTV(.TV/.Mobile)` 与 `Theme.WebHTV.Dialog` 均绑定了该属性，运行时可解析；资源链接已由 mobile flavor 任务验证。
- `TvFocusStatePairContrastTest` 的四道门（状态对、恒定浅色前景完整性、浅底+浅环完整性、双 Activity 接线）经只读复核逻辑自洽，`tv.displayOption.pressed` 注册项在删除按下分支后自动解析到默认分支，仍能拦住「未来新增更亮按下底」的回归。

## 5. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 守卫先红（复评前） | `:app:testLeanbackArm64_v8aDebugUnitTest --tests *TvFocusStatePairContrastTest`（按下 `white_30` 中间态） | FAILED：`tv.displayOption.pressed … fill=#4DFFFFFF ratio=3.38/4.48/3.69:1 < 4.5:1`，精确复现「更亮按下底压 muted 文字」 |
| 守卫后绿 | 同上（修复后） | **BUILD SUCCESSFUL**，4/4 |
| token 门禁 | `bash scripts/check_ui_tokens.sh --strict` | `UI_TOKEN_STATUS PASS`（`violations=0`，`pairs=38 failures=0 min=4.28`，allowlisted 190 未变） |
| leanback 资源/测试链 | 单测任务内 `processLeanbackArm64_v8aDebugResources` | 通过（守卫测试运行即依赖） |
| mobile 资源链接 | `:app:processMobileArm64_v8aDebugResources` | **BUILD SUCCESSFUL**（验证 `selector_display_option` 在主源集对 mobile flavor 合法） |
| 采样脚本 | `python -m py_compile scripts/verify_scope_focus_contrast.py` | 通过 |

本轮未重跑全量单测：改动仅限 1 个 drawable 选择器顺序与 1 个独立采样脚本，`beta` 带入代码未改；上一轮（`2711f89eb2`/`7eb229f50a` 提交时）已记录 `testLeanbackArm64_v8aDebugUnitTest` 4437 项、`testMobileArm64_v8aDebugUnitTest` 5220 项，仅既有 Windows `renameTo` 与已记录 flaky 失败。

## 6. 回滚

撤销本轮复评修复提交即可恢复原状：`selector_display_option.xml` 恢复「pressed 分支置顶 + `white_10`」，`verify_scope_focus_contrast.py` 恢复「缺图 SKIP 后返回 0」。无数据迁移、无接口/协议/ABI 变化。

## 7. 交付坐标

- 合并提交：`fa7392a371572e85a5e2239630bd3784b1ee89d4`（`dev4`，双父：`7eb229f50aa` + `a0641ef0e22`）
- 复评修复提交：`231bbea99bab2f11f9644e02459e14a860bcfed2`
- recovery tag：`recovery/C92-BETA-MERGE-DEV4-20261021/20261021081629-231bbea99bab`
- 推送：`origin/dev4`（`4076b78a11a..231bbea99ba`）
- PR：<https://github.com/Silent1566/webhtv/pull/471>（`dev4` → `beta`，中文描述，只创建不合并；创建时为 `MERGEABLE`）
- 拉取：`git fetch origin && git pull --ff-only origin dev4` → `Already up to date`，`origin/dev4...HEAD` = `0/0`
- 拉取时远端新状态：`origin/beta` 已前进到 `1983d649324`（PR #470，dev2 的 C92 抑制分支修复，同样使用 C92 前缀、不同后缀）；本 PR 相对该 head 仍为 `MERGEABLE`，未再二次合并
