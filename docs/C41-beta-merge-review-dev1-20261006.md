# C41：dev1 合并远端 beta 最新代码（PR#410）并复评已修改代码

## Recovery anchor

- **目标**：把 `origin/beta` 最新代码合入 `dev1`（**远端已移除/回退的提交不得顺带带回**）；复评 dev1 全部已修改代码（含已提交未推送的 `5c95aa19c`）；发现问题修复并验证通过；循环评审直至通过；然后提交、推送 `dev1`、创建 `dev1 -> beta` 中文 PR（**只创建，不合并**）。
- **验收标准**：① 合并结果第二父为 `origin/beta` tip `414babc1b`；② 远端被回退内容**零复活**；③ beta 增量**零丢失**、dev1 既有改动**零丢失**；④ 双 flavor Java 与 androidTest 编译通过；⑤ 全量 JVM 套件零失败；⑥ UI token 门禁相对基线零新增违规；⑦ 净差异只含本分支自身改动；⑧ 提交 + recovery tag；⑨ `dev1` 已推送、PR 已创建且**未合并**。
- **当前状态**：合并完成（0 冲突）；2 轮评审完成，**未发现需要修改的缺陷**；全部验证通过；待 `task_guard.sh finish`。
- **下一动作**：`task_guard.sh finish` → 推送 `dev1` → `gh pr create`（只创建不合并）。

## 合并台账

| 项 | 值 |
| --- | --- |
| 本地分支 | `dev1` |
| 任务开始时 HEAD | `5c95aa19cde1355710970f75f60b5db0c2545b59`（`fix(detail)`：chip 底色 + 浅色剧幕中间层，领先 `origin/dev1` 1 个提交） |
| `origin/beta` tip | `414babc1b5c9acae064a4cb6a35c689379155512`（Merge PR #410 from dev2） |
| 合并基点 | `cb82755ae8fc671f19e26a7771939c7bce0b0580`（Merge PR #409 from dev4） |
| 合并方式 | `git merge --no-commit --no-ff origin/beta`，由 task_guard `finish` 创建合并提交 |
| 合并结果 | 65 路径自动合入，**0 冲突、0 冲突标记** |
| 回滚锚点 | `5c95aa19cde1355710970f75f60b5db0c2545b59` |
| 任务守卫 | `C41-beta-merge-review-dev1`（standard，scope `app/src` + `docs` + `scripts` + `.codex/scripts`） |

### beta 增量 ledger（3 个提交，全部纳入）

`git log --oneline dev1..origin/beta`：

| 完整 commit ID | 内容 | 处置 |
| --- | --- | --- |
| `414babc1b5c9acae064a4cb6a35c689379155512` | Merge PR #410 from dev2 | 纳入 |
| `990f8039d76d1a66b64317b99a92238a7a1001de` | merge：合并 origin/beta（PR#407–#409）并复评修复 TV 浅色迁移的日间白底白字回归 | 纳入 |
| `35a97894a294057db8acf6f6eaf2c72b13bd9da2` | `feat(theme): 真正打通 TV 浅色模式` | 纳入 |

**无一条与 dev1 既有实现重复或被取代**，故全部纳入。beta 增量内容为：删除 leanback 恒深色 token 遮蔽表（`app/src/leanback/res/values/webhtv_tokens.xml`）、`Theme.WebHTV.TV` 去掉硬编码 `isLightTheme=false`、58 个 leanback 布局共 405 处前景从 `?attr/colorOnSurface*` 迁移到调色板无关角色（`?attr/webhtvColorOnWallpaper` / `@color/webhtv_color_player_control_muted` / `white_70|80|90`）、`ThemeDialog` 预览模式按 `ThemeController.isNight` 解析、`ThemeController` 文档订正、新增 `LeanbackForegroundContrastTest`、C40 与 TV-LIGHT-THEME 两份文档。

## 用户核心关注点：远端已移除（回退）内容零复活

**程序化校验方法**（按行比对，不依赖人工目测）：对 beta 历史上每个**单亲 revert 提交** `R` 取父 `R^`，得到 `removed(R,f) = lines(R^:f) − lines(R:f)`；复活定义为「该行出现在合并结果中，且合并前 dev1（`5c95aa19c`）中不存在」。

```text
single-parent revert commits on beta : 26
file-versions checked                : 143
removed lines scanned                : 1377
resurrected lines                    : 0
resurrected files                    : 0
```

26 个 revert 提交清单：`fd29d76ee`、`0616a992f`、`70306f1de`、`be1b02e06`、`868e1a902`、`76fc6bcd3`、`826b20cc9`、`013ef6a2d`、`9a7a9510a`、`72ea1b56e`、`bdb2dfec3`、`a5aeb3de4`、`25956ec50`、`66dc82253`、`c9219e49f`、`aeeb3d130`、`5d4dcc756`、`720851094`、`64e83ddb1`、`dd19cadc1`、`40c2bfd9a`、`8fd731ea5`、`fe8f6528f`、`dd1252b53`、`9409b1a8c`、`d670b208c`。

### 辅助校验：revert 补丁可应用性（与 origin/beta 对照）

对每个 revert 的 `R^..R` 补丁在合并结果上做 `git apply --check`：命中 17 个 `(revert, file)` 组合；对 `origin/beta` 做同一分析同样命中 **同一组 17 个**，`HEAD-only = ∅`、`beta-only = ∅`。这 17 个组合全部是 beta 自己在后续提交里有意重新引入的内容（例如 `be1b02e06` 撤销的动态主题系统部分资源在之后被重新采用），**没有任何一项是 dev1 单方面带回**——所有命中文件在 `HEAD` 与 `origin/beta` 之间**零差异**。

### 双向完整性

```text
beta 增量零丢失：cb82755ae..414babc1b 的 65 个路径
                 → 在合并结果中逐文件与 414babc1b 比对，全部字节一致
dev1 既有零丢失：5c95aa19c 相对 cb82755ae 的 5 个路径
                 → 在合并结果中逐文件与 5c95aa19c 比对，全部字节一致
合并结果净差异：git diff --name-only origin/beta HEAD
  = 5 个路径（本任务文档 C41-*.md 尚未创建时）
  = 6 个路径（包含本任务文档自身），全部为 dev1 自身改动
```

## 评审循环记录

### 第 1 轮：合并完整性 + 本轮改动正确性

**结论：未发现问题。**

1. **合并正确性**：0 冲突；65 路径 beta 增量字节一致；5 路径 dev1 增量字节一致；26 个 revert 零复活；无冲突标记；`git diff --check` 退出码 0。
2. **chip 底色修复正确性**：`setChipState` 改走 `setBackgroundTintList`。全文件 `setBackgroundColor` 剩余 7 处调用点均**不是** `MaterialButton` 的 chip 填充（`mNightModeOverlay`、`binding.root/hero/backdropFill/backdrop`、两处 `image/content`），因此不存在同类遗漏。
3. **浅色剧幕中间层正确性**：`cinemaLightBackdropShade()` 与用户所要求的「早之前」历史实现（`7e42054de^`）**逐字节一致**；`cinemaBackdropShade()` 是全文件唯一的 shade 写入点。
4. **文字色配对**：`tintTmdbSectionTitles()` 与 `personalAiReason` 两处均按 `isCinemaStyle() && !lightTheme` 判定强制白字，与白色中间层/黑色中间层一一对应。
5. **删除面干净**：`applyLightCinemaCopyPlate` / `LightCinemaCopyPlateDrawable` 全仓零引用（仅测试以 `indexOf(...) < 0` 反向断言其不存在）；删除的 7 个 `android.graphics` import 在全文件出现 0 次；文件未使用 import 数与 `origin/beta` 持平（均为 `Traffic`、`JsonParser` 两个既有项，非本次引入）。
6. **`detailInfo` 副作用**：删除 `applyLightCinemaCopyPlate()` 后，该视图不再被程序化改写 `padding` / `background`（全仓 `detailInfo.setPadding|setBackground` 命中 0 处），布局默认值即最终值。
7. **`ThemeController` 文档订正**：`resolvedDark()` / `darkPaletteFor()` 逻辑未变，仅注释从「TV 固定深色表」改写为通用表述；因 leanback 遮蔽表已删除，两 flavor 现在解析同一套 day/night 表，探测逻辑退化为与 uiMode 规则一致的保守保留，**行为无回归**。

### 第 2 轮：针对 beta 新引入的 405 处前景迁移做专项复评

**结论：未发现白底白字，但确认了一处门禁覆盖盲区（不构成本次交付缺陷）。**

beta 的 TV 浅色迁移是**整文件批量替换**，不区分前景压在什么表面上，这是 C40 已在 dev2 侧记录过的风险类型。逐类核对：

| 新增前景 | 数量 | 最近绘制背景 | 判定 |
| --- | --- | --- | --- |
| `?attr/webhtvColorOnWallpaper` | 379 | 壁纸 / 玻璃面板 `shape_*_glass_panel`（`#CC303463` 等）/ `selector_item`（`black_20` / `black_40`） | 两种配色下都是深底，**浅字正确** |
| `@color/webhtv_color_player_control_muted` | 4 | 播放器 OSD 内部（压在视频画面上的固定深色控件） | **浅字正确** |
| `@color/white_70/80/90` | 12 | 全部在 `activity_video.xml` 播放器 OSD 内部（`shape_audio_badge` = `white_15` 叠在视频上） | **浅字正确** |
| `?attr/colorOnSurfaceVariant` | 1 | `adapter_device.xml` 浅色卡片行 | C40 已修正并钉死，**正确** |

- **门禁可失败性反证**：临时把 `?attr/colorOnSurfaceVariant` 注入 `LeanbackForegroundContrastTest.CONSTANT_LIGHT_FOREGROUNDS` 后，`lightCardRowsKeepThePaletteFollowingRole` 与 `constantLightForegroundsStayOnDarkSurfacesInDayMode` **立即失败**（`BUILD FAILED`）。证明该门禁确实覆盖本次迁移面、不是空跑；探针已完全回滚（`git diff` 为空）。
- **已记录的盲区（不修改）**：`LeanbackForegroundContrastTest` 的 `CONSTANT_LIGHT_FOREGROUNDS` 只登记了 2 个角色（`?attr/webhtvColorOnWallpaper`、`@color/webhtv_color_player_control_muted`），未登记 beta 新引入的 `white_70/80/90`。该测试是 **C40（dev2）拥有的测试文件**，在本次合并中为 beta 增量、dev1 侧零改动；扩大其常量表属于另一分支的测试契约变更，超出本任务范围（AGENTS.md §2：不修改无关范围）。实测结论是这 12 处**当前全部正确**，因此不影响本次交付；如需加固，应由 C40/dev2 侧以其自身任务 ID 处理。

## 验证

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 双 flavor Java 编译 | `:app:compile{Leanback,Mobile}Arm64_v8aDebugJavaWithJavac` | `BUILD SUCCESSFUL` |
| 双 flavor androidTest 编译 | `:app:compile{Leanback,Mobile}Arm64_v8aDebugAndroidTestJavaWithJavac` | `BUILD SUCCESSFUL` |
| 手机版全量单测 | `:app:testMobileArm64_v8aDebugUnitTest` | `5199 tests / 0 failures / 0 errors / 2 skipped` |
| TV 版全量单测 | `:app:testLeanbackArm64_v8aDebugUnitTest` | `4351 tests / 0 failures / 0 errors / 2 skipped` |
| UI token 门禁 | `bash scripts/check_ui_tokens.sh --strict` | `violations=1 legacy=0`（唯一违规为既有 `mobile/item_following.xml`，相对基线零新增） |
| 空白/冲突门禁 | `git diff --check` / 冲突标记扫描 | 退出码 0 / 0 处 |
| 关键契约测试 | `TmdbDetailChipFillTest`、`TmdbCinemaLightReadabilityTest`、`TmdbDetailActivityLayoutTest`、`LeanbackForegroundContrastTest`、`ThemeBaseWiringTest`、`ThemeContractTest`、`TvFocusRingContractTest` | 全部通过 |

### 设备级验证（`192.168.50.3:5555`，API 28，arm64，leanback flavor）

构建与安装：`bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5555` → `BUILD SUCCESSFUL`，196M debug APK **覆盖安装**（未卸载），安装后校验通过。

进入路径：`monkey -p com.silent.android.webhtv -c android.intent.category.LAUNCHER` → `HomeActivity` 渲染 → DPAD 进入「最近观看」首项 → 详情页完成渲染（TMDB 详情加载成功，非「正在加载 TMDB 详情…」覆盖层）。

实测（截图像素采样）：

- **浅色光影剧幕中间层可见**：内容浮在浅色幕布上而非剧照原图；标题与正文为深色可读；页脚按钮「浅色」为当前主题态。
- **线路 chip 有底色**：`VIP线路 / 极速蓝光 / 高速蓝光 / 蓝光线路 / 高清线路1 / 高清线路2 / 高清线路4 / 高清线路3 / LZ线路 / JY线路` 填充实测 `(234,240,245)`，相邻空白区 `(168,180,193)`，可区分；选中项 `蓝光线路` 有绿色 tint + 描边 `(29,143,90)`。
- **选集 chip 有底色**：`第 1 季` 填充实测 `(248,245,247)`，与相邻空白区 `(174,184,197)` 可区分。
- 截图：`/tmp/c41/c41_detail.png`（浅色详情页 chip 行）、`/tmp/c41/c41_lines.png`（线路行 + 选集行）。

设备状态：安装前后未卸载；未修改任何持久化偏好；验证结束后无残留构建进程。

## 回滚

revert 合并提交即回到 `5c95aa19cde1355710970f75f60b5db0c2545b59`（chip 走 `setBackgroundColor`、浅色剧幕中间层透明、浅色剧幕强制白字）。
