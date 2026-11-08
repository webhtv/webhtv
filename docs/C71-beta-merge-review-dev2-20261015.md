# C71 dev2 合并 origin/beta 并复评（2026-10-15）

审计日期：2026-10-15（Asia/Shanghai）
任务 ID：`C71-beta-merge-review-dev2-20261015`
分支：`dev2`
范围：`app`、`docs`

## 1. 目标与判定标准

把 `origin/beta` 最新代码合入 `dev2`，**不得把远端已移除/回退的提交顺带带回来**；随后评审本轮已修改的代码（含 dev2 已提交未推送的提交），发现问题就最小修复并验证，通过后再次评审，循环至通过；最后提交、推送、创建到 `beta` 的 PR（中文描述，只创建不合并）。

验收标准：

1. 合并提交为双父合并，第二父等于提交前最新 `origin/beta` tip。
2. 合并结果相对 `origin/beta` 的净差异**只**包含 dev2 自身的本地改动，不含任何 beta 已删除内容。
3. 零复活有可证伪证据（检测器自检必须能报出注入的探针）。
4. 定向测试、双 flavor Java 编译、`git diff --check`、`gradlew clean` 全部通过。
5. PR `base=beta`、`head=dev2`、`state=OPEN`、`mergedAt=null`，描述为中文。

## 2. 合并前的仓库状态

| 项 | 值 |
| --- | --- |
| 分支 / HEAD | `dev2` / `4f75fbe451f6a04bcdfbcdc3024fe2f8f0caa415` |
| `origin/dev2` | `d158f93f9446d26b0fd23c0a7e62dbb89547da42`（本地领先 1） |
| `origin/beta` tip | `870b2f8220d1b9cd83381198945c5a11f8e84b62` |
| 合并基 | `d158f93f9446d26b0fd23c0a7e62dbb89547da42` |
| 领先/落后 | `1 / 9` |
| 开工前脏文件 | `.wh_diag/`（124 个未跟踪诊断产物，由 `.git/info/exclude` 排除，已列为保护路径） |

### 2.1 本地已提交未推送的改动（评审对象）

单个提交 `4f75fbe451`（`MOBILE-MANUAL-ROTATE-PERSIST-20261015`），2 个路径：

- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`：新增 `manualRotate` 标记，`onRotate()` 置位、`checkOrientation()` 让位、进出全屏重建自动基线并清除。
- `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/PlayerPlaybackRegressionSourceTest.java`：新增 `mobileManualRotateSurvivesEpisodeSwitchInsteadOfBeingAutoCorrected`。

### 2.2 beta 侧 9 个提交的真实内容

`870b2f8220` 是「把 `dev2` 合进 `beta`」的双父合并提交，其第二父正是 `d158f93f94`（dev2 当时的 tip）。因此反向合并后：

- 合并结果树 `f01ec5c437fdb8f05f64dd2c69d1b4d3956b24b0`
- `git diff <merge tree> origin/beta` **只**列出 dev2 自己的 2 个路径（方向相反），即**零内容合并**。
- beta 相对 dev2 父的真实内容增量只有 dev3 的站点面板（PR #445）与文档。

## 3. 零复活 / 零丢失证据（四层）

| 层 | 方法 | 结果 |
| --- | --- | --- |
| 1. 树对象中性 | 合并结果 tree vs `origin/beta` tree | 差异恰为 dev2 的 2 个路径，方向相反；**逐字节中性** |
| 2. 文件级零新增 | `git ls-tree -r --name-only` 整树 `comm`（规避 MSYS 把 `:` 当路径分隔符的假阳性） | 合并树 5066 = beta 树 5066；**仅新增 0、仅缺失 0** |
| 3. 行级复活扫描 | 184 个 revert 类提交（主题行锚定 `^(Revert\|revert\|回退\|撤销\|剔除)`）删除的 10844 条实质行 vs 本轮净新增 37 行 | **真实复活 0** |
| 4. 检测器可证伪自检 | 把一条「在 revert 集且不在净差异」的探针行注入伪造集合，要求检测器报出 | **PASS**（探针被报出）；同时 `origin/beta..origin/beta` 空差异对照下仍为 0，排除空转 |

第 3 层刻意加了两道降噪：只匹配**主题行**（避免任务自己的合并提交正文含「revert-like」字样被误判），并只保留「实质行」（长度 ≥ 24 且含标识符/字符串），否则 `}`、`@Test`、`break;` 这类样板行会把命中数从 0 抬到 77 条纯噪声。

## 4. 评审发现与修复

### 4.1 第 1 轮：发现 1 项必修（P1）

**P1 — `PlayerPlaybackRegressionSourceTest` 的跨行字面量断言在 CRLF 工作区恒假。**

- 现象：定向测试 `livePlaybackAlwaysAutoplaysWhileVodUsesTheConfiguredPolicy` **失败**（`AssertionError` at 行 353），本轮 19 个测试 1 失败。
- 根因：仓库 `core.autocrlf=true`，Windows 工作区源文件是 CRLF；`read()` 用原始 `Files.readString` 返回含 `\r\n` 的文本，而 Java 字面量里的 `"\n"` 是单个 LF。于是 `playback.contains("protected boolean shouldAutoPlay() {\n ...")` **永远为假**。
- 影响面不止「一个用例失败」：`assertAlwaysAutoplay()` 用同一模式断言 mobile/leanback `LiveActivity` 的 `@Override shouldAutoPlay(){return true;}`，它被 `&&` 串在真断言之后，**恒假但被掩盖**——即该回归锁实际零保护。同类 `indexOf("\n    private ")` 方法边界查找也会返回 `-1`，使 `methodEnd > method` 类断言恒假。
- 修复：`read()` 统一归一化行尾（`.replace("\r\n", "\n")`），与同包 `SiteDialogLayoutTest`、`TmdbDetailActivityLayoutTest` 的既有写法一致，并补 Javadoc 说明原因。
- 归属：该断言由 `41fee8b3ab4`（2026-09-09）引入，属**既有缺陷**；但本次任务改动的正是同一个文件，且它直接阻断本任务的验证门禁，故在范围内修复。

**变异验证（证明修复后断言非空转，三项全部转红）**

| 探针 | 注入内容 | 结果 |
| --- | --- | --- |
| 1 | `PlaybackActivity.shouldAutoPlay()` 改为 `isAutoPlay() \|\| true` | **FAILED at 行 353**（`playback.contains` 断言） |
| 2 | mobile `LiveActivity.shouldAutoPlay()` 改为 `PlayerSetting.isAutoPlay()` | **FAILED at 行 389**（`assertAlwaysAutoplay`） |
| 3 | `VideoActivity.checkOrientation()` 的 `if (manualRotate) return;` 改为 `if (false) return;` | **FAILED at 行 255**（本轮新增的让位断言） |

三个探针均已 `git checkout --` 还原，`git status` 确认对应文件干净。

### 4.2 第 2 轮：复评修复本身与 beta 侧增量

- **修复自评**：只改 `read()` 的返回值归一化，不改任何断言语义、不改生产代码；`PlayerPlaybackRegressionSourceTest` 中无任何依赖 `\r` 的断言（已扫描确认），故归一化不会让别的断言失效。
- **同类缺陷横向排查**：扫描 `testMobile`/`test`/`testLeanback` 中所有「`Files.readString` + 跨行字面量」组合，命中 15 个类。其中 9 个已自带 `\r\n` 归一化；剩余 6 个（`C47SeekLoadingProgressSourceTest`、`ReaderPlaybackRoutingSourceTest`、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`、`NativeEnhancedPlaybackStyleFocusTest`、`SearchResultDownFocusTest`）经 `git diff` 核实**既不在本次任务差异内、也不在 beta 增量内**，属既有他任务范围，按范围规则**记录不修**。
- **beta 侧站点面板增量**（PR #445，dev3 交付）：本轮合并为**零内容增量**，其正确性由 dev3 的 `docs/C69-beta-merge-review-dev3-20261014.md` 轮次负责，本任务不重复评审。仅做一致性核对：
  - `ROW_HEIGHT_DP = 52` 与 `adapter_site.xml` / `adapter_site_switch.xml` 的 `layout_height="52dp"` 一致；
  - `ROW_SPACING_DP = 16` 与 `SpaceItemDecoration(getCount(), 16)` 一致；
  - `gridHeight()` 整数行收敛数学正确（`fitRows = (budget+spacing)/(row+spacing)` 在 52/68/120/…/1000 各档均不超预算，末行不裁半截）；
  - `maxHeight` 已彻底移除，无残留引用；`actionGap` 仍被正确引用。
- **对比度复算**（WCAG 2.x 相对亮度，日/夜双表）：卡片常态填充 `colorSurfaceContainerHigh` 对面板 `colorSurfaceContainer` 为 1.05:1 / 1.16:1（仅作分层提示，非文字对比度）；卡片文字 `colorOnSurface` 对卡片底 13.99:1 / 10.48:1；面板描边 `colorOutlineVariant` 对面板底 1.47:1 / 1.68:1；焦点态文字 `colorOnPrimary` 对 `colorPrimary` 6.39:1 / 7.50:1。**全部达标**。
- **第 2 轮结论**：无遗留必修项，**通过**。

## 5. 验证

| 验证项 | 命令 / 方法 | 结果 |
| --- | --- | --- |
| 合并冲突 | `git merge --no-commit --no-ff origin/beta` | **0 冲突** |
| 合并内容中性 | `git diff <merge tree> origin/beta` | 仅 dev2 的 2 路径（反向）；逐字节中性 |
| 树对象一致 | `git write-tree` vs 预期 | `f01ec5c437fdb8f05f64dd2c69d1b4d3956b24b0` 一致 |
| 零新增 / 零丢失 | `git ls-tree -r` + `comm` | 5066 = 5066；新增 0、缺失 0 |
| 零复活 | 行级扫描 + 可证伪自检 | 真实复活 0；自检 PASS |
| 冲突标记 | `git grep -nE '^(<<<<<<<\|>>>>>>>\|=======$)'` | 无 |
| 空白校验 | `git diff --check` | 通过 |
| 定向单测（修复前） | `:app:testMobileArm64_v8aDebugUnitTest --tests PlayerPlaybackRegressionSourceTest --tests SiteDialogLayoutTest` | 19 tests / **1 failed**（P1） |
| 定向单测（修复后） | 同上 | **BUILD SUCCESSFUL**；`PlayerPlaybackRegressionSourceTest` **15/15**、`SiteDialogLayoutTest` **4/4**，failures=0 errors=0 skipped=0 |
| 变异验证 ×3 | 见 4.1 | 三项均按预期转红，探针已还原 |
| leanback Java 编译 | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | **BUILD SUCCESSFUL** |
| UI token 门禁 | `bash scripts/check_ui_tokens.sh --strict` | 对比度 `pairs=38 failures=0 min=4.28` 全过；2 处十六进制扫描违规均为 beta 既有（`selector_lab_link.xml`、`item_following.xml`），**非本次引入、不在 CI 中运行**，按范围记录不修 |
| 环境注记 | — | 首次验证因 Gradle 构建缓存锁冲突失败（`G:\GradleCache\caches\build-cache-1`，owner PID 已退出）；清理残留锁并加 `--no-build-cache` 后重跑成功。属环境问题，非代码问题 |

## 6. 非阻塞观察（记录不修）

- **O1**：`PANEL_MIN_WIDTH_DP = 480` 在极窄屏（< 533px）会取 `min(screen, 480)`，使面板等于屏宽、失去左右安全边距。真机电视最小常见宽度为 1280px，触发不到；且 `PANEL_WIDTH_RATIO = 0.9` 已覆盖正常区间。属设计取舍，不改。
- **O2**：`windowVisibleHeight()` 优先取 `getWindowVisibleDisplayFrame`，在 `dialog_site` 根尚未 attach 时回退到 `ResUtil.getScreenHeight`，两者在带系统栏的窗口上可能差一个状态栏高度。`gridHeight()` 已按整数行收敛，最多少显示一行，不会裁半截。不改。
- **O3**：6 个既有测试类的 CRLF 恒假风险（见 4.2），既不在本次差异内也不在 beta 增量内。建议另开任务统一归一化。
- **O4**：任务编号 `C70` 已被 `android6` 分支的 `5a5d07822f`（leanback API 23 history progress）占用，故本任务改用 **`C71`**（已核实 `C71` 在全部 ref 的提交主题、文档名、任务状态目录中均未被占用）。

## 7. 交付坐标

- 合并提交：`3c9cffa4c9e3d3f60053bdc57bf4ebe2cdd914a1`（双父：`4f75fbe451` + `870b2f8220`，第二父 = 提交前最新 `origin/beta` tip）
- 恢复标签（本地）：`recovery/C71-beta-merge-review-dev2-20261015/20261015052943-3c9cffa4c9e3`
- 推送：`origin/dev2`
- PR：`dev2` → `beta`（中文描述，**只创建不合并**）

### 7.1 任务编号说明

`C70` 已被 `android6` 分支的 `5a5d07822f`（leanback API 23 history progress，恢复标签 `recovery/C70-leanback-api23-history-progress/20261014080727-5a5d07822faa`）占用，故本任务使用 **`C71`**。已核实 `C71` 在全部 ref 的提交主题、文档名与任务状态目录中均未被占用。交付前已把提交信息中的 guard trailer、任务文档名与恢复标签统一为 `C71`（提交与标签均未推送，重命名安全）。

## 8. Recovery anchor

- 目标：把 `origin/beta` 最新合入 `dev2`（不回带已移除提交）、复评 dev2 已提交未推送改动、修复问题并验证通过、再复评，通过后提交、推送、开 PR 到 beta（只创建不合并）。
- 状态：**已闭环**。合并 0 冲突且内容中性（树对象逐字节中性、零新增零丢失、零复活且自检可证伪）；双轮评审发现 1 项必修（P1，CRLF 恒假断言，连带使 `assertAlwaysAutoplay` 零保护），已修并三项变异验证；定向单测 15/15 + 4/4、leanback Java 编译通过；已提交、已推送、PR 已创建（只创建未合并）。
- 关键文件：`app/src/testMobile/java/com/fongmi/android/tv/ui/activity/PlayerPlaybackRegressionSourceTest.java`（唯一修复点）、`app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`（dev2 未推送改动，评审通过未改）。
- 下一步：无。
