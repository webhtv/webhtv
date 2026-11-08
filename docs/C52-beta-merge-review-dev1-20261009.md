# C52：dev1 合并远端 beta 最新代码并复评已修改代码（含已提交未推送）

## Recovery anchor

- **目标**：把远端 `beta` 最新代码合入 `dev1`（**远端已移除/回退的提交不得顺带带回**）；复评 dev1 全部已修改代码（含已提交未推送的 `8539a450f`、`1ebec24a6`、`db04c4c8b`）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev1`、创建 `dev1 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并提交第二父为 `origin/beta` tip `29d52ab23`；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev1 既有改动**零丢失**；④ 双 flavor Java 编译通过；⑤ 双 flavor AndroidTest Java 编译通过；⑥ 双 flavor 全量 JVM 套件零失败；⑦ UI token 门禁相对基线零新增违规；⑧ 净差异只含本分支自身改动；⑨ 复评发现的问题已修复或已按 AGENTS.md 明确记录处置；⑩ 提交 + recovery tag、`dev1` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并完成（0 冲突，第二父 = `29d52ab23`）；3 轮复评完成；发现并修复 3 个真实缺陷（F1 停播后通知残留、F2 暂停中改语速出声、F4 朗读桥跑在 JavaBridge 线程）与 1 处文档不实描述（F3）；双 flavor 编译与全量 JVM 套件（leanback 4125 / mobile 4963，0 失败）、UI token 门禁、真机 `ReaderTtsDeviceTest`（`OK (6 tests)`）全部通过；两个缺陷均有修复前真机负对照。- **交付坐标**：见文末「交付坐标」。
- **下一动作**：guard 原子提交（含合并提交）+ 创建 recovery tag → 推送 `dev1` → 创建 `dev1 -> beta` 中文 PR（只创建不合并）。

## 时间与设备

- 任务开始时本地时间：2026-10-09 13:38（Asia/Shanghai），开始时工作区**干净**（`git status --porcelain` 空），`dev1` 领先 `origin/dev1` 3 个提交（**已提交未推送**）。
- 设备：dev1 机位 `192.168.50.3:5555`（LIO-AN00 / Android 9）。未申请额外机位。
- 设备副作用与恢复：沿用 2026-10-09 13:20 已装的 mobile arm64 debug 包，全程 `adb install -r` 覆盖安装（不卸载、不清数据）；androidTest 包用 `adb install -r` + `am instrument` 运行，**不使用 `connectedAndroidTest`**（它会卸载被测应用）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1` |
| 任务开始时 HEAD | `db04c4c8bc3f638980a2983af65170aa09f66439`（`docs(novel-tts)`，领先 `origin/dev1` 3 个提交，**已提交未推送**） |
| `origin/beta` tip | `29d52ab2385702a9350c3099cd94dd4e54c1240f`（Merge PR #426 from dev2） |
| 合并基点（merge-base） | `cb8022bf777b6e9cbc1c2738b6eb4a8f669b62d4`（= 合并前的 `origin/dev1`） |
| 合并方式 | `git merge --no-ff origin/beta`（真实合并提交，非快进） |
| 合并结果 | **0 冲突、0 冲突标记**，12 路径合入 |
| 合并提交 | `86e1bfe3ce4edadc874121decf18ed7d99c7e327`，第一父 `db04c4c8b`（dev1 侧）、第二父 `29d52ab23`（beta 侧） |
| 初始脏路径 | 无 |
| 回滚锚点 | `db04c4c8bc3f638980a2983af65170aa09f66439` |
| 任务守卫 | `C52-beta-merge-review-dev1`（standard，scope `app/src` + `docs` + `scripts` + `.codex/scripts` + `.codex/task-state`）；首次 `start` 发生在合并之前（HEAD 仍为 `db04c4c8b`），而仓库既有顺序是先合并再建档（如 C50 的 `base_head` = 合并提交 `a8b30e09d`）。因此把该会话置为失效（移到 `.codex/task-state/archive/`）并在合并提交 `86e1bfe3c` 上重新 `start`，再执行 `finish`；修复提交与 recovery tag 由重锚后的会话产生 |

### beta 增量 ledger

`git log --format='%H %s' cb8022bf7..29d52ab23`（合并前）= 10 个提交：

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `29d52ab2385702a9350c3099cd94dd4e54c1240f` | Merge pull request #426 from Silent1566/dev2 | 纳入（合并线） |
| `7543959f3e0a388b13eff0cc608ecbf96baa13ba` | docs(c51): 记录 dev2 交付坐标 | 纳入（仅 `docs/`） |
| `1f8fc83369dbd60703b5d72305e507cd3064a6f9` | merge: dev2 合并 beta 并复评首页菜单键弹窗对比度修复 | 纳入（合并线） |
| `c5912111a4ab791dc2403a031c91e5e803eb7d02` | fix(tv): 首页菜单键弹窗焦点条目文字改用 colorOnPrimary | **纳入**（实质增量） |
| `75735b58f6f6eb357a0c0b1574da0c10f8864a4f` | Merge pull request #425 from Silent1566/dev4 | 纳入（合并线） |
| `61e483011ed43f836df7df7c12f097171bebbc08` | docs(c49): 记录 dev4 交付坐标 | 纳入（仅 `docs/`） |
| `8dd441b1ccc3a65d5486be09de96b6b996e471c1` | merge: dev4 合并 beta 并复评 C47 seek 加载圈窗口 | 纳入（合并线） |
| `7df98107befd6bdd49f7aae5c7fc9c1efeb19b34` | Merge pull request #424 from Silent1566/dev1 | 纳入（合并线，即本分支上一轮 PR） |
| `1a4110178fccddc656122e08fc8d3a056ae5e2f2` | docs(c47): 记录模拟器验收证据与精度边界 | 纳入（仅 `docs/`） |
| `23e5280f37d90e62aa6b20bf3d3fc15cef62f010` | 修复：拖拽进度时加载中/网速大概率不显示 | **纳入**（实质增量） |

`git diff --name-status cb8022bf7 29d52ab23` = **12 路径**，与上表 2 个实质增量 + 3 个文档提交一致：

| 路径 | 类型 |
| --- | --- |
| `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` | 修改 |
| `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` | 修改 |
| `app/src/leanback/res/layout/adapter_home_menu.xml` | 修改 |
| `app/src/leanback/res/color/home_menu_text.xml` | 新增 |
| `app/src/test/java/com/fongmi/android/tv/ui/activity/C47SeekLoadingProgressSourceTest.java` | 新增 |
| `app/src/testLeanback/java/com/fongmi/android/tv/ui/dialog/HomeMenuDialogContrastTest.java` | 新增 |
| `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/VideoActivityLayoutTest.java` | 修改 |
| `docs/C47-seek-loading-progress.md` | 新增（dev4 侧文档） |
| `docs/C49-beta-merge-review-dev4-20261008.md` | 新增（dev4 侧文档） |
| `docs/C51-beta-merge-review-dev2-20261009.md` | 新增（dev2 侧文档） |
| `docs/TV-HOME-MENU-CONTRAST-20261009.md` | 新增（dev2 侧文档） |
| `docs/upstream-player-dependency-merge-assessment-2026-08-20.md` | 修改（dev4 侧索引） |

增量零丢失：合并提交相对第一父的改动集与上表**逐项一致**（`diff` 实测 `IDENTICAL_TO_BETA_DELTA=yes`），未多带、未少带。

## 用户核心关注点：远端已移除（回退）内容零复活

采用与 C41/C46/C48/C50 相同的三层递进证据，全部程序化。

### 第一层：结构性证明（最强证据）

逐 blob 全量比对 `origin/beta` tip 与合并后 `dev1` 树：

```text
beta tip 文件总数                              : 4984
dev1 合并树文件总数                            : 5004
两侧共有路径数                                 : 4984
其中 blob 不同的路径数                         : 6（= dev1 自己改动的已有文件）
其中 blob 相同的路径数                         : 4978
只在 beta 存在（dev1 缺失）的路径数            : 0
只在 dev1 存在的路径数                         : 20（dev1 自建的朗读代码/测试/文档）
```

blob 不同的 6 个路径全部属于 dev1 自身的朗读改动：
`app/src/main/AndroidManifest.xml`、`app/src/main/assets/reader.html`、
`app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java`、
`app/src/main/res/values/strings.xml`、`values-zh-rCN/strings.xml`、`values-zh-rTW/strings.xml`。

**结论**：除 dev1 自己改写的 6 个路径外，其余 **4978 个路径与 beta 逐字节相同** → dev1 不可能从 beta 之外引入任何内容 → **结构性零复活**；`只在 beta 存在 = 0` → **结构性零丢失**。

### 第二层：行级复活扫描

对 beta 上每个主题命中 `revert|remove|removal|delete|drop|回退|移除|删除|剔除|撤销` 且**单亲**的提交 `R` 取父 `R^`，得到 `removed(R,f) = lines(R^:f) − lines(R:f)`；复活定义为「该行在合并树中存在，但在 `origin/beta` tip 中不存在」。

```text
beta 非合并提交总数                                                        : 3324
主题命中移除类且单亲的提交                                                 : 139
被这些提交触碰的文件版本数                                                 : 761
其中确实产生删除行的文件版本数                                             : 446
「合并树存在且 beta tip 不存在」的 distinct 删除行数（= 复活行数）         : 0
```

**结论**：零复活。第一层已从根上排除该可能，第二层为加固。

### 第三层：关键移除目标与净差异核对

- `1b42d6624`（beta 移除手机版个性设置触屏优化入口）：`git grep` 在手机源集与 `app/src/mobile` 布局中命中 0（仅在 leanback 侧保留入口，与 beta 一致）。
- `be1b02e06`（`revert: remove dynamic theme color system`）：其删除文件在合并树中仍不存在（第一层已逐 blob 覆盖）。
- `git diff --name-status origin/beta HEAD` = **26 路径**（6 修改 + 20 新增），全部为 dev1 自身改动，无一条来自 beta 之外的来源。
- `git grep -nE '^(<<<<<<<|>>>>>>>)' -- app/src docs` 命中 0（此前命中的 `=======` 是 `assets/VodPlus/examples` 里既有的分隔线文本，非冲突标记）。

**结论：零复活 + 零丢失。**

## 复评记录

复评对象 = dev1 全部已修改代码（26 路径：3 个未推送提交的朗读功能实现 + 本次合并带入的 beta 增量）。

### 第 1 轮：未推送的朗读功能逐模块复核

| 模块 | 复核项 | 判定 |
| --- | --- | --- |
| `TtsTextSplitter` | 段落规范化/分句/软硬切分/纯标点丢弃；段落下标与 JS 侧 `ttsParagraphs` 对齐（两边都按 DOM 顺序过滤空段，索引一一对应） | 通过 |
| `TtsHttpRule` | `url,{json}` 选项分界按「逗号后整段能解析成 JSON」判定，URL 自带逗号不被误切；算术求值只认 `speakSpeed` 与数字，无法注入任意表达式；`encodeURI` 与 JS 语义对齐并可单测 | 通过 |
| `TtsAudioCache` | MD5 键含引擎与参数；写入失败删临时文件；超过 64MB 按 mtime 淘汰；引擎 id 经 `sanitize` 后建目录 | 通过 |
| `SystemTtsEngine` | 首句 `QUEUE_FLUSH` + 续句 `QUEUE_ADD`；`onDone` 推进；无引擎时给可操作 fatal 错误；`pause()` 用 `stop()` 清队列、`resume()` 从当前段重入队（语义与 legado 一致） | 通过 |
| `EdgeTtsEngine` | 协议帧解析（2 字节大端 header 长度 + `Path:audio` 校验）、按 `X-RequestId` 匹配片段、`Sec-MS-GEC` 计算、超时/断线 `markFailed` 后重连一次、SSML 转义与装饰字符清洗 | 通过 |
| `HttpTtsEngine` | Content-Type 音频白名单、错误体片段回传便于诊断、POST/自定义 Header | 通过 |
| `ReaderTtsController` | 全部引擎回调经 `onMain` 归一到主线程；章末先发事件再落 `STOPPED`（否则续读被状态判断吃掉）；定时只在播放中递减 | 通过 |
| `reader.html` | 无原生桥才回落 `speechSynthesis`；段落收集/高亮/续读定位与原生下标一致；音调控件按音源隐藏 | 通过 |
| `WebReaderActivity` | 桥方法与 JS 调用一一对应（`ReaderTtsBridgeSourceTest` 锁定）；退出阅读页停播并清回调 | 通过 |
| JVM 契约测试有效性 | 桥名/回调名/音源 id/清单前台服务/控制器能力均为源码形态断言，回退生产改动即转红 | 有效 |

**第 1 轮结论**：发现 3 个真实缺陷（F1、F2、F4）与 1 处文档不实描述（F3），其余为按 AGENTS.md §2 只记录的观察项（见「发现与处置」）。

### 第 2 轮：修复后复审

| 复审项 | 证据 | 判定 |
| --- | --- | --- |
| F1 修复是否只是「不改通知」而无副作用 | `stopped` 分支仍照旧 `abandonFocus()` + `stopForegroundCompat()` + 6 秒续读窗口；只是不再把刚撤掉的通知贴回来；`onDestroy` 分类取消是幂等的 | 通过 |
| F1 修复是否破坏「章末续读」通知 | 下一章 `startReadAloudNow()` 会重新 `startForegroundCompat()`（`foreground=false` 后不会早退）→ 真机断言「续读后朗读通知重新挂上」通过 | 通过 |
| F2 修复后语速语义是否仍然正确 | 播放中改语速仍即时生效（`!paused` 分支不变）；暂停中改语速存入 `options`，`resume()` 在 `start()` 后补 `applyRate`（非零速度等价 `start()`，不重置进度）；后续片段在 `onPrepared` 一律用新语速 | 通过 |
| F4 修复是否引入新的竞态 | 11 个控制桥 + `ttsStart` 全部只做 `runOnUiThread` 投递，投递顺序与 JS 调用顺序一致（同一条 JavaBridge 线程）；控制器侧方法本身已是主线程语义，无重入问题 | 通过 |
| 修复是否泄漏到无关模块 | 改动集中在 `tts/`、`ReaderTtsService`、`WebReaderActivity` 朗读段与朗读测试；`git diff --name-only` 不含 `res/`、native、锁文件、播放器链路 | 通过 |
| 结论 | 第 2 轮复审全部通过，无剩余阻塞项 | 通过 |

### 第 3 轮：收口复核

- 净差异只含 dev1 自身改动（见「合并台账」与「改动清单」），无 beta 之外来源；
- 零复活三层证据重算通过（复活行数 0）；
- `git diff --check` 退出码 0；冲突标记命中 0；
- 双 flavor Java / AndroidTest Java 编译、双 flavor 全量单测、真机 6 用例、UI token 门禁全部通过（见「验证」）。

## 发现与处置

### F1（已修复）：停播后通知栏残留一条标题写着「正在朗读」的失效控制条

**证据链**：`ReaderTtsService.onTtsState()` 对 `STATE_STOPPED` 先执行 `stopForegroundCompat()`（内部 `stopForeground(true)` + `manager.cancel(NOTIFY_ID)`），随后**无条件**执行 `updateNotification(...)` → `manager.notify(NOTIFY_ID, …)` 又把通知贴了回来。而此时 `buildNotification` 对 `stopped` 状态取的分支标题是 `reader_tts_playing`（「正在朗读」），于是：

- 用户点「停止」或退出阅读页后，通知栏留下一条「正在朗读 · 书名」的通知，带上一段/播放/下一段/定时/停止 五个按钮；
- 服务 6 秒后 `stopSelf` 销毁，而 `onDestroy` 从不取消通知 → 这条失效通知会一直留着，只能用户手动划掉；
- 点它上面的按钮还会重新拉起服务（`dispatch()` → `startService`），而 `ACTION_RESUME` 等动作在新建的空控制器上无效，服务又不会 `startForeground`/`stopSelf`，等于留下一个无通知的后台常驻服务。

**修复**：`stopped` 分支不再重贴通知（`if (!stopped) updateNotification(...)`），并在 `onDestroy()` 兜底 `manager.cancel(NOTIFY_ID)`，覆盖 ERROR 等其它退出路径。

**锁定**：`ReaderTtsDeviceTest.readerPageReadsAloudThroughTheNativeBridge` 新增断言「停播后 `getActiveNotifications()` 不再包含朗读通知」。

### F2（已修复）：暂停中拖动语速会把「已暂停」的朗读重新拖响

**证据链**：`reader.html` 的 `seekTtsSpeed` 在 `ttsState !== 'stopped'`（含 `paused`）时就调用 `AndroidReader.ttsSetRate` → `AudioFileTtsEngine.setRate()` 直接对当前 `MediaPlayer` 调 `setPlaybackParams`。Android 官方文档（`MediaPlayer.setPlaybackParams`，2026-10-09 访问）明确：

> Calling it before the object is prepared does not change the object state. After the object is prepared, calling it with zero speed is equivalent to calling pause(). **After the object is prepared, calling it with non-zero speed is equivalent to calling `start()`.**

即暂停中改语速等价于 `start()`：界面与控制器仍是 `paused`（按钮显示 ▶、通知写「已暂停」）但音频已出声；若片段播完，`onCompletion` 还会在 `playing==true` 时推进到下一段，形成「暂停却在读」的错乱。

**修复**：`setRate()` 只在**非暂停**时对当前播放器生效（暂停期间只更新 `options`，后续片段自然使用新语速）；新增 `applyRate(MediaPlayer)` 抽公共逻辑，`resume()` 在 `start()` 后补一次 `applyRate`，让暂停期间改的语速在恢复时立刻生效（非零速度等价 `start()`，不会重置进度）。

**锁定**：`ReaderTtsDeviceTest.assertOnlineEngineReadsAloud` 新增断言「暂停后 `AudioManager.isMusicActive()` 为假 → 暂停中 `setRate(1.5f)` 后仍为假且状态仍为 `paused`」。

### F3（已修复，文档）：`NOVEL-TTS` 设计章节关于音频焦点「透明丢失后自动恢复」的表述与实现不符

原文 §3.1 写「`LOSS_TRANSIENT` 暂停并在 `GAIN` 恢复」，但 `ReaderTtsService.onAudioFocusChange()` 只处理 `LOSS` / `LOSS_TRANSIENT` / `LOSS_TRANSIENT_CAN_DUCK` → `controller.pause()`，**从不处理 `AUDIOFOCUS_GAIN`**，即没有自动恢复。处置：按实现口径修正文档（保持实现不变——「被抢占即让出、由用户决定是否继续」与通知栏/面板的手动继续一致，且 §4 验收标准未要求自动恢复；避免为此改动运行时代码）。

### F4（已修复）：朗读控制桥在 WebView 的 JavaBridge 线程直接驱动控制器/前台服务

**证据链**：`@JavascriptInterface` 方法跑在 WebView 的 JavaBridge 线程（本文件既有桥方法因此一律 `runOnUiThread`：`back()`、`toast()`、`loadChapter()` 等）。但 11 个朗读控制桥（`ttsPause/ttsResume/ttsToggle/ttsPrev/ttsNext/ttsSeek/ttsSetRate/ttsSetPitch/ttsSetVoice/ttsSetTimer/ttsStop`）与 `ttsStart` 的收尾直接在 JS 线程调 `ReaderTtsService`，于是：

- 控制器状态机（`state`/`chunks`/`currentChunk`）在 JS 线程被改写，而引擎回调在**主线程**被 `onMain` 归一后也改这些字段 → 控制器注释里「回调链路下游要 startForeground/stopSelf、通知更新与 WebView evaluateJavascript，都要求主线程语义」这一前提被绕过；
- `ReaderTtsController.handleStart()` 先读 `chunks.size()` 再 `chunks.get(index)`，而 `stop()` 会把 `chunks` 整体换成空表：两读之间被换掉就抛 `IndexOutOfBoundsException`（主线程崩溃）；
- `MediaPlayer` 操作与 `startForeground`/`stopForeground`/`NotificationManager.notify` 发生在非主线程。

**修复**：新增 `postToService(Consumer<ReaderTtsService>)`（内部 `runOnUiThread`），11 个控制桥改为经它投递；`ttsStop` 与 `ttsStart` 的收尾各自 `runOnUiThread`（`ttsStart` 在主线程内「先登记 `pendingStart` 再 `startForegroundService`」，交接天然有序）。

**锁定**：`ReaderTtsBridgeSourceTest.readAloudControlBridgesDispatchToTheMainThread`（新增）逐个断言 12 个桥方法体内存在 `postToService(` 或 `runOnUiThread(`。

### 只记录不修（按 AGENTS.md §2）

| 编号 | 观察项 | 不修理由 |
| --- | --- | --- |
| N1 | `SystemTtsEngine.prepare(null, …)` 会在 `listener.onTtsError` 处 NPE | 现有全部调用点都传非空监听器（控制器与音色探测），无可达路径 |
| N2 | `TtsAudioCache.evictIfNeeded()` 会删掉非 `.mp3` 文件，可能删掉并发下载中的 `.part` 临时文件 | 该次下载随后失败并由既有的重试/跳过逻辑自愈；缓存超过上限时才触发 |
| N3 | `AudioFileTtsEngine` 在合成线程读 `released`/`playing` 未加 `volatile` | 任务提交与执行之间由 `ExecutorService` 队列建立 happens-before，且回调处一律再校验 `generation`；只可能多跑一小段已作废的合成，无状态错误 |
| N4 | `TtsEngines.systemAvailable()` 用 `queryIntentServices` 探测，而清单**故意**不声明 `<queries>` | 本应用 `targetSdk = 28`（不受 Android 11 包可见性过滤），且清单注释明确说明「Android 9 TV 固件把 `<queries>` 当未知子元素」，改它会破坏目标设备启动 |
| N5 | 通知小图标使用框架 `android.R.drawable.ic_lock_silent_mode_off` | 真机测试已断言通知真实挂上（`getActiveNotifications()`）并在目标机型显示正常；无失败证据 |
| N6 | Edge `X-Timestamp` 用 `EEE MMM d yyyy …`（日不补零），参考实现用 `%d`（补零） | 真机 Edge 合成用例通过，服务端当前容忍；属上游非公开协议的既有差异 |
| N7 | 静态扫描在 `WebReaderActivity` 上报 `Math.random()`（第 128 行 `picNonce`）与 `MD5`（第 640 行图片缓存键） | 两者都在本次改动之外（本次 hunk 从第 1636 行起），`git diff` 证实未被触碰；图片缓存键的 MD5 不用于安全用途 |

## 验证

日志目录：`build/c52-verify/`（构建产物，不入库）。

### 4.1 负对照：修复前在真机上先证明缺陷真实存在

同一份测试代码（只含本轮新增断言）先跑在**修复前**的已装 APK（2026-10-09 13:20 安装，与 `db04c4c8b` 逐字节同源）上，两条断言如实报错：

```text
java.lang.AssertionError: baidu 暂停中改语速把音频拖响了（setPlaybackParams 非零速度等价 start()）
    at ...ReaderTtsDeviceTest.assertOnlineEngineReadsAloud(ReaderTtsDeviceTest.java:168)
Time: 4.695  Tests run: 1,  Failures: 1        # build/c52-verify/negative-baidu.log

java.lang.AssertionError: 停播后通知栏残留朗读通知
    at ...ReaderTtsDeviceTest.readerPageReadsAloudThroughTheNativeBridge(ReaderTtsDeviceTest.java:376)
Time: 49.312  Tests run: 1,  Failures: 1        # build/c52-verify/negative-e2e.log
```

**意义**：这两个缺口不是纸面推论，而是设备上可复现的真实缺陷；新增断言确实能抓到它们（不是恒真断言）。

### 4.2 修复后：同一套测试在真机上全绿

```bash
adb -s 192.168.50.3:5555 install -r app/build/outputs/apk/androidTest/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug-androidTest.apk
adb -s 192.168.50.3:5555 shell am instrument -w -e class com.fongmi.android.tv.tts.ReaderTtsDeviceTest \
  com.silent.android.webhtv.test/androidx.test.runner.AndroidJUnitRunner
```

```text
Time: 102.944
OK (6 tests)
```

覆盖：百度引擎合成+播放推进（含暂停中改语速不出声）、微软 Edge 引擎合成+播放推进（同）、系统引擎无引擎时优雅失败、两个定时用例、阅读页端到端（面板→桥→前台服务→引擎→高亮→后台续读→通知栏控制→路由丢失暂停→章末续读下一章→停止；含「停播后通知不残留」与「续读后通知重新挂上」）。

### 4.3 双 flavor Java / AndroidTest 编译 + 双 flavor 全量 JVM 套件

单次 Gradle 调用（避免重复检查）：

```bash
bash ./gradlew :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac \
  :app:compileLeanbackArm64_v8aDebugAndroidTestJavaWithJavac :app:compileMobileArm64_v8aDebugAndroidTestJavaWithJavac \
  :app:testLeanbackArm64_v8aDebugUnitTest :app:testMobileArm64_v8aDebugUnitTest
```

6 个目标任务全部实际执行：`BUILD SUCCESSFUL in 1m 25s`、`EXIT=0`。解析 JUnit XML：

| flavor | suites | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- | --- |
| leanback | 649 | **4125** | **0** | **0** | 2 |
| mobile | 725 | **4963** | **0** | **0** | 2 |

较 C50 基线（leanback 4087 / mobile 4928）分别 +38 / +35：来源是本分支的朗读用例（`TtsTextSplitterTest` 8 + `TtsHttpRuleTest` 15 + `ReaderTtsBridgeSourceTest` 7）与本次合并带入的 beta 用例（`C47SeekLoadingProgressSourceTest` 等）。

定向核对：`ReaderTtsBridgeSourceTest` 7/7（含本轮新增的桥线程契约用例）、`TtsHttpRuleTest` 15/15、`TtsTextSplitterTest` 8/8、`TtsAudioCache`（无独立用例，逻辑由设备链路覆盖）。

### 4.4 UI token 门禁

`bash scripts/check_ui_tokens.sh` → `UI_TOKEN_STATUS PASS`（EXIT=0）；`UI_TOKEN_CONTRAST pairs=38 failures=0 min=4.28`。
`--strict` 下 `violations=1`，唯一命中 `app/src/mobile/res/layout/item_following.xml`（**不在本次改动集内**，与 C50 基线一致）→ 相对基线**零新增违规**。

### 4.5 静态与结构校验

- 零复活/零丢失：见「合并台账」与三层证据（复活行数 0）；
- `git diff --name-status origin/beta HEAD` = 只含本分支自身改动，无 beta 之外来源；
- `git grep -nE '^(<<<<<<<|>>>>>>>)' -- app/src docs` 命中 0；
- 改动未触及 `res/`、native、依赖锁文件、播放器链路。

### 4.6 回滚

`git revert` 本任务的两个提交（合并提交 + 修复提交）即可回到 `db04c4c8bc3f638980a2983af65170aa09f66439`；无数据迁移、无 ABI/native 变更、无依赖变更。运行时熔断：`AndroidReader.ttsInfo()` 的 `available` 判定与在线引擎失败回退不受本次修复影响。

## 改动清单（本次 C52 相对 `db04c4c8b`）

| 路径 | 类型 | 说明 |
| --- | --- | --- |
| （合并提交 `86e1bfe3c`） | 合并 | 12 路径全部来自 `origin/beta`，逐 blob 与 beta 一致 |
| `app/src/main/java/com/fongmi/android/tv/service/ReaderTtsService.java` | 修改 | F1：`stopped` 不再重贴刚撤掉的通知；`onDestroy` 兜底取消通知 |
| `app/src/main/java/com/fongmi/android/tv/tts/AudioFileTtsEngine.java` | 修改 | F2：暂停中不调 `setPlaybackParams`；`resume` 补 `applyRate` |
| `app/src/main/java/com/fongmi/android/tv/ui/web/WebReaderActivity.java` | 修改 | F4：12 个朗读桥统一回主线程（`postToService`） |
| `app/src/androidTest/java/com/fongmi/android/tv/tts/ReaderTtsDeviceTest.java` | 修改 | 锁定 F1/F2（暂停改语速、通知不残留、续读后通知重挂） |
| `app/src/test/java/com/fongmi/android/tv/ui/web/ReaderTtsBridgeSourceTest.java` | 修改 | 锁定 F4（桥必须切回主线程） |
| `docs/NOVEL-TTS-novel-read-aloud.md` | 修改 | F3：修正音频焦点「GAIN 自动恢复」的不实描述 |
| `docs/C52-beta-merge-review-dev1-20261009.md` | 新增 | 本任务文档 |

## 交付坐标

| 项 | 值 |
| --- | --- |
| 任务起始 HEAD | `db04c4c8bc3f638980a2983af65170aa09f66439` |
| `origin/beta` tip | `29d52ab2385702a9350c3099cd94dd4e54c1240f` |
| 合并提交 | `86e1bfe3ce4edadc874121decf18ed7d99c7e327`（父：`db04c4c8b` + `29d52ab23`） |
| 修复提交 | `32202da2dee86c388b586262f3d1aede19fdc162`（7 路径：6 修改 + 本任务文档） |
| recovery tag | `recovery/C52-beta-merge-review-dev1/20261009140947-32202da2dee8`；坐标提交自带 tag |
| 推送 | `dev1` → `origin/dev1`（`cb8022bf7..32202da2d`，推送后 0 ahead / 0 behind） |
| PR | [#427](https://github.com/Silent1566/webhtv/pull/427) `dev1 -> beta`，**OPEN、未合并**（`mergedAt=null`、`state=OPEN`、MERGEABLE），27 文件 +5373 −122 |
| PR 文件集校验 | `gh api .../pulls/427/files` 分页合计 **27**，与 `git diff --name-only origin/beta HEAD` **逐项一致** |
| 设备 | dev1 机位 `192.168.50.3:5555`（LIO-AN00 / Android 9）；全程 `adb install -r` 覆盖安装，未卸载、未清除应用数据 |
