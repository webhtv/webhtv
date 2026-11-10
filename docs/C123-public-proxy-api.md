# C123 本机公共多线程代理接口实施计划

**Goal**：复用现有控制栏多线程代理引擎，新增免 Token 的本机 `/proxy`，支持请求级 thread / shards / chunkSize（KiB）；应用后台服务期间自动监听，播放器自动代理开关默认不变。
**Architecture**：同一个监听端口及分段引擎；公开入口采用不可变请求覆盖、专属上游安全策略和公共请求准入。旧 `/v1/proxy` 保留 capability-token，`/v1/stream` 保留内部目标注册；不添加 Go/JNI。
**Tech Stack**：NanoHTTPD、OkHttp 5.4.0、Java 21 工程（新增工具类保持 Java17语法）、JUnit4/Gradle9.5.1。
**Spec**：本文件的确认记录与完成标准为规格；用户 2026-11-09 已确认并要求“开始”。

## 已确认约束
- 新用户入口仅绑定 127.0.0.1，免代理 Token，默认固定端口17575；配置自动端口时不承诺静态URL首次发现。
- thread 为请求最大并发，shards 为逻辑分片数，chunkSize 单位KiB，shards与chunkSize互斥；省略继承，显式请求→域名规则→全局配置。
- 每次覆盖不保存全局设置、不扩大线程池、不启用播放器自动代理开关。
- 无Range、HEAD沿用单连接；不支持安全Range分段时沿用现有回退。客户端断开取消，不拼接失效资源。
- 公开入口拒绝本机/私网/保留目标以及危险重定向，来源校验不能只靠CORS；为内部播放器预留会话和处理资源。
- 不引入依赖、native重建、Go代理；原实施阶段不推送远端。本次beta合并交付已获用户允许dev4 push、中文PR到beta及最终pull，阶段状态见下方当前记录。

## 基线、范围与验证
- 基线 dev4@567391cc17667050cccdcdcd6823b15ae6f20283；首次 git status 为空，0用户已有dirty文件。
- GoProxyAndroid参考 HEAD aca8a796f436e8b92288316266327daacfa14de6。
- guard：C123-public-proxy-api / standard。初次命令误用Windows系统bash（WSL服务不可用），改用G:/Git/bin/bash.exe成功。为加入本文件与评估索引重启guard，显式adopt的两测试/工具类均为本任务创建，不是用户已有修改。
- 允许路径：app/src/main/java/com/fongmi/android/tv/server/proxy/**、对应app/src/test/java/**，本文件、原代理评估文档与上游评估索引。控制栏/播放器自动选择代码不修改。
- 执行时间：2026-11-09 19:20 UTC+8开始，预计20:30前收口；实际阻塞为需求纠正与全工程unit源码共同编译。删除冗余探索，使用纯JVM隔离红绿后再进行一次定向Gradle整体验收。
- 验收命令：gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.server.proxy.*" --console=plain；双flavor arm64 Debug Java编译（不重建native、不跑全量测试）。

## 最佳实践与证据
访问日2026-11-09。A为官方/一手；B为成熟项目参考及直接本地源码。
| 类别/来源 | 证据与适用性 | 设计影响 |
|---|---|---|
| B，本地参考GoProxyAndroid main.go:20-41、proxy.go:35-66、184-218、275-290；HEAD见基线 | /proxy必填thread/chunkSize/url，chunkSize×1024；无正值上限、全网卡监听、跳过TLS验证；缺Range仍试探分块且200兼容不足 | 只参考参数形式，不复制引擎/监听/TLS/回退缺陷 |
| B，现有WebHTV ProxyRuntimeConfig、ProxyDomainRuleSet:98-120、RangePlanner:29-33、SegmentedRangeEngine:110-128 | 参数覆盖可沿已有sessionConfig下传；逻辑分片细化为≤1MiB传输块及100字节首块；并发受共享线程和B/S缓冲限制 | 重用现有引擎/首块优化，thread不承诺始终达到 |
| A，OWASP SSRF Prevention Cheat Sheet https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html （本地HTTP200读取） | 任意公网目标场景需IP/协议校验；预解析之后未校验的第二次DNS查询可绕过，连接必须只使用校验结果；redirect绕过需每跳阻断 | 公开客户端采用checked DNS、显式每跳URL/IP检查、禁止HTTPS降级与系统代理旁路 |
| A，MDN Origin https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Origin （HTTP200读取，页面修改2026-08-07） | sandbox iframe/file/data可发Origin:null；no-cors GET可不发Origin | null/file不能信任；组合来源/Fetch Metadata/Host校验，不宣称仅无Origin即可防所有浏览器攻击 |
| A，OkHttp 5.4.0 Dns源码 https://raw.githubusercontent.com/square/okhttp/parent-5.4.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Dns.kt （HTTP200） | lookup(hostname)返回连接候选地址列表，失败按下一地址重试；接口需线程安全、抛UnknownHostException。原官方API文档路径404已改精确版本源码，未使用master新增接口 | checked DNS必须接入实际连接；仅前置check后再由SYSTEM查DNS不足 |
| Issue/maintainer/field | OWASP直接引用GitLab URL blocker与SSRF Bible（DNS pinning/unsafe redirect）；实施前核对直接来源 | 将DNS/跳转视为必测攻击面，不做无界联网研究 |
| 学术论文、吞吐benchmark | 不改分段算法/ABI，非本阶段设计决策依据；已有代理评估记录慢源首帧结果，不复做性能论文调研 | 未实测吞吐/真机，不以编译或并发数宣称性能达标 |

### 备选对比与裁定
- 不修改：保持用户必须开开关、爬虫无法免Token直接用，不满足需求。
- 原样复制Go：参数简单，但引入JNI/ABI及Range/TLS/资源风险，不采用。
- WebHTV窄适配（采用）：仅增加公开入口/请求覆盖与安全准入，复用现有引擎；兼容代价为公开入口不能访问私网、超资源请求拒绝。
- Ruling：参数键存在但空白/null一律400，只有缺键继承；原简报“空白继承”与已确认表冲突，以规格为准。
- Ruling：Map<String,String>无法检测原始重复键；重复参数由HTTP接线层读取getParameters()多值表拒绝，解析类不伪造此能力。
- Ruling：Origin:null/file拒绝；Origin只有合法本机http(s)序列化可考虑，禁止userinfo/query/fragment及非空路径；真实Host绑定在HTTP接线层检查。
- Ruling：公共上游解析结果必须用于连接本身，默认独立安全客户端不经系统HTTP/SOCKS代理；IPv6 mapped/NAT64/6to4必须检查嵌入地址。
- Ruling：三个独立工具文件可并行实现，但Gradle --tests仍编译全部unit源码；隔离javac/JUnitCore红绿合法，最终同一工程验收。
- 审查返修1B（run2/3）：NAT64识别须精确/96（字节4..11全零），不能只检查前48位；IPv6公网判断须额外拒绝2001::/23特殊用途、2001:db8::/32与3fff::/20文档段。已带反例回派a3bb7d05。
- 新发现并路由：现有catvod OkHttp.getBuilder():243使用trust-all TLS/hostnameVerifier并有代理选择器。公共入口不得继承该客户端，须独立标准TLS的OkHttpClient、checked DNS与NO_PROXY；旧播放器客户端不修改。

## 任务书与接口

### 1A 参数覆盖（run2/3，负责人ec1ae24e；原f04088f6创建测试后停在等待）
创建ProxyRequestOverrides.java及ProxyRequestOverridesTest.java。
接口：record ProxyRequestOverrides(Integer rangeConcurrency,Integer shardCount,Long chunkSizeBytes)，NONE、parse(Map<String,String>)、isEmpty()、applyTo(ProxyRuntimeConfig)。
- [x] 测试已创建16项；缺类编译红26错误（24与本目标类有关，其余为并行guard缺类）。
- [x] 生产实现与16例定向绿；仅缺键继承，限制128/65536/64MiB，受检KiB转换，NONE返回原对象。

### 1B 目标守卫（run2/3，a3bb7d05，已完成）
创建UpstreamUrlGuard.java及Test。
接口：PUBLIC_ONLY、check(String/HttpUrl)、public InetAddress[] resolve(String host) throws IOException、包级可注入resolver。
- [x] 测试先行；生产实现已放行。
- [x] 14例覆盖IPv4/IPv6特殊段、映射/转译、混合DNS及异常，返修红2→绿；不依赖真实DNS。

### 1C 来源策略（原afea80a4唯一代码负责人；1d02仅只读审查）
创建ProxyClientOriginPolicy.java及Test；allows(Map<String,String>)。
- [x] 恒true red stub与Origin测试已存在；首次Gradle被其它未产出类阻断，不能称断言红。
- [x] 来源/Fetch Metadata/大小写/trim/空端口11例，隔离JUnitCore红绿及Gradle绿。
- 注意：send_message送达可唤醒已inactive代理；误重新派发被及时改为只读，未发生双写。

### 2 接线与生命周期（run3/3，已实现，两次160/0/0）
修改MultiThreadProxyServer/UpstreamRangeClient/ProxyPortController及必要ProxySessionRegistry/SegmentedRangeEngine，对应集成回归测试。
入口处理顺序：方法与来源/Host→重复/空参数→资源与URL校验→全局域名配置加request override→公共准入→安全上游客户端→同一分段引擎。
- 无Token公共GET/HEAD，原/v1/proxy仍鉴权；服务关闭自动代理开关后仍监听，但内部自动注册继续由现有enabled控制。
- checked DNS必须接入实际连接，禁系统代理/危险重定向；所有公开probe/shard/fallback/head走同一安全策略。
- 最多maxSessions-1公共活会话，无法预留内部名额时503；公共任务不得排队占满内部HTTP处理与Range线程，必须有取消/有界准入。
- 测试：默认disabled仍可健康检查/调用公共入口（策略/假客户端注入测试，不放宽生产公网限制）；旧鉴权401、无Range/HEAD单连、覆盖不改全局、关闭服务取消、跨域头剥离、私网跳转拒绝、公共名额不挤占内部。

### 3 独立验收与收口
- [x] 三个工具实现与红绿结果独立审查。
- [x] 接线初审S1取消与S2域规则已回派，第三轮稳定修复；安全复评通过，协调者独立验证S2/import/死代码。
- [x] 第三轮实际两次proxy包160/0/0/0；mobile编译成功，协调者leanback编译成功退出0，并读取最终XML。
- [x] 原实施阶段的本文件、评估索引及原需求状态已回填；本地提交与annotated recovery标签已闭环，坐标见当前记录；该阶段不push。

## 实施与审查记录
- Objective：本机免Token /proxy，自动可用但播放器默认不变，thread/shards/chunkSize请求级覆盖，安全资源边界同批。
- 2/run1最终证据：20:14交付，proxy包157/0/0/0（PublicRoute18、Controller4）；mobile Java编译成功22s；累计改7文件。该证据尚不能覆盖审查S1真实Call取消缺陷，已明确同一执行者继续run2，不能按自报直接验收。
- 2/run2被独立复跑否决（2026-11-10 08:50 UTC+8）：协调者亲跑proxy包得160例/1失败，单类PublicRouteTest亦21例/1失败，失败均为closingPublicClientCancelsBlockedUpstreamShardAndReleasesAdmission（expected at least 1 but was 0，≈10.05s）。判定：依赖真实socket RST的取消断言不可靠，并暴露“下游已断而上游停滞时Call未及时取消”的真实时序（传输线程与NanoHTTPD send均阻塞在读管道，无写失败可触发close）。已回派run3/3：生产保持既有链路、停滞由readTimeout收敛、关闭必cancel；测试改为直接驱动响应数据流/StreamingTransfer.close的确定性路径并保留真实断连为不悬挂断言；整包须连跑2次全绿并报两次XML与mobile编译。
- 工具环境事实：本机 --rerun-tasks 使pwsh runner崩溃两次（0xC0000409/0x40010004），后续用常规重复执行、避免把大输出重定向到构建目录。
- 2/run3（08:51开始，负责人自述）补充根因：旧取消例chunkSize=6使4MiB被细化为6字节块、throttle不阻塞单片，RST时可能无活动Call，canceled=0非纯OS抖动。修复方向：真实1MiB传输块+慢body并用responseBodyStart锚定；生产让StreamingTransfer.close直接触发state.cancel取消Call（不只等future中断finally），并修onCallStarted清空集合后的漏cancel竞态；不新增deadline。预计09:12交付，需连跑2次整包全绿后再进入收口。
- 独立审查必修S2已验证并并入同run2：默认enabled=false时DomainRuleSet.resolve:65跳过域规则，公共入口须resolveForPublic保留enabled原值并应用域规则。Boundary增补同模块ProxyDomainRuleSet.java，cap10文件；旧resolve内部语义不改。PublicRoute补默认disabled+非空规则及部分override真实HTTP测试。
- 规格终报归并run2：Server import分组与Controller死disable方法修整；production安全client factory加入NO_PROXY/checked DNS直接属性/拒绝私网候选测试（不联网）；参数键大小写敏感，未知键忽略，Thread不同于thread，不作为重复键。
- 配置边界待交付注明：serverWorkers=1时公开入口503以预留内部HTTP，rangeWorkers=1时公开Range回退单连；maxSessions=1无法预留内部会话，公开503。无Range仍单连，thread最大值受rangeWorkers限制，实际并发还减公共预留worker/预算。
- 进度（2026-11-09 19:49 UTC+8）：1A已绿16例/0失败；1B返修先红14例/2失败后Gradle绿，NAT64/96及IPv6特殊段已独立读审；1C严格Origin及空端口先红后绿11例，已独立读审。2接线由b550d6c1（run1/3）执行，允许9文件含SegmentedRangeEngine及必要生命周期测试，不扩大共享Range池。
- 纠正记录：协调者最初提供的3fff掩码不精确，已采用执行者验证的/20检查（0x3f,0xff,第三字节高4位0），并保留3fff:1000边界通过反例；未修改其它公网语义。
- 验证注意：共享Gradle XML目录会被不同--tests覆盖，已保存各类BUILD结果；最终一次proxy包运行汇总所有14/16/11例，不为了取回旧XML重复成功测试。
- 1B补取最终XML（代理已在取消重复前启动）：14/0/0/0，BUILD SUCCESSFUL 26s；此后不再重复单类测试。
- 2负责人方案：Engine请求上下文传安全upstream/reservedSlots，仍共享原Range池；public HTTP cap=serverWorkers-1并持有到body关闭，public Range预留1 worker给内部；预计20:21交付，实际预计7文件。
- 新增审查靶点已发负责人：公共许可须在DNS/probe等阻塞前取得；所有响应退出路径释放；精确Host/Origin authority、HTTPS downgrade及重定向userinfo/fragment校验。
- 独立审查必修S1，已复核并回派2/run2：TransferState.cancel仅Future.cancel，fetchShard的callObserver为null。需transfer-local跟踪真实probe/shard/retry/redirect Call并cancel，补慢body取消而非放开上游latch才完成的回归。持续时间论证已收窄：不主张未实证的无限滴流泄漏，确定当前同步socket read无法靠Future.cancel立即关闭，可占用到读超时/下一字节；修复针对即时取消，不新增全局deadline。
- 事实：docs索引尾随空格由tidy修复，git diff --check已无输出。
- 已知阻塞：Gradle --tests编译全部unit测试，缺类会误挡单类红；已改用隔离JVM或先取缺本类编译红。
- 风险路由：null Origin/DNS重绑定/共享资源争抢为必修；现有OkHttp.player为trust-all，公共入口必须独立客户端（已写入接线brief）。
- 原实施阶段回滚记录（提交前）：尚未提交，保留任务文件；验收后单提交+恢复标签。当前已提交坐标见下方记录。
- 最终实现（2026-11-10 09:12）：run3只改Server/Engine/PublicRouteTest，真实1MiB分片+body开始闩锁后显式关闭流验证Call.cancel；cancel注册竞态修复。公共流关闭直接取消Call，不承诺单纯RST立刻唤醒等待管道；上游完全停滞依现有readTimeout。
- 最终验证：两次实际proxy包执行160/0/0/0（31suite，PublicRoute21）；禁Call.cancel变异测试真红，恢复绿。09:15协调者读取最新XML确认160/0/0/0，取消例0.137s、域规则0.078s、生产client测试0.015s；mobile Java编译8s成功，协调者leanback编译4m29s成功退出0。
- 安全复评e764 run2：S1已解决，安全/质量通过，无新必修。规格复评cf2ed首次执行失败无业务结果，已原会话续接。
- 原实施阶段下一动作（09:15记录，现已闭环）：收集规格复评终报，回填收口记录并执行guard finish原子提交与recovery tag，不再重复构建或扩展研究。

## 当前交付与beta合并评审记录（2026-11-10）
- 原公共接口任务已提交：dev4 `b60a3952d26df2b2786c026a1df1c469dd17cdd6`；annotated标签 `recovery/C123-public-proxy-api/20261110012924-b60a3952d26d`。原guard `C123-public-proxy-api`已闭环，不重开旧finish。
- 此次合并：HEAD仍为上述提交；MERGE_HEAD为 `bf0f23c9326de676812334ad4b96d9b5e52c0335`；guard `C123-beta-review-dev4-20261110` / standard活跃，70个逐路径scope。beta入站54个staged路径，文档唯一冲突已保留双方章节；当前合并尚未提交，处于文档收口。
- 冻结验证tree：`5c9217207b75d142835ef2e0e4b0eae40b2e0304`。Standards报告 `aba02041` 硬违规0；低优先级possible配置复制按AGENTS/YAGNI不重构。Spec报告 `89ce3099` 必修0。主审独立读取公共入口/Call取消关键diff及C122/C124 blob等价证据，确认没有业务问题需要改代码；本次仅修标准评审指出的过期当前摘要/Recovery anchor。
- 验证者 `8ffa3265` 实际运行并由主审独立读取XML：leanback 57 suites / 353 tests / 0 failures / 0 errors / 0 skipped（proxy160、subtitle98、PlayerManager39、VideoDanmakuOwnership36、旧ProxyResponseConsumer6、QuickJS兼容14）；mobile 54 suites / 297 tests / 0 failures / 0 errors / 0 skipped（proxy160、subtitle98、PlayerManager39）。双端arm64 Debug Java编译与两端测试真实执行。
- 证据目录：`.git/C123-beta-review-dev4-20261110-evidence/`；日志 `build-01-leanback-compile-mobile-compile.log`、`build-02-mobile-unit.log`；XML `xml-01-leanback/`、`xml-02-mobile/`。QuickJS main编译FROM-CACHE、依赖UP-TO-DATE是缓存边界，兼容单测真实绿；主审接受该边界，不为缓存重跑。源码相对冻结tree的 `git diff --quiet <tree> -- app quickjs` 于10:49:52 CST退出0。
- 验证范围：未运行native/assemble/设备/真实TLS与吞吐；不把单测或缓存编译等同真机与性能验证。最终PR范围17文件（公共API9生产、5测试、3文档），不同于beta入站54路径。
- 阶段授权：原实施阶段“不push”作为历史保留；用户本次已允许dev4 push、中文PR到beta及最终pull。当前没有新合并commit/tag，也未push或创建PR；新交付坐标以协调者实际工具结果为准。
- 任务区分：本公共接口任务以 `C123-public-proxy-api.md` 为唯一记录；dev1上游同步同编号任务以 `C123-upstream-sync-silent1566.md` 为唯一记录，保留其历史事实。
- 当前下一动作：等待协调者独立文档复审；通过后由协调者执行当前guard finish、dev4 push、中文PR到beta及最终pull，不重复已成功构建或测试。

## 用户调用
应用后台服务运行时无需开启控制栏自动代理；仅本机17575（端口若修改请使用实际值）。
```python
from urllib.parse import quote
proxy_url = f"http://127.0.0.1:17575/proxy?url={quote(url, safe='')}&thread=10&shards=256"
# 或按1MiB分片，不能与shards同时出现：
proxy_url = f"http://127.0.0.1:17575/proxy?url={quote(url, safe='')}&thread=10&chunkSize=1024"
```
- 请求头继续携带视频源所需User-Agent/Cookie/Referer/Authorization；不需要代理Token；公网HTTP(S)目标，私网/本机及危险重定向拒绝。
- thread为并发上限，合法1..min(128,rangeWorkers)；shards为逻辑数量1..65536（不等于HTTP请求次数）；chunkSize单位KiB，合法1..65536且不超过每会话缓冲额度。
- shards和chunkSize互斥；省略继承，显式→域名→全局；仅本请求生效、不保存设置。参数键大小写敏感，未知键忽略，重复/非法/空白/超限400。
- HEAD/无Range单连；安全分段不成立则沿用回退。公共请求为内部预留1会话/HTTP worker/Range worker，实际并发可低于thread。serverWorkers=1或maxSessions=1公共503；rangeWorkers=1公共Range回退单连。
- 显式关闭响应流直接取消上游Call并回收许可。单纯TCP断连不保证立即唤醒等待管道；上游完全停止发送由已有readTimeout收敛，不承诺滴流总时限。
- 应用后台服务停止或被系统回收时端口关闭；自动端口发现、APK打包/安装、真机播放和真实TLS/系统DNS网络验证本轮未执行。

## Recovery anchor（当前beta合并文档收口）
- Objective：公共本机免Token /proxy，自动监听但播放器默认不变，thread/shards/chunkSize请求级覆盖；本轮只收口文档。
- Baseline：原dev4@567391cc17667050cccdcdcd6823b15ae6f20283、0用户初始dirty；原任务现已提交为 `b60a3952d26df2b2786c026a1df1c469dd17cdd6`，annotated标签 `recovery/C123-public-proxy-api/20261110012924-b60a3952d26d`，原guard已闭环。
- Merge：HEAD仍为上述原任务提交；MERGE_HEAD `bf0f23c9326de676812334ad4b96d9b5e52c0335`；当前guard `C123-beta-review-dev4-20261110`活跃，合并未提交。冻结验证tree `5c9217207b75d142835ef2e0e4b0eae40b2e0304`；本轮仅修获准文档。
- Evidence：当前双轴硬违规/必修均0；leanback 353、mobile 297 tests均0失败/错误/跳过，双端arm64 Debug Java编译与测试真实执行；证据及QuickJS缓存边界见当前交付记录。
- Routed risks：null Origin/DNS rebinding/IPv6旁路、会话/worker预留、Call取消竞态与默认disabled域规则已修并有回归；possible配置复制不重构；OS断连/readTimeout和native/assemble/设备/真实TLS/吞吐未验边界保留。
- State：业务评审与已执行验证通过，等待协调者文档复审；用户已授权本次dev4 push、中文PR到beta及最终pull，尚未执行；不推断新commit/tag/PR坐标。
- Next action：仅等待协调者独立文档复审；通过后由协调者执行当前guard finish、dev4 push、中文PR到beta及最终pull，不重开旧finish或重复构建/测试。