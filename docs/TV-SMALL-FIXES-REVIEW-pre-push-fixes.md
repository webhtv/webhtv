# TV-SMALL-FIXES-REVIEW — 未推送批次的推送前独立评审与修复

## 范围与对象

- 2026-11-09 Asia/Shanghai，原 root `F:\Workspace\fish2018\webhtv`，分支 `Silent1566`，评审时 HEAD `9e070fe66b1bc9083a20b754e0bb772eaa1d80ac`（领先 `origin/Silent1566` 13 个提交）。
- 评审对象：`origin/Silent1566..HEAD` 的 13 个未推送提交（28 文件，+4358/−100），即 TV 按键 seek 归属、弹幕自动搜索归属、crawler 初始化容错、STRM 指针解析、QuickJS 代理响应头五批改动。工作区当时干净（唯一未跟踪项为构建产物 `app/.cxx/`）。
- 评审方式：三个只读评审子代理并行（TV seek / 弹幕归属 / loader+strm+proxy），父代理独立复核关键结论后再决定修复面。子代理不运行构建、不改文件、不做 git 写。
- 结论：共 12 条缺陷/风险成立并已修（其中 3 条为阻塞级：切清晰度后自动弹幕整段会话丢失、STRM `url|headers` 与中文路径指针回归、手动选择弹幕被在飞自动结果覆盖）；4 条判定为不修或留作未验边界，理由见下。

## 缺陷与修复对照

| # | 严重度 | 位置 | 缺陷 | 修法 |
|---|---|---|---|---|
| 1 | 阻塞 | leanback/mobile `VideoActivity.onItemClick(Result)` | 切清晰度入口不取消 seek、也不重发自动弹幕：`startPlayer` 覆盖层先 invalidate 掉 token，新会话 trace 无 owner，`renewAutoDanmaku` 因 token 为 null 直接返回 → 整段会话再无自动弹幕 | 与 `setPlayer` 同构：先取 `player()`/`beforeTrace` → `startPlayer(...)` → `searchAutoDanmaku(result, manager, beforeTrace)`；leanback 另补 `cancelKeySeek()` |
| 2 | 阻塞 | `Strm.parseTarget`（HTTP 指针） | 硬 `new URI(line)` 语法门把 `|`、空格、`{}`、`\`、`^`、`"`、非 ASCII（中文路径）判为非法 → 回退成 `.strm` 自身 URL；`url|User-Agent=...` 在本仓库是一等输入，旧实现原样返回首行 | 严格 URI 判据保留为第一判据；仅在 `URISyntaxException` 时做"容忍变形"复核（截掉首个 `|` 之后、`>U+007F` 替换成 `a`）再套同一判据，命中返回原整行，否则仍回退 |
| 3 | 阻塞 | `DanmakuApi` + 双端 `VideoActivity` | 自动搜索改用独立 ownerTag 后，弹幕搜索对话框的 `DanmakuApi.cancel()` 不再取消在飞自动请求 → 慢响应时手动选择的弹幕会被自动结果覆盖 | 无参 `cancel()`（对话框用）= 默认 tag + 全部 owned；新增 `cancel(String ownerTag)`（Activity onDestroy 用）= 默认 tag + 本页 owned，保住跨页面隔离 |
| 4 | 应修 | `CustomKeyDownVod.SeekSession.onUp` | `!owns(event)` 直接 return，使"无配对 DOWN 的左右 UP"失效，回归了旧实现的开播窗口/混键场景下直播切线路 | `!owns` 且 `!pending` 时回调 `onSeekUnavailable(event)`（VOD/Cast 默认 no-op，Live 映射为上下切线路） |
| 5 | 应修 | `LiveActivity.canSeek()` | 混入 UI 门 `dispatch(true)`，UP 之后 250ms 复查会在面板恰好出现时静默丢弃合法的时移 seek | 删除该条件（面板可见性已由 `CustomKeyDownLive.onKeyDown` 在 DOWN 时判过） |
| 6 | 中 | `ProxyResponse.fromJson` | 只过滤 Content-Type/Content-Length/Transfer-Encoding；但正文是已解码 String，站点回显 `Content-Encoding: gzip` 会让本地 `/proxy` 声明 gzip 却发明文 | 过滤集合补 `Content-Encoding`（大小写不敏感），其余头继续透传 |
| 7 | 中 | `Strm.http` | 无 `Content-Length` 的非指针响应会被 `peekBody(64KiB)` 阻塞读（旧实现此时不读正文），开局最多多等一个读超时 | peek 前加内容类型门：`video/`、`audio/` 前缀或含 `mpegurl`、`dash+xml` 时直接回退，不 peek |
| 8 | 低 | `quickjs Spider.destroy()` | init 早期失败（`jsObject == null`）时仍 `call("destroy")` → Async 内 NPE → `ExecutionException` 噪声打印在真正的初始化错误之前 | `jsObject == null` 跳过站点 destroy，直接走 `releaseJS()` + `executor.shutdownNow()` |
| 9 | 低 | `ProxyResponse.fromJson` | 顶层非对象 JSON（`[]`/`123`/`"x"`）漏出 Gson 的 `JsonSyntaxException` | 显式 `element == null || !element.isJsonObject()` → 受控 `IllegalArgumentException` |
| 10 | 低 | 双端 `renewAutoDanmaku` | leanback 与 mobile 分支不一致（leanback trace 相等即返回，坏 URL 下永久不再重试） | 统一为 mobile 版本：trace 相等且 call 在飞才返回，否则按 `PlaybackTrace.NONE` 重搜 |
| 11 | 低 | 双端 `ownsAutoDanmaku`/`searchAutoDanmaku` | `getEpisode()` 多次解引用且发生在新增的异步路径（`App.post`、`onStateChanged`），mobile 侧 `getEpisode()` 可返回 null | 取局部变量判空后复用 |
| 12 | 低 | 双端 `searchAutoDanmaku`、`RefreshEvent.Type.DANMAKU` | 方法自身不取消已有 Call；手动应用的弹幕不失效在飞自动结果 | 方法入口 cancel 旧 Call；DANMAKU 刷新事件在 `reloadDanmaku` 前 `invalidateAutoDanmaku()` |

## 判定不修 / 留作未验边界

- **`ACTION_CANCEL` 处理**：`KeyEvent` 没有 `ACTION_CANCEL`，系统取消语义以带 `FLAG_CANCELED` 的 UP 交付，现有 `event.isCanceled()` 分支已覆盖，不新增分支。
- **`TvSeekOwnershipIntegrationTest` 的 TestLive 未实例化、mobile 无 Robolectric 覆盖**：Live 即时切线路与时移 seek、mobile `onItemClick(Result)` 只有单测级/静态推演证据，本轮不扩测试基础设施（缺 mobile Robolectric 依赖，超出本任务范围）。
- **`DanmakuApi.search` 已无调用者**：属既有 public API，非本任务清理范围，保留。
- **STRM 容忍路径对非 http(s) scheme 更宽松**（如 `rtsp://h/a|UA`）：这是保留 strict-first 后的必然副作用，未加 scheme 白名单，接受并记录。

## 第二轮评审（修复后复审）

- 独立复审对象为工作区未提交 diff，结论：**无阻塞级新缺陷**，11 个核对点逐条通过。
- 采纳的建议项：`DanmakuApi.cancel(String ownerTag)` 增加 `TextUtils.isEmpty` 守卫（catvod `OkHttp.cancel` 内部对 tag 做 equals，null 会 NPE）；`Strm` 媒体类型门收紧为"仅在 `contentLength() < 0`（长度未知）时生效"，已知长度的 `.strm` 指针响应继续照常 peek 解析。
- 明确不改：未配对 UP 分支不额外排除 `event.isCanceled()`（旧实现对带取消标记的 UP 同样切线路，改动会反向偏离保真目标，仅记录）；`RefreshEvent.Type.DANMAKU` 失效路径本轮不补专测；`Strm` 容忍路径对任意绝对 scheme 的宽松度与 strict 门一致，非本批新增面，仅记录。
- 复审提出的文档问题（4 份 C-TV 文档仍写"未测、待父补"）已按真实证据更正，未改缺陷与修法描述。

## 验证证据（2026-11-09，Asia/Shanghai，父代理执行）

- 两次联合运行，最终态一次为决定性证据：`gradlew.bat --console=plain :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:testLeanbackArm64_v8aDebugUnitTest --tests <6 类>` → **BUILD SUCCESSFUL in 3m46s**（首轮修复后为 7m29s）；mobile 与 leanback 主源集、`compileLeanbackArm64_v8aDebugUnitTestJavaWithJavac` 全部通过。
- 测试结果 XML：6 类共 **81 tests / 0 failures / 0 errors / 0 skipped**——`DanmakuApiOwnershipTest` 3、`StrmHttpTest` 20、`TvSeekOwnershipIntegrationTest` 5、`VideoDanmakuOwnershipTest` 22、`CustomKeyDownSeekOwnershipTest` 17、`ProxyResponseCompatibilityTest` 14。
- 新增回归覆盖：切清晰度后按新 trace 重搜弹幕（`qualitySwitchSearchesAutomaticDanmakuOnItsNewTrace`）、无配对直播 UP 仍切线路（`unpairedLiveReleaseStillSwitchesLineWithoutSeek`）、`cancel(ownerTag)` 只取消本页（`pageScopedCleanupCancelsOnlyItsOwnAutomaticCall`）、`url|headers` 与中文路径指针（`headerSuffixedPointersKeepTheirWholeLine`/`nonAsciiPointersKeepTheirWholeLine`）、未知长度媒体响应不 peek 与已知长度仍解析（`mediaResponsesAreNeverTreatedAsPointers`/`knownLengthMediaResponsesStillResolveTheirPointer`）、Content-Encoding 丢弃与非对象 JSON 拒绝。

## 未验边界

- 设备/真机未执行：Exo/MPV 实机 seek、直播即时切线路与时移、慢响应弹幕覆盖场景、Mobile 布局与真实消费者、ARM/native 解码实播、PiP/后台。
- 未执行全量 Gradle 矩阵、其它 ABI/flavor、`connectedAndroidTest`、APK 打包与安装。
- QuickJS/Python 真桥下的站点 `destroy` 清理、站点回显 `Content-Encoding: gzip` 的端到端播放未实测。

## 回滚

- 本次修复为单一原子提交并打恢复标签；`git revert <本提交完整 hash>` 可整体撤销。恢复标签只标已提交且已验证的状态，未获授权不 push、不移动已发布标签。
