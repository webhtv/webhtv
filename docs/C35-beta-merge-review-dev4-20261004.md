# C35：dev4 合并远端 beta 最新代码（PR#399–#401）并复评 TV 横幅按密度输出改动

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev4`（远端已移除/回退的提交不得顺带带回）；复评 dev4 全部已修改代码（含已提交未推送的 `255059d48bf` TV 横幅修复）；发现问题修复并验证通过；循环评审直至通过后提交、推送 `dev4`、创建 `dev4 -> beta` 中文 PR（只创建，不合并）。
- **验收标准**：① 合并结果包含 `origin/beta` tip `13f07c8cbad`；② 远端被回退/剔除内容零复活；③ dev4 相对 beta 净差异仅含本分支自身改动（TV 横幅 + 本评审的 CRLF 归一化修复 + 本文档）；④ 目标 JVM 测试与双 flavor 编译通过；⑤ 中文 PR 已创建且描述排版清楚。
- **当前状态**：合并、三轮评审、全部验证完成；全量双 flavor 测试的 21 个失败已用纯 `origin/beta` 参照工作树（同机同配置独立构建）实证为**既有问题**（失败集合与合并后完全一致，零个由本任务引入）。
- **下一动作**：`task_guard.sh finish`（合并提交 + recovery tag）→ 推送 dev4 → 创建 PR（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev4` |
| 任务开始时 HEAD | `255059d48bfe2849327ab65845dae2a93511648c`（领先 origin/dev4 1 个提交，未推送） |
| `origin/beta` tip | `13f07c8cbadecb77f9ad7bb5e1f22d9207d83e0f`（Merge PR #401 from dev3） |
| 合并基点 | `62527bec42550d8a2162e57d90dad3523c964dda`（dev4 侧原 HEAD，已随 PR #399 进入 beta） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`（由 task_guard finish 创建合并提交） |
| 合并结果 | 21 文件自动合入，0 冲突 |

beta 侧增量（`62527bec4..13f07c8cbad`）：PR #399（dev4 侧追更墓碑同步合入 beta 的合并提交）、`f2cd006b019`（启动按 `config_<type>` 偏好选上次使用接口，dev1）、PR #400（dev1）、dev3 TMDB 线路自定义 4 提交（`41ab47182cc`/`024c13b5f98`/`9d0deec4605`/`bfbd26bb98c`）、PR #401（dev3）、`88477050810`/`f22e24f5f2a`（dev3/dev1 侧的 beta 增量合并提交）。

## 被回退/剔除内容核对（用户核心关注点）

1. 合并后暂存树 vs `origin/beta` 全量文件级 diff = **恰好 11 个路径**，与 dev4 未推送提交 `255059d48bf` 的文件集完全一致（程序化 `diff` 比对：MATCH）。
2. beta 上全部 11 个 revert 提交（`fd29d76ee`、`0616a992f`、`70306f1de`、`be1b02e06`、`bdb2dfec3`、`8c2efeа42`、`a5aeb3de4`、`842727c3a`、`25956ec5`、`66dc82253`、`c9219e49f`）涉及的 **132 个文件**，在合并树与 `origin/beta` 之间**零差异**——被回退内容没有通过 dev4 侧复活，也不会随本 PR 重新提交上去。
3. dev4 未推送提交 `255059d48bf` 只新增/删除 leanback 横幅资源与生成脚本，与任何 revert 无交集。

## 评审记录

### 第 1 轮：dev4 未推送提交 `255059d48bf`（TV 横幅按密度输出）

- **资源形态**：删除 `mipmap-anydpi-v26/ic_banner.xml`、`drawable/ic_banner_foreground.xml`、`drawable/ic_banner.png`，新增 5 档密度 `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_banner.png`。`AndroidManifest.xml` 两处 `android:banner="@mipmap/ic_banner"`（application + alias）引用不变，横幅只在密度桶中解析，不再被 anydpi-v26 方形自适应图标抢占。
- **实测（提交内 PNG 逐字节解析）**：5 档全部 16:9（160x90/240x135/320x180/480x270/640x360）；xhdpi 档内容墨迹宽度占比 0.869 < 安全区 0.878；垂直居中偏差 -0.5px；字标 M(20-87)+O(94-173) 与文字块(209-298) 之间有 36px 间隙，文字块墨迹簇 4 个（默的偏旁分离属正常字形结构），**无豆腐块、无空文字**——打包字体子集渲染真实有效。
- **gen_app_icon.py**：`render_banner` 在最终分辨率上求解 O 直径，最多两轮回塑必收敛；安全区越界抛 `ValueError` 兜底；`_render_text` 先探针量墨迹高度再定字号，避免字号与墨迹高度不成比例；`_purge_banner_leftovers` 幂等（文件已删则跳过）。
- **测试**：`AppBrandingContractTest` 新增 `tvBannerIsProvidedPerDensityAtFullResolution`、`tvBannerUsesBitmapDensitiesNotAdaptiveIcon`，与实现逐条对齐；13 用例全通过。
- **无遗留引用**：全仓 grep 无任何代码/资源再引用被删除的 3 个文件（仅测试中的否定断言）。
- **字体资产**：`scripts/assets/NotoSansSC-Bold-brandmark.otf`（3.6KB，OFL-1.1 子集，仅含"默影视"3 字形）只在生成期使用，不在 APK sourceSets 内，不会被打包分发。
- 结论：**通过**。

### 第 2 轮：beta 带入内容独立复评（PR#397/#398/#400/#401）

> beta 侧内容已由 dev1（C34-beta-merge-review-dev1）与 dev3（C34-beta-merge-review-dev3）各自完整评审并入 beta；本轮为 dev4 侧的独立增量复评，重点是与 dev4 自身改动（横幅、追更墓碑）的交互与回归面。

- **`f2cd006b019`（启动配置偏好选择，dev1）**：`Config.lastActive(type)` 先按 `config_<type>` 偏好 `find(url,type)` 精确定位，偏好缺失或行已删回退 `findOne(type)`（原 time DESC 行为），闭环完整；`ConfigDao.find(url,type)` 与 `Prefers` import 齐全；与 dev4 侧无交互。✅
- **`dd37a13fc` + `5e6af9e54`（集多版本消歧 + 误伤修复，dev2，经 PR#397/#398）**：`Episode.matchesPlayback(Episode, boolean versionAware)` 分层判据 `Flag.containsEpisodeUrl`（历史 URL 仍能定位到本线路条目才启用消歧；换线路/刷新保留集号容错）；leanback/mobile `VideoActivity` 4 处调用点与 `TmdbDetailActivity.isHistoryEpisode` 接线一致；`Flag.find` URL 优先定位放在 `size==1` 快路径之前，TMDB 位置冲突回退季集号定位；null/空 URL/空列表防护完整（`getFlag()` 空列表返回 `new Flag()` 非 null）。与 dev4 侧追更墓碑（Following 数据层）零交集。✅
- **`41ab47182`/`024c13b5f`/`9d0deec4`/`bfbd26bb98`（TMDB 线路自定义，dev3，经 PR#401）**：`apiCustomInput`/`imageCustomInput` 默认 `gone`、确认键弹出 Picker（`setKeyListener(null)` 禁软键盘）、自定义经 `TmdbProxy.normalizeConfig` 归一化保存、官方直连回显修复、D-pad 焦点可进列表项；`TmdbConfigCustomRouteModeTest` 5 用例覆盖保存→重读往返。✅
- **测试对齐**：`EpisodeVersionIdentityTest`（17 用例）与 `HistoryPlaybackTest`（63 用例）覆盖消歧两侧（同集多版本判异、跨线路容错、季号未知容错）；`VideoActivityHistoryTitleTest`/`VideoActivityLayoutTest` 源码契约断言与实现一致。✅

### 第 3 轮：发现并修复的问题（CRLF 脆弱断言）

- **问题**：`TmdbSourceDialogInflationContractTest.apiAndImageRoutesUseSeparateDropdownInputs` 在本机失败（leanback/mobile 两个 flavor 均复现）。根因：测试用 `Files.readAllBytes` 原样读工作区文件后做多行 `contains`（needle 硬编码 `\n`），而本机 `core.autocrlf=true` 使工作区 XML/Java 为 CRLF，断言必然失败。
- **证明既有非本任务引入**：源文件与测试文件均与 `origin/beta` 逐字节一致（净差异 0）；LF 归一化后同一 needle 匹配成立 → 纯行尾敏感，CI（ubuntu LF）上通过。
- **修复（测试侧单点归一化，零生产行为影响）**：`read(Path)` 归一化 `\r\n -> \n` 后再比对，多行断言在 LF/CRLF 检出下行为一致。
- **修复后验证**：leanback + mobile 两个 flavor 的 `TmdbSourceDialogInflationContractTest` 全部通过（BUILD SUCCESSFUL）。
- **同类既有失败（记录不修，超出本任务净差异）**：`DialogRoundedCornerSourceTest.materialDialogShapeUsesTheUnifiedTwentyTwoDpRadius` 同为 CRLF 脆弱断言（文件与 beta 逐字节一致，LF 归一化后通过），与本任务改动无交集，按 AGENTS.md 范围规则仅报告。建议后续在 beta 侧统一把多行断言改为行尾容忍写法。

## 验证记录

| 验证项 | 结果 |
| --- | --- |
| 双 flavor 编译（leanback/mobile arm64 debug JavaWithJavac） | BUILD SUCCESSFUL（4m39s） |
| leanback 定向：AppBrandingContractTest 13 用例 | **13/13 通过** |
| leanback 定向：bean.* + following.* + ui.dialog.* + VideoActivityDetailShellSourceTest | 360 用例，除已分类既有 CRLF 断言外全通过 |
| mobile 定向：bean.* + following.* + VideoActivity*Test | 461 用例，除同类既有 CRLF 断言外全通过 |
| 横幅 PNG 实测（5 档密度解析） | 16:9 全档、安全区内、居中、字形真实渲染 |
| 合并树 vs origin/beta 净差异 | 恰好 = `255059d48bf` 文件集（程序化比对 MATCH） |
| revert 涉及 132 文件 vs beta | 零差异（无复活） |
| CRLF 修复后 TmdbSourceDialogInflationContractTest（双 flavor） | **通过** |
| 全量 leanback JVM 套件 | **4259 用例，11 失败**，与纯 beta 参照树失败集合完全一致（详见下表） |
| 全量 mobile JVM 套件 | **5111 用例，10 失败**，与纯 beta 参照树失败集合完全一致 |

### 全量测试失败归类（纯 beta 参照对照）

在独立检出的纯 `origin/beta` 参照工作树（同机、同 `core.autocrlf=true`、同构建配置）上运行同一批失败测试类，**失败集合与合并后 dev4 完全一致**（leanback 11/11 逐项相同，mobile 含 `PlayerPlaybackRegressionSourceTest` 同名失败）——证明这 21 个失败全部是 beta 侧既有问题（绝大多数为本机 CRLF 行尾下的多行文本断言脆弱，CI ubuntu LF 下不触发），**零个由本任务引入**：

- `LiveActivitySourceFallbackSourceTest`×2（错误信息带 `src\leanback\...` 路径分隔符，平台差异）
- `FollowingUiSourceTest`×2（C33 文档已记录的 CRLF 断言）
- `ReaderPlaybackRoutingSourceTest`×2、`SearchResultDownFocusTest`、`NativeEnhancedPlaybackStyleFocusTest`、`TmdbSourceOnlyInteractionTest`、`DialogRoundedCornerSourceTest`、`WebThemeTokenSourceTest`、`PlayerPlaybackRegressionSourceTest`（同为多行源码文本断言）

按 AGENTS.md 范围规则：既有问题不扩大修复面，仅记录。本任务净差异内的测试全部通过。

## 提交与推送

- 本任务产物：merge commit（含本评审文档 + CRLF 归一化修复）+ recovery tag。
- PR：dev4 → beta，中文描述，只创建不合并。

## Next action

`task_guard.sh finish` → push dev4 → `gh pr create`（只创建不合并）。
