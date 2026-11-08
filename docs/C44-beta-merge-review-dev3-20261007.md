# C44：dev3 合并远端 beta 最新代码并复评 TV 主题模式与选项弹窗

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev3`（**远端已移除/回退的提交不得顺带带回**）；复评 dev3 全部已修改代码（含已提交未推送的 `756f164ab`…`fb85759b03` 五个提交）；发现问题即修复并验证通过；循环评审直至通过；然后提交、推送 `dev3`、创建 `dev3 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并提交第二父为 `origin/beta` tip `2fafb302cc`；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev3 既有改动**零丢失**；④ 双 flavor JVM 套件失败集合与 beta 基线**逐条一致**（无新增）；⑤ UI token 门禁相对基线零新增违规；⑥ 净差异只含本分支自身改动；⑦ 实机（`192.168.50.3:5559`）复现并确认修复；⑧ 提交 + recovery tag；⑨ `dev3` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并完成（0 冲突）；3 轮评审完成——第 1 轮发现并修复 1 类真实缺陷（`LightDialog` 外壳透明度被应用两次），第 2 轮复评通过，第 3 轮复评无新增必修问题；双 flavor 全量测试、双 flavor Java/AndroidTest 编译、UI token 门禁、实机双向模式验证均完成；待 `task_guard.sh finish`。
- **下一动作**：`task_guard.sh finish` → 推送 `dev3` → `gh pr create`（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev3` |
| 任务开始时 HEAD | `fb85759b03a6f724c494160687964e588c3cf8a8`（领先 `origin/dev3` 5 个提交，未推送） |
| `origin/beta` tip | `2fafb302cc`（Merge PR #416 from dev2） |
| 合并基点（merge-base） | `2a2139bebc9cc7dd02ec421a68d0f027baf547ef`（Merge PR #413 from dev3） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`（由 task_guard `finish` 创建合并提交） |
| 合并结果 | **0 冲突、0 冲突标记**，13 路径合入 |
| 初始脏路径 | 无（`git status` 干净） |
| 回滚锚点 | `fb85759b03a6f724c494160687964e588c3cf8a8` |
| 任务守卫 | `C44-beta-merge-review-dev3-20261007`（standard，scope `app/src` + `docs`） |

> **合并期间 beta 两次前进**：首轮合并时 tip 为 `056200cda0`（PR #415）；复评与验证期间 beta 又合入 PR #416（`2fafb302cc`，弹幕接口弹窗 tint 泄漏修复）。已 `git merge --abort` 首轮合并（把第 1 轮修复暂存到 `/tmp`），改为对**新 tip `2fafb302cc`** 重新合并（同样 0 冲突），再恢复修复并重跑全部验证。最终合并树同时包含 PR #414–#416 的全部内容。提交前再次 `git fetch` 确认 beta 仍为 `2fafb302cc`，未再前进。

### beta 增量 ledger（13 个提交，全部纳入）

`git log --oneline 2a2139bebc..2fafb302cc`：

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `2fafb302cc` | Merge PR #416 from dev2 | 纳入 |
| `fb5f2a03df` | merge：合并 origin/beta（PR#412-#415）并复评弹幕弹窗 tint 泄漏修复 | 纳入 |
| `a10322a75c` | docs(danmaku)：记录提交 `7596ebabb7` 与恢复标签 | 纳入 |
| `7596ebabb7` | `fix(dialog): 弹窗改用主题覆盖层，阻止控件 tint 泄漏到系统编辑菜单` | 纳入 |
| `056200cda0` | Merge PR #415 from dev4 | 纳入 |
| `2b10824361` | merge：合并 origin/beta 最新代码并修复 episode 列表并发 | 纳入 |
| `6ff6a4458c` | Merge PR #414 from dev1 | 纳入 |
| `d1b7e2750e` | docs(c42)：记录 dev1 交付坐标并订正合并树与提交树 | 纳入 |
| `84d76f4c59` | merge：合并 origin/beta（PR#412/#413）并复评补齐短剧源禁用契约 | 纳入 |
| `9555806b70` | `fix: prevent episode list concurrent modification crash` | 纳入 |
| `790d678c9e` | `feat(crash): 崩溃页显示最后加载的蜘蛛源` | **不纳入**（beta 已删除，见下） |
| `6414185aff` | 修复短剧源规则无法禁用 | 纳入 |

**无一条与 dev3 既有实现重复或被取代**（除下条例外），故全部纳入。beta 增量内容为：`ShortDramaConfig`/`ShortDramaSourceDialog` 短剧源禁用契约修复、`SourceEpisodeSeasonCache` 与 `TmdbEpisodeSorter` 的 episode 并发修复、`BaseAlertDialog` 改用 `ThemeOverlay.WebHTV.Dialog` 阻止 tint 泄漏、`DialogRoundedCornerSourceTest` 行尾归一化修复，以及 4 份文档。

> **关于 `790d678c9e`（本轮唯一的「已回退」项）**：该提交是 `origin/beta` 的**历史祖先**（父为 `414babc1b5`），但在 beta 当前树中它新增的 3 个路径已被删除：`2b10824361`（dev4 的合并提交，`9555806b70` 的子提交）明确 `D` 掉了 `SpiderCrashBreadcrumb.java`、`SpiderCrashBreadcrumbTest.java` 与 `SPIDER-CRASH-DIAG-spider-crash-diagnostics.md`，并还原了 `JarLoader.java`、`CrashActivity.java` 与三语字符串。逐路径核验结果（合并树 vs `origin/beta`）：
>
> ```text
> JarLoader.java                            beta=present tree=present SAME
> CrashActivity.java                        beta=present tree=present SAME
> SpiderCrashBreadcrumb.java                beta=absent  tree=absent  SAME
> strings.xml ×3                            beta=present tree=present SAME
> SpiderCrashBreadcrumbTest.java            beta=absent  tree=absent  SAME
> SPIDER-CRASH-DIAG-...md                   beta=absent  tree=absent  SAME
> ```
>
> 即合并结果**跟随 beta 当前权威状态**，未把这 3 个被删除路径带回。这是本轮「远端已移除内容零复活」约束下唯一命中的条目，已由第 3 节的程序化扫描（341 路径 / 0 复活）覆盖。

### 三方无损校验

```text
beta 增量零丢失：2a2139bebc..2fafb302cc 的 13 个路径 → 合并树与 2fafb302cc 逐文件一致（0 处 DIFF）
dev3 既有零丢失：fb85759b03 相对 2a2139bebc 的 12 个路径 → 合并树与 fb85759b03 一致，仅 2 处为本任务第 1 轮修复
合并结果净差异：git diff --name-status origin/beta <合并树> = 13 个路径，全部为 dev3 自身改动
```

## 用户核心关注点：远端已移除（回退）内容零复活

程序化逐路径比对（不依赖人工目测）：

```text
beta 历史单亲 revert/撤销/回退/剔除/remove 提交 : 114
其触及唯一路径总数                              : 341
被回退删除文件在合并树中复活数                  : 0
合并前 dev3 基线同样扫描的复活数                : 0
```

检测器自检（反证可失败）：把「合并树中存在、`origin/beta` 中不存在」的真实路径喂给同一解析器 → 正确报出 `REVIVED`，因此「0 复活」不是空转结论。

关键已剔除标志物：`ThemeCatalog` = 0、`assets/themes` = 0（合并树与 beta 均为 0）。

## 评审循环记录

### 第 1 轮：`LightDialog` 外壳透明度被应用两次（真实缺陷，已修复）

**发现方式**：逐行审读 dev3 净差异时，`LightDialog.apply(AlertDialog)` 同时做两件事：

```java
Drawable background = ThemeEditorUi.shape(ctx,
        ThemeEditorUi.withAlpha(tokens.colorSurfaceContainerHigh(), tokens.dialogOpacity()), 0, 0, 22);  // ① 预乘透明度
...
ThemeController.bindWindowBackground(background);  // ② binder 内部再 applyShellOpacity(drawable, active.dialogOpacity())
```

`ThemeBinder.bindWindowBackground` 已经是 `dialogOpacity` 的唯一所有者（`ThemeBinder:267` 的 `applyShellOpacity(drawable, active.dialogOpacity())`，并有 `ThemeBinderContractTest#dialogOpacityReachesTheWindowShellAndNotTheText` 固化该契约）。① 预乘后 ② 再乘一次，得到 `opacity²`。

**第二个后果（更隐蔽）**：`ThemeColorIndex` 按**基线色精确匹配**定位角色。① 把填充改成半透明后，该 drawable 的填充值不再等于任何基线角色，②的 `bindDrawable` 于是找不到角色，**用户对 `surfaceContainerHigh` 的颜色覆写不再到达该外壳**。

**实证**（新增 `LightDialogShellOpacityTest`，Robolectric 在真实 drawable 上断言）：

| 断言 | 修复前 | 修复后 |
| --- | --- | --- |
| 外壳出厂 alpha（`dialogOpacity=0.70` 时） | **179**（已预乘） | **255**（不透明） |
| `bindWindowBackground` 后的最终 alpha | **125** = 0.70² | **179** = 0.70 |

**反证**：把 `shell()` 临时改回预乘写法重跑 → `2 tests completed, 1 failed`，失败信息精确指出 `the shell must ship opaque, so only the binder scales it expected:<255> but was:<179>`；恢复修复后 `BUILD SUCCESSFUL`。因此该门禁在「当前无违规」时**不是空转**。

**修复**（最小改动，生产行为只去掉一次多余的乘法）：

| 文件 | 改动 |
| --- | --- |
| `app/src/main/java/com/fongmi/android/tv/ui/dialog/LightDialog.java` | 抽出唯一外壳形状源 `public static Drawable shell(Context)`；外壳保持**不透明**语义填充，透明度只由 `bindWindowBackground` 施加；`root(...)` 与 `apply(...)` 共用同一形状源；删除随之无用的局部 `tokens` 与 `ThemeTokens` 导入 |
| `app/src/test/java/com/fongmi/android/tv/theme/ThemeBinderContractTest.java` | 新增断言：外壳不得预乘 `dialogOpacity`（禁止 `withAlpha(tokens.colorSurfaceContainerHigh(), tokens.dialogOpacity())`） |
| `app/src/testLeanback/java/com/fongmi/android/tv/theme/LightDialogShellOpacityTest.java` | 新增 2 用例 Robolectric 门禁，在真实 drawable 上断言「出厂不透明」与「透明度只施加一次」 |

### 第 2 轮：复评 + 同类问题排查

1. **同类排查**：全仓搜索 `withAlpha(..., dialogOpacity())`，仅剩 `ThemeDialog`（手机/TV 各一处）。该处设置的是**视图树之外的窗口背景**且**不**调用 `bindWindowBackground`，因此不存在双重施加；该行与 `origin/beta` 逐字节一致，未改动。
2. **`ChoiceDialog` 复核**：其窗口背景是 `ColorDrawable(Color.TRANSPARENT)` + 视图面板（`ThemeEditorUi.shape` 直接 `setBackground`），不经过 `bindWindowBackground`，无同类问题。
3. **净差异复核**：13 个路径全部为 dev3 自身改动，无临时文件、无调试输出、无 `.tmp/.log/.png` 混入。

### 第 3 轮：复评无新增必修问题

`LightDialog` 修复后 `ThemeBinderContractTest` 的既有断言 `light.contains("ThemeController.current().colorSurfaceContainerHigh()")` 仍成立（`shell()` 内保留该调用）；`assertFalse` 新断言与实现一致。定向套件 `com.fongmi.android.tv.theme.*` + `ui.dialog.*` 共 212 用例，唯一失败为 beta 侧既有项（见下），无新增。

## 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| TV 全量 JVM | `:app:testLeanbackArm64_v8aDebugUnitTest` | **4371 用例 / 6 失败 / 2 skipped**（失败集合与 beta 基线逐条一致，见下） |
| 手机全量 JVM | `:app:testMobileArm64_v8aDebugUnitTest` | **5217 用例 / 5 失败 / 2 skipped**（同上） |
| 新增回归门禁 | `LightDialogShellOpacityTest` | 2 用例通过；反证可失败（见第 1 轮） |
| UI token 门禁 | `scripts/check_ui_tokens.sh --strict` | `violations=1 legacy=0 hex_colors=0`（唯一违规为既有 `mobile/item_following.xml`，与 `origin/beta` 逐字节一致，相对基线**零新增**） |
| 空白门禁 | `git diff --check` | 退出码 0 |
| 冲突标记 | 全量扫描 | 0 处 |
| 双 flavor 编译 | `compile{Leanback,Mobile}Arm64_v8aDebugJavaWithJavac` + 两个 `AndroidTestJavaWithJavac` | `BUILD SUCCESSFUL` |
| 构建资源清理 | `gradlew.bat --no-daemon clean` | 见「收尾」 |

### 既有失败集合归属证明（非本任务引入）

双 flavor 失败用例与本任务的关系经**双重程序化核验**：

1. **失败测试读取的 23 个源文件**（`VideoActivity.java`、`reader.html`、`CollectActivity.java`、`eclipse.html`、`ui.css`、`HomeWebController.java`、`PlaybackActivity.java`、`NovelRouter.java`、`ReaderHistory.java`、`WebReaderActivity.java`、4 个 `Tmdb*Presenter.java`、`selector_tmdb_media_focus.xml`、`adapter_tmdb_cast.xml` 等）相对 `origin/beta` **与**相对合并前 HEAD `fb85759b03` **均逐字节一致**（`unchanged-by-merge` × 23，改动数 = 0）。
2. **断言本身是行尾敏感的**：逐条用 JVM 复算，`raw=false / normalized=true`（`\r\n → \n` 归一化后同一 needle 命中）。本机 `core.autocrlf=true`，工作区 XML/HTML/Java 为 CRLF，CI（ubuntu LF）上通过。

失败集合：leanback = `NativeEnhancedPlaybackStyleFocusTest`、`ReaderPlaybackRoutingSourceTest`×2、`SearchResultDownFocusTest`、`TmdbSourceOnlyInteractionTest`、`WebThemeTokenSourceTest`；mobile = 上述去掉 leanback 专有的 1 项，另加 `PlayerPlaybackRegressionSourceTest`。与 `origin/beta` 上 `docs/C33/C35/C37/C39` 记录的既有集合一致。

**同批修复确认**：本任务首轮合并时 `DialogRoundedCornerSourceTest` 尚在失败集合内；PR #416 已在 beta 侧把它改为行尾归一化写法，重合并后该项**转绿**，leanback 失败数由 7 降为 6、mobile 由 6 降为 5。

### 设备级验证（`192.168.50.3:5559`，API 28，1920x1080，arm64，leanback flavor）

- 构建安装：`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5559` → `BUILD SUCCESSFUL`，194 MB，`adb install -r` **覆盖安装**（未卸载）。
- 进入路径：首页 → `KEYCODE_MENU` → 「设置页面」→ 设置页 → 「外观与语言」→ 「主题模式」二级选择弹窗。
- 设备初始状态：`theme_mode=1`（**显式深色**）、`theme_color=-1`、profile `mode=dark`——正是本任务修复要覆盖的显式模式场景。

**显式深色下**（截图 + `uiautomator` 焦点 + 像素采样）：

```text
ChoiceDialog 聚焦项「深色」  填充 #98B8EC / 文字 #062E6F  → 6.39:1
ChoiceDialog 常规项「浅色」  填充 #1F2428 / 文字 #E2E2E9  → 12.14:1
ChoiceDialog 面板            填充 #2A2F34 / 标题 #E2E2E9  → 10.48:1
```

**显式浅色下**（切到「浅色」，`theme_mode` 落盘为 `0`）：

```text
ChoiceDialog 聚焦项「浅色」  填充 #2468D5 / 文字 #FFFFFF  → 5.23:1
ChoiceDialog 常规项「深色」  填充 #ECEEF4 / 文字 #1A1C1E  → 14.73:1
ChoiceDialog 面板            填充 #E7E8EF / 标题 #1A1C1E  → 13.99:1
```

上述实测值与离线 `ThemeContrast.ratio` 预测（浅 6.39/12.57/14.73、深 7.50/7.04/12.14）在**同一 token 对**上吻合，确认 token 接线在真机生效；`LightDialog` 外壳在两种模式下均为当前模式的语义表面，标题清晰可读。

**设备状态还原**：验证结束后把 `theme_mode` 切回 `1`（显式深色），并对整份偏好做逐键比对——296 键中仅 2 个运行时缓存键（`cache_mgmt_inventory_duration_ms`、`site_health`）自然变化，`theme_mode`/`theme_color`/`theme_profile_v2_json` 与验证前备份**完全一致**。未卸载应用、未清数据。

## 相邻但未修复（非本次引入，仅报告）

1. **多行源码文本断言的 CRLF 脆弱性**：本机 `core.autocrlf=true` 导致 11 项既有断言失败。beta 侧已按同类方式（`DialogRoundedCornerSourceTest`）逐项修复，建议后续继续在 beta 侧统一把多行 `contains` 改为行尾归一化写法；本任务按 AGENTS.md 范围规则仅记录、不扩界修改。
2. **`mobile/item_following.xml`** 的 UI token 违规为既有项（与 `origin/beta` 逐字节一致），非本次引入。

## 回滚

revert 合并提交即回到 `fb85759b03a6f724c494160687964e588c3cf8a8`；第 1 轮修复本身是 `LightDialog` 一个方法抽取 + 1 个断言 + 1 个新测试文件，可独立 revert。
