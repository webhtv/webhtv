# C127 — 弹幕搜索剧集排序、输入焦点与 beta 同步发布评审

## Recovery anchor

- 目标：合入最新 origin/beta，覆盖全部当前差异（含已提交未推送），修复、验证、复评至无开放缺陷；原子提交本任务与恢复标签、推送 dev1、中文 PR 到 beta，最后拉取。不直接合并 PR、不强推、不丢弃现有工作。
- 坐标：初始 dev1@fc39b79e68a1114c338b9ea342ced328aae3b00e，origin/dev1@32db88379920e989f878a99b19de923face86969；fetch 后 beta@54908b9206a13f8467df89bd80bfe9a8a87b268d；无冲突合并 HEAD=5c6bbdad20318b6ada287495d1edf7ebaa273cb3，双父为初始 dev1/beta，tree 与第一父同为 bf2c219668a810cca0b2b937bf66a93e60912a92。初始脏路径0。
- 状态：S1 最小修复和红绿验证完成；协调者直接核验完整报告、实际差异、日志及两XML，修复树16项通过。原 Standards run1无违规/smell，Spec run1发现S1中/P2；协调者已直接读取两轴run2/3报告：Standards硬违规0/smell0/开放缺陷0；Spec新增缺失0/越界0/错误实现0，S1 ADDRESSED，最终开放缺陷0。进入守卫finish与发布。
- 当前改动：Dialog平铺集合在组装完成后统一按方向排序；OrderTest新增5个对称回归；本记录。C127/standard守卫active，自合并HEAD启动，scope固定为本文件及下述3个源码/测试路径，未扩大。其他路径不修改。
- 回滚：保留双父历史；两业务提交可各自revert；本任务提交可按 Task-Guard:C127 / recovery/C127定位后revert。唯一下一步：守卫finish原子提交和恢复标签、推送、中文PR、最终拉取；实际交付坐标在最终消息报告。

## 决策与执行计划

用户确认的闭合记录：origin/beta合入当前dev1；评审全部PR差异及两笔未推送提交；规格以提交意图及既有任务合同为有限依据，只修复本次差异引入的问题；通过后提交推送、具体中文PR到beta、最终拉取。用户不需要操作审批，禁止强推、丢弃改动、无关扩展。

旧C126评审止于32db88379920e989f878a99b19de923face86969，未覆盖两笔新增提交，故不能跳过本轮评审。最新beta只带回上一PR #550 的合并坐标，源码tree没有新增，不重审已覆盖且未变的旧功能。仓库缺少docs/agents/issue-tracker.md、没有独立产品issue；提交目的/Verification是有限规格线索，新增测试是实现验收例，不冒称独立用户spec。

时间基准：2026-11-09 17:54 UTC+08:00 / Asia/Shanghai；原目标18:10，合并1分钟、并行评审/验证7分钟、必要修复/复评5分钟、发布2分钟。实际原树Gradle约6分钟，Spec发现必须修复的S1，红绿及报告耗时使目标顺延；18:27已完成修复后独立验收，两轴复评已完成，剩余仅发布，预计18:40闭合，不增加可选检查、设备/native矩阵或重复成功构建。估计是执行目标，不降低质量门禁。

1. 已完成：fetch beta/dev1；无冲突合入beta并固定坐标。
2. 已完成：两轴完整源码评审run1；原合并树11项定向测试通过但发现未覆盖的S1，不发布。
3. 已完成：S1先新增失败回归，再最小生产修复；修复后16项通过，无scope扩展。
4. 已完成协调者独立实物验收及两轴run2修复增量/本记录复评，零开放缺陷；原通过未变代码和测试不重复。
5. 待发布门：零开放缺陷后task_guard finish原子提交任务文件并即时创建annotated recovery标签；只推送dev1与本次新标签，创建或更新中文dev1→beta PR。
6. 待同步：最后拉取dev1并取得beta最新坐标；若引入新源码/冲突，仅重开受影响增量验收，不冒称新树已验。发布的实际commit/tag/PR及同步坐标在最终交付中报告，不为回填ID制造第二个文档提交。

## 全部当前改动与规格

完整PR源码固定基线beta@54908b9206a13f8467df89bd80bfe9a8a87b268d；审查合并HEAD=5c6bbdad20318b6ada287495d1edf7ebaa273cb3；初审命令git diff <beta>...HEAD，3文件276插入/5删除。复评另加git diff HEAD --两修复文件以及本未跟踪记录，避免漏审已提交未推送或未提交修改。

- e84ca2d39a59deb9cff8e59cadf8bdcdee61f19f，原文feat(danmaku): add ascending and descending search episode order。
  - app/src/main/java/com/fongmi/android/tv/bean/DanmakuTitle.java：保留单参数默认升序入口，新增方向参数；数字集数升降，未知集数始终末尾。
  - app/src/main/java/com/fongmi/android/tv/ui/dialog/DanmakuSearchDialog.java：标题行排序按钮；组内及平铺结果切换方向；保留Header关键词相似度/展开状态，跨来源/新结果保留方向；搜索中及空结果隐藏按钮。
  - app/src/testLeanback/java/com/fongmi/android/tv/ui/dialog/DanmakuSearchOrderTest.java：原5个排序用例，后续焦点2例，本次S1新增5例。
- fc39b79e68a1114c338b9ea342ced328aae3b00e，原文fix(danmaku): focus search order on remote up from input；Verification要求real DPAD_UP event moves input focus to order; hidden order retains input focus。Leanback可见按钮时输入框Up事件主动移焦，不改输入文字，隐藏时保留焦点。实际使用项目KeyUtil.isUpKey集合，不声称只拦截DPAD_UP。
- 本次S1：Dialog旧719的提前平铺sort移除；旧735/现734从仅降序最终sort改为DanmakuTitle.sortByEpisode(ungrouped, reverseOrder)，在所有singleton追加后统一排序。生产1插入/2删除；OrderTest新增assertNull import与5用例，共73插入；修复tracked diff共2文件74插入/2删除。本任务不修改DanmakuTitle、焦点/布局、Header排序/展开/选择、请求/网络/lifecycle或依赖。
- 本次beta合并无源码增量；本任务提交包含上述修复、回归及本记录，不把旧beta功能写成本PR新增，不制造空代码提交。

## 评审台账

### Standards

- 代理6bc3b79b-e16c-409e-bab1-94d843a9c08b，run1/3，app/build/c127-standards-review.md。根AGENTS.md优先于12项Fowler smell启发式；三个文件全部hunk及必要调用已读。硬违规0、smell0、开放缺陷空，不能用规范轴结论替代规格轴。
- 旧addAll:719/735两阶段集合不同，不能简单删最终排序并称等价。此次修复删除提前sort但保留完整集合最终双向sort，run2已核对实际增量；不因更优雅而扩展重构。
- run2/3：同一路径保留run1并追加修复增量/记录复评。硬违规0、smell0、开放缺陷0；亲读实际diff、red失败名、green日志/两XML和当前哈希。协调者已读取run2完整新增段，不仅采信通知。

### Spec

- 代理7e129222-1bcf-41c2-ad2c-7d97c930c8e5，run1/3，app/build/c127-spec-review.md；缺失0、越界0、错误实现1，S1中/P2，不通过。
- S1：旧719先sort无titleKey项、725-726追加singleton、735仅降序最终sort，setReverseOrder(false)却全平铺sort。同源尚公主第10集/其他剧第2集初次及重建[10,2]，降→升[2,10]，新结果/换源又恢复[10,2]。旧beta本已按标题rank追加singleton；这是新增排序功能同模式不一致，不称旧历史升序被本轮改坏。协调者读704-780与beta旧实现确认并派修复。
- 静态合同：D:398保留已提交关键词，不读取后改输入（docs/danmaku-manual-match-memory-design.md:179-186）；所有权/取消/lifecycle（docs/C-TV-ASYNC-RESULT-OWNERSHIP-danmaku.md:49-52,270-271）未改。不把旧任务的禁改范围当本轮授权排序越界。
- run2/3：同一路径保留run1并追加复评，S1 ADDRESSED；新增缺失0/越界0/错误实现0，最终开放缺陷0。亲读实际diff、red/green、两XML16项和当前哈希；协调者已读取run2完整新增段。

## 验证与独立验收证据

### 原合并树（不能替代修复树）

- 集成代理7b36af21-5dc2-4f89-b00c-3972a5b9dea2 / run1/3；报告app/build/c127-integration-verification.md，完整日志app/build/c127-targeted-tests.log。
- 命令.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.dialog.DanmakuSearchOrderTest --tests com.fongmi.android.tv.ui.dialog.DanmakuSearchIntentTest --console=plain。
- 18:00:10.1729282→18:06:14.6181820+08，exit0，BUILD SUCCESSFUL in 5m56s；新XML18:06:13，Order7+Intent4=11，failures/errors/skipped0。协调者直接解析两XML并读日志头尾，未把历史.verify2成功日志当本轮证据。

### S1红绿与修复树

- 修复代理892f1826-6afd-43d5-b355-86645f4e2bf6 / run1/3；仅两指定tracked路径修改，ignored报告app/build/c127-s1-fix-report.md、完整red/green日志app/build/c127-s1-red.log及c127-s1-green.log。
- RED：只新增测试、production未改；18:17:30→18:18:19+08，exit1，Order12项/5失败/0errors/skipped，原7通过。五个新增全行为失败：初次/new publish/往返后publish期望[2,10]实际[10,2]；切hongguo期望[3,20]实际[20,3]；有效titleKey-null未知夹具期望[2,10,2147483647]实际[2147483647,10,2]，null/source/unknown前置均通过。协调者直接读red失败名与汇总及当时Git仅O+73行，确认真实红灯先于production。
- GREEN：仅根因修复后按上面两--tests命令；18:21:31→18:23:38+08，126.834357秒，exit0，BUILD SUCCESSFUL in 2m6s，实际测试task执行。XML目录app/build/test-results/testLeanbackArm64_v8aDebugUnitTest/；Order12、Intent4，总16，failures/errors/skipped均0，实际testcase数一致。两XML修改时间18:23:37.7318185+08；suite timestamp分别2026-11-09T10:23:24.639Z和10:23:35.556Z。
- 新5例：ascendingInitialResultsSortFlatItemsAfterSingleGroupsAreAdded、ascendingNewResultsSortFlatItemsAfterSingleGroupsAreAdded、ascendingFlatOrderAfterRoundTripSurvivesNewResults、ascendingFlatOrderPersistsAcrossSources、unknownFlatItemStaysLastAcrossRebuildsAndBothDirections。未知夹具真实titleKey-null，断言真实TextView显示，不测试mock返回值；publish直接onSuccess不是网络验收。
- 协调者18:27:32直接读完整修复报告、实际两文件diff、green末尾exit0、两XML真实计数与用例数、3文件SHA256及diff --check exit0。只生产1插入/2删除和O+73，未扩大范围；未重跑未变成功构建。
- 当前SHA256：Title=917FA24588EE70D7FF7166A6D7CCE6F66F64BDFFAD24E2840EB2841A32755432；Dialog=93BC91ED5993555281296DC6C7876684424FB8C4764B29383A5C5645DA2C1C09；OrderTest=7E80E7C151633761033370A98DA8FD64EE026417BBB68936290E427521917E4E，与green前后记录一致。Title未改；原D/O分别33D515E2061169D19EBF75B409B5CF1F04A44C4A1D9A6B2B0FC5B11DAC1255E0/5E796242FAA8D3CF398231B3A59416AB8A4181532EA664C001E16E8311800368。

## 简报限额与路由

每简报首次加最多2回派，累计3run、不按轮重置：集成run1，Standards/Spec当前将各run2，修复run1。集成pwsh7/20、Gradle1/3、手工ignored2/2、生产编辑0；初审各pwsh4/15、ignored1/1、生产编辑/构建0；修复pwsh6/20、Gradle2/4、tracked2/2、ignored3/3、Git写/新guard/设备/子代理0。代理没有commit/push/PR，均以具体报告证据由协调者验收。

路由均明确deepseek-official/deepseek-v4.1-flash；工具未给Authorized child routes或No allowlist注解，限制状态未确认，不声称已授权或per-task routing强制。

## 发现风险及处置 / 未验边界

1. 旧评审不覆盖两笔未推送：完整三文件双轴初审，不跳过；beta仅tree未变的旧覆盖复用。
2. S1升序重建不一致：Spec发现、协调者源码复核、红灯重现、最小修复与16项green，两轴run2已通过、开放缺陷0；未以原11项通过掩盖缺陷。
3. 缺少独立产品issue：用户确认有限规格，Spec明确限制，不编造需求。
4. 红XML被green正常覆盖：持久保存red完整日志、失败值/名称/生产未改证据，不将greenXML当red证明。
5. 实体TV/IME、Mobile完整UI、旋转、真实HTTP/post/lifecycle竞态、真实播放器选择消费者及大列表性能未验；本次不改这些合同，不冒称设备或全产品验证。仅CXX5202/API/Gradle弃用与LF→CRLF警告，已分类既有/环境提示，不为无关警告升级依赖或扩native矩阵。
6. 最终拉取若带入新源码或冲突，只重开受影响验收，不冒称新树已验；不强推、不直接合并PR。

最终证据强度说明：协调者和Standards独立核对red日志中的五个失败名/行号及12/5汇总；具体历史期望/实际数值由修复代理当时解析、已被green覆盖的redXML记录，不声称从red日志独立还原这些值。两轴均将该边界明示；真实红灯、production未改指纹和green16项证据不受影响。
