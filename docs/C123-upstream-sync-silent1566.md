# C123 — Silent1566 最新改动合入 dev1

## Recovery anchor

- 目标与验收：完整合入本次 15 条上游提交，保留本地定制；所有冲突闭合、双端 Java 编译及风险对应单测通过，再由任务守卫生成单一合并提交及 annotated recovery 标签。不推送、不发布、不重写历史。
- 基线：`dev1@e00f1e862dfd4aef0051190cccc723377999ecc9`；源 `https://github.com/webhtv/webhtv.git` 分支 `Silent1566@fbe5fe91db9ba41fd380929ebc039a699dba463b`；merge-base `47454687f504955775b398905ba09d73aeb9f20c`。源已 fetch，检查日期 2026-11-09 Asia/Shanghai。origin 指向另一 fork，不能作为本次源。
- 状态：完整15提交已合入、7文件36冲突按并集闭合；run2完成风险定向验证（186/0/0/0）与两轴独立增量复审（新增必修0）。本记录随 C123 原子合并提交闭合；不推送、不发布。初始脏文件0，所有31任务路径受同一 upstream 守卫保护。
- 预演：`git merge-tree --write-tree --name-only` exit 1，7 个内容冲突：TV Cast/Live/Video、mobile Video、DanmakuApi、JsLoader、PyLoader；预演含标记树 `56344d33a6aaf083bd8864ae7ea0ae1a9b4ae198` 不是可交付树。
- 回滚锚点：上述 dev1 起点。交付后用 `git revert -m 1 <本次合并提交>` 撤销，禁止 reset --hard。守卫状态及原始日志保存在工作区；必须先独立验收再 finish。
- 唯一下一步：交付本地双父合并提交及 `recovery/C123/` 唯一 annotated 标签；提交由本记录所在 `Task-Guard: C123` 原子提交定位（避免在提交内容中自引用尚未生成的hash）。不推送；SDK28图像通道、真机/native和完整mobile UI仅在后续独立需求中验证。
- 最终独立判定：Spec run2 的S1/S2均ADDRESSED、新增必修0；Standards run2新增必修0。完整源审查加最终5文件增量审查、父源码/日志/XML/哈希核验共同支持放行，执行者自报不是唯一依据。所有已发现缺陷均已修复或经用户明确路由为范围外既有缺陷；历史run1阻塞段仅保留复盘，不是最新状态。
- 交付恢复检查：`app/build/c123-checkpoint-final-retry.log` 最终0 errors/0 warnings，识别C123最新检查点、完整源tip、恢复锚点与下一动作。首次5项假错误均因Git Bash PATH缺rg；仅本次检查进程使用DSH内置rg.exe修正调用路径，未改系统配置/依赖/仓库脚本，未弱化gate。

## 需求与边界

用户选择：合入当前 dev1；完整合并并保留本地定制；共享理解记录明确确认。默认仅本地 commit/tag，不允许 push、PR、发布、依赖升级或无关重构。

范围以 `.codex/task-state/current/scope` 的精确路径为准：上游 diff 的 29 个文件，加本文件和现有 2026-08-20 评估索引。保护初始脏路径：0。两组只读审查不修改源或 Git 状态，集成任务独占 merge/index，协调者只维护此文档及索引。

时间目标：2026-11-09 09:14 +08:00 起，审查约 6 分钟、集成约 7 分钟、验证收尾约 7 分钟，目标 09:34。估计不是任务或质量上限；偏离后停止可选探索，保留最短安全完成路径。

## 全量源提交清单

以下为 `e00f1e862dfd4aef0051190cccc723377999ecc9..fbe5fe91db9ba41fd380929ebc039a699dba463b` 的完整 15 条，均处置为完整纳入本次合并历史；文档/测试/merge 亦不省略。基线已包含 merge-base 以前的提交，不重复实施历史任务。

| 完整 commit | 内容 | 处置 |
| --- | --- | --- |
| `fbe5fe91db9ba41fd380929ebc039a699dba463b` | 推送前评审修复与回归覆盖 | 纳入；检查修复保留 |
| `9e070fe66b1bc9083a20b754e0bb772eaa1d80ac` | 上游窄 TV 修复集成 | 纳入 merge 历史 |
| `6229157f7821bbfc4dc762bf4c7a85a1c704a98f` | 154 条上游评估与文档修正 | 纳入文档；不重新调查 154 条 |
| `1d20dcf89d11a5974054acb224be5a0948c0baff` | 窄修复批次验证闭合 | 纳入文档 |
| `4a4907c859f09468e7624a1803e9e68c4e446323` | decode restore 归属 refresh 请求 | 纳入 |
| `3e39a08eb900b92579e90a68782e5ce18d519b9d` | mobile decode refresh 拒绝旧自动弹幕 | 纳入 |
| `95628e30c32fad7edd8921bfb15070c7d6a8a1f6` | 播放修复集成 | 纳入 merge 历史 |
| `1425d0118975ed9412cb85a688a8de9f0f6b3c10` | 自动弹幕结果绑定播放会话 | 纳入 |
| `c861fac058e89136a6a2be233262b532fb4ceee6` | 隔离 fallback 诊断错误 | 纳入 |
| `93d2217d00156a672f47d1405bb97dac5471ba16` | 代理测试仅属 leanback | 纳入测试 |
| `dc5f1e60c4f96f88f5183cbf11b50bf97f2c6b65` | TV seek 提交绑定当前会话 | 纳入 |
| `38d2670e10eb69fa8c00c3bbd8df48b07d98f1db` | 有界 HTTP STRM 指针解析 | 纳入 |
| `e81ae22a1cdc4d026d6d5ecbbb11b96ddc3e9103` | QuickJS 代理响应头安全兼容 | 纳入 |
| `d3a7298d5f3a6f76f5f2a256d4fd6f02ff7cecef` | crawler 初始化失败恢复 | 纳入 |
| `60f2e86e761b8411e56a86793ee55a2f0b9b67d7` | 窄修复任务索引 | 纳入文档 |

## 设计、证据与决策

共同问题：上游窄修复是否能与后续本地播放/字幕/加载保护组合，而不整文件回退？只调查本次冲突与直接消费者，复用上游任务文档已完成的研究；不会把上游测试绿等同本地集成绿。

| 方案 | 结论与代价 |
| --- | --- |
| 不合并 | 无新增风险，但不能满足用户要求，拒绝 |
| 整文件采用上游 | 会丢失 merge-base 以后的大量本地定制，7 个冲突已证明需判断，拒绝 |
| 三方合并并作最小本地适配 | 推荐；完整历史+本地保护，须逐冲突核对并风险定向验证 |

证据已冻结：Git 源/基线/merge-base、29 文件 diff、7 冲突预演；上游 `docs/TV-SMALL-FIXES-REVIEW-pre-push-fixes.md@fbe5fe91db9ba41fd380929ebc039a699dba463b` 的 12 条修复、两轮审查、81 个通过测试与真实未验边界（源码/测试 A，维护说明 B）。本次未改 native/ABI/lock/资源/依赖，因此 native supply-chain 重建、codec 性能论文及设备渲染基准不适用；不宣称改善解码性能。

具体两组设计评审尚在进行：必须落盘符号、调用/数据流、原始来源 URL/commit、适用性及限制后才能生产编辑。上游既有文档中仍适用的一手证据可复用；来源缺口必须如实记录，不能凭标题认定最佳实践。

## 设计门结论与证据（2026-11-09 09:18 +08:00）

两组只读评审已完成；协调者独立核对 BaseLoader:136-172 namespace 调用、mobile VideoActivity:5307-5354 的 requestId/generation/canApply/activePlaybackKey 守卫、Js/Py slot/clear/诊断，证实原样覆盖会回退已有契约。采用三方并集，拒绝整文件上游覆盖。现有源/测试及上游研究足以决定本次窄适配；不宣称全领域最佳实践研究、native 性能或设备行为已完成。

| 证据类 | 已读取的实际证据与版本 | 等级、适用性与限制 |
| --- | --- | --- |
| 上游源/提交/测试 | 本次 15 完整提交；最终 fbe5fe91db9ba41fd380929ebc039a699dba463b 与父 diff；SpiderInit/loader/STRM/proxy/seek/ownership 真实代码和测试 | A：决定最终实现，不以标题或中间树代替 |
| 官方平台/规格 | 上游唯一任务文档中已阅读的 Java21 CHM.computeIfAbsent/Throwable.addSuppressed/JLS14.20.3.1/URI，AOSP Handler/KeyEvent，RFC3986/9110/9112，Chaquopy PyObject.close | 复用 2026-11-08 源研究，本次 2026-11-09 读其记录，不冒充再次联网；只用于缓存失败不发布、取消、正文与头语义 |
| PR/issue/维护讨论 | TV-SMALL-FIXES-REVIEW-pre-push-fixes.md@fbe5fe91db9ba41fd380929ebc039a699dba463b 两轮评审；FongMi PR772 及源 ba2d0b8dc11a7b658ee5c5e7c490acf573c4d8bb；文档所列限制/未移植后续 owner/runtime | B：明确意图和回退修复；原 PR 测试、外网讨论的缺口仍保留，不能推导完整 native 保障 |
| 成熟相关项目源码/测试 | OkHttp61423f472da24e0ccc42b6a2c0863fb27932fea5 Response/Body，NanoHTTPD16ed41b22c2739838ec7cb0e6ba26d52f4c8db3e sender，Gson3ff35d6269894901ab8006258395aafc4b9765cd，Guava f06690fa3e874f65515e8fd338a74d636e2c792f Closer/test，Kodi d1a1d48c3cb3722d39264ffdd8132f755ffecd27 playlist | A/B：复用已记录研究，针对副本读取/发送器/清理/playlist；本地消费者优先，不盲取其整个实现 |
| 技术报告/论文/现场记录 | 上游五个 C-TV 唯一文档及 final review 的实测/字段记录；本地 subtitle/generation/namespace 的确定性源码证据 | 原记录可供参考，本次尚未实测；codec/算法/功耗论文与本次无算法/native 变更不适用；不存在可据此承诺设备性能收益的证据 |

主要源 URL/版本：FongMi/TV https://github.com/FongMi/TV/commit/123cc086159b0f13bf54bdf64bacbc259ca47c88（初始化回收），7d70ebdfe56997ae9bccca7539a413a6999a09e3（STRM），f1aa53e2526307fa67930dd1d0efb6fc3e4732a9（proxy），7bf115759881e27df819bb01a357f4d7094d295c / 51813cb611386ab3fc3fe388480cfb12d2670f67（seek），fa5a39261ba053174b3a2b100c4a1073e2bd39c0（弹幕）；共同参考最终4512b4c9f82534ff66acfde06ccdd68f35b074f0。8b499e0972b93f6173eac0e0414485c6b894294a/c1c470b84e8111b0660316e842a9e4cb18fd83af 属新 owner/runtime 后续，明确不移植。具体官方/成熟项目 URL 与摘录以本次完整纳入的五个 C-TV 任务文档为来源，不新增平行研究文档。

### 逐冲突与自动合并契约（集成任务的绑定需求）

行号属于固定基线或含冲突预演；执行者以实际符号为准，不机械照抄行号。范围不因大 hunk 花括号匹配而扩大。

- Cast onBackInvoked：先 cancelKeySeek，再保留 finishPlayback；不能换回 super 破坏停止本页播放。
- Live onPlayingChanged：保留 isPlaying 移除 mBufferingTimeout；追加真实暂停撤 seek，buffering false 不当暂停。showControl 保留 track 可见性/用户按钮并撤 seek。canSeek 不重新混入 dispatch(true)。保留普通 Live 左右 UP 切线路、数字 2000ms、未配对 UP 回调。
- TV/mobile getDetail：保留 getDetail(false)/boolean refresh 重载，invalidateAutoDanmaku、pending=null（TV 加 cancelKeySeek）进入实际重载。getPlayer 保留 plugin episode context/控制标题，并在发请求前失效 auto/pending。
- TV/mobile setPlayer：先撤旧 auto/seek；保留 null/finishing/详情未绑定/重复结果的 loading 收尾、mPlaybackRequestActive/mPlaybackPlayerStarted、音频/non-video redirect、初始 position、PlayerButtonSetting、SubtitleRestoreCoordinator/subtitlePlaybackSession、metadata、短剧队列。只在实际原 startPlayer 前取 manager/beforeTrace，start 后用完整 owned 标题链；拒绝 start 不搜。大 hunk 不能把查询插到 canApplyPlayerContentRequest 末尾，也不能丢本地所有辅助方法。
- quality：保留短剧队列失效、updateActionQuality、字幕 restore/session，追加 cancelKeySeek（TV）和实际 start 前后 trace/requery。
- TV next/prev：保留 boolean 返回、getAdjacentEpisode、倒序/末集/intro skip，只补 cancelKeySeek；不能替成上游 void/adapter next。
- kernel：保留 requestId/generation/isCurrent/canApply、activePlaybackKey、rememberPlayerKernel；加 auto invalidation、TV seek cancel、实际切换前后 trace/requery。A→B→A 旧成功/失败不能改变 B。
- TV onDecode/showControl/onReclaim：短剧失效与 seek cancel 并存；保留本地 audio stage 控制策略、canApplyPlayerResult 早退，不能盲取上游 hideControl/return。
- TV dispatchKeyEvent：保留菜单三个配置、speed、PlayerControlFocusHelper、episode 长按、audio stage、Back preview；接管输入时撤 seek。
- TV onCasted/onShare 已迁移到本地文件后部：不能新增重复定义，只给已有 onCasted 加 seek cancel。onStop 保留 cancelTvTouch/cancelKeySeek/releaseSpeed/后台策略；onDestroy 保留短剧/焦点/触摸/IntroSkip/AI/stopBackdropAutoScroll，叠加 retire/invalidate/page-scoped cancel。
- DANMAKU RefreshEvent：reload 前 invalidate，并保留 mobile refreshDanmakuControls 与后续 IntroSkip/TMDB/个人推荐等所有方法。mobile READY 加 renewAutoDanmaku 不覆盖 loading 最小窗口/IntroSkip/短剧分支。
- mobile decode refresh：回调必须同时保留 requestId/generation/key/flag/episode 和上游 Object owner；成功、失败、Throwable 都先验证当前 request/service owner。仅同 owner 失败 finishDecodeRestore(true) 后 fallback，同 owner 成功 finish(false)+trace/new-owned-query。保留 activePlaybackKey、pending UI；不能只采用上游 owner 签名，也不能让晚回调 toggle 新会话。
- DanmakuApi：保留 ArrayList、本地 canAutoSearch=canSearch 的站点弹幕追加语义及 MediaTitleRequest 全链（match cache→人工系列/TMDB→clean→AI fallback）。为整条链增加 owner-aware 查询/取消，且每次 fallback 前校验取消状态；仅最终 found guard 不足。取消已 post cache 命中不可 apply，已取消 onFailure 不可发下一 Call，两页互不取消；人工选择无参 cancel 仍可取消所有 owned，本页 destroy 只能 cancel(ownerTag)。保留默认 public API，不做清理重构。
- 短剧 commitShortDramaTransition/searchShortDramaDanmaku：不能继续无参全局 cancel 或只有 episodeUrl guard；复用本页 invalidate/owned 完整标题链，保留预缓存、站点弹幕、字幕、历史和 metadata。实际 playlist transition 前撤旧 seek，检查 PlayerManager.commitPlaylistTransition 是否更新 trace（只读）；若不更新，使用独立请求/token snapshot 拒绝旧转换结果，不擅改 PlayerManager。
- CustomKeyDownVod 自动块：本地 isChangingSpeed/releaseSpeed 与上游 250ms/key-device-downTime/trace/zero-offset/cancel session 都要保留。
- Js/Py：保留 namespace overload、BaseLoader.slot(namespace,key)、裸 siteKey、recent slot/baseKeys、SpiderCleanup+finally 和所有 public 签名。仅 namespace getSpider 的 computeIfAbsent 主体委托 SpiderInit.load(spiders,slot,原 factory,原 initializer,本地 diagnostic callback)。dex/App/init 必须在 helper 捕获的 factory/initializer 内，不能提前实参求值。
- JS 保留两条 SpiderDebug 和 stderr；PY 保留 PY_TAG/key/api/error 类消息/Throwable 与无条件 printStackTrace。normalizeExt 的 null/空白/原 JSON 字符串保真；trim 后 []→{}，不得其它归一化。
- SpiderInit 最终 c861fac058e89136a6a2be233262b532fb4ceee6 修复保留：失败实例 destroy 一次、cleanup/diagnostic/打印失败均不逸出，不负缓存，返回非 null SpiderNull。不做 Module.clear/Python 重启/共享 app close。新的失败请求会再次 init，是成本而非无限自动重试。
- STRM/QuickJS Spider/ProxyResponse 四个非冲突生产块保留最终 tip；Source/Proxy/SpiderCleanup/chaquo Spider 保持本地，不新增编辑。STRM 严格 URI 优先，|headers/中文容忍返回原整行、已知长度媒体指针继续 peek、未知长度媒体早退免 peek、64KiB 有界、非成功/206 回原地址、资源关闭；不递归目标请求/不改变跨 host header。
- ProxyResponse：raw header 先校验 CR/LF/NUL，Content-Type 独立、过滤 CL/TE/CE，Location/Range/ETag 保真，base64 保原 bytes，顶层非对象受控拒绝；旧 proxy1/Python tuple 不改。Spider early-init destroy guard 保留 releaseJS/executor cleanup，不声称 native 资源归零。

### 已发现风险及路由

1. mobile 晚 decode 刷新回调 / 本地 generation 守卫被覆盖：交集成执行者保留双重 owner/request 守卫，追加回归。
2. 弹幕多标题/缓存/AI/短剧取消后复活、跨页误取消：交执行者完整 owned 链适配及取消/正例测试。
3. 已迁移 onCasted/onShare 重复定义：交执行者按符号去重，双端编译确认。
4. loader namespace/public overload/清理/diagnostic 被覆盖：交执行者最小委托，namespaced 失败恢复回归。
5. 初查远端全树 9 个本地缺文件：独立复核均已在 merge-base 存在，本波新增 18 个均保留（0 missing），属本地历史删除，继续禁止复活。
6. 旧 SpiderCleanup 内 diagnostic 自身异常可能中断剩余清理：已通过 existing_cleanup_scope 问题交用户，用户选择「保持合并范围，旧缺陷另列」，本次不修，不将新增 SpiderInit 隔离等同全量旧 clear 隔离。
7. native/设备/第三方 destroy 吞错或挂起、旧 proxy1/Python framing、真实压缩二进制实体头语义：非已证实本次新增缺陷；保留既有合同，明确未验，不能把单测说成设备/全协议治理。

两组最终 tip 代码检查合并覆盖原 review 的12条修复；A独立确认1/3/4/5/10/11/12，B确认2/6/7/8/9。构建/测试在此时为0，仍需本地集成验证。目标完成时间因多阶段弹幕适配/本地冲突复杂性调为09:45 +08:00，不新增产品行为。

## 集成计划

1. 两组并行只读：A TV/mobile 播放、seek、自动弹幕；B loader/STRM/QuickJS/proxy。每组报告具体冲突合并方案、现有保护、最小测试及真实风险。
2. 协调者判定设计门并更新本记录；集成执行者检查守卫为 C123，再 `git merge --no-ff --no-commit fbe5fe91db9ba41fd380929ebc039a699dba463b`。HEAD 必须保持起点，MERGE_HEAD 必须是源 tip；不能提前 commit。
3. 执行者按两端语义最小解冲突并 `git add` 仅确认属于本任务的路径；新增回归测试只能使用已声明测试路径，不降断言。不得与协调者共写本文档或索引。
4. 执行者 self-check：冲突标记、未合 index、diff whitespace、源码关键契约与最小必要回归；保存实际命令与结果，不以自报完成作为验收。
5. 协调者独立检查最终 diff/保护与测试，必要时返工同一执行者；每简报累计至多 3 runs。
6. 独立验收通过后更新本记录与索引，`task_guard.sh finish --verified <实际证据> --commit-message <合并说明>` 生成双父合并提交及唯一 annotated recovery tag；不 push。

## 任务书与运行台账

- 只读 A：run 1/3；agent `788de76b-4386-472f-b646-1e96a10d28eb`。
- 只读 B：run 1/3；agent `ad5428c3-c0b3-4465-bbaf-93a80455b6eb`。
- 集成：累计run 2/3；agent `7c5ef281-b42a-4ddb-bbce-5a7f2d538c5d`，独占 merge/index，报告 `app/build/c123-integration-report.md`；run1未通过，run2实现/红绿/最终联合验证经父与两轴增量独立验收通过。tracked 文件29个唯一路径跨 runs 累计；ignored 报告1个累计；命令/测试总数不限但不得无新证据重复；同假设失败最多2次累计，无周期配额。两轴独立审查各累计2/3；无需run3。
- 路由：`deepseek-official/deepseek-v4.1-flash`；目录未返回授权标注，限制状态未确认，不声称被 allowlist 授权。
- 两组只读界面互不编辑；集成独占 Git 工作区/index；协调者只维护任务文档/索引并做独立验收。无共享源文件写入竞态。

## 独立验收 run1：不通过（2026-11-09 09:48 +08:00）

- 执行者 run1/3 已停止；报告 `app/build/c123-integration-report.md`。双端生产 Java 和 mobile unit Java 均已编译；原范围19类第一次175/31失败，修服务器teardown与NATIVE graphics后175/30失败，30项仍在真实 PlayerView/ImageDecoder 0×0初始化前失败，不能称业务验证通过。
- 父已读首轮报告与失败XML（`app/build/c123-xml-fixture-retry`），确认根因栈为 `ImageDecoder.setTargetSize`/`computeDensity` 收到(0,0)；NATIVE配置已实际存在但未解决，下一轮必须换诊断假设/更小验证，不再盲跑同样联合命令。
- Spec 独立 run1/3 不通过：`app/build/c123-spec-review.md`，冻结树861d2523bd20c04d07edf37b10d966d97de90430，22文件源码/测试全量diff，31项合同/12项上游修复核对；2必修，P1 mobile decode 未捕获原 service/manager，P2 OwnedSearch 非空但从未成功建Call时丢同trace重搜。父独立读decode/canApply/PlaybackActivity断连确认P1；P2须保留“无cache/从未入队/未取消”与在飞/完成/取消的语义区分。
- 两问题已交同一执行者待统一run2，不提前commit/tag。新增回归必须分别锁服务/manager替换而key/generation不变的成功/失败/Throwable与当前正例，以及无效URL构建失败→有效URL同trace重搜一次与在飞/完成/取消不重复负例。
- Standards 独立run1完成：`app/build/c123-standards-review.md`，独立必修0，整体quality仍阻塞，不以smell要求重构。父已读两份完整报告并统一发送同一执行者run2/3：先最小定位图像fixture实际decoder-size根因，再以真实业务红绿修S1/S2，最后同19类联合验证。图像问题不扩大生产资源/依赖/native，不跳过业务断言或移出失败类。mobile动态seam仅证明截取的实际生产方法，记录最终源版本，不冒称完整mobile UI。
- 时间目标已偏离；原因是上游Activity测试在本地Robolectric图像初始化失败和独立审查确认2项实质回归。停止可选探索，保留最短回归闭合路径；尚无可交付commit/tag。

## run2 最终验证与父核验（2026-11-09 10:25 +08:00）

- 最终候选 source tree：`0faa7a140468d0b0e3bb0994cd25cd1400a64719`（父独立 write-tree 冻结）；相对已完整复审的 `861d2523bd20c04d07edf37b10d966d97de90430` 为5文件 `+147/-20`。增量材料 `app/build/c123-run2-review-package.md`，两轴run2增量复审中，不凭执行者自报放行。
- 父已实际读取日志 `app/build/c123-run2-accepted-retry.log` 末尾 `BUILD SUCCESSFUL in 5m 4s`；实际XML快照 `app/build/c123-xml-run2-final` 共18类、186 tests、0 failures、0 errors、0 skipped。四任务与首轮19个过滤模式逐项相同（native工具比对 identical=True），未移出失败类。testMobile NullSafety仅unit Java编译，不冒称运行类。
- 决定性联合命令：`gradlew.bat --console=plain :app:compileMobileArm64_v8aDebugJavaWithJavac :app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugUnitTestJavaWithJavac :app:testLeanbackArm64_v8aDebugUnitTest`，19个 `--tests` 见 `app/build/c123-run2-accepted-command.log`，与原 `c123-gradle-command.log` 相同。两个production Java与mobile test Java成功。
- 实际业务红证据 `app/build/c123-run2-red.xml`：12 tests/8 failures/0 errors；6项同key/request/generation下原service/manager替换的success/error/null/Throwable污染，2项双端badport无Call后重搜失败。修复后的所有对应例在最终186中通过，不把初始图像失败当业务红。
- S1：mobile发起refresh捕获不可变原 PlaybackService/PlayerManager；成功/错误-null/Throwable在任何 restore/UI/native mutation 前检查两实例身份及原 request五维/Object owner/canApply；当前正例仍工作。S2：OwnedSearch同步状态只在无 supplied/cache、从未queued且未cancel允许同trace重搜；无Call token继续登记到cancel，防手动取消复活；在飞/完成/缓存post/取消不重复。父已独立阅读三个生产文件完整修复diff。
- 最终mobile源码 SHA-256：`824072dd842ed55d3b4635321a3226fbc05cde2bf3ed6847b129c31c172bdd96`；父Get-FileHash与XML运行记录相同。mobile动态seam只缓存该完整源码hash对应Class，源变化重编、每用例新对象，不共享请求或实例；不能称完整mobile UI/服务生命周期验收。
- 图形fixture根因边界：SDK28实际NATIVE/ShadowNativeImageDecoder，同207字节IHDR32×32 PNG直接ResourceSource解码成功，但ResourcesImpl布局路径为0×0；SDK27 BitmapDrawable路径同真Activity/current positive成功。最终只两个新增Activity测试改SDK27+NATIVE，保真实Activity/PlayerView与业务断言；不shadow所有图片、不改生产资源。不宣称解决SDK28环境或真实设备图像问题。
- 后续5项测试夹具差异：本地ViewModel实际4参入口、真实controller/keyDown初始化、同Result的合法quality start及duplicate拒绝、晚A不得toggleB并以当前B新trace作合法renew刺激；保留并加强原断言。test-only Class哈希缓存仅减少重复javac，不当成通过证据。
- 父最终状态审计：HEAD仍 `e00f1e862dfd4aef0051190cccc723377999ecc9`，MERGE_HEAD是冻结tip；unmerged0，diff-check0，31任务路径/越界0。静态全方法保留/onCasted与onShare唯一、四非冲突生产块逐blob等于源tip的证据在执行报告；guard final check PASS。尚未commit/tag，等两轴增量结论。
- 实际耗时超出目标：主因SDK28测试环境两轮初始化失败、mobile每用例重复动态javac及必要业务回归修复；已停止所有可选探索，采用最短有证据完成路径，未降低生产质量或用弱断言换时间。

## 验证计划与环境

已有环境：JAVA_HOME 指向 JDK21，ANDROID_HOME 指向本机 SDK。用 `gradlew.bat` 避免 system32/bash（WSL）与 PATH java8 混用；Git Bash 使用已确认 `G:\Git\bin\bash.exe`。

最小决定性检查：`:app:compileMobileArm64_v8aDebugJavaWithJavac`、`:app:compileLeanbackArm64_v8aDebugJavaWithJavac`；leanback 定向覆盖 SpiderInitTest、LoaderFailureLoggingTest、StrmHttpTest、ProxyResponseConsumerTest、DanmakuApiOwnershipTest、TvSeekOwnershipIntegrationTest、VideoDanmakuOwnershipTest、CustomKeyDownSeekOwnershipTest、ProxyResponseCompatibilityTest，并按本地冲突合同加入现有对应回归。日志一次保存后读取，不重复已成功且未修改的检查。

当前验证证据见上方run2最终验证（186/0/0/0）。设备端 Exo/MPV seek、真实 Python/QuickJS 生命周期、移动端 UI、APK 打包和安装不属于已验证事实；旧SpiderCleanup诊断故障边界已由用户选择不扩范围。新增已发现缺陷必须返工或交用户范围决策，不能只写风险而交付。
