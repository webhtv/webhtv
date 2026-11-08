# C110-beta-merge-review-dev1-20261028

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev1`；评审 `dev1` 已提交未推送的 2 个提交（C108、C109）；
  发现问题即修复并验证，复评直至通过；然后提交、推送 `dev1`、创建到 `beta` 的中文 PR（含具体改动内容），
  最后拉取远端最新代码。
- **验收标准**：
  1. `dev1` 合并后 `origin/beta` 成为 HEAD 祖先，冲突全部解决且不夹带评审外改动。
  2. 评审覆盖 2 个未推送提交涉及的全部路径；每条结论有可复核证据。
  3. 发现的必修项均已修复并有**逐节点**回归断言；修复后复评通过。
  4. `assembleMobileArm64_v8aDebug` + `theme` 单测全绿。
  5. 改动提交并打 recovery tag；推送 `dev1`；创建到 `beta` 的中文 PR；随后拉取远端最新代码。
- **Lane**：`standard`（`app/src` + `docs`）
- **任务守卫**：`C110-BETA-MERGE-DEV1-20261028`，mode `standard`，scope `app/src`、`docs`，
  branch `dev1`，base HEAD `169bc59860cc9048cbe9fdce450466ad79ef4547`，protected 无既有脏路径。
  必须以 Git for Windows 的 bash 运行（默认 `bash` 是 WSL，报 `HCS_E_SERVICE_NOT_AVAILABLE`）。
- **当前状态**：合并、双轴评审、必修项修复、复评通过、验证完成；交付坐标见 §7

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 合并目标 | `origin/beta` = `afdb2b92c5e1e1022883df5f72efaa3daf715e42` |
| 合并前本地 | `dev1` = `169bc59860cc9048cbe9fdce450466ad79ef4547` |
| merge-base | `ce2fcc20957c7ff37a2667740425e3cc136bdcf4`（"docs(C107-2): 回填交付坐标"） |
| 落后 / 领先（合并前） | `git rev-list --left-right --count origin/beta...HEAD` = **11 / 2** |
| 冲突 | **0**（`git merge --no-ff --no-commit origin/beta` → "Automatic merge went well"，`git diff --diff-filter=U` 为空） |
| `.git/MERGE_HEAD` | `afdb2b92c5e1e1022883df5f72efaa3daf715e42`，与 `origin/beta` 一致 |
| beta 侧改动面 | **7 个路径 / +1166 −19**（相对 merge-base） |

beta 本次带入 11 个提交（6 个非 merge 代码提交）：

```
ed85084a89 docs(C108): 回填 PR #496 与推送坐标
c56ece5f16 docs(C108): 回填交付坐标（合并提交 + recovery tag + 回滚路径）
fe51f3a246 fix(tv-play): 修正进场焦点并修复切线路后集数网格左右键失效
0af0e0afc8 fix(tv-play): 撤销常驻焦点看门狗，焦点不再在播放器与集数按钮间来回跳
c8dcbbeef0 fix(tv-play): 选集网格内左右键改为显式接管，不再冲出网格落到「简介」
cc34fb5914 fix(tv-play): 修复切线路后集数分段按钮左右键失效并加固焦点链
b04fcc8abc fix(tv-detail): 关闭点播自动播放后不抢预览框焦点、显示封面、选集直接全屏起播
abe05f2bc6 fix(tv-detail): 关闭点播自动播放后不再抢占预览窗口焦点并显示封面
```

这些均为 `beta` 侧已在 PR #494 / #496 评审并合并的内容，本轮不重复评审。

beta 触及的 7 个路径：

```
app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java
app/src/leanback/res/layout/activity_video.xml
app/src/main/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeGridPolicy.java
app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityLayoutTest.java
app/src/testMobile/java/com/fongmi/android/tv/ui/helper/TmdbEpisodeGridPolicyTest.java
docs/C108-beta-merge-review-dev4-20261028.md
docs/TV-MANUAL-PLAY-20261026.md
```

## 2. 合并保真性核实

- **正向保真**：对上述 7 个路径逐个比较 `origin/beta` 的 blob 与合并后工作树 `git hash-object` 结果
  → `beta_changed_paths=7`、`mismatch_count=0`。
- **dev1 侧未受影响**：C108/C109 的两个文件未被 beta 触及（`git diff 169bc59860 -- app/src/mobile/res/` 输出为空）。
- **冲突标记扫描**：`java`/`xml`/`kt` 三类匹配数均为 0。
  （注意：PowerShell `Select-String` 无 `-Recurse` 参数，用它粗扫会得出假 0，必须用 `grep` 工具或显式递归。）

## 3. 评审范围

| 提交 | 内容 | 路径 |
| --- | --- | --- |
| `6bef992767` | C108：快搜面板线路名称改为常亮前景 | `app/src/mobile/res/layout/adapter_quick.xml`（+1 −1） |
| `169bc59860` | C109：快搜面板搜索按钮改为玻璃底 | `app/src/mobile/res/drawable/shape_quick_search_icon_button.xml`（+5 −2） |

固定点：`ce2fcc20957c7ff37a2667740425e3cc136bdcf4..169bc59860cc9048cbe9fdce450466ad79ef4547`。
双轴（Standards / Spec）并行评审，结果分别呈现。

### 第一轮

- **Standards 轴：通过**。无硬性违规。`#12FFFFFF`/`#40FFFFFF` 落在
  `docs/ui-token-allowlist.txt:195` 既有豁免内；`#site` 改后与同布局 `:39 #name`、`:69 #remark` 一致；
  旧值 `#E8F0FE` 浅底配纯白图标约 1.16:1，属真实缺陷而非风格问题。
- **Spec 轴：需修复（3 项）**：
  1. C108 的验收证据无效 —— `ThemeBaseWiringTest.wallpaperPagesUseTheWallpaperForegroundRole`
     （`app/src/test/java/com/fongmi/android/tv/theme/ThemeBaseWiringTest.java:294-306`）是**整文件存在性断言**：
     `assertFalse(source.contains("android:textColor=\"?attr/colorOnSurface\""))` 与
     `assertTrue(source.contains("android:textColor=\"?attr/webhtvColorOnWallpaper\""))`。
     `#site` 用的是 `colorOnSurfaceVariant`，因断言带右引号而漏检；同布局 `#name`/`#remark` 早已满足两条断言，
     故改动前该守卫就是绿的，缺陷是「绿着出厂」的。
  2. C109 无任何回归断言 —— `darkGlassSheetsUseAConstantLightForeground`（同文件 `:327-352`）只扫布局前景属性
     （`SHEET_FOREGROUND_ATTRS` `:179-182`），从不打开 drawable，`#E8F0FE` 回潮不会变红。
  3. C109 提交信息称「玻璃胶囊底」，实际 `corners` 为 6dp（44dp 按钮的真胶囊应为 22dp）。

协调方逐项独立核实：三项发现全部成立。第 3 项经用户决策为「不改写历史、半径保持 6dp
（该 drawable 与邻居 `shape_quick_search_input.xml` 逐字节相同，6dp 是刻意对齐），
由修复提交信息与 PR 描述准确表述」。

## 4. 修复

唯一改动文件：`app/src/test/java/com/fongmi/android/tv/theme/ThemeBaseWiringTest.java`（+98 −0）。

新增两条**逐节点**契约（沿用仓库 `DialogForegroundTokenTest.java:86-96` 的
「per-call-site, not whole-file」范式）：

- `quickSearchRowSiteNodeUsesTheWallpaperForegroundRole`（`:376-384`）：以
  `android:id="@+id/site"` 为锚取出该标签本身，断言 `android:textColor` 精确等于
  `?attr/webhtvColorOnWallpaper`，并禁止该节点出现 `colorOnSurface`。
- `quickSearchIconButtonKeepsTheDarkGlassFillAndStroke`（`:402-415`）：`<solid>` =
  `#12FFFFFF`、`<stroke>` = `#40FFFFFF` / `1dp`，并禁止 `#E8F0FE`。
- 辅助方法 `element()`（`:466-482`，锚点缺失、重复、标签不完整一律 `fail()`，
  不会静默退化成 no-op）与 `attribute()`（`:485-490`）。

第二轮复审另提三处 Javadoc 事实陈述问题，已修正（只动注释，断言字节未变）：

- `bare prefix` 措辞与「带右引号的完整字面值」矛盾 → 改为明确写出 closing quote included，
  故只匹配 `colorOnSurface` 本身、不匹配更长的 `colorOnSurfaceVariant`。
- 称 `colorOnSurfaceVariant` 为 "near-black" 不准 → 改为引用实际解析链
  `app/src/main/res/values/webhtv_styles.xml:31` → `@color/webhtv_color_on_surface_variant`
  → `app/src/main/res/values/webhtv_tokens.xml:34` = `#44474F`，夜间
  `app/src/main/res/values-night/webhtv_tokens.xml:34` = `#C4C6D0`。
  （第二轮评审提出的反证 `app/src/mobile/res/values/styles.xml:113` = `#5D7278` **不成立**：
  该行属于局部 overlay `ToolbarPopupTheme`，与 `#site` 无关。）
- `glass capsule` 与 `corners 6dp` 矛盾 → 改为 `6dp rounded translucent-white glass chip`，
  并说明 6dp 与邻居 `shape_quick_search_input.xml` 逐字节一致、故为 chip 非 capsule。

## 5. 变异验证（证明新守卫真的会红）

| 步骤 | 操作 | 结果 |
| --- | --- | --- |
| 1 | 基线 | 绿 |
| 2 | `adapter_quick.xml:57` 改回 `?attr/colorOnSurfaceVariant` | 新守卫红：`ComparisonFailure: #site must paint its text with the wallpaper role expected:<?attr/[webhtvColorOnWallpaper]> but was:<?attr/[colorOnSurfaceVariant]>`；**同一份报告里旧的整文件守卫仍无 failure** |
| 3 | 还原后把 drawable `solid` 改回 `#E8F0FE` | 新守卫红：`ComparisonFailure: shape_quick_search_icon_button.xml must fill the button with translucent white glass expected:<#[12FFFFFF]> but was:<#[E8F0FE]>` |
| 4 | `git checkout` 还原两文件 | `app/src/mobile/res/` 与 HEAD 零差异 |

第 2 步是该修复价值的直接实证：旧守卫抓不住 `#site`，新守卫能。

## 6. 验证

```
& 'G:\Git\bin\bash.exe' ./gradlew :app:assembleMobileArm64_v8aDebug \
  :app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.theme.*'
```

- `BUILD SUCCESSFUL in 38s`，exit 0。
- theme 测试结果 XML 聚合：**24 个 suite / tests=189 / failures=0 / errors=0 / skipped=0**。
- `ThemeBaseWiringTest tests="15"`（原 13 + 新增 2），含上述两条新用例。

## 8. 补充：leanback 变体与「failures=1」口径澄清

`app/build/test-results` 下同时存在两个变体的历史结果，聚合时若不加变体目录过滤会串味：

| 变体目录 | 结果文件数 | 时间戳 | 本轮是否运行 |
| --- | --- | --- | --- |
| `testMobileArm64_v8aDebugUnitTest` | 24（theme） | 2026-10-28 16:32 | **是** |
| `testLeanbackArm64_v8aDebugUnitTest` | 733 | 2026-10-28 **04:25**（早于合并 13:41） | 否，为历史残留 |

因此「82 suites / 546 tests / failures=1」是**跨变体混算**的假象；失败项全部落在 leanback 历史结果里
（`MpvFontConfigTest`、`MpvHlsCacheCoordinatorTest`、`NativeEnhancedPlaybackStyleFocusTest`、
`ReaderPlaybackRoutingSourceTest`、`SearchResultDownFocusTest`、`TmdbSourceOnlyInteractionTest`、
`WebThemeTokenSourceTest`），与本轮改动无关。按变体目录收窄后的真实口径：
**24 suites / 189 tests / 0 failures / 0 errors**。

由于 beta 带入的改动包含 leanback 侧文件（`VideoActivity.java`、`activity_video.xml`、
`TmdbEpisodeGridPolicy.java`），另补跑 leanback 打包验证合并未破坏该变体：

```
& 'G:\Git\bin\bash.exe' ./gradlew :app:assembleLeanbackArm64_v8aDebug
```

→ `BUILD SUCCESSFUL in 5m 56s`，exit 0。

## 9. 评审结论

| 轴 | 轮次 | 结论 |
| --- | --- | --- |
| Standards | 第一轮 | 通过（无硬性违规，2 条判断项） |
| Spec | 第一轮 | 需修复（3 项） |
| Standards | 第二轮 | 需修复（仅 3 处 Javadoc 事实陈述，无硬性违规） |
| Spec | 第二轮 | 通过 |
| Standards | 第三轮（定点） | 通过（J1/J2/J3 全部成立） |

最终：两轴均通过，无遗留必修项。

## 7. 交付坐标

- 提交（第一次合并）：`c7200138e58bbc03d4d097ed54075784ec44d983`
- recovery tag：`recovery/C110-BETA-MERGE-DEV1-20261028/20261028085427-c7200138e58b`
- 提交（第二次合并）：`f9ac1bf37ff8321ab8c8cce9ffee21e68ece89ff`
- recovery tag：`recovery/C110-B-NEW-BETA-MERGE-20261028/20261028092110-f9ac1bf37ff8`
- 推送：`origin dev1` → `ce2fcc2095..f9ac1bf37f`；两个 recovery tag 均已推送
- PR：**#498** https://github.com/Silent1566/webhtv/pull/498 （base `beta` ← head `dev1`，中文描述含具体改动内容；只创建不合并）
- 回滚锚点：`169bc59860cc9048cbe9fdce450466ad79ef4547`（合并前 `dev1` HEAD）
- **状态**：全部完成，工作树干净，`origin/beta` 为 HEAD 祖先（left-right = 0/4）

## 10. 第二次合并（beta 再次前进）

第一次合并 `afdb2b92c5` 完成后，`origin/beta` 再次前进到 `93b57d4f23`（PR #497，来自 dev2）。
为使 PR 基于真正最新的 beta，做了第二次合并：

| 项 | 值 |
| --- | --- |
| 合并目标 | `origin/beta` = `93b57d4f23b2200d7759baa39a9f8760ac401767` |
| merge-base | `afdb2b92c5e1e1022883df5f72efaa3daf715e42`（即第一次合并的父提交） |
| 冲突 | **0**（`git merge-tree --write-tree` 预演干净，实际合并亦干净） |
| 带入路径 | 4 个：`ExoCompressedAudioDirectPolicy.java`、`ExoCompressedAudioDirectPolicyTest.java`、`ExoStartupRecoveryIntegrationTest.java`、`docs/C107-audio-direct-startup-stall.md` |
| 与 dev1 改动交集 | **0**（预演阶段逐路径核对，无 OVERLAP） |
| 保真性 | 逐 blob 比对 4/4 一致，`mismatch_count=0`；上一次提交的文件未被触碰 |

带入能力：厂商直通音频在缺少可读缓冲区指标时不再误判为不可直通，恢复 direct audio
（`687ec4af3c`），并补「指标可读转不可读」撤销证据分支的回归用例（`47d7156c5f`）。

验证：`assembleMobileArm64_v8aDebug` BUILD SUCCESSFUL in 3m 59s；
`testMobileArm64_v8aDebugUnitTest` 中 theme **24 suites / 189 tests / 0 failures**、
`player.exo` **82 suites / 639 tests / 0 failures**。

任务守卫以**独立会话** `C110-B-NEW-BETA-MERGE-20261028` 提交（一次守卫会话一个原子变更），
合并暂存的 4 个路径以 `--adopt-dirty` 显式归入本条任务。