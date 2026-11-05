# danmaku-bundle-endpoint（本地包内置弹幕服务自动接入）

任务 ID：`danmaku-bundle-endpoint`　分支：`dev2`　基线 HEAD：`03958c154df74c448fff489d22da781bfafe204f`（Merge pull request #518 from Silent1566/dev5，已合并的 origin/beta）

## Recovery anchor

- 目标：本地包（bundle）源下，猫源 runtime 自带的弹幕 HTTP 服务无需用户手填地址即可生效；换源后不残留上一代 runtime 的弹幕地址；推送源历史标题不被裸源名覆盖；弹幕搜索标题不被分类标签污染。
- 验收：`git diff` 只含本文件列出的 13 个路径；leanback/mobile 两个 mode 变体的单测全绿（`DanmakuBundleEndpointSourceTest` / `MediaTitleParserTest` / `GlobalHistoryResumeSourceTest`）；双轴（Standards / Spec）评审两轮通过。
- 下一步：无（见「交付坐标」）。

## 用户可见能力

本地包源（`CatSource.isBundle(VodConfig.get().getConfig().getUrl())` 为真）启动后，猫源本体监听在一个回环端口，它自带的弹幕服务另外监听第二个回环端口。此前用户必须自己知道 `http://127.0.0.1:<端口>/danmaku` 并手填进设置才有弹幕；本改动在后台探测到该端口后把它作为回退地址，本地包开箱即有弹幕。

## 改动内容

1. `app/src/main/java/com/fongmi/android/tv/node/NodeRuntime.java`
   - 新增 `danmakuPort`、`lastDanmakuPort`、`DANMAKU_PROBING_GENERATION`、`DANMAKU_PROBE_TIMEOUT_MS`(20s)、`DANMAKU_PROBE_POLL_MS`(500ms)。
   - 新增 `danmakuApiUrl()`：端口无效、runtime 未 running、或子进程已死时返回空串。
   - 新增 `resolveDanmakuPort(int catPort, long generation)`：后台轮询 bundle `port` 文件里的候选端口，跳过猫源本体端口，用 `GET /danmaku` 回 200 且 body 是 `[`/`{` 开头确认；每轮与写回前都核对 `START_GENERATION`。在飞去重按**世代号**（`claimDanmakuProbe`/`releaseDanmakuProbe`）而不是「有无线程」，否则快速换源时上一代还活着的线程会把新一代唯一的触发点挡回去，新 bundle 永久探不到。
   - 新增 `ownsLoopbackPort(String url)`：判断一个回环地址是不是本 runtime **现在或曾经**开出的端口（`port` / `danmakuPort` / `lastDanmakuPort`）。
   - `loopbackUrl(int, String)` 统一回环地址拼法；`clearPort()` 同时清 `port` 与 `danmakuPort`（**故意不清 `lastDanmakuPort`**），替换原先散落的 `port = 0;`；`MSG_READY` 就绪后触发探测，`MSG_ERROR` 清端口，`start()` 复用未死进程时补探一次。
2. `app/src/main/java/com/fongmi/android/tv/player/PlayerManager.java`：新增 `dropStaleBundleDanmaku()`；`isStaleBundleDanmaku(url)` 按顺序判：地址为空 → 否；等于 `getConfiguredApiUrl()` → 否；不是回环地址 → 否；`DanmakuSetting.isBundleSource()`（当前订阅仍是本地包，**与运行时是否就绪无关**）→ 否；否则 `NodeRuntime.ownsLoopbackPort(url)`。归属只认 runtime 真的开过的端口，**不用形态（回环 + path `/danmaku`）猜**：用户自建的回环弹幕服务形态相同，按形态猜会把他的话源当陈旧清掉。换源事件与 `restoreDanmakuDataSource`/`setDanmaku` 处各清理一次。
3. `app/src/main/java/com/fongmi/android/tv/setting/DanmakuSetting.java`：拆出 `getConfiguredApiUrl()`（只回用户配置/配置中心下发值）与 `getEffectiveApiUrl()`（配置为空时回退 `bundleApiUrl()`）；新增 `isBundleSource()`，`bundleApiUrl()` 委派它判门（`bundleApiUrl()` 自身保持 `private`，谓词只此一份）。
4. `app/src/{leanback,mobile}/java/com/fongmi/android/tv/ui/dialog/DanmakuApiDialog.java`：对话框回填改用 `getConfiguredApiUrl()`，避免把运行时回环地址固化进偏好。
5. `app/src/{leanback,mobile}/.../SettingDanmakuActivity.java` / `SettingDanmakuFragment.java`：自动弹幕开关可见性改为 `isLoad() && (hasValidApiUrl() || isBundleSource())`——引入回退地址后「有接口」不再等于「用户配过接口」，本地包用户在探测窗口期内也要看得见；同时非本地包且没配接口的用户（此时 `DanmakuApi.canSearch()` 恒为 false）仍不会看到一个无效开关。「蜘蛛优先」判据不变。
6. `app/src/{leanback,mobile}/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`：新增 `historyVodName(Vod)`，推送源优先 Intent 里的真实标题；所有历史标题写入点（详情水合、`createHistory`、`updateVod`、`initPlayer`）统一走它；`onConfigEvent` 里调用 `player().dropStaleBundleDanmaku()`。
7. `app/src/main/java/com/fongmi/android/tv/title/MediaTitleParser.java`：新增 `CATEGORY_LABELS`（分类/地区/题材词）、`EXTENSION_PATTERN`（原先 `cleanTitle` 里的内联扩展名正则，两处共用）与 `mainTitleWithoutCategoryLabels`，三档剥离括号：全剥 → 只留最靠后的一个可用非标签括号（`lastUsableBracketTitle`，跳过 `isCategoryLabel` 分类标签与 `isNoiseTag` 发行噪声）→ 返回空串。第二档不能整串返回，否则 `[北美票房][美国][阿凡达]` 会拼成「北美票房 阿凡达」；噪声词表同时补充 `完整版|未剪辑|未剪輯|双字|雙字|国英|國英|中英|票房`，否则 `[美国][阿凡达][完整版]` 的首选会变成「完整版」。
8. 测试：`DanmakuBundleEndpointSourceTest`（源码守卫）、`MediaTitleParserTest` 新增分类标签、括号里是片名、残留非标签括号、尾部发行噪声四例、`GlobalHistoryResumeSourceTest` 断言历史标题走 `historyVodName`。

## 评审与修复记录

- 第 1 轮双轴评审（Standards 10 项 / 必修 1，Spec 7 项 / 必修 2）后合并为两批修复：A 批（NodeRuntime / PlayerManager / DanmakuSetting / 源码守卫测试）、B 批（两处 VideoActivity / MediaTitleParser / 其测试）。
- 我判定「设置页两个开关判据不一致」不成立，未修：自动弹幕开关按 `isLoad()`、蜘蛛优先按 `isLoad()+hasValidApiUrl()` 是引入回退地址后想要的层次差。
- 第 2 轮复评（Standards 12 项 / 必修 1，Spec 6 项 / 必修 1，首轮 5 项全部 closed）暴露两处必修：探测的在飞去重会吞掉新一代唯一触发点（两轴共识）；残留判据问的是「用户有没有配置」而不是「该地址是不是当前配置的那个」。
- 修复 C 我两次驳回重做：第一版把 `AtomicBoolean DANMAKU_PROBING` 换成按世代号认领；第二版发现判据仍有两个洞——本地包仍是当前源时其自身 `ws://127.0.0.1:<端口>/live` 会被 `ownsLoopbackPort` 命中而误清（功能自坏），换源 `clearPort()` 清零后又完全认不出残留（原缺陷复现）。最终引入 `lastDanmakuPort`（`clearPort()` 不清）+ `hasBundleDanmaku()`，并前移「不是回环地址 → 否」这一层，让自建回环弹幕源（`ws://127.0.0.1:5266/live`，见 `DanmakuUrlPolicyTest`）无条件安全。
- 修复 D 的一处偏离已核实：简报要求第二档作废返回空串，实测 `bestBracketTitle` 的 `titleScore` 只会选出「北美票房」（4 汉字 404 分 > 3 汉字 303 分），故第二档改为「只留最靠后的一个可用非标签括号」，既去掉粘连又不丢片名。
- 第 3 轮复评（Spec 4 项 / 必修 1，Standards 见下）再修三批：E 批删掉按端点形态猜归属的 `isBundleDanmakuEndpoint`（形态不是归属证明，会把用户自建的回环弹幕服务误清），并把「本地包是否仍是当前源」由 `hasBundleDanmaku()`（探测窗口期内为 false）改成 `isBundleSource()`；F 批让第二档同时跳过发行噪声（`isNoiseTag` + 词表补 `完整版|未剪辑|双剪…`），修 `[美国][阿凡达][完整版]` 首选变「完整版」；G 批恢复非本地包用户的开关不一致（`isLoad() && (hasValidApiUrl() || isBundleSource())`）。
- 第 3 轮 Standards（7 项 / 必修 0）后我只修一项：`GlobalHistoryResumeSourceTest` 新增的三条断言原本在整个文件上搜字符串（`source.contains(...)`），helper 被删或被改空也会通过；已切到 `historyVodName` 方法体内。其余主张按既有约定不改——`DanmakuBundleEndpointSourceTest` 的源码文本断言形式在本仓有 50+ 同类先例、`bestBracketTitle`/`lastUsableBracketTitle` 的选择规则本就不同（后者还要跳过分类标签与发行噪声）、`PlayerManager.isStaleBundleDanmaku` 每次求值解析三次 URL 只在切源路径上发生。
- 第 4 轮复评（Spec 7/7 CLEAN、必修 0；Standards 6 项、必修 0）后修三处：「bundleApiUrl()」原本重抄了一遍 `CatSource.isBundle(VodConfig...getUrl())` 谓词（与 `isBundleSource()` 重复、会各自漂移），改为委派 `isBundleSource()`；`DanmakuBundleEndpointSourceTest` 的 accessor 切片终点原锚在一句 javadoc 上（会顺带吞掉 `ownsLoopbackPort()`），改锚方法签名，并同步 `bundleApiUrl()` 委派后的断言；`isCategoryLabel` 里的 `count` 是死代码，删除。
- 第 5 轮自审修正：删 `count` 时我最初直接 `return true;`，自查发现**不等价**——`value` 非空但全由分隔符组成（如 `[/ /]`）时一个 token 都不会被匹配，原实现返回 false，改后返回 true，会让这类括号被误判成分类标签而多剥一层。改为局部标志 `labeled` 并补注释说明，双变体单测复跑通过。
- 第 4 轮 Spec 提出的「端口晚绑定不会补发弹幕搜索」我未修，落为此处的已知风险条目（见下）；「设置页判据收窄」不成立——改动前就是 `isLoad() && hasValidApiUrl()`，现为它的超集，无回归。
- 有意不改（各留一句理由）：`start()` 复用捷径每次重探 20s——`danmakuPort == 0` 前置已挡住重复探测，冷启动晚绑定重试正是该功能目的；`serviceAlive()` 在 UI 线程调 `getRunningAppProcesses()`——Android 8+ 只返回本应用进程，成本可接受；两份 flavor 的 `historyVodName` 重复——需要挪到 `app/src/main/.../ui/helper`，超出本任务守卫 scope，留作后续独立任务；`lastUsableBracketTitle` 与 `bestBracketTitle` 的括号遍历未合并成一个 `forEachUsableBracket`——两者跳过集与选取规则不同（后者还要跳分类标签与发行噪声），合并会把两套规则耦在一起；两 flavor 三处「有可用弹幕」表达式未抽成 `hasUsableApiUrl()`——属可选重构，且 `canSearch()` 的语义（还要 `isAuto()`）并不相同。

## 验证

命令（本机 gradle 必须走 `cmd /c .\gradlew.bat`，`bash ./gradlew` 会因 WSL 不可用失败）：

```
cmd /c ".\gradlew.bat --console=plain :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.setting.DanmakuBundleEndpointSourceTest --tests com.fongmi.android.tv.title.MediaTitleParserTest --tests com.fongmi.android.tv.history.GlobalHistoryResumeSourceTest :app:testMobileArm64_v8aDebugUnitTest --tests <同上三类>"
```

结果（第 4 轮修复与 `isCategoryLabel` 等价性修正之后的最终一轮）：`BUILD SUCCESSFUL in 20s`（前一轮全量执行为 26s）。测试结果 XML（`app/build/test-results/`）leanback 与 mobile 各一份，两份计数一致：

| 测试类 | 用例数 | 失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- |
| `com.fongmi.android.tv.setting.DanmakuBundleEndpointSourceTest` | 7 | 0 | 0 | 0 |
| `com.fongmi.android.tv.title.MediaTitleParserTest` | 18 | 0 | 0 | 0 |
| `com.fongmi.android.tv.history.GlobalHistoryResumeSourceTest` | 31 | 0 | 0 | 0 |

另：`MediaTitleParser` 的行为改动除了 JUnit 之外，还用 JDK21 直接跑过真实样例，`[电影] 小丑回魂：德里故事 (2026) [...] [美国 恐怖 奇幻]` → `小丑回魂：德里故事`、`[美国][恐怖][我的电影].mkv` → `我的电影`、`[北美票房][美国][阿凡达].mkv` → `阿凡达`、`[玩偶][云秀行][01][4K].mkv` → `云秀行`。`[恐怖].mkv` → `恐怖` 是 `bestBracketTitle` 的既有盲区（改动前后行为一致），未在本次范围内改动。

## 已知风险与未处理项

- `bundleApiUrl()` 依赖 `NodeRuntime.danmakuApiUrl()` 的实时状态：runtime 未 running 或子进程已死时回退地址为空，此时即使 bundle 的弹幕服务仍在监听也不会被使用。这是刻意选择（宁可没有弹幕，也不把死端口交给搜索）。
- 探测预算 20s 是经验值；若某设备的 bundle 弹幕服务起得比这更晚，本场将没有自动弹幕，需要用户手填或重进。
- 端口晚绑定时不会自动重发弹幕搜索：`DanmakuApi.canAutoSearch()` 在 `startPlayer` 处求值一次（leanback `VideoActivity.java:3416`、mobile `:3230`），若那一刻 `danmakuPort` 还是 0 就跳过本次自动搜索，之后探测成功也不会补发，重进本集即可恢复。实际影响很窄：片单本身要走 node runtime 才能取到，所以 `MSG_READY` → 起播之间用户还要点选剧集，探测（每个候选约 1s）通常早已完成。要彻底消除需要给 `NodeRuntime` 加一条解析完成的回调并接到两个 flavor 的播放页，属跨模块新接口，超出本任务 scope，留作后续独立任务。
- `MediaTitleParser` 的 `CATEGORY_LABELS` 是词表启发式，新增的片名若恰好等于分类词会被误剥（第二档会把它救回，如 `[恐怖].mkv` 由 `bestBracketTitle` 兜底）。
- `lastDanmakuPort` 故意不随 `clearPort()` 清零，进程内会永久保留一个端口号：若系统把同一端口号重新分配给别的本地服务，`ownsLoopbackPort` 会误判成残留并清掉那一路弹幕。取舍理由：误清的代价是切源后丢一次弹幕，而不记端口号的代价是上一代本地包的回环地址永久留在屏幕上。

## 交付坐标

- 提交：见本任务 `recovery/danmaku-bundle-endpoint/<timestamp>` 注解标签。
- 验证证据：`BUILD SUCCESSFUL`（leanback/mobile arm64 debug 单测，56 用例 × 2 变体全绿：31 + 18 + 7）。
- 回滚：`git revert` 本任务提交即可；改动全部是新增方法 + 调用点替换，无数据迁移、无偏好键变更（`danmaku_auto` 默认值保持与基线一致）。
