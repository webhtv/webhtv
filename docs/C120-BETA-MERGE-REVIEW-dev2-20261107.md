# C120-BETA-MERGE-REVIEW — dev2 合并 `origin/beta` 并三轮复评「插件全屏排他范围 / 电视插件行 / 面板加载壳」

## Recovery anchor

- **目标**：把 `origin/beta`（tip `f375e7019d`，PR #533，含插件全屏排他范围、电视插件行、面板加载壳与两轮评审修复）合并进 `dev2`，与本分支已提交未推送的 C120 播放缓存上限系列（5 个提交）稳定共存；随后对**合并后的全部待评审代码**做三轮评审并在发现缺陷时修复重验，通过后提交、推送 `dev2`、向 `beta` 提 PR。
- **验收标准**
  1. `origin/beta` 成为合并提交的第二父，合并为 `--no-ff` 双父，0 冲突。
  2. 两块改动互不覆盖：相对 `origin/beta` 的净差异只含 C120 自有路径；相对 parent1 的净差异只含 beta 带入路径；重叠的 2 个 strings 为双向并集，无键丢失。
  3. 三轮评审结束且必修项全部关闭。
  4. 双 flavor Java 编译通过；缓存与插件定向单测零失败。
  5. 任务文档随合并提交原子提交并生成 annotated recovery tag。
- **Lane**：`upstream`
- **Scope**：6 个目录（`app/src/main/java`、`app/src/test/java`、`app/src/main/res`、`app/src/leanback`、`app/src/main/assets`、`docs`）
- **任务守卫**：`C120-BETA-MERGE-REVIEW`，base HEAD `cd3643faabf497f3c180e14c33adf1a74bfe53a9`，branch `dev2`，受保护脏路径 0
- **回滚锚点**：`backup/dev2-before-beta-merge-20261107231230`（= `cd3643faab`）
- **当前状态**：三轮评审通过，验证完成，待提交

---

## 1. 合并事实

| 项 | 值 |
| --- | --- |
| 合并前 HEAD | `cd3643faabf497f3c180e14c33adf1a74bfe53a9`（C120 上游缓存上限系列 tip） |
| 合并目标 | `origin/beta` tip `f375e7019d031662c2eda488f03b68393bddc5cb`（PR #533） |
| 合并提交 | `52cd03beb2`（`--no-ff`，双父 = `cd3643faab` + `f375e7019d`） |
| 冲突 | **0 处** |
| 相对 parent1 净差异 | 23 路径，全部为 beta 带入（插件/布局/strings/测试/docs） |
| 相对 parent2 净差异 | 14 路径，全部为 C120 自有（7 Java + 3 strings + 1 test + 3 docs） |

### 为什么本次可以跳过对既有改动的重复评审

用户规则允许"之前已评审过且无合入冲突时跳过"。本次两块改动各自都已有完整评审记录（`docs/C120-upstream-sync-silent1566-cache-limit.md`、`docs/C120-beta-merge-review-dev4-20261106.md`），且合并 0 冲突。因此三轮评审的**对象是合并后的结果**——保真性是否成立、交叉面是否产生新缺陷——而不是重评两块各自的内部语义。

## 2. 三轮评审

### 第一轮（两轴并行，只读）

| 轴 | 结论 | 关键证据 |
| --- | --- | --- |
| 正确性 / 生命周期 | 必修 **0** | 两个方向 `--diff-filter=D` 均为空；`HEAD^1`/`HEAD^2` 单向 diff 逐路径验证每个文件整块取自某一父，无 evil merge；三语 strings 键集合 HEAD ⊇ 两父 |
| 规格 / 接线完整性 | 必修 **0** | 18 条契约全部兑现（缓存三类容量语义、EXO 可持久化上限、面板上限入口与摘要行、`fullscreen` 排他范围、`scopesOf` 电视端 detail+playback、面板加载壳 token 回填与 `ARG_*` 持久化、`inPip` 快照与 PiP 重算、`playback.fullscreen` 事件口径、5 个新测试引用的符号均存在） |

### 第二轮（对抗性证伪）

任务不是附和第一轮，而是**尝试推翻"0 必修"**。三个预设靶子逐个裁决：

| 靶子 | 裁决 | 决定性证据 |
| --- | --- | --- |
| `CacheScheduler.limitCheck()` 第 111 行门控吃掉 12h 兜底 | **不是必修** | `enforceCapacityNow()` 自身要求 `canReleasePending()`（`activeSessions==0`），有过期会话时本来必然返回 false；即去掉门控也什么都不会做。门控没有吃掉任何本会发生的收敛，属上游文档表述不准确 |
| `refresh(force)` 在 `loading` 时丢弃 → 面板永久停在旧值 | **不是必修** | `render()` 首行即 `loading = false`，不会卡死；且"收至 Y"是 render 时**实时**调 `targetCapacityBytes()` 算出的，不来自快照 |
| zh-rTW 缺 `detail_plugin` | **不是必修** | 该键在 `HEAD^1`、`HEAD^2`、`HEAD` 三个版本**全部缺席**，即 beta 侧既有缺口（zh-rTW 相对 values 共缺 95 键），本次合并未引入也未加剧 |

对抗轮同时发现 **1 条第一轮遗漏的必修项（N1）**。

### 必修项 N1 与修复

| 项 | 内容 |
| --- | --- |
| 缺陷 | `chooseTotalLimit()` 回调只做 `putTotalLimitBytes` + `updatePolicyButtons()`，而后者只重绘三个按钮文案；摘要行里的"退出播放后收至 Y"由 `render()` 现算（`targetCapacityBytes()` → `desiredCapacityBytes(IDLE)` → `getTotalLimitBytes()`），改总上限后该值已变但面板不重绘，用户看到已不成立的承诺值 |
| 归因 | C120 引入（`playbackBudgetLine` 为 A 块新增；`git diff --name-only HEAD^1 HEAD \| grep cache` 为空，证明 B 块未触碰） |
| 修复 | `CacheManagementDialog.java` 的 `chooseTotalLimit()` 回调在 `putTotalLimitBytes` 与 `updatePolicyButtons()` 之后补 `applyPlaybackLimit()`，与 EXO 单项上限路径（`chooseLimit`）复用同一应用路径 |
| 守卫 | 新增 `CacheTotalLimitApplyContractTest`（2 例源码契约测试），钉住"总上限必须走应用路径"与"两条上限入口共用同一路径" |

### 第三轮（复审修复本身）

四条裁决全部通过，必修 **0**：

| 裁决点 | 结论 | 依据 |
| --- | --- | --- |
| 修复是否真解决（`refresh` 在 `loading` 时仍会丢弃） | **通过** | `loading` 置位点唯一在 `refresh`、复位点唯一在 `render` 首行，二者互斥：`loading==true` 意味着"已发扫描、render 未跑"，而那次 render 必在写入之后，读到的是新总上限。不存在"render 已跑完且新值未落盘"的窗口 |
| 改总上限触发 SimpleCache 重建是否语义越界 | **通过** | EXO 空闲容量本就由总上限推导（`desiredCapacityBytes(IDLE)` 传的就是 `getTotalLimitBytes()`），面板"收至 Y"与 `enforceCapacityNow()` 用同一公式；播放中由 `canReleasePending()` 守卫挡住不重建；调高上限时若预加载天花板未变则 `hasPending()` 为假、不重建 |
| 契约测试是否有效 | **通过** | 先剥离注释再断言，故新加注释无法满足 `contains`；变异探针（删掉调用 → 转红）已证 |
| 注释准确性 | **可选改进，已采纳** | 原文 "converges the cache" 省略了播放中不收敛的条件，与同文件 `chooseLimit` 既有注释口径不一致，已改为精确表述 |

## 3. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 双 flavor 编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac` / `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | `BUILD SUCCESSFUL` |
| mobile 定向单测 | `:app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.cache.*" --tests "com.fongmi.android.tv.plugin.*" --tests "com.fongmi.android.tv.ui.dialog.CacheTotalLimitApplyContractTest" --tests "com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest"` | **511 tests / 0 failures / 0 errors**（44 suites） |
| leanback 定向单测 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.plugin.*" --tests "com.fongmi.android.tv.cache.*"` | **391 tests / 0 failures / 0 errors**（44 suites） |
| 变异探针（N1） | 删掉 `chooseTotalLimit` 里的 `applyPlaybackLimit();` | 契约测试转红 `BUILD FAILED`；还原后转绿 |
| 冲突标记扫描 | `git grep -n -E "^(<<<<<<<\|>>>>>>>) "` | 空 |
| 三语重名键 | 按键名分组计数 | values 2565 / zh-rCN 2562 / zh-rTW 2470，**重复键 0** |
| 合并保真性 | 两个方向 `--diff-filter=D` + 键集合包含关系 | 无删除、无键丢失；仅 2 个 strings 与两父都不同，且为双方增量并集 |

**未覆盖（如实记录）**：无设备端实测。面板"播放缓存 X（退出播放后收至 Y）"的实际数值变化、退出播放后 EXO 目录是否真的缩小、MPV idle prune 是否落实到磁盘、PiP 下插件 chip 是否有一次可见闪烁，均需真机证据。本机无连接设备。

## 4. 记录不修（已知边界）

| 项 | 理由 |
| --- | --- |
| `CacheScheduler` 12h 兜底受 `isAutoCleanupEnabled()` 门控（默认 false） | 该兜底在活会话下本就不可能生效（`canReleasePending()` 要求无活跃会话），门控未吃掉任何实际收敛；收敛主路径 `releaseCacheSession()` 无条件生效。属上游文档表述不准确，非本次合并引入 |
| `refresh(force)` 在 `loading` 时丢弃 | 目标值实时计算、`loading` 由 render 复位，不会永久陈旧；最坏是"当前 X"晚一拍 |
| `values-zh-rTW` 缺 `detail_plugin` 及其余 94 键 | beta 侧既有系统性 i18n 滞后，本次合并未引入未加剧；补单键会与其余 94 键不一致，属独立任务 |
| `CachePolicyStore.SCHEMA_VERSION` 仍为 1 | `getLimit` 已做 `max(0, …)` + 默认 UNLIMITED，无行为缺陷；迁移语义未与"EXO 加入集合"同步，属清理项 |
| `targetCapacityBytes()` 为 `synchronized`，UI 线程可能与其争锁 | 本次修复为"改总上限"新增了一个触发入口，但该窗口在 EXO 单项上限路径早已存在；根除需改为读 `CACHE_CAPACITY_STATE` 快照 |
| 契约测试为源码形状断言 | `if (false)` 类死分支可绕过，属该类测试固有边界；已用变异探针验证其有效性 |

## 5. 交付坐标

- 合并提交：`52cd03beb2`
- 修复提交与 recovery tag：由 `task_guard.sh finish` 生成后回填
- 回滚锚点：`backup/dev2-before-beta-merge-20261107231230`（= `cd3643faab`）
