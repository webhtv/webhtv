# C-TV-PROXY-RESPONSE — 旧桥代理响应兼容设计

## Recovery anchor / 阶段状态

- 2026-11-08，China Standard Time / +08:00；阶段1仅研究与TDD设计，run 1/3。代码、build、git commit/tag/config、native均未执行；本文件不是实施完成声明。
- 原工作区 `F:/Workspace/fish2018/webhtv`，`Silent1566@47454687f504955775b398905ba09d73aeb9f20c`。保护 `app/.cxx/` 和未提交 `docs/upstream-player-dependency-merge-assessment-2026-11-08.md`；初始status仅这两项。阶段1唯一仓库输出为本文件。
- 父已告知用户批准小修，阶段2仍等父交付干净worktree和实施授权；唯一任务文档迁至该worktree `docs/C-TV-PROXY-RESPONSE-compatibility.md`，之后只追加此文档，不另建plan/fix报告。
- 下一动作：等待父交付干净response worktree并明确阶段2授权，然后启guard和第一个真实消费者红测。父已裁定本设计的三项线级安全过滤在批准范围内；不得在原脏tree实施。

## 1. 问题、边界与限制

完成句：保留本地旧协议，为QuickJS catvod/proxy2补齐有效响应头，明确null/空体行为，使损坏响应进入现有受控500错误路径，而不引入新版运行时或破坏旧站源。

决定性问题：本地server真正消费哪些status/MIME/header/body？上游新桥哪些部分可补本地缺口，哪些会破坏旧协议？

假设与反证：第4槽丢头是可局部修复缺口；若server不消费该槽、消费会破坏framing，或旧协议依赖新SDK，则需适配而非原样透传。实际源代码确认server消费第4槽，但原样透传有framing风险，见E3/E8。

批准范围：旧3/4/5项（第5项base64开关）、flagged `data:...;base64,`、Python bytes、现有MIME/status/fallback；仅QuickJS Spider/Res与有实际必要时chaquo Spider，最小无运行时规范helper只有确实形成行为测试seam时采用。

排除：stream-token、`$tvStream`、TypedArray/数字数组新协议、新版QuickJS SDK、WASM、runtime AAR、native、loader生命周期、server架构/重定向认证、泛化严格校验、全量测试矩阵。

- 简报运行次数：单位run，上限3，跨返工累计；当前1。
- 相同失败工具假设：单位attempt，上限2，累计；达到后换读取方式，不重试同一失败假设。
- 阶段1文件编辑：单位file，上限1，累计；仅本design。
- 只读命令：单位command，不限，累计；不得build或让命令修改tracked文件/config。
- 查询改写：单位query/问题，上限3，累计；响应兼容簇共享研究，不结转quota。
- 前置研究目标：单位minute，目标8（不是任务中止上限）；达到后停止可选资料，仅补改变决策的必要证据、写设计和验证文档。

canonical `.codex/skills/upstream-integration-governor/SKILL.md`（特别57–75）、`references/evidence-and-research.md`、`references/integration-workflow.md`已读。没有加载player/native gates：本阶段不改变播放器/native行为。研究/TDD skill已读；禁止自派子agent的任务边界优先于research skill的后台代理建议。

## 2. 冻结来源与exact patch

| 对象 | 精确身份 | URL / 路径 | 处置 |
| --- | --- | --- | --- |
| WebHTV | `47454687f504955775b398905ba09d73aeb9f20c` / Silent1566 | `F:/Workspace/fish2018/webhtv` | 本地兼容与rollback基线 |
| FongMi/TV候选 | `f1aa53e2526307fa67930dd1d0efb6fc3e4732a9`，2026-10-30 16:40:14 +08:00，FongMi，Align Python and QuickJS proxy response handling | https://github.com/FongMi/TV/commit/f1aa53e2526307fa67930dd1d0efb6fc3e4732a9 | adapted；不cherry-pick |
| FongMi/TV冻结最终树 | `4512b4c9f82534ff66acfde06ccdd68f35b074f0`，2026-11-06 09:22:13 +08:00 | https://github.com/FongMi/TV/tree/4512b4c9f82534ff66acfde06ccdd68f35b074f0 | 只查相关最终方法，不扩大提交集 |
| 已有裸缓存 | 上述两commit均cat-file确认commit | `.git/upstream-tv-assessment.git`，remote https://github.com/FongMi/TV.git | 后续读用 `git -c gc.auto=0 -c maintenance.auto=false --git-dir=... show ...`；不fetch/config/gc |
| Gson本地依赖 | `2.14.0`，tag object `93c8c0cc7fa991833b8c4da0cee487d52f85b092`，peeled commit `3ff35d6269894901ab8006258395aafc4b9765cd` | `gradle/libs.versions.toml:32,92`；https://github.com/google/gson/tree/3ff35d6269894901ab8006258395aafc4b9765cd | 保版本，不升级 |
| NanoHTTPD本地依赖 | `2.3.1`，tag object `0a01da41e3bf48145359a5b8b1a8452fc572a195`，peeled commit `16ed41b22c2739838ec7cb0e6ba26d52f4c8db3e` | `gradle/libs.versions.toml:46,121`；https://github.com/NanoHttpd/nanohttpd/tree/16ed41b22c2739838ec7cb0e6ba26d52f4c8db3e | 不升级，按真实发送器适配 |

exact `f1aa...`只有2文件、3插入/4删除：chaquo Spider proxy约151行删除`>4`拒绝，并改短响应异常文字；QuickJS Spider `proxyResponse`308–315删除`array.length>4`拒绝，`proxyBody`347–355去掉`stream.length()==1`限制。它**不恢复第5项base64语义**：新桥只是忽略多余槽，新桥body另有stream/byte-array约定。不能把标题当“新桥兼容老base64”的证明。

冻结4512最终QuickJS Spider关键位置：proxy入口约170–175；`proxyResponse`322–329、`proxyCode`331–336、`proxyHeaders`338–348、`headerValue`350–359、`proxyBody`361–369、`proxyBytes`371–381、`proxyByte`383–389。新桥要求整数100–599、MIME string、headers object且值string或string-array、文本UTF-8/bytes/stream；这些严格条件与本地不同，不能照抄。Res类在冻结新桥树已不存在，也不能据此删本地Res。

## 3. 本地数据流与现有合同（E1–E3，Grade A）

所有本地路径/行号绑定 `47454687f504955775b398905ba09d73aeb9f20c`，访问日期2026-11-08。

| 路径 / symbol | 实际行为 / 本任务影响 |
| --- | --- |
| `quickjs/src/main/java/com/fongmi/quickjs/crawler/Spider.java:118–120` | `from=catvod`走proxy2，其他走proxy1；不合并或删除两入口 |
| 同文件208–220 `proxy1` | JSArray stringify→JSONArray；`optInt(0)`、`optString(1)`；第4项经Json.toMap，第5项`optInt(4)==1`决定base64；返回4槽。3/4/5项当前均支持，不需要移植上游“放宽长度” |
| 同文件223–234 `proxy2` | 接收JSON string→Res；仅3槽code/contentType/stream；Res已有header却未传，这是明确缺口 |
| 同文件237–244 `getStream` | byte[]直接流；其余toString；Java null会NPE；JSONArray越界opt返回Java null，JSON null是JSONObject.NULL，其toString产生字面`null`，不是空体；flagged base64截前缀用split，空payload可能索引失败 |
| `quickjs/src/main/java/com/fongmi/quickjs/bean/Res.java:19–29` | `@SerializedName("code"/"buffer"/"content"/"headers")`，Integer/Integer/String/JsonElement。注释仅指定JSON字段名，不强制非null或类型安全 |
| 同文件32–62 | code null→200；buffer null→0；content null/empty→""；header经Json.toMap；Content-Type只匹配两种拼写，否则octet-stream；buffer==2按Util.decode，其余String.getBytes（默认charset） |
| `catvod/src/main/java/com/github/catvod/utils/Json.java:56–61,80–98` | safeString取primitive string并trim，失败→""；safeObject接受object或可解析的JSON string，失败→空object；toMap(null JsonElement)→空map。不得换成全局严格拒绝或新增数组header协议 |
| `catvod/src/main/java/com/github/catvod/utils/Util.java:47–53` | Android Base64.DEFAULT\|NO_WRAP解码；不能换严格Java decoder而丢失其现有容忍行为 |
| `chaquo/src/main/java/com/fongmi/chaquo/Spider.java:89–99,114–130` | localProxy list，第5项==1 base64；第4项map转换失败返回null；bytes直接toJava(byte[].class)；None body→null InputStream，server受控500；字符串默认charset；不改Python app/runner调用名 |
| `chaquo/src/main/python/app.py:94–96`、`runner.py:35–36` | Python结果原样返回，bytes没有JSON转字符串；此路径已支持bytes，不新增模拟替代协议 |
| `app/src/main/java/com/fongmi/android/tv/api/loader/BaseLoader.java:79–83`、JsLoader55–63/PyLoader60–63 | 汇到Spider.proxy；root API仍Object[]，不改返回类型 |
| `app/src/main/java/com/fongmi/android/tv/server/process/Proxy.java:35–83,109–117` | loader异常→Nano.error500；短/空/非Integer/非InputStream→Invalid proxy response500；第4槽若Map转换非nullkey/value成string，非map→null；known Status.lookup，unknown100–599保码，其他Integer→500；MIME Objects.toString；newChunkedResponse→addHeaders；仅保护X-WebHTV-Proxy-Range-Start，未过滤framing |
| 同文件67–78及`ProxyRangeResponsePolicy.java:10–27` | 206+合法单range额外发布本地range-start，不能让站源覆盖；必须继续保留 |
| `app/src/main/java/com/fongmi/android/tv/server/Nano.java:68–73` | 错误为500/text/plain固定长度响应；受控报错应复用它，不新增异常逃逸机制 |

## 4. 外部证据账本（实际读源，不以搜索摘要作结论）

共同访问日期：2026-11-08。Grade A=官方spec/API或exact源；Grade B=maintainer解释/成熟相关实现；未进行复现的推断明确标记。

| ID / 级别 | 精确source、位置/版本/URL | 支持论断、适用性与决策影响 |
| --- | --- | --- |
| E1 A | 本地源：第3节全部exact路径/行/HEAD | 旧协议和fallback是基线，不因新桥严格校验改变；无需Python bytes新实现 |
| E2 A/B | FongMi/TV `f1aa53e2526307fa67930dd1d0efb6fc3e4732a9` exact patch和FongMi提交说明；冻结`4512b4c9f82534ff66acfde06ccdd68f35b074f0`方法见第2节；上面commit/blob URL | maintainer调整新桥字段长度和stream识别；只能支持意图，不能证明老协议对等。新桥依赖新runtime，拒绝整桥 |
| E3 A | 本地Proxy49–83、109–117，Nano68–73 | 真实消费4槽、保扩展status、500fallback；设计测试必须产生实际NanoHTTPD.Response，不用源码字符串断言 |
| E4 A | RFC9110（June2022），§5.1、5.5、8.3、10.2.2、14.4、15.1；https://www.rfc-editor.org/rfc/rfc9110.html ; https://www.rfc-editor.org/rfc/rfc9110.txt （文本约2948/4741/§14.4） | field-name大小写不敏感；CR/LF/NUL字段值危险须拒绝或替空格；Content-Type定义表征，没有可用类型可octet-stream；Location和Content-Range是响应语义，不能因桥丢失。仅危险控制字符需新增防护，不能因此拒绝旧容忍header类型 |
| E5 A | Java SE21 ByteArrayInputStream constructor/read/read(byte[],...)/close；https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ByteArrayInputStream.html ; String.getBytes/charset constructor，https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html | 流用原byte[]、read返回0..255/EOF-1，byte signed不损坏octets；String默认charset不是二进制编码器，非UTF8 body必须沿bytes/base64路径。此小修保默认文本charset，不把所有内容“修成UTF8” |
| E6 A | Chaquopy官方PyObject class/toJava/asList/asMap/toString；https://chaquo.com/chaquopy/doc/current/java/com/chaquo/python/PyObject.html （current滚动文档，非不可变revision） | None=Java null；bytes/bytearray→byte[]有128..255到-128..-1转换；toString=Python str，不是二进制解码；证明现有bytes分支必须保留。仅文档证明，不冒充本机Android桥测试 |
| E7 A | Gson `3ff35d6269894901ab8006258395aafc4b9765cd`，`gson/src/main/java/com/google/gson/Gson.java` fromJson(String,Class)；`annotations/SerializedName.java` Javadoc；`internal/bind/TypeAdapters.java:260–274` INTEGER.read；https://github.com/google/gson/blob/3ff35d6269894901ab8006258395aafc4b9765cd/gson/src/main/java/com/google/gson/Gson.java ; 同根路径替换至后两文件 | fromJson Java null/empty→null；Integer JSON null→null，nextInt支持现有转换，NumberFormatException→JsonSyntaxException；SerializedName只覆盖命名策略。必须保护顶层Res null，但不能一概拒绝numeric strings、缺省code/buffer |
| E8 A/B | NanoHTTPD成熟实际consumer `16ed41b22c2739838ec7cb0e6ba26d52f4c8db3e` `core/src/main/java/fi/iki/elonen/NanoHTTPD.java:1530–1532,1556–1565,1587–1649`；https://github.com/NanoHttpd/nanohttpd/blob/16ed41b22c2739838ec7cb0e6ba26d52f4c8db3e/core/src/main/java/fi/iki/elonen/NanoHTTPD.java | addHeader存map不校验控制字符；send先打印MIME Content-Type，再全部map，再自身Transfer-Encoding:chunked；printHeader原样拼CRLF。补proxy2必须避免双Content-Type和CL+TE，测试需观察wire而不只map |
| E9 A | RFC9112（June2022）§6.2、6.3；https://www.rfc-editor.org/rfc/rfc9112.txt | 带TE不得发CL；中介选择转发须移除收到的CL。仅新proxy2映射应去Content-Length/Transfer-Encoding让Nano拥有framing；不在本任务改server全局旧行为 |
| E10 A | Android libcore `android-15.0.0_r1` `json/src/main/java/org/json/JSONObject.java:108–118`、`JSONArray.java:303–308,417–421,484–488`；https://android.googlesource.com/platform/libcore/+/android-15.0.0_r1/json/src/main/java/org/json/JSONObject.java ; 对应JSONArray.java | JSON null sentinel不是Java null，其toString是"null"；越界opt返回Java null；optInt/optString走转换和fallback。测试显式区分Java null、JSON null、字符串"null" |

证据类别覆盖：exact upstream/最终树、本地/官方规范、maintainer commit、成熟相关实际HTTP发送器均已读。检索exact hash及FongMi TV proxy/base64 issue未找到可用专属PR/issue/revert讨论（结果无关，未采用）；已用exact patch+maintainer提交说明，不能声称维护者确认本地适配。学术论文/性能benchmark对此小型协议映射没有算法/优化决策价值，记录不适用，不搜索扩大；第三方TVJar搜索仅作线索未读actual源，不作为证据。

网络限制：read_page云引擎403后本地fetch成功但不支持query focus，RFC/GitHub大页截断；改内存HTTP raw读取必要章节。Android开发站timeout、错误GitHub AOSP路径404后改官方googlesource读取；均未用失败结果当证据。无网络下载执行脚本。

## 5. 方案比较和决定

| 维度 | no change | 原样上游新runtime整桥 | narrow compatible（推荐） |
| --- | --- | --- | --- |
| 正确性/产品价值 | proxy2继续丢Location/range等；null缺陷留下 | 支持新stream协议，但不是本地批准目标 | 补已存在header；明确定义缺失/空体；受控500 |
| 兼容/API | 保旧3/4/5，但不满足批准缺口 | 缺省status/MIME、旧base64第5槽不对等，严格header/status会拒站源；API/runtime ownership改变 | Object[]、两入口、第5base64/data URI/Python bytes/defaults保持；不强制新规范 |
| 生命周期/ABI | 无变化 | Invocation/Streams/ProxyBody/新SDK复杂度与AAR/native链 | 已有ByteArrayInputStream，不新增callback owner或native |
| 性能/质量/包大小 | 无变化 | 流式有新工作负载，无法在本任务证明收益 | 仍内存body；一份小header map。无量化性能结论，不新增大拷贝/依赖/artifact |
| 安全/framing | 旧server风险留存，proxy2未透传头 | 上游server过滤CL/TE，但不能照搬替换本地range safeguards | 新proxy2只转发表征/语义头，去发送器拥有字段；危险控制字符报错；旧server不广改 |
| 维护/验证/rollback | 无改但欠修 | 跨模块和runtime大迁移，昂贵 | 小helper seam+真实Response/wire tests，单逻辑commit可revert |
| provenance/license | 当前 | 多runtime源/二进制尚需另研究 | 记录f1aa来源、手工适配；无新license/依赖输入 |

推荐：**补充并适配上游意图，拒绝上游整桥**。不cherry-pick，不删除Res，不把上游长度放宽错误解释成必须修改本地已支持的5项协议。

### 5.1 新proxy2输出头的最低安全适配（父已确认scope）

有效保真是HTTP语义保真，不是把原map重复打印到wire：

1. 仍从Res.getHeader的本地宽容Json.toMap合同取得map；Content-Type先用既有两个拼写的优先序，随后兼容其他大小写（HTTP字段名不区分大小写），保参数/类型内容，不嗅探body。
2. MIME槽承担有效Content-Type。新proxy2第4map不再含任何大小写Content-Type，防Nano双写；缺省保持application/octet-stream。
3. 仅新proxy2第4map去Content-Length/Transfer-Encoding，让真实chunked emitter拥有framing。Location、Content-Range、Accept-Ranges、ETag、Cache-Control等不重写；本地range-start保护仍由原Proxy完成。
4. 进入wire的key/value/MIME若含CR/LF/NUL，抛稳定IllegalArgumentException进入现有500，不输出原始恶意值到错误消息；仅检测这组危险字符，不新增全token严格schema、不新增header数组join。
5. 原proxy1/Python既有header宽容转换、status fallback不全面改写。旧路径潜在framing/控制字符风险不是悄悄扩成server安全重构；如父要求处理旧路径，先重新限定范围与测试。

此项是读取真实consumer后发现的必需安全适配。父于2026-11-08阶段1交付后明确裁定：用户批准的是补响应头且不破坏现有能力，故以上三项发送器字段过滤与CR/LF/NUL受控错误属于实施必要条件，不需另立需求。非对象头/非字符串值保持fallback；proxy1与Python行为不变；若还需过滤第4种字段，必须先在本doc写理由并回报父，不得静默扩大。

### 5.2 明确null、畸形值和旧容忍行为

| 输入 | 设计输出/保留合同 |
| --- | --- |
| legacy JSONArray长度<3或整体null/非法JSON | 稳定受控异常→现有500；不以缺失槽偶然NPE作协议 |
| QuickJS body Java null / JSONObject.NULL | 空ByteArrayInputStream；字符串"null"仍四个ASCII字节；显式空字符串/空base64也是空流 |
| proxy2顶层JSON null、Java null、空JSON文本 | 受控Invalid proxy response类异常→500；不擅自给null整体200 |
| proxy2 `{}`/缺省code/buffer/content | 保现有200/0/空流；code JSON null仍200 |
| proxy1非法/缺失status、越界Integer | 保optInt默认0，再由server变500；可转换numeric string不新增拒绝 |
| proxy2 code非法类型/非数文本 | 保Gson转换异常→500；可转换numeric string、未知合法Integer仍按原consumer处理 |
| 非对象headers、JSON null headers、字符串JSON object | 保safeObject空map或原object解析；异常header值保持safeString当前空串fallback，不整包拒绝 |
| Python body None | 保现有null流→server Invalid proxy response500；它本来已null-safe，不为“对齐”把既有错误改200 |
| Python headers不可asMap | 保getHeader catch→null；bytes分支保持优先于文本/base64，不调用toString |
| malformed base64 | 原decoder容忍边界不收紧；真实不可解码时受控IllegalArgumentException→500；flag!=1则按旧文本，不自动探测data URI |
| flagged data URI空payload/正常payload | 取首次`base64,`后的全部内容（substring/index，而非split丢尾空字段）；无prefix的raw base64照旧；不引完整data URI新schema |
| body对象等legacy可toString值 | 不一概新增strict拒绝；保旧转换结果。TypedArray/数字数组作为新二进制协议明确不新增 |

## 6. 实施形状及可执行TDD

### seam决定（父批准本设计即批准这些测试seam）

小helper**值得采用**的唯一理由：JS引擎调用后输出已是JSON，响应映射可与native运行时分离，且生产Spider两入口都能真正委托它，避免为了测试实例化QuickJS/native。它不是通用runtime/stream framework。

建议新增 `quickjs/src/main/java/com/fongmi/quickjs/utils/ProxyResponse.java`：仅JSON→旧4槽映射、body转换、新proxy2 emission headers；两窄入口（legacy JSON、catvod JSON），无Context/JSObject/线程/网络/native初始化。使用已有org.json/Gson/Util，不新依赖/decoder。保Res getters及SerializedName字段，必要时在Res收敛null/contentType语义。Spider负责调用/取JSON并委托；不保留第二套相同映射。

预计生产改动3文件：现有quickjs Spider.java、bean/Res.java、此最小helper。chaquo Spider默认**不修改**：bytes/None/header fallback已满足合同；若真桥fixture发现必要回归，再由父决定该必要修改。server Proxy.java不修改，用实际consumer测试。

测试放app leanback test source set（已依赖quickjs/chaquo、JUnit4.13.2/MockWebServer，Robolectric4.16，仅leanback有）。建议：
- `app/src/test/java/com/fongmi/quickjs/utils/ProxyResponseCompatibilityTest.java`：调用生产helper的真实接口，字面期望，不源码文本断言、不mock映射内部。
- `app/src/test/java/com/fongmi/android/tv/server/process/ProxyResponseConsumerTest.java`：把helper真实结果送给当前Proxy创建Response；当前createResponse是private，可用一次测试侧反射adapter调用未修改真实方法（不把反射作单元内部细节断言），检查实际status/MIME/headers/data。若要求完全公共seam，则测试真实doResponse需loader装配，成本更高，不能用空壳consumer代替。
- wire测试：构造NanoHTTPD测试子类serve返回上述实际Response，loopback随机端口，OkHttp client禁redirect读取；assert唯一Content-Type、无Content-Length与chunked冲突、Location/Content-Range和byte[]正确。测试生命周期finally stop/close；无设备/native rebuild。MockWebServer只有来源HTTP fixture需要时使用，不为测试名而加一层。

### 红→绿顺序（一个垂直切片一个循环）

1. 首个红测：catvod JSON含206、Content-Type、Content-Range→helper→真实Proxy Response/wire，旧proxy2缺第4头应失败；再只补4槽、安全头映射。记录确切红因，不把compile失败当行为红。
2. 第二切片：JSON null/空payload→空流，顶层null→受控500，string"null"不被清空；再补最小null和前缀提取。
3. 回归切片：legacy3/4/5、flag/base64/data URI、宽容status/header、Res defaults；若测试只证明helper未证明Spider委托，代码review必须确认两个生产入口确实只走helper，不能靠文本assert“调用了helper”。
4. bytes与wire切片：raw `[0,127,(byte)128,(byte)255]`和独立base64字面`AH+A/w==`均期待octets00 7f 80 ff；非UTF8 fixture例如`ff fe 00 80`须byte-for-byte，不经String解码。
5. malformed/framing切片：坏JSON/短数组/bad base64/CRLF/NUL受控500；header null/数值/数组和status宽容fallback仍按第5.2节。新增strict rejection需证据或不做。

### 必测矩阵

| 类别 | fixture / 真值 | 检查 |
| --- | --- | --- |
| null/空 | Java null、JSON null、整体null、"null"、""、空bytes、flagged空base64/data URI | body空与整体错误区别；无NPE；PythonNone仍500 |
| 旧tuple | `[200,"text/plain","ok"]`、第4object/JSON-string object、第5=1/0、多余非协议槽 | 3/4/5保留；第5仍解码，不新增max4拒绝 |
| binary | `00 7f 80 ff`；`AH+A/w==`；flagged `data:application/octet-stream;base64,AH+A/w==`；非UTF8 `ff fe 00 80` | read/readAllBytes/wire均相同octets；不丢128/255，不经toString |
| text/charset | 非ASCII文本、含charset的Content-Type、缺Content-Type | 默认文本编码保当前行为；bytes路线不受charset影响；缺省octet-stream |
| headers | Location `/next`、Content-Type大小写/charset、Content-Range `bytes 0-3/4`、ETag；CL/TE，站源伪range-start | Location/Content-Range保语义；新proxy2CT唯一；发送器拥有framing；本地range-start不可被覆盖 |
| status | 200/206/302/416/599/unknown合法码、0/600、numeric string、null、"abc" | 本地fallback而非上游strictschema；unknown合法码保留；错误500 |
| malformed | 整体null/数组不足/坏JSON/坏base64、headers primitive/null/object with非stringvalues、key/value/MIME CR/LF/NUL | 可容忍值仍fallback；危险值不出wire且受控500；异常不抛出server boundary |
| Python实际桥 | `localProxy`返回bytes `[0,127,128,255]`、None、3/4/5、flagged base64 | Android/Chaquopy真运行时才证明实际toJava路径；Java fixture与官方文档不能冒充它 |

### 最小执行命令（仅阶段2，阶段1禁止build）

在父给定干净worktree，进程级JAVA_HOME指向 `C:/Program Files/Microsoft/jdk-21.0.12.8-hotspot`；ANDROID_HOME已有。不能改全局PATH/config，不能使用PATH中的Java8。

```powershell
# 在干净response worktree，先guard start（scope包含获批生产/测试和唯一doc）
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'
.\gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.quickjs.utils.ProxyResponseCompatibilityTest --tests com.fongmi.android.tv.server.process.ProxyResponseConsumerTest --console=plain
```

每个实际red/green只跑相应测试过滤；最终一项combined定向测试跑一次，相关编辑或不确定结果才重复。保完整log再窄读，不重复命令只换filter。先确认Gradle所选任务图不会重建native；若触发native任务，停该route，利用已存在预构建artifact/Java定向测试，不降低真实consumer行为要求。没有纯源码文本验证替代方案。

## 7. 验收、剩余门槛与rollback

阶段1验收：exact源/最终方法/当前consumer、官方byte/charset/JSONnull/Gson注释、成熟发送器及maintainer patch、nochange/upstream/adapted、旧API/fallback、真实行为TDD、rollback全部已记录。不是实现通过。

阶段2必须满足：
1. 父给干净response worktree、确认scope及迁移唯一doc；guard启动后再代码编辑，不清原active guard。
2. 真实consumer/wire红→绿记录及旧协议矩阵通过，父独立review生产delegation和完整diff。
3. 新proxy2的Content-Type/Content-Length/Transfer-Encoding三项过滤及CR/LF/NUL受控错误已获父明确接受；实施不得新增第4种字段过滤而不先记doc并回报。
4. Python真桥运行是单列门槛：本阶段不build native、Robolectric不等于Chaquopy真runtime；本地Python路径完全不改时，保留官方+源码证据并明确未做真桥回归；若改chaquo转换，则真桥测试成为代码完成必要门槛，不能仅用Javabyte fixture代替。
5. 没有性能量化/设备播放结果。无新增owner、body全量额外复制、新dependency/ABI/native变化；如发生这些，停止并重开设计。
6. 原proxy1/Python已存在的全局header framing风险不在此次新proxy2补头范围；已向父路由scope，不默默当作“已修复”。

rollout：一个独立compatible小单元，记录site fixture与status/header/body（避免秘密内容），保现有SpiderDebug错误边界；不发布/推送、不改原dirty tree。风险按独立消费者测试与scope门槛处理。

rollback锚：`47454687f504955775b398905ba09d73aeb9f20c`。阶段2经guard原子commit/tag后，父在干净集成tree用 `git revert <本任务实际40字符commit>` 回滚此任务代码+测试+唯一doc兼容集；commit ID未产生前不得编造。不得在保护dirty tree `reset --hard`。回滚验证同一旧3/4/5与proxy消费smoke，确认依赖/ABI未变；无需native重建。

## 阶段1执行说明

初期窄读上游曾生成GitBash临时 `/tmp/up_spider.java`、`/tmp/up_chaquo.java`（非仓库生产路径），已向父报告；父裁定这属于可接受的只读研究副作用，无需清理，但不得写入任何仓库路径或生产。之后改内存读取，不把临时文件当任务doc、不加入commit。其余仓库写仅本文件。可选研究在必要规范/consumer证据充分后停止，没有论文/benchmark或宽泛issue扩展。

## 阶段2 Ruling / 2026-11-08 15:10 +08:00

父已授权干净 `upstream/proxy-response-20261108` worktree实施，root只读；scope实际5文件（Spider、必要helper、测试至多2、唯一doc），Res与chaquo不修改。

- 原design按父要求先复制到docs，guard将该新doc视为dirty而拒绝首次start；已用明确任务自有 `--adopt-dirty docs/C-TV-PROXY-RESPONSE-compatibility.md` 重启start，不采用其他dirty路径，不绕过guard。
- 父明确允许唯一protected脚本调用seam：纯提取proxy2既有脚本获取JSON，线程提交和异常顺序不变；真实生产proxy2委托，无test-only flag/branch，无新增public API；先保3槽做真实proxy2→Proxy.createResponse→Nano.send行为Red，再GREEN。
- 该seam提取是父批准的TDD夹具前置，不是预先修正生产映射；若线程/语义不能保持，则停止该route并如实声明proxy2整体未E2E覆盖。
- 仅新proxy2过滤Content-Type/Content-Length/Transfer-Encoding；CR/LF/NUL受控报错；其余头保留。proxy1/Python源与语义保持不变，因此legacy Java/JSON null的原行为不在本次偷偷改；proxy2整体null须受控500、空content仍空流。三项外新增过滤须先doc+回报。

### TDD前置记录

- 首test先写`ProxyResponseConsumerTest.catvodPartialResponseKeepsContentRangeOnTheWire`，fixture只替代protected脚本调用，执行真实`Spider.proxy(from=catvod)`和现有private `Proxy.createResponse`，再反射调用实际NanoHTTPD.Response.send至ByteArrayOutputStream，独立检查HTTP状态和Content-Range。
- 纯提取`callProxy2(Map)`严格保先get url/header→submit JSArray/get→submit parse/get→call proxy的线程/异常顺序；原3槽Res映射暂未变。测试没有mock response、helper或HTTP编码，seam不是public API。
- task graph dry-run成功：Java/app unit tasks，无externalNative/CMake编译任务；预置native库不会被重建。初始Robolectric sdk28对应已暖cache Android9。
- Red命令：`:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.server.process.ProxyResponseConsumerTest --console=plain`，日志`.codex/task-state/reports/C-TV-PROXY-RESPONSE-red-headers.log`；结果等待实际测试，不以编译或夹具错误计Red。

### 切片1 Red证据

2026-11-08 15:15，首Red确凿：1 test、1 AssertionError；真实wire含`HTTP/1.1 206 Partial Content`、MIME video/mp2t和本地range-start，但`Content-Range`期望`bytes 0-3/4`、实际null。测试XML和red-headers.log均留存。首app暖编译4m34s，无native任务；非编译/夹具错误。
最小GREEN暂只新增4槽header映射；下一切片必须修三项线级重复/framing及危险字符后才可发布。

### 阶段2实现与验证（2026-11-08 15:35）

- 首Red：真实proxy2→Proxy.createResponse→Nano.send wire缺Content-Range（日志`C-TV-PROXY-RESPONSE-red-headers.log`）。
- 第二Red：直接透传响应头造成wire双Content-Type及站源Content-Length/Transfer-Encoding与Nano自身chunked冲突（日志`C-TV-PROXY-RESPONSE-red-framing.log`）；随后只过滤父批准的Content-Type/Content-Length/Transfer-Encoding，保Location/Content-Range/ETag等。
- 第三Red：`ProxyResponseCompatibilityTest`首轮7项全部失败；明确区分夹具环境错误（plain JUnit下Android TextUtils未mock）与真实断言错误（顶层null抛NPE而非受控异常、危险header未拒绝）。该首次plain JUnit结果不作为危险字符Red证明；修正Robolectric夹具后已有检查GREEN。后续末尾控制字符Red在正常Robolectric下真实失败（expected IllegalArgumentException, nothing thrown），补raw检查再GREEN。
- 最终定向命令：`:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.server.process.ProxyResponseConsumerTest --tests com.fongmi.quickjs.utils.ProxyResponseCompatibilityTest --console=plain`，4+7项、0 failures/0 errors；日志`.codex/task-state/reports/C-TV-PROXY-RESPONSE-final-check.log`。该log同时含`:quickjs:compileDebugJavaWithJavac`与app/Java compile任务。未跑native、全矩阵、设备。
- 生产改动仅`Spider.proxy2`委托新helper+纯提取`callProxy2`；`ProxyResponse`复用已有Res getters，JSON null content已在Res内为空字符串，bytes/文本走既有Util/Gson路径。proxy1与chaquo/Python源码零改动，`git status chaquo quickjs/bean app/src/main`为空。未新增依赖/SDK/JNI。
- 现有上限内文件：Spider、ProxyResponse helper、2测试、唯一doc。`.codex/task-state/reports/C-TV-PROXY-RESPONSE-*.log`为忽略证据，不入commit。

### 自查边界及测试层级说明

- `red-header-edges.log`两项真实Red：尾CR被原Json.safeString.trim吞掉（未抛IllegalArgumentException），混合大小写Content-Type过滤后MIME退到octet-stream。helper改为Gson只解析JSON一次、从原JsonElement头先验CR/LF/NUL再沿既有Res/Json转换；MIME保既有两个key优先级，再大小写不敏感匹配。没有第4种字段过滤。
- 真实Nano.send输出经ISO-8859-1无损表示作为线级fixture，而非mock HTTP编码；无需启动socket、MockWebServer或native引擎。测试seam仅替代脚本取JSON，native脚本本身未执行。
- legacy3/4/5真实server tuple消费、invalid integer fallback、旧private getStream的bytes0/127/128/255、base64/data URI实测；proxy1完整JS/native调用和Python真桥**未实测**，其协议解析代码/第五base64条件完全未改（A级exact源码），不得将这些局部测试宣称完整native E2E。
- malformed headers的数字/布尔保原primitive→string，array/object/null值保原空串；不新增strict schema。默认code/MIME/content和literal"null"/JSON null区别均有fixture。
- no build-script/lock/artifact edits；Java编译只走现有定向Gradle任务。若父要求Python或原proxy1真桥E2E，需要单列设备/runtime环境，不用当前JVM测试冒充。

## 最终代码交付记录 / 2026-11-08 15:44 +08:00

- 父closure裁定：一次source+tests+唯一doc原子commit/tag。此tracked doc记录测试/基线/源hash/回滚及查取规则；实际最终40位code commit/recoverytag写忽略report和父回报，父最终集成归档时追加到此同名doc/索引，必要只一次doc-only closure，不递归自引用、不留dirty doc。
- 最终命令：`gradlew.bat :quickjs:compileDebugJavaWithJavac :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.server.process.ProxyResponseConsumerTest --tests com.fongmi.quickjs.utils.ProxyResponseCompatibilityTest --console=plain`；exit0、6+12=18 tests、0 failures/0 errors；quickjs Javac实际执行，app定向Java链亦成功。日志`.codex/task-state/reports/C-TV-PROXY-RESPONSE-final-verified.log`，1m50s。相关source revision之后不再重复build/test。
- 最终源码映射仍`FongMi/TV@f1aa53e2526307fa67930dd1d0efb6fc3e4732a9`手工narrow adapted；最终树参考`4512b4c9f82534ff66acfde06ccdd68f35b074f0`，local rollback `47454687f504955775b398905ba09d73aeb9f20c`。不移植新runtime；proxy1/getStream/Res/chaquo/Python/Nano源码无变化。
- 实测：真正proxy2映射→Proxy.createResponse→Nano.send的206 Content-Range、302 Location、唯一Content-Type、Nano-owned chunked、599扩展状态、binary octets；旧3/4/5 server tuple及旧body converter，Res默认值/JSONnull/literalnull、nonobject/nonstring header fallback、raw CR/LF/NUL和mixedcase Content-Type。
- 未实测：Android/Chaquopy Python真桥、QuickJS实际native脚本调用与完整proxy1 JSArray stringify入口、设备播放；不做性能/包大小量化声明。脚本调用seam仅纯提取不改变提交顺序，已获父授权。
- 查取：此doc所属source commit在交付report中；可用`git log --format=%H -- docs/C-TV-PROXY-RESPONSE-compatibility.md`定位，`git tag --points-at <codecommit>`取annotated recovery tag。回滚采用`git revert <report记录的codecommit>`，不reset root dirty tree。

## run2/3 独立review返工：限定Leanback测试sourceSet

2026-11-08 15:48 +08:00，父review发现本任务新增Robolectric测试误放common `app/src/test/java`。`app/build.gradle`仅`testLeanbackImplementation`提供Robolectric4.16；baseline `47454687f504955775b398905ba09d73aeb9f20c` common test无Robolectric引用（git grep无匹配），所以本任务增加Mobile unit Java编译障碍，不能凭Leanback PASS交付。

- 新guard：`C-TV-PROXY-RESPONSE-TEST-SOURCESET`，不改旧finished guard。基线是代码commit`e81ae22a1cdc4d026d6d5ecbbb11b96ddc3e9103`。
- 两旧文件的最近提交均为本任务e81；迁移前绝对源/目标已确认，目标不存在。git mv到`app/src/testLeanback/java/com/fongmi/android/tv/server/process/ProxyResponseConsumerTest.java`及`app/src/testLeanback/java/com/fongmi/quickjs/utils/ProxyResponseCompatibilityTest.java`；SHA256迁移前后一致，类/包/内容零改动。
- scope仅两旧test、两新test与唯一doc；累计受控路径7。production/helper/Res/chaquo/build.gradle/locks不修改；旧code commit/tag不rewrite。
- 这次仅测试归属纠错，无业务行为改变，不重复业务Red；验收新sourceSet原18项Leanback GREEN及Mobile UnitTestJavaCompile，不修无关既有失败。

### run2验证与提交规则

- 实际验证命令：`gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.server.process.ProxyResponseConsumerTest --tests com.fongmi.quickjs.utils.ProxyResponseCompatibilityTest :app:compileMobileArm64_v8aDebugUnitTestJavaWithJavac --console=plain`。
- exit0，Leanback 6+12=18 tests、0 failures/0 errors；`compileMobileArm64_v8aDebugUnitTestJavaWithJavac`实际执行成功，Mobile不再编译本任务Robolectric fixtures。整项2m27s，未构建native；日志`.codex/task-state/reports/C-TV-PROXY-RESPONSE-sourceset-verified.log`。
- 两测试新sourceSet内容100% unchanged；Java包名/类名不变。生产代码与`e81ae22a1cdc4d026d6d5ecbbb11b96ddc3e9103`完全一致，review回归以测试迁移消除而不是添加Mobile依赖。
- 第二原子test/doc commit/tag记录在忽略report及父回报；不rewrite e81或旧tag。父集成cherry-pick原code commit后再pick本sourceSet commit，回滚反序revert两commit；父归档再将确定ID追加本唯一doc，不自引用、不留dirty。

## 最终集成归档 — 2026-11-08

- 用户已批准本任务窄适配，原始代码提交及恢复点在合并历史保留，不重写。
- 代码/修复提交：`e81ae22a1cdc4d026d6d5ecbbb11b96ddc3e9103` → `93d2217d00156a672f47d1405bb97dac5471ba16`
- 本地恢复 tag：`recovery/C-TV-PROXY-RESPONSE/20261108074404-e81ae22a1cdc`
- 本地恢复 tag：`recovery/C-TV-PROXY-RESPONSE-TEST-SOURCESET/20261108075221-93d2217d0015`
- 单元证据：18项定向测试通过；测试source set修正后Mobile单测编译通过；QuickJS/Python完整native链未测
- 联合验证（2026-11-08 集成树 fresh）：9 类 94 项定向测试 0 失败/0 错误/0 跳过；Leanback Mobile arm64 Debug Java、Mobile UnitTestJavaCompile 编译通过；日志 `.codex/task-state/reports/TV-SMALL-FIXES-INTEGRATION-final.log`。整批仍不冒充 decoder 实播/设备/性能验收。
- 回滚：针对本任务按上述代码链逆序git revert；合并历史保留真实来源，不reset原工作区或移动tag。

## 本轮评审修复 — 解码后响应头与crawler收尾 — 2026-11-08

- 缺陷（F3，回归风险）：`ProxyResponse.fromJson`此前只过滤Content-Type/Content-Length/Transfer-Encoding，而`Res.getStream()`返回的是**已解码String**；站点回显`Content-Encoding: gzip`时本地`/proxy`会声明gzip却发明文，客户端gunzip失败。修法：过滤集合补`Content-Encoding`（`equalsIgnoreCase`，大小写均丢弃），Content-Type继续单独走`rs[1]`，其它头照旧透传；返回值仍是4元素`Object[]`，`Proxy.createResponse`契约不变。
- 为什么只过滤这4个头：本地`/proxy`由自己决定响应体形态，凡描述“上游线上字节”的头都必须丢弃——Content-Type单独作为MIME返回，Content-Length/Transfer-Encoding由本地按解码后字节重新计算，Content-Encoding因解码已经发生必然失真；其余头（ETag、Cache-Control、Content-Disposition、Set-Cookie等）与字节编码无关，透传才不会破坏调用方既有行为。
- 缺陷（F4）：顶层非对象JSON（`[]`、`123`、`"x"`）此前漏出Gson的`JsonSyntaxException`。修法：解析后显式判断`element == null || !element.isJsonObject()`并抛受控`IllegalArgumentException`（信息风格与既有一致），后续统一用局部`root`，不再对非对象调用`getAsJsonObject()`。
- 缺陷（F5，日志噪声）：`Spider.destroy()`在`init`早期失败（`jsObject == null`）时仍`call("destroy")`，`Async.run(null, ...)`在executor里NPE → `.get()`抛`ExecutionException`并在真正的init错误之前printStackTrace。修法：`jsObject == null`时跳过`call("destroy")`，直接走既有`releaseJS()`+`executor.shutdownNow()`收尾路径；异常兜底不变，`proxy1`/`proxy2`结构与loaders未触碰。
- 新增测试（`ProxyResponseCompatibilityTest`）：`contentEncodingIsDroppedBecauseTheBodyIsAlreadyDecoded`（`Content-Encoding`/`content-encoding`均被丢弃，Content-Type仍走`rs[1]`，ETag保留）、`nonObjectTopLevelJsonIsARejectedResponse`（`[]`/`123`/`"x"`抛`IllegalArgumentException`）。既有断言全部保留。
- 测试结果（2026-11-09，Asia/Shanghai，父代理执行）：`gradlew.bat --console=plain :app:compileMobileArm64_v8aDebugJavaWithJavac :app:testLeanbackArm64_v8aDebugUnitTest --tests <6 类>` → `BUILD SUCCESSFUL in 7m29s`，mobile 与 leanback 主源集及 `compileLeanbackArm64_v8aDebugUnitTestJavaWithJavac` 全部通过；6 类共 80 tests / 0 failures / 0 errors / 0 skipped（`DanmakuApiOwnershipTest` 3、`StrmHttpTest` 19、`TvSeekOwnershipIntegrationTest` 5、`VideoDanmakuOwnershipTest` 22、`CustomKeyDownSeekOwnershipTest` 17、`ProxyResponseCompatibilityTest` 14）。仍未验：设备/真机（实机 seek、直播即时切线路、时移、慢响应弹幕覆盖、mobile 真实消费者）、全量 Gradle 矩阵/`connectedAndroidTest`/APK 打包安装、QuickJS/Python 真桥的 `destroy` 清理与 gzip 端到端播放。
