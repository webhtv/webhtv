# C111 — 电视版(leanback)进入播放页加载卡顿修复

任务 ID：`C111`　分支：`dev1`　基线 HEAD：`abff821ba310ff30f6de2fa09475b94660718d78`
开始时间：2026-10-28 18:16（Asia/Shanghai）　状态：**六处改动（F1/F3、F2、F4、F5、F7、F8）已落地并通过评审与整包编译；F9/F10 调查已结案（F10 建议被否决，§12.4）；交叉配对 A/B 测不出收益且被证明有顺序偏置（§13）⇒ 未提交，等收口决策**

## Recovery anchor

- 目标（完成句）：电视版 leanback 首页卡片直进播放页（原生增强直放）后，进页→首帧窗口内「加载转圈中途停住」的主线程长帧被削掉。
- 验收：emulator-5554 同一探针、同一进入动作，改前/改后各 3 次采样；进页窗口 framestats `trav>40ms` 帧数与 p99 相对基线 **降 ≥30%**，转圈无可见停顿。janky% 不作门禁（C101 已证该模拟器有 ~45% 输入延迟地板）。
- 已定约束：验收包 leanback armeabi-v7a debug；若 libnb 翻译致帧数据失真则改 arm64 对照并显式标注偏差。允许触及播放服务/内核初始化与首帧先后顺序，但须前后对照，起播变慢或首帧黑屏即回滚该项。
- 当前文件/符号：见 §3、§6.4（改点落点）。
- 已完成：R0.5 门禁、R0 分诊（L2）、R-G 访谈收敛、两轮只读取证（§2）、§4 设计研究门禁、§6 基线实测、
  §6.3 机制判定、§6.4 三处阻塞段定位、§6.6 debuggerd 栈证据与 Davey 帧算术、§6.7 debug_log A/B 结案、
  §6.8 密集栈采样（10/10 偏移有效）、
  **F1/F3 + F2 + F4 + F7 + F5 五处改动全部落地、编译通过，并全部通过父级独立评审（§7.1-§7.5）**。
- 已实测（仅含 F1/F3+F2 的包 72AE4214）：早期结论「窗口内帧数 45.3→79.3」已作废，原因是单次 framestats 抓取截断（§9.1）。
- **整包复测已完成（全量包 03EA8A39，§9）**：三档验收判据只达成第三档（起播不变慢：窗口 5991→6058ms / rate 口径 3582→2696ms；无新增黑屏）。
  判据一（trav>40 与 p99 降≥30%）**没有合法同口径基线**——同视频基线三跑 trav>40 全为 0，改后 0/2/0；判据二（转圈无可见停顿）**未达成**，
  每次进页仍 1-3 次 ≥300ms 连续无帧、最长 455-888ms。可宣称的只有 maxStarve 856→483ms（-43.6%）与慢态窗口尾巴消失。
  回滚条件（起播变慢 / 首帧黑屏）未触发，无项需回滚。剩余落点见 §9.7（inflate 274ms、relayoutWindow、RenderThread 侧、ExoPlayer 构建期，均已被 F6/F6b 否决或超出 app 可控面）。
- §7.6 归因回执已到并已据其**否决 F6 与 F6b**（理由见 §8.1/§8.2）。
- **第五处热点（F8，已落地并通过评审，§11）**：`PlaybackPerformanceSetting.rawProfileValue` 由 `Prefers.getPrefers().getAll().get(key)`
  改为 `contains` + `getInt/getLong/getFloat` 类型分派，消除 135 键 / 114324 B 偏好的整表拷贝（一次进场窗口 150-240 次）。语义四情形逐条等价，未加缓存。
- **用户决策（2026-10-29，以正文形式回复，本会话 ask_user_question 连续超时不可用）**：选「继续追剩余落点再提交」，不先收口。
- 之后两轮只读/取证：§10 第四轮密集采样把 438-466ms 冻结段精确归因到 `PlaybackService.onCreate` 的 451ms 同步构建；
  §10.4 的线程归属问题已派只读 subagent（f1239ca9）回答，结论待回。
- **F9/F10 已结案**：§10 把 438-466ms 冻结段归因到 `PlaybackService.onCreate` 的 451ms 同步构建（`new PlayerManager` 175ms + native `libexo_dovi_render` 装载探测 76ms + `MediaLibrarySession.Builder().build()` 57ms + 其余）；
  §12 记录 F10 报告并**否决其「日志管线后台化」建议**（debug_log 整体关掉都降不下停帧，同设备 A/B 直接反证），§12.5 给出落点三分类收束。
- **交叉配对 A/B 已结案（§13）**：6 次交替安装、3 对，全量包在 window/maxStall 上表现为**更慢**（+143ms / +145ms），
  但 `tap→START` 存在 ≈+44ms/分钟的单调漂移且该区间在 VideoActivity 启动之前（改动一行都不执行）⇒ 该差异落在漂移噪声带内，**既不能宣称收益也不能判定回归**。
  可宣称的只剩单次 run 内的插桩事实（initView 尾部三段各 1-9ms、首帧在 initView end 后 85ms、127 条正则与设备身份探测移出窗口、114324 B 偏好不再整表拷贝、onPrepare overlay binder 短路）。
- 下一步（唯一）：**等用户收口决策**——(A) 按现状提交（文档已如实记录「不可测收益/不可判定回归」+ 回滚点）；(B) 追加设计级改动（`ActivityVideoBinding.inflate` 274ms 拆 ViewStub / 页面结构瘦身）；(C) 全部回滚。

## 1. 问题陈述

用户报告：原生增强模式播放页加载优化后，电视版进入仍卡顿，**加载中的转圈效果时不时停一下**。

机制：转圈是 indeterminate 动画，由 RenderThread/动画时钟驱动，本身不吃主线程；主线程一旦有长帧，动画即停帧——「停住再恢复」就是主线程单帧超时。

## 2. 只读取证结论（2026-10-28）

### 2.1 进场链全在一个 tick（subagent A）

- 骨架 `app/src/leanback/java/com/fongmi/android/tv/ui/base/BaseActivity.java:61-70`：inflate → initView → initEvent 全同步。
- `VideoActivity.java:1461` `ActivityVideoBinding.inflate()`：`activity_video.xml` 1412 行 / 115+ id。
- `VideoActivity.java:1539-1593` initView：initTmdbMode(**在 super 之前**) → super.initView → prepareInitialDetailShell → **checkCast(:2194 首次点亮圈)** → setRecyclerView(10 个 adapter) → setVideoView → setViewModel → checkId。
- `VideoActivity.java:1613-1735` initEvent：约 110 处监听器注册。

### 2.2 C101-S1 延迟绑定对电视版已生效，但只挪了一格

- `PlaybackActivity.java:212` 基类默认 `false`；leanback `VideoActivity.java:6396-6399` 与 mobile 均覆盖为 `true`。
- `PlaybackActivity.java:655-668`：`postDelayed(500ms)` 兜底 + 首帧 `OnPreDraw` 后 `post` 立即绑定。
- `PlaybackService.java:141-161` 的 PlayerManager/ExoPlayer/MediaLibrarySession 构造仍在主线程，只是移到首帧之后**一格**。

### 2.3 转圈状态机存在同 tick 抖动（subagent A）

- `VideoActivity.java:2194` checkCast 首次 `showProgress()`，并惰性 inflate `view_progress.xml`（`ProgressLayout.java:200-206`），却被其后的 setRecyclerView/setViewModel/checkId/initEvent 挡到 onCreate 结束才能首次绘制。
- `VideoActivity.java:2270→2294`：同 tick 内 `showProgress()` 紧接 `showFastTmdbPlaybackContent()→showContent()`，典型抖动。
- `VideoActivity.java:2211` 与 2194 双重点亮（缓存命中路径）。

### 2.4 setDetail 无分片

`VideoActivity.java:2758-2822` `setDetail(Vod)` 单 tick 完成：TMDB 解析/策略决策、`TmdbEpisodeSorter.sort`、全量分集标题、`FlagAdapter.addAll`、`checkHistory`(查库)、`checkFlag`(重建选集)、7 字段文本、推荐加载。延迟任务只延后 TMDB 绑定（96ms/96ms），不拆分本体。

### 2.5 设备事实（subagent B）

- emulator-5554：SDK 28 / Android 9；abilist32 含 `armeabi-v7a`；`ro.dalvik.vm.native.bridge=libnb.so`（**libnb 翻译，非 houdini**）；`su 0` 可得 root；Permissive。
- 已装 `com.silent.android.webhtv` 5.6.0 **arm64-v8a**（10-28 14:00）。
- `dumpsys gfxinfo` 可用；`framestats` 对系统包可解析，本 app 需实跑复核；`SurfaceFlinger --latency` 不可用。
- `scripts/perf/loading_jank_probe.sh`：默认 serial **5556**（须改 5554）；prefs 读取需 `su 0`（脚本无 su 且失败被 `|| true` 吞）；`ITEM_XY` 默认 220,390 需实跑核对；`playback_ready` 依赖 debug_log 打开。
- bash：`C:\WINDOWS\system32\bash.exe` 是 WSL 不可用，须用 `G:\Git\bin\bash.exe`。
- 历史产物在仓库外 `F:/Workspace/webtv2/whdiag/perf/`（40 个子目录）。

### 2.6 关键判断

C101 削掉的是「服务初始化冻住转圈」；**未做的是进场链的分帧与顺序**——圈点亮后被同 tick 后续重活冻住，且存在同 tick show→content 抖动。这与用户「转圈时不时停一下」的形态吻合。


## 4. §7 设计研究门禁（2026-10-28，subagent D + 父级复核）

### 4.1 本地事实复核（父级亲自核对，纠正 D 的两处与确认其一处）

| # | 事实 | 核对结果 |
|---|---|---|
| R1 | D 称「`checkCast()`/`tryStartFastTmdbPlayback()` 只在 leanback」 | **成立**。mobile `VideoActivity.java` 无 `private void checkCast`，只有 `:1635 hasInitialPreview()` / `:6103 showInitialPreview()`；leanback `:2190` 有 `checkCast`。两端进场链不同，须分别落点。 |
| R2 | D 称「`ProgressLayout` 初始态是 CONTENT」 | **成立**。`ProgressLayout.java:53-56` `init(){ mState = State.CONTENT; }`；`ensureProgressView()` 只在首次 `switchState(PROGRESS)` 时惰性 inflate（:200-206）。 |
| R3 | D 称「`baseline-prof.txt` 未见 gradle 接线，C101-S10 是否生效需复核」 | **D 判断错误，已用产物证伪**。AGP 7.4+ 对 `app/src/main/baseline-prof.txt` 自动接线，无需 gradle 声明。实测 `Release/apk/leanback-armeabi_v7a.apk` 内含 `assets/dexopt/baseline.prof`(3162B) + `baseline.profm`(493B)。**但** `app/build/outputs/apk/leanbackArm64_v8a/debug/*.apk` 内 **0 个 dexopt 条目**。 |
| R4 | `baseline-prof.txt` 规模 | 3354463 字节 / 31712 行，10561 行以 `H` 开头，0 行以 `[` 开头（合法 HSP 格式）。 |
| R5 | leanback 启动主题 | `leanback/res/values/styles.xml:55-56` 已有 `Theme.Splash`（`windowBackground=@drawable/startup_window_background`），主主题 `:22` 为 `?attr/colorSurface`。 |

**R3 的测量影响（重要）**：baseline profile **只在非 debuggable 构建生效**，而我们用 debug 包做 A/B。故 debug 包上的绝对帧数**比电视用户实际所见更差**；这不影响「同包型前后相对对照」的有效性，但不得把 debug 的绝对值外推给用户。此项写入验收须知。

### 4.2 证据（访问日期 2026-10-28；官方结论取自 web_search 原文引用片段，因 read_page 被 firecrawl 403 拒绝，统一降档 A−）

| 类别 | 出处 | 等级 | 支撑主张 |
|---|---|---|---|
| 官方 | AOSP `LayoutInflater` 类 javadoc："not thread-safe … should only be accessed by a single thread" | A− | inflate 不可随手挪后台，直接否掉整页异步化 |
| 官方 | `AsyncLayoutInflater` 参考页：要求 parent 的 `generateLayoutParams` 线程安全、所有被构造 View 不得创建 Handler / 调 `myLooper()`；不满足则静默回退 | A− | 本布局 88~115 id + Leanback + 多自定义 View，前置条件无法承诺 |
| 官方 | `Choreographer`：`View.postOnAnimation` 在下一显示帧开始回调 | A− | 切帧原语语义正确 |
| 官方 | JankStats 建立在 FrameMetrics（**API 24+**） | A− | minSdk=24 ⇒ 度量在最低版本即可用，无需兼容分支 |
| 官方 | Baseline Profiles：首次启动约 30% 代码执行提速 | A− | profile 方向有官方量化背书（但见 R3 的 debug 限制） |
| 官方 | core-splashscreen javadoc：API 31 以下行为「replicated with the exception of」AVD | A− | splash 在 minSdk 24 能降级但能力有缺口；且只延长背景、不缩短主线程工作 |
| 官方 | App Startup 文档 | A− | 只作用于 Application 阶段，**不覆盖** Activity 进场链 → 对本问题不适用（记录而非跳过） |
| 社区 | SO：后台 inflate 的 `InflateException on EditText`；`AsyncLayoutInflater is inflating on the UI thread` | C | 后台 inflate 真实结局多为**静默回退**或异常 |
| 社区 | SO：异步完成后列表不可见、需手动 `notifyDataSetChanged`；自定义 View 动画更新被排队 | C | **分帧过度 → 首帧内容缺失/闪烁**，须防 |
| 社区 | GapWorker 预取导致 Video 声音残留 → `setItemPrefetchEnabled(false)` | C | 预取/增量有真实副作用，须留可关开关 |
| 未取得 | AsyncLayoutInflater×Leanback 一手 issue、ConstraintLayout 官方限制全文 | — | 网络受限未取得；**结论不依赖它们**，仅记为未解关口 |

### 4.3 三档对比

| 档 | 内容 | 正确性 | 兼容/依赖 | 收益 | 线程安全 | 回滚 |
|---|---|---|---|---|---|---|
| **A 不改** | 保持单 tick | 100% | 无 | 0（缺陷常驻） | 不变 | 不需要 |
| **B 照搬通用** | `AsyncLayoutInflater` 整页异步 / SplashScreen 常驻 | ✗ 静默回退；splash 把「更久背景停留」合法化，违反约束 | 需 asynclayoutinflater 依赖；splash 已在(1.2.0)但无调用 | 薄（initView/initEvent 仍在主线程） | ✗ 自定义 View 构造期行为未审查 | 易 |
| **C 窄口径适配（推荐）** | C1 圈先上屏 / C2 尾部 work 分帧 / C3 消同 tick show→content 抖动 / C4 setDetail 首批分片 | ✓ 只改时序不改语义 | ✓ API24+，**零新依赖**，复用既有 `ActivityLaunch.postOnAnimation` | 中-高，直击两机制 | ✓ 全主线程 | 易，四段各自可关 |
| **D1 ViewStub 拆详情区** | 减小 inflate 工作量本身 | ✓ | ✓ | 最高 | ✓ | 中（`mBinding.xxx` 可能变 null） |
| **D2 度量** | `reportFullyDrawn`（现无）+ JankStats | ✓ | ✓ API24+ | 零功能风险 | ✓ | 不需要 |

### 4.4 决策

- **采用 C1 + C3 + C2（≤3 帧）+ C4 首批**，以 D2 作验收锚点；**D1 列为触发式第二阶段**（仅当实测证明 inflate 占首帧 >50%）。
- **明确不做**：整页 `AsyncLayoutInflater`、inflate 后台化、SplashScreen 常驻、任何新依赖。
- 落点按 R1 分成两端：leanback 侧（`VideoActivity:2190/2255/2271/2758-2822`）与 main 侧共享类（`PlaybackActivity`、`ProgressLayout`）。
- **本轮范围按用户已定口径只治 leanback**；mobile 侧改动一律不做，若 C1/C3 触及 main 共享类，须证明对 mobile 行为等价（compiled both flavors + 单测覆盖）。

### 4.5 未解关口（无设备实测不得写进结论）

1. 首帧耗时在 inflate / initView / initEvent / setDetail 四者间的**真实占比**。
2. 「圈中途停住」机制二选一：(a) tick 内阻塞掉帧（圈卡在动画中间帧）；(b) PROGRESS 从未被绘制就被 CONTENT 覆盖。二者修法不同（(a)→C2 分帧；(b)→C3 顺序），**必须由实测区分**。
3. 电视实际刷新率（50Hz 常见）。
4. `view_progress.xml` 惰性 inflate 的单次成本是否本身即掉帧源。

## 5. 待验证与下一步

- [x] 5554 基线采样跑通（framestats 逐帧 trav/starve）→ §6.1
- [x] 确认 armeabi-v7a debug 包可构建并安装 → §6.1
- [x] §4 最佳实践研究门禁 → §4.4 决策
- [x] 按证据定改动项：F1/F3（分帧+消抖动）、F2（身份预热跳过）——F2 完成(§7.1)，F1/F3 实施中
- [ ] 改后复测 + 定向单测 + 评审

## 6. 基线实测（2026-10-28 18:47–19:50）

### 6.1 采样条件（复测必须同口径）

- 验收包：`bash ./gradlew :app:assembleLeanbackArmeabi_v7aDebug` → BUILD SUCCESSFUL in 5m51s，
  产物 `app/build/outputs/apk/leanbackArmeabi_v7a/debug/app-leanback-armeabi_v7a-debug.apk`（171452796 B）；
  安装后 `primaryCpuAbi=armeabi-v7a`（arm32 未触发降级）。
- 设备 emulator-5554：1920x1080 / density 280；native bridge = **libnb.so**（非 houdini），故绝对 jank 值不可外推真实 ARM 盒子，只做同设备前后对照。
- 探针视频 `/sdcard/Download/probe.mp4`：656x1280，16.566s，video/avc bitrate=3061472 + audio/mp4a-latm。
- 导航（**关键校准**）：单次 `input tap` 只让卡片获焦、不进播放页；可靠序列 = `KEYCODE_DPAD_DOWN` → `tap 217,694`（命中「最近观看」左卡 bounds [48,421]-[387,968]）。
  旧默认 220,390 落在顶部按钮行（y=176-292），**不可用**。
- 窗口定义：`tap_wall` → `playback_ready`（logcat `onTimeChanged: position=...duration=...canSave=true`）。
- `debug_log` 实测 = true（C101 时是 off），全程保持 true 并 `--debug-log keep`。

### 6.2 基线帧级数字（c111-b/run1..run3）

| run | 窗口ms | 窗口内帧 | gfx Total | Janky% | p99 | starve>100 | max starve | trav>40 | max trav | trav p90 | trav p99 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 8861 | 22 | 24 | 79.17% | 600ms | 4 | 566.7 | 3 | 67.5 | 50.4 | 66.8 |
| 2 | 8274 | 17 | 19 | 68.42% | 600ms | 5 | 516.7 | 2 | 52.5 | 40.9 | 51.2 |
| 3 | 8032 | 22 | 25 | 56.00% | 450ms | 4 | 383.3 | 3 | 64.3 | 43.1 | 61.6 |
| **均值** | **8389** | **20.3** | — | — | — | **4.3** | **488.9** | **2.7** | **61.4** | **44.8** | **59.9** |

判读：进页 8 秒窗口只渲染 17–22 帧（≈2.5fps）；starve 峰值 383–567ms 而 trav 仅 52–68ms
→ **病灶是主线程长时间不申请 vsync（被同步任务占住），不是布局建树复杂**。

### 6.3 机制判定（关闭 §4.5 关口 2）

截图 `c111-b/run1/screen-load-early.png` 证实：转圈动画**确实已上屏并在绘制**（叠在播放器框内视频黑底上可见）。
→ 判定为 **(a) 主线程长阻塞掉帧**，排除 (b)「PROGRESS 从未被绘制就被 CONTENT 覆盖」。
→ 修法方向锁定「削减主线程串行阻塞 / 让出帧」，而非「调整 show/hide 顺序」。

### 6.4 三处主线程阻塞段（run1 logcat 时间戳坐实）

> 注：本节 1/2 段的成因已由 §6.6 的栈证据收敛（inflate、Device 探测、徽标正则首编、
> 播放事件快照的 Room 查询等）；F2 已消除第 2 段。

1. **onCreate 早期 ~730ms**（19:46:01.405 → 02.137）：紧接 FontFamily/Typeface 反射后、Clock.start() 前，日志近乎空白。
2. **~169ms**（19:46:04.074 attach done → 04.243 PLAYLIST_CHANGED）：其中 04.425 出现
   `D TV-Shell: Shell command 'getprop ro.serialno' with exit code '0'`（主线程）→ 即 `Device.ensureIdentity()` 首次同步探测。
3. **~933ms**（19:46:04.640 → 05.573）：收尾处 `I Choreographer: Skipped 34 frames!`（34×16.67≈566.7ms，恰等于 run1 max starve）。

**重要噪声标注**：该探针仅 16.566s 且起播位 16.617s → 一进页即 ENDED，触发
`checkEnded → checkNext → onNext` → Toast「已经是最后一集了！」（`Notify.show` 调用栈）。
**逐跑核实：三跑全部存在该噪声**（endedToast 各 1 次；首个 onTimeChanged 的 position 分别 16645/16617/16606，duration 均 16566），
故它是**稳定共有噪声**而非偶发。尝试换更长探针失败：/sdcard/Download 另两条 mp4/mkv 各 6478812 B（疑同名副本）、
/sdcard/Movies 与 /sdcard/Music 为空、本机无 ffmpeg → **复测沿用 probe.mp4** 以保持可比性，
并在报告中把 ENDED 分支开销显式标注，避免其变化被误记为优化收益。
该栈同时是「播放事件经 MediaSession/MediaController/ListenerSet 在主线程派发」的铁证。

**已推翻的错误假设**：曾据 PID 误当 TID 得出「解码器在主线程初始化」。按正确列重解析后：
`MediaCodec = tid 12527`、`ACodec = tid 12528`（CodecLooper/MediaCodec_loop），
设备 `/proc/<pid>/task` 共 45 线程含 ExoPlayer:Playb/Backg/Loade 等独立线程。
→ 解码器初始化**不在**主线程；主因是「同 tick 串行发出大量 EventLogger/MediaSession 回调 + 一次性建树」。
`ExoUtil.java:329` `if (BuildConfig.DEBUG) player.addAnalyticsListener(new EventLogger());` 是 debug 包回调洪水来源之一。

### 6.5 测量管线（可复现，已用基线反证）

- `scripts/perf/leanback_enter_playback_probe.sh`（新增）：同口径采样，修掉旧 `loading_jank_probe.sh` 的
  默认串口 5556、无 DPAD 聚焦、只解析 gfxinfo 汇总段三个问题。
- `scripts/perf/leanback_enter_playback_analyze.py`（新增）：逐帧 starve/trav + video-flow 分段对齐 + gfxinfo 汇总。
- 复现校验：对本节基线跑分析器，输出 window 8861/8274/8032、max_starve 566.7/516.7/383.3、trav_gt40 3/2/3，
  与 §6.2 完全一致 → 口径无漂移。
- `boot_wall = wall_ms − uptime_ms = 1793166501877`（实测偏差 <1s，仅用于帧↔日志对齐）。
- 已知不可用手段：atrace/systrace（`/sys/kernel/debug/tracing` 不存在）、`kill -3`（logcat 抓不到 dump）、
  `dumpsys SurfaceFlinger --latency`（128 行仅 2 行非 0）。
- **更正**：早先记「`debuggerd -b` 单次约 3.0s，窗口内不可多次采样」不成立。实测单次
  `adb shell "su 0 debuggerd -b <pid>"` 用时 **430ms**、输出约 2400 行；连续 6 次采样的文件 mtime
  逐秒递增、与请求偏移同步 → 可在进页窗口内多点采样（§6.6 即以此建立阻塞点分布）。
- **累计帧率探针** `scripts/perf/leanback_enter_rate_probe.sh`（新增）：设备侧每 ~200ms 采一次
  `dumpsys gfxinfo <pkg> | grep -m1 Total`，免疫 framestats 约 120 帧的环形缓冲截断。
  **framestats 只能用于同缓冲内的 starve/trav 相对值，不能判「这段时间一帧没画」**（实测同一跑里
  VideoActivity 帧跨度仅 2.279s 而采集跨 7.5s，早期帧已被挤出）。

### 6.6 主线程阻塞点（debuggerd 栈证据，改后包）

采样手段 `F:/Workspace/webtv2/whdiag/perf/c111-stacks/bt_probe.sh` + 解析器 `bt_names3.py`（按 `sysTid=<pid>` 取 main 线程块）。
命中点（偏移自 tap 注入起算）：

| 偏移 | 主线程最内层 app 帧 | 上游链 |
|---|---|---|
| 300ms | `ActivityVideoBinding.inflate` | `VideoActivity.getBinding ← BaseActivity.onCreate` |
| 600ms | `ExoUtil.buildPlayer` | `ExoPlayerEngine.<init> ← PlayerManager.buildEngine ← PlayerManager.<init> ← PlaybackService.onCreate` |
| 900ms | `PlaybackSystemConditionMonitor.registerListenersLocked` | `beginSession ← PlayerManager.beginPlaybackTrace ← PlayerManager.start ← PlaybackActivity.startPlayer ← VideoActivity.setPlayer` |
| 1200/2200ms | `MediaBadgeCatalog.patterns` | `MediaBadgeResolver.addSourceBadges ← resolve ← PlayerOsdController.setMediaBadges ← render ← update` |
| 1500ms | `DesktopLyricsWindow.canShow` | `update ← setForeground ← PlaybackService.setPlaybackForeground ← PlaybackActivity.onServiceConnected`（并叠 `PrettyFormatStrategy.logHeaderContent ← SpiderDebug.log` 同步日志格式化） |
| 2600ms | `Vod.getName ← CatAction.blank ← CatAction.shouldYieldDetail` | `VideoActivity.setDetail`（实测为采样恰好落在入口，`blank()` 首条件即短路，**不是**可消除的阻塞） |
| 3000ms | `PlaybackSystemConditionMonitor.currentNetworkIdentityDigest` | `ExoPerformanceSetting.currentNetworkDigest ← refreshAutoSession ← PlaybackAnalyticsListener.observeAutoThresholds ← onEvents` |
| 3400ms | `ConfigDao_Impl.findById ← Config.find` | `PlaybackConfigIdentity.keyForCid ← PlaybackRecord.from:117 ← PlaybackEventCollector.snapshot ← onPlaybackStateChanged ← PlaybackService$3` |
| 1800/3800ms | 空闲（`epoll_pwait → Looper::pollInner` / `__vdso_clock_gettime`） | — |

**Davey 帧算术（决定性）**：debug_log ON 时 tap+3.0s 那次
`OpenGLRenderer: Davey! duration=706ms` 的分解——
IntendedVsync→Vsync 683.3ms、Vsync→PerformTraversalsStart 6.9ms、PerformTraversalsStart→DrawStart 4.2ms、
DrawStart→SwapBuffers 11.5ms、SwapBuffers→FrameCompleted 0.84ms。
⇒ **694ms 全部消耗在「主线程迟迟不开始绘制」，绘制与交换合计仅 12.4ms**。这排除了「绘制本身慢」，
把病源锁定为「主线程被同步工作占住」，与 §6.3 判定一致。

### 6.7 debug_log 对停帧无显著影响（A/B 已结案）

同一累计帧率探针、同设备、动画三开关=1，分别在 debug_log OFF 下测基线包与改后包各 3 跑
（目录 `F:/Workspace/webtv2/whdiag/perf/c111-logoff-{base,after}-r1..r3`）：

| | window ms | frames | fps | stalls≥300ms | maxStall ms |
|---|---|---|---|---|---|
| OFF 基线 | 2616 / 2432 / 4402 | 14 / 19 / 20 | 5.35 / 7.81 / 4.54 | 1 / 1 / 2 | 894 / 646 / 993 |
| OFF 改后 | 2818 / 4159 / 2782 | 18 / 18 / 18 | 6.39 / 4.33 / 6.47 | 1 / 2 / 1 | 684 / 1430 / 895 |

中位数几乎重合（maxStall 894 vs 895；sumStall 894 vs 895），且与 ON 时已知区间（改前 659-1144ms、
改后 650-1112ms）完全重叠。探针条件已核验：6 份 logcat 中 `SpiderDebug`/`video-flow` 行数均为 0。
⇒ **日志格式化（orval `PrettyFormatStrategy` / `SpiderDebug`）不是停帧来源，不投入优化**。
单一 1430ms 为长尾离群，n=3 不足以支撑「OFF 更差」。

### 6.8 密集栈采样：400-2400ms 整段早于解码器就绪（10 轮，各采 1 点）

手段：每次进页只采 1 次 `debuggerd -b`，10 次进页覆盖 tap+400/600/…/2200ms（产物
`F:/Workspace/webtv2/whdiag/perf/c111-dense/off<偏移>/`，10/10 有效）。采样时刻 = 名义偏移 + 本机 tap 注入耗时（158-237ms）。

| 名义偏移 | 实际采样@ | 最内层 app 帧 / 状态 |
|---|---|---|
| 400 | 431 | `ActivityVideoBinding.inflate` |
| 600 | 608 | `ActivityVideoBinding.inflate` |
| 800 | 816 | 无 app 帧 → `ThreadedRenderer.setStopped`（等 RenderThread） |
| 1000 | 1025 | **空闲**（`__kernel_vsyscall`/`__vdso_clock_gettime`） |
| 1200 | 1225 | 无 app 帧 → `ViewRootImpl.relayoutWindow`（等 WMS binder） |
| 1400 | 1413 | `ExoUtil.buildPlayer`（等 PlaybackLooperProvider HandlerThread） |
| 1600 | 1625 | `DesktopLyricsWindow.canDrawOverlays` |
| 1800 | 1818 | `DesktopLyricsWindow.canDrawOverlays` |
| 2000 | 2010 | `MediaBadgeCatalog.patterns`（ICU 内编译正则） |
| 2200 | 2230 | `MediaBadgeCatalog.patterns` |

**关键判定**：10 轮的采样时刻**全部早于** `videoDecoderInitialized`（tap+2908~3811ms）与
`renderedFirstFrame`（tap+3052~4325ms）。⇒ 400-2400ms 这段整段发生在解码器就绪之前，
**不是解码/首帧造成的**，而是 Activity 启动 + `PlaybackService.onCreate` + `onPrepare` → `startPlayer`
这条同步链造成的。1000ms 处主线程纯空闲，说明该时刻连窗口首帧都还没出来（不是应用可见后的卡顿）。

`DesktopLyricsWindow.canDrawOverlays` 是本次新发现的真热点：`PlaybackService.onPrepare()`
（`PlaybackService.java:632-636` → `desktopLyrics.refresh(player)`）每次准备都无条件走一次
`Settings.canDrawOverlays` binder 权限查询（完整栈：`__ioctl → IPCThreadState::talkWithDriver →
BinderProxy.transactNative → IActivityManager$Stub$Proxy.checkPermission → ContextImpl.checkCallingOrSelfPermission →
Settings.isCallingPackageAllowedToPerformAppOpsProtectedOperation → Settings.canDrawOverlays →
DesktopLyricsWindow.canDrawOverlays+4 → canShow+30 → update+4 → refresh+2 → PlaybackService.onPrepare`）。
`DesktopLyricsWindow.canShow`（:122-144）第一句就是 `boolean overlay = canDrawOverlays();`，
而 `:129` 的其余条件（`!foreground && !activityForeground && enabled && playing && (audioContent || inferredAudio)`）
全是寄存器级布尔量 —— 顺序可以直接短路，语义不变。

## 7. 实施记录

### 7.1 F2 · Device 身份后台预热（已通过父级独立评审）

- **动机**：§6.4 阻塞段 2 的实证——`19:46:04.425` 主线程 tid 12385 打出
  `D TV-Shell: │ Shell command 'getprop ro.serialno' with exit code '0'`，
  即 `Device.ensureIdentity()` 的首次同步探测落在进场窗口内（前置 `04.407`/`04.420` 两条
  `getprop`、`serialno_prop` 的 avc denied 审计行，ppid=12385 亦为主线程）。
- **改动**（2 文件 / +22 行）：
  - `app/src/main/java/com/fongmi/android/tv/bean/Device.java:84-93` 新增 `public static void warmIdentity()`，
    体内只调既有 `ensureIdentity()`（:70-82 的 DCL 一字未改）。
  - `app/src/main/java/com/fongmi/android/tv/App.java:17` 新增 import；`:143` 在
    `resumeBackgroundServices()` 与 `warmSubtitleProviders()` 之间插入 `warmDeviceIdentity()`；
    `:160-167` 方法体 `Task.execute(Device::warmIdentity)`。复用既有固定线程池
    （`Task.java:100-102` → `executor`），未 new Thread、未 App.post。
- **父级独立评审：通过**（逐项自行核对，未采信回执）：
  - 代码事实：`identityReady` 为 `volatile`（Device.java:62）；6 个普通静态字段先写、
    `identityReady = true` 最后写（:80）→ release/acquire 成立，主线程不会读到半初始化身份。
  - 失败路径：`catvod/src/main/java/com/github/catvod/utils/Shell.java:12-25` 整体
    `catch (Exception)` 后 `return ""`（亲读），故 `getSerial()` 不抛；预热最差只是把字段
    存成默认值，与「预热从未发生」的取值等价。
  - 语义边界：`get()` / `identityUuid()` 的返回值与字段语义未变，预热只提前触发同一缓存。
  - 编译：回执 `compileLeanbackArmeabi_v7aDebugJavaWithJavac` BUILD SUCCESSFUL；
    最终 `assembleLeanbackArmeabi_v7aDebug` 与整包行为留待 §7.3 复测轮一并确认。
- **残留边界（如实记录）**：主线程若恰撞预热持锁期仍会等这把锁（getprop 量级，毫秒级），
  但该窗口已从「播放页进场」移到「App onCreate」；这是「提前付成本」方案固有、不可再消除的部分。

### 7.2 F4 · MediaBadgeCatalog 127 条正则预热（已通过父级独立评审）

- **动机**：§6.6 tap+1200ms 与 §6.8 tap+2000/2200ms 三次命中
  `MediaBadgeCatalog.patterns+62 ← MediaBadgeResolver.addSourceBadges+84 ← resolve+134 ←
  PlayerOsdController.setMediaBadges ← render ← update`，栈内最深处停在 ICU
  `icu_60::UnicodeSet::closeOver+288 → RegexCompile::setEval/doParseActions →
  RegexPattern::compile → Pattern.compile`（即 127 条正则首编，源码注释自称约 55ms）。
- **改动**（1 文件 / +12 行；`MediaBadgeCatalog.java` 零 diff）：
  - `app/src/main/java/com/fongmi/android/tv/App.java` 新增 `import com.fongmi.android.tv.player.MediaBadgeCatalog;`
  - `onCreate` 内 `warmSubtitleProviders();` 之后新增 `warmMediaBadgePatterns();`
  - `private void warmMediaBadgePatterns() { Task.execute(MediaBadgeCatalog::patterns); }`
  - 选型：`patterns()` 无参、天然满足 `Runnable`，不额外新增 `warm()` 转发层，避免扩大公开 API 表面。
- **父级独立评审：通过**：
  - 语义：只改「首次调用时机」。`MediaBadgeCatalog.java:165` `private static volatile Pattern[] compiled;`
    与 :174-190 的 DCL 一字未动；并发首次由 `synchronized (MediaBadgeCatalog.class)` 保证只产出一份数组，
    `compiled` 的 volatile 写发布该数组，调用方读到的仍是同一套规则顺序。
  - 失败路径：`Task.execute`（`Task.java:25-33` 的固定线程池）抛异常不影响主流程；
    即便预热线程与主线程并发首次进入，DCL 只多一次无用的重复编译，不产出第二份可见数组。
  - 编译：`compileLeanbackArmeabi_v7aDebugJavaWithJavac` BUILD SUCCESSFUL in 35s（2 executed / 45 up-to-date）。
- **残留边界**：预热发生在 `App.onCreate`，即冷启动期。若用户冷启动后立刻进播放页，该 55ms 仍可能落在进页窗口内，
  只是不再与进页同步链叠加在同一 tick。真实收益需在 §7.6 复测轮按「进页窗口内是否还有 patterns 栈」判定。

### 7.3 F7 · PlaybackConfigIdentity 进程内缓存（已通过父级独立评审）

- **动机**：§6.6 tap+3400ms 命中 `ConfigDao_Impl.findById+22 ← Config.find+16 ←
  PlaybackConfigIdentity.keyForCid ← PlaybackRecord.from+102 ← PlaybackEventCollector.snapshot+34 ←
  PlaybackEventCollector.onPlaybackStateChanged+14 ← PlaybackService$3.onPlaybackStateChanged+20`，
  即**主线程每收到一次播放状态回调就同步查一次 SQLite**（`keyForCid` 与 `nameForCid` 各查一次）。
- **改动**（2 文件）：
  - `app/src/main/java/com/fongmi/android/tv/playback/PlaybackConfigIdentity.java:37-41` 新增
    `KEY_CACHE`/`NAME_CACHE`（`ConcurrentHashMap<Integer,String>`）、`CACHE_LOCK`、`cacheEpoch`；
    `:47-53` `public static void invalidateAll()`（synchronized 内 `cacheEpoch++` 后清两表）；
    `:65-70` `publish(cache, cid, value, epoch)` —— 空值不发布，且 synchronized 内
    `if (cacheEpoch == epoch) cache.put(...)`，堵死「读到旧行 → 并发写入清空 → 旧值被写回」的悬挂写回。
  - `:80-96` `keyForCid` 命中即返回；未命中仍走原 `Config.find` → `normalizeKey(config.getInterfaceKey())`
    非空则 publish 后返回；为空则 `key = normalizeKey(config.ensureInterfaceKey()); config.save(); return key;`
    —— **写库副作用完整保留**（此时不 publish，因 `save()` 已触发 `invalidateAll()`）。
  - `:98-108` `nameForCid` 同构；`config == null` 或 desc 为空 → 回落 `currentName()`，不缓存。
  - `app/src/main/java/com/fongmi/android/tv/bean/Config.java` 五处 DAO 写路径全部追加
    `PlaybackConfigIdentity.invalidateAll();`：`:96-98` 静态 `delete(url)`、`:100-103` 静态 `delete(url,type)`、
    `:451-452` `insert()`、`:461-462` `save()`、`:474-477` 实例 `delete()`；
    `update()`（`:466-471`）经 `return save();` 传递失效。
- **父级独立评审：通过**：
  - 写点完备性：grep 全仓 `getConfigDao().(insert|insertOrUpdate|update|delete)` 仅上述 5 处。
  - 已知残余窗口：`app/src/main/java/com/fongmi/android/tv/bean/Backup.java:128` `clearAllTables()` 与
    `:132/:147/:197-198` 四条写入未加失效（父级禁改范围）。分析：三条恢复入口
    `AppBackup.java:239/:259`、`server/process/Action.java:400` 之后都会经 `BaseConfig.java:119`
    `config.update()` → `save()` → `invalidateAll`，故旧缓存最终仍被清；两者之间的短暂窗口内若恰有播放事件
    快照，可能读到已删除配置的旧键——影响限于一条历史记录的 configKey 字段，不影响播放行为。如实记录，不修。
  - 源码契约测试：`app/src/test/java/com/fongmi/android/tv/bean/StableInterfaceIdentityAcceptanceTest.java:51-52`
    要求本文件含字面量 `"normalizeKey(config.getInterfaceKey())"` 与 `"keyForUrl(config.getUrl())"`；
    父级 grep 确认仍在 `:86` 与 `:143`，测试不受影响。
  - 编译：BUILD SUCCESSFUL in 9s。

### 7.4 F5 · DesktopLyricsWindow.canShow() 短路 overlay 权限 binder 查询（已通过父级独立评审）

- **动机**：§6.8 tap+1600ms 与 +1800ms 两次命中
  `__ioctl → IPCThreadState::talkWithDriver+375 → BinderProxy.transactNative+184 →
  IActivityManager$Stub$Proxy.checkPermission+54 → ContextImpl.checkCallingOrSelfPermission+20 →
  Settings.canDrawOverlays+18 → DesktopLyricsWindow.canDrawOverlays+4 → canShow+30 → update+4 →
  refresh+2 → PlaybackService.onPrepare+16 → PlayerManager.setMediaItemNow+396`。
  即**每次起播的 `onPrepare` 都同步走一次 binder 权限查询**。
- **改动**（1 文件 / 3+3−）：`app/src/main/java/com/fongmi/android/tv/player/lyrics/DesktopLyricsWindow.java:122-144`
  ```java
  // 改前
  boolean overlay = canDrawOverlays();
  boolean canShow = !foreground && !activityForeground && enabled && overlay && playing && (audioContent || inferredAudio);
  // 改后
  boolean eligible = !foreground && !activityForeground && enabled && playing && (audioContent || inferredAudio);
  boolean canShow = eligible && canDrawOverlays();
  ```
- **父级独立评审：通过**（逐项自行核对，未采信回执）：
  - 等价性：`&&` 的操作数全为纯读取——`foreground`/`audioContent` 是字段读；
    `App.activity() != null`；`PlayerSetting.isDesktopLyrics()`；`PlayerManager.isEmpty()`（:705）、
    `PlayerManager.isPlaying()`（:573-575 仅转发）；`LyricsController.isAudioContent()`（:375-378 只读 url/metadata）。
    故换序不改变任何输入下的返回值；唯一带 IPC 的操作数被移到链尾。
  - `canDrawOverlays()` 本体（`:146-148` `Build.VERSION.SDK_INT < M || Settings.canDrawOverlays(context)`）一字未动。
  - 未引入权限结果缓存（运行时授权变化会引入陈旧性），只做短路。
  - **命中路径已亲验**：`PlaybackActivity.java:1322`（`onServiceConnected`）与 `:1349`（`onResume`）调
    `mService.setPlaybackForeground(true)` → `PlaybackService.java:426` `desktopLyrics.setForeground(foreground)` →
    `DesktopLyricsWindow.java:57-60` 写 `this.foreground = foreground`。进播放页时 activity 亦在前台
    （`App.activity() != null`）。故 `onPrepare`（`PlaybackService.java:633-635`）里的这次 binder 调用彻底消失。
  - 仍会调用：仅当「退到后台 + 桌面歌词开关开 + 正在播放 + 判定为音频内容」全成立，此时每次 update/refresh 查一次，
    与原语义一致。
  - 编译：BUILD SUCCESSFUL in 8s（javac 真实执行，2 executed / 45 up-to-date）。
- **可见副作用（如实记录）**：诊断日志字段 `overlay=<bool>` 改为 `eligible=<bool>`（`:134`）。
  原因：保留 `overlay` 字段就必须强制求值，等于优化作废。父级 grep 确认 `scripts/` 与 `docs/` 下无
  `overlay=` / `desktop-lyrics` 的消费方（三个 perf 探针脚本都不解析），`show=` 等其余字段名与拼接顺序未变。

### 7.5 F1/F3 · initView 尾部跨帧分片 + 消除同 tick 转圈抖动（已通过父级独立评审）

- **动机**：§2.1/§2.3——`BaseActivity.onCreate` 一个 tick 跑完 inflate → initView → initEvent；
  `VideoActivity.java:1539-1593` 的 initView 后半段（setRecyclerView 构造 10 个 adapter、setVideoView、
  setViewModel、checkId 发网络请求）把首帧挡到 onCreate 结束；且 `tryStartFastTmdbPlayback`
  `:2270` `showProgress();` 紧接 `:2271` `showFastTmdbPlaybackContent();` 在同一 tick 内先点转圈再点内容。
- **改动**（`app/src/main/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`，+142/−13）：
  - 新增 `ArrayDeque<Runnable> mInitViewStages` 与 `INIT_VIEW_STAGE_FALLBACK_DELAY_MS = 500`（`:364`）。
  - `initView` 中 `setViewModel()` **保持同步**（注释说明 `onServiceConnected` 可能先到，且 immersive-audio
    路径经 `playerContent()`/`updateActionQuality()` 解引用 `mViewModel`）；其后 `:1588` 起改为
    `deferInitViewStage("1-recycler", …)`（setRecyclerView + registerFlagSelectionListener）、
    `"2-chrome"`（setShortDisplay + setVideoView）、`"3-detail"`（checkId），再 `launchInitViewStages()`。
  - `launchInitViewStages()`：`root.getViewTreeObserver().addOnPreDrawListener` 等首帧绘制后移除并 `root.post(pump)`；
    `root.postDelayed(…, 500ms)` 兜底（`mInitViewStagesPumped` 防重复）。
    `scheduleNextInitViewStage()` 用 `root.postOnAnimation(() -> root.postOnAnimation(() -> root.post(pump)))`
    保证至少两帧间隔、**严格保序**。
  - `pumpInitViewStage()` 在 `isFinishing()`/`isDestroyed()` 时清空队列并打 `dropped` 日志。
  - `:1497` 改为 `if (!detailRequested && mViewModel != null) checkId();`；`:9262-9268` `onDestroy` 的
    `removeObserver` 全部加 `mViewModel != null` 保护。
  - `:1738` 内联的 FlagSelectionListener 注册抽成 `registerFlagSelectionListener()`（幂等）。
  - **F3**：`:2230` 新增 `private boolean shouldRequestCachedDetailLoading()` ——
    `if (shouldRevealShellWhileLoading()) return false; if (hasInitialPreview() || mAudioStageVisible) return false;
    return !isIntentTmdbPlayback();`，即只有本 tick 确实没有可展示内容时才请求整页转圈。
- **父级独立评审：通过**：每段执行时打 `initView stage-%s begin/end` 与
  `initView stages queued/first frame drawn`；实测（§6 打点轮）各 stage 已降到 1-9ms，
  `initView end cost=27ms` 之后 **85ms 即出首帧**（`initView first frame drawn sinceLaunch=0ms stages=3`）。
- **F3 未被本轮场景覆盖（如实记录）**：F3 只落在 `setCachedTmdbDetail()`（`:2224-2241`）内。
  实测 `c111-anim-after-dbg/run1` 与 `run2` 的 `logcat-load-full.txt` 中 `detail cache hit`、
  `fast tmdb`、`progress reveal` 命中数均为 **0**（`video-flow: │ detail` 为 2，即走的是 `getDetail()` 网络路径），
  说明探针场景下 `VodDetailCache` 未命中，`setCachedTmdbDetail` 根本没被执行
  ⇒ **F3 的正确性靠代码等价性论证（`shouldRequestCachedDetailLoading()` 三条件全部来自既有私有判定
  `shouldRevealShellWhileLoading()`/`hasInitialPreview()`/`mAudioStageVisible`/`isIntentTmdbPlayback()`），
  本轮复测不对它提供经验证据；不得据此宣称 F3 已生效。**
- **一处时序风险已单独核对（通过）**：FlagSelectionListener 原先在 `initEvent`（`:1738`）同 tick 注册，
  F1 后改由 stage-1 注册，而 `setDetail` 会在 `:2791` `mFlagAdapter.addAll(...)` 填充线路列表。
  若 addAll 早于 listener 注册，初始选中事件就丢了。核对依据：
  (a) `setDetail` 只由 `checkId()`（stage-3）或 `applyCachedTmdbDetailNextFrame` 的 16ms post 触发，两者都晚于 stage-1；
  (b) `mFlagAdapter` 在 `setRecyclerView()` 内构造，故 `registerFlagSelectionListener()` 的 `mFlagAdapter == null` 守卫在 stage-1 时已不成立；
  (c) 实测打点 `flag selection listener registered flags=0`（48.580）早于 `detail finish cost=46ms`（49.118），
  即注册时列表确为空、填充发生在之后。⇒ 初始选中事件不会丢。
- **残留边界**：F1 只把建树工作量分散到后续帧，未减少总量；复测显示窗口内帧数 +75%、`trav>40` 归零，
  但**停帧次数与最长停帧无改善**——这正是 §6.7/§6.8 判定「病灶在解码器就绪前的同步链」的依据。

## 8. 下一轮：整包复测与三档验收

### 8.1 F6 决策：不接管播放线程（否决）

`fcdf6b52` 的只读归因回执（父级逐条核对源码确认）：

- 整条构建链在主线程同步串行：`PlaybackService.java:146 new PlayerManager(this)` → `PlayerManager.java:400-405 buildEngine(playerType, HARD)` → `PlayerManager.java:6016 new ExoPlayerEngine(...)` → `ExoPlayerEngine.java:212-223 ExoUtil.buildPlayer(...)` → `ExoUtil.java:309 ExoPlayer player = builder.build()` → `F:/temp/m3src/ExoPlayerImpl.java:396-421 new ExoPlayerImplInternal(...)`。
- 唯一等待点是 `F:/temp/m3src/PlaybackLooperProvider.java:78-91`：`internalPlaybackThread = new HandlerThread("ExoPlayer:Playback", THREAD_PRIORITY_AUDIO); internalPlaybackThread.start(); playbackLooper = internalPlaybackThread.getLooper();` —— app 侧无任何 join/await（全仓仅 `app/src/main/baseline-prof.txt` 命中 getPlaybackLooper）。
- 消除它的唯一入口是 `F:/temp/m3src/ExoPlayer.java:1288-1294 setPlaybackLooper(Looper)`（`setPlaybackLooperProvider` 是 `@RestrictTo(LIBRARY_GROUP)`，不可用）。一旦传入外部 Looper，`PlaybackLooperProvider.java:104` 的 `internalPlaybackThread != null` 不再成立，**线程退出责任转移到 app**，且 `app/src/main/java/com/fongmi/android/tv/ui/dialog/TmdbVideoPlayerDialog.java:235` 的同进程第二播放器会共享同一 Looper 的调度。

**否决理由（不是「零行为变化」）**：接管播放线程等于改变线程命名/优先级归属、跨播放器共享调度与退出责任，超出本轮「可回滚的最小改动」边界，且一旦泄漏线程就是稳态资源缺陷。**F6 不实施。**

### 8.2 F6b 决策：不预热能力探测与缓存（否决，附硬理由）

回执建议的「零行为变化」替代方案是预热 `ExoUtil.java:560 getPlaybackCapabilityReport()` 与 `MediaSourceFactory.java:108 getCache()`。父级亲读后否决：

- `getPlaybackCapabilityReport()` → `ExoUtil.java:574-582 detectPlaybackCapability` → `:687-709 getDisplayProfile` 会把 **`display.getMode()` 当下的刷新率**固化进 `playbackCapabilityReport` 静态缓存。App 启动时读到的是首页模式，播放期可能已切到 24p/50p（`ExoUtil.java:293-298` 本身就挂了 `setVideoChangeFrameRateStrategy`）⇒ 预热的代价是让 `currentRefreshRate` 固化成一个**非播放态的值**，会改变 `EnhancedVideoProfile`/解码器档位选择。这不是无感优化，是可能改变播放质量的语义变化。
- `getCache()` → `:108-118` 会 `new SimpleCache(dir, LeastRecentlyUsedCacheEvictor, StandaloneDatabaseProvider)`，在 App 启动就打开缓存 DB/文件锁，并与 `App.java:147` 30s 后启动的 `CacheScheduler.get().start()` 并存；对从不看视频的用户变成无条件启动期磁盘 IO 与 DB 创建。收益率（只省一次 `FileUtil.getDirectorySize`）与风险不匹配。

⇒ **F6b 亦不实施。本轮代码改动就此封口为 F1/F3、F2、F4、F5、F7 五项。**

### 8.3 残留冻结段的归因边界（先说清，避免把设备噪声算成 app 缺陷）

F9 密集采样里 `tap+800ms` 的栈是 `ThreadedRenderer.setStopped`（等 RenderThread 的 `std::future::get`），`tap+1000ms` 主线程**纯空闲**（`__vdso_clock_gettime` 仅 3 帧），而按 `Skipped N frames` 反推该帧冻结区是 [888,1438]。即那 550ms 里主线程没活干、帧仍未完成 ⇒ 成本在 RenderThread/GL 侧。本机是 arm32 经 **libnb** 翻译跑在 x86_64 上（`primaryCpuAbi=armeabi-v7a`，`ro.dalvik.vm.native.bridge=libnb.so`），GL/EGL 调用全部经翻译层，且 logcat 出现 `EGL_adreno eglSurfaceAttrib error 0x3009 (EGL_BAD_MATCH)` / `Failed to set EGL_SWAP_BEHAVIOR`。⇒ **该段不可由 app 代码消除，也不得外推到真实 ARM 电视盒。** 验收只能按「同设备同探针相对改善」读，不能按绝对帧率读。

## 9. 整包复测（F1/F3+F2+F4+F5+F7 全量）

### 9.1 先纠正一处口径错误：单次 framestats 抓取会截断，窗口内帧数此前被低估

`dumpsys gfxinfo framestats` 是约 120 帧的环形缓冲。上一轮拿「单次 load-full 抓取」算窗口内帧数，基线被截断到 133 行（而 gfxinfo `Total frames rendered` 同期是 228），
本轮改为 **load-early + load-full 两次抓取按 FrameCompleted 去重合并**后重算，基线实际有 202-230 帧被看到。
⇒ §6/§7 里「窗口内帧数均值 45.3→79.3（+75%）」这个结论 **作废**，那是两次抓取覆盖度不同造成的假象，不是改动效果。

### 9.2 合并口径下的帧级对照（同设备、同探针、同进入动作）

| 数据集 | run | 窗口 ms | 窗口内帧 | fps | trav>40 | travP99 ms | starve>100 帧 | maxStarve ms |
|---|---|---|---|---|---|---|---|---|
| 基线 c111-anim1（debug_log=OFF） | 1 | 6415 | 87 | 13.6 | 0 | 36.4 | 5 | 933 |
| | 2 | 5788 | 154 | 26.6 | 0 | 24.5 | 5 | 833 |
| | 3 | 5769 | 146 | 25.3 | 0 | 29.0 | 5 | 800 |
| | **均值** | **5991** | **129.0** | **21.83** | **0** | **30.0** | **5.0** | **856** |
| 全量包 03EA8A39（debug_log=ON） | 1 | 6198 | 59 | 9.5 | 0 | 25.7 | 5 | 550 |
| | 2 | 6090 | 58 | 9.5 | 2 | 45.1 | 7 | 467 |
| | 3 | 5886 | 58 | 9.9 | 0 | 31.9 | 4 | 433 |
| | **均值** | **6058** | **58.3** | **9.63** | **0.67** | **34.2** | **5.3** | **483** |
| 全量包（debug_log=OFF 匹配组） | 均值 | 6017 | 55.0 | 9.15 | 0.67 | 34.5 | 4.7 | 511 |

**逐条读这张表（不美化）**：

- **验收判据一（trav>40 帧数与 trav p99 相对基线降 ≥30%）无法达成，也不该用它判定**。与全量包同视频同口径的基线 c111-anim1，合并后 `trav>40` 三跑全是 **0**，p99 是 24.5-36.4ms；全量包是 0/2/0、p99 25.7-45.1，**改后反而略差且三跑不一致**。
- 那条「基线 trav>40 = 3/2/3、p99 52-67ms」不来自本口径：它是 c111-b 批次（`/sdcard/Download/probe.mp4`，656x1280、16.566s，窗口 8032-8861ms），与本节用的 `/sdcard/cold_m1.mp4`（1080x1920、29.827s，窗口 5886-6198ms）**是两支不同视频、窗口长度差 40%**。父级用合并口径重算了 c111-b：merged 24/20/25 帧（≈Total，未截断），trav>40 仍为 3/2/3、maxTrav 67.5/52.5/64.3 ⇒ **不是截断造成的假数，是不同探针视频与窗口长度造成的不可比**。判据一因此在本场景下**没有任何一支合法基线可用**，必须宣布不可作为门禁。
- **窗口时长没变慢**：5991 → 6058ms（+1.1%），率采样口径 3582 → 2696ms（-24.7%，见 §9.3）。约束「起播不得变慢」满足。
- **窗口内帧数与 fps 反而下降（129→58 / 21.8→9.6fps），但这不是回归**：终态截图逐像素同构（同为「已经是最后一集了」+ 静态封面 + 控件行），基线在 ready 之后仍在以 ~60fps 持续重绘（合并数据里 ready 后 2 秒还有 115 帧），全量包 ready 后只多 1 帧 —— 即**基线在静态画面上白刷**，全量包已经静止。以「窗口内帧数」为好坏指标会把「少画无用帧」误判成退化，此指标本轮同样**不作为门禁**。
- **可用的判据只剩下帧间隔**：`maxStarve`（同一窗口内相邻帧完成的最大间隔）856 → 483ms（-43.6%），starve>100 的帧数 5.0 → 5.3（持平）。也就是「最长的单次冻结」明显缩短，但「100ms 以上的冻结仍有 4-7 次」。

### 9.3 率采样口径（累计帧数，免疫缓冲截断）

| 口径 | 窗口 ms 均值 | stalls≥300ms 均值 | maxStall 均值 | sumStall 均值 |
|---|---|---|---|---|
| 基线 c111-rate-base-r1..r3 | 3581.7 | 2.0 | 839.7 | 1461 |
| 全量包 c111-full-r1..r3 | 2695.7 | 1.0 | 740.0 | 740 |

窗口 -24.7%、≥300ms 停帧 2→1、停帧总时长 1461→740ms（-49%）。**但这是 n=3 且设备存在「快/慢两态」**：
基线三跑窗口 2593/4277/3875（含两次 4 秒级慢跑），全量包 2689/2628/2770（三次都落在 2.6-2.8 秒窄带）；**最快那一跑基线 2593ms 与全量包 2628ms 基本持平**。
⇒ 能说的是「慢态尾巴被削掉了」，不能说的是「快了 25%」。

### 9.4 尚未消除的用户可见现象（如实记录，不作为已完成）

用户原话是「加载中的转圈效果时不时会停一下」。本轮之后，每次进页窗口内**仍然存在 ≥300ms 的连续无帧**：率采样口径改后三跑各 1 次、最长 455/877/888ms（基线为 1/3/2 次、最长 659/716/1144ms）；帧级口径 starve>100ms 的帧仍各 4-7 个。
即 §6.8 判定的「解码器就绪前的同步链」被 F1/F2/F4/F5/F7 削掉了一部分（窗口尾部、徽标正则首编、onPrepare 的 binder 权限查询、主线程查 Config 表），但**停帧本身没有被消除**。

### 9.5 本次改动可宣称的效果边界

- 可宣称：进页窗口的**最长单次无帧间隔**从 800-933ms 降到 433-550ms（-43.6%）；慢态窗口 4.3 秒级尾巴消失；徽标正则首编、设备身份探测、onPrepare 的 `Settings.canDrawOverlays` binder 调用、主线程 `Config.findById` 已移出进页窗口（各自有栈证据 + 代码等价性论证）。
- 不可宣称：转圈已无停顿；trav 指标改善；帧率提升。
- 不可外推：本机 arm32 经 libnb 翻译跑 x86_64，GL/EGL 全走翻译层（§8.3）。

### 9.6 验收判定（对照访谈定下的三档判据）

| 判据 | 要求 | 实测 | 判定 |
|---|---|---|---|
| 一 | 窗口内 `trav>40ms` 帧数与 p99 相对基线降 ≥30% | 基线(同视频)三跑 trav>40 全为 0、p99 24.5-36.4ms；改后 0/2/0、p99 25.7-45.1ms | **无法判定**：该判据在本场景没有合法同口径基线（§9.1），且改后并未更优 |
| 二 | 转圈无可见停顿 | 每次进页仍有 1 次 ≥300ms 连续无帧，最长 455/877/888ms；starve>100ms 帧 4-7 个（§9.4） | **未达成** |
| 三 | 起播不变慢、首帧不黑 | 窗口 5991→6058ms(+1.1%)；rate 口径 3582→2696ms(-24.7%)；无新增黑屏 | **达成** |

结论：三档判据**只达成第三档**。F1/F3+F2+F4/F5/F7 五项改动有明确的机制证据（每项都有 debuggerd 栈或行号级等价性论证），
也确实削掉了窗口尾部与四类一次性主线程成本，最长单次无帧从 800-933ms 降到 433-550ms，
但**用户报告的「转圈时不时停一下」没有被消除**，且原定主判据不可用。
按访谈约定「起播变慢或首帧黑屏即回滚该项」——该条未触发，无项需回滚。

### 9.7 下一轮若继续，证据指向的剩余落点（本轮未做）

§6.8 的 10 点采样已把 400-2400ms 的同步链拆成可归因的几段，其中三项被本轮处理，剩余未处理的：
1. `ActivityVideoBinding.inflate`（tap+400/600ms 两点命中，实测 inflate cost=274ms）：需拆 ViewStub 或用异步 inflate，属设计改动，须单独立项。
2. `ViewRootImpl.relayoutWindow`（tap+1200ms，binder 等 WMS）与窗口首帧前的系统侧成本：app 侧无可控面。
3. `ThreadedRenderer.setStopped`（tap+800ms，等 RenderThread `std::future::get`）+ 该帧 550ms 冻结：与 libnb 翻译层和 EGL 表面设置失败同批出现（§8.3），app 侧不可消除。
4. `ExoUtil.buildPlayer` / `PlaybackService.onCreate` 同步构建（tap+1400ms）：F6/F6b 已否决其消除方式（§8.1/§8.2），若要再动须先解决「播放线程所有权」与「刷新率/缓存初始化时机」两个硬约束。


## 10. 第四轮密集采样（1600-2200ms）：冻结段落在 PlaybackService.onCreate

### 10.1 采样条件
- 设备与包：emulator-5554，已安装全量包 md5 `03EA8A391DCA2FE83248AAF79B38B666`（F1/F3+F2+F4+F5+F7），动画三开关=1，debug_log=ON。
- 方法：复用 `F:/Workspace/webtv2/whdiag/perf/c111-dense/round.sh`，**每轮进页只采 1 个主线程栈**，取样时刻由设备时钟计算（tap 注入本身耗时 158-237ms）。5 个名义偏移 1600/1750/1900/2050/2200 各 1 次。
- 产物：`F:/Workspace/webtv2/whdiag/perf/c111-dense3/off{1600,1750,1900,2050,2200}/`（bt_*.txt 3549-3921 行、logcat.txt、markers.txt、meta.txt）。解析脚本 `F:/Workspace/webtv2/whdiag/perf/parse_dense3.py`。

### 10.2 命中结果

| 名义偏移 | pid | 主线程最内层（父级亲解析） | 归因 |
|---|---|---|---|
| 1600 | 25878 | `DebugLogStore$1.wallMillis ← DiagnosticLogBuffer.offer+101 ← addCleaned+414 ← DebugLogStore.addCleaned+26 ← SpiderDebug.log+46 ← PlaybackActivity.logSurfaceState+542 ← applyResizeModeNow+384 ← applyResizeMode+12 ← VideoActivity.onSurfaceAttached+8 ← PlaybackActivity.attachSurface+674` | 表面挂载 + 同步日志（debug_log 专属） |
| 1750 | 26386 | `DiagnosticEvent.category+8 ← DebugLogStore.event+20 ← DiagnosticControls.forTrace+122 ← PlayerManager.logPlaybackRoute+100 ← setMediaItemNow+108 ← setMediaItem+98 ← start+120 ← PlaybackActivity.startPlayer+292 ← VideoActivity.setPlayer+820` | 起播路径的同步诊断日志 |
| 1900 | 26932 | binder 等待：`__ioctl ← IPCThreadState::talkWithDriver+375 ← waitForResponse ← transact ← BpBinder::transact ← android_os_BinderProxy_transact` | 主线程 binder 同步调用 |
| 2050 | 27442 | Looper 空闲（`__epoll_pwait ← Looper::pollInner ← pollOnce`），栈内仅残留 JIT 行 | 空闲 |
| 2200 | 27967 | Looper 空闲（同上） | 空闲 |

⇒ 1600-2200ms 这 600ms 里主线程**没有**在做重计算；命中的两处是「起播路径上的同步诊断日志」与一次 binder 同步等待。

### 10.3 真正的耗时分布（off1600/logcat.txt 亲读，同一次进页）
- `19.241 service onCreate start`
- `19.289` 系统侧日志（非本进程主线程）
- `19.311/19.312 AudioTrack$Builder.setOffloadedPlayback` 反射告警 ×2 → `19.329/19.334 VideoCapabilities: Unrecognized profile 4 for video/hevc / Unsupported profile 4 for video/mp4v-es` → `19.338 TV-exo-capability` → `19.345 TV-exo-enhance` → `19.370 TV-exo-tunnel` → `19.380 TV-exo-frame` → `19.390 TV-exo-cache: created capacityBytes=536870912 ...` → `19.398 TV-exo-buffer`
- **`19.416 I ExoPlayerImpl: Init [AndroidXMedia3/1.11.0-alpha01]`**（距 onCreate start **175ms**）
- `19.422-19.498` `V libnb: enter native_bridge3_loadLibraryExt .../lib/arm/libexo_dovi_render` → `native_bridge3_loadLibraryExt: 0xd22ce580` → 7 条 `enter native_bridge2_getTrampoline Java_com_fongmi_android_tv_player_exo_ExoDv5Native_nativeProbeCapabilities` → `D vulkan: searching for layers`（约 **76ms**）
- `19.522 TV-exo-audio: configured passthrough=true` → `19.591 EventLogger: playWhenReady` → **`19.599 service player ready cost=360ms`** → `19.610 MediaSessionImpl: Init` → `19.677 MediaController: Init` → **`19.691 service onCreate end cost=451ms`** → `19.751 service ready`

⇒ **PlaybackService.onCreate 主线程耗时 451ms**：`new PlayerManager(this)` ≈175ms（能力探测 + SimpleCache 建库 + AudioTrack 反射）→ native `libexo_dovi_render` 装载 + 探测 ≈76ms → `MediaLibrarySession.Builder(...).build()` ≈57ms → 其余 ≈143ms。

这一段精确解释了 §6.8/§9.4 记录的 438-466ms 连续无帧：**冻结不在解码器、不在布局，而在服务 onCreate 的同步构建**。

### 10.4 线程归属初判
- `PlayerManager` 的 controller 初始化、`mediaSession.begin(0L)`、`buildEngine` 都只是对象装配，本身无 Looper 约束；但 `buildEngine` → `ExoUtil.buildPlayer` 里的能力探测与缓存创建有 **Display 状态依赖**与**文件 I/O**，两者都必须在播放意图确定后才能算（§8.2 已论证不能挪到 App 启动）。
- native 库装载与 `MediaLibrarySession` 构建的线程约束由 §11 的只读调查回答。

## 11. 第五处热点：PlaybackPerformanceSetting 的全量 SharedPreferences 拷贝（F8）

### 11.1 证据
- 密集栈（F:/Workspace/webtv2/whdiag/perf/c111-dense2/off3200）命中：
  `HashMap.<init> ← SharedPreferencesImpl.getAll+113 ← PlaybackPerformanceSetting.rawProfileValue+77 ← rawProfile+26 ← getProfile+8 ← isAuto ← ExoPerformanceSetting.getEffectiveStartBufferMs+6 ← PlaybackAnalyticsListener.observeAutoThresholds+50 ← PlaybackAnalyticsListener.onEvents+182 ← Handler.dispatchMessage ← Looper.loop`
- 设备实测偏好文件 `/data/data/com.silent.android.webhtv/shared_prefs/com.silent.android.webhtv_preferences.xml` = **114324 B / 135 个键**；`SharedPreferencesImpl.getAll():299-305` 是 `return new HashMap<String, Object>(mMap);` ⇒ 每次调用做整表深拷贝。
- 放大链：`rawProfile()` 一次调用内含两次 `rawProfileValue`（:952-957 与 :959-961）；`observeAutoThresholds` 每条播放事件取 start/rebuffer 两个阈值，各自又走 `isAuto`；一次进场窗口约 30 条 playback-metrics ⇒ 150-240 次整表拷贝。
- 为什么不加缓存：profile 写点分布在 PlaybackPerformanceSetting / ExoPerformanceSetting / IjkPerformanceSetting / MpvPerformanceSetting 共 20+ 处，且 `bean/Backup.java:301` 白名单允许整批导入，失效面过宽，缓存会引入陈旧值风险。

### 11.2 改动（app/src/main/java/com/fongmi/android/tv/setting/PlaybackPerformanceSetting.java:962-983）
`Object value = Prefers.getPrefers().getAll().get(key);` → `prefs.contains(key)` + `getInt` / `getLong` / `getFloat` 依次尝试，全部 CCE 或键缺失时回落 `clampProfile(fallback)`，外层 `catch (Throwable)` 保留。

- 事实纠正：父级最初建议的 `prefs.get(key, null)` 在 AOSP 上不存在——`android/content/SharedPreferences.java:284-397` 只有 `getAll()` 与 6 个类型化 getter，`android/app/SharedPreferencesImpl.java:299-366` 同样没有 `get(String,String)`。
- 等价性依据（父级亲读 AOSP 源码核对）：6 个类型化 getter 都是 `(T) mMap.get(key)` 后 `return v != null ? v : defValue`（:312/:331/:339/:347/:355），取值对象与 `getAll().get(key)` 完全相同，只是不做拷贝；`contains()` = `mMap.containsKey(key)`（:364）；`getInt` 对非 Integer 直接抛 CCE（:331 的 `(Integer)mMap.get(key)`），可被逐级 catch 拦下。
- 四情形等价：①存在-int → 同值；②存在-非 Number → 三个 getter 全 CCE → fallback（旧路径 instanceof 为 false → fallback）；③不存在 → fallback；④prefs 抛异常 → 外层 catch → fallback（旧路径同样在 awaitLoadedLocked 抛）。Long/Float 分别走 `(int) getLong` / `(int) getFloat`，与 `((Number)v).intValue()` 逐位相同。
- 残余差异（不可达）：Byte/Short/BigInteger 等非 Integer/Long/Float 的 Number 会从「intValue() 取值」变为「回落 fallback」；SharedPreferences 的写入 API 与 XML 反序列化只产出 int/long/float/string/boolean/StringSet 五种类型，任何写入路径都无法产生这种值。
- 未新增缓存、未改 `catvod/.../Prefers.java`、未改 `ExoPerformanceSetting.java`；`getAll()` 现仅剩 :920 的批量迁移路径（非热路径）。

### 11.3 验证
- `./gradlew :app:compileLeanbackArmeabi_v7aDebugJavaWithJavac --offline` → exit 0，BUILD SUCCESSFUL in 24s（47 tasks: 2 executed / 45 up-to-date）。
- 设备端行为验证随整包复测（§12）一并进行；本条不与 F1/F3+F2+F4+F5+F7 拆开单独验收。

## 12. 第五轮密集采样（F10）：startPlayer 窗口的方法级归因，及一条被既有 A/B 否掉的推论

### 12.1 采样条件
- 包：全量包 03EA8A39（不含 F8）。偏移 1600/1750/1900/2050/2200ms，每次进页只采 1 个栈；产物 F:/Workspace/webtv2/whdiag/perf/c111-dense3/F10-report.md 与 off*/。
- 5 轮均 mResumedActivity=VideoActivity，无 houdini 退化栈。debuggerd attach 自身耗时 739-965ms，栈落点晚于名义偏移，须按帧内容对齐。

### 12.2 命中结果
| 偏移 | 最内层 app 帧 | 相位 |
|---|---|---|
| 1600 | `DebugLogStore$1.wallMillis` ← DiagnosticLogBuffer.offer ← addCleaned ← SpiderDebug.log ← PlaybackActivity.logSurfaceState ← applyResizeModeNow ← VideoActivity.onSurfaceAttached ← attachSurface ← startPlayer | startPlayer 窗口内 |
| 1750 | `DiagnosticEvent.category` ← DebugLogStore.event ← DiagnosticControls.forTrace ← PlayerManager.logPlaybackRoute ← setMediaItemNow ← start | startPlayer 窗口内 |
| 1900 | 无 app 帧，栈挂在 `IPCThreadState::waitForResponse` ← BinderProxy.transact ← INotificationManager$Stub$Proxy.getNotificationChannel ← NotificationManager.getNotificationChannel ← media3 `DefaultMediaNotificationProvider.createNotification` | 已过 return，media3 建前台通知 |
| 2050 / 2200 | 无 app 帧，`__epoll_pwait → Looper::pollInner` 纯空闲 | startPlayer 已结束 |

时间线（相对本次 tap，ms）：startPlayer dispatch→return 分别为 488 / 439 / 218 / 179 / 186ms。两轮慢跑（488、439ms）与你的 §10.4 冻结区间 tap+1704..2142、1978..2444 完全重合，且栈都停在日志方法内部 —— 这是同一条证据链从「时长」到「方法」的闭环。

### 12.3 关键量化（并给出它**不能**推出的结论）
- 窗口内主线程诊断日志 **114-116 行，与窗口长短无关**（488ms 的 1600 轮与 179ms 的 2050 轮都是 115 行）⇒ 代码路径没变，变的是每条日志的单位成本。
- 窗口内 `┌…└` 多行块内部跨度合计仅 20/13/8/6/5ms；报告据此说「96-97% 时间落在日志与日志之间的空隙」。
- **但这句推论不能成立**：日志之间的空隙是「两个 log 调用之间的业务代码 + 系统调用 + binder」，不是「日志格式化的隐藏成本」。§12.1 的两枪只是恰好采样落在 log 方法内，不能反推空隙里也在格式化日志。
- **既有 A/B 直接反证**（§6.7，同探针、同设备、各 3 跑）：debug_log=OFF 时每跑 logcat 只余 13-16 行诊断日志（对比 ON 的 481 行），但停帧指标与 ON 完全重叠 —— ON 基线 maxStall 659/716/1144、OFF 基线 894/646/993；ON 改后 650/1112/674、OFF 改后 684/1430/895。⇒ **把这些日志整体关掉都降不下停帧，那么把它们搬到后台线程同样降不下来。**

### 12.4 决策：不实施「日志管线后台化」
- 依据上述反证，F10 报告建议的「把 `DiagnosticLogBuffer.offer` / `DiagnosticText.clean` / `DiagnosticEvent.json` 整体挪到后台线程」被否决：它要改 catvod 核心日志管线的线程模型（跨模块、影响崩溃取证与导出顺序），而可测收益已被同设备 A/B 证伪。
- F10 报告的另一半仍然有效且已记录：off1900 的 binder 等待（media3 首次 `getNotificationChannel`）是系统同步契约，app 侧只能减少触发次数，不能消除；这类成本与 §8.3 记录的 RenderThread/GL 侧成本同属「app 不可控面」。

### 12.5 本轮结论
到 §12 为止，进页窗口内已定性到方法级的落点全部落在三类：
1. app 可消除且已消除：F1/F3 建树分片、F2/F4/F7/F5/F8 的一次性成本预热与短路。
2. app 可消除但代价超边界：接管播放 Looper（F6 否决）、预热能力探测与缓存（F6b 否决）、日志管线改线程模型（§12.4 否决）。
3. app 不可控：`ViewRootImpl.relayoutWindow` 等 WMS binder、`ThreadedRenderer`/RenderThread 与 EGL 表面、media3 通知 binder、解码器初始化。

## 13. 交叉配对 A/B（基线包 vs 含 F8 全量包）——结论：本机测不出收益，且配对设计本身有顺序偏置

### 13.1 做法与产物
subagent da71b04c 在设备连续约 8 分钟窗口内（13:50-13:59，重启后）交替安装两个包各 3 次：
基线 `B54442A9DFBE8923DC6AE3A551FB35AE` 与含 F8 全量包 `5FFB429E0A4E619C4F9675F3B8618585`（均 171452796 B）。
每次安装后核对设备 `base.apk` md5，6/6 全部等值；`primaryCpuAbi` 恒为 `armeabi-v7a`。
产物 F:/Workspace/webtv2/whdiag/perf/c111-ab2/（report.md + 6 个 run 目录）；探针因宿主 bash 为被禁用的 WSL 版，改用逐行等价 Python 端口 `c111-ab2/leanback_enter_rate_probe.py`。
设备重启事故：10:58 与 13:49 之间的两次尝试已改名 `_preboot-*` 并排除。

### 13.2 配对数字（window = 首个 onTimeChanged canSave=true − tap）
| pair | 包 | window ms | frames | fps | stalls≥300 | maxStall | sumStall | tap→START |
|---|---|---|---|---|---|---|---|---|
| 1 | base | 2393 | 19 | 7.94 | 1 | 673 | 673 | 860 |
| 1 | full | 2455 | 18 | 7.33 | 1 | 658 | 658 | 895 |
| 2 | base | 2366 | 17 | 7.19 | 1 | 686 | 686 | 875 |
| 2 | full | 2646 | 18 | 6.80 | 1 | 874 | 874 | 927 |
| 3 | base | 4178 | 18 | 4.31 | 4 | 708 | 2364 | 1140 |
| 3 | full | 4265 | 21 | 4.92 | 3 | 971 | 2242 | 1210 |
成对差（full−base）均值：window **+143ms**、frames +1.00、fps −0.13、stalls −0.33、maxStall **+145ms**、sumStall +17ms、tap→START **+52ms**。

### 13.3 为什么这组「全量包更慢」不能当作回归
1. **配对顺序固定为 base→full**，而本机在同一会话内存在单调漂移：`tap→START` 三对基线值 860 → 875 → 1140，斜率 ≈ **+44ms/分钟**；每一对内部相隔约 52s，按斜率外推应出现 **+38ms**，实测成对差 **+35/+52/+70（均值 +52）**。⇒ 这条「full 更慢」的信号完全落在漂移噪声带内。
2. `tap→START` 是 **VideoActivity 启动之前**的区间（系统 monkey/am + 冷启动），本次六处改动在该区间内一行代码都不会执行；它若真是包效应，技术上不可能。
3. 第 3 对两个成员同时劣化到 4178/4265ms（前两对 2366-2646），是整轮环境态位移，不是包效应。
4. 设备 `/proc/loadavg` 全程 6.4-7.7（nproc=4），1 个 D 状态进程，属 I/O 等待型负载；同内容不同时段的采样已实测可差 70%（§12 前的 10:11 那批）。

### 13.4 可宣称与不可宣称
- **不可宣称**：本机同口径点对点比较显示六处改动对「窗口时长 / 停帧次数 / 最长停帧 / fps」没有可测收益。n=3、顺序偏置、loadavg 6-7 的模拟器无法分辨这个量级。
- **可以宣称**（单次 run 内的插桩事实，不依赖跨组统计）：`initView` 尾部三段各 1-9ms（原为同 tick 内连续执行）、首帧在 `initView end` 后 85ms 绘出、127 条徽标正则首编与设备身份探测已移出进页窗口、`rawProfileValue` 不再整表拷贝 114324 B 偏好、`onPrepare` 的 overlay binder 查询在进页态短路。
- **边界**：模拟器是 arm32 经 `libnb` 翻译跑在 x86_64 上（`primaryCpuAbi=armeabi-v7a`、`ro.dalvik.vm.native.bridge=libnb.so`），绝对耗时不可外推到真实 ARM 电视盒；真实盒子上 inflate 274ms 与 `PlaybackService.onCreate` 451ms 这类一次性同步成本会按 CPU 比例放大，而本机残余停帧已被 §8.3/§12.5 判定在 app 不可控面。
- 结论：**剩下的差异要靠更长的样本量和更干净的环境才能分辨；当前证据不足以宣称用户可感知改善，也不足以判定回归。**

## 14. beta 合并后的独立评审与增量修复（2026-10-29）

### 14.1 合并事实
- 合并 `origin/beta` `ba802dc432`（PR #500 from dev5）到 `dev1`，merge-base `abff821ba3`，带入 19 个提交、41 个文件、+2863/-105。
- 唯一冲突 `app/src/main/java/com/fongmi/android/tv/bean/Config.java`：HEAD 侧是 `PlaybackConfigIdentity.invalidateAll()`，beta 侧是新增的 `invalidateCatalog()`。解决方式为两个失效都保留，`invalidateAll()` 在前，落在 `insert()`(:455-457)、`save()`(:466-468)、`delete()`(:482-484)；`VodApiTest.java:610-613` 断言的「`invalidateCatalog();` 恰 3 处」仍成立。

### 14.2 评审覆盖说明（为什么不复用旧评审）
§7.1-§7.5 与 §11 的旧评审只覆盖 `23f51a7736` 的六处改动；本次合并带入 19 个 beta 提交，其中 beta 的 OSD 媒体徽标（`PlayerSetting.java:579-591`、`PlayerOsdController.java:309/469-470`）、`VodPlayerUiHost.isFullscreen()` 覆盖（`VideoActivity.java:1583-1586`）与六处改动落在同一批文件上，且 `Config.java` 发生了真实冲突 ⇒ 旧评审不构成对当前合并结果的覆盖，必须重新评审。评审分两轴独立进行：Spec/集成轴与 Standards 轴（只读 subagent，均未修改文件）。

### 14.3 第一轮结论
- 六处改动忠实度：F1/F3 分片顺序（`VideoActivity.java:1614-1625` + `:1611` 同步 `setViewModel()`）、F2（`App.java:168-170`）、F4（`App.java:178-183`）、F5（`DesktopLyricsWindow.java:128-129`）、F7（`PlaybackConfigIdentity.java:47-70`）、F8（`PlaybackPerformanceSetting.java:962-983`）均与文档设计一致。
- `Config.java` 冲突解决语义正确：三写点双失效齐备；beta 的网关 configKey 桥接（`VodSiteCatalog.java:165/175-176`）只读、不写配置行，既不漏失效也不新增陈旧源；`Backup.java` 直写 DAO 的既有残余经 `BaseConfig.java:119 config.update() → save()` 收敛，非合并引入。
- 必修 1 项、建议 2 项（见 §14.4）。

### 14.4 必修与建议（含证据）
1. **必修：适配器时序窗口（分片机制引入）**。`setViewModel()` 在 `:1611` 同步 `observeForever`（`:2206-2212`），而 `mFlagAdapter` 只在 stage 1 的 `setRecyclerView()` `:1845` 创建；`setDetail(Vod)` 在 `:2910` 无守卫解引用它。leanback `AndroidManifest.xml:156-160` 的 `configChanges` 不含 `fontScale/density/uiMode/locale`，这类配置变更会重建 Activity 而 ViewModel 保留 ⇒ `observeForever` 立即回放上次 Result，此时新实例的 `mService` 必为 null（`PlaybackActivity.java:1007-1014 releaseBinding`），结果落进 `mPendingDetail`，随后 `onServiceConnected()` 消费它时 stage 1 可能还没跑。C111 之前 `setRecyclerView()` 在 `setViewModel()` 之前，不会命中。
2. **必修（建议转必修）：`Backup` 绕过 Config 写路径**。`Backup.java:132/147/197/198` 直接写 Config 表且不调 `invalidateAll()`，恢复后 `KEY_CACHE/NAME_CACHE` 仍持有恢复前的 `cid→interfaceKey` 映射。
3. **建议：关闭媒体徽标仍预热 127 条正则**。总开关关闭时 `PlayerOsdController.java:469-470` 在解析前就返回，`MediaBadgeCatalog.patterns()` 永不触发，`App.java` 侧无条件预热是纯浪费。
4. **建议（未采纳）：`setViewModel()` 推迟到 stage 内**。会破坏 `:1596-1598` 记录的约束——`onServiceConnected()` 的沉浸音频路径经 `playerContent()/updateActionQuality()` 解引用 `mViewModel`。
5. **建议（未采纳）：把 `FlagAdapter` 提前到 `initView` 同步段**。破坏 C111 的帧节奏目标（`setRecyclerView()` 正是被拆出去的重尾之一）。

### 14.5 已实施的增量修复
1. `VideoActivity.onServiceConnected()` 把尾部（`consumeImmersiveAudioLaunch` / `checkId` / `flushPendingFastTmdbPlayback` / 消费 `mPendingDetail`、`mPendingPlayer`）抽成 `applyServiceReadyResults()`；`mInitViewStages` 非空时用 `deferInitViewStage("4-service-results", …)` 排到队尾，空则原地执行 ⇒ 正常路径完全不变。
2. `setDetail(Result)` 的等待闸从 `service() == null` 放宽为 `service() == null || mFlagAdapter == null`，`setPlayer(Result)` 同理加 `|| mQualityAdapter == null`。这两行独立覆盖观察者路径（`mObserveDetail = this::setDetail`，`:1591`），不依赖阶段队列。
3. 500ms 安全网改为 `armInitViewStageSafetyNet()`：队列非空才重排，队列排空即停；并新增 `mInitViewLastPumpAt`，只有帧节奏链真的停满 500ms 才接手，避免慢首帧时安全网变成第二个更慢的推进源。随之删除失效字段 `mInitViewStagesPumped`。
4. `App.warmMediaBadgePatterns()` 先判 `PlayerSetting.isDisplayMediaBadges()`。
5. `Backup.restore(boolean)`（`:133-136` 之后）与 `restore(SyncOptions, boolean)`（`restoreConfig()` 之后 `:155-156`）补 `PlaybackConfigIdentity.invalidateAll()`，覆盖 `Backup.java` 两条整表写入路径；`:197/198` 位于 `restoreConfig()` 内部，由后者统一覆盖；`isKeep()/isHistory()` 分支不写 Config 表，无需失效。

### 14.6 验证
- `gradlew :app:compileLeanbackArmeabi_v7aDebugJavaWithJavac` → BUILD SUCCESSFUL（47s）。
- `gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac` → BUILD SUCCESSFUL。
- `gradlew :app:testLeanbackArmeabi_v7aDebugUnitTest` → 4732 tests, 8 failed；`:app:testMobileArmeabi_v7aDebugUnitTest` → 5514 tests, 6 failed。
- 8 个失败全部是本机 CRLF 检出导致的源码扫描断言，与本次 diff 无交集（读取路径经核对不含改动文件的行为断言）：实测 `VideoActivity.java lone_lf=0 crlf=12762`、`CollectActivity.java lone_lf=0`、`assets/reader.html lone_lf=0`、`assets/css/ui.css lone_lf=0`，断言要求的裸 `\n` 序列在这些文件里根本不存在。同一组失败在 `docs/HOME-STATE-NO-CONFIG-20261018.md:131` 已记为既有环境失败。
- 第二轮只读评审（`a55494fc`）逐条核实 §14.5 的 4 项：Q1 窗口已消除且无重复/漏请求（`checkId` 有 `detailRequested` 幂等闸）；Q2 队列被 clear 时未消费 `mPending*` 可接受且优于修复前；Q3 指出安全网节奏回归并给出收窄方案（已按此实现 `mInitViewLastPumpAt`）；Q4 三处时机/成本/覆盖均成立（`Init.set` 在 `attachBaseContext`，`App.java:131 Setting.applyLanguage()` 已先付过 prefs 磁盘加载；`invalidateAll()` 仅 `synchronized` + 两次 `clear()`，两处 restore 都在后台线程）。
### 14.7 收窄后的复验（2026-10-29）
- 依第二轮评审 Q3 把安全网改为真正的"停摆"判据：新增 `INIT_VIEW_STAGE_STALL_TIMEOUT_MS = 1_000`（`VideoActivity.java:367`），`launchInitViewStages()` 在 `:2293` 以 `mInitViewLastPumpAt = System.currentTimeMillis()` 起算，`armInitViewStageSafetyNet()` 的 `:2332` 只在超过该停摆阈值时才接手。这样慢首帧（>500ms）不再让安全网越过首帧每 500ms 抽一个 stage，队列排空即自停。
- 复验命令：`gradlew :app:compileLeanbackArmeabi_v7aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac :app:testLeanbackArmeabi_v7aDebugUnitTest :app:testMobileArmeabi_v7aDebugUnitTest --offline`。两个编译任务均通过（`verify-c111-4.log:128`、`:148`）。
- 单测结果与 §14.6 完全一致：leanback 4732 tests / 8 failed，mobile 5514 tests / 6 failed，失败集合未因本次收窄改变，仍全部是 §14.6 记录的 CRLF 检出环境失败。
- 首次全量复验曾因 Gradle 并发 daemon 争用 `app/build/tmp/compileLeanbackArmeabi_v7aDebugJavaWithJavac/compileTransaction/stash-dir` 而报 `Unable to delete directory`；`gradlew --stop` 后删除该 tmp 目录即恢复，与被测代码无关。

### 14.8 第三轮只读评审与迟到 stage 的安全网重排（2026-10-29）
- 第三轮只读评审（针对 `VideoActivity` 阶段调度器）逐条核对了 7 条推进路径，确认 detach 只是暂停（`postOnAnimation` 回调被丢弃，但下一次泵在 `:2363-2366` 走 `App.post` 自续；安全网是 `root.postDelayed`，detached 期间的 post 记入 `ViewRootImpl` run-queue，reattach 后执行），finishing 是有意 `clear()` 放弃，`stage.run()` 抛异常会经 `CrashReport` 交回前一个 handler 使进程崩溃而非静默停摆。
- 唯一残留窄口：`pumpInitViewStage()` 在 `:2350-2355` 因 finishing/destroyed 执行 `clear()` 后，安全网下一次 tick 见空队列在 `:2333` 直接 return 且不再自我续期；若此时 `onServiceConnected()` 尾段又 `deferInitViewStage("4-service-results", …)`，`mInitViewStagesLaunched` 已为 true（`:2296`）、`launchInitViewStages()` 在 `:2295` 早退，则这条 stage 没有调度者。
- 修复：`deferInitViewStage()` 在 `addLast` 之后加 `if (mInitViewStagesLaunched) armInitViewStageSafetyNet();`（`:2281`），迟到 stage 自带兜底调度器；`armInitViewStageSafetyNet()` 本身在队列排空时于 `:2333` 自停，因此不会形成常驻定时器。正常路径（stage 1-3 在 launch 之前入队）不触发该分支。
- 该评审同时确认 6 个同由 `setRecyclerView()` 创建的适配器字段（`mPartAdapter`/`mArrayAdapter`/`mEpisodeAdapter`/`mEpisodeGridAdapter`/`mQuickAdapter`/`mParseAdapter`）与 `mFlagAdapter`/`mQualityAdapter` 同生同存（全文件各只有一个创建点、无置 null 处），故 `setDetail(Result)` 与 `setPlayer(Result)` 的 `mFlagAdapter == null` / `mQualityAdapter == null` 闸通过时其余必然非空，闸本身无漏；绕过闸直调 `setDetail(Vod)` 的三处（`applyCachedTmdbDetailNextFrame`、`applyFastTmdbPlaybackFullDetailNextFrame`、`consumeImmersiveAudioLaunch`）均经 `setDetail(Result)` 入口或在服务就绪后调用，已核实安全。
- 复验（第三处修复后，全量一次跑完）：`gradlew :app:compileLeanbackArmeabi_v7aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac :app:testLeanbackArmeabi_v7aDebugUnitTest :app:testMobileArmeabi_v7aDebugUnitTest --offline`。两个编译任务均无 error；leanback `4732 tests completed, 8 failed, 3 skipped`，mobile `5514 tests completed, 6 failed, 3 skipped`，失败集合与 §14.6/§14.7 逐条相同，全部为 CRLF 检出环境失败。