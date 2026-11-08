# NOVEL-TTS 小说模式朗读功能补全

任务 ID：`NOVEL-TTS`（新增产品需求，非上游合并阶段）
分支：`dev1`
唯一文档：本文件（研究、设计、实施、验证、回滚、状态、下一动作全部追加于此）

## 0. 目标与完成定义

目标（用户原始需求）：参考 `Luoyacheng/legado-E` 及 `gedoor/legado`（阅读）等项目的朗读实现，**补全 WebHTV 小说模式的朗读功能**。

完成定义（可验收）：

1. 小说阅读器（`assets/reader.html` + `WebReaderActivity`）点「🔊 朗读」后**能在真机出声朗读本章内容**，按段推进并跟随高亮。
2. 朗读控制完整：播放/暂停/继续、上一段、下一段、停止、语速、音调、音色/引擎选择、定时停止。
3. 语音来源至少覆盖：**系统 TTS**（Android `TextToSpeech`）与**在线 TTS**（无需申请 Key 的内置引擎），并支持**自定义在线朗读引擎**（与 legado 在线朗读引擎格式兼容）。
4. 章末自动续读下一章；退出阅读页即停止，不留后台残留播放。
5. 切到后台/锁屏后朗读不中断，并在通知栏提供逐段控制（对齐 legado 听书的行为）。
6. 具备定向单元/契约测试，并在指定设备（`192.168.50.3:5555`，LIO-AN00 / Android 9 / API 28）上完成实机验证。

## 1. 现状审查（WebHTV 当前实现，具体文件与符号）

| 位置 | 现状 |
| --- | --- |
| `app/src/main/assets/reader.html:526,569,631-655` | 已存在朗读 UI：顶部 `#btnTtsTop`、底部 `#btnTtsEntry`、`#ttsPanel`（音源三元 pill：`system`/`tencent`/`microsoft`、`#ttsStatus`、`#btnTtsPrev/#btnTtsPlay/#btnTtsNext/#btnTtsStop`、`#seekTtsSpeed`、`#btnTtsFollow`）。 |
| `reader.html:1423-1578` | 朗读脚本 `ttsStart/ttsPause/ttsResume/ttsStop/ttsSpeak` 等。**主路径调用 WebView 的 `speechSynthesis`**；`ttsNativeAvailable()` 仅在 `ttsSource==='tencent'` 时调用 `AndroidReader.ttsSpeak/ttsPrefetch/ttsStop`；云语音分支只有提示文案「微软云语音需在原生端配置，暂用系统语音」。 |
| `WebReaderActivity.java:1108+` | JS 桥只实现 `back/toast/saveProgress/loadChapter` 等；**没有 `ttsSpeak/ttsPrefetch/ttsStop` 等任何 TTS 接口**，也没有任何 `android.speech.tts` 引用（全仓库 `rg "TextToSpeech"` 无命中）。 |
| 全仓库 | 无 TTS 引擎层、无朗读服务、无在线朗读引擎配置/持久化。 |

结论：朗读 UI 是**空壳**。`system` 与 `microsoft` 两条路径在 Android 上必然无效，`tencent` 路径调用的原生接口不存在（`typeof AndroidReader.ttsSpeak === 'function'` 恒为 false，静默回落到 `speechSynthesis`，同样无效）。

### 1.1 根因证据（为什么在 Android 上「点了没声音」）

- Android System WebView **不实现 Web Speech API 的 `speechSynthesis`**（只有 Chrome/Chrome for Android 实现）。因此 `reader.html` 里 `new SpeechSynthesisUtterance(...)` + `speechSynthesis.speak()` 在 WebView 中不产生任何音频，`getVoices()` 为空数组。
  证据：Chromium issue 40417848「Support for Web Speech API in Android WebView」（2016 起 open）；StackOverflow 71574756 / 55926880 / 78854760 均记录 Android WebView 中 `window.speechSynthesis` 未定义或 `getVoices()` 为空。
- 因此「参考 legado 补全朗读」的**第一性缺陷不是参数问题，而是宿主层缺失**：必须在原生侧实现 TTS 引擎并通过 JS 桥回传状态。

### 1.2 目标设备实测证据（本机 + 设备）

| 证据 | 命令/结果 |
| --- | --- |
| 设备联网可用 | `adb -s 192.168.50.3:5555 shell ping -c 2 www.baidu.com` → 0% packet loss；`curl https://speech.platform.bing.com/` → HTTP 400（可达） |
| 设备**无系统 TTS 引擎** | `settings get secure tts_default_synth` → `null`；`pm list packages` 无任何 TTS 语音包；`cmd package query-services -a android.intent.action.TTS_SERVICE` → 「No services found」 |
| 设备 HTTP 明文可用 | `app/src/main/AndroidManifest.xml:47` `android:usesCleartextTraffic="true"` |
| 在线引擎 A（百度翻译 gettts）可用 | `curl 'https://fanyi.baidu.com/gettts?lan=zh&text=…&spd=5&source=web'` → `code=200 type=audio/mpeg size=23328`；`spd` 有效区间 1..7（`spd=8` 返回空 `text/html`）；100 字长文本 → 174528 字节 |
| 在线引擎 B（微软 Edge 大声朗读）可用 | 同网段 `python edge-tts` 合成「你好，这是一段测试朗读。」→ 17568 字节音频 |
| 设备无 TTS 引擎的后果 | 本机 `system` 音源**无法出声**（可验收初始化失败提示与自动回退），因此在线引擎是本设备可验收的主路径 |
| 设备分辨率 | 1920x1080 / density 280（平板/横屏形态，小说阅读器可用） |

> 该设备结论只代表本机测试环境，不代表最终用户设备（大多数手机自带 Google/华为/小米 TTS 引擎）。因此 `system` 音源仍必须实现，只是本设备的出声证据落在在线引擎上。

## 2. 参考实现研究（证据台账）

| # | 来源（路径/URL，修订，访问日期 2026-10-09） | 证据等级 | 支撑的结论 | 对 WebHTV 的适用性/取舍 |
| --- | --- | --- | --- | --- |
| R1 | `Luoyacheng/legado-E` @ `8b87c5a`，`app/src/main/java/io/legado/app/service/BaseReadAloudService.kt`（783 行） | 一手源码 | 朗读服务骨架：`contentList` 段落队列、`nowSpeak` 游标、`prevP/nextP`、`nextChapter()`、音频焦点、`MediaSessionCompat`、通知栏 5 个动作（上一章/播放暂停/下一章/停止/定时）、`AlarmManager` 式每分钟定时递减、`ACTION_AUDIO_BECOMING_NOISY` 自动暂停 | **采纳其行为模型**：段落队列 + 游标 + 通知控制 + 音频焦点 + 定时。不采纳 `ReadBook` 静态单例与 Coroutine 依赖（WebHTV 阅读正文在 WebView DOM，不在 Kotlin 侧）。 |
| R2 | 同上，`service/TTSReadAloudService.kt`（265 行） | 一手源码 | 系统朗读实现：`TextToSpeech(this,this)`（可用引擎名构造）、`QUEUE_FLUSH` 首句 + `QUEUE_ADD` 续句、`AppPattern.notReadAloudRegex` 跳过纯标点段落、`UtteranceProgressListener.onStart/onDone/onError/onRangeStart`、`setSpeechRate((speechRate+5)/10f)` | **采纳**：系统引擎用同构策略（长文本整段入队、`onDone` 推进、跳过纯标点）。`onRangeStart` 仅 API26+ 且部分引擎不回调 → 只用于细粒度进度，不作为唯一推进依据。 |
| R3 | 同上，`service/HttpReadAloudService.kt`（617 行） | 一手源码 | 在线朗读：`MediaItem` 队列 + `SimpleCache`(LRU 128MB) + `CacheDataSink`；按「段落 MD5 文件名」落盘缓存；`preDownloadAudios()` 预取**下一章前 10 段**；流式/非流式两种模式；`speakText` 空则用无声音频占位；连续 5 次下载错误暂停朗读；`contentType` 白名单校验与错误体判定 | **采纳其要点、简化实现**：按「文本+引擎」MD5 落盘缓存 + 顺序预取 + 失败退避。不引入 media3 `SimpleCache`/`DownloadRequest`（对 mp3 分句过重），改用应用缓存目录 + LRU 清理；不引入「无声音频占位」（直接跳过空段）。 |
| R4 | 同上，`help/TTS.kt`（144 行）、`model/ReadAloud.kt`、`data/entities/HttpTTS.kt`、`assets/web/help/md/httpTTSHelp.md`、`assets/defaultData/httpTTS.json` | 一手源码/文档 | 在线朗读引擎是**用户可配置的 URL 规则**：`url`（`http://…,{"method":"POST","body":"tex={{java.encodeURI(java.encodeURI(speakText))}}&spd={{(speakSpeed+5)/10+4}}"}`）、`contentType`、`header`、`loginUrl/loginUi/loginCheckJs`；`speakText`/`speakSpeed` 为 URL 规则暴露的 JS 变量 | **部分采纳**：支持 `name/url/contentType/header` 与 `{{speakText}}`/`{{speakSpeed}}`/`{{java.encodeURI(...)}}` 模板子集，**不支持** `@js:` 规则、`loginUrl/loginCheckJs`（WebHTV 无 AnalyzeUrl/规则引擎，不引入半个 JS 规则运行时；缺失能力在 UI 与文档中明确说明并给出替代引擎）。 |
| R5 | `wangz-code/legado-edge-tts` @ clone 2026-10-09，`java-edgetts/EdgeSpeakFetch.kt`（345 行） | 一手第三方实现 | 微软 Edge 大声朗读免 Key 协议：`wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?ConnectionId&Sec-MS-GEC&Sec-MS-GEC-Version&TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4`；`Sec-MS-GEC` = 把「Windows epoch(1601) 秒数取证到 300s 对齐 ×10^7」+ TrustedClientToken 做 SHA-256 大写十六进制；`speech.config` + `ssml` 文本帧；二进制帧 = 2 字节大端 header 长度 + header + payload(mp3)；`turn.end` 结束；`<prosody rate='+X%' pitch='+YHz'>` | **采纳协议**：作为内置免 Key 高质量音源（对应旧 UI 的「微软」）。风险：`Sec-MS-GEC-Version` 需与 Edge 版本常量保持同步、协议可能被上游调整 → 设计为**可选音源**，失败可回退其他音源并给出可操作提示。 |
| R6 | `gedoor/legado` issue #2071「希望可以添加在线朗读的离线预加载功能」 | 官方仓库 issue（需求证据） | 在线朗读的体验瓶颈是「等下一句」，社区做法是**顺序预下载/缓存**音频 | 支持 R3 的预取设计；WebHTV 用最简单的顺序预取 + 落盘缓存满足同等体验。 |
| R7 | Android 平台文档 `TextToSpeech`、`UtteranceProgressListener`（developer.android.com，2026-10-09 访问） | 权威文档 | `speak(text, QUEUE_FLUSH/QUEUE_ADD, params, utteranceId)`；`setSpeechRate/setPitch`；`onStart/onDone/onError/onRangeStart`（`onRangeStart` API 26+，`onError(String)` 已废弃）；`getVoices()`/`setVoice()` 需引擎就绪后调用 | **采纳**：系统引擎 API 用法、`onRangeStart` 版本判定与「不作为唯一推进依据」的结论。 |
| R8 | Android 平台文档：前台服务与通知（`startForeground`、`NotificationCompat`、`foregroundServiceType`） | 权威文档 | Android 8+ 长时间后台音频必须有前台服务 + 常驻通知，否则进程会被回收/受限 | **采纳**：朗读由前台服务承载，通知栏提供逐段控制。 |
| R9 | Chromium issue 40417848 + SO 71574756 / 55926880 / 78854760 | 一手 issue + 社区 | WebView 不支持 `speechSynthesis` | 定案根因（§1.1）：主路径必须走原生桥，`speechSynthesis` 仅保留为「在浏览器里预览 reader.html」的兜底。 |
| R10 | 本仓库 `app/src/main/java/com/fongmi/android/tv/lab/LabRuntimeService.java`、`service/PlaybackService.java`、`ui/novel/NovelRouter.java` | 一手本地源码 | 本仓库既有模式：前台服务 + 通知 + `App.get()` 取 Context；`NovelRouter.currentReader` 静态登记阅读器实例；`activity_web_reader.xml` 承载 WebView 阅读器 | **采纳本地既有模式**，避免引入新的架构风格（不新增 Binder 抽象、不新增 DI）。 |

不可得证据（记录缺口而非跳过）：
- 腾讯云/阿里云官方 TTS 需要真实 `SecretId/SecretKey`，本轮**无法获得合法凭据**，因此不内置需要签名的云引擎（避免把未验证的签名逻辑交付为「可用」）。用户可通过 R4 的自定义在线引擎接入。
- Edge TTS 的 `Sec-MS-GEC-Version` 与上游协议稳定性无法通过文档确证，只能用**实机可用性**作为判据。

## 3. 方案对比与选型

| 维度 | 方案 A：不改（现状） | 方案 B：上游 legado 原样移植 | 方案 C：WebHTV 窄适配（选定） |
| --- | --- | --- | --- |
| 内容来源 | WebView DOM 段落 | `ReadBook`/`TextChapter` 分页模型（Kotlin 侧正文） | WebView DOM 段落（JS 收集后交给原生队列） |
| 系统朗读 | 无效（`speechSynthesis`） | `TextToSpeech` 服务 | `TextToSpeech` 引擎（复用 R2 策略） |
| 在线朗读 | 无效（接口不存在） | `HttpReadAloudService` + `AnalyzeUrl` JS 规则 + media3 `SimpleCache` | 内置免 Key 引擎（百度 gettts、Edge 大声朗读）+ 自定义 URL 模板引擎（R4 子集）+ 文件缓存/预取 |
| 控制/通知 | 面板按钮但无实现 | `MediaSessionCompat` + 通知 5 动作 + 定时 | 前台服务 + 通知 5 动作 + 定时（复用 R1 行为，用 `NotificationCompat` 简化 `MediaSessionCompat`） |
| 交互耦合 | — | 朗读与 `ReadBook` 分页/进度强耦合（`moveToNextPage` 等） | 朗读只回传「段落下标」，由 JS 决定高亮与滚动；不复刻分页几何 |
| 移植成本/风险 | 0，但功能仍不可用 | 高：需引入 `ReadBook`/`AnalyzeUrl`/Rhino/`SimpleCache` 等大量 legado 内部设施，且与 WebView 阅读器模型冲突 | 中：新增约 1.2k 行原生 + 改造 reader.html 朗读段；不动播放器/上游依赖 |
| 回归面 | — | 大（`BaseSource`/规则引擎/存储） | 小（仅阅读器朗读面板 + 新服务；不触碰视频播放链路） |

**选定方案 C**，并对上游做如下取舍决策：

- **优化/补充**：把「朗读」的确定性部分（队列、缓存、重试、通知、定时、音频焦点）下沉到原生；把「内容与视图」部分（段落收集、高亮、滚动、切章）留在 WebView JS，避免复刻 legado 的正文分页模型。
- **拒绝**：`AnalyzeUrl`/`@js:` 规则、`loginUrl/loginCheckJs`（引入半个 JS 规则引擎，收益低于复杂度与安全面）；需要签名的云引擎硬编码。
- **修正**：上游把 `speechSynthesis` 当作浏览器能力的前提在 Android WebView 不成立（R9），因此主路径必须原生化。

### 3.1 具体设计

原生（新增 `com.fongmi.android.tv.tts`）：

- `TtsTextSplitter`：纯函数分句。段落 → 朗读分句（按 `。！？；…!?;` 与换行切分，超长句按 `，、,` 二次切分并限制 160 字），丢弃纯标点/空白分句（对齐 R2 的 `notReadAloudRegex` 语义）。返回「分句文本 + 所属段落下标」。
- `TtsHttpRule`：纯函数 URI 模板渲染（`{{speakText}}` / `{{speakSpeed}}` / `{{java.encodeURI(<expr>)}}` / `{{(...)}}` 简单算术），`url,{json}` 形式解析（`method`/`body`/`headers`）。单测覆盖。
- `TtsEngine`（接口）：`init/speak(text,id)/stop/pause/resume/setRate/setPitch/release` + `Listener.onStart/onDone/onError/onVoices`。
- `SystemTtsEngine`：`android.speech.tts.TextToSpeech`；`getVoices()` 过滤中文；`setSpeechRate(rate)`；`setPitch`；无引擎/初始化失败 → `onError(ERR_NO_ENGINE)` 明确上报。
- `AudioFileTtsEngine`（抽象）：分句 → 音频文件（缓存目录 `cache/tts`，键 = engine+text 的 MD5）→ `MediaPlayer` 顺序播放，`setPlaybackParams` 调速率，预取后续 3 句，失败重试 3 次后退避并跳过；`pause/resume/stop`；缓存上限 64MB LRU 清理。
- `HttpTtsEngine extends AudioFileTtsEngine`：OkHttp GET/POST（`TtsHttpRule`），校验 `contentType` 为音频前缀，否则读错误体上报。
- `EdgeTtsEngine extends AudioFileTtsEngine`：OkHttp WebSocket + `Sec-MS-GEC`（R5），复用一个 socket 串行合成，失败重连一次。
- `BuiltinTtsEngines`：内置「百度在线语音」（`https://fanyi.baidu.com/gettts?lan=zh&text={{java.encodeURI(speakText)}}&spd={{speakSpeed}}&source=web`，`speakSpeed` 映射 `clamp(round(rate*5),1,7)`）与「微软 Edge 语音」（音色 8 个中文 Neural）。两者均**只依赖网络，不需 Key**。
- `ReaderTtsController`：段落/分句队列 + 游标 + 状态机（idle/playing/paused/stopped/error）+ 定时 + 音频焦点（`AudioFocusRequestCompat`，`AUDIOFOCUS_LOSS`/`LOSS_TRANSIENT`/`LOSS_TRANSIENT_CAN_DUCK` 一律让出焦点并暂停；让出后**不自动恢复**，由用户在面板/通知栏手动继续）+ `ACTION_AUDIO_BECOMING_NOISY` 暂停；对外 `Listener.onState/onParagraph/onChapterEnd/onVoices`。
- `service/ReaderTtsService`：前台服务（`mediaPlayback`），持有 controller，构建通知（上一段/播放暂停/下一段/停止/定时），`stopSelf` 时释放引擎与缓存；`static get()` 供 Activity 访问（沿用本地既有模式 R10）。

JS 桥（`WebReaderActivity`）：

```java
String ttsInfo()                       // {sources:[{id,name,available,detail,voices:[...]}], source, state}
void   ttsStart(String json)           // {source,voice,rule,rate,pitch,timer,title,index,paragraphs:[...]}
void   ttsPause() / ttsResume() / ttsStop()
void   ttsPrev() / ttsNext() / ttsSeek(int)
void   ttsSetRate(float) / ttsSetPitch(float) / ttsSetVoice(String) / ttsSetTimer(int)
```
原生 → JS：`window.__onTtsState(json)`、`window.__onTtsProgress(json)`、`window.__onTtsChapterEnd()`、`window.__onTtsVoices(json)`。

reader.html：

- 朗读面板改造为：音源（系统 / 微软 / 在线）、音色下拉（异步填充）、语速（0.5–2.0）、音调（0.5–1.5）、定时停止（关/10/20/30/60 分钟）、跟随高亮开关、段落控制（⏮ ▶/⏸ ⏭）、停止、状态文案、在线引擎配置（名称/URL/Content-Type/Header，JSON 表单）。
- 章末续读：`__onTtsChapterEnd` → 滚动模式走 `loadNextNovelAppend()`（追加），分页模式走 `goChapter(next)`；数据到达后按「最后朗读元素之后的首个段落」重建队列并继续；无下一章则停止并提示。
- 保底：无 `AndroidReader.ttsStart`（浏览器预览）时仍用 `speechSynthesis`。

## 4. 验收标准

1. `TtsTextSplitterTest`、`TtsHttpRuleTest`、`ReaderTtsBridgeSourceTest` 全部通过（`./gradlew :app:testMobileDebugUnitTest --tests '*Tts*' --tests '*ReaderTts*'`）。
2. `mobile`/`leanback` 双 flavor 编译通过（`assembleMobileArm64_v8aDebug`、`assembleLeanbackArm64_v8aDebug`）。
3. 真机（`192.168.50.3:5555`）小说阅读器内点朗读：在线引擎出声、段落高亮跟随、暂停/继续/上一段/下一段/停止生效、章末自动续读下一章。
4. 切后台/锁屏后朗读继续，通知栏控制可用；退出阅读页后服务停止、无残留播放。
5. 系统音源在无 TTS 引擎设备上给出明确失败提示且不崩溃；有引擎设备上可出声。

## 5. 回滚路径

- 代码单点回滚：`git revert <commit>`（本任务所有改动集中在 `assets/reader.html` 朗读段、`WebReaderActivity` 桥方法、新增 `tts/` 包、新增服务与 manifest 一行声明；互不牵连视频播放链路）。
- 运行时熔断：`AndroidReader.ttsInfo()` 返回 `available=false` 时 JS 回落 `speechSynthesis`；在线引擎失败自动降级下一音源并提示。
- 恢复标签：`recovery/NOVEL-TTS/<timestamp>`。

## 6. 实施与验证记录

### 6.1 落地文件

新增（原生朗读层）：

| 文件 | 职责 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/tts/TtsTextSplitter.java` | 段落/分句切分、纯标点片段丢弃（纯逻辑，可单测） |
| `app/src/main/java/com/fongmi/android/tv/tts/TtsHttpRule.java` | legado 在线朗读引擎规则子集：`url,{json}` 解析、`{{speakText}}/{{speakSpeed}}/{{java.encodeURI()}}/String()` 与整数算术求值、`encodeURI` 语义（纯逻辑，可单测） |
| `app/src/main/java/com/fongmi/android/tv/tts/TtsEngineConfig.java` | 自定义引擎配置（name/url/contentType/header）与 JSON 往返 |
| `app/src/main/java/com/fongmi/android/tv/tts/TtsEngines.java` | 引擎注册表、内置音源常量、系统引擎可用性探测、系统音色异步探测、自定义引擎持久化 |
| `app/src/main/java/com/fongmi/android/tv/tts/TtsVoice.java`、`TtsOptions.java` | 音色与朗读参数（语速/音调限幅） |
| `app/src/main/java/com/fongmi/android/tv/tts/TtsEngine.java` | 引擎抽象：队列由引擎持有，逐片段回调进度 |
| `app/src/main/java/com/fongmi/android/tv/tts/SystemTtsEngine.java` | `android.speech.tts.TextToSpeech`：首句 FLUSH + 续句 ADD、`onDone` 推进、单段失败跳过、无引擎时给出可操作错误 |
| `app/src/main/java/com/fongmi/android/tv/tts/AudioFileTtsEngine.java` | 在线引擎基类：MD5 落盘缓存、顺序预取 3 段、`MediaPlayer` 顺序播放、失败重试/跳过、连续失败熔断、播放失败自动清缓存重下 |
| `app/src/main/java/com/fongmi/android/tv/tts/HttpTtsEngine.java` | 按规则发起 GET/POST、Content-Type 音频校验、失败原因提取（HTTP 码/错误体片段/异常） |
| `app/src/main/java/com/fongmi/android/tv/tts/BaiduTtsEngine.java` | 内置「百度在线语音」（免 Key，实测可用） |
| `app/src/main/java/com/fongmi/android/tv/tts/EdgeTtsEngine.java` | 内置「微软 Edge 语音」（WebSocket + `Sec-MS-GEC`，8 个中文 Neural 音色，按 `X-RequestId` 匹配并发片段） |
| `app/src/main/java/com/fongmi/android/tv/tts/ReaderTtsController.java` | 编排：段落队列、上/下一段、定时停止、状态机、音频事件回主线程、章末回调 |
| `app/src/main/java/com/fongmi/android/tv/service/ReaderTtsService.java` | 前台服务：常驻通知（上一段/播放暂停/下一段/停止/+30 分钟）、音频焦点（暂停即让出）、拔耳机暂停、WakeLock |
| `app/src/main/assets/reader.html`（朗读段重写） | 面板改造（系统/微软/百度/在线 + 音色/语速/音调/定时/跟随 + 上一段/播放暂停/下一段/停止 + 在线引擎表单）、原生桥调用、跟随高亮、章末续读；浏览器预览才回落 `speechSynthesis` |
| `app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java` | JS 桥：`ttsInfo/ttsVoices/ttsSaveEngine/ttsStart/ttsPause/ttsResume/ttsToggle/ttsPrev/ttsNext/ttsSeek/ttsSetRate/ttsSetPitch/ttsSetVoice/ttsSetTimer/ttsStop`；回调 `__onTtsState/__onTtsProgress/__onTtsChapterEnd/__onTtsVoices`；退出阅读页停播 |
| `app/src/main/AndroidManifest.xml` | 声明 `ReaderTtsService`（`foregroundServiceType="mediaPlayback"`） |
| `app/src/main/res/values/strings.xml` | 朗读通知文案 |

新增（测试）：

| 文件 | 覆盖 |
| --- | --- |
| `app/src/test/java/com/fongmi/android/tv/tts/TtsTextSplitterTest.java` | 段落/分句、标点丢弃、超长句切分、控制字符、静默判定 |
| `app/src/test/java/com/fongmi/android/tv/tts/TtsHttpRuleTest.java` | encodeURI 语义、算术与 `String()`、legado `url,{json}` 形态、POST body/header、`@js:` 拒绝、配置往返 |
| `app/src/test/java/com/fongmi/android/tv/ui/web/ReaderTtsBridgeSourceTest.java` | JS↔原生桥方法/回调名一一对应、reader.html 不再以 `speechSynthesis` 为主路径、音源 id 与原生常量一致、清单服务声明与前台/焦点/拔耳机行为、控制器具备上一段/下一段/定时/章末能力 |
| `app/src/androidTest/java/com/fongmi/android/tv/tts/ReaderTtsDeviceTest.java` | 真机（联网）验收：百度/微软引擎真实合成+播放+推进、系统引擎无引擎时优雅失败、定时到点真的停播、阅读页端到端（面板按钮 → 桥 → 服务 → 引擎 → 高亮 → 后台续读 → 通知栏控制 → 章末续读 → 停止） |

### 6.2 实施中修正的真实缺陷

1. **缓存目录串到 `default`**：`AudioFileTtsEngine` 在构造函数里调用 `id()` 建缓存目录，而子类的 `engineId` 字段此刻尚未赋值，所有在线引擎的音频都落进同一个目录。设备测试的「必须生成音频缓存」断言把它抓出来；改为懒建缓存。
2. **章末续读被状态顺序吃掉**：控制器先把状态置成 `stopped` 再发「章末」事件，阅读页据此判断「当前是否在读」，于是读完一章就彻底停下。改为先发章末再落 `stopped`，并在阅读页侧把「章末续读意图」独立于状态记录。
3. **音频焦点被反复请求**：每个状态回调都 `requestAudioFocus`，既有焦点栈噪声又让「暂停让出焦点」失效。改为持有标记 + 暂停/停止即让出。

### 6.3 验证证据

| 验证 | 命令 | 结果 |
| --- | --- | --- |
| 定向 JVM 测试 | `./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests '*Tts*' --tests '*ReaderTts*'` | BUILD SUCCESSFUL（TtsTextSplitter/TtsHttpRule/ReaderTtsBridgeSource 共 29 用例，含音调→Hz 映射） |
| 双 flavor 编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | 均通过 |
| 打包安装 | `bash scripts/build_arm64_debug_install.sh` | ✅ 安装成功（`com.silent.android.webhtv`） |
| 真机验收（联网） | `adb -s 192.168.50.3:5555 shell am instrument -w -e class com.fongmi.android.tv.tts.ReaderTtsDeviceTest com.silent.android.webhtv.test/androidx.test.runner.AndroidJUnitRunner` | `OK (6 tests)`：百度引擎合成+播放推进、微软 Edge 引擎合成+播放推进、系统引擎无引擎时给出可操作错误、定时到点真的停播、阅读页端到端（含后台继续朗读、通知栏暂停/继续/下一段、章末续读下一章、停止后服务退出） |
| 自定义在线引擎（临时夹具，已删除） | 同上，`CustomEngineProbeTest` + 开发机上的转发服务日志 | `OK (1 test)`；服务端记录 `POST body=text=%E8%87%AA...&spd=7 x-client=webhtv-tts-test`（模板渲染 + POST + 自定义 Header 均生效），音频落在独立的 `cache/tts/custom` 目录 |

设备事实（`192.168.50.3:5555`，LIO-AN00 / Android 9）：该设备**没有安装任何系统 TTS 引擎**（`tts_default_synth=null`，无 `TTS_SERVICE` 提供者），因此本次出声证据来自在线音源；系统音源在这台设备上的「优雅失败 + 指引改用在线音源」路径已被测试覆盖。

### 6.4 设备副作用与恢复（必须记录）

- 使用 Gradle `connectedAndroidTest` 跑真机测试时，AGP 在跑完后**卸载了被测应用**，导致应用数据（配置、历史、设置）被清除。
- 恢复方式：重新安装 Debug 包后，用应用自身的恢复能力（`AppDatabase.restore` → `AppBackup.restore`）恢复 `/sdcard/TV/bak-20260927-1048.zip`（设备上最新的自动备份）。恢复后核对：`config_0/1/2` 与订阅地址 `http://192.168.50.50:4567/sub/2024/buye-0` 均已回来。
- 缺口：9-27 之后的设置与历史记录无法恢复（该备份之后没有更新的备份）。
- 后续规则：本机真机测试改用 `adb install -r <androidTest apk>` + `adb shell am instrument ...`，避免 `connectedAndroidTest` 卸载应用。

### 6.5 交付后补强（第二个 guard 会话）

| 项目 | 处理 |
| --- | --- |
| 音调控件在在线音源上「拖了没反应」 | 微软 Edge 的 SSML `prosody pitch` 接入真实音调（并把音调纳入缓存键，避免改音调播到旧音频）；百度发音接口没有音调参数，面板在百度/自定义音源上隐藏音调控件。音调→Hz 的映射抽成纯函数 `TtsOptions.pitchOffsetHz`，由单测覆盖含越界夹取 |
| 拔耳机自动暂停无法被真机测试覆盖 | `ACTION_AUDIO_BECOMING_NOISY` 是**系统保护广播**，应用与 `adb shell am broadcast` 均被拒绝（实测 SecurityException），因此把接收器内的动作抽成 `ReaderTtsService.onAudioBecomingNoisy()`，真机测试驱动同一入口验证「路由丢失 → 自动暂停 → 可继续」；广播注册本身由源码契约测试覆盖 |

补强后的验证：定向 JVM 测试与双 flavor 编译通过；`ReaderTtsDeviceTest` 在同一台设备上仍为 `OK (6 tests)`（其中阅读页端到端用例现在多覆盖「路由丢失暂停 + 继续」）。

### 6.6 状态

- 状态：**已完成**（实现、单测、双 flavor 编译、真机端到端验收全部通过）
- 未覆盖（记录缺口，不掩盖）：
  - 有系统 TTS 引擎的设备上的系统朗读出声（本机设备没有引擎）；
  - 微软 Edge 协议为非公开接口，上游若调整 `Sec-MS-GEC-Version` 需同步常量（失败时会回落到可用音源并提示）。
- 下一动作：guard 原子提交并创建恢复 tag。
