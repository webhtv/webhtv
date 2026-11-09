# H-ORIGIN-RESUME：完整隔离的历史原源播放实施设计

**状态**：研究／文档交付；未实现、未构建、未验证设备。2026-11-09，执行简报第 1/3 次。
**目标**：切配置后，历史原配置／站源仍可用就从原源续播，当前配置始终不切、不污染。
**推荐**：独立 Android 进程 + 私有运行快照 + 代理／Node 租约；不是给 spider 多加 namespace。
**技术**：Android Service/Binder、现有 Room/Gson/OkHttp、现有 SiteApi/Source/ParseJob、现有 PlaybackActivity/PlayerManager；不升级依赖。
**实施顺序**：用户已改为两阶段。阶段 1 打通完整隔离骨架和一个真实播放入口；阶段 2 接 WebHome 与双端完整交互。阶段 1 通过不等于原始完整目标完成。

## 1. 已确认用户记录（四行）

1. 选择“完整隔离”：手机／TV／WebHome 跨配置历史优先原源，主进程当前配置、认证、解析器、缓存及代理不切换、不污染。
2. 仅原配置不存在／加载失败、原站源消失、详情失败、目标集缺失可进入已有当前配置匹配；起播后 URL／网络错误不由续播协调器接管。
3. 否决临时切全局再恢复、直链特例与把“不支持隔离”冒充“原源不可用”；有隔离阻塞应明确报错，不暗中匹配当前源。
4. 新决定：分两阶段，先做原源进程骨架、原源详情／目标集及单入口真实播放；再补 WebHome 与双端细节；原始完整目标及约束仍保留。

## 2. 决定性本地证据与方案裁决

所有本地定位基于 `dev2` / `15a893a50cbbbfd99576700865813b57404b2706`（40 字符）；访问日 2026-11-09，等级 **L：已读本地源码**。初始／首次编辑前 `git status --short` 均空。

| 源码路径（`app/src/main/java/com/fongmi/android/tv/` 下，另标模块） | 已确认事实／设计影响 |
|---|---|
| `ui/activity/HistoryResumeCoordinator.java:39–53,113–145`；`history/HistorySourceResolver.java:44–78` | 外部历史目前直接当前源搜索；必须在原有 fallback 之前插入原源尝试，不重写当前匹配器。 |
| `api/config/VodSiteCatalog.java:57–84,208–259`；`api/loader/BaseLoader.java:136–185` | Catalog 只有 sites/doh/rules/flags；namespace 只分 spider 实例缓存槽；不是可运行的配置会话。Catalog 的 Node bundle 路径仍 `CatSource.reuse`。 |
| `api/config/VodConfig.java:93–151,175–180,522–546`；`BaseConfig.java:119,135` | load/selectConfig 清单例／loader、写配置及 live/wall、发事件。不得在主进程调用；子进程也必须先安装私有 DB／偏好，不能照搬共享库加载。 |
| `api/SiteApi.java:149–190,282–349`；`player/ParseJob.java:69–81,132–145,303–311` | detail/player/flags/parser/click/Jar 仍读单例；`playerContentIsolated` 仅 `new Source()`。ParseJob 用 `CustomWebView.create(App.get())`，不能把所有解析扔回主进程。 |
| `api/loader/{JsLoader,PyLoader}.java` 的 `recent`/`proxy`；`JarLoader.java:48–52,223–237,303–329` | JS/Py 的 recent 各自唯一；Jar Dex／methods 按 jar md5 共享。不能通过缓存 namespace 约束未知第三方静态变量。 |
| `chaquo/src/main/java/com/fongmi/chaquo/Loader.java:25–50`、`Platform.java:74–81,126–143`；`quickjs/.../crawler/Spider.java:165–186`、`utils/Module.java` | Python/app 共享解释器／模块；JS 有独立 context 不代表 Java helper／Module／文件状态独立。子进程须在初始化解释器前安装私有 Context。 |
| `catvod/.../net/interceptor/AuthInterceptor.java:17–56`；`net/OkHttp.java:72–74,101–115` | host→账号映射属于共享客户端；`newBuilder()` 不能当隔离。源配置 userinfo／headers 与主进程账号绝不混用。 |
| `App.java:120–153,216–224`；`AndroidManifest.xml:189–195` | attachBaseContext 会处理 WebView 锁并 Init.set；onCreate 仅排除 recovery，其他子进程仍预热 Python、插件、后台 Server 等。新增进程必须在这些副作用之前分流。已有 NodeService 在 `:node`。 |
| `api/CatSource.java:42–79`；`node/NodeRuntime.java:195–240`；`NodeService.java:46–108` | 不同 bundle 会 stop 原 NodeService；Node 单进程只能启动一次，销毁时 kill 自己。必须独立 `:origin_node`，不能复用／停止当前 `:node`。 |
| `node/NodeBundle.java:237–251`；`catvod/.../utils/Path.java:37–54` | Node 默认 files/node/bundle；Path.root 直接外部存储，不经过 Context。仅包装 filesDir 仍不隔离，必须补 Path.root 的进程作用域。 |
| `server/Server.java:52–78`；`player/PlaybackRouteRegistry.java`；`server/process/SpiderApi.java:154–160` | Proxy.port 是进程内 static，Server 抢 9978–9998；不得在 worker 启动整个管理服务器。远端播放不能一律把 loopback 改 LAN。 |
| `db/AppDatabase.java:175–198`；`bean/History.java:996–1022,1183–1215` | Room 未启跨进程失效；历史保存含 TMDB／季进度事务。主库只由主进程写，worker 只处理快照；进度 cid/key 必须来自原历史。 |
| `ui/activity/PlaybackActivity.java:205–211,597–634`；`player/PlayerManager.java:6278–6316` | 可建一个共同播放入口，复用现有 startPlayer／PlaySpec／PlayerManager；必须返回已完成原解析的 Result，防止主进程再取当前 parser。 |
| `web/HomeWebBridge.java:147–160,267–305,464–465`；两份 flavor `ui/activity/VideoActivity.java` | app.history 仅 History.get 当前 cid；playVod 当前组合键查历史。已有 payload/options 足够扩展，阶段 2 不建新桥。双端 getSite/getHistoryKey 仍需明确会话身份。 |

| 方案 | 裁决 |
|---|---|
| 无改动 | 不满足原源优先；只能作为整阶段回滚。 |
| 原实现直接切配置 | 用户明确否决；清 loader、认证／插件／Node 副作用不可通过 finally 恢复。 |
| 同进程显式 Session + namespace | 可减少宿主显式全局读取，但未知 Jar/native/Python 模块、host 账号及 recent 仍共享；完整达标需要控制第三方全部实现，不能推荐为本任务终点。 |
| 独立 `:origin` + `:origin_node` | 推荐；静态／解释器／代理与主进程分开；**进程属性本身不是充分条件**，还须下面的存储、网络、身份和生命周期契约。 |

“完整隔离”在此指配置与播放运行状态隔离，不宣称对恶意 Jar 的安全沙箱：私有进程仍同 UID，可绕过宿主路径助手访问绝对路径。若验收包含防恶意代码访问当前数据，最短替代是独立 UID 的伴随 APK／受控运行宿主，而非扩大 namespace；必须另获授权，不能假装 `android:isolatedProcess=true` 可直接运行现有网络／本地文件插件。

## 3. 一手证据与最佳实践门

访问日均 **2026-11-09**。等级：**A** 官方契约／规范，**B** 第一方实现，**L** 本地源码，**U** 未取得／不得作为支持证据。

| 来源、版本／URL | 等级、支持论断与适用局限 |
|---|---|
| AOSP 官方进程文档，rev `f25ab44975bb0aaeaa88e8dc564f160bb640902e`：https://android.googlesource.com/platform/frameworks/base/+/f25ab44975bb0aaeaa88e8dc564f160bb640902e/docs/html/guide/topics/fundamentals/processes-and-threads.jd | A；已读取 `?format=TEXT` 正文；manifest 可令模块分属不同进程，进程可被系统回收。旧文档不证明现代 FGS 条件或插件兼容性。 |
| Android 官方 Room 文档：https://developer.android.com/room （动态文档，无固定 rev） | A；实际 HTTP 200 并读到多进程要求 `enableMultiInstanceInvalidation()`。本设计避开共享 Room 多写者／监听，而非无理由全库加跨进程广播。 |
| AOSP `android-14.0.0_r1` / `core/java/android/os/IBinder.java`：https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-14.0.0_r1/core/java/android/os/IBinder.java | A/B；已读：跨进程调用在线程池执行，transact 可阻塞，linkToDeath 在对端进程消失时通知；suggested IPC size 为 64 KiB。支持异步工作队列、死亡处理和大结果 FD 传输，不是业务超时／Exactly-once 保证。 |
| AOSP `android-14.0.0_r1` / `core/java/android/webkit/WebView.java`：https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-14.0.0_r1/core/java/android/webkit/WebView.java | A/B；已读 setDataDirectorySuffix／disableWebView 的进程目录冲突契约。成熟类似运行时的一手实践：不同进程必须分目录；不能推导任意 Android 旧版本已有此方法，旧系统列入实现定向门。 |
| OkHttp tag `parent-4.12.0` / `okhttp/src/main/kotlin/okhttp3/OkHttpClient.kt`：https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/OkHttpClient.kt | B；实际读到 Builder(okHttpClient) 复用 dispatcher/pool/interceptors/authenticator/cookieJar/cache。隔离不能通过当前 client.newBuilder 完成；worker 新进程客户端与私有缓存才是合适 Adapter。版本用于检验原则，不授权本仓库升级。 |

证据类处置：官方规范、第一方实现、成熟运行时类比已覆盖；本地直接 tests 的可执行锚点是 `LoaderNamespaceSlotTest`、`HistoryResumeCoordinatorTest`、`NodePortSelectionTest`。尝试上游 `ConnectionReuseTest.kt` 的旧 tag 路径返回 404，**未取得上游测试，不计已读证据**。维护讨论／issue 不用于替代平台契约：本次无已读且针对“任意多运行时配置隔离”的维护结论，保留为 U，不以搜索片段作兼容承诺。论文／性能报告不适用：本裁决是可变状态所有权及平台能力约束，未引入新 IPC 算法、未宣称性能收益；新增 RSS／冷启动须设备测量。

网络只读公共资料，未上传本机内容／改代理；查询 3 个变体后停止。read_page 因 Firecrawl 代理策略失败；官方部分网页直接抓取超时后改 AOSP 正文。FGS 页面未成功读到正文，不声称核验了全部新系统条件。**门结论**：足以否决同进程伪隔离并推荐进程方案；不是所有系统／插件均已通过的兼容证明。缺失的旧 WebView、Node 固定端口和后台租约证据是实现阻塞门，不是匹配 fallback 原因。

## 4. 最小 deep Module 与精确 Interface

### 4.1 所有权与数据流

主进程 `OriginSessionClient` 是调用者唯一 seam：只读原 Config／History／配置相关偏好 → 生成一次性快照 → bind 私有 `OriginSessionService(:origin)` → 原配置加载 → 原站详情／原目标集 → 原 playerContent／Source／ParseJob → 可供现有播放器消费的结果。

* 一次只持有 **1 个原源配置租约**；租约期间不换 worker 配置。新点击取消旧代次；换原配置需关闭旧进程并等 Binder death，再起新租约。每个进程只初始化一次解释器／Node。
* `App.attachBaseContext/onCreate` 最早识别 `:origin`／`:origin_node`：禁止主 Server、管理／远程同步、NSD、插件全局预热、当前配置 failover、launcher/theme 写入及默认 WebView 锁清理。仅保留 Handler/Gson、必要 Init、网络只读代理设置及该进程受控初始化。
* worker 的 App 启动先只识别进程并保留 Handler/Gson，延迟 Init／全部运行时；`open` 收到 leaseId 后先安装 `OriginStorageContext`，再 Init／加载。映射 files/cache/codeCache/getDir/database/SharedPreferences，`getApplicationContext()` 不得逃回 base；`Path.root()` 增进程 override。Node Service 也必须使用该 Context，不能因为 App 已包装就继续把未包装 `this` 交 NodeBundle。原本地输入先解析为只读绝对路径，写入始终归私有根；两 worker 用同一 lease 目录，绝不当前 `node/bundle`。
* 主进程只读并发出 JSON 快照。worker 使用私有 Room 库，种入原 Config、相关 Site 与必要设定，不复制当前激活 cid；worker 的 VodConfig.load 可以只在私有库内激活原配置。禁主库配置／历史／偏好写入与主进程事件通知。订阅 TMDB 凭据在 worker 重新提取，绝不从当前单例借用。
* 源账号仅来自原配置 userinfo/header/ext 或原配置专属持久状态。主进程 host 账号映射不导出。全局旧插件凭据若不能追溯到原配置，禁止猜测归属；报 `ORIGIN_AUTH_REQUIRED`，允许原源登录／实际详情重试，不把“迁移不了状态”编码成配置不可用。

### 4.2 Binder schema（定义在 OriginSessionContract）

```java
// Bundle 是小控制消息；jsonFd 指向 UTF-8 JSON 流，不传活 Site/Spider/Room/Player 对象。
// Contract 内提供手写 Binder Stub/Proxy，固定 transact 编号；不新增 AIDL 构建插件。
interface OriginSessionPort extends android.os.IInterface {
    void open(Bundle request, ParcelFileDescriptor snapshotFd, IBinder owner,
              OriginReply reply);                  // 异步，不在 Binder/UI 线程做 I/O
    void play(Bundle episodeRequest, OriginReply reply);
    void cancel(long generation);
    void close(String leaseId, long generation);
}
interface OriginReply extends android.os.IInterface {
    void complete(Bundle envelope, ParcelFileDescriptor jsonFd);
}
```

`request = {version:1, leaseId:UUID, generation:long, originCid:int, sourceHistoryKey:String, resumePayload:String, expectedResumeVersion:String, currentCidAtClick:int, configRevision:String}`。

`snapshotFd JSON = {config:原Config副本, siteSettings:相关站偏好, appSettings:只读白名单, originCredentials:可证明归属的源凭据, history:History副本}`；不传所有当前全局偏好；指纹不含明文凭据。工人读取后关闭 FD。

`episodeRequest = {version:1, leaseId, generation, siteKey, vodId, stableFlagKey, episodeUrl, episodeName, playerType}`。下一集仍走同一 worker `play`，不得回主进程 SiteApi。

`envelope = {version:1, leaseId, generation, status, reason, originCid, sourceHistoryKey, configRevision, proxyPort, nodePorts:int[]}`；成功 `jsonFd = {vod:序列化Vod, site:展示字段, flags:完整原线路/剧集, selectedFlagKey, selectedEpisode, result:序列化Result, resumePositionMs, resumeDurationMs}`。Result 可用现有 JSON 序列化；transient namespace 不依赖跨进程保留，由租约重建。

控制消息序列化体 **≤64 KiB/次，不累计**；大的配置／详情／结果统一 FD 流，不塞 Intent/Binder。FD 所指文件不包含可执行指令，限制到任务私有目录并校验 schema。主线程回调先验租约、代次、Activity 存活、Config revision 和原历史 resume version；失效则丢弃、关闭 FD，不启动播放器。

### 4.3 返回值、fallback 与播放归属

```text
READY -> 原详情选定目标集 -> PLAY_READY -> 单入口真实播放 -> 保持租约
UNAVAILABLE(CONFIG_MISSING | CONFIG_LOAD_FAILED | SITE_MISSING |
            DETAIL_FAILED | EPISODE_MISSING) -> 释放原租约 -> 原有当前配置匹配
CANCELLED / STALE -> 丢弃；不 fallback
BLOCKED(ISOLATION_ERROR | ORIGIN_AUTH_REQUIRED | PORT_OWNERSHIP_AMBIGUOUS |
        SNAPSHOT_INVALID | WORKER_DIED | PARSE_FAILED | PLAYER_FAILED) -> 明确报错；不匹配
PLAY_STARTED 后的 URL/网络错误 -> 原有播放器处理；协调器不新增切源
```

不能用 `catch(Throwable) -> UNAVAILABLE`；只有在指定业务操作已经真实失败时才给对应 unavailable。Binder 崩溃、不能初始化运行时、端口冲突等是基础设施错误。原目标集优先使用既有稳定 flag/sourceBindingKey 与 episodeUrl，再用既有集身份规则；无法唯一定位才 EPISODE_MISSING，不能默认第 1 集。

主进程 `OriginPlaybackActivity extends PlaybackActivity`：用 PlayerView/CustomSeekView、现有 startPlayer、现有内核与媒体元数据，展示原站及原目标集；所有 `Result.needParse` 必须已在 worker 消解。原线路／下一集通过租约调用。暂停、seek、退出为阶段 1 必需；双端完整遥控、融合详情、字幕／队列／TMDB 展示等阶段 2 才对齐，不把此页当整体 UI 已完成。

`PlaybackService` 持有租约，Activity 暂时离开不得释放正在消费的代理；真正停播／替换媒体／退出才释放。进度以 `(originCid, sourceHistoryKey)` 写主库；新 `History.saveOriginProgress` 路径保留既有删除墓碑／季进度事务，但不调用当前配置的 TMDB enrich。原 History 无效则停止写入，不凭空建当前 cid 记录。worker 不写主 Room；主进程写后沿用当前监听／刷新路径，因此无跨进程 Room invalidation 需求。

### 4.4 代理与 Node：不能仅返回一个 URL 后杀进程

* worker 不启动 `Server/Nano` 管理全集；`OriginMediaRelay` 只绑定 `127.0.0.1:0`，取得实际端口后 `Proxy.set(workerPort)`；仅代理／媒体路由，无配置管理、远程控制、LAN 暴露。租约 token + 已登记 target ID 才可访问，不接受任意 URL 参数。
* JS/Py `do=js/py`、Jar proxy 使用 worker loader/recent；每个租约站内 player 操作串行。一个源多个站切换期间既有无站名 recent URL 可能歧义，不能并发覆盖；必须把选定原站的代理绑定至本次播放代次。
* `OriginNodeService(:origin_node)` 独立绑定／存活 owner，独立通知 ID（若需要前台）、目录、port 文件和启动代次。NodeRuntime 以明确服务 class / process suffix 路由：原路径默认 NodeService，origin 路径只启停 OriginNodeService；不能用原 hardcoded stop。NodeBoot 收到 worker proxyPort 与独立实际端口；端口回报须校验租约和 Node generation。libnode 在该进程仅启动一次；关闭时只杀自己。
* **所有受原源凭据影响的 HTTP(S) 媒体请求也经 worker relay**，不把需要当前 AuthInterceptor 的网络 URL直接交主播放器。由 worker 新客户端获取；原请求头／Cookie／Authorization留在 worker，主进程只访问 lease loopback。普通不需账号的直链也走同一路径，避免“只做直链”。
* relay 透传状态、Content-Type、Range/206、长度和必要播放头；不向主 CookieJar 传播 Set-Cookie。重定向在 worker 内解析；HLS playlist 的相对/绝对 segment、key、map、子 playlist，DASH BaseURL/模板／init，MPV EDL 子 URL 与 DRM license 均保留原 URL 解析语义并映射已登记 relay target；不做全局字符串 host 替换，不改远端签名 query。若某格式未正确适配，返回 BLOCKED，不能当前源 fallback。
* `file:`／本地 bundle 输入按原绝对路径只读；worker 产生的私有临时文件通过 lease relay 或 FD 导出，主进程不能引用释放后不存在的路径。local proxyURL/headers、普通网络URL、本地URL与解析后嵌套资源分别测试。
* `linkToDeath(owner)` 清理 worker 请求／relay／Node；主端对 service Binder death 标记代次终止、关闭 FD、注销主进程 `PlaybackRouteRegistry.ORIGIN_SESSION` 端口归属、报告失败。close 幂等；先取消解析和 I/O，再停 Node/relay，最后 worker 杀自己。不得 kill 主进程／原 `:node`。系统杀进程后的租约不能“复活”为旧 READY。

## 5. 三个不重叠任务边界

以下是**未来代码授权清单**；本轮只编辑本文件。一个路径只有一个任务所有者。不得顺手全局重构 loader／升级运行时。

### A. 核心实现（阶段 1；可独立执行简报）

新增，目录均为 `app/src/main/java/com/fongmi/android/tv/`：

* `playback/origin/OriginSessionContract.java`：上述 schema／状态与错误 enum。
* `playback/origin/OriginSessionClient.java`：快照、绑定、owner、代次、FD／租约关闭。
* `playback/origin/OriginSessionSnapshot.java`：只读原数据导出、私有库／偏好白名单导入。
* `playback/origin/OriginStorageContext.java`：私有文件／偏好／数据库 Context Adapter。
* `playback/origin/OriginSessionBootstrap.java`：进程识别、最早初始化与禁副作用策略。
* `playback/origin/OriginSessionService.java`：worker 串行运行／Binder死亡／资源租约。
* `playback/origin/OriginHistoryResolver.java`：原源详情、稳定目标集、原 player／parse。
* `playback/origin/OriginMediaRelay.java`：loopback、登记 URL、嵌套资源及凭据隔离。
* `playback/origin/OriginProgressWriter.java`：原 cid/key 写入入口。
* `node/OriginNodeService.java`：原源专属 Node 进程租约。
* `ui/activity/OriginPlaybackActivity.java`：阶段 1 唯一真实播放页。
* `app/src/main/res/layout/activity_origin_playback.xml`：PlayerView、seek、暂停、原站／集名与错误；不替换两端既有布局。

修改（闭合清单）：

* `app/src/main/AndroidManifest.xml`：上述 Activity 与非 exported `:origin`／`:origin_node` 声明。
* `app/src/main/java/com/fongmi/android/tv/App.java`：attach/onCreate 子进程轻量分流，worker 初始化早于 Init/Python/WebView。
* `app/src/main/java/com/fongmi/android/tv/api/config/{BaseConfig,VodConfig}.java`：明确 origin 私有加载模式，禁 failover／主事件／主配置持久副作用；现有主路径默认不变。
* `app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java`：worker 私有库 factory／初始数据；主库 builder 不变。
* `app/src/main/java/com/fongmi/android/tv/bean/History.java`：显式原 cid 保存，保留事务但不借当前 TMDB enrich。
* `app/src/main/java/com/fongmi/android/tv/node/{NodeRuntime,NodeService,NodeBundle,NodeBoot}.java`：服务路由、lease Context、端口／通知／生命周期可注入；默认原服务语义保留。
* `app/src/main/java/com/fongmi/android/tv/api/CatSource.java`：origin Node Adapter，主路径 serve/reuse 不变。
* `app/src/main/java/com/fongmi/android/tv/player/PlaybackRouteRegistry.java`：主进程登记／注销 origin 端口，避免当外部地址二次代理。
* `app/src/main/java/com/fongmi/android/tv/service/PlaybackService.java`：持有媒体消费期间租约、明确停播释放与原进度提交。
* `app/src/main/java/com/fongmi/android/tv/ui/activity/HistoryResumeCoordinator.java`：原源在先、五个业务 reason 触发原有 fallback；阶段 1 导向共用入口。
* `catvod/src/main/java/com/github/catvod/utils/Path.java`：进程私有 root override；默认 Environment 根路径保持不变。

SiteApi、ParseJob、BaseLoader、JS/Py/Jar、Chaquo/QuickJS/nodejs 原生模块本阶段不编辑：在受控 worker 的完整原配置中复用。若实际证据证明其中有绕开私有 Context 的写入，先报阻塞及确切路径，再取得范围授权，不悄悄扩大清单。

核心步骤（按依赖执行，不留实现空白）：

- [ ] A1 建 Contract/StorageContext/Bootstrap/Snapshot 与私有 Room；最早分流 App。只 bind 空 worker，断言主 cid、主偏好、主 DB／Node PID／Proxy.port 未变，worker 目录独立；错误返回 BLOCKED。
- [ ] A2 建 worker Service、FD JSON 往返与 generation/owner death；私有 VodConfig 载入原 Config，业务 reason 只在真实配置／站源检查时生成。
- [ ] A3 注入 OriginNodeService 服务路由／共享 lease 目录和 worker proxyPort，原 `:node` 的启停路径不变；Node READY 必须在原源租约中确认，不能 `reuse` 当前不同 bundle。
- [ ] A4 实现 Resolver：原 detail → 稳定线路／目标集 → 原 playerContent → Source → ParseJob；callback 构造已解析 Result。解析超时属于 PARSE_FAILED，不属于 DETAIL_FAILED。
- [ ] A5 实现 relay 的目标登记、headers、Range、redirect/HLS/DASH/本地资源；登记其主进程路由归属。先用 401 同 host 异账号及带 key 的 HLS 验证“主端不接触源账号”。
- [ ] A6 增 OriginPlaybackActivity，复用 `startPlayer(key,result,false,timeout,metadata,positionMs)`；Service 保持 lease，写原进度；Coordinator 先原后匹配，不接起播后错误。退出断言没有残留 owner/FD/worker/relay/Node。
- [ ] A7 执行第 6 节唯一验收路径，记录真实输出；通过后用任务 guard 原子提交／本地恢复 tag。未过隔离门不标阶段 1 完成。

**可复制简报输入**：实现 A 清单与 A1–A7；Goal 为“阶段 1 单入口从原详情／目标集真实播放且主配置状态不变”；Constraints 为第 1、4 节；Acceptance 为第 6 节。必报文件 diff、进程/端口/目录与配置前后快照、fallback reason、真实首帧／续播位置、退出清理、测试输出、未过门。不开访谈、不做 WebHome／双端细节、不安装依赖、不启动 PC 播放器。

未来核心简报定量限制：首次运行及返工合计 ≤3 次，累计；文件编辑 ≤27 个路径，只允许上方闭合清单（括号路径逐一展开），累计；只读／实现命令和测试运行不限，无累计上限；同一假设失败重试 ≤2 次，累计；检查周期无数量配额，不积累余额；构建仅风险对应的单 flavor/ABI 路径，不能因为验收不足扩成全矩阵。当前文档简报限制仍为编辑 ≤1 文件／构建 ≤0 次，不被这段未来授权覆盖。

### B. WebHome＋双端细节（阶段 2；不与 A 同时改文件）

仅所有：`app/src/main/java/com/fongmi/android/tv/web/{HomeWebBridge,WebHomeVodContract}.java`，`app/src/{mobile,leanback}/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`，以及 `examples/homepages/nostr.html`、`skills/webhome-homepage-builder/assets/demo/nostr.html`。

输入为已验收 OriginSessionClient／会话 schema；扩展现有 options：`historyCid`、`historyKey`/`resumePayload`、`resumeFromHistory:true`；app.history 按现有全局历史设置返回带原 cid/key 的记录，播放时恢复具体 History 后进入 Coordinator。双端 getSite/getHistoryKey、原线路、下一集、进度、融合详情／播放器 UI 消费显式 origin identity，当前模式保持原路径。旧 options 不变；不能通过仅 siteKey/vodId 猜同名跨配置历史。两份 nostr 文件属于该任务，本执行者不编辑。若 B 需要改 A 清单，通过接口返工交 A，不共同编辑。

### C. 测试（无生产路径所有权）

只所有新增／调整：

* `app/src/test/java/com/fongmi/android/tv/playback/origin/OriginSessionContractTest.java`
* `app/src/test/java/com/fongmi/android/tv/playback/origin/OriginFallbackPolicyTest.java`
* `app/src/test/java/com/fongmi/android/tv/playback/origin/OriginRelayTargetTest.java`
* `app/src/androidTest/java/com/fongmi/android/tv/playback/origin/OriginIsolationPlaybackTest.java`
* `app/src/androidTest/assets/origin-isolation/` 中当前 A／原 B 的受控配置、媒体及运行时 fixtures。
* 既有 `app/src/test/java/com/fongmi/android/tv/ui/activity/HistoryResumeCoordinatorTest.java`、`history/GlobalHistoryResumeSourceTest.java`；阶段 2 对应 `web/OriginHistoryBridgeContractTest.java` 新增测试。

必须断言：五个业务 reason 才 fallback；ISOLATION_ERROR/WORKER_DIED/PARSE_FAILED 不 fallback；取消／迟到 FD 被关闭；同 host A/B 账号不串；同名站 Jar/JS/Py/Node 原实例与主实例并存；HLS key/redirect/206 原请求头正确；原 cid 进度且无当前历史新建；起播后故意 404 不匹配；切页面／后台持有及真正停播释放；API 不具 WebView 独立目录或 Node 固定端口时显式阻塞。测试可拒绝核心实现，不能自行修生产代码。

## 6. 单一最短验证路径、阻塞与回滚

**阶段 1 最短路径**：只做一次“当前 A、历史 B、同 host 异账号、带代理 HLS”的端到端 instrumentation 流程；同一测试依次替换 Jar/JS/Py/Node fixtures，复用已安装 APK／设备，不新开构建矩阵。若没有设备，停在“静态／单测通过，真实播放与进程生命周期未验收”，不宣布阶段 1 完成。

未来执行命令（本轮均未执行）：

```powershell
# 正确工作目录 F:\Workspace\webtv2\webhtv；一次捕获输出，不换过滤器重跑。
& 'G:\Git\bin\bash.exe' -lc './gradlew :app:testMobileArm64_v8aDebugUnitTest --tests "*Origin*Test" --tests "*HistoryResumeCoordinatorTest"'
& 'G:\Git\bin\bash.exe' -lc './gradlew :app:connectedMobileArm64_v8aDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.fongmi.android.tv.playback.origin.OriginIsolationPlaybackTest'
```

已读 `app/build.gradle:65–82` 确认 mode=mobile、abi=arm64_v8a，命令使用该 variant；未执行 Gradle。若实际任务注册不一致，只修命令，不扩矩阵。

测试流程：截图／日志记录 A cid/站内容/主偏好摘要/主 DB 修改计数/当前 Node PID/主 Proxy.port → 点 B 历史 → B 原详情／目标集和原账号成功 → 首帧且位置在原 position 的合理 seek 容差内 → A 所有基线不变，只有 B 历史进度更新 → 注入后续 segment 404，无当前源匹配 → 恢复／seek → 退出，origin/Node/FD/relay 清理且原 `:node` 仍活。再单独驱动五个 unavailable 与迟到请求，断言走／不走原匹配的计数。单测应先红后绿，不跳过真实 worker／动态运行时检查。

**必须正面处置的未确定项**：

1. 旧 Android WebView 独立目录能力：实现在 Init 前定向查平台能力和当前 guard；无公开隔离能力且两进程会共目录时是阻塞，不是原源不存在。保持完整目标的最短替代为独立数据目录／UID 的伴随运行宿主，需另授权；不偷跑主 WebView／共享 Cookie。
2. Node bundle 硬编码监听／回调端口：NodeBoot/现有端口注入能否覆盖需双 bundle fixture 验证。只能证明识别端口不等于能迁移固定端口；碰撞不得杀当前 Node。无法适配的 bundle 明确 BLOCKED，最短可行替代是受控 bundle 端口 Adapter，而非切全局。
3. 现有全局插件登录数据无原配置来源：无法可靠倒推旧账号；只导入可证明归属的状态或在私有原源重新认证。不要复制当前账号当原账号。
4. Binder 租约在后台的存活／FGS 合规：先以 visible Activity 绑定与 PlaybackService 持有验证；若仍会被系统回收，定向核验目标 SDK 的合法绑定／前台声明。不得直接把 dataSync 前台服务当无限媒体租约。
5. 未读上游维护讨论／测试不阻止基于规范作架构选择，但禁止将“完整 Android／第三方兼容已证实”写入发布验收。上述不确定项任何一个未过都不得作为 unsupported→unavailable 的通道。

收益为保持用户配置、运行时及账号所有权；代价是额外 JVM/Python/V8 RSS、IPC/冷启动和媒体 relay 维护。相同配置历史保留已有快速路径，跨配置仅按需起 worker；不提前启动第二套运行时，不承诺性能提升。阶段 2 发布门覆盖手机/TV/WebHome 及既有内核／生命周期能力，不以阶段 1 新页取代既有功能。

**回滚**：阶段 1 为独立 guard/commit/tag 的一单位；回滚该单位恢复此前当前源匹配，不执行“切原配置再切回”。无主数据库迁移、无源凭据写回当前库；只清任务私有 lease 目录（先校验绝对路径且已无进程持有）。阶段 2 单独一单位，能单独回滚桥/UI。不发布、不 push；本次文档不 commit/tag，不构建。

## 7. 阶段 1 执行记录（执行者 dev2，2026-11-09）

### 7.1 实际落地文件（生产路径 13 个，闭合清单内；测试文件与 fixtures 由测试任务独占）

新增（8，均在 `app/src/main/java/com/fongmi/android/tv/playback/origin/`）：`OriginSessionContract.java`（VERSION=1、状态/reason 常量、`shouldFallback` 仅五业务 reason、手写 Binder Stub/Proxy、64 KiB 控制消息校验）、`OriginStorageContext.java`（租约私有 files/cache/codeCache/db/SharedPreferences/LayoutInflater，`getApplicationContext()` 不逃逸）、`OriginSessionBootstrap.java`（`:origin`/`:origin_node` 识别、仅 open 后安装私有 Context + `WebView.setDataDirectorySuffix`、API<28 直接 `ISOLATION_ERROR`）、`OriginSessionSnapshot.java`（只读原 Config/Site/白名单偏好快照、FD JSON 上限 32 MiB、resume/config 指纹）、`OriginSessionService.java`（`:origin` 单租约、串行执行、owner death、close 幂等、私有 VodConfig 加载）、`OriginSessionClient.java`（主进程 seam：快照 FD、代次、租约比对、迟到丢弃、close/cancel）、`OriginHistoryResolver.java`（原 detail→稳定 flag/episode→原 playerContent，`needParse` 未消解即 `PARSE_FAILED`）、`OriginMediaRelay.java`（127.0.0.1:0、target 登记与请求头继承、Range/206 透传、redirect 改写、HLS/DASH 嵌套改写、manifest 响应类型重写）。

修改（6）：`App.java`（attach 最早识别 worker，worker 不做主进程初始化；`installOriginContext` 换基类 Context）、`AndroidManifest.xml`（`.playback.origin.OriginSessionService`，`exported=false`、`process=":origin"`）、`api/config/BaseConfig.java`（`originPrivateLoad`：禁主 Server、禁失败切换）、`api/config/VodConfig.java`（origin 私有加载开关与 `onLoadSuccess`/`onConfigFailure` 副作用关闭）、`db/AppDatabase.java`（`installOrigin` 私有库、主库 builder 不变）、`catvod/.../utils/Path.java`（`setRootOverride`，默认 `Environment` 根不变）。

### 7.2 已执行命令与真实输出

* `./gradlew --offline :app:compileMobileArm64_v8aDebugJavaWithJavac`：第 1 次失败（`OriginSessionPort` 需 `asBinder()`），第 2 次失败（`Response` 在 NanoHTTPD 子类与 okhttp 间歧义），第 3、4 次 `BUILD SUCCESSFUL`（52s / 25s）。日志 `app/build/origin-isolation/compile-{1..4}.log`。
* `./gradlew --offline :app:assembleMobileArm64_v8aDebug`：`BUILD SUCCESSFUL in 1m 58s`，产物 `app-mobile-arm64_v8a-debug.apk` 204 870 046 B；日志 `assemble-1.log`。
* 设备基线（emulator-5554，未安装新包、未改数据）：`ps` 仅 `com.silent.android.webhtv`(5305) 与 `.test`(4769)，`settings get global http_proxy` = `null`。日志 `device-baseline-before.log`。
* 测试任务（另一 agent）：`OriginSessionContractTest`/`OriginFallbackPolicyTest` 由红（8/8 缺类）转绿，日志 `app/build/origin-isolation/red-junit.log` 与 `jvm-red/green-junit.log`。

### 7.3 未过项（不得声称阶段 1 完成）

1. A1 设备不变量（主 cid/偏好/DB、`:node` PID、`Proxy.port` 前后不变 + worker 目录独立）尚未在设备上跑通：`am startservice` 无法启动非 exported 服务，必须由 instrumentation 在同一 UID 内 bind；本轮未写测试、未安装新 APK。
2. A3 未实现：`node/OriginNodeService.java` 与 `NodeRuntime/NodeService/NodeBundle/NodeBoot` 路由改动、独立端口/通知/启动代次均未落地，`:origin` 内 Node 仍不可用。
3. A5 仅静态实现：relay 的 HLS/DASH/redirect/Range 只做过编译验证，无真实账号媒体、无 key/map/子 playlist 设备证据。
4. A6 未实现：`ui/activity/OriginPlaybackActivity.java` + `activity_origin_playback.xml`、`PlaybackService` 租约持有、`History` 原 cid/key 写回、`HistoryResumeCoordinator` 先原后匹配、`PlaybackRouteRegistry` 登记均未落地；主播放路径仍走当前配置匹配。
5. `:origin_node` 未在 manifest 声明；phase-1 单变体构建通过不代表运行时隔离成立。

### 7.4 唯一下一步

在设备侧由 instrumentation（同 UID）bind `OriginSessionService` 完成 A1：断言主 cid/偏好/DB 行数/`:node` PID/`Proxy.port` 不变、worker `files/cache/databases` 落在 `no_backup/origin/<leaseId>`；再按 A3→A4→A6 顺序补 Node 路由、真实首帧与进度写回，最终跑第 6 节端到端流程。

## 历史阶段锚点（以文末最新 Recovery anchor 为准）

* 目标与验收：第 1 节完整隔离不变；阶段 1 仅骨架＋单入口真实原源播放，第 6 节通过后才可称阶段 1 完成；WebHome／双端完整目标待阶段 2。
* 基线：`dev2` / `15a893a50cbbbfd99576700865813b57404b2706`；初始干净，无需保护的脏路径。开始 2026-11-09 15:01:48 +08:00；原 10 分钟目标因网络超时／跨运行时核查超出，15:44:59 收敛为只写／静态验收，时间不是终止门。
* 已完成：本地决定性链路与已列一手正文已读；否决 namespace／切全局方案；用户确认两阶段；唯一修改本文件。无代码、构建、安装、PC/REA 操作；未取得设备／运行时验收，不宣称实现完成。
* 未过门：旧 WebView、固定 Node 端口、旧凭据归属、后台服务租约、四种运行时真实播放；不能通过降级匹配规避。
* **唯一下一步**：设备侧由 instrumentation（同 UID）bind `OriginSessionService` 完成 A1 不变量，随后 A3 Node 租约、A5 真机 relay、A6 首帧／进度／退出清理；不重开访谈、不重做已完成广搜。

## 8. 第 2 运行增补（同日）

### 8.1 修正的实质缺陷

1. **App 二次 attach**：删除 `installOriginContext` 内的 `attachBaseContext` 调用（运行时会 “Base context already set”）。改为 worker 持有私有 `OriginStorageContext` 并对 `getFilesDir/getCacheDir/getCodeCacheDir/getNoBackupFilesDir/getDir/getDatabasePath/getSharedPreferences` 显式 delegate；`App.instance/Handler/Gson` 仍在构造期就绪，`Init` 由 Bootstrap 指向私有 Context。
2. **relay 包装语法**：`appendReplacement` 替换的是整个匹配区，原实现丢掉 `URI="…"`/`<BaseURL>` 包装；现按 `matcher.start()..start(1)` 与 `end(1)..end()` 原样拼回，HLS KEY/MAP 与 DASH 标签保持合法。
3. **原源媒体全部落 worker**：`open` 顺序改为私有 Context→`OriginMediaRelay.begin`→（Node 租约就绪点）→`VodConfig.load`；`play` 解析后用 `registerPrefix` 登记并改写为回环 URL，结果 `header` 置空，主进程只访问 127.0.0.1；manifest 响应由 relay 重写，`Location` 以 effectiveUrl 为 base 改写。
4. **needParse 不再一律失败**：worker 内 `ParseJob` 异步解析（45s 上限），失败才是 `PARSE_FAILED`；`OriginHistoryResolver` 只负责 detail/线路/目标集。
5. **本地原配置**：worker 禁主 Server 时 `file://`/本地路径在 `VodConfig.load` 直接私有只读（不经 `Server.getAddress`/Decoder），HTTP 原源仍走原路径；缺口为 file JS/bundle 站点资源，属阶段 1 未覆盖，命中时报 `ISOLATION_ERROR` 而不是伪 `CONFIG_LOAD_FAILED`。

### 8.2 新增 A6 路径（生产 distinctpath 累计 21，闭合清单内）

`ui/activity/OriginPlaybackActivity.java`（复用 PlayerView/CustomSeekView/`startPlayer`/PlayerManager，暂停/seek/退出、`onUnavailable` 仅五 reason 触发既有匹配、退出写进度并释放租约）、`res/layout/activity_origin_playback.xml`、`playback/origin/OriginProgressWriter.java`、`bean/History.saveOriginProgress`（显式 cid/key + 保留删除墓碑/季进度事务，不 enrich、不新建已删行）、`service/PlaybackService` 静态持有/释放原租约、`player/PlaybackRouteRegistry.registerOriginSession`（按 App 侧媒体代理登记，避免二次代理；未新增 enum 常量以不扩 `player/PlaybackRoute.java`）、`HistoryResumeCoordinator`（跨配置先原源单入口，`fallbackToCurrent` 只在五 reason／原配置缺失时走既有匹配）、Manifest 增加 `OriginPlaybackActivity` 与 `.node.OriginNodeService`（`:origin_node`，exported=false，mediaPlayback）。

### 8.3 构建证据

`compileMobileArm64_v8aDebugJavaWithJavac`：第 5 次失败（`PlaybackRoute` switch 因新增 enum 常量不穷尽，已改为不新增常量），第 6 次、第 7 次 `BUILD SUCCESSFUL`（1m15s / 40s）。日志 `app/build/origin-isolation/compile-{5,6,7}.log`。

### 8.4 仍未过（不得声称阶段 1 完成）


1. 设备 A1 不变量、READY→PLAY_READY、真实首帧/续播位置、退出清理全部未在设备取得证据：本轮未安装 APK、未写测试，需测试任务用 instrumentation 在同一 UID 内 bind。
2. A3 Node 未接线：`OriginNodeService` 与 `NodeRuntime.openOrigin/closeOrigin/originPorts` 由 Node 任务实现，`open` 里 Node 租约插入点已留位但未调用（当前猫源 bundle 原配置在 worker 仍会走主 Server 语义，属已知未过项）。
3. A5 的 HLS/DASH/Range/headers/redirect 全部只有编译与 JVM（测试任务）证据，无真实账号媒体证据。
4. `PlaybackService` 只做租约持有/释放，未做“暂停/seek 不释放、真正停播才释放”的细分；`OriginProgressWriter` 未在真实播放中验证。


## 9. 第 3/3 运行（最终接线与设备验收准备）

### 9.1 本轮修掉的实质缺陷（均由实证定位）

1. **App 私有 Context 逃逸**：`storage()` 在 `originContext == null` 时返回 `this`，主进程与 worker 都会递归到 `getFilesDir/getCacheDir`。改为委托 `getBaseContext()`，并补齐 `openFileInput/openFileOutput/deleteFile/fileList/getDataDir/getApplicationInfo/getExternal*Dirs` 与 `getApplicationContext`（worker 返回私有 Context）。
2. **ParseJob 结果头丢失**：`Result.getHeader()` 在字段为 null 时每次返回新 HashMap，旧 `getHeader().putAll(...)` 全部丢失原 Authorization/Cookie；改为先合并再 `setHeader(merged)` 写回字段。
3. **DASH 模板只取 basename**：`$RepresentationID$/chunk-$Number$.m4s` 会 404；改为登记完整模板并按目录段/文件名段/query 段逐段校验展开（`substitute`），属性值保留模板形态由播放器展开。
4. **成功 JSON 携带源账号**：`Vod.site` 非 transient、`Result.header/list` 会夹带站点头；改为白名单输出（删 `site`、`header`、`list`，`jx=0`），`edl://` 资源、alt URL、DRM license 全部改写到租约回环。
5. **换集季集身份错位**：换集只改 `episodeUrl/vodRemarks/flag`，季 snapshot 按 `getTmdbEpisodeNumber()` 落库会把 E4 位置写成 E3；改为 `history.setTmdbEpisodePosition(selectedEpisode)`。
6. **后台误释放租约**：`PlaybackService.holdOriginLease` 只有静态赋值，Activity `onDestroy` 的 `releaseService(owner=true, keepAlive=false)` 会走 `shutdown`；改为原租约建立 keepAlive/consumer 语义，销毁页面只 detach callback，真正退出才 release。
7. **布局资源**：`activity_origin_playback.xml` 引用不存在的 `play_play/play_pause`（真实资源链接失败），改为明确文本；新增 `next/lines/close` 与 `app:use_controller`。
8. **新租约早于旧 worker 死亡**：新增 `OriginSessionClient.closeAfterDeath`，等旧 Binder 真正死亡后才允许续租，超时报 `ISOLATION_ERROR`，不复用旧进程。

### 9.2 构建与安装（真实输出）

* `./gradlew --offline :app:assembleMobileArm64_v8aDebug :app:assembleMobileArm64_v8aDebugAndroidTest :app:testMobileArm64_v8aDebugUnitTest --tests '*Origin*Test'` → **BUILD SUCCESSFUL in 29s**（`app/build/origin-isolation/final-build-3.log`）；此前 `final-build-1/2.log` 分别因布局未定义字符串、`startsWith("<MPD>")` 旧断言各失败一次。
* 定向单测：`OriginFallbackPolicyTest` 4/0、`OriginRelayTargetTest` 5/0、`OriginSessionContractTest` 4/0（`app/build/test-results/testMobileArm64_v8aDebugUnitTest/*.xml`）。
* 产物：主 `app-mobile-arm64_v8a-debug.apk` 204,870,299B SHA256 `5F13F257232A561293E36FDA7C43129F128F1B6953F464FC6D98F818C78CD2AE`；test `app-mobile-arm64_v8a-debug-androidTest.apk` 1,984,110B SHA256 `B3AD4432CCE2F116A9C8252D109C0F4C6A329D32018C68C9DD7970C5F907558F`。
* 设备（emulator-5554，SDK 28，abilist 含 arm64-v8a）：两包 `adb install -r` 均 **Success**，数据保留（`files/` 原目录仍在），`no_backup/` 尚未生成（未跑过租约）。日志 `install-main.log`/`install-test.log`/`postinstall-device.log`。

### 9.3 设备验收状态：未完成

一次误触发的 `am instrument` 只推进到第 1 个用例开始（`instrument-1.log` 8 行、无 RESULT），随即 `job_kill` + `force-stop` 止损；设备无残留进程、`no_backup` 仍空、数据未清。该次结果**不计**任何设备证据，改由调度者在 R1 验收内执行 8 个用例。因此下面各条仍全部未验证：真实首帧、续播位置、READY→PLAY_READY 全链路、主 cid/偏好/DB/`:node` PID/`Proxy.port` 前后不变量、原 cid/key 进度写回、退出清理与 owner death。阶段 1 **不得**称完成。

### 9.4 已知未覆盖

1. file JS/bundle 站点资源在 worker 无主 Server 下未实现（命中 `ISOLATION_ERROR`，不伪装 unavailable），阶段 1 只用 HTTP 原源验收。
2. `PlaybackService` 只做到“暂停/seek 不释放、真正退出释放”，未在真实会话验证；原进度 ticker 5s 亦未设备验证。
3. Node 私有租约冷启动是否复用既有 native 库（不触发下载/执行）由调度者核验，本轮未断言。

### 9.5 唯一下一步

调度者跑 `OriginIsolationPlaybackTest` 8 用例（已装两包），把 `am instrument` 完整日志交回；据真实结果补 9.3 的通过/未过项，未过项不得改写为完成。

## 10. 调度者接手后的实际状态

### 用户范围与兼容决定

- 仍按两阶段推进；一期先完成独立原源进程骨架与单一真实原源播放入口，手机／TV／WebHome 完整细化仍是二期目标，不冒充已完成。
- 用户要求先评估低系统机制，若现边界无法可靠支持则采用方案 1。现 APK 的公开 WebView 数据目录机制从 API 28 可用；另 UID 伴随宿主属于后续独立架构。现一期 API<28 明示并保持既有当前源匹配，API>=28 使用原源隔离；不以平台不支持伪造原源业务不可用。
- 模拟器未安装 Node 运行库，禁止为验收隐式下载执行。一期最短设备证据先选 Jar 完整流程；默认测试仍保留 jar/js/py/node 四类型，显式 originRuntime 参数仅选择要实际报告的单类型，不削弱成功／回退／清理断言。

### 审查与返工路由

核心简报累计 3 次、测试简报累计 3 次仍未通过；按调度 R8 仅对这些任务接手，Node 代码与之后任务不继承该例外。独立审查的 P1 均进入明确修正：解析结果非空头真实赋值；subtitle URL 同原头经 worker relay；SpiderNull 初始化预检及 HTTP 认证状态；file URI 正确解码；DASH 相对模板／原 base／原始 query 的实际接线；原季快照与版本比较；closeAfterDeath 真正调用并保存失败回调；服务按租约专属播放 key 判真实媒体替换后先释放而不写错历史；保存／恢复原 keepAlive；复用及起播更新既有导航 key。另保留映射前媒体 MIME，避免回环 token 路径丢扩展造成容器误判。第 2 次只读复核进行中，修正不等于验收通过。

### 已取得的实际证据与设备恢复

- 新增真实 registered DASH target 测试，初跑 50 项仅模板用例失败（unknown template target），日志 dispatcher-template-red-junit.log；修正后统一构建 dispatcher-final-build.log 实际 BUILD SUCCESSFUL，主/test 与聚焦单测通过（3m08s）。媒体 MIME、旧系统策略、导航 key、单类型选择是之后的相关改动，正在 dispatcher-final-build-2.log 重新核验。
- 首次实际设备 relay 单方法结束为 FAILURES 1/1：tearDown 捕获配置取得失败，设备当前指向已停的夹具端口 40509；不能视为 relay 或阶段通过。
- 已按用户选择备份并恢复配置 #4，精确移除中断夹具配置 #35；进一步确认并清除同次夹具 #36 及其唯一 origin_fixture@@@origin-vod@@@36 / bob:fixture 历史。其他真实配置／历史不改。原库与偏好备份仅保留在 ignored app/build/origin-isolation/recovery-*.tar，不发布、不上传；不要在报告打印凭据。
- 尚无原源 READY→PLAY_READY→AVC 首帧／续播、暂停／seek／后台／退出清理的有效设备通过证据；未 commit/tag，不宣布一期或完整目标完成。

## Recovery anchor（最新）

- 目标：一期在不切／污染当前配置前提下，从原源原目标集真实续播；失败分类、版本、FD、进度归属与租约清理均须过门；二期三端完整目标保留。
- 基线：dev2 / 15a893a50cbbbfd99576700865813b57404b2706；守卫 H-ORIGIN-RESUME-S1 active，原先脏代码 0，任务文档已显式 adopt；当前生产 26 路径，主 NodeService 未改，测试 6 Java／16 asset。未提交。
- 当前：调度者已修上述 P1，最后相关构建 pwsh-a78d31e5bf084304 / dispatcher-final-build-2.log 进行中；只读复核 6a7d7678 第2/3轮。设备已恢复真实配置 #4；Node 库缺失，先用 originRuntime=jar 完整验收，不隐式下载。
- 未过门：最后相关构建／复核、新 APK 安装后真实 Jar 流程与清理；JS/Py/Node 多类型、旧系统真实设备、file JS/bundle 等未验／未做，不用 unsupported→unavailable 规避。
- 唯一下一步：收取 pwsh-a78d31e5bf084304 的完整构建结论；失败仅定位直接原因，成功后进入已计划的保数据安装与设备验收阶段。
## 11. 一期最终实施与真机验收（调度者执行）

### 11.1 本轮补修的实质缺陷

1. **主偏好隐私**：验收断言曾把完整 `Prefers` 写进失败信息（含 API Key）。改为只比较 SHA-256 摘要并只报告差异键名；已删除含该转储的临时日志。
2. **广告/播放统计误判**：`ad_block_stats`、`perf_exo_decoder_runtime_v1` 是主进程任何一次播放都会写的自有统计，列入显式白名单；其余偏好仍逐字比对。
3. **Activity 未挂播放器**：子类 `initView` 未调用基类实现，播放服务从未绑定。改为先解析 history 再 `super.initView`，并在服务先连上时补一次 `openOrigin()`。
4. **播放中旧租约复用**：复用条件改为 `matchesHistory`（payload/季身份/配置修订/当前 cid/进度版本），不同身份走 `closeAfterDeath` 再开新租约。
5. **代理出口**：proxy 的 DASH 清单也改写；非 2xx 清单不再吞掉 `Location`；相对 `Location` 统一走 `mapped` 登记；主请求头不再覆盖原源登记头（源头最终优先）；SmoothStreaming 明确 `BLOCKED`。
6. **解析头/错误分类**：非空原头先清再赋；`Failure` 原样重抛，401/403 保持 `ORIGIN_AUTH_REQUIRED` 而非降级成 `DETAIL_FAILED`。
7. **file: 双编码**：捕获端与加载端都用 `File(URI)`，中文/空格路径不再误报 `CONFIG_LOAD_FAILED`。
8. **旧系统**：API<28 明示“暂不支持原源隔离，保留原有当前源匹配”，符合用户本轮决定。
9. **测试基建**：`ContextWrapper` 覆写 `getApplicationContext()`（否则注入绑定被绕过）、异步 open/FD 关闭改为有界等待、租约结束以 worker 进程消失为准、teardown 不再强制联网重载用户远程配置。

### 11.2 真机证据（emulator-5554，SDK 28，arm64-v8a）

- 逐方法隔离运行，8/8 通过：`OK (1 test)` × 8（`app/build/origin-isolation/iso*.log`、`iso3-lateclose.log`）。
- 原源真实播放：Jar / JS / Python 三类分别 `OK (1 test)`；覆盖 READY→PLAY_READY→原第 2 集→经租约回环的 HLS(key/map/相对段/Range206)→真实 AVC 首帧与续播位置→主配置/站点/历史/偏好不变量→worker 进程退出与回环不可达→原 cid 进度写回→注入段 404 不匹配当前源。
- Node：设备无 `libnode` 缓存，未为测试隐式下载执行，**未验收**，不记为通过。
- 定向单测：`OriginFallbackPolicyTest` 4、`OriginSessionContractTest` 4、`OriginRelayTargetTest` 6、`HistoryResumeCoordinatorTest` 4、`GlobalHistoryResumeSourceTest` 32，共 50 项 0 失败（`dispatcher-unit-final.log`）。

### 11.3 设备数据恢复

中断运行留下的夹具已按名称前缀精确清理（16 条夹具配置与 5 条夹具历史），选择指针恢复到用户确认的 #4；其余 26 条配置、106 条真实历史保持不动。备份仍留在 ignored 的 `app/build/origin-isolation/recovery-*.tar`。

### 11.4 仍未覆盖

1. Node 租约的真实双 bundle 并存（缺运行库，未下载）。
2. 阶段 2：WebHome 历史身份透传、手机/TV 完整入口与既有播放页融合。
3. 旧系统（API<28）仅有策略级保留，无设备验证。
4. 起播后 URL 失败明确不接管，未做跨配置回退。