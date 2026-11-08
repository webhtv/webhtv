# C45：T3 → T4 本机接口网关

## Recovery anchor

- **目标**：只读分析用户提供的 APK 与实验室离线包，在 WebHTV 中提供当前已加载站源的 T3 → T4 HTTP 接口。
- **任务/分支**：`C45-t4-api-gateway` / `standard` / `dev3`，沿用原活动任务守卫；原 provider 会话与 Orca pane 仅作只读背景，不恢复、不修改。
- **基线**：`f85398d3598193f70af1c2f998d41f0d4f8e02da`。原守卫启动时工作区干净；接续时的 Nano、VodApi、测试与脚本改动均为本任务遗留，不重置。
- **范围**：`app/src/main/java/com/fongmi/android/tv/server/Nano.java`、`app/src/main/java/com/fongmi/android/tv/server/process/VodApi.java`、`app/src/main/assets/VodPlus/EnvFiles/T4Proxy.js`、`app/src/test/java/com/fongmi/android/tv/server/process/VodApiTest.java`、本文件。原守卫还包含 `SpiderApi.java`，本任务没有修改它。
- **状态**：实现与自动化验收完成；Java 31 项、Node 10 个 HTTP 场景通过。构建输出已清理约 290 MiB。代码与本记录在一个守卫提交内关闭，不推送。
- **提交/恢复标签**：本文件所在的 C45 实现提交；唯一 annotated 标签由守卫生成于 `recovery/C45-t4-api-gateway/` 下。以 Git 和守卫输出为准，不在提交前编造 hash。
- **验收边界**：已验证真实 HTTP socket 与可控 Spider fixture，不等同于任意第三方站源的实机播放。没有打 APK、安装/卸载应用或操作模拟器。
- **唯一下一动作**：执行守卫 `finish` 原子提交并打恢复标签，然后交付下述配置地址；不重复已通过的测试。

## 1. 原 APK 与实验室确实提供了什么

**结论：配套实验室确实具有 T3 → T4 转换能力，核心是 HTTP 代理适配，不是重新实现爬虫。**

- 离线包 `G:\下载\lab离线包\EnvFiles\T4Proxy.js` 调用 App 的 `/spider`、`/spider/config`，输出 T4 配置和 `page/pagecount/limit/total/list/class/filters` 数据。它的源地址硬编码为 `192.168.10.36:9978`，不能原样沿用。
- APK 中有 `assets/VodPlus/wwwroot/vod.php`（29,020 字节），与本仓库既有同路径文件逐字节一致。它提供 T4 风格参数到 Spider API 的转发；不据此宣称 PHP 的全部返回细节与 Node 实现完全等价。
- APK DEX 字符串中发现 `/spider`、`/spider/config`，未发现 `/vod/api`；这是静态线索，不是 APK 运行时能力的完整证明。
- APK 内未找到 `T4Proxy.js`；脚本位于用户给定的独立离线包。WebHTV 已有 `SpiderApi` 和实验室模板命令，但缺少本任务新增的内置 T4 网关。
- `lab_template.json` 的脚本下载地址是 `/file/WebHTV/EnvFiles/T4Proxy.js`。仅添加 asset 不会自动把它复制到外部目录，因此本次在 Nano 对该精确地址增加了缺文件时的 asset 回退；已存在的用户脚本仍优先。

### 制品证据（2026-10-08 读取）

| 制品 | SHA-256 |
| --- | --- |
| `G:\下载\影视+1.2.3实验室.apk`，92,345,077 字节 | `4d656045d4f5620734d82722bfe7f93845f0d6876b4e3827fb089ba5a7fac39a` |
| APK 的 `assets/VodPlus/wwwroot/vod.php` | `62cada19b60f4f519e4047c18425f6b67cdbf2dd4d0ca893790c1f9efecdfcb3` |
| 原离线包 `EnvFiles/T4Proxy.js`，15,640 字节 | `b6d61c832e242c42c8fd6486e9327eeb2f52f04c37b4cb7416526f395b934e01` |

没有执行原 APK 或离线脚本，没有修改这些用户输入文件。

## 2. 设计取舍与本地证据

### 比较

| 方案 | 结果 |
| --- | --- |
| 不改动 | 仍依赖用户安装实验室运行时和手动部署脚本，不提供开箱可用的内置 T4 地址。 |
| 原样搬入 Node/PHP | 地址硬编码；原脚本/示例的路由不完全一致；还需管理额外进程和端口。 |
| **内置网关 + 实验室兼容入口（采用）** | Nano 复用应用服务端口；T3 直接调用当前 Spider；远端源复用 HTTP 传输层；Node 入口转发到同一内置网关。 |

### 决策依据

以下源码均按基线 `f85398d3598193f70af1c2f998d41f0d4f8e02da` 读取，证据等级为 A（本地实现/给定制品），访问日期 2026-10-08。

- `SiteApi.categoryContentRaw()`：T4 分类请求发送 `ac=detail&t=...&pg=...&ext=...`。不能仅凭 `ac=detail` 判断详情；实际参数必须参与路由。
- `SiteApi.playerContent()`：T4 播放采用 `play=...&flag=...`。网关同时接受 `id` 别名。
- `SiteApi.detailContent()` / `Source.parse()`：会展开本机媒体、参与详情缓存。`playerContentIsolated()` 虽不 stop 全局 Source，仍会创建 extractor 并 fetch；不适合直接作为纯 HTTP 转换层。
- `SiteApi.call()`：可复用既有站源地址、header、extend 和 HTTP 请求方式，不引入新的网络栈。
- `BaseLoader.proxy()` / `JarLoader.proxy()` / `Spider.proxy()`：支持显式 `siteKey`，但旧 JAR 的集中代理仍依赖 JarLoader 兼容分派。因此仅对实现了 `Spider.proxy()` 的爬虫补 `siteKey`，其余保留旧分派；网关不显式调用 `Site.recent()`。
- `Result`、`Vod`、`Flag`、`Episode`：公共字段与宿主运行状态混在同一对象中。输出使用白名单；保留实际分页、过滤参数和原始播放提示，避免泄漏内部 Site/TMDB 对象。
- 当前 NanoHTTPD 2.3.1 的会话已经解码路径；站点 key 采用编码后的 query 地址生成，防止重复解码及 `+ / ? &` 丢失。

本次属于已有协议的适配和纠错：给定离线包没有可确认的发布 commit/PR，不能编造。没有发现需要引入上游 merge、依赖更新或新的播放器架构的依据。公开 PR/revert、性能论文对这一精确路由/数据契约无决定性作用；本地成熟消费者和实际 HTTP 测试是验收依据。本记录不声称全生态最佳实践、吞吐量优化或所有爬虫的兼容性已经被证明。

## 3. 使用方法与协议

先在提供服务的 WebHTV 中加载有效点播源，保持 App 服务运行。其他设备使用同一局域网可达的地址：

```text
http://<WebHTV设备IP>:9978/vod/api?ac=config
```

`9978` 是默认端口；如果应用使用其他端口，以应用实际服务地址为准。也支持 `ac=site`；返回的每个站点 `type` 为 `"4"`，`api` 自动生成成 `/vod/api?key=<编码后的站点key>`。

| 操作 | 参数（加在站点 api 后） |
| --- | --- |
| 首页 | 不加额外参数 |
| 分类 | `&ac=detail&t=<分类ID>&pg=2`，也可省略 `ac` |
| 详情 | `&ids=<视频ID>`；批量可逗号分隔或 POST JSON 数组，最多 100 项 |
| 搜索 | `&wd=<关键词>&pg=2&quick=true` |
| 播放 | `&play=<播放ID>&flag=<线路>`；也接受 `id` 和显式 `ac=play` |
| 筛选 | URL-safe Base64 `ext`、JSON 字符串/对象 `extend`、显式 `area/year/type/class/lang`，后者覆盖前者 |

保留 `/vod/api/<key>` 兼容入口。复杂站点 key 优先使用配置自动生成的 query 地址。请求支持 GET、POST JSON、HEAD；OPTIONS 返回 204 且不调用爬虫，不支持的方法返回 405。

### 数据与代理边界

- 列表保留源的分页值、分类和 filters；缺失 pagecount 时非空页使用兼容默认值，空页结束分页；total 使用 long 防溢出。
- 详情保留线路、集列表和空线路占位；不只返回批量请求中的首个 ID。
- 播放保留原始 URL/URL 数组、`parse/jx/playUrl`、请求头、字幕、DRM 等提示。**不是所有返回值都是已经嗅探完成的直链**；所需客户端解析能力仍由接收端承担。
- 仅外化当前 App 服务同端口的 loopback/同主机 URL。不会全局替换远端 URL 的签名 query，也不修改 Referer 等请求头或视频 ID。其他本机端口和插件内部清单的特殊地址不在此重写范围。
- 支持直接 Spider 代理的源可补 `siteKey`；旧 JAR 保持既有集中代理约定，不声称实现了多旧 JAR 的独立代理生命周期。
- 常规业务错误保留 `{"code":-1,"msg":"..."}` JSON；不向客户端回传一般内部异常堆栈。

### 可选实验室 Node 入口

```text
node T4Proxy.js --port 10998 --host http://127.0.0.1:9978
http://<Node所在设备IP>:10998/?ac=config
```

当前兼容脚本需要包含 `/vod/api` 的 WebHTV。它转发到内置网关，不再通过旧 `/spider` 重复实现分页和源类型适配。Node 本身不承载媒体流，客户端仍须能访问 App 的媒体代理端口。内置方式不需要安装 Node/PHP，也不自动启动这个额外进程。

## 4. 改动与兼容性

- `Nano.java`：新增网关注册及精确脚本 URL 的缺文件回退；其他路由不变。
- `VodApi.java`：请求路由、站源执行、公共字段转换、URL 外化、批量详情、筛选与方法/错误边界。
- `VodApiTest.java`：持久化转换与真实 HTTP→Spider fixture 回归。
- `T4Proxy.js`：保留实验室入口，转发内置网关；处理分页、编码、错误、超时/中断及输入大小；沿用现有 LAN CORS 约定。
- `SpiderApi.java`、`SiteApi.java`、`Source.java`、依赖/锁/native 二进制及应用设置没有修改。

安全边界是**可信局域网**：现有服务没有为本网关提供单独认证，不能直接暴露到公网。源内部仍可能要求登录、交互或特定客户端能力；本次不添加公网穿透、不读取或导出账户配置，也不宣称已为所有插件实现无交互服务化。没有新增 Java/Android 依赖，包体增量未通过 APK 构建测量。

## 5. 验证与回收（2026-10-08）

1. 接续时旧的 20 项转换测试通过，但没有覆盖真实 T4 参数。新增分类路由、相邻路径和 ext 用例后，3 项明确失败，作为纠错前证据。
2. 修复后第一次综合验证：Java 30 项通过；Node 10 个真实 HTTP fixture 场景通过，覆盖配置、首页、分类+ext、批量详情、分页搜索、远端播放地址、OPTIONS、非法 JSON、方法拒绝、原生网关转发。所有 Node 测试监听器已关闭。
3. 老式 JAR proxy 补保护后，最终命令通过：

```bash
export JAVA_HOME='/c/Program Files/Android/Android Studio/jbr'
export ANDROID_HOME='C:/Users/Maple/AppData/Local/Android/Sdk'
bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest \
  --tests com.fongmi.android.tv.server.process.VodApiTest \
  --no-daemon --max-workers=2 --console=plain
```

- **31 tests，0 skipped，0 failures，0 errors**；JUnit 时间 `2026-10-08T06:36:51.454Z`，测试用时 0.518 秒；Gradle `BUILD SUCCESSFUL in 2m 10s`。
- HTTP 测试使用真实 NanoHTTPD socket 和可控 Spider；测试覆写 `Site.recent()` 为失败，防止误切换加载器。服务和连接均在 finally/AutoCloseable 中关闭。
- 最终 `node --check`、`git diff --check` 通过；最后 Java LSP 对三个变更路径确认 0 diagnostics。没有把早期 LSP 超时当作成功证据。
- 原始最终证据保留于 `F:/temp/C45-final-test-2.log`、`F:/temp/C45-vodapi-final.xml`；不加入 Git。构建使用单次 daemon，按 `--no-daemon` 结束，不停止其他工作区的进程。
- 已删除本任务工作区的 `app/build`、`build`，回收约 290 MiB；共享 Gradle 依赖缓存保留。
- 没有进行 APK 构建、模拟器或真机测试。因此未声称验证了具体第三方 jar/js/py/php 资源的实播、所有插件或并发播放性能。

## 6. 提交与回滚

实现、测试、脚本和本记录由 C45 守卫原子提交，并立即创建唯一 annotated 本地恢复标签；不 push。

回滚应对该 C45 提交执行常规 `git revert`，恢复 Nano 注册/脚本回退并移除新增网关、测试和脚本；不要对工作区执行破坏性的 reset，也不要误删既有 `/spider` 或用户外置脚本。
