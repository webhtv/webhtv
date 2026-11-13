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
## 12. 版本门槛下调（用户要求：手机版/电视版低版本也能用）

### 12.1 结论

原实现用“系统版本 < 28 就拒绝原源”做门槛。实际情况是：`WebView.setDataDirectorySuffix()` 从 API 28 才有，而 WebView 只被 `ParseJob`（嗅探/解析）使用；直链原源根本不需要 WebView。因此门槛改为**能力**而不是版本：

- `OriginSessionBootstrap.install` 不再因低版本抛错：私有文件/偏好/数据库/代理/Node 在 minSdk 24 起照常隔离；`canIsolateWebView()` 只在能设置独立 WebView 目录时为 true。
- `OriginSessionService` 在 `result.needParse()` 分支前检查 `canIsolateWebView()`：低版本遇到“需要 WebView 嗅探”的原源，明确 `ISOLATION_ERROR`（BLOCKED），不伪装成原源不可用去匹配当前源。
- `HistoryResumeCoordinator` 移除版本分支：任何版本都先尝试原源。
- `OriginSessionBootstrap.processName()` 统一 API 28+ 的 `Application.getProcessName()` 与低版本 `/proc/self/cmdline` 兜底；`OriginNodeService.isOwnProcess()` 与 WebView 目录 suffix 都改用它，避免低版本 Node 进程无法自毁。
- 依据 `minSdk = 24`、`coreLibraryDesugaring(desugar_jdk_libs_nio 2.1.5)`：`Map.of`/`Stream.toList`/`java.nio.file` 在基线已可用（现有代码 553 处 `Map.of`、47 处 `toList()`），无需为本功能抬高门槛。

### 12.2 验证

- `assembleMobileArm64_v8aDebug` + `assembleMobileArm64_v8aDebugAndroidTest` + 定向单测：BUILD SUCCESSFUL；50 项单测 0 失败（`lowapi-build-2.log`）。
- emulator-5554（SDK 28）Jar 原源完整流程复跑：`OK (1 test)`（`lowapi-device-jar.log`），确认门槛下调没有回归。
- **未在 API 24–27 真机/模拟器验证**：本机只有 API 28 设备，且不为验收下载系统镜像。低版本结论目前是代码级（无 28+ API 调用、进程名兜底、WebView 分支受控）。

### 12.3 仍未覆盖

1. 需要 WebView 嗅探的原源在 API < 28 明确不支持（BLOCKED），这是平台限制而非本次疏漏。
2. WebHome 与二期三端融合仍未做（用户允许先忽略 WebHome）。
3. Node 租约真实双 bundle 并存仍未验证（缺 libnode，不隐式下载）。
## 13. 阶段 2 第一步：跨源历史改走正常播放页入口（删除临时页面）

### 13.1 改动

* 删除 `app/src/main/java/.../ui/activity/OriginPlaybackActivity.java` 与 `app/src/main/res/layout/activity_origin_playback.xml`，并从 `AndroidManifest.xml` 移除该 activity 声明（自带的播放/暂停/下一集/线路/退出按钮与"正在匹配…"文案一并消失）。
* 新增 `app/src/main/java/com/fongmi/android/tv/playback/origin/OriginHistoryPlayback.java`：原源会话内核（intent extras `origin_history_cid`/`origin_history_key`/`origin_history_payload`、按需 `open`、PlayReady 缓存 `Vod`/`Result`、进度写回、释放租约）。intent 只带原 cid／historyKey／`HistoryResumePayload.encode(history)`，不塞大 JSON。
* 两个 flavor 的 `VideoActivity` 新增静态入口：`public static Intent originHistoryIntent(Context context, History origin)` 与 `public static void startFromOriginHistory(Activity activity, History origin)`；`originHistoryIntent` 复用既有 `putIntentPlaybackSelection(...)`（原 flag／stableFlagKey／集名／集 URL）与既有 `EXTRA_RESUME_*`，不引入新播放控件。
* 原源模式下逐点复用既有 UI 与既有 `startPlayer`：`getDetail(boolean)` 改为 `mOrigin.requestDetail()`（详情来自 worker 的 `PlayReady.vod`，不调用当前 `VodConfig` 的 SiteApi）；`getPlayer(Flag, Episode)` 只把 `mViewModel.playerContent(...)` 换成 `mOrigin.requestPlay(flag, episode)`，其后的标题/海报/歌词/历史更新/进度条等全部照旧；`setPlayer(Result)`、`startPlayer(...)`、选集器、手势、字幕、倍速、弹幕全部原样复用。
* 身份与站点：`getHistoryKey()` 在原源模式返回 `mOrigin.historyKey()`（原 cid 的组合键），`getSite()` 返回原源 `Site`（原 key + 原站名），因此 `History.findPlayback`／`Keep`／内核偏好都按原源身份走，不落当前配置；`getPlaybackKey()` 返回 `client.playbackKey()`，`startPlayer` 的 key 参数改用它（普通路径下二者等值，逐字不变）。
* 进度与租约：`saveHistory(boolean)`/`syncHistory()` 在原源模式改走 `OriginProgressWriter.write(mHistory, position, duration)` + `client.progressCommitted()`，并复用 `PlaybackService.holdOriginLease(client, history)`（服务侧 5s ticker 同类写回）与 `releaseOriginLease()`；暂停/seek 不释放，只有真正退出（`saveHistory(true)` 且 `isFinishing() && !isChangingConfigurations()`）或服务停播/替换媒体才释放。
* fallback 判定位置：`OriginSessionClient.dispatch` 已用 `OriginSessionContract.shouldFallback(reason)` 且要求 `!playbackStarted`，只有 5 个业务 reason 会进 `onUnavailable`；`OriginHistoryPlayback` 的 `onUnavailable` 回调 → `VideoActivity` 监听器 → `HistoryResumeCoordinator.fallbackToCurrent(...)`。`onBlocked`（BLOCKED／worker 死亡／解析失败）只提示错误（`Notify` + `onError` + 空态），不回落。
* `HistoryResumeCoordinator.open` 跨源分支改为 `VideoActivity.startFromOriginHistory(activity, history)`，保留"原配置不存在 → fallbackToCurrent"，无任何版本号判断。既有当前源历史分支（`startFromHistory` 前的 `NovelRouter`/`shouldOpenLegacyTmdbDetail`/`startDirect`）与 `forPlaybackKey(getHistoryKey(), VodConfig.getCid())` 字面均未改动。
* 设备用例：`OriginIsolationPlaybackTest.exerciseActivity` 改由 `VideoActivity.originHistoryIntent(...)` 启动正常入口，删除 `R.id.message`/`pause` 临时控件断言，改为收集窗口文本断言外壳显示原站（"bob"）与原第 2 集，暂停改用播放器本身并断言租约不释放；其余断言（真实 AVC 首帧、续播位置、主配置/DB/偏好不变、原 cid 进度写回、worker 退出后回环不可达、注入段 404 不触发当前源匹配）保留。

### 13.2 验证（本轮实际输出）

* 编译：`:app:assembleMobileArm64_v8aDebug :app:assembleLeanbackArm64_v8aDebug :app:assembleMobileArm64_v8aDebugAndroidTest` → `BUILD SUCCESSFUL in 8m 55s`（`app/build/origin-isolation/build-1.log`）。
* 定向单测：`OriginFallbackPolicyTest` 4、`OriginSessionContractTest` 4、`OriginRelayTargetTest` 6、`HistoryResumeCoordinatorTest` 4、`GlobalHistoryResumeSourceTest` 32 = 50 项 0 失败（`unit-1.log` + `app/build/test-results/...`）。其中 `GlobalHistoryResumeSourceTest` 的 `OriginPlaybackActivity.start(activity, history)` 断言属于"被本轮删除的入口"，经父协调者批准只改该 1 行为 `VideoActivity.startFromOriginHistory(activity, history)`，断言语义（原源尝试先于 fallback、launch 后 return）未放宽。
* 设备（emulator-5554 / SDK 28，`am instrument -e originRuntime jar`）：首次 7/8 通过，唯一失败为 `main default preferences changed outside the playback-owned keys, keys=[audio_playlist_active, perf_exo_decoder_runtime_v1, site_health]`——失败点在"外壳已真实播放之后"的偏好摘要，属断言口径过窄：`site_health`（`SiteHealthStore.recordParse/recordPlay`，按 site key 记原站协议健康度）与 `audio_playlist_active`（`AudioPlaylistStore.setActive`）是**任何正常播放页**都会写的运行时统计，与"切不切配置"无关。因此只把这两个键加入 `PLAYBACK_OWNED_KEYS`（注释写明调用点依据），账号/配置/解析器/插件等其它偏好仍逐字比对，未放宽其它断言。
* 复跑：`OK (8 tests)`（`device-2.log`）。logcat 实例证据：`ORIGIN_FIRST_FRAME jar position=2494 frameCallbacks=3`；`OriginProgress: cid=156 key=origin_fixture@@@origin-vod@@@156 season=0 episode=0 position=2778 duration=32268`（原 cid 写回）。
* 普通当前源历史未回归：`GlobalHistoryResumeSourceTest` 32 项全绿，其中 `bothPlayersAcceptExplicitCrossConfigResumeHistory`、`resolvedPlaybackRebindsHistoryToTargetMetadata`、`explicitResumeDoesNotDeleteSourceHistoryInIncognitoMode` 直接约束既有当前源路径字面与行为。

### 13.3 仍未覆盖／风险

1. Node 运行时租约仍缺 `libnode`，未验证（不隐式下载）。
2. API 24–27 未验证（本机只有 API 28）。
3. TMDB 刮削在跨源历史下仍走全局 TMDB 配置（与当前源历史一致）；未做"原配置专属 TMDB"语义。
4. 原源模式下旋转/重建依赖 `PlaybackService.originLease()` + `matchesHistory` 复用同一租约；若期间进度被别处改写导致 `matchesHistory` 为 false，会新开租约并得到 `ISOLATION_ERROR`（BLOCKED 明确报错），未做自动重试。
5. 电视端（leanback）只做编译与源级断言验证，**未跑 TV 设备用例**（本轮设备仅手机 APK）。
6. 本次文档未提交、未建 tag（守卫生成由父处理）。
## 14. 返工 2：起播前失败继续全局历史续播，且原因可见

### 14.1 用户实测问题与根因

1. 跨源历史原源匹配成功但取流/解析/worker 失败时，旧实现只 `Notify.show` 一句然后停在原页（旧的 `OriginPlaybackActivity` 还会直接退出），用户既看不清原因也没有后续。
2. 更关键的是 `HistoryResumeCoordinator` 的既有链路被页面寿命打断：`resolveAuto` 完成后用 `isCurrent(...)→!activity.isFinishing()` 保护，旧实现先 `finish()` 再调 `fallbackToCurrent`，异步结果回来时页面已在结束中，自动匹配被静默丢弃。只有 `search` 模式因为同步 `startActivity` 才看起来"能用"。

### 14.2 改动

* `OriginSessionContract.shouldResumeGlobally(resultDelivered, status, reason)`（新增纯函数，纯 JVM 可测）：`UNAVAILABLE` 仍只认五个业务 reason；`BLOCKED` 只在**尚未把任何 Result 交给播放器**时转全局续播；`resultDelivered=true` 时任何状态都不回落（既有"起播后不接管"契约不变）。
* `OriginHistoryPlayback`：新增 `resultDelivered` 边界，唯一置位点是 `deliverResult(...)`（真正调用 `listener.onOriginResult` 之前）。`onUnavailable`/`onBlocked`/`consume(...)` 的"取流结果不可直接播放 / `hasMsg` / 详情选集无效 / 快照异常"统一走 `handleFailure(status, reason, detail)`：按上面的策略决定 `onOriginFallback(history, reason)` 还是 `onOriginError(reason)`，并先 `release()` 释放租约。`markPlaybackStarted()`/`holdOriginLease(...)` 从 `apply(...)` 移到 `deliverResult(...)`，避免"仅解析了详情就当成已起播"。
* `Listener.onOriginFallback(History history, String reason)`（签名变更，内部 API）：两个 flavor 的 `VideoActivity` 实现先 `showOriginError("原源播放失败：<reason>，将按全局历史续播设置匹配当前源")`，可见后 `App.post(... , 2000)` 再调 `HistoryResumeCoordinator.fallbackToCurrent(activity, history, onLaunched)`。`onLaunched` 里才 `markPlaybackExiting()` + `finish()`。
* 原源失败不再走原生 `onError(...)`（它会 `startFlow()→checkFlag()→nextFlag()`，在无线路时 `FlagAdapter.get` 越界）。`VideoActivity.onError` 增加 `if (mOrigin != null)` 早退到纯展示的 `showOriginError(...)`。
* `HistoryResumeCoordinator`：`fallbackToCurrent(activity, history, onLaunching)` 新增重载；`global_history_mode = OFF` 时不再静默（提示"全局历史续播已关闭，未进行当前源匹配"并留在原页）；`AUTO` 的 `resolveAuto(...)` 透传 `onLaunching`，仅在**目标正常播放页真的被启动**（含 `NovelRouter` 回调、`showSearchFallback` 里用户点"搜索"）后才回调；`SEARCH` 在 `directFromHistory` 后立即回调。`VideoActivity.onStop` 只在 `mOriginFallbackPending` 时收尾，避免异步等待期间页面被提前回收。
* 设备用例新增 `testPrePlayerFailureUsesGlobalAutoSearchAndOff`：受控失败由夹具 `/player` 对 bob 返回不可播 scheme（真实 worker 的 `relayResult` 因此抛 `ISOLATION_ERROR`，`played=false` → `BLOCKED`），逐个设置 `AUTO`/`SEARCH`/`OFF` 三种全局设置，断言：失败文案在原正常外壳上可见且带 `ISOLATION_ERROR`、匹配开始前不 finish、`AUTO` 真的搜索到当前源并启动目标播放页、`SEARCH` 启动带 `historyResumeCid/Key/TargetCid` 的搜索页、`OFF` 既不搜索也不离开原页并提示已关闭、主要状态不变量（配置/站点/非原源历史/偏好/`:node`/`Proxy.port`）保持不变。
* 单测新增 `OriginPrePlaybackFallbackTest`（纯 JVM，直接调 `shouldResumeGlobally`）：起播前 BLOCKED 各 reason 必须续播、已交付 Result 后任何状态都不回落、UNAVAILABLE 仍恰为五个业务 reason、READY/PLAY_READY/CANCELLED/STALE 与空值不得触发。

### 14.3 验证（本轮实际输出）

* 先红后绿：新策略测试在实现前 `4 tests completed, 4 failed`（`s3-unit-red.log`，缺失 `shouldResumeGlobally`），实现后 `0 failures`。
* 定向单测：`OriginFallbackPolicyTest` 4、`OriginPrePlaybackFallbackTest` 4、`GlobalHistoryResumeSourceTest` 32、`HistoryResumeCoordinatorTest` 4 = 44 项 0 失败（`s3-unit-1.log`）。
* 编译：`assembleMobileArm64_v8aDebug` + `assembleLeanbackArm64_v8aDebug` + `assembleMobileArm64_v8aDebugAndroidTest` → `BUILD SUCCESSFUL`（`s3-build-1.log`）；修复后重编 `s3-build-2.log`/`s3-build-3.log` 同样 SUCCESSFUL。
* 设备 emulator-5554：`OK (9 tests)`（`s3-device-5.log`）。新用例单跑 `OK (1 test)`，实际打印 `ORIGIN_PREPLAYER_FALLBACK_OK mode=1 searches=1`、`mode=2 searches=1`、`mode=0 searches=0`（`s3-device-6.log` + logcat），即 AUTO/SEARCH 都真的接上了全局续播，OFF 不搜索。
* 既有真实播放断言仍通过：`ORIGIN_FIRST_FRAME jar position=1653 frameCallbacks=2`、`ORIGIN_RUNTIME_OK jar cid=197`、原 cid 进度写回 `OriginProgress: cid=197 key=origin_fixture@@@origin-vod@@@197 position=2500 duration=32268`。
* 过程记录（真实排障，不是通过）：两次"Process crashed"均为 `adb install -r -t <通配符匹配到两个 apk>` 导致主包未装上、device 仍在跑旧 dex，栈 `FlagAdapter.get IndexOutOfBoundsException`；改为显式 debug APK 路径安装后新守护生效，用例通过。

### 14.4 仍未覆盖

1. `AUTO` 模式若全局搜索也匹配不到目标，会给既有 `history_source_not_found` 对话框（带"去搜索"），未新增设备断言。
2. 阅读器（小说/漫画）路由下的起播前失败只按代码路径复用同一 `onLaunching`，未做设备验证。
3. 起播后（已交付 Result）的失败仍完全不接管——本轮只做了策略级断言，未新增设备注入。
4. Node 运行时租约、API 24–27、TV 真机仍与 §13.3 相同，未验证。
## 15. 返工 3：炫彩/独立 TMDB 详情模式下的跨源历史进详情页

### 15.1 根因

当前源历史在 `VideoActivity.startFromHistory` 先用 `shouldOpenLegacyTmdbDetail(key,id)` 判断是否进 `TmdbDetailActivity`；跨源历史走 `HistoryResumeCoordinator` 后直接调 `VideoActivity.startFromOriginHistory`，绕过了这一判断，于是"详情页模式=炫彩详情"时也直接进播放器。

### 15.2 改动

* 新增纯函数 `playback/origin/OriginHistoryRoute.describe(tmdbDetailModeConfigured, standaloneTmdbMode, tmdbId, mediaType)`：有 TMDB 身份且命中独立 TMDB 详情模式 → `tmdb_detail`；否则返回带原因的退化码 `direct:detail_mode_not_tmdb` / `direct:history_without_tmdb_identity`（JVM 可直接断言）。
* `HistoryResumeCoordinator.routeOriginHistory(...)`：与当前源历史同一判据分流到 `TmdbDetailActivity.startFromOriginHistory` 或 `VideoActivity.startFromOriginHistory`；`SpiderDebug.log("origin-route", ...)` 记录每次分流与退化原因；仅"TMDB 模式但历史无 TMDB 身份"这一用户可见意外才 `Notify.show`（字面提示），非 TMDB 模式按用户设置静默直放（与当前源历史一致）。`open()` 跨源分支改为调用它。
* `TmdbDetailActivity` 原源模式（改动点）：`startFromOriginHistory`/`originHistoryIntent`（入口）、`origin()`/`isOriginSession()`（会话内核）、`loadOriginContent()`（**不调用 SiteApi.detailContent**，只按历史 tmdbId/mediaType 补 TMDB 元数据后 `applyLoaded`）、`initHistory()`（原源历史取自会话内核，不再走 intent payload 复原）、`playInline(...)`（改用 `mOrigin.requestPlay`）、`onPlay()`（原源进入内联全屏播放）、`saveInlineHistory()`/`syncInlineHistory()`/`onDestroy()`（`mOrigin.writeProgress` 按原 cid/key 写回，退出释放租约）、`playbackCid`（`history.setCid` 三处改为原配置 cid）、`getHistoryKey()`/`getPlaybackKey()`/`getCurrentSite()`（原源身份）、`bindSeasonSourceRoutes()`（原源模式直接返回，线路行不混入当前配置站源 chip）、`changeSource()`/`tryAutoChangeSource()`（原源模式禁止换到当前配置站源）、`onError()`（原源失败不走原生换源，改 `showOriginError`）。
* 起播前失败沿用上一轮策略：先提示原因，2s 后 `HistoryResumeCoordinator.fallbackToCurrent(..., onLaunched)`；已起播后失败不接管。

### 15.3 验证

* 单测：`OriginHistoryRouteTest` 4 项全绿（TMDB 模式选详情页 / 非 TMDB 模式选直放 / 无身份退化 / 退化码可区分）。
* 编译：`assembleMobileArm64_v8aDebug`、`assembleLeanbackArm64_v8aDebug`、`assembleMobileArm64_v8aDebugAndroidTest` 均 `BUILD SUCCESSFUL`（`s4-build-3.log`）。
* 设备 emulator-5554：新增 `testTmdbDetailModeRoutesCrossSourceHistoryIntoTmdbDetailOnOriginLines`，断言 `Setting.putDetailOpenMode(DETAIL_OPEN_ENHANCED)` 下跨源历史打开的是 `TmdbDetailActivity`、外壳显示原配置 bob 的第 2 集且不含当前配置 alice 的站源 chip、点播放后经原源 relay 真实起播（首帧 + 非黑截图）、原 cid 进度写回、无当前配置记录、退出释放 worker。全量 `OK (10 tests)`（`s4-device-6.log`），logcat `ORIGIN_TMDB_DETAIL_OK cid=265 saved=1200`。
* 过程记录：两次失败均按证据修正——(a) 用例自身改设置导致偏好摘要失败 → 基线改在设置之后取；(b) 全量运行时原源详情页空白，定位到 `initHistory()` 的 intent payload 复原在季进度快照缺失时返回 null，改为原源模式直接用会话内核历史（产品修复，非放宽断言）。另将 `cache_mgmt_inventory_duration_ms`（`CacheCenter.java:59` 的后台缓存盘点耗时）加入运行时统计白名单，理由同 §13.2 的 `site_health`/`audio_playlist_active`：任何详情页/播放页都会写，与切不切配置无关。

### 15.4 仍未覆盖

1. `app/src/test/java/com/fongmi/android/tv/history/GlobalHistoryResumeSourceTest.java:21` 的源码文本断言仍指向 `VideoActivity.startFromOriginHistory`，本轮 `open()` 改调 `routeOriginHistory` 后该断言失败（1 failed / 58）。语义需改为搜索 `routeOriginHistory(activity, history)`；该文件不在本轮 scope，已报父等待授权，未自行修改。
2. TMDB 元数据未在设备上联网验证（无可用 TMDB 凭据），只验证了 tmdbId/mediaType 传入且失败时不影响原源线路渲染。
3. 阅读器（小说/漫画）路由下的原源详情/起播未验证；TV（leanback）只做编译验证。
4. 原源模式旋转重建复用租约的行为仍与 §13.3-4 相同，未新增验证。
## 16. 返工 4（quick-fix）：跨源历史与标准历史同构分流，删除"必须有 TMDB 身份"前置

### 16.1 改动

* `OriginHistoryRoute.describe(tmdbDetailModeConfigured, standaloneTmdbMode, siteEnabled)`：删除 `tmdbId/mediaType` 入参与 `direct:history_without_tmdb_identity` 分支，判据与标准历史逐项同构（详情页模式 + 站点 TMDB 策略）。
* `HistoryResumeCoordinator.routeOriginHistory`：改用 `TmdbSitePolicy.isEnabled(history.getSiteKey(), history.getVodId())` 作为站点判据；删除"这条历史没有 TMDB 身份，直接进入播放"的 `Notify` 与对应注释；非 TMDB 模式静默直放。
* `TmdbDetailActivity`：`originHistoryIntent` 不再预置 `tmdb_id/tmdb_media_type/tmdb_title`（标准历史同样不预置）；`loadOriginContent` 改为调用与标准路径相同的 `loadTmdbResult()`（按原源 Vod 名称/站点既有匹配），不再依赖 `initialTmdbItem`。
* `bindFlags`：原源模式下 `findInitialFlag` 失配时退回首条线路（原源历史里的稳定线路键来自上次播放，可能与本次 worker 返回顺序不符）。
* 过时断言同步：`GlobalHistoryResumeSourceTest:21` 的搜索文本为 `routeOriginHistory(activity, history)`（本轮基线已含该文本，未再改动）。

### 16.2 验证

* 单测：`OriginHistoryRouteTest` 4（新签名/站点策略/无身份前置）、`GlobalHistoryResumeSourceTest` 32、`OriginPrePlaybackFallbackTest` 4、`OriginFallbackPolicyTest` 4、`OriginRelayTargetTest` 6、`OriginSessionContractTest` 4、`HistoryResumeCoordinatorTest` 4 —— 全绿（`s5-unit-1.log`）。
* 编译：mobile + leanback + AndroidTest 三目标 `BUILD SUCCESSFUL`（`s5-build-1.log`）。
* 设备：emulator-5556 `OK (1 test)`，logcat `ORIGIN_TMDB_DETAIL_OK cid=67 saved=1708`（无 TMDB 身份的历史也能进 `TmdbDetailActivity`、显示原源线路/第 2 集、真实起播、原 cid 进度写回）。该设备原先缺少夹具输入目录（`origin-isolation-input`），已从 5554 复制 `origin-fixture.jar` 与 `media-fixture` 后复验。
* emulator-5554 **未通过**：同一用例渲染出 `线路/站源：origin_fixture/暂无可用选集或站源`（原源 Vod 已显示、选集为空），`OK` 未出现。断言原文：`TMDB detail must render the original source lines/episodes, got: …站源：origin_fixture|选集|季度|暂无可用选集或站源…`。已尝试的假设（每项各 1 次）：线路键失配退回首条线路、整进程重开一次，均未修复；按"同一失败假设最多 2 次"停止，未再猜测性改动。
* 全量回归：5556 也曾在全量运行时失败于同一断言（`Tests run: 10, Failures: 2`），5554 全量为 `Tests run: 10, Failures: 1`。即该用例在"进程内已跑过其它用例"或"设备存在历史缓存"时不稳定，**本轮不声称设备全绿**。

### 16.3 结论与未覆盖

1. 分流逻辑（同构判据、无身份前置、无多余提示）已按用户要求改完并经单测锁定；5556 上设备行为正确。
2. **未解决**：5554（以及 5556 的全量运行）上原源 TMDB 详情页偶发"暂无可用选集或站源"，需继续定位（怀疑点：原源 Vod 的 flags/episodes 在复用 `:origin` worker 或历史缓存场景下为空；`renderEpisodes` 依据 `selectedFlag.getEpisodes()`）。这是产品缺陷，不应以放宽断言收场。
3. 未验证：TV(leanback) 设备、阅读器路由、TMDB 联网元数据、API24–27、Node 租约。
## 17. 中断点：跨源历史走 TMDB 详情页（未完成，勿当通过）

**状态**：工作区未提交（守卫 H-ORIGIN-RESUME-S5 active，BASE=264b7e688a3c993a6fc62c5af25a5a66a806c7f9）。

### 已确认修复（各自有设备证据）
1. 删除我自加的“必须有 TMDB 身份”前置判断与那句提示；分流改为与标准历史同构（详情页模式 + `TmdbSitePolicy.isEnabled`）。
2. 旧租约属于另一份历史时：先 `setCallback(null)` + `PlaybackService.releaseOriginLease()`，再按有限次数重试开新租约（替代会超时留空的 `closeAfterDeath` 一次性回调）。
3. `TmdbDetailActivity` 内联播放改用 `startPlayer(getPlaybackKey())`（原先传历史键，导致播放服务按“媒体已被替换”释放租约 → 端口注销 → `unregistered-loopback-port` / `IO_NETWORK_CONNECTION_FAILED`）。
4. 缓存 Result 增加 relay 端口校验，端口变化即丢弃缓存。
5. `progressCommitted()` 不再要求 `playbackStarted`：我们自己的进度写回不应把自己判成过期。
6. 设备测试：`OriginHistoryRoute*` 等单测全绿；`Setting.putDebugLog(true)` 让渲染/取流路径可见。

### 仍未收敛（两机交替失败，非设备差异，同为 SDK 28）
- 详情页“原源线路/选集”渲染与“内联播放器挂载”之间的时序仍不稳定：`5554` 有时文本缺 `origin-line`，`5556` 有时点播放后播放器未挂载。
- 决定性观测：每个租约 `READY → PLAY_READY → BLOCKED/ISOLATION_ERROR`；点击播放时 `onOriginResult`（`origin-play` 日志）未触发，说明播放请求在客户端被判过期并 `close()`，而不是页面绑定问题。
- 断言口径：原断言要求文本含字面量“第2集”，但页面实际渲染集号与 `bob:jar - 2` 标题，属**断言过窄**（“暂无可用选集或站源”是布局常驻占位，即使隐藏也会被 dump）。已改为要求 `bob` + `origin-line` 且不含 `alice`。

### 唯一下一步
在独立会话中专门定位 TMDB 详情页的“详情送达 / 选集绑定 / 内联播放发起”三处时序（建议在该页加一次性 trace 并在 5554/5556 各跑 3 次），收敛后再提交；**在收敛前不得按通过处理**。
## 18. 第 6 轮续接（进行中）

### 18.1 起点现场（本轮开始即核对）

* 分支 `dev2`，HEAD `264b7e688a`；工作区 9 个未提交文件即 §17 记录的改动，全部保留并以 `--adopt-dirty` 纳入守卫 `H-ORIGIN-RESUME-S6`（BASE 同上）。
* 设备主包为 `com.silent.android.webhtv`，测试包 `com.silent.android.webhtv.test`；两台模拟器（5554/5556）均 SDK 28、`ro.product.cpu.abi=x86_64`，主包 18:29/18:30 已更新为含 §17 改动的构建。`com.fongmi.android.tv` 是无关旧包（5556 上状态 corrupt），与本任务无关。
* `files/origin-isolation-input` 不在两台设备静态目录中，由设备用例运行时自行准备。

### 18.2 本轮已核到的新证据

1. `5554` 失败 dump 里**同时**出现“继续播放 - 第2集”（TMDB 标题与第 2 集信息已就位）与“线路|站源：origin_fixture|选集|季度||||暂无可用选集或站源”，即**没有 origin-line 线路 chip**；而 logcat 反复打印 `origin-detail applyLoaded flags=1 [origin-line:3]`，说明带 3 集的原源 Vod 确实进入了 `applyLoaded`。
2. 设备用例的等待条件仍在找字面量 `第2集` 来决定是否重开页面，而最终断言用 `bob` + `origin-line`；“等不到就 finish 再 startActivitySync 重开”会把旧租约释放与新租约 open 挤在一起，两机耗时 40s / 128s 与该两轮 30s 等待吻合。**必须先区分“测试重开造成的竞争”与“真实渲染缺陷”。**
3. `applyLoaded` 在 `initHistory()` 返回 null 时 `loading GONE` 并直接 return，不执行 `bindPage()`（线路与选集在这里渲染）；`bindPage` 渲染走 `enqueueRenderChunk` 且带 `generation != loadGeneration` 丢弃条件。

### 18.3 状态

只读根因调查已派发（报告写入 `app/build/origin-isolation/diagnosis-s6.md`）。该句为本轮初始检查点；后续现场以以下恢复锚点为准，不能当成最新状态。

## 19. S6 续接恢复锚点（2026-11-11，UTC+08）

- 目标/决定：跨源历史与标准历史同构分流（详情模式 + 站点策略），不要求历史已有 TMDB 身份，不新增分流/播放页/额外提示；WebHome 不在本单位内。用户明确继续任务且审批关闭，沿用已定范围，不请求审批、不推送、不下载依赖。
- 现场/回滚：`dev2` / `264b7e688a3c993a6fc62c5af25a5a66a806c7f9`；现有 9 Java + 本文档未提交补丁保留。原 S6 守卫已仅归档状态（未丢弃代码），`H-ORIGIN-RESUME-S6-CONT` 精确采用这 10 个任务路径，其他路径不允许改。使用 `G:\Git\bin\bash.exe`，默认系统 `bash.exe` 是不可用 WSL；start 的 `--adopt-dirty` 实际须逐路径重复选项。
- 已取得证据：`diagnosis-s6.md` 明确旧设备用例的文案 ready 与重开混页缺陷；当前测试已改为单页/结构就绪。`s6-device-5554-2.log`、`s6-device-emulator-5554-3.log`、`s6-device-emulator-5556.log`、`s6-device-emulator-5556-3.log` 各 `OK (1 test)`；这些只是 TMDB 定向通过。`s6-full-5554.log` 为 10 项 2 失败，`s6-fix-testSingleMobileRuntimeSequenceRequiresRealOriginPlayback.log` 后续单项通过；`s6-fix2-testPrePlayerFailureUsesGlobalAutoSearchAndOff.log` 最新 AUTO 仍在目标启动断言失败，不能宣称全量通过。
- 计划/分工：修复 brief/run 1/3，agent `af0caf68-c363-46ea-b6be-85e4abd1b2a3`，仅允许测试/Coordinator/HistoryPlayback/SessionService/mobile VideoActivity；审查 brief/run 1/3，agent `49feeb41-174e-4a2f-b010-555a9a4997f2`，只读固定 `s6-cont-review-base.diff`。源码审查与修复共享接口但不共享写入，审查不得用活动工作树替代固定快照。两份六要素简报、后续报告都在 ignored `app/build/origin-isolation/s6-cont-*`。
- 最新诊断（待验收）：修复者已保存 `s6-cont-before-logcat.log`，旧 AUTO 失败时目标实际启动并 RESUMED 的是 `TmdbDetailActivity`，而测试只监控 `VideoActivity`。详情模式污染了测试目标预期；这不是已证明的产品未启动。修复者继续固定场景并验证真实当前源起播，不降低 AUTO 起播要求。
- 未验证/风险：新增 relative-media URL 补丁、租约重试待播选择、requestPlay false 与结构 ready 身份证明交独立审查。Node/API24–27/阅读器/TMDB联网/TV设备等原有未验边界不冒充本次通过。未提交、未建标签。
- 预计：00:40 起修复 10 分钟、审查/验收 12 分钟、收尾 3 分钟，目标 01:05；超时去掉可选调查而非减少必要验证。
- 审查结果/路由（01:52）：固定快照 Spec/Quality 不通过。A1 mobile 引用未定义 `dispatchOriginFallback`；最新代码已补声明但须实际编译确认。A2 TMDB 调用忽略 `requestPlay=false`、可能永久 pending；A3 结构 ready 未明确证明选中/实际请求第 2 集；两条均已发修复者处理。修复 Boundary 扩为前述 5 个文件 + 已在守卫内的 `TmdbDetailActivity.java`，源码唯一文件 cap 6（累计），未扩行为。
- 审查争议/待复核：A4 把带 scheme URL 判断为晚期 relay 网络错误的推理，与 `URI.resolve` 及后续显式 `ISOLATION_ERROR` 不符；A5 把 OFF/resolved=null 推导为“已 finish”忽略了两分支早于 onLaunching 的 return。已要求原审查者 run 2/3 局部核实并修正报告。同时要求复核“pendingEpisodeUrl 字段保留即能重放用户选集”的结论，不接受未证明的无缺陷判断。
- 超估时：01:52 时已超 01:05 目标，停止可选调查，仅完成编译阻断、真实请求/第2集断言、错误终态和必要设备回归；不重复已经成功且未改变的检查。
- run 2 局部结论（01:57）：审查者基于固定起点 + diff 重建确认 A4/A5 的错误推断，已撤回。OFF/resolved=null 在 onLaunching 前 return；带 scheme URL 仍走 EDL 或明确非媒体 `ISOLATION_ERROR`，不需为错误报告扩功能。“pendingEpisodeUrl 字段保留就不会丢请求”结论亦撤回，改为条件性恢复缺口：READY 默认预解析旧历史集，pending 非旧集时没有自动补发；busy 只在 open/READY 前，UI 可达性尚未证实，已交实施者连同 inactive 明确拒绝验证。
- 新证据（调度者已读真实输出）：`s6-cont-build-2.log` 为 `BUILD SUCCESSFUL in 27s`，`s6-cont-testPrePlayer-2.log` 为 `OK (1 test)`；前者补上 mobile 缺失方法后的构建，后者对应起播前失败 AUTO/SEARCH/OFF 用例。它们不代替 A2 请求拒绝可见终态、A3 明确第 2 集身份、双端最终相关回归。
- 修复 run 1 交回：`s6-cont-fix-report.md` 记录 mobile 缺失派发方法已补、AUTO 用例固定 direct 模式并 try/finally 恢复。`TmdbDetailActivity` 已对 requestPlay=false 清 pending 和可见报错；TMDB 用例增加激活第 2 集与真实 episode 请求断言。`s6-cont-build-3.log` SUCCESS、`s6-cont-test-two-1.log` 5554 `OK (2 tests)`（36.287s），`s6-cont-after-logcat-3.log` 为原 cid=392 saved=1772。
- 独立验收返工 run 2/3：正常两个用例未触发新增拒绝分支，不能替代该分支红绿；要求实施者在既有测试文件新增“详情就绪→释放会话→再播放”的拒绝终态用例，并收紧请求身份至 account=bob + episode=/episode/2（保持同集缓存，不强迫点击重取流）。原 busy 重连缺口经控制流核验：busy 只发生 open/READY 前，选集要 PLAY_READY 之后才可用；此路径不可达，未新增重发抽象。
- 并行验收：5556 独立验证 agent `8dad24e3-ade0-49e6-91be-0638d74e7616`，brief/run 1/3，冻结现有主/test APK 后只跑 TMDB + 三模式回落；报告 `s6-cont-device-report.md`。调度者最终 leanback Java 编译 + 7 类定向单测 job `pwsh-60c87828b46b42e8`，日志 `s6-cont-final-unit-leanback.log`，不得为等待执行占位命令。
- 未闭环边界：拒绝终态新绿例、5556最终两场景、leanback/定向单测、最终增量审查；增强详情模式下 AUTO 落点是需手动点播的正常 TMDB 详情页（同标准历史），本轮三模式自动起播用例固定 direct，不冒充增强 AUTO 全链路通过。
- 最终静态/单测证据已独立核实：job `pwsh-60c87828b46b42e8` exit 0，`s6-cont-final-unit-leanback.log` 为 `BUILD SUCCESSFUL in 1m 11s`、实际执行 leanback Java 编译。7 类 JUnit XML 共 58 项、0 failures/0 errors（GlobalHistoryResumeSource 32、OriginHistoryRoute 4、PrePlaybackFallback 4、FallbackPolicy 4、RelayTarget 6、SessionContract 4、Coordinator 4）。不重复未变的已成功检查。
- 新增拒绝用例 `testTmdbReleasedOriginRejectsPlayWithoutPendingOrCurrentTakeover` 在实现 run 2 做真实 release 后再次 playInline，核 false/pending/error/无当前源接管。红例只暂时回退该分支，已要求立即恢复原修复字节并报告恢复校验；5556已明确只用开始冻结的 02:27 绿版 APK，防止安装临时红例。最终生产源码若与此前验证字节相同，不重跑58项或leanback。
- 5556 验收 run 1 新证据：验证者冻结并只安装 02:27 绿版（02:36:40），main SHA256 `7A308E1541F24E732B99DF15F4F40DC3EC788EAEE0344E8CCF3AD58BF03A075E`，test `0227EFFB971A92403E5968B8EF9E75DAECE256CE85A29DC86613982D98A00E45`，副本/日志位于 `app/build/origin-isolation/s6-cont-5556-20261111-023640/`。双方法实际 2 tests/1 failure：TMDB status=0 通过；PrePlayer在 AUTO 分流前 fixture-private prefs `[account]` 空→非空导致 MainState 断言失败，尚未进入回落行为，不伪报三模式通过。
- 该红例已回到实现 run 2：核当前源 fixture init 与 baseline 时序（TMDB 已显式预热而 PrePlayer 未预热）；若确认为冷夹具基线缺口，必须显式预热再取基线，不对白名单加 account、不弱化污染断言。5556 先收尾 cleanup 证据，新的最终绿包后只复跑失败 PrePlayer，不重复已绿 TMDB。新增两个 lease 目录只核必要清理/进程/端口证据，不擅删设备数据。
- 实现 run 2/3 交回（报告 §7，02:47）：新增 `testTmdbReleasedOriginRejectsPlayWithoutPendingOrCurrentTakeover`，真实 worker 详情就绪→反射读真实选中 URL=/episode/2→`mOrigin.release()`→requestPlay 实际 false→点同页真实 play 按钮。旧分支红例 `s6-cont-run2-red2-test.log` 失败 `pending expected false but true`（logcat `rejected=true pending=true`）；修复绿例 `s6-cont-run2-green2-test.log` `OK (1 test)` 8.273s，logcat `ORIGIN_RELEASED_REJECTION_OK cid=400 episode=/episode/2 pending=false searches=0 alicePlays=0`，错误 isShown/文字、spinner 清除、不 finish、无新增取流、完整 MainState 均实际执行。A3 收紧为 originRequestStart 起算 + path=/player + episode=/episode/2 + account=bob + bob Authorization，读 selectedEpisode 精确 URL；同集缓存不强制二次取流。PrePlayer 在 load(a) 后、matchCurrent 与全部 MainState 前 `SiteApi.detailContent` 预热（根因 OriginFixture.java:15-18 init 同步写 fixture account），不白名单 account。
- 调度者独立核验：`TmdbDetailActivity.java` 当前 SHA256 `FC09786BC346F3A90CEEE58114911DE845C7004E34FD21E6E27AEE3443756043` 与两次红实验前备份一致（identical=True），故此前 58 项单测/leanback 结论仍对应当前生产字节；`git status` 仍为同一 10 个任务路径。
- 终验最终包（`s6-cont-run2-final2-sha.log`）：main `184A23316A5C5F59ED23E450D89D03F5823E725D75D64725FF71D67A020B6A14`、test `2DAB4A746661DAAC8854B45E272A64403006D33CE8C42D5D6CDB24149389D152`。已被要求冻结后仅重验 PrePlayer，SHA 不符即停手。
- 用户范围决定（租约目录）：`OriginStorageContext.java:31` 按 lease 建 `no_backup/origin/<lease>`，`OriginSessionService` 关闭 Node/relay 并退出进程但不删除目录；5556 观察到 2 个非空残留目录（无 fd-/媒体文件，进程与回环监听均已消失）。用户选择“本次完成续播修复，目录清理另立任务”，因此本次不宣称磁盘彻底清理，须作为独立后续任务处理，不在本提交内手删设备数据。
- 审查 run 3/3（§F，02:5x）结论“有条件通过”：A1/A2/A3 均 ADDRESSED 且有红绿差分；增量口径为生产仅 `TmdbDetailActivity` +7 行 false 分支与 `VideoActivity` +13 行 `dispatchOriginFallback`（其余 5 个生产文件新增行逐行未变）。放行条件两条：(i) 强化后的正常 TMDB 用例设备复跑绿；(ii) A4（active 但待播集≠历史集的静默不交付）落地或明确记为已知限制。另有 Minor：`headers("bob")` 无空值防御、激活文本断言偏宽（由反射 URL 断言兜底）、`bindFlags` flags[0] 兜底仅覆盖正常路径、`dispatchOriginFallback` 增加 `pending==false` 早退使全局续播关闭只提示一次（合理去重，属行为微调）。
- 5556 run 2 复验（新冻结包 main `184A23…6A14`/test `2DAB4A…9D152`，两包 SHA 均与期望一致，先 force-stop 残留主进程 17336）**失败**：`OriginIsolationPlaybackTest.java:426` monitor 30s 超时，`pre-player failure must launch the configured global-resume entry, mode=1`。run1 的 fixture 偏好基线断点确已消除（`ORIGIN_FIXTURE_PREFS_BEFORE existed=false keys=[]`，越过 :415）。实观：READY(proxy=42443)→BLOCKED ISOLATION_ERROR→Toast→约 2s 后 `TV-search site=origin-alice,keyword=bob:fixture` 返回 `{"vod_id":"origin-vod","vod_name":"bob:fixture"}`；未观到新 Activity START/onNewIntent/Deliver/新 VideoActivity 实例。SEARCH/OFF 未执行，三模式均不记通过。5554 同用例此前通过，属竞态或观测手段问题，两者尚未分清。
- 决策：A4 记为**已知限制**（控制流上 busy 只在 open/READY 前，选集需 PLAY_READY 后才可用，故不实现 pending 重发）；租约目录清理按用户决定另立任务，本次不手删设备数据、不宣称磁盘彻底清理。
- 实现 run 3/3（最后一轮配额）已派发：用最小一次性 trace 区分“产品真没启动”与“仅测试数新实例不足”，只修根因，禁止延长等待/重开/放宽断言。**结果：第三类根因——AUTO 解析段 `resolved=null`，从未进入启动段。** trace（`s6-cont-run3-trace-5556-logcat.log`）显示 `callback request=2 expected=2 resolved=false finishing=false destroyed=false`（引用计数与存活检查均通过，回调未被丢），同轮搜索已返回 `{"vod_id":"origin-vod","vod_name":"bob:fixture"}`，20ms 后 `resolved=false`，全程无 `startFromResolvedHistory` 早退、无 `onNewIntent`、无新实例。
  - 根因：run 2 为修 §19 记录的空偏好基线，在 `matchCurrent=true` **之前**预热了当前源站点，把 alice 名详情写进 `SiteApi.detailContent` 缓存（键 siteKey+id，不含 cid），而 `HistorySourceResolver.scoreCandidate` 要求标题匹配 → AUTO 候选被判 REJECTED → 走可见搜索兜底。5554 当时缓存已是 bob 名，故此前“通过”是缓存状态巧合，不作为正确性依据。
  - 修复（仅测试文件，生产零净改动）：把 `upstream.matchCurrent = true` 移到预热之前，预热断言改为不依赖账号名的非空判定；仍保证预热早于全部 `MainState` 基线。临时 trace 与红实验后三个生产文件按原字节还原：`TRACE-RUN3` 残留 0（本机 grep 复核无匹配），`TmdbDetailActivity` SHA `FC09786B…6043`、`HistoryResumeCoordinator` `BAE6EF33…93F3`、`VideoActivity` `876D06AF…BD78` 与审查快照一致。
  - 双机绿例（本轮设备运行 4 次）：5556 `testPrePlayerFailureUsesGlobalAutoSearchAndOff` `OK (1 test)` 33.108s（`s6-cont-run3-fix-5556-test.log`，logcat 见 origin-alice 搜索返回 bob:fixture）；5554 同用例 `OK (1 test)` 36.579s（`s6-cont-run3-fix-5554-test.log`），logcat 三条标记 `ORIGIN_PREPLAYER_FALLBACK_OK mode=1 searches=1` / `mode=2 searches=1` / `mode=0 searches=0`（调度者已直接复核该日志行）；5554 强化版 `testTmdbDetailModeRoutesCrossSourceHistoryIntoTmdbDetailOnOriginLines` `OK (1 test)` 17.003s，`ORIGIN_TMDB_DETAIL_OK cid=404 saved=2240`。审查放行条件 (i) 的强化 TMDB 设备绿已取得。
  - 最终产物（调度者 Get-FileHash 复核）：main `936E477ACF4DE9CA37681A15F470F6838FE03ADE56B29286D64DEB695815E74F`、test `2F3CC968E9FAF01840DC662A9F1A49ACFDB578829D606AEFB3E50274C3800592`。生产字节与审查 run3 快照一致，故未重复 58 项单测与 leanback 编译。
- 阶段结论（待独立复核确认后定格）：原源详情第 2 集续播、原源起播前失败按 AUTO/SEARCH/OFF 接续均有双机设备证据；A4 重连待播为已知限制；租约目录清理另立任务；Node/API24–27/阅读器/TMDB 联网/TV 设备仍未验证，不冒充通过。
- **唯一下一步**：收取 5556 独立复核（冻结上述最终包 SHA 后只跑 PrePlayer 一次）。通过则记录最终结论并原子提交 + 恢复标签；失败则如实记录未过项、不提交。

## 20. 本轮最终结论（2026-11-11）

### 20.1 已完成并有三重证据

1. **跨源历史走正常入口**：同构分流（详情页模式 + `TmdbSitePolicy.isEnabled`），无 TMDB 身份前置、无额外提示；原源详情页渲染原配置线路/选集并在同一内联播放器真实起播历史第 2 集，进度按原 cid 写回，不产生当前配置记录。
2. **原源起播前失败按全局历史设置接续**：AUTO 真实到当前源并起播、SEARCH 真搜索并携带原历史参数、OFF 停留原页且原因可见；起播后失败不接管。
3. **拒绝终态**：原源会话已释放后再请求播放，明确拒绝并清除等待态、错误可见、不接管当前源（红绿差分证明）。
4. **不再有静默失败**：原源详情页/播放页的 `requestPlay=false` 由产品显式处理；`mobile` 缺失的 `dispatchOriginFallback` 已补，手机/TV 均能编译。

### 20.2 证据（可复核）

- 编译：`s6-cont-build-2.log`/`s6-cont-build-3.log`/`s6-cont-run3-fix-build.log` 均 `BUILD SUCCESSFUL`；`s6-cont-final-unit-leanback.log` 实际执行 leanback Java 编译并 `BUILD SUCCESSFUL in 1m 11s`。
- 定向单测：7 类共 58 项、0 failures/0 errors（`OriginHistoryRouteTest` 4、`GlobalHistoryResumeSourceTest` 32、`OriginPrePlaybackFallbackTest` 4、`OriginFallbackPolicyTest` 4、`OriginRelayTargetTest` 6、`OriginSessionContractTest` 4、`HistoryResumeCoordinatorTest` 4）。
- 5554：`testPrePlayerFailureUsesGlobalAutoSearchAndOff` `OK (1 test)`，三条 `ORIGIN_PREPLAYER_FALLBACK_OK mode=1/2/0`；强化版 `testTmdbDetailModeRoutesCrossSourceHistoryIntoTmdbDetailOnOriginLines` `OK (1 test)` 17.003s，`ORIGIN_TMDB_DETAIL_OK cid=404 saved=2240`；拒绝用例红绿（旧 `pending=true` 失败 / 新 `pending=false` 通过）。
- 5556：调度者本人独立冻结同一最终包（main `936E477A…5E74F`、test `2F3CC968…00592`，SHA 前后同字节）后保数据安装，只跑 `testPrePlayerFailureUsesGlobalAutoSearchAndOff` → `OK (1 test)` 30.967s，logcat 三条 `ORIGIN_PREPLAYER_FALLBACK_OK mode=1 searches=1`、`mode=2 searches=1`、`mode=0 searches=0`（`app/build/origin-isolation/s6-cont-5556-run3-dispatch/`）。
- 审查：固定快照两轮独立审查 + 最终增量 run3 逐项 ADDRESSED，生产增量仅 `TmdbDetailActivity` +7 行与 `VideoActivity` +13 行。

### 20.3 已知限制（本轮明确不声称通过）

1. **A4 重连待播**：`onReady` 仍按历史集预解析，`busy` 只发生在 open/READY 前、选集需 PLAY_READY 后才可用，故该“用户待播集≠历史集”的静默不交付路径控制流不可达；不实现 pending 重发抽象，记为已知限制。
2. **租约私有目录**：`OriginStorageContext` 按 lease 建 `no_backup/origin/<lease>`，关闭时不停删；5556 观察到非空残留目录（无 fd-/媒体文件，进程与回环监听已消失）。经用户决定另立任务处理，本次未手删设备数据。
3. **增强详情页模式下的 AUTO 落点**：跨源历史失败回落在详情模式下落 `TmdbDetailActivity`（`auto_play=false`，需再点播放，与标准历史同构）；三模式自动起播用例固定 `DETAIL_OPEN_DIRECT` 覆盖，该形态未设备验证。
4. **原有未验边界照旧**：Node 运行时租约（缺 libnode，不隐式下载）、API 24–27、阅读器（小说/漫画）路由、TMDB 联网元数据、TV(leanback) 设备运行。

### 20.4 回滚

本次为单一守卫单位：回滚该提交即恢复“跨源历史只走当前源匹配”的旧行为；不涉及主数据库迁移，不删除用户配置/历史，不改设备数据清理策略。
## 21. H-ORIGIN-ENHANCED-PLAY：炫彩原源历史播放页修复（2026-11-11，执行 run 1 + 返工 1）

### 21.1 本次范围与实现

- BASE `2604ace9ff547c1cce6c5191680b926adfa0657b`，`dev2`；初始工作区干净，守卫 `H-ORIGIN-ENHANCED-PLAY` 由调度者预先启动；只改三份 Activity、既有 OriginIsolationPlaybackTest 与本文件追加节，不提交/打标签。
- 原 `TmdbDetailActivity.onPlay:6994` 把所有原源硬编码成 `playDetailFullscreen`。删除该短路交回模式控制器：Enhanced 的 `playDefaultPlayback` 对原源启动既有正常 VideoActivity；Fusion/Player 分别继续 `playInline`/`playDetailFullscreen`。
- 两 flavor 加 Flag/Episode originHistoryIntent/startFromOriginHistory 重载；沿用 `saveInlineHistory`/`updateInlineHistory` 后，用现有 `mOrigin.writeProgress` 保存当前选集快照再释放租约。HistoryResumePayload 是原 cid/key/季引用，不是完整 History JSON，因此仅改 Intent 选集不足以改变新 worker 首次预解析的集。
- 启动前显式 release，已释放 mOrigin 暂留，后台不会因 null 自动重开；进度保存加 active 门，不让已交接的旧对象写回/影响新页。release 自身已有 client 身份比较与幂等性，不新增全局无条件释放。
- VideoActivity 两端 finishing 的 onPause 调用既有 persistOriginProgress(true)，非 finishing 不释放；返回详情且 service 无有效 lease 时按原 payload/cid/key 更新历史/选集并走 origin() 重建，不走当前源 History.findPlayback。

### 21.2 已取得与未取得的证据

- 红：5554 对未改生产代码的增强用例 `ehp-red-device.log`，40.559s、1/1 failure，准确失败在未启动 VideoActivity。
- 离线统一构建 `ehp-green-build.log`：BUILD SUCCESSFUL in 3m7s，实际执行 mobile+leanback Java 编译；定向 7 类 58 项 0 failures/0 errors。
- 第一次设备绿尝试 `ehp-green-device.log`：PLAYER 拒绝终态通过；增强用例第2集真实正常播放、原源请求、首次返回基础判据通过，但固定 adapter position=2 点击后 selectedEpisode 仍为 /episode/2，未证明第3集。
- 加入新 Vod/Flag/adapter 对象同次渲染检查并按真实 URL 定位后，`ehp-green2-device.log` 91.888s 失败于返回 ready；日志显示旧 worker 死亡、新 PID4199 READY→PLAY_READY→applyLoaded 原源1线3集，但旧日志不能区分哪个 ready 子条件不成立。**不宣称增强全链路验收完成。**
- 调度者授权返工1：测试重编1次、仅增强设备1次，必要时产品修复 mobile 编译1次；不重复已绿 PLAYER/58单测。诊断加入 windowFocus/listShown/源对象身份/选中URL/adapter卡片文字/loadGeneration 实值；以可见真实选集控件代替窗口焦点作为操作就绪必要条件，仍保留同源对象、原集URL、第3集真点击、原源请求与真实起播断言。日志 `ehp-r1-*`，最终结论见执行报告。

### Recovery anchor（H-ORIGIN-ENHANCED-PLAY）

- 当前：四源码/测试文件已改；报告 `app/build/origin-isolation/ehp-fix-report.md`；首轮APK main SHA256 `D7CB24FD0446AE73404B2E7A3FFA0232A9DFD1634BEC315CE2C0C16A9BD68F87`，诊断前 test `232495ED3D1ADF84D5A8F2BEF351CEA40B9ED5D5AC422CD0732CB40245D107B7`。
- 未过：返回真正可用并换第3集再正常起播；两台设备/TV设备运行未做；既有 Node/API24–27/阅读器边界不扩展。
- 唯一下一步：收取 `ehp-r1-device.log` 与对应 `ORIGIN_RETURN_STATE/CARD/THIRD_CLICK` 实测值，定位必要条件或产品缺陷；到额度仍未验收则如实向父调度者交回，不盲试、不提交。
### 21.3 最后返工与本任务最终自验结论（11:20收口）

- 最后返工2经调度者授权仅测试重编1次/增强设备1次；`ehp-r2-build.log`成功19s，`ehp-r2-device.log` **OK (1 test), 56.632s**。没有重复已绿PLAYER拒绝与58单测；产品源码首轮后未改。
- 诊断已确证：返回新worker/Vod/Flag与窗口焦点均正常，仅adapter对象引用不同；TmdbEpisodeAdapter按Episode.equals去重导致旧对象合法保留。最终改用精确URL/名称列表匹配，仍保loadGeneration/新Vod/原Flag/selectedURL/真实卡片绑定与第3集请求、起播。
- 默认夹具原/detail仅两真实集；Vod.objectFrom二次setFlags对未带urls的旧flags追加空集，才有“3项”。增强夹具现提供明确三集及vodFlags.urls，默认其他用例响应保持原样。既有通用空集行为已向调度者路由，不在本次边界外修生产解析器。
- 两循环通过：同一原源详情→正常VideoActivity第2集续播→返回→按URL真点第3集→正常VideoActivity原源取流/真实视频→再次返回可用；完整MainState不变量、原cid进度写回均实际执行。最终日志 `ORIGIN_ENHANCED_NORMAL_OK episode=/episode/3 position=1322 returned=true`，selected=/episode/3、三卡片URL=/episode/1,/episode/2,/episode/3、counts=[1,3,0]、全部必要返回状态true；`ORIGIN_TMDB_DETAIL_OK cid=416 saved=914`。
- 自验结论：本任务要求通过，待调度者独立复核；mobile/leanback编译与7类58项/0失败沿用同一产品字节，PLAYER拒绝用例通过，Fusion控制器仍playInline、Player仍playDetailFullscreen。TV/Fusion设备、第二台、Node/API24–27/阅读器/无痕实机未新增验证，不冒充通过。
- 全部证据见 `app/build/origin-isolation/ehp-fix-report.md` 与ehp-*日志。最终main SHA256 `D7CB24FD0446AE73404B2E7A3FFA0232A9DFD1634BEC315CE2C0C16A9BD68F87`；test `AAE1A41170956362F008EA3228435E33A05163879B7B3CD9537C8FA037056075`；源码散列见ehp-final-hashes.log。
- 如实修正预算：mobile Java任务实际执行5次（此前误把未改生产当成UP-TO-DATE），leanback1次、单测1次、设备5次调用/6用例执行；3次测试补编/后两次设备为调度者后续明确授权，日志全保留。不声称原mobile两次cap未超。guard check PASS，dirty恰5授权路径；不提交/打标签，guard保持active。

### Recovery anchor（H-ORIGIN-ENHANCED-PLAY最终）

- 状态：执行者自验完成；代码和最终APK冻结，报告已写；BASE/HEAD未变、5路径未提交。
- 已路由：后台租约/退出旧对象/返回进度问题已修并验证；测试引用判据/三集夹具已按父授权修正；通用空集、租约目录清理及既有未验边界留后续任务，不扩大本轮。
- 唯一下一步：调度者独立复核末轮增强OK、PLAYER拒绝OK、58单测/双端编译与最终散列，给交付裁决；不再为补系统缓冲里已丢的早期标记重复设备测试。
### 21.4 调度者独立复核与裁决（最终）

- 复核方式：不复用 5554 结果，改在 emulator-5556 上冻结执行者给出的最终 main `D7CB24FD…8F87` / test `AAE1A411…6075`（复制前后 SHA 一致），保数据安装后只跑 `testTmdbDetailModeRoutesCrossSourceHistoryIntoTmdbDetailOnOriginLines` 一次。
- 结果：`OK (1 test)` 54.685s；logcat `ORIGIN_ENHANCED_NORMAL_OK episode=/episode/3 position=365 returned=true`、`ORIGIN_TMDB_DETAIL_OK cid=102 saved=460`（证据目录 `app/build/origin-isolation/ehp-5556-dispatch/`）。即炫彩详情页点播放已进入正常播放页、按原源取流并起播选中的第 3 集、返回详情页仍可用；与执行者 5554 `ehp-r2-device.log`（`saved=914 cid=416`）互相独立。
- 代码复核：`onPlay` 不再短路原源；`playDefaultPlayback` 原源分支按 `originHistoryIntent(context, history, flag, episode)` 启动正常播放页并在启动前 `mOrigin.release()`；`restoreOriginAfterExternalPlayback` 只在无有效 service lease 时按原 cid/key 重建；`persistOriginProgress` 增加 `isActive()` 门；融合/播放器详情控制器未改。
- 裁决：本任务（炫彩模式下跨源历史点播放落点）通过。遗留不属本任务：`Vod.objectFrom` 对未带 urls 的旧 flags 二次 `setFlags` 可能追加空集；租约私有目录清理；TV/Fusion 设备运行、Node/API 24–27、阅读器、无痕实机未验。

## 22. H-ORIGIN-POST-MERGE-20261111：合并后原源播放回归修复（2026-11-11，执行 1/3）

### 22.1 本轮边界、根因与已取得红证据

- 基线 `dev2` / `9f63ddaff60acb8b4f4200822af8ea7f9a591888`，初始工作树干净。原 `H-ORIGIN-FILE-MANIFEST` 未提交守卫已安全 abort；父调度者在同轮明确扩为五类真实缺陷，当前守卫 `H-ORIGIN-POST-MERGE-20261111`，仅九个授权路径，adopt 只接纳执行者自己的改动。Node 实际路径为 `node/OriginNodeService.java`，Robolectric 只属 testLeanback，新增测试实际路径为 `testLeanback/.../OriginPostMergeRegressionTest.java`；这两处事实修正均由父调度者确认。
- file 清单：`respond` 在 HTTP 清单检测前直达 `localFile`，旧分支一律 octet-stream 原样返回；绝对 HTTP URI 逃出帐号隔离，relative 失去原 file 基准。真实监听器红 `relay-red.log` / `relay-red.xml`：11 项、4 failures、0 errors，HLS master 回环资源 `4→0`、DASH `3→0`；超 8 MiB 清单期望 500 却 200。
- suffix Range：旧解析把 `bytes=-500` 当成 `[0,500]`；同一红例在 1024 字节 A/B 夹具返回前 501 个 A，而非末 500 个 B。普通 `bytes=2-5`、open-ended `bytes=7-` 同轮已通过。
- 正常播放页拒绝：双 flavor `getPlayer` 不看 `requestPlay=false`，继续 `updateHistory/showProgress`，覆盖错误并等一个永不来的回调。未改这两份生产代码先构建红包 `video-red-build.log` exit 0（3m4s，跳过 native 构建），5554 保数据安装后 `video-red-device.log`：1 项/1 failure，47.231s，精确失败 `retry must show a visible ended-session failure`，已越过真实视频首帧、原源 lease 激活与 release。
- Node API24/25：`foreground` 的双参数 `Notification.Builder` 仅 API26 可用，通知路径先于 libnode 准备。`postmerge-red2.log` / `postmerge-red.xml` 的 SDK24、25 实际通知构造均 `NoSuchMethodError`。第一次 `postmerge-red.log` 是 `Shadows.shadowOf` 重载触及裁剪 FingerprintManager 的测试编译错误，不冒充业务红；用 `Shadow.extract` 修正，不改依赖。最终生产只将 builder 换成 NotificationCompat，CHANNEL/9533/前台类型保持。
- 无痕：服务 ticker 绕过 Activity 的 Incognito 门，直接写原进度并无条件 `progressCommitted`。上述有效红共 3 项/3 failures/0 errors，无痕真实 Room 原记录在实际 ticker 后从 123 变成 5432。生产只给写入调用加 Incognito 门，并仅在 writer 返回成功时 `progressCommitted`；租约替换检查、退出释放和 5 秒重排未跳过。红运行 4m22s；一次 jstack 定位主要慢点为 Robolectric NativeSQLite 初始化解压 fonts，不是应用 native 重建或 Node 启动，证据 `red-worker-stack.log:248-270`。

### 22.2 最终自验与恢复锚（12:43 完成；待独立复审）

- 最终联合命令：`G:\Git\bin\bash.exe ./gradlew --offline :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:testMobileArm64_v8aDebugUnitTest --tests '*Origin*Test' --tests '*HistoryResumeCoordinatorTest' :app:testLeanbackArm64_v8aDebugUnitTest --tests '*Origin*Test' --tests '*HistoryResumeCoordinatorTest' :app:assembleMobileArm64_v8aDebug :app:assembleMobileArm64_v8aDebugAndroidTest -x :app:externalNativeBuildMobileArm64_v8aDebug`。`final-green-build.log` **exit 0 / BUILD SUCCESSFUL in 8m34s**，204 tasks（30 executed/174 up-to-date）；mobile/leanback Java 两任务均实际执行，不把 UP-TO-DATE 算编译，不跑全 ABI/native 矩阵。
- XML 实读：mobile 7 类 **42 tests/0 failures/0 errors**，leanback 8 类 **45/0/0**。两端均为 OriginFallbackPolicy 4、OriginHistoryRoute 4、OriginPrePlaybackFallback 4、OriginRelayTarget 11、OriginSessionContract 4、ProxyClientOriginPolicy 11、HistoryResumeCoordinator 4；leanback 另 OriginPostMergeRegression 3（Node SDK24/25、无痕 SDK28）。这不是历史 58 项口径，不把未选中的 GlobalHistoryResumeSource 算本轮通过；完整 XML 已复制到报告目录。
- 新增 relay 五例全绿：真实 file HLS master 的绝对 HTTP URI/KEY/MAP 和 relative local 子清单全登记本 lease，原 file 解析结果/sourceUrl/bob headers 均相等，真实子清单/媒体请求 200；真实 DASH BaseURL/SegmentList init/segment 和 BaseURL 续接请求可用、MIME 正确；suffix -500 返回末500 B/206/`bytes 524-1023/1024`，超长 suffix 全文件，-0 为416；普通 closed/open-ended 保留；8MiB+1 清单500，未返回未隔离原文。
- Node/无痕 3 例全绿：SDK24/25 在不启动 Node/libnode 的情况下实际构造 foreground 通知（9533、标题/正文/ongoing）；SDK28 实际 ticker + Room 在无痕开时原进度123不变、不虚构 progressCommitted、lease 未关且继续重排；关时写5432，正常退出 client 关闭/static lease 清空。
- mobile 新增真实绿例：最终两包 `adb -s emulator-5554 install -r` 均 Success；`video-green-device.log` **ADB exit 0 / OK (1 test), 54.586s**。`video-green-markers.log`（PID21394）12:43:28 的 retry/episode 均错误 `原源会话已结束，请返回后重新打开`、loading=false；选集另外验证 `mPlaybackEpisodeKey` 真变化而非旧错误伪绿。12:43:29 `ORIGIN_NORMAL_RELEASED_REJECTION_OK retry=true episode=true loading=false searches=0 alicePlays=0`，`ORIGIN_FIXTURE_PREFS_RESTORED existed=true`。红/绿均验证实际正常播放页与首帧，不只测纯函数或开关。
- 修改位置：OriginMediaRelay `localFile:295` / `localStream:327`；node/OriginNodeService `foreground:231-236`；PlaybackService `originProgressTicker:105-115`；mobile/VideoActivity false 门 `3028`、leanback 对应 `3505`。测试：OriginRelayTargetTest `182/212/236/252/265`；OriginPostMergeRegressionTest `63/78`；OriginIsolationPlaybackTest `401`。
- 完整报告、红/绿命令、XML、源码/test 散列与最终 Git/guard 输出仅在 ignored `app/build/origin-isolation/file-manifest-review/report.md` / `final-evidence.log`；文档只追加本节，未改旧历史。
- 预算说明：初始10分钟目标在父明确扩界后改为约12:38；实际12:43自验结束，主要成本为有效红 NativeSQLite初始化4m22s、红包3m4s、最终双端Java/D8/定向测试/包8m34s，停止可选调查、未削验收门。执行仍1/3，同假设未重复失败重试；有效红未重跑。
- 未验边界：TV设备、完整API24–27原源播放、Node runtime/libnode、阅读器/TMDB联网、无痕真实设备、租约目录清理、旧flags空集、多Range/新协议功能未扩验，不用局部通知/Room证明这些全通过。报告不再以旧文档“file未实现”作本轮末态。
- 恢复锚：HEAD仍 `9f63ddaff60acb8b4f4200822af8ea7f9a591888` / dev2；九授权文件已自验且冻结，guard `H-ORIGIN-POST-MERGE-20261111` 保持 active。未提交、未标签、未推送，未删除用户数据或改配置依赖；工作树留给复审，不在本轮关闭守卫。
- **唯一下一步：收取两轴独立复审。**

**复审返工 run 2/3（TV 等待态补齐，2026-11-11）**

- 父调度者复审并独立实读确认：TV `beginPlayerContentRequest` 置 `mPlaybackRequestActive=true` / `mPlaybackPlayerStarted=false`，原 false 拒绝分支只 invalidate+error，未清两字段；spinner 已被真实 hideProgress 收起，但等待态残留会挡 READY/陈旧进度收口。mobile 无这两字段，本轮不动 mobile 或全局 invalidate 语义。
- 在既有 testLeanback/OriginPostMergeRegressionTest 新增真实 VideoActivity/getPlayer 拒绝用例（复用仓库实际 Activity 构建夹具，只隔离无关 artwork/surface；状态、requestPlay(false)、错误/收圈均执行生产方法）。首次 SDK28 在 PlayerView 的 `exo_icon_vr.png` ImageDecoder `(0,0)` 失败，记录 `tv-pending-red.log/xml`，未到拒绝分支、不算业务红；改为原夹具既定 SDK27。
- 有效红命令：`G:\Git\bin\bash.exe ./gradlew --offline :app:testLeanbackArm64_v8aDebugUnitTest --tests '*OriginPostMergeRegressionTest.tvReleasedOriginRejectionClearsWaitingFlagsAndShowsError'`。`tv-pending-red2.log` exit1 / 44s，`tv-pending-red2.xml` **1 test/1 failure/0 errors**：错误 view VISIBLE、进度 GONE 的断言已过，实际 `ORIGIN_TV_REJECTION_STATE active=true started=false error=原源会话已结束，请返回后重新打开 progress=8`，准确失败于 TV rejected request must clear the real in-flight flag。
- 最小产品修复只有 TV false 分支增加 `mPlaybackRequestActive=false`、`mPlaybackPlayerStarted=false` 两行，置于 showOriginError 前；其后 showError→hideProgress 仍为最终可见态。不抽新 helper，不改任何其他产品路径；其他六份已冻结生产/测试内容保持不变。
- 绿命令：`G:\Git\bin\bash.exe ./gradlew --offline :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:testLeanbackArm64_v8aDebugUnitTest --tests '*OriginPostMergeRegressionTest.tvReleasedOriginRejectionClearsWaitingFlagsAndShowsError'`。`tv-pending-green.log` **exit0 / BUILD SUCCESSFUL in40s**，leanback Java任务实际执行；`tv-pending-green.xml` **1/0/0**，真实 `active=false started=false`、错误 view VISIBLE、同文案、进度 GONE(8)。只重跑受影响单例，未重跑已绿 mobile42/leanback45、未重编 mobile、未构建 APK/跑设备；原3个 Node/无痕case的先前冻结绿XML保留，不冒称本轮新跑4个case。
- 本轮仅 TV/testLeanback 两文件与本段追加；新 hash/Git状态/guard证据 `app/build/origin-isolation/file-manifest-review/tv-pending-final-evidence.log`，前后XML及详细报告留同 ignored 目录。原13:08预计延后，真实 Activity 的 SDK28 图片解码环境绕行后已用 SDK27 收敛，未降低门槛、未无限补案例。剩余未验边界仍为 TV真机/完整原源设备播放等旧边界，不把 Robolectric view 状态说成 TV设备验证。guard 保持 active，HEAD/branch 不变，无 stage/commit/tag/push。
- **当前唯一下一步：收取两轴独立复审。**

### 最终复审通过与待发布状态（2026-11-11）

最终 Standards 与 Spec 两轴 run3 PASS（记录：`app/build/beta-review-dev2-20261111-standards-run3.md`、`app/build/beta-review-dev2-20261111-spec-run3.md`），调度者已实际读取并独立核实修复控制流、XML 与 mobile 真实绿；首审五类缺陷及后续 TV 标志残余均已修复闭环，无未处理新增实质问题。已有实际执行证据：mobile42/leanback45 联合单测全部 0 failure/error/skip，双端 Java 确实执行；TV1 Robolectric 拒绝状态红转绿、active/started 复位且错误可见、progress GONE；device1（5554）真实首帧、release→retry 与另一集错误可见、loading=false，无 current 搜索/取流。既有 merge 91 与调度者 mobile 38 验证保留，不将测试执行次数混称唯一用例数。本次仅记录既有验收证据，不重复构建、测试或复审。

代码已最终验收，当前处于本地闭合、待推送/待 PR 发布状态；本段不宣称已推送或已发布，实际提交、恢复标签、推送、beta PR 与最终远端同步结果由 PR 和最终输出承接。合并审查已覆盖旧本地 7 提交、beta 22 与 merge 共 30 提交、105 净路径（PR 54 路径）及历史 140 映射；beta 51 路径记录与正式提交 blob 等价已由调度者五组 git diff exit 0 核实，App 冲突保留双方 origin 分流与崩溃恢复语义。TV 真机、完整 API24–27、Node libnode、无痕实机、阅读器与 TMDB 联网未验；目录清理及旧 flags 空集已另立任务，不宣称全平台全部通过。

## 23. 发布期间 beta 缓存增量合并（2026-11-11，执行 1/3，待两轴增量复审）

- 本轮起点实际核验为 `dev2/HEAD=origin/dev2=df59c86c21629e372589d5f5191471847ecab954`、初始 dirty 0；已发布原源修复保留。已审旧 beta `4038aab74959f763a80cdc132a6da72b4a3e314c` 至冻结新 beta `c28572a0f0b415472c2f3b43b3f0ae17229ee76a`，实际新增 5 提交、15 路径（原源文档末尾接续为第 16 scope）。本轮没有 fetch 或更新源坐标，`origin=https://github.com/Silent1566/webhtv.git`；用户当前授权覆盖此前文档历史 stage 的“不 push”等限制，但本执行只留未提交 merge，不进行发布。
- 前一次守卫已经 finish，当前不存在旧 current 不是阻塞。先用真实 `G:\Git\bin\bash.exe` 启动 `task_guard start --id beta-resync-dev2-20261111 --mode standard`、精确 16 scope、保护初始脏文件 0，再执行 `git merge --no-ff --no-commit c28572a0f0b415472c2f3b43b3f0ae17229ee76a`：exit 0、无冲突、unmerged 0。没有整文件覆盖、业务修复、重写历史、stash/reset 或删除数据；没有 native/依赖增量，不重做其上游调研。
- 完整读入 beta 的 `docs/C128-upstream-sync-silent1566-cache-total-limit.md`，原文及外来历史不改。该 15 路径与正式已审 source `2caf5b746336b21080abf990c92cc097a9e1adce` 逐路径 blob 全部等价；正式源已有两轴最终实审、缓存失败状态 6 项真实红绿。当前结果树的 14 个非 service 路径又与冻结 beta 逐路径 `git diff --quiet` exit 0；不能把这些旧审查结论说成当前新合并树两轴已 PASS。
- `PlaybackService` 相对 df59 只新增 `CacheScheduler` import 和 `onDestroy` 中 `player.release()` 后的两行注释及 `CacheScheduler.get().onPlaybackEnded()`（4 新增行、0 删除）。删去精确这四行后与 df59 全文等价，原源 hold/release、无痕 ticker、keep-alive/替换检查及关闭顺序保持；`releaseOriginLease` 仍先于音频保存/terminal release，缓存钩子在 player release 后、removeForeground 前。上一原源守卫九路径中扣除本文件和允许交互的 service，实际 7 个其他生产/测试路径逐条相对 df59 diff exit 0；所有范围外原源变更也不在当前差异集合中，不凑“8”个计数。TV false 分支原 `mPlaybackRequestActive=false` / `mPlaybackPlayerStarted=false` 两行仍在 3507/3508。
- 唯一联合验证：`.\gradlew.bat --offline --no-build-cache --console=plain :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.cache.PlaybackCacheBudgetTest' --tests 'com.fongmi.android.tv.cache.TotalLimitConvergencePlanTest' --tests 'com.fongmi.android.tv.cache.CacheSchedulerConvergenceTest' --tests 'com.fongmi.android.tv.playback.origin.OriginPostMergeRegressionTest'`。15:58:36–16:03:56 +08:00，exit 0 / `BUILD SUCCESSFUL in 5m 19s`；两条 Java 编译和 test 任务均实际执行，非 UP-TO-DATE。`--no-build-cache` 仅避开 C128 已证实的跨工作区共享缓存锁，不改全局配置；JDK21 通过现有 JAVA_HOME，warm Robolectric 依赖正常，没有下载/安装新环境。
- 新鲜 XML 实读且原样快照：`PlaybackCacheBudgetTest` 14、`TotalLimitConvergencePlanTest` 7、`CacheSchedulerConvergenceTest` 18，缓存合计 39；`OriginPostMergeRegressionTest` 4（Node SDK24/25、TV SDK27、无痕 SDK28）。总计 **43 tests / 0 failures / 0 errors / 0 skipped**，不是预期或历史 XML 冒报。4186 个 `app/src` / `catvod/src` 已跟踪源码文件测试前后 SHA256 清单完全一致，清单 hash 均 `758D4524B80332F53ECEF0CAFED7CC8607C1EF108BEED0005E9E777B5DA089D7`；文档追加在测试后，不改变产品源码。
- 完整日志、15 路径双方 blob 表、原源保护、source_before/after、XML 快照和准确计数仅保存在 ignored `app/build/beta-resync-dev2-20261111/`。未重复其他已绿 87、全缓存/双端全部单测矩阵；未进行 native 重建、实机、镜像下载或新协议/性能压力验证。C128 已记录的真实缓存 owner 写入竞态/性能等旧边界仍保留，本轮未发现并自行修复新缓存业务缺陷；不以本次 JVM/编译结果证明这些边界。
- 当前恢复锚：`HEAD=df59c86c21629e372589d5f5191471847ecab954`、`MERGE_HEAD=c28572a0f0b415472c2f3b43b3f0ae17229ee76a`、`guard=beta-resync-dev2-20261111 active`；暂存仅 15 merge 产物，本接续末节未暂存，总变化只在授权 16 scope。未 commit/tag/push/PR，未 finish 守卫；本次新缓存简报执行 1/3、追踪编辑 16 路径累计（merge 15 + 本末节），其他生产编辑 0。
- **唯一下一步：由调度者收取 Standards / Spec 两轴对当前未提交合并树的独立增量复审，重点复核 service 并集合流及原源保护；复审前不发布，之后最终发布才更新同 PR555。**

## 24. beta 缓存增量合并发布记录（2026-11-11）

- 两轴增量复审已对当前未提交合并树 PASS（`app/build/beta-resync-dev2-20261111/standards.md`、`spec.md`），重点复核 service 并集与原源保护：`PlaybackService` 相对 df59 仅 +4/−0（`CacheScheduler` import、`onDestroy` 中 player.release() 后两行注释及 `CacheScheduler.get().onPlaybackEnded()`），原源 7 个其他生产/测试路径逐条 diff exit 0。
- 唯一联合验证 exit 0 / `BUILD SUCCESSFUL in 5m 19s`，双端 Java 实际执行；新鲜 XML 43 tests（PlaybackCacheBudget 14 / TotalLimitConvergencePlan 7 / CacheSchedulerConvergence 18 / OriginPostMergeRegression 4），0 failure / 0 error / 0 skip；4186 已跟踪源码测试前后 SHA256 一致。
- 本节只记录既有实际证据，不重跑构建、测试或复审。旧 beta `4038aab74959f763a80cdc132a6da72b4a3e314c` → 新 beta `c28572a0f0b415472c2f3b43b3f0ae17229ee76a`，5 提交 / 15 路径；本文件接续为第 16 scope。
- 提交、恢复标签、dev2 推送、PR555 正文更新与最终远端同步由本轮 `task_guard finish` 和后续发布命令承接；本节落盘时仍未 commit（15 个非本文件路径已暂存、本节未暂存），未推送、未改 PR。
- 未验边界保持：真机缓存收敛效果、缓存 owner 写入竞态、性能压力、TV 真机 / API24–27 / Node libnode / 无痕实机 / 阅读器 / TMDB 联网；目录清理与旧 flags 空集另立任务。
