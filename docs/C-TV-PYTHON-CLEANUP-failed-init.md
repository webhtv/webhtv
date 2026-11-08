# C-TV-PYTHON-CLEANUP — 失败初始化回收与不缓存失败

## 1. 阶段1决策与权限档案 / scope

- 结论：**实施窄适配（adapted），不 cherry-pick / 不整迁移上游 runtime**。已批准业务边界是已创建实例 init 失败回收、不永久缓存失败；此段记录阶段1研究与 TDD 设计时的权限；阶段2实施状态以本文末最新Recovery anchor为准。
- 当前唯一写路径：`.git/tv-implementation/design/C-TV-PYTHON-CLEANUP-failed-init.md`。父协调者提供干净实施树后，将本文件复制为 `docs/C-TV-PYTHON-CLEANUP-failed-init.md`，该文件随后独占此任务的设计、实现、验证与回滚记录；不另建 plan/fix/report 文档。
- 只读基线：`F:\Workspace\fish2018\webhtv`，分支 `Silent1566`，HEAD `47454687f504955775b398905ba09d73aeb9f20c`。保护初始未提交 `app/.cxx/`、`docs/upstream-player-dependency-merge-assessment-2026-11-08.md`，及所有生产路径。
- 开始：2026-11-08 14:32:44 UTC+08:00；研究目标8分钟，预计文档/验收3分钟，预计14:43交付。核心研究14:38收敛，不扩展可选资料。研究简报累计 run 1/3；同失败假设最多2 attempt累计，查询改写每问题最多3次累计；同一失败初始化簇无结转 quota。编辑文件最多1个累计、仅本设计文；只读 commands 不限累计。阶段1不构建、不运行行为测试、不改生产/依赖/gitconfig、不提交或打tag，不运行会写额外元数据的 task guard。
- Canonical：已读 `.codex/skills/upstream-integration-governor/SKILL.md`、`references/integration-workflow.md`、`references/evidence-and-research.md`。未读 player/native gates：本阶段不推荐播放器/native改动，不构建native，问题是 App Java 初始化失败合同。

## 阶段2批准与测试裁定（2026-11-08）

- 用户已批准实施，父已审阅本设计。隔离实施树 `F:\Workspace\fish2018\webhtv\.codex\task-state\worktrees\tv-small-fixes-cleanup`，branch `upstream/python-cleanup-20261108`，实施前完整HEAD `47454687f504955775b398905ba09d73aeb9f20c`，初始clean。原root只读，初始脏路径不触碰。
- 14:48 UTC+08开始；预计实现10分钟、暖缓存定向测试10分钟、自审闭环3分钟，目标15:11；不运行全测试矩阵/native重建。父已有暖缓存基线DanmakuUrlPolicyTest 7项PASS（cold 10m26），本任务不重复基线。
- 生产编辑前已启动 `task_guard.sh start --id C-TV-PYTHON-CLEANUP --mode quick-fix`，scope为两loader、唯一helper、两测试与本doc；guard报告保护0初始脏路径。
- 父裁定覆盖第6/7节的测试限制：新helper缺失造成compile error **不能当RED**。先测试真实现有PyLoader/JsLoader.getSpider的init未destroy、同key仍缓存SpiderNull的断言失败。Win不能执行native factory，允许test-only Robolectric shadow唯一外部chaquo/quickjs Loader constructor/factory，返回真实Spider子类FakeSpider；不shadow被测loader/缓存事务/SpiderInit，不加生产构造hook、Unsafe或复制旧逻辑。FakeSpider真实记录init/destroy与资源状态。
- 第一切片是同一失败初始化簇的Python/JS成对回归（回收与后续调用恢复）；随后按失败证据作最小共享事务修复，纯helper补异常/并发等边界。所有RED/GREEN必须标明行为断言还是环境/编译问题。
- 设备门槛修订：只读确认emulator-5554/5556均x86_64，5556有用户 `com.fongmi.android.tv` 包，5554没有该包。本项目现有flavor仅ARM，不擅自改依赖/ABI或装包。没有安全隔离native测试包，资源实际归零记未实测，**不阻塞Java调用/缓存合同交付**；真实回收挂死或破坏其他站源则必须回报，不静默提交。
- 跨任务索引前置已由父在集成树正式提交：`docs/upstream-player-dependency-merge-assessment-2026-11-08.md`，commit `60f2e86e761b8411e56a86793ee55a2f0b9b67d7`，tag `recovery/TV-SMALL-INDEX-20261108/20261108065008-60f2e86e761b`，链接本唯一taskdoc并记录用户批准。父将本代码commit集成入该分支；叶子树不复制scope外索引、不改guard/HEAD。首次真实RED执行前没有任何生产改动。

## 2. 冻结身份与上游处置

上游裸库 `.git/upstream-tv-assessment.git`，remote `https://github.com/FongMi/TV.git`，使用 `git -c gc.auto=0 -c maintenance.auto=false --git-dir=... show/log`，未 fetch / 改配置 / 阅读全部历史。

| 身份（完整40位） | 实际内容与处置 |
|---|---|
| 本地 `47454687f504955775b398905ba09d73aeb9f20c` | 本设计和回滚前置行为的只读依据；不是未来实施树的可直接 reset 目标。 |
| 上游 `123cc086159b0f13bf54bdf64bacbc259ca47c88` | 真正 patch，标题 Fix Python loading errors and resource cleanup。PyLoader 在原先直接 destroy 的 catch 上增加保留原异常的 cleanup；普通异常转换 SpiderLoadException、Error 重抛；chaquo Spider 聚合资源关闭异常；`_tv_loader.py` 把 SourceNotFoundError 转 ModuleNotFoundError。**局部采用失败清理/异常优先级意图，拒绝runtime及对外异常整迁移**。 |
| 后续 `8b499e0972b93f6173eac0e0414485c6b894294a` | 维护者正文：same-key共享初始化、元数据锁外init、退休后拒绝迟到结果、先换owner后异步清理、保留load/cleanup失败。正文报告22个回归，但本阶段未重跑。**仅作后续ownership阶段证据，不移植**。 |
| 后续 `c1c470b84e8111b0660316e842a9e4cb18fd83af` | 维护者正文明确 init失败不调用crawler生命周期回调、不保留失败实例；native资源清理由语言runtime内部承担；配置变化close host资源、app退出destroy retired spiders，并升级runtime兼容版本。正文报告129 JVM回归等且明确无设备验证。**新生命周期不适用于本地旧bridge，暂不采用**。 |
| 冻结目标 `4512b4c9f82534ff66acfde06ccdd68f35b074f0` | 最终 PyLoader/JsLoader 均继承 SpiderLoader，用 FutureTask共享初始化，close/destroy分离。检查相关树而非认为 patch 在最终树仍原样存在。**不迁移**。 |

上游链接均为 `https://github.com/FongMi/TV/commit/<完整40位>`；冻结源码为 `https://github.com/FongMi/TV/blob/4512b4c9f82534ff66acfde06ccdd68f35b074f0/<下列具体路径>`。上游版本仅用作出处，不意味着其新模块API在本地可用。

## 3. 本地事实：创建 → 初始化 → 消费 → 销毁

以下均在本地完整基线 `47454687f504955775b398905ba09d73aeb9f20c`，访问日期2026-11-08，证据A（直接源码）。路径以仓库根为基准，不将用户简写 `app/api/...` 当作实际路径。

| 文件/行 | 观察与设计影响 |
|---|---|
| `app/src/main/java/com/fongmi/android/tv/api/loader/PyLoader.java:15-22,38-49` | 真正 ConcurrentHashMap；mapping中 factory返回spider后设siteKey、init；catch Throwable打印并返回非null SpiderNull，导致该失败值被缓存。spider变量在try内部，catch拿不到已经创建的实例，未destroy。 |
| 同文件 `:52-57` | normalizeExt：空值只用于比较；trim后为 `[]` 时转换 `{}`，其他原ext原样返回（含null/空白）。不得改成统一trim、默认 `{}` 或新JSON处理。 |
| 同文件 `:24-28,34-35,60-64` | clear仅destroy缓存并clear；activeKeys来自缓存；recent proxy只取缓存。失败不缓存后activeKeys不再含失败key，recent proxy返回null，这是取消失败缓存的直接结果；成功key/proxy合同不变。 |
| `app/src/main/java/com/fongmi/android/tv/api/loader/JsLoader.java:15-22,39-53` | 同一缺陷。factory带 `BaseLoader.get().dex(jar)`；成功保持siteKey与原ext；失败保留 SpiderDebug两条日志及 printStackTrace，不替换成新Notify异常。 |
| 同文件 `:24-29` | clear调用全局 `Module.get().clear()`。失败回收**不调用clear**，也不新增Module.clear；现有clear/owner竞态属于未批准 C-TV-LOADER-OWNERSHIP。 |
| `app/src/main/java/com/fongmi/android/tv/api/loader/BaseLoader.java:50-62,65-83,97-102` | 异步clear，不换owner；根据api分发Py/JS；siteKey proxy直接 `getSpider(key).proxy`。无新的null检查合同，不能暴露内部null。 |
| `app/src/main/java/com/fongmi/android/tv/bean/Site.java:450-452`；`bean/Live.java:374-380` | Site/Live通过BaseLoader取spider，Live设置recent。 |
| `app/src/main/java/com/fongmi/android/tv/api/SiteApi.java:58-71,97,128,157,202,225` | 首页/分类/详情/播放/搜索/action直接调用spider，无null保护；返回非null SpiderNull继续给默认空结果。 |
| `app/src/main/java/com/fongmi/android/tv/api/parser/LiveParser.java:59-68`；`ui/custom/CustomWebView.java:220-228` | liveContent直接调用；嗅探manualVideoCheck/isVideoFormat及既有Exception fallback。不得要求调用方适配null或新异常。 |
| `app/src/main/java/com/fongmi/android/tv/cache/CacheCleanupManager.java:254-260` | activePluginKeys参与保留正在运行的脚本；失败实例不再算active。不得为了维持失败key而偷偷保留SpiderNull。factory失败造成的磁盘/模块副作用不由本任务清理。 |
| `catvod/src/main/java/com/github/catvod/crawler/Spider.java:26-31,33-82`；`SpiderNull.java:1-4` | init可抛Exception；destroy无checked throws且默认no-op。Spider不是AutoCloseable；SpiderNull是默认空行为的真实Spider子类。不能为套try-with-resources改公共API。 |
| `chaquo/src/main/java/com/fongmi/chaquo/Loader.java:21-35,38-50` | Python只启动一次、安装webhtv_logging、加载app模块；factory可能在下载/import/构造Python Spider时失败，Java Spider仅最后返回。factory未返回对象时，本loader无可destroy实例，不能猜测清整个runtime或模块缓存。 |
| `chaquo/src/main/java/com/fongmi/chaquo/Spider.java:23-35,107-112`；`chaquo/src/main/python/app.py:7-12,105-106` | Python init调用依赖下载、siteKey、app.init；destroy只转发该对象的ru.destroy，内部吞Exception，不做PyObject.close、sys.modules卸载或Python重启。本任务只保证**一次调用回收入口/尽力回收**，不能声称bridge吞掉的异常可变成suppressed，也不能保证第三方destroy一定清净资源。 |
| `quickjs/src/main/java/com/fongmi/quickjs/crawler/Loader.java:9-15`；`Spider.java:36-62,128-149,152-175` | JS构造有独立single-thread executor；init先创建ctx/函数/对象再调脚本。destroy先尝试脚本destroy（可因部分init失败而再报错），再releaseJS，finally shutdownNow；releaseJS在本实例executor释放global/jsObject/ctx。无需追到QuickJS JNI：已有入口足以决策，只读bridge到此停止。 |
| `catvod/src/main/java/com/github/catvod/crawler/SpiderDebug.java:18-32,39-44,54-59`；`chaquo/src/main/python/webhtv_logging.py:24-35,56-61,109-111` | console仅捕获，runtime仍拥有stdout/Logcat；Python流转发原stream并采集，stderr不是自动ERROR。不得新增第二套流hook或日志安装。 |
| `chaquo/src/test/python/test_webhtv_logging.py:133-158` | 已有stderr级别、install一次及原流只输出一次测试；后续复用，不在本阶段执行。 |
| `app/build.gradle:87-89,137-139,162-164,204-208` | Java21、现有JUnit与testLeanback Robolectric 4.16可用，app依赖catvod/chaquo/quickjs；不需改依赖。已有 `app/src/testLeanback/java/com/fongmi/android/tv/ui/custom/FlagSelectionListenerTest.java:43-45` 展示Robolectric接缝。 |

## 4. 一手研究记录（日期均2026-11-08）

证据A=规格/官方文档/直接源码及测试；B=维护者解释/成熟项目。官方网页是访问时的版本化Java21或Chaquopy current，不伪造其Git revision。网络read_page聚焦退化为本地全文并有截断，因此Map/JLS关键段已另用只读HTTP精确区块提取。Firecrawl无key 403后本地直取200成功；未修改代理或凭据。

| 来源与精确位置 | grade / revision | 支持的论断与适用性 / 决策影响 |
|---|---|---|
| https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html#computeIfAbsent(K,java.util.function.Function) ，方法detail | A / Java SE21 | 返回null不记录mapping；unchecked异常重抛不记录；Map默认实现**没有atomicity保证**。不能仅凭Map接口声称并发安全。 |
| https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#computeIfAbsent(K,java.util.function.Function) ，方法detail | A / Java SE21 | 整次调用atomic；key不存在时每次调用执行一次factory；null不建立mapping；RuntimeException/Error时不建立mapping；计算不得修改此map，更新可能阻塞。保留现有CHM计算，不在mapping内remove失败值。此保证不是失败后终身只初始化一次。 |
| https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3.1 ，Basic try-with-resources翻译及语义 | A / JLS21 §14.20.3.1 | 主操作异常优先，close异常suppressed；成功创建才有资源可关。借鉴异常优先级，不把Spider强行改成AutoCloseable；成功实例要缓存而不是finally无条件销毁。 |
| https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Throwable.html#addSuppressed(java.lang.Throwable) | A / Java SE21 | 自抑制抛IllegalArgumentException、null抛NPE；suppression可被构造器禁用。必须身份判断 cleanup != primary；不把cleanup当cause替代init失败。禁用suppression时不保证getSuppressed可见，但仍不能掩盖原异常。 |
| https://chaquo.com/chaquopy/doc/current/java/com/chaquo/python/Python.html ，start/getInstance | A / current官方API | start只能一次、且在getInstance前；getInstance同一对象。失败恢复不能重启Python；本地已有单次启动边界保留。 |
| https://chaquo.com/chaquopy/doc/current/java/com/chaquo/python/PyObject.html#close() | A / current官方API | close释放Java侧Python引用；同一Python对象会共享同一个PyObject引用并被一同失效。不能把共享app.close或关闭其他站源Python引用当局部回收。文档未承诺关闭一个站源等于卸载sys.modules。 |
| https://github.com/FongMi/TV/commit/123cc086159b0f13bf54bdf64bacbc259ca47c88 及 `app/src/main/java/com/fongmi/android/tv/api/loader/PyLoader.java:54-61,68-91` 的createSpider/destroySpider，`chaquo/src/main/java/com/fongmi/chaquo/Spider.java:55-97` 的init/releaseSpider/closeResources | A源码 / B维护者title；精确patch hash见URL | cleanup附到原失败，逐资源尝试。上游已有runtime内部失败释放，绝非本地旧bridge的同构补丁。title只解释意图，决策以diff为准。 |
| https://github.com/FongMi/TV/commit/8b499e0972b93f6173eac0e0414485c6b894294a 与 https://github.com/FongMi/TV/commit/c1c470b84e8111b0660316e842a9e4cb18fd83af ，完整commit正文 | B维护者解释 / 精确hash | 新生命周期将ownership/cancellation/exit及runtime兼容配套修改。以后要处理clear竞态须另阶段，不能顺便复制进入此补丁。未泛搜PR讨论：正文已有可核验的维护者解释与验证声明。 |
| `FongMi/TV@4512b4c9f82534ff66acfde06ccdd68f35b074f0`：`app/src/main/java/com/fongmi/android/tv/api/loader/SpiderLoader.java:74-99,111-138` 的load/createSpider/cacheSpider/retireSpider；PyLoader/JsLoader getSpider | A / 冻结完整hash | 直接检查最终树：FutureTask共享、close/destroy分离，init异常未缓存；与本地的public fallback及旧destroy合同不同。拒绝整迁移。 |
| https://github.com/google/guava/blob/f06690fa3e874f65515e8fd338a74d636e2c792f/guava/src/com/google/common/io/Closer.java#L201-L253 | B成熟实现 / `v33.4.8` = `f06690fa3e874f65515e8fd338a74d636e2c792f` | 逐个close、主异常优先、suppressed自抑制保护，连suppression自身抛错也避免替换主异常。本地最多单实例清理，不引入Guava或资源stack。 |
| https://github.com/google/guava/blob/f06690fa3e874f65515e8fd338a74d636e2c792f/guava-tests/test/com/google/common/io/CloserTest.java#L96-L121 ，#L165-L193 ，#L278-L306 | B成熟测试 / 同完整hash | 用真实TestCloseable行为证明创建失败只关已创建资源、主异常对象不变、suppressed含关闭失败；对应FakeSpider事件计数/身份断言，而非mock抛异常后仅verify调用。 |

Guava内容读取固定tag的raw URL，随后 `git ls-remote https://github.com/google/guava.git refs/tags/v33.4.8 refs/tags/v33.4.8^{}` 得到上述唯一直接commit引用。此前GitHub REST tag接口rate limit一次，未同法重试，换只读Git协议确认；未clone/download执行脚本。

资料类别处置：精确上游代码/后续修复/冻结树、官方规格/生命周期、维护者commit说明、成熟实现与测试均已覆盖。论文/性能benchmark/泛博客/设备场景报告本问题不适用：没有新算法、调度策略或声称吞吐/播放性能优化；新增失败回收正确性不能由benchmark证明。不泛搜这些资料。资源实际归零与bridge设备行为仍通过实施后的专项验证解决，不以文档代替。

## 5. 方案比较与推荐

| 方案 | 正确性/兼容与生命周期 | 性能/维护/回滚 | 决策 |
|---|---|---|---|
| no change：保持原样 | 成功缓存与非null fallback不变；init已分配实例未destroy，失败SpiderNull永久cache。重新取同key无法恢复，且失败被activeKeys当active。 | 零改动，零收益；风险已直接源码建立，不需构建才能确认缺陷。 | 拒绝，不能满足已批准业务目标。 |
| upstream：原样移植123cc及最终runtime整迁移 | 新PythonRuntime/RuntimeModule/JsHost、SpiderLoadException/Notify、资源Net/Local/Streams/JS桥，与后续close/destroy/owner合同耦合；Error重抛和普通失败抛异常改变本地消费者fallback。后续最终树已改掉loader负责init失败destroy的模式。 | 跨模块、兼容版本/打包/原生验证增大；依赖/API/ABI/包体/许可来源均需另评估，远大于当前任务；不能只复制文件期待可编译。 | 拒绝当前阶段；新runtime与ownership另任务审批。 |
| adapted：局部失败初始化/缓存事务 | 仅已返回实例初始化失败时一次destroy；保留catch Throwable边界、日志、siteKey、normalizeExt；内部失败返回null，public返回新SpiderNull；成功才缓存。 | 保留CHM成功快路径及同key计算，失败调用会真实重试并付出init/destroy成本，这是需求不是性能改进；不新增锁/future/全局module。一个小helper统一失败清理，最小源码/test/doc原子rollback。 | **推荐，补足旧bridge本地缺陷，适配而非复制上游。** |
| mapping先cache SpiderNull再remove | 能让部分后续调用重试，但瞬时失败值可被其他调用读到，remove可能误删后来成功实例；mapping内改map违背契约。 | 需额外竞争处理，没有优势。 | 拒绝。 |

API/ABI/安全/质量：两loader public签名不变，调用者不改；不改catvod Spider及bridge依赖/二进制，无新的网络能力或脚本权限。失败脚本仍可能执行已有destroy代码，且可卡住/抛错，入口是尽力回收不是安全沙箱。不声称内存/CPU/包体数值收益或性能已验证；成功路径不新增外部I/O/全局锁，失败重试频率不引入backoff、retry循环或negative cache。正常脚本/代理/标准流输出质量需后续验证。

## 6. 最小设计接缝（阶段2候选，不含实现代码）

未来允许路径：
1. `app/src/main/java/com/fongmi/android/tv/api/loader/PyLoader.java`
2. `app/src/main/java/com/fongmi/android/tv/api/loader/JsLoader.java`
3. 必要唯一小helper：`app/src/main/java/com/fongmi/android/tv/api/loader/SpiderInit.java`
4. 相应测试：`app/src/test/java/com/fongmi/android/tv/api/loader/SpiderInitTest.java`（纯行为测试）；仅确有Android日志/normalize接缝需要才追加 `app/src/testLeanback/java/com/fongmi/android/tv/api/loader/LoaderFailureLoggingTest.java`。
5. 唯一任务文件：`docs/C-TV-PYTHON-CLEANUP-failed-init.md`。

helper是包内的**创建—初始化—缓存事务**，不是BaseLoader父类、runtime管理器或泛资源框架。它接收现有ConcurrentHashMap、key、factory（Callable<Spider>）、初始化回调（允许Exception）、失败日志回调；唯一load入口执行真实CHM计算，计算内持有本次spider引用、成功才返回实例，catch Throwable时只回收本次已创建spider、保留primary、执行现有错误日志、返回内部null；计算结束后将null转换新SpiderNull。两loader只供应现有真实factory和原样初始化/日志内容。App.get、Py normalizeExt、JS dex(jar)仍在本次创建/初始化的捕获边界内，不提前在helper调用实参外执行而漏掉fallback。

- 不为测试新增两loader构造器、setFactory、protected newSpider、反射改final、Unsafe、mock native runtime或test-only生产分支。
- FakeSpider必须继承**真实**catvod Spider，实现真实init/destroy事件、计数、资源标志、失败注入；factory只返回该FakeSpider或在返回前抛错，初始化回调直接调用FakeSpider.init，不模拟helper返回值。
- helper接缝承担本次公共业务事务，测试其返回对象与后续取同key行为，不窥探私有map状态；缓存可用返回identity、factory调用数与资源状态验证。两loader极薄适配的正确接线通过完整diff审查及真实脚本smoke覆盖，不能把helper单测宣称成原生bridge已测。
- cleanup只有一处：若spider非null则尝试一次spider.destroy；destroy Throwable与primary不同才addSuppressed。必要按成熟实现防御suppression附加自身失败，决不替换primary；主错误诊断至少仍可输出。保留现有catch Throwable策略（含Error fallback），不采用上游Error重抛。
- 不在mapping内调用map.remove/clear，不将SpiderNull写入map，不主动释放同key以外实例。成功实例直到现有clear才销毁，本次成功不destroy。
- 日志回调不得抛错打断fallback；窄适配至少防御清理/附加异常，不借机重构日志系统。若日志设施本身故障，使用既有标准错误输出保留primary，不创建新collector/hook。

## 7. 接受合同与最小TDD垂直切片

阶段2按“一个测试红 → 最小实现绿 → 下一个测试”，不是一次写完测试批量上生产。以下是设计用例清单，不表示已执行；seam由父协调者随此设计审批固定，实施时不得偷偷扩大。

| 次序 / 测试 | 真实输入与独立断言 | 必须保持的合同 |
|---|---|---|
| T1 成功缓存 | FakeSpider init记事件、分配标志；连续两次load同key同一实例，factory/init各1，destroy=0；homeContent返回已知字面量 `ready`。 | 正常成功不回收、不重复初始化，不返回SpiderNull。 |
| T2 已创建init失败 | init置allocated后抛预建primary；结果非null SpiderNull，destroy=1且allocated释放；事件factory→init→destroy→诊断，诊断primary身份相同。 | 最关键red：当前行为不会回收。失败实例不发布。 |
| T3 同key失败后成功 | factory队列先失败FakeSpider、后成功FakeSpider；第二次load获得成功实例，第三次identity相同；factory=2，失败destroy=1，成功destroy=0。 | 最关键red：当前SpiderNull永久cache导致第二次仍fallback。无自动重试循环，仅新调用重试。 |
| T4 factory失败与null | factory在返回前抛预建异常，init/destroy均0，非null fallback；再调用可成功。factory返回null时也失败、不cache、不destroy，并保留合理诊断。 | 不尝试回收不可得的半构造对象；不清runtime/其他模块。 |
| T5 destroy失败 | init抛primary；destroy计数+1后抛cleanup；结果仍fallback，诊断保持primary且getSuppressed含cleanup。destroy抛Error亦不掩盖primary。 | 清理只一次，cleanup不取代init；可重试。 |
| T6 同一Throwable / Error | init和destroy抛同一个对象：不自抑制、不产生IllegalArgumentException；init抛AssertionError时仍遵守本地catch Throwable fallback且尝试回收。 | 保留本地错误策略，不擅改fatal传播。suppression禁用时原异常身份不变，承认标准API不保存suppressed。 |
| T7 same-key并发成功 | 两线程用CountDownLatch控制首个init进入/退出，无sleep；首个未完成时另线程请求同key；完成后两者同一成功实例，factory/init各1，无未初始化发布。 | CHM atomic计算；不夸称全局无锁或所有不同key永不阻塞（hash碰撞可阻塞）。 |
| T8 same-key并发失败再成功 | 首次init失败前用latch让另请求排队；失败调用得fallback，第二次实际factory成功，后来调用命中成功；失败destroy在第二次开始前完成。 | 不要求所有并发调用共享同一失败结果；失败null不cache，下一次调用可以计算。所有失败时每次调用可分别尝试，不引入single-flight失败缓存。 |
| T9 key隔离 / active/proxy | 成功key A保持identity与destroy=0，B失败回收不影响A；失败key无成功cache，A的proxy正常、B recent proxy没有失败spider可调用；activeKeys只体现成功键。 | 不销毁其他站源，不调用全局Module.clear。activeKeys/缓存清理的失败键差异有意且已记录。 |
| T10 Py/JS适配与标准流日志 | Py真实init空扩展 `[]`→`{}`、其他ext含null/空白不改；两类siteKey在init前已赋；JS仍传原ext/dex；primary stderr堆栈存在；JS原有key/api与throwable两条SpiderDebug诊断仍在；suppressed可见。 | 原stdout/stderr引用不替换，Python logging仍由现有install一次拥有。单例failed wrapper日志1次，不将其和JS bridge自身cleanup日志混算。 |

建议最小验证路线（未来执行，不在本阶段执行）：
- JDK21：`JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`；已有Android SDK在用户Maple目录，具体sdk.dir由干净实施树local.properties只读确认，不写配置。Git Bash `G:\Git\bin\bash.exe`。
- 首先 focused JUnit，使用现有variant：`bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.api.loader.SpiderInitTest`；日志接缝需要时同一次任务加对应 `--tests ...LoaderFailureLoggingTest`。实际task是否存在以实施树任务输出为准，不能本阶段靠构建猜测。不跑全Gradle矩阵/native构建。
- 复用标准流回归：`python -B -m unittest discover -s chaquo/src/test/python -p test_webhtv_logging.py`，保留首次完整结果，不安装依赖/改bridge；若本机Python缺模块，记环境门槛，不伪报通过。
- Android日志与normalize需要Robolectric时使用已有testLeanback配置，FakeSpider在init分别写已知stdout/stderr标记，捕获并在finally恢复，不并行改全局标准流。断言原标记没有额外复制、原异常仍在stderr；不模拟Python logging安装成功。
- 最后真实Android旧bridge小smoke：一个Python脚本已创建后init抛错/下一次可成功，一个JS脚本部分初始化失败/下一次成功；保留正常站源A，确认B回收不影响A、JS退出executor（尤其ctx/jsObject为null的早期失败），以及既有日志输出。缺设备时明确不宣称真实资源生命周期通过。
- 成功/失败路径完整diff审查：Py normalize/siteKey、JS日志/dex、helper fallback边界、public signatures、activeKeys/proxy行为、未改clear/BaseLoader/Module、未动依赖。行为测试与device证据分别标记，禁止拿编译成功替代生命周期正确。

## 8. 未批准问题、风险处置与未完成门槛

| 风险 / 门槛 | 已采取的设计处置 / 后续动作 |
|---|---|
| clear与init迟到发布、BaseLoader owner交换、全局JS Module清缓存误伤 | 明确暂不做，属于 C-TV-LOADER-OWNERSHIP；本设计不新增保证，验收same-key无clear竞争。父不能将本任务完成当成ownership已解决。 |
| factory失败前Python import/module/磁盘副作用 | loader未取得Java实例，不能按假设close；本任务fallback+不cache即可，不卸载sys.modules、不清其他Python站源、不重启Python。另需求另批准。 |
| 第三方destroy可能抛错、吞错、挂住或不清资源 | 尝试一次并保留primary；旧bridge内部吞错的事实记录，不声称所有资源归零。真实JS/Py smoke为阶段2门槛；若实测挂住/残留，回报父分清本任务可修范围和新增bridge设计审批，不能静默交付 defect。 |
| 上游最终生命周期与本地不等价 | 读patch与两次后续正文/冻结树并明确reject整迁移；不引用最新行为作为旧bridge可省destroy的依据。 |
| 失败重复请求成本/日志增多 | 无永久negative cache的直接产品结果；不引入backoff或无限自动retry。成功缓存不退化为每次init；不声称benchmark提升。 |
| 测试接缝逃逸成test-hook架构 | 一个业务helper、真实FakeSpider，不注入两loader构造器或native mock；薄适配差异须diff审查+smoke，不能虚假宣称helper测试等价真实runtime。 |
| suppression或日志自身异常 | 自抑制身份保护，必要防御附加/日志异常；原异常不被替换。禁用suppression时不承诺secondary一定出现在getSuppressed。 |
| GitHub REST rate limit / 网页聚焦退化 | 已换只读Git refs和官方关键区块提取，不反复失败同法；核心来源完整，无必需研究因访问失败缺失。 |

**阶段1可以交付decision-ready设计；阶段2/代码完成尚不具备门槛：**父提供并核实干净实施树及实施前完整HEAD；复制唯一任务文件并登记批准设计/路径；实际red-green记录、focused测试、完整diff及旧bridge最小device smoke；若device不可用，实测生命周期仍是未完成门槛。阶段1没有build/test/commit/tag结果，不能填写“通过”。

## 9. 回滚与Recovery anchor

- 阶段1回滚仅撤去本唯一设计文，不能改生产/保护脏路径；不执行删除或reset。
- 阶段2在干净实施树记录实施前完整HEAD，源文件+必要helper/tests+唯一docs作为一个任务guard/原子commit及唯一recovery tag；本任务批准并不授权push。
- 候选失败时不发布、不叠ownership改动；未提交时仅撤销本任务自有diff，提交后优先 `git revert <本任务完整commit>`，保留其审计文档并记录回滚事实；不使用 `reset --hard`、不清app/.cxx、不要以本只读root HEAD重置别的实施树。
- 回滚验收恢复成功缓存、原fallback/日志/normalize/siteKey基线；回滚会恢复失败永久cache/未回收缺陷，应明确已撤回候选，不称问题解决。

### Recovery anchor档案（2026-11-08，阶段1；阶段2已接续）

- 目标：仅失败init实例局部回收、不cache失败、外部非null fallback与原诊断/扩展/siteKey不变。
- 已完成：固定本地/上游身份；两loader真实消费者/bridge destroy；Java21 CHM/Map/JLS/Throwable、Chaquopy生命周期及Guava成熟代码/tests；方案比较、合同、TDD、回滚。
- 唯一变更：当前 `.git/tv-implementation/design/C-TV-PYTHON-CLEANUP-failed-init.md`；无生产/test/runtime编辑；本地保护脏路径不纳入任务。
- 未验证：所有未来实现/构建/行为与设备门槛；未解决ownership、runtime内部factory副作用或第三方destroy质量。
- 阶段1只读验收：2026-11-08 14:42:06，`git diff --check`无输出；HEAD仍为完整基线，`git status --short`仍仅两项初始保护脏路径；唯一设计文存在，核心合同/10项TDD/方案/回滚/门槛内容检查完成。该命令不校验.git内文档，文档另按行尾空白、冲突标记及引用字段检查；不把无生产diff称为行为测试通过。
- 唯一下步：父协调者审阅本设计并提供干净实施树，复制为唯一 `docs/C-TV-PYTHON-CLEANUP-failed-init.md` 后才开始TDD实施。

## 阶段2实施结果与接受结论（2026-11-08 15:00 UTC+08）

### RED → GREEN与原始证据

- 首次真实RED（生产编辑前）：`./gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.api.loader.LoaderFailureLoggingTest --console=plain`。14:53:26原生只读工具读取JUnit XML：4 tests /4 failures /0 errors。Py/JS回收断言 expected `[init, destroy]` /actual `[init]`；Py/JS同key重试断言 expected成功FakeSpider identity /actual SpiderNull。新helper不存在不曾作为编译RED。
- RED后台shell后来不在job登记中，初始redirect日志停在编译段，未捕获Gradle最终exit/summary。因此不伪报该shell退出码；原生工具实际读到的4项XML断言已追加至RED日志为标注记录。后续GREEN覆盖同名XML。该业务RED事实与shell日志完整性分别陈述。
- 核心GREEN：同一4项真实loader测试，`BUILD SUCCESSFUL in 33s`，JUnit4 tests /0 failures /0 errors。最小实现后补充的边界用例是合同回归/表征，不将其立即PASS冒称每一项都独立经历行为RED。
- 最终GREEN：`./gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.api.loader.SpiderInitTest --tests com.fongmi.android.tv.api.loader.LoaderFailureLoggingTest --console=plain`；exit0，`BUILD SUCCESSFUL in 25s`。SpiderInitTest **14 tests /0 failures /0 errors**；真实PyLoader/JsLoader的LoaderFailureLoggingTest **9 tests /0 failures /0 errors**。共23项，JUnit计数及各case名称追加在final日志。
- 编译证据来自同一最小Gradle任务：app生产Java与UnitTest Java编译成功；没有另跑全构建。现有Java deprecated API及Gradle10兼容性deprecated feature提示保留，不修改构建或依赖，不把警告称为本任务新增回归。
- 既有标准流回归：`python -B -m unittest discover -s chaquo/src/test/python -p test_webhtv_logging.py`，exit0，**7 tests /OK**，0.010s。没有改Python/日志安装；`-B`不写pycache。

日志（均为实施树下授权忽略文本，不stage/commit）：
- `.codex/task-state/reports/C-TV-PYTHON-CLEANUP-red.log`
- `.codex/task-state/reports/C-TV-PYTHON-CLEANUP-green-core.log`
- `.codex/task-state/reports/C-TV-PYTHON-CLEANUP-green-final.log`
- `.codex/task-state/reports/C-TV-PYTHON-CLEANUP-python-logging.log`
- `.codex/task-state/reports/C-TV-PYTHON-CLEANUP-closure.log`（guard闭环输出）
- `.codex/task-state/reports/C-TV-PYTHON-CLEANUP-report.md`（最终commit/tag与自包含交付报告）

### 实际变更与合同对应

- 仅6个tracked任务路径：两loader、包内SpiderInit、纯helper test、真实loader Robolectric test、唯一taskdoc。无生产构造hook/Unsafe/native mock，Robolectric仅shadow外部语言Loader constructor/factory；FakePythonSpider/FakeJavaScriptSpider是对应真实Spider子类，init/destroy执行真实事件与模拟资源副作用。
- 唯一 `SpiderInit.load` 负责CHM计算内持有本次实例、成功返回实例、失败一次destroy、primary优先/secondary suppressed、失败计算null；映射外返回新SpiderNull。诊断回调自身抛错也保持primary。factory null/抛错无可回收实例，不cache。
- Py factory、siteKey赋值、App.get/normalizeExt仍在被捕获初始化边界；JS dex/API/原ext与原两条SpiderDebug+堆栈不变。完整diff审查确认clear/BaseLoader/Module/bridge/依赖/二进制未动；成功cache hit无factory/init/destroy，新失败请求不触发内部自动retry。
- T1/T2/T3：成功identity/一次init、不回收成功；失败destroy一次；同key新调用恢复并cache成功——真实两loader及helper覆盖。
- T4/T5/T6：factory抛错/null、checked init exception、init Error、cleanup RuntimeException/Error、同对象自抑制、禁用suppression、日志回调失败——14项helper覆盖，保留本地catch Throwable fallback。
- T7/T8：真实CHM事务用CountDownLatch而非sleep实现same-key成功共享、失败清理后等待请求重试成功；future/latch都有5s测试超时，finally shutdown测试executor，未改生产并发策略。
- T9：失败key不active/不能recent proxy，另一成功keyidentity/资源/proxy不被回收——helper和真实loader覆盖；不声称clear竞态解决。
- T10：Py null/空/空白/对象ext原样、trim后的 `[]`→`{}`；JS原ext/siteKey；真实failure原标准流不替换、标记不重复、原异常及suppressed stderr、JS两条既有诊断保留——9项真实loader覆盖；Python既有7项验证原logging/stdout/stderr安装合同。

### 风险处置、未实测边界与回滚

- native资源实际归零仍未实测：两个可用emulator为x86_64，5556已有用户TV包，项目受批准ABI仍仅ARM；不改flavor/依赖、不装包、不清数据。按父裁定此不阻塞Java调用/缓存合同，但不能声称真实QuickJS executor/Python资源归零或真实脚本destroy无挂死。
- 旧Python bridge会吞Exception，QuickJS bridge会自行打印部分cleanup错误；本任务只保证一次调用失败实例destroy入口及可恢复缓存，不改变第三方脚本cleanup质量，不卸载模块/close共享PyObject/重启Python。
- BaseLoader owner交换、异步clear迟到发布、全局Module.clear、factory返回前模块/磁盘副作用均明确暂不做。失败高频调用真实重试是批准需求，不添加negative cache/backoff，也不声称性能提升。
- 未观察到本任务代码/test Java合同缺陷；所有已见风险已按上述边界处置。真实设备问题如果后续出现必须反馈父再划分范围，不用记录风险代替修复。
- 原子rollback：本任务commit只含上述6个文件；guard将验证scope后提交并立即创建恢复tag。实际完整commit/tag由guard输出记入授权report；不为了自引用hash另开第二docs提交。回滚使用 `git revert <该完整任务commit>`，不reset原root或撤父索引commit，不触碰用户脏路径。

### 最新Recovery anchor（阶段2，自检完成，guard闭环负责提交）

- 目标/批准：用户批准失败实例局部回收、不cache失败、非null fallback、成功缓存/原日志/ext/siteKey；父批准真实loader factory-only test shadow与跨任务index前置。
- 实施树：`upstream/python-cleanup-20261108`，pre-change HEAD `47454687f504955775b398905ba09d73aeb9f20c`；父index位于集成commit `60f2e86e761b8411e56a86793ee55a2f0b9b67d7`。
- 已完成：真实业务RED4项；最小生产窄适配；最终Java23项GREEN/目标编译；Python标准流7项GREEN；源码完整diff与范围自审。
- 变更：两loader/SpiderInit/两test/本唯一doc；忽略日志与report不提交。
- 未实测：native资源实际释放、第三方destroy挂死/跨站副作用；新owner/clear合同仍未批准。本阶段明确不做设备装包/native rebuild。
- 唯一下步：执行guard finish原子提交并立即恢复tag，把其完整输出与最终交付状态写入指定report，交父独立复核。
## 独立审查F1/P2返工（实施run2/3，2026-11-08 16:00 UTC+08）

- 父独立review `F:\Workspace\fish2018\webhtv\.git\tv-implementation\reviews\C-TV-PYTHON-CLEANUP-review.md` 对首个commit `d3a7298d5f3a6f76f5f2a256d4fd6f02ff7cecef` 判定Spec FAIL/Quality changes-needed，唯一F1/P2：SpiderInit原:38与:51的最后备用printStackTrace未隔离，primary打印/toString/输出设施继续抛Throwable时会逸出computeIfAbsent，遮盖错误fallback。原23项正常GREEN仍有效，但不能证明此异常分支。
- 已读review与实际源码，缺陷静态可达，本返工不扩大到bridge/native/owner。16:00:29固定clean leaf HEAD仍首个commit，新guard `C-TV-PYTHON-CLEANUP-DIAGNOSTICS`，scope仅SpiderInit.java、SpiderInitTest.java、本唯一doc。原guard/旧commit/tag不改。
- 精准RED用真实SpiderInit事务+真实FakeSpider init/destroy行为：primary覆写printStackTrace持续抛AssertionError，同时诊断callback抛IllegalArgumentException；期望无diagnostic逸出、public非null SpiderNull、已创建实例destroy一次、same-key后续恢复成功并cache。不是mock返回值，不加生产hook。
- 新日志：本叶子授权忽略文本 `C-TV-PYTHON-CLEANUP-diagnostics-{red,red-xml,green,closure}.log`，RED会保存完整XML为文本log以免最终GREEN覆盖；不重复Python标准流/native/device测试。
- 最小修复设计：只把两处最后后备printStackTrace收束到同一私有best-effort打印函数，捕获Throwable后停止输出，禁止继续递归诊断；正常回调/primary/suppressed/一次destroy/成功cache无改动。

### F1精准RED结果（生产返工前）

- 原精准测试一次：`./gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.api.loader.SpiderInitTest.failedFallbackPrinterCannotEscapeOrPreventSameKeyRecovery --console=plain`。
- 2026-11-08T08:03:04.084Z的原始XML：1 test /1 failure /0 errors，断言为 `Diagnostic output must not escape the fallback expected null, but was:<java.lang.AssertionError: fallback printer failed>`；此前failed实例init/destroy事件及资源断言已过，证明缺陷只在最后诊断异常逸出，不是未回收或失败缓存。
- 完整原始XML已保存为授权文本 `C-TV-PYTHON-CLEANUP-diagnostics-red-xml.log`；background job登记消失后由原生工具直接收取，没有重复启动RED或推测shell退出码。RED Gradle日志结尾不完整与完整JUnit证据分别说明。
- 最小实现：两处最后打印改调同一私有 `printBestEffort(Throwable)`，该函数捕获任意Throwable后直接结束，既不递归日志也不改primary/suppressed；正常log callback、成功路径、cache/fallback、一次destroy均未改。最终focused GREEN只运行一次新case+既有14+9项，不重跑Python/bridge/device。

### F1 GREEN、自审与返工闭环（实施run2/3）

- 最终focused命令：`./gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.api.loader.SpiderInitTest --tests com.fongmi.android.tv.api.loader.LoaderFailureLoggingTest --console=plain`；exit0，BUILD SUCCESSFUL in 44s。
- JUnit：SpiderInitTest 15 tests /0 failures /0 errors（原14项+精准F1 case）；LoaderFailureLoggingTest 9 tests /0 failures /0 errors；共24项。新case实际返回non-null SpiderNull、失败资源回收事件恰一次、原diagnostic仍suppressed于primary、同key后续成功且保持cache。原14+9正常日志/异常/成功缓存合同不退化。
- diff自审仅SpiderInit两处最后后备输出换私有best-effort函数、一个精准test与本doc；未改两loader或loggingtest、未加public API/test hook、未改native/依赖/设备。无法再输出错误时停止输出而保证fallback，不将“日志永远可见”作为物理坏stderr条件下不可能的合同。
- 风险路由：独立review唯一F1已用实际RED→GREEN修复，不只记风险放行；native实际释放/第三方destroy质量/owner与模块副作用仍按首轮批准边界未实测或暂不做，不重复测试、不扩大功能。
- 第二原子闭环由新guard `C-TV-PYTHON-CLEANUP-DIAGNOSTICS` finish负责，仅source/test/doc三个文件，不amend首个commit或移动旧tag。完整第二commit/tag写授权report及closure日志，父最终归档exactID，无第二纯doc闭环提交。
- 回滚诊断返工时revert第二原子commit；若需撤整个失败初始化修复，逆序revert第二及首个commit，不reset父index或原root。

### 最新Recovery anchor（F1返工自检完成）

- 固定父review head `d3a7298d5f3a6f76f5f2a256d4fd6f02ff7cecef`，implementation累计run2/3，leaf分支与scope不变。
- 已完成：原精准RED完整XML文本快照1 failure；F1两处诊断隔离；最终24项GREEN+目标生产/test Java编译；三个路径diff/格式验收。
- 当前tracked变更仅SpiderInit、SpiderInitTest、本唯一doc；原两loader/bridge/依赖/first commit/tag不改；忽略日志/report只用于证据。
- 唯一下步：新guard finish第二原子commit/tag并追加报告，交父独立re-review。

## 最终集成归档 — 2026-11-08

- 用户已批准本任务窄适配，原始代码提交及恢复点在合并历史保留，不重写。
- 代码/修复提交：`d3a7298d5f3a6f76f5f2a256d4fd6f02ff7cecef` → `c861fac058e89136a6a2be233262b532fb4ceee6`
- 本地恢复 tag：`recovery/C-TV-PYTHON-CLEANUP/20261108070129-d3a7298d5f3a`
- 本地恢复 tag：`recovery/C-TV-PYTHON-CLEANUP-DIAGNOSTICS/20261108080906-c861fac058e8`
- 单元证据：24项定向测试通过；F1诊断后备已修复且独立复审通过；原生资源归零未设备测
- 联合验证（2026-11-08 集成树 fresh）：9 类 94 项定向测试 0 失败/0 错误/0 跳过；Leanback Mobile arm64 Debug Java、Mobile UnitTestJavaCompile 编译通过；日志 `.codex/task-state/reports/TV-SMALL-FIXES-INTEGRATION-final.log`。整批仍不冒充 decoder 实播/设备/性能验收。
- 回滚：针对本任务按上述代码链逆序git revert；合并历史保留真实来源，不reset原工作区或移动tag。
