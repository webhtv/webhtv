# C120 · dev4 合并 origin/beta 并复评「插件全屏排他范围 / 电视插件行 / 面板加载壳」

- 分支：`dev4`
- 合并基线：`origin/beta` tip `13050a007ba`（PR #531，lab 命令编辑器焦点链 + 详情页芯片 dp 内边距 + 内嵌旋转方向保持，共 12 个提交）
- 合并前 HEAD：`db272c65817`（feat(plugin): 全屏排他范围、电视插件行与面板加载壳）
- 合并提交：`e61bdfacfa2`（0 冲突）
- 日期：2026-11-06

## 1. 合并事实与零复活/零丢失证据

| 项 | 结果 |
| --- | --- |
| 合并方式 | `git merge --no-commit --no-ff origin/beta` → 手工核验 → `task_guard finish` 生成合并提交 |
| 冲突 | 0 处（beta 侧仅改 `lab/*`、`TmdbDetailActivity` 芯片 dp 与旋转方向、两个测试文件与三份 C117–C119 文档） |
| 合并后相对 beta 的净差异 | `git diff --name-only origin/beta` 与 `git show --name-only db272c65817` **逐条相同**（20 个路径），即合并结果相对 beta 只有本分支自身改动，无 beta 内容丢失、无历史内容复活 |
| 合并后净差异规模 | 20 文件 `+1021 / -43` |

## 2. 被复评的代码范围

本次评的是"已修改的代码"，包含：`db272c65817` 的 20 个路径（功能提交）＋合并带入的 beta 提交（`lab` 编辑器焦点链、`TmdbDetailActivity` 芯片内边距 dp 化与内嵌旋转方向保持、对应测试与文档），以及后续修复轮的全部改动。

功能提交本身的四项交付（详见 `docs/PLUGIN-01-global-plugin-framework-20261030.md` §24）：

1. 插件槽位新增**排他**范围 `fullscreen`（沉浸态下生效集合只有 `fullscreen`）；
2. 电视详情页「插件」行（`pluginHeader`/`pluginScroll`/`pluginRow`）＋融合播放器控制条 chip 插到最前；
3. 面板加载壳：点击即开壳、结果按 token 回填、连点抑制；
4. 新事件 `playback.fullscreen` 与能力位接线。

## 3. 第一轮评审结论（两轴独立评审）

评审对象：`git diff origin/beta HEAD`（等价功能提交本身）。两轴分别为「正确性/生命周期」与「规格/接线完整性」，均由只读子智能体完成，0 修改。

### 3.1 必修项与处置

| 编号 | 缺陷 | 处置 |
| --- | --- | --- |
| A1 | `PluginPanelDialog` 复用已有内容的面板后，`fill(title, null)` 不清 `this.html`，插件返回空时 `dismissLoading` 的 `existing.html != null` 守卫误命中 → 面板永久转圈 | 已修：`fill` 无条件写 `this.html = html`（切回加载态即清内容），`dismissLoading` 守卫改为只拦"真的有内容" |
| A2 | `title`/`html` 未进 Bundle，Fragment 真正重建（进程回收恢复、fontScale/density/locale 变化）后内容丢失 → 永久转圈。**且原注释以"旋屏重建"论证并不成立**：宿主页 `configChanges` 已声明 `orientation\|screenSize` | 已修：新增 `ARG_TITLE`/`ARG_HTML` + `persistState()` 同步进 arguments，`initView` 回读；注释改为真实重建来源 |
| A3 | 连点抑制只挡住"再开一个壳"，动作照跑；第二次调用的 `finally` 还会替第一次归还 `pending` | 已修：判定提到**派发之前**（同 key 进行中直接 return），add/remove 同源 |
| A4 | 融合控制条 prepend 分支的 `nextFocusDown` 指向原**末尾**（横滚容器里在屏幕外 x≈2900），与注释相反 | 已修：新增 `firstFocusableChild`，改指向原第一个可聚焦子视图 |
| A5 | 电视详情页被 `PluginSurface.of()` 判为 `playback`，`surfaces:["detail"]` 在电视端永远没有入口 | 已修：三参 `scopesOf(page, immersive, leanback)`，电视端 `playback` 同时算 `detail`+`playback`；`PluginUiPresenter.attach` 用自身 `leanback` 形参调用 |
| A6 | 两页 `isImmersive()` 都排除画中画，但 `onPictureInPictureModeChanged` 无人重算 → 判据与渲染脱钩 | 已修：两个 PiP 回调补重算，`TmdbDetailActivity` 的重算位于 `isInlinePlayerMode()` 早退**之前** |
| B2 | `actions.execute(...)` 在线程池 shutdown 后抛 `RejectedExecutionException`，`finally` 不执行 → key 永久泄漏 + 壳永不收尾 | 已修：捕获后归还 key 并 `dismissLoading` |
| B3 | 事件值发裸 `fullscreen`，而隐藏判据是 `isImmersive()`（排除 PiP），两者口径不一致 | 已修：事件值统一取 `isImmersive()`，README 同步（画中画不算全屏） |
| B7 | 详情行 chip 背景是静态 drawable，电视上看不出焦点 | 已修：`rowBackground(context, focused)` + 焦点监听；未聚焦外观与旧实现逐像素等同 |

### 3.2 记录但本轮不修的项（已知边界）

| 项 | 理由 |
| --- | --- |
| `PluginSurface.matches` 与 `matchesAny` 并存（前者生产已无调用点） | 当前无行为差异（清单解析期已统一小写），删除属清理，非缺陷 |
| 手机变体 `activity_tmdb_detail.xml` 的三件套在手机端恒 GONE | 手机走悬浮入口；该布局在电视 flavours 下由 `TmdbDetailActivity` 使用，不能删 |
| 电视插件行未并入显式 `nextFocusUp/Down` 焦点链（方向键靠几何推断） | 需真机验证，改动风险大于收益 |
| `detach()` 会清 `lastClicked`，点击与面板关闭之间若发生 `refreshRender` 会失去焦点归还目标 | 窄路径，未观察到用户可见后果 |
| `TmdbDetailActivity` 在 leanback flavour 的实际可达性未确认 | 无设备证据，不做推断性改动 |

## 4. 修复轮验证

| 项 | 命令 / 方式 | 结果 |
| --- | --- | --- |
| 双 flavour Java 编译 | `:app:compileMobileArm64_v8aDebugJavaWithJavac` / `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| leanback 插件全量单测 | `:app:testLeanbackArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.plugin.*'` | 全绿（含 `PluginPanelLoadingShellTest` 7 例、`PluginRowRenderTest` 4 例、`PluginUiPresenterTest` 20 例、`PluginFullscreenWiringTest` 5 例） |
| mobile 定向单测 | `:app:testMobileArm64_v8aDebugUnitTest --tests 'com.fongmi.android.tv.plugin.*' --tests 'com.fongmi.android.tv.lab.*' --tests 'com.fongmi.android.tv.ui.activity.TmdbDetailActivityLayoutTest'` | 全绿（含 `TmdbDetailActivityLayoutTest` 132 例） |
| 变异探针 1（A1） | 把 `PluginPanelDialog.fill` 的 `this.html = html;` 回退成 `if (html != null) this.html = html;` | `PluginPanelLoadingShellTest` 2 例转红（`reusingAPanelWithContentCanStillBeDismissedByAnEmptyResult`、`fillAlwaysOverwritesTheContentField`），还原后文件 SHA-256 与变异前逐字节相同 |
| 变异探针 2（A6） | 删掉 `VideoActivity.onPictureInPictureModeChanged` 里的 `notifyPluginFullscreen();` | `PluginFullscreenWiringTest.videoActivityRecomputesPluginEntrancesOnPipChange` 转红，还原后文件 SHA-256 与变异前逐字节相同 |
| 既存环境失败 | `MpvFontConfigTest` / `MpvHlsCacheCoordinatorTest` | Windows 文件语义的既有失败，多份历史任务文档已用回退方式复现过，与本任务净差异无交集 |

## 5. 第二轮评审（针对修复轮）

- 评审对象：`git diff e61bdfacfa2` + 两个新增测试文件。
- 两轴：修复正确性（是否真的修掉 R1–R6、是否引入新缺陷）与回归风险（行数压缩是否删坏结构、`fill` 语义变更的其它消费者、`persistState` 的写入时机、PiP 新增调用、chip 焦点监听冲突、新测试的相互污染）。
- 结论：见 §6。

## 6. 第二轮评审结论

评审对象：`git diff e61bdfacfa2` + 两个新增测试文件。两轴为「修复正确性（R1–R6 是否真修掉、是否引入新缺陷）」与「回归风险（行数压缩、语义变更消费者、写入时机、PiP 调用、chip 焦点监听、新测试互污）」。

两轴对 R1–R6 的判定均为**成立**；同时各提出 1 条必修，另提出若干可选改进。

### 6.1 第二轮必修项与处置

| 编号 | 缺陷 | 处置 |
| --- | --- | --- |
| 2-1 | 整段插件 html 写进 `Fragment.arguments`，会随 Activity `onSaveInstanceState` 进 Binder 事务；宿主对 `action` 返回值无长度上限（网络层的 512KB 只限单次响应，插件可多次拼接）→ 数百 KB 正文时 `TransactionTooLargeException` | 已修：`MAX_PERSISTED_HTML_CHARS = 32768` + `ARG_HTML_OVERSIZED` 互斥标记；超限不写正文，重建时若确实无内容可恢复则关面板（`root.post` + 未 attach 时主线程 Handler 兜底 + 幂等守卫），绝不留下永久转圈的空壳 |
| 2-2 | 开不出壳时（非 `FragmentActivity` / 非主线程 / `isStateSaved` / `show()` 抛异常）结果被静默丢弃，用户点击后什么都不出现（相对改动前的回归） | 已修：`fillPanel` 在 `token == null` 且 html 非空时退化为 `PluginPanelDialog.show(fragment, title, html, ...)`；空 html 仍不弹空壳 |
| 2-3 | `routeSlotResult` 在 worker 线程回查槽位，插件被卸载/清单变化时会把 panel 结果误判成预览，已开的壳永不收尾 | 已修：`panel` 与 `label` 在派发侧定死并随 token 传给结果侧，结果侧不再调用 `host.slotOfAction` |
| 2-4 | `isImmersive()` 依赖 `isInPictureInPictureMode()` 在回调内是否已翻新值（两轴均标为未确认的时序依赖） | 已修：两页新增 `inPip` 字段，回调首行用形参赋值，`isImmersive()` 改读字段；`TmdbDetailActivity` 的重算移到大屏布局翻转之后、每条 PiP 路径各一次；并在 `initView` 里用平台真实状态初始化，堵住"进程在 PiP 中被回收后恢复"的误判 |

### 6.2 第二轮判定不成立 / 记录不修

| 项 | 结论 |
| --- | --- |
| 「`inlinePiPLayout` 翻转时序导致 `isImmersive()` 取旧值」 | **不成立**：`enterInlineFullscreen`（守卫含 `inlinePiPLayout`）与 `enterInlinePiPLayout`（守卫含 `inlineFullscreen`）互为排他守卫，`inlineFullscreen && inlinePiPLayout` 不可达。真隐患是平台时序依赖，已按 2-4 处理 |
| `prepend` 焦点链单向，离开 chip 后方向键回不去 | 记录为已知边界，需真机验证 |
| `onClosed`（焦点归还回调）未持久化 | 记录为可选改进 |
| `persistState` 注释中"覆写 `onSaveInstanceState` 会抢同一份 Bundle"论证不严谨 | 记录为可选改进 |
| `fillReplacesTheSpinnerWithTheContent` 的部分可见性断言与布局默认值重合 | 已知，行为断言另有一半有效 |
| `repeatedPanelClickRunsThePluginOnce` 用 1s latch 撑时序、`PluginController` 未 shutdown | 记录为可选改进 |
| 电视端 `VodActivity` 判为 `other`、直播/音频/投屏页不是 `playback.fullscreen` 发送方 | 已在 `README.md` 补明文（本轮实测修正） |

## 6.3 最终验证（修复轮全部落地后）

| 项 | 结果 |
| --- | --- |
| 双 flavour Java 编译 | BUILD SUCCESSFUL |
| leanback 插件全量套件 | 28 个套件全绿，含 `PluginPanelLoadingShellTest` 10 例、`PluginFullscreenWiringTest` 7 例、`PluginUiPresenterTest` 22 例、`PluginRowRenderTest` 4 例、`PluginControllerTest` 10 例、`PluginPanelPlaybackWiringTest` 8 例 |
| mobile 定向单测 | 插件全量套件 + `lab` 套件 + `TmdbDetailActivityLayoutTest`(132 例) 全绿 |
| 变异探针（本轮新增 3 组） | ①`args.putString(ARG_HTML, oversized ? null : html)` 改回无条件写正文 → 3 例转红；②`isImmersive()` 的 `!inPip` 改回 `!isInPictureInPictureMode()` → 1 例转红；③删除 `fillPanel` 的 `token == null` 退化分支 → 1 例转红。三文件还原后 SHA-256 与变异前逐字节相同 |
| 变异探针（第一轮 2 组） | 见 §4 |

## 7. 回滚锚点与交付坐标

## 7. 第三轮评审（增量）与收尾修正

评审对象：第二轮修复轮新增的增量（`inPip` 字段缓存 + 初始化、html 持久化上限与超限关面板、`fillPanel` 退化开面板、`routeSlotResult` 不回查槽位、对应测试）。结论：**必修缺陷 0 条，增量可交付**；A/B/C/D 四组判定全部成立（含 AOSP `Activity.java` 交叉验证：`performCreate` 内先写画中画状态、`dispatch` 先写字段再回调；`initView` 阶段取该值有效）。

评审另指出三条我引入的问题，已在收尾前修正：

| 项 | 修正 |
| --- | --- |
| `inPip`/`isImmersive()` 的 javadoc 以"回调内平台 API 可能未更新"为理由，与 AOSP 事实不符 | 改写为真实理由：判据读快照是为了让"重算入口"与"广播事件"看到同一份形态，而不是绕开时序；并注明断的是写法不是行为 |
| `PluginPanelLoadingShellTest` 断言源码里不含某句注释措辞（正常改注释会误红） | 删除该断言，改为只断行为相关的代码事实 |
| `detailActivityRecomputesOncePerPipPathAfterTheLayoutFlip` 锁死"重算出现 3 次"（未来加一次合法重算会误红） | 改为不锁总数，仍逐条断言三条路径的重算位置关系（早退分支内 / `enterInlinePiPLayout()` 之后 / `exitInlinePiPLayout()` 之后） |

修正后重跑：双 flavour 编译 BUILD SUCCESSFUL；leanback `PluginFullscreenWiringTest` 7 例、`PluginPanelLoadingShellTest` 10 例、`PluginRowRenderTest` 4 例、`PluginUiPresenterTest` 22 例全绿；mobile `PluginFullscreenWiringTest` 7 例、`PluginUiPresenterTest` 22 例全绿。

## 8. 交付坐标

- 功能提交：`db272c65817`（feat(plugin): 全屏排他范围、电视插件行与面板加载壳）
- 合并提交：`e61bdfacfa2`（merge origin/beta `13050a007ba`）
- 修复提交：`a72a4b4f22b5`（fix(plugin): 按三轮评审修复全屏范围、电视插件行与面板加载壳缺陷）
- recovery tag：`recovery/PLUGIN-01-P11-tv-row-fullscreen/20261106100520-db272c658172`、`recovery/C120-BETA-MERGE-DEV4-20261106/20261106100836-e61bdfacfa25`、`recovery/C120-R1-fix-review/20261106164507-a72a4b4f22b5`（均已推送）
- PR：https://github.com/Silent1566/webhtv/pull/533 （`dev4` → `beta`）
- 推送后拉取：`origin/beta` tip 仍为 `13050a007ba`，`git rev-list --left-right --count dev4...origin/beta` = `3 0`（beta 已全部合入，无新提交需要再合并）
- 回滚方式：修复提交可 `git revert <sha>` 单独撤回；整任务可 `git revert e61bdfacfa2` 回到合并前 `db272c65817`

## 9. 遗留与下一步

1. `PluginSurface.matches` 与 `matchesAny` 双实现并存（前者生产已无调用点）——清理任务，非缺陷。
2. 电视插件行未并入显式 `nextFocusUp/Down` 焦点链；`prepend` 后离开 chip 无法用方向键回来——需真机遥控器验收。
3. `PluginPanelDialog` 的 `onClosed`（焦点归还回调）未持久化，重建后关面板不还焦点。
4. `fillPanel` 在 `token == null` 且结果为空时仍无任何反馈（与"非空结果退化开面板"不一致）。
5. `repeatedPanelClickRunsThePluginOnce` 用 1s latch 撑时序，慢机有抖动面；`PluginController` 实例未 shutdown。
6. `playbackFullscreen` 同值也会下发，未确认 `PluginEventBus` 是否去重（非本轮引入）。
7. PiP 小窗里插件 chip 的遮挡观感需产品确认。
