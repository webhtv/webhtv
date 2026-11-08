# C46：dev1 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入 `dev1`（**远端已移除/回退的提交不得顺带带回**）；复评 dev1 全部已修改代码（含已提交未推送的 `56ebd07f1`）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev1`、创建 `dev1 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① `origin/beta` tip 已完整包含在 dev1 祖先中；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev1 既有改动**零丢失**；④ 双 flavor Java 编译通过；⑤ 双 flavor 全量 JVM 套件零失败；⑥ UI token 门禁相对基线零新增违规；⑦ 净差异只含本分支自身改动；⑧ 提交 + recovery tag；⑨ `dev1` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并无需操作（`origin/beta` 已是 dev1 祖先）；4 轮评审完成——第 1 轮发现并修复 1 处真实缺陷（C45 遗漏删除 `scripts/pull-ad-audio-log.sh`），第 2 轮发现并修复 1 处真实缺陷（C45 误删仍在用的 PCM Hub 架构决策），第 3/4 轮为订正我自己新增文档表述并复评通过；全部验证通过。
- **交付坐标**：见文末「交付坐标」。
- **下一动作**：无（任务已收口）。

## 时间与设备

- 开始时本地时间：2026-10-08 16:07（Asia/Shanghai）。
- 设备：`192.168.50.3:5555`（dev1 分配机位），包 `com.silent.android.webhtv`；未使用其它工作区机位。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1` |
| 任务开始时 HEAD | `56ebd07f10529ce014a20c43289ee34feb3b5892`（`移除：删除音频指纹去广告与语音广告识别功能，向 Silent1566 上游靠齐`，领先 `origin/dev1` 1 个提交） |
| `origin/beta` tip | `d8daedf86c57d58c18823343d8fc6510319289b9`（Merge PR #418 from dev1） |
| 合并基点（merge-base） | `d8daedf86c57d58c18823343d8fc6510319289b9` |
| 合并方式 | **无需合并**：`git merge-base --is-ancestor origin/beta dev1` 成立，`git rev-list --count dev1..origin/beta` = 0 |
| 合并结果 | 无冲突（无合并操作） |
| 初始脏路径 | 无（`git status` 干净） |
| 回滚锚点 | `56ebd07f10529ce014a20c43289ee34feb3b5892` |
| 任务守卫 | `C46-beta-merge-review-dev1`（standard，scope `app/src` + `docs` + `scripts` + `.codex/scripts` + `.codex/task-state`） |

### beta 增量 ledger

`git log --oneline dev1..origin/beta` **为空**，`git rev-list --count dev1..origin/beta` = **0**。

即：本次任务启动时，`origin/beta` 的全部提交（含最新的 `d8daedf86` Merge PR #418）**已在 dev1 祖先中**，不存在待合入的 beta 增量，也不存在丢失风险。dev1 相对 beta 的全部差异就是 C45 这一个提交：

```text
git log --oneline origin/beta..dev1
56ebd07f1 移除：删除音频指纹去广告与语音广告识别功能，向 Silent1566 上游靠齐
```

`git log -1 --format='%P' 56ebd07f1` = `d8daedf86`，即 **dev1 = origin/beta tip + C45 单个提交**，线性关系，无交叉合并。

## 用户核心关注点：远端已移除（回退）内容零复活

本次采用**三层递进证据**，不依赖人工目测。

### 第一层：结构性证明（最强证据）

`dev1` 与 `origin/beta` 的差异路径集与 C45 提交的改动路径集**完全一致**：

```text
git diff --name-only origin/beta dev1 | sort > /tmp/d1.txt   # 120 行
git show --name-only --format= 56ebd07f1 | sort > /tmp/d2.txt # 120 行
diff /tmp/d1.txt /tmp/d2.txt → 无差异（IDENTICAL）
```

因此可做逐 blob 比对：**C45 改动集之外的 4935 个文件，dev1 与 beta 的 blob 哈希 100% 相同**：

```text
dev1 文件总数                              : 4956
C45 改动集之外的文件数                     : 4935
其中与 beta blob 不同的文件数              : 0
```

**结论**：dev1 不可能从 beta 之外引入任何内容，因为除 C45 自身改写的 21 个路径外，其余 4935 个路径与 beta 逐字节相同。

### 第二层：行级复活扫描

对 beta 历史上每个**单亲 revert/移除/删除类提交** `R` 取父 `R^`，得到 `removed(R,f) = lines(R^:f) − lines(R:f)`；复活定义为「该行出现在 dev1 中，且该行的来源提交 `R` 之后在 beta 上从未重新引入」。

```text
candidate single-parent revert/removal commits : 22
file-versions checked                          : 98
binary/unavailable skipped                     : 7
removed lines scanned                          : 1686
resurrected lines (原始命中)                   : 10
```

这 10 行经逐一甄别**全部为误报**，原因是 beta 自身在后续提交中合法重新引入了它们：

| 命中行所属文件 | 判定 |
| --- | --- |
| `ThemeBaseWiringTest.java`（`fd29d76ee`） | beta tip 中该文件存在且与 dev1 **逐字节相同** |
| `TmdbDetailActivity.java`（`7e42054de`） | beta tip 中该文件存在且与 dev1 **逐字节相同** |
| `BuiltinHlsRuleFixtureTest.java`（`2ea862ed6`） | beta tip 中该文件存在且与 dev1 **逐字节相同** |

三个文件在 beta tip 与 dev1 中均**完全相同**，说明这些行是 beta 自己在 revert 之后重新引入的合法内容，不是 dev1 带回来的。第一层结构性证明已从根上排除了「dev1 带回 beta 已删内容」的可能。

### 第三层：净差异行级核对

```text
dev1 与 beta tip 共有的文件数                        : 4955
dev1 独有、beta tip 中不存在的非空行                  : 20
```

这 20 行**全部**是 C45 自身的改写行（`Backup.java` 3 行、`PlayerManager.java` 1 行、`PlaybackMediaSignalHub.java` 1 行、`RealtimeSubtitleRecognizer.java` 8 行、三套 `strings.xml` 的 `setting_ad_summary` 3 行、`PlaybackMediaSignalHubTest.java` 3 行、`settings-classification-review.md` 1 行），无一来自 beta 历史。

**关键目标提交 `5682f2b05`「剔除 PR #353 主题系统改动」**：该提交位于分支 `remove-pr353-theme-changes`，`git merge-base --is-ancestor 5682f2b05 origin/beta` → **否**，即它从未进入 `origin/beta`。第一层证明已覆盖：该提交触碰的路径若不在 C45 改动集内，则 dev1 与 beta 逐字节相同；若在改动集内，则属于 C45 自身删改（C45 只删 ad/audio 与语音相关文件，与主题系统零交集）。

**结论：零复活。**

## 复评记录（4 轮）

### 第 1 轮：C45 遗漏删除 `scripts/pull-ad-audio-log.sh`

**发现**：对 C45 改动集做「功能层残留引用」扫描时，`scripts/pull-ad-audio-log.sh` 被命中。逐行判定该脚本：

- 7 行命中 `speech_ad` / `ad-audio` / `AD_AUDIO`（全脚本共 53 行）；
- 抓取的偏好键 `speech_ad_rules_v1` / `speech_ad_rules_source` / `speech_ad_builtin_enabled` 在 dev1 源码中引用数均为 **0**（已随 C45 删除）；
- 依赖的 10 个诊断标记（`ad-audio`、`refresh skipped`、`refresh ineligible`、`activated speech`、`speech pcm`、`speech fed`、`prompt shown`、`SPEECH_TEXT_EMPTY`、`SPEECH_MODEL_UNAVAILABLE`、`pipeline rebuild`）在 dev1 源码中命中数**全部为 0**；
- 上游 `Silent1566` 的 `scripts/` 目录中**不存在**该文件；
- 无任何其他文件或 CI 工作流引用它。

**判定**：该脚本是只服务于被删功能、且依赖已全部失效的孤儿脚本。C45 的验收标准第 2 条明确要求「无悬空引用」，且目标是「向 Silent1566 上游靠齐」。保留它属于 C45 的完成度缺口。

**修复**：`git rm scripts/pull-ad-audio-log.sh`（−53 行）。

### 第 2 轮：C45 误删仍在使用的 PCM Hub 架构决策

**发现**：核对 `智能去广-设计文档.md` 时发现 C45 声明的「决策 F 改写为『实时字幕等 PCM 消费者』」**实际执行成了整节删除**。而该节描述的架构在 dev1 代码中**仍然真实存在并正在工作**：

| 断言 | 证据 |
| --- | --- |
| `PlaybackMediaSignalHub` 仍存在 | `app/src/main/java/com/fongmi/android/tv/player/audio/PlaybackMediaSignalHub.java`（`PipelineLease` 出现 5 次、`generation` 出现 8 次） |
| `PlayerManager` 仍持有该 Hub | `PlayerManager.java:191` `private final PlaybackMediaSignalHub mediaSignals = new PlaybackMediaSignalHub(8);`；`:750` `public PlaybackMediaSignalHub mediaSignals()` |
| 实时字幕是现存生产 consumer | `RealtimeSubtitleController.java:118` `hub.requestCapture(ConsumerKind.REALTIME_SUBTITLE)`；`:138` `hub.register("realtime-subtitle", ...)` |
| 重建链路仍走该 Hub | `PlayerManager.rebuildAudioPipeline()` 调用 `mediaSignals.detachPipeline()`；`RealtimeSubtitleController:777` 依赖 `hub.isPipelineAttached()` 决定是否重建 |

**判定**：C45 的删除范围是「音频指纹去广告 + 语音广告识别」两个功能。PCM Hub 的所有权模型（`PipelineLease` 独占、generation 门控、引用计数租约）是**实时字幕依赖的保留能力**，不是被删功能的一部分。整节删除造成设计文档丢失了一项**仍在生效**的架构决策，属信息损失。

**修复**：以「决策 E」恢复该节（因原「决策 E（复用 PCM + Sherpa-ONNX 语音去广）」已随功能删除，编号顺延），内容改写为以实时字幕为现存唯一 consumer，并显式记录 `AD_AUDIO` consumer 已移除、Hub/`PipelineLease`/generation 门控本身保留。

### 第 3 轮：订正我自己在第 2 轮引入的表述

第 2 轮修复时我额外恢复了 beta 的「当前明确不承诺」整节。复评时逐条核对，发现该节 4 条**全部**指向被删功能：

```text
- 不自动跳过，不把单次音频匹配视为可信执行指令；      → 音频指纹/语音去广专属
- 不以音频指纹或语音识别替代 HLS、URL、WebView 等…； → 音频指纹/语音去广专属
- 不内置、下载或众包大规模指纹库；                    → 音频指纹专属
- 不接入第三方 H5/SDK 的远程协议。                    → 原决策 G 签名规则包专属
```

**判定**：C45 删除该节是**正确的**，我在第 2 轮的恢复属超范围。第 3 轮撤销该恢复，并同步订正 C45 文档中相应的描述。

### 第 4 轮：最终复核

对全部改动重跑静态核对，结论：净差异只含本分支自身改动（21 个路径），零复活仍成立（改动集外 4935 个文件 blob 与 beta 100% 相同），无冲突标记，无空白错误。

## 改动清单（本次 C46 相对 C45）

| 路径 | 类型 | 说明 |
| --- | --- | --- |
| `scripts/pull-ad-audio-log.sh` | 删除（−53） | 第 1 轮：C45 遗漏的孤儿脚本 |
| `智能去广-设计文档.md` | 修改（+9） | 第 2 轮：恢复「决策 E：管线所有权与业务启停分离」 |
| `docs/C45-remove-ad-audio-fingerprint-speech.md` | 修改（+4 −1） | 记录上述两项复评结论 |

**生产代码零改动**：`app/src` 下本次无任何新增/修改/删除，净差异与 C45 提交后完全一致。

## 验证

1. **编译**：`bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac` → `BUILD SUCCESSFUL`（EXIT=0）。
2. **全量单元测试**：`bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest :app:testMobileArm64_v8aDebugUnitTest` → `BUILD SUCCESSFUL`（EXIT=0）；解析 JUnit XML：leanback **4039** tests / mobile **4885** tests，failures + errors 均为 **0**，skipped 各 2（与 C45 基线一致）。
3. **UI token 门禁**：`bash scripts/check_ui_tokens.sh` → `UI_TOKEN_STATUS PASS`（EXIT=0）；`violations=1`，该 1 项为预先存在的 `item_following.xml`，**不在本次改动集内**，与基线一致 → 相对基线**零新增违规**。C45 未新增/删除任何 layout/drawable/color 文件，故门禁计数与 beta 一致。
4. **零复活程序化校验**：见上文三层证据（结构性证明 + 行级扫描 + 净差异核对），resurrected = 0。
5. **残留引用扫描**：`app/src`、`scripts`、`gradle`、`.github`、`.codex` 下对 `AdAudio|SpeechAd|AudioFingerprint|ProbeRule|AdSkipCoordinator|AdSkipPolicy|SpeechRecognitionFactory|AdSkipPromptPresenter|ad_audio|speech_ad|ad-audio|speech-ad` 的引用数为 **0**（`RealtimeSubtitleController.readAudioHeadroomUs` 为大小写不敏感的误报；`graphify-out/` 为已 gitignore 的生成缓存，非源码）。
6. **孤儿检查**：C45 删除的 99 个文件中，除两个预先存在的历史记录文档（`THEME-COLOR-E-20260908-tv-catalog.md`、`BETA-SYNC-DEV3-20260927.md`，均不在改动集内，按 AGENTS.md §2 仅记录不修改）外无任何引用；`ad/` 目录已不存在；无空目录残留。
7. **良构性**：两个 `*_setting_ad.xml`、三套 `strings.xml` XML 解析通过；`git diff --check`（含 `--cached`）无空白错误；无冲突标记。
8. **文档与代码一致性**：设计文档新增「决策 E」的每条断言均经代码实测（见第 2 轮表格）。
9. **设备端**：见「设备验证」节。

## 设备验证

设备 `192.168.50.3:5555`（Android 14 / sdk 28，mobile），包 `com.silent.android.webhtv`。

| 项 | 命令/方法 | 结果 |
| --- | --- | --- |
| 打包 | `bash scripts/build_arm64_debug_install.sh --flavor mobile --serial 192.168.50.3:5555` | `BUILD SUCCESSFUL`（EXIT=0），APK 196 MB |
| 覆盖安装 | 同上（未卸载） | `Performing Streamed Install` → `Success`；包存在校验通过 |
| 冷启动 | `am force-stop` + monkey LAUNCHER | 进程存活（PID 6444） |
| 崩溃扫描 | `logcat -b crash` + 全量 logcat grep | `FATAL EXCEPTION` / `ClassNotFoundException` / `NoClassDefFoundError` / `InflateException` **均为 0** |
| 首页渲染 | `screencap` | 正常（1920×1080，581170 种颜色），无白屏/错位 |
| 设置页摘要 | `screencap` | 「去广告」摘要 = **「总开关、规则、片头片尾」**（不含音频识别） |
| 去广告子页 | `screencap` | 仅三组：**基础**（去广告总开关=开 / AI 智能去广=开）、**规则与统计**（广告规则管理=1 条规则·57 条待审核 / 广告拦截统计）、**片段跳过**（自动跳过片头片尾=自动跳过 / 跳过的片段类型=前情回顾/片头/片尾）；**无任何音频识别或语音广告项** |
| 偏好键 | `run-as cat shared_prefs/..._preferences.xml` | `ad_audio*` 计数 **0**、`speech_ad*` 计数 **0**；`adblock`、`ai_ad_detection`、`auto_skip_intro_outro` **均存在**（既有设置未被破坏） |

**说明**：本次 C46 相对 C45 **零生产代码改动**，故设备证据用于确认 C45 交付物在最新合并基线上仍成立，而非验证新行为。

## 交付坐标

| 项 | 值 |
| --- | --- |
| 任务起始 HEAD | `56ebd07f10529ce014a20c43289ee34feb3b5892` |
| `origin/beta` tip | `d8daedf86c57d58c18823343d8fc6510319289b9` |
| C46 改动 | 4 路径（`app/src` 零改动） |
| 提交 | `cd88c907d912d6f728939d6e5c102fb5f9bb655e` |
| recovery tag | `recovery/C46-beta-merge-review-dev1/20261008164917-cd88c907d912` |
| 推送 | `dev1` → `origin/dev1`，0 ahead / 0 behind |
| PR | [#420](https://github.com/Silent1566/webhtv/pull/420) `dev1 → beta`，**OPEN、未合并**（`merged=false`、`merged_at=null`）、MERGEABLE，122 文件 +323 −24844 |
| PR 文件集校验 | `gh api .../pulls/420/files` 分页合计 **122**，与 `git diff --name-only origin/beta dev1` **逐项一致** |

## 备注

- 用户既有偏好中残留的 `ad_audio*` / `speech_ad*` 键不再被任何代码读取，也不会再参与备份；未主动清理，以免扩大变更面（沿用 C45 的处置）。
- `PlayerManager.java` 的 `com.fongmi.android.tv.player.exo.TrackUtil` 未使用 import 在 beta tip 中已存在（`git blame` 指向 `daaa0a80a`），**非本次引入**，按 AGENTS.md §2 仅记录不修改。
