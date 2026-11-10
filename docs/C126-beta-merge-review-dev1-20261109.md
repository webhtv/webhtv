# C126 — dev1 设置页间距、beta 增量同步与发布评审

## Recovery anchor

- 目标：合入最新 origin/beta，覆盖全部当前修改（含已提交未推送），发现问题后修复、验证、复评；通过后提交本任务改动与恢复标签，推送 dev1，创建具体中文 PR 到 beta，最后同步远端。不强推、不改写历史、不直接合并 PR。
- 初始坐标：dev1@336b9f728fb7f00fd8659dee1857f980b1b415cc；origin/dev1@7c654ecb757940afc6c4005d65f0b206ec36ca68；fetch 后 origin/beta@863cc4904491da1ec7ab7dcbb3d18772133e76e0。初始工作区干净，保护脏路径 0。
- 合并：eced786c9f666b07f8f83ed01c1512f6d3633e6b，ort 无冲突；双父为上述初始 dev1、beta。相对已评审 7c654ecb75 增量 4 文件，251 insertions / 7 deletions。实际 PR 源码新增仅 activity_setting.xml 的一行属性；直播源码、测试及 dev2 记录来自 beta，不冒称 dev1 本次新功能。
- 边界：C126 / standard 守卫从合并 HEAD 启动，仅允许本文件。没有生产代码/测试编辑；未发现需要启动的代码修复，不扩大到依赖/native、设备矩阵或历史无关缺陷。
- 状态：Standards run1/3、Spec run3/3 均无开放缺陷；当前树定向验证 run1/3 通过；协调者已直接核验日志、三测试 XML 和三文件 SHA-256，两轴最终记录核验一致。零开放缺陷，进入守卫 finish 与发布。本任务提交、恢复标签、推送、PR 与最终拉取的真实坐标在交付消息报告，不制造第二个闭合记录提交。
- 回滚：本任务文档提交可按 Task-Guard: C126 与 recovery/C126 标签定位后 git revert；间距提交 336b9f728fb7f00fd8659dee1857f980b1b415cc 可独立 revert。合并保留双父历史，不 reset --hard。唯一下一步：收取 Standards 和最终记录收口，零开放缺陷后守卫 finish、推送、中文 PR、最终同步。

## 决策与执行计划

用户已明确授权合入 beta、循环修复/验证/复评、提交推送、中文 PR 与最终拉取，并要求不再请求审批。继续使用当前 dev1 与 origin（Silent1566/webhtv）；不创建无关分支，不修改远端 beta，不制造空代码提交。按这些明确决策继续，不重复询问批准。

已确定：origin/beta 为合入目标及 PR 基线；未推送提交 336b9f728f 也须覆盖；无冲突且既有完整覆盖的代码允许复用。有限规格来源使用提交目的/Verification 与既有任务文档，不把作者自述说成独立产品 issue。

时间基准：2026-11-09 15:59 UTC+08:00，Asia/Shanghai；合并预计 1 分钟、双轴评审 6 分钟、必要修复/编译验证 12 分钟、复审 4 分钟、发布同步 4 分钟，目标约 16:30。16:10:49 已从合并 HEAD 启动守卫。验证于16:19完成，16:23协调者实物核验完成；停止可选构建/检查，只做剩余必需评审与发布。估计只约束执行目标，不降低验收门禁。

1. 已完成：fetch origin，固定三个初始坐标和脏路径，合入 beta 并确认无冲突。
2. 已完成 Spec、Standards 待结论：两轴核查 7c654ecb75...HEAD 的四文件增量，复用 C125 未变覆盖；Spec 四项疑点经合同复核撤回，不为猜测修改安全门。
3. 已完成：当前合并树 leanback 38 项定向单测与设置页 XML 断言；不重复插件设备用例或全 ABI 构建。
4. 进行中：协调者实际读取报告、关键调用链、日志与 XML；最终记录交两轴只审文档/证据增量，未变源码与成功测试不重复。
5. 待发布：零开放缺陷后守卫 finish 原子提交本记录并创建 annotated recovery 标签；只推送当前 dev1 和本次新标签；创建具体中文 dev1 → beta PR。
6. 待同步：最后 fetch / pull 当前分支并核对 beta；新源码或冲突使对应增量验收失效，须补验后再宣称当前树通过。

## 具体改动与覆盖

- 本地未推送代码：336b9f728fb7f00fd8659dee1857f980b1b415cc，`fix(tv): align source settings heading spacing`；仅在 app/src/leanback/res/layout/activity_setting.xml 源设置首行增加 android:layout_marginTop="16dp"，令四组标题至首行间距一致，首标题原 layout_marginTop="0dp" 不变。该提交自述 XML 红绿与单属性 diff 已验证；本次另检查实际 XML，不将自述替代本轮证据。
- beta 带入：21d27de25a90b254791ec70beb35d3735f7f3303，普通直播按 playbackCatchup 业务模式响应左右换线，失败/停止/加载/未知时长仍能换线；回看 seek 同时要求所有权、生命周期、正时长、COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM；onSeeking 共享资格门。涉及 app/src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java、app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvSeekOwnershipIntegrationTest.java，另带入 docs/beta-review-dev2-20261109.md。
- 新增 9 个直播集成用例覆盖未知/停止/加载/点播式时长直播、有效/未知/无命令回看、可见控制栏与单线路；真实 Activity/PlayerManager/Channel/按键分派，测试 Player 只控制时长/命令资格，LiveViewModel.getUrl 为空实现，不冒充真实失败网络验收。
- 既有插件范围：docs/C125-beta-merge-review-dev1-20261109.md 及本地 app/build/c125-standards-review.md、c125-spec-review.md 最终 run3 覆盖 11 源码/测试文件、H1 动态主题与 A1 无效输入保留修复，以及双变体编译和 5 个 Leanback 模拟器用例。该范围相对 7c654ecb75 未变；协调者实际 diff 返回0，不重复插件历史构建。插件已由 PR #549 带入 beta。
- 协调者独立核验 LiveActivity:989/1002 模式赋值、:1277-1292 换线/回退复位、:1355-1372 seek资格；CustomKeyDownVod:169-171 无seek资格触发 Live fallback，CustomKeyDownLive:100-103 委托左右导航。未知时长的回看不发起非法seek，也不误切普通直播线路。
- 本任务仅新增本记录。初始简报误写 Java 为 main/java 和 androidTest/java，已按 git diff --name-only 纠正；协调者一次输入消费者路径批次同样纠正到实际 main PlaybackActivity、leanback CustomKeyDown*。调用路径错误不视为代码缺陷，没有改生产代码来迁就检查器。

## 两轴评审与运行台账

### Standards

- 子代理41734e14-9613-4cb5-acf7-ead6da4d9b75，run1/3；报告 app/build/c126-standards-review.md；硬规范违规0、Fowler基线smell0、无阻断缺陷，依据AGENTS.md最小范围与保留行为条款，不以"可更优雅"判违规。
- 协调者已直接读取完整报告：确认其覆盖四文件及本记录，且实际读取本轮日志末尾exit0与三XML suite 14/17/7、失败/错误/跳过均0；命令15次（含一次grep超时后改原生read），仅写自身ignored报告，构建/Git写/设备0次。未确认真机焦点与真实Exo/MPV回看，未据此认定代码缺陷。

### Spec

- 子代理5f61f88f-6a1d-499c-aee7-7a93b9d55a16，run1/3 初报(a)2/(b)1/(c)1，同时称无阻断；协调者拒绝矛盾分类，带既有合同和源码实证回派run2/3。
- run2/3：缺失0、scope creep0、实现错误0、真实开放缺陷0。报告 app/build/c126-spec-review.md 保留首轮历史及四项撤回理由；协调者已直接读取完整修订报告，不仅采信子代理结论。
- A1 回看失败不换线：合同 docs/C-TV-SEEK-OWNERSHIP-tv-keys.md:20/51 只要求普通Live即时切线，回看仅具备资格时seek；不存在回看失败必须切普通线路的原文，撤回标题推定。
- A2 未真机验：合同:106 明确无实机不能称全产品完成，后续:141/149/172 记录窄路线和未验边界；不是本轮生产遗漏，保留边界。
- B1 命令资格收紧：合同:48/51 允许当前媒体seek能力门；历史:142因未prepare夹具曾移除门，但本次直播记录:29和测试:172-205具有有效/无效能力的实质证据，不是无需求扩张。
- C1 瞬时门误用：seek能力安全门与普通直播业务导航判据不混用。PlayerManager:729-733第三态注释不证明未就绪应seek，无可达回归证据，撤回猜测；真实引擎实机仍未验。
- Spec已审本记录初稿，未发现PR范围/运行声明错误；本次实测更新只补审文档/证据，不重审已通过源码。

### 验证及修复

- 验证子代理5ca4a55c-95a1-44ad-9b06-075b16682a8b，run1/3；报告 app/build/c126-verification.md，日志 app/build/c126-targeted-tests.log。原生pwsh7/20，Gradle1/3，手工ignored文件2/2，生产/测试/tracked文档编辑0、Git写0。
- 缺陷修复run0/3，未发现必须派发的生产修复；不是修复后重新构建的PASS，不制造空代码提交。每brief最多首次加两次回派，累计不重置。
- 路由工具仅列deepseek-official/deepseek-v4.1-flash，没有授权或无allowlist注解，限制状态未确认；验证显式路由，初始评审默认路由，不据此声称per-task routing授权。

## 当前合并树实物验证

实际命令：`.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.activity.TvSeekOwnershipIntegrationTest --tests com.fongmi.android.tv.ui.custom.CustomKeyDownSeekOwnershipTest --tests com.fongmi.android.tv.player.engine.TimelineStreamKindPolicyTest --console=plain`。

- 执行窗口2026-11-09 16:13:07.438至16:19:19.721 +08:00，372.279秒，实际进程exit0；日志BUILD SUCCESSFUL in 6m11s。正常BuildConfig、leanback production Java、unit Java与指定测试实际执行，没有排除目标。
- 三XML目录 app/build/test-results/testLeanbackArm64_v8aDebugUnitTest；TvSeekOwnershipIntegrationTest 14、CustomKeyDownSeekOwnershipTest 17、player.engine.TimelineStreamKindPolicyTest 7，总38，failures/errors/skipped均0；suite tests与实际testcase一致。协调者直接解析这些本次XML，非仅看成功退出码。
- 四个TvGroupHeader样式heading后的行均16dp，首heading自身0dp；内存移除唯一新增首行16dp属性后完整OuterXml与7c654ecb75基线相同，五断言及单属性门通过，git diff --check退出0。
- XML验证器首次误按类名找heading，退出1；实读证实为style后修正选择器通过。分类为验收脚本错误，不是产品回归；未放宽断言，没有修改产品代码。
- 协调者核对源码SHA-256与验收前后记录相同：LiveActivity `F53F44F67529F7D0CCE738251B1B7AEBE0E07CE55A9B7848D23B309AEC7584C6`；TvSeekOwnershipIntegrationTest `EE98F8CA553C7F1DD905224B2AE03444818ACD839F52A7BB856BE441A2620F4F`；activity_setting.xml `511B4F91942D1D32B5E16966B6C1E8C461605E77A8E776CA793689F8F0D08DB7`。验证树HEAD始终eced786c9f，仅本记录未跟踪。

## 已路由风险与未验边界

1. 增量与PR不同：两轴审四文件，本PR描述只写设置间距与记录，不把beta直播/插件改动冒充新增。
2. Spec首轮四项依据不足：协调者带合同回派run2，逐项撤回/改为验证边界；不把已命名疑点不处理就发布。
3. 旧XML时间早于增量：已执行当前树38项且独立解析真实XML，未复用旧XML冒充当前验证。
4. 路径/验收选择器误判：按Git真实路径和XML样式纠正，精确检查通过，未为工具错误改代码。
5. 物理TV、真实直播网络、Exo/MPV引擎回看seek、完整mobile UI、native/全ABI/APK安装未在本轮验证；CXX5202、Gradle10弃用警告非本轮回归，不扩展全ABI/包装验证。
6. 最终拉取引入新源码或冲突时只重开受影响增量，不沿用旧结果冒称新树已验；不强推、不自动合并PR。
