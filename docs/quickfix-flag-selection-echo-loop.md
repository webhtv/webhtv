# quickfix-flag-selection-echo-loop — TV 版线路行选中回声导致自激切换循环

## Recovery anchor

- 目标：牛二接口「玩偶」线路下（开「切换线路不自动播放」时）能正常切换网盘线路与选集，不再出现线路行自激切换。
- 验收标准：
  1. 线路切换自身引发的选中回声不再回到 `onItemClick(Flag)`，循环消失（不再出现 from==to 的反复 flag switch）。
  2. 用户点击其它线路、以及切走后再切回原线路，仍然正常派发。
  3. 既有「同名线路按稳定键判定当前播放线路」的既得行为不变。
  4. leanback 定向单测通过 + leanback/mobile 编译通过。
- 计划状态：**已交付**。代码 + 单测完成，三轮评审（自查 + 两路独立复评）收敛，双 flavor 编译与定向单测全绿，已提交、推送 `dev2`、创建 PR #493 到 `beta`。
- 交付坐标：`dev2` = `bbb6d68ff2`（`168a485514` 修复 + `b1ef0aa03a` 测试强化 + `ad285c4ad2` 文档 + `bbb6d68ff2` 覆盖缺口补测），已推送；PR [#493](https://github.com/Silent1566/webhtv/pull/493)（4 文件 / +426 −2，只创建不合并）。
- recovery tag：`recovery/C106-flag-selection-echo-loop/20261027085731-168a485514c6`（修复）、`recovery/C106-flag-echo-review-fixes/20261027094225-b1ef0aa03a1a`（测试强化）、
  `recovery/C106-flag-echo-coords/20261027095404-ad285c4ad2c8`（交付坐标）、`recovery/C106-flag-echo-correctness/20261027105647-bbb6d68ff2b8`（覆盖缺口补测）。
- 回滚：`git revert 168a485514`（生产代码修复）；测试强化与文档可按需 revert `b1ef0aa03a`。
- 剩余待办（非阻塞）：端到端设备复验需要「牛二接口 + 玩偶线路」的站点配置，当前所有可用模拟器均无该配置；代码层证据已由 Robolectric 真实 Leanback 选中派发覆盖。

## 交付坐标

| 项 | 值 |
| --- | --- |
| 分支 | `dev2`（相对 `origin/beta` 只领先本任务 3 个提交） |
| 修复提交 | `168a485514` — `fix(leanback): 线路行选中回声不再自激切换，刷新途中的 NO_POSITION 不再清回声窗口` |
| 测试强化提交 | `b1ef0aa03a` — `test(leanback): 强化线路回声守卫的回归断言（存在性+顺序、取消回调、同名身份、点击边界）` |
| 覆盖缺口补测 | `bbb6d68ff2` — `test(leanback): 补上回声窗口清除与真实切换不被吞的回归用例（变异挖出的覆盖缺口）` |
| recovery tag | `recovery/C106-flag-selection-echo-loop/20261027085731-168a485514c6`、`recovery/C106-flag-echo-review-fixes/20261027094225-b1ef0aa03a1a` |
| PR | `dev2` → `beta`：[#493](https://github.com/Silent1566/webhtv/pull/493)（4 文件 / +426 −2，只创建、不合并） |
| 合并远端 beta | 本轮 `git fetch` 后 `origin/beta` = `6db0ebab9f`；`dev2` 已包含 beta 全部代码（`git diff --name-only dev2 origin/beta` 为空，内容零差异），故**无需再合并** |
| 回滚 | `git revert 168a485514`（必要时再 revert `b1ef0aa03a`） |

## 现象

用户反馈：最新 TV 测试版（`appVersion 5.6.0`、`gitRevision 236a8fc02f`）播放牛二接口「玩偶」线路时，不能切换网盘线路和选集。

用户提供的日志 `webhtv-debug-log-1.txt`（9967 行，2026-10-26 20:14:15–20:21:19）里可直接量化：

| 事件 | 次数 |
| --- | --- |
| `short-drama-queue: invalidate reason=line-switch` | 934 |
| `playback-action: flag switch`（其中 from==to） | 926（918） |
| `video-episode: select`（其中 old=8 new=8 / old=-1） | 935 |
| `video-flow: player start`（真正起播） | 8 |
| 循环窗口 20:20:33–20:21:12 内 `input.*` 用户输入事件 | **0** |

`from==to` 的分布：夸克原画→自身 430、百度原画#02→自身 278、百度原画(无限)#01→自身 133、夸克极速→自身 47、百度原画#01→自身 7。循环节拍 200–230 ms，持续 30 秒以上，期间 `play.request` = 0（既不起播也不拆流），直到 20:21:11 被一次真实起播收尾。

「循环窗口内 0 个输入事件」是关键：这不是用户连点，而是应用自己把自己反复点了一遍。

## 根因

**Leanback 线路行的选中回调把「线路切换自身造成的刷新」当成了一次新的选中。**

1. `VideoActivity` 用 `FlagSelectionListener` 监听线路行的选中（`app/src/leanback/.../VideoActivity.java:1708`）。回调会在**任何**选中位变化后 `view.post` 异步派发 `onItemClick(item)`，原先没有「同一项已派发过」的概念。
2. `onItemClick(Flag)` 自身就会重设选中位并刷新整行：`mBinding.flag.setSelectedPosition(newPosition)`、`notifyItemsChanged(...)`、`setEpisodeAdapter(...) -> updateFocus() -> mFlagAdapter.setNextFocusDown(...) -> notifyDataSetChanged()`。
3. 于是 Leanback 再派发一次「同一条线路」，回到 `onItemClick`，造成自激。

放大器：`onItemClick` 的幂等守卫是 `resolved.isSelected() && isPlaybackFlag(resolved) && !initialBinding`，而 `isPlaybackFlag` 只认「真正在播的线路键」`mPlaybackFlagKey`。开「切换线路不自动播放」时 `onRefresh()` 走 defer 分支直接 `return`，永不起播，`mPlaybackFlagKey` 恒为空 → 守卫恒失效 → 回声无人拦截。

该守卫是上一次同类修复（`772a25a454`，见 `docs/quickfix-same-name-flag-switch.md`）为区分**同名线路**引入的，副作用就是把「同线路已选中」的幂等性绑到了「是否正在播」上。

另：leanback 版 `onItemClick` 先 `setEpisodeAdapter(...)`（清空重建选集，选中位变 `-1`）再 `seamless(...)`，与 mobile 版顺序相反，使 `seamless` 里 `flag.find(...)` 拿到的 episode 已非 `isSelected()`，必然走 `selectEpisode(...) -> onRefresh()`。

## 改动（最小、单点）

`app/src/leanback/java/com/fongmi/android/tv/ui/custom/FlagSelectionListener.java`

新增 `dispatchedItem`，让「同一条线路实例的连续选中派发」只生效一次：

- `onChildViewHolderSelected`：`if (item != null && item != dispatchedItem) dispatchedItem = null;` —— 只有「选到另一条线路」才结束回声窗口，之后哪怕再选回原线路也照常派发；
  `item == null`（Leanback 在整行刷新途中短暂派发的 `NO_POSITION`）不算换成别的线路，因此不清窗口，见「第二轮自查修复」。
- `run`：在通过既有 stale 校验（`adapter.get(position) == item`）之后、`listener.onItemClick(item)` 之前加 `if (item == dispatchedItem) return;`，再 `dispatchedItem = item;`。

不改的内容（避免范围外回归）：`onItemClick` 的守卫、`seamless` 判据、`mPlaybackFlagKey` 语义、`FlagAdapter`（点击走 `getRoot().setOnClickListener`，不经过本监听器，永远不被拦）、spider 协议 `SiteApi.playerContent`。

## 测试

- `app/src/testLeanback/.../FlagSelectionListenerTest.java` 新增 `selfRefreshReselectionOfTheSameFlagDoesNotReenterTheListener`：同一线路连续选中只派发 1 次；中间插入一次 `NO_POSITION` 派发后再选中同一线路，仍只派发 1 次（防止回声窗口被刷新的 NULL 选中重开）；改选别的线路派发；再选回原线路仍派发。补充 `select(FlagSelectionListener, int)` 重载（原有 `select(int)` 委托给它）。
- `app/src/test/java/.../FlagSwitchPlaybackWiringTest.java` 新增 `leanbackFlagRowSuppressesItsOwnSelectionEcho`：钉住拦截点在派发之前、且在 stale 校验之内；钉住「改选另一条线路要重开回声窗口」；钉住「`item == null` 的 `NO_POSITION` 不得清窗口」；钉住取消回调仍保留。

## 第二轮自查修复（`NO_POSITION` 重开回声窗口）

**发现（自查，非设备上报）**：第一版窗口条件 `if (item != dispatchedItem) dispatchedItem = null;` 对 `item == null` 也成立。
而 `onItemClick(Flag)` 内的 `notifyItemsChanged(...)`（`BaseActivity.postRecyclerUpdate` → `notifyItemChanged`）与
`setEpisodeAdapter(...) → updateFocus() → FlagAdapter.setNextFocusDown → notifyDataSetChanged()` 都会刷新整行；
`notifyDataSetChanged` / `notifyItemChanged` 会**重设选中位**，Leanback 在重设与恢复之间会派发一次
`onChildViewHolderSelected(..., position = NO_POSITION, ...)`（此时 `item == null`）。
第一版会因此把窗口清掉，紧随其后的「选中回到原线路」就被当成新选中再次进入 `onItemClick` —— 自激仍可重新接上，
只是节拍比改动前更长（多一次刷新周期），在线路上屏/数据合并等会连续刷新整行的场景下尤其可能踩到。

**修复**：窗口条件改为 `if (item != null && item != dispatchedItem) dispatchedItem = null;`，
即只有「真的选中了另一条线路」才结束窗口；`NO_POSITION` 属于刷新中间态，不清窗口。

**为什么不是过宽**：拦截点仍在 `run()` 内通过既有 stale 校验（`adapter.get(position) == item`）之后，
保证「队尾的 pendingItem 就是当前真实选中项」；用户改选另一条线路必然先经历一次 `item == 另一条线路` 的选中派发
（窗口立即清空），因此「切走再切回原线路」仍会正常派发——已由 `selfRefreshReselectionOfTheSameFlagDoesNotReenterTheListener`
第二个断言（`select 2` → `select 1` 共 3 次派发）覆盖。

## 复评（全部触发路径逐一核对，结论：无必修项）

按「`onChildViewHolderSelected` 的每一个可能来源」逐条核对拦截后是否仍会正确派发：

| 来源 | 改动后行为 | 判定 |
| --- | --- | --- |
| 遥控左右键改选另一条线路 | `item != dispatchedItem` → 清窗口 → `run()` 派发 | 正常 |
| 点击线路卡片 | 走 `FlagAdapter` 的 `getRoot().setOnClickListener` → 直接 `onItemClick`，不经过本监听器 | 永不拦截 |
| 线路切换自身的整行刷新（`setSelectedPosition`/`notifyItemsChanged`/`updateFocus→notifyDataSetChanged`） | 同一 `Flag` 实例 → 拦截 | 这就是本次要消除的回声 |
| 整行刷新途中的 `NO_POSITION` | `item == null` 不清窗口（本轮修复） | 正常 |
| `checkFlag` 首次进页（`mProgrammaticFlagSwitch` 直接调 `onItemClick`） | 直调路径照常；其回声被拦 | 正常 |
| 沉浸式音频 `applyImmersiveAudioSelection` / 快进线路 `mFastPlaybackFlag` | 均为直调 + 程序化 `setSelectedPosition`，回声被拦，主流程不受影响 | 正常 |
| 线路列表整体替换（`addAll` 新对象） | `item != dispatchedItem`（新实例）→ 清窗口 → 派发 | 与改动前一致，无回归 |
| 播放页恢复/切集 `setEpisodeAdapter` | 不经过线路行选中派发 | 不受影响 |

补充核对：

- **拦截点位置**：仍在 `run()` 内、既有 stale 校验（`adapter.get(position) == item`）之后，因此「队尾 pendingItem 就是当前真实选中项」这一前提不变；不一致时依旧早退，不会误拦。
- **身份语义**：`dispatchedItem` 与 `adapter.get(position) == item` 用的是同一套**引用同一性**比较（`FlagAdapter.indexOf` 虽先身份后 `equals`，但本监听器全程用 `==`），不引入 `equals` 带来的「同名不同实例被误判为同一线路」风险，与 `C106` 合并带入的稳定键语义一致。
- **不依赖回声**：`onItemClick` 内已自行调 `setEpisodeAdapter(resolved.getEpisodes())`；线路集数据更新走 `updateFlag(...)` 直接 `setEpisodeAdapter`，没有任何路径依赖「回声再派发一次」来刷新 UI。
- **`dispatchedItem` 生命周期**：仅保留最后一次派发的引用，不持有 View/Context；随 `FlagSelectionListener`（`VideoActivity` 内 new 出、随 Activity 生命周期）一起释放，无泄漏。
- **点击重复派发**：既有轻微残留（见下节）在改动前后形态相同，未扩大，也未缩小。

## 第三轮：独立复评提出的测试强度问题与处置

外部复评（测试有效性角度）指出：行为用例只能证明「回声被拦 + 换线路仍派发」，**测不到**「窗口在刷新途中不被重开」；
区分力落在源码结构断言上，而原写法有三处强度问题。逐条处置：

| 复评意见 | 处置 |
| --- | --- |
| `!selected.contains("if (item != dispatchedItem)")` 会误红正确重构（如拆成两段 `if` 或抽方法） | **改写**：改为正向断言完整窗口条件，不再用否定字面量 |
| `guard > run.indexOf("adapter.get(position) == item)")` 在 stale 校验被整段删除时 `indexOf = -1` 仍为真 | **改写**：先 `stale >= 0 && guard >= 0 && dispatch >= 0` 再比较顺序 |
| 同理 `view.post(this) > pendingItem = item` 在 `pendingItem = item;` 被删时误绿 | **改写**：`pending >= 0 && post > pending` 且要求 `view.post(this)` 存在 |
| docstring 声称钉住取消回调，但没钉 `view.removeCallbacks(this)`（删掉它现有用例全绿） | **补断言**：`cancel >= 0 && cancel < pending`，并在文案中写对 |
| 切片依赖 `onChildViewHolderSelected` 与 `run()` 的排版顺序 | **改写**：改用该类既有的 `methodBody(...)` 取两个方法体，不再手写 `substring` |
| `==` 身份比较没有被钉住，改成 `.equals()` 全部用例仍绿（而 `Flag.equals` 只比线路名） | **补行为用例** `sameNameFlagsAreDistinguishedByIdentityNotEquals`：两个同名不同实例必须各派发一次、切回仍派发 |
| 点击路径不受保护只写在注释里 | **补断言**：`onItemClick` 方法体内不得出现 `dispatchedItem`（否则去重下沉会吞掉真实点击） |

**关于「行为测试无法捕获 v1 变异」这一判断**：本轮已用实测反驳——把窗口条件改回 v1 后，
`selfRefreshReselectionOfTheSameFlagDoesNotReenterTheListener` 确实在 `FlagSelectionListenerTest.java:160` 失败。
原因是复评只推演到「`select` 后会立刻清窗口」，忽略了 v1 的实际漏点：`onItemClick` 派发后**同步**刷新整行，
`NO_POSITION` 与「选中回到原线路」都发生在同一 `run()` 派发尚未结束的窗口里，行为层可观测。故该断言保留。

**第三轮验证**：上述改写后
`:app:testLeanbackArm64_v8aDebugUnitTest --tests FlagSelectionListenerTest --tests FlagSwitchPlaybackWiringTest`
+ `:app:testMobileArm64_v8aDebugUnitTest --tests FlagSwitchPlaybackWiringTest` → **BUILD SUCCESSFUL in 3m 6s**，
`FlagSelectionListenerTest` **10/10**（含新增同名不同实例用例）、`FlagSwitchPlaybackWiringTest` 双 flavor **9/9**。

## 第四轮：正确性轴独立复核 + 变异挖出的测试缺口

### 4.1 正确性轴复核（独立评审员，只读）

结论：**三问均不成立，无阻断项**。要点与证据：

- **无人依赖回声**：`FlagSelectionListener` 全仓唯一挂载点是 `VideoActivity.java:1708`；
  点击路径 `FlagAdapter.java:131` 直接调 `onItemClick`，不经监听器；
  对话框线路行 `EpisodeListDialog.java:135` 只 `setAdapter`、**未挂**该监听器（仅 177/183 挂 array/episode），
  故对话框根本没有回声问题。
- **四处业务全部自处理**：`onItemClick`(3644-3670)、`checkFlag`(5448-5469，5465 是**直接调用**)、
  `updateFlag`(6033-6041)、`applyImmersiveAudioSelection`(10547-10560) 都在同方法内完成刷新，
  不读取任何「回调已发生」状态。所有线路行 `setSelectedPosition` 调用点（3659/4490/5458/10554/128）
  均为「直接自己处理」或纯视觉对齐。
- **A→B→A 必然派发**：`FlagAdapter.indexOf` 先按实例身份比较，监听器全程用 `==`；
  `Flag.equals` 按线路名——两者不混用，同名不同行不会被误吞。
- **无死锁路径**：当 `indexOf` 恰等于当前选中位时 Leanback 不派发回调、`dispatchedItem` 保持旧值，
  但那两处（`checkFlag` 5457、`applyImmersiveAudioSelection` 10550）业务已在同方法内完成；
  用户随后真实换行仍是另一实例，窗口照样清空。

### 4.2 变异检验挖出的**真实测试缺口**（本轮最重要发现）

用两个变异验证断言强度：

| 变异 | 改动 | 改前结果 | 结论 |
| --- | --- | --- | --- |
| M1 | 窗口清除行失效（`dispatchedItem` 永不清空） | **12 个用例全绿** | ❌ **未被捕获** |
| M2 | 核心守卫失效（回声不再被拦，即原始缺陷） | `selfRefresh...` 在 `:160` 失败 | ✅ 已捕获 |

**M1 存活说明什么**：`.selfRefresh...` 用例里「连续选中同一线路」根本不会走「清窗口」那一行
（`item == dispatchedItem` 时该行是 no-op），`sameName...` 用的是不同实例，`select 2 → select 1` 之间
上一步已派发过 2、窗口本就被上一次选中清掉了。也就是说——**「窗口清除」这一行在补测前完全没有测试覆盖，
把它删成永不清窗口，全部用例依旧全绿**。而这行一旦失效，真实后果是：快速 A→B→A 时 B 的 `post` 被
`removeCallbacks` 取消，回到 A 会被守卫当成回声吞掉，**用户最后的选择被丢弃、界面停在 B**。

**补测**：`rapidSwitchAwayAndBackKeepsTheUsersFinalChoice`——先让 A 真正派发一次把窗口钉在 A，
再在同一帧内做 A→B→A，断言必须补派发用户最终选中的 A（共 2 次派发，第 2 次是 A）。
重跑 M1 → `FlagSelectionListenerTest.java:264` **FAILED**；恢复后全绿。

**另一个正确性用例**：`realUserSwitchesAreNeverSwallowedByTheEchoGuard`——用真实 Leanback 派发链复刻
生产 `onItemClick` 的自我刷新（`setSelected` + `setSelectedPosition` + `notifyDataSetChanged`），
连续 A→B→C→B→A 五次真实切换，断言「每次恰好派发一次、顺序与用户所按一致、收尾停在最后所选项」。

### 4.3 承接 4.1 的两条非阻断观察（本轮不处理）

- 点击路径不经守卫，故点击换线后由 3659 产生的首次回声仍会多派发一次 `onItemClick`；
  位置未变时 `GridLayoutManager` 不重复派发，最多多一次且被 3651 守卫吞掉或幂等，与修复前一致。
- `adapter.clear()/addAll()` 时 `dispatchedItem` 未复位，仅多持一个 `Flag` 引用，无正确性影响。

## 验证

1. 第一轮（`bash ./gradlew`）：`:app:testLeanbackArm64_v8aDebugUnitTest --tests FlagSwitchPlaybackWiringTest --tests FlagSelectionListenerTest` → BUILD SUCCESSFUL（6m34s），两类的 tests=9 failures=0 errors=0。
2. 第二轮（修复 `NO_POSITION` 重开窗口后，改用 `pwsh .\gradlew.bat`，本机 MSYS bash 对本仓库写入受限）：
   `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` + `:app:compileMobileArm64_v8aDebugJavaWithJavac`
   + `:app:compile{Leanback,Mobile}Arm64_v8aDebugUnitTestJavaWithJavac`
   + `:app:testLeanbackArm64_v8aDebugUnitTest --tests FlagSelectionListenerTest --tests FlagSwitchPlaybackWiringTest`
   + `:app:testMobileArm64_v8aDebugUnitTest --tests FlagSwitchPlaybackWiringTest`
   → **BUILD SUCCESSFUL in 9m 26s**，双 flavor 编译全过；随后修复后复跑 **BUILD SUCCESSFUL in 1m 2s**。
   - `FlagSelectionListenerTest`（leanback）tests=9 failures=0 errors=0 skipped=0
   - `FlagSwitchPlaybackWiringTest`（leanback + mobile）各 tests=9 failures=0 errors=0 skipped=0
   命令均带 `-PchaquopyBuildPython=G:/Python310/python.exe`。
3. **变异检验（证明新断言真的能拦住回归）**：把窗口条件临时改回第一版 `if (item != dispatchedItem) dispatchedItem = null;` 后重跑
   `:app:testLeanbackArm64_v8aDebugUnitTest --tests FlagSelectionListenerTest` → **FAILED**，
   `selfRefreshReselectionOfTheSameFlagDoesNotReenterTheListener` 在 `FlagSelectionListenerTest.java:160`
   （`transient NO_POSITION must not reopen the echo window`）报 `AssertionError`，`9 tests completed, 1 failed`；
   恢复修复后同命令 → **BUILD SUCCESSFUL in 57s**、9/9 全绿。即该断言对本次修复有区分力，不是恒真。
4. `git diff --check` 空输出。

## 设备验证状态

- 新 leanback debug 包已构建（194.8MB，14:08:54）并成功安装到 `127.0.0.1:5559`，`HomeActivity` 可正常启动（`Displayed com.fongmi.android.tv/.ui.activity.HomeActivity: +7s116ms`）。
- **但该模拟器没有牛二接口 / 玩偶线路的站点配置**（`/sdcard/TV` 下只有各网盘 cookie 与 `aowu`、`lib`、`log` 等目录，app 私有 prefs 与 `files/` 均无站点配置），其余 5555/5561/5563 同样没有。因此本次无法在设备上复现「玩偶线路 + 牛二接口」这一具体场景，端到端验证待提供站点配置后进行。
- 本仓库该区域的既有验证手段即为 Robolectric 单元测试（`FlagSelectionListenerTest` 使用真实 `HorizontalGridView` + 真实 Leanback 选中派发），本次已按此完成。

## 已知的轻微残留

用户点击线路时，`FlagAdapter` 的点击回调（`getRoot().setOnClickListener` -> `onItemClick`）与随后的选中派发会各调用一次 `onItemClick`。非 defer 模式下第二次会被既有守卫（`isPlaybackFlag` 已由 `getPlayer` 写入）挡下；defer 模式下会多走一次 UI 刷新，但不再形成循环（改动前是每 200ms 无限次）。这属于改动前即存在的形态，本次不扩大范围处理。

## 环境注意

- 本次会话中 `ops_bash`（`C:\WINDOWS\system32\bash.exe`，WSL 转发器）对 `app/**`、`.codex/**`、`.git/` 写入被 Windows 权限拒绝（`Permission denied`），
  而 DSH 文件工具、`pwsh` 工具，以及 **Git Bash**（`G:\Git\bin\bash.exe`）写入均正常。
  - 任务守卫改用 Git Bash 执行：`G:\Git\bin\bash.exe -lc "cd /f/Workspace/webtv2/webhtv && bash .codex/scripts/task_guard.sh start --id C106-flag-selection-echo-loop --mode quick-fix --scope ... --adopt-dirty ..."`
    → `PASS: task guard started; protected 0 pre-existing dirty path(s)`（4 个路径均为本任务 --adopt-dirty 认领）。
  - 构建与单测由 `pwsh` 执行（`gradlew.bat`），与 bash 等效；`git fetch` 亦须走 `pwsh`（WSL 转发器无法写 `.git/FETCH_HEAD`）。
- 用户指定的 5557 模拟器全程未上线：`adb connect 127.0.0.1:5557` 反复返回 `(10061) 目标计算机积极拒绝`，`netstat` 无 5557 监听。可用设备为 `127.0.0.1:5555/5559/5561/5563` 与 `emulator-5554/5558/5560/5562`，均已装 `com.fongmi.android.tv` 5.6.0。
