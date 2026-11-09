# dev2：同步 beta、完整改动评审与发布（2026-11-09）

## Recovery anchor

- 目标：合入最新 beta，评审全部当前修改（含已提交未推送），缺陷修复后验证并复审，通过后提交本任务改动、推送 dev2、创建具体中文 PR 到 beta，最后同步远端。
- 基线：`dev2@21d27de25a90b254791ec70beb35d3735f7f3303`；`origin/beta@a2836ca655d7e42818d237b19d986c34a7a97d27`；`origin/dev2@35d6be11e21ee258c7ba5d520ec872da3d21e908`。工作区初始干净，保护脏路径 0。
- 同步：已 fetch origin；`git merge --ff-only origin/beta` 返回 Already up to date。beta 已在 HEAD 历史内，无新合并提交或冲突。
- 范围：直播 `app/src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java`、`app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvSeekOwnershipIntegrationTest.java` 和本记录；守卫 `beta-review-dev2-20261109 / standard` 已启动。本任务没有新增生产/测试编辑，不把已有直播修复提交说成本次新修复。
- 状态：Standards / Spec 各 run 1/3 全范围通过；协调者独立读取完整报告、核验历史等价与关键真实输入/seek调用链，38 项定向验证全部通过。无新增代码必修，无需人为制造修复或重复构建。仅本记录待原子提交；网络发布尚待执行。
- 回滚：本任务记录以 Task-Guard 对应提交和恢复标签定位，可 git revert；既有直播修复 `21d27de25a90b254791ec70beb35d3735f7f3303` 可独立 revert 回到 beta 基线。不 reset、不强推。唯一下一步：守卫 finish 提交并生成恢复标签，推送 dev2/新标签，创建中文 PR 到 beta，最终同步远端；交付坐标由最终消息提供，不制造第二份记录。

## 决策与计划

用户已授权完整发布流程且要求不再请求审批。使用当前 dev2，不直接修改 beta，不改写历史；仅能证明既往评审覆盖当前全部相关修改且无合并冲突时复用既有覆盖。

时间基准：2026-11-09 13:59 UTC+08:00；核验与评审预计 10 分钟、验证及必要修复 10 分钟、记录与发布 5 分钟，目标约 14:25。完整历史文件覆盖及旧快照替代证明占用了评审阶段，14:19 两份报告已读取；停止可选检查，按已获授权直接闭合发布。

1. 已完成：固定三坐标和初始脏路径，确认 beta 已同步、无冲突。
2. 已完成：两轴分别核对全部 36 个未推送提交及 65 个差异文件；另审本记录，共 66 路径。
3. 已完成：新增直播两文件实审，未发现本任务需要修复的生产缺陷；风险定向验证通过。
4. 已完成：协调者独立读取关键差异、两份完整报告、测试输出/XML，并逐条验收。
5. 待发布：守卫 finish 原子提交本任务记录及 annotated recovery 标签；推送当前 dev2 与新标签；创建/更新 dev2 → beta 中文 PR。
6. 待同步：最后 fetch/pull 当前分支，并核对最新 beta；如新增代码或冲突，重新核验受影响增量，不沿用旧结果声称新树通过。

## 固定审查点与具体改动

- 本轮开始时 PR 新增：`git diff a2836ca655d7e42818d237b19d986c34a7a97d27...HEAD`，直播源码与测试两文件，161 insertions / 7 deletions。
- 唯一新增代码提交：`21d27de25a90b254791ec70beb35d3735f7f3303`，`fix(live): keep remote line navigation after playback failure`。
- 普通直播左右键按 `playbackCatchup` 业务模式切换线路，不依赖播放器临时 `isLive()`。加载中、失败/停止、无播放规格、未知时长或类似点播时长，仍能手动换线并重置耗尽回退，不发起 seek 或错误显示预览。
- 回看 `canSeek()` 保留 service、生命周期、退出、redirect、owner、非 live、非 empty 守卫，新增业务回看模式、真实正时长及 `COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM` 资格；`onSeeking()` 复用同一门，资格丢失不误切普通直播线路。
- 未修改 `CustomKeyDownLive/CustomKeyDownVod` 的 250ms 延迟提交、DOWN/UP/device/downTime 配对、PlaybackTrace 二次校验及取消状态机；可见控制栏/列表仍把方向键交回焦点处理。
- 集成测试新增 9 例：普通未知时长、停止/失败、无播放规格、点播式时长、合法回看、未知回看、无 seek 命令回看、可见控制栏及单线路；原 Cast/Video 5 例保留。使用真实 Activity/PlayerManager/Channel/按键分派与 PAUSED Looper，测试 Player 仅控制时长/命令能力；不伪称真正引擎 seek 或设备生命周期已验。
- 本任务新增本评审记录，不修改其他历史文档，不新增功能、依赖、native 或无关重构。

## 全部未推送覆盖与可复用依据

本轮开始 `origin/dev2..HEAD` 共 36 提交：35 个已进入固定 beta，1 个为直播修复；但“已 beta”本身不是评审证据。完整差异为 65 文件（50 源码/测试/资源 + 15 文档），其中 63 文件与既往实质评审正式提交等价，新增 2 文件本次实审；本记录另列第 66 路径。两轴完整文件/提交表均无未映射差集。

| 覆盖集合 | 实质评审记录与可达正式提交 | 本次核验 |
| --- | --- | --- |
| C123/C124 的 22 源码/测试、9 文档 | `docs/C123-upstream-sync-silent1566.md` 与 `docs/C124-beta-merge-review-dev1-20261109.md`；正式 C123 提交 `b268b4ff37418940125b2b17b8ca4204194cf518`，C124 文档提交 `ed2a5035ab355cea3de76c850ea66af7255ed8d4` | 当前 app/quickjs 排除本次 2 文件后 diff exit 0；原文档等价，新增两文件本次实审 |
| 字幕/播放器/语言 19 文件 | `docs/C122-beta-merge-review-dev4-20261108.md` 及 SUB-AUTOLOAD/SUB-MATCH 两任务；`88ea7f4242219abd7c647a69c525f10443651d8d` | 两轴逐组 diff / blob 等价，协调者独立核验字幕源码/测试无差异 |
| 首页焦点 4 文件 | `docs/C121-beta-merge-review-dev3-20261108.md`；`3b4b6d3e47991c053434cdc3785f82c8e5595852` | 两轴核验原文件 blob 等价及历史最终复审 |
| 内联加载/OSD/XML 3 文件 | `docs/C121-beta-merge-review-dev1-20261108.md`；`d10b1112965f596d3c0e8a9531d29ee7310d35f5` | 两轴核验原文件等价与最终评审 |
| 主题设置/测试 2 文件 | C121-dev1 第 10 节；`79e3d238390d8f2029df8362de3be49fb1e81152` | 两轴核验原文件等价及默认暗色合同 |
| 全部 15 原文档 | C124 九文档补审、C121/C122及字幕任务末态实审；`ed2a5035ab355cea3de76c850ea66af7255ed8d4` | 协调者 `git diff --exit-code ed2a5035ab... HEAD -- docs` exit 0；两轴映射覆盖 |

协调者独立证据：`git diff --exit-code ed2a5035ab355cea3de76c850ea66af7255ed8d4 a2836ca655d7e42818d237b19d986c34a7a97d27` exit 0，证明 C124 正式记录提交与本轮 beta 整树等价；当前 app/quickjs 除新增两路径外与 ed2a 的 diff exit 0。未把子代理自报作为唯一依据。

旧 `0faa7a140468d0b0e3bb0994cd25cd1400a64719` 临时树在本 checkout 实际返回 `fatal: bad object`；已交两轴改用可达正式提交、tracked 实质评审和逐组等价补证闭合。不声称本次读取不存在的 C123/C124 ignored 旧报告或历史 XML，不联网恢复悬空树。

## Standards

- 结论：PASS；硬规范违规 0、Fowler 基线 smell 0、覆盖缺口 0。
- 子代理 `1e2cd293-c8f5-44a0-8a57-a63819a0d30c`，run 1/3；完整报告 `app/build/beta-review-dev2-20261109-standards.md`，225 行，65 文件覆盖表及本记录单列。
- 协调者完整读取后核验新增 LiveActivity 门控/预览共享、测试真实行为断言、历史差异等价，接受结论。

## Spec

- 结论：PASS；需求遗漏 0、越界行为 0、实现错误/新增 material regression 0、开放证据缺口 0。
- 子代理 `d82693e1-7539-4f0f-86d2-99ccb9640c6d`，run 1/3；完整报告 `app/build/beta-review-dev2-20261109-spec.md`，含全部 36 提交和 66 路径映射。
- 协调者完整读取并独立核验 `playbackCatchup` 赋值、正常/失败普通直播换线、回看资格、CustomKeyDown 的真实即时 fallback/延迟取消、PlayerManager 原始时长语义，接受结论。无生产新编辑，因此没有需要另行复审的代码增量。
- 新修复规格来自已有 `docs/C-TV-SEEK-OWNERSHIP-tv-keys.md`、提交目的与新增行为测试；没有找到独立原始 issue，且无 `docs/agents/issue-tracker.md`，不虚构、不安装无关配置。

两轴均 `deepseek-official/deepseek-v4.1-flash`；路由工具没有 allowlist 状态标注，限制状态未确认，不称已授权或不受限。子代理只写各自 ignored 报告，生产/测试/tracked 文档编辑和构建均为 0；协调者独占本记录与独立验收。缺陷修复简报 run 0/3，未发现需要派发实施的问题。

## 定向验证（本次实际执行）

| 测试 | 本次用例 | 失败/错误/跳过 | XML 时间 |
| --- | ---: | --- | --- |
| `com.fongmi.android.tv.ui.activity.TvSeekOwnershipIntegrationTest` | 14 | 0 / 0 / 0 | `2026-11-09T06:07:38.611Z` |
| `com.fongmi.android.tv.ui.custom.CustomKeyDownSeekOwnershipTest` | 17 | 0 / 0 / 0 | `2026-11-09T06:09:22.239Z` |
| `com.fongmi.android.tv.player.engine.TimelineStreamKindPolicyTest` | 7 | 0 / 0 / 0 | `2026-11-09T06:11:58.297Z` |
| 总计 | 38 | 0 / 0 / 0 | 两阶段输出/XML 均已在运行后实际读取 |

- 命令任务：`.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests <上表实际类> --console=plain`。实际编译 leanback production/unit Java，非仅文本校验。
- 首次 Gradle BUILD SUCCESSFUL in 6m 33s、exit 0；日志 `app/build/beta-review-dev2-20261109-tests.log`。第三筛选最初误写为 `com.fongmi.android.tv.player.TimelineStreamKindPolicyTest`，Gradle 成功但该类未执行；协调者 XML 计数核验发现，不以成功退出码冒称38项执行。
- 按真实 package 仅补跑遗漏7项，BUILD SUCCESSFUL in 35s、exit 0；日志 `app/build/beta-review-dev2-20261109-timeline-tests.log`。前31项未重复运行。
- 补跑替换了 test-results 目录，首次两类 XML 已在首轮结束后实际读取计数，不声称当前三个 XML 同时存在。此表保留实际解析结果。
- 新代码差异 whitespace 校验通过；本记录最终 whitespace/覆盖表校验由发布前步骤执行，守卫 finish 负责最终范围、暂存、原子提交和标签检查。

## 风险及处理

1. 全部36未推送与PR仅1代码提交范围不同：已通过两轴65+1路径完整映射和协调者等价核验闭合，没有漏掉历史文件。
2. 旧临时评审树缺失：已交两轴补证并采用可达提交+tracked 实质评审，替代链已独立核验。
3. 测试筛选包名错误：XML实读发现后仅补漏7项并通过，未弱化门槛，也未重复已有成功验证。
4. C123旧 SpiderCleanup 诊断异常、C121既有P3/滚动夹具未验：原用户已明确范围外，当前有关文件未变，沿用既有排除，不把它们说成本次已修复。
5. 真机遥控、实际Exo/MPV回看seek、真实直播网络、移动完整UI、QuickJS/Python真桥、native/全ABI/APK未在本次验证；本次风险定向单测与源码审查不等于这些场景验收。
6. 最终同步若出现新代码或冲突，本固定HEAD验收对该增量失效，需重新核验；不直接合并PR、不强推、不删除分支或工作区。
