# LIVE-LINE-FALLBACK-20261004 — 直播线路失效优先换线，不再误跳直播接口

## Recovery anchor

- **目标（验收标准）**：修复用户反馈——直播线路失效时不跳转下一个线路、反而跳到配置里其它直播接口。修复后：
  1. 线路播放失败 → 优先在当前频道的线路间轮换（含最后一条绕回第一条），试完一圈即停；**绝不因播放失败直接跳到下一个直播接口**。
  2. 只有当直播接口拉取不到节目列表（renderLive 空结果）时，才直接跳转到下一个直播接口。
- **计划状态**：已完成实施，进入验证。
- **当前文件/符号**：
  - `app/src/main/java/com/fongmi/android/tv/player/LiveSourceFallbackPolicy.java`（decideLineFailure / decideSourceFailure，删除旧 6 参 decide）
  - `app/src/leanback/java/com/fongmi/android/tv/ui/activity/LiveActivity.java`（startFlow / startSourceFallback / advanceLineForFallback / mLineFallbackAnchor / mLineFallbackExhausted / resetLineFallback）
  - `app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java`（同上）
  - `app/src/test/java/com/fongmi/android/tv/player/LiveSourceFallbackPolicyTest.java`（新语义单测）
  - `app/src/testMobile/java/com/fongmi/android/tv/ui/activity/LiveActivityLayoutTest.java`（文本断言同步）
- **已完成动作**：
  - 策略类拆分：`decideLineFailure(changeLineEnabled, channelPresent, multiLine)` 只产出 NEXT_LINE/NONE；`decideSourceFailure(sourceFallbackEnabled, nextSourceAvailable)` 只产出 NEXT_SOURCE/NONE。播放失败与跳接口解耦。
  - 两个变体 LiveActivity：`startFlow()`（onError/缓冲超时入口）只换线；`startSourceFallback()`（renderLive 空列表入口）只跳接口。
  - 绕圈防死循环：`mLineFallbackAnchor` 记录本轮起点，绕回即置 `mLineFallbackExhausted`，onError 据此停止；手动换线（nextLine/prevLine/setLine）、换台（setChannel/selectChannel）、换接口（setLive）、播放成功（STATE_READY）重置。
  - `handleSameReloadUrl` 改走 `advanceLineForFallback()`（受 anchor 保护，不再借 nextLine 重置绕圈状态）。
  - `onSourceHttpError` 门控改为 `isChange() || isSourceFallback()` 任一开启即进入直播回退流程（HTTP 播放错误现在归属"换线"语义）。
- **结果**：验证完成。
- **验证记录（2026-10-04）**：
  1. `:app:testMobileArm64_v8aDebugUnitTest --tests '*LiveSourceFallbackPolicyTest' --tests '*LiveActivityLayoutTest'` → BUILD SUCCESSFUL；XML 证实 LiveSourceFallbackPolicyTest 9/9、LiveActivityLayoutTest 9/9，0 failures/errors/skipped（LiveActivityLayoutTest 同时断言 mobile+leanback 两变体源码）。
  2. `:app:testLeanbackArm64_v8aDebugUnitTest --tests '*LiveSourceFallbackPolicyTest'` → BUILD SUCCESSFUL；leanback 变体 9/9，0 failures/errors（该任务同时完成 leanback 变体 Java 编译）。
  3. `:app:assembleLeanbackArm64_v8aDebug` → BUILD SUCCESSFUL（129 tasks），APK 产出。
  4. 设备验证受阻：全部 6 台在线设备（127.0.0.1:5561、5554/5556/5558/5560/5562）均已安装旧签名包（df5f5167/4dbe7298/3d7b5d15），与本机 debug.keystore（95e4b2e7）不一致；遵守不卸载原则，`install -r` 验证放弃，不作为回归声明。本改动为纯决策逻辑，行为由上述单测/断言/编译覆盖。
- **未验证的 worktree 改动**：全部改动见上；无未验证项。
- **未解决风险**：无。`isChange`（换台自动换线路）关闭且 sourceFallback 开启时，播放失败现在停在错误页（旧行为误跳接口）——这正是本次修复的用户契约。
- **回滚锚点**：单 commit + recovery tag；回滚即 revert 本 commit。
- **下一步（唯一）**：运行 `:app:testMobileArm64_v8aDebugUnitTest`（含 LiveSourceFallbackPolicyTest + LiveActivityLayoutTest）并编译两变体 Java。

## 背景

用户反馈：直播线路失效不会跳转下一个线路，会跳到配置里面其它接口去。

## 根因

旧 `LiveSourceFallbackPolicy.decide(change, sourceFallback, channelPresent, lastLine, onlyLine, nextSourceAvailable)` 把两种失败混在一起：

1. 频道处于**最后一条线路**失败（`lastLine=true`）→ 跳过换线分支，sourceFallback 开启且有下一接口 → `NEXT_SOURCE`（误跳接口）。这是最常见触发场景（2 条线路的第 2 条失败）。
2. 关闭自动换线（`isChange=false`）但开启接口回退 → 同样直接跳接口。
3. 单线路频道播放失败 → 也直接跳接口。

而正确契约是：**播放失败只换线；列表拉取失败才跳接口。**

## 设计决策（含 no-change 备选）

- **no change**：不可接受，直接违背用户契约（bug 本身）。
- **最小修**（已选）：拆分两种失败语义；播放失败路径移除 NEXT_SOURCE 出口；换线允许绕回（修复 lastLine 直接跳接口）；用 Activity 级 anchor+exhausted 防绕圈死循环。
- **备选（拒绝）**：在策略类里加"已试线路集合"参数——需要 Channel 状态或调用方集合，比 Activity 内 anchor 复杂，且策略类保持纯函数更利于单测。

## 影响面

- 直播（leanback + mobile）自动回退行为；点播不受影响（VideoActivity 的 startFlow 是站点/线路回退，另一套逻辑）。
- 行为变化点（有意）：(a) 最后一条线路失败绕回第一条继续试；(b) isChange 关闭时播放失败不再跳接口（停在错误页）；(c) 播放失败任何组合都不再跳接口。
- 不改持久化数据、Channel bean、播放器内核。

## 验证计划

1. `:app:testMobileArm64_v8aDebugUnitTest --tests '*LiveSourceFallbackPolicyTest*' --tests '*LiveActivityLayoutTest*'`（含两变体源码断言）
2. `:app:compileLeanbackArm64_v8aDebugJavaWithJavac` + `:app:compileMobileArm64_v8aDebugJavaWithJavac`
3. 如设备可用：安装 leanback arm64 debug APK 覆盖安装，验证正常频道播放不受影响（不注入故障直播源，不伪称完成故障场景实测）。

## 实施记录

- 2026-10-04：实施完成，进入验证。

## 后续复评修复（C37 dev4 合并 beta 复评轮）

复评 `96531595dc6` 时发现 3 个真实缺陷，已在 leanback 与 mobile 两变体同步修复并验证：

1. **缓冲超时被 `mFailedThisSession` 静默吞掉**：仅把超时改走 `onError` 会导致"上一轮已换线后新线路一直缓冲"时不再换线、进度条常驻。改为独立入口 `onBufferingTimeout()`（不受该标志限制），`if (!startFlow()) showError(error_play_url)`，`mBufferingTimeout = this::onBufferingTimeout;`。
2. **回退耗尽保护被 reload 路径绕过**：`handleSameReloadUrl` 直接调用 `advanceLineForFallback()`，而 exhausted 短路原在 `startFlow()` 内，导致本轮耗尽后一次 reload 即可重开 A→B→A 循环。改为把短路下移到唯一改动线路下标的 `advanceLineForFallback()` 入口。
3. **reload 换不动时无可见反馈**：单线路/已耗尽时进度条常驻、无错误文案。改为 `if (advanceLineForFallback()) return; App.removeCallbacks(mBufferingTimeout); showError(msg);`。

契约约束：既有 `LiveActivitySourceFallbackSourceTest` 断言 `assertFalse(source.contains("if (mFailedThisSession) return;"))`，故采用"保留守卫块 + 独立超时入口"形式，两个契约同时成立。

验证：`LiveActivityLayoutTest` 10/10（新增 `lineFallbackExhaustionKeepsErrorFeedbackForMobileAndLeanback`，覆盖上述 5 项契约）、`LiveSourceFallbackPolicyTest` 9/9、`LiveActivitySourceFallbackSourceTest` 5/5（测试侧补 CRLF 归一化，同 C35 先例）、leanback 变体 9/9，两 flavor 编译成功。详见 `docs/C37-beta-merge-review-dev4-20261004.md`。
