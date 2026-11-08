# C117-beta-merge-review-dev4-20261103

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev4`；评审 `dev4` 已提交未推送的代码（含合并提交）；
  发现问题即修复并验证；复评直至通过；然后提交、推送 `dev4`、创建到 `beta` 的中文 PR，最后拉取远端最新代码。
- **验收标准**：
  1. 合并后 `origin/beta` 成为 HEAD 祖先（落后 0），无冲突、无夹带评审外改动。
  2. 评审覆盖 `origin/beta..HEAD` 中 dev4 独有的全部 92 个路径（15 个提交，含 1 个合并提交）。
  3. 第一轮评审发现的必修项已修复；第二轮复评通过；双 flavour 编译通过；plugin + cache 单测零失败。
  4. 改动提交并打 recovery tag；推送 `dev4`；创建到 `beta` 的中文 PR；随后拉取远端最新代码。
- **Lane**：`upstream`（`app` 两侧 flavour + `docs`）
- **任务守卫**：**未执行**。本机 `bash` 通道不可用（`bash -c "echo ok"` →
  `Bash/Service/CreateInstance/CreateVm/HCS/HCS_E_SERVICE_NOT_AVAILABLE`），`.codex/scripts/task_guard.sh` 无法启动。
  改用等效手工流程：只暂存任务自有路径 + 手工打 recovery tag。该限制与 C105/C108/C111/C113 记录一致，非本轮引入。
- **当前状态**：合并、两轮评审、必修项修复、双 flavour 编译与单测全部完成；交付坐标见 §8。

---

## 1. 合并对象与结果

| 项 | 值 |
| --- | --- |
| 合并目标 | `origin/beta` = `954527e69e790030034b46cc59d7e2ac12597fbe`（"Merge pull request #517 from Silent1566/dev1"） |
| 合并前本地 | `dev4` 领先 `origin/dev4` 14 个提交（插件框架 P1–P10） |
| 冲突预检 | `git merge-tree --write-tree HEAD origin/beta` → 只输出单一 tree 对象 `ef090866f466…`，**无冲突** |
| 合并动作 | `git merge --no-ff origin/beta` → **零手工干预** |
| 合并提交 | `379fbcdd5aa26759b79e4692837f02eb3093f10e`（"Merge remote-tracking branch 'origin/beta' into dev4"） |
| 祖先关系（合并后） | `git merge-base --is-ancestor origin/beta HEAD` → **true**；`git rev-list --left-right --count origin/beta...HEAD` = **0 / 15** |
| merge-base（合并后） | `954527e69e790030034b46cc59d7e2ac12597fbe`（= `origin/beta`，即 beta 已完全包含） |
| 合并工作区状态 | `git status --porcelain` = 空 |

## 2. 评审面判定（为什么本轮必须重新评审）

评审面 = `origin/beta..HEAD` 的 **15 个提交 / 92 个路径 / +16044 −6**。15 个提交是：

```
379fbcdd5aa Merge remote-tracking branch 'origin/beta' into dev4
1e4f7702dae feat(plugin): cache plugin http responses and enrich douban comments
5c686202c82 feat(plugin): bind slots to page surfaces and let chips be dragged
a7afb036215 feat(plugin): seed the bundled douban sample on first launch
a47412d08fa feat(plugin): persist the audit trail and wire panel playback pause
7b0e96a964f feat(plugin): make panel slots reachable by remote on leanback
9c1f6257492 feat(plugin): 豆瓣评论本体——episode 上下文注入与 panel 面板渲染
43a14b71aa1 feat(plugin): add per-plugin params and controlled net.request bridge
45b0d5eb1df feat(plugin): add plugin manager page with package import
e0a91150aa4 feat(plugin): wire slot clicks to runtime and add toolbar chips
a56e547e6dc feat(plugin): P2d floating slot rendering and controller assembly
ad308c99dc1 feat(plugin): P2c QuickJS runtime adapter with lazy native init
83e86b03654 feat(plugin): P2b runtime abstraction and host routing with fault isolation
b1eedb39371 feat(plugin): P2a manifest, capability gate and audit log
6527059eb34 feat(plugin): P1 native host event bus with zero-subscriber no-op
```

**判定为「不可跳过」的证据**（用户允许跳过的前提是「之前的评审已覆盖当前所有修改的代码」）：

1. 把这 14 个非合并提交的 SHA 全仓 grep，`docs/` 下 **零命中** —— 没有任何评审文档引用过这些提交。
2. `docs/` 下最新一份同分支评审文档是 `C113-beta-merge-review-dev4-20261030.md`（287 行），时间早于全部插件提交。
3. 作者自己的 `docs/PLUGIN-01-global-plugin-framework-20261030.md` 是设计稿 + 自述实施记录，属**自我背书**，不是独立评审。

结论：本轮评审必须覆盖全部 25 个插件类 + 全部集成点。

## 3. 第一轮双轴评审（Standards 轴 + Spec 轴并行）

- **Spec 轴**（对照 `docs/PLUGIN-01…md` 逐节核实现状）：11 项 MISSING、2 项 SCOPE CREEP、2 项 DEVIATION，另 12 条明确成立。其中**绝大多数是作者已在文档里写明的阶段性延后**（page 整页槽位、py/html 运行时、订阅通道 `plugins[]`、`targets[]` 与 `ui[].leanback` 覆盖块、`reservedNames`/`staged`、插件设置页审计入口），属**范围**而非缺陷；真正的缺陷形状项被并入下面 §4。
- **Standards 轴**（逐文件读完 25 个插件类 + 全部集成点 + 三语 `strings.xml`）：**2 项硬违规 + 10 项判断性问题**。
  - **H1**：`QuickJsPluginRuntime` 调用超时后只置 `closed`，不销毁上下文、不关执行器 → 原生求值线程与上下文一起泄漏。
  - **H2**：`PluginNetBridge` 三处审计的 `pluginVersion` 恒为 `null`，而 `PluginHost` 同路径传的是真实版本；D2 用「全量审计」替代域名白名单后，审计是唯一观测面，`null` 版本等于无法归因到具体构建。
  - M1–M8 / L1–L3：`PluginHost` 的两处重复遍历、`PluginUiPresenter` 的两套槽位判定、`headers` 裸 JSON 串被解析三次、`PluginAuditLog` 五职责超长、两端 `VideoActivity` 约 40 行近似重复且行为已漂移、`PluginCapabilityRegistry` 有索引却仍线性扫描、`PluginManifest` 重复白名单与便利构造器、超时钳制分裂三处、`PluginManifest`/`PluginAuditLog` 超 500 行门禁、`docs/PLUGIN-01…md` 头部状态过期且缺 Recovery anchor。

## 4. 第一轮修复（S1–S5 + 调度方两处自改）

用户的三个决定（授权边界）：**Q1 真拆分**两个超长类并消除 M1–M4；**Q2 补齐** `episode.changed` + `playback.*` 最小接线，硬要求「空订阅零行为」；**Q3 遗留项只记台账**、不算缺陷。

### 4.1 结构拆分（Q1）

| 改动 | 结果 |
| --- | --- |
| `PluginAuditLog` 542 → **279** 行 | 拆出 `PluginAuditCodec`（126 行，落盘 JSON 编解码）、`PluginAuditWriter`（246 行，落盘调度与版本仲裁） |
| `PluginUiPresenter` 655 → **382** 行 | 拆出 `PluginFloatingDock`（388 行，悬浮入口挂载与拖动）、`PluginChipFactory`（87 行，chip 构造与配色） |
| M1（`PluginHost`） | 新增 `findSlot`（唯一槽位查找）与 `interested`（唯一订阅判定），`onEvent`/`slotOf`/`slotOfAction`/`subscribersOf` 全部改为薄壳 |
| M2（`PluginUiPresenter`） | `landingOf` 单一槽位判定，`attach` 与 `countRenderable` 共用；`identityOf` 单一标识，`tagOf` 与 `positionKey` 只差前缀 |
| M3（`PluginNetBridge`） | 收敛为「解析一次、向下传」的私有重构；**公共 6 参 / 7 参签名与 `blocked` 未变**（被两个测试类约 34 处直接钉住） |
| M4（`PluginAuditLog` 五职责） | 随 4.1 的拆类一并消除 |
| M6（`PluginCapabilityRegistry`） | 新增 `BY_EVENT` 反向索引，`permissionForEvent` 走查表，`putIfAbsent` 复刻「遍历顺序第一条覆盖」语义 |
| M7（`PluginManifest`） | `isSupportedRuntime` 委托 `PluginRuntimeFactory.isKnownApi`，删掉清单侧 case 列表 |
| M8（超时钳制） | 收敛到 `PluginConfig.timeoutOf`（钳 1..`MAX_TIMEOUT_SECONDS`）；`PluginManifest` 的第三份字面量 `60/15` 改为引用同一常量 |

### 4.2 事件接线（Q2）

- `PluginEventBus` 新增 6 个事件常量（`playback.ready` / `playback.playing` / `playback.state` / `playback.pause` / `playback.resume` / `episode.changed`）与 4 个 payload 键（`activity` / `playing` / `state` / `episode`）。
- `PluginHostEvents` 新增对应 6 个 helper；**每一个的 `hasSubscribers` 短路都严格早于任何 payload 构造**。
- 埋点位置（`PlaybackActivity`）：`playbackReady` :1358（`onServiceConnected` 收口内）、`playbackPlaying` :1194、`playbackState` :1215、`playbackResume` :1388、`playbackPause` :1396。前两者刻意放在 `isOwner()` 早退**之前**，因为非 owner 页面同样在播。
- `episode.changed`：leanback `VideoActivity:3363`、mobile `VideoActivity:2864`，**两端各埋一次**（§4.3:217 硬要求），且与 `PluginEpisodeContext.publish` 共用同一次 `PluginEpisodePayload.of` 结果，避免「事件读 A、点击读 B」的评论串台。
- `PluginCapabilityRegistry`：`plugin.events` 事件集加入 5 个 `playback.*`；`plugin.data.episode` 加入 `episode.changed`。**这两个登记是新事件能派发的前提** —— `PluginManifest.events()` 靠 `allowsEvent → permissionForEvent` 过滤，未登记的事件永不派发。
- `app/src/main/assets/plugin/README.md`：为第三方插件补事件表与取值口径（`playing` 只有 true/false 且来自播放器回调；`state` 是 Media3 `STATE_*` 十进制；`pause`/`resume` 是页面可见性语义）。

### 4.3 审计补强

- `Entry` 末尾新增 `bodySize`（§8.1:330 要求「请求体大小」），序列化键 `bodySize`，旧快照缺键回落 `0L`。
- 新增 9 参 `recordRequest(…, bodySize)`；8 参保留并委托传 `0`。
- `PluginNetBridge` 新增 8 参 `request(…, pluginVersion, …)`，三处审计（缓存命中 / 成功 / 异常）与 `blocked` 全部带真实 `pluginVersion` + `requestBytes(verb, body)`。
- 版本透传链逐跳核实：`PluginConfig.create` 写信封 `version` → `versionOf` 读 → `QuickJsPluginRuntime.inject` 闭包捕获 → `netRequest` → `PluginNetBridge`。

## 5. 第二轮双轴复评与追加修复

### 5.1 Standards 轴（拆成「核心」与「UI/事件」两个窄任务重派）

- **核心半区**：判定 **1 阻断 + 3 应修**。
  - **B1（阻断，且是第一轮修复自己引入的）**：`QuickJsPluginRuntime` 超时分支在**调用线程**调 `close()`，而 `close()` 从该线程直接 `QuickJSContext.destroy()`。`TimeoutException` 的语义正是「任务仍在会话线程跑原生 eval」，因此这是跨线程销毁一条仍在执行中的 native 上下文 —— 直接违反本类自己声明的不变式，`SIGABRT` 级。
  - M1/M2：`close()` 的读-置空两步非原子 + 创建后复查分支自己再 destroy 一次 → 两条独立的双销毁路径。
  - M3：`PluginCapabilityRegistry:55-57` 注释与代码矛盾。
- **UI/事件半区**：判定 **2 应修**，无阻断。
  - S-1：`episodeChanged(this, context == null ? "" : context.toString())` 的 `toString()` 是**实参**，在进 helper 前就求值 → 无订阅者时仍序列化整份上下文，违反 §7:311「空订阅即零行为」门禁，且紧邻注释是失真的。
  - S-2：README 事件表用 `"PlaybackActivity"` 作示例值，但 `activityName()` 取 `getClass().getSimpleName()`，那是抽象基类，该值永不出现 → 第三方插件照此过滤永远收不到事件。

### 5.2 追加修复

| # | 文件 | 改动 |
| --- | --- | --- |
| B1+M1+M2 | `QuickJsPluginRuntime` 418 行 | 销毁一律只在会话线程：`close()` 退化为「置位 + 两参摘表 + 把销毁投递到会话线程 + `shutdown()`」，任务体 `finally { if (closed) destroyOnSessionThread(); }`，删掉复查分支的二次 destroy，`submit` 的 `RejectedExecutionException` 转成同一 ISE 契约异常 |
| S-1 | `PluginHostEvents` 187 行 | 新增 `episodeChanged(Activity, JsonObject)` 重载：`hasSubscribers` 短路严格早于 `toString()`，短路后委托给 String 版（保持单一发射点）；两端 `VideoActivity` 改传对象并改正注释 |
| S-2 | `assets/plugin/README.md` | `playback.*` 示例值改 `VideoActivity`；过滤提示从 `ui.contentShown` 扩展到 `playback.*` |
| N-1 | `PluginUiPresenter` | `detach()` 的焦点链还原改正序为**倒序**：同一控件被多次登记时，正序会让「指向已移除 chip 的 id」成为最终值 |
| M3 | `PluginCapabilityRegistry` 166 行 | 注释改对：`episode.changed` 只由 `plugin.data.episode` 覆盖。**未把 `episode.changed` 加进 `plugin.events`** —— 那会让只申请生命周期权限的插件拿到含片名/集列表的完整剧集数据，属越权 |
| S1 | `PluginManifest` 396 行 | 第三份超时字面量 `60/15` 改为 `PluginNetBridge.MAX_TIMEOUT_SECONDS` / `DEFAULT_TIMEOUT_SECONDS`（本就包可见，无需放宽） |
| S2 | `QuickJsPluginRuntime` | `submit` 的 `RejectedExecutionException` 转 ISE，不再以线程池内部文案逃出契约 |
| S3 | `PluginAuditLogTest` 498 行 | 新增 `bodySizeSurvivesReload`（落盘回读 `12L`）与 `legacySnapshotWithoutBodySizeFallsBackToZero`（缺键回落 `0L`）；已有测试一行未改 |
| S5 | `PluginAuditLog` 279 行 | `Entry.pluginVersion` 补 `@Nullable`，与 `event`/`method`/`outcome` 一致 |
| L1 | `docs/PLUGIN-01…md` 1768 行 | 头部状态从「未进入实施 / HEAD `248cdfe111d`」改为「P1–P10 已实施并合入 `dev4` / HEAD `379fbcdd5aa`」；文末补 `Recovery anchor · 恢复锚点`（目标、验收标准、当前文件与符号、未解决风险、回滚锚点、下一个动作），满足 AGENTS.md §5 的恢复锚点要求 |

**接受的设计代价（B1 的方向性取舍）**：销毁只能在会话线程发生，因此「脚本死循环超时后永不返回」这条路径上的上下文**不会被销毁**（销毁任务排在永不空闲的单线程队列里），会话线程作为 daemon 永久存活但不阻塞进程退出。**确定性泄漏优先于不可恢复崩溃** —— 这是 QuickJS 原生 eval 不可中断换来的代价，只能靠进程重启回收。该取舍已写进 `close()` 与 `destroyOnSessionThread()` 的 javadoc。

**明确不修（记台账）**：`PluginNetBridge.requestBytes` 对 ≤256KB 有界载荷多编码一次；三处 `catch Throwable` 无日志；`playback.playing` payload 无 `owner` 键（多播放页各发一条）。

### 5.3 第三轮 Spec 轴复评（窄任务）与最后一处缺口

第二轮 Spec 轴原始派发无产出（该 brief 记第 1 次运行），改为只回答 5 个判据的窄任务重派，第 2 次运行交付：

| 判据 | 结论 | 依据 |
| --- | --- | --- |
| Q1 §7:310 行数门禁 | **通过** | `plugin/` 29 个类全部 ≤500，最大 `PluginPackageImporter.java` 446；`PluginUiPresenter` 655→382、`PluginAuditLog` 542→279 |
| Q2 §7:311 空订阅零行为 | **通过** | `PluginHostEvents.java:63/:114/:139/:174/:184` 短路全部严格早于 payload 构造；`PlaybackActivity.java:1194/:1215/:1358/:1388/:1396` 只传 `this` + 原始值 |
| Q3 §4.3:205-219 事件表一致性 | **通过** | 事件名 `PluginEventBus.java:55/57/59/61/63/77`、payload 键 `:79/:82/:84/:86`；`episode.changed` 埋在 `getPlayer` 漏斗（`mobile VideoActivity.java:2864` / `leanback VideoActivity.java:3363`）而非 `onItemClick`，覆盖比文档要求的更宽，判一致 |
| Q4 §8.1:326-333 审计 | **通过** | `bodySize` 全链通；`pluginVersion` 真通；`clear()` 生产调用点仍只有 `CacheCleanupManager.java:461` |
| Q5 是否夹带范围外改动 | **1 项待裁定** | 指 `QuickJsPluginRuntime` 的 `Session`/`close` 生命周期重写不属授权四类 |

**调度方裁定**：

- Q5 的「越界」**不成立**。那段重写正是修第二轮评审判定的 B1 阻断项（第一轮修复自己引入的跨线程 `destroy`），属于「修复+验证+再评审」循环内的必修缺陷，不是夹带新功能。
- **S1 成立**：`docs/PLUGIN-01…md` 的 Recovery anchor 写「未验证的工作区改动：无」，与当时工作区 20 个未提交文件矛盾。已改为如实描述（收口后无未验证改动，并注明此前轮次的状态与验证出处）。

**本轮最后一处真缺口（调度方自查发现，已修）**：§8.1:330 与 D2 裁定（`docs/PLUGIN-01…md:322`）都要求审计字段含「触发事件」，但 `PluginNetBridge` 三处 `recordRequest` 长期把 `event` 传 `null`（网络桥是静态工具，不知道当前在跑哪个事件/动作）。修法是把触发者沿运行时透传下去，**不改任何既有公共签名**：

| 文件 | 改动 |
| --- | --- |
| `QuickJsPluginRuntime` | `Session` 新增 `trigger` 字段与 `trigger()`/`enterTrigger()`/`exitTrigger()` 三个包内方法（只在会话线程读写，故意不加 `volatile`：跨线程可见性反而会让一条请求读到别的调用留下的触发者）；`dispatch` 在 `fn.call` 前 `enterTrigger(key)`、`finally` 里 `exitTrigger()`；`init` 的求值段包在 `enterTrigger(TRIGGER_INIT)` / `exitTrigger()` 之间（脚本顶层发起的请求归因为 `runtime.init`）；`netRequest` 改为接收 `session` 并读 `session.trigger()` |
| `PluginNetBridge` | 新增 9 参 `request(…, event, …)` 为唯一实现，原 8 参变一行委托传 `null`；新增 6 参 `blocked(…, event, …)` 为唯一实现，原 5 参委托传 `null`；缓存命中 / 成功 / 异常 / blocked 四处审计全部带 `event` |

旧调用面（6 参 `request`、7 参 `request`、4 参 / 5 参 `blocked`）一个签名都没动 —— `PluginNetBridgeTest` 与 `PluginHttpCacheTest` 约 34 处直接调用它们。`PluginAuditLogTest.java:252` 的 `assertNull(entry.event())` 也不会挂：该断言所在的 `entriesSurviveReload()` 自己就传的 `null` event。

### 5.4 第四轮：迟到 Spec 轴完整报告的 3 条硬违规

第二轮 Spec 轴的原始派发先报 failed、后迟到交付完整报告（同一 brief 的第 1 次运行）。它给出 14 条判定：11 条真解决、3 条部分解决、无「引入新问题」；并额外判 3 条硬违规。三条全部核实为真并已修：

| # | 违规 | 依据 | 修法 |
| --- | --- | --- | --- |
| **V1** | 新接线的 `playback.playing`/`playback.state` 链路在**调用线程**同步跑脚本 | `PlaybackActivity.java:1194/:1215` → `PluginHostEvents:88-90` → `PluginEventBus.emit:151-165`（同步遍历）→ `PluginHost.onEvent`（无 executor）→ `QuickJsPluginRuntime.dispatch:227` → `Session.withLock` 的 `future.get(5s)`。只要插件订阅，每次播放/暂停/seek 都能冻主线程最多 5 秒。同一补丁对槽位动作已规避（`PluginController.java:62-73` 的 `actions.execute`），事件路径没对齐 | `PluginHost` 新增懒创建的 `plugin-events` 单线程 daemon 执行器：主线程上的 `onEvent` 只入队，`deliver` 在线程内重读 `plugins` 后再逐个派发；非主线程仍同步走（阻塞后台线程无代价，绕队列只会增大观测延迟） |
| **V2** | chip 自身的 `nextFocusDown` 从未生效 | `PluginChipFactory.java:45` 原用 `new TextView(new ContextThemeWrapper(context, R.style.Control))`；`ContextThemeWrapper` 只换 theme，不会把 style 里的 `android:*` 视图属性套到程序化 View 上，leanback `Control` 提供的 `focusable`/`focusableInTouchMode`/`nextFocusDown=@id/timeBar`/`background=@selector_chip`（`app/src/leanback/res/values/styles.xml:80-89`）全部落空。上一轮宣称修焦点链，实际只修了 up 半条 | 改为四参构造 `new TextView(context, null, 0, R.style.Control)`（defStyleRes），与仓库内唯一被验证过的同款用法 `TmdbDetailActivity.java:8846` 一致；`PluginUiPresenter` 类文档同步改口 |
| **V3** | 文档事实错误 | `docs/PLUGIN-01…md:1767` 写「本轮改动是单个提交，可用 `git revert` 一次撤回」，实际是 16 个已跟踪改动 + 5 个未跟踪文件、零提交；C117 §8 的改动清单漏了 C117 自身 | 回滚锚点改写为「未提交时用 `git checkout -- <path>`，已提交时逐个 `git revert <sha>`」；§8 改动清单补齐 README、两份 docs 与 4 个新增源文件 |

V1 的连带处理：

- 执行器**懒创建**。JVM 单测里 `Looper` 是桩、`isMainThread()` 恒假，事件一律同步走，线程永不创建 —— 因此 `PluginHostTest` 那 9 处「`emitActivity` 后立刻断言」的同步时序**一行都不用改**，实测全绿。
- `PluginController.reset()` 先调 `host.shutdown()` 再 `unloadAll()`：让队列里那些还指向旧运行时的闭包直接消失，而不是卸载后才被派发过去。`shutdown()` 置 `closed` 并丢掉执行器，此后 `events()` 返回 `null`、`onEvent` 直接返回，不会再起线程。
- 刻意**不放**进 `unloadAll()`：卸载之后还会重新装载，那时派发线程必须还在。

**已知代价（V1）**：插件看到事件比事件发生晚一个轮次。事件是「状态通知」而非「同步查询」，这个延迟可接受；反过来「脚本卡死冻界面」不可接受。

## 6. 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 双 flavour 编译 | `.\gradlew.bat :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac --console=plain` | **BUILD SUCCESSFUL in 10m 11s**，58 tasks（4 executed / 54 up-to-date），仅既有 deprecation/unchecked 警告 |
| plugin + cache 单测 | `.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.plugin.*" --tests "com.fongmi.android.tv.cache.*" --console=plain` | **BUILD SUCCESSFUL in 3m 20s**；汇总 XML：**SUITES=39 TESTS=321 FAILURES=0 ERRORS=0 SKIPPED=1** |

与 `docs/PLUGIN-01…md:1728` 记录的基线（SUITES=39 TESTS=319 FAILURES=0 ERRORS=0 SKIPPED=1）对比：用例数 +2，正是本轮新增的两条 `bodySize` 测试；失败/错误仍为 0。

**行数门禁（§7:310，全部 ≤500）**：PluginAuditCodec 126 · PluginAuditLog 279 · PluginAuditStore 210 · PluginAuditWriter 246 · PluginBootstrap 98 · PluginCapabilityRegistry 166 · PluginChipFactory 87 · PluginConfig 133 · PluginController 384 · PluginEnvironment 84 · PluginEnvironmentStore 162 · PluginEpisodeContext 85 · PluginEpisodePayload 83 · PluginEventBus 193 · PluginFloatingDock 388 · PluginHost 412 · PluginHostEvents 187 · PluginHttpCache 218 · PluginManifest 396 · PluginNetBridge 306 · PluginPackageImporter 446 · PluginRuntime 49 · PluginRuntimeFactory 139 · PluginSampleInstaller 311 · PluginScriptSource 61 · PluginSourceConfig 324 · PluginSurface 88 · PluginUiPresenter 382 · QuickJsPluginRuntime 418。

## 7. 遗留台账（本轮明确不实施，非缺陷）

| 项 | 出处 | 状态 |
| --- | --- | --- |
| page 整页槽位（D1 的第一版形态） | §4.4:228、§23.10:1750 | 未实施，`PluginUiPresenter` 只处理 toolbar/panel/floating |
| py / html 运行时 | §4.5:232、§5.4:281 | 只注册了 `js`（`PluginBootstrap:42`） |
| 订阅分发通道 `plugins[]` | D4 另一半、§16.8:974 | 未实施 |
| 清单 `targets[]` 与 `ui[].leanback` 覆盖块 | §4.2:182/190、§11.5:519 | 未实施 |
| `reservedNames` / `staged` 保护 | §8.3:394-395 | 未实施 |
| 插件设置页的审计查看/清理入口 | §8.1:333 | 未实施；当前唯一 `clear()` 调用点是 `CacheCleanupManager.java:461`（既有行为，本轮未新增越界调用） |
| 电视端焦点环人工验收 | §23.10:1755 | 需真机，非代码问题 |
| `net.request` 域名白名单重新评估 | §23.10:1750 | D2 已裁定「只审计不拦截」，`PluginNetBridge` 无域名/重定向/SSRF 策略 |
| `PlaybackEventCollector:14-15` 的 `PAUSE`/`RESUME` 与新增事件同名不同义 | 本轮发现 | 该通道是**播放器语义**（webhook），新增的是**页面可见性语义**；两者无代码耦合，已在各自 javadoc 写明区别 |
| `WebThemeRuntimeDiagnostics.safeUrl()` 无法复用（§8.1:331 要求复用脱敏规则） | 第一轮 Spec 轴 / §8.1:331 | 该方法在 `WebThemeRuntimeDiagnostics.java:107/111` 是**包内可见**（`static String safeUrl(...)`），插件包跨包不可调用；`PluginAuditLog.target()`（:246-258）自己实现同口径的 host+path、丢弃 query/fragment，行为一致但非同一份代码。要真正复用需把该方法放宽为 `public`，属主题侧公共面变更，本轮不动 |
| 禁用插件的日志来源标记（§8.1:333） | 第一轮 Spec 轴 | 现有实现已满足可追责：每条审计都带 `pluginId`+`pluginVersion`；且被禁用的插件根本不会装载（`PluginSourceConfig.enabledEntries()` :153-157 过滤），不会产生请求也就没有待标记的日志 |
| `PluginNetBridge.requestBytes` 重复编码 | 本轮评审 S4 | ≤256KB 有界，收益不抵改动风险 |
| `PluginFloatingDock` 三处 `catch Throwable` 无日志 | 本轮评审 N-2 | 现网几乎不可达（`Init.context()` 缺失的纯 JVM 场景） |
| `playback.playing` payload 无 `owner` 键 | 本轮评审 N-3 | 增键属对外契约变更；README 已写明多播放页各发一条 |
| `ExamplePluginManifestTest.readmeDocumentsEveryBusEvent` 只覆盖 5 个旧事件 | 本轮发现 | 新增 6 个事件未纳入该断言；README 已人工补齐，测试未扩（扩测试不在本轮授权面） |

## 8. 交付坐标

| 项 | 值 |
| --- | --- |
| 基线 HEAD | `379fbcdd5aa26759b79e4692837f02eb3093f10e`（合入 beta 后的状态，= 本轮回滚锚点） |
| 本轮改动 | 16 个已跟踪源文件 + `assets/plugin/README.md` + 2 份 docs（本文件与 `PLUGIN-01`）+ 4 个新增源文件（`PluginAuditCodec`/`PluginAuditWriter`/`PluginChipFactory`/`PluginFloatingDock`），代码部分 **+722 −810**（拆类后净减少） |
| 分支 | `dev4` |
| PR 目标 | `beta` |
| 评审提交 | `c026bae6e6a654cbc2673134fec63668f5fed8c9`（22 files changed, +1940 −823） |
| recovery tag | `recovery/C117-beta-merge-review-dev4-20261103/20261103231405`（指向上述提交） |
| 推送 | `git push origin dev4` → `248cdfe111d..c026bae6e6a  dev4 -> dev4`（含此前 64 个未推提交），exit 0；恢复标签同批推送成功 |
| Pull Request | **#519** → `https://github.com/Silent1566/webhtv/pull/519`，`dev4` → `beta`，state OPEN |
| 拉取远端最新 | `git fetch --all --prune` exit 0（`origin/beta` 前进到 `03958c154df`）→ 见 §9 的再次合并 |
| 终态一致性与后续 | 见 §9（合并提交 `42c7f865655`、推送与终态核对） |

## 9. beta 前进后的再次合并（2026-11-03）

PR #519 创建后 `git fetch --all --prune` 发现 `origin/beta` 已从 `954527e69e7` 前进到 `03958c154df`，新增 4 个提交：
`03958c154df`（Merge PR #518 from dev5）、`b10b9fff865`（docs(tv) 记录 beta 合并复评与全量回归结果）、
`5b24c6cfc4a`（Merge origin/beta into dev5）、`3e8cd7d3101`（fix(tv) 长按方向键在选集网格内持续推进），
改动 7 个路径（+219 −31）。为满足「合并远端 beta 最新代码」的交付目标，本轮再次合并：

- 冲突预检 `git merge-tree --write-tree HEAD origin/beta` → 只输出 tree oid `202e53279ffb5415891f23a168b70ea596569dc1`，无冲突；
- `git merge --no-ff origin/beta` → **`42c7f865655ba2169b8b906b16cfbb5ab63195a2`**；`Auto-merging` 仅涉及 leanback
  `VideoActivity.java` 与 `TmdbDetailActivity.java`，且落在互不重叠的区域（beta 改 `:501` 附近字段与 `:4695`/`:4749`
  的长按选集逻辑；本分支的 `VideoActivity` 改动在 `:3358-3367` 的剧集上下文发布）；合并后 `git status --porcelain` 为空；
- `git merge-base --is-ancestor origin/beta HEAD` → exit 0；`git rev-list --left-right --count origin/beta...HEAD` = `0 / 17`。

合并后复核（beta 的新代码若撞坏插件埋点必须当场发现）：

| 检查项 | 结果 |
| --- | --- |
| leanback `VideoActivity` 插件埋点存活 | `publishPluginEpisodeContext` `:2504` / `:3329`、定义 `:3358`、`PluginEpisodeContext.publish` `:3363`、`PluginHostEvents.episodeChanged` `:3367`、`republishPluginEpisodeContext` `:3384`、`onStart` 调用 `:9344` —— 全部在位 |
| mobile `VideoActivity` 插件埋点存活 | `PluginHostEvents.episodeChanged` `:2864` 在位 |
| `TmdbDetailActivity`（beta 改动最大）插件接线 | 10 处 `Plugin*` 引用全在（`:775` clear、`:3327`/`:3336`/`:3356` publish 与 republish、`:11498-11501` 返回本页重发），beta 未触碰这些区域 |
| 双 flavour 编译 | `.\gradlew.bat :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac --console=plain` → **BUILD SUCCESSFUL in 2m 14s**，58 tasks (4 executed / 54 up-to-date)，exit 0，仅既有 deprecation/unchecked 警告 |
| 插件 + 缓存单测 | `:app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.plugin.*" --tests "com.fongmi.android.tv.cache.*"` → **BUILD SUCCESSFUL in 1m 14s**；XML 汇总 **SUITES=39 TESTS=321 FAILURES=0 ERRORS=0 SKIPPED=1**，与合并前逐项相同 |
| beta 自带的 3 个用例 | 同 module 追加 `TmdbDetailActivityLayoutTest` + `VideoActivityLayoutTest` + `TmdbEpisodeGridPolicyTest` → **BUILD SUCCESSFUL in 1m 58s**，exit 0 |

结论：beta 增量与本分支的插件改动零路径冲突；合并后编译、插件/缓存单测、beta 自带用例三线全绿，
未发现新增缺陷，本轮无需再修复。
