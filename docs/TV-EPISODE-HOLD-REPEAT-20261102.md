# TV 选集网格长按连续移动修复（20261102）

## Recovery anchor

- 目标：详情页（TmdbDetailActivity）与播放页（VideoActivity）的 TMDB 选集网格，按住遥控方向键应连续移动焦点/连续切集；松手后选集区不得弹回长按前的位置或第一集。
- 验收：①按住 LEFT/RIGHT/UP/DOWN，焦点按格连续前进，不再只走一格；②松开方向键后选集区停在当前焦点所在行，不回跳；③未按住时（repeatCount==0）行为与修复前完全一致。
- 现场：dev5 @ 53989159df，task_guard 会话 tv-episode-hold-repeat-20261102（mode standard）；20261103 合并 origin/beta 后复评于 dev5 @ 5b24c6cfc4，task_guard 会话 beta-merge-rereview-20261103。

## 用户报告

最新 TV 测试版不能长按遥控器快速选集（以前修复过）；详情页和播放页都要审查修复；长按结束后会“闪回开始的地方”。

消歧后确认：手势＝选集网格里按住方向键连续移动；闪回＝选集区滚动弹回长按前的位置/第一集（不是播放进度回片头）。用户明确要求：不节流（跟满系统重复事件），上下方向只对 repeatCount>0 生效，repeatCount==0 一律保持原路径。

## 根因

1. 键处理只把 ACTION_DOWN 当“移动一格”，位置每次都从 `getCurrentFocus()` 的 holder 现取。焦点转移是异步的：repeat 事件到来时焦点还停在旧卡片，于是所有 repeat 都算出同一个目标 → 表现为只走一格。
2. 焦点转移原来是“排一次延迟回调”，没有失效令牌：
   - TmdbDetailActivity `focusDetailEpisode` 未命中可见卡片时 `post` + `postDelayed(80ms)`，每次按键排一条；
   - VideoActivity `focusEpisodeGridPosition` 只排一次 `post`。
   多条互不知情的回调各自用过期坐标 `scrollToPositionWithOffset` / `scrollTo` 重排，松手后就把选集区拽回长按前的位置（“闪回开始的地方”）。
3. 详情页 UP/DOWN 在垂直目标有效时直接 `return false`，把焦点交给系统 FocusFinder（与自实现 LEFT/RIGHT 不一致），按住上下同样只走一格。

## 修复

- `TmdbEpisodeGridPolicy.holdBasePosition(int focusedPosition, int pendingTarget)`：以“上一次请求的目标”作为下一次重复事件的基准，焦点真正落地前也能连续推进；无待定目标或已收敛时退回焦点位。
- 两页各加一个“目标即令牌”的字段（TmdbDetailActivity.pendingDetailEpisodeTarget / VideoActivity.mEpisodeHoldTarget），repeatCount==0 与 ACTION_UP 时清空。
- `focusDetailEpisode` / `focusEpisodeGridPosition` 改为按帧重试的重载（`postOnAnimation` + 有界 attempt=6，照抄同文件 `focusTmdbRecyclerItem` 的既有范式）：只有首次尝试才滚动定位，命中缓存 holder 就 `stopScroll` + `requestFocus`，回调执行前校验令牌，目标已换即作废。原 `postDelayed(80ms)` 与单次 `post` 删除。
- 详情页 UP/DOWN：repeatCount>0 走 `focusDetailEpisode(target)` 自实现接管，repeatCount==0 仍 `return false` 交给 RecyclerView 原生焦点搜索（保持既有行为与既有断言）。
- 长按撞边界钳制：`verticalFocusTarget` 返回 NO_FOCUS_TARGET 时，两页都在离开网格前先判 `if (event.getRepeatCount() > 0) return true;`，让“按住”停在首行/末行，而不是把焦点甩到 TMDB 行继续横跑。单次按下（repeatCount==0）仍按原规则离开网格。

## 验证

- `gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --offline --tests '*TmdbEpisodeGridPolicyTest' --tests '*TmdbDetailActivityLayoutTest' --tests '*VideoActivityLayoutTest'` → BUILD SUCCESSFUL（299 tests, 0 failed）。
- `gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --offline --tests '*NativeEnhancedPlaybackStyleFocusTest' --tests '*SpeedAdjustKeyContractTest' --tests '*VideoActivityLayoutTest'` → 18 tests, 1 failed（仅上方已列出的既有基线失败）。
- `holdBasePositionAdvancesFromTheLastRequestedCard` / `holdBasePositionKeepsRepeatingFromThePendingTarget` 覆盖 pending 推进语义。
- 源字符串断言补充：`VideoActivityLayoutTest#leanbackPlaybackEpisodeGridAdvancesOneCardPerHeldRepeat`（长按目标即令牌 + 上下边界钳制）、`TmdbDetailActivityLayoutTest#detailEpisodeGridDpadUpDownUsesFullHeightRecyclerViewUntilBoundary`（新增 boundary 钳制行断言）。
- 既有基线失败（与本改动无关，已用 `git stash` 全量回退复现）：testMobile 6 例（MpvFontConfigTest / MpvHlsCacheCoordinatorTest / ReaderPlaybackRoutingSourceTest ×2 / TmdbSourceOnlyInteractionTest / WebThemeTokenSourceTest）、testLeanback 1 例（NativeEnhancedPlaybackStyleFocusTest#everyTmdbRowCardCarriesItsOwnVerticalFocusTargets）。
- 全量回归（20261103）：`gradlew.bat :app:testMobileArm64_v8aDebugUnitTest --offline` → 5625 tests, 6 failed（全部为上面既有的 6 例）；`gradlew.bat :app:testLeanbackArm64_v8aDebugUnitTest --offline` → 4863 tests, 8 failed ＝ 上面 testMobile 的同一批 6 例（跨 flavor 共享源码测试）＋ NativeEnhancedPlaybackStyleFocusTest 既有基线 ＋ SearchResultDownFocusTest#scrollCallbacksDeferResultLoadingUntilAfterLayout。
- SearchResultDownFocusTest 的失败是 Windows 检出换行导致的环境性问题，不是回归：该测试在 `app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/SearchResultDownFocusTest.java:82-83` 用 `source.indexOf("@Override\n    public void onItemClick", start)` 硬编码 LF，而本机 `core.autocrlf=true` 使 `app/src/leanback/java/com/fongmi/android/tv/ui/activity/CollectActivity.java` 工作区换行为 CRLF（实测 CRLF 计数 1130，LF 字面量不命中），断言因此报 `search scroll helpers must be present`（第 85 行）。两个文件都不在本改动的净差量内。

设备实测（emulator-5554，arm64 leanback debug APK，真实 TMDB 详情页）：
- 详情页（TmdbDetailActivity，「昨夜将至」12 集 span=4）：按住 DOWN 3s 推进到末行后停住；按住 UP 3s 回到首行后停住；按住 RIGHT 2.5s 横移到第 4 格；每次松手后等 3s 位置不变（无回跳）。
- 播放页（VideoActivity，37 集）：按住 RIGHT 2.5s 横移 4 格；按住 DOWN 3s 下移一行；再按住 DOWN 停在末行并在行内移动；按住 UP 3s 回到首行；松手后 3.5s 位置均不变。
- 单次按键映射未变：详情页单次 DOWN 仍逐行下移并在末行按原规则 `focusFirstVisibleTmdbRow()` 离开网格；播放页单次 UP 仍走 `findFocusUp` 离开网格。
- 已知行为（设计取舍，未擅改）：在边界处“重新按住”时第一个事件 repeatCount==0，仍按原规则离开网格一次；若要“到底纹丝不动、必须松手再按才离开”，需把该规则改成显式按键状态机。

## 合并 origin/beta 与复评（20261103）

- `git merge --no-edit origin/beta` 干净合入（ort 策略、无冲突），合并提交 5b24c6cfc4；origin/beta 当时为 954527e69e（Merge PR #517）。
- 本改动相对 origin/beta 的净差量未变，仍是 7 文件：VideoActivity.java、TmdbDetailActivity.java、TmdbEpisodeGridPolicy.java、TmdbDetailActivityLayoutTest.java、VideoActivityLayoutTest.java、TmdbEpisodeGridPolicyTest.java 与本文件。
- beta 新带入的三条提交（954527e69e / 745365b8b6 / 6bb5560b1e）只动 HomeActivity.java 与 HomeCategoryNavigationSourceTest.java（+65/-3），与长按选集无关，复评结论是自洽的：`applyToolbarVisibility()` 用 `toolbarRequestedVisible && webToolbarVisible && !isCategoryVisible()` 决定工具条，`syncNativeContentInset()` 在工具条隐藏且处于分类页时用 `R.dimen.webhtv_space_6`（24dp）兜底，避免顶部留白跳变。`CustomVerticalGridView.setHeaderVisibility` 先改子视图可见性再回调 listener，滚动隐藏工具条时会在分类页短暂显示，随即被 `applyToolbarVisibility()` 纠正回来，属自愈路径。
- 本轮复评未发现新缺陷，未改动本改动的 Java 源码；`:app:compileLeanbackArm64_v8aDebugJavaWithJavac --offline` 通过，定向用例 313 tests 0 failed。

## 回滚

单次提交，`git revert` 该提交即可；无数据/接口/资源变更。
