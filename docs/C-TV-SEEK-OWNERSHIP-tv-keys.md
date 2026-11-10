# C-TV-SEEK-OWNERSHIP — TV 方向键延迟 seek 所有权

## 决策与授权记录

- 2026-11-08（Asia/Shanghai，研究开始 14:35:57，前置研究目标结束 14:43:57）：**推荐窄适配；已批准的局部合同内可进入实施，但必须先取得下面的真实调用 Red，不能把研究当作修复完成。** 本轮只研究，生产编辑、构建、提交、标签均未执行。
- 原 root `F:\Workspace\fish2018\webhtv` HEAD `47454687f504955775b398905ba09d73aeb9f20c`；最终集成 tree `.codex/task-state/worktrees/tv-small-fixes` 已核实相同 HEAD，只读且父正在 baseline，禁止写入。
- 保护原 `app/.cxx/`、未提交 `docs/upstream-player-dependency-merge-assessment-2026-11-08.md` 及其他已有 dirty 文件。本轮唯一写入本文件和另一项已指定设计文件；未来复制本文件为实施 tree 的唯一 `docs/C-TV-SEEK-OWNERSHIP-tv-keys.md`，不另开 plan/评估副本。
- 与 C-TV-ASYNC-RESULT-OWNERSHIP 独立，二者都碰 leanback `VideoActivity`，未来串行，各自 guard/验证/原子提交/恢复标签，不合并任务。

## 冻结来源、证据与分级

访问日期均为 **2026-11-08**。A=精确代码/平台规范/可复现测试，B=维护者解释/成熟项目实现；来源描述不是本地测试通过声明。

| 来源/完整身份 | 级别 | 支持的事实、消费者与影响 | 限制 |
|---|---|---|---|
| `https://github.com/FongMi/TV/commit/7bf115759881e27df819bb01a357f4d7094d295c`；parent `0a4e0f93641d82b205ab5a5dd40a18e249a666a9`，cache `.git/upstream-tv-assessment.git` | A | 新 SeekInput 的稳定 Runnable、keyCode/deviceId/downTime；VodInput/LiveInput，TV Cast/Live/Video 在替换、预载、全屏离开、销毁取消；输入是实际三个 Activity 的生产消费者 | 不直接 cherry-pick：本地仍是 CustomKeyDown*，没有上游 BaseInput/controller 体系 |
| `https://github.com/FongMi/TV/commit/51813cb611386ab3fc3fe388480cfb12d2670f67`；parent `9febe402bb951c92856e26b95deae36c34248071` | A/B | SeekInput:非方向 DOWN 取消、零偏移不 seek；三个 Activity 控制接管/暂停取消。维护者提交正文称 84 focused Leanback tests 和 Java compilation | 该提交没带这些测试；84 是上游自述，非本地复现 |
| 上游最终 HEAD `4512b4c9f82534ff66acfde06ccdd68f35b074f0` 的 `app/src/leanback/java/com/fongmi/android/tv/ui/custom/SeekInput.java`；与 `51813cb611386ab3fc3fe388480cfb12d2670f67` 的该文件 diff 为空 | A | 最终仍是 250ms、固定 Runnable、键所有权、canSeek 二次检查、零偏移取消 | 它本身没有 WebHTV 的 immutable playback trace/session 校验，必须补足 |
| 精确 patch `.git/tv-review-core.patch`:59-762，SHA256 `53a7fd7995ae5acdb393d09c36e8724fcb58635f45d4070a7bbda14716d2b9f1` | A | 已核对这两个 seek commit 的活动取消 hunk 与输入字段/行为；只采用 seek 两条，不带其他 patch | patch 并非本地可直接 apply 的承诺 |
| `https://github.com/FongMi/TV/pull/772`，贡献者源 `ba2d0b8dc11a7b658ee5c5e7c490acf573c4d8bb`；FongMi 在页面引用并关闭 PR | B | 解释每个匿名 Runnable 无法合并，第二次按住前应移除上一提交；保留普通直播即时切线路；贡献者 28 场景是模拟 JVM fixture | 非设备/Android 端到端，贡献者的零偏移完成回调已被第二条上游提交修正为取消；不以最初 PR 覆盖批准合同 |
| `https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/core/java/android/view/KeyEvent.java`:47-52,86-91,2621,3124-3129,3215,3233-3245（官方 AOSP tag） | A | repeat 是同一 DOWN 序列，取消 UP 带 FLAG_CANCELED，异常丢 UP 必须容忍；deviceId 与 downTime（uptime）用于隔离所有者 | 多键 downTime 文档有“最近键按下时间”语义，不能把它单独当全局唯一 ID；组合 key/device/downTime 与会话 |
| `https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/core/java/android/os/Handler.java`:442-456,621-635,849-855 | A | 时间基于 uptime，removeCallbacks 只移除指定 Runnable 的 pending 消息；禁止 removeCallbacksAndMessages(null) 清全局队列 | 不撤销已经运行的回调，执行前 owner 校验仍必要；不宣称 O(1) 或性能实测 |
| `https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/core/java/android/app/Activity.java`:285-292,434-441 | A | onStop 为不可见，状态仍保留并可 restart；不等于释放播放服务 | 输入取消不等于停止后台音频/PiP |
| `https://robolectric.org/blog/2019/06/04/paused-looper/`，2019-06-04，维护者 Brett Chabot/Jonathan Gerrish | A/B | PAUSED 不 inline 执行主 Looper post，显式 idle/idleFor；维护者解释 legacy Scheduler 与 Looper 两份队列可能不同步 | 应使用现有 4.16，不升级至文章最初 4.3 |
| `https://raw.githubusercontent.com/robolectric/robolectric/master/robolectric/src/test/java/org/robolectric/shadows/ShadowPausedLooperTest.java`:44-46,103-121（访问时 master）与本地 `app/src/testLeanback/.../FlagSelectionListenerTest.java`:43-85、`player/exo/ExoStartupRecoveryIntegrationTest.java`:67-71 | A | 成熟 PAUSED 测试控制真实 Handler；本地已有真实组件+Looper 用法 | 外部 master 未冻结 commit，作测试方式佐证；本地 fullhash 基线为决定性锚点 |
| `https://raw.githubusercontent.com/androidx/media/1.8.0/libraries/ui/src/main/java/androidx/media3/ui/PlayerView.java`:650-665 | B | 成熟播放器 UI 在主线程 setPlayer，先把旧 Player listener 与 surface 脱钩；解释 owner 生命周期边界 | 不能推导它已解决任何已排队回调；只是局部 ownership 的旁证，非 WebHTV 替代代码 |

官方 `developer.android.com/reference/android/view/KeyEvent`、`.../os/Handler`、`.../guide/components/activities/activity-lifecycle` 读取因网络引擎失败；改读对应官方 AOSP 精确 tag 源与 Javadoc，A级平台语义已覆盖，不需要再泛搜。Robolectric 错误的初始 tag 路径 404 后换到真实测试路径，不再重试同假设。

证据类覆盖：精确 upstream/parent/final tree、实际 patch、本地调用链、官方平台、PR/维护者、成熟播放器代码与测试均已列明。论文不适用：无新并发算法、调度器、协议或解码设计；性能 benchmark 不适用：不主张吞吐/起播/功耗收益，仅减少多余 seek 的正确性设计。不得用减少回调推断性能提升。未取得实机 field report，直接以确定性复现和设备门槛代替，不把 PR 的模拟 fixture 当实测。

## 本地实际调用链与已有保护（HEAD 同上）

1. `app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomKeyDownVod.java`:44-68：真正 `onKeyDown(KeyEvent)` 包含 DOWN/UP；DOWN 更新 `holdTime`，UP:55 新建匿名 `App.post(() -> listener.onSeekEnd(holdTime),250)`，读的是执行时 mutable holdTime。reset:90-92 只清偏移，不移除队列。
2. `app/src/main/java/com/fongmi/android/tv/App.java`:45-49,68-82：真实 Application handler 是主 Looper async Handler；post(delay) 先 remove **同一个** Runnable，匿名实例不能合并。这是源码可达重复/过期路径，不是“已实测复现”。
3. TV Cast `CastActivity`:454-475：控制隐藏时派发 CustomKeyDownVod；onSeekEnd 只检查 player 非空、reset，再调用实际 seek。Video `VideoActivity`:6239-6240 也接实际 seek。
4. Live `CustomKeyDownLive`:47-69：通过 Listener.dispatch(true) 路由输入，左右 DOWN 预览、UP 调 onKeyLeft/onKeyRight。`LiveActivity`:1049-1052,1096-1104,1119-1127：**player.isLive() 为真时立即 prevLine/nextLine；否则 Activity 匿名 post 250ms seek**。因此只有改 Vod 输入不覆盖 Live；不能在一般 Live 左右键统一提交 seek。数字输入:73-78 的 2000ms 查台 Runnable 与本任务无关。
5. `PlaybackActivity`:321-327：实际 `MediaController.seekTo`，且不 seek 到结尾时还调用 play；故零偏移/取消后不得调用该入口，避免暂停被恢复。
6. 现有 owner `PlaybackActivity.isOwner`:162-165 仅 key 相等；同片 key 下换集、同 URL 重播不够。可复用 `PlayerManager.getPlaybackTraceId`:480-481 → `PlaybackTrace.current`:63-64；trace `begin`:42-52 使用累计 SEQUENCE，清空:67-73，初始 NONE。`PlayerManager.beginPlaybackTrace`:6246-6255 在 start:5202、parse:5229、各 switch:1357/1410/1422/1447/1492/1519/1560 更新；不在普通 seek 更新。这是可用当前媒体 session，不新增全局 generation API。
7. 当前 Video onStop:6327-6333 仅 UI/clock；onDestroy:6366-6393 清其他 Runnable，不清输入；父 PlaybackActivity:904-912 保留后台策略。必须局部清输入但不更改 service 后台/PiP 语义。

## 已批准合同与设计

**实施 disposition：补充/修正上游的所有权模型，不复制其 Activity/controller。**

- 固定 250ms；既有 Constant.INTERVAL_SEEK、长按累计、上下键加速、预览/缓冲逻辑保持。
- 每个实际 CustomKeyDown 实例一个稳定 `seekCommit` Runnable，只有 release 后排队；新合法 DOWN 撤销旧 timer、保留该 burst 偏移；commit 一次消费偏移。输入状态含 keyCode/deviceId/downTime + immutable playback session（captured PlayerManager identity + trace string）。初始 repeat>0、非 owner repeat/UP 无效；owner canceled UP 取消并清键状态；cancel 后同组 late repeat/UP 不复活。
- Listener 仅追加局部 seek 能力/当前 session 查询与 cancel-preview 通知（具体名字由实施定）；优先当前 getter，不新增 PlayerManager/PlaybackActivity API。三个 Activity 提供 `service!=null、未 exiting/finishing/destroyed、当前 owner、当前媒体可 seek` 的谓词；session 比较不能只 key/URL。DOWN 捕获，commit 校验 captured manager/trace 与当前；NONE 不作为有效 session。
- `cancelSeek` 幂等，仅 remove 该 Runnable、清键/累计/预览，不调用 seek/play。左右抵消为零也取消预览，不恢复播放。
- **Activity 必须在新请求发出前取消**（不能等 trace 在 start 才变）：换集/换源/quality/parse选择/预载接管、Cast 新媒体、Live fetch/getPlayer/换台或线路、播放替换/播放器切换、控制显示或控件接管、pause/play/menu/back、离全屏、onStop/onDestroy。trace 校验是二道防线，不替代这些取消点。dispatch 拒绝输入时也先 cancel，尤其 Live 控制/列表已显示的路径。
- 普通 Live 保留左右 UP 即时切线路、上下换台、数字查台；只可 seek Live 的左右路径走合并。`isLive()` 本地分支必须保留，能力检查不允许把普通直播改成 seek。canSeek 可基于当前 Player 的命令能力/既有本地谓词验证，不引用本地不存在的 upstream `player().canSeek()`。
- 不改触摸手势、后台音频、PiP、Web 播放、播放器引擎、Native、缓冲阈值。

### 替代方案

| 方案 | 正确性/兼容/维护 | 决策 |
|---|---|---|
| no change | 匿名多任务和 mutable holdTime 路径继续存在；可回滚但不能满足旧会话不 seek 新片 | 不选 |
| unmodified upstream（两条一起） | key ownership 与控制取消成熟；但 BaseInput/VodInput/controller 迁移过大，本地 Live 和后台/自定义 UI 会被覆盖，输入无 trace 校验 | 拒绝盲拷/cherry-pick |
| narrow adaptation | 现有五个生产文件内补稳定任务/key/session、实际取消点；保留本地消费者与语义；少量相同 input 逻辑先允许局部重复，不为此造框架 | **选择** |
| 只加 Activity remove/只加 helper 测试 | 匿名 Runnable 不可可靠移除；遗漏 late repeat、未知 UP 或真实 Live post；helper 通过不代表入口正确 | 拒绝 |

纯 Java/App 局部状态：ABI/JNI/so/锁/播放器版本/依赖/Gradle 均 **no upgrade/no change**；新增比较/少量字段成本有界，但包体/性能没有测量，不宣称零开销。安全不增加网络、权限或 URL 处理。来源追溯留本文件，不引入新 license 内容/外部代码执行。

## 精确未来允许路径（当前不实施）

生产只允许：

- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomKeyDownVod.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/custom/CustomKeyDownLive.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/CastActivity.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java`

测试允许（新增任务专用）：

- `app/src/testLeanback/java/com/fongmi/android/tv/ui/custom/CustomKeyDownSeekOwnershipTest.java`
- `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvSeekOwnershipIntegrationTest.java`
- 唯一任务记录 `docs/C-TV-SEEK-OWNERSHIP-tv-keys.md`。

不允许改 PlayerManager、PlaybackActivity、App、mobile、controller、Live 网络/弹幕、build.gradle、native/locks/third_party、原 `.cxx`；如真实 fixture 证明缺一个必需接口，不先造 hooks，记录精确缺口交父决定。

## 可执行 TDD 路线与 acceptance

测试基础已在 app/build.gradle:63-79,87-89,204-208 核实：leanback/mobile + arm64_v8a/armeabi_v7a flavors，includeAndroidResources，testLeanback Robolectric **4.16**、JUnit；不需依赖升级。

**确认 seam：真实 `CustomKeyDownVod.create(TestActivity)`/`CustomKeyDownLive.create(TestActivity)` → `onKeyDown(new KeyEvent(...))` → 真实 `App.post` → PAUSED 主 Looper → Listener 的 seek 次数/偏移/所属 session；再通过真实三个 Activity 路由和 PlaybackActivity seek 到 consumer 的集成测试。** TestActivity implements 原 Listener，recording listener 只观察出站结果，不能把输入或 App/Handler mock 掉。测试专用 `TestApp extends App` 可覆盖 onCreate 避免无关初始化；App 构造器:45-49 自动设置 instance 与真实 Handler，无需生产 test hook。

按垂直切片：一个真实失败测试 → 最小生产实现 → 同测试 Green，不先写一堆 helper 测试。

1. **首个 Red**：Vod 实例，RIGHT DOWN/UP（device=1，downTime=10）；advance 100ms，再 RIGHT DOWN/UP（downTime=110）；advance 149ms 应零 seek，advance 1ms 仍零（第二 UP 后才 150ms）；再 advance 100ms 应仅一次 `+2*INTERVAL_SEEK`。旧版第一匿名任务将在第一 UP+250ms 触发，故真实 Red；不要错误断言两次 UP+250 都已经过才测试零。另切片“第二次按住未 UP”，跨第一期限仍不能 commit。
2. 正常 DOWN repeat1/2（同组）累计、最终 release 一次；没有初始 DOWN 的 repeat、错误 device/downTime/key UP、重复 UP 不排队；owner FLAG_CANCELED UP 后 drain 零提交，late repeat 不复活。
3. LEFT→RIGHT 抵消为零不 seek、不 play、预览取消；后续新 burst 干净。非方向 DOWN、控制接管、pause/menu/back 每次待提交清零。
4. **首个 session Red**：真实 CustomKeyDown 排队 → 改测试 host manager/session A→B（或 A→B→A 同 URL）→ idleFor(250ms)，B/A2 均零旧 seek；当前 session 正常回调必须 apply，避免“全取消”伪修复。
5. 三 Activity 集成：通过真实 dispatchKeyEvent/lifecycle、真实 onSeekEnd/seek；可用 test-only subclass 或有限 fixture 注入 controller recording boundary，禁止重写实际取消逻辑、复制源代码回调或 source-text assert。检查换集请求刚发出而尚未返回、源/quality/parse/播放器替换、离全屏/onStop/onDestroy。每个声明取消点必须有可执行 consumer 证据或任务专用设备 smoke；不能只 helper/mocks 当验收。
6. Live 集成：可 seek Live burst 同 Vod；普通 `player.isLive()` 左右 UP 仍立即各一次 prevLine/nextLine，没有 delayed seek；上下换台/数字2000ms不变。Cast/Vod 的暂停状态取消不得恢复播放。

未来实施 tree 环境：JDK21 `C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot`，SDK `C:\Users\Maple\AppData\Local\Android\Sdk`，GitBash `G:\Git\bin\bash.exe`。首个命令（只在该任务隔离实施 tree，不能在 root 或父正在 baseline 的 tree）：

```text
bash ./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.ui.custom.CustomKeyDownSeekOwnershipTest' --tests 'com.fongmi.android.tv.ui.activity.TvSeekOwnershipIntegrationTest'
```

先保留 Red 的原始失败输出（必须行为 assertion 失败，不是 classpath/fixture 错误），再实现、同命令 Green。命令未在本研究运行；flavor 名由本地 build 配置推导，若 Gradle task 名不匹配，只做一次 task 精确定位再执行。不得触发 native 全 ABI rebuild；若 unit-test task 拉 native，使用该实施 tree 的现有 prebuilt/任务排除方式，不在 root 动 `.cxx`。

最终 acceptance：250ms/步长/加速不变；同 burst 一次；cancel/repeat/设备与 session 隔离；每个替换/控制/生命周期零旧 seek；三个生产消费者可达；普通 Live 未变 seek；编译/针对测试通过且已有 baseline 非回归；Exo/MPV 分别设备关键 smoke（至少 VOD 快速按键→换集与可 seek/普通 Live），不把一个 player 当另一种实测。无实机时该项明确未通过，不声称全产品完成。

## 剩余 gate、回滚与交接

- **G1 首个 Red/Green 未执行**：实施先完成真实 App/KeyEvent/Looper fixture；Fixture 若需生产 test hook 或触及第六生产文件，停止扩边并回父。
- **G2 actual canSeek/三个 host 取消映射**：不能照抄上游不存在的能力接口；用本地能力与 controller 行为通过集成测试验证。
- **G3 baseline/设备**：父 baseline 完成后在各自隔离任务 tree 实施，完成上列 smoke；本研究不占用它。
- 回滚锚点为 `47454687f504955775b398905ba09d73aeb9f20c`（后续串行实施前记录真正父 HEAD）。独立原子提交包含五生产文件、任务测试与唯一 docs；使用 `git revert <本任务完整commit>`，不 reset、不撤销弹幕后续任务、不改原 dirty 路径。回滚后核对 250ms/普通 Live 左右与原 baseline；恢复标签只指已提交并验证状态。
- 本轮研究简报 run=1/3 累计；同失败假设最多2 attempts累计后换法；本轮文件编辑 cap=2 file累计，生产=0 file累计；只读 commands不限累计；每实质 query改写 cap=3累计；证据 quota每任务重置不结转，当前取决定性 A/B 证据后停止可选搜找。8分钟为前置研究目标而非停工上限。

### Recovery anchor

研究文档完成；未实施、未运行测试。当前允许本 `.git` 文件，未来唯一 docs 路径见上；关键接口是真实 App handler、CustomKeyDown dispatch、已有 PlaybackTrace getter，isOwner(key) 不是会话。下一步：父 baseline 完成后，为本任务开独立 lane/guard，先运行真实 CustomKeyDown 连按首个 Red。

## 实施阶段2 — 2026-11-08（覆盖上方研究期未实施状态）

- 已批准执行；隔离 tree `F:\Workspace\fish2018\webhtv\.codex\task-state\worktrees\tv-small-fixes`，branch `upstream/small-fixes-20261108`，实际 rollback anchor `60f2e86e761b8411e56a86793ee55a2f0b9b67d7`。root 继续只读。
- 父已提交跨任务索引 `docs/upstream-player-dependency-merge-assessment-2026-11-08.md` SHA256 `beec8fbed4434e0c8004b6d66585dd67d2550553ddc087057f8c944c2cc7edf1`，不得编辑；baseline 同 variant 7项PASS为父提供，不重复运行baseline。
- 15:05:18开始；目标15:30完成（定位/RED6分钟，实现增测12分钟，验证收尾7分钟）。超时删可选工作，保留针对测试/两flavor编译/guard/tag，不降低行为gate。
- guard `start --id C-TV-SEEK-OWNERSHIP --mode standard`，scope为上列五TV生产路径、两个任务测试、唯一doc与同前缀ignored报告/log；受控cap8 file累计，生产未改mobile/公共API/资源/Native/弹幕。
- 第一个业务RED：`:app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.custom.CustomKeyDownSeekOwnershipTest --console=plain`；`burstFromPreviousPlaybackDoesNotSeekReplacement`，真实 CustomKeyDownVod.create/onKeyDown + App.async Handler + ShadowLooper PAUSED；两次RIGHT release后真实PlaybackTrace.begin换会话，再drain，1 test / 1 AssertionError。原始证据 `.codex/task-state/reports/C-TV-SEEK-OWNERSHIP-red-behavior.log`。
- 第一个GREEN：同真实test，`BUILD SUCCESSFUL in 1m 4s`，证据 `.codex/task-state/reports/C-TV-SEEK-OWNERSHIP-green-session.log`。旧session不得提交，而非helper/source断言。
- 非业务故障已分类：首次log目录缺失未执行测试，补建ignored目录；首次Shadows.shadowOf重载导致FingerprintManager类缺失，改用既有Shadow.extract，无依赖升级；一次PowerShell `R`命名撞Invoke-History别名，按已确认的任务文件精确从HEAD恢复本次中间写入，改用ReplaceText且ErrorActionPreference=Stop后正确应用。均不冒充业务RED。
- 最小适配为了两个入口共享同一规则，在CustomKeyDownVod中嵌入package-private `SeekSession`、固定commit Runnable和SeekListener；CustomKeyDownLive沿用该局部状态，不新增BaseInput/VodInput/controller/生产文件。mandatory listener getter使用现有PlaybackTrace，NONE禁止；具体生产取消映射与最终测试见下方最终记录。
- 当前运行真实CastActivity的dispatchKeyEvent/预览Back/onSeekEnd consumer测试（仅seekTo输出边界record，真实PlayerManager/Player/trace/控件）；生产hooks=0。

## 最终实施与验收记录 — 2026-11-08

- 已实现且允许路径为：五个leanback生产文件（CustomKeyDownVod/CustomKeyDownLive/VideoActivity/CastActivity/LiveActivity）、两个testLeanback任务测试、唯一doc；累计8受控文件，生产test-only hooks=0。mobile源文件/触摸/后台/PiP/弹幕/Native/资源/Gradle依赖/PlayerManager与PlaybackActivity公共API未修改。
- CustomKeyDownVod内package-private SeekSession复用真实App主Handler，固定commit Runnable，250ms，keyCode/deviceId/downTime + immutable PlaybackTrace session；NONE无效、cancel幂等、late repeat/UP不复活、zero-offset不触发onSeekEnd（故不会经PlaybackActivity.seekTo恢复播放）。CustomKeyDownLive复用同状态，普通Live左右仍走onSeekUnavailable→原onKeyLeft/onKeyRight即时线路分支，数字2000ms与上下换台保留。
- 实际取消映射：Video getDetail/getPlayer/setPlayer、setParse、refresh/replay、quality/核切换的实际替换/stop路径、show/hide/toggle controls、pause/play/seekBack/seekForward、disc/opening-ending/episode/audio-stage接管、exitFullscreen、onReclaim、onStop:6390、onDestroy:6431；Cast start/核/控制/远程seek/pause/play/stop/Back、onStop:565/onDestroy:591；Live fetch(EpgData):829/fetch():839/start/核/controls/showUI/reload/stop/pause/play/Back、onStop:1203/onDestroy:1226。trace仍是未显式通知的替换/预载接管二道防线；没有另迁controller。
- 保留预览Back：Back DOWN移除timer但保持原预览可见，真正onBackInvoked消费预览而不离页；native pause只在isPaused时cancel，不将buffering false当pause。
- 真实业务RED两条：session替换测试1test/1assertion failure（red-behavior.log）；Cast真实dispatchKeyEvent→预览Back→onSeekEnd输出边界测试1test/1assertion failure（red-back.log）。初始化错误不算RED。
- 最终可信验证命令：`gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.custom.CustomKeyDownSeekOwnershipTest --tests com.fongmi.android.tv.ui.activity.TvSeekOwnershipIntegrationTest :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac --console=plain`，日志 `.codex/task-state/reports/C-TV-SEEK-OWNERSHIP-final5.log`，exit0，BUILD SUCCESSFUL in 2m55s；XML汇总21 tests/0 failures/0 errors（16真实输入+5真实Cast/Video消费者），两flavor Java编译PASS。
- 16输入测试具体覆盖：250ms最后释放/新按住合并、repeat、key/device/downTime误匹配、canceled UP/repeat、zero offset不提交、NONE/lost capability、A→B→A session/current positive、cancel/full exit、UP长按速度、Live合并/控制取消、普通Live即时左右/上下/2000ms数字、取消Live UP。5实际Activity测试覆盖Cast预览Back/BackDOWN/控制接管+当前positive、Video实际消费+同key重启trace/控制接管。
- 两个额外大fixture（完整Live实际engine判定/三页未create生命周期）因fixture初始化不足失败，不作为业务RED；按照父2026-11-08确认的窄等价路线改为“真实CustomKeyDownLive行为 + 实际Activity取消调用点代码核对/两flavor编译”，不只删失败宣称完整生命周期已验。完整设备lifecycle/Live实际线路网络/MPV/后台/PiP仍**未设备验证**，不得把上述21PASS等同这些场景实测。
- 试验中的COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM能力门会在未prepare fixture/timeline时抑制既有正常输入，已移除；保留本地非空/isOwner/非redirect/trace及Live原isLive分支，不将新能力门改变当前TV语义。编译静态start误插入cancel及KeyUtil缺import已修正。
- `git diff --check` PASS；跨任务索引SHA256仍 `beec8fbed4434e0c8004b6d66585dd67d2550553ddc087057f8c944c2cc7edf1`。所有报告/log在ignored .codex/task-state/reports，不stage/commit。原root和app/.cxx未操作。
- 回滚锚点 `60f2e86e761b8411e56a86793ee55a2f0b9b67d7`；应独立revert本任务完整commit，并重新检查250ms/普通Live原分支；不要reset或撤销后续弹幕单元。
- 代码commit/recovery tag由下一个task_guard finish原子生成；精确ID留ignored report及guard current/committed_head/recovery_tag（commit无法在自身正文写自己的hash，不改写/移动标签）。父如要求唯一doc记录具体post-commit ID，可用不可避免的文档-only closure而不重开测试/生产。

### 最终 Recovery anchor

状态：局部seek实现与21聚焦测试/双flavor编译已PASS，代码审阅/范围/索引保护PASS；完整设备生命周期/Live网络/MPV/后台/PiP未验且已明确路由给父。下一动作：task_guard finish，生成本单元原子commit与recovery tag，报告精确ID，交父独立复核后串行弹幕。

## 最终集成归档 — 2026-11-08

- 用户已批准本任务窄适配，原始代码提交及恢复点在合并历史保留，不重写。
- 代码/修复提交：`dc5f1e60c4f96f88f5183cbf11b50bf97f2c6b65`
- 本地恢复 tag：`recovery/C-TV-SEEK-OWNERSHIP/20261108075050-dc5f1e60c4f9`
- 单元证据：21项真实输入/消费者测试和双端Java编译通过；完整设备生命周期未测
- 联合验证（2026-11-08 集成树 fresh）：9 类 94 项定向测试 0 失败/0 错误/0 跳过；Leanback Mobile arm64 Debug Java、Mobile UnitTestJavaCompile 编译通过；日志 `.codex/task-state/reports/TV-SMALL-FIXES-INTEGRATION-final.log`。整批仍不冒充 decoder 实播/设备/性能验收。
- 回滚：针对本任务按上述代码链逆序git revert；合并历史保留真实来源，不reset原工作区或移动tag。

## 本轮评审修复

改动范围仅 `CustomKeyDownVod.java`、`CustomKeyDownLive.java`、`LiveActivity.java`、`CustomKeyDownSeekOwnershipTest.java` 与本文件；无新增/删除文件。

1. `CustomKeyDownVod.SeekSession.onUp`：原先 `if (!owns(event)) return;` 让"没有配对 DOWN 的左右 UP"被完全丢弃，回归了旧实现（旧 `CustomKeyDownVod`/`CustomKeyDownLive` 的 ACTION_UP 分支无条件调用 `onKeyLeft`/`onKeyRight`）。现改为 `pending == false` 时先 `listener.onSeekUnavailable(event)` 再 return；`pending == true`（有进行中的 seek 却收到错配 UP）仍直接 return，不误切线路。
2. `LiveActivity.canSeek()`：删除 `dispatch(true)` 条件。面板/列表可见性已在 `CustomKeyDownLive.onKeyDown` 的 DOWN 阶段判过；保留它会让 UP 之后 250ms 的合法时移 seek 在面板恰好出现时被静默丢弃。其余条件与顺序保持原样。
3. `CustomKeyDownLive.check()`：`listener.onKeyDown();` 后的多余空行删除，if/else 链连续，无逻辑变化。

影响面：VOD/Cast 不受影响（`onSeekUnavailable` 只被 `CustomKeyDownLive.Listener` 覆盖，VOD/Cast 侧仍是默认 no-op，`unownedRepeatAndReleaseCannotCreateBurst` 语义保持）；普通 Live 恢复左右 UP 立即切线路的旧行为；`canSeek` 不再因控制面板可见性丢弃已提交的 seek。`CustomKeyDownVod.SeekListener`/`CustomKeyDownLive.Listener` 方法集合与实现者保持一致，没有新增抽象方法。

新增测试：`CustomKeyDownSeekOwnershipTest.unpairedLiveReleaseStillSwitchesLineWithoutSeek` —— 只发左右 UP、无配对 DOWN 时 `LiveHost` 的 `lefts`/`rights` 各 1 且 `seeks` 为空，再断言 `VodHost` 同输入下 `seeks` 仍为空（VOD 默认 no-op）。

验证状态（2026-11-09，Asia/Shanghai，父代理执行）：`gradlew.bat --console=plain :app:compileMobileArm64_v8aDebugJavaWithJavac :app:testLeanbackArm64_v8aDebugUnitTest --tests <6 类>` → `BUILD SUCCESSFUL in 7m29s`，mobile 与 leanback 主源集及 `compileLeanbackArm64_v8aDebugUnitTestJavaWithJavac` 全部通过；6 类共 80 tests / 0 failures / 0 errors / 0 skipped（`DanmakuApiOwnershipTest` 3、`StrmHttpTest` 19、`TvSeekOwnershipIntegrationTest` 5、`VideoDanmakuOwnershipTest` 22、`CustomKeyDownSeekOwnershipTest` 17、`ProxyResponseCompatibilityTest` 14）。仍未验：设备/真机（实机 seek、直播即时切线路、时移、慢响应弹幕覆盖、mobile 真实消费者）、全量 Gradle 矩阵/`connectedAndroidTest`/APK 打包安装、QuickJS/Python 真桥的 `destroy` 清理与 gzip 端到端播放。
