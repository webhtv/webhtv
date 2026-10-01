# 观看历史封面时间进度

## Recovery anchor

- 用户需求：在观看历史的封面显示上次观看时间，方便确认看到第几分钟。手机观看历史页与电视首页历史卡片同时覆盖。
- 本轮截图定位基线：`dev4` / `908774597a32eca30786fdd3c0c55ab002512ac4`。guard `ui-remove-duplicate-history-watched-mobile`，`quick-fix`；开始前工作区干净，保护面 0。
- 2026-09-28 新需求：手机版历史记录卡片下部重复出现的“已看 mm:ss”行要去掉，进度只保留封面上原有的时间标签。电视端已在 `7605a7d589f` 做过同类清理，本轮只处理手机版。
- 完成条件：手机版历史卡片下部只保留集数（remark）+片名（name），不再出现“已看 …”行；封面标签（playback）保留；收藏页 Goalkeeper/卡片尺寸/删除态/跑马灯契约不变。
- 当前状态：已完成并已交付。手机版 `adapter_vod.xml` 删除 `historyProgress` 节点，`HistoryAdapter` 移除对应绑定与导入；复评后补删已无生产引用的 `HistoryProgressFormatter` 及其单测与三语言 `history_watched_time`；定向单测 3/3 通过；APK 已覆盖安装并在模拟器上确认视图树无 `historyProgress`、截图中已看行消失、封面标签仍在。
- 唯一下一动作：无（已推送 `dev4` 并创建 PR `Silent1566/webhtv#384`，base `beta`，等待维护者评审；本任务不代为合并）。

## 展示设计与证据（2026-09-10）

这是已有观看进度的展示补全，复用原卡片标签，不是播放器/上游依赖整合；不占用P9或上游任务编号。

| 来源 | 证据与决定 |
| --- | --- |
| 当前基线 `History.java`、Mobile `HistoryAdapter`/`HistoryActivity`、Leanback `HistoryPresenter`/`HomeActivity` | A：position/duration为毫秒，未设置值为负数；手机已有进度条，两端均从同一History读取。已有刷新事件及Diffable链可复用；isSameContent尚未比较position/duration/remarks，补齐会影响封面内容的字段即可，不改保存或读取。 |
| 当前基线两端 `adapter_vod.xml`、`KeepAdapter`、`shape_vod_remark.xml` | A：历史和收藏共用卡片，原标签/删除层/图片尺寸是保留契约。只增加默认GONE的时间标签，历史绑定时显式设置文本和可见性；将原集数与时间垂直排列避免互相覆盖。收藏仍无时间标签，不改变图片或卡片高度。 |
| [AOSP DateUtils.java，android-15.0.0_r1](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/text/format/DateUtils.java#L429)，经用户代理读取；快照 `/tmp/history-cover-progress-20260910.CMrH7W/DateUtils.java` | A：formatElapsedTime使用MM:SS/H:MM:SS及累计小时，不将时长当日期/时区处理。采用相同时间形式，在纯Java小格式化器中先过滤未知位置及钳制有效总长，按完整秒展示，不向下一秒四舍五入；英文/简繁中文案走Android资源。 |
| 上游PR/issues/reverts、论文、博客和基准 | 本次无依赖更新、未解决的平台争议、新算法或性能改善主张；不适用新的合并/性能结论。成熟实现与官方契约已覆盖本决策，额外检索不改变数值展示方案。 |

- 不改：只能看手机比例条，无法得知具体分钟数。
- 原样平台方案：直接DateUtils.formatElapsedTime可格式化，但不能决定无效历史、删除态、总时长钳制及列表刷新；仍需本地适配。
- 采用窄适配：每次绑定从现有position/duration生成短时长，原集数标签之下显示一行“已看 …”；无效时设置空文本并GONE，删除态同样GONE。总时长未知不伪装成0，已知时长仅限制显示值，不写回数据、不改变续播位置。
- 风险与对策：共享布局新增标签默认隐藏；保留原备注样式与位置、卡片外部尺寸和电视焦点；单行窄卡片从前方省略标签前缀以优先保留时间。格式化与边界逻辑纯Java，不依赖Android资源初始化；不新增网络、持久化、权限、依赖、ABI或后台任务。
- 验证：小于一分钟/一分钟/一小时/跨24小时、毫秒边界、0/负数/未知、未知总时长、超出总长和大于int范围；进度/总长/集数单独变化影响Diffable而相同副本不变。运行Mobile arm64定向单测与Leanback armv7 Java/资源编译，不做原生或全构建矩阵。当前无设备，实机截图不作为已完成结果。
- 回滚：原子revert本任务提交；上述基线已包含之前MPV修复，本任务不修改其代码/资产。

## 验证记录

- 已实现：默认GONE的封面时间标签，仅由两端历史绑定显示；原集数仍独立显示，删除态隐藏。共用格式化处理无效/未知/超界/长时长，只计算显示值；History内容比较增加position/duration/remarks，保证单独进度变化能刷新。
- 2026-09-10：使用独立JDK21运行 `JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home LC_ALL=C bash ./gradlew --offline :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.utils.HistoryProgressFormatterTest --tests com.fongmi.android.tv.bean.HistoryTest :app:compileLeanbackArmeabi_v7aDebugJavaWithJavac --console=plain`，`BUILD SUCCESSFUL in 2m 3s`，96 tasks、24 executed。
- JUnit XML确认 `HistoryProgressFormatterTest` 7项、`HistoryTest` 7项，均0 failures/0 errors/0 skipped；覆盖时间格式及毫秒/小时/24小时/long边界、无效数据、未知总长、超界钳制，以及相同副本和独立进度/时长/集数变化。
- 首次命令错误指定了不存在的Android Studio JDK目录，Gradle未开始编译；读取仓库已有命令后改用独立JDK21，一次完成实际测试/编译。不是代码回归，也未放宽验证。
- 证据目录 `/tmp/history-cover-progress-20260910.CMrH7W/`；日志 `tests-and-compile-jdk21.log`，原始环境失败保留在 `tests-and-compile.log`。设备列表为空，无截图/安装结果；此次不触发原生/CMake或APK打包，不把编译通过等同实机视觉通过。
- 收口：只提交guard内本任务文件，保留35个预存dirty文件；本地注释恢复tag由guard finish在提交后立即生成，不push。

### 2026-09-10 顶部定位（已被下一记录纠正）

- 将两端`historyProgress`从底部集数容器移到根RelativeLayout，使用`layout_below=site`、`layout_alignStart=image`与`layout_alignWithParentIfMissing=true`。字号、颜色、背景、间距、可见性、文案和Java逻辑均不变。
- 定向XML检查两端均PASS：解析基线与候选，验证新父节点及定位属性；删除时间标签后，剩余树的标签/属性/内容/顺序完全一致。日志`/tmp/history-cover-progress-top-20260910-layout-check.log`；没有重跑未改动的时间格式化测试或构建。

### 2026-09-10 纠正为文件名上方

- 用户澄清位置是文件名上方，上一轮将其移到封面顶部不符合要求。把时间标签放回下部容器，顺序为集数→已看时间→文件名，撤销顶部定位属性；其余内容/样式和Java代码不变。
- `git diff --exit-code 881c8bca2e6831d6a7d32f67c22c64ce37541e0b -- app/src/mobile/res/layout/adapter_vod.xml app/src/leanback/res/layout/adapter_vod.xml`通过，两份文件与此前编译通过的版本完全一致。证据`/tmp/history-cover-progress-above-name-20260910.log`；不重复既有编译/格式化测试，不声称已安装或实机通过。

### 2026-09-10 按圈图互换（取代此前对“文件名”的误识）

- 已查看用户图片，明确红圈是封面内的文件名`remark`，底部`name`是影片标题。此前把两者混为一谈，不符合用户要交换的控件。
- 仅将同一底部LinearLayout的子项从`remark, historyProgress`换为`historyProgress, remark`；时间标签的2dp上边距换为2dp下边距，保留原行间距。没有移动底部标题或改Java/文字/样式。
- 定向XML检查Mobile/Leanback均PASS：先断言实际顺序已互换与间距仍2dp，再在内存中还原这两个变化，验证整个树的标签、属性、内容与顺序和基线完全相同。证据`/tmp/history-cover-swap-remark-20260910-check.log`。未打包/安装，不声称实机已更新。

### 2026-09-28 手机版移除下部重复进度行（本轮）

- 需求来源：用户提供手机版历史页截图，要求去掉卡片下部重复的“已看 00:22”行，只保留封面上方标签里的进度。电视端等价清理已由 `7605a7d589f`（`fix(leanback): remove duplicate watched time on recent cards`）完成，本轮是手机版对齐，不是新能力。手机版无 `HistoryPresenter`，历史页与收藏页共用 `adapter_vod.xml`，因此只动历史绑定与共享布局。
- 改动（3 个文件，最小面）：`app/src/mobile/res/layout/adapter_vod.xml` 删除 `@+id/historyProgress` 文本节点（17 行）；`app/src/mobile/java/com/fongmi/android/tv/ui/adapter/HistoryAdapter.java` 删除该视图的文本/可见性绑定及随之无用的 `HistoryProgressFormatter`、`R` 导入；`HistoryAdapterTest` 新增定向断言。保留 `playback` 封面标签、`progress` 进度条、`remark`/`name` 两行、`delete` 删除层、`setMarquee` 跑马灯与 `history_info`（`HistoryActivity.findMarqueeRange` 依赖该 id，未改）。
- 静态证据：`grep -rn historyProgress app/src` 在非 build 路径下无任何残留；`git diff --check` 干净；lens 诊断对 `HistoryAdapter.java` 判为 clean（布局 XML 无 LSP 服务器，无法自动校验）。
- 定向单测：`JAVA_HOME=<JDK21> bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.adapter.HistoryAdapterTest --console=plain` → `BUILD SUCCESSFUL in 2m 8s`；JUnit XML `tests="2" skipped="0" failures="0" errors="0"`，含既有 `historyCardsShowPlaybackProgressAndTime` 与新增 `mobileHistoryCardDoesNotRepeatWatchedTimeBelowThePoster`。
- 负向对照（证明新断言有判别力，不是永真）：`git show HEAD:...HistoryAdapter.java | grep -c historyProgress` = 2，即基线代码确实会被新断言判失败。
- 设备级视觉证据（覆盖安装，未卸载）：`assembleMobileArm64_v8aDebug` 产出 `app-mobile-arm64_v8a-debug.apk`（sha256 `693018067b25edfd7204d2441d131d9e4b884cefce624ef96f1c10c50fbb2f85`，构建于源码改动之后）。发现 `emulator-5558`/`5562`/`5554` 上的已装包使用另一套证书，直接 `install -r` 报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`；未采取卸载（遵守覆盖安装约束），改用证书一致（`PackageSignatures` hash `0x4dbe7298` = 本机 `~/.android/debug.keystore`）的 `emulator-5560`/`5556`，`adb -s emulator-5560 install -r` → `Success`。
- 设备级断言：进入 `HistoryActivity` 后 `dumpsys activity top` 的视图树中 `historyProgress` 出现次数为 **0**；每张卡片 `app:id/history_info` 下恰有 `name`（11,7-276,36）与 `remark`（11,40-276,64）两个子节点；`@+id/playback` 在首卡为 `V`（0,339-139,382），即封面标签仍在且第二行仍可见。截图（`/tmp/hist-mobile.png`）与放大裁切（`/tmp/crop-card1-full.png`）显示底部只有“第01集”与片名，无“已看 …”行，封面右上仍为“00:22 / 24:12”。
- 已知非本任务现象：`emulator-5560` 过程中出现过一次 `SystemJobService` 的 ANR 与启动缓慢；该现象与本次布局/绑定改动无关，且不影响上面的历史页证据，本任务不做处理。
- 回滚：原子 revert 本任务提交即可恢复下部“已看 …”行；不涉及数据、依赖、ABI、资源字符串或网络变径。

### 2026-09-28 复评修正：清除已被上游移除的遗留死代码（本轮）

- 复评发现（自身提交文档断言不实）：上一轮记录写“`HistoryProgressFormatter` 仍被 TV 端使用”，实测为假。TV 端的同类清理 `7605a7d589f` 在合并基线中已删除 `HistoryPresenter` 对它的唯一调用，因此上一轮把它的最后一次生产调用也删掉后，`HistoryProgressFormatter`、其单测与 `history_watched_time`（三语言）已无任何生产引用，属死代码；同时“电视端仍使用”的说法把范围判断记录错了。这是上一轮提交内的问题，需在本任务内闭环。
- 死代码证据：`grep -rn HistoryProgressFormatter app/src/{main,leanback,mobile}/java --include=*.java` 只命中该类自身；`grep -rn history_watched_time app/src/{main,leanback,mobile}/{java,res}` 无命中（仅 `values*/strings.xml` 定义处）。`HistoryProgressFormatterTest` 只测该类自身；无 proguard/lint 白名单、无反射/SDK 入口引用（无 lint baseline，`app/build.gradle` 的 `lint { }` 未启用 `UnusedResources` 阻断，但删除仍以源码引用为准）。
- 修正（6 个文件）：删除 `app/src/main/java/com/fongmi/android/tv/utils/HistoryProgressFormatter.java` 与 `app/src/test/java/com/fongmi/android/tv/utils/HistoryProgressFormatterTest.java`；从 `values/strings.xml`、`values-zh-rCN/strings.xml`、`values-zh-rTW/strings.xml` 各删 1 行 `history_watched_time`；`HistoryAdapterTest` 把上一轮的单文件字符串断言升级为布局+双端适配器+三语言字符串的联合断言，并新增 `removedBelowPosterTimeHasNoLeftoverProductionReferences` 防止该死代码回流。
- 断言升级点：`assertMobileHistoryDoesNotRenderBelowPosterTime` 解析手机布局，断言 `history_info` 恰好 2 个子控件且顺序为 `name`→`remark`、`playback` 仍挂在根布局并位于 `history_info` 之前（封面标签是唯一进度显示），把上一轮“改后是否真的只少一行”从字符串 grep 提升为结构断言。
- 与合并约束的关系：不改动任何 beta 侧已回退/已移除的提交内容，不重新引入 `historyProgress`/`history_watched_time`；仅删除本任务在自身提交链中已证明无生产引用的残留。
- 回滚：单独 revert 本次修正即可恢复该类与字符串；因它们已无生产调用，revert 不改变任何运行时行为。
- 验证：见下方“2026-09-28 复评修正验证”。

### 2026-09-28 复评修正验证

- 正向定向单测（本轮复跑，工作区干净且已包含本轮修正）：`JAVA_HOME="C:\\Program Files\\Android\\Android Studio\\jbr" bash ./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests com.fongmi.android.tv.ui.adapter.HistoryAdapterTest --console=plain` → `BUILD SUCCESSFUL`；`app/build/test-results/testMobileArm64_v8aDebugUnitTest/TEST-com.fongmi.android.tv.ui.adapter.HistoryAdapterTest.xml` 为 `tests="3" skipped="0" failures="0" errors="0"`，含 `historyCardsShowPlaybackProgressAndTime`、`mobileHistoryCardDoesNotRepeatWatchedTimeBelowThePoster`、`removedBelowPosterTimeHasNoLeftoverProductionReferences`。
- 判别力负向对照（结构断言，本轮新做）：用编辑器把 `@+id/historyProgress` 文本节点重新插回手机布局 `history_info` 容器，复跑同一命令 → `HistoryAdapterTest > historyCardsShowPlaybackProgressAndTime FAILED` 与 `mobileHistoryCardDoesNotRepeatWatchedTimeBelowThePoster FAILED`，`3 tests completed, 2 failed`，`BUILD FAILED`；随后按签名校验还原布局（`diff -q` 与改动前备份一致），复跑恢复 `BUILD SUCCESSFUL`。证明上一轮被本轮取代的“仅查自身文件”断言的盲区（只改布局不查 Java 即漏检）已被新结构断言堵住。
- 编译面：同一次命令附带 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` → `BUILD SUCCESSFUL`，确认删除该类/字符串未破坏 TV 端与数据绑定生成代码。
- 遗留残留面：`grep -rn "HistoryProgressFormatter|history_watched_time" app/src/{main,leanback,mobile}` 在非 build 路径下无命中；`HistoryProgressFormatterTest` 已随之删除，不存在悬空测试。
- 未做与原因：本轮不改 `adapter_vod.xml`、`HistoryAdapter.java`（已无改动）、不改 ABI/依赖/网络；不再重跑上一轮已记录的设备级截图证据（本轮不引入新的运行时行为，只删已无调用的代码）。
