# C125 — dev1 插件管理增量、beta 同步与发布评审

## Recovery anchor

- 目标：合入最新 beta，覆盖全部当前修改（包括已提交未推送），修复、验证、复审后提交与恢复标签，推送 dev1，创建具体中文 PR 到 beta，最终拉取最新代码。不强推、不改写历史、不直接合并 PR。
- 初始状态：dev1@650ec0cab9781d0ed93b91598fd182adfa51bdee；工作区干净，保护初始脏路径 0；origin/dev1 落后 3 个插件管理提交。fetch 后 beta 为 a2836ca655d7e42818d237b19d986c34a7a97d27，PR #542 已带入此前 C124 内容。
- 合并：7243868c59bd77138badf3313765e8ab57f90ba1，无冲突；git diff --exit-code ed2a5035ab origin/beta -- app 返回 0，beta 的合并提交没有新增源码。新范围为 git diff origin/beta...HEAD -- app：11 文件，+820/-21。已覆盖旧范围沿用 C124，不重复全量历史测试。
- 守卫：C125 / standard，自合并 HEAD 启动，允许的 tracked 路径仅 PluginManagerActivity.java、PluginManagerLeanbackUiDeviceTest.java 和本文件。修复 run 1/3 完成，两轴 run 2/3 静态通过；本次完整 Leanback 类 5 tests 通过，协调者直接核验日志与两文件 SHA-256，未变源码不重复审查。
- 已处理：H1 动态主题覆盖已修复；A1 输入保留断言已补。两项测试辅助重复已由 Standards 明确裁定为非缺陷，保留双变体 fixture，不做无关重构。设备/编译证据和最终本文交两轴做最后定向收口，不重复代码审查。
- 唯一下一步：收取最终记录/证据评审结论后守卫 finish 原子提交和标签，推送 dev1 与新标签、创建中文 PR、最终 pull。发布坐标与最终同步结果在交付消息提供；此处不预先声称发布成功。

## 需求记录与执行边界

- 已确定：用户授权合并 beta、循环修复/验证/复审、提交推送 dev1、中文 PR 到 beta、最终拉取；继续请求沿用此目标。
- 规格证据：三个提交正文与 docs/PLUGIN-01-global-plugin-framework-20261030.md、docs/PLUGIN-03-plugin-remote-import-20261104.md；没有专门 UI issue/spec，提交自述是有限的验收来源，不冒称完整产品规格。
- 不扩张：不改依赖/native、不做无关重构、不生成空代码提交、不直接合并 PR；保留导入/删除/重载、双变体、参数持久化与动态主题行为。
- 完成门禁：两轴没有未处理的实际缺陷，定向验证与修复复审闭合，守卫提交/恢复标签、推送和 PR 实际成功，最终同步没有未评审增量。

时间：2026-11-09 Asia/Shanghai。原目标约 14:00；真实主题回归和实际构建使目标延后。修复于 14:43:54 完成，主要等待 11m27s 编译、7m45s 打包和设备安装。14:45 协调者完成直接日志核验，停止可选测试，收口后立即提交/发布。估计不降低验证门禁。

## 新增提交与具体改动

1. 544b13abba67a44eba28faf2e38c2496a2afd07f — mobile 插件管理页独立纵向布局、可滚动空态、元信息与操作区适配、48dp 触控目标；系统栏/displayCutout insets、操作忙碌状态、行状态与内容描述。
2. 8010f12ec843ca3cb55cb6cc4c0af744e318590b — TV 顶栏动作统一样式、图标默认与焦点颜色、参数对话框空/多行 DPAD 与取消/确认/UP 焦点链、重载进度与忙碌入口禁用。
3. 650ec0cab9781d0ed93b91598fd182adfa51bdee — en/zh-CN/zh-TW Android string 的 JSON 示例引号转义与真实资源解析回归。
4. 7243868c59bd77138badf3313765e8ab57f90ba1 — beta 合并，无冲突且不新增代码差异。
5. 本次修复 — 参数按钮从活动 ThemeTokens 构造配色状态表，默认透明描边和原宽度不变；新增主题回归、无效 JSON 原文本保留断言。本次修复与本文由 C125 守卫形成一个原子逻辑提交。

## 评审与运行台账

### Standards

- run 1/3：740473ca-7225-4a1a-8978-f6c36cf41062，报告 app/build/c125-standards-review.md；硬违规 1（H1），判断性 smell 2（测试截图/反射助手重复）。全部 11 文件覆盖。
- H1：show → LightDialog.apply 已绑定动态主题 → wireParameterDialogFocus 又用 ContextCompat.getColorStateList 恢复编译色。协调者读取 LightDialog、ThemeController、ThemeBinder APPLIED signature 缓存与真实 selector 独立确认，回派修复。
- run 2/3：静态 PASS，H1 closed，新增硬违规 0。活动同一 token 快照替代编译色，不依赖重绑；状态表与原三个 selector 等价。报告保存原发现和处置，不删除历史 FAIL。
- S1/S2：明确裁定非阻断双变体测试 fixture 重复，不是需修复的实际缺陷；两者 IO/反射失败均升级断言，截图 finally 回收资源，等待策略有意不同。处置保留，不为启发式建议增加第三个文件、共享抽象或生产 hook。

### Spec

- run 1/3：b5615bf6-ccc9-4477-adb3-05510114f177，报告 app/build/c125-spec-review.md；验证遗漏 1（A1）、scope creep 0、生产实现错误 0。最初三语资源 dump 是已有 APK 只读核验，不冒称当时重建。
- A1：invalid JSON retained 自述未被编辑器原文本相等断言锁住；旧测试只断言 dialog/error/旧持久值。协调者读测试独立确认，回派补断言。
- run 2/3：静态 PASS，A1 closed，未关闭遗漏/scope creep/实现错误均 0；保留合法值精确保存与 finally 恢复，H1 的测试与 token 映射有效。
- 运行边界：忙碌测试反射 beginBusy/endBusy，是控件状态测试，非真实后台导入/删除/重载端到端。新增主题用例走真实控件与 wireParameterDialogFocus helper，不冒称整个 editParams 端到端。物理 BACK、额外超长/选择区承诺、两变体完全同色没有 spec，不编造缺陷。

### 修复与映射纠错

- run 1/3：b031060a-e213-4217-ab7e-9f02b4deece5，完成；只改两个 Java，未进行 Git 写操作，协调者维护本文。
- H1：Activity:651-666 当前活动 token 状态表；背景 disabled→surfaceContainerHighest、focused/pressed→primaryContainer、default→transparent；文字 disabled→onSurfaceVariant，其余→primary；描边仅 focused/pressed→onPrimaryContainer，其余透明，strokeWidth 仍 round(density*2)。独立 FOCUS 槽不替代配对环色。
- 修复简报最初误写 disabled/default 常驻描边；子代理以真实 focus_ring_primary_container.xml 指出，协调者即时纠正。values/colors.xml:87 指向 onPrimaryContainer，ThemeBinder 明确保留配对 on-色语义。错误表没有进入最终实现。
- A1：LeanbackUiDeviceTest:128 肯定式比较无效输入确认后的编辑器原文与 /。H1 新测试:149-208 在自定义非编译色且 onPrimaryContainer≠FOCUS 下比较两按钮五状态和原宽度，finally dismiss 并恢复原活动 tokens，不改持久主题。
- 运行计数：19 次 PowerShell 命令；后台收取 8 次不启动新命令；累计编译/打包/安装/测试 6 次，上限 8 次。原样失败命令重试 0。修复任务最多 3 轮，各轴总最多 3 轮，不重置。

## 本次实物验证与独立验收

以下是本次修复树实际执行结果，非复用旧 .verify2 测试：

- app/build/c125-fix-build.log：mobile/leanback production Java 与 leanback androidTest Java 指定三目标，BUILD SUCCESSFUL in 11m 27s。
- app/build/c125-fix-package-standard.log：正常 leanback debug / androidTest 两 APK 打包，BUILD SUCCESSFUL in 7m 45s。
- app/build/c125-fix-install.log：emulator-5554 两次 Success；arm64 经该模拟器 native bridge 运行，非物理电视验证。
- app/build/c125-fix-device-test.log：完整 PluginManagerLeanbackUiDeviceTest，5 tests、每个结束状态 0、OK (5 tests)、Time 44.539。包含真实 DPAD 空/多行与输入保留、有效保存、三语实际资源 JSON 引号、主题状态表、忙碌控件与动作颜色。
- 协调者直接读取打包末尾、完整安装/设备日志；H1/A1 对应测试确实在类中运行。git diff --check 返回 0，最终 tracked 修改仅两个 Java，另新增本文。
- 修复后与复审源码 SHA-256 相同：Activity E8C5422F804B9F1B518685E7D0A742941D85F624BEBC623FD01703723FA4BD4D；LeanbackTest B5C63A0AB4FC462454DCEC87586370494ED0B38F1D06023468216EAFBD43054C。
- 期间一次 -x generateBuildConfig 打包优化被 Gradle provider 拒绝，app/build/c125-fix-package.log 记录 BUILD FAILED in 8s；归类为调用错误，取消排除的正常打包通过，不改业务代码或放宽门禁。

## 发布边界与剩余未验

- dev1→beta 开放 PR 查询为空，需新建具体中文 PR，不重复编辑已合并 #542。本地闭合后只推送当前 dev1 与新恢复标签。
- 两轴最终记录/证据收口报告仍写 ignored app/build，不制造后续文档提交。未执行完成的网络操作不在本文冒称成功；最终交付消息给出 commit、恢复标签、PR 和实际同步结果。
- 未本次运行 mobile UI 设备用例；其此前代码证据见 544b13abba Verification 与 .verify2/mobile-plugin-ui-final-tests.log。本次 mobile 指定 Java 编译通过。未做物理 TV、完整设备/ABI/全仓测试矩阵。
- 已有资源 namespace、弃用 API 与 Gradle10 兼容警告不属于本次回归，不擅自扩大修复。SDK28 native bridge 模拟器通过不等同于所有物理电视通过。
- 若最终拉取引入新源码或冲突，旧等价结论不覆盖该增量，重新评审/验证后才能宣称新树通过。

回滚：原三个代码提交可分别 git revert；本次修复提交以 Task-Guard: C125 与 recovery/C125 标签定位。合并回滚保留两父坐标，不使用 reset --hard。
