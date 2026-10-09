# CACHE-MGMT-01 设置页缓存管理升级设计

> 状态：P0-P4 代码已实现并提交；手机与 TV 设备主路径验收通过，其余回归项按清单标注
> 适用分支：`Silent1566` 及其后续开发分支
> 基线：`8e4d9333de8ea7346491e71a0b1ab6858a852298`
> 文档类型：实现设计 + 测试验收规范
> 本文只定义方案，不代表代码已经实现。

## Recovery anchor

- 当前追加修复（2026-10-09，`cache-full-cleanup-and-temp-fix`）：用户反馈①「缓存管理里的临时文件清理不了」，②「新增长按缓存管理按钮清理全部（即拆分前的缓存一键清理效果）」。已完成：`explicitRequest(mode)` 让显式请求（`MODULE`/`FULL`）忽略后台保留期，新增 `CacheCleanupMode.FULL` + `CacheCleanupManager.cleanEverything()/sweepResidual()`，长按入口接到 TV `SettingActivity` 与移动端 `SettingFragment`（最终为 `CacheManagementDialog.cleanEverything()`，**不打开面板**）。代码仅限缓存包、`CacheManagementDialog`、两个设置入口与缓存测试，未改播放器、布局和清理策略。验证：缓存包单元测试 **69/69 通过**，双形态 Debug Java 编译通过；**设备（5563，leanback arm64 debug 覆盖安装）两项均已实测通过**：模块行「清理」`释放 3 MB · 删除 1 个文件`（新建临时包被删、其他模块不动），长按设置页缓存行 `释放 6.6 MB · 删除 119 个文件` 且总量变「共 无」、cache 目录清空，两者 journal 均 `COMPLETED/warnings=[]`；设置行自动刷新为「无」；全清后冷启动无崩溃且 chaquopy/WebView 缓存自动重建。两项需求已完成验证，无未完成项。**后续修正（同日，用户指正）**：长按一键全清原本会打开面板，已改为不弹面板、后台执行并以 Toast 报告结果（见下 2026-10-09 记录 ⑦），并按此重新实测（长按后 `mResumedActivity` 仍为 `SettingActivity`、无障碍树无面板节点、Toast「释放 2.3 MB · 删除 7 个文件」、journal `mode=FULL/reason=shortcut/deletedFiles=7/COMPLETED`）。**评审轮（同日，`cache-full-cleanup-review`）**：对本分支 3 个已提交未推送提交逐文件评审，确认无功能缺陷，修复 2 项真问题并补 2 例回归测试——①保留期判定收敛为单一表达式 `CacheCleanupManager.explicitRetention(mode, backgroundRetentionMs)`，使「临时文件清理无效」缺陷真正被回归锁锁住（此前用例把窗口写死成 `0L`，把行内按钮改回 24 小时窗口仍会全绿）；②`describe(result, resources)` 由调用方提供资源，面板恢复走 Activity 资源、长按 Toast 走 `Setting.wrapLanguage(App.get())`，避免应用内语言与系统语言不一致时状态行/Toast 退回简体。验证：缓存包单测 **71/71 通过**、`:app:compileMobileArm64_v8aDebugJavaWithJavac` 与 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过。残留未覆盖：移动端 flavor、播放中长按，以及同一进程内「全清后立即播放」的 Exo 缓存写回路径（见下 2026-10-09 评审轮记录）。
- 上一轮修复（2026-10-06，`mobile-cache-layout`，已提交 `58ebb653ff`）：用户截图证实手机窄屏下模块文字被同排按钮挤成窄列，底部策略/清理按钮被裁切。范围仅限 `CacheManagementDialog` 的 mobile 排版/焦点分支、mobile 专用布局及 `CacheManagementDialogLayoutTest`，TV 原布局、缓存策略和确认流程不变；基线 `d042cd542b8768ec9dbd2582923088e54a7bfeb1` / `Silent1566`，保护原有 `app/.cxx/`。基线 arm64 mobile 包和测试包均以 `adb install -r` 覆盖安装，4/4 布局用例按预期失败，320dp/2 倍字体的明细宽度仅 16px（应为 464px）。修复后 `:app:assembleMobileArm64_v8aDebug` 与对应 AndroidTest 构建成功，4/4 用例通过；TV 公共布局 XML 未改动。emulator-5560 实测 720×1600、320dpi、1.3 倍字体：模块详情占满宽度，按钮分成独立两列，向下滚动后“轻度/标准/深度清理、刷新、确定”均完整可见；截图与 UIAutomator 均无文字截断，测试结束后尺寸、密度、字体和旋转已恢复。

- 目标：把设置页现有“缓存大小 + 一键全删”升级为可观测、可分级清理、可配置上限、可自动维护的缓存管理中心。
- 当前状态：P0-P4、全部收口修复与 §11.3 配置迁移均已提交并带恢复标签。缓存包独立 JUnit 曾 **41/41 通过**，本轮迁移判定另以 `CachePolicyStoreTest` **3/3 通过**复核；双形态 Java 编译通过。5563 设备端已实测：统计/清理恢复真实值（此前因祖先符号链接误判恒为 0，已修复）、深度清理单窗口两步确认、播放/seek/预载正常、播放中标准清理不中断、EXO↔MPV 切换不改动模块统计、低空间 Job 触发 L2、系统配额展示与上限收敛、EPG 自动刷新落盘、歌词多源链路、字幕渲染、K 歌模块隔离、备份/恢复闭环、进行中临时包保护、移动端 flavor 主路径；另于 2026-09-28 修复并实测「清理完成后设置页缓存数值立即刷新」（leanback 22.1→14.2 MB、移动端 23.5→14.5 MB，均在同一个 Activity/pid、无翻页/无 tab 切换下完成，与弹窗可信值一致）。
- 关键约束：`Path.cache()` 是混合目录，绝不能被当作一个全局可任意淘汰的缓存池。
- 实施顺序：P0 缓存清单 → P1 分类清理 → P2 模块上限 → P3 自动清理 → P4 运行中治理增强。
- 已知边界（非阻塞、已记录）：①应用更新完整「检查更新→HTTPS 下载→安装器交互」闭环受环境限制（`ApkUrlPolicy` 拒绝私网/HTTP、无线上发布包、安装需人工确认），其缓存契约部分已实测；②插件模块上限仅持久化配置，自动淘汰按安全设计暂不启用（§20.6）；③**冷启动首次清单扫描可能被每模块 2 秒预算截断而少计一个模块**（见下方 2026-09-28 记录第③条），与清理后刷新链路无关，已列为独立跟进项。
- 下一步：如需继续，仅剩上述三项环境/设计边界；无未解决的 P0/P1 缺陷，无未勾选的验收项。

## 1. 背景

### 实施记录

- 2026-09-22：P0 落地。`CacheInventory` 只读扫描已归类的 owner 路径，未知顶层文件由 legacy/orphan 模块覆盖；`CacheCenter` 在单线程执行器上生成快照并做 3 秒缓存；移动端和 TV 端设置入口改为进入 `CacheManagementDialog`，显示总量、系统配额和模块明细。
- P0 验证：`:app:compileMobileArm64_v8aDebugJavaWithJavac` 与 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 通过。缓存单测源码已新增；本机 `testMobileArm64_v8aDebugUnitTest` 当前被既有 `ExoCompressedAudioDirectPolicyTest` 的 Media3 API 可见性错误阻断，该失败与缓存改动无调用关系，待 P1 设备/测试阶段复核。
- 2026-09-23：P1 落地。新增 `CacheCleanupManager`、`CachePolicyEngine`、`CacheCleanupPlan/Progress/Result/Status`；为 Exo、MPV HLS、WebHome raw、歌词、K 歌、EPG 增加 owner 清理接口；缓存管理弹窗支持 L1/L2/L3、单项清理、确认、进度、取消和部分失败结果。
- P1 修正：`CacheInventory` 的空排除后缀集合曾导致所有文件被跳过，已修为显式非空判断；缓存纯测试 12/12 通过（独立 JUnit，绕开既有 Exo 测试的编译阻塞）。
- 2026-09-23：P2 落地。新增 `CachePolicyStore`（`cache_mgmt_` 前缀持久化）、`CacheLimitOptions`、`CacheRetentionManager`（LRU + TTL）；Glide 内部磁盘缓存上限接入 `OkGlideModule`；缓存管理页支持模块上限选择；缓存纯测试 15/15 通过，双形态 Java 编译通过。播放缓存上限继续沿用现有播放设置，没有新增第二套 key。
- 2026-09-23：P3 进程内落地。新增 `CacheScheduler` 与 `CacheAutoCleanupPolicy`：启动延迟 30 秒、每 7 天检查、低空间连续两次触发；低空间阈值 `max(512MB, 10%)`；播放中降级 L1；自动清理只使用 L1/L2，永不 L3；记录 `cache_mgmt_last_auto_ms` 与低空间连续计数。缓存纯测试 18/18 通过，双形态 Java 编译通过。
- P3B 修正：使用平台自带 `JobScheduler`（`setPersisted(true)` + `RECEIVE_BOOT_COMPLETED`）补齐设备重启后的调度恢复，`CacheCleanupJobService` 在系统触发时执行同一套 L1/L2 自动清理；未引入 WorkManager 依赖。同时为 `CacheRetentionManager` 增加符号链接保护，缓存纯测试 20/20 通过。
- 2026-09-23：P4 落地。Exo 空闲重建、MPV HLS coordinator 安全清理、Glide 后台 `clearDiskCache`、OkHttp `evictAll` 均已接入；新增 `CacheCleanupRecord` 与 `CacheCleanupJournal`，记录触发原因、等级、清理前后字节、删除/跳过数、失败原因和耗时；缓存纯测试 19/19 通过，双形态 Java 编译通过。
- 2026-09-23：P2B 落地。新增 `CacheTotalLimitPolicy` 与 `cache_mgmt_total_limit_bytes` 配置，缓存管理页支持总缓存软上限；调度器在超限时先执行 L1，仍超限且未播放时再执行 L2，永不自动执行 L3；有效上限取 `min(用户上限, 系统 quota)`。缓存纯测试 23/23 通过。
- 2026-09-23：P2C 收口。临时文件模块上限现在真实生效（LRU 到 90% 目标，且受 1 小时最小年龄保护，避免删除进行中的下载/安装）；插件脚本上限入口隐藏并记录为暂缓，因为运行中暂停插件加载尚未实现，误删可能导致回归。缓存纯测试 24/24 通过。
- 2026-09-23：P2D 收口。插件脚本上限实现为 owner 级保守回收：只删除超过保留期且不在活跃加载器键集合中的 `jar/`、`py/`、`js/` 文件；`jar/` 同时是 `DexClassLoader` 优化目录，因此容量上限不会强制淘汰 jar，避免影响正在加载的插件。缓存纯测试 25/25 通过。
- 2026-09-23：设备验收发现模块列表会把弹窗撑满，导致三级清理和自动清理控件被裁出屏幕；`dialog_cache_management.xml` 改为根布局占满可用高度、模块列表 `0dp + weight=1`，确保底部操作始终可见。
- 2026-09-23：移动端设备验收（LIO-AN00 / Android 9 / 192.168.50.3:5555，arm64 debug 覆盖安装）。设置页显示“缓存管理”，弹窗显示总量/系统配额/模块列表/上限/自动清理/保留期/总上限，三级清理按钮完整可见；点击“轻度清理”出现确认框，确认后显示“清理完成 · 释放 无 · 删除 0 个文件”。扫描因模拟器 cache 目录权限返回“部分结果 · 16 项警告”，符合部分结果降级预期。
- 2026-09-23：TV（leanback）验收通过（NX627J / Android 9 / 192.168.50.3:5563，arm64 debug 覆盖安装）。遥控器方向键可到达设置页“缓存管理”，弹窗内模块列表、上限、自动清理、保留期、总上限、三级清理、刷新和确定均可见且 focusable；在模块“清理”按钮上按确认可打开确认框，确认后显示“清理完成 · 释放 无 · 删除 0 个文件”，并写入 `cache_mgmt_cleanup_history` 持久化记录。5557 上既有包签名冲突，未卸载任何现有包。
- 2026-09-23：扫描性能实测。`CacheCenter` 持久化记录 `cache_mgmt_inventory_duration_ms/modules/warnings`；在 NX627J（Android 9）实测 13 个模块、16 条部分结果 warning、扫描耗时 9 ms，满足高速设备 <1 秒与超时/降级要求。
- 2026-09-23：兼容性实测。NX627J 上切换 `user_rotation` 与 `cmd uimode night yes/no` 后，缓存管理弹窗控件仍完整可见，进程存活且无 `FATAL EXCEPTION`/`ANR`；`tv` 主数据库内容文件未被缓存弹窗改写，说明收藏/历史/设置库不在清理路径内。
- 2026-09-23：自动清理调度实测。NX627J 打开自动清理后 `cache_mgmt_auto_enabled=true`，`dumpsys jobscheduler` 显示 `CacheCleanupJobService` 以 `PERSISTED`、周期 `7d` 注册（`batteryNotLow` 约束）；关闭开关后偏好写回 `false`，Pending queue 为空，Job 正确取消，仅保留历史 START/STOP 记录。
- 2026-09-23：配置保留实测。执行“标准清理”（真实结果为“部分完成”）前后，MPV `files/mpv/mpv.conf` 与 `fonts.conf` 的 md5 完全一致（`d696b635…`、`cf92f416…`），WebHome 相关偏好行哈希保持一致（`7963216a…`），证明清理不会改动 MPV 自定义配置或 WebHome 扩展配置。
- 2026-09-23：回归验证受阻记录。5563 上唯一本地视频 `ijk-smoke.mp4` 经应用 `push` 入口播放后在 32 ms 处返回 `Source error`（MediaSession state=7），远程 Google 样例同样 `Source error`；该设备未加载 VOD/EPG 配置，TV 界面 `uiautomator` 多次返回 null root。因此“播放、seek、预载、重缓冲不变差”“歌词、字幕、K 歌仍可用”“EPG 直播节目单仍可刷新”“应用更新、备份、恢复仍可完成”“播放器切换不影响其他模块统计”保持未通过，待具备可用媒体与配置的环境补测。
- 2026-09-23：阻塞根因定位。5563 上 `VodConfig.load` 以 `okhttp3.Request$Builder.url` 的 NPE 结束——传入 URL 为 null（`OkHttp.newCall(OkHttp.java:174)` ← `Decoder.getJson(Decoder.java:26)` ← `VodConfig.load(VodConfig.java:118)`）。该设备点播配置为空，导致依赖点播源的播放/歌词/K 歌/EPG 回归无法执行；这与缓存管理改动无调用关系，但需重新配置设备后再验证。
- 2026-09-23：主线程 I/O 修复。审计发现 `CacheScheduler.enforceTotalLimit` 的 L1 清理回调由 `App.post` 在主线程执行，其中仍会调用 `totalCacheBytes()` 触发全量库存扫描，违反“扫描不得在主线程”的不变量。现改为把二次扫描与 L2 触发提交到调度器后台线程；双形态编译与缓存纯测试 25/25 通过。
- 2026-09-23：注册表校验补齐。抽出不依赖 Android 运行时的 `CacheModuleRegistry`（纯函数，只接收 cacheDir），并实现设计风险表要求的启动校验：模块 ID 唯一、根路径必须落在 cacheDir 内、禁止重复根目录、禁止树形根目录相互嵌套；`CacheInventory.scan()` 每次扫描都会执行校验并把问题作为 warning 暴露。新增 `CacheModuleRegistryTest` 覆盖 ID 唯一、越界、嵌套、重复及“注册表与播放器无关（纯函数）”不变量；缓存纯测试 32/32 通过。
- 2026-09-23：播放热路径改动审查（结构性证据，不等于设备实测）。相对基线 `8e4d9333de`，播放相关生产代码（`player/`、`androidx/media3/mpvplayer`、`web/`、`api/`、`service/`、`App`、`AndroidManifest`）为 **110 行纯新增、0 行修改、0 行删除**：Exo 仅新增 `clearCacheIfIdle()`、MPV HLS 仅新增 `clearIfIdle()`、EPG/K 歌/WebHome 仅新增只读或 owner 清理入口、App 仅新增延迟启动调度、Manifest 仅新增 JobService 与引导权限；`DiskCacheCapacityPolicy` 与 LRU evictor 未改动。该证据表明既有播放、seek、预载、重缓冲与选路代码未被改写，但**不能替代**播放中清理的设备实测。
- 2026-09-23：删除边界加固。审计发现 `CacheRetentionManager.enforceFileLimit` 会对调用方传入的任意 `File` 直接 `delete()`，未校验是否位于声明根目录内，违反不变量 8（所有路径必须严格限制在预声明根目录内）。现要求显式传入 `allowedRoot`，越界路径与符号链接一律跳过；新增 `fileLimitNeverDeletesOutsideAllowedRoot` 与 `fileLimitSkipsSymbolicLinksInsideRoot` 测试，缓存纯测试 34/34 通过。
- 2026-09-24：TV 弹窗尺寸与滚动修复。用户反馈 TV 端弹窗高宽过小、中间固定高度列表易用性差。`CacheManagementDialog` 现按屏幕 **90%×90%** 设置窗口并加暗背景；`dialog_cache_management.xml` 去掉中间的固定高度滚动区，改为**整页 NestedScrollView**，所有内容（模块列表、上限、自动清理、保留期、总上限、三级清理、刷新/确定）都在同一滚动流中，遥控器持续下移即可到达。
- 2026-09-24：上述改动首次设备验证时崩溃（`ClassCastException: ViewGroup$LayoutParams cannot be cast to ViewGroup$MarginLayoutParams`），原因是 `applyWindowSize()` 给根视图设置了非 Margin 的 `LayoutParams`。已删除该赋值（布局本身即 `match_parent`），崩溃消除。
- 2026-09-24：TV 设备实测（NX627J / Android 9 / 192.168.50.3:5563，arm64 debug 覆盖安装）。屏幕 1920×1080，弹窗外框实测 `[96,54][1824,1026]` = 1728×972 ≈ 90%×90%，四周留边；内容根为 `android.widget.ScrollView`（`scrollable=true`）覆盖整个内容区；遥控器下移后自动清理、保留期、总上限、三级清理、刷新与确定全部可见且 focusable，无 `FATAL EXCEPTION`。
- 2026-09-24：TV 弹窗第二轮修复（用户反馈四项）。①**高度**：原先只是窗口为 90%，但 MaterialAlertDialog 内部面板仍是 wrap_content，实测可见面板仅 652/1080 px；改为**自绘 Dialog**（窗口即面板），实测面板从 y≈50 延伸到 y≈1030，接近全屏。②**对比度**：模块标题/详情原用硬编码 `#202124`/`#5F6368`，在深色面板上几乎不可读；改用 `android.R.attr.textColorPrimary/Secondary` 主题色。③**焦点高亮**：新增 `selector_cache_button_focus` 焦点环（3dp 纯白描边），所有按钮统一使用；焦点态不加填充，避免前景层压暗文字（第一版用半透明填充会把聚焦按钮文字压灰，已修正）；同时把「确定」从填充样式改为与其它按钮一致的 TonalButton，避免未聚焦按钮看起来像已选中。④**焦点顺序**：默认几何寻焦会让「刷新」向上跳到「总计」而跳过整行清理按钮；现显式声明 `nextFocusUp/Down` 策略行→清理行→底栏，并把末行模块按钮与策略行双向连接。设备实测：`刷新` ↑ → `标准清理`（此前为 `总计`），再 ↑ → `保留期`，逐行推进不再跳行。
- 2026-09-24：TV 深度清理确认去重。用户反馈点击“深度清理”会连续弹出多个确认框。根因是 `confirmDeep()` 的第一个确认框在“确定”后无条件创建第二个内容相同的 `MaterialAlertDialog`，视觉上形成重复弹窗。现改为同一个 `AlertDialog` 内两步确认：第一步「取消 / 继续」说明影响；第二步替换文案为“深度清理不可撤销，确定现在开始？”，按钮变为「取消 / 开始深度清理」，第二次确认后才执行清理。设备实测（NX627J / Android 9 / 192.168.50.3:5563，arm64 leanback debug 覆盖安装，包名 `com.silent.android.webhtv`）：点击“深度清理”后无障碍树只有一组标题/正文/「取消」「继续」；点击“继续”后同一窗口更新为不可撤销文案与「取消」「开始深度清理」，未出现第二个叠加弹窗。
- 2026-09-24：符号链接防护误伤缓存根目录（重大修复）。设备实测发现缓存管理一直显示 `共 无`、16 项警告，清理恒为“删除 0 个文件”，但 `run-as ... du -sk cache` 实际为 65,708 KB。根因是该 Android 9 模拟器上 `/data/user/0` 是指向 `/data/data` 的符号链接，而 `CacheInventory`、`CacheCleanupManager`、`CacheRetentionManager` 都通过“规范化路径 != 绝对路径”判断符号链接，导致缓存根目录的所有子项被误判为符号链接并跳过。现新增 `CachePathSafety.isSymbolicLink()`：只比较最终路径组件（规范化父目录 + 原文件名 vs 规范化文件），既保留真实文件/目录符号链接防护，又不再误伤祖先目录别名。新增 `CachePathSafetyTest` 覆盖祖先符号链接（正常计入）和文件本身符号链接（仍跳过，结果 PARTIAL）两种回归场景；缓存包独立 JUnit **36/36 通过**，双形态 Java 编译通过。设备覆盖安装后复测：总缓存 `59.1 MB / 478.9 MB`，歌词模块 `1 MB · 1 个文件`，MPV HLS `519.8 KB · 2 个文件`，无“部分结果”警告；标准清理实测 `释放 165.3 KB · 删除 8 个文件`，恢复真实统计与删除行为。
- 2026-09-24：自动清理调度设备证据。`dumpsys jobscheduler` 历史包含 `START-P: #u0a58/1128350465 com.silent.android.webhtv/com.fongmi.android.tv.cache.CacheCleanupJobService`，证明持久化 Job 已在设备上真实执行；`cache_mgmt_cleanup_history` 中存在 `"reason":"periodic"` 的 LIGHT 自动清理记录；最近一次手动 STANDARD 记录为 `bytesBefore=48101199 → bytesAfter=47931933`、`deletedFiles=8`、`status=COMPLETED`，与界面「释放 165.3 KB · 删除 8 个文件」一致，证明统计与结果上报链路端到端正确。低空间分支（`CacheAutoCleanupPolicy.isLowSpace` 阈值 `max(512MB, 10%)`、连续 2 次采样生效、播放中恒为 LIGHT）由 `CacheAutoCleanupPolicyTest` 覆盖；本机可用空间 31 GB，无法在不破坏环境的前提下制造真实低空间条件。系统配额已由界面「共 59.1 MB / 系统配额 478.9 MB」证实在读取与展示，上限收敛逻辑由 `CacheTotalLimitPolicyTest` 覆盖。
- 2026-09-24：5563 设备回归（缓存升级后专项）。
  1) **播放/seek/预载**：推送自建 180 秒 H.264+AAC 测试媒体（原有 `ijk-smoke.mp4` 只有单帧、无时长，播放器判 `Source error`，属陈旧测试素材问题，非回归）。实测 `state=3`、`speed=1.0`，位置持续推进；seek 由 70,656 ms 跳至 82,709 ms 后继续播放且缓冲由 108,466 ms 继续增长，证明 seek 与 seek 后预载正常。
  2) **播放中清理不中断**：后台保持播放时打开缓存管理并执行标准清理，清理前 42,953 ms → 清理中 45,953 ms → 清理后 48,957 ms，全程 `state=3`、`speed=1.0`，播放未中断。
  3) **播放器切换不影响其他模块统计**：EXO → MPV 切换前后模块统计完全一致（总计 58.9 MB、歌词 1 MB/1 文件、MPV HLS 519.8 KB/2 文件、JS·Python·Jar 12.7 MB/31 文件、Exo 1 文件）；切换后 MPV 亦正常播放（position 3,971→6,973 ms），验证完已恢复为 EXO。
  4) **备份/恢复**：备份实测生成 `/sdcard/TV/bak-20260924-2028.zip`（998,683 字节，与日志 `create complete size=998683` 一致，toast「备份成功」，临时 zip 已清理）；恢复实测最新备份 `restore complete shared=50 login=0 app=3 warning=`，toast「恢复成功」，临时包已清理。
  5) EPG 与歌词/字幕/K 歌：5563 的直播配置为空（点击「直播」弹出新增直播二维码），点播配置为最小 smoke 源，缺少 EPG 与媒体增强所需源，本机无法验证，保持未通过。
- 2026-09-25：临时文件“使用中跳过”补齐（设计 §9.12）。原实现仅靠 24 小时保留期间接避免误删进行中的 `update.apk`/`pushed-url-*.apk`，缺少设计要求的显式“使用中禁止”。现新增 `CacheTempFilePolicy.isInUse(name, updaterDownloading, apkUrlPushing)`，并在 `CacheCleanupManager.clearTemporaryFiles` 中接入 `Updater.isDownloading()`（`downloading` 改为 `volatile`）与 `ApkUrlPush.isActive()`；使用中的 `update.apk` 与 `pushed-url-*.apk` 一律跳过，其余临时文件仍按 24h + 字节上限处理。新增 `CacheTempFilePolicyTest`（3 例）覆盖进行中保护与无关文件放行；缓存包独立 JUnit **39/39 通过**，双形态 Java 编译通过。设备复测（覆盖安装 leanback debug）：同时放入新写入的 `update.apk` 与 2 天前的 `pushed-stale.apk` 后执行轻度清理，结果 `释放 512 KB · 删除 1 个文件`，`update.apk` 保留、过期包被删除。
- 2026-09-25：歌词与字幕设备实测（5563，沉浸式音频模式已开启）。①**歌词链路**：推送音频源后设备正确识别为音频内容并进入歌词流程，日志依次记录 `qqmusic search mode=lite` → `load source=QQMusic done` → `load source=LRCLIB done` → `load ranked` → `match result=none`，并在无匹配时调用 `refresh empty` + `views clear`（不残留错误字幕）；即多源检索、排序、空结果降级均正常执行。②**字幕**：通过 `/action?do=file` 投递 `/sdcard/Download/Obsession.2025.zh-Hans.srt`（HTTP 200），全屏播放至 00:02:32 时画面实际渲染出中文台词（“好吧，那，那也太尴尬了。”“天哪，我就知道。”），证明字幕加载与渲染链路未被缓存改动影响。K 歌轨道需绑定真实音频 fingerprint 的在线曲目，本次合成音频无对应指纹，未做设备级 K 歌生成/导入实测。
- 2026-09-25：EPG 缓存保留策略设备实测（5563）。在 `cache/epg/` 构造 `today.xml`（当前日期）与 `old.xml`（mtime 回拨 2 天）后执行轻度清理，结果 `清理完成 · 释放 4 bytes · 删除 1 个文件`：`old.xml` 被删除、`today.xml` 保留，符合设计「清理 EPG：过期删除，今天的保留」。该项此前因设备无直播源无法验证自动刷新，本次以缓存策略层实测补齐可验证部分；EPG 自动下载刷新仍需有可用直播源。
- 2026-09-25：完成设计 §23.1 的缓存管理功能开关。`CachePolicyStore.isManagementEnabled()` 读取 `cache_mgmt_enabled`（默认 `true`，缺失即开启），TV `SettingActivity` 与移动端 `SettingFragment` 在关闭时回退到旧实现（`FileUtil.getCacheSize()` 显示、`FileUtil.clearCache()` 一键清理），开启时使用 `CacheCenter` + `CacheManagementDialog`。默认值语义由纯函数 `enabledByDefault(Boolean)` 承载并由 `CachePolicyStoreTest` 覆盖；缓存包独立 JUnit **41/41 通过**，双形态 Java 编译通过。设备实测（5563，覆盖安装 leanback debug）：置 `cache_mgmt_enabled=false` 后设置页显示旧式 `60.3 MB`，点击缓存行不打开新面板、直接全量清理并把文案更新为「无」（`cache` 目录由约 60MB 降至 4KB）；验证后已把开关恢复为 `true`。
- 2026-09-25：低空间触发设备实测（5563，root 可逆构造）。为不破坏设备存储，仅在应用 `cache` 目录挂载 400MB tmpfs（`/data/data/com.silent.android.webhtv/cache`），使 `StatFs` 真实返回 low-space；同时写入 `cache_mgmt_auto_enabled=true`、`cache_mgmt_last_auto_ms=now`、`cache_mgmt_low_space_streak=1` 并放入一个 2 天前的 `pushed-stale.apk`。启动应用后 `dumpsys jobscheduler` 记录 `START-P: #u0a58/1128350465 ... CacheCleanupJobService`，清理历史新增 **`{"mode":"STANDARD","reason":"low-space","status":"COMPLETED","deletedFiles":1}`**，临时文件被删除：证明持久化 Job 在真实低空间下触发，且按策略执行 L2（**未执行 L3**）。验证后已卸载 tmpfs、恢复检测前的偏好文件，并确认应用可正常启动（无 FATAL EXCEPTION）。设备常规环境可用空间 25GB，因此此前无法在不动设备的前提下制造低空间。
- 2026-09-25：EPG 直播源尝试与应用更新缓存契约。
  1) **EPG 自动刷新（已通过）**：本地 HTTP 提供 `live.m3u`（`#EXTM3U tvg-url="http://192.168.50.34:8099/epg.xml"`，频道指向本地 HLS `playlist.m3u8`，含 8 个真实 TS 分片）。设备播放该直播频道时，服务端日志完整记录 `GET /live.m3u 200` → `GET /epg.xml 200` → `GET /hls/playlist.m3u8 200` → 全部 `segNNN.ts 200`（持续重取），且 `PlaybackState state=3`、`buffered=30000ms`、`position` 持续推进。下载的 EPG 实际落盘：`cache/epg/epg.xml`（319 字节），内容为本地提供的 `测试频道` / `缓存管理 EPG 验证节目`，证明直播节目单自动刷新与 EPG 缓存写入闭环均正常。验证后已把 `Config` 恢复为原始 3 行并删除测试 EPG 缓存。
  2) **应用更新缓存契约**：`Updater` 的下载目标为 `Path.cache("update.apk")`（`HttpUpdateTransfer`/`OciUpdateTransfer` 共用同一文件），与 `CacheCleanupManager` 的临时文件规则一致；本轮已实测清理期间该文件被保护（新写入的 `update.apk` 保留、过期 `pushed-stale.apk` 删除）。应用内“检查更新→下载→安装”完整闭环还需线上 HTTPS 发布包与安装器交互确认。
- 2026-09-25：K 歌与歌词模块缓存隔离实测（5563）。在 `cache/karaoke_tracks/` 放入 `verify.generated.txt`（K 歌轨道命名）与 `verify-track.bin`（非轨道文件），并在 `cache/lyrics/` 放入 `verify-lyric.bin`。缓存管理页正确统计「K 歌轨道 256 KB · 1 个文件」与「歌词与选源结果 128 KB · 1 个文件」；点击 K 歌行的「清理」后确认框明确显示「清理 K 歌轨道？」，执行结果 `释放 6 bytes · 删除 1 个文件`：只删除 `verify.generated.txt`，保留非轨道文件与歌词文件。证明模块级统计与清理按模块隔离、互不误删。
- 2026-09-25：移动端 flavor 设备验证（5563，覆盖安装 `mobileArm64_v8a` debug，包名 `com.silent.android.webhtv`）。设置页移动端布局显示「缓存管理 25.7 MB / 478.9 MB」，点击后打开缓存管理弹窗并显示真实扫描结果（`共 25.7 MB / 系统配额 478.9 MB`、`扫描于 14:52:03`）；滚动后「轻度清理 / 标准清理 / 深度清理 / 刷新 / 确定」均可见可点；执行轻度清理弹出确认框并返回 `清理完成 · 释放 无 · 删除 0 个文件`（无过期项，符合预期），全程无 `FATAL EXCEPTION`。验证后已重新覆盖安装 leanback flavor，设备恢复为 TV 形态。
- 2026-09-25：系统 quota 生效证据（5563）。设备通过 `StorageManager.getCacheQuotaBytes` 读到缓存配额 **478.9 MB**，并在设置页与缓存管理页同时展示「共 X / 系统配额 478.9 MB」。设备侧把总缓存上限设为 **1 GB**（界面 `总计：1 GB`、偏好 `cache_mgmt_total_limit_bytes=1073741824`），超过配额；有效上限的收敛由 `CacheTotalLimitPolicy.effectiveLimit(userLimit, quota)` 负责，`CacheTotalLimitPolicyTest.systemQuotaClampsUserLimit` 断言 `min(1024,512)=512`，因此该设备实际有效上限为 `min(1 GB, 478.9 MB)=478.9 MB`。验证后已把总上限还原为「不限」（`cache_mgmt_total_limit_bytes=0`）。三个强制边界场景（播放中清理、低空间触发、系统 quota）至此均有实测或实测+单测证据。
- 2026-09-25：应用更新可验证范围与剩余限制。审查确认 `Updater` 的下载目标是 `Path.cache("update.apk")`，`HttpUpdateTransfer`/`OciUpdateTransfer` 写入同一文件，且取消路径会清理该文件；与本计划相关的缓存契约是「下载/安装进行中不得被缓存清理删除」，已由 `CacheTempFilePolicy` + `Updater.isDownloading()`（`downloading` 为 `volatile`）实现并设备实测（新写入的 `update.apk` 在轻度清理后保留，2 天前的 `pushed-stale.apk` 被删除）。完整更新闭环在本环境无法验证，原因有三：①`ApkUrlPolicy` 明确拒绝私网与 HTTP，无法用本机自建发布源；②没有真实线上 HTTPS 发布包与版本号；③安装需系统安装器人工确认（`FileUtil.openFile` 走 `ACTION_VIEW` + FileProvider）。**实测确认发布端本身不可用（非本机网络故障）**：`Updater` 的检查路径是 `Github.getReleasesApi()/latest`。设备与宿主机分别请求 `https://api.github.com/repos/fish2018/webhtv/releases/latest` 均返回 `403`，响应体为 `API rate limit exceeded`（匿名调用被限流）；发布镜像 `https://cnb.cool/fish2035/webhtv-release/-/git/raw/main/apk/tv-arm64_v8a.json` 返回 `403 {"errcode":10003,"errmsg":"Repo fish2035/webhtv-release has been frozen."}`（仓库已冻结）。宿主机同样失败，说明是本项目发布端/配额问题而非设备网络问题，因此无法在本环境完成「检查更新」真实流程。按 §24 第 5 条如实标注为「缓存契约已通过、完整闭环受外部网络与安装器交互限制」，不虚报为完成。
- 2026-09-25：清理结果改为通知，不再弹确认框（用户反馈）。`renderResult` 原实现用 `MaterialAlertDialogBuilder` 并复用确认标题 `cache_cleanup_confirm_title`，导致清理**已经完成后**还要再按一次「确定」——职责错误（确认框用于执行前，结果不应再要求确认）。现改为：①用 `Notify.show(text)` 发被动通知（Toast）；②同时把结果文本写入面板自身状态行并在随后刷新中保留，信息不会随通知消失而丢失；③不再创建任何结果弹窗。设备实测（5563，覆盖安装 leanback debug）：构造 2 天前的 `pushed-stale.apk` 后执行轻度清理，清理瞬间截图显示 Toast「清理完成 释放 512 KB · 删除 1 个文件」且**无任何弹窗**；4 秒后 Toast 自然消失、界面无「确定/取消」按钮；无障碍树读取 `id/status` 为「清理完成\n释放 512 KB · 删除 1 个文件」，`id/summary` 刷新为「共 19.3 MB / 系统配额 478.9 MB」，证明结果只以通知 + 状态行呈现。回归确认深度清理**执行前**的二次确认未被影响（点击「深度清理」仍为第一步「取消/继续」）。
- 2026-09-26：补齐设计 §15.2「进度对话框处理返回键：运行中只请求取消，不直接关闭」。原实现既没有拦截 BACK，也没有禁用点击外部关闭：清理进行中按返回键或点弹窗外部会直接关闭面板，而清理线程继续在后台运行——用户既看不到进度也拿不到结果；面板底部「确定/关闭」按钮有同样问题。现统一为：①`Dialog.setOnKeyListener` 拦截 BACK，`CacheCleanupManager.isRunning()` 为真时调用 `cancel()` 并消费事件（面板保持打开），未运行时返回键仍按层级正常关闭；②关闭按钮在运行中同样只请求取消；③清理开始时 `setCanceledOnTouchOutside(false)`，结果回调时恢复，避免清理被“点外部”静默丢弃。设备实测（5563，覆盖安装 leanback debug，预置 30000~120000 个 2 天前的 `.tmp` 使清理持续数秒）：清理中按 BACK 面板不关闭、任务取消且状态行显示「缓存清理已取消」；清理中点击底部关闭按钮同样保持面板打开（无障碍树仍可读到「缓存管理」，无任何弹窗按钮）；空闲时按 BACK 正常关闭回到设置页。测试文件已全部清理。
- 2026-09-26：补齐设计 §15.1「清理确认对话框默认焦点在取消」。原先 `confirm()`/`confirmModule()`/`confirmDeep()` 都只 `show()`，遥控器打开确认框后**没有任何按钮获得焦点**（无障碍树显示 focused 为空），此时按确认键无反应，用户必须先按方向键才能操作；且默认落在操作按钮上有误删风险。现在统一改为构造 `AlertDialog` 后注册 `setOnShowListener`，在 `post()` 中把焦点显式放到「取消」并标记 `focusable/focusableInTouchMode`（在 `onShow` 里直接 `requestFocus()` 会被随后的布局顶掉）。深度清理第二步（文案切换为“不可撤销”）同样重新把焦点放回「取消」。设备实测（5563，覆盖安装 leanback debug）：打开轻度/标准清理确认框后无障碍树 `focused` 立即为 `android.widget.Button '取消'`，**单击一次确认键即执行「取消」**（预置的 4 个过期 `tmp` 文件清理前后数量均为 4，确认框关闭且未清理）；深度清理第一步同样默认聚焦「取消」。测试文件已全部清理。
- 2026-09-26：补齐设计 §11.3 的版本化迁移。`CachePolicyStore` 新增 `cache_mgmt_schema_version` 标记与幂等 `migrate()`：在 `CacheCenter` 初始化（UI/清单入口）和 `CacheScheduler.start()`（自动维护入口）调用，且 `getLimit()` 读取前兜底；迁移只把本功能拥有的模块上限、保留期和总上限规范化为安全范围，现有 key 不删除，播放器的 `play_cache` / `preload_size` 等设置不改动，未知或更高版本保持原样，失败时保留旧值并留待下次启动重试。`CachePolicyStoreTest` 新增 fresh/current/newer 三种版本判定，独立 JUnit **3/3 通过**；`:app:compileMobileArm64_v8aDebugJavaWithJavac` 与 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 均通过。
- 2026-09-27：修复清理确认框关闭后焦点随机跳转（用户反馈）。原实现只保证确认框打开时默认聚焦「取消」，但关闭后让系统按几何寻焦重新选择，遥控器焦点可能离开触发按钮。现在为模块「清理」、三级清理和深度清理确认框记录触发 `View`，在 `OnDismiss` 后 `post()` 回原按钮；清理启动时按钮保持 focusable、仅关闭 clickable 并降低透明度，避免确认框消失瞬间原按钮不可聚焦而再次随机寻焦。模块列表刷新还会按 `ModuleFocusTag(id, limitButton)` 恢复原「清理/上限」按钮。设备实测（5563，覆盖安装 leanback debug）：在「Exo 播放缓存」的「清理」按钮上打开确认框并点「确定」，关闭约 0.4 秒后无障碍树 `focused=true` 仍为该行原「清理」按钮（坐标 `[1344,673]-[1568,761]`），未跳到相邻模块或策略行。`:app:compileMobileArm64_v8aDebugJavaWithJavac` 与 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 均通过。
- 2026-09-27：修复「遗留/孤立文件」清理失败（用户反馈，截图证据 `QQ20260927-101816.png`：模块报告 17 MB · 93 个文件，点击「清理」返回「清理完成 释放 无 · 删除 0 个文件」）。两项根因：
  1) **统计与清理范围互斥**：`LEGACY_FILES` 统计的是 `CacheRoot.orphanTree(cache, ...)`（“除已登记根目录外的全部”），而 `CacheCleanupManager` 只清理 `clearAgedTree(cache/"restore-legacy")`，且 `restore-legacy` 同时还在 `MANAGED_ROOTS` 里被统计排除 —— 该模块报告的任何字节都不可能被它自己删除。
  2) **把活跃缓存谎报为遗留**：5563 实测该模块的 17 MB / 93 文件实际是 `plugin-preheat`（82 文件 3.9 MB，内容是 `//@name...` 的插件脚本预热点）与 `webhtv-debug-log.txt{,.1,.2,.3,.pinned}` 轮转日志（约 13.4 MB），两者都是当前应用仍在使用的活跃数据；而设计 §9.13 明确要求「版本化规则表」「不根据文件名模糊匹配未知文件」。
  修复：新增版本化规则表 `CacheLegacyRules`（`TABLE_VERSION = 2`，规则 `restore-legacy`、`subtitle_asset`），统计根与清理路径均从 `CacheLegacyRules.rules()` 派生，保证“报告即删除”；`restore-legacy` 从 `MANAGED_ROOTS` 移除（它过去同时被排除与清理，是 incoherence 的来源）；新增 `diagnostic.logs`（owner 清理 + 手动可清 + 永不自动/分级清理）与 `unclassified.cache`（只统计不清理）两个模块；`CacheRoot` 增加前后缀过滤（`prefixedFiles`/`orphanTree` 前缀重载），使 `webhtv-debug-log.txt.1` 这类派生名可被整体寻址；`CachePolicyEngine` 的 L3 显式排除两个新模块（设计 §12.1 的 L3 = L2 + 播放缓存），UI 的“清理」按钮是否禁用改由注册表 `allowManualCleanup` 决定（不再硬编码 `PLUGIN_SCRIPTS`），模块文案改为「遗留路径 / 诊断日志 / 未归类缓存」。
  验证：缓存包独立 JUnit **57/57 通过**（新增 `CacheLegacyRulesTest` 6 例、`CacheRootPrefixTest` 4 例，并扩展 `CacheInventoryTest`/`CachePolicyEngineTest`），`CacheModuleRegistryTest.productionRegistryPassesValidation` 仍通过（前缀过滤已纳入根定义去重）；`:app:compileMobileArm64_v8aDebugJavaWithJavac` 与 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 均通过。
  设备实测（NX627J / Android 9 / 192.168.50.3:5563，leanback arm64 debug 覆盖安装，无卸载）：为匹配设备上既有包名，临时把 `applicationId` 改为 `com.silent.android.webhtv` 构建，安装后已恢复 `app/build.gradle` 并校对 md5 一致（`c6ad2a8176046335686bac30be2c9c45`）、`git status` 无此文件改动。
  ①**不再谎报**：同一设备上，修复前该行显示 `遗留/孤立文件 90.4% — 17 MB · 93 个文件`；修复后拆分为 `诊断日志 69.3% — 13.3 MB · 5 个文件`、`未归类缓存 19.5% — 3.7 MB · 87 个文件`（按钮为「由模块管理」，不可直接清理）、`遗留路径 <0.1% — 20 bytes · 2 个文件`（正是本次放入的 2 个测试遗留文件），而 `plugin-preheat`（82 文件）与 debug log 已不再被当作遗留。
  ②**报告即删除（核心回归）**：点击「遗留路径」行的「清理」，确认框文案为「清理 遗留路径？」且默认焦点在「取消」；确认后返回「清理完成 · 释放 20 bytes · 删除 2 个文件」，与统计值完全一致；随后 `run-as ... ls` 确认 `cache/restore-legacy/interrupted-restore.bin` 与 `cache/subtitle_asset/old-asset.bin` 两个目录均已不存在，且 `cache_mgmt_cleanup_history` 新增 `bytesBefore=20 → bytesAfter=0, deletedFiles=2, mode=MODULE, status=COMPLETED`。修复前同样的操作返回「删除 0 个文件」。
  ③**诊断日志模块可用**：点击「诊断日志」行「清理」，返回「清理完成 · 释放 13.3 MB · 删除 3 个文件」，journal 记录 `bytesBefore=13987893 → bytesAfter=4048, deletedFiles=3`；磁盘上 4 个轮转分段（`.1/.2/.3` ≥3.4 MB）被删、活动段与 `.pinned` 由 owner 的写入器立即重建（各自 2024 bytes），未破坏日志写入。
  ④**分级/自动清理不误伤（本次改动的关键不变式）**：执行「轻度清理」（L1）后返回「删除 0 个文件」，日志族与 82 个 `plugin-preheat` 文件全部保留；执行「深度清理」（L3，两步确认后）journal 记录 `bytesBefore=2261255 → bytesAfter=1901071, deletedFiles=16, warnings=["not_allowed"]`（`not_allowed` 来自按设计不可直接清理的插件脚本模块），日志族与 `plugin-preheat` 仍全部保留。证明新拆出的两个模块确实被排除在 L1/L3 与自动清理之外。
  全程 `pid` 6974 未重启、无 `FATAL EXCEPTION`。
- 2026-09-28：修复「清理完成后设置页缓存数值不刷新」（用户反馈，截图证据 `QQ20260927-203536.png`：设置页「缓存管理 12.2 MB / 486.5 MB」，描述为“清理完成后要及时刷新这里的数据，现在没刷新需要切换到其他页面后再切换设置也才能看到清理后的正确的值”）。
  根因：设置页那一行只在自己的视图创建时读一次缓存清单 —— leanback 是 `SettingActivity.initView()`（`onCreate`），移动端是 `SettingFragment.onHiddenChanged(false)`（翻页/切 tab 才会回调）。清理是被 `CacheManagementDialog` 发起的，它只更新弹窗自己的 `summary/status`；**没有任何东西告诉设置页“缓存已经变了”**，所以该行会一直保留清理前的数值，直到用户离开再回到设置页触发一次新的视图创建/翻页回调 —— 这正是用户描述的“切到其他页面再切回来才能看到正确的值”。次要因素：`CacheCenter` 的清单快照有 3 秒 TTL，即使有人重新读取也可能命中旧快照。
  修复（`Exo`/`MPV` 等播放器代码与清理策略均未改动）：
  1) 新增 `RefreshEvent.Type.CACHE` 与 `RefreshEvent.cache()`，作为“缓存内容已变化”的公共信号。
  2) `CacheCenter` 新增 `invalidate()`（丢掉清单快照，字段是 `volatile`，任意线程可调）、`publishChanged()`（发 `CACHE` 事件）；`notifyChanged()` = 两者组合，供其他缓存变更点复用。
  3) `CacheCleanupManager.run()` 结束时按 `invalidate → 结果回调 → publishChanged` 的顺序投递到主线程，保证结果摘要先渲染、设置页随后按新快照刷新；`applyConfiguredLimits()`（上限/TTL 淘汰）同样 `notifyChanged()`。
  4) 两个设置入口订阅 `CACHE` 并调用既有的 `setCacheText()`：leanback `SettingActivity.onRefreshEvent()`、移动端 `SettingFragment.onRefreshEvent()`（移动端同时把 `mBinding` 在 `onDestroyView` 置空，避免事件打到已销毁的视图上）。
  验证：缓存包独立 JUnit **57/57 通过**（本轮未改纯函数，属回归复核）；`:app:compileMobileArm64_v8aDebugJavaWithJavac` 与 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 均 BUILD SUCCESSFUL。设备实测（NX627J / Android 9 / 192.168.50.3:5563，arm64 debug 覆盖安装，无卸载；为匹配既有包名临时把 `applicationId` 改为 `com.silent.android.webhtv`，验证后 `app/build.gradle` 已按 md5 `c6ad2a8176046335686bac30be2c9c45` 还原为字节相同）：
  ①**leanback**：预置过期临时包 `pushed-verify9.apk`（9 MB）与过期遗留路径 `restore-legacy/verify-legacy-stale.bin`（8 MB）。清理前设置页该行 `22.1 MB / 486.5 MB`，弹窗可信值 `共 31.2 MB`；执行「轻度清理」后 journal 记录 `bytesBefore=17825792 → bytesAfter=0, deletedFiles=2, status=COMPLETED`（与两个夹具字节数完全一致）；**在同一个 `ActivityRecord{... t31}` / 同一个 `pid 20949` 下**（`logcat` 无任何新的 `START/Displayed ... SettingActivity`，且弹窗是宿主 Activity 的 `DialogFragment`、全程只按了一次 BACK 关闭弹窗、没有翻页）该行自动变为 `14.2 MB / 486.5 MB`，与随后重新打开弹窗得到的 `共 14.2 MB` 一致。
  ②**移动端 flavor**：覆盖安装 `mobileArm64_v8a` debug 后预置 `pushed-mobile9.apk`（9 MB）。清理前该行 `23.5 MB / 486.5 MB` 且弹窗 `共 23.5 MB`（基线可信）；「轻度清理」journal `bytesBefore=9437184 → bytesAfter=0, deletedFiles=1, COMPLETED`；**在同一个 `ActivityRecord{... t32}` / 同一个 `pid 22345`、始终停留在“设置”tab（无 tab 切换、无 Activity 重建）**下该行变为 `14.5 MB / 486.5 MB`（= 23.5 − 9.0），与重新打开弹窗的 `共 14.5 MB` 一致。验证后重新覆盖安装 leanback，设备恢复 TV 形态；测试夹具与临时创建的 `cache/restore-legacy` 目录均已删除，`cache` 目录条目恢复为验证前清单。
  ③**未修复的相邻缺陷（已确认，另行跟进，不在本次改动范围）**：冷启动后的**首次**清单扫描可能触发 `CacheInventory` 的每模块 2 秒预算（`MODULE_TIMEOUT_NS`），被截断的模块会整块丢失字节，因此该次 `totalBytes()` 偏小，而设置页那一行不像弹窗那样显示「部分结果 · N 项警告」。实测（同上设备）：冷启动首扫 `warnings=1, duration=4875ms/2341ms`，该行 `13.4 MB`，而同一时刻热扫描的弹窗为 `共 37.4 MB`（按磁盘逐项核算：临时文件 24 MB + 图片 6.4 MB + 未归类 3.7 MB + JS 1.8 MB + 诊断 1.4 MB ≈ 37 MB，弹窗值正确、该行偏小）；把夹具换成 `restore-legacy`（8 MB）后复现同样模式（冷扫丢该模块 8 MB，热扫可见 `遗留路径 8 MB · 1 个文件`）。该问题与清理后的刷新链路无关（清理后的刷新发生在进程内第二次扫描，实测 `warnings=0`、`52–54ms`），因此本次不修改扫描预算；如需跟进，属于独立需求（放大预算或让设置页暴露 partial 状态）。
- 2026-10-09：修复「临时文件清理不了」并补回「长按缓存管理按钮一键清理全部」（用户反馈）。两项改动的根因与决策：
  ①**临时文件根因**：该模块的显式入口（模块行内「清理」按钮）走 `CacheCleanupMode.MODULE`，但 `clearTemporaryFiles` 仍套用给后台/分级清理准备的 24 小时保留期（`TEMP_RETENTION_MS`），于是行内报告「N 个文件」、点下去却是「释放 无 · 删除 0 个文件」，文件仍在磁盘上。现新增 `explicitRequest(mode)`（`MODULE` 或 `FULL`）作为唯一判定，并把歌词/K 歌/EPG/插件脚本/遗留路径一并收敛到它；进行中的 `update.apk` / `pushed-url-*` 仍由 `CacheTempFilePolicy.isInUse` 保护。`clearTemporaryFiles` 同时改为注入 `(cache, retentionMs, limitBytes, updaterDownloading, apkUrlPushing, now)`，使判定可脱离 Android 运行时验证。
  ②**长按一键全清**：新增 `CacheCleanupMode.FULL`，`CachePolicyEngine.modules(FULL)` 覆盖全部模块（含分级清理刻意不碰的 `DIAGNOSTIC_LOGS` 与 `UNCLASSIFIED`）；入口是 `CacheManagementDialog.cleanEverything()`（`reason=shortcut`，长按后直接执行、只以 Toast 报结果，不打开面板），执行体为 `CacheCleanupManager` 内的 `cleanEverything(plan, progress)`，先按 owner 逐个清理（Exo `SimpleCache`、Glide 磁盘缓存、滚动诊断日志必须由 owner 释放，不能直接从打开的 journal/句柄下拉走文件），再 `sweepResidual()` 清掉没有模块认领的残留、插件脚本目录内容与未归类缓存。整轮只保留两个豁免：**正在进行中的传输**（`CacheTempFilePolicy.isInUse`）与**必须等播放结束的播放缓存根**（`directCleanupStatus == DEFERRED` 时把该模块根放入保留集合）；另有 `mpv-playback-recovery.*` 与符号链接一律跳过。入口接在设置页缓存行的**长按**上（leanback `SettingActivity`、移动端 `SettingFragment`）。**这是 §24 第 6 条明确允许的兼容入口**（拆分前的「一键全清」）；单击仍然只打开分级面板，因此长按不是唯一入口（满足 §15.2）。刻意不加二次确认：该入口的语义就是拆分前的单击全清，手势本身即显式动作；若后续要求确认，只需复用既有 `confirm(...)` 流程包一层。**（本版首实现会在长按后打开面板；该点当日经用户指正并改正，见本条 ⑦。）**
  ③**记录在案的行为权衡**：全量清理会连同「空闲时已由 owner 清空并重建」的模块目录（典型是 `cache/exo`）一起扫掉，与拆分前 `Path.clear(Path.cache())` 的效果一致；Exo 侧 `CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR`（`MediaSourceFactory.java:300`）保证此刻缓存写入失败只降级为「本会话不再缓存」，不中断播放。
  验证：缓存包单元测试 **69/69 通过**（新增 `CacheFullCleanupTest` 5 例：显式请求删除新建临时包、后台保留期仍保留新包、进行中传输豁免、残留扫描删除无主残留与闲置插件脚本、保留被保留树/传输/恢复文件；`CachePolicyEngineTest` 新增 `fullPlanCoversEveryModuleExactlyOnce`）；`:app:compileMobileArm64_v8aDebugJavaWithJavac` 与 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 均通过。
  设备实测（NX627J / Android 9 / `192.168.50.3:5563`，leanback arm64 debug **覆盖安装** `com.silent.android.webhtv`；安装前先以 `apksigner` 校验证书 SHA-256 `95e4b2e7…7e4d` 与既有包及本机 debug keystore 一致，故按约定直接 `install -r` 不卸载，未触及同机 `com.fongmi.android.tv`。为匹配既有测试包名临时把 `applicationId` 改为 `com.silent.android.webhtv` 构建，验证后 `app/build.gradle` 已按 `git hash-object`/`sha256` 双指纹还原为字节相同。设备 `versionText` 回显 `5.6.0-202610091450` 与本机构建时间一致。）：
  ①**临时文件（用户报告的缺陷）**：预置新建 `cache/webhtv-sync-test1.zip`（3 MB，mtime 为当下），面板「临时文件」行如实报告 `3 MB · 1 个文件`；按下该行「清理」并确认后，面板状态行显示 **`清理完成 释放 3 MB · 删除 1 个文件`**，总量 `共 9.6 MB → 共 6.6 MB`；`run-as` 复核该 zip 已被删除，同时 `js/zz-test-idle.js` 与 `unknown-probe.bin` 原样保留（模块隔离未被破坏）。修复前该按钮会返回「释放 无 · 删除 0 个文件」并把文件留在磁盘上。
  ②**长按一键全清（新功能）**：在设置页缓存行上只发一次长按手势（`input swipe x y x y 1600`，不预先点击）后，面板自动打开并立即执行全量清理，状态行 **`清理完成 释放 6.6 MB · 删除 119 个文件`**，总量变为 **`共 无`**、全部模块归零；`run-as` 复核 `cache` 目录已**完全为空**（任何模块都没认领的遗留、插件脚本目录内容与未归类缓存一并清除），与拆分前 `FileUtil.clearCache()`（`Path.clear(Path.cache())`）的效果一致。
  ③**编辑流程与刷新**：确认框仍默认聚焦「取消」（§15.1 未回归）；全量清理结束后**无需离开设置页**，缓存行文案即由 `9.6 MB / 702 MB` 自动刷新为 `无 / 702 MB`；`清理完成` 不含「跳过 N 个文件」，即 `CacheCleanupStatus.COMPLETED`。
  ④**journal 持久化证据**（`shared_prefs/com.silent.android.webhtv_preferences.xml` 的 `cache_mgmt_cleanup_history`，机器可读）：`{"mode":"MODULE","bytesBefore":3145728,"deletedFiles":1,"status":"COMPLETED","warnings":[]}`（= 3 MB 临时包，即 ①）与 `{"mode":"FULL","bytesBefore":6911351,"deletedFiles":119,"durationMs":337,"status":"COMPLETED","warnings":[]}`（= 6.6 MB，即 ②）；`FULL` 为本轮首次出现在历史中。（记录时按可读性压缩了字段顺序并省略了 `skippedFiles`；`CacheCleanupRecord` 的 10 个分量都会被 Gson 写入，即 `startedAtMs, durationMs, reason, mode, status, bytesBefore, bytesAfter, deletedFiles, skippedFiles, warnings`。）
  ⑤**全清副作用**：全清会连 `cache/data`、`chaquopy`、`WebView`、`plugin-preheat` 与 `tv.lck`/`following.lck` 一并清除（这些都能被 `sweepResidual` 扫到，因为它们不在 `MANAGED_ROOTS`、也不是受保护名）。这与拆分前行为一致，但必须证明不破坏运行：清理后 `am force-stop` + 冷启动复测，进程正常存活、无 `FATAL EXCEPTION`/`ANR`，应用自行重建了 `chaquopy`、`proc_auxv`、`tv.lck`、`webhtv-debug-log.txt` 等条目。`tv.lck`/`following.lck` 在仓库中没有任何创建者（仅被 `LoginStateSync` 排除出同步快照），属外部运行时的瞬态锁文件。
  ⑥**未覆盖**：移动端 flavor 与播放中长按本次未实测（设备当前为 leanback 形态）；移动端入口与 TV 共用同一 `cleanEverything()` 与同一执行体，差异仅在 `Fragment`/`FragmentActivity` 入口包装。
  ⑦**修正（用户指正，同日）：长按一键全清不再打开面板**。用户指出「长按一键清理后就不用再弹出缓存管理界面了吧」——指正成立。该入口复刻的是缓存管理拆分**之前**的单击全清，而那个入口从不打开任何界面：按住键本身就是那个显式动作。原实现「打开面板再清理」把一键动作变成了「先开界面、再看着它跑」，还把「取消」按钮摆到用户刚刚用长按确认过的动作前面。现改为：`CacheManagementDialog.cleanEverything()` 直接执行 `CachePolicyEngine.plan(CacheCleanupMode.FULL)`（`reason=shortcut`），结果只以 `Notify.show`（Toast）呈现；`showFullCleanup` 三个重载、随 `Bundle` 参数启动的 `FULL` 路径、`ARG_CLEANUP_MODE`/`shortcutStarted`/`isFullCleanupShortcut` 全部删除。同时把结果文案抽成静态 `describe(result)`，面板内清理与长按清理共用，不会再出现两份格式。为让遥控器长按有即时反馈（否则清理期间数秒无任何可见变化，且重复长按会被 `isRunning()` 静默吞掉），长按后立即提示新增串 `cache_cleanup_full_started`（zh-rCN「正在清理全部缓存…」/ zh-rTW「正在清理全部暫存…」/ en「Cleaning all cache…」三语齐备）；注：本次实测清理仅需 200 ms，该提示随即被结果 Toast 取代，因此未单独抓屏留存——它的价值在缓存较大、清理持续数秒的场景（否则那段时间界面无任何变化，重复长按又会被 `isRunning()` 静默吞掉）。代价：长按路径不再有进度行与「取消」按钮（与拆分前一致）；面板内清理仍完整保留进度与取消。设置页数值刷新不依赖面板——`CacheCleanupManager.run()` 结束时的 `publishChanged` 已足够。
  验证（同上设备，`versionText=5.6.0-202610091513`）：预置 `cache/webhtv-sync-np.zip`（2 MB）、`cache/js/zz-idle-np.js`（64 KB）、`cache/stray-np.bin`（256 KB），设置页该行 `2.3 MB / 702 MB`。**①长按不弹面板**：只发一次长按（`input swipe 496 879 496 879 1600`，不预先点击）后 `dumpsys activity activities` 的 `mResumedActivity` 仍是 `SettingActivity`，无障碍树中不存在面板的任何节点（无「缓存管理」标题、无 `cleanupLight` 等按钮）。**②结果只以通知呈现**：长按后 1 秒抓屏（1920×1080 PNG）同时可见设置页仍在前台、缓存行已自动刷新为 `无 / 702 MB`、Toast 为「清理完成 / 释放 2.3 MB · 删除 7 个文件」。**③journal 机器可读证据**（`cache_mgmt_cleanup_history` 末条）：`{"mode":"FULL","reason":"shortcut","bytesBefore":2437024,"bytesAfter":0,"deletedFiles":7,"status":"COMPLETED","warnings":[],"durationMs":200}`，与 Toast 的 2.3 MB / 7 个文件完全一致（同上，字段按可读性压缩）。**④无回归**：短按（`tap` + 确认键）仍正常打开面板（`title=缓存管理`、`summary=共 无 / 系统配额 702 MB`、各行「清理/上限」按钮齐全）；`logcat` 无 `FATAL EXCEPTION`/`ANR`。**⑤未覆盖**：移动端 flavor 与「播放中长按」仍未实测（原因同 ⑥）。
- 2026-10-09（评审轮，`cache-full-cleanup-review`）：逐文件评审本分支 3 个已提交未推送提交（`f9f47c24ae`/`92f1844342`/`f0da126a2c`），确认**无功能缺陷**，修复 2 项真问题并补 2 例回归测试：
  ①**缺陷回归锁缺失（真问题）**：`CacheFullCleanupTest` 的文件头声称锁定「临时文件清理无效」缺陷，但两条用例把保留期写死（`0L` / `DAY_MS`），只调用 `clearTemporaryFiles`，从不经过产线判定——把 `explicitRequest` 判定删掉、让行内按钮重新套用 `TEMP_RETENTION_MS`，69 例仍会全绿，用户报的缺陷可以原样复发而不被发现。现把保留期判定收敛成单一表达式 `CacheCleanupManager.explicitRetention(mode, backgroundRetentionMs)`（`explicitRequest` 由 `private` 改为包内可见），`PLUGIN_SCRIPTS`/`TEMP_FILES`/`LEGACY_FILES` 三处共用；两条临时文件用例改为向该表达式取窗口（不再自带字面量），并新增 `onlyExplicitRequestsIgnoreTheBackgroundRetentionWindow`（`MODULE`/`FULL` = 显式请求，`LIGHT`/`STANDARD`/`DEEP` 保留后台窗口）与 `explicitRequestStillOnlyClaimsTheReportedFamily`（显式请求只删注册表认领的那一族，`notes.txt`/`attachment.zip.part` 保留）。
  ②**面板/Toast 语言路径回退（真问题）**：上一提交把结果文案抽成静态 `describe()` 时改用 `ResUtil.getString`，而 `ResUtil` 走 `App.get().getResources()`——应用内语言是通过 `Setting.wrapLanguage()` 挂在 Activity 上的，`App` 资源不跟随，于是「应用内选繁體、系统为简体」时面板状态行会退回简体；`Notify.show(String)` 同样走 `App` 资源，长按结果 Toast 在繁体设备上也会显示简体文案。现改为 `describe(result, resources)` 由调用方提供资源：面板传自己的 `getResources()`（回到该提交之前的行为），长按路径传 `Setting.wrapLanguage(App.get()).getResources()`（与设置页同一套语言包装，`Notify` 的 Toast 惯例未改动）。`ResUtil` 在本文件仍用于尺寸换算。
  评审确认**不是问题**的项（附残留风险）：①全清会连同 owner 刚重建的 `cache/exo`、以及缓存根下任何无主条目一并删除——这与拆分前 `FileUtil.clearCache()`（`Path.clear(Path.cache())`，连 `cache` 目录本身一起删）语义一致，也正是用户②要的效果；Exo 侧后果由 `FLAG_IGNORE_CACHE_ON_ERROR`（`MediaSourceFactory.java:300`）降级为「本会话不再缓存」，实测全清后冷启动无崩溃且缓存自动重建。**残留风险（本次不修改，需先征得用户同意才能改变「全清等于 cache 目录清空」的语义）**：`sweepResidual` 只豁免「正在进行的 `update.apk`/`pushed-url-*`」（`CacheTempFilePolicy.isInUse`）与延后的播放缓存根，而 AOSP 把 `java.io.tmpdir` 指向应用 `getCacheDir()`（`ActivityThread.setupGraphicsSupport`），NanoHTTPD 的 `DefaultTempFileManager` 因此把局域网推送的 APK 上传分片落在 `cache/NanoHTTPD-*`——正在上传时长按会把分片一起删掉（拆分前同样如此）；如需收口，应作为独立需求（例如给「本地服务器有活跃上传」加一个豁免）。②`PLUGIN_SCRIPTS` 的 `explicitRetention` 在当前门禁下不可达（该模块 `directCleanupStatus` 恒为 `NOT_ALLOWED`，由残留扫描代劳），保留是为了让「显式请求不带窗口」只有一处；③TV 遥控器长按不是触摸专用：AOSP `View.onKeyDown` 对 confirm key（`KEYCODE_DPAD_CENTER`/`ENTER`）在 `clickable || longClickable` 时即调 `checkForLongClick`，`onKeyUp` 在 `mHasPerformedLongPress` 为真时不再执行单击（`$ANDROID_SDK/sources/android-37.0/android/view/View.java`）。
  未覆盖（沿用上条 ⑥，另加一项）：移动端 flavor 与「播放中长按」仍未实测；**同一进程内「全清后立即播放」的 Exo 缓存写回路径未实测**——全清会把 owner 刚重建（`clearCacheIfIdle()` 末尾的 `getCache()`）的 `cache/exo` 一并删掉，而 `MediaSourceFactory.cache` 仍持有该实例，缓存写入要等下一次 owner 重建或进程重启才恢复正常；该行为与拆分前一致，但尚无同进程实测证据。
  验证：缓存包单测 **71/71 通过**（`CacheFullCleanupTest` 由 5 例增至 7 例）；`:app:compileMobileArm64_v8aDebugJavaWithJavac` 与 `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` 均通过。
- 设计第 24 节第 4 条边界场景现状：①**播放中执行清理且播放不中断** —— 5563 已实测通过（清理前后 `state=3`、`speed=1.0`，位置持续推进）。②**低空间触发自动清理** —— 调度执行链已设备验证（持久化 Job 真实运行、`reason=periodic` 自动清理入库），低空间判定阈值与「连续 2 次采样」由 `CacheAutoCleanupPolicyTest` 覆盖；真实低空间条件受限于 31 GB 可用空间未做破坏性模拟。③**系统 quota 生效** —— 设备界面实测读取并展示「系统配额 478.9 MB」，有效上限收敛由 `CacheTotalLimitPolicyTest` 覆盖。

当前设置页在移动端和 TV 端的“缓存”入口都只有两个行为：

1. 调用 `FileUtil.getCacheSize()` 统计整个 `Path.cache()` 的递归文件大小。
2. 调用 `FileUtil.clearCache()` 删除 `Path.cache()` 下的全部文件。

代码位置：

- `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingFragment.java`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingActivity.java`
- `app/src/main/java/com/fongmi/android/tv/utils/FileUtil.java`

这不是一个普通的数据缓存目录。当前代码中至少存在以下不同类型的数据：

| 类别 | 目录/文件 | 归属 |
|---|---|---|
| Exo 播放分片缓存 | `exo/` | Media3 `SimpleCache` |
| MPV HLS/ISO 分片缓存 | `mpv_hls/` | `MpvHlsCacheCoordinator` |
| MPV 本地预载缓存 | `mpv-demuxer-cache/` | MPV 原生 |
| MPV shader/font/ICC 缓存 | `mpv_lut_shaders/`、`fontconfig/`、根目录若干缓存 | MPV/native |
| 歌词及选源结果 | `lyrics/`、`lyrics/choices/` | `LyricsRepository` |
| K 歌轨道 | `karaoke_tracks/` | `KaraokeTrackRepository` |
| WebHome 扩展脚本 | `webhome_ext/` | `WebHomeExtension` |
| WebHome HTTP 缓存 | `webhome_raw/` | OkHttp `Cache` |
| EPG 节目单 | `epg/` | `EpgParser` |
| JS/Python/Jar 资源 | `js/`、`py/`、`jar/` | QuickJS/Chaquopy/JarLoader |
| 图片资源 | Glide 默认内部磁盘缓存 | Glide |
| 临时文件 | `update.apk`、`pushed-*`、`restore-legacy/`、备份同步临时文件 | 各功能模块 |
| 进程状态/锁/诊断 | `mpv-playback-recovery.lock`、恢复状态文件等 | 运行时状态 |

因此，“总大小上限 + 一套全局淘汰算法”会同时带来三类错误：

1. 删除仍被播放器、WebView、OkHttp 或 native 使用的文件。
2. 删除临时更新 APK、恢复包或进程状态，造成功能异常。
3. 绕过 Exo/MPV 已有的磁盘空间保护、重建和故障回退逻辑。

## 2. 业界证据

### 2.1 Android 官方

来源：
- https://developer.android.com/training/data-storage/app-specific
- https://developer.android.com/reference/android/os/storage/StorageManager

结论：

- `cacheDir` 中的数据是“可以更早被系统删除”的临时数据，系统在空间不足时可能主动清理。
- 官方明确要求应用“自己维护自己的缓存”，不能只依赖系统清理。
- `StorageManager.getCacheQuotaBytes()` 给出系统允许应用使用的缓存配额；`getCacheSizeBytes()` 给出当前缓存总量。
- 用户可见的上限只能是“应用期望上限”，有效上限还必须受系统 quota、文件系统可用空间和播放器安全保留值约束。

### 2.2 Media3 / ExoPlayer

来源：
- Media3 `SimpleCache`
- Media3 `LeastRecentlyUsedCacheEvictor`
- 当前代码：`MediaSourceFactory`、`DiskCacheCapacityPolicy`、`CacheCapacityState`

结论：

- `LeastRecentlyUsedCacheEvictor` 是官方提供的“按最近使用顺序淘汰”实现。
- 当前 WebHTV 已经有更强的保护：至少保留 10% 或 512MB 可用空间、容量重建等待、写入拒绝分类。
- 新功能只能调用和暴露现有策略，不能替换其淘汰器，也不能直接删除正在使用的 `exo/` 文件。

### 2.3 Glide

来源：
- https://bumptech.github.io/glide/doc/caching.html
- https://bumptech.github.io/glide/doc/configuration.html

结论：

- Glide 内存缓存和磁盘缓存默认使用 LRU。
- 磁盘缓存大小和目录应在 `AppGlideModule` 中配置。
- `Glide.clearDiskCache()` 必须在后台线程调用。
- Glide 的文件名是哈希 key，不能按业务 URL 精准删除，只能整库清理或由 Glide 自己淘汰。

### 2.4 OkHttp

来源：
- OkHttp `Cache` 官方 API 与实现

结论：

- OkHttp 磁盘缓存有 `maxSize`，内部按 journal + 最近使用记录淘汰。
- `evictAll()` 清空，`size()` 获取字节数；关闭/替换缓存需要同步处理。
- WebHome raw 当前使用 `128MB` 独立 OkHttp `Cache`，应继续由 OkHttp 自己维护，不接入普通文件 LRU。

### 2.5 mpv

来源：
- 当前 MPV 集成代码和上游 mpv 缓存选项

结论：

- `cache-on-disk`、`demuxer-cache-dir`、`demuxer-max-bytes` 属于 native renderer 管辖区。
- App 不应在播放过程中直接删除 MPV 正在读写的目录。
- 应用层可以提供状态、开关和“停止播放后清理”，但实际淘汰仍由 MPV 或已有 coordinator 完成。

## 3. 目标与非目标

### 3.1 产品目标

1. 用户能看到总缓存量、各模块大小、占比、文件数、最近更新时间。
2. 用户能对安全模块执行单项清理或分级清理。
3. 用户能设置模块级上限，并可设置一个受模块策略约束的总缓存软上限。
4. 用户能设置自动清理的时间与触发条件。
5. 设置页能解释“为什么实际大小没有达到上限”或“为什么当前不能清理”。
6. 移动端和 TV 端都有完整、可聚焦、可确认、可返回的交互。
7. 不破坏收藏、历史、设置、WebHome 扩展配置和正在播放的缓存。
8. 所有清理操作可取消、可观察、可重试，不出现 ANR 或 UI 卡死。

### 3.2 非目标

1. 不把 `cacheDir` 变成用户手动管理的任意文件浏览器。
2. 不把图片、播放分片、EPG、临时 APK 放进同一个全局 LRU。
3. 不承诺缓存一定保留；Android 仍可能提前清除。
4. 不使用 Android 不保证存在的“文件创建时间”作为主淘汰依据。
5. 不修改媒体播放协议、DRM、鉴权、代理或网络语义。
6. 不清理用户明确保存的文件、数据库、偏好设置或备份源文件。
7. 不在播放过程中强制重写 Exo `SimpleCache`。

## 4. 术语与不变量

### 4.1 CacheModule

一个可独立统计、独立解释、独立清理策略的缓存域。每个模块必须声明：

- 稳定 ID
- 用户可见名称
- 归类
- 根路径或查询函数
- 生命周期 owner
- 统计方式
- 清理方式
- 淘汰策略
- 是否允许自动清理
- 是否允许运行中清理
- 是否可重建
- 风险级别
- 预计最大的统计耗时

### 4.2 核心不变量

1. `CacheInventory` 只读统计，绝不修改文件；扫描和删除都不得跟随指向模块根目录外的符号链接。
2. 清理动作必须走模块自己的 executor，不能调用 `FileUtil.clearCache()`。
3. 任何模块清理前都要重新检查 owner 是否处于活跃状态。
4. 活跃模块的策略是 `defer` 或 `runtime-safe`，不是“直接 delete”。
5. 用户可见的大小是扫描时的快照，不承诺实时一致。
6. 所有清除操作都是幂等的。
7. 统计和清理都不在主线程执行。
8. 所有路径必须严格限制在预声明根目录内，防止路径穿越。
9. 单个模块失败不能阻断其他模块统计或清理。
10. 默认清理范围永远不包含不可重建状态、数据库、偏好和用户文件。

## 5. 缓存模块目录

### 5.1 分组建议

| 分组 | 模块 | 是否可重建 | 推荐淘汰 | 运行中清理 | 默认等级 |
|---|---|---|---|---|---|
| 播放 | Exo 播放缓存 | 是 | LRU + 空间保护 | 禁止，延后 | 深度 |
| 播放 | MPV HLS/ISO 分片 | 是 | LRU + 空间保护 | 安全条件下允许 | 深度 |
| 播放 | MPV demuxer 预载 | 是 | native 容量 | 禁止，延后 | 深度 |
| 播放 | MPV shader/font/ICC | 是 | 容量或保留期 | 禁止，延后 | 标准 |
| 媒体增强 | 歌词与选源 | 是 | LRU + 条目上限 | 允许 | 标准 |
| 媒体增强 | K 歌轨道 | 是 | LRU + 容量/条数 | 允许 | 标准 |
| 网络与扩展 | WebHome 扩展 | 是 | 网络优先 + LRU/容量 | 允许 | 标准 |
| 网络与扩展 | WebHome raw HTTP | 是 | OkHttp LRU | 禁止，重建客户端 | 标准 |
| 网络与扩展 | EPG 节目单 | 是 | 6 小时/次日 TTL | 允许，避开刷新中文件 | 轻度 |
| 网络与扩展 | 图片与封面 | 是 | Glide LRU | 后台允许 | 标准 |
| 插件资源 | JS/Python/Jar | 分情况 | 按使用时间 + 容量 | 需暂停相关加载 | 深度 |
| 其他 | 更新/推送/备份临时文件 | 分情况 | 文件年龄 + 白名单 | 使用中禁止 | 轻度 |
| 其他 | 遗留和孤立文件 | 是 | 明确规则清单 | 允许 | 深度 |

### 5.2 播放缓存不可一律显示为“可直接清理”

设置页必须区分：

- `当前可安全清理`
- `下次播放前清理`
- `正在使用，暂不能清理`
- `系统或 native 管理，仅可显示`

对 Exo/MPV 正在播放的场景，清理按钮可以保留，但执行时转为“排队清理”或明确提示用户在停止播放后重试。

## 6. 总体架构

```text
设置页缓存总览
    |
    v
CacheCenter (应用层门面)
    |
    +-- CacheInventory (只读扫描/快照/回调)
    +-- CacheCleanupManager (分级清理/排队/任务状态)
    +-- CachePolicyStore (上限/保留期/自动清理配置)
    +-- CachePolicyEngine (纯函数，决定每一项是否清理)
    +-- CacheScheduler (启动/空间不足/周期触发)
    |
    +-- CacheModule 注册表
          |
          +-- playback.exo
          +-- playback.mpv_hls
          +-- playback.mpv_demuxer
          +-- playback.mpv_runtime
          +-- media.lyrics
          +-- media.karaoke
          +-- network.webhome_ext
          +-- network.webhome_raw
          +-- network.epg
          +-- image.glide
          +-- plugin.scripts
          +-- temp.file
          +-- legacy.file
```

### 6.1 分层职责

| 层 | 职责 | 禁止事项 |
|---|---|---|
| UI | 展示快照、按钮状态、确认、进度、结果 | 不直接扫描目录，不直接 delete |
| CacheCenter | 初始化模块、协调 UI 与后台任务 | 不写业务模块淘汰逻辑 |
| Inventory | 统计大小、数量、时间、可清理性 | 不修改文件 |
| PolicyEngine | 纯函数计算保留/清理/排队决策 | 不做 I/O，不依赖 Android UI |
| CleanupManager | 执行模块清理、限额、并发控制、重试 | 不绕过模块 owner |
| PolicyStore | 持久化上限、保留期、自动清理配置 | 不保存扫描结果 |
| Module | 提供路径、状态、统计、清理、保护规则 | 不共享全局目录删除 |

### 6.2 为什么 PolicyEngine 要纯函数化

缓存淘汰策略容易出现边界错误。将“是否删除、保留多少、延期多久”做成不依赖 Android 的纯函数，可以直接单元测试：

- 用户上限
- 系统 quota
- 磁盘安全保留值
- 模块最小保留
- 活跃模块
- 文件年龄
- 最近访问

这样 UI、Android `File` 和 native owner 都不必进入测试。

## 7. 数据模型

### 7.1 CacheModuleId

```java
enum CacheModuleId {
    EXO("playback.exo"),
    MPV_HLS("playback.mpv_hls"),
    MPV_DEMUXER("playback.mpv_demuxer"),
    MPV_RUNTIME("playback.mpv_runtime"),
    LYRICS("media.lyrics"),
    KARAOKE("media.karaoke"),
    WEBHOME_EXT("network.webhome_ext"),
    WEBHOME_RAW("network.webhome_raw"),
    EPG("network.epg"),
    GLIDE("image.glide"),
    PLUGIN_SCRIPTS("plugin.scripts"),
    TEMP_FILES("temp.file"),
    LEGACY_FILES("legacy.file")
}
```

### 7.2 CacheModule 定义

```java
interface CacheModule {
    CacheModuleId id();
    String displayName();
    CacheGroup group();

    List<CacheRoot> roots();
    CacheMeasurement measure(MeasurementContext context) throws Exception;

    CacheCleanability cleanability();
    CacheEvictionPolicy evictionPolicy();
    CacheProtection protection();

    CacheCleanupResult clean(CleanupRequest request) throws Exception;
}
```

### 7.3 CacheRoot

```java
record CacheRoot(
        File root,
        PathKind kind,
        boolean recursive,
        Set<String> includePatterns,
        Set<String> excludeNames,
        boolean countSynthetic
) {}
```

`PathKind`：

- `FILES_ONLY`：只统计普通文件
- `TREE`：统计文件和子目录
- `SYNTHETIC`：无普通文件目录，使用第三方 API 统计（如 Glide、OkHttp）

### 7.4 CacheMeasurement

```java
record CacheMeasurement(
        CacheModuleId id,
        long bytes,
        long fileCount,
        int directoryCount,
        long oldestModifiedMs,
        long newestModifiedMs,
        long lastUsedHintMs,
        CacheAvailability availability,
        List<String> warnings
) {}
```

### 7.5 CacheSnapshot

```java
record CacheSnapshot(
        long generatedAtElapsedMs,
        long totalBytes,
        long userVisibleBytes,
        long reclaimableBytes,
        long protectedBytes,
        long systemQuotaBytes,
        long availableBytes,
        long totalStorageBytes,
        List<CacheMeasurement> modules,
        CacheScanStatus status
) {}
```

### 7.6 CacheCleanability

```java
enum CacheCleanability {
    SAFE_NOW,
    DEFER_UNTIL_IDLE,
    OWNER_MANAGED,
    TEMPORARILY_LOCKED,
    UNAVAILABLE
}
```

### 7.7 CacheEvictionPolicy

```java
enum CacheEvictionPolicy {
    LRU,
    TTL,
    LRU_WITH_TTL,
    AGE_BASED,
    SIZE_THEN_LRU,
    COUNT_THEN_LRU,
    OWNER_MANAGED
}
```

### 7.8 CacheProtection

```java
record CacheProtection(
        boolean allowAutomaticCleanup,
        boolean allowManualCleanup,
        boolean requireOwnerIdle,
        boolean requireAppIdle,
        long minimumKeepBytes,
        long minimumKeepFiles,
        boolean keepIfActiveSession
) {}
```

### 7.9 用户配置模型

```java
record CachePreferences(
        boolean autoCleanupEnabled,
        int retentionDays,
        AutoCleanupTrigger trigger,
        long totalLimitBytes,
        long imageLimitBytes,
        long lyricsLimitBytes,
        long karaokeLimitBytes,
        long epgLimitBytes,
        long webHomeLimitBytes,
        long pluginLimitBytes,
        long tempFileRetentionHours,
        int cleanupLevel
) {}
```

约束：

- 所有数值都必须有上下界。
- 任何 `0` 都表示“仅关闭自动清理”，不等同于“立即清空模块”。
- 用户上限不能覆盖系统 quota 和磁盘安全保留。
- `totalLimitBytes` 是软治理目标，不是直接文件淘汰规则。
- 不把播放缓存上限放在这里；播放缓存上限沿用现有播放器设置，避免双写。

## 8. 统计与扫描设计

### 8.1 扫描执行模型

```text
UI 打开缓存管理
  -> CacheCenter.requestSnapshot()
  -> 单线程 inventory executor 扫描模块
  -> 每个模块独立 try/catch
  -> 汇总快照
  -> App.post 更新 UI
```

要求：

- 扫描不得在主线程执行。
- 默认只扫描文件元数据，不读取文件内容。
- 每个模块有最大扫描时间和最大目录深度；超限返回部分结果和 warning。
- 结果缓存 3 秒，避免频繁点击造成重复 I/O。
- 用户主动下拉或点击刷新时绕过缓存。
- 播放中允许扫描，但播放缓存模块只报告快照，不触发淘汰。

### 8.2 大小统计口径

必须明确区分：

- `onDiskBytes`：文件 `length()` 之和，含稀疏文件时不等于实际物理分配。
- `logicalBytes`：逻辑长度。
- `ownerReportedBytes`：SimpleCache、OkHttp、Glide 报告的大小。
- `quotaBytes`：系统允许的缓存配额。

UI 默认使用 `onDiskBytes` 或 `ownerReportedBytes`，并在详情里标明来源，避免“数字不一致”被误判为 bug。

### 8.3 占比计算

```text
modulePercent = moduleBytes / totalBytes
```

- `totalBytes == 0` 时显示 `0%`
- 单项小于 0.1% 时显示 `<0.1%`
- 占比使用最近一次同一快照的数据，不能混用旧快照
- 排序默认按大小降序，第二排序为模块固定顺序，保证界面稳定

### 8.4 最近使用时间

Android `File.lastModified()` 可以用于“最近修改/最近使用”近似值，但不能当作严格 LRU 元数据。

- 对媒体分片：由 SimpleCache / OkHttp / MPV coordinator 自己维护
- 对歌词、K 歌、WebHome 扩展：可在读取时主动 `setLastModified(now)`
- 对无法安全更新时间戳的文件：只使用 mtime，并在 UI 标注“按修改时间估算”
- 不使用 `BasicFileAttributes.creationTime()` 作为硬依赖

### 8.5 失败降级

| 失败 | 行为 |
|---|---|
| 目录不存在 | bytes=0，availability=NOT_PRESENT |
| 无权限 | availability=UNAVAILABLE，保留其他模块结果 |
| 读取中途 IO 错误 | 返回已统计部分 + warning |
| 第三方 API 抛错 | 标记 OWNER_MANAGED，不影响总分 |
| 扫描超时 | 取消该模块，标记 TIMEOUT |
| 目录在扫描中消失 | 视为 0，不报致命错误 |

## 9. 模块级实现设计

### 9.1 Exo 播放缓存

现有实现：

- 路径：`Path.exoCache()`，即 `cacheDir/exo`
- 创建：`MediaSourceFactory.getCache()`
- 淘汰器：`LeastRecentlyUsedCacheEvictor`
- 容量来源：`PreloadSetting.getPreloadSizeBytes(PlayerSetting.EXO)`
- 空间保护：`DiskCacheCapacityPolicy`
- 重建：`CacheCapacityState` + `acquireCacheSession()` / `releaseCacheSession()`

设计：

1. 统计来源优先使用 `SimpleCache.getCacheSpace()`；创建失败时退化为目录文件统计。
2. 清理不直接调用 `Path.clear(Path.exoCache())`。
3. 用户点击清理时：
   - 无活跃 session：逻辑上重建 cache，或由 owner 提供安全清理接口。
   - 有活跃 session：标记 `DEFER_UNTIL_IDLE`，在最后一个 session 释放后执行。
4. 用户调整上限时，仍通过现有 `PlayerSetting.putPlayCacheOption()` 或 `PreloadSetting` 写入，不能新建第二套 key。
5. Exo 容量变更的生效时刻必须在 UI 中说明：
   - 空播放：立即重建
   - 播放中：下一次空闲或下一次播放时生效
6. 禁止把 `exo/` 加入全局 age-based 清理。

验收关注点：

- 活跃播放时点清理，不丢当前播放内容。
- 空闲时清理，下一次播放可重新下载。
- 上限降低不会破坏 `DiskCacheCapacityPolicy` 的 10% / 512MB 保护。

### 9.2 MPV HLS / ISO 分片缓存

现有实现：

- 路径：`Path.cache("mpv_hls")`
- owner：`MpvHlsCacheCoordinator.shared(...)`
- 容量：`PlayerSetting.getPlayCacheSize(PlayerSetting.MPV)`
- 已有：writer/reader lease、temp 文件、MIME sidecar、orphan 回收、circuit open、空间保护

设计：

1. 统计由 `cacheBytes()` + 文件数组成。
2. 运行中可由 coordinator 自己 `prune(capacity)`。
3. 手动清理不能直接删正在写入的 `.tmp` 或已打开文件。
4. 清理流程：
   - 获取 coordinator 清理锁；
   - 跳过 readers/writers 使用的 key；
   - 删除可回收的完整缓存对象及其 `.meta`；
   - 保留未完成写入，由 writer 自己结束或失败清理；
   - 完成后重新统计。
5. 用户上限继续使用现有 MPV play cache 设置。
6. ISO 会话的租约必须保持有效；清理时以 lease/active session 为准。

验收关注点：

- 播放 MPV HLS 时执行清理，当前播放保持连续。
- 清理后可继续缓存新分片。
- `.meta` 不会成为孤儿文件。
- circuit open 状态下不会反复触发磁盘风暴。

### 9.3 MPV demuxer 预载缓存

现有实现：

- 路径：`config.cacheDir()/mpv-demuxer-cache`
- 由 `MpvPlayer.resolvePreloadCacheCapacity()` 读取 `PreloadSetting` 和 `DiskCacheCapacityPolicy`
- 仅在 `cache-on-disk=yes` 时使用

设计：

1. 设置页只显示大小、容量、开关状态和“由 MPV 管理”提示。
2. 允许手动清理，但必须：
   - 无 MPV 活跃实例，或
   - 停止播放后执行
3. 不在播放中直接删除目录。
4. 不把其上限做成独立 key；继续沿用 `PreloadSetting`。

### 9.4 MPV 运行时缓存

包括：

- `mpv_lut_shaders`
- `fontconfig`
- `icc-cache-dir` 指向的缓存
- `youtube-mpd.xml` 等临时诊断/兼容文件

设计：

- 标记为 `OWNER_MANAGED` 或 `DEFER_UNTIL_IDLE`
- 默认自动清理只清理明确过期和无引用文件
- shader/font/ICC 属于可重建缓存，但在 MPV 退出后清理更安全
- 诊断文件和锁文件不纳入用户可见缓存统计，除非能证明可安全删除

### 9.5 歌词与选源缓存

现有实现：

- 目录：`Path.cache("lyrics")`
- 选源结果：`Path.cache("lyrics").choices/`
- 已有：`cacheCount()`、`clearCache()`
- 无 TTL、无容量上限

设计：

1. 推荐策略：`LRU_WITH_TTL`
2. 读取命中时更新 `lastModified`
3. 默认上限建议：
   - 256MB 或 10,000 个文件，哪个先触发
   - 保留期默认 90 天，可配置 7/30/90/永久
4. 清理顺序：
   - 先删超过保留期的文件
   - 再按最近修改时间从旧到新删，直到低于 90% 目标上限
5. 选源结果和歌词文件使用同一模块，但统计时分开展示更清晰。
6. 现有歌词页面“清空歌词缓存”继续可用，并改为调用模块清理接口。

### 9.6 K 歌轨道缓存

现有实现：

- 目录：`Path.cache("karaoke_tracks")`
- 已有内部删除但无统一上限

设计：

1. 推荐策略：`SIZE_THEN_LRU`
2. 默认上限建议 256MB 或 5,000 个文件
3. 保留用户主动绑定来源生成的轨道；仅清理自动生成或未被引用的 sidecar
4. 清理前检查正在使用的 K 歌会话
5. 读取命中时更新 mtime

### 9.7 WebHome 扩展缓存

现有实现：

- 目录：`Path.cache("webhome_ext")`
- 加载策略：网络优先，失败回退缓存
- 现有清缓存：`WebHomeExtensionRegistry.clear()`

设计：

1. 推荐策略：`LRU_WITH_TTL`
2. 永久缓存扩展脚本不是目标；默认保留 30 天
3. 用户点击“刷新”或网络成功时更新 mtime
4. 清理不会删除扩展配置，只删除脚本/manifest 副本
5. 清理后下一次加载允许重新下载；离线时功能可能退化，必须在确认框说明

### 9.8 WebHome raw HTTP 缓存

现有实现：

- 目录：`Path.cache("webhome_raw")`
- OkHttp `Cache`，`CACHE_BYTES = 128MB`

设计：

1. 推荐策略：`OWNER_MANAGED`，由 OkHttp LRU 处理
2. 统计使用 `Cache.size()` / `maxSize()`
3. 手动清理使用 `cache.evictAll()`，并在后台线程执行
4. 如果实现替换 cache 或关闭 cache，必须先停止使用该 client 的请求
5. 不建议直接删除目录后再让活跃 client 继续使用

### 9.9 EPG 缓存

现有实现：

- 目录：`Path.epg()`
- 刷新规则：文件不存在、不是今天、修改超过 6 小时
- 解压 XML：`file.name + ".xml"`

设计：

1. 推荐策略：`TTL`
2. 模块统计要同时包含压缩原始文件和派生 XML
3. 清理规则：
   - 删除超过 7 天的节目单
   - 删除没有 live source 引用的孤儿文件
   - 保留当前正在解析/下载的文件
4. 不把 EPG 清理放进播放缓存清理按钮，避免直播页首次进入变慢
5. 默认纳入“轻度清理”的过期项，而不是全部删除

### 9.10 图片与封面 Glide

现状：

- `OkGlideModule` 只设置日志级别
- 未显式设置 disk cache size/location
- Glide 默认使用内部 cacheDir 下的 `image_manager_disk_cache`

设计：

1. 推荐策略：`LRU`
2. 默认上限建议 256MB（可按设备内存/屏幕调整）
3. 配置必须放在 `OkGlideModule.applyOptions()`，不能在设置页运行时重建 Glide 单例
4. 用户修改上限后，下一次进程启动生效；UI 必须明确提示
5. 手动清理使用后台线程 `Glide.get(context).clearDiskCache()`
6. 统计使用 Glide 已知缓存目录或 `DiskCache` 接口；不要把 Glide 哈希文件纳入通用规则

### 9.11 插件脚本缓存

包括：

- `js/`
- `py/`
- `jar/`
- Dex 优化相关缓存

设计：

1. 默认策略：`LRU_WITH_TTL`，但默认不自动清理
2. 清理前必须确认没有正在执行/加载的插件
3. 删除 Jar 后，下一次加载必须能够重新下载或从用户配置恢复
4. 将插件缓存与播放缓存严格分开展示，避免用户误删
5. 初次上线建议只提供手动清理和统计，自动清理 P3 再开

### 9.12 临时文件

包括：

- `update.apk`
- `pushed-*.apk`
- `restore-legacy/`
- 备份、同步、恢复、推送过程中生成的 zip/临时文件

设计：

1. 推荐策略：`AGE_BASED`
2. 默认保留 24 小时
3. 正在下载/安装/恢复/推送的文件必须跳过
4. 使用显式 allowlist，禁止对 `cacheDir` 做“除 X 外全部删除”
5. 纳入“轻度清理”

### 9.13 遗留和孤立文件

实现（版本化规则表，`CacheLegacyRules`）：

1. 必须有版本化规则表；当前 `TABLE_VERSION = 2`，规则为 `restore-legacy`（v1）与 `subtitle_asset`（v2）。新增/移除规则时必须同时递增版本号，并有测试断言表版本等于最高规则版本。
2. 不根据文件名模糊匹配未知文件：只有规则表里显式声明的路径才进入本模块。
3. **统计与清理必须使用同一份规则表**（`CacheLegacyRules.rules()`）：`CacheModuleRegistry` 的统计根来自 `CacheLegacyRules.roots(cache)`，`CacheCleanupManager.clearLegacyPaths` 遍历同一组规则。旧实现统计“除已登记根目录外的全部”、却只清理 `restore-legacy`，导致「报告 17 MB 却删除 0 个文件」，该结构性缺陷已由 `CacheLegacyRulesTest.moduleRootsAreBuiltFromTheSameRuleTableCleanupUses` 锁定。
4. 分级清理（L1/L3）保留 7 天年龄保护；模块行的「清理」按钮表示“立即移除该遗留路径”，不受保留期限制。
5. 未登记的活跃缓存不再谎报为“遗留”。原先 `plugin-preheat`（82 文件）、`webhtv-debug-log.txt{,.1,.2,.3,.pinned}` 等被当作遗留文件统计，既无法清理也误导用户。现在拆分为两个模块：
   - `diagnostic.logs`（诊断日志）：owner 为 `DebugLogStore`，手动可清理，通过 owner 的 `clear()` 删除；**不进入任何分级清理与自动清理**（用户可能还需要这些排障证据）。
   - `unclassified.cache`（未归类缓存）：只统计、不清理（`allowManualCleanup=false`、`allowAutomaticCleanup=false`）。这里保留“排除已知 owner 后全部计入”的逆向规则是**有意的**：需要看见未来新增但尚未登记的缓存目录；因为该模块永不删除，逆向规则在此处不产生危害。
6. 每次升级只操作明确声明的 legacy 路径。

## 10. 淘汰策略实现

### 10.1 全局排序键

对允许文件级淘汰的模块，统一使用以下排序：

```text
primary: state (active writers/readers always last)
second:  protected flag
third:   lastUsedMs (fallback: lastModifiedMs)
fourth:  size (large files can resolve pressure faster)
fifth:   path (deterministic tie-breaker)
```

禁止使用创建时间作为唯一排序键。

### 10.2 LRU

适用于：

- Exo
- MPV HLS
- Glide
- WebHome raw
- 歌词
- K 歌
- WebHome 扩展

实现要求：

- 优先调用 owner 提供的 LRU
- 只有在没有 owner API 时才使用应用层排序
- 读取命中时应更新 `lastUsedMs` 或 mtime
- 写完成后才能给文件标记可回收

### 10.3 TTL

适用于：

- EPG
- 歌词
- WebHome 扩展
- 临时文件

规则：

- `now - lastModified > ttl` 才可过期
- 当前活跃文件不参与 TTL
- TTL 清理完成后，再判断容量上限

### 10.4 大小优先

适用于：

- 歌词
- K 歌
- WebHome 扩展
- 临时文件

执行顺序：

1. 删除过期项
2. 计算超出量
3. 按 LRU 从旧到新删除
4. 删除到 `target = limit * 0.9`，避免刚清理完立刻又触发

### 10.5 条数优先

适用于：

- 歌词
- K 歌
- WebHome 扩展

不要只按条数判断，必须同时考虑大小，避免大量小文件清理后仍然超限。

### 10.6 用户上限计算

单模块有效上限：

```text
effectiveLimit = min(
    configuredLimit,
    systemCacheQuota,
    ownerLimit,
    diskSafeLimit
)
```

总缓存有效上限：

```text
effectiveTotalLimit = min(
    configuredTotalLimit or unbounded,
    systemCacheQuota,
    diskSafeLimit
)
```

总上限的强制方式：

1. 先扫描得到总用量和模块占比。
2. 超过总上限时，优先执行 L1，再执行 L2。
3. L2 完成后仍超限，只提示用户执行 L3，不自动删除播放缓存。
4. 播放缓存占用过高时显示“由播放设置和系统空间决定”，而不是偷偷扩大总上限。
5. 总上限永远不能直接触发对整个 `cacheDir` 的文件级 LRU。

其中：

```text
diskSafeLimit = reclaimableBytes + availableBytes - safetyReserve
safetyReserve = max(512MB, totalStorage * 10%)
```

这个公式必须复用现有 `DiskCacheCapacityPolicy`，不能复制第二份常量。

### 10.7 运行中保护

每个模块必须返回：

- `activeReaders`
- `activeWriters`
- `activeSessions`
- `lastActivityMs`

清理时：

- `activeWriters > 0`：跳过
- `activeReaders > 0`：跳过或延迟
- `requireOwnerIdle`：排队到 owner idle
- `requireAppIdle`：只在应用后台或无播放时执行

## 11. 配置模型与迁移

### 11.1 存储位置

使用现有 `Prefers` / `SharedPreferences`，键名统一前缀：

```text
cache_mgmt_auto_enabled = true|false
cache_mgmt_retention_days = 7|30|90|0
cache_mgmt_trigger = low_space|weekly|startup
cache_mgmt_total_limit_mb = auto|2048|4096|8192
cache_mgmt_image_limit_mb = 256
cache_mgmt_lyrics_limit_mb = 256
cache_mgmt_karaoke_limit_mb = 256
cache_mgmt_epg_limit_mb = 256
cache_mgmt_webhome_limit_mb = 256
cache_mgmt_plugin_limit_mb = 1024
cache_mgmt_temp_retention_hours = 24
```

要求：

- 每个值有默认值和 clamp
- 读取时统一 normalize，不信任持久化值
- 新版本如果改变单位，必须迁移旧 key
- 配置写入后触发 `PolicyEngine` 重新评估，不直接启动清理

### 11.2 默认值建议

| 配置 | 默认值 | 可选项 |
|---|---|---|
| 自动清理 | 开 | 开/关 |
| 保留期限 | 30 天 | 7/30/90/永久 |
| 触发条件 | 空间不足 + 每周 | 仅空间不足/每周/启动时 |
| 总缓存上限 | 自动 | 自动/2GB/4GB/8GB |
| 图片上限 | 256MB | 128/256/512/1GB |
| 歌词上限 | 256MB | 128/256/512/1GB |
| K 歌上限 | 256MB | 128/256/512/1GB |
| EPG 上限 | 256MB | 128/256/512 |
| WebHome 上限 | 256MB | 128/256/512 |
| 插件上限 | 1GB | 512MB/1GB/2GB |
| 临时文件 | 24 小时 | 6/24/72 小时 |

### 11.3 迁移

1. 首次启动本功能时写入默认值。
2. 不修改现有 `play_cache`、`preload_size` 等播放设置。
3. 如果用户已有 MPV/Exo 上限设置，设置页显示其实际值并标注“由播放设置管理”。
4. 版本升级时按 `cache_mgmt_schema_version` 迁移。
5. 迁移失败时保留旧值并使用安全默认。

## 12. 清理引擎

### 12.1 清理等级定义

| 等级 | 名称 | 清理内容 | 预期影响 |
|---|---|---|---|
| L1 | 轻度清理 | 过期 EPG、孤立临时文件、过期 legacy、缓存元数据 | 几乎无感知 |
| L2 | 标准清理 | L1 + 图片、歌词、K 歌、WebHome 扩展、插件过期项 | 首次加载略慢 |
| L3 | 深度清理 | L2 + Exo、MPV HLS、MPV demuxer、MPV runtime | 首次播放需重新缓存 |

播放缓存永远不进入 L1/L2。

### 12.2 清理接口

```java
interface CacheCleaner {
    CacheCleanupPlan plan(CleanupRequest request);
    CacheCleanupResult execute(CacheCleanupPlan plan, CleanupProgressListener listener);
    void cancel();
}

record CleanupRequest(
        CacheModuleId moduleId,
        CleanupMode mode,
        long targetBytes,
        boolean force,
        boolean allowDeferred
) {}
```

### 12.3 清理状态机

```text
IDLE
  -> PLANNING
  -> WAITING_FOR_IDLE        （需要 owner idle）
  -> RUNNING
  -> POST_VERIFY
  -> COMPLETED
  -> PARTIAL
  -> FAILED
  -> CANCELLED
```

规则：

- 用户可取消 `PLANNING` 和 `WAITING_FOR_IDLE`。
- `RUNNING` 只能请求取消；当前文件删除完成后停止。
- 完成后必须重新统计该模块，不能直接用删除前数字相减作为最终结果。
- 部分失败返回 `PARTIAL` 和明细，不整体报成功。

### 12.4 执行顺序

1. 校验模块 ID、路径、权限和符号链接边界。
2. 重新检查 active owner 状态。
3. 生成不可变 `CacheCleanupPlan`。
4. 用户确认后进入 `RUNNING`。
5. 按“过期 → 超额 → 孤儿”的顺序删除。
6. 每个文件处理前再次检查 active/protected 状态。
7. 使用 `delete()` 结果判断成功，不假设删除一定成功。
8. 每 100 个文件或 250ms 报告一次进度。
9. 完成后执行 `POST_VERIFY`。
10. 写入结构化结果和日志。

### 12.5 并发控制

- 同一模块同一时间只允许一个清理任务。
- 全局最多同时运行 1 个清理任务，避免 I/O 抖动。
- 扫描和清理可以并行，但同一模块不能。
- 播放开始时可以取消可取消的清理任务。
- L3 清理在播放中默认拒绝，除非模块声明 `runtime-safe`。

### 12.6 删除顺序

对普通文件模块：

```text
1. temp/orphan 文件
2. 超过 TTL 的文件
3. 超过条数上限的文件
4. 超过大小上限的文件
5. 空目录
```

对 owner-managed 模块：

```text
1. 调用 owner 的 prune/evict/clear
2. 不自行遍历删除
3. 只处理 owner 明确遗留的 orphan/temp
```

### 12.7 保护名单

任何清理都不能删除：

- `filesDir` 中的数据库和偏好
- 用户选择的壁纸和本地文件
- `mpv-playback-recovery.lock`
- 正在播放的 media session 状态
- WebHome 扩展配置
- 历史记录、收藏、Cookie（若不在 cacheDir）
- 备份/恢复的源文件
- 正在下载/安装的 APK

保护名单必须由代码显式声明，不能只靠文件名前缀。

## 13. 自动清理设计

### 13.1 触发条件

支持三种，默认全部使用：

1. 启动后延迟 30 秒，低优先级执行
2. 每 7 天检查一次
3. 可用空间低于阈值时

空间阈值：

```text
lowSpace = availableBytes < max(512MB, totalBytes * 10%)
```

### 13.2 启动清理流程

```text
应用启动
  -> 延迟 30s
  -> 读取配置
  -> 获取 StorageManager quota
  -> 扫描模块
  -> 只对 allowAutomaticCleanup 模块生成 plan
  -> 优先 L1
  -> 若仍空间不足，生成 L2
  -> 永不自动执行 L3
  -> 记录结构化结果
```

### 13.3 周期清理流程

- 记录 `cache_mgmt_last_auto_ms`
- 距上次成功清理超过配置周期才执行
- 避免每次进程重启重复扫描
- 若上次为 PARTIAL，下次优先重试失败模块

### 13.4 低空间清理流程

- 监听 `StorageManager` 存储状态或使用周期性 `StatFs`
- 连续两次低于阈值才触发，避免瞬时抖动
- 播放中只允许 L1
- L1 完成后仍低空间时提示用户手动执行 L2
- 不允许自动执行 L3

### 13.5 自动清理结果

记录：

- 触发原因
- 清理等级
- 清理前后总量
- 每个模块删除字节数、文件数
- 失败原因
- 耗时

对用户展示时只在“结果异常”或手动进入缓存页时显示，不打扰正常播放。

## 14. 设置页设计

### 14.1 第一层入口

现有设置页：

```text
缓存                                       1.8 GB
```

改为：

```text
缓存管理                                   1.8 GB / 3.0 GB
```

行为：

- 点击进入缓存管理页
- 右侧显示总用量/有效上限
- 若正在扫描，右侧显示“计算中”
- 若扫描失败，显示“无法读取”

不直接把第一层点击改成“清空全部”，降低误删概率。

### 14.2 第二层页面结构

```text
[缓存管理]

总计
  1.8 GB / 3.0 GB
  可用空间 42.6 GB

[使用分布]
  播放缓存                 1.44 GB   80%
  图片与封面                180 MB    10%
  歌词与字幕                 42 MB     2%
  WebHome                   96 MB     5%
  EPG                       18 MB     1%
  插件与脚本                 30 MB     2%
  临时文件                  12 MB    <1%
  其他                       0 B      0%

[清理]
  轻度清理
  标准清理
  深度清理

[自动清理]
  自动清理                   开
  总缓存上限                 自动（当前 3.0 GB）
  保留期限                   30 天
  触发条件                   空间不足、每周

[模块设置]
  图片缓存上限               256 MB
  歌词缓存上限               256 MB
  ...
```

### 14.3 模块详情页

每个模块详情显示：

```text
视频播放缓存
  大小: 1.2 GB
  占比: 67%
  文件: 3,421
  最近使用: 今天 14:32
  上限: 512 MB
  策略: LRU + 空间保护
  状态: 正在播放，暂不可清理

[清理此模块]
[查看清理规则]
```

关键规则：

- `OWNER_MANAGED` 模块的按钮文案为“由播放器自动管理”，不是“清空”。
- `DEFER_UNTIL_IDLE` 显示“停止播放后清理”。
- 未播放且安全可清理时显示普通按钮。
- 若清理会导致首次播放变慢，在按钮下方说明。

### 14.4 清理确认交互

轻度清理：

```text
清理过期节目单和临时文件？
不会删除图片、播放缓存或设置。
[取消] [清理]
```

标准清理：

```text
清理图片、歌词、WebHome 等可重建缓存？
下次打开相关页面可能需要重新下载，收藏、历史和设置不受影响。
[取消] [清理]
```

深度清理：

```text
清理全部播放缓存？
下次播放需要重新缓冲和下载，可能增加流量消耗。
当前正在播放时会跳过对应模块。
[取消] [清理]
```

深度清理必须二次确认；标准清理可选二次确认；轻度清理一次确认即可。

### 14.5 清理进度与结果

进度示例：

```text
正在清理图片与封面…
已清理 128 MB / 预计 180 MB
```

完成后：

```text
清理完成
释放 312 MB，删除 2,418 个文件
跳过 3 个正在使用的文件
```

部分失败：

```text
部分完成
释放 298 MB，删除 2,401 个文件
图片缓存目录被系统占用，17 个文件未删除
[重试] [关闭]
```

### 14.6 空状态和边界状态

| 状态 | 文案 |
|---|---|
| 无缓存 | 暂无缓存 |
| 扫描中 | 正在计算缓存占用… |
| 无权限 | 无法读取部分缓存，其他模块仍可管理 |
| 全部不可清理 | 当前缓存均由播放器自动管理 |
| 空间充足 | 缓存空间充足 |
| 空间紧张 | 可用空间不足，建议执行轻度清理 |
| 上限被系统限制 | 已按系统缓存配额限制为 X |

## 15. TV 端交互设计

### 15.1 焦点模型

- 第一层“缓存管理”可聚焦
- 第二层每个分组一个 RecyclerView 或等价焦点列表
- 清理等级按钮横向排列
- 模块项上下移动，确定进入详情，返回回到列表
- 清理确认对话框默认焦点在“取消”

### 15.2 遥控器要求

- 不能依赖触摸滚动
- 不能依赖长按作为唯一入口
- 确认键执行当前焦点项
- 返回键逐层返回，不直接退出设置
- 所有按钮有明确焦点态
- 进度对话框处理返回键：运行中只请求取消，不直接关闭

### 15.3 大屏信息密度

- 占比用进度条 + 百分比
- 每个模块项最多两行文字
- 详细规则放入详情页，不挤在列表
- 数字使用统一的本地化格式，例如 `1.8 GB`

## 16. 可访问性与本地化

### 16.1 可访问性

- 每个进度条有 contentDescription
- 颜色不是唯一状态表达
- 清理按钮提供文本状态
- 焦点顺序与视觉顺序一致
- 重要删除操作有非颜色提示

### 16.2 本地化

需要新增字符串：

- `cache_management_title`
- `cache_total_usage`
- `cache_percent`
- `cache_cleanup_light`
- `cache_cleanup_standard`
- `cache_cleanup_deep`
- `cache_cleanup_running`
- `cache_cleanup_done`
- `cache_cleanup_partial`
- `cache_cleanup_skipped_active`
- `cache_policy_lru`
- `cache_policy_ttl`
- `cache_owner_managed`
- `cache_defer_until_idle`
- `cache_limit_effective_by_system`
- `cache_retention_days`
- `cache_trigger_low_space`
- `cache_trigger_weekly`
- `cache_trigger_startup`

必须同时更新：

- `values/strings.xml`
- `values-zh-rCN/strings.xml`
- `values-zh-rTW/strings.xml`
- 其他现有语言文件如项目已有对应项

### 16.3 单位格式

- 使用现有 `FileUtil.byteCountToDisplaySize()`
- 占比保留一位小数
- 小于 0.1% 显示 `<0.1%`
- 时间显示“今天 HH:mm / 昨天 / N 天前”，但底层保存毫秒值

## 17. 分阶段实施计划

### P0：缓存清单与可视化

目标：只读展示各模块大小和占比，不改变清理行为。

范围：

- 新增 `CacheModuleId`
- 新增 `CacheModule`
- 新增 `CacheInventory`
- 新增 `CacheSnapshot`
- 新增 `CacheCenter`
- 移动端和 TV 端缓存管理页
- 保留旧的一键清理入口，但改为跳转到新页面
- 补充 strings

验收：

- 各模块统计与目录实际大小一致
- 占比总和在浮点容差内等于 100%
- 空目录、无权限、目录消失都能正常显示
- 扫描不阻塞主线程
- 移动端和 TV 端可完整操作

### P1：分级清理

目标：轻度/标准/深度清理，模块单项清理。

范围：

- 新增 `CacheCleanupManager`
- 新增 `CachePolicyEngine`
- 新增清理确认、进度、结果对话框
- 接入歌词、K 歌、WebHome、EPG、临时文件、Glide
- 为 Exo/MPV 增加 idle/defer 状态

验收：

- 清理只影响目标模块
- 活跃模块被安全跳过或排队
- 清理后数字刷新
- 取消和重试正常工作
- 不出现 ANR

### P2：模块上限配置

目标：用户可设置图片、歌词、K 歌、WebHome、EPG、插件、临时文件上限。

范围：

- 新增 `CachePolicyStore`
- 新增设置页模块配置入口
- Glide disk cache 配置
- 应用层 LRU/TTL 淘汰器
- 播放器上限仍复用原有播放设置

验收：

- 配置持久化跨进程有效
- 修改非播放模块上限后按策略生效
- 修改 Exo/MPV 上限有明确生效说明
- 系统 quota 更低时显示有效上限

### P3：自动清理

目标：启动、周期、空间不足触发自动清理。

范围：

- 新增 `CacheScheduler`
- 新增 `CacheAutoCleanupWorker`
- 使用 `WorkManager` 或项目已有后台机制
- 低空间检测
- 清理结果记录

验收：

- 只自动清理允许的模块
- 不自动清理 L3
- 播放中不打断播放
- 不重复执行
- 设备重启后仍能恢复调度

### P4：运行中治理增强

目标：让运行中清理更细粒度、可观测。

范围：

- Exo 空闲重建
- MPV coordinator 安全清理接口
- Glide/OkHttp cache 重建
- 清理原因和结果日志

验收：

- 播放中清理不导致卡顿、崩溃、黑屏
- 清理结束播放继续可用
- 下一次播放可正常回填缓存

## 18. 文件级实施清单

### 18.1 新增文件

```text
app/src/main/java/com/fongmi/android/tv/cache/CacheModuleId.java
app/src/main/java/com/fongmi/android/tv/cache/CacheGroup.java
app/src/main/java/com/fongmi/android/tv/cache/CacheModule.java
app/src/main/java/com/fongmi/android/tv/cache/CacheRoot.java
app/src/main/java/com/fongmi/android/tv/cache/CacheMeasurement.java
app/src/main/java/com/fongmi/android/tv/cache/CacheSnapshot.java
app/src/main/java/com/fongmi/android/tv/cache/CacheAvailability.java
app/src/main/java/com/fongmi/android/tv/cache/CacheCleanability.java
app/src/main/java/com/fongmi/android/tv/cache/CacheEvictionPolicy.java
app/src/main/java/com/fongmi/android/tv/cache/CacheProtection.java
app/src/main/java/com/fongmi/android/tv/cache/CacheInventory.java
app/src/main/java/com/fongmi/android/tv/cache/CacheCenter.java
app/src/main/java/com/fongmi/android/tv/cache/CacheCleanupManager.java
app/src/main/java/com/fongmi/android/tv/cache/CachePolicyStore.java
app/src/main/java/com/fongmi/android/tv/cache/CachePolicyEngine.java
app/src/main/java/com/fongmi/android/tv/cache/CacheScheduler.java
app/src/main/java/com/fongmi/android/tv/cache/CacheCleanupResult.java
app/src/main/java/com/fongmi/android/tv/cache/CacheCleanupPlan.java
app/src/main/java/com/fongmi/android/tv/cache/CacheCleanupProgress.java
app/src/main/java/com/fongmi/android/tv/ui/dialog/CacheManagementDialog.java
app/src/main/res/layout/dialog_cache_management.xml
app/src/test/java/com/fongmi/android/tv/cache/CachePolicyEngineTest.java
app/src/test/java/com/fongmi/android/tv/cache/CacheInventoryTest.java
app/src/test/java/com/fongmi/android/tv/cache/CacheCleanupPlannerTest.java
```

具体包名和基础类应与项目现状对齐；如果项目已有 `utils` 或 `setting` 归属惯例，可在实施时调整，但模块边界不能合并。

### 18.2 修改文件

```text
app/src/main/java/com/fongmi/android/tv/utils/FileUtil.java
  - 保留兼容入口
  - 新增只读统计辅助
  - 禁止新增全局 deleteAll 调用

app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingFragment.java
  - 缓存行改为进入缓存管理页

app/src/leanback/java/com/fongmi/android/tv/ui/activity/SettingActivity.java
  - 缓存行改为进入缓存管理页

app/src/mobile/res/layout/fragment_setting.xml
app/src/leanback/res/layout/activity_setting.xml
  - 更新缓存入口文案和右侧显示

app/src/main/res/values/strings.xml
app/src/main/res/values-zh-rCN/strings.xml
app/src/main/res/values-zh-rTW/strings.xml
  - 新增缓存管理文案

app/src/main/java/com/fongmi/android/tv/utils/OkGlideModule.java
  - 配置 Glide disk cache size/location

app/src/main/java/com/fongmi/android/tv/player/lyrics/LyricsRepository.java
  - 接入模块统计、LRU、TTL

app/src/main/java/com/fongmi/android/tv/player/karaoke/KaraokeTrackRepository.java
  - 接入模块统计、LRU、容量上限

app/src/main/java/com/fongmi/android/tv/web/ext/WebHomeExtensionRegistry.java
  - 保留现有清缓存入口，改为调用 CacheCenter

app/src/main/java/com/fongmi/android/tv/web/WebHomeRawAdapter.java
  - 暴露 OkHttp cache 统计与 evict 接口

app/src/main/java/com/fongmi/android/tv/api/parser/EpgParser.java
  - 提供缓存文件枚举和过期规则

app/src/main/java/com/fongmi/android/tv/player/exo/MediaSourceFactory.java
  - 暴露安全清理/idle 重建接口
```

### 18.3 明确不修改

```text
收藏、历史、偏好数据库
播放协议、DRM、代理、鉴权
MPV/FFmpeg native 源码
third_party 依赖锁和二进制
正式包构建配置
```

## 19. 测试矩阵

### 19.1 纯单元测试

| 测试类 | 覆盖 |
|---|---|
| `CachePolicyEngineTest` | 上限、TTL、LRU、保护、quota、并发保护 |
| `CacheInventoryTest` | 大小、条数、失败降级、空目录、symlink |
| `CacheCleanupPlannerTest` | 分级计划、删除顺序、保护名单、幂等 |
| `CacheRetentionPolicyTest` | 7/30/90/永久、边界时间 |
| `CacheLimitPolicyTest` | min(用户上限, quota, 磁盘安全值) |
| `CacheAutoCleanupPolicyTest` | 启动/周期/低空间、播放中降级为 L1 |
| `CacheModuleRegistryTest` | ID 唯一、路径不重叠、默认策略正确 |

### 19.2 集成测试

| 场景 | 预期 |
|---|---|
| 扫描歌词目录 | 大小和数量正确 |
| 扫描 WebHome raw | 使用 OkHttp size，不按目录估算 |
| 清理歌词 | 只删除歌词文件，不碰其他目录 |
| 清理 EPG | 过期删除，今天的保留 |
| 清理临时文件 | 正在下载的 APK 被跳过 |
| 清理 Exo（空闲） | 清空后下次播放可重建 |
| 清理 Exo（播放中） | 播放不中断，任务被延后或拒绝 |
| 清理 MPV HLS（播放中） | 当前分片不被删除 |
| 清理 Glide | 后台完成，不阻塞 UI |
| 清理 WebHome | 配置保留，脚本副本可重新下载 |
| 取消清理 | 当前文件完成后停止，状态为 CANCELLED |
| 部分失败 | 返回 PARTIAL，可重试失败模块 |

### 19.3 设备矩阵

| 设备/形态 | 必测项 |
|---|---|
| Android 手机，API 24 | 基础统计、清理、无崩溃 |
| Android 手机，API 29+ | Scoped storage、cache quota |
| Android TV / 遥控器 | 焦点、方向键、返回键、确认 |
| ARM64 模拟器 | 移动端安装、启动、清理 |
| TV32 模拟器 | TV 端焦点和布局 |
| 低存储设备 | 安全保留值、系统清缓存 |
| 高分辨率图片 | Glide 缓存统计与清理 |
| MPV 播放设备 | 播放中清理保护 |
| Exo 播放设备 | session 重建和 pending 生效 |

### 19.4 验证命令

实施完成后按项目规范执行：

```bash
./gradlew :app:testDebugUnitTest --tests 'com.fongmi.android.tv.cache.*'
./gradlew :app:assembleDebug
bash scripts/build_arm64_debug_install.sh
```

说明：

- 当前任务只写设计文档，不执行上述构建。
- 实施阶段按实际改动选择最小测试集。
- 设备验证必须使用覆盖安装，不卸载现有包。
- 需要多模拟器对比时先申请分配。

## 20. 验收标准

### 20.1 功能验收

- [x] 设置页第一层显示总缓存量和有效上限
- [x] 缓存管理页显示每个模块的大小、占比、文件数和最近时间
- [x] 各模块可以单项清理（插件/owner-managed 模块按设计禁用直接清理）
- [x] 提供轻度、标准、深度三级清理
- [x] 清理前有明确确认，深度清理有二次确认（确认框默认焦点在「取消」，单次确认键即安全取消；深度第二步同样回到「取消」）
- [x] 清理中有进度，可取消
- [x] 清理后有释放量、删除数、跳过数和失败明细（以被动通知 + 面板状态行呈现，不再要求用户二次确认；执行前的三级清理确认与深度二次确认保持不变）
- [x] 支持总缓存软上限（L1→L2 升级，永不自动 L3）；设备实测可设 1 GB，受系统配额 478.9 MB 收敛
- [x] 支持图片、歌词、K 歌、WebHome、EPG、插件、临时文件的模块上限（统一档位，见 §20.6）；插件上限仅持久化配置，自动淘汰按安全设计暂不启用
- [x] 播放器上限继续由播放器设置驱动，没有重复配置
- [x] 支持自动清理开关与保留期限；触发条件为固定的启动/周期/低空间策略（不支持用户裁剪，见 §20.6）。低空间触发已设备实测（400MB tmpfs 真实低空间下 Job 执行 `reason=low-space` 的 L2 清理）
- [x] 支持启动、周期、低空间触发（含持久化 JobScheduler 重启恢复；`dumpsys jobscheduler` 记录 Job 实际运行，历史含 `reason=periodic` 与 `reason=low-space` 记录）
- [x] 自动清理永不执行 L3

### 20.2 安全验收

- [x] 任何清理都不会删除 `filesDir` 数据
- [x] 任何清理都不会删除用户保存文件（清理根均为 `cacheDir` 子目录）
- [x] 任何清理都不会删除正在使用的播放文件（Exo/MPV 走 owner idle 接口）
- [x] Exo/MPV 的空间保护策略保持不变（未改动 `DiskCacheCapacityPolicy` 与 LRU evictor；清理仅在 idle 时经 owner 接口）
- [x] 清理失败不会导致数据丢失或应用崩溃（逐文件判断删除结果，失败返回 PARTIAL/FAILED；Owner 拒绝时不影响播放）
- [x] 路径校验能阻止目录穿越（显式根路径 + 白名单文件名）
- [x] 扫描和清理不跟随指向缓存根目录外的符号链接（`CachePathSafety.isSymbolicLink` 只判最终路径组件，祖先目录别名不再误伤；`CachePathSafetyTest` 覆盖「祖先符号链接正常计入」与「文件本身符号链接仍跳过」；曾因误伤导致统计恒为 0，已修复并设备复测）
- [x] 不依赖 Android 创建时间（使用 lastModified）
- [x] 不调用全局 `Path.clear(cacheDir)` 作为新清理实现

### 20.3 性能验收

- [x] 缓存页首次扫描在主线程外完成
- [x] 高速设备上扫描 < 1 秒（NX627J 实测 9 ms / 13 模块）
- [x] 低速设备上有超时和部分结果降级（每模块 2 秒超时，超时记 warning 并降级为 PARTIAL，由 `CacheInventoryTest`/扫描实现覆盖）。注：早期设备上那 16 条 warning 实为 `/data/user/0 → /data/data` 祖先符号链接误判，已在 2026-09-24 修复，修复后设备侧无警告
- [x] 清理每 250ms 最多一次 UI 更新（当前按模块上报进度，低于该频率）
- [x] 列表滚动无明显掉帧（扫描/删除均在后台线程）
- [x] 自动清理不阻塞应用启动（延迟 30 秒）
- [x] 低空间设备上不会同时发起多个清理任务（全局单任务锁；`CacheCleanupManager.isRunning()` 拒绝并发）

### 20.4 兼容性验收

- [x] Android API 24 到当前 compileSdk 可用（minSdk 24，API 24/29+ 分支编译通过）
- [x] 移动端和 TV 端布局正常（LIO-AN00 与 NX627J 实机验证控件完整可见）
- [x] 横竖屏切换不崩溃（NX627J 旋转设置切换后弹窗完整、无崩溃）
- [x] 深色/浅色主题可读（NX627J night yes/no 切换后控件完整、无崩溃）
- [x] 中文简繁体和英文文案完整（三套 strings 均已补齐并通过资源合并）
- [x] 遥控器可完成全部清理流程（NX627J 方向键+确认完成清理与结果闭环）；清理运行中 BACK 只请求取消、不关闭面板，空闲时 BACK 正常逐层返回
- [x] 播放器切换不影响其他模块统计（EXO→MPV 前后模块统计逐项一致，且切换后 MPV 可正常播放）

### 20.5 回归验收

- [x] 收藏、历史、设置不变（清理仅访问 cacheDir；设备实测 `tv` 主数据库内容文件未被改写）
- [x] WebHome 扩展配置不变（清理前后偏好行哈希一致）
- [x] MPV 自定义配置不变（清理前后 `mpv.conf`/`fonts.conf` 哈希一致）
- [x] 播放、seek、预载、重缓冲不变差（180s 测试媒体实测播放推进、seek 后缓冲持续增长、播放中标准清理不中断）
- [x] 歌词、字幕、K 歌缓存可用（5563 实测歌词多源检索链路与空结果降级正常；外挂 SRT 实际渲染中文台词；K 歌/歌词模块统计正确且 K 歌模块清理只删除自身轨道文件、保留其他模块文件）
- [x] EPG 直播节目单可刷新（本地 HLS 直播源实测 `GET /epg.xml 200` 且 `cache/epg/epg.xml` 落盘、内容正确）；EPG 缓存保留策略亦实测（过期删除、今日保留）
- [x] 备份/恢复已实测通过（`bak-20260924-2028.zip` 生成成功、恢复 `shared=50 app=3` 成功）
- [~] 应用更新的**缓存契约**已实测：`Updater` 目标为 `cache/update.apk`，进行中该项在清理中被显式保护（`CacheTempFilePolicy` + `Updater.isDownloading()`），过期临时包正常淘汰。完整“检查更新→HTTPS 下载→安装器交互”闭环需真实 HTTPS 发布包与人工确认安装，本地环境（私网被 `ApkUrlPolicy` 拒绝、无线上发布包、安装器需交互）无法完成

### 20.6 设计与实现的已知偏离（第 24 节第 1 条要求逐项记录）

| 设计条目 | 设计建议 | 当前实现 | 说明 |
|---|---|---|---|
| §11.2 模块上限档位 | 分模块档位（EPG 仅 128/256/512；插件 512MB/1GB/2GB；临时文件 6/24/72 小时） | 统一档位 `0/64/128/256/512/1024 MB`，`CacheLimitOptions` 单一来源 | 统一档位可减少三套 UI 文案与测试矩阵；不同模块只体现在默认值（`CachePolicyStore.defaultLimit`：图片/歌词/K 歌/WebHome/插件 256MB，EPG/临时/遗留 128MB） |
| §11.2 自动清理默认值 | 默认开 | 默认关（`isAutoCleanupEnabled` 默认 `false`） | 自动删除缓存属破坏性行为，默认关闭、由用户显式开启更安全；开启后启动/周期/低空间触发均生效 |
| §11.2 触发条件可配置 | 可选「仅空间不足/每周/启动时」 | 固定策略：启动延迟 30s + 每 7 天 + 低空间连续两次 | 当前 UI 只暴露开关与保留期限，没有触发条件选择项；固定策略覆盖三类触发但不支持用户裁剪 |
| §11.2 临时文件保留期 | 可选 6/24/72 小时 | 固定 24 小时（`TEMP_RETENTION_MS`），另受模块字节上限约束 | 临时文件按 24h + 上限双重约束，未暴露独立保留期配置 |
| §14.6 空状态/边界文案 | 独立文案（暂无缓存/空间充足/空间紧张等） | 复用更紧凑的等价展示（`共 X / 系统配额 Y`、`部分结果 · N 项警告`、模块行「无 · 0 个文件 · 最近 无」） | 信息等价但文案不完全一致；界面密度更适合 TV 大屏 |
| §23.1 功能开关 | `cache_mgmt_enabled` 开关用于回滚 | 已实现 `CachePolicyStore.isManagementEnabled()`（默认开）；关闭时设置页恢复旧行为：只显示 `FileUtil.getCacheSize()` 并点击执行旧的一键 `FileUtil.clearCache()` | 设备实测：关闭开关后设置页显示 `60.3 MB`（无系统配额后缀），点击缓存行不弹新面板、直接全清并回显「无」，cache 目录降至 4KB；随后已恢复默认开启 |
| §11.2 插件上限自动淘汰 | 插件模块上限应能自动淘汰超限文件 | 插件项上限可配置并持久化，但**不参与自动淘汰**：`CachePolicyEngine.directCleanupStatus` 对 `PLUGIN_SCRIPTS` 恒返回 `NOT_ALLOWED`，UI 将插件「清理」按钮显示为「由模块管理」并禁用；`applyConfiguredLimits()` 也不淘汰插件文件 | 这是有意的安全决策：暂停/卸载运行中插件尚未实现，后台自动删除 jar/py/js 可能导致插件突然失效或播放回归（见 §P2C 记录）。设备实测确认：放入 80MB `cache/js/*.js` 并将插件上限设为 64MB 后执行深度清理，插件文件仍被保留（清理只释放其他模块的 8.7MB/56 文件），符合「宁可保留也不误删」的取向。若未来实现运行中插件暂停，再开放该项自动淘汰 |
| §23.1 兼容入口 | 保留 `FileUtil.getCacheSize()` / `clearCache()` 直到功能稳定 | 两个方法仍保留在 `FileUtil` 中；面板入口不再调用它们，但设置页缓存行的**长按**会以 `CacheCleanupMode.FULL` 复现拆分前的「一键全清」效果（功能开关关闭时点击仍走 `FileUtil.clearCache()`） | 满足 §24 第 6 条「明确标注为兼容入口」；如需回滚到旧行为可直接接回调用方 |

上述偏离不影响 §20.1-§20.5 的功能与安全验收；如需回到设计原文的分模块档位、可选择触发条件或默认开启自动清理，应作为独立增量需求实施。

## 21. 日志与可观测性

建议事件：

```text
cache.inventory.start
cache.inventory.module
cache.inventory.done
cache.cleanup.plan
cache.cleanup.start
cache.cleanup.progress
cache.cleanup.skip
cache.cleanup.done
cache.cleanup.partial
cache.cleanup.failed
cache.auto.trigger
cache.auto.done
cache.limit.clamped
```

日志要求：

- 不打印用户 URL、token、cookie、文件内容
- 路径最多打印模块 ID 和根目录名
- 记录 bytes、count、duration、state、skip reason
- 诊断开关关闭时只保留必要结果，不做高频日志

## 22. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 把 cacheDir 当全局 LRU | 删除状态/临时文件 | 模块白名单 + 显式根路径 |
| 活跃播放文件被删 | 卡顿/崩溃 | owner lease + idle 检查 |
| Exo 上限变更不生效 | 用户误解 | pending 状态 + 明确提示 |
| 系统 quota 低于用户设置 | 实际值不一致 | 显示有效上限和 clamp 原因 |
| Glide 运行时修改上限 | 单例无法安全重建 | 下次进程启动生效 |
| OkHttp cache 被外部删除 | journal 不一致 | 使用 `evictAll()` |
| 扫描大目录卡 UI | ANR/掉帧 | 后台扫描 + 超时 + 分模块 |
| 自动清理过于激进 | 首次加载变慢 | 默认 L1/L2，禁用 L3 |
| API 24/29+ 存储差异 | 兼容性问题 | 设备矩阵 + StorageManager 降级 |
| 多模块路径重叠 | 重复统计/删错 | 启动时校验根路径互不包含（已实现 `CacheModuleRegistry.validate`） |

## 23. 回滚策略

### 23.1 代码回滚

- P0/P1/P2/P3 每个阶段独立提交。
- 新入口使用功能开关 `cache_mgmt_enabled`。
- 回滚时关闭功能开关，恢复原设置页行为。
- 保留原 `FileUtil.getCacheSize()` / `clearCache()` 兼容入口，直到功能稳定。
- 不修改播放器容量 key，回滚后播放器配置仍有效。

### 23.2 数据回滚

- 新增配置全部使用 `cache_mgmt_` 前缀。
- 删除新配置不影响旧设置。
- 旧缓存目录结构不迁移，避免双向迁移风险。
- 清理动作不可逆，因此每个清理计划必须持久化最近一次结果用于诊断。

### 23.3 阶段回滚点

| 阶段 | 回滚方式 |
|---|---|
| P0 | 恢复设置页入口，移除新页面 |
| P1 | 关闭清理按钮，仅保留统计 |
| P2 | 停用模块上限，恢复默认值 |
| P3 | 取消 worker，保留手动清理 |
| P4 | 关闭 idle 重建增强 |

## 24. 实施完成定义

只有同时满足以下条件，才能宣称缓存管理升级完成：

1. 设计与最终代码一致，任何偏离都在文档记录。
2. P0 到 P3 的功能全部实现；P4 如果因设备能力暂缓，必须明确列为未完成。
3. 单元测试、集成测试、构建和至少一个手机 + 一个 TV 场景通过。
4. 播放中清理、低空间、系统 quota 三个边界场景有实测证据。
5. 文档记录的验收标准逐条勾选。
6. 旧全量清理入口不再暴露给用户，或明确标注为兼容入口。
7. 没有未解决的 P0/P1 级缺陷。
8. 提交、tag、回滚锚点、验证证据完整。

## 25. 推荐实施顺序摘要

```text
P0 只读统计 + 设置页可视
P1 模块清理 + 三级清理
P2 模块上限 + Glide 配置
P3 自动清理
P4 运行中治理增强
```

不要先做“全局最大缓存值”，也不要先做“全局 age-based 清理”。先把归属和 owner 做清楚，再开放上限和自动清理。

## 26. 参考资料

- Android app-specific storage: https://developer.android.com/training/data-storage/app-specific
- Android StorageManager: https://developer.android.com/reference/android/os/storage/StorageManager
- Media3 SimpleCache / LRU evictor: https://developer.android.com/reference/androidx/media3/datasource/cache/LeastRecentlyUsedCacheEvictor
- Glide caching: https://bumptech.github.io/glide/doc/caching.html
- Glide configuration: https://bumptech.github.io/glide/doc/configuration.html
- OkHttp Cache API: https://square.github.io/okhttp/
- 当前项目代码：
  - `app/src/main/java/com/fongmi/android/tv/utils/FileUtil.java`
  - `app/src/main/java/com/fongmi/android/tv/player/cache/DiskCacheCapacityPolicy.java`
  - `app/src/main/java/com/fongmi/android/tv/player/exo/MediaSourceFactory.java`
  - `app/src/main/java/androidx/media3/mpvplayer/MpvHlsCacheCoordinator.java`
  - `app/src/main/java/com/fongmi/android/tv/player/lyrics/LyricsRepository.java`
  - `app/src/main/java/com/fongmi/android/tv/player/karaoke/KaraokeTrackRepository.java`
  - `app/src/main/java/com/fongmi/android/tv/web/WebHomeRawAdapter.java`
  - `app/src/main/java/com/fongmi/android/tv/api/parser/EpgParser.java`
