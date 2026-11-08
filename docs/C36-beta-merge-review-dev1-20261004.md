# C36：dev1 合并远端 beta 最新代码（PR#399–#403）并复评 TV 焦点环统一改动

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev1`（远端已移除/回退的提交不得顺带带回）；复评 dev1 全部已修改代码（含已提交未推送的 `94b7fba36`「统一电视版焦点高亮为主题色边框环」）；发现问题即修复并验证，循环评审直至通过；通过后提交、推送 `dev1`、创建 `dev1 -> beta` 中文 PR（只创建，不合并）。
- **验收标准**：① 合并结果包含 `origin/beta` tip `e72239063b4122c5cfc3658231fed2b06798848a`；② 远端被回退/剔除内容零复活；③ dev1 相对 beta 净差异仅含本分支自身改动（13 个路径，含本文档）；④ 目标 JVM 测试、双 flavor 编译、UI token 门、设备端 Instrumentation 契约测试、实机冷启动全部通过；⑤ 中文 PR 已创建且描述排版清楚。
- **当前状态**：合并 + 四轮评审 + 全部验证完成。第 1 轮发现并修复 1 项真实缺陷（`focusRingColor()` 曾一度改成动态取值，会把「代码路径跟随 / XML 选择器不跟随」的两种环色重新引入，已回退为单一静态取值源）；第 2–4 轮未再发现新问题，净差异内零缺陷。
- **下一动作**：`task_guard.sh finish`（合并提交 + recovery tag）→ 推送 `dev1` → 创建 PR（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1` |
| 任务开始时 HEAD | `94b7fba3634567408a570a11a38e59dd215cbce1`（领先 `origin/dev1` 1 个提交，未推送） |
| `origin/beta` tip（合并目标） | `e72239063b4122c5cfc3658231fed2b06798848a`（Merge PR #403 from dev3） |
| 合并基点 | `f22e24f5f2affc352129900d570cfd679297bf77` |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`（由 task_guard finish 创建合并提交） |
| 合并结果 | 60 个路径进入索引，**0 冲突** |
| 索引树哈希 | `772c1f6d15e81d8409d0ca454c8ac5dc8e51feb5` |

beta 侧增量（`f22e24f5f..e72239063`，**19 个提交**）：

- **PR #403（dev3，本次新带入的 5 个提交）**：`e72239063` Merge、`08d6cca51` docs(c36) 订正、`06f9af403` dev3 增量合并、`c39e5748a` Java 构建对话框前景改语义角色、`ef8bed60b` 手机版默认主题深色表面文字修复
- **PR #402（dev4）**：`faac13dd6` Merge、`3400cd7b0` 合并、`255059d48` TV 横幅按密度输出 16:9 位图
- **PR #401（dev3）**：`13f07c8cb` Merge、`884770508` 合并
- **PR #400（dev1）**：`e224f1b8c`（dev1 上一轮已进入 beta）
- **PR #399（dev4）**：`490218a66` Merge、`62527bec4` 合并
- 其它：`e2304fc1d` task-guard staged 空白校验分批（规避 Windows argv 上限）、`699635fca` 追更取消跨设备同步（墓碑方案）、`41ab47182`/`024c13b5f`/`9d0deec46`/`bfbd26bb9` TMDB 线路自定义下拉与 D-pad 修复

> 过程中 beta 由 `faac13dd6` 前进到 `e72239063`（PR #403）。已核实 `faac13dd6` 是 `e72239063` 的祖先、新增 5 个提交与本地修复文件集合**零交集**，故安全重做合并（保存补丁 → `git merge --abort` → 重新 `git merge --no-commit --no-ff origin/beta` → 还原补丁与设备测试），最终合入最新 tip。

## 被回退/剔除内容核对（用户核心关注点）

1. **beta 自带删除零复活**：`beta` 相对合并基点删除 3 个路径（`app/src/leanback/res/drawable/ic_banner.png`、`.../ic_banner_foreground.xml`、`app/src/leanback/res/mipmap-anydpi-v26/ic_banner.xml`），逐一用 `git cat-file -e :<path>` 检查合并索引 → **resurrected=0**，删除状态正确带入。
2. **历史回退提交仍在祖先链中且未被改写**：`fd29d76ee`（撤销把电视版暗色配色搬到手机版）、`5682f2b05`（剔除 PR #353 主题系统改动）在合并前后均为 HEAD 祖先，回退效果原样保留；被回退的关键路径（如 `app/src/mobile/res/values/webhtv_tokens.xml`）在 `origin/beta` 与合并索引中**均不存在**。
3. **反向核对**：dev1 侧删除但 beta 保留的路径为 0；`git diff --cached --name-status origin/beta` 恰好是 dev1 自有 13 个路径（见下节），不含任何 beta 内容 → 合并未把任何 beta 侧删除项或回退项带回。
4. **task_guard 一致性**：`.codex/scripts/task_guard.sh` 合并后与 `origin/beta` 版本 MD5 相同（`1b04c226952b0903d5de1f67b3b93b8b`），即 beta 的「分批 staged 空白校验」修复被正确带入且未与本地版本冲突。

## 净差异（dev1 相对 beta 的全部改动，13 个路径）

`git diff --cached --name-status origin/beta`：

```text
M  app/src/leanback/res/layout/activity_following.xml
A  app/src/main/res/color/focus_ring_error.xml
A  app/src/main/res/color/focus_ring_primary.xml
A  app/src/main/res/color/focus_ring_secondary.xml
M  app/src/main/res/drawable/about_primary_icon_button.xml
M  app/src/main/res/layout/dialog_about.xml
M  app/src/main/res/layout/item_following.xml
M  app/src/main/res/values/colors.xml
M  app/src/main/res/values/webhtv_dimens.xml
M  app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/NativeEnhancedPlaybackStyleFocusTest.java
A  app/src/testLeanback/java/com/fongmi/android/tv/ui/activity/TvFocusRingContractTest.java
A  docs/TV-FOCUS-RING-20261004-unified-theme-ring.md
M  docs/webhtv-unified-visual-design-system-20260920.md
```

（加上本文档 `docs/C36-beta-merge-review-dev1-20261004.md`，提交后共 14 个路径。）

## 评审记录

### 第 1 轮：dev1 未推送提交 `94b7fba36`（TV 焦点环统一）

**复评对象**：`94b7fba36` 把 `tv_item_focus_ring` 从写死 `#FFD166` 接线到 `@color/webhtv_color_focus`（主题 FOCUS 用户槽），并为详情页补 3 个 `focus_ring_*` 选择器与唯一的 3dp 宽度 token。

**发现的真实缺陷（本次修复）**：详情页在 Java 里仍写死 `0xFFFFD166`，与已接线到主题槽的 `?attr/tvFocusRing` 并存 → 同一个 TV 应用里两种焦点环色（播放页/追更页跟主题、详情页永远黄色），且写死的黄色环画在浅色底板上对比度仅 **1.14–1.35:1**，几乎不可见。

**修复**（`ThemeController.focusRingColor(Context[, float alpha])` + 6 个消费点）：

| 文件 | 改动 |
| --- | --- |
| `theme/ThemeController.java` | 新增唯一取值源 `focusRingColor(Context)` / `focusRingColor(Context, float alpha)`（解析 `?attr/tvFocusRing`，失败回落 `R.color.tv_item_focus_ring`） |
| `ui/activity/TmdbDetailActivity.java` | `FOCUS_STROKE` 常量 → `focusStroke()`；chip 与外部链接描边改走同一取值源 |
| `ui/activity/TmdbPersonActivity.java` | 焦点描边改走同一取值源 |
| `ui/adapter/TmdbCardFocusHelper.java` | `private static final int FOCUS_STROKE = 0xFFFFD166` → `ThemeController.focusRingColor(card.getContext())` |
| `ui/adapter/TmdbEpisodeAdapter.java` | 原生增强卡片焦点描边改走同一取值源 |
| `ui/adapter/TmdbVideoAdapter.java` | 同上 |
| `res/drawable/shape_episode_photo_focused.xml` | `android:color="#FFD166"` → `?attr/tvFocusRing` |

**关键设计判定（经设备探针实证后确定，是本次最重要的一次纠错）**：

- 曾把 `focusRingColor()` 改成「有 profile 覆写时取 `current().colorFocus()`」，设备探针随即证明这会**引入新的分裂**：`?attr/tvFocusRing` 是编译期静态资源，Android 无公开 API 可在运行时改写已编译的 `?attr/color*`（见 `ThemeBinder` 类注释）；`ThemeBinder` 只改写 `view.getBackground()`、`MaterialCardView`/`MaterialButton` 描边、文字与 tint，**不触碰前景选择器与 `GradientDrawable` 描边**（binder 既有边界）。于是「代码路径跟随、选择器不跟随」→ 主题覆写后同一页面又出现两种环色，正是本次要消除的缺陷类。
- 结论：**回退该动态取值改动**，`focusRingColor()` 与 XML 选择器共用同一个静态编译值，主题可控性统一由 `ThemeBinder` 的既有边界承担，不额外做第二套通道。设备探针证据：

  | 场景 | `?attr/tvFocusRing` / `focusRingColor()` | `ThemeController.current().colorFocus()` |
  | --- | --- | --- |
  | 无覆写 | `#FFA8C7FA` | `#FFA8C7FA` |
  | seed `#00B0FF` | `#FFA8C7FA`（两条路径一致） | `#FF96CCF8` |
  | seed `#FF7A59` | `#FFA8C7FA`（两条路径一致） | `#FFFFB4A2` |

  同时确认 `MaterialButton` 描边由 binder 正常改写（`#A8C7FA → #96CCF8`），即「主题可控」在 binder 能力边界内照常生效。

**回归门**：新增设备端 Instrumentation 测试 `app/src/androidTest/java/com/fongmi/android/tv/theme/TvFocusRingResolutionDeviceTest.java`（3 项），把「单一取值源 + 两条路径在任何主题下同色」钉死；并把它写入提交，避免以后有人再次「只让一条路径跟随」。临时探针文件（`FocusRingProbeTest`/`FocusRingBinderProbeTest`/`FocusRingXmlPathProbeTest`）已在验证后删除，未进入提交。

### 第 2 轮：净差异全量复审

- **单一取值源收敛**：`grep -rni "ffd166" app/src --include=*.java --include=*.xml` 剩余 16 处，逐条判定：11 处为**注释/文档字符串**（说明历史值），3 处为**契约测试的反向断言**（要求这些文件不再含 `FFD166`，或要求视频层保持隔离），2 处为 `KaraokeStatusView`/`AudioPlayerBackgroundDrawable` 的**视频层配色**（已由契约测试显式排除在焦点环规范之外，属阶段 M 非目标）。**零处**遗留的可执行写死焦点环色。
- **契约测试自洽**：`TvFocusRingContractTest#noTvFocusRingColourIsHardCodedOutsideTheThemeSlot` 断言 5 个 Java 消费点都含 `ThemeController.focusRingColor` 且 6 个文件都不含 `FFD166`，与 `readMainRes` 新增的 `shape_episode_photo_focused.xml` 属性断言互补。
- **alpha 重载正确性**：`focusRingColor(context, alpha)` 按 `(color >>> 24) & 0xFF` 缩放 alpha 并保留 RGB，替换历史 `0x55FFD166`/`0x1AFFD166` 填充，行为等价。

### 第 3 轮：beta 新带入内容与本地修复的语义交叉

- 新带入的 5 个提交与本地修复文件集合**零交集**（`comm -12` 为空）。
- 新带入内容对 3 个共享对话框（`DebugLogDialog`/`MpvConfigDialog`/`PlaybackPerformanceDialog`）改为 `tokens.colorOnSurface()`/`colorPrimary()` 等语义角色，**未出现** `focus`/`tvFocusRing`/`tv_item_focus_ring` 字样，与焦点环取值源无冲突；且该三处位于 `app/src/main`（TV/mobile 共用），其焦点描边沿用 `tokens.colorPrimary()`，与 TV 焦点环同族（冻结调色板中 FOCUS 与 PRIMARY 同值），不构成第二种环色。
- 手机版 12 个 `dialog_*` 布局的深色表面文字改动只在 `app/src/mobile`，与 TV 焦点环无交集。

### 第 4 轮：合并后设备实证与残留检查

- 合并后重跑设备端 3 项 Instrumentation 契约测试：**OK (3 tests)**。
- 冷启动冒烟：进程存在、`FATAL EXCEPTION` 计数 0、`HomeActivityCurrent` 已 resumed。
- 首页实机像素采样：主题 FOCUS 槽色 `#A8C7FA` 命中 390 像素（其中 `(168,199,250)` 375 个），即焦点环确为主题色。
- 临时改过的设备偏好 `detail_open_mode` 已由 `shared_prefs/C36-backup.xml` 还原，备份文件已删除（`ls shared_prefs` 无 `C36-backup.xml`）。
- 证据目录已移出仓库（`/home/maple/Workspace/webhtv/.c36-evidence/`），`git status` 无 `??` 噪声（除新增设备测试）。

## 验证表

| 门 | 命令/方式 | 结果 |
| --- | --- | --- |
| JVM 单测（双 flavor） | `./gradlew :app:testLeanbackArm64_v8aDebugUnitTest :app:testMobileArm64_v8aDebugUnitTest` | **9390 tests / 0 failures / 0 errors / 4 skipped**（leanback 4273、mobile 5117） |
| 编译门（双 flavor） | `:app:compileLeanbackArm64_v8aDebugJavaWithJavac :app:compileMobileArm64_v8aDebugJavaWithJavac` | BUILD SUCCESSFUL |
| UI token 门 | `bash scripts/check_ui_tokens.sh` | `UI_TOKEN_STATUS PASS`（contrast pairs=38 failures=0 min=4.28） |
| 打包 + 覆盖安装 | `bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5555` | BUILD SUCCESSFUL，APK 196M，`install -r` 成功 |
| 设备契约测试 | `am instrument -e class …TvFocusRingResolutionDeviceTest` | **OK (3 tests)** |
| 实机冷启动 | `monkey … LEANBACK_LAUNCHER` + logcat | 无 FATAL，`HomeActivityCurrent` resumed，猫源 `done reason=vod-config-loaded` |
| 实机像素 | 首页截图采样 | 主题 FOCUS 色 `#A8C7FA` 命中 390 px |
| 合并空白检查 | `git diff --cached --check` | 退出码 0 |
| 合并可复现性 | 索引树哈希 `772c1f6d15e8…` | 与 `git merge --no-commit --no-ff origin/beta` 重放一致 |
| 设备状态复原 | `run-as … ls shared_prefs` | 无 `C36-backup.xml` 残留 |

## 风险与回滚

- **风险**：合并为 0 冲突的纯增量合并，且与本地修复文件集合零交集；焦点环改动为「同一取值源替换写死字面量」，不改动布局层级、不新增依赖、不改变 ABI，`focusRingColor()` 在属性解析失败时回落 `R.color.tv_item_focus_ring`（与既有 `ContextCompat.getColor(App.get(), …)` 用法同款），最坏情形与改动前同色。
- **回滚**：`git revert <merge-commit>` 或重置到 `94b7fba3634567408a570a11a38e59dd215cbce1`（recovery tag 另存）；设备侧 `install -r` 上一版 APK 即可。
- **未解决/已知边界**：`ThemeBinder` 不改写前景选择器与 `GradientDrawable` 描边，因此用户覆写 FOCUS 槽时，TV 焦点环的**选择器路径**不会变化（`MaterialButton` 描边会变化）——这是主题系统的既有架构边界，非本次引入，已在本文件与 `ThemeController.focusRingColor` 注释中如实记录，不夸大「完全跟随主题」。
