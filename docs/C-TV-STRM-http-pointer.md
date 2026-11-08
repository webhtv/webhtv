# C-TV-STRM HTTP pointer — decision-ready 设计与实施计划

> 给续接执行者：按 `executing-plans` 和 TDD 逐切片执行；本文件就是唯一任务记录，不创建第二份 plan/assessment。第一阶段为只读研究；第二阶段已获父节点完整实施授权，在指定隔离树执行。最新状态以末尾阶段2记录为准。

**Goal:** HTTP `.strm` 即使没有文件名响应头，也能从完整、成功、有界的小正文解析合法绝对媒体目标；失败、无效或不能确认完整时返回原始 URL。
**Architecture:** 仅在 `Strm.http` 内替换正文判定；保留原 `Source` 调用链、`OkHttp.noRedirect()`、本地文件读取与 match。可增加 Strm 内的私有 `parseTarget`，不增加共享 parser 层、URI resolver 或认证机制。
**Tech Stack:** Java / Android Uri / 现有 OkHttp 5.4.0 / JUnit 4.13.2 / 现有 Leanback Robolectric 4.16 / MockWebServer。
**Spec:** 本文件的「授权与行为契约」及「验收与 TDD」；阶段与唯一任务文档规则来自 canonical `.codex/skills/upstream-integration-governor/`。

## 1. 结论、授权与阶段边界

- **建议：实施窄适配（correct + supplement），不整文件 cherry-pick。** 缺少 Content-Disposition 的漏解析已由本地源码和上游实际差异建立；有界 peek、错误隔离、URI 语法检查可在 Strm 单文件内实现。[L1,U1,O1,O3]
- 用户已经批准这个窄修并说开始；本研究不再请求设计审批。**实施续接门槛只有父节点提供干净、隔离的实施树**，并确认其 HEAD/范围；不得在当前原 root 修改生产代码。
- 研究期间唯一允许写入：`.git/tv-implementation/design/C-TV-STRM-http-pointer.md`。父节点在实施树将它移为 `docs/C-TV-STRM-http-pointer.md`，并由父节点负责 assessment index 链接；不能写入受保护的原 assessment。
- 全部证据访问日：**2026-11-08（+08:00，China Standard Time）**。研究开工取时 14:32:49，原预计 14:40:49；14:43:26 的最后源码检查已超出 8 分钟执行目标。外部文档失败与替代源码读取造成延长，已停止可选扩展。该预计不是停工限额。
- **证据状态：decision-ready，可续接实施；不称“best-practice complete”。** 官方 API 起始网页失败、GitHub API 限流、PR 原始测试夹具未取得；适用语义已有确切官方源码/RFC/成熟实现替代证据，未验证本地运行效果，剩余门槛明确列在第 9 节。

### Global Constraints / 授权与行为契约

1. 原 root `F:\Workspace\fish2018\webhtv` 只读；基线 `Silent1566@47454687f504955775b398905ba09d73aeb9f20c`。
2. 保留原有未跟踪 `app/.cxx/` 及 `docs/upstream-player-dependency-merge-assessment-2026-11-08.md`；不得清理、提交、移动或覆盖它们。
3. 只处理 C-TV-STRM；不读取/补做 154 项全表，不合入同 PR 的 seek 功能。
4. HTTP 分支 peek 上限 **65536 bytes（64 KiB）**；返回 peek body 长度 **>=65536** 一律回原 URL，包括恰好 65536。宁可保守拒绝合法大指针，也不使用截断目标。[U1,O1]
5. HTTP 正文只取第一物理行；处理正文起始 BOM、首尾空格/制表符及 CR/LF/CRLF；**不跳过空白第一行去寻找后续 URL**，不读取多目标/注释/扩展播放属性。
6. 只返回语法有效、scheme 非空、scheme-specific-part 非空的绝对 URI 引用；不 normalize/resolve/URL-decode/重新编码签名参数。URI 含 fragment 可以作为媒体引用，不能把 RFC3986 的无 fragment `absolute-URI` 产生式误作媒体 URL 的限制。[O3,O4]
7. 保留 `match` 的 `.strm` 判断（本地仍大小写敏感）、本地路径与 `file://` 分支原行为、Source extractor 顺序/parse/header/DASH 路径；不增加 `content://` 输入支持、ContentResolver、UriUtil、新认证、递归请求或新平台协议处理。
8. 不改变全局环境或设置；研究不运行 Gradle。已给工具链仅供实施树续接：`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`，`ANDROID_HOME=C:\Users\Maple\AppData\Local\Android\Sdk`，Git Bash `G:\Git\bin\bash.exe`。
9. 研究预算：最多 **3 runs 累计，当前 run 1**；同失败假设最多 **2 attempts 累计**；编辑最多 **1 file 累计**且只有本文；只读命令不限累计；外部搜索每个实质问题最多 **3 queries 累计**；每批仅同一 STRM 问题，额度不结转。这里的研究限额不自动转成实施限额，父节点续接 brief 需单独声明。

## 2. 冻结来源、提交处置与历史

所有 cache Git 查询均使用 `git -c gc.auto=0 -c maintenance.auto=false --git-dir=.git/upstream-tv-assessment.git ...`，没有 fetch/checkout/维护命令。cache remote 为 `https://github.com/FongMi/TV.git`，标记 `[blob:none]`。

| 角色 | full hash / 路径 | 处置与证据 |
| --- | --- | --- |
| 原树基线 | `47454687f504955775b398905ba09d73aeb9f20c` | 只读；当前 Strm 没有新 HTTP 逻辑。[L1] |
| 本地 Strm 最近历史 | `3192791d221e685b12b1c9ca0ddccf5112eb574a`，subject `update` | 精确路径 log 返回；不能以 subject 推断能力，行为以 L1 为准。 |
| 唯一任务上游变更 | `7d70ebdfe56997ae9bccca7539a413a6999a09e3` | **candidate / narrow adapted**；完整 diff 只有 Strm.java，14 insertions / 4 deletions；不整文件替换。[U1] |
| 实际直接父 | `7bf115759881e27df819bb01a357f4d7094d295c` | 仅上下文；父树 Strm 已有 content/UriUtil/大小写行为，本任务不承接这些前置平台差异。 |
| 冻结上游头 | `4512b4c9f82534ff66acfde06ccdd68f35b074f0` | `merge-base --is-ancestor` 返回 0；candidate..frozen-head 的 Strm 路径 log 无后续项；final tree 的 Strm:33-47 与本任务候选 HTTP 改动一致。不是对全仓库无后续问题的证明。 |
| PR772 原提议 | `ba2d0b8dc11a7b658ee5c5e7c490acf573c4d8bb` | **provenance only**；PR 同时包括 seek 功能，不能 whole-PR 移植；FongMi commit message 明确 adapted from。原测试夹具没有随本候选提交进入树。[U2] |

### 可复现的关键只读查询

```powershell
git -c gc.auto=0 -c maintenance.auto=false show 47454687f504955775b398905ba09d73aeb9f20c:app/src/main/java/com/fongmi/android/tv/player/extractor/Strm.java
git -c gc.auto=0 -c maintenance.auto=false --git-dir=.git/upstream-tv-assessment.git show --format=fuller 7d70ebdfe56997ae9bccca7539a413a6999a09e3
git -c gc.auto=0 -c maintenance.auto=false --git-dir=.git/upstream-tv-assessment.git rev-parse '7d70ebdfe56997ae9bccca7539a413a6999a09e3^'
git -c gc.auto=0 -c maintenance.auto=false --git-dir=.git/upstream-tv-assessment.git log --format='%H %s' 7d70ebdfe56997ae9bccca7539a413a6999a09e3..4512b4c9f82534ff66acfde06ccdd68f35b074f0 -- app/src/main/java/com/fongmi/android/tv/player/extractor/Strm.java
```

## 3. 本地精确 call/data path 与已有契约（A 级）

以下 L* 均是原树 full hash `47454687f504955775b398905ba09d73aeb9f20c` 的文件；访问日统一见第 1 节。

| ID | 精确位置 | 观察与实施意义 |
| --- | --- | --- |
| L1 | `app/src/main/java/com/fongmi/android/tv/player/extractor/Strm.java:18-35` | match:19 只看 `UrlUtil.path(uri).endsWith(".strm")`；fetch:24 `startsWith("http")` 走 HTTP；25-26 `file` 前缀去掉 7 字符后 `Path.read(File).split("\\R",2)[0]`。HTTP:30 `noRedirect`，31-33 依赖 Content-Disposition 含 `.strm` 或 `.txt`，对满足头的正文无大小/状态/合法性检查。已有 header 指针正常输入必须继续成功；无头漏解析是要补的能力。 |
| L2 | `app/src/main/java/com/fongmi/android/tv/utils/UrlUtil.java:33-39` | path 使用 Uri 的 last path segment，null 返回空串并 trim；带查询参数的 `.strm?token=...`仍可 match，不改它。 |
| L3 | `app/src/main/java/com/fongmi/android/tv/player/Source.java:40-49,56-58,87-135,173-181` | Force/JianPian/Push 在 Strm 前；getExtractor 取首次匹配。fetch 把提取器输入设为 parse=0，再通过 `Extractor.fetch(Result)` 默认委派 String URL；只有 Youtube 在 99-108 捕捉特定失败，**Strm IOException 目前会往外抛**。随后 MpdSanitizer 与 MPV DASH resolver 均保留；因此“回原URL”在 Strm.http 内完成，而不能声称 Source 已提供该 fallback。 |
| L4 | `catvod/src/main/java/com/github/catvod/net/OkHttp.java:41,100-123,177-190,238-244` | 超时 30s；noRedirect 基于 client().newBuilder()，同时 `followRedirects(false)` 和 `followSslRedirects(false)`；newCall(client,url) 创建原 URL 的 GET，不传 result headers。继承现有请求/auth/response interceptors、DNS、proxy selector/proxyAuthenticator 和 TLS 配置；不能通过改 HttpClient 来偷渡认证、安全或超时修复。 |
| L5 | `app/src/main/java/com/fongmi/android/tv/api/SiteApi.java:150-193`；`app/src/main/java/com/fongmi/android/tv/api/LiveApi.java:44-49`；`app/src/main/java/com/fongmi/android/tv/web/WebHomeInlineVodStore.java:81` | SiteApi 多类点播路径把 Source.fetch 的结果写回 Result；LiveApi 亦同；inline VOD 直接使用。公共前置 extractor 改动会触达多个播放器消费者，不是 Exo/MPV decoder 改动。SiteApi:161-162/173-174 的部分 headers 在 fetch 后才补；本修不改变此流程。 |
| L6 | `gradle/libs.versions.toml:36,49,124-127,131`；`catvod/build.gradle:25`；`app/build.gradle:63-89,119,204-208` | 现有 OkHttp 5.4.0、JUnit、MockWebServer，Leanback 已具 Robolectric，unitTests.includeAndroidResources=true；不需要依赖升级、native 重建、lock/Gradle 编辑。flavors 为 leanback/mobile + arm64_v8a/armeabi_v7a。 |
| L7 | `app/src/testLeanback/java/com/fongmi/android/tv/ui/custom/FlagSelectionListenerTest.java:24-46` | 现有 RobolectricTestRunner + `@Config(sdk=28,application=Application.class)`，可复用 focused test 风格；并非已证明整个 CatVod 网络 singleton 在该环境无需初始化。 |

**静态到播放的链：** SiteApi/LiveApi/inlineVOD → Source.fetch(Result[,playerType]) → 首个 extractor.match(Uri) → Strm.fetch(String) → Strm.http → 现有 OkHttp.noRedirect GET → 返回 target/original string → 原有 MpdSanitizer / DASH 分支 → result.setUrl → 后续播放器。没有 Strm 内的第二次请求/循环解析。

## 4. 外部与上游证据登记

Grade 使用 canonical `references/evidence-and-research.md:27-39`：A=实际源码/规范/API；B=维护者解释/成熟实现；C=可复现实地资料；不能把生成摘要或标题作 A。所有表项都在 2026-11-08 实际读取。

| ID / grade | URL、revision/fullhash、精确定位 | 支持结论、适用性、限制与决策影响 |
| --- | --- | --- |
| U1 / A（source），B（intent） | https://github.com/FongMi/TV/commit/7d70ebdfe56997ae9bccca7539a413a6999a09e3 ；实际读取 cache full commit/diff/parent 与 frozen tree；`Strm.java@4512b4c9f82534ff66acfde06ccdd68f35b074f0:18,33-47` | successful→peekBody(65536)→长度>=cap回退→BOM+firstline.trim→scheme regex。commit message 明确缺 header、保留redirect/error/invalid、截断保护的意图。**源码 regex 不完整验证 URI，且网络/peek 异常仍外抛**；本地不能照搬整树前置 ContentResolver/UriUtil 行为。证据支持保留 core、补异常隔离和 syntax check。 |
| U2 / B（作者声明，不是本地验证） | https://github.com/FongMi/TV/pull/772 ；PR 原 commit `ba2d0b8dc11a7b658ee5c5e7c490acf573c4d8bb`（U1 full message）；页面 HTTP STRM/验证段及 FongMi 2026-10-30 引用 commit/关闭动作 | 作者说明无头漏解析，64KiB防整媒体读取，保留播放器redirect，UTF8 BOM/CRLF；声称独立 JVM 28 场景，其中 STRM 10。页面明确模拟 Android/HTTP，非真实网盘端到端测试。FongMi separate STRM commit 为维护者实际适配说明；没有评审列表，不夸大为正式 reviewed test suite。测试应在 WebHTV 补齐，不照抄“28通过”。 |
| O1 / A | https://raw.githubusercontent.com/square/okhttp/61423f472da24e0ccc42b6a2c0863fb27932fea5/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Response.kt#L220-L237 ；实际读取 tag `parent-5.4.0` 的同路径，`git ls-remote` 将 annotated tag `95c1c03d45fc935e91c2968859fd9add09f85af2` peel 到 full commit `61423f472da24e0ccc42b6a2c0863fb27932fea5` | 220-229 官方文档：peek返回最多指定 bytes，新 body；短体返回全部；长体截断；调用前不能已consume；会装入内存应有限额。232-237 源码 source.peek/request/复制min长度并以复制 buffer.size 建body。peek.contentLength 是副本实际字节数，不是相信网络 Content-Length。证实 exact-cap 不能区别完整/截断，保守>=cap拒绝；**API只承诺返回副本边界，不能宣称网络线速总接收绝对<=64KiB，也不证明耗时上限**。 |
| O2 / A | https://raw.githubusercontent.com/square/okhttp/61423f472da24e0ccc42b6a2c0863fb27932fea5/okhttp/src/commonJvmAndroid/kotlin/okhttp3/ResponseBody.kt#L171-L195 ；revision同 O1 | string() 消费整个所选 body，BOM 优先/Content-Type charset/否则UTF8；183-185 警告整正文可OOM；195 close。应只对 bounded peek 副本 string()，不是 res.body().string()；两层 try-with-resources。保留现有解码语义，不升级成独立charset识别器。 |
| O3 / A | https://www.rfc-editor.org/rfc/rfc3986.txt ；RFC3986 / STD66（2005-01，不是 Git对象）；实际文本行 903-921（§3.1）、1463-1480（§4.3），同时读取官方概述、§3/§4目录 | scheme=ALPHA*(ALPHA/DIGIT/+/-/.)；绝对与相对不同；scheme规范仍有额外规则。不能把 scheme:\\S+ 当严格合法完整 URI。STRM目标是带scheme的引用，可包含fragment；不添加相对resolution或判定URI一定可播放。 |
| O4 / A | https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URI.html ；Java SE21，constructor URI(String)、isAbsolute、URI/URL/URN 与 character categories | URI(String)做语法解析，isAbsolute依据scheme；无DNS、无scheme stream handler。官方明确其基础是 RFC2396+2732 并有Unicode等偏差，**不能称它完全RFC3986验证器**。适合标准库轻量语法门；原字串返回，绝不 toURL（未知scheme会引入handler限制）、normalize或resolve。 |
| O5 / A | https://www.rfc-editor.org/rfc/rfc9110.txt ；RFC9110（2022-06，不是Git对象）；实际行 2107-2125（§6.4.1）、3201-3216（§8.6）、6910-6928（§15.3/200）、7066-7083（§15.3.7） | 2xx=成功，不等于完整媒体指针；206是选定表示的一部分，客户端必须看range/type。请求不发Range且不实现重组，本窄修对206直接fallback，避免短而合法的“半截URL”被用作目标。Content-Length是octets/可能 framing，不作为唯一可信边界。无认证重试、无Range补请求。 |
| M1 / B（成熟代码），A（可核查代码事实） | https://github.com/xbmc/xbmc/blob/d1a1d48c3cb3722d39264ffdd8132f755ffecd27/xbmc/playlists/PlayListFactory.cpp#L83-L89 ；https://github.com/xbmc/xbmc/blob/d1a1d48c3cb3722d39264ffdd8132f755ffecd27/xbmc/playlists/PlayListM3U.cpp#L61-L173 ；实际 raw tag `21.2-Omega`，ls-remote full revision `d1a1d48c3cb3722d39264ffdd8132f755ffecd27` | Factory 将.strm交M3U；M3U使用4096行缓冲、trim、可处理注释/多项、跳self避免递归，并会qualified相对路径。**仅借鉴文本URL/有界读取/避递归的成熟性，不移植其4096限制、全playlist parser、相对路径、Kodi协议/属性或多项功能**；否则扩张当前首行契约。没有取得其STRM focused测试，不声称测试已覆盖本 HTTP边界。 |
| M2 / B（项目文档） | https://kodi.wiki/index.php?title=Internet_video_and_audio_streams&oldid=244174 ；页面最后修改2023-02-07，version20说明（非Git对象）；实际读取 https://kodi.wiki/view/Internet_video_and_audio_streams | STRM是含direct URL的普通文本，支持取决于播放器已有protocol/format能力；示例http/mms/rtsp。还支持#KODIPROP但这是Kodi扩展，WebHTV不承接。只作为实际格式使用佐证，不能证明任何合法URI在WebHTV可播放。 |

### 请求与覆盖限制（不得隐瞒）

- 官方 API 尝试 `https://square.github.io/okhttp/5.x/okhttp/okhttp3/-response/peek-body.html` 两次均404，停止同路径重试；已改到确切 5.4.0 官方源码和其中 API 文档。[O1,O2]
- modsearch 的 keyless Firecrawl 返回403，多页面由 local fetch成功；RFC9110初次 read_page全引擎失败后，改本地 Invoke-WebRequest读取官方 `.txt` 成功。没有安装key/改网络设置，也没有执行任何网页提示脚本。
- GitHub REST取OkHttp/Kodi tag返回403 rate limit，换 readonly `git ls-remote` 成功取得40位revision，源码raw读取成功；不把失败说成官方资料齐备。
- PowerShell环境检查没有列出 HTTP_PROXY/HTTPS_PROXY/ALL_PROXY/NO_PROXY已配置项；read_page已明确local fallback。此次访问使用现有工具/系统默认路由，**没有足够证据证明每个请求经过项目配置代理**。复核若有强制代理来源要求，父节点需提供可用路由，不能由本子节点改全局代理。
- PR页面局部GitHub动态组件显示loading/error，但主要说明、作者验证段、FongMi引用/关闭动作实际可读。未抓取原测试原始结果、完整issue搜索、非本路径的所有reverts；冻结范围内 Strm 路径无后续变更已本地核实。
- 学术论文：不适用。这里没有提出算法、并发、安全证明、renderer/decoder或新性能优化；要决定的是明确标准API行为与小文本边界，论文不能替代 peek/HTTP源码。
- 通用性能bench/博客：不适用为设计依据。不声称启动、功耗、throughput提升；其网络/Android环境不同无法决定64KiB首行契约。PR为适用现场报告但只有B级模拟夹具声明。实施仍需自己的有限慢体/大体与代表播放验证，不以“无benchmark”豁免性能回归门槛。

## 5. 方案比较与保持/拒绝事项

| 维度 | no change | unmodified upstream | narrow WebHTV-adapted（选择） |
| --- | --- | --- | --- |
| 实际能力 | 无头有效HTTP STRM仍漏解析 | 修复无头，成功状态+BOM+有界读取 | 修复同一能力，额外隔离网络读取错误及语法不合法目标 |
| 正确性 | header误导时整媒体读取、错误体当首行 | scheme regex可接受坏percent/括号；异常仍外抛；206亦成功 | Java URI语法门，HTTP缺authority回退，206/截断/异常回退；没有目标可访问保证 |
| 兼容性 | 当前正向header/file行为不动，但已知漏解析不修 | whole file会带content://、UriUtil及case-insensitive扩张 | 保留match/file/Source/header/noRedirect；有效header正常指针继续成功；旧“错误/相对/无效/大体目标”被明确批准的失败回退契约替代 |
| 内存/性能 | `.body().string()`无体大小界 | 副本<=64KiB，仍需等到EOF或cap/原30sreadtimeout | 同上；可用已知length>=cap早退（只作优化，不信任header证明短体完整）；不宣称总wire bytes或latency固定界 |
| 生命周期/错误 | 原Response关闭，但抛错影响播放选择 | response+peek关闭，但Exception往外 | 每条分支关闭资源；HTTP I/O/URL构造异常→original，file失败保持Path.read空串合同；不catch Error，不改stop/exit |
| 安全/认证 | 既有TLS/auth/proxy行为 | 整文件引入平台读取行为 | 不改TLS/认证/handler；不跟Location、不发目标请求、不增加跨host header转发；本修不是SSRF/TLS全面治理 |
| 质量/ABI/API/包 | 无新产物 | 若全文件会承接平台API差异 | Java类内部小改，没有native/decoder/output/ABI/依赖变化；不承诺APK字节零变化，只无新依赖或二进制资产 |
| provenance/维护 | 不承接补丁 | 有upstream lineage但不合本地树 | fullhash记录，手工窄移植与focused tests；无需泛化parser；未来diff容易理解 |
| rollout/rollback | 无候选 | 更宽回退单位 | 单原子Strm+focused test+唯一doc，可revert；已有错误回退由后续播放器处理 |

**其他可行替代并拒绝：** 要求服务器补Content-Disposition（不可控且漏已有合法输入）；HEAD/Range探测（加请求、Range/认证契约）；直接读整body（无界内存）；只读到首个换行即接受（无法保证整个响应不是截断/伪装媒体）；Kodi全playlist parser（不批准的comments、多目标、relative、property）；content URI迁移（明确不在此任务）。

## 6. 精确最终行为与候选最小实现

### HTTP 分支的顺序

1. 用**原URL**、**现有noRedirect**发唯一GET；不是播放器 client、不自行携带 Result headers。
2. 非2xx立即返回原URL，且不peek；206也返回原URL，因为本任务不拼接部分表示。[O5] 204/205空体在后续空内容路径回退。
3. 若原body已知 contentLength >=65536，可立即回退以省读取；未知/短声明仍必须bounded peek。禁止以 Content-Type/Disposition或短Content-Length单独证明合法。
4. try-with-resources取得 `res.peekBody(65536)`；副本 actual length >=65536回退。小体必须peek成功至EOF；short-read/framing IOException回退，不能把已经读到的部分当pointer。
5. 对副本 string()，按旧OkHttp的BOM/charset/UTF8语义解码；最多移除正文开头一个U+FEFF，然后 `split("\\R",2)[0].trim()`。
6. 空行、relative、scheme-empty、无scheme-specific-part、语法异常、行内控制/空白回退。以标准库URI作为轻量语法门，不自称完整RFC3986/scheme-validity验证器；HTTP(S)额外要求非空authority，拒绝`http:///movie`等。其他已可由播放器理解的合法scheme字串只原样传递，不在Strm新增protocol handler。
7. 成功只返回该清理后的第一行字串，不返回URI序列化结果，不请求目标；第二行即使也是URL也忽略。目标等于原URL仍只返回一次，不循环。
8. HTTP execute/peek/string 的 IOException（含timeout/truncated framing）以及HTTP URL构造 IllegalArgumentException回原URL；不改变file分支的Path.read空串失败合同，不catch Throwable/Error。

### 设计代码形状（阶段2已按 TDD 落地，实际结果见第11节）

允许 imports：`java.io.IOException`、`java.net.URI`、`java.net.URISyntaxException`、`okhttp3.ResponseBody`；删除不再使用的 `HttpHeaders` import。不要动 `match`、`fetch` 的非HTTP部分。

```java
private static final long MAX_STRM_BYTES = 64 * 1024;

private String http(String url) {
    try (Response res = OkHttp.newCall(OkHttp.noRedirect(), url).execute()) {
        if (!res.isSuccessful() || res.code() == 206) return url;
        if (res.body().contentLength() >= MAX_STRM_BYTES) return url;
        try (ResponseBody body = res.peekBody(MAX_STRM_BYTES)) {
            if (body.contentLength() >= MAX_STRM_BYTES) return url;
            return parseTarget(body.string(), url);
        }
    } catch (IOException | IllegalArgumentException e) {
        return url;
    }
}

private String parseTarget(String content, String original) {
    if (content.startsWith("\uFEFF")) content = content.substring(1);
    String line = content.split("\\R", 2)[0];
    for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        if (Character.isISOControl(c) && c != '\t') return original;
    }
    line = line.trim();
    try {
        URI target = new URI(line);
        if (!target.isAbsolute() || target.getRawSchemeSpecificPart().isEmpty()) return original;
        String scheme = target.getScheme();
        if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && (target.getRawAuthority() == null || target.getRawAuthority().isEmpty())) {
            return original;
        }
        return line;
    } catch (URISyntaxException e) {
        return original;
    }
}
```

**精确定义/不确定边界：** URI是generic syntactic reference；例如未知scheme不能证明可播放，Java允许一部分Unicode而并非ASCII-only RFC3986实现，HTTP的scheme-specific合法性/host连通性仍由原播放器处理。这里没有新增host/DNS验证、IDN转换、scheme allowlist、content resolver、header语法或percent decoding。若需要完整HTTP host/port校验或禁止某scheme，必须先由父节点确认是否改变现有有效target合同，不能悄悄加严。上述候选针对已批准合法绝对引用和明显非法输入，不声称防住所有“像URI的错误文本”。

## 7. 允许路径、seams、验收与可执行 TDD

### 候选允许路径（实施树，仅此任务）

- Modify `app/src/main/java/com/fongmi/android/tv/player/extractor/Strm.java`：HTTP行为与私有helper。
- Create `app/src/testLeanback/java/com/fongmi/android/tv/player/extractor/StrmHttpTest.java`：真实Strm公共fetch/match、loopback HTTP和file回归；依赖现有Leanback Robolectric/MockWebServer，不编辑Gradle。
- Move/continue `docs/C-TV-STRM-http-pointer.md`：就是本文唯一文档。assessment index由父节点另行协调，不能顺手纳入本原树写范围。
- **明确不允许：** Source.java、UrlUtil、catvod production、Gradle/locks、native/assets、其它播放器parser/encoder、任何全局配置。fixture可在单testclass里内联，不创建泛化test架构或新parser。

### Seams 与最小测试环境

测试只经 `new Strm().fetch(url)`、`new Strm().match(Uri.parse(url))`观察返回值；不测试私有parseTarget、不反射改解析器。HTTP服务器是外部transport边界，输入真实响应；不Mock URI/OkHttp peek语义。test class复用 L7 的 Robolectric runner/SDK28/Application.class，用现有 `okhttp3.mockwebserver` 模块。

初始最小 tracer test（标准testclass imports见代码；这是计划，不是已经通过的夹具）：

```java
package com.fongmi.android.tv.player.extractor;

import static org.junit.Assert.assertEquals;
import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class StrmHttpTest {
    @Test
    public void successfulPointerWithoutFilenameHeaderReturnsFirstTarget() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("https://media.example/movie.mp4\n"));
            server.start();
            String input = server.url("/movie.strm").toString();
            assertEquals("https://media.example/movie.mp4", new Strm().fetch(input));
            assertEquals(1, server.getRequestCount());
        }
    }
}
```

若真实CatVod singleton/代理/拦截器需要Context初始化，先按实际失败栈在**同一testclass**初始化既有入口；不得通过修改生产OkHttp、反射替换解析器或“mock通过”掩盖运行失败。若最小测试任务触发native配置/预置资产缺失，分类为环境门槛并交父节点，不能在原树用Gradle探路。选择loopback IP避免外部DNS依赖，记录实际request method/path及redirect目标request count。

### 测试真值矩阵（逐切片实现，不批量先写所有tests）

| 切片 | 输入/响应 | 独立预期与必须断言 |
| --- | --- | --- |
| T1 RED→GREEN（核心复现） | HTTP200 `/movie.strm`，无Content-Disposition，`https://media.example/movie.mp4\n` | 返回literal target；仅一个GET，path保持原query；未访问target。原基线必定返回input，应观察断言失败而不是环境失败。 |
| T2 旧正向合同 | 相同小正文，分别Disposition `.strm`、`.txt`；无头与错误/其它filename | 全部返回同literal target；新版不再依赖头。 |
| T3 BOM/空白/首行 | UTF8 BOM + `\t https://media.example/a.mp4?sig=a%2Bb&x=1 \t\r\nhttps://other.example/b`；LF、CR | 返回清理后的第一target，签名percent/query保持原字串；second URL不请求。`\u001b`等控制字符藏在line内部应回原URL。 |
| T4 空/非地址/relative | `""`、`" \t\r\nhttps://valid.example/a"`、HTML、JSON、`/media/a`、`//host/a`、`https:`、`http:///a`、`https://host/%GG`、坏括号 | 返回input，不跳过空第一行，URI syntax失败不抛到Source。 |
| T5 保留已有target表达能力 | `https://host/a#track`、`rtsp://host/stream`、`file:///sdcard/a.mp4`、query `%2F/%2B` | 返回原target字串；不声称验证播放器实际protocol支持，不递归、不打开content handler。 |
| T6 redirect/error | 301/302/307/308且Location指另一个loopback server；401/403/404/500正文刻意放合法target | 返回input；redirect server requestCount=0；不把error body当pointer。 |
| T7 2xx不代表完整pointer | 204/205空体；206含有效短URL且Content-Range | 全部input；206不重组、不补Range请求。 |
| T8 cap边界 | 完整小体；65535 bytes（URL后可用空白填充）；65536；65537；大binary/media；firstline虽有效但total>=cap | 65535完整有效时target；>=65536始终input，不因为第一行短而接受；不能仅断言常量。 |
| T9 framing/未知长度 | chunked小体合法；chunked到cap；Content-Length声明比实得大、body中途断连 | 小完整target，其余input；没有利用部分合法prefix；IOException不泄漏。原始网络Content-Length不是唯一判据。 |
| T10 请求失败/资源/耗时 | 关闭loopback listener后fetch；慢体/断连；大体后正常body；多次fetch | HTTP failures返回input；随后正常请求仍成功（资源关闭）；只一次提取请求，无目标预读。慢体不要求小于原30sreadtimeout，只排查整媒体无界读取/EOF等待以外新增阻塞。 |
| T11 旧file/match | 临时 `.strm` 首行普通媒体URL，多行；显式file://与裸path；`.strm?x=1`、`.STRM`、`.txt`；缺失file | 保留file第一行，BOM/trim新规则不误套到file；小写strm match=true，uppercase和txt保持false；缺失file保持空串（`Path.java:135-140`捕获读取异常，生产分支不变）。 |
| T12 公共消费smoke | 一份无头真实HTTP STRM及直接媒体redirect/大体fallback，原点播入口 | Exo与MPV各观察实际最终URL与正常播放/错误fallback；记录sample/设备/设置/player/候选commit，不能用一个播放器结果替代另一个。无需decoder/native矩阵。 |

**行为验收：** T1-T11的相关返回值/请求次数/异常边界全部成立；无Source/match/file/headers/noRedirect改动；无新增content/认证/recursive fetch；source/test/doc唯一范围；`git diff --check`通过。T12为发布前代表链路门槛，不把本研究静态证据当设备通过。

### 逐切片执行与命令（只在父节点提供的实施树）

- [ ] 父节点给干净隔离树；记录该树absolute path、branch、full HEAD、protected dirty，本文移到唯一docs位置。若HEAD不同先只比较Strm/Source/OkHttp相关漂移，不重读全表。
- [ ] 在已声明scope内开启一个task guard：
  ```bash
  bash .codex/scripts/task_guard.sh start --id C-TV-STRM --mode upstream \
    --scope app/src/main/java/com/fongmi/android/tv/player/extractor/Strm.java \
    --scope app/src/testLeanback/java/com/fongmi/android/tv/player/extractor/StrmHttpTest.java \
    --scope docs/C-TV-STRM-http-pointer.md
  ```
- [ ] 只先加入 T1 tracer test并运行精确方法，确认RED是literal target versus原URL：
  ```powershell
  $env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'
  $env:ANDROID_HOME='C:\Users\Maple\AppData\Local\Android\Sdk'
  & 'G:\Git\bin\bash.exe' ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.player.extractor.StrmHttpTest.successfulPointerWithoutFilenameHeaderReturnsFirstTarget' --no-daemon
  ```
  这些是当前进程env，不写全局；workdir必须是实施树。Gradle任务名按 L6 现有flavors拼出，尚未执行发现，不写为“已验证任务”。若直接报unknown task，按实际task name改正确调用一次（属于invocation修正），不跑所有variants来猜。
- [ ] 按 T1 先完成success+bounded读取最小GREEN；随后 T2→T11每轮只增一个行为test和最小生产改动。T4/T7/T9在各自RED后加语法/206/异常规则，禁止提前无关抽象。
- [ ] 跑一次整focused class（每次失败仅相关修改后重跑），检查scoped diff：
  ```powershell
  & 'G:\Git\bin\bash.exe' ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.player.extractor.StrmHttpTest' --no-daemon
  git -c gc.auto=0 -c maintenance.auto=false diff --check
  git -c gc.auto=0 -c maintenance.auto=false diff -- app/src/main/java/com/fongmi/android/tv/player/extractor/Strm.java app/src/testLeanback/java/com/fongmi/android/tv/player/extractor/StrmHttpTest.java docs/C-TV-STRM-http-pointer.md
  ```
  记录原始失败/通过output与test report；环境失败不等于RED或功能失败。只做此focused JVM门槛，不安排全Gradle/native/ABI矩阵。
- [ ] 完成 T12 或由父节点明确记录具体sample/device门槛；没有代表链路证据时只交candidate，不称发布通过。若需出APK，编译最小实际设备variant，不扩建native。
- [ ] 写入实际结果/来源/commit记录到同doc；经父节点review及任务guard安全验收，一次原子关闭source+test+doc：
  ```bash
  bash .codex/scripts/task_guard.sh finish \
    --verified 'focused StrmHttpTest results and scoped diff check recorded in docs/C-TV-STRM-http-pointer.md' \
    --commit-message 'player: resolve bounded HTTP STRM pointers without filename headers'
  ```
  guard负责scope、commit与unique annotated recovery tag；不自己stage脏路径，不另建documentation-only平行文件、不push。若实际guard只提交未tag，紧接一条signing-disabled annotated local tag命令，不在commit/tag间跑测试或研究。

## 8. Rollout / rollback / 风险路由

- rollout：先隔离树focused候选，后同sample Exo/MPV代表点播验证，再交父节点决定发布；不新建feature flag/配置体系。普通非.strm媒体完全不增加探测；原.strm正向header/file回归必须先通过。
- 观察：记录输入URL（脱敏签名/认证）、status、返回原URL还是target、single request计数、selected player、测试/样本ID；不把整个body/secret写生产日志，不要求新增日志框架。
- rollback基线：实施树与基线相同时为 `47454687f504955775b398905ba09d73aeb9f20c`；不同则实施开始记录其**实际full HEAD**，不能沿用旧值。已发布/共享改动优先 `git revert <实际C-TV-STRM原子提交fullhash>`，随后只跑focused关键旧header/file + 原播放smoke；不运行`reset --hard`，不触碰当前root dirty。
- 未提交candidate回滚：在干净实施树内，经guard/父节点确认task-owned范围后只恢复本任务source/test/doc；原root无待回滚生产改动。source/test/doc同一逻辑单位，无lock/artifact耦合回滚。

| 已注意风险 | 路由与拒绝带病发布条件 |
| --- | --- |
| whole upstream带content/大小写/UriUtil | 仅手工HTTP窄移植；diff必须无match/file/Source变化，否则退回重做。 |
| Source对Strm不捕异常 | 在Strm.http隔离I/O，不改Source；T9/T10负责。 |
| exactcap不能区别完整 | >=cap保守拒绝，T8/T9；不把64KiB精确长度的漏解析列为未知回归。 |
| 206半截URL | 直接fallback，T7；不新增Range重组。 |
| URI regex不是合法语法 | 标准库syntax gate与T4；generic URI不等于可播放/完整scheme-specific校验，第6节明确边界。 |
| 不同scheme/platform能力 | 不加content输入/handler/allowlist/recursive resolver；合法字串由既有消费者处理，M1/M2不移植。 |
| 大/慢body的启动与wire额外读 | capped副本/knownlength早退/原timeout保留；实施T8-T10与代表smoke观察，不声称任意网络latency硬界或性能已测。 |
| CatVod singleton test环境 | 在同focused testclass按实际失败栈初始化既有入口；若环境仍阻断明确交父节点，不能mock出“通过”或改production transport。 |
| 外部资料/API/代理访问限制 | 已列失败与源码替代，不称best-practice complete；如治理要求必须有configured proxy证据，交父节点补路由，不擅改设置。 |
| 原树脏路径 | 当前只写git design；移文/guard/commit必须在父给干净树，不直接原root实施。 |

## 9. 第一阶段历史 Recovery anchor / hand-off

- objective/acceptance：仅C-TV-STRM成功完整小体首行absolute target；无效/错/redirect/部分/截断/请求读取失败回原URL；保留file/match/Source/noRedirect，不加content/认证/递归。
- research status：本地与exact upstream/parent/frozen path核对、官方5.4.0源码与RFC、PR772、Kodi实现已完成；唯一设计文完成，**生产/测试/Gradle/commit/tag均未执行**。
- changed file：仅当前 `.git/tv-implementation/design/C-TV-STRM-http-pointer.md`；最终唯一任务文档为实施树 `docs/C-TV-STRM-http-pointer.md`。
- baseline/protected：`Silent1566@47454687f504955775b398905ba09d73aeb9f20c`；原未跟踪app/.cxx及2026-11-08 assessment保持只读；research git diff --numstat无tracked生产diff。
- unresolved execution gates：父节点提供干净隔离实施树；运行最小public-fetch RED/GREEN，处理实际CatVod test初始化/Gradle环境错误；T12真实sample/Exo/MPV发布smoke。外部原PR tests与代理路由不确定，不能夸大研究完整或本地测试通过。
- **唯一下一动作：父节点审阅本文后交给同任务执行者干净实施树absolute path/full HEAD与允许source/test/doc范围，以T1 tracer RED开始续接；不重新申请已批准设计、不重新研究全表。**

## 10. 阶段2实施记录（2026-11-08 +08:00）

- 父节点已审阅设计并批准完整实施；本阶段 run 1/3，仅执行本窄边界。
- 实施树：`F:\Workspace\fish2018\webhtv\.codex\task-state\worktrees\tv-small-fixes-strm`，branch `upstream/strm-20261108`，开工 `15:05:09 +08:00`，预计 `15:26:09`。开工status空；基线/回滚HEAD `47454687f504955775b398905ba09d73aeb9f20c`。
- 集成索引由父节点在集成branch提交：`60f2e86e761b8411e56a86793ee55a2f0b9b67d7`；本树不复制原root未提交索引、不修改跨任务索引。
- `.git/.../design`源已复制为本文件，后续只更新本文件；原root保持只读。
- task_guard 已用 `--id C-TV-STRM --mode quick-fix` 开启，scope为Strm、StrmHttpTest、本文以及忽略的`.codex/task-state/reports`；protected initial dirty=0。报告/log经check-ignore确认忽略，不stage/commit。
- tracer T1先写真实MockWebServer + Strm.fetch；精确Gradle方法调用已启动，尚待RED结果；没有生产修改。进程环境仅设置给定JDK21/SDK/core.longpaths，无全局设置。
- 已见Gradle准备Chaquopy本树构建需求，未自行运行native/全矩阵/安装设备命令。初始时间预算若延长，只保留必要TDD与闭环，不扩展研究。
- 当前唯一下一动作：确认T1失败是literal target对原URL，随后最小GREEN，逐切片补边界。
- 父节点闭环决定：一次原子source+tests+doc提交；本文在提交前记录真实测试/基线/恢复tag规则及查取命令。最终40位commit/tag写忽略报告与父回报；不为自引用开第二docs commit、不重测unchanged code。父节点统一归档阶段再补本文/索引精确code commit/tag。
- T1：baseline真实ComparisonFailure（expected媒体URL，actual原`.strm`），RED 1 test/1 failed；最小移除header门后GREEN 1/1。日志`C-TV-STRM-t1-red.log`、`C-TV-STRM-t1-green.log`。
- T3：实际返回含首尾tabs/spaces的首行，RED 1/1 failed；添加BOM/trim后GREEN 2/2（含T1）。T4：空第一行被返回空串而非原URL，RED 1/1 failed；正在验证URI gate GREEN。均真实public fetch，未使用mocked OkHttp或私有parser测试。

## 11. 实施完成与当前 Recovery anchor

### 实际生产改动

- 仅`Strm.java`：65536 bytes peek，known-length/actual peek均`>=cap`原URL；非2xx/206原URL；BOM/首行trim、URI generic syntax与HTTP(S)非空authority；HTTP IOException/IllegalArgumentException原URL；两层try-with-resources。
- 实际T4反例：Java trim会吞掉行尾ESC，使非法行变成合法URL。已在trim前拒绝非tab ISO-control，NUL/ESC/行内tab回归通过。保留允许的边缘tab/space，不加新protocol handler或parser层。
- `match`、`fetch`的file/裸path分支、Source/UrlUtil/catvod/Gradle/native/locks/assets均不改。
- 原设计缺失file描述已纠正：基线`47454687f504955775b398905ba09d73aeb9f20c`的`catvod/src/main/java/com/github/catvod/utils/Path.java:135-140`捕获读取Exception返回空串；真实public fetch验证missing file返回`""`，本地BOM/首尾空白继续原样保留。
- test新增唯一`app/src/testLeanback/java/com/fongmi/android/tv/player/extractor/StrmHttpTest.java`，16个JUnit test methods；真实Strm/OkHttp/MockWebServer，不替换production singleton，不测试private helper、不新增初始化入口。

### TDD与最终验证证据

| Slice | 真实RED | GREEN整class累计 | 日志前缀（忽略报告目录） |
| --- | --- | --- | --- |
| T1 无header | ComparisonFailure：期望literal媒体目标，实际原localhost `.strm`URL；1/1 failed | 1/1 PASS | `C-TV-STRM-t1-red.log`、`-t1-green.log` |
| T3 BOM/首行trim | 期望清理后target，实际含首尾tabs/spaces；1/1 failed | 2/2 PASS | `C-TV-STRM-t3-red.log`、`-t3-green.log` |
| T4 URI/invalid | 空行返回空串而非原URL；1/1 failed；首次GREEN额外暴露末尾ESC被trim吞掉 | 控制字符保护后3/3 PASS | `C-TV-STRM-t4-red.log`、`-t4-green.log`、`-t4-green-control.log` |
| T6 error status | HTTP401合法URL正文被当目标；1/1 failed | 4/4 PASS | `C-TV-STRM-t6-red.log`、`-t6-green.log` |
| T7 partial | HTTP206合法短URL被当完整目标；1/1 failed | 5/5 PASS | `C-TV-STRM-t7-red.log`、`-t7-green.log` |
| T8 cap | 65536 bytes body仍返回首行target；1/1 failed | 6/6 PASS | `C-TV-STRM-t8-red.log`、`-t8-green.log` |
| T9 I/O containment | 真实socket半体造成ProtocolException `unexpected end of stream`；1/1 failed（不是classpath/fixture错误） | 7/7 PASS | `C-TV-STRM-t9-red.log`、`-t9-green.log` |

随后补既有合同characterization（不新增生产逻辑），不是把已有正常行为冒充RED：Disposition变体、合法其它scheme/fragment/escapes、301/302/307/308不follow、unknown-length/chunked 65535/65536/65537、完整传输512-byte二进制body和HTML、file/match合同、Source.fetch三player参数链、fallback后下一请求/慢小体。

最终命令（实施树workdir）：

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'
$env:ANDROID_HOME='C:\Users\Maple\AppData\Local\Android\Sdk'
$env:GIT_CONFIG_COUNT='1'
$env:GIT_CONFIG_KEY_0='core.longpaths'
$env:GIT_CONFIG_VALUE_0='true'
.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.player.extractor.StrmHttpTest --console=plain
```

- **最终有效结果：16 tests，0 failures，0 errors，BUILD SUCCESSFUL，1m42s。** 日志绝对路径：`F:\Workspace\fish2018\webhtv\.codex\task-state\worktrees\tv-small-fixes-strm\.codex\task-state\reports\C-TV-STRM-final-test-literal.log`。
- 前一轮最终16/16亦PASS；自检发现Source header断言共享预期map引用，改为独立literal `Map.of("Authorization","Bearer retained")` 后才执行上述最终有效轮。此次相关test edit是重测原因，不是unchanged reassurance。最终代码/测试不再重测。
- Source public consumer验证EXO/IJK/MPV三个player参数：正常target/HTTP500 fallback、parse=0、literal header内容保持、不新增Result header转发；**不是三个decoder真实实播**。
- 二进制case是完整HTTP512-byte deterministic binary payload并标记video/mp4，不声称它是decoder-valid电影样本或完成任何codec验证。
- 原项目既有Gradle deprecation/incubating警告仍出现；没有为此改Gradle或扩范围。测试没有classpath/Context初始化门槛，真实默认transport可运行。

### 关闭、rollback与精确ID查取

- 本树branch仍为`upstream/strm-20261108`；开工clean，base/rollback为`47454687f504955775b398905ba09d73aeb9f20c`；source/test/doc三个受控文件，忽略logs/report不stage。
- 最终scope、旧match/file方法一致性和whitespace audit完成后，执行一次`task_guard.sh finish --verified <上述真实命令/16PASS与scoped diffcheck> --commit-message 'fix(strm): resolve bounded HTTP stream pointers'`。guard原子提交并立即创建annotated `recovery/C-TV-STRM/<timestamp>-<commit12>` local tag。
- 按父节点明确决定避免commit自引用：本次精确40位commit与实际tag写忽略`C-TV-STRM-report.md`及父回报；父统一归档阶段再补本唯一doc/索引。不在本任务额外docs commit或amend/tag移动。
- commit后查询：`git log -1 --format=%H`；精确tag从忽略`C-TV-STRM-finish.log`中的`PASS: annotated recovery tag ...`读取。不要为确认成功tag重复验tag命令。
- 已共享/发布需回滚时：以报告中的实际code commit fullhash执行`git revert <code-commit>`；source/test/doc同一原子单位，无lock/native联动。只回归关键header/file/原播放合同，不破坏原root脏路径或hard reset。

### 当前状态 / 唯一下一动作

- 已完成：获批窄修、7个新行为逐片RED/GREEN、16个最终focused tests PASS、精确代码/测试自检；原root只读。
- 未做且不假装已做：设备安装、Exo/MPV decoder实播、native/ABI全矩阵、APK发布、远端push/tag。T12真实sample实播是父节点发布前门槛，本阶段只做Source public链验证。
- 研究外部API/代理/原PR tests限制仍见第4节；未称best-practice complete或端到端发布通过。
- 唯一下一动作：完成一次guard scope/commit/tag闭环并将忽略report交父独立review；随后由父决定集成与真实sample验证，不重开设计审批、不重测unchanged code。

## 最终集成归档 — 2026-11-08

- 用户已批准本任务窄适配，原始代码提交及恢复点在合并历史保留，不重写。
- 代码/修复提交：`38d2670e10eb69fa8c00c3bbd8df48b07d98f1db`
- 本地恢复 tag：`recovery/C-TV-STRM/20261108074718-38d2670e10eb`
- 单元证据：16项定向测试通过；HTTP/Source公共链，不等于decoder实播
- 联合验证（2026-11-08 集成树 fresh）：9 类 94 项定向测试 0 失败/0 错误/0 跳过；Leanback Mobile arm64 Debug Java、Mobile UnitTestJavaCompile 编译通过；日志 `.codex/task-state/reports/TV-SMALL-FIXES-INTEGRATION-final.log`。整批仍不冒充 decoder 实播/设备/性能验收。
- 回滚：针对本任务按上述代码链逆序git revert；合并历史保留真实来源，不reset原工作区或移动tag。

## 本轮评审修复 — 指针解析容忍变形 / 媒体响应免 peek — 2026-11-08

- 缺陷（F1，播放回归）：`Strm.parseTarget`只用`new URI(line)`做严格语法门，而站点回显的`url|User-Agent=...`（本仓库一等输入，见`LiveParser`的`split("\\|", 2)`）与中文路径在`java.net.URI`里都是非法字符，抛`URISyntaxException`后整行被丢弃、回退成`.strm`自身URL，播放退化。
- 修法：拆成`isPointer(String)`+`normalizeTolerated(String)`两支。`isPointer`保留原严格判据（isAbsolute、scheme-specific-part非空、http/https必须有authority）作为第一判据；仅当严格判据失败时，`normalizeTolerated`截掉首个`|`之后的整段、把>U+007F的字符逐个替换成`'a'`，再用同一套`isPointer`复核；复核通过返回**原始整行**（保留`|headers`与中文），否则返回original。控制字符行仍在解析前直接拒绝，未放宽。
- 缺陷（F2，启动延迟）：`contentLength() == -1`时对真实媒体/流响应多做一次最多64KiB阻塞peek。修法：仅在响应长度未知（`contentLength() < 0`）时才套内容类型门`isMediaType`——`video/`、`audio/`前缀或含`mpegurl`、`dash+xml`（忽略大小写）直接返回原URL；已知长度的响应本来就受64KiB上限约束，照常peek解析（站点把`.strm`指针以`application/vnd.apple.mpegurl`下发时不会因此被放弃）；缺失Content-Type、`text/*`、`application/octet-stream`仍走原peek逻辑。206/重定向/64KiB上限语义未改。
- 仍然拒绝（既有用例全部保留）：`https://host/a b`、含制表符的`https://host/a<TAB>b`、`https://host/%GG`、`https://[bad]/a`、`http:///a`、`https:`、`<html>error</html>`、`{"error":"failed"}`、`/media/a`、`//host/a`与控制字符行；原因是容忍只处理`|`与非ASCII，不替换空格/制表符/非法百分号转义/坏authority。
- 新增测试（`StrmHttpTest`，沿用既有`assertTarget`/`assertFallback`/MockWebServer，无新依赖）：`headerSuffixedPointersKeepTheirWholeLine`、`nonAsciiPointersKeepTheirWholeLine`、`mediaResponsesAreNeverTreatedAsPointers`。既有16项断言未改动、未放宽。
- 测试结果（2026-11-09，Asia/Shanghai，父代理执行）：`gradlew.bat --console=plain :app:compileMobileArm64_v8aDebugJavaWithJavac :app:testLeanbackArm64_v8aDebugUnitTest --tests <6 类>` → `BUILD SUCCESSFUL in 7m29s`，mobile 与 leanback 主源集及 `compileLeanbackArm64_v8aDebugUnitTestJavaWithJavac` 全部通过；6 类共 80 tests / 0 failures / 0 errors / 0 skipped（`DanmakuApiOwnershipTest` 3、`StrmHttpTest` 19、`TvSeekOwnershipIntegrationTest` 5、`VideoDanmakuOwnershipTest` 22、`CustomKeyDownSeekOwnershipTest` 17、`ProxyResponseCompatibilityTest` 14）。仍未验：设备/真机（实机 seek、直播即时切线路、时移、慢响应弹幕覆盖、mobile 真实消费者）、全量 Gradle 矩阵/`connectedAndroidTest`/APK 打包安装、QuickJS/Python 真桥的 `destroy` 清理与 gzip 端到端播放。
