# C69 · dev2 合并 beta（PR#441–#444）与 TV 弹层 chip 文字对比度改动复评

- 任务 ID：`C69-beta-merge-review-dev2-20261014`
- 分支：`dev2`；合并前 dev2 tip = `2137fffc5a`（本任务复评对象，已提交未推送）
- 合并对象：`origin/beta` = `a8c0d6a5fe`（Merge pull request #444 from Silent1566/dev3）
- 合并基点：`4913d6ff23`（C65 的文档提交，见 `docs/C65-beta-merge-review-dev2-20261012.md`）

## 1. 合并范围

`git merge --no-commit --no-ff origin/beta`，**0 冲突**，MERGE_HEAD 即 beta tip `a8c0d6a5fe`。

本轮与 C65 的结构性差别：**beta 侧有真实增量**。C65 时 beta 相对合并基点未新增任何内容（合并内容中性）；本轮 beta 自 `4913d6ff23` 起新增 **22 个提交、33 个路径**（+2324 / −77 行），来自三个并行分支：

| 来源 | 内容 |
| --- | --- |
| PR #441 | dev2 自己上一轮（C65）的交付合并 |
| PR #442 / #444 | dev3：电视版实验室遥控焦点（含 1 个 revert） |
| PR #443 | dev1：WorkManager 直启修复 |
| `3c230bac1e` 等 | 硬解自动软解回落（含 Exo 解码合同修订） |

dev2 自身相对合并基点的改动只有 **2 个路径**（`2137fffc5a` 的 chip 选择器与其测试）。

| 判据 | 命令 | 结果 |
| --- | --- | --- |
| 冲突 | `git merge --no-commit --no-ff origin/beta` | 0 |
| 合并后索引树 | `git write-tree` | `8bcdd91fbd8d95b383a15dd084917ede1c213cdf` |
| 文件数 | `git ls-tree -r origin/beta` vs 合并树 | 5063 = 5063 |

## 2. 零复活 / 零丢失（四层证据）

用户明确要求：远端已移除/回退的提交不得顺带提交上去。

| 层 | 判据 | 结果 |
| --- | --- | --- |
| 1 · 净差异 | `git diff --cached --name-status origin/beta` | 恰为 dev2 自身 2 路径（chip 选择器 + 其测试） |
| 2 · beta 删除面 | `git diff --diff-filter=D --name-status 4913d6ff23 origin/beta` | **空** ⇒ beta 未删除任何路径 |
| 3 · 复活形态 | `git diff --cached --diff-filter=A --name-status origin/beta` | **空** ⇒ 不存在「把已删文件加回来」 |
| 4 · 逐行交集 | beta 侧 revert 删除行 ∩ dev2 新增行 | 5 行，全为样板（空行、`/**`、`*`、`*/`、`}`） |

第 1 层之上还有一条更强的判据——**补丁摘要相等**：

```
git diff --cached --binary origin/beta  | git hash-object --stdin  => d6802118fac46812e23a881ef07cd57342a24d6b
git diff --binary 4913d6ff23 2137fffc5a | git hash-object --stdin  => d6802118fac46812e23a881ef07cd57342a24d6b
```

两者哈希相同，即**合并结果 ≡ beta tip + dev2 自身提交**，逐字节成立；除 dev2 自己的 2 个路径外，合并没有引入、删除或改写任何一个字节。第 2 层进一步说明结构上不可能复活：beta 相对合并基点一个路径都没删。

本轮 beta 侧确实包含 1 个 revert 提交（`b2bb4a249b revert(lab): 弹窗按钮保留几何就近上行焦点（撤回钉死到根目录的连线）`，触及 `LabActivity.java` / `LabTvFocusSourceTest.java` / `docs/LAB-TV-REMOTE-FOCUS-20261013.md`），但它撤的是 beta 自己刚引入的连线，dev2 从未持有该内容（dev2 自合并基点只改过 chip 两个文件），故不存在「被回退的内容又被顺带提交」的形态。

## 3. 复评：dev2 已提交未推送的 `2137fffc5a`

复评对象：`fix(tv): 弹层 chip 文字改为调色板无关常量，修复深色主题下选中项白底白字`。

改动集 2 路径：

- `app/src/main/res/color/selector_dialog_glass_chip_text.xml`
- `app/src/testMobile/java/com/fongmi/android/tv/ui/DialogGlassChipStyleTest.java`

实质：chip 文字选择器由 `?attr/colorOnSurface` 改为固定常量（常态 `@color/white_90`、选中/聚焦/激活/勾选 `@color/black`），并新增对比度守卫与反空转探针。

### 3.1 第 1 轮：双轴评审

#### Spec 轴（并行子代理）

用户报告原文：「**电视版**主题选深色后，选中项变成一条发光的白条」。

| 编号 | 发现 | 严重度 | 判定 |
| --- | --- | --- | --- |
| A1 | 守卫位于 `src/testMobile`，`testLeanbackArm64_v8aDebugUnitTest` 不执行它；而用户报告是电视版 | 中 | **记录不修**（见 3.3） |
| A2 | 选择器注释声明覆盖「音轨/视轨/字幕/标题/弹幕」5 类，测试只断言 track/title，未断言确实使用共享选择器的 `adapter_danmaku.xml` | 低 | **修复** |
| B | 旧断言 `occurrences(text,"?attr/colorOnSurface")==6` 被删除后，选择器的状态覆盖不再被任何断言钉住 | 中 | **修复** |
| C1 | 注释称浅色 onSurface 压常态 chip ≈ **1.79:1**；按守卫自身的 WCAG 公式复算为 **1.68:1** | 低 | **修复** |
| D1 | `selector_dialog_glass_button_tint.xml` 用 `?attr/colorOutline(Variant)` 作 `app:backgroundTint`，配恒定白字 `?attr/webhtvColorOnWallpaper`，宿主是固定深靛玻璃；浅色默认态白字压 `#C4C6CF` 仅 1.70:1 | 高 | **记录不修**（范围外，见 5） |

Spec 轴同时确认：**无 scope creep**，注释引用的先例 `display_option_text.xml` 的 `state_selected` 确实用 `@android:color/black` 压在固定浅色选中底上，引用成立。

#### Standards 轴

并行子代理未在本次预算内返回结论，改由本会话按同一口径自查（diff 仅 2 文件，已逐行读完）：

| 编号 | 发现 | 严重度 | 判定 |
| --- | --- | --- | --- |
| S1 | 删除旧断言后 `text` 局部变量成为死变量（Dead code / 未使用局部） | 低 | **修复**（由 B 的修复直接消除） |
| S2 | `contrast` / `luminance` 两个 WCAG 助手在测试源集中已重复 ≥8 处（`LeanbackForegroundContrastTest`、`TvFixedDarkSurfaceContrastTest`、`PlayerSettingsContrastTest`、`HomeMenuDialogContrastTest`、`TvFocusRingContractTest`、`LabSettingsDialogThemeTest`、`SearchScopePopupLayoutTest`、`TmdbCinemaLightReadabilityTest`），仓库无共享测试工具 | 判断 | **记录不修**：仓库既有惯例（8+ 处同类），属"仓库标准优先于基线 smell"；抽公共工具需改动 9 个无关文件，违反范围规则 |
| S3 | 神秘命名 / 臆测式泛化 / 中间人 / 霰弹式修改等其余基线 smell | — | 未命中 |

### 3.2 第 1 轮发现与修复

修复集 2 文件（+4 / −3 行，均落在复评对象自身的两个文件内）：

1. **A2**：`trackAndTitleSelectionsShareTheLightGlassFocusStyle` 增加 `adapter_danmaku.xml` 的共享选择器断言，使断言面与注释声明的 5 类 chip 一致（`adapter_track` / `adapter_title` / `adapter_danmaku` 三个布局已逐一核实同时引用 `selector_dialog_glass_chip` 与 `selector_dialog_glass_chip_text`）。
2. **B + S1**：把旧断言的强度补回来——`withoutComments(text)` 上断言恰好 **6** 个 `<item `，并逐一断言 `state_enabled="false"` / `state_checked` / `state_activated` / `state_selected` / `state_focused` 五个状态条目存在。这同时消除了死变量 `text`。
   - 为什么重要：`state_activated` 正是 TV 侧持久选中态（`TrackAdapter` 用 `setActivated` 表达当前播放轨）。删掉该条目会让选中 chip 回落到跟随调色板的默认白字，**重新出现用户报告的缺陷**，而原对比度守卫只查 `state_checked` 与默认态，抓不到。
3. **C1**：选择器注释与测试 Javadoc 中的 `1.79:1` 均订正为 `1.68:1`，并注明三个比值的口径是"不透明原始色对按 WCAG 2.x 相对亮度直接计算（与守卫同一口径）"，与常态态的"半透明合成后实测区间 7.98–9.57:1"区分开。

### 3.3 第 2 轮：修复后复评

- 修复未触碰任何生产行为：`selector_dialog_glass_chip_text.xml` 的 `<selector>` 主体 6 个状态条目**逐字节未变**（仅注释文字变化），因此不存在回归面。
- 新增断言的两条不变量已用**变异验证**证明非空转（见 4）。
- 复评确认修复后无遗留必修项；A1、D1、S2 为非阻塞观察，理由见 3.1 与第 5 节。
- **第 2 轮通过。**

## 4. 验证

| 验证项 | 命令 / 方法 | 结果 |
| --- | --- | --- |
| 合并冲突 | `git merge --no-commit --no-ff origin/beta` | 0 冲突 |
| 合并内容中性 | 补丁摘要比对 | 相同 `d6802118…`（见第 2 节） |
| 零丢失 | `git ls-tree -r` 文件数 | 5063 = 5063；除 2 路径外全部与 beta 逐字节一致 |
| 定向单测（修复前） | `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.DialogGlassChipStyleTest --tests com.fongmi.android.tv.theme.LeanbackForegroundContrastTest --tests com.fongmi.android.tv.theme.TvFixedDarkSurfaceContrastTest --tests com.fongmi.android.tv.theme.ThemeBaseWiringTest` | **BUILD SUCCESSFUL**（9m19s）；`tests=26 failures=0 errors=0 skipped=0`（chip 6/6） |
| 定向单测（修复后） | 同上，仅 `--tests …DialogGlassChipStyleTest` | **BUILD SUCCESSFUL**（30s）；`tests=6 failures=0 errors=0 skipped=0` |
| 变异验证（状态锁非空转） | 删除选择器 `state_activated` 条目后重跑 | **转红**：`trackAndTitleSelectionsShareTheLightGlassFocusStyle FAILED`，`6 tests completed, 1 failed` |
| 变异恢复 | 从字节备份还原 + SHA-256 比对 | 一致 `2ae28d3c…`；重跑 **6/6 全绿** |
| 对比度门禁 | `bash scripts/check_ui_tokens.sh --strict` | `UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28`（对比度全过） |
| 独立复算 | 本会话按 WCAG 2.x 相对亮度复算 | 1.04 / **1.68** / 16.87（不透明原始色对）；常态态半透明合成后 8.17–10.39 |

### 4.1 门禁违规归属（记录，非本次引入）

`check_ui_tokens.sh --strict` 的十六进制扫描（`has_hex`，**含注释文本**）报告 2 处违规，均非本次改动引入：

| 路径 | 归属 | 说明 |
| --- | --- | --- |
| `app/src/main/res/drawable/selector_lab_link.xml` | **beta 本轮引入**（PR#444） | 其文档注释含 `#E7E8EF` / `#2196F3` 两个字面量即触发扫描；合并前 dev2 树中该文件不存在 |
| `app/src/mobile/res/layout/item_following.xml` | 既有 | 合并前 dev2 已存在（`2137fffc5a` 的提交信息已记录） |

该门禁**不在 CI 中运行**（`.github/workflows` 无引用），且 `2137fffc5a` 当时即已带 1 处同类违规。按范围规则：对比度门禁必须 0 失败（已满足），既有/他分支的十六进制扫描违规记录不修。

## 5. 非阻塞观察（记录不修）

- **A1（守卫的 lane 归属）**：守卫在 `src/testMobile`，故 `testLeanbackArm64_v8aDebugUnitTest` 不执行它。不改的理由：(1) 守卫是**纯静态资源/源码检查**——按路径直读 `src/leanback/res/layout/*.xml`，与 flavor 无关，在 mobile lane 运行得到的是**完全相同**的 TV 布局信号；(2) 本仓库的验证惯例对任何改动都同时跑 leanback 与 mobile 两个 lane，守卫实际会被执行；(3) 仓库 `src/testMobile` 内本就有大量 TV 面守卫（`TvAppExitSourceTest`、`PlayerControlFocusIntegrationTest`、`TmdbCinemaLightReadabilityTest` 等），放在此处并非异类；(4) 移动文件会放大本次交付面且需再跑一次完整 leanback 编译验证，收益不抵成本。
- **D1（同类缺陷，范围外）**：`app/src/main/res/color/selector_dialog_glass_button_tint.xml` 用调色板跟随的 `?attr/colorOutline` / `colorOutlineVariant` 作 `MaterialButton` 的 `app:backgroundTint`，而按钮文字是两表恒为 `#FFFFFF` 的 `?attr/webhtvColorOnWallpaper`，宿主是固定深靛玻璃。复算白字对比：浅色默认态（`colorOutlineVariant` `#C4C6CF`）**1.70:1**、浅色选中态（`colorOutline` `#74777F`）4.48:1、深色选中态（`#8E9099`）3.18:1。这与本次缺陷同属"调色板跟随值压在固定玻璃语境"，但该文件**两侧都未改动**（`git diff --name-only 4913d6ff23 2137fffc5a` 与 `… origin/beta` 均为空），不在本次评审对象内，且对比度门禁的 38 组配对未覆盖它。**建议另开任务**（消费方：`leanback/res/layout/dialog_timer.xml:114`、`mobile/res/layout/dialog_timer.xml:114`、`mobile/res/layout/dialog_offset.xml:35`）。
- **S2（WCAG 助手重复）**：见 3.1 Standards 轴。
- **门禁扫描把注释当颜色**：`selector_lab_link.xml` 因注释里的十六进制字面量被记为违规。这不是本次改动的问题，但说明该扫描对"文档性十六进制"零容忍；本次修复的注释因此**刻意避免写入任何十六进制字面量**（用"近白/近黑/浅灰实底/深靛"表述），以免把 `selector_dialog_glass_chip_text.xml` 变成第三处违规。

## 6. 已知限制

- 既有 `.wh_diag/` 诊断产物为任务开始前的工作区脏文件（124 个未跟踪路径），已由任务守卫列为保护路径，未触碰、未提交。
- 全量 JVM 套件在本机存在既有 CRLF 行尾敏感失败（根因 `core.autocrlf=true`，见 `docs/C58-beta-merge-review-dev2-20261011.md`、`docs/C35-beta-merge-review-dev4-20261004.md`），与本次改动无交集，按范围规则不扩大修复面；本次只跑与改动直接相关的 4 个测试类。
- 本轮合并有真实内容增量（33 路径），但合并结果已被证明**逐字节等于 beta tip + dev2 自身提交**，故 beta 侧内容的正确性由 beta 自身各分支轮次（`docs/C66` / `C67` / `C68`、`docs/C65-player-hwdec-auto-soft-fallback-merge-20261012.md`）负责，本任务不重复评审。
- Standards 轴并行子代理未在预算内返回，已改用同口径自查（3.1），并保留上述 S2 观察。

## 7. Recovery anchor

- 目标：把 `origin/beta` 最新合入 `dev2`（不回退已移除提交）、复评 dev2 已提交未推送改动、修复问题并验证通过、再复评，通过后提交、推送、开 PR 到 beta（只创建不合并）。
- 状态：**已闭环**。合并 0 冲突且内容中性（补丁摘要相等）；双轴评审发现 5 项，其中 4 项已修（A2/B/C1/S1，同一处 B+S1）、3 项记录不修（A1/D1/S2）；定向单测 26/26（修复后 6/6）通过；变异验证证明新增状态锁非空转；已提交、已推送、PR 已创建（只创建未合并）。
- 交付坐标：
  - 合并提交：`b515bf11bbe4d71664dbe6d462246b29fa27dc19`（双父：`2137fffc5a` + `a8c0d6a5fe`）
  - 恢复标签（本地）：`recovery/C69-beta-merge-review-dev2-20261014/20261014153930-b515bf11bbe4`
  - 推送：`origin/dev2` 快进 `4913d6ff23..b515bf11bb`
  - PR：[#446](https://github.com/Silent1566/webhtv/pull/446)（`dev2` → `beta`，`OPEN` / `MERGEABLE` / `CLEAN`，只创建不合并）
  - PR 净增量：恰为 3 个路径（`selector_dialog_glass_chip_text.xml`、`DialogGlassChipStyleTest.java`、本文件）
- 下一步：无。
