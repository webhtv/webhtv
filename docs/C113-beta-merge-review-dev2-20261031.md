# C113 合并 beta 后评审与修复（dev2）

> 任务编号：C113-beta-merge-review-dev2
> 分支：dev2
> 起始 HEAD：086a25cc25（含 1 个未推送提交）
> 合并后 HEAD：f9ebfec8d40118a6c83737050abb78209265cd54
> 任务模式：standard（task_guard scope = app/src, docs）
> 记录时间：2026-10-31 09:54 (+08:00)

## Recovery anchor

- **目标**：合并远端 beta 最新代码 → 评审本轮全部修改（含已提交未推送的 086a25cc25 与合并带入内容）→ 有问题修复并验证 → 复评循环直到通过 → 提交、推送 dev2、开 PR 到 beta（中文描述需含改动具体内容）→ 拉取远端最新。
- **验收标准**：leanback 与 mobile 双变体 Java 编译通过；本轮改动（cutout padding + C111 主线程卡顿优化）语义与既有契约无冲突、无回归；单测失败集合与 origin/beta 基线同名同类（零新增回归）；改动只落在 app/src 与 docs。
- **当前状态**：合并完成；两轴只读评审（Spec / 合并语义）均判定通过、无阻断项；双变体编译成功；双变体单测失败集合已逐条取证为既有环境冤案，与本轮改动零关联；本文件即评审记录。
- **下一步唯一动作**：task_guard.sh finish 提交本记录并打恢复 tag，随后推送 dev2、开 PR 到 beta、回拉远端。

## 1. 合并远端 beta

| 项 | 值 |
| --- | --- |
| 合并来源 | origin/beta = 77b302540d |
| 领先提交 | 77b302540d（Merge PR #502 dev1）+ 380aad38da（merge(C111-dev1)：修复评审 5 处）+ 23f51a7736（perf(leanback): cut playback-enter main-thread stalls） |
| 合并前预判 | git merge-tree --write-tree 退出码 0，无文本冲突 |
| 合并提交 | f9ebfec8d40118a6c83737050abb78209265cd54（双亲 = 086a25cc25 + 77b302540d），消息已改为中文并保留带入内容说明 |
| 带入规模 | 相对基线 ba802dc432 → 77b302540d：13 文件 +1412 −27 |
| 合并后 | git rev-list --count HEAD..origin/beta = 0，工作区干净，无冲突标记 |

带入文件（13）：

- app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java
- app/src/main/java/com/fongmi/android/tv/App.java
- app/src/main/java/com/fongmi/android/tv/bean/Backup.java
- app/src/main/java/com/fongmi/android/tv/bean/Config.java
- app/src/main/java/com/fongmi/android/tv/bean/Device.java
- app/src/main/java/com/fongmi/android/tv/playback/PlaybackConfigIdentity.java
- app/src/main/java/com/fongmi/android/tv/player/lyrics/DesktopLyricsWindow.java
- app/src/main/java/com/fongmi/android/tv/setting/PlaybackPerformanceSetting.java
- docs/C111-leanback-playback-loading-jank.md
- scripts/perf/leanback_enter_playback_analyze.py / leanback_enter_playback_probe.sh / leanback_enter_rate_probe.sh / set_debug_log.sh

## 2. 评审组织

本仓库当前无 agent teams 通道，按 code-review-zh 的双轴思路派 2 个并行只读子代理（禁改文件、禁 git 写、禁跑 gradle，要求 file:line 证据），再由协调者独立复核关键判定：

| 轴 | 范围 | 结论 |
| --- | --- | --- |
| Spec 轴 | 086a25cc25 的唯一改动（mobile BaseActivity.setPadding cutout 避让） | **通过**，无阻断项 |
| 合并语义轴 | beta 3 个提交带入的 8 个代码文件 vs dev2 本地增量 | **通过**，零交集、无回归 |

### 2.1 Spec 轴：086a25cc25 cutout padding 修复

改动（app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java:105-114）：

```java
int left = cutout.getSafeInsetLeft();
int right = leftOnly ? 0 : cutout.getSafeInsetRight();
layout.setPadding(left, 0, right, 0);
```

原实现把 left|right|top|bottom 按位或成一个值再对称写进左右。核对结论：

- 调用点全集 4 处（均在 mobile 源集）：app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java:101-103（重载入口 setPadding(ViewGroup) → setPadding(layout,false)）、app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java:2369（setPadding(mBinding.recycler, true)，updateLiveMenuInsets 的 else 分支）、app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java:2375（setPadding(mBinding.control.getRoot())）、app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:9682（setRotate 的 fullscreen && !rotate 分支）。app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java:116-118 noPadding 写全 0。leanback 源集无同名 helper。
- 不可能引入竖直遮挡回归：旧行 setPadding(padding, 0, leftOnly ? 0 : padding, 0) 第 2/4 实参恒为 0，旧实现从未给 top/bottom 留竖直空间，因此新实现也没有减少竖直避让。
- 位或本身没有物理含义：left=60、top=80 时按位或得 124（并非取最大 80），旧值会把竖屏顶部切口高度变成左右内缩，全屏时控制栏整体缩窄、左右露出黑边，正是本提交修复的现象。
- RTL 无风险：app/src/main/AndroidManifest.xml:45 声明 supportsRtl="true"，但 getSafeInsetLeft() 是物理左缘量，改用 setPaddingRelative 反而会把切口左右搞反，现用绝对 setPadding 正确。
- 与既有 docs 契约不冲突：docs/DIALOG-SAFE-01-dialog-system-bar-safe-area-20261022.md:27、docs/C99-beta-merge-review-dev3-20261022.md:73、docs/SITE-HEALTH-UI-01-health-report-bottom-inset-20261022.md:32 锁定的是 Dialog 层安全区（systemBars() | displayCutout()），与 Activity 内控制栏根不同层；app/src/mobile/java/com/fongmi/android/tv/Product.java:58-65 的 getCutout() 同样只用切口左右（返回 left | right 作为屏宽扣除量），不涉及竖直方向。
- 提示级（均 pre-existing，不在本任务范围）：P1 竖直方向仍无切口契约（app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:10430-10436 applyStatusBarSpacer 的 mStatusBarInset 只在 :1783-1791 setStatusBar() 里由 statusBars().top 赋值，从不含 displayCutout()；app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java:2351-2364 同）；P2 app/src/mobile/java/com/fongmi/android/tv/ui/activity/LiveActivity.java:2369 的 leftOnly 绑定物理左边，与 RTL 存在潜在冲突；P3 app/src/mobile/java/com/fongmi/android/tv/Product.java:58-65 的 getCutout() 把 left|right 按位或成一个值，与本次精度不同（同为 pre-existing）。

### 2.2 合并语义轴：beta 3 提交 vs dev2 增量

- 事实更正：Config/Device/Backup 没有新增任何字段（前缀 app/src/main/java/com/fongmi/android/tv/bean/）。Config.java 只 +1 import 与 5 处 PlaybackConfigIdentity.invalidateAll()（行 97,102,457,468,484）；Device.java:91-93 只加 warmIdentity()；Backup.java:136,156 只加 invalidateAll()。因此备份格式未变，无升级默认值问题。
- 失效覆盖完整：Config 行写入全集 9 处（app/src/main/java/com/fongmi/android/tv/bean/Config.java:96,101,456,467,481 与 app/src/main/java/com/fongmi/android/tv/bean/Backup.java:133,151,202,203），与 7 处 invalidateAll 一一对应；app/src/main/java/com/fongmi/android/tv/bean/Backup.java restoreConfig() 的 insert/update 在 :202-203、PlaybackIdentityResolver.resolveSaved 在 :206，都早于 :156 的失效调用，顺序正确。
- 缓存竞态正确：publish() 在 CACHE_LOCK 内以 cacheEpoch == epoch 为条件（app/src/main/java/com/fongmi/android/tv/playback/PlaybackConfigIdentity.java:65-69），invalidateAll() 同锁内 ++epoch 并 clear（:47-53）；命中路径无锁；config==null 塌落值不缓存（:85），desc 为空不缓存（:104-105）；ensureInterfaceKey()+save() 分支不 publish（:90-94）。
- dev2 侧增量（git diff --stat ba802dc432 15cf4ee1e2，19 文件）与本次带入的 8 个代码文件零交集：dev2 只动 following/*、History.java、PlaybackProgressWriter.java、FollowingActivity/Adapter、3 个 strings.xml（各 +2）、测试与文档。
- leanback VideoActivity 时序（前缀 app/src/leanback/java/com/fongmi/android/tv/）：setViewModel() 同步（VideoActivity.java:1614）→ defer 三阶段（:1620-1633）→ launchInitViewStages（:2294）；mFlagAdapter==null 门（:2891,:3340）转存 mPendingDetail/mPendingPlayer；checkId() 自带 if (detailRequested) return;（:2239）幂等。
- app/src/main/java/com/fongmi/android/tv/setting/PlaybackPerformanceSetting.java rawProfileValue（:962-980）逐类型等价旧实现（Int/Long/Float → clampProfile；String/Boolean → 全 CCE → clampProfile(fallback)），真实收益是避免 getAll() 全量拷贝。DesktopLyricsWindow.java:128-140 只把诊断字段 overlay= 改 eligible=。三个 strings.xml 无重复键（values 2498 / zh-rCN 2495 / zh-rTW 2440）。

## 3. 验证结果

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 双变体编译 | :app:compileLeanbackArm64_v8aDebugJavaWithJavac + :app:compileMobileArm64_v8aDebugJavaWithJavac | BUILD SUCCESSFUL in 2m 6s |
| leanback 单测 | :app:testLeanbackArm64_v8aDebugUnitTest | 4765 tests, 8 failed, 3 skipped |
| mobile 单测 | :app:testMobileArm64_v8aDebugUnitTest | 5544 tests, 6 failed, 3 skipped |

### 3.1 8 / 6 个失败是既有环境冤案，不是本轮回归

失败集合（mobile 6 例是 leanback 8 例的子集）：

- androidx.media3.mpvplayer.MpvFontConfigTest.writeIfChanged_replacesStaleConfigurationAndCleansTemporaryFile（IOException: Unable to commit fontconfig configuration，app/src/main/java/androidx/media3/mpvplayer/MpvFontConfig.java:74）
- androidx.media3.mpvplayer.MpvHlsCacheCoordinatorTest.successfulCommitPublishesCompleteFileAndReleasesReservation（AssertionError，:208）
- com.fongmi.android.tv.ui.activity.ReaderPlaybackRoutingSourceTest.readerDefinesTheMonotonicClockUsedByRestore（:382）
- com.fongmi.android.tv.ui.activity.ReaderPlaybackRoutingSourceTest.horizontalComicProgressUsesItsOwnPageState（:367）
- com.fongmi.android.tv.ui.activity.TmdbSourceOnlyInteractionTest.nativeVideoSourceOnlyHidesNetworkActionsInBothFlavors（:42）
- com.fongmi.android.tv.web.WebThemeTokenSourceTest.webHomeTokenDeclarationsStayInsideRootScope（:64）
- （仅 leanback）NativeEnhancedPlaybackStyleFocusTest.everyTmdbRowCardCarriesItsOwnVerticalFocusTargets（:213）
- （仅 leanback）SearchResultDownFocusTest.scrollCallbacksDeferResultLoadingUntilAfterLayout（:85）

判定依据：

- 同一失败集合已在 docs/C111-beta-merge-review-dev3-20261029.md:166 记录为「与 origin/beta 基线逐一同名 ⇒ 零回归」；docs/C101-loading-spinner-jank.md:172,175,227,272,319、docs/C58-beta-merge-review-dev2-20261011.md:101、docs/C87:147、docs/C90:83 记录同一集合。
- CRLF 类 6 例：本机 core.autocrlf=true，工作区以 CRLF 检出，断言写死 \n。实测 leanback VideoActivity.java CRLF 行 12783 / 纯 LF 行 0；CollectActivity.java CRLF 1130 行且含 preloadNextRows(int count)；eclipse.html CRLF 1282、eclipse-detail.html CRLF 1718；needle 按 CRLF 版本全部命中。git show HEAD:<file> 的 blob 本身是 LF，说明是检出行为而非仓库内容问题。
- MPV 2 例：Windows 上 File.renameTo 不能覆盖已存在文件（app/src/main/java/androidx/media3/mpvplayer/MpvFontConfig.java:74），docs/C57-beta-merge-review-dev4-20261010.md:117、docs/C76:174 已记录「Microsoft JDK 21.0.12.8 下确定性失败，JBR 21.0.10 下通过」。
- 与本轮改动无关的直接证据：git diff ba802dc432 77b302540d -- app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java 的 hunk 不含 applyTmdbRowFocusChain / updateFocus / renderTmdbRatingChips 任一 needle；cutout 改动只在 mobile 源集，不参与 leanback 测试。
- mobile 任务另有一条 Gradle 提示 "Unable to connect to the child process 'Gradle Test Executor 3'"（timeout after 120s, machine extremely loaded），是构建机负载导致的执行器连接超时，不是测试失败。

## 4. 未修复但已记录的风险

1. **API 29+ 视觉验证缺口（086a25cc25）**：本地 AVD 全为 sdk=28，小于 VERSION_CODES.Q 早退阈值（app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java:106），getCutout() 新逻辑未在真实 API29+ 设备上执行。当前证据为源码语义核对 + 编译通过，无设备实拍。验收需后续在 API29+ 设备（或带 cutout 的折叠/挖孔机）上确认全屏控制栏左右不再缩窄。
2. **armInitViewStageSafetyNet 与帧链同源（beta 带入，pre-existing）**：安全网用 mBinding.getRoot().postDelayed（app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2332），帧链用 root.postOnAnimation（app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2370），root 长期 detach 时两者会一起失效；App.post/App.postDelayed（app/src/main/java/com/fongmi/android/tv/App.java:96,100）是更稳的锚点。属上游 beta 既有实现，非本轮改动引入，未在本任务内修改以免与 beta 分叉扩大；建议回到 C111 上游任务处理。
3. **mFlagSelectionListenerRegistered 不复位（beta 带入，pre-existing）**：app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:443 与 app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2373-2379，setRecyclerView() 重建 adapter 前不复位。当前 initView 只被基类调用一次，非活跃缺陷。

## 5. 改动清单

本轮相对 origin/beta 的净差异：

- app/src/mobile/java/com/fongmi/android/tv/ui/base/BaseActivity.java:105-114（086a25cc25，已提交未推送）
- 合并带入 13 文件（见第 1 节，均为 beta 上游内容，无本地改写）
- docs/C113-beta-merge-review-dev2-20261031.md（本文件）

## 6. 回滚锚点

- 合并前 HEAD：086a25cc25e8889d23002c967e1bc236173c8fd7
- 合并提交：f9ebfec8d40118a6c83737050abb78209265cd54（可用 git reset --hard 086a25cc25 完整回退本次合并）
- 086a25cc25 已有恢复 tag：recovery/fullscreen-control-cutout-inset/20261030211754-086a25cc25e8
