# C-TV-ASYNC-RESULT-OWNERSHIP — 自动弹幕异步结果当前会话归属

## 决策与授权记录

- 研究日期 **2026-11-08**，Asia/Shanghai；前置研究 14:35:57→14:43:57 目标达到后停止可选搜索。**建议窄适配；已批准边界内可进入实施，第一 gate 是已 post→换片→drain 的真实竞态 Red，不是继续泛搜。** 本轮未实施、构建、提交或标记“已实测竞态”。
- root `F:\Workspace\fish2018\webhtv` 与最终 tree `.codex/task-state/worktrees/tv-small-fixes` HEAD 均已核实 `47454687f504955775b398905ba09d73aeb9f20c`；两者只读，父正在跑 baseline，禁止编辑。
- 原 `app/.cxx/` 与未提交 `docs/upstream-player-dependency-merge-assessment-2026-11-08.md` 保护。本轮仅写本文件与 C-TV-SEEK-OWNERSHIP 指定设计文件；未来本文件复制为唯一任务文档 `docs/C-TV-ASYNC-RESULT-OWNERSHIP-danmaku.md`。
- 与 seek 独立，均 touch leanback VideoActivity，后续串行，各自测试/提交/回滚，不迁移 controller，不合成任务。下述是已批准合同的可执行设计，不再请求审批。

## 来源、完整身份与证据等级

全部访问日期 **2026-11-08**；A=精确源码/官方规范/可复现试验，B=维护者解释/成熟相关代码。研究读取不等于本地实验通过。

| 来源/身份 | 等级 | 支持的事实与实际消费者 | WebHTV 决策影响/限制 |
|---|---|---|---|
| `https://github.com/FongMi/TV/commit/fa5a39261ba053174b3a2b100c4a1073e2bd39c0`；parent `6e9dd1bb3065d205dfe4774add9578ea2e52ea2f`，cache `.git/upstream-tv-assessment.git` | A/B | 精确 diff：双端 VideoActivity.loadDanmaku → VodPlaybackMedia.searchDanmaku(BooleanSupplier) → found 应用前 isCurrent；controller.isCurrentResult 要 host非finishing、pendingRequest==null、quality==result；destroy仅 binding owner global cancel。维护者提交正文解释 current playback owner | **只采用弹幕结果所有权**；pixelWidthHeightRatio/手机视频尺寸属于其他变化，拒绝纳入；不能复制 controller |
| 上游最终 HEAD `4512b4c9f82534ff66acfde06ccdd68f35b074f0` 的 `app/src/main/java/com/fongmi/android/tv/playback/vod/VodPlaybackController.java`，isCurrentResult/cannotApply/requestResolvedPlayback | A | 仍有 pending request 与 Result 引用检查，先 pending 再 request，解决请求等待窗口；消费者仍 VodPlaybackMedia/双端 Activity | 本地没有这个 controller/state，必须映射本地真实 start/session/result，不只照抄谓词 |
| 本地 HEAD `47454687f504955775b398905ba09d73aeb9f20c`，`DanmakuApi.java`:36-55,83-98；双端 VideoActivity:1236/1400 | A | 当前自动请求 newCall 使用全局 TAG 且先 cancel(TAG)；HTTP onResponse 解析后 App.post(found.accept)，网络 cancel 不撤销已排队 Main callback | 是源码可达风险，尚无测试/设备竞态复现；必须先 Red |
| 本地 PlayerManager:5983-6029 与 start:5194-5208、parse:5219-5235 | A | setDanmaku 有空/支持类型/相同 URL/forced reload guard；start/parse clearDanmaku 并换 spec，均创建 trace。setDanmaku 写当前 spec/URL/controller，没有自动查询请求的 Result/session guard | 不能说“完全没 guard”；这些是后段加载/类型 guard，不能阻止另一集已 post 自动搜索结果成为新来源 |
| 本地 `PlaybackActivity.java`:162-165,334-350,904-912,922-932；`PlayerManager.java`:480-481,6246-6255；`PlaybackTrace.java`:42-64,67-73 | A | isOwner只比较key；startPlayer void 可拒绝 DRM/error/空URL，也可 start/parse；trace 是累计 SEQUENCE 的真实 session，start/parse/switch 更新，NONE/clear区分无会话；背景/配置重建可以保留服务 | 用现成 trace getter，不增加全局 token API；不能把调用返回当成功或用同 URL/key 替代 session |
| `https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/core/java/android/os/Handler.java`:442-456,621-635,849-855 | A | pending Runnable 移除是 Handler 队列操作，取消网络 Call 不是队列撤销；null global 清队列过宽 | 在 UI apply 前验证，不用 removeCallbacksAndMessages(null) 或 cancel 全 App |
| `https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/core/java/android/app/Activity.java`:285-292,434-441 | A | onStop不可见但状态仍保留并可能重启；不等于播放资源停止 | 不因 onStop 统一清播放服务/后台音频/PiP；destroy retire自己 owner并cancel自己 Call |
| `https://robolectric.org/blog/2019/06/04/paused-looper/`，2019-06-04；维护者 Brett Chabot/Jonathan Gerrish | A/B | PAUSED主 post 不 inline，显式 idle，legacy 双调度队列可能错误；可构造网络已 post但 UI未执行窗口 | 使用现有4.16，不能 Mockito直接调用消费者当完整测试 |
| `https://raw.githubusercontent.com/robolectric/robolectric/master/robolectric/src/test/java/org/robolectric/shadows/ShadowPausedLooperTest.java`:44-46,103-121（访问时master）；本地 FlagSelectionListenerTest:43-85、ExoStartupRecoveryIntegrationTest:67-71 | A | 成熟实际 Handler/Looper 测试；本地已有4.16 PAUSED实际组件 fixture | master未冻结fullhash，仅佐证；本地fullhash是测试基线锚点 |
| `https://raw.githubusercontent.com/androidx/media/1.8.0/libraries/ui/src/main/java/androidx/media3/ui/PlayerView.java`:650-665 | B | 成熟播放器UI setPlayer主线程切换，旧player移除listener/surface，只维护自己的owner | 不声称其listener removal撤销了任意已post任务；该文件 onImageAvailable 也post，不能把类名当完备异步guard证明 |

官方 Android lifecycle/Handler 网页抓取失败；已改官方 AOSP `android-16.0.0_r1` tag 源/Javadoc，平台语义证据不缺。上游维护者解释用精确 commit正文（B）配真实代码（A）；本次未发现/不捏造该提交专属 PR或竞态设备报告。

证据类：upstream精确commit/parent/finaltree、本地实际调用、官方平台、维护者、成熟播放器代码/测试已覆盖；本轮未获得 field实测，门槛为真实确定性 fixture与双端 smoke。论文不适用：没有新并发算法/队列/资源调度，只恢复已有callback的owner条件。性能bench不适用：没有性能优化或吞吐主张，不用小字段/减少错结果推断性能。无需浏览播放器依赖链或154行候选账本；报告 `.git/tv-assessment-player.json` 仅候选6（与候选1并行独立）。

## 本地 actual lifecycle/start/result identity

- leanback `getPlayer`:1181-1207、mobile:1357-1375，在新的 episode/source 请求发出时改变选集/历史，然后调用 ViewModel.playerContent；此刻旧 Player 可能仍播放。因此只在新结果返回后比较 trace，会漏掉**pending换片窗口**。
- leanback `setPlayer`:1209-1236，mobile:1377-1400：先finishing/destroyed检查；服务未连接则分别缓存 `mPendingPlayer`/`mPendingPlayerResult`；随后 QualityAdapter更新并调用 startPlayer，再无guard调用 `DanmakuApi.search(...,player()::setDanmaku)`。
- quality点击 leanback:1340-1343、mobile:1474-1477 直接 startPlayer，不重新自动搜索，仍必须让旧请求失效。服务绑定恢复 leanback:561、mobile:1403-1415 可能再次执行 setPlayer；不能把旧 pending Result 当新epoch下已接受结果。
- `PlaybackActivity.startPlayer`:334-350 为 void，拒绝无效/不支持DRM、错误/空真实URL；成功走 parse/start。真实 PlayerManager start/parse 在同线程先替换spec、clearDanmaku、beginPlaybackTrace，再异步prepare/parse。因此可在startPlayer**前后读取trace**确定本调用是否被接纳到新原生会话，不要求首次帧出现；前后trace相同、NONE或owner失效时不能建立自动查询token。
- `PlayerManager.getPlaybackTraceId`:480-481 为稳定现成getter；`PlaybackTrace.begin`:42-52 使用累计SEQUENCE，A→B→A同URL也不同；current:63-64，clear:67-73。PlayerManager beginPlaybackTrace:6246-6255，start:5202，parse:5229，各switch:1357/1410/1422/1447/1492/1519/1560更新；普通seek不改trace。trace用途虽为诊断，但此代码提供确实可用会话身份，未来测试锁定相关合同而不改变实现。
- 现有父callback多处用isOwner，但其:162-165只key相等，不能判定同key多集/相同URL；绑定生命周期可保留player，需同时校验captured PlayerManager实例与trace、Activity本地token/retired状态、native非redirect与owner资格。
- onDestroy leanback:6382/mobile:6588 全局 DanmakuApi.cancel；手动 `DanmakuSearchDialog`:164/373、`DanmakuSearchInputDialog`:150/330 同TAG/cancel/newCall体系。若自动查询仍用这个TAG，旧Activity destroy或自动newCall可能取消新页面/手动owner。不能只把全局cancel套isOwner(key)，也不能改手动两个dialog。
- PlayerManager现有WebSocket live连接、danmakuController/backpressure、source load guards全部保持；本任务只控制“自动搜索结果能否成为当前源”。

## 已批准合同与窄设计

**Disposition：补足上游 current-result 设计在本地的 request/session/binding 边界；不移植 VodPlaybackController/VodPlaybackMedia。**

1. 两个VideoActivity各自保留一个当前自动查询token与自己Call句柄。token为不可变快照：Activity-local request epoch/owner对象、captured PlayerManager实例、non-NONE playback trace string、Result引用身份、frozen source key/真实URL/episode标识（字符串拷贝而非后续读mutable Result/History）。token对象身份与当前字段必须相同；URL不是session。
2. **先invalidate再发新请求**：getPlayer、source/episode/detail改变、quality/parse/重新start等replacement入口先清当前token、仅cancel自己的auto Call；等待结果期间旧callback即使旧trace没变也无效。只对本任务字段动手，不动ViewModel/全局播放状态。
3. setPlayer先记录beforeTrace，执行真实startPlayer，读取afterTrace；只有当前owner/native、service/manager有效，且trace被本调用更新为有效新session时捕获accepted Result和frozen source并创建token/发查询。错误、DRM拒绝、pending未绑定或parse拒绝不得以void返回“开播”。走parse可建立已接受parse会话token，因为真实parse同步换spec/trace；后续trace变化时旧token失效。
4. DanmakuApi仅追加一个独立自动owner入口/overload（例如 `searchOwned(name,episode,ownerTag,found)` 返回 `Call`），复用URL规范化与解析，但**不得调用现有newCall的global cancel(TAG)**。内部可把请求构造拆成private函数以接收owner tag，原public `search/newCall/cancel` 签名和手动语义不改。自动请求使用每owner唯一tag/Call.cancel；旧owner不取消新owner，不全局cancel新页面。
5. 真正 `App.post` 的UI回调应用前（不是仅enqueue前）检查：当前token对象仍是captured、request epoch没换、未retired/finishing/destroyed/exiting、当前service与captured manager相符、当前native非redirect、owner资格成立、当前trace==capturedtrace、当前accepted Result为captured引用及冻结source对应。全部通过才调用captured/current同一manager.setDanmaku。不可闭包只引用动态player()而无比较。
6. onDestroy先retire本Activitytoken、cancel自己Call，并删除该自动流的global DanmakuApi.cancel调用；两页所有权不共享global registry。HTTP取消只是优化，不是正确性guard。相反网络完成顺序/已post必须仍安全。
7. onStop保持现有foreground/backpressure/后台音频/PiP语义，不单凭Lifecycle不RESUMED拒绝同一后台播放会话；不改变手动搜索、WebSocket live、spider-first优先策略、站点弹幕初始数据、DanmakuUrlPolicy/网络权限。

### 方案比较

| 方案 | tradeoff | 结论 |
|---|---|---|
| no change | 保留current源码路径，网络cancel不足以清App.post；当前后段URL guard无法知道请求来源 | 不满足合同 |
| unmodified upstream | controller pending/currentResult和bindingownerguard正确，但需引入controller/state/host；含视频尺寸另行为；本地isOwner(key)并非其bindingowner | 拒绝盲port |
| narrow adaptation | 三生产文件；复用真实trace + local request/result token、owner-scoped Call + apply前校验，保持手动与后台 | **选择**，必须先真实Red |
| 仅比较URL/Result、仅HTTP cancel、只写helper测试 | 同URL A→B→A、pending窗口、已post、旧owner destroy会漏；无当前回调positive则可能全丢弃 | 拒绝 |
| 增全局generation/controller或全局cancel新页 | 可增加更广架构，但不在批准边界，容易误伤其它owner/后台 | 不选 |

ABI/JNI/播放器版本/锁/Native制品/依赖均 **no upgrade/no change**，旧公共API签名保留；只多局部token/比较/owner tag。未测包体、网络性能、功耗，不宣称改善或零回归。安全不扩大URL/scheme/日志凭据/权限；维持现有脱敏和解析策略，回调不重用mutable Result来源。

## 精确未来允许路径

生产只允许：

- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/main/java/com/fongmi/android/tv/api/DanmakuApi.java`

任务测试允许：

- `app/src/testLeanback/java/com/fongmi/android/tv/api/DanmakuApiOwnershipTest.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/VideoDanmakuOwnershipTest.java`
- `app/src/androidTestMobile/java/com/fongmi/android/tv/ui/activity/VideoDanmakuOwnershipInstrumentedTest.java`
- 唯一任务记录 `docs/C-TV-ASYNC-RESULT-OWNERSHIP-danmaku.md`。

禁止PlayerManager/PlaybackActivity/App/controller、两手动dialog、WebSocket/backpressure、Gradle/依赖/native/locks/原`.cxx`。**Mobile当前没有Robolectric依赖**，不能暗中把testLeanback4.16当mobile可用；首选已有androidTest runner的真实mobile测试。若要mobile Robolectric dependency配置，超上述路径，须先把具体gate交父，不能以“测试”为名擅加生产/构建文件。

## 可执行 TDD 与验收

实际基础：app/build.gradle:63-79模式/ABI flavors，87-89 includeAndroidResources，204-208 JUnit、testLeanback Robolectric4.16、testImplementation MockWebServer、androidTest runner1.7.0。App:45-49构造器设置instance并真实main Handler；测试专用TestApp extends App覆盖onCreate防止无关初始化，不能替换post。

**seams：真实DanmakuApi的HTTP enqueue/parse→真实App.post/PAUSED Main Looper→双端真实VideoActivity创建并验证token的回调→PlayerManager.setDanmaku出站consumer。** 可record最终consumer调用，不mock时间/guard/token路径；禁止仅helper或source-text断言。Activity fixture可以有限test-only subclass与ReflectionHelpers seed现有私有UI/History/Result状态，不新增production test-only hooks，不复制setPlayer或消费者逻辑。

垂直切片，一条真实Red→最小实现→Green；本研究没有执行这些测试。

1. **首个已post竞态Red**（leanback API+Activity集成）：MockWebServer返回A弹幕；真实DanmakuApi发A查询；PAUSED主Looper不drain，等待HTTP onResponse结束并确认真实Main队列存在该post（test-side限定poll+timeout，只检查队列/回调完成证据，禁止sleep猜已post）。在主线程执行实际换集/getPlayer入口使request B开始，尚不返回结果；drain Main；A不得调用setDanmaku。旧实现真实found将调用captured player.setDanmaku，行为assertion应失败。**网络只收到response不等于已post**，必须证明post已存在后再换片。
2. 紧接positive：B实际setPlayer/start或parse产生新的trace，B网络完成→post→drain，B一次apply，A零次；用不同danmaku URL避免被PlayerManager“sameURL” guard遮蔽。consumer spy只record实际setDanmaku，不替代自动callback创建/guard。
3. A→B→A2同source URL/同vod key，第一A callback已post；两个实际start/parse生成不同trace；最终drain第一A不能apply，A2必须apply。另同Result引用复用/URL字段变动案例，验证snapshot+epoch不是字符串等价伪session。
4. 相反完成顺序：发A、换B、B先返回、A后返回；只Bapply。cancel发生在HTTP响应前、已post后各一次；已post后cancel仍须guard生效。当前request取消后新request仍成功，避免全局cancel实现。
5. 旧Activity A查询/已post→BActivity新owner发查询→A destroy；A只cancel自身Call，B未cancel且结果apply。相同key/session绑定重建与不同播放session都覆盖；不使用isOwner(key)作为唯一通过条件。
6. startPlayer拒绝（error/空URL/不支持DRM）、service pending/rebind、quality/parse选择pending、Native→Web/redirect、onDestroy；均不得把旧结果apply；成功parse会话当前结果仍可apply。普通seek/后台音频/PiP同一owner/session不能被一刀切生命周期拒绝；手动选择/搜索与WebSocket直播弹幕维持baseline行为。
7. Activity集成必须调用实际getPlayer/setPlayer/quality与lifecycle入口；私有入口允许test-side ReflectionHelpers invoke（seed只为了隔离无关UI），不把private helper的单测当完整覆盖。如果真实Activity fixture无法在现有依赖下安全构造，留下精确constructor/service/UI缺口交父，不添加泛化test hook，也不声称竞态已复现。

未来隔离实施tree（不是root/父baseline tree）：JDK21 `C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`，SDK `C:\Users\Maple\AppData\Local\Android\Sdk`，GitBash `G:\Git\bin\bash.exe`：

```text
bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.api.DanmakuApiOwnershipTest' --tests 'com.fongmi.android.tv.ui.activity.VideoDanmakuOwnershipTest'
bash ./gradlew :app:connectedMobileArm64_v8aDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.fongmi.android.tv.ui.activity.VideoDanmakuOwnershipInstrumentedTest
```

命令按实际flavor配置推导，未在本研究运行；mobile设备门槛需要可连接设备。第一命令覆盖真正API/Activity/Looper，不因mobile Robo依赖缺失去升级Gradle。mobile已有runner路线可用test-side本地受控HTTP（现有NanoHTTPD）与Instrumentation.runOnMainSync/有界latch，精确实现必须证明callback已post再替换，而不能只waitForIdleSync后说有race；若设备无法控制已post窗口，mobile改用实际生产guard路径烟测并将完整mobile确定性竞态留具体gate，不虚报通过。

acceptance：已post→pending换片→drain、A→B→A同URL、逆序完成、取消、旧owner destroy不cancel新owner、current positive全通过；同key不能代替session；真实拒绝/parse/binding入口可达；双端编译与真实消费者验证；手动/Live/后台/PiP不改；无新生产hook/依赖/native变化。Red失败须是业务断言而不是fixture/classpath错误；留Red/Green原始日志，不用单次Green或代码检查称竞态证明。

## 剩余 gate、回滚与恢复锚点

- **G1 未执行真实Red/Green**：实施第一步建立真实App/HTTP/Main queue/Activity fixture。不可把源码路径说成实机race；目前充分证据支持设计，不支持“已修复”。
- **G2 接纳token fixture**：验证startPlayer前后trace、成功parse、拒绝不变trace；不新增全局sessionAPI，已有trace getter可用。local request invalidate必须在请求发出前，pending服务结果重新接纳应绑定正确epoch。
- **G3 mobile工具/设备**：无testMobile Robo依赖；既有instrumentation路线与双端smoke要有实际结果。缺设备是具体剩余门槛，不用动原.cxx/依赖链换取伪验收。
- **G4 baseline/串行**：父baseline结束并seek独立单元确定后才在本任务隔离tree改代码，实施前记录真正父HEAD与protected dirty；当前HEAD锚点不意味着后续父HEAD必须相同。
- 回滚：一个原子提交含三生产文件、任务测试、唯一docs；`git revert <本任务完整commit>`，同时撤销owned API overload与双端调用，以免一端/旧签名不配套。不得reset原root/撤销seek任务/清dirty。核对旧自动搜索与手动/Live行为；恢复标签仅标已提交已验证状态，不push。
- 本研究简报run=1/3累计；同失败假设cap=2 attempts累计后换法；编辑cap=2 files累计且只两指定`.git`设计，生产cap=0 files累计；read-only commands不限累计；每实质query改写cap=3累计；证据quota每任务重置不结转；8分钟前置研究目标达到已停可选搜索，不是终止完成目标。

## Implementation run 1/3 — 2026-11-08 Asia/Shanghai

- 用户已批准开始；真实前置 HEAD `dc5f1e60c4f96f88f5183cbf11b50bf97f2c6b65`，branch `upstream/small-fixes-20261108`，tree clean，seek guard finished（亲自核实）。旧研究 HEAD 仅作研究身份，不用于重置。
- standard guard 已启动，scope 为三生产文件、两个 testLeanback 测试、唯一本文档。索引 SHA256 `BEEC8FBED4434E0C8004B6D66585DD67D2550553DDC087057F8C944C2CC7EDF1`，保持字节不变；原 root 全部只读。
- 16:02 起预计 55 分钟：fixture/RED 20 分钟、实现 15 分钟、测试/双端编译/闭环 20 分钟。未做新研究、依赖或 native 改动。
- 真实接口核实：`PlayerManager.isNativePlayer()` 是 `!isExo()`，不是“非 Web 播放”；自动结果采用 Activity 非 redirect 条件，不能用该 getter 错杀 Exo。
- 首个测试隔离 artwork/history persistence 与末端 media prepare，不替代真实 getPlayer/setPlayer/startPlayer/PlayerManager.start 的 trace 或真实 HTTP/App.post。

### 实施与证据

- 首个业务 RED：`C-TV-ASYNC-RESULT-OWNERSHIP-red-fixture5.log` 与 `...-red.xml.log`；1 test / 1 failure，HTTP onResponse 完成且真实主 MessageQueue 有 DanmakuApi Runnable，再实际 getPlayer dispatch，旧 trace 保留，drain 后 setDanmaku 实际 1 次、预期 0。之前 fixture 日志不算业务 RED。
- 最小 GREEN：`...-green1.log` / `...-green1.xml.log`，9 tests / 0 failures。覆盖已 post pending、当前 start positive、同 URL/Result 复用、mutable URL、拒绝 start、quality、断连/redirect、服务 pending 丢弃、成功 parse/parse 替换。
- 双端追加局部 immutable token（manager、binding、真实 trace、Result identity 与冻结 key/URL/episode/flag/name）；token 身份是 local generation。getPlayer/getDetail/setParse/start 与内核异步 refresh 发请求前撤销；未接纳 start（前后 trace 相同/NONE）不创建查询。pending service Result 在新请求/start 前清除。
- 复用真实 App.post，在 UI apply 前检查本地 token、未退休/finishing/destroyed/exiting/redirect、service/manager、owner、冻结媒体来源、trace。没有改 PlayerManager/PlaybackTrace；普通 seek 不触发 invalidation。
- DanmakuApi.searchOwned 独立 tag/返回 Call，不调用默认 newCall 的 global cancel。原公开 search/newCall/cancel 签名/手动语义保持。
- 按父最新裁定保留原 onDestroy 默认 TAG cleanup（仅手动流）；同时先退休并取消自己的自动 Call。OkHttp.cancel(String) 确认只按 request.tag.equals 匹配，UUID auto tag 不属于默认 TAG；与设计原“删除 global cancel”文字相比，此为保留手动退出行为的窄兼容适配。
- 同媒体 decoder/kernel switch 更新 trace 后，READY 仅在旧 token 仍匹配当前媒体但 trace 变化时重建 owned 查询；相同 trace 的 READY/seek 不重发。异步 kernel refresh 的新 Result 则用真实 switch 前后 trace 重新接纳，不沿用旧结果。
- 扩展测试 final1 15 tests / 6 failures，均已定位 fixture：相对 URL 与 getUrl 原值期望、未 create 的 Fragment lifecycle、缺 backend rebuild callback、FormBody 空格编码。完整日志/XML保留；final2 在修正测试侧隔离后执行中，不能将它提前记通过。
- final2：15 tests / 1 failure，其余 14 通过（包含实际旧 Activity destroy、新 owner positive、真实 toggleDecode→READY 重发一次、同 trace 重复 READY/seek 无重发、真实 onStop 当前结果 positive、两个手动/auto API 隔离案例）。唯一逆序 case 的 idle latch 在 A 取消后、B 尚 enqueue 的间隙提前归零，已改为 B HTTP received 且 response gate 未释放后设置 idle latch，再释放 response；final3 正在执行。此是测试同步缺口，不更改生产 guard。
- 双端 Java 编译：final2.log 第 94/133 行的 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac`、`:app:compileMobileArm64_v8aDebugJavaWithJavac` 任务均完成，无 Javac error（同轮 Gradle 整体退出 1 仅因上述逆序测试）。之后仅编辑测试/文档，生产 revision 未变，不为过滤或安心重复编译。
- Mobile 限制：无 Robolectric 依赖；仅两台 x86_64 emulator，而批准 flavor 为 ARM 且禁止安装/替换用户 App。因此没有执行 candidate Mobile instrumentation/真实消费者；双 Java 编译作为可执行 gate，不将 Leanback Robolectric 替代 Mobile 行为证明。不做 native/ABI/设备 App 安装。

### 最终自检结果（2026-11-08 Asia/Shanghai）

- `./gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.api.DanmakuApiOwnershipTest --tests com.fongmi.android.tv.ui.activity.VideoDanmakuOwnershipTest --console=plain`：final3，退出 0，15 tests（API 2 + Activity 13）/ 0 failure / 0 error / 0 skipped，4m17s。真实已 post→pending 竞态与所有当前 positive 均通过；error/空 URL start 均不会为旧 trace 搜索。
- 原始最终 XML 保留在忽略报告目录 `C-TV-ASYNC-RESULT-OWNERSHIP-final3-*.xml.log`。完整首 RED、GREEN、失败 fixture、最终日志均保留，不重跑成功 revision 来过滤日志。
- 双端 Java 编译任务在 final2 完成；其后仅测试/本文档编辑，无生产 revision 变更。没有 ARM/native 或候选 App 安装、没有 Mobile 真消费者/竞态、DRM 设备拒绝/PiP 实机与完整 kernel-refresh 后端场景未实测，不据 Java 编译声称它们通过。
- `git diff --check` 通过；canonical checkpoint 审计首轮因 GitBash 缺 rg 不确定，第二轮仅添加进程 PATH 后退出 0（185 full hashes、0 errors、0 warnings）；没有改脚本/全局环境。索引 SHA256 与冻结值完全一致。
- 自查三生产完整 diff：seek 的 cancelKeySeek 及测试保持不变；手动默认 TAG API/退出清理、spider-first、站点初始弹幕、setDanmaku URL/类型/reload、WebSocket/backpressure、后台/PiP路径没有修改。依赖/native/lock 构建文件无变更。
- 接受状态：代码与 Leanback 可执行合同已自检通过，交父独立 review；Mobile/设备行为保留为具体 gate，不能将它标记为双端行为通过。一个 code/test/doc 原子 commit 和 tag 由 task_guard finish 形成，精确值只写忽略 report，父最终统一文档归档。
- 回滚（完成后）：从 report 取本任务完整 hash，`git revert <C-TV-ASYNC-RESULT-OWNERSHIP commit>`；不 reset tree、不撤销前置 seek、不触碰原 root dirty。

### Recovery anchor

run 1/3，自检完成：15/15 最终测试、双端 Java 编译、scope/diff/checkpoint通过，索引字节不变。剩余 Mobile/设备 gate 已明确交父，不冒称行为验证。下一步仅执行 task_guard finish 原子提交与立即 tag，然后把精确 code hash/tag 写忽略 report 发父；不追加自引用 docs commit、不 push、不删除工作树。
