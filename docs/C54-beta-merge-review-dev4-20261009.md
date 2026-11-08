# C54-beta-merge-review-dev4-20261009：dev4 合入 origin/beta 最新代码并复评搜索下拉对比度修复

## Recovery anchor

- 目标：把 `origin/beta` 最新代码合入 `dev4`，保留 dev4 上「已提交未推送」的手机版搜索下拉对比度修复（`42b03f028e9`），且不把远端已回退/已删除的内容顺带带回；对合并结果与本地未推送改动循环评审 → 有问题则最小修复并重新验证 → 直到无必修问题；最后原子提交、打 recovery tag、推送 dev4、创建 base=beta 的中文 PR（只创建不合并）。
- 允许路径：`app/**`、`docs/**`、`scripts/**`、`.codex/**`。
- 保护面：任务 guard 启动时工作区干净（预先存在脏路径 0 个）；本分支自身未推送提交 `42b03f028e9` 必须原样保留。
- 验收：① 合并树相对 `origin/beta` 的净差异只含本地既有改动 + 本任务文档；② 零复活（可证伪自检通过）；③ beta 增量零丢失零漂移、dev4 自身改动零改写；④ 双 flavor Java/AndroidTest 编译与双 flavor 全量 JVM 单测零失败；⑤ 收口后推送并以中文 PR 提交到 beta。
- 回滚：撤销本任务合并提交即可回到 `42b03f028e9`（旧 tip 另有备份分支 `backup/dev4-before-beta-merge-202610091605`）。
- 下一步唯一动作：无（已交付）。

## 1. 任务坐标（合入前）

| 项目 | 值 |
| --- | --- |
| 分支 / HEAD | `dev4` / `42b03f028e9b8f29ff6f803571952a82f6d91e7e`（`fix(mobile): 修复深色模式搜索分组下拉面板对比度不足`） |
| 合入时 `origin/beta` tip | `35a5a63f8a526ca9e6a54d1bf59b941d1f03d840`（`Merge pull request #428 from Silent1566/dev1`） |
| 合并基点 | `29d52ab2385702a9350c3099cd94dd4e54c1240f`（`Merge pull request #426 from Silent1566/dev2`，即上一轮交付后的 beta tip） |
| 分支上游 | `origin/dev4` = `61e483011ed`（本轮不动上游配置，仅新增提交） |
| 备份分支 | `backup/dev4-before-beta-merge-202610091605` → `42b03f028e9` |
| 任务守护 | `C54-beta-merge-review-dev4`（mode=standard，scope=`app`、`docs`、`scripts`、`.codex`） |

**beta 在验证期间前进过一次（已按约定重做）**：首轮以 `fe1f725a129`（PR #427 小说朗读）合入并通过验证；提交前最终 fetch 发现 beta 已推进到 `35a5a63f8a5`（PR #428 去广告总时长提示）。于是中止旧合并（`git merge --abort`，未提交、无痕）、以新 tip 重新合入，并对新合并结果**重新执行**全部完整性校验与验证。下文所有数据均取自新 tip 的合并结果。

「已提交未推送」的实测口径：`git rev-list origin/dev4..dev4` 共 6 个提交，其中 5 个已是 `origin/beta` 的祖先（`75735b58f6f`、`c5912111a4a`、`1f8fc83369d`、`7543959f3e0`、`29d52ab2385`），**只有 `42b03f028e9` 不在 beta**。因此本轮真正需要带入 beta 的本地内容就只有这一个提交。

## 2. 合并执行与结果

```text
git merge --no-commit --no-ff origin/beta
→ Automatic merge went well; stopped before committing as requested（0 冲突）
```

| 指标 | 值 |
| --- | ---: |
| 带入的 beta 提交 | 11（PR #427 小说朗读 7 + PR #428 去广告总时长 4） |
| 带入的 beta 路径 | 36（PR #427 的 27 + PR #428 的 9；**删除 0**） |
| 冲突数 | 0 |
| `git diff origin/beta`（权威净差异） | 4 路径（全部为本分支既有改动） |
| 本地改动与 beta 增量的路径交集 | 0 |

## 3. 零复活校验（远端已回退内容不许带回来）

方法：以**当前 `origin/beta` 整树**为唯一准绳，用两层程序化证据 + 一个可证伪自检。

1. **净差异层**：`git diff --name-status origin/beta` 只输出 4 个本地路径（`SearchFragment.java`、`CollectFragment.java`、`SearchScopePopupLayoutTest.java`、`docs/MOBILE-SEARCH-DROPDOWN-CONTRAST-20261009.md`）——合并树 = beta 树 + 本地改动，任何「beta 已删除/已回退但被合并带回」的路径都会在这里暴露。
2. **集合层**：`resurrected = (合并树 − beta 树) ∩ (revert 类提交触及的 725 个路径)` = **0**。
   - revert 集按主题行锚定提取（`^(Revert|revert|回退|撤销|剔除)`，只匹配主题行，避免匹配正文）：109 个提交 / 725 个路径；
   - 路径比对固定 `ref` 后用 `git ls-tree -r --name-only` 取整树做 `comm`（规避 MSYS 把 `ref:path` 的 `/` 当分隔符的假阳性）。
3. **反向丢失层**：`beta 树 − 合并树` = **0 条**，即 beta 现有路径一条都没在合并结果里消失；`dev4 自身 4 路径` 的 `HEAD` blob 与索引 blob 逐一相同（合并未改写本地修复）。

**可证伪自检**：把 5 条确实「在 revert 集且不在 beta 树」的路径（`ISSUE_TEMPLATE`、`RELEASENOTES.md`、`app/src/main/assets/themes/index.json`、`…index.sha256`、`…previews/default.svg`）注入伪造合并树，检测器精确报出这 5 条；真实合并树下报 0。检测器不是空转。

## 4. 零漂移校验（beta 增量逐字节等于 beta）

对 36 条带入路径逐条比较 `origin/beta:path` 与索引 blob（两个 tip 分别校验过一次，均为零漂移）：

```text
identical=36 drifted=0
```

`app/**` 无冲突标记；`git diff --cached --check` 空白错误 0。

## 5. 评审轮次与结论

### 第 1 轮：合并完整性 + 本地未推送改动 + beta 增量集成

| 评审项 | 证据 | 判定 |
| --- | --- | --- |
| 合并是否丢弃/改写本地修复 | 4 路径 `HEAD` blob == 索引 blob | 通过（零改写） |
| 本地修复是否仍对症 | 面板 `ThemeController.current().colorSurfaceContainer()`（`SearchFragment:354`、`CollectFragment:646`），item 文字 `colorOnSurface()`（`:381`、`:673`），同一张日夜表 | 通过（M3 菜单容器 + 其上文字的标准配对） |
| 本地修复是否引入新回归 | 圆角 `dp2px(6)`、`selectableItemBackground` 水波纹、条目高度/宽度计算、`onScopeSelected`/`onGroupFilterSelected` 回调均未改动；两个文件仍在使用 `Color`（`Color.TRANSPARENT`、`Color.WHITE` 图标 tint），无失效导入 | 通过 |
| 自定义主题下会否重现缺陷 | `ThemeResolver.applyProfile()`：`surfaceChanged = hasSurface \|\| hasContainer \|\| hasContainerHigh`，`onSurface` 经 `enforceBase(..., surface, surfaceContainer, surfaceContainerHigh)`，`enforceBase` 对三者均 `ensureContrast(..., 4.5)` → 面板改挂 `surfaceContainer` 后正好落在这道既有的 ≥4.5:1 门内（旧实现写死 `Color.WHITE` 完全不受该门保护） | 通过（修复后比修复前更受约束） |
| beta 增量是否引入新依赖/新权限/新导出面 | 带入路径不含 `build.gradle`/`libs.versions.toml`；`AndroidManifest.xml` 仅新增 `<service android:name=".service.ReaderTtsService" exported="false" foregroundServiceType="mediaPlayback" stopWithTask="true"/>`；`FOREGROUND_SERVICE_MEDIA_PLAYBACK`、`POST_NOTIFICATIONS` 为既有声明（targetSdk=28，无 API 34 FGS 类型缺失风险）；`startForegroundCompat()` 自带 try/catch | 通过 |
| 新增文案是否三语一致 | 新增 13 个 `reader_tts_*` 键在 `values` / `values-zh-rCN` / `values-zh-rTW` 三套均存在，占位符逐一比对全部一致（`%1$d / %2$d`、`%1$d`），无缺失、无错配 | 通过 |
| 新代码是否与本分支改动冲突 | 带入 36 路径 ∩ 本地 4 路径 = 0；双 flavor 编译与全量单测见 §6 | 通过 |

**beta 增量集成评审（PR #428：去广告成功提示补充总广告时长）**：三条 HLS 通道（`M3u8`、`MpvHlsProxy`、`ExoHlsAdblockDataSource`）的提示文案统一收敛到 `HlsAdblockNotice.message(int, double)`；`durationText()` 先把时长四舍五入到 0.1 秒**再**选择单位（59.96 秒 → `1 分钟`，不再出现自相矛盾的 `60.0 秒`），与既有 `AdBlockTimeFormatter.formatSeconds(double)` 的「先归整再分档」一致；`removedDurationSec` 为 0/负/非有限/小于显示粒度时返回空串并回退到片段数，不会显示误导性的 `0.0 秒`。逐项复核实测：`HlsAdblockNoticeTest` 8、`HlsAdblockPipelineTest` 6、`ExoHlsAdblockDataSourceTest` 3 用例在两 flavor 均 0 失败；该增量不改资源文件（无 res 路径），不影响 UI token 基线。判定：无必修问题。

**第 1 轮结论：无必修问题，生产代码零新增改动。**

### 第 2 轮：评审复核（针对第 1 轮结论再走一遍证据）

- 净差异重算：`git diff --name-status origin/beta` 仍为 4 路径（+ 本任务文档），与第 1 轮一致。
- 全量单测第 2 次运行（同一 `BUILD SUCCESSFUL`）计数与第 1 轮一致，且定向类逐个落在 0 失败。
- 本轮新增核对的 4 项（`enforceBase` 触发条件、三语占位符、manifest FGS/权限、`Color` 导入有效性）全部通过。
- 因 beta 在验证期间前进，重做合并后又执行了一轮完整校验（净差异、零复活、零丢失、零漂移、双 flavor 编译与全量单测）与一轮增量评审，结论与首轮一致。
- 复核后未产生任何代码修改，故无需第 3 轮。

**第 2 轮结论：通过，无剩余阻塞项。**

## 6. 验证（终轮，安静环境）

1. **双 flavor Java 编译 + 双 flavor AndroidTest Java 编译**：
   `:app:compileMobileArm64_v8aDebugJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugJavaWithJavac`、`:app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac` → `BUILD SUCCESSFUL`。
2. **双 flavor 全量 JVM 单测**（同一次 `BUILD SUCCESSFUL in 7m 55s`，无并发负载）：

   | flavor | tests | failures | errors | skipped |
   | --- | ---: | ---: | ---: | ---: |
   | mobile | **4971** | **0** | **0** | 2 |
   | leanback | **4131** | **0** | **0** | 2 |

   定向核对（两 flavor 均 0 失败）：`SearchScopePopupLayoutTest`（mobile）4（本地修复的守卫仍生效）、`TtsHttpRuleTest` 15、`TtsTextSplitterTest` 8、`ReaderTtsBridgeSourceTest` 7（PR #427 增量）、`HlsAdblockNoticeTest` 8、`HlsAdblockPipelineTest` 6、`ExoHlsAdblockDataSourceTest` 3（PR #428 增量）、`MpvFontConfigTest` 3、`MpvHlsCacheCoordinatorTest` 13。
3. **UI token 门禁**：`bash scripts/check_ui_tokens.sh --strict` → `UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28`；`violations=1` 为**预先存在**的 `app/src/mobile/res/layout/item_following.xml`，与基线一致，相对基线**零新增违规**（`layouts=385` 未变）。
4. **零复活 / 零丢失 / 零漂移**：见 §3、§4（含可证伪自检）。
5. **良构性**：`git diff --cached --check` 无空白错误；`app/**` 无冲突标记。
6. **设备端**：**未执行**。本次合并相对 beta 的生产代码净差异为 0（只有本分支既有修复与文档），该修复的设备证据见 `docs/MOBILE-SEARCH-DROPDOWN-CONTRAST-20261009.md`；带入的 beta 增量设备证据见 `docs/C52-beta-merge-review-dev1-20261009.md`。不把 JVM 测试描述为设备验证。

## 7. 环境说明（如实记录，不影响被提交内容）

1. **JDK 选择会改变 2 个既有 MPV 测试的结果**。本机 `JAVA_HOME` 现为 `C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`，在该 JDK 下：

   ```text
   MpvFontConfigTest > writeIfChanged_replacesStaleConfigurationAndCleansTemporaryFile  → IOException: Unable to commit fontconfig configuration
   MpvHlsCacheCoordinatorTest > successfulCommitPublishesCompleteFileAndReleasesReservation → assertTrue(commit(...)) 失败
   ```

   根因是生产代码用 `File.renameTo` 覆盖已存在文件，而 Microsoft JDK 21.0.12 在 Windows 上返回 `false`（JBR 21.0.10 返回 `true`）。C49 已记录该差异。切换回项目 provisioned 工具链 **JBR 21.0.10**（`G:/GradleCache/jdks/jetbrains_s_r_o_-21-amd64-windows.2`）后，上述两类连同全量单测全部 0 失败，故 §6 的所有数据都产自该 JDK。

   `MpvFontConfig.java`、`MpvHlsCacheCoordinator.java` 与 beta 逐字节相同、不在本分支改动集内，按 AGENTS.md §2 仅记录不修改（跨 JDK 稳健宜改为 `Files.move(..., REPLACE_EXISTING, ATOMIC_MOVE)`，属独立任务）。

2. **运行参数**：全部 Gradle 调用使用 `--no-daemon`。

## 8. 遗留与边界

- 本轮对 beta 增量的评审按「集成契约」深度进行（路径/内容/依赖/权限/文案/测试），未重做 `docs/C52-beta-merge-review-dev1-20261009.md` 已完成的小说朗读功能实现级评审。
- `check_ui_tokens --strict` 的既存违规 `item_following.xml` 为历史基线，未在本任务修复。
- `File.renameTo` 的跨 JDK 行为差异为环境问题，未在本任务修改生产代码。

## 9. 交付坐标

| 项目 | 值 |
| --- | --- |
| 合并提交 | `08d4b6611802db1f3349aff828df9d08b42c5d69`（第二父 = `35a5a63f8a526ca9e6a54d1bf59b941d1f03d840`，即提交前 `origin/beta` tip） |
| recovery tag | `recovery/C54-beta-merge-review-dev4/20261009083551-08d4b6611802`（annotated，本地对象 `5a643f19ec`，已推送） |
| 推送 | `origin/dev4`：`61e483011ed` → `08d4b661180`（fast-forward）；recovery tag 同步推送 |
| PR | [#429](https://github.com/Silent1566/webhtv/pull/429)（base=`beta`、head=`dev4`、state=OPEN、mergedAt=null、MERGEABLE/CLEAN，**只创建未合并**） |
| PR 文件集核对 | 5 路径，与 `git diff --name-status origin/beta dev4` 及 GitHub compare API 的 `files` 列表三者一致 |
